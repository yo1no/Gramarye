# R1 鎖版平台來源、Mixin provider 與 compiled anchors

**SOURCE_FACT · HISTORICAL_UNAVAILABLE（歷史 Z 不可比） · R2 PROSPECTIVE V0 PLATFORM BINDING**

Canonical 平台鎖定 Minecraft 1.21.1、NeoForge 21.1.241、ModDevGradle 2.0.142。既有 artifact manifest 將該版 NeoForge sources／userdev 對應 NeoForm 1.21.1-20240808.144430。下表的生成 archive 成員可供現行 Java／平台分析。歷史權威 Z 的 expected identity 出自 P11 §2、H6 §65.9。原件缺失，生成物不得代替該身分。 [P11 §2](../../docs/codex-spec/19_P11持久化與多人修正案.md#L27) · [H6 §65.9](../../docs/architecture/P5-A-server-runtime-event-kernel.md#L25585)

## Archive 角色

| 角色 | 來源身分與用途 |
|---|---|
| 歷史權威 Z | 原路徑 `sourcesWithNeoForge_3ee2480f073edf30163679436a51e2c438b1d553_output.zip`。權威指定 regular non-symlink ZIP、9,393,663 bytes、SHA-256 `0e1dcae8e21cd8d8c656e7fe76efe1e31260cf0f956f07aa749b86992cf4fe23`。本機路徑缺失，當前 mode、bytes、digest 及子成員均不可重新觀測。 |
| 現行 generated convenience archives | 工作區的 `neoforge-21.1.241-sources.jar`、現行 `sourcesWithNeoForge_aa65…`、與封存的 `sourcesWithNeoForge_6182…`。皆為同鎖版生成 source 容器。下列 51 個原成員及 12 個新增 motion 成員在三者逐位元組相同；這個結果只適用所列生成物。 |
| 現行 named compiled Minecraft/NeoForge JAR | `build/moddev/artifacts/neoforge-21.1.241.jar` 是本文 `javap` 的唯一輸入 JAR。NeoForm compiled cache `compiledWithNeoForge_c815e16058a9aa68785805dfbf0b5b8d9fb3738c_output.jar` 同為 22,917,953 bytes，SHA-256 `7f33fced5bd55152234675a6479e60d78cdd39caf4e6ee72719bf192d0538485`。兩個 ZIP 容器摘要不同，本文三個 compiled target class entries 均逐位元組相同。 |
| NeoForge Universal／MixinExtras | Universal JarJar 載入 provider，與 ModDev 解析的同座標 cache JAR 完全相同。此身分只用於 platform mixin provider 論證。 |
| 歷史普通 Gramarye JAR | 兩份既有產品 JAR 實際不內嵌 provider。此處只是 existing artifact 盤點。R1 工程品尚無編譯／轉換／封裝證據。 |

## 現存平台物件與工作單原 51 個 members

表中的 member bytes 與 digest 取 canonical generated source archive，已在其餘兩個生成物核對。Old Z 的獨立比對結果另列於末節。

| Asset absolute path | Bytes | SHA-256 |
|---|---:|---|
| `sourcesWithNeoForge_aa65c9a2ddff6ad5024e2dcc763be1f81b1fc4c0_output.zip` | 9393659 | `fea5b1405f082e64d5fbab2a1143766fac1f94d80e1f79374d8c428bbe5fe437` |
| `neoforge-21.1.241-sources.jar` | 9393659 | `1743bda78e4fd2016eb48b09353444cfdcd2142e7c9509d2eaa03c7dba7cc606` |
| `sourcesWithNeoForge_6182dcba56bd60f6ff0059f69718f5315e5dad95_output.zip` | 9393659 | `1743bda78e4fd2016eb48b09353444cfdcd2142e7c9509d2eaa03c7dba7cc606` |
| `neoforge-21.1.241.jar` | 22917953 | `1a9fef8eb83ceb8c92023f4a8f2086744fdb46b52db6c6cd82a0a4684dac9279` |
| `neoforge-21.1.241-sources.jar` | 1790022 | `0cd2d62a061472a2b7ab62bec5d55f555d6683632500a0264ef714940fae8c2a` |
| `neoform-1.21.1-20240808.144430.zip` | 1683830 | `3fcdf0f0d115774cfceaf206a05a6dc964e2189bfd00f5b5f863e37f4b7733c7` |
| `neoforge-21.1.241-userdev.jar` | 3982201 | `4460831295e35769d0320256a25b0e5b78e5271f9fae65702a334ca457597e54` |
| `nfrt_artifact_manifest.properties` | 25912 | `f9a2b7c1757055a408c55d7374447376242bf10133d58ef54ca847a8ba681072` |

| Exact source member | Bytes | SHA-256 | 核對位置／責任 |
|---|---:|---|---|
| `net/minecraft/client/multiplayer/ClientPacketListener.java` | 126873 | `f7b2e614e700416651bc933f51691cf0079965a7e3088acfa488d6014591ddde` | 466–481 native client entity factory and packet recreation |
| `net/minecraft/client/renderer/entity/ThrownItemRenderer.java` | 2574 | `fcc9f0012884d08be38b886d5653d334f19bdcdea6f02238ce9c18694518cf0d` | existing ItemSupplier and render-registration type compatibility |
| `net/minecraft/client/server/IntegratedServer.java` | 11268 | `6ca17d0e74d8d1151e11f3a231ef901e54020c705d2568ea86359fd111f0b1c5` | 220–239 stop/halt and player teardown |
| `net/minecraft/network/protocol/game/ClientboundAddEntityPacket.java` | 5988 | `2132a53e006501369989cfdebdad4c88f8deda001866aa8330df2138a4059bab` | registry ID、numeric id/UUID、motion、position、type payload |
| `net/minecraft/server/MinecraftServer.java` | 83629 | `b906c6597095e206ade6a9db34f76993f667bdf2bfacaeca793ba9ce14e568ed` | 627 LevelEvent.Unload；724–750 stop phases；1477–1519 reload |
| `net/minecraft/server/level/ChunkMap.java` | 60373 | `8051062b0f0b25b1f64704371b0d0fb0e9bfc06dd289eae297163bee5164d648` | 227–230 isChunkTracked；258–259 visible holder；1080–1132 add/remove；1212–1222 watchers；1297–1337 tracking pair |
| `net/minecraft/server/level/ServerChunkCache.java` | 22621 | `02465fed32fb76cf2bc415c6ec58ba3d7cdf7b2580af2de6f9ba0c278c1bf306` | 136–169 getChunk；173–200 getChunkNow；227–259 ticket/future 與 hasChunk 差異 |
| `net/minecraft/server/level/ServerEntity.java` | 16328 | `93ac1ef6120a4bb81377a173486e6a85ac608cd93cde38ab9581df55d2f4062e` | 233–264 pairing/remove/data |
| `net/minecraft/server/level/ServerLevel.java` | 77451 | `8d1086b8e63b11d340853e7595a63f2f27edc0d690311c9a0d46d5098eb21925` | 764–788 native tick；893–895、930–942 addFreshEntity／onAddedToLevel；1293–1306 exact loaded index；1726–1797 tracking callbacks |
| `net/minecraft/server/level/ServerPlayer.java` | 92555 | `8e0aef1d589ae453b70ff98d6c8be37fd80c4f9480a68c468a0876c25f2ca969` | 667–685 native death cancellation；768ff hurt permissions；846–916 End/dimension；1407ff clone |
| `net/minecraft/server/network/ServerCommonPacketListenerImpl.java` | 10733 | `eccb1d592fead6173134b7ef6e40b606f8edc7f08c353dd14aad5bb2985a341a` | 68ff outer connection/host close |
| `net/minecraft/server/network/ServerGamePacketListenerImpl.java` | 96669 | `ec075b7115af7e755f209d1b459f80a61deb89f0069fdfffd8e31a2782064754` | 1202–1220 named onDisconnect and removal |
| `net/minecraft/server/players/PlayerList.java` | 41224 | `4bb1f33bbf4c056e4435d789d4b9eff9bc9010603ae325cc9f650833102fdf14` | 361–396 logout/remove/save；447–499 respawn replaces A；897–909 reload |
| `net/minecraft/world/damagesource/DamageSources.java` | 9261 | `60d17da8d58d3d1e2db618043fca3da7879d1f8bddaf86bf331b1c487d513d68` | 237–239 indirectMagic direct／causing entities |
| `net/minecraft/world/entity/Entity.java` | 139369 | `ca0586cb259e95dbd29524bf2db0ed7eaccfa5ca3a3c21492f5072c6ceef44bb` | 135、154、317 原 entity ID；338–340 discard；393–400 position；428–490 baseTick；1012–1042 inside blocks；1642–1665、1835–1839 noSave；2575–2642 Dimension；3368–3385 setPosRaw 強載風險；3464–3493 Removal/insert witness；3553–3568 Neo lifecycle |
| `net/minecraft/world/entity/EntityType.java` | 64977 | `a41c8270be7f5e14592181cba8cf135b9a2a8441b9e3badc859b60b9d35bb3f8` | 1027–1029 canSerialize；1333–1337 noSave；builder tracking/factory |
| `net/minecraft/world/entity/LivingEntity.java` | 146153 | `1bbe1b37d6f73eadefdcdfe6f5822106e410fbc80d20c163a4eb5feefb317731` | 1114–1266 hurt true/false、mutation and reward; 1371–1390 death; 1739–1760 actuallyHurt health/absorption and Post |
| `net/minecraft/world/entity/projectile/Projectile.java` | 11998 | `06a735152a5942af580144d1b93327cebc33655297e950364144be49d8213bea` | 45–63 owner UUID fallback；173–224 native deflect/hit；239–245 owner vehicle predicate；268–280 packet owner |
| `net/minecraft/world/entity/projectile/ProjectileUtil.java` | 7498 | `c20416f82ce40f83571ebc9eba74b0b53da7203f4f326ca61192e48fd7f405c0` | 24–62、102–126 block/entity collision query and nearest |
| `net/minecraft/world/entity/projectile/ThrowableItemProjectile.java` | 2414 | `1fd38dfe403c2991c14021752de4d80ef1f38d69c9daa878bd2806e33aa421c7` | constructor／default item SynchedEntityData and inheritance |
| `net/minecraft/world/entity/projectile/ThrowableProjectile.java` | 2557 | `ce9bc95785c07452f0467eff4529661f72071420fb3a2bf0462c7fabf6d8d1bb` | 42–73 vanilla tick／impact gate／motion |
| `net/minecraft/world/level/BlockGetter.java` | 8011 | `8a2bd86ee1efbc1bb6b106cd4a921e77472bd0e0ebc38c974a7cb86877bd5a88` | 65–81 terrain clip，114–170 block DDA |
| `net/minecraft/world/level/Level.java` | 47720 | `47ff78754901fad8a193669d28b260e836b1ac32ff06c7614c9900f0a5f8da7c` | 192–208 chunk get；378–385 direct block lookup；795–799 isLoaded；863–935 unbounded native entity and multipart traversal |
| `net/minecraft/world/level/entity/EntityInLevelCallback.java` | 422 | `0f04d902c66331cac0a86f17e17e168b35654c58d9dbd6ec4a6b22d80d86fe7d` | NULL sentinel and exact movement/removal callbacks |
| `net/minecraft/world/level/entity/PersistentEntitySectionManager.java` | 20144 | `12122c6cd7e2645f9e8d1f3f495298e70e68b9631da5d5ea883199718c255395` | 62–105 join cancel／one-way insertion witness；134–142 tracking；162–187 visibility；224–240 unload；394–474 movement/removal callbacks |
| `net/neoforged/neoforge/client/event/EntityRenderersEvent.java` | 10731 | `a81aa6d91b6cde4dc0969b3d0a74fa8cfb29bdf8390f538ea8ce1d4ccbdd31f5` | registerEntityRenderer renderer mapping |
| `net/neoforged/neoforge/common/CommonHooks.java` | 80546 | `1c01b3c4aabdfeb9b0bc74ddbe3f481bc1762ce8e5e4cfb550417fd7e0f0b929` | 295–335 incoming damage/event Post |
| `net/neoforged/neoforge/common/damagesource/DamageContainer.java` | 7493 | `7940c1f1c700c276f4f9f69575ae5c7e65044771b1b4f49a8bd5b1e03ed6d0c7` | 64–94 original/new damage；162–167 exact reduction behavior |
| `net/neoforged/neoforge/event/EventHooks.java` | 59299 | `83f577d8c146b0bb80e359054cc490829b3664ffbaf3b61a2cb141c6d2cc61ff` | 691–696 impact cancel event; death/reload player callbacks |
| `net/neoforged/neoforge/event/entity/EntityJoinLevelEvent.java` | 2150 | `63f3a66ce430bc4ca49ae03190733f95baad7387a85c5417450d9947acca929f` | cancellable add entry and loading-state restrictions |
| `net/neoforged/neoforge/event/entity/EntityLeaveLevelEvent.java` | 1142 | `dfc4dca4d27057da82b172f5276acd084f4238caf2c1e6079ac2ab14d73f5bff` | native leave-event contract |
| `net/neoforged/neoforge/event/entity/ProjectileImpactEvent.java` | 1456 | `342554c45667bc1a5963e1571aeb17811f27a02b1790ae66086198df3a1a33bd` | native cancellation contract |
| `net/neoforged/neoforge/event/entity/living/LivingDamageEvent.java` | 6442 | `c7f6d3d4d730c99bb55f5079232c9017c87fb4e4b5c9e78a1eb3999c45a94fe1` | 86–155 positive mutation and exact DamageSource witness |
| `net/neoforged/neoforge/event/entity/living/LivingDeathEvent.java` | 1516 | `9a54e3c7f7fa1b4dc49d9df7aef6f10ce8296c75719b74e32d5482ed3b7f75e0` | native death cancellation contract |
| `net/neoforged/neoforge/event/entity/player/PlayerEvent.java` | 16980 | `a251597e9990e596186f9997614a8950154ed9bcb1759e92cd2f0df5a92e85f6` | logout/login/dimension/clone event shapes |
| `net/neoforged/neoforge/event/level/ChunkEvent.java` | 3133 | `b589d1002c55872ed7b5048a58ef8403552c74b1dffabe24d4ff246db31eaab6` | chunk load/unload ownership |
| `net/neoforged/neoforge/event/level/LevelEvent.java` | 6819 | `03e3ce8f132bb0679d08b4e979287e93d6f5f1e5b5fac2236abc44fd824591c7` | load/unload ownership |
| `net/neoforged/neoforge/server/ServerLifecycleHooks.java` | 10932 | `5aa550efe4408ae61e25f217b207dc1a749fa1e97bd3d3694e49840476c05d65` | 116–131 stopping/stopped event order |
| `net/minecraft/server/network/ServerLoginPacketListenerImpl.java` | 13049 | `1206f36938382fe6a99fdd0382a91c114b29cddb797d227e2cc84e9d026bb6e8` | 193–209 auth-service success/missing; integrated LAN profile fallback |
| `net/minecraft/world/damagesource/DamageSource.java` | 4763 | `04a04036be569e38d7aa0775e0e3ed3d2bf5b180b8c6a5e9e5d42cd75b03908e` | 37–68 direct/causing fields, ctor and accessors |
| `net/minecraft/world/entity/player/Player.java` | 93155 | `750fc78b308139047c444bf6ac766d4afa5285154b2d69c9da20bdda046515c8` | 889–921 pre-damage and 989–1014 actual primary damage Post |
| `net/minecraft/world/entity/animal/Animal.java` | 9066 | `97712c86f1624bbcd1c954e270a2ee79cfa7e4ad09008331d6c47102efed1e8d` | 72–75 actuallyHurt delegates LivingEntity |
| `net/minecraft/world/entity/animal/Pig.java` | 10965 | `ca1ba663c7b9d114f19a1e531f81eae80cf41e2d869750be27a8d1fa9f8a98fc` | exact fixture: native Animal damage path |
| `net/minecraft/world/level/chunk/LevelChunk.java` | 33296 | `7fe6a0f8b6e46375c0a7dc387550e4ed101c33e1e42d63fe4ed48c6164b99a61` | 178ff/214ff direct block/fluid, CHECK block entity can promote 324–354; FULL witness 603–614 |
| `net/minecraft/world/level/LevelHeightAccessor.java` | 1685 | `3f22f8c195f606d3b0b80202b8d1c1b5327f95df3e7c7e83b545cd0c9ee10ff9` | 8–9 two mandatory abstract methods |
| `net/neoforged/neoforge/common/extensions/IBlockGetterExtension.java` | 3280 | `7d23b7e9cfbbb2ac93089f8d2dc44c0b90bf7b58c3e106027f0dad8b582d4c6e` | source has no mandatory extra methods; optional default aux light scope |
| `net/neoforged/neoforge/common/extensions/IEntityExtension.java` | 14603 | `3bd43e7effddda10ce86ff09d2c9b10a47233679a07d0543cca6876390ecd079` | 396–400 ordinary native only for exact P9 type; custom payload exclusive to complex spawn |
| `net/minecraft/world/level/gameevent/GameEventDispatcher.java` | 3000 | `cbb6e2e56222cde146295b85e7d268e4d448ae8175a37255b035911e68c5a386` | 21–67 native delivery loaded-only dispatcher; V0 omits projectile vibration and keeps real damage |
| `net/minecraft/server/level/GenerationChunkHolder.java` | 13192 | `e6c100571befa4fa2736b8bc03b46d73aa30bf9912d3dc5bb7f282feb628900c` | 248–255 present FULL witness from completed future via getNow |
| `net/minecraft/server/level/ChunkHolder.java` | 14918 | `2da0c42a6f8efd52ecd3db07c072f028cc943f6d8ab6d24dbbf4be66b34b83b6` | 74–93 existing ticking/ready witnesses |
| `net/minecraft/server/level/FullChunkStatus.java` | 255 | `c85edea531b091f8016b73945bc8bfb9b10553950896fcce094bb6ed3d6f2533` | status and non-inaccessible range |

## MixinExtras 0.5.3 provider

**SOURCE_FACT**　開發座標 `io.github.llamalad7:mixinextras-neoforge:0.5.3`。本機 cache 實體檔 `mixinextras-neoforge-0.5.3.jar` 為 regular non-symlink 0644，725,927 bytes，SHA-256 `9822e773bd9f42d36ed53ea3d67486207291747a7bfacc29beeb92040721bc9b`。其整份 bytes 與 NeoForge Universal `META-INF/jarjar/mixinextras-neoforge-0.5.3.jar` 相等。外層 Universal 3,544,283 bytes，SHA-256 `4ceaf2c6a1bd9520bf77f227bde8e9eeb441764ecadd33ef1a4328dcbaff4d3f`，與原 packaged dedicated 目錄的同版 Universal 完全相等。JarJar metadata 帶 group／artifact、artifactVersion 0.5.3、range `[0.5.3,)` 及 exact nested path。R1 鎖值是實際證成 0.5.3，而非任意滿足上游 dependency range 的版本。 [Provider 與載體 identity](../evidence/v0/manifest.json) · [NeoForge JarJar metadata](../evidence/v0/manifest.json)

| Class member（皆以 `com/llamalad7/mixinextras/` 起首） | Bytes | SHA-256 | 用途 |
|---|---:|---|---|
| `injector/wrapoperation/WrapOperation.class` | 872 | `abff941d9249a6f6f1f94529126e3aab1c837cbba5698829bc85cebb3513c426` | Runtime retention、METHOD target、method/at/require/expect/allow 等 annotation members。 |
| `injector/wrapoperation/Operation.class` | 377 | `71de3b327b2823374e68a4840bb8315a0666c27b72da4208a9712f69a727c29b` | Generic interface `Operation<R>`。 `R call(Object...)` erased descriptor `([Ljava/lang/Object;)Ljava/lang/Object;`。這個 provider descriptor 保留，使用端分別採 `Operation<Void>`、`Operation<LevelChunk>` 及具名原參數。 |
| `injector/wrapoperation/WrapOperationInjectionInfo.class` | 7,640 | `f314db9ab9010387f63f96c34e0dac84bc08539bc5102b8fe864e58245be6839` | Announced injection parser。 |
| `injector/wrapoperation/WrapOperationInjector.class` | 20,844 | `a9aa1e3d4d5bd2ac20ddded9488557ad0740215741fdb98f87dbea055fa29c4f` | Injection、handler、operation forwarding。 |
| `injector/wrapoperation/WrapOperationRuntime.class` | 2,626 | `3a1557dcaf3c88af7beac86ed300a1353212242c36287a8cbf10f03ae6aa562a` | Runtime call argument check。 |
| `service/MixinExtrasServiceImpl.class` | 18,822 | `9e8ed25b149d8007f7fcb13e474a9b5e0bfc7356a3992869f5d05093eb2b363d` | Existing loader service implementation。 |
| `platform/neoforge/MixinExtrasConfigPlugin.class` | 1,906 | `4bad4219b1d2b6012f7aa4ccfc83d5b82e2ff723ec87244e6deed9913f7d42b9` | Provider MixinConfigs plugin 的 onLoad 呼叫 bootstrap。 |
| `MixinExtrasBootstrap.class` | 924 | `89c9354e735f91b52f7739954a1376c452bc8d708d2a90d877f0e089c406e314` | 初始化 MixinExtras service。 |

- **SOURCE_FACT · Compile classpath**　NeoForge 本機 moddev-config 的 `libraries` 明列 exact coordinate。Canonical `build.gradle` 使用 NeoForge ModDevGradle 2.0.142，並以 `addModdingDependenciesTo` 接各專屬 source set。Locked plugin bytecode 建立 `modDevCompileDependencies`、`modDevRuntimeDependencies`，兩者引入 platform game libraries。 `ModDevArtifactsWorkflow.addToSourceSet` 各接到來源集 compile／runtime classpath。Platform 鎖版 artifact manifest 另將該座標解析至上述 exact provider cache JAR。此處驗 classpath 接線及實際 provider bytes。本輪無新增 source set，無宣稱工程 class 已編譯。 [build.gradle](../../build.gradle#L721) · [Artifact manifest](V0-platform-evidence-manifest.md) · [ModDev bytecode](../evidence/v0/compiled-anchor-manifest.json)
- **SOURCE_FACT · Dedicated / integrated runtime**　同一 NeoForge 21.1.241 的 server/client run configuration 都參照同版 provider；兩邊 importer 的 `ignoreList` 都列出本 library 檔名，不能把這個 list 當作成功載入或直接 compile classpath 證據。Runtime dependency 由 Universal JarJar 提供；封存 NeoForge installer 本身 client／server 依賴都指向同一 platform Universal。Provider manifest 為 `FMLModType: GAMELIBRARY`、`Automatic-Module-Name: mixinextras.neoforge`、`MixinConfigs: mixinextras.init.mixins.json`，沒有 physical-client 限定。既有 packaged dedicated 紀錄實見 Universal→jarinjar discovery 及 `MixinExtrasServiceImpl(version=0.5.3)`。Integrated server 在 client host 行程使用同一 provider metadata，現行證據支持裝載路徑，仍待 R1 工程品確認該行程實際載入及 transformations。 [Universal 證據](../evidence/v0/manifest.json) · [Provider manifest](../evidence/v0/mixinextras-manifest.json) · [Provider bootstrap](../evidence/v0/mixinextras-manifest.json) · [既有 dedicated discovery](../evidence/v0/historical-Z-unavailability-ledger.md) · [Native initializer](../evidence/v0/historical-Z-unavailability-ledger.md)
- **SOURCE_FACT / PROPOSED_CONTRACT · 兩種 Gramarye artifact**　已讀 ordinary product JAR entries 與 JarJar metadata。既有 P11 Mixin class 使用該 provider，Gramarye 自身只內嵌 Commons Compress，無 `mixinextras` class、nested JAR 或私有初始器。R1 工程品沿正常同版 provider，不複製第二份 provider、不以新增開放倉庫作證。後續封裝需實測 integrated 模式 class linkage、兩個 wrap anchors、verification inject、classloading 與 server-only installer 之 final SHA/artifact。 [公開證據計數](../evidence/v0/manifest.json) · [Declared annotation disassembly](../evidence/v0/mixinextras-manifest.json) · [Provider bootstrap and runtime disassembly](../evidence/v0/mixinextras-manifest.json)

## Authentication compiled anchor

**SOURCE_FACT**　Temurin 21.0.8 的 `javap -c -p -s -verbose` 讀 named compiled JAR。Anonymous entry `net/minecraft/server/network/ServerLoginPacketListenerImpl$1.class` 4,081 bytes，SHA-256 `3b81c82aeec29e2e7eaf725044e6e49dff2d58846366521b5fe72360c84bb27b`。Outer entry `net/minecraft/server/network/ServerLoginPacketListenerImpl.class` 13,551 bytes，SHA-256 `11d5f2de99f9a9f94a2679f956869c520656d904f98f6c60fd1d61c7f60613c5`。Class flags 為 `ACC_SUPER`、extends `java.lang.Thread`，class 本身未標 synthetic。兩個 capture fields `this$0`／`val$s` 標 `ACC_FINAL, ACC_SYNTHETIC`；class attributes 的 `EnclosingMethod` 為 outer `handleKey(Lnet/minecraft/network/protocol/login/ServerboundKeyPacket;)V`、`NestHost` 為 outer class。Inner `public run()V` 的唯三個比對 target 都是 `invokevirtual net/minecraft/server/network/ServerLoginPacketListenerImpl.startClientVerification:(Lcom/mojang/authlib/GameProfile;)V`。

| Anchor | Exact neighborhood | 判定 |
|---|---|---|
| Ordinal **0**，offset **73** | 35 `hasJoinedServer`，40 store result，42 `ifnull 79`，46 `ProfileResult.profile()`，49 store，69 原 receiver，72 取回該 profile，73 invocation，76 `goto 139`。 | 唯一 verified session service success。 |
| Ordinal **1**，offset **110** | 79 null branch，86 `isSingleplayer`，89 false 跳去 disconnect，103 receiver，107 `UUIDUtil.createOfflineProfile`，110 invoke，113 `goto 139`。 | Null fallback。 |
| Ordinal **2**，offset **174** | Exception table `16..139 → 142 AuthenticationUnavailableException`，150 `isSingleplayer`，153 false 跳去 disconnect，167 receiver，171 `UUIDUtil.createOfflineProfile`，174 invoke，177 `goto 202`。 | Auth-service fault fallback。 |

Outer `startClientVerification(GameProfile)V` 只依原順序寫入 `authenticatedProfile` 與 VERIFYING；outer `handleHello` 另有兩個 invocation（76、156），與 inner `run()V` exact match 分離。此區只描述本機 bytecode，未改認證流程，未處理任一 credential 或真實登入內容。 [Inner javap](../evidence/v0/compiled-anchor-manifest.json) · [Outer javap](../evidence/v0/compiled-anchor-manifest.json)

**PROPOSED_CONTRACT**　工程 wrap 只選 `run()V` ordinal 0，require/expect/allow 各 1。Static reader 另鎖整個 descriptor 的三個 occurrences 與上表 offset/neighborhood，避免 ordinal 0 的語意漂移。原 invocation 只調一次，使用 exact receiver/profile，scoped marker 在 finally 清除。Outer `startClientVerification(GameProfile)V` 的工程 observation 只接受該成功 marker，不能從 integrated mode、UUID、roster 或 `handleHello` 補授。Native exception 依原路徑，witness 無法形成時只限制相依 V0。 [工程入口契約](V0-engineering-entry-and-isolation-contract.md) · [Compiled anchor checks](../evidence/v0/compiled-anchor-manifest.json)

## Entity.setPosRaw compiled anchor

**SOURCE_FACT**　`net/minecraft/world/entity/Entity.class` 142,667 bytes，SHA-256 `4f65b7d5d4628e9f10a6a23e5a0f68814520d8019f4bc001924cd752c00eed84`。 `public final void setPosRaw(double,double,double)` 的 descriptor 為 `(DDD)V`。Offset 178 先調 `EntityInLevelCallback.onMove:()V`。Offset 184／194／201 經 `isAddedToLevel`、`Level.isClientSide`、`isRemoved` 三條 guard；失效分支跳到 230。207–225 取當前 Level 及 floor-shift 後的 X/Z chunk coordinates。方法唯一一次 `invokevirtual net/minecraft/world/level/Level.getChunk:(II)Lnet/minecraft/world/level/chunk/LevelChunk;` 在 **226**，緊接 **229 POP**、230 RETURN。先完成原移動 callback、後取 chunk 的順序成立。 [Exact javap 方法](../evidence/v0/compiled-anchor-manifest.json)

**PROPOSED_CONTRACT**　在工程品對 descriptor 鎖 1 個 invoke，require/expect/allow 各 1。普通 entity、legacy carrier、client 或非該次 exact engineering custody 的正常分支，以原 Level、chunk X/Z 原樣轉給 `Operation<LevelChunk>` 一次並回其結果。Exact V0 對應的是 P9 受限 loaded FULL witness／fail-closed 單一路徑，原 invocation 不取新票，回傳被原 POP 丟棄。作用域與 native timing、exception、實際 ticket 驗證見 P9。 [P9 原生接合](V0-P9-projectile-carrier-and-exact-A-contract.md) · [Compiled anchor checks](../evidence/v0/compiled-anchor-manifest.json)

## Motion supplemental evidence

**SOURCE_FACT**　額外查驗 12 個現行 motion source entries，包含 native movement/data/velocity packets、`VecDeltaCodec`、`SynchedEntityData`、client world/renderer、`Vec3`、closed-scene AIR/STONE 坐標及 `BlockBehaviour`。這 12 個條目另記，與工作單要求的原 51 個 members 分開。現行 named JAR 的 ThrowableProjectile／Projectile 和 Entity motion declarations 已唯讀反組譯。 [Motion source entries](../evidence/v0/member-hashes.csv) · [Projectile bytecode](../evidence/v0/compiled-anchor-manifest.json) · [Entity descriptors](../evidence/v0/compiled-anchor-manifest.json) · [P9 方程與採用判定](V0-P9-projectile-carrier-and-exact-A-contract.md)

## Old Z → generated member bridge

**BLOCKED_BY_EXACT_GAP**　H6 §65.9 指定路徑不存在。歷史 P11 最終接受紀錄已在當時記錄 missing。長期 rescue archive 再次記錄缺口，封存的是 9,393,659 bytes、SHA-256 `1743bda78e4fd2016eb48b09353444cfdcd2142e7c9509d2eaa03c7dba7cc606` 的不同生成 ZIP。以 exact filename 與 9,393,663 bytes 搜索可讀使用者目錄、Gradle cache、canonical checkout、Gramarye-Design、P11 歷史 evidence、rescue 與暫存，未找到符合 Z 的原件。歷史紀錄中幾個生成 variants 名稱相等及 access-transformer 可見性差異的記錄，無法檢定已缺失 Z 的 exact member bytes。 [P11 既有缺口](../evidence/v0/historical-Z-unavailability-ledger.md) · [封存紀錄](../evidence/v0/historical-Z-unavailability-ledger.md)

機讀表列出原 51 個預期 old-Z member paths、各 generated bytes/SHA-256 與比較狀態。Old-Z 資料欄固定 `UNAVAILABLE`。原 51 列在三份可用生成容器確證 **51/51** 相等；對 frozen old Z 的比較 **0/51 已完成，51/51 缺來源**。平台 source compatibility 的 adoption blocker 保持，原已完成的 P11 接受仍在原證據角色下保留。關閉此缺口需要 exact 9,393,663-byte Z 原件對全列重跑，或後續使用者另行明示重綁 authority。 [完整 TSV](../evidence/v0/member-hashes.csv) · [完整 JSON](../evidence/v0/manifest.json) · [唯讀身份核對程式](../evidence/v0/manifest.json)

## R2 prospective V0 evidence

R2 的平台證據只採 GRAMARYE-V0-PLATFORM-1.21.1-21.1.241-1743bda78e4f 的 sealed source、compiled anchors、provider 及 51＋12 精確成員，用於前瞻 V0。歷史小節保留其當時查核結論。R2 依具名現行來源證據直接查證，歷史 Z bytes 與逐成員等價均未判定。既有 P11、M1、C 的各期驗收輸入依原史料。既有候選採用前 Java 21 revalidation 與 Path B 持久證據沿用。
