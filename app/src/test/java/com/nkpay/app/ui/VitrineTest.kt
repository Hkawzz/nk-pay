package com.nkpay.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.nkpay.app.ui.componentes.EstiloCard
import com.nkpay.app.ui.componentes.NkBotaoPrimario
import com.nkpay.app.ui.componentes.NkBotaoSecundario
import com.nkpay.app.ui.componentes.NkCabecalhoSecao
import com.nkpay.app.ui.componentes.NkCampo
import com.nkpay.app.ui.componentes.NkCard
import com.nkpay.app.ui.componentes.NkDivisor
import com.nkpay.app.ui.componentes.NkIconeRedondo
import com.nkpay.app.ui.componentes.NkListItem
import com.nkpay.app.ui.componentes.NkTextoAjuda
import com.nkpay.app.ui.componentes.NkTituloTela
import com.nkpay.app.ui.componentes.NkValor
import com.nkpay.app.ui.componentes.NkValorRotulado
import com.nkpay.app.ui.componentes.TamanhoValor
import com.nkpay.app.ui.componentes.TipoValor
import com.nkpay.app.ui.theme.Espaco
import com.nkpay.app.ui.theme.NkPayTema
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Gera uma captura com todos os componentes Nk*, para revisar o visual em um lugar só. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class VitrineTest {
    @get:Rule val regra = createComposeRule()

    @Test
    fun vitrineDeComponentes() {
        regra.setContent {
            NkPayTema {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        NkTituloTela("Componentes")
                        NkCard(estilo = EstiloCard.DESTAQUE) {
                            Column(Modifier.padding(Espaco.l)) {
                                NkValorRotulado("Disponível", 150_000, tamanho = TamanhoValor.DESTAQUE, corRotulo = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        NkCabecalhoSecao("Valores") { NkValor(43_000, tipo = TipoValor.SECUNDARIO, tamanho = TamanhoValor.PEQUENO) }
                        NkCard {
                            Row(Modifier.padding(Espaco.m)) {
                                NkValorRotulado("Entradas", 50_000, Modifier.weight(1f), tipo = TipoValor.NEUTRO, corValor = com.nkpay.app.ui.theme.VerdeEntrada)
                                NkValorRotulado("Despesas", 8_000, Modifier.weight(1f), corValor = com.nkpay.app.ui.theme.RosaDespesa)
                            }
                        }
                        NkCabecalhoSecao("Lista")
                        NkCard {
                            NkListItem("Mercado", subtitulo = "Alimentação · Pix", inicio = { NkIconeRedondo(Icons.AutoMirrored.Rounded.List) }, fim = { NkValor(2_500, tipo = TipoValor.DESPESA) }, aoClicar = {})
                            NkDivisor()
                            NkListItem("Salário", subtitulo = "Pix", inicio = { NkIconeRedondo(Icons.Rounded.Star) }, fim = { NkValor(300_000, tipo = TipoValor.ENTRADA) }, aoClicar = {})
                            NkDivisor()
                            NkListItem("Ajuste de saldo", subtitulo = "Dinheiro", fim = { NkValor(-1_270, tipo = TipoValor.COM_SINAL) })
                        }
                        NkCard(estilo = EstiloCard.ELEVADO) {
                            NkTextoAjuda("Card elevado, com texto de ajuda.")
                        }
                        Column(Modifier.padding(Espaco.m)) {
                            NkCampo("Nome", "Pix", {}, ajuda = "Ex.: Pix, Dinheiro")
                            NkBotaoPrimario("Botão primário", {})
                            Row(Modifier.padding(top = Espaco.xs)) {}
                            NkBotaoSecundario("Botão secundário", {})
                        }
                    }
                }
            }
        }
        regra.waitForIdle()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/00-vitrine-componentes.png")
    }
}
