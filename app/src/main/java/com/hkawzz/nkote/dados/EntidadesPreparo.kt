package com.hkawzz.nkote.dados

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// Tabela nova do esquema versão 5 (v0.6): pendências levadas para um mês (ADR 0024 e 0026).

/**
 * Marca uma conta a pagar de um mês anterior para aparecer TAMBÉM na lista do mês [mes]
 * (Preparar próximo mês). Não muda o mês de referência, o vencimento nem o valor da
 * obrigação: ela continua "atrasada" e pertencendo ao mês original (ADR 0013).
 */
@Serializable
@Entity(
    tableName = "pendencia_levada",
    foreignKeys = [ForeignKey(entity = ObrigacaoEntidade::class, parentColumns = ["id"], childColumns = ["obrigacaoId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("obrigacaoId"), Index("mes")],
)
data class PendenciaLevadaEntidade(
    @PrimaryKey val id: String,
    val obrigacaoId: String,
    /** "AAAA-MM": o mês em que ela passa a aparecer também. */
    val mes: String,
    val criadoEm: Long,
    val atualizadoEm: Long,
    val excluidoEm: Long?,
)
