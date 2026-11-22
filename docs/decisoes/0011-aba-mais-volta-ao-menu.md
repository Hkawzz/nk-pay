# 0011 — A aba Mais sempre reabre no menu principal

**Estado:** aceita · 2026-11-22 (adendo v0.3, item A2)

## Contexto
A barra inferior guarda o estado de cada aba ao trocar (`saveState`/`restoreState`). Na aba Mais, isso fazia ela reabrir na última tela interna (por exemplo, o Backup), e não no menu, o que confundia.

## Decisão
Só na aba **Mais**, o estado não é restaurado ao voltar para ela (`restoreState = false`): ela abre sempre no menu principal. As outras abas continuam preservando o estado. Por exemplo, o mês escolhido em Movimentações continua o mesmo.

## Consequências
- ✅ Tocar em "Mais" sempre leva ao mesmo lugar.
- ⚠️ **Se você sair da aba Mais com um formulário aberto e não salvo** (por exemplo, editando uma conta), **o que foi digitado se perde.** Por enquanto não há um diálogo de "descartar alterações?". Ele pode ser adicionado depois, se fizer falta.
- ↩️ É fácil reverter: é uma linha em `NkPayApp.kt`.
