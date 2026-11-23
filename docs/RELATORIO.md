# Relatório de desenvolvimento do NK Pay

Este arquivo conta **tudo o que foi feito**, versão por versão, em linguagem simples: o que entrou, por que, quais decisões foram tomadas, o que deu errado e como foi resolvido. Ele é atualizado **a cada versão publicada**. A versão mais nova fica no topo.

Para os detalhes técnicos de cada decisão, veja [`docs/decisoes/`](decisoes/). Para as regras de cálculo, veja [`docs/regras-financeiras.md`](regras-financeiras.md). O [`CHANGELOG.md`](../CHANGELOG.md) é a versão curta deste relatório.

| Versão | Data | Em uma frase | Release |
|---|---|---|---|
| v0.3.0 | 22/11/2026 | Contas a pagar (fixas, avulsas, botão Pagar) e visual refinado | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.3.0) |
| v0.2.0 | 22/11/2026 | Backup e restauração | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.2.0) |
| v0.1.1 | 22/11/2026 | Valor estilo app de banco, transições e saldo por conta na Início | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.1.1) |
| v0.1.0 | 22/11/2026 | Primeira versão de uso real: contas, categorias, movimentações, Início e auditoria | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.1.0) |
| v0.0.1 | 21/11/2026 | Esqueleto instalável para validar o caminho até o celular | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.0.1) |

---

## v0.4.0 (em validação) — Cartões, crediários, categorias com ícone, nova Início e Análises

**Objetivo:** cartões de crédito e crediários (compras parceladas, faturas, pagamento total, parcial ou por compras), categorias com ícone e cor, design system v2, a nova tela Início e a tela de Análises. Fontes: `planejamento/proposta-execucao-v0.4.md` (revisão 2), `planejamento/briefing-v0.4-autonomo.md` e `planejamento/adendo-inicio-v0.4.md`.

**Como foi feito:** de forma autônoma (o dono estava indisponível), seguindo a ordem de PRs da proposta. Cada PR termina com o CI verde e esta seção atualizada, para retomar sem perda se o limite de uso acabar.

**PRs**

| # | Etapa | O que entrou | Estado |
|---|---|---|---|
| [#20](https://github.com/Hkawzz/nk-pay/pull/20) | F0 | Regras 6B, 6C e 6D em `regras-financeiras.md` e as decisões 0014 a 0018. Sem código | ✅ |
| [#21](https://github.com/Hkawzz/nk-pay/pull/21) | V0 | Capturas de referência das telas atuais (`ref-*` no artefato do CI) e o APK de debug guardado pelo CI | ✅ |
| [#22](https://github.com/Hkawzz/nk-pay/pull/22) | V1 | Design system v2: tokens, `NkCardDestaque`, `NkIconeComFundo`, `NkCardConta`, `NkCartaoCredito`, `NkValorAjustavel`, 40 ícones próprios e 12 cores, com vitrine no Android 8, 9 e atual | ✅ |
| [#23](https://github.com/Hkawzz/nk-pay/pull/23) | F1 | Banco v3: tabelas `compra` e `pagamento_fatura`, `icone` e `cor` na categoria, migração 2→3 explícita, backup com as tabelas novas. Testes de migração com banco v2 fiel e a matriz de backup | ✅ |
| [#24](https://github.com/Hkawzz/nk-pay/pull/24) | F2 | Domínio de crédito: datas por fechamento e vencimento, arredondamento das parcelas, fatura calculada, devido, limite, invariante, distribuição de um pagamento entre faturas, validação por natureza na camada de dados | ✅ |
| [#25](https://github.com/Hkawzz/nk-pay/pull/25) | C1 | Aparência sugerida às categorias pelo nome (auditada, só nas sem ícone, idempotente), também depois de restaurar um backup da v0.3; categorias novas já nascem com ícone e cor | ✅ |
| [#26](https://github.com/Hkawzz/nk-pay/pull/26) | C2 | Tela da categoria (nome, ícone, cor, cor personalizada com ajuste de contraste, arquivar); ícone e cor nas listas de movimentações, nas escolhas de categoria e nas contas (que também ganham escolha de ícone) | ✅ |
| [#27](https://github.com/Hkawzz/nk-pay/pull/27) | F3 | Mais → Cartões e crediários: cadastro (limite, fechamento, vencimento), cartão visual com devido e limite, tela do cartão com as faturas e o aviso de invariante. As listas "Conta", "De/Para", o filtro e o "Pagar com" deixam de mostrar contas de crédito | ✅ |
| [#28](https://github.com/Hkawzz/nk-pay/pull/28) | F4 | Compra parcelada (despesa do total + parcelas) e compra anterior ao app (despesa no mês original, parcelas quitadas antes, ajuste no crédito, aviso de possível duplicidade); excluir compra; parcelas fora de A pagar | ✅ |

**Decisões tomadas sozinho** (todas com a opção mais simples, mais segura para os dados e mais fácil de reverter)
- **Compra no dia do fechamento** entra na fatura seguinte (é o comportamento mais comum dos bancos). Regra 6B.3.
- **1ª parcela do crediário:** no mês seguinte ao da compra, no dia de vencimento do crediário (ou no dia da compra). Sempre editável no cadastro.
- **Desconto em fatura de cartão não existe na v0.4:** pagar menos é sempre parcial. No crediário, o desconto existe (como na v0.3). ([0015](decisoes/0015-fatura-e-pagamento-de-credito.md))
- **Pagar compras específicas** quita o restante exato das compras escolhidas e é recusado se passar do restante da fatura.
- **Contas de crédito nascem com saldo inicial zero**: a dívida anterior entra como compra anterior ao app.
- **Ícones próprios** (caminhos do Material Icons, Apache 2.0) em vez do pacote "extended"; 12 cores predefinidas e uma personalizada com ajuste de contraste para 3:1. ([0018](decisoes/0018-categorias-com-aparencia.md))
- **Sem a biblioteca Haze** (vidro de verdade): o brilho é um degradê radial igual em todas as versões do Android, e a sombra colorida aparece no Android 9+. ([0019](decisoes/0019-design-system-v2.md))

- **Uma conta com lançamentos não vira cartão ou crediário** (nem o contrário): a mudança de natureza é recusada, porque o devido deixaria de bater.
- **Editar a categoria virou uma tela própria** (era um diálogo): a grade de ícones não cabe bem num diálogo, e o diálogo travava o layout nos testes.
- **Nas listas de movimentações**, entradas e despesas mostram o ícone da categoria; transferências e ajustes, o ícone da conta.
- **Editar valor ou parcelas de uma compra é excluir e cadastrar de novo**, só enquanto nada foi pago. Descrição e categoria também seguem essa regra (opção mais simples, com tudo auditado).
- **O aviso de duplicidade** procura contas a pagar (fora do crédito) e despesas com nome parecido **ou** com o valor igual ao total ou a uma parcela. Ele só avisa.
- **Movimentações de conta de crédito** (compra, encargo, desconto, pagamento) ficam bloqueadas em Movimentações, com uma mensagem que aponta para a tela do cartão.

**Pendências**
- **Revisão externa dos PRs F0, F1, F2, F5 e F6** (ChatGPT e o analista, seção 5 da proposta): não aconteceu, porque o dono estava indisponível. Os PRs ficam abertos para consulta no GitHub, e os testes automáticos cobrem os exemplos numéricos das regras.

**Problemas encontrados**
- **Duas linhas de um teste antigo (`MigracaoTest`, "migrar de 1 para 2") precisaram mudar**, e não havia como evitar: o teste conferia `db.version == 2` e comparava todas as colunas da `categoria`. Com o banco v3, o caminho 1→2→3 termina na versão 3, e a categoria tem duas colunas novas no fim. As mudanças foram: (1) conferir `NkPayBanco.VERSAO`; (2) comparar as linhas sem o sufixo `|icone=NULL|cor=NULL`, o que **também confere que as colunas novas nascem vazias**. Todo o resto do teste (todas as linhas idênticas, contagens, saldos) continua igual. **Contradição anotada:** o briefing pede os 97 testes "sem alteração" e também uma migração para a v3. As duas coisas não cabem juntas nesse teste, e a regra 5 do briefing permite mudar um teste com justificativa. Os outros 96 testes não mudaram.

---

## v0.3.0 — Contas a pagar e visual refinado

**Objetivo:** substituir as colunas "Fixos", "Contas do Mês" e "Pago?" da planilha (adendo `planejamento/adendo-v0.3-visual.md`), com o visual padronizado antes das telas novas.

**O que foi feito**, na ordem de PRs do adendo:

| PR | O que entrou |
|---|---|
| [#14](https://github.com/Hkawzz/nk-pay/pull/14) | Ajustes rápidos: o "+" saiu da Início, a aba Mais sempre volta ao menu e as transições ficaram mais rápidas |
| [#15](https://github.com/Hkawzz/nk-pay/pull/15) | Tokens de design (espaçamento, cantos, tipografia) e os componentes `Nk*` |
| [#16](https://github.com/Hkawzz/nk-pay/pull/16) | Componentes aplicados em todas as telas, ícones em um estilo só, barra inferior mais clara (com antes e depois) |
| [#17](https://github.com/Hkawzz/nk-pay/pull/17) | Banco versão 2: 4 tabelas novas, migração explícita, cópia de segurança antes de migrar e testes de migração |
| [#18](https://github.com/Hkawzz/nk-pay/pull/18) | Aba A pagar, contas fixas, conta avulsa e lançamento manual das fixas do mês, sem duplicar |
| [#19](https://github.com/Hkawzz/nk-pay/pull/19) | Botão Pagar: pagamento parcial, desconto, encargos separados e desfazer pagamento |

**Decisões aprovadas por você**
- Estrutura do banco v2 ([0012](decisoes/0012-banco-v2-obrigacoes.md)): só tabelas novas, sem mexer nas existentes.
- Regras de pagamento ([0013](decisoes/0013-regras-de-pagamento.md)): a despesa é o valor efetivamente pago, cada parcial gera a sua despesa, os encargos ficam em "Encargos financeiros", o desconto quita sem receita, as fixas entram por seleção manual sem duplicar e as atrasadas ficam no mês original.

**Decisões tomadas sozinho**
- A aba Mais reabre no menu; um formulário não salvo se perde ao sair dela ([0011](decisoes/0011-aba-mais-volta-ao-menu.md)).
- Uma despesa criada pelo Pagar só muda desfazendo o pagamento, para os números nunca se desencontrarem.
- Pagar uma conta já paga é recusado. "Crédito/pagamento adiantado" fica para depois.
- Dividir uma mesma diferença entre vários motivos fica para depois: na v0.3, cada pagamento tem uma classificação.

**Proteção dos dados reais**
- A migração 1→2 só cria tabelas; ela foi testada com um banco igual ao da v0.2, e tudo ficou idêntico campo a campo.
- Antes de migrar, o app copia o arquivo do banco (as 3 últimas cópias ficam guardadas).
- Backups da v0.2 continuam restauráveis.
- As notas da Release pedem um backup antes de atualizar.

**Problemas encontrados e resolvidos**
- O `MigrationTestHelper` do Room falha com caminhos do Windows no Robolectric. O teste passou a montar o banco v1 a partir do esquema oficial, e o próprio Room valida o resultado.
- As capturas mostraram valores cortados em 3 colunas e o histórico sem o valor do encargo. Os dois foram corrigidos antes da publicação.
- O limite de uso acabou no meio do PR 6. O trabalho foi salvo numa branch separada, sem ir para a `main`, e retomado depois.

**Testes:** 97, todos com dados fictícios (eram 54 na v0.2), incluindo migração, pagamentos e dois roteiros pela interface.

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
