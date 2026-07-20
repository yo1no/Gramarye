# Codex 實作總規格 Prompt
## Minecraft 1.21.1 / NeoForge 21.1.x 魔法 Node 系統

> 使用方式：將本檔與 `16_骨架定案清單_NeoForge1.21.1_凍結版.md` 一起交給 Codex。
>
> 本檔是實作契約；凍結版骨架是架構真相。若兩者衝突，以凍結版骨架為準，停止實作並回報衝突，不得自行改變骨架。

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
| SkillDefinition revision | Overworld `SkillDefinitionStore` SavedData |
| SkillInstance | Overworld `RuntimePersistentStore` SavedData |
| 生物／造物標記 | 錨點 Entity Attachment |
| 位置標記 | RuntimePersistentStore |
| Construct | Construct Entity 自身持久資料 |
| Schedule | RuntimePersistentStore |
| Mana | 玩家 Attachment |
| Cooldown | 玩家 Attachment |
| 主動接續指標 | 玩家 Attachment 中的指標，非 SkillInstance 真相 |

中央索引只能是可重建索引，不能與真相形成雙寫。

## 4.2 不可變技能 revision

- `(SkillId, SkillRevision)` 唯一定位不可變定義。
- 玩家提交編輯時建立新 revision 並寫入 `SkillDefinitionStore`。
- 未提交工作副本不可施放、不可落庫為正式 revision。
- 施放只 pin revision，不重寫定義。
- 進行中實例永遠解析固定 revision。
- 舊 revision 只有在沒有玩家引用與執行期引用時才可回收。

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
public record SkillRevision(long value) {}
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

## 6.2 SkillDefinition

最小欄位：

```java
record SkillDefinition(
    int schemaVersion,
    SkillId skillId,
    SkillRevision revision,
    List<NodeDefinition> nodes,
    AppearanceDefinition appearance
) {}
```

要求：

- 完全不可變。
- `List.copyOf`.
- Node 數有 decode 與 validation 上限。
- 建構時不偷偷執行 migration。
- migration 在獨立流程完成。
- Appearance 可 fallback，但玩法 Node 解析不可被外觀錯誤連坐。

## 6.3 NodeDefinition

```java
record NodeDefinition(
    int index,
    TriggerDefinition trigger,
    ActionDefinition action,
    AppearanceOverride appearanceOverride
) {}
```

要求：

- index 唯一、穩定、非負。
- Node 只能引用更小 index。
- Node 1 不可選來源依賴 Action。
- 一個 Node 恰有一個 Trigger 與一個 Action。

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

建立：

```java
sealed interface TriggerDefinition permits ResolvedTriggerDefinition, UnknownTriggerDefinition
sealed interface ActionDefinition permits ResolvedActionDefinition, UnknownActionDefinition
```

## 6.5 Context

```java
record SourceContext(
    UUID casterId,
    SkillId skillId,
    SkillRevision revision,
    SkillInstanceId skillInstanceId,
    int rootNodeId,
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

責任：

- 保存不可變 revision。
- 提交新 revision。
- 查詢固定 revision。
- 保存玩家正式引用計數或可重建 metadata。
- pin／unpin 執行期引用。
- mark-and-sweep 或可驗證 reference count 回收。
- 每次 mutation `setDirty()`。

禁止：

- 保存 Player／Entity live reference。
- 在查詢 getter 中隱式修改引用。
- 施放時覆寫 revision。

## 8.2 RuntimePersistentStore

保存：

- SkillInstance
- position Marker
- Schedule metadata
- 跨維度持久化 runtime 狀態

記憶體 RuntimeIndex 與 Store 分離。

## 8.3 玩家 Attachment

至少：

```text
PlayerMagicData
├─ mana
├─ maxMana
├─ equippedSkillRefs
├─ latestSkillRevisions
├─ cooldowns
└─ activeContinuationInstanceIds
```

要求：

- 伺服器為真相。
- 明確死亡複製政策。
- 明確 End return clone policy。
- Attachment 不假定自動同步。
- mutation 經專用 service。

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

## 12.1 Appearance

```java
record AppearanceDefinition(
    int primaryArgb,
    int secondaryArgb,
    ResourceLocation soundProfileId,
    ResourceLocation particleProfileId,
    Optional<ResourceLocation> trailProfileId,
    int intensityFixed
) {}
```

要求：

- 有界。
- 玩法 Codec 與外觀 Codec 錯誤隔離。
- 未知欄位忽略。
- 越界 clamp。
- 整體錯誤 fallback。
- 保存有限錯誤資訊。
- 不阻止技能施放。

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
4. SkillDefinition／NodeDefinition。
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
- revision submit/store/pin/unpin/reclaim。
- SavedData mutation calls dirty。
- Attachment clone policy。
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

---

# 19. 第一個指令

現在只執行以下工作：

1. 檢查 repository 與 NeoForge 版本。
2. 確認 mod id 與資料 namespace 均為 `gramarye`，並列出現有 package、建置 task、註冊類別、網路類別、SavedData、Attachment 與測試。
3. 比對凍結版骨架，產出 Gap Analysis。
4. 提出「階段 0」的檔案／類別／測試變更計畫。
5. 不要直接一次實作整個模組。
6. 在計畫獲准後，從最小可編譯地基開始實作。
