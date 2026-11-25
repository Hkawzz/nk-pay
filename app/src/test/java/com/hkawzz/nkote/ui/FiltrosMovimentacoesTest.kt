package com.hkawzz.nkote.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dados.NkPayBanco
import com.hkawzz.nkote.dados.RelogioFalso
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.ui.movimentacoes.FiltroMovimentacoes
import com.hkawzz.nkote.ui.movimentacoes.MovimentacoesTela
import com.hkawzz.nkote.ui.theme.NkPayTema
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/** v0.9 (item 8): filtros por data e categoria em Movimentações, combináveis, com o total do resultado. Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class FiltrosMovimentacoesTest {
    @get:Rule val regra = createComposeRule()

    private fun m(id: String, tipo: TipoMovimentacao, dia: Int, valor: Long, cat: String? = null, mes: Int = 12, conta: String = "pix", destino: String? = null) =
        Movimentacao(id, tipo, LocalDate.of(2026, mes, dia), valor, conta, destino, cat, id)

    private val movs = listOf(
        m("a", TipoMovimentacao.DESPESA, 1, 1_000, "ali"),
        m("b", TipoMovimentacao.DESPESA, 2, 2_000, "tra"),
        m("c", TipoMovimentacao.ENTRADA, 2, 5_000, "sal"),
        m("d", TipoMovimentacao.TRANSFERENCIA, 2, 9_000, destino = "din"),
        m("e", TipoMovimentacao.AJUSTE, 3, -300),
        m("f", TipoMovimentacao.DESPESA, 30, 4_000, "ali", mes = 11),
    )

    @Test
    fun `filtros combinaveis e totais sem transferencias nem ajustes`() {
        val dez = YearMonth.of(2026, 12)
        assertFalse(FiltroMovimentacoes().ativo)
        assertEquals(listOf("a", "b", "c", "d", "e"), FiltroMovimentacoes().aplicar(movs, dez).map { it.id })
        // Um dia.
        val dia2 = FiltroMovimentacoes(de = LocalDate.of(2026, 12, 2), ate = LocalDate.of(2026, 12, 2))
        assertEquals(listOf("b", "c", "d"), dia2.aplicar(movs, dez).map { it.id })
        // Intervalo que passa do mês (o seletor de mês fica de lado), em qualquer ordem.
        val intervalo = FiltroMovimentacoes(de = LocalDate.of(2026, 12, 1), ate = LocalDate.of(2026, 11, 29))
        assertEquals(listOf("a", "f"), intervalo.aplicar(movs, dez).map { it.id })
        // Várias categorias, combinadas com data e tipo.
        val cats = FiltroMovimentacoes(categorias = setOf("ali", "sal"))
        assertEquals(listOf("a", "c"), cats.aplicar(movs, dez).map { it.id })
        assertEquals(listOf("a", "f"), cats.copy(de = LocalDate.of(2026, 11, 1), ate = LocalDate.of(2026, 12, 31), tipo = TipoMovimentacao.DESPESA).aplicar(movs, dez).map { it.id })
        assertTrue(cats.ativo)
        // Totais: transferência e ajuste não somam.
        assertEquals(5_000L to 3_000L, FiltroMovimentacoes.totais(dia2.aplicar(movs, dez) + movs.first()))
        assertEquals(0L to 0L, FiltroMovimentacoes.totais(listOf(movs[3], movs[4])))
    }

    @Test
    fun `na tela - filtrar por hoje e por categoria mostra o total e Limpar volta tudo`() {
        val banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        try {
            val c = AppContainer(banco, RelogioFalso()) // hoje = 01/12/2026
            runBlocking {
                c.categorias.garantirCategoriasIniciais()
                val pix = (c.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2026, 11, 1))) as Resultado.Sucesso).valor
                val cats = c.categorias.observar().first()
                val ali = cats.first { it.nome == "Alimentação" }.id
                val outra = cats.first { it.nome != "Alimentação" && !it.arquivada }
                c.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, LocalDate.of(2026, 12, 1), 1_234, pix, null, ali, "Padaria"))
                c.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, LocalDate.of(2026, 12, 1), 5_000, pix, null, outra.id, "Outra coisa"))
                c.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, LocalDate.of(2026, 11, 20), 7_000, pix, null, ali, "Mercado antigo"))
            }
            regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides c) { MovimentacoesTela(aoAbrir = {}, aoNova = {}) } } }
            regra.waitUntil(10_000) { regra.onAllNodes(hasText("Padaria")).fetchSemanticsNodes().isNotEmpty() }
            regra.onNodeWithText("Data").performClick()
            regra.onNodeWithText("Filtrar por hoje").performClick()
            regra.onNodeWithText("Categorias").performClick()
            regra.onNodeWithText("Alimentação").performClick()
            regra.waitUntil(10_000) { regra.onAllNodes(hasText("Outra coisa")).fetchSemanticsNodes().isEmpty() }
            regra.onNodeWithText("Resultado do filtro · 1 lançamento").assertExists()
            regra.onNodeWithText("Filtros ativos: data, categorias").assertExists()
            assertEquals(0, regra.onAllNodes(hasText("Mercado antigo")).fetchSemanticsNodes().size)
            regra.onRoot().captureRoboImage("build/outputs/roborazzi/v09-filtros.png")
            regra.onNodeWithText("Limpar").performClick()
            regra.waitUntil(10_000) { regra.onAllNodes(hasText("Outra coisa")).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(0, regra.onAllNodes(hasText("Resultado do filtro", substring = true)).fetchSemanticsNodes().size)
        } finally {
            banco.close()
        }
    }
}
