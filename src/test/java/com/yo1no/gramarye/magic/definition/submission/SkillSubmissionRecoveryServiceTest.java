package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.neoforged.bus.api.BusBuilder;
import org.junit.jupiter.api.Test;

final class SkillSubmissionRecoveryServiceTest {
    private static final Object PLAYER = new Object();
    private static final Object SERVER = new Object();
    private static final SkillOwnerId OWNER = new SkillOwnerId(new UUID(7, 11));
    private static final SkillId SKILL_A = new SkillId(new UUID(0, 1));
    private static final SkillId SKILL_B = new SkillId(new UUID(0, 2));
    private static final SkillId SKILL_C = new SkillId(new UUID(0, 3));

    @Test
    void emptyProjectionIsNoPendingAndSkipsAttachmentObservation() {
        var dependencies = new FakeDependencies(available());

        var outcome = recover(dependencies);

        assertSame(SkillSubmissionRecoveryService.NoPending.INSTANCE, outcome);
        assertEquals(List.of("journal"), dependencies.calls);
    }

    @Test
    void registrationIsSingleShotOnTheProvidedNeoForgeBus() {
        var service = new SkillSubmissionRecoveryService(
                new FakeDependencies(available()));
        var eventBus = BusBuilder.builder().build();

        service.registerOn(eventBus);

        assertThrows(IllegalStateException.class, () -> service.registerOn(eventBus));
    }

    @Test
    void journalUnavailableAndInvalidTargetPrecedeAttachmentObservation() {
        for (var reason : SkillDefinitionStoreSubmissionPort
                .PendingRecoveryUnavailableReason.values()) {
            var dependencies = new FakeDependencies(
                    new SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection
                            .Unavailable(reason));

            var unavailable = assertInstanceOf(
                    SkillSubmissionRecoveryService.Unavailable.class,
                    recover(dependencies));

            assertEquals(reason.name(), unavailable.reason().name());
            assertEquals(0, unavailable.entriesClearedBeforeFailure());
            assertEquals(0, unavailable.stepsReplayedBeforeFailure());
            assertEquals(Optional.empty(), unavailable.exceptionClass());
            assertEquals(List.of("journal"), dependencies.calls);
        }

        for (var reason : SkillDefinitionStoreSubmissionPort
                .PendingRecoveryTargetFailure.values()) {
            var invalidTarget = reference(SKILL_A, 4);
            var dependencies = new FakeDependencies(
                    new SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection
                            .TargetInvalid(SKILL_A, invalidTarget, reason));

            var invalid = assertInstanceOf(
                    SkillSubmissionRecoveryService.TargetInvalid.class,
                    recover(dependencies));
            assertEquals(SKILL_A, invalid.skillId());
            assertEquals(invalidTarget, invalid.target());
            assertEquals(reason, invalid.reason());
            assertEquals(List.of("journal"), dependencies.calls);
        }
    }

    @Test
    void attachmentQuarantinesRemainUnavailableWithoutMutation() {
        var chain = chain(SKILL_A, step(Optional.empty(), 0, reference(SKILL_A, 1), 1));
        for (var reason : PlayerSkillAttachmentService.UnavailableReason.values()) {
            var dependencies = new FakeDependencies(available(chain));
            dependencies.latestObservation = new PlayerSkillAttachmentService.Unavailable<>(reason);

            var unavailable = assertInstanceOf(
                    SkillSubmissionRecoveryService.Unavailable.class,
                    recover(dependencies));

            assertEquals(
                    reason == PlayerSkillAttachmentService.UnavailableReason
                                    .PRESERVED_RAW_QUARANTINE
                            ? SkillSubmissionRecoveryService.RecoveryUnavailableReason
                                    .ATTACHMENT_PRESERVED_RAW_QUARANTINE
                            : SkillSubmissionRecoveryService.RecoveryUnavailableReason
                                    .ATTACHMENT_OVERSIZE_QUARANTINE,
                    unavailable.reason());
            assertEquals(List.of("journal", "latest"), dependencies.calls);
        }
    }

    @Test
    void latestBatchCannotExpandPastItsExistingAttachmentRouteCeiling() {
        var only = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);
        var dependencies = new FakeDependencies(available(chain(SKILL_A, only)));
        var oversized = new ArrayList<PlayerSkillAttachmentService.LatestStateView>();
        for (int index = 0;
                index <= MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES;
                index++) {
            oversized.add(latest(
                    new SkillId(new UUID(0, index + 100)), Optional.empty(), 0));
        }
        dependencies.setLatest(oversized);

        assertThrows(IllegalStateException.class, () -> recover(dependencies));
        assertEquals(List.of("journal", "latest"), dependencies.calls);
    }

    @Test
    void baseStateReplaysEveryStepAndNeverClearsReplayedJournalEntries() {
        var first = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);
        var second = step(
                Optional.of(first.targetPointer()), 1, reference(SKILL_A, 2), 2);
        var dependencies = new FakeDependencies(available(chain(SKILL_A, first, second)));
        dependencies.setLatest(List.of());

        var replayed = assertInstanceOf(
                SkillSubmissionRecoveryService.Replayed.class,
                recover(dependencies));

        assertEquals(2, replayed.stepsReplayed());
        assertEquals(
                List.of(
                        "journal",
                        "latest",
                        "prepare:1:1",
                        "current:1",
                        "publish:1",
                        "prepare:1:2",
                        "current:1",
                        "publish:1"),
                dependencies.calls);
        assertFalse(dependencies.calls.stream().anyMatch(call -> call.startsWith("clear")));
        assertEquals(new FakeTuple(Optional.of(second.targetPointer()), 2),
                dependencies.live.get(SKILL_A));
    }

    @Test
    void everyIntermediateIndexClearsItsConfirmedPrefixBeforeReplayingSuffix() {
        var first = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);
        var second = step(
                Optional.of(first.targetPointer()), 1, reference(SKILL_A, 2), 2);
        var third = step(
                Optional.of(second.targetPointer()), 2, reference(SKILL_A, 3), 3);
        var chain = chain(SKILL_A, first, second, third);

        var afterFirst = new FakeDependencies(available(chain));
        afterFirst.setLatest(latest(SKILL_A, Optional.of(first.targetPointer()), 1));
        afterFirst.clearEntriesRemoved = 1;
        var firstOutcome = assertInstanceOf(
                SkillSubmissionRecoveryService.ClearedAndReplayed.class,
                recover(afterFirst));
        assertEquals(new SkillSubmissionRecoveryService.ClearedAndReplayed(1, 2), firstOutcome);
        assertEquals("clear-prepare:1:1", afterFirst.calls.get(2));
        assertEquals("clear-commit", afterFirst.calls.get(3));
        assertEquals("prepare:1:2", afterFirst.calls.get(4));

        var afterSecond = new FakeDependencies(available(chain));
        afterSecond.setLatest(latest(SKILL_A, Optional.of(second.targetPointer()), 2));
        afterSecond.clearEntriesRemoved = 2;
        var secondOutcome = assertInstanceOf(
                SkillSubmissionRecoveryService.ClearedAndReplayed.class,
                recover(afterSecond));
        assertEquals(new SkillSubmissionRecoveryService.ClearedAndReplayed(2, 1), secondOutcome);
        assertEquals("clear-prepare:1:2", afterSecond.calls.get(2));
        assertEquals("clear-commit", afterSecond.calls.get(3));
        assertEquals("prepare:1:3", afterSecond.calls.get(4));
    }

    @Test
    void finalStateClearsWholeChainWithoutAttachmentTransition() {
        var first = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);
        var second = step(
                Optional.of(first.targetPointer()), 1, reference(SKILL_A, 2), 2);
        var dependencies = new FakeDependencies(available(chain(SKILL_A, first, second)));
        dependencies.setLatest(latest(SKILL_A, Optional.of(second.targetPointer()), 2));
        dependencies.clearEntriesRemoved = 2;

        var cleared = assertInstanceOf(
                SkillSubmissionRecoveryService.Cleared.class,
                recover(dependencies));

        assertEquals(2, cleared.entriesCleared());
        assertEquals(
                List.of("journal", "latest", "clear-prepare:1:2", "clear-commit"),
                dependencies.calls);
    }

    @Test
    void singleStepFinalStateAlsoClearsWithoutPublishing() {
        var only = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);
        var dependencies = new FakeDependencies(available(chain(SKILL_A, only)));
        dependencies.setLatest(latest(SKILL_A, Optional.of(only.targetPointer()), 1));

        var cleared = assertInstanceOf(
                SkillSubmissionRecoveryService.Cleared.class,
                recover(dependencies));

        assertEquals(1, cleared.entriesCleared());
        assertFalse(dependencies.calls.stream().anyMatch(call -> call.startsWith("prepare:")));
    }

    @Test
    void thirdPointerAndThirdGenerationNeverClearOrPublish() {
        var first = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);
        var chain = chain(SKILL_A, first);
        for (var latest : List.of(
                latest(SKILL_A, Optional.of(reference(SKILL_A, 9)), 1),
                latest(SKILL_A, Optional.of(first.targetPointer()), 9))) {
            var dependencies = new FakeDependencies(available(chain));
            dependencies.setLatest(latest);

            var conflict = assertInstanceOf(
                    SkillSubmissionRecoveryService.Conflict.class,
                    recover(dependencies));

            assertEquals(SkillSubmissionRecoveryService.RecoveryConflictCode.THIRD_STATE,
                    conflict.code());
            assertEquals(List.of("journal", "latest"), dependencies.calls);
        }
    }

    @Test
    void pointerReturningToAnOldReferenceStillRequiresTheExactGeneration() {
        var old = reference(SKILL_A, 1);
        var middle = reference(SKILL_A, 2);
        var first = step(Optional.empty(), 0, old, 1);
        var second = step(Optional.of(old), 1, middle, 2);
        var third = step(Optional.of(middle), 2, old, 3);
        var dependencies = new FakeDependencies(
                available(chain(SKILL_A, first, second, third)));
        dependencies.setLatest(latest(SKILL_A, Optional.of(old), 2));

        var conflict = assertInstanceOf(
                SkillSubmissionRecoveryService.Conflict.class,
                recover(dependencies));

        assertEquals(SkillSubmissionRecoveryService.RecoveryConflictCode.THIRD_STATE,
                conflict.code());
        assertEquals(List.of("journal", "latest"), dependencies.calls);
    }

    @Test
    void clearPreparationFailuresStopBeforeCommitAndReplay() {
        var step = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);
        for (var preparation : List.<SkillSubmissionRecoveryService.ClearPreparation>of(
                SkillSubmissionRecoveryService.ClearPreparation.NoOp.INSTANCE,
                SkillSubmissionRecoveryService.ClearPreparation.Rejected.INSTANCE)) {
            var dependencies = finalStateDependencies(step);
            dependencies.clearPreparation = preparation;

            var conflict = assertInstanceOf(
                    SkillSubmissionRecoveryService.Conflict.class,
                    recover(dependencies));

            assertEquals(
                    SkillSubmissionRecoveryService.RecoveryConflictCode
                            .CLEAR_PREPARATION_REJECTED,
                    conflict.code());
            assertEquals(List.of("journal", "latest", "clear-prepare:1:1"),
                    dependencies.calls);
        }

        var unavailableDependencies = finalStateDependencies(step);
        unavailableDependencies.clearPreparation =
                new SkillSubmissionRecoveryService.ClearPreparation.Unavailable(
                        SkillSubmissionRecoveryService.RecoveryUnavailableReason
                                .JOURNAL_UNAVAILABLE);
        var unavailable = assertInstanceOf(
                SkillSubmissionRecoveryService.Unavailable.class,
                recover(unavailableDependencies));
        assertEquals(
                SkillSubmissionRecoveryService.RecoveryUnavailableReason.JOURNAL_UNAVAILABLE,
                unavailable.reason());
        assertEquals(List.of("journal", "latest", "clear-prepare:1:1"),
                unavailableDependencies.calls);
    }

    @Test
    void clearCommitFailuresStopBeforeReplayAndPreservePriorProgressCounts() {
        var first = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);
        var second = step(
                Optional.of(first.targetPointer()), 1, reference(SKILL_A, 2), 2);
        for (var commit : List.<SkillSubmissionRecoveryService.ClearCommit>of(
                SkillSubmissionRecoveryService.ClearCommit.NoOp.INSTANCE,
                SkillSubmissionRecoveryService.ClearCommit.Rejected.INSTANCE)) {
            var dependencies = new FakeDependencies(available(chain(SKILL_A, first, second)));
            dependencies.setLatest(latest(SKILL_A, Optional.of(first.targetPointer()), 1));
            dependencies.clearCommit = commit;

            var conflict = assertInstanceOf(
                    SkillSubmissionRecoveryService.Conflict.class,
                    recover(dependencies));

            assertEquals(
                    SkillSubmissionRecoveryService.RecoveryConflictCode.CLEAR_COMMIT_REJECTED,
                    conflict.code());
            assertEquals(0, conflict.entriesClearedBeforeFailure());
            assertFalse(dependencies.calls.stream().anyMatch(call -> call.startsWith("prepare:")));
        }

        var unavailableDependencies = new FakeDependencies(
                available(chain(SKILL_A, first, second)));
        unavailableDependencies.setLatest(
                latest(SKILL_A, Optional.of(first.targetPointer()), 1));
        unavailableDependencies.clearCommit =
                new SkillSubmissionRecoveryService.ClearCommit.Unavailable(
                        SkillSubmissionRecoveryService.RecoveryUnavailableReason
                                .STORE_UNAVAILABLE);
        var unavailable = assertInstanceOf(
                SkillSubmissionRecoveryService.Unavailable.class,
                recover(unavailableDependencies));
        assertEquals(
                SkillSubmissionRecoveryService.RecoveryUnavailableReason.STORE_UNAVAILABLE,
                unavailable.reason());

        var wrongCount = new FakeDependencies(available(chain(SKILL_A, first, second)));
        wrongCount.setLatest(latest(SKILL_A, Optional.of(second.targetPointer()), 2));
        wrongCount.clearEntriesRemoved = 1;
        var countConflict = assertInstanceOf(
                SkillSubmissionRecoveryService.Conflict.class,
                recover(wrongCount));
        assertEquals(
                SkillSubmissionRecoveryService.RecoveryConflictCode.CLEAR_COMMIT_REJECTED,
                countConflict.code());
        assertEquals(1, countConflict.entriesClearedBeforeFailure());
    }

    @Test
    void replayPreparationRejectsNoOpAndMismatchedPreparedMetadata() {
        var step = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);

        var rejectedDependencies = baseDependencies(step);
        rejectedDependencies.transitionPreparation =
                SkillSubmissionRecoveryService.TransitionPreparation.Rejected.INSTANCE;
        assertEquals(
                SkillSubmissionRecoveryService.RecoveryConflictCode.REPLAY_PREPARATION_REJECTED,
                assertInstanceOf(
                                SkillSubmissionRecoveryService.Conflict.class,
                                recover(rejectedDependencies))
                        .code());

        var noOpDependencies = baseDependencies(step);
        noOpDependencies.forceNoOpTransition = true;
        assertEquals(
                SkillSubmissionRecoveryService.RecoveryConflictCode.REPLAY_UNEXPECTED_NO_OP,
                assertInstanceOf(
                                SkillSubmissionRecoveryService.Conflict.class,
                                recover(noOpDependencies))
                        .code());

        var mismatchDependencies = baseDependencies(step);
        mismatchDependencies.expectedGenerationOffset = 1;
        assertEquals(
                SkillSubmissionRecoveryService.RecoveryConflictCode.REPLAY_PREPARATION_REJECTED,
                assertInstanceOf(
                                SkillSubmissionRecoveryService.Conflict.class,
                                recover(mismatchDependencies))
                        .code());
        assertEquals(0, mismatchDependencies.invocationsStartingWith("current:"));
        assertEquals(0, mismatchDependencies.invocationsStartingWith("publish:"));
    }

    @Test
    void replayCurrentnessAndPublicationFailuresAreFailClosed() {
        var step = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);

        var changed = baseDependencies(step);
        changed.transitionCheck = SkillSubmissionRecoveryService.TransitionCheck.Changed.INSTANCE;
        var changedConflict = assertInstanceOf(
                SkillSubmissionRecoveryService.Conflict.class,
                recover(changed));
        assertEquals(
                SkillSubmissionRecoveryService.RecoveryConflictCode.REPLAY_CURRENTNESS_CHANGED,
                changedConflict.code());
        assertEquals(0, changed.invocationsStartingWith("publish:"));

        var rejected = baseDependencies(step);
        rejected.transitionPublication =
                SkillSubmissionRecoveryService.TransitionPublication.Rejected.INSTANCE;
        var rejectedConflict = assertInstanceOf(
                SkillSubmissionRecoveryService.Conflict.class,
                recover(rejected));
        assertEquals(
                SkillSubmissionRecoveryService.RecoveryConflictCode.REPLAY_PUBLICATION_REJECTED,
                rejectedConflict.code());
        assertEquals(new FakeTuple(Optional.empty(), 0), rejected.live.get(SKILL_A));
    }

    @Test
    void typedReplayUnavailabilityRetainsExactAttachmentReason() {
        var step = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);
        for (var stage : List.of("prepare", "current", "publish")) {
            var dependencies = baseDependencies(step);
            var reason = SkillSubmissionRecoveryService.RecoveryUnavailableReason
                    .ATTACHMENT_PRESERVED_RAW_QUARANTINE;
            switch (stage) {
                case "prepare" -> dependencies.transitionPreparation =
                        new SkillSubmissionRecoveryService.TransitionPreparation
                                .Unavailable(reason);
                case "current" -> dependencies.transitionCheck =
                        new SkillSubmissionRecoveryService.TransitionCheck.Unavailable(reason);
                case "publish" -> dependencies.transitionPublication =
                        new SkillSubmissionRecoveryService.TransitionPublication
                                .Unavailable(reason);
                default -> throw new AssertionError(stage);
            }

            var unavailable = assertInstanceOf(
                    SkillSubmissionRecoveryService.Unavailable.class,
                    recover(dependencies));

            assertEquals(reason, unavailable.reason());
            assertEquals(Optional.empty(), unavailable.exceptionClass());
            assertEquals(0, unavailable.stepsReplayedBeforeFailure());
        }
    }

    @Test
    void runtimeExceptionsAtControlledAttachmentBoundariesAreBoundedButErrorsEscape() {
        var step = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);
        for (var stage : List.of("prepare", "current", "publish")) {
            var dependencies = baseDependencies(step);
            dependencies.runtimeStage = stage;

            var unavailable = assertInstanceOf(
                    SkillSubmissionRecoveryService.Unavailable.class,
                    recover(dependencies));

            assertEquals(
                    SkillSubmissionRecoveryService.RecoveryUnavailableReason.RUNTIME_EXCEPTION,
                    unavailable.reason());
            assertEquals(Optional.of(TestRuntimeFailure.class.getName()),
                    unavailable.exceptionClass());
            assertFalse(unavailable.exceptionClass().orElseThrow().contains("fixture message"));
        }

        var errorDependencies = baseDependencies(step);
        errorDependencies.errorStage = "prepare";
        assertThrows(TestError.class, () -> recover(errorDependencies));
    }

    @Test
    void programmingRuntimeExceptionsOutsideAttachmentReplayRemainFailFast() {
        var step = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);
        for (var stage : List.of("journal", "latest", "clear-prepare", "clear-commit")) {
            var dependencies = stage.startsWith("clear")
                    ? finalStateDependencies(step)
                    : baseDependencies(step);
            dependencies.runtimeStage = stage;

            assertThrows(TestRuntimeFailure.class, () -> recover(dependencies), stage);
        }
    }

    @Test
    void successfulEarlierChainIsNotRolledBackWhenLaterChainConflictsAndThirdIsSkipped() {
        var first = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);
        var conflicting = step(Optional.empty(), 0, reference(SKILL_B, 1), 1);
        var neverReached = step(Optional.empty(), 0, reference(SKILL_C, 1), 1);
        var dependencies = new FakeDependencies(available(
                chain(SKILL_A, first),
                chain(SKILL_B, conflicting),
                chain(SKILL_C, neverReached)));
        dependencies.setLatest(List.of(
                latest(SKILL_B, Optional.of(reference(SKILL_B, 9)), 4),
                latest(SKILL_C, Optional.empty(), 0)));

        var conflict = assertInstanceOf(
                SkillSubmissionRecoveryService.Conflict.class,
                recover(dependencies));

        assertEquals(SKILL_B, conflict.skillId());
        assertEquals(1, conflict.stepsReplayedBeforeFailure());
        assertEquals(new FakeTuple(Optional.of(first.targetPointer()), 1),
                dependencies.live.get(SKILL_A));
        assertEquals(0, dependencies.invocationsContaining(":3"));
    }

    @Test
    void earlierClearAndReplayProgressIsReportedWhenLaterChainFails() {
        var firstA = step(Optional.empty(), 0, reference(SKILL_A, 1), 1);
        var secondA = step(
                Optional.of(firstA.targetPointer()), 1, reference(SKILL_A, 2), 2);
        var firstB = step(Optional.empty(), 0, reference(SKILL_B, 1), 1);
        var dependencies = new FakeDependencies(available(
                chain(SKILL_A, firstA, secondA), chain(SKILL_B, firstB)));
        dependencies.setLatest(List.of(
                latest(SKILL_A, Optional.of(firstA.targetPointer()), 1),
                latest(SKILL_B, Optional.of(reference(SKILL_B, 8)), 8)));

        var conflict = assertInstanceOf(
                SkillSubmissionRecoveryService.Conflict.class,
                recover(dependencies));

        assertEquals(SKILL_B, conflict.skillId());
        assertEquals(1, conflict.entriesClearedBeforeFailure());
        assertEquals(1, conflict.stepsReplayedBeforeFailure());
    }

    @Test
    void outcomeRecordsRejectUnboundedCountsAndRawExceptionText() {
        assertEquals(
                MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES,
                new SkillSubmissionRecoveryService.Replayed(
                                MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES)
                        .stepsReplayed());
        assertThrows(IllegalArgumentException.class,
                () -> new SkillSubmissionRecoveryService.Cleared(0));
        assertThrows(IllegalArgumentException.class,
                () -> new SkillSubmissionRecoveryService.Conflict(
                        SKILL_A,
                        SkillSubmissionRecoveryService.RecoveryConflictCode.THIRD_STATE,
                        -1,
                        0));
        assertThrows(IllegalArgumentException.class,
                () -> new SkillSubmissionRecoveryService.Replayed(
                        MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SkillSubmissionRecoveryService.ClearedAndReplayed(
                        MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SkillSubmissionRecoveryService.Unavailable(
                        SkillSubmissionRecoveryService.RecoveryUnavailableReason
                                .JOURNAL_UNAVAILABLE,
                        0,
                        0,
                        Optional.of("not allowed")));
        assertThrows(IllegalArgumentException.class,
                () -> new SkillSubmissionRecoveryService.Unavailable(
                        SkillSubmissionRecoveryService.RecoveryUnavailableReason
                                .RUNTIME_EXCEPTION,
                        0,
                        0,
                        Optional.empty()));
    }

    @Test
    void outcomeTopologyAndMachineVocabulariesStayExactAndBounded() {
        assertEquals(
                Set.of(
                        "NoPending",
                        "Cleared",
                        "Replayed",
                        "ClearedAndReplayed",
                        "Conflict",
                        "TargetInvalid",
                        "Unavailable"),
                Arrays.stream(SkillSubmissionRecoveryService.RecoveryOutcome.class
                                .getPermittedSubclasses())
                        .map(Class::getSimpleName)
                        .collect(Collectors.toSet()));
        assertEquals(
                Set.of(
                        "THIRD_STATE",
                        "CLEAR_PREPARATION_REJECTED",
                        "CLEAR_COMMIT_REJECTED",
                        "REPLAY_PREPARATION_REJECTED",
                        "REPLAY_CURRENTNESS_CHANGED",
                        "REPLAY_PUBLICATION_REJECTED",
                        "REPLAY_UNEXPECTED_NO_OP"),
                Arrays.stream(SkillSubmissionRecoveryService.RecoveryConflictCode.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()));
        assertEquals(
                Set.of(
                        "JOURNAL_NOT_BOOTSTRAPPED",
                        "JOURNAL_UNAVAILABLE",
                        "STORE_UNAVAILABLE",
                        "AUTHORITY_UNAVAILABLE",
                        "ATTACHMENT_PRESERVED_RAW_QUARANTINE",
                        "ATTACHMENT_OVERSIZE_QUARANTINE",
                        "RUNTIME_EXCEPTION"),
                Arrays.stream(SkillSubmissionRecoveryService.RecoveryUnavailableReason.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()));
        assertEquals(
                List.of(
                        "reason",
                        "entriesClearedBeforeFailure",
                        "stepsReplayedBeforeFailure",
                        "exceptionClass"),
                Arrays.stream(SkillSubmissionRecoveryService.Unavailable.class
                                .getRecordComponents())
                        .map(component -> component.getName())
                        .toList());
    }

    private static SkillSubmissionRecoveryService.RecoveryOutcome recover(
            FakeDependencies dependencies) {
        return new SkillSubmissionRecoveryService(dependencies)
                .recoverCore(PLAYER, SERVER, OWNER);
    }

    private static FakeDependencies baseDependencies(
            SkillDefinitionStoreSubmissionPort.PendingRecoveryStep step) {
        var dependencies = new FakeDependencies(available(chain(SKILL_A, step)));
        dependencies.setLatest(latest(
                SKILL_A, step.expectedPointer(), step.expectedGeneration()));
        return dependencies;
    }

    private static FakeDependencies finalStateDependencies(
            SkillDefinitionStoreSubmissionPort.PendingRecoveryStep step) {
        var dependencies = new FakeDependencies(available(chain(SKILL_A, step)));
        dependencies.setLatest(latest(
                SKILL_A, Optional.of(step.targetPointer()), step.targetGeneration()));
        return dependencies;
    }

    private static SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available available(
            SkillDefinitionStoreSubmissionPort.PendingSkillRecoveryChain... chains) {
        return new SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available(
                List.of(chains));
    }

    private static SkillDefinitionStoreSubmissionPort.PendingSkillRecoveryChain chain(
            SkillId skillId,
            SkillDefinitionStoreSubmissionPort.PendingRecoveryStep... steps) {
        return new SkillDefinitionStoreSubmissionPort.PendingSkillRecoveryChain(
                skillId, List.of(steps));
    }

    private static SkillDefinitionStoreSubmissionPort.PendingRecoveryStep step(
            Optional<SkillReference> expectedPointer,
            int expectedGeneration,
            SkillReference targetPointer,
            int targetGeneration) {
        return new SkillDefinitionStoreSubmissionPort.PendingRecoveryStep(
                expectedPointer, expectedGeneration, targetPointer, targetGeneration);
    }

    private static PlayerSkillAttachmentService.LatestStateView latest(
            SkillId skillId, Optional<SkillReference> pointer, int generation) {
        return new PlayerSkillAttachmentService.LatestStateView(
                skillId, pointer, generation);
    }

    private static SkillReference reference(SkillId skillId, int revision) {
        return new SkillReference(skillId, new SkillRevision(revision));
    }

    private static final class FakeDependencies
            implements SkillSubmissionRecoveryService.Dependencies {
        private final SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection projection;
        private final List<String> calls = new ArrayList<>();
        private final Map<SkillId, FakeTuple> live = new HashMap<>();
        private PlayerSkillAttachmentService.Result<
                        List<PlayerSkillAttachmentService.LatestStateView>>
                latestObservation = new PlayerSkillAttachmentService.Available<>(List.of());
        private SkillSubmissionRecoveryService.ClearPreparation clearPreparation =
                new SkillSubmissionRecoveryService.ClearPreparation.Prepared(
                        TestClearHandle.INSTANCE);
        private SkillSubmissionRecoveryService.ClearCommit clearCommit;
        private int clearEntriesRemoved = 1;
        private SkillSubmissionRecoveryService.TransitionPreparation transitionPreparation;
        private SkillSubmissionRecoveryService.TransitionCheck transitionCheck =
                SkillSubmissionRecoveryService.TransitionCheck.Current.INSTANCE;
        private SkillSubmissionRecoveryService.TransitionPublication transitionPublication =
                SkillSubmissionRecoveryService.TransitionPublication.Applied.INSTANCE;
        private boolean forceNoOpTransition;
        private int expectedGenerationOffset;
        private String runtimeStage;
        private String errorStage;

        private FakeDependencies(
                SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection projection) {
            this.projection = projection;
        }

        private void setLatest(PlayerSkillAttachmentService.LatestStateView latest) {
            setLatest(List.of(latest));
        }

        private void setLatest(List<PlayerSkillAttachmentService.LatestStateView> latest) {
            latestObservation = new PlayerSkillAttachmentService.Available<>(List.copyOf(latest));
            live.clear();
            for (var state : latest) {
                live.put(state.skillId(),
                        new FakeTuple(state.pointer(), state.mutationGeneration()));
            }
        }

        @Override
        public SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection
                observePendingRecovery(Object server, SkillOwnerId owner) {
            assertSame(SERVER, server);
            assertEquals(OWNER, owner);
            calls.add("journal");
            throwIfConfigured("journal");
            return projection;
        }

        @Override
        public PlayerSkillAttachmentService.Result<
                        List<PlayerSkillAttachmentService.LatestStateView>>
                observeLatestStates(Object player) {
            assertSame(PLAYER, player);
            calls.add("latest");
            throwIfConfigured("latest");
            return latestObservation;
        }

        @Override
        public SkillSubmissionRecoveryService.ClearPreparation prepareClear(
                Object server,
                SkillOwnerId owner,
                SkillId skillId,
                int confirmedTargetGeneration,
                SkillReference confirmedTargetPointer) {
            assertSame(SERVER, server);
            assertEquals(OWNER, owner);
            assertEquals(skillId, confirmedTargetPointer.skillId());
            calls.add("clear-prepare:" + skillId.value().getLeastSignificantBits()
                    + ':' + confirmedTargetGeneration);
            throwIfConfigured("clear-prepare");
            return clearPreparation;
        }

        @Override
        public SkillSubmissionRecoveryService.ClearCommit commitClear(
                Object server, SkillSubmissionRecoveryService.ClearHandle handle) {
            assertSame(SERVER, server);
            assertSame(TestClearHandle.INSTANCE, handle);
            calls.add("clear-commit");
            throwIfConfigured("clear-commit");
            return clearCommit != null
                    ? clearCommit
                    : new SkillSubmissionRecoveryService.ClearCommit.Cleared(
                            clearEntriesRemoved);
        }

        @Override
        public SkillSubmissionRecoveryService.TransitionPreparation prepareTransition(
                Object player, SkillId skillId, SkillReference targetPointer) {
            assertSame(PLAYER, player);
            calls.add("prepare:" + skillId.value().getLeastSignificantBits()
                    + ':' + targetPointer.revision().value());
            throwIfConfigured("prepare");
            if (transitionPreparation != null) {
                return transitionPreparation;
            }
            var current = live.getOrDefault(skillId, new FakeTuple(Optional.empty(), 0));
            var targetGeneration = forceNoOpTransition
                    ? current.generation()
                    : current.generation() + 1;
            return new SkillSubmissionRecoveryService.TransitionPreparation.Prepared(
                    new TestTransition(
                            skillId,
                            current.pointer(),
                            current.generation() + expectedGenerationOffset,
                            Optional.of(targetPointer),
                            targetGeneration,
                            forceNoOpTransition));
        }

        @Override
        public SkillSubmissionRecoveryService.TransitionCheck checkCurrent(
                Object player, SkillSubmissionRecoveryService.TransitionHandle transition) {
            assertSame(PLAYER, player);
            calls.add("current:" + transition.skillId().value().getLeastSignificantBits());
            throwIfConfigured("current");
            return transitionCheck;
        }

        @Override
        public SkillSubmissionRecoveryService.TransitionPublication publishTransition(
                Object player, SkillSubmissionRecoveryService.TransitionHandle transition) {
            assertSame(PLAYER, player);
            calls.add("publish:" + transition.skillId().value().getLeastSignificantBits());
            throwIfConfigured("publish");
            if (transitionPublication ==
                    SkillSubmissionRecoveryService.TransitionPublication.Applied.INSTANCE) {
                live.put(transition.skillId(),
                        new FakeTuple(
                                transition.targetPointer(), transition.targetGeneration()));
            }
            return transitionPublication;
        }

        private void throwIfConfigured(String stage) {
            if (stage.equals(errorStage)) {
                throw new TestError();
            }
            if (stage.equals(runtimeStage)) {
                throw new TestRuntimeFailure("fixture message must not escape");
            }
        }

        private int invocationsStartingWith(String prefix) {
            return (int) calls.stream().filter(call -> call.startsWith(prefix)).count();
        }

        private int invocationsContaining(String fragment) {
            return (int) calls.stream().filter(call -> call.contains(fragment)).count();
        }
    }

    private enum TestClearHandle implements SkillSubmissionRecoveryService.ClearHandle {
        INSTANCE
    }

    private record TestTransition(
            SkillId skillId,
            Optional<SkillReference> expectedPointer,
            int expectedGeneration,
            Optional<SkillReference> targetPointer,
            int targetGeneration,
            boolean isNoOp) implements SkillSubmissionRecoveryService.TransitionHandle {
        @Override
        public SkillOwnerId owner() {
            return OWNER;
        }

        @Override
        public boolean isBoundTo(Object server) {
            return server == SERVER;
        }
    }

    private record FakeTuple(Optional<SkillReference> pointer, int generation) {
    }

    private static final class TestRuntimeFailure extends RuntimeException {
        private TestRuntimeFailure(String message) {
            super(message);
        }
    }

    private static final class TestError extends Error {
    }
}
