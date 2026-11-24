package com.hkawzz.nkote.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hkawzz.nkote.NkPayAplicacao
import com.hkawzz.nkote.dados.ArquivoDeBackup
import com.hkawzz.nkote.dados.BackupServico
import com.hkawzz.nkote.dados.FrequenciaBackup
import com.hkawzz.nkote.dados.PastaDeBackup
import java.io.OutputStream
import java.util.concurrent.TimeUnit

// v0.8 (V2, ADR 0034): a parte Android do backup automático. A regra (gravar, guardar os
// últimos 7 da série automática, registrar o resultado) fica em BackupAutomaticoServico.

/** Uma pasta escolhida pelo seletor do Android (Drive permitido), pela URI da árvore. */
class PastaDoSeletor(private val context: Context, uriDaArvore: String) : PastaDeBackup {
    private val arvore = Uri.parse(uriDaArvore)
    private val pasta = DocumentsContract.buildDocumentUriUsingTree(arvore, DocumentsContract.getTreeDocumentId(arvore))

    override fun listar(): List<ArquivoDeBackup> {
        val filhos = DocumentsContract.buildChildDocumentsUriUsingTree(arvore, DocumentsContract.getDocumentId(pasta))
        val lista = mutableListOf<ArquivoDeBackup>()
        context.contentResolver.query(filhos, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) lista += ArquivoDeBackup(c.getString(1) ?: "", c.getString(0))
        }
        return lista
    }

    override fun criar(nome: String): OutputStream {
        val doc = DocumentsContract.createDocument(context.contentResolver, pasta, "application/octet-stream", nome)
            ?: error("a pasta não aceitou um arquivo novo")
        return context.contentResolver.openOutputStream(doc) ?: error("não foi possível abrir o arquivo novo")
    }

    override fun apagar(arquivo: ArquivoDeBackup) {
        DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(arvore, arquivo.id))
    }
}

/** A tarefa periódica: grava o backup na pasta escolhida. Nunca falha "para o sistema": o resultado fica registrado. */
class BackupAutomaticoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? NkPayAplicacao ?: return Result.success()
        val servico = app.container.backupAutomatico
        val uri = servico.atual().pastaUri
        servico.executar(uri?.let { PastaDoSeletor(applicationContext, it) })
        return Result.success()
    }
}

object AgendadorDeBackup {
    private const val NOME = "backup-automatico"

    /** Agenda (ou reagenda) a série automática na frequência escolhida. */
    fun agendar(context: Context, frequencia: FrequenciaBackup) {
        val pedido = PeriodicWorkRequestBuilder<BackupAutomaticoWorker>(frequencia.dias, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NOME, ExistingPeriodicWorkPolicy.UPDATE, pedido)
    }

    /** Extensão usada pela série automática (a mesma dos backups manuais). */
    val extensao: String get() = BackupServico.EXTENSAO
}
