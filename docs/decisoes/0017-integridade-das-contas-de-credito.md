# 0017 — Integridade das contas de crédito

**Estado:** aceita (recomendação 2.4 da proposta, adotada pelo briefing autônomo) · v0.4

## Contexto
O devido de um cartão é `−saldo` da conta. Se uma entrada, despesa, ajuste ou transferência avulsa for lançada nela, o devido deixa de bater com as parcelas, sem que ninguém perceba.

## Decisão
- Contas `CARTAO` e `CREDIARIO` **não podem** ser usadas em lançamentos avulsos (Movimentações). A regra fica no `MovimentacaoRepositorio` (camada de dados), e não só na tela: um caminho esquecido da interface não consegue gravar.
- As listas "Conta", "De/Para" e "Pagar com" mostram só contas de dinheiro próprio e benefícios.
- Contas de crédito nascem com saldo inicial zero (o que já se devia entra como compra anterior ao app).
- Elas só mudam pelos serviços de compra e de pagamento de crédito, que gravam direto nos DAOs dentro de uma transação, com auditoria.
- A tela do cartão mostra um aviso se a invariante `Devido = Σ restante` não fechar.
