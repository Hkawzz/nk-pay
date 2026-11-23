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
import com.nkpay.app.ui.theme.Lilas
import com.nkpay.app.ui.theme.Tamanho

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
            .size(Tamanho.avatar)
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
    NkListItem(
        titulo = conta.nome,
        subtitulo = when {
            conta.arquivada -> "Arquivada"
            conta.natureza == Natureza.BENEFICIO -> conta.tipoBeneficio?.rotulo ?: "Benefício"
            else -> null
        },
        inicio = { IconeDaConta(conta) },
        fim = { NkValor(saldo) },
        aoClicar = aoClicar,
    )
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
    NkListItem(
        titulo = tituloDaMovimentacao(m, visao),
        subtitulo = detalheDaMovimentacao(m, visao),
        // v0.4: entradas e despesas mostram o ícone da categoria; transferências e ajustes, o da conta.
        inicio = {
            val conta = visao.conta(m.contaId)
            when {
                m.tipo.temCategoria -> IconeDaCategoria(visao.categoria(m.categoriaId))
                conta != null -> IconeDaConta(conta)
                else -> AvatarConta(null)
            }
        },
        fim = {
            when (m.tipo) {
                TipoMovimentacao.ENTRADA -> NkValor(m.valor, tipo = TipoValor.ENTRADA)
                TipoMovimentacao.DESPESA -> NkValor(m.valor, tipo = TipoValor.DESPESA)
                TipoMovimentacao.AJUSTE -> NkValor(m.valor, tipo = TipoValor.COM_SINAL)
                TipoMovimentacao.TRANSFERENCIA -> {
                    // Na tela de uma conta, mostra + ou − do ponto de vista dela; fora dela, neutro.
                    val efeito = contaReferencia?.let { Saldos.efeitoNaConta(m, it) }
                    if (efeito == null) {
                        NkValor(m.valor, cor = MaterialTheme.colorScheme.onSurface)
                    } else {
                        NkValor(efeito, tipo = TipoValor.COM_SINAL, cor = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        aoClicar = aoClicar,
    )
}
