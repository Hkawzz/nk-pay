package com.hkawzz.nkote.ui.emprestimos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.ClassificacaoDiferenca
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Emprestimos
import com.hkawzz.nkote.dominio.Pagamentos
import com.hkawzz.nkote.dominio.ResultadoPlano
import com.hkawzz.nkote.dominio.TipoEmprestimo
import com.hkawzz.nkote.ui.Formatos
import com.hkawzz.nkote.ui.LocalContainer
import com.hkawzz.nkote.ui.ajuda.ChaveAjuda
import com.hkawzz.nkote.ui.ajuda.NkAjuda
import com.hkawzz.nkote.ui.apagar.corDoStatus
import com.hkawzz.nkote.ui.componentes.CampoData
import com.hkawzz.nkote.ui.componentes.CampoDinheiro
import com.hkawzz.nkote.ui.componentes.CampoEscolha
import com.hkawzz.nkote.ui.componentes.EstiloCard
import com.hkawzz.nkote.ui.componentes.IconeDaConta
import com.hkawzz.nkote.ui.componentes.NkBotaoPrimario
import com.hkawzz.nkote.ui.componentes.NkBotaoSecundario
import com.hkawzz.nkote.ui.componentes.NkCabecalhoSecao
import com.hkawzz.nkote.ui.componentes.NkCard
import com.hkawzz.nkote.ui.componentes.NkDivisor
import com.hkawzz.nkote.ui.componentes.NkListItem
import com.hkawzz.nkote.ui.componentes.NkValorRotulado
import com.hkawzz.nkote.ui.credito.PerguntaDaDiferenca
import com.hkawzz.nkote.ui.credito.contaPadraoParaPagar
import com.hkawzz.nkote.ui.credito.opcoesPagarCom
import com.hkawzz.nkote.ui.nkViewModel
import com.hkawzz.nkote.ui.theme.Espaco
import com.hkawzz.nkote.ui.theme.RosaDespesa
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Pagar (recebido) ou receber (concedido) uma parcela de empréstimo (v0.5, PR 8). Regras 6E.

class ParcelaEmprestimoViewModel(private val container: AppContainer, val obrigacaoId: String) : ViewModel() {
    var carregado by mutableStateOf(false); private set
    var contaId by mutableStateOf<String?>(null)
    var valorTexto by mutableStateOf("0,00")
    var data by mutableStateOf(container.relogio.hoje())
    var classificacao by mutableStateOf<ClassificacaoDiferenca?>(null)
    var novoValorTexto by mutableStateOf("")
    var novaData by mutableStateOf<java.time.LocalDate?>(null)
    var futuras by mutableStateOf(false)
    var erro by mutableStateOf<String?>(null)
    var mensagem by mutableStateOf<String?>(null)
    var salvando by mutableStateOf(false); private set

    init {
        viewModelScope.launch {
            val v = container.visaoEmprestimos.filterNotNull().first()
            val o = v.obrigacoes.firstOrNull { it.id == obrigacaoId }
            val s = o?.emprestimoId?.let { v.situacao(it, container.relogio.hoje()) }
            s?.parcelas?.firstOrNull { it.obrigacao.id == obrigacaoId }?.let { valorTexto = Dinheiro.formatarSemSimbolo(it.restante) }
            contaId = s?.emprestimo?.contaPropriaId ?: contaPadraoParaPagar(container.visao.filterNotNull().first())
            carregado = true
        }
    }

    fun mudarValor(t: String) { valorTexto = t; classificacao = null }

    fun confirmar(tipo: TipoEmprestimo) {
        val valor = Dinheiro.ler(valorTexto) ?: run { erro = "Valor inválido."; return }
        val conta = contaId ?: run { erro = "Escolha a conta."; return }
        salvando = true
        viewModelScope.launch {
            val r = if (tipo == TipoEmprestimo.RECEBIDO) container.pagamentosCredito.pagarParcela(obrigacaoId, conta, data, valor, classificacao)
            else container.pagamentosCredito.receberParcela(obrigacaoId, conta, data, valor, classificacao)
            salvando = false
            when (r) {
                is Resultado.Sucesso -> { erro = null; classificacao = null; mensagem = if (tipo == TipoEmprestimo.RECEBIDO) "Pagamento registrado." else "Recebimento registrado." }
                is Resultado.Falha -> erro = r.mensagem
            }
        }
    }

    fun desfazer(movId: String) {
        viewModelScope.launch {
            when (val r = container.pagamentosCredito.desfazer(movId)) {
                is Resultado.Sucesso -> mensagem = "Desfeito."
                is Resultado.Falha -> erro = r.mensagem
            }
        }
    }

    fun editar() {
        val valor = novoValorTexto.takeIf { it.isNotBlank() }?.let { Dinheiro.ler(it) ?: run { erro = "Valor inválido."; return } }?.takeIf { it > 0 }
        if (valor == null && novaData == null) { erro = "Informe o novo valor ou a nova data."; return }
        viewModelScope.launch {
            when (val r = container.pagamentosCredito.editarParcelas(obrigacaoId, valor, novaData, futuras)) {
                is Resultado.Sucesso -> { erro = null; novoValorTexto = ""; novaData = null; mensagem = if (r.valor == 1) "Parcela alterada." else "${r.valor} parcelas alteradas." }
                is Resultado.Falha -> erro = r.mensagem
            }
        }
    }
}

@Composable
fun ParcelaEmprestimoTela(obrigacaoId: String, aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val vm = nkViewModel(chave = "parcela-emprestimo-$obrigacaoId") { ParcelaEmprestimoViewModel(it, obrigacaoId) }
    val visao by container.visao.collectAsState()
    val emprestimos by container.visaoEmprestimos.collectAsState()
    val hoje = container.relogio.hoje()

    Subtela2(titulo = "Parcela", aoVoltar = aoVoltar) { modifier ->
        val v = visao ?: return@Subtela2
        val ve = emprestimos ?: return@Subtela2
        val o = ve.obrigacoes.firstOrNull { it.id == obrigacaoId } ?: return@Subtela2
        val s = ve.situacao(o.emprestimoId!!, hoje) ?: return@Subtela2
        val p = s.parcelas.firstOrNull { it.obrigacao.id == obrigacaoId } ?: return@Subtela2
        val recebido = s.tipo == TipoEmprestimo.RECEBIDO
        val pessoa = ve.conta(s.emprestimo.contaId)
        if (!vm.carregado) return@Subtela2
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Espaco.xs), verticalArrangement = Arrangement.spacedBy(Espaco.s)) {
            NkCard {
                Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xxs)) {
                    Text(o.descricao, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${pessoa?.nome.orEmpty()} · ${rotuloDaParcela(p)}", style = MaterialTheme.typography.bodySmall, color = if (p.atrasada) RosaDespesa else corDoStatus(p.situacao.status))
                        if (p.atrasada) NkAjuda(ChaveAjuda.EMPRESTIMO_ATRASADA)
                    }
                    Row {
                        NkValorRotulado(if (recebido) "Falta pagar" else "Falta receber", p.restante, Modifier.weight(1f), corValor = if (p.atrasada) RosaDespesa else null)
                        NkValorRotulado("Valor da parcela", o.valorPrevisto, Modifier.weight(1f))
                    }
                }
            }

            val pags = ve.pagamentos.filter { it.obrigacaoId == obrigacaoId }
            if (pags.isNotEmpty()) {
                NkCabecalhoSecao(if (recebido) "Pagamentos" else "Recebimentos")
                NkCard {
                    pags.forEachIndexed { i, pg ->
                        if (i > 0) NkDivisor()
                        val m = v.movimentacoes.firstOrNull { it.id == pg.movimentacaoId }
                        val conta = v.conta(if (recebido) m?.contaId else (m?.contaDestinoId ?: m?.contaId))
                        NkListItem(
                            titulo = Dinheiro.formatar(pg.valor),
                            subtitulo = listOfNotNull(m?.data?.let { Formatos.data(it) }, conta?.nome).joinToString(" · "),
                            inicio = { conta?.let { IconeDaConta(it) } },
                            fim = { TextButton(onClick = { vm.desfazer(pg.movimentacaoId) }) { Text("Desfazer") } },
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = Espaco.l), verticalArrangement = Arrangement.spacedBy(Espaco.s)) {
                if (p.restante > 0) {
                    Text(if (recebido) "Pagar" else "Receber", style = MaterialTheme.typography.titleMedium)
                    CampoEscolha(if (recebido) "Pagar com" else "Receber em", opcoesPagarCom(v, vm.contaId), vm.contaId, { vm.contaId = it })
                    CampoDinheiro(if (recebido) "Valor pago" else "Valor recebido", vm.valorTexto, vm::mudarValor)
                    CampoData("Data", vm.data, { vm.data = it })
                    val valor = Dinheiro.ler(vm.valorTexto) ?: 0
                    val resultado = Pagamentos.planejar(p.restante, valor, vm.classificacao)
                    val diferenca = if (valor < p.restante) p.restante - valor else valor - p.restante
                    val menos = valor < p.restante
                    val opcoes = when {
                        diferenca == 0L -> null
                        recebido -> if (menos) ClassificacaoDiferenca.quandoPagaMenos else ClassificacaoDiferenca.quandoPagaMais
                        menos -> listOf(ClassificacaoDiferenca.PARCIAL, ClassificacaoDiferenca.DESCONTO)
                        else -> listOf(ClassificacaoDiferenca.JUROS, ClassificacaoDiferenca.MULTA, ClassificacaoDiferenca.OUTRO_ACRESCIMO)
                    }
                    if (opcoes != null && valor > 0) {
                        PerguntaDaDiferenca(
                            (if (recebido) "Você está pagando " else "Você está recebendo ") + Dinheiro.formatar(diferenca) + (if (menos) " a menos" else " a mais") + ". O que é essa diferença?",
                            opcoes, vm.classificacao,
                        ) { vm.classificacao = it }
                    }
                    if (resultado is ResultadoPlano.Pronto) {
                        val plano = resultado.plano
                        NkCard(estilo = EstiloCard.ELEVADO) {
                            Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xxs)) {
                                Text("Vai ser registrado:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (recebido) {
                                    Text("• Transferência de ${Dinheiro.formatar(plano.totalPago)} para ${pessoa?.nome} (não é despesa)", style = MaterialTheme.typography.bodyMedium)
                                    if (plano.encargo > 0) Text("• Despesa de ${Dinheiro.formatar(plano.encargo)} em Encargos financeiros", style = MaterialTheme.typography.bodyMedium)
                                    if (plano.abatimento > 0) Text("• Desconto de ${Dinheiro.formatar(plano.abatimento)}: reduz o que você deve (não é receita)", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    val juros = Emprestimos.jurosDoRecebimento(plano.despesaPrincipal, s.restante, s.jurosNaoReconhecidos)
                                    Text("• Devolução de ${Dinheiro.formatar(plano.despesaPrincipal - juros)} (transferência, não é receita)", style = MaterialTheme.typography.bodyMedium)
                                    if (juros > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("• Juros recebidos de ${Dinheiro.formatar(juros)} (receita)", style = MaterialTheme.typography.bodyMedium)
                                        NkAjuda(ChaveAjuda.EMPRESTIMO_JUROS_RECEBIDOS)
                                    }
                                    if (plano.encargo > 0) Text("• Mais ${Dinheiro.formatar(plano.encargo)} em Juros recebidos (${plano.classificacao?.rotulo?.lowercase()})", style = MaterialTheme.typography.bodyMedium)
                                    if (plano.abatimento > 0) Text("• Desconto concedido de ${Dinheiro.formatar(plano.abatimento)} (não é despesa)", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(if (plano.restanteDepois > 0) "Depois disso, ainda faltam ${Dinheiro.formatar(plano.restanteDepois)}." else "A parcela fica quitada.", style = MaterialTheme.typography.bodyMedium, color = if (plano.restanteDepois > 0) RosaDespesa else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    NkBotaoPrimario(if (recebido) "Confirmar pagamento" else "Confirmar recebimento", { vm.confirmar(s.tipo) }, habilitado = resultado is ResultadoPlano.Pronto && vm.contaId != null && !vm.salvando)
                }

                // Edição: só sem pagamento (6E.8).
                if (pags.isEmpty()) {
                    Spacer(Modifier.height(Espaco.xs))
                    Text("Alterar esta parcela", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (recebido) "Se o valor mudar, a diferença de juros é lançada hoje (despesa se aumentar, redução se diminuir)."
                        else "Se o valor mudar, só os juros que ainda vão virar receita mudam.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CampoDinheiro("Novo valor (opcional)", vm.novoValorTexto.ifBlank { "0,00" }, { vm.novoValorTexto = it })
                    CampoData("Novo vencimento", vm.novaData ?: o.vencimento ?: hoje, { vm.novaData = it })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(vm.futuras, { vm.futuras = it })
                        Text("Aplicar também às parcelas seguintes (sem pagamento)", style = MaterialTheme.typography.bodyMedium)
                    }
                    NkBotaoSecundario("Salvar alteração", vm::editar)
                }
                vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                vm.mensagem?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(Espaco.l))
            }
        }
    }
}

/** Atalho para a Subtela padrão (mantém os imports deste arquivo curtos). */
@Composable
private fun Subtela2(titulo: String, aoVoltar: () -> Unit, conteudo: @Composable (Modifier) -> Unit) =
    com.hkawzz.nkote.ui.componentes.Subtela(titulo = titulo, aoVoltar = aoVoltar, conteudo = conteudo)
