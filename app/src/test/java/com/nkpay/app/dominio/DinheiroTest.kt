package com.nkpay.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Test

class DinheiroTest {

    @Test
    fun `formata zero`() = assertEquals("R$ 0,00", Dinheiro.formatar(0))

    @Test
    fun `formata centavos sozinhos`() {
        assertEquals("R$ 0,05", Dinheiro.formatar(5))
        assertEquals("R$ 0,99", Dinheiro.formatar(99))
    }

    @Test
    fun `formata milhar com ponto e centavos com virgula`() {
        assertEquals("R$ 1.234,56", Dinheiro.formatar(123456))
        assertEquals("R$ 1.000,00", Dinheiro.formatar(100000))
        assertEquals("R$ 999,00", Dinheiro.formatar(99900))
    }

    @Test
    fun `formata milhoes`() = assertEquals("R$ 12.345.678,90", Dinheiro.formatar(1234567890))

    @Test
    fun `formata negativo com sinal antes do simbolo`() =
        assertEquals("-R$ 42,10", Dinheiro.formatar(-4210))
}
