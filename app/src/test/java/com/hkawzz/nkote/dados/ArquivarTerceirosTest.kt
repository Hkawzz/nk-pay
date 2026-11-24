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

/** v0.6 (A3, A5, regra 6B.10, ADR 0027): excluir e arquivar cartões, crediários e pessoas. Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class ArquivarTerceirosTest {
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

    @Test
    fun `cartao sem nada pode ser excluido`() = runTest {
        assertEquals(ContaRepositorio.ResultadoExclusao.Excluida, contas.excluir(cartao))
        assertEquals(null, conta(cartao))
    }

    @Test
    fun `cartao com compra nao exclui e so arquiva depois de quitado`() = runTest {
        compras.registrar(NovaCompra(cartao, "Fone", null, LocalDate.of(2026, 12, 3), 10_000, 1, LocalDate.of(2026, 12, 12))).ok()
        val r = contas.excluir(cartao)
        assertTrue(r is ContaRepositorio.ResultadoExclusao.TemVinculos)
        assertTrue((r as ContaRepositorio.ResultadoExclusao.TemVinculos).motivo.contains("1 compra"))
        // Com devido, arquivar é recusado.
        val a = contas.arquivar(cartao)
        assertTrue(a is Resultado.Falha)
        assertTrue((a as Resultado.Falha).mensagem.contains("R$ 100,00"))
        assertEquals(false, conta(cartao)!!.arquivada)
        // Quitado, arquiva; e desarquiva.
        pagamentos.pagarFatura(cartao, YearMonth.of(2026, 12), pix, LocalDate.of(2026, 12, 10), 10_000, null).ok()
        contas.arquivar(cartao).ok()
        assertEquals(true, conta(cartao)!!.arquivada)
        contas.arquivar(cartao, arquivar = false).ok()
        assertEquals(false, conta(cartao)!!.arquivada)
    }

    @Test
    fun `pessoa com valor a receber nao arquiva e o nome pode mudar com auditoria`() = runTest {
        val e = emprestimos.registrar(NovoEmprestimo(TipoEmprestimo.CONCEDIDO, null, "Tonho", "Carro", null, 50_000, LocalDate.of(2026, 11, 25), pix,
            listOf(LocalDate.of(2027, 1, 10) to 50_000))).ok()
        val pessoa = banco.emprestimoDao().buscar(e)!!.contaId
        assertTrue(contas.arquivar(pessoa) is Resultado.Falha)
        assertTrue(contas.excluir(pessoa) is ContaRepositorio.ResultadoExclusao.TemVinculos)
        contas.editar(conta(pessoa)!!.copy(nome = "Antônio")).ok()
        assertEquals("Antônio", conta(pessoa)!!.nome)
        assertTrue(banco.auditoriaDao().listarTodas().any { it.entidadeId == pessoa && it.resumo.contains("Antônio") })
        // O empréstimo continua ligado à mesma conta.
        assertEquals(pessoa, banco.emprestimoDao().buscar(e)!!.contaId)
    }

    @Test
    fun `pessoa sem nada ligado pode ser excluida`() = runTest {
        val p = contas.criar(Conta("", "Maria", Natureza.EMPRESTIMO_RECEBIDO, null, 0, LocalDate.of(2026, 7, 1))).ok()
        assertEquals(ContaRepositorio.ResultadoExclusao.Excluida, contas.excluir(p))
    }
}
