# 0008 — Estrutura do banco de dados (esquema versão 1)

**Estado:** aceita (aprovada pelo dono antes da v0.1.0) · 2026-11-22

## Contexto
A partir da v0.1.0, o app guarda dados reais. Depois disso, qualquer mudança nas tabelas exige uma **migração** (um código que converte o banco antigo no novo sem perder nada). Por isso, a estrutura precisa nascer certa e já comportar as regras futuras (cartão, crediário, parcelas, objetivos e sincronização).

## Decisão
**Room 2.8** (SQLite). O Room 3 já é estável, mas é muito recente. O Room 2.8 é maduro e usa o mesmo arquivo SQLite, então uma troca futura não exige converter os dados.

Quatro tabelas na versão 1:

| Tabela | Colunas principais | Observações |
|---|---|---|
| `conta` | `natureza` (PROPRIO, BENEFICIO, CARTAO, CREDIARIO), `tipoBeneficio`, `saldoInicialCentavos`, `dataSaldoInicial`, `icone`, `cor`, `arquivadaEm` | `instituicao`, `limiteCentavos`, `diaFechamento` e `diaVencimento` já existem, vazias, para os cartões da v0.4 |
| `categoria` | `nome`, `codigoSistema`, `arquivadaEm` | `codigoSistema` identifica "Encargos financeiros" sem depender do nome |
| `movimentacao` | `tipo`, `data`, `valorCentavos`, `contaId`, `contaDestinoId`, `categoriaId`, `descricao` | No AJUSTE, o valor tem sinal. Nos outros tipos, é sempre positivo |
| `auditoria` | `grupoId`, `quando`, `operacao`, `entidade`, `entidadeId`, `resumo`, `antes`, `depois` | `antes` e `depois` guardam o registro inteiro em JSON |

Convenções em todas as tabelas financeiras:
- `id` é um UUID em texto.
- Dinheiro em centavos (`INTEGER`).
- Datas de lançamento como texto `AAAA-MM-DD`: sem hora, ordenáveis e legíveis.
- `criadoEm`, `atualizadoEm` e `excluidoEm` em milissegundos (UTC).
- **Exclusão lógica:** `excluidoEm` preenchido. Nada é apagado fisicamente.
- Chaves estrangeiras com `RESTRICT`: o banco recusa apagar uma conta ou categoria que ainda tenha movimentações.

Regras de gravação:
- Toda alteração passa por um repositório, que roda **uma transação** com a alteração **e** a linha de auditoria.
- `exportSchema = true`: o esquema de cada versão fica em `app/schemas/` (no Git).
- **Nunca** usar `fallbackToDestructiveMigration`.

### Como as regras futuras cabem nesta estrutura
- **Cartão e crediário (v0.4):** são contas com outra natureza. A compra parcelada é uma `movimentacao` DESPESA pelo valor total, e pagar a fatura é uma TRANSFERENCIA para a conta do cartão. As parcelas vão para uma tabela nova (`obrigacao`), sem mudar as atuais.
- **Pagamentos parciais e diferenças (v0.3):** tabelas novas (`pagamento`, `obrigacao_ajuste`) que apontam para `movimentacao`.
- **Encargos:** DESPESA com a categoria de `codigoSistema = ENCARGOS_FINANCEIROS`.
- **Objetivos (v0.6):** tabelas novas. O "reservado" continua calculado.
- **Sincronização:** UUID + `atualizadoEm` + `excluidoEm` já existem.

## Consequências
- ✅ As versões futuras **adicionam** tabelas, sem precisar reestruturar as atuais.
- ✅ A auditoria guarda o estado completo, e não só "o que mudou". É mais espaço, mas é simples e não perde informação.
- ⚠️ A tabela de auditoria cresce sempre, porque nunca é limpa. Para uso pessoal, isso dá poucos MB por ano.
- ⚠️ Mudar esta estrutura depois da v0.1.0 exige uma migração testada.
