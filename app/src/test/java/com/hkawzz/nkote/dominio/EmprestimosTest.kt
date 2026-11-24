package com.hkawzz.nkote.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Domínio de empréstimos (regras 6E.4 a 6E.7, D2, D3, D5). Valores fictícios. */
class EmprestimosTest {
    private val d = LocalDate.of(2026, 12, 10)
    private val pix = Conta("pix", "Pix", Natureza.PROPRIO, null, 200_000, d)
    private val banco = Conta("ban", "Banco X", Natureza.EMPRESTIMO_RECEBIDO, null, 0, d)
    private val tonho = Conta("ton", "Tonho", Natureza.EMPRESTIMO_CONCEDIDO, null, 0, d)
    private val contas = listOf(pix, banco, tonho)

    private fun parcelas(emprestimo: String, conta: String, n: Int, valor: Long) = (1..n).map { k ->
        val venc = YearMonth.of(2027, 1).plusMonths(k - 1L).atDay(10)
        Obrigacao("$emprestimo-$k", OrigemObrigacao.EMPRESTIMO, "P $k/$n", valor, venc, YearMonth.from(venc), contaCreditoId = conta, numeroParcela = k, totalParcelas = n, emprestimoId = emprestimo)
    }

    // ------------------------------------------------------------ recebido (J1)

    private val recebido = Emprestimo("er", "ban", "Empréstimo", null, 100_000, d, "pix", false, null, "tc", "tj")
    private val movsRecebido = listOf(
        Movimentacao("tc", TipoMovimentacao.TRANSFERENCIA, d, 100_000, "ban", contaDestinoId = "pix"),
        Movimentacao("tj", TipoMovimentacao.DESPESA, d, 10_000, "ban"),
    )

    @Test
    fun `recebido J1 divida 1100 e 10 pagamentos de 110 zeram sem despesa nova`() {
        val ps = parcelas("er", "ban", 10, 11_000)
        var movs = movsRecebido
        val pags = mutableListOf<Pagamento>()
        fun v() = VisaoEmprestimos(contas, movs, listOf(recebido), ps, pags, emptyList())
        val s0 = v().situacao("er", d)!!
        assertEquals(110_000L, s0.saldoNaConta)
        assertEquals(10_000L, s0.jurosPrevistos)
        assertTrue(s0.invarianteFecha)
        assertTrue(v().invarianteDaConta("ban", d))
        for ((i, p) in ps.withIndex()) {
            movs = movs + Movimentacao("pg$i", TipoMovimentacao.TRANSFERENCIA, d, 11_000, "pix", contaDestinoId = "ban")
            pags += Pagamento("l$i", p.id, "pg$i", 11_000)
            assertTrue(v().situacao("er", d)!!.invarianteFecha)
        }
        val fim = v().situacao("er", d)!!
        assertEquals(0L, fim.saldoNaConta)
        assertTrue(fim.quitado)
        // A única despesa é a dos juros, uma vez.
        assertEquals(10_000L, Analises.totais(movs, Periodo(YearMonth.of(2026, 12), YearMonth.of(2027, 12))).despesas)
    }

    // ------------------------------------------------------------ concedido (K2)

    private val concedido = Emprestimo("ec", "ton", "Para o Tonho", null, 100_000, d, "pix", false, null, "cc", null)

    /** Simula o recebimento de [valor] na [parcela], como o serviço vai gravar. */
    private class Simulacao(val e: Emprestimo, val ps: List<Obrigacao>, val contas: List<Conta>) {
        var movs = listOf(Movimentacao("cc", TipoMovimentacao.TRANSFERENCIA, LocalDate.of(2026, 12, 10), e.valorOriginal, "pix", contaDestinoId = e.contaId))
        val pags = mutableListOf<Pagamento>()
        val ajustes = mutableListOf<AjusteObrigacao>()
        var n = 0
        fun v() = VisaoEmprestimos(contas, movs, listOf(e), ps, pags, ajustes)
        fun situacao() = v().situacao(e.id, LocalDate.of(2026, 12, 10))!!
        fun receber(parcela: Obrigacao, valor: Long): Long {
            val s = situacao()
            val j = Emprestimos.jurosDoRecebimento(valor, s.restante, s.jurosNaoReconhecidos)
            n++
            movs = movs + Movimentacao("r$n", TipoMovimentacao.TRANSFERENCIA, LocalDate.of(2027, 1, 10), valor - j, e.contaId, contaDestinoId = "pix")
            pags += Pagamento("l$n", parcela.id, "r$n", valor)
            if (j > 0) {
                movs = movs + Movimentacao("j$n", TipoMovimentacao.ENTRADA, LocalDate.of(2027, 1, 10), j, "pix")
                ajustes += AjusteObrigacao("a$n", parcela.id, "l$n", NaturezaAjuste.JUROS_RECEBIDOS, j, "j$n")
            }
            return j
        }
    }

    @Test
    fun `concedido K2 cada parcela de 110 e 100 de devolucao mais 10 de juros`() {
        val sim = Simulacao(concedido, parcelas("ec", "ton", 10, 11_000), contas)
        val s0 = sim.situacao()
        assertEquals(100_000L, s0.saldoNaConta)
        assertEquals(10_000L, s0.jurosNaoReconhecidos)
        var juros = 0L
        for (p in sim.ps) {
            val j = sim.receber(p, 11_000)
            assertEquals(1_000L, j)
            juros += j
            assertTrue(sim.situacao().invarianteFecha)
            assertTrue(sim.v().invarianteDaConta("ton", LocalDate.of(2026, 12, 10)))
        }
        val fim = sim.situacao()
        assertEquals(0L, fim.saldoNaConta)
        assertEquals(10_000L, juros)
        assertEquals(10_000L, fim.jurosRecebidos)
        // Nenhuma despesa; as entradas são só os juros.
        val t = Analises.totais(sim.movs, Periodo(YearMonth.of(2026, 12), YearMonth.of(2027, 12)))
        assertEquals(0L, t.despesas)
        assertEquals(10_000L, t.entradas)
    }

    @Test
    fun `concedido com centavos nao divisiveis fecha os juros exatos`() {
        // R$ 1.000,00 em 3 × R$ 350,00: juros de R$ 50,00 (16,67 por parcela).
        val e = concedido.copy(id = "ex")
        val sim = Simulacao(e, parcelas("ex", "ton", 3, 35_000), contas)
        val js = sim.ps.map { sim.receber(it, 35_000) }
        assertEquals(5_000L, js.sum())
        assertEquals(0L, sim.situacao().saldoNaConta)
        assertTrue(sim.situacao().invarianteFecha)
    }

    @Test
    fun `recebimento parcial segue a proporcao e depois quita`() {
        val sim = Simulacao(concedido, parcelas("ec", "ton", 10, 11_000), contas)
        assertEquals(500L, sim.receber(sim.ps[0], 5_500)) // R$ 55 = 50 + 5
        assertEquals(5_500L, sim.situacao().parcelas[0].restante)
        assertTrue(sim.situacao().invarianteFecha)
        assertEquals(500L, sim.receber(sim.ps[0], 5_500))
        assertEquals(0L, sim.situacao().parcelas[0].restante)
        assertEquals(90_000L, sim.situacao().saldoNaConta)
    }

    @Test
    fun `anterior ao app concedido com juros informados e recebido sem juros`() {
        // Concedido anterior (D5): 4 pendentes de R$ 110,00 com R$ 40,00 de juros contidos.
        val ant = Emprestimo("ea", "ton", "Antigo", null, 0, d, null, true, 4_000, "aj", null)
        val ps = parcelas("ea", "ton", 4, 11_000)
        val v = VisaoEmprestimos(contas, listOf(Movimentacao("aj", TipoMovimentacao.AJUSTE, d, 40_000, "ton")), listOf(ant), ps, emptyList(), emptyList())
        val s = v.situacao("ea", d)!!
        assertEquals(40_000L, s.saldoNaConta)
        assertEquals(4_000L, s.jurosNaoReconhecidos)
        assertTrue(s.invarianteFecha)
        // Com juros informados = 0, tudo é devolução.
        val zero = VisaoEmprestimos(contas, listOf(Movimentacao("aj", TipoMovimentacao.AJUSTE, d, 44_000, "ton")), listOf(ant.copy(jurosInformados = 0)), ps, emptyList(), emptyList())
        assertEquals(0L, zero.situacao("ea", d)!!.jurosNaoReconhecidos)
        // Recebido anterior: ajuste −Σ pendentes, sem juros e sem despesa.
        val r = Emprestimo("eb", "ban", "Banco antigo", null, 0, d, null, true, null, "ab", null)
        val vr = VisaoEmprestimos(contas, listOf(Movimentacao("ab", TipoMovimentacao.AJUSTE, d, -33_000, "ban")), listOf(r), parcelas("eb", "ban", 3, 11_000), emptyList(), emptyList())
        assertEquals(33_000L, vr.situacao("eb", d)!!.saldoNaConta)
        assertEquals(0L, vr.situacao("eb", d)!!.jurosPrevistos)
        assertTrue(vr.situacao("eb", d)!!.invarianteFecha)
    }

    @Test
    fun `atraso em dias ate a quitacao sem juros automaticos`() {
        val venc = LocalDate.of(2027, 1, 10)
        assertEquals(0, Emprestimos.diasDeAtraso(venc, 11_000, venc))
        assertEquals(5, Emprestimos.diasDeAtraso(venc, 11_000, venc.plusDays(5)))
        assertEquals(0, Emprestimos.diasDeAtraso(venc, 0, venc.plusDays(5)))
        val sim = Simulacao(concedido, parcelas("ec", "ton", 2, 55_000), contas)
        val s = sim.v().situacao("ec", LocalDate.of(2027, 1, 15))!!
        assertEquals(1, s.atrasadas.size)
        assertEquals(5, s.atrasadas.single().diasDeAtraso)
        assertEquals(110_000L, s.restante) // nada foi acrescentado sozinho
    }

    @Test
    fun `juros recebidos nao mexem no restante e tipos e terceiros`() {
        assertFalse(NaturezaAjuste.JUROS_RECEBIDOS.afetaRestante)
        val o = parcelas("ec", "ton", 1, 11_000).single()
        val s = Obrigacoes.situacao(o, emptyList(), listOf(AjusteObrigacao("a", o.id, null, NaturezaAjuste.JUROS_RECEBIDOS, 1_000, null)), d)
        assertEquals(11_000L, s.restante)
        assertEquals(TipoEmprestimo.RECEBIDO, TipoEmprestimo.de(Natureza.EMPRESTIMO_RECEBIDO))
        assertNull(TipoEmprestimo.de(Natureza.CARTAO))
        for (n in listOf(Natureza.CARTAO, Natureza.CREDIARIO, Natureza.EMPRESTIMO_RECEBIDO, Natureza.EMPRESTIMO_CONCEDIDO)) {
            assertTrue(n.ehContaDeTerceiros)
            assertFalse(Validacao.aceitaLancamentoAvulso(n))
        }
        assertFalse(Natureza.EMPRESTIMO_RECEBIDO.ehCredito)
        assertNotNull(Validacao.conta(tonho.copy(saldoInicial = 100)))
        assertNull(Validacao.conta(tonho))
    }

    @Test
    fun `parcelas automaticas mensais`() {
        val l = Emprestimos.parcelasAutomaticas(3, 11_000, LocalDate.of(2027, 1, 31))
        assertEquals(listOf(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)), l.map { it.first })
        assertEquals(10_000L, Emprestimos.jurosPrevistos(110_000, 100_000))
    }
}
