package com.hkawzz.nkote.ui

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatação de datas em português do Brasil. */
object Formatos {
    private val ptBR: Locale = Locale.forLanguageTag("pt-BR")
    private val dataCurta = DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBR)
    private val diaComSemana = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ptBR)
    private val mesAno = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBR)
    private val dataHora = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm:ss", ptBR)

    /** 22/11/2026 */
    fun data(d: LocalDate): String = d.format(dataCurta)

    /** "Hoje", "Ontem" ou "Sábado, 21 de novembro" (com o ano se não for o atual). */
    fun cabecalhoDoDia(d: LocalDate, hoje: LocalDate): String = when (d) {
        hoje -> "Hoje"
        hoje.minusDays(1) -> "Ontem"
        else -> {
            val texto = d.format(diaComSemana).replaceFirstChar { it.titlecase(ptBR) }
            if (d.year == hoje.year) texto else "$texto de ${d.year}"
        }
    }

    /** "Novembro de 2026" */
    fun mes(m: YearMonth): String = m.atDay(1).format(mesAno).replaceFirstChar { it.titlecase(ptBR) }

    /** 22/11/2026 às 14:03:12 (no fuso do celular) */
    fun dataHora(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dataHora)

    // O DatePicker do Material trabalha com milissegundos em UTC à meia-noite.
    fun paraMillisUtc(d: LocalDate): Long = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun deMillisUtc(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
