package com.nkpay.app.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.nkpay.app.AppContainer
import com.nkpay.app.dados.NkPayBanco
import com.nkpay.app.dados.RelogioFalso
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.ui.theme.NkPayTema
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** v0.6 (6B.8): compra parcelada com juros pela interface. Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CompraJurosUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            container.contas.criar(Conta("", "Cartão Teste", Natureza.CARTAO, null, 0, LocalDate.of(2026, 12, 1), limite = 200_000, diaFechamento = 5, diaVencimento = 12))
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun foto(nome: String) { regra.waitForIdle(); regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png") }

    @Test
    fun registrarCompraComJuros() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onNodeWithText("Mais").performClick()
        regra.onNodeWithText("Cartões e crediários").performClick()
        esperar("Cartão Teste")
        regra.onNodeWithText("Cartão Teste").performClick()
        esperar("Faturas")
        regra.onNodeWithContentDescription("Nova compra").performClick()
        esperar("Valor total")
        regra.onNode(hasSetTextAction() and hasText("Descrição")).performTextInput("Celular")
        regra.onNode(hasSetTextAction() and hasText("Valor total")).performTextInput("30000")
        regra.onNode(hasSetTextAction() and hasText("Parcelas")).performTextClearance()
        regra.onNode(hasSetTextAction() and hasText("Parcelas")).performTextInput("12")
        esperar("Valor da parcela (se tiver juros)")
        regra.onNode(hasSetTextAction() and hasText("Valor da parcela (se tiver juros)")).performTextInput("4550")
        regra.waitUntil(10_000) { regra.onAllNodes(hasText("Juros do parcelamento: R$ 246,00", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        foto("v06-compra-com-juros")
        regra.onNodeWithText("Registrar compra").performScrollTo().performClick()

        esperar("Juros a pagar")
        foto("v06-cartao-juros-a-pagar")
        val credito = container.visaoCredito.value!!
        val id = credito.contasDeCredito.single().id
        regra.waitUntil(10_000) { container.visaoCredito.value!!.devido(id) == 30_000L }
        assertEquals(24_600L, container.visaoCredito.value!!.jurosAPagar(id, LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun compraAnteriorComAntecipada() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onNodeWithText("Mais").performClick()
        regra.onNodeWithText("Cartões e crediários").performClick()
        esperar("Cartão Teste")
        regra.onNodeWithText("Cartão Teste").performClick()
        esperar("Faturas")
        regra.onNodeWithContentDescription("Nova compra").performClick()
        esperar("Valor total")
        regra.onNode(hasSetTextAction() and hasText("Descrição")).performTextInput("Geladeira")
        regra.onNode(hasSetTextAction() and hasText("Valor total")).performTextInput("120000")
        regra.onNode(hasSetTextAction() and hasText("Parcelas")).performTextClearance()
        regra.onNode(hasSetTextAction() and hasText("Parcelas")).performTextInput("12")
        regra.onNode(androidx.compose.ui.test.isToggleable()).performScrollTo().performClick()
        esperar("Parcelas antecipadas (opcional)")
        regra.onNode(hasSetTextAction() and hasText("Parcelas antecipadas (opcional)")).performScrollTo().performTextInput("12")
        esperar("Parcela 12: prevista R$ 100,00")
        regra.onNode(hasSetTextAction() and hasText("Desconto")).performScrollTo().performTextInput("1000")
        foto("v06-compra-antecipada")
        regra.onNodeWithText("Registrar compra").performScrollTo().performClick()
        esperar("Compras")
        val vc = container.visaoCredito.value!!
        val id = vc.contasDeCredito.single().id
        regra.waitUntil(10_000) { container.visaoCredito.value!!.devido(id) == 120_000L - 10_000L }
        // A parcela 12 foi paga com R$ 90,00 + R$ 10,00 de desconto.
        assertEquals(1_000L, container.visaoCredito.value!!.ajustes.filter { it.natureza.ehDesconto }.sumOf { it.valor })
    }
}
