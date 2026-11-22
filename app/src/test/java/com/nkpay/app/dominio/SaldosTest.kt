package com.nkpay.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Testes das regras de saldo. Os números são os mesmos exemplos (fictícios)
 * de docs/regras-financeiras.md: se um teste mudar, o documento muda junto.
 */
class SaldosTest {

    private val dia = LocalDate.of(2026, 12, 1)
    private val dezembro = YearMonth.of(2026, 12)

    private val pix = Conta("pix", "Pix", Natureza.PROPRIO, saldoInicial = 120_000, dataSaldoInicial = dia)
    private val dinheiro = Conta("din", "Dinheiro", Natureza.PROPRIO, saldoInicial = 30_000, dataSaldoInicial = dia)
    private val va = Conta("va", "VA", Natureza.BENEFICIO, TipoBeneficio.VA, saldoInicial = 25_000, dataSaldoInicial = dia)
    private val vr = Conta("vr", "VR", Natureza.BENEFICIO, TipoBeneficio.VR, saldoInicial = 18_000, dataSaldoInicial = dia)
    private val contas = listOf(pix, dinheiro, va, vr)

    private var seq = 0
    private fun mov(
        tipo: TipoMovimentacao, valor: Long, conta: String, destino: String? = null,
        data: LocalDate = dia.plusDays(5),
    ) = Movimentacao("m${seq++}", tipo, data, valor, conta, destino)

    // --- Saldo por conta (regras 3.1) ---

    @Test
    fun `saldo inicial sem movimentacoes`() {
        assertEquals(120_000, Saldos.saldoDaConta(pix, emptyList()))
    }

    @Test
    fun `saldo da conta soma entradas, despesas, transferencias e ajustes`() {
        val movs = listOf(
            mov(TipoMovimentacao.ENTRADA, 50_000, "pix"),
            mov(TipoMovimentacao.DESPESA, 8_000, "pix"),
            mov(TipoMovimentacao.TRANSFERENCIA, 10_000, "pix", destino = "din"),
            mov(TipoMovimentacao.AJUSTE, -2_000, "pix"),
        )
        assertEquals(150_000, Saldos.saldoDaConta(pix, movs)) // R$ 1.500,00
        assertEquals(40_000, Saldos.saldoDaConta(dinheiro, movs)) // recebeu a transferência
        assertEquals(25_000, Saldos.saldoDaConta(va, movs)) // não foi tocado
    }

    @Test
    fun `saldosPorConta da o mesmo resultado que saldoDaConta`() {
        val movs = listOf(
            mov(TipoMovimentacao.ENTRADA, 50_000, "pix"),
            mov(TipoMovimentacao.TRANSFERENCIA, 4_000, "pix", destino = "din"),
            mov(TipoMovimentacao.DESPESA, 2_500, "va"),
            mov(TipoMovimentacao.AJUSTE, 300, "vr"),
        )
        val mapa = Saldos.saldosPorConta(contas, movs)
        for (c in contas) assertEquals(c.nome, Saldos.saldoDaConta(c, movs), mapa.getValue(c.id))
    }

    // --- Dinheiro próprio, disponível e benefícios (regras 4) ---

    @Test
    fun `beneficios ficam fora do disponivel e aparecem em bloco proprio`() {
        val r = Saldos.resumo(contas, emptyList())
        assertEquals(150_000, r.dinheiroProprio) // Pix + Dinheiro
        assertEquals(0, r.reservado)
        assertEquals(150_000, r.disponivel)
        assertEquals(43_000, r.totalBeneficios) // VA + VR
        assertEquals(listOf(25_000L, 18_000L), r.beneficios.map { it.saldo })
    }

    @Test
    fun `transferencia entre contas proprias nao muda o disponivel nem os totais do mes`() {
        val movs = listOf(mov(TipoMovimentacao.TRANSFERENCIA, 4_000, "pix", destino = "din"))
        val r = Saldos.resumo(contas, movs)
        val saldos = Saldos.saldosPorConta(contas, movs)
        assertEquals(116_000L, saldos["pix"])
        assertEquals(34_000L, saldos["din"])
        assertEquals(150_000, r.disponivel)
        assertEquals(TotaisDoMes(0, 0), Saldos.totaisDoMes(movs, dezembro))
    }

    @Test
    fun `despesa paga com VA reduz o VA, entra nas despesas e nao muda o disponivel`() {
        val movs = listOf(mov(TipoMovimentacao.DESPESA, 2_500, "va"))
        val r = Saldos.resumo(contas, movs)
        assertEquals(22_500, r.beneficios.first { it.conta.id == "va" }.saldo)
        assertEquals(150_000, r.disponivel)
        assertEquals(TotaisDoMes(entradas = 0, despesas = 2_500), Saldos.totaisDoMes(movs, dezembro))
    }

    @Test
    fun `tabela 4_2 do documento de regras`() {
        val movs = listOf(
            mov(TipoMovimentacao.TRANSFERENCIA, 4_000, "pix", destino = "din"),
            mov(TipoMovimentacao.DESPESA, 2_500, "va"),
            mov(TipoMovimentacao.DESPESA, 6_000, "pix"),
        )
        val s = Saldos.saldosPorConta(contas, movs)
        assertEquals(110_000L, s["pix"])
        assertEquals(34_000L, s["din"])
        assertEquals(22_500L, s["va"])
        assertEquals(144_000, Saldos.resumo(contas, movs).disponivel)
        assertEquals(TotaisDoMes(0, 8_500), Saldos.totaisDoMes(movs, dezembro))
    }

    @Test
    fun `reservado e descontado do disponivel`() {
        val r = Saldos.resumo(contas, emptyList(), reservado = 40_000)
        assertEquals(150_000, r.dinheiroProprio)
        assertEquals(110_000, r.disponivel)
    }

    @Test
    fun `conta arquivada continua contando no dinheiro proprio`() {
        val r = Saldos.resumo(listOf(pix, dinheiro.copy(arquivada = true)), emptyList())
        assertEquals(150_000, r.dinheiroProprio)
    }

    @Test
    fun `sem contas de beneficio o bloco fica vazio`() {
        val r = Saldos.resumo(listOf(pix), emptyList())
        assertEquals(emptyList<SaldoDeConta>(), r.beneficios)
        assertEquals(0, r.totalBeneficios)
    }

    // --- Totais do mês (regras 3.3) ---

    @Test
    fun `totais do mes ignoram transferencias e ajustes`() {
        val movs = listOf(
            mov(TipoMovimentacao.ENTRADA, 50_000, "pix"),
            mov(TipoMovimentacao.DESPESA, 8_000, "pix"),
            mov(TipoMovimentacao.TRANSFERENCIA, 10_000, "pix", destino = "din"),
            mov(TipoMovimentacao.AJUSTE, -2_000, "pix"),
        )
        assertEquals(TotaisDoMes(entradas = 50_000, despesas = 8_000), Saldos.totaisDoMes(movs, dezembro))
    }

    @Test
    fun `totais do mes so consideram o mes pedido`() {
        val movs = listOf(
            mov(TipoMovimentacao.ENTRADA, 50_000, "pix", data = LocalDate.of(2026, 11, 30)),
            mov(TipoMovimentacao.ENTRADA, 7_000, "pix", data = LocalDate.of(2026, 12, 1)),
            mov(TipoMovimentacao.DESPESA, 1_000, "pix", data = LocalDate.of(2026, 12, 31)),
            mov(TipoMovimentacao.DESPESA, 9_999, "pix", data = LocalDate.of(2027, 1, 1)),
        )
        assertEquals(TotaisDoMes(7_000, 1_000), Saldos.totaisDoMes(movs, dezembro))
    }

    @Test
    fun `saldo inicial nao e entrada`() {
        assertEquals(TotaisDoMes(0, 0), Saldos.totaisDoMes(emptyList(), dezembro))
    }

    // --- Ajuste pelo novo saldo (regras 3.2) ---

    @Test
    fun `ajuste pelo novo saldo calcula a diferenca`() {
        assertEquals(-1_270, Saldos.valorDoAjuste(saldoAtual = 150_000, novoSaldoInformado = 148_730))
        assertEquals(500, Saldos.valorDoAjuste(saldoAtual = 1_000, novoSaldoInformado = 1_500))
        assertEquals(0, Saldos.valorDoAjuste(saldoAtual = 1_000, novoSaldoInformado = 1_000))
    }

    @Test
    fun `aplicar o ajuste calculado leva o saldo ao valor informado`() {
        val base = listOf(mov(TipoMovimentacao.DESPESA, 3_333, "pix"))
        val atual = Saldos.saldoDaConta(pix, base)
        val ajuste = Saldos.valorDoAjuste(atual, 100_000)
        val depois = base + mov(TipoMovimentacao.AJUSTE, ajuste, "pix")
        assertEquals(100_000, Saldos.saldoDaConta(pix, depois))
    }
}
