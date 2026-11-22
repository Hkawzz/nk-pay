package com.nkpay.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/** Rotas de navegação. Cada string identifica uma tela. */
object Rotas {
    const val INICIO = "inicio"
    const val MOVIMENTACOES = "movimentacoes"
    const val A_PAGAR = "a-pagar"
    const val OBJETIVOS = "objetivos"
    const val MAIS = "mais"

    // Telas dentro de "Mais"
    const val CONTAS = "mais/contas"
    const val CATEGORIAS = "mais/categorias"
    const val REGISTRO = "mais/registro"
    const val SOBRE = "mais/sobre"
}

/** As 5 abas da barra inferior, na ordem em que aparecem. */
enum class Aba(val rota: String, val rotulo: String, val icone: ImageVector) {
    INICIO(Rotas.INICIO, "Início", Icons.Filled.Home),
    MOVIMENTACOES(Rotas.MOVIMENTACOES, "Movimentações", Icons.AutoMirrored.Filled.List),
    A_PAGAR(Rotas.A_PAGAR, "A pagar", Icons.Filled.DateRange),
    OBJETIVOS(Rotas.OBJETIVOS, "Objetivos", Icons.Filled.Star),
    MAIS(Rotas.MAIS, "Mais", Icons.Filled.Menu),
}
