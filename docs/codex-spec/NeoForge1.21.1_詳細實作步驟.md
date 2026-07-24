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
P4-B：saved_data_schema_version、唯一Overworld Skill Store SavedData outer carrier／lifecycle、
      bounded ingress、Ready／Quarantined、使用A3 primitives的live carrier publication、save
      callback與dirty
P4-C：獨立player skill Attachment、Draft／latest／equipped／editor persistence、
      total serializer、migration與clone policy
P4-D：authenticated composition、調用A3 prospective Store builder、prospective journal、
      commit-oriented preflight、P3-D commit、carrier／journal publication、Attachment transition
      與crash recovery
P4-E：offline roots、rebuildable index、reconciliation與reclaim composition
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

External roots由P4-E complete offline audit提供player latest／equipped、pending journal與每一個
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

Composition facade留給P4-D：authenticated principal → fresh authorization → 一份immutable
quota snapshot → P3-C prepare → persistence／journal preflight → P3-D commit → Attachment
transition。Facade不把Player／ServerPlayer傳入domain API。

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

P4-A1～A3與P4-B～E不得重寫P3-D owner truth、quota counting、CAS、revision allocation或reclaim
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

## P4-B：Overworld SavedData adapter

### 責任

- 建立`saved_data_schema_version`與SavedData outer carrier；outer migration完成後將
  `store_blob`委派給P4-A2 load，不重寫Store／History／Revision encoding。
- Stable name固定`gramarye_skill_definitions`；唯一位置是Overworld data storage，其他維度
  取得同一adapter，不使用static singleton。
- 專用one-time loader先區分primary file absent／present，執行compressed-file size bound、
  bounded NBT decompression、SavedData outer migration與P4-A2 load pipeline。標準fail-open
  `computeIfAbsent`不得直接作
  deserializer入口。
- Absent file建立empty Ready且不dirty；existing invalid file安裝non-dirty Quarantined，
  不建立empty Store、不覆寫原檔、不自動提升`.dat_old`。
- Ready同時持有domain Store、matching immutable encoded carrier、pending journal carrier與
  rewrite-pending state。Live carrier ownership／publication與save callback屬P4-B；使用P4-A3
  pure primitives，且save callback只寫prebuilt carrier。
- 落實完整dirty matrix與migration成功後canonical rewrite pending。

### Gate

- File／decompressed carrier exact／+1、Overworld singleton、non-Overworld same adapter、
  absent／invalid分流、dirty matrix、primary invalid不提升`.dat_old`、restart round-trip。
- Async save語意只承諾in-memory committed + persistence scheduled，不宣稱fsync durable。

### 禁止

Player Attachment、submission composition、重寫Store／History／Revision encoding、在save
callback首次執行一般Codec。

## P4-C：Player Attachment

### 責任

- 建立獨立永久`gramarye:player_skills` Attachment V0，保存bounded Draft、latest state
  （含mutation generation）、equipped slots與editor metadata；owner由authenticated player
  UUID導出，不持久化。
- Disk collection使用List保留duplicate corruption；custom total serializer回
  Ready／Quarantined。Missing tag才建立empty Ready，existing malformed tag不得視為missing。
- Draft使用獨立adjacent migration；Attachment總byte ceiling優先於per-Draft ceiling。
- 使用immutable replacement `setData`；永久資料serialize + `copyOnDeath`，End return不得
  double-copy；不自動sync。

### Gate

- Empty default、Draft／latest／equipped／editor round-trip、Draft migration、duplicate／stale／
  bounds、missing／malformed分流、death／keepInventory／End／logout-login、generation exact／
  overflow與no auto sync。

### 禁止

Store commit composition、revision allocation、Store owner/latest覆寫。

## P4-D：Submission composition

### 責任

- Facade位於submission package，從authenticated server principal導出`SkillOwnerId`，從own
  Ready Attachment取得Draft，讀current Store owner/latest，只取得一次immutable quota
  snapshot，再重用package-private C1～C4 preparation。
- Preparation後fresh owner/state recheck；調用P4-A3 pure prospective Store carrier builder，
  建立prospective journal replacement，並在live Store mutation前完成全部revision／history／
  Store／journal／carrier capacity preflight。
- 用同一quota snapshot呼叫P3-D commit。只有`Committed`才發布預建carrier／journal、dirty，
  再執行Attachment immutable transition。
- 建立獨立composition outcome；不得把`SkillSubmissionOutcome.Prepared`當Committed。取得
  preparation report後，後續outcome保留同一warning-only report reference。
- Store-first bounded generation journal記錄expected／target pointer transition；in-memory
  `setData`後不立即清除，等startup／login persisted playerdata readback確認後才清除。
- Crash recovery replay idempotent；pending target是external retention root。

### Gate

- Fresh authorization、quota只取一次、SkillIdSource null fail-fast、preparation／commit conflict
  分離、preflight failure零Store mutation、opaque owner rejection、success／Attachment failure、
  journal cap與所有crash windows、readback-confirmed clear、report identity。

### 禁止

Network、接受client owner／authorization／quota／revision／Store state、Attachment-first ordering、
重寫P4-A2 Store／History／Revision encoding。

## P4-E：Offline roots 與 reconciliation

### 責任

- Bounded read-only audit涵蓋offline player latest／equipped、pending journal與所有已啟用的
  future SkillInstance／Marker／Construct／Schedule persistent source family。
- Rebuildable world-level index不是truth；restart預設Incomplete，只有全部enabled source
  family audit完成才Complete。Unreadable／truncated／unknown source一律Incomplete。
- Store owner／documents裁決pointer reconciliation；missing／owner mismatch做opaque prune，
  無journal舊pointer不自動升latest，orphan revision不自動刪除或釋放quota。
- Root capture最多MAX+1並與reclaim位於同一logic-thread call chain；Complete不跨tick保存。
- Reclaim依P3-D既有規則；只改Attachment不dirty Store，改journal或實際reclaim才依matrix
  dirty。

### Gate

- Offline roots、loaded-only→Incomplete、unreadable／future-source incomplete gate、MAX+1、
  journal roots、missing Store root reconciliation、no chunk load、reclaim Rejected／0／>0 dirty
  mapping與Complete不得跨tick重用。

### 禁止

Chunk force、background sweep、Incomplete best-effort reclaim、重寫P3-D reclaim policy。

Marker gameplay Attachment、`RuntimePersistentStore`、SkillInstance、Schedule與Construct lifecycle
不由P4-A～E提前建立。P4-E只定義這些未來persistent root source的completeness gate；實際
source implementation仍留各自後續工程階段。

## P4 Definition of Done

- P4-A1～A3與P4-B～E各自gate及
  [18號修正案required tests](18_P4持久化與組合修正案.md#21-required-tests)通過。
- `SkillDefinitionStore`只有一份domain truth；Overworld adapter、player Attachment與journal
  各自遵守其單一真相／derived data邊界。
- Existing invalid data不silent empty，所有load failure不partial、不dirty、不覆寫原檔。
- Store-first ordering、readback-confirmed journal clear與offline-root fail-closed均可由測試證明。

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
- [ ] Existing malformed persistence不等同missing；load failure不安裝partial／empty truth。
- [ ] 一般可失敗encode與byte-capacity checks在truth mutation前完成，save callback只寫
      prebuilt carrier。
- [ ] Cross-location update有明確ordering、bounded recovery journal與reconciliation；不宣稱
      fsync或durable atomic。
- [ ] Persistent roots只有在offline與所有enabled source family完整時才可Complete；不完整時
      reclaim fail closed。
- [ ] 新生命週期有清理與 idempotence。
- [ ] `/skill trace` 可解釋失敗。
- [ ] dedicated server 無 client class。
- [ ] 文件與 ADR 更新。
