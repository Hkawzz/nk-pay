package com.hkawzz.nkote.ui

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
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dados.NkPayBanco
import com.hkawzz.nkote.dados.RelogioFalso
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
import com.hkawzz.nkote.ui.theme.NkPayTema
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
