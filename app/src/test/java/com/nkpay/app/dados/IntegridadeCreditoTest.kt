package com.nkpay.app.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Movimentacao
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.dominio.Validacao
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Regra 6B.7 / ADR 0017: contas de crédito não aceitam lançamentos avulsos, venha de onde vier. */
@RunWith(AndroidJUnit4::class)
class IntegridadeCreditoTest {
    private lateinit var banco: NkPayBanco
    private lateinit var contas: ContaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private val dia = LocalDate.of(2026, 12, 1)
    private lateinit var pix: String
    private lateinit var cartao: String
    private lateinit var loja: String

    @Before
    fun abrir() = runTest {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        val relogio = RelogioFalso()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        movs = MovimentacaoRepositorio(banco, auditor, relogio)
        pix = (contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 50_000, dia)) as Resultado.Sucesso).valor
        cartao = (contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, dia, limite = 100_000, diaFechamento = 5, diaVencimento = 12)) as Resultado.Sucesso).valor
        loja = (contas.criar(Conta("", "Loja", Natureza.CREDIARIO, null, 0, dia)) as Resultado.Sucesso).valor
    }

    @After fun fechar() = banco.close()

    @Test
    fun `entrada despesa ajuste e transferencia avulsos em conta de credito sao recusados`() = runTest {
        val tentativas = listOf(
            Movimentacao("", TipoMovimentacao.DESPESA, dia, 1_000, cartao),
            Movimentacao("", TipoMovimentacao.ENTRADA, dia, 1_000, loja),
            Movimentacao("", TipoMovimentacao.AJUSTE, dia, 1_000, cartao),
            Movimentacao("", TipoMovimentacao.TRANSFERENCIA, dia, 1_000, pix, contaDestinoId = cartao),
            Movimentacao("", TipoMovimentacao.TRANSFERENCIA, dia, 1_000, loja, contaDestinoId = pix),
        )
        for (m in tentativas) {
            val r = movs.criar(m)
            assertTrue("$m deveria ser recusada", r is Resultado.Falha)
            assertEquals(Validacao.MENSAGEM_CONTA_DE_CREDITO, (r as Resultado.Falha).mensagem)
        }
        assertTrue(banco.backupDao().movimentacoes().isEmpty())
        // Uma despesa normal continua funcionando.
        assertTrue(movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 1_000, pix)) is Resultado.Sucesso)
    }

    @Test
    fun `editar uma movimentacao para uma conta de credito e recusado`() = runTest {
        val id = (movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 1_000, pix)) as Resultado.Sucesso).valor
        assertTrue(movs.editar(Movimentacao(id, TipoMovimentacao.DESPESA, dia, 1_000, cartao)) is Resultado.Falha)
    }

    @Test
    fun `conta com lancamentos nao vira cartao e cartao guarda limite fechamento e vencimento`() = runTest {
        movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 1_000, pix))
        val p = contas.buscar(pix)!!
        assertTrue(contas.editar(p.copy(natureza = Natureza.CARTAO, saldoInicial = 0, diaFechamento = 5, diaVencimento = 12)) is Resultado.Falha)
        val c = contas.buscar(cartao)!!
        assertEquals(100_000L, c.limite)
        assertEquals(5, c.diaFechamento)
        assertEquals(12, c.diaVencimento)
        assertTrue(contas.criar(Conta("", "Outro", Natureza.CARTAO, null, 500, dia, diaFechamento = 1, diaVencimento = 8)) is Resultado.Falha)
    }
}
