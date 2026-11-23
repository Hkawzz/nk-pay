# 0024 — Banco versão 5: juros do parcelamento e pendências levadas

**Estado:** aceita (briefing da v0.6) · v0.6

## Decisão
Esquema 5, com a migração `MIGRACAO_4_5` escrita à mão:

| Mudança | Detalhe |
|---|---|
| `compra.valorOriginalCentavos` | `ADD COLUMN INTEGER` **vazia**. Vazia = igual ao total (as compras existentes continuam sem juros). Não há `UPDATE` nas linhas existentes: nada é reescrito |
| `pendencia_levada` (nova) | id, obrigacaoId, mes (`AAAA-MM`), carimbos e exclusão lógica. Marca uma pendência de um mês anterior para aparecer **também** no mês novo (Preparar mês, ADR 0026) |

- A marcação ficou numa **tabela nova**, e não numa coluna de `obrigacao`, para não alterar de novo a tabela de obrigações (e os testes de migração que a comparam campo a campo).
- Não há coluna para a despesa de juros da compra: os juros viram despesa **ao pagar** (ADR 0025), ligados ao pagamento por um ajuste, como os juros recebidos da v0.5.
- O `.nkpay` passa a levar `pendenciasLevadas` (lista vazia como padrão). A v0.5 recusa backups da v0.6 (versão 5 > 4).
