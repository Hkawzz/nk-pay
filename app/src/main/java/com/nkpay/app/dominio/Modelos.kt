package com.nkpay.app.dominio

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
    ;

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
)

data class Categoria(
    val id: String,
    val nome: String,
    /** Preenchido só nas categorias do sistema (que não podem ser excluídas). */
    val codigoSistema: String? = null,
    val arquivada: Boolean = false,
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
}
