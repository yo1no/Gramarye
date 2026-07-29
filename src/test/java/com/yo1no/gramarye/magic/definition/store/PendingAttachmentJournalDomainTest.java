package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PendingAttachmentJournalDomainTest {
    @Test
    void admissionCanonicalizesOnlyAfterInputOrderValidation() {
        var high = PendingAttachmentJournalTestSupport.physicalEntry(
                2, 2, 0, 1, Optional.empty(), 0);
        var low = PendingAttachmentJournalTestSupport.physicalEntry(
                1, 1, 0, 1, Optional.empty(), 0);

        var admitted = assertInstanceOf(
                PendingAttachmentJournal.DomainAdmission.Admitted.class,
                PendingAttachmentJournal.admitPhysical(
                        new PendingAttachmentJournalPhysicalV0(0, List.of(high, low))));

        assertTrue(admitted.nonCanonicalOrder());
        assertEquals(List.of(low.owner(), high.owner()), admitted.journal().entries()
                .stream().map(PendingAttachmentJournalEntry::owner).toList());
    }

    @Test
    void physicalV0AdmissionRejectsEveryNonCurrentVersion() {
        for (var version : List.of(-1, 1, Integer.MAX_VALUE)) {
            var rejected = assertInstanceOf(
                    PendingAttachmentJournal.DomainAdmission.Rejected.class,
                    PendingAttachmentJournal.admitPhysical(
                            new PendingAttachmentJournalPhysicalV0(version, List.of())));
            assertEquals(
                    PendingAttachmentJournalFailure.Code.UNSUPPORTED_SCHEMA,
                    rejected.failure().code());
            assertEquals(
                    PendingAttachmentJournalFailure.Field.VERSION,
                    rejected.failure().field());
        }
        assertInstanceOf(
                PendingAttachmentJournal.DomainAdmission.Admitted.class,
                PendingAttachmentJournal.admitPhysical(
                        new PendingAttachmentJournalPhysicalV0(
                                PendingAttachmentJournalSchema.CURRENT_SCHEMA_VERSION,
                                List.of())));
    }

    @Test
    void continuousGenerationAndPointerChainIsAccepted() {
        var skill = PendingAttachmentJournalTestSupport.skill(9);
        var firstTarget = PendingAttachmentJournalTestSupport.reference(skill, 1);
        var first = new PendingAttachmentJournalEntryPhysicalV0(
                PendingAttachmentJournalTestSupport.owner(3), skill,
                5, 6, Optional.empty(), firstTarget);
        var second = new PendingAttachmentJournalEntryPhysicalV0(
                first.owner(), skill, 6, 7, Optional.of(firstTarget),
                PendingAttachmentJournalTestSupport.reference(skill, 2));

        var admitted = assertInstanceOf(
                PendingAttachmentJournal.DomainAdmission.Admitted.class,
                PendingAttachmentJournal.admitPhysical(
                        new PendingAttachmentJournalPhysicalV0(0, List.of(first, second))));

        assertEquals(2, admitted.journal().entryCount());
    }

    @Test
    void generationPointerRouteDuplicateAndChainFailuresAreTyped() {
        var base = PendingAttachmentJournalTestSupport.physicalEntry(
                1, 2, 0, 1, Optional.empty(), 0);
        assertCode(PendingAttachmentJournalFailure.Code.GENERATION_INVALID,
                copy(base, -1, 0, base.expectedPointer(), base.targetPointer()));
        assertCode(PendingAttachmentJournalFailure.Code.GENERATION_EXHAUSTED,
                copy(base, Integer.MAX_VALUE, Integer.MAX_VALUE,
                        base.expectedPointer(), base.targetPointer()));
        assertCode(PendingAttachmentJournalFailure.Code.POINTER_ROUTE_MISMATCH,
                copy(base, 0, 1, base.expectedPointer(),
                        PendingAttachmentJournalTestSupport.reference(
                                PendingAttachmentJournalTestSupport.skill(99), 0)));

        assertFailure(PendingAttachmentJournalFailure.Code.DUPLICATE_STABLE_KEY,
                List.of(base, base));

        var gap = new PendingAttachmentJournalEntryPhysicalV0(
                base.owner(), base.skillId(), 2, 3,
                Optional.of(base.targetPointer()),
                PendingAttachmentJournalTestSupport.reference(base.skillId(), 1));
        assertFailure(PendingAttachmentJournalFailure.Code.BROKEN_GENERATION_CHAIN,
                List.of(base, gap));

        var pointerGap = new PendingAttachmentJournalEntryPhysicalV0(
                base.owner(), base.skillId(), 1, 2,
                Optional.empty(),
                PendingAttachmentJournalTestSupport.reference(base.skillId(), 1));
        assertFailure(PendingAttachmentJournalFailure.Code.BROKEN_POINTER_CHAIN,
                List.of(base, pointerGap));
    }

    @Test
    void appendEnforcesCountAndPrefixClearKeepsCanonicalSuffix() {
        var skill = PendingAttachmentJournalTestSupport.skill(7);
        var firstTarget = PendingAttachmentJournalTestSupport.reference(skill, 1);
        var secondTarget = PendingAttachmentJournalTestSupport.reference(skill, 2);
        var first = new PendingAttachmentJournalEntryPhysicalV0(
                PendingAttachmentJournalTestSupport.owner(4), skill,
                10, 11, Optional.empty(), firstTarget);
        var second = new PendingAttachmentJournalEntryPhysicalV0(
                first.owner(), skill, 11, 12, Optional.of(firstTarget), secondTarget);
        var journal = PendingAttachmentJournalTestSupport.journal(first, second);

        assertInstanceOf(PendingAttachmentJournal.PrefixClear.TargetMismatch.class,
                journal.clearPrefix(first.owner(), skill, 11, secondTarget));
        var cleared = assertInstanceOf(PendingAttachmentJournal.PrefixClear.Cleared.class,
                journal.clearPrefix(first.owner(), skill, 11, firstTarget));

        assertEquals(1, cleared.entriesRemoved());
        assertEquals(1, cleared.journal().entryCount());
        assertEquals(secondTarget, cleared.journal().entries().getFirst().targetPointer());
    }

    @Test
    void appendAcceptsOnlyTheExistingRouteFinalButAllowsADifferentRoute() {
        var owner = PendingAttachmentJournalTestSupport.owner(4);
        var skill = PendingAttachmentJournalTestSupport.skill(7);
        var firstTarget = PendingAttachmentJournalTestSupport.reference(skill, 1);
        var secondTarget = PendingAttachmentJournalTestSupport.reference(skill, 2);
        var first = new PendingAttachmentJournalEntryPhysicalV0(
                owner, skill, 5, 6, Optional.empty(), firstTarget);
        var second = new PendingAttachmentJournalEntryPhysicalV0(
                owner, skill, 6, 7, Optional.of(firstTarget), secondTarget);
        var journal = PendingAttachmentJournalTestSupport.journal(first, second);

        var prepend = new PendingAttachmentJournalEntry(
                owner,
                skill,
                4,
                5,
                Optional.empty(),
                PendingAttachmentJournalTestSupport.reference(skill, 0));
        var middle = new PendingAttachmentJournalEntry(
                owner,
                skill,
                6,
                7,
                Optional.of(firstTarget),
                PendingAttachmentJournalTestSupport.reference(skill, 3));
        var base = new PendingAttachmentJournalEntry(
                owner,
                skill,
                5,
                6,
                Optional.empty(),
                PendingAttachmentJournalTestSupport.reference(skill, 4));
        for (var rejectedEntry : List.of(prepend, middle, base)) {
            var rejected = assertInstanceOf(
                    PendingAttachmentJournal.DomainMutation.Rejected.class,
                    journal.append(rejectedEntry));
            assertEquals(
                    PendingAttachmentJournalFailure.Code.BROKEN_GENERATION_CHAIN,
                    rejected.failure().code());
            assertEquals(2, journal.entryCount());
        }

        var pointerMismatch = new PendingAttachmentJournalEntry(
                owner,
                skill,
                7,
                8,
                Optional.of(firstTarget),
                PendingAttachmentJournalTestSupport.reference(skill, 5));
        assertEquals(
                PendingAttachmentJournalFailure.Code.BROKEN_POINTER_CHAIN,
                assertInstanceOf(
                        PendingAttachmentJournal.DomainMutation.Rejected.class,
                        journal.append(pointerMismatch)).failure().code());

        var appended = assertInstanceOf(
                PendingAttachmentJournal.DomainMutation.Updated.class,
                journal.append(new PendingAttachmentJournalEntry(
                        owner,
                        skill,
                        7,
                        8,
                        Optional.of(secondTarget),
                        PendingAttachmentJournalTestSupport.reference(skill, 5))));
        assertEquals(3, appended.journal().entryCount());

        var otherSkill = PendingAttachmentJournalTestSupport.skill(8);
        var differentRoute = assertInstanceOf(
                PendingAttachmentJournal.DomainMutation.Updated.class,
                journal.append(new PendingAttachmentJournalEntry(
                        owner,
                        otherSkill,
                        0,
                        1,
                        Optional.empty(),
                        PendingAttachmentJournalTestSupport.reference(otherSkill, 0))));
        assertEquals(3, differentRoute.journal().entryCount());
    }

    @Test
    void largestRawCountWithDistinctRoutesIsAdmittedAndEncodedWithinByteCeiling() {
        var entries = new ArrayList<PendingAttachmentJournalEntryPhysicalV0>(
                MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES);
        for (var index = 0; index < MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES; index++) {
            entries.add(PendingAttachmentJournalTestSupport.physicalEntry(
                    index + 1L, index + 1L, 0, 1, Optional.empty(), 0));
        }

        var journal = assertInstanceOf(
                PendingAttachmentJournal.DomainAdmission.Admitted.class,
                PendingAttachmentJournal.admitPhysical(
                        new PendingAttachmentJournalPhysicalV0(0, entries))).journal();
        var encoded = assertInstanceOf(
                PendingAttachmentJournalFraming.JournalEncodingResult.Encoded.class,
                PendingAttachmentJournalFraming.encode(journal)).journal();

        assertEquals(MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES,
                encoded.entryCount());
        assertTrue(encoded.byteCount()
                <= MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES);
    }

    private static PendingAttachmentJournalEntryPhysicalV0 copy(
            PendingAttachmentJournalEntryPhysicalV0 base,
            int expected,
            int target,
            Optional<com.yo1no.gramarye.magic.definition.document.SkillReference> expectedPointer,
            com.yo1no.gramarye.magic.definition.document.SkillReference targetPointer) {
        return new PendingAttachmentJournalEntryPhysicalV0(
                base.owner(), base.skillId(), expected, target,
                expectedPointer, targetPointer);
    }

    private static void assertCode(
            PendingAttachmentJournalFailure.Code code,
            PendingAttachmentJournalEntryPhysicalV0 entry) {
        assertFailure(code, List.of(entry));
    }

    private static void assertFailure(
            PendingAttachmentJournalFailure.Code code,
            List<PendingAttachmentJournalEntryPhysicalV0> entries) {
        var rejected = assertInstanceOf(
                PendingAttachmentJournal.DomainAdmission.Rejected.class,
                PendingAttachmentJournal.admitPhysical(
                        new PendingAttachmentJournalPhysicalV0(0, entries)));
        assertEquals(code, rejected.failure().code());
    }
}
