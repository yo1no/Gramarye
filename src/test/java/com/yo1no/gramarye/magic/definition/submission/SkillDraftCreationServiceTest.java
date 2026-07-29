package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SkillDraftCreationServiceTest {
    private static final SkillId CANDIDATE = new SkillId(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174901"));
    private static final SkillOwnerId OWNER = new SkillOwnerId(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174902"));

    @Test
    void createsExactEmptyDraftWithOneMintOneLookupAndOnePublication() {
        var access = new FakeAttachmentAccess();
        var source = new CountingSource(CANDIDATE);
        var result = new SkillDraftCreationService(access, source).createDraftCore(new Object());

        var created = assertInstanceOf(SkillDraftCreationService.Created.class, result);
        assertSame(CANDIDATE, created.skillId());
        assertEquals(1, source.calls);
        assertEquals(1, access.ownerCalls);
        assertEquals(1, access.findCalls);
        assertEquals(1, access.putCalls);
        assertSame(CANDIDATE, access.lastLookup);
        assertEquals(SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION, access.lastDraft.draftSchemaVersion());
        assertSame(CANDIDATE, access.lastDraft.skillId());
        assertTrue(access.lastDraft.baseRevision().isEmpty());
        assertTrue(access.lastDraft.nodes().isEmpty());
        assertSame(AppearanceDocument.Default.INSTANCE, access.lastDraft.appearance());
    }

    @Test
    void collisionDoesNotRetryOrOverwrite() {
        var access = new FakeAttachmentAccess();
        access.findResult = new PlayerSkillAttachmentService.Available<>(Optional.of(
                SubmissionAuthorityTestFixtures.draft(CANDIDATE, Optional.empty())));
        var source = new CountingSource(CANDIDATE);

        var rejected = assertInstanceOf(
                SkillDraftCreationService.Rejected.class,
                new SkillDraftCreationService(access, source).createDraftCore(new Object()));

        assertEquals(SkillDraftCreationService.CreationRejectionCode.SKILL_ID_COLLISION,
                rejected.code());
        assertEquals(1, source.calls);
        assertEquals(1, access.findCalls);
        assertEquals(0, access.putCalls);
    }

    @Test
    void mapsControlledAttachmentRejectionsExactly() {
        for (var mapping : new Object[][] {
            {PlayerSkillAttachmentService.MutationRejectionCode.DRAFT_LIMIT_REACHED,
                    SkillDraftCreationService.CreationRejectionCode.DRAFT_LIMIT_REACHED},
            {PlayerSkillAttachmentService.MutationRejectionCode.ATTACHMENT_CAPACITY_REJECTED,
                    SkillDraftCreationService.CreationRejectionCode.ATTACHMENT_CAPACITY_REJECTED},
            {PlayerSkillAttachmentService.MutationRejectionCode.DRAFT_PERSISTENCE_REJECTED,
                    SkillDraftCreationService.CreationRejectionCode.DRAFT_PERSISTENCE_REJECTED}
        }) {
            var access = new FakeAttachmentAccess();
            access.putResult = new PlayerSkillAttachmentService.Available<>(
                    new PlayerSkillAttachmentService.MutationRejected(
                            (PlayerSkillAttachmentService.MutationRejectionCode) mapping[0]));
            var rejected = assertInstanceOf(
                    SkillDraftCreationService.Rejected.class,
                    new SkillDraftCreationService(access, () -> CANDIDATE)
                            .createDraftCore(new Object()));
            assertEquals(mapping[1], rejected.code());
            assertEquals(1, access.putCalls);
        }
    }

    @Test
    void quarantineShortCircuitsAtEachAttachmentObservation() {
        var ownerUnavailable = new FakeAttachmentAccess();
        ownerUnavailable.ownerResult = new PlayerSkillAttachmentService.Unavailable<>(
                PlayerSkillAttachmentService.UnavailableReason.PRESERVED_RAW_QUARANTINE);
        var source = new CountingSource(CANDIDATE);
        var beforeMint = assertInstanceOf(
                SkillDraftCreationService.Unavailable.class,
                new SkillDraftCreationService(ownerUnavailable, source)
                        .createDraftCore(new Object()));
        assertEquals(PlayerSkillAttachmentService.UnavailableReason.PRESERVED_RAW_QUARANTINE,
                beforeMint.reason());
        assertEquals(0, source.calls);
        assertEquals(0, ownerUnavailable.findCalls);

        var findUnavailable = new FakeAttachmentAccess();
        findUnavailable.findResult = new PlayerSkillAttachmentService.Unavailable<>(
                PlayerSkillAttachmentService.UnavailableReason.OVERSIZE_QUARANTINE);
        var afterMint = assertInstanceOf(
                SkillDraftCreationService.Unavailable.class,
                new SkillDraftCreationService(findUnavailable, source)
                        .createDraftCore(new Object()));
        assertEquals(PlayerSkillAttachmentService.UnavailableReason.OVERSIZE_QUARANTINE,
                afterMint.reason());
        assertEquals(1, source.calls);
        assertEquals(0, findUnavailable.putCalls);

        var putUnavailable = new FakeAttachmentAccess();
        putUnavailable.putResult = new PlayerSkillAttachmentService.Unavailable<>(
                PlayerSkillAttachmentService.UnavailableReason.PRESERVED_RAW_QUARANTINE);
        var afterBuild = assertInstanceOf(
                SkillDraftCreationService.Unavailable.class,
                new SkillDraftCreationService(putUnavailable, () -> CANDIDATE)
                        .createDraftCore(new Object()));
        assertEquals(PlayerSkillAttachmentService.UnavailableReason.PRESERVED_RAW_QUARANTINE,
                afterBuild.reason());
        assertEquals(1, putUnavailable.ownerCalls);
        assertEquals(1, putUnavailable.findCalls);
        assertEquals(1, putUnavailable.putCalls);
    }

    @Test
    void defensiveNoOpAndProgrammingFailuresCannotReportCreated() {
        var noOp = new FakeAttachmentAccess();
        noOp.putResult = new PlayerSkillAttachmentService.Available<>(
                PlayerSkillAttachmentService.NoOp.INSTANCE);
        var rejected = assertInstanceOf(
                SkillDraftCreationService.Rejected.class,
                new SkillDraftCreationService(noOp, () -> CANDIDATE)
                        .createDraftCore(new Object()));
        assertEquals(SkillDraftCreationService.CreationRejectionCode.SKILL_ID_COLLISION,
                rejected.code());

        var invariant = new FakeAttachmentAccess();
        invariant.putResult = new PlayerSkillAttachmentService.Available<>(
                new PlayerSkillAttachmentService.MutationRejected(
                        PlayerSkillAttachmentService.MutationRejectionCode
                                .ATTACHMENT_INVARIANT_REJECTED));
        assertThrows(IllegalStateException.class, () ->
                new SkillDraftCreationService(invariant, () -> CANDIDATE)
                        .createDraftCore(new Object()));
    }

    @Test
    void sourceAndResultsHaveStrictNullBoundariesAndRandomFactoryIsNarrow() {
        var access = new FakeAttachmentAccess();
        assertThrows(NullPointerException.class, () ->
                new SkillDraftCreationService(access, () -> null)
                        .createDraftCore(new Object()));
        assertThrows(NullPointerException.class, () ->
                new SkillDraftCreationService((SkillDraftCreationService.AttachmentAccess) null,
                        () -> CANDIDATE));
        assertThrows(NullPointerException.class, () ->
                new SkillDraftCreationService(access, null));
        assertInstanceOf(
                RandomUuidSkillIdSource.class,
                SkillDraftCreationService.randomUuidSkillIdSource());
        assertInstanceOf(
                SkillId.class,
                SkillDraftCreationService.randomUuidSkillIdSource().nextSkillId());
    }

    private static final class CountingSource implements SkillIdSource {
        private final SkillId result;
        private int calls;

        private CountingSource(SkillId result) {
            this.result = result;
        }

        @Override
        public SkillId nextSkillId() {
            calls++;
            return result;
        }
    }

    private static final class FakeAttachmentAccess
            implements SkillDraftCreationService.AttachmentAccess {
        private PlayerSkillAttachmentService.Result<SkillOwnerId> ownerResult =
                new PlayerSkillAttachmentService.Available<>(OWNER);
        private PlayerSkillAttachmentService.Result<Optional<SkillDraft>> findResult =
                new PlayerSkillAttachmentService.Available<>(Optional.empty());
        private PlayerSkillAttachmentService.Result<PlayerSkillAttachmentService.MutationOutcome>
                putResult = new PlayerSkillAttachmentService.Available<>(
                        PlayerSkillAttachmentService.Applied.INSTANCE);
        private int ownerCalls;
        private int findCalls;
        private int putCalls;
        private SkillId lastLookup;
        private SkillDraft lastDraft;

        @Override
        public PlayerSkillAttachmentService.Result<SkillOwnerId> ownerId(Object player) {
            ownerCalls++;
            return ownerResult;
        }

        @Override
        public PlayerSkillAttachmentService.Result<Optional<SkillDraft>> findDraft(
                Object player,
                SkillId skillId) {
            findCalls++;
            lastLookup = skillId;
            return findResult;
        }

        @Override
        public PlayerSkillAttachmentService.Result<PlayerSkillAttachmentService.MutationOutcome>
                putDraft(Object player,
                SkillDraft draft) {
            putCalls++;
            lastDraft = draft;
            return putResult;
        }
    }
}
