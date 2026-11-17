package com.yo1no.gramarye;

import com.google.gson.GsonBuilder;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.runtime.mana.P11ManaMaterial;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.Invocation;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.DamageInvocation;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.SpawnProjectileInvocation;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.SpawnCommit;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.DamageCommit;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0Terminal;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0Reason;
import net.minecraft.resources.ResourceLocation;
import com.yo1no.gramarye.magic.runtime.mana.P7ManaSnapshotBridge;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Static qualification composition, excluded from both product JARs. */
final class A04UnknownQualification {
    private static final boolean DAMAGE_UNKNOWN = false;
    private static final long INITIAL_BALANCE = 5L;
    private static final long FINAL_BALANCE = 1L;
    private static final int ROOT_OBSERVATION_TICKS = 160;
    private static final int PROBE_OBSERVATION_TICKS = 64;
    private static final int ORIGINAL_DEADLINE_WINDOW_TICKS = 42;
    private static final int MAX_COLLECTION_ATTEMPTS = 8;
    private static boolean created;

    private final A04UnknownOutcomeProducer producer;
    private final P6RuntimeExecutionCapability capability;
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> observations = new LinkedHashMap<>();
    private final Map<String, WeakReference<?>> oldCallReferences = new LinkedHashMap<>();
    private P11FoundationService foundation;
    private P8ServerPresentationService presentation;
    private V0P6StatementAdapter negativeAdapter;
    private SkillRuntimeService runtime;
    private P11QualifiedSourceOwner source;
    private ServerPlayer actor;
    private Pig target;
    private P11ManaMaterial.State initialMana;
    private P11CastCooldownMaterial.State initialCooldown;
    private V0CatalogAuthority authority;
    private Map<String, Object> presentationAtTickStart;
    private int readyTick;
    private int rootReceiptTick = -1;
    private int unknownTick = -1;
    private int probeTick = -1;
    private int collectionTick = -1;
    private int collectionAttempts;
    private boolean unknownObservedAfterDispatch;
    private boolean probeSubmitted;
    private boolean probeObservedAfterDispatch;
    private boolean oldReferencesDropped;
    private boolean done;

    private A04UnknownQualification(P11FoundationService foundation,
            P6RuntimeExecutionCapability capability, A04UnknownOutcomeProducer producer,
            P8ServerPresentationService presentation) {
        this.foundation = Objects.requireNonNull(foundation);
        this.capability = Objects.requireNonNull(capability);
        this.producer = Objects.requireNonNull(producer);
        this.presentation = Objects.requireNonNull(presentation);
    }

    static SkillRuntimeService create(IEventBus gameBus,
            SkillDefinitionStoreService storeService,
            SkillSubmissionPolicyProvider policyProvider,
            ProfileAvailabilityView profiles,
            P6RuntimeExecutionCapability capability,
            P8ServerPresentationService presentationService,
            P11FoundationService foundation,
            P11CastCooldownService cooldowns) {
        Objects.requireNonNull(gameBus, "gameBus");
        Objects.requireNonNull(profiles, "profiles");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(presentationService, "presentationService");
        Objects.requireNonNull(foundation, "foundation");
        Objects.requireNonNull(cooldowns, "cooldowns");
        if (created) {
            throw new IllegalStateException("A04 qualification composition is already installed");
        }
        var producer = new A04UnknownOutcomeProducer(DAMAGE_UNKNOWN);
        var observer = new A04UnknownQualification(foundation, capability, producer, presentationService);
        var service = new SkillRuntimeService(storeService, policyProvider,
                new P5RuntimeProjector(profiles), new P5LoadedReferenceResolver(),
                new P6RuntimeExecutionPortAdapter(capability, presentationService), foundation, cooldowns,
                new V0P6StatementAdapter(presentationService, producer));
        observer.runtime = service;
        observer.negativeAdapter = new V0P6StatementAdapter(presentationService);
        producer.negativeControls = observer::observeNegativeCoordinates;
        gameBus.addListener(EventPriority.HIGHEST, observer::observe);
        gameBus.addListener(EventPriority.LOWEST, service::handleRuntimePost);
        gameBus.addListener(EventPriority.LOWEST, observer::observePostDispatch);
        gameBus.addListener((ServerStoppingEvent event) -> service.enterStoppingForTesting(event.getServer()));
        gameBus.addListener(service::handleRuntimeStopped);
        created = true;
        return service;
    }

    private void observe(ServerTickEvent.Post event) {
        if (done) {
            return;
        }
        var server = event.getServer();
        try {
            if (!server.isSameThread()) {
                finish(server, "WRONG_SERVER_THREAD", false);
                return;
            }
            presentationAtTickStart = presentationScalars();
            if (actor == null) {
                observeReadiness(server);
                return;
            }
            if (!producer.failure.isEmpty()) {
                finish(server, producer.failure, false);
                return;
            }
            if (producer.unknownReceipts == 0) {
                if ((long) server.getTickCount() - readyTick > ROOT_OBSERVATION_TICKS) {
                    finish(server, "UNKNOWN_RECEIPT_NOT_OBSERVED", false);
                }
                return;
            }
            if (!unknownObservedAfterDispatch) {
                finish(server, "UNKNOWN_DISPATCH_OBSERVATION_MISSING", false);
                return;
            }
            if (!probeSubmitted) {
                if ((long) server.getTickCount() - unknownTick >= 2L) {
                    submitCleanupProbe(server);
                }
                return;
            }
            if (!probeObservedAfterDispatch) {
                if ((long) server.getTickCount() - probeTick > PROBE_OBSERVATION_TICKS) {
                    finish(server, "CLEANUP_PROBE_RECEIPT_NOT_OBSERVED", false);
                }
                return;
            }
            if ((long) server.getTickCount() - rootReceiptTick < ORIGINAL_DEADLINE_WINDOW_TICKS) {
                return;
            }
            if (!oldReferencesDropped) {
                observeFinalSource();
                checks.put("noOriginalRequestReplayThroughDeadline", originalReceiptCounts());
                captureAndDropOldCallReferences();
                collectionTick = server.getTickCount();
                return;
            }
            observeCollection(server);
        } catch (RuntimeException | Error failure) {
            finish(server, "OBSERVER_FAILURE_" + failure.getClass().getSimpleName(), false);
        }
    }

    private void observeReadiness(MinecraftServer server) {
        if (!server.usesAuthentication() || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        if (server.getPlayerList().getPlayers().size() != 1) {
            finish(server, "FIXTURE_REQUIRES_EXACTLY_ONE_CURRENT_ACTOR", false);
            return;
        }
        var candidate = server.getPlayerList().getPlayers().getFirst();
        if (!currentAuthenticatedPlay(server, candidate)) {
            return;
        }
        var candidateSource = foundation.sourceOwner(server);
        var body = candidateSource == null ? null : candidateSource.body(candidate);
        if (body == null || !candidateSource.qualifiedForV0Capture(body)) {
            return;
        }
        var beforeMana = P11ManaMaterial.capture(candidate);
        long balance = P7ManaSnapshotBridge.observeBalance(capability, candidate);
        var afterMana = P11ManaMaterial.capture(candidate);
        if (balance != INITIAL_BALANCE || !beforeMana.sameState(afterMana)) {
            finish(server, "INITIAL_MANA_TRUTH_MISMATCH", false);
            return;
        }
        var diagnostics = candidateSource.diagnostics(candidate.getUUID());
        if (!diagnostics.bodyComplete() || diagnostics.candidatePresent()
                || !"NONE".equals(diagnostics.sourceFault())
                || rootCount(diagnostics, "WORK") != 0L
                || rootCount(diagnostics, "OPERATION") != 0L) {
            return;
        }
        prepareScene(server, candidate);
        if (!candidateSource.qualifiedForV0Capture(body)) {
            finish(server, "SCENE_SETUP_LOST_QUALIFIED_SOURCE", false);
            return;
        }
        if (producer.expectationReady || producer.expectedActor != null) {
            finish(server, "EXPECTATION_ALREADY_INSTALLED", false);
            return;
        }
        producer.expectedActor = candidate;
        producer.expectedServer = server;
        producer.expectedSource = candidateSource;
        producer.expectationReady = true;
        actor = candidate;
        source = candidateSource;
        initialMana = afterMana;
        initialCooldown = P11CastCooldownMaterial.capture(candidate);
        observations.put("initialCooldownEntryCount", initialCooldown.data == null ? 0 : initialCooldown.data.entries.size());
        readyTick = server.getTickCount();
        observations.put("readiness", sourceScalars(diagnostics));
        observations.put("initialBalance", balance);
        observations.put("readyNativeTick", readyTick);
        checks.put("authenticatedCurrentPlayAtReadiness", true);
        checks.put("completeQualifiedP11SourceAtReadiness", true);
        checks.put("initialManaObservationPreservedMaterial", true);
    }

    private void prepareScene(MinecraftServer server, ServerPlayer candidate) {
        candidate.teleportTo(candidate.serverLevel(), 4.5, 65.0, 0.5, 45.0F, 6.0F);
        var level = candidate.serverLevel();
        var pig = EntityType.PIG.create(level);
        if (pig == null) {
            throw new IllegalStateException("A04 target could not be created");
        }
        pig.setPos(-1.0, 65.0, 6.0);
        pig.setNoAi(true);
        pig.setSilent(true);
        if (!level.addFreshEntity(pig) || !P9StarterProjectile.eligibleV0Target(level, pig)) {
            pig.discard();
            throw new IllegalStateException("A04 requires one loaded native target");
        }
        target = pig;
        var scene = new LinkedHashMap<String, Object>();
        scene.put("nativeServerThread", server.isSameThread());
        scene.put("actorPosition", List.of(candidate.getX(), candidate.getY(), candidate.getZ()));
        scene.put("actorYaw", candidate.getYRot());
        scene.put("actorPitch", candidate.getXRot());
        scene.put("targetPosition", List.of(pig.getX(), pig.getY(), pig.getZ()));
        scene.put("targetHealth", pig.getHealth());
        scene.put("targetAbsorption", pig.getAbsorptionAmount());
        observations.put("scene", scene);
        if (pig.getHealth() != 10.0F || pig.getAbsorptionAmount() != 0.0F) {
            throw new IllegalStateException("A04 target health baseline changed");
        }
    }

    private void observePostDispatch(ServerTickEvent.Post event) {
        if (done || actor == null) {
            return;
        }
        var server = event.getServer();
        try {
            if (!producer.failure.isEmpty()) {
                finish(server, producer.failure, false);
                return;
            }
            if (producer.completedCallbacks > 0 && rootReceiptTick < 0) {
                rootReceiptTick = server.getTickCount();
                observations.put("rootPresentationAfterDispatch", presentationScalars());
            }
            if (producer.unknownReceipts == 1 && !unknownObservedAfterDispatch) {
                unknownObservedAfterDispatch = true;
                unknownTick = server.getTickCount();
                var after = presentationScalars();
                observations.put("presentationBeforeUnknown", presentationAtTickStart);
                observations.put("presentationAfterUnknown", after);
                checks.put("unknownAllocatedNoPresentationSequence", presentationAtTickStart != null
                        && presentationAtTickStart.get("sequenceHighWater").equals(after.get("sequenceHighWater")));
                checks.put("unknownAddedNoBufferedPresentation", presentationAtTickStart != null
                        && presentationAtTickStart.get("bufferedEvents").equals(after.get("bufferedEvents")));
                checks.put("selectedUnknownCompletedExactlyOnce", originalReceiptCounts());
                checks.put("operationClosedAfterOriginalDispatch",
                        rootCount(source.diagnostics(actor.getUUID()), "OPERATION") == 0L);
                checks.put("originalDebitMaterialCurrentAfterDispatch", producer.debitMaterial.isCurrent(actor));
                var unknownReceipt = producer.receipts.getLast();
                checks.put("originalUnknownReceiptTupleAndReason", ((Number) unknownReceipt.get("requestId")).longValue()
                        == (DAMAGE_UNKNOWN ? producer.damageRequestId : producer.rootRequestId)
                        && ((Number) unknownReceipt.get("sourceEventId")).longValue()
                        == (DAMAGE_UNKNOWN ? producer.damageEventId : producer.rootEventId)
                        && ((Number) unknownReceipt.get("statementOrdinal")).intValue() == (DAMAGE_UNKNOWN ? 0 : -1)
                        && "OUTCOME_UNKNOWN".equals(unknownReceipt.get("terminal"))
                        && "NATIVE_OUTCOME_UNCERTAIN".equals(unknownReceipt.get("reason")));
                var rootReceipt = producer.receipts.getFirst();
                checks.put("originalRootReceiptTupleAndTerminal", ((Number) rootReceipt.get("requestId")).longValue() == producer.rootRequestId
                        && ((Number) rootReceipt.get("sourceEventId")).longValue() == producer.rootEventId
                        && ((Number) rootReceipt.get("statementOrdinal")).intValue() == -1
                        && (DAMAGE_UNKNOWN ? "CONFIRMED_APPLIED" : "OUTCOME_UNKNOWN").equals(rootReceipt.get("terminal"))
                        && (DAMAGE_UNKNOWN ? "NONE" : "NATIVE_OUTCOME_UNCERTAIN").equals(rootReceipt.get("reason")));
                checks.put("nativeRootCountMatchesCase", producer.rootNativeApplied == (DAMAGE_UNKNOWN ? 1 : 0));
                checks.put("noChildNativeAttempt", producer.childAttempts == 0);
                checks.put("targetHealthUnchangedAtUnknown", targetUnchanged());
                if (DAMAGE_UNKNOWN) {
                    checks.put("naturalClaimTargetIsExactFixtureTarget", producer.damageFrame != null
                            && producer.damageFrame.target() == target);
                }
                authority = producer.rootFrame.authority();
                observations.put("originalCell", producer.binding.cell);
                observations.put("originalLineage", producer.binding.lineage.value());
                observations.put("originalBinding", producer.binding.id.value());
            }
            if (producer.probeReceipts == 1 && !probeObservedAfterDispatch) {
                probeObservedAfterDispatch = true;
                var probe = producer.receipts.getLast();
                boolean fresh = ((Number) probe.get("requestId")).longValue() > Math.max(producer.rootRequestId, producer.damageRequestId)
                        && ((Number) probe.get("sourceEventId")).longValue() != producer.rootEventId
                        && ((Number) probe.get("sourceEventId")).longValue() != producer.damageEventId
                        && ((Number) probe.get("statementOrdinal")).intValue() == -1
                        && "REJECTED_BEFORE_COMMIT".equals(probe.get("terminal"))
                        && "INSUFFICIENT_MANA".equals(probe.get("reason"));
                checks.put("freshCleanupProbeExecutedInSingleCellSlot", fresh && producer.binding.cell == 0
                        && !producer.work.qualifies(actor));
                observations.put("cleanupProbeReceipt", probe);
                observations.put("probeCellProof", "FIXTURE_MAX_LINEAGES_PER_SERVER_1; P5_DISPATCH_RECEIPT_REQUIRES_ACCEPTED_NEW_LINEAGE");
                checks.put("originalCarrierInsertionMatchesCase", producer.carrier.v0EverInserted() == DAMAGE_UNKNOWN);
                checks.put("originalBindingClosedAndCarrierCleared", producer.binding.closing()
                        && producer.binding.carrier() == null);
                checks.put("originalCarrierAbsentFromBothWorldIndexes", !producer.carrier.isAddedToLevel()
                        && (DAMAGE_UNKNOWN ? producer.carrier.isRemoved() : !producer.carrier.v0EverInserted())
                        && server.getLevel(producer.carrier.level().dimension()).getEntity(producer.carrier.getId()) == null
                        && server.getLevel(producer.carrier.level().dimension()).getEntity(producer.carrier.getUUID()) == null);
                observations.put("cleanupProof", "AVAILABLE_CELL_NULL_ONLY_AFTER_W_RELEASE_CATALOG_RELEASE_QUEUE_CLOSE");
            }
        } catch (RuntimeException | Error failure) {
            finish(server, "POST_DISPATCH_OBSERVATION_" + failure.getClass().getSimpleName(), false);
        }
    }

    private void submitCleanupProbe(MinecraftServer server) {
        probeSubmitted = true;
        probeTick = server.getTickCount();
        producer.cleanupProbeExpected = true;
        boolean submitted = authority != null && runtime.reserveV0Capture(server, authority, actor,
                new SkillOwnerId(actor.getUUID()), V0RuntimeCatalog.Selection.standalone(257L));
        observations.put("cleanupProbeReservationNativeTick", probeTick);
        checks.put("singleFreshCleanupProbeReserved", submitted);
        if (!submitted) {
            finish(server, "CLEANUP_PROBE_RESERVATION_REJECTED", false);
        }
    }

    private boolean originalReceiptCounts() {
        int expectedOriginal = DAMAGE_UNKNOWN ? 2 : 1;
        long originalRootReceipts = producer.receipts.stream()
                .filter(receipt -> ((Number) receipt.get("requestId")).longValue() == producer.rootRequestId).count();
        long originalDamageReceipts = producer.receipts.stream()
                .filter(receipt -> ((Number) receipt.get("requestId")).longValue() == producer.damageRequestId).count();
        return producer.rootAttempts == 1 && producer.damageAttempts == (DAMAGE_UNKNOWN ? 1 : 0)
                && producer.rootNativeApplied == (DAMAGE_UNKNOWN ? 1 : 0) && producer.childAttempts == 0
                && producer.unknownReturns == 1 && producer.unknownReceipts == 1
                && originalRootReceipts == 1L && originalDamageReceipts == (DAMAGE_UNKNOWN ? 1L : 0L)
                && producer.completedCallbacks == expectedOriginal + producer.probeReceipts
                && producer.receipts.size() == expectedOriginal + producer.probeReceipts;
    }

    private void observeNegativeCoordinates() {
        var frame = DAMAGE_UNKNOWN ? producer.damageFrame : producer.rootFrame;
        var owner = DAMAGE_UNKNOWN ? producer.damageOwner : producer.rootOwner;
        var before = P11ManaMaterial.capture(actor);
        var attemptsBefore = producer.rootAttempts + producer.damageAttempts;
        checks.put("negativeControlsUseOriginalActiveFrame", frame != null && frame.allowsNative());
        var negativeReceipts = new LinkedHashMap<String, Object>();
        for (int test = 0; test < (DAMAGE_UNKNOWN ? 4 : 3); test++) {
            long request = frame.requestId() + (test == 0 ? 1 : 0);
            long event = frame.sourceEventId() + (test == 1 ? 1 : 0);
            int ordinal = test == 2 ? (DAMAGE_UNKNOWN ? -1 : 0) : frame.statementOrdinal();
            Invocation input;
            if (DAMAGE_UNKNOWN) {
                input = new DamageInvocation(ResourceLocation.fromNamespaceAndPath("gramarye", "damage"),
                        request, event, test == 3 ? actor.getUUID() : target.getUUID(), producer.damageMagnitude, 0L);
            } else {
                var geometry = frame.spawn();
                var origin = geometry.origin();
                input = new SpawnProjectileInvocation(ResourceLocation.fromNamespaceAndPath("gramarye", "spawn_projectile"),
                        request, event, geometry.level().dimension().location(), origin.x, origin.y, origin.z,
                        geometry.directionXQ15(), geometry.directionYQ15(), geometry.directionZQ15(), 0, producer.rootManaCost);
            }
            var receipt = negativeAdapter.execute(actor, input, ordinal,
                    new V0P6StatementGuard(frame), negativeAdapter.nativeHandoff(frame));
            String label = switch (test) {
                case 0 -> "wrongRequest";
                case 1 -> "wrongEvent";
                case 2 -> "wrongOrdinal";
                default -> "wrongTarget";
            };
            checks.put(label + "RejectedBeforeCommit", receipt.terminal() == V0Terminal.REJECTED_BEFORE_COMMIT
                    && receipt.reason() == (test == 2 ? V0Reason.INVALID_REQUEST : V0Reason.ACTOR_OR_SOURCE_INVALID)
                    && receipt.requestId() == request && receipt.sourceEventId() == event
                    && receipt.statementOrdinal() == ordinal);
            negativeReceipts.put(label, Map.of("terminal", receipt.terminal().name(),
                    "reason", receipt.reason().name(), "requestId", receipt.requestId(),
                    "sourceEventId", receipt.sourceEventId(), "ordinal", receipt.statementOrdinal()));
        }
        boolean reusedRejected = false;
        try {
            if (DAMAGE_UNKNOWN) {
                owner.commitDamage(new DamageCommit(frame.requestId(), frame.sourceEventId(), target.getUUID(), producer.damageMagnitude, 0L));
            } else {
                var geometry = frame.spawn();
                var origin = geometry.origin();
                owner.commitSpawn(new SpawnCommit(frame.requestId(), frame.sourceEventId(),
                        geometry.level().dimension().location(), origin.x, origin.y, origin.z,
                        geometry.directionXQ15(), geometry.directionYQ15(), geometry.directionZQ15(), 0, producer.rootManaCost));
            }
        } catch (IllegalStateException expectedReuse) {
            reusedRejected = "Reused exact V0 native call".equals(expectedReuse.getMessage());
        }
        checks.put("sameNativeCallSecondUseRejected", reusedRejected);
        checks.put("negativeControlsPreservedDebitMaterial", before.isCurrent(actor));
        checks.put("negativeControlsNeverReachedNativeProducer", attemptsBefore == producer.rootAttempts + producer.damageAttempts);
        checks.put("negativeControlsPreservedTarget", targetUnchanged());
        observations.put("negativeCoordinateReceipts", negativeReceipts);
    }

    private void observeFinalSource() {
        boolean sameActor = currentAuthenticatedPlay(actor.getServer(), actor)
                && P11NativeStorageBoundary.nativeSourceOwner(actor) == source;
        var diagnostics = source.diagnostics(actor.getUUID());
        var before = P11ManaMaterial.capture(actor);
        long balance = P7ManaSnapshotBridge.observeBalance(capability, actor);
        boolean observationPreserved = before.sameState(P11ManaMaterial.capture(actor));
        observations.put("terminalSource", sourceScalars(diagnostics));
        var currentPresentation = presentationScalars();
        observations.put("presentationAfterFullDrainAndDeadline", currentPresentation);
        var beforeUnknown = (Map<?, ?>) observations.get("presentationBeforeUnknown");
        checks.put("noPresentationSequenceAfterUnknownThroughDeadline", beforeUnknown != null
                && beforeUnknown.get("sequenceHighWater")
                        .equals(currentPresentation.get("sequenceHighWater")));
        checks.put("cooldownMaterialAndEntriesUnchanged", initialCooldown != null
                && initialCooldown.current(actor)
                && initialCooldown.same(P11CastCooldownMaterial.capture(actor)));
        observations.put("finalCooldownEntryCount", P11CastCooldownAttachments.existing(actor) == null ? 0
                : P11CastCooldownAttachments.existing(actor).entries.size());
        var readiness = (Map<?, ?>) observations.get("readiness");
        checks.put("sourceMaintainsOriginalEpochAfterDebit", readiness != null
                && ((Number) readiness.get("sourceEpoch")).longValue() == diagnostics.sourceEpoch()
                && diagnostics.sourceVersion() >= ((Number) readiness.get("sourceVersion")).longValue() + 1L);
        observations.put("rewardPolicy", "UNKNOWN_DOES_NOT_ASSERT_OR_AUTHORIZE_REWARD");
        observations.put("finalBalance", balance);
        checks.put("sameAuthenticatedActorAtTerminal", sameActor);
        checks.put("finalBalanceOneAfterPaidFourAndFreshProbe", balance == FINAL_BALANCE);
        checks.put("finalManaObservationPreservedMaterial", observationPreserved);
        checks.put("initialManaMaterialReplaced", initialMana != null && !initialMana.isCurrent(actor));
        checks.put("originalDebitMaterialStillCurrentAfterProbe", producer.debitMaterial.isCurrent(actor));
        checks.put("terminalSourceComplete", diagnostics.bodyComplete() && !diagnostics.candidatePresent()
                && "NONE".equals(diagnostics.sourceFault()));
        checks.put("terminalWorkAndOperationZero", rootCount(diagnostics, "WORK") == 0L
                && rootCount(diagnostics, "OPERATION") == 0L);
        checks.put("targetHealthUnchangedThroughOriginalDeadline", targetUnchanged());
    }

    private boolean targetUnchanged() {
        return target != null && target.isAddedToLevel() && !target.isRemoved()
                && target.getHealth() == 10.0F && target.getAbsorptionAmount() == 0.0F;
    }

    private void captureAndDropOldCallReferences() {
        oldCallReferences.put("rootFrame", new WeakReference<>(producer.rootFrame));
        oldCallReferences.put("rootHandoff", new WeakReference<>(producer.rootOwner));
        oldCallReferences.put("binding", new WeakReference<>(producer.binding));
        oldCallReferences.put("work", new WeakReference<>(producer.work));
        oldCallReferences.put("carrier", new WeakReference<>(producer.carrier));
        if (DAMAGE_UNKNOWN) {
            oldCallReferences.put("damageFrame", new WeakReference<>(producer.damageFrame));
            oldCallReferences.put("damageHandoff", new WeakReference<>(producer.damageOwner));
        }
        producer.rootFrame = null;
        producer.rootOwner = null;
        producer.damageFrame = null;
        producer.damageOwner = null;
        producer.binding = null;
        producer.work = null;
        producer.carrier = null;
        producer.target = null;
        producer.debitMaterial = null;
        producer.expectedActor = null;
        producer.expectedServer = null;
        producer.expectedSource = null;
        producer.negativeControls = null;
        initialMana = null;
        initialCooldown = null;
        oldReferencesDropped = true;
    }

    private void observeCollection(MinecraftServer server) {
        boolean collected = oldCallReferences.values().stream().allMatch(reference -> reference.get() == null);
        if (collected) {
            var states = new LinkedHashMap<String, Object>();
            oldCallReferences.forEach((name, reference) -> states.put(name, reference.get() == null));
            observations.put("oldCallCollection", states);
            observations.put("collectionAttempts", collectionAttempts);
            checks.put("oldFramesHandoffsBindingWorkCarrierCollected", true);
            checks.put("serviceAndOriginalAuthorityStillLive", runtime != null && authority != null
                    && authority.isOwnerThread() && server.isRunning() && !server.isStopped()
                    && currentAuthenticatedPlay(server, actor)
                    && source.qualifiedForV0Capture(source.body(actor)));
            checks.put("oneUnknownAndNoReplayAfterCollection", originalReceiptCounts());
            finish(server, null, false);
        } else if ((long) server.getTickCount() - collectionTick >= 4L * MAX_COLLECTION_ATTEMPTS) {
            checks.put("oldFramesHandoffsBindingWorkCarrierCollected", false);
            observations.put("collectionAttempts", collectionAttempts);
            finish(server, "BOUNDED_WEAK_COLLECTION_UNPROVED", true);
        } else if (collectionAttempts < MAX_COLLECTION_ATTEMPTS
                && ((long) server.getTickCount() - collectionTick) / 4L >= collectionAttempts) {
            collectionAttempts++;
            System.gc();
        }
    }

    private Map<String, Object> presentationScalars() {
        var events = presentation.bufferedEventsForTesting().stream()
                .map(event -> Map.<String, Object>of("sequence", event.sequence(), "kind", event.kind().name())).toList();
        return Map.of("sequenceHighWater", presentation.presentationSequenceHighWaterForTesting(),
                "bufferedEvents", events);
    }

    private void finish(MinecraftServer server, String refusal, boolean incomplete) {
        if (done) {
            return;
        }
        done = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("format", "gramarye-a04-qualification-v2");
        result.put("case", DAMAGE_UNKNOWN ? "U2" : "U1");
        result.put("composition", "STATIC_TEST_BOOTSTRAP");
        result.put("terminalNativeTick", server.getTickCount());
        result.put("observations", observations);
        result.put("checks", checks);
        result.put("receipts", new ArrayList<>(producer.receipts));
        result.put("counts", Map.of("rootOutcomeProducerEntries", producer.rootAttempts,
                "damageOutcomeProducerEntries", producer.damageAttempts, "childOutcomeProducerEntries", producer.childAttempts,
                "rootNativeApplied", producer.rootNativeApplied, "unknownReturns", producer.unknownReturns,
                "unknownReceipts", producer.unknownReceipts, "completedCallbacks", producer.completedCallbacks,
                "cleanupProbeReceipts", producer.probeReceipts));
        if (refusal != null) {
            result.put("refusal", refusal);
        }
        boolean accepted = refusal == null && producer.failure.isEmpty()
                && producer.probeReceipts == 1 && !checks.isEmpty()
                && checks.values().stream().allMatch(Boolean.TRUE::equals);
        result.put("result", accepted ? "PASS" : incomplete ? "INCOMPLETE" : "FAIL");
        releaseCallReferences();
        var name = DAMAGE_UNKNOWN ? "a04-u2-qualification.json" : "a04-u1-qualification.json";
        try {
            Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve(name),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException failure) {
            throw new IllegalStateException("A04_RESULT_WRITE_FAILED", failure);
        }
    }

    private void releaseCallReferences() {
        producer.rootFrame = null;
        producer.rootOwner = null;
        producer.damageFrame = null;
        producer.damageOwner = null;
        producer.binding = null;
        producer.work = null;
        producer.carrier = null;
        producer.target = null;
        producer.debitMaterial = null;
        producer.expectedActor = null;
        producer.expectedServer = null;
        producer.expectedSource = null;
        producer.negativeControls = null;
        initialMana = null;
        initialCooldown = null;
        actor = null;
        source = null;
        foundation = null;
        presentation = null;
        target = null;
        authority = null;
        runtime = null;
        negativeAdapter = null;
    }

    private static boolean currentAuthenticatedPlay(MinecraftServer server, ServerPlayer candidate) {
        if (candidate == null || candidate.getServer() != server || candidate.isFakePlayer()
                || !server.usesAuthentication() || !candidate.isAlive() || candidate.isRemoved()
                || server.getPlayerList().getPlayer(candidate.getUUID()) != candidate) {
            return false;
        }
        var listener = candidate.connection;
        return listener != null && listener.player == candidate && listener.isAcceptingMessages()
                && listener.protocol() == ConnectionProtocol.PLAY
                && listener.getConnection().isConnected()
                && !listener.getConnection().isMemoryConnection()
                && listener.getConnection().getPacketListener() == listener;
    }

    private static long rootCount(P11QualifiedSourceOwner.Diagnostics diagnostics, String kind) {
        return diagnostics.nativeResponsibilities().roots().stream()
                .filter(root -> root.kind().equals(kind))
                .mapToLong(P11QualifiedSourceOwner.NativeRootDiagnostic::count).findFirst().orElse(-1L);
    }

    private static Map<String, Object> sourceScalars(P11QualifiedSourceOwner.Diagnostics diagnostics) {
        return Map.of("bodyComplete", diagnostics.bodyComplete(), "candidatePresent", diagnostics.candidatePresent(),
                "sourceFault", diagnostics.sourceFault(), "sourceInput", diagnostics.sourceInput(),
                "sourceEpoch", diagnostics.sourceEpoch(), "sourceVersion", diagnostics.sourceVersion(),
                "work", rootCount(diagnostics, "WORK"), "operation", rootCount(diagnostics, "OPERATION"));
    }
}
