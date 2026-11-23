# 0021 — Empréstimos recebidos e concedidos

**Estado:** aceita (especificação da v0.5.0 e briefing: D2 = J1, D3 = K2, D5 = a; itens [ADOTADO] sem veto) · v0.5

## Decisão
- **Pessoa = conta** (`EMPRESTIMO_RECEBIDO` ou `EMPRESTIMO_CONCEDIDO`). Parcelas = `obrigacao` (origem `EMPRESTIMO`, com `emprestimoId` e `contaCreditoId` = a conta da pessoa, como as parcelas do crediário). Pagamentos = `pagamento`. Diferenças = `obrigacao_ajuste`.
- **Recebido (J1):** contratação = transferência pessoa → conta própria + despesa de juros ("Encargos financeiros") na conta da pessoa. Pagar = transferência. O pagamento reusa o caminho do crediário (`PagamentoCreditoServico.pagarParcela`).
- **Concedido (K2):** contratação = transferência conta própria → pessoa. Receber = transferência pessoa → conta própria **do principal** + entrada em "Juros recebidos" **dos juros proporcionais**.

## Decisões tomadas no desenvolvimento
1. **Como os juros K2 ficam ligados ao recebimento.** A especificação sugeria duas linhas de `pagamento`. Usei **uma** linha (com o valor aplicado inteiro: principal + juros) e um ajuste novo, de natureza `JUROS_RECEBIDOS`, que **não altera o restante** e aponta para a entrada de juros. O desfazer da v0.4 já exclui os ajustes de um pagamento e as movimentações deles, então a entrada de juros **nunca fica órfã**, com o mesmo código já testado. O efeito nas contas é o mesmo da especificação.
2. **Principal e juros não reconhecidos são calculados**, nunca gravados: o principal de um empréstimo é a soma do efeito, na conta da pessoa, das movimentações dele; os juros não reconhecidos = Σ restante − principal.
3. **Edição de parcela = acréscimo ou abatimento** (o previsto não muda), datado no dia da edição. No recebido, com a despesa de encargo ou o ajuste de redução; no concedido, sem movimentação (só muda a proporção).
4. **Total das parcelas menor que o valor original é recusado** (juros negativos não fazem sentido e quebrariam a proporção).
5. **Conta própria** do empréstimo: só contas de dinheiro próprio (não benefícios).
6. **Desconto concedido** usa a natureza `DESCONTO`, mas as Análises o separam pela natureza da conta: em empréstimo concedido ele é "Desconto concedido", nunca "Desconto obtido".
7. **A pagar:** cada parcela de empréstimo recebido em aberto aparece como uma linha própria (o toque abre a parcela). As de concedido nunca aparecem.
8. **Excluir:** só sem pagamento ou recebimento; desfaz contratação, juros J1, parcelas e edições.
