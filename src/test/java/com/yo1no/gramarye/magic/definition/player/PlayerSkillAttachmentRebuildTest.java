package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PlayerSkillAttachmentRebuildTest {
    @Test
    void freshEmptyReadyHasFreshOuterIdentityAndMatchingCanonicalCarrier() {
        var first = PlayerSkillAttachmentPersistenceBridge.freshEmptyReady();
        var second = PlayerSkillAttachmentPersistenceBridge.freshEmptyReady();

        assertNotSame(first, second);
        assertEquals(first.drafts(), second.drafts());
        assertEquals(first.latestStates(), second.latestStates());
        assertEquals(first.equipped(), second.equipped());
        assertEquals(first.editor(), second.editor());
        assertEquals(first.carrier().copyTag(), second.carrier().copyTag());
        assertEquals(first.carrier().encodedByteCount(), second.carrier().encodedByteCount());
    }

    @Test
    void universalRebuildRejectsNullsAndAllCountOverflowsBeforeAdmission() {
        assertRejected(PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                null, List.of(), List.of(), PlayerSkillEditorState.empty()));
        assertRejected(PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                Arrays.asList((PlayerDraftEntry) null),
                List.of(),
                List.of(),
                PlayerSkillEditorState.empty()));

        var draft = draft(new SkillId(new UUID(0, 1)), 0);
        var draftEntry = new PlayerDraftEntry(draft.skillId(), draft, encoded(draft));
        var latest = new PlayerLatestState(
                new SkillId(new UUID(0, 2)), Optional.empty(), 0);
        var equipped = new EquippedSkillReference(
                0,
                new SkillReference(
                        new SkillId(new UUID(0, 3)), new SkillRevision(0)));

        assertCountRejected(
                PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        Collections.nCopies(
                                MagicSafetyCeilings.MAX_PLAYER_DRAFTS + 1, draftEntry),
                        List.of(),
                        List.of(),
                        PlayerSkillEditorState.empty()),
                PlayerSkillAttachmentFailure.Stage.DRAFT_COUNT);
        assertCountRejected(
                PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        List.of(),
                        Collections.nCopies(
                                MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES + 1, latest),
                        List.of(),
                        PlayerSkillEditorState.empty()),
                PlayerSkillAttachmentFailure.Stage.LATEST_COUNT);
        assertCountRejected(
                PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        List.of(),
                        List.of(),
                        Collections.nCopies(
                                MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES + 1,
                                equipped),
                        PlayerSkillEditorState.empty()),
                PlayerSkillAttachmentFailure.Stage.EQUIPPED_COUNT);
    }

    @Test
    void exactCountBoundariesRemainAdmissible() {
        var drafts = new ArrayList<PlayerDraftEntry>();
        for (var index = 0; index < MagicSafetyCeilings.MAX_PLAYER_DRAFTS; index++) {
            var draft = draft(new SkillId(new UUID(0, index + 1L)), 0);
            drafts.add(new PlayerDraftEntry(draft.skillId(), draft, encoded(draft)));
        }
        var latest = new ArrayList<PlayerLatestState>();
        for (var index = 0; index < MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES; index++) {
            latest.add(new PlayerLatestState(
                    new SkillId(new UUID(1, index + 1L)), Optional.empty(), index));
        }
        var equipped = new ArrayList<EquippedSkillReference>();
        for (var slot = 0; slot < MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES; slot++) {
            equipped.add(new EquippedSkillReference(
                    slot,
                    new SkillReference(
                            new SkillId(new UUID(2, slot + 1L)), new SkillRevision(slot))));
        }

        assertInstanceOf(
                PlayerSkillAttachmentBuildResult.Built.class,
                PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        drafts, latest, equipped, PlayerSkillEditorState.empty()));
        assertTrue(PlayerSkillAttachmentPersistenceBridge.canAddDraftRoute(
                MagicSafetyCeilings.MAX_PLAYER_DRAFTS - 1));
        assertFalse(PlayerSkillAttachmentPersistenceBridge.canAddDraftRoute(
                MagicSafetyCeilings.MAX_PLAYER_DRAFTS));
        assertFalse(PlayerSkillAttachmentPersistenceBridge.canAddDraftRoute(-1));
    }

    @Test
    void universalRebuildRejectsDuplicateRoutesSlotsAndOutOfRangeSlot() {
        var draft = draft(new SkillId(new UUID(0, 10)), 0);
        var draftEntry = new PlayerDraftEntry(draft.skillId(), draft, encoded(draft));
        var latest = new PlayerLatestState(
                new SkillId(new UUID(0, 11)), Optional.empty(), 0);
        var reference = new SkillReference(
                new SkillId(new UUID(0, 12)), new SkillRevision(0));

        assertFailureCode(
                PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        List.of(draftEntry, draftEntry),
                        List.of(),
                        List.of(),
                        PlayerSkillEditorState.empty()),
                PlayerSkillAttachmentFailure.Code.DUPLICATE_DRAFT_ROUTE);
        assertFailureCode(
                PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        List.of(),
                        List.of(latest, latest),
                        List.of(),
                        PlayerSkillEditorState.empty()),
                PlayerSkillAttachmentFailure.Code.DUPLICATE_LATEST_ROUTE);
        assertFailureCode(
                PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        List.of(),
                        List.of(),
                        List.of(
                                new EquippedSkillReference(3, reference),
                                new EquippedSkillReference(3, reference)),
                        PlayerSkillEditorState.empty()),
                PlayerSkillAttachmentFailure.Code.DUPLICATE_EQUIPPED_SLOT);
        assertFailureCode(
                PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        List.of(),
                        List.of(),
                        List.of(new EquippedSkillReference(
                                MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES,
                                reference)),
                        PlayerSkillEditorState.empty()),
                PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED);
    }

    @Test
    void canonicalDraftBytesOwnExactMatchAndMismatchedCarrierIsRejected() {
        var route = new SkillId(new UUID(0, 20));
        var current = draft(route, 0);
        var sameFirst = encoded(current);
        var sameSecond = encoded(current);

        assertNotSame(sameFirst, sameSecond);
        assertEquals(sameFirst, sameSecond);
        assertInstanceOf(
                PlayerSkillAttachmentBuildResult.Built.class,
                PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        List.of(new PlayerDraftEntry(route, current, sameSecond)),
                        List.of(),
                        List.of(),
                        PlayerSkillEditorState.empty()));

        var otherCarrier = encoded(draft(route, 1));
        assertFailureCode(
                PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        List.of(new PlayerDraftEntry(route, current, otherCarrier)),
                        List.of(),
                        List.of(),
                        PlayerSkillEditorState.empty()),
                PlayerSkillAttachmentFailure.Code.DRAFT_CARRIER_MISMATCH);
    }

    @Test
    void successfulRebuildCanonicalizesEveryRouteBearingCollection() {
        var low = new SkillId(new UUID(0, 1));
        var high = new SkillId(new UUID(0, 99));
        var lowDraft = draft(low, 0);
        var highDraft = draft(high, 0);
        var lowReference = new SkillReference(low, new SkillRevision(1));
        var highReference = new SkillReference(high, new SkillRevision(2));

        var built = assertInstanceOf(
                PlayerSkillAttachmentBuildResult.Built.class,
                PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        List.of(
                                new PlayerDraftEntry(high, highDraft, encoded(highDraft)),
                                new PlayerDraftEntry(low, lowDraft, encoded(lowDraft))),
                        List.of(
                                new PlayerLatestState(
                                        high, Optional.of(highReference), 2),
                                new PlayerLatestState(
                                        low, Optional.of(lowReference), 1)),
                        List.of(
                                new EquippedSkillReference(63, highReference),
                                new EquippedSkillReference(0, lowReference)),
                        PlayerSkillEditorState.empty()));

        assertEquals(List.of(low, high), built.ready().drafts().stream()
                .map(PlayerDraftEntry::skillId)
                .toList());
        assertEquals(List.of(low, high), built.ready().latestStates().stream()
                .map(PlayerLatestState::skillId)
                .toList());
        assertEquals(List.of(0, 63), built.ready().equipped().stream()
                .map(EquippedSkillReference::slot)
                .toList());
    }

    private static SkillDraft draft(SkillId route, int baseRevision) {
        return new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                route,
                Optional.of(new SkillRevision(baseRevision)),
                List.of(),
                AppearanceDocument.Default.INSTANCE);
    }

    private static SkillDraftPersistenceFacade.EncodedSkillDraft encoded(SkillDraft draft) {
        return assertInstanceOf(
                        SkillDraftPersistenceFacade.Encoded.class,
                        SkillDraftPersistenceFacade.encodeCurrent(draft))
                .draft();
    }

    private static PlayerSkillAttachmentBuildResult.Rejected assertRejected(
            PlayerSkillAttachmentBuildResult result) {
        return assertInstanceOf(PlayerSkillAttachmentBuildResult.Rejected.class, result);
    }

    private static void assertCountRejected(
            PlayerSkillAttachmentBuildResult result,
            PlayerSkillAttachmentFailure.Stage stage) {
        var rejected = assertRejected(result);
        assertEquals(PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED,
                rejected.failure().code());
        assertEquals(stage, rejected.failure().stage());
    }

    private static void assertFailureCode(
            PlayerSkillAttachmentBuildResult result,
            PlayerSkillAttachmentFailure.Code code) {
        assertEquals(code, assertRejected(result).failure().code());
    }
}
