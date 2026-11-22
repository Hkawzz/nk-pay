# 0009 — Saldo inicial, contas arquivadas e transferências com benefícios

**Estado:** aceita (decisões secundárias, reversíveis) · 2026-11-22

## Contexto
Três situações que o briefing não detalha:
1. Uma movimentação com data **anterior** à data do saldo inicial da conta. Ela conta no saldo?
2. Uma conta **arquivada** que ainda tem saldo. Ele entra no dinheiro próprio?
3. Uma transferência entre uma conta **própria** e uma de **benefício**. Ela é permitida?

## Decisão
1. **Todas as movimentações contam**, qualquer que seja a data. A data do saldo inicial é só informativa. É a regra mais simples de entender: o saldo é o inicial mais tudo o que você lançou naquela conta.
2. **Contas arquivadas continuam contando** nos saldos. Arquivar só esconde a conta das novas escolhas, e o dinheiro dela não deixa de existir. O caso normal é zerar a conta (com uma transferência ou um ajuste) e depois arquivar.
3. **Transferências entre próprio e benefício são permitidas nos dois sentidos.** A análise deixou "benefício → próprio" em aberto. Bloquear agora poderia impedir casos reais, e o bloqueio pode ser adicionado depois sem afetar dados.

## Consequências
- ✅ Nenhum dinheiro "some" dos totais sem uma ação explícita.
- ⚠️ Se você lançar algo com data anterior ao saldo inicial, o valor conta duas vezes (já estava no saldo inicial). Evite fazer isso.
- ↩️ As três regras podem mudar sem migração do banco: são só cálculo e validação.
