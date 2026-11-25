package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Objetivo
import com.hkawzz.nkote.dominio.PartesCaixinha
import com.hkawzz.nkote.dominio.SaldoDeParte
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.dominio.VisaoObjetivos
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * v0.9 (item 7, ADR 0036): caixinhas com partes por origem. Exemplo da regra:
 * guardar R$ 100,00 do Dinheiro + R$ 50,00 do Banco; mover R$ 100,00 Dinheiro → Banco;
 * retirar R$ 30,00 do Banco. Valores fictícios.
 */
@RunWith(AndroidJUnit4::class)
class PartesCaixinhaTest {
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso()
    private lateinit var contas: ContaRepositorio
    private lateinit var servico: ObjetivoServico
    private lateinit var dinheiro: String
    private lateinit var bancoX: String
    private val dia = LocalDate.of(2026, 12, 1)

    @Before
    fun abrir() = runBlocking {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        CategoriaRepositorio(banco, auditor, relogio).garantirCategoriasIniciais()
        servico = ObjetivoServico(banco, auditor, relogio)
        dinheiro = ok(contas.criar(Conta("", "Dinheiro", Natureza.PROPRIO, null, 50_000, LocalDate.of(2026, 11, 1), icone = "dinheiro")))
        bancoX = ok(contas.criar(Conta("", "Banco X", Natureza.PROPRIO, null, 50_000, LocalDate.of(2026, 11, 1))))
    }

    @After fun fechar() = banco.close()

    private fun <T> ok(r: Resultado<T>): T = when (r) { is Resultado.Sucesso -> r.valor; is Resultado.Falha -> error(r.mensagem) }

    private fun visao(): VisaoObjetivos = runBlocking {
        val cs = banco.backupDao().contas().filter { it.excluidoEm == null }.map { it.paraDominio() }
        val ms = banco.backupDao().movimentacoes().filter { it.excluidoEm == null }.map { it.paraDominio() }
        VisaoObjetivos(banco.objetivoDao().validos().map { it.paraDominio() }, emptyList(), cs, ms, banco.parteCaixinhaDao().validas().map { it.paraDominio() })
    }

    private fun saldoConta(id: String) = visao().let { v -> Saldos.saldosPorConta(v.contas, v.movimentacoes)[id] }

    @Test
    fun `exemplo da regra - guardar de duas contas, mover entre partes e retirar parcial`() = runBlocking {
        val fone = ok(servico.criar(Objetivo("", "Fone")))
        ok(servico.guardar(fone, dinheiro, 10_000, dia))
        ok(servico.guardar(fone, bancoX, 5_000, dia))
        assertEquals(listOf(SaldoDeParte(dinheiro, 10_000), SaldoDeParte(bancoX, 5_000)), visao().partesDe(fone))
        assertEquals(15_000L, visao().guardado(fone))

        // Mover entre partes: o total e as contas não mudam; nenhuma movimentação nova.
        val movsAntes = banco.backupDao().movimentacoes().size
        val grupo = ok(servico.moverEntrePartes(fone, dinheiro, bancoX, 10_000, dia))
        assertEquals(movsAntes, banco.backupDao().movimentacoes().size)
        assertEquals(listOf(SaldoDeParte(bancoX, 15_000)), visao().partesDe(fone))
        assertEquals(15_000L, visao().guardado(fone))
        assertEquals(40_000L, saldoConta(dinheiro))
        assertEquals(45_000L, saldoConta(bancoX))

        // Retirar R$ 30,00 do Banco: parcial, limitado ao saldo da parte.
        assertTrue(servico.retirar(fone, bancoX, 20_000, dia, bancoX) is Resultado.Falha) // passa do total
        assertTrue(servico.retirar(fone, dinheiro, 1_000, dia, dinheiro) is Resultado.Falha) // a parte Dinheiro está zerada
        ok(servico.retirar(fone, bancoX, 3_000, dia, bancoX))
        assertEquals(listOf(SaldoDeParte(bancoX, 12_000)), visao().partesDe(fone))
        assertEquals(12_000L, visao().guardado(fone))
        assertEquals(48_000L, saldoConta(bancoX))

        // O histórico mostra o movimento entre partes, e ele pode ser desfeito (Dinheiro 100 e Banco 20 = 120).
        val s = visao().situacao(fone)!!
        assertTrue(s.movimentos.any { it.tipo == com.hkawzz.nkote.dominio.TipoMovimentoCaixinha.MOVER_PARTE && it.partes == listOf(dinheiro, bancoX) })
        ok(servico.desfazerEntrePartes(grupo))
        assertEquals(listOf(SaldoDeParte(dinheiro, 10_000), SaldoDeParte(bancoX, 2_000)), visao().partesDe(fone))
        // Agora retirar tudo do Banco e tentar desfazer o guardar do Banco: a parte ficaria negativa.
        ok(servico.retirar(fone, bancoX, 2_000, dia, bancoX))
        val guardarBanco = banco.backupDao().movimentacoes().first { it.contaId == bancoX && it.descricao.startsWith("Guardar") }.id
        assertTrue(servico.desfazer(guardarBanco) is Resultado.Falha)
        assertTrue(banco.auditoriaDao().listarTodas().any { it.entidade == Auditor.PARTE_CAIXINHA })
    }

    @Test
    fun `rendimento vai para a parte de banco com mais saldo e retirada sem escolha comeca pela conta de destino`() = runBlocking {
        val viagem = ok(servico.criar(Objetivo("", "Viagem")))
        ok(servico.guardar(viagem, dinheiro, 20_000, dia))
        ok(servico.guardar(viagem, bancoX, 5_000, dia))
        ok(servico.rendimento(viagem, 300, dia)) // padrão: a parte de banco (o Dinheiro tem mais, mas é físico)
        assertEquals(listOf(SaldoDeParte(dinheiro, 20_000), SaldoDeParte(bancoX, 5_300)), visao().partesDe(viagem))
        ok(servico.rendimento(viagem, 200, dia, ObjetivoServico.SEM_ORIGEM))
        assertEquals(SaldoDeParte(null, 200), visao().partesDe(viagem).last())
        // Sem escolha: sai primeiro da parte da conta de destino (Banco 53,00), o resto da mais antiga (Dinheiro).
        ok(servico.retirar(viagem, bancoX, 6_000, dia))
        assertEquals(listOf(SaldoDeParte(dinheiro, 19_300), SaldoDeParte(null, 200)), visao().partesDe(viagem))
        // A soma das partes é sempre o total.
        assertEquals(visao().guardado(viagem), visao().partesDe(viagem).sumOf { it.valor })
        // Mover entre caixinhas leva a parte junto.
        val outro = ok(servico.criar(Objetivo("", "Outro")))
        ok(servico.mover(viagem, outro, 1_000, dia, dinheiro))
        assertEquals(listOf(SaldoDeParte(dinheiro, 1_000)), visao().partesDe(outro))
        // Desfazer a retirada devolve as partes.
        val ret = banco.backupDao().movimentacoes().last { it.descricao.startsWith("Retirar") && it.excluidoEm == null }.id
        ok(servico.desfazer(ret))
        assertEquals(visao().guardado(viagem), visao().partesDe(viagem).sumOf { it.valor })
        assertEquals(5_300L, visao().partesDe(viagem).first { it.contaId == bancoX }.valor)
    }

    @Test
    fun `recalculo pelo historico - guardar, retirar pela conta de destino, mover e rendimento sem origem`() {
        val d = LocalDate.of(2026, 11, 1)
        var n = 0L
        fun mov(tipo: TipoMovimentacao, valor: Long, de: String, para: String?) = PartesCaixinha.Mov("m${++n}", tipo, d.plusDays(n), valor, de, para, n)
        val movs = listOf(
            mov(TipoMovimentacao.TRANSFERENCIA, 10_000, "din", "cx1"), // guardar 100 do Dinheiro
            mov(TipoMovimentacao.TRANSFERENCIA, 5_000, "ban", "cx1"),  // guardar 50 do Banco
            mov(TipoMovimentacao.ENTRADA, 400, "cx1", null),          // rendimento → sem origem
            mov(TipoMovimentacao.TRANSFERENCIA, 7_000, "cx1", "ban"),  // retirada para o Banco: 50 do Banco + 20 do Dinheiro
            mov(TipoMovimentacao.TRANSFERENCIA, 8_400, "cx1", "cx2"),  // mover tudo: 80 do Dinheiro + 4 sem origem
        )
        val linhas = PartesCaixinha.recalcular(setOf("cx1", "cx2"), movs) { m, i -> "${m.id}-$i" }
        val ids = movs.map { it.id }.toSet()
        assertEquals(emptyList<SaldoDeParte>(), PartesCaixinha.saldos("cx1", 0, linhas, ids))
        assertEquals(listOf(SaldoDeParte("din", 8_000), SaldoDeParte(null, 400)), PartesCaixinha.saldos("cx2", 8_400, linhas, ids))
    }
}
