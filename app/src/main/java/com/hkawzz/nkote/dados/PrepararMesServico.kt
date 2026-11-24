package com.hkawzz.nkote.dados

import androidx.room.withTransaction
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Obrigacoes
import com.hkawzz.nkote.dominio.OrigemObrigacao
import java.time.YearMonth

/** Uma conta fixa marcada no assistente, com o valor (sugerido ou editado). */
data class FixaEscolhida(val contaFixaId: String, val valor: Long)

data class ResultadoPreparo(val fixasLancadas: Int, val pendenciasLevadas: Int)

/**
 * Preparar próximo mês (v0.6, regra 7.6, ADR 0026). A confirmação grava TUDO numa
 * transação, com um grupo só no Registro de alterações:
 * - as contas fixas marcadas, com o valor escolhido (as já lançadas no mês são ignoradas);
 * - a marcação das pendências levadas (o mês de referência, o vencimento e o valor da
 *   obrigação NÃO mudam; ela continua atrasada). Uma já levada para o mês é ignorada.
 * Repetir para o mesmo mês nunca duplica nada.
 */
class PrepararMesServico(
    private val banco: NkPayBanco,
    private val auditor: Auditor,
    private val relogio: Relogio,
) {
    private val obrigacaoDao = banco.obrigacaoDao()
    private val fixaDao = banco.contaFixaDao()
    private val levadaDao = banco.pendenciaLevadaDao()

    suspend fun confirmar(mes: YearMonth, fixas: List<FixaEscolhida>, pendencias: Collection<String>): Resultado<ResultadoPreparo> {
        fixas.firstOrNull { it.valor <= 0 }?.let { return Resultado.Falha("Toda conta fixa marcada precisa de um valor maior que zero.") }
        fixas.firstOrNull { it.valor > Dinheiro.LIMITE_CENTAVOS }?.let { return Resultado.Falha("Valor alto demais.") }
        return banco.withTransaction {
            val grupo = relogio.novoId()
            val agora = relogio.agora()
            var lancadas = 0
            var levadas = 0
            // Confere TUDO antes de gravar: uma falha no meio não pode deixar meio preparo.
            val fixasValidas = fixas.distinctBy { it.contaFixaId }.map { escolha ->
                (fixaDao.buscar(escolha.contaFixaId)?.takeIf { it.excluidoEm == null } ?: return@withTransaction Resultado.Falha("Conta fixa não encontrada.")) to escolha
            }
            val obrigacoes = pendencias.distinct().map { id ->
                val o = obrigacaoDao.buscar(id)?.takeIf { it.excluidoEm == null && it.contaCreditoId == null }
                    ?: return@withTransaction Resultado.Falha("Pendência não encontrada.")
                val ref = o.mesReferencia?.let { YearMonth.parse(it) }
                if (ref == null || ref >= mes) return@withTransaction Resultado.Falha("“${o.descricao}” não é de um mês anterior.")
                o
            }
            for ((f, escolha) in fixasValidas) {
                if (obrigacaoDao.contarDaFixaNoMes(f.id, mes.toString()) > 0) continue
                val o = ObrigacaoEntidade(
                    id = relogio.novoId(), origem = OrigemObrigacao.FIXA.name, descricao = f.nome,
                    valorPrevistoCentavos = escolha.valor, vencimento = Obrigacoes.vencimentoNoMes(f.diaVencimento, mes)?.toString(),
                    mesReferencia = mes.toString(), contaFixaId = f.id, categoriaId = f.categoriaId,
                    contaCreditoId = null, compraId = null, numeroParcela = null, totalParcelas = null,
                    canceladaEm = null, criadoEm = agora, atualizadoEm = agora, excluidoEm = null,
                )
                obrigacaoDao.inserir(o)
                auditor.registrar(grupo, Operacao.CRIACAO, Auditor.OBRIGACAO, o.id,
                    "Conta fixa “${f.nome}” lançada em $mes (${Dinheiro.formatar(o.valorPrevistoCentavos)}) ao preparar o mês", ObrigacaoEntidade.serializer(), null, o)
                lancadas++
            }
            for (o in obrigacoes) {
                val situacao = Obrigacoes.situacao(
                    o.paraDominio(),
                    banco.pagamentoDao().validosDa(o.id).map { it.paraDominio() },
                    banco.ajusteObrigacaoDao().validosDa(o.id).map { it.paraDominio() },
                    relogio.hoje(),
                )
                if (situacao.restante <= 0) continue
                if (levadaDao.contar(o.id, mes.toString()) > 0) continue
                val p = PendenciaLevadaEntidade(relogio.novoId(), o.id, mes.toString(), agora, agora, null)
                levadaDao.inserir(p)
                auditor.registrar(grupo, Operacao.CRIACAO, Auditor.PENDENCIA_LEVADA, p.id,
                    "Pendência “${o.descricao}” (ref. ${o.mesReferencia}) levada para aparecer também em $mes", PendenciaLevadaEntidade.serializer(), null, p)
                levadas++
            }
            Resultado.Sucesso(ResultadoPreparo(lancadas, levadas))
        }
    }
}
