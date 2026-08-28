# CarePack UX Contract

## Primary navigation

The four primary destinations are Today, Medications, Calendar, and Settings. Secondary routes own normal back navigation. Archived medication management belongs under Medications.

## Preference persistence

`UserExperiencePreferenceStore.state` is authoritative for rendered saved preferences. A preference write has three observable states:

1. idle — persisted value is shown;
2. saving — conflicting selection controls are disabled;
3. failed — persisted value remains shown, a polite inline recovery message is shown, and retry repeats the failed selection.

Cancellation is never converted into user-visible storage failure.

## Medication lifecycle

Valid transitions are Active to Ended, then Ended to Archived, followed by permanent deletion where the existing safe deletion flow permits it. Ended and Archived medications cannot be reactivated, restored, edited, or assigned/edited schedules. Archived detail is read-only medication and lifecycle context, not a new occurrence/report timeline.

## Occurrence actions

Report mutation is domain-authoritative for active occurrences at or after `scheduledAt`. Remind Later is separately domain-authoritative for active, unreported, due occurrences on the same occurrence-local snapshot day. UI availability mirrors these policies but does not replace service enforcement.

## Timezone warning

Review is navigation only and Dismiss persists only warning dismissal. Neither action rewrites schedules, occurrences, reports, or timezone snapshots. A later explicit schedule edit remains prospective according to schedule versioning.
