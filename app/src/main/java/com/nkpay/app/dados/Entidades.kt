package com.nkpay.app.dados

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// =====================================================================================
// Tabelas do banco (versão 1 do esquema).
//
// ⚠️ Mudar qualquer coisa aqui depois da v0.1.0 exige uma MIGRAÇÃO (o celular já terá
// dados reais). O esquema gerado fica em app/schemas/ e é versionado no Git.
//
// Convenções (docs/regras-financeiras.md, seção 1):
// - id: UUID em texto.
// - dinheiro: centavos (INTEGER).
// - datas de lançamento/vencimento: texto ISO "AAAA-MM-DD" (sem hora, ordenável).
// - instantes (criadoEm, atualizadoEm, excluidoEm, arquivadaEm, quando): milissegundos
//   desde 1970 (UTC). Só a auditoria e os controles usam data e hora.
// - excluidoEm != null: exclusão lógica (o registro some das telas e dos cálculos).
//
// @Serializable: a auditoria guarda o registro inteiro, antes e depois, em JSON.
// =====================================================================================

@Serializable
@Entity(tableName = "conta")
data class ContaEntidade(
    @PrimaryKey val id: String,
    val nome: String,
    /** PROPRIO | BENEFICIO | CARTAO | CREDIARIO */
    val natureza: String,
    /** VA | VR | OUTRO (só natureza BENEFICIO) */
    val tipoBeneficio: String?,
    val saldoInicialCentavos: Long,
    val dataSaldoInicial: String,
    val icone: String?,
    val cor: Long?,
    // --- Reservadas para cartão/crediário (telas na v0.4) ---
    val instituicao: String?,
    val limiteCentavos: Long?,
    val diaFechamento: Int?,
    val diaVencimento: Int?,
    // ---------------------------------------------------------
    val arquivadaEm: Long?,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
)

@Serializable
@Entity(tableName = "categoria")
data class CategoriaEntidade(
    @PrimaryKey val id: String,
    val nome: String,
    /** Só nas categorias do sistema, ex.: ENCARGOS_FINANCEIROS. */
    @ColumnInfo(index = true) val codigoSistema: String?,
    val arquivadaEm: Long?,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
)

@Serializable
@Entity(
    tableName = "movimentacao",
    foreignKeys = [
        // RESTRICT: o banco recusa apagar fisicamente uma conta/categoria que ainda
        // tem movimentações. (No app, a exclusão é sempre lógica.)
        ForeignKey(entity = ContaEntidade::class, parentColumns = ["id"], childColumns = ["contaId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ContaEntidade::class, parentColumns = ["id"], childColumns = ["contaDestinoId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoriaEntidade::class, parentColumns = ["id"], childColumns = ["categoriaId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("contaId"), Index("contaDestinoId"), Index("categoriaId"), Index("data")],
)
data class MovimentacaoEntidade(
    @PrimaryKey val id: String,
    /** ENTRADA | DESPESA | TRANSFERENCIA | AJUSTE */
    val tipo: String,
    val data: String,
    /** Positivo, exceto no AJUSTE (positivo aumenta, negativo diminui o saldo). */
    val valorCentavos: Long,
    /** Na transferência, é a conta de origem. */
    val contaId: String,
    val contaDestinoId: String?,
    val categoriaId: String?,
    val descricao: String,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
)

/**
 * Registro de alterações. Uma linha por registro alterado. Uma operação que mexe
 * em vários registros (no futuro, "pagar fatura") usa o mesmo grupoId em todas as linhas.
 * Esta tabela nunca é editada pelo app; só a restauração de um backup a substitui inteira.
 */
@Serializable
@Entity(tableName = "auditoria", indices = [Index("quando"), Index("grupoId"), Index("entidadeId")])
data class AuditoriaEntidade(
    @PrimaryKey val id: String,
    val grupoId: String,
    val quando: Long,
    /** CRIACAO | EDICAO | EXCLUSAO | RESTAURACAO */
    val operacao: String,
    /** conta | categoria | movimentacao | backup */
    val entidade: String,
    val entidadeId: String,
    /** Frase curta para a lista, ex.: "Conta “Pix” arquivada". */
    val resumo: String,
    /** JSON do registro antes da alteração (null na criação). */
    val antes: String?,
    /** JSON do registro depois da alteração. */
    val depois: String?,
)
