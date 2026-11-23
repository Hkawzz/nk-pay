package com.nkpay.app.ui.credito

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nkpay.app.AppContainer
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.ClassificacaoDiferenca
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.ParteDoPagamento
import com.nkpay.app.dominio.StatusObrigacao
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.apagar.corDoStatus
import com.nkpay.app.ui.componentes.CampoData
import com.nkpay.app.ui.componentes.CampoDinheiro
import com.nkpay.app.ui.componentes.CampoEscolha
import com.nkpay.app.ui.componentes.EscolhaSegmentada
import com.nkpay.app.ui.componentes.IconeDaConta
import com.nkpay.app.ui.componentes.NkBotaoPrimario
import com.nkpay.app.ui.componentes.NkCabecalhoSecao
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.NkDivisor
import com.nkpay.app.ui.componentes.NkListItem
import com.nkpay.app.ui.componentes.NkTextoAjuda
import com.nkpay.app.ui.componentes.NkValor
import com.nkpay.app.ui.componentes.NkValorRotulado
import com.nkpay.app.ui.componentes.Opcao
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.componentes.TipoValor
import com.nkpay.app.ui.nkViewModel
import com.nkpay.app.ui.theme.Espaco
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth

// A fatura do cartão (v0.4, F6). Regras 6B.4 e ADR 0015.

enum class ModoPagamentoFatura { VALOR, COMPRAS }

class FaturaViewModel(private val container: AppContainer, val contaId: String, val mes: YearMonth) : ViewModel() {
    var carregado by mutableStateOf(false); private set
    var modo by mutableStateOf(ModoPagamentoFatura.VALOR)
    var origemId by mutableStateOf<String?>(null)
    var valorTexto by mutableStateOf("0,00")
    var data by mutableStateOf(container.relogio.hoje())
    var classificacao by mutableStateOf<ClassificacaoDiferenca?>(null)
    val escolhidas = mutableStateListOf<String>()
    var erro by mutableStateOf<String?>(null)
    var mensagem by mutableStateOf<String?>(null)
    var salvando by mutableStateOf(false); private set

    init {
        viewModelScope.launch {
            val vc = container.visaoCredito.filterNotNull().first()
            sugerirValor(vc.faturas(contaId, container.relogio.hoje()).filter { it.mes <= mes }.sumOf { it.restante })
            origemId = contaPadraoParaPagar(container.visao.filterNotNull().first())
            carregado = true
        }
    }

    /** Sugere o total em aberto até esta fatura (inclui as anteriores em atraso, 8.5). */
    fun sugerirValor(total: Long) { valorTexto = Dinheiro.formatarSemSimbolo(total); classificacao = null }
    fun mudarValor(t: String) { valorTexto = t; classificacao = null }

    fun pagar() {
        val origem = origemId ?: run { erro = "Escolha a conta de onde sai o dinheiro."; return }
        salvando = true
        viewModelScope.launch {
            val r = when (modo) {
                ModoPagamentoFatura.VALOR -> {
                    val valor = Dinheiro.ler(valorTexto) ?: 0
                    container.pagamentosCredito.pagarFatura(contaId, mes, origem, data, valor, classificacao)
                }
                ModoPagamentoFatura.COMPRAS -> container.pagamentosCredito.pagarCompras(contaId, mes, escolhidas.toList(), origem, data)
            }
            salvando = false
            when (r) {
                is Resultado.Sucesso -> { erro = null; escolhidas.clear(); classificacao = null; mensagem = "Pagamento registrado." }
                is Resultado.Falha -> erro = r.mensagem
            }
        }
    }

    fun desfazer(transferenciaId: String) {
        viewModelScope.launch {
            when (val r = container.pagamentosCredito.desfazer(transferenciaId)) {
                is Resultado.Sucesso -> mensagem = "Pagamento desfeito."
                is Resultado.Falha -> erro = r.mensagem
            }
        }
    }
}

/**
 * A fatura de um mês: total, pago, abatido e restante; as compras (com o status de cada
 * uma); o pagamento (um valor, que cobre também faturas anteriores em aberto, ou
 * compras escolhidas); e os pagamentos feitos, com desfazer.
 * No crediário, mostra as parcelas do mês: cada uma é paga na tela dela.
 */
@Composable
fun FaturaTela(contaId: String, mes: YearMonth, aoVoltar: () -> Unit, aoAbrirParcela: (String) -> Unit) {
    val container = LocalContainer.current
    val vm = nkViewModel(chave = "fatura-$contaId-$mes") { FaturaViewModel(it, contaId, mes) }
    val visao by container.visao.collectAsState()
    val credito by container.visaoCredito.collectAsState()
    val hoje = container.relogio.hoje()
    val conta = credito?.conta(contaId)
    val ehCartao = conta?.natureza == Natureza.CARTAO

    Subtela(titulo = (if (ehCartao) "Fatura de " else "Parcelas de ") + Formatos.mes(mes).lowercase(), aoVoltar = aoVoltar) { modifier ->
        val v = visao ?: return@Subtela
        val vc = credito ?: return@Subtela
        if (conta == null || !vm.carregado) return@Subtela
        val f = vc.fatura(contaId, mes, hoje)
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
            if (f == null) { NkTextoAjuda("Nada neste mês."); return@Column }
            NkCard {
                Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                    Text("${conta.nome} · ${f.status.rotulo}" + (f.vencimento?.let { " · vence ${Formatos.data(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = corDoStatus(f.status))
                    Row {
                        NkValorRotulado("Falta pagar", f.restante, Modifier.weight(1f), corValor = corDoStatus(f.status))
                        NkValorRotulado("Total", f.total, Modifier.weight(1f))
                    }
                    if (f.jurosPendentes > 0) Text("Inclui ${Dinheiro.formatar(f.jurosPendentes)} de juros do parcelamento: viram despesa quando forem pagos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (f.abatimentosGerais > 0) Text("${Dinheiro.formatar(f.abatimentosGerais)} abatidos sem escolher compras", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (f.status == StatusObrigacao.ATRASADA) Text("Atrasada: continua neste mês até ser paga. Nenhum juro é lançado sozinho.", style = MaterialTheme.typography.bodySmall, color = corDoStatus(f.status))
                }
            }

            NkCabecalhoSecao(if (ehCartao) "Compras" else "Parcelas")
            NkCard {
                f.parcelas.forEachIndexed { i, p ->
                    if (i > 0) NkDivisor()
                    val podeEscolher = ehCartao && vm.modo == ModoPagamentoFatura.COMPRAS && p.restante > 0
                    val marcada = p.obrigacao.id in vm.escolhidas
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        if (podeEscolher) { if (marcada) vm.escolhidas.remove(p.obrigacao.id) else vm.escolhidas.add(p.obrigacao.id) } else aoAbrirParcela(p.obrigacao.id)
                    }) {
                        if (podeEscolher) Checkbox(marcada, onCheckedChange = { if (it) vm.escolhidas.add(p.obrigacao.id) else vm.escolhidas.remove(p.obrigacao.id) })
                        NkListItem(
                            titulo = p.obrigacao.descricao,
                            subtitulo = p.status.rotulo + if (p.quitadaPelaFatura) " (pela fatura)" else "",
                            corSubtitulo = corDoStatus(p.status),
                            fim = { if (p.restante > 0) NkValor(p.restante) else NkValor(p.obrigacao.valorPrevisto, tipo = TipoValor.SECUNDARIO) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Pagamentos feitos nesta fatura (transferências), com desfazer.
            val ids = f.parcelas.map { it.obrigacao.id }.toSet()
            val transferencias = (vc.pagamentos.filter { it.obrigacaoId in ids }.map { it.movimentacaoId } +
                vc.pagamentosFatura.filter { it.contaCreditoId == contaId && it.mesReferencia == mes }.map { it.movimentacaoId }).distinct()
            if (transferencias.isNotEmpty()) {
                NkCabecalhoSecao("Pagamentos")
                NkCard {
                    transferencias.forEachIndexed { i, id ->
                        if (i > 0) NkDivisor()
                        val t = v.movimentacoes.firstOrNull { it.id == id }
                        val origem = v.conta(t?.contaId)
                        NkListItem(
                            titulo = Dinheiro.formatar(t?.valor ?: 0),
                            subtitulo = listOfNotNull(t?.data?.let { Formatos.data(it) }, origem?.nome).joinToString(" · "),
                            inicio = { origem?.let { IconeDaConta(it) } },
                            fim = { TextButton(onClick = { vm.desfazer(id) }) { Text("Desfazer") } },
                        )
                    }
                }
            }

            if (ehCartao && vc.restanteEmAberto(contaId, hoje) > 0) {
                Column(Modifier.padding(horizontal = Espaco.l), verticalArrangement = Arrangement.spacedBy(Espaco.s)) {
                    Text("Pagar", style = MaterialTheme.typography.titleMedium)
                    EscolhaSegmentada(
                        listOf(Opcao(ModoPagamentoFatura.VALOR, "Um valor"), Opcao(ModoPagamentoFatura.COMPRAS, "Escolher compras")),
                        vm.modo, { vm.modo = it; vm.erro = null },
                    )
                    CampoEscolha("Pagar com", opcoesPagarCom(v, vm.origemId), vm.origemId, { vm.origemId = it })
                    CampoData("Data do pagamento", vm.data, { vm.data = it })
                    if (vm.modo == ModoPagamentoFatura.VALOR) {
                        CampoDinheiro("Valor pago", vm.valorTexto, vm::mudarValor)
                        val valor = Dinheiro.ler(vm.valorTexto) ?: 0
                        val dist = vc.distribuir(contaId, valor, mes, hoje)
                        val anteriores = vc.faturas(contaId, hoje).filter { it.mes < mes && it.emAberto }
                        if (anteriores.isNotEmpty()) {
                            Text("Inclui ${Dinheiro.formatar(anteriores.sumOf { it.restante })} de faturas anteriores em aberto: o valor paga primeiro a mais antiga.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (dist.excedente > 0) {
                            PerguntaDaDiferenca("Você está pagando ${Dinheiro.formatar(dist.excedente)} a mais. O que é essa diferença?", ClassificacaoDiferenca.quandoPagaMais, vm.classificacao) { vm.classificacao = it }
                        }
                        if (valor > 0) {
                            NkCard(estilo = com.nkpay.app.ui.componentes.EstiloCard.ELEVADO) {
                                Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xxs)) {
                                    Text("Vai ser registrado:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("• Transferência de ${Dinheiro.formatar(valor)} para o ${conta.nome} (não é despesa)", style = MaterialTheme.typography.bodyMedium)
                                    for (parte in dist.partes) Text(
                                        when (parte) {
                                            is ParteDoPagamento.FaturaInteira -> "• Fatura de ${Formatos.mes(parte.mes).lowercase()} quitada (${Dinheiro.formatar(parte.valor)})"
                                            is ParteDoPagamento.AbatimentoGeral -> "• ${Dinheiro.formatar(parte.valor)} abatidos da fatura de ${Formatos.mes(parte.mes).lowercase()}, sem quitar compras"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (dist.excedente > 0 && vm.classificacao != null) Text("• Despesa de ${Dinheiro.formatar(dist.excedente)} de ${vm.classificacao!!.rotulo.lowercase()} (Encargos financeiros)", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        NkBotaoPrimario("Confirmar pagamento", vm::pagar, habilitado = valor > 0 && vm.origemId != null && !vm.salvando && (dist.excedente == 0L || vm.classificacao != null))
                    } else {
                        val total = f.parcelas.filter { it.obrigacao.id in vm.escolhidas }.sumOf { it.restante }
                        Text(
                            if (vm.escolhidas.isEmpty()) "Marque as compras que você está pagando." else "Total das compras escolhidas: ${Dinheiro.formatar(total)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        NkBotaoPrimario("Pagar compras escolhidas", vm::pagar, habilitado = vm.escolhidas.isNotEmpty() && vm.origemId != null && !vm.salvando)
                    }
                    vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    vm.mensagem?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            } else if (!ehCartao) {
                NkTextoAjuda("No crediário, cada parcela é paga na tela dela: toque na parcela.")
                vm.erro?.let { NkTextoAjuda(it) }
            }
            Spacer(Modifier.height(Espaco.xl))
        }
    }
}
