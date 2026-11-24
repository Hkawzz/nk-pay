package com.hkawzz.nkote.ui.apagar

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
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Pagamentos
import com.hkawzz.nkote.dominio.ResultadoPlano
import com.hkawzz.nkote.ui.LocalContainer
import com.hkawzz.nkote.ui.componentes.CampoData
import com.hkawzz.nkote.ui.componentes.CampoDinheiro
import com.hkawzz.nkote.ui.componentes.CampoEscolha
import com.hkawzz.nkote.ui.componentes.EstiloCard
import com.hkawzz.nkote.ui.componentes.NkBotaoPrimario
import com.hkawzz.nkote.ui.componentes.NkCard
import com.hkawzz.nkote.ui.componentes.NkValorRotulado
import com.hkawzz.nkote.ui.componentes.Opcao
import com.hkawzz.nkote.ui.componentes.Subtela
import com.hkawzz.nkote.ui.nkViewModel
import com.hkawzz.nkote.ui.theme.Espaco
import com.hkawzz.nkote.ui.theme.RosaDespesa
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PagarViewModel(private val container: AppContainer, val obrigacaoId: String) : ViewModel() {
    var carregado by mutableStateOf(false); private set
    var contaId by mutableStateOf<String?>(null)
    var valorTexto by mutableStateOf("0,00")
    var data by mutableStateOf(container.relogio.hoje())
    var classificacao by mutableStateOf<ClassificacaoDiferenca?>(null)
    var erro by mutableStateOf<String?>(null); private set
    var salvando by mutableStateOf(false); private set

    init {
        viewModelScope.launch {
            val aPagar = container.visaoAPagar.filterNotNull().first()
            val visao = container.visao.filterNotNull().first()
            val o = aPagar.obrigacao(obrigacaoId)
            if (o != null) {
                // Sugere o valor que falta e a conta de costume (se for conta fixa).
                valorTexto = Dinheiro.formatarSemSimbolo(aPagar.situacao(o, container.relogio.hoje()).restante.coerceAtLeast(0))
                contaId = aPagar.contaFixa(o.contaFixaId)?.contaPadraoId?.takeIf { visao.conta(it)?.arquivada == false }
                    ?: visao.contas.firstOrNull { !it.arquivada && it.natureza == Natureza.PROPRIO }?.id
            }
            carregado = true
        }
    }

    fun mudarValor(texto: String) {
        valorTexto = texto
        classificacao = null // valor novo, pergunta de novo
    }

    fun confirmar(aoTerminar: () -> Unit) {
        val valor = Dinheiro.ler(valorTexto) ?: run { erro = "Valor inválido."; return }
        val conta = contaId ?: run { erro = "Escolha a conta de onde sai o dinheiro."; return }
        salvando = true
        viewModelScope.launch {
            when (val r = container.pagamentos.pagar(obrigacaoId, conta, data, valor, classificacao)) {
                is Resultado.Sucesso -> aoTerminar()
                is Resultado.Falha -> erro = r.mensagem
            }
            salvando = false
        }
    }
}

/**
 * Pagar uma conta. Mostra, ANTES de confirmar, exatamente o que vai ser gravado.
 * Se o valor for diferente do que falta, pergunta o que é a diferença (ADR 0013).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PagarTela(obrigacaoId: String, aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val vm = nkViewModel(chave = "pagar-$obrigacaoId") { PagarViewModel(it, obrigacaoId) }
    val visao by container.visao.collectAsState()
    val aPagar by container.visaoAPagar.collectAsState()

    Subtela(titulo = "Pagar", aoVoltar = aoVoltar) { modifier ->
        val v = visao ?: return@Subtela
        val ap = aPagar ?: return@Subtela
        val o = ap.obrigacao(obrigacaoId) ?: return@Subtela
        if (!vm.carregado) return@Subtela
        val s = ap.situacao(o, container.relogio.hoje())
        val valor = Dinheiro.ler(vm.valorTexto) ?: 0
        val resultado = Pagamentos.planejar(s.restante, valor, vm.classificacao)

        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.l, vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            NkCard {
                Column(Modifier.padding(Espaco.m)) {
                    Text(o.descricao, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Espaco.xs))
                    Row {
                        NkValorRotulado("Falta pagar", s.restante, Modifier.weight(1f), corValor = corDoStatus(s.status))
                        NkValorRotulado("Já pago", s.pago, Modifier.weight(1f))
                    }
                }
            }

            CampoEscolha(
                "Pagar com",
                // Contas de crédito não pagam contas (ADR 0017).
                v.contas.filter { it.ehMeioDePagamento && (!it.arquivada || it.id == vm.contaId) }.map { c ->
                    Opcao(c.id, c.nome, (if (c.natureza == Natureza.BENEFICIO) "Benefício · " else "") + "Saldo " + Dinheiro.formatar(v.saldo(c.id)))
                },
                vm.contaId,
                { vm.contaId = it },
            )
            CampoDinheiro("Valor pago", vm.valorTexto, vm::mudarValor)
            CampoData("Data do pagamento", vm.data, { vm.data = it })

            // A pergunta sobre a diferença (nunca é respondida sozinha).
            val opcoes = (resultado as? ResultadoPlano.PrecisaClassificar)?.opcoes
                ?: vm.classificacao?.let { c -> if (c.pagouMenos) ClassificacaoDiferenca.quandoPagaMenos else ClassificacaoDiferenca.quandoPagaMais }
            val diferenca = if (valor < s.restante) s.restante - valor else valor - s.restante
            if (opcoes != null && diferenca > 0) {
                Text(
                    if (valor < s.restante) "Você está pagando ${Dinheiro.formatar(diferenca)} a menos. O que é essa diferença?"
                    else "Você está pagando ${Dinheiro.formatar(diferenca)} a mais. O que é essa diferença?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                    for (c in opcoes) {
                        FilterChip(
                            selected = vm.classificacao == c,
                            onClick = { vm.classificacao = c },
                            label = { Text(c.rotulo) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            // O que vai ser gravado.
            when (resultado) {
                is ResultadoPlano.Pronto -> {
                    val p = resultado.plano
                    val categoria = v.categoria(o.categoriaId)?.nome ?: "sem categoria"
                    NkCard(estilo = EstiloCard.ELEVADO) {
                        Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xxs)) {
                            Text("Vai ser registrado:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• Despesa de ${Dinheiro.formatar(p.despesaPrincipal)} ($categoria)", style = MaterialTheme.typography.bodyMedium)
                            if (p.encargo > 0) Text("• Despesa de ${Dinheiro.formatar(p.encargo)} de ${p.classificacao?.rotulo?.lowercase()} (Encargos financeiros)", style = MaterialTheme.typography.bodyMedium)
                            if (p.abatimento > 0) Text("• ${p.classificacao?.rotulo} de ${Dinheiro.formatar(p.abatimento)} (não é receita)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (p.restanteDepois > 0) "Depois disso, ainda faltam ${Dinheiro.formatar(p.restanteDepois)}." else "A conta fica paga.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (p.restanteDepois > 0) RosaDespesa else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                is ResultadoPlano.Invalido -> Text(resultado.mensagem, color = MaterialTheme.colorScheme.error)
                is ResultadoPlano.PrecisaClassificar -> Text(
                    "Escolha uma opção acima para continuar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(Espaco.xxs))
            NkBotaoPrimario(
                "Confirmar pagamento",
                { vm.confirmar(aoVoltar) },
                habilitado = resultado is ResultadoPlano.Pronto && vm.contaId != null && !vm.salvando,
            )
            Spacer(Modifier.height(Espaco.l))
        }
    }
}
