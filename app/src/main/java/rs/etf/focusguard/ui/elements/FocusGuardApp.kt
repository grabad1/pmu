package rs.etf.focusguard.ui.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import rs.etf.focusguard.ActiveSession
import rs.etf.focusguard.Home
import rs.etf.focusguard.NewSession
import rs.etf.focusguard.PreviousSessions
import rs.etf.focusguard.ScheduledSessions
import rs.etf.focusguard.ui.elements.screens.ActiveSessionScreen
import rs.etf.focusguard.ui.elements.screens.HomeScreen
import rs.etf.focusguard.ui.elements.screens.NewSessionScreen
import rs.etf.focusguard.ui.elements.screens.PreviousSessionsScreen
import rs.etf.focusguard.ui.elements.screens.ScheduledSessionsScreen
import rs.etf.focusguard.util.navigateSafely
import rs.etf.focusguard.util.popBackStackSafely

@Composable
fun FocusGuardApp() {
    val navController = rememberNavController()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        NavHost(
            navController = navController,
            startDestination = Home,
        ) {
            composable<Home> {
                HomeScreen(
                    onNewSession = { navController.navigateSafely(NewSession) },
                    onScheduledSessions = { navController.navigateSafely(ScheduledSessions) },
                    onPreviousSessions = { navController.navigateSafely(PreviousSessions) },
                )
            }
            composable<NewSession> {
                NewSessionScreen(
                    onBack = { navController.popBackStackSafely() },
                    onStart = { navController.navigateSafely(ActiveSession) },
                )
            }
            composable<ScheduledSessions> {
                ScheduledSessionsScreen(onBack = { navController.popBackStackSafely() })
            }
            composable<PreviousSessions> {
                PreviousSessionsScreen(onBack = { navController.popBackStackSafely() })
            }
            composable<ActiveSession> {
                ActiveSessionScreen(
                    onFinished = {
                        navController.popBackStackSafely(Home, inclusive = false)
                    },
                )
            }
        }
    }
}
