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
import androidx.compose.runtime.Composable
import com.hkawzz.nkote.dados.NovaCompra
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.Objetivo
import com.hkawzz.nkote.dominio.Obrigacao
import com.hkawzz.nkote.dominio.OrigemObrigacao
import com.hkawzz.nkote.dominio.TipoEmprestimo
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** v0.8 (R2): nenhuma lista de pagamento ou de despesa mostra caixinhas. Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CaixinhaNaoPagaUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    private lateinit var obrigacao: String
    private lateinit var cartao: String
    private lateinit var parcelaLoja: String

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            fun <T> ok(r: Resultado<T>) = (r as Resultado.Sucesso).valor
            val pix = ok(container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2026, 11, 1))))
            val viagem = ok(container.objetivos.criar(Objetivo("", "Cofrinho Viagem")))
            ok(container.objetivos.guardar(viagem, pix, 30_000, LocalDate.of(2026, 12, 1)))
            obrigacao = ok(container.obrigacoes.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Luz", 5_000, LocalDate.of(2026, 12, 10), YearMonth.of(2026, 12))))
            cartao = ok(container.contas.criar(Conta("", "Cartão Teste", Natureza.CARTAO, null, 0, LocalDate.of(2026, 11, 1), diaFechamento = 5, diaVencimento = 12)))
            ok(container.compras.registrar(NovaCompra(cartao, "Fone", null, LocalDate.of(2026, 12, 3), 5_000, 1, LocalDate.of(2026, 12, 12))))
            val loja = ok(container.contas.criar(Conta("", "Loja", Natureza.CREDIARIO, null, 0, LocalDate.of(2026, 11, 1), diaVencimento = 20)))
            val compra = ok(container.compras.registrar(NovaCompra(loja, "Sofá", null, LocalDate.of(2026, 12, 3), 5_000, 1, LocalDate.of(2027, 1, 20))))
            parcelaLoja = banco.obrigacaoDao().daCompra(compra).single().id
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }

    /** Abre a lista do campo [rotulo] e confere: o Pix aparece, a caixinha nunca. */
    private fun confereLista(rotulo: String, tela: @Composable () -> Unit) {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { tela() } } }
        esperar(rotulo)
        regra.onNodeWithText(rotulo).performScrollTo().performClick()
        regra.waitUntil(10_000) { regra.onAllNodes(hasText("Pix", substring = true)).fetchSemanticsNodes().size >= 1 }
        assertEquals(0, regra.onAllNodes(hasText("Cofrinho Viagem", substring = true)).fetchSemanticsNodes().size)
    }

    @Test fun despesaNaoMostraCaixinha() = confereLista("Conta") { com.hkawzz.nkote.ui.movimentacoes.MovimentacaoFormTela(null, null) {} }
    @Test fun pagarContaNaoMostraCaixinha() = confereLista("Pagar com") { com.hkawzz.nkote.ui.apagar.PagarTela(obrigacao) {} }
    @Test fun faturaNaoMostraCaixinha() = confereLista("Pagar com") { com.hkawzz.nkote.ui.credito.FaturaTela(cartao, YearMonth.of(2026, 12), {}, {}) }
    @Test fun crediarioNaoMostraCaixinha() = confereLista("Pagar com") { com.hkawzz.nkote.ui.credito.ParcelaTela(parcelaLoja) {} }
    @Test fun emprestimoNaoMostraCaixinha() = confereLista("Saiu de") { com.hkawzz.nkote.ui.emprestimos.EmprestimoFormTela(TipoEmprestimo.CONCEDIDO) {} }
}