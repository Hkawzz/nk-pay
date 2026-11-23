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
import com.nkpay.app.dominio.TipoEmprestimo
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

/** Área Empréstimos pela interface (v0.5, PR 7). Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class EmprestimosUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 200_000, LocalDate.of(2026, 11, 1)))
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun foto(nome: String) { regra.waitForIdle(); regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png") }
    private fun digitar(rotulo: String, texto: String, limpar: Boolean = false) {
        val n = regra.onNode(hasSetTextAction() and hasText(rotulo))
        n.performScrollTo()
        if (limpar) n.performTextClearance()
        n.performTextInput(texto)
    }

    @Test
    fun cadastrarEmprestimoRecebidoEmParcelasAutomaticas() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onNodeWithText("Mais").performClick()
        regra.onNodeWithText("Empréstimos").performClick()
        esperar("Nenhum empréstimo recebido")
        regra.onNodeWithContentDescription("Novo empréstimo").performClick()
        esperar("Nome da pessoa ou instituição")
        digitar("Nome da pessoa ou instituição", "Banco X")
        digitar("Nome do empréstimo", "Empréstimo pessoal")
        digitar("Valor original", "100000")
        digitar("Quantidade", "10", limpar = true)
        digitar("Valor de cada parcela", "11000")
        esperar("10 parcelas · total R$ 1.100,00")
        esperar("Juros previstos: R$ 100,00")
        foto("v05-emprestimo-form")
        regra.onNodeWithText("Registrar empréstimo").performScrollTo().performClick()

        esperar("Empréstimo pessoal")
        foto("v05-emprestimos")
        val v = container.visaoEmprestimos.value!!
        val s = v.situacoes(TipoEmprestimo.RECEBIDO, LocalDate.of(2026, 12, 1)).single()
        assertEquals(110_000L, s.restante)
        assertEquals(110_000L, s.saldoNaConta)
        regra.onNodeWithText("Empréstimo pessoal").performClick()
        esperar("Parcela 1/10")
        foto("v05-emprestimo")
        regra.onNodeWithContentDescription("Ajuda: Juros previstos").assertExists()
    }
}
