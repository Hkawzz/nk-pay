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
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTextClearance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** v0.6 (A3, A5): pessoas, excluir e arquivados pela interface. Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ArquivarUiTest {
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
    fun pessoasEditarNomeEArquivar() {
        runBlocking { container.contas.criar(Conta("", "Maria", Natureza.EMPRESTIMO_RECEBIDO, null, 0, LocalDate.of(2026, 12, 1))) }
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onNodeWithText("Mais").performClick()
        esperar("Empréstimos")
        regra.onNodeWithText("Empréstimos").performClick()
        esperar("Pessoas")
        regra.onNodeWithText("Pessoas").performClick()
        esperar("Maria")
        foto("v06-pessoas")
        regra.onNodeWithText("Maria").performClick()
        esperar("Salvar nome")
        regra.onNode(hasSetTextAction() and hasText("Maria")).performTextClearance()
        regra.onNode(hasSetTextAction() and hasText("Nome")).performTextInput("Maria Souza")
        regra.onNodeWithText("Salvar nome").performClick()
        esperar("Nome salvo.")
        regra.onNodeWithText("Arquivar").performClick()
        esperar("Mostrar arquivadas")
        regra.waitUntil(10_000) { container.visao.value!!.contas.any { it.nome == "Maria Souza" && it.arquivada } }
        regra.onNodeWithText("Mostrar arquivadas").performClick()
        esperar("Maria Souza")
    }

    @Test
    fun cartaoSemNadaExcluiPeloFormulario() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onNodeWithText("Mais").performClick()
        regra.onNodeWithText("Cartões e crediários").performClick()
        esperar("Cartão Teste")
        regra.onNodeWithText("Mostrar arquivados").performClick()
        esperar("Nenhum cartão ou crediário arquivado.")
        regra.onNodeWithText("Cartão Teste").performClick()
        esperar("Faturas")
        regra.onNodeWithContentDescription("Editar").performClick()
        esperar("Salvar alterações")
        regra.onNodeWithText("Excluir").performScrollTo().performClick()
        esperar("Excluir “Cartão Teste”?")
        regra.onAllNodesWithText("Excluir").onLast().performClick()
        regra.waitUntil(10_000) { container.visaoCredito.value!!.contasDeCredito.isEmpty() }
        esperar("Nenhum cartão ainda")
    }
}