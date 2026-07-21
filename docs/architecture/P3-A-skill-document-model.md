# P3-A Skill document model baseline

本文件只記錄 P3-A 已落實的資料與 I/O 邊界。架構真相仍以 [P3 資料模型修正案](../codex-spec/17_P3資料模型修正案.md)、[凍結骨架](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md)、[實作總規格](../codex-spec/Codex_實作總規格Prompt.md) 與[詳細工程步驟](../codex-spec/NeoForge1.21.1_詳細實作步驟.md)為準；本文件不建立第二份完整規格。

## 文件真相與身分

- `SkillRevision` 是非負 32-bit `int`，canonical JSON 是數字。達到 `Integer.MAX_VALUE` 後，P3-C 的 allocator 必須回傳 exhaustion failure，不得 overflow、wrap 或重用。
- `SkillDocument` 是固定 revision 的唯一持久化文件真相；只保存 `DefinitionEnvelope`，不保存 descriptor、resolved definition、validation issue、diagnostic 或 runtime cache。
- `SkillDraft` 是不可施放的 immutable editing snapshot。其 `SkillId` 由呼叫者提供；鑄造與所有權驗證留給 P3-C。`baseRevision` 只是 optimistic concurrency metadata，不是正式 revision。
- `nodes` 的 List position 是唯一、零起算 `nodeIndex`。`NodeDocument` 與 `DraftNode` 不保存 index，也沒有 `NodeId`。
- P3-B 才能從 `SkillDocument` 建立可執行的 `ValidatedSkillDefinition`；P3-A 不建立可執行 projection。

## Codec、Reader 與 Writer

`SkillDocument.CODEC` 與 `SkillDraft.CODEC` 只處理 canonical shape：canonical encode、strict decode、hard bounds，且不接受 partial。它們不做 legacy `null` normalization、clamp、registry resolution、migration 或 semantic validation；malformed appearance 是 Codec error，不會變成 `Unparsed`。

實際 persistence load、import 與 repair preview 必須使用 tolerant `SkillDocumentReader`／`SkillDraftReader`。Reader 處理 legacy `null`、exact-integral intensity clamp、未知 appearance 欄位忽略、whole-blob `Unparsed`、over-hard `Rejected` 與非持久化 read facts。P3-A facts 只是 provenance；P3-B 才把它們轉成 bounded warning。

`SkillDocumentReadFailure` 是 raw orchestration 使用的最小 typed Reader failure boundary。它只保存 machine-readable `READER_REJECTED_INPUT`，從 `DataResult` 轉換時不讀取或保存 DFU message、raw tree、exception 或 stack trace。P3-B2 已在 `ResolvedSkillCandidate` 存在後建立完整 `SkillResolutionResult`，沒有使用 placeholder、`Object` 或無意義的泛型 wrapper。

`SkillDocumentWriter`／`SkillDraftWriter` 對 `Default`／`Decoded` 輸出 canonical shape。`Unparsed` 只可無損寫回相同 value family：JSON→JSON 或 NBT→NBT；跨 family 回傳無 partial 的 `DataResult.error`。`Rejected` 不保存 raw，top appearance 寫成 `{}`，node override 省略。

## Appearance shape

Appearance 是 partial typed object。ARGB canonical 形式是固定八位大寫 `0xAARRGGBB`，Java 保存 32-bit `int` bit-pattern。`intensity_milli` 以整數保存，`1000 = 1.0`，hard range 是 `0..10000`。sound、particle、trail Profile 各自是三態：欄位缺失為 inherit、`{"mode":"disabled"}`、或 `{"mode":"specified","id":"namespace:path"}`。

Top state 是 `Default`／`Decoded`／`Unparsed`／`Rejected`；node override 對應 `None`／`Decoded`／`Unparsed`／`Rejected`。單一已知欄位若無法可靠解碼，整個 appearance blob 進 `Unparsed`，不做部分 typed salvage。Unparsed raw snapshot 對 constructor source 與 accessor 都做 JSON/NBT deep copy，支援相符的 `RegistryOps` wrapper，且 `toString()` 不含 raw tree。

Clamp 只適用於具有有序範圍的純量。可精確證明為整數的 `intensity_milli` 才可 clamp；fractional、NaN 或 infinity 使 whole blob `Unparsed`。ARGB 是 bit-pattern，不做 clamp；越界或無法解析時 whole blob `Unparsed`。

未知 appearance 欄位採刻意有損政策：Reader 忽略、只記錄不含欄位名的 fact，canonical Writer 會剝除。可被舊版剝除的純提示欄位可接受此風險；必須跨舊版 round-trip 保留的新欄位，必須提升 skill-level schema version，並由 P3-B 定義 migration／forward-compatibility。

## Read report 與 bounds

Read report 不屬於模型 equality，也不進 JSON/NBT。Fact 只含固定 code、location kind、optional node index 與 optional known appearance field；不含自由字串、未知欄位名、raw 或 stack trace。`MAX_READ_REPORT_FACTS = 1024`，達 cap 後停止收集並設定 `truncated=true`，不使文件讀取失敗。

全域 document/draft root depth 是 1，hard maximum 是 64；strict Codec decode、Reader 與 Writer 輸出使用同一座標。Appearance subtree root depth 是 1，quarantine hard maximum 是 depth 32、node count 1024；key 不算 node。達上限仍合法，超過時 traversal 立即短路。Global 超限拒絕整份文件；global 合法但 appearance-relative 超限只形成 `Rejected`。Whole-document 與 raw-payload byte ceilings 已定義，但 P3-A 不以 tree 或 `toString()` 假算 bytes，實際 enforcement 留給具有原始 bytes 的 I/O boundary。

## 整份文件拒絕白名單

只有下列情況可使整份 `SkillDocument`／`SkillDraft` 讀取失敗：

1. root representation 不受支援，或 parser 無法建立安全 root tree；
2. 未來 raw I/O boundary 判定整份輸入超過 hard byte limit；
3. global parsed-tree depth 超過 hard limit；
4. gameplay／draft outer required field 缺失或型別錯誤；
5. nodes 超過 hard count；
6. `DefinitionEnvelope` 外殼無法解碼；
7. strict canonical Codec 收到 non-canonical input；
8. Writer 被要求把 `Unparsed` raw 寫到不同 Ops family；
9. production invariant 或 unsupported `DynamicOps` family。

Unknown Trigger／Action type ID、appearance 缺失或 legacy null、appearance 已知欄位錯誤、ARGB 錯誤、profile tagged-state 錯誤、可 clamp intensity、appearance quarantine 超限、未知 appearance 欄位，以及語法正確但日後 registry 缺 entry 的 profile ID，都不得拒絕整份文件；appearance 必須落入四種 storage state。

## 後續階段邊界

- P3-B：skill-level migration contract、registry resolution、descriptor/cross-node validation、read facts→warnings，以及可重建的 runtime projection。
- P3-C：submission、SkillId 鑄造／所有權與 revision allocation/exhaustion。
- P3-D：尚未接持久化的 store domain behavior、pin/unpin/reclaim。
- P4：Overworld `SavedData` 與玩家 Attachment。

P3-A 不實作上述責任，也不建立另一個 persistent store。
