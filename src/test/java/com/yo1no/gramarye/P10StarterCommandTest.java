package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionCompositionOutcome;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class P10StarterCommandTest {
    private static final SkillId ID = new SkillId(new UUID(10L, 1L));
    private static final SkillReference TARGET = new SkillReference(ID, new SkillRevision(1));

    @Test
    void cleanDraftBaseAdmitsOnlyTheClosedLatestAndImmediatePredecessorSet() {
        assertTrue(P9StarterCommand.cleanBase(draft(Optional.empty(), 0), new SkillRevision(0)));
        assertTrue(P9StarterCommand.cleanBase(draft(Optional.of(new SkillRevision(0)), 0), new SkillRevision(0)));
        assertFalse(P9StarterCommand.cleanBase(draft(Optional.of(new SkillRevision(1)), 0), new SkillRevision(0)));
        for (int latest : new int[] {1, 2, 50, Integer.MAX_VALUE}) {
            assertTrue(P9StarterCommand.cleanBase(draft(Optional.of(new SkillRevision(latest)), 0),
                    new SkillRevision(latest)));
            assertTrue(P9StarterCommand.cleanBase(draft(Optional.of(new SkillRevision(latest - 1)), 0),
                    new SkillRevision(latest)));
            assertFalse(P9StarterCommand.cleanBase(draft(Optional.empty(), 0), new SkillRevision(latest)));
            if (latest > 1) {
                assertFalse(P9StarterCommand.cleanBase(draft(Optional.of(new SkillRevision(latest - 2)), 0),
                        new SkillRevision(latest)));
            }
            if (latest < Integer.MAX_VALUE) {
                assertFalse(P9StarterCommand.cleanBase(draft(Optional.of(new SkillRevision(latest + 1)), 0),
                        new SkillRevision(latest)));
            }
            assertFalse(P9StarterCommand.cleanBase(draft(Optional.of(new SkillRevision(latest)), 1),
                    new SkillRevision(latest)));
        }
    }

    @Test
    void everyFineResultHasExactlyItsFrozenCoarseObservationOrSuccess() {
        var expected = new EnumMap<P9StarterCommand.Fine, Optional<String>>(P9StarterCommand.Fine.class);
        add(expected, "PROVISION_REJECTED", "SOURCE_REJECTED");
        add(expected, "PROVISION_AMBIGUOUS_OR_UNAVAILABLE",
                "TEMPLATE_UNAVAILABLE", "STALE_CONTEXT", "SNAPSHOT_CHANGED", "ATTACHMENT_UNAVAILABLE",
                "STORE_UNAVAILABLE", "STARTER_IDENTITY_NOT_AUTHORIZED", "STARTER_IDENTITY_UNAVAILABLE_OR_COLLISION",
                "RECOVERY_PENDING", "RECOVERY_TARGET_INVALID", "RECOVERY_UNAVAILABLE",
                "EQUIP_STATE_UNAVAILABLE", "EQUIP_PUBLISH_UNAVAILABLE");
        add(expected, "PROVISION_DRAFT_ROUTE_REJECTED", "DRAFT_CONFLICT", "DRAFT_HAS_UNSUBMITTED_CHANGES",
                "DRAFT_LIMIT_REACHED", "REVISION_EXHAUSTED", "ROUTE_CAPACITY_REJECTED");
        add(expected, "PROVISION_DRAFT_RECOVERABLE", "DRAFT_RETAINED_QUOTA_REJECTED",
                "DRAFT_RETAINED_CAPACITY_REJECTED", "DRAFT_RETAINED_SUBMISSION_REJECTED");
        add(expected, "PROVISION_PARTIAL_PERSISTENT_SUCCESS", "STORE_COMMITTED_JOURNAL_PUBLICATION_INVARIANT",
                "COMMITTED_PENDING_RECOVERY", "COMMITTED_NOT_EQUIPPED");
        add(expected, "SLOT_OCCUPIED", "SLOT_OCCUPIED", "SLOT_UNAVAILABLE", "EQUIP_CONFLICT");
        expected.put(P9StarterCommand.Fine.ALREADY_CURRENT, Optional.empty());
        expected.put(P9StarterCommand.Fine.EQUIPPED_TARGET, Optional.empty());
        assertEquals(java.util.Set.of(P9StarterCommand.Fine.values()), expected.keySet());
        expected.forEach((fine, coarse) -> assertEquals(coarse, fine.coarse(), fine.name()));
    }

    @Test
    void typedPreCommitFailureNeverClaimsPersistentSuccessOrPendingRecovery() {
        for (var failure : SkillSubmissionCompositionOutcome.AfterPreparationFailure.values()) {
            var result = new SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation(
                    TARGET, SkillSubmissionCompositionOutcome.AfterPreparationPhase.PRE_COMMIT,
                    failure, ValidationResult.valid());
            assertRejectedSubmission(P9StarterCommand.Fine.DRAFT_RETAINED_SUBMISSION_REJECTED, result);
        }
        assertRejectedSubmission(P9StarterCommand.Fine.DRAFT_RETAINED_SUBMISSION_REJECTED,
                new SkillSubmissionCompositionOutcome.DraftUnavailable(ID));
        assertRejectedSubmission(P9StarterCommand.Fine.DRAFT_RETAINED_QUOTA_REJECTED,
                new SkillSubmissionCompositionOutcome.QuotaRejected(ID, 1, 1, ValidationResult.valid()));
        for (var scope : SkillSubmissionCompositionOutcome.PersistenceCapacityScope.values()) {
            assertRejectedSubmission(P9StarterCommand.Fine.DRAFT_RETAINED_CAPACITY_REJECTED,
                    new SkillSubmissionCompositionOutcome.PersistenceCapacityRejected(scope, ValidationResult.valid()));
        }
    }

    @Test
    void storeCommittedJournalInvariantAndPendingAttachmentRecoveryRemainDifferent() {
        var committed = new SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation(
                TARGET, SkillSubmissionCompositionOutcome.AfterPreparationPhase.POST_COMMIT_STORE_COMMITTED,
                SkillSubmissionCompositionOutcome.AfterPreparationFailure.STORE_JOURNAL_PUBLICATION_INVARIANT,
                ValidationResult.valid());
        assertRejectedSubmission(P9StarterCommand.Fine.STORE_COMMITTED_JOURNAL_PUBLICATION_INVARIANT, committed);
        for (var failure : SkillSubmissionCompositionOutcome.AttachmentPublicationFailureCode.values()) {
            var metadata = failure == SkillSubmissionCompositionOutcome.AttachmentPublicationFailureCode.RUNTIME_EXCEPTION
                    ? SkillSubmissionCompositionOutcome.AttachmentPublicationFailure.runtime(new IllegalStateException())
                    : SkillSubmissionCompositionOutcome.AttachmentPublicationFailure.of(failure);
            var pending = new SkillSubmissionCompositionOutcome.CommittedPendingAttachmentRecovery(
                    TARGET, metadata, ValidationResult.valid());
            assertRejectedSubmission(P9StarterCommand.Fine.COMMITTED_PENDING_RECOVERY, pending);
        }
        assertThrows(IllegalArgumentException.class, () -> P9StarterCommand.submissionFailure(
                new SkillSubmissionCompositionOutcome.Committed(TARGET, ValidationResult.valid())));
    }

    @Test
    void completeDraftAdapterRejectsFutureOuterSchemaBeforeAnyMaterialization() {
        assertTrue(P9StarterCommand.completeDocument(draft(Optional.empty(), 1)).isEmpty());
        var complete = P9StarterCommand.completeDocument(draft(Optional.of(new SkillRevision(7)), 0));
        assertTrue(complete.isPresent());
        assertEquals(ID, complete.orElseThrow().skillId());
        assertTrue(complete.orElseThrow().nodes().isEmpty());
    }

    @Test
    void finalUnavailableRereadNeverPublishesAndPreservesCommittedDistinction() {
        for (boolean committed : new boolean[] {false, true}) {
            for (var reason : PlayerSkillAttachmentService.UnavailableReason.values()) {
                var reads = new AtomicInteger();
                var writes = new AtomicInteger();
                var result = P9StarterCommand.finishEquip(Optional.empty(), committed, () -> {
                    reads.incrementAndGet();
                    return new PlayerSkillAttachmentService.Unavailable<>(reason);
                }, () -> {
                    writes.incrementAndGet();
                    throw new AssertionError("unavailable reread must not reach publication");
                });
                assertEquals(committed ? P9StarterCommand.Fine.COMMITTED_NOT_EQUIPPED
                        : P9StarterCommand.Fine.EQUIP_STATE_UNAVAILABLE, result);
                assertEquals(1, reads.get());
                assertEquals(0, writes.get());
            }
        }
    }

    @Test
    void finalSlotDriftNeverOverwritesTheObservedReference() {
        for (boolean committed : new boolean[] {false, true}) {
            var slot = new AtomicReference<>(Optional.of(TARGET));
            var reads = new AtomicInteger();
            var writes = new AtomicInteger();
            var result = P9StarterCommand.finishEquip(Optional.empty(), committed, () -> {
                reads.incrementAndGet();
                return new PlayerSkillAttachmentService.Available<>(slot.get());
            }, () -> {
                writes.incrementAndGet();
                slot.set(Optional.empty());
                return new PlayerSkillAttachmentService.Available<>(PlayerSkillAttachmentService.Applied.INSTANCE);
            });
            assertEquals(committed ? P9StarterCommand.Fine.COMMITTED_NOT_EQUIPPED
                    : P9StarterCommand.Fine.EQUIP_CONFLICT, result);
            assertEquals(Optional.of(TARGET), slot.get());
            assertEquals(1, reads.get());
            assertEquals(0, writes.get());
        }
    }

    @Test
    void finalMatchingRereadPublishesExactlyOnceAndAcceptsOnlyApplied() {
        var failures = new ArrayList<PlayerSkillAttachmentService.Result<PlayerSkillAttachmentService.MutationOutcome>>();
        failures.add(new PlayerSkillAttachmentService.Available<>(PlayerSkillAttachmentService.NoOp.INSTANCE));
        for (var reason : PlayerSkillAttachmentService.MutationRejectionCode.values()) {
            failures.add(new PlayerSkillAttachmentService.Available<>(
                    new PlayerSkillAttachmentService.MutationRejected(reason)));
        }
        for (var reason : PlayerSkillAttachmentService.UnavailableReason.values()) {
            failures.add(new PlayerSkillAttachmentService.Unavailable<>(reason));
        }
        for (boolean committed : new boolean[] {false, true}) {
            for (var failure : failures) {
                var order = new ArrayList<String>();
                var result = P9StarterCommand.finishEquip(Optional.empty(), committed, () -> {
                    order.add("reread");
                    return new PlayerSkillAttachmentService.Available<>(Optional.empty());
                }, () -> {
                    order.add("set");
                    return failure;
                });
                assertEquals(committed ? P9StarterCommand.Fine.COMMITTED_NOT_EQUIPPED
                        : P9StarterCommand.Fine.EQUIP_PUBLISH_UNAVAILABLE, result);
                assertEquals(List.of("reread", "set"), order);
            }
            var slot = new AtomicReference<Optional<SkillReference>>(Optional.empty());
            var order = new ArrayList<String>();
            assertEquals(P9StarterCommand.Fine.EQUIPPED_TARGET,
                    P9StarterCommand.finishEquip(Optional.empty(), committed, () -> {
                        order.add("reread");
                        return new PlayerSkillAttachmentService.Available<>(slot.get());
                    }, () -> {
                        order.add("set");
                        slot.set(Optional.of(TARGET));
                        return new PlayerSkillAttachmentService.Available<>(PlayerSkillAttachmentService.Applied.INSTANCE);
                    }));
            assertEquals(List.of("reread", "set"), order);
            assertEquals(Optional.of(TARGET), slot.get());
        }
    }

    @Test
    void finalReadAndSetFaultsPropagateTheSameThrowableWithoutRetry() {
        for (boolean committed : new boolean[] {false, true}) {
            for (Throwable fault : List.of(new IllegalStateException("read/set"), new AssertionError("read/set"))) {
                var reads = new AtomicInteger();
                var writes = new AtomicInteger();
                assertSame(fault, assertThrows(Throwable.class, () -> P9StarterCommand.finishEquip(
                        Optional.empty(), committed, () -> {
                            reads.incrementAndGet();
                            throw unchecked(fault);
                        }, () -> {
                            writes.incrementAndGet();
                            throw new AssertionError("read fault must not reach publication");
                        })));
                assertEquals(1, reads.get());
                assertEquals(0, writes.get());
                reads.set(0);
                assertSame(fault, assertThrows(Throwable.class, () -> P9StarterCommand.finishEquip(
                        Optional.empty(), committed, () -> {
                            reads.incrementAndGet();
                            return new PlayerSkillAttachmentService.Available<>(Optional.empty());
                        }, () -> {
                            writes.incrementAndGet();
                            throw unchecked(fault);
                        })));
                assertEquals(1, reads.get());
                assertEquals(1, writes.get());
            }
        }
    }

    @Test
    void committedSubmissionHandsOffItsExactReferenceOnceAndReturnsTheActualEquipOutcome() {
        for (var fine : List.of(P9StarterCommand.Fine.EQUIPPED_TARGET, P9StarterCommand.Fine.COMMITTED_NOT_EQUIPPED)) {
            var order = new ArrayList<String>();
            assertEquals(fine, P9StarterCommand.submitAndEquip(() -> {
                order.add("submit");
                return new SkillSubmissionCompositionOutcome.Committed(TARGET, ValidationResult.valid());
            }, reference -> {
                order.add("equip");
                assertSame(TARGET, reference);
                return fine;
            }));
            assertEquals(List.of("submit", "equip"), order);
        }
    }

    @Test
    void submissionAndCommittedHandoffFaultsPropagateWithoutRetryOrSecondHandoff() {
        for (Throwable fault : List.of(new IllegalStateException("submit/equip"), new AssertionError("submit/equip"))) {
            var submits = new AtomicInteger();
            var equips = new AtomicInteger();
            assertSame(fault, assertThrows(Throwable.class, () -> P9StarterCommand.submitAndEquip(() -> {
                submits.incrementAndGet();
                throw unchecked(fault);
            }, reference -> {
                equips.incrementAndGet();
                throw new AssertionError("submission fault must not reach equip");
            })));
            assertEquals(1, submits.get());
            assertEquals(0, equips.get());
            submits.set(0);
            assertSame(fault, assertThrows(Throwable.class, () -> P9StarterCommand.submitAndEquip(() -> {
                submits.incrementAndGet();
                return new SkillSubmissionCompositionOutcome.Committed(TARGET, ValidationResult.valid());
            }, reference -> {
                equips.incrementAndGet();
                assertSame(TARGET, reference);
                throw unchecked(fault);
            })));
            assertEquals(1, submits.get());
            assertEquals(1, equips.get());
        }
    }

    @Test
    void maximumAlreadyCurrentDoesNotObserveRecoveryOrAttemptAnyMutation() {
        var maximum = new SkillReference(ID, new SkillRevision(Integer.MAX_VALUE));
        var calls = new ArrayList<String>();
        assertEquals(P9StarterCommand.Fine.ALREADY_CURRENT,
                P9StarterCommand.finishRevisionRoute(true, Optional.of(maximum), Optional.of(maximum), () -> {
                    calls.add("guard");
                    throw new AssertionError("already-current must not observe recovery");
                }, reference -> {
                    calls.add("equip");
                    throw new AssertionError("already-current must not equip");
                }, () -> {
                    calls.add("draft/submit");
                    throw new AssertionError("already-current must not inspect or publish a Draft");
                }));
        assertTrue(calls.isEmpty());
    }

    @Test
    void maximumSameContentEmptyAndOlderSlotsReequipWithoutAnySuccessorWork() {
        var maximum = new SkillReference(ID, new SkillRevision(Integer.MAX_VALUE));
        for (var expected : List.of(Optional.<SkillReference>empty(), Optional.of(TARGET))) {
            var calls = new ArrayList<String>();
            var slot = new AtomicReference<>(expected);
            assertEquals(P9StarterCommand.Fine.EQUIPPED_TARGET,
                    P9StarterCommand.finishRevisionRoute(true, expected, Optional.of(maximum), () -> {
                        calls.add("guard");
                        return Optional.empty();
                    }, reference -> {
                        calls.add("equip");
                        assertSame(maximum, reference);
                        slot.set(Optional.of(reference));
                        return P9StarterCommand.Fine.EQUIPPED_TARGET;
                    }, () -> {
                        calls.add("draft/submit");
                        throw new AssertionError("same-content MAX must not need a successor");
                    }));
            assertEquals(List.of("guard", "equip"), calls);
            assertEquals(Optional.of(maximum), slot.get());
        }
    }

    @Test
    void maximumDifferentContentStopsBeforeDraftObservationOrSubmission() {
        var maximum = new SkillReference(ID, new SkillRevision(Integer.MAX_VALUE));
        for (var expected : List.of(Optional.<SkillReference>empty(), Optional.of(TARGET), Optional.of(maximum))) {
            var calls = new ArrayList<String>();
            assertEquals(P9StarterCommand.Fine.REVISION_EXHAUSTED,
                    P9StarterCommand.finishRevisionRoute(false, expected, Optional.of(maximum), () -> {
                        calls.add("guard");
                        return Optional.empty();
                    }, reference -> {
                        calls.add("equip");
                        throw new AssertionError("different-content MAX must not re-equip");
                    }, () -> {
                        calls.add("draft/submit");
                        throw new AssertionError("exhausted MAX must not inspect or publish a Draft");
                    }));
            assertEquals(List.of("guard"), calls);
        }
    }

    @Test
    void recoveryAndSnapshotRejectionPrecedeMaximumAndSameContentReequip() {
        var maximum = new SkillReference(ID, new SkillRevision(Integer.MAX_VALUE));
        for (var rejection : List.of(P9StarterCommand.Fine.RECOVERY_PENDING,
                P9StarterCommand.Fine.RECOVERY_TARGET_INVALID, P9StarterCommand.Fine.RECOVERY_UNAVAILABLE,
                P9StarterCommand.Fine.SNAPSHOT_CHANGED)) {
            for (boolean same : new boolean[] {false, true}) {
                var calls = new ArrayList<String>();
                assertEquals(rejection,
                        P9StarterCommand.finishRevisionRoute(same, Optional.empty(), Optional.of(maximum), () -> {
                            calls.add("guard");
                            return Optional.of(rejection);
                        }, reference -> {
                            calls.add("equip");
                            throw new AssertionError("guard rejection must prevent equip");
                        }, () -> {
                            calls.add("draft/submit");
                            throw new AssertionError("guard rejection must prevent Draft observation/submission");
                        }));
                assertEquals(List.of("guard"), calls);
            }
        }
    }

    @Test
    void availableRevisionSpaceHandsOffToTheActualSuccessorBodyExactlyOnce() {
        var almostMaximum = new SkillReference(ID, new SkillRevision(Integer.MAX_VALUE - 1));
        for (var latest : List.of(Optional.<SkillReference>empty(), Optional.of(TARGET), Optional.of(almostMaximum))) {
            var calls = new ArrayList<String>();
            assertEquals(P9StarterCommand.Fine.DRAFT_RETAINED_QUOTA_REJECTED,
                    P9StarterCommand.finishRevisionRoute(false, Optional.empty(), latest, () -> {
                        calls.add("guard");
                        return Optional.empty();
                    }, reference -> {
                        calls.add("equip");
                        throw new AssertionError("different content must use the successor path");
                    }, () -> {
                        calls.add("draft/submit");
                        return P9StarterCommand.Fine.DRAFT_RETAINED_QUOTA_REJECTED;
                    }));
            assertEquals(List.of("guard", "draft/submit"), calls);
        }
    }

    @Test
    void revisionRouteGuardEquipAndSuccessorFaultsPropagateWithoutRetry() {
        for (Throwable fault : List.of(new IllegalStateException("revision-route"), new AssertionError("revision-route"))) {
            for (var stage : List.of("guard", "equip", "draft/submit")) {
                var calls = new ArrayList<String>();
                assertSame(fault, assertThrows(Throwable.class,
                        () -> P9StarterCommand.finishRevisionRoute(stage.equals("equip"), Optional.empty(),
                                Optional.of(TARGET), () -> {
                                    calls.add("guard");
                                    if (stage.equals("guard")) throw unchecked(fault);
                                    return Optional.empty();
                                }, reference -> {
                                    calls.add("equip");
                                    assertSame(TARGET, reference);
                                    throw unchecked(fault);
                                }, () -> {
                                    calls.add("draft/submit");
                                    throw unchecked(fault);
                                })));
                assertEquals(stage.equals("guard") ? List.of("guard") : List.of("guard", stage), calls);
            }
        }
    }

    private static void assertRejectedSubmission(P9StarterCommand.Fine expected,
            SkillSubmissionCompositionOutcome outcome) {
        var calls = new ArrayList<String>();
        assertEquals(expected, P9StarterCommand.submitAndEquip(() -> {
            calls.add("submit");
            return outcome;
        }, reference -> {
            calls.add("equip");
            throw new AssertionError("typed submission failure must never equip");
        }));
        assertEquals(List.of("submit"), calls);
        assertEquals(expected, P9StarterCommand.submissionFailure(outcome));
    }

    private static RuntimeException unchecked(Throwable failure) {
        if (failure instanceof Error error) throw error;
        return (RuntimeException) failure;
    }

    private static SkillDraft draft(Optional<SkillRevision> base, int schema) {
        return new SkillDraft(schema, ID, base, List.of(), AppearanceDocument.defaultAppearance());
    }

    private static void add(EnumMap<P9StarterCommand.Fine, Optional<String>> expected,
            String coarse, String... fine) {
        Arrays.stream(fine).map(P9StarterCommand.Fine::valueOf)
                .forEach(value -> assertNull(expected.put(value, Optional.of(coarse))));
    }
}
