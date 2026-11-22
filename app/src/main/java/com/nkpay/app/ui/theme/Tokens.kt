package com.nkpay.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// =====================================================================================
// Tokens de design: os valores que dão consistência visual ao app.
// As telas NÃO usam números soltos (16.dp, 24.dp...): usam estes nomes. Assim, ajustar
// o espaçamento ou os cantos do app inteiro é mudar uma linha aqui.
// =====================================================================================

/** Escala de espaçamento (múltiplos de 4). */
object Espaco {
    /** 4 dp: entre um texto e o seu subtítulo. */
    val xxs = 4.dp
    /** 8 dp: entre itens pequenos (chips, ícone e texto). */
    val xs = 8.dp
    /** 12 dp: entre campos de um formulário. */
    val s = 12.dp
    /** 16 dp: margem lateral dos cards e padding das linhas. */
    val m = 16.dp
    /** 24 dp: padding interno dos cards e margem de títulos. */
    val l = 24.dp
    /** 32 dp: separação entre blocos grandes. */
    val xl = 32.dp

    /** Espaço no fim das listas para o botão "+" não cobrir o último item. */
    val fimDeListaComBotao = 96.dp
}

/** Raios de canto. */
object Raio {
    val pequeno = 12.dp
    val medio = 16.dp
    val grande = 24.dp

    val formaPequena = RoundedCornerShape(pequeno)
    val formaMedia = RoundedCornerShape(medio)
    val formaGrande = RoundedCornerShape(grande)
    /** Totalmente arredondado: botões e chips. */
    val formaPilula = RoundedCornerShape(percent = 50)
}

/** Tamanhos de elementos repetidos. */
object Tamanho {
    /** Altura dos botões principais (área de toque confortável). */
    val botao = 52.dp
    /** Avatar redondo das contas. */
    val avatar = 40.dp
    /** Ícone padrão. */
    val icone = 24.dp
    /** Altura da barra de navegação inferior. */
    val barraInferior = 72.dp
}
