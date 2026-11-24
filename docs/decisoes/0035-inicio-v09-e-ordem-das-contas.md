# 0035 — Início da v0.9 e ordem das contas

**Estado:** aceita (briefing da v0.9: itens 1, 2, 3 e 5) · v0.9

## Decisão
- **Item 1:** os blocos Cartões e Caixinhas saem da Início, sempre. Próximos vencimentos e A receber ficam. Nenhum número muda: os blocos só mostravam o que já estava nas telas próprias.
- **Itens 2 e 3:** um único jeito de montar a grade (`linhasDaGrade`, até N por linha). Cada linha divide a largura toda entre os seus cards, então a linha que sobra fica inteira (contas: até 2 por linha; benefícios: até 3). O limite de cards antes de "Ver todas" continua o mesmo.
- **Item 5:** coluna `conta.ordem` (banco 8). A lista de contas é ordenada por `arquivada`, depois `ordem`, depois nome. Ordem 0 quer dizer "ainda não reordenada": a migração deixa todas em 0, e o desempate pelo nome reproduz exatamente a ordem de hoje. Ao salvar uma ordem, as posições vão de 1 em diante; a partir daí, conta nova entra no fim. Sem nenhuma ordem escolhida, conta nova continua entrando na posição alfabética, como antes. Contas e benefícios são reordenados na mesma tela.
- **Arrastar sem biblioteca:** a tela "Editar ordem" tem uma alça para arrastar (gesto do próprio Compose) e setas para subir e descer. Preferi não acrescentar dependência para uma lista curta; as setas também servem para acessibilidade e para os testes. Nada é gravado antes de "Salvar ordem".
- **Auditoria:** cada conta que mudou de posição gera um registro, todos no mesmo grupo.
- **Backup:** a ordem vai junto (é uma coluna da conta). Backups antigos chegam sem ordem (todas 0), e o desempate pelo nome reproduz a ordem que tinham.
