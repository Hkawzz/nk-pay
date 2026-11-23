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
import com.nkpay.app.dados.NovaCompra
import com.nkpay.app.dados.RelogioFalso
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Movimentacao
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.TipoMovimentacao
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
import java.time.YearMonth

/** Refinos das Análises da v0.5: despesas registradas × saídas do dinheiro próprio, com "?". */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2200dp-xxhdpi")
class AnalisesV05Test {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            val d = LocalDate.of(2026, 12, 1)
            fun ok(r: Resultado<String>) = (r as Resultado.Sucesso).valor
            val pix = ok(container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, d)))
            val cartao = ok(container.contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, d, diaFechamento = 5, diaVencimento = 12)))
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.ENTRADA, d, 300_000, pix))
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, d, 20_000, pix))
            container.compras.registrar(NovaCompra(cartao, "Fone", null, d, 60_000, 1, LocalDate.of(2026, 12, 12)))
            container.pagamentosCredito.pagarFatura(cartao, YearMonth.of(2026, 12), pix, d, 30_000, null)
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun despesasRegistradasESaidasComAjuda() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onNodeWithText("Mais").performClick()
        regra.onNodeWithText("Análises").performClick()
        esperar("Despesas registradas")
        esperar("Saídas do dinheiro próprio")
        // Despesas registradas = 200 + 600 (compra no cartão); saídas = 200 + 300 (fatura).
        esperar("R$ 800,00")
        esperar("R$ 500,00")
        esperar("Dezembro de 2026 (atual)")
        regra.waitForIdle()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/v05-analises.png")
        regra.onNodeWithContentDescription("Ajuda: Saídas do dinheiro próprio").performClick()
        esperar("Não é o mesmo que Despesas registradas, e os dois números podem ser diferentes de propósito.")
        regra.onNodeWithText("Entendi").performClick()
        for (c in listOf("Resultado", "Despesas registradas", "Cartões e crediários")) {
            regra.onNodeWithContentDescription("Ajuda: $c").assertExists()
        }
    }
}
