package rs.etf.focusguard

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations (Navigation Compose 2.9+).
 * Using @Serializable routes instead of string routes means arguments are
 * checked at compile time rather than parsed out of a URL template.
 */
sealed interface FocusGuardDestination

@Serializable
data object Home : FocusGuardDestination

@Serializable
data object NewSession : FocusGuardDestination

@Serializable
data object ScheduledSessions : FocusGuardDestination

@Serializable
data object PreviousSessions : FocusGuardDestination

@Serializable
data object ActiveSession : FocusGuardDestination
