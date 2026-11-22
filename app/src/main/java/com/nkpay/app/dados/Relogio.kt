package com.nkpay.app.dados

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Fonte de "agora" e de ids. Nos testes, é trocada por uma versão previsível. */
interface Relogio {
    /** Milissegundos desde 1970 (UTC). */
    fun agora(): Long

    /** A data de hoje no fuso do celular. */
    fun hoje(): LocalDate

    fun novoId(): String
}

object RelogioDoSistema : Relogio {
    override fun agora(): Long = System.currentTimeMillis()
    override fun hoje(): LocalDate = LocalDate.now(ZoneId.systemDefault())
    override fun novoId(): String = UUID.randomUUID().toString()
}
