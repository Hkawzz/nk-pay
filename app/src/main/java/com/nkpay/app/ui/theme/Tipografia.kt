package com.nkpay.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Tipografia = Typography()

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
}
