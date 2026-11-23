package com.nkpay.app.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nkpay.app.dominio.ClassificacaoDiferenca
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Emprestimos
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.Saldos
import com.nkpay.app.dominio.TipoEmprestimo
import com.nkpay.app.dominio.VisaoEmprestimos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Matriz de backup da v0.5: um backup com empréstimos recebidos e concedidos, anteriores
 * ao app, pagamentos, recebimentos com juros, desconto concedido e edição de parcela vai e
 * volta igual, campo a campo, e as invariantes continuam fechando. Valores fictícios.
 */
@RunWith(AndroidJUnit4::class)
class BackupEmprestimosTest {
    @get:Rule val pasta = TemporaryFolder()
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso()

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
    }

    @After fun fechar() = banco.close()

    private suspend fun foto(): List<Any> = banco.backupDao().let {
        listOf(it.contas(), it.categorias(), it.movimentacoes(), it.obrigacoes(), it.pagamentos(), it.ajustesDeObrigacao(), it.emprestimos(), it.compras(), it.pagamentosDeFatura())
    }

    @Test
    fun `backup com emprestimos vai e volta igual e as invariantes fecham`() = runTest {
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        val contas = ContaRepositorio(banco, auditor, relogio)
        val movs = MovimentacaoRepositorio(banco, auditor, relogio)
        CategoriaRepositorio(banco, auditor, relogio).garantirCategoriasIniciais()
        val emp = EmprestimoServico(banco, auditor, relogio)
        val pagar = PagamentoCreditoServico(banco, auditor, relogio)
        fun <T> Resultado<T>.ok() = (this as Resultado.Sucesso).valor
        val d = LocalDate.of(2026, 12, 1)
        val pix = contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 300_000, d)).ok()
        fun p(n: Int, v: Long) = Emprestimos.parcelasAutomaticas(n, v, LocalDate.of(2027, 1, 10))
        val r = emp.registrar(NovoEmprestimo(TipoEmprestimo.RECEBIDO, null, "Banco X", "Pessoal", null, 100_000, d, pix, p(10, 11_000))).ok()
        val c = emp.registrar(NovoEmprestimo(TipoEmprestimo.CONCEDIDO, null, "Tonho", "Carro", null, 100_000, d, pix, p(3, 35_000))).ok()
        val ra = emp.registrar(NovoEmprestimo(TipoEmprestimo.RECEBIDO, null, "Loja Y", "Antigo", null, 0, d, null, p(2, 20_000), anteriorAoApp = true)).ok()
        val ca = emp.registrar(NovoEmprestimo(TipoEmprestimo.CONCEDIDO, null, "Zé", "Antigo", null, 0, d, null, p(4, 11_000), anteriorAoApp = true, jurosInformados = 4_000)).ok()
        suspend fun visao(): VisaoEmprestimos {
            val o = banco.backupDao().obrigacoes().filter { it.excluidoEm == null }.map { it.paraDominio() }
            return VisaoEmprestimos(
                contas.observar().first(), movs.observar().first(), banco.emprestimoDao().observarValidos().first().map { it.paraDominio() }, o,
                banco.pagamentoDao().observarValidos().first().map { it.paraDominio() }, banco.ajusteObrigacaoDao().observarValidos().first().map { it.paraDominio() },
            )
        }
        suspend fun parcela(id: String, k: Int) = visao().parcelasDe(id)[k - 1].id
        pagar.pagarParcela(parcela(r, 1), pix, d, 11_500, ClassificacaoDiferenca.MULTA).ok()
        pagar.receberParcela(parcela(c, 1), pix, d, 35_000, null).ok()
        pagar.receberParcela(parcela(c, 2), pix, d, 30_000, ClassificacaoDiferenca.DESCONTO).ok()
        pagar.receberParcela(parcela(ca, 1), pix, d, 11_000, null).ok()
        pagar.editarParcelas(parcela(r, 5), 12_000, null, true).ok()

        val hoje = relogio.hoje()
        fun todas(v: VisaoEmprestimos) = listOf(r, c, ra, ca).map { v.situacao(it, hoje)!! }
        val antes = foto()
        val numeros = todas(visao()).map { listOf(it.restante, it.saldoNaConta, it.jurosNaoReconhecidos, it.jurosRecebidos) }
        val saldoPix = Saldos.saldosPorConta(contas.observar().first(), movs.observar().first())[pix]
        assertTrue(todas(visao()).all { it.invarianteFecha })

        val servico = BackupServico(banco, relogio, "teste", pasta.newFolder("copias"), RegistroDeBackupEmMemoria())
        val arquivo = ByteArrayOutputStream().also { servico.exportar(it) }.toByteArray()
        contas.criar(Conta("", "Depois", Natureza.PROPRIO, null, 1, d))
        servico.restaurar(servico.ler(ByteArrayInputStream(arquivo)))

        assertEquals(antes, foto())
        assertEquals(numeros, todas(visao()).map { listOf(it.restante, it.saldoNaConta, it.jurosNaoReconhecidos, it.jurosRecebidos) })
        assertEquals(saldoPix, Saldos.saldosPorConta(contas.observar().first(), movs.observar().first())[pix])
        val v = visao()
        assertTrue(todas(v).all { it.invarianteFecha })
        assertTrue(listOf(r, c, ra, ca).all { v.invarianteDaConta(v.emprestimo(it)!!.contaId, hoje) })
        assertEquals(4, servico.ler(ByteArrayInputStream(arquivo)).manifesto.emprestimos)
    }
}
