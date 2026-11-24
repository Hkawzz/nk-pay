package com.hkawzz.nkote.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Regras de contas a pagar (regras-financeiras.md 6A; ADR 0013). Valores fictícios. */
class ObrigacoesTest {

    private val dezembro = YearMonth.of(2026, 12)
    private val luz = Obrigacao("luz", OrigemObrigacao.FIXA, "Luz", 10_000, LocalDate.of(2026, 12, 10), dezembro)
    private fun pag(v: Long) = Pagamento("p$v", "luz", "m$v", v)
    private fun aj(n: NaturezaAjuste, v: Long) = AjusteObrigacao("a$v", "luz", null, n, v, null)

    @Test
    fun `sem pagamento e antes do vencimento fica pendente com o valor previsto`() {
        val s = Obrigacoes.situacao(luz, emptyList(), emptyList(), hoje = LocalDate.of(2026, 12, 5))
        assertEquals(10_000L, s.restante)
        assertEquals(StatusObrigacao.PENDENTE, s.status)
    }

    @Test
    fun `pagamento parcial deixa o restante pendente e status parcial`() {
        val s = Obrigacoes.situacao(luz, listOf(pag(6_000)), emptyList(), hoje = LocalDate.of(2026, 12, 5))
        assertEquals(4_000L, s.restante)
        assertEquals(StatusObrigacao.PARCIAL, s.status)
    }

    @Test
    fun `dois parciais que somam o previsto quitam`() {
        val s = Obrigacoes.situacao(luz, listOf(pag(6_000), pag(4_000)), emptyList(), hoje = LocalDate.of(2027, 1, 5))
        assertEquals(0L, s.restante)
        assertEquals(StatusObrigacao.QUITADA, s.status)
        // O valor previsto e o mês de referência originais não mudam.
        assertEquals(10_000L, s.obrigacao.valorPrevisto)
        assertEquals(dezembro, s.obrigacao.mesReferencia)
    }

    @Test
    fun `vencida e nao paga fica atrasada, mesmo com pagamento parcial`() {
        assertEquals(StatusObrigacao.ATRASADA, Obrigacoes.situacao(luz, emptyList(), emptyList(), LocalDate.of(2026, 12, 11)).status)
        assertEquals(StatusObrigacao.ATRASADA, Obrigacoes.situacao(luz, listOf(pag(1_000)), emptyList(), LocalDate.of(2026, 12, 11)).status)
        // No próprio dia do vencimento ainda não está atrasada.
        assertEquals(StatusObrigacao.PENDENTE, Obrigacoes.situacao(luz, emptyList(), emptyList(), LocalDate.of(2026, 12, 10)).status)
    }

    @Test
    fun `multa soma ao restante e e quitada pelo pagamento maior`() {
        val s = Obrigacoes.situacao(luz, listOf(pag(10_500)), listOf(aj(NaturezaAjuste.MULTA, 500)), LocalDate.of(2026, 12, 15))
        assertEquals(500L, s.acrescimos)
        assertEquals(0L, s.restante)
        assertEquals(StatusObrigacao.QUITADA, s.status)
    }

    @Test
    fun `desconto abate e quita sem pagar o previsto inteiro`() {
        val s = Obrigacoes.situacao(luz, listOf(pag(9_500)), listOf(aj(NaturezaAjuste.DESCONTO, 500)), LocalDate.of(2026, 12, 5))
        assertEquals(500L, s.abatimentos)
        assertEquals(0L, s.restante)
        assertEquals(StatusObrigacao.QUITADA, s.status)
    }

    @Test
    fun `pagamentos de outra obrigacao nao contam`() {
        val outro = Pagamento("x", "outra", "m", 10_000)
        assertEquals(10_000L, Obrigacoes.situacao(luz, listOf(outro), emptyList(), LocalDate.of(2026, 12, 5)).restante)
    }

    @Test
    fun `vencimento da conta fixa usa o ultimo dia quando o mes e curto`() {
        assertEquals(LocalDate.of(2027, 2, 28), Obrigacoes.vencimentoNoMes(31, YearMonth.of(2027, 2)))
        assertEquals(LocalDate.of(2026, 12, 10), Obrigacoes.vencimentoNoMes(10, dezembro))
        assertNull(Obrigacoes.vencimentoNoMes(null, dezembro))
    }

    @Test
    fun `so aparecem para lancar as fixas ativas ainda nao lancadas no mes`() {
        val fixas = listOf(
            ContaFixa("f1", "Luz", 10_000, 10),
            ContaFixa("f2", "Internet", 9_990, 15),
            ContaFixa("f3", "Antiga", 1_000, 1, arquivada = true),
        )
        val jaLancada = Obrigacao("o", OrigemObrigacao.FIXA, "Luz", 10_000, null, dezembro, contaFixaId = "f1")
        val emNovembro = jaLancada.copy(id = "o2", mesReferencia = dezembro.minusMonths(1), contaFixaId = "f2")
        assertEquals(listOf("f2"), Obrigacoes.fixasDisponiveisParaLancar(fixas, listOf(jaLancada, emNovembro), dezembro).map { it.id })
    }

    @Test
    fun `pendencias de meses anteriores continuam no mes original`() {
        val novembro = luz.copy(id = "nov", mesReferencia = dezembro.minusMonths(1), vencimento = LocalDate.of(2026, 11, 10))
        val paga = luz.copy(id = "paga", mesReferencia = dezembro.minusMonths(1))
        val v = VisaoAPagar(emptyList(), listOf(novembro, paga, luz), listOf(Pagamento("p", "paga", "m", 10_000)), emptyList())
        val hoje = LocalDate.of(2026, 12, 5)
        val pendentes = v.pendentesAnteriores(dezembro, hoje)
        assertEquals(listOf("nov"), pendentes.map { it.obrigacao.id })
        assertEquals(dezembro.minusMonths(1), pendentes.single().obrigacao.mesReferencia)
        assertEquals(StatusObrigacao.ATRASADA, pendentes.single().status)
        assertEquals(listOf("luz"), v.doMes(dezembro, hoje).map { it.obrigacao.id })
    }
}
