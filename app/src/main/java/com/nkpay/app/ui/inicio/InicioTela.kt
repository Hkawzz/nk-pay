package com.nkpay.app.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.Saldos
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.backup.diasDesde
import com.nkpay.app.ui.componentes.EstiloCard
import com.nkpay.app.ui.componentes.LinhaConta
import com.nkpay.app.ui.componentes.LinhaMovimentacao
import com.nkpay.app.ui.componentes.NkBotaoPrimario
import com.nkpay.app.ui.componentes.NkCabecalhoSecao
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.NkDivisor
import com.nkpay.app.ui.componentes.NkIconeRedondo
import com.nkpay.app.ui.componentes.NkListItem
import com.nkpay.app.ui.componentes.NkTituloTela
import com.nkpay.app.ui.componentes.NkValor
import com.nkpay.app.ui.componentes.NkValorRotulado
import com.nkpay.app.ui.componentes.TamanhoValor
import com.nkpay.app.ui.componentes.TipoValor
import com.nkpay.app.ui.theme.Espaco
import com.nkpay.app.ui.theme.RosaDespesa
import com.nkpay.app.ui.theme.VerdeEntrada
import java.time.YearMonth

/** Depois de quantos dias sem backup a Início mostra o aviso. */
private const val DIAS_PARA_AVISO = 7L

/**
 * Tela Início.
 * 1. Disponível em destaque (= dinheiro próprio − reservado).
 * 2. Dinheiro próprio e reservado, e o saldo de cada conta de dinheiro próprio.
 * 3. Benefícios, em bloco próprio (só se houver conta de benefício). Nunca somados ao disponível.
 * 4. Entradas e despesas do mês atual.
 * 5. Últimas 5 movimentações.
 * Sem botão "+": lançar fica na aba Movimentações (adendo v0.3, item A1).
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

    Scaffold(contentWindowInsets = WindowInsets(0)) { espaco ->
        val v = visao ?: return@Scaffold
        val r = v.resumo
        val mes = YearMonth.from(hoje)
        val totais = Saldos.totaisDoMes(v.movimentacoes, mes)

        Column(Modifier.padding(espaco).fillMaxSize().verticalScroll(rememberScrollState())) {
            NkTituloTela("Início")

            // --- Disponível ---
            NkCard(estilo = EstiloCard.DESTAQUE) {
                Column(Modifier.padding(Espaco.l), verticalArrangement = Arrangement.spacedBy(Espaco.m)) {
                    NkValorRotulado(
                        "Disponível",
                        r.disponivel,
                        tamanho = TamanhoValor.DESTAQUE,
                        corRotulo = MaterialTheme.colorScheme.onPrimaryContainer,
                        corValor = if (r.disponivel < 0) RosaDespesa else MaterialTheme.colorScheme.onBackground,
                    )
                    Row {
                        NkValorRotulado("Dinheiro próprio", r.dinheiroProprio, Modifier.weight(1f), tamanho = TamanhoValor.LISTA, corRotulo = MaterialTheme.colorScheme.onPrimaryContainer)
                        NkValorRotulado("Reservado", r.reservado, Modifier.weight(1f), tamanho = TamanhoValor.LISTA, corRotulo = MaterialTheme.colorScheme.onPrimaryContainer)
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

            // --- Saldo de cada conta de dinheiro próprio (as que formam o Disponível) ---
            val proprias = v.contas.filter { it.natureza == Natureza.PROPRIO && !it.arquivada }
            if (proprias.isNotEmpty()) {
                NkCabecalhoSecao("Suas contas")
                NkCard {
                    proprias.forEachIndexed { i, conta ->
                        if (i > 0) NkDivisor()
                        LinhaConta(conta, v.saldo(conta.id)) { aoAbrirConta(conta.id) }
                    }
                }
            }

            // --- Benefícios (nunca somados ao Disponível) ---
            if (r.beneficios.isNotEmpty()) {
                NkCabecalhoSecao("Benefícios · fora do Disponível") {
                    NkValor(r.totalBeneficios, tipo = TipoValor.SECUNDARIO, tamanho = TamanhoValor.PEQUENO)
                }
                NkCard {
                    r.beneficios.forEachIndexed { i, b ->
                        if (i > 0) NkDivisor()
                        LinhaConta(b.conta, b.saldo) { aoAbrirConta(b.conta.id) }
                    }
                }
            }

            // --- Mês atual ---
            NkCabecalhoSecao(Formatos.mes(mes))
            NkCard {
                Row(Modifier.padding(Espaco.l)) {
                    NkValorRotulado("Entradas", totais.entradas, Modifier.weight(1f), corValor = VerdeEntrada)
                    Spacer(Modifier.width(Espaco.xs))
                    NkValorRotulado("Despesas", totais.despesas, Modifier.weight(1f), corValor = RosaDespesa)
                }
            }

            // --- Últimas movimentações ---
            if (v.movimentacoes.isNotEmpty()) {
                NkCabecalhoSecao("Últimas movimentações") {
                    TextButton(onClick = aoVerMovimentacoes) { Text("Ver todas") }
                }
                NkCard {
                    // A lista já vem da mais recente para a mais antiga.
                    v.movimentacoes.take(5).forEachIndexed { i, m ->
                        if (i > 0) NkDivisor()
                        LinhaMovimentacao(m, v, aoClicar = { aoAbrirMovimentacao(m.id) })
                    }
                }
            }
            Spacer(Modifier.height(Espaco.xl))
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
