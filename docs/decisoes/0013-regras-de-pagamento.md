# 0013 — Regras de pagamento de contas fixas e avulsas

**Estado:** aceita (aprovada pelo dono em 2026-11-22, com esclarecimentos) · v0.3

## Contexto
As análises deixaram em aberto como pagar contas fixas e avulsas, como tratar o pagamento parcial e as diferenças (juros, desconto) e como as contas fixas entram em cada mês. O dono aprovou as propostas com os esclarecimentos abaixo.

## Decisão
1. **A despesa é o valor efetivamente pago.** Pagar uma conta fixa ou avulsa cria uma DESPESA pelo valor pago, na conta escolhida e na categoria da obrigação.
   - Em pagamentos parciais, **cada pagamento gera a sua própria despesa**, e a obrigação continua com o restante pendente.
   - A obrigação **preserva o mês de referência original**, mesmo quando o pagamento acontece em outro mês. A despesa fica no mês em que foi paga; a obrigação continua pertencendo ao mês dela.
2. **Encargos são despesas próprias.** Juros, multa, tarifa e IOF viram uma despesa separada, na categoria de sistema "Encargos financeiros", e nunca se misturam com a despesa original.
   - Exemplo: luz de R$ 100,00 paga com R$ 5,00 de multa vira uma despesa de R$ 100,00 (categoria da luz) e outra de R$ 5,00 (Encargos financeiros), mais um ajuste MULTA de R$ 5,00 na obrigação.
3. **Pagar menos que o restante:** o app **pergunta** se foi pagamento parcial ou desconto. Nada é classificado automaticamente.
   - *Parcial:* registra a despesa do valor pago, e o restante continua pendente.
   - *Desconto:* registra a despesa do valor pago e um abatimento (ajuste DESCONTO) da diferença, e a obrigação fica quitada. **Não é criada nenhuma receita fictícia.**
   - Na v0.3, cada pagamento tem uma classificação. Dividir uma mesma diferença entre vários motivos pode vir depois.
4. **Contas fixas no mês:** o botão "Lançar contas fixas de [mês]" mostra as contas fixas disponíveis para **seleção manual** e confirmação. Ele não lança todas automaticamente e **impede duplicar** a obrigação de uma conta fixa que já foi lançada naquele mês, mesmo se a operação for repetida. O assistente completo continua na v0.5.
5. **Pendências:** obrigações vencidas e não quitadas continuam em "A pagar" como **atrasadas**, com o mês de referência original. Elas não mudam de mês sozinhas.

## Consequências
- ✅ Os totais de despesas do mês mostram o dinheiro que de fato saiu.
- ✅ Encargos ficam visíveis separados, o que permite saber quanto se perde com juros e multas.
- ✅ Nada muda de lugar sem uma ação da pessoa.
- ⚠️ Uma conta paga com atraso aparece nas despesas do mês do pagamento, e não do mês de referência. É o comportamento pedido ("valor efetivamente pago, quando pago").
