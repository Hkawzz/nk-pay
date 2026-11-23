package com.nkpay.app.ui.ajuda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nkpay.app.ui.theme.Espaco
import com.nkpay.app.ui.theme.Lilas

/**
 * O "?" da ajuda (v0.5): um círculo pequeno ao lado do indicador. O toque abre a
 * explicação (o que é · como é calculado · exemplo · o que não é).
 * A área de toque tem 32 dp, embora o círculo desenhado seja menor.
 */
@Composable
fun NkAjuda(chave: ChaveAjuda, modifier: Modifier = Modifier, cor: androidx.compose.ui.graphics.Color = Lilas) {
    var aberta by rememberSaveable { mutableStateOf(false) }
    val texto = TextosDeAjuda.de(chave)
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable { aberta = true }
            .semantics { contentDescription = "Ajuda: ${texto.titulo}"; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(cor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text("?", color = cor, fontSize = 12.sp, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (aberta) {
        // Uma folha simples, só com texto (componente padrão, igual em todas as versões do Android).
        AlertDialog(
            onDismissRequest = { aberta = false },
            title = { Text(texto.titulo) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Espaco.s)) {
                    Bloco("O que é", texto.oQueE)
                    texto.calculo?.let { Bloco("Como é calculado", it) }
                    texto.exemplo?.let { Bloco("Exemplo", it) }
                    texto.oQueNaoE?.let { Bloco("O que não é", it) }
                }
            },
            confirmButton = { TextButton(onClick = { aberta = false }) { Text("Entendi") } },
        )
    }
}

@Composable
private fun Bloco(rotulo: String, texto: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(rotulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}
