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
    /** v0.9 (item 7): mudança de composição dentro da caixinha (o total não muda). */
    MOVER_PARTE("Entre partes"),
}

/** Uma movimentação que entra (+) ou sai (−) de uma caixinha. */
data class MovimentoCaixinha(
    val movimentacaoId: String,
    val data: LocalDate,
    val valor: Long,
    val tipo: TipoMovimentoCaixinha,
    /** A outra conta (de onde veio ou para onde foi), se houver. */
    val outraContaId: String?,
    /** v0.9 (item 7): as partes envolvidas (conta de origem; null = "Sem origem definida"). */
    val partes: List<String?> = emptyList(),
    /** v0.9: no movimento entre partes, o grupo (para desfazer). */
    val grupoId: String? = null,
)

// =====================================================================================
// v0.9 (item 7, ADR 0036): PARTES da caixinha, uma por conta de origem. Cada linha diz
// quanto entrou (+) ou saiu (−) de uma parte; o saldo da parte é a soma das linhas
// válidas (nada é gravado como saldo). "Sem origem definida" não tem linhas: é o que
// sobra (saldo da caixinha − partes atribuídas), então o total nunca muda.
// =====================================================================================

/** Uma linha de composição de uma caixinha. [parteContaId] null = "Sem origem definida". */
data class LinhaParte(
    val id: String,
    val caixinhaId: String,
    val parteContaId: String?,
    val valor: Long,
    /** A movimentação que a gerou; null no movimento entre partes. */
    val movimentacaoId: String?,
    val grupoId: String,
    val data: LocalDate,
)

/** O saldo de uma parte. [contaId] null = "Sem origem definida". */
data class SaldoDeParte(val contaId: String?, val valor: Long)

object PartesCaixinha {
    /**
     * Saldos das partes de uma caixinha, na ordem em que cada parte recebeu dinheiro pela
     * primeira vez (da mais antiga para a mais nova). "Sem origem definida" vem por último e
     * só aparece com saldo. Linhas de movimentações que não estão em [movsValidas] não contam.
     */
    fun saldos(caixinhaId: String, saldoDaCaixinha: Long, linhas: List<LinhaParte>, movsValidas: Set<String>): List<SaldoDeParte> {
        val minhas = linhas.filter { it.caixinhaId == caixinhaId && (it.movimentacaoId == null || it.movimentacaoId in movsValidas) }
            .sortedWith(compareBy({ it.data }, { it.id }))
        val porParte = LinkedHashMap<String, Long>()
        for (l in minhas) l.parteContaId?.let { porParte[it] = (porParte[it] ?: 0) + l.valor }
        val atribuido = porParte.values.sum()
        val reais = porParte.filter { it.value != 0L }.map { SaldoDeParte(it.key, it.value) }
        val semOrigem = saldoDaCaixinha - atribuido
        return if (semOrigem != 0L) reais + SaldoDeParte(null, semOrigem) else reais
    }

    /**
     * Divide uma saída de [valor] entre as partes: primeiro a [preferida] (ex.: a conta para
     * onde o dinheiro volta), depois as outras da mais antiga para a mais nova, e por último
     * "Sem origem definida". Usado na migração e quando a parte não é escolhida.
     */
    fun dividirSaida(valor: Long, partes: List<SaldoDeParte>, preferida: String?): List<Pair<String?, Long>> {
        var falta = valor
        val ordem = partes.filter { it.contaId != null && it.contaId == preferida } +
            partes.filter { it.contaId != null && it.contaId != preferida } + partes.filter { it.contaId == null }
        val pedacos = mutableListOf<Pair<String?, Long>>()
        for (p in ordem) {
            if (falta <= 0) break
            val tira = minOf(falta, maxOf(0, p.valor))
            if (tira > 0) { pedacos += p.contaId to tira; falta -= tira }
        }
        if (falta > 0) pedacos += null to falta
        return pedacos
    }

    /** Um movimento de caixinha visto pela recalculação: só o necessário. */
    data class Mov(val id: String, val tipo: TipoMovimentacao, val data: LocalDate, val valor: Long, val contaId: String, val contaDestinoId: String?, val criadoEm: Long)

    /**
     * Recalcula as linhas de TODAS as caixinhas a partir do histórico, em ordem cronológica
     * (migração 8 → 9 e restauração de backups antigos, ADR 0036):
     * guardar de X → parte X; retirada → sai primeiro da parte da conta de destino, o resto
     * das outras (da mais antiga para a mais nova); mover entre caixinhas leva as partes junto;
     * rendimentos e o que não dá para atribuir ficam em "Sem origem definida".
     */
    fun recalcular(caixinhas: Set<String>, movs: List<Mov>, novoId: (Mov, Int) -> String): List<LinhaParte> {
        val linhas = mutableListOf<LinhaParte>()
        val saldos = mutableMapOf<String, LinkedHashMap<String?, Long>>()
        fun partes(cx: String) = saldos.getOrPut(cx) { LinkedHashMap() }
        fun adicionar(m: Mov, cx: String, parte: String?, v: Long) {
            val p = partes(cx); p[parte] = (p[parte] ?: 0) + v
            if (parte != null) linhas += LinhaParte(novoId(m, linhas.size), cx, parte, v, m.id, MIGRACAO, m.data)
        }
        fun saida(m: Mov, cx: String, preferida: String?): List<Pair<String?, Long>> {
            val atuais = partes(cx).entries.filter { it.key != null }.map { SaldoDeParte(it.key, it.value) } +
                SaldoDeParte(null, partes(cx)[null] ?: 0)
            val pedacos = dividirSaida(m.valor, atuais, preferida)
            for ((parte, v) in pedacos) adicionar(m, cx, parte, -v)
            return pedacos
        }
        for (m in movs.sortedWith(compareBy({ it.data }, { it.criadoEm }, { it.id }))) {
            val origemCx = m.contaId in caixinhas
            val destinoCx = m.contaDestinoId != null && m.contaDestinoId in caixinhas
            when {
                m.tipo == TipoMovimentacao.TRANSFERENCIA && origemCx && destinoCx ->
                    for ((parte, v) in saida(m, m.contaId, null)) adicionar(m, m.contaDestinoId!!, parte, v)
                m.tipo == TipoMovimentacao.TRANSFERENCIA && destinoCx -> adicionar(m, m.contaDestinoId!!, m.contaId, m.valor)
                m.tipo == TipoMovimentacao.TRANSFERENCIA && origemCx -> saida(m, m.contaId, m.contaDestinoId)
                origemCx -> {
                    // Entradas (rendimentos) e qualquer outra coisa: sem origem definida.
                    val sinal = when (m.tipo) { TipoMovimentacao.ENTRADA -> 1; TipoMovimentacao.AJUSTE -> 1; else -> -1 }
                    adicionar(m, m.contaId, null, sinal * m.valor)
                }
            }
        }
        return linhas
    }

    const val MIGRACAO = "recalculo-de-partes"
}

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
    /** v0.9 (item 7): linhas de composição das caixinhas (sem as excluídas). */
    val partes: List<LinhaParte> = emptyList(),
) {
    private val porId by lazy { objetivos.associateBy { it.id } }
    private val movsValidas: Set<String> by lazy { movimentacoes.map { it.id }.toSet() }

    /** v0.9 (item 7): as partes da caixinha do objetivo (a soma é sempre o guardado). */
    fun partesDe(objetivoId: String): List<SaldoDeParte> {
        val cx = objetivo(objetivoId)?.contaCaixinhaId ?: return emptyList()
        return PartesCaixinha.saldos(cx, guardado(objetivoId), partes, movsValidas)
    }
    private val caixinhas: Set<String> by lazy { contas.filter { it.natureza == Natureza.CAIXINHA }.map { it.id }.toSet() }
    private val saldos by lazy { Saldos.saldosPorConta(contas.filter { it.id in caixinhas }, movimentacoes) }

    fun objetivo(id: String?): Objetivo? = id?.let { porId[it] }
    fun objetivoDaCaixinha(contaId: String?): Objetivo? = contaId?.let { c -> objetivos.firstOrNull { it.contaCaixinhaId == c } }

    /** Guardado do objetivo = saldo da caixinha (zero se ainda não tem caixinha). */
    fun guardado(objetivoId: String): Long = objetivo(objetivoId)?.contaCaixinhaId?.let { saldos[it] } ?: 0

    /** Guardado total = Σ caixinhas dos objetivos não excluídos (ativos, concluídos e arquivados). */
    val totalGuardado: Long by lazy { objetivos.sumOf { guardado(it.id) } }

    /** As movimentações de uma caixinha, classificadas, na ordem recebida. v0.9: com as partes de cada uma. */
    fun movimentosDaCaixinha(contaId: String): List<MovimentoCaixinha> {
        val minhas = partes.filter { it.caixinhaId == contaId }
        val partesPorMov = minhas.filter { it.movimentacaoId != null }.groupBy({ it.movimentacaoId!! }, { it.parteContaId })
        val daCaixinha = movimentacoes.mapNotNull { m ->
            val pt = partesPorMov[m.id].orEmpty().distinct()
            when {
                m.tipo == TipoMovimentacao.ENTRADA && m.contaId == contaId -> MovimentoCaixinha(m.id, m.data, m.valor, TipoMovimentoCaixinha.RENDIMENTO, null, pt.ifEmpty { listOf(null) })
                m.tipo == TipoMovimentacao.TRANSFERENCIA && m.contaDestinoId == contaId ->
                    MovimentoCaixinha(m.id, m.data, m.valor, if (m.contaId in caixinhas) TipoMovimentoCaixinha.MOVER_ENTRADA else TipoMovimentoCaixinha.GUARDAR, m.contaId, pt)
                m.tipo == TipoMovimentacao.TRANSFERENCIA && m.contaId == contaId ->
                    MovimentoCaixinha(m.id, m.data, -m.valor, if (m.contaDestinoId in caixinhas) TipoMovimentoCaixinha.MOVER_SAIDA else TipoMovimentoCaixinha.RETIRAR, m.contaDestinoId, pt)
                else -> null
            }
        }
        // Movimentos entre partes: um par de linhas sem movimentação (−de, +para), pelo grupo.
        val internos = minhas.filter { it.movimentacaoId == null }.groupBy { it.grupoId }.map { (g, ls) ->
            val de = ls.firstOrNull { it.valor < 0 }
            val para = ls.firstOrNull { it.valor > 0 }
            MovimentoCaixinha(g, ls.first().data, para?.valor ?: -(de?.valor ?: 0), TipoMovimentoCaixinha.MOVER_PARTE, null, listOf(de?.parteContaId, para?.parteContaId), g)
        }
        return daCaixinha + internos
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
