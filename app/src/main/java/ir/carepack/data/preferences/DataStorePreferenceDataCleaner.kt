package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import ir.carepack.settings.deletion.PreferenceDataCleaner

class DataStorePreferenceDataCleaner(
    context: Context,
) : PreferenceDataCleaner {
    private val applicationContext =
        context.applicationContext

    override suspend fun clearAllPreservingOperationMarkers() {
        applicationContext
            .carePackDataStore
            .edit { preferences ->
                val dataDeletionVersion =
                    preferences[DataDeletionPreferenceKeys.version]
                val dataDeletionOperationId =
                    preferences[DataDeletionPreferenceKeys.operationId]
                val dataDeletionStage =
                    preferences[DataDeletionPreferenceKeys.stage]
                val dataDeletionStartedAt =
                    preferences[DataDeletionPreferenceKeys.startedAt]
                val dataDeletionChecksum =
                    preferences[DataDeletionPreferenceKeys.checksum]

                val medicationDeletionVersion =
                    preferences[MedicationDeletionPreferenceKeys.version]
                val medicationId =
                    preferences[MedicationDeletionPreferenceKeys.medicationId]
                val medicationName =
                    preferences[MedicationDeletionPreferenceKeys.medicationName]
                val medicationUpdatedAt =
                    preferences[MedicationDeletionPreferenceKeys.medicationUpdatedAt]
                val scheduleSeriesCount =
                    preferences[MedicationDeletionPreferenceKeys.scheduleSeriesCount]
                val scheduleVersionCount =
                    preferences[MedicationDeletionPreferenceKeys.scheduleVersionCount]
                val scheduleTimeCount =
                    preferences[MedicationDeletionPreferenceKeys.scheduleTimeCount]
                val occurrenceCount =
                    preferences[MedicationDeletionPreferenceKeys.occurrenceCount]
                val caregiverReportCount =
                    preferences[MedicationDeletionPreferenceKeys.caregiverReportCount]
                val scheduleSeriesIds =
                    preferences[MedicationDeletionPreferenceKeys.scheduleSeriesIds]
                val occurrenceIds =
                    preferences[MedicationDeletionPreferenceKeys.occurrenceIds]
                val medicationDeletionStage =
                    preferences[MedicationDeletionPreferenceKeys.stage]
                val medicationDeletionStartedAt =
                    preferences[MedicationDeletionPreferenceKeys.startedAt]
                val medicationDeletionChecksum =
                    preferences[MedicationDeletionPreferenceKeys.checksum]

                preferences.clear()

                if (
                    dataDeletionVersion != null &&
                    dataDeletionOperationId != null &&
                    dataDeletionStage != null &&
                    dataDeletionStartedAt != null &&
                    dataDeletionChecksum != null
                ) {
                    preferences[DataDeletionPreferenceKeys.version] =
                        dataDeletionVersion
                    preferences[DataDeletionPreferenceKeys.operationId] =
                        dataDeletionOperationId
                    preferences[DataDeletionPreferenceKeys.stage] =
                        dataDeletionStage
                    preferences[DataDeletionPreferenceKeys.startedAt] =
                        dataDeletionStartedAt
                    preferences[DataDeletionPreferenceKeys.checksum] =
                        dataDeletionChecksum
                }

                if (
                    medicationDeletionVersion != null &&
                    medicationId != null &&
                    medicationName != null &&
                    medicationUpdatedAt != null &&
                    scheduleSeriesCount != null &&
                    scheduleVersionCount != null &&
                    scheduleTimeCount != null &&
                    occurrenceCount != null &&
                    caregiverReportCount != null &&
                    scheduleSeriesIds != null &&
                    occurrenceIds != null &&
                    medicationDeletionStage != null &&
                    medicationDeletionStartedAt != null &&
                    medicationDeletionChecksum != null
                ) {
                    preferences[MedicationDeletionPreferenceKeys.version] =
                        medicationDeletionVersion
                    preferences[MedicationDeletionPreferenceKeys.medicationId] =
                        medicationId
                    preferences[MedicationDeletionPreferenceKeys.medicationName] =
                        medicationName
                    preferences[MedicationDeletionPreferenceKeys.medicationUpdatedAt] =
                        medicationUpdatedAt
                    preferences[MedicationDeletionPreferenceKeys.scheduleSeriesCount] =
                        scheduleSeriesCount
                    preferences[MedicationDeletionPreferenceKeys.scheduleVersionCount] =
                        scheduleVersionCount
                    preferences[MedicationDeletionPreferenceKeys.scheduleTimeCount] =
                        scheduleTimeCount
                    preferences[MedicationDeletionPreferenceKeys.occurrenceCount] =
                        occurrenceCount
                    preferences[MedicationDeletionPreferenceKeys.caregiverReportCount] =
                        caregiverReportCount
                    preferences[MedicationDeletionPreferenceKeys.scheduleSeriesIds] =
                        scheduleSeriesIds
                    preferences[MedicationDeletionPreferenceKeys.occurrenceIds] =
                        occurrenceIds
                    preferences[MedicationDeletionPreferenceKeys.stage] =
                        medicationDeletionStage
                    preferences[MedicationDeletionPreferenceKeys.startedAt] =
                        medicationDeletionStartedAt
                    preferences[MedicationDeletionPreferenceKeys.checksum] =
                        medicationDeletionChecksum
                }
            }
    }
}
