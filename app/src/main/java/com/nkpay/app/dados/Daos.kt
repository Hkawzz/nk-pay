package com.nkpay.app.dados

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// DAOs: as consultas SQL. Só os repositórios chamam os DAOs, nunca as telas,
// para que toda alteração passe pela auditoria.

@Dao
interface ContaDao {
    @Query("SELECT * FROM conta WHERE excluidoEm IS NULL ORDER BY arquivadaEm IS NOT NULL, nome COLLATE NOCASE")
    fun observarTodas(): Flow<List<ContaEntidade>>

    @Query("SELECT * FROM conta WHERE id = :id")
    suspend fun buscar(id: String): ContaEntidade?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(conta: ContaEntidade)

    @Update
    suspend fun atualizar(conta: ContaEntidade)

    @Query(
        """SELECT COUNT(*) FROM movimentacao
           WHERE excluidoEm IS NULL AND (contaId = :contaId OR contaDestinoId = :contaId)""",
    )
    suspend fun contarMovimentacoes(contaId: String): Int
}

@Dao
interface CategoriaDao {
    @Query("SELECT * FROM categoria WHERE excluidoEm IS NULL ORDER BY arquivadaEm IS NOT NULL, nome COLLATE NOCASE")
    fun observarTodas(): Flow<List<CategoriaEntidade>>

    @Query("SELECT * FROM categoria WHERE id = :id")
    suspend fun buscar(id: String): CategoriaEntidade?

    @Query("SELECT COUNT(*) FROM categoria")
    suspend fun contarTodas(): Int

    @Query("SELECT * FROM categoria WHERE codigoSistema = :codigo LIMIT 1")
    suspend fun buscarPorCodigo(codigo: String): CategoriaEntidade?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(categoria: CategoriaEntidade)

    @Update
    suspend fun atualizar(categoria: CategoriaEntidade)
}

@Dao
interface MovimentacaoDao {
    @Query("SELECT * FROM movimentacao WHERE excluidoEm IS NULL ORDER BY data DESC, criadoEm DESC")
    fun observarTodas(): Flow<List<MovimentacaoEntidade>>

    @Query("SELECT * FROM movimentacao WHERE id = :id")
    suspend fun buscar(id: String): MovimentacaoEntidade?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(movimentacao: MovimentacaoEntidade)

    @Update
    suspend fun atualizar(movimentacao: MovimentacaoEntidade)
}

@Dao
interface AuditoriaDao {
    @Query("SELECT * FROM auditoria ORDER BY quando DESC, id DESC")
    fun observarTodas(): Flow<List<AuditoriaEntidade>>

    @Query("SELECT * FROM auditoria WHERE id = :id")
    suspend fun buscar(id: String): AuditoriaEntidade?

    @Query("SELECT * FROM auditoria ORDER BY quando DESC, id DESC")
    suspend fun listarTodas(): List<AuditoriaEntidade>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(registro: AuditoriaEntidade)
}

/**
 * Usado só pelo backup: lê TUDO (inclusive o que foi excluído logicamente, para o
 * backup ser completo) e substitui tudo de uma vez na restauração.
 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM conta ORDER BY criadoEm, id") suspend fun contas(): List<ContaEntidade>
    @Query("SELECT * FROM categoria ORDER BY criadoEm, id") suspend fun categorias(): List<CategoriaEntidade>
    @Query("SELECT * FROM movimentacao ORDER BY criadoEm, id") suspend fun movimentacoes(): List<MovimentacaoEntidade>
    @Query("SELECT * FROM auditoria ORDER BY quando, id") suspend fun auditoria(): List<AuditoriaEntidade>
    @Query("SELECT * FROM conta_fixa ORDER BY criadoEm, id") suspend fun contasFixas(): List<ContaFixaEntidade>
    @Query("SELECT * FROM obrigacao ORDER BY criadoEm, id") suspend fun obrigacoes(): List<ObrigacaoEntidade>
    @Query("SELECT * FROM pagamento ORDER BY criadoEm, id") suspend fun pagamentos(): List<PagamentoEntidade>
    @Query("SELECT * FROM obrigacao_ajuste ORDER BY criadoEm, id") suspend fun ajustesDeObrigacao(): List<ObrigacaoAjusteEntidade>

    // A ordem importa por causa das chaves estrangeiras: primeiro quem aponta, depois quem é apontado.
    @Query("DELETE FROM obrigacao_ajuste") suspend fun apagarAjustesDeObrigacao()
    @Query("DELETE FROM pagamento") suspend fun apagarPagamentos()
    @Query("DELETE FROM obrigacao") suspend fun apagarObrigacoes()
    @Query("DELETE FROM conta_fixa") suspend fun apagarContasFixas()
    @Query("DELETE FROM movimentacao") suspend fun apagarMovimentacoes()
    @Query("DELETE FROM conta") suspend fun apagarContas()
    @Query("DELETE FROM categoria") suspend fun apagarCategorias()
    @Query("DELETE FROM auditoria") suspend fun apagarAuditoria()

    @Insert suspend fun inserirContas(lista: List<ContaEntidade>)
    @Insert suspend fun inserirCategorias(lista: List<CategoriaEntidade>)
    @Insert suspend fun inserirMovimentacoes(lista: List<MovimentacaoEntidade>)
    @Insert suspend fun inserirAuditoria(lista: List<AuditoriaEntidade>)
    @Insert suspend fun inserirContasFixas(lista: List<ContaFixaEntidade>)
    @Insert suspend fun inserirObrigacoes(lista: List<ObrigacaoEntidade>)
    @Insert suspend fun inserirPagamentos(lista: List<PagamentoEntidade>)
    @Insert suspend fun inserirAjustesDeObrigacao(lista: List<ObrigacaoAjusteEntidade>)
}
