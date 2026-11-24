package com.hkawzz.nkote

import android.app.Application
import android.content.Context
import com.hkawzz.nkote.dados.BackupServico
import com.hkawzz.nkote.dados.ContaFixaRepositorio
import com.hkawzz.nkote.dados.ObrigacaoRepositorio
import com.hkawzz.nkote.dados.paraDominio
import com.hkawzz.nkote.dominio.VisaoAPagar
import kotlinx.coroutines.flow.map
import com.hkawzz.nkote.dados.RegistroDeBackup
import com.hkawzz.nkote.dados.RegistroDeBackupEmMemoria
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import com.hkawzz.nkote.dados.AuditoriaRepositorio
import com.hkawzz.nkote.dados.Auditor
import com.hkawzz.nkote.dados.CategoriaRepositorio
import com.hkawzz.nkote.dados.ContaRepositorio
import com.hkawzz.nkote.dados.MovimentacaoRepositorio
import com.hkawzz.nkote.dados.NkPayBanco
import com.hkawzz.nkote.dados.Relogio
import com.hkawzz.nkote.dados.RelogioDoSistema
import com.hkawzz.nkote.dominio.VisaoFinanceira
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Injeção de dependência manual: este objeto cria os repositórios UMA vez
 * e os entrega para quem precisar. Sem Hilt/Dagger (docs/decisoes/0001-stack.md).
 */
/**
 * Se o banco for fechado com uma leitura em andamento (acontece nos testes, que fecham o
 * banco ao terminar), a visão mantém o último valor em vez de derrubar o processo.
 * Qualquer outro erro continua sendo lançado.
 */
private fun semBanco(e: Throwable) {
    if (e !is IllegalStateException) throw e
}

class AppContainer(
    val banco: NkPayBanco,
    val relogio: Relogio = RelogioDoSistema,
    escopo: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    pastaCopias: File = File(System.getProperty("java.io.tmpdir"), "nkpay-copias"),
    registroBackup: RegistroDeBackup = RegistroDeBackupEmMemoria(),
    /** v0.8: avisos de uma vez só (ex.: a mensagem da conversão V3). Não é dado financeiro. */
    val avisos: AvisosDoApp = AvisosEmMemoria(),
) {
    private val auditor = Auditor(banco.auditoriaDao(), relogio)

    val contas = ContaRepositorio(banco, auditor, relogio)
    val categorias = CategoriaRepositorio(banco, auditor, relogio)
    val movimentacoes = MovimentacaoRepositorio(banco, auditor, relogio)
    val auditoria = AuditoriaRepositorio(banco)
    // v0.8 (V3): restaurar um backup da v0.7 dá caixinhas zeradas aos objetivos antigos.
    val backup = BackupServico(banco, relogio, BuildConfig.VERSION_NAME, pastaCopias, registroBackup) { objetivos.converterObjetivosAntigos() }

    /**
     * Contas + categorias + movimentações, sempre atualizadas. Qualquer alteração no
     * banco gera uma visão nova, e todas as telas que a observam se redesenham.
     * É `null` até a primeira leitura do banco terminar.
     */
    val visao: StateFlow<VisaoFinanceira?> =
        combine(
            contas.observar(), categorias.observar(), movimentacoes.observar(),
            // v0.7: objetivos e movimentos de reserva (o Guardado do Saldo disponível).
            banco.objetivoDao().observarValidos().map { l -> l.map { it.paraDominio() } },
            banco.movimentoReservaDao().observarValidos().map { l -> l.map { it.paraDominio() } },
        ) { c, cat, m, obj, mov ->
            // v0.8: as caixinhas vêm das contas; os movimentos de reserva da v0.7 ficam só para leitura.
            VisaoFinanceira(c, cat, m, com.hkawzz.nkote.dominio.VisaoObjetivos(obj, mov, c, m))
        }.catch { semBanco(it) }.stateIn(escopo, SharingStarted.Eagerly, null)

    // --- Contas a pagar (v0.3) ---
    val contasFixas = ContaFixaRepositorio(banco, auditor, relogio)
    val obrigacoes = ObrigacaoRepositorio(banco, auditor, relogio)
    val pagamentos = com.hkawzz.nkote.dados.PagamentoServico(banco, auditor, relogio)

    /** Contas fixas + obrigações + pagamentos e ajustes válidos, sempre atualizados. */
    val visaoAPagar: StateFlow<VisaoAPagar?> =
        combine(
            contasFixas.observar(),
            obrigacoes.observar(),
            banco.pagamentoDao().observarValidos().map { l -> l.map { it.paraDominio() } },
            banco.ajusteObrigacaoDao().observarValidos().map { l -> l.map { it.paraDominio() } },
            banco.pendenciaLevadaDao().observarValidas().map { l -> l.map { it.paraDominio() } },
        ) { f, o, p, a, l -> VisaoAPagar(f, o, p, a, l) }
            .catch { semBanco(it) }.stateIn(escopo, SharingStarted.Eagerly, null)

    // --- Cartões e crediários (v0.4) ---
    val compras = com.hkawzz.nkote.dados.CompraServico(banco, auditor, relogio)
    val pagamentosCredito = com.hkawzz.nkote.dados.PagamentoCreditoServico(banco, auditor, relogio)
    /** v0.8 (V2): backup automático (a regra; o WorkManager fica no pacote backup). */
    val backupAutomatico = com.hkawzz.nkote.dados.BackupAutomaticoServico(banco, relogio, backup)

    /** v0.7: objetivos e reservas (regra 6F). */
    val objetivos = com.hkawzz.nkote.dados.ObjetivoServico(banco, auditor, relogio)
    /** v0.6: Preparar próximo mês (regra 7.6). */
    val prepararMes = com.hkawzz.nkote.dados.PrepararMesServico(banco, auditor, relogio)

    // --- Empréstimos (v0.5) ---
    val emprestimos = com.hkawzz.nkote.dados.EmprestimoServico(banco, auditor, relogio)

    /** Contas, movimentações, empréstimos, parcelas, pagamentos e ajustes, sempre atualizados. */
    val visaoEmprestimos: StateFlow<com.hkawzz.nkote.dominio.VisaoEmprestimos?> =
        combine(visao, visaoAPagar, banco.emprestimoDao().observarValidos().map { l -> l.map { it.paraDominio() } }) { v, a, e ->
            if (v == null || a == null) null
            else com.hkawzz.nkote.dominio.VisaoEmprestimos(v.contas, v.movimentacoes, e, a.obrigacoes, a.pagamentos, a.ajustes)
        }.catch { semBanco(it) }.stateIn(escopo, SharingStarted.Eagerly, null)

    /** Último filtro das Análises: só na memória, enquanto o app estiver aberto (nada é gravado). */
    val filtroAnalises = MutableStateFlow(com.hkawzz.nkote.dominio.FiltroPeriodo.MES_ATUAL)

    /** Contas, movimentações, parcelas, pagamentos, ajustes, abatimentos gerais e compras, sempre atualizados. */
    val visaoCredito: StateFlow<com.hkawzz.nkote.dominio.VisaoCredito?> =
        combine(
            combine(visao, visaoAPagar) { v, a -> v to a },
            banco.pagamentoFaturaDao().observarValidos().map { l -> l.map { it.paraDominio() } },
            banco.compraDao().observarValidas().map { l -> l.map { it.paraDominio() } },
        ) { (v, a), gerais, compras ->
            if (v == null || a == null) null
            else com.hkawzz.nkote.dominio.VisaoCredito(v.contas, v.movimentacoes, a.obrigacoes.filter { it.ehParcela }, a.pagamentos, a.ajustes, gerais, compras)
        }.catch { semBanco(it) }.stateIn(escopo, SharingStarted.Eagerly, null)
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

/** v0.8: avisos que aparecem uma vez só (guardados nas preferências; não é dado financeiro). */
interface AvisosDoApp {
    val conversaoV3Vista: kotlinx.coroutines.flow.StateFlow<Boolean>
    fun marcarConversaoV3Vista()
}

class AvisosEmMemoria(inicial: Boolean = false) : AvisosDoApp {
    override val conversaoV3Vista = MutableStateFlow(inicial)
    override fun marcarConversaoV3Vista() { conversaoV3Vista.value = true }
}

class AvisosNasPreferencias(context: Context) : AvisosDoApp {
    private val prefs = context.getSharedPreferences("avisos", Context.MODE_PRIVATE)
    override val conversaoV3Vista = MutableStateFlow(prefs.getBoolean("conversao_v3", false))
    override fun marcarConversaoV3Vista() {
        prefs.edit().putBoolean("conversao_v3", true).apply()
        conversaoV3Vista.value = true
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
            avisos = AvisosNasPreferencias(this),
        )
        // Primeira abertura: cria as categorias sugeridas (só age se faltarem).
        // v0.4: categorias que vieram da v0.3 sem ícone recebem a aparência sugerida (auditada).
        escopo.launch {
            container.categorias.garantirCategoriasIniciais()
            container.categorias.aplicarAparenciaSugerida()
            container.categorias.garantirJurosRecebidos() // v0.5
            container.objetivos.converterObjetivosAntigos() // v0.8 (V3): caixinhas zeradas, nenhum saldo muda
            // v0.8 (V2): mantém a série automática agendada, se a pasta já foi escolhida.
            container.backupAutomatico.atual().takeIf { it.pastaUri != null }?.let {
                com.hkawzz.nkote.backup.AgendadorDeBackup.agendar(this@NkPayAplicacao, it.frequencia)
            }
        }
    }
}
