# Changelog

Todas as mudanças relevantes de cada versão. Formato inspirado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/), com versões no padrão [SemVer](https://semver.org/lang/pt-BR/).

As notas de cada Release do GitHub são tiradas automaticamente da seção da versão correspondente.

## [0.0.1] - 2026-11-21

Primeira versão instalável. Ela não tem funcionalidades financeiras: serve para validar o caminho completo, do código até o celular (CI, assinatura, Release e instalação).

### Adicionado
- Projeto Android (Kotlin, Jetpack Compose e Material 3), com `applicationId` `com.nkpay.app`, para Android 8 ou mais novo.
- Tema escuro próprio (fundo `#09090B`, roxo `#4C1D95` e lilás `#A78BFA`), com algarismos tabulares nos valores.
- Ícone do app e nome "NK Pay".
- Barra inferior com as abas **Início · Movimentações · A pagar · Objetivos · Mais**. Todas mostram telas vazias, sem dados falsos.
- Tela **Mais**, com Contas e carteiras, Categorias, Registro de alterações e **Sobre** (mostra a versão do app).
- Formatação de dinheiro em pt-BR a partir de centavos (`R$ 1.234,56`), com testes.
- CI no GitHub Actions (compila e testa) e Release automática com o APK assinado a cada tag `v*`.
- Documentação: README com o passo a passo de instalação, regras financeiras e registros de decisão.
