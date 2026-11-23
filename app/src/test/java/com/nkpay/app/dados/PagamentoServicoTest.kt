package com.nkpay.app.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nkpay.app.dominio.CategoriasDoSistema
import com.nkpay.app.dominio.ClassificacaoDiferenca
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.ContaFixa
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.Obrigacoes
import com.nkpay.app.dominio.Saldos
import com.nkpay.app.dominio.StatusObrigacao
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.dominio.VisaoAPagar
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

/** Botão Pagar com banco real em memória (ADR 0013). Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class PagamentoServicoTest {

    private lateinit var banco: NkPayBanco
    private lateinit var contas: ContaRepositorio
    private lateinit var categorias: CategoriaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var fixas: ContaFixaRepositorio
    private lateinit var obrigs: ObrigacaoRepositorio
    private lateinit var servico: PagamentoServico
    private val relogio = RelogioFalso() // hoje = 01/12/2026
    private val dezembro = YearMonth.of(2026, 12)
    private lateinit var pix: String
    private lateinit var luz: String
    private lateinit var moradia: String

    @Before
    fun abrir() = runTest {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java)
            .allowMainThreadQueries().build()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        categorias = CategoriaRepositorio(banco, auditor, relogio)
        movs = MovimentacaoRepositorio(banco, auditor, relogio)
        fixas = ContaFixaRepositorio(banco, auditor, relogio)
        obrigs = ObrigacaoRepositorio(banco, auditor, relogio)
        servico = PagamentoServico(banco, auditor, relogio)

        categorias.garantirCategoriasIniciais()
        moradia = categorias.observar().first().first { it.nome == "Moradia" }.id
        pix = (contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 120_000, LocalDate.of(2026, 12, 1))) as Resultado.Sucesso).valor
        val f = (fixas.criar(ContaFixa("", "Luz", 10_000, 10, moradia)) as Resultado.Sucesso).valor
        obrigs.lancarFixas(dezembro, listOf(f))
        luz = obrigs.observar().first().single().id
    }

    @After fun fechar() = banco.close()

    private suspend fun visao() = VisaoAPagar(
        fixas.observar().first(),
        obrigs.observar().first(),
        banco.pagamentoDao().observarValidos().first().map { it.paraDominio() },
        banco.ajusteObrigacaoDao().observarValidos().first().map { it.paraDominio() },
    )

    private suspend fun situacao() = visao().let { v -> v.situacao(v.obrigacao(luz)!!, relogio.hoje()) }
    private suspend fun despesas() = movs.observar().first().filter { it.tipo == TipoMovimentacao.DESPESA }
    private suspend fun saldoPix() = Saldos.saldosPorConta(contas.observar().first(), movs.observar().first())[pix]

    @Test
    fun `pagamento exato cria uma despesa na categoria da conta e quita`() = runTest {
        assertTrue(servico.pagar(luz, pix, LocalDate.of(2026, 12, 8), 10_000, null) is Resultado.Sucesso)
        val d = despesas().single()
        assertEquals(10_000L, d.valor)
        assertEquals(moradia, d.categoriaId)
        assertEquals(StatusObrigacao.QUITADA, situacao().status)
        assertEquals(110_000L, saldoPix())
    }

    @Test
    fun `dois parciais geram duas despesas e a referencia continua dezembro`() = runTest {
        servico.pagar(luz, pix, LocalDate.of(2026, 12, 8), 6_000, ClassificacaoDiferenca.PARCIAL)
        assertEquals(4_000L, situacao().restante)
        assertEquals(StatusObrigacao.PARCIAL, situacao().status)

        // O resto é pago em janeiro: a despesa é de janeiro, a obrigação continua de dezembro.
        servico.pagar(luz, pix, LocalDate.of(2027, 1, 5), 4_000, null)
        assertEquals(StatusObrigacao.QUITADA, situacao().status)
        assertEquals(dezembro, situacao().obrigacao.mesReferencia)
        val d = despesas()
        assertEquals(listOf(6_000L, 4_000L).sorted(), d.map { it.valor }.sorted())
        val totDez = Saldos.totaisDoMes(movs.observar().first(), dezembro).despesas
        val totJan = Saldos.totaisDoMes(movs.observar().first(), YearMonth.of(2027, 1)).despesas
        assertEquals(6_000L, totDez)
        assertEquals(4_000L, totJan)
    }

    @Test
    fun `multa vira despesa separada em encargos financeiros`() = runTest {
        servico.pagar(luz, pix, LocalDate.of(2026, 12, 15), 10_500, ClassificacaoDiferenca.MULTA)
        val encargos = categorias.observar().first().first { it.codigoSistema == CategoriasDoSistema.ENCARGOS_FINANCEIROS }.id
        val d = despesas()
        assertEquals(2, d.size)
        assertEquals(10_000L, d.single { it.categoriaId == moradia }.valor)
        assertEquals(500L, d.single { it.categoriaId == encargos }.valor)
        assertEquals(StatusObrigacao.QUITADA, situacao().status)
        assertEquals(109_500L, saldoPix())
    }

    @Test
    fun `desconto registra a despesa paga e o abatimento sem criar entrada`() = runTest {
        servico.pagar(luz, pix, LocalDate.of(2026, 12, 5), 9_500, ClassificacaoDiferenca.DESCONTO)
        assertEquals(9_500L, despesas().single().valor)
        assertEquals(StatusObrigacao.QUITADA, situacao().status)
        assertEquals(500L, situacao().abatimentos)
        val t = Saldos.totaisDoMes(movs.observar().first(), dezembro)
        assertEquals(0L, t.entradas) // nenhuma receita fictícia
        assertEquals(9_500L, t.despesas)
    }

    @Test
    fun `diferenca sem classificacao e recusada e nada e gravado`() = runTest {
        val antes = banco.auditoriaDao().listarTodas().size
        assertTrue(servico.pagar(luz, pix, LocalDate.of(2026, 12, 5), 6_000, null) is Resultado.Falha)
        assertTrue(despesas().isEmpty())
        assertEquals(antes, banco.auditoriaDao().listarTodas().size)
    }

    @Test
    fun `conta ja paga nao aceita novo pagamento`() = runTest {
        servico.pagar(luz, pix, LocalDate.of(2026, 12, 8), 10_000, null)
        assertTrue(servico.pagar(luz, pix, LocalDate.of(2026, 12, 9), 100, null) is Resultado.Falha)
    }

    @Test
    fun `desfazer um pagamento com multa apaga tudo e a conta volta ao que faltava`() = runTest {
        val p = (servico.pagar(luz, pix, LocalDate.of(2026, 12, 15), 10_500, ClassificacaoDiferenca.MULTA) as Resultado.Sucesso).valor
        assertTrue(servico.excluir(p) is Resultado.Sucesso)
        assertTrue(despesas().isEmpty())
        assertEquals(10_000L, situacao().restante)
        assertEquals(0L, situacao().acrescimos)
        assertEquals(120_000L, saldoPix())
    }

    @Test
    fun `desfazer um pagamento com desconto remove o abatimento`() = runTest {
        val p = (servico.pagar(luz, pix, LocalDate.of(2026, 12, 5), 9_500, ClassificacaoDiferenca.DESCONTO) as Resultado.Sucesso).valor
        servico.excluir(p)
        assertEquals(10_000L, situacao().restante)
        assertEquals(0L, situacao().abatimentos)
    }

    @Test
    fun `despesa de pagamento nao pode ser editada nem excluida pela tela de movimentacoes`() = runTest {
        servico.pagar(luz, pix, LocalDate.of(2026, 12, 8), 10_000, null)
        val d = despesas().single()
        assertTrue(movs.excluir(d.id) is Resultado.Falha)
        assertTrue(movs.editar(d.copy(valor = 1)) is Resultado.Falha)
        assertTrue(movs.ehPagamentoDeConta(d.id))
    }

    @Test
    fun `um pagamento usa um grupo so de auditoria e o backup leva tudo`() = runTest {
        servico.pagar(luz, pix, LocalDate.of(2026, 12, 15), 10_500, ClassificacaoDiferenca.MULTA)
        val ultimos = banco.auditoriaDao().listarTodas().filter { it.entidade in setOf("movimentacao", "pagamento", "obrigacao_ajuste") }
        assertEquals(5, ultimos.size) // 2 despesas + 2 pagamentos + 1 ajuste
        assertEquals(1, ultimos.map { it.grupoId }.distinct().size)
        assertEquals(2, banco.backupDao().pagamentos().size)
        assertEquals(1, banco.backupDao().ajustesDeObrigacao().size)
        assertEquals(10_500L, Obrigacoes.situacao(visao().obrigacao(luz)!!, visao().pagamentos, visao().ajustes, relogio.hoje()).pago)
    }
}
