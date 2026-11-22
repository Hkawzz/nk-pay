package com.nkpay.app.ui.movimentacoes

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.nkpay.app.dominio.Movimentacao
import com.nkpay.app.dominio.VisaoFinanceira
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.LinhaMovimentacao
import com.nkpay.app.ui.componentes.NkCabecalhoSecao
import java.time.LocalDate

/**
 * Adiciona a uma LazyColumn as movimentações agrupadas por dia
 * ("Hoje", "Ontem", "Sexta-feira, 20 de novembro"...), da mais recente para a mais antiga.
 */
fun LazyListScope.listaAgrupadaPorDia(
    movimentacoes: List<Movimentacao>,
    visao: VisaoFinanceira,
    hoje: LocalDate,
    contaReferencia: String? = null,
    aoAbrir: (String) -> Unit,
) {
    val porDia = movimentacoes.sortedByDescending { it.data }.groupBy { it.data }
    for ((dia, lista) in porDia) {
        item(key = "dia-$dia") { NkCabecalhoSecao(Formatos.cabecalhoDoDia(dia, hoje)) }
        item(key = "lista-$dia") {
            NkCard {
                lista.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LinhaMovimentacao(m, visao, aoClicar = { aoAbrir(m.id) }, contaReferencia = contaReferencia)
                }
            }
        }
    }
}
