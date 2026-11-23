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
    const val CATEGORIA_FORM = "mais/categoria-form?id={id}"
    fun categoriaForm(id: String? = null) = if (id == null) "mais/categoria-form" else "mais/categoria-form?id=$id"
    // Cartões e crediários (v0.4)
    const val CARTOES = "mais/cartoes"
    const val CARTAO_FORM = "mais/cartao-form?id={id}"
    fun cartaoForm(id: String? = null) = if (id == null) "mais/cartao-form" else "mais/cartao-form?id=$id"
    const val CARTAO = "mais/cartao/{id}"
    fun cartao(id: String) = "mais/cartao/$id"
    const val COMPRA_FORM = "mais/compra-form?conta={conta}"
    fun compraForm(contaId: String) = "mais/compra-form?conta=$contaId"
    const val COMPRA = "mais/compra/{id}"
    fun compra(id: String) = "mais/compra/$id"
    const val PARCELA = "mais/parcela/{id}"
    fun parcela(id: String) = "mais/parcela/$id"
    const val ANALISES = "mais/analises"
    const val REGISTRO = "mais/registro"
    const val SOBRE = "mais/sobre"
    const val BACKUP = "mais/backup"
    const val CONTAS_FIXAS = "mais/contas-fixas"
    const val CONTA_FIXA_FORM = "mais/conta-fixa-form?id={id}"
    fun contaFixaForm(id: String? = null) = if (id == null) "mais/conta-fixa-form" else "mais/conta-fixa-form?id=$id"

    /** Fatura (cartão) ou parcelas do mês (crediário). Fora de Mais: abre também a partir de A pagar. */
    const val FATURA = "fatura/{conta}/{mes}"
    fun fatura(contaId: String, mes: java.time.YearMonth) = "fatura/$contaId/$mes"

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
