package com.nkpay.app.dados

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Consultas das tabelas da versão 3 (v0.4). Só os repositórios e serviços usam.

@Dao
interface CompraDao {
    /** Compras válidas: não excluídas e com a despesa ainda existente. */
    @Query(
        """SELECT c.* FROM compra c JOIN movimentacao m ON m.id = c.movimentacaoId
           WHERE c.excluidoEm IS NULL AND m.excluidoEm IS NULL ORDER BY c.dataCompra DESC, c.criadoEm DESC""",
    )
    fun observarValidas(): Flow<List<CompraEntidade>>

    @Query("SELECT * FROM compra WHERE id = :id") suspend fun buscar(id: String): CompraEntidade?

    @Query("SELECT COUNT(*) FROM compra WHERE movimentacaoId = :movimentacaoId AND excluidoEm IS NULL")
    suspend fun contarDaMovimentacao(movimentacaoId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun inserir(c: CompraEntidade)
    @Update suspend fun atualizar(c: CompraEntidade)
}

@Dao
interface PendenciaLevadaDao {
    @Query("SELECT p.* FROM pendencia_levada p JOIN obrigacao o ON o.id = p.obrigacaoId WHERE p.excluidoEm IS NULL AND o.excluidoEm IS NULL")
    fun observarValidas(): Flow<List<PendenciaLevadaEntidade>>

    @Query("SELECT COUNT(*) FROM pendencia_levada WHERE obrigacaoId = :obrigacaoId AND mes = :mes AND excluidoEm IS NULL")
    suspend fun contar(obrigacaoId: String, mes: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun inserir(p: PendenciaLevadaEntidade)
}

@Dao
interface EmprestimoDao {
    @Query("SELECT * FROM emprestimo WHERE excluidoEm IS NULL ORDER BY dataContratacao DESC, criadoEm DESC")
    fun observarValidos(): Flow<List<EmprestimoEntidade>>

    @Query("SELECT * FROM emprestimo WHERE id = :id") suspend fun buscar(id: String): EmprestimoEntidade?

    /** Empréstimos (não excluídos) cuja contratação ou juros J1 é esta movimentação. */
    @Query("SELECT COUNT(*) FROM emprestimo WHERE excluidoEm IS NULL AND (movimentacaoContratacaoId = :movimentacaoId OR movimentacaoJurosId = :movimentacaoId)")
    suspend fun contarDaMovimentacao(movimentacaoId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun inserir(e: EmprestimoEntidade)
    @Update suspend fun atualizar(e: EmprestimoEntidade)
}

@Dao
interface PagamentoFaturaDao {
    /** Abatimentos gerais válidos: não excluídos e com a transferência ainda existente. */
    @Query(
        """SELECT p.* FROM pagamento_fatura p JOIN movimentacao m ON m.id = p.movimentacaoId
           WHERE p.excluidoEm IS NULL AND m.excluidoEm IS NULL""",
    )
    fun observarValidos(): Flow<List<PagamentoFaturaEntidade>>

    @Query(
        """SELECT p.* FROM pagamento_fatura p JOIN movimentacao m ON m.id = p.movimentacaoId
           WHERE p.contaCreditoId = :contaId AND p.excluidoEm IS NULL AND m.excluidoEm IS NULL""",
    )
    suspend fun validosDaConta(contaId: String): List<PagamentoFaturaEntidade>

    @Query("SELECT * FROM pagamento_fatura WHERE movimentacaoId = :movimentacaoId AND excluidoEm IS NULL")
    suspend fun daMovimentacao(movimentacaoId: String): List<PagamentoFaturaEntidade>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun inserir(p: PagamentoFaturaEntidade)
    @Update suspend fun atualizar(p: PagamentoFaturaEntidade)
}
