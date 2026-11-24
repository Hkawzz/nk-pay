package com.hkawzz.nkote.dados

import androidx.room.withTransaction
import com.hkawzz.nkote.dominio.AparenciaSugerida
import com.hkawzz.nkote.dominio.CategoriasDoSistema
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Objetivo
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.TipoMovimentacao
import java.time.LocalDate

/**
 * Objetivos como CAIXINHAS (v0.8, R2, regra 6G.2, ADR 0032). Cada objetivo tem uma conta
 * interna de natureza CAIXINHA, que começa zerada:
 * - guardar = transferência conta própria → caixinha (a R1 confere o saldo da conta);
 * - retirar = transferência caixinha → conta própria;
 * - mover = transferência caixinha → caixinha;
 * - rendimento = ENTRADA na caixinha, categoria de sistema "Rendimentos".
 * Tudo numa transação conferida pela R1, com auditoria. A caixinha nunca é meio de
 * pagamento (validado em MovimentacaoRepositorio, PagamentoServico, PagamentoCreditoServico...).
 */
class ObjetivoServico(
    private val banco: NkPayBanco,
    private val auditor: Auditor,
    private val relogio: Relogio,
) {
    private val dao = banco.objetivoDao()
    private val contaDao = banco.contaDao()
    private val movDao = banco.movimentacaoDao()
    private val categoriaDao = banco.categoriaDao()

    companion object {
        fun validar(o: Objetivo): String? = when {
            o.nome.isBlank() -> "Dê um nome para o objetivo."
            o.nome.length > 60 -> "O nome pode ter no máximo 60 caracteres."
            o.meta != null && o.meta <= 0 -> "A meta precisa ser maior que zero (ou deixe sem meta)."
            o.meta != null && o.meta > Dinheiro.LIMITE_CENTAVOS -> "Meta alta demais."
            else -> null
        }
    }

    // ---------------------------------------------------------------- apoio (dentro da transação)

    private suspend fun valido(id: String): ObjetivoEntidade? = dao.buscar(id)?.takeIf { it.excluidoEm == null }

    private suspend fun saldo(contaId: String): Long {
        val conta = contaDao.buscar(contaId) ?: return 0
        val movs = banco.backupDao().movimentacoes().filter { it.excluidoEm == null && (it.contaId == contaId || it.contaDestinoId == contaId) }.map { it.paraDominio() }
        return Saldos.saldoDaConta(conta.paraDominio(), movs)
    }

    /** A conta CAIXINHA do objetivo; cria (zerada) se ainda não existir (objetivos da v0.7, V3). */
    private suspend fun caixinha(o: ObjetivoEntidade, grupo: String): ContaEntidade {
        o.contaCaixinhaId?.let { id -> contaDao.buscar(id)?.let { return it } }
        val agora = relogio.agora()
        val c = ContaEntidade(
            id = relogio.novoId(), nome = o.nome, natureza = Natureza.CAIXINHA.name, tipoBeneficio = null,
            saldoInicialCentavos = 0, dataSaldoInicial = relogio.hoje().toString(), icone = o.icone, cor = o.cor,
            instituicao = null, limiteCentavos = null, diaFechamento = null, diaVencimento = null,
            arquivadaEm = null, criadoEm = agora, atualizadoEm = agora, excluidoEm = null,
        )
        contaDao.inserir(c)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.CONTA, c.id, "Caixinha “${c.nome}” criada (zerada)", ContaEntidade.serializer(), null, c)
        val depois = o.copy(contaCaixinhaId = c.id, atualizadoEm = agora)
        dao.atualizar(depois)
        auditor.registrar(grupo, Operacao.EDICAO, Auditor.OBJETIVO, o.id, "Objetivo “${o.nome}” ligado à caixinha", ObjetivoEntidade.serializer(), o, depois)
        return c
    }

    /** Conta própria (nunca benefício, caixinha nem terceiros) para guardar e retirar. */
    private suspend fun contaPropria(id: String): ContaEntidade? =
        contaDao.buscar(id)?.takeIf { it.excluidoEm == null && it.natureza == Natureza.PROPRIO.name }

    private suspend fun transferencia(grupo: String, de: ContaEntidade, para: ContaEntidade, valor: Long, data: LocalDate, descricao: String, resumo: String): MovimentacaoEntidade {
        val agora = relogio.agora()
        val m = MovimentacaoEntidade(relogio.novoId(), TipoMovimentacao.TRANSFERENCIA.name, data.toString(), valor, de.id, para.id, null, descricao, agora, agora, null)
        movDao.inserir(m)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.MOVIMENTACAO, m.id, resumo, MovimentacaoEntidade.serializer(), null, m)
        return m
    }

    /** A categoria de sistema "Rendimentos": existe ou é criada aqui, com auditoria. */
    private suspend fun categoriaRendimentos(grupo: String): String =
        categoriaDao.buscarPorCodigo(CategoriasDoSistema.RENDIMENTOS)?.id ?: run {
            val agora = relogio.agora()
            val a = AparenciaSugerida.ouNeutra("Rendimentos")
            val c = CategoriaEntidade(relogio.novoId(), "Rendimentos", CategoriasDoSistema.RENDIMENTOS, null, agora, agora, null, a.icone, a.cor)
            categoriaDao.inserir(c)
            auditor.registrar(grupo, Operacao.CRIACAO, Auditor.CATEGORIA, c.id, "Categoria de sistema “Rendimentos” criada", CategoriaEntidade.serializer(), null, c)
            c.id
        }

    private fun valorInvalido(valor: Long) = if (valor <= 0) Resultado.Falha("O valor precisa ser maior que zero.") else null

    // ---------------------------------------------------------------- cadastro

    /** Cria o objetivo e a caixinha dele, zerada. */
    suspend fun criar(o: Objetivo): Resultado<String> {
        val limpo = o.copy(nome = o.nome.trim())
        validar(limpo)?.let { return Resultado.Falha(it) }
        val agora = relogio.agora()
        val e = ObjetivoEntidade(relogio.novoId(), limpo.nome, limpo.icone, limpo.cor, limpo.meta, limpo.prazo?.toString(), null, false, agora, agora, null, null)
        banco.withTransaction {
            val grupo = relogio.novoId()
            dao.inserir(e)
            auditor.registrar(grupo, Operacao.CRIACAO, Auditor.OBJETIVO, e.id,
                "Objetivo “${e.nome}” criado" + (e.metaCentavos?.let { " (meta ${Dinheiro.formatar(it)})" } ?: " (sem meta)"), ObjetivoEntidade.serializer(), null, e)
            caixinha(e, grupo)
        }
        return Resultado.Sucesso(e.id)
    }

    /** Edita nome, ícone, cor, meta e prazo; a caixinha acompanha o nome, o ícone e a cor. */
    suspend fun editar(o: Objetivo): Resultado<Unit> {
        val limpo = o.copy(nome = o.nome.trim())
        validar(limpo)?.let { return Resultado.Falha(it) }
        return banco.withTransaction {
            val antes = valido(o.id) ?: return@withTransaction Resultado.Falha("Objetivo não encontrado.")
            val agora = relogio.agora()
            val depois = antes.copy(nome = limpo.nome, icone = limpo.icone, cor = limpo.cor, metaCentavos = limpo.meta, prazo = limpo.prazo?.toString(), atualizadoEm = agora)
            if (depois.copy(atualizadoEm = antes.atualizadoEm) == antes) return@withTransaction Resultado.Sucesso(Unit)
            val grupo = relogio.novoId()
            dao.atualizar(depois)
            auditor.registrar(grupo, Operacao.EDICAO, Auditor.OBJETIVO, antes.id, "Objetivo “${depois.nome}” editado", ObjetivoEntidade.serializer(), antes, depois)
            antes.contaCaixinhaId?.let { contaDao.buscar(it) }?.let { c ->
                val d = c.copy(nome = depois.nome, icone = depois.icone, cor = depois.cor, atualizadoEm = agora)
                if (d != c) { contaDao.atualizar(d); auditor.registrar(grupo, Operacao.EDICAO, Auditor.CONTA, c.id, "Caixinha “${d.nome}” renomeada", ContaEntidade.serializer(), c, d) }
            }
            Resultado.Sucesso(Unit)
        }
    }

    // ---------------------------------------------------------------- operações (6G.2)

    /** Guardar: transferência conta própria → caixinha. Só em objetivo ativo. @return a movimentação. */
    suspend fun guardar(id: String, contaOrigemId: String, valor: Long, data: LocalDate): Resultado<String> {
        valorInvalido(valor)?.let { return it }
        return banco.transacaoConferida {
            val o = valido(id) ?: return@transacaoConferida Resultado.Falha("Objetivo não encontrado.")
            if (o.concluidoEm != null || o.arquivado) return@transacaoConferida Resultado.Falha("Só dá para guardar em objetivo ativo. Reabra o objetivo antes.")
            val origem = contaPropria(contaOrigemId) ?: return@transacaoConferida Resultado.Falha("Escolha a conta de onde sai o dinheiro.")
            val grupo = relogio.novoId()
            val cx = caixinha(o, grupo)
            val m = transferencia(grupo, origem, cx, valor, data, "Guardar: ${o.nome}", "${Dinheiro.formatar(valor)} guardados em “${o.nome}” (de ${origem.nome})")
            Resultado.Sucesso(m.id)
        }
    }

    /** Retirar: transferência caixinha → conta própria. Não é despesa. */
    suspend fun retirar(id: String, contaDestinoId: String, valor: Long, data: LocalDate): Resultado<String> {
        valorInvalido(valor)?.let { return it }
        return banco.transacaoConferida {
            val o = valido(id) ?: return@transacaoConferida Resultado.Falha("Objetivo não encontrado.")
            val destino = contaPropria(contaDestinoId) ?: return@transacaoConferida Resultado.Falha("Escolha a conta para onde o dinheiro volta.")
            val grupo = relogio.novoId()
            val cx = caixinha(o, grupo)
            val g = saldo(cx.id)
            if (valor > g) return@transacaoConferida Resultado.Falha("Só há ${Dinheiro.formatar(g)} na caixinha “${o.nome}”.")
            val m = transferencia(grupo, cx, destino, valor, data, "Retirar: ${o.nome}", "${Dinheiro.formatar(valor)} retirados de “${o.nome}” (para ${destino.nome})")
            Resultado.Sucesso(m.id)
        }
    }

    /** Mover: transferência caixinha → caixinha (o destino precisa estar ativo). */
    suspend fun mover(deId: String, paraId: String, valor: Long, data: LocalDate): Resultado<String> {
        valorInvalido(valor)?.let { return it }
        if (deId == paraId) return Resultado.Falha("Escolha outro objetivo de destino.")
        return banco.transacaoConferida {
            val de = valido(deId) ?: return@transacaoConferida Resultado.Falha("Objetivo não encontrado.")
            val para = valido(paraId) ?: return@transacaoConferida Resultado.Falha("Objetivo de destino não encontrado.")
            if (para.concluidoEm != null || para.arquivado) return@transacaoConferida Resultado.Falha("O destino precisa ser um objetivo ativo.")
            val grupo = relogio.novoId()
            val cxDe = caixinha(de, grupo)
            val cxPara = caixinha(para, grupo)
            val g = saldo(cxDe.id)
            if (valor > g) return@transacaoConferida Resultado.Falha("Só há ${Dinheiro.formatar(g)} na caixinha “${de.nome}”.")
            val m = transferencia(grupo, cxDe, cxPara, valor, data, "Mover: ${de.nome} → ${para.nome}", "${Dinheiro.formatar(valor)} movidos de “${de.nome}” para “${para.nome}”")
            Resultado.Sucesso(m.id)
        }
    }

    /** Rendimento: ENTRADA na caixinha, categoria "Rendimentos" (é receita). */
    suspend fun rendimento(id: String, valor: Long, data: LocalDate): Resultado<String> {
        valorInvalido(valor)?.let { return it }
        return banco.transacaoConferida {
            val o = valido(id) ?: return@transacaoConferida Resultado.Falha("Objetivo não encontrado.")
            val grupo = relogio.novoId()
            val cx = caixinha(o, grupo)
            val categoria = categoriaRendimentos(grupo)
            val agora = relogio.agora()
            val m = MovimentacaoEntidade(relogio.novoId(), TipoMovimentacao.ENTRADA.name, data.toString(), valor, cx.id, null, categoria, "Rendimento: ${o.nome}", agora, agora, null)
            movDao.inserir(m)
            auditor.registrar(grupo, Operacao.CRIACAO, Auditor.MOVIMENTACAO, m.id, "Rendimento de ${Dinheiro.formatar(valor)} em “${o.nome}”", MovimentacaoEntidade.serializer(), null, m)
            Resultado.Sucesso(m.id)
        }
    }

    /** Desfaz um guardar, retirar, mover ou rendimento (exclusão lógica), com a R1 nas duas contas. */
    suspend fun desfazer(movimentacaoId: String): Resultado<Unit> = banco.transacaoConferida {
        val m = movDao.buscar(movimentacaoId)?.takeIf { it.excluidoEm == null } ?: return@transacaoConferida Resultado.Falha("Movimento não encontrado.")
        val ehDeCaixinha = listOfNotNull(m.contaId, m.contaDestinoId).any { contaDao.buscar(it)?.natureza == Natureza.CAIXINHA.name }
        if (!ehDeCaixinha) return@transacaoConferida Resultado.Falha("Esta movimentação não é de uma caixinha.")
        val agora = relogio.agora()
        val d = m.copy(excluidoEm = agora, atualizadoEm = agora)
        movDao.atualizar(d)
        auditor.registrar(relogio.novoId(), Operacao.EXCLUSAO, Auditor.MOVIMENTACAO, m.id,
            "Movimento de caixinha de ${Dinheiro.formatar(m.valorCentavos)} “${m.descricao}” desfeito", MovimentacaoEntidade.serializer(), m, d)
        Resultado.Sucesso(Unit)
    }

    // ---------------------------------------------------------------- concluir, arquivar, excluir (O3)

    suspend fun concluir(id: String, concluir: Boolean = true): Resultado<Unit> = alterar(id) { o ->
        if ((o.concluidoEm != null) == concluir) null
        else o.copy(concluidoEm = if (concluir) relogio.agora() else null) to (if (concluir) "concluído" else "reaberto")
    }

    suspend fun arquivar(id: String, arquivar: Boolean = true): Resultado<Unit> = alterar(id) { o ->
        if (o.arquivado == arquivar) null else o.copy(arquivado = arquivar) to (if (arquivar) "arquivado" else "desarquivado")
    }

    private suspend fun alterar(id: String, mudanca: (ObjetivoEntidade) -> Pair<ObjetivoEntidade, String>?): Resultado<Unit> = banco.withTransaction {
        val antes = valido(id) ?: return@withTransaction Resultado.Falha("Objetivo não encontrado.")
        val (depois, verbo) = mudanca(antes) ?: return@withTransaction Resultado.Sucesso(Unit)
        val d = depois.copy(atualizadoEm = relogio.agora())
        dao.atualizar(d)
        auditor.registrar(relogio.novoId(), Operacao.EDICAO, Auditor.OBJETIVO, id, "Objetivo “${antes.nome}” $verbo", ObjetivoEntidade.serializer(), antes, d)
        Resultado.Sucesso(Unit)
    }

    /**
     * Excluir com dinheiro na caixinha é recusado (O3 = a). Sem dinheiro, o objetivo é
     * excluído (lógico) e a caixinha é ARQUIVADA, não excluída: o histórico das
     * transferências continua sendo de dinheiro seu (não vira saída no fluxo).
     */
    suspend fun excluir(id: String): Resultado<Unit> = banco.withTransaction {
        val antes = valido(id) ?: return@withTransaction Resultado.Falha("Objetivo não encontrado.")
        val cx = antes.contaCaixinhaId?.let { contaDao.buscar(it) }
        val g = cx?.let { saldo(it.id) } ?: 0
        if (g != 0L) return@withTransaction Resultado.Falha("Retire ou mova os ${Dinheiro.formatar(g)} antes de excluir “${antes.nome}”.")
        val agora = relogio.agora()
        val grupo = relogio.novoId()
        val d = antes.copy(excluidoEm = agora, atualizadoEm = agora)
        dao.atualizar(d)
        auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.OBJETIVO, id, "Objetivo “${antes.nome}” excluído", ObjetivoEntidade.serializer(), antes, d)
        if (cx != null && cx.arquivadaEm == null) {
            val dc = cx.copy(arquivadaEm = agora, atualizadoEm = agora)
            contaDao.atualizar(dc)
            auditor.registrar(grupo, Operacao.EDICAO, Auditor.CONTA, cx.id, "Caixinha “${cx.nome}” arquivada", ContaEntidade.serializer(), cx, dc)
        }
        Resultado.Sucesso(Unit)
    }

    /**
     * V3 (ADR 0032): dá uma caixinha ZERADA a cada objetivo da v0.7 que ainda não tem.
     * Nenhuma transferência é criada e nenhum saldo de conta muda. Idempotente.
     * @return quantos objetivos foram convertidos.
     */
    suspend fun converterObjetivosAntigos(): Int = banco.withTransaction {
        val semCaixinha = dao.validos().filter { it.contaCaixinhaId == null }
        if (semCaixinha.isEmpty()) return@withTransaction 0
        val grupo = relogio.novoId()
        for (o in semCaixinha) caixinha(o, grupo)
        semCaixinha.size
    }
}
