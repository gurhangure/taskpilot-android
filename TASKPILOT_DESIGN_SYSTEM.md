# TaskPilot — Design System v1.0

**Status:** Baseline for Android (Kotlin/Jetpack Compose), iOS (SwiftUI), and Web (React/TypeScript).

## 1. Product principles

- **Dark-only.** Do not implement a light theme or a theme switch in v1.
- Small, useful personal task manager; optimize for clarity and quick task entry, not dashboards or elaborate effects.
- Use a shared visual language and feature set across platforms while respecting native navigation, keyboard, touch, and accessibility conventions.
- All identifiers, source-code comments, UI labels, accessibility labels, and documentation are in **English**.
- No login, reminders, calendars, collaboration, or complex task categorization in v1.

## 2. Design tokens

| Token | Value | Purpose |
|---|---|---|
| `color.background` | `#0B1220` | Main background |
| `color.surface` | `#172033` | Cards, forms, panels |
| `color.surfaceRaised` | `#202C42` | Inputs and elevated surfaces |
| `color.primary` | `#818CF8` | Primary actions, selection and focus |
| `color.onPrimary` | `#0B1220` | Text on filled primary controls |
| `color.textPrimary` | `#F8FAFC` | Primary text |
| `color.textSecondary` | `#CBD5E1` | Supporting text |
| `color.border` | `#36445C` | Outlines and separators |
| `color.success` | `#4ADE80` | Completed state |
| `color.error` | `#FCA5A5` | Errors and destructive warnings |

- **Spacing:** 4 px base grid; prefer 8, 12, 16, 24, and 32 px.
- **Corner radius:** 12 px on inputs/cards; 16 px on panels; pill shape for filter chips.
- **Typography:** system-native fonts (Android Roboto, iOS San Francisco, web system stack). Use a clear scale: title 24–28 sp/px, section 18–20, body 16, secondary 14. Respect system text scaling.
- **Icons:** consistent semantic meaning; use native icon libraries rather than forcing identical glyphs.
- **Motion:** subtle, optional state transitions; respect reduced-motion settings.
- Keep text and UI contrast accessible (aim for WCAG AA: 4.5:1 normal text, 3:1 large text and essential UI elements); verify actual rendered colors.

## 3. Screens and components

### Task List (home)

- Header: **My Tasks** and a Settings affordance.
- Compact summary: **Total**, **Active**, **Done** (derived from loaded tasks).
- Search tasks by title (case-insensitive, client-side in v1).
- Filter chips: **All**, **Active**, **Done**.
- Task cards show a completion toggle, title, optional description preview, and edit affordance.
- Clear **New Task** primary action.
- Appropriate empty, loading, retry/error, and no-search-results states.

### Create / Edit Task

- **Title** required, 1–200 UTF-16 code units after enforcing the API's existing validation semantics; whitespace-only invalid. Keep client feedback consistent with backend behavior.
- **Description** optional.
- In edit mode, show completion status, Save and Delete; confirm destructive deletion.
- Prevent accidental duplicate submits, show saving/progress and actionable API errors.

### Settings / Connection

- Configurable API base URL stored locally on the client; never hardcode an individual developer's LAN IP.
- Show connection status and a **Test Connection** action (use an existing documented endpoint; do not assume a health endpoint exists).
- Explain that the backend runs on the user's computer and that physical devices need LAN connectivity.
- Warn before using plain HTTP; do not silently relax platform network security or connect to arbitrary internet hosts.

## 4. Shared behavior

- Same TaskPilot API contract and main CRUD workflows across Android, iOS, and Web.
- Completion toggles and CRUD results must reflect server-confirmed state; show errors and allow retry.
- No real-time sync required in v1. Refresh data on screen revisit or manual refresh; changes made on another client become visible after refresh.
- Client-side search, filters, and counts are based on the current retrieved task list.
- Use native affordances: Material 3 components on Android, SwiftUI controls on iOS, responsive accessible HTML controls on Web.
- Responsive layouts: compact single-column mobile view; web can widen the content area but should retain task-card hierarchy.
- Include loading, empty, error, validation, and success feedback for critical flows.

## 5. Scope and delivery

- **In v1:** task CRUD; complete/reopen; title search; status filters; three small summary counts; API connection settings; dark UI; basic accessibility.
- **Not in v1:** light mode, accounts/authentication UI, due dates, push notifications, categories, charts, advanced analytics, background sync, offline writes.
- Distribute this file with each client repository as a **tracked, public design specification**. Keep `AGENTS.md` and `prompts/` local-only and Git-ignored.
- For project-specific Codex instructions, add: `Follow TASKPILOT_DESIGN_SYSTEM.md; dark-only; preserve cross-platform consistency without sacrificing native behavior.`

## 6. Change policy

Only change shared design tokens, feature scope, or shared behavior deliberately. If updated, propagate the change to all three client repositories and document any intentional platform-specific differences.
