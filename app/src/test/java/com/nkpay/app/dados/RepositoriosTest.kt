package com.nkpay.app.dados

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nkpay.app.dominio.CategoriasDoSistema
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Movimentacao
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.Saldos
import com.nkpay.app.dominio.TipoBeneficio
import com.nkpay.app.dominio.TipoMovimentacao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Relógio previsível: o tempo anda 1 s a cada chamada e os ids são sequenciais. */
class RelogioFalso : Relogio {
    private var t = 1_796_126_400_000L // 01/12/2026 12:00 UTC (09:00 em Brasília)
    private var n = 0
    override fun agora(): Long = t.also { t += 1_000 }
    override fun hoje(): LocalDate = LocalDate.of(2026, 12, 1)
    override fun novoId(): String = "id-${n++}"
}

/**
 * Testes da camada de dados com um banco SQLite em memória (Robolectric).
 * Todos os dados são fictícios.
 */
@RunWith(AndroidJUnit4::class)
class RepositoriosTest {

    private lateinit var banco: NkPayBanco
    private lateinit var contas: ContaRepositorio
    private lateinit var categorias: CategoriaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var auditoria: AuditoriaRepositorio
    private val dia = LocalDate.of(2026, 12, 1)

    @Before
    fun abrir() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        banco = Room.inMemoryDatabaseBuilder(context, NkPayBanco::class.java).allowMainThreadQueries().build()
        val relogio = RelogioFalso()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        contas = ContaRepositorio(banco, auditor, relogio)
        categorias = CategoriaRepositorio(banco, auditor, relogio)
        movs = MovimentacaoRepositorio(banco, auditor, relogio)
        auditoria = AuditoriaRepositorio(banco)
    }

    @After
    fun fechar() = banco.close()

    private fun <T> Resultado<T>.ok(): T = when (this) {
        is Resultado.Sucesso -> valor
        is Resultado.Falha -> throw AssertionError("Esperava sucesso, veio: $mensagem")
    }

    private suspend fun criarConta(nome: String, saldo: Long, natureza: Natureza = Natureza.PROPRIO, beneficio: TipoBeneficio? = null) =
        contas.criar(Conta("", nome, natureza, beneficio, saldo, dia)).ok()

    private suspend fun registros() = banco.auditoriaDao().listarTodas()

    // --- Auditoria na mesma transação ---

    @Test
    fun `cada criacao edicao e exclusao gera um registro de auditoria`() = runTest {
        val pix = criarConta("Pix", 120_000)
        val conta = contas.buscar(pix)!!
        contas.editar(conta.copy(nome = "Pix Banco")).ok()
        val m = movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 2_000, pix, descricao = "Padaria")).ok()
        movs.excluir(m).ok()

        val ops = registros().reversed().map { it.operacao to it.entidade }
        assertEquals(
            listOf(
                "CRIACAO" to "conta",
                "EDICAO" to "conta",
                "CRIACAO" to "movimentacao",
                "EXCLUSAO" to "movimentacao",
            ),
            ops,
        )
    }

    @Test
    fun `auditoria guarda o antes e o depois em JSON`() = runTest {
        val pix = criarConta("Pix", 120_000)
        contas.editar(contas.buscar(pix)!!.copy(saldoInicial = 130_000)).ok()

        val edicao = registros().first { it.operacao == "EDICAO" }
        val json = Json { ignoreUnknownKeys = true }
        val antes = json.decodeFromString(ContaEntidade.serializer(), edicao.antes!!)
        val depois = json.decodeFromString(ContaEntidade.serializer(), edicao.depois!!)
        assertEquals(120_000, antes.saldoInicialCentavos)
        assertEquals(130_000, depois.saldoInicialCentavos)
        assertNull(registros().first { it.operacao == "CRIACAO" }.antes)
    }

    @Test
    fun `se a transacao falhar nem a alteracao nem a auditoria sao gravadas`() = runTest {
        val pix = criarConta("Pix", 120_000)
        val antesDoErro = registros().size
        try {
            banco.withTransaction {
                banco.contaDao().atualizar(banco.contaDao().buscar(pix)!!.copy(nome = "Alterado"))
                banco.auditoriaDao().inserir(
                    AuditoriaEntidade("x", "g", 0, "EDICAO", "conta", pix, "teste", null, null),
                )
                error("falha simulada no meio da operação")
            }
            fail("deveria ter lançado")
        } catch (_: IllegalStateException) {
        }
        assertEquals("Pix", contas.buscar(pix)!!.nome)
        assertEquals(antesDoErro, registros().size)
    }

    @Test
    fun `edicao sem mudanca nao gera registro`() = runTest {
        val pix = criarConta("Pix", 120_000)
        contas.editar(contas.buscar(pix)!!).ok()
        assertEquals(1, registros().size)
    }

    // --- Exclusão lógica ---

    @Test
    fun `movimentacao excluida some da lista e dos saldos mas fica no banco`() = runTest {
        val pix = criarConta("Pix", 120_000)
        val m = movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 2_000, pix)).ok()
        movs.excluir(m).ok()

        assertTrue(movs.observar().first().isEmpty())
        assertNull(movs.buscar(m))
        val linha = banco.movimentacaoDao().buscar(m)
        assertNotNull(linha)
        assertNotNull(linha!!.excluidoEm)
        val contaPix = contas.buscar(pix)!!
        assertEquals(120_000, Saldos.saldoDaConta(contaPix, movs.observar().first()))
    }

    @Test
    fun `excluir conta com movimentacoes e bloqueado e oferece arquivar`() = runTest {
        val pix = criarConta("Pix", 120_000)
        val din = criarConta("Dinheiro", 30_000)
        movs.criar(Movimentacao("", TipoMovimentacao.TRANSFERENCIA, dia, 4_000, pix, contaDestinoId = din)).ok()

        // A conta de destino também conta como "tem movimentações".
        assertEquals(ContaRepositorio.ResultadoExclusao.TemMovimentacoes(1), contas.excluir(din))
        assertNotNull(contas.buscar(din))

        contas.arquivar(din).ok()
        assertTrue(contas.buscar(din)!!.arquivada)
    }

    @Test
    fun `conta sem movimentacoes pode ser excluida e some da lista`() = runTest {
        val pix = criarConta("Pix", 120_000)
        val m = movs.criar(Movimentacao("", TipoMovimentacao.ENTRADA, dia, 100, pix)).ok()
        movs.excluir(m).ok() // movimentações excluídas não bloqueiam

        assertEquals(ContaRepositorio.ResultadoExclusao.Excluida, contas.excluir(pix))
        assertTrue(contas.observar().first().isEmpty())
        assertNotNull(banco.contaDao().buscar(pix)!!.excluidoEm)
    }

    // --- Regras na gravação ---

    @Test
    fun `transferencia com a mesma conta e recusada e nao gera auditoria`() = runTest {
        val pix = criarConta("Pix", 120_000)
        val r = movs.criar(Movimentacao("", TipoMovimentacao.TRANSFERENCIA, dia, 4_000, pix, contaDestinoId = pix))
        assertTrue(r is Resultado.Falha)
        assertEquals(1, registros().size)
    }

    @Test
    fun `movimentacao em conta excluida e recusada`() = runTest {
        val pix = criarConta("Pix", 120_000)
        contas.excluir(pix)
        val r = movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 100, pix))
        assertTrue(r is Resultado.Falha)
    }

    @Test
    fun `cenario do criterio de aceite com valores ficticios`() = runTest {
        val pix = criarConta("Pix", 120_000)
        val din = criarConta("Dinheiro", 30_000)
        val va = criarConta("VA", 25_000, Natureza.BENEFICIO, TipoBeneficio.VA)
        movs.criar(Movimentacao("", TipoMovimentacao.TRANSFERENCIA, dia, 4_000, pix, contaDestinoId = din)).ok()
        movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 2_500, va)).ok()

        val todasContas = contas.observar().first()
        val todasMovs = movs.observar().first()
        val r = Saldos.resumo(todasContas, todasMovs)
        assertEquals(150_000, r.disponivel)
        assertEquals(22_500, r.totalBeneficios)
        val t = Saldos.totaisDoMes(todasMovs, java.time.YearMonth.of(2026, 12))
        assertEquals(0, t.entradas)
        assertEquals(2_500, t.despesas)
    }

    // --- Categorias ---

    @Test
    fun `categorias iniciais sao criadas uma vez so`() = runTest {
        categorias.garantirCategoriasIniciais()
        categorias.garantirCategoriasIniciais()
        val lista = categorias.observar().first()
        assertEquals(CategoriaRepositorio.SUGESTOES.size + 1, lista.size)
        assertEquals(1, lista.count { it.codigoSistema == CategoriasDoSistema.ENCARGOS_FINANCEIROS })
        // Todas as criações iniciais compartilham o mesmo grupo na auditoria.
        assertEquals(1, registros().map { it.grupoId }.distinct().size)
    }

    @Test
    fun `categoria do sistema nao pode ser arquivada nem renomeada`() = runTest {
        categorias.garantirCategoriasIniciais()
        val encargos = categorias.observar().first().first { it.ehDoSistema }
        assertTrue(categorias.arquivar(encargos.id) is Resultado.Falha)
        assertTrue(categorias.renomear(encargos.id, "Juros") is Resultado.Falha)
    }

    @Test
    fun `categoria arquivada continua nas movimentacoes antigas`() = runTest {
        val pix = criarConta("Pix", 120_000)
        val lazer = categorias.criar("Lazer").ok()
        val m = movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 3_000, pix, categoriaId = lazer)).ok()
        categorias.arquivar(lazer).ok()

        assertEquals(lazer, movs.buscar(m)!!.categoriaId)
        assertTrue(categorias.observar().first().first { it.id == lazer }.arquivada)
    }
}
