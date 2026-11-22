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
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.nkpay.app.ui.backup.BackupTela
import com.nkpay.app.ui.categorias.CategoriasTela
import com.nkpay.app.ui.contas.ContaDetalheTela
import com.nkpay.app.ui.contas.ContaFormTela
import com.nkpay.app.ui.contas.ContasTela
import com.nkpay.app.ui.telas.APagarTela
import com.nkpay.app.ui.telas.EmBreveSubtela
import com.nkpay.app.ui.inicio.InicioTela
import com.nkpay.app.ui.registro.RegistroDetalheTela
import com.nkpay.app.ui.registro.RegistroTela
import com.nkpay.app.ui.telas.MaisTela
import com.nkpay.app.ui.movimentacoes.MovimentacaoFormTela
import com.nkpay.app.ui.movimentacoes.MovimentacoesTela
import com.nkpay.app.ui.telas.ObjetivosTela
import com.nkpay.app.ui.telas.SobreTela

private const val DURACAO = 260
private const val DURACAO_ABA = 150

/** As telas-raiz de cada aba (trocar entre elas não é "abrir" uma tela nova). */
private val rotasDeAba = Aba.entries.map { it.rota }.toSet() + "mais/lista"

private fun ehAba(entrada: NavBackStackEntry) = entrada.destination.route in rotasDeAba

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
                            // A fonte encolhe só o necessário para "Movimentações" caber inteira.
                            BasicText(
                                aba.rotulo,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    letterSpacing = 0.sp,
                                    color = LocalContentColor.current,
                                ),
                                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
                            )
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
            // Transições curtas (o padrão da biblioteca é um fade de 700 ms, que parece lento):
            // trocar de aba = fade rápido; abrir tela = desliza da direita; voltar = desliza para a direita.
            enterTransition = {
                if (ehAba(initialState) && ehAba(targetState)) fadeIn(tween(DURACAO_ABA))
                else slideInHorizontally(tween(DURACAO, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(DURACAO))
            },
            exitTransition = {
                if (ehAba(initialState) && ehAba(targetState)) fadeOut(tween(DURACAO_ABA))
                else fadeOut(tween(DURACAO / 2))
            },
            popEnterTransition = {
                if (ehAba(initialState) && ehAba(targetState)) fadeIn(tween(DURACAO_ABA))
                else fadeIn(tween(DURACAO))
            },
            popExitTransition = {
                if (ehAba(initialState) && ehAba(targetState)) fadeOut(tween(DURACAO_ABA))
                else slideOutHorizontally(tween(DURACAO, easing = FastOutSlowInEasing)) { it / 5 } + fadeOut(tween(DURACAO))
            },
            modifier = Modifier.padding(espaco),
        ) {
            composable(Rotas.INICIO) {
                InicioTela(
                    aoNovaMovimentacao = { navController.navigate(Rotas.movimentacaoForm()) },
                    aoAbrirMovimentacao = { navController.navigate(Rotas.movimentacaoForm(id = it)) },
                    aoCadastrarConta = { navController.navigate(Rotas.contaForm()) },
                    aoAbrirConta = { navController.navigate(Rotas.conta(it)) },
                    aoFazerBackup = { navController.navigate(Rotas.BACKUP) },
                    aoVerMovimentacoes = {
                        navController.navigate(Rotas.MOVIMENTACOES) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Rotas.MOVIMENTACOES) {
                MovimentacoesTela(
                    aoAbrir = { navController.navigate(Rotas.movimentacaoForm(id = it)) },
                    aoNova = { navController.navigate(Rotas.movimentacaoForm()) },
                )
            }
            composable(Rotas.A_PAGAR) { APagarTela() }
            composable(Rotas.OBJETIVOS) { ObjetivosTela() }

            // "Mais" é um subgrafo: as telas internas mantêm a aba "Mais" selecionada.
            navigation(startDestination = "mais/lista", route = Rotas.MAIS) {
                composable("mais/lista") { MaisTela(aoAbrir = { navController.navigate(it) }) }
                composable(Rotas.CONTAS) {
                    ContasTela(
                        aoVoltar = { navController.popBackStack() },
                        aoAbrirConta = { navController.navigate(Rotas.conta(it)) },
                        aoNovaConta = { navController.navigate(Rotas.contaForm()) },
                    )
                }
                composable(Rotas.CONTA, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
                    val id = entrada.arguments?.getString("id").orEmpty()
                    ContaDetalheTela(
                        contaId = id,
                        aoVoltar = { navController.popBackStack() },
                        aoEditar = { navController.navigate(Rotas.contaForm(id)) },
                        aoAbrirMovimentacao = { navController.navigate(Rotas.movimentacaoForm(id = it)) },
                        aoNovaMovimentacao = { navController.navigate(Rotas.movimentacaoForm(conta = id)) },
                    )
                }
                composable(
                    Rotas.CONTA_FORM,
                    arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { entrada ->
                    ContaFormTela(contaId = entrada.arguments?.getString("id"), aoVoltar = { navController.popBackStack() })
                }
                composable(Rotas.CATEGORIAS) { CategoriasTela(aoVoltar = { navController.popBackStack() }) }
                composable(Rotas.REGISTRO) {
                    RegistroTela(
                        aoVoltar = { navController.popBackStack() },
                        aoAbrir = { navController.navigate(Rotas.registroDetalhe(it)) },
                    )
                }
                composable(Rotas.REGISTRO_DETALHE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
                    RegistroDetalheTela(id = entrada.arguments?.getString("id").orEmpty(), aoVoltar = { navController.popBackStack() })
                }
                composable(Rotas.SOBRE) { SobreTela(aoVoltar = { navController.popBackStack() }) }
                composable(Rotas.BACKUP) { BackupTela(aoVoltar = { navController.popBackStack() }) }
            }

            composable(
                Rotas.MOVIMENTACAO_FORM,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("conta") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entrada ->
                MovimentacaoFormTela(
                    id = entrada.arguments?.getString("id"),
                    contaInicial = entrada.arguments?.getString("conta"),
                    aoVoltar = { navController.popBackStack() },
                )
            }
        }
    }
}
