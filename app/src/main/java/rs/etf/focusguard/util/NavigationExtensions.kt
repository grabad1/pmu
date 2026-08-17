package rs.etf.focusguard.util

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * A destination only reaches RESUMED once its transition has settled. Two taps that land
 * inside the same transition would otherwise be handled twice — popping one screen too
 * many (which empties the graph and leaves a blank window) or pushing a duplicate copy.
 * Ignoring events while the entry is not RESUMED makes both cases idempotent.
 */
private fun NavController.isCurrentEntryResumed(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

fun NavController.navigateSafely(route: Any, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (isCurrentEntryResumed()) navigate(route, builder)
}

fun NavController.popBackStackSafely() {
    if (isCurrentEntryResumed()) popBackStack()
}

fun NavController.popBackStackSafely(route: Any, inclusive: Boolean) {
    if (isCurrentEntryResumed()) popBackStack(route, inclusive)
}
