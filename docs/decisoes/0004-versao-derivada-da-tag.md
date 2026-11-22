# 0004 — Versão e `versionCode` derivados da tag

**Estado:** aceita · 2026-11-21

## Contexto
O Android usa dois números de versão: o `versionName` (o texto que aparece, como "0.1.0") e o `versionCode` (um inteiro que **precisa crescer** a cada atualização). Manter os dois à mão é fácil de esquecer e gera erros.

## Decisão
- A versão vem **da tag do Git**: a tag `v0.1.0` gera `versionName = "0.1.0"`.
- O `versionCode` é calculado a partir dela: `MAIOR × 10000 + MENOR × 100 + CORREÇÃO`.

| Tag | versionName | versionCode |
|---|---|---|
| v0.0.1 | 0.0.1 | 1 |
| v0.1.0 | 0.1.0 | 100 |
| v0.1.1 | 0.1.1 | 101 |
| v1.2.3 | 1.2.3 | 10203 |

- Os números MENOR e CORREÇÃO precisam ser menores que 100 (o build falha se não forem).
- Sem tag (compilação local), a versão é `0.0.0-dev` e o `versionCode` é 1.

## Consequências
- ✅ Basta criar a tag: nenhum arquivo precisa ser editado para mudar a versão.
- ✅ Seguindo o SemVer, o `versionCode` sempre cresce.
- ⚠️ Não se pode publicar uma tag "menor" depois de uma maior (por exemplo, v0.1.1 depois de v0.2.0): o Android recusaria a instalação por cima.
