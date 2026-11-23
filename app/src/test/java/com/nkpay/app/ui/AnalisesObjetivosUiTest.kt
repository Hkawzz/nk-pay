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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import com.nkpay.app.dominio.Objetivo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** v0.7 (regra 6F): a seção Objetivos das Análises. Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AnalisesObjetivosUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2026, 11, 1)))
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String, substring: Boolean = false) =
        regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
    private fun foto(nome: String) { regra.waitForIdle(); regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png") }
    private fun guardado(nome: String) = container.visao.value!!.objetivos.let { v -> v.guardado(v.objetivos.first { it.nome == nome }.id) }

    @Test
    fun secaoObjetivosMostraGuardadoERetiradoNoPeriodo() {
        val dia = LocalDate.of(2026, 12, 1)
        runBlocking {
            val fone = (container.objetivos.criar(Objetivo("", "Fone", meta = 30_000)) as com.nkpay.app.dados.Resultado.Sucesso).valor
            container.objetivos.guardar(fone, 30_000, dia)
            container.objetivos.retirar(fone, 10_000, dia)
        }
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onNodeWithText("Mais").performClick()
        regra.onNodeWithText("Análises").performClick()
        esperar("Este mês")
        regra.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(hasText("Guardado agora"))
        esperar("Retirado no período")
        esperar("R$ 200,00 de R$ 300,00 (66%)")
        regra.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(hasText("Fone"))
        foto("v07-analises-objetivos")
        val vo = container.visao.value!!.objetivos
        val p = com.nkpay.app.dominio.Periodo(java.time.YearMonth.of(2026, 12), java.time.YearMonth.of(2026, 12))
        assertEquals(30_000L, vo.guardadoNoPeriodo(p))
        assertEquals(10_000L, vo.retiradoNoPeriodo(p))
        // Não é entrada nem despesa.
        assertEquals(0, container.visao.value!!.movimentacoes.size)
    }
}