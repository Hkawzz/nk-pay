package com.nkpay.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// =====================================================================================
// Design system v2 (v0.4, ADR 0019). Evolução dos tokens da v0.3, sem reescrita:
// as telas continuam usando Espaco, Raio, Tamanho e os componentes Nk*.
// =====================================================================================

/** Níveis de superfície, do fundo para cima. */
object Superficies {
    val fundo = Fundo // #09090B
    val card = Superficie // #18181B
    val elevada = SuperficieElevada // #27272A
}

/** Raios do v2: cards com 20 dp e destaques com 24 dp. */
object RaioV2 {
    val card = 20.dp
    val destaque = 24.dp
    val icone = 12.dp
    val formaCard = RoundedCornerShape(card)
    val formaDestaque = RoundedCornerShape(destaque)
    val formaIcone = RoundedCornerShape(icone)
}

/** Opacidades usadas de forma repetida. */
object Opacidade {
    /** Fundo translúcido dos ícones na cor da categoria. */
    const val fundoIcone = 0.16f
    /** Brilho lilás discreto do card de destaque. */
    const val brilho = 0.30f
    /** Borda sutil dos destaques. */
    const val borda = 0.22f
}

/** Durações de animação (ms). */
object Duracao {
    const val curta = 150
    const val media = 220
}

/** Degradês. No máximo 2 elementos de destaque por tela (ADR 0019). */
object Degrade {
    val destaqueInicio = Color(0xFF4C1D95)
    val destaqueFim = Color(0xFF1E1B4B)

    /** Card de destaque: roxo da marca → índigo profundo, na diagonal. */
    val destaque: Brush = Brush.linearGradient(listOf(destaqueInicio, destaqueFim))

    /** Cartão de crédito (menos chamativo que o destaque). */
    val cartao: Brush = Brush.linearGradient(listOf(Color(0xFF2E1065), Color(0xFF18181B)))
}

/**
 * Cores das categorias e das contas (ARGB). As 12 têm contraste de pelo menos 3:1
 * com o card (#18181B), conferido no teste `CoresTest`.
 */
val PaletaNk: List<Long> = listOf(
    0xFFA78BFA, // lilás
    0xFFC084FC, // violeta
    0xFF60A5FA, // azul
    0xFF22D3EE, // ciano
    0xFF2DD4BF, // verde-água
    0xFF4ADE80, // verde
    0xFFA3E635, // lima
    0xFFFACC15, // amarelo
    0xFFFB923C, // laranja
    0xFFF87171, // vermelho
    0xFFF472B6, // rosa
    0xFFA1A1AA, // cinza
)

/** Contraste e ajuste de cores personalizadas (WCAG 2.1). */
object Contraste {
    private fun canal(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    fun luminancia(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * canal(r) + 0.7152 * canal(g) + 0.0722 * canal(b)
    }

    fun razao(a: Long, b: Long): Double {
        val la = luminancia(a)
        val lb = luminancia(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * Clareia a cor (misturando com branco, em passos de 5%) até ter pelo menos [minimo]
     * de contraste com o [fundo]. Uma cor que já tem contraste volta igual.
     */
    fun ajustar(argb: Long, fundo: Long = 0xFF18181B, minimo: Double = 3.0): Long {
        var atual = argb or 0xFF000000
        var passo = 0
        while (razao(atual, fundo) < minimo && passo < 20) {
            passo++
            val t = passo * 0.05
            fun mistura(c: Long) = (c + (255 - c) * t).toLong().coerceIn(0, 255)
            val r = mistura((argb shr 16) and 0xFF)
            val g = mistura((argb shr 8) and 0xFF)
            val b = mistura(argb and 0xFF)
            atual = 0xFF000000 or (r shl 16) or (g shl 8) or b
        }
        return atual
    }
}
