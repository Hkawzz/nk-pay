package com.nkpay.app.ui

import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
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
import com.nkpay.app.ui.apagar.APagarTela
import com.nkpay.app.ui.apagar.ContaFixaFormTela
import com.nkpay.app.ui.apagar.ContasFixasTela
import com.nkpay.app.ui.apagar.ObrigacaoFormTela
import com.nkpay.app.ui.apagar.PagarTela
import com.nkpay.app.ui.telas.EmBreveSubtela
import com.nkpay.app.ui.inicio.InicioTela
import com.nkpay.app.ui.registro.RegistroDetalheTela
import com.nkpay.app.ui.registro.RegistroTela
import com.nkpay.app.ui.telas.MaisTela
import com.nkpay.app.ui.movimentacoes.MovimentacaoFormTela
import com.nkpay.app.ui.movimentacoes.MovimentacoesTela
import com.nkpay.app.ui.telas.SobreTela

private const val DURACAO = 220
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
                                // Exceção: a aba Mais sempre reabre no menu principal
                                // (docs/decisoes/0011-aba-mais-volta-ao-menu.md).
                                restoreState = aba != Aba.MAIS
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
                                    // Item ativo com o rótulo mais forte, além do indicador roxo.
                                    fontWeight = if (selecionada) FontWeight.SemiBold else FontWeight.Normal,
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
            composable(Rotas.A_PAGAR) { entrada ->
                // v0.6: o resultado do Preparar mês volta para cá como um aviso.
                val avisoPreparo by entrada.savedStateHandle.getStateFlow<String?>("aviso", null).collectAsState()
                APagarTela(
                    avisoExterno = avisoPreparo,
                    aoPreparar = { mes -> navController.navigate(Rotas.prepararMes(mes)) },
                    aoNova = { mes -> navController.navigate(Rotas.obrigacaoForm(mes = mes)) },
                    aoAbrir = { navController.navigate(Rotas.obrigacaoForm(id = it)) },
                    aoContasFixas = { navController.navigate(Rotas.CONTAS_FIXAS) },
                    aoAbrirFatura = { conta, mes -> navController.navigate(Rotas.fatura(conta, mes)) },
                    aoAbrirParcelaEmprestimo = { navController.navigate("parcela-emprestimo/$it") },
                )
            }
            composable(
                Rotas.OBRIGACAO_FORM,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("mes") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entrada ->
                val mes = entrada.arguments?.getString("mes")?.let { java.time.YearMonth.parse(it) }
                    ?: java.time.YearMonth.from(LocalContainer.current.relogio.hoje())
                ObrigacaoFormTela(
                    id = entrada.arguments?.getString("id"),
                    mesInicial = mes,
                    aoVoltar = { navController.popBackStack() },
                    aoPagar = { navController.navigate(Rotas.pagar(it)) },
                )
            }
            composable(Rotas.PAGAR, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
                PagarTela(obrigacaoId = entrada.arguments?.getString("id").orEmpty(), aoVoltar = { navController.popBackStack() })
            }
            composable(
                Rotas.FATURA,
                arguments = listOf(navArgument("conta") { type = NavType.StringType }, navArgument("mes") { type = NavType.StringType }),
            ) { entrada ->
                com.nkpay.app.ui.credito.FaturaTela(
                    contaId = entrada.arguments?.getString("conta").orEmpty(),
                    mes = java.time.YearMonth.parse(entrada.arguments?.getString("mes")),
                    aoVoltar = { navController.popBackStack() },
                    aoAbrirParcela = { navController.navigate(Rotas.parcela(it)) },
                )
            }
            composable(Rotas.PREPARAR_MES, arguments = listOf(navArgument("mes") { type = NavType.StringType })) { entrada ->
                com.nkpay.app.ui.apagar.PrepararMesTela(
                    mes = java.time.YearMonth.parse(entrada.arguments?.getString("mes")),
                    aoVoltar = { navController.popBackStack() },
                    aoTerminar = { aviso ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("aviso", aviso)
                        navController.popBackStack()
                    },
                )
            }
            // v0.5: a parcela de empréstimo aberta a partir de A pagar (fora de Mais, para a aba continuar A pagar).
            composable("parcela-emprestimo/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
                com.nkpay.app.ui.emprestimos.ParcelaEmprestimoTela(obrigacaoId = entrada.arguments?.getString("id").orEmpty(), aoVoltar = { navController.popBackStack() })
            }
            composable(Rotas.OBJETIVOS) {
                com.nkpay.app.ui.objetivos.ObjetivosTela(aoAbrir = { navController.navigate(Rotas.objetivo(it)) }, aoNovo = { navController.navigate(Rotas.objetivoForm()) })
            }
            composable(Rotas.OBJETIVO, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
                val id = entrada.arguments?.getString("id").orEmpty()
                com.nkpay.app.ui.objetivos.ObjetivoTela(
                    id = id,
                    aoVoltar = { navController.popBackStack() },
                    aoEditar = { navController.navigate(Rotas.objetivoForm(id)) },
                    aoOperar = { tipo -> navController.navigate(Rotas.objetivoOperacao(id, tipo)) },
                )
            }
            composable(Rotas.OBJETIVO_FORM, arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) { entrada ->
                com.nkpay.app.ui.objetivos.ObjetivoFormTela(id = entrada.arguments?.getString("id"), aoVoltar = { navController.popBackStack() })
            }
            composable(
                Rotas.OBJETIVO_OPERACAO,
                arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("tipo") { type = NavType.StringType }),
            ) { entrada ->
                com.nkpay.app.ui.objetivos.OperacaoObjetivoTela(
                    id = entrada.arguments?.getString("id").orEmpty(),
                    tipo = com.nkpay.app.dominio.TipoMovimentoReserva.valueOf(entrada.arguments?.getString("tipo") ?: "GUARDAR"),
                    aoVoltar = { navController.popBackStack() },
                )
            }

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
                composable(Rotas.EMPRESTIMOS) {
                    com.nkpay.app.ui.emprestimos.EmprestimosTela(
                        aoVoltar = { navController.popBackStack() },
                        aoAbrir = { navController.navigate(Rotas.emprestimo(it)) },
                        aoNovo = { navController.navigate(Rotas.emprestimoForm(it)) },
                        aoPessoas = { navController.navigate(Rotas.PESSOAS) },
                    )
                }
                composable(Rotas.PESSOAS) {
                    com.nkpay.app.ui.emprestimos.PessoasTela(aoVoltar = { navController.popBackStack() }, aoAbrir = { navController.navigate(Rotas.pessoa(it)) })
                }
                composable(Rotas.PESSOA, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
                    com.nkpay.app.ui.emprestimos.PessoaTela(id = entrada.arguments?.getString("id").orEmpty(), aoVoltar = { navController.popBackStack() })
                }
                composable(Rotas.EMPRESTIMO_FORM, arguments = listOf(navArgument("tipo") { type = NavType.StringType; defaultValue = "RECEBIDO" })) { entrada ->
                    com.nkpay.app.ui.emprestimos.EmprestimoFormTela(
                        tipoInicial = com.nkpay.app.dominio.TipoEmprestimo.valueOf(entrada.arguments?.getString("tipo") ?: "RECEBIDO"),
                        aoVoltar = { navController.popBackStack() },
                    )
                }
                composable(Rotas.EMPRESTIMO, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
                    com.nkpay.app.ui.emprestimos.EmprestimoTela(
                        id = entrada.arguments?.getString("id").orEmpty(),
                        aoVoltar = { navController.popBackStack() },
                        aoAbrirParcela = { navController.navigate(Rotas.emprestimoParcela(it)) },
                    )
                }
                composable(Rotas.EMPRESTIMO_PARCELA, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
                    com.nkpay.app.ui.emprestimos.ParcelaEmprestimoTela(obrigacaoId = entrada.arguments?.getString("id").orEmpty(), aoVoltar = { navController.popBackStack() })
                }
                composable(Rotas.ANALISES) { com.nkpay.app.ui.analises.AnalisesTela(aoVoltar = { navController.popBackStack() }) }
                composable(Rotas.CARTOES) {
                    com.nkpay.app.ui.credito.CartoesTela(
                        aoVoltar = { navController.popBackStack() },
                        aoAbrir = { navController.navigate(Rotas.cartao(it)) },
                        aoNovo = { navController.navigate(Rotas.cartaoForm()) },
                    )
                }
                composable(
                    Rotas.CARTAO_FORM,
                    arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { entrada ->
                    com.nkpay.app.ui.credito.ContaCreditoFormTela(id = entrada.arguments?.getString("id"), aoVoltar = { navController.popBackStack() })
                }
                composable(Rotas.CARTAO, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
                    val id = entrada.arguments?.getString("id").orEmpty()
                    com.nkpay.app.ui.credito.ContaCreditoTela(
                        id = id,
                        aoVoltar = { navController.popBackStack() },
                        aoEditar = { navController.navigate(Rotas.cartaoForm(id)) },
                        aoAbrirFatura = { navController.navigate(Rotas.fatura(id, it)) },
                        conteudoExtra = { vc, conta -> com.nkpay.app.ui.credito.SecaoCompras(vc, conta) { navController.navigate(Rotas.compra(it)) } },
                        botaoFlutuante = { com.nkpay.app.ui.componentes.BotaoAdicionar("Nova compra") { navController.navigate(Rotas.compraForm(id)) } },
                    )
                }
                composable(Rotas.COMPRA_FORM, arguments = listOf(navArgument("conta") { type = NavType.StringType; defaultValue = "" })) { entrada ->
                    com.nkpay.app.ui.credito.CompraFormTela(contaId = entrada.arguments?.getString("conta").orEmpty(), aoVoltar = { navController.popBackStack() })
                }
                composable(Rotas.COMPRA, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
                    com.nkpay.app.ui.credito.CompraTela(
                        id = entrada.arguments?.getString("id").orEmpty(),
                        aoVoltar = { navController.popBackStack() },
                        aoAbrirParcela = { navController.navigate(Rotas.parcela(it)) },
                    )
                }
                composable(Rotas.PARCELA, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
                    com.nkpay.app.ui.credito.ParcelaTela(obrigacaoId = entrada.arguments?.getString("id").orEmpty(), aoVoltar = { navController.popBackStack() })
                }
                composable(Rotas.CATEGORIAS) {
                    CategoriasTela(aoVoltar = { navController.popBackStack() }, aoAbrir = { navController.navigate(Rotas.categoriaForm(it)) })
                }
                composable(
                    Rotas.CATEGORIA_FORM,
                    arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { entrada ->
                    com.nkpay.app.ui.categorias.CategoriaFormTela(id = entrada.arguments?.getString("id"), aoVoltar = { navController.popBackStack() })
                }
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
                composable(Rotas.CONTAS_FIXAS) {
                    ContasFixasTela(
                        aoVoltar = { navController.popBackStack() },
                        aoAbrir = { navController.navigate(Rotas.contaFixaForm(it)) },
                        aoNova = { navController.navigate(Rotas.contaFixaForm()) },
                    )
                }
                composable(
                    Rotas.CONTA_FIXA_FORM,
                    arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { entrada ->
                    ContaFixaFormTela(id = entrada.arguments?.getString("id"), aoVoltar = { navController.popBackStack() })
                }
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
