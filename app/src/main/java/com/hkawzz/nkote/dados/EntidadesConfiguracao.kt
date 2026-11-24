package com.hkawzz.nkote.dados

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Tabela nova do esquema versão 7 (v0.8): configuração do backup automático (ADR 0033 e 0034).
// NÃO vai no arquivo de backup: a pasta escolhida é uma permissão deste celular.

/** Uma linha só (id = [ConfiguracaoBackupEntidade.UNICA]). */
@Entity(tableName = "configuracao_backup")
data class ConfiguracaoBackupEntidade(
    @PrimaryKey val id: String,
    /** URI da pasta escolhida pelo seletor do Android (null = ainda não escolhida). */
    val pastaUri: String?,
    /** DIARIO | SEMANAL | MENSAL */
    val frequencia: String,
    val ultimoEm: Long?,
    /** OK | FALHA (null = nunca rodou). */
    val ultimoResultado: String?,
    val ultimaMensagem: String?,
    val atualizadoEm: Long,
) {
    companion object {
        const val UNICA = "unica"
    }
}

@Dao
interface ConfiguracaoBackupDao {
    @Query("SELECT * FROM configuracao_backup WHERE id = 'unica'") fun observar(): Flow<ConfiguracaoBackupEntidade?>
    @Query("SELECT * FROM configuracao_backup WHERE id = 'unica'") suspend fun buscar(): ConfiguracaoBackupEntidade?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvar(c: ConfiguracaoBackupEntidade)
}
