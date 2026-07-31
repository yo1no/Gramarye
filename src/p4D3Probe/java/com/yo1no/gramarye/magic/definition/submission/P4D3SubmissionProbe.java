package com.yo1no.gramarye.magic.definition.submission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
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
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort;
import com.yo1no.gramarye.magic.definition.store.SkillQuota;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.definition.validation.SkillDefinitionProjector;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalyzer;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayloadInspector;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.lang.ref.Reference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Test-only actual D2 submission driver for the P4-D3 combined fixed-heap envelope. */
public final class P4D3SubmissionProbe {
    private static final ResourceLocation TRIGGER_ID = id("p4_d3_submission_trigger");
    private static final ResourceLocation ACTION_ID = id("p4_d3_submission_action");
    private static final ResourceLocation EVENT_ID = id("p4_d3_submission_event");
    private static final TriggerDescriptor TRIGGER_DESCRIPTOR = new TriggerDescriptor();
    private static final ActionDescriptor ACTION_DESCRIPTOR = new ActionDescriptor();
    private static final ValidationIssue WARNING = new ValidationIssue(
            ValidationIssueCode.fromNamespaceAndPath(
                    "gramarye", "p4_d3.warning_only_descriptor"),
            ValidationSeverity.WARNING,
            ValidationPath.empty(),
            ValidationIssueMetadata.none());
    private static final ValidationIssue EXPECTED_WARNING = new ValidationIssue(
            WARNING.code(),
            WARNING.severity(),
            ValidationPath.empty()
                    .field("nodes")
                    .index(0)
                    .field("action")
                    .field("payload"),
            WARNING.metadata());

    private P4D3SubmissionProbe() {
    }

    /**
     * Executes the real authenticated D2 facade against the authoritative loaded Draft.
     *
     * <p>The observer is invoked while the prepared Store handle, P3-C plan, validated
     * definition, prepared Attachment transition, and warning-only report remain live, and before
     * either currentness or commit is attempted.</p>
     */
    public static SubmissionFacts submitActual(
            ServerPlayer player,
            PlayerSkillAttachmentService attachments,
            SkillDefinitionStoreSubmissionPort storePort,
            SkillId skillId,
            PeakObserver observer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(attachments, "attachments");
        Objects.requireNonNull(storePort, "storePort");
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(observer, "observer");
        requireAuthoritativeDraft(player, attachments, skillId);

        var pipeline = new SkillSubmissionPreparationPipeline(
                new SkillCandidateResolver(
                        new FixedTriggerLookup(), new FixedActionLookup()),
                new SkillValidationAnalyzer(
                        new NodeProjectionResolver(), ProfileAvailabilityView.unknown()),
                new SkillDefinitionProjector());
        var production = new SkillDefinitionSubmissionService.ProductionDependencies(
                attachments,
                storePort,
                SkillSubmissionPolicyProvider.defaults(),
                pipeline);
        var counted = new CountingDependencies(production, observer);
        var outcome = new SkillDefinitionSubmissionService(counted).submit(player, skillId);
        if (!(outcome instanceof SkillSubmissionCompositionOutcome.Committed committed)) {
            throw new AssertionError(
                    "P4-D3 actual D2 submission was not committed: "
                            + outcome.getClass().getSimpleName());
        }
        if (counted.preparedReport == null
                || committed.report() != counted.preparedReport
                || committed.report().hasErrors()
                || !committed.report().issues().equals(List.of(EXPECTED_WARNING))) {
            throw new AssertionError(
                    "P4-D3 committed outcome did not retain the exact one-warning report");
        }
        if (!committed.reference().equals(counted.preparedTarget)) {
            throw new AssertionError(
                    "P4-D3 committed target changed after preparation");
        }
        var counts = counted.stageCounts();
        counts.requireExactOnce();
        return new SubmissionFacts(
                committed.reference(),
                committed.report().warnings().size(),
                counted.preparedDocumentNodes,
                counted.preparedDefinitionNodes,
                counts);
    }

    private static void requireAuthoritativeDraft(
            ServerPlayer player,
            PlayerSkillAttachmentService attachments,
            SkillId skillId) {
        var result = attachments.findDraft(player, skillId);
        if (!(result instanceof PlayerSkillAttachmentService.Available<?> available)
                || !(available.value() instanceof Optional<?> optional)
                || optional.isEmpty()) {
            throw new AssertionError("P4-D3 authoritative submission Draft is absent");
        }
        var draft = (SkillDraft) optional.orElseThrow();
        if (!draft.skillId().equals(skillId)
                || draft.draftSchemaVersion() != SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION
                || draft.baseRevision().isPresent()
                || draft.nodes().size() != 1
                || !(draft.nodes().getFirst().trigger() instanceof DraftTriggerSlot.Present trigger)
                || !(draft.nodes().getFirst().action() instanceof DraftActionSlot.Present action)
                || !trigger.definition().typeId().equals(TRIGGER_ID)
                || !action.definition().typeId().equals(ACTION_ID)
                || trigger.definition().schemaVersion() != 0
                || action.definition().schemaVersion() != 0) {
            throw new AssertionError(
                    "P4-D3 authoritative submission Draft shape changed");
        }
    }

    @FunctionalInterface
    public interface PeakObserver {
        void observe(PeakFacts facts);
    }

    public record PeakFacts(
            SkillReference target,
            int warningCount,
            int documentNodeCount,
            int validatedNodeCount) {
        public PeakFacts {
            Objects.requireNonNull(target, "target");
            requirePositive(warningCount, "warningCount");
            requirePositive(documentNodeCount, "documentNodeCount");
            requirePositive(validatedNodeCount, "validatedNodeCount");
        }
    }

    public record SubmissionFacts(
            SkillReference target,
            int warningCount,
            int documentNodeCount,
            int validatedNodeCount,
            StageCounts stageCounts) {
        public SubmissionFacts {
            Objects.requireNonNull(target, "target");
            requirePositive(warningCount, "warningCount");
            requirePositive(documentNodeCount, "documentNodeCount");
            requirePositive(validatedNodeCount, "validatedNodeCount");
            Objects.requireNonNull(stageCounts, "stageCounts");
        }
    }

    public record StageCounts(
            int findDraft,
            int precheck,
            int authorityObservation,
            int authorityCheck,
            int policySnapshot,
            int preparation,
            int transitionPreparation,
            int storePreparation,
            int peakObservation,
            int transitionCurrentness,
            int storeCommit,
            int attachmentPublication,
            int rejectionMapping) {
        public StageCounts {
            if (findDraft < 0
                    || precheck < 0
                    || authorityObservation < 0
                    || authorityCheck < 0
                    || policySnapshot < 0
                    || preparation < 0
                    || transitionPreparation < 0
                    || storePreparation < 0
                    || peakObservation < 0
                    || transitionCurrentness < 0
                    || storeCommit < 0
                    || attachmentPublication < 0
                    || rejectionMapping < 0) {
                throw new IllegalArgumentException("P4-D3 stage counts must be non-negative");
            }
        }

        private void requireExactOnce() {
            if (findDraft != 1
                    || precheck != 1
                    || authorityObservation != 1
                    || authorityCheck != 1
                    || policySnapshot != 1
                    || preparation != 1
                    || transitionPreparation != 1
                    || storePreparation != 1
                    || peakObservation != 1
                    || transitionCurrentness != 1
                    || storeCommit != 1
                    || attachmentPublication != 1
                    || rejectionMapping != 0) {
                throw new AssertionError("P4-D3 actual D2 stage multiplicity changed: " + this);
            }
        }
    }

    private static final class CountingDependencies
            implements SkillDefinitionSubmissionService.Dependencies {
        private final SkillDefinitionSubmissionService.Dependencies delegate;
        private final PeakObserver observer;
        private int findDraftCalls;
        private int precheckCalls;
        private int authorityObservationCalls;
        private int authorityCheckCalls;
        private int policySnapshotCalls;
        private int preparationCalls;
        private int transitionPreparationCalls;
        private int storePreparationCalls;
        private int peakObservationCalls;
        private int transitionCurrentnessCalls;
        private int storeCommitCalls;
        private int attachmentPublicationCalls;
        private int rejectionMappingCalls;
        private ValidationResult preparedReport;
        private SkillReference preparedTarget;
        private int preparedDocumentNodes;
        private int preparedDefinitionNodes;

        private CountingDependencies(
                SkillDefinitionSubmissionService.Dependencies delegate,
                PeakObserver observer) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.observer = Objects.requireNonNull(observer, "observer");
        }

        @Override
        public PlayerSkillAttachmentService.Result<Optional<SkillDraft>> findDraft(
                Object playerIdentity, SkillId skillId) {
            findDraftCalls++;
            return delegate.findDraft(playerIdentity, skillId);
        }

        @Override
        public DraftSubmissionPrecheck precheck(SkillDraft draft) {
            precheckCalls++;
            return delegate.precheck(draft);
        }

        @Override
        public SkillDefinitionStoreSubmissionPort.AuthoritySnapshot
                observeSubmissionAuthority(
                        Object serverIdentity, SkillId skillId, SkillOwnerId owner) {
            authorityObservationCalls++;
            return delegate.observeSubmissionAuthority(serverIdentity, skillId, owner);
        }

        @Override
        public SubmissionAuthorityCheck checkAuthority(
                DraftSubmissionPrecheck.Ready ready,
                SkillSubmissionAuthorizationResult authorization) {
            authorityCheckCalls++;
            return delegate.checkAuthority(ready, authorization);
        }

        @Override
        public SkillSubmissionOutcome map(DraftSubmissionPrecheck.Invalid invalid) {
            rejectionMappingCalls++;
            return delegate.map(invalid);
        }

        @Override
        public SkillSubmissionOutcome map(
                SubmissionAuthorityCheck.IdentityRejected rejected) {
            rejectionMappingCalls++;
            return delegate.map(rejected);
        }

        @Override
        public SkillSubmissionOutcome map(SubmissionAuthorityCheck.Conflict conflict) {
            rejectionMappingCalls++;
            return delegate.map(conflict);
        }

        @Override
        public SkillSubmissionPolicySnapshot snapshotPolicy(Object serverIdentity) {
            policySnapshotCalls++;
            return delegate.snapshotPolicy(serverIdentity);
        }

        @Override
        public SkillSubmissionOutcome prepareAndMap(
                SubmissionAuthorityCheck.Passed passed,
                ValidationContext context) {
            preparationCalls++;
            var outcome = delegate.prepareAndMap(passed, context);
            if (outcome instanceof SkillSubmissionOutcome.Prepared prepared) {
                preparedReport = prepared.report();
                var plan = prepared.plan();
                preparedTarget = new SkillReference(
                        plan.proposedDocument().skillId(),
                        plan.proposedDocument().revision());
                preparedDocumentNodes = plan.proposedDocument().nodes().size();
                preparedDefinitionNodes = plan.validatedDefinition().nodes().size();
            }
            return outcome;
        }

        @Override
        public SkillDefinitionSubmissionService.TransitionStep
                prepareLatestTransitionToCurrent(
                        Object playerIdentity, SkillId skillId, SkillReference target) {
            transitionPreparationCalls++;
            return delegate.prepareLatestTransitionToCurrent(
                    playerIdentity, skillId, target);
        }

        @Override
        public SkillDefinitionSubmissionService.StorePreparationStep prepareSubmissionCommit(
                Object serverIdentity,
                SkillSubmissionPlan plan,
                SkillQuota quota,
                Object transitionHandle) {
            storePreparationCalls++;
            var result = delegate.prepareSubmissionCommit(
                    serverIdentity, plan, quota, transitionHandle);
            if (result instanceof SkillDefinitionSubmissionService.PreparedStorePreparationStep) {
                peakObservationCalls++;
                observer.observe(new PeakFacts(
                        new SkillReference(
                                plan.proposedDocument().skillId(),
                                plan.proposedDocument().revision()),
                        Objects.requireNonNull(preparedReport, "preparedReport")
                                .warnings()
                                .size(),
                        plan.proposedDocument().nodes().size(),
                        plan.validatedDefinition().nodes().size()));
                Reference.reachabilityFence(preparedReport);
                Reference.reachabilityFence(plan.validatedDefinition());
                Reference.reachabilityFence(plan);
                Reference.reachabilityFence(transitionHandle);
                Reference.reachabilityFence(result);
            }
            return result;
        }

        @Override
        public PlayerSkillAttachmentService.Result<
                        PlayerSkillAttachmentService.TransitionCurrentness>
                checkPreparedTransitionCurrent(
                        Object playerIdentity, Object transitionHandle) {
            transitionCurrentnessCalls++;
            return delegate.checkPreparedTransitionCurrent(
                    playerIdentity, transitionHandle);
        }

        @Override
        public SkillDefinitionStoreSubmissionPort.SubmissionCommitResult
                commitPreparedSubmission(Object serverIdentity, Object storeHandle) {
            storeCommitCalls++;
            return delegate.commitPreparedSubmission(serverIdentity, storeHandle);
        }

        @Override
        public PlayerSkillAttachmentService.Result<PlayerSkillAttachmentService.MutationOutcome>
                publishPreparedTransition(
                        Object playerIdentity, Object transitionHandle) {
            attachmentPublicationCalls++;
            return delegate.publishPreparedTransition(playerIdentity, transitionHandle);
        }

        private StageCounts stageCounts() {
            return new StageCounts(
                    findDraftCalls,
                    precheckCalls,
                    authorityObservationCalls,
                    authorityCheckCalls,
                    policySnapshotCalls,
                    preparationCalls,
                    transitionPreparationCalls,
                    storePreparationCalls,
                    peakObservationCalls,
                    transitionCurrentnessCalls,
                    storeCommitCalls,
                    attachmentPublicationCalls,
                    rejectionMappingCalls);
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
                java.util.Set.of(new TriggerEventKind(EVENT_ID)),
                java.util.Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                java.util.Set.of(TriggerGranularity.PER_EVENT));
        private static final TriggerReferenceProjection PROJECTION =
                new TriggerReferenceProjection(
                        SourceSelection.NONE,
                        TargetSelection.NONE,
                        false,
                        List.of());

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
                java.util.Set.of(),
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
                        SourceSelection.NONE,
                        TargetSelection.NONE,
                        List.of(),
                        java.util.Set.of());

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
            return ValidationResult.of(WARNING);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
