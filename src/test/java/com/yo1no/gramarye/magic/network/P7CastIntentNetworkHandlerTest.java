package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class P7CastIntentNetworkHandlerTest {
    private static final Path HANDLER_SOURCE = projectRoot().resolve(
            "src/main/java/com/yo1no/gramarye/magic/network/"
                    + "P7CastIntentNetworkHandler.java");

    @TempDir
    Path temporary;

    @Test
    void validEpochAndPermitEnqueueOneScalarTaskThenDispatchExactlyOnce() {
        var playerId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        var epochLookup = new AtomicReference<UUID>();
        var owner = new P7PendingPermitOwner();
        var dispatched = new ArrayList<P7QueuedCastIntent>();
        var composition = composition(
                requestedPlayer -> {
                    epochLookup.set(requestedPlayer);
                    return OptionalLong.of(31L);
                }, owner, dispatched::add);
        var context = new P7RecordingPayloadContext(null);
        var intent = minimumIntent(71L);

        P7CastIntentNetworkHandler.handleAuthenticated(
                new CastIntentPayload(intent), playerId, context, composition);

        assertEquals(playerId, epochLookup.get());
        assertEquals(1, context.enqueueCalls());
        assertEquals(1, context.queuedTaskCount());
        assertEquals(0, context.playerCalls());
        assertEquals(0, context.disconnectCalls());
        assertEquals(1, owner.playerPending(playerId));
        assertEquals(1, owner.serverPending());
        assertTrue(dispatched.isEmpty());

        context.takeOnlyTask().run();

        assertEquals(1, dispatched.size());
        var queued = dispatched.getFirst();
        assertEquals(playerId, queued.authenticatedPlayerId());
        assertEquals(31L, queued.connectionEpoch());
        assertSame(intent, queued.intent());
        assertEquals(0, owner.playerPending(playerId));
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
    }

    @Test
    void absentEpochReturnsWithoutPermitEnqueueDisconnectOrDispatch() {
        var playerId = new UUID(0L, 202L);
        var epochCalls = new AtomicInteger();
        var owner = new P7PendingPermitOwner();
        var dispatchCalls = new AtomicInteger();
        var composition = composition(
                requestedPlayer -> {
                    assertEquals(playerId, requestedPlayer);
                    epochCalls.incrementAndGet();
                    return OptionalLong.empty();
                }, owner, ignored -> dispatchCalls.incrementAndGet());
        var context = new P7RecordingPayloadContext(null);

        P7CastIntentNetworkHandler.handleAuthenticated(
                new CastIntentPayload(minimumIntent(72L)),
                playerId,
                context,
                composition);

        assertEquals(1, epochCalls.get());
        assertEquals(0, context.enqueueCalls());
        assertEquals(0, context.queuedTaskCount());
        assertEquals(0, context.disconnectCalls());
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
        assertEquals(0, dispatchCalls.get());
    }

    @Test
    void busyPermitReturnsWithoutEnqueueAndLeavesCountsUnchanged() {
        var playerId = new UUID(0L, 203L);
        var owner = new P7PendingPermitOwner();
        var existingPermits = new ArrayList<P7PendingPermit>();
        for (var epoch = 1L; epoch <= 8L; epoch++) {
            existingPermits.add(owner.acquire(playerId, epoch).permit().orElseThrow());
        }
        var dispatchCalls = new AtomicInteger();
        var composition = composition(
                ignored -> OptionalLong.of(99L),
                owner,
                ignored -> dispatchCalls.incrementAndGet());
        var context = new P7RecordingPayloadContext(null);

        P7CastIntentNetworkHandler.handleAuthenticated(
                new CastIntentPayload(minimumIntent(73L)),
                playerId,
                context,
                composition);

        assertEquals(0, context.enqueueCalls());
        assertEquals(0, context.queuedTaskCount());
        assertEquals(8, owner.playerPending(playerId));
        assertEquals(8, owner.serverPending());
        assertEquals(0, dispatchCalls.get());

        existingPermits.forEach(P7PendingPermit::release);
    }

    @Test
    void invalidEpochSourceValueFailsBeforePermitOrEnqueue() {
        var playerId = new UUID(0L, 204L);
        var owner = new P7PendingPermitOwner();
        var dispatchCalls = new AtomicInteger();
        var composition = composition(
                ignored -> OptionalLong.of(0L),
                owner,
                ignored -> dispatchCalls.incrementAndGet());
        var context = new P7RecordingPayloadContext(null);

        assertThrows(P7SemanticInvariantException.class, () ->
                P7CastIntentNetworkHandler.handleAuthenticated(
                        new CastIntentPayload(minimumIntent(74L)),
                        playerId,
                        context,
                        composition));

        assertEquals(0, context.enqueueCalls());
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
        assertEquals(0, dispatchCalls.get());
    }

    @Test
    void nonServerSenderDisconnectsExactlyOnceWithGenericBoundedReason() {
        var epochCalls = new AtomicInteger();
        var owner = new P7PendingPermitOwner();
        var dispatchCalls = new AtomicInteger();
        var composition = composition(
                ignored -> {
                    epochCalls.incrementAndGet();
                    return OptionalLong.of(1L);
                }, owner, ignored -> dispatchCalls.incrementAndGet());
        var context = new P7RecordingPayloadContext(null);

        P7CastIntentNetworkHandler.handle(
                new CastIntentPayload(minimumIntent(75L)), context, composition);

        assertEquals(1, context.playerCalls());
        assertEquals(1, context.disconnectCalls());
        assertEquals("Invalid packet sender", context.disconnectReason().getString());
        assertTrue(context.disconnectReason().getString().length() <= 64);
        assertEquals(0, context.enqueueCalls());
        assertEquals(0, context.queuedTaskCount());
        assertEquals(0, epochCalls.get());
        assertEquals(0, owner.serverPending());
        assertEquals(0, dispatchCalls.get());
    }

    @Test
    void synchronousEnqueueRuntimeExceptionReleasesPermitAndPropagatesSameObject() {
        var playerId = new UUID(0L, 205L);
        var failure = new IllegalStateException("runtime enqueue failure");
        var owner = new P7PendingPermitOwner();
        var dispatchCalls = new AtomicInteger();
        var context = new P7RecordingPayloadContext(null, failure);
        var composition = composition(
                ignored -> OptionalLong.of(1L),
                owner,
                ignored -> dispatchCalls.incrementAndGet());

        var observed = assertThrows(IllegalStateException.class, () ->
                P7CastIntentNetworkHandler.handleAuthenticated(
                        new CastIntentPayload(minimumIntent(76L)),
                        playerId,
                        context,
                        composition));

        assertSame(failure, observed);
        assertEquals(1, context.enqueueCalls());
        assertEquals(0, context.queuedTaskCount());
        assertEquals(0, owner.playerPending(playerId));
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
        assertEquals(0, dispatchCalls.get());
    }

    @Test
    void synchronousEnqueueErrorReleasesPermitAndPropagatesSameObject() {
        var playerId = new UUID(0L, 206L);
        var failure = new AssertionError("error enqueue failure");
        var owner = new P7PendingPermitOwner();
        var dispatchCalls = new AtomicInteger();
        var context = new P7RecordingPayloadContext(null, failure);
        var composition = composition(
                ignored -> OptionalLong.of(1L),
                owner,
                ignored -> dispatchCalls.incrementAndGet());

        var observed = assertThrows(AssertionError.class, () ->
                P7CastIntentNetworkHandler.handleAuthenticated(
                        new CastIntentPayload(minimumIntent(77L)),
                        playerId,
                        context,
                        composition));

        assertSame(failure, observed);
        assertEquals(1, context.enqueueCalls());
        assertEquals(0, context.queuedTaskCount());
        assertEquals(0, owner.playerPending(playerId));
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
        assertEquals(0, dispatchCalls.get());
    }

    @Test
    void platformEntryCopiesServerPlayerUuidAndNetworkPathAvoidsSemanticOwners()
            throws Exception {
        var source = Files.readString(HANDLER_SOURCE);

        assertTrue(source.contains("player instanceof ServerPlayer serverPlayer"));
        assertTrue(source.contains("serverPlayer.getUUID()"));
        assertTrue(source.contains("handleAuthenticated("));
        assertTrue(source.contains("context.enqueueWork(task)"));
        assertFalse(source.contains("IntentSequenceState"));
        assertFalse(source.contains("IntentTokenBucket"));
        assertFalse(source.contains("IntentTickBudget"));
        assertFalse(source.contains("RateStrikeState"));
        assertFalse(source.contains("CastIntentAdmissionSemantics"));
        assertFalse(source.contains("SkillRuntimeService"));
        assertFalse(source.contains("P6RuntimeExecutionBridge"));
        assertFalse(source.contains("IntentAcknowledgement"));
        assertPlatformEntryBehavior();
    }

    private void assertPlatformEntryBehavior() throws Exception {
        var sourceRoot = Files.createDirectories(temporary.resolve("source"));
        var outputRoot = Files.createDirectories(temporary.resolve("classes"));
        var componentStub = write(sourceRoot,
                "net/minecraft/network/chat/Component.java", """
                package net.minecraft.network.chat;

                public final class Component {
                    private Component() {
                    }

                    public static Component literal(String ignored) {
                        return new Component();
                    }
                }
                """);
        var serverPlayerStub = write(sourceRoot,
                "net/minecraft/server/level/ServerPlayer.java", """
                package net.minecraft.server.level;

                import java.util.UUID;

                public final class ServerPlayer {
                    private final UUID playerId;
                    private int uuidCalls;

                    public ServerPlayer(UUID playerId) {
                        this.playerId = playerId;
                    }

                    public UUID getUUID() {
                        uuidCalls++;
                        return playerId;
                    }

                    public int uuidCalls() {
                        return uuidCalls;
                    }
                }
                """);
        var contextStub = write(sourceRoot,
                "net/neoforged/neoforge/network/handling/IPayloadContext.java", """
                package net.neoforged.neoforge.network.handling;

                import net.minecraft.network.chat.Component;

                public interface IPayloadContext {
                    Object player();

                    void disconnect(Component reason);

                    void enqueueWork(Runnable task);
                }
                """);
        var harness = write(sourceRoot,
                "com/yo1no/gramarye/magic/network/P7PlatformEntryHarness.java", """
                package com.yo1no.gramarye.magic.network;

                import java.util.Optional;
                import java.util.OptionalLong;
                import java.util.UUID;
                import net.minecraft.network.chat.Component;
                import net.minecraft.server.level.ServerPlayer;
                import net.neoforged.neoforge.network.handling.IPayloadContext;

                public final class P7PlatformEntryHarness {
                    private P7PlatformEntryHarness() {
                    }

                    public static String run() {
                        var playerId = UUID.fromString(
                                "00000000-0000-0000-0000-000000000299");
                        var player = new ServerPlayer(playerId);
                        var context = new RecordingContext(player);
                        var owner = new P7PendingPermitOwner();
                        var epochPlayer = new UUID[1];
                        var epochCalls = new int[1];
                        var dispatchCalls = new int[1];
                        var composition = new P7NetworkComposition(
                                requestedPlayer -> {
                                    epochPlayer[0] = requestedPlayer;
                                    epochCalls[0]++;
                                    return OptionalLong.of(47L);
                                },
                                owner,
                                ignored -> dispatchCalls[0]++);
                        var intent = new CastIntent();

                        P7CastIntentNetworkHandler.handle(
                                new CastIntentPayload(intent), context, composition);

                        check(context.playerCalls == 1, "context player lookup count");
                        check(player.uuidCalls() == 1, "ServerPlayer UUID lookup count");
                        check(playerId.equals(epochPlayer[0]), "epoch UUID capture");
                        check(epochCalls[0] == 1, "epoch lookup count");
                        check(playerId.equals(owner.playerId), "permit UUID capture");
                        check(owner.epoch == 47L, "permit epoch capture");
                        check(context.enqueueCalls == 1, "enqueue count");
                        check(context.disconnectCalls == 0, "unexpected disconnect");
                        var task = (P7ServerDispatchTask) context.task;
                        check(playerId.equals(task.queuedIntent().authenticatedPlayerId()),
                                "queued UUID capture");
                        check(task.queuedIntent().connectionEpoch() == 47L,
                                "queued epoch capture");
                        check(task.queuedIntent().intent() == intent,
                                "queued intent identity");
                        task.run();
                        check(dispatchCalls[0] == 1, "dispatch count");
                        check(owner.permit.releases == 1, "permit release count");
                        return "PASS";
                    }

                    private static void check(boolean condition, String message) {
                        if (!condition) {
                            throw new AssertionError(message);
                        }
                    }

                    private static final class RecordingContext implements IPayloadContext {
                        private final Object player;
                        private int playerCalls;
                        private int enqueueCalls;
                        private int disconnectCalls;
                        private Runnable task;

                        private RecordingContext(Object player) {
                            this.player = player;
                        }

                        @Override
                        public Object player() {
                            playerCalls++;
                            return player;
                        }

                        @Override
                        public void disconnect(Component ignored) {
                            disconnectCalls++;
                        }

                        @Override
                        public void enqueueWork(Runnable queued) {
                            enqueueCalls++;
                            task = queued;
                        }
                    }
                }

                final class CastIntent {
                }

                final class CastIntentPayload {
                    private final CastIntent intent;

                    CastIntentPayload(CastIntent intent) {
                        this.intent = intent;
                    }

                    CastIntent intent() {
                        return intent;
                    }
                }

                final class P7NetworkComposition {
                    private final P7ConnectionEpochSnapshotSource epochSource;
                    private final P7PendingPermitOwner owner;
                    private final P7ServerIntentDispatchPort dispatchPort;

                    P7NetworkComposition(
                            P7ConnectionEpochSnapshotSource epochSource,
                            P7PendingPermitOwner owner,
                            P7ServerIntentDispatchPort dispatchPort) {
                        this.epochSource = epochSource;
                        this.owner = owner;
                        this.dispatchPort = dispatchPort;
                    }

                    P7ConnectionEpochSnapshotSource connectionEpochSource() {
                        return epochSource;
                    }

                    P7PendingPermitOwner pendingPermitOwner() {
                        return owner;
                    }

                    P7ServerIntentDispatchPort serverIntentDispatchPort() {
                        return dispatchPort;
                    }
                }

                interface P7ConnectionEpochSnapshotSource {
                    OptionalLong currentEpoch(UUID playerId);
                }

                interface P7ServerIntentDispatchPort {
                    void dispatch(P7QueuedCastIntent queuedIntent);
                }

                final class P7PendingPermitOwner {
                    enum AcquireOutcome {
                        GRANTED,
                        SERVER_BUSY
                    }

                    UUID playerId;
                    long epoch;
                    P7PendingPermit permit;

                    AcquireResult acquire(UUID authenticatedPlayerId, long connectionEpoch) {
                        playerId = authenticatedPlayerId;
                        epoch = connectionEpoch;
                        permit = new P7PendingPermit();
                        return new AcquireResult(permit);
                    }

                    static final class AcquireResult {
                        private final P7PendingPermit permit;

                        AcquireResult(P7PendingPermit permit) {
                            this.permit = permit;
                        }

                        AcquireOutcome outcome() {
                            return AcquireOutcome.GRANTED;
                        }

                        Optional<P7PendingPermit> permit() {
                            return Optional.of(permit);
                        }
                    }
                }

                final class P7PendingPermit {
                    int releases;

                    void release() {
                        releases++;
                    }
                }

                final class P7QueuedCastIntent {
                    private final UUID authenticatedPlayerId;
                    private final long connectionEpoch;
                    private final CastIntent intent;

                    P7QueuedCastIntent(
                            UUID authenticatedPlayerId,
                            long connectionEpoch,
                            CastIntent intent) {
                        this.authenticatedPlayerId = authenticatedPlayerId;
                        this.connectionEpoch = connectionEpoch;
                        this.intent = intent;
                    }

                    UUID authenticatedPlayerId() {
                        return authenticatedPlayerId;
                    }

                    long connectionEpoch() {
                        return connectionEpoch;
                    }

                    CastIntent intent() {
                        return intent;
                    }
                }

                final class P7ServerDispatchTask implements Runnable {
                    private final P7QueuedCastIntent queuedIntent;
                    private final P7ServerIntentDispatchPort dispatchPort;
                    private final P7PendingPermit permit;

                    P7ServerDispatchTask(
                            P7QueuedCastIntent queuedIntent,
                            P7ServerIntentDispatchPort dispatchPort,
                            P7PendingPermit permit) {
                        this.queuedIntent = queuedIntent;
                        this.dispatchPort = dispatchPort;
                        this.permit = permit;
                    }

                    P7QueuedCastIntent queuedIntent() {
                        return queuedIntent;
                    }

                    @Override
                    public void run() {
                        try {
                            dispatchPort.dispatch(queuedIntent);
                        } finally {
                            permit.release();
                        }
                    }
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
                    HANDLER_SOURCE,
                    componentStub,
                    serverPlayerStub,
                    contextStub,
                    harness));
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
                    "com.yo1no.gramarye.magic.network.P7PlatformEntryHarness",
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

    private static CastIntent minimumIntent(long sequence) {
        return new CastIntent(sequence, 0, CastInputKind.CAST, 0, null, null);
    }

    private static P7NetworkComposition composition(
            P7ConnectionEpochSnapshotSource epochSource,
            P7PendingPermitOwner owner,
            P7ServerIntentDispatchPort serverDispatchPort) {
        return new P7NetworkComposition(
                epochSource, owner, serverDispatchPort, new NoOpClientDispatchPort());
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

    private static final class NoOpClientDispatchPort implements P7ClientMirrorDispatchPort {
        @Override
        public void onIntentAcknowledgement(IntentAcknowledgement acknowledgement) {}

        @Override
        public void onPlayerManaSnapshot(PlayerManaSnapshot snapshot) {}

        @Override
        public void onSkillCooldownSnapshot(SkillCooldownSnapshot snapshot) {}
    }
}
