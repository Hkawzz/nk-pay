package com.nkpay.app.ui.credito

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nkpay.app.AppContainer
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.AparenciaSugerida
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Fatura
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.StatusObrigacao
import com.nkpay.app.dominio.Validacao
import com.nkpay.app.dominio.VisaoCredito
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.apagar.corDoStatus
import com.nkpay.app.ui.componentes.BotaoAdicionar
import com.nkpay.app.ui.componentes.CampoDinheiro
import com.nkpay.app.ui.componentes.EscolhaSegmentada
import com.nkpay.app.ui.componentes.EstiloCard
import com.nkpay.app.ui.componentes.IconeDaConta
import com.nkpay.app.ui.componentes.NkBotaoPrimario
import com.nkpay.app.ui.componentes.NkCabecalhoSecao
import com.nkpay.app.ui.componentes.NkCampo
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.NkCartaoCredito
import com.nkpay.app.ui.componentes.NkDivisor
import com.nkpay.app.ui.componentes.NkEstadoVazio
import com.nkpay.app.ui.componentes.NkListItem
import com.nkpay.app.ui.componentes.NkTextoAjuda
import com.nkpay.app.ui.componentes.NkValor
import com.nkpay.app.ui.componentes.Opcao
import com.nkpay.app.ui.componentes.SeletorDeAparencia
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.icones.IconesNk
import com.nkpay.app.ui.nkViewModel
import com.nkpay.app.ui.theme.Espaco
import com.nkpay.app.ui.theme.RosaDespesa
import kotlinx.coroutines.launch
import java.time.LocalDate

// Cartões e crediários na interface (v0.4, F3). Regras: regras-financeiras.md, 6B.

/** Subtítulo de uma conta de crédito: dias de fechamento e vencimento. */
fun descricaoDaContaDeCredito(c: Conta): String = when (c.natureza) {
    Natureza.CARTAO -> "Fecha dia ${c.diaFechamento} · vence dia ${c.diaVencimento}"
    else -> c.diaVencimento?.let { "Crediário · vence dia $it" } ?: "Crediário"
}

/** Mais → Cartões e crediários: um cartão visual por conta, com o devido e o limite. */
@Composable
fun CartoesTela(aoVoltar: () -> Unit, aoAbrir: (String) -> Unit, aoNovo: () -> Unit) {
    val credito by LocalContainer.current.visaoCredito.collectAsState()
    Subtela(titulo = "Cartões e crediários", aoVoltar = aoVoltar, botaoFlutuante = { BotaoAdicionar("Novo cartão ou crediário", aoNovo) }) { modifier ->
        val vc = credito ?: return@Subtela
        val contas = vc.contasDeCredito
        if (contas.isEmpty()) {
            NkEstadoVazio(
                icone = IconesNk.de("cartao"),
                titulo = "Nenhum cartão ainda",
                texto = "Cadastre seus cartões de crédito e crediários (carnês, boletos parcelados). Eles nunca entram no Disponível.",
                modifier = modifier,
                acao = { NkBotaoPrimario("Cadastrar cartão ou crediário", aoNovo) },
            )
            return@Subtela
        }
        LazyColumn(modifier.fillMaxSize()) {
            for ((titulo, natureza) in listOf("Cartões" to Natureza.CARTAO, "Crediários" to Natureza.CREDIARIO)) {
                val doTipo = contas.filter { it.natureza == natureza && !it.arquivada }
                if (doTipo.isEmpty()) continue
                item(key = "t-$titulo") {
                    NkCabecalhoSecao(titulo) { NkValor(doTipo.sumOf { vc.devido(it.id) }, tamanho = com.nkpay.app.ui.componentes.TamanhoValor.PEQUENO) }
                }
                for (c in doTipo) {
                    item(key = c.id) {
                        NkCartaoCredito(c.nome, descricaoDaContaDeCredito(c), vc.devido(c.id), c.limite, aoClicar = { aoAbrir(c.id) })
                    }
                }
            }
            item { NkTextoAjuda("O devido é o que falta pagar de todas as compras. Ele nunca entra no Disponível.") }
            item { Spacer(Modifier.height(Espaco.fimDeListaComBotao)) }
        }
    }
}

class ContaCreditoFormViewModel(private val container: AppContainer, private val id: String?) : ViewModel() {
    var carregado by mutableStateOf(id == null); private set
    var nome by mutableStateOf("")
    var natureza by mutableStateOf(Natureza.CARTAO)
    var limiteTexto by mutableStateOf("0,00")
    var fechamentoTexto by mutableStateOf("")
    var vencimentoTexto by mutableStateOf("")
    var icone by mutableStateOf<String?>(null)
    var cor by mutableStateOf<Long?>(null)
    var erro by mutableStateOf<String?>(null); private set
    var salvando by mutableStateOf(false); private set
    private var original: Conta? = null
    val editando get() = id != null

    init {
        if (id != null) viewModelScope.launch {
            container.contas.buscar(id)?.let { c ->
                original = c
                nome = c.nome; natureza = c.natureza
                limiteTexto = Dinheiro.formatarSemSimbolo(c.limite ?: 0)
                fechamentoTexto = c.diaFechamento?.toString().orEmpty()
                vencimentoTexto = c.diaVencimento?.toString().orEmpty()
                icone = c.icone; cor = c.cor
            }
            carregado = true
        }
    }

    fun salvar(aoTerminar: () -> Unit) {
        val limite = Dinheiro.ler(limiteTexto)?.takeIf { it > 0 }
        val base = original ?: Conta("", "", natureza, null, 0, container.relogio.hoje())
        val conta = base.copy(
            nome = nome.trim(), natureza = natureza, tipoBeneficio = null, saldoInicial = 0,
            limite = if (natureza == Natureza.CARTAO) limite else null,
            diaFechamento = if (natureza == Natureza.CARTAO) fechamentoTexto.trim().toIntOrNull() else null,
            diaVencimento = vencimentoTexto.trim().toIntOrNull(),
            icone = icone, cor = cor,
        )
        Validacao.conta(conta)?.let { erro = it; return }
        salvando = true
        viewModelScope.launch {
            val r = if (id == null) container.contas.criar(conta) else container.contas.editar(conta)
            salvando = false
            when (r) {
                is Resultado.Sucesso -> aoTerminar()
                is Resultado.Falha -> erro = r.mensagem
            }
        }
    }
}

/** Cadastro de cartão (limite, fechamento, vencimento) ou crediário. */
@Composable
fun ContaCreditoFormTela(id: String?, aoVoltar: () -> Unit) {
    val vm = nkViewModel(chave = "credito-form-$id") { ContaCreditoFormViewModel(it, id) }
    Subtela(titulo = if (vm.editando) "Editar" else "Novo cartão ou crediário", aoVoltar = aoVoltar) { modifier ->
        if (!vm.carregado) return@Subtela
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.l, vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            if (!vm.editando) {
                EscolhaSegmentada(
                    listOf(Opcao(Natureza.CARTAO, "Cartão de crédito"), Opcao(Natureza.CREDIARIO, "Crediário")),
                    vm.natureza,
                    { vm.natureza = it },
                )
            }
            Text(
                if (vm.natureza == Natureza.CARTAO) "Compras entram na fatura pelo dia de fechamento. Pagar a fatura é uma transferência, não uma despesa."
                else "Carnê ou boleto parcelado de uma loja. Cada parcela é paga por transferência, sem despesa nova.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NkCampo("Nome", vm.nome, { vm.nome = it }, ajuda = if (vm.natureza == Natureza.CARTAO) "Ex.: Cartão do banco" else "Ex.: Loja de móveis")
            if (vm.natureza == Natureza.CARTAO) {
                CampoDinheiro("Limite (opcional)", vm.limiteTexto, { vm.limiteTexto = it }, ajuda = "Deixe 0,00 se não quiser acompanhar o limite.")
                NkCampo("Dia de fechamento", vm.fechamentoTexto, { vm.fechamentoTexto = it.filter(Char::isDigit).take(2) }, ajuda = "Compras a partir deste dia vão para a fatura seguinte.")
            }
            NkCampo(
                if (vm.natureza == Natureza.CARTAO) "Dia de vencimento" else "Dia de vencimento (opcional)",
                vm.vencimentoTexto,
                { vm.vencimentoTexto = it.filter(Char::isDigit).take(2) },
            )
            val padrao = AparenciaSugerida.daConta(vm.natureza, null)
            SeletorDeAparencia(vm.icone ?: padrao.icone, vm.cor ?: padrao.cor, { vm.icone = it }, { vm.cor = it })
            vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            NkBotaoPrimario(if (vm.editando) "Salvar alterações" else "Cadastrar", { vm.salvar(aoVoltar) }, habilitado = !vm.salvando)
            Spacer(Modifier.height(Espaco.l))
        }
    }
}

/** Uma linha de fatura (ou de mês do crediário). */
@Composable
fun LinhaFatura(f: Fatura, aoClicar: (() -> Unit)?) {
    val rotulo = when (f.status) {
        StatusObrigacao.QUITADA -> "Paga"
        StatusObrigacao.ATRASADA -> "Atrasada · venceu ${f.vencimento?.let { Formatos.data(it) }}"
        StatusObrigacao.PARCIAL -> "Em parte · vence ${f.vencimento?.let { Formatos.data(it) }}"
        StatusObrigacao.PENDENTE -> "Vence ${f.vencimento?.let { Formatos.data(it) }}"
    }
    NkListItem(
        titulo = "Fatura de " + Formatos.mes(f.mes).lowercase(),
        subtitulo = rotulo,
        corSubtitulo = corDoStatus(f.status),
        fim = { if (f.restante > 0) NkValor(f.restante) else NkValor(f.total, tipo = com.nkpay.app.ui.componentes.TipoValor.SECUNDARIO) },
        aoClicar = aoClicar,
    )
}

/**
 * Tela de um cartão ou crediário: devido, limite, faturas e compras.
 * As ações (nova compra, pagar) chegam nos PRs seguintes (F4 a F6).
 */
@Composable
fun ContaCreditoTela(
    id: String,
    aoVoltar: () -> Unit,
    aoEditar: () -> Unit,
    conteudoExtra: @Composable (VisaoCredito, Conta) -> Unit = { _, _ -> },
    aoAbrirFatura: ((java.time.YearMonth) -> Unit)? = null,
    botaoFlutuante: (@Composable () -> Unit)? = null,
) {
    val container = LocalContainer.current
    val credito by container.visaoCredito.collectAsState()
    val hoje: LocalDate = container.relogio.hoje()
    val conta = credito?.conta(id)
    Subtela(
        titulo = conta?.nome ?: "Cartão",
        aoVoltar = aoVoltar,
        acoes = { IconButton(onClick = aoEditar) { Icon(Icons.Rounded.Edit, contentDescription = "Editar") } },
        botaoFlutuante = botaoFlutuante,
    ) { modifier ->
        val vc = credito ?: return@Subtela
        if (conta == null) { LaunchedEffect(Unit) { aoVoltar() }; return@Subtela }
        val faturas = vc.faturas(id, hoje)
        LazyColumn(modifier.fillMaxSize()) {
            item { NkCartaoCredito(conta.nome, descricaoDaContaDeCredito(conta), vc.devido(id), conta.limite) }
            if (!vc.invarianteFecha(id, hoje)) {
                item {
                    NkCard(estilo = EstiloCard.ELEVADO) {
                        NkListItem(
                            "O devido não bate com as parcelas",
                            subtitulo = "Devido ${Dinheiro.formatar(vc.devido(id))} · parcelas em aberto ${Dinheiro.formatar(vc.restanteEmAberto(id, hoje))}. Confira o Registro de alterações.",
                            corTitulo = RosaDespesa,
                            linhas = 3,
                        )
                    }
                }
            }
            item { conteudoExtra(vc, conta) }
            item { NkCabecalhoSecao(if (conta.natureza == Natureza.CARTAO) "Faturas" else "Parcelas por mês") }
            item {
                NkCard {
                    if (faturas.isEmpty()) NkTextoAjuda("Nenhuma compra ainda.")
                    // Abertas primeiro (da mais antiga para a mais nova), depois as pagas (mais novas primeiro).
                    val ordenadas = faturas.filter { it.emAberto } + faturas.filter { !it.emAberto }.reversed()
                    ordenadas.forEachIndexed { i, f ->
                        if (i > 0) NkDivisor()
                        LinhaFatura(f, aoAbrirFatura?.let { abrir -> { abrir(f.mes) } })
                    }
                }
            }
            item { Spacer(Modifier.height(Espaco.fimDeListaComBotao)) }
        }
    }
}

/** Ícone + nome de uma conta de crédito, para listas de escolha. */
@Composable
fun IconeContaCredito(c: Conta) = IconeDaConta(c)
