# 0014 — Banco versão 3: compras, abatimentos de fatura e aparência das categorias

**Estado:** aceita (estrutura prevista na proposta de execução da v0.4, aprovada em 23/11/2026) · v0.4

## Contexto
Cartões e crediários precisam de duas tabelas novas: uma para identificar cada **compra** (que gera a despesa e as parcelas) e outra para o **abatimento geral de uma fatura** (pagamento parcial sem escolher compras), que não cabe na tabela `pagamento`, porque ela sempre aponta para uma obrigação. As categorias ganham **ícone e cor**.

## Decisão
Esquema versão 3, com a migração `MIGRACAO_2_3` escrita à mão:

| Tabela | Mudança |
|---|---|
| `compra` (nova) | id, contaCreditoId, descricao, categoriaId, dataCompra, valorTotalCentavos, totalParcelas, movimentacaoId (a despesa), anteriorAoApp, carimbos e exclusão lógica |
| `pagamento_fatura` (nova) | id, contaCreditoId, mesReferencia, movimentacaoId (a transferência), valorCentavos, carimbos e exclusão lógica |
| `categoria` | `ALTER TABLE ADD COLUMN icone TEXT` e `ADD COLUMN cor INTEGER`, vazias |

- É a **primeira alteração em uma tabela existente**. `ADD COLUMN` no SQLite não reescreve as linhas: os valores atuais ficam idênticos, e as colunas novas nascem vazias.
- A aparência sugerida **não** é aplicada pela migração (que não passa pela auditoria), mas por um passo auditado do app ([0018](0018-categorias-com-aparencia.md)).
- `CategoriaEntidade` recebe `icone: String? = null` e `cor: Long? = null`. Os valores padrão mantêm os backups da v0.3 restauráveis.
- O `.nkpay` passa a incluir `compras` e `pagamentosDeFatura`, com lista vazia como padrão (backups antigos continuam válidos). A v0.3 recusa backups da v0.4, porque a versão do banco é maior.
- As colunas reservadas da v0.1 e da v0.2 (`conta.limiteCentavos`, `diaFechamento`, `diaVencimento`; `obrigacao.contaCreditoId`, `compraId`, `numeroParcela`, `totalParcelas`) passam a ser usadas, sem migração.
- A natureza de ajuste nova `QUITADA_ANTES_DO_APP` é só um texto novo na coluna `natureza`: não precisa de migração.

## Proteções
- A cópia do arquivo do banco antes de migrar (v0.3) continua valendo.
- Teste de migração com um banco **v2 fiel** (criado pelo SQL oficial do esquema 2, com dados fictícios): todas as linhas idênticas campo a campo, saldos iguais, abrir duas vezes não migra de novo, e o próprio Room valida o esquema final.
- Matriz de backup: v0.3 → v0.4, v0.4 → v0.4 com cartões, v0.4 recusado pela v0.3, arquivo adulterado recusado.

## Consequências
- O teste antigo `MigracaoTest` conferia `db.version == 2` depois de abrir um banco v1. Com o esquema 3, o mesmo caminho termina na versão 3. É a **única linha alterada** em um teste antigo, e o motivo está registrado no PR e no RELATORIO.
