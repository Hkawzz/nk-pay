package com.nkpay.app.dados

import androidx.room.withTransaction
import com.nkpay.app.dominio.CategoriasDoSistema
import com.nkpay.app.dominio.ClassificacaoDiferenca
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.NaturezaAjuste
import com.nkpay.app.dominio.ParteDoPagamento
import com.nkpay.app.dominio.Pagamentos
import com.nkpay.app.dominio.ResultadoPlano
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.dominio.VisaoCredito
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pagamento em MODO CRÉDITO (v0.4, ADR 0015). Separado do `PagamentoServico` das contas
 * fixas, que não muda. Aqui, pagar é sempre uma TRANSFERÊNCIA (conta própria → conta de
 * crédito), nunca uma despesa: a despesa já foi contada no mês da compra.
 *
 * - Desconto (crediário): AJUSTE positivo na conta de crédito + abatimento na parcela.
 * - Pagou a mais (8.1-a): DESPESA de encargo na conta de crédito, em "Encargos
 *   financeiros", + acréscimo na parcela. Só com a classificação da pessoa.
 * - Desfazer: exclui (logicamente) a transferência e tudo o que ela criou.
 *
 * Tudo em uma transação e em um grupo do Registro de alterações.
 * @return sempre o id da transferência (é por ela que o pagamento é desfeito).
 */
class PagamentoCreditoServico(
    private val banco: NkPayBanco,
    private val auditor: Auditor,
    private val relogio: Relogio,
) {
    private val contaDao = banco.contaDao()
    private val movDao = banco.movimentacaoDao()
    private val obrigacaoDao = banco.obrigacaoDao()
    private val pagamentoDao = banco.pagamentoDao()
    private val ajusteDao = banco.ajusteObrigacaoDao()
    private val faturaDao = banco.pagamentoFaturaDao()
    private val categoriaDao = banco.categoriaDao()

    /** A visão de crédito de UMA conta, lida do banco dentro da transação. */
    private suspend fun visao(contaCreditoId: String): VisaoCredito {
        val contas = banco.backupDao().contas().filter { it.excluidoEm == null }.map { it.paraDominio() }
        val movs = banco.backupDao().movimentacoes().filter { it.excluidoEm == null && (it.contaId == contaCreditoId || it.contaDestinoId == contaCreditoId) }.map { it.paraDominio() }
        val parcelas = obrigacaoDao.parcelasDaConta(contaCreditoId).map { it.paraDominio() }
        val ids = parcelas.map { it.id }.toSet()
        return VisaoCredito(
            contas, movs, parcelas,
            pagamentoDao.validosTodos().filter { it.obrigacaoId in ids }.map { it.paraDominio() },
            ajusteDao.validosTodos().filter { it.obrigacaoId in ids }.map { it.paraDominio() },
            faturaDao.validosDaConta(contaCreditoId).map { it.paraDominio() },
            emptyList(),
        )
    }

    private companion object {
        const val SEM_ENCARGOS = "Categoria “Encargos financeiros” não encontrada."
    }

    private class Contexto(val grupo: String, val agora: Long, val data: LocalDate)

    private suspend fun contaOrigemValida(id: String): ContaEntidade? =
        contaDao.buscar(id)?.takeIf { it.excluidoEm == null && !Natureza.valueOf(it.natureza).ehContaDeTerceiros }

    private suspend fun Contexto.mov(tipo: TipoMovimentacao, valor: Long, contaId: String, destino: String?, categoriaId: String?, descricao: String, resumo: String): MovimentacaoEntidade {
        val m = MovimentacaoEntidade(relogio.novoId(), tipo.name, data.toString(), valor, contaId, destino, categoriaId, descricao, agora, agora, null)
        movDao.inserir(m)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.MOVIMENTACAO, m.id, resumo, MovimentacaoEntidade.serializer(), null, m)
        return m
    }

    private suspend fun Contexto.ligar(obrigacao: ObrigacaoEntidade, movId: String, valor: Long): PagamentoEntidade {
        val p = PagamentoEntidade(relogio.novoId(), obrigacao.id, movId, valor, agora, agora, null)
        pagamentoDao.inserir(p)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.PAGAMENTO, p.id, "Pagamento de ${Dinheiro.formatar(valor)} em “${obrigacao.descricao}”", PagamentoEntidade.serializer(), null, p)
        return p
    }

    private suspend fun Contexto.ajustar(obrigacao: ObrigacaoEntidade, pagamentoId: String, natureza: NaturezaAjuste, valor: Long, movId: String) {
        val a = ObrigacaoAjusteEntidade(relogio.novoId(), obrigacao.id, pagamentoId, natureza.name, valor, movId, "", agora, agora, null)
        ajusteDao.inserir(a)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.AJUSTE_OBRIGACAO, a.id, "${natureza.rotulo} de ${Dinheiro.formatar(valor)} em “${obrigacao.descricao}”", ObrigacaoAjusteEntidade.serializer(), null, a)
    }

    /** Despesa de encargo na conta de crédito + acréscimo ligado ao pagamento. */
    private suspend fun Contexto.encargo(credito: ContaEntidade, obrigacao: ObrigacaoEntidade, pagamentoId: String, classificacao: ClassificacaoDiferenca, valor: Long): Resultado.Falha? {
        val categoria = categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) ?: return Resultado.Falha(SEM_ENCARGOS)
        val natureza = classificacao.natureza!!
        val e = mov(TipoMovimentacao.DESPESA, valor, credito.id, null, categoria.id, "${natureza.rotulo}: ${credito.nome}",
            "Despesa de ${Dinheiro.formatar(valor)} de ${natureza.rotulo.lowercase()} no ${credito.nome}")
        ajustar(obrigacao, pagamentoId, natureza, valor, e.id)
        return null
    }

    // ------------------------------------------------------------------ parcela (crediário)

    /** Paga UMA parcela (crediário), com as perguntas da v0.3 (parcial, desconto, encargos). */
    suspend fun pagarParcela(
        obrigacaoId: String, contaOrigemId: String, data: LocalDate, valorPago: Long, classificacao: ClassificacaoDiferenca?,
    ): Resultado<String> = banco.withTransaction {
        val o = obrigacaoDao.buscar(obrigacaoId)?.takeIf { it.excluidoEm == null && it.contaCreditoId != null }
            ?: return@withTransaction Resultado.Falha("Parcela não encontrada.")
        val credito = contaDao.buscar(o.contaCreditoId!!)!!
        if (Natureza.valueOf(credito.natureza) == Natureza.CARTAO) return@withTransaction Resultado.Falha("Parcelas de cartão são pagas pela fatura.")
        val origem = contaOrigemValida(contaOrigemId) ?: return@withTransaction Resultado.Falha("Escolha a conta de onde sai o dinheiro.")
        val restante = visao(credito.id).situacaoDaParcela(o.id, relogio.hoje())?.restante ?: 0
        val plano = when (val r = Pagamentos.planejar(restante, valorPago, classificacao)) {
            is ResultadoPlano.Pronto -> r.plano
            is ResultadoPlano.PrecisaClassificar -> return@withTransaction Resultado.Falha("Diga o que é a diferença de ${Dinheiro.formatar(r.diferenca)}.")
            is ResultadoPlano.Invalido -> return@withTransaction Resultado.Falha(r.mensagem)
        }
        // Confere ANTES de gravar qualquer coisa (uma falha no meio não pode deixar meio pagamento).
        if (plano.encargo > 0 && categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) == null) {
            return@withTransaction Resultado.Falha(SEM_ENCARGOS)
        }
        val c = Contexto(relogio.novoId(), relogio.agora(), data)
        with(c) {
            val t = mov(TipoMovimentacao.TRANSFERENCIA, plano.totalPago, origem.id, credito.id, null, "Pagamento: ${o.descricao}",
                "Transferência de ${Dinheiro.formatar(plano.totalPago)} ${origem.nome} → ${credito.nome} (pagamento de “${o.descricao}”)")
            val p = ligar(o, t.id, plano.totalPago)
            if (plano.encargo > 0) encargo(credito, o, p.id, plano.classificacao!!, plano.encargo)?.let { return@withTransaction it }
            if (plano.abatimento > 0) {
                val natureza = plano.classificacao!!.natureza!!
                val d = mov(TipoMovimentacao.AJUSTE, plano.abatimento, credito.id, null, null, "${natureza.rotulo}: ${o.descricao}",
                    "Ajuste de ${Dinheiro.formatarComSinal(plano.abatimento)} no ${credito.nome} (${natureza.rotulo.lowercase()})")
                ajustar(o, p.id, natureza, plano.abatimento, d.id)
            }
            Resultado.Sucesso(t.id)
        }
    }

    // ------------------------------------------------------------------ fatura (cartão)

    /**
     * Paga [valor] no cartão: cobre as faturas em aberto até [ateMes], da mais antiga para
     * a mais nova (8.5). Fatura coberta inteira → ligações com as parcelas; coberta em parte
     * → abatimento geral. O que passar do total em aberto precisa de [classificacao] (encargo).
     */
    suspend fun pagarFatura(
        contaCreditoId: String, ateMes: YearMonth, contaOrigemId: String, data: LocalDate, valor: Long, classificacao: ClassificacaoDiferenca?,
    ): Resultado<String> = banco.withTransaction {
        if (valor <= 0) return@withTransaction Resultado.Falha("O valor pago precisa ser maior que zero.")
        val credito = contaDao.buscar(contaCreditoId)?.takeIf { it.excluidoEm == null && it.natureza == Natureza.CARTAO.name }
            ?: return@withTransaction Resultado.Falha("Cartão não encontrado.")
        val origem = contaOrigemValida(contaOrigemId) ?: return@withTransaction Resultado.Falha("Escolha a conta de onde sai o dinheiro.")
        val dist = visao(credito.id).distribuir(credito.id, valor, ateMes, relogio.hoje())
        if (dist.partes.isEmpty()) return@withTransaction Resultado.Falha("Não há fatura em aberto até ${ateMes}.")
        val classificacaoValida = classificacao?.takeIf { it in ClassificacaoDiferenca.quandoPagaMais }
        if (dist.excedente > 0 && classificacaoValida == null) {
            return@withTransaction Resultado.Falha("Diga o que é a diferença de ${Dinheiro.formatar(dist.excedente)}.")
        }
        if (dist.excedente > 0 && categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) == null) {
            return@withTransaction Resultado.Falha(SEM_ENCARGOS)
        }
        val c = Contexto(relogio.novoId(), relogio.agora(), data)
        with(c) {
            val t = mov(TipoMovimentacao.TRANSFERENCIA, valor, origem.id, credito.id, null, "Fatura: ${credito.nome}",
                "Transferência de ${Dinheiro.formatar(valor)} ${origem.nome} → ${credito.nome} (pagamento de fatura)")
            // O excedente fica com a 1ª parcela da ÚLTIMA fatura coberta inteira.
            val ultimaInteira = dist.partes.filterIsInstance<ParteDoPagamento.FaturaInteira>().lastOrNull()
            for (parte in dist.partes) when (parte) {
                is ParteDoPagamento.FaturaInteira -> parte.aplicacoes.forEachIndexed { i, (obrigacaoId, aplicado) ->
                    val o = obrigacaoDao.buscar(obrigacaoId)!!
                    val comExcedente = dist.excedente > 0 && parte === ultimaInteira && i == 0
                    val p = ligar(o, t.id, aplicado + if (comExcedente) dist.excedente else 0)
                    if (comExcedente) encargo(credito, o, p.id, classificacaoValida!!, dist.excedente)?.let { return@withTransaction it }
                }
                is ParteDoPagamento.AbatimentoGeral -> {
                    val g = PagamentoFaturaEntidade(relogio.novoId(), credito.id, parte.mes.toString(), t.id, parte.valor, agora, agora, null)
                    faturaDao.inserir(g)
                    auditor.registrar(grupo, Operacao.CRIACAO, Auditor.PAGAMENTO_FATURA, g.id,
                        "Abatimento de ${Dinheiro.formatar(parte.valor)} na fatura de ${parte.mes} do ${credito.nome}", PagamentoFaturaEntidade.serializer(), null, g)
                }
            }
            Resultado.Sucesso(t.id)
        }
    }

    /** Paga só as compras escolhidas de uma fatura: o restante exato de cada parcela. */
    suspend fun pagarCompras(
        contaCreditoId: String, mes: YearMonth, parcelas: Collection<String>, contaOrigemId: String, data: LocalDate,
    ): Resultado<String> = banco.withTransaction {
        if (parcelas.isEmpty()) return@withTransaction Resultado.Falha("Escolha pelo menos uma compra.")
        val credito = contaDao.buscar(contaCreditoId)?.takeIf { it.excluidoEm == null && it.natureza == Natureza.CARTAO.name }
            ?: return@withTransaction Resultado.Falha("Cartão não encontrado.")
        val origem = contaOrigemValida(contaOrigemId) ?: return@withTransaction Resultado.Falha("Escolha a conta de onde sai o dinheiro.")
        val v = visao(credito.id)
        val fatura = v.fatura(credito.id, mes, relogio.hoje()) ?: return@withTransaction Resultado.Falha("Fatura não encontrada.")
        val escolhidas = fatura.parcelas.filter { it.obrigacao.id in parcelas && it.restante > 0 }
        if (escolhidas.size != parcelas.toSet().size) return@withTransaction Resultado.Falha("Alguma compra escolhida já está paga ou não é desta fatura.")
        val total = escolhidas.sumOf { it.restante }
        if (total > fatura.restante) {
            return@withTransaction Resultado.Falha("A fatura já tem ${Dinheiro.formatar(fatura.abatimentosGerais)} abatidos. Pague o restante da fatura (${Dinheiro.formatar(fatura.restante)}).")
        }
        val c = Contexto(relogio.novoId(), relogio.agora(), data)
        with(c) {
            val t = mov(TipoMovimentacao.TRANSFERENCIA, total, origem.id, credito.id, null, "Fatura: ${credito.nome}",
                "Transferência de ${Dinheiro.formatar(total)} ${origem.nome} → ${credito.nome} (compras escolhidas)")
            for (s in escolhidas) ligar(obrigacaoDao.buscar(s.obrigacao.id)!!, t.id, s.restante)
            Resultado.Sucesso(t.id)
        }
    }

    // ------------------------------------------------------------------ desfazer

    /** Desfaz um pagamento de crédito inteiro, pela transferência que o fez. */
    suspend fun desfazer(transferenciaId: String): Resultado<Unit> = banco.withTransaction {
        val t = movDao.buscar(transferenciaId)?.takeIf { it.excluidoEm == null && it.tipo == TipoMovimentacao.TRANSFERENCIA.name }
            ?: return@withTransaction Resultado.Falha("Pagamento não encontrado.")
        val destino = t.contaDestinoId?.let { contaDao.buscar(it) }
        if (destino == null || !Natureza.valueOf(destino.natureza).ehCredito) return@withTransaction Resultado.Falha("Esta transferência não é um pagamento de cartão ou crediário.")
        val grupo = relogio.novoId()
        val agora = relogio.agora()
        suspend fun excluirMov(m: MovimentacaoEntidade) {
            if (m.excluidoEm != null) return
            val d = m.copy(excluidoEm = agora, atualizadoEm = agora)
            movDao.atualizar(d)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.MOVIMENTACAO, m.id, "${TipoMovimentacao.valueOf(m.tipo).rotulo} de ${Dinheiro.formatar(m.valorCentavos)} “${m.descricao}” excluída", MovimentacaoEntidade.serializer(), m, d)
        }
        for (p in pagamentoDao.daMovimentacao(t.id)) {
            for (a in ajusteDao.doPagamento(p.id)) {
                val d = a.copy(excluidoEm = agora, atualizadoEm = agora)
                ajusteDao.atualizar(d)
                auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.AJUSTE_OBRIGACAO, a.id, "Ajuste de ${Dinheiro.formatar(a.valorCentavos)} excluído", ObrigacaoAjusteEntidade.serializer(), a, d)
                a.movimentacaoId?.let { id -> movDao.buscar(id)?.let { excluirMov(it) } }
            }
            val d = p.copy(excluidoEm = agora, atualizadoEm = agora)
            pagamentoDao.atualizar(d)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.PAGAMENTO, p.id, "Pagamento de ${Dinheiro.formatar(p.valorAplicadoCentavos)} excluído", PagamentoEntidade.serializer(), p, d)
        }
        for (g in faturaDao.daMovimentacao(t.id)) {
            val d = g.copy(excluidoEm = agora, atualizadoEm = agora)
            faturaDao.atualizar(d)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.PAGAMENTO_FATURA, g.id, "Abatimento de ${Dinheiro.formatar(g.valorCentavos)} na fatura de ${g.mesReferencia} excluído", PagamentoFaturaEntidade.serializer(), g, d)
        }
        excluirMov(t)
        Resultado.Sucesso(Unit)
    }
}
