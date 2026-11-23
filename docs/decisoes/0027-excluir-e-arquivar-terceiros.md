# 0027 — Excluir e arquivar cartões, crediários e pessoas

**Estado:** aceita (análise de ajustes pós-testes da v0.6, itens 3 e 5, [ADOTADO]) · v0.6

## Decisão
- **Excluir** (exclusão lógica, com auditoria) só se a conta não tiver nenhuma compra, pagamento, abatimento geral, empréstimo nem movimentação. Se tiver, o app diz o que impede e oferece **Arquivar**.
- **Arquivar** é recusado enquanto houver devido (cartão, crediário) ou valor a pagar ou a receber (pessoa), porque a conta sumiria das telas com algo em aberto.
- **"Mostrar arquivados"** nas listas de Cartões e crediários e de pessoas, com **Desarquivar**.
- **Editar o nome** de uma pessoa ou instituição: livre, com auditoria; os registros apontam para a conta, não para o nome.
- As regras ficam no `ContaRepositorio` (camada de dados), não só na tela.
