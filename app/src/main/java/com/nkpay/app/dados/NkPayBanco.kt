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
    entities = [ContaEntidade::class, CategoriaEntidade::class, MovimentacaoEntidade::class, AuditoriaEntidade::class],
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
        const val VERSAO = 1
        const val NOME_ARQUIVO = "nk-pay.db"

        fun criar(context: Context): NkPayBanco =
            Room.databaseBuilder(context.applicationContext, NkPayBanco::class.java, NOME_ARQUIVO)
                // Migrações entram aqui, por exemplo: .addMigrations(MIGRACAO_1_2)
                .build()
    }
}
