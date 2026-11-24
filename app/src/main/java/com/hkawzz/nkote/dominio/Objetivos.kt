package com.hkawzz.nkote.dominio

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// =====================================================================================
// Objetivos e reservas (v0.7). Regras: regras-financeiras.md, seção 6F; ADR 0029.
// Kotlin puro. Nada é gravado: o guardado é sempre a soma dos movimentos.
// Guardar, retirar, mover e ajustar NÃO são movimentações de dinheiro.
// =====================================================================================

/** Um objetivo com os números calculados. */
data class SituacaoObjetivo(
    val objetivo: Objetivo,
    val guardado: Long,
    /** Do mais novo para o mais antigo. */
    val movimentos: List<MovimentoReserva>,
) {
    /** Só com meta: meta − guardado, nunca abaixo de zero. */
    val falta: Long? get() = objetivo.meta?.let { maxOf(0, it - guardado) }
    /** Só com meta: guardado ÷ meta, em % inteiro (pode passar de 100). */
    val progresso: Int? get() = objetivo.meta?.takeIf { it > 0 }?.let { (guardado * 100 / it).toInt() }
    val metaAtingida: Boolean get() = objetivo.meta != null && guardado >= objetivo.meta

    /** Só com meta e prazo: quanto guardar por mês (informativo). Null se não se aplica ou o prazo venceu. */
    fun porMes(hoje: LocalDate): Long? {
        val f = falta ?: return null
        val prazo = objetivo.prazo ?: return null
        if (f <= 0) return null
        return Objetivos.guardarPorMes(f, prazo, hoje)
    }

    fun prazoVencido(hoje: LocalDate): Boolean = objetivo.prazo?.let { it < hoje } == true && !metaAtingida
}

/** Foto de todos os objetivos, sempre atualizada (como a VisaoFinanceira). */
data class VisaoObjetivos(
    val objetivos: List<Objetivo>,
    val movimentos: List<MovimentoReserva>,
) {
    private val guardados by lazy { movimentos.groupBy { it.objetivoId }.mapValues { (_, l) -> l.sumOf { it.valor } } }
    private val porId by lazy { objetivos.associateBy { it.id } }

    fun objetivo(id: String?): Objetivo? = id?.let { porId[it] }
    fun guardado(objetivoId: String): Long = guardados[objetivoId] ?: 0

    /** Guardado total = Σ objetivos não excluídos (ativos, concluídos e arquivados). */
    val totalGuardado: Long by lazy { objetivos.sumOf { guardado(it.id) } }

    fun situacao(id: String): SituacaoObjetivo? = objetivo(id)?.let { o ->
        SituacaoObjetivo(o, guardado(o.id), movimentos.filter { it.objetivoId == o.id }.sortedWith(compareByDescending<MovimentoReserva> { it.data }.thenByDescending { it.criadoEm }))
    }

    val ativos: List<SituacaoObjetivo> by lazy { objetivos.filter { it.ativo }.mapNotNull { situacao(it.id) } }
    /** Concluídos e arquivados (o guardado deles continua contando). */
    val encerrados: List<SituacaoObjetivo> by lazy { objetivos.filter { !it.ativo }.mapNotNull { situacao(it.id) } }

    /** Análises (6F.6): guardar + ajustes para cima no período, pela data do movimento. Mover não conta. */
    fun guardadoNoPeriodo(periodo: Periodo): Long = movimentos.filter { it.data in periodo && it.valor > 0 &&
        (it.tipo == TipoMovimentoReserva.GUARDAR || it.tipo == TipoMovimentoReserva.AJUSTE) }.sumOf { it.valor }

    /** Análises (6F.6): retirar + ajustes para baixo no período. Mover não conta. */
    fun retiradoNoPeriodo(periodo: Periodo): Long = -movimentos.filter { it.data in periodo && it.valor < 0 &&
        (it.tipo == TipoMovimentoReserva.RETIRAR || it.tipo == TipoMovimentoReserva.AJUSTE) }.sumOf { it.valor }

    companion object {
        val VAZIA = VisaoObjetivos(emptyList(), emptyList())
    }
}

object Objetivos {
    /** Meses do mês atual ao mês do prazo, contando os dois (mínimo 1); null se o prazo já passou. */
    fun mesesAtePrazo(prazo: LocalDate, hoje: LocalDate): Long? {
        if (prazo < hoje) return null
        return ChronoUnit.MONTHS.between(YearMonth.from(hoje), YearMonth.from(prazo)) + 1
    }

    /** falta ÷ meses até o prazo, arredondado para cima (6F.1). */
    fun guardarPorMes(falta: Long, prazo: LocalDate, hoje: LocalDate): Long? {
        val meses = mesesAtePrazo(prazo, hoje) ?: return null
        return (falta + meses - 1) / meses
    }

    /**
     * Desfazer um grupo de movimentos (6F.2): recusado se deixar algum objetivo negativo;
     * se o efeito for AUMENTAR o guardado, segue o bloqueio do Guardar ([disponivel]).
     * @return a mensagem do bloqueio, ou null se pode desfazer.
     */
    fun bloqueioDoDesfazer(grupo: List<MovimentoReserva>, guardadoAtual: (String) -> Long, disponivel: Long): String? {
        for ((objetivoId, l) in grupo.groupBy { it.objetivoId }) {
            if (guardadoAtual(objetivoId) - l.sumOf { it.valor } < 0) return "Desfazer deixaria o objetivo com valor guardado negativo."
        }
        val aumento = -grupo.sumOf { it.valor }
        if (aumento > 0 && aumento > disponivel) {
            return "Desfazer guardaria de novo ${Dinheiro.formatar(aumento)}, mas o disponível é ${Dinheiro.formatar(maxOf(0, disponivel))}."
        }
        return null
    }
}
