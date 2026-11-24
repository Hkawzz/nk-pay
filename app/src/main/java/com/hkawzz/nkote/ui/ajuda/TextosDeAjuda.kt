package com.hkawzz.nkote.ui.ajuda

// =====================================================================================
// TODOS os textos da ajuda "?" (v0.5, ADR 0023), num arquivo só, conferidos contra
// docs/regras-financeiras.md. Os números dos exemplos são conferidos por AjudaTest
// contra as funções do domínio. Não acrescente "?" fora dos locais da especificação.
// =====================================================================================

/** Um texto de ajuda: só os blocos que ajudam (os nulos não aparecem). */
data class TextoAjuda(
    val titulo: String,
    val oQueE: String,
    val calculo: String? = null,
    val exemplo: String? = null,
    val oQueNaoE: String? = null,
)

enum class ChaveAjuda {
    // Início
    SALDO_DISPONIVEL, SALDO_TOTAL, GUARDADO, BENEFICIOS, SALDO_MOVIMENTACOES,
    // Análises
    RESULTADO, DESPESAS_REGISTRADAS, SAIDAS_PROPRIO, DESCONTOS_OBTIDOS, CARTOES_CREDIARIOS,
    // Empréstimos
    EMPRESTIMO_RESTANTE, EMPRESTIMO_JUROS_PREVISTOS, EMPRESTIMO_JUROS_RECEBIDOS, EMPRESTIMO_ATRASADA,
    // Cartões e crediários (v0.6, regra 6B.8)
    JUROS_DO_PARCELAMENTO,
    // Objetivos (v0.7, regra 6F)
    OBJETIVO, OBJETIVO_RETIRAR, ANALISES_OBJETIVOS,
}

object TextosDeAjuda {
    val todos: Map<ChaveAjuda, TextoAjuda> = mapOf(
        ChaveAjuda.OBJETIVO to TextoAjuda(
            titulo = "Objetivos",
            oQueE = "Cada objetivo tem uma caixinha, que guarda dinheiro de verdade. Guardar transfere de uma conta sua para a caixinha; retirar transfere de volta.",
            calculo = "Guardado = saldo da caixinha (o que entrou − o que saiu + rendimentos). Saldo total = contas próprias + caixinhas. Saldo disponível = Saldo total − Guardado.",
            exemplo = "Pix R$ 1.000,00. Guardar R$ 300,00 do Pix no \"Fone\": Pix R$ 700,00, caixinha R$ 300,00, Saldo total R$ 1.000,00 e Saldo disponível R$ 700,00.",
            oQueNaoE = "Guardar, retirar e mover não são entrada, saída nem despesa (só o rendimento é entrada). A caixinha não paga nada: para gastar, retire para uma conta e lance a despesa por ela.",
        ),
        ChaveAjuda.ANALISES_OBJETIVOS to TextoAjuda(
            titulo = "Objetivos nas Análises",
            oQueE = "Quanto está nas caixinhas agora e quanto foi guardado, retirado e rendeu no período, pela data de cada movimento.",
            calculo = "Guardado no período = transferências das contas para as caixinhas. Retirado = das caixinhas para as contas. Rendimentos = entradas nas caixinhas. Mover entre caixinhas não conta.",
            exemplo = "Guardar R$ 300,00, render R$ 2,00 e retirar R$ 100,00 em dezembro: guardado R$ 300,00, retirado R$ 100,00 e rendimentos R$ 2,00. As entradas de dezembro sobem só R$ 2,00.",
            oQueNaoE = "Guardar e retirar não são entrada, saída nem despesa: o dinheiro continua sendo seu. Só o rendimento é receita.",
        ),
        ChaveAjuda.OBJETIVO_RETIRAR to TextoAjuda(
            titulo = "Gastar o dinheiro de um objetivo",
            oQueE = "Vai gastar o dinheiro guardado? Retire da caixinha para uma conta e depois lance a despesa por essa conta.",
            calculo = "1) Retirar transfere da caixinha para a conta (não é despesa). 2) A despesa, lançada normalmente, tira o dinheiro da conta. A caixinha nunca aparece como conta para pagar.",
            exemplo = "Pix R$ 700,00 e R$ 300,00 na caixinha \"Fone\". Retire R$ 300,00 para o Pix (Pix R$ 1.000,00) e lance a despesa de R$ 300,00 no Pix: Saldo total R$ 700,00 e Saldo disponível R$ 700,00.",
            oQueNaoE = "Sem retirar, a despesa sai do Pix e o dinheiro da caixinha continua guardado. Se o Pix não tiver saldo, a despesa é recusada (sem cheque especial).",
        ),
        ChaveAjuda.JUROS_DO_PARCELAMENTO to TextoAjuda(
            titulo = "Juros a pagar",
            oQueE = "Os juros das compras parceladas com juros que ainda não foram pagos. Eles ficam fora do devido: o devido é só o que falta da compra.",
            calculo = "Juros de cada parcela = valor da parcela − parte da compra (valor original ÷ número de parcelas). Ao pagar uma parcela, os juros dela viram despesa em Encargos financeiros, na data do pagamento.",
            exemplo = "Compra de R$ 300,00 em 12 × R$ 45,50: despesa de R$ 300,00 na compra e R$ 246,00 de juros a pagar. Ao pagar a 1ª parcela, R$ 20,50 viram despesa de juros e o devido cai R$ 25,00.",
            oQueNaoE = "Não é como o empréstimo pego, em que os juros viram despesa de uma vez, na contratação. Aqui eles entram parcela a parcela, quando cada uma é paga.",
        ),
        ChaveAjuda.SALDO_DISPONIVEL to TextoAjuda(
            titulo = "Saldo disponível",
            oQueE = "O dinheiro que é seu e está livre para usar agora.",
            calculo = "Saldo total − Guardado, ou seja, a soma das contas de dinheiro próprio (Pix, bancos, dinheiro físico), sem as caixinhas.",
            exemplo = "Pix R$ 1.200,00 + Dinheiro R$ 300,00, sem nada guardado: Saldo disponível R$ 1.500,00.",
            oQueNaoE = "Não inclui VA/VR, o limite do cartão nem o que você emprestou ou pegou emprestado.",
        ),
        ChaveAjuda.SALDO_TOTAL to TextoAjuda(
            titulo = "Saldo total",
            oQueE = "A soma das contas de dinheiro próprio e das caixinhas dos objetivos.",
            calculo = "Σ contas de dinheiro próprio + Σ caixinhas.",
            oQueNaoE = "Não inclui VA/VR, cartões, crediários nem empréstimos.",
        ),
        ChaveAjuda.GUARDADO to TextoAjuda(
            titulo = "Guardado",
            oQueE = "O dinheiro que está nas caixinhas dos objetivos. É seu, mas não fica disponível para gastar.",
            calculo = "Σ saldos das caixinhas.",
        ),
        ChaveAjuda.BENEFICIOS to TextoAjuda(
            titulo = "Benefícios",
            oQueE = "VA, VR e outros benefícios, cada um com o seu saldo.",
            exemplo = "VA R$ 250,00 + VR R$ 180,00 = R$ 430,00 em benefícios, à parte do Saldo disponível.",
            oQueNaoE = "Nunca entram no Saldo disponível nem no Saldo total.",
        ),
        ChaveAjuda.SALDO_MOVIMENTACOES to TextoAjuda(
            titulo = "Saldo das movimentações",
            oQueE = "O que entrou menos o que saiu do seu dinheiro próprio no mês.",
            calculo = "Entradas: entradas nas contas próprias + o que veio de outras contas. Saídas: despesas pagas com conta própria + o que foi para outras contas (fatura, parcela, empréstimo).",
            exemplo = "Salário R$ 3.000,00, mercado R$ 200,00 no Pix e fatura de R$ 300,00 paga: entradas R$ 3.000,00, saídas R$ 500,00, saldo R$ 2.500,00.",
            oQueNaoE = "Compras no cartão não são saída (a saída é quando a fatura é paga). Transferências entre suas contas, ajustes e despesas do VA/VR não entram.",
        ),
        ChaveAjuda.RESULTADO to TextoAjuda(
            titulo = "Resultado",
            oQueE = "Entradas registradas − despesas registradas no período.",
            exemplo = "Entrada de R$ 3.000,00, mercado de R$ 200,00 e compra de R$ 600,00 no cartão: resultado R$ 2.200,00.",
            oQueNaoE = "Transferências, ajustes, pagamentos de fatura e o valor de empréstimos não entram. Só os juros dos empréstimos entram.",
        ),
        ChaveAjuda.DESPESAS_REGISTRADAS to TextoAjuda(
            titulo = "Despesas registradas",
            oQueE = "Tudo o que foi consumido no período, pela data da compra.",
            calculo = "Σ despesas, de qualquer conta (inclusive cartão e VA/VR).",
            exemplo = "Mercado R$ 200,00 no Pix + compra de R$ 600,00 no cartão = R$ 800,00, mesmo que a fatura não tenha sido paga.",
            oQueNaoE = "Não é o dinheiro que saiu da conta: isso são as Saídas do dinheiro próprio.",
        ),
        ChaveAjuda.SAIDAS_PROPRIO to TextoAjuda(
            titulo = "Saídas do dinheiro próprio",
            oQueE = "O dinheiro que de fato saiu das suas contas próprias no período.",
            calculo = "Despesas pagas com conta própria + transferências de conta própria para outras contas (fatura, parcelas, empréstimos).",
            exemplo = "Mercado R$ 200,00 no Pix + fatura de R$ 300,00 paga = R$ 500,00. A compra de R$ 600,00 no cartão não entra até a fatura ser paga.",
            oQueNaoE = "Não é o mesmo que Despesas registradas, e os dois números podem ser diferentes de propósito.",
        ),
        ChaveAjuda.DESCONTOS_OBTIDOS to TextoAjuda(
            titulo = "Descontos obtidos",
            oQueE = "Quanto você deixou de pagar por ter conseguido desconto ao pagar uma conta, um crediário ou um empréstimo.",
            exemplo = "Luz de R$ 100,00 paga com R$ 95,00 de desconto: R$ 5,00 de desconto obtido.",
            oQueNaoE = "Não é receita, e não inclui parcelas pagas antes de usar o app nem descontos que você deu.",
        ),
        ChaveAjuda.CARTOES_CREDIARIOS to TextoAjuda(
            titulo = "Cartões e crediários",
            oQueE = "Quanto você deve em cada cartão e crediário, e a situação das faturas.",
            calculo = "Devido = compras − pagamentos + encargos − descontos.",
            oQueNaoE = "O devido nunca entra no Saldo disponível.",
        ),
        ChaveAjuda.EMPRESTIMO_RESTANTE to TextoAjuda(
            titulo = "Restante",
            oQueE = "Quanto ainda falta pagar (ou receber) das parcelas do empréstimo.",
            calculo = "Σ (valor da parcela + acréscimos − abatimentos − pago) de cada parcela.",
            exemplo = "10 parcelas de R$ 110,00 com 1 paga: restante R$ 990,00.",
        ),
        ChaveAjuda.EMPRESTIMO_JUROS_PREVISTOS to TextoAjuda(
            titulo = "Juros previstos",
            oQueE = "Quanto o empréstimo custa (ou rende) além do valor emprestado.",
            calculo = "Total das parcelas − valor original.",
            exemplo = "R$ 1.000,00 em 10 × R$ 110,00: juros previstos de R$ 100,00.",
            oQueNaoE = "Empréstimo que você pegou: os juros viram uma despesa na data da contratação. Que você deu: viram receita aos poucos, conforme recebe.",
        ),
        ChaveAjuda.EMPRESTIMO_JUROS_RECEBIDOS to TextoAjuda(
            titulo = "Juros recebidos",
            oQueE = "A parte dos recebimentos que é juros (receita). O resto é a devolução do que você emprestou.",
            calculo = "Em cada recebimento: valor recebido × (juros ainda não recebidos ÷ restante). O último recebimento fecha os centavos.",
            exemplo = "R$ 1.000,00 em 10 × R$ 110,00: cada parcela de R$ 110,00 recebida = R$ 100,00 de devolução + R$ 10,00 de juros.",
            oQueNaoE = "A devolução do valor emprestado nunca é receita.",
        ),
        ChaveAjuda.EMPRESTIMO_ATRASADA to TextoAjuda(
            titulo = "Atrasada",
            oQueE = "Parcela que venceu e ainda não foi paga (ou recebida) por inteiro.",
            calculo = "Vencimento antes de hoje e restante maior que zero. Os dias de atraso contam desde o vencimento.",
            oQueNaoE = "O app não lança juros nem multa sozinho: se houver, você informa ao pagar.",
        ),
    )

    fun de(chave: ChaveAjuda): TextoAjuda = todos.getValue(chave)
}
