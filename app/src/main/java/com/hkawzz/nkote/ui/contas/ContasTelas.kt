package com.hkawzz.nkote.ui.contas

import com.hkawzz.nkote.ui.componentes.EstiloCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.TipoBeneficio
import com.hkawzz.nkote.ui.Formatos
import com.hkawzz.nkote.ui.LocalContainer
import com.hkawzz.nkote.ui.componentes.BotaoAdicionar
import com.hkawzz.nkote.ui.componentes.CampoData
import com.hkawzz.nkote.ui.componentes.CampoDinheiro
import com.hkawzz.nkote.ui.componentes.NkCampo
import com.hkawzz.nkote.ui.componentes.NkCard
import com.hkawzz.nkote.ui.componentes.CoresDeConta
import com.hkawzz.nkote.ui.componentes.DialogoConfirmacao
import com.hkawzz.nkote.ui.componentes.EscolhaSegmentada
import com.hkawzz.nkote.ui.componentes.NkEstadoVazio
import com.hkawzz.nkote.ui.componentes.LinhaConta
import com.hkawzz.nkote.ui.componentes.LinhaMovimentacao
import com.hkawzz.nkote.ui.componentes.Opcao
import com.hkawzz.nkote.ui.componentes.NkCabecalhoSecao
import com.hkawzz.nkote.ui.componentes.Subtela
import com.hkawzz.nkote.ui.componentes.NkBotaoPrimario
import com.hkawzz.nkote.ui.componentes.NkValor
import com.hkawzz.nkote.ui.componentes.NkValorRotulado
import com.hkawzz.nkote.ui.componentes.TamanhoValor
import com.hkawzz.nkote.ui.componentes.TipoValor
import com.hkawzz.nkote.ui.theme.Espaco
import com.hkawzz.nkote.ui.componentes.lembrarSnackbar
import com.hkawzz.nkote.ui.movimentacoes.listaAgrupadaPorDia
import com.hkawzz.nkote.ui.nkViewModel
import com.hkawzz.nkote.ui.theme.EstiloValor
import com.hkawzz.nkote.ui.theme.RosaDespesa

/** Lista de contas: dinheiro próprio, benefícios e arquivadas, cada uma com o saldo atual. */
@Composable
fun ContasTela(
    aoVoltar: () -> Unit,
    aoAbrirConta: (String) -> Unit,
    aoNovaConta: () -> Unit,
) {
    val visao by LocalContainer.current.visao.collectAsState()
    Subtela(
        titulo = "Contas e carteiras",
        aoVoltar = aoVoltar,
        botaoFlutuante = { BotaoAdicionar("Nova conta", aoNovaConta) },
    ) { modifier ->
        val v = visao ?: return@Subtela
        if (v.contas.isEmpty()) {
            NkEstadoVazio(
                icone = Icons.Rounded.AccountBox,
                titulo = "Nenhuma conta ainda",
                texto = "Cadastre o Pix, o dinheiro físico, os bancos e também o VA e o VR, cada um com o saldo de hoje.",
                modifier = modifier,
                acao = { NkBotaoPrimario("Cadastrar conta", aoNovaConta) },
            )
            return@Subtela
        }
        val ativas = v.contas.filter { !it.arquivada }
        val grupos = listOf(
            "Dinheiro próprio" to ativas.filter { it.natureza == Natureza.PROPRIO },
            "Benefícios" to ativas.filter { it.natureza == Natureza.BENEFICIO },
            // Cartões e crediários ficam em Mais → Cartões e crediários.
            "Arquivadas" to v.contas.filter { it.arquivada && it.ehMeioDePagamento },
        )
        LazyColumn(modifier.fillMaxSize()) {
            for ((titulo, contas) in grupos) {
                if (contas.isEmpty()) continue
                item(key = "t-$titulo") {
                    val total = contas.sumOf { v.saldo(it.id) }
                    NkCabecalhoSecao(titulo) {
                        if (titulo != "Arquivadas") NkValor(total, tipo = TipoValor.SECUNDARIO, tamanho = TamanhoValor.PEQUENO)
                    }
                }
                item(key = "g-$titulo") {
                    NkCard {
                        contas.forEachIndexed { i, conta ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            LinhaConta(conta, v.saldo(conta.id)) { aoAbrirConta(conta.id) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) } // espaço para o botão "+"
        }
    }
}

/** Formulário para criar ou editar uma conta. */
@Composable
fun ContaFormTela(contaId: String?, aoVoltar: () -> Unit) {
    val vm = nkViewModel(chave = "conta-form-$contaId") { ContaFormViewModel(it, contaId) }
    Subtela(
        titulo = if (vm.editando) "Editar conta" else "Nova conta",
        aoVoltar = aoVoltar,
    ) { modifier ->
        if (!vm.carregado) return@Subtela
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espaco.l, vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            NkCampo("Nome", vm.nome, { vm.nome = it }, erro = vm.erroNome, ajuda = "Ex.: Pix, Dinheiro, Banco X, VA")

            Text("Tipo de conta", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            EscolhaSegmentada(
                opcoes = Natureza.disponiveisNaV01.map { Opcao(it, if (it == Natureza.PROPRIO) "Dinheiro próprio" else "Benefício") },
                selecionado = vm.natureza,
                aoEscolher = vm::mudarNatureza,
            )
            Text(
                if (vm.natureza == Natureza.PROPRIO) {
                    "Pix, dinheiro físico e bancos. Entra no Disponível."
                } else {
                    "VA, VR e similares. Fica em um bloco separado e nunca entra no Disponível."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (vm.natureza == Natureza.BENEFICIO) {
                EscolhaSegmentada(
                    opcoes = TipoBeneficio.entries.map { Opcao(it, if (it == TipoBeneficio.OUTRO) "Outro" else it.name) },
                    selecionado = vm.tipoBeneficio ?: TipoBeneficio.VA,
                    aoEscolher = { vm.tipoBeneficio = it },
                )
            }

            Spacer(Modifier.height(4.dp))
            CampoDinheiro(
                "Saldo inicial",
                vm.saldoTexto,
                { vm.saldoTexto = it },
                erro = vm.erroSaldo,
                ajuda = "Quanto tem na conta na data abaixo. Não conta como entrada.",
                permitirNegativo = true,
            )
            CampoData("Data do saldo inicial", vm.data, { vm.data = it })

            val padrao = com.hkawzz.nkote.dominio.AparenciaSugerida.daConta(vm.natureza, vm.tipoBeneficio)
            com.hkawzz.nkote.ui.componentes.SeletorDeAparencia(
                icone = vm.icone ?: padrao.icone,
                cor = vm.cor ?: padrao.cor,
                aoMudarIcone = { vm.icone = it },
                aoMudarCor = { vm.cor = it },
            )

            vm.erroGeral?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(Espaco.xs))
            NkBotaoPrimario(if (vm.editando) "Salvar alterações" else "Criar conta", { vm.salvar(aoVoltar) }, habilitado = !vm.salvando)
            Spacer(Modifier.height(Espaco.l))
        }
    }
}

@Composable
private fun SeletorDeCor(selecionada: Long?, aoEscolher: (Long?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in CoresDeConta) {
            val marcada = c == selecionada
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (marcada) 3.dp else 0.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    .clickable { aoEscolher(if (marcada) null else c) },
                contentAlignment = Alignment.Center,
            ) {
                if (marcada) Icon(Icons.Rounded.Check, contentDescription = "Selecionada", tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Tela de uma conta: saldo atual, informações e movimentações. */
@Composable
fun ContaDetalheTela(
    contaId: String,
    aoVoltar: () -> Unit,
    aoEditar: () -> Unit,
    aoAbrirMovimentacao: (String) -> Unit,
    aoNovaMovimentacao: () -> Unit,
) {
    val vm = nkViewModel(chave = "conta-$contaId") { ContaDetalheViewModel(it, contaId) }
    val visao by vm.visao.collectAsState()
    val hoje = LocalContainer.current.relogio.hoje()
    var menu by remember { mutableStateOf(false) }
    var confirmarExclusao by rememberSaveable { mutableStateOf(false) }
    val snackbar = lembrarSnackbar()
    val v = visao
    val conta = v?.conta(contaId)

    LaunchedEffect(vm.mensagem) {
        vm.mensagem?.let { snackbar.showSnackbar(it); vm.mensagem = null }
    }

    Subtela(
        titulo = conta?.nome ?: "Conta",
        aoVoltar = aoVoltar,
        snackbar = snackbar,
        acoes = {
            IconButton(onClick = aoEditar) { Icon(Icons.Rounded.Edit, contentDescription = "Editar") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Mais opções") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (conta?.arquivada == true) "Desarquivar" else "Arquivar") },
                    onClick = { menu = false; vm.alternarArquivada(conta?.arquivada == true) },
                )
                DropdownMenuItem(
                    text = { Text("Excluir", color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; confirmarExclusao = true },
                )
            }
        },
        botaoFlutuante = if (conta != null && !conta.arquivada) ({ BotaoAdicionar("Nova movimentação", aoNovaMovimentacao) }) else null,
    ) { modifier ->
        if (v == null) return@Subtela
        if (conta == null) {
            // A conta foi excluída (ou não existe): volta.
            LaunchedEffect(Unit) { aoVoltar() }
            return@Subtela
        }
        val saldo = v.saldo(contaId)
        val movs = v.movimentacoesDaConta(contaId)
        LazyColumn(modifier.fillMaxSize()) {
            item {
                NkCard(estilo = EstiloCard.ELEVADO) {
                    Column(Modifier.padding(Espaco.l)) {
                        NkValorRotulado("Saldo atual", saldo, tamanho = TamanhoValor.DESTAQUE)
                        Spacer(Modifier.height(Espaco.xs))
                        val tipo = if (conta.natureza == Natureza.BENEFICIO) {
                            "Benefício · ${conta.tipoBeneficio?.rotulo ?: ""} · fora do Disponível"
                        } else {
                            "Dinheiro próprio · entra no Disponível"
                        }
                        Text(tipo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Saldo inicial de ${Dinheiro.formatar(conta.saldoInicial)} em ${Formatos.data(conta.dataSaldoInicial)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (conta.arquivada) {
                            Text("Arquivada: não aparece nas novas escolhas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (movs.isEmpty()) {
                item {
                    Text(
                        "Nenhuma movimentação nesta conta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                listaAgrupadaPorDia(movs, v, hoje, contaReferencia = contaId, aoAbrir = aoAbrirMovimentacao)
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (confirmarExclusao) {
        DialogoConfirmacao(
            titulo = "Excluir conta?",
            texto = "A conta “${conta?.nome}” some das listas. O registro fica guardado no Registro de alterações.",
            textoConfirmar = "Excluir",
            destrutivo = true,
            aoConfirmar = { confirmarExclusao = false; vm.excluir(aoVoltar) },
            aoCancelar = { confirmarExclusao = false },
        )
    }
    vm.bloqueioExclusao?.let { quantidade ->
        AlertDialog(
            onDismissRequest = vm::fecharBloqueio,
            title = { Text("Não dá para excluir") },
            text = {
                Text(
                    "Esta conta tem $quantidade ${if (quantidade == 1) "movimentação" else "movimentações"}. " +
                        "Excluir apagaria o histórico. Você pode arquivar: ela some das novas escolhas e o histórico continua.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.alternarArquivada(false) }) { Text("Arquivar") } },
            dismissButton = { TextButton(onClick = vm::fecharBloqueio) { Text("Cancelar") } },
        )
    }
}
