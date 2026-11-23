package com.nkpay.app.dominio

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

data class FluxoFinanceiro(val entrou: Long, val saiu: Long, val pagamentosDeCredito: Long) {
    val saldo: Long get() = entrou - saiu
}

data class DespesaPorCategoria(val categoria: Categoria?, val total: Long, val percentual: Double)

data class SaldoMensal(val mes: YearMonth, val saldo: Long)

data class EvolucaoDoMes(val mes: YearMonth, val entradas: Long, val despesas: Long, val semDados: Boolean) {
    val resultado: Long get() = entradas - despesas
}

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
        val proprias = contas.filter { it.natureza == Natureza.PROPRIO }.map { it.id }.toSet()
        val credito = contas.filter { it.ehCredito }.map { it.id }.toSet()
        var entrou = 0L
        var saiu = 0L
        var pagamentos = 0L
        for (m in movs) {
            if (m.data !in periodo) continue
            val deProprio = m.contaId in proprias
            when (m.tipo) {
                TipoMovimentacao.ENTRADA -> if (deProprio) entrou += m.valor
                TipoMovimentacao.DESPESA -> if (deProprio) saiu += m.valor
                TipoMovimentacao.TRANSFERENCIA -> {
                    val paraProprio = m.contaDestinoId in proprias
                    when {
                        deProprio && !paraProprio -> {
                            saiu += m.valor
                            if (m.contaDestinoId in credito) pagamentos += m.valor
                        }
                        !deProprio && paraProprio -> entrou += m.valor
                    }
                }
                TipoMovimentacao.AJUSTE -> Unit
            }
        }
        return FluxoFinanceiro(entrou, saiu, pagamentos)
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
    fun descontosObtidos(ajustes: List<AjusteObrigacao>, pagamentos: List<Pagamento>, movs: List<Movimentacao>, periodo: Periodo): Long {
        val pagPorId = pagamentos.associateBy { it.id }
        val movPorId = movs.associateBy { it.id }
        return ajustes.filter { it.natureza.ehDesconto }.sumOf { a ->
            val data = a.pagamentoId?.let { pagPorId[it] }?.let { movPorId[it.movimentacaoId] }?.data
            if (data != null && data in periodo) a.valor else 0L
        }
    }
}
