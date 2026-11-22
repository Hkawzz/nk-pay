package com.nkpay.app.ui.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.nkpay.app.dominio.Dinheiro
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nkpay.app.ui.Formatos
import com.nkpay.app.ui.theme.EstiloValor
import java.time.LocalDate

/**
 * Campo de valor em reais, no estilo dos apps de banco: começa em 0,00 e cada dígito
 * entra pela direita. O texto entregue a [aoMudar] já vem formatado ("1.234,56", ou
 * "-1.234,56" quando o sinal está invertido); a conversão para centavos usa Dinheiro.ler.
 */
@Composable
fun CampoDinheiro(
    rotulo: String,
    texto: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    erro: String? = null,
    ajuda: String? = null,
    permitirNegativo: Boolean = false,
) {
    // Digitação estilo app de banco: os números entram pela direita.
    // 0,00 → 0,01 → 0,12 → 1,27 → 12,78 → 127,80. Apagar faz o caminho inverso.
    val negativo = texto.trim().startsWith("-")
    val centavos = Dinheiro.ler(texto)?.let { if (it < 0) -it else it } ?: 0L
    val exibido = Dinheiro.formatarSemSimbolo(centavos)
    fun emitir(valor: Long, comSinal: Boolean) {
        aoMudar((if (comSinal) "-" else "") + Dinheiro.formatarSemSimbolo(valor))
    }
    OutlinedTextField(
        // O cursor fica sempre no fim: cada dígito novo entra pela direita.
        value = TextFieldValue(exibido, selection = TextRange(exibido.length)),
        onValueChange = { novo ->
            val digitos = novo.text.filter { it.isDigit() }.trimStart('0')
            val valor = if (digitos.isEmpty()) 0L else digitos.take(11).toLong()
            if (valor <= Dinheiro.LIMITE_CENTAVOS) emitir(valor, negativo)
        },
        label = { Text(rotulo) },
        prefix = { Text(if (negativo) "-R$ " else "R$ ") },
        trailingIcon = if (permitirNegativo) {
            {
                TextButton(onClick = { emitir(centavos, !negativo) }) {
                    Text("+/−", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            null
        },
        singleLine = true,
        textStyle = EstiloValor.lista,
        isError = erro != null,
        supportingText = (erro ?: ajuda)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Campo que parece um texto, mas abre uma escolha ao tocar. */
@Composable
private fun CampoClicavel(
    rotulo: String,
    valor: String,
    icone: @Composable () -> Unit,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    erro: String? = null,
) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = {},
            readOnly = true,
            label = { Text(rotulo) },
            trailingIcon = icone,
            singleLine = true,
            isError = erro != null,
            supportingText = erro?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        // Camada transparente por cima: o toque abre a escolha em vez do teclado.
        Box(Modifier.matchParentSize().padding(top = 8.dp).testTag("campo:$rotulo").clickable(onClick = aoClicar))
    }
}

/** Campo de data que abre o calendário do Material. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoData(rotulo: String, data: LocalDate, aoMudar: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var aberto by remember { mutableStateOf(false) }
    CampoClicavel(
        rotulo = rotulo,
        valor = Formatos.data(data),
        icone = { Icon(Icons.Rounded.DateRange, contentDescription = null) },
        aoClicar = { aberto = true },
        modifier = modifier,
    )
    if (aberto) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = Formatos.paraMillisUtc(data))
        DatePickerDialog(
            onDismissRequest = { aberto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { aoMudar(Formatos.deMillisUtc(it)) }
                    aberto = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { aberto = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = estado)
        }
    }
}

/** Uma opção de uma lista de escolha. */
data class Opcao<T>(val valor: T, val rotulo: String, val detalhe: String? = null)

/** Campo que abre uma lista de opções (contas, categorias...). */
@Composable
fun <T> CampoEscolha(
    rotulo: String,
    opcoes: List<Opcao<T>>,
    selecionado: T?,
    aoEscolher: (T) -> Unit,
    modifier: Modifier = Modifier,
    erro: String? = null,
    textoVazio: String = "Escolher",
) {
    var aberto by remember { mutableStateOf(false) }
    val atual = opcoes.firstOrNull { it.valor == selecionado }
    CampoClicavel(
        rotulo = rotulo,
        valor = atual?.rotulo ?: textoVazio,
        icone = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        aoClicar = { aberto = true },
        modifier = modifier,
        erro = erro,
    )
    if (aberto) {
        AlertDialog(
            onDismissRequest = { aberto = false },
            title = { Text(rotulo) },
            text = {
                if (opcoes.isEmpty()) {
                    Text("Nenhuma opção disponível.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(opcoes) { opcao ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { aoEscolher(opcao.valor); aberto = false }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(opcao.rotulo, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    opcao.detalhe?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (opcao.valor == selecionado) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Rounded.Check, contentDescription = "Selecionado", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { aberto = false }) { Text("Fechar") } },
        )
    }
}

/** Botões segmentados para escolher uma entre poucas opções (2 a 4). */
@Composable
fun <T> EscolhaSegmentada(opcoes: List<Opcao<T>>, selecionado: T, aoEscolher: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        opcoes.forEachIndexed { i, opcao ->
            SegmentedButton(
                selected = opcao.valor == selecionado,
                onClick = { aoEscolher(opcao.valor) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = opcoes.size),
                // Sem o ícone de "check": no celular, o espaço é curto para 4 opções.
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                label = {
                    Text(
                        opcao.rotulo,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (opcoes.size > 3) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}
