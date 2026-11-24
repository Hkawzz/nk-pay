package com.hkawzz.nkote.ui

import com.hkawzz.nkote.dominio.Analises
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Periodo
import com.hkawzz.nkote.dominio.TipoBeneficio
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.ui.ajuda.ChaveAjuda
import com.hkawzz.nkote.ui.ajuda.TextosDeAjuda
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Textos da ajuda (v0.5): todo indicador tem texto, e os números dos exemplos batem com o domínio. Sem banco. */
class AjudaTextosTest {
    @Test
    fun `todo indicador tem titulo e o que e`() {
        for (c in ChaveAjuda.entries) {
            val t = TextosDeAjuda.de(c)
            assertTrue(c.name, t.titulo.isNotBlank() && t.oQueE.isNotBlank())
        }
    }

    /** O exemplo da regra 6D: entrada 3.000, mercado 200 no Pix, compra 600 no cartão, fatura 300 paga. */
    @Test
    fun `os numeros dos exemplos batem com o dominio`() {
        val d = LocalDate.of(2026, 12, 5)
        val contas = listOf(
            Conta("pix", "Pix", Natureza.PROPRIO, null, 120_000, d),
            Conta("din", "Dinheiro", Natureza.PROPRIO, null, 30_000, d),
            Conta("cart", "Cartão", Natureza.CARTAO, null, 0, d, diaFechamento = 5, diaVencimento = 12),
        )
        val movs = listOf(
            Movimentacao("e", TipoMovimentacao.ENTRADA, d, 300_000, "pix"),
            Movimentacao("m", TipoMovimentacao.DESPESA, d, 20_000, "pix"),
            Movimentacao("c", TipoMovimentacao.DESPESA, d, 60_000, "cart"),
            Movimentacao("f", TipoMovimentacao.TRANSFERENCIA, d, 30_000, "pix", contaDestinoId = "cart"),
        )
        val dez = Periodo(YearMonth.of(2026, 12), YearMonth.of(2026, 12))
        val fluxo = Analises.fluxo(contas, movs, dez)
        val totais = Analises.totais(movs, dez)
        fun contem(c: ChaveAjuda, vararg valores: Long) = valores.forEach { v ->
            assertTrue("${c.name}: ${Dinheiro.formatar(v)}", TextosDeAjuda.de(c).exemplo!!.contains(Dinheiro.formatar(v)))
        }
        contem(ChaveAjuda.SALDO_MOVIMENTACOES, fluxo.entrou, fluxo.saiu, fluxo.saldo)
        contem(ChaveAjuda.SAIDAS_PROPRIO, fluxo.saiu)
        contem(ChaveAjuda.DESPESAS_REGISTRADAS, totais.despesas)
        contem(ChaveAjuda.RESULTADO, totais.resultado)
        // Saldo disponível: Pix 1.200 + Dinheiro 300.
        contem(ChaveAjuda.SALDO_DISPONIVEL, com.hkawzz.nkote.dominio.Saldos.resumo(contas.take(2), emptyList()).disponivel)
        // Benefícios: VA 250 + VR 180.
        val beneficios = com.hkawzz.nkote.dominio.Saldos.resumo(
            listOf(Conta("va", "VA", Natureza.BENEFICIO, TipoBeneficio.VA, 25_000, d), Conta("vr", "VR", Natureza.BENEFICIO, TipoBeneficio.VR, 18_000, d)),
            emptyList(),
        ).totalBeneficios
        contem(ChaveAjuda.BENEFICIOS, beneficios)
        // Empréstimos: R$ 1.000,00 em 10 × R$ 110,00.
        contem(ChaveAjuda.EMPRESTIMO_JUROS_PREVISTOS, com.hkawzz.nkote.dominio.Emprestimos.jurosPrevistos(110_000, 100_000))
        val juros = com.hkawzz.nkote.dominio.Emprestimos.jurosDoRecebimento(11_000, 110_000, 10_000)
        contem(ChaveAjuda.EMPRESTIMO_JUROS_RECEBIDOS, 11_000 - juros, juros)
        contem(ChaveAjuda.EMPRESTIMO_RESTANTE, 110_000L - 11_000L)
    }

}
