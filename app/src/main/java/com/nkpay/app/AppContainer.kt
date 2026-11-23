package com.nkpay.app

import android.app.Application
import android.content.Context
import com.nkpay.app.dados.BackupServico
import com.nkpay.app.dados.ContaFixaRepositorio
import com.nkpay.app.dados.ObrigacaoRepositorio
import com.nkpay.app.dados.paraDominio
import com.nkpay.app.dominio.VisaoAPagar
import kotlinx.coroutines.flow.map
import com.nkpay.app.dados.RegistroDeBackup
import com.nkpay.app.dados.RegistroDeBackupEmMemoria
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
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
    pastaCopias: File = File(System.getProperty("java.io.tmpdir"), "nkpay-copias"),
    registroBackup: RegistroDeBackup = RegistroDeBackupEmMemoria(),
) {
    private val auditor = Auditor(banco.auditoriaDao(), relogio)

    val contas = ContaRepositorio(banco, auditor, relogio)
    val categorias = CategoriaRepositorio(banco, auditor, relogio)
    val movimentacoes = MovimentacaoRepositorio(banco, auditor, relogio)
    val auditoria = AuditoriaRepositorio(banco)
    val backup = BackupServico(banco, relogio, BuildConfig.VERSION_NAME, pastaCopias, registroBackup)

    /**
     * Contas + categorias + movimentações, sempre atualizadas. Qualquer alteração no
     * banco gera uma visão nova, e todas as telas que a observam se redesenham.
     * É `null` até a primeira leitura do banco terminar.
     */
    val visao: StateFlow<VisaoFinanceira?> =
        combine(contas.observar(), categorias.observar(), movimentacoes.observar()) { c, cat, m ->
            VisaoFinanceira(c, cat, m)
        }.stateIn(escopo, SharingStarted.Eagerly, null)

    // --- Contas a pagar (v0.3) ---
    val contasFixas = ContaFixaRepositorio(banco, auditor, relogio)
    val obrigacoes = ObrigacaoRepositorio(banco, auditor, relogio)
    val pagamentos = com.nkpay.app.dados.PagamentoServico(banco, auditor, relogio)

    /** Contas fixas + obrigações + pagamentos e ajustes válidos, sempre atualizados. */
    val visaoAPagar: StateFlow<VisaoAPagar?> =
        combine(
            contasFixas.observar(),
            obrigacoes.observar(),
            banco.pagamentoDao().observarValidos().map { l -> l.map { it.paraDominio() } },
            banco.ajusteObrigacaoDao().observarValidos().map { l -> l.map { it.paraDominio() } },
        ) { f, o, p, a -> VisaoAPagar(f, o, p, a) }
            .stateIn(escopo, SharingStarted.Eagerly, null)

    // --- Cartões e crediários (v0.4) ---
    val compras = com.nkpay.app.dados.CompraServico(banco, auditor, relogio)

    /** Contas, movimentações, parcelas, pagamentos, ajustes, abatimentos gerais e compras, sempre atualizados. */
    val visaoCredito: StateFlow<com.nkpay.app.dominio.VisaoCredito?> =
        combine(
            combine(visao, visaoAPagar) { v, a -> v to a },
            banco.pagamentoFaturaDao().observarValidos().map { l -> l.map { it.paraDominio() } },
            banco.compraDao().observarValidas().map { l -> l.map { it.paraDominio() } },
        ) { (v, a), gerais, compras ->
            if (v == null || a == null) null
            else com.nkpay.app.dominio.VisaoCredito(v.contas, v.movimentacoes, a.obrigacoes.filter { it.ehParcela }, a.pagamentos, a.ajustes, gerais, compras)
        }.stateIn(escopo, SharingStarted.Eagerly, null)
}

/** Guarda a data do último backup exportado nas preferências do app (não é dado financeiro). */
class RegistroDeBackupNasPreferencias(context: Context) : RegistroDeBackup {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    override val ultimo = MutableStateFlow(prefs.getLong("ultimo", 0L).takeIf { it > 0 })
    override fun marcar(quando: Long) {
        prefs.edit().putLong("ultimo", quando).apply()
        ultimo.value = quando
    }
}

class NkPayAplicacao : Application() {
    lateinit var container: AppContainer
        private set

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            banco = NkPayBanco.criar(this),
            pastaCopias = File(filesDir, "copias-de-seguranca"),
            registroBackup = RegistroDeBackupNasPreferencias(this),
        )
        // Primeira abertura: cria as categorias sugeridas (só age se faltarem).
        // v0.4: categorias que vieram da v0.3 sem ícone recebem a aparência sugerida (auditada).
        escopo.launch {
            container.categorias.garantirCategoriasIniciais()
            container.categorias.aplicarAparenciaSugerida()
        }
    }
}
