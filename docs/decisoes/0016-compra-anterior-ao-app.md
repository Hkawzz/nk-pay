# 0016 — Compra feita antes de usar o app (parcelamento já existente)

**Estado:** aceita (opção B de 23/11/2026; 8.2 opção a, 8.3 e 8.4 adotadas pelo briefing autônomo) · v0.4

## Decisão
- A compra é registrada com `anteriorAoApp = true`. Ela cria a **DESPESA pelo valor total na data original** (mesmo que seja o mês atual: 8.3), as N parcelas, um abatimento `QUITADA_ANTES_DO_APP` para cada parcela já paga e **um** AJUSTE positivo na conta de crédito com a soma dessas parcelas ("Parcelas pagas antes do app").
- A despesa é marcada como histórica **pela ligação com a compra**, sem coluna nova em `movimentacao`.
- **Parcela paga em parte antes do app** (8.4): o cadastro aceita o valor já pago da parcela seguinte, que vira um abatimento `QUITADA_ANTES_DO_APP` pelo valor parcial.
- **Despesa duplicada** (8.2, opção a): o app **não converte nada**. No cadastro, ele avisa quando encontra contas avulsas ou despesas com nome ou valor parecidos e explica o roteiro (desfazer o lançamento antigo e pagar a parcela pelo fluxo do cartão).
- `QUITADA_ANTES_DO_APP` é um abatimento, mas **não** é desconto: fica fora de "descontos obtidos".
- Contas próprias e benefícios **não mudam** com o cadastro.

## Consequências
- O número de despesas do mês original sobe de uma vez quando a compra anterior é cadastrada (8.3, aceito pelo dono).
- Excluir a compra desfaz tudo o que ela criou, inclusive o ajuste das parcelas pagas antes.
