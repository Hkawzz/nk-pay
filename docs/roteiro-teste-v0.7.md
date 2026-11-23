# Roteiro de teste da v0.7 no celular (com uma cópia dos seus dados)

**Objetivo:** testar a v0.7 com os seus dados de verdade **sem tocar no app principal**, no **app de debug** ("NK Pay debug", identificador `com.nkpay.app.debug`, [ADR 0007](decisoes/0007-app-de-debug-separado.md)), que tem dados próprios.

> ⚠️ O backup tem os seus dados financeiros. Ele **não sai do celular** e **nunca** vai para o repositório nem para o computador.

Este roteiro cobre **o que mudou na v0.7** (objetivos). O teste de **todo o app**, área por área, está em [`roteiro-teste-profundo.md`](roteiro-teste-profundo.md); os problemas vão para [`registro-de-problemas-v0.8.md`](registro-de-problemas-v0.8.md).

## O que você precisa
Na pasta `Documents\nk-pay-apks` do computador (fora do repositório), estão:
- `nk-pay-v0.6.0-debug.apk`: a v0.6 do app de debug.
- `nk-pay-v0.7.0-debug.apk`: a v0.7 do app de debug.

Os dois foram compilados no mesmo computador, com a mesma chave de debug: um atualiza por cima do outro. Passe os dois para o celular e apague-os de lá ao terminar.

## Parte 1: a migração de verdade, sobre uma cópia
1. No **app principal** (NK Pay v0.6): **Mais → Backup e restauração → Fazer backup agora**.
2. Se ainda houver um "NK Pay debug" antigo no celular, desinstale-o.
3. Instale `nk-pay-v0.6.0-debug.apk`. Confira em **Mais → Sobre** que a versão é `0.6.0-debug`. Se não terminar em **-debug**, você está no app principal: **não restaure nada**.
4. No app de debug: **Restaurar** o backup do passo 1. Confira as contas e os saldos.
5. Instale `nk-pay-v0.7.0-debug.apk` **por cima**. É aqui que o banco migra da versão 5 para a 6, **com os seus dados**. **Mais → Sobre** mostra `0.7.0-debug`.
6. Confira:
   - [ ] Saldo disponível, cada conta, benefícios, cartões, faturas, empréstimos e A pagar **iguais**.
   - [ ] A Início mostra **só** o Saldo disponível (nada guardado ainda).
   - [ ] A aba **Objetivos** mostra "Nenhum objetivo ainda".

## Parte 2: objetivos (use valores pequenos)
Siga a **seção 10** do [roteiro do teste profundo](roteiro-teste-profundo.md#10-objetivos-aba-objetivos--novo-na-v07). Os pontos principais:
- [ ] Guardar diminui o **Saldo disponível**, mas **não** muda o saldo de nenhuma conta, as entradas, as despesas nem o Resumo da Início.
- [ ] Com algo guardado, a Início mostra **Saldo total** e **Guardado**; retirando tudo, volta a mostrar só o Saldo disponível.
- [ ] Guardar mais que o disponível é **recusado**.
- [ ] Para gastar: **retire** do objetivo e **depois** lance a despesa (o "?" explica).
- [ ] Guardado maior que o saldo: disponível **negativo** com aviso; nada é reduzido sozinho.
- [ ] Excluir com valor guardado é recusado; concluir e arquivar mantêm o guardado.
- [ ] Análises → seção **Objetivos**.
- [ ] Backup e restauração levam os objetivos e o histórico.

## Quando estiver tudo certo
1. No **app principal**: faça um backup novo e guarde-o **fora** do celular.
2. Instale `nk-pay-v0.7.0.apk` (da Release, assinado com a sua chave) por cima do app principal.
