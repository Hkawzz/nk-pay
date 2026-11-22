# Changelog

Todas as mudanças relevantes de cada versão. Formato inspirado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/), com versões no padrão [SemVer](https://semver.org/lang/pt-BR/).

As notas de cada Release do GitHub são tiradas automaticamente da seção da versão correspondente.

## [0.2.0] - 2026-11-22

**Backup e restauração.** A partir desta versão, perder ou trocar de celular não precisa mais significar perder os dados.

### Adicionado
- **Mais → Backup e restauração.**
  - **Fazer backup agora:** gera um arquivo `.nkpay` com tudo (contas, categorias, movimentações, inclusive as excluídas, e o Registro de alterações) e salva onde você escolher, como uma pasta do Google Drive ou Downloads.
  - **Restaurar de um arquivo:** confere o arquivo (se está íntegro e se é de uma versão compatível), mostra a data e o que ele contém e só substitui depois da sua confirmação.
  - Antes de restaurar, o app guarda sozinho uma **cópia do estado atual** ("Cópias antes de restaurar"), para você poder desfazer.
  - A restauração aparece no Registro de alterações.
- **Aviso na Início** quando você ainda não fez nenhum backup, ou quando o último tem 7 dias ou mais.
- 10 testes novos do backup: ida e volta idênticas, nenhuma mistura de dados, desfazer pela cópia automática e recusa de arquivos corrompidos, adulterados ou de versão mais nova.

## [0.1.1] - 2026-11-22

Ajustes de uso pedidos depois do primeiro teste no celular.

### Alterado
- **Digitar valores ficou como nos apps de banco:** o campo começa em 0,00 e cada número entra pela direita (0,01 → 0,12 → 1,27 → 12,78 → 127,80). Não é mais preciso apagar o 0,00. Nos campos que aceitam valor negativo (saldo inicial e ajuste), o botão **+/−** inverte o sinal.
- **Transições mais fluidas:** abrir uma tela desliza da direita e voltar desliza para a direita, em cerca de 0,25 s. Trocar de aba usa um fade rápido. Antes, todas as transições usavam o fade padrão, de 0,7 s.
- **Início mostra o saldo de cada conta** de dinheiro próprio ("Suas contas"). Tocar em uma conta abre a tela dela.

## [0.1.0] - 2026-11-22

Primeira versão para uso no dia a dia: contas, categorias, movimentações e o Registro de alterações. **A partir desta versão, os dados são reais: nenhuma atualização futura pode apagá-los.**

### Adicionado
- **Contas e carteiras** (Mais → Contas e carteiras): criar, editar, arquivar e excluir. Cada conta é *Dinheiro próprio* (Pix, dinheiro, bancos) ou *Benefício* (VA, VR, outro), com saldo inicial, data e cor opcional. A tela da conta mostra o saldo atual calculado e as movimentações dela. Uma conta com movimentações não pode ser excluída: o app oferece arquivar.
- **Categorias** (Mais → Categorias): sugestões criadas na primeira abertura (Alimentação, Moradia, Transporte, Saúde, Lazer, Tecnologia, Assinaturas, Educação, Outros) e a categoria de sistema "Encargos financeiros". Dá para criar, renomear e arquivar. Uma categoria arquivada some das novas escolhas e continua nos registros antigos.
- **Movimentações**: os 4 tipos (entrada, despesa, transferência e ajuste), com um formulário que muda conforme o tipo. O ajuste pode ser informado pelo **novo saldo**, e o app calcula a diferença. A lista é agrupada por dia, com seletor de mês, filtros por tipo e por conta e totais de entradas e despesas do mês (transferências e ajustes não entram).
- **Início**: Disponível em destaque, dinheiro próprio, reservado (R$ 0 até os objetivos chegarem), bloco **Benefícios** separado (nunca somado ao disponível), entradas e despesas do mês, últimas 5 movimentações e botão +. Na primeira abertura, mostra um passo a passo curto.
- **Registro de alterações** (Mais → Registro de alterações): toda criação, edição e exclusão, com data e hora e com o valor antes e depois de cada campo.
- Banco de dados local (Room/SQLite) com esquema versionado e sem migração destrutiva. Toda alteração é gravada na mesma transação da sua auditoria.
- 44 testes automáticos com dados fictícios: regras de saldo, banco de dados e um roteiro de aceite executado pela interface, que também gera capturas de tela.

### Segurança
- A Release só é publicada se o APK estiver assinado com a keystore oficial (o workflow confere a impressão digital do certificado).

## [0.0.1] - 2026-11-21

Primeira versão instalável. Ela não tem funcionalidades financeiras: serve para validar o caminho completo, do código até o celular (CI, assinatura, Release e instalação).

### Adicionado
- Projeto Android (Kotlin, Jetpack Compose e Material 3), com `applicationId` `com.nkpay.app`, para Android 8 ou mais novo.
- Tema escuro próprio (fundo `#09090B`, roxo `#4C1D95` e lilás `#A78BFA`), com algarismos tabulares nos valores.
- Ícone do app e nome "NK Pay".
- Barra inferior com as abas **Início · Movimentações · A pagar · Objetivos · Mais**. Todas mostram telas vazias, sem dados falsos.
- Tela **Mais**, com Contas e carteiras, Categorias, Registro de alterações e **Sobre** (mostra a versão do app).
- Formatação de dinheiro em pt-BR a partir de centavos (`R$ 1.234,56`), com testes.
- CI no GitHub Actions (compila e testa) e Release automática com o APK assinado a cada tag `v*`.
- Documentação: README com o passo a passo de instalação, regras financeiras e registros de decisão.
