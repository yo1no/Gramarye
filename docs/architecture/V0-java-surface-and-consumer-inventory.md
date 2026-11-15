# V0 Java surface 與直接 consumers

**R2 EXACT JAVA FENCE · NOT_IMPLEMENTED**

## R1-05　計數與證據範圍

「Exported API」指 Java 能從其他 package 命名的 type/member，含公開 type 的 enum 生成 members。每項以宣告者 scope 與 erased JVM descriptor 唯一識別。表中的 ordinary 增量也進入 engineering JAR。Engineering-only 是 engineering 相對於 ordinary 的額外增量。Mandatory override 以 H 標示其必要性，仍屬 D 的既有計數。所有 boundary members 的新 Java source 宣告與實際 ABI 須分別驗收。

| 類別 | 計 exported API | Exact declaration fence | Ordinary JAR scope | Engineering JAR scope |
|---|---|---|---|---|
| A　可匯出的 public/protected top-level 與 nested types | 是 | 逐 type，包含 nesting、permits 和 ctor visibility | 計 shared | 計 shared ＋ engineering-only |
| B　explicit public/protected constructors | 依 declaring type 可見性 | 逐 descriptor | 計 shared | 計 shared ＋ engineering-only |
| C　public types 上 explicit public/protected methods | 是 | 逐 descriptor | 計 shared | 計 shared ＋ engineering-only |
| D　non-public concrete types 上 explicit public/protected overrides | 否 | 逐 descriptor，保留 override provenance | 計 shared | 計 shared ＋ engineering-only |
| E　介面隱含 public abstract methods | 介面可匯出才計 | 逐 descriptor，按介面聲明計一次 | 計 shared | 計 shared ＋ engineering-only |
| F　enum `values()`／`valueOf(String)` | enum 可匯出才計 | 逐 descriptor，與 explicit methods 分開 | 計 shared | 計 shared ＋ engineering-only |
| G　public enum constants | enum 可匯出才計 | 逐 field descriptor，保宣告順序 | 計 shared | 計 shared ＋ engineering-only |
| H　平台 mandatory overrides | 取原 C/D 分類 | 每筆附 mandatory 標籤，不重複計 | 在對應 C/D 中 | 在對應 C/D 中 |
| I　宣告本身為 package-private | 否 | 逐具名 type／method／constructor | 計 shared | 計 shared ＋ engineering-only |
| J　private methods／constructors | 否 | 按已明定的精確 descriptor 列，opaque ctor 保留 visibility fence | 計 shared | 計 shared ＋ engineering-only |
| K　anonymous／synthetic methods | 否，產生的新 bridge 受獨立稽核 | 不採用新匿名 callback／synthetic seam。現有 anonymous target 單列鎖證，編譯器產生的 records methods 另列 `K_RECORD`，之後逐實際 classfile flags 對帳 | 核對 retained 與編譯器生成物 | 加驗注入 anchors／生成物 |

- Before 為 canonical `e49867051ec5990fc498fd2f67098dcf875d8e7e`。現有 owner 及 protected/private descriptors 取已封存的 C ordinary JAR，`596ec935fbe604498915a941cd1fbe543abff7ad41ef9ba2a96f7e8507aee81b`，共 2,765 個 Gramarye class entries。本次用 `javap -p -s` 唯讀檢查指定現有 owners，並以空 production diff 證明 C 正式 source 到 base 的連續性。該 JAR manifest commit 為 C qualification 的先前 source snapshot `4342ce946dae94d345ec6dd4cb954e169874b3e5`。Scoped 現有 owner 另證與該 manifest commit 間 source 零差異，因此只將 classfile descriptors 歸於這些吻合 owners。Before 無 V0 engineering variant。 [Before machine inventory](../evidence/v0/java-surface-before.json) · [C 到 base production diff](../evidence/v0/manifest.json) · [Affected owner source diff](../evidence/v0/manifest.json) · [JAR manifest](../evidence/v0/manifest.json) · [JAR entry list](../evidence/v0/manifest.json)
- After 是設計中的具名宣告與 exact caller fence。每筆含 FQCN、descriptor、generic signature、visibility、declaring type／constructor visibility、artifact、caller、retention、failure／Error 與直接 consumers。Opaque owner-local private ctor 可見性受限，資料儲存引數與未規定的 private helpers 留受同一 owner 約束。彼此有依賴的 package methods 直接列精確參數及回傳。未生成 candidate JAR，因此 compiler synthetic、transformed methods、source-set isolation 和兩個實際 JAR 的 member／entry fences 保留為實作驗收。 [After machine inventory](../evidence/v0/java-surface-after.json) · [計數 JSON](../evidence/v0/java-surface-totals.json) · [唯讀產生器](../evidence/v0/manifest.json)

## R1.1　Classfile flags 與外部可見性

Classfile declaration flags、外部 package 有效 Java 可見性、隱含／明列與 artifact 都分欄。Package-private outer interface V0StartupLoadState 的 Disabled、Unavailable、Ready、Invalid 為隱含 public static member types。Ready(V0StartupLimits)／Invalid(V0ConfigFailure) 的 canonical constructors 為隱含 public。外部 package 無法經 outer 命名。這 4 個 type 與 2 個 constructor 均不計 I，單列 declaration flags，且不增可匯出 A 或明列 B。

SkillRuntimeService.V0CarrierBinding.Phase 是同 owner 的 private member enum。七個 public fields 依序 RESERVED、ADDING_INERT、OPEN、OBSERVED_PENDING_CLEANUP、CLAIMED、CLOSING、CLOSED。values() 與 valueOf(String) 為生成 public static methods，隨 private enum 計入 non-exported F/G。Phase 的私有型別、生成 ctor 與 synthetic helper 另列 compiler metadata，未增加 J 所計明定 descriptor 的產品宣告或跨 owner synthetic seam。H 是既有 C/D 的平台標記，在此全屬 D。

樁類別的 Temurin 21 classfile manifest 只證這兩處。其他 exact after-set 沿 R1，實際 ordinary/engineering JAR 全量計數由實作後驗證。 [R1.1 Java ledger](../evidence/v0/java-surface-after.json) · [計數](../evidence/v0/java-surface-totals.json)

## R1.1　Ordinary／engineering after-set

| 類別 | Ordinary 設計增量 | Engineering-only 額外設計增量 |
|---|---:|---:|
| A　Public types | +24（2 top-level、22 nested） | +4（2 top-level、2 nested） |
| B　Explicit public/protected constructors | 0 | 0 |
| C　Declared methods in public types | +45 | +5 |
| D　Explicit overrides in non-public concrete types | +15 | 0 |
| E　Implicit public abstract methods | +3，均在 package-private interface | 0 |
| F　Enum methods，匯出／非匯出 | +12／+14 | +4／+4 |
| G　Enum constants，匯出／非匯出 | +40／+43 | +6／+7 |
| H　Mandatory platform overrides，已計在 D | +6 | 0 |
| I　具名 package-private types／constructors and methods | +50／+80 | +7／+2 |
| 個別列錄，非匯出隱含 public static member types／public canonical constructors | +4／+2 | 0／0 |
| J　已明定 descriptors 的 private methods／constructors | +14 | +6 |
| K　設計生成邊界 | 110 個 deterministic record accessors／value methods。新增 anonymous callback 與設計要求的 bridge synthetic 皆為 0 | 0 record generated methods。新增 anonymous callback 與設計要求的 bridge synthetic 皆為 0 |

| Ordinary 公開接面 | A | C | D | E | F 匯出 | G 匯出 | H（含於 D） |
|---|---:|---:|---:|---:|---:|---:|---:|
| Catalog facade／authority | 17 | 30 | 0 | 0 | 6 | 20 | 0 |
| P6 bridge／capability 及 P6 V0 internal interfaces | 6 | 12 | 9 | 3 | 6 | 20 | 0 |
| P7 exact PLAY witness | 1 | 2 | 0 | 0 | 0 | 0 | 0 |
| P9 position chunk guard／platform overrides | 0 | 1 | 6 | 0 | 0 | 0 | 6 |
| P8、P11、P5、root startup | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| Ordinary 合計 | 24 | 45 | 15 | 3 | 12 | 40 | 6 |
| Engineering-only catalog assembly | 3 | 2 | 0 | 0 | 4 | 6 | 0 |
| Engineering-only auth observer | 1 | 3 | 0 | 0 | 0 | 0 | 0 |
| Engineering-only 額外增量 | 4 | 5 | 0 | 0 | 4 | 6 | 0 |

P6 Bridge public nested **15→19**，原 outer 的唯一 `void execute` 保持，新增 `executeV0`。P6 capability public nested **0→2**。Package-private `V0ManaAccountAccess` 的五個 interface overrides、`V0EffectResolver.resolve`、具名 `V0NativeCommitAdapter` 的三個 overrides 為 D 的九筆。E 的三筆全是 package-private `V0EffectCommitPort` 上的隱含 public abstract methods。其餘新 sealed interfaces 為 closed marker types，implicit abstract methods 為零。P9 `P9StarterProjectile` 平台 overrides **6→7**，其唯一新增 public member 為 `setLevelCallback`。其新增 package-private `v0InitialVelocity(IIII)Lnet/minecraft/world/phys/Vec3;` 只有同類 V0 server constructor 呼叫。Package-private `P9V0LoadedBlocks` 的五個 mandatory overrides 為 D 與 H 的另外五筆。 [Catalog](V0-catalog-runtime-lease-and-admission-handoff.md) · [P6](V0-P6-statement-terminal-receipt-contract.md) · [P7／engineering entry](V0-engineering-entry-and-isolation-contract.md) · [P9](V0-P9-projectile-carrier-and-exact-A-contract.md)

`P11QualifiedSourceOwner.WorkReservation` 只新增 package-private `Optional<P11ReceiptLedger.Source> paymentSource(ServerPlayer exactA)`，JVM descriptor `(Lnet/minecraft/server/level/ServerPlayer;)Ljava/util/Optional;`。唯一 caller 是同 root package 的 `V0P6StatementGuard`。一次核對原 W 與 exact A canonical writer，同次取得既有 immutable proof，empty 判拒。Source proof 只在該 debit／補償幀保有 data identity、account key、epoch、version 與 disposition。此讀取無 checked exception，owner invariant fault 依原型傳遞。曾提出的 `qualifiesPayment(ServerPlayer)` 單一 boolean 無法傳回同次 source，兩次查詢會產生可觀察落差，因此 R1 採單一 proof 方法。Existing legacy `qualifies(ServerPlayer)` descriptor 與 actor qualification 責任保持。 [P6 付款及補償](V0-P6-statement-terminal-receipt-contract.md)

## R1-06　PUBLIC_BRIDGE_REQUIRED

Locked NeoForge 的 Mixin 為 `net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7`，JAR 1,494,751 bytes，SHA-256 `1d45cfe3ae4a2eab38dc74276803748cf799088986260d6d912e50ddb35d15c5`。唯讀檢查 precise classfile provenance 與 bytecode。 [Classfile hashes](../evidence/v0/mixinextras-manifest.json) · [Locked platform](V0-locked-platform-source-inventory.md)

1. **Mixin config 的 root package 會阻擋原 mod classes 載入。** `MixinInfo` constructor offsets 85–96 直接串接 `config.getMixinPackage()` 與 entry。既有配置 package 是 `com.yo1no.gramarye.mixin`，以 full name 列入原 config 只會將兩者串接。新增配置若設 `com.yo1no.gramarye` 則 `MixinConfig.onSelect` offsets 45–80 正規化為 `com.yo1no.gramarye.`。`packageMatch` offsets 6–18 對所有 class name 作 prefix match，`MixinProcessor.applyMixins` offsets 312–430 在 matching config 選擇後拒絕該 package 普通 class load，普通 `Gramarye` 等命中 `IllegalClassLoadError` 路徑。`prepareMixins` offsets 5–63 也會在 package 空且有 entries 時記錄 orphaned mixins 並返回。這使 root relocation 破壞同一 engineering artifact 的正常啟動。 [Existing config](../../src/main/resources/gramarye.p11.mixins.json#L4) · [MixinInfo bytecode](../evidence/v0/mixinextras-manifest.json) · [MixinConfig bytecode](../evidence/v0/mixinextras-manifest.json) · [MixinProcessor bytecode](../evidence/v0/mixinextras-manifest.json)
2. **轉換後 caller 的 runtime package 獨立決定 Java access。** 同 package source 加 production classes 至 compile classpath 時可以命名 package-private members。三個 handler 經 Mixin 併入 Minecraft target，`MixinApplicatorStandard.mergeMethod` offsets 188–195 把 handler 插入 target methods。`Entity`、`ServerLoginPacketListenerImpl$1`、`ServerLoginPacketListenerImpl` 的 bytecode 呼叫 `com.yo1no.gramarye` helper 時，各為不同 runtime package。Mixin 類的原 source package 無法賦予 target JVM package-private access。禁止靠改原 access transformer、runtime reflection、同名新模組或另一跨包旁路解此依賴。 [MixinApplicatorStandard bytecode](../evidence/v0/mixinextras-manifest.json) · [Exact native anchors](V0-locked-platform-source-inventory.md)
3. **保留狹義 bridge。** 工程 mixins 留 `com.yo1no.gramarye.v0engineering.mixin`，config 使用此相對 prefix，targets 仍用原 native class／descriptor。`@Shadow` 對綁定欄位、`@WrapOperation` 的 owner／descriptor、`@Inject` anchors 均沿已鎖證的 typed declarations。普通與工程 JAR 共用 `public static boolean P11P9TrackingCleanup.guardV0PositionChunk(EntityAccess,Level,int,int)`，唯一具名 caller 是併入 `Entity.setPosRaw` 的 engineering wrapper，ordinary 裡無 producer。Engineering-only public final `V0AuthenticatedLoginObservation` 的 `enter/leave/observe` 只有兩個登入 mixins 的 exact transformed sites，保 private ctor、package-private `consume`。此方案仍為同一 mod identity 的替代 artifact，沒有 module/split-package deployment。Server anchors 不新增 client 型別依賴；ordinary absence、每個 target 恰好一次、dedicated／client linkage、source set 實際編譯與轉換，當工程 candidate 存在時以兩個 JAR、classfile anchors 及相應 direct cases 驗收。 [Entry 與 artifact 隔離](V0-engineering-entry-and-isolation-contract.md) · [P9 bridge](V0-P9-projectile-carrier-and-exact-A-contract.md) · [直接驗收](V0-direct-acceptance-plan.md)

## 直接消費者

- `ScopeDProductionIsolationTest` 驗 named facade／alternate issuer references，保持 `ScopeDCatalogTest`、C exact compiler 與 proof readers 的私有 boundary。
- `P5RuntimeStaticGateTest`、`P5RuntimeConfigurationTest`、`P5RuntimeHardLimitWorkloadTest`、`P8S2BoundaryTest` 檢查 exact descriptors、owner field、queue 上界、同一 configuration tuple、普通隔離與 H6 bytes。
- `P6S4BoundaryTest`、`P6EffectVocabularyTest`、`EffectCommitPlanTest`、`ManaBoundaryTest` 保存 legacy bridge descriptors 與 internal vocab，檢查新 V0-only call、結果和正付款的唯一 attributed write。
- `P7D3CoreOwnershipTest`、`P9S1BoundaryTest`、`P9S3DirectConsumerContractTest`、`P11ExactCleanupTest` 對帳 exact witnesses、preserved receivers、loader anchors、ordinary absence、原 delegate 結果及 legacy behavior。
- `P8S4PayloadBoundaryTest`、`P8PresentationRuntimeTest` 對帳同 tick legacy residual 與 source confirmation。兩個 retained packet roles 提供 negative controls。
- `P11AcceptedWorkBoundaryTest`、`P11AcceptedWorkObservedHitTest`、`P11SourceProvenanceTest`、`P11TransitionControlTest`、`P11ControlBudgetsTest` 分別驗原 W/N/Fop/Qctx/Dd/Iw/H、save/readback、來源 conflict 與付款。 [V0 direct acceptance](V0-direct-acceptance-plan.md)
