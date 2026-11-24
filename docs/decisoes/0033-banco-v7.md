# 0033 — Banco versão 7: caixinhas e backup automático

**Estado:** aceita (briefing da v0.8) · v0.8

## Decisão
Esquema 7, com a migração `MIGRACAO_6_7` escrita à mão:

| Mudança | Detalhe |
|---|---|
| `objetivo.contaCaixinhaId` | `ADD COLUMN TEXT` **vazia**. Vazia = objetivo ainda sem caixinha (os da v0.7, até a conversão V3) |
| `configuracao_backup` (nova) | Uma linha: pasta escolhida, frequência, data e resultado do último backup automático |

- A natureza `CAIXINHA` é texto na coluna `conta.natureza`: não muda o esquema.
- A categoria de sistema **"Rendimentos"** é criada pelo app quando falta (com auditoria), como "Juros recebidos" na v0.5, e não pelo SQL da migração: a migração mexe só no esquema.
- O `.nkote` leva tudo, menos a `configuracao_backup`: a pasta escolhida é uma permissão deste celular e não vale em outro. A v0.7 recusa backups da v0.8 (versão 7 > 6).