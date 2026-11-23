package com.nkpay.app.ui.emprestimos

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nkpay.app.AppContainer
import com.nkpay.app.dados.NovoEmprestimo
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.AparenciaSugerida
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Emprestimos
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.ParcelaEmprestimo
import com.nkpay.app.dominio.SituacaoEmprestimo
import com.nkpay.app.dominio.StatusObrigacao
import com.nkpay.app.dominio.TipoEmprestimo
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.ajuda.ChaveAjuda
import com.nkpay.app.ui.ajuda.NkAjuda
import com.nkpay.app.ui.apagar.corDoStatus
import com.nkpay.app.ui.componentes.BotaoAdicionar
import com.nkpay.app.ui.componentes.CampoData
import com.nkpay.app.ui.componentes.CampoDinheiro
import com.nkpay.app.ui.componentes.CampoEscolha
import com.nkpay.app.ui.componentes.DialogoConfirmacao
import com.nkpay.app.ui.componentes.EscolhaSegmentada
import com.nkpay.app.ui.componentes.EstiloCard
import com.nkpay.app.ui.componentes.IconeDaConta
import com.nkpay.app.ui.componentes.NkBotaoPrimario
import com.nkpay.app.ui.componentes.NkBotaoSecundario
import com.nkpay.app.ui.componentes.NkCabecalhoSecao
import com.nkpay.app.ui.componentes.NkCampo
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.NkCardDestaque
import com.nkpay.app.ui.componentes.NkDivisor
import com.nkpay.app.ui.componentes.NkEstadoVazio
import com.nkpay.app.ui.componentes.NkListItem
import com.nkpay.app.ui.componentes.NkTextoAjuda
import com.nkpay.app.ui.componentes.NkValor
import com.nkpay.app.ui.componentes.NkValorAjustavel
import com.nkpay.app.ui.componentes.Opcao
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.icones.IconesNk
import com.nkpay.app.ui.nkViewModel
import com.nkpay.app.ui.theme.Espaco
import com.nkpay.app.ui.theme.EstiloValor
import com.nkpay.app.ui.theme.RosaDespesa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

// Mais → Empréstimos (v0.5, PR 7). Regras 6E; ADR 0021.

/** O último tipo aberto (Recebidos/Concedidos), só na memória da tela. */
private var tipoLembrado = TipoEmprestimo.RECEBIDO

/** Texto da situação de uma parcela: em dia, paga, ou "vencida há N dias". */
fun rotuloDaParcela(p: ParcelaEmprestimo): String = when {
    p.situacao.status == StatusObrigacao.QUITADA -> "Paga"
    p.atrasada -> "Atrasada · vencida há ${p.diasDeAtraso} ${if (p.diasDeAtraso == 1) "dia" else "dias"}"
    p.situacao.pago > 0 -> "Em parte · vence ${p.obrigacao.vencimento?.let { Formatos.data(it) }}"
    else -> "Vence ${p.obrigacao.vencimento?.let { Formatos.data(it) }}"
}

/** Lista de empréstimos, com as abas Recebidos (eu devo) e Concedidos (me devem). */
@Composable
fun EmprestimosTela(aoVoltar: () -> Unit, aoAbrir: (String) -> Unit, aoNovo: (TipoEmprestimo) -> Unit) {
    val container = LocalContainer.current
    val visao by container.visaoEmprestimos.collectAsState()
    val hoje = container.relogio.hoje()
    var tipo by rememberSaveable { mutableStateOf(tipoLembrado) }
    Subtela(titulo = "Empréstimos", aoVoltar = aoVoltar, botaoFlutuante = { BotaoAdicionar("Novo empréstimo") { aoNovo(tipo) } }) { modifier ->
        val v = visao ?: return@Subtela
        val lista = v.situacoes(tipo, hoje)
        LazyColumn(modifier.fillMaxSize()) {
            item {
                EscolhaSegmentada(
                    listOf(Opcao(TipoEmprestimo.RECEBIDO, "Recebidos (eu devo)"), Opcao(TipoEmprestimo.CONCEDIDO, "Concedidos (me devem)")),
                    tipo, { tipo = it; tipoLembrado = it }, Modifier.padding(horizontal = Espaco.m, vertical = Espaco.xs),
                )
            }
            val abertos = lista.filter { !it.quitado }
            item {
                NkCardDestaque {
                    Column(Modifier.padding(Espaco.l)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (tipo == TipoEmprestimo.RECEBIDO) "Total a pagar" else "Total a receber", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            NkAjuda(ChaveAjuda.EMPRESTIMO_RESTANTE, cor = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        NkValorAjustavel(abertos.sumOf { it.restante }, estilo = EstiloValor.destaque, cor = MaterialTheme.colorScheme.onBackground)
                        val atrasadas = abertos.sumOf { it.atrasadas.size }
                        if (atrasadas > 0) Text("$atrasadas ${if (atrasadas == 1) "parcela atrasada" else "parcelas atrasadas"}", color = RosaDespesa, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (lista.isEmpty()) {
                item {
                    NkEstadoVazio(
                        icone = IconesNk.de(if (tipo == TipoEmprestimo.RECEBIDO) "banco" else "pessoa"),
                        titulo = if (tipo == TipoEmprestimo.RECEBIDO) "Nenhum empréstimo recebido" else "Nenhum empréstimo concedido",
                        texto = if (tipo == TipoEmprestimo.RECEBIDO) "Cadastre o dinheiro que você pegou emprestado e as parcelas a pagar." else "Cadastre o dinheiro que você emprestou e as parcelas a receber.",
                        modifier = Modifier.height(280.dp),
                    )
                }
            }
            // Agrupados por pessoa: "quanto o Tonho me deve".
            for ((contaId, doa) in lista.groupBy { it.emprestimo.contaId }) {
                val pessoa = v.conta(contaId) ?: continue
                item(key = contaId) {
                    NkCabecalhoSecao(pessoa.nome) { NkValor(doa.sumOf { it.restante }) }
                    NkCard {
                        doa.forEachIndexed { i, s ->
                            if (i > 0) NkDivisor()
                            LinhaEmprestimo(s) { aoAbrir(s.emprestimo.id) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Espaco.fimDeListaComBotao)) }
        }
    }
}


@Composable
private fun LinhaEmprestimo(s: SituacaoEmprestimo, aoClicar: () -> Unit) {
    val proxima = s.proxima
    val sub = when {
        s.quitado -> "Quitado"
        s.atrasadas.isNotEmpty() -> "${s.atrasadas.size} atrasada(s) · " + rotuloDaParcela(s.atrasadas.first())
        proxima != null -> "Próxima: ${Dinheiro.formatar(proxima.restante)} em ${proxima.obrigacao.vencimento?.let { Formatos.data(it) }}"
        else -> ""
    } + if (s.emprestimo.anteriorAoApp) " · anterior ao app" else ""
    NkListItem(
        titulo = s.emprestimo.descricao,
        subtitulo = sub,
        corSubtitulo = if (s.atrasadas.isNotEmpty()) RosaDespesa else MaterialTheme.colorScheme.onSurfaceVariant,
        linhas = 2,
        fim = { NkValor(s.restante) },
        aoClicar = aoClicar,
    )
}

// ---------------------------------------------------------------- cadastro

enum class ModoParcelas { AUTOMATICAS, PERSONALIZADAS }

class EmprestimoFormViewModel(private val container: AppContainer, tipoInicial: TipoEmprestimo) : ViewModel() {
    var tipo by mutableStateOf(tipoInicial)
    var pessoaId by mutableStateOf<String?>(null)
    var novaPessoa by mutableStateOf("")
    var descricao by mutableStateOf("")
    var categoriaId by mutableStateOf<String?>(null)
    var valorTexto by mutableStateOf("0,00")
    var data by mutableStateOf(container.relogio.hoje())
    var contaPropriaId by mutableStateOf<String?>(null)
    var anterior by mutableStateOf(false)
    var jurosTexto by mutableStateOf("0,00")
    var modo by mutableStateOf(ModoParcelas.AUTOMATICAS)
    var quantidadeTexto by mutableStateOf("1")
    var valorParcelaTexto by mutableStateOf("0,00")
    var primeiroVencimento by mutableStateOf(container.relogio.hoje().plusMonths(1))
    /** Parcelas personalizadas: valor (texto) e data de cada linha. */
    val linhas = mutableStateListOf(Pair("0,00", container.relogio.hoje().plusMonths(1)))
    var avisos by mutableStateOf<List<String>>(emptyList()); private set
    var erro by mutableStateOf<String?>(null)
    var salvando by mutableStateOf(false); private set

    fun parcelas(): List<Pair<LocalDate, Long>> = when (modo) {
        ModoParcelas.AUTOMATICAS -> {
            val n = quantidadeTexto.toIntOrNull() ?: 0
            val v = Dinheiro.ler(valorParcelaTexto) ?: 0
            if (n in 1..240) Emprestimos.parcelasAutomaticas(n, v, primeiroVencimento) else emptyList()
        }
        ModoParcelas.PERSONALIZADAS -> linhas.map { (texto, data) -> data to (Dinheiro.ler(texto) ?: 0) }
    }

    fun novo() = NovoEmprestimo(
        tipo = tipo, contaPessoaId = pessoaId, nomeNovaPessoa = novaPessoa.takeIf { pessoaId == null && it.isNotBlank() },
        descricao = descricao, categoriaId = categoriaId, valorOriginal = Dinheiro.ler(valorTexto) ?: 0, data = data,
        contaPropriaId = contaPropriaId.takeIf { !anterior }, parcelas = parcelas(), anteriorAoApp = anterior,
        jurosInformados = if (anterior && tipo == TipoEmprestimo.CONCEDIDO) Dinheiro.ler(jurosTexto) ?: 0 else 0,
    )

    fun verificarDuplicadas() {
        viewModelScope.launch { avisos = container.compras.possiveisDuplicadas(descricao, parcelas().sumOf { it.second }, parcelas().size) }
    }

    fun salvar(aoTerminar: () -> Unit) {
        salvando = true
        viewModelScope.launch {
            val r = container.emprestimos.registrar(novo())
            salvando = false
            when (r) {
                is Resultado.Sucesso -> aoTerminar()
                is Resultado.Falha -> erro = r.mensagem
            }
        }
    }
}

/** Cadastro de empréstimo: parcelas automáticas ou personalizadas, e "anterior ao app". */
@Composable
fun EmprestimoFormTela(tipoInicial: TipoEmprestimo, aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val vm = nkViewModel(chave = "emprestimo-form-$tipoInicial") { EmprestimoFormViewModel(it, tipoInicial) }
    val visao by container.visao.collectAsState()
    LaunchedEffect(vm.anterior, vm.descricao, vm.valorParcelaTexto, vm.quantidadeTexto) { if (vm.anterior) vm.verificarDuplicadas() }
    LaunchedEffect(visao) {
        if (vm.contaPropriaId == null) vm.contaPropriaId = visao?.contas?.firstOrNull { it.natureza == Natureza.PROPRIO && !it.arquivada }?.id
    }

    Subtela(titulo = "Novo empréstimo", aoVoltar = aoVoltar) { modifier ->
        val v = visao ?: return@Subtela
        val recebido = vm.tipo == TipoEmprestimo.RECEBIDO
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.l, vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            EscolhaSegmentada(
                listOf(Opcao(TipoEmprestimo.RECEBIDO, "Peguei (eu devo)"), Opcao(TipoEmprestimo.CONCEDIDO, "Emprestei (me devem)")),
                vm.tipo, { vm.tipo = it; vm.pessoaId = null },
            )
            val pessoas = v.contas.filter { it.natureza == vm.tipo.natureza && !it.arquivada }
            CampoEscolha(
                if (recebido) "De quem (pessoa ou instituição)" else "Para quem",
                listOf(Opcao<String?>(null, "Nova pessoa…")) + pessoas.map { Opcao<String?>(it.id, it.nome) },
                vm.pessoaId,
                { vm.pessoaId = it },
                textoVazio = "Nova pessoa…",
            )
            if (vm.pessoaId == null) NkCampo("Nome da pessoa ou instituição", vm.novaPessoa, { vm.novaPessoa = it })
            NkCampo("Nome do empréstimo", vm.descricao, { vm.descricao = it }, ajuda = if (recebido) "Ex.: Empréstimo do banco" else "Ex.: Para o conserto do carro")
            CampoEscolha(
                "Categoria (opcional, só descritiva)",
                listOf(Opcao<String?>(null, "Sem categoria")) + v.categorias.filter { !it.arquivada }.map {
                    val a = AparenciaSugerida.NEUTRA
                    Opcao<String?>(it.id, it.nome, icone = IconesNk.de(it.icone ?: a.icone), cor = it.cor ?: a.cor)
                },
                vm.categoriaId, { vm.categoriaId = it }, textoVazio = "Sem categoria",
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Anterior ao app", style = MaterialTheme.typography.bodyLarge)
                    Text("Já existia antes de usar o app: cadastre só as parcelas pendentes. Nada mexe no Pix.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.anterior, onCheckedChange = { vm.anterior = it })
            }

            if (!vm.anterior) {
                CampoDinheiro("Valor original", vm.valorTexto, { vm.valorTexto = it }, ajuda = if (recebido) "Quanto entrou na sua conta." else "Quanto saiu da sua conta.")
                CampoData(if (recebido) "Data em que recebeu" else "Data em que emprestou", vm.data, { vm.data = it })
                CampoEscolha(
                    if (recebido) "Entrou em" else "Saiu de",
                    v.contas.filter { it.natureza == Natureza.PROPRIO && (!it.arquivada || it.id == vm.contaPropriaId) }.map { Opcao(it.id, it.nome, "Saldo " + Dinheiro.formatar(v.saldo(it.id))) },
                    vm.contaPropriaId, { vm.contaPropriaId = it },
                )
            } else if (!recebido) {
                CampoDinheiro("Juros contidos nas parcelas pendentes (opcional)", vm.jurosTexto, { vm.jurosTexto = it }, ajuda = "Com R$ 0,00, tudo o que receber conta como devolução, sem receita de juros.")
            }

            Text(if (vm.anterior) "Parcelas pendentes" else "Parcelas", style = MaterialTheme.typography.titleSmall)
            EscolhaSegmentada(listOf(Opcao(ModoParcelas.AUTOMATICAS, "Automáticas"), Opcao(ModoParcelas.PERSONALIZADAS, "Personalizadas")), vm.modo, { vm.modo = it })
            if (vm.modo == ModoParcelas.AUTOMATICAS) {
                NkCampo("Quantidade", vm.quantidadeTexto, { vm.quantidadeTexto = it.filter(Char::isDigit).take(3) })
                CampoDinheiro("Valor de cada parcela", vm.valorParcelaTexto, { vm.valorParcelaTexto = it })
                CampoData("1º vencimento", vm.primeiroVencimento, { vm.primeiroVencimento = it })
            } else {
                vm.linhas.forEachIndexed { i, (texto, data) ->
                    Text("Parcela ${i + 1}", style = MaterialTheme.typography.labelLarge)
                    CampoDinheiro("Valor da parcela ${i + 1}", texto, { vm.linhas[i] = it to data })
                    CampoData("Vencimento da parcela ${i + 1}", data, { vm.linhas[i] = texto to it })
                }
                Row {
                    TextButton(onClick = { vm.linhas.add("0,00" to (vm.linhas.lastOrNull()?.second ?: vm.data).plusMonths(1)) }) { Text("Adicionar parcela") }
                    if (vm.linhas.size > 1) TextButton(onClick = { vm.linhas.removeAt(vm.linhas.lastIndex) }) { Text("Remover a última") }
                }
            }

            // O app mostra na hora o total e os juros previstos.
            val ps = vm.parcelas()
            val total = ps.sumOf { it.second }
            NkCard(estilo = EstiloCard.ELEVADO) {
                Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xxs)) {
                    Text("${ps.size} ${if (ps.size == 1) "parcela" else "parcelas"} · total ${Dinheiro.formatar(total)}", style = MaterialTheme.typography.bodyMedium)
                    if (!vm.anterior) {
                        val juros = total - (Dinheiro.ler(vm.valorTexto) ?: 0)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Juros previstos: ${Dinheiro.formatar(juros)}", style = MaterialTheme.typography.bodyMedium, color = if (juros < 0) RosaDespesa else MaterialTheme.colorScheme.onSurface)
                            NkAjuda(ChaveAjuda.EMPRESTIMO_JUROS_PREVISTOS)
                        }
                        Text(
                            if (recebido) "Os juros viram uma despesa (Encargos financeiros) na data em que recebeu. Pagar as parcelas é só transferência."
                            else "Os juros viram receita aos poucos, conforme você recebe as parcelas.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("Nada mexe nas suas contas: só a ${if (recebido) "dívida" else "quantia a receber"} passa a aparecer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (vm.anterior && vm.avisos.isNotEmpty()) {
                NkCard(estilo = EstiloCard.ELEVADO) {
                    Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xxs)) {
                        Text("Isto pode já estar no app", style = MaterialTheme.typography.titleSmall, color = RosaDespesa)
                        for (a in vm.avisos) Text("• $a", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Se for o mesmo empréstimo, ele seria contado duas vezes. O app não converte nada sozinho. Se você usou um cartão ou crediário de mentira para ele: desfaça os pagamentos, exclua a compra, arquive a conta e cadastre aqui como anterior ao app.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            NkBotaoPrimario("Registrar empréstimo", { vm.salvar(aoVoltar) }, habilitado = !vm.salvando)
            Spacer(Modifier.height(Espaco.l))
        }
    }
}

// ---------------------------------------------------------------- um empréstimo

/** Um empréstimo: números, parcelas (com atraso) e excluir (sem pagamentos). */
@Composable
fun EmprestimoTela(id: String, aoVoltar: () -> Unit, aoAbrirParcela: (String) -> Unit) {
    val container = LocalContainer.current
    val visao by container.visaoEmprestimos.collectAsState()
    val hoje = container.relogio.hoje()
    val escopo = rememberCoroutineScope()
    var confirmar by rememberSaveable { mutableStateOf(false) }
    var erro by rememberSaveable { mutableStateOf<String?>(null) }
    val s = visao?.situacao(id, hoje)
    Subtela(titulo = s?.emprestimo?.descricao ?: "Empréstimo", aoVoltar = aoVoltar) { modifier ->
        val v = visao ?: return@Subtela
        if (s == null) { LaunchedEffect(Unit) { aoVoltar() }; return@Subtela }
        val recebido = s.tipo == TipoEmprestimo.RECEBIDO
        val pessoa = v.conta(s.emprestimo.contaId)
        LazyColumn(modifier.fillMaxSize()) {
            item {
                NkCard {
                    Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            pessoa?.let { IconeDaConta(it) }
                            Spacer(Modifier.padding(Espaco.xxs))
                            Text((if (recebido) "Eu devo a " else "Me deve: ") + (pessoa?.nome ?: "?"), style = MaterialTheme.typography.titleMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Restante", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            NkAjuda(ChaveAjuda.EMPRESTIMO_RESTANTE)
                        }
                        NkValorAjustavel(s.restante, estilo = EstiloValor.medio)
                        if (!s.emprestimo.anteriorAoApp) LinhaNumero("Valor original", s.emprestimo.valorOriginal)
                        LinhaNumero(if (s.emprestimo.anteriorAoApp) "Total das parcelas pendentes" else "Total das parcelas", s.totalParcelas)
                        if (!(s.emprestimo.anteriorAoApp && recebido)) LinhaNumero("Juros previstos", s.jurosPrevistos, ChaveAjuda.EMPRESTIMO_JUROS_PREVISTOS)
                        LinhaNumero(if (recebido) "Pago até agora" else "Recebido até agora", s.pagoOuRecebido)
                        if (!recebido) LinhaNumero("Juros recebidos", s.jurosRecebidos, ChaveAjuda.EMPRESTIMO_JUROS_RECEBIDOS)
                        Text(
                            (if (s.emprestimo.anteriorAoApp) "Anterior ao app" else Formatos.data(s.emprestimo.dataContratacao)) +
                                (s.emprestimo.contaPropriaId?.let { c -> " · " + (if (recebido) "entrou em " else "saiu de ") + (v.conta(c)?.nome ?: "?") } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(Modifier.padding(start = Espaco.l, top = Espaco.m), verticalAlignment = Alignment.CenterVertically) {
                    Text("Parcelas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.atrasadas.isNotEmpty()) NkAjuda(ChaveAjuda.EMPRESTIMO_ATRASADA)
                }
            }
            item {
                NkCard {
                    s.parcelas.forEachIndexed { i, p ->
                        if (i > 0) NkDivisor()
                        NkListItem(
                            titulo = "Parcela ${p.obrigacao.numeroParcela}/${p.obrigacao.totalParcelas}",
                            subtitulo = rotuloDaParcela(p),
                            corSubtitulo = if (p.atrasada) RosaDespesa else corDoStatus(p.situacao.status),
                            fim = { if (p.restante > 0) NkValor(p.restante, cor = if (p.atrasada) RosaDespesa else null) else NkValor(p.obrigacao.valorPrevisto, tipo = com.nkpay.app.ui.componentes.TipoValor.SECUNDARIO) },
                            aoClicar = { aoAbrirParcela(p.obrigacao.id) },
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(Espaco.l), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                    erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!s.temPagamento) {
                        NkBotaoSecundario("Excluir empréstimo", { confirmar = true })
                        NkTextoAjuda("Para corrigir o valor original ou a data, exclua e cadastre de novo (só enquanto nada foi pago ou recebido).", Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    if (confirmar) {
        DialogoConfirmacao(
            titulo = "Excluir empréstimo?",
            texto = "A transferência inicial, os juros e as parcelas saem dos cálculos. Tudo fica no Registro de alterações.",
            textoConfirmar = "Excluir",
            destrutivo = true,
            aoConfirmar = {
                confirmar = false
                escopo.launch {
                    when (val r = container.emprestimos.excluir(id)) {
                        is Resultado.Sucesso -> withContext(Dispatchers.Main) { aoVoltar() }
                        is Resultado.Falha -> erro = r.mensagem
                    }
                }
            },
            aoCancelar = { confirmar = false },
        )
    }
}

@Composable
private fun LinhaNumero(rotulo: String, valor: Long, ajuda: ChaveAjuda? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, style = MaterialTheme.typography.bodyMedium)
        ajuda?.let { NkAjuda(it) }
        Spacer(Modifier.weight(1f))
        Text(Dinheiro.formatar(valor), style = EstiloValor.lista)
    }
}
