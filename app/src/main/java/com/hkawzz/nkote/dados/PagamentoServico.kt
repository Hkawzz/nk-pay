package com.hkawzz.nkote.dados

import androidx.room.withTransaction
import com.hkawzz.nkote.dominio.CategoriasDoSistema
import com.hkawzz.nkote.dominio.ClassificacaoDiferenca
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Obrigacoes
import com.hkawzz.nkote.dominio.Pagamentos
import com.hkawzz.nkote.dominio.ResultadoPlano
import com.hkawzz.nkote.dominio.TipoMovimentacao
import java.time.LocalDate

/**
 * O botão Pagar (v0.3). Regras: ADR 0013 e regras-financeiras.md, seção 6A.
 *
 * Um pagamento grava, em UMA transação:
 * - a despesa do valor pago na conta escolhida, na categoria da conta a pagar;
 * - se houve encargo (juros, multa...), uma SEGUNDA despesa só do encargo, em
 *   "Encargos financeiros", e o ajuste de acréscimo na conta a pagar;
 * - se houve desconto/abatimento, o ajuste de abatimento (sem movimentação: não há
 *   receita fictícia);
 * - a ligação (pagamento) entre cada despesa e a conta a pagar;
 * - tudo no Registro de alterações, com um grupo só.
 *
 * O ajuste de encargo aponta para o pagamento PRINCIPAL. Assim, excluir o pagamento
 * desfaz a operação inteira (despesas, ligações e ajustes) de uma vez.
 */
class PagamentoServico(
    private val banco: NkPayBanco,
    private val auditor: Auditor,
    private val relogio: Relogio,
) {
    private val obrigacaoDao = banco.obrigacaoDao()
    private val pagamentoDao = banco.pagamentoDao()
    private val ajusteDao = banco.ajusteObrigacaoDao()
    private val movDao = banco.movimentacaoDao()
    private val contaDao = banco.contaDao()
    private val categoriaDao = banco.categoriaDao()

    /** Restante atual de uma obrigação, lido do banco (dentro da transação). */
    private suspend fun restante(obrigacaoId: String): Long {
        val o = obrigacaoDao.buscar(obrigacaoId)!!.paraDominio()
        val pags = pagamentoDao.validosDa(obrigacaoId).map { it.paraDominio() }
        val ajs = ajusteDao.validosDa(obrigacaoId).map { it.paraDominio() }
        return Obrigacoes.situacao(o, pags, ajs, relogio.hoje()).restante
    }

    /**
     * Paga [valorPago] da obrigação, saindo da [contaId], na [data].
     * Se o valor for diferente do restante, [classificacao] é obrigatória.
     * @return o id do pagamento principal.
     */
    suspend fun pagar(
        obrigacaoId: String,
        contaId: String,
        data: LocalDate,
        valorPago: Long,
        classificacao: ClassificacaoDiferenca?,
    ): Resultado<String> = banco.withTransaction {
        val obrig = obrigacaoDao.buscar(obrigacaoId)?.takeIf { it.excluidoEm == null && it.canceladaEm == null }
            ?: return@withTransaction Resultado.Falha("Conta a pagar não encontrada.")
        // v0.4: parcelas de cartão/crediário são pagas por transferência, na tela do cartão (ADR 0015).
        if (obrig.contaCreditoId != null) return@withTransaction Resultado.Falha("Esta é uma parcela de cartão ou crediário. Pague pela tela do cartão.")
        val conta = contaDao.buscar(contaId)?.takeIf { it.excluidoEm == null }
            ?: return@withTransaction Resultado.Falha("Escolha a conta de onde sai o dinheiro.")

        val plano = when (val r = Pagamentos.planejar(restante(obrigacaoId), valorPago, classificacao)) {
            is ResultadoPlano.Pronto -> r.plano
            is ResultadoPlano.PrecisaClassificar -> return@withTransaction Resultado.Falha("Diga o que é a diferença de ${Dinheiro.formatar(r.diferenca)}.")
            is ResultadoPlano.Invalido -> return@withTransaction Resultado.Falha(r.mensagem)
        }
        val encargos = if (plano.encargo > 0) {
            categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS)
                ?: return@withTransaction Resultado.Falha("Categoria “Encargos financeiros” não encontrada.")
        } else {
            null
        }

        val grupo = relogio.novoId()
        val agora = relogio.agora()

        suspend fun despesa(valor: Long, categoriaId: String?, descricao: String): MovimentacaoEntidade {
            val m = MovimentacaoEntidade(
                id = relogio.novoId(), tipo = TipoMovimentacao.DESPESA.name, data = data.toString(),
                valorCentavos = valor, contaId = conta.id, contaDestinoId = null, categoriaId = categoriaId,
                descricao = descricao, criadoEm = agora, atualizadoEm = agora, excluidoEm = null,
            )
            movDao.inserir(m)
            auditor.registrar(
                grupo, Operacao.CRIACAO, Auditor.MOVIMENTACAO, m.id,
                "Despesa de ${Dinheiro.formatar(valor)} “$descricao” lançada", MovimentacaoEntidade.serializer(), null, m,
            )
            return m
        }

        suspend fun ligar(mov: MovimentacaoEntidade, valor: Long): PagamentoEntidade {
            val p = PagamentoEntidade(relogio.novoId(), obrig.id, mov.id, valor, agora, agora, null)
            pagamentoDao.inserir(p)
            auditor.registrar(
                grupo, Operacao.CRIACAO, Auditor.PAGAMENTO, p.id,
                "Pagamento de ${Dinheiro.formatar(valor)} em “${obrig.descricao}”", PagamentoEntidade.serializer(), null, p,
            )
            return p
        }

        suspend fun ajustar(pagamentoId: String, valor: Long, movimentacaoId: String?) {
            val natureza = plano.classificacao!!.natureza!!
            val a = ObrigacaoAjusteEntidade(
                relogio.novoId(), obrig.id, pagamentoId, natureza.name, valor, movimentacaoId, "", agora, agora, null,
            )
            ajusteDao.inserir(a)
            auditor.registrar(
                grupo, Operacao.CRIACAO, Auditor.AJUSTE_OBRIGACAO, a.id,
                "${natureza.rotulo} de ${Dinheiro.formatar(valor)} em “${obrig.descricao}”", ObrigacaoAjusteEntidade.serializer(), null, a,
            )
        }

        // 1) Despesa principal, na categoria da conta a pagar.
        val principal = despesa(plano.despesaPrincipal, obrig.categoriaId, "Pagamento: ${obrig.descricao}")
        val pagamento = ligar(principal, plano.despesaPrincipal)

        // 2) Encargo: despesa própria em "Encargos financeiros" + acréscimo na conta a pagar.
        if (plano.encargo > 0 && encargos != null) {
            val natureza = plano.classificacao!!.natureza!!
            val movEncargo = despesa(plano.encargo, encargos.id, "${natureza.rotulo}: ${obrig.descricao}")
            ligar(movEncargo, plano.encargo)
            ajustar(pagamento.id, plano.encargo, movEncargo.id)
        }

        // 3) Desconto/abatimento: só o ajuste (o valor não saiu de nenhuma conta).
        if (plano.abatimento > 0) ajustar(pagamento.id, plano.abatimento, null)

        Resultado.Sucesso(pagamento.id)
    }

    /**
     * Desfaz um pagamento inteiro, em uma transação: exclui (logicamente) a despesa,
     * a ligação e os ajustes que ele criou, inclusive a despesa de encargo.
     * A conta a pagar volta ao restante de antes.
     */
    suspend fun excluir(pagamentoId: String): Resultado<Unit> = banco.withTransaction {
        val principal = pagamentoDao.buscar(pagamentoId)?.takeIf { it.excluidoEm == null }
            ?: return@withTransaction Resultado.Falha("Pagamento não encontrado.")
        val grupo = relogio.novoId()
        val agora = relogio.agora()

        suspend fun excluirPagamentoEMov(p: PagamentoEntidade) {
            val depoisP = p.copy(excluidoEm = agora, atualizadoEm = agora)
            pagamentoDao.atualizar(depoisP)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.PAGAMENTO, p.id, "Pagamento de ${Dinheiro.formatar(p.valorAplicadoCentavos)} excluído", PagamentoEntidade.serializer(), p, depoisP)
            val m = movDao.buscar(p.movimentacaoId)
            if (m != null && m.excluidoEm == null && pagamentoDao.daMovimentacao(m.id).isEmpty()) {
                val depoisM = m.copy(excluidoEm = agora, atualizadoEm = agora)
                movDao.atualizar(depoisM)
                auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.MOVIMENTACAO, m.id, "Despesa de ${Dinheiro.formatar(m.valorCentavos)} “${m.descricao}” excluída", MovimentacaoEntidade.serializer(), m, depoisM)
            }
        }

        excluirPagamentoEMov(principal)
        for (a in ajusteDao.doPagamento(principal.id)) {
            val depoisA = a.copy(excluidoEm = agora, atualizadoEm = agora)
            ajusteDao.atualizar(depoisA)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.AJUSTE_OBRIGACAO, a.id, "Ajuste de ${Dinheiro.formatar(a.valorCentavos)} excluído", ObrigacaoAjusteEntidade.serializer(), a, depoisA)
            // Encargo: exclui também a despesa de encargo e a ligação dela.
            a.movimentacaoId?.let { movId -> pagamentoDao.daMovimentacao(movId).forEach { excluirPagamentoEMov(it) } }
        }
        Resultado.Sucesso(Unit)
    }
}
