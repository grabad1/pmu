package rs.etf.focusguard.ui.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import rs.etf.focusguard.ActiveSession
import rs.etf.focusguard.Home
import rs.etf.focusguard.NewSession
import rs.etf.focusguard.PreviousSessions
import rs.etf.focusguard.R
import rs.etf.focusguard.ScheduledSessions
import rs.etf.focusguard.ui.elements.screens.ActiveSessionScreen
import rs.etf.focusguard.ui.elements.screens.HomeScreen
import rs.etf.focusguard.ui.elements.screens.NewSessionScreen
import rs.etf.focusguard.ui.elements.screens.PreviousSessionsScreen
import rs.etf.focusguard.ui.elements.screens.ScheduledSessionsScreen
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.Card
import rs.etf.focusguard.util.navigateSafely
import rs.etf.focusguard.util.popBackStackSafely

@Composable
fun FocusGuardApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scheduledMessage = stringResource(R.string.schedule_confirmation)

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
                    onStart = { session ->
                        navController.navigateSafely(ActiveSession(sessionName = session.name))
                    },
                    onScheduled = { name ->
                        navController.popBackStackSafely()
                        scope.launch {
                            snackbarHostState.showSnackbar(scheduledMessage.format(name))
                        }
                    },
                )
            }
            composable<ScheduledSessions> {
                ScheduledSessionsScreen(onBack = { navController.popBackStackSafely() })
            }
            composable<PreviousSessions> {
                PreviousSessionsScreen(onBack = { navController.popBackStackSafely() })
            }
            composable<ActiveSession> { backStackEntry ->
                val route: ActiveSession = backStackEntry.toRoute()
                ActiveSessionScreen(
                    sessionName = route.sessionName,
                    onFinished = { navController.popBackStackSafely(Home, inclusive = false) },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data ->
            Snackbar(
                shape = MaterialTheme.shapes.medium,
                containerColor = Card,
                contentColor = Accent,
            ) { Text(text = data.visuals.message) }
        }
    }
}
