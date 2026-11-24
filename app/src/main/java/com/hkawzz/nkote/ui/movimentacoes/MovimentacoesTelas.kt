package com.hkawzz.nkote.ui.movimentacoes

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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Delete
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
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.dominio.VisaoFinanceira
import com.hkawzz.nkote.ui.Formatos
import com.hkawzz.nkote.ui.LocalContainer
import com.hkawzz.nkote.ui.componentes.BotaoAdicionar
import com.hkawzz.nkote.ui.componentes.CampoData
import com.hkawzz.nkote.ui.componentes.CampoDinheiro
import com.hkawzz.nkote.ui.componentes.CampoEscolha
import com.hkawzz.nkote.ui.componentes.NkCampo
import com.hkawzz.nkote.ui.componentes.NkCard
import com.hkawzz.nkote.ui.componentes.DialogoConfirmacao
import com.hkawzz.nkote.ui.componentes.EscolhaSegmentada
import com.hkawzz.nkote.ui.componentes.NkEstadoVazio
import com.hkawzz.nkote.ui.componentes.Opcao
import com.hkawzz.nkote.ui.componentes.Subtela
import com.hkawzz.nkote.ui.componentes.NkBotaoPrimario
import com.hkawzz.nkote.ui.componentes.NkValorRotulado
import com.hkawzz.nkote.ui.theme.Espaco
import com.hkawzz.nkote.ui.componentes.NkTituloTela
import com.hkawzz.nkote.ui.nkViewModel
import com.hkawzz.nkote.ui.theme.EstiloValor
import com.hkawzz.nkote.ui.theme.RosaDespesa
import com.hkawzz.nkote.ui.theme.VerdeEntrada

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
            item { NkTituloTela("Movimentações") }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = vm::mesAnterior) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Mês anterior")
                    }
                    Text(
                        Formatos.mes(vm.mes),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = vm::proximoMes) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Próximo mês")
                    }
                }
            }
            item {
                NkCard {
                    Row(Modifier.padding(start = Espaco.l, end = Espaco.l, top = Espaco.l, bottom = Espaco.xs)) {
                        NkValorRotulado("Entradas", totais.entradas, Modifier.weight(1f), corValor = VerdeEntrada)
                        NkValorRotulado("Despesas", totais.despesas, Modifier.weight(1f), corValor = RosaDespesa)
                    }
                    Text(
                        "Transferências e ajustes não entram nesses totais.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Espaco.l, end = Espaco.l, bottom = Espaco.m),
                    )
                }
            }
            item { Filtros(vm, v) }
            if (lista.isEmpty()) {
                item {
                    NkEstadoVazio(
                        icone = Icons.AutoMirrored.Rounded.List,
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
        horizontalArrangement = Arrangement.spacedBy(Espaco.xs),
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
                opcoes = listOf(Opcao<String?>(null, "Todas as contas")) + v.contas.filter { !it.ehContaDeTerceiros }.map { Opcao<String?>(it.id, it.nome) },
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
                    Icon(Icons.Rounded.Delete, contentDescription = "Excluir")
                }
            }
        },
    ) { modifier ->
        val v = visao ?: return@Subtela
        if (!vm.carregado) return@Subtela
        if (v.contas.isEmpty()) {
            NkEstadoVazio(
                icone = Icons.AutoMirrored.Rounded.List,
                titulo = "Cadastre uma conta primeiro",
                texto = "Toda movimentação acontece em uma conta. Vá em Mais → Contas e carteiras.",
                modifier = modifier,
            )
            return@Subtela
        }

        // Contas e categorias arquivadas não aparecem nas escolhas, exceto a que já está selecionada.
        // Contas de crédito não aceitam lançamentos avulsos (ADR 0017).
        fun opcoesDeConta(selecionada: String?) = v.contas
            .filter { (!it.arquivada && !it.ehContaDeTerceiros) || it.id == selecionada }
            .map { c ->
                val natureza = if (c.natureza == Natureza.BENEFICIO) "Benefício · " else ""
                Opcao(c.id, c.nome, natureza + "Saldo " + Dinheiro.formatar(v.saldo(c.id)))
            }
        val opcoesCategoria = v.categorias
            .filter { !it.arquivada || it.id == vm.categoriaId }
            .map {
                val a = com.hkawzz.nkote.dominio.AparenciaSugerida.NEUTRA
                Opcao(it.id, it.nome, icone = com.hkawzz.nkote.ui.icones.IconesNk.de(it.icone ?: a.icone), cor = it.cor ?: a.cor)
            }

        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espaco.l, vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            if (vm.ehPagamentoDeConta) {
                Text(
                    "Este lançamento é o pagamento de uma conta. Para mudar ou excluir, abra a conta em A pagar e desfaça o pagamento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
                    NkCampo("Descrição", vm.descricao, { vm.descricao = it }, ajuda = "Opcional. Ex.: Mercado, Salário")
                }
                TipoMovimentacao.TRANSFERENCIA -> {
                    CampoEscolha("De (origem)", opcoesDeConta(vm.contaId), vm.contaId, { vm.contaId = it })
                    CampoEscolha("Para (destino)", opcoesDeConta(vm.contaDestinoId).filter { it.valor != vm.contaId }, vm.contaDestinoId, { vm.contaDestinoId = it })
                    CampoDinheiro("Valor", vm.valorTexto, { vm.valorTexto = it }, erro = vm.erroValor)
                    CampoData("Data", vm.data, { vm.data = it })
                    NkCampo("Descrição", vm.descricao, { vm.descricao = it }, ajuda = "Opcional")
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
                            ajuda = "Positivo aumenta o saldo. Toque em +/− para diminuir.",
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
                    NkCampo("Motivo", vm.descricao, { vm.descricao = it }, ajuda = "Ex.: conferência com o extrato")
                }
            }

            vm.erroGeral?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(Espaco.xxs))
            NkBotaoPrimario(if (vm.editando) "Salvar alterações" else "Lançar", { vm.salvar(aoVoltar) }, habilitado = !vm.salvando)
            Spacer(Modifier.height(Espaco.l))
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
