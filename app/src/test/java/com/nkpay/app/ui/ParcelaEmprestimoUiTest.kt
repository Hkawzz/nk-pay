package com.nkpay.app.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.nkpay.app.dados.NovoEmprestimo
import com.nkpay.app.dados.RelogioFalso
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Emprestimos
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.TipoEmprestimo
import com.nkpay.app.ui.theme.NkPayTema
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Receber uma parcela de empréstimo concedido com os juros K2, e desfazer, pela interface (v0.5, PR 8). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1800dp-xxhdpi")
class ParcelaEmprestimoUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer
    private lateinit var emprestimo: String

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            val pix = (container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 200_000, LocalDate.of(2026, 11, 1))) as Resultado.Sucesso).valor
            emprestimo = (container.emprestimos.registrar(
                NovoEmprestimo(TipoEmprestimo.CONCEDIDO, null, "Tonho", "Conserto do carro", null, 100_000, LocalDate.of(2026, 11, 10), pix,
                    Emprestimos.parcelasAutomaticas(10, 11_000, LocalDate.of(2026, 11, 25))),
            ) as Resultado.Sucesso).valor
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun foto(nome: String) { regra.waitForIdle(); regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png") }
    private fun s() = container.visaoEmprestimos.value!!.situacao(emprestimo, LocalDate.of(2026, 12, 1))!!

    @Test
    fun receberParcelaAtrasadaComJurosEDesfazer() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onNodeWithText("Mais").performClick()
        regra.onNodeWithText("Empréstimos").performClick()
        esperar("Recebidos (eu devo)")
        regra.onNodeWithText("Concedidos (me devem)").performClick()
        esperar("Conserto do carro")
        regra.onNodeWithText("Conserto do carro").performClick()
        esperar("Parcela 1/10")
        // Vence em 25/11 e hoje é 01/12: atrasada há 6 dias.
        esperar("Atrasada · vencida há 6 dias")
        regra.onNodeWithText("Parcela 1/10").performClick()
        esperar("Confirmar recebimento")
        esperar("• Devolução de R$ 100,00 (transferência, não é receita)")
        esperar("• Juros recebidos de R$ 10,00 (receita)")
        foto("v05-receber-parcela")
        regra.onNodeWithText("Confirmar recebimento").performScrollTo().performClick()
        regra.waitUntil(10_000) { s().jurosRecebidos == 1_000L && s().saldoNaConta == 90_000L }
        esperar("Desfazer")
        regra.onNodeWithText("Desfazer").performScrollTo().performClick()
        regra.waitUntil(10_000) { s().jurosRecebidos == 0L && s().saldoNaConta == 100_000L }

        // Recebeu a menos: pergunta parcial ou desconto.
        regra.onNode(hasSetTextAction() and hasText("Valor recebido")).performTextClearance()
        regra.onNode(hasSetTextAction() and hasText("Valor recebido")).performTextInput("5500")
        esperar("Você está recebendo R$ 55,00 a menos. O que é essa diferença?")
        regra.onNodeWithText("Pagamento parcial").assertExists()
        regra.onNodeWithText("Desconto").assertExists()
    }
}
