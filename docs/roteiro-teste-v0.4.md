# Roteiro de teste da v0.4 no celular (com uma cópia dos seus dados)

**Objetivo:** testar a v0.4 com os seus dados de verdade **sem tocar no app principal**. Você usa o **app de debug**, um app separado ("NK Pay debug", identificador `com.nkpay.app.debug`, [ADR 0007](decisoes/0007-app-de-debug-separado.md)), com dados próprios.

> ⚠️ O backup tem os seus dados financeiros. Ele **não sai do celular** e **nunca** vai para o repositório nem para o computador.

## O que você precisa
Na pasta `Documents\nk-pay-apks` do computador (fora do repositório), estão os dois arquivos:
- `nk-pay-v0.3.0-debug.apk`: a v0.3 do app de debug.
- `nk-pay-v0.4.0-debug.apk`: a v0.4 do app de debug.

Os dois foram compilados no mesmo computador, com a mesma chave de debug. Por isso, um atualiza por cima do outro. Passe os dois para o celular (cabo USB ou Drive) e apague-os do celular quando terminar.

## Parte 1: a migração de verdade, sobre uma cópia
1. No **app principal** (NK Pay v0.3): **Mais → Backup e restauração → Fazer backup agora**. Salve o arquivo no celular.
2. Instale `nk-pay-v0.3.0-debug.apk`. Aparece um **segundo** ícone, que nesta versão também se chama "NK Pay" (a partir da v0.4 ele passa a se chamar "NK Pay debug").
   - ⚠️ **Antes de restaurar qualquer coisa**, abra o app novo e confira em **Mais → Sobre**: a versão precisa terminar em **-debug** (`0.3.0-debug`). Se não terminar, você está no app principal: **não restaure nada** e volte um passo.
3. No **app de debug** (versão -debug): **Mais → Backup e restauração → Restaurar** e escolha o backup do passo 1. Confira se as contas e os saldos estão iguais aos do app principal.
4. Instale `nk-pay-v0.4.0-debug.apk` **por cima** do app de debug. É aqui que o banco migra da versão 2 para a 3, **com os seus dados**. O ícone passa a se chamar "NK Pay debug", e **Mais → Sobre** mostra `0.4.0-debug`.
5. Abra o app de debug e confira:
   - [ ] Disponível, Dinheiro próprio e o saldo de cada conta iguais aos de antes.
   - [ ] Benefícios iguais.
   - [ ] Movimentações e contas a pagar iguais (A pagar, inclusive as pagas).
   - [ ] Em **Mais → Categorias**, cada categoria ganhou um ícone e uma cor. As de nome conhecido recebem a sugestão; as outras, um ícone neutro.
   - [ ] Em **Mais → Registro de alterações**, aparecem as linhas "Aparência sugerida aplicada à categoria…". Nada financeiro mudou.

## Parte 2: a nova Início
- [ ] Disponível centralizado, com Dinheiro próprio e Reservado embaixo.
- [ ] Minhas contas em 2 colunas (até 4) e Benefícios em 3 colunas (até 3). Se houver mais, aparece "Ver todas" / "Recolher".
- [ ] Valores grandes não são cortados. Tocar num card abre a conta.
- [ ] Resumo do mês: troque o mês pelas setas. Entradas e despesas mostram a quantidade, e o resultado é entradas − despesas.
- [ ] Últimas movimentações agrupadas por dia, com o ícone da categoria.

## Parte 3: cartão (use valores pequenos, é só um teste)
1. **Mais → Cartões e crediários → +** → Cartão de crédito, com limite, dia de fechamento e dia de vencimento iguais aos do seu cartão.
2. **Nova compra** de 3x: confira as parcelas (a última absorve os centavos) e os meses das faturas.
   - [ ] A despesa total aparece no mês da compra (Movimentações e Resumo do mês).
   - [ ] O devido sobe e o limite disponível cai. **O Disponível não muda.**
3. Abra a fatura do mês (pela tela do cartão ou por **A pagar → Cartões e crediários**):
   - [ ] **Um valor** menor que o total → "abatidos sem escolher compras"; nenhuma compra fica paga.
   - [ ] **Desfazer** esse pagamento → tudo volta.
   - [ ] **Escolher compras** → só a compra marcada fica paga.
   - [ ] Pagar **mais** que o total → o app pergunta se é juros, multa… e cria uma despesa de encargo no cartão.
   - [ ] Pagar a fatura **não** cria despesa nova: o Pix cai, e o devido cai junto.
4. Em Movimentações, tente editar a despesa da compra: o app explica que ela muda pela tela do cartão.

## Parte 4: compra anterior ao app
Se você já tinha um parcelamento antes de usar o app:
1. **Nova compra** → ligue "Compra feita antes de usar o app" e informe quantas parcelas já foram pagas.
   - [ ] Se você já lançou essa compra no app (conta avulsa ou despesa), aparece o aviso **"Isto pode já estar no app"**, com o roteiro. O app não converte nada sozinho.
   - [ ] O devido é só o que falta. O Pix e o Disponível **não mudam**.
   - [ ] A despesa total aparece no mês original da compra.

## Parte 5: crediário
1. **Mais → Cartões e crediários → +** → Crediário.
2. Registre uma compra em 2x e abra a parcela 1 (Compra → Parcela 1/2):
   - [ ] Pague com **desconto**: a parcela fica paga, e as entradas do mês não mudam.
   - [ ] **Desfazer** volta tudo.

## Parte 6: regressão da v0.3 (contas fixas)
- [ ] A pagar → lançar contas fixas do mês (seleção manual, sem duplicar).
- [ ] Pagar uma conta: total, parcial, com multa e com desconto. **Desfazer.**
- [ ] Backup: **Fazer backup agora** no app de debug e restaurar o mesmo arquivo.

## Parte 7: Análises
- [ ] **Mais → Análises**: troque o período (este mês, 3, 6 e 12 meses). Saia para outra aba e volte: o filtro continua.
- [ ] Os números batem com o Resumo do mês da Início.

## Anote o que encontrar
Para cada problema: a tela, o que você fez, o que esperava e o que aconteceu. **Não mande prints com valores reais para fora do celular.** As correções voltam como PRs, e a v0.4 só vai para o app principal depois disso.

## Quando estiver tudo certo
1. No **app principal**: faça um backup novo e guarde o arquivo **fora** do celular.
2. Instale `nk-pay-v0.4.0.apk` (da Release, assinado com a sua chave) por cima do app principal.
3. Desinstale o app de debug, se quiser (os dados dele são só uma cópia).
