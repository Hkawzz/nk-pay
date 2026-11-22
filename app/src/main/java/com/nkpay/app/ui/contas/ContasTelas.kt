package com.nkpay.app.ui.contas

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
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
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
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.TipoBeneficio
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.componentes.BotaoAdicionar
import com.nkpay.app.ui.componentes.CampoData
import com.nkpay.app.ui.componentes.CampoDinheiro
import com.nkpay.app.ui.componentes.CampoTexto
import com.nkpay.app.ui.componentes.CartaoSecao
import com.nkpay.app.ui.componentes.CoresDeConta
import com.nkpay.app.ui.componentes.DialogoConfirmacao
import com.nkpay.app.ui.componentes.EscolhaSegmentada
import com.nkpay.app.ui.componentes.EstadoVazio
import com.nkpay.app.ui.componentes.LinhaConta
import com.nkpay.app.ui.componentes.LinhaMovimentacao
import com.nkpay.app.ui.componentes.Opcao
import com.nkpay.app.ui.componentes.RotuloDeSecao
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.componentes.lembrarSnackbar
import com.nkpay.app.ui.movimentacoes.listaAgrupadaPorDia
import com.nkpay.app.ui.nkViewModel
import com.nkpay.app.ui.theme.EstiloValor
import com.nkpay.app.ui.theme.RosaDespesa

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
            EstadoVazio(
                icone = Icons.Filled.AccountBox,
                titulo = "Nenhuma conta ainda",
                texto = "Cadastre o Pix, o dinheiro físico, os bancos e também o VA e o VR, cada um com o saldo de hoje.",
                modifier = modifier,
                acao = { Button(onClick = aoNovaConta) { Text("Cadastrar conta") } },
            )
            return@Subtela
        }
        val ativas = v.contas.filter { !it.arquivada }
        val grupos = listOf(
            "Dinheiro próprio" to ativas.filter { it.natureza == Natureza.PROPRIO },
            "Benefícios" to ativas.filter { it.natureza == Natureza.BENEFICIO },
            "Arquivadas" to v.contas.filter { it.arquivada },
        )
        LazyColumn(modifier.fillMaxSize()) {
            for ((titulo, contas) in grupos) {
                if (contas.isEmpty()) continue
                item(key = "t-$titulo") {
                    val total = contas.sumOf { v.saldo(it.id) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RotuloDeSecao(titulo, Modifier.weight(1f))
                        if (titulo != "Arquivadas") {
                            Text(
                                Dinheiro.formatar(total),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 24.dp, top = 12.dp),
                            )
                        }
                    }
                }
                item(key = "g-$titulo") {
                    CartaoSecao {
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CampoTexto("Nome", vm.nome, { vm.nome = it }, erro = vm.erroNome, ajuda = "Ex.: Pix, Dinheiro, Banco X, VA")

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

            Text("Cor (opcional)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SeletorDeCor(vm.cor) { vm.cor = it }

            vm.erroGeral?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.salvar(aoVoltar) },
                enabled = !vm.salvando,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) { Text(if (vm.editando) "Salvar alterações" else "Criar conta") }
            Spacer(Modifier.height(24.dp))
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
                if (marcada) Icon(Icons.Filled.Check, contentDescription = "Selecionada", tint = Color.Black, modifier = Modifier.size(18.dp))
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
            IconButton(onClick = aoEditar) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções") }
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
                CartaoSecao(cor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Saldo atual", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            Dinheiro.formatar(saldo),
                            style = EstiloValor.destaque,
                            color = if (saldo < 0) RosaDespesa else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
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
