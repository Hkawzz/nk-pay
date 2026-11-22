package com.nkpay.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nkpay.app.AppContainer

/**
 * O AppContainer fica disponível para toda a interface por este "CompositionLocal".
 * A MainActivity o fornece; nos testes, dá para fornecer um container com banco em memória.
 */
val LocalContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer não fornecido")
}

/**
 * Cria (ou reaproveita) um ViewModel que recebe o AppContainer.
 * `chave` separa instâncias, por exemplo uma por conta aberta.
 */
@Composable
inline fun <reified VM : ViewModel> nkViewModel(
    chave: String? = null,
    crossinline criar: (AppContainer) -> VM,
): VM {
    val container = LocalContainer.current
    return viewModel(key = chave, factory = viewModelFactory { initializer { criar(container) } })
}
