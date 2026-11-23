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
    limite = limiteCentavos,
    diaFechamento = diaFechamento,
    diaVencimento = diaVencimento,
)

fun CategoriaEntidade.paraDominio() = Categoria(
    id = id,
    nome = nome,
    codigoSistema = codigoSistema,
    arquivada = arquivadaEm != null,
    icone = icone,
    cor = cor,
)

fun CompraEntidade.paraDominio() = com.nkpay.app.dominio.Compra(
    id = id, contaCreditoId = contaCreditoId, descricao = descricao, categoriaId = categoriaId,
    dataCompra = LocalDate.parse(dataCompra), valorTotal = valorTotalCentavos, totalParcelas = totalParcelas,
    movimentacaoId = movimentacaoId, anteriorAoApp = anteriorAoApp,
    valorOriginal = valorOriginalCentavos ?: valorTotalCentavos,
)

fun PendenciaLevadaEntidade.paraDominio() = com.nkpay.app.dominio.PendenciaLevada(id, obrigacaoId, java.time.YearMonth.parse(mes))

fun EmprestimoEntidade.paraDominio() = com.nkpay.app.dominio.Emprestimo(
    id = id, contaId = contaId, descricao = descricao, categoriaId = categoriaId, valorOriginal = valorOriginalCentavos,
    dataContratacao = LocalDate.parse(dataContratacao), contaPropriaId = contaPropriaId, anteriorAoApp = anteriorAoApp,
    jurosInformados = jurosInformadosCentavos, movimentacaoContratacaoId = movimentacaoContratacaoId, movimentacaoJurosId = movimentacaoJurosId,
)

fun PagamentoFaturaEntidade.paraDominio() = com.nkpay.app.dominio.PagamentoFatura(
    id = id, contaCreditoId = contaCreditoId, mesReferencia = java.time.YearMonth.parse(mesReferencia),
    movimentacaoId = movimentacaoId, valor = valorCentavos,
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
