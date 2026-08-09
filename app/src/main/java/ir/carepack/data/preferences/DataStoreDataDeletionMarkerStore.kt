package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import ir.carepack.settings.deletion.DataDeletionMarker
import ir.carepack.settings.deletion.DataDeletionMarkerReadResult
import ir.carepack.settings.deletion.DataDeletionMarkerStage
import ir.carepack.settings.deletion.DataDeletionMarkerStore
import ir.carepack.settings.deletion.DeletionMarkerCorruptionReason
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreDataDeletionMarkerStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) : DataDeletionMarkerStore {

    constructor(context: Context) :
        this(
            dataStore =
                context.applicationContext
                    .carePackDataStore,
        )

    override val state:
        Flow<DataDeletionMarkerReadResult> =
        dataStore
            .data
            .map(
                ::decodeMarker,
            )
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(
                        DataDeletionMarkerReadResult
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
        marker: DataDeletionMarker,
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
        operationId: String,
        stage: DataDeletionMarkerStage,
    ) {
        val target = operationId.trim()
        require(target.isNotBlank())

        dataStore
            .edit { preferences ->
                val marker =
                    (
                        decodeMarker(preferences) as?
                            DataDeletionMarkerReadResult.Valid
                    )?.marker
                        ?: error(
                            "Data deletion marker is not valid.",
                        )

                check(marker.operationId == target)

                writeMarker(
                    preferences = preferences,
                    marker = marker.withStage(stage),
                )
            }
    }

    override suspend fun clear(
        operationId: String,
    ) {
        val target = operationId.trim()
        require(target.isNotBlank())

        dataStore
            .edit { preferences ->
                if (
                    preferences[
                        DataDeletionPreferenceKeys.operationId
                    ] == target
                ) {
                    dataMarkerKeys().forEach { key ->
                        preferences.remove(key)
                    }
                }
            }
    }

    private fun decodeMarker(
        preferences: Preferences,
    ): DataDeletionMarkerReadResult {
        val keys = preferences.asMap().keys
        val markerKeys = dataMarkerKeys()

        if (keys.none { it in markerKeys }) {
            return DataDeletionMarkerReadResult.Absent
        }

        if (!keys.containsAll(markerKeys)) {
            return DataDeletionMarkerReadResult
                .Corrupted(
                    reason =
                        DeletionMarkerCorruptionReason
                            .PARTIAL_MARKER,
                )
        }

        val version =
            preferences[DataDeletionPreferenceKeys.version]
                ?: return DataDeletionMarkerReadResult
                    .Corrupted(
                        reason =
                            DeletionMarkerCorruptionReason
                                .PARTIAL_MARKER,
                    )

        if (version != DataDeletionMarker.CURRENT_VERSION) {
            return DataDeletionMarkerReadResult
                .Corrupted(
                    reason =
                        DeletionMarkerCorruptionReason
                            .UNKNOWN_VERSION,
                )
        }

        val stage =
            preferences[DataDeletionPreferenceKeys.stage]
                ?.let { storedStage ->
                    DataDeletionMarkerStage.entries
                        .firstOrNull { candidate ->
                            candidate.name == storedStage
                        }
                }
                ?: return DataDeletionMarkerReadResult
                    .Corrupted(
                        reason =
                            DeletionMarkerCorruptionReason
                                .INVALID_STAGE,
                    )

        val marker =
            runCatching {
                DataDeletionMarker(
                    version = version,
                    operationId =
                        checkNotNull(
                            preferences[
                                DataDeletionPreferenceKeys.operationId
                            ],
                        ),
                    stage = stage,
                    startedAtEpochMillis =
                        checkNotNull(
                            preferences[
                                DataDeletionPreferenceKeys.startedAt
                            ],
                        ),
                    checksum =
                        checkNotNull(
                            preferences[
                                DataDeletionPreferenceKeys.checksum
                            ],
                        ),
                )
            }.getOrElse {
                return DataDeletionMarkerReadResult
                    .Corrupted(
                        reason =
                            DeletionMarkerCorruptionReason
                                .INVALID_VALUE,
                    )
            }

        if (!marker.hasValidChecksum()) {
            return DataDeletionMarkerReadResult
                .Corrupted(
                    reason =
                        DeletionMarkerCorruptionReason
                            .CHECKSUM_MISMATCH,
                )
        }

        return DataDeletionMarkerReadResult
            .Valid(
                marker = marker,
            )
    }

    private fun writeMarker(
        preferences: MutablePreferences,
        marker: DataDeletionMarker,
    ) {
        preferences[DataDeletionPreferenceKeys.version] =
            marker.version
        preferences[DataDeletionPreferenceKeys.operationId] =
            marker.operationId
        preferences[DataDeletionPreferenceKeys.stage] =
            marker.stage.name
        preferences[DataDeletionPreferenceKeys.startedAt] =
            marker.startedAtEpochMillis
        preferences[DataDeletionPreferenceKeys.checksum] =
            marker.checksum
    }

    private fun dataMarkerKeys():
        Set<Preferences.Key<*>> =
        setOf(
            DataDeletionPreferenceKeys.version,
            DataDeletionPreferenceKeys.operationId,
            DataDeletionPreferenceKeys.stage,
            DataDeletionPreferenceKeys.startedAt,
            DataDeletionPreferenceKeys.checksum,
        )
}
