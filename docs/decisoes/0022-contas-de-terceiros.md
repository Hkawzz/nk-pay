# 0022 — "Conta de terceiros": um ponto único de verdade

**Estado:** aceita (especificação da v0.5.0, 2.1 e 4.3) · v0.5 · amplia a [0017](0017-integridade-das-contas-de-credito.md)

## Decisão
- `Natureza.ehContaDeTerceiros` = `CARTAO`, `CREDIARIO`, `EMPRESTIMO_RECEBIDO`, `EMPRESTIMO_CONCEDIDO`.
- Toda regra de integridade (validação de lançamentos avulsos, saldo inicial zero, mudança de natureza, listas "Conta", "De/Para", "Pagar com", filtro de Movimentações, "Contas e carteiras", origem de pagamentos) usa **só** essa função.
- `ehCredito` continua existindo com o sentido estreito de **cartão e crediário** (telas de cartões, faturas, Análises de crédito), para os empréstimos nunca aparecerem nelas por engano.
