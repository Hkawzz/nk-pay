package com.hkawzz.nkote.ui.apagar

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
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.ContaFixa
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Obrigacoes
import com.hkawzz.nkote.dominio.SituacaoObrigacao
import com.hkawzz.nkote.dominio.StatusObrigacao
import com.hkawzz.nkote.ui.Formatos
import com.hkawzz.nkote.ui.LocalContainer
import com.hkawzz.nkote.ui.componentes.BotaoAdicionar
import com.hkawzz.nkote.ui.componentes.NkBotaoSecundario
import com.hkawzz.nkote.ui.componentes.NkCabecalhoSecao
import com.hkawzz.nkote.ui.componentes.NkCard
import com.hkawzz.nkote.ui.componentes.NkDivisor
import com.hkawzz.nkote.ui.componentes.NkEstadoVazio
import com.hkawzz.nkote.ui.componentes.NkIconeRedondo
import com.hkawzz.nkote.ui.componentes.NkListItem
import com.hkawzz.nkote.ui.componentes.NkTextoAjuda
import com.hkawzz.nkote.ui.componentes.NkTituloTela
import com.hkawzz.nkote.ui.componentes.NkValor
import com.hkawzz.nkote.ui.componentes.NkValorRotulado
import com.hkawzz.nkote.ui.nkViewModel
import com.hkawzz.nkote.ui.theme.EstiloValor
import com.hkawzz.nkote.ui.theme.Espaco
import com.hkawzz.nkote.ui.theme.Lilas
import com.hkawzz.nkote.ui.theme.RosaDespesa
import com.hkawzz.nkote.ui.theme.TextoSecundario
import com.hkawzz.nkote.ui.theme.VerdeEntrada
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
    val hoje = LocalContainer.current.relogio.hoje()
    // v0.9 (item 4): atrasada mostra a data e "venceu há X dias" em vermelho, no lugar do "ref.".
    val atrasada = s.status == StatusObrigacao.ATRASADA
    val vence = when {
        atrasada -> "${Formatos.data(o.vencimento!!)} · ${Formatos.venceuHa(o.vencimento, hoje)}"
        else -> o.vencimento?.let { "Vence ${Formatos.data(it)}" } ?: "Sem vencimento"
    }
    val mes = if (mostrarMes && !atrasada) o.mesReferencia?.let { " · ref. %02d/%d".format(it.monthValue, it.year) } ?: "" else ""
    NkListItem(
        titulo = o.descricao,
        subtitulo = "$vence$mes · ${o.origem.rotulo}",
        corSubtitulo = if (atrasada) RosaDespesa else MaterialTheme.colorScheme.onSurfaceVariant,
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

/** Uma fatura (ou mês de crediário) em A pagar. v0.9 (item 4): atrasada mostra "venceu há X dias" em vermelho. */
@Composable
private fun LinhaFaturaAPagar(c: com.hkawzz.nkote.dominio.Conta, f: com.hkawzz.nkote.dominio.Fatura, mesVisto: YearMonth, hoje: java.time.LocalDate, aoClicar: () -> Unit) {
    val atrasada = f.status == StatusObrigacao.ATRASADA
    NkListItem(
        titulo = (if (c.natureza == com.hkawzz.nkote.dominio.Natureza.CARTAO) "Fatura " else "Parcelas ") + c.nome,
        subtitulo = if (atrasada) {
            "${f.vencimento?.let { Formatos.data(it) }} · ${Formatos.venceuHa(f.vencimento, hoje)}"
        } else {
            (f.vencimento?.let { "Vence ${Formatos.data(it)}" } ?: "") +
                (if (f.mes != mesVisto) " · ref. %02d/%d".format(f.mes.monthValue, f.mes.year) else "")
        },
        corSubtitulo = if (atrasada) RosaDespesa else MaterialTheme.colorScheme.onSurfaceVariant,
        inicio = { NkIconeRedondo(iconeDoStatus(f.status), corDoStatus(f.status)) },
        fim = {
            Column(horizontalAlignment = Alignment.End) {
                if (f.restante > 0) NkValor(f.restante) else NkValor(f.total, cor = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(f.status.rotulo, style = MaterialTheme.typography.labelMedium, color = corDoStatus(f.status))
            }
        },
        aoClicar = aoClicar,
    )
}

/** Uma parcela de empréstimo recebido em A pagar. */
@Composable
private fun LinhaParcelaAPagar(p: com.hkawzz.nkote.dominio.ParcelaEmprestimo, nomeDaConta: String?, aoClicar: () -> Unit) {
    NkListItem(
        titulo = p.obrigacao.descricao,
        subtitulo = com.hkawzz.nkote.ui.emprestimos.rotuloDaParcela(p) + (nomeDaConta?.let { " · $it" } ?: ""),
        corSubtitulo = if (p.atrasada) RosaDespesa else corDoStatus(p.situacao.status),
        inicio = { NkIconeRedondo(iconeDoStatus(if (p.atrasada) StatusObrigacao.ATRASADA else p.situacao.status), if (p.atrasada) RosaDespesa else corDoStatus(p.situacao.status)) },
        fim = {
            Column(horizontalAlignment = Alignment.End) {
                if (p.restante > 0) NkValor(p.restante) else NkValor(p.obrigacao.valorPrevisto, cor = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.atrasada) Text(StatusObrigacao.ATRASADA.rotulo, style = MaterialTheme.typography.labelMedium, color = RosaDespesa)
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
fun APagarTela(
    aoNova: (YearMonth) -> Unit,
    aoAbrir: (String) -> Unit,
    aoContasFixas: () -> Unit,
    aoAbrirFatura: (String, YearMonth) -> Unit = { _, _ -> },
    aoAbrirParcelaEmprestimo: (String) -> Unit = {},
    aoPreparar: (YearMonth) -> Unit = {},
    avisoExterno: String? = null,
) {
    val container = LocalContainer.current
    val vm = nkViewModel { APagarViewModel(it) }
    val visao by container.visaoAPagar.collectAsState()
    val credito by container.visaoCredito.collectAsState()
    val emprestimos by container.visaoEmprestimos.collectAsState()
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
        // v0.4: faturas de cartão e meses de crediário (uma linha por fatura), deste mês e atrasadas.
        val faturas = credito?.let { vc ->
            vc.contasDeCredito.flatMap { c -> vc.faturas(c.id, hoje).map { c to it } }
                .filter { (_, f) -> f.mes == vm.mes || (f.mes < vm.mes && f.emAberto) }
                .sortedWith(compareBy({ it.second.mes }, { it.first.nome }))
        }.orEmpty()
        // v0.5 (6E.11): parcelas de empréstimo RECEBIDO deste mês e atrasadas. As de concedido nunca aparecem aqui.
        val parcelasEmprestimo = emprestimos?.let { ve ->
            ve.situacoes(com.hkawzz.nkote.dominio.TipoEmprestimo.RECEBIDO, hoje).flatMap { s -> s.parcelas.map { s to it } }
                .filter { (_, p) -> val m = p.obrigacao.mesReferencia; m == vm.mes || (m != null && m < vm.mes && p.restante > 0) }
                .sortedBy { it.second.obrigacao.vencimento }
        }.orEmpty()

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
                val falta = (doMes + anteriores).filter { it.status != StatusObrigacao.QUITADA }.sumOf { it.restante } +
                    faturas.sumOf { it.second.restante } + parcelasEmprestimo.sumOf { it.second.restante }
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
                    // v0.6 (7.6): o assistente fica sempre à mão.
                    Spacer(Modifier.height(Espaco.xs))
                    NkBotaoSecundario("Preparar ${Formatos.mes(vm.mes).lowercase()}", { aoPreparar(vm.mes) })
                }
            }
            // v0.6: sugerido na virada do mês (mês atual, ou o próximo na última semana), enquanto houver fixas a lançar.
            val mesAtual = YearMonth.from(hoje)
            val naVirada = vm.mes == mesAtual || (vm.mes == mesAtual.plusMonths(1) && hoje.dayOfMonth > hoje.lengthOfMonth() - 7)
            if (naVirada && com.hkawzz.nkote.dominio.PrepararMes.deveSugerir(v, vm.mes)) {
                item {
                    NkCard(estilo = com.hkawzz.nkote.ui.componentes.EstiloCard.ELEVADO, modifier = Modifier.padding(horizontal = Espaco.m)) {
                        NkListItem(
                            "Preparar ${Formatos.mes(vm.mes).lowercase()}?",
                            subtitulo = "Contas fixas, parcelas do mês e pendências numa conferência só. Nada é gravado antes de confirmar.",
                            linhas = 3,
                            aoClicar = { aoPreparar(vm.mes) },
                        )
                    }
                }
            }
            (aviso ?: avisoExterno)?.let { item { NkTextoAjuda(it) } }

            // v0.9 (item 4): tudo o que venceu e falta pagar fica junto, no topo, da mais antiga
            // para a mais nova. As outras seções mostram só o que ainda não venceu.
            val obrigacoesAtrasadas = (anteriores + doMes).filter { it.status == StatusObrigacao.ATRASADA }.distinctBy { it.obrigacao.id }
            val faturasAtrasadas = faturas.filter { it.second.status == StatusObrigacao.ATRASADA }
            val parcelasAtrasadas = parcelasEmprestimo.filter { it.second.atrasada }
            val atrasadas: List<Pair<java.time.LocalDate, @Composable () -> Unit>> =
                obrigacoesAtrasadas.map { s -> s.obrigacao.vencimento!! to @Composable { LinhaObrigacao(s, { aoAbrir(s.obrigacao.id) }) } } +
                    faturasAtrasadas.map { (c, f) -> f.vencimento!! to @Composable { LinhaFaturaAPagar(c, f, vm.mes, hoje) { aoAbrirFatura(c.id, f.mes) } } } +
                    parcelasAtrasadas.map { (s, p) -> p.obrigacao.vencimento!! to @Composable { LinhaParcelaAPagar(p, emprestimos?.conta(s.emprestimo.contaId)?.nome) { aoAbrirParcelaEmprestimo(p.obrigacao.id) } } }
            if (atrasadas.isNotEmpty()) {
                item { NkCabecalhoSecao("Atrasadas") }
                item {
                    NkCard {
                        atrasadas.sortedBy { it.first }.forEachIndexed { i, (_, linha) ->
                            if (i > 0) NkDivisor()
                            linha()
                        }
                    }
                }
            }

            val anterioresEmDia = anteriores.filter { it.status != StatusObrigacao.ATRASADA }
            if (anterioresEmDia.isNotEmpty()) {
                item { NkCabecalhoSecao("Pendentes de meses anteriores") }
                item {
                    NkCard {
                        anterioresEmDia.forEachIndexed { i, s ->
                            if (i > 0) NkDivisor()
                            LinhaObrigacao(s, { aoAbrir(s.obrigacao.id) }, mostrarMes = true)
                        }
                    }
                }
            }

            val faturasEmDia = faturas - faturasAtrasadas.toSet()
            if (faturasEmDia.isNotEmpty()) {
                item { NkCabecalhoSecao("Cartões e crediários") }
                item {
                    NkCard {
                        faturasEmDia.forEachIndexed { i, (c, f) ->
                            if (i > 0) NkDivisor()
                            LinhaFaturaAPagar(c, f, vm.mes, hoje) { aoAbrirFatura(c.id, f.mes) }
                        }
                    }
                }
            }

            val parcelasEmDia = parcelasEmprestimo - parcelasAtrasadas.toSet()
            if (parcelasEmDia.isNotEmpty()) {
                item { NkCabecalhoSecao("Empréstimos a pagar") }
                item {
                    NkCard {
                        parcelasEmDia.forEachIndexed { i, (s, p) ->
                            if (i > 0) NkDivisor()
                            LinhaParcelaAPagar(p, emprestimos?.conta(s.emprestimo.contaId)?.nome) { aoAbrirParcelaEmprestimo(p.obrigacao.id) }
                        }
                    }
                }
            }

            val doMesEmDia = doMes.filter { it.status != StatusObrigacao.ATRASADA }
            if (doMes.isEmpty() || doMesEmDia.isNotEmpty()) item { NkCabecalhoSecao("Deste mês") }
            if (doMes.isEmpty()) {
                item {
                    NkEstadoVazio(
                        icone = Icons.Rounded.DateRange,
                        titulo = "Nada a pagar neste mês",
                        texto = "Lance as contas fixas do mês ou adicione uma conta avulsa pelo +.",
                        modifier = Modifier.heightIn(min = 240.dp).height(280.dp),
                    )
                }
            } else if (doMesEmDia.isNotEmpty()) {
                item {
                    NkCard {
                        doMesEmDia.forEachIndexed { i, s ->
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
