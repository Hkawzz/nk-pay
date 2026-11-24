package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.CategoriasDoSistema
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Emprestimos
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.TipoEmprestimo
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.dominio.VisaoEmprestimos
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

/** Registrar e excluir empréstimos (regras 6E.4, 6E.5, 6E.9 e 6E.10). Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class EmprestimoServicoTest {
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso() // hoje = 01/12/2026
    private lateinit var contas: ContaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var servico: EmprestimoServico
    private lateinit var categorias: CategoriaRepositorio
    private lateinit var pix: String
    private val d = LocalDate.of(2026, 12, 10)

    @Before
    fun abrir() = runTest {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        movs = MovimentacaoRepositorio(banco, auditor, relogio)
        servico = EmprestimoServico(banco, auditor, relogio)
        categorias = CategoriaRepositorio(banco, auditor, relogio)
        categorias.garantirCategoriasIniciais()
        pix = (contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 200_000, LocalDate.of(2026, 11, 1))) as Resultado.Sucesso).valor
    }

    @After fun fechar() = banco.close()

    private fun dez10() = Emprestimos.parcelasAutomaticas(10, 11_000, LocalDate.of(2027, 1, 10))
    private fun novo(tipo: TipoEmprestimo, anterior: Boolean = false, parcelas: List<Pair<LocalDate, Long>> = dez10(), juros: Long = 0, valor: Long = 100_000) = NovoEmprestimo(
        tipo, null, if (tipo == TipoEmprestimo.RECEBIDO) "Banco X" else "Tonho", "Empréstimo", null, valor, d,
        if (anterior) null else pix, parcelas, anterior, juros,
    )

    private suspend fun visao(): VisaoEmprestimos {
        val o = banco.backupDao().obrigacoes().filter { it.excluidoEm == null }.map { it.paraDominio() }
        return VisaoEmprestimos(
            contas.observar().first(), movs.observar().first(), banco.emprestimoDao().observarValidos().first().map { it.paraDominio() },
            o, banco.pagamentoDao().observarValidos().first().map { it.paraDominio() }, banco.ajusteObrigacaoDao().observarValidos().first().map { it.paraDominio() },
        )
    }

    private suspend fun saldoPix() = Saldos.saldosPorConta(contas.observar().first(), movs.observar().first())[pix]
    private fun <T> Resultado<T>.ok(): T = (this as Resultado.Sucesso).valor

    @Test
    fun `recebido J1 Pix mais 1000 juros 100 uma vez na contratacao e divida 1100`() = runTest {
        val id = servico.registrar(novo(TipoEmprestimo.RECEBIDO)).ok()
        assertEquals(300_000L, saldoPix())
        val s = visao().situacao(id, relogio.hoje())!!
        assertEquals(110_000L, s.saldoNaConta)
        assertEquals(10_000L, s.jurosPrevistos)
        assertTrue(s.invarianteFecha)
        assertTrue(visao().invarianteDaConta(s.emprestimo.contaId, relogio.hoje()))
        val encargos = categorias.observar().first().first { it.codigoSistema == CategoriasDoSistema.ENCARGOS_FINANCEIROS }.id
        val despesas = movs.observar().first().filter { it.tipo == TipoMovimentacao.DESPESA }
        assertEquals(listOf(10_000L), despesas.map { it.valor })
        assertEquals(encargos, despesas.single().categoriaId)
        assertEquals(d, despesas.single().data)
        // A pessoa virou uma conta de terceiros (empréstimo recebido), com saldo inicial zero.
        assertEquals(Natureza.EMPRESTIMO_RECEBIDO, contas.buscar(s.emprestimo.contaId)!!.natureza)
        // Nenhuma entrada (o valor original é transferência).
        assertEquals(0L, Saldos.totaisDoMes(movs.observar().first(), YearMonth.of(2026, 12)).entradas)
    }

    @Test
    fun `concedido K2 Pix menos 1000 sem despesa`() = runTest {
        val id = servico.registrar(novo(TipoEmprestimo.CONCEDIDO)).ok()
        assertEquals(100_000L, saldoPix())
        val s = visao().situacao(id, relogio.hoje())!!
        assertEquals(100_000L, s.saldoNaConta)
        assertEquals(10_000L, s.jurosNaoReconhecidos)
        assertTrue(s.invarianteFecha)
        assertEquals(0L, Saldos.totaisDoMes(movs.observar().first(), YearMonth.of(2026, 12)).despesas)
    }

    @Test
    fun `parcelas personalizadas e um segundo emprestimo com a mesma pessoa somam na conta`() = runTest {
        val p = listOf(LocalDate.of(2027, 3, 5) to 30_000L, LocalDate.of(2027, 1, 20) to 50_000L, LocalDate.of(2027, 2, 1) to 40_000L)
        val id = servico.registrar(novo(TipoEmprestimo.CONCEDIDO, parcelas = p)).ok()
        val v = visao()
        val ps = v.parcelasDe(id)
        assertEquals(listOf(50_000L, 40_000L, 30_000L), ps.map { it.valorPrevisto }) // em ordem de vencimento
        assertEquals(listOf(1, 2, 3), ps.map { it.numeroParcela })
        val pessoa = v.emprestimo(id)!!.contaId
        servico.registrar(novo(TipoEmprestimo.CONCEDIDO, valor = 10_000, parcelas = listOf(LocalDate.of(2027, 1, 1) to 10_000L)).copy(contaPessoaId = pessoa, nomeNovaPessoa = null)).ok()
        assertEquals(110_000L, Saldos.saldosPorConta(contas.observar().first(), movs.observar().first())[pessoa])
        assertTrue(visao().invarianteDaConta(pessoa, relogio.hoje()))
    }

    @Test
    fun `anterior ao app nao mexe no Pix nem cria receita ou despesa`() = runTest {
        val antes = saldoPix()
        val r = servico.registrar(novo(TipoEmprestimo.RECEBIDO, anterior = true, parcelas = Emprestimos.parcelasAutomaticas(3, 11_000, LocalDate.of(2027, 1, 10)))).ok()
        val c = servico.registrar(novo(TipoEmprestimo.CONCEDIDO, anterior = true, parcelas = Emprestimos.parcelasAutomaticas(4, 11_000, LocalDate.of(2027, 1, 10)), juros = 4_000)).ok()
        assertEquals(antes, saldoPix())
        val t = Saldos.totaisDoMes(movs.observar().first(), YearMonth.of(2026, 12))
        assertEquals(0L, t.entradas + t.despesas)
        val v = visao()
        assertEquals(33_000L, v.situacao(r, relogio.hoje())!!.saldoNaConta)
        assertEquals(40_000L, v.situacao(c, relogio.hoje())!!.saldoNaConta)
        assertEquals(4_000L, v.situacao(c, relogio.hoje())!!.jurosNaoReconhecidos)
        assertTrue(v.situacao(r, relogio.hoje())!!.invarianteFecha && v.situacao(c, relogio.hoje())!!.invarianteFecha)
    }

    @Test
    fun `validacoes recusam sem gravar`() = runTest {
        val antes = banco.backupDao().movimentacoes().size
        assertTrue(servico.registrar(novo(TipoEmprestimo.RECEBIDO, valor = 120_000)) is Resultado.Falha) // total < original
        assertTrue(servico.registrar(novo(TipoEmprestimo.RECEBIDO).copy(descricao = " ")) is Resultado.Falha)
        assertTrue(servico.registrar(novo(TipoEmprestimo.RECEBIDO).copy(parcelas = emptyList())) is Resultado.Falha)
        assertTrue(servico.registrar(novo(TipoEmprestimo.CONCEDIDO, anterior = true, juros = 200_000)) is Resultado.Falha)
        assertEquals(antes, banco.backupDao().movimentacoes().size)
        assertTrue(banco.backupDao().emprestimos().isEmpty())
    }

    @Test
    fun `excluir desfaz tudo e e bloqueado com pagamento`() = runTest {
        val id = servico.registrar(novo(TipoEmprestimo.RECEBIDO)).ok()
        assertTrue(servico.excluir(id) is Resultado.Sucesso)
        assertEquals(200_000L, saldoPix())
        assertTrue(movs.observar().first().isEmpty())
        assertTrue(visao().parcelasDe(id).isEmpty())

        val id2 = servico.registrar(novo(TipoEmprestimo.CONCEDIDO)).ok()
        val p = visao().parcelasDe(id2).first()
        val pessoa = visao().emprestimo(id2)!!.contaId
        banco.movimentacaoDao().inserir(MovimentacaoEntidade("t", "TRANSFERENCIA", "2027-01-10", 10_000, pessoa, pix, null, "x", 1, 1, null))
        banco.pagamentoDao().inserir(PagamentoEntidade("pg", p.id, "t", 11_000, 1, 1, null))
        assertTrue(servico.excluir(id2) is Resultado.Falha)
        // A contratação fica bloqueada em Movimentações.
        val contratacao = visao().emprestimo(id2)!!.movimentacaoContratacaoId!!
        assertTrue(movs.excluir(contratacao) is Resultado.Falha)
    }
}
