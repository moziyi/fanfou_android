package com.kansen.fanfan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import com.kansen.fanfan.ui.dm.DmConversationScreen
import com.kansen.fanfan.ui.dm.DmListScreen
import com.kansen.fanfan.ui.me.MeScreen
import com.kansen.fanfan.ui.mentions.MentionsScreen
import com.kansen.fanfan.ui.timeline.TimelineScreen
import com.kansen.fanfan.ui.user.UserScreen

private const val ROUTE_TIMELINE = "timeline"
private const val ROUTE_MENTIONS = "mentions"
private const val ROUTE_DM = "dm"
private const val ROUTE_DM_CONVERSATION = "dm/{userId}"
private const val ROUTE_ME = "me"
private const val ROUTE_DETAIL = "detail/{id}"
private const val ROUTE_COMPOSE = "compose?inReplyTo={inReplyTo}&repostOf={repostOf}"
private const val ROUTE_USER = "user/{id}?tab={tab}"

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(ROUTE_TIMELINE, "首页", Icons.Filled.Home),
    BottomTab(ROUTE_MENTIONS, "@我", Icons.Filled.AlternateEmail),
    BottomTab(ROUTE_DM, "私信", Icons.Filled.Email),
    BottomTab(ROUTE_ME, "我", Icons.Filled.Person),
)

private fun composeRoute(inReplyTo: String? = null, repostOf: String? = null): String =
    "compose?inReplyTo=${inReplyTo ?: "-1"}&repostOf=${repostOf ?: "-1"}"

private fun userRoute(id: String, tab: Int = 0): String = "user/$id?tab=$tab"

@Composable
fun MainShell(
    user: SelfUser,
    unreadMentions: Int,
    unreadDms: Int,
    openStatusId: String?,
    openMentions: Boolean,
    onDeepLinkHandled: () -> Unit,
    /** Debug-only: posts a sample notification (see MentionNotifications). */
    testNotification: (() -> Unit)? = null,
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

    // Notification taps: the intent may arrive while logged out, so it is
    // applied once MainShell is composed (i.e. right after a successful login).
    LaunchedEffect(openStatusId) {
        if (!openStatusId.isNullOrBlank()) {
            navController.navigate("detail/$openStatusId")
            onDeepLinkHandled()
        }
    }
    LaunchedEffect(openMentions) {
        if (openMentions) {
            navController.navigate(ROUTE_MENTIONS) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onDeepLinkHandled()
        }
    }

    // Mention polling is only useful if the notification can actually be shown.
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
                            icon = {
                                val badge = when (tab.route) {
                                    ROUTE_MENTIONS -> unreadMentions
                                    ROUTE_DM -> unreadDms
                                    else -> 0
                                }
                                if (badge > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(if (badge > 99) "99+" else badge.toString())
                                            }
                                        },
                                    ) {
                                        Icon(tab.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
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
                    onOpenUser = { id -> navController.navigate(userRoute(id)) },
                    onReply = { status -> navController.navigate(composeRoute(inReplyTo = status.id)) },
                )
            }
            composable(ROUTE_MENTIONS) {
                MentionsScreen(
                    onOpenStatus = { id -> navController.navigate("detail/$id") },
                    onOpenUser = { id -> navController.navigate(userRoute(id)) },
                    onReply = { status -> navController.navigate(composeRoute(inReplyTo = status.id)) },
                )
            }
            composable(ROUTE_DM) {
                DmListScreen(
                    onOpenConversation = { conversation ->
                        navController.navigate("dm/${conversation.otherId}")
                    },
                )
            }
            composable(
                route = ROUTE_DM_CONVERSATION,
                arguments = listOf(navArgument("userId") { type = NavType.StringType }),
            ) {
                DmConversationScreen(
                    onBack = { navController.popBackStack() },
                    onOpenUser = { id -> navController.navigate(userRoute(id)) },
                )
            }
            composable(ROUTE_ME) {
                MeScreen(
                    user = user,
                    onOpenProfile = { navController.navigate(userRoute(user.id)) },
                    onOpenFriends = { navController.navigate(userRoute(user.id, tab = 1)) },
                    onOpenFollowers = { navController.navigate(userRoute(user.id, tab = 2)) },
                    onOpenDirectMessages = {
                        navController.navigate(ROUTE_DM) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onTestNotification = { testNotification?.invoke() },
                    onLogout = onLogout,
                )
            }

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
                    onOpenUser = { id -> navController.navigate(userRoute(id)) },
                )
            }

            composable(
                route = ROUTE_USER,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("tab") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                ),
            ) { entry ->
                UserScreen(
                    initialTab = entry.arguments?.getInt("tab") ?: 0,
                    onBack = { navController.popBackStack() },
                    onSendMessage = { id -> navController.navigate("dm/$id") },
                    onOpenStatus = { id -> navController.navigate("detail/$id") },
                    onOpenUser = { id -> navController.navigate(userRoute(id)) },
                )
            }
        }
    }

    viewerPhoto?.let { photo ->
        PhotoViewer(photo = photo, onDismiss = { viewerPhoto = null })
    }
}
