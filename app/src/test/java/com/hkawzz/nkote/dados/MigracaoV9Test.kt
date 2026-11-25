package com.hkawzz.nkote.dados

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Objetivo
import com.hkawzz.nkote.dominio.SaldoDeParte
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.VisaoObjetivos
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
import org.junit.Assert.fail
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
 * Migração 8 → 9 (v0.9, item 7, ADR 0036) com um banco v8 FIEL (SQL oficial do esquema 8) e
 * caixinhas com guardar de várias contas, retiradas, rendimentos e mover entre caixinhas.
 * Portão do briefing: o total de cada caixinha é IDÊNTICO antes e depois. Dados fictícios.
 */
@RunWith(AndroidJUnit4::class)
class MigracaoV9Test {
    @get:Rule val pasta = TemporaryFolder()
    private val nome = "migracao-v9-teste.db"
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val arquivo get() = context.getDatabasePath(nome)
    private val tabelasV8 = listOf("conta", "categoria", "movimentacao", "auditoria", "conta_fixa", "obrigacao", "pagamento", "obrigacao_ajuste", "compra", "pagamento_fatura", "emprestimo", "pendencia_levada", "objetivo", "movimento_reserva", "configuracao_backup")

    @Before fun limpar() { context.deleteDatabase(nome) }
    @After fun apagar() { context.deleteDatabase(nome) }

    private fun esquema(v: Int): JsonObject =
        Json.parseToJsonElement(File("schemas/com.hkawzz.nkote.dados.NkPayBanco/$v.json").readText()).jsonObject.getValue("database").jsonObject

    private fun criarBancoV8() {
        val e = esquema(8)
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
            db.version = 8
        }
    }

    /** Pix, Dinheiro e Banco; três caixinhas (uma recebe por "mover"); rendimentos; uma movimentação excluída. */
    private fun preencher(db: SQLiteDatabase) {
        fun conta(id: String, nome: String, natureza: String, saldo: Long) = db.execSQL(
            "INSERT INTO conta (id, nome, natureza, tipoBeneficio, saldoInicialCentavos, dataSaldoInicial, icone, cor, instituicao, limiteCentavos, diaFechamento, diaVencimento, arquivadaEm, criadoEm, atualizadoEm, excluidoEm, ordem) " +
                "VALUES (?, ?, ?, NULL, ?, '2026-11-01', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, NULL, 0)",
            arrayOf<Any?>(id, nome, natureza, saldo),
        )
        conta("pix", "Pix", "PROPRIO", 100_000)
        conta("din", "Dinheiro", "PROPRIO", 50_000)
        conta("ban", "Banco", "PROPRIO", 80_000)
        conta("cx1", "Fone", "CAIXINHA", 0)
        conta("cx2", "Viagem", "CAIXINHA", 0)
        conta("cx3", "Reserva", "CAIXINHA", 0)
        db.execSQL("INSERT INTO categoria VALUES ('ren', 'Rendimentos', 'RENDIMENTOS', NULL, 1, 1, NULL, 'estrela', 4283163008)")
        var n = 0
        fun mov(tipo: String, valor: Long, conta: String, destino: String?, dia: Int, excluido: Boolean = false) = db.execSQL(
            "INSERT INTO movimentacao VALUES (?, ?, ?, ?, ?, ?, ?, 'teste', ?, ?, ?)",
            arrayOf<Any?>("m${++n}", tipo, "2026-11-%02d".format(dia), valor, conta, destino, if (tipo == "ENTRADA") "ren" else null, n, n, if (excluido) 99 else null),
        )
        // Fone: guarda 100 do Dinheiro e 50 do Banco, rende 2, retira 70 para o Banco (50 Banco + 20 Dinheiro).
        mov("TRANSFERENCIA", 10_000, "din", "cx1", 2)
        mov("TRANSFERENCIA", 5_000, "ban", "cx1", 3)
        mov("ENTRADA", 200, "cx1", null, 4)
        mov("TRANSFERENCIA", 7_000, "cx1", "ban", 5)
        // Viagem: guarda 300 do Pix, rende 5, move 100 para a Reserva; um guardar excluído não conta.
        mov("TRANSFERENCIA", 30_000, "pix", "cx2", 6)
        mov("ENTRADA", 500, "cx2", null, 7)
        mov("TRANSFERENCIA", 10_000, "cx2", "cx3", 8)
        mov("TRANSFERENCIA", 9_999, "din", "cx2", 9, excluido = true)
        db.execSQL("INSERT INTO objetivo (id, nome, icone, cor, metaCentavos, prazo, concluidoEm, arquivado, criadoEm, atualizadoEm, excluidoEm, contaCaixinhaId) VALUES ('o1', 'Fone', NULL, NULL, 30000, NULL, NULL, 0, 1, 1, NULL, 'cx1')")
        db.execSQL("INSERT INTO objetivo (id, nome, icone, cor, metaCentavos, prazo, concluidoEm, arquivado, criadoEm, atualizadoEm, excluidoEm, contaCaixinhaId) VALUES ('o2', 'Viagem', NULL, NULL, NULL, NULL, NULL, 0, 1, 1, NULL, 'cx2')")
        db.execSQL("INSERT INTO objetivo (id, nome, icone, cor, metaCentavos, prazo, concluidoEm, arquivado, criadoEm, atualizadoEm, excluidoEm, contaCaixinhaId) VALUES ('o3', 'Reserva', NULL, NULL, NULL, NULL, NULL, 0, 1, 1, NULL, 'cx3')")
        db.execSQL("INSERT INTO auditoria VALUES ('h1', 'g1', 10, 'CRIACAO', 'conta', 'pix', 'Conta “Pix” criada', NULL, '{}')")
    }

    private fun foto(c: Cursor): List<String> {
        val l = mutableListOf<String>()
        c.use { while (it.moveToNext()) l += (0 until it.columnCount).joinToString("|") { i -> "${it.getColumnName(i)}=${if (it.isNull(i)) "NULL" else it.getString(i)}" } }
        return l
    }

    private fun abrirComoOApp(): NkPayBanco = Room.databaseBuilder(context, NkPayBanco::class.java, nome).addMigrations(*MIGRACOES).allowMainThreadQueries().build()

    private fun visao(banco: NkPayBanco): VisaoObjetivos = runBlocking {
        val cs = banco.backupDao().contas().filter { it.excluidoEm == null }.map { it.paraDominio() }
        val ms = banco.backupDao().movimentacoes().filter { it.excluidoEm == null }.map { it.paraDominio() }
        VisaoObjetivos(banco.objetivoDao().validos().map { it.paraDominio() }, emptyList(), cs, ms, banco.parteCaixinhaDao().validas().map { it.paraDominio() })
    }

    private val esperado = mapOf(
        "o1" to listOf(SaldoDeParte("din", 8_000), SaldoDeParte(null, 200)),
        "o2" to listOf(SaldoDeParte("pix", 20_000), SaldoDeParte(null, 500)),
        "o3" to listOf(SaldoDeParte("pix", 10_000)),
    )

    @Test
    fun `migrar de 8 para 9 recalcula as partes e o total de cada caixinha fica identico`() {
        criarBancoV8()
        val antes = SQLiteDatabase.openDatabase(arquivo.path, null, SQLiteDatabase.OPEN_READONLY).use { db -> tabelasV8.associateWith { t -> foto(db.rawQuery("SELECT * FROM $t ORDER BY 1", null)) } }
        val banco = abrirComoOApp()
        try {
            val db = banco.openHelper.writableDatabase
            assertEquals(NkPayBanco.VERSAO, db.version)
            // Nenhuma tabela existente muda.
            for (t in tabelasV8) assertEquals("tabela $t mudou", antes.getValue(t), foto(db.query("SELECT * FROM $t ORDER BY 1")))
            val v = visao(banco)
            val totais = Saldos.saldosPorConta(v.contas, v.movimentacoes)
            // Total de cada caixinha: o mesmo de antes (Fone 82,00; Viagem 205,00; Reserva 100,00), e a soma das partes bate.
            assertEquals(8_200L, totais["cx1"]); assertEquals(20_500L, totais["cx2"]); assertEquals(10_000L, totais["cx3"])
            for ((o, partes) in esperado) {
                assertEquals("partes de $o", partes, v.partesDe(o))
                assertEquals("soma de $o", v.guardado(o), v.partesDe(o).sumOf { it.valor })
            }
            // Para o RELATORIO: quantas caixinhas ficaram com "Sem origem definida".
            assertEquals(2, listOf("o1", "o2", "o3").count { o -> v.partesDe(o).any { it.contaId == null } })
        } finally {
            banco.close()
        }
    }

    @Test
    fun `matriz de backup - nkote da v0_8 recalcula, v0_9 ida e volta, v0_8 recusa v0_9`() {
        criarBancoV8()
        val banco = abrirComoOApp()
        try {
            val r = RelogioFalso()
            val servico = BackupServico(banco, r, "teste", pasta.newFolder("copias"), RegistroDeBackupEmMemoria())
            val objetivos = ObjetivoServico(banco, Auditor(banco.auditoriaDao(), r), r)
            // Uma mudança de composição feita na v0.9 (não dá para recalcular: precisa ir no backup).
            runBlocking { objetivos.moverEntrePartes("o3", "pix", "ban", 4_000, LocalDate.of(2026, 12, 1)) }
            val bytes = ByteArrayOutputStream().also { runBlocking { servico.exportar(it) } }.toByteArray()
            val lido = servico.ler(ByteArrayInputStream(bytes))
            assertEquals(2, lido.manifesto.formato)
            assertEquals(9, lido.manifesto.versaoBanco)
            assertTrue(lido.manifesto.partesCaixinha > 0)

            // v0.9 → v0.9: ida e volta sem perda (inclusive o movimento entre partes).
            val partesAntes = visao(banco).partesDe("o3")
            runBlocking { objetivos.moverEntrePartes("o3", "ban", "pix", 1_000, LocalDate.of(2026, 12, 1)) }
            runBlocking { servico.restaurar(lido) }
            assertEquals(partesAntes, visao(banco).partesDe("o3"))
            assertEquals(listOf(SaldoDeParte("pix", 6_000), SaldoDeParte("ban", 4_000)), partesAntes)

            // Como a v0.8 gerava: formato 1, versão 8, sem partes → recalculadas na restauração.
            val p = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { val e = z.nextEntry ?: break; p[e.name] = z.readBytes() } }
            val dados = Json.parseToJsonElement(p.getValue("dados.json").toString(Charsets.UTF_8)).jsonObject
            val dadosV8 = JsonObject(dados.filterKeys { it != "partesCaixinha" }).toString().toByteArray()
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(dadosV8).joinToString("") { "%02x".format(it) }
            val man = Json.parseToJsonElement(p.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject
            val manV8 = JsonObject(man.filterKeys { it != "partesCaixinha" } + mapOf("formato" to JsonPrimitive(1), "versaoBanco" to JsonPrimitive(8), "sha256Dados" to JsonPrimitive(sha)))
            val zip = ByteArrayOutputStream().also { s -> ZipOutputStream(s).use { z ->
                z.putNextEntry(ZipEntry("manifest.json")); z.write(manV8.toString().toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("dados.json")); z.write(dadosV8); z.closeEntry()
            } }.toByteArray()
            runBlocking { servico.restaurar(servico.ler(ByteArrayInputStream(zip))) }
            val v = visao(banco)
            for ((o, partes) in esperado) assertEquals("partes de $o", partes, v.partesDe(o))

            // A v0.8 (formato 1, banco 8) recusa o backup da v0.9.
            if (lido.manifesto.formato <= 1 || lido.manifesto.versaoBanco <= 8) fail("o backup da v0.9 precisa ser recusado pela v0.8")
        } finally {
            banco.close()
        }
    }
}
