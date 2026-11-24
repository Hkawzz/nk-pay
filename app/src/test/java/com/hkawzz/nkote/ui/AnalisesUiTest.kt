package com.hkawzz.nkote.ui

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
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dados.NkPayBanco
import com.hkawzz.nkote.dados.NovaCompra
import com.hkawzz.nkote.dados.RelogioFalso
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.FiltroPeriodo
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.TipoBeneficio
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.ui.theme.NkPayTema
import kotlinx.coroutines.flow.first
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

/** Tela de Análises (v0.4, A2), com dados fictícios em 3 meses. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2200dp-xxhdpi")
class AnalisesUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
    }

    @After fun fechar() = banco.close()

    private fun dados() = runBlocking {
        container.categorias.garantirCategoriasIniciais()
        fun ok(r: Resultado<String>) = (r as Resultado.Sucesso).valor
        val d0 = LocalDate.of(2026, 10, 1)
        val pix = ok(container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, d0)))
        val va = ok(container.contas.criar(Conta("", "Vale A", Natureza.BENEFICIO, TipoBeneficio.VA, 30_000, d0)))
        val cartao = ok(container.contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, d0, limite = 300_000, diaFechamento = 5, diaVencimento = 12)))
        val cats = container.categorias.observar().first()
        fun cat(n: String) = cats.first { it.nome == n }.id
        for ((mes, salario) in listOf(10 to 300_000L, 11 to 300_000L, 12 to 320_000L)) {
            val dia = LocalDate.of(2026, mes, 1)
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.ENTRADA, dia, salario, pix, descricao = "Salário"))
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 90_000 + mes * 1_000L, pix, categoriaId = cat("Moradia"), descricao = "Aluguel"))
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 25_000, va, categoriaId = cat("Alimentação"), descricao = "Mercado"))
        }
        container.compras.registrar(NovaCompra(cartao, "Fone", cats.first { it.nome == "Tecnologia" }.id, LocalDate.of(2026, 12, 1), 60_000, 3, LocalDate.of(2026, 12, 12)))
        container.pagamentosCredito.pagarFatura(cartao, java.time.YearMonth.of(2026, 12), pix, LocalDate.of(2026, 12, 1), 20_000, null)
    }

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun foto(nome: String) { regra.waitForIdle(); regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png") }
    private fun abrirAnalises() {
        regra.onNodeWithText("Mais").performClick()
        regra.onNodeWithText("Análises").performClick()
        esperar("Despesas por categoria")
    }

    @Test
    fun analisesComFiltroGuardadoNaMemoria() {
        dados()
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        abrirAnalises()
        esperar("Fluxo do dinheiro próprio")
        foto("a2-analises-mes")
        regra.onNodeWithText("3 meses").performClick()
        esperar("Outubro de 2026 a dezembro de 2026")
        foto("a2-analises-3-meses")
        assertEquals(FiltroPeriodo.TRES, container.filtroAnalises.value)

        // A aba Mais volta ao menu, mas o filtro continua na memória.
        regra.onNodeWithText("Início").performClick()
        abrirAnalises()
        esperar("Outubro de 2026 a dezembro de 2026")
    }

    @Test
    fun analisesSemDados() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        abrirAnalises()
        esperar("Nenhuma despesa neste período.")
        foto("a2-analises-vazia")
    }
}
