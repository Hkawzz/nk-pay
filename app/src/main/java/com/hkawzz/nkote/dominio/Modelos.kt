package com.hkawzz.nkote.dominio

import java.time.LocalDate

// Modelos do domínio: Kotlin puro, sem Android e sem banco de dados.
// São eles que as regras financeiras (Saldos.kt, Validacao.kt) usam e que os testes exercitam.
// Todos os valores em dinheiro são CENTAVOS (Long). Ver docs/regras-financeiras.md.

/** Natureza da conta: define como ela entra nos cálculos (regras, seção 2). */
enum class Natureza(val rotulo: String) {
    /** Pix, dinheiro físico, bancos. Forma o "Dinheiro próprio". */
    PROPRIO("Dinheiro próprio"),

    /** VA e VR. Nunca entra no disponível. */
    BENEFICIO("Benefício"),

    /** Cartão de crédito. Telas na v0.4; a v0.1 só reserva a natureza. */
    CARTAO("Cartão de crédito"),

    /** Credor de carnê/boleto parcelado, sem limite. Telas na v0.4. */
    CREDIARIO("Crediário"),

    /** v0.5: pessoa ou instituição de quem peguei dinheiro (eu devo). */
    EMPRESTIMO_RECEBIDO("Empréstimo recebido"),

    /** v0.5: pessoa a quem emprestei (me deve). */
    EMPRESTIMO_CONCEDIDO("Empréstimo concedido"),

    /**
     * v0.8 (R2, ADR 0032): a conta interna de um objetivo. Dinheiro seu, guardado: entra no
     * Saldo total e no Guardado, nunca no Saldo disponível. NÃO é meio de pagamento.
     */
    CAIXINHA("Caixinha"),
    ;

    /** v0.8: dinheiro seu (contas próprias e caixinhas): Saldo total e fluxo. */
    val ehDinheiroProprio: Boolean get() = this == PROPRIO || this == CAIXINHA

    /** v0.8 (R2): as únicas contas que pagam, recebem e aparecem nas listas de "pagar com" e "conta". */
    val ehMeioDePagamento: Boolean get() = this == PROPRIO || this == BENEFICIO

    /** Só cartão e crediário (telas de cartões, faturas e Análises de crédito). */
    val ehCredito: Boolean get() = this == CARTAO || this == CREDIARIO

    /** v0.5: o ponto único de verdade da integridade (ADR 0022): cartão, crediário e empréstimos. */
    val ehContaDeTerceiros: Boolean get() = ehCredito || ehEmprestimo

    val ehEmprestimo: Boolean get() = this == EMPRESTIMO_RECEBIDO || this == EMPRESTIMO_CONCEDIDO

    companion object {
        /** Naturezas que a interface da v0.1 permite escolher. */
        val disponiveisNaV01 = listOf(PROPRIO, BENEFICIO)
    }
}

enum class TipoBeneficio(val rotulo: String) {
    VA("Vale-alimentação"),
    VR("Vale-refeição"),
    OUTRO("Outro benefício"),
}

enum class TipoMovimentacao(val rotulo: String) {
    ENTRADA("Entrada"),
    DESPESA("Despesa"),
    TRANSFERENCIA("Transferência"),
    AJUSTE("Ajuste"),
    ;

    /** Só entradas e despesas têm categoria. */
    val temCategoria: Boolean get() = this == ENTRADA || this == DESPESA
}

data class Conta(
    val id: String,
    val nome: String,
    val natureza: Natureza,
    val tipoBeneficio: TipoBeneficio? = null,
    /** Saldo na data do saldo inicial. NÃO é uma entrada. */
    val saldoInicial: Long,
    val dataSaldoInicial: LocalDate,
    val icone: String? = null,
    /** Cor em ARGB, opcional. */
    val cor: Long? = null,
    val arquivada: Boolean = false,
    // Contas de crédito (v0.4). Cartão: fechamento e vencimento obrigatórios; limite opcional.
    val limite: Long? = null,
    val diaFechamento: Int? = null,
    val diaVencimento: Int? = null,
) {
    val ehCredito: Boolean get() = natureza.ehCredito
    val ehContaDeTerceiros: Boolean get() = natureza.ehContaDeTerceiros
    /** v0.8 (R2): conta própria ou de benefício (nunca caixinha nem conta de terceiros). */
    val ehMeioDePagamento: Boolean get() = natureza.ehMeioDePagamento
}

data class Categoria(
    val id: String,
    val nome: String,
    /** Preenchido só nas categorias do sistema (que não podem ser excluídas). */
    val codigoSistema: String? = null,
    val arquivada: Boolean = false,
    /** Nome do ícone (v0.4). Null = ainda sem aparência. */
    val icone: String? = null,
    /** Cor em ARGB (v0.4). */
    val cor: Long? = null,
) {
    val ehDoSistema: Boolean get() = codigoSistema != null
}

data class Movimentacao(
    val id: String,
    val tipo: TipoMovimentacao,
    val data: LocalDate,
    /**
     * Valor em centavos.
     * - ENTRADA, DESPESA e TRANSFERENCIA: sempre positivo.
     * - AJUSTE: positivo aumenta o saldo, negativo diminui.
     */
    val valor: Long,
    /** Conta da movimentação. Na transferência, é a conta de ORIGEM. */
    val contaId: String,
    /** Só na transferência: a conta de destino. */
    val contaDestinoId: String? = null,
    /** Só em entradas e despesas. */
    val categoriaId: String? = null,
    /** Descrição livre. No ajuste, é o motivo. */
    val descricao: String = "",
)

/** Códigos das categorias criadas pelo sistema. */
object CategoriasDoSistema {
    const val ENCARGOS_FINANCEIROS = "ENCARGOS_FINANCEIROS"
    /** v0.5: receita dos juros de empréstimos concedidos (regra 6E.5). */
    const val JUROS_RECEBIDOS = "JUROS_RECEBIDOS"
    /** v0.8 (R2): receita dos rendimentos das caixinhas (regra 6G.2). */
    const val RENDIMENTOS = "RENDIMENTOS"
}
