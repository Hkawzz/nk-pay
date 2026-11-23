# 0028 — Banco versão 6: objetivos e movimentos de reserva

**Estado:** aceita (briefing da v0.7) · v0.7

## Decisão
Esquema 6, com a migração `MIGRACAO_5_6` escrita à mão. **Só tabelas novas**: nenhuma tabela existente muda.

| Tabela | Colunas |
|---|---|
| `objetivo` | id, nome, icone, cor, metaCentavos (opcional), prazo (`AAAA-MM-DD`, opcional), concluidoEm (opcional), arquivado, criadoEm, atualizadoEm, excluidoEm |
| `movimento_reserva` | id, objetivoId (→ objetivo), data (`AAAA-MM-DD`), valorCentavos (+ aumenta, − diminui), tipo (`GUARDAR`, `RETIRAR`, `MOVER`, `AJUSTE`), grupoId, criadoEm, atualizadoEm, excluidoEm |

- **Nada calculado é gravado:** o guardado de cada objetivo é a soma dos movimentos não excluídos.
- **Mover** grava duas linhas com o mesmo `grupoId` (−X na origem, +X no destino). As outras operações gravam uma linha com o seu próprio grupo. Desfazer exclui logicamente todas as linhas do grupo.
- **Sem `movimentacaoId`** e **sem o tipo `USO_EM_DESPESA`**: pela decisão O1 = (b), não existe ligação entre despesa e objetivo.
- O `.nkpay` passa a levar `objetivos` e `movimentosReserva` (listas vazias como padrão, para os backups antigos). A v0.6 recusa backups da v0.7 (versão 6 > 5).
