package com.hkawzz.nkote.ui.backup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hkawzz.nkote.backup.AgendadorDeBackup
import com.hkawzz.nkote.backup.PastaDoSeletor
import com.hkawzz.nkote.dados.ConfiguracaoBackup
import com.hkawzz.nkote.dados.FrequenciaBackup
import com.hkawzz.nkote.ui.Formatos
import com.hkawzz.nkote.ui.LocalContainer
import com.hkawzz.nkote.ui.componentes.EscolhaSegmentada
import com.hkawzz.nkote.ui.componentes.NkBotaoPrimario
import com.hkawzz.nkote.ui.componentes.NkBotaoSecundario
import com.hkawzz.nkote.ui.componentes.NkCabecalhoSecao
import com.hkawzz.nkote.ui.componentes.NkCard
import com.hkawzz.nkote.ui.componentes.Opcao
import com.hkawzz.nkote.ui.theme.Espaco
import com.hkawzz.nkote.ui.theme.RosaDespesa
import com.hkawzz.nkote.ui.theme.VerdeEntrada
import kotlinx.coroutines.launch

// v0.8 (V2, regra 6G.6, ADR 0034): a seção "Backup automático" em Mais → Backup.

/** Texto do último resultado do backup automático. */
fun resumoDoUltimoBackupAutomatico(c: ConfiguracaoBackup): String = when (c.ultimoOk) {
    null -> "Ainda não rodou."
    true -> "Último: ${Formatos.dataHora(c.ultimoEm ?: 0)} · deu certo."
    false -> "Último: ${Formatos.dataHora(c.ultimoEm ?: 0)} · falhou. ${c.ultimaMensagem.orEmpty()}"
}

/** Nome legível da pasta escolhida (o fim do caminho da URI). */
private fun nomeDaPasta(uri: String): String = Uri.decode(uri).substringAfterLast(':').substringAfterLast('/').ifBlank { "Pasta escolhida" }

@Composable
fun SecaoBackupAutomatico() {
    val container = LocalContainer.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val config by container.backupAutomatico.observar().collectAsState(initial = null)
    var rodando by remember { mutableStateOf(false) }

    val escolherPasta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) escopo.launch {
            // Mantém o acesso à pasta depois de reiniciar o celular.
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            container.backupAutomatico.escolherPasta(uri.toString())
            AgendadorDeBackup.agendar(context, container.backupAutomatico.atual().frequencia)
        }
    }

    NkCabecalhoSecao("Backup automático")
    NkCard {
        Column(Modifier.padding(Espaco.l), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
            val c = config ?: return@Column
            Text(
                "O app grava sozinho um backup na pasta escolhida e guarda os 7 mais recentes dessa série. Os backups que você faz à mão nunca são apagados.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                c.pastaUri?.let { "Pasta: ${nomeDaPasta(it)}" } ?: "Nenhuma pasta escolhida ainda.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (c.pastaUri == null) RosaDespesa else MaterialTheme.colorScheme.onSurface,
            )
            NkBotaoSecundario(if (c.pastaUri == null) "Escolher pasta" else "Trocar pasta", { escolherPasta.launch(null) })
            Text("Frequência", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            EscolhaSegmentada(
                FrequenciaBackup.entries.map { Opcao(it, it.rotulo) }, c.frequencia,
                { f -> escopo.launch { container.backupAutomatico.mudarFrequencia(f); if (c.pastaUri != null) AgendadorDeBackup.agendar(context, f) } },
            )
            Text(
                resumoDoUltimoBackupAutomatico(c), style = MaterialTheme.typography.bodySmall,
                color = when (c.ultimoOk) { true -> VerdeEntrada; false -> RosaDespesa; null -> MaterialTheme.colorScheme.onSurfaceVariant },
            )
            NkBotaoPrimario("Fazer agora", {
                rodando = true
                escopo.launch {
                    container.backupAutomatico.executar(c.pastaUri?.let { PastaDoSeletor(context, it) })
                    rodando = false
                }
            }, habilitado = !rodando && c.pastaUri != null)
            Text(
                "Em celulares Samsung, a economia de bateria pode atrasar o backup. Libere o NKote: Configurações → Aplicativos → NKote → Bateria → Sem restrições.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }) {
                Text("Abrir otimização de bateria")
            }
        }
    }
}
