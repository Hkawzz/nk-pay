# NK Pay — instruções para o Claude Code

App Android de finanças pessoais (Kotlin + Compose + Room), 100% offline, de uso pessoal. **O app está em uso com dados financeiros reais: preservar os dados é prioridade absoluta.**

## Regras do projeto
- **A cada versão publicada, atualize `docs/RELATORIO.md`**: adicione a seção da versão no topo e a linha na tabela (o que foi feito, PRs, decisões, problemas e testes), além do `CHANGELOG.md`.
- Documentação, commits e PRs em português, explicando o *porquê* em linguagem simples. Commits em Conventional Commits.
- Fluxo: branch → PR → CI verde → merge → tag `vX.Y.Z` (o `release.yml` assina e publica). A `main` sempre compila.
- Decisões secundárias: escolha a opção mais simples e reversível e registre em `docs/decisoes/`. Pergunte ao dono só o que é difícil de desfazer (estrutura do banco, applicationId, assinatura).
- Banco: toda mudança de esquema tem uma `Migration` explícita e um teste de migração. **Nunca** use `fallbackToDestructiveMigration`. Os esquemas ficam em `app/schemas/`.
- Nenhum dado real (planilhas, bancos, backups `.nkpay`) entra no repositório. Os testes usam só valores fictícios.
- Material de planejamento e qualquer arquivo pessoal do dono ficam fora do repositório: nunca copie nada de lá para cá.

## Ambiente (Windows)
- Ferramentas (JDK 21, Android SDK, Git, GitHub CLI, Gradle) numa pasta local de ferramentas, fora do repositório. O login do `gh` fica nessa pasta, apontado por `GH_CONFIG_DIR`.
- Testes: `.\gradlew.bat testDebugUnitTest -Proborazzi.test.record=true` (as capturas de tela vão para `app/build/outputs/roborazzi/`).
