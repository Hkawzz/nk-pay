package com.hkawzz.nkote.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dados.ContaFixaRepositorio.OcorrenciaAtrasada
import com.hkawzz.nkote.dados.NkPayBanco
import com.hkawzz.nkote.dados.NovaCompra
import com.hkawzz.nkote.dados.RelogioFalso
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.ContaFixa
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Obrigacao
import com.hkawzz.nkote.dominio.OrigemObrigacao
import com.hkawzz.nkote.dominio.StatusObrigacao
import com.hkawzz.nkote.ui.apagar.APagarTela
import com.hkawzz.nkote.ui.theme.NkPayTema
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/** v0.9 (itens 4 e 6): atrasos em A pagar e conta fixa já vencida no cadastro. Data simulada: hoje = 01/12/2026. Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class AtrasosUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer
    private val hoje = LocalDate.of(2026, 12, 1)
    private val dez = YearMonth.of(2026, 12)

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking { container.categorias.garantirCategoriasIniciais() }
    }

    @After fun fechar() = banco.close()

    private fun <T> ok(r: Resultado<T>) = (r as Resultado.Sucesso).valor
    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `texto de atraso`() {
        assertNull(Formatos.venceuHa(hoje, hoje)) // no dia do vencimento ainda não está atrasada
        assertNull(Formatos.venceuHa(hoje.plusDays(2), hoje))
        assertNull(Formatos.venceuHa(null, hoje))
        assertEquals("venceu ontem", Formatos.venceuHa(hoje.minusDays(1), hoje))
        assertEquals("venceu há 5 dias", Formatos.venceuHa(hoje.minusDays(5), hoje))
    }

    @Test
    fun `atrasadas ficam no topo, da mais antiga para a mais nova, em vermelho com venceu ha`() {
        runBlocking {
            ok(container.obrigacoes.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Água", 5_000, hoje.minusDays(1), YearMonth.of(2026, 11))))
            ok(container.obrigacoes.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Luz", 10_000, hoje.minusDays(10), YearMonth.of(2026, 11))))
            ok(container.obrigacoes.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Internet", 9_000, hoje, dez))) // vence hoje: não está atrasada
            val cartao = ok(container.contas.criar(Conta("", "Cartão Teste", Natureza.CARTAO, null, 0, LocalDate.of(2026, 9, 1), diaFechamento = 5, diaVencimento = 12)))
            ok(container.compras.registrar(NovaCompra(cartao, "Fone", null, LocalDate.of(2026, 10, 20), 20_000, 1, LocalDate.of(2026, 11, 12))))
        }
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { APagarTela(aoNova = {}, aoAbrir = {}, aoContasFixas = {}) } } }
        esperar("Atrasadas")
        esperar("venceu há 10 dias")
        esperar("venceu ontem")
        esperar("venceu há 19 dias") // a fatura de novembro venceu em 12/11
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/v09-a-pagar-atrasadas.png")
        // Ordem: fatura (12/11), Luz (21/11), Água (30/11); a Internet fica em "Deste mês", pendente.
        val y = { t: String -> regra.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().first().boundsInRoot.top }
        assertTrue(y("Fatura Cartão Teste") < y("Luz"))
        assertTrue(y("Luz") < y("Água"))
        assertTrue(y("Água") < y("Deste mês"))
        assertTrue(y("Deste mês") < y("Internet"))
        regra.onNodeWithText("Vence 01/12/2026", substring = true).assertExists()
        assertEquals(0, regra.onAllNodes(hasText("ref. 11/2026", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun `conta fixa cadastrada ja vencida aparece na hora como atrasada e nunca duplica`() = runBlocking {
        // Data no futuro é recusada.
        assertTrue(container.contasFixas.criar(ContaFixa("", "Aluguel", 80_000, 10), OcorrenciaAtrasada(hoje, 80_000)) is Resultado.Falha)
        val fixa = ok(container.contasFixas.criar(ContaFixa("", "Aluguel", 80_000, 10), OcorrenciaAtrasada(LocalDate.of(2026, 11, 10), 75_000)))
        val va = container.visaoAPagar.first { v -> v?.obrigacoes?.any { it.contaFixaId == fixa } == true }!!
        val o = va.obrigacoes.single { it.contaFixaId == fixa }
        assertEquals(YearMonth.of(2026, 11), o.mesReferencia)
        assertEquals(75_000L, o.valorPrevisto)
        assertEquals(StatusObrigacao.ATRASADA, va.situacao(o, hoje).status)
        // Lançar as fixas de novembro (ou Preparar novembro) não duplica; dezembro segue normal.
        assertEquals(0, ok(container.obrigacoes.lancarFixas(YearMonth.of(2026, 11), listOf(fixa))))
        assertEquals(1, ok(container.obrigacoes.lancarFixas(dez, listOf(fixa))))
        // Uma segunda ocorrência no mesmo mês é recusada (a regra de uma por mês).
        assertTrue(container.contasFixas.lancarAtrasada(fixa, OcorrenciaAtrasada(LocalDate.of(2026, 11, 20), 1_000)) is Resultado.Falha)
        // Na edição, dá para lançar a de outro mês.
        ok(container.contasFixas.lancarAtrasada(fixa, OcorrenciaAtrasada(LocalDate.of(2026, 10, 10), 80_000)))
        assertEquals(3, banco.backupDao().obrigacoes().count { it.contaFixaId == fixa && it.excluidoEm == null })
        assertTrue(banco.auditoriaDao().listarTodas().any { it.resumo.contains("vencida em 2026-11-10") })
    }

    @Test
    fun `formulario da conta fixa cria a ocorrencia vencida`() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { com.hkawzz.nkote.ui.apagar.ContaFixaFormTela(null, aoVoltar = {}) } } }
        esperar("Nome")
        regra.onNode(androidx.compose.ui.test.hasSetTextAction() and hasText("Nome")).performTextInput("Condomínio")
        regra.onNode(androidx.compose.ui.test.hasSetTextAction() and hasText("Valor de costume")).performTextInput("12000")
        regra.onNodeWithText("Já tenho uma conta deste tipo vencida e não paga").performScrollTo()
        regra.onAllNodes(androidx.compose.ui.test.isToggleable())[0].performClick()
        esperar("Se houver mais de uma vencida")
        regra.onNodeWithText("Criar conta fixa").performScrollTo().performClick()
        // Espera pelo banco (o retorno da tela vem de uma corrotina do ViewModel).
        regra.waitUntil(10_000) { runBlocking { banco.backupDao().obrigacoes().isNotEmpty() } }
        assertEquals(listOf("Condomínio"), runBlocking { banco.backupDao().contasFixas() }.map { it.nome })
        val obrig = runBlocking { banco.backupDao().obrigacoes() }
        assertEquals(1, obrig.size)
        assertEquals(hoje.minusDays(1).toString(), obrig.single().vencimento) // padrão: ontem
        assertEquals(12_000L, obrig.single().valorPrevistoCentavos) // padrão: o valor da conta fixa
    }
}
