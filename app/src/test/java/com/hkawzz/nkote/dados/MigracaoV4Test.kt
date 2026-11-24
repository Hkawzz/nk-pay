package com.hkawzz.nkote.dados

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.CategoriasDoSistema
import com.hkawzz.nkote.dominio.Saldos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Migração 3 → 4 (v0.5, ADR 0020) e a matriz de backup do briefing: banco v3 FIEL
 * (SQL oficial do esquema 3) com dados fictícios em todas as tabelas da v0.4.
 */
@RunWith(AndroidJUnit4::class)
class MigracaoV4Test {
    @get:Rule val pasta = TemporaryFolder()
    private val nome = "migracao-v4-teste.db"
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val arquivo get() = context.getDatabasePath(nome)
    private val tabelasV3 = listOf("conta", "categoria", "movimentacao", "auditoria", "conta_fixa", "obrigacao", "pagamento", "obrigacao_ajuste", "compra", "pagamento_fatura")
    private val colunasObrigacaoV3 = "id, origem, descricao, valorPrevistoCentavos, vencimento, mesReferencia, contaFixaId, categoriaId, contaCreditoId, compraId, numeroParcela, totalParcelas, canceladaEm, criadoEm, atualizadoEm, excluidoEm"

    @Before fun limpar() { context.deleteDatabase(nome) }
    @After fun apagar() { context.deleteDatabase(nome) }

    private fun esquema(v: Int): JsonObject =
        Json.parseToJsonElement(File("schemas/com.hkawzz.nkote.dados.NkPayBanco/$v.json").readText()).jsonObject.getValue("database").jsonObject

    private fun criarBancoV3() {
        val e = esquema(3)
        arquivo.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(arquivo, null).use { db ->
            for (ent in e.getValue("entities").jsonArray) {
                val o = ent.jsonObject
                val t = o.getValue("tableName").jsonPrimitive.content
                db.execSQL(o.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", t))
                for (i in (o["indices"] as? JsonArray).orEmpty()) db.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", t))
            }
            for (q in e.getValue("setupQueries").jsonArray) db.execSQL(q.jsonPrimitive.content)
            preencher(db)
            db.version = 3
        }
    }

    /** Dados fictícios em TODAS as tabelas da v0.4, inclusive cartão, compra e abatimento geral. */
    private fun preencher(db: SQLiteDatabase) {
        fun conta(id: String, nome: String, natureza: String, ben: String?, saldo: Long, limite: Long? = null, fech: Int? = null, venc: Int? = null) = db.execSQL(
            "INSERT INTO conta VALUES (?, ?, ?, ?, ?, '2026-11-01', 'carteira', 4289825786, NULL, ?, ?, ?, NULL, 1, 1, NULL)",
            arrayOf<Any?>(id, nome, natureza, ben, saldo, limite, fech, venc),
        )
        conta("pix", "Pix", "PROPRIO", null, 90_000)
        conta("va", "VA", "BENEFICIO", "VA", 31_000)
        conta("cart", "Cartão", "CARTAO", null, 0, 300_000, 5, 12)
        db.execSQL("INSERT INTO categoria VALUES ('ali', 'Alimentação', NULL, NULL, 1, 1, NULL, 'carrinho', 4283163008)")
        db.execSQL("INSERT INTO categoria VALUES ('enc', 'Encargos financeiros', 'ENCARGOS_FINANCEIROS', NULL, 1, 1, NULL, 'crescimento', 4294472049)")
        fun mov(id: String, tipo: String, data: String, valor: Long, conta: String, destino: String?, cat: String?, excl: Long? = null) = db.execSQL(
            "INSERT INTO movimentacao VALUES (?, ?, ?, ?, ?, ?, ?, 'teste', 2, 2, ?)", arrayOf<Any?>(id, tipo, data, valor, conta, destino, cat, excl),
        )
        mov("m1", "ENTRADA", "2026-11-23", 250_000, "pix", null, null)
        mov("m2", "DESPESA", "2026-11-23", 7_300, "va", null, "ali")
        mov("mc", "DESPESA", "2026-11-25", 60_000, "cart", null, "ali")
        mov("mt", "TRANSFERENCIA", "2026-12-05", 10_000, "pix", "cart", null)
        mov("mx", "DESPESA", "2026-11-24", 55_555, "pix", null, "ali", excl = 9)
        db.execSQL("INSERT INTO compra VALUES ('c1', 'cart', 'Fone', 'ali', '2026-11-25', 60000, 3, 'mc', 0, 1, 1, NULL)")
        for (n in 1..3) db.execSQL(
            "INSERT INTO obrigacao VALUES (?, 'PARCELA', ?, 20000, ?, ?, NULL, 'ali', 'cart', 'c1', ?, 3, NULL, 1, 1, NULL)",
            arrayOf<Any?>("p$n", "Fone ($n/3)", "2026-%02d-12".format(11 + n).replace("2026-13", "2027-01").replace("2026-14", "2027-02"), "2026-%02d".format(11 + n).replace("2026-13", "2027-01").replace("2026-14", "2027-02"), n),
        )
        db.execSQL("INSERT INTO pagamento_fatura VALUES ('g1', 'cart', '2026-12', 'mt', 10000, 1, 1, NULL)")
        db.execSQL("INSERT INTO auditoria VALUES ('h1', 'g1', 10, 'CRIACAO', 'conta', 'pix', 'Conta “Pix” criada', NULL, '{}')")
    }

    private fun foto(consulta: (String) -> Cursor, sql: String): List<String> {
        val linhas = mutableListOf<String>()
        consulta(sql).use { c -> while (c.moveToNext()) linhas += (0 until c.columnCount).filter { c.getColumnName(it) !in COLUNAS_NOVAS }.joinToString("|") { i -> "${c.getColumnName(i)}=${if (c.isNull(i)) "NULL" else c.getString(i)}" } }
        return linhas
    }

    private fun consultaDe(t: String) = if (t == "obrigacao") "SELECT $colunasObrigacaoV3 FROM obrigacao ORDER BY id" else "SELECT * FROM $t ORDER BY id"

    private fun abrirComoOApp(): NkPayBanco = Room.databaseBuilder(context, NkPayBanco::class.java, nome).addMigrations(*MIGRACOES).allowMainThreadQueries().build()

    @Test
    fun `migrar de 3 para 4 preserva todas as linhas campo a campo`() {
        criarBancoV3()
        val antes = SQLiteDatabase.openDatabase(arquivo.path, null, SQLiteDatabase.OPEN_READONLY).use { db -> tabelasV3.associateWith { t -> foto({ db.rawQuery(it, null) }, consultaDe(t)) } }
        val banco = abrirComoOApp()
        try {
            val db = banco.openHelper.writableDatabase
            assertEquals(NkPayBanco.VERSAO, db.version) // v0.6: o caminho 3 → 4 → 5 termina na versão atual (ADR 0024)
            // v0.6: a compra ganhou valorOriginalCentavos no fim, que precisa nascer vazia.
            for (t in tabelasV3) assertEquals("tabela $t mudou", antes.getValue(t), foto({ db.query(it) }, consultaDe(t)).map { it.removeSuffix("|valorOriginalCentavos=NULL") })
            db.query("SELECT COUNT(*) FROM obrigacao WHERE emprestimoId IS NOT NULL").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            db.query("SELECT COUNT(*) FROM emprestimo").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            val o = esquema(4).getValue("entities").jsonArray.map { it.jsonObject }.first { it.getValue("tableName").jsonPrimitive.content == "emprestimo" }
            val sql = o.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", "emprestimo").replace("CREATE TABLE IF NOT EXISTS", "CREATE TABLE")
            db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'emprestimo'").use { c -> assertTrue(c.moveToFirst()); assertEquals(sql, c.getString(0)) }
        } finally {
            banco.close()
        }
    }

    @Test
    fun `saldos e devido iguais e abrir de novo nao migra outra vez`() {
        criarBancoV3()
        val banco = abrirComoOApp()
        try {
            val r = RelogioFalso()
            val a = Auditor(banco.auditoriaDao(), r)
            val contas = runBlocking { ContaRepositorio(banco, a, r).observar().first() }
            val movs = runBlocking { MovimentacaoRepositorio(banco, a, r).observar().first() }
            val s = Saldos.saldosPorConta(contas, movs)
            assertEquals(90_000L + 250_000 - 10_000, s["pix"])
            assertEquals(31_000L - 7_300, s["va"])
            assertEquals(-50_000L, s["cart"])
            assertEquals(5, runBlocking { banco.backupDao().movimentacoes() }.size)
        } finally {
            banco.close()
        }
        val deNovo = abrirComoOApp()
        try { assertEquals(3, runBlocking { deNovo.backupDao().obrigacoes() }.size) } finally { deNovo.close() }
    }

    @Test
    fun `copia do arquivo antes de migrar da v3 para a v4`() {
        criarBancoV3()
        val copia = copiarAntesDeMigrar(arquivo, pasta.newFolder("c"), NkPayBanco.VERSAO, 1000)
        assertNotNull(copia)
        assertTrue(copia!!.name.startsWith("banco-v3-antes-da-v${NkPayBanco.VERSAO}")) // v0.6: a versão atual é a 5
    }

    // ------------------------------------------------------------ matriz de backup

    private fun zip(vararg a: Pair<String, ByteArray>): ByteArray {
        val s = ByteArrayOutputStream()
        ZipOutputStream(s).use { z -> for ((n, b) in a) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return s.toByteArray()
    }

    private fun partes(b: ByteArray): Map<String, ByteArray> {
        val m = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(b)).use { z -> while (true) { val e = z.nextEntry ?: break; m[e.name] = z.readBytes() } }
        return m
    }

    private fun sha(b: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test
    fun `backup da v0_4 restaura na v0_5 e o da v0_5 com emprestimo vai e volta`() {
        criarBancoV3()
        val banco = abrirComoOApp()
        try {
            val r = RelogioFalso()
            val servico = BackupServico(banco, r, "teste", pasta.newFolder("copias"), RegistroDeBackupEmMemoria())
            val bytes = ByteArrayOutputStream().also { runBlocking { servico.exportar(it) } }.toByteArray()
            val p = partes(bytes)

            // 1) Como a v0.4 gerava: versaoBanco = 3, sem emprestimos e sem emprestimoId.
            val dados = Json.parseToJsonElement(p.getValue("dados.json").toString(Charsets.UTF_8)).jsonObject
            val obrigs = JsonArray(dados.getValue("obrigacoes").jsonArray.map { o -> JsonObject(o.jsonObject.filterKeys { it != "emprestimoId" }) })
            val dadosV3 = JsonObject(dados.filterKeys { it != "emprestimos" } + ("obrigacoes" to obrigs)).toString().toByteArray()
            val man = Json.parseToJsonElement(p.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject
            val manV3 = JsonObject(man.filterKeys { it != "emprestimos" } + mapOf("versaoBanco" to JsonPrimitive(3), "sha256Dados" to JsonPrimitive(sha(dadosV3))))
            val antes = runBlocking { banco.backupDao().let { listOf(it.movimentacoes(), it.obrigacoes(), it.compras(), it.pagamentosDeFatura()) } }
            val lido = servico.ler(ByteArrayInputStream(zip("manifest.json" to manV3.toString().toByteArray(), "dados.json" to dadosV3)))
            assertEquals(3, lido.manifesto.versaoBanco)
            runBlocking { servico.restaurar(lido) }
            assertEquals(antes, runBlocking { banco.backupDao().let { listOf(it.movimentacoes(), it.obrigacoes(), it.compras(), it.pagamentosDeFatura()) } })
            // Depois de restaurar um backup antigo, a categoria "Juros recebidos" volta (com auditoria).
            val cats = CategoriaRepositorio(banco, Auditor(banco.auditoriaDao(), r), r)
            runBlocking { cats.garantirJurosRecebidos() }
            assertNotNull(runBlocking { banco.categoriaDao().buscarPorCodigo(CategoriasDoSistema.JUROS_RECEBIDOS) })
            assertEquals(runBlocking { cats.garantirJurosRecebidos() }, runBlocking { cats.garantirJurosRecebidos() }) // não duplica

            // 2) O backup da v0.5 diz versão 4: a v0.4 (versão 3) o recusa.
            assertTrue(servico.ler(ByteArrayInputStream(bytes)).manifesto.versaoBanco > 3)

            // 3) Com um empréstimo (linhas fictícias), vai e volta igual.
            runBlocking {
                banco.contaDao().inserir(ContaEntidade("ton", "Tonho", "EMPRESTIMO_CONCEDIDO", null, 0, "2026-12-01", null, null, null, null, null, null, null, 1, 1, null))
                banco.movimentacaoDao().inserir(MovimentacaoEntidade("me", "TRANSFERENCIA", "2026-12-10", 100_000, "pix", "ton", null, "Empréstimo", 1, 1, null))
                banco.emprestimoDao().inserir(EmprestimoEntidade("e1", "ton", "Empréstimo", null, 100_000, "2026-12-10", "pix", false, null, "me", null, 1, 1, null))
                banco.obrigacaoDao().inserir(ObrigacaoEntidade("pe1", "EMPRESTIMO", "Empréstimo (1/1)", 110_000, "2027-01-10", "2027-01", null, null, "ton", null, 1, 1, null, 1, 1, null, emprestimoId = "e1"))
            }
            val comEmprestimo = ByteArrayOutputStream().also { runBlocking { servico.exportar(it) } }.toByteArray()
            val foto = runBlocking { banco.backupDao().emprestimos() to banco.backupDao().obrigacoes() }
            runBlocking { servico.restaurar(servico.ler(ByteArrayInputStream(comEmprestimo))) }
            assertEquals(foto, runBlocking { banco.backupDao().emprestimos() to banco.backupDao().obrigacoes() })
            assertEquals(1, servico.ler(ByteArrayInputStream(comEmprestimo)).manifesto.emprestimos)

            // 4) Adulterado é recusado.
            val pc = partes(comEmprestimo)
            val ruim = pc.getValue("dados.json").toString(Charsets.UTF_8).replace("110000", "11000").toByteArray()
            assertTrue(runCatching { servico.ler(ByteArrayInputStream(zip("manifest.json" to pc.getValue("manifest.json"), "dados.json" to ruim))) }.exceptionOrNull() is BackupServico.BackupInvalido)
        } finally {
            banco.close()
        }
    }
}
