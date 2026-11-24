package com.hkawzz.nkote.dados

import androidx.room.withTransaction
import com.hkawzz.nkote.dominio.CategoriasDoSistema
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.OrigemObrigacao
import com.hkawzz.nkote.dominio.TipoEmprestimo
import com.hkawzz.nkote.dominio.TipoMovimentacao
import java.time.LocalDate
import java.time.YearMonth

/** O que a pessoa informa para registrar um empréstimo (regra 6E). */
data class NovoEmprestimo(
    val tipo: TipoEmprestimo,
    /** Uma pessoa já cadastrada (conta da natureza do tipo)... */
    val contaPessoaId: String?,
    /** ...ou o nome de uma pessoa nova (vira uma conta). */
    val nomeNovaPessoa: String?,
    val descricao: String,
    val categoriaId: String?,
    val valorOriginal: Long,
    val data: LocalDate,
    /** Conta de dinheiro próprio por onde o dinheiro entrou ou saiu (não no anterior ao app). */
    val contaPropriaId: String?,
    /** Vencimento e valor de cada parcela (automáticas ou personalizadas). No anterior: só as pendentes. */
    val parcelas: List<Pair<LocalDate, Long>>,
    val anteriorAoApp: Boolean = false,
    /** Só no concedido anterior (D5): juros contidos nas parcelas pendentes. */
    val jurosInformados: Long = 0,
    /** v0.6 (A4): no anterior, o total de parcelas do contrato (vazio = só as pendentes)... */
    val totalParcelasDoContrato: Int? = null,
    /** ...e quantas já foram pagas antes do app. As pendentes são numeradas a partir daí (ex.: 5/12). */
    val parcelasJaPagas: Int = 0,
)

/**
 * Registrar e excluir empréstimos (v0.5, ADR 0021). Tudo numa transação, com um grupo de
 * auditoria. Pagar e receber ficam no `PagamentoCreditoServico` (PR 8).
 *
 * - Recebido (J1): transferência pessoa → conta própria + despesa de juros (Encargos
 *   financeiros) na conta da pessoa, na data da contratação.
 * - Concedido (K2): transferência conta própria → pessoa. Os juros viram receita ao receber.
 * - Anterior ao app: só um ajuste de abertura na conta da pessoa (nada toca no Pix).
 */
class EmprestimoServico(
    private val banco: NkPayBanco,
    private val auditor: Auditor,
    private val relogio: Relogio,
) {
    private val contaDao = banco.contaDao()
    private val movDao = banco.movimentacaoDao()
    private val emprestimoDao = banco.emprestimoDao()
    private val obrigacaoDao = banco.obrigacaoDao()
    private val pagamentoDao = banco.pagamentoDao()
    private val ajusteDao = banco.ajusteObrigacaoDao()
    private val categoriaDao = banco.categoriaDao()

    fun validar(n: NovoEmprestimo): String? {
        val total = n.parcelas.sumOf { it.second }
        return when {
            n.descricao.isBlank() -> "Dê um nome para o empréstimo."
            n.descricao.length > 80 -> "O nome pode ter no máximo 80 caracteres."
            n.contaPessoaId == null && n.nomeNovaPessoa.isNullOrBlank() -> "Escolha a pessoa ou instituição."
            n.parcelas.isEmpty() -> "Informe pelo menos uma parcela."
            n.parcelas.size > 240 -> "No máximo 240 parcelas."
            n.parcelas.any { it.second <= 0 } -> "Toda parcela precisa ter valor maior que zero."
            !n.anteriorAoApp && n.valorOriginal <= 0 -> "Informe o valor original."
            !n.anteriorAoApp && n.contaPropriaId == null -> "Escolha a conta por onde o dinheiro ${if (n.tipo == TipoEmprestimo.RECEBIDO) "entrou" else "saiu"}."
            !n.anteriorAoApp && total < n.valorOriginal -> "O total das parcelas (${Dinheiro.formatar(total)}) não pode ser menor que o valor original."
            n.anteriorAoApp && n.tipo == TipoEmprestimo.CONCEDIDO && n.jurosInformados !in 0 until total -> "Os juros contidos precisam ser menores que o total das parcelas pendentes."
            !n.anteriorAoApp && (n.totalParcelasDoContrato != null || n.parcelasJaPagas != 0) -> "Parcelas já pagas só existem em empréstimos anteriores ao app."
            n.parcelasJaPagas < 0 -> "As parcelas já pagas não podem ser negativas."
            n.totalParcelasDoContrato != null && n.parcelasJaPagas + n.parcelas.size != n.totalParcelasDoContrato ->
                "As parcelas já pagas (${n.parcelasJaPagas}) e as pendentes (${n.parcelas.size}) precisam somar o total do contrato (${n.totalParcelasDoContrato})."
            else -> null
        }
    }

    /** @return o id do empréstimo. */
    suspend fun registrar(n: NovoEmprestimo): Resultado<String> {
        validar(n)?.let { return Resultado.Falha(it) }
        val total = n.parcelas.sumOf { it.second }
        val juros = if (n.anteriorAoApp) 0 else total - n.valorOriginal
        return banco.withTransaction {
            val propria = if (n.anteriorAoApp) null else contaDao.buscar(n.contaPropriaId!!)?.takeIf { it.excluidoEm == null && it.natureza == Natureza.PROPRIO.name }
                ?: return@withTransaction Resultado.Falha("A conta escolhida precisa ser de dinheiro próprio.")
            if (n.categoriaId != null && categoriaDao.buscar(n.categoriaId)?.takeIf { it.excluidoEm == null } == null) {
                return@withTransaction Resultado.Falha("Categoria não encontrada.")
            }
            val encargos = if (n.tipo == TipoEmprestimo.RECEBIDO && juros > 0) {
                categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) ?: return@withTransaction Resultado.Falha("Categoria “Encargos financeiros” não encontrada.")
            } else null

            val grupo = relogio.novoId()
            val agora = relogio.agora()
            val dataTexto = n.data.toString()

            // Pessoa: a conta existente (da natureza certa) ou uma nova.
            val pessoa = if (n.contaPessoaId != null) {
                contaDao.buscar(n.contaPessoaId)?.takeIf { it.excluidoEm == null && it.natureza == n.tipo.natureza.name }
                    ?: return@withTransaction Resultado.Falha("Pessoa não encontrada.")
            } else {
                val nova = ContaEntidade(
                    relogio.novoId(), n.nomeNovaPessoa!!.trim().take(60), n.tipo.natureza.name, null, 0, dataTexto, null, null,
                    null, null, null, null, null, agora, agora, null,
                )
                contaDao.inserir(nova)
                auditor.registrar(grupo, Operacao.CRIACAO, Auditor.CONTA, nova.id, "Conta “${nova.nome}” criada (${n.tipo.natureza.rotulo.lowercase()})", ContaEntidade.serializer(), null, nova)
                nova
            }
            val descricao = n.descricao.trim()

            suspend fun mov(tipo: TipoMovimentacao, valor: Long, conta: String, destino: String?, categoria: String?, texto: String, resumo: String): MovimentacaoEntidade {
                val m = MovimentacaoEntidade(relogio.novoId(), tipo.name, dataTexto, valor, conta, destino, categoria, texto, agora, agora, null)
                movDao.inserir(m)
                auditor.registrar(grupo, Operacao.CRIACAO, Auditor.MOVIMENTACAO, m.id, resumo, MovimentacaoEntidade.serializer(), null, m)
                return m
            }

            var contratacao: MovimentacaoEntidade? = null
            var movJuros: MovimentacaoEntidade? = null
            if (n.anteriorAoApp) {
                // 6E.10: ajuste de abertura, sem tocar em nenhuma conta própria.
                val abertura = if (n.tipo == TipoEmprestimo.RECEBIDO) -total else total - n.jurosInformados
                if (abertura != 0L) contratacao = mov(TipoMovimentacao.AJUSTE, abertura, pessoa.id, null, null, "Empréstimo anterior ao app: $descricao",
                    "Ajuste de abertura de ${Dinheiro.formatarComSinal(abertura)} em “${pessoa.nome}” (empréstimo anterior ao app)")
            } else if (n.tipo == TipoEmprestimo.RECEBIDO) {
                contratacao = mov(TipoMovimentacao.TRANSFERENCIA, n.valorOriginal, pessoa.id, propria!!.id, null, "Empréstimo: $descricao",
                    "Empréstimo de ${Dinheiro.formatar(n.valorOriginal)} recebido de “${pessoa.nome}” em ${propria.nome}")
                if (juros > 0) movJuros = mov(TipoMovimentacao.DESPESA, juros, pessoa.id, null, encargos!!.id, "Juros: $descricao",
                    "Juros de ${Dinheiro.formatar(juros)} do empréstimo “$descricao” (Encargos financeiros)")
            } else {
                contratacao = mov(TipoMovimentacao.TRANSFERENCIA, n.valorOriginal, propria!!.id, pessoa.id, null, "Empréstimo: $descricao",
                    "Empréstimo de ${Dinheiro.formatar(n.valorOriginal)} concedido a “${pessoa.nome}” saindo de ${propria.nome}")
            }

            val emp = EmprestimoEntidade(
                relogio.novoId(), pessoa.id, descricao, n.categoriaId, if (n.anteriorAoApp) n.valorOriginal.coerceAtLeast(0) else n.valorOriginal,
                dataTexto, propria?.id, n.anteriorAoApp, if (n.anteriorAoApp && n.tipo == TipoEmprestimo.CONCEDIDO) n.jurosInformados else null,
                contratacao?.id, movJuros?.id, agora, agora, null,
            )
            emprestimoDao.inserir(emp)
            auditor.registrar(grupo, Operacao.CRIACAO, Auditor.EMPRESTIMO, emp.id,
                "Empréstimo ${n.tipo.rotulo.lowercase()} “$descricao” registrado (${n.parcelas.size} parcelas, total ${Dinheiro.formatar(total)})", EmprestimoEntidade.serializer(), null, emp)

            val ordenadas = n.parcelas.sortedBy { it.first }
            // v0.6 (A4): no anterior, as pendentes continuam a numeração do contrato (ex.: 5/12).
            val noContrato = n.totalParcelasDoContrato ?: (n.parcelasJaPagas + ordenadas.size)
            ordenadas.forEachIndexed { i0, (venc, valor) ->
                val i = n.parcelasJaPagas + i0
                val o = ObrigacaoEntidade(
                    id = relogio.novoId(), origem = OrigemObrigacao.EMPRESTIMO.name,
                    descricao = if (noContrato == 1) descricao else "$descricao (${i + 1}/$noContrato)",
                    valorPrevistoCentavos = valor, vencimento = venc.toString(), mesReferencia = YearMonth.from(venc).toString(),
                    contaFixaId = null, categoriaId = null, contaCreditoId = pessoa.id, compraId = null,
                    numeroParcela = i + 1, totalParcelas = noContrato, canceladaEm = null,
                    criadoEm = agora, atualizadoEm = agora, excluidoEm = null, emprestimoId = emp.id,
                )
                obrigacaoDao.inserir(o)
                auditor.registrar(grupo, Operacao.CRIACAO, Auditor.OBRIGACAO, o.id, "Parcela ${i + 1}/$noContrato de ${Dinheiro.formatar(valor)} (vence $venc)", ObrigacaoEntidade.serializer(), null, o)
            }
            Resultado.Sucesso(emp.id)
        }
    }

    /** 6E.9: exclui o empréstimo e tudo o que ele criou. Só sem nenhum pagamento ou recebimento. */
    suspend fun excluir(id: String): Resultado<Unit> = banco.withTransaction {
        val emp = emprestimoDao.buscar(id)?.takeIf { it.excluidoEm == null } ?: return@withTransaction Resultado.Falha("Empréstimo não encontrado.")
        val parcelas = obrigacaoDao.doEmprestimo(id)
        if (parcelas.any { pagamentoDao.validosDa(it.id).isNotEmpty() }) {
            return@withTransaction Resultado.Falha("Este empréstimo já tem pagamento ou recebimento. Desfaça-os antes de excluir.")
        }
        val grupo = relogio.novoId()
        val agora = relogio.agora()
        suspend fun excluirMov(movId: String?) {
            val m = movId?.let { movDao.buscar(it) }?.takeIf { it.excluidoEm == null } ?: return
            val d = m.copy(excluidoEm = agora, atualizadoEm = agora)
            movDao.atualizar(d)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.MOVIMENTACAO, m.id, "${TipoMovimentacao.valueOf(m.tipo).rotulo} “${m.descricao}” excluída", MovimentacaoEntidade.serializer(), m, d)
        }
        for (p in parcelas) {
            for (a in ajusteDao.validosDa(p.id)) {
                val d = a.copy(excluidoEm = agora, atualizadoEm = agora)
                ajusteDao.atualizar(d)
                auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.AJUSTE_OBRIGACAO, a.id, "Ajuste de ${Dinheiro.formatar(a.valorCentavos)} excluído", ObrigacaoAjusteEntidade.serializer(), a, d)
                excluirMov(a.movimentacaoId)
            }
            val d = p.copy(excluidoEm = agora, atualizadoEm = agora)
            obrigacaoDao.atualizar(d)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.OBRIGACAO, p.id, "Parcela “${p.descricao}” excluída", ObrigacaoEntidade.serializer(), p, d)
        }
        excluirMov(emp.movimentacaoContratacaoId)
        excluirMov(emp.movimentacaoJurosId)
        val d = emp.copy(excluidoEm = agora, atualizadoEm = agora)
        emprestimoDao.atualizar(d)
        auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.EMPRESTIMO, emp.id, "Empréstimo “${emp.descricao}” excluído", EmprestimoEntidade.serializer(), emp, d)
        Resultado.Sucesso(Unit)
    }
}
