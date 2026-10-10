package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.P11D3ServerHarness;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Excluded observation of the actual owners. A held continuation is engineering scheduling,
 * not a fabricated context, session, permit, task, result, or ordinary network timing claim. */
public final class P11D3Observation {
    public enum Hold { NONE, SOURCE, TASK, RESULT }
    private static P7ServerSessionService sessions;
    private static P7PendingPermitOwner permits;
    private static MinecraftServer server;
    private static UUID selectedPlayer;
    private static Connection selectedConnection;
    private static Hold requested = Hold.NONE;
    private static Runnable held;
    private static Object candidateTask;
    private static P7SessionIdentity heldIdentity;
    private static int heldCount, releasedCount, networkEntries, taskEntries, results, ackSubmissions;
    private static long lastResultSequence;
    private static String lastResult = "NOT_OBSERVED";
    private static Hold releasing = Hold.NONE;
    private static Connection releaseSource;
    private static int lateCaptureReturns;
    private static String lateCaptureOutcome = "NOT_OBSERVED";
    private static final long[][] COST = new long[5][4];
    private static final long[] REJECTIONS = new long[8];
    private static boolean costUnavailable;
    private P11D3Observation() { }

    public static synchronized void owner(Object actual, Object actualPermits) {
        if (!P11D3ServerHarness.selected()) return;
        require(sessions == null && actual instanceof P7ServerSessionService
                && actualPermits instanceof P7PendingPermitOwner, "SOLE_ACTUAL_OWNER");
        sessions = (P7ServerSessionService) actual;
        permits = (P7PendingPermitOwner) actualPermits;
    }

    public static synchronized void begin(MinecraftServer actual) {
        require(actual.isSameThread() && sessions != null && permits != null
                && held == null && candidateTask == null && sessions.isCurrentServer(actual), "ACTUAL_STARTED_OWNER");
        server = actual; selectedPlayer = null; selectedConnection = null; requested = Hold.NONE;
        heldIdentity = null; heldCount = releasedCount = networkEntries = taskEntries = results = ackSubmissions = 0;
        lastResult = "NOT_OBSERVED"; lastResultSequence = 0;
        releasing = Hold.NONE; releaseSource = null; lateCaptureReturns = 0; lateCaptureOutcome = "NOT_OBSERVED";
    }

    public static synchronized void arm(ServerPlayer actor, Hold kind) {
        require(server != null && server.isSameThread() && requested == Hold.NONE && held == null
                && candidateTask == null && kind != Hold.NONE, "ONE_AUTHENTIC_HOLDER");
        var identity = sessions.currentIdentity(server, actor.getUUID()).orElseThrow();
        require(sessions.matchesCurrentActor(server, identity, actor), "CURRENT_ACTOR_AT_ARM");
        selectedPlayer = actor.getUUID(); selectedConnection = actor.connection.getConnection(); requested = kind;
    }

    public static synchronized void beginNextServer(MinecraftServer actual) {
        require(actual.isSameThread() && server != actual && held != null && requested == Hold.TASK
                && candidateTask == null && heldCount == 1 && releasedCount == 0
                && sessions.isCurrentServer(actual) && sessions.currentSession(heldIdentity).isEmpty()
                && permits.serverPending() == 0, "ACTUAL_NEXT_SERVER_WITH_ONLY_OLD_TASK_HOLDER");
        server = actual;
    }

    /** The wrapper receives the original handler's actual NETWORK arguments. */
    public static void network(Object value, IPayloadContext context, Runnable original) {
        if (!P11D3ServerHarness.selected()) { original.run(); return; }
        require(value instanceof CastIntentPayload && context.player() instanceof ServerPlayer, "ACTUAL_NETWORK_INPUT");
        var actor = (ServerPlayer) context.player();
        synchronized (P11D3Observation.class) {
            networkEntries++;
            if (requested == Hold.SOURCE && actor.getUUID().equals(selectedPlayer)
                    && context.connection() == selectedConnection) {
                require(held == null && candidateTask == null && heldCount == 0, "SOURCE_ONCE");
                heldIdentity = sessions.captureAuthenticatedSession(selectedPlayer, selectedConnection)
                        .identity().orElseThrow();
                held = original; heldCount++; return;
            }
        }
        original.run();
    }

    public static synchronized void taskConstructed(Object task, Object queued) {
        if (!P11D3ServerHarness.selected() || requested != Hold.TASK) return;
        require(queued instanceof P7QueuedCastIntent, "ACTUAL_QUEUED_BODY");
        var identity = ((P7QueuedCastIntent) queued).sessionIdentity();
        if (!identity.authenticatedPlayerId().equals(selectedPlayer)) return;
        require(candidateTask == null && held == null && heldCount == 0
                && sessions.isCurrentCapture(identity, selectedConnection), "TASK_CAPTURE_ONCE");
        candidateTask = task; heldIdentity = identity;
    }

    public static void task(Object task, Runnable original) {
        if (!P11D3ServerHarness.selected()) { original.run(); return; }
        synchronized (P11D3Observation.class) {
            taskEntries++;
            if (task == candidateTask) {
                require(server.isSameThread() && requested == Hold.TASK && held == null, "TASK_ACTUAL_SERVER_ENTRY");
                candidateTask = null; held = original; heldCount++; return;
            }
        }
        original.run();
    }

    public static void result(Object value, Runnable original) {
        if (!P11D3ServerHarness.selected()) { original.run(); return; }
        require(value instanceof P7ServerIntentResult && server.isSameThread(), "ACTUAL_RESULT_THREAD");
        var result = (P7ServerIntentResult) value;
        synchronized (P11D3Observation.class) {
            results++; lastResultSequence = result.receivedSequence();
            lastResult = result.failureReason().map(Enum::name).orElse("ACCEPTED");
            if (requested == Hold.RESULT && result.sessionIdentity().authenticatedPlayerId().equals(selectedPlayer)) {
                require(held == null && heldCount == 0 && result.p5AdmissionAccepted()
                        && sessions.isCurrentCapture(result.sessionIdentity(), selectedConnection), "RESULT_ACTUAL_ACCEPTED_ONCE");
                heldIdentity = result.sessionIdentity(); held = original; heldCount++; return;
            }
        }
        original.run();
    }

    public static synchronized void submitted(ServerPlayer actor, CustomPacketPayload value) {
        if (!P11D3ServerHarness.selected() || !(value instanceof IntentAckPayload)) return;
        require(server.isSameThread() && actor.getServer() == server, "ACK_ACTUAL_SUBMISSION_THREAD");
        ackSubmissions++;
    }

    /** No monitor surrounds original code; custody is removed before the sole invocation. */
    public static void release() {
        Runnable original;
        synchronized (P11D3Observation.class) {
            require(server.isSameThread() && held != null && candidateTask == null && heldCount == 1
                    && releasedCount == 0 && sessions.currentSession(heldIdentity).isEmpty(), "OLD_LIFETIME_RETIRED_BEFORE_RELEASE");
            original = held; held = null; releasing = requested; releaseSource = selectedConnection;
            selectedConnection = null; selectedPlayer = null;
            requested = Hold.NONE; releasedCount++;
        }
        try { original.run(); }
        finally { synchronized (P11D3Observation.class) { releasing = Hold.NONE; releaseSource = null; } }
    }

    public static synchronized boolean held() { return held != null && heldCount == 1; }
    /** Only the original NETWORK handler callsite counts, never the observer's arm-time read. */
    public static synchronized void handlerCaptured(UUID id, Connection connection, Object result) {
        if (!P11D3ServerHarness.selected() || releasing != Hold.SOURCE || !server.isSameThread()) return;
        require(connection == releaseSource && id.equals(heldIdentity.authenticatedPlayerId())
                && result instanceof P7ConnectionEpochSnapshotSource.CaptureResult && ++lateCaptureReturns == 1,
                "EXACT_LATE_ORIGINAL_CAPTURE_RETURN");
        lateCaptureOutcome = ((P7ConnectionEpochSnapshotSource.CaptureResult) result).outcome().name();
    }
    public static synchronized boolean lateSourceRejected() { return lateCaptureReturns == 1 && lateCaptureOutcome.equals("CONNECTION_MISMATCH"); }
    public static synchronized int pending(ServerPlayer actor) { return permits.playerPending(actor.getUUID()); }
    public static synchronized int ackCount() { return ackSubmissions; }
    public static synchronized int resultCount() { return results; }
    public static synchronized String lastResult() { return lastResult; }
    public static synchronized long lastResultSequence() { return lastResultSequence; }
    public static synchronized int pending() { return permits.serverPending(); }
    public static synchronized int activeSessions() { return sessions.activeSessionCount(); }

    public static final class Snapshot {
        private final P7SessionIdentity identity;
        private final CastIntentAdmissionSemantics.SessionState admission;
        private final int playerPending, serverPending, acks;
        private Snapshot(P7SessionIdentity id, CastIntentAdmissionSemantics.SessionState state, int pp, int sp, int count) {
            identity = id; admission = state; playerPending = pp; serverPending = sp; acks = count;
        }
        public long epoch() { return identity.connectionEpoch(); }
        public long generation() { return identity.serverGeneration(); }
        public long expectedNext() { return admission.sequenceState().expectedNext().orElseThrow(); }
        public Map<String, Object> facts() {
            return Map.of("epoch", epoch(), "serverGeneration", generation(), "expectedNext", expectedNext(),
                    "playerPending", playerPending, "serverPending", serverPending, "ackSubmissions", acks);
        }
    }

    public static synchronized Snapshot snapshot(ServerPlayer actor) {
        require(server != null && server.isSameThread(), "SNAPSHOT_SERVER_THREAD");
        var identity = sessions.currentIdentity(server, actor.getUUID()).orElseThrow();
        require(sessions.matchesCurrentActor(server, identity, actor), "SNAPSHOT_CURRENT_ACTOR");
        return new Snapshot(identity, sessions.currentSession(identity).orElseThrow().admissionState(),
                permits.playerPending(actor.getUUID()), permits.serverPending(), ackSubmissions);
    }
    public static synchronized boolean initialDelivered(ServerPlayer actor) {
        if (server == null || !server.isSameThread()) return false;
        return sessions.currentIdentity(server, actor.getUUID()).flatMap(sessions::currentSession)
                .map(state -> !state.syncState().initialPending() && !state.syncState().sending()).orElse(false);
    }
    public static boolean unchanged(Snapshot before, ServerPlayer actor) {
        var after = snapshot(actor);
        return before.identity.equals(after.identity) && before.admission.equals(after.admission)
                && before.playerPending == after.playerPending && before.serverPending == after.serverPending
                && before.acks == after.acks;
    }
    public static boolean sameIdentity(Snapshot before, ServerPlayer actor) { return before.identity.equals(snapshot(actor).identity); }
    public static boolean sameAdmission(Snapshot before, ServerPlayer actor) {
        var after = snapshot(actor);
        return before.identity.equals(after.identity) && before.admission.equals(after.admission);
    }
    public static synchronized Map<String, Object> counters() {
        var facts = new LinkedHashMap<String, Object>();
        facts.put("networkEntries", networkEntries); facts.put("actualTaskEntries", taskEntries);
        facts.put("resultEntries", results); facts.put("ackSubmissions", ackSubmissions);
        facts.put("authenticContinuationsHeld", heldCount); facts.put("authenticContinuationsReleased", releasedCount);
        facts.put("holderEmpty", held == null && candidateTask == null); facts.put("serverPending", permits.serverPending());
        facts.put("activeSessions", sessions.activeSessionCount()); facts.put("lastResult", lastResult);
        facts.put("lastResultSequence", lastResultSequence); facts.put("lateOriginalCaptureReturns", lateCaptureReturns);
        facts.put("lateOriginalCaptureOutcome", lateCaptureOutcome); return facts;
    }
    /** Fixed counts and elapsed nanoseconds only; never a capacity/latency threshold. */
    public static synchronized long costStart(Object owner) {
        return P11D3ServerHarness.selected() && (owner == sessions || owner == permits) ? System.nanoTime() : -1;
    }
    public static synchronized void costEnd(int kind, long start, boolean normal, Object result) {
        if (start == -1) return;
        try {
            long elapsed = System.nanoTime() - start;
            if (kind < 0 || kind >= COST.length || elapsed < 0) { costUnavailable = true; return; }
            COST[kind][0] = add(COST[kind][0], 1); COST[kind][1] = add(COST[kind][1], elapsed);
            COST[kind][2] = Math.max(COST[kind][2], elapsed); if (!normal) COST[kind][3] = add(COST[kind][3], 1);
            if (result instanceof P7ConnectionEpochSnapshotSource.CaptureResult capture)
                REJECTIONS[capture.outcome().ordinal()] = add(REJECTIONS[capture.outcome().ordinal()], 1);
            if (result instanceof P7PendingPermitOwner.AcquireResult acquired)
                REJECTIONS[4 + acquired.outcome().ordinal()] = add(REJECTIONS[4 + acquired.outcome().ordinal()], 1);
        } catch (RuntimeException | Error diagnosticFailure) {
            // Fixed observer failures are unavailable evidence, never a replacement for an escaping original primary.
            costUnavailable = true;
        }
    }
    public static synchronized Map<String, Object> costs() {
        String[] operations = {"BINDING_PUBLICATION", "AUTHENTICATED_CAPTURE", "CAPTURE_RECHECK", "PERMIT_ACQUIRE", "SERVER_STOP"};
        var facts = new LinkedHashMap<String, Object>();
        for (int i = 0; i < operations.length; i++) facts.put(operations[i], Map.of("calls", COST[i][0],
                "totalNanos", COST[i][1], "maximumNanos", COST[i][2], "exceptionalExits", COST[i][3]));
        var outcomes = new LinkedHashMap<String, Object>();
        for (var value : P7ConnectionEpochSnapshotSource.CaptureOutcome.values()) outcomes.put("CAPTURE_" + value.name(), REJECTIONS[value.ordinal()]);
        for (var value : P7PendingPermitOwner.AcquireOutcome.values()) outcomes.put("ACQUIRE_" + value.name(), REJECTIONS[4 + value.ordinal()]);
        return Map.of("status", costUnavailable ? "UNAVAILABLE" : "BOUNDED_ACTUAL_OWNER_SCALAR_COSTS", "operations", facts,
                "outcomes", outcomes, "capacityClaim", false, "performanceThreshold", false, "contentionQualification", false,
                "includesReadOnlyObserverCalls", true);
    }
    private static long add(long a, long b) { return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b; }
    /** Abort drops only fixture references. Original logout/stop remains the sole permit cleanup owner. */
    public static synchronized void clear() {
        held = null; candidateTask = null; heldIdentity = null; selectedConnection = null; selectedPlayer = null;
        requested = Hold.NONE; releasing = Hold.NONE; releaseSource = null; server = null;
    }
    private static void require(boolean value, String code) { P11D3ServerHarness.require(value, "OBSERVATION_" + code); }
}
