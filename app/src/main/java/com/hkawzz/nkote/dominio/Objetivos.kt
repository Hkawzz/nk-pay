package com.hkawzz.nkote.dominio

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// =====================================================================================
// Objetivos como CAIXINHAS (v0.8, regra 6G.2, ADR 0032). Kotlin puro. Nada é gravado:
// o guardado de um objetivo é o SALDO da conta CAIXINHA dele, calculado pelas
// movimentações. Os movimentos de reserva da v0.7 ("carimbo") ficam só para leitura.
// =====================================================================================

enum class TipoMovimentoCaixinha(val rotulo: String) {
    GUARDAR("Guardado"),
    RETIRAR("Retirado"),
    MOVER_SAIDA("Movido"),
    MOVER_ENTRADA("Recebido"),
    RENDIMENTO("Rendimento"),
}

/** Uma movimentação que entra (+) ou sai (−) de uma caixinha. */
data class MovimentoCaixinha(
    val movimentacaoId: String,
    val data: LocalDate,
    val valor: Long,
    val tipo: TipoMovimentoCaixinha,
    /** A outra conta (de onde veio ou para onde foi), se houver. */
    val outraContaId: String?,
)

/** Um objetivo com os números calculados. */
data class SituacaoObjetivo(
    val objetivo: Objetivo,
    /** O saldo da caixinha. */
    val guardado: Long,
    /** Do mais novo para o mais antigo. */
    val movimentos: List<MovimentoCaixinha>,
    /** v0.7: os movimentos de reserva de antes das caixinhas, só para leitura (V3). */
    val antigos: List<MovimentoReserva> = emptyList(),
) {
    /** Só com meta: meta − guardado, nunca abaixo de zero. */
    val falta: Long? get() = objetivo.meta?.let { maxOf(0, it - guardado) }
    /** Só com meta: guardado ÷ meta, em % inteiro arredondado para baixo (pode passar de 100). */
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

/**
 * Foto dos objetivos. [contas] e [movimentacoes] são as mesmas da VisaoFinanceira (sem os
 * excluídos); [antigos] são os movimentos de reserva da v0.7, que não contam mais.
 */
data class VisaoObjetivos(
    val objetivos: List<Objetivo>,
    val antigos: List<MovimentoReserva> = emptyList(),
    val contas: List<Conta> = emptyList(),
    val movimentacoes: List<Movimentacao> = emptyList(),
) {
    private val porId by lazy { objetivos.associateBy { it.id } }
    private val caixinhas: Set<String> by lazy { contas.filter { it.natureza == Natureza.CAIXINHA }.map { it.id }.toSet() }
    private val saldos by lazy { Saldos.saldosPorConta(contas.filter { it.id in caixinhas }, movimentacoes) }

    fun objetivo(id: String?): Objetivo? = id?.let { porId[it] }
    fun objetivoDaCaixinha(contaId: String?): Objetivo? = contaId?.let { c -> objetivos.firstOrNull { it.contaCaixinhaId == c } }

    /** Guardado do objetivo = saldo da caixinha (zero se ainda não tem caixinha). */
    fun guardado(objetivoId: String): Long = objetivo(objetivoId)?.contaCaixinhaId?.let { saldos[it] } ?: 0

    /** Guardado total = Σ caixinhas dos objetivos não excluídos (ativos, concluídos e arquivados). */
    val totalGuardado: Long by lazy { objetivos.sumOf { guardado(it.id) } }

    /** As movimentações de uma caixinha, classificadas, na ordem recebida. */
    fun movimentosDaCaixinha(contaId: String): List<MovimentoCaixinha> = movimentacoes.mapNotNull { m ->
        when {
            m.tipo == TipoMovimentacao.ENTRADA && m.contaId == contaId -> MovimentoCaixinha(m.id, m.data, m.valor, TipoMovimentoCaixinha.RENDIMENTO, null)
            m.tipo == TipoMovimentacao.TRANSFERENCIA && m.contaDestinoId == contaId ->
                MovimentoCaixinha(m.id, m.data, m.valor, if (m.contaId in caixinhas) TipoMovimentoCaixinha.MOVER_ENTRADA else TipoMovimentoCaixinha.GUARDAR, m.contaId)
            m.tipo == TipoMovimentacao.TRANSFERENCIA && m.contaId == contaId ->
                MovimentoCaixinha(m.id, m.data, -m.valor, if (m.contaDestinoId in caixinhas) TipoMovimentoCaixinha.MOVER_SAIDA else TipoMovimentoCaixinha.RETIRAR, m.contaDestinoId)
            else -> null
        }
    }

    fun situacao(id: String): SituacaoObjetivo? = objetivo(id)?.let { o ->
        SituacaoObjetivo(
            o, guardado(o.id),
            // As movimentações já chegam da mais recente para a mais antiga; o sort é estável.
            o.contaCaixinhaId?.let { movimentosDaCaixinha(it) }.orEmpty().sortedByDescending { it.data },
            antigos.filter { it.objetivoId == o.id }.sortedWith(compareByDescending<MovimentoReserva> { it.data }.thenByDescending { it.criadoEm }),
        )
    }

    val ativos: List<SituacaoObjetivo> by lazy { objetivos.filter { it.ativo }.mapNotNull { situacao(it.id) } }
    /** Concluídos e arquivados (o dinheiro deles continua nas caixinhas). */
    val encerrados: List<SituacaoObjetivo> by lazy { objetivos.filter { !it.ativo }.mapNotNull { situacao(it.id) } }

    /** Há objetivos com histórico de antes das caixinhas (para a mensagem da V3). */
    val temHistoricoAntigo: Boolean get() = antigos.any { a -> objetivos.any { it.id == a.objetivoId } }

    private fun noPeriodo(periodo: Periodo, tipo: TipoMovimentoCaixinha): Long =
        objetivos.mapNotNull { it.contaCaixinhaId }.flatMap { movimentosDaCaixinha(it) }.filter { it.tipo == tipo && it.data in periodo }.sumOf { kotlin.math.abs(it.valor) }

    /** Análises (6G.2): o que entrou nas caixinhas vindo das contas. Mover não conta. */
    fun guardadoNoPeriodo(periodo: Periodo): Long = noPeriodo(periodo, TipoMovimentoCaixinha.GUARDAR)
    /** Análises: o que voltou das caixinhas para as contas. */
    fun retiradoNoPeriodo(periodo: Periodo): Long = noPeriodo(periodo, TipoMovimentoCaixinha.RETIRAR)
    /** Análises: rendimentos (são entrada, receita). */
    fun rendimentosNoPeriodo(periodo: Periodo): Long = noPeriodo(periodo, TipoMovimentoCaixinha.RENDIMENTO)

    companion object {
        val VAZIA = VisaoObjetivos(emptyList())
    }
}

object Objetivos {
    /** Meses do mês atual ao mês do prazo, contando os dois (mínimo 1); null se o prazo já passou. */
    fun mesesAtePrazo(prazo: LocalDate, hoje: LocalDate): Long? {
        if (prazo < hoje) return null
        return ChronoUnit.MONTHS.between(YearMonth.from(hoje), YearMonth.from(prazo)) + 1
    }

    /** falta ÷ meses até o prazo, arredondado para cima (6F.1, mantida na 6G). */
    fun guardarPorMes(falta: Long, prazo: LocalDate, hoje: LocalDate): Long? {
        val meses = mesesAtePrazo(prazo, hoje) ?: return null
        return (falta + meses - 1) / meses
    }
}
