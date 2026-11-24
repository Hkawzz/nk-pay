package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.ClassificacaoDiferenca
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.VisaoCredito
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

/**
 * Matriz de backup da v0.4 (proposta, seção 5): um backup com TODOS os tipos de registro
 * de crédito (compras, compra anterior, parcelas, abatimentos gerais, encargo, desconto)
 * restaura igual, campo a campo, e as contas continuam fechando. Valores fictícios.
 */
@RunWith(AndroidJUnit4::class)
class BackupCreditoTest {
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
        listOf(it.contas(), it.categorias(), it.movimentacoes(), it.contasFixas(), it.obrigacoes(), it.pagamentos(), it.ajustesDeObrigacao(), it.compras(), it.pagamentosDeFatura())
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
    fun `backup com cartao crediario compras e pagamentos restaura igual e as contas fecham`() = runTest {
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        CategoriaRepositorio(banco, auditor, relogio).garantirCategoriasIniciais()
        val compras = CompraServico(banco, auditor, relogio)
        val pagar = PagamentoCreditoServico(banco, auditor, relogio)
        fun ok(r: Resultado<String>) = (r as Resultado.Sucesso).valor
        val d = LocalDate.of(2026, 10, 1)
        val pix = ok(contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 500_000, d)))
        val cartao = ok(contas.criar(Conta("", "Cartão", Natureza.CARTAO, null, 0, d, limite = 400_000, diaFechamento = 5, diaVencimento = 12)))
        val loja = ok(contas.criar(Conta("", "Loja", Natureza.CREDIARIO, null, 0, d, diaVencimento = 20)))
        compras.registrar(NovaCompra(cartao, "Geladeira", null, LocalDate.of(2026, 8, 10), 120_000, 12, LocalDate.of(2026, 8, 12), anteriorAoApp = true, parcelasJaPagas = 4, pagoEmParteDaSeguinte = 1_000))
        compras.registrar(NovaCompra(cartao, "Fone", null, LocalDate.of(2026, 12, 3), 30_000, 3, LocalDate.of(2026, 12, 12)))
        val sofa = ok(compras.registrar(NovaCompra(loja, "Sofá", null, LocalDate.of(2026, 11, 20), 30_000, 2, LocalDate.of(2026, 12, 20))))
        // Abatimento geral, encargo e desconto.
        // Dezembro: R$ 90 (parcela 5 da geladeira, já com R$ 10 pagos antes) + R$ 100 do fone = R$ 190.
        ok(pagar.pagarFatura(cartao, YearMonth.of(2026, 12), pix, LocalDate.of(2026, 12, 1), 5_000, null))
        // Restam R$ 140 em dezembro + R$ 200 em janeiro; paga R$ 345 (R$ 5 de juros).
        ok(pagar.pagarFatura(cartao, YearMonth.of(2027, 1), pix, LocalDate.of(2026, 12, 12), 34_500, ClassificacaoDiferenca.JUROS))
        val p1 = visao().parcelasDaCompra(sofa).first()
        ok(pagar.pagarParcela(p1.id, pix, LocalDate.of(2026, 12, 10), 14_000, ClassificacaoDiferenca.DESCONTO))

        val hoje = relogio.hoje()
        val antes = foto()
        val devidos = visao().let { it.devido(cartao) to it.devido(loja) }
        val disponivel = Saldos.resumo(contas.observar().first(), movs.observar().first()).disponivel
        assertTrue(visao().invarianteFecha(cartao, hoje))
        assertTrue(visao().invarianteFecha(loja, hoje))

        val arquivo = ByteArrayOutputStream().also { backup.exportar(it) }.toByteArray()
        // Bagunça depois do backup.
        contas.criar(Conta("", "Depois", Natureza.PROPRIO, null, 1, d))
        backup.restaurar(backup.ler(ByteArrayInputStream(arquivo)))

        assertEquals(antes, foto())
        assertEquals(devidos, visao().let { it.devido(cartao) to it.devido(loja) })
        assertEquals(disponivel, Saldos.resumo(contas.observar().first(), movs.observar().first()).disponivel)
        assertTrue(visao().invarianteFecha(cartao, hoje))
        assertTrue(visao().invarianteFecha(loja, hoje))
        val m = backup.ler(ByteArrayInputStream(arquivo)).manifesto
        assertEquals(NkPayBanco.VERSAO, m.versaoBanco)
        assertEquals(3, m.compras)
    }
}
