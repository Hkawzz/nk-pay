package com.hkawzz.nkote.dados

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.dominio.AparenciaSugerida
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.TipoMovimentacao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Passo da aparência sugerida (regra 6C, ADR 0018). Dados fictícios. */
@RunWith(AndroidJUnit4::class)
class AparenciaCategoriasTest {
    private lateinit var banco: NkPayBanco
    private lateinit var categorias: CategoriaRepositorio
    private val relogio = RelogioFalso()

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NkPayBanco::class.java).allowMainThreadQueries().build()
        categorias = CategoriaRepositorio(banco, Auditor(banco.auditoriaDao(), relogio), relogio)
    }

    @After fun fechar() = banco.close()

    /** Categoria como a v0.3 deixava: sem icone e sem cor. */
    private suspend fun categoriaDaV03(id: String, nome: String, arquivada: Boolean = false) =
        banco.categoriaDao().inserir(CategoriaEntidade(id, nome, null, if (arquivada) 5 else null, 1, 1, null))

    @Test
    fun `nomes conhecidos ignoram maiusculas e acentos e os demais ficam neutros`() {
        assertEquals("carrinho", AparenciaSugerida.paraNome("  ALIMENTACAO ")!!.icone)
        assertEquals("saude", AparenciaSugerida.paraNome("Saúde")!!.icone)
        assertEquals(AparenciaSugerida.NEUTRA, AparenciaSugerida.ouNeutra("Pet do vizinho"))
    }

    @Test
    fun `aplica so nas sem icone, audita em um grupo e nao toca em nada financeiro`() = runTest {
        categoriaDaV03("a", "Alimentação")
        categoriaDaV03("b", "Coisas do gato")
        categoriaDaV03("c", "Lazer", arquivada = true)
        // Uma categoria com escolha da pessoa não pode ser sobrescrita.
        banco.categoriaDao().inserir(CategoriaEntidade("d", "Saúde", null, null, 1, 1, null, icone = "estrela", cor = 0xFF60A5FA))
        val contas = ContaRepositorio(banco, Auditor(banco.auditoriaDao(), relogio), relogio)
        val movs = MovimentacaoRepositorio(banco, Auditor(banco.auditoriaDao(), relogio), relogio)
        val pix = (contas.criar(Conta("", "Pix", Natureza.PROPRIO, null, 10_000, LocalDate.of(2026, 12, 1))) as Resultado.Sucesso).valor
        movs.criar(Movimentacao("", TipoMovimentacao.DESPESA, LocalDate.of(2026, 12, 1), 1_000, pix, categoriaId = "a"))
        val financeiroAntes = banco.backupDao().let { listOf(it.contas(), it.movimentacoes(), it.obrigacoes(), it.pagamentos()) }
        val auditoriaAntes = banco.auditoriaDao().listarTodas().size

        assertEquals(3, categorias.aplicarAparenciaSugerida())

        val porId = banco.backupDao().categorias().associateBy { it.id }
        assertEquals("carrinho", porId.getValue("a").icone)
        assertEquals("outros", porId.getValue("b").icone)
        assertEquals("jogo", porId.getValue("c").icone)
        assertEquals("estrela", porId.getValue("d").icone)
        assertEquals(0xFF60A5FA, porId.getValue("d").cor)
        val novos = banco.auditoriaDao().listarTodas().drop(0).filter { it.resumo.startsWith("Aparência sugerida") }
        assertEquals(3, novos.size)
        assertEquals(1, novos.map { it.grupoId }.distinct().size)
        assertEquals(auditoriaAntes + 3, banco.auditoriaDao().listarTodas().size)
        assertEquals(financeiroAntes, banco.backupDao().let { listOf(it.contas(), it.movimentacoes(), it.obrigacoes(), it.pagamentos()) })

        // Idempotente: rodar de novo não faz nada.
        assertEquals(0, categorias.aplicarAparenciaSugerida())
        assertEquals(auditoriaAntes + 3, banco.auditoriaDao().listarTodas().size)
    }

    @Test
    fun `categorias novas ja nascem com aparencia e a aparencia pode ser trocada`() = runTest {
        categorias.garantirCategoriasIniciais()
        assertTrue(categorias.observar().first().all { it.icone != null && it.cor != null })
        assertEquals(0, categorias.aplicarAparenciaSugerida())
        val id = categorias.observar().first().first { it.nome == "Moradia" }.id
        assertTrue(categorias.mudarAparencia(id, "agua", 0xFF22D3EE) is Resultado.Sucesso)
        assertEquals("agua", categorias.observar().first().first { it.id == id }.icone)
        assertTrue(categorias.mudarAparencia(id, "", 0xFF22D3EE) is Resultado.Falha)
    }
}
