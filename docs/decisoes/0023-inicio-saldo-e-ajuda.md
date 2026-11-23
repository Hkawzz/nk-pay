# 0023 — Início: Saldo disponível, Resumo pelo fluxo e ajuda "?"

**Estado:** aceita (especificação da v0.5.0, 1.1 a 1.3, e D1) · v0.5

## Decisão
- O card principal mostra **Saldo disponível** (= Saldo total − Guardado). **Saldo total** e **Guardado** só aparecem quando o Guardado for maior que zero (a partir da v0.7). A fórmula é a do antigo "Disponível".
- O **Resumo do mês** passa a ser o **fluxo do dinheiro próprio** (regra 6E.2): Entradas, Saídas e Saldo das movimentações. Despesas pagas com VA/VR e compras no cartão **não** são saídas. Nas Análises, "Despesas registradas" (consumo) e "Saídas do dinheiro próprio" (fluxo) aparecem lado a lado, cada um com "?".
- **Ajuda "?"**: um componente só (`NkAjuda`), que abre uma folha com "o que é · como é calculado · exemplo · o que não é", e **todos os textos num arquivo só** (`ui/ajuda/TextosDeAjuda.kt`), com os exemplos conferidos por teste contra o domínio. Só nos locais listados na especificação.

## Consequência
O roteiro de aceite da v0.1 (`AceiteTest`) esperava ver na Início o valor de uma despesa paga com o VA como total do mês. Pela D1, essa despesa não é saída do dinheiro próprio. O teste muda **uma linha**: passa a procurar a despesa na lista de últimas movimentações. A justificativa está no PR e no RELATORIO.
