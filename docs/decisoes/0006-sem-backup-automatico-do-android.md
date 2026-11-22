# 0006 — Backup automático do Android desligado

**Estado:** aceita, provisória · 2026-11-21

## Contexto
O Android pode copiar sozinho os dados do app para a conta Google (`allowBackup`). Mas você não controla quando a cópia roda, não vê o conteúdo e não escolhe o que restaurar. Além disso, isso envia o histórico financeiro para fora do aparelho sem uma decisão explícita.

## Decisão
`android:allowBackup="false"` no `AndroidManifest.xml`. A proteção dos dados será o **backup próprio** do app (arquivo `.nkpay`, previsto para a v0.2), salvo onde você escolher.

## Consequências
- ✅ Os dados não saem do celular sem uma ação sua.
- ⚠️ **Até a v0.2, não existe backup nenhum.** Perder ou resetar o celular apaga os dados. É mais um motivo para a v0.2 vir logo depois da v0.1.
- ↩️ É fácil reverter: basta mudar para `true` em uma versão futura.
