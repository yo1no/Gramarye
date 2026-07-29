package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Creates one current empty Draft through the controlled player Attachment boundary. */
public final class SkillDraftCreationService {
    private final AttachmentAccess attachments;
    private final SkillIdSource skillIdSource;

    public SkillDraftCreationService(
            PlayerSkillAttachmentService attachmentService,
            SkillIdSource skillIdSource) {
        this(productionAccess(attachmentService), skillIdSource);
    }

    SkillDraftCreationService(
            AttachmentAccess attachments,
            SkillIdSource skillIdSource) {
        this.attachments = Objects.requireNonNull(attachments, "attachments");
        this.skillIdSource = Objects.requireNonNull(skillIdSource, "skillIdSource");
    }

    /** Returns a fresh stateless random-UUID adapter for composition-root ownership. */
    public static SkillIdSource randomUuidSkillIdSource() {
        return new RandomUuidSkillIdSource();
    }

    public CreationResult createDraft(ServerPlayer player) {
        return createDraftCore(Objects.requireNonNull(player, "player"));
    }

    CreationResult createDraftCore(Object playerIdentity) {
        Objects.requireNonNull(playerIdentity, "playerIdentity");
        var ownerResult = Objects.requireNonNull(
                attachments.ownerId(playerIdentity), "ownerId result");
        if (ownerResult instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable) {
            return new Unavailable(unavailable.reason());
        }

        var candidate = Objects.requireNonNull(
                skillIdSource.nextSkillId(), "skillIdSource returned null");
        var existingResult = Objects.requireNonNull(
                attachments.findDraft(playerIdentity, candidate), "findDraft result");
        if (existingResult instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable) {
            return new Unavailable(unavailable.reason());
        }
        var existing = ((PlayerSkillAttachmentService.Available<Optional<SkillDraft>>)
                existingResult).value();
        if (existing.isPresent()) {
            return new Rejected(CreationRejectionCode.SKILL_ID_COLLISION);
        }

        var draft = new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                candidate,
                Optional.empty(),
                List.of(),
                AppearanceDocument.Default.INSTANCE);
        var mutationResult = Objects.requireNonNull(
                attachments.putDraft(playerIdentity, draft), "putDraft result");
        if (mutationResult instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable) {
            return new Unavailable(unavailable.reason());
        }
        return switch (((PlayerSkillAttachmentService.Available<
                        PlayerSkillAttachmentService.MutationOutcome>) mutationResult).value()) {
            case PlayerSkillAttachmentService.Applied ignored -> new Created(candidate);
            case PlayerSkillAttachmentService.NoOp ignored ->
                    new Rejected(CreationRejectionCode.SKILL_ID_COLLISION);
            case PlayerSkillAttachmentService.MutationRejected rejected -> switch (rejected.code()) {
                case DRAFT_LIMIT_REACHED ->
                        new Rejected(CreationRejectionCode.DRAFT_LIMIT_REACHED);
                case ATTACHMENT_CAPACITY_REJECTED ->
                        new Rejected(CreationRejectionCode.ATTACHMENT_CAPACITY_REJECTED);
                case DRAFT_PERSISTENCE_REJECTED ->
                        new Rejected(CreationRejectionCode.DRAFT_PERSISTENCE_REJECTED);
                case ATTACHMENT_INVARIANT_REJECTED,
                        WRONG_SERVER,
                        WRONG_PLAYER,
                        STATE_CHANGED -> throw new IllegalStateException(
                                "Unexpected Draft creation rejection: " + rejected.code());
            };
        };
    }

    private static AttachmentAccess productionAccess(
            PlayerSkillAttachmentService attachmentService) {
        Objects.requireNonNull(attachmentService, "attachmentService");
        return new AttachmentAccess() {
            @Override
            public PlayerSkillAttachmentService.Result<SkillOwnerId> ownerId(Object player) {
                return attachmentService.ownerId((ServerPlayer) player);
            }

            @Override
            public PlayerSkillAttachmentService.Result<Optional<SkillDraft>> findDraft(
                    Object player,
                    SkillId skillId) {
                return attachmentService.findDraft((ServerPlayer) player, skillId);
            }

            @Override
            public PlayerSkillAttachmentService.Result<
                            PlayerSkillAttachmentService.MutationOutcome>
                    putDraft(Object player, SkillDraft draft) {
                return attachmentService.putDraft((ServerPlayer) player, draft);
            }
        };
    }

    interface AttachmentAccess {
        PlayerSkillAttachmentService.Result<SkillOwnerId> ownerId(Object player);

        PlayerSkillAttachmentService.Result<Optional<SkillDraft>> findDraft(
                Object player,
                SkillId skillId);

        PlayerSkillAttachmentService.Result<PlayerSkillAttachmentService.MutationOutcome> putDraft(
                Object player,
                SkillDraft draft);
    }

    public sealed interface CreationResult permits Created, Rejected, Unavailable {
    }

    public record Created(SkillId skillId) implements CreationResult {
        public Created {
            Objects.requireNonNull(skillId, "skillId");
        }
    }

    public record Rejected(CreationRejectionCode code) implements CreationResult {
        public Rejected {
            Objects.requireNonNull(code, "code");
        }
    }

    public record Unavailable(PlayerSkillAttachmentService.UnavailableReason reason)
            implements CreationResult {
        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }

    public enum CreationRejectionCode {
        DRAFT_LIMIT_REACHED,
        ATTACHMENT_CAPACITY_REJECTED,
        DRAFT_PERSISTENCE_REJECTED,
        SKILL_ID_COLLISION
    }
}
