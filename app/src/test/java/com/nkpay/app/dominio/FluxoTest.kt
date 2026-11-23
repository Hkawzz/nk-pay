package com.nkpay.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Fluxo do dinheiro próprio (regra 6E.2, D1): a tabela de integração, linha a linha.
 * "pessoa" é uma conta de terceiros qualquer (o fluxo só olha se a conta é PROPRIO).
 */
class FluxoTest {
    private val dia = LocalDate.of(2026, 12, 10)
    private val dez = Periodo(YearMonth.of(2026, 12), YearMonth.of(2026, 12))
    private val pix = Conta("pix", "Pix", Natureza.PROPRIO, null, 100_000, dia)
    private val din = Conta("din", "Dinheiro", Natureza.PROPRIO, null, 0, dia)
    private val va = Conta("va", "VA", Natureza.BENEFICIO, TipoBeneficio.VA, 50_000, dia)
    private val cartao = Conta("cart", "Cartão", Natureza.CARTAO, null, 0, dia, diaFechamento = 5, diaVencimento = 12)
    private val pessoa = Conta("pes", "Pessoa", Natureza.CREDIARIO, null, 0, dia)
    private val contas = listOf(pix, din, va, cartao, pessoa)

    private fun fluxo(vararg m: Movimentacao) = Analises.fluxo(contas, m.toList(), dez)
    private fun t(valor: Long, de: String, para: String) = Movimentacao("t$valor$de", TipoMovimentacao.TRANSFERENCIA, dia, valor, de, contaDestinoId = para)

    @Test
    fun `pegar emprestado entra 1000 e os juros J1 na conta da pessoa nao saem`() {
        val f = fluxo(t(100_000, "pes", "pix"), Movimentacao("j", TipoMovimentacao.DESPESA, dia, 10_000, "pes"))
        assertEquals(100_000L, f.entrou)
        assertEquals(0L, f.saiu)
    }

    @Test
    fun `pagar parcela sai 110`() {
        val f = fluxo(t(11_000, "pix", "pes"))
        assertEquals(11_000L, f.saiu)
        assertEquals(1, f.quantidadeSaidas)
    }

    @Test
    fun `emprestar sai 1000 e receber parcela entra 110`() {
        assertEquals(100_000L, fluxo(t(100_000, "pix", "pes")).saiu)
        val f = fluxo(t(10_000, "pes", "pix"), Movimentacao("jr", TipoMovimentacao.ENTRADA, dia, 1_000, "pix"))
        assertEquals(11_000L, f.entrou)
        assertEquals(2, f.quantidadeEntradas)
    }

    @Test
    fun `compra no cartao nunca e saida e pagar a fatura e`() {
        assertEquals(0L, fluxo(Movimentacao("c", TipoMovimentacao.DESPESA, dia, 60_000, "cart")).saiu)
        assertEquals(30_000L, fluxo(t(30_000, "pix", "cart")).saiu)
    }

    @Test
    fun `proprio para proprio ajustes e despesas do VA ficam fora`() {
        val f = fluxo(
            t(10_000, "pix", "din"),
            Movimentacao("a", TipoMovimentacao.AJUSTE, dia, -700, "pix"),
            Movimentacao("v", TipoMovimentacao.DESPESA, dia, 2_500, "va"),
        )
        assertEquals(0L, f.entrou)
        assertEquals(0L, f.saiu)
        assertEquals(0, f.quantidadeEntradas + f.quantidadeSaidas)
    }

    @Test
    fun `saldo das movimentacoes e entradas menos saidas`() {
        val f = fluxo(Movimentacao("e", TipoMovimentacao.ENTRADA, dia, 300_000, "pix"), Movimentacao("d", TipoMovimentacao.DESPESA, dia, 20_000, "pix"))
        assertEquals(280_000L, f.saldo)
    }
}
