package com.nkpay.app.ui.apagar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nkpay.app.AppContainer
import com.nkpay.app.dados.FixaEscolhida
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.PrepararMes
import com.nkpay.app.dominio.VisaoAPagar
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.componentes.CampoDinheiro
import com.nkpay.app.ui.componentes.NkBotaoPrimario
import com.nkpay.app.ui.componentes.NkBotaoSecundario
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.NkDivisor
import com.nkpay.app.ui.componentes.NkListItem
import com.nkpay.app.ui.componentes.NkTextoAjuda
import com.nkpay.app.ui.componentes.NkValor
import com.nkpay.app.ui.componentes.NkValorRotulado
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.nkViewModel
import com.nkpay.app.ui.theme.Espaco
import com.nkpay.app.ui.theme.RosaDespesa
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth

// v0.6 (regra 7.6, ADR 0026): o assistente "Preparar [mês]". Nada é gravado antes de Confirmar.

class PrepararMesViewModel(private val container: AppContainer, val mes: YearMonth) : ViewModel() {
    var passo by mutableStateOf(1)
    var carregado by mutableStateOf(false); private set
    /** Passo 1: id da conta fixa → marcada; valor (texto). Todas começam marcadas. */
    val marcadas = mutableStateMapOf<String, Boolean>()
    val valores = mutableStateMapOf<String, String>()
    /** Passo 3: pendências marcadas (todas começam desmarcadas). */
    val pendencias = mutableStateListOf<String>()
    var erro by mutableStateOf<String?>(null)
    var salvando by mutableStateOf(false); private set

    init {
        viewModelScope.launch {
            val v = container.visaoAPagar.filterNotNull().first()
            for (s in PrepararMes.fixas(v, mes)) {
                marcadas[s.fixa.id] = true
                valores[s.fixa.id] = Dinheiro.formatarSemSimbolo(s.valorSugerido)
            }
            carregado = true
        }
    }

    fun escolhidas(v: VisaoAPagar): List<FixaEscolhida> = PrepararMes.fixas(v, mes)
        .filter { marcadas[it.fixa.id] == true }
        .map { FixaEscolhida(it.fixa.id, Dinheiro.ler(valores[it.fixa.id].orEmpty()) ?: 0) }

    fun confirmar(v: VisaoAPagar, aoTerminar: (String) -> Unit) {
        salvando = true
        viewModelScope.launch {
            val r = container.prepararMes.confirmar(mes, escolhidas(v), pendencias.toList())
            salvando = false
            when (r) {
                is Resultado.Sucesso -> aoTerminar(
                    "${Formatos.mes(mes)} preparado: ${r.valor.fixasLancadas} conta(s) fixa(s) lançada(s) e ${r.valor.pendenciasLevadas} pendência(s) levada(s).",
                )
                is Resultado.Falha -> erro = r.mensagem
            }
        }
    }
}

private val TITULOS = listOf("Contas fixas", "Parcelas previstas", "Pendências atrasadas", "Revisão")

@Composable
fun PrepararMesTela(mes: YearMonth, aoVoltar: () -> Unit, aoTerminar: (String) -> Unit = { aoVoltar() }) {
    val container = LocalContainer.current
    val vm = nkViewModel(chave = "preparar-$mes") { PrepararMesViewModel(it, mes) }
    val visao by container.visaoAPagar.collectAsState()
    val credito by container.visaoCredito.collectAsState()
    val emprestimos by container.visaoEmprestimos.collectAsState()
    val hoje = container.relogio.hoje()

    Subtela(titulo = "Preparar " + Formatos.mes(mes).lowercase(), aoVoltar = { if (vm.passo > 1) vm.passo-- else aoVoltar() }) { modifier ->
        val v = visao ?: return@Subtela
        if (!vm.carregado) return@Subtela
        val fixas = PrepararMes.fixas(v, mes)
        val parcelas = PrepararMes.parcelasPrevistas(credito, emprestimos, mes, hoje)
        val pendencias = PrepararMes.pendencias(v, mes, hoje)
        Column(modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Espaco.l, vertical = Espaco.xs),
                verticalArrangement = Arrangement.spacedBy(Espaco.s),
            ) {
                Text("Passo ${vm.passo} de 4 · ${TITULOS[vm.passo - 1]}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                when (vm.passo) {
                    1 -> {
                        NkTextoAjuda("As contas fixas que ainda não entraram neste mês, todas marcadas, com o valor do mês anterior. Ajuste o valor ou desmarque a que não vai entrar.")
                        if (fixas.isEmpty()) NkTextoAjuda("Todas as contas fixas deste mês já foram lançadas.")
                        for (s in fixas) NkCard {
                            Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = vm.marcadas[s.fixa.id] == true, onCheckedChange = { vm.marcadas[s.fixa.id] = it })
                                    Column(Modifier.weight(1f)) {
                                        Text(s.fixa.nome, style = MaterialTheme.typography.bodyLarge)
                                        Text(s.vencimento?.let { "Vence ${Formatos.data(it)}" } ?: "Sem vencimento", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (vm.marcadas[s.fixa.id] == true) {
                                    CampoDinheiro("Valor de ${s.fixa.nome}", vm.valores[s.fixa.id].orEmpty(), { vm.valores[s.fixa.id] = it })
                                }
                            }
                        }
                    }
                    2 -> {
                        NkTextoAjuda("Faturas, parcelas de crediário e de empréstimo deste mês. Só para conferência: elas já existem e não mudam aqui.")
                        if (parcelas.isEmpty()) NkTextoAjuda("Nenhuma parcela em aberto neste mês.")
                        else NkCard {
                            parcelas.forEachIndexed { i, p ->
                                if (i > 0) NkDivisor()
                                NkListItem(
                                    titulo = p.titulo,
                                    subtitulo = (p.vencimento?.let { "Vence ${Formatos.data(it)}" } ?: "Sem vencimento") +
                                        (if (p.juros > 0) " · inclui ${Dinheiro.formatar(p.juros)} de juros" else ""),
                                    fim = { NkValor(p.valor) },
                                    linhas = 2,
                                )
                            }
                        }
                        if (parcelas.isNotEmpty()) NkValorRotulado("Total das parcelas, juros incluídos", parcelas.sumOf { it.valor })
                    }
                    3 -> {
                        NkTextoAjuda("Contas de meses anteriores ainda não pagas. Marque as que você quer ver também neste mês: elas continuam com o vencimento, o valor e o mês originais, e continuam atrasadas. As não marcadas continuam como pendentes no mês delas. Nada some.")
                        if (pendencias.isEmpty()) NkTextoAjuda("Nenhuma pendência de meses anteriores.")
                        else NkCard {
                            pendencias.forEachIndexed { i, s ->
                                if (i > 0) NkDivisor()
                                val id = s.obrigacao.id
                                Row(Modifier.padding(horizontal = Espaco.xs), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = id in vm.pendencias, onCheckedChange = { if (it) vm.pendencias.add(id) else vm.pendencias.remove(id) })
                                    Column(Modifier.weight(1f).padding(vertical = Espaco.xs)) {
                                        Text(s.obrigacao.descricao, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            (s.obrigacao.mesReferencia?.let { "ref. %02d/%d".format(it.monthValue, it.year) } ?: "") +
                                                (s.obrigacao.vencimento?.let { " · venceu ${Formatos.data(it)}" } ?: "") + " · ${s.status.rotulo}",
                                            style = MaterialTheme.typography.bodySmall, color = corDoStatus(s.status),
                                        )
                                    }
                                    NkValor(s.restante)
                                    Spacer(Modifier.padding(end = Espaco.s))
                                }
                            }
                        }
                    }
                    else -> {
                        val escolhidas = vm.escolhidas(v)
                        val levadas = pendencias.filter { it.obrigacao.id in vm.pendencias }
                        val total = PrepararMes.totalPrevisto(v, mes, hoje, escolhidas.map { it.valor }, levadas, parcelas)
                        NkCard {
                            Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                                Text("Vai entrar em ${Formatos.mes(mes).lowercase()}:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("• ${escolhidas.size} conta(s) fixa(s): ${Dinheiro.formatar(escolhidas.sumOf { it.valor })}")
                                for (e in escolhidas) Text("    ${fixas.firstOrNull { it.fixa.id == e.contaFixaId }?.fixa?.nome}: ${Dinheiro.formatar(e.valor)}", style = MaterialTheme.typography.bodySmall)
                                Text("• ${levadas.size} pendência(s) levada(s): ${Dinheiro.formatar(levadas.sumOf { maxOf(0, it.restante) })}")
                                Text("• Parcelas previstas (já existem): ${Dinheiro.formatar(parcelas.sumOf { it.valor })}")
                                NkDivisor()
                                NkValorRotulado("Total previsto do mês", total)
                                Text("Inclui o que já está no mês e falta pagar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (escolhidas.any { it.valor <= 0 }) Text("Toda conta fixa marcada precisa de um valor maior que zero.", color = RosaDespesa)
                        NkTextoAjuda("Nada foi gravado ainda. Ao confirmar, tudo é gravado de uma vez, com registro no Registro de alterações.")
                    }
                }
                vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(Espaco.s))
            }
            Row(Modifier.fillMaxWidth().padding(Espaco.m), horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
                if (vm.passo > 1) NkBotaoSecundario("Voltar", { vm.passo-- }, Modifier.weight(1f))
                if (vm.passo < 4) NkBotaoPrimario("Próximo", { vm.passo++ }, Modifier.weight(1f))
                else NkBotaoPrimario("Confirmar", { vm.confirmar(v, aoTerminar) }, Modifier.weight(1f), habilitado = !vm.salvando && vm.escolhidas(v).none { it.valor <= 0 })
            }
        }
    }
}
