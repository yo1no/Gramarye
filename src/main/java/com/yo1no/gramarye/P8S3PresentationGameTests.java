package com.yo1no.gramarye;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.api.id.EventId;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.capability.ControlClass;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.capability.TriggerGranularity;
import com.yo1no.gramarye.magic.capability.TriggerSourceScope;
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearance;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearanceOverride;
import com.yo1no.gramarye.magic.definition.validation.ValidatedActionReferenceProjection;
import com.yo1no.gramarye.magic.definition.validation.ValidatedNodeDefinition;
import com.yo1no.gramarye.magic.definition.validation.ValidatedNodeReferenceProjection;
import com.yo1no.gramarye.magic.definition.validation.ValidatedSkillDefinition;
import com.yo1no.gramarye.magic.definition.validation.ValidatedTriggerReferenceProjection;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Actual-platform S3 proof; this controlled source never registers a gameplay trigger. */
@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class P8S3PresentationGameTests {
    private static final ResourceLocation ACTIVE_CAST = id("active_cast");
    private static final ResourceLocation CONTROLLED_HIT = id("p8_controlled_hit");
    private static final RuntimeServerToken SERVER_TOKEN = new RuntimeServerToken(83L);
    private static final P6RuntimeExecutionBridge.AppliedFact ONE_APPLIED =
            new P6RuntimeExecutionBridge.AppliedFact(
                    P6RuntimeExecutionBridge.AppliedTerminal.SUCCEEDED,
                    1,
                    List.of(new P6RuntimeExecutionBridge.AppliedStep(
                            0, P6RuntimeExecutionBridge.AppliedStepKind.APPLIED)));
    private static final P6RuntimeExecutionBridge.AppliedFact TWO_APPLIED =
            new P6RuntimeExecutionBridge.AppliedFact(
                    P6RuntimeExecutionBridge.AppliedTerminal.SUCCEEDED,
                    2,
                    List.of(
                            new P6RuntimeExecutionBridge.AppliedStep(
                                    0, P6RuntimeExecutionBridge.AppliedStepKind.APPLIED),
                            new P6RuntimeExecutionBridge.AppliedStep(
                                    1, P6RuntimeExecutionBridge.AppliedStepKind.APPLIED)));
    private static final TriggerType<FixtureTriggerPayload> TRIGGER_TYPE =
            new FixtureTriggerType();
    private static final ActionType<FixtureActionPayload> ACTION_TYPE =
            new FixtureActionType();

    private P8S3PresentationGameTests() {}

    @GameTest(
            batch = "p8_s3",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 80)
    @SuppressWarnings("removal")
    public static void appliedFactHandoffRollsBackFailuresAndDrainsCurrentTick(
            GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var transport = new RecordingTransport(player.getUUID());
        var service = new P8ServerPresentationService(transport);
        try {
            service.startForTesting(server, emptyCatalog());
            long runtimeTick = server.getTickCount();
            RuntimeFixture prior = runtimeFixture(server, player, 1L, runtimeTick, ACTIVE_CAST);
            P6RuntimeExecutionCapability capability =
                    P6RuntimeExecutionCapability.forRuntimeAdapter();
            boolean[] invoked = {false};
            var adapter = new P6RuntimeExecutionPortAdapter(
                    capability,
                    service,
                    (actualCapability,
                                    actor,
                                    actionTypeKey,
                                    requestId,
                                    sourceEventId,
                                    targetId,
                                    magnitude,
                                    manaCost,
                                    guard,
                                    observer) -> {
                        helper.assertTrue(actualCapability == capability,
                                "adapter must hand the exact capability to the P6 bridge");
                        helper.assertTrue(observer != null,
                                "adapter must install the call-scoped applied-fact observer");
                        invoked[0] = true;
                        observer.observe(ONE_APPLIED);
                    },
                    (ignoredEvent, ignoredContext) -> Optional.empty());
            RuntimeExecutionBatch adapterBatch = adapter.executeMapped(
                    prior.event(),
                    prior.context(),
                    new P6RuntimeExecutionInput(
                            player,
                            id("p8_fixture_action"),
                            player.getUUID(),
                            1L,
                            0L));
            helper.assertTrue(invoked[0]
                            && adapterBatch.outcome() instanceof RuntimePortOutcome.Completed
                            && adapterBatch.children() == RuntimeChildPlan.EMPTY,
                    "adapter must complete with the exact empty child plan after observer handoff");
            helper.assertTrue(sequences(service).equals(List.of(1L)),
                    "adapter observer handoff must buffer the controlled prior entry");

            RuntimeFixture first = runtimeFixture(server, player, 2L, runtimeTick, ACTIVE_CAST);
            transport.failSecondCaptureWith(CaptureFailure.RUNTIME_EXCEPTION);
            new P8AppliedFactHandoff(service, first.event(), first.context())
                    .observe(TWO_APPLIED);
            helper.assertTrue(sequences(service).equals(List.of(1L)),
                    "observer RuntimeException must publish no call prefix and preserve prior state");
            helper.assertTrue(service.presentationSequenceHighWaterForTesting() == 3L,
                    "two-step RuntimeException rollback must retain both allocated sequence gaps");
            helper.assertTrue(service.hasRuntimeDiagnosticForTesting(
                            P8ServerRuntimeDiagnosticCode.OBSERVER_RUNTIME_EXCEPTION),
                    "observer RuntimeException must record its fixed diagnostic code");

            RuntimeFixture second = runtimeFixture(server, player, 3L, runtimeTick, ACTIVE_CAST);
            transport.failSecondCaptureWith(CaptureFailure.ERROR);
            Error actual = null;
            try {
                new P8AppliedFactHandoff(service, second.event(), second.context())
                        .observe(TWO_APPLIED);
            } catch (Error failure) {
                actual = failure;
            }
            helper.assertTrue(actual == transport.observerError,
                    "observer Error must propagate as the same object");
            helper.assertTrue(sequences(service).equals(List.of(1L)),
                    "observer Error must publish no call prefix and preserve prior state");
            helper.assertTrue(service.presentationSequenceHighWaterForTesting() == 5L,
                    "two-step Error rollback must retain both allocated sequence gaps");

            transport.clearCaptureFailure();
            RuntimeFixture third = runtimeFixture(server, player, 4L, runtimeTick, ACTIVE_CAST);
            helper.assertTrue(service.offerApplied(third.event(), third.context(), ONE_APPLIED)
                            == P8PresentationOfferOutcome.ACCEPTED,
                    "first complete applied fact must retain one distinct event");
            helper.assertTrue(service.offerApplied(third.event(), third.context(), ONE_APPLIED)
                            == P8PresentationOfferOutcome.DEGRADED,
                    "same-tick exact identity must coalesce to the earliest event");
            List<PresentationEvent> retained = service.bufferedEventsForTesting();
            helper.assertTrue(retained.size() == 2
                            && retained.getFirst().sequence() == 1L
                            && retained.get(1).sequence() == 6L
                            && service.presentationSequenceHighWaterForTesting() == 7L,
                    "coalescing must preserve prior state, retain the earliest duplicate, and consume the later gap");

            var replacement = P8ServerPresentationService.newReloadIdentityForTesting();
            service.stageCandidateForTesting(replacement, emptyCatalog());
            helper.assertTrue(service.activateCandidateForTesting(replacement),
                    "controlled reload candidate must activate");
            helper.assertTrue(service.bufferedEventsForTesting().isEmpty()
                            && service.presentationSequenceHighWaterForTesting() == 7L,
                    "activation must clear old-generation events without resetting sequence");

            RuntimeFixture fourth = runtimeFixture(server, player, 5L, runtimeTick, ACTIVE_CAST);
            helper.assertTrue(service.offerApplied(fourth.event(), fourth.context(), ONE_APPLIED)
                            == P8PresentationOfferOutcome.ACCEPTED,
                    "new generation must admit the next sequence");
            RuntimeFixture fifth = runtimeFixture(server, player, 6L, runtimeTick, ACTIVE_CAST);
            helper.assertTrue(service.offerApplied(fifth.event(), fifth.context(), ONE_APPLIED)
                            == P8PresentationOfferOutcome.ACCEPTED,
                    "new generation must admit a second distinct sequence");
            RuntimeFixture sixth = runtimeFixture(server, player, 7L, runtimeTick, ACTIVE_CAST);
            helper.assertTrue(service.offerApplied(sixth.event(), sixth.context(), ONE_APPLIED)
                            == P8PresentationOfferOutcome.ACCEPTED,
                    "new generation must admit a third distinct sequence");
            transport.submitModes = List.of(
                    SubmitMode.UNAVAILABLE,
                    SubmitMode.RUNTIME_EXCEPTION,
                    SubmitMode.ERROR);
            Error submitFailure = null;
            try {
                service.drainPresentationForTesting(server);
            } catch (Error failure) {
                submitFailure = failure;
            }
            helper.assertTrue(submitFailure == transport.submitError,
                    "transport Error must propagate as the same object after buffer expiry");
            helper.assertTrue(service.bufferedEventsForTesting().isEmpty()
                            && transport.submitAttempts.equals(List.of(8L, 9L, 10L))
                            && transport.submittedSequences.isEmpty(),
                    "drain must not retry UNAVAILABLE, must isolate RuntimeException, and must stop at the same Error");
            helper.assertTrue(service.hasRuntimeDiagnosticForTesting(
                            P8ServerRuntimeDiagnosticCode.EVENT_TRANSPORT_RUNTIME_EXCEPTION),
                    "isolated transport RuntimeException must record its fixed diagnostic code");
            RuntimeFixture postDrain = runtimeFixture(
                    server, player, 8L, runtimeTick, ACTIVE_CAST);
            helper.assertTrue(service.offerApplied(
                                    postDrain.event(), postDrain.context(), ONE_APPLIED)
                            == P8PresentationOfferOutcome.DROPPED,
                    "a same-physical-tick offer after drain must be dropped without mutation");
            helper.assertTrue(service.presentationSequenceHighWaterForTesting() == 10L,
                    "post-drain rejection must not allocate another presentation sequence");
            RuntimeFixture olderTick = runtimeFixture(
                    server,
                    player,
                    9L,
                    Math.subtractExact(runtimeTick, 1L),
                    ACTIVE_CAST);
            RuntimeException regressionFailure = null;
            try {
                service.offerApplied(
                        olderTick.event(), olderTick.context(), ONE_APPLIED);
            } catch (RuntimeException failure) {
                regressionFailure = failure;
            }
            helper.assertTrue(regressionFailure instanceof IllegalArgumentException
                            && service.presentationSequenceHighWaterForTesting() == 10L,
                    "an older runtime tick after drain must be rejected as regression before allocation");
            RuntimeException duplicateDrain = null;
            try {
                service.drainPresentationForTesting(server);
            } catch (RuntimeException failure) {
                duplicateDrain = failure;
            }
            helper.assertTrue(duplicateDrain instanceof IllegalStateException
                            && service.bufferedEventsForTesting().isEmpty(),
                    "a duplicate physical-tick drain must fail closed without resurrecting work");
            assertOfferReentryGuards(helper, server, player, runtimeTick);
        } finally {
            service.stopForTesting();
            server.getPlayerList().remove(player);
        }
        helper.assertTrue(service.presentationSequenceHighWaterForTesting() == 0L
                        && service.bufferedEventsForTesting().isEmpty(),
                "stop must clear sequence and current-tick presentation state");
        helper.succeed();
    }

    @GameTest(
            batch = "p8_s3",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 80)
    @SuppressWarnings("removal")
    public static void hitRecipientsUseActualWatchersAndRevalidateCurrentIdentity(
            GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel targetLevel = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        NetworkRegistry.configureMockConnection(player.connection.getConnection());
        Vec3 playerPosition = player.position();
        Entity origin = new ArmorStand(
                targetLevel, playerPosition.x, playerPosition.y, playerPosition.z);
        Entity target = new ArmorStand(
                targetLevel, playerPosition.x + 2.0D, playerPosition.y, playerPosition.z);
        boolean originAdded = targetLevel.addFreshEntity(origin);
        boolean targetAdded = targetLevel.addFreshEntity(target);
        var transport = new RecordingTransport(player.getUUID());
        var service = new P8ServerPresentationService(transport);
        service.startForTesting(server, emptyCatalog());
        helper.runAfterDelay(10L, () -> {
            boolean passed = false;
            String failure = "P8 HIT watcher integration did not complete";
            try {
                targetLevel.getChunkSource().move(player);
                var watchers = targetLevel
                        .getChunkSource()
                        .chunkMap
                        .getPlayersWatching(target);
                if (!originAdded || !targetAdded) {
                    failure = "actual server level did not accept the controlled entities";
                } else if (!watchers.contains(player)) {
                    failure = "actual ChunkMap watcher path did not contain the current player";
                } else {
                    long runtimeTick = server.getTickCount();
                    RuntimeFixture hit = runtimeFixture(
                            server,
                            player,
                            origin,
                            target,
                            1L,
                            runtimeTick,
                            CONTROLLED_HIT);
                    P8PresentationOfferOutcome outcome = service.offerApplied(
                            hit.event(), hit.context(), ONE_APPLIED);
                    List<P8BufferedPresentation> buffered =
                            service.bufferedPresentationsForTesting();
                    if (outcome != P8PresentationOfferOutcome.ACCEPTED
                            || buffered.size() != 1
                            || buffered.getFirst().authoritativeTick() != runtimeTick
                            || buffered.getFirst().sourceEventId() != 1L
                            || buffered.getFirst().appliedStepIndex() != 0
                            || buffered.getFirst().event().kind() != PresentationEventKind.HIT
                            || buffered.getFirst().event().sourceSummary().targetEntityId().isEmpty()
                            || buffered.getFirst().event().sourceSummary().targetEntityId()
                                    .orElseThrow() != target.getId()
                            || buffered.getFirst().targetEntityUuid().isEmpty()
                            || !buffered.getFirst().targetEntityUuid().orElseThrow()
                                    .equals(target.getUUID())
                            || buffered.getFirst().recipients().size() != 1
                            || buffered.getFirst().recipients().getFirst().category()
                                    != PresentationOrdering.RecipientCategory.ORDINARY) {
                        failure = "actual offerApplied HIT path did not retain the exact applied-step identity";
                    } else {
                        P8BufferedPresentation retained = buffered.getFirst();
                        P8SelectedRecipient selected = retained.recipients().getFirst();
                        if (!P8RecipientSelector.remainsEligible(
                                server, retained, selected, transport)) {
                            failure = "current ready watcher was not eligible before drain";
                        } else {
                            transport.advanceConnectionEpoch();
                            if (P8RecipientSelector.remainsEligible(
                                    server, retained, selected, transport)) {
                                failure = "drain eligibility accepted a stale connection epoch";
                            } else {
                                transport.restoreConnectionEpoch();
                                target.discard();
                                service.drainPresentationForTesting(server);
                                passed = service.bufferedEventsForTesting().isEmpty()
                                        && transport.submitAttempts.isEmpty();
                                failure = "post-tick drain did not revalidate the removed watched target";
                            }
                        }
                    }
                }
            } finally {
                service.stopForTesting();
                origin.discard();
                target.discard();
                server.getPlayerList().remove(player);
            }
            if (passed) {
                helper.succeed();
            } else {
                helper.fail(failure);
            }
        });
    }

    private static void assertOfferReentryGuards(
            GameTestHelper helper,
            MinecraftServer server,
            ServerPlayer player,
            long runtimeTick) {
        RuntimeFixture isCurrentFixture = runtimeFixture(
                server, player, 10L, runtimeTick, ACTIVE_CAST);
        var isCurrentTransport = new RecordingTransport(player.getUUID());
        var isCurrentService = new P8ServerPresentationService(isCurrentTransport);
        try {
            isCurrentService.startForTesting(server, emptyCatalog());
            helper.assertTrue(isCurrentService.offerApplied(
                                    isCurrentFixture.event(),
                                    isCurrentFixture.context(),
                                    ONE_APPLIED)
                            == P8PresentationOfferOutcome.ACCEPTED,
                    "isCurrent reentry fixture must retain one delivery");
            isCurrentTransport.reenterOnNextIsCurrent(() -> isCurrentService.offerApplied(
                    isCurrentFixture.event(), isCurrentFixture.context(), ONE_APPLIED));
            RuntimeException isCurrentFailure = null;
            try {
                isCurrentService.drainPresentationForTesting(server);
            } catch (RuntimeException failure) {
                isCurrentFailure = failure;
            }
            helper.assertTrue(isCurrentFailure instanceof IllegalStateException
                            && isCurrentService.bufferedEventsForTesting().isEmpty(),
                    "synchronous isCurrent offer reentry must fail the invariant after buffer expiry");
        } finally {
            isCurrentService.stopForTesting();
        }

        RuntimeFixture submitFixture = runtimeFixture(
                server, player, 11L, runtimeTick, ACTIVE_CAST);
        var submitTransport = new RecordingTransport(player.getUUID());
        var submitService = new P8ServerPresentationService(submitTransport);
        try {
            submitService.startForTesting(server, emptyCatalog());
            helper.assertTrue(submitService.offerApplied(
                                    submitFixture.event(),
                                    submitFixture.context(),
                                    ONE_APPLIED)
                            == P8PresentationOfferOutcome.ACCEPTED,
                    "submit reentry fixture must retain one delivery");
            RuntimeException[] observedSubmitReentry = {null};
            submitTransport.reenterOnNextSubmit(() -> {
                try {
                    submitService.offerApplied(
                            submitFixture.event(), submitFixture.context(), ONE_APPLIED);
                } catch (RuntimeException failure) {
                    observedSubmitReentry[0] = failure;
                    throw failure;
                }
            });
            submitService.drainPresentationForTesting(server);
            helper.assertTrue(observedSubmitReentry[0] instanceof IllegalStateException
                            && submitService.bufferedEventsForTesting().isEmpty()
                            && submitTransport.submitAttempts.equals(List.of(1L))
                            && submitTransport.submittedSequences.isEmpty()
                            && submitService.hasRuntimeDiagnosticForTesting(
                                    P8ServerRuntimeDiagnosticCode.EVENT_TRANSPORT_RUNTIME_EXCEPTION),
                    "synchronous submit offer reentry must fail the invariant and be isolated by transport policy");
        } finally {
            submitService.stopForTesting();
        }
    }

    private static RuntimeFixture runtimeFixture(
            MinecraftServer server,
            ServerPlayer player,
            long eventId,
            long runtimeTick,
            ResourceLocation triggerKey) {
        return runtimeFixture(
                server,
                player,
                new ResolvedPlayerOrigin(player),
                NoResolvedRuntimeTarget.INSTANCE,
                eventId,
                runtimeTick,
                triggerKey);
    }

    private static RuntimeFixture runtimeFixture(
            MinecraftServer server,
            ServerPlayer player,
            Entity origin,
            Entity target,
            long eventId,
            long runtimeTick,
            ResourceLocation triggerKey) {
        return runtimeFixture(
                server,
                player,
                new ResolvedEntityOrigin(origin),
                new ResolvedEntityTarget(target),
                eventId,
                runtimeTick,
                triggerKey);
    }

    private static RuntimeFixture runtimeFixture(
            MinecraftServer server,
            ServerPlayer player,
            ResolvedRuntimeOrigin origin,
            ResolvedRuntimeTarget target,
            long eventId,
            long runtimeTick,
            ResourceLocation triggerKey) {
        SkillReference reference = new SkillReference(
                new SkillId(new UUID(0x8300000000004000L, eventId)),
                new SkillRevision(1));
        ValidatedNodeDefinition node = validatedNode();
        ValidatedSkillDefinition definition = construct(
                ValidatedSkillDefinition.class,
                new Class<?>[] {SkillReference.class, List.class, RuntimeNeutralAppearance.class},
                reference,
                List.of(node),
                RuntimeNeutralAppearance.Default.INSTANCE);
        var playerId = new RuntimePlayerId(player.getUUID());
        var instanceId = new SkillInstanceId(new UUID(0x8300000000005000L, eventId));
        var event = new RuntimeEvent(
                new EventId(eventId),
                instanceId,
                new RuntimeSkillInstanceSequence(eventId),
                new RuntimeCancellationToken(SERVER_TOKEN, instanceId),
                Optional.empty(),
                reference,
                0,
                runtimeTick,
                runtimeTick,
                Math.addExact(runtimeTick, 10L),
                0,
                0,
                RuntimeSchedulePersistence.MEMORY_ONLY,
                new PlayerRuntimeBudgetAttribution(SERVER_TOKEN, playerId),
                new PlayerOrigin(SERVER_TOKEN, player.serverLevel().dimension(), playerId),
                Optional.empty(),
                new RootTriggerCause(new TriggerEventKind(triggerKey)),
                NoRuntimeExecutionData.INSTANCE);
        var context = new RuntimeExecutionContext(
                server,
                definition,
                node,
                runtimeTick,
                SERVER_TOKEN,
                new ResolvedRuntimeReferenceContext(origin, target),
                new RuntimeExecutionBudget(0, 0, 0, 0, 0, 0, 0, 0, 0),
                () -> RuntimeExecutionGuardDecision.ALLOWED);
        return new RuntimeFixture(event, context);
    }

    private static List<Long> sequences(P8ServerPresentationService service) {
        return service.bufferedEventsForTesting().stream()
                .map(PresentationEvent::sequence)
                .toList();
    }

    private static ValidatedNodeDefinition validatedNode() {
        var trigger = new ResolvedTriggerDefinition<>(
                TRIGGER_TYPE, 0, new FixtureTriggerPayload(0));
        var action = new ResolvedActionDefinition<>(
                ACTION_TYPE, 0, new FixtureActionPayload(0));
        var triggerReferences = construct(
                ValidatedTriggerReferenceProjection.class,
                new Class<?>[] {
                    SourceSelection.class, TargetSelection.class, boolean.class, List.class
                },
                SourceSelection.NONE,
                TargetSelection.NONE,
                false,
                List.of());
        var actionReferences = construct(
                ValidatedActionReferenceProjection.class,
                new Class<?>[] {
                    SourceSelection.class, TargetSelection.class, List.class, Set.class
                },
                SourceSelection.NONE,
                TargetSelection.NONE,
                List.of(),
                Set.of());
        var references = construct(
                ValidatedNodeReferenceProjection.class,
                new Class<?>[] {
                    ValidatedTriggerReferenceProjection.class,
                    ValidatedActionReferenceProjection.class
                },
                triggerReferences,
                actionReferences);
        return construct(
                ValidatedNodeDefinition.class,
                new Class<?>[] {
                    int.class,
                    ResolvedTriggerDefinition.class,
                    ResolvedActionDefinition.class,
                    ValidatedNodeReferenceProjection.class,
                    RuntimeNeutralAppearanceOverride.class
                },
                0,
                trigger,
                action,
                references,
                RuntimeNeutralAppearanceOverride.None.INSTANCE);
    }

    private static P8ServerPresentationService.PreparedCatalog emptyCatalog() {
        return P8ServerPresentationService.loadCandidateForTesting(
                MagicRegistries.profileTypeRegistry(), Map.of());
    }

    private static <T> T construct(
            Class<T> type, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("controlled P8 fixture construction failed", cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("controlled P8 fixture construction failed", exception);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private record RuntimeFixture(RuntimeEvent event, RuntimeExecutionContext context) {}

    private enum CaptureFailure {
        NONE,
        RUNTIME_EXCEPTION,
        ERROR
    }

    private enum SubmitMode {
        READY,
        UNAVAILABLE,
        RUNTIME_EXCEPTION,
        ERROR
    }

    private static final class RecordingTransport implements P8PresentationTransport {
        private final UUID readyPlayer;
        private final Error observerError = new AssertionError("same P8 observer Error");
        private final Error submitError = new AssertionError("same P8 transport Error");
        private final List<Long> submitAttempts = new ArrayList<>();
        private final List<Long> submittedSequences = new ArrayList<>();
        private CaptureFailure captureFailure = CaptureFailure.NONE;
        private int captureCalls;
        private long connectionEpoch = 1L;
        private List<SubmitMode> submitModes = List.of();
        private Runnable nextIsCurrentCallback;
        private Runnable nextSubmitCallback;

        private RecordingTransport(UUID readyPlayer) {
            this.readyPlayer = readyPlayer;
        }

        private void failSecondCaptureWith(CaptureFailure failure) {
            captureFailure = failure;
            captureCalls = 0;
        }

        private void clearCaptureFailure() {
            captureFailure = CaptureFailure.NONE;
            captureCalls = 0;
        }

        private void advanceConnectionEpoch() {
            connectionEpoch = 2L;
        }

        private void restoreConnectionEpoch() {
            connectionEpoch = 1L;
        }

        private void reenterOnNextIsCurrent(Runnable callback) {
            nextIsCurrentCallback = callback;
        }

        private void reenterOnNextSubmit(Runnable callback) {
            nextSubmitCallback = callback;
        }

        @Override
        public Optional<P8RecipientIdentity> captureReadyIdentity(
                ServerPlayer player, long catalogGeneration) {
            if (!player.getUUID().equals(readyPlayer)) {
                return Optional.empty();
            }
            captureCalls++;
            if (captureCalls == 2 && captureFailure == CaptureFailure.RUNTIME_EXCEPTION) {
                throw new IllegalStateException("controlled P8 observer RuntimeException");
            }
            if (captureCalls == 2 && captureFailure == CaptureFailure.ERROR) {
                throw observerError;
            }
            return Optional.of(new P8RecipientIdentity(readyPlayer, connectionEpoch));
        }

        @Override
        public boolean isCurrent(
                ServerPlayer player,
                P8RecipientIdentity identity,
                long catalogGeneration) {
            Runnable callback = nextIsCurrentCallback;
            nextIsCurrentCallback = null;
            if (callback != null) {
                callback.run();
            }
            return player.getUUID().equals(readyPlayer)
                    && identity.playerId().equals(readyPlayer)
                    && identity.connectionEpoch() == connectionEpoch;
        }

        @Override
        public P8PresentationSubmissionResult submit(
                P8RecipientIdentity identity, PresentationEvent event) {
            submitAttempts.add(event.sequence());
            Runnable callback = nextSubmitCallback;
            nextSubmitCallback = null;
            if (callback != null) {
                callback.run();
            }
            SubmitMode submitMode = submitAttempts.size() <= submitModes.size()
                    ? submitModes.get(submitAttempts.size() - 1)
                    : SubmitMode.READY;
            if (submitMode == SubmitMode.UNAVAILABLE) {
                return P8PresentationSubmissionResult.UNAVAILABLE;
            }
            if (submitMode == SubmitMode.RUNTIME_EXCEPTION) {
                throw new IllegalStateException("controlled P8 transport RuntimeException");
            }
            if (submitMode == SubmitMode.ERROR) {
                throw submitError;
            }
            submittedSequences.add(event.sequence());
            return P8PresentationSubmissionResult.SUBMITTED;
        }
    }

    private record FixtureTriggerPayload(int value) implements TriggerPayload {
        private static final MapCodec<FixtureTriggerPayload> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                                Codec.INT.fieldOf("value").forGetter(FixtureTriggerPayload::value))
                        .apply(instance, FixtureTriggerPayload::new));
    }

    private record FixtureActionPayload(int value) implements ActionPayload {
        private static final MapCodec<FixtureActionPayload> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                                Codec.INT.fieldOf("value").forGetter(FixtureActionPayload::value))
                        .apply(instance, FixtureActionPayload::new));
    }

    private static final class FixtureTriggerType implements TriggerType<FixtureTriggerPayload> {
        private static final TriggerCapabilities CAPABILITIES = new TriggerCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(new TriggerEventKind(ACTIVE_CAST)),
                Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                Set.of(TriggerGranularity.PER_EVENT));

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public MapCodec<FixtureTriggerPayload> payloadCodec() {
            return FixtureTriggerPayload.CODEC;
        }

        @Override
        public TriggerCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(
                FixtureTriggerPayload payload, ValidationContext context) {
            return ValidationResult.valid();
        }
    }

    private static final class FixtureActionType implements ActionType<FixtureActionPayload> {
        private static final ActionCapabilities CAPABILITIES = new ActionCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                ControlClass.NONE,
                AppearanceParameterPolicy.none());

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public MapCodec<FixtureActionPayload> payloadCodec() {
            return FixtureActionPayload.CODEC;
        }

        @Override
        public ActionCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(
                FixtureActionPayload payload, ValidationContext context) {
            return ValidationResult.valid();
        }
    }
}
