# 0015 — Fatura calculada e pagamento das contas de crédito

**Estado:** aceita (decisões de 23/11/2026; 8.1 opção a e 8.5 adotadas pelo briefing autônomo) · v0.4

## Contexto
A fatura do cartão precisa aceitar pagamento total, parcial sem escolher compras e por compras específicas, sem nunca gerar despesa duplicada. O botão Pagar da v0.3 sempre cria uma DESPESA, e ele já está em uso.

## Decisão
1. **A fatura é calculada**: são as parcelas do cartão com o mesmo mês de referência. Não existe tabela de fatura.
2. **Pagar crédito é transferência** (conta própria → conta de crédito). O código do crédito fica num serviço **separado** (`PagamentoCreditoServico`). O `PagamentoServico` das contas fixas **não muda**.
3. **Pagar total ou um valor:** o valor é distribuído entre as faturas em aberto, **da mais antiga para a mais nova** (8.5). Cada fatura coberta por inteiro tem as parcelas ligadas à transferência (`pagamento`); a última, coberta em parte, recebe um abatimento geral (`pagamento_fatura`).
4. **Pagar compras específicas** quita exatamente o restante das compras escolhidas. Se isso passar do restante da fatura (porque ela já tem abatimentos gerais), o app recusa e sugere pagar a fatura.
5. **Pagar mais que o restante** (8.1, opção a): o app pergunta a natureza (juros, multa, tarifa, IOF, outro acréscimo). O excedente vira uma DESPESA de encargo **na conta de crédito**, em "Encargos financeiros", com um acréscimo na parcela, ligado ao pagamento para ser desfeito junto.
6. **Crediário:** pagamento por parcela, com as mesmas perguntas da v0.3. O desconto vira um AJUSTE positivo na conta do crediário mais o abatimento na parcela.
7. **Desconto em fatura de cartão não existe na v0.4** (decisão tomada sozinho: é a opção mais simples; um pagamento menor é sempre parcial). Pode entrar depois sem mudar o banco.
8. **Desfazer** um pagamento de crédito exclui (logicamente), numa transação: a transferência, todas as ligações e abatimentos gerais dela, e os ajustes, encargos e descontos que ela criou.
9. Fatura vencida e não paga fica **atrasada no mês original**, sem juros automáticos.

## Consequências
- As despesas criadas pela compra, pelos encargos e pelos descontos ficam bloqueadas para edição direta em Movimentações. Só mudam desfazendo o pagamento ou excluindo a compra.
- A invariante `Devido = Σ restante das faturas em aberto` é testada em todos os cenários.
