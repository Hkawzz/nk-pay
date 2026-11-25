package com.hkawzz.nkote.dados

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
        // Versão 3 (v0.4): compras e abatimentos de fatura; categoria ganha icone e cor (ADR 0014).
        CompraEntidade::class, PagamentoFaturaEntidade::class,
        // Versão 4 (v0.5): empréstimos; obrigacao ganha emprestimoId (ADR 0020).
        EmprestimoEntidade::class,
        // Versão 5 (v0.6): pendências levadas; compra ganha valorOriginalCentavos (ADR 0024).
        PendenciaLevadaEntidade::class,
        // Versão 6 (v0.7): objetivos e movimentos de reserva; só tabelas novas (ADR 0028).
        ObjetivoEntidade::class, MovimentoReservaEntidade::class,
        // Versão 7 (v0.8): objetivo ganha contaCaixinhaId; configuração do backup automático (ADR 0033).
        ConfiguracaoBackupEntidade::class,
        // Versão 9 (v0.9): partes das caixinhas.
        ParteCaixinhaEntidade::class,
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
    abstract fun contaFixaDao(): ContaFixaDao
    abstract fun obrigacaoDao(): ObrigacaoDao
    abstract fun pagamentoDao(): PagamentoDao
    abstract fun ajusteObrigacaoDao(): AjusteObrigacaoDao
    abstract fun compraDao(): CompraDao
    abstract fun pagamentoFaturaDao(): PagamentoFaturaDao
    abstract fun emprestimoDao(): EmprestimoDao
    abstract fun pendenciaLevadaDao(): PendenciaLevadaDao
    abstract fun objetivoDao(): ObjetivoDao
    abstract fun movimentoReservaDao(): MovimentoReservaDao
    abstract fun configuracaoBackupDao(): ConfiguracaoBackupDao
    abstract fun parteCaixinhaDao(): ParteCaixinhaDao

    companion object {
        /** Versão do esquema. Também vai no manifesto de cada backup. */
        const val VERSAO = 9
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
