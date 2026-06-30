# CarePack 1.0 Release Contract

CarePack Farsi 1.0 is a Persian, right-to-left, local-only Android app for one caregiver and one care recipient.

This document records the final repository-level contract for the implemented 1.0 project.

## Product scope

CarePack helps a caregiver manually record a care plan, view medication occurrences, record what is known about each occurrence, review recent history, preview today's text report, share it explicitly, use optional local reminders, and delete all local CarePack data.

CarePack is not a medical device, diagnosis system, prescription validator, dosage recommender, interaction checker, treatment system, or proof that medication was consumed.

## Application identity

- Package/application ID: `ir.carepack`
- Version name: `1.0.0`
- Version code: `1`
- Primary UI language: Persian
- Layout direction: RTL
- Target distribution context: Iranian Android markets such as Cafe Bazaar and Myket

## Privacy and network contract

CarePack is local-only.

The app must not declare `INTERNET`.

The app must not include analytics, advertising, backend sync, accounts, remote logging, marketing trackers, or cloud storage.

CarePack stores domain data in Room and preferences in DataStore. Android reminder state, notification permission, exact-alarm capability, displayed notifications, device time, and device timezone remain external platform state.

No privacy-policy URL is opened from inside the app.

The in-app Privacy screen contains only a short local-only privacy summary. The repository privacy document is `docs/privacy-policy.md`.

## Database contract

CarePack 1.0 has exactly one final Room schema version.

- `CarePackDatabase` version: `1`
- Schema file: `app/schemas/ir.carepack.data.local.CarePackDatabase/1.json`

No historical schema files, migration code, migration tests, or migration documentation are part of the final 1.0 repository.

The version-1 schema is the final first-release schema and contains the complete CarePack 1.0 data model.

## Domain storage contract

Room is the durable source of truth for:

- care recipient;
- medications;
- schedule versions and schedule times;
- occurrences;
- caregiver reports.

NoReport is represented by the absence of a CaregiverReport row. Unknown is an explicit report row.

Temporal phase and overdue presentation are derived at read time and are not stored.

Occurrence identity is immutable. Logical occurrence uniqueness is enforced by the database key equivalent to scheduleVersionId, local date, and local time.

## Validation contract

Final input limits are:

- Care Recipient display name: 1 to 120 characters after trimming;
- Medication name: 1 to 120 characters after trimming;
- Medication instruction: 1 to 1000 characters after trimming.

The service layer enforces these limits. UI validation must not be the only protection.

## Reminder contract

Reminders are optional. Denying notification permission or exact-alarm access must not block care-plan management, Today, reporting, history, privacy, or deletion.

Alarm firing, notification opening, and notification dismissal must never write caregiver reports.

Unlocked notification content may include medication name and scheduled time. Lock-screen public notification content must remain generic.

## Sharing contract

Today's report is plain text, contains today only, excludes cancelled occurrences from normal totals, distinguishes all report states, includes a non-medical disclaimer, and is shared only through explicit user action.

The recipient name may be omitted.

## Deletion contract

Delete All Data removes CarePack Room data, preferences, owned alarms, active CarePack notifications, and temporary files controlled by the app.

After deletion and restart, no CarePack domain data may reappear.

## Documentation contract

The repository intentionally keeps only minimal documentation:

- `README.md`
- `CONTEXT.md`
- `docs/CarePack-1.0-Release-Contract.md`
- `docs/privacy-policy.md`

Release evidence, reviewer notes, rollout notes, store submission notes, maintenance notes, and market-specific materials are not stored in the repository.
