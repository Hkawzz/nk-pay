package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.NaturezaAjuste
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.VisaoCredito
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/** v0.6 (A2, regra 6B.9, D-A2 b): parcelas antecipadas antes do app. Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class AntecipadasTest {
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso() // hoje = 01/12/2026
    private lateinit var contas: ContaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var obrigs: ObrigacaoRepositorio
    private lateinit var compras: CompraServico
    private lateinit var cartao: String
    private lateinit var categoria: String

    @Before
    fun abrir() = runTest {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        movs = MovimentacaoRepositorio(banco, auditor, relogio)
        obrigs = ObrigacaoRepositorio(banco, auditor, relogio)
        compras = CompraServico(banco, auditor, relogio)
        val categorias = CategoriaRepositorio(banco, auditor, relogio)
        categorias.garantirCategoriasIniciais()
        categoria = categorias.observar().first().first { it.nome == "Alimentação" }.id
        contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 300_000, LocalDate.of(2026, 7, 1)))
        cartao = (contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, LocalDate.of(2026, 7, 1), limite = 500_000, diaFechamento = 5, diaVencimento = 12)) as Resultado.Sucesso).valor
    }

    @After fun fechar() = banco.close()

    private suspend fun visao(): VisaoCredito {
        val o = obrigs.observar().first()
        return VisaoCredito(
            contas.observar().first(), movs.observar().first(), o.filter { it.ehParcela },
            banco.pagamentoDao().observarValidos().first().map { it.paraDominio() },
            banco.ajusteObrigacaoDao().observarValidos().first().map { it.paraDominio() },
            banco.pagamentoFaturaDao().observarValidos().first().map { it.paraDominio() },
            banco.compraDao().observarValidas().first().map { it.paraDominio() },
        )
    }

    private fun nova(parcela: Long?, anterior: Boolean = false, pagas: Int = 0, parcial: Long = 0, data: LocalDate = LocalDate.of(2026, 12, 3)) =
        NovaCompra(cartao, "Celular", categoria, data, 30_000, 12, LocalDate.of(2026, 12, 12), anteriorAoApp = anterior, parcelasJaPagas = pagas, pagoEmParteDaSeguinte = parcial, valorParcela = parcela)

    private suspend fun descontos(mes: YearMonth) = com.hkawzz.nkote.dominio.Analises.descontosObtidos(
        banco.ajusteObrigacaoDao().observarValidos().first().map { it.paraDominio() },
        banco.pagamentoDao().observarValidos().first().map { it.paraDominio() },
        movs.observar().first(), com.hkawzz.nkote.dominio.Periodo(mes, mes),
    )

    @Test
    fun `antecipada com desconto quita a parcela e o desconto entra no mes da compra`() = runTest {
        val ago = YearMonth.of(2026, 8)
        val id = (compras.registrar(nova(4_550, anterior = true, pagas = 4, data = LocalDate.of(2026, 8, 10)).copy(antecipadas = listOf(Antecipada(12, 4_000)))) as Resultado.Sucesso).valor
        val v = visao()
        assertEquals(30_000L - 5 * 2_500, v.devido(cartao))
        assertEquals(7 * 2_050L, v.jurosAPagar(cartao, relogio.hoje()))
        assertTrue(v.invarianteFecha(cartao, relogio.hoje()))
        val p12 = v.situacaoDaParcela(v.parcelasDaCompra(id).last().id, relogio.hoje())!!
        assertEquals(com.hkawzz.nkote.dominio.StatusObrigacao.QUITADA, p12.status)
        // Desconto R$ 5,50 em Descontos obtidos, datado na compra (D-A2 b); juros não viram despesa (D-A3 b).
        assertEquals(550L, descontos(ago))
        assertEquals(0L, descontos(YearMonth.of(2026, 12)))
        assertEquals(30_000L, Saldos.totaisDoMes(movs.observar().first(), ago).despesas)
        assertEquals(1, movs.observar().first().count { it.tipo == com.hkawzz.nkote.dominio.TipoMovimentacao.DESPESA })
        assertTrue(banco.ajusteObrigacaoDao().observarValidos().first().any { it.natureza == NaturezaAjuste.DESCONTO_ANTES_DO_APP.name && it.valorCentavos == 550L })
    }

    @Test
    fun `antecipada em compra sem juros`() = runTest {
        compras.registrar(NovaCompra(cartao, "Geladeira", null, LocalDate.of(2026, 8, 10), 120_000, 12, LocalDate.of(2026, 8, 12),
            anteriorAoApp = true, parcelasJaPagas = 4, antecipadas = listOf(Antecipada(12, 9_000), Antecipada(11, 10_000))))
        val v = visao()
        assertEquals(120_000L - 6 * 10_000, v.devido(cartao))
        assertTrue(v.invarianteFecha(cartao, relogio.hoje()))
        assertEquals(1_000L, descontos(YearMonth.of(2026, 8)))
    }

    @Test
    fun `antecipadas invalidas sao recusadas`() = runTest {
        val base = nova(4_550, anterior = true, pagas = 4, parcial = 1_000, data = LocalDate.of(2026, 8, 10))
        for (a in listOf(listOf(Antecipada(3, 4_550)), listOf(Antecipada(5, 4_550)), listOf(Antecipada(13, 100)),
            listOf(Antecipada(12, 4_551)), listOf(Antecipada(12, 100), Antecipada(12, 200)))) {
            assertTrue("$a", compras.registrar(base.copy(antecipadas = a)) is Resultado.Falha)
        }
        assertTrue(compras.registrar(nova(4_550).copy(antecipadas = listOf(Antecipada(12, 100)))) is Resultado.Falha)
        assertTrue(visao().compras.isEmpty())
    }
}