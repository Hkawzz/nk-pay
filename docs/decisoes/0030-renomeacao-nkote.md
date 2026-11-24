# 0030 — Renomeação para NKote

**Estado:** aceita (briefing da v0.8, aprovada pelo dono) · 23/11/2026 · v0.8

## Contexto
"Pay" sugeria um app de pagamentos, e o app é de controle financeiro pessoal. Antes da v1.0 e da Play Store é a única janela para trocar também o identificador.

## Decisão
O nome passa a ser **NKote** (pronuncia-se "enikote").

| O que mudou | De | Para |
|---|---|---|
| Nome visível | NK Pay / NK Pay debug | NKote / NKote debug |
| `applicationId` | `com.nkpay.app` (debug `.debug`) | `com.hkawzz.nkote` (debug `com.hkawzz.nkote.debug`) |
| Pacotes Kotlin e `namespace` | `com.nkpay.app` | `com.hkawzz.nkote` |
| Esquemas do Room | `app/schemas/com.nkpay.app.dados.NkPayBanco/` | `app/schemas/com.hkawzz.nkote.dados.NkPayBanco/` (os JSONs 1 a 6 foram movidos) |
| Extensão dos backups novos | `.nkpay` | `.nkote` (a restauração continua aceitando `.nkpay`) |
| Repositório | `Hkawzz/nk-pay` | `Hkawzz/nkote` (o GitHub redireciona os links antigos) |

**Ficou de propósito:**
- o nome do **arquivo do banco** (ninguém o vê, e trocar exigiria uma etapa extra de migração);
- os **nomes dos Secrets** e da **keystore** (trocar arriscaria a assinatura; a chave é a mesma);
- os nomes das classes (`NkPayBanco`, `NkPayTema`...), para o PR mudar só o necessário;
- o **ícone** (o novo vem numa v0.8.x);
- o histórico: CHANGELOG, RELATORIO e ADRs antigos continuam dizendo "NK Pay".

## Consequência
Com o identificador novo, a v0.8.0 é instalada como um **app novo**, ao lado do NK Pay. O caminho: backup no NK Pay → instalar o NKote → restaurar o `.nkpay` → conferir → só então desinstalar o NK Pay. O mesmo vale para o app de debug.