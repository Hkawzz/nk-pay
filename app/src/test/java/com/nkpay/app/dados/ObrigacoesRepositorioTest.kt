package com.nkpay.app.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nkpay.app.dominio.ContaFixa
import com.nkpay.app.dominio.Obrigacao
import com.nkpay.app.dominio.OrigemObrigacao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/** Repositórios de contas fixas e obrigações (banco em memória, dados fictícios). */
@RunWith(AndroidJUnit4::class)
class ObrigacoesRepositorioTest {

    private lateinit var banco: NkPayBanco
    private lateinit var fixas: ContaFixaRepositorio
    private lateinit var obrigs: ObrigacaoRepositorio
    private val dezembro = YearMonth.of(2026, 12)

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java)
            .allowMainThreadQueries().build()
        val relogio = RelogioFalso()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        fixas = ContaFixaRepositorio(banco, auditor, relogio)
        obrigs = ObrigacaoRepositorio(banco, auditor, relogio)
    }

    @After fun fechar() = banco.close()

    private fun <T> Resultado<T>.ok(): T = when (this) {
        is Resultado.Sucesso -> valor
        is Resultado.Falha -> throw AssertionError(mensagem)
    }

    @Test
    fun `lancar fixas cria so as escolhidas, com o valor e o vencimento do mes`() = runTest {
        val luz = fixas.criar(ContaFixa("", "Luz", 10_000, 31)).ok()
        val net = fixas.criar(ContaFixa("", "Internet", 9_990, 15)).ok()
        fixas.criar(ContaFixa("", "Streaming", 2_990, null)).ok()

        assertEquals(2, obrigs.lancarFixas(YearMonth.of(2027, 2), listOf(luz, net)).ok())
        val lista = obrigs.observar().first()
        assertEquals(2, lista.size)
        val o = lista.first { it.contaFixaId == luz }
        assertEquals(OrigemObrigacao.FIXA, o.origem)
        assertEquals(10_000L, o.valorPrevisto)
        assertEquals(LocalDate.of(2027, 2, 28), o.vencimento) // dia 31 em fevereiro → último dia
        assertEquals(YearMonth.of(2027, 2), o.mesReferencia)
    }

    @Test
    fun `repetir o lancamento no mesmo mes nao duplica`() = runTest {
        val luz = fixas.criar(ContaFixa("", "Luz", 10_000, 10)).ok()
        assertEquals(1, obrigs.lancarFixas(dezembro, listOf(luz)).ok())
        assertEquals(0, obrigs.lancarFixas(dezembro, listOf(luz, luz)).ok())
        assertEquals(1, obrigs.observar().first().size)
        // Em outro mês, pode.
        assertEquals(1, obrigs.lancarFixas(dezembro.plusMonths(1), listOf(luz)).ok())
        assertEquals(2, obrigs.observar().first().size)
    }

    @Test
    fun `mudar a obrigacao de uma fixa para um mes onde ela ja existe e recusado`() = runTest {
        val luz = fixas.criar(ContaFixa("", "Luz", 10_000, 10)).ok()
        obrigs.lancarFixas(dezembro, listOf(luz)).ok()
        obrigs.lancarFixas(dezembro.plusMonths(1), listOf(luz)).ok()
        val dez = obrigs.observar().first().first { it.mesReferencia == dezembro }
        val r = obrigs.editar(dez.copy(mesReferencia = dezembro.plusMonths(1), vencimento = null))
        assertTrue(r is Resultado.Falha)
    }

    @Test
    fun `editar a conta fixa nao muda as obrigacoes ja lancadas`() = runTest {
        val luz = fixas.criar(ContaFixa("", "Luz", 10_000, 10)).ok()
        obrigs.lancarFixas(dezembro, listOf(luz)).ok()
        fixas.editar(fixas.buscar(luz)!!.copy(valorPadrao = 12_000)).ok()
        assertEquals(10_000L, obrigs.observar().first().single().valorPrevisto)
    }

    @Test
    fun `conta avulsa sem mes usa o mes do vencimento`() = runTest {
        val id = obrigs.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "IPVA", 50_000, LocalDate.of(2027, 1, 20), null)).ok()
        assertEquals(YearMonth.of(2027, 1), obrigs.buscar(id)!!.mesReferencia)
    }

    @Test
    fun `cada operacao gera auditoria e o lancamento de varias fixas usa um grupo so`() = runTest {
        val a = fixas.criar(ContaFixa("", "Luz", 10_000, 10)).ok()
        val b = fixas.criar(ContaFixa("", "Internet", 9_990, 15)).ok()
        obrigs.lancarFixas(dezembro, listOf(a, b)).ok()
        val reg = banco.auditoriaDao().listarTodas()
        assertEquals(4, reg.size)
        val lancamentos = reg.filter { it.entidade == Auditor.OBRIGACAO }
        assertEquals(1, lancamentos.map { it.grupoId }.distinct().size)
    }

    @Test
    fun `excluir e logico e some da lista`() = runTest {
        val id = obrigs.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Presente", 8_000, null, dezembro)).ok()
        obrigs.excluir(id).ok()
        assertTrue(obrigs.observar().first().isEmpty())
        assertNull(obrigs.buscar(id))
        assertTrue(banco.obrigacaoDao().buscar(id)!!.excluidoEm != null)
    }

    @Test
    fun `validacoes recusam valor zero e conta sem mes nem vencimento`() = runTest {
        assertTrue(obrigs.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "X", 0, null, dezembro)) is Resultado.Falha)
        assertTrue(obrigs.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "X", 100, null, null)) is Resultado.Falha)
        assertTrue(fixas.criar(ContaFixa("", "Luz", 100, 32)) is Resultado.Falha)
    }
}
