package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.ContaFixa
import com.hkawzz.nkote.dominio.Obrigacao
import com.hkawzz.nkote.dominio.OrigemObrigacao
import com.hkawzz.nkote.dominio.PrepararMes
import com.hkawzz.nkote.dominio.StatusObrigacao
import com.hkawzz.nkote.dominio.VisaoAPagar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/** v0.6 (regra 7.6, ADR 0026): Preparar próximo mês. Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class PrepararMesTest {
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso() // hoje = 01/12/2026
    private lateinit var fixas: ContaFixaRepositorio
    private lateinit var obrigs: ObrigacaoRepositorio
    private lateinit var servico: PrepararMesServico
    private lateinit var aluguel: String
    private lateinit var luz: String
    private lateinit var pendencia: String
    private val nov = YearMonth.of(2026, 11)
    private val dez = YearMonth.of(2026, 12)

    @Before
    fun abrir() = runTest {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        fixas = ContaFixaRepositorio(banco, auditor, relogio)
        obrigs = ObrigacaoRepositorio(banco, auditor, relogio)
        servico = PrepararMesServico(banco, auditor, relogio)
        aluguel = (fixas.criar(ContaFixa("", "Aluguel", 100_000, 10)) as Resultado.Sucesso).valor
        luz = (fixas.criar(ContaFixa("", "Luz", 15_000, 20)) as Resultado.Sucesso).valor
        // Novembro: só a luz foi lançada, com R$ 180,00 (a conta veio mais alta).
        obrigs.lancarFixas(nov, listOf(luz))
        val luzNov = obrigs.observar().first().single { it.contaFixaId == luz }
        obrigs.editar(luzNov.copy(valorPrevisto = 18_000))
        // Uma conta avulsa de outubro, não paga.
        pendencia = (obrigs.criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Conserto", 25_000, LocalDate.of(2026, 10, 15), YearMonth.of(2026, 10))) as Resultado.Sucesso).valor
    }

    @After fun fechar() = banco.close()

    private suspend fun visao() = VisaoAPagar(
        fixas.observar().first(), obrigs.observar().first(),
        banco.pagamentoDao().observarValidos().first().map { it.paraDominio() },
        banco.ajusteObrigacaoDao().observarValidos().first().map { it.paraDominio() },
        banco.pendenciaLevadaDao().observarValidas().first().map { it.paraDominio() },
    )

    @Test
    fun `sugere as fixas com o valor do mes anterior e as pendencias desmarcadas`() = runTest {
        val v = visao()
        val sugeridas = PrepararMes.fixas(v, dez)
        assertEquals(listOf("Aluguel" to 100_000L, "Luz" to 18_000L), sugeridas.map { it.fixa.nome to it.valorSugerido })
        assertEquals(LocalDate.of(2026, 12, 10), sugeridas.first().vencimento)
        // Pendências: a avulsa de outubro e a luz de novembro (não paga).
        assertEquals(setOf("Conserto", "Luz"), PrepararMes.pendencias(v, dez, relogio.hoje()).map { it.obrigacao.descricao }.toSet())
        assertTrue(PrepararMes.deveSugerir(v, dez))
    }

    @Test
    fun `confirmar grava tudo numa transacao e repetir nao duplica`() = runTest {
        val r = servico.confirmar(dez, listOf(FixaEscolhida(aluguel, 100_000), FixaEscolhida(luz, 20_000)), listOf(pendencia))
        assertEquals(ResultadoPreparo(2, 1), (r as Resultado.Sucesso).valor)
        val v = visao()
        val doMes = v.doMes(dez, relogio.hoje())
        assertEquals(setOf("Aluguel", "Luz", "Conserto"), doMes.map { it.obrigacao.descricao }.toSet())
        assertEquals(20_000L, doMes.single { it.obrigacao.descricao == "Luz" }.obrigacao.valorPrevisto)
        // A pendência levada mantém mês, vencimento e valor originais, e continua atrasada.
        val levada = doMes.single { it.obrigacao.id == pendencia }
        assertEquals(YearMonth.of(2026, 10), levada.obrigacao.mesReferencia)
        assertEquals(LocalDate.of(2026, 10, 15), levada.obrigacao.vencimento)
        assertEquals(25_000L, levada.obrigacao.valorPrevisto)
        assertEquals(StatusObrigacao.ATRASADA, levada.status)
        // Não aparece duas vezes em dezembro; continua no mês original e nos meses seguintes como pendente.
        assertFalse(v.pendentesAnteriores(dez, relogio.hoje()).any { it.obrigacao.id == pendencia })
        assertTrue(v.pendentesAnteriores(dez.plusMonths(1), relogio.hoje()).any { it.obrigacao.id == pendencia })
        assertTrue(v.situacoes(relogio.hoje()).any { it.obrigacao.id == pendencia && it.obrigacao.mesReferencia == YearMonth.of(2026, 10) })
        // Um grupo só no Registro de alterações.
        val registros = banco.auditoriaDao().listarTodas().filter { it.resumo.contains("preparar o mês") || it.resumo.contains("levada") }
        assertEquals(3, registros.size)
        assertEquals(1, registros.map { it.grupoId }.distinct().size)
        // Repetir: nada novo, e o assistente não sugere mais as fixas.
        assertEquals(ResultadoPreparo(0, 0), (servico.confirmar(dez, listOf(FixaEscolhida(aluguel, 100_000)), listOf(pendencia)) as Resultado.Sucesso).valor)
        assertEquals(2 + 1, obrigs.observar().first().count { it.mesReferencia == dez } + 1)
        assertEquals(1, banco.pendenciaLevadaDao().observarValidas().first().size)
        assertTrue(PrepararMes.fixas(visao(), dez).isEmpty())
        assertFalse(PrepararMes.pendencias(visao(), dez, relogio.hoje()).any { it.obrigacao.id == pendencia })
    }

    @Test
    fun `valor zero e pendencia do proprio mes sao recusados sem gravar nada`() = runTest {
        assertTrue(servico.confirmar(dez, listOf(FixaEscolhida(aluguel, 0)), emptyList()) is Resultado.Falha)
        val luzNov = obrigs.observar().first().single { it.contaFixaId == luz }.id
        assertTrue(servico.confirmar(nov, listOf(FixaEscolhida(aluguel, 100_000)), listOf(luzNov)) is Resultado.Falha)
        // A falha desfaz tudo: o aluguel de novembro não entrou.
        assertTrue(obrigs.observar().first().none { it.contaFixaId == aluguel })
    }
}
