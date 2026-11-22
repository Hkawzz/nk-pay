package com.nkpay.app.dominio

import java.time.LocalDate
import java.time.YearMonth

// Contas a pagar (v0.3). Regras: docs/regras-financeiras.md, seção 6A, e ADR 0013.
// Kotlin puro: nada aqui conhece o banco ou a interface.

enum class OrigemObrigacao(val rotulo: String) {
    FIXA("Conta fixa"),
    AVULSA("Conta avulsa"),
    PARCELA("Parcela"),
    EMPRESTIMO("Empréstimo"),
}

/** Natureza de um ajuste. Acréscimos aumentam o restante; abatimentos diminuem. */
enum class NaturezaAjuste(val rotulo: String, val ehAcrescimo: Boolean, val ehEncargo: Boolean) {
    JUROS("Juros", ehAcrescimo = true, ehEncargo = true),
    MULTA("Multa", ehAcrescimo = true, ehEncargo = true),
    TARIFA("Tarifa", ehAcrescimo = true, ehEncargo = true),
    IOF("IOF", ehAcrescimo = true, ehEncargo = true),
    OUTRO_ACRESCIMO("Outro acréscimo", ehAcrescimo = true, ehEncargo = true),
    DESCONTO("Desconto", ehAcrescimo = false, ehEncargo = false),
    OUTRO_ABATIMENTO("Outro abatimento", ehAcrescimo = false, ehEncargo = false),
    CREDITO("Crédito/pagamento adiantado", ehAcrescimo = false, ehEncargo = false),
}

enum class StatusObrigacao(val rotulo: String) {
    PENDENTE("Pendente"),
    PARCIAL("Parcial"),
    ATRASADA("Atrasada"),
    QUITADA("Paga"),
}

data class ContaFixa(
    val id: String,
    val nome: String,
    val valorPadrao: Long,
    /** 1 a 31, ou null ("sem vencimento"). */
    val diaVencimento: Int? = null,
    val categoriaId: String? = null,
    val contaPadraoId: String? = null,
    val arquivada: Boolean = false,
)

data class Obrigacao(
    val id: String,
    val origem: OrigemObrigacao,
    val descricao: String,
    /** Valor original; pagamentos e ajustes nunca o alteram. */
    val valorPrevisto: Long,
    val vencimento: LocalDate? = null,
    /** Mês a que pertence. Preservado mesmo se paga em outro mês. */
    val mesReferencia: YearMonth? = null,
    val contaFixaId: String? = null,
    val categoriaId: String? = null,
)

data class Pagamento(val id: String, val obrigacaoId: String, val movimentacaoId: String, val valor: Long)

data class AjusteObrigacao(
    val id: String,
    val obrigacaoId: String,
    val pagamentoId: String?,
    val natureza: NaturezaAjuste,
    /** Sempre positivo; a natureza diz se soma ou subtrai. */
    val valor: Long,
    val movimentacaoId: String?,
)

/** Uma obrigação com os números calculados. */
data class SituacaoObrigacao(
    val obrigacao: Obrigacao,
    val pago: Long,
    val acrescimos: Long,
    val abatimentos: Long,
    val restante: Long,
    val status: StatusObrigacao,
)

/** Foto de tudo o que é de "A pagar", sempre atualizada (como a VisaoFinanceira). */
data class VisaoAPagar(
    val contasFixas: List<ContaFixa>,
    val obrigacoes: List<Obrigacao>,
    val pagamentos: List<Pagamento>,
    val ajustes: List<AjusteObrigacao>,
) {
    fun situacao(o: Obrigacao, hoje: LocalDate) = Obrigacoes.situacao(o, pagamentos, ajustes, hoje)
    fun situacoes(hoje: LocalDate) = obrigacoes.map { situacao(it, hoje) }
    fun obrigacao(id: String?): Obrigacao? = id?.let { i -> obrigacoes.firstOrNull { it.id == i } }
    fun contaFixa(id: String?): ContaFixa? = id?.let { i -> contasFixas.firstOrNull { it.id == i } }

    /** Obrigações do mês (pelo mês de referência). */
    fun doMes(mes: YearMonth, hoje: LocalDate) = situacoes(hoje).filter { it.obrigacao.mesReferencia == mes }

    /** Pendências de meses anteriores ao [mes], ainda não quitadas (continuam no mês original). */
    fun pendentesAnteriores(mes: YearMonth, hoje: LocalDate) = situacoes(hoje).filter {
        val m = it.obrigacao.mesReferencia
        m != null && m < mes && it.status != StatusObrigacao.QUITADA
    }

    companion object {
        val VAZIA = VisaoAPagar(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

object Obrigacoes {

    /**
     * restante = previsto + acréscimos − abatimentos − pagamentos aplicados.
     * Status: quitada (restante ≤ 0); atrasada (venceu e falta pagar); parcial (já recebeu
     * algum pagamento); pendente (os demais casos).
     *
     * As listas recebidas devem conter só pagamentos e ajustes válidos (não excluídos e
     * com a movimentação ainda existente).
     */
    fun situacao(o: Obrigacao, pagamentos: List<Pagamento>, ajustes: List<AjusteObrigacao>, hoje: LocalDate): SituacaoObrigacao {
        val pago = pagamentos.filter { it.obrigacaoId == o.id }.sumOf { it.valor }
        val meus = ajustes.filter { it.obrigacaoId == o.id }
        val acrescimos = meus.filter { it.natureza.ehAcrescimo }.sumOf { it.valor }
        val abatimentos = meus.filter { !it.natureza.ehAcrescimo }.sumOf { it.valor }
        val restante = o.valorPrevisto + acrescimos - abatimentos - pago
        val status = when {
            restante <= 0 -> StatusObrigacao.QUITADA
            o.vencimento != null && o.vencimento < hoje -> StatusObrigacao.ATRASADA
            pago > 0 -> StatusObrigacao.PARCIAL
            else -> StatusObrigacao.PENDENTE
        }
        return SituacaoObrigacao(o, pago, acrescimos, abatimentos, restante, status)
    }

    /**
     * Vencimento de uma conta fixa num mês. Se o dia não existe no mês (ex.: 31 em
     * fevereiro), usa o último dia do mês. Sem dia definido, fica sem vencimento.
     */
    fun vencimentoNoMes(diaVencimento: Int?, mes: YearMonth): LocalDate? =
        diaVencimento?.let { mes.atDay(minOf(it, mes.lengthOfMonth())) }

    /** Contas fixas ativas que ainda NÃO têm obrigação no mês (as que podem ser lançadas). */
    fun fixasDisponiveisParaLancar(fixas: List<ContaFixa>, obrigacoes: List<Obrigacao>, mes: YearMonth): List<ContaFixa> {
        val jaLancadas = obrigacoes.filter { it.mesReferencia == mes }.mapNotNull { it.contaFixaId }.toSet()
        return fixas.filter { !it.arquivada && it.id !in jaLancadas }
    }
}
