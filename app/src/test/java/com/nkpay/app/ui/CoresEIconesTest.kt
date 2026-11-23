package com.nkpay.app.ui

import com.nkpay.app.ui.icones.IconesNk
import com.nkpay.app.ui.theme.Contraste
import com.nkpay.app.ui.theme.PaletaNk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paleta, ajuste de contraste e o conjunto de ícones (ADR 0018 e 0019). */
class CoresEIconesTest {
    private val card = 0xFF18181BL

    @Test
    fun `as 12 cores da paleta tem contraste de pelo menos 3 para 1 com o card`() {
        assertEquals(12, PaletaNk.size)
        for (c in PaletaNk) assertTrue("cor ${c.toString(16)}", Contraste.razao(c, card) >= 3.0)
    }

    @Test
    fun `cor personalizada escura e clareada ate ter contraste`() {
        val vermelhoEscuro = 0xFF7F1D1DL
        assertTrue(Contraste.razao(vermelhoEscuro, card) < 3.0)
        val ajustada = Contraste.ajustar(vermelhoEscuro)
        assertTrue(Contraste.razao(ajustada, card) >= 3.0)
        // Continua avermelhada: o vermelho é o canal mais forte.
        assertTrue(((ajustada shr 16) and 0xFF) > (ajustada and 0xFF))
    }

    @Test
    fun `cor que ja tem contraste nao muda`() {
        assertEquals(0xFFA78BFAL, Contraste.ajustar(0xFFA78BFAL))
    }

    @Test
    fun `todos os icones sao desenhados e nomes desconhecidos viram outros`() {
        assertTrue(IconesNk.todos.size >= 35)
        assertEquals(IconesNk.todos.size, IconesNk.todos.map { it.nome }.toSet().size)
        for (i in IconesNk.todos) assertTrue(i.nome, i.vetor.root.iterator().hasNext())
        assertEquals(IconesNk.de("outros"), IconesNk.de("nao-existe"))
        assertEquals(IconesNk.de("outros"), IconesNk.de(null))
    }
}
