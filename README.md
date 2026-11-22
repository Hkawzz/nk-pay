# NK Pay

App Android de finanças pessoais, feito para uso de uma única pessoa.

- **100% offline:** não usa internet, não tem login e não tem servidor. Os dados ficam só no celular.
- **Feito para substituir a planilha mensal:** contas e carteiras, movimentações (entrada, despesa, transferência e ajuste), categorias e, nas próximas versões, contas a pagar, cartões, parcelamentos e objetivos.
- **Regras financeiras explícitas:** estão em [`docs/regras-financeiras.md`](docs/regras-financeiras.md). As decisões técnicas, cada uma com o seu porquê, estão em [`docs/decisoes/`](docs/decisoes/).

> Estado atual: **v0.2.0**. Tem contas e carteiras (dinheiro próprio e benefícios), categorias, os 4 tipos de movimentação, a tela Início com o Disponível, o Registro de alterações e **backup e restauração** (Mais → Backup e restauração). O histórico de versões está em [`CHANGELOG.md`](CHANGELOG.md).
>
> **Faça backups:** os dados ficam só no celular. Salve o arquivo `.nkpay` em um lugar fora dele (por exemplo, o Google Drive). Para trocar de celular, instale o app no novo e use "Restaurar de um arquivo".

---

## Instalar no celular (passo a passo)

O app não está na Play Store. Cada versão é publicada como um arquivo **APK** na página de *Releases* deste repositório. Requer **Android 8 ou mais novo**.

### Primeira instalação

1. **No celular**, abra o navegador (Chrome, por exemplo) e entre em <https://github.com>. **Faça login** na sua conta: o repositório é privado, então sem login a página aparece como "não encontrada".
2. Abra <https://github.com/Hkawzz/nk-pay/releases>. A versão mais nova fica no topo.
3. Em **Assets**, toque em `nk-pay-vX.Y.Z.apk` (por exemplo, `nk-pay-v0.0.1.apk`). O download começa.
   - Se o Chrome avisar que o arquivo "pode ser perigoso", toque em **Baixar mesmo assim**. O aviso aparece para todo APK baixado fora da Play Store.
4. Quando o download terminar, toque em **Abrir** na notificação (ou abra o app **Arquivos** → **Downloads** e toque no APK).
5. Na primeira vez, o Android bloqueia a instalação e mostra **"Por segurança, seu telefone não pode instalar apps desconhecidos desta fonte"**:
   - Toque em **Configurações**.
   - Ative **Permitir desta fonte** para o app que você está usando (Chrome ou Arquivos).
   - Volte (seta ←) e toque em **Instalar**.
6. Se aparecer o **Google Play Protect** dizendo que o app é desconhecido ou não foi verificado:
   - Toque em **Mais detalhes** → **Instalar mesmo assim** (os nomes mudam um pouco de aparelho para aparelho).
   - Se ele oferecer **"Enviar o app para verificação"**, você pode recusar.
   - Isso é esperado: o app é assinado por você, e não por um desenvolvedor da Play Store.
7. Pronto: o **NK Pay** aparece na lista de apps.

> Dica de segurança: depois de instalar, você pode desligar de novo o "Permitir desta fonte". Na próxima atualização, o Android pede a permissão outra vez.

### Atualizar para uma versão nova

Repita os passos 2 a 6 com o APK novo e **instale por cima**. **Não desinstale antes:** desinstalar apaga todos os dados do app. Instalando por cima, os dados são mantidos.

Isso só funciona porque todas as versões são assinadas com a **mesma chave** (a keystore de release). Ver [`docs/decisoes/0003-assinatura-e-releases.md`](docs/decisoes/0003-assinatura-e-releases.md).

Se o Android disser **"App não instalado: o pacote entra em conflito com um pacote existente"**, o APK foi assinado com outra chave. Não desinstale: algo deu errado na Release, e é preciso investigar antes.

---

## Compilar (para desenvolvimento)

A fonte oficial dos APKs é o GitHub Actions (ver abaixo). Compilar localmente é opcional.

Requisitos: JDK 21 e Android SDK (plataforma `android-37.2`), com as variáveis `JAVA_HOME` e `ANDROID_HOME` configuradas.

```powershell
# Todos os testes (domínio, banco e interface), sem celular
.\gradlew.bat testDebugUnitTest

# Os mesmos testes, salvando capturas de tela em app/build/outputs/roborazzi/
.\gradlew.bat testDebugUnitTest -Proborazzi.test.record=true

# APK de desenvolvimento (app/build/outputs/apk/debug/)
.\gradlew.bat assembleDebug
```

Em cada PR, o CI publica as capturas de tela como o artefato **capturas-de-tela**: abra a execução em *Actions* e role até *Artifacts*.

O APK de debug tem outro identificador (`com.nkpay.app.debug`) e se chama "NK Pay" também. Ele é instalado **ao lado** do app oficial, nunca por cima, para que os testes não toquem nos dados reais.

### Como uma versão é publicada

1. O trabalho é feito em uma branch (`feat/...`) e entra na `main` por Pull Request. O workflow `ci.yml` compila e roda os testes em todo PR e em todo push na `main`.
2. Para publicar, atualiza-se o `CHANGELOG.md` e cria-se uma tag `vX.Y.Z` na `main`.
3. O workflow `release.yml` compila o APK de release, assina com a keystore guardada nos GitHub Secrets e cria a Release com o arquivo `nk-pay-vX.Y.Z.apk` e as notas do `CHANGELOG.md`.

---

## Estrutura

```
app/src/main/java/com/nkpay/app/
  dominio/      Regras financeiras em Kotlin puro (sem Android). É o código mais testado.
  dados/        Banco (Room): tabelas, consultas e repositórios. Toda alteração grava a auditoria junto.
  ui/           Telas em Jetpack Compose, tema e navegação.
  AppContainer  Cria o banco e os repositórios uma vez (injeção de dependência manual).
app/schemas/    Esquema de cada versão do banco. É a base das migrações: não apague.
docs/
  regras-financeiras.md   As regras, com exemplos numéricos.
  decisoes/               Uma decisão por arquivo: contexto, decisão e consequências.
```

## Privacidade

Nenhuma planilha, banco de dados ou backup real entra neste repositório (o `.gitignore` bloqueia esses formatos). Os testes usam só dados fictícios.
