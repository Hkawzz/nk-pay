package com.nkpay.app.dominio

import java.time.LocalDate

/**
 * Regras de validação. Devolvem a mensagem do primeiro problema encontrado
 * (para mostrar na tela) ou `null` se está tudo certo.
 *
 * A camada de dados também chama estas funções antes de gravar, então um
 * registro inválido nunca chega ao banco, venha de onde vier.
 */
object Validacao {

    fun conta(conta: Conta): String? = when {
        conta.nome.isBlank() -> "Dê um nome para a conta."
        conta.nome.length > 60 -> "O nome pode ter no máximo 60 caracteres."
        conta.natureza == Natureza.BENEFICIO && conta.tipoBeneficio == null ->
            "Escolha o tipo de benefício (VA, VR ou outro)."
        conta.natureza != Natureza.BENEFICIO && conta.tipoBeneficio != null ->
            "Só contas de benefício têm tipo de benefício."
        // Contas de crédito (v0.4, regra 6B.1)
        conta.ehCredito && conta.saldoInicial != 0L ->
            "Cartões e crediários começam com saldo zero. O que já deve entra como compra anterior ao app."
        conta.natureza == Natureza.CARTAO && (conta.diaFechamento == null || conta.diaVencimento == null) ->
            "Informe o dia de fechamento e o dia de vencimento do cartão."
        conta.diaFechamento != null && conta.diaFechamento !in 1..31 -> "O dia de fechamento vai de 1 a 31."
        conta.diaVencimento != null && conta.diaVencimento !in 1..31 -> "O dia de vencimento vai de 1 a 31."
        conta.limite != null && conta.limite <= 0 -> "O limite precisa ser maior que zero (ou deixe em branco)."
        else -> null
    }

    /** Regra 6B.7 / ADR 0017: contas de crédito não aceitam lançamentos avulsos. */
    fun aceitaLancamentoAvulso(natureza: Natureza): Boolean = !natureza.ehCredito

    const val MENSAGEM_CONTA_DE_CREDITO =
        "Cartões e crediários só mudam por compras e pagamentos. Use a tela do cartão ou do crediário."

    fun compra(descricao: String, valorTotal: Long, parcelas: Int, primeiroVencimento: LocalDate?): String? = when {
        descricao.isBlank() -> "Dê uma descrição para a compra."
        descricao.length > 80 -> "A descrição pode ter no máximo 80 caracteres."
        valorTotal <= 0 -> "O valor precisa ser maior que zero."
        parcelas !in 1..120 -> "O número de parcelas vai de 1 a 120."
        valorTotal < parcelas -> "O valor é pequeno demais para tantas parcelas."
        primeiroVencimento == null -> "Informe o vencimento da 1ª parcela."
        else -> null
    }

    fun categoria(categoria: Categoria): String? = when {
        categoria.nome.isBlank() -> "Dê um nome para a categoria."
        categoria.nome.length > 40 -> "O nome pode ter no máximo 40 caracteres."
        else -> null
    }

    fun contaFixa(f: ContaFixa): String? = when {
        f.nome.isBlank() -> "Dê um nome para a conta fixa."
        f.nome.length > 60 -> "O nome pode ter no máximo 60 caracteres."
        f.valorPadrao <= 0 -> "O valor precisa ser maior que zero."
        f.diaVencimento != null && f.diaVencimento !in 1..31 -> "O dia do vencimento vai de 1 a 31."
        else -> null
    }

    fun obrigacao(o: Obrigacao): String? = when {
        o.descricao.isBlank() -> "Dê uma descrição para a conta."
        o.descricao.length > 80 -> "A descrição pode ter no máximo 80 caracteres."
        o.valorPrevisto <= 0 -> "O valor precisa ser maior que zero."
        o.mesReferencia == null && o.vencimento == null -> "Informe o vencimento ou o mês da conta."
        else -> null
    }

    /** @param contaExiste diz se um id de conta existe e não está excluído. */
    fun movimentacao(m: Movimentacao, contaExiste: (String) -> Boolean): String? {
        if (!contaExiste(m.contaId)) return "Escolha uma conta."
        return when (m.tipo) {
            TipoMovimentacao.ENTRADA, TipoMovimentacao.DESPESA -> when {
                m.valor <= 0 -> "O valor precisa ser maior que zero."
                m.contaDestinoId != null -> "Só transferências têm conta de destino."
                else -> null
            }
            TipoMovimentacao.TRANSFERENCIA -> when {
                m.valor <= 0 -> "O valor precisa ser maior que zero."
                m.contaDestinoId == null || !contaExiste(m.contaDestinoId) -> "Escolha a conta de destino."
                m.contaDestinoId == m.contaId -> "A conta de origem e a de destino precisam ser diferentes."
                m.categoriaId != null -> "Transferências não têm categoria."
                else -> null
            }
            TipoMovimentacao.AJUSTE -> when {
                m.valor == 0L -> "O ajuste não muda nada: o saldo já é esse."
                m.contaDestinoId != null -> "Só transferências têm conta de destino."
                m.categoriaId != null -> "Ajustes não têm categoria."
                else -> null
            }
        }
    }
}
