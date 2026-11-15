# V0 R1 真認證工程入口與發行隔離

**R2 ADOPTION_CANDIDATE · UNVERIFIED**

## 已有接點與選案

- **SOURCE_FACT**　ModDevGradle 2.0.142 已使用專屬 `p11OnlineHarness` source set、兩個 DevLogin client run、獨立目錄及 frozen JAR。現有 P11 harness 是測試先例，其 JVM properties 和 mixins 不授 V0 權威。P7 已有 exact capture、listener/connection 檢查、epoch、server generation 與 reload gate。P11 `P11QualifiedSourceOwner.acquireWork` 另驗已授權來源、當前 A、native listener、活體及 C6 餘裕。[build.gradle](../../build.gradle#L1407) · [P7 session](../../src/main/java/com/yo1no/gramarye/magic/network/P7ServerSessionService.java#L84) · [P11 W](../../src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java#L348)
- **REJECTED**　只做 GameTest。適用純機制與平台故障矩陣，無法證明真認證、兩同時 session、獨立重連及正常 logout。獨立 companion mod 若需跨兩個 module 存取同 package 私有 composition 也不可取，會多開公開安裝面並有 split package 風險。
- **RECOMMENDED_AFTER_SET**　同 mod id 的 alternate isolated engineering JAR。兩種 server JAR 互斥。工程品以同一 production bytecode 做基礎，替換唯一 package-private composition leaf `V0Distribution`；加入明確 engineering manifest 的 server-only classes、fixtures 與限定平台 movement 接合；普通發布 pipeline 只依 main 輸出，無 V0 producer、engineering descriptor issuer、腳本、credentials、fixture 或配置啟用材料。Dedicated 的兩個 client 與 integrated 的遠端 peer 用同版普通 JAR及既有 P8 接收與 renderer。Host-leave 專案的 integrated host 行程需裝同一 alternate artifact 以帶起隔離 server，client-side 遞交仍使用原註冊。用 packaged launch 證明此組合的 NeoForge metadata、握手與 Jar-in-Jar 依賴可用。
- **BLOCKED_BY_AUTHORITY**　普通公用指令、新 C2S packet、debug boolean/property、多 provider 安裝器、公用 plugin lookup。V0 邏輯 intent 全由 isolated server producer 提交。

## 安裝、身分與冷工作

1. `Gramarye.handleP5RuntimeStarted` 在同一次 raw snapshot 讀取之後，保原 P8→P10→P11→P5 起動順序。當 P5 core slot 成立時建立 actor-free 的不可構造 slot capability，將不可變 startup snapshot 與同次 slot 給固定 `V0Distribution.open`。普通 leaf 固定 empty。工程 leaf 先證 artifact 封裝、只讀 fixture recipe、owned private world/catalog 路徑和 V0 config，再回傳同 slot assembly。只有 cold lane 能打開實體 catalog、確立 writer、走 publication 和準備；ready 前 V0 入場關閉。所有 worker/completion 由此 assembly 擁有，P5 不新增 worker 或 producer 回調。
2. 工程 producer 唯一觸發點為已安裝工程 leaf 捕捉的 server-main 固定 scenario step，目標從當前 native PLAY roster 觀察實際 `ServerPlayer`，用封閉角色表匹配 operator 授權的兩個測試身分。角色表只作必要條件，不能從角色、UUID 或玩家名稱獨立取得身分。Dedicated 必須 `isDedicatedServer && usesAuthentication && !isSingleplayer`，`Connection` 為非 memory 的真 PLAY。Integrated host-leave 專案可由同一隔離工程品的真認證 LAN **非 memory 遠端 peer** 建立已受理 V0 工作；host 作 native stop producer，host 本機身分不能代替 V0 root、dedicated 或雙玩家證據。
3. P5 單次 exact capture 同時確認 server main、slot、已公開的 P7 session identity（epoch+generation）、`ServerGamePacketListenerImpl`/connection/published packet listener、當前 alive/非 FakePlayer 的 exact A、P7/P5 reload gate、允許的工程角色、當前 P11 qualified source、dimension/level與 server world policy。P7 判定只查原 session；新 B、相同 UUID 或新連線不接舊值。P5 在 cold completion 後及 compare 前同樣重驗原捕獲；native release 等檢查另依 V04 exact-A 及 L1 條件。持有 A 的 provisional row 只在 P5 main，有 bound/timeout。Worker 接收 actor-free 的 owner ID、原 catalog routing、context 與唯一配置型 Manifest entry cap；selected closure 2、program logical charge 1089 由固定 profile 負責，無 server graph。
4. 接口形狀為 package-private `enum V0LogicalIntent { STANDALONE, MANIFEST_ENTRY }`。只有該 slot 所掛的 `V0EngineeringEntry.submit(ServerPlayer observedActor, V0LogicalIntent intent)` 可提交；方法即時回 package-private `V0Submission { RESERVED_COLD, NOT_READY, UNAUTHORIZED, BUSY, REJECTED }`。`RESERVED_COLD` 僅識別有界 provisional row。Server 內部查此 actor 的 fixture catalog owner 與 server-side stable route table。兩種 intent 都不帶 revision、program、owner、target、geometry、付款或旁路旗標。Root 幾何由 server 的 exact A 眼位及 Q15 方向、有限 native position/level loaded validation 取得；launch input 由可信 engine profile，child 從 captured hit/cause 和 parent typed input 按 binder 形成。
5. Fixture 只用兩個獨立 owner-private catalog、合法 Scope D 批次、allocation watermark、C trusted v1 descriptor context、durable replace/readback，附件或 legacy Store 不寫入新 schema。預先發布 r1（獨立 root、含至少兩 entries 的 Manifest、child 和第二 entry 路由），以 r2 更新 child 及一選取材料，保持 stable logical IDs。由正式 actor login 的 qualified native 資料提供 Mana。測試 universe 的起始 Mana 只使用場景自有的完整 primary `.dat`，在 server 關閉、握有 fixture directory ownership 時形成。真 A 正常登入登出先經原 owner 建立該完整檔，場景重開前只在隔離 fixture seed 將既有 `gramarye:player_mana` 填 schema_version Int 0、balance Long；保留其他 material，照既有 native read/P11 source 資格讀回確認。實驗涵蓋原 live debit/補償/save readback，無 public credit caller、直接 live Attachment 寫入或真玩家現用資料。費用 profile 以場景配方選 zero 或同一 startup 中的正費值。C whole-Manifest unavailable 由 V0 eligibility/context 的合法退役差異或單元 fault fixture 構成，禁止偽造舊 proof 作已發布 current。
6. Stop 先封 slot 和 exact callback，完成/隔離 owned worker in-flight，最後由 writer 關閉自己的檔案鎖。晚 completion 只允原 owner 收束，用新 slot token 不可能採到舊結果。原 process 可在終態前保持 pinned 守護及 writer lock；新 slot 只在舊 lock 確認關閉且新來源重開後起動工程品，legacy stop 不等待不相依 worker。

## Exact Java surface

`com.yo1no.gramarye` package-private 增量如下。Signature 為本輪草案，constructor 均限本 package 或更窄，沒有新增 public extension point。

```java
final class V0Distribution {
    private V0Distribution();
    static Optional<V0SlotBinding> open(V0SlotStartup startup);
}
record V0SlotStartup(MinecraftServer server, V0CatalogAuthority authority,
                    V0StartupLoadState v0State, SkillRuntimeService runtime);
final class V0SlotBinding {
    private V0SlotBinding();
    static V0SlotBinding create(V0RuntimeCatalog catalog, V0ColdMailbox mailbox);
    V0RuntimeCatalog catalog();
    V0ColdMailbox mailbox();
    void close();
}
final class V0ColdMailbox {
    V0ColdMailbox(V0CatalogAuthority exactOwner);
    boolean submit(V0CaptureRequest request);
    Optional<V0CaptureRequest> takeRequest();
    boolean complete(V0CaptureCompletion completion);
    Optional<V0CaptureCompletion> takeCompletion();
    void fence();
}
record V0CaptureRequest(V0CatalogAuthority authority, long ticket,
                        SkillOwnerId owner, V0RuntimeCatalog.Selection selection,
                        long inclusivePreparationDeadlineTick,
                        int maxManifestEntries) {}
record V0CaptureCompletion(V0CatalogAuthority authority, long ticket,
                           V0RuntimeCatalog.PrepareResult result) {}
enum V0Admission { ACCEPTED, ACTOR_STALE, SLOT_STALE, DEADLINE_EXPIRED,
                  WORK_UNAVAILABLE, CAPACITY, CATALOG_BUSY, REJECTED }
enum V0LogicalIntent { STANDALONE, MANIFEST_ENTRY }
enum V0Submission { RESERVED_COLD, NOT_READY, UNAUTHORIZED, BUSY, REJECTED }
final class V0EngineeringEntry {
    private V0EngineeringEntry();
    V0Submission submit(ServerPlayer observedActor, V0LogicalIntent intent);
}
```

`V0SlotStartup` 僅 composition call-local，可含 native server；`V0SlotBinding` 是 P5 exact slot 所持的 owner closure，`close` 只由 P5 本次 stop，ordinary leaf 無值。Engineering leaf 是 binding create 唯一 caller，P5 擁有 binding access 和 close。Mailbox 為精確 slot 單格 `EMPTY → REQUESTED → RUNNING → COMPLETED → EMPTY`，fence 後不得新 submit。submit/takeCompletion/fence 只由 P5 main，takeRequest/complete 只由原 cold lane。Close 先封 mailbox/owner 與舊 ticket，已在手者要到真 terminal，late result 由原 lane 收束；兩個 record 只含 scalar、不可變 logical intent 及 exact opaque catalog candidate，無 callbacks 或 native objects。Cold supervisor 與 root 共用單工 writer lane；reload/retire 優先封當次 root request 並以原 Suspension 做同一 slot 的原管理責任。Engineering entry 位於 alternate only，由 leaf 組裝，唯一 caller 為 alternate scenario producer；ordinary JAR 缺 `V0EngineeringEntry`、`V0LogicalIntent`、`V0Submission` producer 檔案與 caller。P5 核心另用 catalog 的 typed `Selection`，不引用上述 alternate-only types。工程 leaf 可內部建立 owner-local binding，禁止公開 constructor 或 supplier/registrar。

P7 的窄讀取 seam 設於已 public 的 `com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary`。現行類有 0 個 V0 members。提議 after-set 為一個 nested class、兩個 static members，沒有 public constructor 或新增 override／公開 enum。P7 boundary 轉給自身 session owner，保持 P7 state mutation 和原 protocol 獨立。

```java
public static final class V0PlayWitness {
    private V0PlayWitness();
}
public static Optional<V0PlayWitness> captureV0Play(
        V0CatalogAuthority authority, MinecraftServer server, ServerPlayer actor);
public static boolean currentV0Play(
        V0CatalogAuthority authority, V0PlayWitness witness,
        MinecraftServer server, ServerPlayer actor);
```

唯一跨包 caller `SkillRuntimeService` 的 V0 admission 分支。Witness 綁不可構造 slot capability 與 private `P7SessionIdentity`，無玩家、連線、server 欄位。P5 在 own pending/accepted exact-A row 綁 A、原 listener、原 connection。兩個讀取成員只主線執行，先檢 exact P7 actor/connection 與原 gate，再以 P7 session owner 的 identity。Routine invalid 回 empty/false，主線不捕吞 runtime invariant／VM Error。不改原 P7 slot、sequence、rate、ACK、P8 sync。以上為 `ADOPTION_REQUIRED_PUBLIC_SURFACE`，實作時應以 exact FQCN 和 descriptor 驗唯一 caller。

## 身分／封裝證據

- A、B 各自合法授權，使用兩個隔離 client/workdir 與個別 DevLogin store。只有 DevLogin/正常 launcher 讀寫 credentials。報告收角色代號、session provenance、去識別化 actor、序號及 scoped counters。禁止收錄或展示帳密、token、device code、account cache、process arguments 內秘密及真玩家資料。普通 server 子集需證沒有 client-only type links；client use ordinary renderer。
- 比對 ordinary／engineering JAR class/resources、同一 native entity/type id、registrar、mixins、module/Jar-in-Jar 及 manifest。只允許固定 leaf bytecode、明確 engineering-only mixin manifest 和列舉工程 additions 的差異。以合法普通 `gramarye-server.toml` 即使設 enabled，也驗無 producer/issuer/engine entry 能力。工程啟動所用 catalog/world/credentials 私有 root 與 raw evidence 根分離。P11 既有驗收可證同類架構曾運作，但工程 artifact 啟動與兩玩家證據仍為 `UNVERIFIED`。

## Integrated host-leave 的真認證來源

**SOURCE_FACT**　鎖版 `ServerLoginPacketListenerImpl.handleHello` 116–130 與其 authenticator `run` 187–214：integrated server 即使 authentication on 且為 TCP，也可能對 host 名稱走單機 shortcut，或在 `hasJoinedServer` null/故障後用 offline profile。單靠 `usesAuthentication()`、非 memory 與 roster 不能證這一列。Dedicated 非單機原路徑失敗即拒，此 fallback 不用來補 dedicated 證據。已唯讀核對本地 jar bytecode，成功路徑是 authenticator 對 `startClientVerification(GameProfile)` 的 ordinal 0，後面另有 offline ordinals 1、2。

**PROPOSED_CONTRACT**　只在 alternate engineering artifact 安裝兩個 private injector，僅觀察真成功路徑，完全保留原 login 協定、回呼、結果與例外。

- Package `com.yo1no.gramarye.v0engineering.mixin` 的 package-private `V0OnlineSessionSuccessMixin`，target `net.minecraft.server.network.ServerLoginPacketListenerImpl$1`，在 `run()V` 以 exact `@WrapOperation` ordinal 0（require/expect/allow=1）包住 `Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;startClientVerification(Lcom/mojang/authlib/GameProfile;)V`。Private handler `void v0ObserveSuccess(ServerLoginPacketListenerImpl login, GameProfile profile, Operation<Void> original)`。只把 exact login receiver 與該 call-local 成功 profile 的 object identity 放 private scoped marker，調原操作一次，在 finally 清 marker。沒有 token、profile table public getter、handshake data 或認證替代。
- 同 package `V0OnlineSessionVerificationMixin` target `ServerLoginPacketListenerImpl`，private `@Inject(method = "startClientVerification(Lcom/mojang/authlib/GameProfile;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)` `void v0ObserveVerification(GameProfile profile, CallbackInfo callback)`，只在上述 marker 完全匹配自己與同一 profile 時，讀 native `@Shadow @Final` 的 server/connection，記 1 個 bounded exact connection/pseudonymous role witness。Marker 和 connection table 由下列唯一工程觀察橋的 private cell 擁有。完整 native authentication 發生後還需原 P7 exact main PLAY，功能不輸入 spoofed Session。Auth thread 只發布 immutable identifier/connection identity evidence，不觸 actor/world、P5 acceptance 或 catalog。
- Alternate-only `com.yo1no.gramarye.V0AuthenticatedLoginObservation` 為 `public final`、private ctor。其前兩個 programmer-declared public methods：`public static void enter(ServerLoginPacketListenerImpl login, GameProfile profile)`／`public static void leave(ServerLoginPacketListenerImpl login, GameProfile profile)` 由 success wrapper 唯一呼叫；第 3 個 `public static void observe(ServerLoginPacketListenerImpl login, GameProfile profile, MinecraftServer server, Connection connection)` 由 verification injector 唯一呼叫。全為 engineering-only **ADOPTION_REQUIRED_PUBLIC_SURFACE**。本 class 的 package-private `static boolean consume(MinecraftServer server, Connection connection, UUID authenticatedPlayerId, V0CatalogAuthority authority)` 只由 engineering leaf 對 candidate remote session 首次真 PLAY 證據消費；slot 保 private exact admission context，直到 native exit/stop。Observe 的 marker、bound artifact slot、正訊號、exact connection/object 及合格角色均必要。另需滅活溢額、late、普通 caller 不可達的精準 bytecode gate。上限同時 2 個 role slots，auth pending 2，positive witness 於同次 PLAY 轉移、connection close、600 server ticks（目標 auth 窗口）或 stop 中的最早者清除；無 queue/backfill，因不足只拒 V0。
- Host-leave scenario 必須取得已證成功的 remote peer receipt，演練 wrong name／offline fallback（負控制）、same UUID new connection、錯 receiver、stop reset。若鎖版對應的成功 branch／callback 未能完整觀測，該列 **BLOCKED_BY_EXACT_GAP**，普通 dedicated 兩帳號硬條件維持。Artifact 證明普通 JAR 無這兩個 auth mixin、bridge 與成功觀測資源。

**PUBLIC_BRIDGE_REQUIRED**　正式候選保留原公開最小橋與非根 mixin package。Locked Mixin reservation 依 package prefix 檢查一般 class loading，且已 weave 的 handler 由 native target package 呼 Java 成員。放進工程 source set 的同 package 只解決編譯期 visibility，不能讓 transformed native caller 呼叫 root package-private 方法。詳細證明見 Java inventory；工程 mixins／auth observer 仍只存在於 alternate artifact。

對照 native 身分、平台 jar 與工程隔離的 before/after 見 [鎖版平台來源](V0-locked-platform-source-inventory.md) 及 [Java 清單](V0-java-surface-and-consumer-inventory.md)。
