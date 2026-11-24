package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.dominio.Validacao
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Regra 6E.1 / ADR 0022: contas de empréstimo são contas de terceiros, recusadas em lançamentos avulsos. */
@RunWith(AndroidJUnit4::class)
class IntegridadeEmprestimoTest {
    private lateinit var banco: NkPayBanco
    private lateinit var contas: ContaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private val dia = LocalDate.of(2026, 12, 1)

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        val relogio = RelogioFalso()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        movs = MovimentacaoRepositorio(banco, auditor, relogio)
    }

    @After fun fechar() = banco.close()

    @Test
    fun `entrada despesa ajuste e transferencia avulsos em conta de emprestimo sao recusados`() = runTest {
        val pix = (contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 50_000, dia)) as Resultado.Sucesso).valor
        val deve = (contas.criar(Conta("", "Banco X", Natureza.EMPRESTIMO_RECEBIDO, null, 0, dia)) as Resultado.Sucesso).valor
        val meDeve = (contas.criar(Conta("", "Tonho", Natureza.EMPRESTIMO_CONCEDIDO, null, 0, dia)) as Resultado.Sucesso).valor
        val tentativas = listOf(
            Movimentacao("", TipoMovimentacao.ENTRADA, dia, 1_000, deve),
            Movimentacao("", TipoMovimentacao.DESPESA, dia, 1_000, meDeve),
            Movimentacao("", TipoMovimentacao.AJUSTE, dia, 1_000, deve),
            Movimentacao("", TipoMovimentacao.TRANSFERENCIA, dia, 1_000, pix, contaDestinoId = meDeve),
            Movimentacao("", TipoMovimentacao.TRANSFERENCIA, dia, 1_000, deve, contaDestinoId = pix),
        )
        for (m in tentativas) assertEquals(Validacao.MENSAGEM_CONTA_DE_CREDITO, (movs.criar(m) as Resultado.Falha).mensagem)
        assertTrue(banco.backupDao().movimentacoes().isEmpty())
        // Saldo inicial diferente de zero é recusado, e uma conta com lançamentos não vira empréstimo.
        assertTrue(contas.criar(Conta("", "Outro", Natureza.EMPRESTIMO_CONCEDIDO, null, 500, dia)) is Resultado.Falha)
        movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 1_000, pix))
        assertTrue(contas.editar(contas.buscar(pix)!!.copy(natureza = Natureza.EMPRESTIMO_RECEBIDO, saldoInicial = 0)) is Resultado.Falha)
    }
}
