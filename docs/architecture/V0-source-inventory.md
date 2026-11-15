# V0 R1 基底、權威與來源清單

**SOURCE_FACT**　2026-11-14（Asia/Taipei），canonical checkout `Gramarye`。

| 欄位 | 唯讀觀測 |
|---|---|
| Remote | `yo1no/Gramarye`，origin main 查詢為 `e49867051ec5990fc498fd2f67098dcf875d8e7e` |
| Local branch／HEAD／main／origin/main | `main`，SHA 均同上 |
| Tree／sole parent | `ba0b45094c1684eccccc9aab73f14cbab6c52bab`／`d4ab92cafea5c6728df83858aca83bb030a4b071` |
| Subject | `docs: adopt DP-2 Scope V runtime authority` |
| Status | ahead/behind 0/0。Tracked/index clean，stash empty。只辨認既有 untracked `logs/latest.log` 的 Git 路徑。 |

## 正式權威

| 文件 | 直接核對條款／身分 |
|---|---|
| AGENTS.md | 全文，authority precedence、stop rule、shared owner invariants 和工程交付。SHA-256 `1b5812de1b1cc83e1119552635d423af048bd2704ab1e385466867822183af6f`。 [AGENTS.md](../../AGENTS.md) |
| 21 Scope V | 全文 §§1–10，§2 exact captured admission、§3 identity、§4 十六列 lifecycle、§§5–8 fee/native facts/child/leases、§§9–10 replacement 和實作前件。47,390 bytes，SHA-256 `73de65984bc01bab88a45fc42ba92238e95d7efeaf6e4d64b030ae92a9dd2a26`。 [21](../../docs/codex-spec/21_DP2_ScopeV_SpellRuntime與V0Projectile修正案.md) |
| 20 Scope D | 全文 §§1–11，§4 whole Manifest、§§5.3/6 exact compiler/C、§8 HOOK_ONLY、§11 scoped ledger。SHA-256 `e4b824bdaa79729c4e20894a03b7cba58577cd6358432ddb4a9328dff96baea9`。 [20](../../docs/codex-spec/20_DP2_ScopeD_SpellDefinition與可選SkillManifest修正案.md) |
| M1/C 工程契約 | 全文，物理身份、耐久根、recover、supersession guard、current proof、C schema/typed operand、checked projection caps、release scope。SHA-256 `c4f7a089be446768f4784b0e86a797c793c7ffdd8a0e4c42dc353bd2b45ca896` 與 `8a2fef3f4c48d4d07fe6b147ffec9db5587c4147ba51259acd72a6a1d41291`。 [M1](../../docs/architecture/M1-data-and-physical-contract.md) · [C](../../docs/architecture/C-resolver-compiler-contract.md) |
| 19 P11 | §3、§§4–6 L1/A–B–R/F1、§§7–9 C4a/D3、§§10–11 C6/正式冷卻、§12。W/N/Fop/Qctx/Dd/Iw/H 各由真 producer、終態與 qualified owner 定義。 [19](../../docs/codex-spec/19_P11持久化與多人修正案.md) |
| 17 P3／18 P4 | 17 §§2–9-D 舊 IDs、node/Envelope/Unknown/appearance、CAS；18 §§5–8、11–19 legacy Store/Attachment/收據、保存/重啟及單 writer 範圍。 [17](../../docs/codex-spec/17_P3資料模型修正案.md) · [18](../../docs/codex-spec/18_P4持久化與組合修正案.md) |
| 16 凍結、總規格、步驟 | 16 §§8-B、15、20–22、26–30。總規格 §§4、6、9–10。詳細施工 P5–P11、Scope V 文件採用與後續獨立階段。 [16](../../docs/codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md) · [總規格](../../docs/codex-spec/Codex_實作總規格Prompt.md) · [詳細步驟](../../docs/codex-spec/NeoForge1.21.1_詳細實作步驟.md) |
| H6 | §47.3/.4 各 L1729/3001 原 fence／責任；§57.13、.16–.19 P6 call/結果；§58.26–.27 P7；§60.11、.17；§61.4/.9/.18/.19 P8；§63.8/.10/.12/.14/.16/.19/.20/.22/.23/.25 P5★P9；§65.5–.10/.14 exact A/custody；§§66–67 啟動、P7；§68.12–.14 P10；§69.2 P11；§70 Scope D；§71 Scope V。Whole 29,017,933 bytes，SHA-256 `bcc19072653a800f2a19f92ea2719ba7b32d9a173f437e3836c3f93b9f7967b0`。 [H6](../../docs/architecture/P5-A-server-runtime-event-kernel.md#L1729) |

**COMPLETED_ACCEPTANCE**　M1 與 C 的耐久／相容驗收，各限其已確認材料；P11 COMPLETE—PASS 依發布 authority 保留。Scope V 1.0 是文件採用，既有 required CI 即為此提交 regression 結果。 [M1 驗收](../evidence/v0/historical-Z-unavailability-ledger.md) · [C 驗收](../evidence/v0/historical-Z-unavailability-ledger.md) · [Scope V 發布](../evidence/v0/historical-Z-unavailability-ledger.md)

## Canonical 生產 consumers

下列檔案相對於 canonical `src/main/java/com/yo1no/gramarye/`。段落中的絕對連結指向直接證據，個別附錄再列 exact producer/consumer。

| 子系統 | 直接核對 source |
|---|---|
| Catalog／C | `magic/definition/scoped/` 的 ScopeDCatalog（43 current；187 roots；203 compile；234 publish）、ScopeDStorage（current/readback/source identity）、ScopeDCatalogImage、ScopeDSource、ScopeDGraph（route/retain closure）、ScopeDResolver、ScopeDCompiler、ScopeDProgram、ScopeDFrontend、ScopeDLimits（同時 1 C session、capacity units）。現無 transient runtime lease issuer。 [Catalog](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDCatalog.java#L187) · [Storage](../../src/main/java/com/yo1no/gramarye/magic/definition/scoped/ScopeDStorage.java) |
| P5 | SkillRuntimeService（106 registration、127 start、159 auth、509 exact carrier、726 受理、1113 exact body/level、1300–1365 logout、1976 post、2290 drain、3054 schedule、5200 slot、5665 legacy lease）、P5RuntimeVocabulary、P5LoadedReferenceResolver、P5RuntimeProjector、P5RuntimeConfiguration、P5ServerRuntimeConfig、Gramarye。 [P5](../../src/main/java/com/yo1no/gramarye/SkillRuntimeService.java#L1976) · [Config](../../src/main/java/com/yo1no/gramarye/P5ServerRuntimeConfig.java#L151) |
| P6 | P6RuntimeExecutionCapability、P6RuntimeExecutionPortAdapter；`magic/runtime/mana/` 的 P6RuntimeExecutionBridge、EffectExecutionEngine、EffectCommitPlan、EffectResolution、ActionDamageTransactionEngine、EffectStepOutcome、ManaTransactionService、ManaAttachments。 [Bridge](../../src/main/java/com/yo1no/gramarye/magic/runtime/mana/P6RuntimeExecutionBridge.java#L31) · [Legacy resolver](../../src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectResolution.java#L45) |
| P7 | `magic/network/` 的 P7ServerAuthorizationBoundary、P7CastIntentNetworkHandler、P7ServerSessionService、P7ServerAccess、P7ServerLifecycleCoordinator、P7NetworkComposition；root P7AuthenticatedPlayerCastIngress。 [Capture](../../src/main/java/com/yo1no/gramarye/magic/network/P7ServerSessionService.java#L84) · [Ingress](../../src/main/java/com/yo1no/gramarye/P7AuthenticatedPlayerCastIngress.java#L32) |
| P8 | P8AppliedFactHandoff、P8ServerPresentationService、P8PresentationRuntime、PresentationEvent、PresentationEventPayload、PresentationLimits、AppearanceSemantics。 [Applied](../../src/main/java/com/yo1no/gramarye/P8AppliedFactHandoff.java#L13) · [Selection](../../src/main/java/com/yo1no/gramarye/P8PresentationRuntime.java#L291) |
| P9 | P9StarterProjectile（tick/getOwner/claim/unload）、P9StarterProjectileRegistration（same type/noSave）、P9WorldEffectHandoff（native add/hurt exact actor）、既有 client registration/factory。 [Carrier](../../src/main/java/com/yo1no/gramarye/P9StarterProjectile.java#L99) · [Registration](../../src/main/java/com/yo1no/gramarye/P9StarterProjectileRegistration.java#L18) |
| P11 | P11StartupConfiguration、P11ControlBudgets、P11QualifiedSourceOwner、P11NativeOperationBoundary、P11NativeStorageBoundary、P11LiveTransitionService、P11CooldownPolicyProjection、P11CastCooldownService、P11FoundationService、P11P9TrackingCleanup。 [W](../../src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java#L348) · [正常登出](../../src/main/java/com/yo1no/gramarye/P11NativeStorageBoundary.java#L1045) |
| Engineering precedent | build.gradle 2.0.142 source sets/loadedMods、P11L1ServerHarness、P11OnlineServerHarness、P11CooldownServerHarness、P11D3ServerHarness。只讀現有實作／公開啟動拓撲；credential/data 與 log 內容不在證據中。 [build.gradle](../../build.gradle#L1407) |

## 現有直接測試

下列為 canonical `src/test/java/com/yo1no/gramarye/` 的實際類及方法。此回合只讀。

| 路徑／類 | 代表 exact methods |
|---|---|
| `magic/definition/scoped/ScopeDProductionIsolationTest.java`；同包 ScopeDCatalogTest | `onlyPackageInternalTypesCanNameCatalogSourceAndProof`、`productionJarContainsDataEngineAndNoTestProofFaultOrClientHarness`；`reclaimProtectsExactClosureAndRefusesStaleInventoryOrRetiredIds`。 |
| P5RuntimeStaticGateTest、P5RuntimeConfigurationTest、P5RuntimeHardLimitWorkloadTest | `v1AuthorityPlusTheExactS4GuardIsByteForByteTheProductionVocabulary`、`startedConfigurationSnapshotPrecedesEverySlotAndTokenPublication`、`exactRevisionLeaseRetainReleaseAndStopCloseWiringIsClosed`；`rawDecodeUsesDeclarationOrderForMissingTypeAndRangeFailures`；`defaultAndLowerHardLimitWorkloadsAreDeterministicBoundedAndDrainToZero`。 |
| P6S4BoundaryTest；`magic/runtime/mana/` EffectCommitPlanTest、P6EffectVocabularyTest、ManaBoundaryTest | `capabilityAndBridgeSurfacesMatchTheExactClosedDescriptors`；`acceptsOneAndEightOrderedSteps`；舊 effect terminal enum gate；`productApiIsPackagePrivateAndAccountHandleIsNotPublic`。 |
| `magic/network/P7D3CoreOwnershipTest.java`；P8S4PayloadBoundaryTest、P8S2BoundaryTest | `taskRejectsDifferentPlayerEpochOrGenerationBeforeTakingPermitOwnership`；`bridgeOwnsExactlyTwoClientboundRegistrationsWithoutASecondSubscriber`；`authorityAndRecoveredP7IsolationRemainExactBaseBytes`。 |
| P9S3DirectConsumerContractTest、P11AcceptedWorkObservedHitTest、P11AcceptedWorkBoundaryTest、P11ExactCleanupTest | `actualProductionInventoryAcceptsTheBoundBreezeAndScansAllHolders`；`preAdvanceResumePreservesPrimaryAndUsesOnlyBoundedExistingPermits`；`normalLogoutIsExactWholeOnDisconnectNotTheSharedRemoveOrAnEvent`；`changedSectionCallbackKeyOrLevelCannotAuthorizeTheCapturedTail`。 |
| P11StartupConfigurationTest、P11TransitionControlTest、P11ControlBudgetsTest | `p5InvalidAndP11InvalidRemainIndependentWithinOneCandidate`、`concurrentRawObservationAndAtomicPublicationNeverMixReloadTuples`；`exactConnectionHasOneControlOwnerAcrossActorChangesAndRetirement`；`dispatcherQueuedAndTickPumpsShareExactQuantaWithoutSyntheticTicks`。 |

鎖版 source archive 的 exact paths、bytes、hash、對照 entries 和平台 source 細節見 [鎖版平台來源](V0-locked-platform-source-inventory.md)。所有草案型別、容量和 runtime 結果均按 **ENGINEERING_DERIVATION / PROPOSED_CONTRACT / UNVERIFIED** 區分。

## R1 鎖定來源的四個角色

| Role | R1 判定 |
|---|---|
| Authority source | 舊 frozen Z 是 H6 §65.9／P11 §2 登錄的 9,393,663-byte archive，SHA-256 `0e1dcae8e21cd8d8c656e7fe76efe1e31260cf0f956f07aa749b86992cf4fe23`。本機登錄原件查無，51 個舊 member 對照為不可判定，`BLOCKED_BY_EXACT_GAP`。 |
| Generated convenience source archives | Cache 與 canonical build 生成檔各為 9,393,659 bytes，供現有成員的逐一讀取與工程推導。其 51 個成員互等只證這兩份生成檔。 |
| Compiled runtime JAR | Canonical 鎖版 compiled member 提供 `ServerLoginPacketListenerImpl$1.run()V`、`Entity.setPosRaw(DDD)V` 的 exact bytecode anchors，另外標示本機實際封存與 H6 歷史 compiled identity。 |
| Engineering mixin provider | NeoForge 21.1.241 所解析的 MixinExtras 0.5.3 與 runtime JarJar 角色，按 provider、class bytes、compile/run wiring 與實際 transformation owner 實證。 |

[鎖版平台細證與 51-member 表](V0-locked-platform-source-inventory.md) · [R1 blocker 對帳](V0-R2-reconciliation-report.md)

## R2 evidence adoption

本紀錄保存原始資料與角色。R2 獨立將 prospective bundle 用於後續 V0 engineering、implementation、qualification 的平台來源。歷史 Z 保留同原 SHA／長度及 unavailable，舊報告和接受維持歷史原狀。R1.1 只更正 Java classfile inventory，配置／限額／K1 及產品責任沿原 R1。
