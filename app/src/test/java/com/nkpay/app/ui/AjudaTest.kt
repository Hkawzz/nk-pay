package com.nkpay.app.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.nkpay.app.AppContainer
import com.nkpay.app.dados.NkPayBanco
import com.nkpay.app.dados.RelogioFalso
import com.nkpay.app.dominio.Analises
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Movimentacao
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.Periodo
import com.nkpay.app.dominio.TipoBeneficio
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.ui.ajuda.ChaveAjuda
import com.nkpay.app.ui.ajuda.TextosDeAjuda
import com.nkpay.app.ui.theme.NkPayTema
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/** Ajuda "?" (v0.5): todo indicador tem texto, e os números dos exemplos batem com o domínio. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AjudaTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
    }

    @After fun fechar() = banco.close()

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
        contem(ChaveAjuda.SALDO_DISPONIVEL, com.nkpay.app.dominio.Saldos.resumo(contas.take(2), emptyList()).disponivel)
        // Benefícios: VA 250 + VR 180.
        val beneficios = com.nkpay.app.dominio.Saldos.resumo(
            listOf(Conta("va", "VA", Natureza.BENEFICIO, TipoBeneficio.VA, 25_000, d), Conta("vr", "VR", Natureza.BENEFICIO, TipoBeneficio.VR, 18_000, d)),
            emptyList(),
        ).totalBeneficios
        contem(ChaveAjuda.BENEFICIOS, beneficios)
    }

    @Test
    fun ajudaDoSaldoDisponivelAbreNaInicio() {
        runBlocking { container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 150_000, LocalDate.of(2026, 12, 1))) }
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.waitUntil(10_000) { regra.onAllNodes(hasText("Saldo disponível")).fetchSemanticsNodes().isNotEmpty() }
        regra.onNodeWithContentDescription("Ajuda: Saldo disponível").performClick()
        regra.waitUntil(10_000) { regra.onAllNodes(hasText("Como é calculado")).fetchSemanticsNodes().isNotEmpty() }
        regra.onNodeWithText("O que não é").assertExists()
        regra.waitForIdle()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/v05-ajuda.png")
        regra.onNodeWithText("Entendi").performClick()
    }
}
