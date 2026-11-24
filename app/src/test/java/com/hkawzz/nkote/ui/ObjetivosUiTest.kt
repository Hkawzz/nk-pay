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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import com.hkawzz.nkote.dominio.Objetivo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** v0.7 (regra 6F): a aba Objetivos pela interface. Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ObjetivosUiTest {
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
    fun criarGuardarRetirarMoverEExcluir() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onAllNodesWithText("Objetivos").onFirst().performClick()
        esperar("Nenhum objetivo ainda")
        // Criar "Fone", meta R$ 300,00, prazo.
        regra.onNodeWithContentDescription("Novo objetivo").performClick()
        esperar("Criar objetivo")
        regra.onNode(hasSetTextAction() and hasText("Nome")).performTextInput("Fone")
        regra.onNode(hasSetTextAction() and hasText("Meta (opcional)")).performTextInput("30000")
        foto("v07-objetivo-novo")
        regra.onNodeWithText("Criar objetivo").performScrollTo().performClick()
        // v0.8: espera a LISTA (o "Fone" também está no campo do formulário; numa máquina lenta o toque ia para ele).
        esperar("Ativos")
        esperar("Fone")
        // Guardar R$ 300,00.
        regra.onNodeWithText("Fone").performClick()
        esperar("Guardar")
        regra.onNodeWithText("Guardar").performClick()
        esperar("Valor")
        regra.onNode(hasSetTextAction() and hasText("Valor")).performTextInput("30000")
        regra.onNodeWithText("Confirmar").performClick()
        esperar("100% da meta")
        assertEquals(30_000L, guardado("Fone"))
        assertEquals(70_000L, container.visao.value!!.resumo.disponivel)
        foto("v07-objetivo-detalhe")
        // Retirar R$ 100,00.
        regra.onNodeWithText("Retirar").performClick()
        esperar("Valor")
        regra.onNode(hasSetTextAction() and hasText("Valor")).performTextInput("10000")
        regra.onNodeWithText("Confirmar").performClick()
        esperar("66% da meta") // arredonda para baixo: nunca mostra 100% antes da hora
        assertEquals(20_000L, guardado("Fone"))
        // Excluir com guardado: recusado com a explicação.
        regra.onNodeWithText("Excluir").performScrollTo().performClick()
        esperar("Excluir “Fone”?")
        regra.onAllNodesWithText("Excluir").onLast().performClick()
        esperar("Retire ou mova os R$ 200,00", substring = true)
        // Mover para outro objetivo.
        runBlocking { container.objetivos.criar(Objetivo("", "Viagem")) }
        regra.onNodeWithText("Mover").performScrollTo().performClick()
        esperar("Para")
        regra.onNode(hasSetTextAction() and hasText("Valor")).performTextInput("5000")
        regra.onNodeWithText("Para").performClick()
        esperar("Viagem")
        regra.onAllNodesWithText("Viagem").onLast().performClick()
        regra.onNodeWithText("Confirmar").performScrollTo().performClick()
        esperar("Movido para Viagem")
        assertEquals(15_000L, guardado("Fone"))
        assertEquals(5_000L, guardado("Viagem"))
        assertEquals(80_000L, container.visao.value!!.resumo.disponivel)
        // A lista mostra o topo com o Guardado e o Saldo disponível.
        regra.onNodeWithContentDescription("Voltar").performClick()
        esperar("Ativos")
        foto("v07-objetivos-lista")
    }
}