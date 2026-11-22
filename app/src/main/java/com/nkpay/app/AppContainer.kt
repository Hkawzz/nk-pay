package com.nkpay.app

import android.app.Application
import android.content.Context
import com.nkpay.app.dados.AuditoriaRepositorio
import com.nkpay.app.dados.Auditor
import com.nkpay.app.dados.CategoriaRepositorio
import com.nkpay.app.dados.ContaRepositorio
import com.nkpay.app.dados.MovimentacaoRepositorio
import com.nkpay.app.dados.NkPayBanco
import com.nkpay.app.dados.Relogio
import com.nkpay.app.dados.RelogioDoSistema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Injeção de dependência manual: este objeto cria o banco e os repositórios UMA vez
 * e os entrega para quem precisar. Sem Hilt/Dagger (docs/decisoes/0001-stack.md).
 */
class AppContainer(context: Context, relogio: Relogio = RelogioDoSistema) {
    val banco: NkPayBanco = NkPayBanco.criar(context)
    private val auditor = Auditor(banco.auditoriaDao(), relogio)

    val relogio: Relogio = relogio
    val contas = ContaRepositorio(banco, auditor, relogio)
    val categorias = CategoriaRepositorio(banco, auditor, relogio)
    val movimentacoes = MovimentacaoRepositorio(banco, auditor, relogio)
    val auditoria = AuditoriaRepositorio(banco)
}

class NkPayAplicacao : Application() {
    lateinit var container: AppContainer
        private set

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Primeira abertura: cria as categorias sugeridas (só age se faltarem).
        escopo.launch { container.categorias.garantirCategoriasIniciais() }
    }
}
