package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Obrigacao
import com.hkawzz.nkote.dominio.OrigemObrigacao
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.TipoBeneficio
import com.hkawzz.nkote.dominio.TipoEmprestimo
import com.hkawzz.nkote.dominio.TipoMovimentacao
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

/** v0.8 (R1, regra 6G.1, ADR 0031): toda saída que deixaria a conta negativa é recusada. Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class SaldoSemChequeEspecialTest {
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso() // hoje = 01/12/2026
    private lateinit var contas: ContaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var pix: String
    private val dia = LocalDate.of(2026, 12, 1)

    @Before
    fun abrir() = runTest {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        movs = MovimentacaoRepositorio(banco, auditor, relogio)
        CategoriaRepositorio(banco, auditor, relogio).garantirCategoriasIniciais()
        pix = contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 4_000, LocalDate.of(2026, 11, 1))).ok()
    }

    @After fun fechar() = banco.close()

    private fun <T> Resultado<T>.ok(): T = when (this) { is Resultado.Sucesso -> valor; is Resultado.Falha -> error(mensagem) }
    private fun Resultado<*>.recusada(): String = (this as? Resultado.Falha)?.mensagem ?: error("deveria ter sido recusada")
    private suspend fun saldo(id: String) = Saldos.saldosPorConta(contas.observar().first(), movs.observar().first())[id]
    private fun mov(tipo: TipoMovimentacao, valor: Long, conta: String, destino: String? = null, data: LocalDate = dia) =
        Movimentacao("", tipo, data, valor, conta, destino, null, "teste")

    @Test
    fun `despesa maior que o saldo e recusada com a mensagem e a exata e aceita`() = runTest {
        val m = movs.criar(mov(TipoMovimentacao.DESPESA, 5_000, pix)).recusada()
        assertEquals("Saldo insuficiente no Pix (R$ 40,00). Registre a entrada antes ou escolha outra conta.", m)
        assertEquals(4_000L, saldo(pix))
        movs.criar(mov(TipoMovimentacao.DESPESA, 4_000, pix)).ok()
        assertEquals(0L, saldo(pix))
    }

    @Test
    fun `transferencia sem saldo na origem e despesa no beneficio sem saldo sao recusadas`() = runTest {
        val dinheiro = contas.criar(Conta("", "Dinheiro", Natureza.PROPRIO, null, 0, LocalDate.of(2026, 11, 1))).ok()
        val va = contas.criar(Conta("", "VA", Natureza.BENEFICIO, TipoBeneficio.VA, 1_000, LocalDate.of(2026, 11, 1))).ok()
        movs.criar(mov(TipoMovimentacao.TRANSFERENCIA, 4_001, pix, dinheiro)).recusada()
        movs.criar(mov(TipoMovimentacao.TRANSFERENCIA, 4_000, pix, dinheiro)).ok()
        assertTrue(movs.criar(mov(TipoMovimentacao.DESPESA, 1_001, va)).recusada().contains("VA (R$ 10,00)"))
    }

    @Test
    fun `vale o saldo de hoje mesmo com data antiga`() = runTest {
        movs.criar(mov(TipoMovimentacao.ENTRADA, 6_000, pix, data = LocalDate.of(2026, 11, 20))).ok()
        // Despesa com data anterior à entrada: o saldo de hoje continua positivo, então é aceita.
        movs.criar(mov(TipoMovimentacao.DESPESA, 7_000, pix, data = LocalDate.of(2026, 11, 5))).ok()
        assertEquals(3_000L, saldo(pix))
        // Outra despesa antiga que deixaria o saldo de hoje negativo: recusada.
        movs.criar(mov(TipoMovimentacao.DESPESA, 3_001, pix, data = LocalDate.of(2026, 11, 2))).recusada()
    }

    @Test
    fun `excluir ou editar uma entrada e ajuste para baixo tambem passam pela regra`() = runTest {
        val e = movs.criar(mov(TipoMovimentacao.ENTRADA, 3_000, pix)).ok()
        movs.criar(mov(TipoMovimentacao.DESPESA, 6_000, pix)).ok() // Pix R$ 10,00
        movs.excluir(e).recusada()
        val entrada = movs.observar().first().first { it.id == e }
        movs.editar(entrada.copy(valor = 1_000)).recusada()
        movs.editar(entrada.copy(valor = 2_000)).ok() // Pix R$ 0,00
        movs.criar(mov(TipoMovimentacao.AJUSTE, -1, pix)).recusada()
        assertEquals(0L, saldo(pix))
    }

    @Test
    fun `conta ja negativa nao muda e so aceita o que nao a piora`() = runTest {
        val antiga = contas.criar(Conta("", "Banco antigo", Natureza.PROPRIO, null, -5_000, LocalDate.of(2026, 11, 1))).ok()
        assertEquals(-5_000L, saldo(antiga))
        movs.criar(mov(TipoMovimentacao.DESPESA, 100, antiga)).recusada()
        movs.criar(mov(TipoMovimentacao.ENTRADA, 2_000, antiga)).ok()
        assertEquals(-3_000L, saldo(antiga))
        // Editar o nome não piora nada.
        contas.editar(contas.buscar(antiga)!!.copy(nome = "Banco")).ok()
    }

    @Test
    fun `pagar conta fatura emprestar e desfazer recebimento sem saldo sao recusados`() = runTest {
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        // Pagar uma conta a pagar.
        val obrigs = ObrigacaoRepositorio(banco, auditor, relogio)
        val luz = obrigs.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Luz", 5_000, dia, YearMonth.of(2026, 12))).ok()
        PagamentoServico(banco, auditor, relogio).pagar(luz, pix, dia, 5_000, null).recusada()
        // Pagar fatura.
        val cartao = contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, LocalDate.of(2026, 11, 1), diaFechamento = 5, diaVencimento = 12)).ok()
        CompraServico(banco, auditor, relogio).registrar(NovaCompra(cartao, "Fone", null, LocalDate.of(2026, 11, 3), 5_000, 1, LocalDate.of(2026, 11, 12))).ok()
        PagamentoCreditoServico(banco, auditor, relogio).pagarFatura(cartao, YearMonth.of(2026, 11), pix, dia, 5_000, null).recusada()
        // Emprestar mais do que tem.
        val emprestimos = EmprestimoServico(banco, auditor, relogio)
        emprestimos.registrar(NovoEmprestimo(TipoEmprestimo.CONCEDIDO, null, "Tonho", "Carro", null, 5_000, dia, pix, listOf(dia.plusMonths(1) to 5_000))).recusada()
        // Receber e gastar, depois desfazer o recebimento: recusado.
        val e = emprestimos.registrar(NovoEmprestimo(TipoEmprestimo.CONCEDIDO, null, "Maria", "Ajuda", null, 4_000, dia, pix, listOf(dia.plusMonths(1) to 4_000))).ok()
        val parcela = banco.obrigacaoDao().doEmprestimo(e).single()
        val pagCred = PagamentoCreditoServico(banco, auditor, relogio)
        val recebimento = pagCred.receberParcela(parcela.id, pix, dia, 4_000, null).ok()
        movs.criar(mov(TipoMovimentacao.DESPESA, 4_000, pix)).ok()
        pagCred.desfazer(recebimento).recusada()
        assertEquals(0L, saldo(pix))
    }
}
