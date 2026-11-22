package com.nkpay.app.dominio

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
        else -> null
    }

    fun categoria(categoria: Categoria): String? = when {
        categoria.nome.isBlank() -> "Dê um nome para a categoria."
        categoria.nome.length > 40 -> "O nome pode ter no máximo 40 caracteres."
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
