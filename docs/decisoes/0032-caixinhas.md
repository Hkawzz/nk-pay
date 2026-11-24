# 0032 — Objetivos como caixinhas de verdade (R2 e V3)

**Estado:** aceita (briefing da v0.8: R2, V3 = b) · v0.8 · substitui o "carimbo" da [0029](0029-objetivos.md)

## Decisão
- Cada objetivo ganha uma **conta interna de natureza `CAIXINHA`** (`objetivo.contaCaixinhaId`). Ela reusa transferências, saldo, backup e auditoria que já existem. O objetivo continua sendo a tela.
- Guardar = transferência conta própria → caixinha; retirar = caixinha → conta própria; mover = caixinha → caixinha; **rendimento = entrada** na caixinha, categoria de sistema "Rendimentos".
- Saldo total = próprias + caixinhas; Guardado = Σ caixinhas; Saldo disponível = Saldo total − Guardado (regra 6G.2).
- **A caixinha não é meio de pagamento**, garantido na camada de dados (como as contas de terceiros, [0022](0022-contas-de-terceiros.md)): ela não pode ser usada em lançamentos avulsos, no Pagar, em faturas, crediários, empréstimos nem como conta padrão de conta fixa, e não aparece nas listas de contas.
- O aviso O2 deixa de existir (a R1 impede a despesa sem saldo). O ajuste manual do guardado também deixa de existir: o saldo da caixinha é o que entrou e saiu dela.
- **V3 = (b):** os objetivos da v0.7 ganham uma caixinha **zerada**; os movimentos de reserva antigos ficam **só para leitura** ("antes das caixinhas"). Nenhuma transferência é criada e nenhum saldo de conta muda. A conversão é idempotente e roda também depois de restaurar um backup da v0.7.