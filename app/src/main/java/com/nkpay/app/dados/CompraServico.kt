package com.nkpay.app.dados

import androidx.room.withTransaction
import com.nkpay.app.dominio.AparenciaSugerida
import com.nkpay.app.dominio.DatasDeCredito
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.NaturezaAjuste
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.OrigemObrigacao
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.dominio.Validacao
import java.time.LocalDate
import java.time.YearMonth

/** O que a pessoa informa para registrar uma compra no cartão ou crediário. */
data class NovaCompra(
    val contaCreditoId: String,
    val descricao: String,
    val categoriaId: String?,
    val dataCompra: LocalDate,
    val valorTotal: Long,
    val parcelas: Int,
    val primeiroVencimento: LocalDate?,
    /** Compra feita antes de usar o app (ADR 0016). */
    val anteriorAoApp: Boolean = false,
    /** Quantas parcelas (as primeiras) já foram pagas antes do app. */
    val parcelasJaPagas: Int = 0,
    /** Valor já pago, antes do app, da parcela seguinte às quitadas (8.4). */
    val pagoEmParteDaSeguinte: Long = 0,
    /**
     * v0.6 (D-A1 c): o valor de cada parcela, quando o parcelamento tem juros. Nesse caso
     * [valorTotal] é o valor ORIGINAL (a despesa) e o total das parcelas = parcela × N.
     * Vazio, ou igual a original ÷ N, é uma compra sem juros (como na v0.5).
     */
    val valorParcela: Long? = null,
    /** v0.6 (A2, regra 6B.9): parcelas pagas adiantado antes do app, fora das primeiras. */
    val antecipadas: List<Antecipada> = emptyList(),
)

/** Uma parcela antecipada antes do app: o [valorPago]; o desconto é o previsto − pago. */
data class Antecipada(val numero: Int, val valorPago: Long)

/**
 * Compras parceladas (regras 6B.2 e 6B.6, ADRs 0015 e 0016). Tudo em UMA transação,
 * com um grupo só no Registro de alterações:
 * - a DESPESA do valor total, na conta de crédito, na data da compra;
 * - a compra e as N parcelas (obrigações de origem PARCELA);
 * - na compra anterior ao app: um abatimento QUITADA_ANTES_DO_APP por parcela já paga
 *   (e pela parte paga da seguinte) e UM ajuste positivo na conta de crédito com a soma.
 * As contas próprias e os benefícios nunca são tocados.
 */
class CompraServico(
    private val banco: NkPayBanco,
    private val auditor: Auditor,
    private val relogio: Relogio,
) {
    private val contaDao = banco.contaDao()
    private val categoriaDao = banco.categoriaDao()
    private val movDao = banco.movimentacaoDao()
    private val compraDao = banco.compraDao()
    private val obrigacaoDao = banco.obrigacaoDao()
    private val ajusteDao = banco.ajusteObrigacaoDao()
    private val pagamentoDao = banco.pagamentoDao()
    private val faturaDao = banco.pagamentoFaturaDao()

    /** @return o id da compra. */
    suspend fun registrar(n: NovaCompra): Resultado<String> {
        val descricao = n.descricao.trim()
        Validacao.compra(descricao, n.valorTotal, n.parcelas, n.primeiroVencimento)?.let { return Resultado.Falha(it) }
        // v0.6 (6B.8): as partes da compra (original ÷ N) e, com juros, parcelas iguais de valorParcela.
        val partes = DatasDeCredito.valoresDasParcelas(n.valorTotal, n.parcelas)
        val comJuros = n.valorParcela != null && n.valorParcela != partes.first()
        if (comJuros) {
            val p = n.valorParcela!!
            if (p <= 0 || p > Long.MAX_VALUE / n.parcelas) return Resultado.Falha("Informe um valor de parcela válido.")
            if (p * n.parcelas < n.valorTotal) return Resultado.Falha(
                "O total das parcelas (${Dinheiro.formatar(p * n.parcelas)}) não pode ser menor que o valor original (${Dinheiro.formatar(n.valorTotal)}).",
            )
        }
        val valores = if (comJuros) List(n.parcelas) { n.valorParcela!! } else partes
        val total = valores.sum()
        if (n.anteriorAoApp) {
            if (n.parcelasJaPagas !in 0..n.parcelas) return Resultado.Falha("As parcelas já pagas vão de 0 a ${n.parcelas}.")
            if (n.pagoEmParteDaSeguinte < 0 || (n.pagoEmParteDaSeguinte > 0 &&
                    (n.parcelasJaPagas >= n.parcelas || n.pagoEmParteDaSeguinte >= valores[n.parcelasJaPagas]))
            ) return Resultado.Falha("O valor já pago da parcela seguinte precisa ser menor que ela.")
            // v0.6 (6B.9): antecipadas não repetem parcela, não se misturam com as pagas e não passam do previsto.
            val parcial = if (n.pagoEmParteDaSeguinte > 0) n.parcelasJaPagas + 1 else null
            val numeros = n.antecipadas.map { it.numero }
            if (numeros.size != numeros.toSet().size) return Resultado.Falha("Uma parcela antecipada foi informada duas vezes.")
            for (a in n.antecipadas) {
                if (a.numero !in 1..n.parcelas) return Resultado.Falha("A parcela antecipada ${a.numero} não existe (são ${n.parcelas}).")
                if (a.numero <= n.parcelasJaPagas || a.numero == parcial) return Resultado.Falha("A parcela ${a.numero} já está entre as pagas: ela não pode ser antecipada também.")
                if (a.valorPago < 0 || a.valorPago > valores[a.numero - 1]) return Resultado.Falha("O valor pago da parcela ${a.numero} vai de zero a ${Dinheiro.formatar(valores[a.numero - 1])}.")
            }
        } else if (n.parcelasJaPagas != 0 || n.pagoEmParteDaSeguinte != 0L || n.antecipadas.isNotEmpty()) {
            return Resultado.Falha("Parcelas já pagas só existem em compras anteriores ao app.")
        }
        return banco.withTransaction {
            val conta = contaDao.buscar(n.contaCreditoId)?.takeIf { it.excluidoEm == null }
                ?: return@withTransaction Resultado.Falha("Cartão ou crediário não encontrado.")
            if (!Natureza.valueOf(conta.natureza).ehCredito) return@withTransaction Resultado.Falha("Compras parceladas só em cartões e crediários.")
            if (n.categoriaId != null && categoriaDao.buscar(n.categoriaId)?.takeIf { it.excluidoEm == null } == null) {
                return@withTransaction Resultado.Falha("Categoria não encontrada.")
            }
            val grupo = relogio.novoId()
            val agora = relogio.agora()

            // 1) Despesa do valor total, no mês da compra (mesmo que seja o mês atual: 8.3).
            val despesa = MovimentacaoEntidade(
                relogio.novoId(), TipoMovimentacao.DESPESA.name, n.dataCompra.toString(), n.valorTotal, conta.id, null, n.categoriaId,
                descricao, agora, agora, null,
            )
            movDao.inserir(despesa)
            auditor.registrar(grupo, Operacao.CRIACAO, Auditor.MOVIMENTACAO, despesa.id,
                "Compra de ${Dinheiro.formatar(n.valorTotal)} “$descricao” em ${n.parcelas}x no ${conta.nome}" +
                    (if (comJuros) " (parcelas de ${Dinheiro.formatar(n.valorParcela!!)}, juros de ${Dinheiro.formatar(total - n.valorTotal)})" else ""),
                MovimentacaoEntidade.serializer(), null, despesa)

            // 2) A compra.
            val compra = CompraEntidade(
                relogio.novoId(), conta.id, descricao, n.categoriaId, n.dataCompra.toString(), total, n.parcelas, despesa.id,
                n.anteriorAoApp, agora, agora, null, valorOriginalCentavos = if (comJuros) n.valorTotal else null,
            )
            compraDao.inserir(compra)
            auditor.registrar(grupo, Operacao.CRIACAO, Auditor.COMPRA, compra.id,
                (if (n.anteriorAoApp) "Compra anterior ao app" else "Compra") + " “$descricao” registrada", CompraEntidade.serializer(), null, compra)

            // 3) As parcelas.
            val vencimentos = DatasDeCredito.vencimentos(n.primeiroVencimento!!, n.parcelas)
            val parcelas = valores.mapIndexed { i, valor ->
                ObrigacaoEntidade(
                    id = relogio.novoId(), origem = OrigemObrigacao.PARCELA.name,
                    descricao = if (n.parcelas == 1) descricao else "$descricao (${i + 1}/${n.parcelas})",
                    valorPrevistoCentavos = valor, vencimento = vencimentos[i].toString(),
                    mesReferencia = YearMonth.from(vencimentos[i]).toString(), contaFixaId = null, categoriaId = n.categoriaId,
                    contaCreditoId = conta.id, compraId = compra.id, numeroParcela = i + 1, totalParcelas = n.parcelas,
                    canceladaEm = null, criadoEm = agora, atualizadoEm = agora, excluidoEm = null,
                ).also {
                    obrigacaoDao.inserir(it)
                    auditor.registrar(grupo, Operacao.CRIACAO, Auditor.OBRIGACAO, it.id,
                        "Parcela ${i + 1}/${n.parcelas} de ${Dinheiro.formatar(valor)} (vence ${vencimentos[i]})", ObrigacaoEntidade.serializer(), null, it)
                }
            }

            // 4) Compra anterior: o que já foi pago antes do app (abatimentos + um ajuste na conta de crédito).
            // v0.6 (6B.9): o ajuste soma só a PARTE DA COMPRA do que foi pago; os juros dessas
            // parcelas não viram despesa (D-A3 b). Na parcela paga em parte, a divisão é proporcional.
            if (n.anteriorAoApp) {
                val quitadas = parcelas.take(n.parcelasJaPagas).map { it to it.valorPrevistoCentavos } +
                    (if (n.pagoEmParteDaSeguinte > 0) listOf(parcelas[n.parcelasJaPagas] to n.pagoEmParteDaSeguinte) else emptyList())
                fun jurosDoPago(p: ObrigacaoEntidade, pago: Long): Long {
                    val i = p.numeroParcela!! - 1
                    val juros = p.valorPrevistoCentavos - partes[i]
                    return if (juros <= 0) 0 else if (pago >= p.valorPrevistoCentavos) juros else pago * juros / p.valorPrevistoCentavos
                }
                val antecipadas = n.antecipadas.map { a -> parcelas[a.numero - 1] to a.valorPago }
                // A antecipada fica quitada: o ajuste soma a parte da compra inteira dela (o desconto sai primeiro dos juros).
                val soma = quitadas.sumOf { (p, pago) -> pago - jurosDoPago(p, pago) } + antecipadas.sumOf { (p, _) -> partes[p.numeroParcela!! - 1] }
                if (soma > 0) {
                    val ajuste = MovimentacaoEntidade(
                        relogio.novoId(), TipoMovimentacao.AJUSTE.name, n.dataCompra.toString(), soma, conta.id, null, null,
                        "Parcelas pagas antes do app: $descricao", agora, agora, null,
                    )
                    movDao.inserir(ajuste)
                    auditor.registrar(grupo, Operacao.CRIACAO, Auditor.MOVIMENTACAO, ajuste.id,
                        "Ajuste de ${Dinheiro.formatarComSinal(soma)} no ${conta.nome}: parcelas pagas antes do app", MovimentacaoEntidade.serializer(), null, ajuste)
                    for ((p, valor) in quitadas) {
                        val a = ObrigacaoAjusteEntidade(relogio.novoId(), p.id, null, NaturezaAjuste.QUITADA_ANTES_DO_APP.name, valor, ajuste.id, "", agora, agora, null)
                        ajusteDao.inserir(a)
                        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.AJUSTE_OBRIGACAO, a.id,
                            "Parcela ${p.numeroParcela}/${n.parcelas}: ${Dinheiro.formatar(valor)} pagos antes do app", ObrigacaoAjusteEntidade.serializer(), null, a)
                        // Parcela paga em parte: os juros dessa parte ficam resolvidos, sem despesa.
                        val juros = jurosDoPago(p, valor)
                        if (juros > 0 && valor < p.valorPrevistoCentavos) {
                            val j = ObrigacaoAjusteEntidade(relogio.novoId(), p.id, null, NaturezaAjuste.JUROS_DO_PARCELAMENTO.name, juros, null, "", agora, agora, null)
                            ajusteDao.inserir(j)
                            auditor.registrar(grupo, Operacao.CRIACAO, Auditor.AJUSTE_OBRIGACAO, j.id,
                                "Parcela ${p.numeroParcela}/${n.parcelas}: ${Dinheiro.formatar(juros)} de juros pagos antes do app", ObrigacaoAjusteEntidade.serializer(), null, j)
                        }
                    }
                    // v0.6 (A2, D-A2 b): antecipadas = "quitada antes do app" pelo valor pago + "desconto
                    // antes do app" pelo resto, os dois ligados ao ajuste datado na data da compra.
                    for ((p, pago) in antecipadas) {
                        val desconto = p.valorPrevistoCentavos - pago
                        for ((natureza, valor) in listOf(NaturezaAjuste.QUITADA_ANTES_DO_APP to pago, NaturezaAjuste.DESCONTO_ANTES_DO_APP to desconto)) {
                            if (valor <= 0) continue
                            val a = ObrigacaoAjusteEntidade(relogio.novoId(), p.id, null, natureza.name, valor, ajuste.id, "antecipada", agora, agora, null)
                            ajusteDao.inserir(a)
                            auditor.registrar(grupo, Operacao.CRIACAO, Auditor.AJUSTE_OBRIGACAO, a.id,
                                "Parcela ${p.numeroParcela}/${n.parcelas} antecipada antes do app: ${natureza.rotulo.lowercase()} de ${Dinheiro.formatar(valor)}", ObrigacaoAjusteEntidade.serializer(), null, a)
                        }
                    }
                }
            }
            Resultado.Sucesso(compra.id)
        }
    }

    /**
     * Exclui a compra e tudo o que ela criou (despesa, parcelas, abatimentos e ajuste das
     * parcelas pagas antes). Bloqueado se alguma parcela tiver pagamento ou se uma fatura
     * dela tiver abatimento geral: é preciso desfazer os pagamentos antes.
     */
    suspend fun excluir(compraId: String): Resultado<Unit> = banco.withTransaction {
        val compra = compraDao.buscar(compraId)?.takeIf { it.excluidoEm == null }
            ?: return@withTransaction Resultado.Falha("Compra não encontrada.")
        val parcelas = obrigacaoDao.daCompra(compraId)
        if (parcelas.any { pagamentoDao.validosDa(it.id).isNotEmpty() }) {
            return@withTransaction Resultado.Falha("Esta compra já tem parcela paga. Desfaça os pagamentos antes de excluir.")
        }
        val meses = parcelas.mapNotNull { it.mesReferencia }.toSet()
        if (faturaDao.validosDaConta(compra.contaCreditoId).any { it.mesReferencia in meses }) {
            return@withTransaction Resultado.Falha("Uma fatura desta compra já recebeu pagamento. Desfaça esse pagamento antes de excluir.")
        }
        val grupo = relogio.novoId()
        val agora = relogio.agora()
        suspend fun excluirMov(id: String) {
            val m = movDao.buscar(id)?.takeIf { it.excluidoEm == null } ?: return
            val d = m.copy(excluidoEm = agora, atualizadoEm = agora)
            movDao.atualizar(d)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.MOVIMENTACAO, m.id, "${TipoMovimentacao.valueOf(m.tipo).rotulo} “${m.descricao}” excluída", MovimentacaoEntidade.serializer(), m, d)
        }
        for (p in parcelas) {
            for (a in ajusteDao.validosDa(p.id)) {
                val d = a.copy(excluidoEm = agora, atualizadoEm = agora)
                ajusteDao.atualizar(d)
                auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.AJUSTE_OBRIGACAO, a.id, "Abatimento de ${Dinheiro.formatar(a.valorCentavos)} excluído", ObrigacaoAjusteEntidade.serializer(), a, d)
                a.movimentacaoId?.let { excluirMov(it) }
            }
            val d = p.copy(excluidoEm = agora, atualizadoEm = agora)
            obrigacaoDao.atualizar(d)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.OBRIGACAO, p.id, "Parcela “${p.descricao}” excluída", ObrigacaoEntidade.serializer(), p, d)
        }
        excluirMov(compra.movimentacaoId)
        val d = compra.copy(excluidoEm = agora, atualizadoEm = agora)
        compraDao.atualizar(d)
        auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.COMPRA, compra.id, "Compra “${compra.descricao}” excluída", CompraEntidade.serializer(), compra, d)
        Resultado.Sucesso(Unit)
    }

    /**
     * 8.2 (opção a): contas avulsas/fixas e despesas com nome ou valor parecidos com a
     * compra, para AVISAR (nada é convertido). Valor igual ao total ou ao de uma parcela.
     */
    suspend fun possiveisDuplicadas(descricao: String, valorTotal: Long, parcelas: Int): List<String> {
        val nome = AparenciaSugerida.normalizar(descricao)
        if (nome.length < 3 && valorTotal <= 0) return emptyList()
        val valorParcela = if (parcelas >= 1 && valorTotal > 0) DatasDeCredito.valoresDasParcelas(valorTotal, parcelas).first() else -1
        fun parecido(texto: String, valor: Long): Boolean {
            val t = AparenciaSugerida.normalizar(texto)
            val nomeBate = nome.length >= 3 && (t.contains(nome) || (t.length >= 3 && nome.contains(t)))
            val valorBate = valor > 0 && (valor == valorTotal || valor == valorParcela)
            return nomeBate || valorBate
        }
        val achados = mutableListOf<String>()
        for (o in banco.backupDao().obrigacoes()) {
            if (o.excluidoEm != null || o.contaCreditoId != null) continue
            if (parecido(o.descricao, o.valorPrevistoCentavos)) achados += "Conta a pagar “${o.descricao}” (${Dinheiro.formatar(o.valorPrevistoCentavos)})"
        }
        val contasDeCredito = banco.backupDao().contas().filter { Natureza.valueOf(it.natureza).ehContaDeTerceiros }.map { it.id }.toSet()
        for (m in banco.backupDao().movimentacoes()) {
            if (m.excluidoEm != null || m.tipo != TipoMovimentacao.DESPESA.name || m.contaId in contasDeCredito) continue
            if (parecido(m.descricao, m.valorCentavos)) achados += "Despesa “${m.descricao}” de ${Dinheiro.formatar(m.valorCentavos)} em ${m.data}"
        }
        return achados.take(5)
    }
}
