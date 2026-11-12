# DP-2 Scope D C resolver／compiler 契約

本文件記錄 [Scope D 1.0](../codex-spec/20_DP2_ScopeD_SpellDefinition與可選SkillManifest修正案.md) §§4–10 的實作。M1 的 [資料與持久契約](M1-data-and-physical-contract.md) 保有物理格式、唯一 writer、提交決定、recovery、roots 與 reclaim 的責任。`DISABLED_PENDING_COMPILER_FRONTEND_C` 是 M1 結案狀態。C 加入 package-internal compatibility gate，只有相符的 trusted compiler context 和 proof 能使 catalog 發布。普通 JAR 未配置新 gameplay descriptors，沒有玩家、network、command 或 server-lifecycle 入口。

## Exact 來源與純編譯

- `ScopeDCatalog.compile` 從已證 current capture source incarnation、owner、generation 與相符的 frontend context。Resolver 依該世代的 exact routes 和 M1 graph 取完整 closure。只有 retained 歷史 body 而無對應世代的 exact closure 時拒絕。Stable Manifest entry 先驗整份 Manifest 及每個 reachable Spell 的相容性，再產所選 closure 的 program。Manifest exact ref、entry 和 whole-root 證據留在結果的 provenance。
- `ScopeDFrontend` 提供封閉、不可變的 tagged schema 與 policy。Catalog descriptor context 由同一 snapshot 與 policy 投射，fingerprint 綁各 ABI 及完整內容 digest。Proof 同時比對 `ContextIdentity` 的結構。Descriptor 只含 schema、capability、facts、cost 和 causal metadata。無 provider callbacks、global mutable registry 或 gameplay composition。
- `ScopeDLowering` 驗 backend/category、requested capabilities、activation、settings、event facts、effect operands、creation input。Required 或有 default 的 trusted input 才可作必需讀取。兩個 expression 分支先完整驗型，再做純 fold。每個 handler 按作者順序 lower；`CreateChild` 固定 captured exact target，按具名 typed event facts 驗 child signature。Appearance 可保 typed 純視覺資料或 fallback；HOOK_ONLY 與 gameplay Unknown 拒絕。
- `ScopeDFeedback` 用 Spell creation 與具名 event handler 作 vertices。Creation 連到可能隨 creation 觸發的 handlers；block 連到 exact child creation 或可能內部 emit 的 handlers。通常外部觸發的 event 若可由 effect emit，也算內部 edge。此處只驗可由當前 event 具名同型 facts 提供的 emission。以 bounded topological traversal 拒絕 causal cycle 並回 `RUNTIME_POLICY_REQUIRED`。有限 reference SCC 若可證外部 event 隔開因果連鎖可編譯。沒有設定 Scope V runtime budget。
- `ScopeDProgram` 保留 exact typed ref、有序 closure＋每份 canonical body digest、frontend/IR ABI 和完整 registry/policy identity。Program 內是 immutable settings、optional activation contract、ordered event table、instructions/operands/constants、source ranges 及技術成本。Source map 用 exact Spell ref 與 stable handler/block ID、setting symbol；純 metadata 可對應空 instruction range。Expression 型別錯誤另附子路徑。沒有 executor、persistence ABI、latest 查詢、pin 或 cache。獨立 fatal 計數跨診斷截斷保留。
- `ScopeDLegacyCompiler` 用原 P4 exact document loader、P3 resolver/migration/analyzer/projector。IR 保 exact SkillReference、完整載體 content evidence、trigger/action 版本與 capabilities、已驗的 bool/int/long payload 欄位、node order、references／outputs／selections 與 top/node visual fallback。原 reader、canonical bytes、P9/P10/P11 runtime、template LKG 和 goldens 沿用原路徑。

## 發布前件

Catalog 的私有 issuer 以當前實體 predecessor、writer reservation 或 exact recovered prepare 核對 source、owner、candidate generation、transaction、完整 bytes digest、context 及 verification version 2。從 `Prepared` 自己的 bytes 重建 candidate；驗所有 current roots 和全部 Manifest entries。每個 exact Spell body 在同一次驗證只 lower 一次，所有相依 target 仍來自同一 image。沒有前代 proof 重用。保留的 historical revisions 仍受 M1 roots 規則保護。它們不作當前 routes。

只有相符的當次 `Prepared` 實例及 exact context 可使用 `CompatibilityProof`。它是私有建構、不可序列化的暫態值。`revalidatePending` 重建另一個 `Prepared`，重新編譯後才可發布。Stale 或 compiler failure 於 stage 前拒絕。成功驗證本身不寫入、安裝或清除 material。Storage 仍以實際 forced readback、同目錄替換及父目錄 force 決定可見性；物理失敗保持 M1 fence/recovery。

## 技術限額與量測邊界

`ScopeDLimits` 統一持有數值。以下乘數來自實際表示和工作路徑，各項上限可獨立觸發，沒有承諾全坐標同時取滿。

| 責任 | 上限推導 |
|---|---|
| Resolver | 每個 route 至多一個 exact binding，最多 96。單 session 至多 `96+24+1` 次 root checks，涵蓋全候選及 Manifest 選取。總 traversal budget 以 `121×(4×96+3×384+24)` 推導，實際走訪、SCC 兩遍、投影與 entry 都計入共用上限。無跨 root 的 closure cache。 |
| Registry | 96 typed contracts，8 descriptor argument fields，24 settings/creation schema fields，24 context keys 和 capability/conflict 座標，每 contract 1,024 static units。Canonical identity 對 SHA-256 串流寫入，獨立限制 512 KiB。 |
| Lowering | 每 Spell 至多 24 settings、24 creation symbols、96 authored expression visits 加最多 24 trusted defaults、12 handlers、96 blocks。每 expression visit 或 block 至多一個 instruction。設定深度 12。每 instruction 最多三個 expression addresses 或一個 descriptor/child 加 24 typed facts，另外計 inline arguments 和 handler/activation facts。Constants 計 literal、default 與最多每 descriptor 八個 bound arguments。所有 count 加總 checked。 |
| Source map／IR | 每 Spell `24+12+96+3` ranges，技術估算每 range `64 + UTF-8 symbol bytes`。每個 unit 依指令、operand、constant、settings、input 和 handler 等 48-byte 座標，加 scalar UTF-8 長度和 source range 計入 retained projection estimate。整 session 上限 16 MiB。這些 bytes 是保守的工程計帳座標，不是序列化格式或 JVM allocation 觀察值。 |
| Feedback | `96×(12+1)` vertices。每 handler 至多一個 creation 入口 edge，每 `CreateSpell` 一個 child edge，effect 最多連同 Spell 的 12 個 handler。所有 emission descriptor checks 另計至 `96×96×24`。工作上限為三倍 vertices/edges 加 emission checks，scratch 記保留的 vertices/edges 數。 |
| 診斷／並行 | Canonical traversal 保留首 64 條新-family 診斷，另計 omitted 與 fatal。新-family resolver/lowering 與 legacy compiler 共用全程序單一 session。Catalog 在唯一 owner 下序列化 proof preflight 和 physical publication。 |
| Legacy | 沿 legacy input 256 nodes 與 1 MiB carrier。已採 payload profile 每 side 最多三個 scalar fields，每非首 node 至多一個已驗 effect-hit reference，最多 255。Source map 含 top appearance、三個 node sides、兩邊 payload 欄位和 ref roles，最多 2,560 entries。Profile snapshot 最多 `3×(256+1)` facts，投影獨立限額為 2 MiB。 |

固定 heap 測量使用隔離子程序與已鎖 Java 21。48 MiB 新-family probe 跑 cold/fresh rebuild、96 routes／384 edges、24-entry Manifest、diamond、SCC 和 diagnostics flood。六個較大 Spell 同時產 settings、creation symbols、instructions 和 source ranges，並量測 old/current、staged/new image 與重新取證共存。32 MiB legacy probe 驗 256 nodes／255 references 及重建。計時與 allocation 是工程觀察，容量安全依 counts、bytes、hard rejection 及直接測試。無大型服、tick time、設備斷電或 Scope V runtime 資格聲明。
