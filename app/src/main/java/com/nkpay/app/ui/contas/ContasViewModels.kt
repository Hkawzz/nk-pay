package com.nkpay.app.ui.contas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nkpay.app.AppContainer
import com.nkpay.app.dados.ContaRepositorio
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.Conta
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.TipoBeneficio
import com.nkpay.app.dominio.Validacao
import com.nkpay.app.dominio.VisaoFinanceira
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Formulário de criar/editar conta. `contaId == null` cria uma nova. */
class ContaFormViewModel(private val container: AppContainer, private val contaId: String?) : ViewModel() {

    var carregado by mutableStateOf(contaId == null); private set
    var nome by mutableStateOf("")
    var natureza by mutableStateOf(Natureza.PROPRIO)
    var tipoBeneficio by mutableStateOf<TipoBeneficio?>(null)
    var saldoTexto by mutableStateOf("0,00")
    var data by mutableStateOf(container.relogio.hoje())
    var cor by mutableStateOf<Long?>(null)
    /** Nome do ícone (v0.4). Null = o padrão do tipo de conta. */
    var icone by mutableStateOf<String?>(null)
    /** A conta como estava (na edição), para não perder campos que este formulário não mostra. */
    private var original: Conta? = null

    var erroNome by mutableStateOf<String?>(null); private set
    var erroSaldo by mutableStateOf<String?>(null); private set
    var erroGeral by mutableStateOf<String?>(null); private set
    var salvando by mutableStateOf(false); private set

    val editando: Boolean get() = contaId != null

    init {
        if (contaId != null) {
            viewModelScope.launch {
                container.contas.buscar(contaId)?.let { c ->
                    nome = c.nome
                    natureza = c.natureza
                    tipoBeneficio = c.tipoBeneficio
                    saldoTexto = Dinheiro.formatarSemSimbolo(c.saldoInicial).let { if (c.saldoInicial < 0) "-$it" else it }
                    data = c.dataSaldoInicial
                    cor = c.cor
                    icone = c.icone
                    original = c
                }
                carregado = true
            }
        }
    }

    fun mudarNatureza(n: Natureza) {
        natureza = n
        tipoBeneficio = if (n == Natureza.BENEFICIO) (tipoBeneficio ?: TipoBeneficio.VA) else null
    }

    /** Valida, grava e chama [aoTerminar] se deu certo. */
    fun salvar(aoTerminar: () -> Unit) {
        val saldo = Dinheiro.ler(saldoTexto)
        erroSaldo = if (saldo == null) "Valor inválido. Exemplo: 1.234,56" else null
        val base = original ?: Conta(id = "", nome = "", natureza = natureza, saldoInicial = 0, dataSaldoInicial = data)
        val conta = base.copy(
            id = contaId ?: "",
            nome = nome.trim(),
            natureza = natureza,
            tipoBeneficio = tipoBeneficio,
            saldoInicial = saldo ?: 0,
            dataSaldoInicial = data,
            cor = cor,
            icone = icone,
        )
        val erro = Validacao.conta(conta)
        erroNome = erro?.takeIf { "nome" in it }
        erroGeral = erro?.takeIf { "nome" !in it }
        if (saldo == null || erro != null) return

        salvando = true
        viewModelScope.launch {
            val r = if (contaId == null) container.contas.criar(conta) else container.contas.editar(conta)
            salvando = false
            when (r) {
                is Resultado.Sucesso -> aoTerminar()
                is Resultado.Falha -> erroGeral = r.mensagem
            }
        }
    }
}

/** Tela de uma conta: saldo, movimentações e ações (arquivar, excluir). */
class ContaDetalheViewModel(private val container: AppContainer, val contaId: String) : ViewModel() {
    val visao: StateFlow<VisaoFinanceira?> = container.visao

    /** Preenchido quando a exclusão foi bloqueada: a tela oferece arquivar. */
    var bloqueioExclusao by mutableStateOf<Int?>(null); private set
    var mensagem by mutableStateOf<String?>(null)

    fun alternarArquivada(arquivadaAgora: Boolean) {
        viewModelScope.launch {
            when (val r = container.contas.arquivar(contaId, arquivar = !arquivadaAgora)) {
                is Resultado.Sucesso -> mensagem = if (arquivadaAgora) "Conta desarquivada" else "Conta arquivada"
                is Resultado.Falha -> mensagem = r.mensagem
            }
            bloqueioExclusao = null
        }
    }

    fun excluir(aoExcluir: () -> Unit) {
        viewModelScope.launch {
            when (val r = container.contas.excluir(contaId)) {
                ContaRepositorio.ResultadoExclusao.Excluida -> aoExcluir()
                is ContaRepositorio.ResultadoExclusao.TemMovimentacoes -> bloqueioExclusao = r.quantidade
                ContaRepositorio.ResultadoExclusao.NaoEncontrada -> aoExcluir()
            }
        }
    }

    fun fecharBloqueio() { bloqueioExclusao = null }
}
