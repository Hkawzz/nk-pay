# 0029 — Objetivos: reservas virtuais sobre o dinheiro próprio

**Estado:** aceita (briefing da v0.7: O1 = b, O2 = a, O3 = a; itens [ADOTADO] da proposta) · v0.7

## Contexto
O Guardado existe na fórmula do Saldo disponível desde a v0.1 (regra 4), mas era sempre R$ 0,00. A v0.7 cria os objetivos, e é a primeira vez que o Saldo disponível pode ser diferente do Saldo total.

## Decisão
- **Reserva virtual:** guardar, retirar, mover e ajustar **não** são movimentações de dinheiro. Não mudam entradas, saídas, despesas, Resumo da Início, fluxo nem saldo de conta; só o Guardado e o Saldo disponível (regra 6F).
- **Só o dinheiro próprio:** VA/VR, cartões, crediários e empréstimos não entram. O objetivo não escolhe conta: ele reserva uma parte do total próprio.
- **O1 = (b):** sem ligação entre despesa e objetivo. Para gastar, retire e depois lance a despesa. O "?" explica o passo a passo.
- **O2 = (a):** guardado maior que o saldo é permitido; o Saldo disponível fica negativo, em destaque, com aviso. Nada é reduzido sozinho. Novas reservas continuam bloqueadas sem disponível.
- **O3 = (a):** excluir com guardado é recusado ("Retire ou mova os R$ X antes"). Sem guardado, exclusão lógica e auditada.
- **Ajuste** nunca sobrescreve: grava a diferença como movimento "ajuste", no histórico.
- **Desfazer** é recusado se deixar algum objetivo negativo; desfazer algo que aumenta o guardado (ex.: uma retirada) segue o bloqueio do Guardar.
- **Concluir e arquivar** mantêm o guardado; só se guarda em objetivo ativo.
- **Prazo:** "guardar R$ X/mês" é só informativo (falta ÷ meses do mês atual ao do prazo, contando os dois, arredondado para cima).
- **Preparar mês** fica sem objetivos na v0.7.

## Consequências
- Todos os testes da Início com Guardado > 0 passam a valer de verdade.
- As regras ficam no domínio (`Objetivos`) e no serviço (`ObjetivoServico`), na camada de dados, e não só na tela.
