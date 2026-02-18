# AI Execution Protocol

This repository follows a strict separation of responsibilities:

- ./ARCHITECTURE.md defines conceptual system design.
- ./PRD.md defines high-level goals and constraints.
- Each story folder contains:
  - requirements.md (acceptance criteria)
  - plan.md (ordered implementation phases)
  - tasks.md (atomic execution checklist)

---

## Authority Hierarchy

When implementing a story, AI must follow this precedence:

1. ARCHITECTURE.md — authoritative for system design.
2. Story requirements.md — authoritative for acceptance criteria.
3. Story plan.md — authoritative for phase sequencing.
4. Story tasks.md — authoritative for execution tracking.

If conflicts are detected, stop and request clarification.

---

## Phase Execution Rules

AI must:

1. Implement only the explicitly requested phase.
2. Follow the story's `plan.md` exactly for that phase.
3. Use `requirements.md` as the acceptance contract.
4. Not implement future phases.
5. Not invent additional features.
6. Not modify ARCHITECTURE.md unless explicitly instructed.
7. Respect unique constraints and idempotency rules.
8. Not introduce unrelated refactors.
9. Stop immediately after completing the requested phase.

---

## Scope Control

When invoked, AI will be given explicit file paths.

AI may modify only:

- Files within the referenced story folder
- Files required to implement that story (e.g., new service classes, repositories, Liquibase changesets)

AI must not:

- Modify other story folders
- Modify unrelated modules
- Perform cross-story refactors
- Restructure the project

Unless explicitly instructed.

---

## Task Execution Rules

- `plan.md` defines phases.
- `tasks.md` defines atomic checklist items.

When implementing a phase:

1. Execute only the tasks belonging to that phase.
2. Mark a task complete (`- [x]`) only after:
   - Code compiles.
   - Changes align with plan.md.
3. Do not add, remove, or reorder tasks.
4. Do not mark tasks complete prematurely.
5. After completing the phase:
   - Update tasks.md accordingly.
   - Provide a concise summary:
     - Files created/modified
     - Tasks marked complete

---

## Completion Definition

A phase is complete only when:

- All tasks in that phase are marked complete.
- The project builds successfully.
- No TODO placeholders remain for that phase.

---

## Clarification Rule

If scope is ambiguous, incomplete, or conflicts exist between files, stop and request clarification before proceeding.
