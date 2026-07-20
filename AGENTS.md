# AGENTS.md — Gramarye

## Project identity

- `MOD_ID=gramarye`
- `DATA_NAMESPACE=gramarye`
- `MINECRAFT_VERSION=1.21.1`
- `NEOFORGE_VERSION=21.1.x`
- `MIN_NEOFORGE_VERSION=21.1.229`
- `JAVA_VERSION=21`
- `JAVA_PACKAGE_ROOT=com.yo1no.gramarye`

`MOD_ID` and `DATA_NAMESPACE` are frozen. Do not rename them.

Before writing Java source, replace `JAVA_PACKAGE_ROOT` with the final package root.
Recommended shape when a stable domain is unavailable:

```text
com.yo1no.gramarye
```

Do not use `java.example.gramarye`, `com.example.gramarye`, or another placeholder in committed production source.

## Authoritative specifications

Read these files before planning or editing architecture:

1. `docs/codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md`
2. `docs/codex-spec/Codex_實作總規格Prompt.md`
3. `docs/codex-spec/NeoForge1.21.1_詳細實作步驟.md`

Priority:

```text
Frozen architecture specification
> Codex implementation contract
> Detailed implementation steps
> Existing code
> Task-specific prompt
```

If specifications conflict, stop the affected work and report the exact conflict. Do not redesign the frozen architecture.

## Required workflow

For each phase:

1. Inspect the repository and relevant files.
2. State the current phase and its Definition of Done.
3. Produce a focused change plan.
4. Implement only the requested phase.
5. Run the applicable build and tests.
6. Review the diff for architecture violations.
7. Report completed work, tests, remaining work, and risks.

Do not implement P0–P19 in one task.

## Phase name mapping

- Frozen Stage 0 = engineering phases P0–P8 collectively.
- Engineering P9 = frozen architecture stage 1A.
- When the user specifies a P number, use the engineering P number in `docs/codex-spec/NeoForge1.21.1_詳細實作步驟.md` as the execution scope.

## Core invariants

- Server authoritative.
- Client sends intent only.
- One persistent source of truth per data category.
- Skill revisions are immutable.
- Actions never mutate world state directly.
- Gameplay effects use Request → Resolve → CommitPlan → ordered Commit.
- Mana changes only through ManaTransactionService.
- Internal events are queued; no unbounded synchronous event recursion.
- Gameplay and presentation are isolated.
- Unknown definition payloads must round-trip without data loss.
- Persisted state and network payloads must be bounded and versioned.
- Dedicated server code must not load client-only classes.

## Build commands

Discover actual Gradle task names first, then use the repository equivalents of:

```bash
./gradlew --version
./gradlew compileJava
./gradlew test
./gradlew runGameTestServer
./gradlew runServer
```

Do not claim a command passed unless it was executed successfully.

## Git discipline

- Keep changes phase-scoped.
- Do not rewrite unrelated files.
- Do not format the entire repository.
- Do not delete unknown or user-authored work.
- Show the diff summary before declaring completion.
- Prefer one coherent commit per completed work unit when the user requests commits.

## Current starting task

Unless the user's current prompt explicitly selects a later approved phase, begin with:

```text
Repository inspection
→ Gap Analysis
→ P0 plan
```

Do not write the entire framework during the initial inspection task.
