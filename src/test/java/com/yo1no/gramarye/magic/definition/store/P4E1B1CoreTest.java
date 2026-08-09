package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentServiceTestSupport;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class P4E1B1CoreTest {
    @Test
    void inventoryIsClosedAndReportsEachMissingProvider() {
        assertEquals(
                List.of("PLAYER_SKILL_ATTACHMENT", "PENDING_ATTACHMENT_JOURNAL"),
                java.util.Arrays.stream(P4E1RootSourceFamily.values())
                        .map(Enum::name)
                        .toList());

        var missingPlayer = assertInstanceOf(
                P4E1SourceInventory.Result.Missing.class,
                P4E1SourceInventory.capture(null, null));
        assertEquals(P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT,
                missingPlayer.family());

        var missingJournal = assertInstanceOf(
                P4E1SourceInventory.Result.Missing.class,
                P4E1SourceInventory.capture(
                        PlayerSkillAttachmentServiceTestSupport.createService(), null));
        assertEquals(P4E1RootSourceFamily.PENDING_ATTACHMENT_JOURNAL,
                missingJournal.family());

    }

    @Test
    void segmentedBackingPreservesOrderDuplicatesAndParallelMetadata() {
        var budget = P4E1TestBudgets.create();
        var buffer = new P4E1RawClaimBuffer();
        var duplicate = reference(7, 3);
        var other = reference(9, 4);
        var reservation = reserve(buffer, budget, 2, 3);

        reservation.appendLatest(duplicate);
        reservation.appendEquipped(6, other);
        reservation.appendLatest(duplicate);
        reservation.finish();

        assertEquals(3, buffer.size());
        assertSame(duplicate, buffer.referenceAt(0));
        assertSame(other, buffer.referenceAt(1));
        assertSame(duplicate, buffer.referenceAt(2));
        assertEquals(List.of(2, 2, 2), List.of(
                buffer.sourceTableIndexAt(0),
                buffer.sourceTableIndexAt(1),
                buffer.sourceTableIndexAt(2)));
        assertEquals(List.of(0, 1, 2), List.of(
                buffer.sourceLocalOrdinalAt(0),
                buffer.sourceLocalOrdinalAt(1),
                buffer.sourceLocalOrdinalAt(2)));
        assertEquals(P4E1RawClaimBuffer.ClaimKind.PLAYER_LATEST,
                buffer.claimKindAt(0));
        assertEquals(P4E1RawClaimBuffer.ClaimKind.PLAYER_EQUIPPED,
                buffer.claimKindAt(1));
        assertEquals(6, buffer.equippedSlotAt(1));
        assertEquals(-1, buffer.equippedSlotAt(2));
        buffer.discard();
        assertThrows(IllegalStateException.class, buffer::size);
    }

    @Test
    void exactRootMaximumSucceedsAndMaximumPlusOneReservesNothing() {
        var reference = reference(1, 0);
        var maximum = MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM;
        var successBuffer = new P4E1RawClaimBuffer();
        var success = reserve(successBuffer, P4E1TestBudgets.create(), 0, maximum);
        for (var index = 0; index < maximum; index++) {
            success.appendJournal(reference);
        }
        success.finish();
        assertEquals(maximum, successBuffer.size());
        assertSame(reference, successBuffer.referenceAt(maximum - 1));
        var afterMaximum = assertInstanceOf(
                P4E1RawClaimBuffer.ReservationResult.OverLimit.class,
                successBuffer.reserve(P4E1TestBudgets.create(), 1, maximum + 1));
        assertEquals((long) maximum + 1L, afterMaximum.exceeded().observedAtLeast());
        successBuffer.discard();

        var rejectedBuffer = new P4E1RawClaimBuffer();
        var rejected = assertInstanceOf(
                P4E1RawClaimBuffer.ReservationResult.OverLimit.class,
                rejectedBuffer.reserve(P4E1TestBudgets.create(), 0, maximum + 1));
        assertEquals(P4E1AuditCounter.RAW_ROOT_CLAIMS, rejected.exceeded().counter());
        assertEquals((long) maximum + 1L, rejected.exceeded().observedAtLeast());
        assertEquals(0, rejectedBuffer.size());
        rejectedBuffer.discard();
    }

    @Test
    void sequentialSourcesKeepGlobalOrderAndCrossSourceDuplicates() {
        var budget = P4E1TestBudgets.create();
        var buffer = new P4E1RawClaimBuffer();
        var duplicate = reference(31, 2);
        var player = reserve(buffer, budget, 0, 2);
        player.appendLatest(duplicate);
        player.appendEquipped(4, reference(32, 3));
        player.finish();
        var journal = reserve(buffer, budget, 1, 2);
        journal.appendJournal(duplicate);
        journal.appendJournal(duplicate);
        journal.finish();

        assertEquals(4, buffer.size());
        assertEquals(List.of(0, 0, 1, 1), List.of(
                buffer.sourceTableIndexAt(0),
                buffer.sourceTableIndexAt(1),
                buffer.sourceTableIndexAt(2),
                buffer.sourceTableIndexAt(3)));
        assertEquals(P4E1RawClaimBuffer.ClaimKind.PLAYER_LATEST,
                buffer.claimKindAt(0));
        assertEquals(P4E1RawClaimBuffer.ClaimKind.PLAYER_EQUIPPED,
                buffer.claimKindAt(1));
        assertEquals(P4E1RawClaimBuffer.ClaimKind.JOURNAL_TARGET,
                buffer.claimKindAt(2));
        assertSame(duplicate, buffer.referenceAt(0));
        assertSame(duplicate, buffer.referenceAt(2));
        assertSame(duplicate, buffer.referenceAt(3));
        buffer.discard();
    }

    @Test
    void failedWholeSourceReservationProducesNoReservationOrAppend() {
        var buffer = new P4E1RawClaimBuffer();
        var rejected = assertInstanceOf(
                P4E1RawClaimBuffer.ReservationResult.OverLimit.class,
                buffer.reserve(
                        P4E1TestBudgets.create(),
                        0,
                        MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM + 1));

        assertEquals(P4E1AuditCounter.RAW_ROOT_CLAIMS, rejected.exceeded().counter());
        assertEquals(0, buffer.size());
        buffer.discard();
    }

    @Test
    void reservationMustDrainExactlyAndCannotBeReused() {
        var buffer = new P4E1RawClaimBuffer();
        var reservation = reserve(buffer, P4E1TestBudgets.create(), 0, 2);
        reservation.appendLatest(reference(1, 0));
        assertThrows(IllegalStateException.class, reservation::finish);
        assertThrows(IllegalStateException.class,
                () -> reservation.appendLatest(reference(2, 0)));
        assertEquals(1, buffer.size());
        buffer.discard();
    }

    @Test
    void zeroReservationInvokesNoSinkAndFinishesAtCurrentCursor() {
        var buffer = new P4E1RawClaimBuffer();
        var reservation = reserve(buffer, P4E1TestBudgets.create(), 4, 0);
        reservation.finish();
        assertEquals(0, buffer.size());
        assertThrows(IllegalStateException.class,
                () -> reservation.appendJournal(reference(1, 0)));
        buffer.discard();
    }

    @Test
    void onlyOneReservationMayBeOutstandingAndOverAppendIsRejected() {
        var buffer = new P4E1RawClaimBuffer();
        var budget = P4E1TestBudgets.create();
        var reservation = reserve(buffer, budget, 0, 1);
        assertThrows(IllegalStateException.class, () -> buffer.reserve(budget, 1, 0));
        reservation.appendLatest(reference(51, 0));
        assertThrows(IllegalStateException.class,
                () -> reservation.appendJournal(reference(52, 0)));
        reservation.finish();
        assertThrows(IllegalStateException.class, reservation::finish);
        buffer.discard();
        assertThrows(IllegalStateException.class, buffer::discard);
    }

    private static P4E1RawClaimBuffer.Reservation reserve(
            P4E1RawClaimBuffer buffer,
            P4E1AuditBudget budget,
            int sourceIndex,
            int count) {
        return assertInstanceOf(
                P4E1RawClaimBuffer.ReservationResult.Reserved.class,
                buffer.reserve(budget, sourceIndex, count)).reservation();
    }

    private static SkillReference reference(long route, int revision) {
        return new SkillReference(
                new SkillId(new UUID(0L, route)), new SkillRevision(revision));
    }
}
