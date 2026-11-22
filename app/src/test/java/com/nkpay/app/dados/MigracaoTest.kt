package com.nkpay.app.dados

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nkpay.app.dominio.Saldos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * Testes de migração do banco (adendo v0.3, regras de segurança dos dados).
 *
 * Cada teste cria um banco EXATAMENTE como a v0.2 o deixa no celular: as tabelas são
 * criadas com o SQL oficial do esquema 1 (app/schemas/.../1.json, o mesmo que o Room
 * gerou), com o "identity hash" da versão 1. Depois o banco é preenchido com dados
 * fictícios e aberto pelo Room com as migrações reais do app.
 *
 * Ao abrir, o próprio Room VALIDA o esquema resultante contra as entidades da versão 2
 * e falha se qualquer tabela, coluna, índice ou chave estrangeira estiver diferente.
 */
@RunWith(AndroidJUnit4::class)
class MigracaoTest {

    @get:Rule val pasta = TemporaryFolder()

    private val nome = "migracao-teste.db"
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val arquivo get() = context.getDatabasePath(nome)
    private val tabelasV1 = listOf("conta", "categoria", "movimentacao", "auditoria")
    private val tabelasNovas = listOf("conta_fixa", "obrigacao", "pagamento", "obrigacao_ajuste")

    @Before fun limpar() { context.deleteDatabase(nome) }
    @After fun apagar() { context.deleteDatabase(nome) }

    /** Lê o esquema oficial exportado pelo Room (o teste roda na pasta do módulo app/). */
    private fun esquema(versao: Int): JsonObject {
        val f = File("schemas/com.nkpay.app.dados.NkPayBanco/$versao.json")
        return Json.parseToJsonElement(f.readText()).jsonObject.getValue("database").jsonObject
    }

    /** Cria o arquivo do banco no esquema 1, igual ao que está no celular com a v0.2. */
    private fun criarBancoV1(preencher: (SQLiteDatabase) -> Unit) {
        val e = esquema(1)
        arquivo.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(arquivo, null).use { db ->
            for (ent in e.getValue("entities").jsonArray) {
                val o = ent.jsonObject
                val tabela = o.getValue("tableName").jsonPrimitive.content
                db.execSQL(o.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", tabela))
                for (i in (o["indices"] as? JsonArray).orEmpty()) {
                    db.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", tabela))
                }
            }
            for (q in e.getValue("setupQueries").jsonArray) db.execSQL(q.jsonPrimitive.content)
            preencher(db)
            db.version = 1
        }
    }

    /** Dados fictícios no formato do esquema 1. */
    private fun preencherV1(db: SQLiteDatabase) {
        fun conta(id: String, nome: String, natureza: String, beneficio: String?, saldo: Long, arquivada: Long? = null) =
            db.execSQL(
                "INSERT INTO conta (id, nome, natureza, tipoBeneficio, saldoInicialCentavos, dataSaldoInicial, icone, cor, instituicao, limiteCentavos, diaFechamento, diaVencimento, arquivadaEm, criadoEm, atualizadoEm, excluidoEm) VALUES (?, ?, ?, ?, ?, '2026-12-01', NULL, NULL, NULL, NULL, NULL, NULL, ?, 1, 1, NULL)",
                arrayOf<Any?>(id, nome, natureza, beneficio, saldo, arquivada),
            )
        conta("pix", "Pix", "PROPRIO", null, 120_000)
        conta("din", "Dinheiro", "PROPRIO", null, 30_000)
        conta("va", "VA", "BENEFICIO", "VA", 25_000)
        conta("velha", "Conta antiga", "PROPRIO", null, 0, arquivada = 5)

        db.execSQL("INSERT INTO categoria VALUES ('alim', 'Alimentação', NULL, NULL, 1, 1, NULL)")
        db.execSQL("INSERT INTO categoria VALUES ('enc', 'Encargos financeiros', 'ENCARGOS_FINANCEIROS', NULL, 1, 1, NULL)")

        fun mov(id: String, tipo: String, valor: Long, conta: String, destino: String?, categoria: String?, excluido: Long? = null) =
            db.execSQL(
                "INSERT INTO movimentacao VALUES (?, ?, '2026-12-05', ?, ?, ?, ?, 'teste', 2, 2, ?)",
                arrayOf<Any?>(id, tipo, valor, conta, destino, categoria, excluido),
            )
        mov("m1", "TRANSFERENCIA", 4_000, "pix", "din", null)
        mov("m2", "DESPESA", 2_500, "va", null, "alim")
        mov("m3", "ENTRADA", 50_000, "pix", null, null)
        mov("m4", "AJUSTE", -1_270, "din", null, null)
        mov("m5", "DESPESA", 99_999, "pix", null, "alim", excluido = 9) // excluída logicamente

        db.execSQL("INSERT INTO auditoria VALUES ('a1', 'g1', 10, 'CRIACAO', 'conta', 'pix', 'Conta “Pix” criada', NULL, '{}')")
        db.execSQL("INSERT INTO auditoria VALUES ('a2', 'g2', 11, 'EXCLUSAO', 'movimentacao', 'm5', 'Despesa excluída', '{}', '{}')")
    }

    /** Todas as linhas de uma tabela, como texto, para comparar antes e depois. */
    private fun foto(consulta: (String) -> Cursor, tabela: String): List<String> {
        val linhas = mutableListOf<String>()
        consulta("SELECT * FROM $tabela ORDER BY id").use { c ->
            while (c.moveToNext()) {
                linhas += (0 until c.columnCount).joinToString("|") { i -> "${c.getColumnName(i)}=${if (c.isNull(i)) "NULL" else c.getString(i)}" }
            }
        }
        return linhas
    }

    private fun abrirComoOApp(): NkPayBanco =
        Room.databaseBuilder(context, NkPayBanco::class.java, nome)
            .addMigrations(*MIGRACOES)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `migrar de 1 para 2 preserva todas as linhas exatamente iguais`() {
        criarBancoV1(::preencherV1)
        val antes = SQLiteDatabase.openDatabase(arquivo.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            tabelasV1.associateWith { t -> foto({ db.rawQuery(it, null) }, t) }
        }

        val banco = abrirComoOApp()
        try {
            val db = banco.openHelper.writableDatabase // dispara a migração + validação do esquema
            assertEquals(2, db.version)
            val depois = tabelasV1.associateWith { t -> foto({ db.query(it) }, t) }
            for (t in tabelasV1) assertEquals("tabela $t mudou", antes.getValue(t), depois.getValue(t))
            assertEquals(4, depois.getValue("conta").size)
            assertEquals(5, depois.getValue("movimentacao").size)
        } finally {
            banco.close()
        }
    }

    @Test
    fun `tabelas novas nascem vazias com o esquema da versao 2 e nada e duplicado`() {
        criarBancoV1(::preencherV1)
        val banco = abrirComoOApp()
        try {
            val db = banco.openHelper.writableDatabase
            for (t in tabelasNovas) {
                db.query("SELECT COUNT(*) FROM $t").use { c -> c.moveToFirst(); assertEquals("$t deveria estar vazia", 0, c.getInt(0)) }
            }
            for (t in tabelasV1) {
                db.query("SELECT COUNT(*), COUNT(DISTINCT id) FROM $t").use { c -> c.moveToFirst(); assertEquals("duplicado em $t", c.getInt(0), c.getInt(1)) }
            }
            // O SQL de cada tabela nova é idêntico ao do esquema 2 exportado pelo Room.
            val esperado = esquema(2).getValue("entities").jsonArray.map { it.jsonObject }
                .filter { it.getValue("tableName").jsonPrimitive.content in tabelasNovas }
                .associate { o ->
                    val t = o.getValue("tableName").jsonPrimitive.content
                    t to o.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", t).replace("CREATE TABLE IF NOT EXISTS", "CREATE TABLE")
                }
            for ((t, sql) in esperado) {
                db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(t)).use { c ->
                    assertTrue("tabela $t não foi criada", c.moveToFirst())
                    assertEquals("SQL de $t", sql, c.getString(0))
                }
            }
        } finally {
            banco.close()
        }
    }

    @Test
    fun `saldos disponivel beneficios e historico sao os mesmos depois da migracao`() {
        criarBancoV1(::preencherV1)
        val banco = abrirComoOApp()
        try {
            val relogio = RelogioFalso()
            val auditor = Auditor(banco.auditoriaDao(), relogio)
            val contas = runBlocking { ContaRepositorio(banco, auditor, relogio).observar().first() }
            val movs = runBlocking { MovimentacaoRepositorio(banco, auditor, relogio).observar().first() }

            val saldos = Saldos.saldosPorConta(contas, movs)
            assertEquals(166_000L, saldos["pix"]) // 1.200 − 40 + 500 (a despesa excluída não conta)
            assertEquals(32_730L, saldos["din"]) // 300 + 40 − 12,70
            assertEquals(22_500L, saldos["va"]) // 250 − 25
            val r = Saldos.resumo(contas, movs)
            assertEquals(198_730L, r.disponivel)
            assertEquals(22_500L, r.totalBeneficios)

            assertEquals(4, contas.size)
            assertEquals(4, movs.size) // 5 no banco, 1 excluída logicamente
            assertEquals(2, runBlocking { banco.auditoriaDao().listarTodas() }.size)
        } finally {
            banco.close()
        }

        // Abrir de novo não migra outra vez nem duplica nada.
        val deNovo = abrirComoOApp()
        try {
            assertEquals(5, runBlocking { deNovo.backupDao().movimentacoes() }.size)
            assertEquals(4, runBlocking { deNovo.backupDao().contas() }.size)
            assertEquals(2, runBlocking { deNovo.backupDao().auditoria() }.size)
        } finally {
            deNovo.close()
        }
    }

    @Test
    fun `um backup feito depois da migracao inclui tudo e restaura igual`() {
        criarBancoV1(::preencherV1)
        val banco = abrirComoOApp()
        try {
            val servico = BackupServico(banco, RelogioFalso(), "teste", pasta.newFolder("copias"), RegistroDeBackupEmMemoria())
            val bytes = java.io.ByteArrayOutputStream().also { runBlocking { servico.exportar(it) } }.toByteArray()
            val antes = runBlocking { banco.backupDao().movimentacoes() }
            runBlocking { servico.restaurar(servico.ler(bytes.inputStream())) }
            assertEquals(antes, runBlocking { banco.backupDao().movimentacoes() })
        } finally {
            banco.close()
        }
    }

    @Test
    fun `copia de seguranca do arquivo e feita antes de migrar`() {
        val arq = File(pasta.root, "nk-pay.db")
        SQLiteDatabase.openOrCreateDatabase(arq, null).use { db ->
            db.execSQL("CREATE TABLE t (x INTEGER)")
            db.execSQL("INSERT INTO t VALUES (42)")
            db.version = 1
        }
        val copias = File(pasta.root, "copias")

        val copia = copiarAntesDeMigrar(arq, copias, versaoAtual = 2, agora = 1000)
        assertNotNull(copia)
        assertArrayEquals(arq.readBytes(), File(copia, "nk-pay.db").readBytes())

        // Já na versão atual (ou sem banco), não copia nada.
        assertNull(copiarAntesDeMigrar(arq, copias, versaoAtual = 1, agora = 2000))
        assertNull(copiarAntesDeMigrar(File(pasta.root, "nao-existe.db"), copias, versaoAtual = 2, agora = 3000))

        // Guarda só as 3 mais recentes.
        for (t in 4000L..8000L step 1000) copiarAntesDeMigrar(arq, copias, 2, t)
        assertEquals(3, copias.listFiles()!!.size)
        assertTrue(File(copias, "banco-v1-antes-da-v2-8000").exists())
    }
}
