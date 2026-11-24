package com.hkawzz.nkote.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Cálculos puros dos objetivos. v0.8 (R2, regra 6G.2): o guardado é o SALDO da caixinha;
 * os testes da v0.7 (carimbo) foram reescritos para o modelo de caixinhas. Valores fictícios.
 */
class ObjetivosTest {
    private val hoje = LocalDate.of(2027, 1, 15)
    private val d0 = LocalDate.of(2027, 1, 1)
    private val pix = Conta("pix", "Pix", Natureza.PROPRIO, null, 100_000, d0)
    private val va = Conta("va", "VA", Natureza.BENEFICIO, TipoBeneficio.VA, 50_000, d0)
    private val cxFone = Conta("cxf", "Fone", Natureza.CAIXINHA, null, 0, d0)
    private val cxViagem = Conta("cxv", "Viagem", Natureza.CAIXINHA, null, 0, d0)
    private val contas = listOf(pix, va, cxFone, cxViagem)
    private val objetivos = listOf(Objetivo("a", "Fone", meta = 30_000, contaCaixinhaId = "cxf"), Objetivo("b", "Viagem", arquivado = true, contaCaixinhaId = "cxv"))
    private fun t(id: String, valor: Long, de: String, para: String) = Movimentacao(id, TipoMovimentacao.TRANSFERENCIA, hoje, valor, de, para)

    // A tabela da regra 6G.2, linha a linha: guardar 300, rendimento 2, retirar 100.
    private val movs = listOf(
        t("g", 30_000, "pix", "cxf"),
        Movimentacao("r", TipoMovimentacao.ENTRADA, hoje, 200, "cxf"),
        t("ret", 10_000, "cxf", "pix"),
    )

    @Test
    fun `tabela da regra 6G2`() {
        val r = Saldos.resumo(contas, movs)
        assertEquals(100_200L, r.dinheiroProprio) // Saldo total = Pix 800 + caixinha 202
        assertEquals(20_200L, r.reservado) // Guardado
        assertEquals(80_000L, r.disponivel) // Saldo disponível = só o Pix
        assertEquals(50_000L, r.totalBeneficios) // o VA nunca entra
        val v = VisaoObjetivos(objetivos, emptyList(), contas, movs)
        assertEquals(20_200L, v.guardado("a"))
        assertEquals(20_200L, v.totalGuardado)
        assertEquals(listOf(TipoMovimentoCaixinha.GUARDAR, TipoMovimentoCaixinha.RENDIMENTO, TipoMovimentoCaixinha.RETIRAR), v.movimentosDaCaixinha("cxf").map { it.tipo })
        val s = v.situacao("a")!!
        assertEquals(9_800L, s.falta)
        assertEquals(67, s.progresso)
    }

    @Test
    fun `guardar e retirar nao entram no fluxo nem nos totais e o rendimento e entrada`() {
        val p = Periodo(YearMonth.from(hoje), YearMonth.from(hoje))
        val f = Analises.fluxo(contas, movs, p)
        assertEquals(200L, f.entrou)
        assertEquals(0L, f.saiu)
        assertEquals(TotaisDoMes(200, 0), Saldos.totaisDoMes(movs, YearMonth.from(hoje)))
        val v = VisaoObjetivos(objetivos, emptyList(), contas, movs)
        assertEquals(30_000L, v.guardadoNoPeriodo(p))
        assertEquals(10_000L, v.retiradoNoPeriodo(p))
        assertEquals(200L, v.rendimentosNoPeriodo(p))
    }

    @Test
    fun `mover entre caixinhas nao muda o guardado total e encerrados continuam contando`() {
        val comMover = movs + t("m", 5_000, "cxf", "cxv")
        val v = VisaoObjetivos(objetivos, emptyList(), contas, comMover)
        assertEquals(15_200L, v.guardado("a"))
        assertEquals(5_000L, v.guardado("b"))
        assertEquals(20_200L, v.totalGuardado)
        assertEquals(listOf("a"), v.ativos.map { it.objetivo.id })
        assertEquals(listOf("b"), v.encerrados.map { it.objetivo.id })
        assertEquals(TipoMovimentoCaixinha.MOVER_SAIDA, v.movimentosDaCaixinha("cxf").last().tipo)
        assertEquals(TipoMovimentoCaixinha.MOVER_ENTRADA, v.movimentosDaCaixinha("cxv").single().tipo)
        val p = Periodo(YearMonth.from(hoje), YearMonth.from(hoje))
        assertEquals(30_000L, v.guardadoNoPeriodo(p)) // mover não conta
        assertEquals(80_000L, Saldos.resumo(contas, comMover).disponivel)
    }

    @Test
    fun `objetivo da v0_7 sem caixinha tem guardado zero e o historico antigo nao conta`() {
        val antigo = MovimentoReserva("x", "c", hoje, 30_000, TipoMovimentoReserva.GUARDAR, "g")
        val v = VisaoObjetivos(listOf(Objetivo("c", "Antigo", meta = 50_000)), listOf(antigo), contas, emptyList())
        assertEquals(0L, v.guardado("c"))
        assertEquals(0L, v.totalGuardado)
        assertTrue(v.temHistoricoAntigo)
        assertEquals(listOf(antigo), v.situacao("c")!!.antigos)
        // O Saldo disponível não depende mais do carimbo.
        assertEquals(100_000L, Saldos.resumo(listOf(pix), emptyList()).disponivel)
    }

    @Test
    fun `guardar por mes e informativo e arredonda para cima`() {
        // Falta R$ 900,00 de janeiro a dezembro de 2027: 12 meses, R$ 75,00 por mês.
        assertEquals(12L, Objetivos.mesesAtePrazo(LocalDate.of(2027, 12, 31), hoje))
        assertEquals(7_500L, Objetivos.guardarPorMes(90_000, LocalDate.of(2027, 12, 31), hoje))
        assertEquals(3_334L, Objetivos.guardarPorMes(10_000, LocalDate.of(2027, 3, 1), hoje))
        assertEquals(10_000L, Objetivos.guardarPorMes(10_000, LocalDate.of(2027, 1, 31), hoje))
        assertNull(Objetivos.guardarPorMes(10_000, LocalDate.of(2027, 1, 14), hoje))
        val s = SituacaoObjetivo(Objetivo("x", "X", meta = 10_000, prazo = LocalDate.of(2026, 12, 1)), 2_000, emptyList())
        assertTrue(s.prazoVencido(hoje))
        assertNull(s.porMes(hoje))
        assertNotNull(SituacaoObjetivo(Objetivo("y", "Y", meta = 10_000, prazo = LocalDate.of(2027, 6, 1)), 0, emptyList()).porMes(hoje))
        // Sem meta: "caixinha" livre, sem falta nem %.
        assertNull(SituacaoObjetivo(Objetivo("z", "Z"), 5_000, emptyList()).progresso)
    }
}
