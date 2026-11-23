package com.nkpay.app.dados

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// =====================================================================================
// Tabelas novas do esquema versão 2 (v0.3): contas fixas, obrigações, pagamentos e
// ajustes. Aprovadas pelo dono em 2026-11-22 (docs/decisoes/0012-banco-v2-obrigacoes.md).
//
// As tabelas da versão 1 (conta, categoria, movimentacao, auditoria) NÃO mudam:
// as novas só apontam para elas. Nenhum dado é copiado nem duplicado.
//
// Mesmas convenções da versão 1: UUID em texto, centavos, datas "AAAA-MM-DD",
// instantes em milissegundos e exclusão lógica (excluidoEm).
// =====================================================================================

/** Modelo de uma conta que se repete todo mês (luz, internet, assinatura). Não é paga: gera obrigações. */
@Serializable
@Entity(
    tableName = "conta_fixa",
    foreignKeys = [
        ForeignKey(entity = CategoriaEntidade::class, parentColumns = ["id"], childColumns = ["categoriaId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ContaEntidade::class, parentColumns = ["id"], childColumns = ["contaPadraoId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("categoriaId"), Index("contaPadraoId")],
)
data class ContaFixaEntidade(
    @PrimaryKey val id: String,
    val nome: String,
    val valorPadraoCentavos: Long,
    /** Dia do mês (1–31). Pode ficar vazio ("sem vencimento"). */
    val diaVencimento: Int?,
    val categoriaId: String?,
    /** Conta sugerida para pagar (opcional). */
    val contaPadraoId: String?,
    val arquivadaEm: Long?,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
)

/**
 * Algo a pagar: uma conta fixa de um mês, uma conta avulsa ou (v0.4) uma parcela.
 * O status (pendente, parcial, quitada, atrasada) e o restante são CALCULADOS,
 * nunca gravados: restante = previsto + acréscimos − abatimentos − pagamentos aplicados.
 */
@Serializable
@Entity(
    tableName = "obrigacao",
    foreignKeys = [
        ForeignKey(entity = ContaFixaEntidade::class, parentColumns = ["id"], childColumns = ["contaFixaId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoriaEntidade::class, parentColumns = ["id"], childColumns = ["categoriaId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ContaEntidade::class, parentColumns = ["id"], childColumns = ["contaCreditoId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("contaFixaId", "mesReferencia"), Index("categoriaId"), Index("contaCreditoId"), Index("vencimento"), Index("mesReferencia"), Index("emprestimoId")],
)
data class ObrigacaoEntidade(
    @PrimaryKey val id: String,
    /** FIXA | AVULSA | PARCELA | EMPRESTIMO */
    val origem: String,
    val descricao: String,
    /** Valor original. Nunca é alterado por pagamentos ou ajustes. */
    val valorPrevistoCentavos: Long,
    /** Pode ficar vazio ("sem vencimento"). */
    val vencimento: String?,
    /** Mês a que a obrigação pertence ("AAAA-MM"). Preservado mesmo se paga em outro mês. */
    val mesReferencia: String?,
    val contaFixaId: String?,
    /** Categoria da despesa gerada ao pagar (contas fixas e avulsas). */
    val categoriaId: String?,
    // --- Reservados para a v0.4 (parcelas de cartão/crediário) ---
    val contaCreditoId: String?,
    val compraId: String?,
    val numeroParcela: Int?,
    val totalParcelas: Int?,
    // ---------------------------------------------------------------
    val canceladaEm: Long?,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
    /** Versão 4 (v0.5): o empréstimo da parcela. Sem chave estrangeira (ADD COLUMN, ADR 0020). */
    val emprestimoId: String? = null,
)

/**
 * Liga uma movimentação a uma obrigação: "esta movimentação pagou X desta obrigação".
 * Uma obrigação pode ter vários pagamentos (parciais).
 */
@Serializable
@Entity(
    tableName = "pagamento",
    foreignKeys = [
        ForeignKey(entity = ObrigacaoEntidade::class, parentColumns = ["id"], childColumns = ["obrigacaoId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MovimentacaoEntidade::class, parentColumns = ["id"], childColumns = ["movimentacaoId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("obrigacaoId"), Index("movimentacaoId")],
)
data class PagamentoEntidade(
    @PrimaryKey val id: String,
    val obrigacaoId: String,
    val movimentacaoId: String,
    val valorAplicadoCentavos: Long,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
)

/**
 * Acréscimo ou abatimento em uma obrigação: juros, multa, tarifa, IOF, desconto...
 * O valor é sempre positivo; a natureza diz se soma ou subtrai do restante.
 * Encargos (juros, multa, tarifa, IOF) têm a sua própria despesa (movimentacaoId),
 * na categoria "Encargos financeiros". Descontos não geram movimentação.
 */
@Serializable
@Entity(
    tableName = "obrigacao_ajuste",
    foreignKeys = [
        ForeignKey(entity = ObrigacaoEntidade::class, parentColumns = ["id"], childColumns = ["obrigacaoId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PagamentoEntidade::class, parentColumns = ["id"], childColumns = ["pagamentoId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MovimentacaoEntidade::class, parentColumns = ["id"], childColumns = ["movimentacaoId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("obrigacaoId"), Index("pagamentoId"), Index("movimentacaoId")],
)
data class ObrigacaoAjusteEntidade(
    @PrimaryKey val id: String,
    val obrigacaoId: String,
    val pagamentoId: String?,
    /** JUROS | MULTA | TARIFA | IOF | OUTRO_ACRESCIMO | DESCONTO | OUTRO_ABATIMENTO | CREDITO */
    val natureza: String,
    val valorCentavos: Long,
    val movimentacaoId: String?,
    val observacao: String,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
)
