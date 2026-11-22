package com.nkpay.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.nkpay.app.ui.telas.APagarTela
import com.nkpay.app.ui.telas.EmBreveSubtela
import com.nkpay.app.ui.telas.InicioTela
import com.nkpay.app.ui.telas.MaisTela
import com.nkpay.app.ui.telas.MovimentacoesTela
import com.nkpay.app.ui.telas.ObjetivosTela
import com.nkpay.app.ui.telas.SobreTela

/** Estrutura do app: barra inferior com 5 abas e o grafo de navegação. */
@Composable
fun NkPayApp() {
    val navController = rememberNavController()
    val entradaAtual by navController.currentBackStackEntryAsState()
    val destinoAtual = entradaAtual?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Aba.entries.forEach { aba ->
                    val selecionada = destinoAtual?.hierarchy?.any { it.route == aba.rota } == true
                    NavigationBarItem(
                        selected = selecionada,
                        onClick = {
                            navController.navigate(aba.rota) {
                                // Padrão das abas: volta à raiz da aba sem empilhar telas
                                // e preserva o estado de cada aba ao trocar.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(aba.icone, contentDescription = null) },
                        label = {
                            Text(aba.rotulo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { espaco ->
        NavHost(
            navController = navController,
            startDestination = Rotas.INICIO,
            modifier = Modifier.padding(espaco),
        ) {
            composable(Rotas.INICIO) { InicioTela() }
            composable(Rotas.MOVIMENTACOES) { MovimentacoesTela() }
            composable(Rotas.A_PAGAR) { APagarTela() }
            composable(Rotas.OBJETIVOS) { ObjetivosTela() }

            // "Mais" é um subgrafo: as telas internas mantêm a aba "Mais" selecionada.
            navigation(startDestination = "mais/lista", route = Rotas.MAIS) {
                composable("mais/lista") { MaisTela(aoAbrir = { navController.navigate(it) }) }
                composable(Rotas.CONTAS) {
                    EmBreveSubtela("Contas e carteiras", aoVoltar = { navController.popBackStack() })
                }
                composable(Rotas.CATEGORIAS) {
                    EmBreveSubtela("Categorias", aoVoltar = { navController.popBackStack() })
                }
                composable(Rotas.REGISTRO) {
                    EmBreveSubtela("Registro de alterações", aoVoltar = { navController.popBackStack() })
                }
                composable(Rotas.SOBRE) { SobreTela(aoVoltar = { navController.popBackStack() }) }
            }
        }
    }
}
