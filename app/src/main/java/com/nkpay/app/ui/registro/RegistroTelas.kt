package com.nkpay.app.ui.registro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nkpay.app.dados.AuditoriaEntidade
import com.nkpay.app.dados.Operacao
import com.nkpay.app.dominio.Dinheiro
import com.nkpay.app.dominio.Natureza
import com.nkpay.app.dominio.TipoBeneficio
import com.nkpay.app.dominio.TipoMovimentacao
import com.nkpay.app.dominio.VisaoFinanceira
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.componentes.CartaoSecao
import com.nkpay.app.ui.componentes.EstadoVazio
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.theme.EstiloValor
import com.nkpay.app.ui.theme.RosaDespesa
import com.nkpay.app.ui.theme.VerdeEntrada
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val NOME_ENTIDADE = mapOf("conta" to "Conta", "categoria" to "Categoria", "movimentacao" to "Movimentação")

private fun rotuloOperacao(op: String) = runCatching { Operacao.valueOf(op).rotulo }.getOrDefault(op)

/** Lista do Registro de alterações: do mais recente para o mais antigo. */
@Composable
fun RegistroTela(aoVoltar: () -> Unit, aoAbrir: (String) -> Unit) {
    val container = LocalContainer.current
    val fluxo = remember(container) { container.auditoria.observar() }
    val registros by fluxo.collectAsState(initial = null)
    Subtela(titulo = "Registro de alterações", aoVoltar = aoVoltar) { modifier ->
        val lista = registros ?: return@Subtela
        if (lista.isEmpty()) {
            EstadoVazio(
                icone = Icons.Filled.Edit,
                titulo = "Nada registrado ainda",
                texto = "Toda criação, edição e exclusão aparece aqui, com o antes e o depois.",
                modifier = modifier,
            )
            return@Subtela
        }
        LazyColumn(modifier.fillMaxSize()) {
            item {
                Text(
                    "Cada alteração é gravada junto com o próprio registro, e esta lista nunca é apagada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            items(lista, key = { it.id }) { r ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { aoAbrir(r.id) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(r.resumo, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${rotuloOperacao(r.operacao)} · ${NOME_ENTIDADE[r.entidade] ?: r.entidade} · ${Formatos.dataHora(r.quando)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = corDaOperacao(r.operacao),
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun corDaOperacao(op: String) = when (op) {
    Operacao.CRIACAO.name -> VerdeEntrada
    Operacao.EXCLUSAO.name -> RosaDespesa
    else -> MaterialTheme.colorScheme.primary
}

/** Detalhe de um registro: cada campo com o valor antes e depois. Os campos alterados ficam em destaque. */
@Composable
fun RegistroDetalheTela(id: String, aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val visao by container.visao.collectAsState()
    val registro by produceState<AuditoriaEntidade?>(null, id) { value = container.auditoria.buscar(id) }

    Subtela(titulo = "Alteração", aoVoltar = aoVoltar) { modifier ->
        val r = registro ?: return@Subtela
        val v = visao ?: VisaoFinanceira.VAZIA
        val antes = lerJson(r.antes)
        val depois = lerJson(r.depois)
        val campos = (antes.keys + depois.keys).distinct().filter { it !in CAMPOS_OCULTOS }
            .filter { !(antes[it].ehNulo() && depois[it].ehNulo()) }
        val alterados = campos.filter { antes[it] != depois[it] }
        val ordenados = alterados + (campos - alterados.toSet())

        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CartaoSecao {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.resumo, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${rotuloOperacao(r.operacao)} · ${NOME_ENTIDADE[r.entidade] ?: r.entidade}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = corDaOperacao(r.operacao),
                    )
                    Text(Formatos.dataHora(r.quando), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            CartaoSecao {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text("Campo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("Antes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.2f))
                        Text("Depois", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.2f))
                    }
                    for (campo in ordenados) {
                        val mudou = campo in alterados
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text(
                                ROTULOS[campo] ?: campo,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (mudou) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (mudou) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            val estilo = EstiloValor.lista.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
                            Text(
                                if (r.antes == null) "—" else formatarCampo(campo, antes[campo], v),
                                style = estilo,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1.2f),
                            )
                            Text(
                                formatarCampo(campo, depois[campo], v),
                                style = estilo,
                                color = if (mudou) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1.2f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun lerJson(texto: String?): JsonObject =
    texto?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: JsonObject(emptyMap())

private fun kotlinx.serialization.json.JsonElement?.ehNulo() = this == null || this is JsonNull

/** Campos técnicos que não ajudam a entender a alteração. */
private val CAMPOS_OCULTOS = setOf("id")

private val ROTULOS = mapOf(
    "nome" to "Nome",
    "natureza" to "Tipo de conta",
    "tipoBeneficio" to "Benefício",
    "saldoInicialCentavos" to "Saldo inicial",
    "dataSaldoInicial" to "Data do saldo inicial",
    "icone" to "Ícone",
    "cor" to "Cor",
    "instituicao" to "Instituição",
    "limiteCentavos" to "Limite",
    "diaFechamento" to "Dia de fechamento",
    "diaVencimento" to "Dia de vencimento",
    "arquivadaEm" to "Arquivada em",
    "criadoEm" to "Criado em",
    "atualizadoEm" to "Atualizado em",
    "excluidoEm" to "Excluído em",
    "codigoSistema" to "Código do sistema",
    "tipo" to "Tipo",
    "data" to "Data",
    "valorCentavos" to "Valor",
    "contaId" to "Conta",
    "contaDestinoId" to "Conta de destino",
    "categoriaId" to "Categoria",
    "descricao" to "Descrição",
)

/** Deixa o valor legível: centavos em reais, instantes em data e hora, ids em nomes. */
private fun formatarCampo(campo: String, valor: kotlinx.serialization.json.JsonElement?, v: VisaoFinanceira): String {
    if (valor == null || valor is JsonNull) return "—"
    val p = valor as? JsonPrimitive ?: return valor.toString()
    val texto = p.content
    return when (campo) {
        "saldoInicialCentavos", "valorCentavos", "limiteCentavos" -> texto.toLongOrNull()?.let { Dinheiro.formatar(it) } ?: texto
        "arquivadaEm", "criadoEm", "atualizadoEm", "excluidoEm" -> texto.toLongOrNull()?.let { Formatos.dataHora(it) } ?: texto
        "dataSaldoInicial", "data" -> runCatching { Formatos.data(java.time.LocalDate.parse(texto)) }.getOrDefault(texto)
        "natureza" -> runCatching { Natureza.valueOf(texto).rotulo }.getOrDefault(texto)
        "tipoBeneficio" -> runCatching { TipoBeneficio.valueOf(texto).rotulo }.getOrDefault(texto)
        "tipo" -> runCatching { TipoMovimentacao.valueOf(texto).rotulo }.getOrDefault(texto)
        "contaId", "contaDestinoId" -> v.conta(texto)?.nome ?: "(conta excluída)"
        "categoriaId" -> v.categoria(texto)?.nome ?: "(categoria)"
        "cor" -> "#" + (texto.toLongOrNull()?.let { java.lang.Long.toHexString(it).uppercase().takeLast(6) } ?: texto)
        "descricao" -> texto.ifBlank { "—" }
        else -> texto
    }
}
