package com.nkpay.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Star
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
    const val CONTAS_FIXAS = "mais/contas-fixas"
    const val CONTA_FIXA_FORM = "mais/conta-fixa-form?id={id}"
    fun contaFixaForm(id: String? = null) = if (id == null) "mais/conta-fixa-form" else "mais/conta-fixa-form?id=$id"

    const val PAGAR = "pagar/{id}"
    fun pagar(obrigacaoId: String) = "pagar/$obrigacaoId"

    /** Conta a pagar: sem id cria uma avulsa no mês informado. */
    const val OBRIGACAO_FORM = "obrigacao-form?id={id}&mes={mes}"
    fun obrigacaoForm(id: String? = null, mes: java.time.YearMonth? = null): String {
        val params = listOfNotNull(id?.let { "id=$it" }, mes?.let { "mes=$it" })
        return if (params.isEmpty()) "obrigacao-form" else "obrigacao-form?" + params.joinToString("&")
    }

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
    INICIO(Rotas.INICIO, "Início", Icons.Rounded.Home),
    MOVIMENTACOES(Rotas.MOVIMENTACOES, "Movimentações", Icons.AutoMirrored.Rounded.List),
    A_PAGAR(Rotas.A_PAGAR, "A pagar", Icons.Rounded.DateRange),
    OBJETIVOS(Rotas.OBJETIVOS, "Objetivos", Icons.Rounded.Star),
    MAIS(Rotas.MAIS, "Mais", Icons.Rounded.Menu),
}
