# Codex 實作總規格 Prompt
## Minecraft 1.21.1 / NeoForge 21.1.x 魔法 Node 系統

> 使用方式：將本檔與 `18_P4持久化與組合修正案.md`、`17_P3資料模型修正案.md`、
> `16_骨架定案清單_NeoForge1.21.1_凍結版.md` 一起交給 Codex。
>
> 本檔是實作契約；18號修正案只在明示的P4 persistence／Attachment／composition範圍內
> 優先，17號修正案只在明示的P3範圍內優先，其他範圍以凍結版骨架為架構真相。若文件
> 尚未同步而存在實質衝突，停止實作並回報，不得自行選邊或改變骨架。

---

# 1. 你的角色

你是此 Minecraft NeoForge 模組的資深 Java／NeoForge 實作者。

你的任務不是重新設計系統，而是依照已凍結的骨架：

1. 檢查現有 repository。
2. 建立可編譯、可測試、可逐步擴充的地基。
3. 依階段完成最小垂直切片。
4. 保持資料真相、序列化邊界、執行緒所有權、網路信任與效果管線不變量。
5. 每次只完成目前指定階段，不提前大量加入技能內容。

不得自行增加大型框架、跨載入器抽象、ECS、腳本語言、反射式自動註冊或未經要求的第三方依賴。

---

# 2. 平台與建置硬限制

- Minecraft Java Edition：`1.21.1`
- NeoForge：`21.1.x`
- NeoForge 最低允許版本：`21.1.229`
- Java Toolchain：Java 21
- 單載入器：NeoForge
- Mojang mappings
- 使用 repository 已選定的官方 NeoForge MDK／ModDevGradle 方式
- 不使用 Architectury
- 不支援 Fabric 或舊 Forge
- 不使用動態版本，例如 `latest`、`+`
- mod id：`gramarye`（已凍結，不得更改）
- 資料 namespace：`gramarye`（已凍結，不得更改）
- Java package root：從 repository 根目錄 `AGENTS.md` 的 `JAVA_PACKAGE_ROOT` 讀取；不得由 mod id 自動猜測
- 若 package root 尚未填寫，停止建立 Java source，先回報缺少 `JAVA_PACKAGE_ROOT`

開始前必須先執行：

```bash
./gradlew --version
./gradlew compileJava
./gradlew test
```

若 repository 尚未初始化，先建立最小 NeoForge 1.21.1 專案，再開始其他工作。

---

# 3. 工作方式

## 3.1 先檢查，不盲目覆寫

開始每個階段前：

1. 列出相關檔案。
2. 閱讀 `build.gradle`、`gradle.properties`、`neoforge.mods.toml`、主 mod class 與現有測試。
3. 確認目前 package、mod id、版本與註冊模式。
4. 找出已有但可能重複的元件。
5. 提出本階段的檔案變更清單。
6. 再開始修改。

不要重建已存在且符合規格的程式。

## 3.2 小步提交

每個工作單元應：

- 聚焦一個責任。
- 能獨立編譯。
- 附測試或最低驗證。
- 不混入格式化整個 repository。
- 不做與本階段無關的重構。

建議 commit 粒度：

```text
build: lock NeoForge 1.21.1 toolchain
feat(data): add typed runtime identifiers
feat(skill): add immutable skill definition model
feat(runtime): add persistent runtime store
feat(event): add internal event queue
feat(effect): add request-resolve-commit pipeline
test(skill): verify unknown action payload preservation
```

## 3.3 遇到不確定 API

只查：

- NeoForged 官方 1.21.1 文件
- NeoForge 1.21.1 source／Javadocs
- Mojang 1.21.1 source
- repository 中目前鎖定版本的 API

不要根據其他 Minecraft 版本直接猜 API 名稱。

如果官方文件與實際依賴版本 API 不一致，以已鎖定依賴的 source／編譯結果為準，並記錄差異。

---

# 4. 不可違反的架構不變量

## 4.1 單一真相

每一類資料只能有一個持久化真相：

| 型別 | 真相 |
| --- | --- |
| Skill owner binding與retained SkillDocument revision | P3-D pure-Java `SkillDefinitionStore` aggregate；P4 Overworld SavedData為唯一persistence adapter |
| SkillInstance | Overworld `RuntimePersistentStore` SavedData |
| 生物／造物標記 | 錨點 Entity Attachment |
| 位置標記 | RuntimePersistentStore |
| Construct | Construct Entity 自身持久資料 |
| Schedule | RuntimePersistentStore |
| Mana | 玩家 Attachment |
| Cooldown | 玩家 Attachment |
| 主動接續指標 | 玩家 Attachment 中的指標，非 SkillInstance 真相 |

中央索引只能是可重建索引，不能與真相形成雙寫。

P4 的 `gramarye_skill_definitions` 只持久化 P3-D Skill Store 與 pending Attachment update
journal；不承載 `RuntimePersistentStore`。Player Draft／latest／equipped／editor truth 位於獨立
`gramarye:player_skills` Attachment。P4-B SavedData中的exact opaque pending blob是唯一persistent
pending-transition truth；P4-D decoded journal state只是與該blob identity-bound的derived operational
view，encoded Store carrier只是derived save representation。三者都不是第二份Store domain truth。

## 4.2 不可變技能 revision

- `(SkillId, SkillRevision)` 唯一定位不可變 `SkillDocument`。
- 玩家提交編輯時建立新 revision 並寫入 `SkillDefinitionStore`。
- P3-C 的 Prepared `SkillSubmissionPlan` 只包含 proposed revision；P3-D atomic compare-and-insert 成功後，該 revision 才正式配置並存在。
- P3-D Store 的 immutable owner binding與retained `SkillDocument` entries是唯一domain truth；latest、owner count與allocation counter不是第二真相，第一版不建立這些indices／counter。
- 配置下一版所使用的 latest truth 是 Store 最大retained revision key；玩家 Attachment 的 latest pointer 只是玩家引用，不是owner或allocator truth。
- 未提交工作副本不可施放、不可落庫為正式 revision。
- 施放只 pin revision，不重寫定義。
- 進行中實例永遠解析固定 revision。
- retained history可稀疏；最大retained revision是implicit root，一般reclaim不得移除。只有無玩家／persistent root與active pin的non-latest revision可回收，且old revision reclaim不降低distinct-skill quota。
- 移除Attachment引用不等於Store retire，也不釋放quota；正式retire需要未來獨立operation與persistent tombstone／等價no-reuse truth。

## 4.3 效果管線

所有玩法效果必須經：

```text
ActionExecutor
→ EffectRequest
→ EffectResolver
→ EffectCommitPlan
→ EffectCommitter
→ EventEmitter
```

Action 禁止直接：

- `Entity#hurt`
- 建立爆炸
- `addEffect`
- 修改方塊
- 修改魔力
- 發送未驗證玩法結果

## 4.4 魔力守恆

所有魔力變化經 `ManaTransactionService`。

- 技能不得替自己創造魔力。
- 分享魔力不得產生額外魔力。
- 分裂、連鎖、重複不得複製分享量。
- 道具回復、自然回復、技能消耗與分享都有 reason code。
- 不允許直接 set mana。

## 4.5 伺服器權威

- 客戶端只送 Intent。
- 客戶端不能指定傷害、Action 參數、完整目標清單、技能 revision 或魔力結果。
- 伺服器依自己的技能定義、玩家狀態、視線、距離與世界狀態重建操作。
- 所有世界與 runtime state 修改只在伺服器主執行緒進行。

## 4.6 事件不得同步遞迴

新的模組事件一律進中央佇列。

禁止在目前 Java call stack 中由：

```text
Commit → Event → Trigger → Commit → Event
```

無限制遞迴。

## 4.7 表現與玩法分離

表現層只能決定：

- 顏色
- 音效
- 粒子
- 尾跡
- 純顯示強度

不得改變：

- 命中
- 傷害
- 範圍
- 魔耗
- 冷卻
- Trigger
- 伺服器效果結果

外觀解碼失敗必須 fallback，不得讓技能玩法定義損壞。

---

# 5. 建議 package 結構

請依 repository package root 調整，不要硬編碼 `com.example`.

```text
<root>.magic
├─ api
│  ├─ id
│  ├─ registry
│  └─ capability
├─ definition
│  ├─ model
│  ├─ envelope
│  ├─ codec
│  ├─ validation
│  ├─ migration
│  └─ store
├─ runtime
│  ├─ instance
│  ├─ context
│  ├─ scheduler
│  ├─ index
│  └─ persistence
├─ trigger
│  ├─ type
│  ├─ matcher
│  └─ builtin
├─ action
│  ├─ type
│  ├─ executor
│  └─ builtin
├─ effect
│  ├─ request
│  ├─ resolve
│  ├─ plan
│  ├─ commit
│  └─ result
├─ event
│  ├─ model
│  ├─ bridge
│  ├─ queue
│  └─ dispatch
├─ mana
│  ├─ model
│  ├─ transaction
│  └─ attachment
├─ marker
│  ├─ model
│  ├─ attachment
│  └─ index
├─ network
│  ├─ payload
│  ├─ codec
│  ├─ handler
│  └─ sync
├─ presentation
│  ├─ definition
│  ├─ profile
│  ├─ event
│  ├─ budget
│  ├─ network
│  └─ client
├─ command
├─ config
└─ test
```

這是責任分區建議，不要求為每個目錄建立空類別。

禁止建立萬能 `MagicManager`、`Utils` 或數千行 `SkillEngine`。

---

# 6. 核心資料型別規格

## 6.1 型別化 ID

至少建立不可變 record：

```java
public record SkillId(UUID value) {}
public record SkillRevision(int value) {}
public record SkillInstanceId(UUID value) {}
public record MarkerInstanceId(UUID value) {}
public record ConstructInstanceId(UUID value) {}
public record ScheduleId(UUID value) {}
public record EventId(long value) {}
```

要求：

- constructor 驗證 non-null／非負值。
- 提供 Codec。
- 需要網路同步的才提供 StreamCodec。
- API 不接受語意不明的裸 UUID。
- `SkillRevision` 範圍固定為 `0..Integer.MAX_VALUE`，canonical JSON 為普通整數。P3-D1 additive建立唯一 `SkillRevision.successor()`，建議回傳`Optional<SkillRevision>`，由P3-C3與P3-D2共用；MAX回empty且不表示allocation。
- P3-C只提出revision，authoritative latest為MAX時形成既有preparation `RevisionExhausted`。合法Plan不含`ExpectedLatest(MAX)`，因此normal Store commit result無`RevisionExhausted`；Plan後actual latest前進MAX依CAS回`LatestMismatch`。正式allocation只有P3-D commit成功，不得overflow、wrap或重用。

## 6.2 SkillDocument 與 ValidatedSkillDefinition

持久化文件最小欄位：

```java
record SkillDocument(
    int schemaVersion,
    SkillId skillId,
    SkillRevision revision,
    List<NodeDocument> nodes,
    AppearanceDocument appearance
) {}
```

要求：

- 完全不可變。
- `List.copyOf`.
- Node 數有 decode 與 validation 上限。
- 建構時不偷偷執行 migration。
- migration 在獨立流程完成。
- Appearance 可 fallback，但玩法 Node 解析不可被外觀錯誤連坐。
- `SkillDocument` 只持久化 `DefinitionEnvelope`，不保存 registry descriptor、Resolved／Unknown union、validation issue 或 runtime cache。
- P3-B 才由 `SkillDocument` 建立 transient resolved／unknown candidate，完成驗證後產生 `ValidatedSkillDefinition`。
- Runtime API 只接受 `ValidatedSkillDefinition`；此 projection 不持久化且可重建。
- 不建立讓兩者同時實作的寬鬆 `SkillDefinition` runtime 介面。

## 6.3 NodeDocument

```java
record NodeDocument(
    DefinitionEnvelope trigger,
    DefinitionEnvelope action,
    AppearanceOverrideDocument appearanceOverride
) {}
```

要求：

- `NodeDocument` 不保存 index；`SkillDocument.nodes` 的 List position 是該 revision 內唯一的零起算 `nodeIndex`。
- persistence 與 runtime 使用零起算 index，UI 顯示 `index + 1`。
- Node 只能引用更小 index，驗證留給 P3-B。
- 不建立 `NodeId`。
- UI Node 1（`nodeIndex = 0`）不可選來源依賴 Action。
- 一個 Node 恰有一個 Trigger 與一個 Action。
- 存在 Unknown classification 時，編輯器不得重排無法安全更新隱藏 reference 的 Node。

## 6.4 DefinitionEnvelope

```java
record DefinitionEnvelope(
    ResourceLocation typeId,
    int schemaVersion,
    Dynamic<?> rawPayload
) {}
```

實際 Java 表達可依 Codec 可行性調整，但必須達成：

1. 先保存 type ID 與 raw payload。
2. 再嘗試解析。
3. 未知型別能完整 round-trip。
4. 錯誤不刪除原始資料。

P4 physical storage 對每個 Trigger／Action payload 與每個 Unparsed appearance subtree 個別
使用 `RawTreeEnvelope`。同一 document／node 可混合 JSON 與 NBT；Unknown preservation 是
same-family structural guarantee，不得以whole-document family tag或跨family convert取代。

`SkillMigrationPlan` 的權威輸入是 logical `SkillDocument` outer schema，不是
physical `RawTreeEnvelope` tree。`SkillMigrationStep` 只處理明文屬 skill-level schema
的 document／node outer fields；不得讀取、遍歷、比較、hash、修改或依賴
DefinitionEnvelope payload、Unparsed appearance raw slot、raw family／registry context／
compressed-maps state 或 token sentinel。DefinitionEnvelope `type`、payload `schema_version` 與
opaque slot 都必須保持不變。

P4 以 logical tokenized conformance view 實現此模型：保留 outer fields 與 envelope
shell，將 logical payload／raw root 替換為 sentinel，family、context 與 exact bytes 只存
side table。`resolveFromRaw` 仍是正式 P3-B2 raw-ingress pipeline；P4 因 mixed-family
表示需求不呼叫它，而是以同一 `SkillMigrationPlans.production()` 運行 conformance
view。合規 production step 必須對 JSON／NBT／RegistryOps／tokenized representation
產生相同 outer output 與 facts。

下列 definition union 是 registry resolution 後的 transient classification，不是 `SkillDocument` 的持久化欄位：

```java
sealed interface TriggerDefinition permits ResolvedTriggerDefinition, UnknownTriggerDefinition
sealed interface ActionDefinition permits ResolvedActionDefinition, UnknownActionDefinition
```

P3-B 固定流程：

```text
SkillDocument DefinitionEnvelope
→ registry resolution
→ transient resolved/unknown candidate
→ validation
→ ValidatedSkillDefinition
```

## 6.5 SkillDraft

`SkillDraft` 是業務上可編輯、Java instance 上不可變的 snapshot。P3-A 建立正式 Draft Codec，並以明確 Missing／Present slot 表達不完整 Trigger 與 Action，不使用 `null`。

- Draft top-level 外觀直接使用 `AppearanceDocument`，Draft Node 使用 `AppearanceOverrideDocument`；不建立 `DraftAppearance`。
- Draft 持有候選 `SkillId`；P3-C 定義 server-side mint contract 與 authoritative submission precheck input。P3-A 不提供 production random UUID factory，client 也不得鑄造正式 SkillId。
- transient mint grant 不是跨重啟 submission credential；submission authorization 每次使用當下 authoritative snapshot，identity rejection 不得暴露未授權技能存在性、latest revision 或 owner。
- Optional `baseRevision` 只是 optimistic concurrency metadata，不是 Draft 的正式 revision。
- Draft 可為空、不完整或暫時不合法，不得直接施放或冒充 `SkillDocument`。

## 6.6 Context

```java
record SourceContext(
    UUID casterId,
    SkillId skillId,
    SkillRevision revision,
    SkillInstanceId skillInstanceId,
    int rootNodeIndex,
    OriginKind originKind
) {}
```

```java
final class EffectState {
    // 有界欄位與明確 copy/derive 方法
}
```

```java
record EventContext(
    EventId eventId,
    long sequence,
    long gameTick,
    ResourceKey<Level> dimension,
    Vec3 position,
    Vec3 direction,
    AnchorRef source,
    Optional<AnchorRef> target
) {}
```

不得把 live `Entity`、`Level` 寫入持久化 Context。

---

# 7. Registry 規格

建立 Trigger 與 Action custom registry。

每個 descriptor 至少提供：

```java
interface TriggerType<P> {
    ResourceLocation id();
    MapCodec<P> payloadCodec();
    TriggerCapabilities capabilities();
    DataResult<ValidatedTrigger<P>> validate(P payload, ValidationContext context);
    TriggerMatcher<P> matcher();
}
```

```java
interface ActionType<P> {
    ResourceLocation id();
    MapCodec<P> payloadCodec();
    ActionCapabilities capabilities();
    DataResult<ValidatedAction<P>> validate(P payload, ValidationContext context);
    ActionExecutor<P> executor();
}
```

實際泛型可調整以符合 Java 與 Codec，但必須保持：

- descriptor singleton
- payload 是玩家技能資料
- capability 可機器讀取
- UI 與 server validator 共用能力資料
- 不以大量 `instanceof` 判斷核心規則

`ActionCapabilities` 至少描述：

- source requirement
- target requirement
- outputs
- splittable／chainable／repeatable
- modifies blocks
- transfers mana
- persistent
- requires live entity
- control class
- appearance parameter bounds

---

# 8. SavedData 與 Attachment 規格

## 8.1 SkillDefinitionStore

P3-D 建立production pure-Java aggregate，不是interface-only、test-only Map或第二個persistent adapter：

```text
SkillDefinitionStore
└─ SkillId → active StoredSkillHistory

StoredSkillHistory
├─ immutable SkillOwnerId owner
└─ immutable retained SkillRevision → SkillDocument
```

owner binding與retained documents是唯一domain truth。`SkillDocument`是revision內容唯一真相；committed document不可覆寫／修改。Store不保存`ValidatedSkillDefinition`、Plan、Outcome、ValidationResult或Draft。Latest由retained keys最大值推導；latest index、skillsByOwner、owner count、revision count與allocation counter只能可重建，P3-D1／D2第一版不建立。

最小read責任是固定revision lookup、latest reference、owner lookup與由owner bindings推導的committed-skill count；不得回傳mutable internal Map或在getter中修改state。P3-D另提供只含active SkillId、owner與retained documents的detached immutable snapshot／validated restore seam；snapshot不是第二truth且無Codec。

### P3-D thread與atomic contract

- Store由server logic thread confinement使用，不承諾任意Java threads間linearizability，不使用lock／`synchronized`，也不依賴Minecraft thread API檢查caller；misuse是programming-contract violation。
- Authoritative quota admission、owner check、ExpectedAbsent／ExpectedLatest CAS、technical capacity checks與revision insert必須位於同一aggregate method。
- 單次method在第一個truth mutation前完成所有typed-failure checks；failure時owner/history observable state完全不變。
- 完整replacement history與success result先建立，再以一次outer-map insert／replace發布；mutation後不呼叫provider、Codec、Reader、Writer、validator或其他可正常失敗callback。
- 不承諾OOME、VM `Error`或任意`Error` rollback；不宣稱database transaction或Store／Attachment跨位置原子。

### Quota與technical capacity

```text
SkillQuota
├─ Unlimited
└─ Limited(maxCommittedSkills)
```

- Unlimited只表示無額外per-owner policy quota，不突破hard ceilings。
- Limited範圍是`0..MAX_COMMITTED_SKILLS_PER_OWNER`；0表示拒絕所有New。
- Quota計算per-owner distinct active committed SkillId／owner binding，不計revision、Attachment pointer、Draft或pin。
- New成功+1；Existing、failed commit與old revision reclaim不變。Quota只在確認history absent後檢查；Existing不因policy降低而被拒絕。
- 每個composition attempt在P3-C前從唯一provider恰取得一次combined immutable policy snapshot；final fresh
  reauthorize／identity check只驗證同一snapshot與prepared identities，不重新snapshot。Store mutation內
  也不呼叫policy provider。
- 正式retire／quota release出現前預設policy是Unlimited；主動使用Limited的伺服器必須接受目前沒有release workflow。

Hard ceilings固定為：

```text
MAX_COMMITTED_SKILLS_PER_OWNER = 256
MAX_COMMITTED_SKILLS_GLOBAL = 4096
MAX_RETAINED_REVISIONS_PER_SKILL = 128
MAX_RETAINED_REVISIONS_GLOBAL = 32768
MAX_RETENTION_ROOTS_PER_RECLAIM = 65536
```

後續Java常數放在`MagicSafetyCeilings`或唯一canonical位置。Count limits不取代P4 encoded-byte bounds。Policy `QuotaRejected`與technical `CapacityRejected`分離；capacity scope固定為`OWNER_SKILL_HISTORIES`、`GLOBAL_SKILL_HISTORIES`、`SKILL_RETAINED_REVISIONS`、`GLOBAL_RETAINED_REVISIONS`，其current／maximum非負且current至少為maximum。

### Owner、CAS與result

每個active SkillId恰有一個immutable owner。New以同一replacement建立owner與revision 0；Existing stored owner必須等於Plan owner且不可透過submission轉移。Mismatch回`OwnerRejected(SkillId)`，不保存actual owner或observed latest；composition對外映射opaque rejection。

CAS precedence：

```text
ExpectedAbsent:
programming invariants
→ history present: ExpectedAbsentButPresent
→ per-owner history capacity
→ global history capacity
→ policy quota
→ global retained-revision capacity
→ build replacement
→ single insert

ExpectedLatest:
programming invariants
→ history absent: ExpectedLatestButAbsent
→ owner mismatch: OwnerRejected
→ actual latest mismatch: LatestMismatch
→ successor/proposed-revision invariant
→ per-skill retained-revision capacity
→ global retained-revision capacity
→ build replacement
→ single replace
```

Owner mismatch早於latest mismatch。正常commit vocabulary：

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

不重用P3-C conflict，不保存Plan／document／definition／report／actual owner／任意message。合法Plan不含`ExpectedLatest(MAX)`，正常commit result無`RevisionExhausted`；race到MAX是`LatestMismatch`，非法Plan在successor arithmetic前fail fast。相同Plan第二次回typed conflict；無AlreadyCommitted、attempt UUID、retry cache、document-equality idempotence或automatic retry。

### Retention與retire

- New從0開始，Existing使用唯一`SkillRevision.successor()`；committed revision不覆寫。
- Retained history可稀疏；gap不是corruption。最大retained revision是implicit root，一般reclaim永不移除，因此max key不倒退且不需highest counter。
- Mark roots是每個history的implicit latest、P4提供的complete external persistent roots與active in-memory pins。只有無root／pin的non-latest可回收。
- Pin missing不建立ghost root；multiple handles分別計數，double-close idempotent。Pins不持久化，restart後由authoritative roots重建。
- External roots超過65536、截斷或不完整時fail closed，整輪不sweep。Report只保存bounded counts；reclaim不移除owner或降低quota。
- P3-D1～D3不實作whole SkillId delete／retire／owner removal／tombstone／quota release。移除Attachment引用不等於retire。未來retire需獨立operation並保存persistent tombstone或等價no-reuse truth，另立scoped amendment。

### P4 persistence／Attachment／composition boundary

P4 的完整 physical schema、per-raw-subtree `RawTreeEnvelope`、hard byte ceilings、load failure、
Attachment、journal、reconciliation及offline-root contract以
[`18_P4持久化與組合修正案.md`](18_P4持久化與組合修正案.md)為準。

P4固定拆分為：

- P4-A1：`SkillOwnerId` canonical UUID Codec、family/context判定、bounded raw Codec、
  `RawTreeEnvelope`、mixed-family current document bridge、appearance mapping與shared logical
  bounds；無Store schema／migration／carrier／SavedData。
- P4-A2：`store_schema_version`、Store／History／Revision physical schema與三層byte ceilings、
  Store physical migration、logical opaque-token conformance migration、migration before
  hydration、current snapshot／P3-D restore、bounded facts與current Store blob encode／load；無
  `saved_data_schema_version`／carrier／journal／commit preflight／heap probe。
- P4-A3：pure immutable hierarchical Store carrier rebuild／replacement／reclaim filtering、
  checked totals與64 MiB fixed-heap validation；無lifecycle／publication／dirty／commit／journal／
  Attachment／composition。
- P4-B1：`saved_data_schema_version`、whole-root／inner exact framing、zero-length no-journal
  sentinel、bounded opaque pending blob、outer migration、bounded decompressed carrier、P4-A2
  load＋P4-A3 rebuild與Ready candidate；無world／filesystem／cache lifecycle。
- P4-B2：primary file ingress、strict single-member gzip、custom one-time load、
  Ready／Quarantined／Unavailable SavedData adapter、唯一Overworld cache install、live
  Store／carrier ownership、save callback、controlled read／pin／reclaim、dirty與fixed-heap load／save
  Gate；無Player Attachment、journal domain、submission composition或offline root collection。
- P4-C0：只修訂player Attachment totality、bounded raw與destructive oversize quarantine權威政策。
- P4-C1：physical V0、total serializer、bounded counting、Ready／PreservedRaw／OversizeMarker、
  Draft persistence／三軸migration、exact bounds與prebuilt Ready carrier；無registration／lifecycle service。
- P4-C2：Attachment registration、immutable `setData` service、唯一int generation transition、
  death／End、P4-D transition seam、P4-E bounded per-player root projection、GameTests／fixed-heap／
  phase gates；無Store commit composition。
- P4-D0：documentation-only journal framing／availability、policy ownership、composition與combined
  memory authority。
- P4-D1：strict journal／migration、single Store authority snapshot、窄Store submission port、
  prospective Store／journal preflight、opaque commit handle、publication與journal roots。
- P4-D2：unique policy／SkillId providers、Draft creation、authenticated exactly-once P3-C
  composition、prepared Attachment transition與composition outcome。
- P4-D3：bootstrap／login recovery、persisted-readback clear、paired restart與combined fixed-heap Gate；
  無offline enumeration或network。
- P4-E0-B：documentation-only將第18號§18的V0 numeric／heap／truth／completeness／
  reconciliation裁決同步進權威文件；無Java／Gradle／CI／study rerun。
- P4-E0-B.1：documentation-only固定integrated loaded-player snapshot的logical counting、
  post-DFU、freshness／alias與E3 Gate；不改numeric profile、heap或evidence。
- P4-E0-B.2：documentation-only將heap-floor唯一判定座標修正為effective HotSpot
  `MaxHeapSize` VM option bytes並同步三狀態、precedence與process controls；不改floor、numeric
  profile、R2Q evidence或implementation。B.2 remote closure前不得開始／恢復E1-A。
- P4-E0-B.3：documentation-only固定online Attachment source的25-counter applicability、
  `online > integrated > disk` arbitration、統一UUID owner ordering、online final freshness與E3
  qualification；不改numeric profile、heap floor、R2Q evidence或implementation。B.3 closure前新的
  E1-B read-only review blocked，closure後須從頭重開review。
- P4-E0-B.4：documentation-only固定memory-only root-index generation／exhaustion、E2 invalidation、
  Complete permit／active handoff與exact server removal／restart authority；不改25 counters、heap
  floor、R2Q evidence／profile／case plan或implementation。B.4 closure前P4-E1-B2-B read-only
  review blocked，closure後只可從clean HEAD重開review，implementation仍為`NOT STARTED`。
- P4-E1：read-only bounded online／integrated／disk audit；online只觀察existing admitted state，
  disk／integrated執行full P4-C admission；journal／grouped Store
  audit、memory-only index與bounded completeness results；player／Store／journal mutation與reclaim 0。
- P4-E2：P4-D login recovery後的online-only immutable latest／equipped reconciliation；offline
  disk、Store、journal與reclaim mutation 0。
- P4-E3：唯一`ServerStartingEvent` composition、fresh E1 `Complete`後immediate controlled
  reclaim exactly once，restart／fixed-1,536-MiB／CI／final gates；無chunk force、background／
  periodic audit或cross-tick `Complete`。E1／E2／E3不得合併。

P4-B的whole root固定為unnamed Compound，exact fields只有`data` Compound與`DataVersion` Int；
inner `data` exact fields只有`saved_data_schema_version` Int、`store_blob` ByteArray與
`pending_attachment_updates_blob` ByteArray。Pending欄位不得省略，zero-length ByteArray payload
是唯一canonical no-journal representation；non-zero bytes在P4-B只做bound、defensive copy與
byte-exact opaque preservation，P4-D才以`NbtIo.writeAnyTag` type-byte＋payload／no-root-name framing
strict解析journal schema。Malformed／future journal保留exact bytes與Store read／pin，但停用
submission／recovery／production reclaim composition並使root completeness為false。
`MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES`
計量完整unnamed inner Compound encoding，whole-root V0 framing固定比它增加26 bytes；pending ceiling
只計ByteArray raw payload，compressed-file ceiling計完整primary `.dat`。完整數值、finite
`NbtAccounter` quota與framing推導以18號P4修正案
[§5](18_P4持久化與組合修正案.md#5-persistent-store-physical-schema)與
[§7](18_P4持久化與組合修正案.md#7-exact-hard-byte-ceilings)為準。

Primary `.dat`只接受一個gzip member與compressed EOF；解壓後只接受一個unnamed whole root與EOF，
第二member／root、trailing garbage與zero padding全部拒絕。P4-B2在`ServerStartingEvent`完成一次
bounded primary load，將exact Ready／Quarantined instance安裝到Overworld
`DimensionDataStorage` cache；安裝前不得有Gramarye accessor，安裝後不得使用cache-miss
`computeIfAbsent`重讀disk。`.dat_old`不自動讀取、提升或刪除。

P4-A2因document／migration package visibility恰好核准兩個public facade classes。
`SkillDocumentStorePersistenceFacade` 是`definition.store ↔ definition.document`的唯一
document persistence seam：`encodeCurrent` 只委派A1 package-private current encoder並回
defensive immutable `EncodedSkillDocument`；public load對current／legacy都必須經schema
probe、唯一production plan orchestration、token reinsertion後才呼叫A1 current hydrate。第二個是
`OpaqueSkillDocumentMigrationFacade`。Public current encode不是load bypass；不得公開
current-only decode／hydrate／load／skip-migration、Raw snapshot、`Dynamic`／`Tag`、caller plan或
第三個document encoder facade。Store current encode的每份document必須委派此facade，不得
複製A1 mixed-family serializer。

P4 conformance view中每個token必須保持 ID、typed original location、
`SerializedTreeContext` 與恰好一次 occurrence；raw bytes只能在 migration 完成後exact
reinsertion。不得relocate／exchange token、修改 envelope type／payload schema version、刪除
token-bearing slot 或新增 raw slot。

Store／History／Revision exact-field preflight只處理 physical count sanity：non-negative
count、element type、checked arithmetic、remaining-bytes／minimum-framing 相容性、nested
byte length 與 trailing input，且不依 untrusted count 預配置大型 collection。它不得使用
P3-D 的四種 Store domain count ceilings。Physical shape 錯誤是 `Malformed*Envelope`
且 restore 0 次；physically valid domain overage 必須建立 list-based snapshot、呼叫
`SkillDefinitionStore.restore` 恰好一次，並映射為
`StoreRestoreRejected(CapacityExceeded(...))`。不得建立 P4 平行 count-capacity failure。

P4-A2 repository gate 必須確認 production `SkillMigrationStep` 文件明載
payload／raw／data opaque，migration-visible tree 不含 raw bytes，preflight 不引用 P3-D
domain ceiling，無 `StoreCountCapacityExceeded` 等平行 failure，無 public current-only
hydrate 與第二份 `SkillMigrationPlan`。此為 source-review／test consistency gate，不宣稱可
沙箱化任意惡意 trusted migration code。

P4不得重寫P3-D owner、quota、CAS、revision allocation或reclaim policy。P3-D沒有Store
Codec、NBT、DynamicOps、SavedData、Minecraft dependency或`setDirty()`。Load固定為migration
before restore；migration／decode／restore任一失敗不得安裝partial或empty Store。

Ready adapter同時持有domain Store、matching immutable encoded carrier、immutable opaque pending
blob與rewrite-required state；carrier不是第二truth。Quarantined只表示load-time persistent
corruption／unreadable input，Unavailable只表示runtime Store／carrier pairing invariant failure；
後兩者不保存empty Store或stale carrier、不dirty、不save。Save callback只接受Ready且只寫
預建logical carrier；意外進入其他state時在修改output前fail fast。Async SavedData write不代表
fsync或cross-location durable atomic。

所有一般可失敗encoding與capacity checks仍在Store mutation前完成；final identity／authority
recheck位於全部prebuild之後且緊鄰commit。P3-D回`Committed`後才可發布預建carrier／journal並dirty，
再publication prepared Attachment transition；P4-B不得提前實作或重寫此P4-D composition邊界。

P4-E V0的canonical細節只以第18號修正案§18為準。它採25個獨立inclusive
counters、exact disk-playerdata `IntTag(3955)`、zero P4-E DFU、strict disk
gzip／unnamed-Compound ingress、
`INCOMPLETE_AND_CONTINUE`與product-selected
`MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES = 1_610_612_736`／1,536-MiB audit heap
floor；這些是產品政策，不是universal minimum。V0 closed inventory恰為
`PLAYER_SKILL_ATTACHMENT`與`PENDING_ATTACHMENT_JOURNAL`。`ONLINE_PLAYER_ATTACHMENT`、
integrated snapshot與disk primary／old是player family內的source kinds，不是新增inventory family；
同UUID truth precedence固定為`online > integrated > disk`且恰選一種。只有disk／integrated
materialized Tag必須經完整P4-C admission；online只觀察existing admitted state且本次
`attachment_admissions += 0`。Raw roots在dedup前計capacity，index只memory-only且restart預設Incomplete。

Root-index generation唯一owner是一個`SkillRetentionRootAuditService` identity × 一個exact
`MinecraftServer` object identity的slot。每slot使用memory-only `long` `0..Long.MAX_VALUE`；NoEntry
對外是Incomplete且沒有published generation，新slot internal baseline為0，第一個accepted audit
reservation為1。
Tick、P4-C mutation generation、Store revision、journal generation與SavedData identity都不得挪用。
只有accepted global audit attempt與P4-E2 explicit invalidation消耗一次generation：audit在
null／programming、exact server、logic-thread與active-lease／reentrant checks後、heap與source work
前reserve `g + 1`，立即撤銷舊Complete backing／permit；success與所有normal／RuntimeException
terminals只在同reserved generation發布，Error／OOME原樣傳播且留下同generation non-Complete。
E2每個accepted batch只reserve一次並發布Incomplete，不reaudit／reproject／snapshot／reclaim；上述
更早fail-fast均不改index／generation。
這個lifecycle prelude不是第26個counter，也不改25維profile或既有source-first-failure順序。
Internal state machine至少包含NoEntry、`Incomplete(g)`、`AuditInProgress(g)`、`CompleteIndex(g)`、
`CompleteIndexWithActiveLease(g)`、`GenerationExhausted(Long.MAX_VALUE)`與Removed；publication必須
單次replace完整state，不得逐項發布partial index。

`Long.MAX_VALUE - 1 → Long.MAX_VALUE`合法；current為MAX而audit／E2需要advance時不得
wrap／saturate／reset，必須清除舊Complete backing／permit並以new state identity轉為terminal
`GenerationExhausted(Long.MAX_VALUE)`／
`Incomplete(GENERATION_EXHAUSTED)`，source work、roots、Store audit、snapshot與reclaim皆0，startup
繼續且source data不變；之後
同slot idempotent直到exact server stop `removeServer`。Complete permit每次consume先標used；second
use或wrong service／server／thread／tick／state identity／generation只消耗permit／清自身authority
references，不改index／generation。Success consume在同generation／
backing開active lease；lease open／close不增加generation、active lease阻止audit／E2、close不重發
permit。`removeServer`可強制失效lease並刪slot且不增加generation；新exact server object才從baseline
0開始，同一stopped object不可reset，移除後原handoff操作固定拒絕；不使用Cleaner／finalizer／
background lease timeout。Complete／handoff必須同時綁exact service、server、state
identity、generation、thread與tick，handoff另綁lease identity；generation相等不足以證明currentness。

Heap-floor唯一normative observation是
`HotSpotDiagnosticMXBean.getVMOption("MaxHeapSize").getValue()`的strict canonical base-10
nonnegative `long`。Effective value小於floor為`HEAP_FLOOR_NOT_MET`，大於等於為
`QUALIFIED_FLOOR_PRESENT`，bean／option／value／核准observation無法驗證則為
`HEAP_FLOOR_UNVERIFIABLE`；`Error`／OOME不捕捉。兩個非qualified結果都在journal與source
work前回Incomplete、startup繼續，directory／file／journal／Attachment／root／Store／reclaim／
mutation全為0。`Runtime.maxMemory()`、heap usage max、pool max／peak sum都只作diagnostic，
不得fallback、取min／max、套容差或取代effective `MaxHeapSize`。

Integrated snapshot是platform-post-DFU materialized source：compressed checkpoint不適用且total +0；
以單次checked、read-only traversal計as-if unnamed-Compound logical width並加入同一
structural／aggregate counters。它在canonical owner UUID順序取代同UUID disk source，不查
snapshot `DataVersion`、不再DFU、不copy、不寫byte array／SNBT、不double-count disk、不跨
tick retention。E1必須在same logic-thread／pre-login call chain捕獲並重查exact
server／profile UUID／Tag reference與其他freshness witnesses；任一drift即discard partial roots且
reclaim 0。Object identity不證明沒有敵對same-object mutation。Integrated admission必須共用
且證明pure inner P4-C core，不得直接走registered serializer的NBT-size／raw-copy wrapper。

Online winner排除同UUID integrated projection與disk open／decode；physical disk entries仍照常計
`directory_entries`並保留race witness。`relevant_records`統一定義為每個selected authoritative
owner UUID一筆，source kind可為ONLINE／INTEGRATED／DISK_PRIMARY／DISK_OLD；maximum 2,048
inclusive，第2,049筆capacity failure。Online Missing／Ready／Quarantined都先計一筆，Missing為
zero roots，Quarantined再形成`ATTACHMENT_QUARANTINED`且不退還counter。Online的全部per-file
counters為`NOT_APPLICABLE`，byte／structural aggregates +0，admission +0，Ready actual roots在
append前計capacity；`NOT_APPLICABLE`不是0-byte file。唯一精確25-row table以第18號修正案§18為準。

`PlayerList#getPlayers()` live view不得被保留；E1在logic thread上建立最多2,049個distinct UUID的
compact exact player／UUID／server identity observation，使用既有relevant cap，不新增online
ceiling。所有selected owners共同按UUID natural order，owner內latest按SkillId、equipped按slot，
journal接在所有player claims之後；不得採online-first partition。Online只使用E1-A non-installing
Missing／Ready／Quarantined observation，不取raw Tag、不執行serializer／admission／tree traversal／
size／DataVersion／DFU，不安裝default或`setData`。

Global checkpoints固定為programming／thread → effective heap → Store Ready → journal Ready →
inventory coverage → directory count → filename／pair metadata → bounded online identity → integrated
four-state → source arbitration → UUID sort → relevant count → source-local observation／admission →
player roots → journal roots → grouped Store audit → final freshness → result／index。Final online
freshness只在Complete-candidate路徑重取完整UUID set並驗exact player／server／presence／state identity；
不reproject、不重讀disk、不重跑admission、不retry。Drift形成`ONLINE_SOURCE_FRESHNESS_LOST`、
discard claims／capability、index Incomplete、reclaim 0，且不得覆蓋較早terminal failure。

Offline missing／foreign pointer只defer-to-login，disk不變，當輪reclaim 0；P4-E2只在P4-D
recovery後對online Ready作一次immutable prune，也不reclaim。只有P4-E3能在唯一
`ServerStartingEvent` call chain中使用fresh E1 `Complete`，立即呼叫
`SkillDefinitionStoreService.reclaim` exactly once。Fresh public Complete permit只屬same-call-chain
local，不存field／index／callback、不跨tick；memory-only index可保存internal `CompleteIndex`／
`CompleteIndexWithActiveLease`與同一audited single backing，但不保存public permit或
`SkillRetentionRootSnapshot.Complete`。
P4-B2 controlled API負責實際Store reclaim、matching carrier publication與dirty：Rejected／
reclaimed=0不改state，reclaimed>0先發布carrier再dirty，filter invariant failure轉
Unavailable且不使用舊carrier。P4-E3仍需production-shaped fixed-1,536-MiB combined
Gate；R2Q research evidence不能取代它。Quarantine byte ceilings在P4-B沒有核准
consumer，不得為消耗常數建立raw-copy機制。

R2Q沒有自然執行online `ServerPlayer` path。E3必須在同一1,536-MiB envelope actual執行
online Missing＋Ready、source exclusion與initial／final witness，或提供reviewed machine-checked
domination proof並另執行actual freshness runtime test；兩種方案都維持relevant 2,048與raw roots
65,536 exact maximum。Online不得因大多數counter為+0而完全省略。

## 8.2 RuntimePersistentStore

保存：

- SkillInstance
- position Marker
- Schedule metadata
- 跨維度持久化 runtime 狀態

記憶體 RuntimeIndex 與 Store 分離。

`RuntimePersistentStore` 不屬於 `gramarye_skill_definitions` carrier，也不由 P4-A～P4-E
提前建立；其實際schema與lifecycle留給對應後續工程階段。P4-E只要求未來啟用的每一種
persistent runtime root source加入completeness gate。

## 8.3 玩家技能 Attachment

永久技能資料使用獨立 `gramarye:player_skills` Attachment；Ready V0固定為：

```text
gramarye:player_skills V0
├─ attachment_schema_version
├─ drafts[]
├─ latest_states[]
│  └─ skill_id + optional pointer + mutation_generation
├─ equipped_slots[]
└─ editor
```

- Quarantine marker是alternative representation，不是Ready的追加欄位。Exact physical schema、marker與
  failure vocabulary以[18號P4修正案 §13](18_P4持久化與組合修正案.md#13-player-attachment-schema)為準。
- Total custom serializer必須是`IAttachmentSerializer<Tag, ...>`。Totality只涵蓋平台已materialize
  playerdata、outer attachments為Compound且per-attachment non-null Tag已進serializer body後的
  範圍；read總回non-null Ready或Quarantined，expected data failure只回PreservedRaw或
  OversizeMarker，不throw、不回null或empty／partial Ready。Whole-playerdata／outer-container
  failure不在此保證。
- `MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES`計量`NbtIo.writeAnyTag`的one Tag type byte＋complete
  Tag payload，沒有root name，也不含attachment key／outer playerdata framing；`writeUnnamedTag`
  禁止。`long` bounded counter於16,777,217停止，計量前不copy、不配置等長second array。它是
  post-materialization Gramarye bound，不限制首次playerdata allocation／OOM。
  `MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES`只計`draft_bytes` ByteArray raw payload，不使用此座標。
- In-bound malformed Tag量測後deep-copy，write時`raw.copy()`；只保證materialized logical-tree
  structural equality。Oversize input不保存raw，write deterministic reserved marker；此為明示
  destructive quarantine，restart仍是OversizeMarker，不得稱lossless、missing或empty Ready。
  Raw與marker使用同一`writeAnyTag` coordinate；V0不建sidecar、whole-save blocker、mixin或export Store。
- Route collections使用List並拒絕route／slot duplicate；custom serializer不能觀察平台已
  last-write-wins的materialized Compound duplicate names。
- Owner由authenticated UUID導出。Generation是Java`int`／NBT`IntTag`；absent route為empty pointer
  ＋generation 0，explicit empty generation > 0保留，same-pointer no-op，changed successor只有P4-C
  helper可算。Editor hard-invalid形成Quarantined，structurally valid stale metadata保留。
- Attachment outer、Draft physical encoding與Draft logical schema三個migration軸分離；Draft read
  facts／`ValidationResult`不持久化，不重用`SkillDocument`／payload migration。
- 所有mutation使用immutable replacement `setData`；serialize＋`copyOnDeath`，End不manual copy；no sync。
- Exact-limit PreservedRaw與maximum + 1 marker必須通過512 MiB Xms／1 GiB Xmx／ExitOnOOME
  lifecycle Gate。
- Mana、cooldown與continuation依各自不同的death／sync policy接入，不屬這份永久技能
  Attachment schema。

## 8.4 Marker Attachment

生物／造物 Anchor 上保存 Marker 本體。

要求：

- Entity load 時重建中央 index。
- 讀取時檢查過期。
- Entity 永久消失時 Marker 隨之消失。
- 不在中央 SavedData 再保存相同 Marker 本體。

---

# 9. 事件與排程規格

## 9.1 Internal event

建立 sealed／明確事件型別，例如：

```text
SpellCastEvent
EffectHitEvent
MagicDamageEvent
MagicDeathEvent
PresentationRequestedEvent
```

事件必須：

- 不可變。
- 有 event id、sequence、tick。
- 有 SourceContext。
- 有 EventContext 或等價快照。
- 不保存 NeoForge Event instance。

## 9.2 EventBridge

只在 bridge 中監聽 NeoForge／Minecraft 事件。

Bridge 負責：

1. 判斷是否與模組效果有關。
2. 避免重複回送自身事件。
3. 轉換成 internal event。
4. 放入 queue。

Trigger 不直接依賴 NeoForge Event class。

## 9.3 Scheduler

排序：

```text
scheduledTick
eventSequence
skillInstanceSequence
nodeIndex
childSequence
```

要求：

- 不依 HashMap iteration order。
- cancellation idempotent。
- owner 不存在則取消。
- 新事件不在當前 call stack 直接執行。
- budget 超額時玩法任務延後，不丟棄。
- 視覺任務可降級或丟棄。
- 有 per-instance、per-player、global hard cap。
- 有 trace。

---

# 10. EffectPipeline 規格

## 10.1 EffectRequest

ActionExecutor 只能建立 request。

第一版可先有：

```text
DamageEffectRequest
SpawnProjectileRequest
PresentationRequest
```

後續再增加：

```text
ExplosionRequest
StatusEffectRequest
BlockModificationRequest
ManaTransferRequest
```

## 10.2 Resolve

Resolve 不修改世界。

回傳：

```java
sealed interface ResolveResult {
    record Accepted(EffectCommitPlan plan) implements ResolveResult {}
    record Rejected(RejectReason reason) implements ResolveResult {}
}
```

檢查：

- source/revision/instance
- target validity
- dimension
- range
- faction
- mana
- protection
- budget
- control immunity
- request-specific bounds

## 10.3 CommitPlan

CommitPlan：

- 固定步驟順序。
- 每一步有明確結果。
- 不宣稱全面 rollback。
- 不自動重試整份 plan。
- 能記錄部分成功。

## 10.4 Compensation

每種 Action 定義失敗政策。

第一版至少處理：

- 扣魔後主要效果完全失敗：建立 refund transaction。
- 主要傷害成功、Presentation 失敗：不回滾傷害。
- Presentation 失敗：只 trace／fallback。

---

# 11. 網路規格

## 11.1 Serverbound Intent

最小 payload：

```text
skill slot
input kind
client sequence
optional bounded aim direction
optional bounded target hint
```

伺服器必須重新驗證 target hint。

要求：

- 使用 `CustomPacketPayload`.
- 使用有界 `StreamCodec`.
- payload registration 使用目前 NeoForge 1.21.1 API。
- handler 切到正確主執行緒。
- per-player rate limit。
- sequence 防重播。
- 過期／重複 Intent 拒絕並 trace。
- 不接受客戶端完整技能 JSON。

## 11.2 Clientbound

分開 payload：

- cooldown／continuation sync
- mana sync
- presentation event
- construct tracking state（未來）

不要用一個巨大萬用 payload。

---

# 12. Presentation Layer 規格

## 12.1 Appearance storage schema

```java
record AppearanceDefinition(
    OptionalInt primaryArgb,
    OptionalInt secondaryArgb,
    ProfileSelection soundProfile,
    ProfileSelection particleProfile,
    ProfileSelection trailProfile,
    OptionalInt intensityMilli
) {}
```

要求：

- 有界。
- 玩法 Codec 與外觀 Codec 錯誤隔離。
- 未知欄位忽略。
- 欄位缺失表示 Inherit；僅指定顏色或 intensity 也是合法 partial appearance。
- Top-level 缺失、`null` 或 `{}` 解讀為 Default，canonical 編碼統一寫 `{}`。Node override 缺失或 `null` 解讀為 None，canonical 省略欄位。
- Profile 採 `Inherit | Disabled | Specified(ResourceLocation)` tagged 三態。Canonical Disabled 是 `{"mode":"disabled"}`，Specified 是 `{"mode":"specified","id":"namespace:path"}`；JSON `null` 只是寬鬆 Disabled 輸入。
- 可正規化的值解碼為 Decoded canonical value。型別正確且具明確範圍的數值越界 clamp 至 hard boundary，由 P3-B 產生 bounded warning。
- 無法解析的 ARGB、無效 Profile 結構、型別錯誤或其他無法可靠解釋的錯誤，使整個 blob 成為 Unparsed；不做逐欄位 salvage，並在 quarantine hard bounds 內保存完整 raw snapshot。
- raw subtree 超過 quarantine hard depth／node count 時成為 Rejected，不保存超限 raw tree。
- Decoded／Unparsed／Rejected 都使用 presentation fallback，不得使 gameplay document 失效。
- 保存有限 transient 錯誤資訊，不寫入 `SkillDocument`。
- 不阻止技能施放。
- P4 persistent top-level states只有default／decoded／unparsed，override只有
  none／decoded／unparsed。Top Rejected寫為default，override Rejected寫為none／省略；
  rejection diagnostic、reason與被拒raw tree不持久化。每個Unparsed使用自己的
  family-tagged `RawTreeEnvelope`。

Hard limits 與預設 policy：

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

Appearance depth 從外觀子樹 root 以 1 起算；SkillDocument global depth 從 document root 以 1 起算，兩者獨立。Byte limit 只在真正 raw-byte I/O 邊界執行，不使用 `toString().length()` 估算。

## 12.2 Profile

採：

```text
Java 註冊 Profile Type
+
datapack/JSON Profile Instance
```

- 型別在啟動期凍結。
- 實例可資料重載。
- 技能只保存 Profile ID 與 bounded override。
- 缺 Profile 使用 default。
- 不允許任意 URL、路徑、Java class 或 shader source。

## 12.3 PresentationEvent

第一階段至少：

```text
CAST_RELEASE
HIT
```

payload：

- type
- position
- direction
- profile ids
- bounded override
- intensity
- deterministic seed
- sequence

禁止逐粒子同步。

## 12.4 Budget

至少有：

- events per player per tick
- estimated particles
- max sound volume/range
- max lifetime
- max visual size

超額只降級視覺，不影響玩法。

---

# 13. 第一批內建 Trigger／Action

只實作垂直切片所需最少集合。

## Trigger

1. `active_cast`
2. `effect_hit`
3. 可選：`cooldown_active_cast`，只在階段 3 實作

## Action

1. `spawn_projectile`
2. `damage`
3. 可選：`presentation_only` 只供內部測試，不作正式玩家 Action

暫不實作：

- 完整爆炸
- 標記
- 分裂
- 連鎖
- 重複
- 造物
- 技能編輯器
- 複雜粒子內容

---

# 14. 凍結規格階段 0 的總交付物（由工程 P0～P8 分批完成）

- 本清單不是單一工程 P0 的工作範圍。
- 不得在工程 P0 一次實作本節全部項目。
- 實際執行順序以詳細實作步驟 P0～P8 為準。

階段 0 只建立骨架，不要求可玩技能。

必須交付：

1. 平台版本鎖定。
2. Mod registries。
3. Typed IDs。
4. SkillDraft／SkillDocument／NodeDocument 與未來 `ValidatedSkillDefinition` 的分層邊界。
5. DefinitionEnvelope 與 unknown proxy。
6. Schema migration interface。
7. SkillDefinitionStore。
8. RuntimePersistentStore skeleton。
9. Attachments 註冊與資料模型。
10. Internal event model 與 queue。
11. Scheduler skeleton。
12. EffectPipeline interfaces。
13. ManaTransaction interfaces。
14. Network channel／payload registration skeleton。
15. AppearanceDefinition。
16. PresentationEvent／PresentationBudget skeleton。
17. `/skill inspect` 最小命令或 debug dump service。
18. 單元測試。

階段 0 不得建立大量 placeholder 類別。沒有實際責任的元件先以 interface／record 或明確 TODO 文件表示。

P3 與 P4 工程責任固定切分為：

```text
P3-A：SkillRevision int、SkillDraft／SkillDocument／NodeDocument、
      Appearance storage schema 與 Codec
P3-B：migration、Envelope resolution、validation、ValidatedSkillDefinition
P3-C：Draft formalization、server-side SkillId mint contract、authoritative
      submission precheck、optimistic concurrency precheck、proposed revision、
      既有 resolution／validation／projection 與 immutable SkillSubmissionPlan；
      不寫 Store、不配置正式 revision
P3-D1：production pure-Java Store aggregate、owner/history truth、read API、
       sparse retained revisions、hard ceilings、successor與detached snapshot
P3-D2：quota／owner／CAS／capacity／insert同一atomic mutation、zero-partial
       typed failure、commit result與正式revision allocation
P3-D3：active pin handles、complete retention roots、latest implicit root與reclaim
P4-A1：owner Codec、family/context判定、bounded raw Codec、per-raw-subtree envelope、
       mixed-family current document bridge、appearance mapping與shared logical bounds
P4-A2：store_schema_version、Store／History／Revision physical schema與三層byte ceilings、
       Store migration、logical opaque-token conformance migration、migration-before-hydration、
       snapshot／restore、bounded facts、document persistence facade與current Store blob encode／load
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
P4-D0：documentation-only journal framing／availability、policy ownership與combined memory authority
P4-D1：strict journal、single Store authority snapshot、窄Store submission port、Store／journal
       preflight、opaque commit handle、publication與journal roots
P4-D2：unique policy／SkillId providers、Draft creation、authenticated P3-C composition、
       Attachment transition與composition outcome
P4-D3：bootstrap／login recovery、persisted-readback clear、paired restart與combined fixed-heap Gate
P4-E0-B：documentation-only V0 root-audit authority；無implementation／study rerun
P4-E0-B.1：documentation-only integrated snapshot counting／freshness authority
P4-E0-B.2：documentation-only effective HotSpot MaxHeapSize observation／三狀態／precedence／
           process-control authority；無floor／numeric／R2Q evidence／implementation change
P4-E0-B.3：documentation-only online source counter applicability／arbitration／UUID order／freshness／
           E3 obligation；無numeric／R2Q evidence／implementation change
P4-E0-B.4：documentation-only memory-only root-index generation／exhaustion、E2 invalidation、
           Complete permit／handoff lease／removeServer authority；無counter／heap／evidence／implementation change
P4-E1：read-only bounded online／integrated／disk scanner；online existing-state observation only，
      disk／integrated full P4-C；journal／Store audit與memory-only index；
      mutation／reclaim 0
P4-E2：P4-D recovery後login-only immutable reconciliation；offline／Store／journal／reclaim mutation 0
P4-E3：unique ServerStarting fresh audit→controlled reclaim once、restart／fixed-1,536-MiB／CI gates
```

P3-D建立production pure-Java domain aggregate與behavior；它不是第二個persistent adapter。P4接入Overworld SavedData前不建立檔案I/O或替代persistent copy，P4也不得重寫P3-D policy。

P4-D 才由 composition facade `SkillDefinitionSubmissionService` 串接authenticated principal、single
Store authority snapshot、唯一provider的一份quota／ValidationContext snapshot、P3-C exactly-once
prepare、persistence preflight、final recheck、P3-D commit與Attachment transition；成功後Draft保留。
Submission prepare規則
以[P3 scoped amendment §9-A](17_P3資料模型修正案.md#9-a-p3-c-submission-preparation-與-p3-d-commit-邊界)為準；
composition outcome、report identity、journal與recovery以
[18號P4修正案](18_P4持久化與組合修正案.md)為準。

---

# 15. 階段 1A 垂直切片

流程：

```text
玩家按技能鍵
→ serverbound CastIntent
→ 伺服器驗證技能槽
→ 建立 SkillInstance
→ active_cast Trigger
→ spawn_projectile Action
→ SpawnProjectileRequest
→ Resolve
→ Commit 建立投射物
→ 發出 CAST_RELEASE PresentationEvent
→ 投射物命中
→ EventBridge 建立 EffectHitEvent
→ effect_hit Trigger
→ damage Action
→ DamageEffectRequest
→ Resolve
→ Commit 使用 Minecraft DamageSource
→ 發出 HIT PresentationEvent
```

驗收：

- 單人 integrated server 可執行。
- dedicated server 不載入 client class。
- 命中與傷害只發生一次。
- 無同步遞迴。
- Action 沒有直接 hurt。
- CAST_RELEASE／HIT 走 clientbound presentation payload。
- 客戶端關閉粒子不影響傷害。
- trace 能解釋每一步。

---

# 16. 測試要求

## 16.1 每次階段都執行

```bash
./gradlew compileJava
./gradlew test
./gradlew runGameTestServer
./gradlew runServer
```

若實際 MDK task 名稱不同，先列出 tasks 並使用現有對應 task。

## 16.2 必須測試

- immutable collection。
- Codec round-trip。
- unknown raw payload round-trip。
- schema migration failure retention。
- P3-D1 owner/history/read/snapshot/sparse-history/hard-ceiling與successor。
- P3-D2 quota／owner／CAS／capacity／insert同mutation、zero typed partial、exact conflict/result與repeat commit。
- P3-D3 pin/unpin/latest-root/complete-root fail-closed/non-latest reclaim。
- P4 SavedData adapter successful truth mutation calls dirty；typed failure與pin不dirty。
- Attachment clone policy。
- P4-A1 JSON／NBT／mixed-family same-family structural preservation、RegistryOps／compressed
  JsonOps rebind與A1 raw／document bounds。
- P4-A2 Store／History exact／+1、logical opaque-token conformance migration與四類
  獨立migration／restore gates；
  Revision outer exact只驗inclusive admission，outer MAX + 1在parse前拒絕，最大canonical V0
  `1_048_661`（85-byte wrapper）完整round-trip，outer exact但inner document超限回document
  capacity failure。
- P4-A2 `encodeCurrent` canonical／context-preserving bytes、bounds與alias isolation；current／legacy
  load均經migration seam，production恰好兩個P4-A2 public facade classes，且無public
  current-only decode／hydrate／load bypass、caller plan、mutable tree或Store-side A1 serializer複製。
- P4-A2 migration representation independence：相同outer shell搭配JSON／NBT／RegistryOps
  payload或token view必須產生相同output／facts，payload content／family／context
  不影響branch，opaque slots不變，raw bytes不進migration-visible tree，
  `resolveFromRaw` 與P4 view對合規shell-only step結果一致。
- P4-A2 physical count sanity：negative／impossible count、wrong element type、count與remaining
  bytes不相容均回`Malformed*Envelope`且restore 0次，decoder不依declared count預配
  大型collection。Domain classification：4,097 valid histories、257 same-owner histories、
  129 revisions、32,769 global revisions均回`StoreRestoreRejected(CapacityExceeded)`且
  restore恰好一次，不得是malformed或P4 count failure。
- P4-A3 immutable carrier builder／replacement／filter tests、checked totals與64 MiB fixed-heap
  validation。
- P4-B1 whole-root／inner exact fields、duplicate／unknown／missing／wrong-type拒絕、zero-length
  no-journal sentinel、non-zero opaque preservation、inner／pending exact／+1、whole-root +26 golden、
  finite quota、outer migration、A2 load＋A3 rebuild與no-partial Ready candidate。
- P4-B2 strict single-member gzip與雙層EOF、file exact／+1、one-time Overworld cache install、
  Ready／Quarantined／Unavailable、prebuilt save callback、dirty／reclaim matrix、`.dat_old`政策、
  restart round-trip與1 GiB full-size dedicated-server load／save Gate。
- P4-C totality／duplicate platform boundary、`writeAnyTag` exact／+1 counting、PreservedRaw／marker
  structural round-trip、Draft／latest／equipped／editor、int／same-pointer-no-op generation、
  death／End／logout-login與fixed-1-GiB Gate。
- P4-D strict `writeAnyTag` journal、1 MiB／4096 bounds、continuous chains、partial availability、single
  policy／authority snapshots、preflight zero-mutation failures、Store-first crash windows、
  readback-confirmed clear、replay idempotence、report reference identity與single-process fixed-1-GiB
  combined Gate。
- P4-E第18號§18的25 counters每維exact／MAX+1與first-failure precedence；effective HotSpot
  `MaxHeapSize`三狀態、floor − 1／floor／floor + 1 pure seam、1,536 MiB
  G1／Parallel／Serial／ZGC qualified、1,535 MiB G1 alignment-positive與1,024 MiB G1
  below-floor controls，並證明Runtime／heap／pool values只作diagnostic及非qualified source work 0；
  directory／relevant exact／+1、UUID grammar、primary／old完整matrix、strict gzip／NBT／
  disk DataVersion、DFU 0、integrated snapshot四態／disk exclusion、logical width／modified UTF、
  no-copy single traversal／no-DataVersion／identity freshness／inner P4-C purity、closed
  inventory／journal／grouped Store audit，以及65,536／65,537 raw roots before dedup。另驗online
  Missing／Ready／兩種Quarantined、逐項25-counter applicability、admission +0、same-UUID source
  exclusion、all-source UUID ordering與initial／final exact freshness witness，不得重跑admission。
- P4-E offline defer-to-login disk preservation、P4-D recovery-before-E2、atomic multiprune／generation
  MAX、memory-only index invalidation，Audit N reconciliation後reclaim 0／restart N+1，no chunk
  load／same-call-chain fresh Complete／dirty matrix。P4-E3另須exact fixed-1,536-MiB
  production-shaped combined Gate與production-JAR fixture isolation；Gate必須覆蓋integrated-owner
  path或reviewed machine-checked domination proof；online另須actual Missing＋Ready＋source exclusion＋
  freshness，或machine-checked domination加actual freshness runtime test，並維持relevant 2,048／
  roots 65,536 exact。R2Q不能取代integrated alias或online runtime evidence。
- P4-E memory-only index generation：NoEntry／baseline 0／first reservation 1；audit的success、
  Incomplete、OverLimit、ReconciliationRequired、freshness failure、RuntimeException及Error／OOME
  都恰消耗一次reservation，E2每batch恰一次。驗MAX−1→MAX、MAX後terminal exhaustion、zero source
  work／reclaim、old Complete清除與repeated idempotence；programming／wrong-thread／reentrant、permit
  misuse、lease open／close與removeServer不增加。另驗misused permit已consume但index不變、active
  lease阻止audit／E2、stop強制清lease、new-server slots獨立，以及state identity＋generation共同綁定
  Complete／handoff；index generation不序列化、不進SavedData或R2Q profile。
- scheduler stable ordering。
- cancellation idempotence。
- event re-entry guard。
- mana conservation。
- payload bounds and replay rejection。
- dedicated server client class isolation。
- appearance fallback。
- gameplay unaffected by presentation settings。

---

# 17. Codex 每次回報格式

每次完成工作後，必須回報：

## 已完成

- 具體功能
- 變更檔案

## 架構符合性

- 本階段涉及哪些骨架不變量
- 如何保證沒有形成第二真相
- 是否新增持久化 schema
- 是否新增網路 payload
- 是否需要 migration

## 測試

- 執行的 command
- 通過／失敗
- 若未執行，說明不可執行的具體原因

## 尚未完成

- 本階段剩餘項目
- 已知限制
- 不屬本階段的 TODO

## 風險

- API 不確定處
- 效能風險
- 存檔／網路相容風險

不得只回答「完成」。

---

# 18. 停止條件

遇到以下任一情況，停止相關實作並回報，不得自行猜測或改架構：

- 凍結骨架內部出現直接矛盾。
- 需要讓同一資料存在兩個持久化真相。
- NeoForge 1.21.1 API 無法達成指定生命週期。
- 需要修改已凍結的 mod id `gramarye` 或 namespace `gramarye`。
- 需要修改已由使用者確認的 package root。
- 需要新增第三方 runtime dependency。
- 需要更改 schema、網路信任邊界或 Commit 失敗政策。
- 現有 repository 已有相衝突的持久化世界資料。
- P4 implementation需要whole-document raw family invariant或跨JSON／NBT family conversion。
- Existing invalid SavedData只能被fail-open重設成empty，或在第18號totality boundary內已交付
  serializer body的existing per-attachment Tag只能被重設成empty，或鎖定API無法建立bounded
  non-fail-open入口。
- Whole-root／inner exact framing的+26 golden或finite quota不能接受合法exact fixture。
- Strict reader無法證明單一gzip member、compressed EOF、單一unnamed NBT root與decompressed EOF。
- Bounded custom instance無法一次安裝到Overworld cache，或必須讓`computeIfAbsent`重讀disk。
- SavedData Quarantined無法阻止empty fallback／覆寫，或Unavailable無法阻止dirty與
  stale-carrier save。
- Player Attachment wrong-root不能進`Tag` serializer body、in-bound raw不能structural round-trip、
  counting需先copy／完整second-array、oversize marker被省略／變default、Quarantined variants無法
  跨copyOnDeath／End保存，或exact-limit 1 GiB lifecycle Gate失敗。
- Save callback必須首次執行Store／document encoding、需要未核准lock模型，或只能公開裸
  Store／carrier才能接合。
- P4-A3 reclaim filter仍有normal typed failure，或1 GiB full-size P4-B load／save Gate失敗。
- 任一migration／decode／restore failure會安裝partial Store。
- 無法證明complete player roots卻需要執行reclaim，需要offline playerdata rewrite、
  root-only Attachment parser、DFU、dynamic provider completeness、chunk force、background／periodic
  audit、cross-tick public Complete permit，或讓E1／E2直接reclaim。
- Integrated source無法以單次logical traversal量測／重查identity，或必須copy、完整
  序列化、second checksum、重讀`level.dat`、再次DFU、double-count disk、callback／retry或
  cross-tick retention。
- Online source需要raw Tag、serializer／admission、tree／size／DataVersion／DFU，無法排除同UUID
  integrated／disk，無法採單一UUID natural order，或final freshness需要reproject／retry並覆蓋
  較早terminal failure。
- P4-E3 exact production profile、integrated-owner path或online qualification obligation在
  product-selected `-Xmx1536m` Gate發生
  OOME或timeout。
  不得縮fixture／numeric ceiling、拆分simultaneous envelope或自行提heap；唯一出口是
  先修訂P4-0／第18號heap-floor authority。
- SavedData／Attachment組合被要求承諾fsync或資料庫式durable atomic。
- Production `SkillMigrationStep` 需要觀察或修改 payload／raw／token／
  DefinitionEnvelope type 或 payload schema version，或 direct-raw 與 P4 tokenized view 無法保持
  representation-independent 語意。
- P4 exact-field preflight 需要重寫 P3-D domain count ceilings／precedence、建立平行
  count-capacity failure，或只能依 untrusted declared count 預配置大型 collection。

---

# 19. 第一個指令

現在只執行以下工作：

1. 檢查 repository 與 NeoForge 版本。
2. 確認 mod id 與資料 namespace 均為 `gramarye`，並列出現有 package、建置 task、註冊類別、網路類別、SavedData、Attachment 與測試。
3. 比對凍結版骨架，產出 Gap Analysis。
4. 提出「階段 0」的檔案／類別／測試變更計畫。
5. 不要直接一次實作整個模組。
6. 在計畫獲准後，從最小可編譯地基開始實作。
