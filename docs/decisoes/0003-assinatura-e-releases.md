# 0003 — Uma keystore de release, guardada nos GitHub Secrets

**Estado:** aceita · 2026-11-21

## Contexto
O Android só aceita **atualizar** um app se o APK novo for assinado com a **mesma chave** do instalado. Se a chave mudar, a única saída é desinstalar, e **desinstalar apaga todos os dados**. Para um app 100% offline, isso significa perder o histórico financeiro.

A chave de debug do CI não serve: o GitHub Actions gera uma nova a cada execução.

## Decisão
- Existe **uma única keystore de release** (`nk-pay-release.jks`), gerada com `keytool` **fora do repositório**.
- O dono guarda **duas cópias** dela, mais as senhas, em lugares diferentes (por exemplo, um gerenciador de senhas e um pendrive ou nuvem pessoal).
- No GitHub, a keystore fica nos **Secrets** do repositório, criptografados:
  - `NKPAY_KEYSTORE_BASE64`: o arquivo `.jks` em base64.
  - `NKPAY_KEYSTORE_PASSWORD`, `NKPAY_KEY_ALIAS` e `NKPAY_KEY_PASSWORD`.
- O `release.yml` decodifica a keystore em um arquivo temporário, assina o APK e **falha** se os Secrets não existirem. Ele nunca publica um APK sem assinatura ou com a chave de debug.
- O `build.gradle.kts` lê a assinatura de variáveis de ambiente. Sem elas, o APK de release sai sem assinatura (e não instala), o que é intencional.

## Consequências
- ✅ Todas as versões instalam por cima da anterior, mantendo os dados.
- ✅ Nenhum segredo fica em arquivo versionado.
- ⚠️ **Se a keystore e as cópias forem perdidas, não há como recuperar.** O app teria que mudar de assinatura, o que exige desinstalar. A partir da v0.2 (backup), daria para exportar os dados antes e importá-los depois, mas isso dá trabalho.
- ⚠️ Os GitHub Secrets não podem ser lidos de volta: eles não substituem as cópias pessoais.
