package com.hkawzz.nkote.dominio

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

    @Test
    fun `formata com sinal explicito`() {
        assertEquals("+R$ 5,00", Dinheiro.formatarComSinal(500))
        assertEquals("-R$ 12,70", Dinheiro.formatarComSinal(-1270))
        assertEquals("R$ 0,00", Dinheiro.formatarComSinal(0))
    }

    // --- Leitura do que a pessoa digita ---

    @Test
    fun `le valores no formato brasileiro`() {
        assertEquals(123456L, Dinheiro.ler("1.234,56"))
        assertEquals(123456L, Dinheiro.ler("1234,56"))
        assertEquals(123456L, Dinheiro.ler("R$ 1.234,56"))
        assertEquals(1200L, Dinheiro.ler("12"))
        assertEquals(1250L, Dinheiro.ler("12,5"))
        assertEquals(99L, Dinheiro.ler("0,99"))
        assertEquals(50L, Dinheiro.ler(",5"))
        assertEquals(-1250L, Dinheiro.ler("-12,50"))
        assertEquals(100000000L, Dinheiro.ler("1.000.000"))
    }

    @Test
    fun `rejeita entradas invalidas`() {
        for (t in listOf("", "  ", "-", "abc", "12,345", "1,2,3", "12.34", "1.23,00", "12a", "9999999999")) {
            assertEquals("“$t” deveria ser rejeitado", null, Dinheiro.ler(t))
        }
    }

    @Test
    fun `ler e formatar sao inversos`() {
        for (c in listOf(0L, 5L, 99L, 100L, 123456L, 99_999_999_999L)) {
            assertEquals(c, Dinheiro.ler(Dinheiro.formatar(c)))
        }
    }
}
