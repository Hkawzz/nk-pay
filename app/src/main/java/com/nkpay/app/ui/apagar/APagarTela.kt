package com.nkpay.app.ui.apagar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.nkpay.app.AppContainer
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.ContaFixa
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Obrigacoes
import com.nkpay.app.dominio.SituacaoObrigacao
import com.nkpay.app.dominio.StatusObrigacao
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.componentes.BotaoAdicionar
import com.nkpay.app.ui.componentes.NkBotaoSecundario
import com.nkpay.app.ui.componentes.NkCabecalhoSecao
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.NkDivisor
import com.nkpay.app.ui.componentes.NkEstadoVazio
import com.nkpay.app.ui.componentes.NkIconeRedondo
import com.nkpay.app.ui.componentes.NkListItem
import com.nkpay.app.ui.componentes.NkTextoAjuda
import com.nkpay.app.ui.componentes.NkTituloTela
import com.nkpay.app.ui.componentes.NkValor
import com.nkpay.app.ui.componentes.NkValorRotulado
import com.nkpay.app.ui.nkViewModel
import com.nkpay.app.ui.theme.EstiloValor
import com.nkpay.app.ui.theme.Espaco
import com.nkpay.app.ui.theme.Lilas
import com.nkpay.app.ui.theme.RosaDespesa
import com.nkpay.app.ui.theme.TextoSecundario
import com.nkpay.app.ui.theme.VerdeEntrada
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Guarda o mês escolhido na aba A pagar (sobrevive à troca de abas). */
class APagarViewModel(container: AppContainer) : ViewModel() {
    var mes by mutableStateOf(YearMonth.from(container.relogio.hoje()))
}

/** Cor e ícone de cada status. */
fun corDoStatus(s: StatusObrigacao): Color = when (s) {
    StatusObrigacao.ATRASADA -> RosaDespesa
    StatusObrigacao.PARCIAL -> Lilas
    StatusObrigacao.PENDENTE -> TextoSecundario
    StatusObrigacao.QUITADA -> VerdeEntrada
}

private fun iconeDoStatus(s: StatusObrigacao) = when (s) {
    StatusObrigacao.ATRASADA -> Icons.Rounded.Warning
    StatusObrigacao.PARCIAL -> Icons.Rounded.Refresh
    StatusObrigacao.PENDENTE -> Icons.Rounded.DateRange
    StatusObrigacao.QUITADA -> Icons.Rounded.CheckCircle
}

/** Linha de uma conta a pagar: descrição, vencimento, quanto falta e o status. */
@Composable
fun LinhaObrigacao(s: SituacaoObrigacao, aoClicar: () -> Unit, mostrarMes: Boolean = false) {
    val o = s.obrigacao
    val vence = o.vencimento?.let { "Vence ${Formatos.data(it)}" } ?: "Sem vencimento"
    val mes = if (mostrarMes) o.mesReferencia?.let { " · ref. %02d/%d".format(it.monthValue, it.year) } ?: "" else ""
    NkListItem(
        titulo = o.descricao,
        subtitulo = "$vence$mes · ${o.origem.rotulo}",
        inicio = { NkIconeRedondo(iconeDoStatus(s.status), corDoStatus(s.status)) },
        fim = {
            Column(horizontalAlignment = Alignment.End) {
                if (s.status == StatusObrigacao.QUITADA) {
                    NkValor(o.valorPrevisto + s.acrescimos - s.abatimentos, cor = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    NkValor(s.restante)
                }
                Text(s.status.rotulo, style = MaterialTheme.typography.labelMedium, color = corDoStatus(s.status))
            }
        },
        aoClicar = aoClicar,
    )
}

/**
 * Aba A pagar: as contas do mês escolhido (pelo mês de referência), as pendências de
 * meses anteriores (continuam no mês original, ADR 0013) e o lançamento das contas fixas.
 */
@Composable
fun APagarTela(aoNova: (YearMonth) -> Unit, aoAbrir: (String) -> Unit, aoContasFixas: () -> Unit) {
    val container = LocalContainer.current
    val vm = nkViewModel { APagarViewModel(it) }
    val visao by container.visaoAPagar.collectAsState()
    val hoje = container.relogio.hoje()
    var lancando by rememberSaveable { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val escopo = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = { BotaoAdicionar("Nova conta a pagar") { aoNova(vm.mes) } },
    ) { espaco ->
        val v = visao ?: return@Scaffold
        val doMes = v.doMes(vm.mes, hoje).sortedWith(compareBy({ it.status == StatusObrigacao.QUITADA }, { it.obrigacao.vencimento }))
        val anteriores = v.pendentesAnteriores(vm.mes, hoje)
        val disponiveis = Obrigacoes.fixasDisponiveisParaLancar(v.contasFixas, v.obrigacoes, vm.mes)

        LazyColumn(Modifier.padding(espaco).fillMaxSize()) {
            item { NkTituloTela("A pagar") }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = Espaco.xs), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.mes = vm.mes.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Mês anterior")
                    }
                    Text(Formatos.mes(vm.mes), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.mes = vm.mes.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Próximo mês")
                    }
                }
            }
            item {
                val falta = (doMes + anteriores).filter { it.status != StatusObrigacao.QUITADA }.sumOf { it.restante }
                val pago = doMes.sumOf { it.pago }
                NkCard {
                    Row(Modifier.padding(Espaco.l)) {
                        NkValorRotulado("Falta pagar", falta, Modifier.weight(1f), corValor = if (falta > 0) RosaDespesa else null)
                        NkValorRotulado("Já pago (deste mês)", pago, Modifier.weight(1f), corValor = VerdeEntrada)
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = Espaco.m, vertical = Espaco.xs)) {
                    if (v.contasFixas.none { !it.arquivada }) {
                        NkBotaoSecundario("Cadastrar contas fixas", aoContasFixas)
                    } else {
                        NkBotaoSecundario(
                            if (disponiveis.isEmpty()) "Contas fixas de ${Formatos.mes(vm.mes).lowercase()} já lançadas" else "Lançar contas fixas de ${Formatos.mes(vm.mes).lowercase()}",
                            { lancando = true },
                            habilitado = disponiveis.isNotEmpty(),
                        )
                    }
                }
            }
            aviso?.let { item { NkTextoAjuda(it) } }

            if (anteriores.isNotEmpty()) {
                item { NkCabecalhoSecao("Pendentes de meses anteriores") }
                item {
                    NkCard {
                        anteriores.forEachIndexed { i, s ->
                            if (i > 0) NkDivisor()
                            LinhaObrigacao(s, { aoAbrir(s.obrigacao.id) }, mostrarMes = true)
                        }
                    }
                }
            }

            item { NkCabecalhoSecao("Deste mês") }
            if (doMes.isEmpty()) {
                item {
                    NkEstadoVazio(
                        icone = Icons.Rounded.DateRange,
                        titulo = "Nada a pagar neste mês",
                        texto = "Lance as contas fixas do mês ou adicione uma conta avulsa pelo +.",
                        modifier = Modifier.heightIn(min = 240.dp).height(280.dp),
                    )
                }
            } else {
                item {
                    NkCard {
                        doMes.forEachIndexed { i, s ->
                            if (i > 0) NkDivisor()
                            LinhaObrigacao(s, { aoAbrir(s.obrigacao.id) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Espaco.fimDeListaComBotao)) }
        }

        if (lancando) {
            DialogoLancarFixas(
                mes = vm.mes,
                disponiveis = disponiveis,
                aoConfirmar = { ids ->
                    lancando = false
                    escopo.launch {
                        val r = container.obrigacoes.lancarFixas(vm.mes, ids)
                        aviso = when (r) {
                            is Resultado.Sucesso -> if (r.valor == 1) "1 conta fixa lançada." else "${r.valor} contas fixas lançadas."
                            is Resultado.Falha -> r.mensagem
                        }
                    }
                },
                aoCancelar = { lancando = false },
            )
        }
    }
}

/**
 * Seleção MANUAL das contas fixas a lançar no mês (ADR 0013): nada vem marcado.
 * Só aparecem as que ainda não foram lançadas naquele mês, então repetir não duplica.
 */
@Composable
fun DialogoLancarFixas(
    mes: YearMonth,
    disponiveis: List<ContaFixa>,
    aoConfirmar: (List<String>) -> Unit,
    aoCancelar: () -> Unit,
) {
    val marcadas = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text("Contas fixas de ${Formatos.mes(mes).lowercase()}") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Text(
                        "Marque as que entram neste mês. As que já foram lançadas não aparecem.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Espaco.xs),
                    )
                }
                items(disponiveis, key = { it.id }) { f ->
                    val marcada = f.id in marcadas
                    Row(
                        Modifier.fillMaxWidth().clickable { if (marcada) marcadas.remove(f.id) else marcadas.add(f.id) }.padding(vertical = Espaco.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = marcada, onCheckedChange = { if (it) marcadas.add(f.id) else marcadas.remove(f.id) })
                        Column(Modifier.weight(1f)) {
                            Text(f.nome, style = MaterialTheme.typography.bodyLarge)
                            val venc = Obrigacoes.vencimentoNoMes(f.diaVencimento, mes)?.let { "vence ${Formatos.data(it)}" } ?: "sem vencimento"
                            Text(venc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Dinheiro.formatar(f.valorPadrao), style = EstiloValor.pequeno)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { aoConfirmar(marcadas.toList()) }, enabled = marcadas.isNotEmpty()) {
                Text(if (marcadas.isEmpty()) "Lançar" else "Lançar ${marcadas.size}")
            }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )
}
