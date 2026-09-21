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

1. `docs/codex-spec/18_P4持久化與組合修正案.md`
2. `docs/codex-spec/17_P3資料模型修正案.md`
3. `docs/codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md`
4. `docs/codex-spec/Codex_實作總規格Prompt.md`
5. `docs/codex-spec/NeoForge1.21.1_詳細實作步驟.md`

Priority:

```text
Approved scoped amendment within its stated scope
> Frozen architecture specification
> Codex implementation contract
> Detailed implementation steps
> Existing code
> Task-specific prompt
```

`18_P4持久化與組合修正案.md` supersedes older rules only within its explicitly stated P4
persistence, Attachment, and composition scope. `17_P3資料模型修正案.md` retains the same
scoped precedence for the P3 clauses that it explicitly identifies. All P0-P3 and other rules not
amended by those documents remain governed by the frozen architecture specification.

If the P4 amendment and another authoritative document have not been synchronized and still
contain a substantive conflict, stop the affected work and report the exact conflict; do not choose
one side or redesign the frozen architecture. Apply the same stop rule to other specification
conflicts.

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

## 驗證與交付原則（使用者批准，持續適用）

本節規範工程執行程序，取代舊工作單中工具故障必須另開恢復 campaign、全歷史 exact-once、只能一次 commit/push 與唯讀重播禁令；不改產品設計、schema、安全邊界或 required 接受條件。除使用者明確更新外，後續階段沿用。

- 以玩家功能與資料安全為目的；不為修驗證器再建立通用驗證層、程序資格階段或多層 successor。
- 產品失敗阻止交付及受影響的後續工作；可在已批准的產品 scope 內修正並重驗。工具故障則保存原資料，在同一工作項修工具、測工具、補驗，不將其他有效產品結果歸零。
- 原 raw、result、失敗與執行時間保持。新判定明列 source/tool 版本及來源；不得偽造、挑選有利片段、默認缺資料為 PASS，或把新結果寫回舊結果。
- 允許真實封存資料的唯讀重播與有原因的重跑。每次重跑須有修正、已定位原因或具體診斷問題；相同条件下盲目重跑至綠燈不被接受。
- 只重驗受影響範圍。沿用證據比重跑更複雜時，可直接重跑必要測試；不為省一次測試建立多層證據接續。版本改變只失效真正受影響的接受。
- 分開 immutable input、合法生成物、動態狀態與 console 顯示。比較符合角色的身份／合法轉移，不把不同時點整份 dict 相等當成通用正確性條件。
- 失去 ownership、來源或安全終態證據時，不猜 PASS、不提權、不誤殺其他process。只阻擋依賴該缺口的接受；不新增無關程序責任。
- 工具修正只需其直接正負測試與受影響 consumers。既有測試可以驗證工具；禁止為了驗證這些測試再開新的資格流程。
- 可以用合理的小 commits 保存進度；正式交付對應明確 candidate SHA 及完整 required checks，不以 commit/push/attempt 次數衡量品質。不 force、不改 branch protection、不抹除失敗歷史。
- 用一份接受清單記錄已通過、待完成、阻擋及證據。不為控制案例數、文件數、演練次數或摘要格式另建產品 Gate。
