# 0019 — Design system v2 e a regra dos 2 destaques

**Estado:** aceita (direção visual mostrada ao dono em 23/11/2026; detalhes decididos no desenvolvimento) · v0.4

## Decisão
- **Evolução, não reescrita:** os tokens da v0.3 continuam; o v2 acrescenta níveis de superfície, raios (cards 20 dp, destaques 24 dp, ícones 12 dp), opacidades, durações e degradês (`ui/theme/DesignV2.kt`).
- **Destaque:** card com degradê #4C1D95 → #1E1B4B, brilho lilás discreto no canto e borda lilás sutil (`NkCardDestaque`). **No máximo 2 destaques por tela.** Listas longas não recebem efeitos.
- **Ícones de categoria e conta** em quadrados arredondados com fundo translúcido na cor (`NkIconeComFundo`). Valores com algarismos tabulares; verde para entradas e rosa para despesas.
- **Valores nunca são cortados:** `NkValorAjustavel` reduz a fonte até caber.
- **Efeitos por versão do Android:**

| Efeito | Todas as versões | Observação |
|---|---|---|
| Brilho lilás | Degradê radial desenhado atrás do card | Mesmo visual do Android 8 ao 17, sem ramificação de código |
| Sombra colorida | `shadow` com cor lilás | O Android 9+ pinta a sombra de lilás; o 8 desenha uma sombra neutra, e a borda lilás mantém o destaque |
| Vidro | Não usado | **Sem a biblioteca Haze** por ora (dependência nova só se fizer falta, com ADR própria) |

- As capturas `v2-vitrine-android-8`, `-9` e `-novo` (Robolectric com SDK 26, 28 e o atual) mostram as alternativas.
