# Roteiro do teste profundo (v0.8)

**Objetivo:** conferir **todo o app**, área por área, com números. Tudo o que não bater vai para [`registro-de-problemas-v0.9.md`](registro-de-problemas-v0.9.md). O que é novo na v0.8 está marcado com **(v0.8)**.

**Onde testar:** no **app de debug** ("NKote debug", `0.8.0-debug` em Mais → Sobre), nunca no principal. Veja a Parte 0 para levar uma cópia dos seus dados para lá.

**Como ler cada item:** `ação` → **resultado esperado**. Os valores são de exemplo: use valores pequenos. Quando um item diz "+R$ X" ou "−R$ X", confira a **diferença** em relação ao que estava antes, porque o seu ponto de partida pode ser outro. Marque `[x]` no que passou; o que falhar, anote no registro (tela · o que fez · o que esperava · o que apareceu · gravidade).

> ⚠️ O backup tem os seus dados financeiros. Ele **não sai do celular**. Não mande prints com valores reais para fora do celular.

---

## 0. Preparação e migração (app de debug) (v0.8)
O NKote é **outro app** para o Android (identificador novo): os dados chegam pelo backup.
- [ ] NK Pay (principal, v0.7) → Mais → Backup e restauração → **Fazer backup agora** → o arquivo `.nkpay` aparece na pasta escolhida.
- [ ] Instale `nkote-v0.8.0-debug.apk` → aparece "NKote debug", **ao lado** do NK Pay; Mais → Sobre mostra **0.8.0-debug**; o app começa vazio.
- [ ] Mais → Backup → **Restaurar de um arquivo** → o `.nkpay` é aceito → tudo igual ao NK Pay: Saldo total, cada conta, benefícios, cartões, faturas, empréstimos e A pagar.
- [ ] Se havia objetivos na v0.7: eles aparecem como **caixinhas com R$ 0,00**, com o histórico antigo só para leitura, e a mensagem "viraram caixinhas" aparece **uma vez** (some depois de "Entendi"). Os saldos das contas **não** mudam.

## 1. Contas e carteiras (Mais → Contas e carteiras)
- [ ] Criar "Pix teste" (Dinheiro próprio) com saldo inicial R$ 100,00 → Saldo total **+R$ 100,00**. Entradas do mês **não** mudam (saldo inicial não é entrada).
- [ ] Criar "VA teste" (Benefício, VA) com R$ 50,00 → Saldo total **igual**; Benefícios **+R$ 50,00**, num bloco separado.
- [ ] Editar o nome do "Pix teste" → o nome muda nas listas; o Registro de alterações mostra a edição.
- [ ] Arquivar o "Pix teste" → some das listas de escolha, mas o saldo **continua** contando.
- [ ] Excluir uma conta **com** lançamentos → o app recusa e oferece Arquivar. Conta **sem** lançamentos → é excluída.
- [ ] **(v0.8)** As caixinhas **não** aparecem aqui como conta para lançar.

## 2. Movimentações (aba Movimentações)
- [ ] **Entrada** de R$ 50,00 no Pix teste → Pix **+R$ 50,00**; Saldo total **+R$ 50,00**; Entradas do mês **+R$ 50,00**.
- [ ] **Despesa** de R$ 20,00 no Pix teste (Alimentação) → Pix **−R$ 20,00**; Saldo total **−R$ 20,00**; Despesas do mês **+R$ 20,00**.
- [ ] **Transferência** Pix teste → Dinheiro de R$ 10,00 → Pix −R$ 10,00, Dinheiro +R$ 10,00; Saldo total, entradas e despesas **iguais**.
- [ ] **Despesa no VA** de R$ 5,00 → VA −R$ 5,00; Saldo total **igual**; Despesas do mês **+R$ 5,00** (é consumo).
- [ ] **Ajuste pelo novo saldo:** informe o saldo real do Pix teste R$ 1,00 abaixo do que o app mostra → o app cria um ajuste de **−R$ 1,00**; entradas e despesas **iguais**.
- [ ] Editar o valor de uma despesa → o saldo é recalculado. Excluir → o saldo volta. As duas operações aparecem no Registro de alterações.
- [ ] Cartões, crediários, contas de empréstimo e **caixinhas** **não** aparecem como conta para lançar.
- [ ] **(v0.8) Sem cheque especial:** com o Pix teste em R$ 40,00, lançar uma despesa de R$ 50,00 → **recusada**: "Saldo insuficiente no Pix teste (R$ 40,00). Registre a entrada antes ou escolha outra conta." Nada é gravado.
- [ ] **(v0.8)** O mesmo vale para: transferência maior que o saldo, despesa no VA maior que o saldo do VA, **editar** uma entrada para menos do que já foi gasto e **excluir** uma entrada já gasta → recusados com a mesma mensagem.
- [ ] **(v0.8)** Uma conta que **já estava** negativa (dados antigos) continua aparecendo; dá para lançar **entradas** nela, mas não saídas que a deixem mais negativa.
- [ ] **(v0.8) Excluir protegido:** excluir por aqui o pagamento de uma fatura, de uma parcela de empréstimo, um guardar/retirar de caixinha ou o pagamento de uma conta → aparece "Não dá para excluir por aqui", com o motivo e o botão **Abrir cartão / empréstimo / caixinha / conta a pagar**, que leva à tela onde se desfaz.

## 3. A pagar e Preparar mês (aba A pagar)
- [ ] Mais → Contas fixas → "Aluguel teste" R$ 80,00, vence dia 10. A pagar → **Lançar contas fixas** → ela entra no mês com vencimento no dia 10.
- [ ] Lançar de novo no mesmo mês → **não** duplica ("já lançadas").
- [ ] **Pagar** R$ 30,00 (parcial) → status **Parcial**, restante **R$ 50,00**; despesa de R$ 30,00 no mês.
- [ ] Pagar R$ 52,00 classificando R$ 2,00 como **multa** → quitada; despesa de R$ 52,00 (R$ 50,00 + R$ 2,00 de multa em Encargos financeiros).
- [ ] Numa conta de R$ 40,00, pagar R$ 35,00 com **desconto** → quitada; despesa de R$ 35,00; "Descontos obtidos" nas Análises **+R$ 5,00**.
- [ ] **(v0.8)** Pagar com uma conta sem saldo suficiente → **recusado** com "Saldo insuficiente…". As caixinhas **não** aparecem entre as contas para pagar.
- [ ] **Desfazer** um pagamento → a conta volta ao status anterior e a despesa some.
- [ ] Conta avulsa vencida e não paga de um mês anterior → aparece em "Pendentes de meses anteriores", **Atrasada**, no mês original.
- [ ] **Preparar [próximo mês]:**
  - [ ] Passo 1: as fixas ainda não lançadas, **todas marcadas**, com o valor do mês anterior (editável).
  - [ ] Passo 2: faturas, crediários e empréstimos do mês, só para conferência, com "inclui R$ X de juros" quando houver.
  - [ ] Passo 3: pendências antigas, **todas desmarcadas**.
  - [ ] Passo 4: total previsto; **antes de confirmar, nada mudou** em A pagar.
  - [ ] Confirmar: as fixas marcadas entram com o valor escolhido; a pendência marcada aparece em "Deste mês" com "ref." do mês original e **Atrasada**. Rodar de novo **não duplica nada**.
  - [ ] Caixinhas **não** aparecem no Preparar mês.

## 4. Cartão de crédito (Mais → Cartões e crediários)
Crie "Cartão teste": fecha dia 5, vence dia 12, limite R$ 2.000,00.
- [ ] **Compra** de R$ 100,00 em 3x, feita no dia 3 → parcelas **R$ 33,33 + R$ 33,33 + R$ 33,34**; a 1ª vence no dia 12 do mesmo mês. Despesas do mês **+R$ 100,00**; Devido **R$ 100,00**; Limite disponível **R$ 1.900,00**. O Pix **não** muda.
- [ ] Compra feita **no dia 5** (dia do fechamento) → a 1ª parcela vai para a fatura seguinte (vence no dia 12 do mês seguinte).
- [ ] **Pagar a fatura** inteira (R$ 33,33) pelo Pix → transferência Pix → Cartão; Pix −R$ 33,33; **nenhuma despesa nova**; Devido **−R$ 33,33**; fatura **Paga**.
- [ ] **(v0.8)** Pagar a fatura com uma conta sem saldo suficiente → **recusado**.
- [ ] Pagar **parte** de uma fatura (ex.: R$ 10,00) → abatimento geral; a fatura fica **Em parte** com o restante.
- [ ] Pagar **compras escolhidas** → só elas ficam pagas.
- [ ] Pagar **mais** que a fatura classificando como **juros** → despesa do excedente em Encargos financeiros.
- [ ] Fatura vencida e não paga → **Atrasada** no mês original; não passa para a fatura seguinte.
- [ ] **Desfazer** cada pagamento → tudo volta.
- [ ] Excluir uma compra **sem** pagamento → a despesa, as parcelas e o devido somem.
- [ ] **(v0.8)** Excluir uma compra **com** pagamento → recusado, com a **lista** de cada pagamento ou abatimento (data, valor, fatura) e **Desfazer** em cada um. Desfeitos todos, a exclusão passa.

## 5. Compra parcelada com juros
- [ ] Nova compra: Valor total **R$ 300,00**, 12 parcelas, Valor da parcela **R$ 45,50** → o app mostra **12x de R$ 45,50 = R$ 546,00; juros do parcelamento R$ 246,00**.
- [ ] Depois de registrar: despesa da compra **R$ 300,00**; Devido **R$ 300,00**; card **Juros a pagar R$ 246,00** (com "?").
- [ ] Parcela: "Parte da compra **R$ 25,00** · juros do parcelamento **R$ 20,50**".
- [ ] Pagar a fatura com a 1ª parcela (R$ 45,50) → despesa de **R$ 20,50** em Encargos financeiros na data do pagamento; Devido **R$ 275,00**; Juros a pagar **R$ 225,50**.
- [ ] Desfazer → a despesa de juros some junto; Devido volta a R$ 300,00.
- [ ] Valor da parcela que dá total **menor** que o valor da compra → recusado.

## 6. Crediário
Crie "Loja teste" (crediário, vence dia 20).
- [ ] Compra de R$ 300,00 em 2x → a 1ª vence no dia 20 do mês seguinte; despesa de R$ 300,00 na data da compra.
- [ ] Parcela de R$ 150,00 paga com R$ 140,00 e **desconto** → transferência de R$ 140,00; parcela paga; Devido **−R$ 150,00**; entradas e despesas **iguais**; "Descontos obtidos" **+R$ 10,00**.
- [ ] Parcela com juros paga com desconto → o desconto sai **primeiro dos juros** (a despesa de juros fica menor).

## 7. Compras anteriores ao app e antecipadas
- [ ] Compra anterior: R$ 1.200,00 em 12x, de uma data de 4 meses atrás, **4 já pagas** → Devido **R$ 800,00**; despesa de R$ 1.200,00 **no mês original**; o Pix **não** muda; "quitada antes do app" **não** entra em Descontos obtidos.
- [ ] Com **parcela antecipada**: R$ 300,00, 12 × R$ 45,50, 4 pagas, antecipada a 12 com valor pago R$ 40,00 (o Desconto mostra R$ 5,50) → Devido **R$ 175,00**; nenhuma despesa de juros; Descontos obtidos **+R$ 5,50 no mês da compra**.
- [ ] Antecipar uma parcela que já está entre as pagas → recusado.
- [ ] Se já existir algo parecido no app → aparece o aviso "Isto pode já estar no app".

## 8. Empréstimos (Mais → Empréstimos)
- [ ] **Peguei (eu devo):** R$ 100,00 entrou no Pix, 2 parcelas de R$ 55,00 → o app mostra na hora total **R$ 110,00** e juros **R$ 10,00**; Pix **+R$ 100,00**; despesa de **R$ 10,00** em Encargos financeiros (uma vez, na data); as parcelas aparecem em A pagar → Empréstimos a pagar.
- [ ] Pagar a parcela 1 (R$ 55,00) → Pix −R$ 55,00, **nenhuma despesa nova**. Desfazer volta tudo.
- [ ] **Emprestei (me devem):** R$ 100,00 saindo do Pix, 2 × R$ 55,00 → Pix **−R$ 100,00**, sem despesa; as parcelas **não** aparecem em A pagar.
- [ ] **(v0.8)** Emprestar mais do que há no Pix, ou pagar uma parcela sem saldo → **recusado**.
- [ ] Receber a parcela 1 (R$ 55,00) → "Devolução de **R$ 50,00**" + "Juros recebidos de **R$ 5,00**"; Pix +R$ 55,00; entrada de R$ 5,00 em Juros recebidos.
- [ ] Receber a menos com **desconto** → parcela paga, sem despesa; aparece em "Descontos concedidos".
- [ ] Parcela vencida → "Atrasada · vencida há N dias". "Alterar esta parcela" só sem pagamento.
- [ ] **Anterior ao app** com "Total de parcelas do contrato 12" e "Parcelas já pagas 4" → parcelas **5/12 … 12/12**; o Pix **não** muda.

## 9. Pessoas e excluir/arquivar
- [ ] Empréstimos → **Pessoas** → mudar o nome → os empréstimos continuam com ela; o Registro mostra a edição.
- [ ] Arquivar uma pessoa que ainda deve ou te deve → **recusado** com o valor. Sem nada em aberto → arquiva; "Mostrar arquivadas" → Desarquivar.
- [ ] Cartão com devido → Arquivar **recusado**. Cartão sem nada → Editar → **Excluir**. Com compras → o app explica e oferece Arquivar.

## 10. Caixinhas (aba Objetivos) (v0.8)
Cada objetivo tem a sua **caixinha**: uma conta de dinheiro seu, separada, que **não** paga nada. Parta do Pix teste com **R$ 1.000,00** e nada guardado.
- [ ] Criar "Fone teste" com meta **R$ 300,00** → aparece em Ativos com **R$ 0,00** e **0%**.
- [ ] **Guardar R$ 300,00 do Pix teste** → Pix **−R$ 300,00**; caixinha **R$ 300,00**; **Saldo total igual**; Guardado **R$ 300,00**; entradas e despesas **iguais**; "meta atingida".
- [ ] A **Início** mostra **Saldo total** e **Guardado R$ 300,00**.
- [ ] Guardar **mais do que há no Pix** (ex.: R$ 800,00) → **recusado**: "Saldo insuficiente no Pix teste (R$ 700,00)…".
- [ ] **Retirar R$ 100,00 para o Pix** → caixinha R$ 200,00; Pix **+R$ 100,00**; **nenhuma despesa**.
- [ ] Criar "Viagem teste" (sem meta) e **Mover R$ 50,00** do Fone → Fone R$ 150,00, Viagem R$ 50,00; Saldo total **igual**. O histórico mostra "Movido para…" e "Recebido de…".
- [ ] **Rendimento** de R$ 2,00 na Viagem → caixinha **+R$ 2,00**; **entrada** de R$ 2,00 na categoria **Rendimentos**; Saldo total **+R$ 2,00**; Análises → "Rendimentos no período" **+R$ 2,00**.
- [ ] **Desfazer** um guardar → o dinheiro volta para a conta de origem. Desfazer uma retirada **depois de gastar** o dinheiro → **recusado** (deixaria a conta negativa).
- [ ] **Prazo:** meta R$ 1.200,00, guardado R$ 300,00, prazo no 11º mês depois do atual (12 meses, contando o atual) → "guardar **R$ 75,00/mês**".
- [ ] **Gastar o dinheiro guardado:** retirar R$ 150,00 do Fone para o Pix e lançar a despesa no Pix. O "?" explica esse passo a passo. A caixinha **não** aparece em nenhuma lista de pagamento ou de despesa.
- [ ] **Concluir** e **arquivar** → vai para "Concluídos e arquivados"; o dinheiro **continua** contando no Guardado; não dá para guardar nele; dá para retirar e lançar rendimento.
- [ ] **Excluir** com dinheiro na caixinha → "Retire ou mova os R$ X antes". Zerada → excluído (fica no Registro; a caixinha é arquivada).
- [ ] Retirar tudo de todas as caixinhas → a Início volta a mostrar **só** o saldo.

## 11. Início (v0.8)
- [ ] **Saldo total** = contas de dinheiro próprio + caixinhas (VA/VR, cartões e empréstimos **fora**). Com algo guardado, aparece também **Guardado**.
- [ ] Resumo do mês: **Entradas**, **Saídas** e **Saldo das movimentações** do dinheiro próprio. Uma compra no cartão **não** é saída; o pagamento da fatura é. Despesa no VA **não** é saída. Guardar, retirar e mover **não** aparecem; rendimento é entrada.
- [ ] Abaixo do card principal, só quando têm conteúdo: **Próximos vencimentos** (até 7 dias à frente e os atrasados, com atalho para pagar), **Cartões** (devido e próxima fatura), **Caixinhas** (até 3, com "Ver todas") e **A receber**. Sem nada disso, nenhum bloco vazio aparece.
- [ ] Tocar num vencimento, num cartão ou numa caixinha abre a tela certa.
- [ ] Todos os "?" abrem: o que é, como é calculado, exemplo, o que não é.
- [ ] Aviso de backup aparece se o último tiver 7 dias ou mais. Se o backup automático falhar, aparece "O backup automático falhou".

## 12. Análises (Mais → Análises)
- [ ] Filtros: Este mês, 3, 6 e 12 meses → os números mudam de acordo.
- [ ] "Despesas registradas" × "Saídas do dinheiro próprio" (podem ser diferentes; cada um com "?").
- [ ] Despesas por categoria somam as despesas do período.
- [ ] Cartões e crediários; Empréstimos (a pagar, a receber, juros pagos e recebidos); Descontos obtidos × concedidos.
- [ ] **Objetivos:** guardado agora, guardado e retirado no período (mover não conta), **rendimentos no período** e o progresso de cada um.

## 13. Backup e restauração (Mais → Backup)
- [ ] Fazer backup → o arquivo `nkote-backup-….nkote` é criado; a tela mostra a data do último.
- [ ] Mudar alguma coisa e **restaurar** → tudo volta ao estado do backup, **inclusive as caixinhas e o histórico**; a cópia "antes de restaurar" aparece.
- [ ] Restaurar um `.nkpay` da v0.7 → funciona (ver Parte 0).
- [ ] **(v0.8) Backup automático:** sem pasta escolhida → "Nenhuma pasta escolhida ainda" e "Fazer agora" desabilitado.
- [ ] Escolher uma pasta (pode ser do Drive) → o nome aparece; a frequência começa em **Diário** (também há Semanal e Mensal, sem opção de desligar).
- [ ] **Fazer agora** → aparece `nkote-auto-….nkote` na pasta e "Último: [data] · deu certo".
- [ ] Depois de 8 ou mais automáticos, ficam **só os 7 mais recentes** da série `nkote-auto-`; os backups manuais da mesma pasta **nunca** somem.
- [ ] Apagar a pasta escolhida (ou tirar a permissão) e tocar em "Fazer agora" → "falhou", com a mensagem, e a Início avisa.
- [ ] Em Samsung: o texto de bateria aparece, e "Abrir otimização de bateria" abre a tela do sistema.

## 14. Registro de alterações (Mais → Registro de alterações)
- [ ] Cada operação acima aparece, agrupada (ex.: um pagamento com encargo = um grupo).
- [ ] Guardar, retirar, mover, rendimento e desfazer de caixinhas aparecem.

## 15. Sobre (Mais → Sobre) (v0.8)
- [ ] Nome **NKote**, versão, política de privacidade (texto provisório) e o e-mail de suporte. Não há botão Apoiar.

---

## Ao terminar
- Anote tudo em [`registro-de-problemas-v0.9.md`](registro-de-problemas-v0.9.md).
- Para passar para o app principal, siga o fim do [`roteiro-teste-v0.8.md`](roteiro-teste-v0.8.md): backup no NK Pay → instalar `nkote-v0.8.0.apk` → restaurar o `.nkpay` → conferir → só então desinstalar o NK Pay.
