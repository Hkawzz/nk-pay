package com.nkpay.app.dominio

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// =====================================================================================
// Domínio de empréstimos (v0.5). Regras: regras-financeiras.md, seção 6E; ADR 0021.
// Kotlin puro. Nada é gravado: restante, principal, juros não reconhecidos e atraso
// são sempre calculados a partir das parcelas, pagamentos, ajustes e movimentações.
// =====================================================================================

enum class TipoEmprestimo(val rotulo: String) {
    /** Eu devo (J1). */
    RECEBIDO("Recebido"),

    /** Me devem (K2). */
    CONCEDIDO("Concedido"),
    ;

    val natureza: Natureza get() = if (this == RECEBIDO) Natureza.EMPRESTIMO_RECEBIDO else Natureza.EMPRESTIMO_CONCEDIDO

    companion object {
        fun de(n: Natureza): TipoEmprestimo? = when (n) {
            Natureza.EMPRESTIMO_RECEBIDO -> RECEBIDO
            Natureza.EMPRESTIMO_CONCEDIDO -> CONCEDIDO
            else -> null
        }
    }
}

/** Uma parcela com os números calculados e o atraso (regra 6E.7). */
data class ParcelaEmprestimo(val situacao: SituacaoObrigacao, val diasDeAtraso: Int) {
    val obrigacao get() = situacao.obrigacao
    val atrasada: Boolean get() = diasDeAtraso > 0
    val restante: Long get() = maxOf(0, situacao.restante)
}

data class SituacaoEmprestimo(
    val emprestimo: Emprestimo,
    val tipo: TipoEmprestimo,
    val parcelas: List<ParcelaEmprestimo>,
    /** Σ valores previstos das parcelas (com as edições, que são acréscimos e abatimentos). */
    val totalParcelas: Long,
    val jurosPrevistos: Long,
    /** Σ aplicado (pago ou recebido) nas parcelas. */
    val pagoOuRecebido: Long,
    /** Σ restante das parcelas. */
    val restante: Long,
    /**
     * Recebido: a dívida (−efeito das movimentações dele na conta da pessoa).
     * Concedido: o principal a receber (+efeito).
     */
    val saldoNaConta: Long,
    /** Só no concedido: Σ restante − principal (os juros que ainda vão virar receita). */
    val jurosNaoReconhecidos: Long,
    /** Só no concedido: as entradas de juros já recebidas (proporcionais e excedentes). */
    val jurosRecebidos: Long,
) {
    val atrasadas: List<ParcelaEmprestimo> get() = parcelas.filter { it.atrasada }
    val proxima: ParcelaEmprestimo? get() = parcelas.filter { it.restante > 0 }.minByOrNull { it.obrigacao.vencimento ?: LocalDate.MAX }
    val quitado: Boolean get() = restante <= 0
    val temPagamento: Boolean get() = pagoOuRecebido > 0

    /** Invariantes 6E.4 e 6E.5. */
    val invarianteFecha: Boolean get() = when (tipo) {
        TipoEmprestimo.RECEBIDO -> saldoNaConta == restante
        TipoEmprestimo.CONCEDIDO -> jurosNaoReconhecidos in 0..restante
    }
}

object Emprestimos {

    fun jurosPrevistos(totalParcelas: Long, valorOriginal: Long): Long = totalParcelas - valorOriginal

    /**
     * K2: juros de um recebimento de [valor] = valor × (juros não reconhecidos ÷ restante),
     * arredondado. O recebimento que quita o empréstimo ([valor] ≥ restante) reconhece
     * exatamente os juros que faltam (fecha os centavos).
     */
    fun jurosDoRecebimento(valor: Long, restanteTotal: Long, jurosNaoReconhecidos: Long): Long {
        if (valor <= 0 || restanteTotal <= 0 || jurosNaoReconhecidos <= 0) return 0
        if (valor >= restanteTotal) return jurosNaoReconhecidos
        val j = (valor * jurosNaoReconhecidos + restanteTotal / 2) / restanteTotal
        return j.coerceIn(0, minOf(jurosNaoReconhecidos, valor))
    }

    /** Dias de atraso: vencida antes de hoje e com restante. Zero se em dia ou quitada. */
    fun diasDeAtraso(vencimento: LocalDate?, restante: Long, hoje: LocalDate): Int =
        if (vencimento == null || restante <= 0 || !vencimento.isBefore(hoje)) 0 else ChronoUnit.DAYS.between(vencimento, hoje).toInt()

    /** Parcelas automáticas: [quantidade] de [valor], mensais a partir de [primeiro]. */
    fun parcelasAutomaticas(quantidade: Int, valor: Long, primeiro: LocalDate): List<Pair<LocalDate, Long>> =
        DatasDeCredito.vencimentos(primeiro, quantidade).map { it to valor }
}

/** Foto de todos os empréstimos, sempre atualizada (como a VisaoCredito). */
data class VisaoEmprestimos(
    val contas: List<Conta>,
    val movimentacoes: List<Movimentacao>,
    val emprestimos: List<Emprestimo>,
    /** Obrigações (válidas); só as com emprestimoId são usadas. */
    val obrigacoes: List<Obrigacao>,
    val pagamentos: List<Pagamento>,
    val ajustes: List<AjusteObrigacao>,
) {
    private val contasPorId by lazy { contas.associateBy { it.id } }
    private val movsPorId by lazy { movimentacoes.associateBy { it.id } }

    fun conta(id: String?): Conta? = id?.let { contasPorId[it] }
    fun emprestimo(id: String?): Emprestimo? = id?.let { i -> emprestimos.firstOrNull { it.id == i } }
    fun tipo(e: Emprestimo): TipoEmprestimo? = conta(e.contaId)?.natureza?.let { TipoEmprestimo.de(it) }
    fun parcelasDe(id: String): List<Obrigacao> = obrigacoes.filter { it.emprestimoId == id }.sortedWith(compareBy({ it.vencimento }, { it.numeroParcela }))

    /** As movimentações que pertencem ao empréstimo (contratação, juros J1, pagamentos, ajustes). */
    fun movimentacoesDe(e: Emprestimo): List<Movimentacao> {
        val parcelas = parcelasDe(e.id).map { it.id }.toSet()
        val ids = listOfNotNull(e.movimentacaoContratacaoId, e.movimentacaoJurosId) +
            pagamentos.filter { it.obrigacaoId in parcelas }.map { it.movimentacaoId } +
            ajustes.filter { it.obrigacaoId in parcelas }.mapNotNull { it.movimentacaoId }
        return ids.distinct().mapNotNull { movsPorId[it] }
    }

    fun situacao(id: String, hoje: LocalDate): SituacaoEmprestimo? {
        val e = emprestimo(id) ?: return null
        val tipo = tipo(e) ?: return null
        val parcelas = parcelasDe(id).map { o ->
            val s = Obrigacoes.situacao(o, pagamentos, ajustes, hoje)
            ParcelaEmprestimo(s, Emprestimos.diasDeAtraso(o.vencimento, s.restante, hoje))
        }
        val restante = parcelas.sumOf { it.restante }
        val movs = movimentacoesDe(e)
        val efeito = movs.sumOf { Saldos.efeitoNaConta(it, e.contaId) }
        val saldo = if (tipo == TipoEmprestimo.RECEBIDO) -efeito else efeito
        val total = parcelas.sumOf { it.obrigacao.valorPrevisto }
        val principalInicial = if (e.anteriorAoApp) total - (e.jurosInformados ?: 0) else e.valorOriginal
        val idsParcelas = parcelas.map { it.obrigacao.id }.toSet()
        val jurosRecebidos = if (tipo == TipoEmprestimo.CONCEDIDO) {
            ajustes.filter { it.obrigacaoId in idsParcelas && it.movimentacaoId != null }
                .mapNotNull { a -> movsPorId[a.movimentacaoId]?.takeIf { it.tipo == TipoMovimentacao.ENTRADA } }
                .distinctBy { it.id }.sumOf { it.valor }
        } else 0
        return SituacaoEmprestimo(
            emprestimo = e,
            tipo = tipo,
            parcelas = parcelas,
            totalParcelas = total,
            jurosPrevistos = if (e.anteriorAoApp && tipo == TipoEmprestimo.RECEBIDO) 0 else total - principalInicial,
            pagoOuRecebido = parcelas.sumOf { it.situacao.pago },
            restante = restante,
            saldoNaConta = saldo,
            jurosNaoReconhecidos = if (tipo == TipoEmprestimo.CONCEDIDO) restante - saldo else 0,
            jurosRecebidos = jurosRecebidos,
        )
    }

    fun situacoes(tipo: TipoEmprestimo, hoje: LocalDate): List<SituacaoEmprestimo> =
        emprestimos.mapNotNull { situacao(it.id, hoje) }.filter { it.tipo == tipo }

    /** Invariante por conta da pessoa: todo o saldo dela é explicado pelos empréstimos dela. */
    fun invarianteDaConta(contaId: String, hoje: LocalDate): Boolean {
        val c = conta(contaId) ?: return true
        val tipo = TipoEmprestimo.de(c.natureza) ?: return true
        val saldo = Saldos.saldosPorConta(listOf(c), movimentacoes)[contaId] ?: 0
        val doa = emprestimos.filter { it.contaId == contaId }.mapNotNull { situacao(it.id, hoje) }
        return when (tipo) {
            TipoEmprestimo.RECEBIDO -> -saldo == doa.sumOf { it.restante } && doa.all { it.invarianteFecha }
            TipoEmprestimo.CONCEDIDO -> saldo == doa.sumOf { it.saldoNaConta } && doa.all { it.invarianteFecha }
        }
    }

    companion object {
        val VAZIA = VisaoEmprestimos(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}
