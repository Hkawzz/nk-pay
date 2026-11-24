package com.hkawzz.nkote.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Hierarquia de texto do app (do maior para o menor):
 * - headlineMedium: título da tela ("Início", "Movimentações").
 * - titleLarge:     título de estado vazio e de diálogos.
 * - titleMedium:    título de card ou de bloco.
 * - titleSmall:     rótulo forte (ex.: "Disponível").
 * - bodyLarge:      texto principal das linhas de lista.
 * - bodyMedium:     texto corrido.
 * - bodySmall:      texto secundário (subtítulos, ajudas).
 * - labelLarge:     cabeçalho de seção e botões.
 * - labelMedium:    rótulos pequenos acima de valores.
 * Valores em dinheiro usam [EstiloValor].
 */
val Tipografia = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    )
}

/**
 * Estilos para valores em dinheiro. "tnum" liga os algarismos tabulares:
 * todos os dígitos têm a mesma largura, então colunas de valores ficam alinhadas
 * e o número não "pula" quando muda.
 */
object EstiloValor {
    val destaque = TextStyle(
        fontSize = 40.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
    )
    val medio = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )
    val lista = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )
    val pequeno = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )
}
