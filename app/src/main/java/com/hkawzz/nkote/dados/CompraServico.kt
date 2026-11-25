package com.hkawzz.nkote.dados

import androidx.room.withTransaction
import com.hkawzz.nkote.dominio.AparenciaSugerida
import com.hkawzz.nkote.dominio.CategoriasDoSistema
import com.hkawzz.nkote.dominio.ClassificacaoDiferenca
import com.hkawzz.nkote.dominio.DatasDeCredito
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.NaturezaAjuste
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.OrigemObrigacao
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.dominio.Validacao
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
    /**
     * v0.9 (item 9, regra 8.1): entre as [parcelasJaPagas], as que foram pagas com valor MAIOR
     * que o previsto (atraso com juros, multa...), com a data do pagamento e a natureza da diferença.
     */
    val pagasComValorMaior: List<PagaAntes> = emptyList(),
)

/** v0.9 (item 9): uma parcela paga antes do app com [valorPago] maior que a parcela, em [data]. */
data class PagaAntes(val numero: Int, val valorPago: Long, val data: LocalDate, val classificacao: ClassificacaoDiferenca?)

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
            // v0.9 (item 9): valor pago maior numa parcela já paga.
            val maiores = n.pagasComValorMaior.map { it.numero }
            if (maiores.size != maiores.toSet().size) return Resultado.Falha("Uma parcela paga com valor diferente foi informada duas vezes.")
            for (m in n.pagasComValorMaior) {
                validarPagaAntes(m.numero, m.valorPago, m.data, m.classificacao, valores.getOrNull(m.numero - 1) ?: 0, m.numero in 1..n.parcelasJaPagas)?.let { return Resultado.Falha(it) }
            }
            if (n.pagasComValorMaior.any { it.valorPago > valores[it.numero - 1] } && categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) == null) {
                return Resultado.Falha(SEM_ENCARGOS_COMPRA)
            }
        } else if (n.parcelasJaPagas != 0 || n.pagoEmParteDaSeguinte != 0L || n.antecipadas.isNotEmpty() || n.pagasComValorMaior.isNotEmpty()) {
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
                // v0.9 (item 9, regra 8.1): o que foi pago a mais vira encargo, na data do pagamento.
                for (m in n.pagasComValorMaior) {
                    val p = parcelas[m.numero - 1]
                    val excedente = m.valorPago - p.valorPrevistoCentavos
                    if (excedente > 0) excedenteAntesDoApp(grupo, conta, p, excedente, m.data, m.classificacao!!.natureza!!)
                }
            }
            Resultado.Sucesso(compra.id)
        }
    }

    private val SEM_ENCARGOS_COMPRA = "Categoria “Encargos financeiros” não encontrada."

    /** Marca usada nos dois ajustes do excedente pago antes do app (para corrigir depois). */
    private val marcaExcedente = "excedente-antes"

    private fun validarPagaAntes(numero: Int, valorPago: Long, data: LocalDate, classificacao: ClassificacaoDiferenca?, previsto: Long, ehPaga: Boolean): String? = when {
        !ehPaga -> "A parcela $numero não está entre as já pagas."
        valorPago < previsto -> "O valor pago da parcela $numero não pode ser menor que ela (${Dinheiro.formatar(previsto)}). Para pagar menos, use “pago em parte” ou parcela antecipada."
        data.isAfter(relogio.hoje()) -> "A data do pagamento da parcela $numero não pode ser depois de hoje."
        valorPago > previsto && (classificacao == null || classificacao.pagouMenos || classificacao.natureza == null) ->
            "Diga o que é a diferença de ${Dinheiro.formatar(valorPago - previsto)} na parcela $numero."
        else -> null
    }

    /**
     * v0.9 (item 9, regra 8.1): o excedente pago ANTES do app numa parcela. Vira despesa em
     * "Encargos financeiros" na conta de crédito, na data do pagamento, com o acréscimo na
     * parcela; um ajuste do mesmo valor e na mesma data registra que ele já foi pago (o devido
     * não muda). A categoria precisa existir (conferido antes).
     */
    private suspend fun excedenteAntesDoApp(grupo: String, conta: ContaEntidade, p: ObrigacaoEntidade, excedente: Long, data: LocalDate, natureza: NaturezaAjuste) {
        val agora = relogio.agora()
        val categoria = categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS)!!
        val despesa = MovimentacaoEntidade(
            relogio.novoId(), TipoMovimentacao.DESPESA.name, data.toString(), excedente, conta.id, null, categoria.id,
            "${natureza.rotulo}: ${p.descricao} (pago antes do app)", agora, agora, null,
        )
        movDao.inserir(despesa)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.MOVIMENTACAO, despesa.id,
            "Despesa de ${Dinheiro.formatar(excedente)} de ${natureza.rotulo.lowercase()} em “${p.descricao}” (pago antes do app)", MovimentacaoEntidade.serializer(), null, despesa)
        val pago = MovimentacaoEntidade(
            relogio.novoId(), TipoMovimentacao.AJUSTE.name, data.toString(), excedente, conta.id, null, null,
            "Pago antes do app: ${natureza.rotulo.lowercase()} de ${p.descricao}", agora, agora, null,
        )
        movDao.inserir(pago)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.MOVIMENTACAO, pago.id,
            "Ajuste de ${Dinheiro.formatarComSinal(excedente)} no ${conta.nome}: ${natureza.rotulo.lowercase()} pago antes do app", MovimentacaoEntidade.serializer(), null, pago)
        for ((nat, movId) in listOf(natureza to despesa.id, NaturezaAjuste.QUITADA_ANTES_DO_APP to pago.id)) {
            val a = ObrigacaoAjusteEntidade(relogio.novoId(), p.id, null, nat.name, excedente, movId, marcaExcedente, agora, agora, null)
            ajusteDao.inserir(a)
            auditor.registrar(grupo, Operacao.CRIACAO, Auditor.AJUSTE_OBRIGACAO, a.id,
                "Parcela “${p.descricao}”: ${nat.rotulo.lowercase()} de ${Dinheiro.formatar(excedente)} (pago antes do app em $data)", ObrigacaoAjusteEntidade.serializer(), null, a)
        }
    }

    /** v0.9 (item 9): a parcela foi quitada antes do app (inteira, sem antecipação nem pagamento no app)? */
    suspend fun pagaAntesDoApp(obrigacaoId: String): Boolean {
        val p = obrigacaoDao.buscar(obrigacaoId)?.takeIf { it.excluidoEm == null && it.compraId != null } ?: return false
        if (pagamentoDao.validosDa(p.id).isNotEmpty()) return false
        val originais = ajusteDao.validosDa(p.id).filter { it.natureza == NaturezaAjuste.QUITADA_ANTES_DO_APP.name && it.observacao == "" }
        return originais.sumOf { it.valorCentavos } == p.valorPrevistoCentavos
    }

    /** O valor pago antes do app hoje registrado na parcela (previsto + excedente). */
    suspend fun valorPagoAntesDoApp(obrigacaoId: String): Long? {
        if (!pagaAntesDoApp(obrigacaoId)) return null
        val p = obrigacaoDao.buscar(obrigacaoId)!!
        return p.valorPrevistoCentavos + ajusteDao.validosDa(p.id).filter { it.observacao == marcaExcedente && it.natureza == NaturezaAjuste.QUITADA_ANTES_DO_APP.name }.sumOf { it.valorCentavos }
    }

    /**
     * v0.9 (item 9, fluxo 3): corrige o valor pago ANTES do app numa parcela já marcada como
     * paga. O excedente anterior (se houver) é desfeito e o novo é registrado na [data].
     * Valor menor que a parcela é recusado (a regra de pago em parte e desconto não muda).
     */
    suspend fun corrigirPagoAntesDoApp(obrigacaoId: String, valorPago: Long, data: LocalDate, classificacao: ClassificacaoDiferenca?): Resultado<Unit> = banco.withTransaction {
        if (!pagaAntesDoApp(obrigacaoId)) return@withTransaction Resultado.Falha("Só dá para corrigir o valor de uma parcela paga inteira antes do app.")
        val p = obrigacaoDao.buscar(obrigacaoId)!!
        validarPagaAntes(p.numeroParcela ?: 1, valorPago, data, classificacao, p.valorPrevistoCentavos, true)?.let { return@withTransaction Resultado.Falha(it) }
        val excedente = valorPago - p.valorPrevistoCentavos
        if (excedente > 0 && categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) == null) return@withTransaction Resultado.Falha(SEM_ENCARGOS_COMPRA)
        val conta = contaDao.buscar(p.contaCreditoId!!)!!
        val grupo = relogio.novoId()
        val agora = relogio.agora()
        for (a in ajusteDao.validosDa(p.id).filter { it.observacao == marcaExcedente }) {
            val d = a.copy(excluidoEm = agora, atualizadoEm = agora)
            ajusteDao.atualizar(d)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.AJUSTE_OBRIGACAO, a.id, "Valor pago antes do app corrigido: ajuste de ${Dinheiro.formatar(a.valorCentavos)} desfeito", ObrigacaoAjusteEntidade.serializer(), a, d)
            a.movimentacaoId?.let { id ->
                movDao.buscar(id)?.takeIf { it.excluidoEm == null }?.let { m ->
                    val dm = m.copy(excluidoEm = agora, atualizadoEm = agora)
                    movDao.atualizar(dm)
                    auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.MOVIMENTACAO, m.id, "${TipoMovimentacao.valueOf(m.tipo).rotulo} “${m.descricao}” excluída (valor corrigido)", MovimentacaoEntidade.serializer(), m, dm)
                }
            }
        }
        if (excedente > 0) excedenteAntesDoApp(grupo, conta, p, excedente, data, classificacao!!.natureza!!)
        Resultado.Sucesso(Unit)
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
        // v0.8 (D2, regra 6G.4): a recusa diz exatamente o que trava (data, valor e fatura).
        val travas = travas(compra, parcelas)
        if (travas.isNotEmpty()) {
            return@withTransaction Resultado.Falha(
                "Esta compra tem pagamentos. Desfaça antes de excluir:\n" + travas.joinToString("\n") { "• " + it.descricao },
            )
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

    /** v0.8 (D2): o que impede excluir a compra: pagamentos de parcelas e abatimentos gerais das faturas dela. */
    suspend fun travasDaExclusao(compraId: String): List<TravaDaCompra> {
        val compra = compraDao.buscar(compraId)?.takeIf { it.excluidoEm == null } ?: return emptyList()
        return travas(compra, obrigacaoDao.daCompra(compraId))
    }

    private suspend fun travas(compra: CompraEntidade, parcelas: List<ObrigacaoEntidade>): List<TravaDaCompra> {
        val fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
        fun mes(m: String?) = m?.let { YearMonth.parse(it) }?.let { "%02d/%d".format(it.monthValue, it.year) } ?: ""
        val resultado = mutableListOf<TravaDaCompra>()
        for (p in parcelas) for (pg in pagamentoDao.validosDa(p.id)) {
            if (resultado.any { it.movimentacaoId == pg.movimentacaoId }) continue
            val mov = movDao.buscar(pg.movimentacaoId) ?: continue
            resultado += TravaDaCompra("Pagamento de ${Dinheiro.formatar(mov.valorCentavos)} em ${LocalDate.parse(mov.data).format(fmt)} na fatura de ${mes(p.mesReferencia)}", mov.id)
        }
        val meses = parcelas.mapNotNull { it.mesReferencia }.toSet()
        for (g in faturaDao.validosDaConta(compra.contaCreditoId).filter { it.mesReferencia in meses }) {
            if (resultado.any { it.movimentacaoId == g.movimentacaoId }) continue
            val mov = movDao.buscar(g.movimentacaoId) ?: continue
            resultado += TravaDaCompra("Abatimento de ${Dinheiro.formatar(g.valorCentavos)} em ${LocalDate.parse(mov.data).format(fmt)} na fatura de ${mes(g.mesReferencia)}", mov.id)
        }
        return resultado
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

/** v0.8 (D2): um pagamento ou abatimento que impede excluir a compra; desfazer pela transferência. */
data class TravaDaCompra(val descricao: String, val movimentacaoId: String)
