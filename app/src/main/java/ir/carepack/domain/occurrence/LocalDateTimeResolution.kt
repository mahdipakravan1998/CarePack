package ir.carepack.domain.occurrence

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

enum class LocalDateTimeResolutionKind {
    NORMAL,
    GAP_ADJUSTED,
    OVERLAP_EARLIER_OFFSET,
}

data class LocalDateTimeResolution(
    val requestedLocalDateTime: LocalDateTime,
    val resolvedLocalDateTime: LocalDateTime,
    val zoneId: ZoneId,
    val offset: ZoneOffset,
    val instant: Instant,
    val kind: LocalDateTimeResolutionKind,
)

class CarePackLocalDateTimeResolver {

    fun resolve(
        localDateTime: LocalDateTime,
        zoneId: ZoneId,
    ): LocalDateTimeResolution {
        val rules = zoneId.rules
        val validOffsets = rules.getValidOffsets(localDateTime)

        return when (validOffsets.size) {
            0 -> {
                val transition =
                    checkNotNull(
                        rules.getTransition(localDateTime),
                    )

                val resolvedLocalDateTime =
                    transition.dateTimeAfter
                val offset =
                    transition.offsetAfter

                LocalDateTimeResolution(
                    requestedLocalDateTime = localDateTime,
                    resolvedLocalDateTime =
                        resolvedLocalDateTime,
                    zoneId = zoneId,
                    offset = offset,
                    instant =
                        resolvedLocalDateTime
                            .toInstant(offset),
                    kind =
                        LocalDateTimeResolutionKind
                            .GAP_ADJUSTED,
                )
            }

            1 -> {
                val offset = validOffsets.single()

                LocalDateTimeResolution(
                    requestedLocalDateTime = localDateTime,
                    resolvedLocalDateTime = localDateTime,
                    zoneId = zoneId,
                    offset = offset,
                    instant =
                        localDateTime.toInstant(offset),
                    kind =
                        LocalDateTimeResolutionKind.NORMAL,
                )
            }

            else -> {
                val offset =
                    validOffsets.minBy { candidateOffset ->
                        localDateTime
                            .toInstant(candidateOffset)
                    }

                LocalDateTimeResolution(
                    requestedLocalDateTime = localDateTime,
                    resolvedLocalDateTime = localDateTime,
                    zoneId = zoneId,
                    offset = offset,
                    instant =
                        localDateTime.toInstant(offset),
                    kind =
                        LocalDateTimeResolutionKind
                            .OVERLAP_EARLIER_OFFSET,
                )
            }
        }
    }
}
