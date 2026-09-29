package com.mersadai.app.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mersadai.app.R
import com.mersadai.app.data.local.SettingsRepository
import com.mersadai.app.domain.model.AppSettings
import com.mersadai.app.feature.explore.ExploreViewModel
import com.mersadai.app.feature.explore.HomeSection
import com.mersadai.app.feature.explore.screens.DetailsScreen
import com.mersadai.app.feature.explore.screens.FavoritesScreen
import com.mersadai.app.feature.explore.screens.HomeScreen
import com.mersadai.app.feature.explore.screens.SearchScreen
import com.mersadai.app.feature.explore.screens.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val DETAILS = "details/{itemId}"
    fun details(itemId: String) = "details/${Uri.encode(itemId)}"
}

private data class Destination(val route: String, val label: Int, val icon: ImageVector)

@Composable
fun MersadNavHost(
    viewModel: ExploreViewModel,
    settings: AppSettings,
    settingsRepository: SettingsRepository,
    onManualSync: suspend () -> Unit,
    initialDiscoverySection: HomeSection? = null,
) {
    val navController = rememberNavController()
    LaunchedEffect(initialDiscoverySection) {
        if (initialDiscoverySection != null) {
            viewModel.selectSearchSection(initialDiscoverySection)
            navController.navigate(Routes.SEARCH) { launchSingleTop = true }
        }
    }
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route
    val destinations = listOf(
        Destination(Routes.HOME, R.string.nav_home, Icons.Default.Home),
        Destination(Routes.SEARCH, R.string.nav_search, Icons.Default.Search),
        Destination(Routes.FAVORITES, R.string.nav_favorites, Icons.Default.Favorite),
        Destination(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = Routes.HOME, modifier = Modifier) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenSearch = { section ->
                        viewModel.selectSearchSection(section)
                        navController.navigate(Routes.SEARCH)
                    },
                    onOpenItem = { navController.navigate(Routes.details(it)) },
                    onManualSync = onManualSync,
                    contentPadding = padding,
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(viewModel, onOpenItem = { navController.navigate(Routes.details(it)) }, contentPadding = padding)
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(viewModel, onOpenItem = { navController.navigate(Routes.details(it)) }, contentPadding = padding)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    settingsRepository = settingsRepository,
                    onClearCache = viewModel::clearCache,
                    contentPadding = padding,
                )
            }
            composable(
                route = Routes.DETAILS,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { entry ->
                DetailsScreen(
                    itemId = Uri.decode(entry.arguments?.getString("itemId").orEmpty()),
                    viewModel = viewModel,
                    onOpenItem = { navController.navigate(Routes.details(it)) },
                    contentPadding = padding,
                )
            }
        }
    }
}
