package com.nkpay.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Análises (regras-financeiras.md, 6D), com o exemplo do documento. Valores fictícios. */
class AnalisesTest {
    private fun d(m: Int, dia: Int, a: Int = 2026) = LocalDate.of(a, m, dia)
    private val pix = Conta("pix", "Pix", Natureza.PROPRIO, null, 100_000, d(11, 1))
    private val din = Conta("din", "Dinheiro", Natureza.PROPRIO, null, 0, d(11, 1))
    private val va = Conta("va", "VA", Natureza.BENEFICIO, TipoBeneficio.VA, 20_000, d(11, 1))
    private val cartao = Conta("cart", "Cartão", Natureza.CARTAO, null, 0, d(11, 1), diaFechamento = 5, diaVencimento = 12)
    private val contas = listOf(pix, din, va, cartao)
    private val alim = Categoria("alim", "Alimentação")
    private val lazer = Categoria("laz", "Lazer")
    private val dez = Periodo(YearMonth.of(2026, 12), YearMonth.of(2026, 12))

    /** O exemplo da regra 6D (dezembro). */
    private val movs = listOf(
        Movimentacao("e", TipoMovimentacao.ENTRADA, d(12, 5), 300_000, "pix"),
        Movimentacao("m", TipoMovimentacao.DESPESA, d(12, 6), 20_000, "pix", categoriaId = "alim"),
        Movimentacao("c", TipoMovimentacao.DESPESA, d(12, 7), 60_000, "cart", categoriaId = "laz"),
        Movimentacao("f", TipoMovimentacao.TRANSFERENCIA, d(12, 12), 30_000, "pix", contaDestinoId = "cart"),
        Movimentacao("t", TipoMovimentacao.TRANSFERENCIA, d(12, 13), 10_000, "pix", contaDestinoId = "din"),
    )

    @Test
    fun `entradas despesas e resultado do exemplo`() {
        val t = Analises.totais(movs, dez)
        assertEquals(300_000L, t.entradas)
        assertEquals(80_000L, t.despesas)
        assertEquals(220_000L, t.resultado)
    }

    @Test
    fun `fluxo do dinheiro proprio do exemplo ignora transferencia entre proprias`() {
        val f = Analises.fluxo(contas, movs, dez)
        assertEquals(300_000L, f.entrou)
        assertEquals(50_000L, f.saiu) // 200 do mercado + 300 da fatura
        assertEquals(30_000L, f.pagamentosDeCredito)
        // Despesa paga com benefício e ajustes não entram no fluxo próprio.
        val extra = movs + Movimentacao("b", TipoMovimentacao.DESPESA, d(12, 8), 5_000, "va") + Movimentacao("a", TipoMovimentacao.AJUSTE, d(12, 9), -700, "pix")
        assertEquals(50_000L, Analises.fluxo(contas, extra, dez).saiu)
    }

    @Test
    fun `despesas por categoria do maior para o menor com percentual`() {
        val l = Analises.despesasPorCategoria(movs, listOf(alim, lazer), dez)
        assertEquals(listOf("Lazer", "Alimentação"), l.map { it.categoria?.nome })
        assertEquals(75.0, l[0].percentual, 0.001)
        assertTrue(Analises.despesasPorCategoria(emptyList(), listOf(alim), dez).isEmpty())
        val semCategoria = Analises.despesasPorCategoria(listOf(Movimentacao("x", TipoMovimentacao.DESPESA, d(12, 1), 100, "pix")), listOf(alim), dez)
        assertNull(semCategoria.single().categoria)
    }

    @Test
    fun `saldo no fim de cada mes e evolucao com meses sem dados`() {
        val periodo = Periodo(YearMonth.of(2026, 10), YearMonth.of(2026, 12))
        val saldos = Analises.saldosNoFimDoMes(pix, movs, periodo)
        assertEquals(listOf(100_000L, 100_000L, 100_000L + 300_000 - 20_000 - 30_000 - 10_000), saldos.map { it.saldo })
        val ev = Analises.evolucaoMensal(movs, periodo)
        assertTrue(ev[0].semDados)
        assertTrue(ev[1].semDados)
        assertFalse(ev[2].semDados)
        assertEquals(220_000L, ev[2].resultado)
    }

    @Test
    fun `filtros de periodo`() {
        val hoje = d(12, 15)
        assertEquals(listOf(YearMonth.of(2026, 12)), FiltroPeriodo.MES_ATUAL.periodo(hoje).meses)
        assertEquals(YearMonth.of(2026, 10), FiltroPeriodo.TRES.periodo(hoje).inicio)
        assertEquals(12, FiltroPeriodo.DOZE.periodo(hoje).meses.size)
        assertEquals(YearMonth.of(2026, 1), FiltroPeriodo.DOZE.periodo(hoje).inicio)
    }

    @Test
    fun `descontos obtidos excluem quitada antes do app`() {
        val pags = listOf(Pagamento("p", "o", "m", 9_500))
        val ajustes = listOf(
            AjusteObrigacao("a1", "o", "p", NaturezaAjuste.DESCONTO, 500, null),
            AjusteObrigacao("a2", "o2", null, NaturezaAjuste.QUITADA_ANTES_DO_APP, 40_000, "aj"),
            AjusteObrigacao("a3", "o", "p", NaturezaAjuste.MULTA, 300, "e"),
        )
        assertEquals(500L, Analises.descontosObtidos(ajustes, pags, movs, dez))
    }

    @Test
    fun `resumo de credito`() {
        val o = Obrigacao("o", OrigemObrigacao.PARCELA, "X", 60_000, d(12, 12), YearMonth.of(2026, 12), contaCreditoId = "cart", compraId = "c1", numeroParcela = 1, totalParcelas = 1)
        val vc = VisaoCredito(contas, movs, listOf(o), listOf(Pagamento("pg", "o", "f", 30_000)), emptyList(), emptyList(), emptyList())
        val r = Analises.credito(vc, d(12, 20)).single()
        assertEquals(30_000L, r.devido)
        assertEquals(1, r.faturasAtrasadas)
        assertEquals(0, r.faturasPagas)
    }
}
