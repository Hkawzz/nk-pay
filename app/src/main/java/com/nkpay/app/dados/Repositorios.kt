package com.nkpay.app.dados

import androidx.room.withTransaction
import com.nkpay.app.dominio.Categoria
import com.nkpay.app.dominio.CategoriasDoSistema
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Movimentacao
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.dominio.Validacao
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
            instituicao = null, limiteCentavos = null, diaFechamento = null, diaVencimento = null,
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
        return banco.withTransaction {
            val antes = dao.buscar(conta.id)?.takeIf { it.excluidoEm == null }
                ?: return@withTransaction Resultado.Falha("Conta não encontrada.")
            val depois = antes.copy(
                nome = normalizada.nome,
                natureza = normalizada.natureza.name,
                tipoBeneficio = normalizada.tipoBeneficio?.name,
                saldoInicialCentavos = normalizada.saldoInicial,
                dataSaldoInicial = normalizada.dataSaldoInicial.toString(),
                icone = normalizada.icone,
                cor = normalizada.cor,
                atualizadoEm = relogio.agora(),
            )
            if (depois.copy(atualizadoEm = antes.atualizadoEm) == antes) return@withTransaction Resultado.Sucesso(Unit)
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
        data object NaoEncontrada : ResultadoExclusao
    }

    /** Exclusão lógica. Bloqueada se a conta tiver movimentações (não excluídas). */
    suspend fun excluir(id: String): ResultadoExclusao = banco.withTransaction {
        val antes = dao.buscar(id)?.takeIf { it.excluidoEm == null }
            ?: return@withTransaction ResultadoExclusao.NaoEncontrada
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

    private suspend fun inserirComAuditoria(grupo: String, nome: String, codigo: String?, resumo: String): String {
        val agora = relogio.agora()
        val nova = CategoriaEntidade(
            id = relogio.novoId(), nome = nome, codigoSistema = codigo,
            arquivadaEm = null, criadoEm = agora, atualizadoEm = agora, excluidoEm = null,
        )
        dao.inserir(nova)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.CATEGORIA, nova.id, resumo, CategoriaEntidade.serializer(), null, nova)
        return nova.id
    }

    suspend fun criar(nome: String): Resultado<String> {
        val limpo = nome.trim()
        Validacao.categoria(Categoria(id = "", nome = limpo))?.let { return Resultado.Falha(it) }
        val id = banco.withTransaction {
            inserirComAuditoria(relogio.novoId(), limpo, null, "Categoria “$limpo” criada")
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

    /** Movimentações não excluídas, da mais recente para a mais antiga. */
    fun observar(): Flow<List<Movimentacao>> = dao.observarTodas().map { lista -> lista.map { it.paraDominio() } }

    suspend fun buscar(id: String): Movimentacao? = dao.buscar(id)?.takeIf { it.excluidoEm == null }?.paraDominio()

    /** Regras que precisam consultar o banco (dentro da transação). */
    private suspend fun validar(m: Movimentacao): String? {
        val contasValidas = mutableMapOf<String, Boolean>()
        for (id in listOfNotNull(m.contaId, m.contaDestinoId)) {
            val conta = contaDao.buscar(id)
            contasValidas[id] = conta != null && conta.excluidoEm == null
        }
        Validacao.movimentacao(m) { contasValidas[it] == true }?.let { return it }
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
        return banco.withTransaction {
            validar(limpa)?.let { return@withTransaction Resultado.Falha(it) }
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
        return banco.withTransaction {
            val antes = dao.buscar(m.id)?.takeIf { it.excluidoEm == null }
                ?: return@withTransaction Resultado.Falha("Movimentação não encontrada.")
            validar(limpa)?.let { return@withTransaction Resultado.Falha(it) }
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
            if (depois.copy(atualizadoEm = antes.atualizadoEm) == antes) return@withTransaction Resultado.Sucesso(Unit)
            dao.atualizar(depois)
            auditor.registrar(
                relogio.novoId(), Operacao.EDICAO, Auditor.MOVIMENTACAO, antes.id,
                resumo(depois, "editada"), MovimentacaoEntidade.serializer(), antes, depois,
            )
            Resultado.Sucesso(Unit)
        }
    }

    /** Exclusão lógica: some das telas e dos cálculos, mas fica no banco e na auditoria. */
    suspend fun excluir(id: String): Resultado<Unit> = banco.withTransaction {
        val antes = dao.buscar(id)?.takeIf { it.excluidoEm == null }
            ?: return@withTransaction Resultado.Falha("Movimentação não encontrada.")
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

class AuditoriaRepositorio(banco: NkPayBanco) {
    private val dao = banco.auditoriaDao()

    fun observar(): Flow<List<AuditoriaEntidade>> = dao.observarTodas()

    suspend fun buscar(id: String): AuditoriaEntidade? = dao.buscar(id)
}
