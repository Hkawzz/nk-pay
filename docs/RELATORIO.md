# Relatório de desenvolvimento do NK Pay

Este arquivo conta **tudo o que foi feito**, versão por versão, em linguagem simples: o que entrou, por que, quais decisões foram tomadas, o que deu errado e como foi resolvido. Ele é atualizado **a cada versão publicada**. A versão mais nova fica no topo.

Para os detalhes técnicos de cada decisão, veja [`docs/decisoes/`](decisoes/). Para as regras de cálculo, veja [`docs/regras-financeiras.md`](regras-financeiras.md). O [`CHANGELOG.md`](../CHANGELOG.md) é a versão curta deste relatório.

| Versão | Data | Em uma frase | Release |
|---|---|---|---|
| v0.2.0 | 22/11/2026 | Backup e restauração | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.2.0) |
| v0.1.1 | 22/11/2026 | Valor estilo app de banco, transições e saldo por conta na Início | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.1.1) |
| v0.1.0 | 22/11/2026 | Primeira versão de uso real: contas, categorias, movimentações, Início e auditoria | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.1.0) |
| v0.0.1 | 21/11/2026 | Esqueleto instalável para validar o caminho até o celular | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.0.1) |

---

## v0.2.0 — Backup e restauração

**Objetivo:** como os dados ficam só no celular, criar a única proteção contra perder, resetar ou trocar o aparelho.

**O que foi feito** (PR [#12](https://github.com/Hkawzz/nk-pay/pull/12))
- Nova tela **Mais → Backup e restauração**.
  - *Fazer backup agora:* gera um arquivo `.nkpay` e salva onde você escolher (por exemplo, o Google Drive). O app continua sem acesso à internet: quem salva no Drive é o próprio Android.
  - *Restaurar de um arquivo:* valida o arquivo, mostra a data e o conteúdo, e só substitui depois da sua confirmação.
  - Antes de restaurar, o app guarda uma **cópia automática** do estado atual (as 5 mais recentes), que pode ser restaurada para desfazer.
  - A restauração fica registrada no Registro de alterações.
- **Aviso na Início** quando não há backup ou quando o último tem 7 dias ou mais.

**Como funciona por dentro**
- O `.nkpay` é um ZIP com `manifest.json` (versões, data, contagens e uma "impressão digital" SHA-256 dos dados) e `dados.json` (as 4 tabelas inteiras, inclusive os registros excluídos e a auditoria).
- É um backup *lógico* (os dados em texto), e não uma cópia do arquivo do banco. Isso funciona em qualquer Android 8+ e permite converter backups antigos quando o banco mudar.
- Antes de mexer em qualquer coisa, a restauração confere a estrutura, a versão (um backup de versão mais nova é recusado), o SHA-256 (detecta arquivo corrompido ou adulterado), as referências entre as tabelas e o tamanho. Depois, troca tudo em **uma única transação**: ou dá certo inteiro, ou nada muda.

**Decisões tomadas** ([0010](decisoes/0010-backup.md))
- Sem senha no arquivo por enquanto: esquecer a senha perderia o backup para sempre. Pode entrar depois como opção.
- O aviso aparece a partir de 7 dias sem backup.
- O backup é manual até a v0.8 (backup automático).

**Testes:** 10 novos (ida e volta idênticas, nenhuma mistura de dados, desfazer pela cópia automática, recusa de arquivos inválidos, adulterados e de versão mais nova). Total: **54 testes**.

**Limitação conhecida:** o seletor de arquivos real do Android com o Drive não é testável no computador. Precisa ser conferido no celular.

---

## v0.1.1 — Ajustes de uso

**Objetivo:** corrigir o que incomodou no primeiro uso real.

**O que foi feito** (PR [#11](https://github.com/Hkawzz/nk-pay/pull/11))
1. **Campo de valor estilo app de banco:** começa em 0,00 e cada número entra pela direita (0,01 → 0,12 → 1,27 → 12,78). Não é mais preciso apagar o 0,00. Onde o valor pode ser negativo, o botão **+/−** inverte o sinal.
2. **Transições mais rápidas:** a lentidão vinha do fade padrão da biblioteca, de 700 ms. Agora abrir uma tela desliza da direita, voltar desliza para a direita (cerca de 260 ms) e trocar de aba é um fade de 150 ms.
3. **Saldo de cada conta na Início:** o bloco "Suas contas" lista cada conta de dinheiro próprio com o saldo, e tocar em uma conta abre a tela dela.

Também marquei a estrutura do banco (ADR [0008](decisoes/0008-banco-de-dados.md)) como **aceita**, registrando a sua aprovação.

---

## v0.1.0 — Primeira versão de uso real

**Objetivo:** um app que já substitui a planilha no dia a dia, com dados que continuam válidos nas versões seguintes.

**O que foi feito**, em PRs pequenos:

| PR | O que entrou |
|---|---|
| [#2](https://github.com/Hkawzz/nk-pay/pull/2) | A Release passa a conferir a impressão digital da chave de assinatura: um APK assinado com outra chave nunca é publicado |
| [#3](https://github.com/Hkawzz/nk-pay/pull/3) | Banco de dados (4 tabelas), regras de saldo em Kotlin puro e auditoria gravada na mesma transação de cada alteração |
| [#4](https://github.com/Hkawzz/nk-pay/pull/4) | Contas e carteiras: lista, formulário, tela da conta, arquivar e excluir (bloqueado se houver movimentações) |
| [#5](https://github.com/Hkawzz/nk-pay/pull/5) | Categorias: sugestões iniciais, criar, renomear e arquivar. "Encargos financeiros" fica travada |
| [#6](https://github.com/Hkawzz/nk-pay/pull/6) | Movimentações: os 4 tipos, ajuste pelo novo saldo, lista por dia com seletor de mês, filtros e totais |
| [#7](https://github.com/Hkawzz/nk-pay/pull/7) | Tela Início: Disponível, benefícios à parte, resumo do mês e últimas movimentações |
| [#8](https://github.com/Hkawzz/nk-pay/pull/8) | Registro de alterações: lista e detalhe com o antes e o depois de cada campo |
| [#9](https://github.com/Hkawzz/nk-pay/pull/9) | Teste de aceite pela interface (toques e digitação) e capturas de tela automáticas |
| [#10](https://github.com/Hkawzz/nk-pay/pull/10) | Notas da versão e README |

**A estrutura do banco** ([0008](decisoes/0008-banco-de-dados.md)), aprovada por você antes da publicação:
- `conta` (com natureza PROPRIO, BENEFICIO, CARTAO e CREDIARIO; as colunas do cartão já existem, vazias).
- `categoria`, `movimentacao` e `auditoria`.
- Valores em centavos, IDs em UUID e exclusão lógica em todas as tabelas financeiras.
- O esquema de cada versão fica em `app/schemas/`, e uma atualização nunca apaga dados.

**Decisões tomadas sozinho** ([0009](decisoes/0009-saldo-inicial-e-contas-arquivadas.md) e PRs)
- Todas as movimentações contam no saldo, mesmo as com data anterior à do saldo inicial.
- Contas arquivadas continuam contando no saldo: arquivar só esconde a conta.
- Transferências entre dinheiro próprio e benefício são permitidas nos dois sentidos.
- Room 2.8 em vez do Room 3, por ser mais maduro.
- A conta tem só cor por enquanto; a coluna de ícone fica reservada.
- No seletor de tipo, "Transferência" aparece como "Transf." para caber na tela.

**Problemas encontrados e resolvidos**
- As capturas de tela automáticas mostraram textos cortados ("Movimenta…" na barra inferior e "Transfer…" no seletor). Corrigido antes da publicação.
- O relógio falso usado nos testes estava em 2025 por um erro na constante. Corrigido.
- O PR #10 (só documentação) foi juntado antes de o CI rodar. O CI da `main` rodou logo depois e passou.

**Testes:** 44, todos com dados fictícios (domínio, banco e um roteiro de aceite pela interface).

---

## v0.0.1 — Esqueleto instalável

**Objetivo:** provar o caminho inteiro (código → CI → APK assinado → Release → celular) antes de investir em funcionalidades.

**O que foi feito** (PR [#1](https://github.com/Hkawzz/nk-pay/pull/1))
- Projeto Android em Kotlin + Jetpack Compose (Material 3), com `applicationId` **`com.nkpay.app`** (permanente) e suporte a Android 8+.
- Tema escuro próprio (preto e roxo), ícone e nome "NK Pay".
- Barra inferior com as 5 abas e telas vazias, sem dados falsos.
- CI no GitHub Actions e Release automática a cada tag `v*`.
- Documentação: README com o passo a passo de instalação, regras financeiras e registros de decisão.

**Preparação do ambiente**
- Instalados Git, GitHub CLI, JDK 21, Gradle e Android SDK. Como o app do Claude é da Microsoft Store, o Windows redirecionava as gravações em `AppData`; por isso, as ferramentas ficam em `C:\Users\kawan\nk-pay-tools`.
- Repositório privado `Hkawzz/nk-pay` criado.
- **Keystore de release** criada por você (você digitou a senha) em `Documents\nk-pay-chave\`, com os 4 GitHub Secrets cadastrados. Impressão digital: `E4:08:56:3D:…:FC:64`.

**Decisões tomadas** ([0001](decisoes/0001-stack.md) a [0007](decisoes/0007-app-de-debug-separado.md))
- Stack Kotlin + Compose + Room, um módulo, sem Hilt.
- Dinheiro em centavos inteiros.
- Uma keystore única, guardada nos Secrets.
- `versionCode` derivado da tag (0.1.0 → 100).
- compileSdk 37.2.
- Backup automático do Android desligado.
- App de debug com outro identificador.
