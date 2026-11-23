package com.nkpay.app.dados

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// =====================================================================================
// Tabelas novas do esquema versão 3 (v0.4): compras parceladas e abatimentos gerais de
// fatura (docs/decisoes/0014-banco-v3.md). Mesmas convenções das versões 1 e 2.
// =====================================================================================

/**
 * Uma compra no cartão ou no crediário. Ela gera UMA despesa pelo valor total
 * ([movimentacaoId], na conta de crédito, na data da compra) e N obrigações-parcela
 * (obrigacao.compraId = id). `anteriorAoApp`: compra feita antes de usar o app (ADR 0016).
 */
@Serializable
@Entity(
    tableName = "compra",
    foreignKeys = [
        ForeignKey(entity = ContaEntidade::class, parentColumns = ["id"], childColumns = ["contaCreditoId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoriaEntidade::class, parentColumns = ["id"], childColumns = ["categoriaId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MovimentacaoEntidade::class, parentColumns = ["id"], childColumns = ["movimentacaoId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("contaCreditoId"), Index("categoriaId"), Index("movimentacaoId")],
)
data class CompraEntidade(
    @PrimaryKey val id: String,
    val contaCreditoId: String,
    val descricao: String,
    val categoriaId: String?,
    val dataCompra: String,
    val valorTotalCentavos: Long,
    val totalParcelas: Int,
    /** A despesa do valor total. */
    val movimentacaoId: String,
    val anteriorAoApp: Boolean,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
)

/**
 * Abatimento GERAL de uma fatura: um pagamento parcial que pertence à fatura (cartão +
 * mês de referência) e não a uma compra. A movimentação é a transferência que pagou.
 */
@Serializable
@Entity(
    tableName = "pagamento_fatura",
    foreignKeys = [
        ForeignKey(entity = ContaEntidade::class, parentColumns = ["id"], childColumns = ["contaCreditoId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MovimentacaoEntidade::class, parentColumns = ["id"], childColumns = ["movimentacaoId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("contaCreditoId", "mesReferencia"), Index("movimentacaoId")],
)
data class PagamentoFaturaEntidade(
    @PrimaryKey val id: String,
    val contaCreditoId: String,
    /** "AAAA-MM" */
    val mesReferencia: String,
    val movimentacaoId: String,
    val valorCentavos: Long,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
)
