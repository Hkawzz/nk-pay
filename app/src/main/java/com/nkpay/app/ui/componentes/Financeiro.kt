package com.nkpay.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Movimentacao
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.Saldos
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.dominio.VisaoFinanceira
import com.nkpay.app.ui.theme.EstiloValor
import com.nkpay.app.ui.theme.Lilas
import com.nkpay.app.ui.theme.RosaDespesa
import com.nkpay.app.ui.theme.VerdeEntrada

/** Cores que a pessoa pode escolher para uma conta (ARGB). Todas com bom contraste no escuro. */
val CoresDeConta: List<Long> = listOf(
    0xFFA78BFA, // lilás
    0xFF60A5FA, // azul
    0xFF34D399, // verde
    0xFFFBBF24, // amarelo
    0xFFFB923C, // laranja
    0xFFF472B6, // rosa
    0xFF22D3EE, // ciano
    0xFFA1A1AA, // cinza
)

/** Círculo com a inicial da conta, na cor dela. */
@Composable
fun AvatarConta(conta: Conta?, modifier: Modifier = Modifier) {
    val cor = conta?.cor?.let { Color(it) } ?: Lilas
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(cor.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (conta?.nome?.firstOrNull()?.uppercase() ?: "?"),
            color = cor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}

/** Uma linha de conta com o saldo atual. */
@Composable
fun LinhaConta(conta: Conta, saldo: Long, aoClicar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = aoClicar)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarConta(conta)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(conta.nome, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detalhe = when {
                conta.arquivada -> "Arquivada"
                conta.natureza == Natureza.BENEFICIO -> conta.tipoBeneficio?.rotulo ?: "Benefício"
                else -> null
            }
            detalhe?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(
            Dinheiro.formatar(saldo),
            style = EstiloValor.lista,
            color = if (saldo < 0) RosaDespesa else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Título que a lista mostra para uma movimentação. */
fun tituloDaMovimentacao(m: Movimentacao, visao: VisaoFinanceira): String = when {
    m.descricao.isNotBlank() -> m.descricao
    m.tipo == TipoMovimentacao.TRANSFERENCIA -> "Transferência"
    m.tipo == TipoMovimentacao.AJUSTE -> "Ajuste de saldo"
    else -> visao.categoria(m.categoriaId)?.nome ?: m.tipo.rotulo
}

/** Linha de subtítulo: conta(s) e categoria. */
fun detalheDaMovimentacao(m: Movimentacao, visao: VisaoFinanceira): String {
    val conta = visao.conta(m.contaId)?.nome ?: "Conta excluída"
    return when (m.tipo) {
        TipoMovimentacao.TRANSFERENCIA -> "$conta → ${visao.conta(m.contaDestinoId)?.nome ?: "Conta excluída"}"
        TipoMovimentacao.AJUSTE -> "Ajuste · $conta"
        else -> listOfNotNull(visao.categoria(m.categoriaId)?.nome?.takeIf { m.descricao.isNotBlank() }, conta).joinToString(" · ")
    }
}

/**
 * Uma movimentação em lista.
 * @param contaReferencia se informada (tela da conta), a transferência mostra + ou − do ponto de vista dela.
 */
@Composable
fun LinhaMovimentacao(
    m: Movimentacao,
    visao: VisaoFinanceira,
    aoClicar: () -> Unit,
    contaReferencia: String? = null,
) {
    val (texto, cor) = when (m.tipo) {
        TipoMovimentacao.ENTRADA -> "+" + Dinheiro.formatar(m.valor) to VerdeEntrada
        TipoMovimentacao.DESPESA -> "-" + Dinheiro.formatar(m.valor) to RosaDespesa
        TipoMovimentacao.AJUSTE -> Dinheiro.formatarComSinal(m.valor) to MaterialTheme.colorScheme.onSurfaceVariant
        TipoMovimentacao.TRANSFERENCIA -> {
            val efeito = contaReferencia?.let { Saldos.efeitoNaConta(m, it) }
            when {
                efeito == null -> Dinheiro.formatar(m.valor) to MaterialTheme.colorScheme.onSurface
                efeito >= 0 -> "+" + Dinheiro.formatar(efeito) to MaterialTheme.colorScheme.onSurface
                else -> Dinheiro.formatar(efeito) to MaterialTheme.colorScheme.onSurface
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = aoClicar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarConta(visao.conta(m.contaId))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tituloDaMovimentacao(m, visao), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                detalheDaMovimentacao(m, visao),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(texto, style = EstiloValor.lista, color = cor)
    }
}
