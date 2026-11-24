package com.hkawzz.nkote.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dados.NkPayBanco
import com.hkawzz.nkote.dados.RelogioFalso
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.TipoBeneficio
import com.hkawzz.nkote.ui.contas.OrdemDasContasTela
import com.hkawzz.nkote.ui.inicio.linhasDaGrade
import com.hkawzz.nkote.ui.theme.NkPayTema
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** v0.9 (itens 2, 3 e 5): grades adaptativas da Início e "Editar ordem". Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class InicioV09UiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking { container.categorias.garantirCategoriasIniciais() }
    }

    @After fun fechar() = banco.close()

    private fun criar(nome: String, natureza: Natureza = Natureza.PROPRIO) = runBlocking {
        (container.contas.criar(Conta("", nome, natureza, if (natureza == Natureza.BENEFICIO) TipoBeneficio.VA else null, 10_000, LocalDate.of(2026, 11, 1))) as Resultado.Sucesso).valor
    }

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun largura(texto: String): Dp = regra.onNodeWithText(texto).getUnclippedBoundsInRoot().let { it.right - it.left }

    @Test
    fun `linhas da grade adaptativa`() {
        assertEquals(listOf(listOf(1)), linhasDaGrade(listOf(1), 2))
        assertEquals(listOf(listOf(1, 2), listOf(3)), linhasDaGrade(listOf(1, 2, 3), 2))
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(5)), linhasDaGrade(listOf(1, 2, 3, 4, 5), 2))
        assertEquals(listOf(listOf(1, 2, 3), listOf(4)), linhasDaGrade(listOf(1, 2, 3, 4), 3))
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5)), linhasDaGrade(listOf(1, 2, 3, 4, 5), 3))
    }

    @Test
    fun `tres contas ficam 2 mais 1 inteira e dois beneficios meio a meio`() {
        criar("Conta Alfa"); criar("Conta Beta"); criar("Conta Gama")
        criar("VA Um", Natureza.BENEFICIO); criar("VR Dois", Natureza.BENEFICIO)
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        esperar("Conta Gama")
        regra.waitForIdle()
        val meia = largura("Conta Alfa")
        assertEquals(meia.value, largura("Conta Beta").value, 1f)
        assertTrue("a terceira conta ocupa a largura toda", largura("Conta Gama") > meia * 1.8f)
        regra.onNodeWithText("VR Dois").performScrollTo()
        assertEquals(largura("VA Um").value, largura("VR Dois").value, 1f)
        assertTrue("dois benefícios dividem a linha", largura("VA Um") < largura("Conta Gama") * 0.6f)
        regra.onNodeWithText("Editar ordem").assertExists()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/v09-inicio-grades.png")
    }

    @Test
    fun `editar ordem pelas setas grava a ordem nova`() {
        val a = criar("Banco"); val b = criar("Pix"); criar("VA", Natureza.BENEFICIO)
        var voltou = false
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { OrdemDasContasTela(aoVoltar = { voltou = true }) } } }
        esperar("Banco")
        regra.onNodeWithContentDescription("Descer Banco").performClick()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/v09-editar-ordem.png")
        // Nada é gravado antes de salvar.
        assertEquals(listOf("Banco", "Pix", "VA"), runBlocking { container.contas.observar().first() }.map { it.nome })
        regra.onNodeWithText("Salvar ordem").performScrollTo().performClick()
        regra.waitUntil(10_000) { voltou }
        assertEquals(listOf(b, a), runBlocking { container.contas.observar().first() }.filter { it.natureza == Natureza.PROPRIO }.map { it.id })
    }
}
