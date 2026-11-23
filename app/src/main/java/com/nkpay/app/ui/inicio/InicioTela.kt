package com.nkpay.app.ui.inicio

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Movimentacao
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.dominio.VisaoFinanceira
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.ajuda.ChaveAjuda
import com.nkpay.app.ui.ajuda.NkAjuda
import com.nkpay.app.ui.backup.diasDesde
import com.nkpay.app.ui.componentes.EstiloCard
import com.nkpay.app.ui.componentes.IconeDaCategoria
import com.nkpay.app.ui.componentes.IconeDaConta
import com.nkpay.app.ui.componentes.NkBotaoPrimario
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.NkCardConta
import com.nkpay.app.ui.componentes.NkCardDestaque
import com.nkpay.app.ui.componentes.NkDivisor
import com.nkpay.app.ui.componentes.NkIconeRedondo
import com.nkpay.app.ui.componentes.NkListItem
import com.nkpay.app.ui.componentes.NkTituloTela
import com.nkpay.app.ui.componentes.NkValor
import com.nkpay.app.ui.componentes.NkValorAjustavel
import com.nkpay.app.ui.componentes.TipoValor
import com.nkpay.app.ui.componentes.aparenciaDaConta
import com.nkpay.app.ui.componentes.tituloDaMovimentacao
import com.nkpay.app.ui.theme.Espaco
import com.nkpay.app.ui.theme.EstiloValor
import com.nkpay.app.ui.theme.Lilas
import com.nkpay.app.ui.theme.RaioV2
import com.nkpay.app.ui.theme.RosaDespesa
import com.nkpay.app.ui.theme.VerdeEntrada
import java.time.LocalDate
import java.time.YearMonth

/** Depois de quantos dias sem backup a Início mostra o aviso. */
private const val DIAS_PARA_AVISO = 7L

/** Quantos cards de cada grade aparecem antes do "Ver todas" (adendo da Início). */
private const val CONTAS_VISIVEIS = 4
private const val BENEFICIOS_VISIVEIS = 3

/**
 * Tela Início (adendo-inicio-v0.4.md, aprovado em 23/11):
 * 1. Disponível centralizado e em destaque, com Dinheiro próprio e Reservado embaixo.
 * 2. Minhas contas: grade de 2 colunas, até 4 cards ("Ver todas as contas").
 * 3. Benefícios: grade de 3 colunas, até 3 cards. Nunca somados ao Disponível.
 * 4. Resumo do mês, com seletor: entradas e despesas (com a quantidade) e o resultado.
 * 5. Últimas movimentações agrupadas por dia.
 * 6. Avisos (backup, primeiro uso).
 * Cartões e crediários não aparecem aqui. "Reservado" não é conta: não ocupa card.
 */
@Composable
fun InicioTela(
    aoAbrirMovimentacao: (String) -> Unit,
    aoCadastrarConta: () -> Unit,
    aoVerMovimentacoes: () -> Unit,
    aoAbrirConta: (String) -> Unit,
    aoFazerBackup: () -> Unit,
) {
    val container = LocalContainer.current
    val visao by container.visao.collectAsState()
    val ultimoBackup by container.backup.registro.ultimo.collectAsState()
    val hoje = container.relogio.hoje()
    var mesResumo by rememberSaveable { mutableStateOf(YearMonth.from(hoje).toString()) }

    Scaffold(contentWindowInsets = WindowInsets(0)) { espaco ->
        val v = visao ?: return@Scaffold
        val r = v.resumo

        Column(Modifier.padding(espaco).fillMaxSize().verticalScroll(rememberScrollState())) {
            NkTituloTela("Início")

            // --- 1. Disponível (destaque 1 de 1) ---
            NkCardDestaque {
                Column(Modifier.fillMaxWidth().padding(Espaco.l), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Saldo disponível", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        NkAjuda(ChaveAjuda.SALDO_DISPONIVEL, cor = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(Espaco.xxs))
                    NkValorAjustavel(
                        r.disponivel,
                        estilo = EstiloValor.destaque,
                        cor = if (r.disponivel < 0) RosaDespesa else MaterialTheme.colorScheme.onBackground,
                        alinhamento = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // v0.5 (regra 6E.3): Saldo total e Guardado só aparecem quando há algo guardado (objetivos, v0.7).
                    if (r.reservado > 0) {
                        Spacer(Modifier.height(Espaco.m))
                        Row(Modifier.fillMaxWidth()) {
                            ValorCentral("Saldo total", r.dinheiroProprio, Modifier.weight(1f), ChaveAjuda.SALDO_TOTAL)
                            ValorCentral("Guardado", r.reservado, Modifier.weight(1f), ChaveAjuda.GUARDADO)
                        }
                    }
                }
            }

            // --- Aviso de backup: nunca feito, ou feito há 7 dias ou mais ---
            val dias = diasDesde(ultimoBackup, container.relogio.agora())
            if (v.contas.isNotEmpty() && (dias == null || dias >= DIAS_PARA_AVISO)) {
                NkCard(estilo = EstiloCard.ELEVADO, aoClicar = aoFazerBackup) {
                    NkListItem(
                        titulo = if (dias == null) "Você ainda não fez nenhum backup" else "Último backup há $dias dias",
                        subtitulo = "Se o celular for perdido, só um backup recupera seus dados.",
                        corTitulo = RosaDespesa,
                        linhas = 3,
                        inicio = { NkIconeRedondo(Icons.Rounded.Lock, RosaDespesa) },
                        fim = { TextButton(onClick = aoFazerBackup) { Text("Fazer") } },
                    )
                }
            }

            // --- Primeira abertura ---
            if (v.contas.isEmpty()) {
                NkCard {
                    Column(Modifier.padding(Espaco.l), verticalArrangement = Arrangement.spacedBy(Espaco.s)) {
                        Text("Cadastre suas contas e saldos iniciais", style = MaterialTheme.typography.titleMedium)
                        Passo(1, "Cadastre cada lugar onde você tem dinheiro (Pix, dinheiro físico, bancos) com o saldo de hoje.")
                        Passo(2, "Cadastre o VA e o VR como “Benefício”: eles ficam separados e não entram no Disponível.")
                        Passo(3, "Depois, lance entradas, despesas e transferências pela aba Movimentações.")
                        NkBotaoPrimario("Cadastrar conta", aoCadastrarConta)
                    }
                }
            }

            // --- 2. Minhas contas (as que formam o Disponível) ---
            val proprias = v.contas.filter { it.natureza == Natureza.PROPRIO && !it.arquivada }
            if (proprias.isNotEmpty()) {
                GradeDeContas("Minhas contas", proprias, v, colunas = 2, visiveis = CONTAS_VISIVEIS, textoVerTodas = "Ver todas as contas", aoAbrir = aoAbrirConta)
            }

            // --- 3. Benefícios (nunca somados ao Disponível) ---
            val beneficios = r.beneficios.map { it.conta }.filter { !it.arquivada }
            if (beneficios.isNotEmpty()) {
                GradeDeContas(
                    "Benefícios · fora do Disponível", beneficios, v, colunas = 3, visiveis = BENEFICIOS_VISIVEIS,
                    textoVerTodas = "Ver todos os benefícios", aoAbrir = aoAbrirConta,
                    complemento = {
                        NkAjuda(ChaveAjuda.BENEFICIOS)
                        NkValor(r.totalBeneficios, tipo = TipoValor.SECUNDARIO, tamanho = com.nkpay.app.ui.componentes.TamanhoValor.PEQUENO)
                    },
                )
            }

            // --- 4. Resumo do mês ---
            ResumoDoMes(v, YearMonth.parse(mesResumo)) { mesResumo = it.toString() }

            // --- 5. Últimas movimentações ---
            if (v.movimentacoes.isNotEmpty()) {
                CabecalhoInicio("Últimas movimentações") { TextButton(onClick = aoVerMovimentacoes) { Text("Ver todas") } }
                UltimasMovimentacoes(v, hoje, aoAbrirMovimentacao)
            }
            Spacer(Modifier.height(Espaco.xl))
        }
    }
}

@Composable
private fun ValorCentral(rotulo: String, centavos: Long, modifier: Modifier, ajuda: ChaveAjuda) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            NkAjuda(ajuda, cor = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        NkValorAjustavel(centavos, estilo = EstiloValor.lista, cor = MaterialTheme.colorScheme.onBackground, alinhamento = TextAlign.Center)
    }
}

/** Título de seção da Início (mais forte que o NkCabecalhoSecao, como na referência). */
@Composable
private fun CabecalhoInicio(texto: String, complemento: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = Espaco.l, end = Espaco.m, top = Espaco.l, bottom = Espaco.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texto, style = MaterialTheme.typography.titleMedium, color = Lilas, modifier = Modifier.weight(1f))
        complemento?.invoke()
    }
}

/**
 * Grade de contas: [colunas] por linha, [visiveis] cards de início. Se houver mais, a seta
 * "Ver todas" mostra o resto e vira "Recolher". O estado aberto não é salvo (adendo).
 */
@Composable
private fun GradeDeContas(
    titulo: String,
    contas: List<Conta>,
    v: VisaoFinanceira,
    colunas: Int,
    visiveis: Int,
    textoVerTodas: String,
    aoAbrir: (String) -> Unit,
    complemento: (@Composable () -> Unit)? = null,
) {
    var aberta by androidx.compose.runtime.remember { mutableStateOf(false) }
    CabecalhoInicio(titulo, complemento)
    val mostradas = if (aberta) contas else contas.take(visiveis)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Espaco.m).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(Espaco.xs),
    ) {
        for (linha in mostradas.chunked(colunas)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                for (c in linha) {
                    val (icone, cor) = aparenciaDaConta(c)
                    NkCardConta(c.nome, v.saldo(c.id), icone, cor, Modifier.weight(1f), compacto = colunas > 2) { aoAbrir(c.id) }
                }
                // Completa a linha para os cards manterem a mesma largura.
                repeat(colunas - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (contas.size > visiveis) {
            Row(
                Modifier.fillMaxWidth().clip(RaioV2.formaIcone).clickable { aberta = !aberta }.padding(vertical = Espaco.xs),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (aberta) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Lilas)
                Spacer(Modifier.width(Espaco.xxs))
                Text(if (aberta) "Recolher" else textoVerTodas, style = MaterialTheme.typography.labelLarge, color = Lilas)
            }
        }
    }
}

/**
 * Resumo do mês escolhido (v0.5, D1): o FLUXO do dinheiro próprio (regra 6E.2), com o
 * mesmo cálculo das Análises. Despesas pagas com VA/VR e compras no cartão não são saídas.
 */
@Composable
private fun ResumoDoMes(v: VisaoFinanceira, mes: YearMonth, aoMudarMes: (YearMonth) -> Unit) {
    val fluxo = com.nkpay.app.dominio.Analises.fluxo(v.contas, v.movimentacoes, com.nkpay.app.dominio.Periodo(mes, mes))
    val totalEntradas = fluxo.entrou
    val totalDespesas = fluxo.saiu
    val resultado = fluxo.saldo
    CabecalhoInicio("Resumo do mês") {
        IconButton(onClick = { aoMudarMes(mes.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Mês anterior do resumo") }
        Text(Formatos.mes(mes), style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { aoMudarMes(mes.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Próximo mês do resumo") }
    }
    Column(Modifier.padding(horizontal = Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espaco.xs)) {
            QuadroTotal("Entradas", totalEntradas, fluxo.quantidadeEntradas, VerdeEntrada, Modifier.weight(1f))
            QuadroTotal("Saídas", totalDespesas, fluxo.quantidadeSaidas, RosaDespesa, Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxWidth().clip(RaioV2.formaCard).background(MaterialTheme.colorScheme.surfaceContainer).padding(Espaco.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Saldo das movimentações", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NkAjuda(ChaveAjuda.SALDO_MOVIMENTACOES)
            }
            NkValorAjustavel(
                resultado,
                estilo = EstiloValor.medio,
                cor = if (resultado < 0) RosaDespesa else VerdeEntrada,
                prefixo = if (resultado > 0) "+ " else "",
            )
            Text("O que entrou menos o que saiu do seu dinheiro próprio. Compras no cartão saem quando a fatura é paga.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuadroTotal(rotulo: String, valor: Long, quantidade: Int, cor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier.clip(RaioV2.formaCard).background(cor.copy(alpha = 0.10f)).padding(Espaco.m),
        verticalArrangement = Arrangement.spacedBy(Espaco.xxs),
    ) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = cor)
        NkValorAjustavel(valor, estilo = EstiloValor.medio, cor = MaterialTheme.colorScheme.onSurface)
        Text(if (quantidade == 1) "1 movimentação" else "$quantidade movimentações", style = MaterialTheme.typography.bodySmall, color = cor)
    }
}

/** Rótulo do dia: Hoje, Ontem ou a data. */
private fun rotuloDoDia(d: LocalDate, hoje: LocalDate): String = when (d) {
    hoje -> "Hoje"
    hoje.minusDays(1) -> "Ontem"
    else -> Formatos.data(d)
}

/** "categoria · conta" (ou o que faz sentido em transferências e ajustes). */
private fun linhaDeApoio(m: Movimentacao, v: VisaoFinanceira): String {
    val conta = v.conta(m.contaId)?.nome ?: "Conta excluída"
    return when (m.tipo) {
        TipoMovimentacao.TRANSFERENCIA -> "Transferência · $conta → ${v.conta(m.contaDestinoId)?.nome ?: "Conta excluída"}"
        TipoMovimentacao.AJUSTE -> "Ajuste · $conta"
        else -> "${v.categoria(m.categoriaId)?.nome ?: m.tipo.rotulo} · $conta"
    }
}

/** As últimas movimentações, agrupadas por dia, com o ícone da categoria. */
@Composable
private fun UltimasMovimentacoes(v: VisaoFinanceira, hoje: LocalDate, aoAbrir: (String) -> Unit) {
    NkCard {
        // A lista já vem da mais recente para a mais antiga.
        val porDia = v.movimentacoes.take(6).groupBy { it.data }
        var primeiro = true
        for ((dia, movs) in porDia) {
            if (!primeiro) NkDivisor()
            primeiro = false
            Text(
                rotuloDoDia(dia, hoje).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Espaco.m, top = Espaco.s),
            )
            for (m in movs) {
                NkListItem(
                    titulo = tituloDaMovimentacao(m, v),
                    subtitulo = linhaDeApoio(m, v),
                    inicio = {
                        when {
                            m.tipo.temCategoria -> IconeDaCategoria(v.categoria(m.categoriaId))
                            else -> v.conta(m.contaId)?.let { IconeDaConta(it) } ?: Box(Modifier)
                        }
                    },
                    fim = {
                        when (m.tipo) {
                            TipoMovimentacao.ENTRADA -> NkValor(m.valor, tipo = TipoValor.ENTRADA)
                            TipoMovimentacao.DESPESA -> NkValor(m.valor, tipo = TipoValor.DESPESA)
                            TipoMovimentacao.AJUSTE -> NkValor(m.valor, tipo = TipoValor.COM_SINAL)
                            TipoMovimentacao.TRANSFERENCIA -> NkValor(m.valor, cor = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    aoClicar = { aoAbrir(m.id) },
                )
            }
        }
    }
}

@Composable
private fun Passo(numero: Int, texto: String) {
    Row {
        Text("$numero.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(Espaco.l))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
