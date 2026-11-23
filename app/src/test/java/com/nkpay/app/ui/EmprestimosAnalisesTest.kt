package com.nkpay.app.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.nkpay.app.AppContainer
import com.nkpay.app.dados.NkPayBanco
import com.nkpay.app.dados.NovoEmprestimo
import com.nkpay.app.dados.RelogioFalso
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.Analises
import com.nkpay.app.dominio.ClassificacaoDiferenca
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Emprestimos
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.Periodo
import com.nkpay.app.dominio.TipoEmprestimo
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

/** Empréstimos nas Análises e em A pagar (v0.5, PR 9). Dados fictícios. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class EmprestimosAnalisesTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var banco: NkPayBanco
    private lateinit var container: AppContainer
    private val hoje = LocalDate.of(2026, 12, 1)
    private lateinit var recebido: String
    private lateinit var concedido: String

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).build()
        container = AppContainer(banco, RelogioFalso())
        runBlocking {
            container.categorias.garantirCategoriasIniciais()
            val pix = (container.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 300_000, LocalDate.of(2026, 11, 1))) as Resultado.Sucesso).valor
            fun ok(r: Resultado<String>) = (r as Resultado.Sucesso).valor
            recebido = ok(container.emprestimos.registrar(NovoEmprestimo(TipoEmprestimo.RECEBIDO, null, "Banco X", "Pessoal", null, 100_000, hoje, pix, Emprestimos.parcelasAutomaticas(10, 11_000, LocalDate.of(2026, 12, 10)))))
            concedido = ok(container.emprestimos.registrar(NovoEmprestimo(TipoEmprestimo.CONCEDIDO, null, "Tonho", "Carro", null, 100_000, hoje, pix, Emprestimos.parcelasAutomaticas(10, 11_000, LocalDate.of(2026, 12, 5)))))
            val ve = { container.visaoEmprestimos.value }
            while (ve()?.situacao(concedido, hoje) == null) kotlinx.coroutines.delay(10)
            val p1 = ve()!!.parcelasDe(concedido)[0].id
            val p2 = ve()!!.parcelasDe(concedido)[1].id
            container.pagamentosCredito.receberParcela(p1, pix, hoje, 11_000, null)
            container.pagamentosCredito.receberParcela(p2, pix, hoje, 10_000, ClassificacaoDiferenca.DESCONTO)
        }
    }

    @After fun fechar() = banco.close()

    private fun esperar(texto: String) = regra.waitUntil(10_000) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun resumoDosEmprestimosEDescontosSeparados() {
        // Espera todas as listas do banco (pagamentos, ajustes e movimentações) chegarem.
        regra.waitUntil(10_000) {
            container.visaoEmprestimos.value?.let { ve ->
                val r = Analises.emprestimos(ve, Periodo(YearMonth.of(2026, 12), YearMonth.of(2026, 12)), hoje)
                r.jurosRecebidos == 1_909L && r.totalAReceber == 88_000L && container.visaoAPagar.value?.ajustes?.size == 3
            } == true
        }
        val ve = container.visaoEmprestimos.value!!
        val dez = Periodo(YearMonth.of(2026, 12), YearMonth.of(2026, 12))
        val r = Analises.emprestimos(ve, dez, hoje)
        assertEquals(110_000L, r.totalAPagar)
        assertEquals(88_000L, r.totalAReceber)
        assertEquals(10_000L, r.jurosPagos) // J1, uma vez
        assertEquals(1_000L + 909, r.jurosRecebidos) // 10,00 + 100 × 100/1100 (arredondado)
        val ap = container.visaoAPagar.value!!
        val concedidas = ve.parcelasDe(concedido).map { it.id }.toSet()
        // O desconto dado ao Tonho é concedido, nunca obtido.
        assertEquals(0L, Analises.descontosObtidos(ap.ajustes, ap.pagamentos, ve.movimentacoes, dez, concedidas))
        assertEquals(1_000L, Analises.descontosConcedidos(ap.ajustes, ap.pagamentos, ve.movimentacoes, dez, concedidas))

        regra.setContent { NkPayTema { CompositionLocalProvider(LocalContainer provides container) { NkPayApp() } } }
        regra.onNodeWithText("Mais").performClick()
        regra.onNodeWithText("Análises").performClick()
        esperar("Juros recebidos no período")
        esperar("Descontos concedidos: R$ 10,00")
        regra.waitForIdle()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/v05-analises-emprestimos.png")

        // A pagar: a parcela do recebido aparece; as do concedido, nunca.
        regra.onNodeWithText("A pagar").performClick()
        esperar("Empréstimos a pagar")
        esperar("Pessoal (1/10)")
        assertEquals(0, regra.onAllNodes(hasText("Carro (1/10)")).fetchSemanticsNodes().size)
        regra.onNodeWithText("Pessoal (1/10)").performClick()
        esperar("Confirmar pagamento")
    }
}
