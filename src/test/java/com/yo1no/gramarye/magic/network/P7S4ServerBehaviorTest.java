package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executes the actual S4 owners against typed, synchronous platform boundaries. */
final class P7S4ServerBehaviorTest {
    @TempDir
    static Path temporary;
    private static URLClassLoader loader;
    private static Class<?> harness;

    @BeforeAll
    static void compileActualOwners() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var sourceRoot = temporary.resolve("source");
        var outputRoot = Files.createDirectories(temporary.resolve("classes"));
        var units = new ArrayList<Path>();
        var production = projectRoot().resolve("src/main/java/com/yo1no/gramarye/magic/network");
        for (var file : List.of("P7ServerSessionService.java", "P7ServerSessionState.java",
                "P7ServerSyncState.java", "P7SyncSequence.java", "P7SessionIdentity.java",
                "ConnectionEpochState.java", "P7NetworkBounds.java", "IntentTickBudget.java",
                "CastIntentAdmissionSemantics.java", "IntentSequenceState.java", "IntentTokenBucket.java",
                "RateStrikeState.java", "P7SemanticInvariantException.java", "P7IntentFailureReason.java",
                "IntentAcknowledgement.java", "P7ServerIntentResult.java", "PlayerManaSnapshot.java",
                "SkillCooldownSnapshot.java", "CooldownSnapshotEntry.java", "PendingPermitAccounting.java",
                "P7PendingPermitOwner.java", "P7PendingPermit.java", "P7Diagnostics.java",
                "P7ReloadAdmissionGate.java", "P7AuthoritativeSyncService.java",
                "P7ServerLifecycleCoordinator.java", "P7ServerAuthorizationDispatcher.java",
                "P7AdmissionDispositionMapper.java", "P7QueuedCastIntent.java", "CastIntent.java",
                "CastInputKind.java", "AimHint.java", "EntityHint.java", "P7ServerIntentResultSink.java",
                "P7ServerDisconnectPort.java")) {
            units.add(production.resolve(file));
        }
        units.add(write(sourceRoot, "net/minecraft/server/MinecraftServer.java", """
                package net.minecraft.server;
                public final class MinecraftServer {
                    public boolean sameThread = true;
                    public boolean running = true;
                    public long tick;
                    public boolean isSameThread() { return sameThread; }
                }
                """));
        units.add(write(sourceRoot, "net/minecraft/network/protocol/common/custom/CustomPacketPayload.java", """
                package net.minecraft.network.protocol.common.custom;
                public interface CustomPacketPayload {}
                """));
        units.add(write(sourceRoot, "net/minecraft/server/level/ServerPlayer.java", """
                package net.minecraft.server.level;
                import java.util.UUID;
                import java.util.ArrayList;
                import java.util.List;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
                public final class ServerPlayer {
                    private final UUID id;
                    public final MinecraftServer server;
                    public final Connection connection = new Connection();
                    public boolean connected = true;
                    public int disconnects;
                    public RuntimeException disconnectFailure;
                    public ServerPlayer(MinecraftServer server, UUID id) { this.server = server; this.id = id; }
                    public UUID getUUID() { return id; }
                    public boolean isAlive() { return true; }
                    public boolean isSpectator() { return false; }
                    public static final class Connection {
                        public final List<CustomPacketPayload> sent = new ArrayList<>();
                        public void send(CustomPacketPayload payload) { sent.add(payload); }
                    }
                }
                """));
        units.add(write(sourceRoot, "com/yo1no/gramarye/magic/network/P7ServerAuthorizationBoundary.java", """
                package com.yo1no.gramarye.magic.network;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerPlayer;
                final class P7ServerAuthorizationBoundary {
                    enum AdmissionDisposition {
                        ACCEPTED, UNKNOWN_SKILL, UNAUTHORIZED_INTENT, INVALID_TARGET,
                        TARGET_UNAVAILABLE, P5_ADMISSION_REJECTED, P5_UNAVAILABLE, INTERNAL_SERVER_FAULT
                    }
                    enum TargetDisposition { VALID, INVALID_TARGET, TARGET_UNAVAILABLE }
                    interface AdvisoryTargetCheck {
                        TargetDisposition validate(MinecraftServer server, ServerPlayer actor);
                    }
                    static AdmissionDisposition dispatch(MinecraftServer server, ServerPlayer actor,
                            int slot, AdvisoryTargetCheck target) {
                        return AdmissionDisposition.UNKNOWN_SKILL;
                    }
                }
                """));
        units.add(write(sourceRoot, "com/yo1no/gramarye/magic/network/P7AdvisoryTargetValidator.java", """
                package com.yo1no.gramarye.magic.network;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerPlayer;
                final class P7AdvisoryTargetValidator {
                    P7ServerAuthorizationBoundary.TargetDisposition validate(MinecraftServer server,
                            ServerPlayer actor, AimHint aim, EntityHint entity) {
                        return P7ServerAuthorizationBoundary.TargetDisposition.VALID;
                    }
                }
                """));
        units.add(write(sourceRoot, "com/yo1no/gramarye/magic/network/P7ServerAccess.java", """
                package com.yo1no.gramarye.magic.network;
                import java.util.HashMap;
                import java.util.Map;
                import java.util.UUID;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerPlayer;
                final class P7ServerAccess {
                    final Map<UUID, ServerPlayer> players = new HashMap<>();
                    MinecraftServer server;
                    MinecraftServer currentServer() { return server; }
                    boolean sameThread(MinecraftServer server) { return server.sameThread; }
                    boolean running(MinecraftServer server) { return server.running; }
                    long authoritativeTick(MinecraftServer server) { return server.tick; }
                    ServerPlayer currentPlayer(MinecraftServer server, UUID id) { return players.get(id); }
                    boolean currentConnectedPlayer(MinecraftServer server, ServerPlayer actor, UUID id) {
                        return actor.server == server && actor.connected && actor.getUUID().equals(id)
                                && players.get(id) == actor;
                    }
                    void disconnectCurrent(MinecraftServer server, ServerPlayer actor) {
                        if (currentConnectedPlayer(server, actor, actor.getUUID())) {
                            actor.disconnects++;
                            if (actor.disconnectFailure != null) { throw actor.disconnectFailure; }
                            actor.connected = false;
                        }
                    }
                }
                """));
        units.add(write(sourceRoot, "com/mojang/logging/LogUtils.java", """
                package com.mojang.logging;
                public final class LogUtils {
                    public static int logs;
                    private static final Logger LOGGER = new Logger();
                    private LogUtils() {}
                    public static Logger getLogger() { return LOGGER; }
                    public static final class Logger {
                        public void debug(String message, Object... values) { logs++; }
                    }
                }
                """));
        units.add(write(sourceRoot, "com/yo1no/gramarye/magic/network/IntentAckPayload.java", """
                package com.yo1no.gramarye.magic.network;
                import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
                record IntentAckPayload(IntentAcknowledgement acknowledgement) implements CustomPacketPayload {}
                """));
        units.add(write(sourceRoot, "com/yo1no/gramarye/magic/network/PlayerManaSyncPayload.java", """
                package com.yo1no.gramarye.magic.network;
                import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
                record PlayerManaSyncPayload(PlayerManaSnapshot snapshot) implements CustomPacketPayload {}
                """));
        units.add(write(sourceRoot, "com/yo1no/gramarye/magic/network/SkillCooldownSyncPayload.java", """
                package com.yo1no.gramarye.magic.network;
                import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
                record SkillCooldownSyncPayload(SkillCooldownSnapshot snapshot) implements CustomPacketPayload {}
                """));
        units.add(write(sourceRoot, "com/yo1no/gramarye/magic/network/S4BehaviorHarness.java", harnessSource()));
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        boolean compiled;
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            compiled = Boolean.TRUE.equals(compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-Xlint:all,-serial,-auxiliaryclass", "-Werror",
                            "-classpath", outputRoot.toString(), "-d", outputRoot.toString()),
                    null, manager.getJavaFileObjectsFromPaths(units)).call());
        }
        assertTrue(compiled, () -> diagnostics.getDiagnostics().toString());
        loader = new URLClassLoader(new java.net.URL[] {outputRoot.toUri().toURL()},
                ClassLoader.getPlatformClassLoader());
        harness = loader.loadClass("com.yo1no.gramarye.magic.network.S4BehaviorHarness");
    }

    @AfterAll
    static void closeLoader() throws Exception {
        if (loader != null) {
            loader.close();
        }
    }

    @Test
    void ackCandidateCurrentEpochAndAbsentRecipientDecideAtMostOneSubmission() throws Exception {
        run("ack");
    }

    @Test
    void ackFailurePreservesPrimaryAndAttemptsEveryCleanupIncludingSecondaryFault() throws Exception {
        run("ack-failure");
    }

    @Test
    void fullSnapshotsSubmitManaFirstAndRespectTheTwentyTickCadence() throws Exception {
        run("sync");
    }

    @Test
    void eachPartialFailurePreservesOnlyPreviouslySubmittedFamilyState() throws Exception {
        run("partial");
    }

    @Test
    void exactMaximumAndPreexhaustedSequencesNeverWrapOrRetry() throws Exception {
        run("exhaustion");
    }

    @Test
    void syncAndIngressShareTheExactSixtyFourUnitOwner() throws Exception {
        run("budget");
    }

    @Test
    void duplicateReloadAndNewerCloseRequestUseOneBoundedSixteenPerTickQueue() throws Exception {
        run("reload");
    }

    @Test
    void reconnectCapacityAndStopUseExactSessionEpochAndFiveHundredSeventySixBound() throws Exception {
        run("lifecycle");
    }

    @Test
    void diagnosticsSaturateAndThrottleWithoutRetainingFaultObjects() throws Exception {
        run("diagnostics");
    }

    @Test
    void eighthRateStrikeSubmitsBeforeInvalidationAndFailureCannotDisconnectTwice() throws Exception {
        run("rate-terminal");
    }

    private static void run(String scenario) throws Exception {
        assertEquals("PASS", harness.getMethod("run", String.class).invoke(null, scenario));
    }

    private static Path write(Path root, String relative, String source) throws Exception {
        var path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, source, StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath(); candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root unavailable");
    }

    private static String harnessSource() {
        return """
                package com.yo1no.gramarye.magic.network;
                import java.util.ArrayList;
                import java.util.List;
                import java.util.Optional;
                import java.util.UUID;
                import com.mojang.logging.LogUtils;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerPlayer;
                import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

                public final class S4BehaviorHarness {
                    private S4BehaviorHarness() {}
                    public static String run(String scenario) throws Exception {
                        switch (scenario) {
                            case "ack" -> ack();
                            case "ack-failure" -> ackFailure();
                            case "sync" -> sync();
                            case "partial" -> partial();
                            case "exhaustion" -> exhaustion();
                            case "budget" -> budget();
                            case "reload" -> reload();
                            case "lifecycle" -> lifecycle();
                            case "diagnostics" -> diagnostics();
                            case "rate-terminal" -> rateTerminal();
                            default -> throw new AssertionError("unknown case");
                        }
                        return "PASS";
                    }

                    private static void ack() {
                        var f = new Fixture();
                        var actor = f.player(1);
                        var id = f.open(actor);
                        var sent = new ArrayList<CustomPacketPayload>();
                        var sync = f.sync((a, payload) -> sent.add(payload));
                        sync.accept(result(id, false));
                        check(sent.isEmpty(), "absent ACK must not submit");
                        sync.accept(result(id, true));
                        check(sent.size() == 1 && sent.get(0) instanceof IntentAckPayload,
                                "current ACK must submit exactly once");
                        for (var disposition : IntentAcknowledgement.Disposition.values()) {
                            var reason = switch (disposition) {
                                case ACCEPTED -> Optional.<P7IntentFailureReason>empty();
                                case REJECTED -> Optional.of(P7IntentFailureReason.UNKNOWN_SKILL);
                                case DUPLICATE -> Optional.of(P7IntentFailureReason.DUPLICATE_SEQUENCE);
                                case STALE -> Optional.of(P7IntentFailureReason.STALE_SEQUENCE);
                                case SEQUENCE_GAP -> Optional.of(P7IntentFailureReason.SEQUENCE_GAP);
                                case SEQUENCE_EXHAUSTED -> Optional.of(P7IntentFailureReason.SEQUENCE_EXHAUSTED);
                                case RATE_LIMITED -> Optional.of(P7IntentFailureReason.RATE_LIMITED);
                                case SERVER_BUSY -> Optional.of(P7IntentFailureReason.SERVER_BUSY);
                                case UNAVAILABLE -> Optional.of(P7IntentFailureReason.P5_UNAVAILABLE);
                            };
                            var accepted = disposition == IntentAcknowledgement.Disposition.ACCEPTED;
                            var consumed = accepted || disposition == IntentAcknowledgement.Disposition.REJECTED;
                            Long expected = switch (disposition) {
                                case DUPLICATE, STALE, SEQUENCE_GAP -> 2L;
                                case ACCEPTED, REJECTED, SEQUENCE_EXHAUSTED,
                                        RATE_LIMITED, SERVER_BUSY, UNAVAILABLE -> null;
                            };
                            var flags = (consumed ? IntentAcknowledgement.SEQUENCE_CONSUMED : 0)
                                    | (expected != null ? IntentAcknowledgement.HAS_EXPECTED_NEXT : 0);
                            var candidate = new IntentAcknowledgement(1, disposition, flags, expected);
                            var beforeSend = sent.size();
                            sync.accept(new P7ServerIntentResult(id, 1, reason, Optional.of(candidate),
                                    consumed, false, accepted, true, accepted));
                            check(sent.size() == beforeSend + 1
                                            && ((IntentAckPayload) sent.get(beforeSend)).acknowledgement() == candidate,
                                    "ordinary disposition must submit the exact candidate once");
                        }
                        var submitted = sent.size();
                        var before = f.sessions.currentSession(id).orElseThrow();
                        check(before.admissionState().sequenceState().expectedNext().orElseThrow() == 1,
                                "ACK must not mutate admission sequence");
                        sync.accept(result(new P7SessionIdentity(actor.getUUID(), id.connectionEpoch() + 1), true));
                        actor.connected = false;
                        sync.accept(result(id, true));
                        check(sent.size() == submitted, "stale/disconnected recipients must not submit");
                    }

                    private static void ackFailure() {
                        for (var error : new boolean[] {false, true}) {
                            var f = new Fixture();
                            var actor = f.player(1);
                            var id = f.open(actor);
                            f.life.requestSync(f.server, actor.getUUID());
                            var permit = f.permits.acquire(actor.getUUID(), id.connectionEpoch()).permit().orElseThrow();
                            Throwable primary = error ? new AssertionError("primary") : new IllegalStateException("primary");
                            var secondary = new IllegalArgumentException("disconnect-secondary");
                            actor.disconnectFailure = secondary;
                            var calls = new int[1];
                            var sync = f.sync((a, payload) -> { calls[0]++; throwExact(primary); });
                            try {
                                sync.accept(result(id, true));
                                throw new AssertionError("ACK failure disappeared");
                            } catch (RuntimeException | Error observed) {
                                check(observed == primary, "ACK primary identity replaced");
                                check(observed.getSuppressed().length == 1
                                                && observed.getSuppressed()[0] == secondary,
                                        "secondary cleanup must remain suppressed on primary");
                            }
                            check(calls[0] == 1 && actor.disconnects == 1, "send/disconnect duplicated");
                            check(f.sessions.currentSession(id).isEmpty(), "failed session survived");
                            check(f.life.queuedCount() == 0 && f.permits.serverPending() == 0 && permit.released(),
                                    "failure did not clear queue/permit state");
                            check(f.diagnostics.snapshot().stream().anyMatch(record ->
                                            record.reason() == P7IntentFailureReason.INTERNAL_SERVER_FAULT),
                                    "diagnostic skipped after secondary cleanup fault");
                            permit.releaseAfterTask();
                        }
                    }

                    private static void sync() {
                        var f = new Fixture();
                        var actor = f.player(1);
                        var id = f.open(actor);
                        var sent = new ArrayList<CustomPacketPayload>();
                        var sync = f.sync((a, payload) -> sent.add(payload));
                        check(sync.fullSync(f.server, id, 0), "initial login is immediately due");
                        check(sent.size() == 2 && sent.get(0) instanceof PlayerManaSyncPayload
                                        && sent.get(1) instanceof SkillCooldownSyncPayload,
                                "full set order must be mana then cooldown");
                        var mana = ((PlayerManaSyncPayload) sent.get(0)).snapshot();
                        var cooldown = ((SkillCooldownSyncPayload) sent.get(1)).snapshot();
                        check(mana.syncSequence() == 1 && mana.balance() == 731
                                        && cooldown.syncSequence() == 1 && cooldown.entries().isEmpty(),
                                "initial full snapshot differs from authoritative observations");
                        check(!sync.fullSync(f.server, id, 19) && sent.size() == 2, "early full sync escaped cadence");
                        f.server.tick = 20;
                        check(sync.fullSync(f.server, id, 20) && sent.size() == 4, "twentieth tick not due");
                        check(f.state(id).mana().value() == 3 && f.state(id).cooldown().value() == 3
                                        && f.state(id).lastResyncTick() == 20,
                                "normal local submission did not advance own-family state");
                        var unavailable = new P7AuthoritativeSyncService(f.sessions, f.access, f.life,
                                a -> -1, (a, payload) -> sent.add(payload));
                        f.server.tick = 40;
                        unavailable.fullSync(f.server, id, 40);
                        var unavailableMana = ((PlayerManaSyncPayload) sent.get(4)).snapshot();
                        check(unavailableMana.availability() == PlayerManaSnapshot.Availability.UNAVAILABLE
                                        && unavailableMana.balance() == 0,
                                "unavailable observation must use zero wire balance with unavailable flag");
                    }

                    private static void partial() {
                        for (var failAt : new int[] {1, 2}) {
                            for (var error : new boolean[] {false, true}) {
                                var f = new Fixture();
                                var actor = f.player(1);
                                var id = f.open(actor);
                                var calls = new int[1];
                                Throwable primary = error ? new AssertionError("partial") : new IllegalStateException("partial");
                                var sync = f.sync((a, payload) -> {
                                    calls[0]++;
                                    var atCall = f.state(id);
                                    check(atCall.mana().value() == (calls[0] == 1 ? 1 : 2)
                                                    && atCall.cooldown().value() == 1,
                                            "family was reserved before own send or prior mana was rolled back");
                                    if (calls[0] == failAt) { throwExact(primary); }
                                });
                                try {
                                    sync.fullSync(f.server, id, 0);
                                    throw new AssertionError("partial fault disappeared");
                                } catch (RuntimeException | Error observed) {
                                    check(observed == primary, "partial fault identity replaced");
                                }
                                check(calls[0] == failAt && actor.disconnects == 1
                                                && f.sessions.currentSession(id).isEmpty(),
                                        "partial failure retried/compensated or preserved the session");
                            }
                        }
                    }

                    private static void exhaustion() {
                        var familyValues = new long[][] {
                            {Long.MAX_VALUE, 1}, {1, Long.MAX_VALUE}, {Long.MAX_VALUE, Long.MAX_VALUE}
                        };
                        for (var values : familyValues) {
                            var f = new Fixture();
                            var actor = f.player(1);
                            var id = f.open(actor);
                            f.sessions.updateSync(f.server, id, new P7ServerSyncState(
                                    new P7SyncSequence(values[0], false),
                                    new P7SyncSequence(values[1], false), 0, true));
                            var calls = new int[1];
                            var sync = f.sync((a, payload) -> {
                                calls[0]++;
                                var boundary = f.state(id);
                                check(actor.disconnects == 0, "exhaustion disconnected before full-set completion");
                                check(boundary.cooldown().value() == values[1]
                                                && !boundary.cooldown().exhausted(),
                                        "cooldown was advanced or exhausted before its own normal return");
                                if (calls[0] == 1) {
                                    check(payload instanceof PlayerManaSyncPayload mana
                                                    && mana.snapshot().syncSequence() == values[0]
                                                    && boundary.mana().value() == values[0]
                                                    && !boundary.mana().exhausted(),
                                            "mana maximum was reserved before its own normal return");
                                }
                                if (calls[0] == 2) {
                                    check(payload instanceof SkillCooldownSyncPayload cooldown
                                                    && cooldown.snapshot().syncSequence() == values[1]
                                                    && boundary.mana().value()
                                                            == (values[0] == Long.MAX_VALUE ? Long.MAX_VALUE : values[0] + 1)
                                                    && boundary.mana().exhausted() == (values[0] == Long.MAX_VALUE),
                                            "only normally submitted mana must advance or exhaust before cooldown");
                                }
                            });
                            check(sync.fullSync(f.server, id, 0), "exhaustion terminal must complete");
                            check(calls[0] == 2 && actor.disconnects == 1,
                                    "single-family or dual-family maximum skipped or repeated a permitted send");
                            check(f.sessions.currentSession(id).isEmpty(), "exhausted session survived");
                            check(sync.fullSync(f.server, id, 1) && calls[0] == 2 && actor.disconnects == 1,
                                    "a later sync request revived or retried an exhausted session");
                        }
                        for (var exhausted : new boolean[][] {{true, false}, {false, true}, {true, true}}) {
                            var f = new Fixture();
                            var actor = f.player(1);
                            var id = f.open(actor);
                            f.sessions.updateSync(f.server, id, new P7ServerSyncState(
                                    new P7SyncSequence(exhausted[0] ? Long.MAX_VALUE : 1, exhausted[0]),
                                    new P7SyncSequence(exhausted[1] ? Long.MAX_VALUE : 1, exhausted[1]), 0, true));
                            var observations = new int[1];
                            var calls = new int[1];
                            var sync = new P7AuthoritativeSyncService(f.sessions, f.access, f.life,
                                    a -> { observations[0]++; return 731; }, (a, payload) -> calls[0]++);
                            check(sync.fullSync(f.server, id, 0) && observations[0] == 0 && calls[0] == 0,
                                    "either pre-exhausted family must reject before observation or either send");
                            check(actor.disconnects == 1 && f.sessions.currentSession(id).isEmpty(),
                                    "pre-exhausted session did not terminate exactly once");
                        }
                        for (var values : familyValues) {
                            for (var failAt : new int[] {1, 2}) {
                                for (var error : new boolean[] {false, true}) {
                                    var f = new Fixture();
                                    var actor = f.player(1);
                                    var id = f.open(actor);
                                    f.sessions.updateSync(f.server, id, new P7ServerSyncState(
                                            new P7SyncSequence(values[0], false),
                                            new P7SyncSequence(values[1], false), 0, true));
                                    var calls = new int[1];
                                    var failureBoundary = new P7ServerSyncState[1];
                                    Throwable primary = error ? new AssertionError("maximum-send")
                                            : new IllegalStateException("maximum-send");
                                    var sync = f.sync((a, payload) -> {
                                        calls[0]++;
                                        if (calls[0] == failAt) {
                                            failureBoundary[0] = f.state(id);
                                            throwExact(primary);
                                        }
                                    });
                                    try {
                                        sync.fullSync(f.server, id, 0);
                                        throw new AssertionError("maximum send failure disappeared");
                                    } catch (RuntimeException | Error observed) {
                                        check(observed == primary, "maximum send failure primary identity replaced");
                                    }
                                    var boundary = failureBoundary[0];
                                    check(boundary != null && boundary.mana().value()
                                                    == (failAt == 1 || values[0] == Long.MAX_VALUE
                                                            ? values[0] : values[0] + 1)
                                                    && boundary.mana().exhausted()
                                                            == (failAt == 2 && values[0] == Long.MAX_VALUE)
                                                    && boundary.cooldown().value() == values[1]
                                                    && !boundary.cooldown().exhausted(),
                                            "failure advanced its own family or rolled back previously exhausted mana");
                                    check(calls[0] == failAt && actor.disconnects == 1
                                                    && f.sessions.currentSession(id).isEmpty(),
                                            "maximum failure retried, skipped cleanup or disconnected twice");
                                    check(sync.fullSync(f.server, id, 1) && calls[0] == failAt
                                                    && actor.disconnects == 1,
                                            "failed maximum session was revived or retried");
                                }
                            }
                        }
                    }

                    private static void budget() {
                        var f = new Fixture();
                        var actor = f.player(1);
                        var id = f.open(actor);
                        for (int count = 0; count < 63; count++) {
                            check(f.sessions.consumeSyncWork(f.server, 0), "early budget denial");
                        }
                        var calls = new int[1];
                        var sync = f.sync((a, payload) -> calls[0]++);
                        check(sync.fullSync(f.server, id, 0) && calls[0] == 2,
                                "one full set must cost the final single work unit");
                        var admission = f.sessions.transition(f.server, id, 0, 1).orElseThrow();
                        check(admission.outcome() == CastIntentAdmissionSemantics.Outcome.SERVER_BUSY
                                        && !admission.sequenceConsumed(),
                                "sync and ingress do not share one global budget");
                        var other = f.player(2);
                        var otherId = f.open(other);
                        check(!sync.fullSync(f.server, otherId, 0) && f.state(otherId).mana().value() == 1,
                                "budget denial must not submit or consume family sequence");
                        f.server.tick = 1;
                        check(sync.fullSync(f.server, otherId, 1), "new tick must replenish shared budget");
                    }

                    private static void reload() {
                        var joining = new Fixture();
                        joining.gate.requestReloadClose();
                        joining.life.onLoginReady(joining.server, joining.player(1));
                        joining.life.tick(joining.server);
                        check(joining.sentCount() == 2 && joining.life.queuedCount() == 0
                                        && !joining.gate.isOpen(joining.server),
                                "joining-player initial sync consumed a pending reload request");
                        joining.life.onReloadComplete(joining.server);
                        check(joining.life.queuedCount() == 1
                                        && !joining.gate.isOpen(joining.server),
                                "completion must reconcile the joining player while admission is closed");
                        joining.server.tick = 20;
                        joining.life.tick(joining.server);
                        check(joining.sentCount() == 4 && joining.life.queuedCount() == 0
                                        && joining.gate.isOpen(joining.server),
                                "joining-player reconciliation failed to drain and reopen after cadence");
                        var f = new Fixture();
                        for (int n = 1; n <= 33; n++) {
                            f.life.onLoginReady(f.server, f.player(n));
                        }
                        f.gate.requestReloadClose();
                        f.gate.requestReloadClose();
                        check(!f.sessions.admissionOpen(f.server), "start did not close admission");
                        f.life.onReloadComplete(f.server);
                        check(f.life.queuedCount() == 33, "duplicate start duplicated queue entries");
                        f.life.tick(f.server);
                        check(f.life.queuedCount() == 17 && f.sentCount() == 32,
                                "one tick must process at most sixteen full sets");
                        f.life.tick(f.server);
                        check(f.sentCount() == 32, "same-tick second callback escaped process bound");
                        f.gate.requestReloadClose();
                        f.server.tick = 1;
                        f.life.tick(f.server);
                        f.server.tick = 2;
                        f.life.tick(f.server);
                        check(f.life.queuedCount() == 0 && !f.gate.isOpen(f.server),
                                "old drain erased a newer pending close request");
                        f.life.onReloadComplete(f.server);
                        check(f.life.queuedCount() == 33, "later completion failed to merge fresh UUID snapshot");
                        f.server.tick = 22;
                        f.life.tick(f.server);
                        f.server.tick = 23;
                        f.life.tick(f.server);
                        f.server.tick = 24;
                        f.life.tick(f.server);
                        check(f.life.queuedCount() == 0 && f.gate.isOpen(f.server),
                                "combined reconciliation did not reopen after final drain");
                        f.life.onReloadComplete(f.server);
                        check(f.diagnostics.snapshot().stream().anyMatch(record -> record.playerId() == null
                                        && record.reason() == P7IntentFailureReason.RELOAD_IN_PROGRESS),
                                "missing start must be bounded diagnostic and normal reconciliation");
                    }

                    private static void lifecycle() {
                        var f = new Fixture();
                        var actor = f.player(1);
                        f.life.onLoginReady(f.server, actor);
                        var first = f.identity(actor);
                        f.life.onLoginReady(f.server, actor);
                        check(f.sessions.activeSessionCount() == 1 && f.life.queuedCount() == 1
                                        && f.identity(actor).equals(first),
                                "ALREADY_ACTIVE duplicated/reset the session or initial sync");
                        var permit = f.permits.acquire(actor.getUUID(), first.connectionEpoch()).permit().orElseThrow();
                        f.life.onDisconnect(f.server, actor);
                        check(f.sessions.activeSessionCount() == 0 && f.life.queuedCount() == 0 && permit.released(),
                                "logout did not clear bounded session/permit/queue state");
                        var replacement = f.player(1);
                        f.life.onLoginReady(f.server, replacement);
                        var second = f.identity(replacement);
                        check(second.connectionEpoch() > first.connectionEpoch()
                                        && f.state(second).mana().value() == 1,
                                "reconnect did not allocate a fresh epoch and sync state");
                        f.life.onDisconnect(f.server, actor);
                        f.life.terminate(f.server, actor, first);
                        check(f.sessions.currentSession(second).isPresent() && replacement.disconnects == 0
                                        && f.life.queuedCount() == 1,
                                "stale lifecycle removed a newer reconnect session");
                        for (int n = 2; n <= 256; n++) { f.life.onLoginReady(f.server, f.player(n)); }
                        var overflow = f.player(257);
                        f.life.onLoginReady(f.server, overflow);
                        check(f.sessions.activeSessionCount() == 256 && f.life.queuedCount() == 256
                                        && overflow.disconnects == 1,
                                "session-cap rejection left a partial session");
                        for (int n = 1; n <= 8; n++) {
                            var id = f.identity(f.access.players.get(uuid(n)));
                            for (int p = 0; p < 8; p++) {
                                f.permits.acquire(id.authenticatedPlayerId(), id.connectionEpoch()).permit().orElseThrow();
                            }
                        }
                        check(f.life.stop(f.server) == 576, "stop must count exact 256 + 256 + 64 records");
                        check(f.life.stop(f.server) == 0 && f.sessions.activeSessionCount() == 0
                                        && f.life.queuedCount() == 0 && f.permits.serverPending() == 0
                                        && f.diagnostics.snapshot().isEmpty(),
                                "stop left server-lifetime state or repeated cleanup");
                        f.life.start(f.server);
                        var restarted = f.player(300);
                        f.life.onLoginReady(f.server, restarted);
                        check(f.identity(restarted).connectionEpoch() == 1,
                                "new server lifetime must reset checked epoch allocator");
                    }

                    private static void diagnostics() throws Exception {
                        var diagnostic = new P7Diagnostics();
                        LogUtils.logs = 0;
                        for (int tick = 0; tick < 100; tick++) {
                            diagnostic.record(uuid(1), tick, P7IntentFailureReason.SERVER_BUSY);
                        }
                        check(LogUtils.logs == 1, "pair log throttle emitted before hundred ticks");
                        diagnostic.record(uuid(1), 100, P7IntentFailureReason.SERVER_BUSY);
                        check(LogUtils.logs == 2, "hundredth tick must permit next coarse log");
                        for (int n = 0; n < 300; n++) {
                            diagnostic.record(uuid(n + 2), 200, P7IntentFailureReason.RATE_LIMITED);
                        }
                        check(diagnostic.snapshot().size() == 256, "diagnostic ring escaped capacity");
                        var containerField = P7Diagnostics.class.getDeclaredField("container");
                        containerField.setAccessible(true);
                        var container = containerField.get(diagnostic);
                        var ringField = container.getClass().getDeclaredField("ring");
                        ringField.setAccessible(true);
                        var ring = (P7Diagnostics.Observation[]) ringField.get(container);
                        ring[0] = new P7Diagnostics.Observation(uuid(999), 300,
                                P7IntentFailureReason.INTERNAL_SERVER_FAULT, Long.MAX_VALUE);
                        diagnostic.record(uuid(999), 301, P7IntentFailureReason.INTERNAL_SERVER_FAULT);
                        check(diagnostic.snapshot().stream().anyMatch(record -> record.playerId().equals(uuid(999))
                                        && record.tick() == 301 && record.count() == Long.MAX_VALUE),
                                "diagnostic count wrapped instead of saturating");
                        diagnostic.discard();
                        check(diagnostic.snapshot().isEmpty(), "stop did not discard diagnostic container");
                    }

                    private static void rateTerminal() {
                        for (int failureMode = 0; failureMode < 3; failureMode++) {
                            var f = new Fixture();
                            var actor = f.player(1);
                            var id = f.open(actor);
                            Throwable primary = failureMode == 1 ? new IllegalStateException("rate-ACK")
                                    : failureMode == 2 ? new AssertionError("rate-ACK") : null;
                            var sent = new int[1];
                            var terminalSends = new int[1];
                            var sync = f.sync((a, payload) -> {
                                check(f.sessions.currentSession(id).isPresent(),
                                        "rate ACK attempted after session invalidation");
                                sent[0]++;
                                var acknowledgement = ((IntentAckPayload) payload).acknowledgement();
                                if (acknowledgement.disposition() == IntentAcknowledgement.Disposition.RATE_LIMITED
                                        && f.sessions.currentSession(id).orElseThrow()
                                                .admissionState().rateStrikeState().strikeCount() == 8) {
                                    terminalSends[0]++;
                                    check(actor.disconnects == 0 && f.permits.serverPending() == 1,
                                            "rate terminal cleanup ran before best-effort ACK");
                                    if (primary != null) { throwExact(primary); }
                                }
                            });
                            var dispatcher = new P7ServerAuthorizationDispatcher(f.sessions, f.access,
                                    new P7AdvisoryTargetValidator(), sync::accept, f.life::finishInvalidated);
                            for (long sequence = 1; sequence <= 8; sequence++) {
                                dispatcher.dispatch(new P7QueuedCastIntent(actor.getUUID(), id.connectionEpoch(),
                                        new CastIntent(sequence, 0, CastInputKind.CAST, 0, null, null)));
                            }
                            var rejected = new P7QueuedCastIntent(actor.getUUID(), id.connectionEpoch(),
                                    new CastIntent(9, 0, CastInputKind.CAST, 0, null, null));
                            for (int strike = 0; strike < 7; strike++) { dispatcher.dispatch(rejected); }
                            check(sent[0] == 15 && f.sessions.currentSession(id).isPresent(),
                                    "seven strikes should preserve the current session");
                            var permit = f.permits.acquire(actor.getUUID(), id.connectionEpoch()).permit().orElseThrow();
                            f.life.requestSync(f.server, actor.getUUID());
                            try {
                                dispatcher.dispatch(rejected);
                                check(primary == null, "terminal ACK fault disappeared");
                            } catch (RuntimeException | Error observed) {
                                check(observed == primary, "outer rate branch replaced primary failure");
                            }
                            check(sent[0] == 16 && terminalSends[0] == 1 && actor.disconnects == 1,
                                    "rate terminal repeated ACK or disconnect after failure");
                            check(f.sessions.currentSession(id).isEmpty() && f.life.queuedCount() == 0
                                            && permit.released() && f.permits.serverPending() == 0,
                                    "rate terminal did not clear exact session-owned state");
                            permit.releaseAfterTask();
                        }
                    }

                    private static P7ServerIntentResult result(P7SessionIdentity id, boolean ack) {
                        return new P7ServerIntentResult(id, 1,
                                Optional.of(ack ? P7IntentFailureReason.UNKNOWN_SKILL : P7IntentFailureReason.INTERNAL_SERVER_FAULT),
                                ack ? Optional.of(new IntentAcknowledgement(1, IntentAcknowledgement.Disposition.REJECTED,
                                        IntentAcknowledgement.SEQUENCE_CONSUMED, null)) : Optional.empty(),
                                true, false, false, true, false);
                    }

                    private static final class Fixture {
                        final MinecraftServer server = new MinecraftServer();
                        final P7ServerAccess access = new P7ServerAccess();
                        final P7ReloadAdmissionGate gate = new P7ReloadAdmissionGate();
                        final P7ServerSessionService sessions = new P7ServerSessionService(access, gate);
                        final P7PendingPermitOwner permits = new P7PendingPermitOwner();
                        final P7Diagnostics diagnostics = new P7Diagnostics();
                        final P7ServerLifecycleCoordinator life = new P7ServerLifecycleCoordinator(
                                sessions, access, permits, gate, diagnostics, actor -> 731);
                        Fixture() { access.server = server; }
                        ServerPlayer player(long n) {
                            var actor = new ServerPlayer(server, uuid(n));
                            access.players.put(actor.getUUID(), actor);
                            return actor;
                        }
                        P7SessionIdentity open(ServerPlayer actor) {
                            check(sessions.open(server, actor.getUUID()) == P7ServerSessionService.OpenResult.OPENED,
                                    "test session open failed");
                            return identity(actor);
                        }
                        P7SessionIdentity identity(ServerPlayer actor) {
                            return new P7SessionIdentity(actor.getUUID(), sessions.currentEpoch(actor.getUUID()).orElseThrow());
                        }
                        P7ServerSyncState state(P7SessionIdentity id) { return sessions.currentSession(id).orElseThrow().syncState(); }
                        P7AuthoritativeSyncService sync(P7AuthoritativeSyncService.Transport transport) {
                            return new P7AuthoritativeSyncService(sessions, access, life, actor -> 731, transport);
                        }
                        int sentCount() { return access.players.values().stream().mapToInt(actor -> actor.connection.sent.size()).sum(); }
                    }
                    private static UUID uuid(long n) { return new UUID(0, n); }
                    private static void throwExact(Throwable failure) {
                        if (failure instanceof RuntimeException runtime) { throw runtime; }
                        throw (Error) failure;
                    }
                    private static void check(boolean condition, String message) {
                        if (!condition) { throw new AssertionError(message); }
                    }
                }
                """;
    }
}
