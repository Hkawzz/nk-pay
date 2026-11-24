# 0034 — Backup automático, Início completa e preparação para a Play

**Estado:** aceita (briefing da v0.8: V1, V2, D1, D2, G) · v0.8

## Decisão
- **Backup automático (V2):** WorkManager, numa pasta escolhida uma vez pelo seletor do Android (Drive permitido). Diário (padrão), semanal ou mensal, sem opção de desligar. Guarda os últimos 7 da série automática (arquivos com prefixo próprio); nunca apaga os manuais. Falha avisada na Início; Mais → Backup mostra o último resultado, "Fazer agora" e a orientação sobre a otimização de bateria.
- **Início completa (V1):** quatro blocos que só aparecem com conteúdo (regra 6G.5), calculados a partir das visões que já existem.
- **D1 e D2:** a exclusão protegida sempre responde, com o motivo e o atalho para a tela certa.
- **Play (G):** o `release.yml` gera também o **AAB** assinado com a mesma keystore; Mais → Sobre traz o link da política de privacidade (constante fácil de trocar, com texto provisório) e o e-mail de suporte. Sem botão Apoiar.
- **Ícone:** fica o atual nesta versão (o briefing prevalece sobre o `LEIA-ME-logo.md`); o logo novo entra numa v0.8.x.