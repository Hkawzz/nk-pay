# 0025 — Juros do parcelamento no cartão e no crediário (D-A1 c)

**Estado:** aceita (briefing da v0.6, D-A1 c, D-A2 b, D-A3 b) · v0.6 · amplia a [0015](0015-fatura-e-pagamento-de-credito.md) e a [0016](0016-compra-anterior-ao-app.md)

## Decisão
- A compra grava a despesa do **valor original**. Cada parcela tem o valor cobrado (com juros); a **parte da compra** (original ÷ N, a última com os centavos) e os **juros previstos** (parcela − parte) são **calculados**, nunca gravados.
- **Devido = só a parte da compra.** "Juros a pagar" aparece separado. Invariante: Devido = Σ restante das faturas − juros a pagar.
- **Ao pagar**, os juros da parte paga viram uma despesa em "Encargos financeiros", na conta de crédito, na data do pagamento. Ela fica ligada ao pagamento por um ajuste novo, `JUROS_DO_PARCELAMENTO`, que **não muda o restante** (como o `JUROS_RECEBIDOS` da v0.5). Desfazer remove tudo junto.
- **Proporção:** juros do pagamento = valor × (juros pendentes ÷ restante). O pagamento que quita (a parcela, ou a fatura) fecha os centavos.
- **Abatimento geral da fatura:** os juros ficam num ajuste `JUROS_DO_PARCELAMENTO` na primeira parcela em aberto da fatura, marcado com a transferência (`observacao = "geral:<id>"`), para o desfazer achá-lo sem mudar o banco.
- **Desconto** (crediário): consome primeiro os juros pendentes da parcela (um abatimento sem movimentação); só o que passar reduz o devido (ajuste na conta).
- **Compra anterior:** o ajuste na conta de crédito soma só a **parte da compra** das parcelas pagas antes; os juros delas **não** viram despesa (D-A3 b). Desconto de antecipação antes do app: natureza `DESCONTO_ANTES_DO_APP`, que **entra** em "Descontos obtidos", datado na data da compra (D-A2 b).

## Diferença para a J1 dos empréstimos
Na J1, os juros entram inteiros na contratação. Aqui, entram parcela a parcela, quando cada uma é paga. O "?" do cartão explica isso.

## Limitação conhecida (nota para o futuro)
Pela D-A3 (b), os **juros das parcelas pagas antes do app não entram nas Análises**. Se o app um dia for usado por outras pessoas, os juros e os descontos de parcelas pagas antes do app vão precisar entrar nas Análises de forma completa.
