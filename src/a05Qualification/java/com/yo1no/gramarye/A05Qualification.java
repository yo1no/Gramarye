package com.yo1no.gramarye;

import com.google.gson.GsonBuilder;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.runtime.mana.P11ManaMaterial;
import com.yo1no.gramarye.magic.runtime.mana.P7ManaSnapshotBridge;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One statically composed validated pre-native-send negative case, outside all distributable artifacts. */
final class A05Qualification {
    private static final int CASE_ABSORPTION = 1;
    private static final int READY_DEADLINE = 2_300;
    private static final int FIRST_NATIVE_OFFER = 2_400;
    private static final int TERMINAL_WINDOW = 64;
    private static final Path OWNED_ROOT = Path.of("/private/tmp/gramarye-v0-final-continuation");
    private static final StackWalker STACK = StackWalker.getInstance();

    static final A05QualificationRecorder recorder = new A05QualificationRecorder(CASE_ABSORPTION);
    final A05Observer observer = new A05Observer(recorder::receipt, recorder::offer, recorder::submission);
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> facts = new LinkedHashMap<>();
    private P11FoundationService foundation;
    private final A05OneShotSubmissionFault submissionFault = new A05OneShotSubmissionFault();
    private final A05OneShotSubmissionFault controlFault = new A05OneShotSubmissionFault();
    final P8ServerPresentationService presentation;
    private P6RuntimeExecutionCapability capability;
    private SkillRuntimeService runtime;
    private MinecraftServer server;
    private ServerPlayer actor;
    private ServerPlayer peer;
    private P11QualifiedSourceOwner source;
    private P11ManaMaterial.State initialMana;
    private P11CastCooldownMaterial.State initialCooldown;
    private Pig target;
    private P9StarterProjectile rootCarrier;
    private P9StarterProjectile childCarrier;
    private A05Observer.ReceiptObservation rootReceipt;
    private A05Observer.ReceiptObservation hitReceipt;
    private A05Observer.ReceiptObservation childReceipt;
    private Vec3 impact;
    private Vec3 impactDirection;
    private Path outputDirectory;
    private boolean created;
    private boolean sceneReady;
    private boolean redirected;
    private boolean rootTracked;
    private boolean rootOwnerMatchedAtInitialJoin;
    private boolean cooldownObservationsValid = true;
    private boolean cooldownDispatchIdentityPreserved = true;
    private int cooldownSamples;
    private int cooldownDispatchSamples;
    private long initialCooldownGameTime = -1L;
    private long lastCooldownClockFloor = -1L;
    private boolean childTracked;
    private boolean finished;
    private int stableReadiness;
    private int readyTick = -1;
    private int rootJoinTick = -1;
    private int impactTick = -1;
    private int postTick = -1;
    private int childJoinTick = -1;
    private int impactCount;
    private int postCount;
    private int childJoinCount;
    private long initialBalance;
    private long initialSourceEpoch;
    private int initialExperience;
    private int initialItems;
    private float postHealth;
    private float postAbsorption;
    private float originalDamage;
    private float healthDamage;
    private float absorptionConsumed;
    private String issue = "NONE";

    A05Qualification() {
        presentation = new P8ServerPresentationService(observer, submissionFault);
    }

    SkillRuntimeService create(IEventBus gameBus,
            SkillDefinitionStoreService storeService,
            SkillSubmissionPolicyProvider policyProvider,
            ProfileAvailabilityView profiles,
            P6RuntimeExecutionCapability capability,
            P8ServerPresentationService presentationService,
            P11FoundationService foundation,
            P11CastCooldownService cooldowns) {
        if (created) { throw new IllegalStateException("A05 qualification already composed"); }
        this.capability = Objects.requireNonNull(capability);
        if (Objects.requireNonNull(presentationService) != presentation) {
            throw new IllegalArgumentException("A05 original presentation composition changed");
        }
        this.foundation = Objects.requireNonNull(foundation);
        Objects.requireNonNull(gameBus);
        Objects.requireNonNull(profiles);
        Objects.requireNonNull(cooldowns);
        var service = new SkillRuntimeService(storeService, policyProvider,
                new P5RuntimeProjector(profiles), new P5LoadedReferenceResolver(),
                new P6RuntimeExecutionPortAdapter(capability, presentationService), foundation, cooldowns,
                new V0P6StatementAdapter(presentationService,
                        new P9V0WorldEffectHandoff.NativeOutcomePort(), observer));
        runtime = service;
        gameBus.addListener(EventPriority.HIGHEST, this::onPostTick);
        gameBus.addListener(EventPriority.HIGHEST, this::onPreTick);
        gameBus.addListener(EventPriority.LOWEST, (ServerTickEvent.Post event) -> {
            if (!sceneReady || finished || event.getServer() != server) {
                service.handleRuntimePost(event);
                return;
            }
            // Qualification-only bracket around the unchanged production P5 drain.
            var beforeCooldown = P11CastCooldownMaterial.capture(actor);
            service.handleRuntimePost(event);
            var afterCooldown = P11CastCooldownMaterial.capture(actor);
            cooldownDispatchSamples++;
            cooldownDispatchIdentityPreserved &= beforeCooldown.current(actor)
                    && beforeCooldown.same(afterCooldown);
            observeCooldownState(event.getServer());
            identifyReceipts();
            if (hitReceipt != null && !submissionFault.armed && issue.equals("NONE")) {
                try {
                    if (!confirmed(hitReceipt) || rootReceipt == null || !confirmed(rootReceipt)
                            || hitReceipt.lineageId != rootReceipt.lineageId
                            || hitReceipt.instanceId != rootReceipt.instanceId
                            || hitReceipt.nativeTick != event.getServer().getTickCount()
                            || postTick != hitReceipt.nativeTick || postCount != 1 || impactCount != 1
                            || !checks.getOrDefault("exactIndirectMagicNativePost", false)
                            || !checks.getOrDefault("exactNaturalUncancelledImpact", false)
                            || postHealth != 10.0F || postAbsorption != 2.0F
                            || !currentPlay(server, actor) || !currentPlay(server, peer)
                            || !qualified(source, actor) || !qualified(source, peer)) {
                        throw new IllegalStateException("A05 original native hit preconditions unproved");
                    }
                    long generation = 0L;
                    long rootSequence = 0L;
                    for (var offer : recorder.offers) {
                        if (offer.phase.equals("LINK") && offer.instanceId == rootReceipt.instanceId
                                && offer.requestId == rootReceipt.requestId
                                && offer.sourceEventId == rootReceipt.sourceEventId
                                && offer.statementOrdinal == -1 && offer.kind.equals("CAST_RELEASE")) {
                            generation = offer.generation;
                            rootSequence = offer.sequence;
                        }
                    }
                    long selectedEpoch = 0L;
                    for (var sent : recorder.submissions) {
                        if (sent.generation == generation && sent.sequence == rootSequence
                                && sent.kind.equals("CAST_RELEASE") && sent.outcome.equals("SUBMITTED")) {
                            selectedEpoch = sent.connectionEpoch;
                            break;
                        }
                    }
                    // Both instances receive only this same original post-P5, pre-P8 receipt.
                    // The secondary instance is never installed in the native transport.
                    submissionFault.expect(hitReceipt, generation, selectedEpoch);
                    controlFault.expect(hitReceipt, generation, selectedEpoch);
                    facts.put("faultArmedNativeTick", event.getServer().getTickCount());
                    facts.put("faultArmedAfterOriginalP5BeforeP8", true);
                } catch (RuntimeException armingFailure) {
                    issue = "ORIGINAL_PRE_SEND_ARMING_PRECONDITION_FAILED";
                }
            }
        });
        gameBus.addListener(EventPriority.LOWEST, this::onJoin);
        gameBus.addListener(EventPriority.LOWEST, this::onImpact);
        gameBus.addListener(EventPriority.LOWEST, this::onDamage);
        gameBus.addListener((ServerStoppingEvent event) -> {
            if (!finished) { finish(event.getServer(), "SERVER_STOPPED_BEFORE_CASE_TERMINAL"); }
            service.enterStoppingForTesting(event.getServer());
        });
        gameBus.addListener(service::handleRuntimeStopped);
        created = true;
        return service;
    }

    private void onPostTick(ServerTickEvent.Post event) {
        if (finished) { return; }
        MinecraftServer current = event.getServer();
        try {
            if (server == null) { bindOwnedRuntime(current); }
            if (server != current) { return; }
            if (!sceneReady) {
                if (current.getTickCount() >= READY_DEADLINE) {
                    finish(current, "PRECONDITION_NOT_MET_BEFORE_2300");
                    current.halt(false);
                    return;
                }
                if (readyPair(current)) {
                    stableReadiness++;
                    if (stableReadiness >= 2) { prepareScene(current); }
                } else {
                    stableReadiness = 0;
                }
                return;
            }
            if (!currentPlay(current, actor) || !currentPlay(current, peer)) {
                finish(current, "ORIGINAL_PAIR_LOST");
                return;
            }
            if (recorder.overflow != 0 || recorder.invalid != 0) {
                finish(current, "SCALAR_RECORDER_INCOMPLETE");
                return;
            }
            if (!issue.equals("NONE")) {
                finish(current, issue);
                return;
            }
            if (rootCarrier != null && !redirected && current.getTickCount() > rootJoinTick) {
                // A later native pose change affects future casts only. The original carrier keeps its capture.
                actor.teleportTo(actor.serverLevel(), actor.getX(), actor.getY(), actor.getZ(), -135.0F, 0.0F);
                peer.teleportTo(peer.serverLevel(), peer.getX(), peer.getY(), peer.getZ(), -135.0F, 0.0F);
                redirected = true;
                facts.put("futureCastPoseChangedAtTick", current.getTickCount());
            }
            observeCooldownState(current);
            identifyReceipts();
            if (rootJoinTick < 0 && current.getTickCount() > FIRST_NATIVE_OFFER + 80) {
                finish(current, "NO_SELECTED_NATIVE_ROOT");
            } else if (rootJoinTick >= 0 && current.getTickCount() >= rootJoinTick + TERMINAL_WINDOW) {
                assess(current);
                finish(current, issue);
            }
        } catch (RuntimeException fixtureFailure) {
            finish(current, "FIXTURE_RUNTIME_FAILURE");
            current.halt(false);
        }
    }

    private void onPreTick(ServerTickEvent.Pre event) {
        if (finished || !sceneReady || event.getServer() != server) { return; }
        if (rootCarrier != null && indexed(rootCarrier)) { rootTracked = true; }
        if (childCarrier != null && indexed(childCarrier)) { childTracked = true; }
    }

    private void bindOwnedRuntime(MinecraftServer current) {
        try {
            Path owned = Path.of(".").toRealPath();
            if (!owned.startsWith(OWNED_ROOT) || owned.equals(OWNED_ROOT)
                    || !current.isSameThread() || !current.isDedicatedServer() || !current.usesAuthentication()
                    || !"127.0.0.1".equals(current.getLocalIp())
                    || !current.getWorldPath(LevelResource.ROOT).toRealPath().getParent().equals(owned)
                    || !Files.getOwner(owned).equals(Files.getOwner(current.getWorldPath(LevelResource.ROOT)))) {
                throw new IllegalStateException("A05 owned dedicated runtime is unproved");
            }
            server = current;
            outputDirectory = owned;
        } catch (IOException failure) {
            throw new IllegalStateException("A05 runtime ownership inspection failed", failure);
        }
    }

    private boolean readyPair(MinecraftServer current) {
        var players = current.getPlayerList().getPlayers();
        if (players.size() != 2) { return false; }
        var first = players.getFirst();
        var second = players.get(1);
        if (!currentPlay(current, first) || !currentPlay(current, second)
                || first == second || first.getUUID().equals(second.getUUID())
                || first.connection == second.connection
                || first.connection.getConnection() == second.connection.getConnection()
                || !presentation.connectionReadyForTesting(first.getUUID())
                || !presentation.connectionReadyForTesting(second.getUUID())) {
            return false;
        }
        var originalSource = foundation.sourceOwner(current);
        if (originalSource == null || !qualified(originalSource, first) || !qualified(originalSource, second)) {
            return false;
        }
        // The operator launches B first and A second. The first native V0 root checks this assignment again.
        peer = first;
        actor = second;
        source = originalSource;
        return true;
    }

    private void prepareScene(MinecraftServer current) {
        if (current.getTickCount() >= READY_DEADLINE || !readyPair(current)) {
            throw new IllegalStateException("A05 readiness changed before world actions");
        }
        var before = P11ManaMaterial.capture(actor);
        initialBalance = P7ManaSnapshotBridge.observeBalance(capability, actor);
        initialMana = P11ManaMaterial.capture(actor);
        if (initialBalance < 0 || !before.sameState(initialMana)) {
            throw new IllegalStateException("A05 Mana observation changed material or was unavailable");
        }
        initialCooldown = P11CastCooldownMaterial.capture(actor);
        initialCooldownGameTime = current.overworld().getGameTime();
        if (initialCooldown.data == null || initialCooldown.data.kind != P11CastCooldownData.Kind.ROUTED
                || !initialCooldown.data.entries.isEmpty() || initialCooldown.data.clockFloor < 0L
                || initialCooldown.data.clockFloor > initialCooldownGameTime || !initialCooldown.current(actor)) {
            throw new IllegalStateException("A05 initially routed empty cooldown material is unproved");
        }
        lastCooldownClockFloor = initialCooldown.data.clockFloor;
        observeCooldownState(current);
        var diagnostics = source.diagnostics(actor.getUUID());
        initialSourceEpoch = diagnostics.sourceEpoch();
        initialExperience = actor.totalExperience;
        initialItems = itemCount(actor);
        readyTick = current.getTickCount();
        var readiness = new LinkedHashMap<String, Object>();
        readiness.put("case", "A05.transport-drop");
        readiness.put("nativeTick", readyTick);
        readiness.put("players", 2);
        readiness.put("distinctAuthenticatedPlayConnections", true);
        readiness.put("qualifiedCompleteSources", true);
        readiness.put("bothP8CatalogsReady", true);
        readiness.put("observerArmed", true);
        readiness.put("beforeWorldActions", true);
        readiness.put("beforeFirstEngineeringOffer", readyTick < FIRST_NATIVE_OFFER);
        readiness.put("casterChosenBySecondNativePlay", true);
        writeJson("a05-preflight.json", readiness);

        ServerLevel level = actor.serverLevel();
        if (peer.serverLevel() != level) { throw new IllegalStateException("A05 pair is in different levels"); }
        actor.teleportTo(level, 4.5, 65.0, 0.5, 45.0F, 6.0F);
        peer.teleportTo(level, 20.5, 65.0, 20.5, -135.0F, 0.0F);
        Pig pig = EntityType.PIG.create(level);
        if (pig == null) { throw new IllegalStateException("A05 native Pig factory failed"); }
        pig.setNoAi(true);
        pig.setSilent(true);
        pig.setPos(-1.0, 65.0, 6.0);
        if (!pig.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 6_000, 0, false, false))
                || pig.getHealth() != 10.0F || pig.getAbsorptionAmount() != 4.0F
                || !level.addFreshEntity(pig) || !P9StarterProjectile.eligibleV0Target(level, pig)) {
            pig.discard();
            throw new IllegalStateException("A05 native absorption target is unproved");
        }
        target = pig;
        sceneReady = true;
        facts.put("readyNativeTick", readyTick);
        facts.put("initialMana", initialBalance);
        facts.put("initialCooldownEntries", initialCooldown.data.entries.size());
        facts.put("initialCooldownKind", initialCooldown.data.kind.name());
        facts.put("initialCooldownClockFloor", initialCooldown.data.clockFloor);
        facts.put("initialCooldownGameTime", initialCooldownGameTime);
        facts.put("initialSource", sourceScalars(diagnostics));
        facts.put("casterPose", List.of(4.5, 65.0, 0.5, 45.0, 6.0));
        facts.put("targetPose", List.of(-1.0, 65.0, 6.0));
        facts.put("targetInitialHealth", 10.0F);
        facts.put("targetInitialAbsorption", 4.0F);
        checks.put("bothAuthenticatedBeforeExperimentalWorldActions", true);
        checks.put("readinessBeforeOriginalFormatTwoFirstOffer", readyTick < READY_DEADLINE);
        checks.put("initialManaReadPreservedMaterial", true);
    }

    private void onJoin(EntityJoinLevelEvent event) {
        if (finished || !sceneReady || !server.isSameThread() || event.isCanceled() || event.loadedFromDisk()
                || !(event.getEntity() instanceof P9StarterProjectile projectile)
                || !projectile.isNoGravity() || !inStack("P9V0WorldEffectHandoff", "commitSpawnNative")
                || !inStack("V0SlotRuntime", "dispatchRelease")) {
            return;
        }
        if (rootCarrier == null) {
            if (projectile.getOwner() != actor || server.getTickCount() < FIRST_NATIVE_OFFER) {
                issue = "FIRST_NATIVE_ROOT_OWNER_OR_SCHEDULE_CHANGED";
                return;
            }
            // The native owner is intentionally unavailable once the V0 carrier closes.
            rootOwnerMatchedAtInitialJoin = projectile.getOwner() == actor;
            rootCarrier = projectile;
            rootJoinTick = server.getTickCount();
            facts.put("firstRootOwnerMatchedAtInitialJoin", rootOwnerMatchedAtInitialJoin);
            facts.put("firstRootOwnerWitnessTick", rootJoinTick);
            return;
        }
        if (projectile.getOwner() == actor && projectile != rootCarrier && postTick >= 0
                && server.getTickCount() == postTick + 1 && impact != null
                && projectile.position().distanceTo(impact.add(impactDirection.scale(0.10))) <= 0.001) {
            childJoinCount++;
            if (childCarrier == null) {
                childCarrier = projectile;
                childJoinTick = server.getTickCount();
            }
        }
    }

    private void onImpact(ProjectileImpactEvent event) {
        if (finished || !sceneReady || event.getProjectile() != rootCarrier) { return; }
        if (event.isCanceled() || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || hit.getEntity() != target || !indexed(rootCarrier) || !indexed(target)
                || rootCarrier.getOwner() != actor || !currentPlay(server, actor)
                || !inStack("P9StarterProjectile", "stepV0")) {
            issue = "SELECTED_NATIVE_IMPACT_INVALID";
            return;
        }
        impactCount++;
        if (impact == null) {
            impact = hit.getLocation();
            impactDirection = rootCarrier.getDeltaMovement().normalize();
            impactTick = server.getTickCount();
            checks.put("exactNaturalUncancelledImpact", true);
            checks.put("impactTargetInitialState", target.getHealth() == 10.0F && target.getAbsorptionAmount() == 4.0F);
        }
    }

    private void onDamage(LivingDamageEvent.Post event) {
        if (finished || !sceneReady || event.getSource().getDirectEntity() != rootCarrier) { return; }
        postCount++;
        if (event.getEntity() != target || event.getSource().getEntity() != actor
                || !event.getSource().is(DamageTypes.INDIRECT_MAGIC)
                || !inStack("P9V0WorldEffectHandoff", "commitDamageNative") || impact == null) {
            issue = "SELECTED_NATIVE_POST_IDENTITY_INVALID";
            return;
        }
        if (postTick < 0) {
            postTick = server.getTickCount();
            postHealth = target.getHealth();
            postAbsorption = target.getAbsorptionAmount();
            originalDamage = event.getOriginalDamage();
            healthDamage = event.getNewDamage();
            absorptionConsumed = event.getReduction(DamageContainer.Reduction.ABSORPTION);
            checks.put("exactIndirectMagicNativePost", true);
        }
    }

    private void identifyReceipts() {
        for (var receipt : recorder.receipts) {
            if (rootReceipt == null && rootJoinTick >= 0 && receipt.nativeTick == rootJoinTick
                    && receipt.statementOrdinal == -1) {
                rootReceipt = receipt;
            }
            if (rootReceipt != null && receipt.lineageId == rootReceipt.lineageId
                    && receipt.instanceId == rootReceipt.instanceId && receipt.statementOrdinal == 0) {
                if (hitReceipt != null && hitReceipt != receipt) { issue = "DUPLICATE_PARENT_HIT_RECEIPT"; }
                hitReceipt = receipt;
            }
            if (rootReceipt != null && childJoinTick >= 0 && receipt.nativeTick == childJoinTick
                    && receipt.lineageId == rootReceipt.lineageId && receipt.instanceId != rootReceipt.instanceId
                    && receipt.statementOrdinal == -1) {
                if (childReceipt != null && childReceipt != receipt) { issue = "DUPLICATE_CHILD_RELEASE_RECEIPT"; }
                childReceipt = receipt;
            }
        }
    }

    private void assess(MinecraftServer current) {
        identifyReceipts();
        checks.put("recorderComplete", recorder.overflow == 0 && recorder.invalid == 0);
        checks.put("firstNativeRootOwnerWasSelectedCaster", rootCarrier != null && rootOwnerMatchedAtInitialJoin);
        checks.put("originalRootWasNativeInserted", rootCarrier != null && rootCarrier.v0EverInserted() && rootTracked);
        checks.put("oneParentImpactAndOneExactPost", impactCount == 1 && postCount == 1);
        checks.put("nativePostSameTickAsImpact", impactTick > 0 && postTick == impactTick);
        checks.put("absorptionOnlyNativeChange", postHealth == 10.0F && postAbsorption == 2.0F
                && originalDamage == 2.0F && healthDamage == 0.0F && absorptionConsumed == 2.0F);
        checks.put("oneGenuineChildAtNextLegalTick", childJoinCount == 1 && childJoinTick == postTick + 1
                && childCarrier != null && childCarrier.v0EverInserted() && childTracked);
        checks.put("sameLineageDistinctChildInstance", rootReceipt != null && childReceipt != null
                && rootReceipt.lineageId == childReceipt.lineageId
                && childReceipt.instanceId > rootReceipt.instanceId);
        checks.put("rootReleaseReceiptConfirmed", confirmed(rootReceipt));
        checks.put("parentHitReceiptConfirmed", confirmed(hitReceipt));
        checks.put("childReleaseReceiptConfirmed", confirmed(childReceipt));
        checks.put("receiptNativeTimesMatchActualNativeFacts", rootReceipt != null && hitReceipt != null
                && childReceipt != null && rootReceipt.nativeTick == rootJoinTick
                && hitReceipt.nativeTick == postTick && childReceipt.nativeTick == childJoinTick);
        checks.put("originalRequestsAndEventsDistinct", rootReceipt != null && hitReceipt != null && childReceipt != null
                && rootReceipt.requestId != hitReceipt.requestId && rootReceipt.requestId != childReceipt.requestId
                && hitReceipt.requestId != childReceipt.requestId && rootReceipt.sourceEventId != hitReceipt.sourceEventId
                && rootReceipt.sourceEventId != childReceipt.sourceEventId
                && hitReceipt.sourceEventId != childReceipt.sourceEventId);
        checkPresentation(rootReceipt, "CAST_RELEASE", "root");
        checkPresentation(hitReceipt, "HIT", "parentHit");
        checkPresentation(childReceipt, "CAST_RELEASE", "child");
        // These calls exercise only a second, never-installed scalar strategy.
        // They do not call packet, transport, receipt producer, P8 offer or native send.
        int rejectedControls = 0;
        if (controlFault.armed && hitReceipt != null && submissionFault.boundSequence > 0L) {
            long sequence = submissionFault.boundSequence;
            try {
                controlFault.beforeSend(hitReceipt.nativeTick, hitReceipt.runtimeTick, hitReceipt.instanceId,
                        hitReceipt.requestId, hitReceipt.sourceEventId, 0, controlFault.expectedGeneration,
                        sequence + 1L, controlFault.selectedEpoch, PresentationEventKind.HIT);
            } catch (IllegalArgumentException expected) { rejectedControls++; }
            try {
                controlFault.beforeSend(hitReceipt.nativeTick, hitReceipt.runtimeTick, hitReceipt.instanceId,
                        hitReceipt.requestId, hitReceipt.sourceEventId, 0, controlFault.expectedGeneration + 1L,
                        sequence, controlFault.selectedEpoch, PresentationEventKind.HIT);
            } catch (IllegalArgumentException expected) { rejectedControls++; }
            try {
                controlFault.beforeSend(hitReceipt.nativeTick, hitReceipt.runtimeTick, hitReceipt.instanceId,
                        hitReceipt.requestId, hitReceipt.sourceEventId, 0, controlFault.expectedGeneration,
                        sequence, Long.MAX_VALUE, PresentationEventKind.HIT);
            } catch (IllegalArgumentException expected) { rejectedControls++; }
            try {
                controlFault.beforeSend(hitReceipt.nativeTick, hitReceipt.runtimeTick, hitReceipt.instanceId,
                        hitReceipt.requestId, hitReceipt.sourceEventId + 1L, 0, controlFault.expectedGeneration,
                        sequence, controlFault.selectedEpoch, PresentationEventKind.HIT);
            } catch (IllegalArgumentException expected) { rejectedControls++; }
            checks.put("directControlsWrongKeysRejectedBeforeConsumption", rejectedControls == 4
                    && !controlFault.consumed && controlFault.faultCount == 0
                    && controlFault.rejectedSequence == 1 && controlFault.rejectedGeneration == 1
                    && controlFault.rejectedRecipient == 1 && controlFault.rejectedEvent == 1);
            boolean exactFaultIdentity = false;
            try {
                controlFault.beforeSend(hitReceipt.nativeTick, hitReceipt.runtimeTick, hitReceipt.instanceId,
                        hitReceipt.requestId, hitReceipt.sourceEventId, 0, controlFault.expectedGeneration,
                        sequence, controlFault.selectedEpoch, PresentationEventKind.HIT);
            } catch (RuntimeException expected) { exactFaultIdentity = expected == controlFault.plannedFault; }
            boolean reuseRejected = false;
            try {
                controlFault.beforeSend(hitReceipt.nativeTick, hitReceipt.runtimeTick, hitReceipt.instanceId,
                        hitReceipt.requestId, hitReceipt.sourceEventId, 0, controlFault.expectedGeneration,
                        sequence, controlFault.selectedEpoch, PresentationEventKind.HIT);
            } catch (IllegalStateException expected) { reuseRejected = true; }
            checks.put("directControlsExactOnceThenReuseRejected", exactFaultIdentity && reuseRejected
                    && controlFault.consumed && controlFault.faultCount == 1 && controlFault.rejectedSecondUse == 1);
        } else {
            checks.put("directControlsWrongKeysRejectedBeforeConsumption", false);
            checks.put("directControlsExactOnceThenReuseRejected", false);
        }
        checks.put("installedBoundaryFaultOccurredOnceWithoutInvalidTuple", submissionFault.expectCalls == 1
                && submissionFault.faultCount == 1 && submissionFault.otherRecipientPasses == 1
                && submissionFault.rejectedEvent == 0 && submissionFault.rejectedGeneration == 0
                && submissionFault.rejectedRecipient == 0 && submissionFault.rejectedSequence == 0
                && submissionFault.rejectedLink == 0 && submissionFault.rejectedAdmission == 0
                && submissionFault.rejectedSecondUse == 0 && submissionFault.rejectedExpectation == 0);
        facts.put("negativeEvidenceRole", "INJECTED_VALIDATED_PRE_NATIVE_SEND_BOUNDARY; NOT_PHYSICAL_NETWORK_FAILURE");
        facts.put("installedFault", Map.of("expectCalls", submissionFault.expectCalls,
                "beforeSendCalls", submissionFault.beforeSendCalls, "faultCount", submissionFault.faultCount,
                "boundSequence", submissionFault.boundSequence, "generation", submissionFault.expectedGeneration,
                "selectedEpoch", submissionFault.selectedEpoch, "otherEpoch", submissionFault.otherEpoch,
                "otherRecipientPasses", submissionFault.otherRecipientPasses));
        facts.put("directControlFault", Map.of("notInstalledInTransport", true, "rejectedWrongKeys", rejectedControls,
                "faultCount", controlFault.faultCount, "rejectedSecondUse", controlFault.rejectedSecondUse));
        checks.put("originalCarriersTerminalAndUnindexed", absent(rootCarrier) && absent(childCarrier));
        checks.put("nativeTargetSurvivedWithExactAbsorptionResult", target != null && indexed(target)
                && target.isAlive() && target.getHealth() == 10.0F && target.getAbsorptionAmount() == 2.0F);
        checks.put("originalCasterAndPeerStillAuthenticated", currentPlay(current, actor) && currentPlay(current, peer));
        checks.put("manaMaterialUnchanged", initialMana != null && initialMana.isCurrent(actor));
        var before = P11ManaMaterial.capture(actor);
        long balance = P7ManaSnapshotBridge.observeBalance(capability, actor);
        checks.put("manaBalanceUnchangedAndReadPreservedMaterial", balance == initialBalance
                && before.sameState(P11ManaMaterial.capture(actor)));
        observeCooldownState(current);
        checks.put("zeroCooldownEntriesRemainEmptyWithLegalClockFloor", initialCooldown != null
                && cooldownObservationsValid && cooldownSamples > 0);
        checks.put("originalP5DispatchPreservesCooldownMaterial", cooldownDispatchIdentityPreserved
                && cooldownDispatchSamples > 0);
        var diagnostics = source.diagnostics(actor.getUUID());
        checks.put("originalSourceStillComplete", qualified(source, actor) && diagnostics.sourceEpoch() == initialSourceEpoch);
        checks.put("noDeathRewardInTargetCase", target.isAlive() && target.getHealth() == 10.0F
                && actor.totalExperience == initialExperience && itemCount(actor) == initialItems);
        facts.put("terminalSource", sourceScalars(diagnostics));
        facts.put("finalMana", balance);
        var finalCooldown = P11CastCooldownMaterial.capture(actor);
        facts.put("finalCooldownEntries", finalCooldown.data == null ? -1 : finalCooldown.data.entries.size());
        facts.put("finalCooldownKind", finalCooldown.data == null ? "ABSENT" : finalCooldown.data.kind.name());
        facts.put("finalCooldownClockFloor", finalCooldown.data == null ? -1L : finalCooldown.data.clockFloor);
        facts.put("finalCooldownGameTime", current.overworld().getGameTime());
        facts.put("cooldownSamples", cooldownSamples);
        facts.put("cooldownDispatchSamples", cooldownDispatchSamples);
        facts.put("cooldownImmediateObservationPreserved", cooldownObservationsValid);
        facts.put("cooldownDispatchIdentityPreserved", cooldownDispatchIdentityPreserved);
        facts.put("cooldownSameIdentityAcrossElapsedNativeTicks", initialCooldown.same(finalCooldown));
        facts.put("cooldownClockFloorPolicy", "ROUTED_EMPTY_MONOTONIC_NOT_ABOVE_ACTUAL_GAMETIME");
        facts.put("nativeImpactTick", impactTick);
        facts.put("nativePostTick", postTick);
        facts.put("nativeChildJoinTick", childJoinTick);
        facts.put("postHealth", postHealth);
        facts.put("postAbsorption", postAbsorption);
        facts.put("originalDamage", originalDamage);
        facts.put("postHealthDamage", healthDamage);
        facts.put("postAbsorptionConsumed", absorptionConsumed);
        facts.put("nativeHurtBooleanObservation", "CONFIRMED_BY_ORIGINAL_APPLIED_RECEIPT; NO_NEW_NATIVE_RETURN_HOOK");
    }

    private void observeCooldownState(MinecraftServer current) {
        var before = P11CastCooldownMaterial.capture(actor);
        var data = before.data;
        long gameTime = current.overworld().getGameTime();
        boolean legal = data != null && data.kind == P11CastCooldownData.Kind.ROUTED
                && data.entries.isEmpty() && data.clockFloor >= lastCooldownClockFloor
                && data.clockFloor >= 0L && data.clockFloor <= gameTime;
        if (data != null) { lastCooldownClockFloor = data.clockFloor; }
        cooldownSamples++;
        cooldownObservationsValid &= legal && before.current(actor)
                && before.same(P11CastCooldownMaterial.capture(actor));
    }

    private void checkPresentation(A05Observer.ReceiptObservation receipt, String kind, String label) {
        if (receipt == null) {
            checks.put(label + "ActualPresentation", false);
            return;
        }
        int offerReturns = 0;
        int links = 0;
        int admissions = 0;
        A05Observer.OfferObservation link = null;
        for (var offer : recorder.offers) {
            if (offer.requestId != receipt.requestId || offer.sourceEventId != receipt.sourceEventId
                    || offer.statementOrdinal != receipt.statementOrdinal || offer.instanceId != receipt.instanceId) {
                continue;
            }
            if (offer.phase.equals("OFFER_RETURN") && offer.kind.equals(kind) && offer.outcome.equals("ACCEPTED")) {
                offerReturns++;
            }
            if (offer.phase.equals("LINK") && offer.kind.equals(kind) && offer.generation > 0 && offer.sequence > 0) {
                links++;
                link = offer;
            }
            if (offer.phase.equals("DELIVERY_ADMISSION") && offer.kind.equals(kind)
                    && offer.selectedRecipients == 2 && offer.candidateDeliveries == 2
                    && offer.admittedDeliveries == 2 && offer.outcome.equals("ACCEPTED")) {
                admissions++;
            }
        }
        var epochs = new HashSet<Long>();
        int submissions = 0;
        int failedSubmissions = 0;
        if (link != null) {
            for (var submission : recorder.submissions) {
                if (submission.generation == link.generation && submission.sequence == link.sequence
                        && submission.kind.equals(kind)) {
                    if (submission.outcome.equals("SUBMITTED")) {
                        submissions++;
                        epochs.add(submission.connectionEpoch);
                    } else {
                        failedSubmissions++;
                    }
                }
            }
        }
        checks.put(label + "OriginalOfferOnce", offerReturns == 1);
        checks.put(label + "SequenceLinkedOnce", links == 1);
        checks.put(label + "TwoRealRecipientsAdmitted", admissions == 1);
        if (label.equals("parentHit")) {
            int selectedFaults = 0;
            int actualOtherSends = 0;
            int selectedAttempts = 0;
            if (link != null) {
                for (var sent : recorder.submissions) {
                    if (sent.generation == link.generation && sent.sequence == link.sequence) {
                        selectedAttempts++;
                        if (sent.nativeTick == receipt.nativeTick && sent.kind.equals("HIT")
                                && sent.connectionEpoch == submissionFault.selectedEpoch
                                && sent.outcome.equals("RUNTIME_EXCEPTION")) { selectedFaults++; }
                        if (sent.nativeTick == receipt.nativeTick && sent.kind.equals("HIT")
                                && sent.connectionEpoch == submissionFault.otherEpoch
                                && sent.outcome.equals("SUBMITTED")) { actualOtherSends++; }
                    }
                }
            }
            checks.put("parentHitOneInjectedBoundaryFailureAndOtherRealSend", selectedAttempts == 2
                    && selectedFaults == 1 && actualOtherSends == 1
                    && submissions == 1 && failedSubmissions == 1 && epochs.size() == 1
                    && submissionFault.armed && submissionFault.consumed && submissionFault.faultCount == 1
                    && submissionFault.boundSequence == (link == null ? 0L : link.sequence));
        } else {
            checks.put(label + "ActualNativeSubmissionExactlyOncePerConnection", submissions == 2
                    && epochs.size() == 2 && failedSubmissions == 0);
        }
        facts.put(label + "Presentation", Map.of("offers", offerReturns, "links", links,
                "admissions", admissions, "submissions", submissions, "distinctConnectionEpochs", epochs.size(),
                "failedSubmissions", failedSubmissions));
    }

    private void finish(MinecraftServer current, String reason) {
        if (finished) { return; }
        finished = true;
        boolean accepted = reason.equals("NONE") && rootReceipt != null && hitReceipt != null && childReceipt != null
                && !checks.isEmpty() && checks.values().stream().allMatch(Boolean.TRUE::equals);
        var result = new LinkedHashMap<String, Object>();
        result.put("case", "A05.transport-drop");
        result.put("accepted", accepted);
        result.put("terminal", true);
        result.put("issue", reason);
        result.put("nativeTick", current.getTickCount());
        result.put("checks", checks);
        result.put("facts", facts);
        result.put("receiptRecords", receiptRows());
        result.put("offerRecords", offerRows());
        result.put("submissionRecords", submissionRows());
        result.put("recorderOverflow", recorder.overflow);
        result.put("recorderInvalid", recorder.invalid);
        result.put("notRun", List.of("A05.missing-profile", "A05.stale-profile", "A05.residual-drop",
                "A05.absorption", "A05.zero-post", "A05.postcondition"));
        try {
            if (outputDirectory != null) { writeJson("a05-transport-result.json", result); }
        } catch (RuntimeException writeFailure) {
            System.err.println("A05_QUALIFICATION_RESULT_WRITE_FAILED");
            current.halt(false);
        }
        actor = null;
        peer = null;
        target = null;
        rootCarrier = null;
        childCarrier = null;
        source = null;
        initialMana = null;
        initialCooldown = null;
        foundation = null;
        capability = null;
        runtime = null;
        impact = null;
        impactDirection = null;
    }

    private List<Map<String, Object>> receiptRows() {
        var result = new ArrayList<Map<String, Object>>();
        for (var item : recorder.receipts) {
            var row = new LinkedHashMap<String, Object>();
            row.put("nativeTick", item.nativeTick);
            row.put("runtimeTick", item.runtimeTick);
            row.put("instanceId", item.instanceId);
            row.put("lineageId", item.lineageId);
            row.put("requestId", item.requestId);
            row.put("sourceEventId", item.sourceEventId);
            row.put("statementOrdinal", item.statementOrdinal);
            row.put("terminal", item.terminal);
            row.put("reason", item.reason);
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> offerRows() {
        var result = new ArrayList<Map<String, Object>>();
        for (var item : recorder.offers) {
            var row = new LinkedHashMap<String, Object>();
            row.put("phase", item.phase);
            row.put("nativeTick", item.nativeTick);
            row.put("runtimeTick", item.runtimeTick);
            row.put("instanceId", item.instanceId);
            row.put("requestId", item.requestId);
            row.put("sourceEventId", item.sourceEventId);
            row.put("statementOrdinal", item.statementOrdinal);
            row.put("kind", item.kind);
            row.put("generation", item.generation);
            row.put("sequence", item.sequence);
            row.put("selectedRecipients", item.selectedRecipients);
            row.put("candidateDeliveries", item.candidateDeliveries);
            row.put("admittedDeliveries", item.admittedDeliveries);
            row.put("outcome", item.outcome);
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> submissionRows() {
        var result = new ArrayList<Map<String, Object>>();
        for (var item : recorder.submissions) {
            result.add(Map.of("nativeTick", item.nativeTick, "generation", item.generation,
                    "sequence", item.sequence, "kind", item.kind, "connectionEpoch", item.connectionEpoch,
                    "outcome", item.outcome));
        }
        return result;
    }

    private void writeJson(String name, Map<String, ?> value) {
        try {
            Files.writeString(outputDirectory.resolve(name), new GsonBuilder().setPrettyPrinting().create().toJson(value)
                    + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException failure) {
            throw new IllegalStateException("A05 fixed scalar output failed", failure);
        }
    }

    private static boolean confirmed(A05Observer.ReceiptObservation receipt) {
        return receipt != null && receipt.terminal.equals("CONFIRMED_APPLIED") && receipt.reason.equals("NONE");
    }

    private static boolean currentPlay(MinecraftServer current, ServerPlayer player) {
        if (player == null || player.getServer() != current || !current.usesAuthentication()
                || player.isFakePlayer() || !player.isAlive() || player.isRemoved()
                || current.getPlayerList().getPlayer(player.getUUID()) != player) { return false; }
        var listener = player.connection;
        return listener != null && listener.player == player && listener.isAcceptingMessages()
                && listener.protocol() == ConnectionProtocol.PLAY && listener.getConnection().isConnected()
                && listener.getConnection().isEncrypted() && !listener.getConnection().isMemoryConnection()
                && listener.getConnection().getPacketListener() == listener;
    }

    private static boolean qualified(P11QualifiedSourceOwner owner, ServerPlayer player) {
        var body = owner.nativeRecipient(player);
        var diagnostics = owner.diagnostics(player.getUUID());
        return body != null && owner.qualifiedForV0Capture(body) && owner.canSerialize(body)
                && diagnostics.bodyComplete() && !diagnostics.candidatePresent()
                && diagnostics.sourceFault().equals("NONE");
    }

    private static boolean indexed(Entity entity) {
        return entity != null && entity.level() instanceof ServerLevel level && entity.isAddedToLevel()
                && !entity.isRemoved() && level.getEntity(entity.getId()) == entity
                && level.getEntity(entity.getUUID()) == entity;
    }

    private static boolean absent(Entity entity) {
        return entity != null && entity.level() instanceof ServerLevel level && entity.isRemoved()
                && level.getEntity(entity.getId()) != entity && level.getEntity(entity.getUUID()) != entity;
    }

    private static boolean inStack(String type, String method) {
        return STACK.walk(frames -> frames.limit(64).anyMatch(frame ->
                frame.getClassName().equals("com.yo1no.gramarye." + type) && frame.getMethodName().equals(method)));
    }

    private static int itemCount(ServerPlayer player) {
        int result = 0;
        for (var stack : player.getInventory().items) { result = Math.addExact(result, stack.getCount()); }
        return result;
    }

    private static Map<String, Object> sourceScalars(P11QualifiedSourceOwner.Diagnostics diagnostics) {
        var roots = new LinkedHashMap<String, Long>();
        for (var root : diagnostics.nativeResponsibilities().roots()) { roots.put(root.kind(), root.count()); }
        return Map.of("bodyComplete", diagnostics.bodyComplete(), "candidatePresent", diagnostics.candidatePresent(),
                "sourceFault", diagnostics.sourceFault(), "sourceEpoch", diagnostics.sourceEpoch(),
                "sourceVersion", diagnostics.sourceVersion(), "nativeRoots", roots);
    }
}
