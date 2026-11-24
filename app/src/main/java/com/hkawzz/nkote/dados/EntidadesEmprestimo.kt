package com.hkawzz.nkote.dados

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// =====================================================================================
// Tabela nova do esquema versão 4 (v0.5): empréstimos (docs/decisoes/0020-banco-v4.md).
// As parcelas são obrigações (origem EMPRESTIMO, com emprestimoId); os pagamentos e
// recebimentos são `pagamento`; as diferenças são `obrigacao_ajuste`.
// =====================================================================================

/**
 * Um empréstimo recebido (eu devo) ou concedido (me devem). O tipo vem da natureza da
 * conta da pessoa ([contaId]). Nada calculável é gravado: restante, juros não
 * reconhecidos e atraso são sempre calculados.
 */
@Serializable
@Entity(
    tableName = "emprestimo",
    foreignKeys = [
        ForeignKey(entity = ContaEntidade::class, parentColumns = ["id"], childColumns = ["contaId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoriaEntidade::class, parentColumns = ["id"], childColumns = ["categoriaId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ContaEntidade::class, parentColumns = ["id"], childColumns = ["contaPropriaId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MovimentacaoEntidade::class, parentColumns = ["id"], childColumns = ["movimentacaoContratacaoId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MovimentacaoEntidade::class, parentColumns = ["id"], childColumns = ["movimentacaoJurosId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("contaId"), Index("categoriaId"), Index("contaPropriaId"), Index("movimentacaoContratacaoId"), Index("movimentacaoJurosId")],
)
data class EmprestimoEntidade(
    @PrimaryKey val id: String,
    /** A conta da pessoa ou instituição (natureza EMPRESTIMO_RECEBIDO ou EMPRESTIMO_CONCEDIDO). */
    val contaId: String,
    val descricao: String,
    /** Só descritiva: não entra em "Despesas por categoria". */
    val categoriaId: String?,
    val valorOriginalCentavos: Long,
    val dataContratacao: String,
    /** Conta própria por onde o dinheiro entrou ou saiu. Vazia no anterior ao app. */
    val contaPropriaId: String?,
    val anteriorAoApp: Boolean,
    /** Só no concedido anterior ao app (D5): juros contidos nas parcelas pendentes. */
    val jurosInformadosCentavos: Long?,
    /** A transferência da contratação, ou o ajuste de abertura no anterior ao app. */
    val movimentacaoContratacaoId: String?,
    /** A despesa dos juros J1 (só no recebido, se houver juros). */
    val movimentacaoJurosId: String?,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
)
