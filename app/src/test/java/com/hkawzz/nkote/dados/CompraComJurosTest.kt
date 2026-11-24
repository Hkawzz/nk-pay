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

/** v0.6 (regra 6B.8, D-A1 c): cadastro de compra parcelada com juros. Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class CompraComJurosTest {
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

    @Test
    fun `compra de 300 em 12 de 45_50 tem despesa do original e juros a pagar fora do devido`() = runTest {
        val id = (compras.registrar(nova(4_550)) as Resultado.Sucesso).valor
        val v = visao()
        val c = v.compra(id)!!
        assertEquals(54_600L, c.valorTotal)
        assertEquals(30_000L, c.valorOriginal)
        assertEquals(24_600L, c.juros)
        assertTrue(v.parcelasDaCompra(id).all { it.valorPrevisto == 4_550L })
        // A despesa é o valor original, no mês da compra.
        assertEquals(30_000L, Saldos.totaisDoMes(movs.observar().first(), YearMonth.of(2026, 12)).despesas)
        assertEquals(30_000L, v.devido(cartao))
        assertEquals(24_600L, v.jurosAPagar(cartao, relogio.hoje()))
        assertEquals(24_600L, v.jurosAPagarDaCompra(id, relogio.hoje()))
        assertEquals(470_000L, v.limiteDisponivel(cartao))
        assertTrue(v.invarianteFecha(cartao, relogio.hoje()))
        // Parcela: parte da compra R$ 25,00 e juros R$ 20,50 (a última parte absorve os centavos).
        val p1 = v.situacaoDaParcela(v.parcelasDaCompra(id).first().id, relogio.hoje())!!
        assertEquals(2_500L, p1.parteDaCompra)
        assertEquals(2_050L, p1.jurosPrevistos)
        assertEquals(30_000L - 2_500L * 11, v.situacaoDaParcela(v.parcelasDaCompra(id).last().id, relogio.hoje())!!.parteDaCompra)
    }

    @Test
    fun `parcela igual ao original dividido e compra sem juros como na v0_5`() = runTest {
        val id = (compras.registrar(nova(2_500)) as Resultado.Sucesso).valor
        val v = visao()
        assertEquals(30_000L, v.compra(id)!!.valorTotal)
        assertEquals(0L, v.compra(id)!!.juros)
        assertNull(banco.compraDao().buscar(id)!!.valorOriginalCentavos)
        assertEquals(0L, v.jurosAPagar(cartao, relogio.hoje()))
        assertTrue(v.invarianteFecha(cartao, relogio.hoje()))
    }

    @Test
    fun `total das parcelas menor que o original e recusado`() = runTest {
        val r = compras.registrar(nova(2_000))
        assertTrue(r is Resultado.Falha)
        assertTrue((r as Resultado.Falha).mensagem.contains("menor que o valor original"))
        assertTrue(visao().compras.isEmpty())
    }

    @Test
    fun `compra anterior com juros soma so a parte da compra e os juros pagos nao viram despesa`() = runTest {
        val id = (compras.registrar(nova(4_550, anterior = true, pagas = 4, parcial = 2_275, data = LocalDate.of(2026, 8, 10))) as Resultado.Sucesso).valor
        val v = visao()
        // 4 parcelas inteiras (4 × 25,00) + metade da 5ª (2.275 de 4.550: parte 1.250, juros 1.025).
        val jurosDaParcial = 2_275L * 2_050 / 4_550
        val parteDaParcial = 2_275L - jurosDaParcial
        assertEquals(30_000L - 4 * 2_500 - parteDaParcial, v.devido(cartao))
        assertEquals(7 * 2_050L + (2_050L - jurosDaParcial), v.jurosAPagar(cartao, relogio.hoje()))
        assertTrue(v.invarianteFecha(cartao, relogio.hoje()))
        // Despesa só do original, em agosto; nenhuma despesa de juros.
        val todas = movs.observar().first()
        assertEquals(30_000L, Saldos.totaisDoMes(todas, YearMonth.of(2026, 8)).despesas)
        assertEquals(1, todas.count { it.tipo == com.hkawzz.nkote.dominio.TipoMovimentacao.DESPESA })
        val ajustes = banco.ajusteObrigacaoDao().observarValidos().first()
        assertEquals(1, ajustes.count { it.natureza == NaturezaAjuste.JUROS_DO_PARCELAMENTO.name && it.movimentacaoId == null })
        assertEquals(5, ajustes.count { it.natureza == NaturezaAjuste.QUITADA_ANTES_DO_APP.name })
        // Excluir a compra apaga tudo, inclusive os juros antes do app.
        assertTrue(compras.excluir(id) is Resultado.Sucesso)
        assertEquals(0L, visao().devido(cartao))
        assertTrue(banco.ajusteObrigacaoDao().observarValidos().first().isEmpty())
    }
}
