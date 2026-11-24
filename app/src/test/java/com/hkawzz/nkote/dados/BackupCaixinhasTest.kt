package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Objetivo
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/** v0.8 (matriz de backup): ida e volta do `.nkote` com caixinhas. Valores fictícios. */
@RunWith(AndroidJUnit4::class)
class BackupCaixinhasTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var banco: NkPayBanco
    private val relogio = RelogioFalso()
    private val dia = LocalDate.of(2026, 12, 1)

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
    }

    @After fun fechar() = banco.close()

    private fun texto(sql: String): String? = banco.openHelper.readableDatabase.query(sql).use { if (it.moveToFirst()) it.getString(0) else null }
    private fun numero(sql: String): Long = banco.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getLong(0) }

    @Test
    fun `backup da v0_8 leva as caixinhas e o dinheiro guardado de volta`() = runTest {
        val auditor = Auditor(banco.auditoriaDao(), relogio)
        CategoriaRepositorio(banco, auditor, relogio).garantirCategoriasIniciais()
        val pix = (ContaRepositorio(banco, auditor, relogio).criar(Conta("", "Pix", Natureza.PROPRIO, null, 100_000, dia)) as Resultado.Sucesso).valor
        val objetivos = ObjetivoServico(banco, auditor, relogio)
        val fone = (objetivos.criar(Objetivo("", "Fone", meta = 30_000)) as Resultado.Sucesso).valor
        assert(objetivos.guardar(fone, pix, 20_000, dia) is Resultado.Sucesso)
        val backup = BackupServico(banco, relogio, "0.8.0", tmp.newFolder("copias"), RegistroDeBackupEmMemoria())

        val caixinha = texto("SELECT contaCaixinhaId FROM objetivo")
        assertNotNull(caixinha)
        val movsAntes = numero("SELECT COUNT(*) FROM movimentacao")
        val arquivo = ByteArrayOutputStream().also { backup.exportar(it) }.toByteArray()

        // Depois do backup: guarda mais e cria outro objetivo.
        assert(objetivos.guardar(fone, pix, 5_000, dia) is Resultado.Sucesso)
        objetivos.criar(Objetivo("", "Viagem"))

        backup.restaurar(backup.ler(ByteArrayInputStream(arquivo)))
        assertEquals(1L, numero("SELECT COUNT(*) FROM objetivo"))
        assertEquals(caixinha, texto("SELECT contaCaixinhaId FROM objetivo"))
        assertEquals(Natureza.CAIXINHA.name, texto("SELECT natureza FROM conta WHERE id = '$caixinha'"))
        assertEquals(movsAntes, numero("SELECT COUNT(*) FROM movimentacao"))
    }
}
