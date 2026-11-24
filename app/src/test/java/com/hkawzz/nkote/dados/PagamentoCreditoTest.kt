package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.CategoriasDoSistema
import com.hkawzz.nkote.dominio.ClassificacaoDiferenca
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
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

/** Pagamento em modo crédito (regras 6B.4 e 6B.5, ADR 0015). Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class PagamentoCreditoTest {
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso() // hoje = 01/12/2026
    private lateinit var contas: ContaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var obrigs: ObrigacaoRepositorio
    private lateinit var compras: CompraServico
    private lateinit var servico: PagamentoCreditoServico
    private lateinit var categorias: CategoriaRepositorio
    private lateinit var pix: String
    private lateinit var cartao: String
    private lateinit var loja: String
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
        categorias = CategoriaRepositorio(banco, auditor, relogio)
        categorias.garantirCategoriasIniciais()
        pix = (contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 300_000, LocalDate.of(2026, 10, 1))) as Resultado.Sucesso).valor
        cartao = (contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, LocalDate.of(2026, 10, 1), limite = 500_000, diaFechamento = 5, diaVencimento = 12)) as Resultado.Sucesso).valor
        loja = (contas.criar(Conta("", "Loja", Natureza.CREDIARIO, null, 0, LocalDate.of(2026, 10, 1), diaVencimento = 20)) as Resultado.Sucesso).valor
    }

    @After fun fechar() = banco.close()

    private suspend fun comprar(conta: String, descricao: String, valor: Long, data: LocalDate = LocalDate.of(2026, 12, 3), venc: LocalDate = LocalDate.of(2026, 12, 12), n: Int = 1) =
        (compras.registrar(NovaCompra(conta, descricao, null, data, valor, n, venc)) as Resultado.Sucesso).valor

    /** Fatura de dezembro com A R$ 100, B R$ 200 e C R$ 180 (exemplo da regra 6B.4). */
    private suspend fun faturaDeExemplo(): Map<String, String> {
        val ids = mapOf("A" to comprar(cartao, "A", 10_000), "B" to comprar(cartao, "B", 20_000), "C" to comprar(cartao, "C", 18_000))
        return ids.mapValues { (_, compraId) -> visao().parcelasDaCompra(compraId).single().id }
    }

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

    private suspend fun saldo(id: String) = Saldos.saldosPorConta(contas.observar().first(), movs.observar().first())[id]
    private suspend fun despesasDe(mes: YearMonth) = Saldos.totaisDoMes(movs.observar().first(), mes)
    private suspend fun fatura() = visao().fatura(cartao, dez, relogio.hoje())!!
    private suspend fun fecha(conta: String) = assertTrue("invariante", visao().invarianteFecha(conta, relogio.hoje()))
    private fun <T> Resultado<T>.ok(): T = (this as Resultado.Sucesso).valor

    @Test
    fun `pagar total quita a fatura com uma transferencia e nao cria despesa`() = runTest {
        faturaDeExemplo()
        val t = servico.pagarFatura(cartao, dez, pix, dia, 48_000, null).ok()
        assertEquals(StatusObrigacao.QUITADA, fatura().status)
        assertEquals(0L, visao().devido(cartao))
        assertEquals(252_000L, saldo(pix))
        assertEquals(48_000L, despesasDe(dez).despesas) // só as compras
        assertEquals(TipoMovimentacao.TRANSFERENCIA, movs.buscar(t)!!.tipo)
        assertEquals(3, banco.pagamentoDao().daMovimentacao(t).size)
        fecha(cartao)
    }

    @Test
    fun `parcial geral de 300 e depois 180`() = runTest {
        faturaDeExemplo()
        servico.pagarFatura(cartao, dez, pix, dia, 30_000, null).ok()
        assertEquals(18_000L, fatura().restante)
        assertTrue(fatura().parcelas.none { it.status == StatusObrigacao.QUITADA })
        fecha(cartao)
        servico.pagarFatura(cartao, dez, pix, dia, 18_000, null).ok()
        assertEquals(0L, fatura().restante)
        assertTrue(fatura().parcelas.all { it.status == StatusObrigacao.QUITADA })
        assertEquals(0L, visao().devido(cartao))
        fecha(cartao)
    }

    @Test
    fun `pagar compras especificas e o limite pelo restante da fatura`() = runTest {
        val p = faturaDeExemplo()
        servico.pagarCompras(cartao, dez, listOf(p.getValue("B")), pix, dia).ok()
        assertEquals(28_000L, fatura().restante)
        assertEquals(StatusObrigacao.QUITADA, fatura().parcelas.first { it.obrigacao.id == p.getValue("B") }.status)
        fecha(cartao)
        // Com R$ 250 abatidos em geral, pagar A + C (R$ 280) passa do restante (R$ 30) e é recusado.
        servico.pagarFatura(cartao, dez, pix, dia, 25_000, null).ok()
        val antes = banco.backupDao().movimentacoes().size
        assertTrue(servico.pagarCompras(cartao, dez, listOf(p.getValue("A"), p.getValue("C")), pix, dia) is Resultado.Falha)
        assertEquals(antes, banco.backupDao().movimentacoes().size)
    }

    @Test
    fun `pagar a mais exige classificar e vira encargo no cartao`() = runTest {
        faturaDeExemplo()
        val antes = banco.backupDao().movimentacoes().size
        assertTrue(servico.pagarFatura(cartao, dez, pix, dia, 49_200, null) is Resultado.Falha)
        assertEquals(antes, banco.backupDao().movimentacoes().size)

        servico.pagarFatura(cartao, dez, pix, dia, 49_200, ClassificacaoDiferenca.JUROS).ok()
        val encargos = categorias.observar().first().first { it.codigoSistema == CategoriasDoSistema.ENCARGOS_FINANCEIROS }.id
        val enc = movs.observar().first().single { it.categoriaId == encargos }
        assertEquals(1_200L, enc.valor)
        assertEquals(cartao, enc.contaId)
        assertEquals(0L, visao().devido(cartao))
        assertEquals(StatusObrigacao.QUITADA, fatura().status)
        assertEquals(48_000L + 1_200L, despesasDe(dez).despesas)
        assertEquals(250_800L, saldo(pix))
        fecha(cartao)
    }

    @Test
    fun `um pagamento cobre a fatura atrasada e abate a seguinte`() = runTest {
        comprar(cartao, "Antiga", 10_000, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 12))
        faturaDeExemplo()
        val nov = YearMonth.of(2026, 11)
        assertEquals(StatusObrigacao.ATRASADA, visao().fatura(cartao, nov, relogio.hoje())!!.status)
        servico.pagarFatura(cartao, dez, pix, dia, 25_000, null).ok()
        assertEquals(StatusObrigacao.QUITADA, visao().fatura(cartao, nov, relogio.hoje())!!.status)
        assertEquals(33_000L, fatura().restante)
        assertEquals(nov, visao().fatura(cartao, nov, relogio.hoje())!!.mes) // continua no mês original
        fecha(cartao)
    }

    @Test
    fun `desfazer qualquer pagamento volta exatamente ao estado anterior`() = runTest {
        faturaDeExemplo()
        val fotoInicial = visao().devido(cartao) to saldo(pix)
        val t1 = servico.pagarFatura(cartao, dez, pix, dia, 30_000, null).ok()
        val t2 = servico.pagarFatura(cartao, dez, pix, dia, 18_000 + 700, ClassificacaoDiferenca.MULTA).ok()
        fecha(cartao)
        // Desfaz fora de ordem.
        servico.desfazer(t1).ok()
        fecha(cartao)
        servico.desfazer(t2).ok()
        assertEquals(fotoInicial, visao().devido(cartao) to saldo(pix))
        assertEquals(48_000L, despesasDe(dez).despesas)
        assertTrue(banco.pagamentoFaturaDao().observarValidos().first().isEmpty())
        assertTrue(banco.ajusteObrigacaoDao().observarValidos().first().isEmpty())
        fecha(cartao)
    }

    @Test
    fun `crediario parcela com desconto vira ajuste no crediario sem receita`() = runTest {
        val compra = comprar(loja, "Sofá", 30_000, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 20), n = 2)
        val p1 = visao().parcelasDaCompra(compra).first()
        assertTrue(servico.pagarParcela(p1.id, pix, dia, 14_000, null) is Resultado.Falha)
        servico.pagarParcela(p1.id, pix, dia, 14_000, ClassificacaoDiferenca.DESCONTO).ok()
        val s = visao().situacaoDaParcela(p1.id, relogio.hoje())!!
        assertEquals(StatusObrigacao.QUITADA, s.status)
        assertEquals(15_000L, visao().devido(loja))
        assertEquals(0L, despesasDe(dez).entradas)
        assertEquals(286_000L, saldo(pix))
        fecha(loja)
    }

    @Test
    fun `crediario parcial e encargo e o cartao nao paga por parcela`() = runTest {
        val compra = comprar(loja, "Sofá", 30_000, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 20), n = 2)
        val (p1, p2) = visao().parcelasDaCompra(compra)
        servico.pagarParcela(p1.id, pix, dia, 5_000, ClassificacaoDiferenca.PARCIAL).ok()
        assertEquals(10_000L, visao().situacaoDaParcela(p1.id, relogio.hoje())!!.restante)
        servico.pagarParcela(p2.id, pix, dia, 15_500, ClassificacaoDiferenca.MULTA).ok()
        assertEquals(StatusObrigacao.QUITADA, visao().situacaoDaParcela(p2.id, relogio.hoje())!!.status)
        assertEquals(10_000L, visao().devido(loja))
        fecha(loja)

        val p = faturaDeExemplo()
        assertTrue(servico.pagarParcela(p.getValue("A"), pix, dia, 10_000, null) is Resultado.Falha)
        // Conta de crédito não pode ser a origem do pagamento.
        assertTrue(servico.pagarFatura(cartao, dez, loja, dia, 10_000, null) is Resultado.Falha)
    }
}
