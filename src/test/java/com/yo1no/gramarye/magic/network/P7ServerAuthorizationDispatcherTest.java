package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class P7ServerAuthorizationDispatcherTest {
    private static final Path SOURCE = projectRoot().resolve(
            "src/main/java/com/yo1no/gramarye/magic/network/"
                    + "P7ServerAuthorizationDispatcher.java");

    @TempDir
    Path temporary;

    @Test
    void dispatcherIsPackagePrivateFinalAndRetainsOnlyStatelessOwners() {
        var type = P7ServerAuthorizationDispatcher.class;
        var fields = Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();

        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertFalse(Modifier.isPublic(type.getModifiers()));
        assertEquals(Set.of(
                P7ServerSessionService.class,
                P7ServerAccess.class,
                P7AdvisoryTargetValidator.class,
                P7ServerIntentResultSink.class,
                P7ServerDisconnectPort.class), fields.stream()
                .map(java.lang.reflect.Field::getType)
                .collect(Collectors.toSet()));
        assertTrue(fields.stream().allMatch(field -> Modifier.isPrivate(field.getModifiers())
                && Modifier.isFinal(field.getModifiers())));
        for (var forbidden : List.of(
                "MinecraftServer",
                "ServerPlayer",
                "Entity",
                "Level",
                "SkillReference",
                "RuntimeAdmissionResult",
                "Throwable")) {
            assertTrue(fields.stream().noneMatch(field ->
                    field.getGenericType().getTypeName().contains(forbidden)), forbidden);
        }
    }

    @Test
    void sourcePreservesServerSessionReloadTransitionAndActorOrder() throws Exception {
        var source = Files.readString(SOURCE);
        assertOrdered(
                source,
                "serverAccess.currentServer()",
                "serverAccess.sameThread(server)",
                "sessionService.currentSession(identity)",
                "serverAccess.running(server)",
                "serverAccess.currentPlayer(server, identity.authenticatedPlayerId())",
                "serverAccess.currentConnectedPlayer(",
                "sessionService.admissionOpen(server)",
                "serverAccess.authoritativeTick(server)",
                "sessionService.transition(",
                "actor.isAlive()",
                "actor.isSpectator()",
                "P7ServerAuthorizationBoundary.dispatch(");
        assertEquals(1, occurrences(source, "sessionService.transition("));
        assertEquals(1, occurrences(source, "P7ServerAuthorizationBoundary.dispatch("));
        assertProductionDispatcherBehavior();
    }

    @Test
    void targetCallbackIsOneTypedCallScopedLambdaWithOnlyReviewedCaptures()
            throws Exception {
        var source = Files.readString(SOURCE);

        assertEquals(1, occurrences(
                source, "P7ServerAuthorizationBoundary.AdvisoryTargetCheck targetCheck"));
        assertEquals(1, occurrences(source, "(currentServer, currentActor) ->"));
        assertTrue(source.contains("var aimHint = intent.aimHint().orElse(null);"));
        assertTrue(source.contains("var entityHint = intent.entityHint().orElse(null);"));
        assertTrue(source.contains(
                "validator.validate(\n                        currentServer, currentActor, aimHint, entityHint)"));
        assertFalse(source.contains("IPayloadContext"));
        assertFalse(source.contains("ByteBuf"));
        assertFalse(source.contains("CompletableFuture"));
    }

    @Test
    void eighthStrikeInvalidatesPublishesThenDisconnects() throws Exception {
        var source = Files.readString(SOURCE);
        assertOrdered(
                source,
                "if (decision.disconnect())",
                "P7AdmissionDispositionMapper.fromAdmissionSemantics(",
                "resultSink.accept(result)",
                "sessionService.invalidateAfterRateLimit(server, identity)",
                "disconnectPort.disconnect(server, actor, identity)");
        assertFalse(source.contains("PacketDistributor"));
        assertFalse(source.contains("P6RuntimeExecution"));
    }

    private static void assertOrdered(String source, String... needles) {
        var previous = -1;
        for (var needle : needles) {
            var next = source.indexOf(needle, previous + 1);
            assertTrue(next > previous, needle);
            previous = next;
        }
    }

    private static long occurrences(String source, String needle) {
        return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1L;
    }

    private void assertProductionDispatcherBehavior() throws Exception {
        var sourceRoot = Files.createDirectories(temporary.resolve("dispatcher-source"));
        var outputRoot = Files.createDirectories(temporary.resolve("dispatcher-classes"));
        var serverStub = write(sourceRoot,
                "net/minecraft/server/MinecraftServer.java", """
                package net.minecraft.server;

                public final class MinecraftServer {
                }
                """);
        var playerStub = write(sourceRoot,
                "net/minecraft/server/level/ServerPlayer.java", """
                package net.minecraft.server.level;

                public final class ServerPlayer {
                    private final boolean alive;
                    private final boolean spectator;
                    private final Runnable aliveProbe;
                    private final Runnable spectatorProbe;

                    public ServerPlayer(
                            boolean alive,
                            boolean spectator,
                            Runnable aliveProbe,
                            Runnable spectatorProbe) {
                        this.alive = alive;
                        this.spectator = spectator;
                        this.aliveProbe = aliveProbe;
                        this.spectatorProbe = spectatorProbe;
                    }

                    public boolean isAlive() {
                        aliveProbe.run();
                        return alive;
                    }

                    public boolean isSpectator() {
                        spectatorProbe.run();
                        return spectator;
                    }
                }
                """);
        var harness = write(sourceRoot,
                "com/yo1no/gramarye/magic/network/P7DispatcherExecutionHarness.java", """
                package com.yo1no.gramarye.magic.network;

                import java.util.ArrayList;
                import java.util.List;
                import java.util.Optional;
                import java.util.UUID;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerPlayer;

                public final class P7DispatcherExecutionHarness {
                    private P7DispatcherExecutionHarness() {
                    }

                    public static String run() {
                        noServerStopsImmediately();
                        wrongThreadFailsClosed();
                        absentSessionStopsBeforeServerState();
                        staleSessionStopsBeforeServerState();
                        unavailableServerPublishesOneRepairAck();
                        disconnectedActorPublishesOneAck();
                        reloadGatePrecedesTransition();
                        nonEligibleAdmissionNeverCallsRoot();
                        deadAndSpectatorActorsAreRejectedAfterConsumption();
                        eligibleAdmissionCallsRootAndSinkOnce();
                        disconnectInvalidatesThenPublishesThenDisconnects();
                        return "PASS";
                    }

                    private static void noServerStopsImmediately() {
                        var fixture = new Fixture();
                        fixture.trace.serverPresent = false;

                        fixture.dispatch();

                        fixture.trace.expectEvents("server");
                        fixture.trace.expectCounts(0, 0, 0, 0, 0);
                    }

                    private static void wrongThreadFailsClosed() {
                        var fixture = new Fixture();
                        fixture.trace.sameThread = false;

                        try {
                            fixture.dispatch();
                            throw new AssertionError("wrong thread did not fail closed");
                        } catch (P7SemanticInvariantException expected) {
                            check(expected.getMessage().contains("server thread"),
                                    "wrong-thread diagnostic");
                        }

                        fixture.trace.expectEvents("server", "sameThread");
                        fixture.trace.expectCounts(0, 0, 0, 0, 0);
                    }

                    private static void absentSessionStopsBeforeServerState() {
                        var fixture = new Fixture();
                        fixture.trace.sessionPresent = false;

                        fixture.dispatch();

                        fixture.trace.expectEvents("server", "sameThread", "session");
                        fixture.trace.expectCounts(1, 0, 0, 0, 0);
                    }

                    private static void staleSessionStopsBeforeServerState() {
                        var fixture = new Fixture();
                        fixture.trace.sessionPresent = false;
                        fixture.trace.staleSession = true;

                        fixture.dispatch();

                        fixture.trace.expectEvents("server", "sameThread", "session:stale");
                        fixture.trace.expectCounts(1, 0, 0, 0, 0);
                    }

                    private static void unavailableServerPublishesOneRepairAck() {
                        var fixture = new Fixture();
                        fixture.trace.running = false;
                        fixture.trace.expectedNext = 81L;

                        fixture.dispatch();

                        fixture.trace.expectEvents(
                                "server", "sameThread", "session", "running",
                                "map:unavailable", "sink:unavailable");
                        fixture.trace.expectCounts(1, 0, 0, 1, 0);
                        check(fixture.trace.lastResult.sequence() == 73L,
                                "unavailable sequence");
                        check(fixture.trace.lastResult.expectedNext() == 81L,
                                "unavailable repair scalar");
                    }

                    private static void disconnectedActorPublishesOneAck() {
                        var fixture = new Fixture();
                        fixture.trace.connected = false;

                        fixture.dispatch();

                        fixture.trace.expectEvents(
                                "server", "sameThread", "session", "running", "player",
                                "connected", "map:disconnected", "sink:disconnected");
                        fixture.trace.expectCounts(1, 0, 0, 1, 0);
                    }

                    private static void reloadGatePrecedesTransition() {
                        var fixture = new Fixture();
                        fixture.trace.admissionOpen = false;
                        fixture.trace.expectedNext = 83L;

                        fixture.dispatch();

                        fixture.trace.expectEvents(
                                "server", "sameThread", "session", "running", "player",
                                "connected", "admission", "map:reload", "sink:reload");
                        fixture.trace.expectCounts(1, 0, 0, 1, 0);
                        check(fixture.trace.lastResult.expectedNext() == 83L,
                                "reload repair scalar");
                    }

                    private static void nonEligibleAdmissionNeverCallsRoot() {
                        var fixture = new Fixture();
                        fixture.trace.outcome = CastIntentAdmissionSemantics.Outcome.REPLAYED;

                        fixture.dispatch();

                        fixture.trace.expectEvents(
                                "server", "sameThread", "session", "running", "player",
                                "connected", "admission", "tick", "transition",
                                "map:admission", "sink:admission");
                        fixture.trace.expectCounts(1, 1, 0, 1, 0);
                    }

                    private static void deadAndSpectatorActorsAreRejectedAfterConsumption() {
                        var dead = new Fixture();
                        dead.trace.alive = false;
                        dead.dispatch();
                        dead.trace.expectEvents(
                                "server", "sameThread", "session", "running", "player",
                                "connected", "admission", "tick", "transition", "alive",
                                "map:unauthorized", "sink:unauthorized");
                        dead.trace.expectCounts(1, 1, 0, 1, 0);

                        var spectator = new Fixture();
                        spectator.trace.spectator = true;
                        spectator.dispatch();
                        spectator.trace.expectEvents(
                                "server", "sameThread", "session", "running", "player",
                                "connected", "admission", "tick", "transition", "alive",
                                "spectator", "map:unauthorized", "sink:unauthorized");
                        spectator.trace.expectCounts(1, 1, 0, 1, 0);
                    }

                    private static void eligibleAdmissionCallsRootAndSinkOnce() {
                        var fixture = new Fixture();

                        fixture.dispatch();

                        fixture.trace.expectEvents(
                                "server", "sameThread", "session", "running", "player",
                                "connected", "admission", "tick", "transition", "alive",
                                "spectator", "root", "target", "map:root", "sink:root");
                        fixture.trace.expectCounts(1, 1, 1, 1, 0);
                        check(fixture.trace.rootSlot == 4, "authoritative root slot");
                        check(fixture.trace.targetCalls == 1, "target callback count");
                    }

                    private static void disconnectInvalidatesThenPublishesThenDisconnects() {
                        var fixture = new Fixture();
                        fixture.trace.disconnectDecision = true;

                        fixture.dispatch();

                        fixture.trace.expectEvents(
                                "server", "sameThread", "session", "running", "player",
                                "connected", "admission", "tick", "transition",
                                "map:admission", "sink:disconnect", "invalidate", "disconnect");
                        fixture.trace.expectCounts(1, 1, 0, 1, 1);
                        check(fixture.trace.invalidations == 1, "invalidation count");
                    }

                    static void check(boolean condition, String message) {
                        if (!condition) {
                            throw new AssertionError(message);
                        }
                    }

                    private static final class Fixture {
                        private static final UUID PLAYER_ID = UUID.fromString(
                                "00000000-0000-0000-0000-000000000733");
                        private final Trace trace = new Trace();

                        private void dispatch() {
                            P7ServerAuthorizationBoundary.installTrace(trace);
                            var dispatcher = new P7ServerAuthorizationDispatcher(
                                    new P7ServerSessionService(trace),
                                    new P7ServerAccess(trace),
                                    new P7AdvisoryTargetValidator(trace),
                                    new P7ServerIntentResultSink(trace),
                                    new P7ServerDisconnectPort(trace));
                            dispatcher.dispatch(new P7QueuedCastIntent(
                                    PLAYER_ID, 19L, new CastIntent(73L, 4)));
                        }
                    }
                }

                final class Trace {
                    final List<String> events = new ArrayList<>();
                    boolean serverPresent = true;
                    boolean sameThread = true;
                    boolean sessionPresent = true;
                    boolean staleSession;
                    boolean running = true;
                    boolean playerPresent = true;
                    boolean connected = true;
                    boolean admissionOpen = true;
                    boolean transitionPresent = true;
                    boolean disconnectDecision;
                    boolean alive = true;
                    boolean spectator;
                    long expectedNext = 74L;
                    CastIntentAdmissionSemantics.Outcome outcome =
                            CastIntentAdmissionSemantics.Outcome.ELIGIBLE;
                    P7ServerAuthorizationBoundary.TargetDisposition targetDisposition =
                            P7ServerAuthorizationBoundary.TargetDisposition.VALID;
                    P7ServerAuthorizationBoundary.RootDisposition rootDisposition =
                            P7ServerAuthorizationBoundary.RootDisposition.ACCEPTED;
                    int sessionLookups;
                    int transitions;
                    int rootCalls;
                    int targetCalls;
                    int sinkCalls;
                    int disconnects;
                    int invalidations;
                    int rootSlot = -1;
                    P7ServerIntentResult lastResult;

                    void add(String event) {
                        events.add(event);
                    }

                    void expectEvents(String... expected) {
                        P7DispatcherExecutionHarness.check(
                                events.equals(List.of(expected)),
                                "events expected=" + List.of(expected) + " actual=" + events);
                    }

                    void expectCounts(
                            int expectedSessions,
                            int expectedTransitions,
                            int expectedRoots,
                            int expectedSinks,
                            int expectedDisconnects) {
                        P7DispatcherExecutionHarness.check(
                                sessionLookups == expectedSessions,
                                "session lookup count " + sessionLookups);
                        P7DispatcherExecutionHarness.check(
                                transitions == expectedTransitions,
                                "transition count " + transitions);
                        P7DispatcherExecutionHarness.check(
                                rootCalls == expectedRoots,
                                "root count " + rootCalls);
                        P7DispatcherExecutionHarness.check(
                                sinkCalls == expectedSinks,
                                "sink count " + sinkCalls);
                        P7DispatcherExecutionHarness.check(
                                disconnects == expectedDisconnects,
                                "disconnect count " + disconnects);
                    }
                }

                final class P7ServerSessionService {
                    private final Trace trace;

                    P7ServerSessionService(Trace trace) {
                        this.trace = trace;
                    }

                    Optional<P7ServerSessionState> currentSession(P7SessionIdentity identity) {
                        trace.sessionLookups++;
                        trace.add(trace.staleSession ? "session:stale" : "session");
                        P7DispatcherExecutionHarness.check(
                                identity.authenticatedPlayerId().equals(
                                        UUID.fromString(
                                                "00000000-0000-0000-0000-000000000733")),
                                "authenticated identity");
                        P7DispatcherExecutionHarness.check(
                                identity.connectionEpoch() == 19L, "connection epoch");
                        return trace.sessionPresent
                                ? Optional.of(new P7ServerSessionState(trace.expectedNext))
                                : Optional.empty();
                    }

                    boolean admissionOpen(MinecraftServer server) {
                        trace.add("admission");
                        return trace.admissionOpen;
                    }

                    Optional<CastIntentAdmissionSemantics.Decision> transition(
                            MinecraftServer server,
                            P7SessionIdentity identity,
                            long tick,
                            long sequence) {
                        trace.transitions++;
                        trace.add("transition");
                        P7DispatcherExecutionHarness.check(tick == 211L, "authoritative tick");
                        P7DispatcherExecutionHarness.check(sequence == 73L, "intent sequence");
                        return trace.transitionPresent
                                ? Optional.of(new CastIntentAdmissionSemantics.Decision(
                                        trace.disconnectDecision, trace.outcome))
                                : Optional.empty();
                    }

                    void invalidateAfterRateLimit(
                            MinecraftServer server, P7SessionIdentity identity) {
                        trace.invalidations++;
                        trace.add("invalidate");
                    }
                }

                final class P7ServerAccess {
                    private final Trace trace;
                    private final MinecraftServer server = new MinecraftServer();
                    private final ServerPlayer actor;

                    P7ServerAccess(Trace trace) {
                        this.trace = trace;
                        actor = new ServerPlayer(
                                trace.alive,
                                trace.spectator,
                                () -> trace.add("alive"),
                                () -> trace.add("spectator"));
                    }

                    MinecraftServer currentServer() {
                        trace.add("server");
                        return trace.serverPresent ? server : null;
                    }

                    boolean sameThread(MinecraftServer currentServer) {
                        trace.add("sameThread");
                        return trace.sameThread;
                    }

                    boolean running(MinecraftServer currentServer) {
                        trace.add("running");
                        return trace.running;
                    }

                    ServerPlayer currentPlayer(MinecraftServer currentServer, UUID playerId) {
                        trace.add("player");
                        return trace.playerPresent ? actor : null;
                    }

                    boolean currentConnectedPlayer(
                            MinecraftServer currentServer,
                            ServerPlayer currentActor,
                            UUID playerId) {
                        trace.add("connected");
                        return trace.connected;
                    }

                    long authoritativeTick(MinecraftServer currentServer) {
                        trace.add("tick");
                        return 211L;
                    }
                }

                final class P7AdvisoryTargetValidator {
                    private final Trace trace;

                    P7AdvisoryTargetValidator(Trace trace) {
                        this.trace = trace;
                    }

                    P7ServerAuthorizationBoundary.TargetDisposition validate(
                            MinecraftServer server,
                            ServerPlayer actor,
                            AimHint aimHint,
                            EntityHint entityHint) {
                        trace.targetCalls++;
                        trace.add("target");
                        return trace.targetDisposition;
                    }
                }

                final class P7ServerIntentResultSink {
                    private final Trace trace;

                    P7ServerIntentResultSink(Trace trace) {
                        this.trace = trace;
                    }

                    void accept(P7ServerIntentResult result) {
                        trace.sinkCalls++;
                        trace.lastResult = result;
                        trace.add("sink:" + result.kind());
                    }
                }

                final class P7ServerDisconnectPort {
                    private final Trace trace;

                    P7ServerDisconnectPort(Trace trace) {
                        this.trace = trace;
                    }

                    void disconnect(MinecraftServer server, ServerPlayer actor,
                            P7SessionIdentity identity) {
                        trace.disconnects++;
                        trace.add("disconnect");
                    }
                }

                final class P7AdmissionDispositionMapper {
                    private P7AdmissionDispositionMapper() {
                    }

                    static P7ServerIntentResult serverUnavailable(
                            P7SessionIdentity identity, long sequence, long expectedNext) {
                        var trace = P7ServerAuthorizationBoundary.trace();
                        trace.add("map:unavailable");
                        return new P7ServerIntentResult(
                                "unavailable", sequence, expectedNext);
                    }

                    static P7ServerIntentResult disconnected(
                            P7SessionIdentity identity, long sequence, boolean consumed) {
                        var trace = P7ServerAuthorizationBoundary.trace();
                        trace.add("map:disconnected");
                        return new P7ServerIntentResult("disconnected", sequence, -1L);
                    }

                    static P7ServerIntentResult reloadInProgress(
                            P7SessionIdentity identity, long sequence, long expectedNext) {
                        var trace = P7ServerAuthorizationBoundary.trace();
                        trace.add("map:reload");
                        return new P7ServerIntentResult("reload", sequence, expectedNext);
                    }

                    static P7ServerIntentResult fromAdmissionSemantics(
                            P7SessionIdentity identity,
                            long sequence,
                            CastIntentAdmissionSemantics.Decision decision) {
                        var trace = P7ServerAuthorizationBoundary.trace();
                        trace.add("map:admission");
                        var kind = decision.disconnect() ? "disconnect" : "admission";
                        return new P7ServerIntentResult(kind, sequence, -1L);
                    }

                    static P7ServerIntentResult unauthorizedAfterConsumption(
                            P7SessionIdentity identity, long sequence) {
                        var trace = P7ServerAuthorizationBoundary.trace();
                        trace.add("map:unauthorized");
                        return new P7ServerIntentResult("unauthorized", sequence, -1L);
                    }

                    static P7ServerIntentResult fromRootDisposition(
                            P7SessionIdentity identity,
                            long sequence,
                            P7ServerAuthorizationBoundary.RootDisposition disposition) {
                        var trace = P7ServerAuthorizationBoundary.trace();
                        trace.add("map:root");
                        return new P7ServerIntentResult("root", sequence, -1L);
                    }
                }

                final class P7ServerAuthorizationBoundary {
                    enum TargetDisposition {
                        VALID,
                        INVALID_TARGET
                    }

                    enum RootDisposition {
                        ACCEPTED
                    }

                    interface AdvisoryTargetCheck {
                        TargetDisposition validate(MinecraftServer server, ServerPlayer actor);
                    }

                    private static Trace currentTrace;

                    private P7ServerAuthorizationBoundary() {
                    }

                    static void installTrace(Trace trace) {
                        currentTrace = trace;
                    }

                    static Trace trace() {
                        return currentTrace;
                    }

                    static RootDisposition dispatch(
                            MinecraftServer server,
                            ServerPlayer actor,
                            int slot,
                            AdvisoryTargetCheck targetCheck) {
                        var trace = trace();
                        trace.rootCalls++;
                        trace.rootSlot = slot;
                        trace.add("root");
                        targetCheck.validate(server, actor);
                        return trace.rootDisposition;
                    }
                }

                final class CastIntentAdmissionSemantics {
                    enum Outcome {
                        ELIGIBLE,
                        REPLAYED
                    }

                    record Decision(boolean disconnect, Outcome outcome) {
                    }
                }

                record P7SessionIdentity(UUID authenticatedPlayerId, long connectionEpoch) {
                }

                final class P7ServerSessionState {
                    private final long expectedNext;

                    P7ServerSessionState(long expectedNext) {
                        this.expectedNext = expectedNext;
                    }

                    AdmissionState admissionState() {
                        return new AdmissionState(expectedNext);
                    }
                }

                record AdmissionState(long expectedNext) {
                    SequenceState sequenceState() {
                        return new SequenceState(expectedNext);
                    }
                }

                record SequenceState(long expectedNext) {
                }

                record P7ServerIntentResult(String kind, long sequence, long expectedNext) {
                }

                final class P7QueuedCastIntent {
                    private final UUID playerId;
                    private final long epoch;
                    private final CastIntent intent;

                    P7QueuedCastIntent(UUID playerId, long epoch, CastIntent intent) {
                        this.playerId = playerId;
                        this.epoch = epoch;
                        this.intent = intent;
                    }

                    UUID authenticatedPlayerId() {
                        return playerId;
                    }

                    long connectionEpoch() {
                        return epoch;
                    }

                    CastIntent intent() {
                        return intent;
                    }
                }

                final class CastIntent {
                    private final long sequence;
                    private final int slot;

                    CastIntent(long sequence, int slot) {
                        this.sequence = sequence;
                        this.slot = slot;
                    }

                    long sequence() {
                        return sequence;
                    }

                    int slot() {
                        return slot;
                    }

                    Optional<AimHint> aimHint() {
                        return Optional.empty();
                    }

                    Optional<EntityHint> entityHint() {
                        return Optional.empty();
                    }
                }

                final class AimHint {
                }

                final class EntityHint {
                }

                final class P7SemanticInvariantException extends RuntimeException {
                    private static final long serialVersionUID = 1L;

                    P7SemanticInvariantException(String message) {
                        super(message);
                    }
                }
                """);

        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        boolean success;
        try (var files = compiler.getStandardFileManager(
                diagnostics, java.util.Locale.ROOT, StandardCharsets.UTF_8)) {
            var units = files.getJavaFileObjectsFromPaths(List.of(
                    SOURCE, serverStub, playerStub, harness));
            var options = List.of(
                    "--release", "21",
                    "-proc:none",
                    "-Xlint:all,-auxiliaryclass",
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
            var behavioralHarness = Class.forName(
                    "com.yo1no.gramarye.magic.network.P7DispatcherExecutionHarness",
                    true,
                    loader);
            assertEquals("PASS", behavioralHarness.getMethod("run").invoke(null));
        }
    }

    private static Path write(Path root, String relativePath, String content)
            throws IOException {
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
