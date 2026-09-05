package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

final class P7ServerSessionServiceTest {
    @Test
    void initialSessionHasExactEpochSequenceTokenAndStrikeState() {
        var identity = new P7SessionIdentity(
                UUID.fromString("00000000-0000-0000-0000-000000000706"), 1L);
        var state = P7ServerSessionState.initial(identity, 12L);

        assertEquals(identity, state.identity());
        assertEquals(1L, state.admissionState().sequenceState()
                .expectedNext().orElseThrow());
        assertEquals(
                P7NetworkBounds.RATE_BUCKET_INITIAL_TOKENS,
                state.admissionState().tokenBucket().tokens());
        assertEquals(0, state.admissionState().playerIngressBudget().used());
        assertEquals(0, state.admissionState().rateStrikeState().strikeCount());
    }

    @Test
    void sessionStateReplacementIsImmutableAndIdentityPreserving() {
        var identity = new P7SessionIdentity(
                UUID.fromString("00000000-0000-0000-0000-000000000707"), 2L);
        var original = P7ServerSessionState.initial(identity, 0L);
        var decision = CastIntentAdmissionSemantics.evaluate(
                original.admissionState(),
                IntentTickBudget.initial(IntentTickBudget.Kind.GLOBAL_WORK, 0L),
                0L,
                1L);
        var replacement = original.withAdmissionState(decision.nextSessionState());

        assertNotSame(original, replacement);
        assertEquals(identity, replacement.identity());
        assertEquals(1L, original.admissionState().sequenceState()
                .expectedNext().orElseThrow());
        assertEquals(2L, replacement.admissionState().sequenceState()
                .expectedNext().orElseThrow());
    }

    @Test
    void newSessionOwnerStartsEmptyAndEpochSnapshotIsScalarOnly() {
        var service = new P7ServerSessionService(
                new P7ServerAccess(), new P7ReloadAdmissionGate());
        var playerId = UUID.fromString("00000000-0000-0000-0000-000000000708");

        assertEquals(0, service.activeSessionCount());
        assertTrue(service.currentEpoch(playerId).isEmpty());
    }

    @Test
    void sessionOwnerFieldsRetainNoServerPlayerWorldOrThrowable() {
        var fields = Arrays.stream(P7ServerSessionService.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        var names = fields.stream().map(field -> field.getGenericType().getTypeName()).toList();

        assertTrue(fields.stream().allMatch(field -> Modifier.isPrivate(field.getModifiers())));
        assertEquals(Set.of(
                Object.class,
                java.util.Map.class,
                P7ServerAccess.class,
                P7ReloadAdmissionGate.class,
                ConnectionEpochState.class,
                boolean.class,
                IntentTickBudget.class), fields.stream()
                .map(java.lang.reflect.Field::getType)
                .collect(java.util.stream.Collectors.toSet()));
        for (var forbidden : List.of(
                "MinecraftServer",
                "ServerPlayer",
                "Entity",
                "Level",
                "IPayloadContext",
                "ByteBuf",
                "SkillReference",
                "RuntimeAdmissionResult",
                "Throwable")) {
            assertTrue(names.stream().noneMatch(name -> name.contains(forbidden)), forbidden);
        }
    }

    @Test
    void actualProductionSessionOwnerExecutesBoundedAtomicLifecycleSemantics()
            throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var temporaryRoot = Files.createTempDirectory("p7-session-owner-behavior-");
        var sourceRoot = temporaryRoot.resolve("source");
        var outputRoot = temporaryRoot.resolve("classes");
        Files.createDirectories(outputRoot);

        var minecraftServerStub = write(sourceRoot,
                "net/minecraft/server/MinecraftServer.java", """
                package net.minecraft.server;

                public final class MinecraftServer {
                    private boolean sameThread = true;
                    private boolean running = true;
                    private long authoritativeTick;

                    public boolean sameThread() {
                        return sameThread;
                    }

                    public void setSameThread(boolean value) {
                        sameThread = value;
                    }

                    public boolean running() {
                        return running;
                    }

                    public void setRunning(boolean value) {
                        running = value;
                    }

                    public long authoritativeTick() {
                        return authoritativeTick;
                    }

                    public void setAuthoritativeTick(long value) {
                        authoritativeTick = value;
                    }
                }
                """);
        var serverAccessStub = write(sourceRoot,
                "com/yo1no/gramarye/magic/network/P7ServerAccess.java", """
                package com.yo1no.gramarye.magic.network;

                import net.minecraft.server.MinecraftServer;

                final class P7ServerAccess {
                    boolean sameThread(MinecraftServer server) {
                        return server.sameThread();
                    }

                    boolean running(MinecraftServer server) {
                        return server.running();
                    }

                    long authoritativeTick(MinecraftServer server) {
                        return server.authoritativeTick();
                    }
                }
                """);
        var reloadGateStub = write(sourceRoot,
                "com/yo1no/gramarye/magic/network/P7ReloadAdmissionGate.java", """
                package com.yo1no.gramarye.magic.network;

                import net.minecraft.server.MinecraftServer;

                final class P7ReloadAdmissionGate {
                    boolean isOpen(MinecraftServer server) {
                        if (!server.sameThread()) {
                            throw new P7SemanticInvariantException(
                                    "reload gate requires the server thread");
                        }
                        return true;
                    }
                }
                """);
        var harness = write(sourceRoot,
                "com/yo1no/gramarye/magic/network/P7ServerSessionBehaviorHarness.java", """
                package com.yo1no.gramarye.magic.network;

                import java.util.ArrayList;
                import java.util.List;
                import java.util.UUID;
                import net.minecraft.server.MinecraftServer;

                public final class P7ServerSessionBehaviorHarness {
                    private P7ServerSessionBehaviorHarness() {
                    }

                    public static String run() {
                        epochsCloseAndReconnectResetAllPerSessionState();
                        capacityIsExactlyTwoHundredFiftySix();
                        wrongThreadOperationsHaveZeroMutation();
                        stoppedServerOpenHasZeroMutation();
                        transitionCommitsSessionAndGlobalStateAtomically();
                        return "PASS";
                    }

                    private static void epochsCloseAndReconnectResetAllPerSessionState() {
                        var server = serverAt(12L);
                        var service = service();
                        var playerId = uuid(1L);

                        check(service.currentEpoch(playerId).isEmpty(),
                                "unopened player exposed an epoch");
                        var firstEpoch = service.openSession(server, playerId).orElseThrow();
                        check(firstEpoch == 1L, "first epoch was not one");
                        check(service.currentEpoch(playerId).orElseThrow() == firstEpoch,
                                "currentEpoch did not expose the bounded scalar");
                        check(service.openSession(server, playerId).isEmpty(),
                                "duplicate active UUID was accepted");
                        check(service.activeSessionCount() == 1,
                                "duplicate active UUID mutated capacity");

                        var firstIdentity = new P7SessionIdentity(playerId, firstEpoch);
                        var accepted = service.transition(server, firstIdentity, 12L, 1L)
                                .orElseThrow();
                        check(accepted.outcome()
                                        == CastIntentAdmissionSemantics.Outcome.ELIGIBLE,
                                "first transition was not eligible");
                        var consumed = service.currentSession(firstIdentity)
                                .orElseThrow()
                                .admissionState();
                        check(consumed.sequenceState().expectedNext().orElseThrow() == 2L,
                                "accepted transition did not commit sequence");
                        check(consumed.tokenBucket().tokens() == 7,
                                "accepted transition did not commit token consumption");

                        check(!service.closeSession(server, playerId, firstEpoch + 1L),
                                "stale close removed the current session");
                        check(service.currentEpoch(playerId).orElseThrow() == firstEpoch,
                                "stale close changed the current epoch");
                        check(service.closeSession(server, playerId, firstEpoch),
                                "current close was rejected");
                        check(service.currentEpoch(playerId).isEmpty(),
                                "current close retained the session");

                        var secondEpoch = service.openSession(server, playerId).orElseThrow();
                        check(secondEpoch == 2L,
                                "duplicate rejection or reconnect corrupted epoch allocation");
                        check(service.currentEpoch(playerId).orElseThrow() == secondEpoch,
                                "reconnect epoch scalar was not current");
                        var reset = service.currentSession(
                                        new P7SessionIdentity(playerId, secondEpoch))
                                .orElseThrow()
                                .admissionState();
                        check(reset.sequenceState().expectedNext().orElseThrow() == 1L,
                                "reconnect retained the prior sequence");
                        check(reset.tokenBucket().tokens()
                                        == P7NetworkBounds.RATE_BUCKET_INITIAL_TOKENS,
                                "reconnect retained the prior token count");
                        check(reset.playerIngressBudget().used() == 0,
                                "reconnect retained the prior player budget");
                        check(reset.rateStrikeState().strikeCount() == 0,
                                "reconnect retained prior rate strikes");
                    }

                    private static void capacityIsExactlyTwoHundredFiftySix() {
                        var server = serverAt(20L);
                        var service = service();
                        var identities = new ArrayList<P7SessionIdentity>();
                        for (var index = 0;
                                index < P7NetworkBounds.MAX_ACTIVE_SESSIONS_PER_SERVER;
                                index++) {
                            var playerId = uuid(1_000L + index);
                            var epoch = service.openSession(server, playerId).orElseThrow();
                            check(epoch == index + 1L, "capacity allocation skipped an epoch");
                            identities.add(new P7SessionIdentity(playerId, epoch));
                        }
                        check(service.activeSessionCount() == 256,
                                "the 256th active session was not retained");

                        var overflowPlayer = uuid(2_000L);
                        check(service.openSession(server, overflowPlayer).isEmpty(),
                                "the 257th active session was accepted");
                        check(service.activeSessionCount() == 256,
                                "capacity rejection mutated the active count");

                        var first = identities.get(0);
                        check(service.closeSession(
                                        server,
                                        first.authenticatedPlayerId(),
                                        first.connectionEpoch()),
                                "capacity slot could not be released");
                        var postCloseEpoch = service.openSession(server, overflowPlayer)
                                .orElseThrow();
                        check(postCloseEpoch == 257L,
                                "capacity rejection consumed a connection epoch");
                        check(service.activeSessionCount() == 256,
                                "released capacity was not reusable");
                    }

                    private static void wrongThreadOperationsHaveZeroMutation() {
                        var server = serverAt(30L);
                        var service = service();
                        var playerId = uuid(3_000L);

                        server.setSameThread(false);
                        expectInvariant(() -> service.openSession(server, playerId),
                                "wrong-thread open");
                        check(service.activeSessionCount() == 0,
                                "wrong-thread open mutated sessions");
                        server.setSameThread(true);
                        var epoch = service.openSession(server, playerId).orElseThrow();
                        check(epoch == 1L, "wrong-thread open consumed an epoch");
                        var identity = new P7SessionIdentity(playerId, epoch);
                        var before = service.currentSession(identity)
                                .orElseThrow()
                                .admissionState();

                        server.setSameThread(false);
                        expectInvariant(() -> service.transition(server, identity, 30L, 1L),
                                "wrong-thread transition");
                        expectInvariant(() -> service.closeSession(server, playerId, epoch),
                                "wrong-thread close");
                        var after = service.currentSession(identity)
                                .orElseThrow()
                                .admissionState();
                        check(before.equals(after),
                                "wrong-thread transition partially mutated session state");
                        check(service.currentEpoch(playerId).orElseThrow() == epoch,
                                "wrong-thread close removed the session");

                        server.setSameThread(true);
                        var accepted = service.transition(server, identity, 30L, 1L)
                                .orElseThrow();
                        check(accepted.outcome()
                                        == CastIntentAdmissionSemantics.Outcome.ELIGIBLE,
                                "wrong-thread transition mutated global admission state");
                        check(service.closeSession(server, playerId, epoch),
                                "current-thread close failed after wrong-thread rejection");
                    }

                    private static void stoppedServerOpenHasZeroMutation() {
                        var server = serverAt(40L);
                        var service = service();
                        var playerId = uuid(4_000L);

                        server.setRunning(false);
                        check(service.openSession(server, playerId).isEmpty(),
                                "stopped server accepted a session");
                        check(service.activeSessionCount() == 0,
                                "stopped server mutated sessions");
                        server.setRunning(true);
                        check(service.openSession(server, playerId).orElseThrow() == 1L,
                                "stopped-server rejection consumed an epoch");
                    }

                    private static void transitionCommitsSessionAndGlobalStateAtomically() {
                        var server = serverAt(100L);
                        var service = service();
                        var identities = new ArrayList<P7SessionIdentity>();
                        for (var index = 0; index < 9; index++) {
                            var playerId = uuid(5_000L + index);
                            identities.add(new P7SessionIdentity(
                                    playerId,
                                    service.openSession(server, playerId).orElseThrow()));
                        }

                        var first = identities.get(0);
                        assertEligible(service, server, first, 1L);
                        var beforeRegression = service.currentSession(first)
                                .orElseThrow()
                                .admissionState();
                        var regression = service.transition(server, first, 99L, 2L)
                                .orElseThrow();
                        check(regression.outcome()
                                        == CastIntentAdmissionSemantics.Outcome.INTERNAL_SERVER_FAULT,
                                "tick regression was not fail-closed");
                        check(!regression.sequenceConsumed(),
                                "tick regression consumed sequence");
                        check(regression.expectedNext().isEmpty(),
                                "tick regression emitted a repair scalar");
                        var afterRegression = service.currentSession(first)
                                .orElseThrow()
                                .admissionState();
                        check(beforeRegression.equals(afterRegression),
                                "tick regression partially committed session state");

                        var committed = 1;
                        for (long sequence = 2L; sequence <= 8L; sequence++) {
                            assertEligible(service, server, first, sequence);
                            committed++;
                        }
                        for (var identityIndex = 1; identityIndex < 8; identityIndex++) {
                            for (long sequence = 1L; sequence <= 8L; sequence++) {
                                assertEligible(
                                        service,
                                        server,
                                        identities.get(identityIndex),
                                        sequence);
                                committed++;
                            }
                        }
                        check(committed == P7NetworkBounds.MAX_GLOBAL_WORK_UNITS_PER_TICK,
                                "test did not exercise the exact global budget");

                        var untouched = identities.get(8);
                        var beforeBusy = service.currentSession(untouched)
                                .orElseThrow()
                                .admissionState();
                        var busy = service.transition(server, untouched, 100L, 1L)
                                .orElseThrow();
                        check(busy.outcome()
                                        == CastIntentAdmissionSemantics.Outcome.SERVER_BUSY,
                                "committed transitions did not exhaust global budget");
                        check(!busy.sequenceConsumed(),
                                "global denial consumed sequence");
                        check(busy.expectedNext().orElseThrow() == 1L,
                                "global denial returned the wrong repair scalar");
                        var afterBusy = service.currentSession(untouched)
                                .orElseThrow()
                                .admissionState();
                        check(beforeBusy.equals(afterBusy),
                                "global denial partially committed session state");
                    }

                    private static void assertEligible(
                            P7ServerSessionService service,
                            MinecraftServer server,
                            P7SessionIdentity identity,
                            long sequence) {
                        var decision = service.transition(server, identity, 100L, sequence)
                                .orElseThrow();
                        check(decision.outcome()
                                        == CastIntentAdmissionSemantics.Outcome.ELIGIBLE,
                                "expected eligible transition " + sequence);
                        check(decision.sequenceConsumed(),
                                "eligible transition did not consume sequence");
                    }

                    private static P7ServerSessionService service() {
                        return new P7ServerSessionService(
                                new P7ServerAccess(), new P7ReloadAdmissionGate());
                    }

                    private static MinecraftServer serverAt(long tick) {
                        var server = new MinecraftServer();
                        server.setAuthoritativeTick(tick);
                        return server;
                    }

                    private static UUID uuid(long leastSignificantBits) {
                        return new UUID(0L, leastSignificantBits);
                    }

                    private static void expectInvariant(Runnable operation, String label) {
                        try {
                            operation.run();
                        } catch (P7SemanticInvariantException expected) {
                            return;
                        }
                        throw new AssertionError(label + " did not fail closed");
                    }

                    private static void check(boolean condition, String message) {
                        if (!condition) {
                            throw new AssertionError(message);
                        }
                    }
                }
                """);

        var productionRoot = projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/magic/network");
        var units = new ArrayList<Path>();
        for (var fileName : List.of(
                "P7ServerSessionService.java",
                "P7ServerSessionState.java",
                "P7ServerSyncState.java",
                "P7SyncSequence.java",
                "P7SessionIdentity.java",
                "ConnectionEpochState.java",
                "P7NetworkBounds.java",
                "IntentTickBudget.java",
                "CastIntentAdmissionSemantics.java",
                "IntentSequenceState.java",
                "IntentTokenBucket.java",
                "RateStrikeState.java",
                "P7SemanticInvariantException.java")) {
            var source = productionRoot.resolve(fileName);
            assertTrue(Files.isRegularFile(source), source::toString);
            units.add(source);
        }
        units.addAll(List.of(
                minecraftServerStub, serverAccessStub, reloadGateStub, harness));

        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        boolean compiled;
        try (var files = compiler.getStandardFileManager(
                diagnostics, java.util.Locale.ROOT, StandardCharsets.UTF_8)) {
            var options = List.of(
                    "--release", "21",
                    "-proc:none",
                    "-Xlint:all,-serial,-auxiliaryclass",
                    "-Werror",
                    "-classpath", outputRoot.toString(),
                    "-d", outputRoot.toString());
            compiled = Boolean.TRUE.equals(compiler.getTask(
                            null,
                            files,
                            diagnostics,
                            options,
                            null,
                            files.getJavaFileObjectsFromPaths(units))
                    .call());
        }
        assertTrue(compiled, () -> diagnostics.getDiagnostics().toString());

        try (var loader = new URLClassLoader(
                new java.net.URL[] {outputRoot.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            var behavioralHarness = Class.forName(
                    "com.yo1no.gramarye.magic.network.P7ServerSessionBehaviorHarness",
                    true,
                    loader);
            assertEquals("PASS", behavioralHarness.getMethod("run").invoke(null));
        }
    }

    private static Path write(Path root, String relativePath, String content)
            throws Exception {
        var target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return target;
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root unavailable");
    }
}
