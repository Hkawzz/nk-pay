package com.nkpay.app.dados

import com.nkpay.app.dominio.Categoria
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Movimentacao
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.TipoBeneficio
import com.nkpay.app.dominio.TipoMovimentacao
import java.time.LocalDate

// Conversão entre as linhas do banco (Entidade) e os modelos do domínio.
// Separar os dois deixa as regras financeiras livres de qualquer detalhe do banco.

fun ContaEntidade.paraDominio() = Conta(
    id = id,
    nome = nome,
    natureza = Natureza.valueOf(natureza),
    tipoBeneficio = tipoBeneficio?.let { TipoBeneficio.valueOf(it) },
    saldoInicial = saldoInicialCentavos,
    dataSaldoInicial = LocalDate.parse(dataSaldoInicial),
    icone = icone,
    cor = cor,
    arquivada = arquivadaEm != null,
)

fun CategoriaEntidade.paraDominio() = Categoria(
    id = id,
    nome = nome,
    codigoSistema = codigoSistema,
    arquivada = arquivadaEm != null,
)

fun MovimentacaoEntidade.paraDominio() = Movimentacao(
    id = id,
    tipo = TipoMovimentacao.valueOf(tipo),
    data = LocalDate.parse(data),
    valor = valorCentavos,
    contaId = contaId,
    contaDestinoId = contaDestinoId,
    categoriaId = categoriaId,
    descricao = descricao,
)
