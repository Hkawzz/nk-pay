# 0026 — Preparar próximo mês

**Estado:** aceita (briefing da v0.6, requisitos do planejamento inicial) · v0.6

## Decisão
- Um assistente em 4 passos (contas fixas, parcelas previstas, pendências atrasadas, revisão). Nada é gravado antes da confirmação; a confirmação grava tudo numa transação, num grupo do Registro de alterações.
- **Contas fixas:** reusa o lançamento da v0.3 (`lancarFixas`), que já impede duplicar, agora com o valor de cada uma editável. Valor sugerido: o da obrigação do mês anterior daquela conta fixa; sem ela, o valor padrão.
- **"Levar" uma pendência** não muda a regra da ADR 0013 (a pendência continua no mês original): é uma **marcação** (`pendencia_levada`) que faz ela aparecer **também** na lista do mês novo, com o mesmo vencimento, valor e mês de referência, e continuar "atrasada". As faturas de cartão nunca entram nessa lista (regra 6B.4).
- **Sugestão na virada:** em A pagar, um card "Preparar [mês]" aparece quando o mês escolhido ainda tem contas fixas a lançar ou pendências de meses anteriores.
- Rodar de novo não duplica: contas fixas já lançadas e pendências já levadas não aparecem.
