package com.hkawzz.nkote.ui.backup

import com.hkawzz.nkote.ui.theme.Espaco
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hkawzz.nkote.AppContainer
import com.hkawzz.nkote.dados.BackupLido
import com.hkawzz.nkote.dados.BackupServico
import com.hkawzz.nkote.ui.Formatos
import com.hkawzz.nkote.ui.componentes.NkCard
import com.hkawzz.nkote.ui.componentes.NkCabecalhoSecao
import com.hkawzz.nkote.ui.componentes.Subtela
import com.hkawzz.nkote.ui.componentes.NkBotaoPrimario
import com.hkawzz.nkote.ui.componentes.NkBotaoSecundario
import com.hkawzz.nkote.ui.componentes.lembrarSnackbar
import com.hkawzz.nkote.ui.nkViewModel
import com.hkawzz.nkote.ui.theme.RosaDespesa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration

/** Lê e grava arquivos escolhidos pela pessoa (o app nunca usa internet). */
class BackupViewModel(private val container: AppContainer) : ViewModel() {
    private val servico = container.backup
    val ultimo = servico.registro.ultimo

    var trabalhando by mutableStateOf(false); private set
    var mensagem by mutableStateOf<String?>(null)
    var erro by mutableStateOf<String?>(null)
    /** Backup lido e validado, aguardando a confirmação para restaurar. */
    var paraRestaurar by mutableStateOf<BackupLido?>(null); private set
    var copias by mutableStateOf(servico.copiasAutomaticas()); private set

    fun nomeSugerido() = servico.nomeSugerido()

    fun exportar(context: Context, uri: Uri) = executar {
        val m = withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { servico.exportar(it) }
                ?: throw BackupServico.BackupInvalido("Não foi possível gravar no local escolhido.")
        }
        mensagem = "Backup salvo: ${m.contas} contas e ${m.movimentacoes} movimentações."
    }

    fun abrir(context: Context, uri: Uri) = executar {
        paraRestaurar = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { servico.ler(it) }
                ?: throw BackupServico.BackupInvalido("Não foi possível abrir o arquivo.")
        }
    }

    fun abrirCopia(arquivo: File) = executar {
        paraRestaurar = withContext(Dispatchers.IO) { arquivo.inputStream().use { servico.ler(it) } }
    }

    fun cancelarRestauracao() { paraRestaurar = null }

    fun confirmarRestauracao() {
        val backup = paraRestaurar ?: return
        paraRestaurar = null
        executar {
            withContext(Dispatchers.IO) {
                servico.restaurar(backup)
                // Um backup da v0.3 volta sem ícones: as categorias recebem a sugestão de novo (ADR 0018).
                container.categorias.aplicarAparenciaSugerida()
                container.categorias.garantirJurosRecebidos() // v0.5: um backup antigo não tem essa categoria
            }
            copias = servico.copiasAutomaticas()
            mensagem = "Backup restaurado. O estado anterior foi guardado em “Cópias antes de restaurar”."
        }
    }

    private fun executar(bloco: suspend () -> Unit) {
        trabalhando = true
        erro = null
        viewModelScope.launch {
            try {
                bloco()
            } catch (e: BackupServico.BackupInvalido) {
                erro = e.message
            } catch (e: Exception) {
                erro = "Algo deu errado: ${e.message ?: e.javaClass.simpleName}. Nada foi alterado."
            } finally {
                trabalhando = false
            }
        }
    }
}

/**
 * Mais → Backup e restauração.
 * Exportar grava um arquivo .nkpay onde a pessoa escolher (Drive, Downloads...).
 * Restaurar lê um .nkpay, mostra um resumo e só substitui depois da confirmação.
 */
@Composable
fun BackupTela(aoVoltar: () -> Unit) {
    val vm = nkViewModel { BackupViewModel(it) }
    val context = LocalContext.current
    val ultimo by vm.ultimo.collectAsState()
    val snackbar = lembrarSnackbar()

    val criarArquivo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.exportar(context, uri)
    }
    val abrirArquivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.abrir(context, uri)
    }

    LaunchedEffect(vm.mensagem) { vm.mensagem?.let { snackbar.showSnackbar(it); vm.mensagem = null } }

    Subtela(titulo = "Backup e restauração", aoVoltar = aoVoltar, snackbar = snackbar) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NkCard {
                Column(Modifier.padding(Espaco.l), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                    Text("Último backup", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val u = ultimo
                    Text(
                        if (u == null) "Nenhum backup feito ainda" else Formatos.dataHora(u),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (u == null) RosaDespesa else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Os dados ficam só neste celular. Se ele for perdido ou resetado, só um backup salvo fora dele (Google Drive, computador) recupera tudo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    NkBotaoPrimario("Fazer backup agora", { criarArquivo.launch(vm.nomeSugerido()) }, habilitado = !vm.trabalhando)
                    Text(
                        "Escolha onde salvar. Dica: uma pasta do Google Drive, para ter uma cópia fora do celular.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            NkCabecalhoSecao("Restaurar")
            NkCard {
                Column(Modifier.padding(Espaco.l), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                    Text(
                        "Substitui TODOS os dados do app pelos do arquivo. Antes, o app guarda sozinho uma cópia do estado atual, para você poder voltar atrás.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NkBotaoSecundario("Restaurar de um arquivo (.nkote ou .nkpay)", { abrirArquivo.launch(arrayOf("*/*")) }, habilitado = !vm.trabalhando)
                }
            }

            vm.erro?.let {
                NkCard {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Espaco.l))
                }
            }

            if (vm.copias.isNotEmpty()) {
                NkCabecalhoSecao("Cópias antes de restaurar")
                NkCard {
                    vm.copias.forEachIndexed { i, arquivo ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Formatos.dataHora(arquivo.lastModified()),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.abrirCopia(arquivo) }, enabled = !vm.trabalhando) { Text("Restaurar") }
                        }
                    }
                }
                Text(
                    "Guardadas só dentro do app (as 5 mais recentes). Servem para desfazer uma restauração, não substituem o backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    vm.paraRestaurar?.let { b ->
        val m = b.manifesto
        AlertDialog(
            onDismissRequest = vm::cancelarRestauracao,
            title = { Text("Restaurar este backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Feito em ${Formatos.dataHora(m.criadoEm)} (versão ${m.versaoApp} do app).")
                    Text("${m.contas} contas · ${m.categorias} categorias · ${m.movimentacoes} movimentações · ${m.registrosDeAuditoria} registros de alteração.")
                    Text(
                        "Tudo o que está no app agora será substituído. Uma cópia do estado atual será guardada antes.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::confirmarRestauracao) { Text("Restaurar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = vm::cancelarRestauracao) { Text("Cancelar") } },
        )
    }
}

/** Dias desde o último backup (null se nunca houve). */
fun diasDesde(ultimo: Long?, agora: Long): Long? = ultimo?.let { Duration.ofMillis(agora - it).toDays() }
