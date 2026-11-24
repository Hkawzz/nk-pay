package com.hkawzz.nkote.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.ui.theme.Degrade
import com.hkawzz.nkote.ui.theme.Espaco
import com.hkawzz.nkote.ui.theme.EstiloValor
import com.hkawzz.nkote.ui.theme.Lilas
import com.hkawzz.nkote.ui.theme.Opacidade
import com.hkawzz.nkote.ui.theme.RaioV2
import com.hkawzz.nkote.ui.theme.RosaDespesa
import com.hkawzz.nkote.ui.theme.Tamanho

// =====================================================================================
// Componentes do design system v2 (v0.4, ADR 0019).
//
// Efeitos: o brilho lilás é um degradê radial desenhado atrás do conteúdo, igual em
// todas as versões do Android (8 a 17). A sombra colorida só aparece no Android 9+;
// nas versões antigas, o próprio sistema desenha uma sombra neutra, e a borda lilás
// sutil mantém o destaque. Não há desfoque de fundo (sem a biblioteca Haze).
// =====================================================================================

/**
 * O destaque da tela (no máximo 2 por tela): degradê #4C1D95 → #1E1B4B, cantos de
 * 24 dp, brilho lilás no canto superior direito e borda sutil.
 */
@Composable
fun NkCardDestaque(
    modifier: Modifier = Modifier,
    aoClicar: (() -> Unit)? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val forma = RaioV2.formaDestaque
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Espaco.m, vertical = Espaco.xs / 2 + 2.dp)
            .shadow(12.dp, forma, ambientColor = Lilas, spotColor = Lilas)
            .clip(forma)
            .background(Degrade.destaque)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Lilas.copy(alpha = Opacidade.brilho), Color.Transparent),
                        center = Offset(size.width * 0.9f, 0f),
                        radius = size.width * 0.6f,
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(size.width * 0.9f, 0f),
                )
            }
            .border(1.dp, Lilas.copy(alpha = Opacidade.borda), forma)
            .then(if (aoClicar != null) Modifier.clickable(onClick = aoClicar) else Modifier),
        content = conteudo,
    )
}

/** Ícone num quadrado arredondado com fundo translúcido na cor (categorias e contas). */
@Composable
fun NkIconeComFundo(icone: ImageVector, cor: Color, modifier: Modifier = Modifier, tamanho: Dp = Tamanho.avatar) {
    Box(
        modifier.size(tamanho).clip(RaioV2.formaIcone).background(cor.copy(alpha = Opacidade.fundoIcone)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(tamanho * 0.55f))
    }
}

/**
 * Valor em dinheiro que NUNCA é cortado: a fonte diminui até caber (ex.: R$ 12.345,67
 * num card estreito da grade).
 */
@Composable
fun NkValorAjustavel(
    centavos: Long,
    modifier: Modifier = Modifier,
    estilo: TextStyle = EstiloValor.medio,
    cor: Color = if (centavos < 0) RosaDespesa else MaterialTheme.colorScheme.onSurface,
    alinhamento: TextAlign = TextAlign.Start,
    prefixo: String = "",
) {
    BasicText(
        prefixo + Dinheiro.formatar(centavos),
        modifier = modifier,
        style = estilo.copy(color = cor, textAlign = alinhamento),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = estilo.fontSize, stepSize = 0.5.sp),
    )
}

/** Card pequeno de uma conta (grade da Início): ícone, nome e saldo. */
@Composable
fun NkCardConta(
    nome: String,
    saldo: Long,
    icone: ImageVector,
    cor: Color,
    modifier: Modifier = Modifier,
    compacto: Boolean = false,
    aoClicar: () -> Unit,
) {
    Column(
        modifier
            .clip(RaioV2.formaCard)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = aoClicar)
            .padding(if (compacto) Espaco.s else Espaco.m),
        verticalArrangement = Arrangement.spacedBy(if (compacto) Espaco.xxs else Espaco.xs),
    ) {
        NkIconeComFundo(icone, cor, tamanho = if (compacto) 32.dp else 36.dp)
        Spacer(Modifier.height(Espaco.xxs))
        Text(
            nome,
            style = if (compacto) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        NkValorAjustavel(saldo, estilo = if (compacto) EstiloValor.lista else EstiloValor.medio)
    }
}

/** Cartão de crédito visual: nome, devido e (se houver) a barra do limite usado. */
@Composable
fun NkCartaoCredito(
    nome: String,
    subtitulo: String,
    devido: Long,
    limite: Long?,
    modifier: Modifier = Modifier,
    aoClicar: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Espaco.m, vertical = Espaco.xs / 2 + 2.dp)
            .clip(RaioV2.formaCard)
            .background(Degrade.cartao)
            .border(1.dp, Lilas.copy(alpha = Opacidade.borda), RaioV2.formaCard)
            .then(if (aoClicar != null) Modifier.clickable(onClick = aoClicar) else Modifier)
            .padding(Espaco.l - 4.dp),
        verticalArrangement = Arrangement.spacedBy(Espaco.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(nome, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(com.hkawzz.nkote.ui.icones.IconesNk.de("cartao"), contentDescription = null, tint = Lilas)
        }
        Text("Devido", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NkValorAjustavel(devido, estilo = EstiloValor.medio, cor = MaterialTheme.colorScheme.onSurface)
        if (limite != null && limite > 0) {
            val usado = (devido.toFloat() / limite).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RaioV2.formaIcone).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Box(Modifier.fillMaxWidth(usado).height(6.dp).clip(RaioV2.formaIcone).background(Lilas))
            }
            Text(
                "Limite disponível ${Dinheiro.formatar(limite - devido)} de ${Dinheiro.formatar(limite)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
