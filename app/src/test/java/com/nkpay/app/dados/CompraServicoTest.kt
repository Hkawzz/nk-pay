package com.nkpay.app.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.NaturezaAjuste
import com.nkpay.app.dominio.Obrigacao
import com.nkpay.app.dominio.OrigemObrigacao
import com.nkpay.app.dominio.Saldos
import com.nkpay.app.dominio.StatusObrigacao
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.dominio.VisaoAPagar
import com.nkpay.app.dominio.VisaoCredito
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/** Compras parceladas e compras anteriores ao app (regras 6B.2 e 6B.6). Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class CompraServicoTest {
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso() // hoje = 01/12/2026
    private lateinit var contas: ContaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var obrigs: ObrigacaoRepositorio
    private lateinit var compras: CompraServico
    private lateinit var pix: String
    private lateinit var cartao: String
    private lateinit var alimentacao: String

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
        alimentacao = categorias.observar().first().first { it.nome == "Alimentação" }.id
        pix = (contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 300_000, LocalDate.of(2026, 7, 1))) as Resultado.Sucesso).valor
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

    private suspend fun saldoPix() = Saldos.saldosPorConta(contas.observar().first(), movs.observar().first())[pix]

    @Test
    fun `compra de 100 em 3x cria a despesa total e 3 parcelas com a ultima absorvendo os centavos`() = runTest {
        val id = (compras.registrar(NovaCompra(cartao, "Fone", alimentacao, LocalDate.of(2026, 12, 3), 10_000, 3, LocalDate.of(2026, 12, 12))) as Resultado.Sucesso).valor
        val v = visao()
        val ps = v.parcelasDaCompra(id)
        assertEquals(listOf(3_333L, 3_333L, 3_334L), ps.map { it.valorPrevisto })
        assertEquals(listOf(LocalDate.of(2026, 12, 12), LocalDate.of(2027, 1, 12), LocalDate.of(2027, 2, 12)), ps.map { it.vencimento })
        assertEquals(10_000L, v.devido(cartao))
        assertEquals(490_000L, v.limiteDisponivel(cartao))
        assertTrue(v.invarianteFecha(cartao, relogio.hoje()))
        assertEquals(300_000L, saldoPix())
        assertEquals(10_000L, Saldos.totaisDoMes(movs.observar().first(), YearMonth.of(2026, 12)).despesas)
        // Um grupo só no Registro de alterações.
        val registros = banco.auditoriaDao().listarTodas().filter { it.entidade in setOf("compra", "obrigacao", "movimentacao") }
        assertEquals(1, registros.map { it.grupoId }.distinct().size)
    }

    @Test
    fun `compra anterior de 1200 em 12x com 4 pagas fica com devido de 800 e nao mexe no Pix`() = runTest {
        val disponivelAntes = Saldos.resumo(contas.observar().first(), movs.observar().first()).disponivel
        val id = (compras.registrar(
            NovaCompra(cartao, "Geladeira", alimentacao, LocalDate.of(2026, 8, 10), 120_000, 12, LocalDate.of(2026, 8, 12), anteriorAoApp = true, parcelasJaPagas = 4),
        ) as Resultado.Sucesso).valor
        val v = visao()
        assertEquals(80_000L, v.devido(cartao))
        assertTrue(v.invarianteFecha(cartao, relogio.hoje()))
        val situacoes = v.parcelasDaCompra(id).map { v.situacaoDaParcela(it.id, relogio.hoje())!! }
        assertTrue(situacoes.take(4).all { it.status == StatusObrigacao.QUITADA })
        assertTrue(situacoes.drop(4).none { it.status == StatusObrigacao.QUITADA })
        // A despesa fica no mês original (agosto), pelo total; Pix e Disponível iguais.
        assertEquals(120_000L, Saldos.totaisDoMes(movs.observar().first(), YearMonth.of(2026, 8)).despesas)
        assertEquals(0L, Saldos.totaisDoMes(movs.observar().first(), YearMonth.of(2026, 12)).despesas)
        assertEquals(disponivelAntes, Saldos.resumo(contas.observar().first(), movs.observar().first()).disponivel)
        assertEquals(300_000L, saldoPix())
        assertTrue(v.compra(id)!!.anteriorAoApp)
        // "Quitada antes do app" não é desconto.
        val ajustes = banco.ajusteObrigacaoDao().observarValidos().first()
        assertEquals(4, ajustes.size)
        assertTrue(ajustes.all { it.natureza == NaturezaAjuste.QUITADA_ANTES_DO_APP.name && !NaturezaAjuste.valueOf(it.natureza).ehDesconto })
    }

    @Test
    fun `parcela paga em parte antes do app`() = runTest {
        val id = (compras.registrar(
            NovaCompra(cartao, "Sofá", null, LocalDate.of(2026, 9, 1), 30_000, 3, LocalDate.of(2026, 9, 12), anteriorAoApp = true, parcelasJaPagas = 1, pagoEmParteDaSeguinte = 4_000),
        ) as Resultado.Sucesso).valor
        val v = visao()
        assertEquals(16_000L, v.devido(cartao))
        assertEquals(6_000L, v.situacaoDaParcela(v.parcelasDaCompra(id)[1].id, relogio.hoje())!!.restante)
        assertTrue(v.invarianteFecha(cartao, relogio.hoje()))
        assertTrue(compras.registrar(NovaCompra(cartao, "X", null, LocalDate.of(2026, 9, 1), 30_000, 3, LocalDate.of(2026, 9, 12), anteriorAoApp = true, parcelasJaPagas = 1, pagoEmParteDaSeguinte = 10_000)) is Resultado.Falha)
    }

    @Test
    fun `excluir a compra desfaz tudo e e bloqueado quando ha parcela paga`() = runTest {
        val id = (compras.registrar(NovaCompra(cartao, "Geladeira", alimentacao, LocalDate.of(2026, 8, 10), 120_000, 12, LocalDate.of(2026, 8, 12), anteriorAoApp = true, parcelasJaPagas = 4)) as Resultado.Sucesso).valor
        assertTrue(compras.excluir(id) is Resultado.Sucesso)
        val v = visao()
        assertEquals(0L, v.devido(cartao))
        assertTrue(v.parcelasDaCompra(id).isEmpty())
        assertTrue(movs.observar().first().none { it.contaId == cartao })

        // Com uma parcela paga (ligação feita direto no banco), a exclusão é recusada.
        val id2 = (compras.registrar(NovaCompra(cartao, "Fone", null, LocalDate.of(2026, 12, 3), 10_000, 2, LocalDate.of(2026, 12, 12))) as Resultado.Sucesso).valor
        val p1 = visao().parcelasDaCompra(id2).first()
        banco.movimentacaoDao().inserir(MovimentacaoEntidade("t", "TRANSFERENCIA", "2026-12-10", 5_000, pix, cartao, null, "Fatura", 1, 1, null))
        banco.pagamentoDao().inserir(PagamentoEntidade("pg", p1.id, "t", 5_000, 1, 1, null))
        assertTrue(compras.excluir(id2) is Resultado.Falha)
    }

    @Test
    fun `despesa da compra e ajuste das parcelas antigas sao bloqueados em Movimentacoes`() = runTest {
        compras.registrar(NovaCompra(cartao, "Geladeira", alimentacao, LocalDate.of(2026, 8, 10), 120_000, 12, LocalDate.of(2026, 8, 12), anteriorAoApp = true, parcelasJaPagas = 4))
        val doCartao = movs.observar().first().filter { it.contaId == cartao }
        assertEquals(setOf(TipoMovimentacao.DESPESA, TipoMovimentacao.AJUSTE), doCartao.map { it.tipo }.toSet())
        for (m in doCartao) {
            assertTrue(movs.excluir(m.id) is Resultado.Falha)
            assertTrue(movs.editar(m.copy(descricao = "outra")) is Resultado.Falha)
            assertTrue(movs.ehPagamentoDeConta(m.id))
        }
        assertEquals(MENSAGEM_COMPRA, (movs.excluir(doCartao.first { it.tipo == TipoMovimentacao.DESPESA }.id) as Resultado.Falha).mensagem)
    }

    @Test
    fun `parcelas nao entram na aba A pagar e o Pagar das contas fixas as recusa`() = runTest {
        val id = (compras.registrar(NovaCompra(cartao, "Fone", null, LocalDate.of(2026, 12, 3), 10_000, 1, LocalDate.of(2026, 12, 12))) as Resultado.Sucesso).valor
        val parcela = visao().parcelasDaCompra(id).single()
        val ap = VisaoAPagar(emptyList(), obrigs.observar().first(), emptyList(), emptyList())
        assertTrue(ap.doMes(YearMonth.of(2026, 12), relogio.hoje()).isEmpty())
        assertTrue(ap.pendentesAnteriores(YearMonth.of(2027, 3), relogio.hoje()).isEmpty())
        val r = PagamentoServico(banco, Auditor(banco.auditoriaDao(), relogio), relogio).pagar(parcela.id, pix, relogio.hoje(), 10_000, null)
        assertTrue(r is Resultado.Falha)
    }

    @Test
    fun `aviso de possivel duplicidade encontra conta avulsa e despesa parecidas`() = runTest {
        obrigs.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Parcela geladeira", 10_000, LocalDate.of(2026, 11, 12), YearMonth.of(2026, 11)))
        movs.criar(com.nkpay.app.dominio.Movimentacao("", TipoMovimentacao.DESPESA, LocalDate.of(2026, 11, 20), 9_999, pix, descricao = "Mercado"))
        val avisos = compras.possiveisDuplicadas("Geladeira", 120_000, 12)
        assertEquals(1, avisos.size)
        assertTrue(avisos.single().contains("geladeira", ignoreCase = true))
        assertTrue(compras.possiveisDuplicadas("Bicicleta", 77_700, 3).isEmpty())
        assertFalse(compras.possiveisDuplicadas("Mercado", 1, 1).isEmpty())
    }
}
