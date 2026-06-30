# CarePack Domain Context

## Care Recipient

The single person whose medication schedule is recorded in CarePack.

Avoid: patient account, user profile, member, dependent list.

## Medication

A caregiver-entered medication name and instruction text copied from an existing source. CarePack does not validate the medication.

Avoid: verified drug, prescription entity, treatment.

## Schedule

The logical recurring plan for one medication, consisting of selected weekdays, one or more local times, optional date limits, and a fixed zone.

Avoid: dose recommendation, medical timing rule.

## Schedule Version

An immutable historical version of a schedule. Editing creates a new version; it does not rewrite old occurrences.

Avoid: overwrite, mutable schedule row.

## Occurrence

One scheduled instance for a specific local date and local time under one schedule version.

Avoid: consumed dose, administration proof, alarm event.

## Caregiver Report

The caregiver's explicit statement about what they know for an occurrence: Given, Not Given, or Unknown. The absence of a report is NoReport.

Avoid: medical confirmation, adherence proof.

## Lifecycle

The stored occurrence lifecycle: Active or Cancelled.

Avoid: upcoming lifecycle, overdue state.

## Report State

NoReport, ReportedAsGiven, ReportedAsNotGiven, or ReportedAsUnknown.

Avoid: dismissed, missed notification.

## Temporal Phase

A derived presentation classification computed from scheduledAt and current time: Upcoming, Due, or Past.

Avoid: stored phase, medical grace period.

## Today Report

A structured text report containing only today's non-cancelled occurrences and their report states.

Avoid: medical report, date-range report, PDF report.

## Recent History

The in-app display of today and the previous seven calendar days. It is not a retention policy.

Avoid: seven-day storage, automatic deletion.

## Reminder

A user-visible Android notification attempt associated with an occurrence. Delivery does not change its Caregiver Report.

Avoid: proof, administration event.

## Fixed Zone

The ZoneId stored with a schedule version. Device timezone changes do not silently convert it.

Avoid: floating local time, follow-device schedule.
