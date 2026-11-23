package com.nkpay.app.ui.categorias

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nkpay.app.dados.Resultado
import com.nkpay.app.dominio.Aparencia
import com.nkpay.app.dominio.AparenciaSugerida
import com.nkpay.app.dominio.Categoria
import com.nkpay.app.ui.LocalContainer
import com.nkpay.app.ui.componentes.BotaoAdicionar
import com.nkpay.app.ui.componentes.IconeDaCategoria
import com.nkpay.app.ui.componentes.NkBotaoPrimario
import com.nkpay.app.ui.componentes.NkBotaoSecundario
import com.nkpay.app.ui.componentes.NkCabecalhoSecao
import com.nkpay.app.ui.componentes.NkCampo
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.SeletorDeAparencia
import com.nkpay.app.ui.componentes.Subtela
import com.nkpay.app.ui.theme.Espaco
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Categorias: lista com ícone e cor. Tocar abre a tela da categoria (nome, ícone, cor,
 * arquivar). Uma categoria arquivada some das novas escolhas, mas continua nas
 * movimentações antigas.
 */
@Composable
fun CategoriasTela(aoVoltar: () -> Unit, aoAbrir: (String?) -> Unit) {
    val container = LocalContainer.current
    val visao by container.visao.collectAsState()

    Subtela(
        titulo = "Categorias",
        aoVoltar = aoVoltar,
        botaoFlutuante = { BotaoAdicionar("Nova categoria") { aoAbrir(null) } },
    ) { modifier ->
        val v = visao ?: return@Subtela
        val ativas = v.categorias.filter { !it.arquivada }
        val arquivadas = v.categorias.filter { it.arquivada }
        LazyColumn(modifier.fillMaxSize()) {
            item {
                Text(
                    "As categorias dizem o que foi comprado ou recebido. Elas não dependem da forma de pagamento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Espaco.l, vertical = Espaco.xs),
                )
            }
            item { NkCabecalhoSecao("Em uso") }
            item { ListaCategorias(ativas) { aoAbrir(it.id) } }
            if (arquivadas.isNotEmpty()) {
                item { NkCabecalhoSecao("Arquivadas") }
                item { ListaCategorias(arquivadas) { aoAbrir(it.id) } }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun ListaCategorias(categorias: List<Categoria>, aoClicar: (Categoria) -> Unit) {
    NkCard {
        if (categorias.isEmpty()) {
            Text("Nenhuma.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Espaco.m))
        }
        categorias.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { aoClicar(c) }
                    .padding(horizontal = Espaco.m, vertical = Espaco.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconeDaCategoria(c, tamanho = 36.dp)
                Spacer(Modifier.width(Espaco.s + 2.dp))
                Text(c.nome, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (c.ehDoSistema) {
                    Icon(Icons.Rounded.Lock, contentDescription = "Categoria do sistema", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Criar (id nulo) ou editar uma categoria: nome, ícone e cor, e arquivar. */
@Composable
fun CategoriaFormTela(id: String?, aoVoltar: () -> Unit) {
    val container = LocalContainer.current
    val visao by container.visao.collectAsState()
    val escopo = rememberCoroutineScope()
    val inicial = id?.let { visao?.categoria(it) }

    var carregado by rememberSaveable { mutableStateOf(id == null) }
    var nome by rememberSaveable { mutableStateOf("") }
    var icone by rememberSaveable { mutableStateOf(AparenciaSugerida.NEUTRA.icone) }
    var cor by rememberSaveable { mutableStateOf(AparenciaSugerida.NEUTRA.cor) }
    // Numa categoria nova, a aparência acompanha a sugestão do nome até a pessoa escolher outra.
    var escolhida by rememberSaveable { mutableStateOf(id != null) }
    var erro by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(inicial) {
        if (!carregado && inicial != null) {
            nome = inicial.nome
            icone = inicial.icone ?: AparenciaSugerida.NEUTRA.icone
            cor = inicial.cor ?: AparenciaSugerida.NEUTRA.cor
            carregado = true
        }
    }
    val sistema = inicial?.ehDoSistema == true

    Subtela(titulo = if (id == null) "Nova categoria" else if (sistema) inicial?.nome ?: "Categoria" else "Editar categoria", aoVoltar = aoVoltar) { modifier ->
        if (!carregado) return@Subtela
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.l, vertical = Espaco.xs),
            verticalArrangement = Arrangement.spacedBy(Espaco.s),
        ) {
            if (sistema) {
                Text(
                    "Categoria do sistema, usada para juros, multas, tarifas e IOF. O nome não muda, mas o ícone e a cor podem ser trocados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                NkCampo("Nome", nome, { novo ->
                    nome = novo; erro = null
                    if (!escolhida) AparenciaSugerida.paraNome(novo)?.let { icone = it.icone; cor = it.cor }
                }, erro = erro)
            }
            SeletorDeAparencia(
                icone = icone,
                cor = cor,
                aoMudarIcone = { icone = it; escolhida = true },
                aoMudarCor = { cor = it; escolhida = true },
            )
            Spacer(Modifier.height(Espaco.xs))
            NkBotaoPrimario(if (id == null) "Criar categoria" else "Salvar", {
                escopo.launch {
                    val r: Resultado<*> = when {
                        id == null -> container.categorias.criar(nome, Aparencia(icone, cor))
                        sistema -> container.categorias.mudarAparencia(id, icone, cor)
                        else -> when (val r1 = container.categorias.renomear(id, nome)) {
                            is Resultado.Falha -> r1
                            is Resultado.Sucesso -> container.categorias.mudarAparencia(id, icone, cor)
                        }
                    }
                    when (r) {
                        is Resultado.Sucesso -> withContext(Dispatchers.Main) { aoVoltar() }
                        is Resultado.Falha -> erro = r.mensagem
                    }
                }
            })
            if (inicial != null && !sistema) {
                NkBotaoSecundario(if (inicial.arquivada) "Desarquivar" else "Arquivar", {
                    escopo.launch { container.categorias.arquivar(inicial.id, arquivar = !inicial.arquivada); withContext(Dispatchers.Main) { aoVoltar() } }
                })
                Text(
                    "Arquivada, a categoria some das novas escolhas e continua nas movimentações antigas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sistema) erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(Espaco.l))
        }
    }
}
