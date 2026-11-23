package com.nkpay.app.ui.analises

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nkpay.app.dominio.Analises
import com.nkpay.app.dominio.AparenciaSugerida
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.FiltroPeriodo
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.Periodo
import com.nkpay.app.dominio.VisaoCredito
import com.nkpay.app.dominio.VisaoFinanceira
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.ajuda.ChaveAjuda
import com.nkpay.app.ui.ajuda.NkAjuda
import com.nkpay.app.ui.componentes.IconeDaCategoria
import com.nkpay.app.ui.componentes.IconeDaConta
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.NkCardDestaque
import com.nkpay.app.ui.componentes.NkDivisor
import com.nkpay.app.ui.componentes.NkListItem
import com.nkpay.app.ui.componentes.NkTextoAjuda
import com.nkpay.app.ui.componentes.NkValor
import com.nkpay.app.ui.componentes.NkValorAjustavel
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.componentes.TamanhoValor
import com.nkpay.app.ui.componentes.TipoValor
import com.nkpay.app.ui.componentes.aparenciaDaConta
import com.nkpay.app.ui.theme.Espaco
import com.nkpay.app.ui.theme.EstiloValor
import com.nkpay.app.ui.theme.Lilas
import com.nkpay.app.ui.theme.RaioV2
import com.nkpay.app.ui.theme.RosaDespesa
import com.nkpay.app.ui.theme.VerdeEntrada
import java.time.LocalDate

// =====================================================================================
// Mais → Análises (v0.4, A2). Os números vêm de dominio/Analises.kt (regra 6D), e todo
// gráfico tem os valores também em texto. A referência visual de Análises não estava
// disponível: a organização segue a lista da proposta (2.6) e o design system v2.
// Para reordenar as seções, mude só a lista ORDEM_DAS_SECOES.
// =====================================================================================

enum class SecaoAnalise { RESUMO, CATEGORIAS, EVOLUCAO, FLUXO, CONTAS, CREDITO, EMPRESTIMOS }

/** Ordem das seções na tela (fácil de ajustar depois, como pede o briefing). */
val ORDEM_DAS_SECOES = listOf(
    SecaoAnalise.RESUMO, SecaoAnalise.CATEGORIAS, SecaoAnalise.EVOLUCAO,
    SecaoAnalise.FLUXO, SecaoAnalise.CONTAS, SecaoAnalise.CREDITO, SecaoAnalise.EMPRESTIMOS,
)

@Composable
fun AnalisesTela(aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val visao by container.visao.collectAsState()
    val aPagar by container.visaoAPagar.collectAsState()
    val credito by container.visaoCredito.collectAsState()
    val filtro by container.filtroAnalises.collectAsState()
    val emprestimos by container.visaoEmprestimos.collectAsState()
    val hoje = container.relogio.hoje()

    Subtela(titulo = "Análises", aoVoltar = aoVoltar) { modifier ->
        val v = visao ?: return@Subtela
        val vc = credito ?: VisaoCredito.VAZIA
        val periodo = filtro.periodo(hoje)
        LazyColumn(modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Espaco.m, vertical = Espaco.xxs),
                    horizontalArrangement = Arrangement.spacedBy(Espaco.xs),
                ) {
                    for (f in FiltroPeriodo.entries) FilterChip(
                        selected = filtro == f,
                        onClick = { container.filtroAnalises.value = f },
                        label = { Text(f.rotulo) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            item {
                val texto = if (periodo.inicio == periodo.fim) Formatos.mes(periodo.fim) else "${Formatos.mes(periodo.inicio)} a ${Formatos.mes(periodo.fim).lowercase()}"
                NkTextoAjuda(texto)
            }
            if (v.movimentacoes.isEmpty()) {
                item { NkTextoAjuda("Ainda não há lançamentos. As análises aparecem conforme você usa o app.") }
            }
            for (secao in ORDEM_DAS_SECOES) item(key = secao.name) {
                when (secao) {
                    SecaoAnalise.RESUMO -> {
                        // v0.5: descontos de empréstimos concedidos são "concedidos", nunca "obtidos".
                        val concedidas = emprestimos?.let { ve -> ve.situacoes(com.nkpay.app.dominio.TipoEmprestimo.CONCEDIDO, hoje).flatMap { it.parcelas.map { p -> p.obrigacao.id } }.toSet() }.orEmpty()
                        SecaoResumo(
                            v, periodo,
                            aPagar?.let { Analises.descontosObtidos(it.ajustes, it.pagamentos, v.movimentacoes, periodo, concedidas) } ?: 0,
                            aPagar?.let { Analises.descontosConcedidos(it.ajustes, it.pagamentos, v.movimentacoes, periodo, concedidas) } ?: 0,
                        )
                    }
                    SecaoAnalise.CATEGORIAS -> SecaoCategorias(v, periodo)
                    SecaoAnalise.EVOLUCAO -> SecaoEvolucao(v, periodo)
                    SecaoAnalise.FLUXO -> SecaoFluxo(v, periodo)
                    SecaoAnalise.CONTAS -> SecaoContas(v, periodo)
                    SecaoAnalise.CREDITO -> SecaoCredito(vc, hoje)
                    SecaoAnalise.EMPRESTIMOS -> emprestimos?.let { SecaoEmprestimos(Analises.emprestimos(it, periodo, hoje)) }
                }
            }
            item { Spacer(Modifier.height(Espaco.xl)) }
        }
    }
}

@Composable
private fun Titulo(texto: String, ajuda: String? = null, chave: ChaveAjuda? = null) {
    Column(Modifier.padding(start = Espaco.l, end = Espaco.m, top = Espaco.l, bottom = Espaco.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(texto, style = MaterialTheme.typography.titleMedium, color = Lilas)
            chave?.let { NkAjuda(it) }
        }
        ajuda?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SemDados(texto: String = "Sem dados neste período.") {
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Espaco.m))
}

// ---------------------------------------------------------------- 1. resumo (destaque)

@Composable
private fun SecaoResumo(v: VisaoFinanceira, periodo: Periodo, descontos: Long, descontosConcedidos: Long = 0) {
    val t = Analises.totais(v.movimentacoes, periodo)
    NkCardDestaque {
        Column(Modifier.padding(Espaco.l), verticalArrangement = Arrangement.spacedBy(Espaco.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Resultado do período", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                NkAjuda(ChaveAjuda.RESULTADO, cor = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            NkValorAjustavel(t.resultado, estilo = EstiloValor.destaque, cor = if (t.resultado < 0) RosaDespesa else MaterialTheme.colorScheme.onBackground, prefixo = if (t.resultado > 0) "+ " else "")
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Entradas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    NkValorAjustavel(t.entradas, estilo = EstiloValor.lista, cor = VerdeEntrada)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Despesas registradas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        NkAjuda(ChaveAjuda.DESPESAS_REGISTRADAS, cor = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    NkValorAjustavel(t.despesas, estilo = EstiloValor.lista, cor = RosaDespesa)
                }
            }
            if (descontos > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Descontos obtidos: ${Dinheiro.formatar(descontos)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                NkAjuda(ChaveAjuda.DESCONTOS_OBTIDOS, cor = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (descontosConcedidos > 0) Text("Descontos concedidos: ${Dinheiro.formatar(descontosConcedidos)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Compras no cartão contam no mês da compra. Transferências, ajustes e pagamentos de fatura não entram.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

// ---------------------------------------------------------------- 2. categorias (barras)

@Composable
private fun SecaoCategorias(v: VisaoFinanceira, periodo: Periodo) {
    Titulo("Despesas por categoria")
    val lista = Analises.despesasPorCategoria(v.movimentacoes, v.categorias, periodo)
    NkCard {
        if (lista.isEmpty()) { SemDados("Nenhuma despesa neste período."); return@NkCard }
        Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.s)) {
            val maior = lista.first().total.toFloat()
            for (item in lista) {
                val cor = Color(item.categoria?.cor ?: AparenciaSugerida.NEUTRA.cor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconeDaCategoria(item.categoria, tamanho = 32.dp)
                    Spacer(Modifier.width(Espaco.s))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(item.categoria?.nome ?: "Sem categoria", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(Dinheiro.formatar(item.total), style = EstiloValor.pequeno)
                        }
                        Spacer(Modifier.height(Espaco.xxs))
                        Barra(item.total / maior, cor, "${item.categoria?.nome ?: "Sem categoria"}: ${"%.0f".format(item.percentual)}%")
                        Text("${"%.0f".format(item.percentual)}% das despesas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Barra(fracao: Float, cor: Color, descricao: String) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RaioV2.formaIcone).background(MaterialTheme.colorScheme.surfaceContainerHigh).semantics { contentDescription = descricao }) {
        Box(Modifier.fillMaxWidth(fracao.coerceIn(0.02f, 1f)).height(8.dp).clip(RaioV2.formaIcone).background(cor))
    }
}

// ---------------------------------------------------------------- 3. evolução (colunas)

@Composable
private fun SecaoEvolucao(v: VisaoFinanceira, periodo: Periodo) {
    Titulo("Evolução mensal", "Entradas (verde) e despesas (rosa) de cada mês.")
    val ev = Analises.evolucaoMensal(v.movimentacoes, periodo)
    val mesAtual = java.time.YearMonth.from(LocalContainer.current.relogio.hoje())
    val destaqueMes = Lilas.copy(alpha = 0.12f)
    NkCard {
        if (ev.all { it.semDados }) { SemDados("Os dados começam no seu primeiro lançamento."); return@NkCard }
        val maior = ev.maxOf { maxOf(it.entradas, it.despesas) }.coerceAtLeast(1).toFloat()
        Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
            Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Gráfico de entradas e despesas por mês" }) {
                val n = ev.size
                val largura = size.width / n
                val barra = (largura * 0.3f).coerceAtMost(28.dp.toPx())
                ev.forEachIndexed { i, m ->
                    val x = largura * i + largura / 2
                    // O mês atual tem um fundo lilás discreto.
                    if (m.mes == mesAtual && n > 1) drawRoundRect(destaqueMes, Offset(largura * i + 2.dp.toPx(), 0f), Size(largura - 4.dp.toPx(), size.height), CornerRadius(8.dp.toPx()))
                    fun coluna(valor: Long, dx: Float, cor: Color) {
                        val h = size.height * (valor / maior)
                        drawRoundRect(cor, Offset(x + dx, size.height - h), Size(barra, h), CornerRadius(4.dp.toPx()))
                    }
                    if (!m.semDados) {
                        coluna(m.entradas, -barra - 2.dp.toPx(), VerdeEntrada)
                        coluna(m.despesas, 2.dp.toPx(), RosaDespesa)
                    }
                }
            }
            for (m in ev.reversed()) {
                val atual = m.mes == mesAtual
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Formatos.mes(m.mes) + if (atual) " (atual)" else "",
                        style = if (atual) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                        color = if (atual) Lilas else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (m.semDados) Text("sem dados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Column(horizontalAlignment = Alignment.End) {
                        Text("+${Dinheiro.formatar(m.entradas)}  -${Dinheiro.formatar(m.despesas)}", style = EstiloValor.pequeno)
                        Text(
                            "Resultado " + (if (m.resultado > 0) "+" else "") + Dinheiro.formatar(m.resultado),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (m.resultado < 0) RosaDespesa else VerdeEntrada,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 4. fluxo

@Composable
private fun SecaoFluxo(v: VisaoFinanceira, periodo: Periodo) {
    Titulo("Fluxo do dinheiro próprio", "O que entrou e saiu do Pix, do dinheiro e dos bancos, incluindo o que você pagou de cartão e crediário.")
    val f = Analises.fluxo(v.contas, v.movimentacoes, periodo)
    NkCard {
        Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
            LinhaValor("Entrou", f.entrou, VerdeEntrada)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Saídas do dinheiro próprio", style = MaterialTheme.typography.bodyMedium)
                NkAjuda(ChaveAjuda.SAIDAS_PROPRIO)
                Spacer(Modifier.weight(1f))
                Text(Dinheiro.formatar(f.saiu), style = EstiloValor.lista, color = RosaDespesa)
            }
            if (f.pagamentosDeCredito > 0) LinhaValor("… dos quais pagamentos de cartão e crediário", f.pagamentosDeCredito, MaterialTheme.colorScheme.onSurfaceVariant)
            NkDivisor()
            LinhaValor("Saldo do período", f.saldo, if (f.saldo < 0) RosaDespesa else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun LinhaValor(rotulo: String, valor: Long, cor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(Dinheiro.formatar(valor), style = EstiloValor.lista, color = cor)
    }
}

// ---------------------------------------------------------------- 5. contas (linha)

@Composable
private fun SecaoContas(v: VisaoFinanceira, periodo: Periodo) {
    Titulo("Contas e carteiras", "Saldo de cada conta no fim de cada mês.")
    val contas = v.contas.filter { !it.arquivada && (it.natureza == Natureza.PROPRIO || it.natureza == Natureza.BENEFICIO) }
    NkCard {
        if (contas.isEmpty()) { SemDados("Nenhuma conta."); return@NkCard }
        contas.forEachIndexed { i, c ->
            if (i > 0) NkDivisor()
            val saldos = Analises.saldosNoFimDoMes(c, v.movimentacoes, periodo)
            val cor = aparenciaDaConta(c).second
            NkListItem(
                titulo = c.nome,
                subtitulo = if (c.natureza == Natureza.BENEFICIO) "Benefício" else "Dinheiro próprio",
                inicio = { IconeDaConta(c, tamanho = 36.dp) },
                fim = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (saldos.size > 1) Linha(saldos.map { it.saldo }, cor, "Evolução do saldo de ${c.nome}")
                        Spacer(Modifier.width(Espaco.xs))
                        NkValor(saldos.last().saldo)
                    }
                },
            )
        }
    }
}

@Composable
private fun Linha(valores: List<Long>, cor: Color, descricao: String) {
    Canvas(Modifier.width(64.dp).height(28.dp).semantics { contentDescription = descricao }) {
        val min = valores.min().toFloat()
        val max = valores.max().toFloat()
        val faixa = (max - min).takeIf { it > 0 } ?: 1f
        val passo = size.width / (valores.size - 1)
        val pontos = valores.mapIndexed { i, v -> Offset(i * passo, size.height - (v - min) / faixa * size.height) }
        for (i in 1 until pontos.size) drawLine(cor, pontos[i - 1], pontos[i], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- 6. crédito

@Composable
private fun SecaoCredito(vc: VisaoCredito, hoje: LocalDate) {
    val lista = Analises.credito(vc, hoje)
    if (lista.isEmpty()) return
    Titulo("Cartões e crediários", "O devido nunca entra no Saldo disponível.", ChaveAjuda.CARTOES_CREDIARIOS)
    NkCard {
        lista.forEachIndexed { i, r ->
            if (i > 0) NkDivisor()
            NkListItem(
                titulo = r.conta.nome,
                subtitulo = listOfNotNull(
                    "${r.faturasPagas} pagas · ${r.faturasEmAberto} em aberto · ${r.faturasAtrasadas} atrasadas",
                    r.limiteDisponivel?.let { "Limite disponível ${Dinheiro.formatar(it)}" },
                    r.comprasAnterioresAoApp.takeIf { it > 0 }?.let { "Compras anteriores ao app: ${Dinheiro.formatar(it)}" },
                ).joinToString("\n"),
                linhas = 3,
                inicio = { IconeDaConta(r.conta, tamanho = 36.dp) },
                fim = { NkValor(r.devido, tipo = if (r.devido > 0) TipoValor.NEUTRO else TipoValor.SECUNDARIO, tamanho = TamanhoValor.LISTA) },
            )
        }
    }
}

// ---------------------------------------------------------------- 7. empréstimos (v0.5)

@Composable
private fun SecaoEmprestimos(r: com.nkpay.app.dominio.ResumoDeEmprestimos) {
    if (r.quantidade == 0) return
    Titulo("Empréstimos", "O valor emprestado nunca é receita nem despesa: só os juros entram.")
    NkCard {
        Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
            LinhaValor("Total a pagar", r.totalAPagar, MaterialTheme.colorScheme.onSurface)
            LinhaValor("Total a receber", r.totalAReceber, MaterialTheme.colorScheme.onSurface)
            NkDivisor()
            LinhaValor("Juros pagos no período (encargos)", r.jurosPagos, RosaDespesa)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Juros recebidos no período", style = MaterialTheme.typography.bodyMedium)
                NkAjuda(ChaveAjuda.EMPRESTIMO_JUROS_RECEBIDOS)
                Spacer(Modifier.weight(1f))
                Text(Dinheiro.formatar(r.jurosRecebidos), style = EstiloValor.lista, color = VerdeEntrada)
            }
            if (r.parcelasAtrasadas > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${r.parcelasAtrasadas} ${if (r.parcelasAtrasadas == 1) "parcela atrasada" else "parcelas atrasadas"}", color = RosaDespesa, style = MaterialTheme.typography.bodyMedium)
                NkAjuda(ChaveAjuda.EMPRESTIMO_ATRASADA)
            }
        }
    }
}
