package com.nkpay.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Título grande no topo das telas principais. */
@Composable
fun TituloDeTela(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** Tela (ou bloco) vazia bem acabada, sem dados falsos. */
@Composable
fun EstadoVazio(
    icone: ImageVector,
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    acao: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(titulo, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (acao != null) {
                Spacer(Modifier.height(20.dp))
                acao()
            }
        }
    }
}

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
                title = { Text(titulo) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
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

/** Botão "+" redondo, roxo, padrão do app. */
@Composable
fun BotaoAdicionar(descricao: String, aoClicar: () -> Unit) {
    FloatingActionButton(
        onClick = aoClicar,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(Icons.Filled.Add, contentDescription = descricao)
    }
}

/** Card de superfície com cantos arredondados, para agrupar linhas. */
@Composable
fun CartaoSecao(
    modifier: Modifier = Modifier,
    cor: Color = MaterialTheme.colorScheme.surfaceContainer,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = cor),
        shape = MaterialTheme.shapes.medium,
        content = conteudo,
    )
}

/** Rótulo pequeno acima de um grupo ("Dinheiro próprio", "Hoje"...). */
@Composable
fun RotuloDeSecao(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
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
