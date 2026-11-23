package com.nkpay.app.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
import com.nkpay.app.dominio.ContaFixa
import com.nkpay.app.dominio.Obrigacao
import com.nkpay.app.dominio.OrigemObrigacao
import com.nkpay.app.ui.theme.NkPayTema
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
import java.time.YearMonth

/** Roteiro da aba A pagar pela interface (valores fictícios), com capturas de tela. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class APagarTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso()) // hoje = 01/12/2026
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun foto(nome: String) { regra.waitForIdle(); regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png") }

    @Test
    fun lancarContasFixasPelaInterface() {
        runBlocking {
            container.contasFixas.criar(ContaFixa("", "Luz", 10_000, 10))
            container.contasFixas.criar(ContaFixa("", "Internet", 9_990, 15))
            container.contasFixas.criar(ContaFixa("", "Streaming", 2_990, null))
            // Uma conta avulsa de novembro, vencida e não paga: deve aparecer como pendente anterior.
            container.obrigacoes.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Presente", 8_000, LocalDate.of(2026, 11, 20), YearMonth.of(2026, 11)))
        }
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }

        regra.onNodeWithText("A pagar").performClick()
        esperar("Lançar contas fixas de dezembro de 2026")
        esperar("Pendentes de meses anteriores")
        foto("11-apagar-antes")

        // Seleção manual: marca só Luz e Internet.
        regra.onNodeWithText("Lançar contas fixas de dezembro de 2026").performClick()
        esperar("Marque as que entram neste mês. As que já foram lançadas não aparecem.")
        regra.onNode(hasText("Luz") and hasAnyAncestor(isDialog())).performClick()
        regra.onNode(hasText("Internet") and hasAnyAncestor(isDialog())).performClick()
        foto("12-apagar-dialogo")
        regra.onNodeWithText("Lançar 2").performClick()

        esperar("2 contas fixas lançadas.")
        esperar("Luz")
        foto("13-apagar-depois")
        val lancadas = runBlocking { container.obrigacoes.observar().first() }.filter { it.origem == OrigemObrigacao.FIXA }
        assertEquals(setOf("Luz", "Internet"), lancadas.map { it.descricao }.toSet())

        // Repetir: só sobra o Streaming para lançar; Luz e Internet não aparecem de novo.
        regra.onNodeWithText("Lançar contas fixas de dezembro de 2026").performClick()
        esperar("Streaming")
        assertEquals(0, regra.onAllNodes(hasText("Luz") and hasAnyAncestor(isDialog())).fetchSemanticsNodes().size)
        regra.onNodeWithText("Cancelar").performClick()

        // Detalhe de uma conta a pagar.
        regra.onNodeWithText("Luz").performScrollTo().performClick()
        esperar("Previsto")
        foto("14-conta-a-pagar")
        regra.onNodeWithContentDescription("Voltar").performClick()

        // Mais → Contas fixas.
        regra.onNodeWithText("Mais").performClick()
        regra.onNodeWithText("Contas fixas").performClick()
        esperar("Ativas")
        foto("15-contas-fixas")
    }

    @Test
    fun pagarComMultaPelaInterface() {
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            container.contas.criar(com.nkpay.app.dominio.Conta("", "Pix", com.nkpay.app.dominio.Natureza.PROPRIO, null, 120_000, LocalDate.of(2026, 12, 1)))
            val f = (container.contasFixas.criar(ContaFixa("", "Luz", 10_000, 10)) as com.nkpay.app.dados.Resultado.Sucesso).valor
            container.obrigacoes.lancarFixas(YearMonth.of(2026, 12), listOf(f))
        }
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }

        regra.onNodeWithText("A pagar").performClick()
        esperar("Luz")
        regra.onNodeWithText("Luz").performClick()
        esperar("Pagar")
        regra.onNodeWithText("Pagar").performScrollTo().performClick()
        esperar("Confirmar pagamento")

        // Paga R$ 105,00: o app pergunta o que são os R$ 5,00 a mais.
        regra.onNode(androidx.compose.ui.test.hasSetTextAction() and hasText("Valor pago")).performTextClearance()
        regra.onNode(androidx.compose.ui.test.hasSetTextAction() and hasText("Valor pago")).performTextInput("10500")
        esperar("Você está pagando R$ 5,00 a mais. O que é essa diferença?")
        regra.onNodeWithText("Multa").performClick()
        esperar("A conta fica paga.")
        foto("16-pagar-com-multa")
        regra.onNodeWithText("Confirmar pagamento").performScrollTo().performClick()

        // De volta à conta: paga, com o pagamento no histórico.
        esperar("Pagamentos")
        foto("17-conta-paga")
        val pagas = runBlocking { container.visaoAPagar.value!! }
        val s = pagas.situacao(pagas.obrigacoes.single(), LocalDate.of(2026, 12, 1))
        assertEquals(com.nkpay.app.dominio.StatusObrigacao.QUITADA, s.status)
        val despesas = runBlocking { container.movimentacoes.observar().first() }
        assertEquals(listOf(500L, 10_000L), despesas.map { it.valor }.sorted())
    }
}
