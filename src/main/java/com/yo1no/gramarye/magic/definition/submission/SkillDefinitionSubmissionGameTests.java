package com.yo1no.gramarye.magic.definition.submission;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionPayloadInspector;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.capability.ControlClass;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.capability.TriggerGranularity;
import com.yo1no.gramarye.magic.capability.TriggerSourceScope;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.inspection.ActionReferenceProjection;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.inspection.TriggerReferenceProjection;
import com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationPlan;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentGameTests;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort;
import com.yo1no.gramarye.magic.definition.store.SkillQuota;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.definition.validation.SkillDefinitionProjector;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalyzer;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayloadInspector;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Normal P4-D2-B GameTests for Store-first authenticated submission composition. */
@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillDefinitionSubmissionGameTests {
    private static final String SAVED_DATA_NAME = "gramarye_skill_definitions";
    private static final ResourceLocation TRIGGER_ID = id("p4_d2_b_test_trigger");
    private static final ResourceLocation ACTION_ID = id("p4_d2_b_test_action");
    private static final TriggerDescriptor TRIGGER_DESCRIPTOR = new TriggerDescriptor();
    private static final ActionDescriptor ACTION_DESCRIPTOR = new ActionDescriptor();
    private static final SkillId SUCCESS_SKILL_ID = new SkillId(
            UUID.fromString("d2b00000-0000-4000-8000-000000000001"));
    private static final SkillId DRIFT_SKILL_ID = new SkillId(
            UUID.fromString("d2b00000-0000-4000-8000-000000000002"));
    private static final UUID SUCCESS_PLAYER_ID =
            UUID.fromString("d2b00000-0000-4000-8000-000000000011");
    private static final UUID DRIFT_PLAYER_ID =
            UUID.fromString("d2b00000-0000-4000-8000-000000000012");
    private static final SavedData.Factory<SavedData> CACHE_HIT_ONLY_FACTORY =
            new SavedData.Factory<>(
                    () -> {
                        throw new AssertionError("submission GameTest expected a cache hit");
                    },
                    (tag, provider) -> {
                        throw new AssertionError("submission GameTest must not read disk via cache get");
                    });

    private SkillDefinitionSubmissionGameTests() {
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 200)
    public static void fullSubmissionCommitsStoreJournalThenAttachmentExactlyOnce(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.isSameThread(), "submission GameTest requires server thread");
        var attachments = PlayerSkillAttachmentGameTests.newServiceForSubmissionGameTests();
        try (var store = installIsolatedStore(server, helper, attachments)) {
            var player = player(server, SUCCESS_PLAYER_ID, "p4-d2-b-success");
            var draft = installCompleteDraft(
                    helper, attachments, player, SUCCESS_SKILL_ID);
            var dependencies = dependencies(
                    attachments, store.port(), player, false);
            var service = new SkillDefinitionSubmissionService(dependencies);

            var outcome = service.submit(player, SUCCESS_SKILL_ID);
            helper.assertTrue(
                    outcome instanceof SkillSubmissionCompositionOutcome.Committed committed
                            && committed.reference().skillId().equals(SUCCESS_SKILL_ID)
                            && committed.reference().revision().value() == 0
                            && !committed.report().hasErrors()
                            && committed.report() == dependencies.preparedReport,
                    "normal submission must commit revision zero with exact report identity");
            var target = ((SkillSubmissionCompositionOutcome.Committed) outcome).reference();

            assertCommittedStoreAndJournal(helper, store, server, player, target);
            var latest = attachments.findLatestState(player, SUCCESS_SKILL_ID);
            helper.assertTrue(
                    latest instanceof PlayerSkillAttachmentService.Available<?> available
                            && available.value() instanceof Optional<?> optional
                            && optional.orElse(null)
                                    instanceof PlayerSkillAttachmentService.LatestStateView view
                            && view.pointer().equals(Optional.of(target))
                            && view.mutationGeneration() == 1,
                    "Attachment latest pointer and generation must publish after Store commit");
            assertDraftRetained(helper, attachments, player, draft);
            helper.assertTrue(
                    dependencies.authorityCalls == 1
                            && dependencies.policyCalls == 1
                            && dependencies.transitionPrepareCalls == 1
                            && dependencies.storePrepareCalls == 1
                            && dependencies.currentnessCalls == 1
                            && dependencies.storeCommitCalls == 1
                            && dependencies.publishCalls == 1,
                    "normal submission must not retry or repeat any composition boundary");
            helper.assertTrue(store.installedAdapter().isDirty(),
                    "Store/journal publication must mark SavedData dirty before Attachment publish");
        }
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 200)
    public static void postCommitAttachmentDriftReturnsPendingRecovery(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.isSameThread(), "submission GameTest requires server thread");
        var attachments = PlayerSkillAttachmentGameTests.newServiceForSubmissionGameTests();
        try (var store = installIsolatedStore(server, helper, attachments)) {
            var player = player(server, DRIFT_PLAYER_ID, "p4-d2-b-drift");
            var draft = installCompleteDraft(helper, attachments, player, DRIFT_SKILL_ID);
            var dependencies = dependencies(attachments, store.port(), player, true);
            var service = new SkillDefinitionSubmissionService(dependencies);

            var outcome = service.submit(player, DRIFT_SKILL_ID);
            helper.assertTrue(
                    outcome instanceof SkillSubmissionCompositionOutcome
                                    .CommittedPendingAttachmentRecovery pending
                            && pending.reference().skillId().equals(DRIFT_SKILL_ID)
                            && pending.failure().code()
                                    == SkillSubmissionCompositionOutcome
                                            .AttachmentPublicationFailureCode.STATE_CHANGED
                            && pending.report() == dependencies.preparedReport,
                    "postcommit Attachment drift must retain the committed target for recovery");
            var target = ((SkillSubmissionCompositionOutcome
                    .CommittedPendingAttachmentRecovery) outcome).reference();

            assertCommittedStoreAndJournal(helper, store, server, player, target);
            var latest = attachments.findLatestState(player, DRIFT_SKILL_ID);
            helper.assertTrue(
                    latest instanceof PlayerSkillAttachmentService.Available<?> available
                            && available.value().equals(Optional.empty()),
                    "failed Attachment publication must retain the old implicit latest state");
            assertDraftRetained(helper, attachments, player, draft);
            helper.assertTrue(
                    dependencies.driftMutations == 1
                            && dependencies.authorityCalls == 1
                            && dependencies.policyCalls == 1
                            && dependencies.transitionPrepareCalls == 1
                            && dependencies.storePrepareCalls == 1
                            && dependencies.currentnessCalls == 1
                            && dependencies.storeCommitCalls == 1
                            && dependencies.publishCalls == 1,
                    "postcommit drift must not reprepare, recommit, or republish");
            helper.assertTrue(store.installedAdapter().isDirty(),
                    "pending recovery must retain the dirty Store/journal publication");
        }
        helper.succeed();
    }

    private static CountingDependencies dependencies(
            PlayerSkillAttachmentService attachments,
            SkillDefinitionStoreSubmissionPort port,
            ServerPlayer player,
            boolean driftAfterCommit) {
        var pipeline = new SkillSubmissionPreparationPipeline(
                new SkillCandidateResolver(
                        new FixedTriggerLookup(), new FixedActionLookup()),
                new SkillValidationAnalyzer(
                        new NodeProjectionResolver(), ProfileAvailabilityView.unknown()),
                new SkillDefinitionProjector());
        var production = new SkillDefinitionSubmissionService.ProductionDependencies(
                attachments,
                port,
                SkillSubmissionPolicyProvider.defaults(),
                pipeline);
        return new CountingDependencies(
                production, attachments, player, driftAfterCommit);
    }

    private static SkillDraft installCompleteDraft(
            GameTestHelper helper,
            PlayerSkillAttachmentService attachments,
            ServerPlayer player,
            SkillId skillId) {
        var creation = new SkillDraftCreationService(attachments, () -> skillId)
                .createDraft(player);
        helper.assertTrue(
                creation instanceof SkillDraftCreationService.Created created
                        && created.skillId().equals(skillId),
                "Draft creation must mint and install the requested deterministic route");
        var draft = completeDraft(skillId);
        var replacement = attachments.putDraft(player, draft);
        helper.assertTrue(
                replacement instanceof PlayerSkillAttachmentService.Available<?> available
                        && available.value() == PlayerSkillAttachmentService.Applied.INSTANCE,
                "complete Draft must replace the newly created empty Draft");
        return draft;
    }

    private static SkillDraft completeDraft(SkillId skillId) {
        var triggerPayload = new JsonObject();
        triggerPayload.addProperty("value", 41);
        var actionPayload = new JsonObject();
        actionPayload.addProperty("value", 42);
        var node = new DraftNode(
                DraftTriggerSlot.present(new DefinitionEnvelope(
                        TRIGGER_ID,
                        0,
                        new Dynamic<>(JsonOps.INSTANCE, triggerPayload))),
                DraftActionSlot.present(new DefinitionEnvelope(
                        ACTION_ID,
                        0,
                        new Dynamic<>(JsonOps.INSTANCE, actionPayload))),
                AppearanceOverrideDocument.none());
        return new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                skillId,
                Optional.empty(),
                List.of(node),
                AppearanceDocument.defaultAppearance());
    }

    private static void assertCommittedStoreAndJournal(
            GameTestHelper helper,
            StoreFixture store,
            MinecraftServer server,
            ServerPlayer player,
            SkillReference target) {
        var found = store.service().find(server, target);
        helper.assertTrue(
                found instanceof SkillSubsystemResult.Available<?> available
                        && available.value() instanceof Optional<?> optional
                        && optional.isPresent(),
                "committed target must be readable through the controlled Store service");
        var owner = store.service().ownerOf(server, target.skillId());
        helper.assertTrue(
                owner instanceof SkillSubsystemResult.Available<?> available
                        && available.value().equals(
                                Optional.of(new SkillOwnerId(player.getUUID()))),
                "committed Store owner must derive from the authenticated player UUID");
        helper.assertTrue(
                store.port().journalStatus(server)
                        instanceof SkillDefinitionStoreSubmissionPort.JournalStatus.Ready ready
                        && ready.entryCount() == 1,
                "one successful Store commit must retain one pending journal entry");
        helper.assertTrue(
                store.port().journalRoots(server)
                        instanceof SkillDefinitionStoreSubmissionPort.JournalRootProjection
                                .Available roots
                        && roots.references().equals(List.of(target)),
                "pending journal roots must retain the exact committed target");
    }

    private static void assertDraftRetained(
            GameTestHelper helper,
            PlayerSkillAttachmentService attachments,
            ServerPlayer player,
            SkillDraft draft) {
        var found = attachments.findDraft(player, draft.skillId());
        helper.assertTrue(
                found instanceof PlayerSkillAttachmentService.Available<?> available
                        && available.value().equals(Optional.of(draft)),
                "success and postcommit failure must retain the authoritative Draft unchanged");
    }

    private static StoreFixture installIsolatedStore(
            MinecraftServer server,
            GameTestHelper helper,
            PlayerSkillAttachmentService attachments) {
        var storage = server.overworld().getDataStorage();
        var original = Objects.requireNonNull(
                storage.get(CACHE_HIT_ONLY_FACTORY, SAVED_DATA_NAME),
                "startup SavedData adapter");
        IEventBus bus = BusBuilder.builder().build();
        var service = SkillDefinitionStoreService.registerOn(
                bus, attachments, (loginServer, actor) -> {});
        StoreFixture fixture = null;
        try {
            bus.start();
            bus.post(new ServerStartingEvent(server));
            var installed = Objects.requireNonNull(
                    storage.get(CACHE_HIT_ONLY_FACTORY, SAVED_DATA_NAME),
                    "isolated SavedData adapter");
            fixture = new StoreFixture(
                    server,
                    bus,
                    service,
                    service.submissionPort(),
                    storage,
                    original,
                    installed);
            helper.assertTrue(
                    service.submissionPort().journalStatus(server)
                            instanceof SkillDefinitionStoreSubmissionPort.JournalStatus.Ready ready
                            && ready.entryCount() == 0,
                    "D2-B isolated ServerStarting must install then bootstrap the journal once");
            return fixture;
        } catch (RuntimeException failure) {
            if (fixture == null) {
                stopAndRestoreStoreFixture(server, bus, storage, original, failure);
            } else {
                fixture.closeAfterFailure(failure);
            }
            throw failure;
        }
    }

    private static void stopAndRestoreStoreFixture(
            MinecraftServer server,
            IEventBus bus,
            DimensionDataStorage storage,
            SavedData originalAdapter,
            Throwable pendingFailure) {
        Throwable cleanupFailure = null;
        try {
            bus.post(new ServerStoppedEvent(server));
        } catch (RuntimeException failure) {
            cleanupFailure = failure;
        }
        try {
            storage.set(SAVED_DATA_NAME, originalAdapter);
        } catch (RuntimeException failure) {
            if (cleanupFailure == null) {
                cleanupFailure = failure;
            } else if (cleanupFailure != failure) {
                cleanupFailure.addSuppressed(failure);
            }
        }
        if (pendingFailure != null) {
            if (cleanupFailure != null && cleanupFailure != pendingFailure) {
                pendingFailure.addSuppressed(cleanupFailure);
            }
            return;
        }
        if (cleanupFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
    }

    private static ServerPlayer player(
            MinecraftServer server, UUID playerId, String name) {
        var cookie = CommonListenerCookie.createInitial(
                new GameProfile(playerId, name), false);
        return new ServerPlayer(
                server,
                server.overworld(),
                cookie.gameProfile(),
                cookie.clientInformation());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private record StoreFixture(
            MinecraftServer server,
            IEventBus bus,
            SkillDefinitionStoreService service,
            SkillDefinitionStoreSubmissionPort port,
            DimensionDataStorage storage,
            SavedData originalAdapter,
            SavedData installedAdapter) implements AutoCloseable {
        private StoreFixture {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(bus, "bus");
            Objects.requireNonNull(service, "service");
            Objects.requireNonNull(port, "port");
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(originalAdapter, "originalAdapter");
            Objects.requireNonNull(installedAdapter, "installedAdapter");
        }

        @Override
        public void close() {
            stopAndRestoreStoreFixture(server, bus, storage, originalAdapter, null);
        }

        private void closeAfterFailure(Throwable failure) {
            stopAndRestoreStoreFixture(server, bus, storage, originalAdapter, failure);
        }
    }

    private static final class CountingDependencies
            implements SkillDefinitionSubmissionService.Dependencies {
        private final SkillDefinitionSubmissionService.Dependencies delegate;
        private final PlayerSkillAttachmentService attachments;
        private final ServerPlayer player;
        private final boolean driftAfterCommit;
        private int authorityCalls;
        private int policyCalls;
        private int transitionPrepareCalls;
        private int storePrepareCalls;
        private int currentnessCalls;
        private int storeCommitCalls;
        private int publishCalls;
        private int driftMutations;
        private ValidationResult preparedReport;

        private CountingDependencies(
                SkillDefinitionSubmissionService.Dependencies delegate,
                PlayerSkillAttachmentService attachments,
                ServerPlayer player,
                boolean driftAfterCommit) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.attachments = Objects.requireNonNull(attachments, "attachments");
            this.player = Objects.requireNonNull(player, "player");
            this.driftAfterCommit = driftAfterCommit;
        }

        @Override
        public PlayerSkillAttachmentService.Result<Optional<SkillDraft>> findDraft(
                Object playerIdentity, SkillId skillId) {
            return delegate.findDraft(playerIdentity, skillId);
        }

        @Override
        public DraftSubmissionPrecheck precheck(SkillDraft draft) {
            return delegate.precheck(draft);
        }

        @Override
        public SkillDefinitionStoreSubmissionPort.AuthoritySnapshot
                observeSubmissionAuthority(
                        Object serverIdentity, SkillId skillId, SkillOwnerId owner) {
            authorityCalls++;
            return delegate.observeSubmissionAuthority(serverIdentity, skillId, owner);
        }

        @Override
        public SubmissionAuthorityCheck checkAuthority(
                DraftSubmissionPrecheck.Ready ready,
                SkillSubmissionAuthorizationResult authorization) {
            return delegate.checkAuthority(ready, authorization);
        }

        @Override
        public SkillSubmissionOutcome map(DraftSubmissionPrecheck.Invalid invalid) {
            return delegate.map(invalid);
        }

        @Override
        public SkillSubmissionOutcome map(
                SubmissionAuthorityCheck.IdentityRejected rejected) {
            return delegate.map(rejected);
        }

        @Override
        public SkillSubmissionOutcome map(SubmissionAuthorityCheck.Conflict conflict) {
            return delegate.map(conflict);
        }

        @Override
        public SkillSubmissionPolicySnapshot snapshotPolicy(Object serverIdentity) {
            policyCalls++;
            return delegate.snapshotPolicy(serverIdentity);
        }

        @Override
        public SkillSubmissionOutcome prepareAndMap(
                SubmissionAuthorityCheck.Passed passed, ValidationContext context) {
            var outcome = delegate.prepareAndMap(passed, context);
            if (outcome instanceof SkillSubmissionOutcome.Prepared prepared) {
                preparedReport = prepared.report();
            }
            return outcome;
        }

        @Override
        public SkillDefinitionSubmissionService.TransitionStep
                prepareLatestTransitionToCurrent(
                        Object playerIdentity, SkillId skillId, SkillReference target) {
            transitionPrepareCalls++;
            return delegate.prepareLatestTransitionToCurrent(
                    playerIdentity, skillId, target);
        }

        @Override
        public SkillDefinitionSubmissionService.StorePreparationStep prepareSubmissionCommit(
                Object serverIdentity,
                SkillSubmissionPlan plan,
                SkillQuota quota,
                Object transitionHandle) {
            storePrepareCalls++;
            return delegate.prepareSubmissionCommit(
                    serverIdentity, plan, quota, transitionHandle);
        }

        @Override
        public PlayerSkillAttachmentService.Result<
                        PlayerSkillAttachmentService.TransitionCurrentness>
                checkPreparedTransitionCurrent(
                        Object playerIdentity, Object transitionHandle) {
            currentnessCalls++;
            return delegate.checkPreparedTransitionCurrent(
                    playerIdentity, transitionHandle);
        }

        @Override
        public SkillDefinitionStoreSubmissionPort.SubmissionCommitResult
                commitPreparedSubmission(Object serverIdentity, Object storeHandle) {
            storeCommitCalls++;
            var result = delegate.commitPreparedSubmission(serverIdentity, storeHandle);
            if (driftAfterCommit
                    && result
                            instanceof SkillDefinitionStoreSubmissionPort.SubmissionCommitResult
                                    .Committed) {
                driftMutations++;
                var drift = attachments.setEditorState(
                        player,
                        new PlayerSkillAttachmentService.EditorStateView(
                                Optional.of(DRIFT_SKILL_ID), OptionalInt.empty()));
                if (!(drift instanceof PlayerSkillAttachmentService.Available<?> available)
                        || available.value() != PlayerSkillAttachmentService.Applied.INSTANCE) {
                    throw new AssertionError("postcommit Attachment drift was not applied");
                }
            }
            return result;
        }

        @Override
        public PlayerSkillAttachmentService.Result<PlayerSkillAttachmentService.MutationOutcome>
                publishPreparedTransition(
                        Object playerIdentity, Object transitionHandle) {
            publishCalls++;
            return delegate.publishPreparedTransition(playerIdentity, transitionHandle);
        }
    }

    private record FixedTriggerLookup(Map<ResourceLocation, TriggerType<?>> entries)
            implements TriggerTypeLookup {
        private FixedTriggerLookup() {
            this(Map.of(TRIGGER_ID, TRIGGER_DESCRIPTOR));
        }

        private FixedTriggerLookup {
            entries = Map.copyOf(entries);
        }

        @Override
        public Optional<TriggerType<?>> find(ResourceLocation typeId) {
            return Optional.ofNullable(entries.get(typeId));
        }

        @Override
        public Optional<ResourceLocation> keyOf(TriggerType<?> descriptor) {
            return descriptor == TRIGGER_DESCRIPTOR ? Optional.of(TRIGGER_ID) : Optional.empty();
        }
    }

    private record FixedActionLookup(Map<ResourceLocation, ActionType<?>> entries)
            implements ActionTypeLookup {
        private FixedActionLookup() {
            this(Map.of(ACTION_ID, ACTION_DESCRIPTOR));
        }

        private FixedActionLookup {
            entries = Map.copyOf(entries);
        }

        @Override
        public Optional<ActionType<?>> find(ResourceLocation typeId) {
            return Optional.ofNullable(entries.get(typeId));
        }

        @Override
        public Optional<ResourceLocation> keyOf(ActionType<?> descriptor) {
            return descriptor == ACTION_DESCRIPTOR ? Optional.of(ACTION_ID) : Optional.empty();
        }
    }

    private record TriggerData(int value) implements TriggerPayload {
        private static final MapCodec<TriggerData> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.INT.fieldOf("value").forGetter(TriggerData::value))
                        .apply(instance, TriggerData::new));
    }

    private record ActionData(int value) implements ActionPayload {
        private static final MapCodec<ActionData> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.INT.fieldOf("value").forGetter(ActionData::value))
                        .apply(instance, ActionData::new));
    }

    private static final class TriggerDescriptor implements TriggerType<TriggerData> {
        private static final TriggerCapabilities CAPABILITIES = new TriggerCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(new TriggerEventKind(id("p4_d2_b_test_event"))),
                Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                Set.of(TriggerGranularity.PER_EVENT));
        private static final TriggerReferenceProjection PROJECTION =
                new TriggerReferenceProjection(
                        SourceSelection.NONE, TargetSelection.NONE, false, List.of());

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public PayloadMigrationPlan payloadMigrationPlan() {
            return PayloadMigrationPlan.empty();
        }

        @Override
        public Optional<TriggerPayloadInspector<TriggerData>> payloadInspector() {
            return Optional.of(payload -> new PayloadInspectionResult.Success<>(PROJECTION));
        }

        @Override
        public MapCodec<TriggerData> payloadCodec() {
            return TriggerData.CODEC;
        }

        @Override
        public TriggerCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(
                TriggerData payload, ValidationContext context) {
            return ValidationResult.valid();
        }
    }

    private static final class ActionDescriptor implements ActionType<ActionData> {
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
        private static final ActionReferenceProjection PROJECTION =
                new ActionReferenceProjection(
                        SourceSelection.NONE, TargetSelection.NONE, List.of(), Set.of());

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public PayloadMigrationPlan payloadMigrationPlan() {
            return PayloadMigrationPlan.empty();
        }

        @Override
        public Optional<ActionPayloadInspector<ActionData>> payloadInspector() {
            return Optional.of(payload -> new PayloadInspectionResult.Success<>(PROJECTION));
        }

        @Override
        public MapCodec<ActionData> payloadCodec() {
            return ActionData.CODEC;
        }

        @Override
        public ActionCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(
                ActionData payload, ValidationContext context) {
            return ValidationResult.valid();
        }
    }
}
