package rs.etf.focusguard.util

/**
 * Categories offered on the new-session form.
 *
 * A plain list rather than an enum because the user may add their own — the form shows these
 * plus any category already used, so a made-up one becomes a suggestion from then on without
 * needing a schema change.
 */
val PRESET_CATEGORIES = listOf("Studying", "Work", "Reading", "Yoga", "Other")

/**
 * Trims a typed category or topic, treating blank as "not set".
 *
 * Case is preserved for display; queries compare with COLLATE NOCASE, so "Math" and "math"
 * are already the same topic and the user is never told off for capitalisation.
 */
fun normaliseLabel(raw: String?): String? = raw?.trim()?.ifBlank { null }
