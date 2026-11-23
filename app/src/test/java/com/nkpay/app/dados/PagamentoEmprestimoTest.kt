package com.nkpay.app.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nkpay.app.dominio.Analises
import com.nkpay.app.dominio.CategoriasDoSistema
import com.nkpay.app.dominio.ClassificacaoDiferenca
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Emprestimos
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.Periodo
import com.nkpay.app.dominio.Saldos
import com.nkpay.app.dominio.StatusObrigacao
import com.nkpay.app.dominio.TipoEmprestimo
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.dominio.VisaoEmprestimos
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

/** Pagar e receber parcelas de empréstimo (regras 6E.4 a 6E.9). Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class PagamentoEmprestimoTest {
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso() // hoje = 01/12/2026
    private lateinit var contas: ContaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var emprestimos: EmprestimoServico
    private lateinit var pagar: PagamentoCreditoServico
    private lateinit var categorias: CategoriaRepositorio
    private lateinit var pix: String
    private val d = LocalDate.of(2026, 12, 10)
    private val ano = Periodo(YearMonth.of(2026, 12), YearMonth.of(2027, 12))

    @Before
    fun abrir() = runTest {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        movs = MovimentacaoRepositorio(banco, auditor, relogio)
        emprestimos = EmprestimoServico(banco, auditor, relogio)
        pagar = PagamentoCreditoServico(banco, auditor, relogio)
        categorias = CategoriaRepositorio(banco, auditor, relogio)
        categorias.garantirCategoriasIniciais()
        pix = (contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 200_000, LocalDate.of(2026, 11, 1))) as Resultado.Sucesso).valor
    }

    @After fun fechar() = banco.close()

    private fun <T> Resultado<T>.ok(): T = (this as Resultado.Sucesso).valor

    private suspend fun registrar(tipo: TipoEmprestimo, n: Int = 10, parcela: Long = 11_000, valor: Long = 100_000): String =
        emprestimos.registrar(NovoEmprestimo(tipo, null, "Pessoa", "Empréstimo", null, valor, d, pix, Emprestimos.parcelasAutomaticas(n, parcela, LocalDate.of(2027, 1, 10)))).ok()

    private suspend fun visao(): VisaoEmprestimos {
        val o = banco.backupDao().obrigacoes().filter { it.excluidoEm == null }.map { it.paraDominio() }
        return VisaoEmprestimos(
            contas.observar().first(), movs.observar().first(), banco.emprestimoDao().observarValidos().first().map { it.paraDominio() },
            o, banco.pagamentoDao().observarValidos().first().map { it.paraDominio() }, banco.ajusteObrigacaoDao().observarValidos().first().map { it.paraDominio() },
        )
    }

    private suspend fun s(id: String) = visao().situacao(id, relogio.hoje())!!
    private suspend fun parcela(id: String, k: Int) = visao().parcelasDe(id)[k - 1].id
    private suspend fun saldoPix() = Saldos.saldosPorConta(contas.observar().first(), movs.observar().first())[pix]
    private suspend fun fecha(id: String) {
        assertTrue("invariante do empréstimo", s(id).invarianteFecha)
        assertTrue("invariante da pessoa", visao().invarianteDaConta(s(id).emprestimo.contaId, relogio.hoje()))
    }

    // ------------------------------------------------------------ recebido

    @Test
    fun `recebido 10 pagamentos de 110 sao so transferencias e zeram a divida`() = runTest {
        val id = registrar(TipoEmprestimo.RECEBIDO)
        for (k in 1..10) {
            pagar.pagarParcela(parcela(id, k), pix, d, 11_000, null).ok()
            fecha(id)
        }
        assertEquals(0L, s(id).saldoNaConta)
        assertEquals(200_000L + 100_000 - 110_000, saldoPix())
        assertEquals(10_000L, Analises.totais(movs.observar().first(), ano).despesas) // só os juros J1
    }

    @Test
    fun `recebido parcial desconto e encargo`() = runTest {
        val id = registrar(TipoEmprestimo.RECEBIDO)
        pagar.pagarParcela(parcela(id, 1), pix, d, 6_000, ClassificacaoDiferenca.PARCIAL).ok()
        assertEquals(5_000L, s(id).parcelas[0].restante)
        fecha(id)
        pagar.pagarParcela(parcela(id, 1), pix, d, 5_000, null).ok()
        assertEquals(StatusObrigacao.QUITADA, s(id).parcelas[0].situacao.status)
        // Desconto: ajuste na conta da pessoa, sem receita.
        pagar.pagarParcela(parcela(id, 2), pix, d, 10_000, ClassificacaoDiferenca.DESCONTO).ok()
        assertEquals(StatusObrigacao.QUITADA, s(id).parcelas[1].situacao.status)
        assertEquals(0L, Analises.totais(movs.observar().first(), ano).entradas)
        fecha(id)
        // A mais: despesa de encargo na conta da pessoa.
        pagar.pagarParcela(parcela(id, 3), pix, d, 11_500, ClassificacaoDiferenca.MULTA).ok()
        assertEquals(10_000L + 500, Analises.totais(movs.observar().first(), ano).despesas)
        fecha(id)
        // Receber em empréstimo recebido é recusado (e pagar em concedido também).
        assertTrue(pagar.receberParcela(parcela(id, 4), pix, d, 11_000, null) is Resultado.Falha)
    }

    // ------------------------------------------------------------ concedido

    @Test
    fun `concedido 10 recebimentos de 110 viram 100 de devolucao e 10 de juros`() = runTest {
        val id = registrar(TipoEmprestimo.CONCEDIDO)
        assertEquals(100_000L, saldoPix())
        for (k in 1..10) {
            pagar.receberParcela(parcela(id, k), pix, d, 11_000, null).ok()
            fecha(id)
        }
        assertEquals(0L, s(id).saldoNaConta)
        assertEquals(10_000L, s(id).jurosRecebidos)
        assertEquals(210_000L, saldoPix())
        val t = Analises.totais(movs.observar().first(), ano)
        assertEquals(10_000L, t.entradas)
        assertEquals(0L, t.despesas)
        val juros = categorias.observar().first().first { it.codigoSistema == CategoriasDoSistema.JUROS_RECEBIDOS }.id
        assertTrue(movs.observar().first().filter { it.tipo == TipoMovimentacao.ENTRADA }.all { it.categoriaId == juros })
        assertTrue(pagar.pagarParcela(parcela(id, 1), pix, d, 11_000, null) is Resultado.Falha)
    }

    @Test
    fun `concedido com centavos fecha os juros exatos e parcial segue a proporcao`() = runTest {
        val id = registrar(TipoEmprestimo.CONCEDIDO, n = 3, parcela = 35_000)
        for (k in 1..3) { pagar.receberParcela(parcela(id, k), pix, d, 35_000, null).ok(); fecha(id) }
        assertEquals(5_000L, s(id).jurosRecebidos)

        val id2 = registrar(TipoEmprestimo.CONCEDIDO)
        pagar.receberParcela(parcela(id2, 1), pix, d, 5_500, ClassificacaoDiferenca.PARCIAL).ok() // 50 + 5
        assertEquals(500L, s(id2).jurosRecebidos)
        assertEquals(95_000L, s(id2).saldoNaConta)
        fecha(id2)
    }

    @Test
    fun `concedido recebi a mais vira juros recebidos e a menos com desconto consome os juros primeiro`() = runTest {
        val id = registrar(TipoEmprestimo.CONCEDIDO)
        pagar.receberParcela(parcela(id, 1), pix, d, 11_500, ClassificacaoDiferenca.MULTA).ok()
        assertEquals(1_000L + 500, s(id).jurosRecebidos)
        assertEquals(90_000L, s(id).saldoNaConta)
        fecha(id)
        // Desconto concedido: recebe 55 de 110 e perdoa 55, que saem dos juros ainda não reconhecidos.
        pagar.receberParcela(parcela(id, 2), pix, d, 5_500, ClassificacaoDiferenca.DESCONTO).ok()
        assertEquals(StatusObrigacao.QUITADA, s(id).parcelas[1].situacao.status)
        assertEquals(85_000L, s(id).saldoNaConta) // só os R$ 50 de devolução reduziram o principal
        assertEquals(0L, Analises.totais(movs.observar().first(), ano).despesas) // sem despesa
        fecha(id)

        // Desconto maior que os juros que restam: o que passar reduz o principal.
        val id2 = registrar(TipoEmprestimo.CONCEDIDO, n = 2, parcela = 52_500)
        pagar.receberParcela(parcela(id2, 1), pix, d, 52_500, null).ok()
        pagar.receberParcela(parcela(id2, 2), pix, d, 40_000, ClassificacaoDiferenca.DESCONTO).ok()
        assertEquals(0L, s(id2).saldoNaConta)
        assertEquals(0L, s(id2).restante)
        fecha(id2)
    }

    @Test
    fun `desfazer em qualquer ordem volta ao estado anterior e nao deixa juros orfaos`() = runTest {
        val id = registrar(TipoEmprestimo.CONCEDIDO)
        val inicial = s(id).saldoNaConta to saldoPix()
        val m1 = pagar.receberParcela(parcela(id, 1), pix, d, 11_000, null).ok()
        val m2 = pagar.receberParcela(parcela(id, 2), pix, d, 11_500, ClassificacaoDiferenca.JUROS).ok()
        val m3 = pagar.receberParcela(parcela(id, 3), pix, d, 5_500, ClassificacaoDiferenca.DESCONTO).ok()
        pagar.desfazer(m2).ok(); fecha(id)
        pagar.desfazer(m1).ok(); fecha(id)
        pagar.desfazer(m3).ok(); fecha(id)
        assertEquals(inicial, s(id).saldoNaConta to saldoPix())
        assertTrue(movs.observar().first().none { it.tipo == TipoMovimentacao.ENTRADA })
        assertTrue(banco.ajusteObrigacaoDao().observarValidos().first().isEmpty())

        val r = registrar(TipoEmprestimo.RECEBIDO)
        val p = pagar.pagarParcela(parcela(r, 1), pix, d, 11_500, ClassificacaoDiferenca.JUROS).ok()
        pagar.desfazer(p).ok()
        assertEquals(110_000L, s(r).saldoNaConta)
        fecha(r)
    }

    @Test
    fun `edicao so sem pagamento e datada no dia da edicao`() = runTest {
        val r = registrar(TipoEmprestimo.RECEBIDO)
        pagar.pagarParcela(parcela(r, 1), pix, d, 11_000, null).ok()
        assertTrue(pagar.editarParcelas(parcela(r, 1), 12_000, null, false) is Resultado.Falha)
        // Aumenta a parcela 2 e as futuras para R$ 120: despesa de juros HOJE (9 × R$ 10).
        assertEquals(9, pagar.editarParcelas(parcela(r, 2), 12_000, null, true).ok())
        val hoje = movs.observar().first().filter { it.tipo == TipoMovimentacao.DESPESA && it.data == relogio.hoje() }
        assertEquals(9_000L, hoje.sumOf { it.valor })
        assertEquals(9 * 12_000L, s(r).restante)
        fecha(r)
        // Diminui a 10 para R$ 100: ajuste de redução, nada de receita.
        pagar.editarParcelas(parcela(r, 10), 10_000, LocalDate.of(2027, 12, 20), false).ok()
        assertEquals(LocalDate.of(2027, 12, 20), visao().parcelasDe(r).last().vencimento)
        fecha(r)

        val c = registrar(TipoEmprestimo.CONCEDIDO)
        pagar.editarParcelas(parcela(c, 1), 12_000, null, false).ok() // +10 de juros previstos
        assertEquals(11_000L, s(c).jurosNaoReconhecidos)
        fecha(c)
        // Não pode ficar abaixo do principal.
        assertTrue(pagar.editarParcelas(parcela(c, 1), 1_000, null, true) is Resultado.Falha)
    }
}
