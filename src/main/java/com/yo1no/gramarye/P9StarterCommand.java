package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionCompositionOutcome;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** One synchronous authenticated starter operation; Store/submission remain the mutation owners. */
final class P9StarterCommand {
    private static final int EQUIPPED_SLOT = 0;
    private final PlayerSkillAttachmentService attachments;
    private final SkillDefinitionSubmissionService submissions;
    private final SkillDefinitionStoreService store;
    private final P10TemplateService templates;
    private final P10TemplateValidation validation;

    P9StarterCommand(PlayerSkillAttachmentService attachments,
            SkillDefinitionSubmissionService submissions, SkillDefinitionStoreService store,
            P10TemplateService templates, P10TemplateValidation validation) {
        this.attachments = Objects.requireNonNull(attachments, "attachments");
        this.submissions = Objects.requireNonNull(submissions, "submissions");
        this.store = Objects.requireNonNull(store, "store");
        this.templates = Objects.requireNonNull(templates, "templates");
        this.validation = Objects.requireNonNull(validation, "validation");
    }

    void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gramarye")
                .then(Commands.literal("starter").requires(source -> source.hasPermission(0))
                        .executes(context -> provision(context.getSource()))));
    }

    private int provision(CommandSourceStack source) {
        Objects.requireNonNull(source, "source");
        var server = Objects.requireNonNull(source.getServer(), "command server");
        if (!server.isSameThread()
                || !(source.getEntity() instanceof ServerPlayer player)
                || source.source != player || player instanceof FakePlayer
                || player.getServer() != server
                || server.getPlayerList().getPlayer(player.getUUID()) != player) {
            return 0;
        }
        var capture = templates.capture(server);
        if (capture.body().isEmpty()) return terminal(source, Fine.TEMPLATE_UNAVAILABLE, capture);
        if (!capture.contextCurrent()) return terminal(source, Fine.STALE_CONTEXT, capture);
        if (!validation.validate(server, capture.body().orElseThrow()).ready()) {
            return terminal(source, Fine.STALE_CONTEXT, capture);
        }
        if (!templates.isCurrent(server, capture)) return terminal(source, Fine.SNAPSHOT_CHANGED, capture);
        return terminal(source, provision(player, server, capture), capture);
    }

    private Fine provision(ServerPlayer player, MinecraftServer server, P10TemplateService.Capture capture) {
        var owner = new SkillOwnerId(player.getUUID());
        var skillId = P9StarterSkillIdentityV0.forPlayer(player.getUUID());
        var initial = attachments.equippedAt(player, EQUIPPED_SLOT);
        if (!(initial instanceof PlayerSkillAttachmentService.Available<Optional<SkillReference>> slot)) {
            return Fine.ATTACHMENT_UNAVAILABLE;
        }
        var expected = slot.value();
        if (expected.isPresent() && !expected.orElseThrow().skillId().equals(skillId)) {
            return Fine.SLOT_OCCUPIED;
        }
        var ownerRead = store.ownerOf(server, skillId);
        var latestRead = store.latestReference(server, skillId);
        if (!(ownerRead instanceof SkillSubsystemResult.Available<Optional<SkillOwnerId>> actualOwner)
                || !(latestRead instanceof SkillSubsystemResult.Available<Optional<SkillReference>> latestValue)) {
            return Fine.STORE_UNAVAILABLE;
        }
        if (actualOwner.value().isPresent() && !actualOwner.value().equals(Optional.of(owner))) {
            return expected.isPresent() ? Fine.SLOT_UNAVAILABLE : Fine.STARTER_IDENTITY_NOT_AUTHORIZED;
        }
        var latest = latestValue.value();
        if (actualOwner.value().isPresent() != latest.isPresent()) {
            return expected.isPresent() ? Fine.SLOT_UNAVAILABLE : Fine.STARTER_IDENTITY_UNAVAILABLE_OR_COLLISION;
        }
        SkillDocument latestDocument = null;
        if (latest.isPresent()) {
            var read = store.find(server, latest.orElseThrow());
            if (!(read instanceof SkillSubsystemResult.Available<Optional<SkillDocument>> available)) {
                return Fine.STORE_UNAVAILABLE;
            }
            if (available.value().isEmpty()
                    || !P9StarterSkillContent.hasSupportedStarterGameplay(latest.orElseThrow(), available.value().orElseThrow())) {
                return expected.equals(latest) ? Fine.SLOT_UNAVAILABLE
                        : Fine.STARTER_IDENTITY_UNAVAILABLE_OR_COLLISION;
            }
            latestDocument = available.value().orElseThrow();
        }
        if (expected.isPresent()) {
            if (latest.isEmpty()) return Fine.SLOT_UNAVAILABLE;
            var observed = expected.orElseThrow();
            if (!observed.equals(latest.orElseThrow())) {
                var read = store.find(server, observed);
                if (!(read instanceof SkillSubsystemResult.Available<Optional<SkillDocument>> available)) {
                    return Fine.STORE_UNAVAILABLE;
                }
                if (available.value().isEmpty()
                        || observed.revision().value() >= latest.orElseThrow().revision().value()
                        || !P9StarterSkillContent.hasSupportedStarterGameplay(observed, available.value().orElseThrow())) {
                    return Fine.SLOT_UNAVAILABLE;
                }
            }
        }
        var target = capture.body().orElseThrow().materialize(skillId, latest.map(SkillReference::revision));
        var targetDocument = completeDocument(target);
        if (targetDocument.isEmpty()) throw new IllegalStateException("Admitted template is incomplete");
        var targetReference = new SkillReference(skillId, new SkillRevision(0));
        boolean same = latestDocument != null && P9StarterSkillContent.sameNormalizedContent(
                latest.orElseThrow(), latestDocument, targetReference, targetDocument.orElseThrow());
        var observedLatestDocument = latestDocument;
        return finishRevisionRoute(same, expected, latest,
                () -> mutationGuard(server, owner, capture),
                reference -> equip(player, expected, reference, false),
                () -> submitSuccessor(player, expected, latest, observedLatestDocument,
                        target, targetReference, targetDocument.orElseThrow()));
    }

    static Fine finishRevisionRoute(boolean same, Optional<SkillReference> expected,
            Optional<SkillReference> latest, Supplier<Optional<Fine>> mutationGuard,
            Function<SkillReference, Fine> equipExisting, Supplier<Fine> submitSuccessor) {
        if (same && expected.equals(latest)) return Fine.ALREADY_CURRENT;
        var rejection = mutationGuard.get();
        if (rejection.isPresent()) return rejection.orElseThrow();
        if (same) return equipExisting.apply(latest.orElseThrow());
        if (latest.isPresent() && latest.orElseThrow().revision().value() == Integer.MAX_VALUE) {
            return Fine.REVISION_EXHAUSTED;
        }
        return submitSuccessor.get();
    }

    private Optional<Fine> mutationGuard(MinecraftServer server, SkillOwnerId owner,
            P10TemplateService.Capture capture) {
        // Observe only the pre-existing chain, once, before any command-owned mutation.
        var pending = store.submissionPort().observePendingRecovery(server, owner);
        if (pending instanceof SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.TargetInvalid) {
            return Optional.of(Fine.RECOVERY_TARGET_INVALID);
        }
        if (!(pending instanceof SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available available)) {
            return Optional.of(Fine.RECOVERY_UNAVAILABLE);
        }
        if (!available.chains().isEmpty()) return Optional.of(Fine.RECOVERY_PENDING);
        if (!templates.isCurrent(server, capture)) return Optional.of(Fine.SNAPSHOT_CHANGED);
        return Optional.empty();
    }

    private Fine submitSuccessor(ServerPlayer player, Optional<SkillReference> expected,
            Optional<SkillReference> latest, SkillDocument latestDocument, SkillDraft target,
            SkillReference targetReference, SkillDocument targetDocument) {
        var skillId = target.skillId();
        var draftRead = attachments.findDraft(player, skillId);
        if (!(draftRead instanceof PlayerSkillAttachmentService.Available<Optional<SkillDraft>> draftValue)) {
            return Fine.ATTACHMENT_UNAVAILABLE;
        }
        var existing = draftValue.value();
        boolean reuse = false;
        if (existing.isPresent()) {
            var draft = existing.orElseThrow();
            reuse = draft.skillId().equals(skillId)
                    && draft.baseRevision().equals(latest.map(SkillReference::revision))
                    && sameContent(draft, targetReference, targetDocument);
            if (!reuse) {
                boolean clean = latestDocument != null && draft.skillId().equals(skillId)
                        && cleanBase(draft, latest.orElseThrow().revision())
                        && sameContent(draft, latest.orElseThrow(), latestDocument);
                if (!clean) return latest.isEmpty() ? Fine.DRAFT_CONFLICT : Fine.DRAFT_HAS_UNSUBMITTED_CHANGES;
            }
        } else {
            var count = attachments.draftCount(player);
            if (!(count instanceof PlayerSkillAttachmentService.Available<Integer> availableCount)) {
                return Fine.ATTACHMENT_UNAVAILABLE;
            }
            if (availableCount.value() >= MagicSafetyCeilings.MAX_PLAYER_DRAFTS) return Fine.DRAFT_LIMIT_REACHED;
        }
        if (!reuse) {
            var publication = attachments.putDraft(player, target);
            if (!(publication instanceof PlayerSkillAttachmentService.Available<?> published)) {
                return Fine.ATTACHMENT_UNAVAILABLE;
            }
            if (published.value() instanceof PlayerSkillAttachmentService.MutationRejected rejected) {
                return switch (rejected.code()) {
                    case DRAFT_LIMIT_REACHED -> Fine.DRAFT_LIMIT_REACHED;
                    case DRAFT_PERSISTENCE_REJECTED, ATTACHMENT_CAPACITY_REJECTED -> Fine.ROUTE_CAPACITY_REJECTED;
                    default -> throw new IllegalStateException("Impossible starter Draft publication result");
                };
            }
            if (published.value() != PlayerSkillAttachmentService.Applied.INSTANCE) {
                throw new IllegalStateException("Starter Draft publication did not apply");
            }
        }
        // Formal submission obtains its own unique current authoritative policy snapshot.
        return submitAndEquip(() -> submissions.submit(player, skillId),
                reference -> equip(player, expected, reference, true));
    }

    static Fine submitAndEquip(Supplier<SkillSubmissionCompositionOutcome> submit,
            Function<SkillReference, Fine> equipCommitted) {
        var submission = submit.get();
        if (submission instanceof SkillSubmissionCompositionOutcome.Committed committed) {
            return equipCommitted.apply(committed.reference());
        }
        return submissionFailure(submission);
    }

    private Fine equip(ServerPlayer player, Optional<SkillReference> expected,
            SkillReference target, boolean committed) {
        return finishEquip(expected, committed,
                () -> attachments.equippedAt(player, EQUIPPED_SLOT),
                () -> attachments.setEquipped(player, EQUIPPED_SLOT, Optional.of(target)));
    }

    static Fine finishEquip(Optional<SkillReference> expected, boolean committed,
            Supplier<PlayerSkillAttachmentService.Result<Optional<SkillReference>>> read,
            Supplier<PlayerSkillAttachmentService.Result<PlayerSkillAttachmentService.MutationOutcome>> set) {
        var reread = read.get();
        if (!(reread instanceof PlayerSkillAttachmentService.Available<Optional<SkillReference>> current)) {
            return committed ? Fine.COMMITTED_NOT_EQUIPPED : Fine.EQUIP_STATE_UNAVAILABLE;
        }
        if (!current.value().equals(expected)) return committed ? Fine.COMMITTED_NOT_EQUIPPED : Fine.EQUIP_CONFLICT;
        var result = set.get();
        if (result instanceof PlayerSkillAttachmentService.Available<?> applied
                && applied.value() == PlayerSkillAttachmentService.Applied.INSTANCE) {
            return Fine.EQUIPPED_TARGET;
        }
        return committed ? Fine.COMMITTED_NOT_EQUIPPED : Fine.EQUIP_PUBLISH_UNAVAILABLE;
    }

    static Fine submissionFailure(SkillSubmissionCompositionOutcome result) {
        return switch (result) {
            case SkillSubmissionCompositionOutcome.QuotaRejected ignored -> Fine.DRAFT_RETAINED_QUOTA_REJECTED;
            case SkillSubmissionCompositionOutcome.CapacityRejected ignored -> Fine.DRAFT_RETAINED_CAPACITY_REJECTED;
            case SkillSubmissionCompositionOutcome.PersistenceCapacityRejected ignored -> Fine.DRAFT_RETAINED_CAPACITY_REJECTED;
            case SkillSubmissionCompositionOutcome.CommittedPendingAttachmentRecovery ignored -> Fine.COMMITTED_PENDING_RECOVERY;
            case SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation unavailable ->
                    unavailable.phase() == SkillSubmissionCompositionOutcome.AfterPreparationPhase.POST_COMMIT_STORE_COMMITTED
                            && unavailable.failure() == SkillSubmissionCompositionOutcome.AfterPreparationFailure.STORE_JOURNAL_PUBLICATION_INVARIANT
                            ? Fine.STORE_COMMITTED_JOURNAL_PUBLICATION_INVARIANT : Fine.DRAFT_RETAINED_SUBMISSION_REJECTED;
            case SkillSubmissionCompositionOutcome.Committed ignored -> throw new IllegalArgumentException("Committed is not failure");
            default -> Fine.DRAFT_RETAINED_SUBMISSION_REJECTED;
        };
    }

    static boolean cleanBase(SkillDraft draft, SkillRevision latest) {
        if (draft.draftSchemaVersion() != SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION) return false;
        return draft.baseRevision().equals(Optional.of(latest))
                || latest.value() == 0 && draft.baseRevision().isEmpty()
                || latest.value() > 0 && draft.baseRevision().equals(Optional.of(new SkillRevision(latest.value() - 1)));
    }

    static Optional<SkillDocument> completeDocument(SkillDraft draft) {
        if (draft.draftSchemaVersion() != SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION) return Optional.empty();
        var nodes = new ArrayList<NodeDocument>();
        for (var node : draft.nodes()) {
            if (!(node.trigger() instanceof DraftTriggerSlot.Present trigger)
                    || !(node.action() instanceof DraftActionSlot.Present action)) return Optional.empty();
            nodes.add(new NodeDocument(trigger.definition(), action.definition(), node.appearanceOverride()));
        }
        return Optional.of(new SkillDocument(SkillDocument.CURRENT_SCHEMA_VERSION, draft.skillId(),
                new SkillRevision(0), nodes, draft.appearance()));
    }

    private static boolean sameContent(SkillDraft draft, SkillReference reference, SkillDocument document) {
        var complete = completeDocument(draft);
        return complete.isPresent() && P9StarterSkillContent.sameNormalizedContent(
                new SkillReference(draft.skillId(), new SkillRevision(0)), complete.orElseThrow(), reference, document);
    }

    private static int terminal(CommandSourceStack source, Fine result, P10TemplateService.Capture capture) {
        var key = "commands.gramarye.starter." + result.name().toLowerCase(Locale.ROOT);
        var message = Component.translatable(key, capture.origin().name());
        if (result == Fine.ALREADY_CURRENT || result == Fine.EQUIPPED_TARGET) {
            source.sendSuccess(() -> message, false);
            return 1;
        }
        source.sendFailure(message);
        return 0;
    }

    enum Fine {
        SOURCE_REJECTED, TEMPLATE_UNAVAILABLE, STALE_CONTEXT, SNAPSHOT_CHANGED,
        ATTACHMENT_UNAVAILABLE, STORE_UNAVAILABLE, STARTER_IDENTITY_NOT_AUTHORIZED,
        STARTER_IDENTITY_UNAVAILABLE_OR_COLLISION, RECOVERY_PENDING, RECOVERY_TARGET_INVALID,
        RECOVERY_UNAVAILABLE, EQUIP_STATE_UNAVAILABLE, EQUIP_PUBLISH_UNAVAILABLE,
        DRAFT_CONFLICT, DRAFT_HAS_UNSUBMITTED_CHANGES, DRAFT_LIMIT_REACHED, REVISION_EXHAUSTED,
        ROUTE_CAPACITY_REJECTED, DRAFT_RETAINED_QUOTA_REJECTED, DRAFT_RETAINED_CAPACITY_REJECTED,
        DRAFT_RETAINED_SUBMISSION_REJECTED, STORE_COMMITTED_JOURNAL_PUBLICATION_INVARIANT,
        COMMITTED_PENDING_RECOVERY, COMMITTED_NOT_EQUIPPED, SLOT_OCCUPIED, SLOT_UNAVAILABLE,
        EQUIP_CONFLICT, ALREADY_CURRENT, EQUIPPED_TARGET;

        Optional<String> coarse() {
            return switch (this) {
                case SOURCE_REJECTED -> Optional.of("PROVISION_REJECTED");
                case TEMPLATE_UNAVAILABLE, STALE_CONTEXT, SNAPSHOT_CHANGED, ATTACHMENT_UNAVAILABLE,
                        STORE_UNAVAILABLE, STARTER_IDENTITY_NOT_AUTHORIZED,
                        STARTER_IDENTITY_UNAVAILABLE_OR_COLLISION, RECOVERY_PENDING, RECOVERY_TARGET_INVALID,
                        RECOVERY_UNAVAILABLE, EQUIP_STATE_UNAVAILABLE, EQUIP_PUBLISH_UNAVAILABLE ->
                        Optional.of("PROVISION_AMBIGUOUS_OR_UNAVAILABLE");
                case DRAFT_CONFLICT, DRAFT_HAS_UNSUBMITTED_CHANGES, DRAFT_LIMIT_REACHED,
                        REVISION_EXHAUSTED, ROUTE_CAPACITY_REJECTED -> Optional.of("PROVISION_DRAFT_ROUTE_REJECTED");
                case DRAFT_RETAINED_QUOTA_REJECTED, DRAFT_RETAINED_CAPACITY_REJECTED,
                        DRAFT_RETAINED_SUBMISSION_REJECTED -> Optional.of("PROVISION_DRAFT_RECOVERABLE");
                case STORE_COMMITTED_JOURNAL_PUBLICATION_INVARIANT, COMMITTED_PENDING_RECOVERY,
                        COMMITTED_NOT_EQUIPPED -> Optional.of("PROVISION_PARTIAL_PERSISTENT_SUCCESS");
                case SLOT_OCCUPIED, SLOT_UNAVAILABLE, EQUIP_CONFLICT -> Optional.of("SLOT_OCCUPIED");
                case ALREADY_CURRENT, EQUIPPED_TARGET -> Optional.empty();
            };
        }
    }
}
