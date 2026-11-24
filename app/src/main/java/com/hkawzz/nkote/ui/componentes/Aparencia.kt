package com.hkawzz.nkote.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hkawzz.nkote.dominio.AparenciaSugerida
import com.hkawzz.nkote.dominio.Categoria
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.ui.icones.IconesNk
import com.hkawzz.nkote.ui.theme.Contraste
import com.hkawzz.nkote.ui.theme.Espaco
import com.hkawzz.nkote.ui.theme.Lilas
import com.hkawzz.nkote.ui.theme.PaletaNk
import com.hkawzz.nkote.ui.theme.RaioV2

// Aparência (ícone e cor) de categorias e contas na interface (v0.4, ADR 0018).

fun corOuLilas(argb: Long?): Color = argb?.let { Color(it) } ?: Lilas

/** Ícone da categoria com fundo na cor dela. Sem categoria: o ícone neutro. */
@Composable
fun IconeDaCategoria(categoria: Categoria?, tamanho: androidx.compose.ui.unit.Dp = 40.dp) {
    val a = AparenciaSugerida.NEUTRA
    NkIconeComFundo(IconesNk.de(categoria?.icone ?: a.icone), Color(categoria?.cor ?: a.cor), tamanho = tamanho)
}

/** Aparência de uma conta: a escolhida, ou a padrão do tipo dela. */
fun aparenciaDaConta(conta: Conta): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    val padrao = AparenciaSugerida.daConta(conta.natureza, conta.tipoBeneficio)
    return IconesNk.de(conta.icone ?: padrao.icone) to Color(conta.cor ?: padrao.cor)
}

@Composable
fun IconeDaConta(conta: Conta, tamanho: androidx.compose.ui.unit.Dp = 40.dp) {
    val (icone, cor) = aparenciaDaConta(conta)
    NkIconeComFundo(icone, cor, tamanho = tamanho)
}

/**
 * Escolha de ícone e de cor: a grade de ícones, as 12 cores e uma cor personalizada
 * (código hexadecimal), clareada automaticamente até ficar legível no fundo escuro.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeletorDeAparencia(
    icone: String,
    cor: Long,
    aoMudarIcone: (String) -> Unit,
    aoMudarCor: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hex by remember { mutableStateOf("") }
    var avisoContraste by remember { mutableStateOf<String?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Espaco.s)) {
        Text("Ícone", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Espaco.xs), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
            for (i in IconesNk.todos) {
                val marcado = i.nome == icone
                Box(
                    Modifier
                        .clip(RaioV2.formaIcone)
                        .border(if (marcado) 2.dp else 0.dp, if (marcado) Color(cor) else Color.Transparent, RaioV2.formaIcone)
                        .clickable { aoMudarIcone(i.nome) }
                        .semantics { contentDescription = "Ícone ${i.rotulo}" },
                ) {
                    NkIconeComFundo(i.vetor, if (marcado) Color(cor) else MaterialTheme.colorScheme.onSurfaceVariant, tamanho = 40.dp)
                }
            }
        }
        Text("Cor", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (c in PaletaNk) {
                val marcada = c == cor
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (marcada) 3.dp else 0.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        .clickable { aoMudarCor(c); avisoContraste = null },
                    contentAlignment = Alignment.Center,
                ) {
                    if (marcada) Icon(Icons.Rounded.Check, contentDescription = "Selecionada", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            }
        }
        NkCampo(
            "Cor personalizada (ex.: 7F1D1D)",
            hex,
            { novo ->
                hex = novo.trim().removePrefix("#").take(6)
                val lido = hex.takeIf { it.length == 6 }?.toLongOrNull(16)
                if (lido != null) {
                    val original = 0xFF000000 or lido
                    val ajustada = Contraste.ajustar(original)
                    aoMudarCor(ajustada)
                    avisoContraste = if (ajustada != original) "A cor foi clareada para ficar legível no fundo escuro." else null
                }
            },
            ajuda = avisoContraste ?: "Opcional. Cores muito escuras são clareadas automaticamente.",
        )
    }
}
