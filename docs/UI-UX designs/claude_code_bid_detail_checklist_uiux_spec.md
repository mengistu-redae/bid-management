# Claude Code Specification: Bid Detail & Checklist Page UI/UX Redesign

## Mission

Redesign the existing bid detail page into a modern, efficient **Bid Workspace** for an enterprise IT solutions sales/bid management application. Use the attached PDF and its page screenshots as the source reference. Implement changes directly in the existing repository.

The uploaded reference page is for **“Request for Information (RFI) For the Enterprise GPU Computing Solution”** for **Commercial Bank of Ethiopia**. The current page contains:
- Back-to-bids navigation and a Deal 360 action.
- Bid title/customer, current status and classification indicators.
- Closing, opening, clarification deadline, bid validity, and scouted date.
- Expandable date editing.
- Document upload.
- Bid status update with reason for Dropped/Cancelled.
- Go/No-Go decision and reason.
- Lots, each with division/status/readiness indicators, warning messages, estimated value, bid bond, OEM/brand, scope, officers, and checklist.
- Checklist items such as tender document purchased, clarification questions sent, MAF from OEM, vendor/distributor quotation, price protection, lead time, implementation partner quote, training quote, scope of work/HLD/LLD, technical compliance, bid bond arranged, financial offer approved, and final submission.
- Add-lot flow and independent lot status changes.
- Linked deal registrations.
- Activity log / notes.
- Status history with from/to/reason/by/when.

Preserve these workflows and business rules. Do not remove features to simplify the layout.

## First: Inspect the actual codebase

1. Inspect repository, framework, routes, existing page/components, styling, API/data models, authentication, permissions, and tests.
2. Identify the existing status values and all lot/checklist persistence behavior.
3. Run the current page if possible and compare it with the attached reference.
4. Write a short plan, then proceed with implementation unless a real blocker exists.
5. Do not invent API endpoints, data fields, business rules, or live data. Reuse the existing stack and contracts.

## Design direction

Build a professional enterprise SaaS experience with a calm light surface, dark navy text, restrained blue accent, subtle borders, readable typography, consistent spacing, and accessible status colors.

Suggested starting tokens (adapt to existing brand):
- Navy: `#10243A`
- Blue: `#2477D4`
- Background: `#F5F7FA`
- Surface: `#FFFFFF`
- Main text: `#172B3A`
- Secondary text: `#667085`
- Border: `#E4E7EC`
- Success: `#16845B`
- Warning: `#D97706`
- Critical: `#C24141`

Use the existing icon library where available. Avoid excessive shadows, oversized empty cards, too many pill badges, and long unstructured vertical forms.

## 1. Page structure: Bid Workspace

Reorganize the long page into a clearer hierarchy:

### A. Compact breadcrumb and title header
- Breadcrumb/back link to the bids list.
- Clear bid title and customer name.
- Status badge and any classification/readiness badge with consistent semantics.
- Primary actions aligned on the right: use existing actions only (for example edit, change status, or Deal 360).
- Long titles should wrap gracefully.
- Keep the most important actions visible without making the header too tall.

### B. Bid health summary
Immediately below the title, provide a compact summary row using only actual available data:
- Overall bid status.
- Time until submission deadline or “Closed” state.
- Overall checklist/readiness completion, if calculable from persisted checklist items.
- Number of outstanding blockers or incomplete required checklist items, if available.
- Number of lots.
- Bid value only if it exists and its aggregation semantics are correct.

Do not show invented values or misleading zeros during loading. If a summary cannot be calculated reliably, omit it.

### C. Deadline and key dates
Replace the oversized date card with a compact, readable key-dates panel:
- Closing deadline is the most prominent date.
- Opening date, clarification deadline, bid validity, and scouted date are secondary.
- Use consistent date formatting and show missing dates as “Not set” rather than a dash where appropriate.
- Preserve the current “Change dates” behavior in an inline edit panel, popover, or accessible dialog.
- Validate date fields and preserve current timezone/date semantics.
- Clearly distinguish past deadlines from upcoming deadlines; do not rely on color alone.

### D. Use tabs or anchored section navigation
Where suitable for the current app, add a sticky section navigation or tabs:
- Overview
- Documents
- Lots & Checklist
- Deal Registrations
- Activity & History

Use real working anchors/routes and preserve direct linking behavior if it exists. On small screens, allow the navigation to scroll horizontally or use a compact selector. Do not build fake tabs that hide data without working navigation.

## 2. Documents

Make document handling a purposeful upload area:
- Show an upload dropzone or clear file picker with accepted file types and size limits only if known from the existing implementation.
- Keep a visible Upload action and show upload progress/errors if supported.
- Once uploaded, display documents in a list/table with filename, type/size if available, uploader/date if available, and actions supported by the application.
- Include a useful empty state when no documents exist.
- Prevent duplicate submissions during upload and surface errors accessibly.
- Do not invent document metadata or supported file restrictions.

## 3. Bid status and Go/No-Go decision

These are important decisions and should not be visually buried among unrelated fields.

### Status change
- Show current status and an explicit action to change it.
- Use a select or structured transition control with the actual allowed statuses.
- Keep the reason required for Dropped/Cancelled exactly as required by the current business rules.
- Show validation beside the field.
- Confirm high-impact transitions if appropriate to existing behavior.
- Make the action's saving state and result clear.

### Go/No-Go
- Present the decision as a clear decision panel with the existing options.
- Keep the reason field and validation.
- If decision has not been made, display “Decision pending” clearly.
- Do not imply that Go/No-Go is the same as the bid lifecycle status unless the current domain model says so.
- Preserve audit/history behavior.

## 4. Lots & checklist — primary work area

This is the most important part of the page. Improve the current long list without losing any checklist items or lot-level independence.

### Lot summary card
Each lot should begin with a compact summary containing:
- Lot name/number.
- Division, with an explicit “Not assigned” state when absent.
- Lot status.
- Readiness percentage calculated from actual checklist state.
- Estimated value, bid bond, OEM/brand, scope, and officers when those values exist.
- Warning/blocker messages in a compact alert region with clear next steps where possible.
- An expand/collapse control with accessible label and visible state.

Do not use a warning banner as a substitute for a fix workflow. Preserve any existing action that lets users set the missing division or resolve a warning.

### Checklist interaction
- Group checklist items into sensible categories only if this can be done without changing the stored data or business meaning. Suggested visual groupings: Tender & Clarifications; OEM & Commercial; Technical & Delivery; Financial & Submission.
- Preserve every existing checklist item and custom-item behavior.
- Show completion count and progress (for example, completed/total) as well as percentage.
- Use accessible checkboxes with clear labels and large enough click targets.
- Preserve due dates and item metadata if present.
- Make it clear whether checking an item saves immediately or requires a save action.
- Prevent double updates and show saving/saved/error feedback.
- Distinguish required, optional, blocked, and not-applicable states only if supported by the current data model.
- Keep the “Add checklist item” action easy to find, with validation and optional due date where the current app supports it.
- Keep “Change this lot’s status independently” accessible but visually secondary to the checklist.
- Ensure keyboard users can operate expand/collapse, checklist, and add-item controls.

### Multiple lots
- Make it easy to scan all lots and compare readiness.
- If there are many lots, consider collapsible cards with one open by default only if that behavior fits the current use.
- Preserve add-lot functionality and any lot-specific status transitions.
- Avoid showing the full checklist for every lot expanded simultaneously by default if that creates a very long page.

## 5. Deal registrations

- Keep linked registrations visible with OEM/vendor, registration reference, status, and expiry/submission date only when present in actual data.
- Provide a clear empty state and preserve the “New deal registration” flow.
- Use a compact table or cards depending on existing layout and data.
- Make the relationship to this bid explicit.

## 6. Activity log and status history

These are audit-sensitive sections. Preserve existing data and order.

### Activity log
- Present notes and events in a chronological timeline or compact feed.
- Keep the “Add a note” workflow with clear validation, disabled/saving state, and success/error feedback.
- Show author and timestamp only if supplied by the system.
- Empty state should explain that no activity has been recorded yet.

### Status history
- Use a compact table on desktop with From, To, Reason, By, When.
- On mobile, convert to a readable stacked record/list.
- Keep empty state and date formatting consistent.
- Never fabricate audit events, actors, or timestamps.

## 7. Interaction, state, and safety

- All buttons must work; no decorative/inert controls.
- Preserve API calls, data schema, authentication, authorization, and business logic.
- Keep current status labels and transitions unless the existing code explicitly defines a change.
- Avoid hard-coded sample metrics or tender information.
- Maintain audit trail semantics.
- Preserve form validation and required reasons.
- Add loading, empty, error, and success states.
- Prevent duplicate submissions and repeated mutations while requests are in progress.
- Do not change destructive-action behavior without reviewing existing requirements.
- Use semantic HTML, accessible labels, visible focus, keyboard support, and sufficient contrast.
- Do not rely on color alone to communicate status.

## 8. Responsive behavior

Desktop:
- Use a clear title/summary area and balanced two-column layouts where useful.
- Keep dates and decision controls compact.
- Let lot checklists use the available width without long, awkward lines.

Tablet:
- Reflow summary cards and forms.
- Avoid compressed form fields and overflowing action buttons.

Mobile:
- Stack header actions and summary metrics.
- Use full-width form controls.
- Keep checklists easy to tap.
- Convert wide history tables to stacked records or contained horizontal scrolling.
- Prevent page-level horizontal overflow.

## 9. Suggested component boundaries

Adapt these to the project’s current conventions:
- `BidHeader`
- `BidHealthSummary`
- `BidKeyDates`
- `BidDocuments`
- `BidStatusEditor`
- `GoNoGoDecision`
- `LotsSection`
- `LotCard`
- `LotChecklist`
- `ChecklistItem`
- `DealRegistrations`
- `ActivityTimeline`
- `StatusHistory`
- Shared status badges, progress indicators, empty states, loading skeletons, and confirmation dialogs.

Do not create components solely to match these names if the project already has suitable equivalents.

## 10. Acceptance criteria

- The page is materially easier to scan and use than the current long vertical form.
- Bid title, customer, status, closing deadline, and readiness are easy to find.
- Date editing, document uploads, status changes, Go/No-Go, lot operations, checklist updates, deal registration, notes, and history remain functional.
- Every existing checklist item and custom checklist flow is preserved.
- Readiness is computed from actual data and explained.
- Missing division/data warnings remain visible and actionable.
- Responsive layouts work on desktop, tablet, and mobile.
- Loading, empty, validation, and error states are implemented.
- Accessibility basics are addressed.
- No mock values are shown as live data.
- Existing routes and backend contracts are preserved.
- Available lint, type-check, tests, and build checks pass, or remaining failures are reported.

## 11. Implementation workflow and final report

1. Audit the codebase and current page.
2. Summarize the plan briefly.
3. Implement the redesign in the existing stack.
4. Run available checks and fix regressions.
5. Report changed files, interactions implemented, checks run, known limitations, and suggested next steps.

**Use the attached PDF and screenshots as a visual/functional reference. Adapt the implementation to the real repository; do not create a static mockup instead of a working page.**
