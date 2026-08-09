package ir.carepack.data.preferences

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

internal object MedicationDeletionPreferenceKeys {
    val version =
        intPreferencesKey(
            "medication_deletion_marker_version",
        )

    val medicationId =
        stringPreferencesKey(
            "medication_deletion_target_id",
        )

    val medicationName =
        stringPreferencesKey(
            "medication_deletion_target_name",
        )

    val medicationUpdatedAt =
        longPreferencesKey(
            "medication_deletion_expected_updated_at",
        )

    val scheduleSeriesCount =
        intPreferencesKey(
            "medication_deletion_schedule_series_count",
        )

    val scheduleVersionCount =
        intPreferencesKey(
            "medication_deletion_schedule_version_count",
        )

    val scheduleTimeCount =
        intPreferencesKey(
            "medication_deletion_schedule_time_count",
        )

    val occurrenceCount =
        intPreferencesKey(
            "medication_deletion_occurrence_count",
        )

    val caregiverReportCount =
        intPreferencesKey(
            "medication_deletion_caregiver_report_count",
        )

    val scheduleSeriesIds =
        stringSetPreferencesKey(
            "medication_deletion_schedule_series_ids",
        )

    val occurrenceIds =
        stringSetPreferencesKey(
            "medication_deletion_occurrence_ids",
        )

    val stage =
        stringPreferencesKey(
            "medication_deletion_stage",
        )

    val startedAt =
        longPreferencesKey(
            "medication_deletion_started_at",
        )

    val checksum =
        stringPreferencesKey(
            "medication_deletion_checksum",
        )
}

internal object DataDeletionPreferenceKeys {
    val version =
        intPreferencesKey(
            "data_deletion_marker_version",
        )

    val operationId =
        stringPreferencesKey(
            "data_deletion_operation_id",
        )

    val stage =
        stringPreferencesKey(
            "data_deletion_stage",
        )

    val startedAt =
        longPreferencesKey(
            "data_deletion_started_at",
        )

    val checksum =
        stringPreferencesKey(
            "data_deletion_checksum",
        )
}
