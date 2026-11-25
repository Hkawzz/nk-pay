package com.hkawzz.nkote.dados

import androidx.sqlite.db.SupportSQLiteDatabase
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.PartesCaixinha
import com.hkawzz.nkote.dominio.TipoMovimentacao
import java.time.LocalDate

// v0.9 (item 7, ADR 0036): as partes das caixinhas recalculadas pelo histórico. A regra fica
// em PartesCaixinha.recalcular (Kotlin puro); aqui só a leitura e a gravação, nos dois
// caminhos que precisam dela: a migração 8 → 9 e a restauração de backups de antes da v0.9.

fun ParteCaixinhaEntidade.paraDominio() = com.hkawzz.nkote.dominio.LinhaParte(
    id, caixinhaId, parteContaId, valorCentavos, movimentacaoId, grupoId, LocalDate.parse(data),
)

private fun MovimentacaoEntidade.paraRecalculo() = PartesCaixinha.Mov(
    id, TipoMovimentacao.valueOf(tipo), LocalDate.parse(data), valorCentavos, contaId, contaDestinoId, criadoEm,
)

private fun idDaLinha(m: PartesCaixinha.Mov, i: Int) = "parte-${m.id}-$i"

/** Linhas recalculadas a partir das contas e movimentações (as excluídas não contam). */
fun partesRecalculadas(contas: List<ContaEntidade>, movimentacoes: List<MovimentacaoEntidade>): List<ParteCaixinhaEntidade> {
    val caixinhas = contas.filter { it.natureza == Natureza.CAIXINHA.name }.map { it.id }.toSet()
    if (caixinhas.isEmpty()) return emptyList()
    val movs = movimentacoes.filter { it.excluidoEm == null && (it.contaId in caixinhas || it.contaDestinoId in caixinhas) }
    val criado = movs.associate { it.id to it.criadoEm }
    return PartesCaixinha.recalcular(caixinhas, movs.map { it.paraRecalculo() }, ::idDaLinha).map { l ->
        val quando = criado[l.movimentacaoId] ?: 0
        ParteCaixinhaEntidade(l.id, l.caixinhaId, l.parteContaId, l.valor, l.movimentacaoId, l.grupoId, l.data.toString(), quando, quando, null)
    }
}

/** A mesma conta, direto no SQLite, para a migração 8 → 9 (antes de o Room abrir o banco). */
fun recalcularPartesNoBanco(db: SupportSQLiteDatabase) {
    val caixinhas = mutableSetOf<String>()
    db.query("SELECT id FROM conta WHERE natureza = 'CAIXINHA'").use { while (it.moveToNext()) caixinhas += it.getString(0) }
    if (caixinhas.isEmpty()) return
    val movs = mutableListOf<PartesCaixinha.Mov>()
    db.query("SELECT id, tipo, data, valorCentavos, contaId, contaDestinoId, criadoEm FROM movimentacao WHERE excluidoEm IS NULL").use { c ->
        while (c.moveToNext()) {
            val conta = c.getString(4)
            val destino = if (c.isNull(5)) null else c.getString(5)
            if (conta !in caixinhas && destino !in caixinhas) continue
            movs += PartesCaixinha.Mov(c.getString(0), TipoMovimentacao.valueOf(c.getString(1)), LocalDate.parse(c.getString(2)), c.getLong(3), conta, destino, c.getLong(6))
        }
    }
    val criado = movs.associate { it.id to it.criadoEm }
    for (l in PartesCaixinha.recalcular(caixinhas, movs, ::idDaLinha)) {
        val quando = criado[l.movimentacaoId] ?: 0
        db.execSQL(
            "INSERT INTO `parte_caixinha` (`id`, `caixinhaId`, `parteContaId`, `valorCentavos`, `movimentacaoId`, `grupoId`, `data`, `criadoEm`, `atualizadoEm`, `excluidoEm`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)",
            arrayOf<Any?>(l.id, l.caixinhaId, l.parteContaId, l.valor, l.movimentacaoId, l.grupoId, l.data.toString(), quando, quando),
        )
    }
}
