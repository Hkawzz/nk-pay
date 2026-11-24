package com.hkawzz.nkote.ui

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
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dados.NkPayBanco
import com.hkawzz.nkote.dados.RelogioFalso
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.ui.theme.NkPayTema
import kotlinx.coroutines.runBlocking
import com.hkawzz.nkote.AvisosEmMemoria
import com.hkawzz.nkote.dados.MovimentoReservaEntidade
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.Objetivo
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** v0.8 (V3): a mensagem de que os objetivos viraram caixinhas aparece uma vez só. Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AvisoCaixinhasUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    private val avisos = AvisosEmMemoria()

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso(), avisos = avisos)
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2026, 11, 1)))
            val o = (container.objetivos.criar(Objetivo("", "Fone")) as Resultado.Sucesso).valor
            // Como um objetivo da v0.7: tinha R$ 300,00 "carimbados", que agora são só histórico.
            banco.movimentoReservaDao().inserir(MovimentoReservaEntidade("r1", o, "2026-11-20", 30_000, "GUARDAR", "g1", 1, 1, null))
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun mensagemApareceNaInicioEDepoisDeEntendiNaoVolta() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        esperar("Seus objetivos agora são caixinhas")
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/v08-aviso-caixinhas.png")
        // O guardado antigo não conta: o Saldo disponível é o saldo inteiro do Pix.
        assertEquals(100_000L, container.visao.value!!.resumo.disponivel)
        regra.onNodeWithText("Entendi").performScrollTo().performClick()
        regra.waitUntil(10_000) { regra.onAllNodes(hasText("Seus objetivos agora são caixinhas")).fetchSemanticsNodes().isEmpty() }
        assertTrue(avisos.conversaoV3Vista.value)
    }
}