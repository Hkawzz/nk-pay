# Roteiro de teste da v0.8 no celular (com uma cópia dos seus dados)

**Objetivo:** testar a v0.8 com os seus dados de verdade **sem tocar no app principal**, no **app de debug** ("NKote debug", identificador `com.hkawzz.nkote.debug`, [ADR 0007](decisoes/0007-app-de-debug-separado.md)), que tem dados próprios.

> ⚠️ O backup tem os seus dados financeiros. Ele **não sai do celular** e **nunca** vai para o repositório nem para o computador.

Este roteiro cobre **o que mudou na v0.8**. O teste de **todo o app**, área por área, está em [`roteiro-teste-profundo.md`](roteiro-teste-profundo.md); os problemas vão para [`registro-de-problemas-v0.9.md`](registro-de-problemas-v0.9.md).

## O que muda de diferente nesta versão: o app tem nome e identificador novos
A v0.8 se chama **NKote** e tem outro identificador (`com.hkawzz.nkote`, [ADR 0030](decisoes/0030-renomeacao-nkote.md)). Para o Android, é **outro app**: ele **não** instala por cima do NK Pay e começa vazio. Os dados passam de um para o outro **pelo backup**. Nada é apagado do NK Pay até você desinstalá-lo, e isso só deve ser feito no fim, depois de conferir tudo.

## O que você precisa
Na pasta local de APKs do computador (fora do repositório), estão:
- `nk-pay-v0.7.0-debug.apk`: a v0.7 do app de debug antigo ("NK Pay debug").
- `nkote-v0.8.0-debug.apk`: a v0.8 do app de debug novo ("NKote debug").

Os dois podem ficar instalados ao mesmo tempo (identificadores diferentes). Passe-os para o celular e apague-os de lá ao terminar.

## Parte 1: a migração de verdade, sobre uma cópia
1. No **app principal** (NK Pay v0.7): **Mais → Backup e restauração → Fazer backup agora**. O arquivo termina em `.nkpay`.
2. Instale `nkote-v0.8.0-debug.apk`. Ele aparece como **"NKote debug"**. Confira em **Mais → Sobre** que a versão é `0.8.0-debug`. Se não terminar em **-debug**, você está no app principal: **não restaure nada**.
3. No NKote debug: **Mais → Backup → Restaurar de um arquivo** → escolha o `.nkpay` do passo 1. É aqui que o banco migra da versão 6 para a 7, **com os seus dados**.
4. Confira:
   - [ ] Saldo total, cada conta, benefícios, cartões, faturas, empréstimos e A pagar **iguais** aos do NK Pay.
   - [ ] Se você tinha objetivos na v0.7: aparece **uma vez** a mensagem de que eles viraram **caixinhas**, começando em R$ 0,00 (o dinheiro continua nas contas); o histórico antigo fica visível, só para leitura. Toque em "Entendi": ela não volta.
   - [ ] Nenhuma caixinha aparece como conta para pagar ou lançar despesa.

## Parte 2: o que é novo (use valores pequenos)
Siga as seções marcadas **v0.8** do [roteiro do teste profundo](roteiro-teste-profundo.md). Os pontos principais:
- [ ] **Sem cheque especial:** uma despesa maior que o saldo do Pix é **recusada** ("Saldo insuficiente no Pix…"). Vale também para transferências, pagar fatura, pagar conta, pagar parcela de empréstimo e guardar.
- [ ] **Caixinhas:** guardar tira da conta e põe na caixinha (é uma transferência); retirar devolve; o **Saldo total não muda** ao guardar; a Início mostra **Guardado**; rendimento é uma **entrada** (categoria Rendimentos).
- [ ] **Excluir protegido:** excluir pela aba Movimentações um pagamento de fatura, de empréstimo ou um guardar mostra o motivo e o botão para abrir a tela certa.
- [ ] **Compra com pagamento:** tentar excluir mostra a lista de pagamentos, cada um com "Desfazer".
- [ ] **Início completa:** Próximos vencimentos, Cartões, Caixinhas e A receber, só quando têm conteúdo.
- [ ] **Backup automático:** Mais → Backup → escolha uma pasta (pode ser do Drive) → **Fazer agora** → um arquivo `nkote-auto-….nkote` aparece na pasta e a tela mostra "deu certo". No dia seguinte, deve haver outro.
- [ ] **Mais → Sobre:** nome NKote, política de privacidade (texto provisório) e o e-mail de suporte.

## Quando estiver tudo certo (no app principal)
1. No **NK Pay** (principal): faça um backup novo e guarde-o **fora** do celular.
2. Instale `nkote-v0.8.0.apk` (da Release, assinado com a sua chave). Ele aparece como **"NKote"**, ao lado do NK Pay.
3. No NKote: **Mais → Backup → Restaurar de um arquivo** → escolha o `.nkpay` do passo 1.
4. **Confira** saldos, contas, cartões, empréstimos e A pagar com o NK Pay aberto ao lado.
5. Escolha a pasta do **backup automático** no NKote.
6. Só então **desinstale o NK Pay** (e o NK Pay debug, se ainda estiver lá).
