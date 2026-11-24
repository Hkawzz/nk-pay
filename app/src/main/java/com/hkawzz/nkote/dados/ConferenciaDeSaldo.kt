package com.hkawzz.nkote.dados

import androidx.room.withTransaction
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Saldos

// =====================================================================================
// v0.8 (regra 6G.1, R1, ADR 0031): saída sem saldo é recusada. UM ponto só, na camada de
// dados: a transação compara os saldos de antes e de depois e desfaz TUDO se uma conta
// própria, de benefício ou de caixinha ficou negativa E menor do que estava. Assim
// despesas, transferências, pagamentos, empréstimos, exclusões, edições e desfazer passam
// todos pela mesma regra. Contas que já estavam negativas só aceitam o que não as piora.
// =====================================================================================

/** Naturezas conferidas pela R1 (dinheiro seu). Contas de terceiros seguem regras próprias. */
private val NATUREZAS_CONFERIDAS = setOf("PROPRIO", "BENEFICIO", "CAIXINHA")

/** Lançada dentro da transação para desfazê-la; vira [Resultado.Falha] fora dela. */
class SaldoInsuficiente(mensagem: String) : RuntimeException(mensagem)

private data class SaldoVigiado(val nome: String, val saldo: Long)

private suspend fun NkPayBanco.saldosVigiados(): Map<String, SaldoVigiado> {
    val b = backupDao()
    val contas = b.contas().filter { it.excluidoEm == null && it.natureza in NATUREZAS_CONFERIDAS }
    val movs = b.movimentacoes().filter { it.excluidoEm == null }.map { it.paraDominio() }
    val saldos = Saldos.saldosPorConta(contas.map { it.paraDominio() }, movs)
    return contas.associate { it.id to SaldoVigiado(it.nome, saldos[it.id] ?: 0) }
}

/** A mensagem da R1, com o saldo da conta antes da operação. */
fun mensagemSaldoInsuficiente(nomeConta: String, saldo: Long): String =
    "Saldo insuficiente no $nomeConta (${Dinheiro.formatar(saldo)}). Registre a entrada antes ou escolha outra conta."

/**
 * Roda [bloco] numa transação e confere a R1 no fim. Se [bloco] der [Resultado.Sucesso] e
 * alguma conta conferida ficar negativa e menor do que estava, nada é gravado e o
 * resultado é a falha com o saldo da conta. Contas criadas dentro do bloco não são
 * conferidas (o saldo inicial pode ser negativo, como sempre foi).
 */
suspend fun <T> NkPayBanco.transacaoConferida(bloco: suspend () -> Resultado<T>): Resultado<T> = try {
    withTransaction {
        val antes = saldosVigiados()
        val r = bloco()
        if (r is Resultado.Sucesso) {
            for ((id, depois) in saldosVigiados()) {
                val a = antes[id] ?: continue
                if (depois.saldo < 0 && depois.saldo < a.saldo) throw SaldoInsuficiente(mensagemSaldoInsuficiente(a.nome, a.saldo))
            }
        }
        r
    }
} catch (e: SaldoInsuficiente) {
    Resultado.Falha(e.message ?: "Saldo insuficiente.")
}
