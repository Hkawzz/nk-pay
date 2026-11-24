package com.hkawzz.nkote.dados

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * Migração 5 → 6 (v0.7, ADR 0028) e a matriz de backup do briefing, com um banco v5 FIEL
 * (SQL oficial do esquema 5) e dados fictícios em todas as tabelas da v0.6.
 */
@RunWith(AndroidJUnit4::class)
class MigracaoV6Test {
    @get:Rule val pasta = TemporaryFolder()
    private val nome = "migracao-v6-teste.db"
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val arquivo get() = context.getDatabasePath(nome)
    private val tabelasV5 = listOf("conta", "categoria", "movimentacao", "auditoria", "conta_fixa", "obrigacao", "pagamento", "obrigacao_ajuste", "compra", "pagamento_fatura", "emprestimo", "pendencia_levada")

    @Before fun limpar() { context.deleteDatabase(nome) }
    @After fun apagar() { context.deleteDatabase(nome) }

    private fun esquema(v: Int): JsonObject =
        Json.parseToJsonElement(File("schemas/com.hkawzz.nkote.dados.NkPayBanco/$v.json").readText()).jsonObject.getValue("database").jsonObject

    private fun criarBancoV5() {
        val e = esquema(5)
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
            db.version = 5
        }
    }

    /** Dados fictícios em todas as tabelas da v0.6: compra com juros, abatimento geral, empréstimo, pendência levada. */
    private fun preencher(db: SQLiteDatabase) {
        fun conta(id: String, nome: String, natureza: String, saldo: Long, fech: Int? = null, venc: Int? = null) = db.execSQL(
            "INSERT INTO conta VALUES (?, ?, ?, NULL, ?, '2026-11-01', NULL, NULL, NULL, NULL, ?, ?, NULL, 1, 1, NULL)",
            arrayOf<Any?>(id, nome, natureza, saldo, fech, venc),
        )
        conta("pix", "Pix", "PROPRIO", 90_000)
        conta("cart", "Cartão", "CARTAO", 0, 5, 12)
        conta("ton", "Tonho", "EMPRESTIMO_CONCEDIDO", 0)
        db.execSQL("INSERT INTO categoria VALUES ('ali', 'Alimentação', NULL, NULL, 1, 1, NULL, 'carrinho', 4283163008)")
        fun mov(id: String, tipo: String, valor: Long, conta: String, destino: String?, cat: String?) = db.execSQL(
            "INSERT INTO movimentacao VALUES (?, ?, '2026-11-25', ?, ?, ?, ?, 'teste', 2, 2, NULL)", arrayOf<Any?>(id, tipo, valor, conta, destino, cat),
        )
        mov("mc", "DESPESA", 30_000, "cart", null, "ali")
        mov("mt", "TRANSFERENCIA", 10_000, "pix", "cart", null)
        mov("me", "TRANSFERENCIA", 50_000, "pix", "ton", null)
        db.execSQL("INSERT INTO compra VALUES ('c1', 'cart', 'Fone', 'ali', '2026-11-25', 39000, 3, 'mc', 0, 1, 1, NULL, 30000)")
        db.execSQL("INSERT INTO obrigacao VALUES ('p1', 'PARCELA', 'Fone (1/3)', 13000, '2026-12-12', '2026-12', NULL, 'ali', 'cart', 'c1', 1, 3, NULL, 1, 1, NULL, NULL)")
        db.execSQL("INSERT INTO pagamento_fatura VALUES ('g1', 'cart', '2026-12', 'mt', 10000, 1, 1, NULL)")
        db.execSQL("INSERT INTO emprestimo VALUES ('e1', 'ton', 'Carro', NULL, 50000, '2026-11-25', 'pix', 0, NULL, 'me', NULL, 1, 1, NULL)")
        db.execSQL("INSERT INTO obrigacao VALUES ('pe1', 'EMPRESTIMO', 'Carro (1/1)', 55000, '2027-01-10', '2027-01', NULL, NULL, 'ton', NULL, 1, 1, NULL, 1, 1, NULL, 'e1')")
        db.execSQL("INSERT INTO obrigacao VALUES ('av', 'AVULSA', 'Conserto', 25000, '2026-10-15', '2026-10', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, NULL, NULL)")
        db.execSQL("INSERT INTO pendencia_levada VALUES ('pl', 'av', '2026-12', 1, 1, NULL)")
        db.execSQL("INSERT INTO auditoria VALUES ('h1', 'g1', 10, 'CRIACAO', 'conta', 'pix', 'Conta “Pix” criada', NULL, '{}')")
    }

    private fun foto(consulta: (String) -> Cursor, sql: String): List<String> {
        val l = mutableListOf<String>()
        consulta(sql).use { c -> while (c.moveToNext()) l += (0 until c.columnCount).filter { c.getColumnName(it) !in COLUNAS_NOVAS }.joinToString("|") { i -> "${c.getColumnName(i)}=${if (c.isNull(i)) "NULL" else c.getString(i)}" } }
        return l
    }

    private fun consultaDe(t: String) = "SELECT * FROM $t ORDER BY id"
    private fun abrirComoOApp(): NkPayBanco = Room.databaseBuilder(context, NkPayBanco::class.java, nome).addMigrations(*MIGRACOES).allowMainThreadQueries().build()

    @Test
    fun `migrar de 5 para 6 preserva tudo e cria os objetivos vazios`() {
        criarBancoV5()
        val antes = SQLiteDatabase.openDatabase(arquivo.path, null, SQLiteDatabase.OPEN_READONLY).use { db -> tabelasV5.associateWith { t -> foto({ db.rawQuery(it, null) }, consultaDe(t)) } }
        val banco = abrirComoOApp()
        try {
            val db = banco.openHelper.writableDatabase
            assertEquals(NkPayBanco.VERSAO, db.version) // v0.8: o caminho 5 → 6 → 7 termina na versão atual (ADR 0033)
            for (t in tabelasV5) assertEquals("tabela $t mudou", antes.getValue(t), foto({ db.query(it) }, consultaDe(t)))
            for (t in listOf("objetivo", "movimento_reserva")) db.query("SELECT COUNT(*) FROM $t").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            // O SQL das tabelas novas é idêntico ao do esquema 6 exportado pelo Room.
            for (o in esquema(6).getValue("entities").jsonArray.map { it.jsonObject }.filter { it.getValue("tableName").jsonPrimitive.content in setOf("objetivo", "movimento_reserva") }) {
                val t = o.getValue("tableName").jsonPrimitive.content
                val sql = o.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", t).replace("CREATE TABLE IF NOT EXISTS", "CREATE TABLE")
                // v0.8: a migração 6 → 7 acrescenta contaCaixinhaId ao objetivo (ADR 0033).
                db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(t)).use { c -> assertTrue(c.moveToFirst()); assertEquals(sql, c.getString(0).replace("`contaCaixinhaId` TEXT, ", "")) }
            }
            // A compra com juros continua com juros; saldos iguais.
            val compra = runBlocking { banco.compraDao().buscar("c1") }!!.paraDominio()
            assertEquals(30_000L, compra.valorOriginal)
            assertEquals(9_000L, compra.juros)
            val r = RelogioFalso()
            val a = Auditor(banco.auditoriaDao(), r)
            val s = Saldos.saldosPorConta(runBlocking { ContaRepositorio(banco, a, r).observar().first() }, runBlocking { MovimentacaoRepositorio(banco, a, r).observar().first() })
            assertEquals(90_000L - 10_000 - 50_000, s["pix"])
            assertEquals(-20_000L, s["cart"])
            assertEquals(1, runBlocking { banco.pendenciaLevadaDao().observarValidas().first() }.size)
        } finally {
            banco.close()
        }
    }

    @Test
    fun `copia antes de migrar e backup da v0_6 restaura na v0_7 com objetivos ida e volta e a v0_6 recusa a v0_7`() {
        criarBancoV5()
        assertTrue(copiarAntesDeMigrar(arquivo, pasta.newFolder("c"), NkPayBanco.VERSAO, 1000)!!.name.startsWith("banco-v5-antes-da-v${NkPayBanco.VERSAO}")) // v0.8: a versão atual é a 7
        val banco = abrirComoOApp()
        try {
            val servico = BackupServico(banco, RelogioFalso(), "teste", pasta.newFolder("copias"), RegistroDeBackupEmMemoria())
            val bytes = ByteArrayOutputStream().also { runBlocking { servico.exportar(it) } }.toByteArray()
            val p = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { val e = z.nextEntry ?: break; p[e.name] = z.readBytes() } }
            // Como a v0.6 gerava: versão 5, sem objetivos nem movimentos de reserva.
            val dados = Json.parseToJsonElement(p.getValue("dados.json").toString(Charsets.UTF_8)).jsonObject
            val dadosV5 = JsonObject(dados.filterKeys { it != "objetivos" && it != "movimentosReserva" }).toString().toByteArray()
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(dadosV5).joinToString("") { "%02x".format(it) }
            val man = Json.parseToJsonElement(p.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject
            val manV5 = JsonObject(man.filterKeys { it != "objetivos" } + mapOf("versaoBanco" to JsonPrimitive(5), "sha256Dados" to JsonPrimitive(sha)))
            val zip = ByteArrayOutputStream().also { s -> ZipOutputStream(s).use { z ->
                z.putNextEntry(ZipEntry("manifest.json")); z.write(manV5.toString().toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("dados.json")); z.write(dadosV5); z.closeEntry()
            } }.toByteArray()
            val antes = runBlocking { banco.backupDao().let { listOf(it.compras(), it.obrigacoes(), it.emprestimos(), it.movimentacoes(), it.pendenciasLevadas()) } }
            runBlocking { servico.restaurar(servico.ler(ByteArrayInputStream(zip))) }
            assertEquals(antes, runBlocking { banco.backupDao().let { listOf(it.compras(), it.obrigacoes(), it.emprestimos(), it.movimentacoes(), it.pendenciasLevadas()) } })

            // v0.7 ida e volta com objetivos e movimentos (inclusive um excluído e um "mover").
            runBlocking {
                banco.objetivoDao().inserir(ObjetivoEntidade("o1", "Fone", "estrela", 4283163008, 30_000, "2027-06-30", null, false, 1, 1, null))
                banco.objetivoDao().inserir(ObjetivoEntidade("o2", "Viagem", null, null, null, null, 5, true, 2, 2, null))
                banco.movimentoReservaDao().inserir(MovimentoReservaEntidade("m1", "o1", "2026-11-26", 20_000, "GUARDAR", "g1", 3, 3, null))
                banco.movimentoReservaDao().inserir(MovimentoReservaEntidade("m2", "o1", "2026-11-27", -5_000, "MOVER", "g2", 4, 4, null))
                banco.movimentoReservaDao().inserir(MovimentoReservaEntidade("m3", "o2", "2026-11-27", 5_000, "MOVER", "g2", 4, 4, null))
                banco.movimentoReservaDao().inserir(MovimentoReservaEntidade("m4", "o1", "2026-11-28", 1_000, "AJUSTE", "g3", 5, 5, 6))
            }
            val tudo = runBlocking { banco.backupDao().let { listOf(it.objetivos(), it.movimentosReserva(), it.contas(), it.movimentacoes()) } }
            val v7 = ByteArrayOutputStream().also { runBlocking { servico.exportar(it) } }.toByteArray()
            runBlocking { banco.objetivoDao().inserir(ObjetivoEntidade("o3", "Depois", null, null, null, null, null, false, 9, 9, null)) }
            val lido = servico.ler(ByteArrayInputStream(v7))
            assertEquals(NkPayBanco.VERSAO, lido.manifesto.versaoBanco) // v0.8: a versão atual é a 7
            assertEquals(2, lido.manifesto.objetivos)
            runBlocking { servico.restaurar(lido) }
            assertEquals(tudo, runBlocking { banco.backupDao().let { listOf(it.objetivos(), it.movimentosReserva(), it.contas(), it.movimentacoes()) } })
            // A v0.6 (banco 5) recusa: a versão do backup é maior que a dela.
            assertTrue(lido.manifesto.versaoBanco > 5)
            assertNotNull(runBlocking { banco.compraDao().buscar("c1") })
        } finally {
            banco.close()
        }
    }
}