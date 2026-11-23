package com.nkpay.app.dados

import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

// =====================================================================================
// Migrações do banco. Cada uma é escrita à mão e testada (MigracaoTest).
// NUNCA usar fallbackToDestructiveMigration: o app tem dados financeiros reais.
//
// O SQL de cada tabela é copiado de app/schemas/.../<versão>.json (gerado pelo Room),
// para o resultado ser idêntico ao de um banco criado do zero na versão nova.
// =====================================================================================

/**
 * 1 → 2 (v0.3): cria as tabelas de contas fixas, obrigações, pagamentos e ajustes.
 * Só ADICIONA tabelas: nenhuma linha das tabelas existentes é lida, alterada ou copiada.
 * O Room roda a migração dentro de uma transação: se algo falhar, nada muda.
 */
val MIGRACAO_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `conta_fixa` (`id` TEXT NOT NULL, `nome` TEXT NOT NULL, `valorPadraoCentavos` INTEGER NOT NULL, " +
                "`diaVencimento` INTEGER, `categoriaId` TEXT, `contaPadraoId` TEXT, `arquivadaEm` INTEGER, `criadoEm` INTEGER NOT NULL, " +
                "`atualizadoEm` INTEGER NOT NULL, `excluidoEm` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`categoriaId`) REFERENCES `categoria`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`contaPadraoId`) REFERENCES `conta`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_conta_fixa_categoriaId` ON `conta_fixa` (`categoriaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_conta_fixa_contaPadraoId` ON `conta_fixa` (`contaPadraoId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `obrigacao` (`id` TEXT NOT NULL, `origem` TEXT NOT NULL, `descricao` TEXT NOT NULL, " +
                "`valorPrevistoCentavos` INTEGER NOT NULL, `vencimento` TEXT, `mesReferencia` TEXT, `contaFixaId` TEXT, `categoriaId` TEXT, " +
                "`contaCreditoId` TEXT, `compraId` TEXT, `numeroParcela` INTEGER, `totalParcelas` INTEGER, `canceladaEm` INTEGER, " +
                "`criadoEm` INTEGER NOT NULL, `atualizadoEm` INTEGER NOT NULL, `excluidoEm` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`contaFixaId`) REFERENCES `conta_fixa`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`categoriaId`) REFERENCES `categoria`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`contaCreditoId`) REFERENCES `conta`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_obrigacao_contaFixaId_mesReferencia` ON `obrigacao` (`contaFixaId`, `mesReferencia`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_obrigacao_categoriaId` ON `obrigacao` (`categoriaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_obrigacao_contaCreditoId` ON `obrigacao` (`contaCreditoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_obrigacao_vencimento` ON `obrigacao` (`vencimento`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_obrigacao_mesReferencia` ON `obrigacao` (`mesReferencia`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pagamento` (`id` TEXT NOT NULL, `obrigacaoId` TEXT NOT NULL, `movimentacaoId` TEXT NOT NULL, " +
                "`valorAplicadoCentavos` INTEGER NOT NULL, `criadoEm` INTEGER NOT NULL, `atualizadoEm` INTEGER NOT NULL, `excluidoEm` INTEGER, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`obrigacaoId`) REFERENCES `obrigacao`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`movimentacaoId`) REFERENCES `movimentacao`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pagamento_obrigacaoId` ON `pagamento` (`obrigacaoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pagamento_movimentacaoId` ON `pagamento` (`movimentacaoId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `obrigacao_ajuste` (`id` TEXT NOT NULL, `obrigacaoId` TEXT NOT NULL, `pagamentoId` TEXT, " +
                "`natureza` TEXT NOT NULL, `valorCentavos` INTEGER NOT NULL, `movimentacaoId` TEXT, `observacao` TEXT NOT NULL, " +
                "`criadoEm` INTEGER NOT NULL, `atualizadoEm` INTEGER NOT NULL, `excluidoEm` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`obrigacaoId`) REFERENCES `obrigacao`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`pagamentoId`) REFERENCES `pagamento`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`movimentacaoId`) REFERENCES `movimentacao`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_obrigacao_ajuste_obrigacaoId` ON `obrigacao_ajuste` (`obrigacaoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_obrigacao_ajuste_pagamentoId` ON `obrigacao_ajuste` (`pagamentoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_obrigacao_ajuste_movimentacaoId` ON `obrigacao_ajuste` (`movimentacaoId`)")
    }
}

/**
 * 2 → 3 (v0.4, ADR 0014): cria `compra` e `pagamento_fatura` e acrescenta `icone` e `cor`
 * (vazias) à tabela `categoria`. É a primeira alteração numa tabela existente: o
 * `ADD COLUMN` do SQLite não reescreve as linhas, então os dados atuais ficam idênticos.
 * A aparência sugerida NÃO é aplicada aqui (é um passo auditado do app, ADR 0018).
 */
val MIGRACAO_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `categoria` ADD COLUMN `icone` TEXT")
        db.execSQL("ALTER TABLE `categoria` ADD COLUMN `cor` INTEGER")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `compra` (`id` TEXT NOT NULL, `contaCreditoId` TEXT NOT NULL, `descricao` TEXT NOT NULL, " +
                "`categoriaId` TEXT, `dataCompra` TEXT NOT NULL, `valorTotalCentavos` INTEGER NOT NULL, `totalParcelas` INTEGER NOT NULL, " +
                "`movimentacaoId` TEXT NOT NULL, `anteriorAoApp` INTEGER NOT NULL, `criadoEm` INTEGER NOT NULL, `atualizadoEm` INTEGER NOT NULL, " +
                "`excluidoEm` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`contaCreditoId`) REFERENCES `conta`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`categoriaId`) REFERENCES `categoria`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`movimentacaoId`) REFERENCES `movimentacao`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_compra_contaCreditoId` ON `compra` (`contaCreditoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_compra_categoriaId` ON `compra` (`categoriaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_compra_movimentacaoId` ON `compra` (`movimentacaoId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pagamento_fatura` (`id` TEXT NOT NULL, `contaCreditoId` TEXT NOT NULL, `mesReferencia` TEXT NOT NULL, " +
                "`movimentacaoId` TEXT NOT NULL, `valorCentavos` INTEGER NOT NULL, `criadoEm` INTEGER NOT NULL, `atualizadoEm` INTEGER NOT NULL, " +
                "`excluidoEm` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`contaCreditoId`) REFERENCES `conta`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`movimentacaoId`) REFERENCES `movimentacao`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pagamento_fatura_contaCreditoId_mesReferencia` ON `pagamento_fatura` (`contaCreditoId`, `mesReferencia`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pagamento_fatura_movimentacaoId` ON `pagamento_fatura` (`movimentacaoId`)")
    }
}

/**
 * 3 → 4 (v0.5, ADR 0020): cria emprestimo e acrescenta emprestimoId (vazia, com
 * índice) à tabela obrigacao. Nenhuma linha existente é reescrita. A categoria
 * "Juros recebidos" NÃO é criada aqui (é um passo auditado do app).
 */
val MIGRACAO_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `obrigacao` ADD COLUMN `emprestimoId` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_obrigacao_emprestimoId` ON `obrigacao` (`emprestimoId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `emprestimo` (`id` TEXT NOT NULL, `contaId` TEXT NOT NULL, `descricao` TEXT NOT NULL, `categoriaId` TEXT, " +
                "`valorOriginalCentavos` INTEGER NOT NULL, `dataContratacao` TEXT NOT NULL, `contaPropriaId` TEXT, `anteriorAoApp` INTEGER NOT NULL, " +
                "`jurosInformadosCentavos` INTEGER, `movimentacaoContratacaoId` TEXT, `movimentacaoJurosId` TEXT, `criadoEm` INTEGER NOT NULL, " +
                "`atualizadoEm` INTEGER NOT NULL, `excluidoEm` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`contaId`) REFERENCES `conta`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`categoriaId`) REFERENCES `categoria`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`contaPropriaId`) REFERENCES `conta`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`movimentacaoContratacaoId`) REFERENCES `movimentacao`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`movimentacaoJurosId`) REFERENCES `movimentacao`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_emprestimo_contaId` ON `emprestimo` (`contaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_emprestimo_categoriaId` ON `emprestimo` (`categoriaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_emprestimo_contaPropriaId` ON `emprestimo` (`contaPropriaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_emprestimo_movimentacaoContratacaoId` ON `emprestimo` (`movimentacaoContratacaoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_emprestimo_movimentacaoJurosId` ON `emprestimo` (`movimentacaoJurosId`)")
    }
}

/** Todas as migrações, em ordem. */
val MIGRACOES = arrayOf(MIGRACAO_1_2, MIGRACAO_2_3, MIGRACAO_3_4)

/**
 * Rede de segurança: ANTES de o Room migrar, copia o arquivo do banco (e os arquivos
 * de diário WAL/SHM) para uma pasta à parte. Se a migração falhar no celular, os dados
 * originais continuam intactos nessa cópia e podem ser recuperados.
 *
 * Só age quando o banco existe e está numa versão mais antiga que [versaoAtual].
 * Mantém as 3 cópias mais recentes.
 * @return a pasta da cópia feita, ou null se não precisou copiar.
 */
fun copiarAntesDeMigrar(arquivoBanco: File, pastaCopias: File, versaoAtual: Int, agora: Long): File? {
    if (!arquivoBanco.exists()) return null
    val versaoNoArquivo = try {
        SQLiteDatabase.openDatabase(arquivoBanco.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
    } catch (_: Exception) {
        return null
    }
    if (versaoNoArquivo <= 0 || versaoNoArquivo >= versaoAtual) return null

    val destino = File(pastaCopias, "banco-v$versaoNoArquivo-antes-da-v$versaoAtual-$agora")
    destino.mkdirs()
    for (sufixo in listOf("", "-wal", "-shm")) {
        val origem = File(arquivoBanco.path + sufixo)
        if (origem.exists()) origem.copyTo(File(destino, origem.name), overwrite = true)
    }
    pastaCopias.listFiles { f -> f.isDirectory && f.name.startsWith("banco-v") }
        ?.sortedByDescending { it.name.substringAfterLast('-').toLongOrNull() ?: 0 }
        ?.drop(3)
        ?.forEach { it.deleteRecursively() }
    return destino
}
