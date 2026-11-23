package com.nkpay.app.dados

import androidx.room.withTransaction
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Objetivo
import com.nkpay.app.dominio.Objetivos
import com.nkpay.app.dominio.Saldos
import com.nkpay.app.dominio.TipoMovimentoReserva
import java.time.LocalDate

/**
 * Objetivos e reservas (v0.7, regra 6F, ADR 0029). Tudo numa transação, com auditoria.
 * Guardar, retirar, mover e ajustar NÃO criam movimentação de dinheiro: só movimentos
 * de reserva, que mudam o Guardado e, por ele, o Saldo disponível. Um objetivo nunca
 * fica com guardado negativo. As regras ficam aqui, e não só na tela.
 */
class ObjetivoServico(
    private val banco: NkPayBanco,
    private val auditor: Auditor,
    private val relogio: Relogio,
) {
    private val dao = banco.objetivoDao()
    private val movDao = banco.movimentoReservaDao()

    companion object {
        fun validar(o: Objetivo): String? = when {
            o.nome.isBlank() -> "Dê um nome para o objetivo."
            o.nome.length > 60 -> "O nome pode ter no máximo 60 caracteres."
            o.meta != null && o.meta <= 0 -> "A meta precisa ser maior que zero (ou deixe sem meta)."
            o.meta != null && o.meta > Dinheiro.LIMITE_CENTAVOS -> "Meta alta demais."
            else -> null
        }
    }

    // ---------------------------------------------------------------- leitura dentro da transação

    private suspend fun guardado(objetivoId: String): Long = movDao.validos().filter { it.objetivoId == objetivoId }.sumOf { it.valorCentavos }

    /** Saldo disponível = Saldo total (dinheiro próprio) − Guardado (regra 4 e 6F). */
    private suspend fun disponivel(): Long {
        val b = banco.backupDao()
        val contas = b.contas().filter { it.excluidoEm == null }.map { it.paraDominio() }
        val movs = b.movimentacoes().filter { it.excluidoEm == null }.map { it.paraDominio() }
        return Saldos.resumo(contas, movs, movDao.validos().sumOf { it.valorCentavos }).disponivel
    }

    private suspend fun valido(id: String): ObjetivoEntidade? = dao.buscar(id)?.takeIf { it.excluidoEm == null }

    private suspend fun movimento(grupo: String, objetivo: ObjetivoEntidade, data: LocalDate, valor: Long, tipo: TipoMovimentoReserva, resumo: String) {
        val agora = relogio.agora()
        val m = MovimentoReservaEntidade(relogio.novoId(), objetivo.id, data.toString(), valor, tipo.name, grupo, agora, agora, null)
        movDao.inserir(m)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.MOVIMENTO_RESERVA, m.id, resumo, MovimentoReservaEntidade.serializer(), null, m)
    }

    private fun semDisponivel(valor: Long, disponivel: Long) =
        Resultado.Falha("Não há disponível suficiente: você quer guardar ${Dinheiro.formatar(valor)}, mas o disponível é ${Dinheiro.formatar(maxOf(0, disponivel))}.")

    // ---------------------------------------------------------------- cadastro

    suspend fun criar(o: Objetivo): Resultado<String> {
        val limpo = o.copy(nome = o.nome.trim())
        validar(limpo)?.let { return Resultado.Falha(it) }
        val agora = relogio.agora()
        val e = ObjetivoEntidade(relogio.novoId(), limpo.nome, limpo.icone, limpo.cor, limpo.meta, limpo.prazo?.toString(), null, false, agora, agora, null)
        banco.withTransaction {
            dao.inserir(e)
            auditor.registrar(relogio.novoId(), Operacao.CRIACAO, Auditor.OBJETIVO, e.id,
                "Objetivo “${e.nome}” criado" + (e.metaCentavos?.let { " (meta ${Dinheiro.formatar(it)})" } ?: " (sem meta)"), ObjetivoEntidade.serializer(), null, e)
        }
        return Resultado.Sucesso(e.id)
    }

    /** Edita nome, ícone, cor, meta e prazo. O guardado não muda aqui (só por movimentos). */
    suspend fun editar(o: Objetivo): Resultado<Unit> {
        val limpo = o.copy(nome = o.nome.trim())
        validar(limpo)?.let { return Resultado.Falha(it) }
        return banco.withTransaction {
            val antes = valido(o.id) ?: return@withTransaction Resultado.Falha("Objetivo não encontrado.")
            val depois = antes.copy(nome = limpo.nome, icone = limpo.icone, cor = limpo.cor, metaCentavos = limpo.meta, prazo = limpo.prazo?.toString(), atualizadoEm = relogio.agora())
            if (depois.copy(atualizadoEm = antes.atualizadoEm) == antes) return@withTransaction Resultado.Sucesso(Unit)
            dao.atualizar(depois)
            auditor.registrar(relogio.novoId(), Operacao.EDICAO, Auditor.OBJETIVO, antes.id, "Objetivo “${depois.nome}” editado", ObjetivoEntidade.serializer(), antes, depois)
            Resultado.Sucesso(Unit)
        }
    }

    // ---------------------------------------------------------------- operações (6F.2)

    /** Guardar: bloqueado se passar do disponível; só em objetivo ativo. @return o grupo do movimento. */
    suspend fun guardar(id: String, valor: Long, data: LocalDate): Resultado<String> {
        if (valor <= 0) return Resultado.Falha("O valor precisa ser maior que zero.")
        return banco.withTransaction {
            val o = valido(id) ?: return@withTransaction Resultado.Falha("Objetivo não encontrado.")
            if (o.concluidoEm != null || o.arquivado) return@withTransaction Resultado.Falha("Só dá para guardar em objetivo ativo. Reabra o objetivo antes.")
            val d = disponivel()
            if (valor > d) return@withTransaction semDisponivel(valor, d)
            val grupo = relogio.novoId()
            movimento(grupo, o, data, valor, TipoMovimentoReserva.GUARDAR, "${Dinheiro.formatar(valor)} guardados em “${o.nome}”")
            Resultado.Sucesso(grupo)
        }
    }

    /** Retirar: até o guardado do objetivo. Não é despesa. */
    suspend fun retirar(id: String, valor: Long, data: LocalDate): Resultado<String> {
        if (valor <= 0) return Resultado.Falha("O valor precisa ser maior que zero.")
        return banco.withTransaction {
            val o = valido(id) ?: return@withTransaction Resultado.Falha("Objetivo não encontrado.")
            val g = guardado(o.id)
            if (valor > g) return@withTransaction Resultado.Falha("Só há ${Dinheiro.formatar(g)} guardados em “${o.nome}”.")
            val grupo = relogio.novoId()
            movimento(grupo, o, data, -valor, TipoMovimentoReserva.RETIRAR, "${Dinheiro.formatar(valor)} retirados de “${o.nome}”")
            Resultado.Sucesso(grupo)
        }
    }

    /** Mover de um objetivo para outro (ativo): duas linhas com o mesmo grupo; o disponível não muda. */
    suspend fun mover(deId: String, paraId: String, valor: Long, data: LocalDate): Resultado<String> {
        if (valor <= 0) return Resultado.Falha("O valor precisa ser maior que zero.")
        if (deId == paraId) return Resultado.Falha("Escolha outro objetivo de destino.")
        return banco.withTransaction {
            val de = valido(deId) ?: return@withTransaction Resultado.Falha("Objetivo não encontrado.")
            val para = valido(paraId) ?: return@withTransaction Resultado.Falha("Objetivo de destino não encontrado.")
            if (para.concluidoEm != null || para.arquivado) return@withTransaction Resultado.Falha("O destino precisa ser um objetivo ativo.")
            val g = guardado(de.id)
            if (valor > g) return@withTransaction Resultado.Falha("Só há ${Dinheiro.formatar(g)} guardados em “${de.nome}”.")
            val grupo = relogio.novoId()
            movimento(grupo, de, data, -valor, TipoMovimentoReserva.MOVER, "${Dinheiro.formatar(valor)} movidos de “${de.nome}” para “${para.nome}”")
            movimento(grupo, para, data, valor, TipoMovimentoReserva.MOVER, "${Dinheiro.formatar(valor)} recebidos de “${de.nome}” em “${para.nome}”")
            Resultado.Sucesso(grupo)
        }
    }

    /**
     * Ajuste manual: grava a DIFERENÇA para [novoGuardado] como movimento "ajuste" (nunca
     * sobrescreve). Um aumento segue o bloqueio do Guardar. @return o grupo, ou null se nada mudou.
     */
    suspend fun ajustar(id: String, novoGuardado: Long, data: LocalDate): Resultado<String?> {
        if (novoGuardado < 0) return Resultado.Falha("O valor guardado não pode ser negativo.")
        return banco.withTransaction {
            val o = valido(id) ?: return@withTransaction Resultado.Falha("Objetivo não encontrado.")
            val diferenca = novoGuardado - guardado(o.id)
            if (diferenca == 0L) return@withTransaction Resultado.Sucesso(null)
            if (diferenca > 0) {
                if (o.concluidoEm != null || o.arquivado) return@withTransaction Resultado.Falha("Só dá para aumentar o guardado de um objetivo ativo.")
                val d = disponivel()
                if (diferenca > d) return@withTransaction semDisponivel(diferenca, d)
            }
            val grupo = relogio.novoId()
            movimento(grupo, o, data, diferenca, TipoMovimentoReserva.AJUSTE,
                "Ajuste de ${Dinheiro.formatarComSinal(diferenca)} em “${o.nome}” (guardado passa a ${Dinheiro.formatar(novoGuardado)})")
            Resultado.Sucesso(grupo)
        }
    }

    /** Desfaz um movimento (o grupo inteiro, num mover), sem deixar objetivo negativo. */
    suspend fun desfazer(grupoId: String): Resultado<Unit> = banco.withTransaction {
        val grupo = movDao.doGrupo(grupoId)
        if (grupo.isEmpty()) return@withTransaction Resultado.Falha("Movimento não encontrado.")
        val todos = movDao.validos()
        Objetivos.bloqueioDoDesfazer(grupo.map { it.paraDominio() }, { id -> todos.filter { it.objetivoId == id }.sumOf { it.valorCentavos } }, disponivel())
            ?.let { return@withTransaction Resultado.Falha(it) }
        val agora = relogio.agora()
        val auditoria = relogio.novoId()
        for (m in grupo) {
            val d = m.copy(excluidoEm = agora, atualizadoEm = agora)
            movDao.atualizar(d)
            auditor.registrar(auditoria, Operacao.EXCLUSAO, Auditor.MOVIMENTO_RESERVA, m.id,
                "Movimento de ${Dinheiro.formatarComSinal(m.valorCentavos)} desfeito", MovimentoReservaEntidade.serializer(), m, d)
        }
        Resultado.Sucesso(Unit)
    }

    // ---------------------------------------------------------------- concluir, arquivar, excluir (6F.5)

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

    /** Excluir com guardado é recusado (O3 = a). Sem guardado, exclusão lógica e auditada. */
    suspend fun excluir(id: String): Resultado<Unit> = banco.withTransaction {
        val antes = valido(id) ?: return@withTransaction Resultado.Falha("Objetivo não encontrado.")
        val g = guardado(id)
        if (g > 0) return@withTransaction Resultado.Falha("Retire ou mova os ${Dinheiro.formatar(g)} antes de excluir “${antes.nome}”.")
        val agora = relogio.agora()
        val d = antes.copy(excluidoEm = agora, atualizadoEm = agora)
        dao.atualizar(d)
        auditor.registrar(relogio.novoId(), Operacao.EXCLUSAO, Auditor.OBJETIVO, id, "Objetivo “${antes.nome}” excluído", ObjetivoEntidade.serializer(), antes, d)
        Resultado.Sucesso(Unit)
    }

}
