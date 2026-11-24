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
import com.hkawzz.nkote.dados.NovaCompra
import com.hkawzz.nkote.dados.Resultado
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** v0.8 (D1): excluir um lançamento protegido pela aba Movimentações explica e leva à tela certa. Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ExclusaoProtegidaUiTest {
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
            val cartao = (container.contas.criar(Conta("", "Cartão Teste", Natureza.CARTAO, null, 0, LocalDate.of(2026, 11, 1), diaFechamento = 5, diaVencimento = 12)) as Resultado.Sucesso).valor
            container.compras.registrar(NovaCompra(cartao, "Fone de ouvido", null, LocalDate.of(2026, 12, 1), 10_000, 1, LocalDate.of(2026, 12, 12)))
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun excluirDespesaDeCompraExplicaELevaAoCartao() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onAllNodesWithText("Movimentações").onFirst().performClick()
        esperar("Fone de ouvido")
        regra.onAllNodesWithText("Fone de ouvido").onFirst().performClick()
        esperar("Editar movimentação")
        regra.waitUntil(10_000) { regra.onAllNodes(androidx.compose.ui.test.hasContentDescription("Excluir")).fetchSemanticsNodes().isNotEmpty() }
        regra.onNodeWithContentDescription("Excluir").performClick()
        esperar("Não dá para excluir por aqui")
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/v08-excluir-protegida.png")
        regra.onNodeWithText("Abrir cartão ou crediário").performClick()
        esperar("Faturas")
        esperar("Cartão Teste")
        // Nada foi excluído.
        assertEquals(1, container.visaoCredito.value!!.compras.size)
    }
}