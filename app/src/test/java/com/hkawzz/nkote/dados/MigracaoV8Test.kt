package com.hkawzz.nkote.dados

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

/**
 * Migração 7 → 8 (v0.9, item 5, ADR 0035): `conta.ordem`. As contas existentes recebem a
 * posição em que aparecem hoje (alfabética), e nenhuma outra coluna muda. Dados fictícios.
 */
@RunWith(AndroidJUnit4::class)
class MigracaoV8Test {
    @get:Rule val pasta = TemporaryFolder()
    private val nome = "migracao-v8-teste.db"
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val arquivo get() = context.getDatabasePath(nome)

    @Before fun limpar() { context.deleteDatabase(nome) }
    @After fun apagar() { context.deleteDatabase(nome) }

    private fun esquema(v: Int): JsonObject =
        Json.parseToJsonElement(File("schemas/com.hkawzz.nkote.dados.NkPayBanco/$v.json").readText()).jsonObject.getValue("database").jsonObject

    private fun criarBancoV7() {
        val e = esquema(7)
        arquivo.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(arquivo, null).use { db ->
            for (ent in e.getValue("entities").jsonArray) {
                val o = ent.jsonObject
                val t = o.getValue("tableName").jsonPrimitive.content
                db.execSQL(o.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", t))
                for (i in (o["indices"] as? JsonArray).orEmpty()) db.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", t))
            }
            for (q in e.getValue("setupQueries").jsonArray) db.execSQL(q.jsonPrimitive.content)
            fun conta(id: String, nome: String, natureza: String, saldo: Long) = db.execSQL(
                "INSERT INTO conta VALUES (?, ?, ?, NULL, ?, '2026-11-01', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, NULL)",
                arrayOf<Any?>(id, nome, natureza, saldo),
            )
            // Inseridas fora da ordem alfabética, com maiúsculas misturadas.
            conta("z", "Zeta Banco", "PROPRIO", 10_000)
            conta("p", "pix", "PROPRIO", 20_000)
            conta("b", "Bradesco", "PROPRIO", 30_000)
            conta("va", "Alelo VA", "BENEFICIO", 5_000)
            db.execSQL("INSERT INTO categoria VALUES ('ali', 'Alimentação', NULL, NULL, 1, 1, NULL, 'carrinho', 4283163008)")
            db.execSQL("INSERT INTO movimentacao VALUES ('m1', 'DESPESA', '2026-11-25', 1000, 'p', NULL, 'ali', 'teste', 2, 2, NULL)")
            db.version = 7
        }
    }

    private fun foto(c: Cursor): List<String> {
        val l = mutableListOf<String>()
        c.use { while (it.moveToNext()) l += (0 until it.columnCount).joinToString("|") { i -> "${it.getColumnName(i)}=${if (it.isNull(i)) "NULL" else it.getString(i)}" } }
        return l
    }

    private fun abrirComoOApp(): NkPayBanco = Room.databaseBuilder(context, NkPayBanco::class.java, nome).addMigrations(*MIGRACOES).allowMainThreadQueries().build()

    @Test
    fun `migrar de 7 para 8 da a ordem de hoje e nao muda mais nada`() {
        criarBancoV7()
        val antes = SQLiteDatabase.openDatabase(arquivo.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            listOf("conta", "movimentacao").associateWith { foto(db.rawQuery("SELECT * FROM $it ORDER BY id", null)) }
        }
        val banco = abrirComoOApp()
        try {
            val db = banco.openHelper.writableDatabase
            assertEquals(8, db.version)
            assertEquals(antes.getValue("movimentacao"), foto(db.query("SELECT * FROM movimentacao ORDER BY id")))
            assertEquals(antes.getValue("conta"), foto(db.query("SELECT * FROM conta ORDER BY id")).map { it.substringBeforeLast("|ordem=") })
            // Ordem 0 em todas ("ainda não reordenada"): a lista fica na ordem alfabética de hoje.
            assertEquals(listOf("ordem=0"), foto(db.query("SELECT DISTINCT ordem FROM conta")))
            val r = RelogioFalso()
            val contas = ContaRepositorio(banco, Auditor(banco.auditoriaDao(), r), r)
            assertEquals(listOf("Alelo VA", "Bradesco", "pix", "Zeta Banco"), runBlocking { contas.observar().first() }.map { it.nome })
        } finally {
            banco.close()
        }
    }

    @Test
    fun `reordenar grava so o que mudou, fica na auditoria e vai no backup`() = runBlocking {
        val banco = Room.inMemoryDatabaseBuilder(context, NkPayBanco::class.java).allowMainThreadQueries().build()
        try {
            val r = RelogioFalso()
            val contas = ContaRepositorio(banco, Auditor(banco.auditoriaDao(), r), r)
            fun criar(n: String) = runBlocking { (contas.criar(Conta("", n, Natureza.PROPRIO, null, 0, LocalDate.of(2026, 11, 1))) as Resultado.Sucesso).valor }
            val a = criar("Banco")
            val b = criar("Pix")
            val c = criar("Carteira")
            // Sem ordem escolhida, continua alfabética (como antes da v0.9).
            assertEquals(listOf("Banco", "Carteira", "Pix"), contas.observar().first().map { it.nome })
            val auditoriaAntes = banco.auditoriaDao().listarTodas().size
            contas.reordenar(listOf(c, a, b))
            assertEquals(listOf("Carteira", "Banco", "Pix"), contas.observar().first().map { it.nome })
            val novas = banco.auditoriaDao().listarTodas().size - auditoriaAntes
            assertEquals(3, novas)
            assertTrue(banco.auditoriaDao().listarTodas().first().resumo.startsWith("Ordem das contas"))
            contas.reordenar(listOf(c, a, b)) // de novo: nada muda
            assertEquals(3, banco.auditoriaDao().listarTodas().size - auditoriaAntes)
            // Depois de reordenar, a conta nova vai para o fim (não para a posição alfabética).
            criar("Abc")
            assertEquals(listOf("Carteira", "Banco", "Pix", "Abc"), contas.observar().first().map { it.nome })
            contas.reordenar(listOf(c, a, b))

            // Ida e volta do backup mantém a ordem.
            val backup = BackupServico(banco, r, "teste", pasta.newFolder("copias"), RegistroDeBackupEmMemoria())
            val bytes = ByteArrayOutputStream().also { backup.exportar(it) }.toByteArray()
            contas.reordenar(listOf(b, a, c))
            backup.restaurar(backup.ler(ByteArrayInputStream(bytes)))
            assertEquals(listOf("Carteira", "Banco", "Pix", "Abc"), contas.observar().first().map { it.nome })
        } finally {
            banco.close()
        }
    }
}
