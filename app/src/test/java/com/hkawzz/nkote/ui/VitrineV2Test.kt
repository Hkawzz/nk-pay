package com.hkawzz.nkote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.hkawzz.nkote.ui.componentes.NkCardConta
import com.hkawzz.nkote.ui.componentes.NkCardDestaque
import com.hkawzz.nkote.ui.componentes.NkCartaoCredito
import com.hkawzz.nkote.ui.componentes.NkIconeComFundo
import com.hkawzz.nkote.ui.componentes.NkValorAjustavel
import com.hkawzz.nkote.ui.icones.IconesNk
import com.hkawzz.nkote.ui.theme.Espaco
import com.hkawzz.nkote.ui.theme.EstiloValor
import com.hkawzz.nkote.ui.theme.NkPayTema
import com.hkawzz.nkote.ui.theme.PaletaNk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Vitrine do design system v2, em três versões do Android: a mais nova e as antigas
 * (8 e 9), onde os efeitos usam as alternativas (ADR 0019). Dados fictícios.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VitrineV2Test {
    @get:Rule val regra = createComposeRule()

    @OptIn(ExperimentalLayoutApi::class)
    private fun vitrine(nome: String) {
        regra.setContent {
            NkPayTema {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        NkCardDestaque {
                            Column(Modifier.fillMaxSize().padding(Espaco.l), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Disponível", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                NkValorAjustavel(1_234_567, estilo = EstiloValor.destaque, alinhamento = TextAlign.Center)
                            }
                        }
                        Row(Modifier.padding(Espaco.m), horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
                            NkCardConta("Banco Exemplo", 1_234_567, IconesNk.de("banco"), Color(PaletaNk[0]), Modifier.weight(1f)) {}
                            NkCardConta("Carteira com nome bem comprido", 9_500, IconesNk.de("carteira"), Color(PaletaNk[2]), Modifier.weight(1f)) {}
                        }
                        Row(Modifier.padding(horizontal = Espaco.m), horizontalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                            NkCardConta("Vale A", 33_410, IconesNk.de("carrinho"), Color(PaletaNk[5]), Modifier.weight(1f), compacto = true) {}
                            NkCardConta("Vale R", 0, IconesNk.de("talheres"), Color(PaletaNk[8]), Modifier.weight(1f), compacto = true) {}
                            NkCardConta("Outro", 49_000, IconesNk.de("presente"), Color(PaletaNk[10]), Modifier.weight(1f), compacto = true) {}
                        }
                        NkCartaoCredito("Cartão Exemplo", "Fecha dia 5 · vence dia 12", 48_000, 200_000)
                        FlowRow(Modifier.padding(Espaco.m), horizontalArrangement = Arrangement.spacedBy(Espaco.xs), verticalArrangement = Arrangement.spacedBy(Espaco.xs)) {
                            IconesNk.todos.forEachIndexed { i, ic -> NkIconeComFundo(ic.vetor, Color(PaletaNk[i % PaletaNk.size]), tamanho = 40.dp) }
                        }
                    }
                }
            }
        }
        regra.waitForIdle()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/v2-vitrine-$nome.png")
    }

    @Test @Config(qualifiers = "w411dp-h1100dp-xxhdpi")
    fun vitrineAndroidNovo() = vitrine("android-novo")

    @Test @Config(sdk = [26], qualifiers = "w411dp-h1100dp-xxhdpi")
    fun vitrineAndroid8() = vitrine("android-8")

    @Test @Config(sdk = [28], qualifiers = "w411dp-h1100dp-xxhdpi")
    fun vitrineAndroid9() = vitrine("android-9")
}
