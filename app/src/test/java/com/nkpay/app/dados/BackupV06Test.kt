package com.nkpay.app.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nkpay.app.dominio.ClassificacaoDiferenca
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.Saldos
import com.nkpay.app.dominio.VisaoCredito
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
import java.time.YearMonth

/** v0.6: backup com compras com juros, antecipadas, juros pagos e pendências levadas restaura igual. Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class BackupV06Test {
    @get:Rule val pasta = TemporaryFolder()
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso()
    private lateinit var contas: ContaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var backup: BackupServico

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        movs = MovimentacaoRepositorio(banco, auditor, relogio)
        backup = BackupServico(banco, relogio, "teste", pasta.newFolder("copias"), RegistroDeBackupEmMemoria())
    }

    @After fun fechar() = banco.close()

    private suspend fun foto(): List<Any> = banco.backupDao().let {
        listOf(it.contas(), it.categorias(), it.movimentacoes(), it.contasFixas(), it.obrigacoes(), it.pagamentos(), it.ajustesDeObrigacao(), it.compras(), it.pagamentosDeFatura(), it.emprestimos(), it.pendenciasLevadas())
    }

    private suspend fun visao(): VisaoCredito {
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        val o = ObrigacaoRepositorio(banco, auditor, relogio).observar().first()
        return VisaoCredito(
            contas.observar().first(), movs.observar().first(), o.filter { it.ehParcela },
            banco.pagamentoDao().observarValidos().first().map { it.paraDominio() },
            banco.ajusteObrigacaoDao().observarValidos().first().map { it.paraDominio() },
            banco.pagamentoFaturaDao().observarValidos().first().map { it.paraDominio() },
            banco.compraDao().observarValidas().first().map { it.paraDominio() },
        )
    }

    @Test
    fun `roundtrip da v0_6 com juros antecipadas e pendencias levadas`() = runTest {
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        CategoriaRepositorio(banco, auditor, relogio).garantirCategoriasIniciais()
        val compras = CompraServico(banco, auditor, relogio)
        val pagar = PagamentoCreditoServico(banco, auditor, relogio)
        val obrigs = ObrigacaoRepositorio(banco, auditor, relogio)
        val fixas = ContaFixaRepositorio(banco, auditor, relogio)
        fun ok(r: Resultado<String>) = (r as Resultado.Sucesso).valor
        val d = LocalDate.of(2026, 7, 1)
        val pix = ok(contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 500_000, d)))
        val cartao = ok(contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, d, limite = 400_000, diaFechamento = 5, diaVencimento = 12)))
        val loja = ok(contas.criar(Conta("", "Loja", Natureza.CREDIARIO, null, 0, d, diaVencimento = 20)))
        // Compra com juros paga em parte (abatimento geral) e outra paga inteira.
        compras.registrar(NovaCompra(cartao, "Celular", null, LocalDate.of(2026, 11, 3), 30_000, 12, LocalDate.of(2026, 11, 12), valorParcela = 4_550))
        ok(pagar.pagarFatura(cartao, YearMonth.of(2026, 11), pix, LocalDate.of(2026, 11, 12), 4_550, null))
        ok(pagar.pagarFatura(cartao, YearMonth.of(2026, 12), pix, LocalDate.of(2026, 12, 1), 3_000, null))
        // Compra anterior com juros, parcelas pagas, parcial e antecipada com desconto.
        compras.registrar(NovaCompra(loja, "Sofá", null, LocalDate.of(2026, 8, 10), 30_000, 12, LocalDate.of(2026, 9, 20), anteriorAoApp = true,
            parcelasJaPagas = 2, pagoEmParteDaSeguinte = 1_000, valorParcela = 4_550, antecipadas = listOf(Antecipada(12, 4_000))))
        // Pendência levada pelo Preparar mês.
        val f = (fixas.criar(com.nkpay.app.dominio.ContaFixa("", "Aluguel", 100_000, 10)) as Resultado.Sucesso).valor
        val conserto = ok(obrigs.criarAvulsa(com.nkpay.app.dominio.Obrigacao("", com.nkpay.app.dominio.OrigemObrigacao.AVULSA, "Conserto", 25_000, LocalDate.of(2026, 10, 15), YearMonth.of(2026, 10))))
        PrepararMesServico(banco, auditor, relogio).confirmar(YearMonth.of(2026, 12), listOf(FixaEscolhida(f, 100_000)), listOf(conserto))

        val hoje = relogio.hoje()
        val antes = foto()
        val numeros = visao().let { v -> listOf(v.devido(cartao), v.devido(loja), v.jurosAPagar(cartao, hoje), v.jurosAPagar(loja, hoje)) }
        assertTrue(visao().invarianteFecha(cartao, hoje))
        assertTrue(visao().invarianteFecha(loja, hoje))
        assertEquals(1, banco.backupDao().pendenciasLevadas().size)

        val arquivo = ByteArrayOutputStream().also { backup.exportar(it) }.toByteArray()
        contas.criar(Conta("", "Depois", Natureza.PROPRIO, null, 1, d))
        backup.restaurar(backup.ler(ByteArrayInputStream(arquivo)))

        assertEquals(antes, foto())
        assertEquals(numeros, visao().let { v -> listOf(v.devido(cartao), v.devido(loja), v.jurosAPagar(cartao, hoje), v.jurosAPagar(loja, hoje)) })
        assertTrue(visao().invarianteFecha(cartao, hoje))
        assertTrue(visao().invarianteFecha(loja, hoje))
        assertTrue(banco.backupDao().compras().any { it.valorOriginalCentavos == 30_000L })
        // Um backup da v0.6 tem versão 5: a v0.5 (versão 4) recusa versões maiores que a dela.
        assertEquals(NkPayBanco.VERSAO, backup.ler(ByteArrayInputStream(arquivo)).manifesto.versaoBanco) // v0.7: a versão atual é a 6
    }
}