package com.hkawzz.nkote.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkawzz.nkote.ui.telas.Contato
import com.hkawzz.nkote.ui.telas.SobreTela
import com.hkawzz.nkote.ui.theme.NkPayTema
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v0.8 (G): Mais → Sobre mostra o nome NKote, a privacidade e o e-mail de suporte, sem botão Apoiar. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SobreUiTest {
    @get:Rule val regra = createComposeRule()

    @Test
    fun `sobre mostra privacidade e suporte e nao tem apoiar`() {
        regra.setContent { NkPayTema { SobreTela(aoVoltar = {}) } }
        regra.onNodeWithContentDescription("NKote").assertExists() // v0.8.1: a assinatura do logo
        regra.onNodeWithText("Política de privacidade").assertExists()
        regra.onNodeWithText(Contato.TEXTO_PROVISORIO_PRIVACIDADE).assertExists()
        regra.onNodeWithText("hkawzzsuporte@gmail.com").assertExists()
        regra.onAllNodesWithText("Apoiar", substring = true, ignoreCase = true).assertCountEquals(0)
    }
}
