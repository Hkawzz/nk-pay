package com.nkpay.app.ui

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
import com.nkpay.app.AppContainer
import com.nkpay.app.dados.NkPayBanco
import com.nkpay.app.dados.RelogioFalso
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Movimentacao
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.TipoBeneficio
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.ui.theme.NkPayTema
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Capturas de referência das telas (etapa V0 da v0.4): a base de comparação do
 * design system v2. Dados fictícios. As capturas ficam no artefato do CI com o
 * prefixo "ref-"; a cada PR visual, o artefato mostra o depois com os mesmos nomes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ReferenciaVisualTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer
    private val dia = LocalDate.of(2026, 12, 1)

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            fun ok(r: Resultado<String>) = (r as Resultado.Sucesso).valor
            val pix = ok(container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 180_000, dia)))
            ok(container.contas.criar(Conta("", "Carteira", Natureza.PROPRIO, null, 9_500, dia)))
            val va = ok(container.contas.criar(Conta("", "Vale A", Natureza.BENEFICIO, TipoBeneficio.VA, 40_000, dia)))
            val cats = container.categorias.observar().first()
            fun cat(n: String) = cats.first { it.nome == n }.id
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.ENTRADA, dia, 320_000, pix, descricao = "Salário"))
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 6_590, va, categoriaId = cat("Alimentação"), descricao = "Mercado"))
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 11_990, pix, categoriaId = cat("Moradia"), descricao = "Internet"))
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun foto(nome: String) { regra.waitForIdle(); regra.onRoot().captureRoboImage("build/outputs/roborazzi/ref-$nome.png") }

    @Test
    fun capturasDeReferencia() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        esperar("Últimas movimentações")
        foto("inicio")

        regra.onNodeWithText("Movimentações").performClick()
        esperar("Transferências e ajustes não entram nesses totais.")
        foto("movimentacoes")
        regra.onNodeWithContentDescription("Nova movimentação").performClick()
        esperar("Lançar")
        foto("form-despesa")
        regra.onNodeWithContentDescription("Voltar").performClick()

        regra.onNodeWithText("A pagar").performClick()
        regra.waitForIdle()
        foto("a-pagar")

        regra.onNodeWithText("Mais").performClick()
        esperar("Contas e carteiras")
        foto("mais")
        regra.onNodeWithText("Categorias").performClick()
        esperar("Em uso")
        foto("categorias")
        regra.onNodeWithContentDescription("Voltar").performClick()
        regra.onNodeWithText("Contas e carteiras").performClick()
        esperar("Dinheiro próprio")
        foto("contas")
    }
}
