package ir.carepack.settings.deletion

import ir.carepack.core.concurrency.AppOperationGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DestructiveOperationInterleavingTest {

    @Test
    fun deleteAllAndReconcile_doNotInterleave() =
        assertSerialized("delete-all", "reconcile")

    @Test
    fun deleteAllAndMedicationDelete_doNotInterleave() =
        assertSerialized("delete-all", "medication-delete")

    @Test
    fun medicationDeleteAndCarePlanUpdate_doNotInterleave() =
        assertSerialized("medication-delete", "care-plan-update")

    @Test
    fun medicationDeleteAndAddSchedule_doNotInterleave() =
        assertSerialized("medication-delete", "add-schedule")

    @Test
    fun medicationDeleteAndStopArchive_doNotInterleave() =
        assertSerialized("medication-delete", "stop-or-archive")

    @Test
    fun medicationDeleteAndReportOrSnooze_doNotInterleave() =
        assertSerialized("medication-delete", "report-or-snooze")

    private fun assertSerialized(
        firstName: String,
        secondName: String,
    ) = runTest {
        val gate = AppOperationGate()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first =
            async {
                gate.withGate {
                    events += "$firstName-enter"
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                    events += "$firstName-exit"
                }
            }

        firstEntered.await()

        val second =
            async {
                gate.withGate {
                    events += "$secondName-enter"
                    events += "$secondName-exit"
                }
            }

        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(
            listOf(
                "$firstName-enter",
                "$firstName-exit",
                "$secondName-enter",
                "$secondName-exit",
            ),
            events,
        )
    }
}
