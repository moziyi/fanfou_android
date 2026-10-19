package com.kansen.fanfan.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kansen.fanfan.core.datastore.SelfUser
import com.kansen.fanfan.core.model.PhotoUi
import com.kansen.fanfan.ui.components.PhotoViewer
import com.kansen.fanfan.ui.components.PlaceholderScreen
import com.kansen.fanfan.ui.compose.ComposeScreen
import com.kansen.fanfan.ui.detail.DetailScreen
import com.kansen.fanfan.ui.me.MeScreen
import com.kansen.fanfan.ui.timeline.TimelineScreen

private const val ROUTE_TIMELINE = "timeline"
private const val ROUTE_MENTIONS = "mentions"
private const val ROUTE_DM = "dm"
private const val ROUTE_ME = "me"
private const val ROUTE_DETAIL = "detail/{id}"
private const val ROUTE_COMPOSE = "compose?inReplyTo={inReplyTo}&repostOf={repostOf}"

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(ROUTE_TIMELINE, "首页", Icons.Filled.Home),
    BottomTab(ROUTE_MENTIONS, "@我", Icons.Filled.AlternateEmail),
    BottomTab(ROUTE_DM, "私信", Icons.Filled.Email),
    BottomTab(ROUTE_ME, "我", Icons.Filled.Person),
)

private fun composeRoute(inReplyTo: String? = null, repostOf: String? = null): String =
    "compose?inReplyTo=${inReplyTo ?: "-1"}&repostOf=${repostOf ?: "-1"}"

@Composable
fun MainShell(
    user: SelfUser,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showChrome = bottomTabs.any { it.route == currentRoute }

    // The viewer lives above the nav graph: one photo at a time, reachable from
    // the timeline and from the detail thread alike, and it survives the list
    // item that opened it being recycled.
    var viewerPhoto by remember { mutableStateOf<PhotoUi?>(null) }
    val openPhoto: (PhotoUi) -> Unit = { viewerPhoto = it }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showChrome) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showChrome) {
                FloatingActionButton(onClick = { navController.navigate(composeRoute()) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "发消息")
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_TIMELINE,
            modifier = Modifier.padding(padding),
        ) {
            composable(ROUTE_TIMELINE) {
                TimelineScreen(
                    onOpenStatus = { id -> navController.navigate("detail/$id") },
                    onReply = { status -> navController.navigate(composeRoute(inReplyTo = status.id)) },
                )
            }
            composable(ROUTE_MENTIONS) { PlaceholderScreen("「@我」将在 M2 交付") }
            composable(ROUTE_DM) { PlaceholderScreen("「私信」将在 M3 交付") }
            composable(ROUTE_ME) { MeScreen(user = user, onLogout = onLogout) }

            composable(
                route = ROUTE_COMPOSE,
                arguments = listOf(
                    navArgument("inReplyTo") {
                        type = NavType.StringType
                        defaultValue = "-1"
                    },
                    navArgument("repostOf") {
                        type = NavType.StringType
                        defaultValue = "-1"
                    },
                ),
            ) {
                ComposeScreen(
                    onBack = { navController.popBackStack() },
                    onPosted = { navController.popBackStack() },
                )
            }

            composable(
                route = ROUTE_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) {
                DetailScreen(
                    onBack = { navController.popBackStack() },
                    onReply = { status -> navController.navigate(composeRoute(inReplyTo = status.id)) },
                    onOpenPhoto = openPhoto,
                )
            }
        }
    }

    viewerPhoto?.let { photo ->
        PhotoViewer(photo = photo, onDismiss = { viewerPhoto = null })
    }
}
