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
 * Migração 4 → 5 (v0.6, ADR 0024) e a matriz de backup do briefing, com um banco v4 FIEL
 * (SQL oficial do esquema 4) e dados fictícios em todas as tabelas da v0.5.
 */
@RunWith(AndroidJUnit4::class)
class MigracaoV5Test {
    @get:Rule val pasta = TemporaryFolder()
    private val nome = "migracao-v5-teste.db"
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val arquivo get() = context.getDatabasePath(nome)
    private val tabelasV4 = listOf("conta", "categoria", "movimentacao", "auditoria", "conta_fixa", "obrigacao", "pagamento", "obrigacao_ajuste", "compra", "pagamento_fatura", "emprestimo")
    private val colunasCompraV4 = "id, contaCreditoId, descricao, categoriaId, dataCompra, valorTotalCentavos, totalParcelas, movimentacaoId, anteriorAoApp, criadoEm, atualizadoEm, excluidoEm"

    @Before fun limpar() { context.deleteDatabase(nome) }
    @After fun apagar() { context.deleteDatabase(nome) }

    private fun esquema(v: Int): JsonObject =
        Json.parseToJsonElement(File("schemas/com.hkawzz.nkote.dados.NkPayBanco/$v.json").readText()).jsonObject.getValue("database").jsonObject

    private fun criarBancoV4() {
        val e = esquema(4)
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
            db.version = 4
        }
    }

    /** Dados fictícios em todas as tabelas da v0.5: cartão com compra, abatimento geral, empréstimo com parcelas. */
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
        db.execSQL("INSERT INTO compra VALUES ('c1', 'cart', 'Fone', 'ali', '2026-11-25', 30000, 3, 'mc', 0, 1, 1, NULL)")
        db.execSQL("INSERT INTO obrigacao VALUES ('p1', 'PARCELA', 'Fone (1/3)', 10000, '2026-12-12', '2026-12', NULL, 'ali', 'cart', 'c1', 1, 3, NULL, 1, 1, NULL, NULL)")
        db.execSQL("INSERT INTO pagamento_fatura VALUES ('g1', 'cart', '2026-12', 'mt', 10000, 1, 1, NULL)")
        db.execSQL("INSERT INTO emprestimo VALUES ('e1', 'ton', 'Carro', NULL, 50000, '2026-11-25', 'pix', 0, NULL, 'me', NULL, 1, 1, NULL)")
        db.execSQL("INSERT INTO obrigacao VALUES ('pe1', 'EMPRESTIMO', 'Carro (1/1)', 55000, '2027-01-10', '2027-01', NULL, NULL, 'ton', NULL, 1, 1, NULL, 1, 1, NULL, 'e1')")
        db.execSQL("INSERT INTO auditoria VALUES ('h1', 'g1', 10, 'CRIACAO', 'conta', 'pix', 'Conta “Pix” criada', NULL, '{}')")
    }

    private fun foto(consulta: (String) -> Cursor, sql: String): List<String> {
        val l = mutableListOf<String>()
        consulta(sql).use { c -> while (c.moveToNext()) l += (0 until c.columnCount).joinToString("|") { i -> "${c.getColumnName(i)}=${if (c.isNull(i)) "NULL" else c.getString(i)}" } }
        return l
    }

    private fun consultaDe(t: String) = if (t == "compra") "SELECT $colunasCompraV4 FROM compra ORDER BY id" else "SELECT * FROM $t ORDER BY id"
    private fun abrirComoOApp(): NkPayBanco = Room.databaseBuilder(context, NkPayBanco::class.java, nome).addMigrations(*MIGRACOES).allowMainThreadQueries().build()

    @Test
    fun `migrar de 4 para 5 preserva tudo e as compras continuam sem juros`() {
        criarBancoV4()
        val antes = SQLiteDatabase.openDatabase(arquivo.path, null, SQLiteDatabase.OPEN_READONLY).use { db -> tabelasV4.associateWith { t -> foto({ db.rawQuery(it, null) }, consultaDe(t)) } }
        val banco = abrirComoOApp()
        try {
            val db = banco.openHelper.writableDatabase
            assertEquals(NkPayBanco.VERSAO, db.version) // v0.7: o caminho 4 → 5 → 6 termina na versão atual (ADR 0028)
            for (t in tabelasV4) assertEquals("tabela $t mudou", antes.getValue(t), foto({ db.query(it) }, consultaDe(t)))
            db.query("SELECT COUNT(*) FROM compra WHERE valorOriginalCentavos IS NOT NULL").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            db.query("SELECT COUNT(*) FROM pendencia_levada").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            // A compra antiga continua sem juros: original = total.
            val compra = runBlocking { banco.compraDao().buscar("c1") }!!.paraDominio()
            assertEquals(30_000L, compra.valorOriginal)
            assertEquals(0L, compra.juros)
            val r = RelogioFalso()
            val a = Auditor(banco.auditoriaDao(), r)
            val s = Saldos.saldosPorConta(runBlocking { ContaRepositorio(banco, a, r).observar().first() }, runBlocking { MovimentacaoRepositorio(banco, a, r).observar().first() })
            assertEquals(90_000L - 10_000 - 50_000, s["pix"])
            assertEquals(-20_000L, s["cart"])
            assertEquals(50_000L, s["ton"])
        } finally {
            banco.close()
        }
        val deNovo = abrirComoOApp()
        try { assertEquals(2, runBlocking { deNovo.backupDao().obrigacoes() }.size) } finally { deNovo.close() }
    }

    @Test
    fun `copia antes de migrar e backup da v0_5 restaura na v0_6 e a v0_5 recusa a v0_6`() {
        criarBancoV4()
        assertTrue(copiarAntesDeMigrar(arquivo, pasta.newFolder("c"), NkPayBanco.VERSAO, 1000)!!.name.startsWith("banco-v4-antes-da-v${NkPayBanco.VERSAO}")) // v0.7: a versão atual é a 6
        val banco = abrirComoOApp()
        try {
            val servico = BackupServico(banco, RelogioFalso(), "teste", pasta.newFolder("copias"), RegistroDeBackupEmMemoria())
            val bytes = ByteArrayOutputStream().also { runBlocking { servico.exportar(it) } }.toByteArray()
            val p = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { val e = z.nextEntry ?: break; p[e.name] = z.readBytes() } }
            // Como a v0.5 gerava: versão 4, sem pendências levadas e sem valorOriginalCentavos.
            val dados = Json.parseToJsonElement(p.getValue("dados.json").toString(Charsets.UTF_8)).jsonObject
            val compras = JsonArray(dados.getValue("compras").jsonArray.map { c -> JsonObject(c.jsonObject.filterKeys { it != "valorOriginalCentavos" }) })
            val dadosV4 = JsonObject(dados.filterKeys { it != "pendenciasLevadas" } + ("compras" to compras)).toString().toByteArray()
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(dadosV4).joinToString("") { "%02x".format(it) }
            val man = Json.parseToJsonElement(p.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject
            val manV4 = JsonObject(man + mapOf("versaoBanco" to JsonPrimitive(4), "sha256Dados" to JsonPrimitive(sha)))
            val zip = ByteArrayOutputStream().also { s -> ZipOutputStream(s).use { z ->
                z.putNextEntry(ZipEntry("manifest.json")); z.write(manV4.toString().toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("dados.json")); z.write(dadosV4); z.closeEntry()
            } }.toByteArray()
            val antes = runBlocking { banco.backupDao().let { listOf(it.compras(), it.obrigacoes(), it.emprestimos(), it.movimentacoes()) } }
            runBlocking { servico.restaurar(servico.ler(ByteArrayInputStream(zip))) }
            assertEquals(antes, runBlocking { banco.backupDao().let { listOf(it.compras(), it.obrigacoes(), it.emprestimos(), it.movimentacoes()) } })
            assertTrue(servico.ler(ByteArrayInputStream(bytes)).manifesto.versaoBanco > 4)
            assertNotNull(runBlocking { banco.compraDao().buscar("c1") })
        } finally {
            banco.close()
        }
    }
}
