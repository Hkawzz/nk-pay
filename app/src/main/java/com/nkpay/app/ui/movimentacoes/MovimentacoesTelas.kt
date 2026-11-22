package com.nkpay.app.ui.movimentacoes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
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
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.Saldos
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.dominio.VisaoFinanceira
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.componentes.BotaoAdicionar
import com.nkpay.app.ui.componentes.CampoData
import com.nkpay.app.ui.componentes.CampoDinheiro
import com.nkpay.app.ui.componentes.CampoEscolha
import com.nkpay.app.ui.componentes.CampoTexto
import com.nkpay.app.ui.componentes.CartaoSecao
import com.nkpay.app.ui.componentes.DialogoConfirmacao
import com.nkpay.app.ui.componentes.EscolhaSegmentada
import com.nkpay.app.ui.componentes.EstadoVazio
import com.nkpay.app.ui.componentes.Opcao
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.componentes.TituloDeTela
import com.nkpay.app.ui.nkViewModel
import com.nkpay.app.ui.theme.EstiloValor
import com.nkpay.app.ui.theme.RosaDespesa
import com.nkpay.app.ui.theme.VerdeEntrada

/** Aba Movimentações: seletor de mês, filtros, totais do mês e lista por dia. */
@Composable
fun MovimentacoesTela(aoAbrir: (String) -> Unit, aoNova: () -> Unit) {
    val vm = nkViewModel { MovimentacoesViewModel(it) }
    val visao by vm.visao.collectAsState()
    val hoje = LocalContainer.current.relogio.hoje()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = { BotaoAdicionar("Nova movimentação", aoNova) },
    ) { espaco ->
        val v = visao ?: return@Scaffold
        val doMes = v.movimentacoes.filter { java.time.YearMonth.from(it.data) == vm.mes }
        val totais = Saldos.totaisDoMes(doMes, vm.mes)
        val lista = vm.filtrar(v)

        LazyColumn(Modifier.padding(espaco).fillMaxSize()) {
            item { TituloDeTela("Movimentações") }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = vm::mesAnterior) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mês anterior")
                    }
                    Text(
                        Formatos.mes(vm.mes),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = vm::proximoMes) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo mês")
                    }
                }
            }
            item {
                CartaoSecao {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Entradas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Dinheiro.formatar(totais.entradas), style = EstiloValor.medio, color = VerdeEntrada)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Despesas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Dinheiro.formatar(totais.despesas), style = EstiloValor.medio, color = RosaDespesa)
                        }
                    }
                    Text(
                        "Transferências e ajustes não entram nesses totais.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
            item { Filtros(vm, v) }
            if (lista.isEmpty()) {
                item {
                    EstadoVazio(
                        icone = Icons.AutoMirrored.Filled.List,
                        titulo = if (doMes.isEmpty()) "Nada lançado neste mês" else "Nada com esses filtros",
                        texto = if (v.contas.isEmpty()) "Cadastre uma conta em Mais → Contas e carteiras para começar." else "Toque no + para lançar.",
                        modifier = Modifier.height(280.dp),
                    )
                }
            } else {
                listaAgrupadaPorDia(lista, v, hoje, contaReferencia = vm.filtroConta, aoAbrir = aoAbrir)
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun Filtros(vm: MovimentacoesViewModel, v: VisaoFinanceira) {
    val tipos = listOf<Pair<TipoMovimentacao?, String>>(
        null to "Todos",
        TipoMovimentacao.ENTRADA to "Entradas",
        TipoMovimentacao.DESPESA to "Despesas",
        TipoMovimentacao.TRANSFERENCIA to "Transferências",
        TipoMovimentacao.AJUSTE to "Ajustes",
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((tipo, rotulo) in tipos) {
            FilterChip(
                selected = vm.filtroTipo == tipo,
                onClick = { vm.filtroTipo = tipo },
                label = { Text(rotulo) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
    if (v.contas.isNotEmpty()) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            CampoEscolha(
                rotulo = "Conta",
                opcoes = listOf(Opcao<String?>(null, "Todas as contas")) + v.contas.map { Opcao<String?>(it.id, it.nome) },
                selecionado = vm.filtroConta,
                aoEscolher = { vm.filtroConta = it },
            )
        }
    }
}

/** Formulário de movimentação. Os campos mudam conforme o tipo escolhido. */
@Composable
fun MovimentacaoFormTela(id: String?, contaInicial: String?, aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val vm = nkViewModel(chave = "mov-form-$id-$contaInicial") { MovimentacaoFormViewModel(it, id, contaInicial) }
    val visao by container.visao.collectAsState()
    var confirmarExclusao by rememberSaveable { mutableStateOf(false) }

    Subtela(
        titulo = if (vm.editando) "Editar movimentação" else "Nova movimentação",
        aoVoltar = aoVoltar,
        acoes = {
            if (vm.editando) {
                IconButton(onClick = { confirmarExclusao = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir")
                }
            }
        },
    ) { modifier ->
        val v = visao ?: return@Subtela
        if (!vm.carregado) return@Subtela
        if (v.contas.isEmpty()) {
            EstadoVazio(
                icone = Icons.AutoMirrored.Filled.List,
                titulo = "Cadastre uma conta primeiro",
                texto = "Toda movimentação acontece em uma conta. Vá em Mais → Contas e carteiras.",
                modifier = modifier,
            )
            return@Subtela
        }

        // Contas e categorias arquivadas não aparecem nas escolhas, exceto a que já está selecionada.
        fun opcoesDeConta(selecionada: String?) = v.contas
            .filter { !it.arquivada || it.id == selecionada }
            .map { c ->
                val natureza = if (c.natureza == Natureza.BENEFICIO) "Benefício · " else ""
                Opcao(c.id, c.nome, natureza + "Saldo " + Dinheiro.formatar(v.saldo(c.id)))
            }
        val opcoesCategoria = v.categorias
            .filter { !it.arquivada || it.id == vm.categoriaId }
            .map { Opcao(it.id, it.nome) }

        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EscolhaSegmentada(
                // "Transf." para caber 4 opções em telas estreitas; a explicação abaixo usa o nome completo.
                opcoes = TipoMovimentacao.entries.map { Opcao(it, if (it == TipoMovimentacao.TRANSFERENCIA) "Transf." else it.rotulo) },
                selecionado = vm.tipo,
                aoEscolher = vm::mudarTipo,
            )
            Text(explicacaoDoTipo(vm.tipo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when (vm.tipo) {
                TipoMovimentacao.ENTRADA, TipoMovimentacao.DESPESA -> {
                    CampoEscolha("Conta", opcoesDeConta(vm.contaId), vm.contaId, { vm.contaId = it })
                    CampoDinheiro("Valor", vm.valorTexto, { vm.valorTexto = it }, erro = vm.erroValor)
                    CampoData("Data", vm.data, { vm.data = it })
                    CampoEscolha(
                        "Categoria",
                        listOf(Opcao<String?>(null, "Sem categoria")) + opcoesCategoria.map { Opcao<String?>(it.valor, it.rotulo) },
                        vm.categoriaId,
                        { vm.categoriaId = it },
                        textoVazio = "Sem categoria",
                    )
                    CampoTexto("Descrição", vm.descricao, { vm.descricao = it }, ajuda = "Opcional. Ex.: Mercado, Salário")
                }
                TipoMovimentacao.TRANSFERENCIA -> {
                    CampoEscolha("De (origem)", opcoesDeConta(vm.contaId), vm.contaId, { vm.contaId = it })
                    CampoEscolha("Para (destino)", opcoesDeConta(vm.contaDestinoId).filter { it.valor != vm.contaId }, vm.contaDestinoId, { vm.contaDestinoId = it })
                    CampoDinheiro("Valor", vm.valorTexto, { vm.valorTexto = it }, erro = vm.erroValor)
                    CampoData("Data", vm.data, { vm.data = it })
                    CampoTexto("Descrição", vm.descricao, { vm.descricao = it }, ajuda = "Opcional")
                }
                TipoMovimentacao.AJUSTE -> {
                    CampoEscolha("Conta", opcoesDeConta(vm.contaId), vm.contaId, { vm.contaId = it })
                    val conta = vm.contaId
                    if (conta != null) {
                        val base = vm.saldoBase(v, conta)
                        Text(
                            "Saldo calculado pelo app: ${Dinheiro.formatar(base)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    EscolhaSegmentada(
                        opcoes = listOf(Opcao(ModoAjuste.NOVO_SALDO, "Novo saldo"), Opcao(ModoAjuste.DIFERENCA, "Diferença")),
                        selecionado = vm.modoAjuste,
                        aoEscolher = { vm.modoAjuste = it },
                    )
                    if (vm.modoAjuste == ModoAjuste.NOVO_SALDO) {
                        CampoDinheiro(
                            "Saldo real da conta",
                            vm.novoSaldoTexto,
                            { vm.novoSaldoTexto = it },
                            erro = vm.erroValor,
                            ajuda = "O que o banco ou a carteira mostra agora.",
                            permitirNegativo = true,
                        )
                    } else {
                        CampoDinheiro(
                            "Diferença",
                            vm.valorTexto,
                            { vm.valorTexto = it },
                            erro = vm.erroValor,
                            ajuda = "Positivo aumenta o saldo; com “-” na frente, diminui.",
                            permitirNegativo = true,
                        )
                    }
                    vm.valorDoAjuste(v)?.let { ajuste ->
                        Text(
                            "O ajuste será de ${Dinheiro.formatarComSinal(ajuste)}",
                            style = EstiloValor.lista,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    CampoData("Data", vm.data, { vm.data = it })
                    CampoTexto("Motivo", vm.descricao, { vm.descricao = it }, ajuda = "Ex.: conferência com o extrato")
                }
            }

            vm.erroGeral?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { vm.salvar(aoVoltar) },
                enabled = !vm.salvando,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) { Text(if (vm.editando) "Salvar alterações" else "Lançar") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmarExclusao) {
        DialogoConfirmacao(
            titulo = "Excluir movimentação?",
            texto = "Ela some das listas e dos saldos. O registro fica guardado no Registro de alterações.",
            textoConfirmar = "Excluir",
            destrutivo = true,
            aoConfirmar = { confirmarExclusao = false; vm.excluir(aoVoltar) },
            aoCancelar = { confirmarExclusao = false },
        )
    }
}

private fun explicacaoDoTipo(t: TipoMovimentacao): String = when (t) {
    TipoMovimentacao.ENTRADA -> "Dinheiro que entra (salário, VA recebido). Entra no total de entradas do mês."
    TipoMovimentacao.DESPESA -> "Consumo real. Entra no total de despesas do mês, mesmo se paga com VA/VR."
    TipoMovimentacao.TRANSFERENCIA -> "Transferência: o dinheiro só muda de conta. Não é entrada nem despesa."
    TipoMovimentacao.AJUSTE -> "Corrige o saldo para bater com o real. Não é entrada nem despesa."
}
