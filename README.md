# CarePack Farsi 1.0

CarePack Farsi is a local-only Android app for one caregiver supporting one care recipient.

It helps the caregiver create medication schedules, view Today and Recent History, record caregiver reports, receive local reminders, share a plain-text Today Report, review privacy information inside the app, and delete all app data from the device.

CarePack is not a medical device and does not provide medical advice, diagnosis, dosage calculation, medication interaction checking, emergency guidance, or verification that medication was taken.

## Product boundaries

CarePack Farsi 1.0 does not include accounts, backend services, cloud sync, analytics, advertising, OCR, camera capture, PDF generation, multiple care recipients, remote caregiver dashboards, or Internet permission.

The app stores domain data locally on the device. Room is the source of truth for care-plan, occurrence, and caregiver-report data. DataStore Preferences stores local app preferences.

CarePack 1.0 uses one final Room database schema version.

## Privacy model

CarePack is local-only.

The app does not request Internet permission and does not send care-plan, medication, report, reminder, or recipient data to a server.

Privacy information is available inside the app and in:

- `docs/privacy-policy.md`

Publisher:

Mahdi Pakravan

Contact:

mahdipakravan1998@gmail.com

## Tech stack

- Kotlin
- Native Android
- Jetpack Compose Material 3
- Room
- Coroutines and Flow
- DataStore Preferences
- AlarmManager
- Android notifications
- Manual dependency injection

## Build and verification

Run from the repository root:

    gradlew.bat clean
    gradlew.bat testDebugUnitTest
    tools\run-xiaomi-android-tests.cmd
    gradlew.bat assembleDebug

Release builds require a local signing key and `keystore.properties`, which must not be committed.

    gradlew.bat assembleRelease
    gradlew.bat bundleRelease

## Release signing

Create `keystore.properties` in the repository root using local secret values:

    storeFile=C:\\Users\\mahdi\\AndroidStudioProjects\\CarePack\\release\\carepack-release.jks
    storePassword=your-local-keystore-password
    keyAlias=carepack-release
    keyPassword=your-local-key-password

The keystore and passwords must remain outside Git.

## Documentation

The repository keeps only the minimum project documentation:

- `CONTEXT.md` — domain glossary.
- `docs/CarePack-1.0-Release-Contract.md` — product and release contract.
- `docs/privacy-policy.md` — privacy policy text.

Store-specific listing text, screenshots, panel-only materials, and release evidence are kept outside the repository.
