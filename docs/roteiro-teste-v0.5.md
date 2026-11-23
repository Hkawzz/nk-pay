# Roteiro de teste da v0.5 no celular (com uma cópia dos seus dados)

**Objetivo:** testar a v0.5 com os seus dados de verdade **sem tocar no app principal**, no **app de debug** ("NK Pay debug", identificador `com.nkpay.app.debug`, [ADR 0007](decisoes/0007-app-de-debug-separado.md)), que tem dados próprios.

> ⚠️ O backup tem os seus dados financeiros. Ele **não sai do celular** e **nunca** vai para o repositório nem para o computador.

## O que você precisa
Na pasta local de APKs do computador (fora do repositório), estão:
- `nk-pay-v0.4.0-debug.apk`: a v0.4 do app de debug.
- `nk-pay-v0.5.0-debug.apk`: a v0.5 do app de debug.

Os dois foram compilados no mesmo computador, com a mesma chave de debug: um atualiza por cima do outro. Passe os dois para o celular e apague-os de lá ao terminar.

## Parte 1: a migração de verdade, sobre uma cópia
1. No **app principal** (NK Pay v0.4): **Mais → Backup e restauração → Fazer backup agora**.
2. Se ainda houver um "NK Pay debug" antigo no celular, desinstale-o.
3. Instale `nk-pay-v0.4.0-debug.apk`. Abra o **NK Pay debug** e confira em **Mais → Sobre** que a versão termina em **-debug** (`0.4.0-debug`). Se não terminar, você está no app principal: **não restaure nada**.
4. No app de debug: **Mais → Backup e restauração → Restaurar** o backup do passo 1. Confira as contas e os saldos.
5. Instale `nk-pay-v0.5.0-debug.apk` **por cima** do app de debug. É aqui que o banco migra da versão 3 para a 4, **com os seus dados**. **Mais → Sobre** mostra `0.5.0-debug`.
6. Confira:
   - [ ] Saldo disponível igual ao "Disponível" de antes; cada conta e os benefícios iguais.
   - [ ] Cartões, crediários, faturas e A pagar iguais.
   - [ ] Em **Mais → Categorias**, existe a categoria de sistema **"Juros recebidos"**.

## Parte 2: Início e ajuda "?"
- [ ] O card mostra **"Saldo disponível"** (Saldo total e Guardado só aparecem quando houver objetivos, na v0.7).
- [ ] O **Resumo do mês** mostra **Entradas, Saídas e Saldo das movimentações**. Uma compra no cartão **não** é saída; o pagamento da fatura é. Uma despesa no VA/VR não é saída.
- [ ] Toque nos **"?"**: cada um explica o que é, como é calculado, um exemplo e o que não é.

## Parte 3: Análises
- [ ] "Despesas registradas" (consumo) × "Saídas do dinheiro próprio" (fluxo), cada um com "?". Os números podem ser diferentes, e isso é de propósito.
- [ ] Evolução mensal com o mês atual destacado.
- [ ] Seção **Empréstimos** (depois da Parte 4).

## Parte 4: empréstimos (use valores pequenos, é só um teste)
**Mais → Empréstimos**.

**Peguei emprestado (eu devo):**
1. **+** → "Peguei (eu devo)" → pessoa nova, valor original R$ 100,00, entrou no Pix, 2 parcelas de R$ 55,00.
   - [ ] O app mostra na hora: total R$ 110,00 e juros previstos R$ 10,00.
   - [ ] Pix +R$ 100,00. Os juros de R$ 10,00 aparecem **uma vez**, como despesa em Encargos financeiros, na data.
   - [ ] As parcelas aparecem em **A pagar → Empréstimos a pagar**.
2. Abra a parcela 1 e **pague** R$ 55,00: Pix −R$ 55,00, **nenhuma despesa nova**. **Desfazer** volta tudo.

**Emprestei (me devem):**
1. **+** → "Emprestei (me devem)" → R$ 100,00 saindo do Pix, 2 parcelas de R$ 55,00.
   - [ ] Pix −R$ 100,00, sem despesa. As parcelas **não** aparecem em A pagar.
2. Abra a parcela 1 e **receba** R$ 55,00:
   - [ ] O resumo mostra "Devolução de R$ 50,00" + "Juros recebidos de R$ 5,00".
   - [ ] Pix +R$ 55,00; entrada de R$ 5,00 em "Juros recebidos".
   - [ ] **Desfazer** remove a devolução e os juros juntos.
3. Receba a menos e escolha **Desconto**: a parcela fica paga, **sem despesa**, e aparece em "Descontos concedidos" nas Análises.

**Atraso e edição:**
- [ ] Uma parcela vencida mostra "Atrasada · vencida há N dias".
- [ ] Numa parcela **sem pagamento**: "Alterar esta parcela" (valor e/ou data, com "aplicar às seguintes").

**Excluir:** um empréstimo sem nenhum pagamento pode ser excluído (o Pix volta ao que era).

## Parte 5: empréstimo anterior ao app
1. **+** → ligue **"Anterior ao app"** → cadastre só as parcelas que ainda faltam.
   - [ ] **O Pix não muda**, e não aparece receita nem despesa.
   - [ ] No "Emprestei", o campo **"juros contidos nas parcelas pendentes"** é opcional. Com R$ 0,00, tudo o que receber conta como devolução.
   - [ ] Se já existir algo parecido no app, aparece o aviso **"Isto pode já estar no app"**.

### Se você usou um cartão ou crediário "de mentira" para um empréstimo
O app **não converte nada sozinho**. Faça assim, no app de debug primeiro:
1. **Desfaça os pagamentos** dessa conta: na tela do cartão ou crediário, em cada fatura ou parcela paga, toque em **Desfazer**.
2. **Exclua a compra fictícia**: Compras → a compra → **Excluir compra**.
3. **Arquive a conta**: na tela do cartão ou crediário, toque em **Arquivar** (no alto). Ela some de Mais → Cartões e crediários; o histórico continua no Registro de alterações.
4. **Cadastre o empréstimo como anterior ao app** (Parte 5), com as parcelas que ainda faltam.
5. Confira que o Saldo disponível e o Pix ficaram iguais aos de antes, exceto pelos pagamentos que você desfez (eles voltam a aparecer como dinheiro na conta: se você realmente pagou, registre de novo pela tela da parcela do empréstimo).

## Parte 6: regressão da v0.4 e da v0.3
- [ ] Cartão: nova compra, pagar a fatura (um valor ou compras escolhidas), desfazer.
- [ ] Crediário: pagar uma parcela com desconto, desfazer.
- [ ] A pagar: lançar contas fixas e pagar (total, parcial, multa, desconto), desfazer.
- [ ] Backup: fazer e restaurar no app de debug.

## Anote o que encontrar
Para cada problema: a tela, o que fez, o que esperava e o que aconteceu. **Não mande prints com valores reais para fora do celular.**

## Quando estiver tudo certo
1. No **app principal**: faça um backup novo e guarde-o **fora** do celular.
2. Instale `nk-pay-v0.5.0.apk` (da Release, assinado com a sua chave) por cima do app principal.
