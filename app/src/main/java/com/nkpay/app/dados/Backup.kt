package com.nkpay.app.dados

import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// =====================================================================================
// Backup e restauração (docs/decisoes/0010-backup.md).
//
// O arquivo .nkpay é um ZIP com dois arquivos:
//   manifest.json  formato, versão do banco, versão do app, data, contagens e o SHA-256 dos dados
//   dados.json     todas as linhas das 4 tabelas, INCLUSIVE as excluídas logicamente
//
// É um backup "lógico" (os dados em JSON), e não uma cópia do arquivo SQLite: funciona
// em qualquer versão do Android e permite converter backups antigos quando o banco mudar.
// =====================================================================================

@Serializable
data class ManifestoBackup(
    /** Formato do arquivo .nkpay (muda só se a estrutura do ZIP mudar). */
    val formato: Int,
    /** Versão do esquema do banco quando o backup foi feito. */
    val versaoBanco: Int,
    val versaoApp: String,
    val criadoEm: Long,
    val sha256Dados: String,
    val contas: Int,
    val categorias: Int,
    val movimentacoes: Int,
    val registrosDeAuditoria: Int,
    // Versão 2 (v0.3). Valor padrão 0 para os manifestos da versão 1.
    val contasFixas: Int = 0,
    val obrigacoes: Int = 0,
    // Versão 3 (v0.4).
    val compras: Int = 0,
    // Versão 4 (v0.5).
    val emprestimos: Int = 0,
)

@Serializable
data class DadosBackup(
    val contas: List<ContaEntidade>,
    val categorias: List<CategoriaEntidade>,
    val movimentacoes: List<MovimentacaoEntidade>,
    val auditoria: List<AuditoriaEntidade>,
    // Versão 2 do banco (v0.3). Backups da versão 1 não têm estes campos:
    // o valor padrão (lista vazia) faz eles continuarem restauráveis.
    val contasFixas: List<ContaFixaEntidade> = emptyList(),
    val obrigacoes: List<ObrigacaoEntidade> = emptyList(),
    val pagamentos: List<PagamentoEntidade> = emptyList(),
    val ajustesDeObrigacao: List<ObrigacaoAjusteEntidade> = emptyList(),
    // Versão 3 do banco (v0.4). Mesma regra: lista vazia para backups antigos.
    val compras: List<CompraEntidade> = emptyList(),
    val pagamentosDeFatura: List<PagamentoFaturaEntidade> = emptyList(),
    // Versão 4 do banco (v0.5). Lista vazia para backups antigos.
    val emprestimos: List<EmprestimoEntidade> = emptyList(),
    // Versão 5 do banco (v0.6). Lista vazia para backups antigos.
    val pendenciasLevadas: List<PendenciaLevadaEntidade> = emptyList(),
)

/** Um backup lido e validado, pronto para ser restaurado. */
class BackupLido internal constructor(val manifesto: ManifestoBackup, internal val dados: DadosBackup)

/** Onde fica guardada a data do último backup exportado (para o aviso na Início). */
interface RegistroDeBackup {
    val ultimo: StateFlow<Long?>
    fun marcar(quando: Long)
}

class RegistroDeBackupEmMemoria(inicial: Long? = null) : RegistroDeBackup {
    override val ultimo = MutableStateFlow(inicial)
    override fun marcar(quando: Long) { ultimo.value = quando }
}

class BackupServico(
    private val banco: NkPayBanco,
    private val relogio: Relogio,
    private val versaoApp: String,
    /** Pasta interna do app onde ficam as cópias automáticas feitas antes de restaurar. */
    private val pastaCopias: File,
    val registro: RegistroDeBackup,
) {
    private val dao = banco.backupDao()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /** Erro com mensagem pronta para mostrar na tela. */
    class BackupInvalido(mensagem: String) : Exception(mensagem)

    // ---------------------------------------------------------------- exportar

    /** Grava o backup completo em [saida]. Devolve o manifesto do que foi gravado. */
    suspend fun exportar(saida: OutputStream, marcarComoUltimo: Boolean = true): ManifestoBackup {
        // Lê tudo dentro de uma transação: o backup é uma "foto" consistente.
        val dados = banco.withTransaction {
            DadosBackup(
                dao.contas(), dao.categorias(), dao.movimentacoes(), dao.auditoria(),
                dao.contasFixas(), dao.obrigacoes(), dao.pagamentos(), dao.ajustesDeObrigacao(),
                dao.compras(), dao.pagamentosDeFatura(), dao.emprestimos(), dao.pendenciasLevadas(),
            )
        }
        val bytesDados = json.encodeToString(DadosBackup.serializer(), dados).toByteArray(Charsets.UTF_8)
        val manifesto = ManifestoBackup(
            formato = FORMATO,
            versaoBanco = NkPayBanco.VERSAO,
            versaoApp = versaoApp,
            criadoEm = relogio.agora(),
            sha256Dados = sha256(bytesDados),
            contas = dados.contas.size,
            categorias = dados.categorias.size,
            movimentacoes = dados.movimentacoes.size,
            registrosDeAuditoria = dados.auditoria.size,
            contasFixas = dados.contasFixas.size,
            obrigacoes = dados.obrigacoes.size,
            compras = dados.compras.size,
            emprestimos = dados.emprestimos.size,
        )
        ZipOutputStream(saida).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFESTO))
            zip.write(json.encodeToString(ManifestoBackup.serializer(), manifesto).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DADOS))
            zip.write(bytesDados)
            zip.closeEntry()
        }
        if (marcarComoUltimo) registro.marcar(manifesto.criadoEm)
        return manifesto
    }

    // ---------------------------------------------------------------- ler e validar

    /**
     * Lê um arquivo .nkpay e confere tudo ANTES de qualquer alteração:
     * estrutura do ZIP, formato, versão do banco, SHA-256 e se as referências fecham.
     * @throws BackupInvalido com a explicação para a pessoa.
     */
    fun ler(entrada: InputStream): BackupLido {
        val arquivos = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(entrada).use { zip ->
                while (true) {
                    val item = zip.nextEntry ?: break
                    if (item.name == MANIFESTO || item.name == DADOS) {
                        arquivos[item.name] = lerComLimite(zip)
                    }
                }
            }
        } catch (e: BackupInvalido) {
            throw e
        } catch (e: Exception) {
            throw BackupInvalido("O arquivo não é um backup do NK Pay (ou está corrompido).")
        }
        val bytesManifesto = arquivos[MANIFESTO] ?: throw BackupInvalido("O arquivo não é um backup do NK Pay.")
        val bytesDados = arquivos[DADOS] ?: throw BackupInvalido("O backup está incompleto (faltam os dados).")

        val manifesto = runCatching { json.decodeFromString(ManifestoBackup.serializer(), bytesManifesto.toString(Charsets.UTF_8)) }
            .getOrElse { throw BackupInvalido("O backup está corrompido (manifesto ilegível).") }
        if (manifesto.formato > FORMATO || manifesto.versaoBanco > NkPayBanco.VERSAO) {
            throw BackupInvalido("Este backup foi feito por uma versão mais nova do NK Pay (${manifesto.versaoApp}). Atualize o app antes de restaurar.")
        }
        if (sha256(bytesDados) != manifesto.sha256Dados) {
            throw BackupInvalido("O backup está corrompido: os dados não conferem com a assinatura do arquivo.")
        }
        // Quando o banco mudar de versão, a conversão de backups antigos entra aqui
        // (ex.: if (manifesto.versaoBanco == 1) converterDe1Para2(...)).
        val dados = runCatching { json.decodeFromString(DadosBackup.serializer(), bytesDados.toString(Charsets.UTF_8)) }
            .getOrElse { throw BackupInvalido("O backup está corrompido (dados ilegíveis).") }
        conferirReferencias(dados)
        return BackupLido(manifesto, dados)
    }

    private fun conferirReferencias(d: DadosBackup) {
        val contas = d.contas.map { it.id }.toSet()
        val categorias = d.categorias.map { it.id }.toSet()
        val ok = d.movimentacoes.all { m ->
            m.contaId in contas && (m.contaDestinoId == null || m.contaDestinoId in contas) &&
                (m.categoriaId == null || m.categoriaId in categorias)
        }
        if (!ok) throw BackupInvalido("O backup está inconsistente: há movimentações de contas ou categorias que não existem nele.")

        val movs = d.movimentacoes.map { it.id }.toSet()
        val fixas = d.contasFixas.map { it.id }.toSet()
        val obrigs = d.obrigacoes.map { it.id }.toSet()
        val pags = d.pagamentos.map { it.id }.toSet()
        val okV2 = d.contasFixas.all { (it.categoriaId == null || it.categoriaId in categorias) && (it.contaPadraoId == null || it.contaPadraoId in contas) } &&
            d.obrigacoes.all { (it.contaFixaId == null || it.contaFixaId in fixas) && (it.categoriaId == null || it.categoriaId in categorias) && (it.contaCreditoId == null || it.contaCreditoId in contas) } &&
            d.pagamentos.all { it.obrigacaoId in obrigs && it.movimentacaoId in movs } &&
            d.ajustesDeObrigacao.all { it.obrigacaoId in obrigs && (it.pagamentoId == null || it.pagamentoId in pags) && (it.movimentacaoId == null || it.movimentacaoId in movs) }
        if (!okV2) throw BackupInvalido("O backup está inconsistente: há contas a pagar que apontam para registros que não existem nele.")

        val okV3 = d.compras.all { it.contaCreditoId in contas && it.movimentacaoId in movs && (it.categoriaId == null || it.categoriaId in categorias) } &&
            d.pagamentosDeFatura.all { it.contaCreditoId in contas && it.movimentacaoId in movs }
        if (!okV3) throw BackupInvalido("O backup está inconsistente: há compras ou pagamentos de fatura que apontam para registros que não existem nele.")

        val okV4 = d.emprestimos.all {
            it.contaId in contas && (it.contaPropriaId == null || it.contaPropriaId in contas) && (it.categoriaId == null || it.categoriaId in categorias) &&
                (it.movimentacaoContratacaoId == null || it.movimentacaoContratacaoId in movs) && (it.movimentacaoJurosId == null || it.movimentacaoJurosId in movs)
        }
        if (!okV4) throw BackupInvalido("O backup está inconsistente: há empréstimos que apontam para registros que não existem nele.")
        if (!d.pendenciasLevadas.all { it.obrigacaoId in obrigs }) throw BackupInvalido("O backup está inconsistente: há pendências levadas de contas que não existem nele.")
    }

    private fun lerComLimite(entrada: InputStream): ByteArray {
        val saida = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = entrada.read(buffer)
            if (n < 0) break
            total += n
            // Proteção contra arquivos gigantes (ou "bombas de ZIP").
            if (total > LIMITE_BYTES) throw BackupInvalido("O arquivo é grande demais para ser um backup do NK Pay.")
            saida.write(buffer, 0, n)
        }
        return saida.toByteArray()
    }

    // ---------------------------------------------------------------- restaurar

    /**
     * Substitui TODOS os dados do app pelos do backup.
     * 1. Guarda uma cópia automática do estado atual na pasta interna (dá para voltar atrás).
     * 2. Em UMA transação: apaga tudo, insere o backup e registra a restauração na auditoria.
     *    Se qualquer passo falhar, nada muda.
     * @return o arquivo da cópia automática feita antes de restaurar.
     */
    suspend fun restaurar(backup: BackupLido): File {
        val copia = salvarCopiaAutomatica()
        val d = backup.dados
        banco.withTransaction {
            // Apaga de "fora para dentro" (quem aponta antes de quem é apontado)...
            dao.apagarPendenciasLevadas()
            dao.apagarEmprestimos()
            dao.apagarPagamentosDeFatura()
            dao.apagarCompras()
            dao.apagarAjustesDeObrigacao()
            dao.apagarPagamentos()
            dao.apagarObrigacoes()
            dao.apagarContasFixas()
            dao.apagarMovimentacoes()
            dao.apagarContas()
            dao.apagarCategorias()
            dao.apagarAuditoria()
            // ...e insere de "dentro para fora".
            dao.inserirContas(d.contas)
            dao.inserirCategorias(d.categorias)
            dao.inserirMovimentacoes(d.movimentacoes)
            dao.inserirContasFixas(d.contasFixas)
            dao.inserirObrigacoes(d.obrigacoes)
            dao.inserirPagamentos(d.pagamentos)
            dao.inserirAjustesDeObrigacao(d.ajustesDeObrigacao)
            dao.inserirCompras(d.compras)
            dao.inserirPagamentosDeFatura(d.pagamentosDeFatura)
            dao.inserirEmprestimos(d.emprestimos)
            dao.inserirPendenciasLevadas(d.pendenciasLevadas)
            dao.inserirAuditoria(d.auditoria)
            val m = backup.manifesto
            dao.inserirAuditoria(
                listOf(
                    AuditoriaEntidade(
                        id = relogio.novoId(),
                        grupoId = relogio.novoId(),
                        quando = relogio.agora(),
                        operacao = Operacao.RESTAURACAO.name,
                        entidade = "backup",
                        entidadeId = m.sha256Dados.take(12),
                        resumo = "Backup de ${dataHora(m.criadoEm)} restaurado (${m.contas} contas, ${m.movimentacoes} movimentações)",
                        antes = null,
                        depois = json.encodeToString(ManifestoBackup.serializer(), m),
                    ),
                ),
            )
        }
        return copia
    }

    /** Cópias automáticas guardadas na pasta interna, da mais nova para a mais antiga. */
    fun copiasAutomaticas(): List<File> =
        (pastaCopias.listFiles { f -> f.name.endsWith(EXTENSAO) } ?: emptyArray())
            .sortedByDescending { it.name }

    private suspend fun salvarCopiaAutomatica(): File {
        pastaCopias.mkdirs()
        val arquivo = File(pastaCopias, "antes-de-restaurar-${carimbo(relogio.agora())}$EXTENSAO")
        arquivo.outputStream().use { exportar(it, marcarComoUltimo = false) }
        // Mantém só as últimas cópias, para não encher o celular.
        copiasAutomaticas().drop(MAX_COPIAS).forEach { it.delete() }
        return arquivo
    }

    /** Nome sugerido para o arquivo exportado: nk-pay-backup-2026-11-22-1430.nkpay */
    fun nomeSugerido(): String = "nk-pay-backup-${carimbo(relogio.agora())}$EXTENSAO"

    private fun carimbo(millis: Long) =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))

    private fun dataHora(millis: Long) =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val FORMATO = 1
        const val EXTENSAO = ".nkpay"
        private const val MANIFESTO = "manifest.json"
        private const val DADOS = "dados.json"
        private const val LIMITE_BYTES = 200L * 1024 * 1024
        private const val MAX_COPIAS = 5
    }
}
