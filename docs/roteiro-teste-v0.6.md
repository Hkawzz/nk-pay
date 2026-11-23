# Roteiro de teste da v0.6 no celular (com uma cópia dos seus dados)

**Objetivo:** testar a v0.6 com os seus dados de verdade **sem tocar no app principal**, no **app de debug** ("NK Pay debug", identificador `com.nkpay.app.debug`, [ADR 0007](decisoes/0007-app-de-debug-separado.md)), que tem dados próprios.

> ⚠️ O backup tem os seus dados financeiros. Ele **não sai do celular** e **nunca** vai para o repositório nem para o computador.

## O que você precisa
Na pasta `Documents\nk-pay-apks` do computador (fora do repositório), estão:
- `nk-pay-v0.5.0-debug.apk`: a v0.5 do app de debug.
- `nk-pay-v0.6.0-debug.apk`: a v0.6 do app de debug.

Os dois foram compilados no mesmo computador, com a mesma chave de debug: um atualiza por cima do outro. Passe os dois para o celular e apague-os de lá ao terminar.

## Parte 1: a migração de verdade, sobre uma cópia
1. No **app principal** (NK Pay v0.5): **Mais → Backup e restauração → Fazer backup agora**.
2. Se ainda houver um "NK Pay debug" antigo no celular, desinstale-o.
3. Instale `nk-pay-v0.5.0-debug.apk`. Abra o **NK Pay debug** e confira em **Mais → Sobre** que a versão termina em **-debug** (`0.5.0-debug`). Se não terminar, você está no app principal: **não restaure nada**.
4. No app de debug: **Mais → Backup e restauração → Restaurar** o backup do passo 1. Confira as contas e os saldos.
5. Instale `nk-pay-v0.6.0-debug.apk` **por cima** do app de debug. É aqui que o banco migra da versão 4 para a 5, **com os seus dados**. **Mais → Sobre** mostra `0.6.0-debug`.
6. Confira:
   - [ ] Saldo disponível, cada conta e os benefícios iguais aos de antes.
   - [ ] Cartões, crediários, faturas, empréstimos e A pagar iguais. As compras antigas continuam **sem juros** (a tela do cartão não mostra "Juros a pagar").

## Parte 2: compra parcelada com juros (use valores pequenos)
No cartão: **+** (Nova compra) → Valor total **R$ 273,00**, 12 parcelas, **Valor da parcela (se tiver juros) R$ 43,58**.
- [ ] O app mostra: 12x de R$ 43,58 = R$ 522,96; juros do parcelamento R$ 249,96.
- [ ] Depois de registrar: **Devido R$ 273,00** e o card **"Juros a pagar R$ 249,96"**, com "?" explicando a diferença para o empréstimo.
- [ ] A despesa da compra é **R$ 273,00** (não R$ 522,96).
- [ ] Abra a parcela: "Parte da compra R$ 22,75 · juros do parcelamento R$ 20,83".
- [ ] Pague a fatura dessa parcela (R$ 43,58): o resumo avisa a despesa de juros; depois, **Devido R$ 250,25**, juros a pagar R$ 229,13, e aparece uma despesa de **R$ 20,83** em Encargos financeiros, na data do pagamento.
- [ ] **Desfazer** o pagamento remove a despesa de juros junto.
- [ ] Num crediário: pague uma parcela com juros com desconto. O desconto sai primeiro dos juros (a despesa de juros fica menor) e aparece em "Descontos obtidos".

### Como corrigir uma compra antiga lançada pelo total com juros
Se você lançou na v0.4/v0.5 uma compra pelo total já com juros (ex.: R$ 522,96 em vez de R$ 273,00), a despesa ficou maior que a compra.
1. **Se nenhuma parcela foi paga:** abra a compra → **Excluir compra** → cadastre de novo com o **valor original** e o **valor da parcela**.
2. **Se já houver parcela paga:** primeiro **desfaça os pagamentos** (fatura ou parcela → Desfazer), depois exclua e cadastre de novo, e por fim **registre de novo os pagamentos** (agora com os juros separados).
3. Se ela foi paga antes de usar o app, cadastre como **compra anterior ao app** (Parte 3), com as parcelas já pagas.

Faça primeiro no app de debug e confira o devido do cartão antes de repetir no principal.

## Parte 3: compra anterior com juros e parcelas antecipadas
Nova compra → ligue **"Compra feita antes de usar o app"** → R$ 273,00, 12 parcelas, parcela R$ 43,58, 4 já pagas, **Parcelas antecipadas: 12**, Valor pago R$ 38,00 (o Desconto mostra R$ 5,58).
- [ ] Devido = R$ 273,00 − 5 × R$ 22,75 = **R$ 159,25**. O Pix não muda.
- [ ] Nenhuma despesa de juros (os juros das parcelas pagas antes do app ficam só no histórico).
- [ ] Nas **Análises**, no mês **da compra**, "Descontos obtidos" mostra R$ 5,58.
- [ ] Informar a parcela 3 como antecipada (ela já está entre as pagas) é recusado.

## Parte 4: excluir e arquivar
- [ ] **Cartões e crediários → Mostrar arquivados** lista os arquivados; abrir um deles permite **Desarquivar**.
- [ ] Cartão **com devido** → Arquivar: o app recusa e diz quanto falta.
- [ ] Cartão **sem nada** → Editar → **Excluir**: some. Cartão **com compras** → Excluir: o app explica e oferece **Arquivar**.
- [ ] **Empréstimos → Pessoas**: mude o nome de uma pessoa (os empréstimos continuam com ela); Arquivar é recusado se ela ainda deve ou te deve; "Mostrar arquivadas".

## Parte 5: empréstimo anterior numerado
Empréstimos → **+** → "Anterior ao app" → **Total de parcelas do contrato 12**, **Parcelas já pagas 4**, 8 parcelas pendentes.
- [ ] As parcelas aparecem como **5/12 … 12/12**. O Pix não muda.
- [ ] Pagas + pendentes diferente do total é recusado.

## Parte 6: Preparar próximo mês
Em **A pagar**, escolha o mês seguinte e toque em **"Preparar [mês]"** (ou no card de sugestão, na virada do mês).
- [ ] **Passo 1:** as contas fixas ainda não lançadas, **todas marcadas**, com o valor do mês anterior; mude um valor e desmarque uma.
- [ ] **Passo 2:** faturas, crediários e empréstimos do mês, **só para conferência**, com "inclui R$ X de juros" quando houver.
- [ ] **Passo 3:** pendências de meses anteriores, **todas desmarcadas**. Marque uma.
- [ ] **Passo 4:** revisão com o total previsto. **Antes de confirmar, nada mudou** em A pagar.
- [ ] Confirmar: as fixas marcadas entram com o valor escolhido; a pendência marcada aparece em "Deste mês" com "ref." do mês original e **Atrasada**; as não marcadas continuam em "Pendentes de meses anteriores".
- [ ] Rodar o assistente de novo para o mesmo mês **não duplica nada**.
- [ ] Registro de alterações: um grupo só para o preparo.

## Parte 7: regressão
- [ ] A pagar: lançar contas fixas e **Pagar** (total, parcial, multa, desconto), desfazer.
- [ ] Cartão: nova compra sem juros, pagar a fatura (valor ou compras escolhidas), desfazer.
- [ ] Crediário: pagar uma parcela com desconto, desfazer.
- [ ] Empréstimos: pagar e receber uma parcela, desfazer.
- [ ] Backup: fazer e restaurar no app de debug.

## Anote o que encontrar
Para cada problema: a tela, o que fez, o que esperava e o que aconteceu. **Não mande prints com valores reais para fora do celular.**

## Quando estiver tudo certo
1. No **app principal**: faça um backup novo e guarde-o **fora** do celular.
2. Instale `nk-pay-v0.6.0.apk` (da Release, assinado com a sua chave) por cima do app principal.
