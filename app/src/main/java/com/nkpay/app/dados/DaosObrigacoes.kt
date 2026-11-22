package com.nkpay.app.dados

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nkpay.app.dominio.AjusteObrigacao
import com.nkpay.app.dominio.ContaFixa
import com.nkpay.app.dominio.NaturezaAjuste
import com.nkpay.app.dominio.Obrigacao
import com.nkpay.app.dominio.OrigemObrigacao
import com.nkpay.app.dominio.Pagamento
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

// Consultas das tabelas da versão 2. Só os repositórios usam (a auditoria passa por eles).

@Dao
interface ContaFixaDao {
    @Query("SELECT * FROM conta_fixa WHERE excluidoEm IS NULL ORDER BY arquivadaEm IS NOT NULL, nome COLLATE NOCASE")
    fun observarTodas(): Flow<List<ContaFixaEntidade>>

    @Query("SELECT * FROM conta_fixa WHERE id = :id")
    suspend fun buscar(id: String): ContaFixaEntidade?

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun inserir(f: ContaFixaEntidade)
    @Update suspend fun atualizar(f: ContaFixaEntidade)
}

@Dao
interface ObrigacaoDao {
    @Query("SELECT * FROM obrigacao WHERE excluidoEm IS NULL AND canceladaEm IS NULL ORDER BY vencimento IS NULL, vencimento, criadoEm")
    fun observarTodas(): Flow<List<ObrigacaoEntidade>>

    @Query("SELECT * FROM obrigacao WHERE id = :id")
    suspend fun buscar(id: String): ObrigacaoEntidade?

    /** Obrigação (não excluída) de uma conta fixa num mês: usada para impedir duplicação. */
    @Query("SELECT COUNT(*) FROM obrigacao WHERE contaFixaId = :contaFixaId AND mesReferencia = :mes AND excluidoEm IS NULL")
    suspend fun contarDaFixaNoMes(contaFixaId: String, mes: String): Int

    @Query("SELECT COUNT(*) FROM pagamento WHERE obrigacaoId = :obrigacaoId AND excluidoEm IS NULL")
    suspend fun contarPagamentos(obrigacaoId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun inserir(o: ObrigacaoEntidade)
    @Update suspend fun atualizar(o: ObrigacaoEntidade)
}

@Dao
interface PagamentoDao {
    /** Pagamentos válidos: não excluídos e cuja movimentação também não foi excluída. */
    @Query(
        """SELECT p.* FROM pagamento p JOIN movimentacao m ON m.id = p.movimentacaoId
           WHERE p.excluidoEm IS NULL AND m.excluidoEm IS NULL""",
    )
    fun observarValidos(): Flow<List<PagamentoEntidade>>

    @Query("SELECT * FROM pagamento WHERE id = :id") suspend fun buscar(id: String): PagamentoEntidade?
    @Query("SELECT * FROM pagamento WHERE movimentacaoId = :movimentacaoId AND excluidoEm IS NULL")
    suspend fun daMovimentacao(movimentacaoId: String): List<PagamentoEntidade>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun inserir(p: PagamentoEntidade)
    @Update suspend fun atualizar(p: PagamentoEntidade)
}

@Dao
interface AjusteObrigacaoDao {
    /** Ajustes válidos: não excluídos e, se têm movimentação (encargo), ela não foi excluída. */
    @Query(
        """SELECT a.* FROM obrigacao_ajuste a LEFT JOIN movimentacao m ON m.id = a.movimentacaoId
           WHERE a.excluidoEm IS NULL AND (a.movimentacaoId IS NULL OR m.excluidoEm IS NULL)""",
    )
    fun observarValidos(): Flow<List<ObrigacaoAjusteEntidade>>

    @Query("SELECT * FROM obrigacao_ajuste WHERE pagamentoId = :pagamentoId AND excluidoEm IS NULL")
    suspend fun doPagamento(pagamentoId: String): List<ObrigacaoAjusteEntidade>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun inserir(a: ObrigacaoAjusteEntidade)
    @Update suspend fun atualizar(a: ObrigacaoAjusteEntidade)
}

// ----------------------------------------------------------------- mapeamento

fun ContaFixaEntidade.paraDominio() = ContaFixa(
    id = id, nome = nome, valorPadrao = valorPadraoCentavos, diaVencimento = diaVencimento,
    categoriaId = categoriaId, contaPadraoId = contaPadraoId, arquivada = arquivadaEm != null,
)

fun ObrigacaoEntidade.paraDominio() = Obrigacao(
    id = id,
    origem = OrigemObrigacao.valueOf(origem),
    descricao = descricao,
    valorPrevisto = valorPrevistoCentavos,
    vencimento = vencimento?.let { LocalDate.parse(it) },
    mesReferencia = mesReferencia?.let { YearMonth.parse(it) },
    contaFixaId = contaFixaId,
    categoriaId = categoriaId,
)

fun PagamentoEntidade.paraDominio() = Pagamento(id, obrigacaoId, movimentacaoId, valorAplicadoCentavos)

fun ObrigacaoAjusteEntidade.paraDominio() =
    AjusteObrigacao(id, obrigacaoId, pagamentoId, NaturezaAjuste.valueOf(natureza), valorCentavos, movimentacaoId)
