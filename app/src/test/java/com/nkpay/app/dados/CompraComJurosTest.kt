package com.nkpay.app.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.NaturezaAjuste
import com.nkpay.app.dominio.Saldos
import com.nkpay.app.dominio.VisaoCredito
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
        NovaCompra(cartao, "Celular", categoria, data, 27_300, 12, LocalDate.of(2026, 12, 12), anteriorAoApp = anterior, parcelasJaPagas = pagas, pagoEmParteDaSeguinte = parcial, valorParcela = parcela)

    @Test
    fun `compra de 273 em 12 de 43_58 tem despesa do original e juros a pagar fora do devido`() = runTest {
        val id = (compras.registrar(nova(4_358)) as Resultado.Sucesso).valor
        val v = visao()
        val c = v.compra(id)!!
        assertEquals(52_296L, c.valorTotal)
        assertEquals(27_300L, c.valorOriginal)
        assertEquals(24_996L, c.juros)
        assertTrue(v.parcelasDaCompra(id).all { it.valorPrevisto == 4_358L })
        // A despesa é o valor original, no mês da compra.
        assertEquals(27_300L, Saldos.totaisDoMes(movs.observar().first(), YearMonth.of(2026, 12)).despesas)
        assertEquals(27_300L, v.devido(cartao))
        assertEquals(24_996L, v.jurosAPagar(cartao, relogio.hoje()))
        assertEquals(24_996L, v.jurosAPagarDaCompra(id, relogio.hoje()))
        assertEquals(472_700L, v.limiteDisponivel(cartao))
        assertTrue(v.invarianteFecha(cartao, relogio.hoje()))
        // Parcela: parte da compra R$ 22,75 e juros R$ 20,83 (a última parte absorve os centavos).
        val p1 = v.situacaoDaParcela(v.parcelasDaCompra(id).first().id, relogio.hoje())!!
        assertEquals(2_275L, p1.parteDaCompra)
        assertEquals(2_083L, p1.jurosPrevistos)
        assertEquals(27_300L - 2_275L * 11, v.situacaoDaParcela(v.parcelasDaCompra(id).last().id, relogio.hoje())!!.parteDaCompra)
    }

    @Test
    fun `parcela igual ao original dividido e compra sem juros como na v0_5`() = runTest {
        val id = (compras.registrar(nova(2_275)) as Resultado.Sucesso).valor
        val v = visao()
        assertEquals(27_300L, v.compra(id)!!.valorTotal)
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
        val id = (compras.registrar(nova(4_358, anterior = true, pagas = 4, parcial = 2_179, data = LocalDate.of(2026, 8, 10))) as Resultado.Sucesso).valor
        val v = visao()
        // 4 parcelas inteiras (4 × 22,75) + metade da 5ª (2.179 de 4.358: parte 1.138, juros 1.041).
        val jurosDaParcial = 2_179L * 2_083 / 4_358
        val parteDaParcial = 2_179L - jurosDaParcial
        assertEquals(27_300L - 4 * 2_275 - parteDaParcial, v.devido(cartao))
        assertEquals(7 * 2_083L + (2_083L - jurosDaParcial), v.jurosAPagar(cartao, relogio.hoje()))
        assertTrue(v.invarianteFecha(cartao, relogio.hoje()))
        // Despesa só do original, em agosto; nenhuma despesa de juros.
        val todas = movs.observar().first()
        assertEquals(27_300L, Saldos.totaisDoMes(todas, YearMonth.of(2026, 8)).despesas)
        assertEquals(1, todas.count { it.tipo == com.nkpay.app.dominio.TipoMovimentacao.DESPESA })
        val ajustes = banco.ajusteObrigacaoDao().observarValidos().first()
        assertEquals(1, ajustes.count { it.natureza == NaturezaAjuste.JUROS_DO_PARCELAMENTO.name && it.movimentacaoId == null })
        assertEquals(5, ajustes.count { it.natureza == NaturezaAjuste.QUITADA_ANTES_DO_APP.name })
        // Excluir a compra apaga tudo, inclusive os juros antes do app.
        assertTrue(compras.excluir(id) is Resultado.Sucesso)
        assertEquals(0L, visao().devido(cartao))
        assertTrue(banco.ajusteObrigacaoDao().observarValidos().first().isEmpty())
    }
}
