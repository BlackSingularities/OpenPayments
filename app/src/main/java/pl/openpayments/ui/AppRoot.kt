package pl.openpayments.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
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
    Tab("transactions", "Transakcje", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    Tab("budgets", "Budżety", Icons.Outlined.Savings, Icons.Filled.Savings),
    Tab("subscriptions", "Cykliczne", Icons.Outlined.Autorenew, Icons.Filled.Autorenew),
    Tab("stats", "Statystyki", Icons.Outlined.Insights, Icons.Filled.Insights),
)
private val tabRoutes = tabs.map { it.route }.toSet()

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
            AnimatedVisibility(
                route in tabRoutes,
                enter = slideInVertically { it }, exit = slideOutVertically { it },
            ) {
                ShortNavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { t ->
                        val sel = route == t.route
                        ShortNavigationBarItem(
                            selected = sel, onClick = { nav.tab(t.route) },
                            icon = { Icon(if (sel) t.selectedIcon else t.icon, null) },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { pad ->
        val motion = MaterialTheme.motionScheme
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            NavHost(
                controller, startDestination = "home",
                enterTransition = { fadeIn(motion.defaultEffectsSpec()) + scaleIn(initialScale = 0.96f) },
                exitTransition = { fadeOut(motion.fastEffectsSpec()) },
                popEnterTransition = { fadeIn(motion.defaultEffectsSpec()) },
                popExitTransition = { fadeOut(motion.fastEffectsSpec()) + scaleOut(targetScale = 0.96f) },
            ) {
                composable("home") { HomeScreen(vm, nav) }
                composable("transactions") { TransactionsScreen(vm, nav) }
                composable("budgets") { BudgetsScreen(vm) }
                composable("subscriptions") { SubscriptionsScreen(vm) }
                composable("stats") { StatsScreen(vm) }
                val slideIn: androidx.compose.animation.AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition =
                    { slideInHorizontally { it / 3 } + fadeIn() }
                val slideOut: androidx.compose.animation.AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition =
                    { slideOutHorizontally { it / 3 } + fadeOut() }
                composable("settings", enterTransition = { slideIn() }, popExitTransition = { slideOut() }) { SettingsScreen(vm, nav, canLock) }
                composable("categories", enterTransition = { slideIn() }, popExitTransition = { slideOut() }) { CategoriesScreen(vm, nav) }
                composable("scan", enterTransition = { slideIn() }, popExitTransition = { slideOut() }) { ScanScreen(vm, nav) }
                composable(
                    "edit/{id}?receipt={receipt}&income={income}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("receipt") { type = NavType.BoolType; defaultValue = false },
                        navArgument("income") { type = NavType.BoolType; defaultValue = false },
                    ),
                    enterTransition = { slideInVertically { it / 4 } + fadeIn() },
                    popExitTransition = { slideOutVertically { it / 4 } + fadeOut() },
                ) { e ->
                    val a = e.arguments!!
                    EditTransactionScreen(vm, nav, a.getLong("id"), a.getBoolean("receipt"), a.getBoolean("income"))
                }
            }
        }
    }
}
