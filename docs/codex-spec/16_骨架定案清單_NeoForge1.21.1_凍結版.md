# 骨架定案清單（NeoForge 1.21.1 凍結版）

> 目標平台固定為 **Minecraft Java Edition 1.21.1 + NeoForge 21.1.x + Java 21**，單載入器開發，不採 Architectury。
>
> 本檔只定義「日後改動會牽動全系統」的骨架、不變量、資料邊界與失敗政策。數值、公式、完整 Trigger／Action 目錄、UI、美術、成長與經濟仍屬內容層。
>
> **重要聲明：任何軟體架構都不可能保證未來零問題。** 本清單的目標是把高風險問題前移、建立可遷移與可診斷能力，使未來問題能局部修正，而不是要求永遠不改程式。
>
> P3 資料模型與 P3-D Store 邊界的已核准範圍限定修訂記錄於
> [`17_P3資料模型修正案.md`](17_P3資料模型修正案.md)；P4 persistence、Attachment、
> composition、recovery、reconciliation 與 offline-root 邊界記錄於
> [`18_P4持久化與組合修正案.md`](18_P4持久化與組合修正案.md)。兩份修正案只在各自
> 明示範圍內優先，本文相關條文已同步其核心邊界。

---

## 第一層：平台與建置鎖定

## 1. 平台固定

- 【決定】Minecraft：`1.21.1`。
- 【決定】載入器：NeoForge `21.1.x`。
- 【決定】Java Toolchain：Java 21。
- 【決定】只支援 NeoForge，不抽象 Fabric／Forge 相容層。
- 【決定】使用 Mojang 官方 mappings 與 NeoForge 官方 MDK／ModDevGradle 建置方式。
- 【必要下限】NeoForge **不得低於 21.1.229**，因為該版本修正伺服器端網路物件配置漏洞。
- 【版本策略】開發與發布都固定到一個已測試的完整 NeoForge build，不使用 `latest`、`+` 或無上限依賴。
- 【版本範圍】`minecraft_version_range` 僅接受 1.21.1；`neo_version_range` 以已測試版本為下限並限制於 21.1 系。
- 【決定】Gradle Wrapper、ModDevGradle／插件版本、Java Toolchain 與 NeoForge build 一起提交版本控制。
- 【決定】`mod_id`、Java package root、資料 namespace 發布後視為永久識別，不任意更名。

## 2. 建置可重現性

- 【決定】CI 必須執行：編譯、單元測試、GameTest、資料生成驗證與專用伺服器啟動測試。
- 【決定】禁止依賴本機未提交檔案、IDE 產物或手動複製資源。
- 【決定】發行 JAR 必須記錄：mod 版本、Git commit、Minecraft 版本、NeoForge build、schemaVersion。
- 【建議】正式發布前建立乾淨環境重建，確認產物可重現。

---

## 第二層：資料模型與序列化

## 3. 三種資料模型分離

- 【決定】不得宣稱「一份 Codec 同時處理所有資料」。分成：
  - `SkillDocument`：固定 revision 的不可變持久化文件，Codec／JSON；Trigger／Action 只保存 `DefinitionEnvelope`。
  - `ValidatedSkillDefinition`：由 `SkillDocument` 解析與驗證後重建的不可持久化 runtime projection，runtime API 只接受此型別。
  - `SkillRuntimeState`：技能實例、標記、排程與持久化狀態，獨立 Codec 或明確 NBT 編碼。
  - `SkillSyncPayload`：客戶端顯示所需資料，使用 `StreamCodec`。
- 【原則】三者可以共用不可變 value object，但不得共用完整資料形狀。
- 【限制】所有 collection、string、byte array、Node 數與巢狀深度在 decode 前後都要有硬上限。
- 【限制】Codec 解析成功不代表資料合法；解析後仍必須通過語意驗證器。
- 【介面邊界】不建立讓 `SkillDocument` 與 `ValidatedSkillDefinition` 同時實作的寬鬆 `SkillDefinition` runtime 介面。
- 【文件硬限】`MAX_SKILL_DOCUMENT_BYTES = 1 MiB`、`MAX_SKILL_DOCUMENT_DEPTH = 64`；預設政策值為 256 KiB 與 32。Byte limit 只在真正持有 raw bytes 的 I/O 邊界執行；post-parse global depth proxy 從 document root depth 1 起算並必須 short-circuit。
- 【Raw family】`SkillDocument` 不具有單一 raw family invariant；同一文件甚至同一 Node
  可同時含 JSON 與 NBT raw subtree，submission 不得只因 mixed family 拒絕技能。P4
  persistence 必須以 per-raw-subtree envelope 保存，不能使用 whole-document／whole-node
  family tag。

## 4. 定義 Envelope 與未知型別保全

- 【決定】Trigger／Action 定義外層統一使用 envelope：

```text
DefinitionEnvelope
├─ typeId: ResourceLocation
├─ schemaVersion: int
└─ rawPayload: Dynamic/Tag/JSON object
```

- 【決定】先讀取 `typeId + rawPayload`，再交給已註冊型別解析。
- 【禁止】不可只使用一般 registry-dispatch Codec 後才嘗試保留未知型別；未知型別可能在 dispatch 階段就失敗並失去原始參數。
- 【文件邊界】`SkillDocument` 只保存 Trigger／Action 的原始 `DefinitionEnvelope`，不持久化 Resolved／Unknown union。
- 【未知型別】P3-B 在 registry resolution 後產生 transient Unknown classification，其中引用文件內的原 type ID、完整 raw payload 與 schemaVersion，並提供有界錯誤訊息。
- 【行為】技能標記為「損壞待修」，不可施放，但可查看、匯出、移除玩家引用與等待依賴恢復後重試解析。移除 Attachment 引用不等於 Store retire，也不釋放 quota。
- 【重試時機】未知型別只在伺服器啟動／資料載入階段、且 Registry 已完成註冊後重試解析；不支援執行中熱插 Java 型別。
- 【Runtime projection】只有不含 Unknown 且完成驗證的 transient candidate 可建立 `ValidatedSkillDefinition`；此 projection 可由 `SkillDocument` 重建，不得持久化為第二真相。
- 【P4 storage】每一個 Trigger／Action `DefinitionEnvelope.payload` 都必須個別包裝為
  `RawTreeEnvelope`，不依 descriptor 是否存在決定；Unknown raw 的 storage 保證是
  same-family structural preservation，不包含 object identity 或任意跨 JSON／NBT family
  無損轉換承諾。RegistryOps 是需重新綁定的 context，不是第三 family。

## 5. Schema migration 與技能 Revision 分離

- 【決定】`schemaVersion`：資料格式版本。
- 【決定】`skillRevision`：玩家修改同一技能後產生的不可變修訂版本。
- 【禁止】不得混用兩者。
- 【決定】所有 schema migration 必須是顯式、逐版、可測試的函式：`v1 -> v2 -> v3`。
- 【決定】migration 失敗時保留原始資料與錯誤，不覆寫原檔。
- 【決定】寫回新格式前，建立世界／技能資料備份或採原子替換策略。
- 【決定】技能 revision 永不原地修改；編輯永遠建立新 revision。
- 【三層 migration】P4 Store／storage envelope migration、P3-B1 既有 skill schema
  migration、P3-B2 payload migration 是三個不同邊界。不得建立第二個 skill migration
  plan；P4 load 只執行前兩層，payload migration 留在 resolution。
- 【Skill migration 權威模型】`SkillMigrationPlan` 的輸入語意是 logical
  `SkillDocument` outer schema。`SkillMigrationStep` 只處理 skill／document-level schema
  fields；`DefinitionEnvelope.type`、payload `schema_version`、payload 內容與 Unparsed
  appearance raw slot 都必須保持不變，不得被讀取、遍歷、比較、hash 或用來
  決定 migration branch／facts。
- 【P4 conformance view】P4 不把 physical `RawTreeEnvelope` wrapper 交給 skill
  migration。Logical view 保留 document／node outer fields 與 DefinitionEnvelope
  `type + schema_version`，只把 logical payload／raw slot 替換為 opaque token sentinel；
  family、registry context、compressed-maps flag 與 exact raw bytes 只存在 side table。Token
  ID、typed location、context 與 occurrence count 都必須保持，且不得改寫
  envelope type／payload schema version。
- 【`resolveFromRaw`】它仍是正式 P3-B2 raw-ingress pipeline，不是 legacy 或
  test-only API。P4 mixed-family load 不呼叫它，而是以 logical tokenized conformance
  view 調用同一 `SkillMigrationPlans.production()`；合規 step 在 direct-raw 與 tokenized
  representation 必須產生相同 outer output 與 facts。
- 【數值範圍】`SkillRevision` 是 `0..Integer.MAX_VALUE` 的非負 `int`，canonical JSON 使用普通整數。P3-C 只提出 revision，latest 為 MAX 時形成既有的 `RevisionExhausted` preparation outcome；P3-D 只有 commit 成功才正式配置。
- 【共用 successor】P3-D1 additive 建立唯一 `SkillRevision.successor()`，由 P3-C3 與 P3-D2 共用；非 MAX 回 `value + 1`，MAX 回 empty。它不表示 allocation。
- 【commit exhaustion】合法 Plan 不含 `ExpectedLatest(MAX)`，正常 Store commit result 不含 `RevisionExhausted`；Plan 後 actual latest 前進至 MAX 依 CAS 回 `LatestMismatch`。不得 overflow、wrap 或重用 revision。

## 6. 不可變技能文件儲存庫

- 【Store shape】P3-D 建立 production pure-Java `SkillDefinitionStore` aggregate：每個 active `SkillId` 對應一個非空 `StoredSkillHistory`，其中保存恰一個 immutable `SkillOwnerId owner` 與 `SkillRevision -> SkillDocument` immutable retained entries。它是 domain truth與行為，不是第二個 persistent adapter。
- 【唯一真相】owner binding 與 retained `SkillDocument` entries 是 Store 唯一 domain truth；`SkillDocument` 是 revision 內容唯一真相。Latest／owner count／skillsByOwner／revision count／allocation counter都只能可重建；P3-D1／D2第一版不建立這些indices或counter。
- 【定位】`SkillDefinitionStore` 以 `(SkillId, SkillRevision)` 定位不可變 `SkillDocument`。Store 不保存 `ValidatedSkillDefinition`、Plan、Outcome、ValidationResult 或 Draft。
- 【決定】`SkillInstance` 只引用 `(SkillId, SkillRevision)`，不深拷貝整份技能。
- 【決定】定義物件必須不可變；Node、參數 collection 也不可暴露可變引用。
- 【落庫】revision 在玩家**提交編輯當下**先由 P3-D aggregate 正式 commit；P4 的 Overworld SavedData adapter再持久化同一 domain snapshot。施放時只 pin 固定 revision，不重寫定義本體；解析不得依賴技能擁有者在線。
- 【玩家資料】獨立永久 player skill Attachment 保存 Draft、latest／equipped引用與editor
  metadata。工作副本未提交前不進 Store、不成為正式 revision、不可施放，也不參與
  revision 回收；它仍可作為 Attachment 中的跨重啟 editing authority。
- 【刪除語義】玩家刪除技能只移除自己的 Attachment latest／equipped引用，不等於Store retire；owner binding與latest保留，quota不釋放。P3-D1～D3不實作whole SkillId delete／retire／owner removal／tombstone／quota release。
- 【未來 retire】正式quota release必須是獨立Store operation，確認無external roots／pins，並保存persistent tombstone或等價no-reuse truth；不得直接移除Map後讓SkillId從revision 0重用。此操作另立scoped amendment。
- 【稀疏 history】New從revision 0開始，Existing使用current latest的唯一 `SkillRevision.successor()`。Retained history可因reclaim稀疏，gap不等於corruption；每個active history的最大retained revision是implicit root，一般reclaim不得移除，因此max key不倒退且不需highest-allocated counter。
- 【回收】只有非latest、沒有玩家／SkillInstance／Marker／Construct／Schedule或其他complete external root，且active pin count為0的revision可回收。回收使用可驗證mark-and-sweep；roots超限、截斷或不完整時fail closed，整輪不sweep。Active pins不持久化，重啟由authoritative persistent roots重建。
- 【提交邊界】P3-C 只根據當下 authoritative snapshot 產生攜帶 owner／principal、Store precondition、proposed document 與 validated definition 的 immutable `SkillSubmissionPlan`；Prepared plan 不等於 committed revision，不寫 Store，也不配置正式 revision。
- 【配置真相】revision allocation 的 latest truth 是 Store 最大 retained key，不是玩家 Attachment pointer。P3-D 只有在 atomic compare-and-insert 成功時才正式配置 proposed revision；committed revision永不覆寫。
- 【共同 mutation】authoritative quota admission、owner check、ExpectedAbsent／ExpectedLatest CAS、technical capacity checks與revision insert在同一thread-confined Store method中完成。所有typed failure在第一個truth mutation前回傳；replacement history完整建立後才單次outer-map insert／replace。Quota／capacity／CAS／owner失敗零狀態變更，成功後不得回quota rejection。
- 【Quota】`SkillQuota` 是 `Unlimited | Limited(maxCommittedSkills)`。Unlimited只表示無額外per-owner policy quota；Limited合法範圍 `0..MAX_COMMITTED_SKILLS_PER_OWNER`，0可拒絕所有New。Quota計算per-owner distinct active committed SkillId，New成功+1，Existing／failed commit／old revision reclaim不變；只在確認Absent後檢查。正式retire前預設policy為Unlimited，Limited目前沒有release workflow。
- 【Hard ceilings】`MAX_COMMITTED_SKILLS_PER_OWNER = 256`、`MAX_COMMITTED_SKILLS_GLOBAL = 4096`、`MAX_RETAINED_REVISIONS_PER_SKILL = 128`、`MAX_RETAINED_REVISIONS_GLOBAL = 32768`、`MAX_RETENTION_ROOTS_PER_RECLAIM = 65536`。它們放在 `MagicSafetyCeilings` 或唯一canonical位置，不可由config突破；count limits不取代P4 encoded-byte limits。
- 【Owner Codec】P3-D仍只使用typed `SkillOwnerId`，不新增Codec／StreamCodec／String representation；P4 persistence時才additive加入唯一canonical UUID Codec，不建立Player UUID平行wrapper。
- 【Capacity result】technical capacity使用 `CapacityRejected(SkillStoreCapacityScope, current, maximum)`；scope固定為 `OWNER_SKILL_HISTORIES`、`GLOBAL_SKILL_HISTORIES`、`SKILL_RETAINED_REVISIONS`、`GLOBAL_RETAINED_REVISIONS`，`current`／`maximum`非負且 `current >= maximum`，不保存任意message，不與policy `QuotaRejected`混用。
- 【Commit result】正常vocabulary為 `Committed`、`Conflict`、`QuotaRejected`、`CapacityRejected`、`OwnerRejected`。Conflict只含 `ExpectedAbsentButPresent`、`ExpectedLatestButAbsent`、`LatestMismatch`；無`AlreadyCommitted`或commit-time `RevisionExhausted`，不自動retry。Owner mismatch先於latest mismatch，不保存actual owner或observed latest。
- 【組合服務】P4-D 的 `SkillDefinitionSubmissionService` 由 authenticated server principal
  導出 owner，以單一Store observation取得authority，並從composition-root唯一provider恰取一次
  同時包含quota與`ValidationContext`的immutable policy snapshot；呼叫P3-C exactly-once prepare，
  完成persistence／journal preflight後作final recheck，再呼叫P3-D commit。Store仍防禦性檢查
  committed owner與state；P3-C／P3-D不依賴Minecraft Player類別。成功提交保留Draft。
- 【Draft】`SkillDraft` 是業務上可編輯、Java instance 上不可變的 snapshot；使用 `AppearanceDocument` 與 `AppearanceOverrideDocument`，不建立 `DraftAppearance`。Draft 持有候選 `SkillId`；P3-C 定義 server-side mint contract，但 transient mint grant 不是跨重啟 submission credential，提交授權仍以當下 authoritative snapshot 為準。
  P4-D2 `SkillDraftCreationService`是第一個production `SkillIdSource` consumer，唯一random-UUID
  adapter由composition root持有。

---

## 第三層：NeoForge 1.21.1 資料住址

## 7. 玩家與實體資料使用 Data Attachment

- 【永久技能 Attachment】Draft、latest、equipped 與 editor metadata 使用獨立
  `gramarye:player_skills` Attachment，不與 mana／cooldown 等不同死亡政策資料混用。
- 【Totality 邊界】Total custom serializer 的承諾只從 NeoForge 已成功 materialize playerdata、
  outer `neoforge:attachments` 是 `CompoundTag`，且 non-null `gramarye:player_skills` `Tag`
  已進入 `IAttachmentSerializer<Tag, ...>` body 後開始；whole-playerdata／outer-container failure
  與平台已消解的 duplicate fields 不在此承諾內。
- 【缺失與損壞】只有 missing key 建立 empty Ready。Existing malformed input 必須建立
  `PreservedRaw` 或 `OversizeMarker` Quarantined，不得回 `null`、empty／partial Ready或讓預期
  data failure逃出serializer。PreservedRaw在核准上限內保存materialized Tag deep copy並以
  `raw.copy()`重寫，只承諾logical NBT tree structural preservation；OversizeMarker以第18號
  修正案的reserved bounded marker明示取代原oversize representation，restart仍是Quarantined，
  不宣稱lossless。
- 【Attachment byte座標】`MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES`計量
  `NbtIo.writeAnyTag` 的one Tag type byte＋complete Tag payload，不含root name、attachment key或
  outer playerdata framing；PreservedRaw與marker使用同一座標。使用`long`
  bounded-counting `DataOutput`，exact合法，觀察maximum + 1立即停止，不先copy或配置等長second
  array。不得使用`writeUnnamedTag`；這是post-materialization bound，不是playerdata ingress／OOM防線。
  `MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES`只計`draft_bytes` ByteArray raw payload，不使用此座標。
- 【Duplicate邊界】Draft／latest／equipped route-bearing collections維持List並拒絕route／slot
  duplicate；materialized Compound的wire-level同名field已由平台last-write-wins，不得宣稱可偵測。
- 【Reference state】Mutation generation固定Java `int`／NBT `IntTag`；absent route是
  empty pointer＋generation 0，same-pointer是no-op，changed transition才由P4-C唯一helper計算
  successor。Editor hard-invalid使Attachment Quarantined；structurally valid stale editor metadata
  原樣保留。Attachment outer、Draft physical encoding與Draft logical schema是三個獨立migration軸。
- 【決定】玩家魔力、技能槽引用、冷卻索引與接續實例引用使用 NeoForge Attachment。
- 【決定】生物／造物錨上的標記本體使用 Attachment，與錨點同生死；中央只保存可重建索引。
- 【決定】Attachment 是否持久化必須由是否提供 serializer 明確決定。
- 【決定】玩家死亡複製政策逐 Attachment 指定：
  - 永久技能資料：複製。
  - 魔力：依遊戲政策。
  - 冷卻／接續狀態：預設複製或明確清除，不可依 NeoForge 預設碰運氣。
- 【注意】Attachment 不會自動同步到客戶端，所有同步由本模組 payload 完成。
- 【注意】若未來在 chunk／block entity 上保存可變 Attachment，直接修改 `getData()` 回傳內容後必須正確標記 dirty；優先使用不可變 record + `setData()`。
- 【複製】永久 player skill Attachment 使用 serialize + `copyOnDeath`；Ready、PreservedRaw與
  OversizeMarker都經serializer write／read重建，End return不得再做手動double-copy，P4-C不sync。

## 8. 全域資料使用 Overworld SavedData

- 【Skill Store 持久化】`gramarye_skill_definitions` 是 P3-D
  `SkillDefinitionStore` 的唯一 Overworld production persistence adapter，只保存 Store
  detached snapshot 與 pending Attachment update journal，不承載 `RuntimePersistentStore`。
  Runtime persistence 仍是另一資料類別與未來獨立 lifecycle。
- 【唯一位置】非 Overworld 維度取得同一 Overworld adapter；不使用 static world singleton。
- 【理由】主世界是伺服器運作期間不會完全卸載的維度。
- 【P4-B 分層】P4-B1只建立pure outer carrier／load state；P4-B2才接入Overworld
  filesystem、`DimensionDataStorage` cache、SavedData callback與dirty lifecycle。B1不得取得world，
  B2不得解析P4-D journal domain。
- 【Outer exact framing】解壓後whole root固定為unnamed Compound，exact fields只有`data`
  Compound與`DataVersion` Int；inner `data` exact fields只有`saved_data_schema_version` Int、
  `store_blob` ByteArray與`pending_attachment_updates_blob` ByteArray。Duplicate／unknown／missing／
  wrong type及root後trailing input一律拒絕；`DataVersion`不是Gramarye migration axis。完整framing與
  byte座標以18號P4修正案
  [§5](18_P4持久化與組合修正案.md#5-persistent-store-physical-schema)與
  [§7](18_P4持久化與組合修正案.md#7-exact-hard-byte-ceilings)為準。
- 【No-journal sentinel】`pending_attachment_updates_blob`欄位必須存在，zero-length ByteArray payload
  是唯一canonical no-journal representation。P4-B只對non-zero blob執行byte bound、defensive copy
  與byte-exact opaque preservation；不解析`journal_schema_version`、entries、generation或pointer。
- 【Journal framing】P4-D non-zero journal唯一使用`NbtIo.writeAnyTag` type-byte＋payload／no-root-name
  framing，raw payload ceiling為1 MiB，raw entries ceiling為4096；strict decoder在materialization前
  拒絕duplicate fields及trailing bytes。完整physical schema、failure precedence與migration只以
  [18號P4修正案 §16](18_P4持久化與組合修正案.md#16-store-first-recovery-journal)為準。
- 【載入】Primary `.dat`只接受exactly one gzip member；member後必須compressed EOF，解壓後只接受
  exactly one whole root與EOF。拒絕第二member／root、任意trailing garbage與zero padding。通過
  compressed-file bound、bounded strict NBT outer decode、SavedData carrier migration後，依序委派
  P4-A2 load／restore與P4-A3 full carrier rebuild。任一步失敗形成non-dirty Quarantined，不得建立
  partial或empty Store，不覆寫原檔，也不自動把`.dat_old`提升為truth。
- 【禁止 fail-open】標準 `computeIfAbsent` 的 exception-to-fresh-data 行為不得直接作技能
  Store deserializer 入口。P4-B2在`ServerStartingEvent`以`server.overworld()`完成一次bounded
  primary load，再把exact Ready／Quarantined instance安裝到Overworld `DimensionDataStorage` cache；
  安裝前不得有Gramarye accessor，安裝後不得讓cache miss重讀disk。
- 【State】Ready同時持有domain Store、matching immutable encoded carrier、immutable opaque pending
  blob與rewrite-required state。Quarantined只表示load-time corruption／unreadable input；Unavailable
  只表示runtime Store／carrier pairing invariant failure。後兩者都不保存empty Store或stale carrier、
  不dirty、不save；save callback只接受Ready，意外進入時必須在修改output前fail fast。
- 【Mutation】所有一般可失敗 encode 與 revision／history／Store／journal／carrier byte
  checks 必須在 Store truth mutation 前完成。P3-D 回 `Committed` 後才發布預建 carrier／
  journal 並 `setDirty()`；typed failure 與 pin／close 不 dirty。P4-E只取得complete retention roots；
  P4-B2的controlled reclaim才負責Store call、carrier publication與dirty：Rejected／reclaimed=0不變，
  reclaimed>0先發布matching carrier再dirty，filter invariant failure轉Unavailable且不得保存stale carrier。
- 【Durability】SavedData async write 無成功 ack；API 只承諾 in-memory committed +
  persistence scheduled，不宣稱 fsync durable、disk write成功或Store／Attachment durably
  atomic。Save callback 只寫預建 immutable carrier，不首次執行一般 Codec。
- 【委派】P4 不重寫 P3-D quota、CAS、owner、revision allocation 或 reclaim domain policy；
  P3-D 本身沒有 SavedData、Codec、DynamicOps 或 dirty 責任。
- 【禁止】SavedData 不保存 Entity、Level、Player 等 live object reference。
- 【持久化引用】只保存型別化 ID、`ResourceKey<Level>`、座標與必要快照。
- 【分離】純記憶體索引與磁碟持久資料分開，重載時由持久資料重建索引。
- 【Physical schema與bounds】Store／History／Revision 以 list-based、length-delimited blobs
  保存，duplicate route 保留至 restore。Exact physical schema、RawTreeEnvelope V0 與唯一
  hard byte ceilings以[18號P4修正案](18_P4持久化與組合修正案.md)為準。
- 【Physical／domain count 分離】Exact-field preflight 只驗證 list count 非負、
  element type、checked arithmetic、remaining-bytes／minimum-framing 相容性、nested byte
  length 與 trailing input，不執行 P3-D 的四種 Store domain count ceilings，也不得
  依 untrusted count 預配置大型 collection。Physical shape 錯誤為
  `Malformed*Envelope` 且 restore 0 次；physically valid 但 domain count 超限必須建立
  list-based snapshot、呼叫 `SkillDefinitionStore.restore` 恰好一次，並回
  `StoreRestoreRejected(CapacityExceeded(...))`，不得建立 P4 平行 count failure。
- 【Revision ceiling語意】`MAX_STORE_REVISION_ENTRY_ENCODED_BYTES`是inclusive outer-envelope
  admission ceiling，不保證V0 canonical revision可成功產生同長度資料；V0目前最大完整合法
  revision為`1_048_661` bytes，inner document limit仍獨立執行。
- 【Player roots】P4-E V0 以第18號修正案§18的25維inclusive vector、exact disk-playerdata
  `IntTag(3955)`、zero P4-E DFU、`MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES = 1_610_612_736`
  （1,536 MiB）product-selected audit heap floor 與
  `INCOMPLETE_AND_CONTINUE` 執行bounded read-only audit。Closed inventory恰為player skill
  Attachment與pending journal；restart預設Incomplete，任一source／counter／heap／Store audit
  無法證明完整時不得sweep。Index只memory-only，不強制載入chunk、不background／
  periodic audit。Fresh public Complete permit只屬same-call-chain local，不存field／index／callback、
  不跨tick；memory-only index可保存internal `CompleteIndex`／`CompleteIndexWithActiveLease`與同一
  audited single backing，但不保存public permit或`SkillRetentionRootSnapshot.Complete`。這個heap
  floor是產品選擇，不是universal
  safe minimum。Heap-floor唯一判定座標是
  `HotSpotDiagnosticMXBean.getVMOption("MaxHeapSize").getValue()`的strict canonical base-10
  nonnegative `long`，不是requested `-Xmx`或`Runtime.maxMemory()`。小於floor為
  `HEAP_FLOOR_NOT_MET`，大於等於為`QUALIFIED_FLOOR_PRESENT`，bean／option／value／核准
  observation無法驗證則為`HEAP_FLOOR_UNVERIFIABLE`；兩個非qualified狀態都在journal、directory
  與source work前回Incomplete，startup繼續且reclaim／mutation為0。Runtime、heap-usage與pool
  memory values只作diagnostic，不得fallback、取min／max或套用容差；`Error`／OOME不捕捉。
- 【Integrated owner】Platform-post-DFU loaded-player snapshot不讀`DataVersion`、不再DFU、沒有
  compressed coordinate，以單次read-only traversal計as-if unnamed-Compound logical width與同一
  structural／aggregate counters。它在owner UUID順序中取代同UUID disk source，不copy、不寫
  byte array、不double-count、不跨tick，並在Complete candidate前對server／profile UUID／
  exact Tag reference作freshness recheck。Object identity不保證偵測違反thread contract的同object
  敵對in-place mutation。Attachment必須使用未修改input的inner P4-C admission core，
  不呼叫會先做NBT size-measure／raw-copy的registered serializer wrapper。
- 【Online owner】`ONLINE_PLAYER_ATTACHMENT`是既有`PLAYER_SKILL_ATTACHMENT` family內的source
  kind，不是第三個inventory family。每UUID的truth precedence固定為
  `online > integrated runtime snapshot > disk primary／old`且恰選一種；online勝出時不open／
  decode disk、不project integrated。Physical disk entries仍計directory及race witness，但被排除的
  pair不計relevant record。所有selected player owners共同按UUID natural order處理，不建立
  online-first分區；owner內latest按SkillId、equipped按slot，journal在所有player claims之後。
- 【Online counting／freshness】Online Missing／Ready／Quarantined都使selected owner的
  `relevant_records += 1`；all per-file counters為`NOT_APPLICABLE`，byte／structural aggregate
  counters +0，`attachment_admissions += 0`，Ready actual roots在append前計cap且保留duplicates。
  `NOT_APPLICABLE`不是0-byte file；唯一逐項25列以第18號修正案§18為準。E1只觀察已admit的
  existing state，不取raw Tag、不重跑serializer／admission／tree／size／DataVersion／DFU。
  Initial witness保存exact server／UUID／player／presence／state identity；journal claims與Store audit
  成功後才作final exact recheck，不reproject／retry，且不得覆蓋較早terminal failure。Drift使claims
  discarded、index Incomplete、reclaim 0。
  全域first-failure依第18號§18固定18 checkpoints：programming／heap → Store → journal → inventory →
  directory／pair metadata → online identity → integrated observation → arbitration → UUID sort → relevant →
  source-local work → player roots → journal roots → grouped Store audit → final freshness → result／index。
- 【Index generation owner】Memory-only root-index generation唯一屬於一個
  `SkillRetentionRootAuditService` identity × 一個exact `MinecraftServer` object identity；每個slot
  獨立使用`long` `0..Long.MAX_VALUE`，不global static、不跨server共享、不持久化。No-entry對外是
  Incomplete且沒有published generation；新slot的internal baseline是0，第一個accepted audit
  reservation使用1。Tick、P4-C mutation generation、Store revision、journal generation與SavedData
  identity都不是本generation。
- 【Index reservation／publication】只有accepted global audit attempt與P4-E2 explicit index
  invalidation消耗一次generation。Audit依序完成null／programming、exact server、logic thread及
  active-lease／reentrant checks後、heap與任何source work前reserve `g + 1`，立即撤銷舊Complete
  backing／permit並進入同reserved generation的non-Complete state；success、Incomplete、OverLimit、
  ReconciliationRequired、final freshness failure與RuntimeException都在同generation完成，不再增加，
  Error／OOME原樣傳播且index留在該reserved generation的non-Complete state。E2每個accepted
  reconciliation batch只reserve一次並發布Incomplete，不reaudit／reproject／snapshot／reclaim；
  programming／wrong-thread／active-lease／
  reentrant failure都不改generation或index。這個prelude不是第26個counter，不改25維profile。
- 【Index states】Internal state machine至少包含NoEntry、`Incomplete(g)`、`AuditInProgress(g)`、
  `CompleteIndex(g)`、`CompleteIndexWithActiveLease(g)`、`GenerationExhausted(Long.MAX_VALUE)`與
  Removed；每次publication都是單一state replacement，不逐項發布partial index。
- 【Exhaustion／permit／lease／remove】`Long.MAX_VALUE - 1 → Long.MAX_VALUE`合法；current為MAX而
  audit／E2需要advance時不得wrap／saturate／reset，先清除舊Complete backing／permit，以新state
  identity發布terminal
  `GenerationExhausted(Long.MAX_VALUE)`與`Incomplete(GENERATION_EXHAUSTED)`，source work／roots／
  Store audit／snapshot／reclaim皆0，startup繼續且source data不變；同slot後續audit／invalidate
  idempotent，只有exact server stop的
  `removeServer`可移除。Complete permit每次consume先標used；second use或wrong service／server／
  thread／tick／state identity／generation會消耗permit並清除其authority
  references，但不改index／generation。成功consume以相同generation／backing進active lease；lease
  open／close不增加generation，active lease阻止audit與E2。B.9後close預設同generation demote至
  Incomplete；只有exact `Completed(0)`及完整source-unchanged零publication證明才回Complete，
  且兩者都不重發permit。
  `removeServer`可強制失效lease並清除backing／permit／slot而不增加generation；新的exact server
  object另從baseline 0開始，同一stopped object不得藉重插reset，移除後原handoff操作固定拒絕；
  不使用Cleaner／finalizer／background lease timeout。Permit與handoff除service／server／
  thread／tick外必須同時綁exact state identity與generation，handoff另綁exact lease identity；generation
  相等不是currentness的充分條件。
- 【Production construction／owner】P4-E2是第一個將production
  `SkillRetentionRootAuditService` instance接入composition的phase；runtime長期lifecycle owner
  固定為既有`SkillDefinitionStoreService`。`Gramarye` composition建立exact Store service，
  每個Store service instance在自身lifecycle內精確建立並以一個`final` field持有一個
  audit service，且必須在任何login reconciliation前存在；production constructor callsite必須
  exactly 1，且只能位於Store lifecycle owner
  或其exact package-private construction helper。不得使用static／global registry／service
  locator／lazy-per-login，`SkillSubmissionRecoveryService`與P4-E3均不得自行建立、替換或
  lazy-create另一個instance。
- 【Sole login trigger】`SkillSubmissionRecoveryService`固定是唯一
  `PlayerLoggedInEvent` owner。既有handler在same server logic thread且same synchronous call
  chain中固定執行：validate exact current `ServerPlayer` → P4-D recovery完成 → 保留typed
  recovery outcome → 呼叫P4-E2 continuation → return。不得增加第二listener／yield／
  future／executor／second dispatch／cross-tick／background／periodic／manual reconciliation；E2
  不得在P4-D recovery之前觀察或修改Attachment。
- 【Exact identity injection／phase closure】Recovery service只接收一個窄、
  constructor-injected的E2 reconciliation dependency，該dependency必須在composition時已綁定
  exact `SkillDefinitionStoreService` identity、該Store持有的exact audit-service identity與E2所需
  其他exact dependencies；handler不直接看audit／index／Store／history／raw state，也不接受
  任意caller-provided audit service、supplier、generic callback／service locator或reflection。P4-E2
  closure必須包含這條active production login wiring；pure core／test-only caller／未接入login的
  package-private operation或延後至E3均不可宣稱E2 COMPLETE。B.5不裁決typed recovery
  outcomes的admissibility matrix，也不裁決E2 atomic reconciliation／prune細節；這些留給
  B.5 closure後重新開始的E2 read-only review。
- 【E3 exact-same-instance reuse】P4-E2與P4-E3必須重用Store service的exact same
  audit-service object identity。Server start／stop不替換service owner；per-server slot仍依exact
  `MinecraftServer` object identity隔離，stop只由同一service field對exact server執行
  `removeServer`，新server object由同一service instance建立新slot。P4-E3在既有唯一
  `ServerStartingEvent` chain中以該same field執行fresh audit／Complete handoff／snapshot／controlled
  reclaim；其audit-service production constructor delta與login wiring delta
  均必須為0。

### P4-E0-B.6 qualification-only direct observation

B.6固定下列唯一direct coordinates；這些座標只用於synthetic qualification，
不是runtime、index、persistence或gameplay authority：

| Coordinate | Unique definition |
| --- | --- |
| `RECOVERY_OUTCOME_DIRECT_COORDINATE` | `HANDLER_LOCAL_EXHAUSTIVE_CLASSIFICATION` |
| `E2_RESULT_DIRECT_COORDINATE` | `COORDINATOR_LOCAL_EXHAUSTIVE_CLASSIFICATION` |
| `E2_INVALIDATION_ATTEMPT` | actual central reconciliation invalidation operation invocation |
| `E2_INVALIDATION_ACCEPTED` | actual `Accepted` result branch after normal return |
| `E2_SET_DATA_ATTEMPT` | actual JVM callsite immediately before the exact E2-bound `ServerPlayer.setData(PLAYER_SKILLS, replacement)` invocation |
| `E2_SET_DATA_SUCCESS` | immediate normal-return checkpoint after that exact invocation |

- 【Recovery direct】`recoverPersistedPlayer(...)`回傳actual sealed `RecoveryOutcome`後，
  當該exact object仍是sole login-handler／consume call-chain local時，立即以exhaustive
  no-default pattern switch分類。只記錄exact variant、`entriesCleared`、`stepsReplayed`、
  既有bounded reason／kind及`recoveryChanged = entriesCleared > 0 || stepsReplayed > 0`。
  Same object仍傳入`RecoveryContinuation.consume(...)`，但object identity不傳Store、不進
  completed record、不序列化／公開／跨tick；不從journal bytes、`RecoveryKind`或post-state
  推導direct result。
- 【E2 result direct】Actual package-private `P4E2ReconciliationResult`從`reconcile(...)`
  回傳後先作coordinator local，在public reviewed `void` wrapper丟棄前以exhaustive
  no-default switch分類。Record只保存actual variant、既有bounded counts／reason與optional
  accepted-generation-presence；result維持package-private、dependency維持`void`。禁止public
  result return／serialization、state-equality inference、second reconcile或log-only proof。
- 【Invalidation direct】Attempt只在唯一central helper真正進入
  `SkillRetentionRootAuditService` exact invalidation operation計數，上層mutually-exclusive
  entries不重複計數；accepted只在operation正常回傳後的actual `Accepted`
  branch計數。`GenerationExhausted = attempt 1 / accepted 0`。Post-index／pre-wrapper
  `Error`／OOME不標accepted、不發partial record，原identity外拋。不得用generation、
  index state或E2 result反推。
- 【`setData` direct】Attempt不是publisher entry；它在actual E2-bound JVM invoke前立即
  計數，success在invoke正常返回後立即計數，並早於enum lookup、result wrapper、
  callback或可失敗diagnostic publication。Publisher先回`STATE_CHANGED`時publisher call = 1
  但`setData = 0/0`；P4-D recovery、submission或shared-method其他caller不計入，shared
  path只能用exact E2-bound nominal session區分。`APPLIED`只是normal semantic
  cross-check，不是success coordinate。Post-return／pre-`APPLIED` `Error`／OOME可已發生
  raw success，但official record必須abort，production mutation不rollback，原throwable外拋。
- 【Bounded state】V0只授權`IDLE -> ARMED -> RECORDING -> COMPLETED -> CONSUMED ->
  IDLE`與`ARMED/RECORDING -> ABORTED/CLEARED -> IDLE`。Mechanism必須test-armed、
  instance-owned、memory-only、single-use、same synchronous call chain與normal-runtime inert；每exact
  owner最多一active與一completed-unconsumed record，無queue／history／last-login record。
  Session綁定exact mod／service、server、authenticated UUID、logic thread、bounded case／phase與
  exact session identity。Completed只含enums／booleans／bounded primitives／UUID／case／phase／
  marker，不含Server／Player／Tag／Ready state／Store／journal／root／Path／Throwable／callback／
  raw bytes。Consume／discard／failure／server stop立即clear strong references；Error／OOME
  allocation-free clear、no partial publication、original identity propagation。
- 【Closed nominal package crossing】Qualification-only受限例外最多允許一個public
  top-level platform-facing facade、必要的public nested sealed session／view types與nonpublic
  construction／implementation。Valid session只由exact allowlisted synthetic source經same-package test
  adapter建立；external caller不得construct／subclass／arm／forge／cross-consume或取得
  internal diagnostics。Public／protected surface禁止internal result／raw state／Store／SavedData／
  journal／owner／roots／Path／bytes／Throwable／callback／generic source／`Object`／raw type／
  unchecked cast／suppression。這是exact-source／unnamed-module contract，不是惡意Java
  sandbox保證。
- 【Conditional receiver route】只在後續locked-artifact technical review證明official
  per-`ModContainer` FML extension或semantic-equivalent exact-instance route具備registration／
  retrieval API、exact identity／lifecycle／pre-login access、GameTest first／restart new-instance與
  no client-only misuse時，才授權該route。B.6不指定method、不宣稱API存在或
  review PASS。無合格route必須`STOPPED — NO SAFE RECEIVER ROUTE`，不得fallback至
  public static locator、`ThreadLocal`、global registry、reflection、second listener、log／file／
  JFR／system-property side channel或second reconcile。
- 【Local cells／no split】最多允許submission-local記actual recovery classification、store-local記
  continuation／E2 result／invalidation、player-local記exact E2-bound `setData`。三者共用same
  nonforgeable session，只記自己local fact，不跨package傳raw object／callback／保留actual
  object，不建立second coordinator或改production result／failure／control flow；因此不改
  P4-E2 `PASS; NO SPLIT`。Store-only若已足夠可省略無用cell，但不可降低coordinate。
- 【Forbidden alternatives】不授權static registry、`ThreadLocal`、global service locator、
  second login listener、callback／observer／function injection、log／stdout／file／JFR evidence、
  reflection／Unsafe、second reconcile、state／generation-derived counters、public E2 result、transient
  diagnostic player `AttachmentType`／actual Attachment-map session、SavedData runtime backlink、
  persistent diagnostic record、queued／multi-login history或normal-login last-result record。需要任一項必須Stop。
- 【Evidence JSON】Production cell不寫檔；synthetic test／probe只在valid session成功consume
  completed record後才可atomic publish canonical UTF-8 regular non-symlink JSON，最大65,536
  bytes，duplicate／unknown／missing field拒絕，failed／partial／aborted session無artifact。Exact
  fields至少為`schema_version`、`case_id`、`phase`、`recovery_handler_calls`、
  `typed_recovery_outcome`、`entries_cleared`、`steps_replayed`、`recovery_changed`、
  `e2_continuation_calls`、`e2_result_variant`、`invalidation_attempts`、
  `invalidation_accepted`、`invalidation_generation_present`、`e2_set_data_attempts`、
  `e2_set_data_successes`與`completion_marker`；Attachment／Store checksum在cell外附加。
  不得寫absolute path、raw object／bytes或message／stack／Throwable。
- 【READY／negative controls】First與restart同時要求recovery calls 1、`NoPending`、
  cleared/replayed 0/0、changed false；E2 calls 1、`NoChanges`；invalidation 0/0、generation
  absent；E2 `setData` 0/0。Bytes／Attachment／Store／generation／call graph只是cross-check。
  Negative controls至少包含`NoChanges` 0/0＋0/0、recovery-changed-only true＋1/1＋0/0、
  `Changed` 1/1＋1/1、`GenerationExhausted` 1/0＋0/0、publisher drift＋`setData`
  0/0、accepted-before-setData failure＋invalidation 1/1＋setData success 0，以及Error／OOME
  no-consumable-partial-record／original-identity propagation。Normal GameTest required count仍為12。
- 【Zero semantics／preserved obligations】Unarmed normal path不建立record／classification result／
  file，persistence／network／thread／callback／second listener。Recovery、E2 result、invalidation、
  `setData`、Attachment content、Store／journal mutation與index generation delta全為0。B.6不改
  25 counters、DataVersion 3955、DFU 0、1,536-MiB floor／effective-MaxHeap coordinate、B.4、
  R2Q profile／cases／identity／evidence、E3 same-service／snapshot／reclaim／fixed-heap obligation；
  direct evidence不取代E3 Gate。

### P4-E0-B.7 qualification receipt COMMIT_READY boundary

- 【Scope】B.7是documentation-only authority patch，只修正「final no-replace receipt link
  must complete by H」這個已證不可實作的命題。它不建立Candidate12 harness／test、
  不執行cold campaign、不重選T1；T2 supervisor-owned observation仍是唯一選定的
  preparation protocol。B.7 final qualification receipt與B.6 test/probe direct-evidence JSON
  是不同authority layer，互不取代。
- 【Deadline】H的唯一名稱與語意是COMMIT_READY_DEADLINE。H嚴格約束
  COMMIT_READY與LINK_PREINVOKE_ELIGIBLE，不約束唯一final no-replace link的normal-return
  時間；H數值保持5.750秒。
- 【COMMIT_READY】H前必須完成formal observation、ps／jcmd正常完成與完整reap、
  native process identity、CLEAR／SAME_EXPECTED_DAEMON classification、全部正式欄位、
  immutable receipt payload、fixed source path／inode與target path、same-filesystem proof、target expected
  absent、全部link參數與seal。此後不得仍有parse、classification、hash、serialization、
  fsync、rename、readback或reconciliation。commit_ready_time必須不晚於H。
- 【Late readiness】任一ready條件或seal晚於H固定為COMMIT_READY_DEADLINE_EXCEEDED；
  link invocation count = 0且不得建立receipt。T2 4.100秒branch-aware ledger只涵蓋
  COMMIT_READY preparation；link latency不是bounded stage。
- 【Preinvoke】唯一LINK_PREINVOKE_ELIGIBLE check必須緊鄰syscall，並同時證明
  commit_ready、current monotonic time不晚於H、link attempts為0、target未使用且session未
  abort。Check與link之間禁止callback、logging、file read、allocation-heavy work、retry、
  yield、second process、wrapper／DONE或另一clock branch。Late check固定不呼叫link。
- 【Publication】COMMIT_READY後只允許同一supervisor直接執行一次same-filesystem、
  atomic、no-replace link／linkat。禁止copy、overwrite／replace、rename-overwrite、
  temporary retry、fallback API、wrapper、ln subprocess、DONE carrier與second publication path。
- 【Success】只要ready與eligibility都不晚於H，link可在H前或H後normal return。Exact
  authorized link的same-process normal return是唯一RECEIPT_COMMITTED coordinate；
  crossed-H normal return仍可PASS，且不得以post-return clock重新否決。
- 【Failure】EEXIST／ENOENT／EXDEV／EACCES／EPERM／EIO／ENOSPC／EINTR、
  RuntimeException及其他direct non-success為RECEIPT_COMMIT_FAILED。Error／OOME保留原identity；它們與
  process termination、external timeout、unknown completion或沒有觀察到same-process normal
  return為RECEIPT_COMMIT_UNADJUDICATED。兩者都禁止retry、alternate path、target
  readback／stat／exists／open、reconciliation、backfill與cleanup-to-success。
- 【No restart authority】Receipt path存在本身不能重建success。禁止restart scan、
  orphan adoption、later manifest reconciliation或backfill。可能orphan只留在該attempt的
  unique namespace；namespace不得重用。
- 【Post-link】Final link是observation最後persistent mutation。Normal return後禁止file
  write／link／rename、receipt／manifest mutation、readback、checksum、fsync claim、
  reconciliation、callback、external process或retry；只允許nonallocating local status
  propagation、normal return與normal exit。
- 【No durability overclaim】只宣稱live process觀察到no-replace directory-entry
  publication normal return；不宣稱fsync、crash durability、cross-file transaction、reboot
  recovery、journal或directory persistence。
- 【Reporting】未來分開報告commit_ready_by_h、link_preinvoke_eligible_by_h、
  receipt_link_normal_return、receipt_link_crossed_h。crossed_h=true可PASS；不得把
  transaction finished／receipt committed／link completed by H當qualification requirement。
- 【Preserved strictness】H numeric value、settling deadline、poll interval、maximum
  observations、three CLEAR、ps、jcmd、PID／birth identity、client／worker／foreign checks、
  collector、heap、fork、test order、Gradle command、qualification counts、fixture、P4-E2、
  P4-C2、R2Q與P4-E3 obligation全部不變。
- 【Next Gate】B.7 closure後仍只開啟新的read-only technical review；必須證明exact
  macOS／JDK same-process API、same-filesystem no-replace、source inode sealed、unique target、
  adjacent preinvoke、one invocation、no retry／readback／post-link mutation、termination
  UNADJUDICATED、orphan不認領、T2 4.100秒preparation ledger與cross-H deterministic tests。
  若normal return後仍需額外operation才能形成authority，Candidate12必須STOP。

Future tests至少覆蓋ready-before-H的before／after-H return PASS、late ready／preinvoke零link、
single EEXIST／EXDEV／EINTR、RuntimeException、Error／OOME original-identity propagation、
mid-link termination、
orphan隔離、unique namespace、zero readback、one link callsite、zero wrapper／DONE／ln、
zero post-link mutation、cross-H不被post-clock否決、payload在COMMIT_READY後byte-identical及
process strictness不變。

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

本地B.7 patch不得宣告Candidate12 READY／implementation PASS、cold v3 OPEN、P4-C2 OPEN、
E2 COMPLETE或E3 OPEN；本輪不得建立Candidate12 implementation或cold-requal-v3 namespace。

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

## 8-B. 真相歸屬表與單一真相原則

- 【原則】每一種執行期資料恰有一個持久化真相；其他副本只能是可重建索引、指標或客戶端快照。
- 【自我修復】這是各型別一般原則；P4-E V0的player roots另受第18號修正案限制。
  Offline audit只標記defer-to-login，不修改disk；online P4-E2只在P4-D login recovery後
  作一次atomic prune。P4-E不使用periodic／background reconciliation。

| 型別 | 持久化真相 | 可重建資料 | 對帳時機 | 衝突裁決 |
| --- | --- | --- | --- | --- |
| SkillInstance | `RuntimePersistentStore` | 玩家 Attachment 的 `activeContinuationInstanceId` | 啟動載入、登入、按鍵解析 | Store 為準；死指標清除 |
| Marker（生物／造物錨） | 錨點 Attachment 中的標記本體 | 中央 MarkerIndex | 實體載入時註冊並檢查到期 | Attachment 為準 |
| Marker（位置錨） | `RuntimePersistentStore` | 中央 MarkerIndex | 啟動載入 | Store 為準 |
| Construct | 造物 Entity 自身持久資料 | 中央 ConstructIndex | Entity 載入時 | Entity 為準 |
| Schedule | `RuntimePersistentStore` | 記憶體排程佇列 | 啟動載入 | Store 為準；owner 不存在則取消 |
| Cooldown | 玩家 Attachment | 客戶端 SyncPayload | 登入與變更時 | 伺服器 Attachment 為準 |
| Mana | 玩家 Attachment | 客戶端 SyncPayload | 登入與交易後 | 伺服器 Attachment 為準 |
| Revision owner binding與定義本體 | P3-D `SkillDefinitionStore` aggregate；P4 Overworld SavedData adapter為唯一持久化位置 | 玩家latest／equipped引用、latest／owner-count index、編輯器Draft、active pin index | **提交落庫**、施放 pin、啟動重建complete roots | Store owner＋retained documents為準 |
| Presentation 設定 | SkillDocument revision 中的 AppearanceDocument | 客戶端解析快取 | 文件同步／資源重載 | 文件為準；缺資源採 fallback |

- 【Revision latest】Store 最大retained key是配置下一版的唯一latest truth與implicit retention root；retained history可稀疏，一般reclaim不得移除latest。Attachment latest只是玩家引用，不是owner／allocator truth。
- 【跨位置提交】Store SavedData 與玩家 Attachment 不是天然原子位置。P4-D 固定採
  Store-first：prebuild carrier／journal → Store commit → publish carrier／journal → dirty →
  Attachment immutable transition。Pending journal target 是 retention root；不得在 in-memory
  `setData` 後立即清除，必須等後續 persisted playerdata readback 確認 generation／pointer。
- 【Journal availability】Malformed／future／chain-invalid或Store-target-invalid journal保留exact
  opaque bytes且不dirty；Store read／pin仍可用，但submission／recovery／production reclaim composition
  停用，journal roots為Unavailable且P4-E global completeness固定為false。
- 【對帳】Store owner／documents 是 truth。Offline missing／owner-mismatched pointer只得
  `ReconciliationRequired(Deferred)`、disk不變、reclaim 0；online P4-E2在P4-D recovery後才
  可用P4-C唯一generation arithmetic建立完整replacement、最多一次`setData`。合法舊
  pointer不自動升Store latest；orphan revision不自動刪除或釋放quota。對帳後當輪
  不得reclaim，只有next restart的fresh audit才可能Complete。Duplicate persisted route／slot
  不採last-write-wins。
- 【標記生命週期】錨點在卸載期間被永久移除時，標記隨 Attachment 靜默消失，不補發「標記被移除」事件；暫時卸載不等於移除，Entity 再載入時由其持久資料恢復。
- 【禁止】同一物件不得同時把完整本體持久化在 Attachment 與中央 Store。

## 9. ItemStack 資料使用 Data Component

- 【決定】技能書、魔力道具、儲存技能的物品全部使用 1.21.1 Data Component，不使用物品 Attachment 或自訂任意 NBT。
- 【決定】Component value 使用不可變 record，正確實作 equals／hashCode。
- 【決定】需要存檔與網路同步的 component 同時提供 persistence Codec 與 network StreamCodec。
- 【限制】物品中不保存完整巨大 runtime state；只保存技能引用、必要 metadata 或受限的匯出資料。

## 10. 型別化識別與實體定位

- 【決定】使用型別化 ID：`SkillInstanceId`、`MarkerInstanceId`、`ConstructInstanceId`、`ScheduleId`。
- 【禁止】核心 API 不接受語意不明的裸 UUID。
- 【實體定位】持久化實體引用至少包含：Entity UUID、Dimension key、預期 Anchor type。
- 【規則】UUID 找不到、維度不存在或型別不符時，回傳明確 `UnresolvedReference`，不可拋出未處理例外。
- 【禁止】不得因引用某實體就強制載入任意遠方 chunk；需要 chunk ticket 的功能必須另行明確授權與限制。

---

## 第四層：Registry 與擴充邊界

## 11. Trigger／Action 型別 Registry

- 【決定】Trigger／Action「實作型別」使用 NeoForge custom registry + `DeferredRegister` 註冊。
- 【決定】registry entry 是 singleton 型別描述器，不是玩家技能實例或技能定義。
- 【決定】每個型別提供：
  - type ID
  - payload Codec
  - capability descriptor
  - validator
  - executor／matcher factory
  - client display metadata ID（不得直接綁 client class）
- 【決定】registry 在啟動完成後視為凍結，不支援執行中新增 Java 型別。

## 12. 能力描述必須機器可讀

- 【Action 能力】來源需求、輸出型別、目標型別、是否可分裂／連鎖／重複、是否修改方塊、是否轉移魔力、是否持續、是否需要存活實體。
- 【控制分類】Action 必須以機器可讀欄位標示：`NONE`／`SOFT_CONTROL`／`HARD_CONTROL`。若第一版尚未開放控制 Action，欄位與 Resolve 鉤子仍保留但不啟用記帳。
- 【Trigger 能力】事件型別、Anchor 能力、來源範圍、觸發粒度、是否要求技能接續狀態。
- 【決定】編輯器過濾、儲存驗證、伺服器執行驗證使用同一份能力描述與 validator。
- 【禁止】UI 與伺服器各自實作一套規則。
- 【禁止】核心合法性不可依大量 `instanceof` 特判。

---

## 第五層：執行上下文與事件模型

## 13. Context 分層

```text
SourceContext（不可變）
├─ casterId
├─ skillId + revision
├─ skillInstanceId
├─ rootNodeIndex
└─ originKind

EffectState（每個效果實例獨立）
├─ currentPower
├─ splitGeneration
├─ chainIndex
├─ repeatIndex
├─ visitedTargets
└─ runtimeTags

EventContext（單次事件）
├─ eventId / eventSequence
├─ eventType
├─ tick
├─ dimension
├─ position / direction
├─ sourceAnchor
└─ targetAnchor
```

- 【決定】三者不得混成一個可隨意修改的大物件。
- 【決定】`SourceContext` 與 `EventContext` 使用不可變 record。
- 【決定】EffectState 的複製、衰減與繼承只能經明確方法，避免淺拷貝共享 collection。

## 14. Anchor 能力模型

- `Anchor`：identity、dimension、position、isValid。
- `LivingAnchor`：傷害、死亡、狀態事件。
- `ConstructAnchor`：摧毀、解除、owner。
- `PositionAnchor`：固定位置與到期。
- 【決定】Trigger 只要求能力，不直接 cast Minecraft 類別。
- 【決定】Anchor resolve 可能失敗，所有使用者必須處理失敗分支。
- 【第一版】只實作 LivingAnchor 與必要 PositionAnchor；ConstructAnchor 等造物階段加入。

## 15. 模組內部事件

- 【決定】Minecraft／NeoForge 事件只在 EventBridge 出現。
- 【決定】Trigger 只接收模組內部事件，如 `EffectHitEvent`、`MagicDamageEvent`、`MagicDeathEvent`。
- 【決定】每個內部事件具有唯一 event ID、建立 tick、sequence、來源與資料快照。
- 【禁止】不得把 NeoForge Event 物件保存到下一 tick 或持久化。
- 【重入】EventEmitter 新產生的事件一律進入中央佇列，不在目前 call stack 無限制遞迴執行。

---

## 第六層：效果管線與交易

## 16. 統一效果施加管線

```text
ActionExecutor
→ EffectRequest
→ EffectResolver
→ EffectCommitPlan
→ EffectCommitter
→ EventEmitter
```

- 【決定】Action 不得直接呼叫 `hurt`、`explode`、`addEffect`、setMana 或修改方塊。
- 【Resolve】驗證來源、目標、維度、距離、陣營、免疫、遊戲規則、區域保護、資源、速率、全域預算，以及控制分類與受控時間記帳。硬控累積達門檻時，在本層執行全清與免疫，並攔截所有路徑的後續控制施加。
- 【CommitPlan】Resolve 階段完成所有可預先驗證的檢查，產生具固定步驟順序的 CommitPlan。
- 【規劃式提交】Commit 依 CommitPlan 執行；Minecraft 世界不是交易資料庫，單步仍可能因第三方事件取消、實體死亡或世界狀態改變而失敗，因此**不承諾回滾或同成同敗**。
- 【失敗政策】失敗步驟不自動重試、不回滾已生效步驟；每類 Action 必須定義補償政策。例如扣魔成功但所有主要效果均失敗時，以新的退魔交易補償；傷害成功但附加狀態失敗時，記錄並保留傷害。
- 【防重】Commit 中途出現例外時記錄 trace；禁止自動重提整份 CommitPlan，以免雙重傷害或雙重扣魔。

## 17. Minecraft 傷害整合

- 【決定】所有傷害走 `DamageSource`／`DamageType` 與原版／NeoForge damage pipeline。
- 【決定】DamageType 是 datapack registry 資料，使用 ResourceKey 引用，不以普通 Java registry 註冊。
- 【第一版】尊重原版無敵幀。
- 【未來】額外命中政策使用集中 `HitPolicy`，不可讓各 Action 各自繞過無敵幀。
- 【事件】避免把自己造成的傷害事件再次誤認為新的外部 Action；使用 event origin 與 re-entry guard。

## 18. 魔力交易與守恆

- 【決定】所有魔力變化經 `ManaTransactionService`。
- 【禁止】任何 Action 或 Item 直接修改 mana 欄位。
- 【交易】自然回復、道具回復、技能扣除、分享轉出、分享轉入、管理員調整都有獨立 reason code。
- 【規劃式轉移】分享魔力先驗證支付者、所有接收者、上限與總量，再依固定 CommitPlan 提交。魔力帳本內的扣款與入帳應由服務保證一致；若世界側附帶效果失敗，不回滾已完成的世界修改，而依明確補償交易處理。
- 【不變量】技能不得替施法者創造魔力；接收總量不得高於實際支付總量；分裂／連鎖／重複不得複製魔力。
- 【數值安全】使用有界整數或明確 fixed-point；禁止 NaN、Infinity、負零與未驗證浮點累積。

## 19. 方塊修改與第三方保護相容

- 【決定】方塊修改是獨立 EffectRequest 類型。
- 【檢查】mobGriefing、伺服器設定、玩家權限、事件取消與保護模組鉤子。
- 【預設】無法確認權限時拒絕修改，而不是放行。
- 【限制】每技能、每 tick、每區塊最大修改數。
- 【回滾】大型多方塊操作第一版不開放；日後開放必須有批次計畫與中斷策略。

---

## 第七層：中央排程與確定性

## 20. 單一伺服器主執行緒所有權

- 【決定】所有世界狀態、RuntimeManager、SkillInstance、Marker、Construct 與 ManaTransaction 只在伺服器主執行緒修改。
- 【Store confinement】P3-D `SkillDefinitionStore` 由server logic thread confinement使用；不承諾任意Java threads間linearizability，不加lock／`synchronized`，也不依賴Minecraft API檢查caller thread。Misuse是programming-contract violation。
- 【Store atomic】單次aggregate call在第一個truth mutation前完成全部typed checks；failure時owner/history不變，成功時完整replacement只以一次outer-map insert／replace發布。Mutation後不呼叫外部provider／Codec／validator；不承諾OOME或任意`Error` rollback，也不宣稱資料庫transaction。
- 【網路】payload decode 可在網路執行緒，但只能產生受限 Intent；實際驗證與執行切回主執行緒。
- 【禁止】背景執行緒持有或修改 Level／Entity。

## 21. 中央排程器

- 【排序鍵】`scheduledTick -> eventSequence -> skillInstanceSequence -> nodeIndex -> childSequence`。
- 【決定】sequence 使用 long 並定義溢位處理；不依 HashMap iteration order。
- 【決定】同 tick 同來源同事件的 Node 依 nodeIndex 執行。
- 【重入】新事件加入佇列，不直接遞迴。
- 【取消】每個排程有 owner instance、cancellation token、deadline 與 persistence flag。
- 【超額】玩法任務延後；視覺任務可丟棄。延後必須記錄 lag ticks。
- 【熔斷】超過每實例／每玩家／全伺服器待處理上限時中止來源技能實例並記錄原因。

## 22. 預算層級

- 每事件最大 Node 數。
- 每技能實例每 tick 最大執行數。
- 每玩家每 tick 最大執行數。
- 全伺服器每 tick 最大執行數。
- 最大事件深度與最大衍生產物數。
- 最大排程等待數與最大延遲時間。
- 【決定】所有上限可由伺服器 config 調整，但必須有不可突破的程式安全上限。

---

## 第八層：網路與安全

## 23. Payload 最小化

- 【決定】客戶端只送 Intent：技能槽、操作類型、client sequence、有限瞄準資訊。
- 【禁止】客戶端上傳完整 SkillDocument、ItemStack、Action 參數、目標清單或任意長 collection 來要求立即執行。
- 【決定】伺服器以自己保存的技能、物品與玩家狀態重建操作。
- 【限制】所有 serverbound string、collection、map、byte array 使用有最大值的 StreamCodec；禁止無界解碼。
- 【必要版本】使用包含網路物件配置漏洞修補的 NeoForge 21.1.229 以上。

## 24. Serverbound 驗證與限流

- 【驗證】玩家仍連線、存活、擁有技能、槽位正確、冷卻／階段合法、距離合法、維度一致。
- 【sequence】拒絕重播、重複或過舊 Intent。
- 【rate limit】按 payload type 與玩家限流；超額記錄、忽略，嚴重時踢出。
- 【禁止】不能信任客戶端時間、魔力、技能 revision、命中結果或「我正在瞄準某 Entity」的合法性。
- 【封包版本】PayloadRegistrar 協議版本與 mod schema 分開；不相容時清楚拒絕連線。

## 25. Clientbound 可見性

- 【決定】只同步 UI／渲染必要資料。
- 【決定】玩家私人技能定義、隱藏參數與其他玩家完整魔力不預設廣播。
- 【追蹤】實體／造物狀態只發給 tracking players。
- 【快照】客戶端可丟棄過期 sequence 的同步資料。

## 25-B. 表現層與玩家自訂外觀

- 【核心分離】玩法層只決定「發生什麼」；表現層只決定「看起來與聽起來像什麼」。音效、顏色、粒子與尾跡不得影響命中、傷害、範圍、魔耗、冷卻或 Trigger 判定。
- 【資料】每個 `SkillDocument` revision 保存不可變 `AppearanceDocument`；Node 可保存 `AppearanceOverrideDocument`。Appearance 是部分覆寫模型，欄位缺失表示 Inherit，不要求非空 Appearance 同時指定 sound、particle 或 trail。

```text
AppearanceDefinition
├─ primaryColor: inherit | ARGB int
├─ secondaryColor: inherit | ARGB int
├─ soundProfile: Inherit | Disabled | Specified(ResourceLocation)
├─ particleProfile: Inherit | Disabled | Specified(ResourceLocation)
├─ trailProfile: Inherit | Disabled | Specified(ResourceLocation)
└─ intensityMilli: inherit | bounded fixed-point
```

- 【缺省】Top-level `appearance` 缺失、`null` 或 `{}` 都寬鬆解讀為 Default；canonical encoding 統一寫 `{}`。Node override 缺失或 `null` 解讀為 None；canonical encoding 省略欄位。
- 【Profile 三態】Profile 欄位缺失表示 Inherit；`{"mode":"disabled"}` 表示 Disabled；`{"mode":"specified","id":"namespace:path"}` 表示 Specified。Canonical schema 不使用 JSON `null` 表示 Disabled；寬鬆 JSON 可接受 `null` 並正規化為 tagged Disabled。三態必須在 JsonOps、NbtOps 與兩者轉換間保持語意。
- 【解析順序】模組預設 → 技能預設 → Node 覆寫 → 單次 PresentationEvent 覆寫。
- 【缺資源政策】未知／缺失的音效、粒子或尾跡 Profile 只回退到預設表現並產生受限警告；不得把技能標為玩法損壞，也不得阻止施放。
- 【解碼隔離】`AppearanceDefinition` 與 `AppearanceOverride` 使用寬鬆且有界的獨立解碼路徑。可正規化輸入產生 Decoded canonical value；型別正確且具明確區間的數值越界（例如 intensity）clamp 至 hard boundary，並由 P3-B 產生 transient bounded warning。
- 【向前相容】未知 appearance 欄位忽略；已知欄位的不可解釋結構才使整個 blob 進入 Unparsed。
- 【Unparsed】ARGB 無法解析或 numeric 超出 bit-pattern 範圍、Profile mode 未知或結構矛盾、欄位型別錯誤等無法可靠解釋的錯誤，使整個 appearance blob 進入 Unparsed；不做逐欄位 salvage，並在 quarantine hard bounds 內保留完整 raw snapshot。
- 【Rejected】Appearance raw subtree 超過 `MAX_UNPARSED_APPEARANCE_DEPTH = 32` 或 `MAX_UNPARSED_APPEARANCE_NODES = 1024` 時進入 Rejected，不保留超限 raw tree。預設政策值為 depth 16、nodes 256；超 policy 但未超 hard 由 P3-B 產生 WARNING，不改變儲存狀態。Appearance relative depth 從子樹 root depth 1 起算。
- 【Fallback】Decoded／Unparsed／Rejected 狀態都不得使 gameplay `SkillDocument` 失效或阻止施放；presentation 使用 fallback。Unparsed raw accessor 必須 defensive deep-copy，不得暴露 mutable tree。
- 【P4 persistence state】Top appearance只持久化default／decoded／unparsed；override只持久化
  none／decoded／unparsed。Top `Rejected` save as default，override `Rejected` save as none／
  省略，不保存 rejection diagnostic、reason 或被拒 raw。每個 Unparsed 使用自己的
  `RawTreeEnvelope`。
- 【事件覆寫來源】單次 `PresentationEvent` 的外觀覆寫只能由伺服器已驗證的技能定義、Action descriptor 與執行狀態產生；不得直接採信客戶端覆寫值，也不得超過該 Action 宣告的外觀參數範圍。
- 【玩家輸入限制】玩家只可選擇伺服器允許的已註冊 Profile 與有界參數；不得輸入檔案路徑、URL、Java 類別、著色器程式或無上限粒子腳本。
- 【顏色】內部統一使用 ARGB int；JSON Codec 可接受十六進位文字，但解碼後立即正規化。

## 25-C. Presentation Profile Registry

- 【定案】採「Java 註冊 Profile 型別 + 資料驅動 Profile 實例」：
  - Profile **型別／Codec／客戶端 factory ID** 在模組啟動期註冊，遵守 §11 Registry 凍結。
  - `SoundProfile`、`ParticleProfile`、`TrailProfile` **實例**以 `ResourceLocation` 定位，使用 JSON／datapack 資料驅動。
- 【NeoForge 1.21.1 實作】若採自訂 datapack registry，必須透過 `DataPackRegistryEvent.NewRegistry` 註冊並提供持久化 Codec；需要客戶端解析的 Profile 必須另提供同步 Codec。不得錯用 `DeferredRegister` 建立 datapack registry。
- 【重載】Profile 實例可隨伺服器資料重載重新解析；技能 revision 只保存 Profile ID 與有界覆寫值，不把整份 Profile 實例複製進技能定義。重載後缺失或失敗依 §25-B fallback，不改變玩法定義合法性。
- 【邊界】Profile 只能引用已註冊的 `SoundEvent`、`ParticleType` 與模組提供的客戶端 renderer／factory ID，不得把 client class 直接存入共用定義。
- 【資源分工】datapack 定義 Profile 參數；resource pack 提供聲音、貼圖與其他客戶端資產。只有 datapack 而缺少對應資產時，依 fallback 呈現。
- 【擴充】第三方模組可在啟動期註冊新的 Profile 型別；伺服器／資料包作者可在不新增 Java 程式碼的情況下新增已知型別的 Profile 實例。
- 【能力描述】每個 Profile 型別宣告可調參數、上下限、是否支援顏色、方向／尾跡及預估視覺成本。

## 25-D. PresentationEvent、同步與視覺預算

```text
PresentationEvent
├─ eventType
├─ source summary
├─ dimension + position + direction
├─ appearance/profile IDs
├─ bounded overrides
├─ intensity
├─ visualSeed
└─ sequence
```

- 【事件類型】至少預留 `CAST_START`、`CAST_RELEASE`、`PROJECTILE_SPAWN`、`HIT`、`EXPLOSION`、`SPLIT`、`CHAIN`、`MARK_APPLY`、`MARK_TRIGGER`、`CONSTRUCT_SPAWN`、`CONSTRUCT_DESTROY`。
- 【網路】伺服器只傳事件、Profile ID、少量參數與 deterministic seed；不得逐粒子同步位置。客戶端依 seed 生成視覺。
- 【可見性】只傳給同維度、合理距離或 tracking 範圍內的玩家；私人 UI 表現可只傳施法者。
- 【預算】建立獨立 `PresentationBudget`：每玩家、每技能實例、每 tick 的事件數、粒子估算數、持續時間、尺寸、音量與範圍皆有硬上限。
- 【降級】視覺超額時依序降低粒子數、降低頻率、移除次要尾跡、合併事件或丟棄遠距純視覺；玩法事件照常。
- 【客戶端設定】玩家可降低粒子密度、關閉非必要音效或尾跡，不能改變玩法結果。
- 【繼承】分裂、連鎖、重複產物預設繼承來源 AppearanceDefinition；每個衍生 Node 可覆寫。

---

## 第九層：生命週期與持久化

## 26. 冷卻、接續、標記、造物、重複分離

- 冷卻：決定何時可建立新技能實例。
- 主動接續：只在冷卻結束前有效，引用特定 SkillInstance。
- 標記：有自身到期 tick、觸發次數與 persistence flag。
- 造物：有自身生命週期與 owner。
- 重複：有次數、間隔、deadline 與 owner instance。
- 【禁止】用單一「技能仍在運作」boolean 代表全部狀態。
- 【多實例預設】同一技能可同時存在多個 SkillInstance；冷卻結束後的新施放不會取消仍有標記、造物或排程的舊實例。若未來需要唯一實例，使用逐技能 policy flag 在施放檢查中限制，不改資料形狀。

## 27. 玩家死亡、登出、維度切換

- 【死亡】逐 Attachment、SkillInstance 與效果類型明確決定保留／取消；不得依預設隱式行為。
- 【登出】冷卻與需持久化狀態保存；非持久化輸入接續可取消。
- 【End 返回】永久 player skill Attachment 使用 NeoForge serialize + `copyOnDeath` policy；
  End 返回不得再手動 double-copy。其他 Attachment 仍須按各自政策區分死亡重生與終界返回。
- 【維度切換】所有 Anchor 與投射物引用重新 resolve，不保存舊 Level reference。

## 28. 時間語義

- 【決定】使用 server game tick／game time，不使用 wall clock 作一般效果時間。
- 【區塊卸載／玩家登出】時間繼續流逝；以 `expiresAtGameTime` 判斷。
- 【伺服器關閉】第一版暫停，不補算真實世界時間。
- 【重啟】載入後，已過期物件立即進入受預算限制的清理佇列，不在載入函式中一次大量觸發效果。
- 【注意】若未來加入離線自然回魔，必須是獨立政策，不得混入一般 tick 到期模型。

## 29. SkillInstance 清除與孤兒回收

- 【正常清除】冷卻已結束且沒有標記、造物、投射物、排程、等待事件與 revision 引用。
- 【孤兒清理】啟動載入後檢查失去 owner、未知維度、未知 revision、無法解析 Anchor 的物件。
- 【決定】孤兒不直接崩潰；依型別取消、轉為待修或延後重試。
- 【決定】所有清理必須 idempotent，可重複呼叫不產生第二次效果。

---

## 第十層：Node 語義不變量

## 30. Node 與引用

- 一個 Node = 一個 Trigger + 一個 Action。
- `SkillDocument.nodes` 的 List position 是該 revision 內唯一的零起算 `nodeIndex`；`NodeDocument` 不儲存重複 index，也不建立 `NodeId`。
- Node 只能引用較小 `nodeIndex` 的 Node 或合法外部 Anchor／事件；驗證屬 P3-B。
- UI 顯示使用 `nodeIndex + 1`；runtime 與 persistence 都使用零起算 index。
- 分裂、連鎖、重複是普通 Action，但要求合法前方來源，因此不可在 UI Node 1（`nodeIndex = 0`）。
- 同一來源、同一事件、同一 tick 的 Node 依排列順序執行。
- Action 改變的世界狀態立即生效；未改變的 EventContext 快照不變。
- Action 失敗預設不停止其他同事件 Node，除非 Trigger 明確依賴其成功事件。
- 存在 Unknown classification 時，編輯器不得執行無法安全更新隱藏 reference 的 Node reorder。

## 31. 衍生效果繼承

- 衍生效果保存同一 SourceContext，建立新的 EffectState。
- 繼承未來 Trigger 規則，不繼承母效果已發生事件。
- Trigger 必須明確選擇「僅原始」或「包含衍生效果」。
- 分裂：最大代數、最低威力、最大總產物。
- 連鎖：最大次數、距離、重複目標政策、visited set 上限。
- 重複：最大次數、最小間隔、deadline。
- 【禁止】任何 visited set、runtime tag 或 lineage collection 無上限增長。

## 32. 多段主動施放

- 非冷卻時按鍵建立新 SkillInstance 並觸發第一階段。
- 冷卻中按鍵只能推進 `activeContinuationInstanceId` 指向的實例。
- 階段狀態保存在 SkillInstance，不只保存在玩家。
- 冷卻結束後接續失效，不影響已存在造物、標記與排程。
- 同一階段多 Node 依排列順序全部執行。
- 來源失效、魔力不足或 validation 失敗時是否消耗階段，必須由 Trigger policy 明確定義並可測試。
- SkillInstance 被熔斷、中止、清除或完成最後接續階段時，立即清除指向它的 `activeContinuationInstanceId`；既有冷卻不受影響。

---

## 第十一層：設定、除錯、測試與發布門檻

## 33. Config 分層

- `SERVER`：預算、上限、PVP、方塊修改、持久化政策。
- `CLIENT`：純 UI／渲染偏好。
- 【禁止】客戶端 config 改變玩法結果。
- 【決定】安全硬上限不可被 config 提高，只能在其範圍內調整。

## 34. 除錯與可觀測性

- `/skill inspect <player|instance>`：列出 revision、階段、排程、標記、造物、引用。
- `/skill trace start|stop`：記錄 Trigger 判斷、Action resolve、拒絕原因、Commit、事件 sequence。
- `/skill validate`：重跑技能合法性與 schema 檢查。
- 【日誌】不得輸出未經限制的玩家自訂 JSON 或造成 log flooding；trace 有時間與筆數上限。
- 【效能】提供每 tick 執行量、延遲任務、被熔斷實例與 top offenders 統計。

## 35. 測試矩陣

### 純 Java 單元測試

- Codec round-trip、未知型別保全、migration。
- Node 合法性與能力匹配。
- P3-D1：owner/history truth、read API、hard ceilings、detached snapshot、sparse retained history與唯一successor。
- P3-D2：quota／owner／CAS／capacity／insert同mutation、zero typed partial mutation、exact result、repeat conflict與無commit-time exhaustion variant。
- P3-D3：multiple pin、idempotent close、latest implicit root、complete-root fail-closed與non-latest sparse reclaim。
- 魔力守恆與原子交易。
- 排程排序、取消、重入與 idempotency。
- 分裂／連鎖／重複上限。
- P4-A1：JSON／NBT／mixed-family same-family structural round-trip、RegistryOps／compressed
  JsonOps rebind、invalid family/context與A1 raw／document bounds。
- P4-A2：Store／History／Revision physical bounds、logical opaque-token conformance
  migration、duplicate route保留至
  restore與四組獨立migration／restore gates；Revision outer exact只驗inclusive admission，
  canonical V0最大值與inner document capacity依18號修正案分開測試。
- P4-A2：`SkillDocumentStorePersistenceFacade.encodeCurrent` canonical／context-preserving
  bytes、bounds與alias isolation；current／legacy public load都經migration seam，API恰好兩個
  P4-A2 public facade classes，且無public current-only decode／hydrate／load bypass。
- P4-A2：shell-only migration step 對 JSON／NBT／RegistryOps／tokenized view 產生相同
  outer output 與 facts；payload content／family／context 不影響 branch，opaque slot 不變，
  raw bytes 不出現在 migration-visible tree，`resolveFromRaw` 與 P4 view 結果一致。
- P4-A2：negative／impossible count、wrong element type 為 `Malformed*Envelope` 且
  restore 0 次；4,097 histories、257 same-owner histories、129 per-skill revisions 與
  32,769 global revisions 均為 `StoreRestoreRejected(CapacityExceeded)` 且 restore 恰好一次，
  不得分類為 malformed 或 P4 count-capacity failure。
- P4-A3：immutable carrier builders、checked aggregate totals與64 MiB fixed-heap validation。

### GameTest

- 傷害、死亡、無敵幀、爆炸、方塊權限。
- 玩家死亡／End 返回／維度切換。
- chunk unload、世界儲存與重載。

### 專用伺服器測試

- 無 client class 載入。
- 多人 payload 惡意輸入、重播、超速與超長資料。
- 停服重啟、舊 schema 世界、缺少擴充型別。
- 高量分裂／連鎖壓力測試與熔斷。
- 雙人連線：分享魔力、多段施放、接續指標跨登出／重連行為。
- Revision：提交即落庫、施放只 pin、施法者離線後延後 Trigger 可解析固定 revision、latest永遠保留、外觀微調產生的未引用non-latest revision可安全回收。
- 表現層：不同客戶端粒子設定不影響玩法；缺 Profile 正確 fallback；大量視覺事件只降級顯示、不丟失玩法效果。
- P4-B1：whole-root／inner exact fields、zero-length no-journal sentinel、byte-ceiling座標、finite
  accounter、outer migration、A2 load＋A3 rebuild與no-partial Ready candidate。
- P4-B2：strict single-member gzip／compressed與decompressed EOF、one-time Overworld cache install、
  Ready／Quarantined／Unavailable、prebuilt save callback、完整dirty matrix、restart round-trip與
  1 GiB fixed-heap full-size load／save Gate。P4-C另以固定1 GiB heap驗證exact 16 MiB
  PreservedRaw load／save／restart／death／End與maximum + 1 marker lifecycle；P4-D～E另驗證
  Store-first journal crash windows、persisted-readback clear、offline roots fail-closed與no chunk load。
  P4-D另須在單一fixed-1-GiB process同時保留full current／prospective Store、largest valid
  4096-entry journal、SavedData deep copy與Attachment transition；既有分離memory Gates不能替代。
  P4-E3另須在單一fixed-1,536-MiB process使用production scanner／index／reclaim
  composition，同時保留第18號§18的25維exact profile、1,024次full P4-C admissions、
  65,536 raw roots、4,096 journal targets、full Store／carrier／deep copy與prospective filtered
  carrier，並覆蓋每維MAX+1、effective-MaxHeap三狀態與reconciliation N／next-restart N+1。
  Heap process controls精確包含1,536 MiB G1／Parallel／Serial／ZGC qualified、locked
  Temurin/macOS aarch64的1,535 MiB G1 alignment-positive、1,024 MiB G1 below-floor，以及pure
  injected floor − 1／floor／floor + 1 comparator；這些是test roles，不是新增ceiling。R2Q research
  evidence不能取代這個production Gate，1,536 MiB也不是universal minimum。Fixture選用integrated
  startup owner時，actual qualifying child必須執行該path；未選用時須有reviewed machine-checked
  structural proof鎖定same-owner disk exclusion、replacement而非dual hydration、no retained
  compressed／gzip state及no second whole-tree copy。該proof不比較object size也不取代actual child。
  P4-E0-B.10把Gate固定在
  sole synchronous `ServerStartingEvent` production audit coordinate；該座標的online player與selected
  online owner都精確為0，Gate仍執行production empty online inventory path，不偽造startup前player，
  並由lifecycle-reachable來源維持relevant 2,048與raw roots 65,536 exact maximum。E1 online契約
  不變；未來新增post-login／post-tick／command／background audit caller即重新開啟online memory
  qualification。
  完整逐項矩陣以
  [18號P4修正案 §21](18_P4持久化與組合修正案.md#21-required-tests)為準。
- P4-E index generation：NoEntry／baseline 0／first reservation 1，audit success與每種normal／
  RuntimeException／Error／OOME terminal都恰消耗一次，E2每batch一次；MAX−1→MAX、MAX後terminal
  exhaustion、zero source work、old Complete清除與repeated idempotence；programming／wrong-thread／
  reentrant、permit misuse、lease open／close及removeServer均不增加。另驗permit misuse consumed但
  index不變、active lease阻止audit／E2、stop強制清lease、new server slot獨立，以及Complete／handoff
  同時以exact state identity與generation防ABA；generation不得進serialization／SavedData／R2Q。
- P4-E production owner／trigger：production audit-service constructor callsite精確為1且owner
  為`SkillDefinitionStoreService`；E2／E3取得same object identity而E3 constructor delta為0；
  sole `PlayerLoggedInEvent` owner精確為`SkillSubmissionRecoveryService`，且P4-D recovery
  call先於same-call-chain E2 continuation。另驗no second listener／next-tick／background、缺
  production wiring時E2 Gate失敗、wrong dependency／identity pairing拒絕、repeated login不新建
  service、server restart不替換owner、exact-server slots仍identity-isolated、E3只重用同一
  instance，且public／protected API不暴露audit internal type。
- P4-E direct qualification：actual RecoveryOutcome／E2 result exhaustive no-default local
  classification、invalidation operation-entry／Accepted-branch、actual E2-bound JVM `setData`
  pre-invoke／immediate-return coordinates及`APPLIED` cross-check。驗publisher drift、shared non-E2
  caller、GenerationExhausted、post-mutation／pre-wrapper Error／OOME與no indirect inference。
- P4-E diagnostic lifecycle：bounded state的single-use／wrong-session／wrong-thread／stop／abort、
  closed nominal facade無external construction／forgery／raw exposure、最多三local cells不形成
  split，並掃描no static／global／reflection／second listener／diagnostic Attachment／SavedData
  backlink／callback。FML route必須在獨立locked-artifact review證明official exact-instance
  registration／retrieval後才可實作；無safe route直接Stop。
- P4-E diagnostic evidence：canonical UTF-8 JSON exact fields、65,536 bytes exact／+1、
  symlink／duplicate／unknown／missing／partial-publication rejection；READY first／restart exact
  `NoPending`／`NoChanges`／invalidation 0/0／`setData` 0/0，以及`NoChanges`、recovery
  changed、`Changed`、`GenerationExhausted`、publisher drift、accepted-before-setData failure、
  Error／OOME negative controls。Unarmed path要求record／file allocation與所有production semantic
  delta為0；normal GameTest count、25 counters、R2Q與E3 Gate不變。

## 36. 發布阻擋條件

以下任一未通過，不得發布可供長期遊玩的版本：

- 舊世界 migration 測試失敗。
- 未知型別會丟失 raw payload。
- serverbound payload 存在無界 collection／string。
- Action 可繞過 EffectPipeline 或 ManaTransactionService。
- Runtime state 可從非主執行緒修改。
- SavedData 修改未 setDirty。
- 玩家死亡／End 返回會重複或丟失 Attachment。
- 分裂／連鎖／重複可突破硬上限。
- 專用伺服器因 client-only class 崩潰。
- 表現層 Profile 可影響玩法結果，或客戶端外觀設定能改變伺服器判定。
- 視覺事件可繞過 PresentationBudget 形成無界封包或粒子生成。
- 已提交但尚未施放的 revision 未落庫，或施放流程仍會重寫定義本體。
- 外觀解碼失敗會連帶使玩法定義損壞或阻止施放。
- Mixed-family document 被拒絕，或任一 raw subtree 被跨 JSON／NBT family 轉換。
- 任一 encoded-byte ceiling 的 maximum + 1 仍可載入／保存，或 Unlimited quota 可突破
  technical byte ceiling。
- Existing invalid SavedData被當成missing而建立empty Store，或在第18號totality boundary內已交付
  serializer body的existing per-attachment Tag未區分in-bound PreservedRaw與oversize OversizeMarker，
  或任一migration／decode／restore failure安裝partial Store。
- SavedData接受第二gzip member、compressed／decompressed trailing bytes，或missing pending blob被
  當成empty journal。
- SavedData `Quarantined`／`Unavailable`仍可dirty或save、save callback可輸出empty／stale carrier，
  或P4-B 1 GiB Gate失敗；player Attachment的in-bound raw structural round-trip、marker restart或
  P4-C 1 GiB exact-limit lifecycle Gate任一失敗亦阻擋發布。
- Store commit 後才首次執行一般 Codec／capacity check，或 Attachment-first ordering 繞過
  Store-first journal。
- P4-D journal使用`writeUnnamedTag`／named-root framing、malformed被當empty、root Unavailable仍允許
  commit／reclaim，或single-process combined 1 GiB Gate失敗。
- Journal 在 persisted playerdata readback 前清除，或 generation overflow 未 fail closed。
- Offline roots不完整仍best-effort reclaim，只掃online players即宣稱Complete，先root
  deduplicate才驗capacity，以root-only parser繞過P4-C admission，或在offline／E1／E2／
  reconciliation當輪執行reclaim；online observation重跑admission／serializer／tree／size／
  DataVersion／DFU、同UUID形成多個truth、採online-first分區、未排除disk／integrated，或freshness
  drift仍發布Complete也一律阻擋。P4-E3 exact production profile或B.10 lifecycle-reachable startup
  envelope若在product-selected 1,536-MiB tier OOME／timeout，或sole startup caller／online exact-zero
  無法鎖定，也阻擋發布；不得縮fixture／ceiling、拆envelope或自行提heap。
- Integrated snapshot被要求copy／byte-array序列化才能計數、重查`DataVersion`、再次
  DFU、與同UUID disk雙重計數、跨tick保存，或無法在same-call-chain作exact identity
  freshness recheck。Fixture選用的integrated path未在1,536-MiB child執行，或未選用integrated且
  structural proof不成立，同樣阻擋發布。
- SavedData API 宣稱 fsync／disk write 成功或 Store／Attachment durable atomic。

---

# 審查修正案採納結論

| 修正 | 結論 | 原因 |
| --- | --- | --- |
| 真相歸屬表 | 必要 | 消除 Attachment、SavedData 與記憶體索引的雙重真相與幽靈資料風險 |
| 被引用 revision 定義落庫 | 必要 | 支援施法者離線後的標記、造物與排程解析 |
| Commit 原子性降級 | 必要 | Minecraft 世界操作無法提供資料庫式全面回滾 |
| 硬控分類與記帳 | 骨架必要 | 即使第一版不開放控制技能，也需避免日後修改所有 Action descriptor 與 Resolver |
| 接續指標清除 | 必要 | 避免死指標與冷卻期重複解析失效實例 |
| 多實例並存明文化 | 必要 | 冷卻與世界效果生命週期已刻意分離 |
| 未知型別重試時機 | 必要 | Registry 啟動後凍結，不應暗示 runtime 熱插 |
| 雙人測試移至專用伺服器 | 必要 | 真實網路、重連與玩家同步行為不能由簡單 mock player 充分覆蓋 |
| Revision 提交即落庫、施放僅 pin | 必要 | 否則已提交但未施放的 revision 沒有持久化真相，且與玩家只存引用的規則矛盾 |
| Profile 型別／實例分離定案 | 必要 | 凍結文件不可保留互斥實作岔路；同時保留 Java 擴充與資料包內容擴充 |
| 外觀解碼隔離 | 必要 | 純表現資料損壞不得連坐玩法定義 |
| 1A 走通 PresentationEvent | 必要 | 表現事件發射是骨架咽喉，延後驗證會提高重構風險 |

- 【限定】「標記本體放錨點 Attachment」適用於與錨點同生死的生物／造物標記；若未來加入「即使錨點被移除仍保留並轉移」的特殊標記，必須建成另一種位置／世界錨型別，而不是破壞本表的單一真相。
- 【限定】表現層為骨架級擴充點，但具體音效、粒子模板、顏色範圍與編輯 UI 仍屬內容層。
- 【連動決定】控制類、蓄力類或其他需要清楚預兆的 Action，在 capability descriptor 中宣告可用外觀參數範圍，其下限由 Action 內容設計期設定於可見／可聞水位，並走 §12 同一 validator。若未來公開第三方 Action 註冊生態，必須在 §25-B 的中央外觀解析出口增加全域最低表現 clamp，因第三方宣告不再完全受本模組控制。
- 【裁減確認】不把「系統保底表現層」、`salience` 暫態欄位、受眾範圍欄位、閃爍頻率上限列為現階段骨架；它們可在既有 descriptor、派發與網路版本鉤子上後補，不造成持久化資料重構。

---

# 開發順序（嚴格版）

## 0. 地基

- 固定 Java 21、Minecraft 1.21.1、NeoForge >= 21.1.229 的具體 build。
- 建立 registries、Attachment、Overworld SavedData、payload 通道、schema/revision envelope。
- 建立真相歸屬表對應的 Store／Attachment schema 與對帳器。
- 建立 AppearanceDefinition、PresentationEvent 與最小 PresentationBudget 骨架。
- 建立內部 event、EffectPipeline、ManaTransaction、Scheduler、trace。

### 地基階段的工程 P3～P4 切分

```text
P3-A：SkillRevision int、SkillDraft／SkillDocument／NodeDocument、
      Appearance storage schema 與 Codec
P3-B：migration、Envelope resolution、validation、ValidatedSkillDefinition
P3-C：Draft formalization、server-side SkillId mint contract、authoritative
      submission precheck、optimistic concurrency precheck、proposed revision、
      既有 resolution／validation／projection 與 immutable SkillSubmissionPlan；
      不寫 Store、不配置正式 revision
P3-D1：production pure-Java SkillDefinitionStore aggregate、owner/history truth、
       sparse retained revisions、read API、hard ceilings、successor與detached snapshot
P3-D2：immutable quota snapshot、owner／CAS／capacity／insert同一atomic mutation、
       zero-partial typed failure、commit result與正式revision allocation
P3-D3：active pin handles、complete retention roots、latest implicit root與reclaim
P4-A1：SkillOwnerId Codec、JSON／NBT／RegistryOps family/context、bounded raw Codec、
       RawTreeEnvelope、mixed-family current document encode／hydrate、appearance physical mapping
       與shared logical bounds；無Store schema／migration／carrier／SavedData
P4-A2：store_schema_version、Store／History／Revision physical schema與三層byte ceilings、
       Store physical migration、logical opaque-token conformance migration、migration before
       hydration、current snapshot／P3-D restore、bounded facts、document persistence facade與
       current Store blob encode／load；
       無saved_data_schema_version／carrier／journal／commit preflight／heap probe
P4-A3：pure immutable hierarchical Store carrier rebuild／replacement／reclaim filtering、checked
       totals與64 MiB fixed-heap validation；無lifecycle／publication／dirty／commit／journal／
       Attachment／composition
P4-B1：saved_data_schema_version、whole-root／inner exact framing、zero-length no-journal sentinel、
       bounded opaque pending blob、outer migration、bounded decompressed carrier、A2 load＋A3 rebuild、
       Ready candidate；無world／filesystem／cache lifecycle
P4-B2：primary file ingress、strict single-member gzip、custom one-time load、
       Ready／Quarantined／Unavailable SavedData adapter、Overworld cache install、live Store／carrier
       ownership、save callback、controlled read／pin／reclaim、dirty與fixed-heap load／save Gate；
       無Player Attachment、journal domain、submission composition或offline root collection
P4-C0：只修訂player Attachment totality、bounded raw與destructive oversize quarantine權威政策
P4-C1：physical V0、total serializer、bounded counting、Ready／PreservedRaw／OversizeMarker、
       Draft persistence／三軸migration、exact bounds與prebuilt Ready carrier；無registration／lifecycle service
P4-C2：Attachment registration、immutable `setData` service、唯一int generation transition、death／End、
       P4-D transition seam、P4-E bounded per-player root projection、GameTests／fixed-heap／phase gates
P4-D0：只修訂journal framing／availability、policy ownership、composition與combined memory authority
P4-D1：strict journal／migration、single Store authority snapshot、窄Store submission port、
       prospective Store／journal preflight、opaque commit handle、publication與journal roots
P4-D2：unique policy／SkillId providers、Draft creation、authenticated P3-C composition、
       prepared Attachment transition與composition outcome
P4-D3：bootstrap／login recovery、persisted-readback prefix clear、paired restart與combined fixed-heap Gate；
       無offline enumeration／network
P4-E0-B：documentation-only V0 numeric／heap／truth／completeness／reconciliation authority；
        無Java／Gradle／CI／study rerun
P4-E0-B.1：documentation-only integrated snapshot counting／post-DFU／freshness authority；
          無implementation／numeric change／study rerun
P4-E0-B.2：documentation-only effective HotSpot MaxHeapSize observation／三狀態／precedence／
          process-control authority；無floor／numeric／R2Q evidence／implementation change
P4-E0-B.3：documentation-only online Attachment 25-counter applicability、online > integrated > disk、
          unified UUID ordering、final freshness與E3 obligation；無numeric／evidence／implementation change
P4-E0-B.4：documentation-only memory-only root-index generation／exhaustion、E2 invalidation、
          Complete permit／active handoff與removeServer authority；無counter／heap／evidence／implementation change
P4-E0-B.5：documentation-only P4-E2 production construction trigger、Store-service exact-final ownership、
          sole login handler／recovery→E2 synchronous ordering、exact-identity injection與E3 same-instance reuse；
          不裁決recovery outcome admissibility或atomic reconciliation final design
P4-E0-B.6：documentation-only direct RecoveryOutcome／E2 result classification、invalidation／setData
          exact coordinates、test-armed bounded state、closed nominal transport、conditional FML route與
          local-cell authority；無implementation／FML API existence claim／production semantics／E3／R2Q delta
P4-E0-B.7：historical documentation-only COMMIT_READY／receipt authority；舊release requirement
          與next-step已由B.8 scoped supersession
P4-E0-B.8：active release-qualification simplification；Candidate10–12 receipt track停止，
          B.7／A0.4僅保留historical research；release改用one-level verification、三次ordinary
          cold full-suite及既有direct product／fixed-heap／restart Gates
P4-E1：read-only bounded online／integrated／disk audit；online existing-state observation only，
      disk／integrated full P4-C admission；journal／Store audit、
      memory-only index與Complete／Incomplete／ReconciliationRequired；reclaim／mutation 0
P4-E2：首次建立production audit service，由Store service以exact final field長期持有；
      P4-D recovery後由既有sole login handler同步呼叫active immutable latest／equipped
      reconciliation；offline／Store／journal／reclaim mutation 0
P4-E3：重用E2接入且Store持有的exact same audit service；唯一ServerStarting
      composition、fresh Complete後immediate controlled reclaim exactly once、exact-server stop removal，
      restart／fixed-1,536-MiB／CI／final gates；無audit constructor／login wiring／chunk force／background sweep
```

P4-E1已`COMPLETE`；P4-E2 read-only design review已`COMPLETE — PASS; NO SPLIT`，product
implementation已在verified repaired backup本地完成且focused tests PASS，但尚未commit至main。
B.6、A0.3、B.7與A0.4皆保留為歷史technical authority／research；Candidate10–12 formal receipt
track已由active B.8 discontinuation。P4-E2 product code completion不因舊harness失敗而失效；
simplified release qualification為`READY; NOT STARTED`且cold為`0/3`。尚待執行的P4-C2及later
product Gates在simplified cold 3/3前blocked；P4-E3在P4-E2 implementation與release closure前
blocked，P4-E仍`INCOMPLETE`。

P4-A2因document／migration package visibility只核准兩個public facade classes：
`SkillDocumentStorePersistenceFacade` 同時提供只接受current-schema typed document的
`encodeCurrent`與always-migrating public load；`OpaqueSkillDocumentMigrationFacade` 處理唯一
production plan orchestration。Public current encode合法，但不得公開current-only decode／hydrate／
load／skip-migration。Store current encode的每份document都必須委派第一個facade，不得複製
A1 mixed-family serializer。

P4-A2 logical migration view 不含 physical `RawTreeEnvelope` metadata 或 raw bytes；side
table 獨立綁定 token ID、typed location、`SerializedTreeContext` 與 exact immutable
bytes。Migration 前後 envelope type／payload schema version、token location／context與恰好一次
occurrence 均不得改變。`resolveFromRaw` 仍是 P3-B2 正式入口；P4 只因
mixed-family 表示需求而不呼叫它，不得把它降級為 legacy／test-only。

P3-D 明確建立production pure-Java aggregate；它集中domain truth與行為，但沒有檔案I/O、
SavedData lifecycle或第二份persistent copy。P4-B的Overworld SavedData adapter是這份P3-D
Skill Store的唯一world persistence，並委派同一aggregate，不得重寫quota／CAS／owner policy。

P4-D composition facade `SkillDefinitionSubmissionService`取得authenticated principal、single Store
authority snapshot與一份combined immutable quota／ValidationContext snapshot，呼叫P3-C exactly once，
完成persistence／journal preflight後作final identity／authority recheck，再呼叫P3-D commit；P3-C與
P3-D本身不依賴Minecraft player class。P3規則以
[P3 scoped amendment §9-A](17_P3資料模型修正案.md#9-a-p3-c-submission-preparation-與-p3-d-commit-邊界)為準，
P4 ordering／outcome／recovery以[18號P4修正案](18_P4持久化與組合修正案.md)為準。

## 1A. 最小單人垂直切片

```text
硬編碼技能
→ 主動 Intent
→ Trigger
→ 發射投射物
→ EffectHitEvent
→ 傷害 EffectRequest
→ Damage pipeline
```

- 【表現咽喉驗證】施放與命中時必須各發出使用模組預設 Profile 的 `PresentationEvent`（至少 `CAST_RELEASE`、`HIT`），走通 EventEmitter → clientbound payload → 客戶端 handler；本階段不製作自訂視覺內容。

## 1B. 資料驅動

- JSON + Codec。
- custom registry type dispatch。
- validation。
- unknown definition proxy。
- schema migration。

## 1C. 持久化與多人

- Attachment／SavedData。
- 玩家死亡、登出、重啟。
- StreamCodec payload。
- 兩人連線、分享魔力、惡意 payload 測試。

## 2. 來源繼承

- SourceContext／EffectState。
- 衍生來源查詢。
- 原始／包含衍生 Trigger 範圍。

## 3. 多段施放與冷卻

## 4. 標記

## 4.5 分裂／連鎖／重複

## 5. 負荷、魔耗、蓄力與冷卻公式

## 6. 簡單造物

## 7. 群體造物

## 7.5 表現層內容與預覽

- 顏色選擇、音效 Profile、粒子 Profile、尾跡與客戶端降級測試。
- 此階段只增加內容與 UI；AppearanceDefinition、PresentationEvent、Registry 與預算骨架已在階段 0 建立。

## 8. 技能編輯器

---

# 開工前最終 Gate

必須全部回答「是」才能開始大量新增 Action／Trigger：

- [ ] 已固定 Java 21、Minecraft 1.21.1 與 NeoForge 21.1.229 以上的具體 build。
- [ ] serverbound payload 只傳 Intent，所有變長資料都有硬上限。
- [ ] Trigger／Action 未知型別能完整保留 raw payload。
- [ ] schemaVersion 與 skillRevision 完全分離。
- [ ] SkillDocument 不可變，runtime 只接受可重建的 ValidatedSkillDefinition，執行期引用固定 revision。
- [ ] P3-D Store 的 owner binding＋retained SkillDocument entries是唯一domain truth；latest／owner count／allocation counter不是第二真相。
- [ ] Quota、owner、CAS、technical capacity與insert位於同一thread-confined Store mutation；所有typed failure都在首個truth mutation前結束。
- [ ] Store hard ceilings固定為per-owner skills 256、global skills 4096、per-skill retained revisions 128、global retained revisions 32768與per-reclaim roots 65536。
- [ ] Retained history可稀疏，latest是implicit root且一般reclaim永不移除；合法Plan的normal commit result沒有RevisionExhausted。
- [ ] 正式retire／quota release完成前預設policy quota為Unlimited；移除Attachment引用不移除owner／latest或釋放quota，未來retire需tombstone防止重用。
- [ ] 玩家／實體資料使用 Attachment；跨維度資料使用 Overworld SavedData。
- [ ] Attachment 死亡複製與 End 返回政策已有測試。
- [ ] 每次需要持久化且成功發布matching carrier／journal的SavedData mutation都依dirty matrix
      `setDirty()`；typed failure、pin／close、reclaim 0與Quarantined／Unavailable不dirty。
- [ ] 18號P4修正案及P4-A2.0／P4-B0／P4-C0明確化已提交且遠端CI通過；P4-A1～A3、P4-B1／B2、P4-C～E責任、
      per-raw-subtree envelope與exact
      encoded-byte ceilings已固定。
- [ ] Revision exact outer ceiling只表示該層inclusive admission；V0最大完整合法revision、
      outer MAX + 1與inner document capacity已依分層語意各自驗證。
- [ ] P4-A2 document persistence seam只有`encodeCurrent`與always-migrating load；production
      P4-A2 public facade classes恰好兩個，無current-only decode／hydrate／load bypass，Store
      package不複製A1 document encoder。
- [ ] `SkillMigrationStep` 只依 logical outer schema 運作；payload／raw／data 與 token
      sentinel 不可觀察或修改，type／payload schema version／location／context 保持不變，
      production edge 具 representation-independence tests。
- [ ] P4 exact-field preflight 不引用或重寫 P3-D 四種 domain count ceilings；
      physically valid domain overage 只經 `StoreRestoreRejected`，不存在 P4 平行 count
      failure，list decoder 不依 declared count 預配置大型 collection。
- [ ] JSON、NBT與mixed-family document都能same-family結構保存；任一migration／decode／
      restore failure都不安裝partial或empty Store。
- [ ] Existing invalid SavedData不安裝empty Store；在第18號totality boundary內已交付serializer body的
      existing per-attachment Tag不等同missing，in-bound malformed形成PreservedRaw，oversize形成
      canonical OversizeMarker，restart都不得變empty Ready；
      custom loader不經fail-open deserializer，SavedData save callback只寫prebuilt carrier。
- [ ] SavedData exact root／inner framing、zero-length no-journal sentinel、strict single-member gzip、
      Ready／Quarantined／Unavailable non-saving邊界與1 GiB full-size load／save Gate已有測試。
- [ ] Store-first journal使用bounded generation且只在persisted readback確認後清除；
      composition outcome不把Prepared冒充Committed；strict `writeAnyTag` framing、partial availability、
      continuous chain與single-process fixed-1-GiB combined Gate均通過。
- [ ] P4-E1已closure，E2 read-only review已`COMPLETE — PASS; NO SPLIT`而implementation
      已在verified repaired backup本地完成，focused tests PASS且尚未commit至main。A0.1／A0.2
      是歷史Stop；B.6／A0.3／B.7／A0.4保留為historical technical research，但B.8已停止
      Candidate10–12 receipt track並以simplified cold 3/3取代其release用途。Direct evidence仍
      必須使用B.6 actual-object／result／invalidation／JVM `setData` coordinates及zero-delta
      assertions；只以ordinary assertion／output／fixed-heap log保存，不要求canonical JSON或
      filesystem receipt publication。P4-E2仍是
      首次將production audit service接入composition的phase；每個Store service exact-one-final、
      sole recovery login handler same-call-chain E2與active wiring條件不變。E3只重用same
      instance作fresh audit／reclaim／exact-server removal，constructor與login wiring delta均0。
- [ ] E1只作read-only
      bounded online／integrated／disk audit、E2只作login reconciliation，二者reclaim 0。Player roots包含online／offline
      players與journal targets，restart預設Incomplete，只有E3在同一ServerStarting call
      chain使用fresh Complete即時controlled reclaim exactly once；25-counter／disk DataVersion／
      zero-P4-E-DFU／online source exclusion與+0 admission／integrated logical counting／pure inner
      P4-C admission／identity freshness／
      1,536-MiB production Gate與N／N+1
      reconciliation規則以第18號§18為準。
- [ ] ItemStack 自訂資料使用 Data Component。
- [ ] Action 無法繞過 EffectPipeline。
- [ ] 魔力無法繞過 ManaTransactionService。
- [ ] 世界狀態只在伺服器主執行緒修改。
- [ ] Scheduler 無遞迴重入、順序穩定且有熔斷。
- [ ] 所有衍生 collection 與 lineage 都有上限。
- [ ] migration、重啟、缺型別、惡意封包與專用伺服器測試存在。
- [ ] 真相歸屬表逐型別只有一個持久化真相；Marker Attachment 與中央索引的角色已用測試驗證。
- [ ] revision 在提交編輯時落庫、施放只 pin；施法者離線後延後 Trigger 可解析固定 revision，外觀微調 churn 的未引用舊 revision 可安全回收。
- [ ] Commit 語義是不重試、不回滾、逐 Action 補償；文件與程式中沒有不可兌現的「同成同敗」承諾。
- [ ] 控制類 Action 具機器可讀分類；若啟用硬控，記帳入口與免疫出口形成閉環。
- [ ] Presentation Layer 與玩法層隔離；缺 Profile 只 fallback，視覺超額只降級顯示。
- [ ] Presentation payload 只有有界事件參數與 seed，沒有逐粒子狀態或任意資源路徑。
- [ ] AppearanceDefinition／Override 解碼失敗只會 fallback，不會使玩法定義損壞或阻止施放。
- [ ] Presentation Profile 已固定為「Java 型別 + 資料驅動實例」，自訂 datapack registry 的註冊與同步方式已有測試。

---

# 凍結宣告

本版本完成第二輪審查修正後，視為 **NeoForge 1.21.1 骨架凍結版**，開發正式進入階段 0。

- 日後數值、公式、Trigger／Action 內容、Profile 內容與 UI 調整不解除骨架凍結。
- 日後任何會改變資料真相、持久化 schema、身分規則、管線邊界、失敗政策或網路信任邊界的修改，必須另立修正案。
- 接受骨架級修正後，必須重跑本檔「開工前最終 Gate」全部項目與相關 migration／專用伺服器測試。


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
