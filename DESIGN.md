# CarePack Design Context

## Product register and audience

CarePack is a Persian, RTL, local-first medication-care application. Its product register is calm, legible, and task-focused: a caregiver must be able to understand an occurrence, complete a safe action, and recover from a failed preference write without guessing.

## Runtime design ownership

- `app/src/main/java/ir/carepack/ui/theme/CarePackTheme.kt` owns RTL direction, Material color scheme selection, and typography.
- `app/src/main/java/ir/carepack/ui/experience/CarePackExperience.kt` owns Standard/Simple spacing and minimum control sizes.
- `app/src/main/java/ir/carepack/ui/accessibility/AccessibilityModifiers.kt` owns semantic headings, polite live regions, traversal groups, and responsive action/control minimum heights.
- Feature screens consume those owners; they do not introduce screen-local visual tokens.

## Visual and interaction principles

- Persian copy is concise, direct, and action-specific.
- Simple Mode changes density, type scale, spacing, and grouping only; it never removes a core action or changes a business rule.
- Saving controls preserve their position and label; while a preference write is pending, mutually exclusive choices are disabled to prevent duplicate writes.
- A failed write is represented by an inline polite live-region message with an explicit retry action. The persisted preference remains the displayed selection until storage confirms a change.
- Long Persian text, RTL, 200% font scale, and narrow widths use the existing scrollable column and `CarePackExperience` spacing instead of fixed text heights.

## Accessibility baseline

Interactive controls use Material buttons plus project minimum-height modifiers. State changes that require recovery use text as well as color and expose a polite live region. Automated tests protect semantic reachability; TalkBack, Switch Access, 200% font, RTL, 320dp, landscape, and theme checks remain manual release gates.
