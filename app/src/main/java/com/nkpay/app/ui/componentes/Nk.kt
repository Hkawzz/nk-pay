package com.nkpay.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.ui.theme.Espaco
import com.nkpay.app.ui.theme.EstiloValor
import com.nkpay.app.ui.theme.Raio
import com.nkpay.app.ui.theme.RosaDespesa
import com.nkpay.app.ui.theme.Tamanho
import com.nkpay.app.ui.theme.VerdeEntrada

// =====================================================================================
// Componentes do NK Pay (prefixo "Nk"). Todas as telas — e principalmente as novas da
// v0.3 em diante — devem ser montadas com eles, para o visual ficar consistente.
// Eles usam só os tokens do tema (Espaco, Raio, Tamanho, EstiloValor, colorScheme).
// =====================================================================================

// ----------------------------------------------------------------- NkCard

enum class EstiloCard {
    /** Superfície padrão (#18181B). */
    PADRAO,
    /** Um tom acima (#27272A), para chamar um pouco mais de atenção. */
    ELEVADO,
    /** Roxo da marca, para o número principal da tela (ex.: Disponível). */
    DESTAQUE,
}

/** Card com cantos arredondados e as margens padrão do app. */
@Composable
fun NkCard(
    modifier: Modifier = Modifier,
    estilo: EstiloCard = EstiloCard.PADRAO,
    aoClicar: (() -> Unit)? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val cores = when (estilo) {
        EstiloCard.PADRAO -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        EstiloCard.ELEVADO -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        EstiloCard.DESTAQUE -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    val forma = if (estilo == EstiloCard.DESTAQUE) Raio.formaGrande else Raio.formaMedia
    val margem = modifier.fillMaxWidth().padding(horizontal = Espaco.m, vertical = Espaco.xs / 2 + 2.dp)
    if (aoClicar != null) {
        Card(onClick = aoClicar, modifier = margem, colors = cores, shape = forma, content = conteudo)
    } else {
        Card(modifier = margem, colors = cores, shape = forma, content = conteudo)
    }
}

/** Divisor fino entre linhas dentro de um card. */
@Composable
fun NkDivisor() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

// ----------------------------------------------------------------- NkValor

enum class TipoValor {
    /** Branco; vira rosa se for negativo (ex.: saldo). */
    NEUTRO,
    /** Verde com "+" (entrada). */
    ENTRADA,
    /** Rosa com "-" (despesa). */
    DESPESA,
    /** Cinza com o sinal explícito (ajuste). */
    COM_SINAL,
    /** Cinza, sem destaque (valores de apoio). */
    SECUNDARIO,
}

enum class TamanhoValor { DESTAQUE, MEDIO, LISTA, PEQUENO }

/** Valor em dinheiro: algarismos tabulares e cor conforme o tipo. Recebe centavos. */
@Composable
fun NkValor(
    centavos: Long,
    modifier: Modifier = Modifier,
    tipo: TipoValor = TipoValor.NEUTRO,
    tamanho: TamanhoValor = TamanhoValor.LISTA,
    cor: Color? = null,
) {
    val texto = when (tipo) {
        TipoValor.ENTRADA -> "+" + Dinheiro.formatar(centavos)
        TipoValor.DESPESA -> "-" + Dinheiro.formatar(centavos)
        TipoValor.COM_SINAL -> Dinheiro.formatarComSinal(centavos)
        TipoValor.NEUTRO, TipoValor.SECUNDARIO -> Dinheiro.formatar(centavos)
    }
    val corFinal = cor ?: when (tipo) {
        TipoValor.ENTRADA -> VerdeEntrada
        TipoValor.DESPESA -> RosaDespesa
        TipoValor.COM_SINAL, TipoValor.SECUNDARIO -> MaterialTheme.colorScheme.onSurfaceVariant
        TipoValor.NEUTRO -> if (centavos < 0) RosaDespesa else MaterialTheme.colorScheme.onSurface
    }
    Text(texto, modifier = modifier, style = estiloDoTamanho(tamanho), color = corFinal, maxLines = 1)
}

private fun estiloDoTamanho(t: TamanhoValor): TextStyle = when (t) {
    TamanhoValor.DESTAQUE -> EstiloValor.destaque
    TamanhoValor.MEDIO -> EstiloValor.medio
    TamanhoValor.LISTA -> EstiloValor.lista
    TamanhoValor.PEQUENO -> EstiloValor.pequeno
}

/** Rótulo pequeno em cima de um valor ("Entradas", "Reservado"...). */
@Composable
fun NkValorRotulado(
    rotulo: String,
    centavos: Long,
    modifier: Modifier = Modifier,
    tipo: TipoValor = TipoValor.NEUTRO,
    tamanho: TamanhoValor = TamanhoValor.MEDIO,
    corRotulo: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    corValor: Color? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Espaco.xxs / 2)) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = corRotulo)
        NkValor(centavos, tipo = tipo, tamanho = tamanho, cor = corValor)
    }
}

// ----------------------------------------------------------------- NkListItem

/**
 * Linha de lista: algo à esquerda (avatar/ícone), título e subtítulo, e algo à direita
 * (valor, seta). Altura mínima de 56 dp para o toque ser confortável.
 */
@Composable
fun NkListItem(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    corTitulo: Color = MaterialTheme.colorScheme.onSurface,
    corSubtitulo: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /** Linhas máximas: 1 nas listas comuns; mais em avisos que não podem ser cortados. */
    linhas: Int = 1,
    inicio: (@Composable () -> Unit)? = null,
    fim: (@Composable () -> Unit)? = null,
    aoClicar: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (aoClicar != null) Modifier.clickable(onClick = aoClicar) else Modifier)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = Espaco.m, vertical = Espaco.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inicio != null) {
            inicio()
            Spacer(Modifier.width(Espaco.s + 2.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, color = corTitulo, maxLines = linhas, overflow = TextOverflow.Ellipsis)
            if (subtitulo != null) {
                Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = corSubtitulo, maxLines = linhas, overflow = TextOverflow.Ellipsis)
            }
        }
        if (fim != null) {
            Spacer(Modifier.width(Espaco.xs))
            fim()
        }
    }
}

/** Ícone dentro de um círculo suave, para o início de uma linha. */
@Composable
fun NkIconeRedondo(icone: ImageVector, cor: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier.size(Tamanho.avatar).clip(CircleShape).background(cor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(Tamanho.icone - 2.dp))
    }
}

// ----------------------------------------------------------------- Botões

/** Ação principal da tela: largura total, roxo, 52 dp de altura. */
@Composable
fun NkBotaoPrimario(texto: String, aoClicar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    Button(
        onClick = aoClicar,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().height(Tamanho.botao),
        shape = Raio.formaPilula,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) { Text(texto, style = MaterialTheme.typography.labelLarge) }
}

/** Ação secundária: contorno, mesma altura do primário. */
@Composable
fun NkBotaoSecundario(texto: String, aoClicar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    OutlinedButton(
        onClick = aoClicar,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().height(Tamanho.botao),
        shape = Raio.formaPilula,
    ) { Text(texto, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
}

// ----------------------------------------------------------------- NkCampo

/** Campo de texto de uma linha, com ajuda ou erro embaixo. */
@Composable
fun NkCampo(
    rotulo: String,
    texto: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    erro: String? = null,
    ajuda: String? = null,
) {
    OutlinedTextField(
        value = texto,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = true,
        isError = erro != null,
        supportingText = (erro ?: ajuda)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        shape = Raio.formaPequena,
        modifier = modifier.fillMaxWidth(),
    )
}

// ----------------------------------------------------------------- Estrutura de tela

/** Título grande no topo das telas principais. */
@Composable
fun NkTituloTela(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = Espaco.l, end = Espaco.l, top = Espaco.l, bottom = Espaco.xs),
    )
}

/** Cabeçalho de um bloco ("Suas contas", "Hoje"), com um complemento opcional à direita. */
@Composable
fun NkCabecalhoSecao(
    texto: String,
    modifier: Modifier = Modifier,
    complemento: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = Espaco.l, end = Espaco.m, top = Espaco.m, bottom = Espaco.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        complemento?.invoke()
    }
}

/** Estado vazio bem acabado (ícone, título, texto e ação opcional), sem dados falsos. */
@Composable
fun NkEstadoVazio(
    icone: ImageVector,
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    acao: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize().padding(Espaco.xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(Espaco.l - 4.dp))
            Text(titulo, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Espaco.xs))
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (acao != null) {
                Spacer(Modifier.height(Espaco.l - 4.dp))
                acao()
            }
        }
    }
}

/** Texto de ajuda pequeno, em cinza, com as margens padrão. */
@Composable
fun NkTextoAjuda(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Espaco.l, vertical = Espaco.xs),
    )
}
