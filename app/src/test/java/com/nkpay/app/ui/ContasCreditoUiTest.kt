package com.nkpay.app.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import kotlinx.coroutines.flow.first
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

/** Contas de crédito pela interface (v0.4, F3). Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ContasCreditoUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking { container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2026, 12, 1))) }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun foto(nome: String) { regra.waitForIdle(); regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png") }

    @Test
    fun cadastrarCartaoEleNaoApareceNosLancamentos() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onNodeWithText("Mais").performClick()
        regra.onNodeWithText("Cartões e crediários").performClick()
        esperar("Nenhum cartão ainda")
        regra.onNodeWithContentDescription("Novo cartão ou crediário").performClick()
        esperar("Dia de fechamento")
        regra.onNode(hasSetTextAction() and hasText("Nome")).performTextInput("Cartão Teste")
        regra.onNode(hasSetTextAction() and hasText("Limite (opcional)")).performTextInput("200000")
        regra.onNode(hasSetTextAction() and hasText("Dia de fechamento")).performTextInput("5")
        regra.onNode(hasSetTextAction() and hasText("Dia de vencimento")).performTextInput("12")
        foto("f3-form-cartao")
        regra.onNodeWithText("Cadastrar").performScrollTo().performClick()

        esperar("Cartão Teste")
        foto("f3-cartoes")
        val cartao = runBlocking { container.contas.observar().first() }.single { it.natureza == Natureza.CARTAO }
        assertEquals(200_000L, cartao.limite)
        assertEquals(5, cartao.diaFechamento)
        assertEquals(12, cartao.diaVencimento)
        assertEquals(0L, cartao.saldoInicial)

        regra.onNodeWithText("Cartão Teste").performClick()
        esperar("Faturas")
        foto("f3-cartao")
        regra.onNodeWithContentDescription("Voltar").performClick()
        regra.onNodeWithContentDescription("Voltar").performClick()

        // O cartão não aparece como conta para lançar.
        regra.onNodeWithText("Movimentações").performClick()
        regra.onNodeWithContentDescription("Nova movimentação").performClick()
        esperar("Lançar")
        regra.onNodeWithTag("campo:Conta").performScrollTo().performClick()
        esperar("Pix")
        assertEquals(0, regra.onAllNodes(hasText("Cartão Teste") and hasAnyAncestor(isDialog())).fetchSemanticsNodes().size)
    }
}
