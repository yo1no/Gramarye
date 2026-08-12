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
read-only review blocked，closure後只重開review，implementation維持
`NOT STARTED`。P4-E1後來已`COMPLETE`；前次P4-E2 read-only review實際已依Stop
Rule停止於`PRODUCTION TRIGGER / OWNER AUTHORITY GAP`，因read-only不得修改文件而留下
stale ledger `OPEN`。P4-E0-B.5只固定E2 production construction phase、Store lifecycle
owner／exact identity、sole login handler與recovery→E2 ordering、E2 active-wiring closure條件、E3
same-instance reuse；不裁決recovery outcome admissibility或atomic reconciliation final design。B.5 authority／closure
完成前當前E2 review為`BLOCKED`，E2 implementation為`NOT STARTED`且E3為`BLOCKED`；
closure後只從clean HEAD重開E2 read-only review，不直接核准implementation。
E0-B／B.1／B.2／B.3／B.4／B.5不寫Java／Gradle／CI，不重跑R1／R2／R2Q formal study。

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
  以同generation／backing開active lease；open／close不增加、active lease阻止audit／E2，close不重發
  permit。`removeServer`強制清lease／backing／permit／slot且不增加generation；新exact server object
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
  failure沿用Unavailable／`setDirty(false)`。Audit／index／Incomplete／E2不改Store dirty。
- 在既有stop lifecycle中，只以上述exact same service field對exact server呼叫
  `removeServer`；該操作只清該identity-isolated slot，不銷毀、替換或重建service owner。
- 建立production-shaped exact fixed `-Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError`
  first／restart Gate，同時保留4,096 directory entries、2,048 records、25 exact maxima、
  1,024 full P4-C admissions、65,536 accepted raw roots、4,096 journal targets、full Store／carrier、
  grouped audit、index、Complete、filtered carrier與SavedData deep copy。R2Q不取代此Gate，
  也未自然涵蓋integrated runtime alias path；E3必須實際加入integrated-owner path，或提供
  reviewed machine-checked domination proof。Snapshot取代同owner disk source，不同時hydrate兩份
  player tree，不建立second whole-tree copy。R2Q也未自然執行online `ServerPlayer` path；同一
  envelope須actual執行online Missing＋Ready、source exclusion與initial／final witness，或提供
  reviewed machine-checked domination proof並另跑actual online freshness runtime test。兩種方案
  都要維持relevant 2,048與raw roots 65,536 exact，不能因online多數counter為+0而略過。

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
serializer／copy／re-encode，停止E1。若E3 exact profile的
disk／integrated path或online obligation在1,536MiB OOME／timeout，停止並先修訂P4-0／第18號heap-floor
authority，不縮fixture／ceilings、拆envelope或自行提heap。

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
  reconciliation與fresh Complete→single controlled reclaim；integrated runtime alias path必須直接測試或有
  reviewed machine-checked domination proof，online path必須actual執行或有domination proof加actual
  freshness test。不宣稱playerdata／SavedData cross-file
  durable atomicity，也不宣稱1,536 MiB是universal safe minimum。
- P4-E2首次建立且`SkillDefinitionStoreService`以exact-one `final` field長期持有
  production audit service；sole recovery login handler在P4-D recovery後同步呼叫已綁定exact
  Store／audit identity的E2 dependency，且production wiring已納入E2 closure。P4-E3只重用同一
  instance作fresh audit／reclaim／exact-server slot removal，無第二constructor或login listener。

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
      可使用fresh Complete。P4-E1已closure；前次E2 review因production trigger／owner
      authority gap停止，P4-E0-B.5 commit／push／remote closure前當前E2 review blocked且
      implementation為`NOT STARTED`，closure後只從clean HEAD重開review。E2首次接入且
      Store service exact-one-final owns production audit service；sole recovery login handler在P4-D
      recovery後同步呼叫exact-identity-bound E2 dependency，active wiring為E2 closure條件；
      E3只重用same instance作fresh audit／reclaim／stop removal，constructor／login delta 0；
      exact 1,536-MiB production Gate未通過前P4-E不完成。
- [ ] 新生命週期有清理與 idempotence。
- [ ] `/skill trace` 可解釋失敗。
- [ ] dedicated server 無 client class。
- [ ] 文件與 ADR 更新。
