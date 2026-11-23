package com.nkpay.app.ui.credito

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nkpay.app.AppContainer
import com.nkpay.app.dados.NovaCompra
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.AparenciaSugerida
import com.nkpay.app.dominio.Compra
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.DatasDeCredito
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.VisaoCredito
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.apagar.corDoStatus
import com.nkpay.app.ui.componentes.CampoData
import com.nkpay.app.ui.componentes.CampoDinheiro
import com.nkpay.app.ui.componentes.CampoEscolha
import com.nkpay.app.ui.componentes.DialogoConfirmacao
import com.nkpay.app.ui.componentes.EstiloCard
import com.nkpay.app.ui.componentes.IconeDaCategoria
import com.nkpay.app.ui.componentes.NkBotaoPrimario
import com.nkpay.app.ui.componentes.NkBotaoSecundario
import com.nkpay.app.ui.componentes.NkCabecalhoSecao
import com.nkpay.app.ui.componentes.NkCampo
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.NkDivisor
import com.nkpay.app.ui.componentes.NkListItem
import com.nkpay.app.ui.componentes.NkTextoAjuda
import com.nkpay.app.ui.componentes.NkValor
import com.nkpay.app.ui.componentes.Opcao
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.componentes.TipoValor
import com.nkpay.app.ui.icones.IconesNk
import com.nkpay.app.ui.nkViewModel
import com.nkpay.app.ui.theme.Espaco
import com.nkpay.app.ui.theme.RosaDespesa
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

// Compras parceladas e compras anteriores ao app (v0.4, F4). Regras 6B.2, 6B.3 e 6B.6.

class CompraFormViewModel(private val container: AppContainer, private val contaId: String) : ViewModel() {
    var descricao by mutableStateOf("")
    var categoriaId by mutableStateOf<String?>(null)
    var valorTexto by mutableStateOf("0,00")
    var data by mutableStateOf(container.relogio.hoje())
    var parcelasTexto by mutableStateOf("1")
    var primeiroVencimento by mutableStateOf<LocalDate?>(null)
    /** A pessoa mudou a data da 1ª parcela à mão: o app para de recalcular. */
    var vencimentoEditado by mutableStateOf(false)
    var anterior by mutableStateOf(false)
    var jaPagasTexto by mutableStateOf("0")
    var parcialTexto by mutableStateOf("0,00")
    var avisos by mutableStateOf<List<String>>(emptyList()); private set
    var erro by mutableStateOf<String?>(null); private set
    var salvando by mutableStateOf(false); private set

    val parcelas: Int get() = parcelasTexto.toIntOrNull() ?: 0

    fun recalcularVencimento(conta: Conta) {
        if (vencimentoEditado) return
        primeiroVencimento = when (conta.natureza) {
            Natureza.CARTAO -> DatasDeCredito.primeiroVencimentoCartao(data, conta.diaFechamento ?: 1, conta.diaVencimento ?: 10)
            else -> DatasDeCredito.primeiroVencimentoCrediario(data, conta.diaVencimento)
        }
    }

    fun verificarDuplicadas() {
        viewModelScope.launch {
            avisos = container.compras.possiveisDuplicadas(descricao, Dinheiro.ler(valorTexto) ?: 0, parcelas)
        }
    }

    fun salvar(aoTerminar: () -> Unit) {
        val valor = Dinheiro.ler(valorTexto) ?: run { erro = "Valor inválido."; return }
        salvando = true
        viewModelScope.launch {
            val r = container.compras.registrar(
                NovaCompra(
                    contaCreditoId = contaId, descricao = descricao, categoriaId = categoriaId, dataCompra = data,
                    valorTotal = valor, parcelas = parcelas, primeiroVencimento = primeiroVencimento,
                    anteriorAoApp = anterior,
                    parcelasJaPagas = if (anterior) jaPagasTexto.toIntOrNull() ?: 0 else 0,
                    pagoEmParteDaSeguinte = if (anterior) Dinheiro.ler(parcialTexto) ?: 0 else 0,
                ),
            )
            salvando = false
            when (r) {
                is Resultado.Sucesso -> aoTerminar()
                is Resultado.Falha -> erro = r.mensagem
            }
        }
    }
}

/** Nova compra no cartão ou crediário (à vista = 1 parcela), ou compra anterior ao app. */
@Composable
fun CompraFormTela(contaId: String, aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val vm = nkViewModel(chave = "compra-form-$contaId") { CompraFormViewModel(it, contaId) }
    val visao by container.visao.collectAsState()
    val conta = visao?.conta(contaId)

    LaunchedEffect(conta, vm.data) { conta?.let { vm.recalcularVencimento(it) } }
    LaunchedEffect(vm.anterior, vm.descricao, vm.valorTexto, vm.parcelasTexto) { if (vm.anterior) vm.verificarDuplicadas() }

    Subtela(titulo = if (vm.anterior) "Compra anterior ao app" else "Nova compra", aoVoltar = aoVoltar) { modifier ->
        val v = visao ?: return@Subtela
        if (conta == null) return@Subtela
        val valor = Dinheiro.ler(vm.valorTexto) ?: 0
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.l, vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            Text("No ${conta.nome}. A compra inteira entra como despesa na data dela; pagar as parcelas é transferência.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            NkCampo("Descrição", vm.descricao, { vm.descricao = it }, ajuda = "Ex.: Geladeira, Mercado")
            CampoEscolha(
                "Categoria",
                v.categorias.filter { !it.arquivada || it.id == vm.categoriaId }.map {
                    val a = AparenciaSugerida.NEUTRA
                    Opcao(it.id, it.nome, icone = IconesNk.de(it.icone ?: a.icone), cor = it.cor ?: a.cor)
                },
                vm.categoriaId,
                { vm.categoriaId = it },
                textoVazio = "Sem categoria",
            )
            CampoDinheiro("Valor total", vm.valorTexto, { vm.valorTexto = it })
            CampoData("Data da compra", vm.data, { vm.data = it })
            NkCampo("Parcelas", vm.parcelasTexto, { vm.parcelasTexto = it.filter(Char::isDigit).take(3) }, ajuda = "1 = à vista.")
            vm.primeiroVencimento?.let { venc ->
                CampoData("Vencimento da 1ª parcela", venc, { vm.primeiroVencimento = it; vm.vencimentoEditado = true })
            }
            if (vm.parcelas in 1..120 && valor >= vm.parcelas) {
                val valores = DatasDeCredito.valoresDasParcelas(valor, vm.parcelas)
                Text(
                    if (vm.parcelas == 1) "1 parcela de ${Dinheiro.formatar(valor)}."
                    else "${vm.parcelas}x de ${Dinheiro.formatar(valores.first())}" +
                        (if (valores.last() != valores.first()) " (a última de ${Dinheiro.formatar(valores.last())})" else "") + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Compra feita antes de usar o app", style = MaterialTheme.typography.bodyLarge)
                    Text("Para dívidas que você já tinha. Não mexe no Pix, no dinheiro nem nos benefícios.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.anterior, onCheckedChange = { vm.anterior = it })
            }
            if (vm.anterior) {
                NkCampo("Parcelas já pagas antes do app", vm.jaPagasTexto, { vm.jaPagasTexto = it.filter(Char::isDigit).take(3) }, ajuda = "As primeiras parcelas que você já pagou.")
                CampoDinheiro("Já pago da parcela seguinte (opcional)", vm.parcialTexto, { vm.parcialTexto = it }, ajuda = "Se uma parcela foi paga só em parte antes do app.")
                if (vm.avisos.isNotEmpty()) {
                    NkCard(estilo = EstiloCard.ELEVADO) {
                        Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xxs)) {
                            Text("Isto pode já estar no app", style = MaterialTheme.typography.titleSmall, color = RosaDespesa)
                            for (a in vm.avisos) Text("• $a", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Se for a mesma compra, a despesa seria contada duas vezes. O app não converte nada sozinho. Roteiro: 1) desfaça o lançamento antigo (A pagar → desfazer o pagamento, ou exclua a despesa em Movimentações); 2) cadastre aqui a compra; 3) pague as próximas parcelas pela tela do cartão.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            NkBotaoPrimario("Registrar compra", { vm.salvar(aoVoltar) }, habilitado = !vm.salvando)
            Spacer(Modifier.height(Espaco.l))
        }
    }
}


/** Seção "Compras" da tela do cartão. */
@Composable
fun SecaoCompras(vc: VisaoCredito, conta: Conta, aoAbrir: (String) -> Unit) {
    val compras = vc.comprasDa(conta.id)
    if (compras.isEmpty()) return
    NkCabecalhoSecao("Compras")
    NkCard {
        compras.take(20).forEachIndexed { i, c ->
            if (i > 0) NkDivisor()
            LinhaCompra(c, vc) { aoAbrir(c.id) }
        }
    }
}

@Composable
private fun LinhaCompra(c: Compra, vc: VisaoCredito, aoClicar: () -> Unit) {
    val v = LocalContainer.current.visao.collectAsState().value
    NkListItem(
        titulo = c.descricao,
        subtitulo = "${c.totalParcelas}x · ${Formatos.data(c.dataCompra)}" + if (c.anteriorAoApp) " · anterior ao app" else "",
        inicio = { IconeDaCategoria(v?.categoria(c.categoriaId)) },
        fim = { NkValor(c.valorTotal, tipo = TipoValor.DESPESA) },
        aoClicar = aoClicar,
    )
}

/** Uma compra: as parcelas com o status de cada uma, e excluir (enquanto nada foi pago). */
@Composable
fun CompraTela(id: String, aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val credito by container.visaoCredito.collectAsState()
    val hoje = container.relogio.hoje()
    val escopo = androidx.compose.runtime.rememberCoroutineScope()
    var confirmar by rememberSaveable { mutableStateOf(false) }
    var erro by rememberSaveable { mutableStateOf<String?>(null) }
    val compra = credito?.compra(id)
    Subtela(titulo = compra?.descricao ?: "Compra", aoVoltar = aoVoltar) { modifier ->
        val vc = credito ?: return@Subtela
        if (compra == null) { LaunchedEffect(Unit) { aoVoltar() }; return@Subtela }
        LazyColumn(modifier.fillMaxSize()) {
            item {
                NkCard {
                    Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xxs)) {
                        NkValor(compra.valorTotal, tamanho = com.nkpay.app.ui.componentes.TamanhoValor.MEDIO)
                        Text("${compra.totalParcelas}x · comprado em ${Formatos.data(compra.dataCompra)} · ${vc.conta(compra.contaCreditoId)?.nome.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (compra.anteriorAoApp) Text("Compra anterior ao app: a despesa está no mês original.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { NkCabecalhoSecao("Parcelas") }
            item {
                NkCard {
                    vc.parcelasDaCompra(id).forEachIndexed { i, o ->
                        if (i > 0) NkDivisor()
                        val s = vc.situacaoDaParcela(o.id, hoje)
                        val quitadaAntes = s?.situacao?.abatimentos?.let { it > 0 } == true && s.situacao.pago == 0L
                        NkListItem(
                            titulo = "Parcela ${o.numeroParcela}/${o.totalParcelas}",
                            subtitulo = (o.vencimento?.let { "Vence ${Formatos.data(it)}" } ?: "") + " · " +
                                (if (s?.status == com.nkpay.app.dominio.StatusObrigacao.QUITADA && quitadaAntes) "Paga antes do app" else s?.status?.rotulo.orEmpty()),
                            corSubtitulo = s?.let { corDoStatus(it.status) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            fim = { NkValor(o.valorPrevisto) },
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(Espaco.l), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                    erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    NkBotaoSecundario("Excluir compra", { confirmar = true })
                    NkTextoAjuda("Para corrigir valor ou parcelas, exclua e cadastre de novo. Só é possível enquanto nenhuma parcela foi paga.", Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (confirmar) {
        DialogoConfirmacao(
            titulo = "Excluir compra?",
            texto = "A despesa, as parcelas e o que foi registrado como pago antes do app somem dos cálculos. Tudo fica no Registro de alterações.",
            textoConfirmar = "Excluir",
            destrutivo = true,
            aoConfirmar = {
                confirmar = false
                escopo.launch {
                    when (val r = container.compras.excluir(id)) {
                        is Resultado.Sucesso -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { aoVoltar() }
                        is Resultado.Falha -> erro = r.mensagem
                    }
                }
            },
            aoCancelar = { confirmar = false },
        )
    }
}
