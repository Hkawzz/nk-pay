package com.hkawzz.nkote.dados

import androidx.room.withTransaction
import com.hkawzz.nkote.dominio.CategoriasDoSistema
import com.hkawzz.nkote.dominio.ClassificacaoDiferenca
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.dominio.NaturezaAjuste
import com.hkawzz.nkote.dominio.ParteDoPagamento
import com.hkawzz.nkote.dominio.Pagamentos
import com.hkawzz.nkote.dominio.ResultadoPlano
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.dominio.VisaoCredito
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pagamento em MODO CRÉDITO (v0.4, ADR 0015). Separado do `PagamentoServico` das contas
 * fixas, que não muda. Aqui, pagar é sempre uma TRANSFERÊNCIA (conta própria → conta de
 * crédito), nunca uma despesa: a despesa já foi contada no mês da compra.
 *
 * - Desconto (crediário): AJUSTE positivo na conta de crédito + abatimento na parcela.
 * - Pagou a mais (8.1-a): DESPESA de encargo na conta de crédito, em "Encargos
 *   financeiros", + acréscimo na parcela. Só com a classificação da pessoa.
 * - Desfazer: exclui (logicamente) a transferência e tudo o que ela criou.
 *
 * Tudo em uma transação e em um grupo do Registro de alterações.
 * @return sempre o id da transferência (é por ela que o pagamento é desfeito).
 */
class PagamentoCreditoServico(
    private val banco: NkPayBanco,
    private val auditor: Auditor,
    private val relogio: Relogio,
) {
    private val contaDao = banco.contaDao()
    private val movDao = banco.movimentacaoDao()
    private val obrigacaoDao = banco.obrigacaoDao()
    private val pagamentoDao = banco.pagamentoDao()
    private val ajusteDao = banco.ajusteObrigacaoDao()
    private val faturaDao = banco.pagamentoFaturaDao()
    private val categoriaDao = banco.categoriaDao()

    /** A visão de crédito de UMA conta, lida do banco dentro da transação. */
    private suspend fun visao(contaCreditoId: String): VisaoCredito {
        val contas = banco.backupDao().contas().filter { it.excluidoEm == null }.map { it.paraDominio() }
        val movs = banco.backupDao().movimentacoes().filter { it.excluidoEm == null && (it.contaId == contaCreditoId || it.contaDestinoId == contaCreditoId) }.map { it.paraDominio() }
        val parcelas = obrigacaoDao.parcelasDaConta(contaCreditoId).map { it.paraDominio() }
        val ids = parcelas.map { it.id }.toSet()
        return VisaoCredito(
            contas, movs, parcelas,
            pagamentoDao.validosTodos().filter { it.obrigacaoId in ids }.map { it.paraDominio() },
            ajusteDao.validosTodos().filter { it.obrigacaoId in ids }.map { it.paraDominio() },
            faturaDao.validosDaConta(contaCreditoId).map { it.paraDominio() },
            // v0.6: as compras dizem a parte da compra e os juros de cada parcela (6B.8).
            banco.backupDao().compras().filter { it.excluidoEm == null && it.contaCreditoId == contaCreditoId }.map { it.paraDominio() },
        )
    }

    private companion object {
        const val SEM_ENCARGOS = "Categoria “Encargos financeiros” não encontrada."
    }

    private class Contexto(val grupo: String, val agora: Long, val data: LocalDate)

    private suspend fun contaOrigemValida(id: String): ContaEntidade? =
        contaDao.buscar(id)?.takeIf { it.excluidoEm == null && !Natureza.valueOf(it.natureza).ehContaDeTerceiros }

    private suspend fun Contexto.mov(tipo: TipoMovimentacao, valor: Long, contaId: String, destino: String?, categoriaId: String?, descricao: String, resumo: String): MovimentacaoEntidade {
        val m = MovimentacaoEntidade(relogio.novoId(), tipo.name, data.toString(), valor, contaId, destino, categoriaId, descricao, agora, agora, null)
        movDao.inserir(m)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.MOVIMENTACAO, m.id, resumo, MovimentacaoEntidade.serializer(), null, m)
        return m
    }

    private suspend fun Contexto.ligar(obrigacao: ObrigacaoEntidade, movId: String, valor: Long): PagamentoEntidade {
        val p = PagamentoEntidade(relogio.novoId(), obrigacao.id, movId, valor, agora, agora, null)
        pagamentoDao.inserir(p)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.PAGAMENTO, p.id, "Pagamento de ${Dinheiro.formatar(valor)} em “${obrigacao.descricao}”", PagamentoEntidade.serializer(), null, p)
        return p
    }

    private suspend fun Contexto.ajustar(obrigacao: ObrigacaoEntidade, pagamentoId: String, natureza: NaturezaAjuste, valor: Long, movId: String) {
        val a = ObrigacaoAjusteEntidade(relogio.novoId(), obrigacao.id, pagamentoId, natureza.name, valor, movId, "", agora, agora, null)
        ajusteDao.inserir(a)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.AJUSTE_OBRIGACAO, a.id, "${natureza.rotulo} de ${Dinheiro.formatar(valor)} em “${obrigacao.descricao}”", ObrigacaoAjusteEntidade.serializer(), null, a)
    }

    /** Despesa de encargo na conta de crédito + acréscimo ligado ao pagamento. */
    private suspend fun Contexto.encargo(credito: ContaEntidade, obrigacao: ObrigacaoEntidade, pagamentoId: String, classificacao: ClassificacaoDiferenca, valor: Long): Resultado.Falha? {
        val categoria = categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) ?: return Resultado.Falha(SEM_ENCARGOS)
        val natureza = classificacao.natureza!!
        val e = mov(TipoMovimentacao.DESPESA, valor, credito.id, null, categoria.id, "${natureza.rotulo}: ${credito.nome}",
            "Despesa de ${Dinheiro.formatar(valor)} de ${natureza.rotulo.lowercase()} no ${credito.nome}")
        ajustar(obrigacao, pagamentoId, natureza, valor, e.id)
        return null
    }

    /**
     * v0.6 (regra 6B.8, D-A1 c): os juros do parcelamento que este pagamento resolve viram
     * despesa em "Encargos financeiros", na data do pagamento, na conta de crédito, e um
     * ajuste JUROS_DO_PARCELAMENTO (não muda o restante) liga a despesa à parcela. No
     * pagamento de uma parcela, o ajuste aponta para a ligação; no abatimento geral da
     * fatura, não há ligação e a [observacao] guarda "geral:<transferência>" para o desfazer.
     */
    private suspend fun Contexto.jurosDoParcelamento(
        credito: ContaEntidade, obrigacao: ObrigacaoEntidade, pagamentoId: String?, valor: Long, observacao: String = "",
    ): Resultado.Falha? {
        if (valor <= 0) return null
        val categoria = categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) ?: return Resultado.Falha(SEM_ENCARGOS)
        val e = mov(TipoMovimentacao.DESPESA, valor, credito.id, null, categoria.id, "Juros do parcelamento: ${obrigacao.descricao}",
            "Despesa de ${Dinheiro.formatar(valor)} de juros do parcelamento (“${obrigacao.descricao}”) no ${credito.nome}")
        val a = ObrigacaoAjusteEntidade(relogio.novoId(), obrigacao.id, pagamentoId, NaturezaAjuste.JUROS_DO_PARCELAMENTO.name, valor, e.id, observacao, agora, agora, null)
        ajusteDao.inserir(a)
        auditor.registrar(grupo, Operacao.CRIACAO, Auditor.AJUSTE_OBRIGACAO, a.id,
            "Juros do parcelamento de ${Dinheiro.formatar(valor)} pagos em “${obrigacao.descricao}”", ObrigacaoAjusteEntidade.serializer(), null, a)
        return null
    }

    private fun jurosDoValor(aplicado: Long, restante: Long, jurosPendentes: Long): Long = VisaoCredito.jurosDoPagamento(aplicado, restante, jurosPendentes)

    // ------------------------------------------------------------------ parcela (crediário)

    /** Paga UMA parcela (crediário), com as perguntas da v0.3 (parcial, desconto, encargos). */
    suspend fun pagarParcela(
        obrigacaoId: String, contaOrigemId: String, data: LocalDate, valorPago: Long, classificacao: ClassificacaoDiferenca?,
    ): Resultado<String> = banco.transacaoConferida {
        val o = obrigacaoDao.buscar(obrigacaoId)?.takeIf { it.excluidoEm == null && it.contaCreditoId != null }
            ?: return@transacaoConferida Resultado.Falha("Parcela não encontrada.")
        val credito = contaDao.buscar(o.contaCreditoId!!)!!
        if (Natureza.valueOf(credito.natureza) == Natureza.CARTAO) return@transacaoConferida Resultado.Falha("Parcelas de cartão são pagas pela fatura.")
        // v0.5: empréstimo concedido não se "paga": recebe-se (receberParcela, com os juros K2).
        if (Natureza.valueOf(credito.natureza) == Natureza.EMPRESTIMO_CONCEDIDO) return@transacaoConferida Resultado.Falha("Empréstimo concedido: use Receber.")
        val origem = contaOrigemValida(contaOrigemId) ?: return@transacaoConferida Resultado.Falha("Escolha a conta de onde sai o dinheiro.")
        val situacao = visao(credito.id).situacaoDaParcela(o.id, relogio.hoje())
        val restante = situacao?.restante ?: 0
        val jurosPendentes = situacao?.jurosPendentes ?: 0
        val plano = when (val r = Pagamentos.planejar(restante, valorPago, classificacao)) {
            is ResultadoPlano.Pronto -> r.plano
            is ResultadoPlano.PrecisaClassificar -> return@transacaoConferida Resultado.Falha("Diga o que é a diferença de ${Dinheiro.formatar(r.diferenca)}.")
            is ResultadoPlano.Invalido -> return@transacaoConferida Resultado.Falha(r.mensagem)
        }
        // Confere ANTES de gravar qualquer coisa (uma falha no meio não pode deixar meio pagamento).
        // v0.6 (6B.8): o desconto consome primeiro os juros pendentes; o resto dos juros vira despesa.
        val descontoNosJuros = minOf(plano.abatimento, jurosPendentes)
        val juros = VisaoCredito.jurosDoPagamento(plano.totalPago - plano.encargo, restante, jurosPendentes, plano.abatimento)
        if ((plano.encargo > 0 || juros > 0) && categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) == null) {
            return@transacaoConferida Resultado.Falha(SEM_ENCARGOS)
        }
        val c = Contexto(relogio.novoId(), relogio.agora(), data)
        with(c) {
            val t = mov(TipoMovimentacao.TRANSFERENCIA, plano.totalPago, origem.id, credito.id, null, "Pagamento: ${o.descricao}",
                "Transferência de ${Dinheiro.formatar(plano.totalPago)} ${origem.nome} → ${credito.nome} (pagamento de “${o.descricao}”)")
            val p = ligar(o, t.id, plano.totalPago)
            if (plano.encargo > 0) encargo(credito, o, p.id, plano.classificacao!!, plano.encargo)?.let { return@transacaoConferida it }
            jurosDoParcelamento(credito, o, p.id, juros)?.let { return@transacaoConferida it }
            if (plano.abatimento > 0) {
                val natureza = plano.classificacao!!.natureza!!
                // Só a parte do desconto que passa dos juros mexe no devido (os juros não estão nele).
                val noDevido = plano.abatimento - descontoNosJuros
                val d = if (noDevido > 0) mov(TipoMovimentacao.AJUSTE, noDevido, credito.id, null, null, "${natureza.rotulo}: ${o.descricao}",
                    "Ajuste de ${Dinheiro.formatarComSinal(noDevido)} no ${credito.nome} (${natureza.rotulo.lowercase()})") else null
                if (d != null && descontoNosJuros == 0L) ajustar(o, p.id, natureza, plano.abatimento, d.id)
                else {
                    val a = ObrigacaoAjusteEntidade(relogio.novoId(), o.id, p.id, natureza.name, plano.abatimento, d?.id, "juros:$descontoNosJuros", agora, agora, null)
                    ajusteDao.inserir(a)
                    auditor.registrar(grupo, Operacao.CRIACAO, Auditor.AJUSTE_OBRIGACAO, a.id,
                        "${natureza.rotulo} de ${Dinheiro.formatar(plano.abatimento)} em “${o.descricao}” (${Dinheiro.formatar(descontoNosJuros)} nos juros)", ObrigacaoAjusteEntidade.serializer(), null, a)
                }
            }
            Resultado.Sucesso(t.id)
        }
    }

    // ------------------------------------------------------------------ fatura (cartão)

    /**
     * Paga [valor] no cartão: cobre as faturas em aberto até [ateMes], da mais antiga para
     * a mais nova (8.5). Fatura coberta inteira → ligações com as parcelas; coberta em parte
     * → abatimento geral. O que passar do total em aberto precisa de [classificacao] (encargo).
     */
    suspend fun pagarFatura(
        contaCreditoId: String, ateMes: YearMonth, contaOrigemId: String, data: LocalDate, valor: Long, classificacao: ClassificacaoDiferenca?,
    ): Resultado<String> = banco.transacaoConferida {
        if (valor <= 0) return@transacaoConferida Resultado.Falha("O valor pago precisa ser maior que zero.")
        val credito = contaDao.buscar(contaCreditoId)?.takeIf { it.excluidoEm == null && it.natureza == Natureza.CARTAO.name }
            ?: return@transacaoConferida Resultado.Falha("Cartão não encontrado.")
        val origem = contaOrigemValida(contaOrigemId) ?: return@transacaoConferida Resultado.Falha("Escolha a conta de onde sai o dinheiro.")
        val vc = visao(credito.id)
        val dist = vc.distribuir(credito.id, valor, ateMes, relogio.hoje())
        // v0.6 (6B.8): os juros que cada parte resolve. Fatura inteira: todos os pendentes dela,
        // parcela a parcela; abatimento geral: proporcionais ao restante da fatura.
        val faturas = vc.faturas(credito.id, relogio.hoje()).associateBy { it.mes }
        val jurosPorParte = dist.partes.associateWith { parte ->
            val f = faturas.getValue(parte.mes)
            val total = when (parte) {
                is ParteDoPagamento.FaturaInteira -> f.jurosPendentes
                is ParteDoPagamento.AbatimentoGeral -> jurosDoValor(parte.valor, f.restante, f.jurosPendentes)
            }
            var resto = total
            f.parcelas.mapNotNull { sp -> minOf(resto, sp.jurosPendentes).takeIf { it > 0 }?.let { j -> resto -= j; sp.obrigacao.id to j } }.toMap()
        }
        if (dist.partes.isEmpty()) return@transacaoConferida Resultado.Falha("Não há fatura em aberto até ${ateMes}.")
        val classificacaoValida = classificacao?.takeIf { it in ClassificacaoDiferenca.quandoPagaMais }
        if (dist.excedente > 0 && classificacaoValida == null) {
            return@transacaoConferida Resultado.Falha("Diga o que é a diferença de ${Dinheiro.formatar(dist.excedente)}.")
        }
        if ((dist.excedente > 0 || jurosPorParte.values.any { it.isNotEmpty() }) && categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) == null) {
            return@transacaoConferida Resultado.Falha(SEM_ENCARGOS)
        }
        val c = Contexto(relogio.novoId(), relogio.agora(), data)
        with(c) {
            val t = mov(TipoMovimentacao.TRANSFERENCIA, valor, origem.id, credito.id, null, "Fatura: ${credito.nome}",
                "Transferência de ${Dinheiro.formatar(valor)} ${origem.nome} → ${credito.nome} (pagamento de fatura)")
            // O excedente fica com a 1ª parcela da ÚLTIMA fatura coberta inteira.
            val ultimaInteira = dist.partes.filterIsInstance<ParteDoPagamento.FaturaInteira>().lastOrNull()
            for (parte in dist.partes) when (parte) {
                is ParteDoPagamento.FaturaInteira -> parte.aplicacoes.forEachIndexed { i, (obrigacaoId, aplicado) ->
                    val o = obrigacaoDao.buscar(obrigacaoId)!!
                    val comExcedente = dist.excedente > 0 && parte === ultimaInteira && i == 0
                    val p = ligar(o, t.id, aplicado + if (comExcedente) dist.excedente else 0)
                    if (comExcedente) encargo(credito, o, p.id, classificacaoValida!!, dist.excedente)?.let { return@transacaoConferida it }
                }.also {
                    // Juros de parcelas da fatura que ficaram sem ligação (já cobertas por abatimentos gerais).
                    for ((obrigacaoId, j) in jurosPorParte.getValue(parte)) {
                        val ligada = parte.aplicacoes.any { it.first == obrigacaoId }
                        val o = obrigacaoDao.buscar(obrigacaoId)!!
                        val ligacao = if (ligada) pagamentoDao.daMovimentacao(t.id).first { it.obrigacaoId == obrigacaoId }.id else null
                        jurosDoParcelamento(credito, o, ligacao, j, if (ligada) "" else "geral:${t.id}")?.let { return@transacaoConferida it }
                    }
                }
                is ParteDoPagamento.AbatimentoGeral -> {
                    val g = PagamentoFaturaEntidade(relogio.novoId(), credito.id, parte.mes.toString(), t.id, parte.valor, agora, agora, null)
                    faturaDao.inserir(g)
                    auditor.registrar(grupo, Operacao.CRIACAO, Auditor.PAGAMENTO_FATURA, g.id,
                        "Abatimento de ${Dinheiro.formatar(parte.valor)} na fatura de ${parte.mes} do ${credito.nome}", PagamentoFaturaEntidade.serializer(), null, g)
                    for ((obrigacaoId, j) in jurosPorParte.getValue(parte)) {
                        jurosDoParcelamento(credito, obrigacaoDao.buscar(obrigacaoId)!!, null, j, "geral:${t.id}")?.let { return@transacaoConferida it }
                    }
                }
            }
            Resultado.Sucesso(t.id)
        }
    }

    /** Paga só as compras escolhidas de uma fatura: o restante exato de cada parcela. */
    suspend fun pagarCompras(
        contaCreditoId: String, mes: YearMonth, parcelas: Collection<String>, contaOrigemId: String, data: LocalDate,
    ): Resultado<String> = banco.transacaoConferida {
        if (parcelas.isEmpty()) return@transacaoConferida Resultado.Falha("Escolha pelo menos uma compra.")
        val credito = contaDao.buscar(contaCreditoId)?.takeIf { it.excluidoEm == null && it.natureza == Natureza.CARTAO.name }
            ?: return@transacaoConferida Resultado.Falha("Cartão não encontrado.")
        val origem = contaOrigemValida(contaOrigemId) ?: return@transacaoConferida Resultado.Falha("Escolha a conta de onde sai o dinheiro.")
        val v = visao(credito.id)
        val fatura = v.fatura(credito.id, mes, relogio.hoje()) ?: return@transacaoConferida Resultado.Falha("Fatura não encontrada.")
        val escolhidas = fatura.parcelas.filter { it.obrigacao.id in parcelas && it.restante > 0 }
        if (escolhidas.size != parcelas.toSet().size) return@transacaoConferida Resultado.Falha("Alguma compra escolhida já está paga ou não é desta fatura.")
        val total = escolhidas.sumOf { it.restante }
        if (escolhidas.any { it.jurosPendentes > 0 } && categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) == null) {
            return@transacaoConferida Resultado.Falha(SEM_ENCARGOS)
        }
        if (total > fatura.restante) {
            return@transacaoConferida Resultado.Falha("A fatura já tem ${Dinheiro.formatar(fatura.abatimentosGerais)} abatidos. Pague o restante da fatura (${Dinheiro.formatar(fatura.restante)}).")
        }
        val c = Contexto(relogio.novoId(), relogio.agora(), data)
        with(c) {
            val t = mov(TipoMovimentacao.TRANSFERENCIA, total, origem.id, credito.id, null, "Fatura: ${credito.nome}",
                "Transferência de ${Dinheiro.formatar(total)} ${origem.nome} → ${credito.nome} (compras escolhidas)")
            for (s in escolhidas) {
                val o = obrigacaoDao.buscar(s.obrigacao.id)!!
                val p = ligar(o, t.id, s.restante)
                jurosDoParcelamento(credito, o, p.id, s.jurosPendentes)?.let { return@transacaoConferida it }
            }
            Resultado.Sucesso(t.id)
        }
    }

    // ------------------------------------------------------------------ empréstimos (v0.5)

    /** A visão de UM empréstimo, lida do banco dentro da transação. */
    private suspend fun visaoEmprestimo(emprestimoId: String): com.hkawzz.nkote.dominio.VisaoEmprestimos? {
        val e = banco.emprestimoDao().buscar(emprestimoId)?.takeIf { it.excluidoEm == null } ?: return null
        val parcelas = obrigacaoDao.doEmprestimo(emprestimoId).map { it.paraDominio() }
        val ids = parcelas.map { it.id }.toSet()
        return com.hkawzz.nkote.dominio.VisaoEmprestimos(
            banco.backupDao().contas().filter { it.excluidoEm == null }.map { it.paraDominio() },
            banco.backupDao().movimentacoes().filter { it.excluidoEm == null }.map { it.paraDominio() },
            listOf(e.paraDominio()), parcelas,
            pagamentoDao.validosTodos().filter { it.obrigacaoId in ids }.map { it.paraDominio() },
            ajusteDao.validosTodos().filter { it.obrigacaoId in ids }.map { it.paraDominio() },
        )
    }

    /** A categoria "Juros recebidos": existe ou é criada aqui, com auditoria (regra 6E.5). */
    private suspend fun Contexto.categoriaJurosRecebidos(): String =
        categoriaDao.buscarPorCodigo(CategoriasDoSistema.JUROS_RECEBIDOS)?.id ?: run {
            val a = com.hkawzz.nkote.dominio.AparenciaSugerida.ouNeutra("Juros recebidos")
            val c = CategoriaEntidade(relogio.novoId(), "Juros recebidos", CategoriasDoSistema.JUROS_RECEBIDOS, null, agora, agora, null, a.icone, a.cor)
            categoriaDao.inserir(c)
            auditor.registrar(grupo, Operacao.CRIACAO, Auditor.CATEGORIA, c.id, "Categoria de sistema “Juros recebidos” criada", CategoriaEntidade.serializer(), null, c)
            c.id
        }

    /**
     * Recebe [valor] numa parcela de empréstimo CONCEDIDO (K2, regras 6E.5 e 6E.6):
     * - a parte até o restante da parcela é dividida em principal (transferência pessoa →
     *   conta própria) e juros (entrada em "Juros recebidos"), na proporção dos juros não
     *   reconhecidos do empréstimo; o recebimento que quita o empréstimo fecha os juros;
     * - recebeu a mais (juros, multa, outro): o excedente é outra entrada em "Juros recebidos"
     *   + acréscimo na parcela;
     * - recebeu a menos com desconto concedido: abatimento sem despesa; consome primeiro os
     *   juros não reconhecidos, e só o que passar reduz o principal (ajuste na conta da pessoa).
     * Uma ligação (`pagamento`) com o valor aplicado inteiro; os juros ficam num ajuste
     * JUROS_RECEBIDOS (não muda o restante), para o desfazer remover tudo junto (ADR 0021).
     * @return o id da movimentação principal (é por ela que se desfaz).
     */
    suspend fun receberParcela(
        obrigacaoId: String, contaDestinoId: String, data: LocalDate, valor: Long, classificacao: ClassificacaoDiferenca?,
    ): Resultado<String> = banco.transacaoConferida {
        val o = obrigacaoDao.buscar(obrigacaoId)?.takeIf { it.excluidoEm == null && it.emprestimoId != null }
            ?: return@transacaoConferida Resultado.Falha("Parcela não encontrada.")
        val pessoa = contaDao.buscar(o.contaCreditoId!!)!!
        if (pessoa.natureza != Natureza.EMPRESTIMO_CONCEDIDO.name) return@transacaoConferida Resultado.Falha("Só se recebe parcela de empréstimo concedido.")
        val destino = contaOrigemValida(contaDestinoId) ?: return@transacaoConferida Resultado.Falha("Escolha a conta onde o dinheiro entrou.")
        val v = visaoEmprestimo(o.emprestimoId!!) ?: return@transacaoConferida Resultado.Falha("Empréstimo não encontrado.")
        val s = v.situacao(o.emprestimoId, relogio.hoje())!!
        val restanteParcela = s.parcelas.first { it.obrigacao.id == o.id }.restante
        val permitidas = setOf(ClassificacaoDiferenca.PARCIAL, ClassificacaoDiferenca.DESCONTO, ClassificacaoDiferenca.JUROS, ClassificacaoDiferenca.MULTA, ClassificacaoDiferenca.OUTRO_ACRESCIMO)
        if (classificacao != null && classificacao !in permitidas) return@transacaoConferida Resultado.Falha("Escolha parcial, desconto, juros, multa ou outro.")
        val plano = when (val r = Pagamentos.planejar(restanteParcela, valor, classificacao)) {
            is ResultadoPlano.Pronto -> r.plano
            is ResultadoPlano.PrecisaClassificar -> return@transacaoConferida Resultado.Falha("Diga o que é a diferença de ${Dinheiro.formatar(r.diferenca)}.")
            is ResultadoPlano.Invalido -> return@transacaoConferida Resultado.Falha(r.mensagem)
        }
        val recebido = plano.despesaPrincipal // a parte até o restante da parcela
        val juros = com.hkawzz.nkote.dominio.Emprestimos.jurosDoRecebimento(recebido, s.restante, s.jurosNaoReconhecidos)
        val principal = recebido - juros
        val c = Contexto(relogio.novoId(), relogio.agora(), data)
        with(c) {
            val catJuros = if (juros > 0 || plano.encargo > 0) categoriaJurosRecebidos() else null
            // Movimentação principal: a transferência do principal (ou, se tudo for juros, a entrada de juros).
            val t = if (principal > 0) mov(TipoMovimentacao.TRANSFERENCIA, principal, pessoa.id, destino.id, null, "Recebido: ${o.descricao}",
                "Transferência de ${Dinheiro.formatar(principal)} ${pessoa.nome} → ${destino.nome} (devolução de “${o.descricao}”)") else null
            val e = if (juros > 0) mov(TipoMovimentacao.ENTRADA, juros, destino.id, null, catJuros, "Juros: ${o.descricao}",
                "Entrada de ${Dinheiro.formatar(juros)} em Juros recebidos (“${o.descricao}”)") else null
            val principalMov = t ?: e ?: return@transacaoConferida Resultado.Falha("Nada a receber.")
            val p = ligar(o, principalMov.id, recebido + plano.encargo)
            if (e != null) ajustar(o, p.id, NaturezaAjuste.JUROS_RECEBIDOS, juros, e.id)
            if (plano.encargo > 0) {
                val natureza = plano.classificacao!!.natureza!!
                val ex = mov(TipoMovimentacao.ENTRADA, plano.encargo, destino.id, null, catJuros, "${natureza.rotulo}: ${o.descricao}",
                    "Entrada de ${Dinheiro.formatar(plano.encargo)} em Juros recebidos (${natureza.rotulo.lowercase()}, “${o.descricao}”)")
                ajustar(o, p.id, natureza, plano.encargo, ex.id)
            }
            if (plano.abatimento > 0) {
                val naoReconhecidosDepois = s.jurosNaoReconhecidos - juros
                val dosJuros = minOf(plano.abatimento, naoReconhecidosDepois.coerceAtLeast(0))
                val doPrincipal = plano.abatimento - dosJuros
                if (dosJuros > 0) {
                    val a = ObrigacaoAjusteEntidade(relogio.novoId(), o.id, p.id, NaturezaAjuste.DESCONTO.name, dosJuros, null, "juros", agora, agora, null)
                    ajusteDao.inserir(a)
                    auditor.registrar(grupo, Operacao.CRIACAO, Auditor.AJUSTE_OBRIGACAO, a.id, "Desconto concedido de ${Dinheiro.formatar(dosJuros)} (juros) em “${o.descricao}”", ObrigacaoAjusteEntidade.serializer(), null, a)
                }
                if (doPrincipal > 0) {
                    val aj = mov(TipoMovimentacao.AJUSTE, -doPrincipal, pessoa.id, null, null, "Desconto concedido: ${o.descricao}",
                        "Ajuste de ${Dinheiro.formatarComSinal(-doPrincipal)} em “${pessoa.nome}” (desconto concedido)")
                    ajustar(o, p.id, NaturezaAjuste.DESCONTO, doPrincipal, aj.id)
                }
            }
            Resultado.Sucesso(principalMov.id)
        }
    }

    /**
     * Edição de parcelas sem pagamento (regra 6E.8): valor e/ou data desta parcela e, se
     * [futuras], das seguintes do mesmo empréstimo (as que também não têm pagamento).
     * O previsto não muda: a diferença vira acréscimo ou abatimento, datado hoje. No
     * recebido, com a despesa de encargo ou o ajuste de redução; no concedido, sem
     * movimentação (só muda a proporção K2), e sem deixar o total abaixo do principal.
     */
    suspend fun editarParcelas(obrigacaoId: String, novoValor: Long?, novaData: LocalDate?, futuras: Boolean): Resultado<Int> = banco.transacaoConferida {
        val o = obrigacaoDao.buscar(obrigacaoId)?.takeIf { it.excluidoEm == null && it.emprestimoId != null }
            ?: return@transacaoConferida Resultado.Falha("Parcela não encontrada.")
        if (novoValor != null && novoValor <= 0) return@transacaoConferida Resultado.Falha("O valor precisa ser maior que zero.")
        if (pagamentoDao.validosDa(o.id).isNotEmpty()) return@transacaoConferida Resultado.Falha("Esta parcela já tem pagamento ou recebimento: ela não pode ser editada.")
        val pessoa = contaDao.buscar(o.contaCreditoId!!)!!
        val recebido = pessoa.natureza == Natureza.EMPRESTIMO_RECEBIDO.name
        val v = visaoEmprestimo(o.emprestimoId!!)!!
        val s = v.situacao(o.emprestimoId, relogio.hoje())!!
        val alvo = s.parcelas.filter { p ->
            p.obrigacao.id == o.id || (futuras && (p.obrigacao.numeroParcela ?: 0) > (o.numeroParcela ?: 0) && p.situacao.pago == 0L)
        }.sortedBy { it.obrigacao.numeroParcela }
        if (novoValor != null && !recebido) {
            val novoRestante = s.restante + alvo.sumOf { novoValor - it.restante }
            if (novoRestante < s.saldoNaConta) return@transacaoConferida Resultado.Falha("O total das parcelas não pode ficar abaixo do que ainda falta devolver (${Dinheiro.formatar(s.saldoNaConta)}).")
        }
        val encargos = if (recebido && novoValor != null && alvo.any { novoValor > it.restante }) {
            categoriaDao.buscarPorCodigo(CategoriasDoSistema.ENCARGOS_FINANCEIROS) ?: return@transacaoConferida Resultado.Falha(SEM_ENCARGOS)
        } else null
        val c = Contexto(relogio.novoId(), relogio.agora(), relogio.hoje())
        with(c) {
            alvo.forEachIndexed { k, p ->
                val ob = obrigacaoDao.buscar(p.obrigacao.id)!!
                if (novoValor != null && novoValor != p.restante) {
                    val delta = novoValor - p.restante
                    val natureza = if (delta > 0) NaturezaAjuste.OUTRO_ACRESCIMO else NaturezaAjuste.OUTRO_ABATIMENTO
                    val movId = if (!recebido) null else if (delta > 0) {
                        mov(TipoMovimentacao.DESPESA, delta, pessoa.id, null, encargos!!.id, "Juros (edição): ${ob.descricao}",
                            "Despesa de ${Dinheiro.formatar(delta)} em Encargos financeiros (parcela editada: “${ob.descricao}”)").id
                    } else {
                        mov(TipoMovimentacao.AJUSTE, -delta, pessoa.id, null, null, "Redução (edição): ${ob.descricao}",
                            "Ajuste de ${Dinheiro.formatarComSinal(-delta)} em “${pessoa.nome}” (parcela editada)").id
                    }
                    val a = ObrigacaoAjusteEntidade(relogio.novoId(), ob.id, null, natureza.name, kotlin.math.abs(delta), movId, "edição", agora, agora, null)
                    ajusteDao.inserir(a)
                    auditor.registrar(grupo, Operacao.CRIACAO, Auditor.AJUSTE_OBRIGACAO, a.id,
                        "Parcela “${ob.descricao}” editada: ${Dinheiro.formatar(p.restante)} → ${Dinheiro.formatar(novoValor)}", ObrigacaoAjusteEntidade.serializer(), null, a)
                }
                if (novaData != null) {
                    val venc = novaData.plusMonths(k.toLong())
                    val d = ob.copy(vencimento = venc.toString(), mesReferencia = YearMonth.from(venc).toString(), atualizadoEm = agora)
                    if (d.vencimento != ob.vencimento) {
                        obrigacaoDao.atualizar(d)
                        auditor.registrar(grupo, Operacao.EDICAO, Auditor.OBRIGACAO, ob.id, "Vencimento de “${ob.descricao}” mudou para $venc", ObrigacaoEntidade.serializer(), ob, d)
                    }
                }
            }
            Resultado.Sucesso(alvo.size)
        }
    }

    // ------------------------------------------------------------------ desfazer

    /** Desfaz um pagamento de crédito inteiro, pela transferência que o fez. */
    suspend fun desfazer(transferenciaId: String): Resultado<Unit> = banco.transacaoConferida {
        val t = movDao.buscar(transferenciaId)?.takeIf { it.excluidoEm == null }
            ?: return@transacaoConferida Resultado.Falha("Pagamento não encontrado.")
        // v0.5: vale para cartão, crediário e empréstimos. A movimentação precisa ser o
        // pagamento (ou recebimento) de uma conta de terceiros: ligada a parcelas dessas
        // contas, ou a um abatimento geral de fatura. Nunca o Pagar das contas fixas.
        val ligacoes = pagamentoDao.daMovimentacao(t.id)
        val gerais = faturaDao.daMovimentacao(t.id)
        val deTerceiros = gerais.isNotEmpty() || (ligacoes.isNotEmpty() && ligacoes.all { p -> obrigacaoDao.buscar(p.obrigacaoId)?.contaCreditoId != null })
        if (!deTerceiros) return@transacaoConferida Resultado.Falha("Esta movimentação não é um pagamento de cartão, crediário ou empréstimo.")
        val grupo = relogio.novoId()
        val agora = relogio.agora()
        suspend fun excluirMov(m: MovimentacaoEntidade) {
            if (m.excluidoEm != null) return
            val d = m.copy(excluidoEm = agora, atualizadoEm = agora)
            movDao.atualizar(d)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.MOVIMENTACAO, m.id, "${TipoMovimentacao.valueOf(m.tipo).rotulo} de ${Dinheiro.formatar(m.valorCentavos)} “${m.descricao}” excluída", MovimentacaoEntidade.serializer(), m, d)
        }
        for (p in pagamentoDao.daMovimentacao(t.id)) {
            for (a in ajusteDao.doPagamento(p.id)) {
                val d = a.copy(excluidoEm = agora, atualizadoEm = agora)
                ajusteDao.atualizar(d)
                auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.AJUSTE_OBRIGACAO, a.id, "Ajuste de ${Dinheiro.formatar(a.valorCentavos)} excluído", ObrigacaoAjusteEntidade.serializer(), a, d)
                a.movimentacaoId?.let { id -> movDao.buscar(id)?.let { excluirMov(it) } }
            }
            val d = p.copy(excluidoEm = agora, atualizadoEm = agora)
            pagamentoDao.atualizar(d)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.PAGAMENTO, p.id, "Pagamento de ${Dinheiro.formatar(p.valorAplicadoCentavos)} excluído", PagamentoEntidade.serializer(), p, d)
        }
        // v0.6: os juros do parcelamento ligados a um abatimento geral desta transferência.
        for (a in ajusteDao.validosTodos().filter { it.observacao == "geral:${t.id}" }) {
            val d = a.copy(excluidoEm = agora, atualizadoEm = agora)
            ajusteDao.atualizar(d)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.AJUSTE_OBRIGACAO, a.id, "Ajuste de ${Dinheiro.formatar(a.valorCentavos)} excluído", ObrigacaoAjusteEntidade.serializer(), a, d)
            a.movimentacaoId?.let { id -> movDao.buscar(id)?.let { excluirMov(it) } }
        }
        for (g in faturaDao.daMovimentacao(t.id)) {
            val d = g.copy(excluidoEm = agora, atualizadoEm = agora)
            faturaDao.atualizar(d)
            auditor.registrar(grupo, Operacao.EXCLUSAO, Auditor.PAGAMENTO_FATURA, g.id, "Abatimento de ${Dinheiro.formatar(g.valorCentavos)} na fatura de ${g.mesReferencia} excluído", PagamentoFaturaEntidade.serializer(), g, d)
        }
        excluirMov(t)
        Resultado.Sucesso(Unit)
    }
}
