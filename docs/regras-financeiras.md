# Regras financeiras do NK Pay

Este documento é a **fonte da verdade** sobre como o app calcula o dinheiro. As regras foram aprovadas no planejamento e são definitivas. Cada exemplo numérico vira um teste automático quando a regra é implementada.

Todos os valores dos exemplos são **fictícios**.

Legenda do estado de cada regra:
- ✅ **v0.1**: implementada (ou em implementação) na v0.1.
- 🔜 **futura**: só chega em versões futuras, mas a estrutura de dados da v0.1 **não pode impedi-la**.

---

## 1. Fundamentos ✅ v0.1

### 1.1 Dinheiro em centavos inteiros
Todo valor é guardado como um número inteiro de **centavos** (`Long`), nunca como número com casas decimais (`Double`).

| Valor na tela | Valor guardado |
|---|---|
| R$ 0,05 | `5` |
| R$ 1.234,56 | `123456` |
| −R$ 42,10 | `-4210` |

Por quê: em ponto flutuante, `0,1 + 0,2 = 0,30000000000000004`. Somando centenas de lançamentos, esses erros aparecem como centavos sobrando ou faltando. Com inteiros, a soma é sempre exata. A formatação usa o padrão brasileiro: `R$ 1.234,56`.

### 1.2 Datas
- O lançamento e o vencimento têm **só a data** (`LocalDate`, sem hora). Isso evita o erro clássico de fuso horário que joga um lançamento para o dia anterior.
- Data **e** hora aparecem só na auditoria (o momento exato em que algo foi alterado).

### 1.3 Identificadores e exclusão
- Todo registro tem um **UUID** como identificador (um texto aleatório, como `3f2a…`), e não um número sequencial. Assim, dois aparelhos podem criar registros sem colidir, se um dia houver sincronização.
- Todo registro tem `criadoEm` e `atualizadoEm`.
- Os registros financeiros usam **exclusão lógica**: excluir preenche `excluidoEm`, e o registro some das telas e dos cálculos, mas continua no banco. Isso permite auditoria, restauração e sincronização futura.

### 1.4 Nada que possa ser calculado é gravado
Saldos, valores restantes, status ("paga", "atrasada") e limite disponível **nunca** são guardados. Eles são sempre calculados a partir dos lançamentos, por isso nunca ficam desatualizados.

### 1.5 Transações
Toda operação que altera mais de um registro (por exemplo, uma movimentação + a sua linha de auditoria) roda em **uma única transação do banco**: ou tudo é gravado, ou nada.

---

## 2. Contas e a sua natureza

Cada conta tem uma **natureza**, que define como ela entra nos cálculos:

| Natureza | Exemplos | Entra no "Dinheiro próprio"? | Estado |
|---|---|---|---|
| `PROPRIO` | Pix, dinheiro físico, bancos | **Sim** | ✅ v0.1 |
| `BENEFICIO` | VA, VR (campo `tipoBeneficio`) | **Não, nunca** | ✅ v0.1 |
| `CARTAO` | Cartão de crédito (limite, dia de fechamento, dia de vencimento) | Não. O limite nunca entra em saldo nenhum | ✅ v0.4 (seção 6B) |
| `CREDIARIO` | Carnê ou boleto parcelado (credor sem limite) | Não | ✅ v0.4 (seção 6B) |

### 2.1 Saldo inicial ✅ v0.1
Cada conta tem um **saldo inicial** e uma **data do saldo inicial**. **O saldo inicial não é uma entrada**: ele não aparece nos totais de entradas do mês.

> Exemplo: conta Pix criada com saldo inicial de R$ 1.200,00 em 01/12. As entradas de dezembro continuam em R$ 0,00.

---

## 3. Os 4 tipos de movimentação ✅ v0.1

| Tipo | O que é | Conta como receita/despesa do mês? |
|---|---|---|
| `ENTRADA` | Dinheiro que entra (salário, VA recebido) | Sim: entra nas **entradas** |
| `DESPESA` | Consumo real (mercado, conta de luz) | Sim: entra nas **despesas** |
| `TRANSFERENCIA` | Conta de origem → conta de destino | **Não**: o dinheiro só muda de lugar |
| `AJUSTE` | Corrige o saldo de uma conta | **Não**: é uma correção, não é receita nem despesa |

### 3.1 Saldo de uma conta
```
Saldo = saldo inicial
      + entradas
      − despesas
      + transferências recebidas
      − transferências enviadas
      ± ajustes
```

> **Exemplo.** Pix com saldo inicial de R$ 1.200,00. Depois:
> - Entrada de R$ 500,00 → R$ 1.700,00
> - Despesa de R$ 80,00 → R$ 1.620,00
> - Transferência de R$ 100,00 do Pix para o Dinheiro → Pix com R$ 1.520,00
> - Ajuste de −R$ 20,00 → **Pix com R$ 1.500,00**

### 3.2 Ajuste pelo novo saldo
No ajuste, você pode informar o **novo saldo** em vez da diferença. O app calcula o valor do ajuste:

```
valor do ajuste = novo saldo informado − saldo atual calculado
```

> Exemplo: o app calcula R$ 1.500,00 no Pix, mas o banco mostra R$ 1.487,30. Você informa o novo saldo de R$ 1.487,30, e o app grava um ajuste de **−R$ 12,70**.

### 3.3 Totais do mês
As **entradas do mês** e as **despesas do mês** consideram só `ENTRADA` e `DESPESA`. **Transferências e ajustes não entram nesses totais.**

> Exemplo, em dezembro: entrada de R$ 500,00, despesa de R$ 80,00, transferência de R$ 100,00 e ajuste de −R$ 20,00.
> Entradas do mês: **R$ 500,00**. Despesas do mês: **R$ 80,00**.

---

## 4. Dinheiro próprio, reservado e disponível ✅ v0.1

```
Dinheiro próprio = Σ saldos das contas PROPRIO
Reservado        = Σ reservas dos objetivos        (na v0.1 é sempre R$ 0,00)
Disponível       = Dinheiro próprio − Reservado
```

### 4.1 Benefícios ficam fora do disponível
VA e VR aparecem em um **bloco próprio**, com o total dos benefícios e cada um separado. **Nenhum número soma benefícios com dinheiro próprio.**

> **Exemplo.** Pix R$ 1.200,00 · Dinheiro R$ 300,00 · VA R$ 250,00 · VR R$ 180,00.
> - Dinheiro próprio: **R$ 1.500,00**
> - Reservado: R$ 0,00
> - **Disponível: R$ 1.500,00**
> - Benefícios: **R$ 430,00** (VA R$ 250,00 · VR R$ 180,00)

### 4.2 Efeito de cada operação (continuando o exemplo)

| Operação | Pix | Dinheiro | VA | Disponível | Entradas do mês | Despesas do mês |
|---|---|---|---|---|---|---|
| Situação inicial | 1.200,00 | 300,00 | 250,00 | 1.500,00 | 0,00 | 0,00 |
| Transferência Pix → Dinheiro de R$ 40,00 | 1.160,00 | 340,00 | 250,00 | **1.500,00** (não muda) | 0,00 | 0,00 |
| Despesa de R$ 25,00 paga com o VA | 1.160,00 | 340,00 | 225,00 | **1.500,00** (não muda) | 0,00 | **25,00** |
| Despesa de R$ 60,00 paga com o Pix | 1.100,00 | 340,00 | 225,00 | 1.440,00 | 0,00 | 85,00 |

A despesa paga com o VA **é** consumo real e entra nas despesas do mês, mas não altera o disponível, porque o VA nunca fez parte dele.

---

## 5. Categorias ✅ v0.1

- As categorias são **independentes** da forma de pagamento e do tipo de movimentação. "Pix" não é categoria. "Empréstimo" também não.
- Uma categoria **arquivada** some das novas escolhas e continua aparecendo nos registros antigos.
- A categoria de sistema **"Encargos financeiros"** não pode ser excluída (ela será usada para juros, multas, tarifas e IOF).

---

## 6. Auditoria ✅ v0.1

Toda criação, edição, exclusão e restauração grava uma linha no **Registro de alterações**: o que mudou, o valor antes, o valor depois, a data e a hora. Essa linha é gravada **na mesma transação** da alteração, então não existe alteração sem registro.

---

## 6A. Contas a pagar (contas fixas e avulsas) ✅ v0.3

Aprovadas em 22/11/2026. Ver [`decisoes/0013-regras-de-pagamento.md`](decisoes/0013-regras-de-pagamento.md).

- **Obrigação** é algo a pagar: uma conta fixa de um mês ou uma conta avulsa. Ela guarda o **valor previsto original** e o **mês de referência**, que nunca mudam por causa de pagamentos.
- `restante = previsto + acréscimos − abatimentos − pagamentos aplicados` (calculado, nunca gravado).
- **A despesa é o valor efetivamente pago**, no mês do pagamento. Cada pagamento parcial gera a sua despesa.
- **Encargos** (juros, multa, tarifa, IOF) são despesas próprias, na categoria "Encargos financeiros".
- **Pagou menos:** o app pergunta se foi pagamento *parcial* (o restante continua) ou *desconto* (abatimento e quitação, sem receita fictícia).
- **Contas fixas no mês:** são lançadas por seleção manual, sem duplicar.
- **Atrasadas** continuam em "A pagar" com o mês original.

> **Exemplos** (luz de R$ 100,00, referência dezembro):
> - Paga R$ 100,00 em 10/12 → despesa de R$ 100,00 em dezembro; restante R$ 0,00 (quitada).
> - Paga R$ 60,00 (parcial) → despesa de R$ 60,00; restante R$ 40,00 (parcial). Depois paga R$ 40,00 em 05/01 → despesa de R$ 40,00 em **janeiro**; a obrigação continua sendo de **dezembro**, agora quitada.
> - Paga R$ 105,00 com R$ 5,00 de multa → despesas de R$ 100,00 (categoria da luz) e R$ 5,00 (Encargos financeiros); restante R$ 0,00.
> - Paga R$ 95,00 com desconto de R$ 5,00 → despesa de R$ 95,00 e abatimento de R$ 5,00; restante R$ 0,00. Entradas do mês: inalteradas.

---

## 6B. Cartões e crediários ✅ v0.4

Aprovadas em 23/11/2026 (proposta de execução da v0.4, revisão 2, e briefing autônomo). Ver as decisões [0014](decisoes/0014-banco-v3.md) a [0017](decisoes/0017-integridade-das-contas-de-credito.md).

### 6B.1 A conta de crédito
- Cartão e crediário são **contas** de natureza `CARTAO` ou `CREDIARIO`. Elas começam com **saldo inicial zero**: o que já se deve entra como **compra anterior ao app** (6B.5).
- O saldo da conta de crédito é negativo enquanto houver dívida. `Devido = −saldo da conta de crédito`.
- O cartão tem **dia de fechamento** e **dia de vencimento** (obrigatórios) e um **limite** (opcional). O crediário não tem limite e pode ter um dia de vencimento.
- Contas de crédito **nunca** entram no Dinheiro próprio, no Disponível nem nos Benefícios, e não aparecem na Início.

```
Devido            = compras − pagamentos recebidos + encargos − descontos − parcelas pagas antes do app
Limite usado      = Devido
Limite disponível = Limite − Devido
Invariante        = Devido = Σ restante das faturas (cartão) ou das parcelas (crediário) em aberto
```

### 6B.2 Compra parcelada
- Uma compra cria **uma DESPESA pelo valor total**, na conta de crédito, **na data da compra** e na categoria escolhida. Ela entra nas despesas do mês da compra.
- Cria também **N parcelas** (obrigações de origem `PARCELA`). `valor da parcela = total ÷ N` (arredondado para baixo, em centavos), e **a última parcela absorve a diferença**.
- Uma compra à vista no cartão é uma compra de 1 parcela.
- A despesa da compra fica **bloqueada para edição direta** em Movimentações. Uma compra só pode ser excluída enquanto nenhuma parcela tiver pagamento (e a fatura dela não tiver abatimento geral). Depois disso, é preciso desfazer os pagamentos antes.

> **Exemplo.** Compra de R$ 100,00 em 3x → parcelas de R$ 33,33, R$ 33,33 e **R$ 33,34**. As despesas do mês da compra sobem R$ 100,00. O devido do cartão sobe R$ 100,00.

### 6B.3 Datas das parcelas
**Cartão** (fechamento F, vencimento V):
1. A fatura fecha no dia F do mês da compra. Se a compra for **no dia do fechamento ou depois**, ela entra na fatura que fecha no mês seguinte.
2. A fatura vence no dia V: no **mesmo mês do fechamento** se V > F, ou no **mês seguinte** se V ≤ F.
3. A parcela *k* vence *k − 1* meses depois da primeira.
4. Um dia que não existe no mês (ex.: 31 em fevereiro) vira o **último dia do mês**.

| Cartão | Compra | 1ª parcela vence |
|---|---|---|
| F = 5, V = 12 | 03/12/2026 | 12/12/2026 |
| F = 5, V = 12 | **05/12/2026** (dia do fechamento) | 12/01/2027 |
| F = 28, V = 5 | 10/12/2026 | 05/01/2027 (virada de ano) |
| F = 31, V = 10 | 28/02/2027 (fecha em 28/02) | 10/04/2027 |

**Crediário:** a 1ª parcela vence no mês seguinte ao da compra, no dia de vencimento do crediário (ou no mesmo dia da compra, se o crediário não tiver dia). Em todos os casos, **a data da 1ª parcela pode ser editada** no cadastro da compra, e as demais seguem mês a mês.

O **mês de referência** de cada parcela é o mês do vencimento dela. Ele nunca muda.

### 6B.4 Fatura (cartão)
A fatura **não é gravada**: ela é o conjunto das parcelas do cartão com o mesmo mês de referência.

```
Restante da fatura = Σ restante das parcelas da fatura − Σ abatimentos gerais da fatura
Parcela quitada    = restante dela ≤ 0  OU  restante da fatura ≤ 0
```

Exemplo: fatura de dezembro com as parcelas A R$ 100,00, B R$ 200,00 e C R$ 180,00 (total R$ 480,00).

| Ação | O que é gravado | Resultado |
|---|---|---|
| **Pagar total** | 1 transferência Pix → Cartão de R$ 480,00, ligada a A, B e C | Fatura quitada. Despesas do mês: sem mudança |
| **Pagar parcial** de R$ 300,00 | 1 transferência de R$ 300,00 + 1 **abatimento geral** de R$ 300,00 na fatura | Restante R$ 180,00. Nenhuma compra é dada como quitada |
| Depois, pagar R$ 180,00 | Transferência + abatimento geral de R$ 180,00 | Fatura quitada: todas as parcelas passam a "pagas" |
| **Pagar compras específicas** (só B) | Transferência de R$ 200,00 ligada a B | B quitada. Restante da fatura R$ 280,00 |
| Desfazer qualquer pagamento | Exclusão lógica da transferência e das ligações | Volta ao estado anterior |

Regras:
- **Pagar uma fatura é uma transferência**, nunca uma despesa: a despesa já foi contada no mês da compra.
- **Fatura vencida com restante** fica **atrasada no mês original**. Ela não passa para a fatura seguinte e não recebe juros automáticos. Pagamentos posteriores continuam abatendo a fatura original.
- **Um pagamento pode cobrir várias faturas em aberto**: o valor é distribuído da **mais antiga para a mais nova**. Cada fatura coberta inteira é quitada; a última, se coberta em parte, recebe um abatimento geral. A transferência é uma só.
- **Pagar compras específicas** quita exatamente o restante das compras escolhidas e não pode passar do restante da fatura.
- **Pagar mais que o restante** das faturas em aberto: o app **pergunta a natureza** da diferença (juros, multa, tarifa, IOF, outro acréscimo). O excedente vira uma **despesa de encargo na conta do cartão**, na categoria "Encargos financeiros", com um acréscimo na parcela. Nada é automático e não existe "lançar encargo" avulso.
- **Desconto em fatura** não existe na v0.4: pagar menos que o restante é sempre um pagamento parcial (abatimento geral).

> **Exemplo de encargo.** Fatura de R$ 480,00 paga com R$ 492,00, classificando R$ 12,00 como juros: transferência de R$ 492,00 Pix → Cartão; despesa de R$ 12,00 em Encargos financeiros, na conta do cartão; acréscimo de R$ 12,00 numa parcela da fatura. Devido: R$ 0,00. Despesas do mês: +R$ 12,00 (só o encargo).

### 6B.5 Crediário (carnê, boleto)
Não tem fatura. Cada parcela é paga **individualmente**, como uma conta a pagar, mas por **transferência** para a conta do crediário (sem despesa). Valem as mesmas perguntas da seção 6A:
- **Pagou menos:** *parcial* (o restante continua) ou *desconto*. No crédito, o desconto é um **AJUSTE positivo na conta do crediário** (reduz o devido) e um abatimento na parcela. Não é receita.
- **Pagou mais:** o excedente é uma despesa de encargo na conta do crediário, com acréscimo na parcela (como no cartão).

> **Exemplo.** Parcela de R$ 150,00. Paga R$ 140,00 com desconto de R$ 10,00 → transferência de R$ 140,00 Pix → Crediário; ajuste de +R$ 10,00 no crediário; parcela quitada; devido −R$ 150,00. Entradas e despesas do mês: inalteradas.

### 6B.6 Compra feita antes de usar o app
Para registrar algo que você já devia quando começou a usar o app (ex.: compra de R$ 1.200,00 em 12x, de 10/08, com as parcelas 1 a 4 já pagas):

| Registro | Valor | Efeito |
|---|---|---|
| Compra marcada como **anterior ao app** | R$ 1.200,00 | Identifica o registro histórico |
| DESPESA na conta do crédito, em **10/08** (o mês original), na categoria da compra | R$ 1.200,00 | Entra nas despesas de **agosto**, mesmo que agosto seja o mês atual |
| 12 parcelas | 12 × R$ 100,00 | Histórico completo |
| Parcelas 1 a 4: abatimento **"quitada antes do app"** | 4 × R$ 100,00 | Aparecem como pagas antes do app. Não são pagamentos nem descontos |
| AJUSTE de +R$ 400,00 na conta do crédito ("Parcelas pagas antes do app") | +R$ 400,00 | O devido cai para R$ 800,00 |

- **Pix, dinheiro e benefícios não mudam.** Disponível: igual.
- Se uma parcela foi paga **só em parte** antes do app, informe o valor já pago dela: ele vira um abatimento "quitada antes do app" pelo valor parcial.
- "Quitada antes do app" **nunca** entra em "descontos obtidos".
- **Possível duplicidade:** se você já lançou essa compra no app (como conta avulsa ou despesa), o app **avisa** quando encontrar nome ou valor parecidos e explica o roteiro: desfazer o lançamento antigo e pagar a parcela pelo fluxo do cartão. **Ele nunca converte nada sozinho.**

### 6B.7 Integridade
Contas de crédito **não podem** ser usadas em entradas, despesas, ajustes ou transferências **avulsas** (lançadas em Movimentações). Só mudam por compras, pagamentos e pelos registros de 6B.6. Essa regra fica na camada de dados, e não só na tela. Ver [0017](decisoes/0017-integridade-das-contas-de-credito.md).

---

## 6C. Categorias com ícone e cor ✅ v0.4

- Cada categoria tem um **ícone** e uma **cor**. Aparência **nunca** toca em registros financeiros.
- As categorias existentes recebem a **aparência sugerida pelo nome** (sem maiúsculas e sem acentos), com auditoria. Só categorias **sem ícone** recebem sugestão, então uma escolha sua nunca é sobrescrita. Categorias com nome desconhecido recebem um ícone e uma cor neutros.
- A cor personalizada é **clareada automaticamente** até ter contraste mínimo de 3:1 com o fundo escuro.
- Nos gráficos, a categoria sempre aparece com o **nome** junto (duas categorias podem ter a mesma cor).

Ver [0018](decisoes/0018-categorias-com-aparencia.md).

---

## 6D. Análises ✅ v0.4

As análises ficam em **Mais → Análises** e são calculadas a partir dos mesmos dados das outras telas (nada é gravado).

| Análise | Definição |
|---|---|
| **Entradas, despesas e resultado** | A regra da seção 3.3: entradas − despesas do período. Compras no cartão contam no mês da compra. Transferências, ajustes e pagamentos de fatura ou crediário **não** entram |
| **Fluxo financeiro** | O dinheiro que **entra e sai das contas de dinheiro próprio** no período: entradas, despesas pagas com dinheiro próprio e pagamentos de fatura ou crediário. Transferências entre duas contas próprias **não** entram. Benefícios ficam fora |
| **Despesas por categoria** | Soma das despesas de cada categoria no período, do maior para o menor, com ícone, cor, nome e percentual |
| **Contas e carteiras** | O saldo de cada conta de dinheiro próprio e de benefício no fim de cada mês do período |
| **Cartões e crediários** | Devido, limite usado e disponível, faturas pagas, em aberto e atrasadas, e o total de compras anteriores ao app |
| **Evolução mensal** | Entradas, despesas e resultado de cada mês do período. Meses antes do primeiro registro aparecem como "sem dados" |
| **Descontos obtidos** | Soma dos descontos e abatimentos dados em pagamentos. **Exclui** "quitada antes do app" |

> **Exemplo** (dezembro): entrada de R$ 3.000,00 no Pix; mercado de R$ 200,00 no Pix; compra de R$ 600,00 no cartão; pagamento de fatura de R$ 300,00 Pix → Cartão; transferência de R$ 100,00 Pix → Dinheiro.
> - Entradas R$ 3.000,00 · Despesas **R$ 800,00** · Resultado **R$ 2.200,00**.
> - Fluxo: entrou R$ 3.000,00, saiu **R$ 500,00** (R$ 200,00 do mercado + R$ 300,00 da fatura). A transferência Pix → Dinheiro não conta.

**Período:** mês atual (padrão), últimos 3, 6 ou 12 meses. O último filtro escolhido fica guardado **na memória enquanto o app estiver aberto** (nada é gravado).

---

## 7. Regras futuras 🔜

> As seções 7.1 a 7.4 foram detalhadas e implementadas na v0.4: ver a seção 6B.

Estas regras não são implementadas na v0.1, mas o banco de dados da v0.1 precisa permiti-las.

### 7.1 Compras parceladas (cartão, carnê, boleto)
- O **valor total** é uma despesa **na data da compra**.
- As parcelas são **obrigações individuais**, cada uma com número, valor, vencimento e status próprios.
- Pagar uma parcela é uma **transferência** para a conta de crédito e **não** gera despesa sobre o valor da compra.
- É permitido pagar fora de ordem.

> Exemplo: uma compra de R$ 600,00 em 6x no cartão, em 10/12, aumenta as despesas de dezembro em R$ 600,00 e cria 6 obrigações de R$ 100,00. Pagar a parcela 6 antes das outras quita só a 6.

### 7.2 Cartão
```
Devido            = compras + encargos − pagamentos − abatimentos
Limite disponível = limite − devido
```
Uma compra parcelada compromete o limite pelo **valor total**.

> Exemplo: limite de R$ 2.000,00 e a compra de R$ 600,00 acima → devido R$ 600,00, limite disponível R$ 1.400,00. Depois de pagar uma parcela de R$ 100,00 com o Pix: devido R$ 500,00 e limite disponível R$ 1.500,00. As despesas do mês não mudam com o pagamento.

### 7.3 Pagamentos parciais
Uma obrigação pode receber vários pagamentos.
```
Restante = previsto + acréscimos − abatimentos − pagamentos aplicados
```
A obrigação continua pendente até o restante chegar a zero.

> Exemplo: parcela de R$ 100,00. Um pagamento de R$ 60,00 deixa o restante em R$ 40,00 (pendente). Um segundo pagamento de R$ 40,00 deixa o restante em R$ 0,00 (quitada).

### 7.4 Diferença entre o valor pago e o restante
Quando o valor pago é diferente do restante, o app **pergunta** a natureza da diferença: pagamento parcial, juros, multa, tarifa, IOF, desconto ou outro. **Ele nunca classifica sozinho.**

### 7.5 Objetivos
São **reservas virtuais** sobre o dinheiro próprio: o dinheiro continua na conta, e o objetivo só "carimba" uma parte. Uma nova reserva fica bloqueada se não houver disponível suficiente.

> Exemplo: com o dinheiro próprio em R$ 1.500,00, uma reserva de R$ 400,00 deixa o disponível em R$ 1.100,00. Uma nova reserva de R$ 1.200,00 é bloqueada.

### 7.6 Virada do mês
- O assistente "Preparar próximo mês" só age com a sua confirmação.
- As pendências não levadas para o mês continuam visíveis.
- O vencimento pode ficar vazio.

### 7.7 Empréstimos entre pessoas
Não são receita nem despesa. O modelo ainda vai ser definido.

### 7.8 Backup e restauração ✅ v0.2
Completos e offline. A restauração substitui tudo (nunca mistura), guarda antes uma cópia do estado atual e fica registrada no Registro de alterações. Ver [`decisoes/0010-backup.md`](decisoes/0010-backup.md).
