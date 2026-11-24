package com.hkawzz.nkote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.hkawzz.nkote.ui.LocalContainer
import com.hkawzz.nkote.ui.NkPayApp
import com.hkawzz.nkote.ui.theme.NkPayTema

/** A única Activity do app. Toda a interface é desenhada em Compose. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras do sistema escuras com ícones claros, combinando com o tema único.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as NkPayAplicacao).container
        setContent {
            NkPayTema {
                CompositionLocalProvider(LocalContainer provides container) {
                    NkPayApp()
                }
            }
        }
    }
}
