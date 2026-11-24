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
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Objetivo
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.ui.inicio.AVISO_GUARDADO_MAIOR
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** v0.7: a Início com Guardado > 0 pela primeira vez (regras 6E.3 e 6F). Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class InicioGuardadoUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    private lateinit var pix: String
    private lateinit var fone: String
    private val dia = LocalDate.of(2026, 12, 1)

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            pix = (container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2026, 11, 1))) as com.hkawzz.nkote.dados.Resultado.Sucesso).valor
            fone = (container.objetivos.criar(Objetivo("", "Fone", meta = 30_000)) as com.hkawzz.nkote.dados.Resultado.Sucesso).valor
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun sumiu(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isEmpty() }
    private fun foto(nome: String) { regra.waitForIdle(); regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png") }

    @Test
    fun inicioMostraTotalEGuardadoSoComAlgoGuardado() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        esperar("Saldo disponível")
        esperar("R$ 1.000,00")
        assertEquals(0, regra.onAllNodes(hasText("Guardado")).fetchSemanticsNodes().size)
        // Guardar R$ 300,00: Saldo disponível R$ 700,00, Saldo total R$ 1.000,00 e Guardado R$ 300,00.
        runBlocking { container.objetivos.guardar(fone, 30_000, dia) }
        esperar("R$ 700,00")
        esperar("Saldo total")
        esperar("Guardado")
        esperar("R$ 300,00")
        foto("v07-inicio-com-guardado")
        // O2: uma despesa de R$ 900,00 deixa o disponível em −R$ 200,00, com o aviso; nada é reduzido.
        runBlocking { container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 90_000, pix, null, null, "Compra grande")) }
        esperar(AVISO_GUARDADO_MAIOR)
        foto("v07-inicio-guardado-maior")
        assertEquals(30_000L, container.visao.value!!.resumo.reservado)
        assertEquals(-20_000L, container.visao.value!!.resumo.disponivel)
        // Retirar tudo: a Início volta a mostrar só o Saldo disponível.
        runBlocking { container.objetivos.retirar(fone, 30_000, dia) }
        sumiu("Saldo total")
        sumiu(AVISO_GUARDADO_MAIOR)
        assertEquals(0, regra.onAllNodes(hasText("Guardado")).fetchSemanticsNodes().size)
    }
}