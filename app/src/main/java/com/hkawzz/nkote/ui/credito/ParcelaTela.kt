package com.hkawzz.nkote.ui.credito

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.ClassificacaoDiferenca
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.PlanoDePagamento
import com.hkawzz.nkote.dominio.ResultadoPlano
import com.hkawzz.nkote.dominio.VisaoFinanceira
import com.hkawzz.nkote.ui.Formatos
import com.hkawzz.nkote.ui.LocalContainer
import com.hkawzz.nkote.ui.apagar.corDoStatus
import com.hkawzz.nkote.ui.componentes.CampoData
import com.hkawzz.nkote.ui.componentes.CampoDinheiro
import com.hkawzz.nkote.ui.componentes.CampoEscolha
import com.hkawzz.nkote.ui.componentes.EstiloCard
import com.hkawzz.nkote.ui.componentes.IconeDaConta
import com.hkawzz.nkote.ui.componentes.NkBotaoPrimario
import com.hkawzz.nkote.ui.componentes.NkCabecalhoSecao
import com.hkawzz.nkote.ui.componentes.NkCard
import com.hkawzz.nkote.ui.componentes.NkDivisor
import com.hkawzz.nkote.ui.componentes.NkListItem
import com.hkawzz.nkote.ui.componentes.NkValor
import com.hkawzz.nkote.ui.componentes.NkValorRotulado
import com.hkawzz.nkote.ui.componentes.Opcao
import com.hkawzz.nkote.ui.componentes.Subtela
import com.hkawzz.nkote.ui.nkViewModel
import com.hkawzz.nkote.ui.theme.Espaco
import com.hkawzz.nkote.ui.theme.RosaDespesa
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Pagar em modo crédito: parcela do crediário (v0.4, F5). A fatura do cartão vem no F6.

/** Contas que podem pagar crédito: dinheiro próprio e benefícios, sem arquivadas (ADR 0017). */
fun opcoesPagarCom(v: VisaoFinanceira, selecionada: String?): List<Opcao<String>> =
    v.contas.filter { it.ehMeioDePagamento && (!it.arquivada || it.id == selecionada) }.map { c ->
        Opcao(c.id, c.nome, (if (c.natureza == Natureza.BENEFICIO) "Benefício · " else "") + "Saldo " + Dinheiro.formatar(v.saldo(c.id)))
    }

fun contaPadraoParaPagar(v: VisaoFinanceira): String? = v.contas.firstOrNull { !it.arquivada && it.natureza == Natureza.PROPRIO }?.id

/** Chips da pergunta "o que é a diferença?" (nunca respondida sozinha). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerguntaDaDiferenca(texto: String, opcoes: List<ClassificacaoDiferenca>, escolhida: ClassificacaoDiferenca?, aoEscolher: (ClassificacaoDiferenca) -> Unit) {
    Text(texto, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Espaco.xs)) {
        for (c in opcoes) {
            FilterChip(
                selected = escolhida == c,
                onClick = { aoEscolher(c) },
                label = { Text(c.rotulo) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/** Resumo do que um pagamento de crédito vai gravar. */
@Composable
fun ResumoPagamentoCredito(transferencia: Long, origem: String, credito: String, encargo: Long, desconto: Long, natureza: String?, restanteDepois: Long?, juros: Long = 0) {
    NkCard(estilo = EstiloCard.ELEVADO) {
        Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xxs)) {
            Text("Vai ser registrado:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("• Transferência de ${Dinheiro.formatar(transferencia)} de $origem para $credito (não é despesa)", style = MaterialTheme.typography.bodyMedium)
            if (juros > 0) Text("• Despesa de ${Dinheiro.formatar(juros)} de juros do parcelamento (Encargos financeiros)", style = MaterialTheme.typography.bodyMedium)
            if (encargo > 0) Text("• Despesa de ${Dinheiro.formatar(encargo)} de ${natureza?.lowercase()} (Encargos financeiros)", style = MaterialTheme.typography.bodyMedium)
            if (desconto > 0) Text("• ${natureza} de ${Dinheiro.formatar(desconto)}: reduz o que você deve (não é receita)", style = MaterialTheme.typography.bodyMedium)
            if (restanteDepois != null) Text(
                if (restanteDepois > 0) "Depois disso, ainda faltam ${Dinheiro.formatar(restanteDepois)}." else "Fica paga.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (restanteDepois > 0) RosaDespesa else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

class PagarParcelaViewModel(private val container: AppContainer, val obrigacaoId: String) : ViewModel() {
    var carregado by mutableStateOf(false); private set
    var contaId by mutableStateOf<String?>(null)
    var valorTexto by mutableStateOf("0,00")
    var data by mutableStateOf(container.relogio.hoje())
    var classificacao by mutableStateOf<ClassificacaoDiferenca?>(null)
    var erro by mutableStateOf<String?>(null)
    var salvando by mutableStateOf(false); private set

    init {
        viewModelScope.launch {
            val vc = container.visaoCredito.filterNotNull().first()
            vc.situacaoDaParcela(obrigacaoId, container.relogio.hoje())?.let { valorTexto = Dinheiro.formatarSemSimbolo(it.restante) }
            contaId = contaPadraoParaPagar(container.visao.filterNotNull().first())
            carregado = true
        }
    }

    fun mudarValor(t: String) { valorTexto = t; classificacao = null }

    fun pagar() {
        val valor = Dinheiro.ler(valorTexto) ?: run { erro = "Valor inválido."; return }
        val conta = contaId ?: run { erro = "Escolha a conta de onde sai o dinheiro."; return }
        salvando = true
        viewModelScope.launch {
            when (val r = container.pagamentosCredito.pagarParcela(obrigacaoId, conta, data, valor, classificacao)) {
                is Resultado.Sucesso -> { erro = null; classificacao = null }
                is Resultado.Falha -> erro = r.mensagem
            }
            salvando = false
        }
    }

    fun desfazer(transferenciaId: String) {
        viewModelScope.launch {
            (container.pagamentosCredito.desfazer(transferenciaId) as? Resultado.Falha)?.let { erro = it.mensagem }
        }
    }
}

/**
 * Uma parcela: situação, pagamentos (com desfazer) e, no crediário, o formulário de
 * pagamento. Parcelas de cartão são pagas pela fatura.
 */
@Composable
fun ParcelaTela(obrigacaoId: String, aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val vm = nkViewModel(chave = "parcela-$obrigacaoId") { PagarParcelaViewModel(it, obrigacaoId) }
    val visao by container.visao.collectAsState()
    val credito by container.visaoCredito.collectAsState()
    val hoje = container.relogio.hoje()

    Subtela(titulo = "Parcela", aoVoltar = aoVoltar) { modifier ->
        val v = visao ?: return@Subtela
        val vc = credito ?: return@Subtela
        val s = vc.situacaoDaParcela(obrigacaoId, hoje) ?: return@Subtela
        val o = s.obrigacao
        val conta: Conta = vc.conta(o.contaCreditoId) ?: return@Subtela
        if (!vm.carregado) return@Subtela
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            NkCard {
                Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xxs)) {
                    Text(o.descricao, style = MaterialTheme.typography.titleMedium)
                    Text("${conta.nome} · vence ${o.vencimento?.let { Formatos.data(it) }} · ${s.status.rotulo}", style = MaterialTheme.typography.bodySmall, color = corDoStatus(s.status))
                    Row {
                        NkValorRotulado("Falta pagar", s.restante, Modifier.weight(1f), corValor = corDoStatus(s.status))
                        NkValorRotulado("Valor", o.valorPrevisto, Modifier.weight(1f))
                    }
                    // v0.6 (6B.8): compra com juros, a parcela tem a parte da compra e os juros.
                    if (s.jurosPrevistos > 0) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            "Parte da compra ${Dinheiro.formatar(s.parteDaCompra)} · juros do parcelamento ${Dinheiro.formatar(s.jurosPrevistos)}" +
                                (if (s.jurosPendentes > 0) " (${Dinheiro.formatar(s.jurosPendentes)} a pagar)" else ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                        )
                        com.hkawzz.nkote.ui.ajuda.NkAjuda(com.hkawzz.nkote.ui.ajuda.ChaveAjuda.JUROS_DO_PARCELAMENTO)
                    }
                }
            }

            // Pagamentos desta parcela (cada um pela transferência que o fez).
            val pagamentos = vc.pagamentos.filter { it.obrigacaoId == obrigacaoId }
            if (pagamentos.isNotEmpty()) {
                NkCabecalhoSecao("Pagamentos")
                NkCard {
                    pagamentos.forEachIndexed { i, p ->
                        if (i > 0) NkDivisor()
                        val t = v.movimentacoes.firstOrNull { it.id == p.movimentacaoId }
                        NkListItem(
                            titulo = Dinheiro.formatar(p.valor),
                            subtitulo = listOfNotNull(t?.data?.let { Formatos.data(it) }, v.conta(t?.contaId)?.nome).joinToString(" · "),
                            inicio = { v.conta(t?.contaId)?.let { IconeDaConta(it) } },
                            fim = { TextButton(onClick = { vm.desfazer(p.movimentacaoId) }) { Text("Desfazer") } },
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = Espaco.l), verticalArrangement = Arrangement.spacedBy(Espaco.s)) {
                if (conta.natureza == Natureza.CARTAO) {
                    Text("Parcelas de cartão são pagas pela fatura (total, parcial ou escolhendo compras), na tela do cartão.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (s.restante > 0) {
                    CampoEscolha("Pagar com", opcoesPagarCom(v, vm.contaId), vm.contaId, { vm.contaId = it })
                    CampoDinheiro("Valor pago", vm.valorTexto, vm::mudarValor)
                    CampoData("Data do pagamento", vm.data, { vm.data = it })
                    val valor = Dinheiro.ler(vm.valorTexto) ?: 0
                    val resultado = com.hkawzz.nkote.dominio.Pagamentos.planejar(s.restante, valor, vm.classificacao)
                    val diferenca = if (valor < s.restante) s.restante - valor else valor - s.restante
                    val opcoes = (resultado as? ResultadoPlano.PrecisaClassificar)?.opcoes
                        ?: vm.classificacao?.let { c -> if (c.pagouMenos) ClassificacaoDiferenca.quandoPagaMenos else ClassificacaoDiferenca.quandoPagaMais }
                    if (opcoes != null && diferenca > 0) {
                        PerguntaDaDiferenca(
                            if (valor < s.restante) "Você está pagando ${Dinheiro.formatar(diferenca)} a menos. O que é essa diferença?"
                            else "Você está pagando ${Dinheiro.formatar(diferenca)} a mais. O que é essa diferença?",
                            opcoes, vm.classificacao,
                        ) { vm.classificacao = it }
                    }
                    if (resultado is ResultadoPlano.Pronto) {
                        val p: PlanoDePagamento = resultado.plano
                        ResumoPagamentoCredito(p.totalPago, v.conta(vm.contaId)?.nome ?: "?", conta.nome, p.encargo, p.abatimento, p.classificacao?.rotulo, p.restanteDepois,
                            juros = com.hkawzz.nkote.dominio.VisaoCredito.jurosDoPagamento(p.totalPago - p.encargo, s.restante, s.jurosPendentes, p.abatimento))
                    }
                    vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    NkBotaoPrimario("Confirmar pagamento", vm::pagar, habilitado = resultado is ResultadoPlano.Pronto && vm.contaId != null && !vm.salvando)
                }
                vm.erro?.takeIf { s.restante <= 0 }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(Espaco.l))
            }
        }
    }
}
