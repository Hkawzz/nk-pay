package com.hkawzz.nkote.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/** Nova tela Início (adendo-inicio-v0.4). Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InicioV2Test {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer
    private val hoje = LocalDate.of(2026, 12, 1)

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            fun ok(r: Resultado<String>) = (r as Resultado.Sucesso).valor
            val banco1 = ok(container.contas.criar(Conta("", "Banco Exemplo", Natureza.PROPRIO, null, 1_234_567, hoje, icone = "banco")))
            ok(container.contas.criar(Conta("", "Carteira digital com nome comprido", Natureza.PROPRIO, null, 5_552, hoje, icone = "carteira")))
            ok(container.contas.criar(Conta("", "Dinheiro físico", Natureza.PROPRIO, null, 0, hoje, icone = "dinheiro")))
            ok(container.contas.criar(Conta("", "Outra conta", Natureza.PROPRIO, null, 1_000, hoje)))
            ok(container.contas.criar(Conta("", "Quinta conta", Natureza.PROPRIO, null, 2_000, hoje)))
            val va = ok(container.contas.criar(Conta("", "Vale A", Natureza.BENEFICIO, TipoBeneficio.VA, 31_000, hoje)))
            ok(container.contas.criar(Conta("", "Vale R", Natureza.BENEFICIO, TipoBeneficio.VR, 0, hoje)))
            ok(container.contas.criar(Conta("", "Auxílio", Natureza.BENEFICIO, TipoBeneficio.OUTRO, 49_000, hoje)))
            ok(container.contas.criar(Conta("", "Quarto benefício", Natureza.BENEFICIO, TipoBeneficio.OUTRO, 100, hoje)))
            // Cartão: não aparece na Início.
            container.contas.criar(Conta("", "Cartão Fora", Natureza.CARTAO, null, 0, hoje, diaFechamento = 5, diaVencimento = 12))
            val cats = container.categorias.observar().first()
            fun cat(n: String) = cats.first { it.nome == n }.id
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.ENTRADA, hoje, 377_026, banco1, descricao = "Recebimento"))
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, hoje, 8_500, banco1, categoriaId = cat("Alimentação"), descricao = "Mercado"))
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, hoje.minusDays(1), 12_000, banco1, categoriaId = cat("Moradia"), descricao = "Internet"))
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, hoje.minusDays(1), 2_500, va, categoriaId = cat("Alimentação"), descricao = "Almoço"))
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun foto(nome: String) { regra.waitForIdle(); regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png") }
    private fun abrirApp() = regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }

    @Test @Config(qualifiers = "w411dp-h2000dp-xxhdpi")
    fun inicioCompleta() {
        abrirApp()
        esperar("Minhas contas")
        esperar("Últimas movimentações")
        foto("v2-inicio")
        // 4 contas e 3 benefícios visíveis; o resto aparece no "Ver todas".
        assertEquals(0, regra.onAllNodes(hasText("Quinta conta")).fetchSemanticsNodes().size)
        assertEquals(0, regra.onAllNodes(hasText("Vale R")).fetchSemanticsNodes().size) // por nome, é o 4º
        assertEquals(0, regra.onAllNodes(hasText("Cartão Fora")).fetchSemanticsNodes().size)
        regra.onNodeWithText("Ver todas as contas").performScrollTo().performClick()
        esperar("Quinta conta")
        esperar("Recolher")
        regra.onNodeWithText("Ver todos os benefícios").performScrollTo().performClick()
        esperar("Vale R")
        foto("v2-inicio-aberta")
        regra.onNodeWithText("HOJE").assertExists()
        regra.onNodeWithText("ONTEM").assertExists()
        // Dezembro: 1 entrada e 1 despesa (as de ontem, 30/11, são de novembro).
        assertEquals(2, regra.onAllNodes(hasText("1 movimentação")).fetchSemanticsNodes().size)
        // Tocar num card abre a conta.
        regra.onNodeWithText("Dinheiro físico").performScrollTo().performClick()
        esperar("Saldo atual")
    }

    @Test @Config(sdk = [26], qualifiers = "w360dp-h1600dp-xxhdpi")
    fun inicioAndroid8TelaEstreita() {
        abrirApp()
        esperar("Minhas contas")
        foto("v2-inicio-android-8-estreita")
    }
}
