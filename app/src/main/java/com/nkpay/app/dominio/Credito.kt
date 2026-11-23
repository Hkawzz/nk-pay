package com.nkpay.app.dominio

import java.time.LocalDate
import java.time.YearMonth

// =====================================================================================
// Domínio de cartões e crediários (v0.4). Regras: regras-financeiras.md, seção 6B.
// Kotlin puro: nada aqui conhece o banco ou a interface. Nada é gravado: devido,
// faturas, restante e status são sempre calculados.
// =====================================================================================

object DatasDeCredito {

    private fun noMes(mes: YearMonth, dia: Int): LocalDate = mes.atDay(minOf(dia, mes.lengthOfMonth()))

    /**
     * 1ª parcela no cartão (regra 6B.3): a compra no dia do fechamento ou depois vai para
     * a fatura seguinte; vence no mesmo mês do fechamento se vencimento > fechamento,
     * senão no mês seguinte.
     */
    fun primeiroVencimentoCartao(dataCompra: LocalDate, diaFechamento: Int, diaVencimento: Int): LocalDate {
        val mesCompra = YearMonth.from(dataCompra)
        val fechamentoNoMes = noMes(mesCompra, diaFechamento)
        val mesFechamento = if (dataCompra < fechamentoNoMes) mesCompra else mesCompra.plusMonths(1)
        val mesVencimento = if (diaVencimento > diaFechamento) mesFechamento else mesFechamento.plusMonths(1)
        return noMes(mesVencimento, diaVencimento)
    }

    /** 1ª parcela no crediário: mês seguinte à compra, no dia de vencimento (ou no dia da compra). */
    fun primeiroVencimentoCrediario(dataCompra: LocalDate, diaVencimento: Int?): LocalDate =
        noMes(YearMonth.from(dataCompra).plusMonths(1), diaVencimento ?: dataCompra.dayOfMonth)

    /** Vencimentos de N parcelas, mês a mês, a partir da 1ª, mantendo o dia [dia] (ajustado ao fim do mês). */
    fun vencimentos(primeiro: LocalDate, n: Int, dia: Int = primeiro.dayOfMonth): List<LocalDate> =
        (0 until n).map { k -> noMes(YearMonth.from(primeiro).plusMonths(k.toLong()), dia) }

    /** total ÷ n em centavos, arredondado para baixo; a ÚLTIMA parcela absorve a diferença. */
    fun valoresDasParcelas(total: Long, n: Int): List<Long> {
        require(n >= 1)
        val base = total / n
        return List(n) { i -> if (i == n - 1) total - base * (n - 1) else base }
    }
}

/** Situação de uma parcela, já considerando a fatura (no cartão). */
data class SituacaoParcela(
    val situacao: SituacaoObrigacao,
    /** Quitada pelo restante da fatura, embora ela mesma ainda tenha restante. */
    val quitadaPelaFatura: Boolean,
) {
    val obrigacao get() = situacao.obrigacao
    val status: StatusObrigacao get() = if (quitadaPelaFatura) StatusObrigacao.QUITADA else situacao.status
    /** Restante que ainda conta (zero se a fatura já foi quitada). */
    val restante: Long get() = if (quitadaPelaFatura) 0 else maxOf(0, situacao.restante)
}

data class Fatura(
    val contaId: String,
    val mes: YearMonth,
    val vencimento: LocalDate?,
    val parcelas: List<SituacaoParcela>,
    /** Soma dos abatimentos gerais (pagamentos parciais sem escolher compras). */
    val abatimentosGerais: Long,
    val restante: Long,
    val status: StatusObrigacao,
) {
    val total: Long get() = parcelas.sumOf { it.situacao.obrigacao.valorPrevisto + it.situacao.acrescimos }
    val pago: Long get() = parcelas.sumOf { it.situacao.pago } + abatimentosGerais
    val emAberto: Boolean get() = restante > 0
}

/** Um pedaço da distribuição de um pagamento: fatura inteira (ligada às parcelas) ou abatimento geral. */
sealed interface ParteDoPagamento {
    val mes: YearMonth
    val valor: Long

    /** Fatura coberta por inteiro: [aplicacoes] = parcela → valor aplicado. */
    data class FaturaInteira(override val mes: YearMonth, val aplicacoes: List<Pair<String, Long>>) : ParteDoPagamento {
        override val valor: Long get() = aplicacoes.sumOf { it.second }
    }

    /** Fatura coberta em parte: abatimento geral. */
    data class AbatimentoGeral(override val mes: YearMonth, override val valor: Long) : ParteDoPagamento
}

data class DistribuicaoDoPagamento(val partes: List<ParteDoPagamento>, val excedente: Long)

/**
 * Foto de cartões e crediários, sempre atualizada (como a VisaoFinanceira).
 * Recebe as listas já sem os registros excluídos/inválidos.
 */
data class VisaoCredito(
    val contas: List<Conta>,
    val movimentacoes: List<Movimentacao>,
    val obrigacoes: List<Obrigacao>,
    val pagamentos: List<Pagamento>,
    val ajustes: List<AjusteObrigacao>,
    val pagamentosFatura: List<PagamentoFatura>,
    val compras: List<Compra>,
) {
    val contasDeCredito: List<Conta> by lazy { contas.filter { it.ehCredito } }
    private val saldos by lazy { Saldos.saldosPorConta(contasDeCredito, movimentacoes) }

    fun conta(id: String?): Conta? = id?.let { i -> contasDeCredito.firstOrNull { it.id == i } }

    /** Devido = −saldo da conta de crédito. */
    fun devido(contaId: String): Long = -(saldos[contaId] ?: 0)

    fun limiteDisponivel(contaId: String): Long? = conta(contaId)?.limite?.let { it - devido(contaId) }

    fun parcelasDa(contaId: String): List<Obrigacao> = obrigacoes.filter { it.contaCreditoId == contaId }
    fun comprasDa(contaId: String): List<Compra> = compras.filter { it.contaCreditoId == contaId }
    fun compra(id: String?): Compra? = id?.let { i -> compras.firstOrNull { it.id == i } }
    fun parcelasDaCompra(compraId: String): List<Obrigacao> = obrigacoes.filter { it.compraId == compraId }.sortedBy { it.numeroParcela }

    /** Faturas do cartão (ou "meses" do crediário), da mais antiga para a mais nova. */
    fun faturas(contaId: String, hoje: LocalDate): List<Fatura> {
        val ehCartao = conta(contaId)?.natureza == Natureza.CARTAO
        return parcelasDa(contaId).groupBy { it.mesReferencia!! }.toSortedMap().map { (mes, ps) ->
            val situacoes = ps.sortedWith(compareBy({ it.vencimento }, { it.numeroParcela })).map { Obrigacoes.situacao(it, pagamentos, ajustes, hoje) }
            val gerais = pagamentosFatura.filter { it.contaCreditoId == contaId && it.mesReferencia == mes }.sumOf { it.valor }
            val restante = situacoes.sumOf { maxOf(0, it.restante) } - gerais
            val quitadaPelaFatura = ehCartao && restante <= 0
            val parcelas = situacoes.map { SituacaoParcela(it, quitadaPelaFatura && it.restante > 0) }
            val vencimento = situacoes.mapNotNull { it.obrigacao.vencimento }.maxOrNull()
            val status = when {
                restante <= 0 -> StatusObrigacao.QUITADA
                vencimento != null && vencimento < hoje -> StatusObrigacao.ATRASADA
                gerais > 0 || situacoes.any { it.pago > 0 } -> StatusObrigacao.PARCIAL
                else -> StatusObrigacao.PENDENTE
            }
            Fatura(contaId, mes, vencimento, parcelas, gerais, maxOf(0, restante), status)
        }
    }

    fun fatura(contaId: String, mes: YearMonth, hoje: LocalDate): Fatura? = faturas(contaId, hoje).firstOrNull { it.mes == mes }

    /** Situação de uma parcela, considerando a fatura dela. */
    fun situacaoDaParcela(obrigacaoId: String, hoje: LocalDate): SituacaoParcela? {
        val o = obrigacoes.firstOrNull { it.id == obrigacaoId } ?: return null
        return fatura(o.contaCreditoId!!, o.mesReferencia!!, hoje)?.parcelas?.firstOrNull { it.obrigacao.id == obrigacaoId }
    }

    /** Σ restante das faturas (cartão) ou parcelas (crediário) em aberto. */
    fun restanteEmAberto(contaId: String, hoje: LocalDate): Long = faturas(contaId, hoje).sumOf { it.restante }

    /** Invariante da regra 6B.1: Devido = Σ restante em aberto. */
    fun invarianteFecha(contaId: String, hoje: LocalDate): Boolean = devido(contaId) == restanteEmAberto(contaId, hoje)

    /** Total das compras anteriores ao app (para as Análises). */
    fun totalComprasAnteriores(contaId: String): Long = comprasDa(contaId).filter { it.anteriorAoApp }.sumOf { it.valorTotal }

    /**
     * Distribui [valor] entre as faturas em aberto até [ateMes] (inclusive), da mais antiga
     * para a mais nova (regra 6B.4). Cada fatura coberta por inteiro é ligada às parcelas
     * (cada uma recebe até o seu restante); a última, coberta em parte, recebe um abatimento
     * geral. O que sobrar é o [DistribuicaoDoPagamento.excedente] (precisa de classificação).
     */
    fun distribuir(contaId: String, valor: Long, ateMes: YearMonth, hoje: LocalDate): DistribuicaoDoPagamento {
        var resto = valor
        val partes = mutableListOf<ParteDoPagamento>()
        for (f in faturas(contaId, hoje).filter { it.emAberto && it.mes <= ateMes }) {
            if (resto <= 0) break
            if (resto >= f.restante) {
                var aCobrir = f.restante
                val aplicacoes = mutableListOf<Pair<String, Long>>()
                for (p in f.parcelas) {
                    if (aCobrir <= 0) break
                    val r = maxOf(0, p.situacao.restante)
                    if (r <= 0) continue
                    val aplicado = minOf(r, aCobrir)
                    aplicacoes += p.obrigacao.id to aplicado
                    aCobrir -= aplicado
                }
                partes += ParteDoPagamento.FaturaInteira(f.mes, aplicacoes)
                resto -= f.restante
            } else {
                partes += ParteDoPagamento.AbatimentoGeral(f.mes, resto)
                resto = 0
            }
        }
        return DistribuicaoDoPagamento(partes, maxOf(0, resto))
    }

    companion object {
        val VAZIA = VisaoCredito(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}
