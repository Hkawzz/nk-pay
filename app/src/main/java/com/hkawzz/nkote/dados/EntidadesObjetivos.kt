package com.hkawzz.nkote.dados

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// Tabelas novas do esquema versão 6 (v0.7): objetivos e reservas (ADR 0028 e 0029, regra 6F).

/** Um objetivo: reserva virtual sobre o dinheiro próprio. O guardado é calculado pelos movimentos. */
@Serializable
@Entity(tableName = "objetivo")
data class ObjetivoEntidade(
    @PrimaryKey val id: String,
    val nome: String,
    val icone: String?,
    /** Cor ARGB (como nas categorias). */
    val cor: Long?,
    /** Meta opcional: sem meta é uma "caixinha" livre. */
    val metaCentavos: Long?,
    /** "AAAA-MM-DD", opcional. */
    val prazo: String?,
    val concluidoEm: Long?,
    val arquivado: Boolean,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
    /** v0.8 (banco v7, ADR 0032): a conta CAIXINHA do objetivo. Vazia = objetivo da v0.7 ainda sem caixinha. */
    val contaCaixinhaId: String? = null,
)

/**
 * Um movimento de reserva. NÃO é movimentação de dinheiro: só muda o guardado do
 * objetivo. Mover grava duas linhas com o mesmo [grupoId]; desfazer exclui o grupo.
 */
@Serializable
@Entity(
    tableName = "movimento_reserva",
    foreignKeys = [ForeignKey(entity = ObjetivoEntidade::class, parentColumns = ["id"], childColumns = ["objetivoId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("objetivoId"), Index("grupoId")],
)
data class MovimentoReservaEntidade(
    @PrimaryKey val id: String,
    val objetivoId: String,
    /** "AAAA-MM-DD". */
    val data: String,
    /** + aumenta o guardado, − diminui. */
    val valorCentavos: Long,
    /** GUARDAR | RETIRAR | MOVER | AJUSTE */
    val tipo: String,
    val grupoId: String,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
)


/**
 * v0.9 (banco 9, item 7, ADR 0036): uma linha de composição de uma caixinha. Diz quanto
 * entrou (+) ou saiu (−) de uma PARTE (a conta de origem do dinheiro). O saldo da parte é a
 * soma das linhas válidas (de movimentações não excluídas); nada é gravado como saldo.
 * [parteContaId] vazia = "Sem origem definida" (só informativa: essa parte é o que sobra).
 * Movimento entre partes: duas linhas sem movimentação, com o mesmo [grupoId].
 */
@Serializable
@Entity(
    tableName = "parte_caixinha",
    foreignKeys = [
        ForeignKey(entity = ContaEntidade::class, parentColumns = ["id"], childColumns = ["caixinhaId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MovimentacaoEntidade::class, parentColumns = ["id"], childColumns = ["movimentacaoId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("caixinhaId"), Index("movimentacaoId"), Index("grupoId")],
)
data class ParteCaixinhaEntidade(
    @PrimaryKey val id: String,
    val caixinhaId: String,
    val parteContaId: String?,
    /** + entrou na parte, − saiu. */
    val valorCentavos: Long,
    val movimentacaoId: String?,
    val grupoId: String,
    /** "AAAA-MM-DD". */
    val data: String,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
)