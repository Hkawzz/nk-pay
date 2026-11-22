# 0005 — compileSdk 37.2, JDK 21 e Gradle 9.9

**Estado:** aceita · 2026-11-21

## Contexto
O briefing pede o target/compile SDK na "versão estável mais recente". Em novembro de 2026, as bibliotecas atuais do Compose (BOM 2026.11.01) exigem compilar contra o **Android 37.2** (API 37, versão menor 2). Com o 37.0, o build falha.

## Decisão
- `compileSdk` = Android **37.2** e `targetSdk` = **37**.
- **JDK 21** (Temurin, LTS) para compilar. O bytecode gerado é Java 17, compatível com o Android.
- **Gradle 9.9.2** (pelo Gradle Wrapper, versionado no repositório) e **Android Gradle Plugin 9.5.2**.
- Todas as versões de bibliotecas ficam em `gradle/libs.versions.toml`.

## Consequências
- ✅ Usamos as versões estáveis mais recentes, com as últimas correções.
- ⚠️ A cada ano sai uma API nova do Android. Atualizar o `targetSdk` pode mudar o comportamento do app e deve ser feito em um PR próprio, com teste no celular.
