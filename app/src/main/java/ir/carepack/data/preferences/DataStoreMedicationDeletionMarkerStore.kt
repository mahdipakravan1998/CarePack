package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import ir.carepack.settings.deletion.MedicationDeletionMarker
import ir.carepack.settings.deletion.MedicationDeletionMarkerStage
import ir.carepack.settings.deletion.MedicationDeletionMarkerStore
import ir.carepack.settings.deletion.MedicationDeletionPreview
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreMedicationDeletionMarkerStore(
    context: Context,
) : MedicationDeletionMarkerStore {

    private val applicationContext =
        context.applicationContext

    override val marker:
            Flow<MedicationDeletionMarker?> =
        applicationContext
            .carePackDataStore
            .data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(
                        emptyPreferences(),
                    )
                } else {
                    throw throwable
                }
            }
            .map(
                ::decodeMarker,
            )

    override suspend fun save(
        marker: MedicationDeletionMarker,
    ) {
        applicationContext
            .carePackDataStore
            .edit { preferences ->
                val preview =
                    marker.expectedPreview

                preferences[MEDICATION_ID] =
                    preview.medicationId

                preferences[MEDICATION_NAME] =
                    preview.medicationName

                preferences[MEDICATION_UPDATED_AT] =
                    preview.medicationUpdatedAtEpochMillis

                preferences[SCHEDULE_SERIES_COUNT] =
                    preview.scheduleSeriesCount

                preferences[SCHEDULE_VERSION_COUNT] =
                    preview.scheduleVersionCount

                preferences[SCHEDULE_TIME_COUNT] =
                    preview.scheduleTimeCount

                preferences[OCCURRENCE_COUNT] =
                    preview.occurrenceCount

                preferences[CAREGIVER_REPORT_COUNT] =
                    preview.caregiverReportCount

                preferences[SCHEDULE_SERIES_IDS] =
                    marker.scheduleSeriesIds

                preferences[MARKER_STAGE] =
                    marker.stage.name

                preferences[STARTED_AT] =
                    marker.startedAtEpochMillis
            }
    }

    override suspend fun updateStage(
        medicationId: String,
        stage: MedicationDeletionMarkerStage,
    ) {
        val trimmedMedicationId =
            medicationId.trim()

        require(trimmedMedicationId.isNotBlank())

        applicationContext
            .carePackDataStore
            .edit { preferences ->
                val storedMedicationId =
                    preferences[MEDICATION_ID]

                check(
                    storedMedicationId ==
                            trimmedMedicationId,
                ) {
                    "Medication deletion marker target changed unexpectedly."
                }

                preferences[MARKER_STAGE] =
                    stage.name
            }
    }

    override suspend fun clear(
        medicationId: String,
    ) {
        val trimmedMedicationId =
            medicationId.trim()

        require(trimmedMedicationId.isNotBlank())

        applicationContext
            .carePackDataStore
            .edit { preferences ->
                if (
                    preferences[MEDICATION_ID] ==
                    trimmedMedicationId
                ) {
                    removeMarkerKeys(
                        preferences = preferences,
                    )
                }
            }
    }

    private fun decodeMarker(
        preferences: Preferences,
    ): MedicationDeletionMarker? {
        val medicationId =
            preferences[MEDICATION_ID]
                ?.trim()
                .orEmpty()

        val medicationName =
            preferences[MEDICATION_NAME]
                ?.trim()
                .orEmpty()

        val medicationUpdatedAt =
            preferences[MEDICATION_UPDATED_AT]

        val scheduleSeriesCount =
            preferences[SCHEDULE_SERIES_COUNT]

        val scheduleVersionCount =
            preferences[SCHEDULE_VERSION_COUNT]

        val scheduleTimeCount =
            preferences[SCHEDULE_TIME_COUNT]

        val occurrenceCount =
            preferences[OCCURRENCE_COUNT]

        val caregiverReportCount =
            preferences[CAREGIVER_REPORT_COUNT]

        val scheduleSeriesIds =
            preferences[SCHEDULE_SERIES_IDS]

        val stage =
            preferences[MARKER_STAGE]
                ?.let { storedStage ->
                    runCatching {
                        MedicationDeletionMarkerStage
                            .valueOf(
                                storedStage,
                            )
                    }.getOrNull()
                }

        val startedAt =
            preferences[STARTED_AT]

        if (
            medicationId.isBlank() ||
            medicationName.isBlank() ||
            medicationUpdatedAt == null ||
            scheduleSeriesCount == null ||
            scheduleVersionCount == null ||
            scheduleTimeCount == null ||
            occurrenceCount == null ||
            caregiverReportCount == null ||
            scheduleSeriesIds == null ||
            stage == null ||
            startedAt == null
        ) {
            return null
        }

        return runCatching {
            MedicationDeletionMarker(
                expectedPreview =
                    MedicationDeletionPreview(
                        medicationId = medicationId,
                        medicationName = medicationName,
                        medicationUpdatedAtEpochMillis =
                            medicationUpdatedAt,
                        scheduleSeriesCount =
                            scheduleSeriesCount,
                        scheduleVersionCount =
                            scheduleVersionCount,
                        scheduleTimeCount =
                            scheduleTimeCount,
                        occurrenceCount =
                            occurrenceCount,
                        caregiverReportCount =
                            caregiverReportCount,
                    ),
                scheduleSeriesIds =
                    scheduleSeriesIds,
                stage = stage,
                startedAtEpochMillis =
                    startedAt,
            )
        }.getOrNull()
    }

    private fun removeMarkerKeys(
        preferences:
        androidx.datastore.preferences.core.MutablePreferences,
    ) {
        preferences.remove(MEDICATION_ID)
        preferences.remove(MEDICATION_NAME)
        preferences.remove(MEDICATION_UPDATED_AT)
        preferences.remove(SCHEDULE_SERIES_COUNT)
        preferences.remove(SCHEDULE_VERSION_COUNT)
        preferences.remove(SCHEDULE_TIME_COUNT)
        preferences.remove(OCCURRENCE_COUNT)
        preferences.remove(CAREGIVER_REPORT_COUNT)
        preferences.remove(SCHEDULE_SERIES_IDS)
        preferences.remove(MARKER_STAGE)
        preferences.remove(STARTED_AT)
    }

    private companion object {

        val MEDICATION_ID =
            stringPreferencesKey(
                "medication_deletion_target_id",
            )

        val MEDICATION_NAME =
            stringPreferencesKey(
                "medication_deletion_target_name",
            )

        val MEDICATION_UPDATED_AT =
            longPreferencesKey(
                "medication_deletion_expected_updated_at",
            )

        val SCHEDULE_SERIES_COUNT =
            intPreferencesKey(
                "medication_deletion_schedule_series_count",
            )

        val SCHEDULE_VERSION_COUNT =
            intPreferencesKey(
                "medication_deletion_schedule_version_count",
            )

        val SCHEDULE_TIME_COUNT =
            intPreferencesKey(
                "medication_deletion_schedule_time_count",
            )

        val OCCURRENCE_COUNT =
            intPreferencesKey(
                "medication_deletion_occurrence_count",
            )

        val CAREGIVER_REPORT_COUNT =
            intPreferencesKey(
                "medication_deletion_caregiver_report_count",
            )

        val SCHEDULE_SERIES_IDS =
            stringSetPreferencesKey(
                "medication_deletion_schedule_series_ids",
            )

        val MARKER_STAGE =
            stringPreferencesKey(
                "medication_deletion_stage",
            )

        val STARTED_AT =
            longPreferencesKey(
                "medication_deletion_started_at",
            )

    }
}
