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
import com.nkpay.app.dominio.ContaFixa
import com.nkpay.app.dominio.Obrigacao
import com.nkpay.app.dominio.OrigemObrigacao
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** v0.6 (7.6): o assistente Preparar mês pela interface. Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PrepararMesUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            container.contasFixas.criar(ContaFixa("", "Aluguel", 100_000, 10))
            container.contasFixas.criar(ContaFixa("", "Internet", 9_990, 15))
            container.obrigacoes.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Conserto", 25_000, LocalDate.of(2026, 10, 15), YearMonth.of(2026, 10)))
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun foto(nome: String) { regra.waitForIdle(); regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png") }

    @Test
    fun prepararDezembro() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onAllNodesWithText("A pagar").onFirst().performClick()
        esperar("Preparar dezembro de 2026?")
        foto("v06-apagar-sugestao")
        regra.onNodeWithText("Preparar dezembro de 2026").performScrollTo().performClick()
        esperar("Passo 1 de 4 · Contas fixas")
        esperar("Valor de Internet")
        regra.onNode(hasSetTextAction() and hasText("Valor de Internet")).performTextClearance()
        regra.onNode(hasSetTextAction() and hasText("Valor de Internet")).performTextInput("12000")
        foto("v06-preparar-1-fixas")
        regra.onNodeWithText("Próximo").performClick()
        esperar("Passo 2 de 4 · Parcelas previstas")
        regra.onNodeWithText("Próximo").performClick()
        esperar("Passo 3 de 4 · Pendências atrasadas")
        esperar("Conserto")
        foto("v06-preparar-3-pendencias")
        regra.onNodeWithText("Conserto").performClick()
        regra.onAllNodes(androidx.compose.ui.test.isToggleable()).onFirst().performClick()
        regra.onNodeWithText("Próximo").performClick()
        esperar("Passo 4 de 4 · Revisão")
        esperar("• 1 pendência(s) levada(s): R$ 250,00")
        foto("v06-preparar-4-revisao")
        // Nada foi gravado antes de confirmar.
        assertEquals(1, container.visaoAPagar.value!!.obrigacoes.size)
        regra.onNodeWithText("Confirmar").performClick()
        esperar("Dezembro de 2026 preparado: 2 conta(s) fixa(s) lançada(s) e 1 pendência(s) levada(s).")
        foto("v06-apagar-preparado")
        val v = container.visaoAPagar.value!!
        val dez = v.doMes(YearMonth.of(2026, 12), LocalDate.of(2026, 12, 1))
        assertEquals(setOf("Aluguel", "Internet", "Conserto"), dez.map { it.obrigacao.descricao }.toSet())
        assertEquals(12_000L, dez.single { it.obrigacao.descricao == "Internet" }.obrigacao.valorPrevisto)
    }
}