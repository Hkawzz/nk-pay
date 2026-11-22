# 0002 — Dinheiro em centavos inteiros (`Long`)

**Estado:** aceita · 2026-11-21

## Contexto
Números com casas decimais (`Double`/`Float`) são aproximações binárias. Exemplo: `0.1 + 0.2 = 0.30000000000000004`. Em um app financeiro, esses erros se acumulam e geram diferenças de centavos que ninguém consegue explicar.

## Decisão
- Todo valor monetário é um **`Long` em centavos**: R$ 1.234,56 é guardado como `123456`.
- Conversões para texto e de texto para centavos ficam em **um único lugar** (`dominio/Dinheiro.kt`), com testes.
- A formatação pt-BR (`R$ 1.234,56`, `-R$ 5,00`) é feita à mão, sem `NumberFormat`, para o resultado ser idêntico em todo aparelho (o `NumberFormat` usa espaços especiais que mudam entre versões do Android).

## Consequências
- ✅ Somas e subtrações exatas, sempre.
- ✅ Um `Long` aguenta até cerca de R$ 92 quatrilhões, muito acima de qualquer necessidade.
- ⚠️ Divisões (por exemplo, R$ 100,00 em 3 parcelas) precisam de uma regra explícita de arredondamento. Isso será definido na v0.4: a última parcela absorve a diferença.
