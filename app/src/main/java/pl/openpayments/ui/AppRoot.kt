package pl.openpayments.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pl.openpayments.ui.screens.*

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("home", "Start", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("transactions", "Transakcje", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    Tab("budgets", "Budżety", Icons.Outlined.Savings, Icons.Filled.Savings),
    Tab("subscriptions", "Cykliczne", Icons.Outlined.Autorenew, Icons.Filled.Autorenew),
    Tab("stats", "Statystyki", Icons.Outlined.Insights, Icons.Filled.Insights),
)

/** Akcje nawigacji przekazywane ekranom. */
class Nav(private val c: androidx.navigation.NavHostController) {
    fun edit(id: Long = 0, fromReceipt: Boolean = false, income: Boolean = false) =
        c.navigate("edit/$id?receipt=$fromReceipt&income=$income")
    fun scan() = c.navigate("scan")
    fun settings() = c.navigate("settings")
    fun categories() = c.navigate("categories")
    fun tab(route: String) = c.navigate(route) {
        popUpTo(c.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true; restoreState = true
    }
    fun back() = c.popBackStack()
    fun replaceWithEdit() = c.navigate("edit/0?receipt=true&income=false") { popUpTo("scan") { inclusive = true } }
}

@Composable
fun AppRoot(vm: MainViewModel, canLock: () -> Boolean) {
    if (!vm.onboarded) { OnboardingScreen(vm); return }
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val entry by controller.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackHost = remember { SnackbarHostState() }
    val snack by vm.snack.collectAsState()
    LaunchedEffect(snack) {
        snack?.let { s ->
            val r = snackHost.showSnackbar(s.text, s.action, withDismissAction = s.action == null, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) s.onAction?.invoke()
            vm.snackShown()
        }
    }
    if (vm.showUpdateDialog && vm.release != null) UpdateDialog(vm)

    Scaffold(
        snackbarHost = { SnackbarHost(snackHost) },
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { t ->
                        val sel = route == t.route
                        NavigationBarItem(
                            selected = sel, onClick = { nav.tab(t.route) },
                            icon = { Icon(if (sel) t.selectedIcon else t.icon, null) },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            controller, startDestination = "home", modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
            enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
        ) {
            composable("home") { HomeScreen(vm, nav) }
            composable("transactions") { TransactionsScreen(vm, nav) }
            composable("budgets") { BudgetsScreen(vm) }
            composable("subscriptions") { SubscriptionsScreen(vm) }
            composable("stats") { StatsScreen(vm) }
            composable("settings",
                enterTransition = { slideInHorizontally { it } }, exitTransition = { slideOutHorizontally { it } },
            ) { SettingsScreen(vm, nav, canLock) }
            composable("categories") { CategoriesScreen(vm, nav) }
            composable("scan") { ScanScreen(vm, nav) }
            composable(
                "edit/{id}?receipt={receipt}&income={income}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("receipt") { type = NavType.BoolType; defaultValue = false },
                    navArgument("income") { type = NavType.BoolType; defaultValue = false },
                ),
                enterTransition = { slideInHorizontally { it } }, exitTransition = { slideOutHorizontally { it } },
            ) { e ->
                val a = e.arguments!!
                EditTransactionScreen(vm, nav, a.getLong("id"), a.getBoolean("receipt"), a.getBoolean("income"))
            }
        }
    }
}
