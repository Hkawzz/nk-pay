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
import com.hkawzz.nkote.dados.NovoEmprestimo
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.InicioCompleta
import com.hkawzz.nkote.dominio.Objetivo
import com.hkawzz.nkote.dominio.Obrigacao
import com.hkawzz.nkote.dominio.OrigemObrigacao
import com.hkawzz.nkote.dominio.TipoEmprestimo
import java.time.YearMonth
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** v0.8 (V1, regra 6G.5): a Início completa; cada bloco só aparece com conteúdo. Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class InicioCompletaUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    private lateinit var pix: String
    private val hoje = LocalDate.of(2026, 12, 1)

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            pix = (container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2026, 11, 1))) as Resultado.Sucesso).valor
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun quantos(texto: String) = regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().size
    private fun <T> ok(r: Resultado<T>) = (r as Resultado.Sucesso).valor

    @Test
    fun blocosSoAparecemComConteudo() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        esperar("Saldo disponível")
        regra.waitForIdle()
        for (b in listOf("Próximos vencimentos", "Cartões", "Caixinhas", "A receber")) assertEquals("bloco $b sem conteúdo", 0, quantos(b))

        runBlocking {
            // Conta a pagar vencendo em 3 dias, outra atrasada e outra fora da janela (10 dias).
            ok(container.obrigacoes.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Luz", 10_000, hoje.plusDays(3), YearMonth.of(2026, 12))))
            ok(container.obrigacoes.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Água", 5_000, hoje.minusDays(2), YearMonth.of(2026, 11))))
            ok(container.obrigacoes.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Internet", 9_000, hoje.plusDays(10), YearMonth.of(2026, 12))))
            // Cartão com fatura vencendo em 11 dias: aparece em Cartões, não nos vencimentos.
            val cartao = ok(container.contas.criar(Conta("", "Cartão Teste", Natureza.CARTAO, null, 0, LocalDate.of(2026, 11, 1), diaFechamento = 5, diaVencimento = 12)))
            ok(container.compras.registrar(NovaCompra(cartao, "Fone", null, hoje, 20_000, 1, LocalDate.of(2026, 12, 12))))
            // Caixinha com dinheiro.
            val viagem = ok(container.objetivos.criar(Objetivo("", "Viagem", meta = 50_000)))
            ok(container.objetivos.guardar(viagem, pix, 10_000, hoje))
            // Empréstimo concedido com parcela em 5 dias.
            ok(container.emprestimos.registrar(NovoEmprestimo(TipoEmprestimo.CONCEDIDO, null, "Tonho", "Ajuda", null, 20_000, hoje, pix, listOf(hoje.plusDays(5) to 20_000))))
        }
        esperar("Próximos vencimentos")
        esperar("A receber")
        // v0.9 (item 1): Cartões e Caixinhas saíram da Início, mesmo com conteúdo.
        for (b in listOf("Cartões", "Caixinhas")) assertEquals("bloco $b", 0, quantos(b))
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/v08-inicio-completa.png")

        val va = container.visaoAPagar.value
        val vc = container.visaoCredito.value
        val ve = container.visaoEmprestimos.value
        val venc = InicioCompleta.proximosVencimentos(va, vc, ve, hoje)
        assertEquals(listOf("Água", "Luz"), venc.map { it.titulo }) // atrasada primeiro; a Internet (10 dias) e a fatura (11 dias) ficam fora
        assertTrue(venc.first().atrasado)
        assertEquals(listOf(20_000L), InicioCompleta.cartoes(vc, hoje).map { it.devido })
        assertEquals(listOf("Ajuda · Tonho"), InicioCompleta.aReceber(ve, hoje).map { it.titulo }.map { it.substringBefore(" (") })
        assertEquals(listOf("Viagem"), InicioCompleta.caixinhas(container.visao.value!!.objetivos).first.map { it.objetivo.nome })
    }
}