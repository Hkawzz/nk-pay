package com.hkawzz.nkote.dados

import com.hkawzz.nkote.dominio.Categoria
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.TipoBeneficio
import com.hkawzz.nkote.dominio.TipoMovimentacao
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

fun CompraEntidade.paraDominio() = com.hkawzz.nkote.dominio.Compra(
    id = id, contaCreditoId = contaCreditoId, descricao = descricao, categoriaId = categoriaId,
    dataCompra = LocalDate.parse(dataCompra), valorTotal = valorTotalCentavos, totalParcelas = totalParcelas,
    movimentacaoId = movimentacaoId, anteriorAoApp = anteriorAoApp,
    valorOriginal = valorOriginalCentavos ?: valorTotalCentavos,
)

fun ObjetivoEntidade.paraDominio() = com.hkawzz.nkote.dominio.Objetivo(
    id, nome, icone, cor, metaCentavos, prazo?.let { java.time.LocalDate.parse(it) }, concluidoEm, arquivado, contaCaixinhaId,
)

fun MovimentoReservaEntidade.paraDominio() = com.hkawzz.nkote.dominio.MovimentoReserva(
    id, objetivoId, java.time.LocalDate.parse(data), valorCentavos, com.hkawzz.nkote.dominio.TipoMovimentoReserva.valueOf(tipo), grupoId, criadoEm,
)

fun PendenciaLevadaEntidade.paraDominio() = com.hkawzz.nkote.dominio.PendenciaLevada(id, obrigacaoId, java.time.YearMonth.parse(mes))

fun EmprestimoEntidade.paraDominio() = com.hkawzz.nkote.dominio.Emprestimo(
    id = id, contaId = contaId, descricao = descricao, categoriaId = categoriaId, valorOriginal = valorOriginalCentavos,
    dataContratacao = LocalDate.parse(dataContratacao), contaPropriaId = contaPropriaId, anteriorAoApp = anteriorAoApp,
    jurosInformados = jurosInformadosCentavos, movimentacaoContratacaoId = movimentacaoContratacaoId, movimentacaoJurosId = movimentacaoJurosId,
)

fun PagamentoFaturaEntidade.paraDominio() = com.hkawzz.nkote.dominio.PagamentoFatura(
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
