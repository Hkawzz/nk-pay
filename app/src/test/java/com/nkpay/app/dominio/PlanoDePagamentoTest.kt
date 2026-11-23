package com.nkpay.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plano de pagamento (ADR 0013), com os exemplos de regras-financeiras.md 6A. */
class PlanoDePagamentoTest {

    private fun pronto(r: ResultadoPlano) = (r as ResultadoPlano.Pronto).plano

    @Test
    fun `valor igual ao restante quita sem perguntar nada`() {
        val p = pronto(Pagamentos.planejar(restante = 10_000, valorPago = 10_000, classificacao = null))
        assertEquals(10_000L, p.despesaPrincipal)
        assertEquals(0L, p.encargo)
        assertEquals(0L, p.restanteDepois)
    }

    @Test
    fun `pagar menos exige escolher entre parcial e desconto`() {
        val r = Pagamentos.planejar(10_000, 6_000, null) as ResultadoPlano.PrecisaClassificar
        assertEquals(4_000L, r.diferenca)
        assertEquals(ClassificacaoDiferenca.quandoPagaMenos, r.opcoes)
        // Uma classificação "de pagar mais" não vale quando se paga menos.
        assertTrue(Pagamentos.planejar(10_000, 6_000, ClassificacaoDiferenca.MULTA) is ResultadoPlano.PrecisaClassificar)
    }

    @Test
    fun `parcial registra a despesa do valor pago e deixa o restante`() {
        val p = pronto(Pagamentos.planejar(10_000, 6_000, ClassificacaoDiferenca.PARCIAL))
        assertEquals(6_000L, p.despesaPrincipal)
        assertEquals(0L, p.abatimento)
        assertEquals(4_000L, p.restanteDepois)
        assertEquals(6_000L, p.totalPago)
    }

    @Test
    fun `desconto registra a despesa do valor pago, o abatimento e quita`() {
        val p = pronto(Pagamentos.planejar(10_000, 9_500, ClassificacaoDiferenca.DESCONTO))
        assertEquals(9_500L, p.despesaPrincipal) // despesa = valor efetivamente pago
        assertEquals(500L, p.abatimento)
        assertEquals(0L, p.restanteDepois)
        assertEquals(9_500L, p.totalPago) // nenhuma receita fictícia
    }

    @Test
    fun `pagar mais exige a natureza do encargo e separa a despesa de encargo`() {
        val r = Pagamentos.planejar(10_000, 10_500, null) as ResultadoPlano.PrecisaClassificar
        assertEquals(ClassificacaoDiferenca.quandoPagaMais, r.opcoes)
        val p = pronto(Pagamentos.planejar(10_000, 10_500, ClassificacaoDiferenca.MULTA))
        assertEquals(10_000L, p.despesaPrincipal)
        assertEquals(500L, p.encargo)
        assertEquals(0L, p.restanteDepois)
        assertEquals(10_500L, p.totalPago)
    }

    @Test
    fun `conta ja paga e valor zero sao recusados`() {
        assertTrue(Pagamentos.planejar(0, 100, null) is ResultadoPlano.Invalido)
        assertTrue(Pagamentos.planejar(10_000, 0, null) is ResultadoPlano.Invalido)
    }
}
