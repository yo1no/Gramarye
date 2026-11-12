# DP-2 M1 資料與持久契約

本文件落實 Scope D 1.0 §§2–10 的工程邊界。M1 不安裝新 family 的伺服器生命周期、載入器、玩家／管理員提交或發布路徑。以下儲存目錄只由明確擁有的隔離資料工作階段開啟。新 family 的正式 production current 發布保持 `DISABLED_PENDING_COMPILER_FRONTEND_C`。

## 邏輯型別與編碼

沿 `SkillOwnerId` 的 UUID principal。Spell／Manifest 各用獨立 positive long ID 與 nonnegative long revision 型別，entry／handler／block 各有 stable positive long 身分，order 由各容器的有序表決定。一般引用為同 owner 的 `SpellIdentityReference`，只存 owner／Spell family／ID。Exact refs 另含 typed revision，只用於 catalog、準備／恢復、保存根與 fork。Generation、publication ID、revision 與局部身分均不得互換。

Spell body 包含 versioned backend/category 與 capability vocabulary、0..1 tagged activation、有限 typed settings expression tree、每具名 event 一個 ordered handler stack、appearance 及 `PhenomenonSpec` hook。Handler 保 stable ID、event tag 與 event version；`CreateSpell` 僅表示 identity edge。Missing、Absent、Known、Unknown 由封閉 slot 形狀區分。非 absent 的 hook 只可保留於 Draft/raw。Formal Manifest 只持 entries（entry ID、target identity、label、group、order）、高階非 gameplay metadata 及 fork provenance；每個 entry ID 及 Spell identity 各自唯一。Draft 可保存重複項並回具名、有限診斷。Draft 的 typed base revision/generation 是 optimistic metadata，不是 catalog root 或授權。新 Draft 正式化採當次已耐久保留的 typed ID，重綁已知 self edge；不能移動的 opaque slot 保原稿並拒絕。獨立 Spell 不產生 default Manifest。

新文件是 uncompressed big-endian exact positional frame，family、formal/Draft schema、logical view、physical frame、raw location 與 descriptor payload 各有自己的 version 座標；u8 discriminators、bounded UTF-8/count 與 bytes lengths、exact EOF。外部資料先檢剩餘長度、數量與版本，再依形狀讀取；不存在額外欄位或未知 discriminator 當 default 的路徑。Raw slot 依穩定 typed location 綁 JSON/NBT family、RegistryOps/context 與原 payload bytes。Raw ingress 先驗 UTF-8／tree framing／depth／count／duplicates，保同 family/context/location 的 immutable copy。已知 visual slot 的 Unknown 與 gameplay Unknown 分離；後者使可達 root 不可用。Legacy 由既有明確 family 入口及原 readers/writers 處理，不嗅探或改寫 opaque slots。

Catalog image 是一 owner 的 exact current routes、不可變 retained bodies、catalog generation 及持久 root metadata。Index 從完整 old/candidate routes 重建，合併兩份 reverse transitive reachability 再查 affected roots 的 forward closures。有限 SCC 是結構結果，不是 runtime feedback 證明。Fork 只接同 source/owner/generation 下證成的 exact closure（Manifest 是全部 entries 聯集），按 receiver 已配置的 typed IDs 一對一重映，拒絕無法證明安全重映的 raw、錯 owner、缺件或超額。每份 fork provenance 只記 source UUID、exact reference、generation。External roots 須有完整 source 和 generation witness，已保存的 exact closure 在本切片沒有釋放入口；欠缺或 unresolved raw 時不掃除。Reclaim 不改變既有 current routes 或回收配置紀錄。

## Physical source 與唯一 writer

現行 `SkillDefinitionStore.latestReference` 由 `lastKey()` 取值，完整 carrier 由 P4 bridge 編碼。`GramaryeSkillSavedData.publishState` 只切換 RAM identity，`save()` 交給平台既有 inner carrier。18 §12 明載 async disk write 缺成功 ACK。因而不選共用 legacy carrier，也不把新 SavedData 寫入當耐久證明。Body/index 分檔再指向 durable root 可減少重寫，但增加跨檔完整性、持久排序和 orphan recovery；M1 選同目錄完整 image，只要求一個可證 durable root 切換。

工程資料 root 由呼叫端在專屬目錄提供。Root 的 `.owner-admission.lock` 保護 `<owner-uuid>` 目錄與 `<owner-uuid>.admitted` 的完整 roster。兩者不符時拒絕 admission，不猜既有 owner 為新 owner。Owner 內固定 `writer.lock`、`catalog.current`、`catalog.prepare`、`allocation.current`、`draft.current`，另有受界定的同目錄替換暫存。每 owner 一個 live writer，檔案鎖涵蓋所有操作；source/owner 鎖持有期間不替換 lock。正常 stop 關閉此 owner；沒有非同步寫入、shutdown 補寫或 dirty 當成功。正式初始空狀態須由 absence 證據產生；malformed、unsupported 或缺失已知 committed material 轉 unavailable/quarantine。Draft 屬 server-global owner-private 資料，沿同 principal／source/lock，原子更新自己的 image 與獨立 edit version，logout、clone、reconnect 無另行拷貝或 source 切換。Draft 沒有普通遊戲入口。

Reservation watermark 先以零值建立 source UUID 錨點，再建立 genesis。受鎖、可恢復、強制同步的 atomic replacement 持久保留一個 serial，才回傳供準備。Publication ID 與此批新的 revision 使用該 serial；新 typed ID 由 `serial * 256 + ordinal` 分配（ordinal 1..32，checked arithmetic）。批次失敗仍燒掉已取得的 serial，永不從 history、mtime 或最大 retained revision 重新推算。永久 watermark 是單調 no-reuse 證據，catalog 保存各 current identity 的局部 ID watermark，已移除的 handler／block／entry 不能再配置。Route 移除無公開功能。Generation 只在實際 owner catalog commit 增一，與 reservation serial 分開。Draft candidate identity 未正式配置，不得繞過 watermark。

Prepare 包住 exact base/new generation、owner/source、publication ID、context 與完整新 image 的 digest。檔案寫完強制同步、讀回核對，仍不可見。唯一提交決定是同目錄的 atomic replacement 安裝 current image，隨後同步父目錄並讀回校驗。`FileChannel.force(true)`、原子 move、directory force、checksum/readback、鎖與 exact predecessor 一起構成契約。任一先決條件不支援時拒絕，不 fallback 到 copy/普通 rename。測試證據必須含真正關閉／另程序重啟及具名 fault；程序證據的界線是已驗證的本機 filesystem primitive，不能推廣到所有檔案系統、設備 cache 或實測停電。

| 故障 | 恢復／可見性 |
|---|---|
| 無 durable prepare／部分 staging | 完整舊 current，截斷 staging 不升為 current。無法證必要 roots 時停止清理。 |
| 完整 prepare，尚未提交 | 舊 current；保留 matching pending，僅可同源重新驗證或安全撤銷。 |
| durable 決定完成，RAM／ACK 尚未接續或 ACK 遺失 | 重開後以 matching current image 恢復同代，不重發 batch 或配置 revision。 |
| move／force／readback 結果不可判定 | 當前 session fence current、prepare、發布與 reclaim；保材料。後續在取得唯一鎖、完成必要同步及 readback 後重新判定。 |
| committed frame 缺件、錯 owner/root/chain 或截斷 | Quarantine；不回空、不從 prepare 或較舊 history promotion。 |
| stale writer/base/context／晚到 completion | 在修改前拒絕；不接續或回寫異代。所有 snapshot 皆完整 old 或 new。 |

## 前端證明與容量

正式發布授權只能依不可任意 mint 的封閉 proof，綁 source、generation、exact routes/bodies digest、registry/backend/policy context 及檢核版本。M1 無 production proof issuer，無 provider registration 或外部入口。隔離測試可用 test-source 專用證明走同一 prepared-image／physical commit mechanics；不把 shape 檢查當語意相容。

唯一技術 limits owner 為 `ScopeDLimits`。單文件 64 KiB，原始 slot 12 KiB、12 slots、合計 40 KiB，raw tree depth 24／768 values。Symbol 96 UTF-8 bytes、label/text 384；settings 24、handlers 12、blocks 96、expression nodes 96／depth 12，descriptor arguments 8、capabilities 12、Manifest entries 24、diagnostics 48。單 owner 96 current routes、192 retained documents、384 graph edges、32 changed documents/batch、256 external roots；完整 traversal 計數／大小超額拒絕。Current 和 prepare 各 2 MiB（uncompressed exact frame），Draft image 512 KiB、32 drafts。目錄內每 owner 一個 in-flight writer，隔離工程環境不做全 server eager decode；最多 32 owner scopes，失去 source/roots 完整性時停止受影響寫入。

Byte caps 是可檢查的 admission 約束，各軸 maxima 不保證可同時取滿。正常 image 替換最多兩個 2 MiB 內容副本在檔案系統可見，加上小型 watermark 和獨立 Draft replace 的檔；讀回、codec 及圖驗證的 heap/work 另外以 MAX／MAX+1、few large／many small 與固定 heap 工程測量驗證。無壓縮 carrier、wire 或 Minecraft tick／大型服容量承諾。
