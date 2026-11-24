package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Objetivo
import com.hkawzz.nkote.dominio.Obrigacao
import com.hkawzz.nkote.dominio.OrigemObrigacao
import com.hkawzz.nkote.dominio.TipoEmprestimo
import com.hkawzz.nkote.dominio.TipoMovimentacao
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

/** v0.8 (D1 e D2, regra 6G.4): exclusões protegidas sempre explicam e levam à tela certa. Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class ProtegidasTest {
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso() // hoje = 01/12/2026
    private lateinit var auditor: Auditor
    private lateinit var contas: ContaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var pix: String
    private lateinit var cartao: String
    private val dia = LocalDate.of(2026, 12, 10)

    @Before
    fun abrir() = runTest {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        movs = MovimentacaoRepositorio(banco, auditor, relogio)
        CategoriaRepositorio(banco, auditor, relogio).garantirCategoriasIniciais()
        pix = contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2026, 11, 1))).ok()
        cartao = contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, LocalDate.of(2026, 11, 1), diaFechamento = 5, diaVencimento = 12)).ok()
    }

    @After fun fechar() = banco.close()

    private fun <T> Resultado<T>.ok(): T = when (this) { is Resultado.Sucesso -> valor; is Resultado.Falha -> error(mensagem) }

    @Test
    fun `cada lancamento protegido diz o motivo e para onde ir`() = runTest {
        val compras = CompraServico(banco, auditor, relogio)
        val compra = compras.registrar(NovaCompra(cartao, "Fone", null, LocalDate.of(2026, 12, 3), 10_000, 1, LocalDate.of(2026, 12, 12))).ok()
        val despesaCompra = banco.compraDao().buscar(compra)!!.movimentacaoId
        assertEquals(DestinoProtegido.Cartao(cartao), movs.protecao(despesaCompra)!!.destino)
        // A exclusão pela aba Movimentações é recusada com a mensagem (nunca fica sem resposta).
        assertTrue(movs.excluir(despesaCompra) is Resultado.Falha)

        val pagamentoFatura = PagamentoCreditoServico(banco, auditor, relogio).pagarFatura(cartao, YearMonth.of(2026, 12), pix, dia, 10_000, null).ok()
        assertEquals(DestinoProtegido.Cartao(cartao), movs.protecao(pagamentoFatura)!!.destino)

        val emp = EmprestimoServico(banco, auditor, relogio).registrar(NovoEmprestimo(TipoEmprestimo.CONCEDIDO, null, "Tonho", "Ajuda", null, 5_000, dia, pix, listOf(dia.plusMonths(1) to 5_000))).ok()
        val contratacao = banco.emprestimoDao().buscar(emp)!!.movimentacaoContratacaoId!!
        assertEquals(DestinoProtegido.Emprestimo(emp), movs.protecao(contratacao)!!.destino)

        val luz = ObrigacaoRepositorio(banco, auditor, relogio).criarAvulsa(Obrigacao("", OrigemObrigacao.AVULSA, "Luz", 1_000, dia, YearMonth.of(2026, 12))).ok()
        PagamentoServico(banco, auditor, relogio).pagar(luz, pix, dia, 1_000, null).ok()
        val despesaLuz = banco.pagamentoDao().validosDa(luz).single().movimentacaoId
        assertEquals(DestinoProtegido.ContaAPagar(luz), movs.protecao(despesaLuz)!!.destino)

        val objetivos = ObjetivoServico(banco, auditor, relogio)
        val viagem = objetivos.criar(Objetivo("", "Viagem")).ok()
        val guardar = objetivos.guardar(viagem, pix, 1_000, dia).ok()
        assertEquals(DestinoProtegido.Caixinha(viagem), movs.protecao(guardar)!!.destino)

        val livre = movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 500, pix, null, null, "Café")).ok()
        assertNull(movs.protecao(livre))
        movs.excluir(livre).ok()
    }

    @Test
    fun `excluir compra paga lista o que trava e desfazer libera`() = runTest {
        val compras = CompraServico(banco, auditor, relogio)
        val compra = compras.registrar(NovaCompra(cartao, "Fone", null, LocalDate.of(2026, 12, 3), 10_000, 1, LocalDate.of(2026, 12, 12))).ok()
        val pagar = PagamentoCreditoServico(banco, auditor, relogio)
        val t = pagar.pagarFatura(cartao, YearMonth.of(2026, 12), pix, dia, 10_000, null).ok()
        val r = compras.excluir(compra)
        assertTrue(r is Resultado.Falha)
        assertTrue((r as Resultado.Falha).mensagem.contains("Pagamento de R$ 100,00 em 10/12/2026 na fatura de 12/2026"))
        val travas = compras.travasDaExclusao(compra)
        assertEquals(listOf(t), travas.map { it.movimentacaoId })
        // Abatimento geral também trava, com a data e a fatura.
        pagar.desfazer(t).ok()
        val parcial = pagar.pagarFatura(cartao, YearMonth.of(2026, 12), pix, dia, 3_000, null).ok()
        assertTrue(compras.travasDaExclusao(compra).single().descricao.startsWith("Abatimento de R$ 30,00 em 10/12/2026"))
        pagar.desfazer(parcial).ok()
        assertTrue(compras.travasDaExclusao(compra).isEmpty())
        compras.excluir(compra).ok()
        assertTrue(banco.compraDao().observarValidas().first().isEmpty())
    }
}
