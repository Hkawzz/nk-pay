package com.nkpay.app.ui

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
import com.nkpay.app.AppContainer
import com.nkpay.app.dados.NkPayBanco
import com.nkpay.app.dados.NovaCompra
import com.nkpay.app.dados.RelogioFalso
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.StatusObrigacao
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

/** A fatura pela interface (v0.4, F6): A pagar → fatura → pagar total → desfazer → pagar uma compra. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FaturaUiTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer
    private lateinit var cartao: String
    private val dez = YearMonth.of(2026, 12)
    private val hoje = LocalDate.of(2026, 12, 1)

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2026, 11, 1)))
            cartao = (container.contas.criar(Conta("", "Cartão Teste", Natureza.CARTAO, null, 0, LocalDate.of(2026, 11, 1), limite = 200_000, diaFechamento = 5, diaVencimento = 12)) as Resultado.Sucesso).valor
            for ((d, v) in listOf("Compra A" to 10_000L, "Compra B" to 20_000L, "Compra C" to 18_000L)) {
                container.compras.registrar(NovaCompra(cartao, d, null, LocalDate.of(2026, 12, 3), v, 1, LocalDate.of(2026, 12, 12)))
            }
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }
    private fun foto(nome: String) { regra.waitForIdle(); regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png") }
    private fun fatura() = container.visaoCredito.value!!.fatura(cartao, dez, hoje)!!

    @Test
    fun pagarTotalDesfazerEPagarUmaCompra() {
        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onNodeWithText("A pagar").performClick()
        esperar("Fatura Cartão Teste")
        foto("f6-apagar-com-fatura")
        regra.onNodeWithText("Fatura Cartão Teste").performClick()
        esperar("Confirmar pagamento")
        esperar("• Fatura de dezembro de 2026 quitada (R$ 480,00)")
        foto("f6-fatura")
        regra.onNodeWithText("Confirmar pagamento").performScrollTo().performClick()
        regra.waitUntil(10_000) { fatura().status == StatusObrigacao.QUITADA }
        esperar("Desfazer")
        foto("f6-fatura-paga")

        regra.onNodeWithText("Desfazer").performScrollTo().performClick()
        regra.waitUntil(10_000) { fatura().restante == 48_000L }

        regra.onNodeWithText("Escolher compras").performScrollTo().performClick()
        regra.onNodeWithText("Compra B").performScrollTo().performClick()
        esperar("Total das compras escolhidas: R$ 200,00")
        regra.onNodeWithText("Pagar compras escolhidas").performScrollTo().performClick()
        regra.waitUntil(10_000) { fatura().restante == 28_000L && container.visaoCredito.value!!.devido(cartao) == 28_000L }
        assertEquals(28_000L, container.visaoCredito.value!!.devido(cartao))
    }
}
