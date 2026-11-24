package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.TipoEmprestimo
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

/** v0.6 (A4): empréstimo anterior com total do contrato e parcelas já pagas. Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class EmprestimoAnteriorNumeradoTest {
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso() // hoje = 01/12/2026
    private lateinit var contas: ContaRepositorio
    private lateinit var compras: CompraServico
    private lateinit var pagamentos: PagamentoCreditoServico
    private lateinit var emprestimos: EmprestimoServico
    private lateinit var pix: String
    private lateinit var cartao: String

    @Before
    fun abrir() = runTest {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        compras = CompraServico(banco, auditor, relogio)
        pagamentos = PagamentoCreditoServico(banco, auditor, relogio)
        emprestimos = EmprestimoServico(banco, auditor, relogio)
        CategoriaRepositorio(banco, auditor, relogio).garantirCategoriasIniciais()
        pix = contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 300_000, LocalDate.of(2026, 7, 1))).ok()
        cartao = contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, LocalDate.of(2026, 7, 1), diaFechamento = 5, diaVencimento = 12)).ok()
    }

    @After fun fechar() = banco.close()

    private fun <T> Resultado<T>.ok(): T = (this as? Resultado.Sucesso)?.valor ?: error((this as Resultado.Falha).mensagem)
    private suspend fun conta(id: String) = contas.observar().first().firstOrNull { it.id == id }

    private fun anterior(total: Int?, pagas: Int, pendentes: Int) = NovoEmprestimo(
        TipoEmprestimo.RECEBIDO, null, "Banco", "Consignado", null, 0, LocalDate.of(2026, 7, 1), null,
        (1..pendentes).map { LocalDate.of(2026, 12, 10).plusMonths(it - 1L) to 10_000L }, anteriorAoApp = true,
        totalParcelasDoContrato = total, parcelasJaPagas = pagas,
    )

    @Test
    fun `so as pendentes sao criadas e numeradas a partir das pagas`() = runTest {
        val e = emprestimos.registrar(anterior(12, 4, 8)).ok()
        val ps = banco.obrigacaoDao().doEmprestimo(e).sortedBy { it.numeroParcela }
        assertEquals(8, ps.size)
        assertEquals((5..12).toList(), ps.map { it.numeroParcela })
        assertTrue(ps.all { it.totalParcelas == 12 })
        assertEquals("Consignado (5/12)", ps.first().descricao)
        // Nenhuma regra financeira muda (6E.10): ajuste de abertura = −Σ pendentes; Pix intacto.
        val pessoa = banco.emprestimoDao().buscar(e)!!.contaId
        val movs = banco.backupDao().movimentacoes().filter { it.excluidoEm == null }
        assertEquals(listOf(-80_000L), movs.filter { it.contaId == pessoa }.map { it.valorCentavos })
        assertTrue(movs.none { it.contaId == pix || it.contaDestinoId == pix })
    }

    @Test
    fun `pagas mais pendentes precisam fechar o total do contrato`() = runTest {
        assertTrue(emprestimos.registrar(anterior(12, 4, 7)) is Resultado.Falha)
        assertTrue(emprestimos.registrar(anterior(null, -1, 3)) is Resultado.Falha)
        // Sem o total, ele é pagas + pendentes.
        val e = emprestimos.registrar(anterior(null, 2, 3)).ok()
        assertEquals(listOf(3, 4, 5), banco.obrigacaoDao().doEmprestimo(e).mapNotNull { it.numeroParcela }.sorted())
        // Sem nada informado, como na v0.5: 1/N.
        val e2 = emprestimos.registrar(anterior(null, 0, 2).copy(descricao = "Outro")).ok()
        assertEquals(listOf(1, 2), banco.obrigacaoDao().doEmprestimo(e2).mapNotNull { it.numeroParcela }.sorted())
    }
}