package com.hkawzz.nkote.dominio

import java.time.LocalDate
import java.time.YearMonth

// =====================================================================================
// Início completa (v0.8, V1, regra 6G.5). Kotlin puro: só calcula o que os quatro blocos
// mostram, a partir das visões que já existem. Cada bloco só aparece com conteúdo.
// =====================================================================================

/** Para onde o atalho de um vencimento leva. */
sealed interface DestinoVencimento {
    data class ContaAPagar(val obrigacaoId: String) : DestinoVencimento
    data class Fatura(val contaId: String, val mes: YearMonth) : DestinoVencimento
    data class ParcelaEmprestimo(val obrigacaoId: String) : DestinoVencimento
}

data class Vencimento(
    val titulo: String,
    val vencimento: LocalDate,
    /** O que falta pagar (ou receber). */
    val valor: Long,
    val atrasado: Boolean,
    val destino: DestinoVencimento,
)

/** Bloco Cartões: o devido e a próxima fatura em aberto (valor e vencimento). */
data class ResumoCartaoInicio(val conta: Conta, val devido: Long, val proximaFatura: Fatura?)

object InicioCompleta {
    /** Janela dos blocos de vencimento: hoje + 7 dias (além dos atrasados). */
    const val DIAS = 7L

    private fun naJanela(venc: LocalDate?, hoje: LocalDate): Boolean = venc != null && venc <= hoje.plusDays(DIAS)

    /**
     * Bloco 1: contas a pagar (fixas e avulsas), faturas de cartão, parcelas de crediário e
     * de empréstimo recebido que vencem nos próximos 7 dias ou estão atrasadas, em aberto.
     */
    fun proximosVencimentos(va: VisaoAPagar?, vc: VisaoCredito?, ve: VisaoEmprestimos?, hoje: LocalDate): List<Vencimento> {
        val contas = va?.situacoes(hoje).orEmpty()
            .filter { !it.obrigacao.ehParcela && it.status != StatusObrigacao.QUITADA && it.restante > 0 && naJanela(it.obrigacao.vencimento, hoje) }
            .map { Vencimento(it.obrigacao.descricao, it.obrigacao.vencimento!!, it.restante, it.obrigacao.vencimento < hoje, DestinoVencimento.ContaAPagar(it.obrigacao.id)) }
        val faturas = vc?.let { c ->
            c.contasDeCredito.filter { !it.arquivada }.flatMap { conta ->
                c.faturas(conta.id, hoje).filter { it.emAberto && naJanela(it.vencimento, hoje) }.map { f ->
                    Vencimento((if (conta.natureza == Natureza.CARTAO) "Fatura " else "Parcelas ") + conta.nome, f.vencimento!!, f.restante, f.vencimento < hoje, DestinoVencimento.Fatura(conta.id, f.mes))
                }
            }
        }.orEmpty()
        val emprestimos = parcelasDeEmprestimo(ve, TipoEmprestimo.RECEBIDO, hoje)
        return (contas + faturas + emprestimos).sortedWith(compareBy({ it.vencimento }, { it.titulo }))
    }

    /** Bloco 4: parcelas de empréstimos concedidos (a receber) nos próximos 7 dias ou atrasadas. */
    fun aReceber(ve: VisaoEmprestimos?, hoje: LocalDate): List<Vencimento> = parcelasDeEmprestimo(ve, TipoEmprestimo.CONCEDIDO, hoje)

    private fun parcelasDeEmprestimo(ve: VisaoEmprestimos?, tipo: TipoEmprestimo, hoje: LocalDate): List<Vencimento> = ve?.let { e ->
        e.situacoes(tipo, hoje).flatMap { s ->
            s.parcelas.filter { it.restante > 0 && naJanela(it.obrigacao.vencimento, hoje) }.map { p ->
                val venc = p.obrigacao.vencimento!!
                Vencimento(p.obrigacao.descricao + (e.conta(s.emprestimo.contaId)?.let { " · " + it.nome } ?: ""), venc, p.restante, venc < hoje,
                    DestinoVencimento.ParcelaEmprestimo(p.obrigacao.id))
            }
        }.sortedWith(compareBy({ it.vencimento }, { it.titulo }))
    }.orEmpty()

    /** Bloco 2: cada cartão (não arquivado) com devido ou fatura em aberto. */
    fun cartoes(vc: VisaoCredito?, hoje: LocalDate): List<ResumoCartaoInicio> = vc?.let { c ->
        c.contasDeCredito.filter { it.natureza == Natureza.CARTAO && !it.arquivada }.map { conta ->
            ResumoCartaoInicio(conta, c.devido(conta.id), c.faturas(conta.id, hoje).firstOrNull { it.emAberto })
        }.filter { it.devido > 0 || it.proximaFatura != null }
    }.orEmpty()

    /** Bloco 3: até 3 caixinhas ativas (primeiro as com dinheiro), e se há mais para "Ver todas". */
    fun caixinhas(vo: VisaoObjetivos): Pair<List<SituacaoObjetivo>, Boolean> {
        val ativos = vo.ativos.sortedByDescending { it.guardado }
        return ativos.take(3) to (vo.objetivos.size > 3)
    }
}
