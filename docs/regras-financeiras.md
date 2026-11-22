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
| `CARTAO` | Cartão de crédito (limite, instituição, dia de fechamento, dia de vencimento) | Não. O limite nunca entra em saldo nenhum | 🔜 telas na v0.4. A v0.1 já reserva a natureza e as colunas |
| `CREDIARIO` | Carnê ou boleto parcelado (credor sem limite) | Não | 🔜 decisão provisória. Telas na v0.4 |

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

## 7. Regras futuras 🔜

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
