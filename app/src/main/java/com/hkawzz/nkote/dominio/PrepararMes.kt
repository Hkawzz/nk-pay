package com.hkawzz.nkote.dominio

import java.time.LocalDate
import java.time.YearMonth

// =====================================================================================
// Preparar próximo mês (v0.6). Regra 7.6 e ADR 0026. Kotlin puro: só calcula o que o
// assistente mostra; nada é gravado aqui.
// =====================================================================================

object PrepararMes {

    /** Uma conta fixa que ainda não entrou no mês, com o valor sugerido (editável). */
    data class FixaSugerida(val fixa: ContaFixa, val valorSugerido: Long, val vencimento: LocalDate?)

    /** Uma linha de conferência (fatura, parcela de crediário ou de empréstimo), que já existe. */
    data class ParcelaPrevista(val titulo: String, val vencimento: LocalDate?, val valor: Long, val juros: Long)

    /**
     * Passo 1: as contas fixas ativas que ainda não entraram em [mes] (reusa a regra da
     * v0.3, que impede duplicar). Valor sugerido: o do mês anterior; sem ele, o padrão.
     */
    fun fixas(v: VisaoAPagar, mes: YearMonth): List<FixaSugerida> =
        Obrigacoes.fixasDisponiveisParaLancar(v.contasFixas, v.obrigacoes, mes).map { f ->
            val anterior = v.obrigacoes.firstOrNull { it.contaFixaId == f.id && it.mesReferencia == mes.minusMonths(1) }
            FixaSugerida(f, anterior?.valorPrevisto ?: f.valorPadrao, Obrigacoes.vencimentoNoMes(f.diaVencimento, mes))
        }.sortedWith(compareBy({ it.vencimento == null }, { it.vencimento }, { it.fixa.nome }))

    /**
     * Passo 2: faturas de cartão, parcelas de crediário e de empréstimo recebido de [mes],
     * ainda em aberto, só para conferência, com o que será cobrado (juros incluídos).
     */
    fun parcelasPrevistas(vc: VisaoCredito?, ve: VisaoEmprestimos?, mes: YearMonth, hoje: LocalDate): List<ParcelaPrevista> {
        val credito = vc?.let { c ->
            c.contasDeCredito.flatMap { conta ->
                c.faturas(conta.id, hoje).filter { it.mes == mes && it.emAberto }.map { f ->
                    ParcelaPrevista((if (conta.natureza == Natureza.CARTAO) "Fatura " else "Parcelas ") + conta.nome, f.vencimento, f.restante, f.jurosPendentes)
                }
            }
        }.orEmpty()
        val emprestimos = ve?.let { e ->
            e.situacoes(TipoEmprestimo.RECEBIDO, hoje).flatMap { s ->
                s.parcelas.filter { it.obrigacao.mesReferencia == mes && it.restante > 0 }.map { p ->
                    ParcelaPrevista(p.obrigacao.descricao + (e.conta(s.emprestimo.contaId)?.let { " · " + it.nome } ?: ""), p.obrigacao.vencimento, p.restante, 0)
                }
            }
        }.orEmpty()
        return (credito + emprestimos).sortedWith(compareBy({ it.vencimento == null }, { it.vencimento }, { it.titulo }))
    }

    /** Passo 3: pendências de meses anteriores ainda não levadas para [mes] (fora faturas e parcelas). */
    fun pendencias(v: VisaoAPagar, mes: YearMonth, hoje: LocalDate): List<SituacaoObrigacao> =
        v.pendentesAnteriores(mes, hoje).sortedWith(compareBy({ it.obrigacao.mesReferencia }, { it.obrigacao.vencimento }))

    /**
     * Passo 4: total previsto do mês = o que já está no mês e falta pagar + as contas fixas
     * escolhidas + as pendências levadas + as parcelas previstas.
     */
    fun totalPrevisto(
        v: VisaoAPagar, mes: YearMonth, hoje: LocalDate,
        fixasEscolhidas: Collection<Long>, pendenciasLevadas: Collection<SituacaoObrigacao>, parcelas: Collection<ParcelaPrevista>,
    ): Long = v.doMes(mes, hoje).filter { it.status != StatusObrigacao.QUITADA }.sumOf { maxOf(0, it.restante) } +
        fixasEscolhidas.sum() + pendenciasLevadas.sumOf { maxOf(0, it.restante) } + parcelas.sumOf { it.valor }

    /** A sugestão aparece quando o mês ainda tem contas fixas a lançar. */
    fun deveSugerir(v: VisaoAPagar, mes: YearMonth): Boolean = fixas(v, mes).isNotEmpty()
}
