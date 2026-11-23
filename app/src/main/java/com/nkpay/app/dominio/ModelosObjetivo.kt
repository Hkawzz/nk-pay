package com.nkpay.app.dominio

import java.time.LocalDate

// Modelos de objetivos e reservas (v0.7). Regras: regras-financeiras.md, seção 6F.

enum class TipoMovimentoReserva(val rotulo: String) {
    GUARDAR("Guardado"),
    RETIRAR("Retirado"),
    MOVER("Movido"),
    AJUSTE("Ajuste"),
}

data class Objetivo(
    val id: String,
    val nome: String,
    val icone: String? = null,
    val cor: Long? = null,
    /** Opcional: sem meta é uma "caixinha" livre, sem %. */
    val meta: Long? = null,
    val prazo: LocalDate? = null,
    val concluidoEm: Long? = null,
    val arquivado: Boolean = false,
) {
    val concluido: Boolean get() = concluidoEm != null
    /** Ativo = nem concluído nem arquivado. Só se guarda em objetivo ativo. */
    val ativo: Boolean get() = !concluido && !arquivado
}

/** Um movimento de reserva. NÃO é movimentação de dinheiro (regra 6F.2). */
data class MovimentoReserva(
    val id: String,
    val objetivoId: String,
    val data: LocalDate,
    /** + aumenta o guardado, − diminui. */
    val valor: Long,
    val tipo: TipoMovimentoReserva,
    val grupoId: String,
    val criadoEm: Long = 0,
)
