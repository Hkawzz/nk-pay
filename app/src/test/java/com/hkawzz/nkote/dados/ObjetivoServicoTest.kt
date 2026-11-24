package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Analises
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Objetivo
import com.hkawzz.nkote.dominio.Periodo
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.TipoBeneficio
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

/** v0.7 (regra 6F, ADR 0029): objetivos e reservas no banco. Exemplo da regra 6F.2. Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class ObjetivoServicoTest {
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso() // hoje = 01/12/2026
    private lateinit var contas: ContaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var categorias: CategoriaRepositorio
    private lateinit var servico: ObjetivoServico
    private lateinit var pix: String
    private val dia = LocalDate.of(2026, 12, 1)

    @Before
    fun abrir() = runTest {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        movs = MovimentacaoRepositorio(banco, auditor, relogio)
        categorias = CategoriaRepositorio(banco, auditor, relogio)
        categorias.garantirCategoriasIniciais()
        servico = ObjetivoServico(banco, auditor, relogio)
        pix = contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2026, 11, 1))).ok()
        // VA e cartão nunca entram no guardado nem no disponível.
        contas.criar(Conta("", "VA", Natureza.BENEFICIO, TipoBeneficio.VA, 50_000, LocalDate.of(2026, 11, 1)))
        contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, LocalDate.of(2026, 11, 1), diaFechamento = 5, diaVencimento = 12))
    }

    @After fun fechar() = banco.close()

    private fun <T> Resultado<T>.ok(): T = when (this) {
        is Resultado.Sucesso -> valor
        is Resultado.Falha -> error(mensagem)
    }

    private suspend fun visao() = VisaoFinanceira(
        contas.observar().first(), categorias.observar().first(), movs.observar().first(),
        VisaoObjetivos(banco.objetivoDao().observarValidos().first().map { it.paraDominio() }, banco.movimentoReservaDao().observarValidos().first().map { it.paraDominio() }),
    )

    @Test
    fun `exemplo da regra 6F2 sem mexer em dinheiro`() = runTest {
        val antesMovs = movs.observar().first()
        val fone = servico.criar(Objetivo("", "Fone", meta = 30_000)).ok()
        val viagem = servico.criar(Objetivo("", "Viagem")).ok()
        servico.guardar(fone, 30_000, dia).ok()
        visao().resumo.let { assertEquals(100_000L, it.dinheiroProprio); assertEquals(30_000L, it.reservado); assertEquals(70_000L, it.disponivel) }
        // Guardar R$ 800,00 com R$ 700,00 disponíveis: recusado (o VA não conta).
        val r = servico.guardar(viagem, 80_000, dia)
        assertTrue(r is Resultado.Falha && r.mensagem.contains("R$ 700,00"))
        servico.retirar(fone, 10_000, dia).ok()
        assertEquals(80_000L, visao().resumo.disponivel)
        servico.mover(fone, viagem, 5_000, dia).ok()
        visao().let { assertEquals(15_000L, it.objetivos.guardado(fone)); assertEquals(5_000L, it.objetivos.guardado(viagem)); assertEquals(80_000L, it.resumo.disponivel) }
        assertEquals("ajuste", servico.ajustar(viagem, 8_000, dia).ok()?.let { "ajuste" })
        visao().let { assertEquals(23_000L, it.resumo.reservado); assertEquals(77_000L, it.resumo.disponivel) }
        // Nada disso é dinheiro: movimentações, saldos, entradas, despesas e fluxo iguais.
        val depois = movs.observar().first()
        assertEquals(antesMovs, depois)
        assertEquals(100_000L, visao().saldo(pix))
        assertEquals(0L, Saldos.totaisDoMes(depois, YearMonth.of(2026, 12)).despesas)
        val p = Periodo(YearMonth.of(2026, 12), YearMonth.of(2026, 12))
        assertEquals(Analises.fluxo(visao().contas, depois, p), Analises.fluxo(visao().contas, antesMovs, p))
        // Ajuste para baixo e para o mesmo valor (nada muda).
        servico.ajustar(viagem, 2_000, dia).ok()
        assertEquals(null, servico.ajustar(viagem, 2_000, dia).ok())
        assertEquals(2_000L, visao().objetivos.guardado(viagem))
        // Retirar mais que o guardado e ajustar acima do disponível: recusados.
        assertTrue(servico.retirar(viagem, 2_001, dia) is Resultado.Falha)
        assertTrue(servico.ajustar(viagem, 2_000 + 84_000, dia) is Resultado.Falha)
    }

    @Test
    fun `desfazer nao deixa negativo e uma retirada desfeita precisa de disponivel`() = runTest {
        val a = servico.criar(Objetivo("", "A")).ok()
        val b = servico.criar(Objetivo("", "B")).ok()
        val g1 = servico.guardar(a, 40_000, dia).ok()
        val mover = servico.mover(a, b, 10_000, dia).ok()
        servico.retirar(b, 10_000, dia).ok()
        // Desfazer o mover deixaria B negativo.
        assertTrue(servico.desfazer(mover) is Resultado.Falha)
        // Desfazer o guardar deixaria A (R$ 300,00) negativo.
        assertTrue(servico.desfazer(g1) is Resultado.Falha)
        val r1 = servico.retirar(a, 30_000, dia).ok()
        servico.guardar(b, 100_000, dia).ok() // usa todo o disponível
        assertEquals(0L, visao().resumo.disponivel)
        assertTrue(servico.desfazer(r1) is Resultado.Falha) // guardaria de novo sem disponível
        val g2 = servico.guardar(a, 0 + 1, dia)
        assertTrue(g2 is Resultado.Falha)
        servico.retirar(b, 100_000, dia).ok()
        servico.desfazer(r1).ok()
        assertEquals(40_000L - 10_000, visao().objetivos.guardado(a))
        assertTrue(banco.auditoriaDao().listarTodas().any { it.resumo.contains("desfeito") })
    }

    @Test
    fun `O2 guardado maior que o saldo deixa o disponivel negativo sem reduzir nada`() = runTest {
        val fone = servico.criar(Objetivo("", "Fone")).ok()
        servico.guardar(fone, 30_000, dia).ok()
        val mercado = categorias.observar().first().first { it.nome == "Alimentação" }.id
        movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 90_000, pix, null, mercado, "Compra grande")).ok()
        visao().resumo.let { assertEquals(10_000L, it.dinheiroProprio); assertEquals(30_000L, it.reservado); assertEquals(-20_000L, it.disponivel) }
        assertEquals(30_000L, visao().objetivos.guardado(fone))
        assertTrue(servico.guardar(fone, 1, dia) is Resultado.Falha)
        // Retirar continua possível e corrige o disponível.
        servico.retirar(fone, 30_000, dia).ok()
        assertEquals(10_000L, visao().resumo.disponivel)
    }

    @Test
    fun `O3 concluir e arquivar mantem o guardado e excluir com guardado e recusado`() = runTest {
        val fone = servico.criar(Objetivo("", "Fone", meta = 30_000, prazo = LocalDate.of(2027, 6, 30))).ok()
        servico.guardar(fone, 30_000, dia).ok()
        servico.concluir(fone).ok()
        assertEquals(30_000L, visao().resumo.reservado)
        assertTrue(servico.guardar(fone, 100, dia) is Resultado.Falha)
        servico.arquivar(fone).ok()
        assertEquals(30_000L, visao().resumo.reservado)
        val e = servico.excluir(fone)
        assertTrue(e is Resultado.Falha && e.mensagem.contains("Retire ou mova os R$ 300,00"))
        servico.retirar(fone, 30_000, dia).ok()
        servico.excluir(fone).ok()
        assertTrue(visao().objetivos.objetivos.isEmpty())
        assertEquals(0L, visao().resumo.reservado)
        assertTrue(banco.auditoriaDao().listarTodas().any { it.entidade == Auditor.OBJETIVO && it.resumo.contains("excluído") })
        // Validação do cadastro.
        assertTrue(servico.criar(Objetivo("", " ")) is Resultado.Falha)
        assertTrue(servico.criar(Objetivo("", "X", meta = 0)) is Resultado.Falha)
    }
}
