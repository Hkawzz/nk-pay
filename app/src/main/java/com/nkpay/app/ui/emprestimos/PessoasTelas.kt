package com.nkpay.app.ui.emprestimos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nkpay.app.dados.ContaRepositorio
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.componentes.DialogoConfirmacao
import com.nkpay.app.ui.componentes.IconeDaConta
import com.nkpay.app.ui.componentes.NkBotaoPrimario
import com.nkpay.app.ui.componentes.NkBotaoSecundario
import com.nkpay.app.ui.componentes.NkCabecalhoSecao
import com.nkpay.app.ui.componentes.NkCampo
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.NkDivisor
import com.nkpay.app.ui.componentes.NkListItem
import com.nkpay.app.ui.componentes.NkTextoAjuda
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.theme.Espaco
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// v0.6 (A5, regra 6B.10, ADR 0027): pessoas e instituições dos empréstimos.

private val NATUREZAS_DE_PESSOA = setOf(Natureza.EMPRESTIMO_RECEBIDO, Natureza.EMPRESTIMO_CONCEDIDO)

/** Empréstimos → Pessoas: a lista, com "Mostrar arquivadas". */
@Composable
fun PessoasTela(aoVoltar: () -> Unit, aoAbrir: (String) -> Unit) {
    val visao by LocalContainer.current.visao.collectAsState()
    var mostrarArquivadas by rememberSaveable { mutableStateOf(false) }
    Subtela(
        titulo = "Pessoas",
        aoVoltar = aoVoltar,
        acoes = { TextButton(onClick = { mostrarArquivadas = !mostrarArquivadas }) { Text(if (mostrarArquivadas) "Ocultar arquivadas" else "Mostrar arquivadas") } },
    ) { modifier ->
        val v = visao ?: return@Subtela
        val pessoas = v.contas.filter { it.natureza in NATUREZAS_DE_PESSOA }
        LazyColumn(modifier.fillMaxSize()) {
            for ((titulo, lista) in listOf("Pessoas e instituições" to pessoas.filter { !it.arquivada }, "Arquivadas" to if (mostrarArquivadas) pessoas.filter { it.arquivada } else emptyList())) {
                if (lista.isEmpty()) continue
                item(key = titulo) {
                    NkCabecalhoSecao(titulo)
                    NkCard {
                        lista.forEachIndexed { i, p ->
                            if (i > 0) NkDivisor()
                            NkListItem(
                                titulo = p.nome,
                                subtitulo = if (p.natureza == Natureza.EMPRESTIMO_RECEBIDO) "Eu devo" else "Me deve",
                                inicio = { IconeDaConta(p) },
                                aoClicar = { aoAbrir(p.id) },
                            )
                        }
                    }
                }
            }
            if (pessoas.none { !it.arquivada }) item { NkTextoAjuda("Nenhuma pessoa ativa. As pessoas são criadas ao cadastrar um empréstimo.") }
            item { Spacer(Modifier.height(Espaco.l)) }
        }
    }
}

/** Uma pessoa: editar o nome, arquivar/desarquivar e excluir (só sem nada ligado). */
@Composable
fun PessoaTela(id: String, aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val visao by container.visao.collectAsState()
    val escopo = androidx.compose.runtime.rememberCoroutineScope()
    var nome by rememberSaveable { mutableStateOf<String?>(null) }
    var mensagem by rememberSaveable { mutableStateOf<String?>(null) }
    var oferecerArquivar by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmarExcluir by rememberSaveable { mutableStateOf(false) }
    val pessoa = visao?.conta(id)
    LaunchedEffect(pessoa?.id) { if (nome == null && pessoa != null) nome = pessoa.nome }
    suspend fun voltar() = withContext(Dispatchers.Main) { aoVoltar() }

    Subtela(titulo = pessoa?.nome ?: "Pessoa", aoVoltar = aoVoltar) { modifier ->
        if (pessoa == null) return@Subtela
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.l, vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            Text(if (pessoa.natureza == Natureza.EMPRESTIMO_RECEBIDO) "Você pegou emprestado desta pessoa ou instituição." else "Você emprestou para esta pessoa.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            NkCampo("Nome", nome.orEmpty(), { nome = it }, ajuda = "Os empréstimos continuam ligados a ela; só o nome muda.")
            NkBotaoPrimario("Salvar nome", {
                escopo.launch {
                    when (val r = container.contas.editar(pessoa.copy(nome = nome.orEmpty()))) {
                        is Resultado.Sucesso -> mensagem = "Nome salvo."
                        is Resultado.Falha -> mensagem = r.mensagem
                    }
                }
            }, habilitado = nome?.trim()?.let { it.isNotEmpty() && it != pessoa.nome } == true)
            NkBotaoSecundario(if (pessoa.arquivada) "Desarquivar" else "Arquivar", {
                escopo.launch {
                    when (val r = container.contas.arquivar(id, arquivar = !pessoa.arquivada)) {
                        is Resultado.Sucesso -> voltar()
                        is Resultado.Falha -> mensagem = r.mensagem
                    }
                }
            })
            NkBotaoSecundario("Excluir", { confirmarExcluir = true })
            mensagem?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            NkTextoAjuda("Excluir só é possível sem nenhum empréstimo ou movimentação. Arquivar tira das listas e guarda o histórico; só dá para arquivar sem nada a pagar ou a receber.")
        }
    }
    if (confirmarExcluir) DialogoConfirmacao(
        titulo = "Excluir “${pessoa?.nome}”?",
        texto = "Fica no Registro de alterações.",
        textoConfirmar = "Excluir",
        destrutivo = true,
        aoConfirmar = {
            confirmarExcluir = false
            escopo.launch {
                when (val r = container.contas.excluir(id)) {
                    ContaRepositorio.ResultadoExclusao.Excluida, ContaRepositorio.ResultadoExclusao.NaoEncontrada -> voltar()
                    is ContaRepositorio.ResultadoExclusao.TemVinculos -> oferecerArquivar = r.motivo
                    is ContaRepositorio.ResultadoExclusao.TemMovimentacoes -> oferecerArquivar = "Tem ${r.quantidade} movimentação(ões)."
                }
            }
        },
        aoCancelar = { confirmarExcluir = false },
    )
    oferecerArquivar?.let { motivo ->
        DialogoConfirmacao(
            titulo = "Não dá para excluir",
            texto = motivo,
            textoConfirmar = "Arquivar",
            aoConfirmar = {
                oferecerArquivar = null
                escopo.launch {
                    when (val r = container.contas.arquivar(id)) {
                        is Resultado.Sucesso -> voltar()
                        is Resultado.Falha -> mensagem = r.mensagem
                    }
                }
            },
            aoCancelar = { oferecerArquivar = null },
        )
    }
}
