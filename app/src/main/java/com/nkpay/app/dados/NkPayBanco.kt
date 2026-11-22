package com.nkpay.app.dados

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * O banco de dados do app (SQLite, via Room).
 *
 * - exportSchema = true: o esquema de cada versão vai para app/schemas/ (no Git).
 * - NUNCA usar fallbackToDestructiveMigration: isso apagaria os dados reais quando
 *   a versão do banco mudasse. Cada mudança de versão precisa de uma migração
 *   escrita à mão e testada. Ver docs/decisoes/0008-banco-de-dados.md.
 */
@Database(
    entities = [
        // Versão 1
        ContaEntidade::class, CategoriaEntidade::class, MovimentacaoEntidade::class, AuditoriaEntidade::class,
        // Versão 2 (v0.3): só tabelas novas; as da versão 1 não mudam.
        ContaFixaEntidade::class, ObrigacaoEntidade::class, PagamentoEntidade::class, ObrigacaoAjusteEntidade::class,
    ],
    version = NkPayBanco.VERSAO,
    exportSchema = true,
)
abstract class NkPayBanco : RoomDatabase() {
    abstract fun contaDao(): ContaDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun movimentacaoDao(): MovimentacaoDao
    abstract fun auditoriaDao(): AuditoriaDao
    abstract fun backupDao(): BackupDao

    companion object {
        /** Versão do esquema. Também vai no manifesto de cada backup. */
        const val VERSAO = 2
        const val NOME_ARQUIVO = "nk-pay.db"

        fun criar(context: Context): NkPayBanco {
            val app = context.applicationContext
            // Antes de qualquer migração, guarda uma cópia do arquivo do banco como está.
            copiarAntesDeMigrar(
                arquivoBanco = app.getDatabasePath(NOME_ARQUIVO),
                pastaCopias = java.io.File(app.filesDir, "copias-antes-de-migrar"),
                versaoAtual = VERSAO,
                agora = System.currentTimeMillis(),
            )
            return Room.databaseBuilder(app, NkPayBanco::class.java, NOME_ARQUIVO)
                .addMigrations(*MIGRACOES)
                .build()
        }
    }
}
