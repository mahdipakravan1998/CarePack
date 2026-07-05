package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import ir.carepack.domain.reminder.SnoozedReminder
import ir.carepack.domain.reminder.SnoozedReminderStore
import java.io.IOException
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreSnoozedReminderStore(
    context: Context,
) : SnoozedReminderStore {

    private val applicationContext =
        context.applicationContext

    override val reminders:
            Flow<List<SnoozedReminder>> =
        applicationContext
            .carePackDataStore
            .data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map { preferences ->
                preferences[SNOOZED_REMINDERS]
                    .orEmpty()
                    .mapNotNull(
                        ::decodeReminder,
                    )
                    .distinctBy {
                        it.occurrenceId
                    }
                    .sortedBy {
                        it.remindAt
                    }
            }

    override suspend fun upsert(
        reminder: SnoozedReminder,
    ) {
        applicationContext
            .carePackDataStore
            .edit { preferences ->
                val current =
                    preferences[SNOOZED_REMINDERS]
                        .orEmpty()
                        .mapNotNull(
                            ::decodeReminder,
                        )
                        .filterNot {
                            it.occurrenceId ==
                                    reminder.occurrenceId
                        }

                preferences[SNOOZED_REMINDERS] =
                    (current + reminder)
                        .map(
                            ::encodeReminder,
                        )
                        .toSet()
            }
    }

    override suspend fun delete(
        occurrenceId: String,
    ) {
        val trimmedOccurrenceId =
            occurrenceId.trim()

        if (trimmedOccurrenceId.isBlank()) {
            return
        }

        applicationContext
            .carePackDataStore
            .edit { preferences ->
                val updated =
                    preferences[SNOOZED_REMINDERS]
                        .orEmpty()
                        .mapNotNull(
                            ::decodeReminder,
                        )
                        .filterNot {
                            it.occurrenceId ==
                                    trimmedOccurrenceId
                        }

                preferences[SNOOZED_REMINDERS] =
                    updated
                        .map(
                            ::encodeReminder,
                        )
                        .toSet()
            }
    }

    override suspend fun clear() {
        applicationContext
            .carePackDataStore
            .edit { preferences ->
                preferences.remove(
                    SNOOZED_REMINDERS,
                )
            }
    }

    private fun encodeReminder(
        reminder: SnoozedReminder,
    ): String {
        return listOf(
            encodeComponent(
                reminder.occurrenceId,
            ),
            reminder
                .remindAt
                .toEpochMilli()
                .toString(),
            reminder
                .createdAt
                .toEpochMilli()
                .toString(),
        ).joinToString(
            separator =
                FIELD_SEPARATOR,
        )
    }

    private fun decodeReminder(
        encoded: String,
    ): SnoozedReminder? {
        val parts =
            encoded.split(
                FIELD_SEPARATOR,
            )

        if (parts.size != ENCODED_PART_COUNT) {
            return null
        }

        return runCatching {
            SnoozedReminder(
                occurrenceId =
                    decodeComponent(
                        parts[0],
                    ),
                remindAt =
                    Instant.ofEpochMilli(
                        parts[1].toLong(),
                    ),
                createdAt =
                    Instant.ofEpochMilli(
                        parts[2].toLong(),
                    ),
            )
        }.getOrNull()
    }

    private fun encodeComponent(
        value: String,
    ): String {
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                value.toByteArray(
                    Charsets.UTF_8,
                ),
            )
    }

    private fun decodeComponent(
        value: String,
    ): String {
        return String(
            Base64
                .getUrlDecoder()
                .decode(value),
            Charsets.UTF_8,
        )
    }

    private companion object {
        val SNOOZED_REMINDERS =
            stringSetPreferencesKey(
                "snoozed_reminders",
            )

        const val FIELD_SEPARATOR =
            "|"

        const val ENCODED_PART_COUNT =
            3
    }
}
