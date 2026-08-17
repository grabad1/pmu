package rs.etf.focusguard.data.room

import androidx.room.Embedded
import androidx.room.Relation

/** A session together with its pause log, loaded in one query. */
data class SessionWithPauses(
    @Embedded val session: Session,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val pauses: List<Pause>,
) {
    val plannedPauses: List<Pause> get() = pauses.filter { it.type == PauseType.PLANNED }
    val unplannedPauses: List<Pause> get() = pauses.filter { it.type == PauseType.UNPLANNED }
}
