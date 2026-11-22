package com.nkpay.app.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Movimentacao
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.Saldos
import com.nkpay.app.dominio.TipoBeneficio
import com.nkpay.app.dominio.TipoMovimentacao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Testes do backup e da restauração, com dados fictícios. */
@RunWith(AndroidJUnit4::class)
class BackupTest {

    @get:Rule val pasta = TemporaryFolder()

    private lateinit var banco: NkPayBanco
    private lateinit var contas: ContaRepositorio
    private lateinit var categorias: CategoriaRepositorio
    private lateinit var movs: MovimentacaoRepositorio
    private lateinit var backup: BackupServico
    private val registro = RegistroDeBackupEmMemoria()
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
        backup = BackupServico(banco, relogio, "teste", pasta.newFolder("copias"), registro)
    }

    @After
    fun fechar() = banco.close()

    private fun <T> Resultado<T>.ok(): T = (this as Resultado.Sucesso).valor

    /** Cenário fictício: 3 contas, categorias, 3 movimentações (uma excluída). */
    private suspend fun montarCenario(): Map<String, String> {
        categorias.garantirCategoriasIniciais()
        val pix = contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 120_000, dia)).ok()
        val din = contas.criar(Conta("", "Dinheiro", Natureza.PROPRIO, null, 30_000, dia)).ok()
        val va = contas.criar(Conta("", "VA", Natureza.BENEFICIO, TipoBeneficio.VA, 25_000, dia)).ok()
        val cat = categorias.observar().first().first { it.nome == "Alimentação" }.id
        movs.criar(Movimentacao("", TipoMovimentacao.TRANSFERENCIA, dia, 4_000, pix, contaDestinoId = din)).ok()
        movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, dia, 2_500, va, categoriaId = cat, descricao = "Mercado")).ok()
        val apagada = movs.criar(Movimentacao("", TipoMovimentacao.ENTRADA, dia, 999, pix)).ok()
        movs.excluir(apagada).ok()
        return mapOf("pix" to pix, "din" to din, "va" to va)
    }

    private suspend fun exportarBytes(): ByteArray = ByteArrayOutputStream().also { backup.exportar(it) }.toByteArray()

    private suspend fun fotoDoBanco(): List<Any> {
        val d = banco.backupDao()
        return listOf(d.contas(), d.categorias(), d.movimentacoes(), d.auditoria())
    }

    @Test
    fun `exportar e restaurar devolve exatamente os mesmos dados`() = runTest {
        montarCenario()
        val antes = fotoDoBanco()
        val arquivo = exportarBytes()

        // Bagunça o banco depois do backup.
        contas.criar(Conta("", "Conta nova", Natureza.PROPRIO, null, 1, dia)).ok()

        backup.restaurar(backup.ler(ByteArrayInputStream(arquivo)))

        val d = banco.backupDao()
        assertEquals(antes[0], d.contas())
        assertEquals(antes[1], d.categorias())
        assertEquals(antes[2], d.movimentacoes())
        // A auditoria é a do backup + a linha da restauração.
        val auditoria = d.auditoria()
        assertEquals((antes[3] as List<*>).size + 1, auditoria.size)
        assertEquals("RESTAURACAO", auditoria.last().operacao)
    }

    @Test
    fun `restaurar substitui tudo em vez de misturar`() = runTest {
        montarCenario()
        val arquivo = exportarBytes()
        contas.criar(Conta("", "Só depois do backup", Natureza.PROPRIO, null, 1, dia)).ok()

        backup.restaurar(backup.ler(ByteArrayInputStream(arquivo)))

        assertTrue(contas.observar().first().none { it.nome == "Só depois do backup" })
    }

    @Test
    fun `saldos e excluidos logicos sobrevivem ao backup`() = runTest {
        val ids = montarCenario()
        val arquivo = exportarBytes()
        backup.restaurar(backup.ler(ByteArrayInputStream(arquivo)))

        val r = Saldos.resumo(contas.observar().first(), movs.observar().first())
        assertEquals(150_000, r.disponivel)
        assertEquals(22_500, r.totalBeneficios)
        // A entrada excluída continua no banco, marcada como excluída.
        assertEquals(1, banco.backupDao().movimentacoes().count { it.excluidoEm != null })
        assertEquals(116_000L, Saldos.saldosPorConta(contas.observar().first(), movs.observar().first())[ids["pix"]])
    }

    @Test
    fun `antes de restaurar guarda uma copia automatica que tambem pode ser restaurada`() = runTest {
        montarCenario()
        val antes = fotoDoBanco()
        val arquivoVazio = run {
            // Backup de um app "vazio" (outro banco).
            val outro = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
            val s = BackupServico(outro, RelogioFalso(), "teste", pasta.newFolder("outro"), RegistroDeBackupEmMemoria())
            ByteArrayOutputStream().also { s.exportar(it) }.toByteArray().also { outro.close() }
        }

        val copia = backup.restaurar(backup.ler(ByteArrayInputStream(arquivoVazio)))
        assertTrue(contas.observar().first().isEmpty())
        assertTrue(copia.exists())
        assertEquals(listOf(copia), backup.copiasAutomaticas())

        // Desfaz: restaura a cópia automática.
        backup.restaurar(copia.inputStream().use { backup.ler(it) })
        assertEquals(antes[0], banco.backupDao().contas())
        assertEquals(antes[2], banco.backupDao().movimentacoes())
    }

    @Test
    fun `exportar marca a data do ultimo backup`() = runTest {
        montarCenario()
        assertEquals(null, registro.ultimo.value)
        exportarBytes()
        assertNotNull(registro.ultimo.value)
    }

    @Test
    fun `manifesto traz as contagens`() = runTest {
        montarCenario()
        val m = backup.ler(ByteArrayInputStream(exportarBytes())).manifesto
        assertEquals(3, m.contas)
        assertEquals(3, m.movimentacoes)
        assertEquals(CategoriaRepositorio.SUGESTOES.size + 1, m.categorias)
    }

    // --- Arquivos ruins são recusados ANTES de mexer em qualquer coisa ---

    private fun esperarRecusa(bytes: ByteArray, trecho: String) {
        try {
            backup.ler(ByteArrayInputStream(bytes))
            fail("deveria recusar")
        } catch (e: BackupServico.BackupInvalido) {
            assertTrue("mensagem: ${e.message}", e.message!!.contains(trecho))
        }
    }

    private fun zipCom(vararg arquivos: Pair<String, ByteArray>): ByteArray {
        val saida = ByteArrayOutputStream()
        ZipOutputStream(saida).use { zip ->
            for ((nome, bytes) in arquivos) {
                zip.putNextEntry(ZipEntry(nome)); zip.write(bytes); zip.closeEntry()
            }
        }
        return saida.toByteArray()
    }

    private fun conteudo(bytes: ByteArray): Map<String, ByteArray> {
        val mapa = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) { val e = zip.nextEntry ?: break; mapa[e.name] = zip.readBytes() }
        }
        return mapa
    }

    @Test
    fun `arquivo que nao e backup e recusado`() {
        esperarRecusa("isto não é um zip".toByteArray(), "não é um backup")
        esperarRecusa(zipCom("outra-coisa.txt" to "oi".toByteArray()), "não é um backup")
    }

    @Test
    fun `dados alterados sao detectados pelo SHA-256`() = runTest {
        montarCenario()
        val partes = conteudo(exportarBytes())
        val adulterado = partes.getValue("dados.json").toString(Charsets.UTF_8).replace("120000", "999999")
        esperarRecusa(zipCom("manifest.json" to partes.getValue("manifest.json"), "dados.json" to adulterado.toByteArray()), "corrompido")
    }

    @Test
    fun `backup de versao mais nova do banco e recusado`() = runTest {
        montarCenario()
        val partes = conteudo(exportarBytes())
        val manifesto = partes.getValue("manifest.json").toString(Charsets.UTF_8)
            .replace("\"versaoBanco\":${NkPayBanco.VERSAO}", "\"versaoBanco\":${NkPayBanco.VERSAO + 1}")
        esperarRecusa(zipCom("manifest.json" to manifesto.toByteArray(), "dados.json" to partes.getValue("dados.json")), "versão mais nova")
    }

    @Test
    fun `backup feito na v0_2 (banco versao 1) continua restauravel na v0_3`() = runTest {
        montarCenario()
        val antes = fotoDoBanco()
        val partes = conteudo(exportarBytes())

        // Recria o arquivo como a v0.2 gerava: sem as listas novas e com versaoBanco = 1.
        val json = kotlinx.serialization.json.Json
        val dados = json.parseToJsonElement(partes.getValue("dados.json").toString(Charsets.UTF_8)) as kotlinx.serialization.json.JsonObject
        val dadosV1 = kotlinx.serialization.json.JsonObject(dados.filterKeys { it in setOf("contas", "categorias", "movimentacoes", "auditoria") })
        val bytesV1 = dadosV1.toString().toByteArray(Charsets.UTF_8)
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytesV1).joinToString("") { "%02x".format(it) }
        val manifesto = json.parseToJsonElement(partes.getValue("manifest.json").toString(Charsets.UTF_8)) as kotlinx.serialization.json.JsonObject
        val manifestoV1 = kotlinx.serialization.json.JsonObject(
            manifesto.filterKeys { it != "contasFixas" && it != "obrigacoes" } +
                mapOf(
                    "versaoBanco" to kotlinx.serialization.json.JsonPrimitive(1),
                    "sha256Dados" to kotlinx.serialization.json.JsonPrimitive(sha),
                ),
        )
        val arquivoV1 = zipCom("manifest.json" to manifestoV1.toString().toByteArray(), "dados.json" to bytesV1)

        contas.criar(Conta("", "Depois do backup", Natureza.PROPRIO, null, 1, dia)).ok()
        val lido = backup.ler(ByteArrayInputStream(arquivoV1))
        assertEquals(1, lido.manifesto.versaoBanco)
        backup.restaurar(lido)

        assertEquals(antes[0], banco.backupDao().contas())
        assertEquals(antes[2], banco.backupDao().movimentacoes())
        assertTrue(banco.backupDao().obrigacoes().isEmpty())
    }

    @Test
    fun `arquivo recusado nao altera nada`() = runTest {
        montarCenario()
        val antes = fotoDoBanco()
        runCatching { backup.ler(ByteArrayInputStream("lixo".toByteArray())) }
        assertEquals(antes, fotoDoBanco())
    }
}
