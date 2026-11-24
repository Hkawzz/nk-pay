package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.time.LocalDate

/** v0.8 (V2, regra 6G.6, ADR 0034): backup automático. Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class BackupAutomaticoTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso()
    private lateinit var backup: BackupServico
    private lateinit var servico: BackupAutomaticoServico

    /** Uma pasta de verdade no disco, como a do seletor do Android. */
    private class PastaNoDisco(val dir: File) : PastaDeBackup {
        override fun listar() = dir.listFiles().orEmpty().map { ArquivoDeBackup(it.name, it.name) }
        override fun criar(nome: String): OutputStream = File(dir, nome).outputStream()
        override fun apagar(arquivo: ArquivoDeBackup) { File(dir, arquivo.id).delete() }
    }

    private class PastaQuebrada : PastaDeBackup {
        override fun listar() = emptyList<ArquivoDeBackup>()
        override fun criar(nome: String): OutputStream = error("sem espaço")
        override fun apagar(arquivo: ArquivoDeBackup) = Unit
    }

    @Before
    fun abrir() = runTest {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        ContaRepositorio(banco, auditor, relogio).criar(Conta("", "Pix", Natureza.PROPRIO, null, 10_000, LocalDate.of(2026, 11, 1)))
        backup = BackupServico(banco, relogio, "0.8.0", tmp.newFolder("copias"), RegistroDeBackupEmMemoria())
        servico = BackupAutomaticoServico(banco, relogio, backup)
    }

    @After fun fechar() = banco.close()

    @Test
    fun `diario por padrao guarda os ultimos 7 da serie e nunca apaga os manuais`() = runTest {
        assertEquals(FrequenciaBackup.DIARIO, servico.atual().frequencia)
        assertNull(servico.atual().ultimoOk)
        val dir = tmp.newFolder("drive")
        File(dir, "nkote-backup-2026-11-30-0900.nkote").writeText("manual")
        File(dir, "nk-pay-backup-2026-11-01-0900.nkpay").writeText("manual antigo")
        val pasta = PastaNoDisco(dir)
        servico.escolherPasta("content://teste/pasta")
        repeat(9) { assertTrue(servico.executar(pasta)) }
        val nomes = dir.list()!!.sorted()
        assertEquals(7, nomes.count { it.startsWith(BackupAutomaticoServico.PREFIXO) })
        assertTrue("nkote-backup-2026-11-30-0900.nkote" in nomes)
        assertTrue("nk-pay-backup-2026-11-01-0900.nkpay" in nomes)
        // O arquivo gravado é um backup válido.
        val ultimo = nomes.filter { it.startsWith(BackupAutomaticoServico.PREFIXO) }.max()
        assertTrue(ultimo.endsWith(".nkote"))
        assertEquals(1, FileInputStream(File(dir, ultimo)).use { backup.ler(it) }.manifesto.contas)
        val c = servico.atual()
        assertEquals(true, c.ultimoOk)
        assertEquals("content://teste/pasta", c.pastaUri)
        // Ele conta como backup feito (o aviso de 7 dias da Início some).
        assertTrue(backup.registro.ultimo.first() != null)
    }

    @Test
    fun `falha fica registrada com a mensagem e frequencia muda`() = runTest {
        assertFalse(servico.executar(null))
        assertTrue(servico.atual().falhou)
        assertTrue(servico.atual().ultimaMensagem!!.contains("Escolha a pasta"))
        assertFalse(servico.executar(PastaQuebrada()))
        assertTrue(servico.atual().ultimaMensagem!!.contains("sem espaço"))
        servico.mudarFrequencia(FrequenciaBackup.MENSAL)
        assertEquals(FrequenciaBackup.MENSAL, servico.observar().first().frequencia)
        assertTrue(servico.executar(PastaNoDisco(tmp.newFolder("ok"))))
        assertFalse(servico.atual().falhou)
    }
}
