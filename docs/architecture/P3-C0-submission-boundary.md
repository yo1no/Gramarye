# P3-C0 submission preparation and commit boundary

This document records the P3-C0 phase-boundary decision. Architecture authority remains the approved [P3 scoped amendment §9-A](../codex-spec/17_P3資料模型修正案.md#9-a-p3-c-submission-preparation-與-p3-d-commit-邊界), the [frozen skeleton](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md), the [implementation contract](../codex-spec/Codex_實作總規格Prompt.md), and the [detailed phases](../codex-spec/NeoForge1.21.1_詳細實作步驟.md). This is a boundary index, not a second complete submission specification.

## Phase ownership

- P3-C formalizes a Draft, consumes a current authoritative identity/state snapshot, performs optimistic-concurrency and exhaustion prechecks, proposes a revision, runs the existing B2/B3 pipeline, and emits an immutable `SkillSubmissionPlan`. It does not mutate Store state or allocate a formal revision.
- P3-D owns the Store domain API, atomic compare-and-insert, formal revision allocation, commit conflict, and the plan commit boundary. Its allocator latest truth is the greatest revision present in Store, not the player's Attachment pointer.
- After P3-D exists, `SkillDefinitionSubmissionService` is the composition facade: acquire/reconfirm authority and state, prepare through P3-C, then commit through P3-D. P3-C and P3-D domain APIs do not depend on Minecraft player classes.
- The plan carries owner/principal, the compare-and-insert precondition, the proposed document, and the validated definition. Prepared means admissible for an attempted commit, not committed.

## Ordering and bounded report state

Submission short-circuits in this order: Draft read warnings → Draft schema → authorization → concurrency/exhaustion → completeness → B2/B3. Identity rejection reveals no existence, latest-revision, or owner information.

For each source `ValidationResult`, merge retained issues in source order and then call `ValidationCollector.inheritReportState` so `truncated` and `omittedError` are preserved. `Prepared`, `Conflict`, `IdentityRejected`, and `RevisionExhausted` carry warning-only reports.

## Persistence handoff

A transient SkillId mint grant is not a restart-stable credential; authorization always uses the current authoritative snapshot. P3-D rechecks the Store precondition at commit. Store SavedData and the player Attachment do not form one native transaction. The approved [P4 amendment](../codex-spec/18_P4持久化與組合修正案.md) now fixes Store-first publication, a bounded generation journal, persisted-readback-confirmed clearing, and fail-closed reconciliation; P3-C0 implements none of them.
