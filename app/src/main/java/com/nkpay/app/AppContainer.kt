package com.nkpay.app

import android.app.Application
import com.nkpay.app.dados.AuditoriaRepositorio
import com.nkpay.app.dados.Auditor
import com.nkpay.app.dados.CategoriaRepositorio
import com.nkpay.app.dados.ContaRepositorio
import com.nkpay.app.dados.MovimentacaoRepositorio
import com.nkpay.app.dados.NkPayBanco
import com.nkpay.app.dados.Relogio
import com.nkpay.app.dados.RelogioDoSistema
import com.nkpay.app.dominio.VisaoFinanceira
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Injeção de dependência manual: este objeto cria os repositórios UMA vez
 * e os entrega para quem precisar. Sem Hilt/Dagger (docs/decisoes/0001-stack.md).
 */
class AppContainer(
    val banco: NkPayBanco,
    val relogio: Relogio = RelogioDoSistema,
    escopo: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val auditor = Auditor(banco.auditoriaDao(), relogio)

    val contas = ContaRepositorio(banco, auditor, relogio)
    val categorias = CategoriaRepositorio(banco, auditor, relogio)
    val movimentacoes = MovimentacaoRepositorio(banco, auditor, relogio)
    val auditoria = AuditoriaRepositorio(banco)

    /**
     * Contas + categorias + movimentações, sempre atualizadas. Qualquer alteração no
     * banco gera uma visão nova, e todas as telas que a observam se redesenham.
     * É `null` até a primeira leitura do banco terminar.
     */
    val visao: StateFlow<VisaoFinanceira?> =
        combine(contas.observar(), categorias.observar(), movimentacoes.observar()) { c, cat, m ->
            VisaoFinanceira(c, cat, m)
        }.stateIn(escopo, SharingStarted.Eagerly, null)
}

class NkPayAplicacao : Application() {
    lateinit var container: AppContainer
        private set

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(NkPayBanco.criar(this))
        // Primeira abertura: cria as categorias sugeridas (só age se faltarem).
        escopo.launch { container.categorias.garantirCategoriasIniciais() }
    }
}
