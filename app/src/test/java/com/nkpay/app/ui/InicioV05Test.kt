package com.nkpay.app.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import com.nkpay.app.dominio.TipoBeneficio
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.ui.theme.NkPayTema
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

/** Início da v0.5 (regra 6E.3 e D1): Saldo disponível e o Resumo pelo fluxo. Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2000dp-xxhdpi")
class InicioV05Test {
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
            val pix = ok(container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, hoje)))
            val va = ok(container.contas.criar(Conta("", "Vale A", Natureza.BENEFICIO, TipoBeneficio.VA, 30_000, hoje)))
            val cartao = ok(container.contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, hoje, diaFechamento = 5, diaVencimento = 12)))
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.ENTRADA, hoje, 300_000, pix, descricao = "Salário"))
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, hoje, 20_000, pix, descricao = "Mercado"))
            container.movimentacoes.criar(Movimentacao("", TipoMovimentacao.DESPESA, hoje, 2_500, va, descricao = "Almoço"))
            container.compras.registrar(NovaCompra(cartao, "Fone", null, hoje, 60_000, 3, LocalDate.of(2026, 12, 12)))
            container.pagamentosCredito.pagarFatura(cartao, YearMonth.of(2026, 12), pix, hoje, 20_000, null)
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun saldoDisponivelEResumoPeloFluxo() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        esperar("Saldo disponível")
        esperar("Saldo das movimentações")
        regra.waitForIdle()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/v05-inicio.png")
        // Guardado = 0: só o Saldo disponível aparece.
        assertEquals(0, regra.onAllNodes(hasText("Saldo total")).fetchSemanticsNodes().size)
        assertEquals(0, regra.onAllNodes(hasText("Guardado")).fetchSemanticsNodes().size)
        // Saldo disponível = 1.000 + 3.000 − 200 − 200 (fatura) = R$ 3.600,00.
        esperar("R$ 3.600,00")
        // Saídas = mercado (200) + fatura (200). A compra no cartão (600) e o almoço no VA (25) não saem.
        regra.onNodeWithText("Saídas").assertExists()
        regra.onNodeWithText("R$ 400,00").assertExists()
        regra.onNodeWithText("2 movimentações").assertExists()
        // Saldo das movimentações = 3.000 − 400.
        regra.onNodeWithText("+ R$ 2.600,00").assertExists()
    }
}
