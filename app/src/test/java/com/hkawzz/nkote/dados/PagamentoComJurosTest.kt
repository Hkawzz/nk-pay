package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Analises
import com.hkawzz.nkote.dominio.ClassificacaoDiferenca
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.NaturezaAjuste
import com.hkawzz.nkote.dominio.Periodo
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.StatusObrigacao
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.dominio.VisaoCredito
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/**
 * v0.6 (regra 6B.8, D-A1 c): pagamento de parcelas de compra com juros. Exemplo da regra:
 * R$ 300,00 em 12 × R$ 45,50 (parte R$ 25,00, juros R$ 20,50 por parcela). Valores fictícios.
 */
@RunWith(AndroidJUnit4::class)
class PagamentoComJurosTest {
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso() // hoje = 01/12/2026
    private lateinit var contas: ContaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var obrigs: ObrigacaoRepositorio
    private lateinit var compras: CompraServico
    private lateinit var servico: PagamentoCreditoServico
    private lateinit var pix: String
    private lateinit var cartao: String
    private lateinit var loja: String
    private lateinit var encargos: String
    private val dez = YearMonth.of(2026, 12)
    private val dia = LocalDate.of(2026, 12, 10)

    @Before
    fun abrir() = runTest {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        movs = MovimentacaoRepositorio(banco, auditor, relogio)
        obrigs = ObrigacaoRepositorio(banco, auditor, relogio)
        compras = CompraServico(banco, auditor, relogio)
        servico = PagamentoCreditoServico(banco, auditor, relogio)
        val categorias = CategoriaRepositorio(banco, auditor, relogio)
        categorias.garantirCategoriasIniciais()
        encargos = categorias.observar().first().first { it.codigoSistema == com.hkawzz.nkote.dominio.CategoriasDoSistema.ENCARGOS_FINANCEIROS }.id
        pix = (contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 300_000, LocalDate.of(2026, 7, 1))) as Resultado.Sucesso).valor
        cartao = (contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, LocalDate.of(2026, 7, 1), limite = 500_000, diaFechamento = 5, diaVencimento = 12)) as Resultado.Sucesso).valor
        loja = (contas.criar(Conta("", "Loja", Natureza.CREDIARIO, null, 0, LocalDate.of(2026, 7, 1), diaVencimento = 20)) as Resultado.Sucesso).valor
    }

    @After fun fechar() = banco.close()

    private fun <T> Resultado<T>.ok(): T = (this as? Resultado.Sucesso)?.valor ?: error((this as Resultado.Falha).mensagem)

    private suspend fun compra(conta: String, venc: LocalDate) =
        compras.registrar(NovaCompra(conta, "Celular", null, LocalDate.of(2026, 12, 3), 30_000, 12, venc, valorParcela = 4_550)).ok()

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

    private suspend fun despesasDeJuros() = movs.observar().first().filter { it.tipo == TipoMovimentacao.DESPESA && it.categoriaId == encargos }.sumOf { it.valor }
    private suspend fun fecha(conta: String) = assertTrue("invariante", visao().invarianteFecha(conta, relogio.hoje()))
    private suspend fun saldoPix() = Saldos.saldosPorConta(contas.observar().first(), movs.observar().first())[pix]

    @Test
    fun `pagar a fatura com a 1a parcela transfere 45_50 e lanca 20_50 de juros`() = runTest {
        compra(cartao, LocalDate.of(2026, 12, 12))
        val t = servico.pagarFatura(cartao, dez, pix, dia, 4_550, null).ok()
        val v = visao()
        assertEquals(300_000L - 4_550, saldoPix())
        assertEquals(2_050L, despesasDeJuros())
        assertEquals(27_500L, v.devido(cartao))
        assertEquals(22_550L, v.jurosAPagar(cartao, relogio.hoje()))
        assertEquals(StatusObrigacao.QUITADA, v.fatura(cartao, dez, relogio.hoje())!!.status)
        // Despesas do mês: a compra (original) + os juros pagos, na data do pagamento.
        assertEquals(30_000L + 2_050, Saldos.totaisDoMes(movs.observar().first(), dez).despesas)
        fecha(cartao)
        // Desfazer remove a despesa de juros junto.
        servico.desfazer(t).ok()
        assertEquals(0L, despesasDeJuros())
        assertEquals(30_000L, visao().devido(cartao))
        assertEquals(24_600L, visao().jurosAPagar(cartao, relogio.hoje()))
        assertTrue(banco.ajusteObrigacaoDao().observarValidos().first().isEmpty())
        fecha(cartao)
    }

    @Test
    fun `abatimento geral divide entre compra e juros na proporcao e o que quita fecha os centavos`() = runTest {
        compra(cartao, LocalDate.of(2026, 12, 12))
        val t1 = servico.pagarFatura(cartao, dez, pix, dia, 3_000, null).ok()
        val juros1 = 3_000L * 2_050 / 4_550
        assertEquals(juros1, despesasDeJuros())
        assertEquals(30_000L - (3_000 - juros1), visao().devido(cartao))
        fecha(cartao)
        servico.pagarFatura(cartao, dez, pix, dia, 1_550, null).ok()
        assertEquals(2_050L, despesasDeJuros())
        assertEquals(27_500L, visao().devido(cartao))
        fecha(cartao)
        // Desfazer o abatimento geral remove os juros dele (ligados pela transferência).
        servico.desfazer(t1).ok()
        assertEquals(2_050L - juros1, despesasDeJuros())
        assertTrue(banco.ajusteObrigacaoDao().observarValidos().first().none { it.observacao == "geral:$t1" })
        fecha(cartao)
    }

    @Test
    fun `um pagamento que cobre duas faturas lanca os juros de cada parcela`() = runTest {
        compra(cartao, LocalDate.of(2026, 12, 12))
        servico.pagarFatura(cartao, dez.plusMonths(1), pix, dia, 2 * 4_550, null).ok()
        assertEquals(2 * 2_050L, despesasDeJuros())
        assertEquals(30_000L - 2 * 2_500, visao().devido(cartao))
        fecha(cartao)
    }

    @Test
    fun `pagar compras especificas tambem lanca os juros`() = runTest {
        val id = compra(cartao, LocalDate.of(2026, 12, 12))
        val p1 = visao().parcelasDaCompra(id).first()
        servico.pagarCompras(cartao, dez, listOf(p1.id), pix, dia).ok()
        assertEquals(2_050L, despesasDeJuros())
        assertEquals(27_500L, visao().devido(cartao))
        fecha(cartao)
    }

    @Test
    fun `crediario com desconto consome primeiro os juros e parcial e proporcional`() = runTest {
        val id = compra(loja, LocalDate.of(2027, 1, 20))
        val (p1, p2) = visao().parcelasDaCompra(id)
        // Paga R$ 40,00 com desconto de R$ 5,50: o desconto sai dos juros (20,50 → 15,00 de despesa).
        servico.pagarParcela(p1.id, pix, dia, 4_000, ClassificacaoDiferenca.DESCONTO).ok()
        assertEquals(2_050L - 550, despesasDeJuros())
        assertEquals(27_500L, visao().devido(loja))
        fecha(loja)
        val ajustes = banco.ajusteObrigacaoDao().observarValidos().first().map { it.paraDominio() }
        val pagamentos = banco.pagamentoDao().observarValidos().first().map { it.paraDominio() }
        assertEquals(550L, Analises.descontosObtidos(ajustes, pagamentos, movs.observar().first(), Periodo(dez, dez)))
        // Nenhum ajuste na conta da loja: o desconto ficou todo nos juros.
        assertTrue(movs.observar().first().none { it.tipo == TipoMovimentacao.AJUSTE })
        // Parcial na 2ª: R$ 20,00 → juros proporcionais; depois o resto fecha os juros.
        servico.pagarParcela(p2.id, pix, dia, 2_000, ClassificacaoDiferenca.PARCIAL).ok()
        val j = 2_000L * 2_050 / 4_550
        assertEquals(2_050L - 550 + j, despesasDeJuros())
        fecha(loja)
        servico.pagarParcela(p2.id, pix, dia, 2_550, null).ok()
        assertEquals(2_050L - 550 + 2_050, despesasDeJuros())
        assertEquals(30_000L - 2 * 2_500, visao().devido(loja))
        fecha(loja)
    }

    @Test
    fun `desconto maior que os juros reduz o devido so pelo que passa deles`() = runTest {
        val id = compra(loja, LocalDate.of(2027, 1, 20))
        val p1 = visao().parcelasDaCompra(id).first()
        servico.pagarParcela(p1.id, pix, dia, 2_000, ClassificacaoDiferenca.DESCONTO).ok()
        assertEquals(0L, despesasDeJuros())
        assertEquals(27_500L, visao().devido(loja))
        val ajuste = movs.observar().first().single { it.tipo == TipoMovimentacao.AJUSTE }
        assertEquals(2_550L - 2_050, ajuste.valor)
        fecha(loja)
        assertTrue(banco.ajusteObrigacaoDao().observarValidos().first().any { it.natureza == NaturezaAjuste.DESCONTO.name && it.valorCentavos == 2_550L })
    }
}
