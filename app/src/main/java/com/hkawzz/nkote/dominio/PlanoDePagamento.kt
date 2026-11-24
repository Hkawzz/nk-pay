package com.hkawzz.nkote.dominio

/**
 * Como a pessoa classificou a diferença entre o valor pago e o restante (ADR 0013).
 * O app PERGUNTA: nada é classificado automaticamente.
 */
enum class ClassificacaoDiferenca(val rotulo: String, val natureza: NaturezaAjuste?, val pagouMenos: Boolean) {
    // Pagou menos que o restante
    PARCIAL("Pagamento parcial", null, pagouMenos = true),
    DESCONTO("Desconto", NaturezaAjuste.DESCONTO, pagouMenos = true),
    OUTRO_ABATIMENTO("Outro abatimento", NaturezaAjuste.OUTRO_ABATIMENTO, pagouMenos = true),

    // Pagou mais que o restante (encargos: despesa própria em "Encargos financeiros")
    JUROS("Juros", NaturezaAjuste.JUROS, pagouMenos = false),
    MULTA("Multa", NaturezaAjuste.MULTA, pagouMenos = false),
    TARIFA("Tarifa", NaturezaAjuste.TARIFA, pagouMenos = false),
    IOF("IOF", NaturezaAjuste.IOF, pagouMenos = false),
    OUTRO_ACRESCIMO("Outro acréscimo", NaturezaAjuste.OUTRO_ACRESCIMO, pagouMenos = false),
    ;

    companion object {
        val quandoPagaMenos = entries.filter { it.pagouMenos }
        val quandoPagaMais = entries.filter { !it.pagouMenos }
    }
}

/**
 * O que um pagamento vai gravar, calculado ANTES de gravar (e mostrado na tela).
 * Todas as despesas somadas são exatamente o valor pago: nunca há receita fictícia.
 */
data class PlanoDePagamento(
    /** Despesa na categoria da conta (pode ser 0 se só houver encargo a pagar). */
    val despesaPrincipal: Long,
    /** Despesa em "Encargos financeiros" (juros, multa...), ou 0. */
    val encargo: Long,
    /** Abatimento (desconto) registrado na conta, sem movimentação, ou 0. */
    val abatimento: Long,
    val classificacao: ClassificacaoDiferenca?,
    val restanteDepois: Long,
) {
    val totalPago: Long get() = despesaPrincipal + encargo
}

sealed interface ResultadoPlano {
    data class Pronto(val plano: PlanoDePagamento) : ResultadoPlano
    /** Valor diferente do restante: a pessoa precisa escolher uma destas classificações. */
    data class PrecisaClassificar(val opcoes: List<ClassificacaoDiferenca>, val diferenca: Long) : ResultadoPlano
    data class Invalido(val mensagem: String) : ResultadoPlano
}

object Pagamentos {

    /**
     * Monta o plano de um pagamento de [valorPago] numa obrigação que tem [restante].
     * - Igual ao restante: uma despesa, quitada.
     * - Menor: pergunta se foi parcial (restante continua) ou desconto/abatimento (quita).
     * - Maior: pergunta a natureza do encargo (juros, multa...): a parte do restante é
     *   uma despesa na categoria da conta, e o excedente é outra despesa, de encargo.
     */
    fun planejar(restante: Long, valorPago: Long, classificacao: ClassificacaoDiferenca?): ResultadoPlano {
        if (restante <= 0) return ResultadoPlano.Invalido("Esta conta já está paga.")
        if (valorPago <= 0) return ResultadoPlano.Invalido("O valor pago precisa ser maior que zero.")
        if (valorPago == restante) {
            return ResultadoPlano.Pronto(PlanoDePagamento(valorPago, 0, 0, null, 0))
        }
        val pagouMenos = valorPago < restante
        val diferenca = if (pagouMenos) restante - valorPago else valorPago - restante
        val opcoes = if (pagouMenos) ClassificacaoDiferenca.quandoPagaMenos else ClassificacaoDiferenca.quandoPagaMais
        if (classificacao == null || classificacao !in opcoes) return ResultadoPlano.PrecisaClassificar(opcoes, diferenca)

        val plano = when {
            classificacao == ClassificacaoDiferenca.PARCIAL ->
                PlanoDePagamento(valorPago, 0, 0, classificacao, restanteDepois = diferenca)
            pagouMenos ->
                PlanoDePagamento(valorPago, 0, abatimento = diferenca, classificacao = classificacao, restanteDepois = 0)
            else ->
                PlanoDePagamento(despesaPrincipal = restante, encargo = diferenca, abatimento = 0, classificacao = classificacao, restanteDepois = 0)
        }
        return ResultadoPlano.Pronto(plano)
    }
}
