package com.hkawzz.nkote.dados

import androidx.room.withTransaction
import com.hkawzz.nkote.dominio.ContaFixa
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Obrigacao
import com.hkawzz.nkote.dominio.Obrigacoes
import com.hkawzz.nkote.dominio.OrigemObrigacao
import com.hkawzz.nkote.dominio.Validacao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth

// Repositórios das contas a pagar (v0.3). Mesma regra de ouro da versão 1:
// toda alteração roda em uma transação e grava a auditoria junto.

class ContaFixaRepositorio(
    private val banco: NkPayBanco,
    private val auditor: Auditor,
    private val relogio: Relogio,
) {
    private val dao = banco.contaFixaDao()

    fun observar(): Flow<List<ContaFixa>> = dao.observarTodas().map { l -> l.map { it.paraDominio() } }

    suspend fun buscar(id: String): ContaFixa? = dao.buscar(id)?.takeIf { it.excluidoEm == null }?.paraDominio()

    /** v0.8 (R2): a conta padrão de uma conta fixa precisa ser meio de pagamento (nunca caixinha nem terceiros). */
    private suspend fun contaPadraoInvalida(id: String?): Boolean =
        id != null && banco.contaDao().buscar(id)?.let { com.hkawzz.nkote.dominio.Natureza.valueOf(it.natureza).ehMeioDePagamento } != true

    /**
     * Cria a conta fixa. v0.9 (item 6): com [atrasada], cria junto a ocorrência vencida e não
     * paga (mês de referência = mês do vencimento), que aparece na hora em A pagar como atrasada.
     */
    suspend fun criar(f: ContaFixa, atrasada: OcorrenciaAtrasada? = null): Resultado<String> {
        val limpa = f.copy(nome = f.nome.trim())
        Validacao.contaFixa(limpa)?.let { return Resultado.Falha(it) }
        if (contaPadraoInvalida(limpa.contaPadraoId)) return Resultado.Falha("Escolha uma conta própria ou de benefício como conta padrão.")
        atrasada?.let { validarAtrasada(it) }?.let { return Resultado.Falha(it) }
        val agora = relogio.agora()
        val nova = ContaFixaEntidade(
            id = relogio.novoId(), nome = limpa.nome, valorPadraoCentavos = limpa.valorPadrao,
            diaVencimento = limpa.diaVencimento, categoriaId = limpa.categoriaId, contaPadraoId = limpa.contaPadraoId,
            arquivadaEm = null, criadoEm = agora, atualizadoEm = agora, excluidoEm = null,
        )
        banco.withTransaction {
            val grupo = relogio.novoId()
            dao.inserir(nova)
            auditor.registrar(
                grupo, Operacao.CRIACAO, Auditor.CONTA_FIXA, nova.id,
                "Conta fixa “${nova.nome}” criada (${Dinheiro.formatar(nova.valorPadraoCentavos)})",
                ContaFixaEntidade.serializer(), null, nova,
            )
            if (atrasada != null) inserirAtrasada(nova, atrasada, grupo)
        }
        return Resultado.Sucesso(nova.id)
    }

    /** v0.9 (item 6): a ocorrência vencida e não paga informada no cadastro ou na edição. */
    data class OcorrenciaAtrasada(val vencimento: java.time.LocalDate, val valor: Long)

    private fun validarAtrasada(a: OcorrenciaAtrasada): String? = when {
        !a.vencimento.isBefore(relogio.hoje()) -> "A data da conta vencida precisa ser anterior a hoje."
        a.valor <= 0 -> "Informe o valor da conta vencida."
        else -> null
    }

    /** Insere a ocorrência atrasada. Uma por conta fixa por mês (a regra de sempre). */
    private suspend fun inserirAtrasada(fixa: ContaFixaEntidade, a: OcorrenciaAtrasada, grupo: String): Resultado<String> {
        val obrigacaoDao = banco.obrigacaoDao()
        val mes = YearMonth.from(a.vencimento)
        if (obrigacaoDao.contarDaFixaNoMes(fixa.id, mes.toString()) > 0) {
            return Resultado.Falha("Esta conta fixa já foi lançada em ${"%02d/%d".format(mes.monthValue, mes.year)}.")
        }
        val agora = relogio.agora()
        val o = ObrigacaoEntidade(
            id = relogio.novoId(), origem = OrigemObrigacao.FIXA.name, descricao = fixa.nome,
            valorPrevistoCentavos = a.valor, vencimento = a.vencimento.toString(), mesReferencia = mes.toString(),
            contaFixaId = fixa.id, categoriaId = fixa.categoriaId,
            contaCreditoId = null, compraId = null, numeroParcela = null, totalParcelas = null,
            canceladaEm = null, criadoEm = agora, atualizadoEm = agora, excluidoEm = null,
        )
        obrigacaoDao.inserir(o)
        auditor.registrar(
            grupo, Operacao.CRIACAO, Auditor.OBRIGACAO, o.id,
            "Conta fixa “${fixa.nome}” vencida em ${a.vencimento} e não paga lançada (${Dinheiro.formatar(a.valor)})",
            ObrigacaoEntidade.serializer(), null, o,
        )
        return Resultado.Sucesso(o.id)
    }

    /** v0.9 (item 6): na edição, lança a ocorrência vencida e não paga de uma conta fixa que já existe. */
    suspend fun lancarAtrasada(fixaId: String, a: OcorrenciaAtrasada): Resultado<String> {
        validarAtrasada(a)?.let { return Resultado.Falha(it) }
        return banco.withTransaction {
            val fixa = dao.buscar(fixaId)?.takeIf { it.excluidoEm == null }
                ?: return@withTransaction Resultado.Falha("Conta fixa não encontrada.")
            inserirAtrasada(fixa, a, relogio.novoId())
        }
    }

    /** Muda o modelo. As obrigações já lançadas não mudam (guardam o valor de quando foram lançadas). */
    suspend fun editar(f: ContaFixa): Resultado<Unit> {
        val limpa = f.copy(nome = f.nome.trim())
        Validacao.contaFixa(limpa)?.let { return Resultado.Falha(it) }
        if (contaPadraoInvalida(limpa.contaPadraoId)) return Resultado.Falha("Escolha uma conta própria ou de benefício como conta padrão.")
        return banco.withTransaction {
            val antes = dao.buscar(f.id)?.takeIf { it.excluidoEm == null }
                ?: return@withTransaction Resultado.Falha("Conta fixa não encontrada.")
            val depois = antes.copy(
                nome = limpa.nome, valorPadraoCentavos = limpa.valorPadrao, diaVencimento = limpa.diaVencimento,
                categoriaId = limpa.categoriaId, contaPadraoId = limpa.contaPadraoId, atualizadoEm = relogio.agora(),
            )
            if (depois.copy(atualizadoEm = antes.atualizadoEm) == antes) return@withTransaction Resultado.Sucesso(Unit)
            dao.atualizar(depois)
            auditor.registrar(
                relogio.novoId(), Operacao.EDICAO, Auditor.CONTA_FIXA, antes.id,
                "Conta fixa “${depois.nome}” editada", ContaFixaEntidade.serializer(), antes, depois,
            )
            Resultado.Sucesso(Unit)
        }
    }

    /** Arquivar: deixa de aparecer para lançar nos próximos meses. O que já foi lançado continua. */
    suspend fun arquivar(id: String, arquivar: Boolean = true): Resultado<Unit> = banco.withTransaction {
        val antes = dao.buscar(id)?.takeIf { it.excluidoEm == null }
            ?: return@withTransaction Resultado.Falha("Conta fixa não encontrada.")
        if ((antes.arquivadaEm != null) == arquivar) return@withTransaction Resultado.Sucesso(Unit)
        val agora = relogio.agora()
        val depois = antes.copy(arquivadaEm = if (arquivar) agora else null, atualizadoEm = agora)
        dao.atualizar(depois)
        auditor.registrar(
            relogio.novoId(), Operacao.EDICAO, Auditor.CONTA_FIXA, id,
            if (arquivar) "Conta fixa “${antes.nome}” desativada" else "Conta fixa “${antes.nome}” reativada",
            ContaFixaEntidade.serializer(), antes, depois,
        )
        Resultado.Sucesso(Unit)
    }
}

class ObrigacaoRepositorio(
    private val banco: NkPayBanco,
    private val auditor: Auditor,
    private val relogio: Relogio,
) {
    private val dao = banco.obrigacaoDao()
    private val fixaDao = banco.contaFixaDao()
    private val categoriaDao = banco.categoriaDao()

    fun observar(): Flow<List<Obrigacao>> = dao.observarTodas().map { l -> l.map { it.paraDominio() } }

    suspend fun buscar(id: String): Obrigacao? = dao.buscar(id)?.takeIf { it.excluidoEm == null }?.paraDominio()

    private suspend fun categoriaValida(id: String?): Boolean {
        if (id == null) return true
        val c = categoriaDao.buscar(id)
        return c != null && c.excluidoEm == null
    }

    /** Conta avulsa (algo a pagar que não se repete). */
    suspend fun criarAvulsa(o: Obrigacao): Resultado<String> {
        val limpa = o.copy(descricao = o.descricao.trim(), origem = OrigemObrigacao.AVULSA, contaFixaId = null)
        Validacao.obrigacao(limpa)?.let { return Resultado.Falha(it) }
        if (!categoriaValida(limpa.categoriaId)) return Resultado.Falha("Categoria não encontrada.")
        val nova = entidade(limpa)
        banco.withTransaction {
            dao.inserir(nova)
            auditor.registrar(
                relogio.novoId(), Operacao.CRIACAO, Auditor.OBRIGACAO, nova.id,
                "Conta a pagar “${nova.descricao}” criada (${Dinheiro.formatar(nova.valorPrevistoCentavos)})",
                ObrigacaoEntidade.serializer(), null, nova,
            )
        }
        return Resultado.Sucesso(nova.id)
    }

    private fun entidade(o: Obrigacao): ObrigacaoEntidade {
        val agora = relogio.agora()
        return ObrigacaoEntidade(
            id = relogio.novoId(), origem = o.origem.name, descricao = o.descricao,
            valorPrevistoCentavos = o.valorPrevisto, vencimento = o.vencimento?.toString(),
            mesReferencia = (o.mesReferencia ?: o.vencimento?.let { YearMonth.from(it) })?.toString(),
            contaFixaId = o.contaFixaId, categoriaId = o.categoriaId,
            contaCreditoId = null, compraId = null, numeroParcela = null, totalParcelas = null,
            canceladaEm = null, criadoEm = agora, atualizadoEm = agora, excluidoEm = null,
        )
    }

    /**
     * Lança as contas fixas ESCOLHIDAS no mês (seleção manual, ADR 0013).
     * Uma conta fixa que já tem obrigação naquele mês é ignorada: repetir a operação
     * nunca duplica. Tudo em uma transação, com um grupo de auditoria só.
     * @return quantas obrigações foram criadas.
     */
    suspend fun lancarFixas(mes: YearMonth, idsDasFixas: Collection<String>): Resultado<Int> = banco.withTransaction {
        val grupo = relogio.novoId()
        var criadas = 0
        for (id in idsDasFixas.distinct()) {
            val f = fixaDao.buscar(id)?.takeIf { it.excluidoEm == null } ?: continue
            if (dao.contarDaFixaNoMes(f.id, mes.toString()) > 0) continue
            val nova = entidade(
                Obrigacao(
                    id = "", origem = OrigemObrigacao.FIXA, descricao = f.nome, valorPrevisto = f.valorPadraoCentavos,
                    vencimento = Obrigacoes.vencimentoNoMes(f.diaVencimento, mes), mesReferencia = mes,
                    contaFixaId = f.id, categoriaId = f.categoriaId,
                ),
            )
            dao.inserir(nova)
            auditor.registrar(
                grupo, Operacao.CRIACAO, Auditor.OBRIGACAO, nova.id,
                "Conta fixa “${f.nome}” lançada em $mes (${Dinheiro.formatar(nova.valorPrevistoCentavos)})",
                ObrigacaoEntidade.serializer(), null, nova,
            )
            criadas++
        }
        Resultado.Sucesso(criadas)
    }

    /** Edita descrição, valor previsto, vencimento, mês e categoria. */
    suspend fun editar(o: Obrigacao): Resultado<Unit> {
        val limpa = o.copy(descricao = o.descricao.trim())
        Validacao.obrigacao(limpa)?.let { return Resultado.Falha(it) }
        if (!categoriaValida(limpa.categoriaId)) return Resultado.Falha("Categoria não encontrada.")
        return banco.withTransaction {
            val antes = dao.buscar(o.id)?.takeIf { it.excluidoEm == null }
                ?: return@withTransaction Resultado.Falha("Conta a pagar não encontrada.")
            val novoMes = (limpa.mesReferencia ?: limpa.vencimento?.let { YearMonth.from(it) })?.toString()
            if (antes.contaFixaId != null && novoMes != antes.mesReferencia &&
                novoMes != null && dao.contarDaFixaNoMes(antes.contaFixaId, novoMes) > 0
            ) {
                return@withTransaction Resultado.Falha("Esta conta fixa já foi lançada nesse mês.")
            }
            val depois = antes.copy(
                descricao = limpa.descricao, valorPrevistoCentavos = limpa.valorPrevisto,
                vencimento = limpa.vencimento?.toString(), mesReferencia = novoMes,
                categoriaId = limpa.categoriaId, atualizadoEm = relogio.agora(),
            )
            if (depois.copy(atualizadoEm = antes.atualizadoEm) == antes) return@withTransaction Resultado.Sucesso(Unit)
            dao.atualizar(depois)
            auditor.registrar(
                relogio.novoId(), Operacao.EDICAO, Auditor.OBRIGACAO, antes.id,
                "Conta a pagar “${depois.descricao}” editada", ObrigacaoEntidade.serializer(), antes, depois,
            )
            Resultado.Sucesso(Unit)
        }
    }

    /** Exclusão lógica. Bloqueada se já houver pagamento (exclua o pagamento antes). */
    suspend fun excluir(id: String): Resultado<Unit> = banco.withTransaction {
        val antes = dao.buscar(id)?.takeIf { it.excluidoEm == null }
            ?: return@withTransaction Resultado.Falha("Conta a pagar não encontrada.")
        if (dao.contarPagamentos(id) > 0) {
            return@withTransaction Resultado.Falha("Esta conta já tem pagamento. Exclua o pagamento antes.")
        }
        val agora = relogio.agora()
        val depois = antes.copy(excluidoEm = agora, atualizadoEm = agora)
        dao.atualizar(depois)
        auditor.registrar(
            relogio.novoId(), Operacao.EXCLUSAO, Auditor.OBRIGACAO, id,
            "Conta a pagar “${antes.descricao}” excluída", ObrigacaoEntidade.serializer(), antes, depois,
        )
        Resultado.Sucesso(Unit)
    }
}
