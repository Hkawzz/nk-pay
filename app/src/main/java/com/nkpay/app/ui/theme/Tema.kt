package com.nkpay.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Tema escuro, único na v0.1. Não há tema claro nem cores dinâmicas (Material You),
// para a identidade visual ser sempre a mesma.
private val EsquemaEscuro = darkColorScheme(
    primary = Lilas,
    onPrimary = Fundo,
    primaryContainer = Roxo,
    onPrimaryContainer = LilasClaro,
    secondary = Lilas,
    onSecondary = Fundo,
    secondaryContainer = Roxo,
    onSecondaryContainer = LilasClaro,
    tertiary = VerdeEntrada,
    background = Fundo,
    onBackground = TextoPrincipal,
    surface = Fundo,
    onSurface = TextoPrincipal,
    surfaceVariant = SuperficieElevada,
    onSurfaceVariant = TextoSecundario,
    surfaceContainerLowest = Fundo,
    surfaceContainerLow = Superficie,
    surfaceContainer = Superficie,
    surfaceContainerHigh = SuperficieElevada,
    surfaceContainerHighest = SuperficieElevada,
    outline = Contorno,
    outlineVariant = SuperficieElevada,
    error = Erro,
    onError = Fundo,
)

private val Formas = Shapes(
    small = Raio.formaPequena,
    medium = Raio.formaMedia,
    large = Raio.formaGrande,
)

@Composable
fun NkPayTema(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaEscuro,
        typography = Tipografia,
        shapes = Formas,
        content = content,
    )
}
