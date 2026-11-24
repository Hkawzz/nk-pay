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
 * Migração 6 → 7 (v0.8, ADR 0033) e a matriz de backup do briefing, com um banco v6 FIEL
 * (SQL oficial do esquema 6) e dados fictícios em todas as tabelas da v0.7, inclusive objetivos.
 */
@RunWith(AndroidJUnit4::class)
class MigracaoV7Test {
    @get:Rule val pasta = TemporaryFolder()
    private val nome = "migracao-v7-teste.db"
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val arquivo get() = context.getDatabasePath(nome)
    private val tabelasV6 = listOf("conta", "categoria", "movimentacao", "auditoria", "conta_fixa", "obrigacao", "pagamento", "obrigacao_ajuste", "compra", "pagamento_fatura", "emprestimo", "pendencia_levada", "objetivo", "movimento_reserva")

    @Before fun limpar() { context.deleteDatabase(nome) }
    @After fun apagar() { context.deleteDatabase(nome) }

    private fun esquema(v: Int): JsonObject =
        Json.parseToJsonElement(File("schemas/com.hkawzz.nkote.dados.NkPayBanco/$v.json").readText()).jsonObject.getValue("database").jsonObject

    private fun criarBancoV6() {
        val e = esquema(6)
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
            db.version = 6
        }
    }

    /** Dados fictícios em todas as tabelas da v0.7: compra com juros, abatimento geral, empréstimo, pendência levada e um objetivo com R$ 200,00 guardados. */
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
        db.execSQL("INSERT INTO objetivo VALUES ('o1', 'Fone', 'estrela', 4283163008, 30000, '2027-06-30', NULL, 0, 3, 3, NULL)")
        db.execSQL("INSERT INTO movimento_reserva VALUES ('r1', 'o1', '2026-11-26', 20000, 'GUARDAR', 'g1', 4, 4, NULL)")
        db.execSQL("INSERT INTO auditoria VALUES ('h1', 'g1', 10, 'CRIACAO', 'conta', 'pix', 'Conta “Pix” criada', NULL, '{}')")
    }

    private fun foto(consulta: (String) -> Cursor, sql: String): List<String> {
        val l = mutableListOf<String>()
        consulta(sql).use { c -> while (c.moveToNext()) l += (0 until c.columnCount).joinToString("|") { i -> "${c.getColumnName(i)}=${if (c.isNull(i)) "NULL" else c.getString(i)}" } }
        return l
    }

    private fun consultaDe(t: String) = "SELECT * FROM $t ORDER BY id"
    private fun abrirComoOApp(): NkPayBanco = Room.databaseBuilder(context, NkPayBanco::class.java, nome).addMigrations(*MIGRACOES).allowMainThreadQueries().build()

    @Test
    fun `migrar de 6 para 7 preserva tudo e os saldos de conta ficam identicos`() {
        criarBancoV6()
        val antes = SQLiteDatabase.openDatabase(arquivo.path, null, SQLiteDatabase.OPEN_READONLY).use { db -> tabelasV6.associateWith { t -> foto({ db.rawQuery(it, null) }, consultaDe(t)) } }
        val banco = abrirComoOApp()
        try {
            val db = banco.openHelper.writableDatabase
            assertEquals(NkPayBanco.VERSAO, db.version) // v0.9: o banco atual é o 8
            // O objetivo ganhou contaCaixinhaId no fim, que nasce vazia; o resto é igual campo a campo.
            for (t in tabelasV6) assertEquals("tabela $t mudou", antes.getValue(t), foto({ db.query(it) }, consultaDe(t)).map { it.removeSuffix("|contaCaixinhaId=NULL").replace(Regex("\\|ordem=\\d+$"), "") }) // v0.9: conta.ordem
            db.query("SELECT COUNT(*) FROM configuracao_backup").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            for (o in esquema(7).getValue("entities").jsonArray.map { it.jsonObject }.filter { it.getValue("tableName").jsonPrimitive.content == "configuracao_backup" }) {
                val sql = o.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", "configuracao_backup").replace("CREATE TABLE IF NOT EXISTS", "CREATE TABLE")
                db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'configuracao_backup'").use { c -> assertTrue(c.moveToFirst()); assertEquals(sql, c.getString(0)) }
            }
            val r = RelogioFalso()
            val a = Auditor(banco.auditoriaDao(), r)
            val s = Saldos.saldosPorConta(runBlocking { ContaRepositorio(banco, a, r).observar().first() }, runBlocking { MovimentacaoRepositorio(banco, a, r).observar().first() })
            assertEquals(90_000L - 10_000 - 50_000, s["pix"])
            assertEquals(-20_000L, s["cart"])
            assertEquals(50_000L, s["ton"])
            assertEquals(null, runBlocking { banco.objetivoDao().buscar("o1") }!!.contaCaixinhaId)
        } finally {
            banco.close()
        }
    }

    @Test
    fun `backup da v0_7 restaura na v0_8 e a v0_7 recusa a v0_8`() {
        criarBancoV6()
        assertTrue(copiarAntesDeMigrar(arquivo, pasta.newFolder("c"), NkPayBanco.VERSAO, 1000)!!.name.startsWith("banco-v6-antes-da-v${NkPayBanco.VERSAO}"))
        val banco = abrirComoOApp()
        try {
            val servico = BackupServico(banco, RelogioFalso(), "teste", pasta.newFolder("copias"), RegistroDeBackupEmMemoria())
            val bytes = ByteArrayOutputStream().also { runBlocking { servico.exportar(it) } }.toByteArray()
            val p = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { val e = z.nextEntry ?: break; p[e.name] = z.readBytes() } }
            // Como a v0.7 gerava: versão 6, objetivos sem contaCaixinhaId.
            val dados = Json.parseToJsonElement(p.getValue("dados.json").toString(Charsets.UTF_8)).jsonObject
            val objetivos = kotlinx.serialization.json.JsonArray(dados.getValue("objetivos").jsonArray.map { o -> JsonObject(o.jsonObject.filterKeys { it != "contaCaixinhaId" }) })
            val dadosV6 = JsonObject(dados + ("objetivos" to objetivos)).toString().toByteArray()
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(dadosV6).joinToString("") { "%02x".format(it) }
            val man = Json.parseToJsonElement(p.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject
            val manV6 = JsonObject(man + mapOf("versaoBanco" to JsonPrimitive(6), "sha256Dados" to JsonPrimitive(sha)))
            val zip = ByteArrayOutputStream().also { s -> ZipOutputStream(s).use { z ->
                z.putNextEntry(ZipEntry("manifest.json")); z.write(manV6.toString().toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("dados.json")); z.write(dadosV6); z.closeEntry()
            } }.toByteArray()
            val antes = runBlocking { banco.backupDao().let { listOf(it.contas(), it.movimentacoes(), it.objetivos(), it.movimentosReserva(), it.compras(), it.emprestimos()) } }
            runBlocking { servico.restaurar(servico.ler(ByteArrayInputStream(zip))) }
            assertEquals(antes, runBlocking { banco.backupDao().let { listOf(it.contas(), it.movimentacoes(), it.objetivos(), it.movimentosReserva(), it.compras(), it.emprestimos()) } })
            // O backup da v0.8 tem versão 7: a v0.7 (banco 6) recusa versões maiores que a dela.
            assertEquals(NkPayBanco.VERSAO, servico.ler(ByteArrayInputStream(bytes)).manifesto.versaoBanco)
            assertNotNull(runBlocking { banco.objetivoDao().buscar("o1") })
        } finally {
            banco.close()
        }
    }

    @Test
    fun `V3 os objetivos da v0_7 viram caixinhas zeradas sem mudar nenhum saldo de conta`() {
        criarBancoV6()
        val banco = abrirComoOApp()
        try {
            val r = RelogioFalso()
            val a = Auditor(banco.auditoriaDao(), r)
            val contas = ContaRepositorio(banco, a, r)
            val movs = MovimentacaoRepositorio(banco, a, r)
            val antesMovs = runBlocking { movs.observar().first() }
            val antesSaldos = Saldos.saldosPorConta(runBlocking { contas.observar().first() }, antesMovs)
            val servico = ObjetivoServico(banco, a, r)
            assertEquals(1, runBlocking { servico.converterObjetivosAntigos() })
            assertEquals(0, runBlocking { servico.converterObjetivosAntigos() }) // idempotente
            val depoisContas = runBlocking { contas.observar().first() }
            val depoisMovs = runBlocking { movs.observar().first() }
            // Nenhuma transferência criada e nenhum saldo de conta mudou.
            assertEquals(antesMovs, depoisMovs)
            val depoisSaldos = Saldos.saldosPorConta(depoisContas, depoisMovs)
            for ((id, s) in antesSaldos) assertEquals("saldo de $id", s, depoisSaldos[id])
            // A caixinha nasceu zerada; o guardado antigo (R$ 200,00) deixou de contar: o disponível sobe.
            val o = runBlocking { banco.objetivoDao().buscar("o1") }!!
            val cx = depoisContas.single { it.id == o.contaCaixinhaId }
            assertEquals(com.hkawzz.nkote.dominio.Natureza.CAIXINHA, cx.natureza)
            assertEquals(0L, depoisSaldos[cx.id])
            assertEquals(30_000L, Saldos.resumo(depoisContas, depoisMovs).disponivel)
            val vo = com.hkawzz.nkote.dominio.VisaoObjetivos(listOf(o.paraDominio()), runBlocking { banco.movimentoReservaDao().observarValidos().first() }.map { it.paraDominio() }, depoisContas, depoisMovs)
            assertEquals(0L, vo.guardado("o1"))
            assertTrue(vo.temHistoricoAntigo)
            assertEquals(listOf(20_000L), vo.situacao("o1")!!.antigos.map { it.valor })
        } finally {
            banco.close()
        }
    }

    @Test
    fun `V3 roda depois de restaurar um backup da v0_7`() {
        criarBancoV6()
        val banco = abrirComoOApp()
        try {
            val r = RelogioFalso()
            val servicoObj = ObjetivoServico(banco, Auditor(banco.auditoriaDao(), r), r)
            val backup = BackupServico(banco, r, "teste", pasta.newFolder("copias"), RegistroDeBackupEmMemoria()) { servicoObj.converterObjetivosAntigos() }
            val bytes = ByteArrayOutputStream().also { runBlocking { backup.exportar(it) } }.toByteArray()
            runBlocking { backup.restaurar(backup.ler(ByteArrayInputStream(bytes))) }
            assertNotNull(runBlocking { banco.objetivoDao().buscar("o1") }!!.contaCaixinhaId)
        } finally {
            banco.close()
        }
    }
}