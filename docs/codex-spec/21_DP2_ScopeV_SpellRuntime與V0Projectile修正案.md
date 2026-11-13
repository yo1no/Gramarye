# DP-2 Scope V — Spell runtime 與 V0-PROJECTILE 修正案

**Scope V 1.0 · USER_CONFIRMED / ADOPTED_IN_SCOPE · DOCUMENT AUTHORITY**

**V0 runtime · NOT_IMPLEMENTED · RUNTIME_UNVERIFIED**

## 1. 效力、範圍與決策來源

使用者已選定 V-01～V-10、V-B 與首片 V0-PROJECTILE，並限定重施政策、child 資格上限、工程資源政策、生命週期及事件來源。本修正案採用這些決策，依 §9 的 replacement／preservation ledger，僅在明列的新 Spell family 與 V0 範圍內具優先效力。Scope D 1.0、M1、C 與 P11 的既有規範和驗收維持其原適用範圍。以下新增的 runtime 接點均待另授實作與直接驗證。 [Scope D 1.0](20_DP2_ScopeD_SpellDefinition與可選SkillManifest修正案.md)

採用沿革記錄原審查座標 `d4ab92cafea5c6728df83858aca83bb030a4b071`、tree `7ff0cdc21493b3b60306fb59666e2b4a4369ab65`。集中決策包記載使用者確認的來源，C 的既有驗收提供當時的工程證據。

| 適用範圍 | 規範 |
|---|---|
| 新 Spell runtime 共通契約 | Server-authoritative 入場、單一 P5 owner、exact closure、子代繼承、ordered effects、資源與釋放責任。 |
| 首片 V0-PROJECTILE | 一種受信 projectile backend，一個隔離真認證工程入口，loaded-only、同 server、memory-only。工程材料走既有 catalog 相容證明和可信資料來源。可驗 standalone／Manifest、原生飛行與命中、延後 child、P6 真效果與 P8 呈現。 |
| 延後 | 正式玩家收費、正冷卻及其 key／durability／sync，正常玩家入口與 UI／keybind／selection／equipment，公開 command，新 network／payload，editor／renderer，Element E／`PhenomenonSpec` runtime，其他 backend、selector、upkeep、手動控制／轉移、persistent runtime／restart restoration、跨服、P12。Scope D 的 Operation／Custom Action 與玩家任意程序禁令持續有效。 |

## 2. Root admission 與 exact capture（V-01）

1. **工程入口。** 只有隔離 server-side harness 可持受限工程能力，對真認證、當前 PLAY session 的 exact actor 送入有界 logical intent。Server 驗證 permission、native actor／level、session／connection、server generation、qualified source 及工程 policy，並從受權 owner catalog 選擇 standalone exact Spell 或 exact Manifest revision＋stable entry。入口禁止由呼叫端指定 revision、program、actor/owner/target 作權威、付款許可或 bypass；原 P7 payload／slot 0 不導入 V。工程入口與 fixture 不得進普通 JAR 的可達組合。
2. **候選。** 一次捕獲僅使用一個已證 current catalog generation、source/writer incarnation 與可信 frontend/backend/policy context。Manifest 先驗 whole-root availability，再以 selected Spell 的完整傳遞閉包作執行輸入；未選 entries 不因 availability 檢查而成 execution pins。外部 root 必須有該 backend 支援的 activation。無 activation 的定義可當 library，符合已驗 child contract 時才可被建立。Creation-input binder 驗 required/default、型別、範圍與來源；server 按可信本體／facts 形成有限的 loaded geometry。
3. **接受點。** P5 發布 accepted root 前，必須先取得完整 exact closure lease、Manifest exact revision（適用時）、root/instance/carrier 預留、既得 hit/terminal/cleanup 容量、lineage allowance 與所需的 P11 W／native 責任容量。最後重驗 actor/session、source/writer、current generation、context、pins 及整組預留。Catalog 及 P5 的 compare-and-accept 交接須證明沒有 stale 窗口，且不持 catalog lock 執行 platform callbacks。Cold 準備須有界；owner hot path 禁同步 physical publication／scan。
4. **失效與 release。** 任一 authority 座標在接受前失效即具名拒絕、一次釋回 provisional 資源，無扣款／spawn／gameplay dispatch，沒有同次自動重取。新的合法 intent 可獨立驗證。接受後不得查 latest；P5 在合法 drain 以原 lease、token、deadline 與真 native guard 重新判定 release。單純較新的 catalog publication 不撤銷已接受 closure；reload、源資格失效及 §4 的生命週期 fence 各自適用。
5. **Derived artifacts。** Compiler 成功、cache hit、manifest metadata、publish proof、content hash 或任一 UUID 均不授 runtime authority。相同 exact closure/context 可共享 immutable program；兩種入口的 provenance 仍各自保存。

[Scope D §6](20_DP2_ScopeD_SpellDefinition與可選SkillManifest修正案.md#L83) · [C catalog](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCatalog.java#L187) · [P7 exact capture](../../src/main/java/com/yo1no/gramarye/magic/network/P7CastIntentNetworkHandler.java#L33)

## 3. Owner、身分、重施與操控（V-02、V-03、V-09）

| 身分／責任 | 規範 |
|---|---|
| P5 runtime owner | 現有 per-server owner 唯一發布、排程、執行、取消與終止 V 工作。Root 與每個實際建立的 child 各有自己的 server-generation-scoped `SpellInstanceId`；預留中的 child 尚未成為可執行 instance。 |
| Lineage／事件 | P5 分配一次 root lineage，child 沿用。每個 event 獨立於 instance，綁 exact server/token、instance、lineage、due tick 與已驗來源。所有子工作用原 ancestor deadline、共用 allowance，依各自較早的 terminal 失效。 |
| Definition／program | Catalog owner 持 exact revision/closure truth、pins 與 reclaim。C 產不可變 program；program key 和 authoring ID 不等同實例／事件／世界載體。 |
| Creator／cause／payer／budget | Creator 是受理時的 exact A。Native cause 須經原工作與 exact A 的受限見證，不能以同 UUID 的 B 代換。Payer 是原正規帳戶及 policy 的歸屬，V0 只在 exact 在線 A 下發生新正 debit。預算記原 admission owner/lineage，與 player session 或 world carrier 分開。 |
| Controller | V0 沒有創建後手動操控入口，無轉移、引導、dispel 或以再次 activation 觸發隱藏命名 handler。受信 backend 可依其封閉 motion/state 自動演進。其他控制能力須另行採用。 |
| Carrier／presentation／reward | 受管載體只持具名回報與 exact native custody。P5 仍決定 instance truth。P8 只消費受證 applied facts。P11 依原規則決定 whole R；creator、cause、payer、R 不因 UUID 相同而互換。P6 actor/account 僅在單次呼叫有效。 |

**V0 重施。** V0-PROJECTILE 採 `NEW_INSTANCE`。同玩家／logical address 的下一次合法 activation 各自取得新 instance、lineage、exact capture 與配額，舊工作按原期限自然結束。這項政策限 V0。其他受信 backend 的不同封閉重施策略須另行採用，禁止由玩家積木自行建立任意重施狀態機。

[Scope D §§6、8](20_DP2_ScopeD_SpellDefinition與可選SkillManifest修正案.md#L83) · [P11 §§4–5](19_P11持久化與多人修正案.md#L60) · [P5 受理](../../src/main/java/com/yo1no/gramarye/SkillRuntimeService.java#L159)

## 4. V-04 生命週期與終態責任

### 4.1 適用對象與檢查點

V0 的 runtime 限同一運作中伺服器、已載入的世界與記憶體內工作。P5 持有 instance、queue、lineage、原絕對 deadline 和終止責任。Catalog owner 持有該 lineage 的 exact pins。原生載體只保有對 exact server generation、instance、level 與 carrier identity 生效的能力。V0 不序列化 instance、載體、queue、hit、lease 或 runtime authority。新 server 一律從空的 V0 runtime 狀態啟動。

P5 在 dispatch、原生操作前、載體 custody 移交、hit claim 和後續 drain 重驗適用的 exact qualification。已受理的工作仍受原 actor／cause、source、server token、lineage deadline、pins、loaded-only 及 native permission 約束。死亡、卸載、reload、stop、期限或失證先使工作終止時，後到的 logout proof、登入、callback 或載體重新出現均不得恢復該工作。

### 4.2 逐項終止、pins、載體與既有原生責任

下表「依 §4.4 釋放」一律指最後一個合法讀取者與需用 exact binding 的交接真終態後，catalog owner 按原 lease 釋放整份 Spell closure pins 與適用的 Manifest revision pin。若同一 lineage 另有仍合格的既得工作，須保留其使用的共享 capture。表中的 reward／write 由既有 P11 或原生 owner 判定，保留確證結果和已成立義務。

| 生命週期邊界 | V0 terminal／繼續條件 | Exact pins | Carrier cleanup | Reward／write |
|---|---|---|---|---|
| **Normal logout 成功** | 同次具名原生清理的成功證據可使登出前已受理且仍合格的 pre-spawn、飛行、已 claim hit／其既得效果與既得 child，沿原 exact A、原 deadline 完成。未受理 work 終止於 ingress；登出後新 `CreateSpell` 拒絕。待正 Mana 扣款的離線 pre-spawn root 按 §4.3 終止。 | 合格工作繼續持有；未受理 reservation 或不可繼續的工作依 §4.4 釋放。 | 合格載體沿原生節奏繼續；不合格者撤權並關閉。原生移除 A 的流程保持。 | 原 logout save 使用同次具名保存接點與材料。晚到的合法 credit 沿 exact A，按當次 whole R 與 source 責任處理。 |
| **Transport 關閉、原生 cleanup 未終態** | 先關新 ingress。原生收束未證時不得開始新的 native mutation；已觀察 hit 僅能依 §4.3 保留一次。只可在原有期限內以同次完整正常證據取得繼續資格。 | 只保可證且仍有未收束責任的原 lease；失證或逾期依 §4.4 釋放。 | 保持原載體／hit custody；未合格不能再 hit、掃描或自行恢復飛行。失效後關閉。 | 原生清理及保存按自身真終態。未完成的 native 路徑不得當作 whole R 已交付或寫入成功。 |
| **Disconnect fault、被迫／不明離開或 cleanup fault** | 失去正常完成證據的受影響工作終止，封住未開始的 effects、hit/child 與 late callbacks。 | 取消後依 §4.4 釋放；不得以等候重連留住 V0 pins。 | 撤銷 exact permits，按已載入／移除狀態收束。 | 保原 throwable 及確證前綴。F1 只處理具名缺口；未知 native tail、reward 與寫入不得重播或冒稱已完成。 |
| **同 UUID 重連** | Qualified B 只取得自己的新 session 與新 root 資格。合法舊工作仍以 exact A 運作；同 UUID 的 B 仍屬 self-exclusion。 | 舊 A 租約由原 lineage 真終態釋放；B 的新 root 自取 pins。 | 舊載體保原 A binding；無重新指定 owner 或移交控制。 | 當次合法 whole R 可按 P11 使用完整 detached A 或 qualified B。不能以 B 替換既有 A 的 cause、payer 或未完成原生作用域。 |
| **Death** | 以原生確認死亡、已越過可取消入口的有效邊界，終止受影響 exact A 的尚未提交 V0 work；取消死亡不觸發本終止。 | 停止新讀取；尚在原生呼叫中的責任收束後依 §4.4 釋放。 | 關閉原 binding、待派 hit 與 carrier。清理不得生成新 gameplay。 | 既成傷害、死亡、掉落與合法 credit 按原生路徑收束。V0 不以停止或退款重寫其結果。 |
| **Respawn／End-return clone** | 於 A 失去原可執行身分時終止舊 work；與 A 的 death／移除終止去重。新 B 即使共用連線也無舊 work 執行權。 | 舊租約依 §4.4 釋放；未建立 V0 state 的新 B 不承繼 pin。 | 關閉或確認已關閉舊載體，不於 B 重新掛載。 | 原 clone、restore／copy、required material、membership、readiness 及保存各依 P11。無第二次 copy，亦不將 partial B 視為可信 whole writer／recipient。 |
| **Dimension transfer** | 已發生原 dimension／exact level custody 失效時終止受影響 work；取消 transfer 且原資格仍有效時維持原 work。 | 舊 scope 停止讀取後依 §4.4 釋放。 | 撤權並於原世界依有效接點清理；載體不跨維度移交。 | 原 travel／取消、載入與保存沿既有 owner。已提交結果按原 scope 保留，不為維度變更重新發出。 |
| **Chunk unload** | 原 movement、hit 或 continuation 欠缺所需 loaded witness 時，終止依賴該 witness 的 work。尚未開始的 effect 不補 hit。 | 依 §4.4 釋放已終止工作責任；仍合格的其他工作保其租約。 | 關閉受影響載體權限。載體若已退場，認其具證終態；不得為 cleanup 取得 chunk ticket 或強載。 | 無 V0 runtime 儲存。已發生原生效果與原 chunk／player writer 照既有終態，無 V0 補送。 |
| **Entity unload／level unload／tracking retirement** | Exact carrier/level 退出原生 custody 時關閉依賴它的 work。單一觀察者離開追蹤範圍只影響該觀察者的呈現。已 claim、尚未提交的 hit 失去必要 witness 即終止。 | Exact handoff 讀取者收束後依 §4.4 釋放。 | 以實際 unload/remove/tracking 觀測撤銷 exact permit；若 native traversal 正使用索引，先封 gameplay，再於合法接點完成同載體清理。 | 已發生的 native prefix 和 reward/write 依原 scope 持續。Retirement callback 不產新 effect／reward 或保存 V0 state。 |
| **Datapack／runtime reload** | 先關新 ingress；在原 owning boundary 終止受影響舊 context 的待派、飛行、hit/child。重開前須完成相依 cleanup，個別玩家同步不解除 gate。 | 舊 context 的讀取者收束後依 §4.4 釋放。新 context 另驗。 | 關閉舊 context 的 exact permits/carriers，晚 callback 拒絕。 | 保已提交 effects、原 P11 dirty 進度與合格 source。原 reward／command contexts 真終態，reload 不重發或讓較舊材料覆蓋較新資料。 |
| **一般 catalog publication** | 已接受舊 lineage 照原 exact capture／deadline；新根才採新 current。原 lease／source 欠資格另依失證處理。 | 保舊 lineage pins 至 §4.4；sweep 必納入完整 live pins inventory。 | 已接受舊載體保原 binding，catalog 更新不重建載體。 | M1 publication writer 與 P11 原生 reward/write 各依本責。新 catalog revision 不更換舊因果、帳戶或真義務。 |
| **C4a／CONFIG parking** | 健康前置拒絕時既得工作仍按原資格。真進入 CONFIG／parking 關 PLAY 入場；原 actor／level/native custody 失去即終止相依 work。Phase handoff 無正常 logout 授權。 | 舊資格存續才保 pins；終止後依 §4.4 釋放。 | 保仍合格的載體；失去 custody 的原載體撤權關閉。 | 只由真 Fop／Qctx／H 和 source 判原生 gate。單持 V0 instance、carrier 或 pin 不新增 blocker；原保存與 reward 沿既有義務。 |
| **Integrated host leave** | 真 host leave 導致 server stop 時，stop 終止整個 V0 slot。健康 C4a 拒絕不被轉成 host leave。 | Stop 路徑依 §4.4 清原 slot；不跨新 server。 | 停機前封 callback 並收束全部該 slot 受管載體。 | Host cached Player／`level.dat` 的原 qualified writer、native rewards 與真故障照既有 P11。V0 不新增 host-only 保存責任。 |
| **Dedicated stop** | 先 fence 新受理、事件派發與載體 callbacks，再終止該 slot 有界待辦。Stop 不派發 gameplay handlers。 | 同步收束可證 lease，停機終態完成有限清理。保留不明 source 保護，不能把失證當作可 reclaim。 | 關閉受管 permits、解除 bindings、清理已載入載體。無停機重建、補射或 `on_destroy` gameplay。 | 原 qualified writer／dirty／native operations 按自身終態保存。原有已 ARM 的正式冷卻義務依 P11；V0 不建立冷卻義務或 runtime 保存。 |
| **Same-JVM new server** | 全新 server slot/token、空 V0 queues 與 instances。舊 callback/lease close 不得接觸新 slot。 | 舊 lease 只向原 catalog owner 收束，不能扣減新 lease 或跨 slot 重用 handle。 | 舊載體不能在新 server 取得綁定，不靠 UUID／entity ID 接管。 | 新 server 只採各原生／catalog owner 有證的來源；舊 completion 不改新 writer、source 或 reward。 |
| **Full restart** | 無 instance、queue、claim、runtime timeout、hit 或 child 恢復；不重播未完成的原生作用。 | 不從磁碟重建 V0 pins。M1 依原 current／pending／recovery／external roots 規則判 reclaim。 | V0 不還原載體，也不掃 NBT 尋找或重綁舊 permit。 | 只依既有 owner 恢復可證耐久資料及正式義務。失證走既有 recovery／quarantine，V0 無 replay、補償帳或第二 writer。 |

### 4.3 正常登出證據、原生 hit 與工程正 Mana

- 只有 exact listener/connection、server slot、原 actor A、既有 work、相符 qualified source 和同次具名原生 cleanup 的成功終態能證正常登出。Transport 關閉、原 actor removed、UUID 或 roster 缺席都不足。現行 L1 的適用前件延續至 V0 的窄入口。Death、維度失效、reload、unload、stop 和期限終止優先；有故障或失證時照表收束。
- 原生碰撞若已觀察、而同次 cleanup 尚未證終態，保留至多一次具同一原 tick、exact carrier／claim 資格與 target 的有限 observation。仍須沿原生允許的時間點與 P5 原截止驗證，且保持既有 L1 的自然命中順序。禁止重新執行原生 impact hook、再次掃描、補射、穿透後再命中、等待重連、延長原 deadline 或人工增加 hit 延遲。晚到證據只對仍合格的既得工作有效。
- 先前受理且仍合格的 zero-fee pre-spawn、已合法 release 的工作、既得 child 與後續 effect，可按 §4.2 以 exact A 完成，且不新增正 Mana。尚需對離線 A 進行工程正 Mana debit 的 pre-spawn root，在世界 mutation 前終止。若同次原生再入已發生 debit 或世界 mutation，以 P6 的實際交易／補償與 native receipt 收束；不得向 B 扣款、猜退款、把 unknown 寫成零 mutation 或重播原生效果。
- 自然 terminal 的 `on_destroy` 在合法 custody 下至多記一次 bounded 工程觀測，例如自然完成的 hit、block、age 或 range。Forced cleanup、fault、失效或 P5 deadline 到期後的撤權不另發；觀測無 gameplay handler、P8 event、額外生命期或再次施放權。已確認 applied spawn/damage 的 P8 投影按原有條件各自成立。

### 4.4 終止、回收與既有 obligation

1. P5 在主 owner 邊界先撤銷 exact instance／event 的新執行與 callback 資格，收束尚未開始的 pending／claimed work，保留已發生的原生 prefix 與具名終態。令牌與 close 均為同次工作專用；晚到／重複 callback 不清另一 instance、source 或 generation。
2. Exact carrier 權限關閉後，由原生接點完成載體清理。載體正處於 native traversal 時，先不可執行，再在合法收束點清除。診斷與呈現不持有清理權，不可阻擋有效 release。原生 `RuntimeException`／`Error` 的既有傳播及 bounded cleanup 保留，無 catch-and-continue 或重播。
3. P5 以同一次 exact lease 核對最後一個可執行 consumer、既得 queued／claimed work 及仍需讀 exact binding 的 native handoff 已真正收束，再向 catalog owner 釋放完整 pins。Catalog 只在同一可證 source／writer／generation 有完整 current、pending、recovery、external roots 與 live pins inventory 時作 sweep。無完整證據則禁止該 sweep。Carrier removal、program cache miss 和長期 P11 保存都不自行觸發 reclaim。
4. 已存在的 P11 `W` 保到最後一個可產生該 native 責任的工作收束。已發生的 `N`、`Fop`、`Qctx`、`Dd`、`Iw`、`H` 分別由原 owner 判斷其真終態，可晚於 V0 instance／pins 結束。Work close 不等 whole reward 成功、command context 已 drain、dirty 已保存或 writer 已證讀回。V0 不另造 persistent truth，P6 無跨呼叫 actor／account／Mana receipt，P8 無 runtime pins。

適用既有條款與直接接點。 [Scope D §§5.3、6](20_DP2_ScopeD_SpellDefinition與可選SkillManifest修正案.md#L76) · [P11 §§4–6、7–11](19_P11持久化與多人修正案.md#L60) · [H6 §63.16](../architecture/P5-A-server-runtime-event-kernel.md#L23877) · [H6 §65.10](../architecture/P5-A-server-runtime-event-kernel.md#L25603) · [H6 §69.2](../architecture/P5-A-server-runtime-event-kernel.md#L27628) · [P5 normal logout](../../src/main/java/com/yo1no/gramarye/SkillRuntimeService.java#L1300) · [P9 native carrier](../../src/main/java/com/yo1no/gramarye/P9StarterProjectile.java#L105) · [P11 normal logout proof](../../src/main/java/com/yo1no/gramarye/P11NativeStorageBoundary.java#L1045)

## 5. Mana、工程冷卻與 release 事實（V-05）

1. **工程 policy。** V0 只使用受信工程 fixture 的明示 zero 或有限正 root fee，及明示 zero cooldown。正式玩家的費率、正冷卻、共享 key、時鐘／durability／readback／sync、player ingress 均待另行採用。工程 zero 對其他新 Spell 無預設效力，禁止借 legacy `SkillId` 或其既有冷卻 cell 表示 V obligation。
2. **正 debit。** 只由既有 P6 路徑與 `ManaTransactionService` 對 exact 在線 A 的正式帳戶處理。完成所有可前驗的資格、resolve 和 capacity guard 後，在同一次 root native release 交易內扣款。入場、編譯、排隊、pin 或呈現均無扣款。資源不足、A 不再合格或無法使用正規帳戶時，於 world mutation 前停止。Zero branch 無 debit。Native 已 apply 或結果未明，不依後續 miss、handler/P8 故障或缺回執推定可退款；確證無主要 mutation 時，依 P6 既有補償政策及 exact 同次 debit receipt 處理。無跨 tick 帳戶持有、escrow 或退款重試。
3. **Child／effect。** Child、effect attempt 分別記同一 lineage 的有限 allowance；V0 無第二次付費、獨立付款人、新正離線扣款或 upkeep。Static cost 只供編譯、可受理工作量估算與診斷；capacity 與 Mana 各自判定。
4. **Release 與 ARM。** 分開 `prepare`、P5 接受、原生嘗試、confirmed apply、exact transfer／OPEN 與未明結果。單憑 constructor、`addFreshEntity` 返回 true、P8 offer 或 callback 不能推得 ARM。失敗且確證從未完成 release、已 release、outcome unknown 各有終態；V0 zero branch 不建立正冷卻的 PENDING／ACTIVE 持久義務。既有 legacy P11 的 prepare／PENDING／ARM／NO_RELEASE／UNKNOWN、時鐘與 readback 照原規範。

[P6 bridge](../../src/main/java/com/yo1no/gramarye/magic/runtime/mana/P6RuntimeExecutionBridge.java#L31) · [Mana owner](../../src/main/java/com/yo1no/gramarye/magic/runtime/mana/ManaTransactionService.java#L6) · [P9 transfer／ARM](../../src/main/java/com/yo1no/gramarye/SkillRuntimeService.java#L552) · [P11 §11](19_P11持久化與多人修正案.md#L497)

## 6. Native facts、event 與有序提交（V-06）

| V0 事件／觀測 | 成立條件及可執行內容 |
|---|---|
| `on_create` | P6 確認 native spawn 已 apply，且 exact carrier custody 已移交／OPEN 後，P5 方可入列一次。Backend spawn 是 closed creation step。V0 以空 stack 驗派發；創建事件無額外 world effect、child 或第二 carrier。 |
| `on_hit` | 自然 native collision 已通過 impact cancel 及 applicable native 材料，且 exact live carrier、loaded target、dimension、同 owner self exclusion、期限及單次 P5 claim 均成立，P5 方可入列。Claim 證接觸，傷害另依 P6／native commit 結果。V0 authored world effect 限一次對該 target 的受限 damage；root 可在該 statement 成功後排入一個符合 §7 的 child。 |
| `on_destroy` | 限合法 custody 下的自然 hit 結束、block、range 或 age terminal 的一次工程 scalar observation。無 gameplay handler、child、P8 offer 或新 presentation vocabulary。卸載、撤銷、reload、停止與故障只走各自 cleanup terminal，無補發。 |

P5 依既有 owner phase 接收真 native facts，主要順序為 due runtime tick、owner 分配的單調 event sequence，並保 instance sequence、parent event／statement／child ordinal 的穩定座標。各事件保具名 source，禁止用 map iteration、wall clock 或事後 native 掃描重建順序。新 event 在 producer call stack 退出後的合法 drain 執行。必需的已受理自然 hit 依原資格與期限安排；禁止為壓力、logout、診斷或除錯延遲本可按時完成的命中。入場須證已有 drain／hit／cleanup 預留。V0 開始 stack 前要有本次有界 stack 的處理配額，進入後不將半段 stack 藏到下一 tick。

Handler 的 statement 須按作者順序。每個 effect 走 P6 Request → Resolve → CommitPlan → ordered Commit，保持原生 cancel／保護／真結果與 call-scoped guard。首個拒絕或失敗停止該 stack；已確認 applied prefix 保留，後段明記未執行。具名區分拒絕、確證未 apply、已 apply 後失敗及結果未明。Native world effect 無 rollback／replay；P6 只在既有補償條件下處理付款。每個 effect request 具 P5 分配的受限 request identity，綁原 event 與 exact statement。V 的最小 typed terminal 回執限本次 P5 決定停續及具名結果；不向上層暴露 P6 rich result、account、Mana receipt、trace、actor、world 或 throwable 的長期副本。Legacy 介面仍依 §9。故障保原 throwable／Error 邊界，清理先於可選診斷。

P8 僅收經 P6／native handoff 確認的 applied spawn/damage，沿既有 `CAST_RELEASE`／`HIT`、snapshot、profile fallback、recipient/budget 和 wire shape。Constructor、contact claim、false native result、無主副作用和清理本身均不產 applied presentation。Presentation 不可用、drop、缺 asset 或 delivery 故障不改 gameplay、reward、Mana 或冷卻。現行 P8 入口與 P9 native handoff 仍是 legacy-typed，V 必須證成 §9 的窄接合。

[P5 事件排序](../../src/main/java/com/yo1no/gramarye/SkillRuntimeService.java#L3054) · [P6 ordered engine](../../src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionEngine.java#L205) · [P8 applied offer](../../src/main/java/com/yo1no/gramarye/P8ServerPresentationService.java#L148)

## 7. CreateSpell、因果上限與 backpressure（V-07、V-08）

1. **Exact child。** `CreateSpell` 只能取父代 captured exact closure 的目標及當代可信 facts。Child 繼承原 lineage、exact capture、cause、payer、歸屬、deadline 和 bounded allowance，由 P5 queue 延後派發。P5 必須在 child publication 前驗 exact target、input binder、loaded geometry、原 actor 資格及 remaining budgets，分配自己的 instance identity。不得查 latest、呼叫外部 root admission、重新計期限或同步遞迴。
2. **V0 資格上限。** Root depth 為零，每 lineage 至多接受一個 child，child depth 為一。完成或取消後不返還此 lineage 的已使用 lifetime child 額度。已接受的 child 仍須符合自己的 materialization 和 native guard。上限只屬 V0-PROJECTILE 的 qualification profile；Spell-only 語言與其他已採 backend 的將來 runtime 上限須由適用契約決定。V0 不接受登出後新 child；登出前已受理 child 依 §4 判定。
3. **Feedback。** C 的 reference SCC 與 causal/event feedback 分開判定。現行 `RUNTIME_POLICY_REQUIRED` 拒絕保持。V0 runtime quota 不授權 compiler 已拒的因果迴圈。Trusted event descriptor 應反映實際 native producer 及 causal metadata；未來 active destroy handler 或新 emission 需另證。
4. **容量與 overload。** 所有 root admission、child publication、event、instructions、native effects、carrier、pins／program retained 與 cleanup 分別計帳。依唯一 config owner 設 server、owner、lineage 的有界配額和相應共享容量；先保已受理責任。Root capacity full 在首副作用前具名拒絕，釋 provisional 資源。未受理的 child 或後段 effect 若滿額／過期，以具名 statement terminal 停止該 stack；已接納 hit、其他工作及已 apply prefix 保留。確證 native failure 與未知結果依 §6，局部 fence、不傷健康玩家、原生非 V 工作或舊 P11 duty。
5. **限額採用。** 各數值須在實作前的 V0 工程契約中以可核對的表示、最大同存量與實際 workload 推導。至少含 active instances/lineages、每 tick 和 lifetime events/steps/attempts、depth/fan-out、carrier 與 native collision/recipient work、deadline、pin／retained bytes、cold readiness、cleanup、P11 shared duties。V0 一個 instance 至多一個 carrier、一次有效 hit。Loaded age／range、tick／heap／GC／I/O 邊界以 V0 證據定稿，不複製 C、legacy P5/P9 或 P11 T 的數值。平台候選數若無證據，明列限制；不得以截斷合法命中候選換取綠燈。

[C feedback](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDFeedback.java#L23) · [P9 數值與碰撞邊界](../architecture/P5-A-server-runtime-event-kernel.md#L24212) · [P11 C6](19_P11持久化與多人修正案.md#L428)

## 8. Exact pins、reclaim 與衍生資料（V-10）

Catalog sole owner 須為每條接受的 lineage 核發可釋放的 exact lease，包含執行閉包全部 revision，以及 Manifest 入口所需的該 exact Manifest revision。Live instances、已受理 queued/claimed work 及仍依賴 exact binding 的 bounded handoff 共享這份 lease。最後一個相依 consumer 真終態後，由同 catalog owner 依 exact receipt 一次釋放。單一 instance terminal 不會提前釋放其他仍活著的子工作。Pin 釋放與當前 catalog head 變動無關，詳 §4。

取得、重驗、接受、釋放及 reclaim 須受同 source/writer/generation 證據約束。Reclaim 只在核對當前 routes、pending/recovery、必要 external roots 與完整 live exact pins 後進行。無完整證據時停止相依 sweep。舊 close／fault／晚 callback 只能收束其原 lease，不可減記新世代或不同 instance。M1 `captureRoots` 是現有 durable external-root inventory 接點，V 需另證 transient、可釋放的資源接合，並維持同一 catalog truth。

Program 為 immutable derived value。V0 可僅持活躍 lineage 所需 program 及有界工作暫存，跨 lineage 長期 cache 延後。未來 memoization 需另計 bytes、工作與 eviction，hit 仍重驗資格、source／generation 和 context；program eviction、carrier remove、P8 delivery、native reward/write 與各自 runtime/lease 終態分開。任何 V runtime work、pin/lease、carrier、program 或 terminal 記錄均不得持久化、跨 restart 復活或寫入 legacy Store。

[Scope D §§5.3、6](20_DP2_ScopeD_SpellDefinition與可選SkillManifest修正案.md#L76) · [M1 physical contract](../architecture/M1-data-and-physical-contract.md) · [現行 roots inventory](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCatalog.java#L187)

## 9. 限定替換、保留與直接消費者登錄

本節所列 after-set 限 Scope V／V0-PROJECTILE 的工程路徑。`REPLACED_IN_SCOPE` 僅取代該列指明的適用前提；`EXTENDED_IN_SCOPE` 在既有唯一 owner 下增列新 family 的責任；`PRESERVED_UNCHANGED` 的條款依原適用範圍持續有效。本修正案的文件權威隨 canonical 發布成立。若具體接合須變更此表以外的權威或公開 surface，須先提出相依缺口與精確差異。本文未給予實作授權。 [Scope D §11](20_DP2_ScopeD_SpellDefinition與可選SkillManifest修正案.md#L152) · [H6 §69.2](../architecture/P5-A-server-runtime-event-kernel.md#L27628) · [H6 §70](../architecture/P5-A-server-runtime-event-kernel.md#L27661)

| 處置與條款座標 | V0 after-set 與保留邊界 | 直接消費者與接受責任 |
|---|---|---|
| **EXTENDED_IN_SCOPE**　Scope D §§3.4、4、5.3、6、8–11；H6 §70。 | P5 從同一當前 owner catalog 世代取得受信 program 與完整所選 exact closure，在正式受理前取得可釋放 pins。Manifest 先驗全部 entries，所選 closure 供執行，另保 exact Manifest revision 與 entry provenance。M1 的 sole writer、durable publication／recovery、C 的 compatibility proof、M-DUP、未知內容及 `HOOK_ONLY` 規則繼續有效。 | `ScopeDCatalog`／`ScopeDStorage`／`ScopeDResolver`／`ScopeDCompiler`。須直接驗證 source／writer／generation 切換前後的 pin acquire、P5 acceptance、release 與 reclaim；`ScopeDProductionIsolationTest` 定位原隔離責任。 [Scope D §6](20_DP2_ScopeD_SpellDefinition與可選SkillManifest修正案.md#L83) · [Catalog current／roots](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCatalog.java#L186) |
| **REPLACED_IN_SCOPE**　凍結骨架 §§8-B、15、20–22、26–30；總規格 §§9–10；H6 §§47.3–.4、58.26–.27、63.8/.10/.16/.23/.25；19 §4。 | V0 `SpellInstance` 採同 server、loaded-only、memory-only；root 與每個 materialized child 各有 instance，P5 保同一 lineage、queue 與 deadline。V0 重施採 `NEW_INSTANCE`，每 lineage 至多一個 child、深度一，僅限首片。V0 handler 採作者順序、首敗停止與保留已提交前綴；legacy §30 的 Node 規則及原 P5 admission、queue、quota、deadline、pin、L1 與 `SkillReference`／node 對應繼續有效。 | `SkillRuntimeService`、`RuntimeRevisionLease`；`SkillRuntimeAuthenticatedCastIngressTest`、`P5RuntimeStaticGateTest`、`P11AcceptedWorkBoundaryTest`、`P11ExactCleanupTest`。須直接驗證新 family 的 admission、timing、既得短期工作、capacity full、確定性順序、cleanup 及新舊路徑共存。 [凍結骨架 §26](16_骨架定案清單_NeoForge1.21.1_凍結版.md#L1148) · [P5 declaration fence](../architecture/P5-A-server-runtime-event-kernel.md#L1729) · [P5 ingress](../../src/main/java/com/yo1no/gramarye/SkillRuntimeService.java#L159) |
| **REPLACED_IN_SCOPE**　H6 §§57.13、57.16–.19、61.4、63.12/.22 的 invocation／回傳／applied seam，限 V0。 | 既有 legacy adapter 的每 event 最多一 invocation、`requestId == sourceEventId`、`void execute`、正常 `Completed + empty` 及既有 public inventory 原樣適用。V0 由 P5 對每個實際執行的 effect statement／backend release 配發有界、唯一的 request identity，與原 source event 和 statement／release 座標分別綁定。唯一 V capability 接點只交付同一呼叫範圍內足以區分已提交、確證未提交、合法補償或未決的最小 typed outcome；P6 內部完整 result、trace、Mana receipt 與 live objects 留於原 owner。正常結果、未決與原 throwable 分別處理；保持既有 `Error` 傳播及無 retained actor、無 world access、無 queue ownership 規則。 | `P6RuntimeExecutionPortAdapter`、`P6RuntimeExecutionBridge`、`EffectExecutionEngine`、`EffectCommitPlan`、`ManaTransactionService`。`P6S4BoundaryTest`、`EffectCommitPlanTest`、`ManaBoundaryTest` 是直接消費者。須證唯一 capability caller、exact request／event／statement 對應、已提交 prefix、正常及故障分支終態、同 call 補償與原 legacy descriptor／count。 [H6 §57.13](../architecture/P5-A-server-runtime-event-kernel.md#L16771) · [H6 §§57.16–.19](../architecture/P5-A-server-runtime-event-kernel.md#L16847) · [H6 §61.4](../architecture/P5-A-server-runtime-event-kernel.md#L20417) · [H6 §63.22](../architecture/P5-A-server-runtime-event-kernel.md#L24134) · [現行 bridge](../../src/main/java/com/yo1no/gramarye/magic/runtime/mana/P6RuntimeExecutionBridge.java#L31) |
| **EXTENDED_IN_SCOPE**　H6 §§63.12/.14/.16/.20、65.5–.10；19 §§4–6 的真 native handoff。 | V0 只透過 P6 Request→Resolve→CommitPlan→ordered Commit 與具名 native handoff 建立載體／施加傷害。`on_create` 於真 spawn 和 custody 成立後入列，`on_hit` 於未取消的真 collision 與合法 exact claim 成立後入列；`on_destroy` 只記自然終態。維持 exact A、loaded target、自身排除、impact cancellation、不可重播與 non-save custody。既有 P9 的 concrete permit、geometry／hit matcher、factory、renderer、自然命中時序與 `getOwner()` 契約照原條款。 | `P9StarterProjectile`、`P9WorldEffectHandoff`、`P9StarterProjectileRegistration`、`SkillRuntimeService`；`P9S3DirectConsumerContractTest`、`P11AcceptedWorkObservedHitTest` 與既有 L1 online probes。須核對實際 native add／cancel／claim／hurt／remove、A/B 同身分異物件的 self exclusion、晚到 callback 與 pin／carrier 清理。 [P9 handoff](../../src/main/java/com/yo1no/gramarye/P9WorldEffectHandoff.java#L35) · [H6 §65.8](../architecture/P5-A-server-runtime-event-kernel.md#L25502) · [P11 §4](19_P11持久化與多人修正案.md#L60) |
| **PRESERVED_UNCHANGED**　H6 §§58.26–.27、66–67；19 §§7–9；**EXTENDED_IN_SCOPE**　H6 §§61.4/.9、63.20 的內部 presentation 接合。 | 真認證工程入口使用 exact current actor／session／server generation；現有 P7 slot、wire、ACK、sequence、rate 與 D3 仍依原產品路徑。V0 applied projection 須以合法 native spawn／damage facts 交既有 P8 owner，在現有 `CAST_RELEASE`／`HIT` vocabulary、profile/fallback、recipient、budget 與 wire 中表達。工程終態觀測與 P8 offer 各自獨立。 | `P7CastIntentNetworkHandler`、`P7ServerAuthorizationBoundary`、`P7AuthenticatedPlayerCastIngress`、`P8AppliedFactHandoff`、`P8ServerPresentationService`；`P7D3CoreOwnershipTest`、`P8S4PayloadBoundaryTest`。須證過期 session 拒於用前、真已 apply 才呈現，送出失敗與 fallback 對玩法、交易及 reward 無影響。 [P7 exact capture](../../src/main/java/com/yo1no/gramarye/magic/network/P7CastIntentNetworkHandler.java#L33) · [P8 offer 與 identity](../../src/main/java/com/yo1no/gramarye/P8ServerPresentationService.java#L148) · [H6 P8 wire](../architecture/P5-A-server-runtime-event-kernel.md#L20894) |
| **PRESERVED_UNCHANGED**　17 §§3–7、9-A–D；18 §§12、14、16–18；19 §§5–6、10–11；H6 §§63.19、68.12–.14、69.2。**EXTENDED_IN_SCOPE**　僅 V0 工程 Mana、release 與 W 接合。 | 既有 P3/P4 reader、Store／Attachment、journal、readback、P9 starter 與 P10 LKG 依舊。P11 A/B/R、canonical rewards、F1、獨立 writer／dirty 與 legacy cooldown key、schema、PENDING／ARM／NO_RELEASE、durability 和 sync 依舊。工程 V0 的正 root debit 經同一 Mana owner，zero cooldown 限此工程 policy，無新 cooldown key 或持久義務；既有 W／Fop／Qctx 按實際原生責任取得及完成。V0 的 admission、pin、instance、carrier 本身無 C4a blocker 效力。 | `P9StarterSkillContent`、`P10TemplateValidation`、`P11QualifiedSourceOwner`、`P11NativeOperationBoundary`、`P11LiveTransitionService`、`P11CooldownPolicyProjection`、`P11CastCooldownService`；`P11NativeStorageOutcomeTest`、`P11CanonicalAdvancementsTest`、`P11SaveProgressTest`、`P11JournalReadbackDecisionTest`、`P11CooldownPolicyTest`、`P11TransitionControlTest`、`P11ControlBudgetsTest`。須核對已生效 legacy 行為與新 family 的實際 source、reward/write terminal、工程 Mana debit／補償和真零 cooldown。 [P11 §§5–6](19_P11持久化與多人修正案.md#L90) · [P11 §10](19_P11持久化與多人修正案.md#L428) · [P11 §11](19_P11持久化與多人修正案.md#L497) · [P11 W owner](../../src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java#L348) |

### 9.1 接合證據與未決工程事項

1. **跨 package 可見面。** 容許的語意變更限 V0 的閉合、capability-gated 接點。`P6RuntimeExecutionBridge.execute` 的既有公開宣告及型別數、P5 §47.3 既有 production declarations、legacy mapper 和 P8 wire 的適用範圍持續有效。任何實作設計若需要 Java 層面新增可見型別、override 或方法，須列 exact signature、inventory 差異與唯一 caller，經明示採用後才可動工。已採用的 V0 產品結果本身無法核可一份尚無具體形狀的公開 inventory。 [H6 §57.19](../architecture/P5-A-server-runtime-event-kernel.md#L16888) · [H6 §63.22](../architecture/P5-A-server-runtime-event-kernel.md#L24134) · [H6 §65.14](../architecture/P5-A-server-runtime-event-kernel.md#L25698)
2. **Exact source 與 runtime pin。** `ScopeDCatalog.captureRoots` 目前產生合入 durable external roots 的 sweep inventory；`compile` 取 snapshot 後在同步段外編譯。待證前件包括 V0 工程 entry 的實際 authority source 與佈置、catalog sole owner 的 transient pin 落點、P5 接受與該 owner 的 handoff 證明，以及 stale completion／reclaim 的拒絕和 release。完整 live pins 與 pending/recovery roots 必須可見於同一 writer 的 reclaim 判定，source／writer／generation 不可證時停止受影響 sweep。Hot admission／tick 路徑須與現有物理 metadata、讀寫與冷編譯成本隔離。 [Catalog roots／compile](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCatalog.java#L186) · [M1 工程契約](../architecture/M1-data-and-physical-contract.md)
3. **載體與呈現。** 尚待以鎖版平台 source 選定並證明確切 carrier、factory/tracking、server `getOwner`／cause、inert custody、自然碰撞及 non-save 方案。既有 `P9StarterProjectile` 與 `P9WorldEffectHandoff` 要求舊型別配對；共用窄 helper 或獨立封閉型別均須保留舊路徑的行為和時序。工程入口與呈現須能沿現有 server registration／tracking 與 P8 wire、renderer 支援範圍成立。若不能證明，保留 V0 阻擋並具名列缺口，另行裁決任何範圍增加。 [P9 handoff constructor](../../src/main/java/com/yo1no/gramarye/P9WorldEffectHandoff.java#L35) · [P9 載體](../../src/main/java/com/yo1no/gramarye/P9StarterProjectile.java#L105) · [P8 既有 kinds](../../src/main/java/com/yo1no/gramarye/P8ServerPresentationService.java#L306)

### 9.2 採用登錄與身分消費者

本修正案是 Scope V 的正式規範正文，索引、正式規格鏡像及 H6 §71 以窄幅引用登錄。§71 採 EOF strict append，已發布 §§69–70、§47.3／§47.4 Java fence 與完整舊檔前綴逐 byte 保留。`P8S2BoundaryTest.authorityAndRecoveredP7IsolationRemainExactBaseBytes` 保留既有 prefix assertions，並驗證採用前整檔的 prefix 身分與登錄後的 whole-file 身分。`P5RuntimeStaticGateTest` 繼續驗證原 Java fence。正式發布與直接檢查的身分及證據記入採用紀錄，P11、M1、C 的驗收仍限其原證據範圍。 [H6 §69.3](../architecture/P5-A-server-runtime-event-kernel.md#L27647) · [H6 §70](../architecture/P5-A-server-runtime-event-kernel.md#L27661) · [P8 身分測試](../../src/test/java/com/yo1no/gramarye/P8S2BoundaryTest.java#L873) · [P5 fence 驗證](../../src/test/java/com/yo1no/gramarye/P5RuntimeStaticGateTest.java#L364)

## 10. 採用、實作前件與 V0 直接接受

### 10.1 規範採用與實作前件

本次採用涵蓋 V-01～V-10、V-B／V0-PROJECTILE、五項補充限定與 §9 的 replacement／preservation ledger。規範提交、直接檢查、required CI 與 canonical 整合屬文件發布。索引、正式規格鏡像、H6 追加及 identity assertion 同步均限本次登錄範圍，並保留原 prefix、既有資料和歷史驗收。Public Java ABI、新 wire ID、持久 schema number、server config key/value、runtime quota、projectile age/range、Mana fee amount 與 positive cooldown value 仍待另授。

正式文件採用後、另授實作前，V0 工程契約須固定下列接點及數值證據。

- **Catalog 與 P5。** 證 transient pins、同 owner 完整 reclaim inventory、currentness 到 accepted root 的無 stale-window 交接、bounded cold lane 及單一 server owner。C 的 compiler package 與既有 P5 legacy pin/type scope 之間只採可審閱的窄能力；禁止把 publication proof 當 runtime authorization。
- **P6、P9、P8。** 定 V-only terminal 回執的最小欄位、每 statement request identity、單一 caller 與 native guard。選定能經現行 tracking／renderer／wire 表達的載體策略，證真 exact A、self exclusion、單次 collision、no-save、cleanup、原生取消與 applied handoff。若現有接點不足，列 exact authority／API 缺口，停止依賴該接點的 work；擴大協定、renderer 或公開 API 須另行採用。
- **限額、配置與驗收。** 依 §7 推導 age／range／deadline、每 tick 與每 lineage 上限、native cost 和工程 fixture 的 root fee。指認唯一 config owner、生效時點、MAX/MAX+1、足夠的已受理 hit/cleanup 槽及 legacy 共存餘裕。明列受影響的 P11／legacy 直接測試和可沿用證據。已確定的產品語意不得由配額調整暗改。

### 10.2 V0 Definition of Done

| 責任 | 必須直接取得的證據 |
|---|---|
| 真准入與 exact capture | 隔離 dedicated 工程環境以真認證 identity 各走 standalone 與 Manifest。拒絕 wrong/stale actor、session、source/context/generation、無 activation、whole Manifest 不可用及滿額。Catalog 正式提交新 revision 後，舊已受理 root／child 保持原 exact closure，只有新合法 root 取新世代。證 pin 取得失敗無外逸、reclaim race 保護、最後 consumer 終態後一次釋放。 |
| 真 projectile 與 ordered effects | 由 backend 真 spawn，證 `on_create` 確認後入列、自然 impact 單次 claim 後才有 `on_hit`、一次合法 damage、可證一個 depth-one queued child 及拒第 2 個／第 2 層。驗 cancel、false add／hurt、duplicate／late callback、已 apply prefix 後續失敗及未知結果，遵原期限。自然 `on_destroy` 只作具名觀測。確認 P8 只投影 confirmed spawn/damage，缺 asset／drop 無 gameplay 重播。 |
| 工程資源與 terminal | 驗明示 zero／正 root debit、餘額不足、確證無主要 mutation 的同次補償、已 apply／未知結果無臆測退款。驗準備、真 transfer／OPEN 和未完成事實，zero cooldown 無新 key、持久義務或 sync。驗容量滿、child 拒絕與後段停止，既有已受理工作仍可收束。 |
| 生命周期、多人與 P11 | 逐項驗 §4 的真受理與 native custody。至少覆蓋 pre-spawn／飛行／已 claim 登出、原 exact A、B 重連與 self exclusion、獨立真認證玩家並行、未受理或離線新 child 拒絕、已受理 child 的原期限終態、故障／卸載／reload／stop。Carrier/pin、whole R、dirty/readback、Fop/Qctx 分別觀測。直接重驗受影響的 legacy P5–P11 行為；FakePlayer、裸 UUID 與純模型均無法代證真認證／多人。 |
| 界限與封裝 | 用實際鎖版和受控 fixture 檢查 cold/warm、MAX/MAX+1、密集碰撞／呈現、同 tick 壓力、cleanup storm 與 legacy/V 共存。記 workload、配置、耗時、retained bytes/heap 和真結果。普通 JAR 無工程入口／fixture，dedicated 無 client-only linkage。結論限實際工作量及環境。 |

原驗收與接點盤點、來源清單及容量模型保留其歷史證據角色。

本次交付止於 Scope V 1.0 文件限定採用。V0 implementation 與 runtime qualification 均未開始，須另行授權。Element E、正常玩家入口、新 network、editor、renderer 與 P12 均未開始。
