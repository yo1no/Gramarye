package com.yo1no.gramarye;

import com.mojang.brigadier.CommandDispatcher;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionCompositionOutcome;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Package-local authenticated command owner for the bounded starter acquisition flow. */
final class P9StarterCommand {
    private static final int EQUIPPED_SLOT = 0;

    private final PlayerSkillAttachmentService attachments;
    private final SkillDefinitionSubmissionService submissions;
    private final SkillDefinitionStoreService store;

    P9StarterCommand(
            PlayerSkillAttachmentService attachments,
            SkillDefinitionSubmissionService submissions,
            SkillDefinitionStoreService store) {
        this.attachments = Objects.requireNonNull(attachments, "attachments");
        this.submissions = Objects.requireNonNull(submissions, "submissions");
        this.store = Objects.requireNonNull(store, "store");
    }

    void register(RegisterCommandsEvent event) {
        Objects.requireNonNull(event, "event");
        register(event.getDispatcher());
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        dispatcher.register(Commands.literal("gramarye")
                .then(Commands.literal("starter")
                        .requires(source -> source.hasPermission(0))
                        .executes(context -> provision(context.getSource()))));
    }

    private int provision(CommandSourceStack source) {
        Objects.requireNonNull(source, "source");
        var server = Objects.requireNonNull(source.getServer(), "command server");
        if (!server.isSameThread()
                || !(source.getEntity() instanceof ServerPlayer player)
                || source.source != player
                || player instanceof FakePlayer
                || player.getServer() != server
                || server.getPlayerList().getPlayer(player.getUUID()) != player) {
            return 0;
        }

        var owner = new SkillOwnerId(player.getUUID());
        var equippedResult = attachments.equippedAt(player, EQUIPPED_SLOT);
        if (equippedResult instanceof PlayerSkillAttachmentService.Unavailable<?>) {
            return 0;
        }
        var equipped = ((PlayerSkillAttachmentService.Available<Optional<SkillReference>>)
                equippedResult).value();
        if (equipped.isPresent()) {
            var referenceCheck = checkReference(server, owner, equipped.orElseThrow());
            if (referenceCheck == ReferenceCheck.MATCH) {
                return 1;
            }
            if (referenceCheck == ReferenceCheck.NOT_MATCH) {
                source.sendFailure(Component.translatable(
                        "commands.gramarye.starter.slot_occupied"));
            }
            return 0;
        }

        var recovery = store.submissionPort().observePendingRecovery(server, owner);
        if (!(recovery instanceof
                        SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available
                                availableRecovery)
                || !availableRecovery.chains().isEmpty()) {
            return 0;
        }

        var latestResult = attachments.observeLatestStates(player);
        if (latestResult instanceof PlayerSkillAttachmentService.Unavailable<?>) {
            return 0;
        }
        var latestStates = ((PlayerSkillAttachmentService.Available<
                        java.util.List<PlayerSkillAttachmentService.LatestStateView>>)
                latestResult).value();
        if (latestStates.size() > MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES) {
            return 0;
        }

        var matches = new ArrayList<SkillReference>(2);
        for (var state : latestStates) {
            if (state.pointer().isEmpty()) {
                continue;
            }
            var reference = state.pointer().orElseThrow();
            var check = checkReference(server, owner, reference);
            if (check == ReferenceCheck.UNAVAILABLE) {
                return 0;
            }
            if (check == ReferenceCheck.MATCH) {
                matches.add(reference);
                if (matches.size() > 1) {
                    return 0;
                }
            }
        }

        final SkillReference selected;
        if (matches.size() == 1) {
            selected = matches.getFirst();
        } else {
            selected = createOrSubmitCanonical(player, server, owner).orElse(null);
            if (selected == null) {
                return 0;
            }
        }

        var finalEquippedResult = attachments.equippedAt(player, EQUIPPED_SLOT);
        if (finalEquippedResult instanceof PlayerSkillAttachmentService.Unavailable<?>) {
            return 0;
        }
        var finalEquipped = ((PlayerSkillAttachmentService.Available<Optional<SkillReference>>)
                finalEquippedResult).value();
        if (finalEquipped.isPresent()) {
            return 0;
        }
        var mutation = attachments.setEquipped(
                player, EQUIPPED_SLOT, Optional.of(selected));
        return mutation instanceof PlayerSkillAttachmentService.Available<?> available
                        && available.value() == PlayerSkillAttachmentService.Applied.INSTANCE
                ? 1
                : 0;
    }

    private Optional<SkillReference> createOrSubmitCanonical(
            ServerPlayer player,
            MinecraftServer server,
            SkillOwnerId owner) {
        var skillId = P9StarterSkillIdentityV0.forPlayer(player.getUUID());

        var latestStateResult = attachments.findLatestState(player, skillId);
        if (latestStateResult instanceof PlayerSkillAttachmentService.Unavailable<?>
                || ((PlayerSkillAttachmentService.Available<
                                Optional<PlayerSkillAttachmentService.LatestStateView>>)
                        latestStateResult).value().isPresent()) {
            return Optional.empty();
        }

        var storeLatest = store.latestReference(server, skillId);
        if (storeLatest instanceof SkillSubsystemResult.Unavailable<?>
                || ((SkillSubsystemResult.Available<Optional<SkillReference>>) storeLatest)
                        .value().isPresent()) {
            return Optional.empty();
        }
        var storeOwner = store.ownerOf(server, skillId);
        if (storeOwner instanceof SkillSubsystemResult.Unavailable<?>
                || ((SkillSubsystemResult.Available<Optional<SkillOwnerId>>) storeOwner)
                        .value().isPresent()) {
            return Optional.empty();
        }

        var canonicalDraft = P9StarterSkillContent.canonicalDraft(skillId);
        var draftResult = attachments.findDraft(player, skillId);
        if (draftResult instanceof PlayerSkillAttachmentService.Unavailable<?>) {
            return Optional.empty();
        }
        var draft = ((PlayerSkillAttachmentService.Available<Optional<SkillDraft>>) draftResult)
                .value();
        if (draft.isPresent()) {
            if (!sameCanonicalDraft(draft.orElseThrow(), canonicalDraft)) {
                return Optional.empty();
            }
        } else {
            var countResult = attachments.draftCount(player);
            if (countResult instanceof PlayerSkillAttachmentService.Unavailable<?>
                    || ((PlayerSkillAttachmentService.Available<Integer>) countResult).value()
                            >= MagicSafetyCeilings.MAX_PLAYER_DRAFTS) {
                return Optional.empty();
            }
            var immediateReread = attachments.findDraft(player, skillId);
            if (immediateReread instanceof PlayerSkillAttachmentService.Unavailable<?>
                    || ((PlayerSkillAttachmentService.Available<Optional<SkillDraft>>)
                            immediateReread).value().isPresent()) {
                return Optional.empty();
            }
            var publication = attachments.putDraft(player, canonicalDraft);
            if (!(publication instanceof PlayerSkillAttachmentService.Available<?> available)
                    || available.value() != PlayerSkillAttachmentService.Applied.INSTANCE) {
                return Optional.empty();
            }
        }

        var submission = submissions.submit(player, skillId);
        if (!(submission instanceof SkillSubmissionCompositionOutcome.Committed committed)) {
            return Optional.empty();
        }
        var reference = committed.reference();
        return checkReference(server, owner, reference) == ReferenceCheck.MATCH
                ? Optional.of(reference)
                : Optional.empty();
    }

    private ReferenceCheck checkReference(
            MinecraftServer server,
            SkillOwnerId expectedOwner,
            SkillReference reference) {
        var documentResult = store.find(server, reference);
        if (documentResult instanceof SkillSubsystemResult.Unavailable<?>) {
            return ReferenceCheck.UNAVAILABLE;
        }
        var document = ((SkillSubsystemResult.Available<
                        Optional<com.yo1no.gramarye.magic.definition.document.SkillDocument>>)
                documentResult).value();
        if (document.isEmpty()) {
            return ReferenceCheck.NOT_MATCH;
        }

        var ownerResult = store.ownerOf(server, reference.skillId());
        if (ownerResult instanceof SkillSubsystemResult.Unavailable<?>) {
            return ReferenceCheck.UNAVAILABLE;
        }
        var owner = ((SkillSubsystemResult.Available<Optional<SkillOwnerId>>) ownerResult).value();
        if (!owner.equals(Optional.of(expectedOwner))) {
            return ReferenceCheck.NOT_MATCH;
        }
        return P9StarterSkillContent.hasCanonicalGameplayFingerprint(
                        reference, document.orElseThrow())
                ? ReferenceCheck.MATCH
                : ReferenceCheck.NOT_MATCH;
    }

    private static boolean sameCanonicalDraft(SkillDraft actual, SkillDraft canonical) {
        var actualEncoding = SkillDraftPersistenceFacade.encodeCurrent(actual);
        var canonicalEncoding = SkillDraftPersistenceFacade.encodeCurrent(canonical);
        return actualEncoding instanceof SkillDraftPersistenceFacade.Encoded actualEncoded
                && canonicalEncoding instanceof SkillDraftPersistenceFacade.Encoded
                        canonicalEncoded
                && actualEncoded.draft().equals(canonicalEncoded.draft());
    }

    private enum ReferenceCheck {
        MATCH,
        NOT_MATCH,
        UNAVAILABLE
    }
}
