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
  導出 owner，fresh reauthorize、只取得一次 immutable quota snapshot、呼叫 P3-C prepare，
  再經 persistence／journal preflight 呼叫 P3-D commit。Store 仍防禦性檢查 committed owner
  與 state；P3-C／P3-D 不依賴 Minecraft Player 類別。
- 【Draft】`SkillDraft` 是業務上可編輯、Java instance 上不可變的 snapshot；使用 `AppearanceDocument` 與 `AppearanceOverrideDocument`，不建立 `DraftAppearance`。Draft 持有候選 `SkillId`；P3-C 定義 server-side mint contract，但 transient mint grant 不是跨重啟 submission credential，提交授權仍以當下 authoritative snapshot 為準。

---

## 第三層：NeoForge 1.21.1 資料住址

## 7. 玩家與實體資料使用 Data Attachment

- 【永久技能 Attachment】Draft、latest、equipped 與 editor metadata 使用獨立
  `gramarye:player_skills` Attachment，不與 mana／cooldown 等不同死亡政策資料混用。
- 【缺失與損壞】Missing tag 建立 empty Ready；existing malformed tag 建立 Quarantined，
  不得等同 missing。使用 total custom serializer，不使用把 decode failure log-and-skip 成
  missing 的 partial serializer。
- 【決定】玩家魔力、技能槽引用、冷卻索引與接續實例引用使用 NeoForge Attachment。
- 【決定】生物／造物錨上的標記本體使用 Attachment，與錨點同生死；中央只保存可重建索引。
- 【決定】Attachment 是否持久化必須由是否提供 serializer 明確決定。
- 【決定】玩家死亡複製政策逐 Attachment 指定：
  - 永久技能資料：複製。
  - 魔力：依遊戲政策。
  - 冷卻／接續狀態：預設複製或明確清除，不可依 NeoForge 預設碰運氣。
- 【注意】Attachment 不會自動同步到客戶端，所有同步由本模組 payload 完成。
- 【注意】若未來在 chunk／block entity 上保存可變 Attachment，直接修改 `getData()` 回傳內容後必須正確標記 dirty；優先使用不可變 record + `setData()`。
- 【複製】永久 player skill Attachment 使用 serialize + `copyOnDeath`；End return 不得
  再做手動 double-copy。Attachment 不自動同步。

## 8. 全域資料使用 Overworld SavedData

- 【Skill Store 持久化】`gramarye_skill_definitions` 是 P3-D
  `SkillDefinitionStore` 的唯一 Overworld production persistence adapter，只保存 Store
  detached snapshot 與 pending Attachment update journal，不承載 `RuntimePersistentStore`。
  Runtime persistence 仍是另一資料類別與未來獨立 lifecycle。
- 【唯一位置】非 Overworld 維度取得同一 Overworld adapter；不使用 static world singleton。
- 【理由】主世界是伺服器運作期間不會完全卸載的維度。
- 【載入】存在的資料檔必須依序通過 compressed-file bound、bounded NBT carrier、Store
  migration、per-document skill migration、family-aware hydration 與
  `SkillDefinitionStore.restore`。任一步失敗形成 non-dirty Quarantined，不得建立 partial
  或 empty Store，不覆寫原檔，也不自動把 `.dat_old` 提升為 truth。
- 【禁止 fail-open】標準 `computeIfAbsent` 的 exception-to-fresh-data 行為不得直接作技能
  Store deserializer 入口；專用 loader 只執行一次並安裝 Ready／Quarantined adapter。
- 【Ready state】Ready adapter 同時持有 domain Store、matching immutable encoded carrier、
  pending journal carrier 與 rewrite-pending state。Carrier 是 derived representation，不是
  第二 domain truth，也不供玩法查詢。
- 【Mutation】所有一般可失敗 encode 與 revision／history／Store／journal／carrier byte
  checks 必須在 Store truth mutation 前完成。P3-D 回 `Committed` 後才發布預建 carrier／
  journal 並 `setDirty()`；typed failure 與 pin／close 不 dirty。Reclaim 只有實際移除 revision
  才以既有 encoded entries 重建 carrier 並 dirty。
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
- 【Offline roots】P4-E 以 bounded read-only audit 取得包含 offline players、pending journal
  與所有 enabled persistent source family 的 complete roots；restart 預設 Incomplete，未完成、
  unreadable、truncated、unknown 或超過 65536 時不得 sweep。不強制載入 chunk，也不跨 tick
  保存 `Complete`。

## 8-B. 真相歸屬表與單一真相原則

- 【原則】每一種執行期資料恰有一個持久化真相；其他副本只能是可重建索引、指標或客戶端快照。
- 【自我修復】索引查無真相時立即修剪；真相失去索引時，在載入或定期對帳時重新註冊。

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
- 【對帳】Store owner／documents 是 truth。Missing／owner-mismatched pointer 只做 opaque
  Attachment prune；合法舊 pointer 不自動升 Store latest；orphan revision 不自動刪除或釋放
  quota。Duplicate persisted route／slot 不採 last-write-wins。
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
- P4-B～E：absent／invalid SavedData 的 Ready／Quarantined 分流、prebuilt save callback、
  完整 dirty matrix、player skill Attachment clone／migration、Store-first journal crash windows、
  persisted-readback clear、offline roots fail-closed 與 no chunk load。完整逐項矩陣以
  [18號P4修正案 §21](18_P4持久化與組合修正案.md#21-required-tests)為準。

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
- Existing invalid SavedData／Attachment 被當成 missing 而建立 empty truth，或任一
  migration／decode／restore failure 安裝 partial Store。
- Store commit 後才首次執行一般 Codec／capacity check，或 Attachment-first ordering 繞過
  Store-first journal。
- Journal 在 persisted playerdata readback 前清除，或 generation overflow 未 fail closed。
- Offline roots 不完整仍 best-effort reclaim，或只掃 online players 即宣稱 Complete。
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
P4-B：saved_data_schema_version、唯一Overworld SavedData outer carrier與adapter、bounded ingress、
      non-fail-open load、Ready／Quarantined、使用A3 primitive的live carrier publication、save
      callback與dirty；不重寫Store encoding，無Player Attachment
P4-C：獨立player skill Attachment、Draft／latest／equipped／editor persistence、
      total serializer、migration與clone policy；無Store commit composition
P4-D：authenticated submission composition、fresh authority／quota、P3-C prepare、調用A3
      prospective Store builder與prospective journal、commit-oriented preflight、P3-D commit、
      carrier／journal publication、Attachment transition與recovery；無network
P4-E：complete offline root audit、rebuildable index、reconciliation、reclaim composition
      與dirty mapping；無chunk force、無background sweep
```

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

P4-D composition facade `SkillDefinitionSubmissionService` 取得 authenticated principal、fresh
authoritative identity／state與一份immutable quota snapshot，呼叫P3-C prepare，經persistence／
journal preflight後呼叫P3-D commit；P3-C與P3-D本身不依賴Minecraft player class。P3規則以
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
- [ ] SavedData 每次變更都能保證 setDirty。
- [ ] 18號P4修正案及P4-A2.0明確化已提交且遠端CI通過；P4-A1～A3、P4-B～E責任、
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
- [ ] Existing invalid SavedData／Attachment形成Quarantined而非empty；custom loader不經
      fail-open deserializer，save callback只寫prebuilt carrier。
- [ ] Store-first journal使用bounded generation且只在persisted readback確認後清除；
      composition outcome不把Prepared冒充Committed。
- [ ] Offline root audit包含offline players與journal targets；restart預設Incomplete，任何
      source family未證明完整時reclaim disabled。
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
