# V0 R1 Catalog runtime lease、P5 受理交接與事件責任

`PROPOSED_CONTRACT / NOT_ADOPTED / NOT_IMPLEMENTED / UNVERIFIED`

## 1. 現有接點

- `SOURCE_FACT`。M1 的 `ScopeDCatalog.captureRoots` 合併 durable external roots，`prepare` 依 route／external roots 集合保留 revisions，`ScopeDCatalogImage` 將 external roots 寫進 frame。目前無 production runtime lease issuer。Transient retention 必須參與 revisions 的保留判斷，不能進 serialized external roots。 [Catalog](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCatalog.java#L102) · [Image](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCatalogImage.java#L70)
- `SOURCE_FACT`。C 在 catalog monitor 內捕獲 image／source／context，鎖外編譯。Resolver 從同一世代證 standalone 或整份 Manifest available，compiler program key 綁 selected revision 和 body digest，Manifest exact revision／stable entry／whole availability 另有 provenance。Compiler proof 不保護 runtime refs 或授權受理。 [Catalog compile](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCatalog.java#L203) · [Resolver](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDResolver.java#L45) · [Compiler](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCompiler.java#L66)
- `SOURCE_FACT`。`ScopeDStorage.current/requireCatalog/verifySource` 讀檔案屬性與 lock identity，`requireExpected` 重讀 exact frame。Catalog publish monitor 涵蓋 stage/force/readback。Directory/writer 本身由 exact file keys 與 OS file lock 保護。新比較點不得使用現在的 physical/catalog monitor。 [Storage owner](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDStorage.java#L110) · [Replacement](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDStorage.java#L266) · [Source checks](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDStorage.java#L407)
- `SOURCE_FACT`。P5 `ServerSlot` 是 single runtime owner。既有 lease 限 `SkillReference`／`ControlledSkillPin`／`ValidatedSkillDefinition`。P11 W 由 `acquireWork` 驗 current actor/source/PLAY 與 shared budgets，保既有 account duty，不授同 UUID B 替 A。 [P5 post](../../src/main/java/com/yo1no/gramarye/SkillRuntimeService.java#L1976) · [Legacy publication](../../src/main/java/com/yo1no/gramarye/SkillRuntimeService.java#L3153) · [Legacy lease](../../src/main/java/com/yo1no/gramarye/SkillRuntimeService.java#L5665) · [P11 W](../../src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java#L348)
- `COMPLETED_ACCEPTANCE`。M1 physical publication/recovery、C frontend/proof、P11 既有受理與 native duties 沿原適用範圍。 [M1](../../docs/architecture/M1-data-and-physical-contract.md) · [C](../../docs/architecture/C-resolver-compiler-contract.md) · [P11 §4](../../docs/codex-spec/19_P11持久化與多人修正案.md#L60)

## 2. 唯一推薦

同一 catalog owner 設獨立短 RAM gate，與 physical monitor 分離。所有合規 writer mutation、recovery、context retirement 先關 gate，再做 storage I/O。新 current 只在 M1 完整 durable readback 後開門。Accepted lease 維持獨立 exact witness，普通 publication 只使舊 provisional stale；commit 未知先封 current/latest、new capture、sweep。已接受工作仍判其自有 lease/source；若 writer/source 本身失證，封使用並保不明 material，直到原租約安全收束。

- `REJECTED` 直接公布 `ScopeDCatalog`／`ScopeDProgram` 或將 P5 搬到 compiler package，會打開不必要的資料與 caller。
- `REJECTED` compile/unlock 後直接 P5 publish，writer 可在 compare 到 publish 之間更新 current。
- `REJECTED` 使用 catalog/physical monitor 包完整 P5、P11 或 platform admission，會導入檔案與 world callbacks 的耦合。
- `RECOMMENDED_AFTER_SET` 採 provisional exact pin、P5 預留與同一 pure-memory CAS compare-and-accept。公開受控接點由兩個 facade type 的閉合 inventory 支持，catalog、writer、IR、resolver 和 compiler 仍包內。

`ENGINEERING_DERIVATION`。線性化涵蓋 M1 私有受控來源與合規 sole writer。每次 cold prepare 做 exact file/source/writer/readback check，既有 source 損失觀測即封 gate。任意他程序有權覆寫同一路徑，並要求無 I/O hot path 即時發現，超出 M1 來源保證。驗收故障注入要區分該界線，不報未觀測的外部變更已被即時攔截。

## 3. 冷準備與 exact material

P5 main thread 先取得真登入 exact actor/policy，分配有界 ingress ticket；送唯一 cold 工作格一份只含 slot capability、ticket、owner、穩定 ID 與 epoch 的 request。P5 私有 pending-capture row 持需要重驗的 exact A/connection/session；worker 不持 Minecraft/Player/Entity/Level，沒有 backlog，不 enqueue gameplay。P5 Post 讀唯一 scalar completion 格。Worker 一併執行該 slot 下每 owner 的唯一 writer 的 open、fixture publish、verified prepare、recovery、sweep、retire，無 P5 executor/future/thread 或等待。

Lease row 綁 catalog source incarnation、opaque writer incarnation、`SkillOwnerId`、captured generation、exact readback、frontend ABI/descriptor/policy及 runtime startup/reload epoch、exact selected Spell、所有 selected closure revisions、optional exact Manifest revision＋stable entry provenance、immutable program identity、lease ID/generation、opaque P5 slot capability。Pin 集合只含 selected closure＋適用 Manifest body；whole Manifest availability 使用當次完整冷證明，未選 entries 不入 execution pins 或 runtime program。Capture、lease、program 均無 latest resolver、mutable catalog map、world、actor、Mana/account receipt、presentation state、序列化權限。最後讀取者的 exact release 永不看 latest。

C 先驗同一 image，V0 冷 verifier 再縮成 typed immutable runtime projection。固定 `V0_SELECTED_CLOSURE_SPELLS_MAX = 2`、`V0_PROGRAM_LOGICAL_BYTES_MAX = 1089`，各與 Manifest 4／8 whole entries 的獨立配置分開。每租約 root-only 1 Spell、root＋child 2 Spell，另可選 1 exact Manifest。第三個 selected exact Spell、第二條 Create 或 depth 2 於冷端 `PROFILE_REJECTED`，無 partial pin。一次 C compile 的 selected 檢查和所有未選 entries 供 whole-Manifest 證明的 V0 qualifier 必須同一 source/generation/context，使用 C 同次短命 lowered units 或有界 pure batch。C 與 M1 原容量及持久技術未更動。合法 C program 若不符 V0 子集，具名拒於 V0；原 `RUNTIME_POLICY_REQUIRED` 仍生效。 [Frontend](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDFrontend.java#L111) · [Lowering](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDLowering.java#L49) · [Feedback](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDFeedback.java#L23)

| Version 1 descriptor（僅隔離 variant 的可信 frontend） | 冷證明 |
|---|---|
| BACKEND `gramarye:v0_projectile`，category `gramarye:projectile`，capability `gramarye:v0_projectile` | 空 descriptor args、無 settings expressions、無 Phenomenon/raw gameplay。同一既有 entity type。唯一 required INTEGER creation input `launch_step_milli`，無 default，1..1000；fixture 是 server 供 1000，最高 1 block/tick。位置、target 和向量由 server native 接點獨立形成。 |
| ACTIVATION `gramarye:v0_engineering` | 空 args，只給該 backend。Standalone 或 Manifest 所選 root 必有此 activation 與 server input；selected child 必為無 activation 的 V0 child unit，依 exact Create 目標與原 binder。其他 C 合法形狀保持可發布，V0 首片無法接受其不符的執行 unit。 |
| EVENT `gramarye:on_create`，origin CREATION | 至多一 handler，空 blocks。 |
| EVENT `gramarye:on_hit`，origin EXTERNAL | 至多一 handler，最多依序 Damage 和 Create。C scalar fact `launch_step_milli` 由親代已綁值提供，Create contract 以同名同型到 child。Native target 由 P5 call-local proof 給 P6，無 catalog UUID/entity id。Create 綁 exact closure child，須 root damage 已確認成功，depth 1 child 無 Create path。 |
| EFFECT `gramarye:v0_hit_damage` | 只在 on_hit，INTEGER `damage_hp` 1..4，schema default 2、fixture 2 HP，無 emitted event。 |
| Optional APPEARANCE `gramarye:v0_profile_triple` | 只接 `sound_profile`、`particle_profile`、`trail_profile` 有界 scalar profile IDs。每枚 canonical ResourceLocation 最多 128 UTF-8 bytes，沿原 P8 cap 且低於 Scope D text slot 384。純視覺不能證時走原 fallback；不能變成 gameplay authority。P8 自有 current/fallback，無 gameplay 或 wire 授權。 |

### 3.1 R1 封閉 compact projection

**ENGINEERING_DERIVATION**　Projection 是 transient typed values 的 canonical logical-width 計數，沒有 byte encoder、持久格式或 wire ABI。每 lineage 最多一份，保守對各 lineage 原額扣帳。Fq descriptor/event names、backend capability tag 與 creation input 名稱屬固定 profile codebook。兩個 Unit 只持數值 row 與可選 profile，無 C maps、rich IR、raw body、full source map、diagnostics、writer object 或 slot handle。Lease header/authority 另持不可互換的 writer、slot、readback、store proof 與 object identities；下列 fingerprint 都只是 scalar evidence，無 authorization。固定工程 profile 可以直接從已核可信 context 提供 1000，於 cold qualifier 寫入 immutable accounted projection，P5 在 accepted native binder 比對同一 server-supplied typed value，child 只繼承原值和 parent fact。入口與公開 facade 都不接受 launch operand；純 binder 和隔離測試的 typed policy fixture 可證 1..1000／MAX+1。Program fingerprint 按同次 C approved key 與選定 V0 profile 的 bounded binding 做 domain-separated SHA-256，存 32 bytes，執行權仍以 exact immutable witness 檢查。

| Material／最大次數 | 表示與最大 charge | 複製／引用 |
|---|---:|---|
| Lineage header | 4×u8（projection/profile ABI、unit count、root form）＋source UUID 16＋owner UUID 16＋generation i64 8＋frontend digest 32＋program digest 32 = **108** | 複製穩定 scalar、digest 為 32 原始 bytes。 |
| Optional Manifest provenance | stable Manifest ID 8＋exact revision 8＋entry 8 = **24** | 由 C Provenance 複製一次。Whole availability proof 留 catalog/lease；未選 entry 未列入 projection 或 execution pins。 |
| 兩個 Unit | 每 Unit typed same-owner Spell id 8＋revision 8＋content digest 32＋activation 1＋on_create presence 1 與 optional handler id 8＋on_hit presence 1 與 optional handler id 8＋appearance state 1＋statement count 1 = **69**，兩個 **138**。 | 各 Unit 複製 exact C binding、含兩個 stable author handler ids。on_create 即使無 authored handler 仍由真 OPEN 派發 fact，空 stack。 |
| Creation schema＋bound value | INTEGER-tag u8 1＋required/no-default flags u8 1＋launch_step_milli u16 2＋origin tag u8 1＋unit-inheritance mask u8 1 = **6** | 每 lineage 一組。固定 schema 名、range 1..1000 和 binder codebook 由已驗 profile 驗證，不複製字串。兩個 Unit 共享。 |
| 三個 authored statements | 2 Damage，各 opcode u8、block id i64、author ordinal u8、damage_hp u8 → 2×11；1 Create 相同前三欄及 exact child Unit ordinal u8 → 11，合 **33**。 | Handler ID 引用其 Unit 的 on_hit row，不重複存。Create 的 ordinal 只會指到已核對 exact binding 的 index 1。 |
| 六個 appearance profile ID | Root/child 各三項 sound/particle/trail，每欄 u16 length＋最多128 UTF-8 bytes，6×130 = **780**。 | Copied bounded immutable 明確文字；各 canonical ID 全字串按 UTF-8 算。Absent/fallback 無 text payload；三欄均存在時才是全 triplet。 |

唯一最大 legal projection 是由 Manifest 選出的 root、trusted activation、兩個 handler 都存在（on_create 空、on_hit Damage→Create），及唯一 exact 無 activation child（on_create 空、on_hit 一次 Damage、無 Create），二者的三個 ID 皆 128 bytes。可構造形如 `gramarye:` 加 119 個合法 ASCII 路徑字元的 canonical ID，六組可各自有效，P8 catalog 可有其各自 channel 的 fixture。Manifest 的第二 entry 選另個具有 activation 的獨立合法 root；其 whole-availability proof 不增加本 projection 的 material。Spell body 上限與可信 descriptor 的三個 text operands 均容許。計算以 checked add/multiply 在每欄長度／count 校驗後進行，`108 + 24 + 2×69 + 6 + 3×11 + 6×(2+128) = 1089`。計量不加無源倍數或 headroom；ceiling 等於已驗合法最大替代值。Actual JVM headroom 獨立由 heap/token/GC probe 觀測。超出欄位或結構先拒，1089+1 不可能由合法 shape 達成，直接驗最大真 fixture、129-byte 欄位、第三 unit、重複 id／overflow 以及注入已超界的 charge。Raw/unique pins 和每 server charge 見 [容量](V0-limits-config-and-capacity-contract.md)。 [逐欄 TSV](../evidence/v0/projection-inventory.tsv) · [C source](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDProgram.java#L13)

## 4. 生命週期

| Transition | Owner／材料、sweep、P5 可見性及失敗 |
|---|---|
| ABSENT → PREPARING | Cold lane 佔一格，exact source／writer／generation／readback／context 冷驗。Compile 取 immutable image，無 pin、無 P5 active work。Compiler/front-end/whole Manifest/最大值失敗即原票 terminal，釋 cold。 |
| PREPARING → PROVISIONAL_LEASED | Catalog gate 原子核對同一 coherent head、context、capture ticket，整份 exact closure 與可選 Manifest 一次成為 live pins，另有 program charge。失配/capacity/overflow 不留下 partial lease。P5 只可取得候選。 |
| PROVISIONAL_LEASED → ACCEPTANCE_READY | P5 main 預留 root/child 的 instance、lineage、queue、carrier、hit、terminal、release 與 program charge，走原 P11 owner 的 V0 W admission，再重驗 actor／session／slot、native、binder、budget。Reservation 單次綁 exact candidate。可見預留仍不可執行。失敗撤原 W/預留與 provisional lease。 |
| ACCEPTANCE_READY → ACCEPTED | 唯一線性化點是 gate CAS，把 exact candidate 和已預置的 P5 reservation 一起轉為 committed lease。CAS 後同一 main segment 只啟用已配置 row，無 allocation、native/P11 call、callback、yield。CAS 前拒的 event 不入可執行 queue。CAS 後失敗算已受理 fault，照原錯誤路徑收束。 |
| ACCEPTED → RELEASING | 原 slot 先撤 event/child/native claims，再等已有 queued/claimed/current/required exact handoff 的真 terminal；最後可產原生責任者收束時釋 W。原 receipt 送 catalog，整份 pin 仍留保護。 |
| RELEASING → RELEASED | Catalog 核對 facade、slot、writer、lease ID/ref 與 exact last-consumer receipt，原子移除 pins／program charge。Late/duplicate/cross-slot 不遞減其他 cell。Busy 的 release 保留原 fixed cleanup cell；不查 latest。 |
| Preaccept → REJECTED → RELEASED | 尚未入列的原 ticket 被撤，只有 exact holder 釋其 provisional。遲到結果僅走原票 cleanup。 |
| Any → FENCED → RELEASED when safe | Gate 先停 new accept/sweep、撤相依實例與 carrier 能力、保必要不明 material。Safe exact disposition 後釋 pin。Same JVM 新 slot 不能以 UUID 或新的 writer 代原 token。 |

## 5. 線性化、reclaim 與 lock order

1. Cold lane（配置單格）先做 `ScopeDStorage` source/writer/readback、current／pending／recovery 與 C trusted context 的完整核對，鎖外編譯。取得 provisional 時 CAS 比對 verified head object 和 exact context。原票過期、head stale、worker busy 均具名終止，無自動重抓。
2. Server owner 同一段驗 exact P7 live actor/slot/session/connection/PLAY、loaded geometry 與 P11 W，再備妥所有 X/L/E/P/W/R 槽、checked ID／deadline。W 由 `P11FoundationService.acquireV0Work(ServerPlayer, int maxV0Lineages)` 循既有 acquire flow 在 P11 原 monitor 以同次 read/charge 證 `T_after + L_hard ≤ maxUuids` 且 `dirty_current + L_hard < dirtyUuidAdmissionWatermark`。同一 account reuse 不增 T；`L_hard` 是本 slot 的 configured maxLineagesPerServer，範圍 1..4。只管新 V0 W，既得、legacy、已成立並稍後出現的 native/dirty 義務繼續依原 owner。P11 回呼已退出才進 gate。相同 server-thread session P7 mutation 不插入最後 compare/publish；transport fault 若在後續發生，照原 native guard + L1 收束。
3. Catalog RAM gate 將完整登錄的固定 lease rows 與可更換的 head stamp 分離。任何 snapshot 都納入 provisional/accepting/accepted/closing rows，沒有半公開 pin。Compare-and-accept 以一次 CAS 將同一 open witness 換成綁 reservation 的預配 committed stamp，緊接同一 P5 main segment 完成不拋錯的 row activation。若先 fence／publish，CAS 失敗，不 spin、不 I/O。若先 accept，後續 publication／sweep 即使在 accepted flag assign 前觀察，也已必須保其完整 provisional exact roots。Off-thread reload 以一次 atomic exchange 安裝唯一 closed token，worker 只能 CAS 該 token reopen；新 fence/stop 永遠不能被早期 token 打開。
4. Sweep 先封 acquire/accept，再取同 source、writer、generation、readback、context 的 complete snapshot，聯集 current routes、pending/recovery 必留、durable external closure refs、live provisional/accepted/releasing/fenced exact roots。已知 safe release 只可讓 snapshot 過度保留；replace 前重驗 inventory epoch，stale sweep 用原 M1 安全處分 pending material，不同請求重試。從終驗到 replace 不允新增 pin；已完成的 release 只減保護，保守 union 仍涵蓋。Transient roots 只保留 revisions bytes，無 externalRoots 序列化、無 runtime metadata。Pending／unknown 或 missing roots、byte/record cap 時停止該 sweep，無部分 purge。真正 commit／recovery 仍使用 M1 exact forced readback。
5. Reload/stop 先關門，不等待 worker。已接受責任主線在原 slot 收束，沒有新的 native callbacks。原 worker 只能完成 bounded own cleanup／copy-free completion、釋 OS writer lock 後終態。同來源的 successive server 在舊 writer 未離開前 fail closed。Restart 不重建 runtime rows；可移除舊 revisions 仍由原 M1 完整根核對。

| Owner | 合法次序 |
|---|---|
| Server main／P5 | Actor/native facts、W、預配置換 → memory gate → 啟用 preallocated row。無 P5→physical catalog。 |
| Catalog | Storage/current monitor 與 RAM gate 分離。短 gate 僅 typed fields／CAS，0 I/O、0 P5/P11/P6、0 world/network。所有合規 mutation 先透過 gate。 |
| P11 | Server main 先辦 W／qualification，再進 gate。Fop/Qctx/N/dirty duties 永遠依自身 owner。 |
| P6/P9/P8 | Dispatch/target/native add/hurt/recipient/delivery 都在 gate 外。P6 call-scoped，無 latest；原生 callback 不持 service locator，也不從其他 thread 關錯 lease。 |
| Cold lane／lifecycle | Exact ticket 之 1 格 request/completion，reload gate 無 world callback；stop fence 後只做 owned completion，禁止 main 等 worker／join。 |

## 6. P5 after-set

擴 `SkillRuntimeService` 唯一擁有的 `ServerSlot` 的 package-private V cell，維持同一 Post、同一 drain/dispatch guard、同一 runtime tick 及 owner ordering。V0 固定 12/24 個 key cells 作同一 P5 queue 的 typed partition；原 legacy typed heap、no-peek-at-equality、defer、budget、fault 仍走原分支。P5 以 due tick 再同 owner 單調 sequence 區分有效 ready work；保 legacy relative order 和原 IDs。Private slot queue-order sequence 供跨 lane 比較，沒改 legacy event envelope 或 H6 §47.3。兩 partition 的 budget 各自保餘裕，任一飽和不耗用另一個已接受的 reserve，沒有新 scheduler／clock／subscription。

| 身分 | 配發與用途 |
|---|---|
| Spell stable identity／exact revision／program | Catalog owner，C key 加 V runtime policy。Manifest exact revision/entry 只是 source provenance。 |
| `V0SpellInstanceId`／`V0LineageId` | Package-private checked slot sequence。每 root/child 分別 instance，root 建新 lineage，V0-only NEW_INSTANCE。 |
| `V0EventId`／`V0StatementId` | P5 positive event sequence＋kind/due/instance sequence；parent event＋statement＋child ordinal。Statement 具 handler ID、block ID 與作者順序。Release 是單獨 typed coordinate。P6 wire-free request identity 與上述座標綁定；receipt 只在當次 call。 |
| `V0CarrierBindingId`／`V0LeaseToken` | P9 exact carrier instance/slot/level/object，P5 exact lineage→lease；native UUID 或 entity numeric ID 只是輔助辨識。 |
| A／creator／cause／payer／R | Root 與 child 的 creator/cause 都是被捕獲的真 exact A。Child 的直接 causal parent 是父 instance，不構成新 actor witness。正 fee 需要在線原 payer A 與 exact writable qualified Mana account；P11 W 可能依法保護 B 的整體 reward 接收，不能當扣款權。R 憑 P11 whole native recipient/writer 資格。UUID 只管自我排除與 attribution；B 不替 A。 |

L=2/4 server、1/2 attribution，root attempt 1/2 server/tick、最多 1 attribution/tick，但每 server/tick 固定最多成功受理 1 個 completion。Cold ingress ticket 最多 8 runtime ticks，含 equality；逾時撤原票，尚在執行的 worker 仍持該格直到真終態，不以 timeout 假釋並開始第二份。每 lineage 預留 2 instances 與 carrier/claim opportunities。E 預留 release＋on_create＋on_hit 三格/instance，6/lineage、12/24 server。R 另記 hit、native terminal/close、lease release 三個責任/instance，6L=12/24，再加 1 cold cleanup 格。Root scheduled tick = checked current+1（使用當前 drain 已過／下一合法 phase），原祖先 deadline = scheduled tick + configured `lineageDeadlineTicks`（預設／硬值 24／40）。Deadline equality 仍可依法完成，當次算式一律 checked。Carrier age/range 16/24，child 最早下一合法 P5 drain，同祖先上限；能力不足具名拒新 child。每 lineage 僅一次 child，使用後不回收。on_create 只有 confirmed spawn+OPEN 才排，on_hit 只有 native contact/single claim 才排；保真 hit 原合法 drain，沒有為壓力／logout 加延遲。自然 on_destroy 一次 scalar observation，零 gameplay。Stack 進入前預留整段配額，首拒絕、失敗或 unknown 停尾段，保 applied prefix 及原 Throwable。Logout cleanup exact observation 只依原 L1 期限與合法 native phase，禁止重掃、重跑 hook 或用 B reconnect 補資格。

## 7. 失敗與直接接受

| 故障 | 具名結果／原證據 |
|---|---|
| Wrong root/entry、whole unavailable、無 activation、C 合法但超 V subset | ROOT_UNAVAILABLE / MANIFEST_UNAVAILABLE / NO_ACTIVATION / PROFILE_REJECTED；0 active event/pin/Mana/world。 |
| Wrong writer/source、generation/context、pending/recovery、CAS 競爭 | SOURCE_UNPROVED / HEAD_STALE / CATALOG_BUSY；無重取。舊已接受 work 分開判 exact lease。 |
| Wrong actor/session/slot、deadline、W、配額 | ACTOR_STALE / SLOT_STALE / DEADLINE_EXPIRED / WORK_UNAVAILABLE / CAPACITY；原票與全 provisional 一次釋回，0 native effects。 |
| Fault 或 Error 在 CAS 前／後 | 前者無發布，後者是已受理 fault。清理不掩蓋原 server Throwable，worker 不把 Throwable 帶入 P5 state。 |
| Second child／depth2／MAX+1／late hit | 具名 precommit/handler rejection，保既得 hit／cleanup slots、原結果、原 deadlines。 |
| Duplicate release／same JVM 新 slot／lost source／sweep race | STALE_OR_RELEASED / FENCED / blocked sweep。舊 token 無法扣新 cell，未知來源保護、C/M1 recovery 依原正式 proof。 |

直接回歸含 `ScopeDCatalogTest.reclaimProtectsExactClosureAndRefusesStaleInventoryOrRetiredIds`；`ScopeDCompilerTest.standaloneAndManifestShareSelectedProgramWithIndependentOrigin`、`invalidUnselectedEntryDeniesManifestAndFullCandidateButPreservesStandaloneProjection`、`laterReservationAndForeignPhysicalSourceCannotUseOlderProof`、`proofSuccessfulButPhysicalForceFaultFencesUntilSameCommitRecovery`；`ScopeDStorageTest.restartAfterPhysicalMoveAndAfterDurableDecisionBeforeRamOrAck`、`writerExclusivitySurvivesFailedSameJvmAcquisitionAndReleasesOnClose`。新 deterministic barrier 依 exact source capture／pin CAS／snapshot／stage／P5 compare 鎖定正反次序與敗後回收。實機項另驗已接受 root/child 讀舊 closure、新 publication 後只有新 root 使用新版本、最後 consumer release 及安全 reclaim。 [Catalog tests](../../src/test/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCatalogTest.java#L75) · [Compiler tests](../../src/test/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCompilerTest.java#L24) · [Storage tests](../../src/test/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDStorageTest.java#L99)

`ScopeDProductionIsolationTest.onlyPackageInternalTypesCanNameCatalogSourceAndProof` 僅放行具名 facade references；`productionJarContainsDataEngineAndNoTestProofFaultOrClientHarness` 保 compiler/plain package 限界，另對工程 variant 放行其唯一可信 descriptor construction，驗普通 JAR 無 fixture/entry。P5 `v1AuthorityPlusTheExactS4GuardIsByteForByteTheProductionVocabulary` 保持；`p5HasNoBackgroundReflectionUnsafeOrRawGenericSurface`、`queuedAndDiagnosticStateRetainsNoUnexpectedLiveObjectsThrowableOrConfigProvider`、`scheduleDrainAndChildPublicationSourceOrderRemainClosed` 延至 V 限界。既有 `SkillRuntimeAuthenticatedCastIngressTest`、`P11AcceptedWorkBoundaryTest`、`P11AcceptedWorkObservedHitTest`、`P11ExactCleanupTest` 原 cases 照跑，加入 mixed lane 確認兩組 quota/順序和 natural accepted hit 延遲不變。 [C gate](../../src/test/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDProductionIsolationTest.java#L33) · [P5 gate](../../src/test/java/com/yo1no/gramarye/P5RuntimeStaticGateTest.java#L1910)

## 9. Java 邊界精確 inventory

下列 Java 片段列實際 signature、封閉 permits 和建構 visibility，不是 source 實作。Catalog 額外 public/protected surface 一律 `ADOPTION_REQUIRED_PUBLIC_SURFACE`。Before 為 0；after 依表，`ScopeDCatalog/ScopeDSource/ScopeDStorage/ScopeDProgram/ScopeDFrontend` 的 public 表面仍為 0（頂層類型皆保持 package-private）。所有 handle final、建構器 private，沒有 `Serializable`、Codec、mutator 或任意 subclass。Enum 的 Java 產生方法另計。表中的 `package` 成員為 `PACKAGE_PRIVATE_DELTA`。

### 9.1 Ordinary JAR 可見但無 composition 的受控 facade

Package `com.yo1no.gramarye.magic.definition.scoped`；`SkillOwnerId` 精確型別為 `com.yo1no.gramarye.magic.api.id.SkillOwnerId`，`Optional` 來自 `java.util.Optional`。

```java
public final class V0RuntimeCatalog {
    V0RuntimeCatalog(V0CatalogAuthority authority, java.nio.file.Path sourceRoot,
            ScopeDFrontend.Context context); // package-private, cold assembly only

    public PrepareResult prepare(V0CatalogAuthority authority, long captureTicket,
            SkillOwnerId owner, Selection selection, int maxManifestEntries);
    public Acceptance compareAndAccept(V0CatalogAuthority.Reservation permit, Candidate candidate);
    public Disposition discard(V0CatalogAuthority authority, Candidate candidate);
    public Disposition release(V0CatalogAuthority.ReleasePermit permit, Lease lease);
    public boolean usable(V0CatalogAuthority authority, Lease lease);
    public Suspension suspend(V0CatalogAuthority authority);
    public boolean recoverAndReopen(V0CatalogAuthority authority, Suspension suspension);
    public Disposition retire(V0CatalogAuthority authority);

    public sealed interface PrepareResult permits Candidate, Rejected { }
    public static final class Suspension { /* private constructor, opaque exact owner/gate epoch */ }
    public static final class Candidate implements PrepareResult { // private constructor
        public Lease lease();
    }
    public static final class Rejected implements PrepareResult { // private constructor
        public Refusal reason();
    }
    public static final class Selection { // private constructor; immutable logical data
        public static Selection standalone(long spellId);
        public static Selection manifest(long manifestId, long stableEntryId);
    }
    public static final class Lease { // private constructor; opaque immutable token plus program
        public int programChargeBytes();
        public Unit root();
    }
    public static final class Unit { // private constructor; immutable V0 projection
        public Optional<Profile> profile();
        public Optional<Damage> hitDamage();
        public Optional<Create> hitCreate();
    }
    public static final class Profile { // private constructor
        public String soundId();
        public String particleId();
        public String trailId();
    }
    public static final class Damage { // private constructor
        public long handlerId();
        public long blockId();
        public int damageHp();
    }
    public static final class Create { // private constructor
        public long handlerId();
        public long blockId();
        public Unit target();
    }

    public enum Refusal {
        INVALID_SELECTION, SLOT_STALE, TICKET_STALE, SOURCE_UNPROVED,
        HEAD_STALE, CATALOG_BUSY, ROOT_UNAVAILABLE, MANIFEST_UNAVAILABLE,
        NO_ACTIVATION, PROFILE_REJECTED, CAPACITY, PREPARATION_FAULT
    }
    public enum Acceptance { COMMITTED, CATALOG_BUSY, HEAD_STALE, INVALID_RESERVATION }
    public enum Disposition { RELEASED, CATALOG_BUSY, STALE_OR_RELEASED, FENCED }
}
```

Selector 採 strictly positive longs 的 typed constructor choices，內部用閉合 Spell／Manifest branches 和 private fields。Selector 僅是資料。只有 exact P5 slot／engineering cold lane authority 可使用它；revision 與 owner 在同 current source 內解出。無效參數在消耗 authority 前拒絕。

`Unit` 保私有 exact revision identity 和空／缺席 on_create hook。`Damage`／`Create` 攜 stable author coordinates 與 typed contents。Child unit 只指 frozen `Lease` 內的 exact target。無 collection getter、generic evaluator、physical digest accessor、tag lookup、callback 或 live world value。完整 exact diagnostics 是工程證據 owner 的有界 scalar 記錄，按 exact ticket/lease provenance 繫結。

| Member | 唯一 permitted caller／retained field／exception policy |
|---|---|
| Catalog constructor／private owner cells | Engineering-only `V0EngineeringCatalogAssembly.open`。Retains exact passed slot capability, qualified source root, trusted frontend identity, bounded owner table, gate+writer state。No world objects。檔案實際開啟由 cold work 完成。 |
| `prepare` | `V0EngineeringColdLane` designated cold worker。Slot authority、writer lane/thread、exact capture ticket 和 immutable startup capture bounds 一併驗；max tuple 必須精確等於最初 P5 startup witness 綁定值，且低於個別 hard ceilings；scalar 由固定 lane 轉載，不接受任意 override。正常 source／capacity／compiler 拒絕走有限 Refusal，cold I/O 先 seal gate 然後輸出具名拒。M1 primary 原物件只由 worker supervision 保留其 raw failure，跨界僅 scalar fault。 |
| `compareAndAccept` | `V0SlotRuntime` P5 main only；原 Reservation 綁 exact candidate 且私有 one-shot。正常競爭 typed outcome；同一請求於具名 terminal 後不再受理。 |
| `discard` | `V0SlotRuntime` P5 main one exact unpublished completion. Liveness budget 一次 CAS，busy 由 fixed cleanup cell 完成，accepted row 不可 discard。Cold lane 從未交出／stop 的 candidate 用 owner package-private cleanup。 |
| `release`／`usable` | `V0SlotRuntime` P5 main。ReleasePermit 由最後 consumer 收束點配發；usable 用 exact accepted lease 的 witness 判是否仍可 dispatch，無 current 或 physical read。P6/P9 不呼 facade。 |
| `suspend` | `V0SlotRuntime` 擁有的 exact reload/stop adapter，同步封 gate、產原 epoch 的 exact Suspension，無其他 callback，無等待。Stop 自身再閉 slot capability，沒有 reopen。 |
| `recoverAndReopen`／`retire` | `V0EngineeringColdLane` only。恢復需原 Suspension、P5 close/dispatch accounting 完成後才交給 cold lane 的同次 ticket、同 M1 durable/recovered writer proof。無 arbitrary token 製造，若後繼 fence/stop 已接管則拒。Retire 只可 close exact writer，不能擅自取消 P5 既得工作或丟保護材料。 |
| Selection／Candidate／Lease／Unit scalar members | `V0SlotRuntime` 唯一 production consumer；P5 configuration 和 P8 顯式 typed adapter 不讀 catalog internals。沒有 handle 可填入 queue/state 作為自己的 authority。 |
| Legacy catalog/proof | 保現有 package-private 數量、sealed operands、compatibility proof private constructor，以及 C/M1 base 測試。 |

Package `com.yo1no.gramarye`。Token 無 `ServerPlayer`／`MinecraftServer`／`Level`、container 或 callback fields。Private exact identity 由 startup owner 配發。Server thread identity 為 immutable creator thread ID 與自有 slot object identity；只在 current owner thread 能 mint reservation 和 release permits。唯一配置型 capture limit `maxManifestEntries` 於 slot startup 取自 immutable `V0StartupLimits`。`matchesCaptureLimits(int maxManifestEntries)` 只由 `V0RuntimeCatalog.prepare` 查 exact equality，無可讀 limits getter。固定 closure／projected charge 由 profile qualifier 執行。

```java
public final class V0CatalogAuthority {
    private V0CatalogAuthority(long slotGeneration, long ownerThreadId,
            int maxManifestEntries);
    static V0CatalogAuthority open(long slotGeneration, V0StartupLimits limits); // package, V0 slot owner only
    Reservation reserve(V0RuntimeCatalog.Candidate candidate); // package
    ReleasePermit permitRelease(V0RuntimeCatalog.Lease lease); // package
    public boolean isOwnerThread();
    public boolean matchesCaptureLimits(int maxManifestEntries);

    public static final class Reservation { // private constructor; one-shot exact object
        public boolean matches(V0CatalogAuthority authority, V0RuntimeCatalog.Candidate candidate);
    }
    public static final class ReleasePermit { // private constructor; one-shot exact object
        public boolean matches(V0CatalogAuthority authority, V0RuntimeCatalog.Lease lease);
    }
}
```

`matches` 無 mutation；同 exact object、exact capability、owner thread 與 gate expected lease state 才接受。One-shot 消費由 catalog gate 的 row state 決定，不能由只呼 `matches` 偽造。Closed gate 不放過 malformed、cross-facade、old slot 或 reused permit。唯一 writer 離開時舊 authority 沒有任何後代權限。任一 programming invariant throwable 按原 owner、原 identity 維持，正常未提交結果不接管 throwable。

### 9.2 工程 variant-only inventory

```java
// package com.yo1no.gramarye.magic.definition.scoped
public final class V0EngineeringCatalogAssembly {
    private V0EngineeringCatalogAssembly();
    public static V0RuntimeCatalog open(java.nio.file.Path qualifiedSource, V0CatalogAuthority authority);
    public static FixturePublication publishFixture(V0RuntimeCatalog catalog,
            V0CatalogAuthority authority, SkillOwnerId owner, FixtureRevision revision);
    public enum FixtureRevision { BASE, UPDATED_CHILD }
    public enum FixturePublication { PUBLISHED, SOURCE_UNPROVED, BUSY, REJECTED }
}
```

只有原 authority 的 `V0EngineeringColdLane` 能呼叫 `publishFixture`。隔離工程 composition 是 `open` 的唯一 caller。Publication 只給入口依真登入已授的 owner，走 M1 reserve→prepare→C proof→publish 和 durable readback。Stable ID mapping 由工程 fixture 的私有已發布表提供；public result 僅回 disposition。兩個工程方法列入跨 package inventory，catalog package-private constructor 只由 assembly 呼叫。工程測試和注入與同 mod 身分的工程 distribution 一併隔離，同程序只載工程或普通版本之一。普通 startup composition 回 empty，無可信 V0 frontend builder、cold lane 或 facade instance。

### 9.3 Package-private owner additions

```java
// package com.yo1no.gramarye
final class V0SlotRuntime { // exclusive P5 substate, no autonomous lifecycle
    V0SlotRuntime(V0SlotStartup startup, V0SlotBinding binding);
    V0Admission acceptCompletion(V0CaptureCompletion completion, long runtimeTick);
    void invalidateForReload();
    void stop();
}
record V0SpellInstanceId(long value) { }
record V0LineageId(long value) { }
record V0EventId(long value) { }
record V0StatementId(long handlerId, long blockId, int authorOrdinal) { }
record V0CarrierBindingId(long value) { }
record V0LeaseToken(V0RuntimeCatalog.Lease exactLease) { }
enum V0TerminalReason {
    NATURAL_HIT, NATURAL_BLOCK, NATURAL_RANGE, NATURAL_AGE,
    REJECTED_BEFORE_COMMIT, CONFIRMED_NOT_APPLIED, OUTCOME_UNKNOWN,
    OWNER_INVALIDATED, DEADLINE_EXPIRED, CHUNK_UNLOADED, ENTITY_UNLOADED,
    LEVEL_UNLOADED, RELOAD, STOP, SOURCE_UNPROVED, FAULT
}
```

只有既有 P5 owner 可構建、持有 `V0SlotRuntime`。工程 ingress、P9 callback 的有界 typed events 都交此 P5 owner。Exact platform context 由 P5/P9 call-local custody 核對。Instance/lineage/event/carrier/lease 欄位限原 slot 或有界 carrier binding。非公開 record 的 Java 隱含 accessor/value methods 不列為 exported type surface。依本表採用明列的 public declarations 與 Java 強制 enum helpers，H6 §47.3 exact declarations 保持。

| Inventory | Before | After additions |
|---|---:|---:|
| Ordinary Catalog/P5 public class/interface/enum declarations | 0 | 17 (2 top-level, 15 nested) |
| Ordinary explicit public/protected methods/constructors | 0 | 30 methods, 0 constructors, 0 protected |
| Ordinary implicit enum methods | 0 | 6 (`values`/`valueOf` for 3 enums) |
| Ordinary public static enum constants | 0 | 20 (12 Refusal＋4 Acceptance＋4 Disposition) |
| Engineering-only Catalog public type declarations | 0 | 3 (1 top-level, 2 nested) |
| Engineering-only public methods/constructors | 0 | 2 explicit methods＋4 implicit enum methods, 0 public constructors |
| Engineering-only public static enum constants | 0 | 6 (2 FixtureRevision＋4 FixturePublication) |

此表只計 Catalog/P5 跨 package seam；P7 witness、P6 receipt 和 P9 mandatory override 各有直接 inventory。既有 public/protected declarations 變更為 0。Static test 對 exact signature/descriptors/access flags、enum fields、permits、不可序列化及具名 caller。Ordinary JAR 須驗工程 assembly、descriptor fixtures 與 entry 均未封裝。
