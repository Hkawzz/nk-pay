package com.hkawzz.nkote.dominio

import java.time.LocalDate
import java.time.YearMonth

// =====================================================================================
// Análises (v0.4). Definições: regras-financeiras.md, seção 6D. Funções puras: recebem
// as mesmas listas das outras telas (sem excluídos) e não gravam nada.
// =====================================================================================

/** Período das análises: do mês [inicio] ao mês [fim], inclusive. */
data class Periodo(val inicio: YearMonth, val fim: YearMonth) {
    val meses: List<YearMonth> get() = generateSequence(inicio) { it.plusMonths(1) }.takeWhile { it <= fim }.toList()
    operator fun contains(d: LocalDate): Boolean = YearMonth.from(d) in inicio..fim
}

/** Os filtros da tela (6D): mês atual (padrão) e os últimos 3, 6 ou 12 meses. */
enum class FiltroPeriodo(val rotulo: String, val meses: Long) {
    MES_ATUAL("Este mês", 1),
    TRES("3 meses", 3),
    SEIS("6 meses", 6),
    DOZE("12 meses", 12),
    ;

    fun periodo(hoje: LocalDate): Periodo {
        val fim = YearMonth.from(hoje)
        return Periodo(fim.minusMonths(meses - 1), fim)
    }
}

data class TotaisPeriodo(val entradas: Long, val despesas: Long) {
    val resultado: Long get() = entradas - despesas
}

data class FluxoFinanceiro(
    val entrou: Long,
    val saiu: Long,
    val pagamentosDeCredito: Long,
    /** Quantas movimentações formam as entradas e as saídas (Resumo da Início, v0.5). */
    val quantidadeEntradas: Int = 0,
    val quantidadeSaidas: Int = 0,
) {
    val saldo: Long get() = entrou - saiu
}

data class DespesaPorCategoria(val categoria: Categoria?, val total: Long, val percentual: Double)

data class SaldoMensal(val mes: YearMonth, val saldo: Long)

data class EvolucaoDoMes(val mes: YearMonth, val entradas: Long, val despesas: Long, val semDados: Boolean) {
    val resultado: Long get() = entradas - despesas
}

data class ResumoDeEmprestimos(
    val totalAPagar: Long,
    val totalAReceber: Long,
    /** Juros J1 e encargos de empréstimos recebidos, lançados no período. */
    val jurosPagos: Long,
    /** Juros K2 (e excedentes) recebidos no período. */
    val jurosRecebidos: Long,
    val parcelasAtrasadas: Int,
    val quantidade: Int,
)

data class ResumoDeCredito(
    val conta: Conta,
    val devido: Long,
    val limite: Long?,
    val limiteDisponivel: Long?,
    val faturasPagas: Int,
    val faturasEmAberto: Int,
    val faturasAtrasadas: Int,
    val comprasAnterioresAoApp: Long,
)

object Analises {

    /** Entradas e despesas (regra 3.3): compras no crédito contam no mês da compra. */
    fun totais(movs: List<Movimentacao>, periodo: Periodo): TotaisPeriodo {
        val doPeriodo = movs.filter { it.data in periodo }
        return TotaisPeriodo(
            doPeriodo.filter { it.tipo == TipoMovimentacao.ENTRADA }.sumOf { it.valor },
            doPeriodo.filter { it.tipo == TipoMovimentacao.DESPESA }.sumOf { it.valor },
        )
    }

    /**
     * Fluxo do dinheiro PRÓPRIO: o que entra e sai das contas PROPRIO. Transferências
     * entre duas contas próprias não contam; pagamentos de fatura/crediário saem.
     * Ajustes são correções e ficam fora. Benefícios ficam fora.
     */
    fun fluxo(contas: List<Conta>, movs: List<Movimentacao>, periodo: Periodo): FluxoFinanceiro {
        // v0.8: caixinhas também são dinheiro seu: guardar e retirar não entram no fluxo; rendimento entra.
        val proprias = contas.filter { it.natureza.ehDinheiroProprio }.map { it.id }.toSet()
        val credito = contas.filter { it.ehCredito }.map { it.id }.toSet()
        var entrou = 0L
        var saiu = 0L
        var pagamentos = 0L
        var nEntradas = 0
        var nSaidas = 0
        fun entra(v: Long) { entrou += v; nEntradas++ }
        fun sai(v: Long) { saiu += v; nSaidas++ }
        for (m in movs) {
            if (m.data !in periodo) continue
            val deProprio = m.contaId in proprias
            when (m.tipo) {
                TipoMovimentacao.ENTRADA -> if (deProprio) entra(m.valor)
                TipoMovimentacao.DESPESA -> if (deProprio) sai(m.valor)
                TipoMovimentacao.TRANSFERENCIA -> {
                    val paraProprio = m.contaDestinoId in proprias
                    when {
                        deProprio && !paraProprio -> {
                            sai(m.valor)
                            if (m.contaDestinoId in credito) pagamentos += m.valor
                        }
                        !deProprio && paraProprio -> entra(m.valor)
                    }
                }
                TipoMovimentacao.AJUSTE -> Unit
            }
        }
        return FluxoFinanceiro(entrou, saiu, pagamentos, nEntradas, nSaidas)
    }

    /** Despesas por categoria, do maior para o menor, com o percentual do total. */
    fun despesasPorCategoria(movs: List<Movimentacao>, categorias: List<Categoria>, periodo: Periodo): List<DespesaPorCategoria> {
        val despesas = movs.filter { it.tipo == TipoMovimentacao.DESPESA && it.data in periodo }
        val total = despesas.sumOf { it.valor }
        if (total == 0L) return emptyList()
        val porId = categorias.associateBy { it.id }
        return despesas.groupBy { it.categoriaId }
            .map { (id, ms) -> val t = ms.sumOf { it.valor }; DespesaPorCategoria(id?.let { porId[it] }, t, t * 100.0 / total) }
            .sortedByDescending { it.total }
    }

    /** Saldo de uma conta no fim de cada mês (saldo inicial + movimentações até o fim do mês). */
    fun saldosNoFimDoMes(conta: Conta, movs: List<Movimentacao>, periodo: Periodo): List<SaldoMensal> {
        val daConta = movs.filter { it.contaId == conta.id || it.contaDestinoId == conta.id }.sortedBy { it.data }
        return periodo.meses.map { mes ->
            val fim = mes.atEndOfMonth()
            SaldoMensal(mes, conta.saldoInicial + daConta.filter { it.data <= fim }.sumOf { Saldos.efeitoNaConta(it, conta.id) })
        }
    }

    /** Entradas, despesas e resultado mês a mês. Antes do primeiro registro: "sem dados". */
    fun evolucaoMensal(movs: List<Movimentacao>, periodo: Periodo): List<EvolucaoDoMes> {
        val primeiro = movs.minOfOrNull { it.data }?.let { YearMonth.from(it) }
        return periodo.meses.map { mes ->
            val t = totais(movs, Periodo(mes, mes))
            EvolucaoDoMes(mes, t.entradas, t.despesas, semDados = primeiro == null || mes < primeiro)
        }
    }

    fun credito(vc: VisaoCredito, hoje: LocalDate): List<ResumoDeCredito> = vc.contasDeCredito.filter { !it.arquivada }.map { c ->
        val faturas = vc.faturas(c.id, hoje)
        ResumoDeCredito(
            conta = c,
            devido = vc.devido(c.id),
            limite = c.limite,
            limiteDisponivel = vc.limiteDisponivel(c.id),
            faturasPagas = faturas.count { it.status == StatusObrigacao.QUITADA },
            faturasEmAberto = faturas.count { it.emAberto && it.status != StatusObrigacao.ATRASADA },
            faturasAtrasadas = faturas.count { it.status == StatusObrigacao.ATRASADA },
            comprasAnterioresAoApp = vc.totalComprasAnteriores(c.id),
        )
    }

    /**
     * Descontos obtidos no período: descontos e outros abatimentos dados em pagamentos
     * (data do pagamento). "Quitada antes do app" NUNCA entra (ADR 0016).
     */
    fun descontosObtidos(
        ajustes: List<AjusteObrigacao>,
        pagamentos: List<Pagamento>,
        movs: List<Movimentacao>,
        periodo: Periodo,
        /** v0.5: parcelas de empréstimo CONCEDIDO (os descontos delas são concedidos, não obtidos). */
        excluirObrigacoes: Set<String> = emptySet(),
    ): Long = somaDescontos(ajustes.filter { it.obrigacaoId !in excluirObrigacoes }, pagamentos, movs, periodo)

    /** v0.5 (6E.6): descontos que VOCÊ deu ao receber parcelas de empréstimos concedidos. */
    fun descontosConcedidos(ajustes: List<AjusteObrigacao>, pagamentos: List<Pagamento>, movs: List<Movimentacao>, periodo: Periodo, obrigacoesConcedidas: Set<String>): Long =
        somaDescontos(ajustes.filter { it.obrigacaoId in obrigacoesConcedidas }, pagamentos, movs, periodo)

    /** Datado pelo pagamento que criou o desconto ou, numa edição de parcela, pela movimentação do próprio ajuste. */
    private fun somaDescontos(ajustes: List<AjusteObrigacao>, pagamentos: List<Pagamento>, movs: List<Movimentacao>, periodo: Periodo): Long {
        val pagPorId = pagamentos.associateBy { it.id }
        val movPorId = movs.associateBy { it.id }
        return ajustes.filter { it.natureza.ehDesconto }.sumOf { a ->
            val data = a.pagamentoId?.let { pagPorId[it] }?.let { movPorId[it.movimentacaoId] }?.data
                ?: a.movimentacaoId?.let { movPorId[it] }?.data
            if (data != null && data in periodo) a.valor else 0L
        }
    }

    /**
     * v0.5: o resumo dos empréstimos no período. O valor emprestado nunca é receita nem
     * despesa; só os juros entram (J1 como despesa, K2 como receita).
     */
    fun emprestimos(ve: VisaoEmprestimos, periodo: Periodo, hoje: java.time.LocalDate): ResumoDeEmprestimos {
        val recebidos = ve.situacoes(TipoEmprestimo.RECEBIDO, hoje)
        val concedidos = ve.situacoes(TipoEmprestimo.CONCEDIDO, hoje)
        fun movsDe(l: List<SituacaoEmprestimo>) = l.flatMap { ve.movimentacoesDe(it.emprestimo) }.distinctBy { it.id }.filter { it.data in periodo }
        return ResumoDeEmprestimos(
            totalAPagar = recebidos.sumOf { it.restante },
            totalAReceber = concedidos.sumOf { it.restante },
            jurosPagos = movsDe(recebidos).filter { it.tipo == TipoMovimentacao.DESPESA }.sumOf { it.valor },
            jurosRecebidos = movsDe(concedidos).filter { it.tipo == TipoMovimentacao.ENTRADA }.sumOf { it.valor },
            parcelasAtrasadas = (recebidos + concedidos).sumOf { it.atrasadas.size },
            quantidade = recebidos.size + concedidos.size,
        )
    }
}
