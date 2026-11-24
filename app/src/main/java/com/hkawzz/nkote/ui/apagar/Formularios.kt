package com.hkawzz.nkote.ui.apagar

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.ContaFixa
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Obrigacao
import com.hkawzz.nkote.dominio.OrigemObrigacao
import com.hkawzz.nkote.ui.Formatos
import com.hkawzz.nkote.ui.LocalContainer
import com.hkawzz.nkote.ui.componentes.BotaoAdicionar
import com.hkawzz.nkote.ui.componentes.CampoData
import com.hkawzz.nkote.ui.componentes.CampoDinheiro
import com.hkawzz.nkote.ui.componentes.CampoEscolha
import com.hkawzz.nkote.ui.componentes.DialogoConfirmacao
import com.hkawzz.nkote.ui.componentes.NkBotaoPrimario
import com.hkawzz.nkote.ui.componentes.NkCabecalhoSecao
import com.hkawzz.nkote.ui.componentes.NkCampo
import com.hkawzz.nkote.ui.componentes.NkCard
import com.hkawzz.nkote.ui.componentes.NkDivisor
import com.hkawzz.nkote.ui.componentes.NkEstadoVazio
import com.hkawzz.nkote.ui.componentes.NkListItem
import com.hkawzz.nkote.ui.componentes.NkTextoAjuda
import com.hkawzz.nkote.ui.componentes.NkValor
import com.hkawzz.nkote.ui.componentes.NkValorRotulado
import com.hkawzz.nkote.ui.componentes.Opcao
import com.hkawzz.nkote.ui.componentes.Subtela
import com.hkawzz.nkote.ui.nkViewModel
import com.hkawzz.nkote.ui.theme.Espaco
import kotlinx.coroutines.launch
import java.time.YearMonth

// ===================================================================== Conta a pagar

/** Criar uma conta avulsa ou editar qualquer conta a pagar (id != null). */
class ObrigacaoFormViewModel(private val container: AppContainer, private val id: String?, mesInicial: YearMonth) : ViewModel() {
    var carregado by mutableStateOf(id == null); private set
    var origem by mutableStateOf(OrigemObrigacao.AVULSA); private set
    var descricao by mutableStateOf("")
    var valorTexto by mutableStateOf("0,00")
    var temVencimento by mutableStateOf(true)
    var vencimento by mutableStateOf(container.relogio.hoje())
    var mes by mutableStateOf(mesInicial)
    var categoriaId by mutableStateOf<String?>(null)
    var erro by mutableStateOf<String?>(null); private set
    val editando get() = id != null

    init {
        if (id != null) {
            viewModelScope.launch {
                container.obrigacoes.buscar(id)?.let { o ->
                    origem = o.origem
                    descricao = o.descricao
                    valorTexto = Dinheiro.formatarSemSimbolo(o.valorPrevisto)
                    temVencimento = o.vencimento != null
                    o.vencimento?.let { vencimento = it }
                    o.mesReferencia?.let { mes = it }
                    categoriaId = o.categoriaId
                }
                carregado = true
            }
        }
    }

    fun salvar(aoTerminar: () -> Unit) {
        val valor = Dinheiro.ler(valorTexto) ?: run { erro = "Valor inválido."; return }
        val o = Obrigacao(
            id = id ?: "", origem = origem, descricao = descricao, valorPrevisto = valor,
            vencimento = if (temVencimento) vencimento else null, mesReferencia = mes, categoriaId = categoriaId,
        )
        viewModelScope.launch {
            val r = if (id == null) container.obrigacoes.criarAvulsa(o) else container.obrigacoes.editar(o)
            when (r) {
                is Resultado.Sucesso -> aoTerminar()
                is Resultado.Falha -> erro = r.mensagem
            }
        }
    }

    fun excluir(aoTerminar: () -> Unit) {
        val alvo = id ?: return
        viewModelScope.launch {
            when (val r = container.obrigacoes.excluir(alvo)) {
                is Resultado.Sucesso -> aoTerminar()
                is Resultado.Falha -> erro = r.mensagem
            }
        }
    }

    /** Desfaz um pagamento inteiro (despesa, encargo e desconto ligados a ele). */
    fun excluirPagamento(pagamentoId: String) {
        viewModelScope.launch {
            (container.pagamentos.excluir(pagamentoId) as? Resultado.Falha)?.let { erro = it.mensagem }
        }
    }
}

/** Seletor de mês compacto (‹ Dezembro de 2026 ›). */
@Composable
fun SeletorDeMes(rotulo: String, mes: YearMonth, aoMudar: (YearMonth) -> Unit) {
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { aoMudar(mes.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Mês anterior") }
            Text(Formatos.mes(mes), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { aoMudar(mes.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Próximo mês") }
        }
    }
}

@Composable
fun ObrigacaoFormTela(id: String?, mesInicial: YearMonth, aoVoltar: () -> Unit, aoPagar: (String) -> Unit = {}) {
    val container = LocalContainer.current
    val vm = nkViewModel(chave = "obrig-$id-$mesInicial") { ObrigacaoFormViewModel(it, id, mesInicial) }
    val visao by container.visao.collectAsState()
    val aPagar by container.visaoAPagar.collectAsState()
    var confirmarExclusao by rememberSaveable { mutableStateOf(false) }
    var pagamentoParaExcluir by rememberSaveable { mutableStateOf<String?>(null) }

    Subtela(
        titulo = if (vm.editando) "Conta a pagar" else "Nova conta a pagar",
        aoVoltar = aoVoltar,
        acoes = {
            if (vm.editando) IconButton(onClick = { confirmarExclusao = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Excluir") }
        },
    ) { modifier ->
        val v = visao ?: return@Subtela
        if (!vm.carregado) return@Subtela
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.l, vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            // Na edição: situação calculada, botão Pagar e o histórico de pagamentos.
            val ap = aPagar
            val s = ap?.obrigacao(id)?.let { ap.situacao(it, container.relogio.hoje()) }
            if (ap != null && s != null) {
                NkCard {
                    Row(Modifier.padding(Espaco.m)) {
                        // Três colunas: valores no tamanho de lista para caberem inteiros.
                        NkValorRotulado("Previsto", s.obrigacao.valorPrevisto, Modifier.weight(1f), tamanho = com.hkawzz.nkote.ui.componentes.TamanhoValor.LISTA)
                        NkValorRotulado("Pago", s.pago, Modifier.weight(1f), tamanho = com.hkawzz.nkote.ui.componentes.TamanhoValor.LISTA)
                        NkValorRotulado("Falta", s.restante, Modifier.weight(1f), tamanho = com.hkawzz.nkote.ui.componentes.TamanhoValor.LISTA, corValor = corDoStatus(s.status))
                    }
                    NkTextoAjuda("${s.status.rotulo} · ${s.obrigacao.origem.rotulo}")
                }
                if (s.status != com.hkawzz.nkote.dominio.StatusObrigacao.QUITADA) {
                    NkBotaoPrimario("Pagar", { aoPagar(s.obrigacao.id) })
                }

                // Pagamentos "principais" (as despesas de encargo aparecem junto do seu pagamento).
                val movsDeEncargo = ap.ajustes.mapNotNull { it.movimentacaoId }.toSet()
                val pagos = ap.pagamentos.filter { it.obrigacaoId == s.obrigacao.id && it.movimentacaoId !in movsDeEncargo }
                if (pagos.isNotEmpty()) {
                    NkCabecalhoSecao("Pagamentos", Modifier.padding(0.dp))
                    NkCard(Modifier.padding(0.dp)) {
                        pagos.forEachIndexed { i, p ->
                            if (i > 0) NkDivisor()
                            val mov = v.movimentacoes.firstOrNull { it.id == p.movimentacaoId }
                            val extras = ap.ajustes.filter { it.pagamentoId == p.id }.joinToString(" · ") {
                                (if (it.natureza.ehAcrescimo) "+ " else "− ") + it.natureza.rotulo.lowercase() + " " + Dinheiro.formatar(it.valor)
                            }
                            // Total que saiu da conta: o pagamento + o encargo (despesa separada) ligado a ele.
                            val encargo = ap.ajustes.filter { it.pagamentoId == p.id && it.movimentacaoId != null }.sumOf { it.valor }
                            NkListItem(
                                titulo = mov?.let { Formatos.data(it.data) } ?: "Pagamento",
                                subtitulo = listOfNotNull(v.conta(mov?.contaId)?.nome, extras.ifEmpty { null }).joinToString(" · "),
                                fim = { NkValor(p.valor + encargo) },
                                aoClicar = { pagamentoParaExcluir = p.id },
                            )
                        }
                    }
                    Text(
                        "Toque em um pagamento para desfazê-lo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NkCabecalhoSecao("Dados da conta", Modifier.padding(0.dp))
            }

            NkCampo("Descrição", vm.descricao, { vm.descricao = it }, ajuda = "Ex.: Conta de luz, IPVA, Presente")
            CampoDinheiro("Valor previsto", vm.valorTexto, { vm.valorTexto = it })
            CampoEscolha(
                "Categoria",
                listOf(Opcao<String?>(null, "Sem categoria")) + v.categorias.filter { !it.arquivada || it.id == vm.categoriaId }.map { Opcao<String?>(it.id, it.nome) },
                vm.categoriaId,
                { vm.categoriaId = it },
                textoVazio = "Sem categoria",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tem vencimento", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = vm.temVencimento, onCheckedChange = { vm.temVencimento = it })
            }
            if (vm.temVencimento) {
                CampoData("Vencimento", vm.vencimento, { vm.vencimento = it; vm.mes = YearMonth.from(it) })
            }
            SeletorDeMes("Mês da conta", vm.mes) { vm.mes = it }
            Text(
                "O mês fica guardado mesmo se você pagar em outro mês. Contas vencidas e não pagas continuam aparecendo como atrasadas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(Espaco.xxs))
            NkBotaoPrimario(if (vm.editando) "Salvar alterações" else "Adicionar", { vm.salvar(aoVoltar) })
            Spacer(Modifier.height(Espaco.l))
        }
    }

    pagamentoParaExcluir?.let { pid ->
        DialogoConfirmacao(
            titulo = "Desfazer este pagamento?",
            texto = "A despesa (e o encargo ou desconto ligado a ela) será excluída, e a conta volta a ter o valor que faltava antes. Fica tudo no Registro de alterações.",
            textoConfirmar = "Desfazer",
            destrutivo = true,
            aoConfirmar = { pagamentoParaExcluir = null; vm.excluirPagamento(pid) },
            aoCancelar = { pagamentoParaExcluir = null },
        )
    }

    if (confirmarExclusao) {
        DialogoConfirmacao(
            titulo = "Excluir conta a pagar?",
            texto = "Ela some de A pagar. O registro fica no Registro de alterações. Contas com pagamento não podem ser excluídas.",
            textoConfirmar = "Excluir",
            destrutivo = true,
            aoConfirmar = { confirmarExclusao = false; vm.excluir(aoVoltar) },
            aoCancelar = { confirmarExclusao = false },
        )
    }
}

// ===================================================================== Contas fixas

/** Mais → Contas fixas: os modelos que se repetem todo mês. */
@Composable
fun ContasFixasTela(aoVoltar: () -> Unit, aoAbrir: (String) -> Unit, aoNova: () -> Unit) {
    val container = LocalContainer.current
    val aPagar by container.visaoAPagar.collectAsState()
    val visao by container.visao.collectAsState()
    Subtela(titulo = "Contas fixas", aoVoltar = aoVoltar, botaoFlutuante = { BotaoAdicionar("Nova conta fixa", aoNova) }) { modifier ->
        val fixas = aPagar?.contasFixas ?: return@Subtela
        if (fixas.isEmpty()) {
            NkEstadoVazio(
                icone = Icons.Rounded.Refresh,
                titulo = "Nenhuma conta fixa",
                texto = "Cadastre as contas que se repetem todo mês (luz, internet, assinaturas). Depois, lance as do mês em A pagar.",
                modifier = modifier,
                acao = { NkBotaoPrimario("Cadastrar conta fixa", aoNova) },
            )
            return@Subtela
        }
        LazyColumn(modifier.fillMaxSize()) {
            item { NkTextoAjuda("Uma conta fixa é só o modelo: ela não é paga. Em A pagar, você escolhe quais entram em cada mês.") }
            for ((titulo, lista) in listOf("Ativas" to fixas.filter { !it.arquivada }, "Desativadas" to fixas.filter { it.arquivada })) {
                if (lista.isEmpty()) continue
                item { NkCabecalhoSecao(titulo) }
                item {
                    NkCard {
                        lista.forEachIndexed { i, f ->
                            if (i > 0) NkDivisor()
                            val cat = visao?.categoria(f.categoriaId)?.nome
                            NkListItem(
                                titulo = f.nome,
                                subtitulo = listOfNotNull(f.diaVencimento?.let { "Todo dia $it" } ?: "Sem vencimento", cat).joinToString(" · "),
                                fim = { NkValor(f.valorPadrao) },
                                aoClicar = { aoAbrir(f.id) },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Espaco.fimDeListaComBotao)) }
        }
    }
}

class ContaFixaFormViewModel(private val container: AppContainer, private val id: String?) : ViewModel() {
    var carregado by mutableStateOf(id == null); private set
    var nome by mutableStateOf("")
    var valorTexto by mutableStateOf("0,00")
    var diaTexto by mutableStateOf("")
    var categoriaId by mutableStateOf<String?>(null)
    var contaPadraoId by mutableStateOf<String?>(null)
    var arquivada by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set
    val editando get() = id != null

    init {
        if (id != null) {
            viewModelScope.launch {
                container.contasFixas.buscar(id)?.let { f ->
                    nome = f.nome
                    valorTexto = Dinheiro.formatarSemSimbolo(f.valorPadrao)
                    diaTexto = f.diaVencimento?.toString() ?: ""
                    categoriaId = f.categoriaId
                    contaPadraoId = f.contaPadraoId
                    arquivada = f.arquivada
                }
                carregado = true
            }
        }
    }

    fun salvar(aoTerminar: () -> Unit) {
        val valor = Dinheiro.ler(valorTexto) ?: run { erro = "Valor inválido."; return }
        val dia = diaTexto.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: run { erro = "Dia inválido."; return } }
        val f = ContaFixa(id ?: "", nome, valor, dia, categoriaId, contaPadraoId)
        viewModelScope.launch {
            val r = if (id == null) container.contasFixas.criar(f) else container.contasFixas.editar(f)
            when (r) {
                is Resultado.Sucesso -> aoTerminar()
                is Resultado.Falha -> erro = r.mensagem
            }
        }
    }

    fun alternarAtiva(aoTerminar: () -> Unit) {
        val alvo = id ?: return
        viewModelScope.launch {
            container.contasFixas.arquivar(alvo, arquivar = !arquivada)
            aoTerminar()
        }
    }
}

@Composable
fun ContaFixaFormTela(id: String?, aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val vm = nkViewModel(chave = "fixa-$id") { ContaFixaFormViewModel(it, id) }
    val visao by container.visao.collectAsState()
    Subtela(titulo = if (vm.editando) "Conta fixa" else "Nova conta fixa", aoVoltar = aoVoltar) { modifier ->
        val v = visao ?: return@Subtela
        if (!vm.carregado) return@Subtela
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.l, vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            NkCampo("Nome", vm.nome, { vm.nome = it }, ajuda = "Ex.: Luz, Internet, Streaming")
            CampoDinheiro("Valor de costume", vm.valorTexto, { vm.valorTexto = it }, ajuda = "Dá para mudar o valor de cada mês depois de lançar.")
            NkCampo("Dia do vencimento", vm.diaTexto, { novo -> vm.diaTexto = novo.filter { it.isDigit() }.take(2) }, ajuda = "De 1 a 31. Deixe vazio se não tem vencimento.")
            CampoEscolha(
                "Categoria",
                listOf(Opcao<String?>(null, "Sem categoria")) + v.categorias.filter { !it.arquivada || it.id == vm.categoriaId }.map { Opcao<String?>(it.id, it.nome) },
                vm.categoriaId, { vm.categoriaId = it }, textoVazio = "Sem categoria",
            )
            CampoEscolha(
                "Conta que costuma pagar (opcional)",
                listOf(Opcao<String?>(null, "Nenhuma")) + v.contas.filter { it.ehMeioDePagamento && (!it.arquivada || it.id == vm.contaPadraoId) }.map { Opcao<String?>(it.id, it.nome) },
                vm.contaPadraoId, { vm.contaPadraoId = it }, textoVazio = "Nenhuma",
            )
            vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(Espaco.xxs))
            NkBotaoPrimario(if (vm.editando) "Salvar alterações" else "Criar conta fixa", { vm.salvar(aoVoltar) })
            if (vm.editando) {
                com.hkawzz.nkote.ui.componentes.NkBotaoSecundario(if (vm.arquivada) "Reativar" else "Desativar (não lançar mais)", { vm.alternarAtiva(aoVoltar) })
            }
            Spacer(Modifier.height(Espaco.l))
        }
    }
}
