package com.nkpay.app.ui.categorias

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.Categoria
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.componentes.BotaoAdicionar
import com.nkpay.app.ui.componentes.CampoTexto
import com.nkpay.app.ui.componentes.CartaoSecao
import com.nkpay.app.ui.componentes.RotuloDeSecao
import com.nkpay.app.ui.componentes.Subtela
import kotlinx.coroutines.launch

/**
 * Categorias: criar, renomear e arquivar. Uma categoria arquivada some das novas
 * escolhas, mas continua aparecendo nas movimentações antigas.
 * A categoria de sistema ("Encargos financeiros") não pode ser alterada.
 */
@Composable
fun CategoriasTela(aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val visao by container.visao.collectAsState()
    val escopo = rememberCoroutineScope()

    var criando by rememberSaveable { mutableStateOf(false) }
    var editandoId by rememberSaveable { mutableStateOf<String?>(null) }

    Subtela(
        titulo = "Categorias",
        aoVoltar = aoVoltar,
        botaoFlutuante = { BotaoAdicionar("Nova categoria") { criando = true } },
    ) { modifier ->
        val v = visao ?: return@Subtela
        val ativas = v.categorias.filter { !it.arquivada }
        val arquivadas = v.categorias.filter { it.arquivada }
        LazyColumn(modifier.fillMaxSize()) {
            item {
                Text(
                    "As categorias dizem o que foi comprado ou recebido. Elas não dependem da forma de pagamento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            item { RotuloDeSecao("Em uso") }
            item { ListaCategorias(ativas) { editandoId = it.id } }
            if (arquivadas.isNotEmpty()) {
                item { RotuloDeSecao("Arquivadas") }
                item { ListaCategorias(arquivadas) { editandoId = it.id } }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (criando) {
        DialogoNome(
            titulo = "Nova categoria",
            inicial = "",
            textoConfirmar = "Criar",
            aoConfirmar = { nome, mostrarErro ->
                escopo.launch {
                    when (val r = container.categorias.criar(nome)) {
                        is Resultado.Sucesso -> criando = false
                        is Resultado.Falha -> mostrarErro(r.mensagem)
                    }
                }
            },
            aoCancelar = { criando = false },
        )
    }

    val editando = editandoId?.let { id -> visao?.categoria(id) }
    if (editando != null) {
        if (editando.ehDoSistema) {
            AlertDialog(
                onDismissRequest = { editandoId = null },
                title = { Text(editando.nome) },
                text = { Text("Categoria do sistema. Será usada para juros, multas, tarifas e IOF, por isso não pode ser renomeada nem arquivada.") },
                confirmButton = { TextButton(onClick = { editandoId = null }) { Text("Entendi") } },
            )
        } else {
            DialogoNome(
                titulo = "Editar categoria",
                inicial = editando.nome,
                textoConfirmar = "Salvar",
                aoConfirmar = { nome, mostrarErro ->
                    escopo.launch {
                        when (val r = container.categorias.renomear(editando.id, nome)) {
                            is Resultado.Sucesso -> editandoId = null
                            is Resultado.Falha -> mostrarErro(r.mensagem)
                        }
                    }
                },
                aoCancelar = { editandoId = null },
                acaoExtra = if (editando.arquivada) "Desarquivar" else "Arquivar",
                aoAcaoExtra = {
                    escopo.launch {
                        container.categorias.arquivar(editando.id, arquivar = !editando.arquivada)
                        editandoId = null
                    }
                },
            )
        }
    }
}

@Composable
private fun ListaCategorias(categorias: List<Categoria>, aoClicar: (Categoria) -> Unit) {
    CartaoSecao {
        if (categorias.isEmpty()) {
            Text(
                "Nenhuma.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        categorias.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { aoClicar(c) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.nome, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (c.ehDoSistema) {
                    Icon(Icons.Filled.Lock, contentDescription = "Categoria do sistema", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DialogoNome(
    titulo: String,
    inicial: String,
    textoConfirmar: String,
    aoConfirmar: (nome: String, mostrarErro: (String) -> Unit) -> Unit,
    aoCancelar: () -> Unit,
    acaoExtra: String? = null,
    aoAcaoExtra: () -> Unit = {},
) {
    var nome by rememberSaveable { mutableStateOf(inicial) }
    var erro by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = {
            Column {
                CampoTexto("Nome", nome, { nome = it; erro = null }, erro = erro)
                if (acaoExtra != null) {
                    TextButton(onClick = aoAcaoExtra) { Text(acaoExtra) }
                    Text(
                        "Arquivada, a categoria some das novas escolhas e continua nas movimentações antigas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { aoConfirmar(nome) { erro = it } }) { Text(textoConfirmar) } },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )
}
