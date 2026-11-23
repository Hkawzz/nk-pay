# Changelog

Todas as mudanças relevantes de cada versão. Formato inspirado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/), com versões no padrão [SemVer](https://semver.org/lang/pt-BR/).

As notas de cada Release do GitHub são tiradas automaticamente da seção da versão correspondente.

## [0.6.0] - 2026-11-23

> ⚠️ **Antes de atualizar, gere um backup em Mais → Backup e guarde o arquivo fora do celular.**
> Esta versão atualiza o banco de dados (acrescenta uma coluna vazia às compras e cria a tabela das pendências levadas; nenhum dado existente é reescrito). O app guarda sozinho uma cópia do banco antes de atualizar. Sugestão: teste antes no app de debug, com uma cópia dos dados (`docs/roteiro-teste-v0.6.md`).

Ajustes dos testes da v0.5 (compra com juros, parcelas antecipadas, excluir e arquivar) e o assistente **Preparar próximo mês**.

### Adicionado
- **Compra parcelada com juros:** informe o valor da compra e o valor da parcela. A despesa da compra é o valor original; os **juros do parcelamento** viram despesa (Encargos financeiros) **parcela a parcela, quando cada uma é paga**. A tela do cartão mostra o **Devido** e os **Juros a pagar**, com "?".
- **Compra anterior ao app:** **parcelas antecipadas**, com o valor pago ou o desconto (o desconto aparece em "Descontos obtidos", no mês da compra). Compras anteriores com juros contam só a parte da compra no devido.
- **Preparar [mês]** em A pagar (e sugerido na virada do mês): contas fixas marcadas com o valor do mês anterior (editável), parcelas do mês para conferência, pendências atrasadas para levar ao mês novo, revisão e confirmação de uma vez. Nada é gravado antes de confirmar, e repetir não duplica.
- **Empréstimos → Pessoas:** editar o nome, arquivar, desarquivar e excluir.
- **Empréstimo anterior:** "Total de parcelas do contrato" e "Parcelas já pagas"; as pendentes ficam numeradas (ex.: 5/12).
- **Cartões e crediários:** "Mostrar arquivados", Desarquivar e Excluir.

### Alterado
- **Excluir** cartão, crediário ou pessoa só sem nada ligado; com histórico, o app explica e oferece **Arquivar**.
- **Arquivar** é recusado enquanto houver valor devido, a pagar ou a receber.
- Pagamentos de fatura, de parcela e de compras escolhidas separam a parte da compra e os juros; **Desfazer** remove os juros junto.

## [0.5.0] - 2026-11-23

> ⚠️ **Antes de atualizar, gere um backup em Mais → Backup e guarde o arquivo fora do celular.**
> Esta versão atualiza o banco de dados (cria a tabela de empréstimos e acrescenta uma coluna vazia às contas a pagar; nenhum dado existente é reescrito). O app guarda sozinho uma cópia do banco antes de atualizar. Sugestão: teste antes no app de debug, com uma cópia dos dados (`docs/roteiro-teste-v0.5.md`).

**Empréstimos**, o Resumo da Início pelo dinheiro que entra e sai, e a ajuda "?".

### Adicionado
- **Empréstimos** (Mais → Empréstimos), em duas abas: **Recebidos** (eu devo) e **Concedidos** (me devem), agrupados por pessoa, com o total, as parcelas e as atrasadas ("vencida há N dias").
  - **Cadastro** com parcelas automáticas ou personalizadas; o app mostra na hora o total e os juros previstos.
  - **Peguei emprestado:** o dinheiro entra na conta, e os juros viram **uma** despesa (Encargos financeiros) na data. Pagar a parcela é só transferência.
  - **Emprestei:** o dinheiro sai da conta. Cada parcela recebida é dividida em **devolução** e **juros recebidos** (receita), na proporção; o último recebimento fecha os centavos.
  - Pagar ou receber **a mais ou a menos**: o app pergunta (parcial, desconto, juros, multa…). Todo pagamento pode ser **desfeito**.
  - **Alterar parcelas** ainda sem pagamento (valor e data, uma ou as seguintes).
  - **Empréstimo anterior ao app:** só as parcelas que faltam, sem mexer no Pix; no "emprestei", com os juros contidos opcionais. Aviso de possível duplicidade.
  - As parcelas de empréstimo que você deve aparecem em **A pagar**.
- **Ajuda "?"** nos indicadores da Início, das Análises e dos Empréstimos: o que é, como é calculado, exemplo e o que não é.
- **Análises:** seção Empréstimos (a pagar, a receber, juros pagos e recebidos, atrasadas) e "Descontos concedidos" separados dos obtidos.
- **Arquivar** cartões e crediários na tela deles.

### Alterado
- **Início:** o card passa a se chamar **Saldo disponível**. O **Resumo do mês** mostra **Entradas, Saídas e Saldo das movimentações** do seu dinheiro próprio: compras no cartão saem quando a fatura é paga, e despesas do VA/VR não são saídas.
- **Análises:** "Despesas registradas" e "Saídas do dinheiro próprio" lado a lado, cada um com "?"; a evolução mensal destaca o mês atual.
- Contas de empréstimo seguem as mesmas proteções dos cartões: não aparecem nas listas para lançar e não aceitam lançamentos avulsos.

## [0.4.0] - 2026-11-23

> ⚠️ **Antes de atualizar, gere um backup em Mais → Backup e guarde o arquivo fora do celular.**
> Esta versão atualiza o banco de dados (cria as tabelas de compras e de pagamentos de fatura e acrescenta ícone e cor às categorias; nenhum dado existente é reescrito). O app guarda sozinho uma cópia do banco antes de atualizar. Sugestão: teste antes no app de debug, com uma cópia dos dados (`docs/roteiro-teste-v0.4.md`).

**Cartões e crediários**, categorias com ícone e cor, a nova tela Início e as Análises.

### Adicionado
- **Cartões e crediários** (Mais → Cartões e crediários): cadastro com limite, dia de fechamento e dia de vencimento; cartão visual com o devido e o limite disponível. O devido **nunca** entra no Disponível.
- **Compra parcelada:** a compra inteira entra como despesa no mês da compra, e as parcelas caem nas faturas pelo dia de fechamento (a última parcela absorve os centavos).
- **Compra feita antes de usar o app:** registra uma dívida que você já tinha, com as parcelas já pagas (ou pagas em parte), sem mexer no Pix nem no Disponível. O app **avisa** quando encontra algo parecido já lançado e nunca converte nada sozinho.
- **Fatura:** pagar um valor (total ou parcial; o valor cobre primeiro as faturas mais antigas em aberto) ou escolher as compras que está pagando. Pagar a fatura é uma transferência, não uma despesa nova. Se pagar a mais, o app pergunta se foi juros, multa, tarifa ou IOF. Fatura vencida fica atrasada no mês original, sem juros automáticos. Todo pagamento pode ser **desfeito**.
- **Crediário:** cada parcela é paga na tela dela, com parcial, desconto (que reduz o que você deve, sem virar receita) e encargos.
- **Faturas em A pagar:** uma linha por fatura, deste mês e atrasadas.
- **Categorias com ícone e cor:** 40 ícones, 12 cores e uma cor personalizada (clareada automaticamente para ficar legível). As categorias que você já tinha recebem uma aparência sugerida pelo nome, registrada no Registro de alterações.
- **Análises** (Mais → Análises): resultado do período, despesas por categoria, evolução mensal, fluxo do dinheiro próprio, contas e carteiras, e cartões. Filtros de este mês, 3, 6 e 12 meses.

### Alterado
- **Nova tela Início:** Disponível em destaque, Minhas contas em 2 colunas, Benefícios em 3 colunas (com Ver todas), o Resumo do mês com seletor e as últimas movimentações agrupadas por dia, com o ícone da categoria.
- **Visual v2:** cards com cantos maiores, destaque com degradê roxo e ícones em quadrados com a cor da categoria.
- Cartões e crediários não aparecem nas listas para lançar movimentações nem no Pagar das contas fixas.
- O app de debug passa a se chamar "NK Pay debug".

## [0.3.0] - 2026-11-22

> ⚠️ **Antes de atualizar, gere um backup em Mais → Backup e guarde o arquivo fora do celular.**
> Esta versão atualiza o banco de dados (só adiciona tabelas novas; nada do que existe é alterado). Por segurança, o app também guarda sozinho uma cópia do banco antes de atualizar.

**Contas a pagar** e um visual refinado.

### Adicionado
- **Aba A pagar:** as contas de cada mês, com o que falta pagar e o que já foi pago. As contas vencidas e não pagas continuam aparecendo como **atrasadas**, no mês original.
- **Contas fixas** (Mais → Contas fixas): os modelos que se repetem todo mês. O botão **"Lançar contas fixas de [mês]"** deixa você escolher quais entram no mês e nunca duplica.
- **Conta avulsa:** algo a pagar uma vez só, com vencimento opcional.
- **Botão Pagar:** escolhe a conta de onde sai o dinheiro, o valor e a data, e mostra antes o que vai ser registrado.
  - **Pagou menos:** pergunta se foi *pagamento parcial* (o restante continua pendente) ou *desconto* (quita, sem criar receita).
  - **Pagou mais:** pergunta se foi juros, multa, tarifa ou IOF. O encargo vira uma despesa separada, em "Encargos financeiros".
  - A despesa é sempre o valor efetivamente pago, no mês em que foi paga.
  - Cada pagamento pode ser **desfeito** (a despesa, o encargo e o desconto saem juntos).

### Alterado
- Visual refinado, mantendo a identidade preta e roxa: espaçamentos, cantos e tipografia padronizados; ícones em um estilo só; item ativo da barra inferior mais claro.
- O botão "+" saiu da Início (lançar continua na aba Movimentações).
- A aba Mais sempre reabre no menu principal.
- Transições mais rápidas.
- Uma despesa criada pelo botão Pagar só pode ser mudada pela própria conta a pagar (desfazendo o pagamento).

### Segurança dos dados
- Migração do banco testada com cópia fiel do banco da v0.2: contas, movimentações, categorias, saldos e histórico ficam idênticos, sem duplicações.
- Backups feitos na v0.2 continuam restauráveis.

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
