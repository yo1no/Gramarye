# Gramarye DP-2 Scope V0-PROJECTILE 工程契約

**V0 ENGINEERING CONTRACT R2 · ADOPTION_CANDIDATE · NOT_IMPLEMENTED · RUNTIME_UNVERIFIED**

R2 · 2026-11-15（Asia/Taipei） · canonical `main` · commit `e49867051ec5990fc498fd2f67098dcf875d8e7e` · tree `ba0b45094c1684eccccc9aab73f14cbab6c52bab`

## 採用範圍與證據

本草案具體化 21 號 Scope V 1.0 §§2–10 的 V0-PROJECTILE 工程契約，涵蓋原十項決策與五項限定。新增 Java declarations、數值、內部資料型別及工程專屬 platform hooks 均須另行採用。既有公開產品入口、持久 schema、P8 kinds／wire、renderer 與 P11 責任維持原範圍。

- **SOURCE_FACT**　21 號規定原生命週期、真原生 fact、P5 唯一 owner、P6 ordered commit、exact A、一次 child、zero cooldown 和 exact reclaim。Canonical HEAD、main、local origin/main 及 live main 同 SHA，0/0，tracked/index clean、stash empty。唯一未追蹤路徑為既有 `logs/latest.log`。 [權威與來源](V0-source-inventory.md) · [鎖版平台來源](V0-locked-platform-source-inventory.md)
- **COMPLETED_ACCEPTANCE**　M1、C、P11 既有驗收各自在原範圍保留。Scope V 1.0 已發布。C compatibility 與 M1 durable readback 分別證其原責任。 [Scope V 發布紀錄](../evidence/v0/historical-Z-unavailability-ledger.md)
- **ENGINEERING_DERIVATION / PROPOSED_CONTRACT**　以下是一套工程採用候選，依現行 Java／鎖版平台、合規 sole writer 與自有隔離工程場景推導。所需 runtime、memory、性能、錯誤與多人證據皆為 **UNVERIFIED**。

## RECOMMENDED_AFTER_SET

| Seam | 選定結構 |
|---|---|
| Catalog → P5 | 同一 catalog owner 的短純 RAM gate 先取得 verified current／whole Manifest compatibility，再原子 provisional-pin 完整 selected closure＋適用 Manifest。P5 主線預配 root/child、event、hit/terminal/cleanup 和 P11 W，以同一 CAS 將 exact reservation 綁定為唯一 accepted-root 線性化點。單格工程 lane 做冷 I/O、compile、publication。所有 native/owner callbacks 在 gate 之外。合規 writer 先封舊 head 再 stage/write/readback。Sweep 合併 current、pending、recovery、durable 和 live inventory，最後 reader 終態才釋放租約。 [Catalog、P5 與 reclaim](V0-catalog-runtime-lease-and-admission-handoff.md) |
| P5 instance／queue | 原 server slot、clock、dispatch guard 和 owner 的定長 typed partition。用 due tick 與同 owner sequence 合併 eligible ready work，維持 legacy limits、責任和相對時序。Root/child 各 instance，共用原 exact A、cause、payer、closure、allowance、deadline。真 spawn＋OPEN 才入空 `on_create`；真未取消自然 contact＋single claim 才入 `on_hit`。首敗停、保 applied prefix。自然 `on_destroy` 只記一次工程觀測。 |
| P6／Mana | V0 專用 closed capability 與每次 release/statement typed receipt。保 legacy `void execute`、舊 resolver、ID 契約及既有 terminal enums。V-only resolver 走同一 ordered core，結果未明與 exception 保各自邊界。同次 root release 由 exact 在線 writable A 付正費，具證無 primary 才依原同次補償條件。Child/damage zero。Root package 只由 `V0P6StatementGuard` 呼叫 package-private `Optional<P11ReceiptLedger.Source> P11QualifiedSourceOwner.WorkReservation.paymentSource(ServerPlayer exactA)`，一次驗原同一 A 並取既有 immutable source proof，僅在本次 call stack；empty 明確拒絕。此一讀取涵蓋原擬獨立 eligibility check。 [P6 回執與支付](V0-P6-statement-terminal-receipt-contract.md) |
| P9 載體 | 共用 `gramarye:starter_projectile`、factory、no-save、native spawn packet、renderer，新增封閉 V0 server custody。分別證 native insertion、OPEN、cancel、claim、positive damage Post。K1 套用相同 client-visible 初始化、noGravity、dry drag 與 integration/rotation，existing tracking packets 作 native correction。Movement 保既有平台 Pre/Post，採 nonloading terrain 及原 nearest entity query。隔離工程品以精確 guard 接 `Entity.setPosRaw` 唯一 chunk-load tail。B 可作 self-exclusion 或 P11 合格 whole R，原 A 的 cause/payer 維持。 [P9 原生與生命週期](V0-P9-projectile-carrier-and-exact-A-contract.md) |
| P8 applied-only | P6 receipt 和同次 P9 applied fact 一次配對。原 `CAST_RELEASE`／`HIT`、snapshot、recipient/budget/fallback。V0 presentation 只用原 P8 已保 legacy 後的餘量；drop、缺 asset 或 transport fault 讓原 gameplay outcome 維持。 [P8 呈現](V0-P8-applied-fact-projection-contract.md) |
| 工程入口 | Same-mod alternate artifact 與 ordinary JAR 互斥。真非 memory PLAY，exact P7/P11 proof。Dedicated 以 A/B 兩個真認證身分直接驗收；integrated host-leave 只認遠端同次 authenticator 成功，offline fallback 拒。普通 JAR 無工程 entry、fixtures、工程 mixins 和 issuer composition。 [認證與封裝](V0-engineering-entry-and-isolation-contract.md) |
| Config、容量 | 唯一 `P5ServerRuntimeConfig`、`gramarye-server.toml`，同一次 raw snapshot，預設 disabled，reload 僅影響下一工程 slot。P11 W 依既有 T／dirty backpressure 加 V0 入場餘裕。 [配置、容量及 MAX+1](V0-limits-config-and-capacity-contract.md) |

Ordinary 候選增加 24 公開 types、45 明列 public／protected methods、15 個非公開類的 public overrides（其中 6 個為 P9 平台 override）、3 個 implicit interface methods、12 個 exported enum generated methods、40 個 exported enum constants，明列公開／protected constructors 增量 0。另列 4 個受 package-private outer 限制的隱含 public static member types、2 個隱含 public canonical constructors，以及 14 個 non-exported enum methods、43 個 non-exported enum constants（含 P9 Phase）。Engineering variant 另增加 4 公開 types、5 明列 public methods、4 個 exported enum generated methods、6 個 exported enum constants。採 `PUBLIC_BRIDGE_REQUIRED`，保留唯一跨 Minecraft package 的 public chunk guard 與工程專屬 auth observer。 [Java surface](V0-java-surface-and-consumer-inventory.md)

## 容量摘要

開啟後，每歸屬 default／hard 為 1／2 lineage，server 2／4，含預留 child 共 4／8 instances/carriers。每 tick 1／2 ingress 嘗試、至多 1 次接受、單份 cold outstanding、12／24 queue 工作單位、6／12 statements、8／16 native add/hurt 共計、4／8 sweeps/claims、12／24 terminal/cleanup roles。Age 16／24 movement ticks、range 16／24 blocks、原祖先 deadline horizon 24／40 ticks。Selected closure 固定最多 2 Spell，另可選 1 exact Manifest。Raw pins 每 accepted lineage 至多 3、server 預設／硬值 6／12，另 provisional ≤3；actual unique 另報。Manifest whole entries 4／8 獨立。Compact projection 每份固定 ceiling 1089 logical bytes，accepted 預設／硬值 2178／4356 bytes，另 provisional ≤1089。P8 offer 8／16、選取 recipients 8／16 per offer，受現有 P8 餘額。正費 1／4 integral Mana，另一 fixture 明示 zero；damage fixture 2 HP、hard 4 HP。每 lineage 至多一 child、深度一、每 instance 一 carrier、一次有效 claim。數值皆需工程環境的 direct qualification。

## Legacy 與 P11 保留對帳

| 路徑 | 窄幅接合 | 直接接受 |
|---|---|---|
| P5 admission／queue／lease | V0 exact lease／typed queue partition，legacy 原 quota、相對順序、accepted roots 和 RuntimeRevisionLease 保留。 | P5 static／limit workload／auth ingress，same-tick coexistence、保護已受理好工作。 |
| P6 legacy void／P7 authenticated input | 保原 resolver、legacy requestId==sourceEventId、slot、sequence、rate、ACK、D3。工程 witness 只讀原 P7 session、無 normal player cast port。 | P6S4、Mana、effect vocabulary、P7D3，錯 capability/session、重連 shape。 |
| P8 wire/fallback、P9 starter | V0 server-only mode、applied facts 與 residual；原 factory/renderer、starter geometry/timing、已接受 legacy cause 維持。 | P8S4、P9S3 inventory，雙客戶端、native／no-force、add/hurt/throw、P8 fail。 |
| P11 L1／A-B-R／F1 | 原 normal logout proof、W 和 exact detached A；N、Fop/Qctx、whole R 和具名保存各依原 owner。新 B 無 A 的 execution authority。 | 真 pre-spawn／OPEN／claimed logout、fault、重連 self exclusion、原 reward/save/readback。 |
| P11 C4a／C6／cooldown | Pin/instance/carrier 本身無 blocker。真 Fop/Qctx/H、Dd/Iw 保原。新 W 遵唯一 C6 加 V0 guard。Zero 不創／改 legacy skill cell，原 source coherence 檢查適用。 | C4a/C6、原正式 cooldown、source 復原、MAX+1、true/false/unknown native duties。 |
| P3/P4/P10、M1/C | 保 Envelope、raw/Unknown、Store/Attachment、P10 templates/LKG、C compatibility、M1 recovery。V0 lease 不成第二持久 owner。 | Scope D exact schema/recovery、P9 starter、P10 reload、P11 canonical/readback、新舊世代並存。 |

## 停止線與 V0 Definition of Done

1. 先採用 exact APIs、啟動 keys、工程資料／配額與限定 platform hooks。舊 H6 §47.3／§47.4 原 Java fence 保持。若發現需新的玩家產品決策，停止相依分支。
2. 以 exact pins、same-writer reclaim、reserved acceptance 的物理與 interleaving matrix 證無 stale path。真認證 standalone／Manifest、whole availability、舊 closure、最後 release/reclaim 分別有證。
3. 真原生 add→OPEN→on_create、自然 claim→P6 hit→queued child、on_destroy、P8 confirmed-only，含 no-force、載入、故障／reentry、首敗及 whole R/write。
4. 真 A/B、同次 exact A logout、原 deadline/cleanup、各 lifecycle 與 C4a、same-JVM slot/full restart 及 ordinary／engineering 封裝皆具證。
5. 每一配置軸／MAX+1、dense native/P8、cold/warm、retained heap/GC、同 tick service 與 legacy/P11 coexistence 達門檻。Candidate/main CI 綁各自 final SHA，保存首次 raw。 [直接驗收全矩陣](V0-direct-acceptance-plan.md)
6. 原生 sweep API 欠任意世界的 visit cap，採用候選只限可證封閉工程環境。若 exact movement anchor、零 V0 ticket、合格 target damage completion、真 LAN proof 或 fixture topology 無法證成，該列標 `BLOCKED_BY_EXACT_GAP`。保完整自然候選、原時序、真認證及 source facts，依附錄最小缺口處置。

**R2 生效依正式 authority candidate／canonical main CI 閉合。** 採用範圍為本 V0 工程契約（含 R1.1 修正）及具名 prospective platform evidence，historical Z 無原件資料。此文件候選無其他產品改動。 [R2 對帳](V0-R2-reconciliation-report.md)V0 implementation／runtime qualification、Element E、正常玩家入口、新 network、editor、renderer 與 P12 均為 **NOT_STARTED**。
