package com.hkawzz.nkote.ui.movimentacoes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dados.Resultado
import com.hkawzz.nkote.dominio.Dinheiro
import com.hkawzz.nkote.dominio.Movimentacao
import com.hkawzz.nkote.dominio.Saldos
import com.hkawzz.nkote.dominio.TipoMovimentacao
import com.hkawzz.nkote.dominio.VisaoFinanceira
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Lista de movimentações: mês escolhido e filtros por tipo e por conta. */
class MovimentacoesViewModel(container: AppContainer) : ViewModel() {
    val visao = container.visao
    var mes by mutableStateOf(YearMonth.from(container.relogio.hoje()))
    var filtroTipo by mutableStateOf<TipoMovimentacao?>(null)
    var filtroConta by mutableStateOf<String?>(null)

    fun mesAnterior() { mes = mes.minusMonths(1) }
    fun proximoMes() { mes = mes.plusMonths(1) }

    fun filtrar(v: VisaoFinanceira): List<Movimentacao> = v.movimentacoes.filter { m ->
        YearMonth.from(m.data) == mes &&
            (filtroTipo == null || m.tipo == filtroTipo) &&
            (filtroConta == null || m.contaId == filtroConta || m.contaDestinoId == filtroConta)
    }
}

/** Como o ajuste é informado: pelo novo saldo (o app calcula a diferença) ou pela diferença. */
enum class ModoAjuste { NOVO_SALDO, DIFERENCA }

/**
 * Formulário de movimentação (os 4 tipos). `id == null` cria uma nova.
 * Os campos mudam conforme o tipo (ver a tela).
 */
class MovimentacaoFormViewModel(
    private val container: AppContainer,
    private val id: String?,
    contaInicial: String?,
) : ViewModel() {

    var carregado by mutableStateOf(id == null); private set
    var tipo by mutableStateOf(TipoMovimentacao.DESPESA)
    var contaId by mutableStateOf(contaInicial)
    var contaDestinoId by mutableStateOf<String?>(null)
    var categoriaId by mutableStateOf<String?>(null)
    var valorTexto by mutableStateOf("0,00")
    var novoSaldoTexto by mutableStateOf("")
    var modoAjuste by mutableStateOf(ModoAjuste.NOVO_SALDO)
    var data by mutableStateOf(container.relogio.hoje())
    var descricao by mutableStateOf("")

    var erroValor by mutableStateOf<String?>(null); private set
    var erroGeral by mutableStateOf<String?>(null); private set
    var salvando by mutableStateOf(false); private set
    /** True se esta movimentação foi criada pelo botão Pagar (só muda pelo pagamento). */
    var ehPagamentoDeConta by mutableStateOf(false); private set

    /** A movimentação como estava no banco (na edição). */
    private var original: Movimentacao? = null
    val editando: Boolean get() = id != null

    init {
        if (id != null) {
            viewModelScope.launch {
                ehPagamentoDeConta = container.movimentacoes.ehPagamentoDeConta(id)
                container.movimentacoes.buscar(id)?.let { m ->
                    original = m
                    tipo = m.tipo
                    contaId = m.contaId
                    contaDestinoId = m.contaDestinoId
                    categoriaId = m.categoriaId
                    data = m.data
                    descricao = m.descricao
                    if (m.tipo == TipoMovimentacao.AJUSTE) {
                        modoAjuste = ModoAjuste.DIFERENCA
                        valorTexto = (if (m.valor < 0) "-" else "") + Dinheiro.formatarSemSimbolo(m.valor)
                    } else {
                        valorTexto = Dinheiro.formatarSemSimbolo(m.valor)
                    }
                }
                carregado = true
            }
        }
    }

    fun mudarTipo(novo: TipoMovimentacao) {
        tipo = novo
        if (!novo.temCategoria) categoriaId = null
        if (novo != TipoMovimentacao.TRANSFERENCIA) contaDestinoId = null
        erroValor = null
        erroGeral = null
    }

    /**
     * Saldo da conta SEM o efeito desta movimentação (na edição).
     * É a base para calcular o ajuste pelo novo saldo.
     */
    fun saldoBase(v: VisaoFinanceira, conta: String): Long {
        val sem = original?.let { Saldos.efeitoNaConta(it, conta) } ?: 0
        return v.saldo(conta) - sem
    }

    /** Valor do ajuste que será gravado, ou null se o que foi digitado é inválido. */
    fun valorDoAjuste(v: VisaoFinanceira): Long? {
        val conta = contaId ?: return null
        return when (modoAjuste) {
            ModoAjuste.NOVO_SALDO -> Dinheiro.ler(novoSaldoTexto)?.let { Saldos.valorDoAjuste(saldoBase(v, conta), it) }
            ModoAjuste.DIFERENCA -> Dinheiro.ler(valorTexto)
        }
    }

    fun salvar(aoTerminar: () -> Unit) {
        erroGeral = null
        viewModelScope.launch {
            val v = container.visao.filterNotNull().first()
            val valor = if (tipo == TipoMovimentacao.AJUSTE) valorDoAjuste(v) else Dinheiro.ler(valorTexto)
            if (valor == null) {
                erroValor = "Valor inválido. Exemplo: 1.234,56"
                return@launch
            }
            erroValor = null
            val conta = contaId
            if (conta == null) {
                erroGeral = "Escolha uma conta."
                return@launch
            }
            val m = Movimentacao(
                id = id ?: "",
                tipo = tipo,
                data = data,
                valor = valor,
                contaId = conta,
                contaDestinoId = contaDestinoId.takeIf { tipo == TipoMovimentacao.TRANSFERENCIA },
                categoriaId = categoriaId.takeIf { tipo.temCategoria },
                descricao = descricao,
            )
            salvando = true
            val r = if (id == null) container.movimentacoes.criar(m) else container.movimentacoes.editar(m)
            salvando = false
            when (r) {
                is Resultado.Sucesso -> aoTerminar()
                is Resultado.Falha -> erroGeral = r.mensagem
            }
        }
    }

    fun excluir(aoTerminar: () -> Unit) {
        val alvo = id ?: return
        viewModelScope.launch {
            container.movimentacoes.excluir(alvo)
            aoTerminar()
        }
    }
}
