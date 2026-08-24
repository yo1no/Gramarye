# NeoForge 1.21.1 魔法 Node 系統：詳細實作步驟

本文件把凍結骨架拆成可交給 Codex 逐階段執行的工程工作包。

P3 相關工作包已依 `17_P3資料模型修正案.md` 同步；P4-A1～A3與P4-B～P4-E 已依
`18_P4持久化與組合修正案.md` 同步。兩份修正案只在各自明確範圍內優先，其他架構仍以
凍結規格為準。若P4條文尚未同步而形成實質衝突，停止受影響工作，不自行選邊。

## 階段名稱映射

- 凍結骨架的 Stage 0 是由工程階段 P0～P8 共同完成的概念性完整地基階段。
- 工程 P9 對應凍結骨架的 Stage 1A 垂直切片。
- 使用者指定 P 編號時，以本文件的工程 P 編號作為實際執行範圍。

---

# 0. 使用原則

每個階段遵循：

```text
檢查現況
→ 寫設計決策紀錄
→ 建立最小介面與資料
→ 實作
→ 單元測試
→ GameTest／專用伺服器驗證
→ 更新 Gate
```

只有前一階段完成 Definition of Done 才進下一階段。

---

# 階段 P0：Repository 與版本基線

## 目標

確定專案能在固定平台穩定建置。

## 工作

1. 確認 Minecraft 1.21.1。
2. 確認 NeoForge 固定完整 build，且不低於 21.1.229。
3. 確認 Java 21 toolchain。
4. 確認 Gradle Wrapper 已提交。
5. 確認 mod id、namespace、package root。
6. 確認 `neoforge.mods.toml` 版本範圍。
7. 建立 CI 基線。
8. 建立 `docs/architecture/`，放入凍結骨架與 ADR。

## 建議 ADR

```text
ADR-0001-platform-lock.md
ADR-0002-single-source-of-truth.md
ADR-0003-server-authoritative-network.md
ADR-0004-effect-pipeline.md
ADR-0005-skill-revision-store.md
```

## 驗收

- `compileJava` 通過。
- `test` 通過。
- 可啟動 client。
- 可啟動 dedicated server。
- dedicated server log 無 client-only class error。
- 版本皆為固定值。

---

# 階段 P1：共用基礎資料

## 目標

建立不依賴 Minecraft 世界修改的純 Java 資料模型。

## 工作 1：Typed IDs

建立：

```text
SkillId
SkillRevision
SkillInstanceId
MarkerInstanceId
ConstructInstanceId
ScheduleId
EventId
```

`SkillRevision` 固定為 `record SkillRevision(int value)`，合法範圍 `0..Integer.MAX_VALUE`，canonical JSON 為普通整數。P3-D1 additive建立唯一`successor()`給P3-C3／P3-D2共用：非MAX回下一值，MAX回empty。P3-C preparation保留`RevisionExhausted`；合法Plan的normal P3-D commit result沒有此variant。不得overflow、wrap或重用revision。

### 測試

- null 拒絕。
- 負 revision 拒絕。
- Codec round-trip。
- equals/hashCode。

## 工作 2：有界資料工具

建立集中 bounds：

```text
MAX_NODES
MAX_STRING_LENGTH
MAX_RAW_PAYLOAD_BYTES
MAX_RUNTIME_TAGS
MAX_VISITED_TARGETS
MAX_APPEARANCE_INTENSITY
```

分成：

- 不可提高的程式安全上限。
- 可由 server config 在硬上限內調整的政策值。

## 工作 3：Validation

建立：

```text
ValidationIssue
ValidationSeverity
ValidationResult
ValidationContext
```

不要用 exception 表示一般使用者定義錯誤。

## 驗收

- 全部為純 Java 可測。
- 無 NeoForge client dependency。
- 沒有萬能 Utils。

---

# 階段 P2：Registry 與 Definition Envelope

## 目標

讓 Trigger／Action 型別可註冊，未知型別仍能保全。

## 工作 1：Custom registries

建立：

```text
TriggerType Registry
ActionType Registry
```

使用 NeoForge 1.21.1 對應 custom registry 註冊方式。

## 工作 2：Descriptor

建立：

```text
TriggerCapabilities
ActionCapabilities
ControlClass
SourceRequirement
TargetRequirement
OutputKind
```

## 工作 3：Envelope

解碼流程：

```text
讀 envelope
→ 檢查 raw payload 上限
→ 查 type registry
→ 已知：使用該型別 Codec 解碼
→ 未知／失敗：建立 transient UnknownDefinition classification
```

P2 的 Resolved／Unknown definition 是 registry resolution 邊界的 transient 分類。P3-A `SkillDocument` 只持久化原始 `DefinitionEnvelope`，不持久化這些 union。

## 工作 4：Round-trip

未知資料重新編碼時：

- typeId 不變。
- rawPayload 不變。
- schemaVersion 不變。
- error 不需要寫回原始資料主體，可放 metadata。

## 測試

- 已知 Trigger round-trip。
- 已知 Action round-trip。
- 未知 Trigger round-trip。
- 已註冊但 payload 損壞。
- 超大 payload 拒絕。
- 未知欄位保留政策。

## Definition of Done

一份包含未知 Action 的技能可以：

- 載入。
- 顯示損壞待修。
- 匯出。
- 再載入。
- 原始 Action payload 不變。

---

# 階段 P3：不可變技能文件、驗證、提交與 Store domain

```text
P3-A：SkillRevision int、SkillDraft／SkillDocument／NodeDocument、
      Appearance storage schema 與 Codec
P3-B：migration、Envelope resolution、validation、ValidatedSkillDefinition
P3-C：Draft formalization、server-side SkillId mint contract、authoritative
      submission precheck、optimistic concurrency precheck、proposed revision、
      既有 resolution／validation／projection 與 immutable SkillSubmissionPlan
P3-D1：production pure-Java Store aggregate、owner/history truth、read API、
       sparse retained revisions、hard ceilings、successor與detached snapshot
P3-D2：quota／owner／CAS／capacity／insert同一atomic mutation、zero-partial
       typed failure、commit result與正式revision allocation
P3-D3：active pin handles、complete retention roots、latest implicit root與reclaim
P4-A1：owner Codec、family/context判定、bounded raw Codec、per-raw-subtree envelope、
       mixed-family current document bridge、appearance mapping與shared logical bounds
P4-A2：store_schema_version、Store／History／Revision physical schema與三層byte ceilings、
       Store migration、logical typed-location opaque-token conformance migration、
       migration-before-hydration、
       snapshot／restore、bounded facts與current Store blob encode／load
P4-A3：pure immutable hierarchical carrier rebuild／replacement／reclaim filtering、checked totals
       與64 MiB fixed-heap validation
P4-B1：saved_data_schema_version、whole-root／inner exact framing、zero-length no-journal sentinel、
       bounded opaque pending blob、outer migration、bounded decompressed carrier、A2 load＋A3 rebuild、
       Ready candidate；無world／filesystem／cache lifecycle
P4-B2：primary file ingress、strict single-member gzip、custom one-time load、
       Ready／Quarantined／Unavailable SavedData adapter、Overworld cache install、live Store／carrier
       ownership、save callback、controlled read／pin／reclaim、dirty與fixed-heap load／save Gate
P4-C0：只修訂player Attachment totality、bounded raw與destructive oversize quarantine權威政策
P4-C1：physical V0、total serializer、bounded counting、Ready／PreservedRaw／OversizeMarker、
       Draft persistence／三軸migration、exact bounds與prebuilt Ready carrier；無registration／lifecycle service
P4-C2：Attachment registration、immutable `setData` service、唯一int generation transition、death／End、
       P4-D transition seam、P4-E bounded per-player root projection、GameTests／fixed-heap／phase gates
P4-D0：documentation-only journal framing／availability、policy ownership、composition與combined
       memory authority
P4-D1：strict journal／migration、single Store authority snapshot、窄Store submission port、
       Store／journal preflight、opaque commit handle、publication與journal roots
P4-D2：unique policy／SkillId providers、Draft creation、authenticated P3-C composition、
       prepared Attachment transition與composition outcome
P4-D3：bootstrap／login recovery、persisted-readback clear、paired restart與combined fixed-heap Gate
P4-E0-B：documentation-only V0 root-audit authority；無implementation／study rerun
P4-E0-B.1：documentation-only integrated-owner runtime snapshot counting／freshness authority；
         修正stale review ledger，無implementation／study rerun
P4-E0-B.2：documentation-only effective HotSpot MaxHeapSize observation／三狀態／precedence／
         process-control authority；無floor／numeric／R2Q evidence／implementation change
P4-E0-B.3：documentation-only online source counter applicability／online > integrated > disk／
         UUID ordering／final freshness／E3 obligation；無numeric／R2Q evidence／implementation change
P4-E0-B.4：documentation-only memory-only root-index generation／exhaustion、E2 invalidation、
         Complete permit／active handoff／removeServer authority；無counter／heap／evidence／implementation change
P4-E0-B.5：documentation-only E2 production audit-service construction trigger、Store-service
         exact-one-final ownership、sole login recovery→E2 synchronous ordering、exact-identity injection與
         E3 same-instance reuse；不裁決outcome admissibility或atomic reconciliation final design
P4-E0-B.6：documentation-only direct qualification observation coordinates、test-armed bounded state、
         closed nominal public transport及conditional exact-instance FML route authority；不宣稱平台API
         存在，不改production semantics／25 counters／heap／R2Q／E3
P4-E0-B.7：historical documentation-only COMMIT_READY／receipt authority；舊release requirement
         與next-step已由B.8 scoped supersession
P4-E0-B.8：active release-qualification simplification；Candidate10–12 receipt track停止，
         B.7／A0.4只保留historical research；release使用one-level verification、三次ordinary
         cold full-suite及既有direct product／fixed-heap／restart Gates
P4-E1：read-only bounded online／integrated／disk scanner；online existing-state observation only，
      disk／integrated full P4-C；journal／Store audit、
      memory-only index與bounded completeness results；mutation／reclaim 0
P4-E2：首次建立production audit service並由Store service長期持有；P4-D recovery後由
      sole login handler同步呼叫active immutable reconciliation；offline／Store／journal／reclaim mutation 0
P4-E3：重用same Store-owned audit service的unique ServerStarting fresh audit→controlled reclaim once、
      exact-server stop removal、restart／fixed-1,536-MiB／CI gates；audit constructor／login wiring delta 0
```

## P3-A：SkillDraft／SkillDocument／NodeDocument 與 Appearance storage

### 目標

建立首次正式持久化 schema，不做 registry resolution、validation、submission 或 Store。

### 資料與 Codec

```text
SkillRevision(int)
SkillDraft + DraftNode + Missing／Present Trigger／Action slots
SkillDocument
NodeDocument
SkillReference
AppearanceDocument
AppearanceOverrideDocument
```

- `SkillDocument` 只保存 `DefinitionEnvelope`；`NodeDocument` 不保存 index。
- nodes List position 是該 revision 內唯一的零起算 `nodeIndex`；不建立 `NodeId`。
- `SkillDraft` 與 `SkillDocument` 不混用。Draft 使用同一 `AppearanceDocument`，不建立 `DraftAppearance`。
- Draft 的 SkillId 是候選身分；P3-A 不建立 production random factory。
- Appearance 建立 partial override、Profile tagged 三態、hard numeric clamp、whole-blob Unparsed、over-hard Rejected 與 raw snapshot alias isolation。
- 強制 appearance relative depth／node proxy 與 SkillDocument global parsed-tree hard-depth proxy。

### P3-A limits

```text
MAX_UNPARSED_APPEARANCE_DEPTH = 32
MAX_UNPARSED_APPEARANCE_NODES = 1024
DEFAULT_UNPARSED_APPEARANCE_DEPTH = 16
DEFAULT_UNPARSED_APPEARANCE_NODES = 256
MAX_SKILL_DOCUMENT_BYTES = 1 MiB
DEFAULT_SKILL_DOCUMENT_BYTES = 256 KiB
MAX_SKILL_DOCUMENT_DEPTH = 64
DEFAULT_SKILL_DOCUMENT_DEPTH = 32
```

Appearance relative depth 與 document global depth 都從各自 root depth 1 起算。Byte limit 只由未來真正持有 raw bytes 的 I/O 邊界執行。

## P3-B：Migration、resolution 與 validation

```text
SkillDocument DefinitionEnvelope
→ skill/payload migration contracts
→ registry resolution
→ transient resolved/unknown candidate
→ structural/descriptor/cross-node validation
→ ValidatedSkillDefinition
```

- `ValidatedSkillDefinition` 是可重建 runtime projection，不是第二持久化真相。
- P3-B 產生 clamp warning、appearance quarantine policy warning、profile lookup／missing-profile warning 與 gameplay／presentation validation report。
- Node reference 只能指向較小 index；Unknown classification 阻止 runtime projection。

## P3-C：Submission preparation 與 identity precheck

P3-C 建立純資料 preparation boundary；不建立 Store implementation、不寫 Store，也不配置正式 revision：

```text
SkillDraft／SkillDraftReadResult
→ Draft read warnings
→ current Draft schema check
→ authoritative authorization snapshot
→ optimistic concurrency／revision exhaustion precheck
→ Draft completeness formalization
→ proposed SkillRevision
→ transient SkillDocument
→ existing P3-B2 resolution
→ existing P3-B3 validation／projection
→ immutable SkillSubmissionPlan
```

- Draft 使用 Missing／Present slot；Missing Trigger／Action 形成 bounded validation ERROR，任何 ERROR 都不得建立 partial `SkillDocument`。
- SkillId 在 Draft 建立時由 server-side mint contract 產生；client 不得鑄造。Transient mint grant 不是 reservation，也不是跨重啟 submission credential，提交授權必須使用當下 authoritative snapshot。
- authoritative submission precheck input 以純資料 owner／principal 與 new／existing sealed state 表達，不依賴 Minecraft `Player`／`ServerPlayer`，也不建立第二份 ownership Store。
- P3-C 只提出 revision：new skill 提出 0；existing skill 依 snapshot 中的 Store latest 使用P3-D1 additive建立的唯一`SkillRevision.successor()`。latest達`Integer.MAX_VALUE`時形成P3-C preparation `RevisionExhausted`，不建立Plan；失敗不消耗或保留revision。P3-C3與P3-D2不得各自保留`latest + 1`實作。
- optimistic concurrency conflict、identity rejection 與 revision exhaustion 使用獨立 machine-readable outcome，不偽裝成技能內容 `ValidationIssue`。
- identity rejection 不得透露未授權 SkillId 是否存在、latest revision 或 owner。
- proposed `SkillDocument` 使用 `SkillDocument.CURRENT_SCHEMA_VERSION`，保持 Draft SkillId、node order、Envelope 與 appearance storage state；不呼叫 Writer、不重新 decode Draft。
- 既有 `SkillCandidateResolver.resolve(typedDocument, emptyDocumentReadReport)`、`SkillValidationAnalyzer.analyze` 與 `SkillDefinitionProjector.project` 各恰好一次；不走 raw resolution，不重跑 Reader、migration 或 projection validation。
- `SkillSubmissionPlan` 至少攜帶 owner／principal、compare-and-insert precondition、proposed `SkillDocument` 與 `ValidatedSkillDefinition`。Prepared plan 不等於 committed revision，必須 immutable、transient、短生命週期且不可持久化。
- `SkillOwnerId` 的no Codec／StreamCodec邊界延伸至P3-D；只有P4 persistence開始時才additive翻轉為唯一canonical UUID Codec。

Submission short-circuit 順序固定為：read warnings → draft schema → authorization → concurrency／exhaustion → completeness → B2／B3 pipeline。Draft read facts、completeness issues 與 B3 report 合併為單一 bounded `ValidationResult`；每個來源 report 都必須先逐項加入 retained issues，再以 `ValidationCollector.inheritReportState` 傳播 `truncated`／`omittedError`。`Prepared`、`Conflict`、`IdentityRejected` 與 `RevisionExhausted` report 必須 warning-only。

## P3-D：SkillDefinitionStore domain API

P3-D建立production pure-Java aggregate。它是domain truth與behavior，不是interface-only、test-only Map、static global、singleton或第二個persistent adapter；不依賴SavedData、Minecraft API、Codec、DynamicOps或`setDirty()`。

Store shape：

```text
SkillDefinitionStore
└─ SkillId → active StoredSkillHistory

StoredSkillHistory
├─ immutable SkillOwnerId owner
└─ immutable retained SkillRevision → SkillDocument
```

owner binding與retained documents是唯一truth；latest／skillsByOwner／owner count／revision count／allocation counter只能可重建，D1／D2第一版不建立。Committed document不可覆寫，Store不保存Validated definition、Plan、Outcome、ValidationResult或Draft。

### P3-D共同 hard ceilings

```text
MAX_COMMITTED_SKILLS_PER_OWNER = 256
MAX_COMMITTED_SKILLS_GLOBAL = 4096
MAX_RETAINED_REVISIONS_PER_SKILL = 128
MAX_RETAINED_REVISIONS_GLOBAL = 32768
MAX_RETENTION_ROOTS_PER_RECLAIM = 65536
```

後續Java常數只放`MagicSafetyCeilings`或唯一canonical位置。Count ceilings不取代P4 encoded-byte bounds。

### P3-D thread／atomic contract

- Store由server logic thread confinement使用；不承諾arbitrary-thread linearizability，不加lock／`synchronized`，不依賴Minecraft thread API檢查caller。Misuse是programming-contract violation。
- Quota admission、owner check、CAS、capacity與insert在單一aggregate method中完成。
- 全部typed failure checks在第一個truth mutation前完成；failure時state不變。
- 完整replacement與success result建立後，才單次outer-map insert／replace；mutation後不呼叫provider／Codec／Reader／Writer／validator／external callback。
- 不承諾OOME或任意`Error` rollback，不宣稱database transaction或Store／Attachment跨位置原子。

### P3-D1：Store truth、read與snapshot

責任：

- 建立production pure-Java `SkillDefinitionStore`與package-private history state。
- 最小read API：fixed-reference lookup、latest reference、owner lookup與由owner bindings推導的owner skill count；不回傳mutable map。
- Retained history允許稀疏；gap不等於corruption。Max retained key是latest與implicit retention root，一般reclaim不得移除。
- 損壞包括empty active history、route/document ID或revision不符、raw duplicate、null owner與超hard ceiling。
- 建立detached immutable persistence snapshot／validated restore，只含active SkillId、owner與retained documents；無Codec，snapshot不是第二truth。
- additive建立唯一`SkillRevision.successor()`，建議`Optional<SkillRevision>`；非MAX回下一值，MAX回empty。P3-C3 proposer與P3-D2共同使用，它不表示allocation。
- `SkillOwnerId`仍無Codec／StreamCodec／String representation。

Tests：empty、owner binding、revision 0、sparse history、max-derived latest、immutable lookup／snapshot、malformed restore、hard boundaries、successor normal／MAX、無mutable exposure。

禁止：commit、quota、pin、reclaim、SavedData。

### P3-D2：Atomic admission、CAS與insert

Quota：

```text
SkillQuota
├─ Unlimited
└─ Limited(maxCommittedSkills: 0..256)
```

Unlimited只取消額外per-owner policy quota，不能突破hard ceilings。Quota計算per-owner distinct active committed SkillId；New成功+1，Existing／failed commit／old revision reclaim不變。只在確認Absent後檢查；Existing不因policy降低而被拒絕。Composition在fresh authorization後傳入immutable quota snapshot，Store內不重讀provider。Retire完成前預設policy為Unlimited；Limited目前沒有release workflow。

Capacity：

```text
SkillStoreCapacityScope
├─ OWNER_SKILL_HISTORIES
├─ GLOBAL_SKILL_HISTORIES
├─ SKILL_RETAINED_REVISIONS
└─ GLOBAL_RETAINED_REVISIONS
```

`CapacityRejected(scope,current,maximum)`只代表technical ceiling；current／maximum非負且current至少為maximum。Policy使用`QuotaRejected`，兩者不可混用。

Commit input固定為`SkillSubmissionPlan + immutable SkillQuota snapshot`。Plan不是credential；composition仍需fresh reauthorization。每個active SkillId恰有一個immutable owner：New以同一replacement建立owner與revision 0，Existing owner不變且不可藉submission transfer。Mismatch回`OwnerRejected(SkillId)`，不保存actual owner或latest，composition對外opaque-map。

CAS precedence：

```text
ExpectedAbsent:
programming invariants
→ present: ExpectedAbsentButPresent
→ per-owner history capacity
→ global history capacity
→ policy quota
→ global retained-revision capacity
→ replacement
→ one insert

ExpectedLatest:
programming invariants
→ absent: ExpectedLatestButAbsent
→ owner mismatch: OwnerRejected
→ actual latest mismatch: LatestMismatch(expected, observed)
→ successor/proposed-revision invariant
→ per-skill retained-revision capacity
→ global retained-revision capacity
→ replacement
→ one replace
```

Owner mismatch先於latest mismatch。Normal result：

```text
SkillStoreCommitResult
├─ Committed(SkillReference)
├─ Conflict(SkillStoreCommitConflict)
├─ QuotaRejected(SkillId, current, maximum)
├─ CapacityRejected(SkillStoreCapacityScope, current, maximum)
└─ OwnerRejected(SkillId)

SkillStoreCommitConflict
├─ ExpectedAbsentButPresent(SkillId)
├─ ExpectedLatestButAbsent(SkillReference)
└─ LatestMismatch(expected, observed)
```

合法Plan不含`ExpectedLatest(MAX)`，normal commit result無`RevisionExhausted`；Plan後actual latest前進MAX形成`LatestMismatch`。非法Plan在successor前fail fast，不為測試放寬Plan construction。Result不保存Plan／document／definition／report／actual owner／arbitrary diagnostic。

相同Plan第二次依branch回`ExpectedAbsentButPresent`或`LatestMismatch`；無AlreadyCommitted、attempt UUID、retry cache、document equality idempotence或automatic retry。Conflict後重新讀state並完整reprepare。

Tests：ExpectedAbsent／ExpectedLatest全部branch、quota exact／over／Unlimited／0、四種capacity、owner privacy、zero-state-diff for every typed failure、single replacement、repeat conflict、no revalidation／Codec call、no normal exhaustion variant。

禁止：pin／reclaim、SavedData、composition、retry。

### P3-D3：Pin、complete roots與reclaim

Mark set：

```text
每個active history的implicit latest root
∪ complete external persistent roots
∪ active in-memory pins
```

External roots由P4-E complete player-root audit提供player latest／equipped、pending journal與每一個
已啟用的SkillInstance／Marker／Construct／Schedule persistent source family；P3-D不依賴這些
runtime classes。Owner不pin全部revisions。只有non-latest、無external root且active pin count
為0者可回收。

- pin missing不建立ghost root。
- multiple handles分別計數；double-close idempotent。
- active pins不持久化，restart後由authoritative persistent roots重建。
- root集合超過65536、截斷或不完整時fail closed，整輪不sweep。
- report只保存bounded counts，不回傳reference list。
- reclaim後history可稀疏，不移除owner binding或降低quota count。

P3-D1～D3不實作whole SkillId delete／retire／owner removal／tombstone／quota release。移除Attachment latest／equipped不等於Store retire；owner與latest保留，quota不釋放。未來retire需獨立operation、無roots／pins確認及persistent tombstone／等價no-reuse truth，另立scoped amendment。

Tests：missing／multiple pins、double-close、latest root、external root、unreferenced non-latest reclaim、sparse後successor、incomplete／over-limit roots zero sweep、bounded report、無P4/runtime依賴。

禁止：SavedData、Runtime SkillInstance implementation、Attachment、scheduler、automatic root discovery。

Composition facade留給P4-D：authenticated principal → single Store authority snapshot → 唯一provider
的一份immutable quota／ValidationContext snapshot → P3-C prepare → persistence／journal preflight → final
identity／authority recheck → P3-D commit → Attachment transition。Facade不把Player／ServerPlayer傳入
domain API。

## P3 Definition of Done

- P3-A 模型與 Codec 的 hard bounds、alias isolation 與跨 Ops 測試通過。
- P3-B 不持久化 resolved／unknown classification 或 runtime projection。
- P3-C 可產生 immutable plan 或 typed failure；不寫 Store、不配置正式 revision，revision proposal exhaustion 不 overflow，所有非 Invalid outcome report 都是 warning-only。
- P3-D1 production aggregate truth／read／snapshot、P3-D2 atomic quota／owner／CAS／capacity／insert與P3-D3 pin／reclaim皆可測；沒有SavedData或第二個production persistent adapter。

---

# 階段 P4：Skill Store persistence、player Attachment 與 composition

P4 由已核准的 [`18_P4持久化與組合修正案.md`](18_P4持久化與組合修正案.md)治理。
P4-0只修訂權威文件；修正案提交且遠端CI通過前不得開始P4-A1。P4-A2.0只明確化
P4-A子階段契約；該文件變更提交且遠端CI通過前不得開始P4-A2 implementation。
P4-B0只明確化SavedData outer framing、no-journal sentinel、strict gzip與lifecycle；該文件
變更提交且遠端CI通過前不得開始P4-B1 implementation。
P4-D0只明確化journal physical framing、partial availability、policy ownership、composition與
combined fixed-heap authority；該文件boundary完成前不得開始P4-D1 implementation。
P4-C0只修訂player Attachment quarantine authority；整份文件變更提交且遠端CI通過前不得開始
P4-C1。P4-C1完成physical／serializer Gate後才可開始P4-C2 registration／lifecycle；C1、C2與
required remote fixed-heap Gate全部通過前，P4-C不得標記完成。
P4-E0-B只將核准的V0 root-audit政策同步進權威Markdown；該文件patch提交、
push且remote closure完成。P4-E0-B.1只補完integrated runtime snapshot計量與freshness
authority，並修正前次read-only review命中Stop Rule後仍留下的stale `OPEN`；B.1
commit／push／remote closure前P4-E1 review保持blocked，review核准前implementation仍為
`NOT STARTED`。P4-E0-B.2完成effective `MaxHeapSize` authority correction後，E1-A已closure；
其後E1-B read-only review因online source counter applicability gap停止。P4-E0-B.3只固定online
source coordinate／arbitration／ordering／freshness／E3 obligation；B.3 commit／push／remote closure前
新的E1-B review blocked，closure後須從頭重開review，不直接開始implementation。E1完成前不得
開始E2，E2完成前不得開始E3。P4-E0-B.4只固定memory-only root-index generation／exhaustion、
E2 invalidation、Complete permit／handoff lease與removeServer authority；B.4 commit／push／remote
closure前（前次review已停止於`INDEX GENERATION / EXHAUSTION AUTHORITY GAP`）P4-E1-B2-B
read-only review blocked，closure後只重開review，implementation維持`NOT STARTED`。
P4-E1後來已`COMPLETE`；P4-E2 read-only design review已`COMPLETE — PASS; NO SPLIT`，product
implementation已在verified repaired backup本地完成且focused tests PASS，但尚未commit至main。
B.6／A0.3／B.7／A0.4保留為historical technical authority／research，Candidate10–12 formal
receipt track由active B.8停止。P4-E2 simplified release qualification為`READY; NOT STARTED`且
cold為`0/3`；尚待執行的P4-C2及later product Gates在simplified cold 3/3前blocked，E3在
P4-E2 implementation與release closure前blocked，P4-E仍`INCOMPLETE`。
E0-B／B.1／B.2／B.3／B.4／B.5／B.6／B.7不寫Java／Gradle／CI，不重跑R1／R2／R2Q formal study。

P4-A1～A3、P4-B1／B2與P4-C～E不得重寫P3-D owner truth、quota counting、CAS、revision allocation或reclaim
policy，也不得重跑P3-B2／B3 resolution／validation。

## P4-A1：Raw tree／mixed-family document bridge

### 責任

- `SkillOwnerId` additive建立唯一canonical UUID Codec；不建立String owner、平行PlayerId或
  StreamCodec。
- 建立JSON／NBT／RegistryOps family／context唯一判定、bounded raw bytes與strict raw Codec。
- 每個Trigger／Action payload、top-level Unparsed appearance與每個Unparsed override個別
  保存V0 `RawTreeEnvelope`。同一document／node可mixed JSON與NBT；RegistryOps只是可重綁
  context，不是第三family。
- 建立mixed-family current document encode／hydrate、appearance physical mapping與shared
  logical document bounds。

### Gate

- JSON、NBT與mixed-family document都能在相同family下結構保存；無跨family conversion。
- Plain／RegistryOps／compressed JsonOps rebind、invalid family／context、appearance fallback與
  A1 raw／document bounds通過。

### 禁止

Store physical schema、migration、carrier、SavedData、Attachment、production payload migration、
submission facade。

## P4-A2：Store blob format 與 migration-before-restore

### 責任

- 建立`store_schema_version`、list-based length-delimited Store／History／Revision physical
  schema與`family_tagged_subtrees_v0`；duplicate route保留至P3-D restore。
- 只新增並消費revision／history／Store三個nested encoded-byte ceilings，使用bounded／counting
  encode、checked totals且不按theoretical maximum預配置。
- 建立唯一`StorePersistenceMigrationPlan`；不建立History／Revision平行plan。SavedData outer
  migration屬P4-B，payload migration不在load執行。
- P4-A2 document／migration package visibility恰好核准兩個窄public facade classes：
  document package的`SkillDocumentStorePersistenceFacade`與migration package的
  `OpaqueSkillDocumentMigrationFacade`；最小handle／result與P4-D composition facade不計入此
  phase-local數量。
- `SkillDocumentStorePersistenceFacade.encodeCurrent(...)`只接受current-schema immutable
  `SkillDocument`，委派A1 package-private current encoder，執行logical／document byte bounds，
  並回defensive immutable `EncodedSkillDocument`／typed failure；legacy／future不得成功encode。
- 同一facade的public `load(...)`是唯一decode入口：current schema也要probe並經唯一
  production plan的0-step orchestration，legacy經adjacent steps，future拒絕；logical token
  驗證／reinsertion後才呼叫A1 package-private current hydrate。
- `EncodedSkillDocument`只是有界、defensive immutable whole-document bytes carrier，不公開
  per-subtree／physical field／mutable tree，`toString()`只顯示byte count。Document／migration
  facade之間的logical-tokenized handle則完全不含raw payload bytes，也不公開physical DTO、
  `RawSkillDocumentSnapshot`、`Dynamic`或`Tag`。兩者必須是不同nominal types，不得共用
  裸`byte[]`或同一wrapper。
- 新增唯一immutable production plan provider `SkillMigrationPlans.production()`或等價API，
  bootstrap audit、P3-B2 `resolveFromRaw`與opaque facade共用；P4-A2 facade caller不得注入plan。
- `PipelineFactReport` additive提供immutable bounded append／merge，保持left-before-right、
  source truncation OR、no dedup與共用cap；不公開mutable collector。
- Store／History／Revision使用schema-aware exact-field NBT preflight，在完整Compound
  materialization前拒絕duplicate／unknown／missing／wrong-type field，驗證list physical count
  sanity、nested byte length與trailing input；不得使用last-write-wins或`unlimitedHeap()`。
- 固定load順序為Store migration → logical tokenized conformance migration →
  type／schema／token／location／context／exactly-once validation → exact reinsertion → A1 hydrate →
  current Store snapshot → P3-D restore exactly once；所有failure total且no-partial。
- Current Store blob encode固定從`SkillDefinitionStore.snapshot()`取canonical histories／
  revisions，每份document只呼叫`SkillDocumentStorePersistenceFacade.encodeCurrent(...)`，
  再組成Revision → History → Store envelopes與bounded blob。Store package不直接呼叫
  `SkillDocumentPersistenceBridge`，不複製document encoder，不使用`SkillDocumentWriter`、
  `Codec.PASSTHROUGH`或單一`DynamicOps` writer重建mixed-family document。
- 提供current Store blob encode／always-migrating load與bounded migration facts，不安裝live
  world state。

### Skill migration logical conformance view

- `SkillMigrationPlan`的權威輸入是logical `SkillDocument` outer schema。P4 persistence不得把
  physical `RawTreeEnvelope` wrapper交給`SkillMigrationStep`，也不得把tokenized physical tree
  宣稱為另一份skill schema真相。
- P4為每份document建立logical conformance view：保留`SkillDocument` logical outer fields、
  `DefinitionEnvelope.type`與`DefinitionEnvelope.schema_version`；Definition payload root及每個
  Unparsed top／override appearance raw slot以opaque token sentinel取代。
- `family`、`registry_context`、`compressed_maps`與exact raw bytes只存在package-private ordered
  side table，不進migration-visible tree。Migration facade只接收這份raw-free logical view。
- `SkillMigrationStep`不得讀取、遍歷、比較、hash、計算或依賴Definition payload、raw family、
  registry context、compressed-maps狀態、Unparsed appearance raw內容或token sentinel內容；不得
  依這些內容branch、產生fact、判斷schema或決定是否套用edge。
- `SkillMigrationStep`不得修改`DefinitionEnvelope.type`、payload `schema_version`、payload／raw
  slot、token ID／location／context，也不得新增、刪除或搬移opaque slot。它只處理
  `SkillDocument.schema_version`、skill／document outer fields與該edge明文屬skill-level schema的
  node／document shell欄位。
- 每個production edge都必須有representation-independence test：相同outer shell搭配JSON、NBT、
  RegistryOps raw payload或logical token view時，outer output與facts完全相同，opaque slot原值
  不變。目前production plan為empty，沒有既有production step需要改寫。
- P3-B2 `resolveFromRaw`仍是正式raw-ingress pipeline；P4 load不呼叫它，因單一
  `DynamicOps`無法表示mixed-family document。P4 opaque facade使用同一
  `SkillMigrationPlans.production()`及相同migration policies，且合規shell-only step在
  `resolveFromRaw`與P4 logical view上必須產生相同outer result／facts。不得把
  `resolveFromRaw`描述成test-only、legacy-only或deprecated入口。
- Java型別系統不負責沙箱化trusted migration code；Javadoc、source review、每個production
  edge的representation-independence tests與P4 token validation共同承保此契約。

### Opaque token invariants

- Side table entry固定綁定`tokenId + typed original location + SerializedTreeContext + exact
  immutable raw bytes`。Typed location至少有`TriggerPayload(nodeIndex)`、
  `ActionPayload(nodeIndex)`、`AppearanceOverride(nodeIndex)`與`TopAppearance`。
- Token依node index ascending的trigger、action、unparsed override配置，最後配置top appearance；
  最大數由既有結構推導為`3 × MAX_NODES + 1 = 769`，不得新增同義hard ceiling。
- Logical payload／raw slot root使用只含
  `"__gramarye_opaque_raw_token_v0": IntTag(tokenId)`的transient sentinel；sentinel不持久化、
  不攜帶family／context／raw bytes，也不得被step觀察。
- Migration後每個token必須保持原ID、原typed location、原context並恰出現一次；相對應的
  `DefinitionEnvelope.type`與payload `schema_version`也必須不變。拒絕relocation、exchange、
  type／schema改寫、unknown／missing／duplicate token、token-bearing slot刪除、新增raw slot或
  任何ID／location／context mutation。
- Raw bytes exact reinsert後才呼叫A1 current hydrate。Logical-tokenized outer tree仍受skill
  migration depth／node ceilings且每step重新snapshot；A1再對完整hydrated mixed-family document
  執行真實logical shared budget。256 KiB raw payload不得被當成262,144個migration nodes，
  tokenized bounds也不得取代hydrated bounds。

### Physical count 與 domain count

- Exact-field preflight對list count只驗physical／arithmetic sanity：count非負、element tag type
  正確、checked arithmetic不overflow、declared count與enclosing blob remaining bytes／minimum
  framing相容、nested byte length受界，且完整consume後沒有trailing bytes。
- Preflight不得引用或執行P3-D的`MAX_COMMITTED_SKILLS_PER_OWNER`、
  `MAX_COMMITTED_SKILLS_GLOBAL`、`MAX_RETAINED_REVISIONS_PER_SKILL`或
  `MAX_RETAINED_REVISIONS_GLOBAL`；不得依untrusted declared count預配置巨大collection。
- Physical impossible／negative count、wrong element type、count／remaining-bytes不相容、duplicate／
  unknown field、wrong tag type或trailing bytes回對應`MalformedStoreEnvelope`、
  `MalformedHistoryEnvelope`或`MalformedRevisionEnvelope`，且restore invocation count為0。
- Physical byte超限依層級回`StoreBlobEncodedCapacityExceeded`、
  `HistoryBlobEncodedCapacityExceeded`、`RevisionBlobEncodedCapacityExceeded`或
  `DocumentBlobEncodedCapacityExceeded`，且在該層內部parse前停止。
- Physically valid但domain count超限的input必須逐項bounded decode、建立list-based
  `SkillDefinitionStoreSnapshot`並恰好呼叫`SkillDefinitionStore.restore`一次。結果固定為
  `StoreRestoreRejected(SkillDefinitionStoreRestoreFailure.CapacityExceeded(...))`；不得分類為
  `Malformed*Envelope`、migration failure或P4 count-capacity failure，也不得建立
  `StoreCountCapacityExceeded`／`HistoryCountCapacityExceeded`／
  `RevisionCountCapacityExceeded`等平行vocabulary。
- Domain count failure的canonical scopes／metadata固定為：4,097 histories →
  `GLOBAL_SKILL_HISTORIES, 4097, 4096`；same-owner 257 histories →
  `OWNER_SKILL_HISTORIES, 257, 256`；one-skill 129 revisions →
  `SKILL_RETAINED_REVISIONS, 129, 128`；32,769 total revisions →
  `GLOBAL_RETAINED_REVISIONS, 32769, 32768`。Fixture必須避免先命中另一個scope。
- Duplicate SkillId／revision、document SkillId／revision mismatch、unsupported current schema與
  empty nodes仍由P3-D restore依既有precedence回`DuplicateSkillId`、`DuplicateRevision`、
  `DocumentSkillIdMismatch`、`DocumentRevisionMismatch`、`UnsupportedDocumentSchema`或
  `EmptyDocumentNodes`；A2不得先Map化、deduplicate或另造domain failure。

### Revision outer ceiling

- `MAX_STORE_REVISION_ENTRY_ENCODED_BYTES = 1_114_112`只表示inclusive outer admission。
- V0 framing為`85 + document_bytes.length`；最大合法document導出的canonical V0 revision為
  `1_048_661` bytes並須完整round-trip。
- Outer exact不得誤報revision capacity；outer MAX + 1須在parse前拒絕；outer exact但inner
  document超限須回document capacity failure且不得hydrate。
- 實作若得到非85-byte wrapper，停止並回報，不得修改數值或期待。

### Gate

- 四類獨立migration／restore測試通過：migrated success、same legacy direct restore rejection、
  migration failure且hydrate／restore 0 calls、migration success後domain restore rejection。
- JSON、NBT、RegistryOps與logical token view的representation-independence tests證明：相同outer
  shell產生相同outer output／facts；family／context／content不影響branch；opaque slots保持
  原值；`resolveFromRaw`與P4 view對合規shell-only test step結果一致。
- `encodeCurrent` 可由Store package合法呼叫，與A1 internal encoder產生結構等價、
  context相同的bytes，且`EncodedSkillDocument` defensive copy／bounds／bounded
  `toString()`通過。
- Store current encode的document call sites只經document persistence facade；Store package不引用
  `SkillDocumentPersistenceBridge`、`RawTreeEnvelope`、physical document DTO或
  `AppearanceStorageCodec` internal
  persistence methods，也不呼叫`SkillDocumentWriter`／`Codec.PASSTHROUGH`／單一
  `DynamicOps` mixed-family writer。
- Public load對current／legacy都執行schema probe與migration facade；production只有上述
  兩個P4-A2 public facades，不存在public current-only decode／hydrate／load bypass、caller plan或
  mutable tree API，bootstrap audit與load共用同一production plan。
- Migration-visible tree不含raw bytes；token ID／location／context／type／payload schema保持，
  exactly-once、same-context exchange／relocation／type-schema rewrite拒絕，raw exact reinsertion與
  logical-tokenized／hydrated雙層bounds通過。
- Fact merge順序／cap／truncation／immutability與exact-field NBT duplicate／type／length tests通過。
- Negative／impossible count、wrong element type與count／remaining-bytes不相容各回
  `Malformed*Envelope`且restore 0 calls；可觀察decoder證明最多bounded incremental consumption，
  不依declared count預配置collection。
- Physically valid 4,097 histories、257 same-owner histories、129 per-skill revisions與32,769 total
  revisions各自呼叫restore恰好一次，回`StoreRestoreRejected(CapacityExceeded)`的正確P3-D
  scope；不得是`Malformed*Envelope`或P4 count-capacity failure。
- Revision四項size semantics與History／Store exact／+1通過；任一failure不產生partial／empty
  Store。

### Static／phase gate

- `SkillMigrationStep`與opaque facade Javadoc明載payload／raw／data opaque；每個production step
  不得依payload內容branch，且具representation-independence coverage。
- Production migration facade input沒有raw bytes；不存在public current-only hydrate／decode／load、
  第二份`SkillMigrationPlan`、caller-supplied plan或raw-containing tokenized transport。
- Exact-field preflight source不引用P3-D四個domain count ceilings，不存在P4
  `StoreCountCapacityExceeded`類型；domain count overage只經`StoreRestoreRejected`。
- List decoder不以declared count預配置巨大collection。這些gate約束repository中的trusted code，
  不宣稱能沙箱化任意惡意migration implementation。

### 禁止

`saved_data_schema_version`、SavedData outer carrier、hierarchical live carrier、journal、commit
preflight、heap probe、dirty、world I/O、Attachment、submission facade、public current-only
decode／hydrate／load／skip-migration bypass、public Raw snapshot／`Dynamic`／`Tag`、caller-supplied
migration plan、physical RawTreeEnvelope作為skill migration input、payload inspection、
DefinitionEnvelope type／payload schema mutation與P4 parallel domain-count failure。Public current
document encode只允許經`SkillDocumentStorePersistenceFacade`；P4 load不得呼叫
`resolveFromRaw`，但不得把該P3-B2正式入口描述成legacy／test-only。

若單一document persistence facade無法同時提供current encode與always-migrating load，
而不公開physical DTO、current-only decode／hydrate或P3 internals；或transport仍含不該出現的
raw、logical conformance view無法維持payload opacity／representation independence、
type／schema／typed-location binding不能驗證、physical preflight必須執行P3-D domain count或
另造parallel count failure、exact-field preflight不能在materialization前完成、outer／inner
capacity無法分離、唯一plan provider或bounded fact merge無法維持封裝，或A2需要A3／B／D
責任，必須停止並回報；不得新增第三個facade、複製A1 encoding或把`resolveFromRaw`降級。

## P4-A3：Derived Store carrier primitives 與 heap validation

### 責任

- 建立pure immutable hierarchical encoded Store carrier、從current Store snapshot full rebuild、
  New／Existing history／revision replacement builders、reclaim filtering與checked aggregate totals。
- 提供prospective Store-only carrier calculations；old carrier保持immutable。
- 以fixed-heap dedicated server驗證64 MiB Store ceiling。

### Gate

- Full rebuild、replacement、filtering、checked totals、old-carrier isolation與fixed-heap probe通過。

### 禁止

SavedData lifecycle／publication、dirty、Store commit、journal domain／publication、Attachment與
composition。P4-A3不建立任何persistent schema version。

## P4-B1：Pure outer carrier／load state

### 責任

- 建立`saved_data_schema_version`與SavedData outer migration。Production current schema為0，
  production plan為immutable empty adjacent plan；outer migration只處理inner carrier layout，
  `store_blob`交P4-A2，pending blob保持opaque。
- Whole root固定為unnamed Compound，exact fields只有`data` Compound與`DataVersion` Int；inner
  `data` exact fields只有`saved_data_schema_version` Int、`store_blob` ByteArray與
  `pending_attachment_updates_blob` ByteArray。Field order無語意，但duplicate／unknown／missing／
  wrong type與decompressed trailing input全部拒絕；`DataVersion`不作Gramarye migration axis。
- `pending_attachment_updates_blob`欄位必須存在，zero-length ByteArray payload是唯一canonical
  no-journal sentinel。Non-zero blob只做byte bound、defensive immutable copy與byte-exact opaque
  preservation；不解析journal schema／entries／generation／pointer。
- `MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES`只計pending ByteArray raw payload；
  `MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES`計完整unnamed inner Compound encoding；V0 whole root
  固定比inner encoding增加26 bytes。Finite `NbtAccounter` quota固定`69_206_405`；golden fixture不符
  即停止，不自行修改數值。完整推導以18號P4修正案
  [§5](18_P4持久化與組合修正案.md#5-persistent-store-physical-schema)與
  [§7](18_P4持久化與組合修正案.md#7-exact-hard-byte-ceilings)為準。
- 使用schema-aware strict reader，在完整`CompoundTag` materialization與ByteArray allocation前驗證
  exact fields、length與layered bounds；不得使用`unlimitedHeap()`。
- Outer migration完成後將`store_blob`委派P4-A2 complete load／restore，再由P4-A3 full rebuild
  matching carrier。只有全部成功才產生Ready candidate；任一失敗不得產生partial／empty Ready。

### Gate

- Whole-root／inner exact fields、duplicate／unknown／missing／wrong type、field-order independence、
  root trailing input、zero sentinel與non-zero opaque byte-exact preservation通過。
- Pending raw exact／+1、inner carrier exact／+1、whole-root +26 golden、finite quota exact fixture與
  nested Store failure classification通過；outer failure時P4-A2 invocation為0。
- Current／legacy／future／missing-edge／partial／RuntimeException／Error migration policies、opaque
  store／pending blobs、A2 success＋A3 rebuild、canonicality／rewrite flag與no-partial result通過。

### 禁止

SavedData subclass、world／filesystem／`DimensionDataStorage`、dirty、Attachment、journal domain、
composition、Store／History／Revision encoding重寫、`unlimitedHeap()`與raw quarantine copy。

若whole-root +26 golden不成立、finite quota拒絕合法exact fixture、strict preflight無法在
materialization／ByteArray allocation前完成，或B1需要world lifecycle／journal parsing，停止並回報。

## P4-B2：Overworld platform lifecycle

### 責任

- Stable name固定`gramarye_skill_definitions`；唯一位置是Overworld data storage，Nether／End caller
  取得同一Overworld adapter，不使用static cross-server singleton。
- `ServerStartingEvent`時由`server.overworld()`執行一次custom bounded primary load，再把exact
  Ready／Quarantined SavedData instance安裝到`DimensionDataStorage` cache。安裝前不得有Gramarye
  accessor；安裝後不得以cache-miss `computeIfAbsent`或平台loader重讀disk。
- Primary固定`<world>/data/gramarye_skill_definitions.dat`。先以`NOFOLLOW_LINKS`驗regular
  non-symlink與compressed-file size，開啟同一channel後再驗size；不用`Files.readAllBytes`。
- Compressed input只接受exactly one gzip member與compressed EOF；解壓後只接受exactly one
  unnamed whole root與EOF。第二member／root、任意trailing garbage與zero padding全部拒絕。
- Absent primary建立matching empty Ready且不dirty；existing unreadable／invalid primary安裝
  Quarantined，不建立empty Store、不dirty、不覆寫。`.dat_old`不自動讀取、提升或刪除。
- State固定為Ready／Quarantined／Unavailable。Ready持有domain Store、matching immutable A3
  carrier、immutable opaque pending bytes與rewrite-required；Quarantined只表示load-time failure，
  Unavailable只表示runtime Store／carrier pairing invariant failure。後兩者不保存empty Store或
  stale carrier、不dirty、不save；save callback只接受Ready，其他state在修改output前fail fast。
- Save callback只由Ready的prebuilt logical state建立固定三欄inner Compound，不執行Store snapshot、
  A2／A3 encoding、migration、reclaim、journal parse或首次capacity check。只承諾in-memory committed
  + persistence scheduled，不承諾disk success、fsync或cross-location durable atomicity。
- Controlled API只提供typed find／latestReference／ownerOf／committedSkillCount／pin／reclaim，
  不公開裸Store、carrier或state。P4-E提供complete roots；B2執行reclaim與publication：Rejected／
  reclaimed=0不變且不dirty，reclaimed>0先發布matching carrier再dirty，filter invariant failure轉
  Unavailable、清除未排程dirty／non-saving且不使用舊carrier。
- `rewriteRequired`由outer migration、P4-A2 migration或source Store blob與A3 canonical bytes不相等
  決定，不由可能truncated的facts推導。Ready publication後才為canonical rewrite dirty。
- 建立`-Xms512m -Xmx1024m -XX:+ExitOnOutOfMemoryError` dedicated-server Gate，使用至少63 MiB
  actual Store carrier覆蓋strict gzip ingress、B1／A2／A3 load、Ready install、save callback、平台
  whole-root deep copy、dirty scheduling與restart round-trip。

### Gate

- Absent／unreadable／invalid／symlink／nonregular／replacement race、compressed exact／+1、single
  member／second member／trailing bytes、`.dat_old`三種組合與primary不變性通過。
- One-time `ServerStartingEvent` install、cache exact instance、Overworld／Nether／End同adapter、不同
  server不共享、無`computeIfAbsent` disk reread與repeated install rejection通過。
- Ready／Quarantined／Unavailable、non-saving defensive callback、full dirty／reclaim matrix、
  publication-before-dirty、canonical rewrite與current／empty／invalid restart round-trip通過。
- Full-size fixed-1 GiB dedicated-server load／save／restart Gate通過，且沒有OOME／timeout。

### 禁止

Player Attachment、journal DTO／schema／generation／entry parsing、Store commit composition、submission
facade、offline root collection、network、直接公開Store／carrier、在callback首次encode或自行加入lock。

若strict gzip API無法證明first member結束及underlying compressed EOF、bounded custom instance無法
一次安裝cache、必須讓`computeIfAbsent`重讀、Quarantined無法阻止empty fallback／覆寫、Unavailable
無法阻止stale-carrier save、callback必須首次encode、需要未核准thread／lock模型、reclaim filter仍有
normal typed failure、只能公開裸Store／carrier接合，或1 GiB Gate失敗，停止P4-B2並回報。

## P4-C0：Player Attachment quarantine authority

只固定第18號修正案的totality、`NbtIo.writeAnyTag` coordinate、PreservedRaw／OversizeMarker、
logical-structural／destructive政策、List duplicate能力邊界、int／same-pointer-no-op、editor、
三軸migration與C1／C2 Gate；不寫Java。

Attachment total coordinate固定為canonical arbitrary-Tag counting：one Tag type byte + complete Tag
payload，不含root name、attachment key、outer playerdata或gzip framing。`writeUnnamedTag`禁止。
此為post-materialization bound；它不限制Minecraft首次playerdata allocation／OOM。

## P4-C1：Physical V0與total serializer

### 責任

- 建立Ready V0與alternative exact quarantine marker；`IAttachmentSerializer<Tag, ...>`只在平台已
  materialize、outer attachments為Compound且per-attachment non-null Tag進body後保證total。
  Custom read總回non-null Ready或Quarantined；expected data failure只回PreservedRaw或
  OversizeMarker，missing key才empty Ready。
- 使用只保存`long` count／maximum的bounded counting `DataOutput`委派`writeAnyTag`語意；exact
  16,777,216合法，觀察16,777,217立即停止。不得先copy、配置等長second byte array、使用
  `writeUnnamedTag`、SNBT length或whole-playerdata encode。Draft entry ceiling仍只計
  `draft_bytes` ByteArray raw payload，不套用Attachment total coordinate。
- In-bound malformed量後才deep-copy為PreservedRaw，write回fresh`raw.copy()`並只保證materialized
  logical NBT structural equality。Oversize不保存raw，write第18號fixed marker；此為明示destructive
  quarantine，restart仍是OversizeMarker，不得變missing／empty或稱lossless；raw與marker都使用
  同一`writeAnyTag` coordinate。
- Draft physical、Draft logical與Attachment outer migrations分離；建立prebuilt matching Ready
  carrier與五個exact bounds。Route collections使用List並拒絕route duplicate；不得宣稱偵測平台
  materialization前已last-write-wins的Compound duplicate names。
- Latest generation physical type固定`IntTag`且拒絕negative／`LongTag`；absent route是implicit
  `(empty, 0)`，explicit empty generation > 0保留。Editor hard-invalid形成Quarantined，
  structurally valid stale metadata原樣保留。

### Gate

- `writeAnyTag` type-byte＋payload/no-root-name golden、wrong-root進body、exact／+1、raw structural／
  alias／restart、marker exact／restart、Draft mixed-family／三軸migration、latest／equipped／editor
  physical round-trip、generation`IntTag` 0／N／MAX與`LongTag`／negative rejection、implicit／explicit
  empty state、hard-invalid／stale editor及no partial／default通過。

### 禁止

Attachment registration、player lifecycle mutation service、sidecar、whole-playerdata save blocking、
Store commit、journal、offline enumeration、reconciliation與network。

## P4-C2：Registration與immutable lifecycle

### 責任

- 註冊唯一`gramarye:player_skills`、custom serialize＋`copyOnDeath`、no sync；所有mutation使用
  server-thread immutable replacement `setData`。
- Same pointer是no-op；changed successor只由唯一P4-C checked helper計算。Generation mismatch、
  pointer mismatch與MAX changed transition均不呼叫`setData`。
- 提供P4-D controlled transition與P4-E bounded per-player root projection，不做commit、journal或
  offline enumeration／root completeness。
- Ready／PreservedRaw／OversizeMarker經death／End serializer write／read重建，不manual clone copy。

### Gate

- Missing default、immutable replacement、successful `setData` exactly once、same-pointer／MAX／mismatch
  no publication、P4-D seam identity checks與P4-E immutable bounded projection通過。
- Death／keepInventory on／off、respawn、End、dimension transfer、logout-login、Quarantined variants、
  no manual double-copy與no sync通過。
- GameTests與`-Xms512m -Xmx1024m -XX:+ExitOnOutOfMemoryError`專用Gate覆蓋exact 16 MiB raw
  load／save／restart／death／End及maximum + 1 marker。失敗即停止，不縮fixture或提高heap規避。

### 禁止

Store commit composition、journal domain／generation chain、revision allocation、Store owner／latest
覆寫、offline root completeness、Network／client sync。

## P4-D：Submission composition

完整authority以[18號P4修正案 §§14–16](18_P4持久化與組合修正案.md#14-submission-composition)
為準；本節只列phase execution boundary，不複製journal schema。

### P4-D0：Authority patch

- 只修改既有authoritative Markdown與architecture ledger。
- 鎖定`NbtIo.writeAnyTag` no-root-name journal framing、zero sentinel、1 MiB／4096座標、canonical
  continuous chains、derived Unavailable partial availability、unique policy／SkillId ownership、Draft
  retention、report identity與combined fixed-heap obligation。
- 禁止Java、Gradle、tests、resources、CI與第19號修正案。

### P4-D1：Journal model and Store port

- 建立strict pre-materialization journal decoder／migration、canonical writer、Ready／Unavailable
  operational state與root projection。
- 建立single Store authority snapshot及獨立窄`SkillDefinitionStoreSubmissionPort`；public不得暴露raw
  Store、carrier、SavedData Ready或pending bytes。
- Prebuild prospective Store carrier、journal及SavedData inner carrier，完成all byte/count/chain/audit
  checks，建立identity-bound opaque handle；commit恰一次，成功後publication再dirty。
- 不建立authenticated facade、policy provider或event listener。

### P4-D2：Normal submission

- Facade位於submission package，從authenticated player導出owner及own Ready Attachment Draft；使用
  single Store authority snapshot與唯一provider恰取一次的combined quota／ValidationContext snapshot。
- `SkillDraftCreationService`是第一個production `SkillIdSource` consumer；唯一UUID adapter由
  composition root持有。Successful submission保留Draft。
- P3-C C1～C4 stages各恰一次。Prepared後先prepare P4-C transition，再做全部Store／journal prebuild，
  最後在commit前fresh identity／authority recheck；normal same-pointer不commit。
- P3-D Store `Committed`才publication carrier／journal及dirty，再publication Attachment transition。
  Post-Prepared outcomes保留exact same report reference。
- 不建立recovery listener或network。

### P4-D3：Recovery and combined Gate

- 在P4-B install完成後且正常login前bootstrap journal；malformed／future／invalid chain保留exact blob，
  Store read／pin仍可用，但submission／recovery／production reclaim及journal roots fail closed。
- Persisted base replay chain而不clear；persisted intermediate／final target確認並clear prefix；third state或
  Store target invalid保留journal。只以later playerdata readback clear，不以`setData`作durability ack。
- Paired process覆蓋replay／clear crash windows。平台可能先replace playerdata再寫SavedData，不宣稱
  cross-file transaction或能由journal復原不存在於persisted Store的target。
- Single-process fixed `-Xms512m -Xmx1024m -XX:+ExitOnOutOfMemoryError` Gate同時保留full current／
  prospective Store、largest valid 4096-entry journal、SavedData deep copy與Attachment transition，完成
  commit／save／restart／recovery／clear；既有分離memory Gates不可替代。

### Gate

- Framing／duplicate／EOF、byte exact／+1、4096／4097、canonical／broken chain、partial availability、
  single authority／policy snapshots、SkillId ownership、Draft retention、P3-C exactly once、report
  `assertSame`、zero-mutation preflight、commit／Attachment outcomes、paired recovery及combined heap全部通過。

### 禁止

Network、caller-supplied owner／authorization／quota／context／revision／Store state、Attachment-first、
raw Store exposure、Store rollback、offline enumeration、P4-E implementation及重寫P4-A2 encoding。

## P4-E：Player roots 與 reconciliation

完整canonical authority是[18號修正案的§18](18_P4持久化與組合修正案.md)。
本節只將實作強制分成三個不可合併的工作包。

### P4-E0-B.6：Direct qualification coordinates與bounded nominal transport authority

B.6是documentation-only authority工作包；不修改Java／test／script／Gradle／workflow／resources，
不執行FML route technical review或runtime Gate，也不宣稱任何FML extension API存在。

#### Exact direct coordinates

| Coordinate | 唯一座標 |
| --- | --- |
| `RECOVERY_OUTCOME_DIRECT_COORDINATE` | `HANDLER_LOCAL_EXHAUSTIVE_CLASSIFICATION` |
| `E2_RESULT_DIRECT_COORDINATE` | `COORDINATOR_LOCAL_EXHAUSTIVE_CLASSIFICATION` |
| `E2_INVALIDATION_ATTEMPT` | actual central reconciliation invalidation operation invocation |
| `E2_INVALIDATION_ACCEPTED` | actual `Accepted` result branch after operation returns normally |
| `E2_SET_DATA_ATTEMPT` | actual JVM callsite immediately before the exact E2-bound `ServerPlayer.setData(PLAYER_SKILLS, replacement)` invocation |
| `E2_SET_DATA_SUCCESS` | immediate normal-return checkpoint after that exact invocation |

- `RECOVERY_OUTCOME_DIRECT_COORDINATE = HANDLER_LOCAL_EXHAUSTIVE_CLASSIFICATION`。Actual sealed
  `RecoveryOutcome`從`recoverPersistedPlayer(...)`回傳且仍是sole login handler／consume call chain
  local時，立即以exhaustive、無default pattern switch分類。只記exact variant、
  `entriesCleared`、`stepsReplayed`、variant既有bounded reason／kind與
  `recoveryChanged = entriesCleared > 0 || stepsReplayed > 0`。不得從journal bytes、
  `RecoveryKind`、entries或pending state推導。`RecoveryContinuation.consume(...)`對same object的
  identity pairing仍是production invariant；direct evidence不要求跨package／tick保存、公開或序列化
  identity，也不得把`RecoveryOutcome`／continuation／raw journal／`Throwable`放進completed record。
- `E2_RESULT_DIRECT_COORDINATE = COORDINATOR_LOCAL_EXHAUSTIVE_CLASSIFICATION`。Actual
  package-private `P4E2ReconciliationResult`由`reconcile(...)`回傳後先保存為local，並在reviewed
  public void wrapper丟棄前對actual result作exhaustive、無default分類。只記
  `NoChanges`／`RecoveryChanged`／`Changed`／`Deferred`／`Failed`／`GenerationExhausted`、既有
  bounded counts／reason及optional accepted-generation-presence；除非existing internal result已合法
  攜帶，不公開raw `long` authority coordinate。Public dependency保持void，result保持
  package-private；禁止public return／serialization、state equality反推、second reconcile與
  log／stdout-only evidence。
- `E2_INVALIDATION_ATTEMPT = actual central reconciliation invalidation operation invocation`。
  只在唯一central helper進入`SkillRetentionRootAuditService` exact operation時累計；互斥上層入口
  不得重複計同一attempt。
- `E2_INVALIDATION_ACCEPTED = actual Accepted result branch after operation returns normally`。
  只在actual `Accepted` branch累計；`GenerationExhausted`固定attempt 1／accepted 0。若index
  transition後、`Accepted` wrapper形成前發生`Error`／OOME，不標accepted、不發布partial record、
  原identity外拋且production index semantics不變。禁止由generation、index state、E2 result或source
  equality反推。
- `E2_SET_DATA_ATTEMPT = actual JVM callsite immediately before
  ServerPlayer.setData(PLAYER_SKILLS, replacement) in the exact E2-bound publication path`。
- `E2_SET_DATA_SUCCESS = immediate normal-return checkpoint after that exact setData invocation`。
  Publisher entry不是attempt；若publisher先回`STATE_CHANGED`，publisher invocation為1但setData為
  0/0。只有actual E2-bound path可計數，P4-D recovery、submission publication及其他caller皆排除。
  Shared private replacement method必須以exact E2-bound nominal session／capability識別來源，不得
  全域無條件計數。Attempt緊鄰invoke前，success緊鄰normal return後且早於enum lookup、result wrapper、
  callback或potentially failing diagnostic publication。`APPLIED`只是normal semantic cross-check，
  不是success coordinate。SetData已return但其後發生`Error`／OOME時，raw success可已發生，official
  record仍abort、不得發布、原identity外拋且mutation不rollback。E2-owned 0必須由callsite counter 0
  證明，不得由Attachment equality推導。

#### Test-armed bounded state

V0 observation只可test-armed、instance-owned、memory-only、bounded、single-use、same synchronous call
chain且normal-runtime inert；不是gameplay API、runtime／persistence／index authority、reconciliation
authorization、network protocol或production telemetry。State machine固定：

```text
IDLE → ARMED → RECORDING → COMPLETED → CONSUMED → IDLE
ARMED / RECORDING → ABORTED / CLEARED → IDLE
```

每exact production owner最多一筆active及一筆completed-unconsumed record，無queue／history。Armed
record綁exact mod／service identity、server identity、authenticated player UUID、logic thread、bounded
case token、first／restart phase與exact session identity。Completed record只可持有enums、booleans、
bounded primitive counts、UUID value、bounded phase／case identity與completion marker；不得持有
`MinecraftServer`、`ServerPlayer`、`Tag`／`CompoundTag`、Ready Attachment、Store／history／carrier、
journal object／proof、foreign owner、roots、`Path`、`Throwable`／message／stack、callback或raw NBT／
bytes。Completion、consume、discard、case failure及server stop立即清除strong references；`Error`／
OOME不捕捉／重分類，allocation-free clear partial state、不發布partial record並原identity外拋。

Unarmed normal path不建立per-login record、不record classification、不配置diagnostic result、不寫檔、
不改branch／failure／result；technical review若證明必要，只可保留bounded instance-owned inert
facade／cell field。

#### Closed nominal transport與conditional FML route

V0最多允許一個public top-level platform-facing diagnostic facade及必要的public nested sealed
session／view types。Implementation保持package-private／private，constructors／factories nonpublic，
public surface只提供exact nominal operation；同一exact session identity傳至submission／store／player
local cells。Public／protected surface禁止`RecoveryOutcome`、`P4E2ReconciliationResult`、
`Tag`／`CompoundTag`／`Dynamic`、Ready Attachment、Store／history／carrier／SavedData、actual owner、
roots、`Path`／`File`／`byte[]`、`Throwable`、callback／sink／`Runnable`／`Consumer`／`Function`、
generic arbitrary source、`Object` transport、raw type、unchecked cast及suppression。

External code即使取得facade，也不得construct valid session、subclass sealed types、arm未授權record、
forge completed record、consume其他session或取得internal diagnostics。Valid session只能由exact
allowlisted synthetic qualification source，透過same-package test adapter與nonpublic constructor／factory
建立。此為repository exact-source threat model，不宣稱Java unnamed-module／split-package能形成惡意
third-party sandbox。

平台receiver route只有條件式authority：per-`ModContainer` FML extension，或officially supported
equivalent exact-instance extension route。後續locked artifact／source technical review必須先證明
official registration、official retrieval、exact per-container identity、registration lifecycle、login前
retrieval、GameTest取得exact facade、first／restart各新instance、no static／global fallback及未誤用
client-only／display-only contract。若無合格route，固定`STOPPED — NO SAFE RECEIVER ROUTE`；不得改用
public static service locator、`ThreadLocal`、global registry、reflection、second listener、log／stdout／
file／JFR、system property或second reconcile。在technical review、closure及implementation工作單前
不得選定任何FML exact method或開始實作。

#### Cross-package cells與forbidden alternatives

同一nonforgeable session可具有submission-local、store-local及player-local direct cell。每cell只由
existing semantic owner記actual local fact，不成為第二coordinator、不改production control／failure／
result、不跨package傳raw object、不使用generic callback且completion後不保留actual object：

```text
Submission: actual RecoveryOutcome exhaustive classification
Store:      continuation count, actual E2 result, invalidation attempt/accepted
Player:     actual E2-bound setData attempt/success
```

若technical review證明Store-only cell仍滿足全部actual coordinates，可省略無用cell，但不得降低
coordinate。三個owner-local cells共用一個session與一個semantic coordinator，不構成P4-E2
phase／responsibility split，與既有`PASS; NO SPLIT` closure相容。V0明確拒絕static registry、
`ThreadLocal`、global service locator、second
`PlayerLoggedInEvent` listener、callback／observer／function injection、log／stdout evidence、file／JFR
custom side channel、reflection／`Unsafe`、second reconcile、state／generation推導、public E2 result、
transient diagnostic player `AttachmentType`、actual player Attachment map中的diagnostic session、
SavedData→service／runtime diagnostic backlink、persistent／serialized record、queued／multi-login history及
normal production每次login的last-result record。任何technical route需要其中之一即停止。

#### Evidence JSON、READY與negative controls

Production cell不得寫檔；test／probe runtime consume completed record後才可atomic publish canonical
UTF-8 JSON。Artifact必須regular non-symlink、最多65,536 bytes，duplicate／unknown／missing fields拒絕；
failed／partial session不發布，且不得包含absolute path、raw runtime object、message／stack／
`Throwable`。至少包含：

```text
schema_version
case_id
phase
recovery_handler_calls
typed_recovery_outcome
entries_cleared
steps_replayed
recovery_changed
e2_continuation_calls
e2_result_variant
invalidation_attempts
invalidation_accepted
invalidation_generation_present
e2_set_data_attempts
e2_set_data_successes
completion_marker
```

Attachment／Store checksums只在cell外由existing probe附加。P4-C2 READY first／restart都要直接觀察：

```text
recovery_handler_calls               = 1
typed_recovery_outcome               = NoPending
entries_cleared                      = 0
steps_replayed                       = 0
recovery_changed                     = false
e2_continuation_calls                = 1
e2_result_variant                    = NoChanges
invalidation_attempts                = 0
invalidation_accepted                = 0
invalidation_generation_present      = false
e2_set_data_attempts                 = 0
e2_set_data_successes                = 0
```

Pending bytes 0、Attachment equality、Store truth、generation不變及static call graph都不能替代direct
evidence；Attachment／Store truth仍是required cross-check。Future negative controls至少覆蓋：

- `NoChanges`：invalidation 0/0、setData 0/0。
- `RecoveryChanged`-only：recoveryChanged true、invalidation 1/1、setData 0/0。
- `Changed`：invalidation 1/1、setData 1/1。
- `GenerationExhausted`：invalidation attempt 1、accepted 0、setData 0/0。
- Publisher state drift before setData：publisher invocation occurred、setData 0/0。
- Accepted invalidation後／setData前failure：invalidation 1/1、setData success 0。
- `Error`／OOME：partial record不可consume為PASS，original identity外拋。

Normal GameTest required count維持12，negative controls可由unit／probe tests完成。

#### Zero-delta與closure boundary

Recovery semantics、E2 result、invalidation、setData、Attachment content、Store／journal mutation、index
generation、listener、network、persistent data、background thread及callback delta全部為0；這些direct
qualification fields不是第26個root-audit counter。25 counters、`DataVersion 3955`、DFU 0、heap
floor、B.4 generation authority、R2Q profile／case plan／evidence、P4-E3 same audit-service identity、
snapshot／reclaim call graph與fixed-1,536-MiB first／restart Gate都不變，direct evidence不能取代E3。

下列是B.6 local-authority patch當時的歷史phase snapshot；後置B.7 phase block supersede其
「當前」語意，但不回寫此歷史：

```text
P4-E1 = COMPLETE
P4-E2 READ-ONLY DESIGN REVIEW = COMPLETE — PASS; NO SPLIT
P4-E2 IMPLEMENTATION = SUSPENDED IN VERIFIED REPAIRED BACKUP
P4-E2-M1 ROOT CAUSE / REPAIR / THREE-COLD-RUN QUALIFICATION = COMPLETE
P4-C2 READY FIXTURE COMPATIBILITY = BLOCKED AT DIRECT RUNTIME OUTCOME / COUNTER OBSERVATION
P4-E2-M1-D2-A0.1 = STOPPED AT NONPUBLIC ACCESS ROUTE ABSENT
P4-E2-M1-D2-A0.2 = STOPPED AT DIRECT COORDINATE AUTHORITY GAP
P4-E0-B.6 = IMPLEMENTED LOCALLY; COMMIT / PUSH / REMOTE PENDING
FML / PUBLIC NOMINAL ROUTE TECHNICAL REVIEW = BLOCKED UNTIL B.6 COMMIT / PUSH / REMOTE CLOSURE
P4-E3 = BLOCKED
P4-E = INCOMPLETE
```

Local patch只解決A0.1／A0.2揭示的authority boundary；FML／public nominal technical review、
implementation及runtime qualification均是後續獨立工作。

### P4-E0-B.7：COMMIT_READY deadline與final receipt publication authority

B.7只執行documentation-only authority同步；不修改Candidate12 harness／tests，不執行
cold qualification，不建立v3 namespace，也不重選T1。T2 SUPERVISOR-OWNED OBSERVATION仍是
唯一選定的preparation protocol。B.6 direct-evidence JSON與B.7 final qualification receipt分屬
不同authority layer。

#### 唯一deadline與publication contract

~~~text
H = COMMIT_READY_DEADLINE
H bounds COMMIT_READY and LINK_PREINVOKE_ELIGIBLE.
H does not bound the final no-replace link's normal return time.
~~~

H前必須完成formal observation、ps／jcmd normal completion／bounded read／full reap、
process identity、CLEAR／SAME_EXPECTED_DAEMON classification、全部formal fields、immutable
receipt payload、fixed source path／inode與target path、same-filesystem proof、target expected absent、sealed
link arguments及COMMIT_READY seal。Ready後不可還有parse、classification、hash、
serialization、fsync、rename、readback或reconciliation。Late ready固定
COMMIT_READY_DEADLINE_EXCEEDED、link attempts 0、receipt 0。

T2 4.100秒branch-aware ledger只涵蓋COMMIT_READY preparation；不得把link latency算成
bounded stage或修改H = 5.750秒、ps、jcmd、identity或three CLEAR。

Final syscall前必須執行一次相鄰LINK_PREINVOKE_ELIGIBLE check：

~~~text
commit_ready == true
current_monotonic_time <= H
link_attempts == 0
target_not_previously_used
session_not_aborted
~~~

Check與syscall之間不得有callback、logging、file read、allocation-heavy work、retry、yield、
second process、wrapper／DONE或另一clock branch。Late check固定LINK_NOT_INVOKED。

Ready後只允許same supervisor直接執行一次same-filesystem、atomic、no-replace link／linkat；
copy、replace／overwrite、rename-overwrite、retry、fallback API、wrapper、ln subprocess、
DONE carrier及second publication path全部禁止。Exact macOS／JDK route尚待B.7 closure後的
read-only technical review。

Ready與eligibility不晚於H時，link在H前或H後normal return都合法。唯一success coordinate是
same-process normal return from that exact authorized link = RECEIPT_COMMITTED；不得以
post-return clock把cross-H success改判failure。報告必須分開commit_ready_by_h、
link_preinvoke_eligible_by_h、receipt_link_normal_return與receipt_link_crossed_h，
crossed_h=true可PASS。

EEXIST／ENOENT／EXDEV／EACCES／EPERM／EIO／ENOSPC／EINTR、RuntimeException或其他
直接non-success是RECEIPT_COMMIT_FAILED；Error／OOME保留原identity，它們與process termination、external timeout、
unknown completion或未觀察到same-process normal return是RECEIPT_COMMIT_UNADJUDICATED。
不得retry、alternate path、target readback／stat／exists／open、reconciliation、backfill或
cleanup-to-success。

Receipt path existence不是restart authority。禁止restart scan與orphan adoption；可能orphan只留在
該失敗attempt的unique namespace，namespace永不重用。Final link是最後persistent mutation；
normal return後只允許nonallocating local status propagation、normal return／exit，禁止write、
link／rename、receipt／manifest mutation、readback、checksum、fsync claim、reconciliation、
callback、external process及retry。

Authority不宣稱fsync、crash durability、cross-file transaction、reboot recovery、journal或
directory persistence，只宣稱live process觀察到no-replace publication normal return。

#### Preserved Gate與未來測試

H numeric value、settling deadline、poll interval、maximum observations、three CLEAR、ps／jcmd、
PID／birth identity、client／worker／foreign checks、collector、heap、fork、test order、
Gradle command、qualification counts、fixture、P4-E2／P4-C2、R2Q與P4-E3全部不變。

B.7 closure後仍不得直接實作。下一輪read-only review必須證明exact same-process
macOS／JDK API、same filesystem、sealed source、unique target、adjacent check、one invocation、
no retry／readback／post-link mutation、termination UNADJUDICATED、orphan不認領、T2
4.100秒ready ledger、unbounded link latency如實報告及無durability overclaim。

Future tests至少包括ready-before-H且return before／after H都PASS、late ready／preinvoke零link、
single EEXIST／EXDEV／EINTR、RuntimeException、Error／OOME original-identity propagation、
mid-link termination、
orphan不復用、unique namespace、zero readback、exact-one link、zero wrapper／DONE／ln、
zero post-link mutation、no post-clock rejection、ready後payload byte-identical及process
strictness不變。

Candidate direct-observation cold qualification v1／v2與既有P4-E2-M1 memory-repair
three-cold-run campaign不同；後者歷史COMPLETE不變。Focused-validated source／test不代表
P4-E2 production implementation已恢復或完成。

~~~text
P4-E0-B.6
= COMPLETE

P4-E2-M1-D2-A0.3
= COMPLETE

P4-E2-M1-D2 source/test implementation
= LOCALLY FOCUSED-VALIDATED

cold qualification v1
= FAILED; 0/3

cold qualification v2
= FAILED; 0/3

Candidate11
= FAILED AT TRANSACTION WATCHDOG BUDGET GAP

Candidate12 protocol review
= STOPPED AT NO UNIQUE STRICT PROTOCOL

Candidate12 prior implementation feasibility
= STOPPED AT FINAL LINK DEADLINE IMPOSSIBILITY

P4-E0-B.7 COMMIT_READY / final receipt publication authority
= IMPLEMENTED LOCALLY; COMMIT / PUSH / REMOTE PENDING

Candidate12 protocol
= T2 SUPERVISOR-OWNED OBSERVATION
  REMAINS THE ONLY SELECTED PREPARATION PROTOCOL

Candidate12 final receipt boundary
= REQUIRES B.7 CLOSURE
  AND A NEW READ-ONLY TECHNICAL REVIEW

cold qualification v3
= NOT STARTED

P4-C2 and later Gates
= BLOCKED

P4-E3
= BLOCKED

P4-E
= INCOMPLETE
~~~

不得宣告Candidate12 READY／implementation PASS、cold v3 OPEN、P4-C2 OPEN、E2 COMPLETE或
E3 OPEN；本輪不得建立Candidate12 implementation或cold-requal-v3 namespace。

### P4-E0-B.8 P4-E2 release-qualification simplification amendment
<!-- P4_E0_B8_QUALIFICATION_SIMPLIFICATION_COMMON_BEGIN -->

本節是較晚且限於P4-E2 release qualification的scoped amendment，也是本次單一
documentation commit內的governance／phase closure。它不改寫上方歷史B.7／A0.4內容，
不改production Java、資料真相、Store／Attachment／journal、reconciliation、reclaim或
玩家資料安全契約。若本節與本檔較早的Candidate／receipt／cold-v3前瞻文字衝突，本節是
active release policy；較早文字只保留為immutable historical research snapshot。

### Verification recursion limit

```text
VERIFICATION_RECURSION_LIMIT = ONE LEVEL

allowed:
product implementation
-> direct tests / qualification Gates

disallowed:
product
-> verifier
-> verifier-authority protocol
-> receipt publication authority
-> receipt numeric qualification
```

若qualification工具自身要求新的多階段authority、generation／transaction protocol、
persistent receipt、filesystem publication protocol、第二層meta-verifier或另一份codex-spec
amendment，預設處置固定為`SIMPLIFY_OR_REPLACE_VERIFIER`，不是繼續發展驗證器。

### Discontinued non-product receipt track

```text
Candidate10
Candidate11
Candidate12
COMMIT_READY receipt
three-CLEAR receipt authority
PyDLL linkat publication
P4-E0-B.8-R numeric qualification
= DISCONTINUED AS OVER-COMPLEX
  NON-PRODUCT VERIFICATION INFRASTRUCTURE
```

這些工具不進production JAR、不在Minecraft server runtime執行、不保存玩家資料，也不參與
Store／Attachment／journal truth、revision audit、reconciliation或reclaim。既有evidence仍是
immutable historical research；不得刪除、重寫或重新seal，但未來工作不再重驗全部Candidate
manifest、不維護其phase lineage、不建立Candidate13，也不繼續B.8-R2／B.8-P。

```text
P4-E0-B.7
= COMPLETE AS HISTORICAL TECHNICAL AUTHORITY;
  SUPERSEDED FOR RELEASE QUALIFICATION

P4-E2-M1-D2-C12-A0.4
= COMPLETE AS HISTORICAL TECHNICAL RESEARCH;
  SUPERSEDED FOR RELEASE QUALIFICATION
```

因此H=5.750s、three CLEAR、COMMIT_READY、LINK_PREINVOKE_ELIGIBLE、atomic linkat receipt、
same-process receipt publication與session budget ledger都不再是P4-E2 release requirements，
也不得套回下述簡化qualification。此定位不撤銷任何歷史commit或技術研究結論。

### Product implementation and release qualification are separate

```text
P4-E2 product implementation
= LOCALLY IMPLEMENTED IN VERIFIED REPAIRED BACKUP;
  FOCUSED TESTS PASS;
  NOT YET COMMITTED TO MAIN

P4-E2 product code completion
= NOT INVALIDATED BY QUALIFICATION-HARNESS FAILURE

P4-E2 release qualification
= PENDING SIMPLIFIED GATE

P4-E2 production wiring
= NOT ACTIVE UNTIL QUALIFICATION AND COMMIT
```

本文件在B.8之前任何稱P4-E2 implementation為`SUSPENDED`或`NOT STARTED`的phase narration，
一律是pre-B.8 historical coordinate，不是現行狀態；本節與下方active phase block明文
supersede該狀態，但不改寫或刪除其歷史文字。

Qualification infrastructure failure不得把implementation退回`NOT STARTED`；產品test失敗仍可
阻擋code completion。External collector或cleanup失敗只屬qualification infrastructure failure。
Release qualification完成前不得啟用production wiring或進入P4-E3 reclaim composition。

### SIMPLIFIED_COLD_FULL_SUITE_GATE

新的最低充分release Gate是在repaired-E2／fixture qualification source上，依序建立
`simplified-cold-1`、`simplified-cold-2`、`simplified-cold-3`。歷史cold-final／cold-requal
run不得補足；namespace不得使用Candidate或receipt命名。每次固定執行：

```text
./gradlew --stop
./gradlew test --rerun-tasks --console=plain
./gradlew --stop
```

每次結束後只做普通有界cleanup check：最多15秒、每500ms檢查一次該run已知test worker與
Gradle client是否退出；daemon退出只是diagnostic／cleanup requirement。此流程不建立formal
process authority、three CLEAR、H deadline、atomic receipt、DONE、wrapper、generation或新authority
patch。Cleanup check失敗時該run為`QUALIFICATION_INFRA_FAILURE`，P4-E2 product implementation
state不變，且不得因此建立Candidate13。

每次run必須同時滿足：

```text
Gradle rc                         = 0
BUILD SUCCESSFUL                  = exactly once
:test execution                   = exactly once
JUnit suites                      = 199
unique tests                      = 1458
failures / errors / skipped       = 0 / 0 / 0
duplicate tests                   = 0
OOME markers                      = 0
memory-scan infrastructure errors = 0
test worker count                 = 1
effective test worker heap        = 512 MiB unchanged
effective daemon heap             = 1 GiB unchanged
effective maxParallelForks        = 1
effective forkEvery               = 0
effective JUnit parallel          = off
```

這些effective settings須由ordinary run evidence觀察並保持不變；不宣稱目前repository已用額外
literal配置釘死Gradle defaults。不得exclude／split tests、增加heap、改fork、retry、ignore
failure或改class order。JUnit XML canonical inventory是suite／test／duplicate count authority；
普通console log本身不足以取代它。

每次只保存command、runtime/environment identity（包含被測source／worktree HEAD與tree）、
effective heap/fork settings、exit code、JUnit
counts、OOME count、canonical test inventory與SHA、必要concise log及Gate summary；不建立sealed
receipt。只有三次各自PASS且三份canonical inventories byte-identical，才可宣告
`SIMPLIFIED_COLD_FULL_SUITE_GATE = 3/3 PASS`。

本次docs-only amendment的clean-base local regression仍是191 suites／1,387 tests／0 failure／
error／skipped；它不是未來repaired-E2／fixture cold Gate的199／1,458 qualification count。

### Direct product evidence and later product Gates

Simplification保留P4-C2 READY first／restart actual login chain的direct runtime evidence：

```text
P4-D outcome                 = NoPending
P4-E2 result                 = NoChanges
accepted index invalidations = 0
E2-owned setData attempts    = 0
E2-owned setData successes   = 0
```

這些coordinate保護READY player state、P4-C fixture compatibility、E2 no-equivalent-publication與
E1 index stability；只需由GameTest／probe assertion、normal test output與fixed-heap Gate log直接
證明，不需要filesystem receipt authority。
較早B.6的canonical JSON、atomic publication或sealed filesystem artifact文字，僅在其歷史研究
與implementation-test設計範圍保留；作為active release-evidence transport的要求由本B.8
supersede。B.6 actual-object／result／invalidation／JVM-call coordinates與上述zero-delta產品
assertions仍是mandatory direct product evidence，不得一併移除或弱化。

只有simplified cold 3/3 PASS後，才依序執行：P4-C2 fixed-heap READY first／restart、normal
GameTest 12/12、dedicated-server smoke、P4-A3 relevant Gate、P4-B fixed-heap／restart、P4-C
fixed-heap Attachment、P4-D fixed-heap crash／restart、portable verifier matrix、warning compilation、
JAR isolation與javap／API Gates。任何一項都不得再建立formal receipt protocol。

P4-C／P4-D／P4-E的資料安全、fixed-heap、restart、journal、reconciliation與reclaim obligations
維持不變。Gate等級只控制verification成本：A級persistence／journal／reconciliation／reclaim
仍須strict product design與fixed-heap／restart；B級runtime core／integration使用targeted、full unit、
GameTest且heavy matrix只在major closure；C級content／UI／Trigger／Action／材料／配方使用build、
targeted unit／GameTest並在release前整體回歸。Repository-external CI harness internals不是產品authority。
本節的「P4-C2 and later Gates」只指尚待執行的release product Gate，不回退已完成的P4-C2
code phase或其既有產品證據。

### Implementation closure, evidence minimization, and stop policy

Qualification通過後，在fresh worktree整合verified repaired E2 implementation、M1 repair與8-path
fixture compatibility，重跑必要產品Gates，建立一個implementation commit，取得唯一exact-SHA
remote PASS，再建立一次two-ledger phase closure。不再建立qualification／harness／receipt
authority／numeric qualification closure。

Ordinary qualification evidence限於command、runtime identity、heap／fork settings、exit code、test
counts、OOME count、canonical inventory SHA、required log與Gate summary；不要求manifest-of-manifest、
immutable nested bundle、filesystem receipt、generation、transaction authority或multiple closure commits。
完整sealed evidence只保留給fixed-heap formal study、migration、irreversible reclaim與release-candidate
final qualification。

只有產品安全或regression問題會停止implementation：玩家資料可能損壞／誤刪、Store owner／revision
validation錯誤、reconciliation非原子replacement、production regression失敗、required fixed-heap
OOME／timeout、public API暴露禁止raw state，或E3 reclaim completeness無法證明。Daemon慢退出、
cleanup script錯誤、非必要collector欄位缺失、原始證據仍完整時的manifest格式問題、receipt時序未證、
branch protection未知或repository-external diagnostic不精確，只標為
`QUALIFICATION_INFRA_FAILURE`、`KNOWN_LIMITATION`或`RELEASE_EVIDENCE_PENDING`，不把產品實作退回
`NOT STARTED`。

若qualification工具需要第二層meta-verifier、新authority amendment、persistent receipt／generation，
工具量接近被驗證功能，或連續兩次只修工具而未增加產品覆蓋率，固定：

```text
STOP VERIFICATION RECURSION
-> simplify or replace harness
-> preserve product evidence
-> do not create a new Candidate generation
```

### Single-commit conditional closure and active phase

本次單一commit同時是authority amendment、governance closure與phase-ledger update；不得再建立第二個
documentation closure commit。本次local Gate僅為Markdown consistency、official R2Q exact-six checksum
重驗，以及clean detached base上的：

```text
./gradlew verifyPlatformBaseline compileJava test --console=plain
```

Closure-time local result是`PASS`：Gradle rc 0、`BUILD SUCCESSFUL`，detached clean-base JUnit XML
為191 suites／1,387 tests／1,387 testcase nodes／0 failures／0 errors／0 skipped。Official R2Q
只重驗既有exact-six checksum，5/5 payload PASS；其`SHA256SUMS.txt` SHA-256固定為
`cb296db6f2aae653a0db2af25b20df4a5107e90096eff9766e40fa2798f24da9`，未重跑R2Q study。

它不執行GameTest、dedicated、fixed-heap、cold qualification、Candidate harness或R2Q study。

```text
P4-E0-B.8
P4-E2 qualification simplification amendment
= COMPLETE UPON THIS COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1 REMOTE GATE PASS

Candidate10–12 formal receipt track
= DISCONTINUED AS OVER-COMPLEX
  NON-PRODUCT VERIFICATION INFRASTRUCTURE

P4-E2 product implementation
= LOCALLY IMPLEMENTED IN VERIFIED REPAIRED BACKUP;
  PRODUCT CODE COMPLETION NOT INVALIDATED
  BY HARNESS FAILURE

P4-E2 simplified release qualification
= READY; NOT STARTED

simplified cold qualification
= 0/3

P4-C2 and later Gates
= BLOCKED UNTIL SIMPLIFIED COLD 3/3 PASS

P4-E3
= BLOCKED UNTIL P4-E2 IMPLEMENTATION
  AND RELEASE CLOSURE

P4-E
= INCOMPLETE
```

本節不宣告P4-E2 COMPLETE、simplified cold PASS、P4-C2 OPEN、P4-E3 OPEN或Candidate12
implemented。Branch-protection required-check configuration仍是external governance unknown。
本commit的unique exact-SHA attempt-1 remote `Build`與現行exact five jobs全success後，條件解析為
`P4-E0-B.8 = COMPLETE`；不需也不得再為解析條件改寫文件。

Exact next work item是執行三次新的`SIMPLIFIED_COLD_FULL_SUITE_GATE`；在3/3 PASS前，P4-C2
及later product Gates保持blocked，P4-E3保持blocked，P4-E保持incomplete。
<!-- P4_E0_B8_QUALIFICATION_SIMPLIFICATION_COMMON_END -->

### P4-E1：Read-only bounded audit

- 使用25個獨立checked-`long` inclusive counters；exact max合法，第max+1在指定stream
  checkpoint立即停止並回`INCOMPLETE_AND_CONTINUE`。Disk playerdata只接受
  `DataVersion = IntTag(3955)`；integrated snapshot是platform-post-DFU source，不檢查其
  `DataVersion`，P4-E對兩者的DFU calls都精確為0。Heap-floor唯一判定座標是
  `HotSpotDiagnosticMXBean.getVMOption("MaxHeapSize").getValue()`的strict canonical base-10
  nonnegative `long`。Effective value小於
  `MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES = 1_610_612_736`bytes為
  `Incomplete(HEAP_FLOOR_NOT_MET)`，大於等於為`QUALIFIED_FLOOR_PRESENT`；bean／option／
  value／核准observation無法驗證為`Incomplete(HEAP_FLOOR_UNVERIFIABLE)`，`Error`／OOME不捕捉。
  兩個failure狀態都在journal／directory／source work前short-circuit，startup繼續且所有
  journal observation／directory work／file opens／Attachment admission／raw-root capture／Store
  target audit／reclaim／source mutation為0。`Runtime.maxMemory()`、heap
  usage與pool max／peak只作diagnostic，不得fallback、取min／max或套容差。Floor數值與counting
  coordinates不得從本節自行重寫。
- 同步掃描trusted playerdata directory，驗canonical UUID primary／old pairs、NOFOLLOW／
  fileKey／race／same-channel identity，並依權威matrix選truth。重用P4-B reviewed strict
  one-member gzip primitives與streaming unnamed-Compound scanner，在allocation前執行長度上cap。
- `ONLINE_PLAYER_ATTACHMENT`只是既有`PLAYER_SKILL_ATTACHMENT` family內的source kind，不新增
  closed-inventory family／provider。在server logic thread先由`PlayerList#getPlayers()` live view建立bounded compact online identity
  observation，不保留live view；每項只存exact player／authenticated UUID／server。第2,049個distinct
  UUID使用既有`relevant_records` cap立即失敗，不新增online ceiling；duplicate UUID、wrong server、
  null player fail-fast。每UUID truth precedence固定為`online > integrated > disk`，online勝出時不
  open／decode disk、不project integrated；physical disk entries仍計directory及race witness。
- 沒有online winner時，Integrated source選擇固定四態：profile與loaded snapshot都存在時選
  `INTEGRATED_RUNTIME_SNAPSHOT`並以authenticated profile UUID為owner；profile存在但snapshot
  為null，或兩者都不存在時，回一般disk primary／old matrix；snapshot存在但profile
  不存在時回Global Incomplete `INTEGRATED_OWNER_IDENTITY_UNAVAILABLE`。選snapshot時不重讀
  `level.dat`、不open／decode同UUID disk pair，也不雙重投影roots；該pair實際目錄entry
  仍計`directory_entries`並參與directory race witness，但不計`relevant_records`。
- `relevant_records`統一為每個selected authoritative owner UUID一筆，source kind可為ONLINE、
  INTEGRATED、DISK_PRIMARY、DISK_OLD；maximum 2,048 inclusive，第2,049筆立即capacity failure。
  Online Missing／Ready／Quarantined都先+1，integrated選中時+1；online／integrated勝出時同UUID
  disk pair +0，counter不因zero roots或quarantine退還。Snapshot自身不增`directory_entries`。它對
  `compressed_bytes_per_file` complete skip，對`compressed_bytes_total`貢獻`+0`；其他
  per-source／aggregate structural counters與`decompressed_bytes_per_file`／`_total`均適用。
  計量為per-selected-source的as-if unnamed-Compound logical width：1-byte root type＋
  2-byte empty modified-UTF root-name length＋complete payload＋root EndTag。單次checked、
  read-only traversal不建立copy或等長byte array，不用SNBT／`String.length()`；modified-UTF
  counter只計field name與String payload的encoded payload bytes，而2-byte length prefix只計入
  logical decompressed width。Materialization前的wire duplicates與原始physical bytes不在可觀測邊界。
- Snapshot的`neoforge:attachments/gramarye:player_skills`只能進入P4-C inner
  `PlayerSkillAttachmentPersistenceBridge.load`-equivalent pure admission core，不得呼叫會先
  size-measure／copy的serializer wrapper。Key不存在時admissions與roots都不增；完整
  admission一次完成後`attachment_admissions += 1`，只有Ready可將latest-present與
  equipped以raw order／duplicates投入global `raw_root_claims`；任何quarantine／schema／
  migration／Draft hydrate或其他rejection都是Global Incomplete，不保存partial roots。
- Online全部per-file counters為`NOT_APPLICABLE`，byte／structural aggregates +0，
  `attachment_admissions` +0，Ready actual latest／equipped roots在append前計cap且保留duplicates；
  `NOT_APPLICABLE`不是0-byte file，精確25-row table以第18號§18為準。Online只使用E1-A
  non-installing Missing／Ready／Quarantined observation，不取raw Tag、不重跑serializer／admission、
  tree／size／DataVersion／DFU，不安裝default或`setData`。Missing是zero roots；兩種quarantine均
  `ATTACHMENT_QUARANTINED`。
- Integrated traversal只能在same server logic thread、same pre-login `ServerStarting`
  composition call chain內使用；入口capture exact server、profile UUID、loaded `CompoundTag`
  identity與source-set witnesses；online initial／final witness依下一項獨立保存與驗證，不以
  integrated witness替代。完成count／admission／projection後、Complete candidate
  或reclaim前重取並驗證同一identity與witnesses。失敗回
  `INTEGRATED_OWNER_FRESHNESS_LOST`、丟棄partial roots、reclaim 0；不retry／merge／
  cross-tick retention。Object identity不證明同一alias未被敵對in-place mutation；V0只依賴
  thread confinement、no-yield／no third-party callback與Gramarye operations的pure／non-mutating Gate。
- Global first-failure精確為：programming／null／thread → effective heap → Store installed／Ready →
  journal bootstrap／Ready → inventory coverage → directory enumeration／count → filename／pair metadata →
  bounded online identity → integrated four-state → `online > integrated > disk` arbitration → all-source
  owner UUID natural sort → per-owner relevant checkpoint → source-local observation／admission → player
  roots → journal roots → grouped Store audit → final freshness → result／index。Pairing只是metadata；
  online／integrated winner不得進同UUID disk strict ingress。Owner內latest按SkillId、equipped按slot，
  journal在所有player claims後；不得all-online-first。Integrated分支在source-local stage依序skip
  compressed／gzip → logical width／aggregate → structural counters → skip DataVersion → full P4-C
  admission → admission counter → roots；disk stream precedence不變。
- Initial online witness保存exact server／UUID／player／hasData presence／state identity或exact absence。
  只有原本可Complete且journal claims／Store audit成功後才重取完整online UUID set，驗same player／
  server／presence／state identity；不reproject、不讀disk、不重跑admission、不retry。Drift回
  `ONLINE_SOURCE_FRESHNESS_LOST`、discard claims／capability、index Incomplete、reclaim 0。較早counter／
  source／Attachment／journal／Store／reconciliation terminal failure不得被final freshness覆蓋。
- V0 closed inventory恰為`PLAYER_SKILL_ATTACHMENT`與`PENDING_ATTACHMENT_JOURNAL`；使用
  package-private compile-time inventory、exhaustive no-default switch與exact provider coverage。
  SkillInstance／Marker／Construct／Schedule尚未啟用，Store latest／active pins由P3-D implicit。
- Disk／integrated Player Tag必須走完整P4-C admission；online只觀察existing state且admission +0。
  不建立root-only parser；journal使用P4-D
  projection。Raw latest／equipped／journal claims在dedup前最多65,536、再作grouped exact
  reference／expected-owner Store audit，每distinct SkillId最多一次history lookup。
- Index只memory-only、restart預設Incomplete，不存raw或Store truth。它可保存internal
  `CompleteIndex`／`CompleteIndexWithActiveLease`與同一audited single backing，但不保存public
  Complete permit或`SkillRetentionRootSnapshot.Complete`。E1的player／Store／journal mutation與
  reclaim invocation均為0。
- Index generation唯一owner是一個`SkillRetentionRootAuditService` identity × 一個exact
  `MinecraftServer` object identity的slot；每slot使用memory-only `long` `0..Long.MAX_VALUE`。
  NoEntry對外是Incomplete且沒有published generation，新slot internal baseline為0，first accepted
  audit reservation為1；
  tick、P4-C mutation generation、Store revision、journal generation與SavedData identity都不是本generation。
- 只有accepted global audit attempt與P4-E2 explicit invalidation消耗generation。Audit在null／
  programming、server、logic-thread、active-lease／reentrant checks後，heap與任何source work前reserve
  `g + 1`並立即撤銷舊Complete backing／permit；success、Incomplete、OverLimit、
  ReconciliationRequired、final freshness failure與RuntimeException只在同reserved generation完成，
  Error／OOME原樣傳播且留下同generation non-Complete。E2每個accepted batch只reserve一次並發布
  Incomplete，不reaudit／reproject／snapshot／reclaim；更早fail-fast都不改index／generation。這不是
  第26個counter，也不改既有25維profile。
- Internal state machine至少包含NoEntry、`Incomplete(g)`、`AuditInProgress(g)`、`CompleteIndex(g)`、
  `CompleteIndexWithActiveLease(g)`、`GenerationExhausted(Long.MAX_VALUE)`與Removed；publication只作
  單一完整state replacement，不逐項公開partial index。
- `Long.MAX_VALUE - 1 → Long.MAX_VALUE`合法；current為MAX而audit／E2需要advance時不得
  wrap／saturate／reset，必須清除舊Complete backing／permit，以new state identity發布terminal
  `GenerationExhausted(Long.MAX_VALUE)`／
  `Incomplete(GENERATION_EXHAUSTED)`，source work／roots／Store audit／snapshot／reclaim皆0，startup
  繼續且source data不變；同slot
  重複audit／invalidate idempotent，只有exact server stop的`removeServer`可刪除。Permit consume先
  標used；second use或wrong service／server／thread／tick／state identity／generation只消耗permit與
  清自身authority references，不改index／generation。成功consume
  以同generation／backing開active lease；open／close不增加、active lease阻止audit／E2。B.9後close
  預設在同generation demote至Incomplete；只有exact `Completed(0)`及完整source-unchanged
  零publication證明才回Complete，且兩者都不重發permit。`removeServer`強制清
  lease／backing／permit／slot且不增加generation；新exact server object
  才從baseline 0開始，同一stopped object不得reset，移除後原handoff操作固定拒絕；不使用Cleaner／
  finalizer／background lease timeout。Complete／handoff必須同時綁exact service、server、
  state identity、generation、thread與tick，handoff另綁lease identity；generation相等不充分。

### P4-E2：Login-only reconciliation

- P4-E2是第一個將production `SkillRetentionRootAuditService` instance接入composition的
  phase；runtime長期lifecycle owner唯一固定為既有`SkillDefinitionStoreService`。
  `Gramarye` composition建立exact Store service，每個Store service instance在自身
  lifecycle內建立並以一個`final` field長期持有exactly one audit service，且在任何
  login reconciliation前存在。Production
  constructor callsite必須exactly 1，且只屬於Store lifecycle owner或其exact
  package-private construction helper；不使用static／global registry／service locator／
  lazy-per-login，不由recovery service、E3或任意caller建立或提供identity。
- `SkillSubmissionRecoveryService`仍是sole `PlayerLoggedInEvent` owner。既有handler只能在
  same server logic thread與same synchronous call chain中執行：validate exact current
  `ServerPlayer` → P4-D recovery completes → retain typed recovery outcome → invoke P4-E2
  continuation → return。不增加second listener／yield／future／executor／second event
  dispatch／cross-tick plan／background／periodic／manual reconciliation，E2也不得在recovery前
  觀察或修改Attachment。
- Recovery service只接收一個窄、constructor-injected的E2 reconciliation dependency；
  該dependency在composition時已綁定exact Store-service identity、該Store持有的exact
  audit-service identity與E2所需其他exact dependencies。Handler只呼叫E2-facing operation，
  不直接看audit／index／Store／history／raw state，也不接受arbitrary audit service／
  supplier／generic callback／service locator或reflection；最終Java類名／package／sealed
  capability shape留給B.5 closure後的E2 read-only review。
- P4-E2 closure必須包含active production login wiring。Pure core／test-only caller／只有
  package-private operation卻沒有login composition、manual trigger、next-tick／background work或把實際
  wiring延後至E3均不可宣稱E2 `COMPLETE`。B.5只固定recovery先完成且
  typed outcome傳入E2 dependency；不新增outcome admissibility matrix，也不補完既有
  atomic zero-publication原則之下的grouped validation／prune／generation／invalidation／`setData`
  final ordering。這些都必須在B.5 clean closure HEAD從頭審查。
- 只在該player的P4-D login recovery完成後，觀察一個當下Ready Attachment並對全部
  latest／equipped claims作grouped Store audit。Offline stale／foreign在E1只能defer-to-login，
  disk不變。
- Latest prune只使用P4-C唯一generation arithmetic；equipped prune不改generation；valid
  nonlatest保留，不promote。Generation MAX、identity drift或carrier failure使整批零publication；
  成功最多一次`setData`。不改Store／journal／Draft／editor，不寫offline disk，不reclaim。
- Audit N發現reconciliation的當輪與login prune／save之後都不reclaim；只有restart N+1的
  fresh full audit才可能Complete。

### P4-E3：Lifecycle composition and final Gate

- P4-E3必須重用P4-E2已接入、由exact same `SkillDefinitionStoreService` final field
  持有的exact same audit-service object identity；不建立第二Store service，不建立、
  替換或lazy-create第二audit service，也不補做E2 login wiring或增加listener。Server
  start／stop不替換service owner；per-server slots繼續依exact `MinecraftServer` object
  identity隔離，新server object由同一service instance建立新slot。E3 audit-service
  production constructor delta與login wiring delta均為0。
- 在唯一`ServerStartingEvent`、same logic-thread call chain中固定
  P4-B install → P4-D bootstrap → fresh E1 audit → 若Complete即時建立
  `SkillRetentionRootSnapshot.Complete` 並呼叫
  `SkillDefinitionStoreService.reclaim(server, snapshot)` exactly once。Fresh public Complete permit只屬
  same-call-chain local，不存field／index／callback、不跨tick；internal `CompleteIndex`／active-lease
  state與同一audited backing仍可留在memory-only index，但不保存public permit或snapshot。
- Reclaim `Rejected`不retry，0不dirty，>0沿用P4-B publication-before-dirty；invariant
  failure沿用Unavailable／`setDirty(false)`。B.9只允許exact `Completed(0)`及完整
  source-unchanged零publication證明保留`CompleteIndex(g)`；snapshot non-Complete、
  Rejected、Unavailable、`Completed(>0)`與exception全在同generation demote至
  `Incomplete(g)`。Audit／index／Incomplete／E2不改Store dirty。
- 在既有stop lifecycle中，只以上述exact same service field對exact server呼叫
  `removeServer`；該操作只清該identity-isolated slot，不銷毀、替換或重建service owner。
- 建立production-shaped exact fixed `-Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError`
  first／restart Gate，同時保留4,096 directory entries、2,048 records、25 exact maxima、
  1,024 full P4-C admissions、65,536 accepted raw roots、4,096 journal targets、full Store／carrier、
  grouped audit、index、Complete、filtered carrier與SavedData deep copy。R2Q不取代此Gate，
  也未自然涵蓋integrated runtime alias path；fixture選用integrated startup owner時actual child必須
  執行，未選用時須有reviewed machine-checked structural proof鎖定same-owner disk exclusion、
  snapshot replacement、no dual hydration、no retained compressed／gzip state及no second tree。
  該proof不比較object size也不取代actual selected-envelope child。P4-E0-B.10把Gate固定在sole synchronous
  `ServerStartingEvent` production audit coordinate；online player與selected online owner精確為0，
  Gate仍執行production empty online inventory path且不得偽造startup前player。Online逐object
  domination義務在此座標取消；lifecycle-reachable來源仍須維持relevant 2,048與raw roots 65,536
  exact。E1 online契約不變，新增任何post-login／post-tick／command／background audit caller會
  自動重新開啟online memory qualification。

### Gate

- 25 counters各自exact／MAX+1、其他24維不超限與canonical precedence；effective HotSpot
  `MaxHeapSize`三狀態與nonqualified zero-work；1,536 MiB G1／Parallel／Serial／ZGC
  qualified、locked Temurin/macOS aarch64的1,535 MiB G1 alignment-positive、1,024 MiB G1
  below-floor process controls，以及pure injected floor − 1／floor／floor + 1 comparator；
  Runtime／heap／pool values只作diagnostic；
  directory／relevant exact／+1；filename／primary-old／gzip／NBT／disk `DataVersion`／
  integrated四態、compressed skip／logical width／modified-UTF／alias freshness，以及online
  Missing／Ready／兩種Quarantined、25-counter applicability、admission +0、source exclusion、
  unified UUID order與final freshness完整矩陣。
- P4-C admission classification等價、inventory coverage、journal Available／Unavailable、grouped Store
  audit、raw roots 65,536／65,537 before dedup、offline disk hash不變、recovery-before-E2、
  atomic multiprune／MAX、index invalidation與N／N+1。
- Index generation Gate涵蓋NoEntry／baseline 0／first 1；success、全部normal terminal、
  RuntimeException與Error／OOME各只消耗一次reservation，E2每batch一次；MAX−1→MAX、MAX後terminal
  exhaustion、zero source work／reclaim、old Complete清除與repeated idempotence；programming／
  wrong-thread／reentrant、permit misuse、lease open／close與removeServer皆delta 0。另驗permit misuse
  consumed但index不變、active lease阻止audit／E2、server stop強制清lease、new server slots獨立、
  exact state identity＋generation共同currentness，以及generation不進serialization／SavedData／R2Q。
- Production owner／trigger Gate驗證：audit-service production constructor callsite精確為1、
  exact owner為`SkillDefinitionStoreService`、E2／E3使用same object identity、E3 constructor
  delta為0；sole `PlayerLoggedInEvent` owner精確為`SkillSubmissionRecoveryService`且P4-D
  recovery call先於same-call-chain E2 continuation。另驗no second listener／next-tick／
  background／manual continuation、缺production wiring時E2 closure Gate失敗、wrong injected
  dependency／identity pairing拒絕、repeated login不新建service、server restart不替換owner、
  exact-server slots identity-isolated、E3只重用same instance，且public／protected API不暴露
  audit internal type。
- Direct qualification Gate驗證actual handler-local `RecoveryOutcome`與coordinator-local E2 result的
  exhaustive、無default分類；invalidation actual invocation／normal `Accepted`與E2-bound setData JVM
  callsite前／normal-return後六個exact coordinates。READY first／restart與`NoChanges`、
  `RecoveryChanged`-only、`Changed`、`GenerationExhausted`、publisher state drift、accepted-before-setData
  failure、`Error`／OOME controls全部符合record；session state／binding／single-use／clear、closed
  nonforgeable public nominal、same-session local cells、bounded canonical JSON及unarmed normal path inert
  全部成立。Static／global／reflection／callback／second listener／diagnostic Attachment／SavedData backlink
  必須不存在。FML transport Gate只在locked API technical review證明official exact-instance lifecycle及
  retrieval route後才可加入；找不到即`NO SAFE RECEIVER ROUTE`。
- E3 production fixed-1,536-MiB exact／MAX+1／restart Gate、no OOME／timeout、no chunk load／
  background／cross-tick public Complete permit／production-JAR fixture leakage。

### 禁止

Chunk force、background／periodic／lazy／admin audit、offline writer、DFU、root-only parser、dynamic
provider completeness、persistent index、Incomplete best-effort reclaim、E1／E2 reclaim、重寫P3-D
policy、network或E1／E2／E3合併。Integrated snapshot額外禁止whole-tree copy、byte-array
serialization、`DataVersion` Gate／re-DFU、同UUID disk relevant／roots雙計、跨tick保存、
second checksum traversal、registered serializer wrapper或以identity宣稱可排除敵對in-place mutation。
Online額外禁止raw Tag、serializer／admission、tree／size／DataVersion／DFU、online-first partition、
same-UUID second truth、reprojection、retry／rescan或以final freshness覆蓋較早terminal failure。
若same-call-chain identity recheck不可行，或pure inner P4-C core會修改input且只能靠
serializer／copy／re-encode，停止E1。若E3 exact profile或B.10
lifecycle-reachable startup envelope在1,536MiB OOME／timeout；fixture選用的integrated path未執行；
fixture未選用integrated且structural proof不成立；或sole startup caller／online exact-zero無法鎖定，停止並先修訂P4-0／
第18號authority；不縮fixture／ceilings、拆envelope或自行提heap。

Marker gameplay Attachment、`RuntimePersistentStore`、SkillInstance、Schedule與Construct lifecycle
不由P4-A～E提前建立；未來啟用時必須在同一reviewed change擴充closed inventory、
provider、completeness gate與tests。

## P4 Definition of Done

- P4-A1～A3、P4-B1／B2與P4-C～E各自gate及
  [18號修正案required tests](18_P4持久化與組合修正案.md#21-required-tests)通過。
- `SkillDefinitionStore`只有一份domain truth；Store carrier是derived encoding，P4-B exact pending blob
  是pending transition唯一persistent truth，decoded journal state只是identity-bound operational view；
  player Attachment遵守自己的pointer／Draft truth邊界。
- Existing invalid data不silent empty；SavedData load failure不覆寫primary。Player Attachment
  in-bound malformed保留materialized logical tree；oversize只有第18號核准的canonical marker可
  破壞性取代原representation，restart仍必須Quarantined。
- Store-first ordering、readback-confirmed journal clear與malformed partial availability由P4-D fixed-1-GiB
  combined Gate證明；P4-E另必須以production-shaped fixed-1,536-MiB Gate證明25維bounded
  audit、online／integrated／disk唯一truth、disk／integrated full P4-C admission與online +0 admission、
  closed inventory、memory-only index、N／N+1
  reconciliation與fresh Complete→single controlled reclaim；fixture選用integrated startup owner時actual
  child須執行，未選用時須有reviewed machine-checked structural proof鎖定source shape，且不得作
  object-size比較或取代actual child。Online在sole synchronous `ServerStartingEvent` Gate中
  必須為exact zero且仍執行empty inventory path；future非startup audit caller必須重新資格化online
  memory。不宣稱playerdata／SavedData cross-file
  durable atomicity，也不宣稱1,536 MiB是universal safe minimum。
- P4-E2首次建立且`SkillDefinitionStoreService`以exact-one `final` field長期持有
  production audit service；sole recovery login handler在P4-D recovery後同步呼叫已綁定exact
  Store／audit identity的E2 dependency，且production wiring已納入E2 closure。P4-E3只重用同一
  instance作fresh audit／reclaim／exact-server slot removal，無第二constructor或login listener。
- P4-E direct qualification必須以B.6六個exact coordinates建立READY first／restart evidence，並通過
  negative controls；transport只能是test-armed bounded nonforgeable session與經locked API technical
  review核准的exact-instance receiver。它不新增root-audit counter／runtime authority／persistent truth，
  不改25 counters、heap、R2Q或E3 Gate。

---

# 階段 P5：Internal Event Queue 與 Scheduler

## 目標

建立確定、可預算、不可同步遞迴的事件處理。

## 工作 1：Sequence service

建立單調 long sequence。

定義溢位政策，例如：

- long 溢位視為極端不可達。
- 比較使用 unsigned 或明確 comparator。
- 不用相減轉 int。

## 工作 2：EventQueue

API：

```text
enqueue
pollReady
cancelOwner
sizeByOwner
```

## 工作 3：Scheduler

持久化 schedule 只保存資料，不保存 lambda／method reference。

`ScheduledTaskDefinition` 應使用 type ID + bounded payload 或固定內建 task kind。

## 工作 4：Budget

建立：

```text
ExecutionBudget
BudgetDecision
CircuitBreakReason
```

## 測試

- 排序完全穩定。
- 同 tick Node index 順序。
- 新事件在下一個 queue cycle 執行。
- cancellation idempotent。
- owner missing cancel。
- gameplay overflow 延後。
- visual overflow 可丟棄。
- 熔斷清理 continuation pointer。

---

# 階段 P6：Effect Pipeline 與 Mana Transaction

## 目標

所有玩法修改有單一入口。

## 工作 1：Pipeline interfaces

建立：

```text
EffectRequest
EffectResolver
EffectCommitPlan
EffectCommitStep
EffectCommitResult
EffectRejectReason
CompensationPolicy
```

## 工作 2：第一個 Effect

實作 `DamageEffectRequest`，但先以測試 double 驗證 Resolve／Plan。

## 工作 3：Mana

建立：

```text
ManaAccount
ManaTransactionRequest
ManaTransactionPlan
ManaTransactionResult
ManaReason
```

## 工作 4：Commit failure

模擬：

- target 在 resolve 後死亡。
- 傷害事件被取消。
- 扣魔後 spawn 失敗。
- compensation 只執行一次。

## 驗收

- Action package 無直接 mana mutation。
- Action package 無直接 world mutation。
- pipeline trace 可讀。
- partial success 有明確結果。

---

# 階段 P7：Network Intent 與同步

## 目標

建立最小、受限、可拒絕惡意輸入的網路層。

## 工作 1：Payload registration

使用 NeoForge 1.21.1 官方 payload registration。

建立獨立 payload：

```text
CastIntentPayload
PlayerManaSyncPayload
SkillCooldownSyncPayload
PresentationEventPayload
```

## 工作 2：Bounds

每一個變長欄位必須顯式上限。

瞄準資訊只允許：

- 正規化方向或可驗證的方向。
- optional entity hint ID。
- 不接受任意目標集合。

## 工作 3：Replay

每玩家保存 last accepted client sequence／window。

定義：

- duplicate reject。
- too old reject。
- excessive future sequence reject。
- reconnect reset policy。

## 工作 4：Threading

payload handler 只建立 Intent，切到 server main thread 後執行。

## 測試

- 重播。
- 超速。
- malformed。
- out-of-range target。
- client claims wrong revision。
- dedicated server no client class。

---

# 階段 P8：Presentation 骨架

## 目標

走通表現事件咽喉，但不做大量美術內容。

## 工作 1：Appearance runtime integration

P8 不重新建立 persistence decoder。P3-A 已負責 Appearance storage schema、partial override、Profile tagged 三態、hard numeric clamp、Unparsed／Rejected isolation、raw snapshot 與 hard proxy bounds；P3-B 已負責 bounded diagnostics、clamp／quarantine warnings、profile lookup 與 runtime projection。

P8 只將這些結果接入 Profile registry、datapack instances、PresentationEvent、network、client budget 與 render fallback。

## 工作 2：Profile type registry

Java 註冊型別。

## 工作 3：Datapack profile instances

使用 NeoForge 1.21.1 正確 datapack registry API。

若 Profile 需要同步給 client：

- 提供 network codec。
- 驗證 dedicated server 與 client registry 一致。
- 缺 client resource asset 時 client fallback。

## 工作 4：PresentationEventPayload

只傳：

- event kind
- profile IDs
- bounded override
- position/direction
- seed
- sequence

## 工作 5：Budget

客戶端與伺服器皆可降級，但伺服器負責 payload event budget，客戶端負責實際粒子密度偏好。

## 驗收

- CAST_RELEASE 到 client。
- HIT 到 tracking clients。
- 缺 Profile fallback。
- 壞 Appearance 不阻止施放。
- 關粒子仍造成傷害。
- 不逐粒子同步。

---

# 階段 P9：垂直切片 1A

## 目標

完成第一個可玩的硬編碼技能。

## 技能

```text
Node 1
Trigger: active_cast
Action: spawn_projectile

Node 2
Trigger: effect_hit(Node 1, include derived)
Action: damage
```

## 實作順序

1. `CastIntentPayload`.
2. server validation。
3. 建立 SkillInstance。
4. 解析固定 revision。
5. active cast internal event。
6. Node dispatcher。
7. SpawnProjectileRequest。
8. Commit 建立自訂投射物 Entity。
9. Entity 保存 SourceContext／EffectState 的必要資料。
10. 命中轉 EffectHitEvent。
11. Node 2 matcher。
12. DamageEffectRequest。
13. 原版 DamageSource。
14. Presentation events。
15. instance cleanup。

## 投射物注意事項

- Entity 持久資料不保存 live object。
- client 只同步渲染必要 entity data。
- 命中判定以 server 為準。
- 防止命中事件重複提交。
- unload/reload 後 source reference 可解析或明確失效。
- life time 有硬上限。

## Definition of Done

- client + integrated server 成功。
- dedicated server 成功。
- 兩個 client 看到合理顯示。
- damage 一次。
- trace 完整。
- 重啟後沒有 ghost SkillInstance。
- 視覺失敗不影響 damage。

---

# 階段 P10：資料驅動 1B

## 目標

把硬編碼技能換成 JSON／Codec／Registry。

## 工作

1. 建立內建 example skill JSON。
2. Data reload 或定義提交載入。
3. 已知 Trigger／Action envelope。
4. semantic validation。
5. unknown proxy。
6. migration test。
7. `/skill validate`.

## 驗收

修改 JSON 參數後，不修改 Java 即可改變受允許的資料。

不得讓 datapack 直接建立玩家 revision；資料包範本與玩家提交 revision 的責任需分清：

- datapack：提供模板／Profile／系統內容。
- SkillDefinitionStore：保存玩家提交的 `SkillDocument` revision。

---

# 階段 P11：持久化與多人 1C

## 目標

完成真正的長期世界與雙人環境。

## 測試情境

1. 玩家 A 提交技能但未施放，重啟後仍在。
2. A 施放後登出，延遲效果仍解析固定 revision。
3. A 編輯新 revision，舊投射物仍使用舊版。
4. A 冷卻中重登，不能洗冷卻。
5. 兩名玩家同時施放，不共享 sequence／instance。
6. 惡意客戶端重播 CastIntent。
7. client profile 缺失，只 fallback。
8. dedicated server 不載 client renderer。

---

# 階段 P12：來源繼承

## 目標

支援原始／衍生效果家族。

## 工作

- `LineageState` 有界。
- `DerivedKind`.
- EffectState derive methods。
- Trigger scope：
  - original only
  - include derived
- source family matching。
- 代數與 visited set hard cap。

## 測試

- derived 不繼承已發生 event。
- derived 繼承未來 Trigger。
- EffectState collection 不共享。
- original-only 正確排除衍生。

---

# 階段 P13：多段施放與冷卻

## 工作

1. 非冷卻建立新 instance。
2. 冷卻中只推進 continuation instance。
3. stage 存在 SkillInstance。
4. pointer 存玩家 Attachment。
5. pointer dead prune。
6. 最後階段／熔斷清除 pointer。
7. 冷卻不因 pointer 清除而消失。
8. 同技能多 instance 預設允許。

## 測試

- 第一次按鍵。
- 冷卻第一次接續。
- 第二次接續。
- 來源失效。
- 魔力不足是否消耗 stage，依 policy。
- logout/reconnect。
- cooldown end。

---

# 階段 P14：Marker

## 原則

- Living/Construct anchor Marker 本體在 Attachment。
- Position Marker 在 Store。
- 中央只保存 index。
- 觸發時扣魔。
- Marker-triggered chain 禁止新增／刷新／延長／複製／轉移 Marker。
- 有 duration、max triggers、trigger cooldown。

## 最小垂直案例

```text
投射物命中
→ 附加 8 秒標記
→ 標記目標死亡
→ 造成一次後續效果
```

---

# 階段 P15：分裂／連鎖／重複

## 分裂

- 來源依賴 Action。
- 最大代數。
- 最低威力。
- 最大總產物。
- derived inheritance。

## 連鎖

- 最大次數。
- target search radius。
- visited set。
- repeat target policy。

## 重複

- schedule owner。
- max count。
- interval。
- deadline。
- cooldown end 不自動取消已排程效果。

## 測試重點

- 組合爆炸量。
- circuit breaker。
- mana transfer 不可被複製。
- deterministic ordering。

---

# 階段 P16：負荷、魔耗、蓄力、公式

這是內容／數值層，骨架只使用服務介面：

```text
SkillLoadCalculator
ManaCostCalculator
CooldownCalculator
ChargeCapacityCalculator
```

公式可替換，不改資料真相或執行管線。

要求：

- 輸入與輸出有界。
- fixed-point 優先。
- 不允許 NaN／Infinity。
- calculator version 可記錄於 trace。
- 公式變更不修改舊 `SkillDocument` revision 的資料語意，或必須有明確版本政策。

---

# 階段 P17：簡單 Construct

第一版：

- stationary
- follow caster
- straight movement
- direct command
- duration
- destroyed event

Construct Entity 為本體真相，中央只有 index。

---

# 階段 P18：群體造物

使用：

```text
one controller entity
+ client visual instances
+ aggregate server collision
```

禁止每片花瓣一個完整 server Entity。

---

# 階段 P19：表現內容與技能編輯器

先做：

- 少量 Profile。
- 顏色選擇。
- 預覽。
- bounded parameters。
- server-side validation。

最後才做完整 Node editor。

編輯器只能產生 draft；提交經P4-D composition facade
`SkillDefinitionSubmissionService`串接authenticated authority、P3-C prepare、persistence
preflight、P3-D commit與Attachment transition。

---

# 每階段共通驗收清單

- [ ] 編譯通過。
- [ ] 純 Java 測試通過。
- [ ] GameTest／專用伺服器測試按階段更新。
- [ ] 無第二持久化真相。
- [ ] 無 Action 直接修改世界。
- [ ] 無直接修改 mana。
- [ ] 無 client authority。
- [ ] 所有 collection 有硬上限。
- [ ] 新 schema 有 migration／版本決策。
- [ ] 新 payload 有 bounds、version、rate limit。
- [ ] 新持久化 mutation 有 dirty handling。
- [ ] Existing malformed persistence不等同missing；在第18號totality boundary內已交付serializer
      body的player Attachment Tag形成PreservedRaw／OversizeMarker，其他load failure不安裝
      partial／empty truth。
- [ ] 一般可失敗encode與byte-capacity checks在truth mutation前完成，save callback只寫
      prebuilt carrier。
- [ ] Cross-location update有明確ordering、bounded recovery journal與reconciliation；不宣稱
      fsync或durable atomic。
- [ ] Persistent roots只有在第18號§18的closed inventory、25 counters、disk exact
      `DataVersion`／zero DFU、online > integrated > disk唯一truth、online existing-state observation／
      admission +0／final freshness、integrated logical width／pure admission／identity freshness、effective
      HotSpot `MaxHeapSize` observation為`QUALIFIED_FLOOR_PRESENT`、
      disk／integrated full P4-C／journal／Store audit全部通過時才可
      Complete；E1／E2／reconciliation當輪皆reclaim 0，只有E3 same-ServerStarting call chain
      可使用fresh Complete。P4-E1已closure，P4-E2 design review為`COMPLETE — PASS; NO SPLIT`，
      product implementation已在verified repaired backup本地完成且focused tests PASS，但尚未
      commit至main；A0.1／A0.2是歷史Stop，
      B.6與A0.3已`COMPLETE`，source／test implementation為`LOCALLY FOCUSED-VALIDATED`。
      B.7／A0.4保留為historical research，B.8已停止Candidate10–12 formal receipt track；
      direct evidence只需ordinary assertion／output／fixed-heap log，不要求canonical JSON、atomic
      publication或filesystem receipt。E2首次接入且
      Store service exact-one-final owns production audit service；sole recovery login handler在P4-D
      recovery後同步呼叫exact-identity-bound E2 dependency，active wiring為E2 closure條件；
      E3只重用same instance作fresh audit／reclaim／stop removal，constructor／login delta 0；
      exact 1,536-MiB production Gate未通過前P4-E不完成。
- [ ] 新生命週期有清理與 idempotence。
- [ ] `/skill trace` 可解釋失敗。
- [ ] dedicated server 無 client class。
- [ ] 文件與 ADR 更新。


## P4-E0-B.9 post-reclaim index terminal authority
<!-- P4_E0_B9_POST_RECLAIM_INDEX_TERMINAL_COMMON_BEGIN -->

This synchronized block is the scoped P4-E0-B.9 authority in the codex-spec documents and its
decision／phase index in the architecture ledgers. Within active-lease post-reclaim terminal
finalization only, B.9 supersedes B.4's unconditional close-to-`CompleteIndex` transition. All
other B.4 owner, generation, permit, lease-blocking, exhaustion, and `removeServer` rules remain
unchanged. Source precedence, P4-E2 reconciliation, reclaim atomicity, and the existing dirty／save
contract also remain unchanged. Earlier B.8 and P4-E2 closure blocks remain immutable historical
evidence; their phase narration is superseded by the current phase block below.

### Unique terminal state machine

```text
CompleteIndex(g)
-> consume valid Complete
-> CompleteIndexWithActiveLease(g)
   = ActiveLease(g, default = DEMOTE)
   [default terminal = FAIL_CLOSED_TO_INCOMPLETE]
-> close with exact source-unchanged proof
-> CompleteIndex(g)

CompleteIndex(g)
-> consume valid Complete
-> CompleteIndexWithActiveLease(g)
   = ActiveLease(g, default = DEMOTE)
   [default terminal = FAIL_CLOSED_TO_INCOMPLETE]
-> every other terminal
-> Incomplete(g)
```

Same-generation demotion is authority revocation. It is not a new audit reservation, is not a
P4-E2 invalidation, and consumes no generation. Post-reclaim demotion must not use
`invalidateForReconciliation`, `removeServer`, a second audit, a second snapshot, or a second
reclaim.

### Terminal mapping

| E3 terminal | Store source determination | Index terminal | Generation |
|---|---|---|---:|
| Snapshot non-Complete | reclaim did not run, but the Complete authority chain failed | `Incomplete(g)` | unchanged |
| Snapshot RuntimeException | no source result capable of retaining authority formed | `Incomplete(g)` | unchanged |
| Snapshot Error/OOME | no successful source-state claim is permitted | `Incomplete(g)` | unchanged |
| Reclaim `Rejected` | startup reclaim authority is not retained | `Incomplete(g)` | unchanged |
| Reclaim filter／operation `Unavailable` | complete proof that the source remained unchanged is absent | `Incomplete(g)` | unchanged |
| Reclaim `Completed(0)` | exact proof that the Store source remained unchanged | `CompleteIndex(g)` | unchanged |
| Reclaim `Completed(>0)` | Store source changed | `Incomplete(g)` | unchanged |
| Reclaim RuntimeException | Store may be unchanged or partially changed; authority is not retained | `Incomplete(g)` | unchanged |
| Reclaim Error/OOME | Store may be unchanged or partially changed; authority is not retained | `Incomplete(g)` | unchanged |

Only exact reclaim `Completed(0)` may retain `CompleteIndex(g)`. `Rejected`, `Unavailable`,
and exception paths must not infer retained completeness merely because the Store might be
unchanged.

### Exact `Completed(0)` source-unchanged contract

```text
removed revisions                  = 0
replacement histories publication = 0
new Store Ready state              = 0
new carrier publication            = 0
Store source identity change       = 0
dirty delta                        = 0
save request                       = 0
```

Only when every coordinate is zero may the active lease be marked `SOURCE_UNCHANGED`. Removed
count alone is insufficient. If zero removal still publishes an equivalent replacement, a new
carrier, a new Ready identity, or dirty state, `Completed(0)` must demote to `Incomplete(g)` and
the later P4-E3 review must stop or require a product correction.

### Outcome-aware finalization

Before publishing the active lease, its owner prepares the same-generation Complete return state,
the same-generation Incomplete fail-closed state, and all terminal bookkeeping. The active handoff
opens with `Incomplete(g)` selected by default. Only after snapshot and reclaim both succeed, and
only on the reviewed exact `Completed(0)` branch, may it execute one allocation-free internal
mark-source-unchanged operation.

The mark seam is package-private or private, exact owner／server／thread／state-bound, single-use,
nonforgeable, and allocation-free. Wrong owner, thread, or state and a second mark fail fast. It
accepts no generic boolean, caller-selected policy, raw reclaim result, public terminal token,
callback, visitor, `Supplier`, `Object`, or reflection route. B.9 fixes the semantics, not the
future Java method name or exact internal type shape; that shape remains for a later authorized
P4-E3 review. The mark is the final non-cleanup action after the exact `Completed(0)` proof; no
snapshot, reclaim, or other fallible source work may occur between it and close. Any Throwable
pending before terminal publication takes precedence and allocation-free revokes／ignores the mark.

Close is allocation-free and performs one terminal publication. A valid source-unchanged mark
installs `CompleteIndex(g)`; every other terminal installs `Incomplete(g)`. In both cases close
clears the active lease, leaves the consumed public Complete permit consumed, issues no new public
`Complete`, and grants no same-session second reclaim. This allocation-free requirement applies
to terminal bookkeeping and cleanup, not to the already bounded snapshot or reclaim body.

### Complete and Incomplete terminals

Exact `Completed(0)` may install `CompleteIndex(g)` with generation `g`. The same audited root
backing and indexed source metadata may remain only because the full zero-publication contract
proves source identity unchanged. The lease is cleared, the public permit remains consumed, new
public Complete issuance is zero, and same-session second reclaim authority is zero.

Every other active-lease terminal installs `Incomplete(g)` with generation delta zero. It clears
root backing, indexed source metadata, active lease, and Complete-permit authority. New Complete
publication and reclaim authority are zero. Same-session audit, snapshot, and reclaim retry are all
zero. This is same-generation authority demotion, not accepted invalidation or a new audit.

### Generation and exhaustion

Generation remains a memory-only `long` over `0..Long.MAX_VALUE` with baseline `0`. Below MAX,
only a successfully accepted global-audit reservation or P4-E2 explicit invalidation advances it.
E3 same-generation demotion does not advance it, so `Incomplete(Long.MAX_VALUE)` is a legal
terminal and demotion itself must not return generation exhaustion. Only a later audit or P4-E2
advance request made while current generation is `Long.MAX_VALUE`—not an accepted operation—
installs `GenerationExhausted(MAX)` with generation delta, source work, root capture, snapshot,
and reclaim all zero. Generation never wraps, saturates, or resets.

### RuntimeException, Error, and OOME cleanup

RuntimeException follows the matrix and closes fail-closed at `Incomplete(g)`. Error／OOME is not
caught as success and is not converted to a bounded success result. If control reaches `finally`
with any Throwable pending before terminal publication, that Throwable takes precedence over any
source-unchanged mark: the allocation-free close installs the prebuilt `Incomplete(g)` state,
clears backing／metadata／lease／permit authority, and rethrows the original Throwable identity
without replacement or suppression. On the normal exact `Completed(0)` path no Throwable is
pending; the final valid mark is immediately followed by the allocation-free, nonthrowing
`CompleteIndex(g)` close. No Throwable, message, or stack is stored in the index, Store, or
persistent state.

This terminal rule claims neither Store rollback nor absence of partial mutation. If the JVM or
process terminates before cleanup, the memory-only index is not durable truth; restart begins from
a new server object's baseline and performs a fresh audit.

### Reclaim mutation and dirty boundary

The index terminal does not redefine existing reclaim atomicity:

```text
Completed(0):
  Store source mutation = 0
  dirty delta = 0
  save request = 0

Completed(>0):
  Store source mutation = yes
  index = Incomplete(g)
  dirty/save = existing reclaim contract

Rejected / Unavailable / RuntimeException / Error / OOME:
  index = Incomplete(g)
  Store dirty/partial mutation = existing actual result and product contract
```

No index terminal proves rollback, absence of a primary write, or absence of partial mutation.
Later P4-E3 review must still independently confirm that the existing reclaim API meets the
product's fail-closed and once-only requirements.

### ServerStopped precedence

Exact-server `removeServer` retains B.4 precedence. If server stop occurs while a lease is active,
it force-clears the lease, backing, metadata, and permit authority and removes the exact server
slot. It increments no generation and performs no snapshot, reclaim, or Store mutation. Slot
removal wins over either `CompleteIndex(g)` or `Incomplete(g)` publication; close must not recreate
the removed slot. The stopped server object cannot be reused. A new server object begins at
baseline `0`.

### Future P4-E3 first／restart Gate

The future first run must form a Complete audit and Complete snapshot, perform
`Completed(>0)`, remove the expected unreachable revisions, prove the Store source changed, close
to same-generation `Incomplete(g)`, complete the existing dirty／save contract, and shut down
normally with OOME and timeout both zero.

The same-world restart must use a new `MinecraftServer` object, begin from memory-index baseline
`0`, and perform a fresh audit without reusing first-run index state. Reclaimed revisions must not
reappear; retained revisions and their identities／checksums must remain. Its expected
`Completed(0)` must prove zero Store publication, dirty, and save request, close to
`CompleteIndex(g)`, and not reissue the consumed permit. A zero-removal first fixture must not
avoid the positive-reclaim branch.

### Future implementation test authority

Later P4-E3 implementation tests must cover all of the following; this documentation-only block
creates no production or test seam:

1. Snapshot non-Complete -> `Incomplete(g)`.
2. Snapshot RuntimeException -> `Incomplete(g)`.
3. Snapshot Error/OOME -> allocation-free `Incomplete(g)` cleanup.
4. Reclaim `Rejected` -> `Incomplete(g)`.
5. Reclaim `Unavailable` -> `Incomplete(g)`.
6. Reclaim `Completed(0)` -> `CompleteIndex(g)`.
7. `Completed(0)` dirty／carrier／Ready publication = 0.
8. Reclaim `Completed(>0)` -> `Incomplete(g)`.
9. Reclaim RuntimeException -> `Incomplete(g)`.
10. Reclaim Error/OOME -> `Incomplete(g)`.
11. `g = Long.MAX_VALUE` demotion is legal and does not return exhaustion.
12. Only the next advance at MAX returns `GenerationExhausted`.
13. Incomplete clears backing／metadata／permit／lease.
14. CompleteIndex does not reissue the permit.
15. Wrong owner／thread／state mark is rejected.
16. A second mark is rejected.
17. Close and its terminal bookkeeping are allocation-free.
18. Server stop with an active lease removes the slot.
19. First run performs positive reclaim.
20. Restart performs zero reclaim.
21. There is no second audit, snapshot, or reclaim.
22. There is no new public API.

### Visibility and unchanged coordinates

The future terminal seam remains internal, exact-owner-bound, single-use, nonforgeable, and
allocation-free:

```text
new public top-level types             = 0
public Complete API delta              = 0
public handoff API delta               = 0
public reclaim-result authority        = 0
public root Iterable/Collection/List   = 0
public Tag/Path/Store/history/carrier  = 0

counter count                          = 25
new counters                           = 0
relevant_records maximum               = 2,048
raw_root_claims maximum                = 65,536
effective MaxHeapSize floor            = 1,610,612,736 bytes
P4-E3 fixed heap                       = 1,536 MiB product-shaped Gate
DataVersion                            = exact IntTag(3955)
P4-E DFU calls                         = 0
E1 source precedence                   = online > integrated > disk
single startup audit / retry           = unchanged / no retry
P4-E2 reconciliation semantics         = unchanged
R2Q                                    = exploratory / non-normative;
                                         not a substitute for the P4-E3 Gate
```

### Conditional authority phase state

```text
P4-E0-B.9 post-reclaim index terminal authority
= COMPLETE UPON THIS AUTHORITY COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1
  FIVE-JOB REMOTE GATE PASS

P4-E0-B.9 separate two-ledger closure
= READY AFTER AUTHORITY REMOTE PASS;
  NOT STARTED

P4-E1
= COMPLETE

P4-E2
= COMPLETE

P4-E3 prior read-only design review
= STOPPED AT POST-RECLAIM INDEX STATE AUTHORITY GAP
  [HISTORICAL]

P4-E3 read-only design review
= BLOCKED UNTIL P4-E0-B.9
  AUTHORITY AND SEPARATE CLOSURE

P4-E3 implementation
= NOT STARTED

P4-E
= INCOMPLETE
```

This authority commit does not complete the separate two-ledger closure, reopen P4-E3 review, make
P4-E3 implementation ready, or complete P4-E. After its unique exact-SHA attempt-1 five-job remote
Gate passes, the exact next work item is the separate two-ledger B.9 closure. No closure work or
P4-E3 work occurs in this block.
<!-- P4_E0_B9_POST_RECLAIM_INDEX_TERMINAL_COMMON_END -->

## P4-E0-B.10 lifecycle-reachable P4-E3 memory envelope authority
<!-- P4_E0_B10_LIFECYCLE_REACHABLE_P4_E3_MEMORY_ENVELOPE_COMMON_BEGIN -->

This synchronized block is the scoped P4-E0-B.10 authority in the codex-spec documents and its
decision／phase index in the architecture ledgers. It supersedes only the earlier P4-E3 startup
fixed-heap wording that required either an actual online player in the simultaneous envelope or an
online-to-disk／integrated componentwise object-size domination proof. Associated test, Definition
of Done, release-blocker, and Stop wording is superseded within that exact scope. Historical B.3
evidence remains immutable. P4-E0-B.9, E1 online behavior and source precedence, E2 reconciliation,
reclaim semantics, generation, dirty／save, and all other product authority remain unchanged.

The integrated-path safety obligation remains, but not as a second memory-accounting unit. If the
reviewed fixture selects integrated startup owners, the actual qualifying child must exercise that
path. Otherwise, a reviewed machine-checked structural proof must lock same-owner disk exclusion,
snapshot replacement rather than dual hydration, no retained compressed／gzip state, and no second
whole-tree copy. This proof establishes only source arbitration and retention shape; it is not
componentwise object-size domination, establishes no source-size ordering, and does not substitute
for the actual selected-envelope child JVM. Integrated functional／alias evidence may be a separate
ordinary product assertion, not a second formal memory campaign.

### Fixed product lifecycle coordinate

```text
P4-E3_FIXED_HEAP_AUTHORITY_COORDINATE
= SYNCHRONOUS_SERVER_STARTING_EVENT

MinecraftServer.loadLevel completed
-> PlayerList already constructed
-> ServerStartingEvent posted synchronously
-> first tick not begun
-> placeNewPlayer not invoked
-> PlayerLoggedInEvent not occurred

online player count              = exactly 0
selected online owner count      = exactly 0
online player entries            = 0
per-player online handles        = 0
per-player online witnesses      = 0
per-player online observations   = 0
```

This exact zero is a locked lifecycle fact, not a reduced workload or a skipped source. The Gate
must invoke the actual production online inventory path and retain its fixed empty-container and
control overhead. A test hook must not bypass that code, and no fixture may fabricate a
`ServerPlayer` as online before `placeNewPlayer`／login.

### Lifecycle-reachable simultaneous envelope

The P4-E3 first child JVM and same-world restart child JVM must each execute the actual synchronous
`ServerStartingEvent` product chain. Within the fixed-heap product coordinate, the Gate must
compose the lifecycle-reachable combination of:

- Store Ready state and full Store／carrier;
- journal Ready state and exact proof-bound journal roots;
- playerdata directory inventory and selected disk owners;
- selected integrated startup owners when the future reviewed fixture uses them;
- source arbitration, relevant-record accounting, and raw-root reservation;
- grouped Store audit and final source freshness;
- memory-only Complete permit, active lease／handoff, and the sole materialized snapshot;
- controlled reclaim and the P4-E0-B.9 outcome-aware terminal;
- actual dirty／save／normal shutdown; and
- a fresh in-memory index on same-world restart.

```text
counter dimensions                    = 25
new counters                          = 0
directory entries maximum case        = 4,096
relevant_records maximum              = 2,048
attachment admissions maximum case    = 1,024
raw_root_claims maximum               = 65,536
journal targets maximum case          = 4,096
DataVersion                           = exact IntTag(3955)
P4-E DFU calls                        = 0
online contribution at ServerStarting = 0
```

The online contribution of zero does not lower either maximum. The remaining exact maxima must be
supplied by sources reachable at this lifecycle coordinate, including disk, any reviewed
integrated startup source used by the fixture, and journal roots. B.10 intentionally does not
preselect the exact owner／root distribution, Store geometry, or positive removed count; the
resumed P4-E3 read-only design review must decide those coordinates from the actual APIs.

### Memory qualification unit and shared objects

```text
P4-E3 memory qualification unit
= one actual product-shaped child JVM
  at the fixed effective MaxHeapSize coordinate

PASS
= child completed
  and OOME = 0
  and timeout = 0
  and all product assertions passed
  and all required maxima were observed
  and required first／same-world restart both executed
```

The fixed process coordinate remains exact
`-Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError`, with effective
`MaxHeapSize = 1_610_612_736` bytes. The heap, fixture maxima, simultaneous envelope, positive
reclaim, and restart may not be raised, reduced, split, or omitted.

One live object in the actual JVM naturally occupies heap once. The documentation must neither add
the same Store, carrier, directory, state, or other shared backing repeatedly across source
families nor treat a shared object as reusable memory credit. B.10 makes no claim that one source
shape is intrinsically smaller than another; release authority is the actual reachable envelope
completing under the fixed heap.

### Superseded online domination and forbidden tooling

The P4-E3 startup Gate no longer requires an `OnlineIdentity`／handle／observation／Ready object to be
mapped field-by-field or byte-by-byte to a disk or integrated witness, file metadata, list backing,
map node, or other object. The following are not P4-E3 release authority and must not be added for
this Gate:

```text
componentwise shallow-size addition
retained-graph byte estimation
object-header estimation
compressed-oops assumption or detector
reference-width or alignment assumption
Map-node size model
JOL dependency
java.lang.instrument agent
Instrumentation.getObjectSize
Unsafe
JVMTI agent
heap-dump retained-size parser
custom object-layout estimator
object-graph receipt
memory-domination report
new research source set
```

These prohibitions do not ban ordinary profiling or debugging; they prevent those mechanisms from
becoming P4-E3 release authority. Existing directly product-owned heap probes may remain part of
the product Gate.

### E1 online contract and production caller lock

B.10 does not remove the online source and does not declare online source memory generally
qualified by the startup Gate. The following E1 contract remains unchanged:

```text
source precedence                   = online > integrated > disk
bounded online identity snapshot    = unchanged
duplicate UUID／wrong server／player = fail fast
Missing／Ready／Quarantined          = unchanged
online handle                       = single-use
initial／final player-state witness  = unchanged
nonempty online relevant accounting = unchanged
```

Existing E1／E2 unit, API, GameTest, and runtime evidence remains valid. If the resumed E3 review
finds a functional online correctness gap, it may design one ordinary, non-1,536-MiB targeted
runtime case; it must not create a second formal memory campaign.

At future P4-E3 production implementation and closure, the global-audit caller contract is:

```text
SkillRetentionRootAuditService.audit production callers = exactly 1
sole production coordinate = SkillDefinitionStoreService ServerStarting lifecycle
post-login audit callers    = 0
post-first-tick callers     = 0
command／reload callers     = 0
background／scheduled callers = 0
E2 login reconciliation     != global audit caller
```

A future static Gate must lock that cardinality and coordinate. Any later production change adding
a post-login, post-first-tick, command／reload, or background／scheduled global-audit caller
automatically sets:

```text
ONLINE_MEMORY_QUALIFICATION = REOPENED
```

That change requires a new review of its reachable simultaneous envelope and the corresponding
fixed-heap qualification. It must not reuse this B.10 online-zero evidence.

### First run, restart, and unchanged product authority

```text
first run
= actual ServerStarting
-> online count 0
-> lifecycle-reachable maximum audit
-> Complete
-> handoff
-> snapshot Complete
-> positive reclaim with a fixed positive removed count
-> Store source change
-> B.9 same-generation Incomplete(g)
-> dirty／normal save／normal shutdown

same-world restart
= new MinecraftServer object
-> memory index baseline 0
-> online count 0
-> fresh lifecycle-reachable maximum audit
-> Complete
-> handoff
-> snapshot Complete
-> expected Completed(0)
-> exact seven-coordinate zero-publication proof
-> B.9 CompleteIndex(g)
-> dirty delta 0
-> normal shutdown
```

The first and restart runs are both mandatory. A regenerated world is not a restart. R2Q remains
exploratory／non-normative and cannot substitute for this product Gate. The following coordinates
remain unchanged:

```text
P4-E0-B.9 terminal mapping       = unchanged
Completed(0) zero-publication    = unchanged
same-generation demotion         = unchanged
generation and MAX semantics     = unchanged
single audit per startup         = unchanged
startup and same-session retries = 0
E1 arbitration                   = unchanged
E2 reconciliation                = unchanged
Store reclaim／dirty／save        = unchanged
new public API                   = 0
offline／integrated writes       = 0
network／DFU／chunk force        = 0
background work                  = 0
second full root vector          = 0
```

### B.10 Stop rules and review resume point

The affected work stops if the lifecycle coordinate or either online exact-zero value cannot be
proven; if a fake pre-start player or inventory bypass is required; if the 2,048／65,536 maxima,
25-vector, heap, first／restart, positive reclaim, or B.9 terminals are weakened; if the maxima
cannot be supplied by lifecycle-reachable sources in the same child; if selected integrated owners
are not exercised in the actual child; if no integrated owner is selected and the required
structural proof is absent; if the sole production audit caller cannot be statically locked; if a
non-startup caller exists without reopening online memory
qualification; if E1 online semantics／precedence／freshness changes; or if object-layout／
domination tooling becomes required.

After this authority and its separate closure complete, the P4-E3 review resumes only at:

1. exact first／restart fixture geometry;
2. lifecycle-reachable 2,048-owner distribution;
3. lifecycle-reachable 65,536-root distribution;
4. exact Store／journal／snapshot／reclaim counts;
5. future production path plan;
6. tests and static Gates;
7. Gradle and workflow plan; and
8. split decision.

It does not reopen B.9, E1 source arbitration, E2 reconciliation, or Candidate／receipt work unless
a later actual-API review finds a direct product conflict.

### Conditional B.10 authority phase state

```text
P4-E0-B.10 lifecycle-reachable P4-E3 memory envelope
= COMPLETE UPON THIS AUTHORITY COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1
  FIVE-JOB REMOTE GATE PASS

P4-E0-B.10 separate two-ledger closure
= READY AFTER AUTHORITY REMOTE PASS;
  NOT STARTED

P4-E0-B.9
= COMPLETE

P4-E1
= COMPLETE

P4-E2
= COMPLETE

P4-E3 prior review stop
= STOPPED AT ONLINE SOURCE MEMORY OBLIGATION
  [HISTORICAL]

P4-E3 read-only design review
= BLOCKED UNTIL P4-E0-B.10
  AUTHORITY AND SEPARATE CLOSURE

P4-E3 implementation
= NOT STARTED

P4-E
= INCOMPLETE
```

This authority commit does not perform the separate two-ledger closure, reopen the P4-E3 review,
start implementation, or complete P4-E. After its unique exact-SHA attempt-1 five-job remote Gate
passes, the exact next work item is the separate B.10 two-ledger closure.
<!-- P4_E0_B10_LIFECYCLE_REACHABLE_P4_E3_MEMORY_ENVELOPE_COMMON_END -->

## P4-E3-Q0.2 closed startup observation seam authority
<!-- P4_E3_Q02_CLOSED_STARTUP_OBSERVATION_AUTHORITY_COMMON_BEGIN -->

This synchronized documentation-only scoped amendment adopts the completed P4-E3-Q0.1 Candidate A
technical review and exact public-surface evidence. It creates no implementation, does not resume
the preserved 13-path implementation worktree, and does not perform the separate Q0.2 two-ledger
closure.

Within the closed startup-observation seam only, this later authority supersedes the earlier active
wording that assumed no deterministic pre-start arm route, fixed the implementation to three
production files and the older path inventory, required an absolute public-API delta of zero, left
test-listener and JAR isolation unspecified, or described implementation as READY／NOT STARTED.
It also narrowly supersedes the B.9 sentence “There is no new public API” and the B.10 coordinate
“new public API = 0” only for the exact nested facade surface below. Historical B.9, B.10, and
P4-E3 review／closure evidence remains immutable.

The resulting visibility boundary is exact:

~~~text
new public top-level types                    = 0
new public service / Store / root / index API = 0
new public raw-result or authority getter     = 0
new public Complete / handoff API              = 0
new public facade nested recording view        = 1
new public facade nested bounded enums         = 4
new StoreView accessor                         = 1
new public-final recording operations          = 13
public arm / claim / consume                    = 0 / 0 / 0
Gramarye.java                                   = NO DELTA
~~~

P4-E0-B.9, P4-E0-B.10, E1 source precedence, E2 reconciliation, the product
ServerStarting audit／snapshot／reclaim／B.9 chain, P4-E3 NO SPLIT, and all fixed memory-Gate
coordinates remain unchanged.

### Q0.2 evidence basis

The normative surface source for this amendment is the repository-external Q0.1 evidence root:

~~~text
/private/tmp/gramarye-p4-e3-q01-surface-review-evidence-20260823T081707Z
~~~

It contains exactly 14 regular files, no symlinks or extras. The exact
PUBLIC_SURFACE_SKELETON.java.txt SHA-256 is:

~~~text
00bc4b332bc8edce520d7606b8435aff7178f1b09ce596e744e6af5f8668710d
~~~

The Q0.1 Java 21 positive compile, executable state harness, fourteen negative compile probes,
javap, jdeps, exhaustive actual-result mappings, client-only scan, and callback／raw／ThreadLocal／
Unsafe／suppression／side-channel scans all passed. This block records that reviewed surface; it
does not treat the external probe as repository implementation.

### Q0.2 Candidate A deterministic pre-start route

The only authorized route is:

~~~text
Gramarye constructor
-> create the unique existing P4E2QualificationFacade
-> inject that exact facade's StoreView into SkillDefinitionStoreService
-> register that exact facade identity as the existing custom extension
-> constructor returns
-> p4E3GameTest-only AutomaticEventSubscriber class is initialized and registered
-> GameTestServer.initServer
-> PlayerList installed
-> synchronous ServerAboutToStartEvent
-> test-only HIGHEST listener resolves the exact same facade extension
-> armE3Startup(exactServer)
-> loadLevel
-> synchronous production ServerStartingEvent
-> existing NORMAL SkillDefinitionStoreService listener
-> install
-> journal bootstrap
-> audit
-> consume
-> snapshot
-> reclaim
-> B.9 close
-> ServerStarted
-> first GameTest tick resolves the exact same facade again
-> claimE3Startup(exactServer)
-> immediate consumeE3Startup(exactServer, exactSession)
~~~

FMLModContainer.constructMod completes the Gramarye constructor before
AutomaticEventSubscriber.inject loads and registers the isolated test class on the NeoForge game
bus. GameTestServer.initServer posts ServerAboutToStartEvent before loadLevel and posts
ServerStartingEvent after loadLevel. The arm and product listener therefore use different event
types and do not depend on same-event listener registration order.

The isolated listener shape is fixed:

~~~java
@EventBusSubscriber(
        modid = Gramarye.MOD_ID,
        value = Dist.DEDICATED_SERVER)
@SubscribeEvent(priority = EventPriority.HIGHEST)
static void onServerAboutToStart(
        ServerAboutToStartEvent event)
~~~

Facade resolution is fixed:

~~~text
ModList.get()
-> exact Gramarye ModContainer
-> getCustomExtension(P4E2QualificationFacade.class)
-> exact same preconstructed facade identity
~~~

The test-access class has no mutable static field. The later GameTest re-resolves the facade,
claims one local opaque Session, immediately consumes it, and copies the bounded package-private
snapshot into its test-only observation. No session crosses an event callback. First and restart
are separate child JVMs with fresh ModList, container, facade, server, E3 cell, and token sequence;
both first legal sessions use token 1. Only the same-world disk crosses those processes.

The route prohibits a Gramarye.java delta, a second facade, extension point, Store service, or
production listener; a static locator; ThreadLocal; reflection／Unsafe; a system property; and
filesystem／log session transport.

### Q0.2 exact sealed hierarchy and accessor

The nested hierarchy is exact:

~~~java
public static sealed abstract class E3StartupView
        permits E3StartupViewImpl {
    private E3StartupView(
            P4E2QualificationFacade owner);
}

private static final class E3StartupViewImpl
        extends E3StartupView {
    private E3StartupViewImpl(
            P4E2QualificationFacade owner);
}
~~~

The existing closed StoreView receives exactly one accessor:

~~~java
public final E3StartupView e3StartupView();
~~~

Its argument count is zero; its return type is exactly E3StartupView; every call returns the same
nonnull owner-bound view identity; and allocation per accessor call is zero. E3StartupView may not
become an interface, gain another permits target, expose its implementation, or change either
constructor's visibility or parameter list.

### Q0.2 four exact bounded enums

~~~java
public enum E3AuditVariant {
    COMPLETE,
    INCOMPLETE,
    OVER_LIMIT,
    RECONCILIATION_REQUIRED,
    GENERATION_EXHAUSTED
}

public enum E3SnapshotVariant {
    COMPLETE,
    INCOMPLETE,
    TRUNCATED,
    OVER_LIMIT
}

public enum E3ReclaimVariant {
    COMPLETED_ZERO,
    COMPLETED_POSITIVE,
    REJECTED,
    UNAVAILABLE
}

public enum E3IndexTerminal {
    COMPLETE_INDEX,
    INCOMPLETE
}
~~~

The public nested enum count is exactly four and the value count is exactly fifteen. Names and
values may not be renamed, merged, added, or removed. Actual sealed production variants require
exhaustive no-default mapping; UNKNOWN or fallback mapping is prohibited.

### Q0.2 exactly thirteen public-final recording operations

E3StartupView declares exactly these thirteen public final instance operations:

~~~java
public final boolean beginRecording(
        MinecraftServer exactServer);

public final void recordAuditInvocation(
        MinecraftServer exactServer);

public final void recordAuditResult(
        MinecraftServer exactServer,
        E3AuditVariant variant,
        long generation);

public final void recordCompleteConsumeInvocation(
        MinecraftServer exactServer);

public final void recordSnapshotInvocation(
        MinecraftServer exactServer);

public final void recordSnapshotResult(
        MinecraftServer exactServer,
        E3SnapshotVariant variant,
        int completeRootCount);

public final void recordReclaimInvocation(
        MinecraftServer exactServer,
        boolean dirtyBefore);

public final void recordReclaimResult(
        MinecraftServer exactServer,
        E3ReclaimVariant variant,
        int historiesScanned,
        int revisionsScanned,
        int historiesChanged,
        int revisionsReclaimed);

public final void recordDirtyAfter(
        MinecraftServer exactServer,
        boolean dirtyAfter);

public final void recordIndexTerminal(
        MinecraftServer exactServer,
        E3IndexTerminal terminal,
        long generation);

public final void completeRecording(
        MinecraftServer exactServer);

public final void abortRecording(
        MinecraftServer exactServer);

public final void clearOnServerStopped(
        MinecraftServer exactServer);
~~~

Public checked exceptions, generic Object／callback／collection parameters, and public
service／Store／root／index／result returns are all zero. These operations may not be regrouped,
split, merged, or renamed. In IDLE, beginRecording returns false and the remaining twelve
operations are no-ops before argument validation; the unarmed production path allocates and retains
nothing and does not read dirty state or invoke classification or terminal observation.

### Q0.2 package-private controls and bounded records

The exact package-private control surface is:

~~~java
void armE3Startup(
        MinecraftServer exactServer);

E3StartupSession claimE3Startup(
        MinecraftServer exactServer);

E3StartupSnapshot consumeE3Startup(
        MinecraftServer exactServer,
        E3StartupSession exactSession);

void abortE3Startup(
        MinecraftServer exactServer,
        E3StartupSession exactSession);
~~~

The Session is exact:

~~~java
static final class E3StartupSession {
    private final P4E2QualificationFacade owner;
    private final long token;

    private E3StartupSession(
            P4E2QualificationFacade owner,
            long token);
}
~~~

The type is package-private and final, its fields and constructor are private, and it contains only
the exact facade identity and a long token. It contains no server, root, result, or index.

The completed DTO is the following package-private nested record with a package-private canonical
constructor and exactly nineteen primitive／enum fields:

~~~java
record E3StartupSnapshot(
        long sessionToken,
        int auditInvocations,
        E3AuditVariant auditVariant,
        long auditGeneration,
        int completeConsumeInvocations,
        int snapshotInvocations,
        E3SnapshotVariant snapshotVariant,
        int completeRootCount,
        int reclaimInvocations,
        E3ReclaimVariant reclaimVariant,
        int historiesScanned,
        int revisionsScanned,
        int historiesChanged,
        int revisionsReclaimed,
        boolean dirtyBefore,
        boolean dirtyAfter,
        int indexTerminalObservations,
        E3IndexTerminal indexTerminal,
        long indexGeneration) {
}
~~~

Public Session and completed DTO types are zero. Public arm, claim, and consume operations are zero.

### Q0.2 cell separation, identity, and state

The existing E2 qualification cell and the new E3 startup-observation cell are distinct. They share
no active state, completed record, session token, server reference, counter, or consume／abort
lifecycle.

~~~text
IDLE
-> armE3Startup
-> ARMED_BEFORE_SERVER_STARTING
-> beginRecording
-> RECORDING
-> completeRecording
-> COMPLETED
-> claimE3Startup
-> consumeE3Startup
-> CONSUMED
-> IDLE
~~~

~~~text
ARMED_BEFORE_SERVER_STARTING / RECORDING / COMPLETED
-> abortRecording / abortE3Startup / exact ServerStopped clear
-> ABORTED / CLEARED
-> IDLE
~~~

CONSUMED, ABORTED, and CLEARED are assignment-only transient states followed immediately by the
common allocation-free clear to IDLE. Each facade permits at most one active E3 session or one
completed-unconsumed session, never both.

Facade identity is the enclosing owner reference. Server identity is exact Java identity and the
exact server thread; no Thread is retained. Session identity is the nonforgeable pair
(owner, token). The private token counter starts at zero; the first successful arm obtains one;
each successor uses Math.incrementExact; wrap, saturation, reuse, and reset are prohibited.
Exhaustion fails before cell mutation. First and restart have fresh facade-lifetime counters.

The fixed failure codes are:

~~~text
P4E3_STARTUP_OBSERVATION_ALREADY_ACTIVE
P4E3_STARTUP_OBSERVATION_ALREADY_CLAIMED
P4E3_STARTUP_OBSERVATION_WRONG_STATE
P4E3_STARTUP_OBSERVATION_WRONG_CONTEXT
P4E3_STARTUP_OBSERVATION_WRONG_SESSION
P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE
~~~

A second arm, claim, consume, or invalid state transition fails with its fixed code. A wrong facade
or token fails WRONG_SESSION. A wrong server or thread fails WRONG_CONTEXT. Failure never clears a
legitimate foreign session.

### Q0.2 completed-server identity witness

The bounded E3StartupSnapshot is runtime-reference-free. As the sole narrow exception, the existing
facade's private E3 cell may retain exactly one strong MinecraftServer reference while state is
COMPLETED and the record remains unconsumed. It is the same exact-server cell written at arm,
retained as the completed-server identity witness; it is not a second field and is not copied into
the completed DTO.

The witness has no public or protected accessor; is never placed in a static, ThreadLocal, Map, or
Collection; is not serialized; enters no network, filesystem, log, or system property; is null when
unarmed; and is cleared by consume, public abort, package-private discard, or exact server stop.
A foreign-server stop fails closed and leaves the lawful session unchanged.

COMPLETED retains no Thread, Store, handoff, product snapshot, root, index, permit, Throwable,
player, Tag, or Path. Completion ordering is exact:

1. validate the full direct-coordinate branch;
2. allocate and fully construct the bounded E3StartupSnapshot;
3. complete every potentially allocating or fallible operation;
4. assign the completed DTO and completed token;
5. clear mutable coordinate fields by assignment only;
6. assign exactServer to the sole existing exact-server field as the completed-server identity witness;
7. assign state = COMPLETED as the last commit write; and
8. return as the next bytecode action.

Nothing may allocate or invoke a fallible external operation after the commit write.

### Q0.2 direct-coordinate mapping

The completed record is direct runtime evidence, not an inference from disk hashes, size, mtime, or
logs.

| Coordinate | Actual production source | Recording rule |
| --- | --- | --- |
| Recording begin | facade ARMED cell | beginRecording is the first runP4E3StartupReclaim action; false skips all classification, observer, and dirty reads |
| Audit invocation | immediately before rootAuditService.audit | record exactly one actual call |
| Audit result | returned sealed SkillRetentionRootAuditResult | exhaustive mapping to the five E3AuditVariant values |
| Audit generation | actual AuditSummary indexGeneration | record the nonnegative generation, including generationOnly(MAX) |
| Complete consume invocation | actual Complete branch before consumeComplete | zero for every non-Complete audit; otherwise exactly one |
| Snapshot invocation | immediately before fromCompleteRoots | exactly one only after Complete consume |
| Snapshot result | returned sealed SkillRetentionRootSnapshot | exhaustive Complete／Incomplete／Truncated／OverLimit mapping |
| Snapshot root count | actual Complete roots size | nonnegative only for Complete; exact -1 for every non-Complete |
| Reclaim invocation | immediately before the sole service reclaim | exactly one only after snapshot Complete; record same-adapter dirtyBefore |
| Dirty before | same installed adapter immediately before the sole reclaim | record the actual boolean captured with the reclaim invocation |
| Outer reclaim result | actual SkillSubsystemResult | Unavailable maps UNAVAILABLE; Available proceeds to the inner result |
| Inner reclaim result | actual SkillReclaimResult | Rejected maps REJECTED; Completed removed 0／greater than 0 maps COMPLETED_ZERO／COMPLETED_POSITIVE |
| Reclaim report | actual Completed report | four nonnegative counts for Completed; exact four -1 sentinels when there is no report |
| Dirty after | same installed adapter after normal reclaim return | record the actual boolean after the typed result |
| B.9 terminal | actual private index state after normal handoff.close return | bounded observer maps only CompleteIndex or IncompleteState |
| B.9 generation | actual terminal state's generation | must equal the recorded audit generation |
| Normal completion | full validated branch | allocate and publish one bounded DTO only after all required coordinates |
| Server-stop cleanup | exact event server | clear after existing E2 cleanup and before root-audit removal and uninstall |

The audit mapping is exhaustive: Complete maps COMPLETE; ordinary Incomplete maps INCOMPLETE;
OverLimit maps OVER_LIMIT; ReconciliationRequired maps RECONCILIATION_REQUIRED; and the bounded
generation-exhausted Incomplete reason maps GENERATION_EXHAUSTED. Non-Complete audit is a valid
audit-terminal record with consume, snapshot, reclaim, dirty reads, and B.9 terminal observations
all zero.

The snapshot mapping is exhaustive. Reclaim classification is exhaustive over the outer and inner
sealed families. COMPLETED_ZERO requires revisionsReclaimed equal to zero; COMPLETED_POSITIVE
requires it greater than zero. Completed report counts are actual and nonnegative. Rejected and
Unavailable use exactly four -1 sentinels.

recordIndexTerminal is accepted only after either a fully recorded non-Complete snapshot terminal
or a fully recorded reclaim terminal, including dirty-before and dirty-after. Premature terminal
observation and COMPLETED_ZERO plus INCOMPLETE are rejected. completeRecording independently
rechecks the branch, result-to-terminal mapping, and generation.

### Q0.2 bounded B.9 terminal observer

The existing SkillRetentionRootAuditService path may add exactly this package-private nested record
and package-private method:

~~~java
static record P4E3IndexTerminalObservation(
        P4E2QualificationFacade.E3IndexTerminal terminal,
        long generation) {
}

P4E3IndexTerminalObservation observeP4E3IndexTerminal(
        MinecraftServer exactServer);
~~~

The compact constructor requires a nonnull terminal and nonnegative generation. The method applies
the existing installed-server, server-thread, not-stopped, owner, and exact identity-slot checks,
then uses an exhaustive no-default switch over every private IndexState.

~~~text
CompleteIndex                -> COMPLETE_INDEX
IncompleteState              -> INCOMPLETE
NoEntry                      -> P4E3_INDEX_TERMINAL_NOT_AVAILABLE
AuditInProgress              -> P4E3_INDEX_TERMINAL_NOT_AVAILABLE
CompleteIndexWithActiveLease -> P4E3_INDEX_TERMINAL_NOT_AVAILABLE
GenerationExhausted          -> P4E3_INDEX_TERMINAL_NOT_AVAILABLE
Removed                      -> P4E3_INDEX_TERMINAL_NOT_AVAILABLE
~~~

A new IndexState variant must break exhaustive compilation. The observer is called exactly once,
only for an armed normal Complete-audit path and only after handoff.close returns normally. Its one
bounded enum／long record is immediately copied into the facade cell. It exposes or retains no
private IndexState, backing, metadata, lease, permit, root, Store, or handoff. Unarmed and
non-Complete-audit paths do not invoke it.

### Q0.2 cleanup, stop, and unarmed semantics

The armed production wrapper uses an outer RuntimeException／Error catch, calls correct-context
abortRecording, and rethrows the identical Throwable object. Active-lease B.9 close remains in its
inner allocation-free finally before facade abort. RuntimeException, Error, or OOME never becomes
ordinary success and no Throwable, message, or stack is retained.

Correct-context public abort, package-private abort／discard, consume cleanup, and server-stop
cleanup use field assignments only. OOME while allocating the completed DTO occurs before
COMPLETED; abort clears active server, active token, and partial coordinates and rethrows the same
OOME. OOME while claim allocates the bounded Session leaves the completed cell unclaimed so exact
server stop can still clear it.

ServerStopped order is exact:

~~~text
existing E2 StoreView.clearOnServerStopped()
-> E3StartupView.clearOnServerStopped(exactServer)
-> SkillRetentionRootAuditService.removeServer(exactServer)
-> SkillDefinitionStoreService.uninstall(exactServer)
~~~

The stop cleanup clears active or completed session-token cells, partial coordinates, completed
record, completed-server witness, and claimed flag. It does not reset the facade-lifetime
e3NextToken counter. A foreign server or wrong thread throws WRONG_CONTEXT without clearing the
lawful session.

When unarmed, recording allocation, retained reference, dirty read, classification, B.9 observer,
control-flow／result delta, persistence mutation, network mutation, and filesystem mutation are all
zero.

### Q0.2 listener and artifact isolation

~~~text
production ServerStarting listeners       = 1
production ServerStopped listeners        = 1
production lifecycle listeners total      = 2
test-only ServerAboutToStart listeners     = 1
test-only listener source set              = p4E3GameTest only
test-only stop listeners                   = 0
~~~

The test-only source is:

~~~text
src/p4E3GameTest/java/com/yo1no/gramarye/P4E3StartupObservationTestAccess.java
~~~

It is a public final test-access class with a private constructor, the one HIGHEST
ServerAboutToStart handler, no mutable static fields, and one public nested bounded Observation
record for the store-package GameTest. It exposes no production service, Store, root, handoff,
snapshot, index, permit, or raw result.

The production JAR must include the approved production E3StartupView, four enums, private
implementation, E3 cell, and bounded internal classes. It must exclude
P4E3StartupObservationTestAccess and every nested class, P4E3StartupMemoryGameTests and every
nested class, every p4E3Probe class, src/test outputs, JUnit, Hamcrest, generated GameTest
resources, research, Candidate, and receipt tooling. The default production mod and JAR use
sourceSets.main only; only the isolated P4-E3 run adds p4E3GameTest. Static gates count production
and custom-source listeners separately.

### Q0.2 exact twenty-seven-path implementation scope

The later implementation scope is exactly the following paths.

Production modifications — 4:

~~~text
src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java
src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java
src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java
src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.java
~~~

Modified existing tests — 6:

~~~text
src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeTest.java
src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeVisibilityCompileTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BCompleteHandoffTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java
~~~

New P4-E3 tests — 3:

~~~text
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupLifecycleTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3LeaseTerminalTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3ApiGateTest.java
~~~

Probe files — 5:

~~~text
src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureBuilder.java
src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureManifest.java
src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3ProbeMain.java
src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FileVerifier.java
src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/player/P4E3PlayerDataFixture.java
~~~

Custom GameTest files — 2:

~~~text
src/p4E3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupMemoryGameTests.java
src/p4E3GameTest/java/com/yo1no/gramarye/P4E3StartupObservationTestAccess.java
~~~

Modified existing verifiers — 4:

~~~text
scripts/verify-p4-e2-configuration.sh
scripts/verify-p4-e1-configuration.sh
scripts/verify-p4-e0-r-configuration.sh
scripts/verify-p4-e0-r2q-configuration.sh
~~~

New verifier — 1:

~~~text
scripts/verify-p4-e3-configuration.sh
~~~

Build and workflow — 2:

~~~text
build.gradle
.github/workflows/build.yml
~~~

~~~text
production modifications              = 4
modified existing tests               = 6
new P4-E3 tests                       = 3
probe files                           = 5
custom GameTest files                 = 2
modified existing verifiers           = 4
new verifier                          = 1
build / workflow                      = 2
total                                 = 27
unknown paths                         = 0
tracked fixture / resource additions  = 0
codex-spec / ledger / README delta
  in the implementation commit        = 0
Gramarye.java                         = NO DELTA
~~~

A twenty-eighth implementation path, a tracked fixture or resource, a Gramarye.java delta, a second
facade／extension／service／production listener, or a public service／Store／root／index surface is a
Stop condition.

### Q0.2 compile, API, and static-gate obligations

Future implementation tests and portable configuration gates must lock:

- the exact sealed class modifiers, one permits target, private constructors, and same accessor
  identity;
- four enums, fifteen values, exhaustive no-default actual-result mappings, and no fallback;
- exactly thirteen public-final operation descriptors and no checked exceptions;
- exact package-private arm／claim／consume／abort descriptors, private Session constructor, and
  exact nineteen-field package-private E3StartupSnapshot;
- new public top-level types zero and no public facade constructor or top-level arm／claim／consume;
- exact server／thread／facade／session identity, one active or completed-unconsumed maximum,
  monotonic token and exhaustion, wrong-context preservation, duplicate and order failures;
- complete publication's single DTO allocation, final COMPLETED field write, immediate return, and
  no prohibited completed retention;
- direct actual result branches, counts, dirty transition, post-close B.9 terminal, generation,
  premature-terminal rejection, and COMPLETED_ZERO／COMPLETE_INDEX agreement;
- correct RuntimeException／Error／OOME identity and allocation-free cleanup;
- exact production listener count two, exact test-only HIGHEST AboutToStart listener count one,
  no test stop listener, and no non-startup production audit caller;
- exact twenty-seven changed-path allowlists in all affected phase verifiers;
- exact two P4-E3 source sets and five P4-E3 child JVM tasks, with no extra source set, task, retry,
  or second memory job;
- production-JAR inclusion of the authorized nested facade surface and exclusion of all test,
  p4E3GameTest, p4E3Probe, JUnit／Hamcrest, generated resource, research, Candidate, and receipt
  outputs; and
- unchanged normal GameTest count twelve, future P4-E workflow job count one, and exact existing
  build-job dependencies plus the one reviewed P4-E memory job.

Positive and negative compile probes, javap -public -s, full javap -p -s -c -v, jdeps, bytecode
allocation／publication checks, text-block-aware equal-length lexical masking, literal linear
callsite scans, JAR entry and byte-identity checks, and portable verifier three-mode execution are
required. These gates must not use reflection, raw Object／collection surfaces, a static locator,
filesystem／log side channels, or production exposure.

### Q0.2 unchanged product authority

The observation seam is memory-only and qualification-only. It neither changes nor chooses a
product result, mutation, terminal, retry, or caller. The product lifecycle remains:

~~~text
ServerStarting
-> audit
-> handoff
-> snapshot
-> reclaim
-> B.9 close
~~~

The following coordinates remain exact:

~~~text
P4-E0-B.9 terminal semantics        = unchanged
P4-E0-B.10 startup online count     = 0
E1 source precedence                = online > integrated > disk
E2 reconciliation                  = unchanged
P4-E3 split                         = NO SPLIT

relevant_records maximum            = 2,048
raw_root_claims maximum             = 65,536
counter dimensions                  = 25
new counters                        = 0
DataVersion                         = exact IntTag(3955)
P4-E DFU calls                      = 0
P4-E3 fixed heap                    = 1,536 MiB
first revisions reclaimed           = 1
restart revisions reclaimed         = 0
future workflow jobs added          = 1
~~~

The seam adds no retry, second audit, second snapshot, second reclaim, Store mutation, dirty or save
decision, network or filesystem write, DFU call, playerdata read, or background work. It records
the actual local result branch after the product operation. R2Q remains exploratory and
non-normative and does not substitute for the future P4-E3 product Gate.

### Q0.2 conditional authority phase state

~~~text
P4-E3-Q0.1 technical feasibility
= PASS — CANDIDATE A

P4-E3-Q0.1 public-surface completion
= PASS

P4-E3-Q0.2 closed startup observation authority
= COMPLETE UPON THIS AUTHORITY COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1
  FIVE-JOB REMOTE GATE PASS

P4-E3-Q0.2 separate two-ledger closure
= READY AFTER AUTHORITY REMOTE PASS;
  NOT STARTED

P4-E3 read-only design review
= COMPLETE — PASS

P4-E3 split
= NO SPLIT

P4-E3 implementation
= SUSPENDED IN EXISTING WORKTREE;
  BLOCKED UNTIL Q0.2 AUTHORITY
  AND SEPARATE CLOSURE

P4-E
= INCOMPLETE
~~~

This authority commit does not perform the separate Q0.2 two-ledger closure, resume implementation,
create a production or test seam, make implementation READY, or complete P4-E. After its unique
exact-SHA attempt-1 five-job remote Gate passes, the exact next work item is the separate
documentation-only Q0.2 two-ledger closure. No implementation work resumes in this block.
<!-- P4_E3_Q02_CLOSED_STARTUP_OBSERVATION_AUTHORITY_COMMON_END -->

## P4-E3-Q0.3 exact-29 implementation-scope authority
<!-- P4_E3_Q03_EXACT29_IMPLEMENTATION_SCOPE_AUTHORITY_COMMON_BEGIN -->

This synchronized documentation-only scoped amendment corrects only the reviewed P4-E3
implementation path allowlist and the two stale existing-test contracts discovered during local
qualification. It does not resume implementation, change any Java surface or product behavior,
perform the separate Q0.3 two-ledger closure, or complete P4-E. All other Q0.1／Q0.2, B.9, B.10,
E1, E2, and P4-E3 NO SPLIT authority outside this exact allowlist and test-contract correction
remains unchanged.

Within this exact scope only, the earlier twenty-seven-path wording is superseded by the
twenty-nine-path inventory below. All twenty-seven previously approved paths remain approved
without semantic change. Exactly two existing tests are added; no other implementation path is
authorized.

### Historical local-qualification blocker and exhaustive result

The preserved implementation reached local full-unit qualification and stopped after exactly two
substantive stale out-of-scope test contracts were emitted:

~~~text
src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java
= historical global production catch-Error count zero

src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java
= historical direct uninstall(event.getServer()) source shape
~~~

A repository-wide read-only test and portable-verifier inventory found no third stale consumer for
these two changed seams at the reviewed HEAD and preserved implementation bytes.

~~~text
known stale consumers outside exact-27 scope = exactly 2
third known stale consumer                   = none found
~~~

If resumed implementation later exposes a third same-class out-of-scope stale consumer, the result
is STOP VERIFICATION RECURSION. The implementation scope must not be expanded one file at a time
without a new exhaustive review and authority decision.

### Exact reviewed startup Error-cleanup exception

The later implementation may contain exactly one reviewed production catch whose alternatives
include Error:

~~~text
owner       = SkillDefinitionStoreService
method      = private void runP4E3StartupReclaim(MinecraftServer server)
catch shape = catch (RuntimeException | Error failure)
rethrow     = throw failure;
count       = exactly 1
~~~

Its sole purpose is the Q0.2-approved allocation-free E3 startup-observation abort, clearing active
observation references and rethrowing the identical pending RuntimeException／Error／OOME object.
P4B2BApiGateTest must prove that the caught variable and exact rethrown variable are both failure
inside that exact private method and owner. It must not disable or delete the existing dependency
Error-safety checks.

~~~text
wrapper / translation                         = 0
swallow                                       = 0
logging-only completion                       = 0
retry                                         = 0
bounded-success conversion                    = 0
second production Error catch                 = 0
all other production catch Error              = 0
catch Throwable in SkillDefinitionStoreService = 0
catch Throwable in runP4E3StartupReclaim      = 0
Error catch in Store reclaim core             = 0
Error catch in E1 audit path                  = 0
Error catch in E2 login reconciliation        = 0
~~~

The reviewed wrapper may not be widened to catch Throwable. A filename-only exemption, an
owner-wide exemption, a catch with no exact rethrow, or an exception outside
runP4E3StartupReclaim is not authorized. This scoped rule does not rewrite unrelated historical
GameTest helper code.

### Exact ServerStopped local identity and order

The Q0.2 zero-argument E2 cleanup surface remains unchanged. The sole existing
SkillDefinitionStoreService ServerStoppedEvent listener must retain this exact source shape:

~~~java
var server = event.getServer();
if (qualificationStoreView != null) {
    qualificationStoreView.clearOnServerStopped();
    qualificationStoreView.e3StartupView().clearOnServerStopped(server);
}
rootAuditService.removeServer(server);
uninstall(server);
~~~

The inferred type of the exact local server is MinecraftServer. event.getServer(), or its equivalent
bytecode acquisition, occurs exactly once. The existing E2 call
qualificationStoreView.clearOnServerStopped() has zero arguments, executes first, and must not be
changed to accept MinecraftServer. Only the following three operations take a server argument, and
each must read the same local server identity:

~~~text
qualificationStoreView.e3StartupView().clearOnServerStopped(server)
rootAuditService.removeServer(server)
uninstall(server)
~~~

The exact order is:

~~~text
zero-argument E2 qualification cleanup
-> E3 observation cleanup(server)
-> root-audit removeServer(server)
-> Store-service uninstall(server)
~~~

P4E2LifecycleOrderingTest must prove the single effective acquisition, zero-argument E2 call, three
same-local server reads, and lexical or bytecode call order. Merely finding four substrings is not
proof. The production lifecycle listener count remains exactly two: one ServerStarting listener
and one ServerStopped listener. A second stop listener, nested stop event, executor, future,
callback, background cleanup, retry, or second stop dispatch is prohibited.

### Exact twenty-nine-path implementation scope

Production modifications — 4:

~~~text
src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java
src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java
src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java
src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.java
~~~

Modified existing tests — 8:

~~~text
src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeTest.java
src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeVisibilityCompileTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BCompleteHandoffTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java
~~~

New P4-E3 tests — 3:

~~~text
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupLifecycleTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3LeaseTerminalTest.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3ApiGateTest.java
~~~

Probe files — 5:

~~~text
src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureBuilder.java
src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureManifest.java
src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3ProbeMain.java
src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FileVerifier.java
src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/player/P4E3PlayerDataFixture.java
~~~

Custom GameTest files — 2:

~~~text
src/p4E3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupMemoryGameTests.java
src/p4E3GameTest/java/com/yo1no/gramarye/P4E3StartupObservationTestAccess.java
~~~

Modified existing verifiers — 4:

~~~text
scripts/verify-p4-e2-configuration.sh
scripts/verify-p4-e1-configuration.sh
scripts/verify-p4-e0-r-configuration.sh
scripts/verify-p4-e0-r2q-configuration.sh
~~~

New verifier — 1:

~~~text
scripts/verify-p4-e3-configuration.sh
~~~

Build and workflow — 2:

~~~text
build.gradle
.github/workflows/build.yml
~~~

~~~text
production modifications              = 4
modified existing tests               = 8
new P4-E3 tests                       = 3
probe files                           = 5
custom GameTest files                 = 2
modified existing verifiers           = 4
new verifier                          = 1
build.gradle                          = 1
.github/workflows/build.yml           = 1
total                                 = 29
unknown paths                         = 0
tracked fixture / resource additions  = 0
codex-spec / ledger / README delta
  in the implementation commit        = 0
Gramarye.java                         = NO DELTA
~~~

A thirtieth implementation path is not authorized. If one is required, implementation stops with
STOPPED — EXACT-29 IMPLEMENTATION SCOPE INSUFFICIENT.

### Exact verifier synchronization authority

No verifier path is added by Q0.3. When implementation is separately authorized to resume, the
following five already approved verifier paths may replace their exact twenty-seven-path inventory
with the exact twenty-nine-path inventory above:

~~~text
scripts/verify-p4-e2-configuration.sh
scripts/verify-p4-e1-configuration.sh
scripts/verify-p4-e0-r-configuration.sh
scripts/verify-p4-e0-r2q-configuration.sh
scripts/verify-p4-e3-configuration.sh
~~~

Each of the five verifiers must independently lock the same complete exact twenty-nine-path set.
They must require both newly approved test paths, modified-existing-test count eight, total count
twenty-nine, unknown tracked paths zero, unknown untracked paths zero, and missing required paths
zero. A src/test blanket, P4*Test wildcard, directory-prefix allowlist, warning-only unknown path,
or disabled prohibited-path Gate remains forbidden.

The verifiers must continue to lock the Q0.2 exact public surface, production lifecycle listener
count two, isolated test-only listener, and the future exact six-job workflow only after
implementation. This Q0.3 authority commit itself does not modify or run those verifiers and still
qualifies remotely against the current five-job workflow.

### Unchanged authority and product envelope

Q0.3 changes only the implementation allowlist and the two existing-test contracts above.

~~~text
Candidate A route                     = unchanged
E3StartupView sealed hierarchy        = unchanged
four exact enums                      = unchanged
thirteen exact operations             = unchanged
four package-private session controls = unchanged
completed-server witness              = unchanged
clearOnServerStopped(exactServer)     = unchanged
B.9 terminal observer                 = unchanged
B.9 terminal semantics                = unchanged
B.9 close generation increment        = 0
B.10 startup online count             = 0
E1 source precedence                  = online > integrated > disk
E2 reconciliation                     = unchanged
P4-E3 split                           = NO SPLIT

relevant_records                      = 2,048
raw_root_claims                       = 65,536
counter dimensions                    = 25
DataVersion                           = exact IntTag(3955)
P4-E DFU calls                        = 0
fixed heap                            = 1,536 MiB
first revisions reclaimed             = 1
restart revisions reclaimed           = 0
future workflow jobs added            = 1
~~~

No product result, mutation, dirty／save decision, retry, generation, memory envelope, fixture,
workflow topology, network operation, filesystem operation, service ownership, public API, or
listener is changed by this documentation amendment.

### Preserved implementation worktree

The partial implementation remains suspended and read-only during this authority work:

~~~text
worktree
= /private/tmp/gramarye-p4-e3-implementation-resumed-20260823T144730Z

detached HEAD
= 642f004788427a97133fee7f8eca08453548c5fe

base tree
= 4a6aa4ddf0de0ed1ae7cfb87ec8f86387272fdc2

reviewed paths
= exactly 27

unknown paths / index
= 0 / empty

source-freeze SHA-256
= 63903da0141f295ef842d064197095eb543b58060d60b7a222938dc97167067e

porcelain-v2 SHA-256
= 4c77bab0ec57a4475f8f13c3b3e8925a8ec21e822da3a88388fd377b17908a61

full-index diff SHA-256
= 83eac76fbaf60b2f028db49e1c1dc7b2be5ede95d31132b215e3a65822e56fea
~~~

No byte, index entry, scope plan, build output, or test result in that worktree is changed by Q0.3.
The scope becomes twenty-nine only after the authority's remote condition and separate closure,
followed by a separately authorized implementation-resume turn.

### Q0.3 conditional authority phase transition

~~~text
P4-E3-Q0.1 technical feasibility
= COMPLETE — PASS; CANDIDATE A

P4-E3-Q0.1 public-surface completion
= COMPLETE — PASS

P4-E3-Q0.2
= COMPLETE

P4-E3-Q0.3 exact-29 implementation-scope authority
= COMPLETE UPON THIS AUTHORITY COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1
  FIVE-JOB REMOTE GATE PASS

P4-E3-Q0.3 separate two-ledger closure
= READY AFTER AUTHORITY REMOTE PASS;
  NOT STARTED

P4-E3 implementation
= SUSPENDED IN EXISTING WORKTREE;
  BLOCKED UNTIL Q0.3 AUTHORITY
  AND SEPARATE CLOSURE

P4-E
= INCOMPLETE
~~~

Until this authority commit has one unique exact-SHA attempt-1 Build run whose exact five existing
jobs all complete with success, Q0.3 authority is incomplete and its separate two-ledger closure is
not ready. After that remote condition passes, the exact next work item is the separately
authorized Q0.3 two-ledger closure. This block does not perform that closure, resume or complete
implementation, pass the future fixed-heap Gate, or complete P4-E.
<!-- P4_E3_Q03_EXACT29_IMPLEMENTATION_SCOPE_AUTHORITY_COMMON_END -->
## P4-E3-Q0.4 exact-30 verifier-scope correction authority
<!-- P4_E3_Q04_EXACT30_VERIFIER_SCOPE_CORRECTION_AUTHORITY_COMMON_BEGIN -->

This synchronized documentation-only scoped amendment authorizes the final narrow verifier-scope
correction discovered during P4-E3 local qualification. It does not modify repository source,
tests, scripts, Gradle, workflow, resources, README, product behavior, or the frozen implementation
worktree. It does not resume implementation, run the fixed-heap Gate, perform the separate Q0.4
two-ledger closure, or complete P4-E.

Within this exact scope only, this block supersedes the Q0.3 statements that fixed the implementation
allowlist at twenty-nine paths, prohibited a thirtieth path, counted modified existing verifiers as
four, stated that no verifier path was added, and fixed the final P4-E3 portable qualification
matrix at eleven scripts and thirty-three invocations. Those Q0.3 statements remain historical facts
about the then-reviewed scope. Every other Q0.1／Q0.2／Q0.3, B.9, B.10, E1, E2, Candidate A,
product, test-contract, and NO SPLIT authority remains unchanged.

### Historical predecessor-verifier stop

The sole P4-E3 fixed-heap qualification invocation stopped in an existing predecessor configuration
verifier before any P4-E3 child JVM started:

~~~text
canonical command
= ./gradlew --no-daemon --no-build-cache --rerun-tasks
  --console=plain p4E3FixedHeapGate

first failing task
= verifyP4B2Configuration

scope-outside stale path
= scripts/verify-p4-b2-b-configuration.sh

scope-outside stale paths found
= exactly 1

stale assertions in that path
= exactly 3

other scope-outside tests
= 0 paths / 0 assertions

second scope-outside path
= none found

P4-E3 child JVMs started
= 0 / 5

P4-E3 runtime / Gate markers
= 0 / 6

P4-E3 runtime verdict
= NOT ADJUDICATED

P4-E3 product failure
= NOT ESTABLISHED
~~~

The three stale assertions are the former global 600-second count of nine, the former global
300-second count of seven, and the former repository-wide prohibition on every production Error
catch. This predecessor-verifier stop is a scope-model mismatch, not a P4-E3 fixture, heap,
counter, lifecycle, or product result.

### Exact thirty-path implementation allowlist

The complete exact allowlist is the Q0.3 twenty-nine-path inventory plus one and only one new path.
No prefix, wildcard, directory allowance, or warning-only unknown-path policy is authorized:

~~~text
1  src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java
2  src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java
3  src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java
4  src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.java

5  src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeTest.java
6  src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeVisibilityCompileTest.java
7  src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BCompleteHandoffTest.java
8  src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java
9  src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java
10 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java
11 src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java
12 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java

13 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupLifecycleTest.java
14 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3LeaseTerminalTest.java
15 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3ApiGateTest.java

16 src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureBuilder.java
17 src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureManifest.java
18 src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3ProbeMain.java
19 src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FileVerifier.java
20 src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/player/P4E3PlayerDataFixture.java

21 src/p4E3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupMemoryGameTests.java
22 src/p4E3GameTest/java/com/yo1no/gramarye/P4E3StartupObservationTestAccess.java

23 scripts/verify-p4-e2-configuration.sh
24 scripts/verify-p4-e1-configuration.sh
25 scripts/verify-p4-e0-r-configuration.sh
26 scripts/verify-p4-e0-r2q-configuration.sh
27 scripts/verify-p4-e3-configuration.sh
28 build.gradle
29 .github/workflows/build.yml

30 scripts/verify-p4-b2-b-configuration.sh
~~~

~~~text
production modifications       = 4
modified existing tests        = 8
new P4-E3 tests                 = 3
probe files                     = 5
custom GameTest files           = 2
modified existing verifiers     = 5
new P4-E3 verifier              = 1
build.gradle                    = 1
.github/workflows/build.yml     = 1
total                           = 30

unknown paths                   = 0
tracked fixture / resources     = 0
Gramarye.java                   = NO DELTA
codex-spec / ledger / README
  delta in implementation commit = 0

exact-30 sorted path-set SHA-256
= b0813298542a16ab120980fc7c840b6c9553224979eb9e29fce145416c29caa1
~~~

Path 30 is classified only as MODIFIED_EXISTING_VERIFIER. A thirty-first path is not authorized.

### Exact 600-second timeout topology

The former reviewed global count was nine. P4-E3 contributes exactly these three and no other
600-second routes:

~~~text
prepareP4E3Fixture
runP4E3FirstServer
runP4E3RestartServer
~~~

The resulting authority is:

~~~text
existing reviewed 600-second tasks
= 9

new P4-E3 600-second task identities
= exactly 3

new exact identities
= prepareP4E3Fixture
  runP4E3FirstServer
  runP4E3RestartServer

total 600-second tasks
= 12

unexpected additional 600-second tasks
= 0

existing P4-B timeout identities / values
= unchanged

timeout inflation outside the reviewed P4-E3 tasks
= 0
~~~

The future verifier must prove both total count twelve and the exact three-name addition. A bare
count, an at-least-twelve test, or an unrelated task that happens to use 600 seconds is insufficient.

### Exact 300-second timeout topology

The former reviewed global count was seven. P4-E3 contributes exactly these two and no other
300-second routes:

~~~text
verifyP4E3First
verifyP4E3Restart
~~~

The resulting authority is:

~~~text
existing reviewed 300-second tasks
= 7

new P4-E3 300-second task identities
= exactly 2

new exact identities
= verifyP4E3First
  verifyP4E3Restart

total 300-second tasks
= 9

unexpected additional 300-second tasks
= 0

existing P4-B timeout identities / values
= unchanged

timeout inflation outside the reviewed P4-E3 tasks
= 0
~~~

The future verifier must prove both total count nine and the exact two-name addition. A bare count,
an at-least-nine test, or an unrelated task that happens to use 300 seconds is insufficient.

### Sole reviewed production Error-catch exception

The production Error-catch contract is narrowed from global zero to exactly one already reviewed
P4-E3 observation-cleanup exception:

~~~text
production catch Error
= exactly 1 reviewed exception

owner
= SkillDefinitionStoreService

method
= private void runP4E3StartupReclaim(MinecraftServer server)

catch shape
= catch (RuntimeException | Error failure)

required terminal
= throw failure;

caught variable / rethrow variable
= failure / the same failure local

sole purpose
= allocation-free E3 startup observation abort
  followed by exact same-object rethrow

wrapper / translation
= 0 / 0

swallow / continue after catch
= 0 / 0

logging-only completion
= 0

retry
= 0

bounded-success conversion
= 0

second production Error catch
= 0

catch Throwable in SkillDefinitionStoreService
= 0
~~~

The verifier must mechanically isolate the exact owner and method body, prove that the caught and
rethrown variables are the same local, and retain zero Error catches in SkillDefinitionStore reclaim
core, the E1 audit path, and E2 login reconciliation. This amendment only synchronizes the exact
Q0.3-approved cleanup/rethrow; it does not redefine or broaden that product behavior.

### Constrained future delta in the P4-B2-B verifier

When implementation qualification is separately authorized to resume,
scripts/verify-p4-b2-b-configuration.sh may change only these three contract families:

~~~text
600-second topology
= 9 -> 12
  plus exact three P4-E3 task identities

300-second topology
= 7 -> 9
  plus exact two P4-E3 task identities

production Error catch
= global 0 -> exact one reviewed E3 cleanup/rethrow exception
~~~

The following P4-B contracts remain unchanged:

~~~text
compressed / decompressed limits
full-size fixture
first / restart semantics
invalid gzip cases
packaged runtime
heap profiles
JAR isolation
quarantine / hash preservation
all existing P4-B timeout task identities and values
~~~

Path 30 may not weaken, remove, bypass, or make warning-only any P4-B product Gate.

### Exact-30 inventory synchronization authority

After the Q0.4 authority and separate closure conditions have passed, these already reviewed paths
may synchronize their exact allowlist from twenty-nine to thirty and their modified-existing-
verifier count from four to five:

~~~text
scripts/verify-p4-e0-r-configuration.sh
scripts/verify-p4-e0-r2q-configuration.sh
scripts/verify-p4-e1-configuration.sh
scripts/verify-p4-e2-configuration.sh
scripts/verify-p4-e3-configuration.sh
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3ApiGateTest.java
~~~

The sole new allowed literal is scripts/verify-p4-b2-b-configuration.sh. Each Gate must retain:

~~~text
unknown tracked paths
= 0

unknown untracked paths
= 0

missing required paths
= 0

scripts/** or directory-prefix allowlist
= prohibited

verify-p4-* prefix allowlist
= prohibited

unknown-path warning-only behavior
= prohibited

disabled prohibited-path Gate
= prohibited
~~~

### Final P4-E3 portable qualification matrix

The final P4-E3 portable qualification matrix contains exactly the following twelve scripts. This is
the P4-E3 release matrix, not a claim that the repository contains no other phase-specific
configuration verifier:

~~~text
1  scripts/verify-p4-b2-b-configuration.sh
2  scripts/verify-p4-c2-a-configuration.sh
3  scripts/verify-p4-c2-b-configuration.sh
4  scripts/verify-p4-d1-configuration.sh
5  scripts/verify-p4-d2-configuration.sh
6  scripts/verify-p4-d3-a-configuration.sh
7  scripts/verify-p4-d3-configuration.sh
8  scripts/verify-p4-e0-r-configuration.sh
9  scripts/verify-p4-e0-r2q-configuration.sh
10 scripts/verify-p4-e1-configuration.sh
11 scripts/verify-p4-e2-configuration.sh
12 scripts/verify-p4-e3-configuration.sh
~~~

Each script must pass exactly these three modes:

~~~text
bash -n
normal PATH
PATH=/usr/bin:/bin
~~~

~~~text
scripts
= 12

modes per script
= 3

total invocations
= 36

required result
= 36 / 36 PASS
~~~

A missing listed script or an additional script formally declared mandatory for this exact P4-E3
portable matrix is a PORTABLE VERIFIER INVENTORY CONFLICT and requires a stop, not an inferred
inventory change.

### Preserved local-qualification continuity

The following results were already established before the predecessor-verifier stop and are recorded
without rerunning them in this documentation-only amendment:

~~~text
exact-29 migration
= PASS

focused compile
= PASS

focused eleven classes
= 96 / 96 PASS

affected verifier matrix
= 15 / 15 PASS

full unit
= 202 suites / 1,491 tests
  0 failure / error / skip

normal GameTest
= 12 / 12 PASS

dedicated smoke
= PASS

fixture diagnostic
= 2,048 relevant records
  65,536 raw roots
  2,049 histories
  4,096 revisions
  journal 4,096 entries / 1,048,538 bytes
~~~

The P4-E3 fixed-heap Gate is not PASS. Its five child JVMs did not start, so first/restart runtime
coordinates, the actual 25-vector, and the selected B.9 terminal were not adjudicated by that Gate.

### Anti-recursion finality

~~~text
scope-outside stale verifier paths found
= exactly 1

sole path
= scripts/verify-p4-b2-b-configuration.sh

stale assertions in that path
= exactly 3

second scope-outside path
= none found

Q0.4 scope correction
= FINAL ALLOWED SCOPE CORRECTION
  FOR THIS IMPLEMENTATION CAMPAIGN
~~~

If exact-30 qualification requires path 31 or discovers another scope-outside stale consumer:

~~~text
STOP VERIFICATION RECURSION

P4-E3 implementation campaign
= STOPPED FOR COMPLETE SCOPE-MODEL REASSESSMENT

Q0.5 incremental path repair
= PROHIBITED
~~~

### Q0.4 unchanged authority and product envelope

~~~text
Candidate A route
= unchanged

E3StartupView sealed hierarchy
= unchanged

nested E3 enums / public-final operations
= 4 / 13

package-private session controls
= 4

completed-server witness
= unchanged

clearOnServerStopped(exactServer)
= unchanged

ServerStopped order
= zero-argument E2 cleanup
  -> E3 cleanup(server)
  -> root-audit remove(server)
  -> uninstall(server)

B.9 bounded terminal observer / terminal semantics
= unchanged / unchanged

B.10 startup online count
= 0

E1 source precedence
= online > integrated > disk

E2 reconciliation
= unchanged

P4-E3 split
= NO SPLIT
~~~

~~~text
relevant_records maximum
= 2,048

raw_root_claims maximum
= 65,536

counter dimensions
= 25

DataVersion
= exact IntTag(3955)

P4-E DFU calls
= 0

fixed heap
= 1,536 MiB

first revisions reclaimed
= 1

restart revisions reclaimed
= 0

new workflow jobs
= 1
~~~

The observation seam, fixture, reclaim, dirty/save, network, filesystem, lifecycle, generation,
source precedence, memory, and workflow product contracts do not change in Q0.4.

### Frozen implementation-worktree preservation

~~~text
worktree
= /private/tmp/gramarye-p4-e3-implementation-final-20260824T051927Z

detached HEAD
= 287fecefdb77dc0399658e90d0362516aee27872

base tree
= e525ebacd4d84a695b19003bb3f9bee392a00bba

SOURCE_FREEZE_29.tsv SHA-256
= 65341c369810124af40fb78c3f02b6fa246c31d9062d428af4a3ced7dbb5131d

exact path-set SHA-256
= 28493fa89830cbe43dcb29b02418cc3f67e057970dd713b8a61c18946e7460d6

full-index tracked diff SHA-256
= c0a1d647eaa14ac26073454edab0ecf48c339351edb250d607f4e75b6dea39af

paths / unknown paths
= 29 / 0

index
= empty
~~~

This authority amendment does not modify, stage, restore, test, clean, or otherwise resume that
worktree. Its current twenty-nine-path source freeze remains the resumption input only after the
Q0.4 authority and separate closure conditions pass.

### Conditional phase transition

~~~text
P4-E3-Q0.1
= COMPLETE — PASS; CANDIDATE A

P4-E3-Q0.2
= COMPLETE

P4-E3-Q0.3
= COMPLETE

P4-E3-Q0.4 exact-30 verifier-scope authority
= COMPLETE UPON THIS AUTHORITY COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1
  FIVE-JOB REMOTE GATE PASS

P4-E3-Q0.4 separate two-ledger closure
= READY AFTER AUTHORITY REMOTE PASS;
  NOT STARTED

P4-E3 implementation
= EXACT-29 SOURCE FROZEN;
  SUSPENDED DURING LOCAL QUALIFICATION;
  BLOCKED UNTIL Q0.4 AUTHORITY
  AND SEPARATE CLOSURE

P4-E
= INCOMPLETE
~~~

Until this authority commit has one unique exact-SHA attempt-1 Build run whose exact five existing
jobs all complete with success, the Q0.4 authority is incomplete and the separate closure is not
ready. After that condition passes, the exact next work item is the separately authorized Q0.4
two-ledger closure. This block does not perform that closure, resume or complete implementation,
pass the P4-E3 fixed-heap Gate, or complete P4-E.
<!-- P4_E3_Q04_EXACT30_VERIFIER_SCOPE_CORRECTION_AUTHORITY_COMMON_END -->
