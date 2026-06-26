package ir.carepack.app

import android.content.Context
import androidx.room.Room
import ir.carepack.core.id.IdSource
import ir.carepack.core.id.UuidIdSource
import ir.carepack.core.time.SystemZoneProvider
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.DatabaseMigrations
import ir.carepack.data.preferences.DataStoreSetupPreferenceStore
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.RoomCarePlanService
import ir.carepack.domain.occurrence.OccurrenceCandidateResolver
import ir.carepack.domain.occurrence.OccurrenceGenerator
import ir.carepack.domain.occurrence.RoomOccurrenceGenerator
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.RoomCaregiverReportService
import ir.carepack.domain.today.RoomTodayQueryService
import ir.carepack.domain.today.TodayQueryService
import java.time.Clock

class AppContainer(
    context: Context,
) {
    val clock: Clock =
        Clock.systemUTC()

    val zoneProvider: ZoneProvider =
        SystemZoneProvider()

    private val idSource: IdSource =
        UuidIdSource()

    val database: CarePackDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            CarePackDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .build()

    val occurrenceGenerator:
            OccurrenceGenerator =
        RoomOccurrenceGenerator(
            database = database,
            idSource = idSource,
            candidateResolver =
                OccurrenceCandidateResolver(),
        )

    val carePlanService: CarePlanService =
        RoomCarePlanService(
            database = database,
            occurrenceGenerator =
                occurrenceGenerator,
            clock = clock,
            idSource = idSource,
        )

    val caregiverReportService:
            CaregiverReportService =
        RoomCaregiverReportService(
            database = database,
            clock = clock,
        )

    val todayQueryService:
            TodayQueryService =
        RoomTodayQueryService(
            database = database,
        )

    val setupPreferenceStore:
            SetupPreferenceStore =
        DataStoreSetupPreferenceStore(
            context = context,
        )

    private companion object {
        const val DATABASE_NAME =
            "carepack.db"
    }
}
