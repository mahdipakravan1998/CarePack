package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import ir.carepack.settings.deletion.DeletionMarkerCorruptionReason
import ir.carepack.settings.deletion.MedicationDeletionMarker
import ir.carepack.settings.deletion.MedicationDeletionMarkerReadResult
import ir.carepack.settings.deletion.MedicationDeletionMarkerStage
import ir.carepack.settings.deletion.MedicationDeletionMarkerStore
import ir.carepack.settings.deletion.MedicationDeletionPreview
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreMedicationDeletionMarkerStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) : MedicationDeletionMarkerStore {

    constructor(context: Context) :
        this(
            dataStore =
                context.applicationContext
                    .carePackDataStore,
        )

    override val state:
        Flow<MedicationDeletionMarkerReadResult> =
        dataStore
            .data
            .map(
                ::decodeMarker,
            )
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(
                        MedicationDeletionMarkerReadResult
                            .Corrupted(
                                reason =
                                    DeletionMarkerCorruptionReason
                                        .STORAGE_READ_FAILURE,
                            ),
                    )
                } else {
                    throw throwable
                }
            }

    override suspend fun save(
        marker: MedicationDeletionMarker,
    ) {
        require(marker.hasValidChecksum())

        dataStore
            .edit { preferences ->
                writeMarker(
                    preferences = preferences,
                    marker = marker,
                )
            }
    }

    override suspend fun updateStage(
        medicationId: String,
        stage: MedicationDeletionMarkerStage,
    ) {
        val target = medicationId.trim()
        require(target.isNotBlank())

        dataStore
            .edit { preferences ->
                val readResult =
                    decodeMarker(preferences)

                val marker =
                    (readResult as?
                        MedicationDeletionMarkerReadResult.Valid)
                        ?.marker
                        ?: error(
                            "Medication deletion marker is not valid.",
                        )

                check(
                    marker.expectedPreview.medicationId ==
                        target,
                )

                writeMarker(
                    preferences = preferences,
                    marker = marker.withStage(stage),
                )
            }
    }

    override suspend fun clear(
        medicationId: String,
    ) {
        val target = medicationId.trim()
        require(target.isNotBlank())

        dataStore
            .edit { preferences ->
                if (
                    preferences[
                        MedicationDeletionPreferenceKeys
                            .medicationId
                    ] == target
                ) {
                    removeMarkerKeys(
                        preferences = preferences,
                    )
                }
            }
    }

    private fun decodeMarker(
        preferences: Preferences,
    ): MedicationDeletionMarkerReadResult {
        val keys = preferences.asMap().keys
        val markerKeys = medicationMarkerKeys()

        if (keys.none { it in markerKeys }) {
            return MedicationDeletionMarkerReadResult.Absent
        }

        if (!keys.containsAll(markerKeys)) {
            return MedicationDeletionMarkerReadResult
                .Corrupted(
                    reason =
                        DeletionMarkerCorruptionReason
                            .PARTIAL_MARKER,
                )
        }

        val version =
            preferences[
                MedicationDeletionPreferenceKeys.version
            ] ?: return corruptedPartial()

        if (
            version !=
            MedicationDeletionMarker.CURRENT_VERSION
        ) {
            return MedicationDeletionMarkerReadResult
                .Corrupted(
                    reason =
                        DeletionMarkerCorruptionReason
                            .UNKNOWN_VERSION,
                )
        }

        val stage =
            preferences[
                MedicationDeletionPreferenceKeys.stage
            ]
                ?.let { storedStage ->
                    MedicationDeletionMarkerStage.entries
                        .firstOrNull { candidate ->
                            candidate.name == storedStage
                        }
                }
                ?: return MedicationDeletionMarkerReadResult
                    .Corrupted(
                        reason =
                            DeletionMarkerCorruptionReason
                                .INVALID_STAGE,
                    )

        val marker =
            runCatching {
                MedicationDeletionMarker(
                    version = version,
                    expectedPreview =
                        MedicationDeletionPreview(
                            medicationId =
                                checkNotNull(
                                    preferences[
                                        MedicationDeletionPreferenceKeys
                                            .medicationId
                                    ],
                                ),
                            medicationName =
                                checkNotNull(
                                    preferences[
                                        MedicationDeletionPreferenceKeys
                                            .medicationName
                                    ],
                                ),
                            medicationUpdatedAtEpochMillis =
                                checkNotNull(
                                    preferences[
                                        MedicationDeletionPreferenceKeys
                                            .medicationUpdatedAt
                                    ],
                                ),
                            scheduleSeriesCount =
                                checkNotNull(
                                    preferences[
                                        MedicationDeletionPreferenceKeys
                                            .scheduleSeriesCount
                                    ],
                                ),
                            scheduleVersionCount =
                                checkNotNull(
                                    preferences[
                                        MedicationDeletionPreferenceKeys
                                            .scheduleVersionCount
                                    ],
                                ),
                            scheduleTimeCount =
                                checkNotNull(
                                    preferences[
                                        MedicationDeletionPreferenceKeys
                                            .scheduleTimeCount
                                    ],
                                ),
                            occurrenceCount =
                                checkNotNull(
                                    preferences[
                                        MedicationDeletionPreferenceKeys
                                            .occurrenceCount
                                    ],
                                ),
                            caregiverReportCount =
                                checkNotNull(
                                    preferences[
                                        MedicationDeletionPreferenceKeys
                                            .caregiverReportCount
                                    ],
                                ),
                        ),
                    scheduleSeriesIds =
                        checkNotNull(
                            preferences[
                                MedicationDeletionPreferenceKeys
                                    .scheduleSeriesIds
                            ],
                        ),
                    occurrenceIds =
                        checkNotNull(
                            preferences[
                                MedicationDeletionPreferenceKeys
                                    .occurrenceIds
                            ],
                        ),
                    stage = stage,
                    startedAtEpochMillis =
                        checkNotNull(
                            preferences[
                                MedicationDeletionPreferenceKeys
                                    .startedAt
                            ],
                        ),
                    checksum =
                        checkNotNull(
                            preferences[
                                MedicationDeletionPreferenceKeys
                                    .checksum
                            ],
                        ),
                )
            }.getOrElse {
                return MedicationDeletionMarkerReadResult
                    .Corrupted(
                        reason =
                            DeletionMarkerCorruptionReason
                                .INVALID_VALUE,
                    )
            }

        if (!marker.hasValidChecksum()) {
            return MedicationDeletionMarkerReadResult
                .Corrupted(
                    reason =
                        DeletionMarkerCorruptionReason
                            .CHECKSUM_MISMATCH,
                )
        }

        return MedicationDeletionMarkerReadResult
            .Valid(
                marker = marker,
            )
    }

    private fun writeMarker(
        preferences: MutablePreferences,
        marker: MedicationDeletionMarker,
    ) {
        val preview = marker.expectedPreview

        preferences[
            MedicationDeletionPreferenceKeys.version
        ] = marker.version
        preferences[
            MedicationDeletionPreferenceKeys.medicationId
        ] = preview.medicationId
        preferences[
            MedicationDeletionPreferenceKeys.medicationName
        ] = preview.medicationName
        preferences[
            MedicationDeletionPreferenceKeys.medicationUpdatedAt
        ] = preview.medicationUpdatedAtEpochMillis
        preferences[
            MedicationDeletionPreferenceKeys.scheduleSeriesCount
        ] = preview.scheduleSeriesCount
        preferences[
            MedicationDeletionPreferenceKeys.scheduleVersionCount
        ] = preview.scheduleVersionCount
        preferences[
            MedicationDeletionPreferenceKeys.scheduleTimeCount
        ] = preview.scheduleTimeCount
        preferences[
            MedicationDeletionPreferenceKeys.occurrenceCount
        ] = preview.occurrenceCount
        preferences[
            MedicationDeletionPreferenceKeys.caregiverReportCount
        ] = preview.caregiverReportCount
        preferences[
            MedicationDeletionPreferenceKeys.scheduleSeriesIds
        ] = marker.scheduleSeriesIds
        preferences[
            MedicationDeletionPreferenceKeys.occurrenceIds
        ] = marker.occurrenceIds
        preferences[
            MedicationDeletionPreferenceKeys.stage
        ] = marker.stage.name
        preferences[
            MedicationDeletionPreferenceKeys.startedAt
        ] = marker.startedAtEpochMillis
        preferences[
            MedicationDeletionPreferenceKeys.checksum
        ] = marker.checksum
    }

    private fun removeMarkerKeys(
        preferences: MutablePreferences,
    ) {
        medicationMarkerKeys().forEach { key ->
            preferences.remove(key)
        }
    }

    private fun corruptedPartial():
        MedicationDeletionMarkerReadResult =
        MedicationDeletionMarkerReadResult
            .Corrupted(
                reason =
                    DeletionMarkerCorruptionReason
                        .PARTIAL_MARKER,
            )

    private fun medicationMarkerKeys():
        Set<Preferences.Key<*>> =
        setOf(
            MedicationDeletionPreferenceKeys.version,
            MedicationDeletionPreferenceKeys.medicationId,
            MedicationDeletionPreferenceKeys.medicationName,
            MedicationDeletionPreferenceKeys.medicationUpdatedAt,
            MedicationDeletionPreferenceKeys.scheduleSeriesCount,
            MedicationDeletionPreferenceKeys.scheduleVersionCount,
            MedicationDeletionPreferenceKeys.scheduleTimeCount,
            MedicationDeletionPreferenceKeys.occurrenceCount,
            MedicationDeletionPreferenceKeys.caregiverReportCount,
            MedicationDeletionPreferenceKeys.scheduleSeriesIds,
            MedicationDeletionPreferenceKeys.occurrenceIds,
            MedicationDeletionPreferenceKeys.stage,
            MedicationDeletionPreferenceKeys.startedAt,
            MedicationDeletionPreferenceKeys.checksum,
        )
}
