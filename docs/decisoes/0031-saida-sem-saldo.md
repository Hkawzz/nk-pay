# 0031 — Saída sem saldo é recusada (R1)

**Estado:** aceita (briefing da v0.8, R1) · v0.8

## Decisão
- Toda operação que diminui o saldo de uma conta **própria, de benefício ou de caixinha** é recusada se deixar a conta negativa (regra 6G.1). Inclui excluir ou editar entradas, ajustes para baixo e desfazer recebimentos.
- Conta o **saldo atual depois da operação**. Sem cheque especial. Contas já negativas só aceitam operações que não as deixem **mais** negativas.
- **Implementação:** um único ponto na camada de dados. As operações que mexem em dinheiro rodam numa transação que compara os saldos de antes e de depois; se alguma conta ficou negativa **e** menor do que estava, a transação é desfeita inteira e a mensagem traz o saldo da conta. Assim nenhuma tela, nenhum serviço e nenhuma porta lateral (excluir, editar, desfazer) escapa da regra.
- As caixinhas entram na conferência porque também são dinheiro seu: uma retirada maior que a caixinha é recusada pelo mesmo ponto.