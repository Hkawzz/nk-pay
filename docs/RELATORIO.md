# Relatório de desenvolvimento do NK Pay

Este arquivo conta **tudo o que foi feito**, versão por versão, em linguagem simples: o que entrou, por que, quais decisões foram tomadas, o que deu errado e como foi resolvido. Ele é atualizado **a cada versão publicada**. A versão mais nova fica no topo.

Para os detalhes técnicos de cada decisão, veja [`docs/decisoes/`](decisoes/). Para as regras de cálculo, veja [`docs/regras-financeiras.md`](regras-financeiras.md). O [`CHANGELOG.md`](../CHANGELOG.md) é a versão curta deste relatório.

| Versão | Data | Em uma frase | Release |
|---|---|---|---|
| v0.7.0 | 23/11/2026 | Objetivos e reservas, Guardado real no Saldo disponível e roteiro do teste profundo (em validação no app de debug) | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.7.0) |
| v0.6.0 | 23/11/2026 | Compras com juros, parcelas antecipadas, excluir e arquivar, e o assistente Preparar próximo mês (em validação no app de debug) | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.6.0) |
| v0.5.0 | 23/11/2026 | Empréstimos (J1 e K2), Resumo da Início pelo fluxo, Saldo disponível e ajuda "?" (em validação no app de debug) | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.5.0) |
| v0.4.0 | 23/11/2026 | Cartões e crediários, categorias com ícone, nova Início e Análises (em validação no app de debug) | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.4.0) |
| v0.3.0 | 22/11/2026 | Contas a pagar (fixas, avulsas, botão Pagar) e visual refinado | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.3.0) |
| v0.2.0 | 22/11/2026 | Backup e restauração | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.2.0) |
| v0.1.1 | 22/11/2026 | Valor estilo app de banco, transições e saldo por conta na Início | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.1.1) |
| v0.1.0 | 22/11/2026 | Primeira versão de uso real: contas, categorias, movimentações, Início e auditoria | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.1.0) |
| v0.0.1 | 21/11/2026 | Esqueleto instalável para validar o caminho até o celular | [link](https://github.com/Hkawzz/nk-pay/releases/tag/v0.0.1) |

---

## v0.8.0 (em andamento) - NKote: saldo sem cheque especial, caixinhas, Início completa e backup automático

**Objetivo:** a versão do teste profundo e do teste fechado na Play. Novo nome, **NKote**; saída sem saldo recusada (R1); objetivos como caixinhas de verdade (R2), com a conversão dos objetivos da v0.7 (V3); exclusões protegidas que sempre respondem (D1, D2); Início completa (V1); backup automático (V2); AAB e Mais → Sobre para a Play (G). Fontes: `planejamento/briefing-v0.8-autonomo.md` (prevalece), `proposta-execucao-v0.8.md`, `achados-teste-v0.7.md` e `logo/LEIA-ME-logo.md`.

**Como foi feito:** de forma autônoma, na ordem de PRs do briefing. A renomeação foi sozinha, logo depois dos docs; a R1 também. Cada PR termina com o CI verde e esta seção atualizada.

**PRs**

| # | Etapa | O que entrou | Estado |
|---|---|---|---|
| [#64](https://github.com/Hkawzz/nk-pay/pull/64) | Docs | Regra 6G (R1, caixinhas, V3, D1/D2, Início, backup automático) e decisões 0030 a 0034. Sem código | ✅ |
| [#65](https://github.com/Hkawzz/nkote/pull/65) | Renomeação para NKote (sozinho) | Nome visível "NKote"/"NKote debug"; `applicationId` e pacotes `com.hkawzz.nkote`; esquemas 1 a 6 movidos para a pasta nova; backups novos `.nkote` (restauração aceita `.nkpay`); repositório `Hkawzz/nkote`; APK da Release `nkote-vX.Y.Z.apk`; teste novo restaurando um `.nkpay` da v0.7 | ✅ |
| [#66](https://github.com/Hkawzz/nkote/pull/66) | R1 (sozinho) | Saída sem saldo recusada num ponto só da camada de dados (`transacaoConferida`): despesa, transferência, Pagar, fatura, parcelas, emprestar, excluir/editar entrada, ajuste para baixo e desfazer recebimento; contas já negativas só aceitam o que não as piora. Nenhum teste antigo mudou | ✅ |
| [#67](https://github.com/Hkawzz/nkote/pull/67) | Banco v7 | Migração 6 → 7: `objetivo.contaCaixinhaId` vazia e tabela `configuracao_backup`; natureza `CAIXINHA`; banco v6 fiel com objetivos, saldos de conta idênticos; backup da v0.7 restaura, a v0.7 recusa a v0.8 | ✅ |
| [#68](https://github.com/Hkawzz/nkote/pull/68) | Caixinhas: domínio e validação | Cada objetivo tem uma conta `CAIXINHA`: guardar, retirar, mover e rendimento viram transferências e entrada; Saldo total = próprias + caixinhas, Guardado = Σ caixinhas; a caixinha não é meio de pagamento (lançamento avulso, Pagar, fatura, empréstimo, conta padrão e tela de contas recusam); a tela do objetivo já pede a conta | ✅ |
| [#69](https://github.com/Hkawzz/nkote/pull/69) | Caixinhas: telas | Textos de ajuda "?" no modelo de caixinhas; rendimentos nas Análises; teste de interface de que despesa, Pagar, fatura, crediário e empréstimo nunca listam caixinhas | ✅ |
| [#70](https://github.com/Hkawzz/nkote/pull/70) | Conversão V3 | Objetivos da v0.7 ganham caixinha zerada ao abrir o app e depois de restaurar um backup; nenhum saldo de conta muda; histórico antigo só para leitura; mensagem de uma vez só na Início e em Objetivos | ✅ |
| [#71](https://github.com/Hkawzz/nkote/pull/71) | D1 e D2 | Excluir pela aba Movimentações um lançamento protegido mostra o motivo e "Abrir cartão/empréstimo/caixinha/conta a pagar"; a recusa de excluir uma compra lista cada pagamento ou abatimento (data, valor, fatura) com "Desfazer" | ✅ |
| [#72](https://github.com/Hkawzz/nkote/pull/72) | Início completa (V1) | Abaixo do card principal, só com conteúdo: Próximos vencimentos (7 dias + atrasados, com atalho para pagar), Cartões (devido e próxima fatura), Caixinhas (até 3, "Ver todas") e A receber | ✅ |
| [#73](https://github.com/Hkawzz/nkote/pull/73) | Backup automático (V2) | Mais → Backup: escolher a pasta (Drive permitido), Diário (padrão)/Semanal/Mensal, último resultado, "Fazer agora" e orientação de bateria Samsung; guarda os 7 últimos da série `nkote-auto-`, nunca apaga os manuais; falha avisada na Início | ✅ |
| [#74](https://github.com/Hkawzz/nkote/pull/74) | AAB e Sobre (G) | A Release passa a anexar também `nkote-vX.Y.Z.aab`, assinado com a mesma keystore e com a mesma conferência da impressão digital do APK; Mais → Sobre mostra a política de privacidade (constante `Contato.POLITICA_DE_PRIVACIDADE`, texto provisório enquanto vazia) e o e-mail de suporte; sem botão Apoiar | ✅ |

**Decisões tomadas sozinho**
- **O logo novo não entra nesta versão:** o `LEIA-ME-logo.md` pede para aplicá-lo, mas o briefing (que prevalece) manda manter o ícone atual e trazer o novo numa v0.8.x. O ícone continua como adaptive icon em `res/`, fácil de trocar.
- **A R1 é conferida num ponto só da camada de dados:** a transação compara os saldos de antes e de depois e desfaz tudo se uma conta própria, de benefício ou de caixinha ficar negativa e menor do que estava. Assim excluir, editar e desfazer também passam pela regra.
- **O ajuste manual do guardado deixa de existir:** com caixinhas de verdade, o saldo é o que entrou e saiu dela (rendimento é uma entrada; correções são desfazer e lançar de novo).
- **A categoria "Rendimentos" é criada pelo app** quando falta, com auditoria, como "Juros recebidos"; a migração mexe só no esquema.
- **A configuração do backup automático não vai no `.nkote`:** a pasta escolhida é uma permissão deste celular.
- **Guardar e retirar só com conta própria** (benefício não se guarda, como na v0.7). Rendimento pode entrar também em objetivo concluído ou arquivado.
- **Excluir um objetivo arquiva a caixinha** (zerada) em vez de excluí-la: as transferências antigas continuam sendo entre contas suas, e o fluxo do passado não muda.
- **O PR das caixinhas já adaptou a tela do objetivo** (escolher a conta, rendimento no lugar de ajustar, histórico por transferências), porque a interface antiga não compilaria com o serviço novo. O PR das telas fica com a Início, os textos de ajuda e os testes de interface.
- **A mensagem da V3 aparece na Início e em Objetivos** enquanto houver histórico antigo e você não tocar em "Entendi" (a marca fica nas preferências do app, não no banco nem no backup).
- **Início completa:** a janela é de hoje até 7 dias à frente, mais os atrasados. As faturas entram nos vencimentos só quando vencem nessa janela; o bloco Cartões mostra sempre o devido e a próxima fatura em aberto. As caixinhas vêm das com mais dinheiro.
- **Backup automático pelo WorkManager** (2.11.2). Ele acrescenta ao manifesto só permissões de agendamento (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `ACCESS_NETWORK_STATE`); **não** acrescenta `INTERNET`: o app continua sem acesso à rede, e gravar no Drive é feito pelo próprio app do Drive através do seletor. A tarefa sempre termina como "sucesso" para o sistema, para não sair do agendamento; o resultado real (e a mensagem de falha) fica registrado e aparece na Início. "Fazer agora" roda na hora, sem passar pelo agendador. Só os arquivos `nkote-auto-…` entram na conta dos 7.
- **A política de privacidade ainda não tem endereço:** a constante fica vazia e o Sobre mostra um texto provisório ("o NKote não coleta, não envia e não compartilha nenhum dado"). Tocar no link ou no e-mail abre o navegador ou o app de e-mail do celular; o NKote continua sem a permissão `INTERNET`. A assinatura do AAB é conferida com `keytool -printcert` (o `apksigner` é só para APK); testei localmente com uma chave descartável, apagada em seguida.
- **Os nomes das classes (`NkPayBanco`, `NkPayTema`...) ficam:** a renomeação troca só o necessário (nome visível, identificador, pacotes, extensão e repositório).

**Pendências:** nenhuma até aqui.

**Problemas encontrados:**
- Início completa (#72): o `InicioV05Test` (v0.5) conferia que o texto "R$ 400,00" aparecia uma vez só; agora o devido do cartão aparece também no bloco Cartões. Mudança mínima: conferir que o texto existe (1 linha).
- D1 e D2 (#71): o `AceiteTest` (v0.1) falhou no CI. Depois de "Lançar", o teste esperava o saldo no banco e já tocava em "Início"; com a R1, salvar ficou um pouco mais lento, e a volta do formulário acontecia depois do toque, tirando a Início da tela. Correção só no teste: esperar a lista de Movimentações antes de trocar de aba (2 linhas). Nenhuma regra testada mudou.
- Caixinhas (#68): pela R2, os testes de objetivos da v0.7 mudaram de propósito: `ObjetivosTest` e `ObjetivoServicoTest` foram reescritos para caixinhas (tabela da 6G.2, R1 no guardar, "não é meio de pagamento" em cada serviço, desfazer, O3); `InicioGuardadoUiTest` e `AnalisesObjetivosUiTest` passaram a informar a conta e trocaram o aviso O2 (que saiu) pela recusa da R1. No `SaldosTest` (v0.1), o teste "reservado é descontado do disponível" agora guarda numa caixinha, com os mesmos números (Saldo total R$ 1.500,00, disponível R$ 1.100,00).
- Banco v7 (#67): 4 linhas do `MigracaoV6Test` (da v0.7) mudaram, sem como evitar, porque fixavam a versão 6 do banco (versão atual, nome da cópia e versão do manifesto) e a forma exata da tabela `objetivo` (agora com `contaCaixinhaId`). Nenhuma regra testada mudou.
- Renomeação (#65): o `ObjetivosUiTest` (da v0.7) falhou na suíte completa, aqui e no CI. Causa: depois de "Criar objetivo", ele esperava o texto "Fone", que também está no campo do formulário; numa máquina lenta, o toque seguinte ia para o campo, antes de a tela voltar para a lista. Correção só no teste: esperar a seção "Ativos" antes de tocar (uma linha). Fora isso, nenhum teste mudou além dos pacotes e do caminho dos esquemas.

---

## v0.7.0 (em validação) - Objetivos e reservas

**Objetivo:** a aba Objetivos (reservas virtuais sobre o dinheiro próprio), o Guardado de verdade no Saldo disponível, a seção Objetivos nas Análises, e a preparação do teste profundo (roteiro e registro de problemas). Fontes: `planejamento/briefing-v0.7-autonomo.md` (prevalece) e `planejamento/proposta-execucao-v0.7.md`.

**Como foi feito:** de forma autônoma, na ordem de PRs do briefing. Pela decisão O1 = (b), o PR "despesa usando dinheiro do objetivo" da proposta **não existe**. Cada PR termina com o CI verde e esta seção atualizada.

**PRs**

| # | Etapa | O que entrou | Estado |
|---|---|---|---|
| [#57](https://github.com/Hkawzz/nk-pay/pull/57) | Docs | Regra 6F (objetivos, com exemplos e O1 a O3), 7.5, 4 e 6E.3 atualizadas; decisões 0028 e 0029. Sem código | ✅ |
| [#58](https://github.com/Hkawzz/nk-pay/pull/58) | Banco v6 | Migração 5 → 6 só com tabelas novas (`objetivo` e `movimento_reserva`), backup com as duas, banco v5 fiel e matriz de backup (v0.6 → v0.7, ida e volta com objetivos, v0.6 recusa v0.7) | ✅ |
| [#59](https://github.com/Hkawzz/nk-pay/pull/59) | Domínio | Guardado calculado pelos movimentos, Saldo disponível com o Guardado real, guardar (bloqueado sem disponível), retirar, mover, ajustar, desfazer sem negativo, O2 (aviso na Início) e O3; testes da Início com Guardado > 0 | ✅ |
| [#60](https://github.com/Hkawzz/nk-pay/pull/60) | Aba Objetivos | Lista (ativos × concluídos/arquivados) com guardado, meta, falta, %, prazo e "guardar R$ X/mês"; cadastro com ícone e cor; detalhe com histórico e desfazer; guardar, retirar, mover e ajustar em tela cheia; concluir, arquivar e excluir (O3); "?" com o passo a passo do O1 | ✅ |
| [#61](https://github.com/Hkawzz/nk-pay/pull/61) | Análises | Seção Objetivos: guardado agora, guardado e retirado no período, progresso de cada objetivo, com "?" dizendo que objetivos não são entrada, saída nem despesa | ✅ |
| [#62](https://github.com/Hkawzz/nk-pay/pull/62) | Teste profundo e release | `docs/roteiro-teste-profundo.md` (todo o app, ação → resultado com números), `docs/registro-de-problemas-v0.8.md`, `docs/roteiro-teste-v0.7.md`, CHANGELOG, README e tag `v0.7.0` | ✅ |

**Decisões tomadas sozinho**
- **Desfazer uma retirada** (que aumenta o guardado) segue o mesmo bloqueio do Guardar: não pode passar do disponível. É a opção mais segura e coerente com a regra "nova reserva bloqueada sem disponível".
- **Só se guarda em objetivo ativo**; retirar e mover continuam valendo para concluídos e arquivados (o dinheiro deles continua guardado, O3).
- **Análises:** "guardado no período" = guardar + ajustes para cima; "retirado no período" = retirar + ajustes para baixo; mover não conta.
- **Mover só para objetivo ativo**, e **ajuste para cima só em objetivo ativo** (as duas são formas de guardar mais). Ajuste para baixo vale sempre.
- **O objetivo não escolhe conta:** ele reserva uma parte do Saldo total do dinheiro próprio. O disponível para guardar é calculado dentro da transação, com o Guardado de todos os objetivos não excluídos.
- **Operações em tela cheia** (guardar, retirar, mover, ajustar), não em diálogo: diálogos com listas travaram o Robolectric na v0.4.
- **Progresso arredonda para baixo** (nunca mostra 100% antes de atingir a meta).
- **Guardar por mês:** meses do mês atual ao do prazo, contando os dois, arredondado para cima; prazo vencido não sugere valor.

**Portões do briefing para publicar**

| Portão | Resultado |
|---|---|
| CI verde | ✅ em todos os PRs (#57 a #62) |
| Os 231 testes da v0.6 passando; mudanças inevitáveis justificadas | ✅ os **231** passam. **3 linhas** mudaram em 2 arquivos (`MigracaoV5Test` e `BackupV06Test`), só pela versão 6 do banco (ver Problemas). Conferido com `git diff v0.6.0` |
| Migração v5 fiel → v6 | ✅ `MigracaoV6Test` (banco v5 com dados em todas as tabelas, inclusive compra com juros e pendência levada; linhas idênticas; SQL das tabelas novas igual ao do esquema 6) |
| Matriz de backup (v0.6 → v0.7 restaura; v0.7 ida e volta com objetivos; v0.6 recusa v0.7) | ✅ `MigracaoV6Test`. O backup da v0.7 tem versão 6, e a v0.6 recusa versões maiores que a 5 |
| Regressão de saldos, crédito, empréstimos e Preparar mês | ✅ `SaldosTest`, `PagamentoCreditoTest`, `PagamentoComJurosTest`, `CompraServicoTest`, `PagamentoEmprestimoTest`, `EmprestimoServicoTest`, `PrepararMesTest` e `PrepararMesUiTest` sem alteração; `ObjetivoServicoTest` confere que objetivos não mexem em movimentações, saldos de conta, despesas nem fluxo |

No total, **244 testes** (231 da v0.6 + 13 novos), todos passando.

**Proteção dos dados reais**
- A migração 5→6 só cria as tabelas `objetivo` e `movimento_reserva`; nenhuma tabela existente muda. A cópia do banco antes de migrar continua valendo.
- Backups da v0.6 restauram na v0.7 (sem objetivos); a v0.6 recusa backups da v0.7.
- Nenhum dado real no repositório. APKs de debug (v0.6.0 e v0.7.0, mesma chave de debug) numa pasta local do computador, fora do repositório, para o roteiro.

**Pendências:** nenhuma. O teste profundo é seu: o roteiro está em `docs/roteiro-teste-profundo.md`, e o que encontrar vai para `docs/registro-de-problemas-v0.8.md`.

**Problemas encontrados:**
- Progresso (#60): o teste de interface esperava "67%" para R$ 200,00 de R$ 300,00; o app mostra 66% porque arredonda para baixo (de propósito, para não mostrar 100% antes da hora). Corrigi o teste.
- Banco v6 (#58): 3 linhas de testes da v0.6 mudaram, sem como evitar, porque fixavam a versão 5 do banco: `MigracaoV5Test` (versão atual e nome da cópia "banco-v4-antes-da-v6") e `BackupV06Test` (versão do manifesto). Agora usam `NkPayBanco.VERSAO`. Nenhuma regra testada mudou.

---

## v0.6.0 (em validação) - Ajustes dos testes e Preparar próximo mês

**Objetivo:** os ajustes encontrados nos testes da v0.5 (compra com juros, parcelas antecipadas antes do app, excluir e arquivar cartões e pessoas, numeração do empréstimo anterior) e o assistente **Preparar próximo mês**. Fontes: `planejamento/briefing-v0.6-autonomo.md` e `planejamento/analise-ajustes-pos-testes-v0.6.md`.

**Como foi feito:** de forma autônoma; primeiro os ajustes, depois o Preparar mês. O PR do pagamento com juros vai sozinho. Cada PR termina com o CI verde e esta seção atualizada.

**PRs**

| # | Etapa | O que entrou | Estado |
|---|---|---|---|
| [#47](https://github.com/Hkawzz/nk-pay/pull/47) | Docs | Regras 6B.8 a 6B.10 e 7.6 e decisões 0024 a 0027. Sem código | ✅ |
| [#48](https://github.com/Hkawzz/nk-pay/pull/48) | Banco v5 | Migração 4 → 5 explícita: `compra.valorOriginalCentavos` (vazia = sem juros) e tabela `pendencia_levada`; backup com as pendências levadas; testes com banco v4 fiel e matriz de backup | ✅ |
| [#49](https://github.com/Hkawzz/nk-pay/pull/49) | Juros do parcelamento (cadastro) | Compra com valor da parcela: despesa = original, parte da compra e juros calculados por parcela, "Juros a pagar" no cartão com "?", novo invariante, compra anterior com juros (ajuste só da parte da compra) | ✅ |
| [#50](https://github.com/Hkawzz/nk-pay/pull/50) | Pagamento com juros (sozinho) | Parcela do crediário, fatura inteira, abatimento geral e compras escolhidas lançam os juros pagos como despesa em Encargos financeiros; desconto consome primeiro os juros; desfazer remove tudo junto; telas de parcela e fatura mostram parte da compra e juros | ✅ |
| [#51](https://github.com/Hkawzz/nk-pay/pull/51) | Parcelas antecipadas (A2) | Compra anterior com parcelas antecipadas: valor pago ou desconto (o app calcula o outro), sem repetir as pagas, até o previsto; desconto em Descontos obtidos na data da compra; juros não viram despesa | ✅ |
| [#52](https://github.com/Hkawzz/nk-pay/pull/52) | Excluir e arquivar (A3, A5) | Cartões, crediários e pessoas: excluir só sem vínculos (senão oferece Arquivar), arquivar recusado com algo em aberto, "Mostrar arquivados" e Desarquivar; tela Pessoas com edição do nome | ✅ |
| [#53](https://github.com/Hkawzz/nk-pay/pull/53) | Empréstimo anterior (A4) | Campos "Total de parcelas do contrato" e "Parcelas já pagas": só as pendentes são criadas, numeradas no contrato (ex.: 5/12). Nenhuma regra financeira mudou | ✅ |
| [#54](https://github.com/Hkawzz/nk-pay/pull/54) | Preparar mês: domínio e serviço | Sugestões (fixas com o valor do mês anterior, parcelas para conferência, pendências), confirmação numa transação com um grupo de auditoria, pendência levada aparece também no mês novo sem mudar nada dela; repetir não duplica | ✅ |
| [#55](https://github.com/Hkawzz/nk-pay/pull/55) | Preparar mês: tela | Assistente em 4 passos (fixas marcadas e editáveis, parcelas para conferência com juros, pendências desmarcadas, revisão com total previsto); botão "Preparar [mês]" sempre em A pagar e card de sugestão na virada do mês | ✅ |
| [#56](https://github.com/Hkawzz/nk-pay/pull/56) | Qualidade e release | Backup da v0.6 ida e volta (compras com juros, juros pagos, antecipadas, pendência levada), roteiro `docs/roteiro-teste-v0.6.md`, CHANGELOG, README e tag `v0.6.0` | ✅ |

**Decisões tomadas sozinho**
- **A seção 8 da análise não existe no arquivo** (ele termina na seção 7). Segui a regra D-A1 (c) como o próprio briefing a descreve em detalhe; as fórmulas da regra 6B.8 vêm dessa descrição.
- **`compra.valorOriginalCentavos` nasce vazia** (vazia = igual ao total), sem reescrever nenhuma compra existente.
- **Os juros do parcelamento ficam ligados ao pagamento por um ajuste neutro** (`JUROS_DO_PARCELAMENTO`), como os juros recebidos da v0.5 (no abatimento geral, ver abaixo).
- **O desconto num crédito com juros consome primeiro os juros** da parcela (mesmo princípio das antecipadas e do desconto concedido).
- **A marcação de pendência levada fica numa tabela nova** (`pendencia_levada`), e não numa coluna de `obrigacao`.
- **Formulário da compra:** o campo "Valor total" continua com esse nome (com a ajuda "o preço da compra, sem os juros") e ganhou "Valor da parcela (se tiver juros)". Zerado, ou igual a original ÷ N, é compra sem juros. Assim a tela e os testes da v0.5 continuam iguais.
- **Parcelas de uma compra com juros são todas iguais** (parcela × N = total); a parte da compra segue original ÷ N com os centavos na última.
- **Parcela paga em parte antes do app:** a divisão entre parte da compra e juros é proporcional (como num pagamento parcial), e os juros dessa parte ficam marcados como resolvidos sem despesa (D-A3 b).
- **Os juros a pagar de cada parcela nunca passam do restante dela**; parcelas quitadas (inclusive pela fatura) não têm juros a pagar.
- **Uma despesa de juros por parcela** ("Juros do parcelamento: Celular (1/12)"), e não uma por pagamento: fica claro de qual compra veio cada juro.
- **Abatimento geral com juros:** os juros proporcionais são distribuídos pelas parcelas da fatura, da primeira para a última, cada uma até os juros pendentes dela; como não há ligação, o ajuste guarda "geral:&lt;transferência&gt;" e o Desfazer o encontra por aí.
- **Desconto com juros (crediário):** o abatimento na parcela é o desconto inteiro (entra em Descontos obtidos); a parte que cabe nos juros só reduz a despesa de juros, e só o que passar deles vira ajuste na conta (reduz o devido).
- **Antecipadas no formulário:** um campo com os números ("11, 12") e, para cada uma, "Valor pago" e "Desconto" ligados (mudar um recalcula o outro); começa com o valor previsto (sem desconto). A parcela antecipada fica quitada; o ajuste no cartão soma a parte da compra inteira dela.
- **Pessoas ganharam uma tela própria** (Empréstimos → Pessoas), com o nome, Arquivar/Desarquivar e Excluir; antes não havia onde gerenciar a pessoa. O Excluir do cartão fica no formulário de edição; o Arquivar continua no topo da tela do cartão, agora com o motivo quando é recusado.
- **"Em aberto" para arquivar** = saldo da conta diferente de zero ou alguma parcela com restante (o que for maior aparece na mensagem). Contas próprias seguem a regra antiga (só a exclusão é bloqueada por movimentações).
- **Empréstimo anterior:** com o total informado, pagas + pendentes precisam fechar o total (senão a numeração ficaria errada); sem o total, ele é pagas + pendentes. Sem nada informado, fica como na v0.5 (1/N).
- **A sugestão de preparar o mês** é um card em A pagar quando o mês escolhido ainda tem contas fixas a lançar (só pendências antigas não bastam: elas existiriam sempre e o card nunca sairia).
- **Pendências do Preparar mês** são as contas a pagar fixas e avulsas. Parcelas de cartão, crediário e empréstimo ficam fora: elas já aparecem nas suas seções (faturas atrasadas no mês original pela 6B.4; parcelas de empréstimo atrasadas já são listadas em A pagar).
- **No mês novo, a pendência levada aparece em "Deste mês"** (com "ref." do mês original e o status Atrasada) e sai de "Pendentes de meses anteriores" daquele mês, para não aparecer duas vezes. Nos meses seguintes ela volta a ser uma pendência anterior comum.
- **Assistente em tela cheia, não em diálogo** (diálogos com listas travaram o Robolectric na v0.4). O botão "Preparar [mês]" fica sempre em A pagar; o card de sugestão aparece no mês atual, ou no próximo durante a última semana do mês, enquanto houver contas fixas a lançar. O "Lançar contas fixas" da v0.3 continua lá.
- **O serviço do Preparar mês confere tudo antes de gravar**: uma falha no meio não deixa meio preparo.

**Portões do briefing para publicar**

| Portão | Resultado |
|---|---|
| CI verde | ✅ em todos os PRs (#47 a #56) |
| Testes da v0.5 passando; mudanças inevitáveis justificadas | ✅ os **201** testes da v0.5 passam. **4 linhas** mudaram em 2 arquivos (`MigracaoV4Test` e `MigracaoV3Test`), todas pela versão 5 do banco (ver Problemas). Conferido com `git diff v0.5.0` |
| Migração v4 fiel → v5 | ✅ `MigracaoV5Test` (banco v4 com dados em todas as tabelas, linhas idênticas campo a campo, compras sem juros) |
| Matriz de backup (v0.5 → v0.6 restaura; v0.6 ida e volta com compras com juros e antecipadas; v0.5 recusa v0.6) | ✅ `MigracaoV5Test` e `BackupV06Test`. O backup da v0.6 tem versão 5, e a v0.5 recusa versões maiores que a 4 |
| Regressão do Pagar das contas fixas, do crédito e dos empréstimos | ✅ `PagamentoServicoTest`, `APagarTest`, `PagamentoCreditoTest`, `CompraServicoTest`, `FaturaUiTest`, `CrediarioUiTest`, `PagamentoEmprestimoTest` e `EmprestimoServicoTest` sem alteração |

No total, **231 testes** (201 da v0.5 + 30 novos), todos passando.

**Proteção dos dados reais**
- A migração 4→5 só acrescenta `compra.valorOriginalCentavos` vazia (as compras antigas continuam sem juros) e cria a tabela `pendencia_levada`. A cópia do banco antes de migrar continua valendo.
- Backups da v0.5 restauram na v0.6; a v0.5 recusa backups da v0.6.
- Nenhum dado real no repositório. APKs de debug (v0.5.0 e v0.6.0, mesma chave de debug) numa pasta local do computador, fora do repositório, para o roteiro.

**Limitação conhecida** (nota para o futuro, [0025](decisoes/0025-juros-do-parcelamento.md)): pela D-A3 (b), os juros das parcelas pagas antes do app não entram nas Análises. Se o app for usado por outras pessoas, os juros e os descontos de antes do app precisarão entrar nelas de forma completa.

**Pendências:** nenhuma. A instalação no app principal continua sendo decisão sua, depois do roteiro no app de debug.

**Problemas encontrados:**
- O arquivo de análise não tem a "seção 8" citada no pedido; segui o briefing, que descreve a mesma regra (D-A1 c) em detalhe.
- Preparar mês (#54): o primeiro rascunho do serviço podia gravar parte do preparo antes de recusar uma pendência inválida (retornar uma falha dentro da transação não a desfaz). O teste pegou; o serviço passou a conferir tudo antes de gravar.
- APK de debug: um build local falhou por causa das aspas no parâmetro de versão e, por um instante, a cópia levou o APK antigo com o nome da v0.6. Apaguei a cópia, compilei de novo e conferi a versão dentro do APK (`0.6.0-debug`, código 600).
- Banco v5 (#48): 4 linhas de testes antigos mudaram, sem como evitar, porque testam "termina na versão atual" e a forma exata da tabela compra. No `MigracaoV4Test`: a versão esperada passou a ser `NkPayBanco.VERSAO`, a foto da compra ignora a coluna nova `valorOriginalCentavos=NULL` e o nome da cópia passou a ser `banco-v3-antes-da-v5`. No `MigracaoV3Test`: a comparação do SQL da tabela compra desconta a coluna nova. Nenhuma regra testada mudou; o resto dos testes da v0.5 ficou igual.

---

## v0.5.0 (em validação) - Empréstimos, Resumo pelo fluxo e ajuda "?"

**Objetivo:** empréstimos recebidos (eu devo) e concedidos (me devem), com juros J1 e K2; o Resumo da Início pelo fluxo do dinheiro próprio; o Saldo disponível; a ajuda "?"; e refinos das Análises. Fontes: `planejamento/especificacao-v0.5.0.md` e `planejamento/briefing-v0.5-autonomo.md` (decisões D1 a D5).

**Como foi feito:** de forma autônoma, na ordem de PRs da seção 6 da especificação. Cada PR termina com o CI verde e esta seção atualizada.

**PRs**

| # | Etapa | O que entrou | Estado |
|---|---|---|---|
| [#36](https://github.com/Hkawzz/nk-pay/pull/36) | 1 | Regras 6E (empréstimos, fluxo generalizado, Início) e decisões 0020 a 0023. Sem código | ✅ |
| [#37](https://github.com/Hkawzz/nk-pay/pull/37) | 2 | Fluxo com as quantidades; Resumo da Início pelo fluxo (Entradas, Saídas, Saldo das movimentações); card "Saldo disponível" (Saldo total e Guardado só com Guardado > 0) | ✅ |
| [#38](https://github.com/Hkawzz/nk-pay/pull/38) | 3 | Ajuda "?" (`NkAjuda`) com todos os textos num arquivo só, conferidos contra o domínio; aplicada à Início | ✅ |
| [#39](https://github.com/Hkawzz/nk-pay/pull/39) | 4 | Análises: "Despesas registradas" × "Saídas do dinheiro próprio" com "?", mês atual destacado e resultado em texto na evolução, "?" em Resultado, Descontos e Cartões | ✅ |
| [#40](https://github.com/Hkawzz/nk-pay/pull/40) | 5 | Banco v4: tabela `emprestimo`, `obrigacao.emprestimoId`, migração 3→4, backup com empréstimos, categoria "Juros recebidos" auditada. Migração com banco v3 fiel e a matriz de backup | ✅ |
| [#41](https://github.com/Hkawzz/nk-pay/pull/41) | 6 | Domínio de empréstimos (J1, K2 com fechamento exato, invariantes, atraso) e `ehContaDeTerceiros` como ponto único da integridade | ✅ |
| [#42](https://github.com/Hkawzz/nk-pay/pull/42) | 7 | Área Empréstimos: abas Recebidos/Concedidos agrupadas por pessoa, cadastro (automático ou personalizado, total e juros na hora), anterior ao app (D5) com aviso de duplicidade, tela do empréstimo com atraso e excluir | ✅ |
| [#43](https://github.com/Hkawzz/nk-pay/pull/43) | 8 | Pagar (recebido) e receber (concedido, K2 com fechamento exato) parcelas, parciais, as quatro diferenças, desfazer em qualquer ordem e edição das parcelas sem pagamento. O Pagar das contas fixas não mudou | ✅ |
| [#44](https://github.com/Hkawzz/nk-pay/pull/44) | 9 | Seção Empréstimos nas Análises (a pagar, a receber, juros pagos e recebidos, atrasadas), descontos concedidos separados dos obtidos, parcelas de empréstimo recebido em A pagar | ✅ |
| [#45](https://github.com/Hkawzz/nk-pay/pull/45) | 10 | Qualidade e release: backup com todos os tipos de empréstimo, roteiro `docs/roteiro-teste-v0.5.md`, "Arquivar" na tela do cartão ou crediário (para o roteiro do cartão fictício), CHANGELOG, README e tag `v0.5.0` | ✅ |

**Decisões tomadas sozinho** (a opção mais simples, mais segura para os dados e mais fácil de reverter; detalhes na [0021](decisoes/0021-emprestimos.md))
- **Juros K2 ligados ao recebimento** por um ajuste neutro (`JUROS_RECEBIDOS`), em vez de duas linhas de `pagamento`. O desfazer da v0.4 já remove esse ajuste e a entrada de juros junto, sem código novo de exclusão.
- **Principal e juros não reconhecidos são calculados** a partir das movimentações de cada empréstimo, nunca gravados.
- **Edição de parcela vira acréscimo ou abatimento** (o previsto nunca muda), datada no dia da edição.
- **Total das parcelas menor que o valor original é recusado.**
- **A conta própria do empréstimo** só pode ser de dinheiro próprio.
- **Desconto concedido** separado do obtido nas Análises, pela natureza da conta.
- **A pagar:** cada parcela de empréstimo recebido aparece como uma linha.
- **Desfazer foi generalizado:** aceita a movimentação principal de qualquer pagamento de terceiros (cartão, crediário, empréstimo), inclusive uma entrada de juros, e recusa o que não estiver ligado a parcelas de terceiros (nunca desfaz o Pagar das contas fixas por esse caminho).
- **Receber tudo como juros** (caso raro em que não resta principal): a entrada de juros vira a movimentação principal do recebimento.
- **Edição "esta e as futuras"** só altera as parcelas seguintes que também não têm pagamento; a nova data vale para a primeira e as outras seguem mês a mês.
- **Sem a rosca** em Despesas por categoria (era opcional): as barras com valor e % já mostram tudo.
- **A "folha" da ajuda é um diálogo simples com texto** (componente padrão, igual em todas as versões do Android).
- **O título dos benefícios na Início continua "Benefícios · fora do Disponível"** (o roteiro de aceite da v0.1 confere esse texto); o "?" ao lado explica.

**Portões do briefing para publicar**

| Portão | Resultado |
|---|---|
| CI verde em todos os PRs | ✅ (#36 a #45) |
| Testes da v0.4 passando; mudanças inevitáveis justificadas | ✅ os **160** testes da v0.4 passam. **6 linhas** mudaram, todas justificadas nos PRs #37 e #40 e abaixo: `AceiteTest` (1, pela D1) e `MigracaoTest`/`MigracaoV3Test` (5, pela versão 4 do banco). Conferido com `git diff v0.4.0` |
| Migração v3 fiel → v4 | ✅ `MigracaoV4Test` |
| Matriz de backup (v0.4 → v0.5 restaura; v0.5 com empréstimos ida e volta; v0.4 recusa v0.5) | ✅ `MigracaoV4Test` e `BackupEmprestimosTest` |
| Regressão do Pagar das contas fixas e do crédito da v0.4 | ✅ `PagamentoServicoTest`, `APagarTest`, `PagamentoCreditoTest`, `CompraServicoTest`, `FaturaUiTest` e `CrediarioUiTest` sem alteração |

**Proteção dos dados reais**
- A migração 3→4 só cria a tabela `emprestimo` e acrescenta `obrigacao.emprestimoId` vazia. Foi testada com um banco igual ao da v0.4 (inclusive cartões, compras e abatimentos de fatura), com todas as linhas idênticas campo a campo.
- A cópia do arquivo do banco antes de migrar continua valendo.
- Backups da v0.4 restauram na v0.5 (e a categoria "Juros recebidos" é recriada com auditoria). A v0.4 recusa backups da v0.5.
- Nenhum dado real no repositório. APKs de debug (v0.4.0 e v0.5.0, mesma chave de debug) numa pasta local do computador, fora do repositório, para o roteiro.

**Pendências**
- **Teste no seu celular**, pelo roteiro `docs/roteiro-teste-v0.5.md` (migração de verdade sobre uma cópia, empréstimos e a passagem de "cartões fictícios" para empréstimos).
- **Desempenho** no aparelho não medido.
- **A v0.6 (Preparar mês)** vai precisar considerar as parcelas de empréstimo (registrado na especificação; nada a fazer agora).
- Recebimentos que forem **só juros** (sem principal) ficam sem a linha de "juros recebidos" separada na tela do empréstimo; o total das Análises os conta. Caso raro, sem efeito nos saldos.

**Problemas encontrados**
- **Uma linha de um teste antigo mudou** (`AceiteTest`, da v0.1): ele esperava ver na Início o valor de uma despesa paga com o VA como total do mês. Pela D1, ela não é saída do dinheiro próprio; a linha agora procura a despesa nas últimas movimentações. **Contradição anotada:** a regra aprovada (D1) vence o teste antigo, como manda o briefing.
- **Cinco linhas de testes de migração antigos mudaram** por causa da versão 4 do banco, que a especificação pede: `MigracaoTest` (1, SQL da `obrigacao` sem a coluna nova) e `MigracaoV3Test` (4: versão atual em 3 lugares e a `obrigacao` sem o sufixo da coluna nova, que também confere que ela nasce vazia). Todas são do mesmo tipo das feitas na v0.4.
- **Um teste novo deixava uma exceção "vazar" para o teste seguinte no CI** (`AjudaTest` abria o banco também para testes puros). Os testes puros foram para um arquivo sem banco.
- **A primeira tentativa de publicar a v0.5.0 falhou** (nada foi publicado): o workflow de Release roda os testes de novo, e um teste novo (`EmprestimosUiTest`) lia a visão de empréstimos antes de a lista do banco chegar. O teste passou a esperar a lista (e o `EmprestimosAnalisesTest` passou a esperar todas as listas). No mesmo PR, o CI acusou outra oscilação, num teste da v0.4 (`CrediarioUiTest`, "banco fechado"): as visões do `AppContainer` continuavam lendo o banco em segundo plano depois que um teste o fechava, e o erro estourava no teste seguinte. **Correção no app, sem mudar teste antigo:** as visões passam a manter o último valor quando o banco é fechado no meio de uma leitura (qualquer outro erro continua aparecendo). A tag `v0.5.0` foi recriada no commit corrigido, depois do CI verde (PR #46).
- **O roteiro do cartão fictício pedia "arquivar a conta"**, e a tela do cartão não tinha essa ação: entrou um botão "Arquivar" nela (PR #45).

**Testes:** 201, todos com dados fictícios (eram 160 na v0.4), incluindo a migração v3→v4, a matriz de backup com empréstimos, o fluxo linha a linha, J1 e K2 com centavos, as quatro diferenças, desfazer fora de ordem, edição, anterior ao app e roteiros pela interface.

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
| [#29](https://github.com/Hkawzz/nk-pay/pull/29) | F5 | Pagamento em modo crédito num serviço separado: transferência (nunca despesa), desconto como ajuste no crédito, excedente como encargo classificado, distribuição entre faturas da mais antiga para a mais nova, compras específicas, desfazer. Tela da parcela do crediário. O Pagar das contas fixas não mudou | ✅ |
| [#30](https://github.com/Hkawzz/nk-pay/pull/30) | F6 | Tela da fatura (pagar um valor ou escolher compras, faturas anteriores primeiro, encargo classificado, desfazer) e as faturas em A pagar, deste mês e atrasadas | ✅ |
| [#31](https://github.com/Hkawzz/nk-pay/pull/31) | V2 | Nova tela Início (adendo): Disponível em destaque, grades de contas (2 colunas) e benefícios (3 colunas) com Ver todas/Recolher, resumo do mês com seletor e quantidades, últimas movimentações por dia. Design v2 nas outras telas pelos componentes | ✅ |
| [#32](https://github.com/Hkawzz/nk-pay/pull/32) | A1 | Cálculos das Análises (regra 6D): totais, fluxo do dinheiro próprio, despesas por categoria, saldos no fim do mês, evolução mensal, crédito e descontos obtidos (sem quitada antes do app) | ✅ |
| [#33](https://github.com/Hkawzz/nk-pay/pull/33) | A2 | Tela de Análises em Mais: filtros (guardados na memória), resultado, categorias, evolução mensal, fluxo, contas e crédito, com gráficos próprios e valores em texto | ✅ |
| [#34](https://github.com/Hkawzz/nk-pay/pull/34) | Q | Rodada de qualidade: backup e restauração com todos os registros de crédito, roteiro de teste no celular, app de debug com o nome "NK Pay debug" | ✅ |
| [#35](https://github.com/Hkawzz/nk-pay/pull/35) | R | CHANGELOG, README e este relatório; tag `v0.4.0` | ✅ |

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
- **Dívidas:** o total devido aparece em Mais → Cartões e crediários (por tipo e por conta) e, mês a mês, nas faturas da aba A pagar. Não criei uma tela à parte de Dívidas (opção mais simples).
- **Início:** o título dos benefícios continua "Benefícios · fora do Disponível" (deixa claro que não somam). Uma entrada sem categoria mostra o ícone neutro. O estado de "Ver todas" não é salvo (como pede o adendo).
- **Fluxo financeiro:** ajustes ficam fora (são correções, não dinheiro entrando ou saindo); uma transferência de benefício para conta própria conta como entrada, e o contrário como saída.
- **Descontos obtidos** são datados pelo pagamento que os criou.
- **Análises sem a referência visual** (não estava disponível): segui a lista 2.6 da proposta e o design v2. A ordem das seções fica em ORDEM_DAS_SECOES, para ajustar sem mexer no resto. Gráficos próprios em Canvas, sem biblioteca nova.
- **Movimentações de conta de crédito** (compra, encargo, desconto, pagamento) ficam bloqueadas em Movimentações, com uma mensagem que aponta para a tela do cartão.

**Portões do briefing para publicar sem aprovação**

| Portão | Resultado |
|---|---|
| CI verde | ✅ em todos os 16 PRs |
| Os 97 testes antigos passam | ✅ os 97 passam. **Ressalva:** 2 linhas do `MigracaoTest` mudaram, sem como evitar (ver Problemas). Li o portão junto com a regra 5 do briefing ("se algum precisar mudar, justifique"), que permite essa justificativa. Os outros 96 não mudaram, conferidos contra a tag `v0.3.0` |
| Migração (banco v2 fiel → v3) e matriz de backup e restauração | ✅ `MigracaoV3Test` e `BackupCreditoTest` |
| Regressão do Pagar das contas fixas | ✅ `PagamentoServicoTest` (10) e `APagarTest` sem alteração |

Com os portões passando, publiquei a tag `v0.4.0`. **A instalação no app principal continua sendo decisão sua**, depois do roteiro no app de debug.

**Proteção dos dados reais**
- A migração 2→3 só cria tabelas e acrescenta colunas vazias. Foi testada com um banco igual ao da v0.3, com todas as linhas idênticas campo a campo.
- A cópia do arquivo do banco antes de migrar continua valendo.
- Backups da v0.3 restauram na v0.4, e a v0.3 recusa backups da v0.4.
- A aparência das categorias é um passo auditado, que nunca toca em tabelas financeiras.
- Nenhum dado real entrou no repositório. As imagens de referência da Início não foram copiadas.
- **APKs de debug** (v0.3.0 e v0.4.0, mesma chave de debug, `com.nkpay.app.debug`) ficam numa pasta local do computador, fora do repositório, para o roteiro `docs/roteiro-teste-v0.4.md`. O CI também guarda o APK de debug da `main` (artefato `apk-debug`), mas ele é assinado com outra chave de debug e não atualiza por cima do APK de debug local.

**Pendências**
- **Teste no seu celular** (roteiro `docs/roteiro-teste-v0.4.md`): a migração de verdade, sobre uma cópia, no app de debug. Não dá para fazer daqui.
- **Desempenho no celular** (efeitos e Análises com muitos dados): não foi medido num APK de release no A56.
- **Referência visual de Análises:** não estava disponível. Quando houver, ajuste `ORDEM_DAS_SECOES` e os componentes.
- **Revisão externa dos PRs F0, F1, F2, F5 e F6** (ChatGPT e o analista, seção 5 da proposta): não aconteceu, porque o dono estava indisponível. Os PRs ficam abertos para consulta no GitHub, e os testes automáticos cobrem os exemplos numéricos das regras.

**Problemas encontrados**
- **Duas linhas de um teste antigo (`MigracaoTest`, "migrar de 1 para 2") precisaram mudar**, e não havia como evitar: o teste conferia `db.version == 2` e comparava todas as colunas da `categoria`. Com o banco v3, o caminho 1→2→3 termina na versão 3, e a categoria tem duas colunas novas no fim. As mudanças foram: (1) conferir `NkPayBanco.VERSAO`; (2) comparar as linhas sem o sufixo `|icone=NULL|cor=NULL`, o que **também confere que as colunas novas nascem vazias**. Todo o resto do teste (todas as linhas idênticas, contagens, saldos) continua igual. **Contradição anotada:** o briefing pede os 97 testes "sem alteração" e também uma migração para a v3. As duas coisas não cabem juntas nesse teste, e a regra 5 do briefing permite mudar um teste com justificativa. Os outros 96 testes não mudaram.
- **O diálogo de categoria travava** o layout nos testes (grade de ícones dentro de um diálogo). A edição virou uma tela própria.
- **Dois testes de interface oscilavam** no CI: a visão de crédito junta listas que o banco entrega uma de cada vez. Os testes passaram a esperar as duas condições, e o aviso de invariante na tela do cartão só aparece se a diferença durar mais de 1,5 s.
- **Uma espera do script de PR terminava cedo** (`-match` sem diferenciar maiúsculas achava "merged" na dica do `gh`). Nada foi juntado sem o CI verde; o script passou a usar `-cmatch`.
- **Aspas curvas** em comandos do PowerShell quebraram uma troca de texto no código. O erro apareceu na compilação e foi corrigido antes do PR.
- O app de debug tinha o mesmo nome do principal ("NK Pay"). A partir da v0.4, ele se chama "NK Pay debug". O roteiro explica como conferir a versão `-debug` antes de restaurar qualquer coisa.

**Testes:** 160, todos com dados fictícios (eram 97 na v0.3), incluindo migração v2→v3, a matriz de backup, crédito (compras, faturas, compra anterior, pagamentos, desfazer, invariante), Análises e roteiros pela interface com capturas no Android 8, 9 e atual.

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
- Instalados Git, GitHub CLI, JDK 21, Gradle e Android SDK. Como o app do Claude é da Microsoft Store, o Windows redirecionava as gravações em `AppData`; por isso, as ferramentas ficam numa pasta local própria, fora do `AppData`.
- Repositório privado `Hkawzz/nk-pay` criado.
- **Keystore de release** criada por você (você digitou a senha) e guardada numa pasta local fora do repositório, com os 4 GitHub Secrets cadastrados. Impressão digital: `E4:08:56:3D:…:FC:64`.

**Decisões tomadas** ([0001](decisoes/0001-stack.md) a [0007](decisoes/0007-app-de-debug-separado.md))
- Stack Kotlin + Compose + Room, um módulo, sem Hilt.
- Dinheiro em centavos inteiros.
- Uma keystore única, guardada nos Secrets.
- `versionCode` derivado da tag (0.1.0 → 100).
- compileSdk 37.2.
- Backup automático do Android desligado.
- App de debug com outro identificador.
