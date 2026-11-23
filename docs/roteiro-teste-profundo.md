# Roteiro do teste profundo (v0.7 → v0.8)

**Objetivo:** conferir **todo o app**, área por área, com números, antes da v0.8 (a etapa final). Tudo o que não bater vai para [`registro-de-problemas-v0.8.md`](registro-de-problemas-v0.8.md).

**Onde testar:** no **app de debug** ("NK Pay debug", `0.7.0-debug` em Mais → Sobre), nunca no principal. Veja a Parte 0 para levar uma cópia dos seus dados para lá.

**Como ler cada item:** `ação` → **resultado esperado**. Os valores são de exemplo: use valores pequenos. Quando um item diz "+R$ X" ou "−R$ X", confira a **diferença** em relação ao que estava antes, porque o seu ponto de partida pode ser outro. Marque `[x]` no que passou; o que falhar, anote no registro (tela · o que fez · o que esperava · o que apareceu · gravidade).

> ⚠️ O backup tem os seus dados financeiros. Ele **não sai do celular**. Não mande prints com valores reais para fora do celular.

---

## 0. Preparação e migração (app de debug)
- [ ] App principal → Mais → Backup e restauração → **Fazer backup agora** → o arquivo `.nkpay` aparece na pasta escolhida.
- [ ] Instale `nk-pay-v0.6.0-debug.apk` → Mais → Sobre mostra **0.6.0-debug**. Restaure o backup → contas e saldos **iguais** aos do principal.
- [ ] Instale `nk-pay-v0.7.0-debug.apk` por cima → Mais → Sobre **0.7.0-debug** → tudo igual: Saldo disponível, cada conta, benefícios, cartões, faturas, empréstimos e A pagar. A aba **Objetivos** aparece vazia ("Nenhum objetivo ainda"), e a Início mostra **só** o Saldo disponível.

## 1. Contas e carteiras (Mais → Contas e carteiras)
- [ ] Criar "Pix teste" (Dinheiro próprio) com saldo inicial R$ 100,00 → Saldo disponível **+R$ 100,00**. Entradas do mês **não** mudam (saldo inicial não é entrada).
- [ ] Criar "VA teste" (Benefício, VA) com R$ 50,00 → Saldo disponível **igual**; Benefícios **+R$ 50,00**, num bloco separado.
- [ ] Editar o nome do "Pix teste" → o nome muda nas listas; o Registro de alterações mostra a edição.
- [ ] Arquivar o "Pix teste" → some das listas de escolha, mas o saldo **continua** contando no Saldo disponível.
- [ ] Excluir uma conta **com** lançamentos → o app recusa e oferece Arquivar. Conta **sem** lançamentos → é excluída.

## 2. Movimentações (aba Movimentações)
- [ ] **Entrada** de R$ 50,00 no Pix teste → Pix **+R$ 50,00**; Saldo disponível **+R$ 50,00**; Entradas do mês **+R$ 50,00**.
- [ ] **Despesa** de R$ 20,00 no Pix teste (Alimentação) → Pix **−R$ 20,00**; Saldo disponível **−R$ 20,00**; Despesas do mês **+R$ 20,00**.
- [ ] **Transferência** Pix teste → Dinheiro de R$ 10,00 → Pix −R$ 10,00, Dinheiro +R$ 10,00; Saldo disponível, entradas e despesas **iguais**.
- [ ] **Despesa no VA** de R$ 5,00 → VA −R$ 5,00; Saldo disponível **igual**; Despesas do mês **+R$ 5,00** (é consumo).
- [ ] **Ajuste pelo novo saldo:** informe o saldo real do Pix teste R$ 1,00 abaixo do que o app mostra → o app cria um ajuste de **−R$ 1,00**; entradas e despesas **iguais**.
- [ ] Editar o valor de uma despesa → o saldo é recalculado. Excluir → o saldo volta. As duas operações aparecem no Registro de alterações.
- [ ] Cartões, crediários e contas de empréstimo **não** aparecem como conta para lançar.

## 3. A pagar e Preparar mês (aba A pagar)
- [ ] Mais → Contas fixas → "Aluguel teste" R$ 80,00, vence dia 10. A pagar → **Lançar contas fixas** → ela entra no mês com vencimento no dia 10.
- [ ] Lançar de novo no mesmo mês → **não** duplica ("já lançadas").
- [ ] **Pagar** R$ 30,00 (parcial) → status **Parcial**, restante **R$ 50,00**; despesa de R$ 30,00 no mês.
- [ ] Pagar R$ 52,00 classificando R$ 2,00 como **multa** → quitada; despesa de R$ 52,00 (R$ 50,00 + R$ 2,00 de multa em Encargos financeiros).
- [ ] Numa conta de R$ 40,00, pagar R$ 35,00 com **desconto** → quitada; despesa de R$ 35,00; "Descontos obtidos" nas Análises **+R$ 5,00**.
- [ ] **Desfazer** um pagamento → a conta volta ao status anterior e a despesa some.
- [ ] Conta avulsa vencida e não paga de um mês anterior → aparece em "Pendentes de meses anteriores", **Atrasada**, no mês original.
- [ ] **Preparar [próximo mês]:**
  - [ ] Passo 1: as fixas ainda não lançadas, **todas marcadas**, com o valor do mês anterior (editável).
  - [ ] Passo 2: faturas, crediários e empréstimos do mês, só para conferência, com "inclui R$ X de juros" quando houver.
  - [ ] Passo 3: pendências antigas, **todas desmarcadas**.
  - [ ] Passo 4: total previsto; **antes de confirmar, nada mudou** em A pagar.
  - [ ] Confirmar: as fixas marcadas entram com o valor escolhido; a pendência marcada aparece em "Deste mês" com "ref." do mês original e **Atrasada**. Rodar de novo **não duplica nada**.
  - [ ] Objetivos **não** aparecem no Preparar mês.

## 4. Cartão de crédito (Mais → Cartões e crediários)
Crie "Cartão teste": fecha dia 5, vence dia 12, limite R$ 2.000,00.
- [ ] **Compra** de R$ 100,00 em 3x, feita no dia 3 → parcelas **R$ 33,33 + R$ 33,33 + R$ 33,34**; a 1ª vence no dia 12 do mesmo mês. Despesas do mês **+R$ 100,00**; Devido **R$ 100,00**; Limite disponível **R$ 1.900,00**. O Pix **não** muda.
- [ ] Compra feita **no dia 5** (dia do fechamento) → a 1ª parcela vai para a fatura seguinte (vence no dia 12 do mês seguinte).
- [ ] **Pagar a fatura** inteira (R$ 33,33) pelo Pix → transferência Pix → Cartão; Pix −R$ 33,33; **nenhuma despesa nova**; Devido **−R$ 33,33**; fatura **Paga**.
- [ ] Pagar **parte** de uma fatura (ex.: R$ 10,00) → abatimento geral; a fatura fica **Em parte** com o restante.
- [ ] Pagar **compras escolhidas** → só elas ficam pagas.
- [ ] Pagar **mais** que a fatura classificando como **juros** → despesa do excedente em Encargos financeiros.
- [ ] Fatura vencida e não paga → **Atrasada** no mês original; não passa para a fatura seguinte.
- [ ] **Desfazer** cada pagamento → tudo volta.
- [ ] Excluir uma compra **sem** pagamento → a despesa, as parcelas e o devido somem. Com pagamento → recusado até desfazer.

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
- [ ] Receber a parcela 1 (R$ 55,00) → "Devolução de **R$ 50,00**" + "Juros recebidos de **R$ 5,00**"; Pix +R$ 55,00; entrada de R$ 5,00 em Juros recebidos.
- [ ] Receber a menos com **desconto** → parcela paga, sem despesa; aparece em "Descontos concedidos".
- [ ] Parcela vencida → "Atrasada · vencida há N dias". "Alterar esta parcela" só sem pagamento.
- [ ] **Anterior ao app** com "Total de parcelas do contrato 12" e "Parcelas já pagas 4" → parcelas **5/12 … 12/12**; o Pix **não** muda.

## 9. Pessoas e excluir/arquivar
- [ ] Empréstimos → **Pessoas** → mudar o nome → os empréstimos continuam com ela; o Registro mostra a edição.
- [ ] Arquivar uma pessoa que ainda deve ou te deve → **recusado** com o valor. Sem nada em aberto → arquiva; "Mostrar arquivadas" → Desarquivar.
- [ ] Cartão com devido → Arquivar **recusado**. Cartão sem nada → Editar → **Excluir**. Com compras → o app explica e oferece Arquivar.

## 10. Objetivos (aba Objetivos) — novo na v0.7
Parta de um Saldo disponível conhecido (ex.: **R$ 1.000,00**, sem nada guardado).
- [ ] Criar "Fone teste" com meta **R$ 300,00** → aparece em Ativos com **R$ 0,00** e **0%**.
- [ ] **Guardar R$ 300,00** → Guardado **R$ 300,00**; Saldo disponível **R$ 700,00**; o saldo do Pix **não muda**; entradas, despesas e o Resumo da Início **não mudam**; "100% da meta" / "meta atingida".
- [ ] A **Início** passa a mostrar **Saldo total R$ 1.000,00** e **Guardado R$ 300,00**, além do Saldo disponível R$ 700,00.
- [ ] Guardar em outro objetivo **mais que o disponível** (ex.: R$ 800,00) → **recusado**, mostrando o disponível.
- [ ] **Retirar R$ 100,00** → Guardado R$ 200,00; disponível R$ 800,00; **nenhuma despesa**.
- [ ] Criar "Viagem teste" (sem meta) e **Mover R$ 50,00** do Fone → Fone R$ 150,00, Viagem R$ 50,00; disponível **igual** (R$ 800,00). O histórico mostra "Movido para…" e "Recebido de…".
- [ ] **Ajustar** a Viagem para R$ 80,00 → histórico com ajuste de **+R$ 30,00**; disponível R$ 770,00.
- [ ] **Desfazer** o mover depois de retirar tudo da Viagem → **recusado** (deixaria negativo).
- [ ] **Prazo:** meta R$ 1.200,00, guardado R$ 300,00, prazo no 11º mês depois do atual (12 meses, contando o atual; ex.: hoje em janeiro, prazo em dezembro) → "guardar **R$ 75,00/mês**".
- [ ] **Gastar o dinheiro guardado:** retirar R$ 150,00 do Fone e lançar a despesa de R$ 150,00 → Saldo total −R$ 150,00 e Saldo disponível **igual ao de antes da retirada**. O "?" explica esse passo a passo.
- [ ] **Guardado maior que o saldo:** com R$ 300,00 guardados, lance uma despesa que deixe o Saldo total abaixo de R$ 300,00 → Saldo disponível **negativo, em rosa**, com "Seu guardado é maior que o seu saldo…"; **nada é reduzido sozinho**; guardar fica bloqueado.
- [ ] **Concluir** e **arquivar** → o objetivo vai para "Concluídos e arquivados" e o guardado **continua** contando; não dá para guardar nele; dá para retirar.
- [ ] **Excluir** com valor guardado → "Retire ou mova os R$ X antes". Sem guardado → excluído (fica no Registro).
- [ ] Retirar tudo de todos os objetivos → a Início volta a mostrar **só** o Saldo disponível.

## 11. Início
- [ ] Saldo disponível = soma das contas de dinheiro próprio − Guardado (VA/VR, cartões e empréstimos **fora**).
- [ ] Resumo do mês: **Entradas**, **Saídas** e **Saldo das movimentações** do dinheiro próprio. Uma compra no cartão **não** é saída; o pagamento da fatura é. Despesa no VA **não** é saída. Guardar/retirar **não** aparecem.
- [ ] Todos os "?" abrem: o que é, como é calculado, exemplo, o que não é.
- [ ] Aviso de backup aparece se o último tiver 7 dias ou mais.

## 12. Análises (Mais → Análises)
- [ ] Filtros: Este mês, 3, 6 e 12 meses → os números mudam de acordo.
- [ ] "Despesas registradas" × "Saídas do dinheiro próprio" (podem ser diferentes; cada um com "?").
- [ ] Despesas por categoria somam as despesas do período.
- [ ] Cartões e crediários; Empréstimos (a pagar, a receber, juros pagos e recebidos); Descontos obtidos × concedidos.
- [ ] **Objetivos:** guardado agora, guardado e retirado no período (mover não conta) e o progresso de cada um. Entradas e despesas **não** mudam por causa de objetivos.

## 13. Backup e restauração
- [ ] Fazer backup → o arquivo é criado; Mais → Backup mostra a data do último.
- [ ] Mudar alguma coisa e **restaurar** → tudo volta ao estado do backup, **inclusive os objetivos e o histórico deles**; a cópia "antes de restaurar" aparece.
- [ ] Restaurar um backup da v0.6 na v0.7 → funciona (sem objetivos).
- [ ] (Só se tiver um app v0.6) tentar restaurar um backup da v0.7 → **recusado** ("versão mais nova").

## 14. Registro de alterações (Mais → Registro de alterações)
- [ ] Cada operação acima aparece, agrupada (ex.: um pagamento com encargo = um grupo).
- [ ] Guardar, retirar, mover, ajustar e desfazer de objetivos aparecem.

---

## Ao terminar
- Anote tudo em [`registro-de-problemas-v0.8.md`](registro-de-problemas-v0.8.md).
- Só instale a Release (`nk-pay-v0.7.0.apk`) no app principal depois de fazer um backup novo e guardá-lo fora do celular.
