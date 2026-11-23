package com.nkpay.app.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.nkpay.app.dados.NovaCompra
import com.nkpay.app.dados.RelogioFalso
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.StatusObrigacao
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

/** Pagar uma parcela de crediário com desconto e desfazer, pela interface (v0.4, F5). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CrediarioUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer
    private lateinit var loja: String

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2026, 11, 1)))
            loja = (container.contas.criar(Conta("", "Loja Teste", Natureza.CREDIARIO, null, 0, LocalDate.of(2026, 11, 1), diaVencimento = 20)) as Resultado.Sucesso).valor
            container.compras.registrar(NovaCompra(loja, "Sofá", null, LocalDate.of(2026, 11, 20), 30_000, 2, LocalDate.of(2026, 12, 20)))
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun foto(nome: String) { regra.waitForIdle(); regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png") }

    @Test
    fun pagarParcelaComDescontoEDesfazer() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onNodeWithText("Mais").performClick()
        regra.onNodeWithText("Cartões e crediários").performClick()
        esperar("Loja Teste")
        regra.onNodeWithText("Loja Teste").performClick()
        esperar("Sofá")
        regra.onNodeWithText("Sofá").performClick()
        esperar("Parcela 1/2")
        regra.onNodeWithText("Parcela 1/2").performClick()
        esperar("Confirmar pagamento")

        regra.onNode(hasSetTextAction() and hasText("Valor pago")).performTextClearance()
        regra.onNode(hasSetTextAction() and hasText("Valor pago")).performTextInput("14000")
        esperar("Você está pagando R$ 10,00 a menos. O que é essa diferença?")
        regra.onNodeWithText("Desconto").performClick()
        esperar("Fica paga.")
        foto("f5-pagar-parcela")
        regra.onNodeWithText("Confirmar pagamento").performScrollTo().performClick()

        regra.waitUntil(10_000) { container.visaoCredito.value?.devido(loja) == 15_000L }
        val s = container.visaoCredito.value!!.situacaoDaParcela(container.visaoCredito.value!!.parcelasDa(loja).first { it.numeroParcela == 1 }.id, LocalDate.of(2026, 12, 1))!!
        assertEquals(StatusObrigacao.QUITADA, s.status)
        esperar("Desfazer")
        foto("f5-parcela-paga")

        regra.onNodeWithText("Desfazer").performClick()
        regra.waitUntil(10_000) { container.visaoCredito.value?.devido(loja) == 30_000L }
    }
}
