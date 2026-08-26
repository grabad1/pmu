package rs.etf.focusguard.ui.elements

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import rs.etf.focusguard.ActiveSession
import rs.etf.focusguard.FocusSessionService
import rs.etf.focusguard.Home
import rs.etf.focusguard.NewSession
import rs.etf.focusguard.PreviousSessions
import rs.etf.focusguard.R
import rs.etf.focusguard.ScheduledSessions
import rs.etf.focusguard.ui.elements.screens.ActiveSessionScreen
import rs.etf.focusguard.ui.elements.screens.HomeScreen
import rs.etf.focusguard.ui.elements.screens.JoinSessionDialog
import rs.etf.focusguard.ui.elements.screens.NewSessionScreen
import rs.etf.focusguard.ui.elements.screens.PreviousSessionsScreen
import rs.etf.focusguard.ui.elements.screens.ScheduledSessionsScreen
import rs.etf.focusguard.ui.elements.screens.SessionResultDialog
import rs.etf.focusguard.ui.elements.theme.Accent
import rs.etf.focusguard.ui.elements.theme.Card
import rs.etf.focusguard.ui.stateholders.FocusGuardAppViewModel
import rs.etf.focusguard.util.navigateSafely
import rs.etf.focusguard.util.popBackStackSafely

@Composable
fun FocusGuardApp(
    viewModel: FocusGuardAppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scheduledMessage = stringResource(R.string.schedule_confirmation)

    val runningSession by viewModel.runningSession.collectAsStateWithLifecycle()
    val dueSession by viewModel.dueSession.collectAsStateWithLifecycle()
    val finishedSession by viewModel.finishedSession.collectAsStateWithLifecycle()
    val finishedSessionDetail by
        viewModel.finishedSessionDetail.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()

    // Reopening the app mid-session should land on the timer, not on Home.
    LaunchedEffect(runningSession != null, currentEntry) {
        val onSessionScreen = currentEntry?.destination?.hasRoute(ActiveSession::class) == true
        if (runningSession != null && !onSessionScreen) {
            navController.navigate(ActiveSession) { popUpTo(Home) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        NavHost(
            navController = navController,
            startDestination = Home,
            // The prototype swaps screens instantly, which on a real device reads as a glitch.
            // A short slide with a fade gives the back stack a direction: forward moves left,
            // back moves right. Deliberately quick — this is a timer, not a gallery.
            enterTransition = {
                slideInHorizontally(animationSpec = tween(TRANSITION_MILLIS)) { it / 6 } +
                    fadeIn(animationSpec = tween(TRANSITION_MILLIS))
            },
            exitTransition = {
                slideOutHorizontally(animationSpec = tween(TRANSITION_MILLIS)) { -it / 8 } +
                    fadeOut(animationSpec = tween(TRANSITION_MILLIS))
            },
            popEnterTransition = {
                slideInHorizontally(animationSpec = tween(TRANSITION_MILLIS)) { -it / 8 } +
                    fadeIn(animationSpec = tween(TRANSITION_MILLIS))
            },
            popExitTransition = {
                slideOutHorizontally(animationSpec = tween(TRANSITION_MILLIS)) { it / 6 } +
                    fadeOut(animationSpec = tween(TRANSITION_MILLIS))
            },
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
                    onStart = { sessionId ->
                        FocusSessionService.start(context, sessionId)
                        navController.navigateSafely(ActiveSession) {
                            // The form should not sit behind a running session.
                            popUpTo(Home)
                        }
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
            composable<ActiveSession> {
                ActiveSessionScreen(
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

    // Only offered when nothing is already running, so a due session cannot interrupt one.
    dueSession?.takeIf { runningSession == null }?.let { session ->
        JoinSessionDialog(
            session = session,
            onJoin = { viewModel.joinSession(session) },
            onDismiss = { viewModel.dismissDueSession(session.id) },
        )
    }

    finishedSession?.let { session ->
        SessionResultDialog(
            session = session,
            onDismiss = viewModel::dismissFinishedSession,
            detail = finishedSessionDetail,        )
    }
}

/** Short enough to feel like a response rather than a wait. */
private const val TRANSITION_MILLIS = 260
