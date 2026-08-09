# CarePack Engineering Context

## Architecture

CarePack is a single-module Android application with manual dependency injection in `AppContainer`. Pure interfaces, models, and policies remain under `domain`. Room-backed implementations are under `data/service`. Android platform gateways are under `reminder`, `reporting`, and `settings/deletion`. The application intentionally does not add Hilt, Koin, SQLCipher, a repository wrapper for every DAO, or a multi-module structure.

## Shared operation ordering

`AppOperationGate` serializes care-plan mutations, report mutations, reminder reconciliation, permanent medication deletion, delete-all, and recovery. `AppReconciler` is the shared entry-point orchestrator and runs in this order:

1. permanent medication deletion recovery;
2. delete-all recovery;
3. corrupted or retry marker handling;
4. bounded occurrence maintenance;
5. reminder reconciliation.

`MainActivity`, alarm delivery, boot, time, timezone, package replacement, exact-alarm capability change, and retry broadcasts use that ordering.

## Time policy

Schedule zones are snapshots and are not rewritten when the device timezone changes. For an invalid local time in a DST gap, the local time is moved to the first valid local date-time after the gap. For an overlap, the offset that produces the earlier instant is selected and one occurrence is created. Preview, persistence, reports, and alarms use the same resolver result.

## Destructive-operation policy

Markers are versioned, checksum-validated, and fail closed. Partial fields, unknown versions, unknown stages, invalid values, checksum mismatch, and storage read failure are not treated as an absent marker. Permanent medication recovery stores target schedule-series and occurrence identities before database deletion and never performs global cleanup. Delete-all alone is permitted to perform global platform cleanup.

## Diagnostic policy

Diagnostics are debug-only and bounded. They contain operation stage, failure category, and short non-reversible tokens only. Raw recipient names, medication names, instructions, dose text, raw identifiers, and exception messages are prohibited.
