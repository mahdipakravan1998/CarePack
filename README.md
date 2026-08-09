# CarePack

CarePack is an offline Android application for a caregiver to define medication schedules, receive local reminders, and record caregiver-reported outcomes. The application has no backend, account, cloud sync, analytics, advertising, remote logging, OCR, PDF generation, or medical validation.

## Product contract

- One local care recipient is supported.
- A medication can have multiple fixed-time or interval schedules.
- Occurrences preserve schedule-zone and medication-text snapshots.
- Today, Jalali calendar, 7-day and 30-day reports use caregiver-reported wording. They do not prove medication consumption.
- Simple Mode is a global presentation preference.
- Stop, archive, permanent medication deletion, and delete-all are distinct operations.
- Permanent medication deletion explicitly removes the target medication graph, its reports, alarms, snoozes, and notifications.
- Delete-all removes Room data, preferences, temporary files, alarms, snoozes, and notifications through a resumable operation marker.

## Reminder and privacy contract

Reminder occurrence generation maintains a bounded sliding window through foreground, alarm, boot, time, timezone, package-replaced, and retry entry points. Normal operation must continue for at least 30 days without opening the application. Android force-stop and vendor power-management restrictions remain platform limitations and must be explained to users rather than hidden.

CarePack does not use a full-screen intent. `MainActivity` is not shown over the lock screen and does not turn the screen on. Reminder notifications use private visibility with a generic public version. The notification itself contains no medication name, recipient name, instruction, dose, or report action. Occurrence detail is validated and displayed only after the device is unlocked.

## Build prerequisites

- JDK 17
- Android SDK with API 36
- The committed Gradle wrapper, including `gradlew`, `gradlew.bat`, `gradle-wrapper.properties`, and the tool-generated `gradle-wrapper.jar`

`mavenLocal()` is disabled by default. It can be enabled only for explicit local development:

```text
-PcarepackUseMavenLocal=true
```

Release signing reads these environment variables and requires the keystore path to be outside the repository:

```text
CAREPACK_KEYSTORE_PATH
CAREPACK_KEYSTORE_PASSWORD
CAREPACK_KEY_ALIAS
CAREPACK_KEY_PASSWORD
```

No signing value is read from a repository file or printed by the build.

## Verification

Run the commands in `docs/CarePack-1.0-Release-Contract.md`. Any statement that a build, test, signature, secret purge, device behavior, or score is successful must be supported by evidence from an actual execution and independent re-audit.
