package com.hkawzz.nkote.dominio

import java.time.YearMonth

/** O resumo da tela Início. Nenhum número soma benefícios com dinheiro próprio. */
data class Resumo(
    val dinheiroProprio: Long,
    val reservado: Long,
    val disponivel: Long,
    /** Cada conta de benefício com o seu saldo, na ordem das contas. */
    val beneficios: List<SaldoDeConta>,
    val totalBeneficios: Long,
)

data class SaldoDeConta(val conta: Conta, val saldo: Long)

data class TotaisDoMes(val entradas: Long, val despesas: Long)

/**
 * Regras de cálculo de saldo (docs/regras-financeiras.md, seções 3 e 4).
 *
 * Nada aqui é gravado no banco: tudo é recalculado a partir das contas e das
 * movimentações, por isso nunca fica desatualizado.
 *
 * As listas recebidas já devem estar sem os registros excluídos.
 */
object Saldos {

    /**
     * Saldo = saldo inicial + entradas − despesas
     *       + transferências recebidas − transferências enviadas ± ajustes
     *
     * Todas as movimentações da conta contam, qualquer que seja a data
     * (ver docs/decisoes/0009-saldo-inicial.md).
     */
    fun saldoDaConta(conta: Conta, movimentacoes: List<Movimentacao>): Long {
        var saldo = conta.saldoInicial
        for (m in movimentacoes) {
            saldo += efeitoNaConta(m, conta.id)
        }
        return saldo
    }

    /** Quanto uma movimentação muda o saldo de uma conta (0 se não a envolve). */
    fun efeitoNaConta(m: Movimentacao, contaId: String): Long = when (m.tipo) {
        TipoMovimentacao.ENTRADA -> if (m.contaId == contaId) m.valor else 0
        TipoMovimentacao.DESPESA -> if (m.contaId == contaId) -m.valor else 0
        TipoMovimentacao.AJUSTE -> if (m.contaId == contaId) m.valor else 0
        TipoMovimentacao.TRANSFERENCIA -> when (contaId) {
            m.contaId -> -m.valor
            m.contaDestinoId -> m.valor
            else -> 0
        }
    }

    /** Saldo de cada conta, em uma passada só pelas movimentações. */
    fun saldosPorConta(contas: List<Conta>, movimentacoes: List<Movimentacao>): Map<String, Long> {
        val saldos = contas.associate { it.id to it.saldoInicial }.toMutableMap()
        fun somar(id: String?, valor: Long) {
            if (id != null && id in saldos) saldos[id] = saldos.getValue(id) + valor
        }
        for (m in movimentacoes) {
            when (m.tipo) {
                TipoMovimentacao.ENTRADA, TipoMovimentacao.AJUSTE -> somar(m.contaId, m.valor)
                TipoMovimentacao.DESPESA -> somar(m.contaId, -m.valor)
                TipoMovimentacao.TRANSFERENCIA -> {
                    somar(m.contaId, -m.valor)
                    somar(m.contaDestinoId, m.valor)
                }
            }
        }
        return saldos
    }

    /**
     * Dinheiro próprio = Σ contas PROPRIO
     * Disponível       = dinheiro próprio − reservado
     * Benefícios       = cada conta BENEFICIO, à parte (nunca entram no disponível)
     *
     * Contas arquivadas continuam contando: arquivar só esconde a conta das novas
     * escolhas, e o dinheiro dela não deixa de existir.
     */
    // v0.8 (R2, regra 6G.2): Saldo total = próprias + caixinhas; Guardado = Σ caixinhas;
    // Saldo disponível = Saldo total − Guardado (= só as contas próprias). O "carimbo" deixou de existir.
    fun resumo(contas: List<Conta>, movimentacoes: List<Movimentacao>): Resumo {
        val saldos = saldosPorConta(contas, movimentacoes)
        val dinheiroProprio = contas
            .filter { it.natureza.ehDinheiroProprio }
            .sumOf { saldos.getValue(it.id) }
        val reservado = contas.filter { it.natureza == Natureza.CAIXINHA }.sumOf { saldos.getValue(it.id) }
        val beneficios = contas
            .filter { it.natureza == Natureza.BENEFICIO }
            .map { SaldoDeConta(it, saldos.getValue(it.id)) }
        return Resumo(
            dinheiroProprio = dinheiroProprio,
            reservado = reservado,
            disponivel = dinheiroProprio - reservado,
            beneficios = beneficios,
            totalBeneficios = beneficios.sumOf { it.saldo },
        )
    }

    /**
     * Entradas e despesas do mês. Transferências e ajustes NÃO entram.
     * Despesas pagas com benefício entram (são consumo real).
     */
    fun totaisDoMes(movimentacoes: List<Movimentacao>, mes: YearMonth): TotaisDoMes {
        var entradas = 0L
        var despesas = 0L
        for (m in movimentacoes) {
            if (YearMonth.from(m.data) != mes) continue
            when (m.tipo) {
                TipoMovimentacao.ENTRADA -> entradas += m.valor
                TipoMovimentacao.DESPESA -> despesas += m.valor
                TipoMovimentacao.TRANSFERENCIA, TipoMovimentacao.AJUSTE -> Unit
            }
        }
        return TotaisDoMes(entradas, despesas)
    }

    /** Ajuste pelo novo saldo: o app calcula a diferença. */
    fun valorDoAjuste(saldoAtual: Long, novoSaldoInformado: Long): Long =
        novoSaldoInformado - saldoAtual
}
