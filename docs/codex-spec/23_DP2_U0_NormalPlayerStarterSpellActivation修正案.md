# DP-2 U0 — Temporary Bootstrap／Qualification Spell 修正案

U0 1.0，2026-11-25（Asia/Taipei）。使用者已採用本文件的暫時性 Bootstrap 定位、C provisioning、0 Mana、20 advancing ticks、generic typed cooldown-key schema 例外及 Sunset。這是同一 U0 整合工作單的正式 scoped authority candidate；文件發布須 direct local checks、candidate required CI、canonical fast-forward及 exact-main required CI 閉合。**本 authority commit 不實作 production Java；implementation／runtime qualification 未開始。** 實際 SHA、tree、CI 與接受結果記於同一累積交付紀錄，不預填將來 PASS。

## 1. 產品決策、範圍與效力

U0 唯一產品目標是普通玩家以正常、可重綁輸入，經真 authenticated intent、server-selected owner-private Spell、M1/C exact closure、正式 Mana/cooldown owner、V0 與 P8，驗證完整 production path。暫時內容不是永久 Starter、已學會／已裝備技能，也不是未來所有自訂 Spell 的免費或20-tick預設政策。

本次使用者採用來源為2026-11-25 Temporary Bootstrap整包決議（9533 bytes，SHA-256 6b9e0de35ec9dbf09590b3da65ea3e03db0b44dbe65f295c5cd0da98392c120d）。先前最小決策包及其 STOP-SURFACE 是歷史施工材料；本次明示 D4 例外只解除 typed cooldown 持久表示的零 schema 限制，不擴大其他產品範圍。

| 已採決策 | 精確範圍 |
|---|---|
| Temporary Bootstrap | 用於驗普通施法完整路徑；未來玩家建立／保存／選定／施放路徑全數驗收後依 Part A Sunset 移除 |
| Provisioning C | 真 server seed catalog的closed-bundle fork配置actor-owner-private copy；M1/C真source、allocator、durable publication/recovery/readback與原ForkProvenance，不加ownership／learn/equip／current-spell schema |
| Normal input | 同一可重綁R，fresh PRESS一次至多一intent、held/REPEAT不自動發；server仍唯一session/sequence/rate權威 |
| Normal wire | Part C的永久generic operation，一個新PLAY C2S、零新normal S2C；無client Spell／actor／cost／target authority |
| Temporary Mana | 0；仍核真Mana source存在／availability／actor-source pairing及原transaction owner，不造正debit／refund或新增補給經濟 |
| Temporary cooldown | 20 advancing server ticks；原P11 prepare/PENDING/真release/ARM/NO_RELEASE；expiry不保證capacity已空，不自動重送 |
| D4 | Part B的通用typed key/schema2；同一P11 Attachment/service/native writer，非U0特判、非fakeSkillId/hashUUID、非schema delta0 |
| Feedback | 原P8 applied success；vanilla safe localized action-bar拒絕，無HUD／screen／slot／wheel／state-mirror／renderer |
| Runtime | 原V0same-server、loaded-only、memory-only／不restartrestore；舊accepted exact closure、因果、L1/P11責任與期限保留 |
| Permanent foundation | normal input/C2S/P7、owner-privatecatalog/Ccompiler/exactclosure、Mana、generictypedCD、V0/P8於Sunset後保留 |

本正文的 Part A／B／C 各自局部編號；各Part內的「§n」指該Part局部章節。Byte公式、wire、content及caller範圍以本文件整合後正文為唯一契約，不要求讀取外部草稿／工作單來補產品值。

### 1.1 Scoped supersession 與保留

- 本文只在 U0 normal input、單一normal intent、server Bootstrap provisioning／selection、normal V0 composition、generic typed cooldown 與精確 Sunset 範圍，取代16／Codex／詳細步驟、H6 §§58/59/63/66/69–73、19 §11、20／21／22中相應未授或較窄條款。Part B/C列逐項after-set。原文歷史bytes不回寫。
- 20號owner-private／immutable／source／publication／compiler／reclaim與closedfork保持。Server seed只供真fork，不能共享runtime或假扮玩家；M1／C完成不代表普通入口已存在。
- 19號原L1／R1／F1／C1–C6、D3 exact C/generation、原legacy policy1／101、SkillId語意與quota、whole-source/nativewriter/clock/Unknown保留；Part B才是typed-key明示擴充。不可把新版讀回／新actor與原不同时點材料整份相等當通用正確性條件。
- 21/22號V0既有55列與其source/artifact角色、K1、exact A、original legacy/engineeringresults保留。新U0ordinary wiring／nativeboundedquery須另外直接驗證，不拿工程topology、FakePlayer、模型或舊PASS充U0認證／ordinary證據。
- 既有public declarations/callers/fourP7payload及ordinary engineering-issuer absence，僅在本文具名normal入口、typedkey、受控catalog/fork與native橋範圍增補。實作逐物件驗before/after declarations/generated/mandatory/callers/placement，不能整包白名單或冒publicpluginAPI。
- 更早H6所有bytes、P7/P8既有phases／world/resource epochs、normaldependencies／primary Error規則及未明列替換條款全保。單一scheduler、唯一Mana/cooldown/persistence owner、不bounded同步遞迴等不變量保持。

## 2. Normal startup、共同接線與採用前件

### 2.1 唯一 startup owner 與 default availability

本次明示替換「ordinary V0Distribution永遠empty」於 U0 的結果：ordinary artifact在正常server slot預設安裝本次Temporary Bootstrap normal activation policy。由同一 P5ServerRuntimeConfig／root composition 在原startup捕獲點產生immutable normal profile，使用Part A凍結的保守default tuple，受現有P5/P11/source/capacity等共同guard限制；不增TOML key、第二config owner、debug/system-property開關或client許可旗標。

既有[v0]十鍵、預設Disabled、operator raw驗證、reload只影響下一工程slot的語意繼續只管原engineering profile。本U0 normal default不以工程 enabled flag或recipe來授權。Profile選定須在現handleRuntimeStarted的舊Ready早退之前，不能因missing[v0]誤將已採normalprofile永遠關閉。

Engineering artifact在真合法recipe存在且原Ready／auth／qualified topology成立時用原工程profile；只有可證recipe真正不存在時才可選shared normalprofile。Recipe存在但malformed／unreadable／source不可信，必fail-closed，不當absence fallback。每server只有同一P5slot、同一V0partition／catalogbinding與一個profile；不得同時啟兩runtime或讓normal入口取得engineeringissuer。

啟動後catalog/source/frontend尚未ready是具名NOT_READY／UNAVAILABLE，不是已可施放。冷工作不block server main；provision完成不重播舊press。Sunset關閉Bootstrap選擇／provision後，genericnormalinfrastructure仍存在；未有另授正式root policy時只拒絕，不fallback Bootstrap或legacy。

### 2.2 唯一 native 註冊與封閉 bridge

Part A的normal boundedquery／position guard／damage-Post是本次明列的ordinary接合，不授改V0既有targets／terrain或newEntityType/renderer。

- Ordinary common position wrapper採既有exact Entity.setPosRaw→Level.getChunk tail guard body及sourceanchor，只有效註冊一次。將原engineering-only純position wrapper角色移到ordinary/shared common Mixin；engineering只保留兩個auth mixins，不再額外註冊相同position anchor。原helper/scopedcapability保持，legacy/client/noncarrier原呼叫一次；engineering原合法V0結果相同。不是把engineering auth／recipe／scenario整包搬ordinary。
- 真LivingDamageEvent.Post observer也由共同root composition唯一註冊；原engineering Scenario取消其重複observer註冊，不能同event兩次observe而製造duplicatePost。現有資料只在matching native frame時有作用；其他damage不被改寫。
- Mixin類可位於已保留的com.yo1no.gramarye.mixin；普通bridge interface必須是root package com.yo1no.gramarye.U0BoundedQueryAccess，不放Mixin保留package造成普通class link失敗。跨native package必要public declarations只供不可外部mint的current call permit／唯一consumer；無public storage getter／locator／plugin registration。
- U0 raw-entity query最多64、section lookup最多8，完整候選後才原生nearest；65或座標／source失證整次拒絕，不回unboundedquery，不取partialnearest。Legacy／工程branch原delegate一次。未證普通world linkage、boundedtraversal與nativefacts前不報runtime PASS。
- 既有air/STONE/no-fluid、exactPig/ServerPlayer、loadedfootprint、cause／owner／cancel等資格保持。minY+64只為原工程已證envelope；不是nativeclientdiscardthreshold，也不因此新增無依據normalworldgate。U0不承諾所有terrain、entity或第三方adapter皆成功；失證context具名拒絕。

### 2.3 Input 工程界限與 accepted-work 責任

Part C fresh-edge counter採具體上限8（同一current P7 per-player每tick eval budget）；第9個鎖overlimit，不回繞。每Post tick最多32次consumeClick；先有界drain後才決定整批send。若overlimit、32次都true而尚未觀察empty、或任何gate失敗，整回合0send／0sequence並flush；flush可跨tick有界drain，期間新edge亦drop。必真看到false並重驗gate才清flush，後續新的PRESS才能送。Exact同frame兩組短按在bound內保留；不靠單isDown快照、不globalreset、不保舊press待施放。

0 Mana的availability／actor-source前置在新admission實證。已接受work的後續ARM／NO_RELEASE則沿原P11 cooldownWorkRecipient、W／whole R及canonicalmaterial責任；不得把新admission的online/roster guard或較窄paymentSource搬到合法detached A收束，亦不得讓new B取得A的新debit／因果權。Sunset只封新義務，原accepted PENDING可依法ARM／NO_RELEASE，不能清Unknown假稱已終態。

## 3. 實作、驗收與發布順序

本authority commit只含完整23號正文、必要AGENTS/三mirror、strictEOF H6 §74及原identity consumer；不含production Java/resources/Gradle/workflow/Mixin。保存e24 whole H6作exactprefix，所有更早prefix／P7隔離assertions與test method ID保持，23／newwhole身份在正文完成後實算，不能runtime自算expected。

Direct local checks、source-derived完整JUnit coordinates、必要compile/unit及warning歸屬成立後，發布authority candidate。Candidate required CI全過→fresh live-main／policy→ordinary FF→exact-main required CI全過→canonical fetch／ff-only／0:0，才開始本U0 production implementation。同一整合工作單直接接續，不再為input/network/Mana/CD/feedback各開產品提案。

Phase2按content/provenance/catalog、genericinput/P7、同ownerMana/CD/admission、native/P8、feedback/lifecycle/qualification接合；可用合理小commits，保所有raw/result/failure/elapsed與source/tool versions，有原因只重驗相依範圍。FinalU0需全部30個具名rows、ordinary正常authenticated A/B、native/lifecycle/save/reconnect/catalogupdate、P11/V0preservation、fullsource-boundlocalregression、candidate/main六個jobs與cleantracked canonical0:0／原logs保留。

Hard Stops：若需要完整playerSpellownership或learn/equip/selection、第二normal新payload／normalS2C、新Attachment／第二CDstore、publicpluginAPI、新EntityType／renderer、Element或改舊V0/P11玩家結果，停止相依工作並列exactgap。一般compile/reader/fixture/launcher故障在原工作項保留首次資料、修正直接工具與受影響結果；不新增metaqualification流程或放寬接受。完成U0後停止，不開始U1／Element／Editor／Renderer／P12。

## 4. 正文分部

以下Part A/B/C在各自明列的範圍共同構成唯一U0契約；Part A的native橋與Part B資料schema是不同接合，D4本身不新增platformhook。Part A指定normal內容／provenance/Sunset，Part B指定通用CDschema與release，Part C指定genericinput／wire／共同P7與30列接受。各分部的key、界限、位置與註冊規約均已同步，不依靠隱含優先順序解釋互斥條件。

---

## A. Bootstrap內容、真provenance、ordinary native接合與Sunset

### A.1 固定 logical identity 與內容邊界

| 座標 | 本次凍結值／角色 |
|---|---|
| Temporary logical content policy | `gramarye:u0_bootstrap_qualification`，content policy version `1`。這是 server policy 名稱，不冒充 SpellDefinitionId，也不是新增 serialized Spell 欄位。 |
| 正常 activation | `gramarye:normal_cast`，descriptor version `1`，無 authored arguments；由本輪 U0 明示新增 trusted contract／binder。這個 normal infrastructure 可供未來正式 Spell 使用，Sunset 不移除它。 |
| Server seed owner | 既有 `SkillOwnerId` principal，值 `10c92d08-46d6-3e19-87ba-3fe5afabf5d7`：等於 Java `UUID.nameUUIDFromBytes(UTF-8("gramarye:u0/bootstrap-seed-owner/v1"))`。它只標識本 server 的 source catalog，不是 authenticated player、legacy SkillId 或新 owner family。 |
| Player owner | 當前真 authenticated PLAY actor 的既有 `SkillOwnerId(actor.getUUID())`；不得由 payload 提供。若恰等於 reserved seed principal，U0 fail-closed，不豁免同 owner fork／runtime owner guards。 |
| 實際 source／definition ID／revision／generation | 由各自 M1 storage 與 typed allocator 的**真值**提供，不固定為 257、revision1 或 generation1；sourceId 不由 logical 名稱或 UUID hash 偽造。 |
| Mana／cooldown | temporary production cost **0 Mana**；cooldown **20 advancing server ticks**，logical policy key `gramarye:u0_bootstrap_qualification`，同一 policy 下所有 revision 共用。費率、typed cooldown persistence 與 PENDING→true release→ARM／NO_RELEASE 依同輪 D4 契約；不繼承 engineering zero-fee／zero-CD。 |
| 玩家語意 | 非 learned／equipped，不建 known-spell list、current-spell、selection、多槽、交易／分享／editor 功能。Normal C2S 只表達固定操作意圖；client 不提交任一 catalog／content authority。 |

Seed principal 必須與 runtime 資格分離：`SkillRuntimeService.reserveV0Capture` 仍要求 owner UUID 等於 exact actor UUID；任何 seed Selection、seed program 或 cross-owner root 都拒絕。伺服器知道 seed principal，不代表能替它捏造玩家或繞過 P7/P11。

### A.2 Ordinary production body 與 trusted frontend

使用既有 `SpellDefinition(SpellReference, SpellBody, Optional<ForkProvenance>)`／Scope D formal schema1。以下 Known／Absent 及有序容器均有語意；沒有 Unknown、Missing、RAW、Phenomenon runtime 或 unsupported setting。

| Body field／domain | 固定 typed 形狀 |
|---|---|
| backend | Known `Backend(Descriptor("gramarye:v0_projectile",1,[]), "gramarye:projectile", ["gramarye:v0_projectile"])`。 |
| activation | Known `Descriptor("gramarye:normal_cast",1,[])`；不沿用 `gramarye:v0_engineering`。 |
| settings | Known 空 ordered list。age、range、speed、Mana、CD **不**偽裝成未被 frontend 支援的 authored settings。 |
| handlers | Known ordered list，只有下列兩項，stable HandlerIds 分別 1、2。 |
| handler1 | `gramarye:on_create` version1，empty ordered block stack。只有真 release／spawn 後原事件路徑，不另做 world effect。 |
| handler2 | `gramarye:on_hit` version1，一個 `EffectBlock(BlockId(1), Descriptor("gramarye:v0_hit_damage",1,[Argument("damage_hp",IntegerValue(2))]))`。一次合法 claim 最多一次 2 HP requested native damage，不保證 armor／absorption 後一定減血2或一定命中。 |
| appearance | Known `gramarye:v0_profile_triple` version1；arguments 依表內順序為 `sound_profile=TextValue("gramarye:default_sound")`、`particle_profile=TextValue("gramarye:default_particle")`、`trail_profile=TextValue("gramarye:default_trail")`。沿原 P8 builtin data、budget、delivery／fallback；cosmetic failure 不改 gameplay。 |
| phenomenon | Absent。 |
| dependency／child／Manifest | 無 `CreateSpell`、無 child、無 Manifest、無 entry ID；selected closure 恰一個 root。 |
| on_destroy | **沒有 authored handler／block**。沿 V0 原 natural terminal observation；不得新增 effect、reward、child、payment 或 replay。現 qualifier 只支持 create／hit handler 形狀，不能為了「觀測」加第三個 author event。 |

Trusted frontend 仍為 immutable `ScopeDFrontend.Snapshot/Context`，不註冊任意 descriptor callback：

- Backend `gramarye:v0_projectile@1`：category／capability 如上，空 backend arguments／settings；creation input 恰 `launch_step_milli: INTEGER required`，events 為 on_create／on_hit。
- 新 normal activation `gramarye:normal_cast@1`：空 arguments，同 category／capability，提供同名 INTEGER creation input。值由 server binder 固定，不讀 client 數字。
- on_create@1：CREATION origin、無 facts／child contract。on_hit@1：EXTERNAL origin，沿既有 launch fact typing；bootstrap body 不用其 child capability。保全 C emission／feedback／signature checks。
- `gramarye:v0_hit_damage@1` 沿原 descriptor operation／typed `damage_hp`；本 body 明寫2，不依 default 推斷費率。
- `gramarye:v0_profile_triple@1` 僅有上述三個 required TEXT operands；不是新 renderer／normal S2C。
- 每個 source／candidate 的 context fingerprint 由正式 frontend／policy canonical identity 產生；normal snapshot 與工程 snapshot 不同，不共用 proof、issuer 或 fixture context。

#### A.2.1 Runtime scalars（不屬於 Spell serialized body）

本 U0 最小 normal default tuple 沿既有 V0 default 容量及 K1：

`(maxLineagesPerAttribution=1, maxLineagesPerServer=2, rootAttemptsPerTick=1, maxManifestEntries=4, projectileMaxAgeTicks=16, projectileMaxRangeBlocks=16, lineageDeadlineTicks=24, presentationRecipientsPerOffer=8, positiveRootFee=1)`。

`maxManifestEntries=4` 是既有合法 startup record 座標，**不**給 U0 Manifest 路徑；U0 policy 不建立／選取 Manifest。`positiveRootFee=1` 保留既有 config 的正值型別／驗證，不將它改成0；本內容的 zero Mana policy 必須由正式 U0 transaction owner 明確接合，且仍驗 source／availability／pairing，不能依工程 Selection whitelist 偷渡。

`launch_step_milli=1000`，最大初速1 block／tick，之後既有 wire-grid quantization、noGravity、native dry drag `(double)0.99F`。選1000是現 `V0RuntimeCatalog.ProjectionCheck` 已固定的合法 launch 值及既有 K1 直接支援值；本稿未新增較慢 scalar／未驗 binder。16 ticks／16 blocks 是上界，range、age、原 absolute deadline、block／unload／owner失效可更早終止，不延命、不 partial step。若同一P5/P11共同guard給出更小有效界，依原owner拒絕／收束，不能為內容覆寫 operator config 或無限提高限額；工程 hard tuple及其產品結果不動。

### A.3 M1 source、roster、writer 與 normal lifecycle

本輪 ordinary composition 必須新接合 production source/lifecycle；現 ordinary `V0Distribution.open` 的 empty leaf 及工程 only fixture owner 都不能當既有完成品。

- Production catalog root 固定於當前 server/world-owned data 下 `gramarye/spell_catalog`，由唯一正常 composition 建立／辨識／關閉；在這個**同一** `ScopeDSource` roster 裡，seed 與各 player 各佔一個既有 owner scope。不得另開多個 root 規避 32-owner bound。
- Seed 明計1/32；因此 seed存在時最多另有31個已 admitted owner scopes。這不是31個同時在線／accepted roots，也不是保證31個玩家都受理；P11、V0 X/L、物理／byte bounds照原值獨立限制。超額具名 unavailable／capacity，不刪舊 owner「騰位」、不改 UUID、不退還 admitted watermark。若未來要提高容量，須另按原 limits owner正式推導，這裡不授。
- 沿 `ScopeDSource.open(Path, SkillOwnerId)`：default filesystem／qualified APFS/ext4、stable directory identity、`.owner-admission.lock`、UUID directory與 `.admitted` exact roster、每 owner `writer.lock`。已知 source／roster／current／allocation material 缺失、不相符或失鎖，不當作新玩家空 catalog。
- Owner catalog 唯一 current image、sole writer、allocation watermark、draft image仍各由既有 M1 owner管理；不複製到 playerdata Attachment，不以 SavedData dirty／async save ACK 當 durable publication。
- 所有 open/read/compile/reserve/fork/publication/recovery/sweep 在同一 bounded cold owner lane；P7 main intent只取得 server policy選取與原有界 provisional preparation，不做檔案 IO／C編譯。維持原 mailbox／deadline／slot capability、stop封口和 late completion隔離；不得加第二 scheduler或 actor/world callback給worker。
- 登入／首次合法 intent 可要求 provisioning。尚未 ready 回 NOT_READY／安全action-bar；完成 catalog provisioning不代表 replay該 intent或自動施放。之後須新的 fresh press與原 P7 sequence/session驗證。
- 初始 root directory或owner empty 必須是正常 lifecycle可證的合法 absence／genesis；不可對 malformed、unknown、已知 committed missing、pending材料做 reset／repair-to-empty。Seed與player writer都正常 close；不可先close仍有live pins的writer以求釋鎖。

### A.4 真 seed publication → closed fork → player publication

#### A.4.1 Seed（只作資料 source）

1. Seed owner被server policy保留，無 normal cast、command、editor、payload、learn/equip或root Selection入口。非 authenticated actor scope，不建立其Mana/CD或V0instance。
2. 唯一 cold writer先 open／recover並 readback該 seed catalog。只接受合法空 genesis／無 unresolved pending且無其他既有內容，或精確相符的既有 singleton seed。已有 unknown／draft／foreign／multiple／不相符材料一律保留並 fail-closed，不合併／刪除。
3. 空狀態以 `ScopeDCatalog.reserve()` 耐久燒 serial，再 `Reservation.newSpell(1)`。Seed formal body就是§2，初始 origin的 `Optional<ForkProvenance>` 為 empty：**不得捏造「從自己fork」**，現 `checkProvenance`會拒同owner來源。系統來源由 reserved seed source／owner、受控publication、known body與actual source incarnation共同證成。
4. 經 `prepare` → `verifyCompatibility` 的真正 C proof → `publish/publishRuntime` → exact forced readback。取得實際 `seedStorage.sourceId`、seed exact `SpellReference`、seed committed `CatalogGeneration`、正式 encoded-document SHA256、frontend fingerprint。未有 durable readback不得 export。
5. U0 content policy1 不自動改 seed body／revision。已有相符 seed只是讀回重用，不重發 revision／burn serial。缺來源或 fingerprint不符，不自動換新 seed以接續舊玩家copies。

#### A.4.2 Player copy（同一 data schema）

1. 先有當前 P7 exact native PLAY／P11 pairing，owner取actual actor。Seed owner不可當receiver。Player catalog先 recovery／readback；合法空、無pending／other material才可新 provision。
2. Seed owner用 `ScopeDCatalog.exportFork(context, seedExactRef)` 真取同 source/owner/generation exact closure；單 root／no raw使bundle正好一份 Spell。傳遞的是既有 bounded immutable closed bundle，不是分享／交易協定或 client資料。
3. Receiver `reserve()` 後用 `prepareFork(reservation, normalContext, bundle)`。內部按 receiver reservation配置typed identity／revision，一對一remap。**只讓 `ScopeDFork.importBundle` 真寫 provenance**：`ForkProvenance(seedSourceId, seedExactRef, seedGeneration)`；不在外部造 fake source/reference/generation，也不塞RawEnvelope marker。
4. Receiver candidate必須再經同一次完整 ScopeD／C compatibility proof、coordinated publication、force/readback；不能以seed編譯成功代替receiver proof。Readback owner／route／revision／body／true provenance全部相符才ready。
5. 兩個catalog不是跨owner原子transaction。Seed先durable且不變，receiver各自有唯一current commit決定。Receiver失敗保留其舊current或matching recovery material；seed不回滾，不以全server「已發一份」flag猜玩家成功。
6. Actual runtime selection只指receiver-owned route；compile/capture取其current source／generation／exact closure。Seed及其writer不進lease closure；已有player copy不因seed仍在記憶體就有執行權。

**源碼可行性，不是已完成呼叫接合：** `exportFork`／`prepareFork`均為現成 package-internal API；現 `V0RuntimeCatalog.write` 只接 engineering Material或sweep，並未替normal expose fork。新 U0 ordinary cold composition／sole writer須窄幅增加內部 seed/fork publication路徑，沿同 gate、`WriterMutation`、proof/readback與pins保護，不可旁開第二writer。精確Java declarations／unique callers在本輪23號amendment inventory逐項採用；本稿不把package-private機械可用性當已授權普通入口／public plugin API。

### A.5 Idempotence、immutable fingerprints 與碰撞

只有以下幾種具名結果；失敗不觸碰玩家其他材料：

| 實際狀態 | 允許動作 |
|---|---|
| 真空genesis／無pending且來源完整 | 一次真reservation→fork→C publication→readback。 |
| 精確singleton bootstrap current | 證同owner、真seed source/ref/generation、固定body及actual canonical hash；read-only重用。不另配置或upgrade。 |
| Matching pending／ACK遺失 | 重開原source／writer，revalidate同一prepared並重新取得C proof，依M1 current決定恢復同代；不重發batch／重配置identity。 |
| Source/current已durable但RAM未知 | 原source readback後恢復；不根據mtime、最大revision或path存在猜。 |
| Unknown/raw／foreign／multiple roots／不同body／provenance／context／roster／seed來源失證 | unavailable／quarantine，保留原Spell、Draft、Manifest、raw與Unknown；不覆寫、合併、刪除或猜「latest」；不回空。 |
| 真容量／allocation overflow | bounded拒絕；燒掉的serial保留，checked arithmetic，不重用。 |

`SpellDefinition`現在只有既有 `Optional<ForkProvenance>`，**沒有 system-generated boolean／origin欄位**。本方案不新增它：

- Seed provenance witness＝真 M1 source incarnation＋reserved seed principal＋committed exact seed ref/generation＋§2完整typed body＋正式context。
- Player witness＝真fork寫入的三欄provenance＋receiver own exact reference＋同一固定body＋唯一受控current route。
- 實際 immutable fingerprint沿原 C 的 `SHA256(ScopeDDataCodec.encode(formalDocument))`（`ScopeDCompiler:103–107`）。它涵蓋owner/ref、body及provenance，不是「只hash body」的錯誤稱法。Seed與player full hashes因真配置身分不同，**不要求相等**。
- Body精確性以§2的typed record／ordered lists結構完整比較；驗證expected receiver時採其actual allocated ref與已證seed provenance，再以同一existingcodec得到expected canonical digest。這是驗證，不用造出的expected document取代真forkpublication。
- Phase1沒有actual seed／playercatalog，故此處不捏造未來hash常數。Implementation qualification須將真source/ref/generation/context/canonical hashes與本固定body政策關聯存既有證據；provenance欄位與current image本身才是durable資料，外部報告／旁檔／log／可任填system marker不是權威。

Player copy的seed principal／logical policy／真fork provenance與exact內容匹配均不可缺；seed origin自身則依§4.1真publication witness判定，不要求或偽造self-fork provenance。僅名字、damage2、appearance相同、ID257或某個UUID相同都不足。

### A.6 普通 runtime／native composition 的明示採用差額

本輪正式U0採用範圍須同時覆蓋下列窄接合，而不是宣稱「開key即可」：

1. Ordinary `V0Distribution.open`從固定empty改為normal composition；正常server source、boundedcold owner、normal activation/trusted frontend、seed→fork及server-policy receiver route。保原P5/P11起動precedence、slot token、current actor/session/reload gates、provisional/accepted lease與stop/fencing。
2. `V0RuntimeCatalog.ProjectionCheck.projectRoot`現只認`gramarye:v0_engineering@1`；新增normal profile只容許§2 body／normal activation及同原program/refusal/charge bounds。Engineering profile與其原語意不變，不把fixture issuer／hardcoded257變普通source。
3. Normalexactactor source、0Mana前置、typedCD20與PENDING/true release/ARM owner都用同一已採正式責任，不將`positiveRootFee`改0，不沿工程zero-selection bypass availability。
4. 真 `LivingDamageEvent.Post`接到既有 `P9V0WorldEffectHandoff.observeV0DamagePost(Post)`，保持exactnativeDamage identity、target、server main、matching/duplicate/finite/positive檢查。移為ordinary/shared共同root唯一受控registration，取消alternate Scenario的重複observer；未有matchingPost不宣稱HIT applied，不能以hurt boolean／自造receipt補P8。
5. Ordinary/shared需唯一窄native post-position guard（移植純guard角色，engineering不重複註冊）：沿現`P11P9TrackingCleanup.guardV0PositionChunk(EntityAccess,Level,int,int)`，exact `Entity.setPosRaw(DDD)V → Level.getChunk(II)LevelChunk` invoke wrapper，require/expect/allow=1。Legacy/client/noncarrier原call一次；qualifiedserverV0回true才跳過未使用的tail return，真witness缺失沿CHUNK_UNLOADED收束。不是全域禁chunkload，也不搬整包engineering auth/scenario mixins。
6. 對U0新增§7 bounded normal query；必須以captured normal policy識別，**engineering與legacy維持原nativedelegate**。不新增EntityType／renderer／clientmotionmode，P8原applied-only／fallback責任保留。

這些為新U0普通production接合，不撤回既有工程V0證據。`[v0]`missing／false目前Disabled仍是source fact；正常default／config availability依本正文§2.1明示採用；既有Disabled只管工程profile，normalprofile選擇在舊Ready早退前，不將未ready source誤報可施放。

### A.7 Normal loaded-world query：具體安全接合，不擴targets／terrain

#### A.7.1 保留的eligible context

Temporary定位不授擴大原V0 targets或terrain：`P9V0LoadedBlocks:87–89`只有air／STONE且無fluid；exacttarget仍為`Pig.class`／`ServerPlayer.class`並驗alive／exactlevel／index／unmounted／loadedfootprint，self exclusion／impact cancellation照原順序。水、其他block、未知target／listener source不能靠「普通世界」默認成功。Loaded-witness缺失不強載、不建ticket、不用air代替、不等待重掃。

原工程native-query density證據只適用其封閉topology。Positionguard單獨移植不能證任意loadedworld traversal有界。原no-local-discard K1 envelope亦限其已證dry/minY+64場景；本稿不授額外client discard hook或承諾任意voidclient lifetime。NormalU0接受須真證其supportedcontext及server-authoritative終態，不把舊工程p99／no-ticket數字攤成全世界保證。

#### A.7.2 鎖版可用路徑及兩個不充分的shortcut

鎖定NeoForge21.1.241 source容器 `build/moddev/artifacts/neoforge-21.1.241-sources.jar`＝9,393,659B，SHA256 `fea5b1405f082e64d5fbab2a1143766fac1f94d80e1f79374d8c428bbe5fe437`。

- `ServerLevel.getEntities(): LevelEntityGetter<Entity>` 是public（1646–1648），回同一`entityManager.getEntityGetter()`；normalQuery只接受真exact已鎖`LevelEntityGetterAdapter`，不自行new storage／scan global UUID。
- `LevelEntityGetterAdapter.sectionStorage`是private final（11）；其public typed `get(EntityTypeTest,AABB,AbortableIterationConsumer)`（46–48）有abort能力，但 `EntitySection.getEntities`（30–35／47–51）在`intersects`／tryCast之後才呼consumer。只數callback會漏所有被掃但未命中的rawentries。
- `EntitySectionStorage.forEachAccessibleNonEmptySection`（35–63）按X的`sectionIds.subSet`迭代，再濾Y/Z；只用原API的ABORT也不能界定callback前已掃其他Y/Z sections。故不以這兩者冒充full nativevisitcap。
- 現成 `EntitySectionStorage.getSection(long)`（97–99）只查existingmap，不create／load；`EntitySection.getEntities()`（62–64）可lazy逐筆遍歷storage，不先`getAllInstances` copy。這允許窄boundedrawenumeration。

#### A.7.3 本輪採用的U0-only bounded query

1. 每次真U0 movement/nativequery由原exactslot／actor／carrier／server-main／level／same tick／p0-v0/queryAABB／normal-profile witness開一個不可任意mint的call-scopedpermit。不可從UUID、payload、公開constructor或一般worldservice取得；最多一個query in-flight／一次consumption，finally清除。
2. 按locked `EntitySectionStorage`同一座標公式：X/Z＝floorSection(query.min−2)到floorSection(query.max+2)，Y＝floorSection(query.minY−4)到floorSection(query.maxY)。原carrier0.25、step≤1、inflate1令每axis span<16，故每軸最多2sections、**最多8個getSection lookup**。先驗finite／bounds／checked coordinate及真AABB關係；不合格整次拒絕，不clip邊界以湊8。
3. 有限≤8個`SectionPos.asLong(x,y,z)`按native long key順序（同X外圈／AVL排序）排序，只用`getSection`取既有同storagesections；沿native相同`!empty && status.isAccessible()`。每section沿其原storage迭代順序，**所有rawentries在任何AABB／type／self／target filter前計數**。
4. **MAX=64 rawentries／query；看到第65筆立即整次拒絕**，丟棄完整query候選／nearest結果，不發hit、不推進該段／不保partialnearest、不重掃／不等capacity、不排程retry。這是U0 call-work budget，不是新全域worlddensity上限、不刪實體、不禁止玩家在該世界存在。
5. 完整≤64時才把符合原AABB／exceptself／**同一原predicate object**的ordered候選交原`ProjectileUtil.getEntityHitResult(Level,Entity,Vec3,Vec3,AABB,Predicate,float)`；由同原生程式做0.3F inflate／clip／strict`d1<d0`nearest，保持equal-distance先遇者及terrain endpoint ordering。不重寫raycast／改margin／近似nearest。
6. 鎖定`Level.getEntities`（880–884）另掃全部multipart parts；`PartEntity`不可能是exactPig或ServerPlayer，原predicate對全部必false。U0-only可不走這個globalparts掃描，前件是predicate未擴大、原targetfilter純讀並完整保留；不能泛化到arbitrarypredicate或新的target類。
7. 只有permit綁定的U0查詢使用此候選源。Engineering／legacy或非本次invoke精確匹配者仍原call一次。CurrentU0 source失證／未知adapter／malformedcoordinates／MAX+1必typed unavailable，**不能退回unboundednativequery**。
8. U0-query refusal須在nativewrapper把空候選當作查詢失敗狀態保存於本callpermit；外層U0handoff先核status再用nativeHitResult。不得把失敗的emptylist當MISS而繼續movement或扣款／P8。Query限額／source失證映射U0內部具名temporary-unavailable及既有carrier `OWNER_INVALIDATED`收束；loadedwitness失證仍為`CHUNK_UNLOADED`。不改工程／legacyterminal vocabulary，不新造成功receipt。

#### A.7.4 新native anchor／closed bridge清單

不公開mutable `sectionStorage` getter／locator，也不提供一般plugin查worldAPI。以下是本方案的限定ABI／唯一consumer，不表示這些Java類已存在：

- 新internal `com.yo1no.gramarye.U0BoundedEntityQuery`（public final、private constructor），nested `public static final Permit`（private constructor、無public getters／fields）。Package-private entry由P9普通U0branch唯一呼叫，mint／持有exactpermit；public僅為transformed native跨package所需、capability-bound操作。
- 新internal `com.yo1no.gramarye.U0BoundedQueryAccess`：唯一mandatory operation `void gramarye$u0Collect(U0BoundedEntityQuery.Permit)`。唯一caller是上述queryowner；不回storage／world／index／locator。
- `@Mixin(LevelEntityGetterAdapter.class)`的`U0SectionQueryMixin`：`@Shadow @Final private EntitySectionStorage<?> sectionStorage`，field descriptor `Lnet/minecraft/world/level/entity/EntitySectionStorage;`；實作上述operation，僅把同call的shadow來源交`U0BoundedEntityQuery.collect(Permit,Object,EntitySectionStorage<?>)`。該static public bridge只接受private currentpermit＋其capturedadapter object，void、不同源拒絕、storage不retained或export；唯一consumer是此weavedoperation。Rawenumeration由此封閉operation完成；無無guard getter。
- `@Mixin(ProjectileUtil.class)`的`U0ProjectileQueryMixin`只wrap exact static method descriptor `(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;`內唯一 `Level.getEntities(Entity,AABB,Predicate)List` invoke；require/expect/allow=1。Private handler只對activeexactpermit以`U0BoundedEntityQuery.scopedCandidates(Level,Entity,AABB,Predicate)`取boundedephemeral候選；其他分支original一次。此staticpublic bridge唯一consumer是此wrapper，沒有publicmint／arbitrarybackend／queryregistration。Return的livecandidate list僅同nativecall臨時使用，不存入catalog／worker／network／報告。
- Sourcefields／targets／descriptors／invoke-count／interfaceoverride／publicbridgecallerfence與普通／工程artifactmixins差異須在實作後classfileinventory直接驗；不同platform／adapter／anchor不得silentfallback。這些是本輪normal工程接合，不是再開一個產品提案或增加接受framework。

以上source-feasible；實作前未聲稱runtime證明。必要直接驗證限此差額：0／64／65raw entries（含不相交／不合格者）、≤8section／coordinate超界、negativecoords／equal-distanceorder、nearest／terrain一致、multipart拒絕、reentrant/wrongpermit/source／slot及native原例外、legacy/engineeringoriginaldelegate、無load/ticket。真正consumer不在完整≤MAX前使用任何候選；不先選綠色片段。

### A.8 Sunset：暫時內容移除、永久infra保留

Sunset只有在新採用§35五項全部完成並驗收後才啟動：玩家建立／編寫、正式保存重載編譯、正式選擇／裝備、normalinput施放自有Spell、多人生週期／持久化驗收。U0現在不實作那些功能，也不把這些完成條件變成本輪提前開editor／learn/equip的授權。

1. **先封新工作**：停止新玩家provision、移除normalinput固定bootstrap selection、切換正式玩家選定root；禁止新U0prepare／Mana／CD obligations。已存在PENDING不是「新義務」：仍按其真release ARM或NO_RELEASE收束，不偽造取消／強制清掉原義務。
2. **Drain原exact工作**：保已接受root／child的原exactclosure、deadline、actor、same-server／memory-only語意；不restartrestore、不rebind新slot、不等CD到期就假稱instances/pins為0。
3. **完成來源與保留根盤點**：solewriter／runtimegate共同確認live provisional/accepted/closing pins、pendingpublication／recovery、externalroots、Draft／Manifest／其他current依賴與U0cooldown obligations。未知、stale、source失證則停止相關刪除，不從normalstop／portempty推導catalog或P11義務為0。
4. **精確匹配後只移除該subset**：每一player要同時核actualowner／source、trueForkProvenance的seedsource/ref/generation、exactlocalidentity＋revision、§2完整body及canonicaldocfingerprint。僅名字／ID／相似body不夠。未來catalog已含其他玩家內容時，不要求整catalog仍singleton，也不因此刪它們；相依bootstrap的玩家root保持保護，不能改寫其引用以湊清理。
5. **沿既有完整image commit**：`ScopeDCatalog.prepare(reservation, context, changes=[], removals=exactMatchedIdentities, inventory)`是已有internalremoval形狀，當前沒有normalproducer。Sunset明示授予唯一受控normalcleanupcaller，僅在阻止newcapture且真livepins為0、recovery已收束的時刻使用；仍reserve、graphvalidate、Cproof、atomicpublication／readback。Route取消不等於revision已刪；retainedrevision另依current／external／recovery／runtimepinroots真reclaim。
6. 現 `captureRoots`合入既有externalroots；本切片沒有任意釋放既存externalroot的API。U0不得把runtimelease偷存為永久externalroot。若碰到真externalroot或未知root保留bootstrap，fail-closed；不削弱rootinventory／不假造empty／不為刪U0擴出一般deleteAPI。
7. **Seed最後處理**：保留seedexactwitness直到同一boundedadmittedroster所有已知copies均完成精確判定與safe移除、無pending/recovery／引用來源。不能先刪seed後只憑report猜playerprovenance。完整收束後才用同一受控M1image路徑移除seedroute及可回收revision。Allocation/sourceanchor、admittedroster與no-reuse紀錄不reset、不刪整ownerdirectory來清內容。未能證全roster或任一copy時保留source並報未完成，不宣稱Sunset成功。
8. **CD清理**：只由既有P11soleowner處理該temporarynamespacedpolicyentry；需無PENDING／ARM／recovery等未結義務，依正式typed-keyschema及clock/retirement/readback規則。不得清legacy或別的Spellpolicy。Generictyped-keyschema／Attachment／service／writer全部保留。
9. **永久保留infra**：R normalinput、單一normalC2S/P7session/rate、M1owner-privatecatalog、Cfrontend/compiler、exactclosure、Manaowner、generictypedcooldownkey、normalactivation、V0backend及P8presentation；移除的是temporaryprovisioner／fixedselection／精確systemgenerated內容及已安全清理的temporaryCDentry，不是整條正式施法路徑。

Editor從U0存在期間就不能修改／fork-share／交易此bootstrap作玩家創作。未來editor須識別上述真provenance並拒絕修改它；不能現在增持久learn/equip欄位。若內容被外部改過、provenance缺件或無法判定，依§38保留並fail-closed，絕不刪可能玩家創作。沒有正常可刪來源時，不靠RAWmarker或新增systemboolean補權。

### A.9 精確既有來源與新採用界線

下列path均相對基底repository；行號屬e24 source。它們證明機械路徑／缺額，不證未實作U0已PASS。

| 來源 | 決定性事實 |
|---|---|
| `docs/codex-spec/20_DP2_ScopeD_SpellDefinition與可選SkillManifest修正案.md` §§2／3.3／5–7 | owner-private、immutablecurrent／exact、closedfork與durableroots，不含normal入口；本次U0只擴明列normalcomposition。 |
| `docs/architecture/M1-data-and-physical-contract.md:7–13,19–23,38` | Existingfamily/image/provenance、source/writer、no-reuseallocator、durablereadback與32owner/bytebounds。 |
| `docs/architecture/C-resolver-compiler-contract.md:3,7–18` | Ordinary未裝gameplayfrontend；真正proof綁source/candidate/context及preparedbytes，恢復後重新Cproof。 |
| `ScopeDData.java:114–119,126–182,287–294` | Existingforkprovenance只有sourceUUID/exactref/generation；同ownerprovenance非法；無systemmeta。 |
| `ScopeDFork.java:23–40,44–78` | export驗真sourceowner/currentclosure/raw/batch；importreceiveralloc映射，自動寫真來源provenance。 |
| `ScopeDCatalog.java:57–65,85–110,123–196,200–224,258–325,347–391,530–550` | reservation/export/prepareFork/sweep/removals、Cpublish/runtimeReadback/recovery及typedID公式。 |
| `ScopeDStorage.java:82–107,163–165,203–223,266–320,443–460,476–483` | sourceincarnation、watermark、atomicreplace/force/exactreadback、stale/fence/lock資格。 |
| `ScopeDSource.java:28–100`；`ScopeDCatalogImage.java:20–28`；`ScopeDLimits.java:33–46` | 真roster／owner／existingimage及technicalbounds，不新增ownershipschema。 |
| `ScopeDCompiler.java:103–107`；`ScopeDDataCodec.java:31–36,55–67,245–251` | Wholeformaldigest／既有schema及bodycodec，不發明persistentfingerprint欄位。 |
| `V0RuntimeCatalog.java:259–305,317–362,424–444,529–535,632–755` | 原write/lease/livepin單一owner、engineeringactivation/launch固定profile；需要新normalinternalcomposition，不reusefixture。 |
| `SkillRuntimeService.java:179–205`；`V0SlotRuntime.java:126–139` | Emptydistribution接合位置、owner==actor、真sourcecapture及現engineeringzero/positivefee分支。 |
| `V0StartupConfiguration.java:23–32,60–64,149–165`；`docs/architecture/V0-limits-config-and-capacity-contract.md:13–26,48,53,62` | V0typedlimits、合法default/hard、age/range關係與原querydensity證據範圍。 |
| `P9StarterProjectile.java:136–153,508–522,568–676`；`P9V0LoadedBlocks.java:29–89` | K1、loadedtailguard、真碰撞與exacttarget/dryshape。 |
| `P9V0WorldEffectHandoff.java:188–204`；`P8V0AppliedFactHandoff.java:14–22`；`P8ProfileCatalogSnapshot.java:15–31` | MatchingPost／applied-only及builtinappearance。 |
| `src/v0Engineering/java/com/yo1no/gramarye/magic/definition/scoped/V0EngineeringCatalogAssembly.java:18–46,86–164` | 只作現可支援descriptor/profile參照；newnormalpolicy不從硬編碼fixtureid、child或工程費率取得authority。 |
| `src/v0Engineering/java/com/yo1no/gramarye/v0engineering/mixin/P9V0LoadedPositionMixin.java:15–22`；`docs/architecture/V0-P9-projectile-carrier-and-exact-A-contract.md:34–37,65–77,110–122` | 現tailhook工程only；normalguard/query是本次明示接合，不generalize工程世界證據。 |

`ScopeD*`／`V0RuntimeCatalog`在`src/main/java/com/yo1no/gramarye/magic/definition/scoped/`；其餘普通類在`src/main/java/com/yo1no/gramarye/`。本稿沒有Java修改、tests/build/JVM、Minecraft、STATE／world、Gitmutation、masterpublication或protectedlogs/auth/raw讀取。

#### A.9.1 直接依據 source pins

| e24 path | bytes | SHA-256 |
|---|---:|---|
| `src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDFork.java` | 5183 | `6a9ac22fb09d1abe15851b46da529e45b1b99de4648a6590727d3731e9807124` |
| `src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDData.java` | 31910 | `88f9775e38d3fd5176e11acc9ff74af68cb64137202b9215a07211716a81ba66` |
| `src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCatalog.java` | 28325 | `0fa47aa42bb39ee9f43462c7780cfda225af0a9ab3b56744b7cc99a99873e32b` |
| `src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDStorage.java` | 32304 | `d7a6dfd454443ffa23f1088580c9d071f8def410d54afe3324e7d6d83dcf2090` |
| `src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDSource.java` | 5674 | `2c764a69d9a2383ab9a85b02483ff5967cfc1040aac3362a251ba48625865ef0` |
| `src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCatalogImage.java` | 9588 | `12f33d0e11259fba8929443463797ccc6540c8bcdd2df6404dfe84d3c5fe3bc0` |
| `src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCompiler.java` | 15613 | `e08b014a271da673bab672d145e0a2b19fc1f07aab5c184f63b691cdd553202a` |
| `src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDDataCodec.java` | 28446 | `a203bf1629d5b607e8e08db1978009bb059ada07ed000ca1a1ea11a62c501b94` |
| `src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDFrontend.java` | 24148 | `25e15c9f4747e71a31cd3da2274d3bd7d6ee6558f841934ccf6ee2e488c3e20c` |
| `src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDLimits.java` | 5798 | `901d5fdb16571402d21f2a7e18aff05f8ad50895b4844d0785acdc537d287698` |
| `src/main/java/com/yo1no/gramarye/magic/definition/scoped/V0RuntimeCatalog.java` | 44976 | `211ac1c2e2e6ca272a93db934365a9cd2bb2afc4cab2e8b41519ce9ea9127a01` |
| `src/main/java/com/yo1no/gramarye/P9StarterProjectile.java` | 33516 | `0bf8a01d141d993e2951397c5b0a97a0853a34ee563485737d2a37629539397b` |
| `src/main/java/com/yo1no/gramarye/P9V0LoadedBlocks.java` | 5485 | `a68c6f82f50ef38df85ce5c0329c2d8218ad2096fee6ee76a35af973657f1761` |
| `src/main/java/com/yo1no/gramarye/P9V0WorldEffectHandoff.java` | 15796 | `129768dd66671977bc63ba45f6037bd0f21565cf7fdf278c26d627bafe0501a5` |
| `src/main/java/com/yo1no/gramarye/P8V0AppliedFactHandoff.java` | 826 | `81b50e0b1e212bde1ccffc89a773b1372865c9d265ab36dfed254dc615a756af` |
| `src/main/java/com/yo1no/gramarye/P8ProfileCatalogSnapshot.java` | 7136 | `98745d1c7c4835d5595b0f3d00d11fa49b9d2ab61f5f83d45cfd6187df23ff44` |
| `src/main/java/com/yo1no/gramarye/SkillRuntimeService.java` | 277094 | `fa0cbfa8151cb54f57292a1db93cadfaa5934be27c27ae02c78526acc6b77e0d` |
| `src/main/java/com/yo1no/gramarye/V0StartupConfiguration.java` | 7662 | `92aa8beb9c5633779d46dcf523fa1e54a4bb0b1d5291760251912573ea035100` |
| `src/v0Engineering/java/com/yo1no/gramarye/magic/definition/scoped/V0EngineeringCatalogAssembly.java` | 9681 | `69d9ee3384bb930b4e5ddced3c19bedc80f4d775907aaa81f55904b2a5f71898` |
| `src/v0Engineering/java/com/yo1no/gramarye/v0engineering/mixin/P9V0LoadedPositionMixin.java` | 1204 | `9f8c658c8931aeba2f30b596d83674c039f38c5fbf24c6ba2209ddded9eae399` |

---

## B. 通用typed cooldown-key schema、遷移與release

### B.1 精確 supersession／preservation

| 原 authority／source | 本節精確擴充 | 明確保持 |
| --- | --- | --- |
| 19 §11.2／§11.6 的唯一 SkillId key、schema1 | schema2 增具名 discriminator 與 logical-policy 變體；相同 sole owner | schema1 所有有效 legacy states／256 容量、原型別／排序／Raw／marker、原 read／write 行為不降級 |
| 19 §11.4／§11.6 的 policy1／101 | policy1 原樣；另有真正 V0 captured-window 的 policy2 | 不把 101 改成任意值，不重標舊資料，不以新 revision／當前設定重算舊義務 |
| 19 §11.3 的 PREPARED／PENDING／ARM／NO_RELEASE | 同 owner 將 typed-key preparation 接到真 V0 root publication／release | true native add、P8、ACK、constructor 都不能單獨證 ARM；unknown 不退款／不重播 |
| 19 §§5–6、11.5–11.9 | 同 source／writer／clock／clone／readback 處理新變體 | exact A／R、原錯誤、whole-source、native reward、legacy sync 與 source 狀態不改 |
| Scope V §5 的工程 zero-CD | 新 U0 normal policy 才要求正 CD20 | 原工程 zero-CD、fee／Mana receipts、legacy 玩家結果與 V0 55-row 證據保持原 scope |

基底 source：`P11CastCooldownData.java:11–112`、`P11CastCooldownCodec.java:12–225`、`P11CastCooldownService.java:79–174,231–343`；V0 真期限與 release 接點為 `V0SlotRuntime.java:219–270,514–519,694–720`、`SkillRuntimeService.java:1295–1383`。原 legacy prepare／ARM 參見 `SkillRuntimeService.java:675–695,2011–2033,2089–2135`。完整布局、cross-fields與靜態byte算式固定於本Part的表格；其e24來源身分由同一累積交付紀錄保留。

### B.2 Key、身份與權限

內部使用封閉、package-private typed key，語意等價於：

```java
sealed interface P11CooldownKey {
    record LegacySkill(SkillId skillId) implements P11CooldownKey {}
    record LogicalPolicy(ResourceLocation policyName) implements P11CooldownKey {}
}
```

外層不可跨 package 命名；record 的 implicit declarations 必須列入實際 API inventory，不能用「package-private」隱去 classfile 成員。這不是公開 plugin API、actor locator 或任意 policy provider。Key 是資料路由，不是入場 capability。Cell 仍按 **exact server identity＋authenticated UUID**；不同玩家、server、key 變體不共用義務。

`LegacySkill` 必須是原真 `SkillId`。`LogicalPolicy` 保存完整 canonical `namespace:path`，不轉 UUID、不借實體 Spell／Manifest／Skill 的數值 ID；可以代表 server-controlled logical Spell／policy，對未來法術通用，codec 不認 U0 特殊名稱。U0 正文使用一個暫時 key `gramarye:u0_bootstrap_qualification`，跨其合法 immutable revisions 共用同一 cell entry；它不表示玩家 learned／equipped／selected，也不是 catalog identity／provenance 的替代品。

持久 material entries 與 live-attempt index 都以此 typed key 路由；不得在旁另留 namespaced-key→UUID hash map。只有最外層 player cell index 仍使用 authenticated UUID，兩者不能混同。

Policy name 要求：exact NBT `StringTag`；恰一個 `:`；namespace、path 均非空；namespace 字元 `[a-z0-9_.-]`，path `[a-z0-9/._-]`；全字串 ASCII、總長 **3..128 bytes**。不得 trim、大小寫修正、補預設 namespace 或解碼後換 ID；不接受 Unicode／NUL。這不是 filesystem path，不能拿 key 讀檔。Server policy 唯一產生 key、duration 與 release profile；client 不提交以上資料。

### B.3 Schema2 physical shape

NBT number 必須 exact tag type，不 coercion。Root 為：

```text
ROUTED root = {
  schema_version: Int(2), kind: Byte(0), clock_floor: Long(>=0),
  entries: List<Compound>
}

Legacy row = { key_kind: Byte(0), legacy: Compound(<完整原 schema1 entry>) }
Policy row = { key_kind: Byte(1), policy_key: String(<canonical namespace:path>),
               body: Compound(<下節 policy2 body>) }
```

每個 Compound 恰為列出的 fields。Legacy inner **不是重定義的 UUID alias**：它保持 schema1 entry 的 `skill_id:IntArray[4]`、`revision`、`policy_version=1`、kind／bound／Raw／marker exact shape，交原 legacy entry reader／writer。它內部沒有新增 schema_version 或 key_kind。`key_kind` 是真正 family discriminator，`policy_version` 只表示 release／recovery 契約，不能偷作 key family。

Empty entries canonical 為 END element type0／count0，非空 List 必 Compound type10；已 materialize empty list 的原 element-type metadata，沿 P11 舊 logical-tree 語意，不聲稱保存消失的 physical metadata。

### Canonical ordering／duplicate／routing

1. 先 `key_kind`：全部 legacy0 在全部 policy1 前。
2. Legacy 依原 UUID 兩個64-bit unsigned lexicographic（high、low）排序，完整128 bits 保留。
3. Policy 依完整 canonical ASCII `namespace:path` bytes lexicographic 排序；prefix 較短者在前。不能仰賴未指定的 `ResourceLocation.compareTo` 排法。
4. 同 family／同 key 重複，不論 entry kind、revision／policy version 或 value 是否相等，整 root `DUPLICATE_KEY`／unavailable；不能先 Map 化、first-wins、last-wins、merge 或吞掉一筆。不同 family 永不 alias。
5. 先驗完整 root／全部 routing、排序、唯一性與 family counts，才做已知 key 的局部 body quarantine。Unknown key_kind、不可辨 key／outer shape、extra outer fields、非法 policy name 等不能可靠局部路由，保 whole RAW／marker，不取前綴 READY。
6. v2 legacy inner routing 仍由其原 `skill_id`；缺失／錯型時 whole unroutable。已可信 route 下的 inner malformed／future policy 按原 v1 local RAW／marker；normal policy body 的 malformed／future policy 按下節 local RAW／marker，其他可信 key 不被局部錯誤一律禁用。

平台 materialization 前已被 Compound 覆蓋的同名 fields 不可由此 codec 偵測；不宣稱 raw-wire duplicate rejection。

### B.4 Generic policy2 body／cross-fields

Policy2 目前定義「有真 V0 root deadline 的 bounded release profile」，不依 U0 ID 特判；未來其他 backend／release semantics 必須另採 bounded policy version，key 格式不需為每種 Spell 改版。沒有在本節默許 arbitrary backend／unbounded duration。

```text
normal common = {
  kind: Byte, policy_version: Int(2), duration_ticks: Int(1..600),
  accepted_at: Long(>=0), attempt_id: IntArray[4],
  release_window_ticks: Int(4..41), release_not_after: Long
}
PENDING kind1 = common
ACTIVE kind0 = common + { released_at: Long, expires_at: Long }
UNCERTAIN kind2 = common + {
  reason: Byte, uncertain_at: Long, release_knowledge: Byte,
  candidate_release_at: Long, candidate_expires_at: Long
}
local RAW kind3 = { kind: Byte(3), reason: Byte, raw: Compound }
local marker kind4 = {
  kind: Byte(4), reason: Byte, observed_at_least: Long, maximum: Long
}
```

Policy body 不另存 catalog revision：key 對 revisions 共享義務，exact revision／closure 由已受理 P5/M1 lease 持有，CD 到期不 pin／重讀可能已回收的 definition。`policy_version=2` 明確是本 release profile，不把它當 family tag。

Cross-fields：

- `accepted_at <= clock_floor`；checked `release_not_after = accepted_at + release_window_ticks`；checked `U = release_not_after + duration_ticks` 可表示。Reader 只認4..41與該等式，不接受任意自稱 captured 的 bound。
- ACTIVE：`accepted_at <= released_at <= release_not_after`、`released_at <= clock_floor`、checked `expires_at = released_at + duration_ticks`。只有真 ARM 寫 released_at。
- UNCERTAIN：原 P11 knowledge0／1、candidate pair 完整性、uncertain_at／floor、reasons9/10/11及「known ARM 不降未知」規則全部適用，只把原101改為本 entry 已捕獲的 window。Knowledge0 的候選 pair 不是 ARM 證據；半個−1不合法。
- D0 是 admission policy，不是可持久 normal entry。可信無舊義務才返回 Zero；既有 ACTIVE／PENDING／uncertain／quarantine 不因當次D0、revision／session／slot更新而清掉。
- Local RAW 原始 **body** anyTag≤278、depth≤8，外層可信 policy_key 持續路由，RAW 不授 cast。局部 RAW reason只1／2；marker只5／6，maximum分別278／8，observed必超maximum。已是 canonical RAW／marker 再 read→clone→write不重包／不丟原材料。

舊 legacy entry 仍只接受原 policy1、duration1..600、revision Int、accepted+101。把 policy2 寫到 legacy inner 仍是 unsupported，不轉成 LogicalPolicy；舊合法 RAW／marker的297／8原樣。

### B.5 容量、bytes、depth 與 MAX+1

| 軸 | schema1／legacy 保留 | schema2 新域及 mixed |
| --- | --- | --- |
| Legacy keys | **256**，不變 | 仍256；不是255＋U0 |
| Logical-policy keys | 無 | **64**，獨立配額 |
| Combined rows | schema1最多256 | **320**（256＋64） |
| Policy name | 無 | ASCII≤128 bytes |
| Legacy local raw anyTag | 297，depth8 | Legacy inner仍297／8 |
| Policy local original body anyTag | 無 | 278，depth8 |
| ROUTED root anyTag | 90180 | **125572** |
| Whole RAW root anyTag | 90226 | **125618**＝46＋125572 |
| Whole marker anyTag | 87 | 87 |
| Whole original raw subtree depth／final wrapper | 16／17 | 16／17 |
| Normal depth | 4 | 5；routed局部RAW最深12 |

64 是保守的可驗 engineering ceiling：U0只用1，但格式不是U0-only。它不是永久語言上限、已學法術數、M1 quota或在線容量承諾；不新增config／player ownership list。後續提高只針對受影響的新材料與 bounds 接受，不改原256 legacy權利。

任何 local RAW／marker仍占該 key名額；不以 TTL、容量滿、logout或新revision evict義務。新 policy64 已滿只拒新第65個 policy key，不占／減 legacy256；legacy256滿不阻尚有名額的policy。既有key合法 reconcile／expire／update依原owner處理。D0且可信沒有義務不新增entry，不因沒有新entry的quota收費。

Reader pre-count whole byte/depth，遇 limit+1即停止、不先copy巨大raw。對已辨 canonical whole RAW／marker先按其正確版本識別，以維持 wrapper depth17／bytes高於routedcap的合法冪等；whole RAW的原始raw subtree仍限16。Unknown/future root版本依來源schema與完整 bounded材料保存，不猜v2、不fallback empty。

正常 source admission／writer若將新增row超界，typed refusal、原material不變；**讀取既存非法資料**才走具名 whole quarantine／marker，不將自己拒絕的新cast寫成marker。v2 root ENTRY_LIMIT7 的maximum320／observed≥321；另新增僅v2 root marker用closed reasons13 `LEGACY_ENTRY_LIMIT`（256／≥257）、14 `POLICY_ENTRY_LIMIT`（64／≥65）。Reason1–12與所有v1marker maxima不變。兩個新reason對既有P7只映射原BOUNDS，不新增wire ordinal或payload。

Max+1 至少直接覆蓋 legacy257、policy65、total321、policy name129、local原始body279、legacy原始raw298、v2 routed125573、local原始raw depth9、whole原始raw depth17；不能截斷到合法prefix。

### Exact logical NBT derivation

沿現行 `P11CastCooldownNbtSize.measure` 的 post-materialization `writeAnyTag` 座標：tag-id1，named field `3+ASCII名長+payload`，Compound END1，List header5，IntArray[4] payload20。ASCII policy name 的 modified-UTF width等於其byte長；不以 `String.length` 對任意raw字串估算。

| Compound payload（不含本tag-id） | Legacy v1 inner | Policy body | v2 legacy row | v2 policy row（128-byte key） |
| --- | ---: | ---: | ---: | ---: |
| PENDING | 180 | 161 | 202 | 324 |
| ACTIVE | 223 | 204 | 245 | 367 |
| UNCERTAIN | 296 | 277 | 318 | 440 |
| Local RAW at cap | 352 | 302 | 374 | 465 |
| Local marker | 96 | 65 | 118 | 228 |

Legacy wrapper額外22＝END1＋key_kind12＋legacy欄header9。Policy wrapper固定35＋key長L＋body payload；其body common named fields共160，END後PENDING161、uncertain額外116→277、原body anyTag278；local RAW body＝24＋278＝302。

Root fixed overhead68。最大routed為 `68+256×374+64×465 = 125572`；whole RAW `46+125572=125618`。全ACTIVE／PENDING／UNCERTAIN mixed 分別86276／72516／109636。這些是 **完整 logical NBT tree 的width**，不是 compressed bytes、Java retained heap、preallocation/OOM防護或 whole-player上界。原native save／封存budget不足時的既有fallback與source責任不變。

### B.6 Schema1 read／migration／mixed／writer

1. Reader 以exact Int schema_version分派。**Version1完整委派原reader**，包含原256／90180、scope、normal／raw／unknown／marker與錯誤precedence；不能用v2較大cap默默接受原來非法的v1。
2. 只有legacy、來源仍v1時，沿原v1writer／canonicalbytes，read、clone、sync不因新程式就強制改版／dirty。舊不可變revision／輸入材料與既有v1保存結果不回寫。
3. 新logical-policy正義務首次需要安裝時，在same server-main／source／expected generation下，完整預建schema2 replacement：原256 legacy inner逐一完整保留，加目標policy PENDING；全部route／count／bytes／clock／material checks成功才作**一次immutable replacement**。原publication未知仍按原P11處置，沒有第二writer或silent fallback。
4. Legacy normal／local RAW／marker inner使用原entry writer，字段、值、type、policy1／101／raw／marker不被新key重標。外層v2包裝與root版本必然是新bytes，不能聲稱整份已migration的root仍byte-identical；**未migration的legacy-only v1路徑必須byte-identical**。
5. Whole RAW／marker本來unavailable，不以migration修成READY。Version1 whole材料仍保持version1、原raw及原maxima，clone/save不換上新limit；必要unknown/raw狀態保持原錯誤與不可用性。
6. 已寫v2的root不因policy條目歸零自動降回v1。`origin format`屬同一immutable資料的序列化版本，不是第二帳本／selection。Source version／material identity仍由原P11 publication推進。
7. V2能混合v1legacy envelope與policy2正常／局部quarantine；全部canonical order、duplicates、mixed count驗完才建唯一immutable map。Version>2／unknown discriminator／unrouteable不能當absence。合法unknown在上述完整 bounds內保留；超界marker按原明示destructive quarantine語意，不假lossless。

### B.7 同 owner 的 prepare／receipt／release 接線

此節不是另一 cooldown engine。現有 legacy `prepareAdmission(MinecraftServer,ServerPlayer,SkillReference,int,long,long,ReleaseReceipt,WorkReservation)`、`ReleaseReceipt`三個標量方法、legacy caller及policy1的101／strict boundary保持；共用P11 reconciliation／publication／writer。

新增的內部 normal可重用入口定為 `P11CastCooldownService.prepareV0PolicyAdmission`，package-private，輸入exact server／actor、`P11CooldownKey.LogicalPolicy`、server-captured duration、實際 `runtimeAccepted`／`runtimeDeadline`、同slot immutable `lineageDeadlineTicks`、P5私有 `ReleaseReceipt`、原 `WorkReservation`。它不是U0特判：任何已採用server logical policy可沿此真V0 profile，不接受clientkey／duration／bounds。唯一 production caller是P5 `V0SlotRuntime.acceptCompletion` 的normal-policy分支，不能由network／item／command／engineeringissuer直呼。原工程無CD分支保持。

```java
Admission prepareV0PolicyAdmission(
        MinecraftServer exact, ServerPlayer actor,
        P11CooldownKey.LogicalPolicy key, int duration,
        long runtimeAccepted, long runtimeDeadline, int lineageDeadlineTicks,
        ReleaseReceipt receipt, P11QualifiedSourceOwner.WorkReservation work);
```

此處duration可為server policy的0..600；0只回既有Zero語意，不生成normal entry。Caller須使用真slot的immutable limits和已準備root座標，service再次checked驗`runtimeDeadline=runtimeAccepted+1+lineageDeadlineTicks`、3..40及原work/source資格，不能把三個caller scalar當獨立capability。

既有 `prepareArm`／`completeArm`、settlement與publisher按prepared key／captured profile分派，**不新增另一ARM service／platform hook**；legacy projection仍走 `P11CooldownPolicyProjection`，不能以fakeSkillReference使它讀Spell。P5新增私有V0 receipt implementation／精確caller inventory，與現legacy concrete receipt分開；只保存attempt identity、phase、releasedAt／revoked等有界標量，不在持久資料保存actor／slot／catalog／program／Throwable。兩種receipt的數字UUID不授權，必須matching exact prepared receipt／key／cell／source／same slot／work，不能憑UUID相等跨family配對。

#### B.7.1 真期限捕獲，不傳假101

- **Legacy policy1：**原Ga/Ra、Dr=Ra+101；runtime ARM必`R<Dr`，gameTime `G<=Ga+101`；舊persisted bound、orphan恢復與U最壞101＋600＝701完全不動。
- **Policy2 V0：**cold completion已取回、exact actor/source/currentcatalog／capacity已重驗、真正accepted candidate準備點捕獲Ra。原source `dueTick=Ra+1`、`Dr=dueTick+L`，L是該slot immutable lineageDeadlineTicks3..40。因此 `window=Dr−Ra=1+L∈4..41`（default25／hard41），不是從早先rawintent或coldcapture開始計時。
- 同受控點取可信Overworld Ga，checked `release_not_after=Ga+window`、`U=release_not_after+D`；owner重驗傳入Dr與原P5配置/claim一致。新entry保存window，restart只驗capturedpolicy2／range／等式，不讀latestlimits重算。
- ARM保原V0 root inclusive資格 `R<=Dr`（`usable`只拒`currentTick>deadline`）及可信 `G<=release_not_after`；hit／child另外已有strict截止仍原樣。不能把legacy strict或V0 inclusive互換，也不因冷卻延長instance／lease／age。
- 時計rollback／overflow／source不可信，按原clock/unavailable收束；不clamp floor、不重新抓now+window。正常gameTime與P5 runtime tick關係沿19§11.4鎖版clock論證；意外跳時不被猜成可信。

#### B.7.2 Admission／ARM／NO_RELEASE線性化

1. 將PENDING準備放在實際 `acceptCompletion`：已捕獲whole closure、合法P11 W及實際due/Dr，但**catalog compareAndAccept成功之前**。同一次sole owner先pure checks／可信source／old-keyreconcile／完整row與replacement預建，再按expected generation安裝PENDING，才原CAS／P5 publication。不是Accepted後blind cancel回滾。
2. Receipt為P5私有、一次性 UNPUBLISHED→publication-unknown→PENDING；CAS前具證不發布且attempt不可逆撤銷才NO_RELEASE並clear。CAS／source／publication未知保UNCERTAIN與真prefix，不重publish／重spawn。
3. Native add期間保V0現有`ADDING_INERT`／`OPEN&&!openPublished`不可gameplay。只有同次P6 `CONFIRMED_APPLIED`、exact carrier已inserted/alive/added、matching native/custody、原`publishNativeCreate`合法，且原exact A及既有L1/W/whole R、canonical source/slot/clock/bound檢查仍成立（不是重做新admission的online/roster guard，亦不以較窄paymentSource替代cooldownWorkRecipient），P11才預建ArmPreparation。
4. 在沒有外部callback／I/O／可失敗一般分配的短段發布matching root `openPublished`及單調ARM fact；不得令任何consumer看見可執行OPEN卻沒有其true-release receipt。再交原`completeArm`發布預建ACTIVE；之後publisher/diagnostic出錯不能抹去ARM。這沿原P11「receipt先成真，publication可再reconcile」模式，不宣稱VM跨ownertransaction。
5. 原P6已apply但未達真正可用release、或nativefalse／removed／transfer失敗且未ARM，先不可逆關閉未來ARM資格再NO_RELEASE；保native prefix，不能倒稱世界零mutation。未知既不NO_RELEASE也不refund。Cost0本來沒有正debit receipt；正費將來亦只能沿既有具證no-primary補償，不因CD cleanup猜退款。
6. 一旦ARM r已證，expiry只為checked r+D。Miss、hurt=false／protection、child結果、P8／ACK失敗、work terminal、logout、Sunset不退／重起算D。Child不另付費、不重ARM同key。D0仍先查舊義務，無舊義務才不建PENDING／跨tick busy。
7. RuntimeException／Error保持原primary與owner cleanup；不catch-and-continue gameplay，不以清理例外蓋primary。Data重試只寫資料，沒有world／callback／reward重播。

### B.8 Recovery／clone／restart／同步／Sunset

Known receipt優先：same JVM、matching attempt/key/generation的ARM還原exact r+D；irreversibleNO_RELEASE清；unknown才依原knowledge規則等capturedU。Policy2 U≤Ga+41+600＝Ga+641；U0D20的default/hard上界是Ga+45／Ga+61，不是實際冷卻一定多等25/41，也不是給所有fault有限恢復保證。Policy1原701及其例外不變。首次PENDING未落盤即crash的原限制仍明列；不能宣稱不漏義務或RAM可跨restart恢復。

Normal Attachment serialize／copyOnDeath／End／reconnect仍由原native流程及qualifiedsource完成；格式與key不得重置clock_floor、attempt或bound。Unknown、RAW／marker、save failure不可成freshempty。KnownACTIVE保存失敗仍是memory ACTIVE＋trueexpiry／SAVE_FAILED；NO_RELEASE較新clear勝舊disk，但沒有成功readback不能聲稱durable。每physicalsource原solewriter，實際write/close/replace/readback各自證；沒有跨檔atomic承諾。

P7現有legacy equipped snapshot仍只查`LegacySkill`，包體／protocol／64slot／sequence／sourceidentity／resync規則不變；v2中可信legacy資料投射與v1一致。LogicalPolicy不假占slot0／SkillReference，不新增normal S2C或U0mirror。新localpolicyquarantine不把其他可信legacykey一律unavailable；whole不可信仍按原whole-source規則。U0只用server安全localizedactionbar拒絕＋P8success。

Sunset barrier禁止**新U0 admission/PENDING**；先前已接受attempt的PENDING→ARM/NO_RELEASE仍是既有義務收束，不算新root，也不為清理否決原已接受生命週期。等待其instances、pins、publication/recovery及CD責任真終態；只能清exact暫時U0policykey且可信已無任何PENDING／ACTIVE／uncertain／RAW未知義務的row。不能因Bootstrap route已刪、時間夠久或capacity滿直接清unknown。其他policy／legacykey不動；typed schema、normalinput、P7/M1/C/Mana/V0/P8保留。精確U0內容provenance另由provisioning／Sunset正文治理，key本身不能代證可刪某個Spell。

### B.9 受影響 direct acceptance 與量測

只接入原U0 direct matrix／既有P11 tests，沒有另開資格階段。直接涵蓋：

- v1 golden正常／RAW／marker／future／bad input read及legacy-onlywrite bytes exact；old101／D0／ARM／NO_RELEASE及legacy sync不變。
- v1滿256legacy→v2加policy；v2 legacy256＋policy64＝320真mixed；兩域獨立MAX/MAX+1、canonicalorder、duplicates／wrongtag／129-bytekey／unknownfamily及全部routing-before-localquarantine。
- 表中normal／RAW／marker exactbytes、depth與limit+1、no-truncation、immutablealias/copy、unknown冪等；沒有用raw包裝洗成READY。
- 真V0 source Ra/due/Dr/window4/41、default25/hard41、clockrollback/overflow、inclusive/equality、oldstrict101對照，不能假offset／假ARM。
- Prepare拒／source換代／CAS拒／publicationunknown／nativefalse／true-butremoved／reentrantcallback／exactARM／irreversibleNO_RELEASE／savefault，保primary與singleattempt，無extra nativeeffect。
- U0普通真認證A/B cost0／CD20，true root release→sameownerACTIVE，active拒／expiry、兩玩家獨立、revision更新不洗key；clone／normal/faultlogout／reconnect／sameJVMnewserver／fullrestart／readback不重播。Cost0仍驗Mana存在性／availability／actor-sourcepairing，不偽造positive debit/refund。
- Sunset barrier前後accepted義務、unknownfailclosed、onlyexacttemporarykeycleanup、generic/legacyretained；不引入selection/ownership。

新材料在48MiB**獨立category程序**量codec/typedmaps/兩域full＋MAX1／clone/serialize／readback模型的allocated／used／post-GCretained／physicalcapturedtemporary／peak／cleanup／GC，並在普通nativeU0只驗受影響owner/runtime結果。原A01 R0／55列不重跑，不增加全Minecraft48或wholeVM/allocationoracle。新immutablematerial可與source/cell共用同identity，預建replacement／serialization／clone的實際並存群組必identity-dedup量測；NBT125572並非heap上界，不把wirebyte乘數當Instrumentation證據。

64-policy ceiling、MAX+1與native/restart接受未通過前，不報U0 runtime COMPLETE。Source／API／generatedmember／caller／dualJAR placement只同步受影響inventory／consumers；不新增publicpluginAPI或整包白名單，不以此設計稿冒稱任何compile／test已執行。

---

## C. 永久normal input／P7 operation與30列直接接受

### C.1 永久入口與暫時內容的分界

永久保留的是一個rebindable普通施法輸入、generic normal C2S operation、P7認證／session／sequence／rate、正式catalog／compiler、Mana owner、typed cooldown-key schema、V0及P8。**網路不叫U0，也不編入Bootstrap identity、免費政策或20tick值**。

Normal operation的語意是「請server按當前正式activation policy嘗試一次施法」。`current`只是當次server policy解析結果，不建立current-spell持久狀態。U0期間唯一policy選擇當前玩家owner-private、具有精確system-generated provenance的Temporary U0 Bootstrap Spell；未來經另案正式採用的玩家選定／裝備root可以替換policy而沿用同一input／operation。這不授權現在實作learn/equip/selection/editor，也不讓client選root。

Temporary Bootstrap不是legacy Skill、不是工程fixture、不是永久預設技能、不是玩家學會或裝備的資料。成本為0，但仍驗正式Mana source／availability／actor配對及transaction owner；cooldown為**20 advancing authoritative server ticks**，須真prepare→PENDING→release→ARM，未release走原NO_RELEASE。Cooldown到期不代表舊lineage結束；capacity full照原規則拒絕，不排舊輸入、不重送、不延長age/deadline。

### C.2 N1：exact generic wire contract

| 項目 | 固定值／語意 |
|---|---|
| Normal payload ID | `gramarye:cast_current_spell` |
| Required registrar version | `gramarye-normal-spell-v1`，**exact match only**；不使用optional registration，不借P11 version |
| Direction／phase | exactly one `PLAY_TO_SERVER` registration；CONFIGURATION／LOGIN／PREPLAY與反向不註冊、不承接 |
| Body | **exactly8 bytes**：一個big-endian signed `i64 sequence` |
| Body schema byte | 0個；required channel的exact registrar version已唯一決定body形狀，不容同major猜讀 |
| Encoder合法值 | `1..Long.MAX_VALUE`；其他值不產生合法outbound intent |
| Decoder結構值 | exact8bytes的全部i64 bit patterns可結構解碼；`sequence<=0`交P7原semantic INVALID_SEQUENCE，無P5、無假成功；不把它偷偷改成malformed policy |
| Truncation／extra | `<8`／`>8`一律malformed；讀完必exact fully consumed；無可忽略尾碼 |
| Batch | 每payload一intent，無list、無batch、無optional欄位 |
| Forbidden authority | Spell ID/revision/Manifest/program/backend、slot、Mana/cooldown、actor UUID、server generation、target/position、permission/bypass、Bootstrap旗標均0 |
| New normal S2C | **0**；existing P7 ACK／P8／P11 channels不是新的U0 payload額度 |

這是**新N1**，不是現有`CastIntentPayload`的新解釋。Current legacy wire為`gramarye:cast_intent`／`gramarye-p7-v1`，body含slot及optional hints；其current ingress以P4裝備`SkillReference`做owner/store/retained-revision查核，不能把slot0當U0 sentinel、放寬invalid kind或跳過legacy guard。

唯一registration owner仍為現有`com.yo1no.gramarye.magic.network.P7PayloadRegistrar.registerPayloads(RegisterPayloadHandlersEvent)`；同一callback以獨立required version registrar增加上述**一個**PLAY server registration，`executesOn(HandlerThread.NETWORK)`。不新增第二個event subscriber、泛用registrar、public plugin API或client/common靜態旁路。現有P7四payload、P8兩payload和P11固定C4a兩payload的IDs、versions、directions、fields、handlers各保持；N1單列其after-inventory。

Common payload／codec／server owner不link `Minecraft`／KeyMapping／Screen；client adapters保Dist.CLIENT。新normal codec、handler、內部operation／root port的實際宣告、fields、constructors、descriptors、generated members及unique callers須在U0 before/after inventory列實物，不以名稱包含private或本draft推定surface通過。

### C.3 同一 P7 owner 的完整 dispatch path

以下是一條normal操作分支；不是把legacy guards旁路後送V0，也不建立另一P7 session、sequence、rate、permit或scheduler。

```text
existing R KeyMapping + sole P9ClientCastInput Post-tick sender
  → generic gramarye:cast_current_spell(sequence)
  → sole P7PayloadRegistrar NETWORK handler
  → authenticated context.player is exact ServerPlayer
  → same P7NetworkComposition connectionEpochSource
  → same P7ServerSessionService.captureAuthenticatedSession(UUID, exact context.connection)
  → same P7PendingPermitOwner.acquire(UUID, connectionEpoch, expectedServerGeneration)
  → revalidate ORIGINAL capture; never recapture/current-relabel it
  → one immutable scalar NORMAL_SPELL operation + captured identity + single-use permit
  → exactly one IPayloadContext.enqueueWork
  → same P7 server-logic dispatch owner and same current server/session/native actor checks
  → same reload/global-work/player-ingress/token-bucket/sequence transition
  → same alive/non-spectator semantic gate
  → NEW, explicitly closed NORMAL_SPELL qualified root ingress
  → P11 exact source + current server activation policy
  → owner-private catalog/current generation + C compile/exact closure
  → V0 provisional lease/P5 reservation
  → formal Mana0 and typed policy cooldown20 prepare
  → compare-and-accept, then original P5/V0/P6/P9 and P8
  → existing P7 coarse ACK + bounded safe vanilla feedback as applicable
  → permit release in original finally
```

#### C.3.1 舊 branch 與新 branch

- Legacy operation仍由原handler→原`P7ServerAuthorizationDispatcher`→`P7ServerAuthorizationBoundary.dispatch(server,actor,slot,targetCheck)`→`P7AuthenticatedPlayerCastIngress.authorizeAndAdmit`。其`equippedAt(slot)`、exact owner、Store `find`、retained revision、target hint、server geometry與`admitAuthenticatedPlayerCast`全guard保持。不能以normal wrapper命名便跳過它們。
- 新normal operation以封閉內部typed wrapper／operation discriminator與legacy slot operation區分；wrapper由N1唯一handler建，不接受外部任意Action/callback、root/program/ref或permission。Operation本身不是權限；兩分支先共用上述P7安全／資源／sequence步驟，才各進具名root port。
- 新normal root port是本U0明示採用的production入口：逐次確認current exact authenticated actor、P11 source、server policy、owner-private有效exact Spell及original V0准入，不依legacy equipped SkillId，也不使用engineering issuer／`V0EngineeringEntry`／scenario controller。它不把「正常」當作略過qualification的Boolean。
- 不保live payload context、packet buffer、actor／level／connection於queued task；task僅持immutable decoded sequence、原P7 scalar identity、closed operation與permit。P7 sole session owner的既有exact Connection binding仍按P11 D3的限定責任存在，不擴張為通用retention。

#### C.3.2 D3／phase／session

依P11§9，capture必在原session `stateLock`下，以authenticated UUID＋**這個**`context.connection()`比對已由原login-ready公布的native binding，取得epoch＋server generation。另一permit monitor的`acquire(expectedGeneration)`後再查原capture；不得宣稱兩lock是原子transaction。Main再次驗current exact server、published generation、session、roster actor、native listener／connection與P11 actor資格。真old capture晚到、新UUID相同但新connection、same-JVM新server、enqueue前stop、enqueue後session退出都不能自我轉成current。

P7 server epoch／generation、client outgoing sequence、P7 client dispatchGeneration、P8(C,L) PLAY epoch／world／resource generation、P11scene／actor／request序號保持不同語意。CONFIGURATION或actorless parking不因有socket、P11 control channel或P8 catalog就可施法。Same-connection正常respawn沿原P7 epoch政策；新connection才原規則重開。不能靠清mirror、U0 policy切換或sunset洗rate／sequence。

#### C.3.3 Rate／backpressure／sequence

保現行P7：bucket初值及capacity8，每advancing server authoritative tick lazy refill2，CAST成本1；per-player8 evaluations/tick、global64共用work/tick、pending permits每player8／server64、active sessions每player1／server256。採相同server clock來源和regression／overflow政策；不拿client tick或wall clock補token。首strike錨定100ticks窗口，第8個RATE_LIMITED按原ACK/disconnect/cleanup；SERVER_BUSY不加strike。

Normal與legacy共享**同一session的exact-next序列與rate state**；不是各開一份8token。初值1，只有exact-next；duplicate/stale/gap/invalid/exhausted均按原分類，future-gap buffering0，Long.MAX_VALUE後不wrap。Global/player/bucket和semantic消耗先後依現行`CastIntentAdmissionSemantics`／dispatcher。既有不消耗sequence的rate/BUSY/reload拒絕與已消耗的後續semantic拒絕不能改作同一情況。

Client在**實際send attempt前**推進同一outgoing sequence；return、uncertain submission、RuntimeException、Error都不rollback/reuse/retry。Suppressed／flush／沒有新press則不建立intent、不推進sequence。不新增ACK-timeout補送、network拒絕後自動重送、client sequence rewind或延遲舊press；按原policy需要的新session只能由真lifecycle發生。這不是聲稱每個拒絕後下一press必自動恢復成功。

Permit failure保原network-only immediate best-effort BUSY ACK，無main task／queued ACK／P5 work。一次enqueue成功後只task持有single-use permit；enqueue同步失敗與task finally互斥exactly-once release，secondary cleanup不可mask原primary。無future chain／另一worker。

#### C.3.4 重用 ACK，不擴 wire

現有`gramarye:intent_ack`的shape可合法用於normal operation：sequence、九個coarse admission dispositions、三個flag bits及optional expected-next全保持，actual10/18bytes、hard32、pending ACK0。`ACCEPTED`只代表root compare-and-accept已受理，不代表spawn／OPEN／Mana／ARM／damage／reward／完成。Normal精確rejection保持server-local閉集合，按原語意映射既有REJECTED／UNAVAILABLE／RATE_LIMITED等；不增加wire enum、payload或欄位。Duplicate/stale/gap等不得偽裝受理。

Main result只對same current actor/session送；malformed／unknown version／wrong direction沿原platform／P7 fail-closed政策，不為顯示文字重新抓新actor。新normal root completion／NO_RELEASE／unknown等具名結果若稍後到達，回饋只觀察其原exact outcome及合法current recipient，不補執行或修改primary結果。

### C.4 R、fresh PRESS 與唯一 tick sender

#### C.4.1 固定 registration

沿用唯一`P9ClientKeyMappings`（Dist.CLIENT、MOD bus）→`P9ClientCastInput.registerKeyMapping`及既有one KeyMapping；default `KEYSYM/R`、`KeyModifier.NONE`、`IN_GAME`、description key `key.gramarye.cast`、category `key.categories.gramarye`。保持Controls可重新綁定，不每tick強改R。不加第二cast key或raw-key sender。顯示文字可在同正式U0 literal表改為generic `Cast Spell`／`施放法術`，不叫Bootstrap，category仍Gramarye；原文字的精確literal consumer同步須明列。

Actual現況不是「沒有R」：`GramaryeClient`只直接裝P8／P11，但Dist.CLIENT auto subscribers已正式裝R及tick。Locked vanilla `Options.java:506–541`沒有R82 default；Gramarye只有此KeyMapping。第三方modpack自訂衝突未知，不掃live options／不因此改掉既有default。重綁後實際mapping／modifier/context才決定輸入。

#### C.4.2 PRESS counter，不用單 tick isDown 冒充 edge

既有H6§63.6把platform REPEAT視作個別press，現有`consumeClick()`也會取到REPEAT。U0精確取代此政策為**一個實際fresh PRESS至多一個intent；held與REPEAT0intent**。未改code前不能標已完成。

唯一input owner使用鎖版已存在的typed `InputEvent.Key`與`InputEvent.MouseButton.Post` callback **只記edge**，不send、不呼server、不mint sequence。KeyMapping的`matches(key,scanCode)`／`matchesMouse(button)`與正式modifier/conflict context辨別當前重綁；不硬讀GLFW_R當永久施法鍵。Key callback有PRESS/RELEASE/REPEAT、Mouse Post有button/action；不是新增Mixin／native hook或另一raw-sender。

規則如下：

1. 只在同一已註冊mapping的fresh PRESS進入已放開→按下edge時記一個待消費scalar count；REPEAT永不加count，RELEASE只重新允許下一個fresh edge。平台未交付的physical event不被猜造。
2. 必須計數而非單boolean或單次`isDown()`取樣：同tick前兩組PRESS/RELEASE要保兩個候選，PRESS+RELEASE全部發生在兩tick間亦不遺漏成0。Fresh scalar count硬限8，無wrap；第9個鎖overlimit。Post每tick最多32次consumeClick，先bounded drain後才決定整批send；32次都true仍未觀察empty時，保守0send/0sequence並跨tickflush，直到真見false及gate再驗，後續新PRESS才可用。不把Integer.MAX_VALUE視作可安全drain的工作量。
3. Post tick仍是**唯一sender**。它先以本正文32次上限消耗本mapping真pending clicks；只有已見empty、無overlimit且gate成立，才為bound內合格fresh-edge count產生相應一次attempt；重複按住產生但無fresh marker的pending clicks必drop。沒有native mapping click、被Mouse.Pre取消而無Post、非本mapping／modifier不匹配、或callback不合法context都不能由edge callback憑空產生intent。
4. 送前逐次重驗current gate；在8-edge/32-drain界內且整批drained時，send-count等於本回合仍合格的fresh PRESS數；超界整回合consume/drop且不補發，不能一律每tick最多1而吞掉已被正式平台交付的兩個短按，也不能一律drain所有repeat便逐個發送。Input沒有本機Mana／cooldown／catalog permit判定。
5. Context／binding替換使pending fresh count、held bookkeeping及mapping queued clicks一起失效；flush後不能重建舊PRESS。在Controls／screen變更、focus／pause、world/player/session更替與P11 suppression generation觀察到的轉換上保原保守清除責任。重綁或恢復時已held的鍵不自動算新PRESS。
6. 不用global `KeyMapping.releaseAll/resetAll`、反射／private欄位寫入、人工`click()`、raw packet攔截或第三方sender。合法event與mapping消費的精確ordering及keyboard/mouse/modifier/cancelled-mouse/rapid-short-press均以直接工具／client-input cases驗，不以本規約推定PASS。

Locked source提供的可行接點：`InputEvent.Key:214–260`、`MouseButton.Post:116–120`；`ClientHooks:667–682`發typed事件；`KeyboardHandler:444–485`在原mapping click後發Key事件；`MouseHandler:82/123–132`在取消檢查及原click後發Post；`KeyMapping:110–117/173–180`提供consume與configured matching。事件只記edge，並不取得傳送權。

#### C.4.3 Current gate、drop 與 flush

保§66原短路順序：world存在→player存在→current PLAY connection→sender session可用→無screen→window active→client未paused；再含P11 transition pending/denied/unknown suppression與其generation→no pending flush。Server另驗alive／spectator等權限；client gate不成server permission。

任一環境／P11條件失敗時置flush、consume/drop本mapping及edge候選，不建立payload／sequence。條件恢復但flush未完成的第一回合，依每tick32次界drain pending並重驗current context，仍0send；可跨tickflush直到觀察empty，完全清除後後續新PRESS才可送。即使screen在兩tick間開又關、world/player UUID看來相同、same C重入PLAY，也不能漏掉已觀察suppression或把舊pending移到新session。只在原suppression／lifecycle callback記責任；不補發、無busy replay、無UI關閉後auto-cast。

### C.5 安全玩家回饋與 client mirrors

- 成功：既有P8 applied world presentation即可，允no-text success；不把root admission ACK說成真world效果。
- 拒絕：server使用vanilla `ServerPlayer.displayClientMessage(Component.translatable(...), true)`／等價既有安全文字通道，閉集合localization key與有限參數。Locked原API進`ClientboundSystemChatPacket` overlay，無新Gramarye S2C。最小player文案可合併為not-ready／cooldown-active／rate-limited／unavailable等穩定安全組，不暴露UUID、generation、路徑、exception、raw內部enum或program identity。
- 0Mana仍驗source和transaction；不需要為了「顯示不足」把0Mana政策捏造成正debit。未來正式正成本自有exact policy，不由Bootstrap的0自動繼承。
- 目前P7 Mana mirror與legacy slot0 cooldown HUD保原read-only語意；typed logical-policy key不偽造成`SkillId`同步，也不改P7既有cooldown shape。U0不新增Mana HUD、Spell cooldown mirror、icon、screen、wheel、slot bar或renderer。
- 文字／P8的cosmetic failure不改admission、Mana、ARM、lease、damage或primary error；不得吞整個gameplay/native call來換提示成功。失去current recipient只drop回饋，不能換新connection收舊結果或補施。

### C.6 Exact scoped supersession／preservation

下列是正文必逐項登錄的範圍，不能用「U0較新」令整章失效。

| 原authority | U0精確取代／增補 | 必須保持 |
|---|---|---|
| H6§58.6–.8/.10、§58.29四P7 PLAY payload inventory | 原四個family外，增加N1 generic `cast_current_spell`一個required PLAY C2S、exact8byte/version契約；forbidden authority欄位0 | 原四ID／shapes／p7-v1；原P8/P11 phases／registrations；no new normal S2C |
| H6§58.11–.17、§59.23–.30及P11§9 D3 | 新closed NORMAL_SPELL operation共用唯一P7capture/session/sequence/rate/permit、同logic dispatch，之後進本案qualified root port | 舊legacy receiver／equipped exact reference全guard；source／auth／rate／thread／release原語意，無第二owner |
| H6§58.19 ACK | 明示原coarse ACK也可確認generic normal root admission | 9dispositions、flags/body與accepted≠release/effect/transaction，無新wire reason；no retry |
| H6§63.6、§66.7/.12的R→legacy slot0唯一sender操作 | 同一R mapping在U0採用後只送N1；未來按server policy選root | key／rebind／單tick owner／seq-before-attempt／error不rollback；legacy content與原cast_intent語意可獨立驗證，不雙送 |
| H6§63.6的platform REPEAT政策 | fresh PRESS數量＋no held repeat；typed key/mouse callback只記edge | 無第二raw sender，無本地gameplay authority，rapid delivered short presses不靠isDown猜測 |
| H6§66.6–.11；P11§7.3及§8 | 現有flush責任涵新edge bookkeeping | 全部screen/focus/pause/context/transition suppression、first recovery flush、P7seq不洗；無selection state |
| H6§67全client catalog/PLAY epoch契約 | **不取代**；normal input只讀當前合法context | (C,L)身份、preopen/maintenance cleanup、world/resource獨立generation、無新server resend／ACK／wire epoch |
| P11§11.1/.2/.6／H6§69的SkillId-only持久冷卻 | 依本amendment D4章節採typed-key schema2：真legacy SkillId與namespaced logical policy key不同variant，同sole Attachment/service/writer | 既有v1相容、migration、PENDING/ARM/NO_RELEASE、clock/source／raw／Unknown／marker／recovery與save/clone/readback；不假SkillId／hashUUID |
| 22號§3 normal entry/network排除；ordinary V0工程producer隔離 | 本U0才明示授權ordinary正常generic入口與所需production V0 wiring | V0 engineering-only issuer仍不得供正常入口；既有55列結果保留，受影響範圍有理由補驗，不重寫舊PASS／fail歷史 |

D4的精確布局與cross-field真值以本文件Part B完整正文及exact logical NBT推導為準，不另造第二組schema。已由root固定的界限為：root schema2；`key_kind:Byte=0`包真legacy entry、`key_kind:Byte=1`包canonical ASCII `namespace:path` policy key（≤128bytes）；legacy上限256保持、generic policy上限64、mixed合計320。MAX+1分別257／65／321必拒絕且不evict原義務；schema2 routed anyTag125572bytes／wholeRAW125618bytes／wholemarker87bytes。Legacy-only有效資料讀寫保schema1原bytes；首次合法加入policy義務才由同owner可信source做versioned mixed轉換，不能load即重寫舊raw。新policy_version2採當次真V0 admission所捕獲的window（default25／hard41、支援閉範圍4..41，不從coldintent起算）；legacy policy1的101語意不改，也不拿policy_version當key-family discriminator。D4精確公式／布局／來源規約及本數字已同步；不另外預填未產生的runtime證據。

本節rows驗上述MAX及D4完整bounds，不讓「只有一個Bootstrap key」變成generic設計。Schema例外不容新Attachment/store/player ownership/learn/equip/current-spell schema；P7 legacy cooldown wire不因schema2自動擴欄。

### C.7 直接接受清單：30個具名 rows

每列是實際required case，implementation後逐項填PASS／FAILED／BLOCKED與exact source/tool/artifact/receipt；目前全部**NOT_RUN**。表內集合是該列有意義的正負分支，並不要求每種故障與每個lifecycle形成無關full Cartesian product。任何fixture/model只能證其聲明的純分支，不可替真ordinary認證／native world／持久讀回。沿用同一累積報告，不為每row開campaign。

| ID／名稱 | 原A組 | Exact required fact與負例 | 最小證據角色 |
|---|---|---|---|
| U0-01 `BootstrapOwnerPrivateProductionPath` | A1 | 每玩家typed allocator→精確system provenance→M1 durable owner-private publication→ScopeD validation/C compile。無共享假system-owner、無工程fixture／legacy替身，實際readback吻合。 | catalog/codec/compiler單測＋ordinary native readback |
| U0-02 `IdempotentProvisionCollisionAndRecovery` | A1 | 重複join/cast不複製；collision、foreign/modified/Unknown/raw、pending recovery、不一致catalog fail-closed，玩家其他Spell/Draft/Manifest原樣保存，不overwrite/merge/delete。 | 精確source/primary preservation＋負例 |
| U0-03 `CurrentExactClosureAndPublication` | A1/A4 | 只server current allowed exact revision與完整closure進compiler，publication/recovery/readback合法；missing/stale program/catalog無新runtime lease／native effect，無client選identity。 | actual production closure bindings＋單測 |
| U0-04 `SingleRebindableRRegistration` | A2 | actualordinary client只註冊一mapping/defaultR/category；Controls keyboard/scancode/mouse重新綁定可用，dedicated無client linkage；無第二sender。 | source/classfile/registration＋client case |
| U0-05 `FreshPressCountNoHeldRepeat` | A2 | PRESS一次→一attempt；held/REPEAT→0新增；兩tick間短PRESS+RELEASE、同tick兩組短按計數正確；mouse取消、非mapping／modifier不匹配無intent；重綁held不補press。 | extracted input邊界＋真client輸入 |
| U0-06 `ContextConsumeDropAndRecoveryFlush` | A2/A8 | screen/chat/Controls、focus/pause、缺world/player/PLAY、session替換及P11隱藏pending皆drop；between-tick開關screen仍記suppression；首次恢復flush-only，無UI/busy replay或seq消耗。 | currentcallbacks/closedcontext正負＋client |
| U0-07 `GenericNormalIntentExactCodec` | A3 | exact ID/version/8BE bytes；roundtrip1/MAX；0/negative進semantic拒絕；7/9bytes、trailing、未知version、反向、CONFIG/PREPLAY拒絕；無任何forbidden欄位／batch。 | actualcodec／registrar＋負例 |
| U0-08 `D3ExactAuthenticatedCapture` | A3/A9 | 正常A/B由context exact channel捕捉；same UUID/newC、舊capture晚到、generation改變、stop前後、CONFIG parking都不得冒current；真session來源與actor一致。 | D3實際ownercontrols＋ordinary genuineA/B |
| U0-09 `SharedExactSequenceAndNoReplay` | A3 | legacy/normal共用同counter；1/exact-next/MAX、duplicate/stale/gap/exhausted不wrap；semantic consume與rate/BUSY不consume保持；send exception已advance且不retry/reuse；新session只真重連。 | pure semantics／handler＋native replay負例 |
| U0-10 `SharedRateAndBoundedPermits` | A3/A10 | 同bucket8/refill2/player8/global64、permits8/64及strike8/100；兩operation不能各取一份quota；MAX/MAX+1拒絕具名，network BUSY無enqueue，no autoresend。 | currentproductionowners controls＋ordinary壓力 |
| U0-11 `OneLogicHandoffAndPermitFinally` | A3 | NETWORK僅capture/permit，一enqueue，main重驗currentactor；每intent最多一rootdispatch；enqueue/dispatchthrow按原identityfinally release，無livecontext retention/futurechain/worker。 | source/classfile caller inventory＋實際handlercontrols |
| U0-12 `QualifiedNormalRootNotLegacyBypass` | A4 | N1僅入新closednormal ingress，P11 exactsource與serverpolicy選owner-private Spell；legacy cast仍原equipped owner/revision全guard；無engineering issuer／clientbackend／target permission。 | exactdispatch/context正負＋ordinarynative |
| U0-13 `CapacityAndUnavailableHaveNoQueuedCast` | A4 | currentcatalog/compiler/source缺件、capacityfull/oldlineagestilllive時closed拒絕；不保request重試、不延長age/deadline／chunk ticket、不新lease，existingroot照原。 | originalV0 caps＋ordinarynormal refusal |
| U0-14 `ZeroManaUsesRealOwner` | A5 | 真初始0Mana仍經sameexactsource/availability/ManaTransactionService；unavailable/wrongactor/overflow不被0費豁免；真0成本不杜撰正debit/refund。 | transaction/source正負＋native/readback |
| U0-15 `ManaDurabilityAndIndependentPlayers` | A5/A9 | A/B資料獨立，save/clone/reconnect/restart保存原qualifiedMana；failure/unknown不猜refund、不向另一actor扣／重做。 | formalwriter/native primaryreadback |
| U0-16 `TypedCooldownV1V2MigrationAndMixedKeys` | A6/A10 | schema1真SkillId相容讀取且legacy-onlywrite原bytes；首次合法policy義務以可信source作同owner schema2 mixed轉換；legacy與namespacedpolicy並存／canonical ordering／discriminator不同，不能hashUUID/假SkillId；write/read幂等不偷換V1欄位。 | actualD4codec/crossfield＋savedmixedreadback |
| U0-17 `TypedCooldownBoundsAndQuarantine` | A6 | D4 exact每variant/keyASCII128bytes/count/depth/logicalbytes MAX與MAX+1；legacy256／policy64／mixed320及257／65／321反例；不evict老obligation；duplicate/ambiguousroute/futureversion/raw/Unknown/marker按限定保護，不partial讀前MAX洗資料。 | actualcodec／sourcepreservation boundary＋controls |
| U0-18 `CooldownTrueReleaseArmNoRelease` | A6 | 真D20 advancingserverclock；prepare→PENDING→exactrelease→ARM；active拒絕/exactexpiry；nativeaddfalse/cancel/unknown/NO_RELEASE分開，不用constructor／ACK當release，不挪accepteddeadline。 | samecall native receipt＋owner正負 |
| U0-19 `CooldownSourceRecoveryAndPersistentRoundtrip` | A6/A8 | qualifiedsource/faultsave/clockregression、clone、同UUID重連、fullrestart的PENDING/ACTIVE/recovery義務及typedkey保持；nextnewcast不清未知義務、不RAM-only、不第二store。 | primarysave/readback＋limitedfaultcontrols |
| U0-20 `SafeFeedbackWithoutNewS2C` | A7 | success只有既有P8 appliedfact；每closedrefusal映射安全localized文字，ACKaccepted只入場；無exception/UUID/generation洩漏、新S2C/HUD；cosmeticfailure不改gameplay。 | actualmsgmapping/clientworld＋observerfailurecontrol |
| U0-21 `CatalogUpdateOnlyChangesNewCasts` | A1/A8 | 更新currentrevision後新cast取新exact，已接受root/child保oldclosure至真terminal；reload/invalidcontext照原停止，不repin／補射／重播。 | catalogpin/source＋nativeold/newrevision |
| U0-22 `OrdinaryAuthenticatedABNormalCast` | A9/A10 | 兩個正常認證ordinaryclients＋ordinaryserver的正常R→N1→source/catalog/compiler→Mana/CD→V0/P8全鏈；A/Bowner/cooldown/rate獨立，非FakePlayer／engineering入口。 | genuineordinarypair完整provenance |
| U0-23 `LogoutFaultAndReconnectBoundaries` | A8/A9 | admission前logout/fault拒絕；受理後正常／fault按P11/V0各自結果，同UUIDB不接管A因果；新castcurrentpolicy，無session重播，trueclosedwriter/readback。 | 正常及fault具名nativecases |
| U0-24 `DeathRespawnAndDimensionBoundaries` | A8 | 死亡/取消、respawn、dimension commit/cancel按原qualification，舊work與pendinginput不移交；P11 C4a健康拒絕不被變disconnect，冷卻及Mana由原owner保持。 | actuallifecycle＋boundedtypedcontrols |
| U0-25 `ReloadStopAndServerLifetime` | A8/A10 | datapack reload、dedicatedstop、sameJVM新server、fullrestart的ingressfence／pins／queue／permit／sourceclean；runtime不restore，durableMana/CD正常restore，oldtask無新slot權。 | nativeclosure/source＋sameJVM controls |
| U0-26 `SunsetExactProvenanceSelectiveCleanup` | A1/A6/A10 | gate後0newBootstrapprovision/selection/admission/obligation；liveacceptedclosure、pins、pendingpub/recovery/CD未了不可刪。僅exactsystemprovenance+identity/revision/bodyfingerprint且原owner已證可回收的route/revision/temporaryCD可移除；foreign/modified/Unknown/raw及未證source保持。 | sourceboundcontrolledfixtures＋durablereadback；非editor實作 |
| U0-27 `GenericIntentSurvivesBootstrapSunset` | A2/A3/A4/A10 | 在test-only合成未來serveractivationpolicy供應另一合法owner-privateexactroot，**相同ID/version/8Bwire/input/owners**仍可走正式admission；Bootstrapdisabled時不能fallback它。保留typedschema/runtime/P8，無clientroot/learn/equip/editorfeature。 | genericpolicy boundary fixture；只證可替換，不稱未來產品完成 |
| U0-28 `PreservationIsolationAndExactCandidate` | A10 | V0既有55結果/P11/legacycontent與receiver保持；U0actualordinarypath／engineering隔離、newC2S1/S2C0、新schema僅D4、noEntityType/renderer/plugin；來源衍生unit/GT清單、samefinalSHAchecks、原fail/history保留。 | before/afterJAR/source inventory＋affectedregression＋sixjobcandidate/main |
| U0-29 `NormalBoundedNativeQuery` | A4/A10 | U0-only filter前raw64/65、directsection最多8與超界、負座標／equal-distance/nativeorder、multipart排除、wrong/reentrantpermit/source/slot、完整後原nearest；65整次unavailable收束，無partialnearest／重掃／unboundedfallback；legacy/engineering原delegate一次。已合法spawn prefix與原lease依法清理，不假稱先前沒有受理。 | unit/source等價＋ordinary native密度／no-load/ticket觀測 |
| U0-30 `OrdinaryPositionGuardAndDamagePost` | A4/A7/A10 | ordinary/shared exactposition anchor只有一份有效wrapper、engineering無重複position註冊；共同damage-Post observer只一次，matching真nativefact才P6/P8；rootpackageclosedpermit/interface ABI，無mutable storage getter；supported dry/exact-target/K1context、actual ordinary no-ticket/loaded-only/terminal closure。 | transformedclassfile/registration＋真正ordinary native add/hit/Post/P8 |

Sunset列的「停止新義務」是停止新admission/prepare；已接受attempt原本PENDING→ARM／NO_RELEASE／recovery是既得義務的收束，不以sunset把unknown清成沒有，也不阻止其原qualifiedclosure依既有生命週期完成。清理不能用timer或UUID猜provenance，更不能先delete所有U0標籤內容再等pins歸零。Synthetic future policy只測既有generic contract可替換，不建立正式玩家selection功能。

### C.8 Phase 1 docs／static consumer 同步 inventory

以下是**目前存在的直接consumer**與其角色；路徑相對 [repository](../../)。Phase1只做必要authority/doc及其直接tests同步，不預先寫production。Phase2實際after-declarations確定後才依精確role更新source-shape counts，不泛白名單或刪guard。

| 路徑 | 目前角色／本案同步界線 |
|---|---|
| `AGENTS.md` | 登錄U0新authority scope；不把Bootstrap稱永久Starter或把implementation尚未做寫PASS。 |
| `docs/codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md` | 最小scoped mirror／preservation ledger，不全面重寫frozenbody。 |
| `docs/codex-spec/Codex_實作總規格Prompt.md` | 同scope/status/exclusions，production在authoritymain之後。 |
| `docs/codex-spec/NeoForge1.21.1_詳細實作步驟.md` | Authority→implementation/qualification順序與temporarysunset；不開始P12或editor。 |
| `docs/architecture/P5-A-server-runtime-event-kernel.md` | 以本案scope strictEOF append；e24完整prefix及更早已鎖prefix保持，不能改寫§58/§63/§66/§67歷史本文。 |
| `src/test/java/com/yo1no/gramarye/P8S2BoundaryTest.java` | `authorityAndRecoveredP7IsolationRemainExactBaseBytes`:883–942鎖H6全長/finalhash及歷次prefix；新suffix後只更新currentwholeidentity，加e24prefix fence，所有舊prefix與P7隔離原證據保。 |
| `src/test/java/com/yo1no/gramarye/P5RuntimeStaticGateTest.java` | 讀H6及composition/owner先後；只同步正式新增範圍，原startup/token/limits不放寬。 |
| `src/test/java/com/yo1no/gramarye/V0AuthoritySurfaceTest.java` | 22號R2表／historical planned ABI是原身分，不能用U0actualafter回寫舊R2表；U0新增宣告獨立actualinventory。 |
| `src/test/java/com/yo1no/gramarye/magic/network/P7PayloadRegistrarTest.java` | :31–43原四ID集合保持；:75–119 soleowner與source registrationcounts需分originalfour/N1fifth；不把所有新payload自動允許。 |
| `src/test/java/com/yo1no/gramarye/magic/network/P7S1BoundaryTest.java`、`P7S2BoundaryTest.java`、`P7S2DedicatedRegistrationTest.java` | family/surface/common-client/classloader與codec exactsets；只加具名N1及declaredinternaloperation，原fourwire負例保。 |
| `src/test/java/com/yo1no/gramarye/magic/network/P9ClientCastInputTest.java` | :108–133檢mapping/onesender/registration/flush；原OutgoingSequence形狀測試分legacy與genericN1，新增PRESS/REPEAT/mouse/shortpress/gatecontrols，不能只是改stringassert讓heldbehavior未驗。 |
| `src/test/java/com/yo1no/gramarye/P9S5BoundaryTest.java`、`P7AuthenticatedPlayerCastIngressTest.java` | legacyprovisioning與slot/rootguard保原；新genericroot另列，不把originalmethodexpected2branch取代為bypass。 |
| `src/test/java/com/yo1no/gramarye/magic/network/P7CastIntentNetworkHandlerTest.java`、`P7D3CoreOwnershipTest.java`、`P7D3LifecycleCleanupTest.java`、`P7QueuedTaskRetentionTest.java` | 原legacyhandle仍exact；newoperation證同capture/generation/permit/retention，wrongcapture拒絕；model不冒normal認證。 |
| `src/test/java/com/yo1no/gramarye/magic/network/P7ServerAuthorizationDispatcherTest.java`、`P7ServerSequenceRateIntegrationTest.java`、`P7PendingPermitOwnerTest.java`、`P7AdmissionDispositionMapperTest.java` | singleowner/sharedbudget/sequence/ACK語意。原rate/global/permitbounds與failureidentity保持；新tag不多配額。 |
| `src/test/java/com/yo1no/gramarye/P11CooldownPolicyTest.java`、`P11CooldownNbtBoundaryTest.java`、`P11CooldownServiceTest.java`、`P11CooldownRuntimeBoundaryTest.java`、`P11CooldownProjectionTest.java` | D4v1/v2typedkey/migration/source/bounds/NO_RELEASE等actualtests；legacyP7projection只trueSkillId，不能映射logicalkey成假ref。 |
| `src/test/java/com/yo1no/gramarye/P11TransitionControlTest.java`、`P11ClientTransitionStateTest.java`、`P8ClientPlayEpochAcceptanceTest.java`、`P8S4PayloadBoundaryTest.java` | 原C4a／inputblock／(C,L)／P8兩payload維持，不藉N1新增S2C／catalogACK。 |
| `src/test/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDProductionIsolationTest.java`、`ScopeDCatalogTest.java`、`ScopeDCompilerTest.java` | owner-privatecatalog/compiler證明及工程issuer隔離；Bootstrap必productionpublished，不能套fixtureauthority。 |
| `scripts/verify-p4-e0-r-configuration.sh`、`verify-p4-e0-r2q-configuration.sh`、`verify-p4-e1-configuration.sh`、`verify-p4-e2-configuration.sh`、`verify-p4-e3-configuration.sh` | 現有H6/doc與精確source-path／ownerallowlists；實作時只加真採用U0file/role/descriptor及直接負例。不能為新檔泛放整package，不能改gameplay接受。 |
| `scripts/verify-v0-engineering-artifacts.py` | actualordinary／engineeringclassfile/entry/placement；本次新normal入口兩者shared且普通可達、issuer仍alternateonly。歷史V0byteidentical結果保歷史，不把U0改碼後仍假稱final7c1payload無差異。 |

Authorityonly新測試若需新增，名稱與數量由實際source列入清單；本稿沒有新增@Test或預填將來測試PASS。Phase1最小必驗amendment字面選案全閉合、scopedsupersession、e24prefix保護、temporaryidentity/sunset與D4唯一schema規約；不能把approval當成codec/native結果。

### C.9 Source-derived inventory 與既有 required task 接點

- 目前`build.gradle:4263–4265`為JUnit Platform的`test`，未見此block另設include/exclude。每個最終candidate以實際`src/test/java`宣告及Gradle/Test engine選取建立class＋method coordinates，區分@Test、parameterized/repeated/factory展開與helper/main；不要從XML倒推expected，也不把baseline3174／386當新candidate不變的資格數字。Actual worker markers與XML/result對比source清單，漏跑／多跑／skips/errors須按真實原因處理，不能靠console PASS字串。
- 既有warnings流程：`./gradlew --no-daemon verifyPlatformBaseline compileJava compileTestJava --rerun-tasks --warning-mode=all --console=plain`；使用`scripts/verify-p9-s4-warning-attribution.py compile`及同context `unit --compile-proof ...`做WC1讀取。本機role用本機schema，不偽造GitHub run/environment。
- 單測既有入口為`./gradlew test --info --console=plain`或現有`scripts/run-p9-s3-rd1-unit-test-diagnostics.sh`；有受影響source/identity時以必要`--rerun-tasks`確保實執行，保正常dependencies，無`-x`。本draft未執行。
- `check` current dependencies在`build.gradle:4543–4550`：`verifyPlatformBaseline`、`verifyP4A3BConfiguration`、`verifyP4B2Configuration`、`verifyP4C2Configuration`、`verifyP4D3Configuration`、`verifyP4E0ResearchConfiguration`、`verifyP4E0R2QConfiguration`。這些是既有task，不是本案新Gate；其中配置consumer需要真compiledprereqs，不能裸shell失敗便繞guard。
- 既有serverboundregression：`./gradlew runGameTestServer`（stockworld由task正式生成）、`bash scripts/run-p8-s2-reload-gametest.sh`、`scripts/run-dedicated-server-smoke.sh`；源自`.github/workflows/build.yml:207–215`。舊ownedgeneratedworld/report若會碰不可覆寫fixture，先依原owner／terminal／locks封存有理由的舊生成物再新跑；不得碰canonical protectedlogs或任何`server-fixed.stdout`。
- 既有required CI仍六個具名job：`build`、`P4-A3 memory gates`、`P4-B memory gates`、`P4-C memory gates`、`P4-D memory gates`、`P4-E memory gates`。Currentworkflow的A3/B/C/D配置task分別`verifyP4A3BConfiguration`、`verifyP4B2Configuration`、`verifyP4C2Configuration`、`verifyP4D3Configuration`；E是`p4E3FixedHeapGate`。按既有dependency／各Gate正常task執行，不另加U0CIjob或縮required set。
- Phase1 authority與Phase2最終candidate各依工作單綁exactSHA之candidate/mainrequiredchecks，ordinaryno-forcefast-forward，不抹除failedtools/fixtures/results或沿用queued/in_progress作PASS。U0新source實際影響的測試需有理由重驗；既有V0 55row史料保角色，不能為了本案宣布全部原native未完成，也不能用它們充U0新增row。
