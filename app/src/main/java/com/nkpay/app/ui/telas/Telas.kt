package com.nkpay.app.ui.telas

import com.nkpay.app.ui.theme.Espaco
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
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
import com.nkpay.app.ui.componentes.NkEstadoVazio
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.NkDivisor
import com.nkpay.app.ui.componentes.NkIconeRedondo
import com.nkpay.app.ui.componentes.NkListItem
import com.nkpay.app.ui.componentes.NkTituloTela
import com.nkpay.app.ui.theme.EstiloValor

// Telas simples: A pagar e Objetivos (chegam em versões futuras), Mais, Sobre e o aviso "em breve".

@Composable
fun APagarTela() {
    Column(Modifier.fillMaxSize()) {
        NkTituloTela("A pagar")
        NkEstadoVazio(
            icone = Icons.Rounded.DateRange,
            titulo = "Chega na próxima versão",
            texto = "Contas fixas, parcelas e pendências vão aparecer aqui.",
        )
    }
}

@Composable
fun ObjetivosTela() {
    Column(Modifier.fillMaxSize()) {
        NkTituloTela("Objetivos")
        NkEstadoVazio(
            icone = Icons.Rounded.Star,
            titulo = "Chega na próxima versão",
            texto = "Reservas para os seus objetivos, separadas do disponível.",
        )
    }
}

private data class ItemMais(val rota: String, val titulo: String, val icone: ImageVector)

private val itensMais = listOf(
    ItemMais(Rotas.CONTAS, "Contas e carteiras", Icons.Rounded.AccountBox),
    ItemMais(Rotas.CATEGORIAS, "Categorias", Icons.Rounded.ShoppingCart),
    ItemMais(Rotas.BACKUP, "Backup e restauração", Icons.Rounded.Lock),
    ItemMais(Rotas.REGISTRO, "Registro de alterações", Icons.Rounded.Edit),
    ItemMais(Rotas.SOBRE, "Sobre", Icons.Rounded.Info),
)

@Composable
fun MaisTela(aoAbrir: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        NkTituloTela("Mais")
        NkCard {
            itensMais.forEachIndexed { indice, item ->
                if (indice > 0) NkDivisor()
                NkListItem(
                    titulo = item.titulo,
                    inicio = { NkIconeRedondo(item.icone) },
                    fim = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    aoClicar = { aoAbrir(item.rota) },
                )
            }
        }
    }
}

@Composable
fun EmBreveSubtela(titulo: String, aoVoltar: () -> Unit) {
    Subtela(titulo = titulo, aoVoltar = aoVoltar) { modifier ->
        NkEstadoVazio(
            icone = Icons.Rounded.Build,
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
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
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
