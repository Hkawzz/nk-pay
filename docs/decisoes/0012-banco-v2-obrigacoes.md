# 0012 — Banco versão 2: contas fixas, obrigações, pagamentos e ajustes

**Estado:** aceita (aprovada pelo dono em 2026-11-22) · v0.3

## Contexto
A v0.3 traz contas a pagar: contas fixas, obrigações, o botão Pagar, pagamentos parciais e diferenças (juros, desconto...). Isso exige tabelas novas. O app já tem **dados reais**, então a mudança precisa preservar tudo.

## Decisão
**Quatro tabelas novas. As quatro tabelas da versão 1 (`conta`, `categoria`, `movimentacao`, `auditoria`) não mudam.** As novas só apontam para as antigas, sem copiar nem duplicar dados.

| Tabela | Para que serve | Aponta para |
|---|---|---|
| `conta_fixa` | O modelo de uma conta que se repete (luz, internet). Não é paga: gera obrigações | categoria, conta (sugerida para pagar) |
| `obrigacao` | Algo a pagar: conta fixa de um mês, conta avulsa ou (v0.4) parcela. Guarda o **valor previsto original** e o **mês de referência** | conta_fixa, categoria, conta (crédito, v0.4) |
| `pagamento` | "Esta movimentação pagou X desta obrigação". Uma obrigação pode ter vários | obrigacao, movimentacao |
| `obrigacao_ajuste` | Juros, multa, tarifa, IOF, desconto e outros, sempre ligados a uma obrigação | obrigacao, pagamento, movimentacao |

- O status (pendente, parcial, quitada, atrasada) e o restante são **calculados**, nunca gravados:
  `restante = previsto + acréscimos − abatimentos − pagamentos aplicados`.
- Os campos das parcelas da v0.4 (`contaCreditoId`, `compraId`, `numeroParcela`, `totalParcelas`) já existem, vazios.
- A regra de não duplicar a obrigação de uma conta fixa no mesmo mês fica no repositório (com teste), e não em um índice único: a exclusão lógica impediria recriar uma obrigação excluída.

## Como a atualização protege os dados
1. **Cópia do arquivo antes de migrar:** ao abrir o app, se o banco no celular estiver numa versão mais antiga, o arquivo inteiro (com os diários WAL/SHM) é copiado para `copias-antes-de-migrar/` dentro do app, antes de qualquer alteração. São guardadas as 3 cópias mais recentes.
2. **Migração explícita (`MIGRACAO_1_2`):** só executa `CREATE TABLE` e `CREATE INDEX` das tabelas novas, com o SQL exato que o Room gerou para a versão 2. Ela não lê, altera nem copia nenhuma linha existente. O Room roda a migração em uma transação: se algo falhar, nada muda.
3. **Validação:** ao abrir, o Room compara o esquema resultante com o esperado e recusa abrir se houver qualquer diferença.
4. **Backup:** o `.nkpay` passa a incluir as tabelas novas. Backups da v0.2 (banco versão 1) continuam restauráveis: as listas novas simplesmente vêm vazias.

## Testes (MigracaoTest e BackupTest)
- Um banco criado com o SQL oficial do esquema 1 e preenchido com dados fictícios migra para o 2 com **todas as linhas das 4 tabelas antigas idênticas**, campo a campo.
- As tabelas novas nascem vazias, com o SQL idêntico ao do esquema 2, e nenhum id fica duplicado.
- Saldos por conta, Disponível, Benefícios, movimentações e histórico são os mesmos depois da migração. Abrir de novo não migra outra vez.
- Um backup feito na v0.2 restaura na v0.3, e um backup feito depois da migração restaura igual.
- A cópia de segurança do arquivo é feita antes de migrar e guarda as 3 mais recentes.

## Consequências
- ✅ Atualizar da v0.2 para a v0.3 não toca em nenhum dado existente.
- ✅ Se a migração falhar no celular, há duas saídas: a cópia do arquivo feita antes e o backup `.nkpay` que o aviso da Release pede para fazer.
- ⚠️ Depois da v0.3, voltar para a v0.2 não é possível: o Android não instala uma versão mais antiga por cima. Para voltar, seria preciso reinstalar e restaurar um backup da v0.2.
