# 0018 — Categorias com ícone e cor

**Estado:** aceita (2.5 aprovada em 23/11/2026; detalhes decididos no desenvolvimento) · v0.4

## Decisão
- Colunas `icone` (texto, o nome de um ícone do conjunto do app) e `cor` (ARGB) na tabela `categoria` ([0014](0014-banco-v3.md)).
- **Conjunto próprio de ícones vetoriais** (cerca de 35), desenhados com os caminhos do Material Icons (licença Apache 2.0), em vez do pacote "extended", que aumentaria muito o APK.
- **12 cores predefinidas**, testadas no fundo escuro, e uma cor personalizada (por código hexadecimal). A personalizada é **clareada automaticamente** até ter contraste de pelo menos 3:1 com o fundo #18181B.
- **Aparência sugerida:** um passo do app (`aplicarAparenciaSugerida`) roda a cada abertura e depois de restaurar um backup. Ele só mexe em categorias **sem ícone**: as que têm nome conhecido (comparação sem maiúsculas e sem acentos) recebem a sugestão; as demais recebem um ícone e uma cor neutros. Tudo num grupo do Registro de alterações ("Aparência sugerida aplicada"). Como a interface nunca deixa o ícone vazio, o passo não sobrescreve escolhas e não precisa de controle de "já rodou".
- A categoria de sistema "Encargos financeiros" também recebe ícone e cor, e eles podem ser alterados (o nome continua bloqueado).
- Instalações novas já criam as categorias iniciais com a aparência sugerida.
