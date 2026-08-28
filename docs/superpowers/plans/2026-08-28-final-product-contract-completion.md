# Final Product Contract Completion Implementation Plan

> **For implementation workers:** Execute each task test-first and keep the checkboxes current.

**Goal:** Close the remaining production and automated-regression gaps in the finalized CarePack product contract without adding an occurrence/report timeline to archived medication detail.

**Architecture:** Add a focused onboarding presentation-state ViewModel that observes the persisted user-experience store and owns only simple-mode save state, failure feedback, duplicate-action suppression, and retry. Keep lifecycle, report, reminder, timezone, archive, and deletion implementations authoritative in their existing domain/service layers; add integration and Compose coverage around those existing boundaries.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX ViewModel/StateFlow, Room, DataStore abstraction, JUnit, kotlinx-coroutines-test, Android instrumentation tests, Gradle.

**Spec:** `C:\Users\mahdi\OneDrive\Documents\Notes\Notes\CarePack Final Product Decisions, Code Alignment, Snapshot-to-Patch Implementation Package & UX Foundation.md`

## Global Constraints

- Keep exactly four primary destinations: Today, Medications, Calendar, Settings.
- Preserve separate report and Remind Later eligibility policies and domain enforcement.
- Keep Room v1 schema unchanged; do not add a migration.
- Archived detail remains medication/lifecycle historical context only; do not add occurrence/report timeline UI.
- Persisted preferences are authoritative; save failures are visible, recoverable, and never swallowed.
- Preserve cancellation propagation and ViewModel coroutine ownership.
- Do not add Restore/reactivation or any archive mutation other than existing permanent delete.
- Automated verification is distinct from manual accessibility release verification.

---

### Task 1: Establish a clean baseline and audit the existing contract coverage

**Files:**
- Read: `C:\Users\mahdi\OneDrive\Documents\Notes\Notes\CarePack Final Product Decisions, Code Alignment, Snapshot-to-Patch Implementation Package & UX Foundation.md`
- Read: `docs/CarePack-1.0-Release-Contract.md`
- Read: lifecycle/report/reminder/timezone/onboarding implementation and tests.

**Produces:** A requirement-to-test checklist that distinguishes production gaps, automated regression gaps, and manual-only gates.

- [ ] **Step 1: Run the baseline JVM, lint, debug APK, and Android-test compilation tasks.**

Run: `gradlew.bat testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin assembleDebugAndroidTest`

Expected: Either a clean baseline or a recorded pre-existing failure before any source modification.

- [ ] **Step 2: Inspect the current four-destination navigation, Room schema version, and archive read-only path.**

Run: `rg -n "PrimaryDestination|RoomDatabase|version =|ArchivedMedication|Restore" app/src/main app/src/androidTest app/src/test`

Expected: Four primary destinations, schema version unchanged, and no Restore surface.

### Task 2: Add the failing unit tests for onboarding simple-mode persistence

**Files:**
- Create: `app/src/test/java/ir/carepack/feature/onboarding/OnboardingSimpleModeViewModelTest.kt`
- Modify: `app/src/test/java/ir/carepack/testing/ReminderTestDoubles.kt` only if a reusable failing `UserExperiencePreferenceStore` cannot be expressed locally.

**Interfaces:**
- Consumes: `UserExperiencePreferenceStore.state`, `setSeniorMode(SeniorMode)`.
- Produces: desired ViewModel state fields `preferenceState`, `isSaving`, `errorMessage`, and retry method `retryLastSelection()`.

- [ ] **Step 1: Write tests that request SIMPLE and STANDARD through a failing store.**

Assert that the persisted mode stays unchanged, `isSaving` returns false, a visible error appears, and retry repeats exactly the requested mode after the store is made successful.

- [ ] **Step 2: Run the focused test and verify it fails because the ViewModel is absent.**

Run: `gradlew.bat testDebugUnitTest --tests "ir.carepack.feature.onboarding.OnboardingSimpleModeViewModelTest"`

Expected: Compilation failure naming the missing ViewModel/state API.

### Task 3: Implement the minimal onboarding persistence controller and surface its states

**Files:**
- Create: `app/src/main/java/ir/carepack/feature/onboarding/OnboardingSimpleModeViewModel.kt`
- Modify: `app/src/main/java/ir/carepack/feature/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/ir/carepack/app/CarePackApp.kt`
- Test: `app/src/test/java/ir/carepack/feature/onboarding/OnboardingSimpleModeViewModelTest.kt`
- Test: `app/src/androidTest/java/ir/carepack/ui/OnboardingComposeTest.kt`

**Interfaces:**
- Produces: `OnboardingSimpleModeUiState(preferenceState, isSaving, errorMessage)` and methods `selectSimpleMode()`, `keepStandardMode()`, `retryLastSelection()`.
- Consumes: `UserExperiencePreferenceStore` only; navigation remains owned by `CarePackApp`.

- [ ] **Step 1: Implement the ViewModel from the failing tests.**

Use a `StateFlow` that combines persisted store state with transient save/error state. Ignore a new request while saving. On success clear transient state; on non-cancellation failure expose a Persian retry message and retain persisted mode; rethrow `CancellationException`.

- [ ] **Step 2: Bind `OnboardingScreen` to saved state, busy state, and an inline live error/retry action.**

Disable both mode-selection controls during save, retain their existing accessible labels/tags, show the error in a polite live region, and call the ViewModel retry action. Do not optimistically set `simpleModeEnabled`.

- [ ] **Step 3: Replace app-route coroutine writes with the ViewModel factory and callbacks.**

The route collects ViewModel state using lifecycle-aware collection. Remove direct `setSeniorMode` coroutines from the onboarding destination.

- [ ] **Step 4: Run the focused JVM tests and verify they pass.**

Run: `gradlew.bat testDebugUnitTest --tests "ir.carepack.feature.onboarding.OnboardingSimpleModeViewModelTest"`

Expected: SIMPLE failure, STANDARD failure, successful persistence, retry, and duplicate suppression pass.

### Task 4: Add lifecycle, archive, deletion, and historical-integrity regression coverage

**Files:**
- Modify: `app/src/androidTest/java/ir/carepack/domain/careplan/FinalMedicationLifecycleIntegrationTest.kt`
- Modify: `app/src/androidTest/java/ir/carepack/settings/deletion/MedicationDeletionIntegrationTest.kt`
- Modify: `app/src/androidTest/java/ir/carepack/ui/FinalProductAlignmentComposeTest.kt`

**Interfaces:**
- Consumes: `CarePlanService`, `MedicationDeletionCoordinator`, Room fixture, archived list/detail UI state.
- Produces: proof that Ended/Archived are non-editable, permanent deletion is available where valid, archive is one-way, and history remains unchanged before destructive deletion.

- [ ] **Step 1: Add failing integration assertions for Ended and Archived schedule mutation rejection.**

Cover `updateSchedule` as well as existing add/edit medication restrictions, and assert an Ended medication cannot become Active.

- [ ] **Step 2: Add failing lifecycle assertions for a same-name later treatment episode.**

Create/End the first medicine, create the same name again, and assert a distinct medication id while the prior history remains unchanged.

- [ ] **Step 3: Add failing deletion integration scenarios for Ended and Archived medications.**

Use the existing preview/confirmation coordinator path, assert each target is removable, and prove schedules/occurrences/reports remain intact through End/Archive until permanent delete is explicitly confirmed.

- [ ] **Step 4: Add archived-detail Compose assertions.**

Assert medication metadata plus ended/archive timestamps are visible; permanent delete entry is present; Restore/Edit/Add Schedule/Edit Schedule nodes are absent.

- [ ] **Step 5: Run the targeted instrumentation compilation and tests.**

Run: `gradlew.bat compileDebugAndroidTestKotlin` followed by connected execution when a device/emulator is available.

### Task 5: Add occurrence action, entry-point parity, and notification-independence regression coverage

**Files:**
- Modify: `app/src/androidTest/java/ir/carepack/domain/report/CaregiverReportEligibilityIntegrationTest.kt`
- Modify: `app/src/test/java/ir/carepack/domain/occurrence/OccurrenceActionEligibilityTest.kt`
- Modify: `app/src/test/java/ir/carepack/domain/reminder/DefaultReminderCoordinatorTest.kt`
- Modify: relevant Today/detail/notification Compose or ViewModel tests only where an entry point lacks coverage.

**Interfaces:**
- Consumes: `ReportMutationEligibility`, `RemindLaterEligibility`, report service, Today/detail mapped state, reminder coordinator.
- Produces: parity proof that each entry point mirrors, but cannot replace, domain enforcement.

- [ ] **Step 1: Add the failing cancelled-existing-report mutation case.**

Report an occurrence while active, end it so the occurrence is cancelled, and assert further report mutation is rejected without deleting the existing report.

- [ ] **Step 2: Add UI/domain parity cases for upcoming and cancelled occurrences.**

Assert Today, occurrence detail, and notification-driven detail expose disabled/unavailable report state consistent with domain rejection.

- [ ] **Step 3: Add notification-delivery independence tests.**

Evaluate identical occurrence/clock eligibility with no notification delivery and a late-delivery simulation; assert report/remind eligibility is unchanged and Remind Later keeps the fixed ten-minute behavior.

- [ ] **Step 4: Run focused JVM tests.**

Run: `gradlew.bat testDebugUnitTest --tests "ir.carepack.domain.occurrence.OccurrenceActionEligibilityTest" --tests "ir.carepack.domain.reminder.DefaultReminderCoordinatorTest"`

Expected: all temporal, lifecycle, report-state, zone, and notification-independence cases pass.

### Task 6: Add timezone, preference-failure, empty-state, and Simple Mode parity regression coverage

**Files:**
- Modify: `app/src/androidTest/java/ir/carepack/ui/OnboardingComposeTest.kt`
- Modify: `app/src/androidTest/java/ir/carepack/ui/GlobalSimpleModeComposeTest.kt`
- Modify: `app/src/androidTest/java/ir/carepack/ui/FinalProductAlignmentComposeTest.kt`
- Modify: `app/src/test/java/ir/carepack/feature/settings/SettingsViewModelTest.kt` if present, otherwise create the focused equivalent.
- Modify: `app/src/androidTest/java/ir/carepack/reporting/ReportingIntegrationTest.kt` or the existing schedule-version integration test.

**Interfaces:**
- Consumes: failing preference stores, `TimezoneWarningBanner`, `CarePlanScreen` route/app, schedule service/version rows.
- Produces: no mutation on review/dismiss, persisted-state-authoritative failure behavior, all-archived Add availability, and equal access to core actions in both presentation modes.

- [ ] **Step 1: Add onboarding Compose failure/retry coverage with a store that throws.**

Assert both SIMPLE and STANDARD failures leave persisted selection unchanged, display a retryable error, re-enable controls, and recover after retry.

- [ ] **Step 2: Add Settings failure-injection assertions.**

Assert failure is visible and previous persisted mode/week-start remains selected; verify a later successful retry reconciles state.

- [ ] **Step 3: Add empty and all-archived Medications route assertions.**

Assert `add_medication` remains reachable and configured setup does not route back to onboarding.

- [ ] **Step 4: Add timezone review/dismiss and prospective-edit integrity tests.**

Assert review/dismiss alone changes no schedule, occurrence, or report. Then explicitly edit a future schedule and assert old history is preserved while a prospective version changes.

- [ ] **Step 5: Add Simple Mode parity coverage.**

For STANDARD and SIMPLE, assert access to Add Medication, report actions when domain-eligible, and Remind Later when eligible; assert the mode switch does not change domain data or rules.

### Task 7: Run full automated verification, fix in-scope failures, and create the requirement audit

**Files:**
- Create: `docs/final-product-contract-audit-2026-08-28.md`
- Test: all project-owned verification tasks.

- [ ] **Step 1: Run project verification gates.**

Run: `gradlew.bat testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin assembleDebugAndroidTest`

Then, if an Android device/emulator is available: `gradlew.bat connectedDebugAndroidTest`.

- [ ] **Step 2: Run project scripts that are directly applicable to changed scope.**

Run the Room schema, source-hygiene, and static release scripts when their documented inputs are present; record exact exit status.

- [ ] **Step 3: Audit the full source-of-truth requirement list.**

For every substantive product, data, navigation, test, and verification requirement, record exactly `Satisfied`, `Not Applicable` with reason, or `Remaining`. Do not mark manual TalkBack/font/RTL/320dp release checks as complete; place them under `Manual Release Verification Required`.

- [ ] **Step 4: Inspect the final diff and rerun any verification invalidated by a fix.**

Run: `git diff --check` and the full Gradle command above.

Expected: no production-code or automated-regression `Remaining` items in the final audit.
