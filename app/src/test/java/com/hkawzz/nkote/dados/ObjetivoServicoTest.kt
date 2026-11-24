package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Analises
import com.hkawzz.nkote.dominio.CategoriasDoSistema
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.ContaFixa
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Objetivo
import com.hkawzz.nkote.dominio.Obrigacao
import com.hkawzz.nkote.dominio.OrigemObrigacao
import com.hkawzz.nkote.dominio.Periodo
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.TipoBeneficio
import com.hkawzz.nkote.dominio.TipoEmprestimo
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.dominio.VisaoFinanceira
import com.hkawzz.nkote.dominio.VisaoObjetivos
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
 * Objetivos como caixinhas no banco. v0.8 (R2, regra 6G.2, ADR 0032): os testes da v0.7
 * (carimbo) foram reescritos para o modelo de caixinhas. Valores fictícios.
 */
@RunWith(AndroidJUnit4::class)
class ObjetivoServicoTest {
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso() // hoje = 01/12/2026
    private lateinit var contas: ContaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var categorias: CategoriaRepositorio
    private lateinit var servico: ObjetivoServico
    private lateinit var auditor: Auditor
    private lateinit var pix: String
    private lateinit var va: String
    private val dia = LocalDate.of(2026, 12, 1)
    private val dez = YearMonth.of(2026, 12)

    @Before
    fun abrir() = runTest {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        movs = MovimentacaoRepositorio(banco, auditor, relogio)
        categorias = CategoriaRepositorio(banco, auditor, relogio)
        categorias.garantirCategoriasIniciais()
        servico = ObjetivoServico(banco, auditor, relogio)
        pix = contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2026, 11, 1))).ok()
        va = contas.criar(Conta("", "VA", Natureza.BENEFICIO, TipoBeneficio.VA, 50_000, LocalDate.of(2026, 11, 1))).ok()
    }

    @After fun fechar() = banco.close()

    private fun <T> Resultado<T>.ok(): T = when (this) { is Resultado.Sucesso -> valor; is Resultado.Falha -> error(mensagem) }
    private fun Resultado<*>.recusada(): String = (this as? Resultado.Falha)?.mensagem ?: error("deveria ter sido recusada")

    private suspend fun visao(): VisaoFinanceira {
        val c = contas.observar().first()
        val m = movs.observar().first()
        return VisaoFinanceira(c, categorias.observar().first(), m,
            VisaoObjetivos(banco.objetivoDao().observarValidos().first().map { it.paraDominio() }, banco.movimentoReservaDao().observarValidos().first().map { it.paraDominio() }, c, m))
    }

    private suspend fun caixinha(objetivo: String) = banco.objetivoDao().buscar(objetivo)!!.contaCaixinhaId!!

    @Test
    fun `tabela da regra 6G2 com guardar rendimento e retirar`() = runTest {
        val viagem = servico.criar(Objetivo("", "Viagem", meta = 50_000)).ok()
        val cx = caixinha(viagem)
        assertEquals(0L, visao().saldo(cx)) // começa zerada
        servico.guardar(viagem, pix, 30_000, dia).ok()
        visao().let { v -> assertEquals(70_000L, v.saldo(pix)); assertEquals(30_000L, v.saldo(cx)); assertEquals(100_000L, v.resumo.dinheiroProprio); assertEquals(70_000L, v.resumo.disponivel) }
        servico.rendimento(viagem, 200, dia).ok()
        visao().let { v -> assertEquals(30_200L, v.saldo(cx)); assertEquals(100_200L, v.resumo.dinheiroProprio); assertEquals(70_000L, v.resumo.disponivel) }
        servico.retirar(viagem, pix, 10_000, dia).ok()
        visao().let { v ->
            assertEquals(80_000L, v.saldo(pix)); assertEquals(20_200L, v.saldo(cx))
            assertEquals(100_200L, v.resumo.dinheiroProprio); assertEquals(20_200L, v.resumo.reservado); assertEquals(80_000L, v.resumo.disponivel)
            assertEquals(20_200L, v.objetivos.guardado(viagem))
        }
        // Só o rendimento é receita; guardar e retirar não entram no fluxo nem nos totais.
        val v = visao()
        assertEquals(200L, Saldos.totaisDoMes(v.movimentacoes, dez).entradas)
        assertEquals(0L, Saldos.totaisDoMes(v.movimentacoes, dez).despesas)
        val f = Analises.fluxo(v.contas, v.movimentacoes, Periodo(dez, dez))
        assertEquals(200L, f.entrou)
        assertEquals(0L, f.saiu)
        assertTrue(categorias.observar().first().any { it.codigoSistema == CategoriasDoSistema.RENDIMENTOS })
    }

    @Test
    fun `guardar respeita a R1 e so de conta propria`() = runTest {
        val fone = servico.criar(Objetivo("", "Fone")).ok()
        assertEquals("Saldo insuficiente no Pix (R$ 1.000,00). Registre a entrada antes ou escolha outra conta.", servico.guardar(fone, pix, 100_001, dia).recusada())
        servico.guardar(fone, va, 1_000, dia).recusada() // benefício não se guarda
        servico.guardar(fone, pix, 100_000, dia).ok()
        assertEquals(0L, visao().resumo.disponivel)
        assertTrue(servico.retirar(fone, pix, 100_001, dia).recusada().contains("Só há R$ 1.000,00"))
    }

    @Test
    fun `mover entre caixinhas e desfazer com a R1`() = runTest {
        val a = servico.criar(Objetivo("", "A")).ok()
        val b = servico.criar(Objetivo("", "B")).ok()
        val g = servico.guardar(a, pix, 40_000, dia).ok()
        val m = servico.mover(a, b, 10_000, dia).ok()
        visao().let { assertEquals(30_000L, it.objetivos.guardado(a)); assertEquals(10_000L, it.objetivos.guardado(b)); assertEquals(60_000L, it.resumo.disponivel) }
        servico.retirar(b, pix, 10_000, dia).ok()
        // Desfazer o mover deixaria a caixinha B negativa: a R1 recusa.
        servico.desfazer(m).recusada()
        // Desfazer o guardar de 40.000 deixaria a caixinha A (30.000) negativa.
        servico.desfazer(g).recusada()
        // Desfazer a retirada de B depois de gastar o Pix: o Pix ficaria negativo.
        movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 70_000, pix, null, null, "Compra")).ok()
        val retirada = visao().objetivos.situacao(b)!!.movimentos.first { it.tipo == com.hkawzz.nkote.dominio.TipoMovimentoCaixinha.RETIRAR }
        servico.desfazer(retirada.movimentacaoId).recusada()
        // Com uma entrada de 10.000 no Pix, desfazer devolve o dinheiro para B.
        movs.criar(Movimentacao("", TipoMovimentacao.ENTRADA, dia, 10_000, pix, null, null, "Salário")).ok()
        servico.desfazer(retirada.movimentacaoId).ok()
        assertEquals(0L, visao().saldo(pix))
        assertEquals(10_000L, visao().objetivos.guardado(b))
    }

    @Test
    fun `caixinha nao e meio de pagamento em nenhum lugar`() = runTest {
        val fone = servico.criar(Objetivo("", "Fone")).ok()
        servico.guardar(fone, pix, 30_000, dia).ok()
        val cx = caixinha(fone)
        // Lançamento avulso (despesa, transferência de e para a caixinha).
        movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 100, cx, null, null, "x")).recusada()
        movs.criar(Movimentacao("", TipoMovimentacao.TRANSFERENCIA, dia, 100, cx, pix, null, "x")).recusada()
        movs.criar(Movimentacao("", TipoMovimentacao.TRANSFERENCIA, dia, 100, pix, cx, null, "x")).recusada()
        // A transferência do guardar não muda pela aba Movimentações.
        val guardar = visao().objetivos.situacao(fone)!!.movimentos.single()
        movs.excluir(guardar.movimentacaoId).recusada()
        movs.editar(movs.buscar(guardar.movimentacaoId)!!.copy(valor = 1)).recusada()
        // Pagar conta a pagar, fatura, empréstimo e conta padrão.
        val luz = ObrigacaoRepositorio(banco, auditor, relogio).criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Luz", 1_000, dia, dez)).ok()
        PagamentoServico(banco, auditor, relogio).pagar(luz, cx, dia, 1_000, null).recusada()
        val cartao = contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, LocalDate.of(2026, 11, 1), diaFechamento = 5, diaVencimento = 12)).ok()
        CompraServico(banco, auditor, relogio).registrar(NovaCompra(cartao, "Fone", null, LocalDate.of(2026, 11, 3), 1_000, 1, LocalDate.of(2026, 11, 12))).ok()
        PagamentoCreditoServico(banco, auditor, relogio).pagarFatura(cartao, YearMonth.of(2026, 11), cx, dia, 1_000, null).recusada()
        EmprestimoServico(banco, auditor, relogio).registrar(NovoEmprestimo(TipoEmprestimo.CONCEDIDO, null, "Tonho", "Ajuda", null, 1_000, dia, cx, listOf(dia.plusMonths(1) to 1_000))).recusada()
        ContaFixaRepositorio(banco, auditor, relogio).criar(ContaFixa("", "Aluguel", 1_000, 10, contaPadraoId = cx)).recusada()
        // A tela de contas não cria, edita, arquiva nem exclui caixinhas.
        contas.criar(Conta("", "Minha caixinha", Natureza.CAIXINHA, null, 0, dia)).recusada()
        contas.editar(contas.buscar(cx)!!.copy(nome = "X")).recusada()
        contas.arquivar(cx).recusada()
        assertTrue(contas.excluir(cx) is ContaRepositorio.ResultadoExclusao.TemVinculos)
        // E nenhuma lista de "pagar com" (meios de pagamento) a mostra.
        assertTrue(visao().contas.filter { it.ehMeioDePagamento }.none { it.id == cx })
        assertEquals(30_000L, visao().saldo(cx))
    }

    @Test
    fun `O3 concluir e arquivar mantem o dinheiro e excluir so com a caixinha zerada`() = runTest {
        val fone = servico.criar(Objetivo("", "Fone", meta = 30_000, prazo = LocalDate.of(2027, 6, 30))).ok()
        servico.guardar(fone, pix, 30_000, dia).ok()
        servico.concluir(fone).ok()
        assertEquals(30_000L, visao().resumo.reservado)
        servico.guardar(fone, pix, 100, dia).recusada()
        servico.arquivar(fone).ok()
        assertTrue(servico.excluir(fone).recusada().contains("Retire ou mova os R$ 300,00"))
        servico.retirar(fone, pix, 30_000, dia).ok()
        val cx = caixinha(fone)
        servico.excluir(fone).ok()
        assertTrue(visao().objetivos.objetivos.isEmpty())
        // A caixinha fica arquivada (não excluída): o histórico continua sendo dinheiro seu.
        assertTrue(contas.buscar(cx)!!.arquivada)
        assertEquals(0L, Analises.fluxo(visao().contas, visao().movimentacoes, Periodo(dez, dez)).saiu)
        assertTrue(servico.criar(Objetivo("", " ")) is Resultado.Falha)
        assertTrue(servico.criar(Objetivo("", "X", meta = 0)) is Resultado.Falha)
    }
}
