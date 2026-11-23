package com.nkpay.app.ui.objetivos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nkpay.app.AppContainer
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.AparenciaSugerida
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Objetivo
import com.nkpay.app.dominio.SituacaoObjetivo
import com.nkpay.app.dominio.TipoMovimentoReserva
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.ajuda.ChaveAjuda
import com.nkpay.app.ui.ajuda.NkAjuda
import com.nkpay.app.ui.componentes.BotaoAdicionar
import com.nkpay.app.ui.componentes.CampoData
import com.nkpay.app.ui.componentes.CampoDinheiro
import com.nkpay.app.ui.componentes.CampoEscolha
import com.nkpay.app.ui.componentes.DialogoConfirmacao
import com.nkpay.app.ui.componentes.NkBotaoPrimario
import com.nkpay.app.ui.componentes.NkBotaoSecundario
import com.nkpay.app.ui.componentes.NkCabecalhoSecao
import com.nkpay.app.ui.componentes.NkCampo
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.NkCardDestaque
import com.nkpay.app.ui.componentes.NkDivisor
import com.nkpay.app.ui.componentes.NkEstadoVazio
import com.nkpay.app.ui.componentes.NkIconeRedondo
import com.nkpay.app.ui.componentes.NkListItem
import com.nkpay.app.ui.componentes.NkTextoAjuda
import com.nkpay.app.ui.componentes.NkTituloTela
import com.nkpay.app.ui.componentes.NkValor
import com.nkpay.app.ui.componentes.NkValorRotulado
import com.nkpay.app.ui.componentes.Opcao
import com.nkpay.app.ui.componentes.SeletorDeAparencia
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.icones.IconesNk
import com.nkpay.app.ui.inicio.AVISO_GUARDADO_MAIOR
import com.nkpay.app.ui.nkViewModel
import com.nkpay.app.ui.theme.Espaco
import com.nkpay.app.ui.theme.Lilas
import com.nkpay.app.ui.theme.RosaDespesa
import com.nkpay.app.ui.theme.VerdeEntrada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

// v0.7 (regra 6F, ADR 0029): a aba Objetivos. Reservas virtuais: nada aqui é dinheiro entrando ou saindo.

private const val ICONE_PADRAO = "estrela"
private const val COR_PADRAO = 0xFFB39DFFL

@Composable
private fun IconeObjetivo(o: Objetivo) =
    NkIconeRedondo(IconesNk.de(o.icone ?: ICONE_PADRAO), Color(o.cor ?: COR_PADRAO))

/** Barra de progresso simples (0 a 100%). */
@Composable
private fun BarraProgresso(progresso: Int, cor: Color) {
    val f = (progresso.coerceIn(0, 100) / 100f)
    Box(Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Box(Modifier.fillMaxWidth(f).height(6.dp).clip(MaterialTheme.shapes.small).background(cor))
    }
}

/** Linha de resumo de um objetivo: guardado, meta, falta, %, prazo. */
fun resumoDoObjetivo(s: SituacaoObjetivo, hoje: LocalDate): String {
    val meta = s.objetivo.meta
    val partes = mutableListOf<String>()
    if (meta == null) partes += "Sem meta" else {
        partes += "Meta ${Dinheiro.formatar(meta)}"
        partes += if (s.metaAtingida) "meta atingida" else "falta ${Dinheiro.formatar(s.falta ?: 0)}"
    }
    s.objetivo.prazo?.let { prazo ->
        partes += when {
            s.prazoVencido(hoje) -> "prazo vencido (${Formatos.data(prazo)})"
            s.porMes(hoje) != null -> "guardar ${Dinheiro.formatar(s.porMes(hoje)!!)}/mês até ${Formatos.data(prazo)}"
            else -> "até ${Formatos.data(prazo)}"
        }
    }
    if (s.objetivo.concluido) partes += "concluído"
    if (s.objetivo.arquivado) partes += "arquivado"
    return partes.joinToString(" · ")
}

@Composable
private fun CardObjetivo(s: SituacaoObjetivo, hoje: LocalDate, aoClicar: () -> Unit) {
    NkCard(aoClicar = aoClicar) {
        Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconeObjetivo(s.objetivo)
                Spacer(Modifier.padding(start = Espaco.s))
                Column(Modifier.weight(1f)) {
                    Text(s.objetivo.nome, style = MaterialTheme.typography.titleMedium)
                    Text(resumoDoObjetivo(s, hoje), style = MaterialTheme.typography.bodySmall,
                        color = if (s.prazoVencido(hoje)) RosaDespesa else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    NkValor(s.guardado)
                    s.progresso?.let { Text("$it%", style = MaterialTheme.typography.labelMedium, color = if (s.metaAtingida) VerdeEntrada else Lilas) }
                }
            }
            s.progresso?.let { BarraProgresso(it, if (s.metaAtingida) VerdeEntrada else Color(s.objetivo.cor ?: COR_PADRAO)) }
        }
    }
}

/** A aba Objetivos: total guardado, Saldo disponível, ativos × concluídos/arquivados. */
@Composable
fun ObjetivosTela(aoAbrir: (String) -> Unit, aoNovo: () -> Unit) {
    val container = LocalContainer.current
    val visao by container.visao.collectAsState()
    val hoje = container.relogio.hoje()
    var mostrarEncerrados by rememberSaveable { mutableStateOf(false) }
    Scaffold(contentWindowInsets = WindowInsets(0), floatingActionButton = { BotaoAdicionar("Novo objetivo", aoNovo) }) { espaco ->
        val v = visao ?: return@Scaffold
        val vo = v.objetivos
        LazyColumn(Modifier.padding(espaco).fillMaxSize()) {
            item { NkTituloTela("Objetivos") }
            item {
                NkCardDestaque {
                    Column(Modifier.padding(Espaco.l), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Guardado", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    NkAjuda(ChaveAjuda.GUARDADO, cor = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                NkValor(vo.totalGuardado)
                            }
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Saldo disponível", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    NkAjuda(ChaveAjuda.SALDO_DISPONIVEL, cor = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                NkValor(v.resumo.disponivel, cor = if (v.resumo.disponivel < 0) RosaDespesa else null)
                            }
                        }
                        if (v.resumo.disponivel < 0 && vo.totalGuardado > 0) Text(AVISO_GUARDADO_MAIOR, style = MaterialTheme.typography.bodySmall, color = RosaDespesa)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Guardar não tira dinheiro da conta: só separa uma parte do seu saldo.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                            NkAjuda(ChaveAjuda.OBJETIVO, cor = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            if (vo.objetivos.isEmpty()) {
                item {
                    NkEstadoVazio(
                        icone = IconesNk.de(ICONE_PADRAO),
                        titulo = "Nenhum objetivo ainda",
                        texto = "Crie um objetivo (uma viagem, um fone, uma reserva) e guarde parte do seu saldo para ele.",
                        modifier = Modifier.height(280.dp),
                    )
                }
            }
            if (vo.ativos.isNotEmpty()) item { NkCabecalhoSecao("Ativos") }
            for (s in vo.ativos) item(key = s.objetivo.id) { CardObjetivo(s, hoje) { aoAbrir(s.objetivo.id) } }
            if (vo.encerrados.isNotEmpty()) {
                item {
                    NkCabecalhoSecao("Concluídos e arquivados") {
                        TextButton(onClick = { mostrarEncerrados = !mostrarEncerrados }) { Text(if (mostrarEncerrados) "Ocultar" else "Mostrar (${vo.encerrados.size})") }
                    }
                }
                if (mostrarEncerrados) for (s in vo.encerrados) item(key = s.objetivo.id) { CardObjetivo(s, hoje) { aoAbrir(s.objetivo.id) } }
            }
            item { Spacer(Modifier.height(Espaco.fimDeListaComBotao)) }
        }
    }
}

// ---------------------------------------------------------------- cadastro

class ObjetivoFormViewModel(private val container: AppContainer, private val id: String?) : ViewModel() {
    var carregado by mutableStateOf(id == null); private set
    var nome by mutableStateOf("")
    var metaTexto by mutableStateOf("0,00")
    var temPrazo by mutableStateOf(false)
    var prazo by mutableStateOf(container.relogio.hoje().plusMonths(6))
    var icone by mutableStateOf<String?>(null)
    var cor by mutableStateOf<Long?>(null)
    var erro by mutableStateOf<String?>(null); private set
    var salvando by mutableStateOf(false); private set
    val editando get() = id != null

    init {
        if (id != null) viewModelScope.launch {
            container.visao.value?.objetivos?.objetivo(id)?.let { o ->
                nome = o.nome; metaTexto = Dinheiro.formatarSemSimbolo(o.meta ?: 0)
                temPrazo = o.prazo != null; o.prazo?.let { prazo = it }
                icone = o.icone; cor = o.cor
            }
            carregado = true
        }
    }

    fun salvar(aoTerminar: () -> Unit) {
        val meta = Dinheiro.ler(metaTexto)?.takeIf { it > 0 }
        val o = Objetivo(id ?: "", nome.trim(), icone, cor, meta, if (temPrazo) prazo else null)
        salvando = true
        viewModelScope.launch {
            val r = if (id == null) container.objetivos.criar(o) else container.objetivos.editar(o)
            salvando = false
            when (r) {
                is Resultado.Sucesso -> aoTerminar()
                is Resultado.Falha -> erro = r.mensagem
            }
        }
    }
}

@Composable
fun ObjetivoFormTela(id: String?, aoVoltar: () -> Unit) {
    val vm = nkViewModel(chave = "objetivo-form-$id") { ObjetivoFormViewModel(it, id) }
    Subtela(titulo = if (vm.editando) "Editar objetivo" else "Novo objetivo", aoVoltar = aoVoltar) { modifier ->
        if (!vm.carregado) return@Subtela
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.l, vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            NkCampo("Nome", vm.nome, { vm.nome = it }, ajuda = "Ex.: Viagem, Fone, Reserva de emergência")
            CampoDinheiro("Meta (opcional)", vm.metaTexto, { vm.metaTexto = it }, ajuda = "Deixe 0,00 para uma caixinha sem meta.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tem prazo", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = vm.temPrazo, onCheckedChange = { vm.temPrazo = it })
            }
            if (vm.temPrazo) CampoData("Prazo", vm.prazo, { vm.prazo = it })
            val a = AparenciaSugerida.ouNeutra(vm.nome)
            SeletorDeAparencia(vm.icone ?: ICONE_PADRAO, vm.cor ?: a.cor.takeIf { vm.nome.isNotBlank() } ?: COR_PADRAO, { vm.icone = it }, { vm.cor = it })
            vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            NkBotaoPrimario(if (vm.editando) "Salvar alterações" else "Criar objetivo", { vm.salvar(aoVoltar) }, habilitado = !vm.salvando)
            Spacer(Modifier.height(Espaco.l))
        }
    }
}

// ---------------------------------------------------------------- detalhe

/** Um objetivo: números, operações, histórico (com desfazer), concluir, arquivar e excluir. */
@Composable
fun ObjetivoTela(id: String, aoVoltar: () -> Unit, aoEditar: () -> Unit, aoOperar: (String) -> Unit) {
    val container = LocalContainer.current
    val visao by container.visao.collectAsState()
    val hoje = container.relogio.hoje()
    val escopo = rememberCoroutineScope()
    var mensagem by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmarExcluir by rememberSaveable { mutableStateOf(false) }
    val s = visao?.objetivos?.situacao(id)
    fun executar(acao: suspend () -> Resultado<*>, voltar: Boolean = false) {
        escopo.launch {
            when (val r = acao()) {
                is Resultado.Sucesso -> { mensagem = null; if (voltar) withContext(Dispatchers.Main) { aoVoltar() } }
                is Resultado.Falha -> mensagem = r.mensagem
            }
        }
    }
    Subtela(
        titulo = s?.objetivo?.nome ?: "Objetivo",
        aoVoltar = aoVoltar,
        acoes = { IconButton(onClick = aoEditar) { Icon(Icons.Rounded.Edit, contentDescription = "Editar") } },
    ) { modifier ->
        if (s == null) { LaunchedEffect(Unit) { aoVoltar() }; return@Subtela }
        val o = s.objetivo
        LazyColumn(modifier.fillMaxSize()) {
            item {
                NkCard {
                    Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconeObjetivo(o)
                            Spacer(Modifier.padding(start = Espaco.s))
                            Text(resumoDoObjetivo(s, hoje), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        }
                        Row {
                            NkValorRotulado("Guardado", s.guardado, Modifier.weight(1f))
                            s.objetivo.meta?.let { NkValorRotulado("Falta", s.falta ?: 0, Modifier.weight(1f)) }
                        }
                        s.progresso?.let { p ->
                            BarraProgresso(p, if (s.metaAtingida) VerdeEntrada else Color(o.cor ?: COR_PADRAO))
                            Text("$p% da meta", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = Espaco.m, vertical = Espaco.xs), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
                        if (o.ativo) NkBotaoPrimario("Guardar", { aoOperar(TipoMovimentoReserva.GUARDAR.name) }, Modifier.weight(1f))
                        NkBotaoSecundario("Retirar", { aoOperar(TipoMovimentoReserva.RETIRAR.name) }, Modifier.weight(1f), habilitado = s.guardado > 0)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
                        NkBotaoSecundario("Mover", { aoOperar(TipoMovimentoReserva.MOVER.name) }, Modifier.weight(1f), habilitado = s.guardado > 0)
                        NkBotaoSecundario("Ajustar", { aoOperar(TipoMovimentoReserva.AJUSTE.name) }, Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NkTextoAjuda("Vai gastar o dinheiro guardado? Retire do objetivo e depois lance a despesa.", Modifier.weight(1f))
                        NkAjuda(ChaveAjuda.OBJETIVO_RETIRAR)
                    }
                    mensagem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            item { NkCabecalhoSecao("Histórico") }
            item {
                NkCard {
                    if (s.movimentos.isEmpty()) NkTextoAjuda("Nada guardado ainda.", Modifier.padding(Espaco.m))
                    // Um "mover" aparece uma vez em cada objetivo; desfazer desfaz as duas pontas.
                    s.movimentos.forEachIndexed { i, m ->
                        if (i > 0) NkDivisor()
                        val outro = if (m.tipo == TipoMovimentoReserva.MOVER) visao?.objetivos?.movimentos
                            ?.firstOrNull { it.grupoId == m.grupoId && it.objetivoId != m.objetivoId }?.let { visao?.objetivos?.objetivo(it.objetivoId)?.nome } else null
                        NkListItem(
                            titulo = when (m.tipo) {
                                TipoMovimentoReserva.MOVER -> if (m.valor < 0) "Movido para ${outro.orEmpty()}" else "Recebido de ${outro.orEmpty()}"
                                else -> m.tipo.rotulo
                            },
                            subtitulo = Formatos.data(m.data),
                            fim = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(Dinheiro.formatarComSinal(m.valor), style = MaterialTheme.typography.bodyLarge, color = if (m.valor >= 0) VerdeEntrada else RosaDespesa)
                                    TextButton(onClick = { executar({ container.objetivos.desfazer(m.grupoId) }) }) { Text("Desfazer") }
                                }
                            },
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                    NkBotaoSecundario(if (o.concluido) "Reabrir" else "Concluir", { executar({ container.objetivos.concluir(id, !o.concluido) }) })
                    NkBotaoSecundario(if (o.arquivado) "Desarquivar" else "Arquivar", { executar({ container.objetivos.arquivar(id, !o.arquivado) }) })
                    NkBotaoSecundario("Excluir", { confirmarExcluir = true })
                    NkTextoAjuda("Concluir e arquivar mantêm o valor guardado. Para excluir, o objetivo precisa estar sem nada guardado.")
                    Spacer(Modifier.height(Espaco.l))
                }
            }
        }
    }
    if (confirmarExcluir) DialogoConfirmacao(
        titulo = "Excluir “${s?.objetivo?.nome}”?",
        texto = "Fica no Registro de alterações.",
        textoConfirmar = "Excluir",
        destrutivo = true,
        aoConfirmar = { confirmarExcluir = false; executar({ container.objetivos.excluir(id) }, voltar = true) },
        aoCancelar = { confirmarExcluir = false },
    )
}

// ---------------------------------------------------------------- operações

/** Guardar, retirar, mover ou ajustar, em tela cheia (sem diálogos com listas). */
@Composable
fun OperacaoObjetivoTela(id: String, tipo: TipoMovimentoReserva, aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val visao by container.visao.collectAsState()
    val escopo = rememberCoroutineScope()
    var valorTexto by rememberSaveable { mutableStateOf("0,00") }
    var data by rememberSaveable { mutableStateOf(container.relogio.hoje().toString()) }
    var destino by rememberSaveable { mutableStateOf<String?>(null) }
    var erro by rememberSaveable { mutableStateOf<String?>(null) }
    var salvando by rememberSaveable { mutableStateOf(false) }
    val s = visao?.objetivos?.situacao(id)
    val titulo = when (tipo) {
        TipoMovimentoReserva.GUARDAR -> "Guardar"
        TipoMovimentoReserva.RETIRAR -> "Retirar"
        TipoMovimentoReserva.MOVER -> "Mover para outro objetivo"
        TipoMovimentoReserva.AJUSTE -> "Ajustar valor guardado"
    }
    Subtela(titulo = titulo, aoVoltar = aoVoltar) { modifier ->
        val v = visao ?: return@Subtela
        if (s == null) return@Subtela
        LaunchedEffect(Unit) { if (tipo == TipoMovimentoReserva.AJUSTE) valorTexto = Dinheiro.formatarSemSimbolo(s.guardado) }
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.l, vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            Text("${s.objetivo.nome} · guardado ${Dinheiro.formatar(s.guardado)} · disponível ${Dinheiro.formatar(v.resumo.disponivel)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when (tipo) {
                    TipoMovimentoReserva.GUARDAR -> "Separa uma parte do seu saldo para este objetivo. O dinheiro continua na conta; o Saldo disponível diminui."
                    TipoMovimentoReserva.RETIRAR -> "Devolve ao Saldo disponível. Não é despesa. Se for gastar, lance a despesa depois, normalmente."
                    TipoMovimentoReserva.MOVER -> "Passa o valor para outro objetivo. O Saldo disponível não muda."
                    TipoMovimentoReserva.AJUSTE -> "Informe quanto deve ficar guardado. A diferença fica registrada no histórico como ajuste."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            CampoDinheiro(if (tipo == TipoMovimentoReserva.AJUSTE) "Novo valor guardado" else "Valor", valorTexto, { valorTexto = it })
            if (tipo == TipoMovimentoReserva.MOVER) {
                CampoEscolha(
                    "Para",
                    v.objetivos.ativos.filter { it.objetivo.id != id }.map { Opcao(it.objetivo.id, it.objetivo.nome, "Guardado ${Dinheiro.formatar(it.guardado)}") },
                    destino, { destino = it },
                )
            }
            CampoData("Data", LocalDate.parse(data), { data = it.toString() })
            erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            NkBotaoPrimario("Confirmar", {
                val valor = Dinheiro.ler(valorTexto) ?: 0
                val d = LocalDate.parse(data)
                salvando = true
                escopo.launch {
                    val r = when (tipo) {
                        TipoMovimentoReserva.GUARDAR -> container.objetivos.guardar(id, valor, d)
                        TipoMovimentoReserva.RETIRAR -> container.objetivos.retirar(id, valor, d)
                        TipoMovimentoReserva.MOVER -> destino?.let { container.objetivos.mover(id, it, valor, d) } ?: Resultado.Falha("Escolha o objetivo de destino.")
                        TipoMovimentoReserva.AJUSTE -> container.objetivos.ajustar(id, valor, d)
                    }
                    salvando = false
                    when (r) {
                        is Resultado.Sucesso -> withContext(Dispatchers.Main) { aoVoltar() }
                        is Resultado.Falha -> erro = r.mensagem
                    }
                }
            }, habilitado = !salvando)
            Spacer(Modifier.height(Espaco.l))
        }
    }
}
