package com.hkawzz.nkote.dominio

/**
 * Uma "foto" de todos os dados num instante: contas, categorias e movimentações
 * (todas sem os excluídos), com os saldos já calculados. As telas leem daqui.
 */
data class VisaoFinanceira(
    val contas: List<Conta>,
    val categorias: List<Categoria>,
    val movimentacoes: List<Movimentacao>,
    /** v0.7 (6F): os objetivos; o Guardado deles entra no Saldo disponível. */
    val objetivos: VisaoObjetivos = VisaoObjetivos.VAZIA,
) {
    val saldos: Map<String, Long> by lazy { Saldos.saldosPorConta(contas, movimentacoes) }
    val resumo: Resumo by lazy { Saldos.resumo(contas, movimentacoes) }
    private val contasPorId by lazy { contas.associateBy { it.id } }
    private val categoriasPorId by lazy { categorias.associateBy { it.id } }

    fun conta(id: String?): Conta? = id?.let { contasPorId[it] }
    fun categoria(id: String?): Categoria? = id?.let { categoriasPorId[it] }
    fun saldo(contaId: String): Long = saldos[contaId] ?: 0

    /** Movimentações que envolvem a conta (como origem ou destino). */
    fun movimentacoesDaConta(contaId: String): List<Movimentacao> =
        movimentacoes.filter { it.contaId == contaId || it.contaDestinoId == contaId }

    companion object {
        val VAZIA = VisaoFinanceira(emptyList(), emptyList(), emptyList())
    }
}
