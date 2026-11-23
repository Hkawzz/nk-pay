package com.nkpay.app.dados

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

enum class Operacao(val rotulo: String) {
    CRIACAO("Criação"),
    EDICAO("Edição"),
    EXCLUSAO("Exclusão"),
    RESTAURACAO("Restauração"),
}

/**
 * Grava o Registro de alterações.
 *
 * Os repositórios chamam o Auditor DENTRO da mesma transação da alteração.
 * Assim, ou a alteração e o registro são gravados juntos, ou nenhum dos dois é:
 * não existe alteração sem registro.
 */
class Auditor(private val dao: AuditoriaDao, private val relogio: Relogio) {

    private val json = Json { encodeDefaults = true }

    suspend fun <T> registrar(
        grupoId: String,
        operacao: Operacao,
        entidade: String,
        entidadeId: String,
        resumo: String,
        serializador: KSerializer<T>,
        antes: T?,
        depois: T?,
    ) {
        dao.inserir(
            AuditoriaEntidade(
                id = relogio.novoId(),
                grupoId = grupoId,
                quando = relogio.agora(),
                operacao = operacao.name,
                entidade = entidade,
                entidadeId = entidadeId,
                resumo = resumo,
                antes = antes?.let { json.encodeToString(serializador, it) },
                depois = depois?.let { json.encodeToString(serializador, it) },
            ),
        )
    }

    companion object {
        const val CONTA = "conta"
        const val CATEGORIA = "categoria"
        const val MOVIMENTACAO = "movimentacao"
        const val CONTA_FIXA = "conta_fixa"
        const val OBRIGACAO = "obrigacao"
        const val PAGAMENTO = "pagamento"
        const val AJUSTE_OBRIGACAO = "obrigacao_ajuste"
        const val COMPRA = "compra"
        const val PAGAMENTO_FATURA = "pagamento_fatura"
    }
}
