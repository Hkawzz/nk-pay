package com.hkawzz.nkote.dados

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =====================================================================================
// Backup automático (v0.8, V2, regra 6G.6, ADR 0034). Esta parte não conhece o Android:
// ela grava o backup numa [PastaDeBackup], guarda só os últimos 7 da série automática
// (nunca apaga os manuais) e registra o resultado. O WorkManager e a pasta do seletor do
// Android ficam no pacote `backup`.
// =====================================================================================

enum class FrequenciaBackup(val rotulo: String, val dias: Long) {
    DIARIO("Diário", 1),
    SEMANAL("Semanal", 7),
    MENSAL("Mensal", 30),
}

data class ConfiguracaoBackup(
    val pastaUri: String?,
    val frequencia: FrequenciaBackup,
    val ultimoEm: Long?,
    /** true = deu certo, false = falhou, null = nunca rodou. */
    val ultimoOk: Boolean?,
    val ultimaMensagem: String?,
) {
    val falhou: Boolean get() = ultimoOk == false
}

/** Um arquivo numa pasta de backup. */
data class ArquivoDeBackup(val nome: String, val id: String)

/** A pasta escolhida pelo usuário (no Android, uma pasta do seletor; nos testes, uma pasta temporária). */
interface PastaDeBackup {
    fun listar(): List<ArquivoDeBackup>
    fun criar(nome: String): OutputStream
    fun apagar(arquivo: ArquivoDeBackup)
}

class BackupAutomaticoServico(
    private val banco: NkPayBanco,
    private val relogio: Relogio,
    private val backup: BackupServico,
) {
    private val dao = banco.configuracaoBackupDao()

    companion object {
        /** Prefixo da série automática: só esses arquivos entram na contagem dos 7. */
        const val PREFIXO = "nkote-auto-"
        const val MANTER = 7
    }

    private fun ConfiguracaoBackupEntidade.paraDominio() = ConfiguracaoBackup(
        pastaUri, runCatching { FrequenciaBackup.valueOf(frequencia) }.getOrDefault(FrequenciaBackup.DIARIO),
        ultimoEm, ultimoResultado?.let { it == "OK" }, ultimaMensagem,
    )

    private fun padrao() = ConfiguracaoBackupEntidade(ConfiguracaoBackupEntidade.UNICA, null, FrequenciaBackup.DIARIO.name, null, null, null, relogio.agora())

    fun observar(): Flow<ConfiguracaoBackup> = dao.observar().map { (it ?: padrao()).paraDominio() }
    suspend fun atual(): ConfiguracaoBackup = (dao.buscar() ?: padrao()).paraDominio()

    suspend fun escolherPasta(uri: String) {
        val c = dao.buscar() ?: padrao()
        dao.salvar(c.copy(pastaUri = uri, atualizadoEm = relogio.agora()))
    }

    suspend fun mudarFrequencia(f: FrequenciaBackup) {
        val c = dao.buscar() ?: padrao()
        dao.salvar(c.copy(frequencia = f.name, atualizadoEm = relogio.agora()))
    }

    private suspend fun registrar(ok: Boolean, mensagem: String?) {
        val c = dao.buscar() ?: padrao()
        val agora = relogio.agora()
        dao.salvar(c.copy(ultimoEm = agora, ultimoResultado = if (ok) "OK" else "FALHA", ultimaMensagem = mensagem, atualizadoEm = agora))
    }

    fun nomeDoArquivo(): String =
        PREFIXO + SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.ROOT).format(Date(relogio.agora())) + BackupServico.EXTENSAO

    /**
     * Grava um backup na [pasta] e apaga os mais antigos da série automática além dos 7.
     * Nunca lança: o resultado (e a mensagem, se falhar) fica registrado para a Início avisar.
     * @return true se deu certo.
     */
    suspend fun executar(pasta: PastaDeBackup?): Boolean {
        if (pasta == null) {
            registrar(false, "Escolha a pasta do backup automático em Mais → Backup.")
            return false
        }
        return try {
            pasta.criar(nomeDoArquivo()).use { backup.exportar(it) }
            pasta.listar().filter { it.nome.startsWith(PREFIXO) }.sortedByDescending { it.nome }.drop(MANTER).forEach { pasta.apagar(it) }
            registrar(true, null)
            true
        } catch (e: Exception) {
            registrar(false, "Não foi possível gravar na pasta escolhida (${e.message ?: e.javaClass.simpleName}). Confira a pasta e o espaço livre.")
            false
        }
    }
}
