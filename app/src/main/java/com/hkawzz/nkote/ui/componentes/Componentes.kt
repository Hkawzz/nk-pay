package com.hkawzz.nkote.ui.componentes

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hkawzz.nkote.ui.theme.Raio

// Estrutura das telas internas, botão "+" e diálogos. Os blocos visuais (cards, listas,
// valores, títulos, estados vazios) ficam em Nk.kt.

/** Estrutura das telas internas: barra superior com voltar, ações e botão flutuante opcionais. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Subtela(
    titulo: String,
    aoVoltar: () -> Unit,
    acoes: @Composable RowScope.() -> Unit = {},
    botaoFlutuante: (@Composable () -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
    conteudo: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        // O Scaffold externo (com a barra inferior) já trata as margens do sistema.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        floatingActionButton = { botaoFlutuante?.invoke() },
        topBar = {
            TopAppBar(
                title = { Text(titulo, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = acoes,
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { espaco ->
        conteudo(Modifier.padding(espaco))
    }
}

/** Botão "+" do app: cantos arredondados, roxo. */
@Composable
fun BotaoAdicionar(descricao: String, aoClicar: () -> Unit) {
    FloatingActionButton(
        onClick = aoClicar,
        shape = Raio.formaMedia,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = descricao)
    }
}

/** Diálogo de confirmação simples. */
@Composable
fun DialogoConfirmacao(
    titulo: String,
    texto: String,
    textoConfirmar: String,
    aoConfirmar: () -> Unit,
    aoCancelar: () -> Unit,
    destrutivo: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(onClick = aoConfirmar) {
                Text(textoConfirmar, color = if (destrutivo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )
}

/** Lembra um SnackbarHostState (mensagens rápidas no rodapé). */
@Composable
fun lembrarSnackbar(): SnackbarHostState = remember { SnackbarHostState() }
