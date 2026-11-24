package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.TipoMovimentacao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * v0.8 (ADR 0030): o NKote restaura um backup `.nkpay` gerado pela v0.7 (NK Pay).
 * Os backups novos usam `.nkote`. Valores fictícios.
 */
@RunWith(AndroidJUnit4::class)
class BackupNkpayTest {
    @get:Rule val pasta = TemporaryFolder()
    private val contexto get() = ApplicationProvider.getApplicationContext<Context>()
    private val bancos = mutableListOf<NkPayBanco>()

    private fun novoBanco() = Room.inMemoryDatabaseBuilder(contexto, NkPayBanco::class.java).allowMainThreadQueries().build().also { bancos += it }
    @After fun fechar() = bancos.forEach { it.close() }

    private suspend fun foto(b: NkPayBanco) = b.backupDao().let { listOf(it.contas(), it.categorias(), it.movimentacoes(), it.objetivos(), it.movimentosReserva()) }

    @Test
    fun `backup nkpay da v0_7 restaura no NKote`() = runTest {
        // "NK Pay v0.7": contas, uma despesa e um objetivo.
        val antigo = novoBanco()
        val relogio = RelogioFalso()
        val auditor = Auditor(antigo.auditoriaDao(), relogio)
        CategoriaRepositorio(antigo, auditor, relogio).garantirCategoriasIniciais()
        val contas = ContaRepositorio(antigo, auditor, relogio)
        val pix = (contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, LocalDate.of(2026, 11, 1))) as Resultado.Sucesso).valor
        MovimentacaoRepositorio(antigo, auditor, relogio).criar(Movimentacao("", TipoMovimentacao.DESPESA, LocalDate.of(2026, 11, 20), 2_500, pix, descricao = "Mercado"))
        val bytes = ByteArrayOutputStream().also { BackupServico(antigo, relogio, "0.7.0", pasta.newFolder("a"), RegistroDeBackupEmMemoria()).exportar(it) }.toByteArray()
        // Como a v0.7 gravava: o arquivo se chamava .nkpay e o manifesto trazia a versão do NK Pay.
        val p = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { val e = z.nextEntry ?: break; p[e.name] = z.readBytes() } }
        val man = Json.parseToJsonElement(p.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject
        val arquivo = File(pasta.root, "nk-pay-backup-2026-11-22-1430${BackupServico.EXTENSAO_ANTIGA}")
        ZipOutputStream(arquivo.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json")); z.write(JsonObject(man + ("versaoApp" to JsonPrimitive("0.7.0"))).toString().toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("dados.json")); z.write(p.getValue("dados.json")); z.closeEntry()
        }
        assertTrue(arquivo.name.endsWith(".nkpay"))

        // NKote v0.8: restaura o .nkpay.
        val novo = novoBanco()
        val servico = BackupServico(novo, relogio, "0.8.0", pasta.newFolder("b"), RegistroDeBackupEmMemoria())
        val lido = FileInputStream(arquivo).use { servico.ler(it) }
        assertEquals("0.7.0", lido.manifesto.versaoApp)
        servico.restaurar(lido)
        assertEquals(foto(antigo), foto(novo))
        // Os backups novos usam a extensão nova.
        assertTrue(servico.nomeSugerido().startsWith("nkote-backup-"))
        assertTrue(servico.nomeSugerido().endsWith(".nkote"))
    }
}
