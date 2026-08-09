# CarePack 1.0 Release Contract

A release candidate may be considered ready for independent re-audit only after every automated gate and manual check below has been executed for the exact release commit and its evidence retained. Manual evidence may live outside Git but must be cryptographically hashed and keyed to that exact commit. This document does not claim that any command has run or passed.

## Repository and source gate

- The repository contains product code, tests, release documentation, and permanent operational tooling only.
- One-shot remediation guides and `apply-carepack-*` migration/remediation scripts are not part of the permanent repository.
- No JKS, keystore properties, local properties, environment file, raw device dump, or sensitive log may be tracked or uploaded as a source artifact.
- The release source archive must be generated from `git archive HEAD`, scanned with `tools/verify-artifact-hygiene.ps1`, hashed with SHA-256, and retained as release evidence.
- `git diff --check` must pass.

## Wrapper and reproducibility gate

- Gradle Wrapper files `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, and the tool-generated `gradle/wrapper/gradle-wrapper.jar` must be tracked.
- `gradlew` must retain Unix executable mode `100755`.
- The wrapper must report Gradle 8.13 on both Windows and Linux fresh checkouts.
- A strict fresh-cache debug build must pass on Windows and Linux without a separately installed Gradle distribution.
- `mavenLocal()` must remain disabled by default and may only be enabled by the explicit `-PcarepackUseMavenLocal=true` local-development flag.
- Enabling the local-development flag against an empty Maven local repository must not change the resolved debug runtime dependency graph.

## Dependency and supply-chain gate

- `gradle/verification-metadata.xml`, `app/gradle.lockfile`, and `settings-gradle.lockfile` must be generated from the real dependency graph and committed.
- `tools/verify-supply-chain.ps1` must pass and must confirm Gradle-generated SHA-256 verification metadata, tracked lock state, deterministic wrapper state, no dynamic/SNAPSHOT declarations, and default-disabled Maven local resolution.
- Pull requests that modify dependency verification metadata, lockfiles, or wrapper supply-chain artifacts require the `supply-chain-reviewed` review label before CI may pass.
- Strict dependency verification must pass on clean/fresh resolution.
- The checksum-tamper negative test must demonstrate deliberate verification failure for a modified artifact checksum.
- A dependency report must be generated for the release commit.
- The release-day online dependency audit must retain:
  - current open Dependabot alert evidence with zero open alerts;
  - the GitHub dependency-graph SPDX SBOM;
  - an SPDX-derived dependency license inventory;
  - the dependency-audit summary.
- Failure to query the online security/SBOM evidence is a release failure, not a skipped gate.

## Privacy gate

- Merged release manifest contains no `USE_FULL_SCREEN_INTENT`, `showWhenLocked`, or `turnScreenOn`.
- A locked-device reminder exposes no recipient, medication, instruction, dose, report state, or report action.
- Tapping the reminder while locked cannot navigate to detail before unlock.
- Notification content and public version are generic.

## Reliability gate

- Reminder generation and delivery are simulated for at least 30 days without foreground activity.
- Boot, time, timezone, package replacement, exact-alarm capability, and retry entry points execute deletion recovery before maintenance and reconciliation.
- Receiver work is bounded by timeout, propagates cancellation, records safe failure classification, finishes exactly once, and schedules no more than three bounded retries.
- A successful database commit followed by reminder failure preserves the committed data and exposes a recoverable reminder health state.

## Data integrity gate

- Delete-all and permanent medication deletion cannot interleave with each other, care-plan mutation, report mutation, snooze mutation, or reminder reconciliation outside `AppOperationGate`.
- Target medication recovery leaves another medication's alarms, snoozes, notifications, and database graph unchanged.
- Marker corruption remains visible and blocks ordinary reconciliation until resolved.
- `tools/verify-room-schema.ps1` must regenerate the Room schema through the platform-appropriate wrapper and prove byte-equivalent parity with the committed schema baseline.
- Raw Room invariant tests must reject invalid minute/range/pattern/open-version state.

## Time and wording gate

- Berlin and New York gap and overlap tests prove preview, persisted instant, and alarm instant parity.
- Presentation contains caregiver-report wording only.
- Today, 7-day, and 30-day report numbers and times use Persian digits consistently.
- Share chooser and clipboard metadata match the report period and contain no recipient or medication metadata.

## Accessibility and performance gate

- `docs/accessibility-release-matrix.md` remains the mandatory manual R-23 audit checklist and must not contain predeclared PASS results.
- Manual accessibility evidence is not synthesized, inferred from screenshots, or represented as an automated CI result.
- The automated `AccessibilityReleaseMatrixTest` remains part of instrumentation and may detect structural or semantic regressions, but it is not a substitute for genuine manual TalkBack/focus, 200% font, RTL, 320dp, landscape, dark/light, and Switch Access/keyboard verification.
- This release-readiness branch may pass automated build, test, CI, supply-chain, privacy, data-integrity, performance, and release gates while manual R-23 remains explicitly unexecuted.
- The original 100/100 Definition of Done must not be claimed until the manual R-23 matrix is genuinely executed on the release artifact.
- Long-history query-plan and benchmark evidence must be retained. Indexes or query rewrites are accepted only when evidence shows a bottleneck.

## CI and release gate

The permanent authoritative automation is `.github/workflows/android.yml` together with the operational scripts under `tools/`.

A release-ready CI run must include:

1. wrapper validation;
2. secret scan;
3. `git diff --check`;
4. permanent supply-chain verification;
5. cross-platform Room schema regeneration/compare;
6. JVM compile, unit tests, lint, debug APK, Android-test compile and Android-test APK;
7. explicit executed/skipped/failed test accounting;
8. emulator instrumentation;
9. Windows/Linux fresh-checkout reproducibility;
10. dependency graph parity with empty opt-in Maven local;
11. release dependency report plus online CVE/SBOM/license evidence;
12. tracked-only source archive plus SHA-256 and artifact hygiene;
13. minified signed release APK/AAB;
14. merged-manifest privacy contract;
15. signature/hash inspection;
16. release startup smoke;
17. uploaded verification and release evidence.

The Xiaomi physical-device core suite remains required release evidence in addition to hosted CI. A hosted emulator does not replace the recorded Xiaomi device evidence.

## Final independent re-audit

No 100/100 claim is permitted until the final repository, CI artifacts, release artifacts, device evidence, genuine manual R-23 accessibility evidence, supply-chain evidence, and source archive have been independently reviewed with:

- 0 Critical findings;
- 0 High findings;
- 0 Medium findings;
- 0 Low findings;
- 0 secret exposure;
- 0 ambiguous skipped critical tests;
- reproducible build/release evidence.
