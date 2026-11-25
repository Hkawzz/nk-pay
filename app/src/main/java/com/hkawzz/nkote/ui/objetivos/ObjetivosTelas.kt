package com.hkawzz.nkote.ui.objetivos

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
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.AparenciaSugerida
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Objetivo
import com.hkawzz.nkote.dominio.SituacaoObjetivo
import com.hkawzz.nkote.dominio.TipoMovimentoCaixinha
import com.hkawzz.nkote.ui.Formatos
import com.hkawzz.nkote.ui.LocalContainer
import com.hkawzz.nkote.ui.ajuda.ChaveAjuda
import com.hkawzz.nkote.ui.ajuda.NkAjuda
import com.hkawzz.nkote.ui.componentes.BotaoAdicionar
import com.hkawzz.nkote.ui.componentes.CampoData
import com.hkawzz.nkote.ui.componentes.CampoDinheiro
import com.hkawzz.nkote.ui.componentes.CampoEscolha
import com.hkawzz.nkote.ui.componentes.DialogoConfirmacao
import com.hkawzz.nkote.ui.componentes.NkBotaoPrimario
import com.hkawzz.nkote.ui.componentes.NkBotaoSecundario
import com.hkawzz.nkote.ui.componentes.NkCabecalhoSecao
import com.hkawzz.nkote.ui.componentes.NkCampo
import com.hkawzz.nkote.ui.componentes.NkCard
import com.hkawzz.nkote.ui.componentes.NkCardDestaque
import com.hkawzz.nkote.ui.componentes.NkDivisor
import com.hkawzz.nkote.ui.componentes.NkEstadoVazio
import com.hkawzz.nkote.ui.componentes.NkIconeRedondo
import com.hkawzz.nkote.ui.componentes.NkListItem
import com.hkawzz.nkote.ui.componentes.NkTextoAjuda
import com.hkawzz.nkote.ui.componentes.NkTituloTela
import com.hkawzz.nkote.ui.componentes.NkValor
import com.hkawzz.nkote.ui.componentes.NkValorRotulado
import com.hkawzz.nkote.ui.componentes.Opcao
import com.hkawzz.nkote.ui.componentes.SeletorDeAparencia
import com.hkawzz.nkote.ui.componentes.Subtela
import com.hkawzz.nkote.ui.icones.IconesNk
import com.hkawzz.nkote.ui.nkViewModel
import com.hkawzz.nkote.ui.theme.Espaco
import com.hkawzz.nkote.ui.theme.Lilas
import com.hkawzz.nkote.ui.theme.RosaDespesa
import com.hkawzz.nkote.ui.theme.VerdeEntrada
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

/** Texto da mensagem da conversão V3 (regra 6G.3). */
const val TEXTO_AVISO_CAIXINHAS = "Antes, o objetivo só separava uma parte do saldo, sem tirar o dinheiro da conta. Agora cada objetivo tem uma caixinha de verdade, que começou zerada. Nenhuma conta mudou; o Saldo disponível pode ter subido, porque o dinheiro nunca saiu das contas. Para separar o dinheiro de novo, abra o objetivo e toque em Guardar."

/** v0.8 (V3, ADR 0032): explica, uma vez só, que os objetivos da v0.7 viraram caixinhas zeradas. */
@Composable
fun AvisoCaixinhas(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val visao by container.visao.collectAsState()
    val visto by container.avisos.conversaoV3Vista.collectAsState()
    if (visto || visao?.objetivos?.temHistoricoAntigo != true) return
    NkCard(estilo = com.hkawzz.nkote.ui.componentes.EstiloCard.ELEVADO, modifier = modifier) {
        Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
            Text("Seus objetivos agora são caixinhas", style = MaterialTheme.typography.titleSmall, color = Lilas)
            Text(TEXTO_AVISO_CAIXINHAS, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { container.avisos.marcarConversaoV3Vista() }) { Text("Entendi") }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Cada objetivo tem uma caixinha: guardar tira o dinheiro da conta e põe nela.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                            NkAjuda(ChaveAjuda.OBJETIVO, cor = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            item { AvisoCaixinhas() }
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
            // v0.9 (item 7): de onde vem cada parte do dinheiro guardado. A soma é o total acima.
            val partes = visao?.objetivos?.partesDe(id).orEmpty()
            if (partes.isNotEmpty()) {
                item { NkCabecalhoSecao("Partes") }
                item {
                    NkCard {
                        partes.forEachIndexed { i, p ->
                            if (i > 0) NkDivisor()
                            NkListItem(
                                titulo = nomeDaParte(visao, p.contaId),
                                subtitulo = if (p.contaId == null) "Rendimentos antigos e o que não deu para atribuir. Use “Entre partes” para dizer onde está." else null,
                                linhas = 2,
                                fim = { Text(Dinheiro.formatar(p.valor), style = MaterialTheme.typography.bodyLarge) },
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = Espaco.m, vertical = Espaco.xs), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
                        if (o.ativo) NkBotaoPrimario("Guardar", { aoOperar(OperacaoCaixinha.GUARDAR.name) }, Modifier.weight(1f))
                        NkBotaoSecundario("Retirar", { aoOperar(OperacaoCaixinha.RETIRAR.name) }, Modifier.weight(1f), habilitado = s.guardado > 0)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
                        NkBotaoSecundario("Mover", { aoOperar(OperacaoCaixinha.MOVER.name) }, Modifier.weight(1f), habilitado = s.guardado > 0)
                        NkBotaoSecundario("Rendimento", { aoOperar(OperacaoCaixinha.RENDIMENTO.name) }, Modifier.weight(1f))
                    }
                    // v0.9 (item 7): mudar a composição (ex.: depositou no banco o dinheiro físico guardado).
                    NkBotaoSecundario("Entre partes", { aoOperar(OperacaoCaixinha.MOVER_PARTE.name) }, habilitado = s.guardado > 0)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NkTextoAjuda("Vai gastar o dinheiro guardado? Retire para uma conta e depois lance a despesa.", Modifier.weight(1f))
                        NkAjuda(ChaveAjuda.OBJETIVO_RETIRAR)
                    }
                    mensagem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            item { NkCabecalhoSecao("Histórico") }
            item {
                NkCard {
                    if (s.movimentos.isEmpty()) NkTextoAjuda("A caixinha está zerada. Toque em Guardar para separar dinheiro de uma conta.", Modifier.padding(Espaco.m))
                    s.movimentos.forEachIndexed { i, m ->
                        if (i > 0) NkDivisor()
                        val outraConta = visao?.conta(m.outraContaId)?.nome.orEmpty()
                        val outroObjetivo = visao?.objetivos?.objetivoDaCaixinha(m.outraContaId)?.nome ?: outraConta
                        NkListItem(
                            titulo = when (m.tipo) {
                                TipoMovimentoCaixinha.GUARDAR -> "Guardado de $outraConta"
                                TipoMovimentoCaixinha.RETIRAR -> "Retirado para $outraConta"
                                TipoMovimentoCaixinha.MOVER_SAIDA -> "Movido para $outroObjetivo"
                                TipoMovimentoCaixinha.MOVER_ENTRADA -> "Recebido de $outroObjetivo"
                                TipoMovimentoCaixinha.RENDIMENTO -> "Rendimento"
                                TipoMovimentoCaixinha.MOVER_PARTE -> "Entre partes: ${nomeDaParte(visao, m.partes.getOrNull(0))} → ${nomeDaParte(visao, m.partes.getOrNull(1))}"
                            },
                            // v0.9 (item 7): a parte de cada movimento.
                            subtitulo = Formatos.data(m.data) + if (m.tipo != TipoMovimentoCaixinha.MOVER_PARTE && m.partes.isNotEmpty()) {
                                " · parte: " + m.partes.joinToString(", ") { nomeDaParte(visao, it) }
                            } else "",
                            linhas = 2,
                            fim = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(Dinheiro.formatarComSinal(m.valor), style = MaterialTheme.typography.bodyLarge, color = if (m.tipo == TipoMovimentoCaixinha.MOVER_PARTE) MaterialTheme.colorScheme.onSurfaceVariant else if (m.valor >= 0) VerdeEntrada else RosaDespesa)
                                    TextButton(onClick = {
                                        executar({ if (m.tipo == TipoMovimentoCaixinha.MOVER_PARTE) container.objetivos.desfazerEntrePartes(m.grupoId!!) else container.objetivos.desfazer(m.movimentacaoId) })
                                    }) { Text("Desfazer") }
                                }
                            },
                        )
                    }
                }
            }
            // V3 (ADR 0032): o que foi "carimbado" na v0.7 fica só para leitura e não conta mais.
            if (s.antigos.isNotEmpty()) {
                item { NkCabecalhoSecao("Antes das caixinhas (só leitura)") }
                item {
                    NkCard {
                        NkTextoAjuda("Na versão anterior, o objetivo só separava uma parte do saldo, sem tirar o dinheiro da conta. Esses valores não contam mais.", Modifier.padding(Espaco.m))
                        s.antigos.forEach { a ->
                            NkDivisor()
                            NkListItem(
                                titulo = a.tipo.rotulo,
                                subtitulo = Formatos.data(a.data),
                                fim = { Text(Dinheiro.formatarComSinal(a.valor), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(Espaco.m), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                    NkBotaoSecundario(if (o.concluido) "Reabrir" else "Concluir", { executar({ container.objetivos.concluir(id, !o.concluido) }) })
                    NkBotaoSecundario(if (o.arquivado) "Desarquivar" else "Arquivar", { executar({ container.objetivos.arquivar(id, !o.arquivado) }) })
                    NkBotaoSecundario("Excluir", { confirmarExcluir = true })
                    NkTextoAjuda("Concluir e arquivar mantêm o dinheiro na caixinha. Para excluir, a caixinha precisa estar zerada.")
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

/** As operações de uma caixinha (v0.8, regra 6G.2). */
/** v0.9 (item 7): nome de uma parte ("Sem origem definida" quando não tem conta). */
fun nomeDaParte(v: com.hkawzz.nkote.dominio.VisaoFinanceira?, contaId: String?): String =
    contaId?.takeIf { it.isNotEmpty() }?.let { v?.conta(it)?.nome ?: "Conta removida" } ?: "Sem origem definida"

enum class OperacaoCaixinha { GUARDAR, RETIRAR, MOVER, RENDIMENTO, MOVER_PARTE }

/** Guardar, retirar, mover ou lançar rendimento, em tela cheia (sem diálogos com listas). */
@Composable
fun OperacaoObjetivoTela(id: String, tipo: OperacaoCaixinha, aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val visao by container.visao.collectAsState()
    val escopo = rememberCoroutineScope()
    var valorTexto by rememberSaveable { mutableStateOf("0,00") }
    var data by rememberSaveable { mutableStateOf(container.relogio.hoje().toString()) }
    var conta by rememberSaveable { mutableStateOf<String?>(null) }
    var destino by rememberSaveable { mutableStateOf<String?>(null) }
    // v0.9 (item 7): a parte (conta de origem; "" = Sem origem definida).
    var parte by rememberSaveable { mutableStateOf<String?>(null) }
    var erro by rememberSaveable { mutableStateOf<String?>(null) }
    var salvando by rememberSaveable { mutableStateOf(false) }
    val s = visao?.objetivos?.situacao(id)
    val titulo = when (tipo) {
        OperacaoCaixinha.GUARDAR -> "Guardar"
        OperacaoCaixinha.RETIRAR -> "Retirar"
        OperacaoCaixinha.MOVER -> "Mover para outro objetivo"
        OperacaoCaixinha.RENDIMENTO -> "Rendimento"
        OperacaoCaixinha.MOVER_PARTE -> "Entre partes"
    }
    Subtela(titulo = titulo, aoVoltar = aoVoltar) { modifier ->
        val v = visao ?: return@Subtela
        if (s == null) return@Subtela
        // Só contas próprias: a caixinha nunca é meio de pagamento, e benefício não se guarda.
        val proprias = v.contas.filter { it.natureza == com.hkawzz.nkote.dominio.Natureza.PROPRIO && !it.arquivada }
        LaunchedEffect(Unit) { if (conta == null) conta = proprias.firstOrNull()?.id }
        val sem = com.hkawzz.nkote.dados.ObjetivoServico.SEM_ORIGEM
        val partes = v.objetivos.partesDe(id)
        val comSaldo = partes.filter { it.valor > 0 }
        fun chave(c: String?) = c ?: sem
        fun opcaoDaParte(p: com.hkawzz.nkote.dominio.SaldoDeParte) = Opcao(chave(p.contaId), nomeDaParte(v, p.contaId), "Na parte " + Dinheiro.formatar(p.valor))
        LaunchedEffect(tipo) {
            if (parte == null) parte = when (tipo) {
                OperacaoCaixinha.RENDIMENTO -> v.objetivos.objetivo(id)?.contaCaixinhaId?.let { container.objetivos.partePadraoDoRendimento(it) } ?: sem
                OperacaoCaixinha.GUARDAR -> null
                else -> comSaldo.firstOrNull()?.let { chave(it.contaId) }
            }
            // Retirar: o destino padrão é a conta da própria parte.
            if (tipo == OperacaoCaixinha.RETIRAR) parte?.takeIf { p -> proprias.any { it.id == p } }?.let { conta = it }
            if (tipo == OperacaoCaixinha.MOVER_PARTE) destino = proprias.firstOrNull { it.id != parte }?.id
        }
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.l, vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            Text("${s.objetivo.nome} · na caixinha ${Dinheiro.formatar(s.guardado)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when (tipo) {
                    OperacaoCaixinha.GUARDAR -> "Transfere da conta escolhida para a caixinha. A conta fica com menos; o Saldo disponível diminui e o Guardado aumenta."
                    OperacaoCaixinha.RETIRAR -> "Transfere da caixinha para a conta escolhida. Não é despesa. Se for gastar, lance a despesa depois, por essa conta."
                    OperacaoCaixinha.MOVER -> "Transfere para a caixinha de outro objetivo. O Saldo disponível não muda."
                    OperacaoCaixinha.RENDIMENTO -> "O que a caixinha rendeu (ex.: o CDI do banco). Entra como receita, na categoria Rendimentos."
                    OperacaoCaixinha.MOVER_PARTE -> "Muda de onde vem o dinheiro guardado (ex.: você depositou no banco o dinheiro físico). O total da caixinha e o saldo das contas não mudam."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            CampoDinheiro("Valor", valorTexto, { valorTexto = it })
            // v0.9 (item 7): de qual parte sai (retirar, mover, entre partes) ou em qual entra (rendimento).
            when (tipo) {
                OperacaoCaixinha.RETIRAR, OperacaoCaixinha.MOVER, OperacaoCaixinha.MOVER_PARTE -> CampoEscolha(
                    "Da parte", comSaldo.map { opcaoDaParte(it) }, parte,
                    { p -> parte = p; if (tipo == OperacaoCaixinha.RETIRAR && proprias.any { it.id == p }) conta = p },
                )
                OperacaoCaixinha.RENDIMENTO -> CampoEscolha(
                    "Em qual parte",
                    partes.filter { it.contaId != null }.map { opcaoDaParte(it) } + Opcao(sem, "Sem origem definida"),
                    parte, { parte = it },
                )
                OperacaoCaixinha.GUARDAR -> Unit
            }
            when (tipo) {
                OperacaoCaixinha.GUARDAR, OperacaoCaixinha.RETIRAR -> CampoEscolha(
                    if (tipo == OperacaoCaixinha.GUARDAR) "De qual conta" else "Para qual conta",
                    proprias.map { Opcao(it.id, it.nome, "Saldo " + Dinheiro.formatar(v.saldo(it.id))) },
                    conta, { conta = it },
                )
                OperacaoCaixinha.MOVER -> CampoEscolha(
                    "Para",
                    v.objetivos.ativos.filter { it.objetivo.id != id }.map { Opcao(it.objetivo.id, it.objetivo.nome, "Na caixinha ${Dinheiro.formatar(it.guardado)}") },
                    destino, { destino = it },
                )
                OperacaoCaixinha.MOVER_PARTE -> CampoEscolha(
                    "Para a parte",
                    proprias.filter { it.id != parte }.map { Opcao(it.id, it.nome) },
                    destino, { destino = it },
                )
                OperacaoCaixinha.RENDIMENTO -> Unit
            }
            CampoData("Data", LocalDate.parse(data), { data = it.toString() })
            erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            NkBotaoPrimario("Confirmar", {
                val valor = Dinheiro.ler(valorTexto) ?: 0
                val d = LocalDate.parse(data)
                salvando = true
                escopo.launch {
                    val r = when (tipo) {
                        OperacaoCaixinha.GUARDAR -> conta?.let { container.objetivos.guardar(id, it, valor, d) } ?: Resultado.Falha("Escolha a conta de onde sai o dinheiro.")
                        OperacaoCaixinha.RETIRAR -> conta?.let { container.objetivos.retirar(id, it, valor, d, parte) } ?: Resultado.Falha("Escolha a conta para onde o dinheiro volta.")
                        OperacaoCaixinha.MOVER -> destino?.let { container.objetivos.mover(id, it, valor, d, parte) } ?: Resultado.Falha("Escolha o objetivo de destino.")
                        OperacaoCaixinha.RENDIMENTO -> container.objetivos.rendimento(id, valor, d, parte ?: sem)
                        OperacaoCaixinha.MOVER_PARTE -> {
                            val de = parte
                            val para = destino
                            if (de == null || para == null) Resultado.Falha("Escolha as duas partes.") else container.objetivos.moverEntrePartes(id, de, para, valor, d)
                        }
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