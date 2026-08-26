package rs.etf.focusguard.data.room

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Instants are stored as epoch milliseconds and enums as their names, so the schema stays
 * readable and independent of enum ordinals (reordering an enum must not corrupt old rows).
 */
class Converters {

    @TypeConverter
    fun instantFromEpochMillis(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun instantToEpochMillis(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun sessionStatusFromName(value: String?): SessionStatus? = value?.let(SessionStatus::valueOf)

    @TypeConverter
    fun sessionStatusToName(status: SessionStatus?): String? = status?.name

    @TypeConverter
    fun pauseTypeFromName(value: String?): PauseType? = value?.let(PauseType::valueOf)

    @TypeConverter
    fun pauseTypeToName(type: PauseType?): String? = type?.name

    @TypeConverter
    fun sensorKindFromName(value: String?): SensorKind? = value?.let(SensorKind::valueOf)

    @TypeConverter
    fun sensorKindToName(kind: SensorKind?): String? = kind?.name

    @TypeConverter
    fun interruptionKindFromName(value: String?): InterruptionKind? =
        value?.let(InterruptionKind::valueOf)

    @TypeConverter
    fun interruptionKindToName(kind: InterruptionKind?): String? = kind?.name
}
