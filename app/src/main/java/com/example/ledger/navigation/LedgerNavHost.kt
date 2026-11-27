package com.example.ledger.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ledger.ui.add.AddScreen
import com.example.ledger.ui.debts.DebtsScreen
import com.example.ledger.ui.home.HomeScreen
import com.example.ledger.ui.products.ProductsScreen
import com.example.ledger.ui.records.RecordsScreen
import com.example.ledger.ui.report.ReportScreen
import com.example.ledger.ui.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val RECORDS = "records"
    const val PRODUCTS = "products"
    const val DEBTS = "debts"
    const val SETTINGS = "settings"
    const val REPORT = "report"
    const val ADD = "add"
    const val ADD_PATTERN = "add?editId={editId}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "首页", Icons.Filled.Home),
    Tab(Routes.RECORDS, "账单", Icons.AutoMirrored.Filled.List),
    Tab(Routes.PRODUCTS, "商品", Icons.Filled.ShoppingCart),
    Tab(Routes.DEBTS, "赊账", Icons.Filled.Person),
    Tab(Routes.SETTINGS, "设置", Icons.Filled.Settings),
)

@Composable
fun LedgerNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showBars = route != Routes.ADD_PATTERN && route != Routes.ADD && route != Routes.REPORT

    Scaffold(
        bottomBar = {
            if (showBars) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showBars && route != Routes.SETTINGS) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Routes.ADD) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("记一笔") },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(onOpenReport = { navController.navigate(Routes.REPORT) })
            }
            composable(Routes.RECORDS) {
                RecordsScreen(onEdit = { id -> navController.navigate("add?editId=$id") })
            }
            composable(Routes.PRODUCTS) { ProductsScreen() }
            composable(Routes.DEBTS) { DebtsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.REPORT) {
                ReportScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.ADD_PATTERN,
                arguments = listOf(
                    navArgument("editId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                ),
            ) { entry ->
                AddScreen(
                    onClose = { navController.popBackStack() },
                    editId = entry.arguments?.getLong("editId")?.takeIf { it > 0 },
                )
            }
        }
    }
}