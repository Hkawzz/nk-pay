package com.nkpay.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Domínio de crédito (regras-financeiras.md, 6B), com os exemplos do documento. Valores fictícios. */
class CreditoTest {

    private fun d(a: Int, m: Int, dia: Int) = LocalDate.of(a, m, dia)

    // ------------------------------------------------------------ 6B.3 datas

    @Test
    fun `datas da 1a parcela no cartao seguem a tabela da regra 6B_3`() {
        assertEquals(d(2026, 12, 12), DatasDeCredito.primeiroVencimentoCartao(d(2026, 12, 3), 5, 12))
        // Compra no dia do fechamento vai para a fatura seguinte.
        assertEquals(d(2027, 1, 12), DatasDeCredito.primeiroVencimentoCartao(d(2026, 12, 5), 5, 12))
        // Vencimento antes do fechamento: vence no mês seguinte ao fechamento (virada de ano).
        assertEquals(d(2027, 1, 5), DatasDeCredito.primeiroVencimentoCartao(d(2026, 12, 10), 28, 5))
        // Fechamento 31 em fevereiro vira 28/02; compra nesse dia vai para março.
        assertEquals(d(2027, 4, 10), DatasDeCredito.primeiroVencimentoCartao(d(2027, 2, 28), 31, 10))
        assertEquals(d(2027, 3, 10), DatasDeCredito.primeiroVencimentoCartao(d(2027, 2, 27), 31, 10))
    }

    @Test
    fun `crediario vence no mes seguinte e parcelas seguem mes a mes ajustando o fim do mes`() {
        assertEquals(d(2027, 1, 20), DatasDeCredito.primeiroVencimentoCrediario(d(2026, 12, 3), 20))
        assertEquals(d(2027, 1, 3), DatasDeCredito.primeiroVencimentoCrediario(d(2026, 12, 3), null))
        assertEquals(
            listOf(d(2027, 1, 31), d(2027, 2, 28), d(2027, 3, 31)),
            DatasDeCredito.vencimentos(d(2027, 1, 31), 3),
        )
    }

    @Test
    fun `a ultima parcela absorve os centavos`() {
        assertEquals(listOf(3_333L, 3_333L, 3_334L), DatasDeCredito.valoresDasParcelas(10_000, 3))
        assertEquals(listOf(60_000L), DatasDeCredito.valoresDasParcelas(60_000, 1))
        assertEquals(120_000L, DatasDeCredito.valoresDasParcelas(120_000, 7).sum())
    }

    // ------------------------------------------------------------ 6B.4 fatura

    private val cartao = Conta("cart", "Cartão", Natureza.CARTAO, null, 0, d(2026, 11, 1), limite = 200_000, diaFechamento = 5, diaVencimento = 12)
    private val pix = Conta("pix", "Pix", Natureza.PROPRIO, null, 300_000, d(2026, 11, 1))
    private val dez = YearMonth.of(2026, 12)
    private val hoje = d(2026, 12, 1)

    private fun parcela(id: String, valor: Long, mes: YearMonth = dez, n: Int = 1) =
        Obrigacao(id, OrigemObrigacao.PARCELA, id, valor, mes.atDay(12), mes, contaCreditoId = "cart", compraId = "c$id", numeroParcela = n, totalParcelas = 1)

    private val compraTotal = Movimentacao("mc", TipoMovimentacao.DESPESA, d(2026, 11, 20), 48_000, "cart")

    private fun visao(
        movs: List<Movimentacao> = emptyList(),
        pags: List<Pagamento> = emptyList(),
        gerais: List<PagamentoFatura> = emptyList(),
        ajustes: List<AjusteObrigacao> = emptyList(),
        obrigs: List<Obrigacao> = listOf(parcela("A", 10_000), parcela("B", 20_000), parcela("C", 18_000)),
    ) = VisaoCredito(listOf(cartao, pix), listOf(compraTotal) + movs, obrigs, pags, ajustes, gerais, emptyList())

    private fun transf(id: String, valor: Long) = Movimentacao(id, TipoMovimentacao.TRANSFERENCIA, d(2026, 12, 10), valor, "pix", "cart")

    @Test
    fun `fatura de 480 sem pagamentos devido igual ao restante e limite disponivel`() {
        val v = visao()
        val f = v.fatura("cart", dez, hoje)!!
        assertEquals(48_000L, f.total)
        assertEquals(48_000L, f.restante)
        assertEquals(StatusObrigacao.PENDENTE, f.status)
        assertEquals(48_000L, v.devido("cart"))
        assertEquals(152_000L, v.limiteDisponivel("cart"))
        assertTrue(v.invarianteFecha("cart", hoje))
    }

    @Test
    fun `pagar total quita a fatura ligando as tres parcelas`() {
        val dist = visao().distribuir("cart", 48_000, dez, hoje)
        val parte = dist.partes.single() as ParteDoPagamento.FaturaInteira
        assertEquals(listOf("A" to 10_000L, "B" to 20_000L, "C" to 18_000L), parte.aplicacoes)
        assertEquals(0L, dist.excedente)

        val v = visao(movs = listOf(transf("t", 48_000)), pags = parte.aplicacoes.map { Pagamento("p${it.first}", it.first, "t", it.second) })
        assertEquals(StatusObrigacao.QUITADA, v.fatura("cart", dez, hoje)!!.status)
        assertEquals(0L, v.devido("cart"))
        assertTrue(v.invarianteFecha("cart", hoje))
    }

    @Test
    fun `pagar parcial geral de 300 nao quita nenhuma compra e depois 180 quita tudo`() {
        val dist = visao().distribuir("cart", 30_000, dez, hoje)
        assertEquals(listOf(ParteDoPagamento.AbatimentoGeral(dez, 30_000)), dist.partes)

        val v1 = visao(movs = listOf(transf("t1", 30_000)), gerais = listOf(PagamentoFatura("g1", "cart", dez, "t1", 30_000)))
        val f1 = v1.fatura("cart", dez, hoje)!!
        assertEquals(18_000L, f1.restante)
        assertEquals(StatusObrigacao.PARCIAL, f1.status)
        assertTrue(f1.parcelas.none { it.status == StatusObrigacao.QUITADA })
        assertTrue(v1.invarianteFecha("cart", hoje))

        val v2 = visao(
            movs = listOf(transf("t1", 30_000), transf("t2", 18_000)),
            gerais = listOf(PagamentoFatura("g1", "cart", dez, "t1", 30_000), PagamentoFatura("g2", "cart", dez, "t2", 18_000)),
        )
        val f2 = v2.fatura("cart", dez, hoje)!!
        assertEquals(0L, f2.restante)
        assertTrue(f2.parcelas.all { it.status == StatusObrigacao.QUITADA && it.restante == 0L })
        assertTrue(v2.invarianteFecha("cart", hoje))
    }

    @Test
    fun `pagar so a compra B deixa 280 na fatura`() {
        val v = visao(movs = listOf(transf("t", 20_000)), pags = listOf(Pagamento("p", "B", "t", 20_000)))
        val f = v.fatura("cart", dez, hoje)!!
        assertEquals(28_000L, f.restante)
        assertEquals(StatusObrigacao.QUITADA, f.parcelas.first { it.obrigacao.id == "B" }.status)
        assertEquals(28_000L, v.devido("cart"))
        assertTrue(v.invarianteFecha("cart", hoje))
    }

    @Test
    fun `fatura vencida fica atrasada no mes original e um pagamento cobre duas faturas da mais antiga para a nova`() {
        val nov = YearMonth.of(2026, 11)
        val obrigs = listOf(parcela("X", 5_000, nov), parcela("A", 10_000), parcela("B", 20_000))
        val compraNov = Movimentacao("mn", TipoMovimentacao.DESPESA, d(2026, 10, 20), 5_000, "cart")
        val v = VisaoCredito(listOf(cartao, pix), listOf(compraNov, Movimentacao("md", TipoMovimentacao.DESPESA, d(2026, 11, 20), 30_000, "cart")), obrigs, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(StatusObrigacao.ATRASADA, v.fatura("cart", nov, d(2026, 12, 20))!!.status)

        val dist = v.distribuir("cart", 20_000, dez, d(2026, 12, 20))
        assertEquals(2, dist.partes.size)
        assertEquals(nov, dist.partes[0].mes)
        assertTrue(dist.partes[0] is ParteDoPagamento.FaturaInteira)
        assertEquals(ParteDoPagamento.AbatimentoGeral(dez, 15_000), dist.partes[1])
        assertEquals(0L, dist.excedente)
    }

    @Test
    fun `pagar mais que o restante gera excedente para classificar`() {
        val dist = visao().distribuir("cart", 49_200, dez, hoje)
        assertEquals(1_200L, dist.excedente)
        assertEquals(48_000L, dist.partes.sumOf { it.valor })
    }

    @Test
    fun `encargo classificado mantem a invariante`() {
        // Pagou 492 com 12 de juros: transferência 492, despesa de encargo 12 no cartão, acréscimo na parcela A.
        val v = visao(
            movs = listOf(transf("t", 49_200), Movimentacao("e", TipoMovimentacao.DESPESA, d(2026, 12, 10), 1_200, "cart")),
            pags = listOf(Pagamento("pA", "A", "t", 11_200), Pagamento("pB", "B", "t", 20_000), Pagamento("pC", "C", "t", 18_000)),
            ajustes = listOf(AjusteObrigacao("aj", "A", "pA", NaturezaAjuste.JUROS, 1_200, "e")),
        )
        assertEquals(0L, v.devido("cart"))
        assertEquals(0L, v.fatura("cart", dez, hoje)!!.restante)
        assertTrue(v.invarianteFecha("cart", hoje))
    }

    @Test
    fun `quitada antes do app nao e desconto`() {
        assertFalse(NaturezaAjuste.QUITADA_ANTES_DO_APP.ehDesconto)
        assertFalse(NaturezaAjuste.QUITADA_ANTES_DO_APP.ehAcrescimo)
        assertTrue(NaturezaAjuste.DESCONTO.ehDesconto)
    }

    // ------------------------------------------------------------ validação

    @Test
    fun `contas de credito comecam com saldo zero e cartao exige fechamento e vencimento`() {
        assertNull(Validacao.conta(cartao))
        assertNotNull(Validacao.conta(cartao.copy(saldoInicial = -100)))
        assertNotNull(Validacao.conta(cartao.copy(diaFechamento = null)))
        assertNotNull(Validacao.conta(cartao.copy(diaVencimento = 32)))
        assertNotNull(Validacao.conta(cartao.copy(limite = 0)))
        assertNull(Validacao.conta(Conta("c", "Loja", Natureza.CREDIARIO, null, 0, hoje)))
        assertFalse(Validacao.aceitaLancamentoAvulso(Natureza.CARTAO))
        assertFalse(Validacao.aceitaLancamentoAvulso(Natureza.CREDIARIO))
        assertTrue(Validacao.aceitaLancamentoAvulso(Natureza.BENEFICIO))
    }

    @Test
    fun `validacao da compra`() {
        assertNull(Validacao.compra("Geladeira", 120_000, 12, hoje))
        assertNotNull(Validacao.compra("", 120_000, 12, hoje))
        assertNotNull(Validacao.compra("X", 0, 1, hoje))
        assertNotNull(Validacao.compra("X", 5, 12, hoje))
        assertNotNull(Validacao.compra("X", 100, 0, hoje))
        assertNotNull(Validacao.compra("X", 100, 1, null))
    }
}
