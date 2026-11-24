package com.hkawzz.nkote.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** v0.7 (regra 6F): cálculos puros dos objetivos. Valores fictícios. */
class ObjetivosTest {
    private val hoje = LocalDate.of(2027, 1, 15)
    private fun mov(id: String, obj: String, valor: Long, tipo: TipoMovimentoReserva, data: LocalDate = hoje, grupo: String = id) =
        MovimentoReserva(id, obj, data, valor, tipo, grupo)

    @Test
    fun `guardado e a soma dos movimentos e o total soma todos os objetivos nao excluidos`() {
        val v = VisaoObjetivos(
            listOf(Objetivo("a", "Fone", meta = 30_000), Objetivo("b", "Viagem", concluidoEm = 1), Objetivo("c", "Caixinha", arquivado = true)),
            listOf(
                mov("1", "a", 30_000, TipoMovimentoReserva.GUARDAR),
                mov("2", "a", -10_000, TipoMovimentoReserva.RETIRAR),
                mov("3", "a", -5_000, TipoMovimentoReserva.MOVER, grupo = "g"),
                mov("4", "b", 5_000, TipoMovimentoReserva.MOVER, grupo = "g"),
                mov("5", "c", 3_000, TipoMovimentoReserva.AJUSTE),
            ),
        )
        assertEquals(15_000L, v.guardado("a"))
        assertEquals(5_000L, v.guardado("b"))
        assertEquals(23_000L, v.totalGuardado)
        assertEquals(listOf("a"), v.ativos.map { it.objetivo.id })
        assertEquals(setOf("b", "c"), v.encerrados.map { it.objetivo.id }.toSet())
        val a = v.situacao("a")!!
        assertEquals(15_000L, a.falta)
        assertEquals(50, a.progresso)
        // Sem meta: "caixinha" livre, sem falta nem %.
        assertNull(v.situacao("c")!!.falta)
        assertNull(v.situacao("c")!!.progresso)
        // Análises: mover não conta; ajuste para cima conta como guardado.
        val p = Periodo(YearMonth.from(hoje), YearMonth.from(hoje))
        assertEquals(33_000L, v.guardadoNoPeriodo(p))
        assertEquals(10_000L, v.retiradoNoPeriodo(p))
    }

    @Test
    fun `guardar por mes e informativo e arredonda para cima`() {
        // Falta R$ 900,00 de janeiro a dezembro de 2027: 12 meses, R$ 75,00 por mês.
        assertEquals(12L, Objetivos.mesesAtePrazo(LocalDate.of(2027, 12, 31), hoje))
        assertEquals(7_500L, Objetivos.guardarPorMes(90_000, LocalDate.of(2027, 12, 31), hoje))
        // R$ 100,00 em 3 meses: R$ 33,34.
        assertEquals(3_334L, Objetivos.guardarPorMes(10_000, LocalDate.of(2027, 3, 1), hoje))
        // Prazo neste mês: 1 mês. Prazo vencido: sem sugestão.
        assertEquals(10_000L, Objetivos.guardarPorMes(10_000, LocalDate.of(2027, 1, 31), hoje))
        assertNull(Objetivos.guardarPorMes(10_000, LocalDate.of(2027, 1, 14), hoje))
        val s = SituacaoObjetivo(Objetivo("x", "X", meta = 10_000, prazo = LocalDate.of(2026, 12, 1)), 2_000, emptyList())
        assertTrue(s.prazoVencido(hoje))
        assertNull(s.porMes(hoje))
        assertNotNull(SituacaoObjetivo(Objetivo("y", "Y", meta = 10_000, prazo = LocalDate.of(2027, 6, 1)), 0, emptyList()).porMes(hoje))
    }

    @Test
    fun `desfazer nunca deixa objetivo negativo e aumentar segue o disponivel`() {
        val guardados = mapOf("a" to 10_000L, "b" to 0L)
        // Desfazer um guardar de R$ 200,00 com só R$ 100,00 guardados: negativo, recusado.
        assertNotNull(Objetivos.bloqueioDoDesfazer(listOf(mov("1", "a", 20_000, TipoMovimentoReserva.GUARDAR)), { guardados.getValue(it) }, 50_000))
        // Desfazer uma retirada de R$ 300,00 guarda de novo: precisa de disponível.
        val retirada = listOf(mov("2", "a", -30_000, TipoMovimentoReserva.RETIRAR))
        assertNotNull(Objetivos.bloqueioDoDesfazer(retirada, { guardados.getValue(it) }, 20_000))
        assertNull(Objetivos.bloqueioDoDesfazer(retirada, { guardados.getValue(it) }, 30_000))
        // Desfazer um mover: o destino não pode ficar negativo.
        val mover = listOf(mov("3", "a", -5_000, TipoMovimentoReserva.MOVER, grupo = "g"), mov("4", "b", 5_000, TipoMovimentoReserva.MOVER, grupo = "g"))
        assertNotNull(Objetivos.bloqueioDoDesfazer(mover, { guardados.getValue(it) }, 0))
        assertNull(Objetivos.bloqueioDoDesfazer(mover, { if (it == "b") 5_000 else 10_000 }, 0))
    }

    @Test
    fun `guardado reduz o disponivel e pode deixa-lo negativo sem reduzir nada`() {
        val pix = Conta("pix", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2027, 1, 1))
        val va = Conta("va", "VA", Natureza.BENEFICIO, TipoBeneficio.VA, 50_000, LocalDate.of(2027, 1, 1))
        val r = Saldos.resumo(listOf(pix, va), emptyList(), 30_000)
        assertEquals(100_000L, r.dinheiroProprio)
        assertEquals(70_000L, r.disponivel)
        assertEquals(50_000L, r.totalBeneficios)
        // O2: despesa de R$ 900,00 no Pix com R$ 300,00 guardados: disponível −R$ 200,00.
        val despesa = Movimentacao("d", TipoMovimentacao.DESPESA, LocalDate.of(2027, 1, 2), 90_000, "pix", null, null, "")
        val r2 = Saldos.resumo(listOf(pix, va), listOf(despesa), 30_000)
        assertEquals(-20_000L, r2.disponivel)
        assertEquals(30_000L, r2.reservado)
        assertFalse(r2.disponivel >= 0)
    }
}
