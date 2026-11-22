# 0010 — Backup e restauração

**Estado:** aceita · 2026-11-22

## Contexto
Os dados ficam só no celular (0006), então o backup é a única proteção contra perder ou resetar o aparelho. Ele também é a saída de emergência se a keystore um dia se perder: exportar, instalar o app novo e restaurar.

## Decisão
- **Arquivo `.nkpay`:** um ZIP com `manifest.json` (formato, versão do banco, versão do app, data, contagens e o SHA-256 dos dados) e `dados.json` (todas as linhas das 4 tabelas, inclusive as excluídas logicamente e toda a auditoria).
- **Backup lógico (JSON), e não uma cópia do arquivo SQLite.** Funciona em qualquer Android 8+, não depende do estado interno do SQLite e permite converter backups antigos quando o banco mudar de versão (a conversão entra em `BackupServico.ler`).
- **Onde salvar:** o seletor de arquivos do Android, para a pessoa escolher Drive, Downloads ou cartão SD. O app continua sem permissão de internet.
- **Restauração:**
  1. Valida tudo **antes** de mexer em qualquer coisa: estrutura do ZIP, formato, versão (um backup de versão **mais nova** é recusado), SHA-256 e se as referências entre as tabelas fecham. Há também um limite de tamanho, contra arquivos gigantes.
  2. Mostra um resumo (data e contagens) e pede confirmação.
  3. Guarda uma **cópia automática do estado atual** na pasta interna do app (as 5 mais recentes), que pode ser restaurada para desfazer.
  4. Em **uma transação**, apaga tudo, insere o backup e registra a restauração no Registro de alterações. Nada é misturado.
- **Aviso na Início** quando existe conta e o último backup nunca foi feito ou tem 7 dias ou mais. A data do último backup fica nas preferências do app (não é um dado financeiro).
- **Sem senha por enquanto.** Ela protegeria o arquivo se ele vazasse do Drive, mas, se esquecida, o backup se perderia para sempre. Pode entrar depois como opção, sem quebrar os backups atuais (o `formato` do manifesto existe para isso).

## Consequências
- ✅ Trocar de celular, resetar ou desfazer um erro grande passam a ter solução.
- ✅ Um arquivo corrompido ou adulterado nunca chega a ser aplicado.
- ⚠️ O backup é manual até a v0.8 (backup automático). O aviso na Início ajuda a lembrar.
- ⚠️ O arquivo não é criptografado: quem tiver acesso a ele vê os dados. Guarde-o em um lugar só seu.
- ⚠️ Restaurar volta **tudo** ao estado do backup, inclusive o Registro de alterações. O que foi feito depois só fica na cópia automática.
