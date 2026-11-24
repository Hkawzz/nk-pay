package com.hkawzz.nkote.dados

import androidx.room.withTransaction
import com.hkawzz.nkote.dominio.AparenciaSugerida
import com.hkawzz.nkote.dominio.Categoria
import com.hkawzz.nkote.dominio.CategoriasDoSistema
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.dominio.Validacao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Resultado de uma operação que pode ser recusada por uma regra. */
sealed interface Resultado<out T> {
    data class Sucesso<T>(val valor: T) : Resultado<T>
    data class Falha(val mensagem: String) : Resultado<Nothing>
}

// =====================================================================================
// Regra de ouro desta camada: TODA alteração roda em `banco.withTransaction { ... }`
// e chama o Auditor lá dentro. As telas nunca falam com os DAOs diretamente.
// =====================================================================================

class ContaRepositorio(
    private val banco: NkPayBanco,
    private val auditor: Auditor,
    private val relogio: Relogio,
) {
    private val dao = banco.contaDao()

    /** Contas não excluídas: ativas primeiro, depois arquivadas, por nome. */
    fun observar(): Flow<List<Conta>> = dao.observarTodas().map { lista -> lista.map { it.paraDominio() } }

    suspend fun buscar(id: String): Conta? = dao.buscar(id)?.takeIf { it.excluidoEm == null }?.paraDominio()

    /** Cria a conta (o `id` recebido é ignorado; um UUID novo é gerado). */
    suspend fun criar(conta: Conta): Resultado<String> {
        val normalizada = conta.copy(nome = conta.nome.trim())
        Validacao.conta(normalizada)?.let { return Resultado.Falha(it) }
        val agora = relogio.agora()
        val nova = ContaEntidade(
            id = relogio.novoId(),
            nome = normalizada.nome,
            natureza = normalizada.natureza.name,
            tipoBeneficio = normalizada.tipoBeneficio?.name,
            saldoInicialCentavos = normalizada.saldoInicial,
            dataSaldoInicial = normalizada.dataSaldoInicial.toString(),
            icone = normalizada.icone,
            cor = normalizada.cor,
            instituicao = null, limiteCentavos = normalizada.limite,
            diaFechamento = normalizada.diaFechamento, diaVencimento = normalizada.diaVencimento,
            arquivadaEm = null,
            criadoEm = agora, atualizadoEm = agora, excluidoEm = null,
        )
        banco.withTransaction {
            dao.inserir(nova)
            auditor.registrar(
                relogio.novoId(), Operacao.CRIACAO, Auditor.CONTA, nova.id,
                "Conta “${nova.nome}” criada (saldo inicial ${Dinheiro.formatar(nova.saldoInicialCentavos)})",
                ContaEntidade.serializer(), null, nova,
            )
        }
        return Resultado.Sucesso(nova.id)
    }

    /** Edita nome, natureza, benefício, saldo inicial, data, ícone e cor. */
    suspend fun editar(conta: Conta): Resultado<Unit> {
        val normalizada = conta.copy(nome = conta.nome.trim())
        Validacao.conta(normalizada)?.let { return Resultado.Falha(it) }
        return banco.transacaoConferida {
            val antes = dao.buscar(conta.id)?.takeIf { it.excluidoEm == null }
                ?: return@transacaoConferida Resultado.Falha("Conta não encontrada.")
            // Uma conta com lançamentos não vira de crédito (nem deixa de ser): o devido deixaria de bater.
            if (Natureza.valueOf(antes.natureza).ehContaDeTerceiros != normalizada.natureza.ehContaDeTerceiros && dao.contarMovimentacoes(antes.id) > 0) {
                return@transacaoConferida Resultado.Falha("Esta conta já tem lançamentos: ela não pode virar cartão ou crediário (nem o contrário).")
            }
            val depois = antes.copy(
                nome = normalizada.nome,
                natureza = normalizada.natureza.name,
                tipoBeneficio = normalizada.tipoBeneficio?.name,
                saldoInicialCentavos = normalizada.saldoInicial,
                dataSaldoInicial = normalizada.dataSaldoInicial.toString(),
                icone = normalizada.icone,
                cor = normalizada.cor,
                limiteCentavos = normalizada.limite,
                diaFechamento = normalizada.diaFechamento,
                diaVencimento = normalizada.diaVencimento,
                atualizadoEm = relogio.agora(),
            )
            if (depois.copy(atualizadoEm = antes.atualizadoEm) == antes) return@transacaoConferida Resultado.Sucesso(Unit)
            dao.atualizar(depois)
            auditor.registrar(
                relogio.novoId(), Operacao.EDICAO, Auditor.CONTA, antes.id,
                "Conta “${depois.nome}” editada", ContaEntidade.serializer(), antes, depois,
            )
            Resultado.Sucesso(Unit)
        }
    }

    suspend fun arquivar(id: String, arquivar: Boolean = true): Resultado<Unit> = banco.withTransaction {
        val antes = dao.buscar(id)?.takeIf { it.excluidoEm == null }
            ?: return@withTransaction Resultado.Falha("Conta não encontrada.")
        if ((antes.arquivadaEm != null) == arquivar) return@withTransaction Resultado.Sucesso(Unit)
        // v0.6 (A3, A5, ADR 0027): cartão, crediário ou pessoa com algo em aberto não some das telas.
        if (arquivar && Natureza.valueOf(antes.natureza).ehContaDeTerceiros) {
            emAberto(antes)?.let { return@withTransaction Resultado.Falha(it) }
        }
        val agora = relogio.agora()
        val depois = antes.copy(arquivadaEm = if (arquivar) agora else null, atualizadoEm = agora)
        dao.atualizar(depois)
        auditor.registrar(
            relogio.novoId(), Operacao.EDICAO, Auditor.CONTA, id,
            if (arquivar) "Conta “${antes.nome}” arquivada" else "Conta “${antes.nome}” desarquivada",
            ContaEntidade.serializer(), antes, depois,
        )
        Resultado.Sucesso(Unit)
    }

    sealed interface ResultadoExclusao {
        data object Excluida : ResultadoExclusao
        /** A exclusão foi bloqueada: a conta tem movimentações. A tela oferece arquivar. */
        data class TemMovimentacoes(val quantidade: Int) : ResultadoExclusao
        /** v0.6 (A3, A5): cartão, crediário ou pessoa com registros ligados. A tela oferece arquivar. */
        data class TemVinculos(val motivo: String) : ResultadoExclusao
        data object NaoEncontrada : ResultadoExclusao
    }

    /** v0.6: o que ainda está em aberto numa conta de terceiros (null = nada). */
    private suspend fun emAberto(c: ContaEntidade): String? {
        val b = banco.backupDao()
        val conta = c.paraDominio()
        val movs = b.movimentacoes().filter { it.excluidoEm == null && (it.contaId == c.id || it.contaDestinoId == c.id) }.map { it.paraDominio() }
        val saldo = com.hkawzz.nkote.dominio.Saldos.saldosPorConta(listOf(conta), movs)[c.id] ?: 0
        val parcelas = b.obrigacoes().filter { it.excluidoEm == null && it.contaCreditoId == c.id }.map { it.paraDominio() }
        val ids = parcelas.map { it.id }.toSet()
        val vc = com.hkawzz.nkote.dominio.VisaoCredito(
            listOf(conta), movs, parcelas,
            banco.pagamentoDao().validosTodos().filter { it.obrigacaoId in ids }.map { it.paraDominio() },
            banco.ajusteObrigacaoDao().validosTodos().filter { it.obrigacaoId in ids }.map { it.paraDominio() },
            banco.pagamentoFaturaDao().validosDaConta(c.id).map { it.paraDominio() },
            b.compras().filter { it.excluidoEm == null && it.contaCreditoId == c.id }.map { it.paraDominio() },
        )
        val restante = vc.restanteEmAberto(c.id, relogio.hoje())
        return when (conta.natureza) {
            Natureza.CARTAO, Natureza.CREDIARIO -> if (saldo != 0L || restante > 0) "Ainda há ${Dinheiro.formatar(maxOf(-saldo, restante))} a pagar neste cartão ou crediário. Quite antes de arquivar." else null
            Natureza.EMPRESTIMO_RECEBIDO -> if (saldo != 0L || restante > 0) "Você ainda deve ${Dinheiro.formatar(maxOf(-saldo, restante))} para “${c.nome}”. Quite antes de arquivar." else null
            Natureza.EMPRESTIMO_CONCEDIDO -> if (saldo != 0L || restante > 0) "“${c.nome}” ainda te deve ${Dinheiro.formatar(maxOf(saldo, restante))}. Receba antes de arquivar." else null
            else -> null
        }
    }

    /** v0.6: os registros ligados a uma conta de terceiros, para explicar por que não dá para excluir. */
    private suspend fun vinculos(id: String): String? {
        val b = banco.backupDao()
        val partes = listOfNotNull(
            b.compras().count { it.excluidoEm == null && it.contaCreditoId == id }.takeIf { it > 0 }?.let { "$it compra(s)" },
            b.emprestimos().count { it.excluidoEm == null && it.contaId == id }.takeIf { it > 0 }?.let { "$it empréstimo(s)" },
            b.obrigacoes().count { it.excluidoEm == null && it.contaCreditoId == id }.takeIf { it > 0 }?.let { "$it parcela(s)" },
            banco.pagamentoFaturaDao().validosDaConta(id).size.takeIf { it > 0 }?.let { "$it abatimento(s) de fatura" },
            dao.contarMovimentacoes(id).takeIf { it > 0 }?.let { "$it movimentação(ões)" },
        )
        return if (partes.isEmpty()) null else "Tem " + partes.joinToString(", ") + ". Para tirar das listas sem apagar o histórico, arquive."
    }

    /** Exclusão lógica. Bloqueada se a conta tiver movimentações (não excluídas). */
    suspend fun excluir(id: String): ResultadoExclusao = banco.withTransaction {
        val antes = dao.buscar(id)?.takeIf { it.excluidoEm == null }
            ?: return@withTransaction ResultadoExclusao.NaoEncontrada
        if (Natureza.valueOf(antes.natureza).ehContaDeTerceiros) {
            vinculos(id)?.let { return@withTransaction ResultadoExclusao.TemVinculos(it) }
        }
        val quantidade = dao.contarMovimentacoes(id)
        if (quantidade > 0) return@withTransaction ResultadoExclusao.TemMovimentacoes(quantidade)
        val agora = relogio.agora()
        val depois = antes.copy(excluidoEm = agora, atualizadoEm = agora)
        dao.atualizar(depois)
        auditor.registrar(
            relogio.novoId(), Operacao.EXCLUSAO, Auditor.CONTA, id,
            "Conta “${antes.nome}” excluída", ContaEntidade.serializer(), antes, depois,
        )
        ResultadoExclusao.Excluida
    }
}

class CategoriaRepositorio(
    private val banco: NkPayBanco,
    private val auditor: Auditor,
    private val relogio: Relogio,
) {
    private val dao = banco.categoriaDao()

    fun observar(): Flow<List<Categoria>> = dao.observarTodas().map { lista -> lista.map { it.paraDominio() } }

    /**
     * Na primeira abertura, cria as categorias sugeridas e a categoria de sistema
     * "Encargos financeiros". Pode ser chamado sempre: só age se faltar algo.
     */
    suspend fun garantirCategoriasIniciais() {
        banco.withTransaction {
            val grupo = relogio.novoId()
            if (dao.contarTodas() == 0) {
                for (nome in SUGESTOES) inserirComAuditoria(grupo, nome, null, "Categoria sugerida “$nome” criada")
            }
            if (dao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) == null) {
                inserirComAuditoria(
                    grupo, "Encargos financeiros", CategoriasDoSistema.ENCARGOS_FINANCEIROS,
                    "Categoria de sistema “Encargos financeiros” criada",
                )
            }
        }
    }

    private suspend fun inserirComAuditoria(
        grupo: String,
        nome: String,
        codigo: String?,
        resumo: String,
        // v0.4: toda categoria nova já nasce com aparência (sugerida pelo nome, ou neutra).
        aparencia: com.hkawzz.nkote.dominio.Aparencia = AparenciaSugerida.ouNeutra(nome),
    ): String {
        val agora = relogio.agora()
        val nova = CategoriaEntidade(
            id = relogio.novoId(), nome = nome, codigoSistema = codigo,
            arquivadaEm = null, criadoEm = agora, atualizadoEm = agora, excluidoEm = null,
            icone = aparencia.icone, cor = aparencia.cor,
        )
        dao.inserir(nova)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.CATEGORIA, nova.id, resumo, CategoriaEntidade.serializer(), null, nova)
        return nova.id
    }

    /** Cria a categoria. Sem [aparencia], usa a sugerida pelo nome (ou a neutra). */
    suspend fun criar(nome: String, aparencia: com.hkawzz.nkote.dominio.Aparencia? = null): Resultado<String> {
        val limpo = nome.trim()
        Validacao.categoria(Categoria(id = "", nome = limpo))?.let { return Resultado.Falha(it) }
        val id = banco.withTransaction {
            inserirComAuditoria(relogio.novoId(), limpo, null, "Categoria “$limpo” criada", aparencia ?: AparenciaSugerida.ouNeutra(limpo))
        }
        return Resultado.Sucesso(id)
    }

    suspend fun renomear(id: String, nome: String): Resultado<Unit> {
        val limpo = nome.trim()
        Validacao.categoria(Categoria(id = id, nome = limpo))?.let { return Resultado.Falha(it) }
        return banco.withTransaction {
            val antes = dao.buscar(id)?.takeIf { it.excluidoEm == null }
                ?: return@withTransaction Resultado.Falha("Categoria não encontrada.")
            if (antes.codigoSistema != null) return@withTransaction Resultado.Falha("Categorias do sistema não podem ser alteradas.")
            if (antes.nome == limpo) return@withTransaction Resultado.Sucesso(Unit)
            val depois = antes.copy(nome = limpo, atualizadoEm = relogio.agora())
            dao.atualizar(depois)
            auditor.registrar(
                relogio.novoId(), Operacao.EDICAO, Auditor.CATEGORIA, id,
                "Categoria “${antes.nome}” renomeada para “$limpo”", CategoriaEntidade.serializer(), antes, depois,
            )
            Resultado.Sucesso(Unit)
        }
    }

    /** Arquivar: some das novas escolhas, mas continua nos registros antigos. */
    suspend fun arquivar(id: String, arquivar: Boolean = true): Resultado<Unit> = banco.withTransaction {
        val antes = dao.buscar(id)?.takeIf { it.excluidoEm == null }
            ?: return@withTransaction Resultado.Falha("Categoria não encontrada.")
        if (antes.codigoSistema != null) return@withTransaction Resultado.Falha("Categorias do sistema não podem ser arquivadas.")
        if ((antes.arquivadaEm != null) == arquivar) return@withTransaction Resultado.Sucesso(Unit)
        val agora = relogio.agora()
        val depois = antes.copy(arquivadaEm = if (arquivar) agora else null, atualizadoEm = agora)
        dao.atualizar(depois)
        auditor.registrar(
            relogio.novoId(), Operacao.EDICAO, Auditor.CATEGORIA, id,
            if (arquivar) "Categoria “${antes.nome}” arquivada" else "Categoria “${antes.nome}” desarquivada",
            CategoriaEntidade.serializer(), antes, depois,
        )
        Resultado.Sucesso(Unit)
    }

    /**
     * v0.5 (regra 6E.5): garante a categoria de sistema "Juros recebidos", com auditoria.
     * Roda na abertura do app e depois de restaurar um backup. Só age se faltar.
     * @return o id da categoria.
     */
    suspend fun garantirJurosRecebidos(): String = banco.withTransaction {
        dao.buscarPorCodigo(CategoriasDoSistema.JUROS_RECEBIDOS)?.id
            ?: inserirComAuditoria(relogio.novoId(), "Juros recebidos", CategoriasDoSistema.JUROS_RECEBIDOS, "Categoria de sistema “Juros recebidos” criada")
    }

    /**
     * Passo auditado da v0.4 (ADR 0018): dá a aparência sugerida às categorias SEM ícone
     * (as que vieram da v0.3, por migração ou por backup). As de nome conhecido recebem a
     * sugestão; as demais, a neutra. Nunca mexe numa categoria que já tem ícone, e nunca
     * em tabelas financeiras. Pode rodar sempre: sem categorias pendentes, não faz nada.
     * @return quantas categorias receberam aparência.
     */
    suspend fun aplicarAparenciaSugerida(): Int = banco.withTransaction {
        val pendentes = dao.semAparencia()
        if (pendentes.isEmpty()) return@withTransaction 0
        val grupo = relogio.novoId()
        val agora = relogio.agora()
        for (antes in pendentes) {
            val a = AparenciaSugerida.ouNeutra(antes.nome)
            val depois = antes.copy(icone = a.icone, cor = a.cor, atualizadoEm = agora)
            dao.atualizar(depois)
            auditor.registrar(
                grupo, Operacao.EDICAO, Auditor.CATEGORIA, antes.id,
                "Aparência sugerida aplicada à categoria “${antes.nome}”", CategoriaEntidade.serializer(), antes, depois,
            )
        }
        pendentes.size
    }

    /** Muda o ícone e a cor (também da categoria de sistema). Não toca em nada financeiro. */
    suspend fun mudarAparencia(id: String, icone: String, cor: Long): Resultado<Unit> = banco.withTransaction {
        if (icone.isBlank()) return@withTransaction Resultado.Falha("Escolha um ícone.")
        val antes = dao.buscar(id)?.takeIf { it.excluidoEm == null }
            ?: return@withTransaction Resultado.Falha("Categoria não encontrada.")
        if (antes.icone == icone && antes.cor == cor) return@withTransaction Resultado.Sucesso(Unit)
        val depois = antes.copy(icone = icone, cor = cor, atualizadoEm = relogio.agora())
        dao.atualizar(depois)
        auditor.registrar(
            relogio.novoId(), Operacao.EDICAO, Auditor.CATEGORIA, id,
            "Aparência da categoria “${antes.nome}” alterada", CategoriaEntidade.serializer(), antes, depois,
        )
        Resultado.Sucesso(Unit)
    }

    companion object {
        val SUGESTOES = listOf(
            "Alimentação", "Moradia", "Transporte", "Saúde", "Lazer",
            "Tecnologia", "Assinaturas", "Educação", "Outros",
        )
    }
}

class MovimentacaoRepositorio(
    private val banco: NkPayBanco,
    private val auditor: Auditor,
    private val relogio: Relogio,
) {
    private val dao = banco.movimentacaoDao()
    private val contaDao = banco.contaDao()
    private val categoriaDao = banco.categoriaDao()
    private val pagamentoDao = banco.pagamentoDao()

    private val compraDao = banco.compraDao()
    private val pagamentoFaturaDao = banco.pagamentoFaturaDao()
    private val ajusteDao = banco.ajusteObrigacaoDao()

    /**
     * Movimentações criadas pelo botão Pagar (v0.3), por uma compra no crédito ou por um
     * pagamento de crédito (v0.4) só mudam pela própria operação que as criou.
     */
    private suspend fun ehDePagamento(id: String) =
        pagamentoDao.daMovimentacao(id).isNotEmpty() || pagamentoFaturaDao.daMovimentacao(id).isNotEmpty() ||
            ajusteDao.daMovimentacao(id).isNotEmpty()

    // v0.5: a contratação e os juros J1 de um empréstimo também só mudam pelo próprio empréstimo.
    private suspend fun ehDeCompra(id: String) = compraDao.contarDaMovimentacao(id) > 0 || banco.emprestimoDao().contarDaMovimentacao(id) > 0

    private suspend fun mensagemSeProtegida(id: String): String? = when {
        ehDeCompra(id) -> MENSAGEM_COMPRA
        ehDePagamento(id) -> MENSAGEM_PAGAMENTO
        else -> null
    }

    /** True se a movimentação só muda pela operação que a criou (pagamento ou compra). */
    suspend fun ehPagamentoDeConta(id: String): Boolean = mensagemSeProtegida(id) != null

    /** Movimentações não excluídas, da mais recente para a mais antiga. */
    fun observar(): Flow<List<Movimentacao>> = dao.observarTodas().map { lista -> lista.map { it.paraDominio() } }

    suspend fun buscar(id: String): Movimentacao? = dao.buscar(id)?.takeIf { it.excluidoEm == null }?.paraDominio()

    /** Regras que precisam consultar o banco (dentro da transação). */
    private suspend fun validar(m: Movimentacao): String? {
        val contasValidas = mutableMapOf<String, Boolean>()
        var temCredito = false
        for (id in listOfNotNull(m.contaId, m.contaDestinoId)) {
            val conta = contaDao.buscar(id)
            contasValidas[id] = conta != null && conta.excluidoEm == null
            if (conta != null && !Validacao.aceitaLancamentoAvulso(Natureza.valueOf(conta.natureza))) temCredito = true
        }
        Validacao.movimentacao(m) { contasValidas[it] == true }?.let { return it }
        // Regra 6B.7 (ADR 0017): aqui, na camada de dados, e não só na tela.
        if (temCredito) return Validacao.MENSAGEM_CONTA_DE_CREDITO
        if (m.categoriaId != null) {
            if (!m.tipo.temCategoria) return "Este tipo de movimentação não tem categoria."
            val categoria = categoriaDao.buscar(m.categoriaId)
            if (categoria == null || categoria.excluidoEm != null) return "Categoria não encontrada."
        }
        return null
    }

    private fun resumo(m: MovimentacaoEntidade, verbo: String): String {
        val tipo = TipoMovimentacao.valueOf(m.tipo).rotulo
        val valor = if (m.tipo == TipoMovimentacao.AJUSTE.name) Dinheiro.formatarComSinal(m.valorCentavos) else Dinheiro.formatar(m.valorCentavos)
        val descricao = m.descricao.takeIf { it.isNotBlank() }?.let { " “$it”" } ?: ""
        return "$tipo de $valor$descricao $verbo"
    }

    suspend fun criar(m: Movimentacao): Resultado<String> {
        val limpa = m.copy(descricao = m.descricao.trim())
        return banco.transacaoConferida {
            validar(limpa)?.let { return@transacaoConferida Resultado.Falha(it) }
            val agora = relogio.agora()
            val nova = MovimentacaoEntidade(
                id = relogio.novoId(),
                tipo = limpa.tipo.name,
                data = limpa.data.toString(),
                valorCentavos = limpa.valor,
                contaId = limpa.contaId,
                contaDestinoId = limpa.contaDestinoId,
                categoriaId = limpa.categoriaId,
                descricao = limpa.descricao,
                criadoEm = agora, atualizadoEm = agora, excluidoEm = null,
            )
            dao.inserir(nova)
            auditor.registrar(
                relogio.novoId(), Operacao.CRIACAO, Auditor.MOVIMENTACAO, nova.id,
                resumo(nova, "lançada"), MovimentacaoEntidade.serializer(), null, nova,
            )
            Resultado.Sucesso(nova.id)
        }
    }

    suspend fun editar(m: Movimentacao): Resultado<Unit> {
        val limpa = m.copy(descricao = m.descricao.trim())
        return banco.transacaoConferida {
            val antes = dao.buscar(m.id)?.takeIf { it.excluidoEm == null }
                ?: return@transacaoConferida Resultado.Falha("Movimentação não encontrada.")
            mensagemSeProtegida(m.id)?.let { return@transacaoConferida Resultado.Falha(it) }
            validar(limpa)?.let { return@transacaoConferida Resultado.Falha(it) }
            // Uma movimentação de conta de crédito (feita por compra/pagamento) também não muda por aqui.
            if (contaDao.buscar(antes.contaId)?.let { Natureza.valueOf(it.natureza).ehContaDeTerceiros } == true ||
                antes.contaDestinoId?.let { contaDao.buscar(it) }?.let { Natureza.valueOf(it.natureza).ehContaDeTerceiros } == true
            ) return@transacaoConferida Resultado.Falha(Validacao.MENSAGEM_CONTA_DE_CREDITO)
            val depois = antes.copy(
                tipo = limpa.tipo.name,
                data = limpa.data.toString(),
                valorCentavos = limpa.valor,
                contaId = limpa.contaId,
                contaDestinoId = limpa.contaDestinoId,
                categoriaId = limpa.categoriaId,
                descricao = limpa.descricao,
                atualizadoEm = relogio.agora(),
            )
            if (depois.copy(atualizadoEm = antes.atualizadoEm) == antes) return@transacaoConferida Resultado.Sucesso(Unit)
            dao.atualizar(depois)
            auditor.registrar(
                relogio.novoId(), Operacao.EDICAO, Auditor.MOVIMENTACAO, antes.id,
                resumo(depois, "editada"), MovimentacaoEntidade.serializer(), antes, depois,
            )
            Resultado.Sucesso(Unit)
        }
    }

    /** Exclusão lógica: some das telas e dos cálculos, mas fica no banco e na auditoria. */
    suspend fun excluir(id: String): Resultado<Unit> = banco.transacaoConferida {
        val antes = dao.buscar(id)?.takeIf { it.excluidoEm == null }
            ?: return@transacaoConferida Resultado.Falha("Movimentação não encontrada.")
        mensagemSeProtegida(id)?.let { return@transacaoConferida Resultado.Falha(it) }
        val agora = relogio.agora()
        val depois = antes.copy(excluidoEm = agora, atualizadoEm = agora)
        dao.atualizar(depois)
        auditor.registrar(
            relogio.novoId(), Operacao.EXCLUSAO, Auditor.MOVIMENTACAO, id,
            resumo(antes, "excluída"), MovimentacaoEntidade.serializer(), antes, depois,
        )
        Resultado.Sucesso(Unit)
    }
}

/** Mensagem para quem tenta mexer numa movimentação criada pelo botão Pagar. */
const val MENSAGEM_PAGAMENTO =
    "Este lançamento é o pagamento de uma conta. Para mudar, abra a conta em A pagar e exclua o pagamento."

/** Mensagem para quem tenta mexer na despesa de uma compra no cartão ou crediário (v0.4). */
const val MENSAGEM_COMPRA =
    "Este lançamento é uma compra no cartão ou crediário, ou faz parte de um empréstimo. Para mudar, abra a compra (tela do cartão) ou o empréstimo (Mais → Empréstimos)."

class AuditoriaRepositorio(banco: NkPayBanco) {
    private val dao = banco.auditoriaDao()

    fun observar(): Flow<List<AuditoriaEntidade>> = dao.observarTodas()

    suspend fun buscar(id: String): AuditoriaEntidade? = dao.buscar(id)
}
