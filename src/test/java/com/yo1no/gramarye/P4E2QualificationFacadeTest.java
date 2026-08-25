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
import java.util.Set;
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
                .noneMatch(field -> field.getType()
                        == P4E2QualificationFacade.E3StartupSession.class));
        assertTrue(Arrays.stream(type.getDeclaredFields())
                .noneMatch(field -> Modifier.isStatic(field.getModifiers())
                        && !Modifier.isFinal(field.getModifiers())));
        assertTrue(Arrays.stream(type.getDeclaredFields())
                .noneMatch(field -> java.util.Collection.class.isAssignableFrom(field.getType())
                        || java.util.Map.class.isAssignableFrom(field.getType())
                        || ThreadLocal.class.isAssignableFrom(field.getType())));
    }

    @Test
    void fourViewsAreClosedOwnerBoundNominalsWithOnlyFinalOperations() {
        assertClosedView(P4E2QualificationFacade.SubmissionView.class, 3);
        assertClosedView(P4E2QualificationFacade.StoreView.class, 7);
        assertClosedView(P4E2QualificationFacade.PlayerView.class, 2);
        assertClosedView(P4E2QualificationFacade.E3StartupView.class, 13);

        var facade = new P4E2QualificationFacade();
        assertTrue(facade.submissionView() == facade.submissionView());
        assertTrue(facade.storeView() == facade.storeView());
        assertTrue(facade.playerView() == facade.playerView());
        assertTrue(facade.storeView().e3StartupView()
                == facade.storeView().e3StartupView());

        assertEquals(List.of(
                "COMPLETE",
                "INCOMPLETE",
                "OVER_LIMIT",
                "RECONCILIATION_REQUIRED",
                "GENERATION_EXHAUSTED"),
                Arrays.stream(P4E2QualificationFacade.E3AuditVariant.values())
                        .map(Enum::name)
                        .toList());
        assertEquals(List.of("COMPLETE", "INCOMPLETE", "TRUNCATED", "OVER_LIMIT"),
                Arrays.stream(P4E2QualificationFacade.E3SnapshotVariant.values())
                        .map(Enum::name)
                        .toList());
        assertEquals(List.of(
                "COMPLETED_ZERO", "COMPLETED_POSITIVE", "REJECTED", "UNAVAILABLE"),
                Arrays.stream(P4E2QualificationFacade.E3ReclaimVariant.values())
                        .map(Enum::name)
                        .toList());
        assertEquals(List.of("COMPLETE_INDEX", "INCOMPLETE"),
                Arrays.stream(P4E2QualificationFacade.E3IndexTerminal.values())
                        .map(Enum::name)
                        .toList());
    }

    @Test
    void e3SurfaceAndBoundedRecordsAreExact() throws Exception {
        var exactOperations = Set.of(
                "boolean beginRecording(net.minecraft.server.MinecraftServer)",
                "void recordAuditInvocation(net.minecraft.server.MinecraftServer)",
                "void recordAuditResult(net.minecraft.server.MinecraftServer,"
                        + "com.yo1no.gramarye.P4E2QualificationFacade$E3AuditVariant,long)",
                "void recordCompleteConsumeInvocation("
                        + "net.minecraft.server.MinecraftServer)",
                "void recordSnapshotInvocation(net.minecraft.server.MinecraftServer)",
                "void recordSnapshotResult(net.minecraft.server.MinecraftServer,"
                        + "com.yo1no.gramarye.P4E2QualificationFacade$E3SnapshotVariant,int)",
                "void recordReclaimInvocation(net.minecraft.server.MinecraftServer,boolean)",
                "void recordReclaimResult(net.minecraft.server.MinecraftServer,"
                        + "com.yo1no.gramarye.P4E2QualificationFacade$E3ReclaimVariant,"
                        + "int,int,int,int)",
                "void recordDirtyAfter(net.minecraft.server.MinecraftServer,boolean)",
                "void recordIndexTerminal(net.minecraft.server.MinecraftServer,"
                        + "com.yo1no.gramarye.P4E2QualificationFacade$E3IndexTerminal,long)",
                "void completeRecording(net.minecraft.server.MinecraftServer)",
                "void abortRecording(net.minecraft.server.MinecraftServer)",
                "void clearOnServerStopped(net.minecraft.server.MinecraftServer)");
        var actualOperations = Arrays.stream(
                        P4E2QualificationFacade.E3StartupView.class.getDeclaredMethods())
                .map(P4E2QualificationFacadeTest::methodDescriptor)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(exactOperations, actualOperations);

        var facadeType = P4E2QualificationFacade.class;
        assertPackagePrivate(facadeType.getDeclaredMethod(
                "armE3Startup", net.minecraft.server.MinecraftServer.class));
        assertPackagePrivate(facadeType.getDeclaredMethod(
                "claimE3Startup", net.minecraft.server.MinecraftServer.class));
        assertPackagePrivate(facadeType.getDeclaredMethod(
                "consumeE3Startup",
                net.minecraft.server.MinecraftServer.class,
                P4E2QualificationFacade.E3StartupSession.class));
        assertPackagePrivate(facadeType.getDeclaredMethod(
                "abortE3Startup",
                net.minecraft.server.MinecraftServer.class,
                P4E2QualificationFacade.E3StartupSession.class));

        var sessionType = P4E2QualificationFacade.E3StartupSession.class;
        assertTrue(Modifier.isFinal(sessionType.getModifiers()));
        assertTrue(!Modifier.isPublic(sessionType.getModifiers()));
        assertTrue(Arrays.stream(sessionType.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
        assertEquals(List.of("owner", "token"), Arrays.stream(sessionType.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .sorted()
                .toList());

        var snapshotType = P4E2QualificationFacade.E3StartupSnapshot.class;
        assertTrue(snapshotType.isRecord());
        assertTrue(!Modifier.isPublic(snapshotType.getModifiers()));
        assertEquals(List.of(
                "sessionToken",
                "auditInvocations",
                "auditVariant",
                "auditGeneration",
                "completeConsumeInvocations",
                "snapshotInvocations",
                "snapshotVariant",
                "completeRootCount",
                "reclaimInvocations",
                "reclaimVariant",
                "historiesScanned",
                "revisionsScanned",
                "historiesChanged",
                "revisionsReclaimed",
                "dirtyBefore",
                "dirtyAfter",
                "indexTerminalObservations",
                "indexTerminal",
                "indexGeneration"),
                Arrays.stream(snapshotType.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList());
        var serverWitness = facadeType.getDeclaredField("e3SessionServer");
        assertEquals(net.minecraft.server.MinecraftServer.class, serverWitness.getType());
        assertTrue(Modifier.isPrivate(serverWitness.getModifiers()));
        assertTrue(!Modifier.isStatic(serverWitness.getModifiers()));
        assertEquals(1L, Arrays.stream(facadeType.getDeclaredFields())
                .filter(field -> field.getName().startsWith("e3"))
                .filter(field -> field.getType()
                        == net.minecraft.server.MinecraftServer.class)
                .count());
    }

    @Test
    void e3TokenExhaustionFailsBeforeMutationAndTheCounterIsNeverCleared()
            throws Exception {
        var source = Files.readString(FACADE_SOURCE);
        var armStart = source.indexOf("void armE3Startup(");
        var armEnd = source.indexOf("E3StartupSession claimE3Startup(", armStart);
        var arm = source.substring(armStart, armEnd);
        var increment = arm.indexOf("var next = Math.incrementExact(e3NextToken);");
        assertTrue(increment >= 0, "E3 token does not use Math.incrementExact");
        var beforeIncrement = arm.substring(0, increment);
        for (var mutation : List.of(
                "resetE3Coordinates();",
                "e3SessionServer =",
                "e3NextToken =",
                "e3ActiveToken =",
                "e3State = E3StartupState.ARMED_BEFORE_SERVER_STARTING")) {
            assertTrue(!beforeIncrement.contains(mutation),
                    () -> "E3 cell mutates before token exhaustion check: " + mutation);
        }
        assertSourceOrder(
                arm,
                "var next = Math.incrementExact(e3NextToken);",
                "resetE3Coordinates();",
                "e3SessionServer = exactServer;",
                "e3NextToken = next;",
                "e3ActiveToken = next;",
                "e3State = E3StartupState.ARMED_BEFORE_SERVER_STARTING;");

        var clearStart = source.indexOf("private void clearE3Cell()");
        var clearEnd = source.indexOf(
                "private static IllegalStateException e3Failure", clearStart);
        var clear = source.substring(clearStart, clearEnd);
        assertTrue(!clear.contains("e3NextToken"),
                "E3 cell cleanup resets the facade-lifetime token counter");
    }

    @Test
    void exactProductionSourceStateMachinePassesAgainstConstructorFreeServerStub()
            throws Exception {
        var sourceRoot = Files.createDirectories(temporary.resolve("source"));
        var outputRoot = Files.createDirectories(temporary.resolve("classes"));
        var serverStub = write(sourceRoot, "net/minecraft/server/MinecraftServer.java", """
                package net.minecraft.server;

                public class MinecraftServer {
                    private final Thread owner = Thread.currentThread();

                    public boolean isSameThread() {
                        return Thread.currentThread() == owner;
                    }
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
                        e3UnarmedOperationsAreExactNoOps();
                        e3CompletedZeroRoundTripsAndCleansWitness();
                        e3AuditAndSnapshotTerminalsRemainBounded();
                        e3WrongContextPreservesTheLawfulCell();
                        e3SessionOwnerAndTokenAreExact();
                        e3ObservationWrapperPreservesFailureIdentityAndCleans();
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

                    private static void e3UnarmedOperationsAreExactNoOps() {
                        var facade = new P4E2QualificationFacade();
                        var view = facade.storeView().e3StartupView();
                        check(view == facade.storeView().e3StartupView(),
                                "E3 view identity changed");
                        check(!view.beginRecording(null), "unarmed E3 recording began");
                        view.recordAuditInvocation(null);
                        view.recordAuditResult(null, null, -1L);
                        view.recordCompleteConsumeInvocation(null);
                        view.recordSnapshotInvocation(null);
                        view.recordSnapshotResult(null, null, -2);
                        view.recordReclaimInvocation(null, true);
                        view.recordReclaimResult(null, null, -2, -2, -2, -2);
                        view.recordDirtyAfter(null, true);
                        view.recordIndexTerminal(null, null, -1L);
                        view.completeRecording(null);
                        view.abortRecording(null);
                        view.clearOnServerStopped(null);
                    }

                    private static void e3CompletedZeroRoundTripsAndCleansWitness() {
                        var facade = new P4E2QualificationFacade();
                        var server = new MinecraftServer();
                        facade.armE3Startup(server);
                        expectE3Failure(
                                "P4E3_STARTUP_OBSERVATION_ALREADY_ACTIVE",
                                () -> facade.armE3Startup(server));
                        var view = facade.storeView().e3StartupView();
                        check(view.beginRecording(server), "armed E3 recording did not begin");
                        expectE3Failure(
                                "P4E3_STARTUP_OBSERVATION_WRONG_STATE",
                                () -> view.beginRecording(server));
                        view.recordAuditInvocation(server);
                        view.recordAuditResult(
                                server,
                                P4E2QualificationFacade.E3AuditVariant.COMPLETE,
                                7L);
                        view.recordCompleteConsumeInvocation(server);
                        view.recordSnapshotInvocation(server);
                        view.recordSnapshotResult(
                                server,
                                P4E2QualificationFacade.E3SnapshotVariant.COMPLETE,
                                2);
                        expectE3Failure(
                                "P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE",
                                () -> view.recordIndexTerminal(
                                        server,
                                        P4E2QualificationFacade.E3IndexTerminal.COMPLETE_INDEX,
                                        7L));
                        view.recordReclaimInvocation(server, false);
                        view.recordReclaimResult(
                                server,
                                P4E2QualificationFacade.E3ReclaimVariant.COMPLETED_ZERO,
                                3,
                                4,
                                0,
                                0);
                        view.recordDirtyAfter(server, false);
                        view.recordIndexTerminal(
                                server,
                                P4E2QualificationFacade.E3IndexTerminal.COMPLETE_INDEX,
                                7L);
                        view.completeRecording(server);

                        var foreignServer = new MinecraftServer();
                        expectE3Failure(
                                "P4E3_STARTUP_OBSERVATION_WRONG_CONTEXT",
                                () -> view.clearOnServerStopped(foreignServer));
                        var session = facade.claimE3Startup(server);
                        expectE3Failure(
                                "P4E3_STARTUP_OBSERVATION_ALREADY_CLAIMED",
                                () -> facade.claimE3Startup(server));
                        var snapshot = facade.consumeE3Startup(server, session);
                        check(snapshot.sessionToken() == 1L, "first E3 token");
                        check(snapshot.auditInvocations() == 1, "audit calls");
                        check(snapshot.auditVariant()
                                        == P4E2QualificationFacade.E3AuditVariant.COMPLETE,
                                "audit variant");
                        check(snapshot.auditGeneration() == 7L, "audit generation");
                        check(snapshot.completeConsumeInvocations() == 1,
                                "consume calls");
                        check(snapshot.snapshotInvocations() == 1, "snapshot calls");
                        check(snapshot.snapshotVariant()
                                        == P4E2QualificationFacade.E3SnapshotVariant.COMPLETE,
                                "snapshot variant");
                        check(snapshot.completeRootCount() == 2, "root count");
                        check(snapshot.reclaimInvocations() == 1, "reclaim calls");
                        check(snapshot.reclaimVariant()
                                        == P4E2QualificationFacade.E3ReclaimVariant.COMPLETED_ZERO,
                                "reclaim variant");
                        check(snapshot.historiesScanned() == 3, "histories scanned");
                        check(snapshot.revisionsScanned() == 4, "revisions scanned");
                        check(snapshot.historiesChanged() == 0, "histories changed");
                        check(snapshot.revisionsReclaimed() == 0, "revisions reclaimed");
                        check(!snapshot.dirtyBefore() && !snapshot.dirtyAfter(),
                                "zero reclaim dirty transition");
                        check(snapshot.indexTerminalObservations() == 1,
                                "terminal observations");
                        check(snapshot.indexTerminal()
                                        == P4E2QualificationFacade.E3IndexTerminal.COMPLETE_INDEX,
                                "terminal variant");
                        check(snapshot.indexGeneration() == 7L, "terminal generation");
                        expectE3Failure(
                                "P4E3_STARTUP_OBSERVATION_WRONG_STATE",
                                () -> facade.consumeE3Startup(server, session));

                        facade.armE3Startup(server);
                        view.abortRecording(server);
                        facade.armE3Startup(server);
                        view.clearOnServerStopped(server);
                    }

                    private static void e3AuditAndSnapshotTerminalsRemainBounded() {
                        var server = new MinecraftServer();
                        var auditFacade = new P4E2QualificationFacade();
                        auditFacade.armE3Startup(server);
                        var auditView = auditFacade.storeView().e3StartupView();
                        check(auditView.beginRecording(server), "audit terminal begin");
                        auditView.recordAuditInvocation(server);
                        auditView.recordAuditResult(
                                server,
                                P4E2QualificationFacade.E3AuditVariant.OVER_LIMIT,
                                11L);
                        auditView.completeRecording(server);
                        var auditSession = auditFacade.claimE3Startup(server);
                        var audit = auditFacade.consumeE3Startup(server, auditSession);
                        check(audit.completeConsumeInvocations() == 0,
                                "audit terminal consumed Complete");
                        check(audit.snapshotInvocations() == 0,
                                "audit terminal invoked snapshot");
                        check(audit.reclaimInvocations() == 0,
                                "audit terminal invoked reclaim");
                        check(audit.indexTerminalObservations() == 0,
                                "audit terminal observed B.9");

                        var snapshotFacade = new P4E2QualificationFacade();
                        snapshotFacade.armE3Startup(server);
                        var snapshotView = snapshotFacade.storeView().e3StartupView();
                        check(snapshotView.beginRecording(server), "snapshot terminal begin");
                        snapshotView.recordAuditInvocation(server);
                        snapshotView.recordAuditResult(
                                server,
                                P4E2QualificationFacade.E3AuditVariant.COMPLETE,
                                12L);
                        snapshotView.recordCompleteConsumeInvocation(server);
                        snapshotView.recordSnapshotInvocation(server);
                        snapshotView.recordSnapshotResult(
                                server,
                                P4E2QualificationFacade.E3SnapshotVariant.INCOMPLETE,
                                -1);
                        snapshotView.recordIndexTerminal(
                                server,
                                P4E2QualificationFacade.E3IndexTerminal.INCOMPLETE,
                                12L);
                        snapshotView.completeRecording(server);
                        var snapshotSession = snapshotFacade.claimE3Startup(server);
                        var snapshot = snapshotFacade.consumeE3Startup(
                                server, snapshotSession);
                        check(snapshot.completeRootCount() == -1,
                                "snapshot terminal root sentinel");
                        check(snapshot.reclaimInvocations() == 0,
                                "snapshot terminal invoked reclaim");
                        check(snapshot.indexTerminal()
                                        == P4E2QualificationFacade.E3IndexTerminal.INCOMPLETE,
                                "snapshot terminal B.9 variant");
                    }

                    private static void e3WrongContextPreservesTheLawfulCell()
                            throws InterruptedException {
                        var facade = new P4E2QualificationFacade();
                        var server = new MinecraftServer();
                        facade.armE3Startup(server);
                        var failure = new AtomicReference<Throwable>();
                        var thread = new Thread(() -> {
                            try {
                                facade.storeView().e3StartupView().beginRecording(server);
                            } catch (Throwable throwable) {
                                failure.set(throwable);
                            }
                        });
                        thread.start();
                        thread.join();
                        check(failure.get() instanceof IllegalStateException,
                                "E3 wrong thread was accepted");
                        check("P4E3_STARTUP_OBSERVATION_WRONG_CONTEXT".equals(
                                        failure.get().getMessage()),
                                "E3 wrong-thread failure code");
                        check(facade.storeView().e3StartupView().beginRecording(server),
                                "wrong thread cleared lawful E3 cell");
                        facade.storeView().e3StartupView().abortRecording(server);
                    }

                    private static void e3SessionOwnerAndTokenAreExact() {
                        var server = new MinecraftServer();
                        var facade = new P4E2QualificationFacade();
                        completeE3AuditTerminal(facade, server, 21L);
                        var firstSession = facade.claimE3Startup(server);
                        var first = facade.consumeE3Startup(server, firstSession);
                        check(first.sessionToken() == 1L, "first session token was not one");

                        completeE3AuditTerminal(facade, server, 22L);
                        expectE3Failure(
                                "P4E3_STARTUP_OBSERVATION_WRONG_SESSION",
                                () -> facade.consumeE3Startup(server, firstSession));
                        var secondSession = facade.claimE3Startup(server);
                        var second = facade.consumeE3Startup(server, secondSession);
                        check(second.sessionToken() == 2L,
                                "E3 token did not use its strict successor");

                        var foreign = new P4E2QualificationFacade();
                        completeE3AuditTerminal(foreign, server, 23L);
                        expectE3Failure(
                                "P4E3_STARTUP_OBSERVATION_WRONG_SESSION",
                                () -> foreign.consumeE3Startup(server, secondSession));
                        var foreignSession = foreign.claimE3Startup(server);
                        foreign.abortE3Startup(server, foreignSession);
                    }

                    private static void completeE3AuditTerminal(
                            P4E2QualificationFacade facade,
                            MinecraftServer server,
                            long generation) {
                        facade.armE3Startup(server);
                        var view = facade.storeView().e3StartupView();
                        check(view.beginRecording(server), "audit terminal did not begin");
                        view.recordAuditInvocation(server);
                        view.recordAuditResult(
                                server,
                                P4E2QualificationFacade.E3AuditVariant.INCOMPLETE,
                                generation);
                        view.completeRecording(server);
                    }

                    private static void e3ObservationWrapperPreservesFailureIdentityAndCleans() {
                        assertE3PrimaryAndCleanup(new RuntimeException("e3-runtime"));
                        assertE3PrimaryAndCleanup(new TestError());
                        assertE3PrimaryAndCleanup(new OutOfMemoryError("e3-oome"));
                    }

                    private static void assertE3PrimaryAndCleanup(Throwable primary) {
                        var facade = new P4E2QualificationFacade();
                        var server = new MinecraftServer();
                        facade.armE3Startup(server);
                        try {
                            failInsideE3ObservationWrapper(facade, server, primary);
                            throw new AssertionError("E3 primary failure did not escape");
                        } catch (RuntimeException | Error escaped) {
                            check(escaped == primary,
                                    "E3 cleanup replaced the primary Throwable identity");
                        }
                        facade.armE3Startup(server);
                        facade.storeView().e3StartupView().abortRecording(server);
                    }

                    private static void failInsideE3ObservationWrapper(
                            P4E2QualificationFacade facade,
                            MinecraftServer server,
                            Throwable primary) {
                        var view = facade.storeView().e3StartupView();
                        var recording = view.beginRecording(server);
                        try {
                            if (recording) {
                                view.recordAuditInvocation(server);
                            }
                            if (primary instanceof RuntimeException runtimeFailure) {
                                throw runtimeFailure;
                            }
                            throw (Error) primary;
                        } catch (RuntimeException | Error failure) {
                            if (recording) {
                                view.abortRecording(server);
                            }
                            throw failure;
                        }
                    }

                    private static void expectE3Failure(
                            String code, Runnable operation) {
                        try {
                            operation.run();
                            throw new AssertionError("expected E3 failure: " + code);
                        } catch (IllegalStateException expected) {
                            check(code.equals(expected.getMessage()),
                                    "unexpected E3 failure code: " + expected.getMessage());
                        }
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

    private static void assertPackagePrivate(java.lang.reflect.Method method) {
        var modifiers = method.getModifiers();
        assertTrue(!Modifier.isPublic(modifiers)
                && !Modifier.isProtected(modifiers)
                && !Modifier.isPrivate(modifiers));
    }

    private static String methodDescriptor(java.lang.reflect.Method method) {
        return method.getReturnType().getTypeName()
                + " "
                + method.getName()
                + "("
                + Arrays.stream(method.getParameterTypes())
                        .map(Class::getTypeName)
                        .collect(java.util.stream.Collectors.joining(","))
                + ")";
    }

    private static void assertSourceOrder(String source, String... fragments) {
        var previous = -1;
        for (var fragment : fragments) {
            var current = source.indexOf(fragment, previous + 1);
            assertTrue(current > previous, () -> "missing or out-of-order: " + fragment);
            previous = current;
        }
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
