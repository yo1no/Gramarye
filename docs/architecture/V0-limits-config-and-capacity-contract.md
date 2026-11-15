# V0 R1 啟動配置、工作與容量契約

**R2 ADOPTION_CANDIDATE · UNVERIFIED**

## 唯一配置 owner

**SOURCE_FACT**　`P5ServerRuntimeConfig` 唯一註冊 SERVER `gramarye-server.toml`。`P5RawServerConfigSpec.acceptRawConfig` 在同一 NightConfig bulk read 解碼 P5/P11，atomic reference 只發布一次。P5 按 `P5RuntimeLimitKey` 順序拒缺值、錯型及範圍，先於 cross-field。P11 獨立持有 Invalid/Unavailable。既有 `correct` 只寫舊 P5 16 個預設。Reload/Unloading 依 exact spec、mod、type、filename，原 slot 保 immutable limits。 [P5 owner](../../src/main/java/com/yo1no/gramarye/P5ServerRuntimeConfig.java#L15) · [Raw read](../../src/main/java/com/yo1no/gramarye/P5ServerRuntimeConfig.java#L151) · [P11 decoder](../../src/main/java/com/yo1no/gramarye/P11StartupConfiguration.java#L177) · [Startup](../../src/main/java/com/yo1no/gramarye/Gramarye.java#L162)

**RECOMMENDED_AFTER_SET**　同一 candidate 加第三個 immutable `V0StartupLoadState` 分量，同一 raw read、同一次 atomic publication。原 P5／P11 key、fallback、first-failure、reload disposition 及 startup order 保持。V0 無獨立檔、第二 owner 或 live provider。配置 reload 只影響下一 engineering slot。Datapack/runtime context reload 仍依 Scope V §4.2 關閉舊工作。普通 leaf 無 V0 composition。

`v0` 全部未設定判 Disabled。Present table 的 `enabled` 必須 Boolean，true 時整組必填。False 可只有 enabled，所有其餘 supplied keys 仍驗。拒未知 v0 key 及錯 table，其他 TOML 區沿原行為。工程樣本為完整 default tuple 且先以 false 儲存，operator 在隔離工程 universe 才設 true。P5 spec unavailable 時 V0 Unavailable。V0 起動要求原 P5/P11、工程 artifact、環境與 source 全可證，invalid 只阻 V0 composition；原 P5/P11 故障 precedence 保持。診斷只留 key、closed code 和範圍。

| 順序 | Raw key | 型別 | Min／Default／Hard |
|---:|---|---|---|
| 0 | `v0.enabled` | boolean | false／false／true（僅 engineering） |
| 1 | `v0.maxLineagesPerAttribution` | integral→int | 1／1／2 |
| 2 | `v0.maxLineagesPerServer` | integral→int | 1／2／4 |
| 3 | `v0.rootAttemptsPerTick` | integral→int | 1／1／2 |
| 4 | `v0.maxManifestEntries` | integral→int | 1／4／8 |
| 5 | `v0.projectileMaxAgeTicks` | integral→int | 1／16／24 |
| 6 | `v0.projectileMaxRangeBlocks` | integral→int | 1／16／24 |
| 7 | `v0.lineageDeadlineTicks` | integral→int | 3／24／40 |
| 8 | `v0.presentationRecipientsPerOffer` | integral→int | 1／8／16 |
| 9 | `v0.positiveRootFee` | integral→long，正 Mana 單位 | 1／1／4 |

Raw integral 接 Integer/Long。固定驗序為 table shape、enabled、逐 key missing/type/min/max、未知 key（字典序第一）、cross-field。關係按序為 attribution≤server、root attempts≤server、deadline≥age+2（root release 及合法排程各保留一 tick 時計空間。Child 繼承原 deadline，無 fresh age grant）、`projectileMaxRangeBlocks >= projectileMaxAgeTicks`、checked arithmetic 計 derived counts/bytes。K1 在已允 startup tuple 下每完整步最多 1 block，之後按 native dry drag 衰減，避免單邊 partial range step。Error family `UNAVAILABLE, WRONG_SECTION_TYPE, MISSING_REQUIRED_VALUE, WRONG_VALUE_TYPE, BELOW_MINIMUM, ABOVE_HARD_MAXIMUM, UNKNOWN_KEY, INCONSISTENT_LIMITS, ARITHMETIC_OVERFLOW`。驗 0、負數、MAX/MAX+1、long 數值、bool/text/float、缺表、缺 key、每個關係、concurrent raw read/old-slot identity。

`com.yo1no.gramarye` package-private 增量：`sealed interface V0StartupLoadState permits V0StartupLoadState.Disabled, V0StartupLoadState.Ready, V0StartupLoadState.Invalid, V0StartupLoadState.Unavailable`，nested `enum Disabled { INSTANCE }`、`enum Unavailable { INSTANCE }`、`record Ready(V0StartupLimits limits)`、`record Invalid(V0ConfigFailure failure)`。Package-private `record V0StartupLimits` 用表 1–9 的欄位順序、key 最後名稱及型別作 components。另有 package-private `record V0ConfigFailure(V0ConfigFailureCode code, String key, Optional<String> relatedKey)`，`enum V0ConfigFailureCode` 用上段 closed constants。既有 `P11ServerConfigCandidate`、`P11StartupSnapshot` 各追加 `V0StartupLoadState v0State` component，保原兩參數 construction 並以 Disabled 接舊 in-package fixture。`final class V0ConfigurationValidation` 的 `static V0StartupLoadState decode(UnmodifiableCommentedConfig raw)` 唯一 caller `P5RawServerConfigSpec.decodeCandidate`。四個 member types 的 classfile declarations 為隱含 public static。Ready／Invalid canonical constructors 為隱含 public。外層 V0StartupLoadState 為 package-private，全組零跨 package 可命名的 exported API 增項。 [精確宣告計數](V0-java-surface-and-consumer-inventory.md)

**REJECTED**　第二個 spec/file、live 配置、借 P5 16 keys 或 P11 T 作 V0 數量。P5 舊 limits 不變，V0 邊界獨立記帳並證 legacy 餘裕。

Raw V0 keys 為 10 項（索引 0–9，Ready record 有 9 個 limits components），其中 Manifest entries 仍是可配置 1／4／8。Selected closure MAX 2 和 portable logical charge MAX 1089 bytes 固定於 qualifier，無 raw key、override 或 caller-supplied cap。已不存在的任一 field 如被放入合法 `v0` table，依 UNKNOWN_KEY 拒下個工程 slot。Root 結果與 legacy P5/P11 failure precedence 保持。

## Default／hard 及 MAX+1

技術值均為 **PROVISIONAL_ENGINEERING_CEILING · MUST_PASS_DIRECT_QUALIFICATION_BEFORE_IMPLEMENTATION_COMPLETE**。D/H 是預設與硬 tuple 的衍生上界。排程、statement、native attempt 與 reserved capacity 分帳。滿額先拒新工作並保既得責任。

| 軸／量綱 | Min／D／H | 取得／歸還與 MAX+1 |
|---|---|---|
| L active＋reserved／attribution、server | 1／1／2；1／2／4 | Compare 前佔完整 lineage 及合法 P11 W。最後 reader/close 終態後還。B 同 UUID 不另取額。滿回 LINEAGE_CAPACITY。 |
| X slots／lineage、attribution、server | 2 固定；2／2／4；2／4／8 | 根取得 root+child，含保留及終結中。第三 instance 回 INSTANCE_CAPACITY／CHILD_LIFETIME_EXHAUSTED。 |
| A 嘗試及接受／tick | per attribution 嘗試 1，server 嘗試 1／1／2，成功 accept 固定最多 1/server/tick。Cold 同時 in-flight/result 合 1，等待佇列 0。 | 嘗試開始計入、失敗不返當 tick credit。Cold provisional 捕獲 tick+8 截止，原 worker 真退出才返 cold credit。第二 concurrent request 回 PREPARATION_BUSY。Producer 每 tick 最多先納兩個原角色，先消費前一 cold completion，才容新意圖占 mailbox。以 owner direct matrix 驗第 A+1。 |
| E pending、lifetime／instance、lineage、server | 3、6、6／12／24 | 每 instance release、create、hit 各最多 1。三節點全預留，positive MAX 同相位可全部服務。重入、duplicate、extra 回 EVENT_CAPACITY／DUPLICATE_FACT。自然 destroy scalar observation 另計每 instance 1。 |
| I authored／stack、instance、lineage、server/tick | Root ≤2，child ≤1，lineage ≤3，server 3／6／12。 | Root hit 一 Damage 可接一 Create；child hit 最多一 Damage；on_create empty。先保 whole stack，首敗停。Root 第 3／child 第 2／lineage 第 4 拒 STATEMENT_BOUND。Creation binder 的一個 typed scalar 與 native geometry guard 分列。 |
| C child／lineage、server/tick | lifetime 1、depth 1；1／2／4 | Compare 前 reserve。發布結束皆不還 lifetime allowance。第二個、depth2、sync re-entry 均拒。 |
| F native calls／type/server/tick | spawn ≤X=2／4／8；damage ≤X；總 4／8／16 | 原生呼叫開始前扣，任何 outcome 不退該 tick 的 work。第二次同 permit 回 SPAWN_ATTEMPT_SPENT／HIT_CLAIM_SPENT。 |
| Q movement sweeps、claims／server/tick | 各 ≤X=2／4／8 | 每 OPEN carrier 一次 native sweep，終生最多一次 valid claim；claim 後 inert。MAX+1 以 overcapacity／duplicate 拒，不削減合法自然命中。 |
| Q 原生 query 密度，單 sweep fixture | 1／32／64 個 native 可迭代實體，合樣本 2／128／512 | 隔離世界 topology 含 actual sections 中所有 visited entities，包括拒絕者。預驗受控 actor/entity roster、loaded scope。MAX+1 recipe 回 ENGINEERING_TOPOLOGY_UNSUPPORTED。不得截斷 native result、重掃或把測試密度當 production cap。 |
| W carrier／instance、server | 1；2／4／8 | Planned、OPEN、closing 同額。建立前預留，終態後歸還。第二 carrier 回 CARRIER_CAPACITY。 |
| R reserves／role、server | 3/instance，6／12／24，另 1 cold cancellation | 保 hit、native terminal/close、lease-close bookkeeping。與 E 同一實體可各列角色帳。真正 lineage lease 只還一次。滿拒新，不搶舊 reserves，stop/reload 的撤權優先。 |
| P leases／raw pins | 1 lease／lineage；1／2／4 accepted＋至多 1 provisional。Accepted raw refs 3／6／12，provisional 0..3，combined max 6／9／15。 | Root-only=1 Spell，root＋child=2，各可另持 1 exact Manifest。第三 selected Spell／depth 2 拒 PROFILE_REJECTED，無 partial pin。先扣每租約 raw refs，全服按 exact source/owner/family/id/revision 去重為 actual unique 統計。相同 revision 得共享實體索引，不能少扣 admission。保 current、pending、recovery、durable external roots 和每個 live/provisional pin；受 M1 physical caps。 |
| K programs／portable logical charge | 1／2／4 accepted＋至多 1 provisional。每份硬上限 **1089 bytes**；accepted 1089／2178／4356，provisional ≤1089，combined 2178／3267／5445。 | 採封閉欄位和 checked calculation。最大合法 fixture 1089，第三 Unit、129-byte ID、含錯欄位或 synthetic 1090 charge 各拒。JVM heap、M1 image、C scratch、catalog lease row、P5/P9 和 diagnostics ring 各自量測。 [逐欄算式](V0-catalog-runtime-lease-and-admission-handoff.md#31-r1-封閉-compact-projection) |
| Age/range/deadline | 1／16／24 ticks；1／16／24 blocks；3／24／40 ticks | launch_step_milli 1..1000，能力速度上限 1 block/tick。實際 child 繼承原 inclusive deadline。每 movement/claim 分開記最多 4 個 terrain/segment chunks 及最多 4 個 target-footprint checks，均取 exact 已完整載入的 FULL LevelChunk。MAX+1 驗 config 超界、range 少於 age、16／24 tick 等式、最後完整步／真 hit 與期限撤權，無單邊 partial step 或強載。 |
| Z applied offers/server/tick、selected/offer | offers 4／8／16；selected 1／8／16；existing scan ≤512/offer | 每 carrier spawn＋damage 最多兩個 fact，P8 維持排序且只用 legacy 後餘額。D/H 為 4,096／8,192 evaluations、64／256 candidate deliveries，仍受原共享 bytes／player 限制。第 9／17 receiver 按相同 comparator 限前 N，budget extra drop 無 gameplay effect。 |
| 工程 fee／root；damage／statement | explicit zero；正 1／1／4；damage 1..4 HP，fixture 2 HP | Root release 同 call 收一次 P6 integral fee；damage/child zero，cooldown zero。一 HP = 1000 raw units，checked 轉入。+1 config/descriptor 拒，在 native mutation 前。 |

Attribution 是已認證 UUID 的額度歸屬，不授權。P11 W 先依本次 accepted responsibility 保留，T 只度量來源責任，N/Fop/Qctx/Dd/Iw/H 不按 instance 計。V0-only 入場檢查在 P11 原 Resources monitor 以同次原子 read/charge 要求安全餘裕，`T_after + L_hard ≤ maxUuids` 且 `dirty_current + L_hard < dirtyUuidAdmissionWatermark`，本 slot 的 L_hard 是實際配置的 maxLineagesPerServer（1..4）。處於同 account 的合法 reuse 不增加 T。只有本次新 W 受此門檻，既得與後續真 dirty/native obligation 不取消，也不限制 legacy。此條避免 V0 耗盡邊界，並不保證未來無限玩家可受理。新增 package-private `P11FoundationService.acquireV0Work(ServerPlayer, int maxV0Lineages)`，經原 `P11QualifiedSourceOwner` 共用 acquire flow 及 `P11ControlBudgets.Resources.tryAcquireV0Work(AccountOwner, int maxV0Lineages)` 原 monitor；package 層回傳原 work/root 或 typed refusal 的既有 null/empty 形狀，無第二套 owners。原 W admission 不改。P6 正費的付款與退回兩個檢查均仍要 exact current writable A，qualified successor B 無付款權。

## 配置與最壞工作推導

1. 原 P5 owner 置於同一 tick/drain，legacy 全量 budget 保持，V0 有獨立已受理 reserves。兩族 ready partition 用同一 owner 的 `(dueTick, ownerOrderSequence)`，保 legacy 既有 relative order、cap equality/no-peek 及可服務性，只在固定 V0 名額內合併 dispatch。每 phase 同時 (6L) 次 E、(3L) 次 I、(4L) 次 native mutation、(2L) 次 sweep、(6L) 個 cleanup roles。純 native 呼叫不可搶占。2 個預設 lineage 容雙人與 same tick，4 個 hard 容多 attribution 和故障 storm。硬值同時達到時仍須真實量測。C 既有 fixed heap 不代替此證。
2. `ProjectileUtil.getHitResultOnMoveVector` 走 `level.clip` 和完整 `getEntities` native traversal，call cap 本身無法限制候選數或工作時間。隔離工程 recipe 用 owned void floor/loaded course，禁止自然生物、裝置觸發、跨界輸入、非批准 mods/datapack producers。所有可移動的人、target、carrier、drops 先按計數最壞尺寸入 roster，量測 instrumentation 只觀察真遍歷。Recipe MAX+1 啟動前拒，執行期間 topology/source 不可證則封 scene，按失證收束。**若無法直接證此受控環境的 native traversal 上界，正式資格受阻**，原生 API 無不改自然結果的任意世界 visit cap。 [鎖版來源](V0-platform-evidence-manifest.md) · [現行 sweep](../../src/main/java/com/yo1no/gramarye/P9StarterProjectile.java#L197)
3. 實測 cold/warm、same-tick hit、dense Q/P8、legacy/P11 mixed、cleanup storm 和 churn。錄 work counts、tick p50/p95/p99/max、native/cold I/O、allocation/GC、full retained object graph 去重、M1 current/prepare、C scratch、diagnostics ring 與 exact actor retained graph。既有 writer/dirty 壓力須同時測。Hard 壓力不合格只能先縮下一 slot 或關 V0，重新證達標，禁止延遲既得 hit。數值與測試 headroom 見 [直接驗收](V0-direct-acceptance-plan.md)。
