package com.nkpay.app.ui.telas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nkpay.app.BuildConfig
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.ui.Rotas
import com.nkpay.app.ui.componentes.EstadoVazio
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.componentes.TituloDeTela
import com.nkpay.app.ui.theme.EstiloValor

// Telas simples: A pagar e Objetivos (chegam em versões futuras), Mais, Sobre e o aviso "em breve".

@Composable
fun APagarTela() {
    Column(Modifier.fillMaxSize()) {
        TituloDeTela("A pagar")
        EstadoVazio(
            icone = Icons.Filled.DateRange,
            titulo = "Chega na próxima versão",
            texto = "Contas fixas, parcelas e pendências vão aparecer aqui.",
        )
    }
}

@Composable
fun ObjetivosTela() {
    Column(Modifier.fillMaxSize()) {
        TituloDeTela("Objetivos")
        EstadoVazio(
            icone = Icons.Filled.Star,
            titulo = "Chega na próxima versão",
            texto = "Reservas para os seus objetivos, separadas do disponível.",
        )
    }
}

private data class ItemMais(val rota: String, val titulo: String, val icone: ImageVector)

private val itensMais = listOf(
    ItemMais(Rotas.CONTAS, "Contas e carteiras", Icons.Filled.AccountBox),
    ItemMais(Rotas.CATEGORIAS, "Categorias", Icons.Filled.ShoppingCart),
    ItemMais(Rotas.BACKUP, "Backup e restauração", Icons.Filled.Lock),
    ItemMais(Rotas.REGISTRO, "Registro de alterações", Icons.Filled.Edit),
    ItemMais(Rotas.SOBRE, "Sobre", Icons.Filled.Info),
)

@Composable
fun MaisTela(aoAbrir: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TituloDeTela("Mais")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.medium,
        ) {
            itensMais.forEachIndexed { indice, item ->
                if (indice > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { aoAbrir(item.rota) }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(item.icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        item.titulo,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun EmBreveSubtela(titulo: String, aoVoltar: () -> Unit) {
    Subtela(titulo = titulo, aoVoltar = aoVoltar) { modifier ->
        EstadoVazio(
            icone = Icons.Filled.Build,
            titulo = "Chega na v0.1.0",
            texto = "Esta tela está sendo construída.",
            modifier = modifier,
        )
    }
}

@Composable
fun SobreTela(aoVoltar: () -> Unit) {
    Subtela(titulo = "Sobre", aoVoltar = aoVoltar) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("NK Pay", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Versão ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Finanças pessoais, 100% offline. Seus dados ficam só neste aparelho: " +
                    "o app não usa internet, não tem login e não envia nada para lugar nenhum.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
