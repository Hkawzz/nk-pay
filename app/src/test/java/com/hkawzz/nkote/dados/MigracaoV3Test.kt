package com.hkawzz.nkote.dados

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.VisaoAPagar
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Migração 2 → 3 (v0.4, ADR 0014) e a matriz de backup da proposta (seção 5).
 *
 * O banco v2 é criado EXATAMENTE como a v0.3 o deixa no celular (SQL oficial de
 * app/schemas/.../2.json), preenchido com dados fictícios em todas as tabelas,
 * inclusive contas fixas, obrigações, pagamentos, ajustes e registros excluídos.
 */
@RunWith(AndroidJUnit4::class)
class MigracaoV3Test {

    @get:Rule val pasta = TemporaryFolder()

    private val nome = "migracao-v3-teste.db"
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val arquivo get() = context.getDatabasePath(nome)
    private val tabelasV2 = listOf("conta", "categoria", "movimentacao", "auditoria", "conta_fixa", "obrigacao", "pagamento", "obrigacao_ajuste")
    private val colunasCategoriaV2 = "id, nome, codigoSistema, arquivadaEm, criadoEm, atualizadoEm, excluidoEm"

    @Before fun limpar() { context.deleteDatabase(nome) }
    @After fun apagar() { context.deleteDatabase(nome) }

    private fun esquema(versao: Int): JsonObject =
        Json.parseToJsonElement(File("schemas/com.hkawzz.nkote.dados.NkPayBanco/$versao.json").readText()).jsonObject.getValue("database").jsonObject

    private fun criarBancoV2() {
        val e = esquema(2)
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
            db.version = 2
        }
    }

    /** Dados fictícios em todas as tabelas da v2. */
    private fun preencher(db: SQLiteDatabase) {
        fun conta(id: String, nome: String, natureza: String, beneficio: String?, saldo: Long) = db.execSQL(
            "INSERT INTO conta VALUES (?, ?, ?, ?, ?, '2026-11-22', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, NULL)",
            arrayOf<Any?>(id, nome, natureza, beneficio, saldo),
        )
        conta("pix", "Pix", "PROPRIO", null, 90_000)
        conta("din", "Dinheiro", "PROPRIO", null, 4_500)
        conta("va", "VA", "BENEFICIO", "VA", 31_000)
        db.execSQL("INSERT INTO categoria VALUES ('mor', 'Moradia', NULL, NULL, 1, 1, NULL)")
        db.execSQL("INSERT INTO categoria VALUES ('ali', 'Alimentação', NULL, NULL, 1, 1, NULL)")
        db.execSQL("INSERT INTO categoria VALUES ('velha', 'Categoria velha', NULL, 7, 1, 1, NULL)")
        db.execSQL("INSERT INTO categoria VALUES ('enc', 'Encargos financeiros', 'ENCARGOS_FINANCEIROS', NULL, 1, 1, NULL)")
        fun mov(id: String, tipo: String, data: String, valor: Long, conta: String, destino: String?, cat: String?, excl: Long? = null) = db.execSQL(
            "INSERT INTO movimentacao VALUES (?, ?, ?, ?, ?, ?, ?, 'teste', 2, 2, ?)",
            arrayOf<Any?>(id, tipo, data, valor, conta, destino, cat, excl),
        )
        mov("m1", "ENTRADA", "2026-11-23", 250_000, "pix", null, null)
        mov("m2", "DESPESA", "2026-11-23", 7_300, "va", null, "ali")
        mov("m3", "TRANSFERENCIA", "2026-11-23", 2_000, "pix", "din", null)
        mov("m4", "DESPESA", "2026-11-24", 10_000, "pix", null, "mor") // pagamento da luz
        mov("m5", "DESPESA", "2026-11-24", 400, "pix", null, "enc") // multa da luz
        mov("m6", "AJUSTE", "2026-11-24", -350, "din", null, null)
        mov("m7", "DESPESA", "2026-11-24", 55_555, "pix", null, "ali", excl = 9)
        db.execSQL("INSERT INTO conta_fixa VALUES ('f1', 'Luz', 10000, 10, 'mor', 'pix', NULL, 1, 1, NULL)")
        db.execSQL("INSERT INTO obrigacao VALUES ('o1', 'FIXA', 'Luz', 10000, '2026-11-10', '2026-11', 'f1', 'mor', NULL, NULL, NULL, NULL, NULL, 1, 1, NULL)")
        db.execSQL("INSERT INTO obrigacao VALUES ('o2', 'AVULSA', 'Presente', 8000, '2026-12-05', '2026-12', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, NULL)")
        db.execSQL("INSERT INTO pagamento VALUES ('p1', 'o1', 'm4', 10000, 1, 1, NULL)")
        db.execSQL("INSERT INTO pagamento VALUES ('p2', 'o1', 'm5', 400, 1, 1, NULL)")
        db.execSQL("INSERT INTO obrigacao_ajuste VALUES ('a1', 'o1', 'p1', 'MULTA', 400, 'm5', '', 1, 1, NULL)")
        db.execSQL("INSERT INTO auditoria VALUES ('h1', 'g1', 10, 'CRIACAO', 'conta', 'pix', 'Conta “Pix” criada', NULL, '{}')")
    }

    private fun foto(consulta: (String) -> Cursor, sql: String): List<String> {
        val linhas = mutableListOf<String>()
        consulta(sql).use { c ->
            while (c.moveToNext()) linhas += (0 until c.columnCount).joinToString("|") { i -> "${c.getColumnName(i)}=${if (c.isNull(i)) "NULL" else c.getString(i)}" }
        }
        return linhas
    }

    private fun consultaDe(tabela: String) =
        if (tabela == "categoria") "SELECT $colunasCategoriaV2 FROM categoria ORDER BY id" else "SELECT * FROM $tabela ORDER BY id"

    private fun abrirComoOApp(): NkPayBanco =
        Room.databaseBuilder(context, NkPayBanco::class.java, nome).addMigrations(*MIGRACOES).allowMainThreadQueries().build()

    @Test
    fun `migrar de 2 para 3 preserva todas as linhas campo a campo`() {
        criarBancoV2()
        val antes = SQLiteDatabase.openDatabase(arquivo.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            tabelasV2.associateWith { t -> foto({ db.rawQuery(it, null) }, consultaDe(t)) }
        }
        val banco = abrirComoOApp()
        try {
            val db = banco.openHelper.writableDatabase // migra e valida o esquema contra as entidades v3
            assertEquals(NkPayBanco.VERSAO, db.version) // v0.5: o caminho 2 → 3 → 4 termina na versão atual (ADR 0020)
            // v0.5: a obrigação ganhou emprestimoId no fim, que precisa nascer vazia.
            for (t in tabelasV2) assertEquals("tabela $t mudou", antes.getValue(t), foto({ db.query(it) }, consultaDe(t)).map { it.removeSuffix("|emprestimoId=NULL") })
            // As colunas novas da categoria nascem vazias: a aparência vem depois, auditada.
            db.query("SELECT COUNT(*) FROM categoria WHERE icone IS NOT NULL OR cor IS NOT NULL").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            for (t in listOf("compra", "pagamento_fatura")) {
                db.query("SELECT COUNT(*) FROM $t").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            }
            // O SQL das tabelas novas é idêntico ao do esquema 3 exportado pelo Room.
            val esperado = esquema(3).getValue("entities").jsonArray.map { it.jsonObject }
                .filter { it.getValue("tableName").jsonPrimitive.content in setOf("compra", "pagamento_fatura") }
            for (o in esperado) {
                val t = o.getValue("tableName").jsonPrimitive.content
                val sql = o.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", t).replace("CREATE TABLE IF NOT EXISTS", "CREATE TABLE")
                db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(t)).use { c ->
                    // v0.6: a migração 4 → 5 acrescenta valorOriginalCentavos à compra (ADR 0024).
                    assertTrue(c.moveToFirst()); assertEquals(sql, c.getString(0).replace("`valorOriginalCentavos` INTEGER, ", ""))
                }
            }
        } finally {
            banco.close()
        }
    }

    @Test
    fun `saldos contas a pagar e historico sao os mesmos e abrir de novo nao migra outra vez`() {
        criarBancoV2()
        val banco = abrirComoOApp()
        try {
            val relogio = RelogioFalso()
            val auditor = Auditor(banco.auditoriaDao(), relogio)
            val contas = runBlocking { ContaRepositorio(banco, auditor, relogio).observar().first() }
            val movs = runBlocking { MovimentacaoRepositorio(banco, auditor, relogio).observar().first() }
            val s = Saldos.saldosPorConta(contas, movs)
            assertEquals(90_000L + 250_000 - 2_000 - 10_000 - 400, s["pix"])
            assertEquals(4_500L + 2_000 - 350, s["din"])
            assertEquals(31_000L - 7_300, s["va"])
            val v = VisaoAPagar(
                runBlocking { ContaFixaRepositorio(banco, auditor, relogio).observar().first() },
                runBlocking { ObrigacaoRepositorio(banco, auditor, relogio).observar().first() },
                runBlocking { banco.pagamentoDao().observarValidos().first() }.map { it.paraDominio() },
                runBlocking { banco.ajusteObrigacaoDao().observarValidos().first() }.map { it.paraDominio() },
            )
            assertEquals(0L, v.situacao(v.obrigacao("o1")!!, LocalDate.of(2026, 12, 1)).restante)
            assertEquals(8_000L, v.situacao(v.obrigacao("o2")!!, LocalDate.of(2026, 12, 1)).restante)
            assertEquals(4, runBlocking { CategoriaRepositorio(banco, auditor, relogio).observar().first() }.size)
        } finally {
            banco.close()
        }
        val deNovo = abrirComoOApp()
        try {
            assertEquals(7, runBlocking { deNovo.backupDao().movimentacoes() }.size)
            assertEquals(4, runBlocking { deNovo.backupDao().categorias() }.size)
            assertEquals(1, runBlocking { deNovo.backupDao().auditoria() }.size)
        } finally {
            deNovo.close()
        }
    }

    @Test
    fun `copia do arquivo e feita antes de migrar da v2 para a v3`() {
        criarBancoV2()
        val copias = pasta.newFolder("copias")
        val copia = copiarAntesDeMigrar(arquivo, copias, NkPayBanco.VERSAO, 1000)
        assertTrue(copia!!.name.startsWith("banco-v2-antes-da-v${NkPayBanco.VERSAO}")) // v0.5: a versão atual é a 4
        assertTrue(File(copia, arquivo.name).readBytes().contentEquals(arquivo.readBytes()))
    }

    // ------------------------------------------------------------ matriz de backup

    private fun zip(vararg arquivos: Pair<String, ByteArray>): ByteArray {
        val saida = ByteArrayOutputStream()
        ZipOutputStream(saida).use { z -> for ((n, b) in arquivos) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return saida.toByteArray()
    }

    private fun partes(bytes: ByteArray): Map<String, ByteArray> {
        val m = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { val e = z.nextEntry ?: break; m[e.name] = z.readBytes() } }
        return m
    }

    private fun sha(b: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test
    fun `backup da v0_3 restaura na v0_4 e backup da v0_4 leva compras e abatimentos`() {
        criarBancoV2()
        val banco = abrirComoOApp()
        try {
            val servico = BackupServico(banco, RelogioFalso(), "teste", pasta.newFolder("c"), RegistroDeBackupEmMemoria())
            val bytes = ByteArrayOutputStream().also { runBlocking { servico.exportar(it) } }.toByteArray()
            val p = partes(bytes)

            // 1) Backup como a v0.3 gerava: versaoBanco = 2, sem compras/abatimentos e sem icone/cor.
            val dados = Json.parseToJsonElement(p.getValue("dados.json").toString(Charsets.UTF_8)).jsonObject
            val categoriasV2 = JsonArray(dados.getValue("categorias").jsonArray.map { c -> JsonObject(c.jsonObject.filterKeys { it != "icone" && it != "cor" }) })
            val dadosV2 = JsonObject(dados.filterKeys { it != "compras" && it != "pagamentosDeFatura" } + ("categorias" to categoriasV2))
            val bytesV2 = dadosV2.toString().toByteArray()
            val manifesto = Json.parseToJsonElement(p.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject
            val manifestoV2 = JsonObject(manifesto.filterKeys { it != "compras" } + mapOf("versaoBanco" to JsonPrimitive(2), "sha256Dados" to JsonPrimitive(sha(bytesV2))))
            val antes = runBlocking { banco.backupDao().movimentacoes() to banco.backupDao().pagamentos() }
            val lido = servico.ler(ByteArrayInputStream(zip("manifest.json" to manifestoV2.toString().toByteArray(), "dados.json" to bytesV2)))
            assertEquals(2, lido.manifesto.versaoBanco)
            runBlocking { servico.restaurar(lido) }
            assertEquals(antes, runBlocking { banco.backupDao().movimentacoes() to banco.backupDao().pagamentos() })
            assertEquals(4, runBlocking { banco.backupDao().categorias() }.size)

            // 2) O backup da v0.4 diz versão 3: a v0.3 (versão 2) o recusa pela regra que ela já tem.
            assertEquals(NkPayBanco.VERSAO, servico.ler(ByteArrayInputStream(bytes)).manifesto.versaoBanco) // v0.5: versão atual
            assertTrue(servico.ler(ByteArrayInputStream(bytes)).manifesto.versaoBanco > 2)

            // 3) Compras e abatimentos de fatura vão e voltam (linhas inseridas direto, fictícias).
            runBlocking {
                banco.contaDao().inserir(ContaEntidade("cart", "Cartão", "CARTAO", null, 0, "2026-11-22", null, null, null, 200_000, 5, 12, null, 1, 1, null))
                banco.movimentacaoDao().inserir(MovimentacaoEntidade("mc", "DESPESA", "2026-11-23", 30_000, "cart", null, "ali", "Compra", 1, 1, null))
                banco.movimentacaoDao().inserir(MovimentacaoEntidade("mt", "TRANSFERENCIA", "2026-12-12", 10_000, "pix", "cart", null, "Fatura", 1, 1, null))
                banco.compraDao().inserir(CompraEntidade("c1", "cart", "Compra", "ali", "2026-11-23", 30_000, 3, "mc", false, 1, 1, null))
                banco.pagamentoFaturaDao().inserir(PagamentoFaturaEntidade("pf1", "cart", "2026-12", "mt", 10_000, 1, 1, null))
            }
            val comCredito = ByteArrayOutputStream().also { runBlocking { servico.exportar(it) } }.toByteArray()
            val fotoAntes = runBlocking { banco.backupDao().compras() to banco.backupDao().pagamentosDeFatura() }
            runBlocking { servico.restaurar(servico.ler(ByteArrayInputStream(comCredito))) }
            assertEquals(fotoAntes, runBlocking { banco.backupDao().compras() to banco.backupDao().pagamentosDeFatura() })
            assertEquals(1, servico.ler(ByteArrayInputStream(comCredito)).manifesto.compras)

            // 4) Arquivo adulterado é recusado, e nada muda.
            val pc = partes(comCredito)
            val adulterado = pc.getValue("dados.json").toString(Charsets.UTF_8).replace("30000", "3000")
            val recusou = runCatching { servico.ler(ByteArrayInputStream(zip("manifest.json" to pc.getValue("manifest.json"), "dados.json" to adulterado.toByteArray()))) }
            assertTrue(recusou.exceptionOrNull() is BackupServico.BackupInvalido)
            assertEquals(fotoAntes, runBlocking { banco.backupDao().compras() to banco.backupDao().pagamentosDeFatura() })
        } finally {
            banco.close()
        }
    }
}
