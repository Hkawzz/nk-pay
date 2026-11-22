# 0001 — Stack: Kotlin + Jetpack Compose + Room

**Estado:** aceita · 2026-11-21

## Contexto
O NK Pay é um app só para Android, usado por uma pessoa, **100% offline** (sem servidor, sem login, sem internet). O risco principal deixa de ser "o servidor cair" e passa a ser "perder dados no celular". Por isso, o banco local precisa ter transações, chaves estrangeiras e migrações confiáveis.

## Decisão
- **Kotlin + Jetpack Compose (Material 3)** para a interface. É a stack oficial do Android: a mais leve e a mais bem documentada.
- **Room (SQLite)** para o banco. Ele oferece transações, chaves estrangeiras e migrações versionadas.
- **Navigation Compose**, **ViewModel + StateFlow** e **Coroutines**.
- **Um único módulo `app`**, organizado em pacotes (`dominio`, `dados`, `ui`).
- **Sem Hilt/Dagger.** As dependências são montadas à mão em um `AppContainer` simples (chega na v0.1.0).
- **minSdk 26 (Android 8):** permite usar `java.time` (`LocalDate`) nativamente e ícones adaptativos sem PNGs.

## Consequências
- ✅ Menos código e menos "mágica": dá para entender de onde vem cada objeto.
- ✅ As regras financeiras ficam em `dominio/`, em Kotlin puro, e são testadas sem emulador.
- ⚠️ Sem versão para computador ou web. Se isso for necessário um dia, a camada de domínio pode ser reaproveitada, mas a interface teria que ser refeita.
- ⚠️ Se o app crescer muito, vale reavaliar multi-módulo ou Hilt. Hoje seria custo sem retorno.
