# 0007 — App de debug com outro identificador

**Estado:** aceita · 2026-11-21

## Contexto
Durante o desenvolvimento, às vezes é útil instalar um APK de teste no celular. Se ele tivesse o mesmo `applicationId` do app oficial, não instalaria (a assinatura é outra) ou, pior, mexeria nos dados reais.

## Decisão
O build de debug usa `applicationIdSuffix = ".debug"`, ou seja, `com.nkpay.app.debug`, e versão com sufixo `-debug`.

## Consequências
- ✅ O app de teste e o oficial convivem no mesmo celular, cada um com os seus dados.
- ✅ O `applicationId` oficial (`com.nkpay.app`) continua exclusivo das Releases.
