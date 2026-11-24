package com.hkawzz.nkote.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import com.hkawzz.nkote.dados.NkPayBanco
import com.hkawzz.nkote.dados.RelogioFalso
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.TipoBeneficio
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

/**
 * Roteiro de aceite da v0.1.0 executado pela INTERFACE (toques e digitação),
 * com valores fictícios. Também gera capturas de tela em app/build/outputs/roborazzi/
 * para revisar o visual sem celular.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AceiteTest {

    @get:Rule val regra = createComposeRule()

    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer
    private val dia = LocalDate.of(2026, 12, 1)

    @Before
    fun abrir() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        banco = Room.inMemoryDatabaseBuilder(context, NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
    }

    @After
    fun fechar() = banco.close()

    private fun criarConta(nome: String, saldo: Long, natureza: Natureza = Natureza.PROPRIO, beneficio: TipoBeneficio? = null): String =
        runBlocking {
            (container.contas.criar(Conta("", nome, natureza, beneficio, saldo, dia)) as Resultado.Sucesso).valor
        }

    private fun abrirApp() {
        regra.setContent {
            NkPayTema {
                CompositionLocalProvider(LocalContainer provides container) { NkPayApp() }
            }
        }
    }

    private fun esperarTexto(texto: String) {
        regra.waitUntil(10_000) {
            regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun foto(nome: String) {
        regra.waitForIdle()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png")
    }

    /** Abre um campo de escolha e toca na opção (dentro do diálogo). */
    private fun escolher(campo: String, opcao: String) {
        regra.onNodeWithTag("campo:$campo").performScrollTo().performClick()
        regra.onNode(hasText(opcao) and hasAnyAncestor(isDialog())).performClick()
    }

    private fun digitar(rotulo: String, texto: String) {
        regra.onNode(hasSetTextAction() and hasText(rotulo)).performScrollTo().performTextInput(texto)
    }

    private fun saldos(): Map<String, Long> = runBlocking {
        Saldos.saldosPorConta(container.contas.observar().first(), container.movimentacoes.observar().first())
    }

    @Test
    fun roteiroDeAceite() {
        // Primeira abertura: sem contas, mostra o passo a passo.
        abrirApp()
        esperarTexto("Cadastre suas contas e saldos iniciais")
        foto("01-inicio-vazio")

        // Cadastro: Pix R$ 1.200, Dinheiro R$ 300 e VA R$ 250 (fictícios).
        val pix = criarConta("Pix", 120_000)
        val din = criarConta("Dinheiro", 30_000)
        val va = criarConta("VA", 25_000, Natureza.BENEFICIO, TipoBeneficio.VA)

        // Início: Disponível R$ 1.500 (só o próprio) e Benefícios R$ 250 à parte.
        esperarTexto("R$ 1.500,00")
        regra.onNodeWithText("Benefícios · fora do Disponível").assertExists()
        // Com contas e sem backup, a Início avisa.
        regra.onNodeWithText("Você ainda não fez nenhum backup").assertExists()
        foto("02-inicio-com-contas")
        // A Início não tem mais o botão "+" (adendo v0.3, A1).
        regra.onNodeWithContentDescription("Nova movimentação").assertDoesNotExist()

        // Transferência Pix → Dinheiro de R$ 40 pela interface, a partir da aba Movimentações.
        regra.onNodeWithText("Movimentações").performClick()
        esperarTexto("Transferências e ajustes não entram nesses totais.")
        regra.onNodeWithContentDescription("Nova movimentação").performClick()
        esperarTexto("Lançar")
        regra.onNodeWithText("Transf.").performClick()
        escolher("De (origem)", "Pix")
        escolher("Para (destino)", "Dinheiro")
        // Estilo app de banco: "4000" vira R$ 40,00.
        digitar("Valor", "4000")
        foto("03-form-transferencia")
        regra.onNodeWithText("Lançar").performScrollTo().performClick()

        regra.waitUntil(10_000) { saldos()[pix] == 116_000L }
        assertEquals(34_000L, saldos()[din])
        // O Disponível não muda.
        regra.onNodeWithText("Início").performClick()
        esperarTexto("Últimas movimentações")
        esperarTexto("R$ 1.500,00")
        regra.onNodeWithText("Movimentações").performClick()
        esperarTexto("Transferências e ajustes não entram nesses totais.")

        // Despesa de R$ 25 paga com o VA.
        regra.onNodeWithContentDescription("Nova movimentação").performClick()
        esperarTexto("Lançar")
        escolher("Conta", "VA")
        digitar("Valor", "2500")
        digitar("Descrição", "Mercado")
        regra.onNodeWithText("Lançar").performScrollTo().performClick()

        regra.waitUntil(10_000) { saldos()[va] == 22_500L }
        // Disponível igual; a despesa entra no total do mês.
        regra.onNodeWithText("Início").performClick()
        esperarTexto("R$ 1.500,00")
        // v0.5 (D1): o Resumo da Início é o fluxo do dinheiro próprio; a despesa do VA aparece nas últimas movimentações.
        esperarTexto("-R$ 25,00")
        foto("04-inicio-depois")

        // Totais do mês: transferências não entram.
        val movs = runBlocking { container.movimentacoes.observar().first() }
        val totais = Saldos.totaisDoMes(movs, java.time.YearMonth.from(dia))
        assertEquals(0L, totais.entradas)
        assertEquals(2_500L, totais.despesas)

        // Cada criação aparece no Registro de alterações (3 contas + 2 movimentações).
        val registros = runBlocking { container.auditoria.observar().first() }
        assertEquals(5, registros.size)

        // Visitas para as capturas de tela.
        regra.onNodeWithText("Movimentações").performClick()
        esperarTexto("Transferências e ajustes não entram nesses totais.")
        foto("05-movimentacoes")

        regra.onNodeWithText("Mais").performClick()
        regra.onNodeWithText("Contas e carteiras").performClick()
        esperarTexto("Dinheiro próprio")
        foto("06-contas")

        regra.onNodeWithText("Pix").performClick()
        esperarTexto("Saldo atual")
        foto("07-conta-pix")
        regra.onNodeWithContentDescription("Voltar").performClick()
        regra.onNodeWithContentDescription("Voltar").performClick()

        regra.onNodeWithText("Registro de alterações").performClick()
        esperarTexto("Conta “Pix” criada (saldo inicial R$ 1.200,00)")
        foto("08-registro")

        regra.onNodeWithText("Mercado", substring = true).performClick()
        esperarTexto("Depois")
        foto("09-registro-detalhe")

        assertTrue(regra.onAllNodes(hasText("Antes")).fetchSemanticsNodes().isNotEmpty())

        // Tela de backup (v0.2).
        regra.onNodeWithContentDescription("Voltar").performClick()
        regra.onNodeWithContentDescription("Voltar").performClick()
        regra.onNodeWithText("Backup e restauração").performClick()
        esperarTexto("Fazer backup agora")
        regra.onNodeWithText("Nenhum backup feito ainda").assertExists()
        foto("10-backup")

        // A aba Mais sempre reabre no menu principal (adendo v0.3, A2)...
        regra.onNodeWithText("Início").performClick()
        esperarTexto("Últimas movimentações")
        regra.onNodeWithText("Mais").performClick()
        esperarTexto("Contas e carteiras")
        regra.onNodeWithText("Fazer backup agora").assertDoesNotExist()

        // ...mas as outras abas preservam o estado (o mês escolhido em Movimentações continua).
        regra.onNodeWithText("Movimentações").performClick()
        esperarTexto("Dezembro de 2026")
        regra.onNodeWithContentDescription("Mês anterior").performClick()
        esperarTexto("Novembro de 2026")
        regra.onNodeWithText("Início").performClick()
        esperarTexto("Últimas movimentações")
        regra.onNodeWithText("Movimentações").performClick()
        esperarTexto("Novembro de 2026")
    }
}
