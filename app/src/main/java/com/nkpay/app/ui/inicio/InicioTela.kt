package com.nkpay.app.ui.inicio

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Saldos
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.componentes.BotaoAdicionar
import com.nkpay.app.ui.componentes.CartaoSecao
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.ui.backup.diasDesde
import com.nkpay.app.ui.componentes.LinhaConta
import com.nkpay.app.ui.componentes.LinhaMovimentacao
import com.nkpay.app.ui.componentes.RotuloDeSecao
import com.nkpay.app.ui.componentes.TituloDeTela
import com.nkpay.app.ui.theme.EstiloValor
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
 */
@Composable
fun InicioTela(
    aoNovaMovimentacao: () -> Unit,
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

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = { BotaoAdicionar("Nova movimentação", aoNovaMovimentacao) },
    ) { espaco ->
        val v = visao ?: return@Scaffold
        val r = v.resumo
        val mes = YearMonth.from(hoje)
        val totais = Saldos.totaisDoMes(v.movimentacoes, mes)

        Column(
            Modifier
                .padding(espaco)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            TituloDeTela("Início")

            // --- Disponível ---
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("Disponível", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Dinheiro.formatar(r.disponivel),
                        style = EstiloValor.destaque,
                        color = if (r.disponivel < 0) RosaDespesa else MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row {
                        ValorRotulado("Dinheiro próprio", Dinheiro.formatar(r.dinheiroProprio), Modifier.weight(1f))
                        ValorRotulado("Reservado", Dinheiro.formatar(r.reservado), Modifier.weight(1f))
                    }
                }
            }

            // --- Aviso de backup: nunca feito, ou feito há mais de 7 dias ---
            val dias = diasDesde(ultimoBackup, container.relogio.agora())
            if (v.contas.isNotEmpty() && (dias == null || dias >= DIAS_PARA_AVISO)) {
                CartaoSecao(cor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (dias == null) "Você ainda não fez nenhum backup" else "Último backup há $dias dias",
                                style = MaterialTheme.typography.titleSmall,
                                color = RosaDespesa,
                            )
                            Text(
                                "Se o celular for perdido, só um backup recupera seus dados.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = aoFazerBackup) { Text("Fazer") }
                    }
                }
            }

            // --- Saldo de cada conta de dinheiro próprio (as que formam o Disponível) ---
            val proprias = v.contas.filter { it.natureza == Natureza.PROPRIO && !it.arquivada }
            if (proprias.isNotEmpty()) {
                RotuloDeSecao("Suas contas")
                CartaoSecao {
                    proprias.forEachIndexed { i, conta ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        LinhaConta(conta, v.saldo(conta.id)) { aoAbrirConta(conta.id) }
                    }
                }
            }

            // --- Primeira abertura ---
            if (v.contas.isEmpty()) {
                CartaoSecao {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Cadastre suas contas e saldos iniciais", style = MaterialTheme.typography.titleMedium)
                        Passo(1, "Cadastre cada lugar onde você tem dinheiro (Pix, dinheiro físico, bancos) com o saldo de hoje.")
                        Passo(2, "Cadastre o VA e o VR como “Benefício”: eles ficam separados e não entram no Disponível.")
                        Passo(3, "Depois, lance entradas, despesas e transferências pelo botão +.")
                        Button(
                            onClick = aoCadastrarConta,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) { Text("Cadastrar conta") }
                    }
                }
            }

            // --- Benefícios ---
            if (r.beneficios.isNotEmpty()) {
                RotuloDeSecao("Benefícios · fora do Disponível")
                CartaoSecao {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(Dinheiro.formatar(r.totalBeneficios), style = EstiloValor.medio)
                        }
                        for (b in r.beneficios) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    b.conta.nome,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(Dinheiro.formatar(b.saldo), style = EstiloValor.lista, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // --- Mês atual ---
            RotuloDeSecao(Formatos.mes(mes))
            CartaoSecao {
                Row(Modifier.padding(20.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Entradas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Dinheiro.formatar(totais.entradas), style = EstiloValor.medio, color = VerdeEntrada)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Despesas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Dinheiro.formatar(totais.despesas), style = EstiloValor.medio, color = RosaDespesa)
                    }
                }
            }

            // --- Últimas movimentações ---
            if (v.movimentacoes.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RotuloDeSecao("Últimas movimentações", Modifier.weight(1f))
                    TextButton(onClick = aoVerMovimentacoes, modifier = Modifier.padding(end = 12.dp, top = 8.dp)) { Text("Ver todas") }
                }
                CartaoSecao {
                    // A lista já vem da mais recente para a mais antiga.
                    v.movimentacoes.take(5).forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        LinhaMovimentacao(m, v, aoClicar = { aoAbrirMovimentacao(m.id) })
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun ValorRotulado(rotulo: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium)
        Text(valor, style = EstiloValor.lista, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Passo(numero: Int, texto: String) {
    Row {
        Text("$numero.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
