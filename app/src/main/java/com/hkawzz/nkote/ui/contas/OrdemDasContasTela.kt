package com.hkawzz.nkote.ui.contas

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import com.hkawzz.nkote.dominio.Conta
import com.hkawzz.nkote.dominio.Natureza
import com.hkawzz.nkote.ui.LocalContainer
import com.hkawzz.nkote.ui.componentes.NkBotaoPrimario
import com.hkawzz.nkote.ui.componentes.NkCabecalhoSecao
import com.hkawzz.nkote.ui.componentes.NkCard
import com.hkawzz.nkote.ui.componentes.NkIconeRedondo
import com.hkawzz.nkote.ui.componentes.Subtela
import com.hkawzz.nkote.ui.componentes.aparenciaDaConta
import com.hkawzz.nkote.ui.theme.Espaco
import kotlinx.coroutines.launch

// v0.9 (item 5, ADR 0035): "Editar ordem" das contas e dos benefícios. Arrastar pela alça
// (implementação própria, sem biblioteca) ou usar as setas. Nada é gravado antes de "Salvar".

@Composable
fun OrdemDasContasTela(aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val visao by container.visao.collectAsState()
    val escopo = rememberCoroutineScope()
    val v = visao ?: return
    val ativas = v.contas.filter { !it.arquivada }
    val proprias = remember { mutableStateListOf<Conta>().apply { addAll(ativas.filter { it.natureza == Natureza.PROPRIO }) } }
    val beneficios = remember { mutableStateListOf<Conta>().apply { addAll(ativas.filter { it.natureza == Natureza.BENEFICIO }) } }
    var salvando by remember { mutableStateOf(false) }

    Subtela(titulo = "Editar ordem", aoVoltar = aoVoltar) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                "Arraste pela alça (≡) ou use as setas. A ordem vale na Início e nas listas de contas.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Espaco.l, vertical = Espaco.xs),
            )
            if (proprias.isNotEmpty()) {
                NkCabecalhoSecao("Minhas contas")
                ListaReordenavel(proprias)
            }
            if (beneficios.isNotEmpty()) {
                NkCabecalhoSecao("Benefícios")
                ListaReordenavel(beneficios)
            }
            NkBotaoPrimario(
                "Salvar ordem",
                {
                    salvando = true
                    escopo.launch {
                        container.contas.reordenar(proprias.map { it.id } + beneficios.map { it.id })
                        aoVoltar()
                    }
                },
                modifier = Modifier.padding(Espaco.l),
                habilitado = !salvando,
            )
        }
    }
}

@Composable
private fun ListaReordenavel(lista: SnapshotStateList<Conta>) {
    var arrastando by remember { mutableStateOf<String?>(null) }
    var deslocamento by remember { mutableFloatStateOf(0f) }
    var alturaItem by remember { mutableFloatStateOf(1f) }

    fun mover(de: Int, para: Int) {
        if (para !in lista.indices || de == para) return
        lista.add(para, lista.removeAt(de))
    }

    NkCard {
        Column(verticalArrangement = Arrangement.spacedBy(Espaco.xxs)) {
            lista.forEachIndexed { i, c ->
                val ativo = arrastando == c.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { if (it.height > 0) alturaItem = it.height.toFloat() }
                        .zIndex(if (ativo) 1f else 0f)
                        .graphicsLayer { translationY = if (ativo) deslocamento else 0f }
                        .then(if (ativo) Modifier.shadow(Espaco.xs) else Modifier)
                        .padding(horizontal = Espaco.m, vertical = Espaco.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Menu, contentDescription = "Arrastar ${c.nome}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.pointerInput(c.id) {
                            detectDragGestures(
                                onDragStart = { arrastando = c.id; deslocamento = 0f },
                                onDragEnd = { arrastando = null; deslocamento = 0f },
                                onDragCancel = { arrastando = null; deslocamento = 0f },
                            ) { mudanca, arraste ->
                                mudanca.consume()
                                deslocamento += arraste.y
                                val atual = lista.indexOfFirst { it.id == c.id }
                                if (deslocamento > alturaItem / 2 && atual < lista.lastIndex) {
                                    mover(atual, atual + 1); deslocamento -= alturaItem
                                } else if (deslocamento < -alturaItem / 2 && atual > 0) {
                                    mover(atual, atual - 1); deslocamento += alturaItem
                                }
                            }
                        },
                    )
                    Spacer(Modifier.width(Espaco.s))
                    val (icone, cor) = aparenciaDaConta(c)
                    NkIconeRedondo(icone, cor)
                    Spacer(Modifier.width(Espaco.s))
                    Text(c.nome, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).semantics { contentDescription = "Posição ${i + 1}: ${c.nome}" })
                    IconButton(onClick = { mover(i, i - 1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Subir ${c.nome}") }
                    IconButton(onClick = { mover(i, i + 1) }, enabled = i < lista.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Descer ${c.nome}") }
                }
            }
        }
    }
}
