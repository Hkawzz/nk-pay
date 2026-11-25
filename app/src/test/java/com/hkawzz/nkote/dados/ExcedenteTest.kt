package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dominio.Analises
import com.hkawzz.nkote.dominio.CategoriasDoSistema
import com.hkawzz.nkote.dominio.ClassificacaoDiferenca
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Periodo
import com.hkawzz.nkote.dominio.Saldos
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/**
 * v0.9 (item 9, regra 8.1): parcela paga com valor MAIOR que o previsto. O excedente vira
 * "Encargos financeiros" na data do pagamento, com acréscimo na parcela; nada é automático.
 * Três fluxos: pagar agora, compra anterior com parcelas pagas a mais, e corrigir depois.
 * Hoje simulado: 01/12/2026. Valores fictícios.
 */
@RunWith(AndroidJUnit4::class)
class ExcedenteTest {
    private lateinit var banco: NkPayBanco
    private lateinit var c: AppContainer
    private lateinit var pix: String
    private lateinit var cartao: String
    private val juros = ClassificacaoDiferenca.quandoPagaMais.first { it.natureza != null }

    @Before
    fun abrir() = runBlocking {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        c = AppContainer(banco, RelogioFalso())
        c.categorias.garantirCategoriasIniciais()
        pix = ok(c.contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 300_000, LocalDate.of(2026, 7, 1))))
        cartao = ok(c.contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, LocalDate.of(2026, 7, 1), diaFechamento = 5, diaVencimento = 12)))
    }

    @After fun fechar() = banco.close()

    private fun <T> ok(r: Resultado<T>): T = when (r) { is Resultado.Sucesso -> r.valor; is Resultado.Falha -> error(r.mensagem) }

    private fun movs() = runBlocking { banco.backupDao().movimentacoes() }.filter { it.excluidoEm == null }.map { it.paraDominio() }
    private fun contas() = runBlocking { banco.backupDao().contas() }.filter { it.excluidoEm == null }.map { it.paraDominio() }
    private fun devido() = -(Saldos.saldosPorConta(contas(), movs())[cartao] ?: 0)

    /** Encargos financeiros do mês, como nas Análises (despesas por categoria). */
    private fun encargos(mes: YearMonth): Long {
        val cats = runBlocking { banco.backupDao().categorias() }.filter { it.excluidoEm == null }.map { it.paraDominio() }
        val id = runBlocking { banco.categoriaDao().buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) }!!.id
        return Analises.despesasPorCategoria(movs(), cats, Periodo(mes, mes)).firstOrNull { it.categoria?.id == id }?.total ?: 0
    }

    private fun parcelas(compra: String) = runBlocking { banco.obrigacaoDao().daCompra(compra) }

    @Test
    fun `fluxo 1 - pagar agora a fatura com valor maior vira encargo (ja funcionava)`() = runBlocking {
        ok(c.compras.registrar(NovaCompra(cartao, "Fone", null, LocalDate.of(2026, 10, 20), 10_000, 1, LocalDate.of(2026, 11, 12))))
        assertTrue(c.pagamentosCredito.pagarFatura(cartao, YearMonth.of(2026, 11), pix, LocalDate.of(2026, 12, 1), 11_000, null) is Resultado.Falha) // pergunta a natureza
        ok(c.pagamentosCredito.pagarFatura(cartao, YearMonth.of(2026, 11), pix, LocalDate.of(2026, 12, 1), 11_000, juros))
        assertEquals(1_000L, encargos(YearMonth.of(2026, 12)))
        assertEquals(0L, devido())
    }

    @Test
    fun `fluxo 2 - compra anterior com parcela paga a mais registra o encargo na data do pagamento`() = runBlocking {
        val n = NovaCompra(
            cartao, "Geladeira", null, LocalDate.of(2026, 7, 10), 30_000, 3, LocalDate.of(2026, 8, 12),
            anteriorAoApp = true, parcelasJaPagas = 2,
            pagasComValorMaior = listOf(PagaAntes(2, 11_500, LocalDate.of(2026, 9, 20), null)),
        )
        // Sem dizer o que é a diferença, recusa; valor menor que a parcela também.
        assertTrue(c.compras.registrar(n) is Resultado.Falha)
        assertTrue(c.compras.registrar(n.copy(pagasComValorMaior = listOf(PagaAntes(2, 9_000, LocalDate.of(2026, 9, 20), juros)))) is Resultado.Falha)
        assertTrue(c.compras.registrar(n.copy(pagasComValorMaior = listOf(PagaAntes(3, 11_000, LocalDate.of(2026, 9, 20), juros)))) is Resultado.Falha) // a 3ª não está paga
        val compra = ok(c.compras.registrar(n.copy(pagasComValorMaior = listOf(PagaAntes(2, 11_500, LocalDate.of(2026, 9, 20), juros)))))
        // O devido é só a 3ª parcela; o excedente não muda o que se deve.
        assertEquals(10_000L, devido())
        // Encargo de R$ 15,00 em setembro (a data informada, antes do app), nada nos outros meses.
        assertEquals(1_500L, encargos(YearMonth.of(2026, 9)))
        assertEquals(0L, encargos(YearMonth.of(2026, 7)))
        // A parcela 2 continua paga, com o acréscimo.
        val p2 = parcelas(compra)[1]
        val ajustes = runBlocking { banco.ajusteObrigacaoDao().validosDa(p2.id) }
        assertEquals(11_500L, ajustes.filter { it.natureza == "QUITADA_ANTES_DO_APP" }.sumOf { it.valorCentavos })
        assertEquals(1_500L, ajustes.filter { it.natureza == juros.natureza!!.name }.sumOf { it.valorCentavos })
        // O Pix não muda (foi pago antes do app).
        assertEquals(300_000L, Saldos.saldosPorConta(contas(), movs())[pix])
        // Excluir a compra leva tudo junto.
        ok(c.compras.excluir(compra))
        assertEquals(0L, encargos(YearMonth.of(2026, 9)))
        assertEquals(0L, devido())
    }

    @Test
    fun `fluxo 3 - corrigir o valor pago de uma parcela ja marcada como paga`() = runBlocking {
        val compra = ok(c.compras.registrar(NovaCompra(cartao, "TV", null, LocalDate.of(2026, 7, 10), 30_000, 3, LocalDate.of(2026, 8, 12), anteriorAoApp = true, parcelasJaPagas = 2)))
        val p1 = parcelas(compra)[0].id
        val p3 = parcelas(compra)[2].id
        assertTrue(c.compras.pagaAntesDoApp(p1))
        assertTrue(!c.compras.pagaAntesDoApp(p3)) // ainda não paga
        assertEquals(10_000L, c.compras.valorPagoAntesDoApp(p1))
        ok(c.compras.corrigirPagoAntesDoApp(p1, 12_000, LocalDate.of(2026, 8, 15), juros))
        assertEquals(2_000L, encargos(YearMonth.of(2026, 8)))
        assertEquals(12_000L, c.compras.valorPagoAntesDoApp(p1))
        // Corrigir de novo troca o excedente (não soma).
        ok(c.compras.corrigirPagoAntesDoApp(p1, 11_000, LocalDate.of(2026, 8, 20), juros))
        assertEquals(1_000L, encargos(YearMonth.of(2026, 8)))
        // Voltar ao valor da parcela tira o encargo; menor que a parcela é recusado (a regra de desconto não muda).
        ok(c.compras.corrigirPagoAntesDoApp(p1, 10_000, LocalDate.of(2026, 8, 20), null))
        assertEquals(0L, encargos(YearMonth.of(2026, 8)))
        assertTrue(c.compras.corrigirPagoAntesDoApp(p1, 9_000, LocalDate.of(2026, 8, 20), null) is Resultado.Falha)
        assertTrue(c.compras.corrigirPagoAntesDoApp(p3, 12_000, LocalDate.of(2026, 8, 20), juros) is Resultado.Falha)
        // Data no futuro é recusada.
        assertTrue(c.compras.corrigirPagoAntesDoApp(p1, 12_000, LocalDate.of(2026, 12, 2), juros) is Resultado.Falha)
        // O devido nunca mudou: só a 3ª parcela.
        assertEquals(10_000L, devido())
        assertTrue(runBlocking { banco.auditoriaDao().listarTodas() }.any { it.resumo.contains("pago antes do app") })
    }

    @Test
    fun `crediario - pagar a parcela agora com valor maior pergunta a natureza (ja funcionava)`() = runBlocking {
        val loja = ok(c.contas.criar(Conta("", "Loja", Natureza.CREDIARIO, null, 0, LocalDate.of(2026, 7, 1), diaVencimento = 20)))
        val compra = ok(c.compras.registrar(NovaCompra(loja, "Sofá", null, LocalDate.of(2026, 10, 1), 20_000, 2, LocalDate.of(2026, 11, 20))))
        val p1 = parcelas(compra)[0].id
        assertTrue(c.pagamentosCredito.pagarParcela(p1, pix, LocalDate.of(2026, 12, 1), 10_800, null) is Resultado.Falha)
        ok(c.pagamentosCredito.pagarParcela(p1, pix, LocalDate.of(2026, 12, 1), 10_800, juros))
        assertEquals(800L, encargos(YearMonth.of(2026, 12)))
    }
}
