package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import net.neoforged.fml.IExtensionPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** State, retention, and nominal-surface coverage for the one P4-E2 qualification cell. */
final class P4E2QualificationFacadeTest {
    private static final Path FACADE_SOURCE = projectRoot().resolve(
            "src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java");

    @TempDir
    Path temporary;

    @Test
    void topLevelHasNoPublicOperationOrSessionRetentionBacklink() {
        var type = P4E2QualificationFacade.class;
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertTrue(IExtensionPoint.class.isAssignableFrom(type));
        assertTrue(Arrays.stream(type.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                        || Modifier.isProtected(constructor.getModifiers())));
        assertTrue(Arrays.stream(type.getDeclaredMethods())
                .noneMatch(method -> Modifier.isPublic(method.getModifiers())));
        assertTrue(Arrays.stream(type.getDeclaredFields())
                .noneMatch(field -> field.getType()
                        == P4E2QualificationFacade.Session.class));
        assertTrue(Arrays.stream(type.getDeclaredFields())
                .noneMatch(field -> Modifier.isStatic(field.getModifiers())
                        && !Modifier.isFinal(field.getModifiers())));
        assertTrue(Arrays.stream(type.getDeclaredFields())
                .noneMatch(field -> java.util.Collection.class.isAssignableFrom(field.getType())
                        || java.util.Map.class.isAssignableFrom(field.getType())
                        || ThreadLocal.class.isAssignableFrom(field.getType())));
    }

    @Test
    void threeViewsAreClosedOwnerBoundNominalsWithOnlyFinalOperations() {
        assertClosedView(P4E2QualificationFacade.SubmissionView.class, 3);
        assertClosedView(P4E2QualificationFacade.StoreView.class, 6);
        assertClosedView(P4E2QualificationFacade.PlayerView.class, 2);

        var facade = new P4E2QualificationFacade();
        assertTrue(facade.submissionView() == facade.submissionView());
        assertTrue(facade.storeView() == facade.storeView());
        assertTrue(facade.playerView() == facade.playerView());
    }

    @Test
    void exactProductionSourceStateMachinePassesAgainstConstructorFreeServerStub()
            throws Exception {
        var sourceRoot = Files.createDirectories(temporary.resolve("source"));
        var outputRoot = Files.createDirectories(temporary.resolve("classes"));
        var serverStub = write(sourceRoot, "net/minecraft/server/MinecraftServer.java", """
                package net.minecraft.server;

                public class MinecraftServer {
                }
                """);
        var extensionStub = write(sourceRoot, "net/neoforged/fml/IExtensionPoint.java", """
                package net.neoforged.fml;

                public interface IExtensionPoint {
                }
                """);
        var harness = write(sourceRoot, "com/yo1no/gramarye/FacadeStateHarness.java", """
                package com.yo1no.gramarye;

                import java.util.concurrent.atomic.AtomicReference;
                import net.minecraft.server.MinecraftServer;

                public final class FacadeStateHarness {
                    private FacadeStateHarness() {
                    }

                    public static String run() throws InterruptedException {
                        unarmedIsAnExactNoOp();
                        noChangesCompletesWithoutPlayerCoordinate();
                        changedRequiresAndRecordsPlayerCoordinate();
                        directNegativeControlMatrixRecordsExactCoordinates();
                        abnormalFinallyPreservesThrowableAndRearms();
                        qualificationCleanupPreservesPrimaryAfterProductionClear();
                        identityThreadAndMandatoryCoordinatesFailFast();
                        stopClearsActiveAndCompletedCells();
                        return "PASS";
                    }

                    private static void unarmedIsAnExactNoOp() {
                        var facade = new P4E2QualificationFacade();
                        facade.submissionView().recordRecovery(
                                null, 1L, 2L,
                                P4E2QualificationFacade.RecoveryVariant.NO_PENDING,
                                P4E2QualificationFacade.RecoveryDetail.NONE, 0, 0);
                        facade.storeView().recordContinuation(null, 1L, 2L);
                        facade.storeView().recordInvalidationAttempt(null, 1L, 2L);
                        facade.storeView().recordInvalidationAccepted(null, 1L, 2L);
                        facade.playerView().recordE2SetDataAttempt(null, 1L, 2L);
                        facade.playerView().recordE2SetDataSuccess(null, 1L, 2L);
                        facade.storeView().recordReconciliation(
                                null, 1L, 2L,
                                P4E2QualificationFacade.ReconciliationVariant.NO_CHANGES,
                                P4E2QualificationFacade.ReconciliationDetail.NONE,
                                0, 0, 0, 0, 0, 0, 0, 0, false);
                        facade.submissionView().completeAfterContinuation(null, 1L, 2L);
                        facade.storeView().clearOnServerStopped();
                        check(!facade.submissionView().enabledFor(null, 1L, 2L),
                                "unarmed facade became enabled");
                    }

                    private static void noChangesCompletesWithoutPlayerCoordinate() {
                        var facade = new P4E2QualificationFacade();
                        var server = new MinecraftServer();
                        var session = facade.arm(
                                server, 10L, 20L, 30L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        facade.submissionView().recordRecovery(
                                server, 10L, 20L,
                                P4E2QualificationFacade.RecoveryVariant.NO_PENDING,
                                P4E2QualificationFacade.RecoveryDetail.NONE, 0, 0);
                        facade.storeView().recordContinuation(server, 10L, 20L);
                        facade.storeView().recordReconciliation(
                                server, 10L, 20L,
                                P4E2QualificationFacade.ReconciliationVariant.NO_CHANGES,
                                P4E2QualificationFacade.ReconciliationDetail.NONE,
                                0, 0, 0, 0, 0, 0, 0, 0, false);
                        facade.submissionView().completeAfterContinuation(server, 10L, 20L);
                        var snapshot = facade.consume(session);
                        check(snapshot.caseId() == 30L, "case id");
                        check(snapshot.phase() == P4E2QualificationFacade.Phase.READY_FIRST,
                                "phase");
                        check(snapshot.recoveryVariant()
                                == P4E2QualificationFacade.RecoveryVariant.NO_PENDING,
                                "recovery variant");
                        check(snapshot.reconciliationVariant()
                                == P4E2QualificationFacade.ReconciliationVariant.NO_CHANGES,
                                "reconciliation variant");
                        check(snapshot.continuationCalls() == 1, "continuation count");
                        check(snapshot.invalidationAttempts() == 0,
                                "no-changes invalidation attempts");
                        check(snapshot.invalidationAccepted() == 0,
                                "no-changes invalidation accepted");
                        check(snapshot.setDataAttempts() == 0,
                                "no-changes setData attempts");
                        check(snapshot.setDataSuccesses() == 0,
                                "no-changes setData successes");
                        check(!snapshot.acceptedGenerationPresent(),
                                "no-changes accepted generation");
                        rejectedConsume(facade, session);
                    }

                    private static void changedRequiresAndRecordsPlayerCoordinate() {
                        var missing = new P4E2QualificationFacade();
                        var server = new MinecraftServer();
                        var missingSession = missing.arm(
                                server, 1L, 2L, 3L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        missing.submissionView().recordRecovery(
                                server, 1L, 2L,
                                P4E2QualificationFacade.RecoveryVariant.NO_PENDING,
                                P4E2QualificationFacade.RecoveryDetail.NONE, 0, 0);
                        missing.storeView().recordContinuation(server, 1L, 2L);
                        missing.storeView().recordInvalidationAttempt(server, 1L, 2L);
                        missing.storeView().recordInvalidationAccepted(server, 1L, 2L);
                        missing.storeView().recordReconciliation(
                                server, 1L, 2L,
                                P4E2QualificationFacade.ReconciliationVariant.CHANGED,
                                P4E2QualificationFacade.ReconciliationDetail.NONE,
                                0, 0, 0, 0, 0, 0, 0, 0, true);
                        missing.submissionView().completeAfterContinuation(server, 1L, 2L);
                        rejectedConsume(missing, missingSession);
                        var replacementSession = missing.arm(
                                server, 1L, 2L, 4L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        missing.discard(replacementSession);

                        var facade = new P4E2QualificationFacade();
                        var session = facade.arm(
                                server, 4L, 5L, 6L,
                                P4E2QualificationFacade.Phase.READY_RESTART);
                        facade.submissionView().recordRecovery(
                                server, 4L, 5L,
                                P4E2QualificationFacade.RecoveryVariant.NO_PENDING,
                                P4E2QualificationFacade.RecoveryDetail.NONE, 0, 0);
                        facade.storeView().recordContinuation(server, 4L, 5L);
                        facade.storeView().recordInvalidationAttempt(server, 4L, 5L);
                        facade.storeView().recordInvalidationAccepted(server, 4L, 5L);
                        facade.playerView().recordE2SetDataAttempt(server, 4L, 5L);
                        facade.playerView().recordE2SetDataSuccess(server, 4L, 5L);
                        facade.storeView().recordReconciliation(
                                server, 4L, 5L,
                                P4E2QualificationFacade.ReconciliationVariant.CHANGED,
                                P4E2QualificationFacade.ReconciliationDetail.NONE,
                                0, 0, 1, 1, 0, 0, 0, 0, true);
                        facade.submissionView().completeAfterContinuation(server, 4L, 5L);
                        var snapshot = facade.consume(session);
                        check(snapshot.reconciliationVariant()
                                        == P4E2QualificationFacade.ReconciliationVariant.CHANGED,
                                "changed result variant");
                        check(snapshot.invalidationAttempts() == 1, "attempt count");
                        check(snapshot.invalidationAccepted() == 1, "accepted count");
                        check(snapshot.setDataAttempts() == 1, "setData attempt count");
                        check(snapshot.setDataSuccesses() == 1, "setData success count");
                        check(snapshot.acceptedGenerationPresent(), "accepted generation");
                    }

                    private static void directNegativeControlMatrixRecordsExactCoordinates() {
                        recoveryChangedOnlyRecordsAcceptedInvalidationWithoutSetData();
                        generationExhaustedRecordsAttemptWithoutAcceptanceOrSetData();
                        publisherStateDriftRecordsNoActualSetDataCoordinate();
                        acceptedInvalidationThenFailureRecordsNoActualSetDataCoordinate();
                    }

                    private static void
                            recoveryChangedOnlyRecordsAcceptedInvalidationWithoutSetData() {
                        var facade = new P4E2QualificationFacade();
                        var server = new MinecraftServer();
                        var session = facade.arm(
                                server, 61L, 62L, 63L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        facade.submissionView().recordRecovery(
                                server, 61L, 62L,
                                P4E2QualificationFacade.RecoveryVariant.CLEARED,
                                P4E2QualificationFacade.RecoveryDetail.NONE, 1, 0);
                        facade.storeView().recordContinuation(server, 61L, 62L);
                        facade.storeView().recordInvalidationAttempt(server, 61L, 62L);
                        facade.storeView().recordInvalidationAccepted(server, 61L, 62L);
                        facade.storeView().recordReconciliation(
                                server, 61L, 62L,
                                P4E2QualificationFacade.ReconciliationVariant.RECOVERY_CHANGED,
                                P4E2QualificationFacade.ReconciliationDetail.NONE,
                                1, 0, 0, 0, 0, 0, 0, 0, true);
                        facade.submissionView().completeAfterContinuation(server, 61L, 62L);

                        var snapshot = facade.consume(session);
                        check(snapshot.recoveryVariant()
                                        == P4E2QualificationFacade.RecoveryVariant.CLEARED,
                                "recovery-changed recovery variant");
                        check(snapshot.reconciliationVariant()
                                        == P4E2QualificationFacade.ReconciliationVariant
                                                .RECOVERY_CHANGED,
                                "recovery-changed result variant");
                        check(snapshot.entriesCleared() == 1,
                                "recovery-changed entries cleared");
                        check(snapshot.stepsReplayed() == 0,
                                "recovery-changed steps replayed");
                        check(snapshot.invalidationAttempts() == 1,
                                "recovery-changed invalidation attempts");
                        check(snapshot.invalidationAccepted() == 1,
                                "recovery-changed invalidation accepted");
                        check(snapshot.setDataAttempts() == 0,
                                "recovery-changed setData attempts");
                        check(snapshot.setDataSuccesses() == 0,
                                "recovery-changed setData successes");
                        check(snapshot.acceptedGenerationPresent(),
                                "recovery-changed accepted generation");
                    }

                    private static void
                            generationExhaustedRecordsAttemptWithoutAcceptanceOrSetData() {
                        var facade = new P4E2QualificationFacade();
                        var server = new MinecraftServer();
                        var session = facade.arm(
                                server, 71L, 72L, 73L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        facade.submissionView().recordRecovery(
                                server, 71L, 72L,
                                P4E2QualificationFacade.RecoveryVariant.NO_PENDING,
                                P4E2QualificationFacade.RecoveryDetail.NONE, 0, 0);
                        facade.storeView().recordContinuation(server, 71L, 72L);
                        facade.storeView().recordInvalidationAttempt(server, 71L, 72L);
                        facade.storeView().recordReconciliation(
                                server, 71L, 72L,
                                P4E2QualificationFacade.ReconciliationVariant
                                        .GENERATION_EXHAUSTED,
                                P4E2QualificationFacade.ReconciliationDetail.NONE,
                                0, 0, 0, 0, 0, 0, 0, 0, false);
                        facade.submissionView().completeAfterContinuation(server, 71L, 72L);

                        var snapshot = facade.consume(session);
                        check(snapshot.reconciliationVariant()
                                        == P4E2QualificationFacade.ReconciliationVariant
                                                .GENERATION_EXHAUSTED,
                                "generation-exhausted result variant");
                        check(snapshot.invalidationAttempts() == 1,
                                "generation-exhausted invalidation attempts");
                        check(snapshot.invalidationAccepted() == 0,
                                "generation-exhausted invalidation accepted");
                        check(snapshot.setDataAttempts() == 0,
                                "generation-exhausted setData attempts");
                        check(snapshot.setDataSuccesses() == 0,
                                "generation-exhausted setData successes");
                        check(!snapshot.acceptedGenerationPresent(),
                                "generation-exhausted accepted generation");
                    }

                    private static void publisherStateDriftRecordsNoActualSetDataCoordinate() {
                        var facade = new P4E2QualificationFacade();
                        var server = new MinecraftServer();
                        var session = facade.arm(
                                server, 81L, 82L, 83L,
                                P4E2QualificationFacade.Phase.READY_RESTART);
                        facade.submissionView().recordRecovery(
                                server, 81L, 82L,
                                P4E2QualificationFacade.RecoveryVariant.NO_PENDING,
                                P4E2QualificationFacade.RecoveryDetail.NONE, 0, 0);
                        facade.storeView().recordContinuation(server, 81L, 82L);
                        facade.storeView().recordInvalidationAttempt(server, 81L, 82L);
                        facade.storeView().recordInvalidationAccepted(server, 81L, 82L);
                        facade.storeView().recordReconciliation(
                                server, 81L, 82L,
                                P4E2QualificationFacade.ReconciliationVariant.FAILED,
                                P4E2QualificationFacade.ReconciliationDetail.FRESHNESS_LOST,
                                0, 0, 1, 0, 0, 0, 0, 0, true);
                        facade.submissionView().completeAfterContinuation(server, 81L, 82L);

                        var snapshot = facade.consume(session);
                        check(snapshot.reconciliationVariant()
                                        == P4E2QualificationFacade.ReconciliationVariant.FAILED,
                                "publisher-drift result variant");
                        check(snapshot.reconciliationDetail()
                                        == P4E2QualificationFacade.ReconciliationDetail
                                                .FRESHNESS_LOST,
                                "publisher-drift result detail");
                        check(snapshot.invalidationAttempts() == 1,
                                "publisher-drift invalidation attempts");
                        check(snapshot.invalidationAccepted() == 1,
                                "publisher-drift invalidation accepted");
                        check(snapshot.setDataAttempts() == 0,
                                "publisher-drift setData attempts");
                        check(snapshot.setDataSuccesses() == 0,
                                "publisher-drift setData successes");
                    }

                    private static void
                            acceptedInvalidationThenFailureRecordsNoActualSetDataCoordinate() {
                        var facade = new P4E2QualificationFacade();
                        var server = new MinecraftServer();
                        var session = facade.arm(
                                server, 91L, 92L, 93L,
                                P4E2QualificationFacade.Phase.READY_RESTART);
                        facade.submissionView().recordRecovery(
                                server, 91L, 92L,
                                P4E2QualificationFacade.RecoveryVariant.NO_PENDING,
                                P4E2QualificationFacade.RecoveryDetail.NONE, 0, 0);
                        facade.storeView().recordContinuation(server, 91L, 92L);
                        facade.storeView().recordInvalidationAttempt(server, 91L, 92L);
                        facade.storeView().recordInvalidationAccepted(server, 91L, 92L);
                        facade.storeView().recordReconciliation(
                                server, 91L, 92L,
                                P4E2QualificationFacade.ReconciliationVariant.FAILED,
                                P4E2QualificationFacade.ReconciliationDetail
                                        .INTERNAL_RUNTIME_FAILURE,
                                0, 0, 1, 0, 0, 0, 0, 0, true);
                        facade.submissionView().completeAfterContinuation(server, 91L, 92L);

                        var snapshot = facade.consume(session);
                        check(snapshot.reconciliationVariant()
                                        == P4E2QualificationFacade.ReconciliationVariant.FAILED,
                                "accepted-failure result variant");
                        check(snapshot.reconciliationDetail()
                                        == P4E2QualificationFacade.ReconciliationDetail
                                                .INTERNAL_RUNTIME_FAILURE,
                                "accepted-failure result detail");
                        check(snapshot.invalidationAttempts() == 1,
                                "accepted-failure invalidation attempts");
                        check(snapshot.invalidationAccepted() == 1,
                                "accepted-failure invalidation accepted");
                        check(snapshot.setDataAttempts() == 0,
                                "accepted-failure setData attempts");
                        check(snapshot.setDataSuccesses() == 0,
                                "accepted-failure setData successes");
                    }

                    private static void abnormalFinallyPreservesThrowableAndRearms() {
                        var server = new MinecraftServer();

                        var recoverFacade = new P4E2QualificationFacade();
                        var recoverSession = recoverFacade.arm(
                                server, 31L, 32L, 33L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        var recoverError = new TestError();
                        try {
                            try {
                                throw recoverError;
                            } finally {
                                recoverFacade.submissionView().completeAfterContinuation(
                                        server, 31L, 32L);
                            }
                        } catch (TestError caught) {
                            check(caught == recoverError, "recover Error identity was masked");
                        }
                        rejectedDiscard(recoverFacade, recoverSession);
                        var recoverReplacement = recoverFacade.arm(
                                server, 31L, 32L, 34L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        recoverFacade.discard(recoverReplacement);

                        var recordFacade = new P4E2QualificationFacade();
                        var recordSession = recordFacade.arm(
                                server, 41L, 42L, 43L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        var recordOutOfMemory = new OutOfMemoryError("representative fixture");
                        try {
                            try {
                                recordFacade.submissionView().recordRecovery(
                                        server, 41L, 42L,
                                        P4E2QualificationFacade.RecoveryVariant.NO_PENDING,
                                        P4E2QualificationFacade.RecoveryDetail.NONE, 0, 0);
                                throw recordOutOfMemory;
                            } finally {
                                recordFacade.submissionView().completeAfterContinuation(
                                        server, 41L, 42L);
                            }
                        } catch (OutOfMemoryError caught) {
                            check(caught == recordOutOfMemory, "record OOME identity was masked");
                        }
                        rejectedConsume(recordFacade, recordSession);
                        var recordReplacement = recordFacade.arm(
                                server, 41L, 42L, 44L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        recordFacade.discard(recordReplacement);

                        var continuationFacade = new P4E2QualificationFacade();
                        var continuationSession = continuationFacade.arm(
                                server, 51L, 52L, 53L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        var continuationError = new TestError();
                        try {
                            try {
                                continuationFacade.submissionView().recordRecovery(
                                        server, 51L, 52L,
                                        P4E2QualificationFacade.RecoveryVariant.NO_PENDING,
                                        P4E2QualificationFacade.RecoveryDetail.NONE, 0, 0);
                                continuationFacade.storeView().recordContinuation(
                                        server, 51L, 52L);
                                throw continuationError;
                            } finally {
                                continuationFacade.submissionView().completeAfterContinuation(
                                        server, 51L, 52L);
                            }
                        } catch (TestError caught) {
                            check(caught == continuationError,
                                    "continuation Error identity was masked");
                        }
                        rejectedDiscard(continuationFacade, continuationSession);
                        var continuationReplacement = continuationFacade.arm(
                                server, 51L, 52L, 54L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        continuationFacade.discard(continuationReplacement);
                    }

                    private static void qualificationCleanupPreservesPrimaryAfterProductionClear() {
                        var server = new MinecraftServer();

                        var armedFacade = new P4E2QualificationFacade();
                        var armedSession = armedFacade.arm(
                                server, 101L, 102L, 103L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        var armedError = new TestError();
                        try {
                            failAfterProductionCleanup(
                                    armedFacade, armedSession, server,
                                    101L, 102L, armedError, false);
                        } catch (TestError caught) {
                            check(caught == armedError,
                                    "outer cleanup masked the armed Error identity");
                        }
                        rejectedConsume(armedFacade, armedSession);
                        var armedReplacement = armedFacade.arm(
                                server, 101L, 102L, 104L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        armedFacade.discard(armedReplacement);

                        var recordingFacade = new P4E2QualificationFacade();
                        var recordingSession = recordingFacade.arm(
                                server, 111L, 112L, 113L,
                                P4E2QualificationFacade.Phase.READY_RESTART);
                        var recordingOutOfMemory =
                                new OutOfMemoryError("representative outer cleanup fixture");
                        try {
                            failAfterProductionCleanup(
                                    recordingFacade, recordingSession, server,
                                    111L, 112L, recordingOutOfMemory, true);
                        } catch (OutOfMemoryError caught) {
                            check(caught == recordingOutOfMemory,
                                    "outer cleanup masked the recording OOME identity");
                        }
                        rejectedConsume(recordingFacade, recordingSession);
                        var recordingReplacement = recordingFacade.arm(
                                server, 111L, 112L, 114L,
                                P4E2QualificationFacade.Phase.READY_RESTART);
                        recordingFacade.discard(recordingReplacement);
                    }

                    private static void failAfterProductionCleanup(
                            P4E2QualificationFacade facade,
                            P4E2QualificationFacade.Session session,
                            MinecraftServer server,
                            long playerMost,
                            long playerLeast,
                            Error primaryFailure,
                            boolean recording) {
                        try {
                            try {
                                if (recording) {
                                    facade.submissionView().recordRecovery(
                                            server, playerMost, playerLeast,
                                            P4E2QualificationFacade.RecoveryVariant.NO_PENDING,
                                            P4E2QualificationFacade.RecoveryDetail.NONE, 0, 0);
                                }
                                throw primaryFailure;
                            } finally {
                                facade.submissionView().completeAfterContinuation(
                                        server, playerMost, playerLeast);
                            }
                        } catch (RuntimeException | Error failure) {
                            try {
                                facade.discard(session);
                            } catch (RuntimeException | Error ignoredCleanupFailure) {
                                // Mirrors the test-only adapter: preserve the original identity.
                            }
                            throw failure;
                        }
                    }

                    private static void identityThreadAndMandatoryCoordinatesFailFast()
                            throws InterruptedException {
                        var facade = new P4E2QualificationFacade();
                        var foreign = new P4E2QualificationFacade();
                        var server = new MinecraftServer();
                        var session = facade.arm(
                                server, 7L, 8L, 9L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        try {
                            facade.arm(server, 7L, 8L, 9L,
                                    P4E2QualificationFacade.Phase.READY_FIRST);
                            throw new AssertionError("second arm succeeded");
                        } catch (IllegalStateException expected) {
                        }
                        try {
                            foreign.discard(session);
                            throw new AssertionError("foreign facade accepted session");
                        } catch (IllegalStateException expected) {
                        }
                        try {
                            facade.submissionView().enabledFor(
                                    new MinecraftServer(), 7L, 8L);
                            throw new AssertionError("wrong server succeeded");
                        } catch (IllegalStateException expected) {
                        }
                        try {
                            facade.submissionView().enabledFor(server, 7L, 99L);
                            throw new AssertionError("wrong player succeeded");
                        } catch (IllegalStateException expected) {
                        }
                        var failure = new AtomicReference<Throwable>();
                        var thread = new Thread(() -> {
                            try {
                                facade.submissionView().enabledFor(server, 7L, 8L);
                            } catch (Throwable throwable) {
                                failure.set(throwable);
                            }
                        });
                        thread.start();
                        thread.join();
                        check(failure.get() instanceof IllegalStateException, "wrong thread");
                        facade.submissionView().completeAfterContinuation(server, 7L, 8L);
                        rejectedDiscard(facade, session);

                        var missingSubmission = new P4E2QualificationFacade();
                        var missingSession = missingSubmission.arm(
                                server, 11L, 12L, 13L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        missingSubmission.storeView().recordContinuation(
                                server, 11L, 12L);
                        try {
                            missingSubmission.storeView().recordReconciliation(
                                    server, 11L, 12L,
                                    P4E2QualificationFacade.ReconciliationVariant.NO_CHANGES,
                                    P4E2QualificationFacade.ReconciliationDetail.NONE,
                                    0, 0, 0, 0, 0, 0, 0, 0, false);
                            throw new AssertionError("missing recovery coordinate succeeded");
                        } catch (IllegalStateException expected) {
                            missingSubmission.discard(missingSession);
                        }
                    }

                    private static void stopClearsActiveAndCompletedCells() {
                        var facade = new P4E2QualificationFacade();
                        var server = new MinecraftServer();
                        var active = facade.arm(
                                server, 21L, 22L, 23L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        facade.storeView().clearOnServerStopped();
                        rejectedDiscard(facade, active);

                        var completed = facade.arm(
                                server, 21L, 22L, 24L,
                                P4E2QualificationFacade.Phase.READY_RESTART);
                        facade.submissionView().recordRecovery(
                                server, 21L, 22L,
                                P4E2QualificationFacade.RecoveryVariant.NO_PENDING,
                                P4E2QualificationFacade.RecoveryDetail.NONE, 0, 0);
                        facade.storeView().recordContinuation(server, 21L, 22L);
                        facade.storeView().recordReconciliation(
                                server, 21L, 22L,
                                P4E2QualificationFacade.ReconciliationVariant.NO_CHANGES,
                                P4E2QualificationFacade.ReconciliationDetail.NONE,
                                0, 0, 0, 0, 0, 0, 0, 0, false);
                        facade.submissionView().completeAfterContinuation(
                                server, 21L, 22L);
                        facade.storeView().clearOnServerStopped();
                        rejectedConsume(facade, completed);

                        var finalSession = facade.arm(
                                server, 21L, 22L, 25L,
                                P4E2QualificationFacade.Phase.READY_FIRST);
                        facade.discard(finalSession);
                    }

                    private static void rejectedConsume(
                            P4E2QualificationFacade facade,
                            P4E2QualificationFacade.Session session) {
                        try {
                            facade.consume(session);
                            throw new AssertionError("consume unexpectedly succeeded");
                        } catch (IllegalStateException expected) {
                        }
                    }

                    private static void rejectedDiscard(
                            P4E2QualificationFacade facade,
                            P4E2QualificationFacade.Session session) {
                        try {
                            facade.discard(session);
                            throw new AssertionError("discard unexpectedly succeeded");
                        } catch (IllegalStateException expected) {
                        }
                    }

                    private static void check(boolean condition, String message) {
                        if (!condition) {
                            throw new AssertionError(message);
                        }
                    }

                    private static final class TestError extends Error {
                        private static final long serialVersionUID = 1L;
                    }
                }
                """);

        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        boolean success;
        try (var files = compiler.getStandardFileManager(
                diagnostics, java.util.Locale.ROOT, StandardCharsets.UTF_8)) {
            var units = files.getJavaFileObjectsFromPaths(
                    List.of(FACADE_SOURCE, serverStub, extensionStub, harness));
            var options = List.of(
                    "--release", "21",
                    "-proc:none",
                    "-Xlint:all",
                    "-Werror",
                    "-classpath", outputRoot.toString(),
                    "-d", outputRoot.toString());
            success = Boolean.TRUE.equals(compiler.getTask(
                    null, files, diagnostics, options, null, units).call());
        }
        assertTrue(success, () -> diagnostics.getDiagnostics().toString());

        try (var loader = new URLClassLoader(
                new java.net.URL[] {outputRoot.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            var stateHarness = Class.forName(
                    "com.yo1no.gramarye.FacadeStateHarness", true, loader);
            assertEquals("PASS", stateHarness.getMethod("run").invoke(null));
        }
    }

    private static void assertClosedView(Class<?> view, int operationCount) {
        assertTrue(Modifier.isPublic(view.getModifiers()));
        assertTrue(Modifier.isAbstract(view.getModifiers()));
        assertTrue(view.isSealed());
        assertEquals(1, view.getPermittedSubclasses().length);
        assertTrue(Modifier.isFinal(view.getPermittedSubclasses()[0].getModifiers()));
        assertTrue(Modifier.isPrivate(view.getPermittedSubclasses()[0].getModifiers()));
        assertTrue(Arrays.stream(view.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
        assertEquals(operationCount, Arrays.stream(view.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .peek(method -> assertTrue(Modifier.isFinal(method.getModifiers())))
                .count());
    }

    private static Path write(Path root, String relative, String source) throws Exception {
        var path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, source, StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        var candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("project root not found");
    }
}
