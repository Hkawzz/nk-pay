package com.hkawzz.nkote.dominio

import java.time.LocalDate

// Modelo de empréstimo (v0.5). Regras: regras-financeiras.md, seção 6E.

data class Emprestimo(
    val id: String,
    /** A conta da pessoa (EMPRESTIMO_RECEBIDO ou EMPRESTIMO_CONCEDIDO). */
    val contaId: String,
    val descricao: String,
    val categoriaId: String?,
    val valorOriginal: Long,
    val dataContratacao: LocalDate,
    val contaPropriaId: String?,
    val anteriorAoApp: Boolean,
    /** Só no concedido anterior ao app (D5). */
    val jurosInformados: Long?,
    val movimentacaoContratacaoId: String?,
    val movimentacaoJurosId: String?,
)
