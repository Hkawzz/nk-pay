# 0020 — Banco versão 4: empréstimos

**Estado:** aceita (especificação da v0.5.0, seção 4.1, aprovada) · v0.5

## Decisão
Esquema 4, com a migração `MIGRACAO_3_4` escrita à mão:

| Tabela | Mudança |
|---|---|
| `emprestimo` (nova) | id, contaId (a conta da pessoa), descricao, categoriaId, valorOriginalCentavos, dataContratacao, contaPropriaId (vazio no anterior ao app), anteriorAoApp, jurosInformadosCentavos (só no concedido anterior, D5), movimentacaoContratacaoId (a transferência ou o ajuste de abertura), movimentacaoJurosId (a despesa J1), carimbos e exclusão lógica |
| `obrigacao` | `ALTER TABLE ADD COLUMN emprestimoId TEXT` (vazia) + índice. Sem chave estrangeira (o SQLite não acrescenta uma sem recriar a tabela; é o mesmo caso de `compraId`) |

- É a segunda alteração numa tabela existente. `ADD COLUMN` não reescreve as linhas.
- A origem `EMPRESTIMO` da obrigação e as naturezas novas de conta são textos novos, sem mudança de coluna.
- O `.nkpay` passa a incluir `emprestimos` (lista vazia como padrão). A v0.4 recusa backups da v0.5 (versão 4 > 3).
- A categoria de sistema **"Juros recebidos"** é criada por um passo auditado do app (primeira abertura e depois de restaurar), e não pela migração.

## Proteções
A cópia do arquivo do banco antes de migrar continua valendo. O teste usa um banco **v3 fiel** (SQL oficial do esquema 3) com dados de todas as tabelas, inclusive cartões, compras e abatimentos de fatura.
