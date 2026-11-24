package com.hkawzz.nkote.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ValidacaoTest {

    private val dia = LocalDate.of(2026, 12, 1)
    private val existe = { id: String -> id in setOf("a", "b") }

    private fun mov(tipo: TipoMovimentacao, valor: Long, conta: String = "a", destino: String? = null, categoria: String? = null) =
        Movimentacao("x", tipo, dia, valor, conta, destino, categoria)

    @Test
    fun `entrada e despesa precisam de valor positivo`() {
        assertNull(Validacao.movimentacao(mov(TipoMovimentacao.ENTRADA, 1), existe))
        assertNotNull(Validacao.movimentacao(mov(TipoMovimentacao.ENTRADA, 0), existe))
        assertNotNull(Validacao.movimentacao(mov(TipoMovimentacao.DESPESA, -5), existe))
    }

    @Test
    fun `movimentacao precisa de conta existente`() {
        assertNotNull(Validacao.movimentacao(mov(TipoMovimentacao.DESPESA, 100, conta = "zz"), existe))
    }

    @Test
    fun `transferencia precisa de destino diferente e sem categoria`() {
        assertNull(Validacao.movimentacao(mov(TipoMovimentacao.TRANSFERENCIA, 100, destino = "b"), existe))
        assertNotNull(Validacao.movimentacao(mov(TipoMovimentacao.TRANSFERENCIA, 100), existe))
        assertNotNull(Validacao.movimentacao(mov(TipoMovimentacao.TRANSFERENCIA, 100, destino = "a"), existe))
        assertNotNull(Validacao.movimentacao(mov(TipoMovimentacao.TRANSFERENCIA, 100, destino = "b", categoria = "c"), existe))
    }

    @Test
    fun `ajuste pode ser negativo mas nao zero`() {
        assertNull(Validacao.movimentacao(mov(TipoMovimentacao.AJUSTE, -1_270), existe))
        assertNull(Validacao.movimentacao(mov(TipoMovimentacao.AJUSTE, 500), existe))
        assertNotNull(Validacao.movimentacao(mov(TipoMovimentacao.AJUSTE, 0), existe))
        assertNotNull(Validacao.movimentacao(mov(TipoMovimentacao.AJUSTE, 10, categoria = "c"), existe))
    }

    @Test
    fun `conta de beneficio exige tipo e as outras nao aceitam`() {
        val base = Conta("a", "VA", Natureza.BENEFICIO, null, 0, dia)
        assertNotNull(Validacao.conta(base))
        assertNull(Validacao.conta(base.copy(tipoBeneficio = TipoBeneficio.VA)))
        assertNotNull(Validacao.conta(base.copy(natureza = Natureza.PROPRIO, tipoBeneficio = TipoBeneficio.VA)))
    }

    @Test
    fun `conta e categoria precisam de nome`() {
        assertEquals("Dê um nome para a conta.", Validacao.conta(Conta("a", "  ", Natureza.PROPRIO, null, 0, dia)))
        assertNotNull(Validacao.categoria(Categoria("c", "")))
    }
}
