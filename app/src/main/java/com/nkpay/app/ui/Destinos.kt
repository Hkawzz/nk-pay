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
    const val BACKUP = "mais/backup"

    // Telas com parâmetros (o padrão fica junto da função que monta a rota)
    const val CONTA = "mais/conta/{id}"
    fun conta(id: String) = "mais/conta/$id"

    const val REGISTRO_DETALHE = "mais/registro/{id}"
    fun registroDetalhe(id: String) = "mais/registro/$id"

    const val CONTA_FORM ="mais/conta-form?id={id}"
    fun contaForm(id: String? = null) = if (id == null) "mais/conta-form" else "mais/conta-form?id=$id"

    /** Formulário de movimentação. Sem id: nova. `conta`: conta já escolhida. */
    const val MOVIMENTACAO_FORM = "movimentacao-form?id={id}&conta={conta}"
    fun movimentacaoForm(id: String? = null, conta: String? = null): String {
        val params = listOfNotNull(id?.let { "id=$it" }, conta?.let { "conta=$it" })
        return if (params.isEmpty()) "movimentacao-form" else "movimentacao-form?" + params.joinToString("&")
    }
}

/** As 5 abas da barra inferior, na ordem em que aparecem. */
enum class Aba(val rota: String, val rotulo: String, val icone: ImageVector) {
    INICIO(Rotas.INICIO, "Início", Icons.Filled.Home),
    MOVIMENTACOES(Rotas.MOVIMENTACOES, "Movimentações", Icons.AutoMirrored.Filled.List),
    A_PAGAR(Rotas.A_PAGAR, "A pagar", Icons.Filled.DateRange),
    OBJETIVOS(Rotas.OBJETIVOS, "Objetivos", Icons.Filled.Star),
    MAIS(Rotas.MAIS, "Mais", Icons.Filled.Menu),
}
