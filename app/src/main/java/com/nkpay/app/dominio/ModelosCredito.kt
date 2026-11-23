package com.nkpay.app.dominio

import java.time.LocalDate
import java.time.YearMonth

// Modelos de cartões e crediários (v0.4). Regras: regras-financeiras.md, seção 6B.

data class Compra(
    val id: String,
    val contaCreditoId: String,
    val descricao: String,
    val categoriaId: String?,
    val dataCompra: LocalDate,
    val valorTotal: Long,
    val totalParcelas: Int,
    /** A despesa do valor total. */
    val movimentacaoId: String,
    val anteriorAoApp: Boolean,
    /** v0.6 (D-A1 c): o valor original (a despesa). Igual ao total quando não há juros. */
    val valorOriginal: Long = valorTotal,
) {
    val juros: Long get() = valorTotal - valorOriginal
}

/** v0.6: uma pendência de mês anterior levada para aparecer também no [mes] (ADR 0026). */
data class PendenciaLevada(val id: String, val obrigacaoId: String, val mes: YearMonth)

/** Abatimento geral de uma fatura (pagamento parcial sem escolher compras). */
data class PagamentoFatura(
    val id: String,
    val contaCreditoId: String,
    val mesReferencia: YearMonth,
    val movimentacaoId: String,
    val valor: Long,
)
