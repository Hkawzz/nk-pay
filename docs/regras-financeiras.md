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
Reservado        = Σ reservas dos objetivos        (Guardado; R$ 0,00 até a v0.6, ver 6F na v0.7)
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

### 6B.8 Compra parcelada com juros ✅ v0.6 (D-A1 c)
Aprovada no briefing da v0.6. Ver [0025](decisoes/0025-juros-do-parcelamento.md).

**Cadastro:** valor original + valor da parcela + quantidade. Total = parcela × quantidade; juros do parcelamento = total − original. Total menor que o original é recusado. Sem juros (parcela = original ÷ N), tudo é igual à v0.5.

```
Despesa da compra          = valor ORIGINAL, na categoria e na data da compra
Parte da compra da parcela = original ÷ N (a última absorve os centavos)   (calculada)
Juros previstos da parcela = valor da parcela − parte da compra             (calculado)
Devido = limite usado      = só a parte da compra ainda não paga
Juros a pagar              = Σ juros previstos ainda não pagos
Invariante                 = Devido = Σ restante das faturas em aberto − Juros a pagar
```

**Ao pagar:** a parte da compra abate o devido, e **os juros previstos viram despesa em "Encargos financeiros", na data do pagamento**, na conta de crédito. Pagamento parcial ou abatimento geral da fatura: o valor é dividido entre a parte da compra e os juros **na proporção do restante**. O pagamento que quita fecha os centavos. Valor diferente do esperado: o app pergunta (multa, parcial, desconto). O desconto reduz **primeiro os juros**. Desfazer um pagamento remove a despesa de juros junto.

> **Diferente dos empréstimos (J1):** num empréstimo pego, os juros entram como despesa **na contratação**. Na compra parcelada, eles entram **parcela a parcela, quando cada uma é paga**.

> **Exemplo.** Compra de R$ 273,00 em 12 × R$ 43,58 (total R$ 522,96; juros R$ 249,96).
> - Compra: despesa de **R$ 273,00** na categoria da compra. Devido R$ 273,00; juros a pagar R$ 249,96.
> - Paga a 1ª parcela (R$ 43,58): transferência Pix → Cartão de R$ 43,58 + despesa de **R$ 20,83** em Encargos financeiros (43,58 − 22,75). Devido R$ 250,25 (273,00 − 22,75); juros a pagar R$ 229,13.

### 6B.9 Compra anterior ao app com juros e parcelas antecipadas ✅ v0.6
- A despesa é o **valor original**, no mês original. As parcelas pagas antes do app (as primeiras k, a parcial e as **antecipadas**) têm o abatimento "quitada antes do app", e o ajuste na conta de crédito soma **só a parte da compra** delas.
- **Os juros das parcelas pagas antes do app não viram despesa** (D-A3 b): ficam só no histórico da parcela.
- **Parcelas antecipadas:** você informa quais e, para cada uma, o **valor pago** ou o **desconto** (o app calcula o outro). Uma parcela não pode estar nas pagas e nas antecipadas ao mesmo tempo, e o valor pago não passa do previsto. O desconto reduz **primeiro os juros previstos** da parcela.
- **O desconto de antes do app aparece em "Descontos obtidos", datado na data da compra** (D-A2 b).

> **Exemplo.** Parcela 12 de R$ 43,58 (parte R$ 22,75, juros R$ 20,83) antecipada por R$ 38,00: "quitada antes do app" R$ 38,00 + "desconto antes do app" R$ 5,58 (que sai dos juros). O ajuste no cartão soma R$ 22,75 (a parte da compra). Descontos obtidos no mês da compra: +R$ 5,58.

### 6B.10 Excluir e arquivar cartões, crediários e pessoas ✅ v0.6
- **Excluir** só sem nenhuma compra, pagamento, abatimento, empréstimo ou movimentação. Se houver, o app explica e oferece **Arquivar**.
- **Arquivar** é recusado enquanto houver devido, valor a pagar ou a receber (sumiria das telas com algo em aberto). "Mostrar arquivados" e "Desarquivar" trazem de volta.
- O nome de uma pessoa ou instituição pode ser editado, com auditoria (os registros apontam para a conta, não para o nome).

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
| **Fluxo financeiro** | O dinheiro que **entra e sai das contas de dinheiro próprio** no período: entradas, despesas pagas com dinheiro próprio e pagamentos de fatura ou crediário. Transferências entre duas contas próprias **não** entram. Benefícios ficam fora. **v0.5:** generalizado na regra 6E.2 (é o mesmo cálculo do Resumo da Início) |
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

## 6E. Empréstimos, fluxo do dinheiro próprio e a Início ✅ v0.5

Aprovadas em 23/11/2026 (`planejamento/especificacao-v0.5.0.md` e briefing da v0.5, decisões D1 a D5). Ver as decisões [0020](decisoes/0020-banco-v4.md) a [0023](decisoes/0023-inicio-saldo-e-ajuda.md).

### 6E.1 Contas de terceiros
| Natureza | O que é | Entra no Saldo total? | Aparece nas listas avulsas? |
|---|---|---|---|
| `EMPRESTIMO_RECEBIDO` | Pessoa ou instituição de quem peguei dinheiro (eu devo) | Não | Não |
| `EMPRESTIMO_CONCEDIDO` | Pessoa a quem emprestei (me deve) | Não | Não |

**Conta de terceiros** é o nome comum de `CARTAO`, `CREDIARIO` e das duas naturezas novas. Todas seguem a integridade da regra 6B.7: ficam fora das listas "Conta", "De/Para" e "Pagar com", são bloqueadas em Movimentações, são recusadas na validação e começam com saldo inicial zero.

**Pessoa = conta:** cada pessoa ou instituição é uma conta. Vários empréstimos com a mesma pessoa somam na mesma conta ("quanto o Tonho me deve"). Quem me deve e a quem eu devo são contas separadas.

### 6E.2 Fluxo do dinheiro próprio (D1)
```
Entradas do fluxo = entradas em contas PROPRIO
                  + transferências de qualquer conta NÃO própria → conta PROPRIO
Saídas do fluxo   = despesas pagas com conta PROPRIO
                  + transferências de conta PROPRIO → qualquer conta NÃO própria
Transferência PROPRIO → PROPRIO: não entra. Ajustes: não entram.
```
É o mesmo cálculo no **Resumo do mês da Início** (Entradas, Saídas e Saldo das movimentações) e nas **"Saídas do dinheiro próprio"** das Análises. A compra no cartão **nunca** é saída: a saída acontece quando a fatura é paga. Despesas pagas com VA/VR ficam fora do fluxo (continuam em "Despesas registradas").

| Evento | Saldo total | Fluxo (Início) | Receitas (Análises) | Despesas (Análises) |
|---|---|---|---|---|
| Pegar emprestado R$ 1.000,00 (10 × R$ 110,00) | +1.000,00 | Entrada 1.000,00 | — | Juros 100,00, **uma vez** |
| Pagar parcela de R$ 110,00 | −110,00 | Saída 110,00 | — | — |
| Emprestar R$ 1.000,00 (10 × R$ 110,00) | −1.000,00 | Saída 1.000,00 | — | — |
| Receber parcela de R$ 110,00 | +110,00 | Entrada 110,00 | Juros 10,00 | — |
| Compra de R$ 600,00 no cartão | — | — | — | 600,00 |
| Pagar fatura de R$ 300,00 | −300,00 | Saída 300,00 | — | — |
| Transferência Pix → Dinheiro | — | — | — | — |
| Empréstimo anterior ao app | — | — | — | — |

### 6E.3 A Início
| Indicador | Fórmula | Quando aparece |
|---|---|---|
| **Saldo disponível** | Saldo total − Guardado | Sempre, em destaque |
| **Saldo total** | Σ contas `PROPRIO` (sem VA/VR, cartões e empréstimos) | Só se Guardado > 0 |
| **Guardado** | Σ reservas dos objetivos | Só se Guardado > 0 |

Até a v0.6, o Guardado era sempre R$ 0,00, então só o Saldo disponível aparecia. Na v0.7 ele vem dos objetivos (6F), e Saldo total e Guardado aparecem enquanto houver algo guardado. É a mesma fórmula do antigo "Disponível", com nome novo.

### 6E.4 Empréstimo recebido (eu devo): juros J1 (D2)
Os **juros** (total das parcelas − valor original) viram **uma despesa** em "Encargos financeiros", na conta do empréstimo, **na data da contratação**. Pagar uma parcela é **só transferência**.

> **Exemplo.** Recebo R$ 1.000,00 no Pix em 10/12 e assumo 10 × R$ 110,00.
> - Contratação: transferência Empréstimo → Pix de R$ 1.000,00 + despesa de R$ 100,00 (Encargos financeiros), em 10/12. Pix +1.000,00; dívida **R$ 1.100,00**; despesas de dezembro +100,00 (uma vez).
> - 1ª parcela paga: transferência Pix → Empréstimo de R$ 110,00. Pix −110,00; dívida R$ 990,00; despesas inalteradas.

Invariante: **dívida = Σ restante das parcelas** (dívida = −saldo da conta do empréstimo).

### 6E.5 Empréstimo concedido (me devem): juros K2 (D3)
Emprestar é **só transferência**. Os juros viram **receita** ("Juros recebidos", categoria de sistema) **só quando recebidos**, na proporção:
```
juros não reconhecidos  = Σ restante das parcelas − principal a receber
proporção               = juros não reconhecidos ÷ Σ restante das parcelas
juros deste recebimento = valor recebido × proporção   (arredondado em centavos)
```
O **recebimento que quita o empréstimo** reconhece exatamente os juros que faltam (os centavos fecham).

> **Exemplo.** Empresto R$ 1.000,00 do Pix em 10/12, a receber 10 × R$ 110,00.
> - Contratação: transferência Pix → Empréstimo de R$ 1.000,00. Pix −1.000,00; a receber (principal) R$ 1.000,00; nenhuma despesa.
> - 1ª parcela recebida: transferência Empréstimo → Pix de **R$ 100,00** + entrada de **R$ 10,00** em "Juros recebidos", no Pix. Pix +110,00; a receber R$ 900,00; entradas +10,00.
> - Parcial: receber R$ 55,00 = R$ 50,00 de principal + R$ 5,00 de juros.

Invariante: **a receber (principal) = Σ restante das parcelas − juros não reconhecidos.**

### 6E.6 Pagamentos parciais e diferenças (o app pergunta, nunca presume)
| Caso | O app pergunta | Registro |
|---|---|---|
| Recebido, paguei a mais | Juros, multa, tarifa, IOF, outro | Despesa de encargo na conta do empréstimo + acréscimo na parcela |
| Recebido, paguei a menos | Parcial **ou** desconto | Parcial: o restante continua. Desconto: ajuste na conta do empréstimo + abatimento, **sem receita** ("Descontos obtidos") |
| Concedido, recebi a mais | Juros, multa, outro | Entrada em "Juros recebidos" pelo excedente + acréscimo na parcela |
| Concedido, recebi a menos | Parcial **ou** desconto concedido | Parcial: o restante continua. Desconto: abatimento **sem despesa** ("Descontos concedidos"). Ele consome **primeiro os juros não reconhecidos**; só o que passar reduz o principal (ajuste na conta) |

O valor previsto de uma parcela **nunca muda**: toda diferença é um registro separado e auditado.

### 6E.7 Atraso
Uma parcela com vencimento antes de hoje e restante > 0 fica **Atrasada**: "vencida há N dias", até a quitação. É calculado, nunca gravado, e não há juros automáticos.

### 6E.8 Edição de parcelas
- Só as parcelas **sem nenhum pagamento ou recebimento** podem ser editadas (uma parcela ou "esta e as futuras"): valor e data.
- A mudança de valor é um **acréscimo ou abatimento** na parcela (o previsto não muda), datado no dia da edição:
  - **Recebido (J1):** se aumentou, uma despesa de encargo nesse dia; se diminuiu, um ajuste de redução na conta do empréstimo ("Descontos obtidos"). O mês da contratação nunca é reescrito.
  - **Concedido (K2):** só mudam os juros não reconhecidos (a proporção é recalculada para os próximos recebimentos). Os juros já reconhecidos não mudam. A edição não pode deixar o total abaixo do principal a receber.
- Valor original e data da contratação: só enquanto nada foi pago ou recebido (excluir e cadastrar de novo).

### 6E.9 Desfazer e excluir
- Desfazer um pagamento ou recebimento remove juntos, numa transação: a transferência, a entrada de juros proporcional e os acréscimos e abatimentos daquele pagamento. Pode ser feito em qualquer ordem.
- Excluir um empréstimo só é possível sem nenhum pagamento ou recebimento. Isso remove a transferência inicial (ou o ajuste de abertura), os juros J1, as parcelas e as edições. Tudo fica auditado, com exclusão lógica.

### 6E.10 Empréstimo anterior ao app
- Só as **parcelas pendentes** são cadastradas, e **nenhuma transferência** toca no Pix.
- **Ajuste de abertura** na conta do empréstimo:
  - Recebido: −Σ pendentes. Nenhuma despesa (os juros J1 pertencem a uma contratação anterior ao app).
  - Concedido (D5): +(Σ pendentes − **juros contidos nas parcelas pendentes**), um campo opcional com padrão R$ 0,00. Com zero, todo recebimento é devolução de principal, sem receita.
- Aviso de possível duplicidade, como na compra anterior. Nada é convertido sozinho.

### 6E.11 Integração
- O valor original é sempre transferência, nunca entrada nem despesa.
- Juros J1: uma despesa criada só na contratação ou na edição.
- Juros K2: uma entrada criada **só** junto com um recebimento e desfeita com ele.
- Pagamentos de dívida são transferências, nunca despesas.
- A categoria de um empréstimo é só descritiva e **não** entra em "Despesas por categoria".
- As parcelas de empréstimo **recebido** aparecem em A pagar; as de **concedido**, nunca (só em Empréstimos → Concedidos).

---

## 6F. Objetivos e reservas ✅ v0.7

Aprovadas no briefing da v0.7 (decisões O1 = b, O2 = a, O3 = a). Ver [0028](decisoes/0028-banco-v6.md) e [0029](decisoes/0029-objetivos.md).

### 6F.1 O que é um objetivo
- Um objetivo é uma **reserva virtual** sobre o **dinheiro próprio**: o dinheiro continua na conta, e o objetivo só "carimba" uma parte dele.
- Tem nome, ícone e cor; **meta** e **prazo** são opcionais (sem meta, é uma "caixinha" livre, sem %).
- **VA/VR, cartões, crediários e empréstimos não podem ser reservados.** O Guardado é só sobre o dinheiro próprio.

```
Guardado do objetivo = Σ movimentos de reserva dele            (calculado, nunca gravado)
Guardado (total)     = Σ guardado dos objetivos não excluídos  (ativos, concluídos e arquivados)
Saldo disponível     = Saldo total − Guardado
Falta                = meta − guardado (mínimo R$ 0,00)          · só com meta
Progresso            = guardado ÷ meta                            · só com meta
Guardar por mês      = falta ÷ meses até o prazo (arredondado para cima) · só com meta e prazo, informativo
```

Meses até o prazo: do mês atual ao mês do prazo, **contando os dois** (mínimo 1). Prazo já passado: o app mostra "prazo vencido" e não sugere valor.

### 6F.2 Operações
| Operação | Guardado do objetivo | Disponível | Regra |
|---|---|---|---|
| **Guardar** R$ X | +X | −X | Bloqueada se X > disponível |
| **Retirar** R$ X | −X | +X | Até o guardado do objetivo. **Não é despesa** |
| **Mover** R$ X de A para B | A −X, B +X | igual | Até o guardado de A |
| **Ajustar** para R$ Y | grava a diferença (Y − guardado) como "ajuste" | −diferença | Um aumento segue o bloqueio do Guardar; nunca sobrescreve sem rastro |
| **Desfazer** um movimento | volta ao que era | volta ao que era | Recusado se deixar algum objetivo negativo; desfazer algo que **aumenta** o guardado segue o bloqueio do Guardar |

**Guardar, retirar, mover e ajustar NÃO são movimentações de dinheiro.** Eles não mudam entradas, saídas, despesas, o Resumo da Início, o fluxo do dinheiro próprio nem o saldo de nenhuma conta. Só mudam o Guardado e o Saldo disponível. **Um objetivo nunca fica com guardado negativo.**

> **Exemplo.** Saldo total R$ 1.000,00, nada guardado.
> - Guardar R$ 300,00 no "Fone": Guardado R$ 300,00 · **Saldo disponível R$ 700,00** · Saldo total R$ 1.000,00 (igual).
> - Guardar R$ 800,00 em outro objetivo: **recusado** (o disponível é R$ 700,00).
> - Retirar R$ 100,00 do "Fone": Guardado R$ 200,00 · disponível R$ 800,00.
> - Mover R$ 50,00 do "Fone" para a "Viagem": Fone R$ 150,00 · Viagem R$ 50,00 · Guardado R$ 200,00 · disponível R$ 800,00 (igual).
> - Ajustar a "Viagem" para R$ 80,00: ajuste de +R$ 30,00 no histórico · Guardado R$ 230,00 · disponível R$ 770,00.
> - Entradas, despesas e o Resumo da Início: **iguais** em todos os passos.

### 6F.3 Gastar o dinheiro de um objetivo (O1 = b)
**Não existe ligação entre despesa e objetivo.** Para gastar o dinheiro guardado: **1)** retire do objetivo; **2)** lance a despesa normalmente.

> **Exemplo.** Saldo total R$ 1.000,00, R$ 300,00 guardados no "Fone" (disponível R$ 700,00). Você compra o fone por R$ 300,00:
> - Retirar R$ 300,00 do "Fone": Guardado R$ 0,00 · disponível R$ 1.000,00.
> - Lançar a despesa de R$ 300,00: Saldo total R$ 700,00 · **disponível R$ 700,00** (correto).
> - Se você só lançar a despesa e esquecer de retirar: Saldo total R$ 700,00, Guardado R$ 300,00, disponível R$ 400,00. Para corrigir, retire do objetivo.

### 6F.4 Guardado maior que o saldo (O2 = a)
Uma despesa, um ajuste ou a exclusão de uma entrada podem deixar o Guardado maior que o Saldo total. Isso é **permitido**: o Saldo disponível aparece **negativo, em destaque**, com o aviso *"Seu guardado é maior que o seu saldo. Retire de um objetivo ou registre uma entrada."* **Nada é reduzido automaticamente.** Novas reservas continuam bloqueadas enquanto não houver disponível.

> **Exemplo.** Saldo total R$ 1.000,00, Guardado R$ 300,00. Uma despesa de R$ 900,00: Saldo total R$ 100,00 · Guardado R$ 300,00 · **Saldo disponível −R$ 200,00** com o aviso.

### 6F.5 Concluir, arquivar e excluir (O3 = a)
- **Concluir** e **arquivar** tiram o objetivo da lista ativa e mantêm o histórico. **O valor guardado continua guardado** até você retirar ou mover. Dá para reabrir (desconcluir/desarquivar). Só se guarda em objetivo ativo; retirar e mover continuam possíveis.
- **Excluir um objetivo com valor guardado é recusado:** "Retire ou mova os R$ X antes." Sem guardado, a exclusão é lógica e auditada.

### 6F.6 Análises
Seção **Objetivos**: guardado atual, progresso de cada objetivo, **guardado no período** (guardar e ajustes para cima) e **retirado no período** (retirar e ajustes para baixo), pela data do movimento. Mover não conta (é entre objetivos). Objetivos **não** são entrada, saída nem despesa.

### 6F.7 Fora da v0.7
- Guardar dentro do Preparar mês e cards de objetivos na Início (ficam para a v0.8, se fizerem falta).

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

### 7.5 Objetivos ✅ v0.7 (detalhada na seção 6F)
São **reservas virtuais** sobre o dinheiro próprio: o dinheiro continua na conta, e o objetivo só "carimba" uma parte. Uma nova reserva fica bloqueada se não houver disponível suficiente.

> Exemplo: com o dinheiro próprio em R$ 1.500,00, uma reserva de R$ 400,00 deixa o disponível em R$ 1.100,00. Uma nova reserva de R$ 1.200,00 é bloqueada.

### 7.6 Virada do mês: Preparar próximo mês ✅ v0.6
Assistente **"Preparar [mês]"** (em A pagar, e sugerido na virada do mês). **Nada é gravado antes da confirmação final.** Ver [0026](decisoes/0026-preparar-mes.md).
1. **Contas fixas:** as ativas que ainda não entraram no mês, **todas marcadas**, com o valor sugerido (o do mês anterior; sem ele, o valor padrão), editável. Desmarcar = não entra.
2. **Parcelas previstas:** faturas de cartão, parcelas de crediário e de empréstimo do mês, **só para conferência** (já existem), com o total que será cobrado, juros incluídos.
3. **Pendências atrasadas:** contas a pagar não quitadas de meses anteriores (**fora** as faturas, que ficam no mês original pela 6B.4), todas **desmarcadas**. As marcadas passam a aparecer **também** no mês novo, com o vencimento, o valor e o mês de referência **originais**, e continuam "atrasadas". As não marcadas continuam visíveis como pendentes no mês original. **Nada some.**
4. **Revisão:** o que entra e o total previsto do mês. Só então a confirmação grava tudo **numa transação**, com auditoria.

Rodar o assistente de novo para o mesmo mês **não duplica nada** (contas fixas já lançadas e pendências já levadas não aparecem de novo).

- O vencimento pode ficar vazio.

### 7.7 Empréstimos entre pessoas ✅ v0.5
Detalhados e implementados na v0.5: ver a seção 6E.

### 7.8 Backup e restauração ✅ v0.2
Completos e offline. A restauração substitui tudo (nunca mistura), guarda antes uma cópia do estado atual e fica registrada no Registro de alterações. Ver [`decisoes/0010-backup.md`](decisoes/0010-backup.md).
