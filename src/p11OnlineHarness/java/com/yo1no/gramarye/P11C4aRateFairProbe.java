package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import static com.yo1no.gramarye.P11C4aNativeObservations.Event.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.level.GameRules;

/** Separate excluded C6 episode. All conclusions require actual original observations. */
public final class P11C4aRateFairProbe {
    public interface BucketView { long rf$units(); }
    private static volatile Run active;
    private static final ThreadLocal<Ingress> INGRESS = new ThreadLocal<>();
    private static final ThreadLocal<OfferCall> OFFER = new ThreadLocal<>();
    private P11C4aRateFairProbe() { }

    static boolean ready(ServerPlayer a, ServerPlayer b) { return materialReady(a) && materialReady(b); }
    private static boolean materialReady(ServerPlayer actor) {
        if (actor == null) { return false; }
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.nativeRecipient(actor);
        return body != null && body.actor == actor && source.canCopy(body) && body.account.metadata == null
                && body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0;
    }

    static boolean tick(MinecraftServer server, ServerPlayer a, ServerPlayer b, Path output) throws IOException {
        if (active == null) {
            require(server.isSameThread() && server.isDedicatedServer() && a != b
                    && !a.isFakePlayer() && !b.isFakePlayer() && !a.getUUID().equals(b.getUUID()), "RF_REAL_PAIR");
            active = new Run(server, a, b, output);
            server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
            server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(false, server);
        }
        var run = active;
        require(server == run.server && server.isSameThread() && ++run.ticks <= 2400
                && run.failure == null, "RF_OWNER_DEADLINE_OR_OBSERVER");
        if (run.stage < 9) { unchanged(run); }
        switch (run.stage) {
            case 0 -> {
                if (run.limits == null) { return false; }
                cue(run, "arm"); run.stage++;
            }
            case 1 -> {
                if (!receipt(run, "a", "rf-armed") || !receipt(run, "b", "rf-armed")) { return false; }
                arm(run.a); run.a.kill(); cue(run, "a", "first"); run.stage++;
            }
            case 2 -> {
                if (!receipt(run, "a", "rf-first") || !P11C4aC6EarlyGateProbe.returned()) { return false; }
                gateReport(run, "a-first"); arm(run.b); run.b.kill(); cue(run, "b", "first"); run.stage++;
            }
            case 3 -> {
                if (!receipt(run, "b", "rf-first") || !P11C4aC6EarlyGateProbe.returned()) { return false; }
                gateReport(run, "b-first"); run.fair = true; cue(run, "fair"); run.stage++;
            }
            case 4 -> {
                if (!receipt(run, "a", "rf-fair") || !receipt(run, "b", "rf-fair")) { return false; }
                run.fair = false;
                write(run, "rf-fairness.json", "ACTUAL_WINDOW_OBSERVATIONS_QUALIFICATION_EXPLICIT");
                arm(run.a); cue(run, "a", "consume"); run.stage++;
            }
            case 5 -> {
                if (!receipt(run, "a", "rf-consume") || !P11C4aC6EarlyGateProbe.returned()) { return false; }
                gateReport(run, "a-consume");
                synchronized (run) {
                    require(run.lastTry[0] != null && run.lastTry[0].requestSeq() < Long.MAX_VALUE, "RF_ACTUAL_TRY_BASELINE");
                    run.rateN = run.lastTry[0].requestSeq() + 1;
                }
                // If the real wire round trip refills TRY, the next original gate still
                // refuses under an actual native reward. A late window cannot spawn B.
                arm(run.a); run.rate = true; cue(run, "a", "wire"); run.stage++;
            }
            case 6 -> {
                if (!receipt(run, "a", "rf-wire")) { return false; }
                var clientFile = output.getParent().resolve("client-a/rf-wire.json");
                require(java.nio.file.Files.size(clientFile) <= 65_536, "RF_CLIENT_RECEIPT_BOUND");
                var client = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(clientFile)).getAsJsonObject();
                run.clientRateReceipt = client.get("rateSequence").getAsLong() == run.rateN
                        && client.get("rawRateReceiptObserved").getAsBoolean();
                if (P11C4aC6EarlyGateProbe.returned()) { gateReport(run, "late-wire-fallback"); }
                else { P11C4aC6EarlyGateProbe.abort(); }
                run.rate = false;
                write(run, "rf-result.json", "BOUNDED_EPISODE_FINISHED_QUALIFIED_ROWS_EXPLICIT_OTHERWISE_UNPROVEN");
                server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(run.keepInventory, server);
                server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(run.immediate, server);
                run.stage = 9; cue(run, "b", "finish");
            }
            case 9 -> {
                if (!run.leftB) { return false; }
                require(run.ca.isConnected() && !run.cb.isConnected(), "RF_CLEANUP_PEER_ORDER");
                cue(run, "a", "finish"); run.stage++;
            }
            case 10 -> { return run.leftA && run.leftB; }
            default -> throw new IllegalStateException("RF_BAD_STAGE");
        }
        return false;
    }

    static boolean started() { return active != null; }
    static void logout(ServerPlayer actor) {
        var run = active;
        if (run == null || actor.getServer() != run.server) { return; }
        if (run.stage == 9 && actor == run.b && !run.leftB) { run.leftB = true; }
        else if (run.stage == 10 && actor == run.a && run.leftB && !run.leftA) { run.leftA = true; }
        else { fail(run, "RF_UNEXPECTED_LOGOUT"); }
    }

    public static void limits(MinecraftServer server, Object supplied) {
        var run = active;
        if (run == null || run.server != server || run.limits != null) { return; }
        var value = (P11StartupLimits) supplied;
        if (value.maxUuids() < 2 || value.maxWaitingConnections() != 1 || value.mainQuantaPerTick() != 1
                || value.tryBurst() != 1 || value.tryRefillPerSecond() != 1
                || value.statusBurst() != 4 || value.statusRefillPerSecond() != 4
                || value.admissionWaitMillis() != 45_000 || value.maxSealedSnapshots() != 1 || value.maxSealedBytes() != 1) {
            fail(run, "RF_STARTUP_LIMITS"); return;
        }
        run.limits = Map.of("T", value.maxUuids(), "K", 1, "q", 1, "tryBurst", 1, "tryRefill", 1,
                "statusBurst", 4, "statusRefill", 4, "admissionWaitMillis", 45_000,
                "sealedSnapshots", 1, "sealedBytes", 1);
    }

    public static Object entering(Connection connection) {
        var run = active;
        if (run == null) { return null; }
        int role = connection == run.ca ? 0 : connection == run.cb ? 1 : -1;
        if (role < 0) { return null; }
        var call = new Ingress(run, role, INGRESS.get()); INGRESS.set(call); return call;
    }
    public static void entered(Object value) {
        if (value instanceof Ingress call) { if (call.previous == null) { INGRESS.remove(); } else { INGRESS.set(call.previous); } }
    }
    public static Object offering(Object control, Object supplied, Object tryBucket, Object statusBucket) {
        var ingress = INGRESS.get();
        if (ingress == null || !(supplied instanceof Request request)) { return null; }
        var run = ingress.run;
        synchronized (run) {
            if (run.controls[ingress.role] != null && run.controls[ingress.role] != control) { fail(run, "RF_CONTROL_CHANGED"); }
            run.controls[ingress.role] = control;
            run.epochs[ingress.role] = request.connectionEpoch();
        }
        var call = new OfferCall(run, ingress.role, request, tryBucket, statusBucket, OFFER.get());
        OFFER.set(call); return call;
    }
    public static void offered(Object value, Object result, boolean normal) {
        if (!(value instanceof OfferCall call)) { return; }
        try {
            synchronized (call.run) {
                if (call.request.command() == Command.TRY) { call.run.lastTry[call.role] = call.request; }
                event(call.run, Map.of("type", "OFFER", "role", call.role, "request", call.request,
                        "normal", normal, "result", result instanceof Enum<?> e ? e.name() : "NO_RETURN"));
            }
        } finally { if (call.previous == null) { OFFER.remove(); } else { OFFER.set(call.previous); } }
    }
    public static long counterpartBefore(Object bucket) {
        var call = OFFER.get();
        if (call == null) { return -1; }
        return ((BucketView) (bucket == call.tryBucket ? call.statusBucket : call.tryBucket)).rf$units();
    }
    public static void token(Object bucket, long now, long before, long after, long otherBefore, Object result) {
        var call = OFFER.get();
        if (call == null) { return; }
        var run = call.run;
        long otherAfter = ((BucketView) (bucket == call.tryBucket ? call.statusBucket : call.tryBucket)).rf$units();
        synchronized (run) {
            boolean isTry = call.request.command() == Command.TRY;
            if (call.tryBucket == call.statusBucket || bucket != (isTry ? call.tryBucket : call.statusBucket)) {
                fail(run, "RF_CROSS_BUCKET_IDENTITY"); return;
            }
            if (otherBefore != otherAfter) { fail(run, "RF_OTHER_BUCKET_MUTATED_BY_TAKE"); }
            event(run, Map.of("type", "TOKEN", "role", call.role, "command", call.request.command(),
                    "n", call.request.requestSeq(), "now", now, "beforeUnits", before, "afterUnits", after,
                    "otherBeforeUnits", otherBefore, "otherAfterUnits", otherAfter, "result", ((Enum<?>) result).name()));
            if (!run.rate || call.role != 0 || call.request.requestSeq() != run.rateN) { return; }
            if (isTry) {
                if (run.rateTryObserved) { fail(run, "RF_DUPLICATE_RATE_TRY"); }
                run.rateTryObserved = true;
                run.rateLimited = result == P11ControlBudgets.RateResult.RATE_LIMITED && before < 1000;
                run.rateTryTime = now;
            } else {
                if (run.rateStatusObserved) { fail(run, "RF_DUPLICATE_RATE_STATUS"); }
                run.rateStatusObserved = true;
                run.statusAcceptedAfterTry = run.rateTryObserved && result == P11ControlBudgets.RateResult.ACCEPTED
                        && now >= run.rateTryTime;
            }
        }
    }
    public static void observerFailed() { var run = active; if (run != null) { fail(run, "RF_OBSERVER_FAILURE"); } }
    public static void service(Object control, boolean inbox, boolean notification, boolean prefer, Object result) {
        var run = active;
        if (run == null || !run.fair) { return; }
        synchronized (run) {
            int role = control == run.controls[0] ? 0 : control == run.controls[1] ? 1 : -1;
            if (role < 0) { return; }
            var value = (P11TransitionControl.Service) result;
            event(run, Map.of("type", "SERVICE", "role", role, "tick", tick(run),
                    "inboxBefore", inbox, "notificationBefore", notification, "preferNotificationBefore", prefer,
                    "result", value.name()));
            if (run.owedInbox[role]) {
                if (!inbox || value != P11TransitionControl.Service.INBOX) { fail(run, "RF_PENDING_INBOX_STARVED"); }
                else { run.notificationToInboxPairs++; }
                run.owedInbox[role] = false;
            }
            if (inbox && notification) {
                if (value != (prefer ? P11TransitionControl.Service.NOTIFICATION : P11TransitionControl.Service.INBOX)) {
                    fail(run, "RF_SERVICE_PREFERENCE_VIOLATION");
                }
                if (value == P11TransitionControl.Service.INBOX) { run.bothInbox++; }
                if (value == P11TransitionControl.Service.NOTIFICATION) { run.bothNotification++; run.owedInbox[role] = true; }
            }
        }
    }
    public static void polled(long nativeTick, List<Long> before, int used, Object supplied) {
        var run = active;
        if (run == null || !run.fair || !run.server.isSameThread()) { return; }
        var result = (java.util.Optional<?>) supplied;
        if (result.isEmpty()) { return; }
        long selected = ((P11ControlBudgets.FairDispatcher.Dispatch) result.orElseThrow()).connectionId();
        synchronized (run) {
            if (selected != run.epochs[0] && selected != run.epochs[1]) { return; }
            if (nativeTick != tick(run) || used != 1 || before.isEmpty() || before.getFirst() != selected
                    || run.lastPollTick == nativeTick) { fail(run, "RF_Q1_FIFO_OR_REAL_TICK"); }
            if (run.owedPeer != 0) {
                if (selected != run.owedPeer || nativeTick <= run.owedTick) { fail(run, "RF_PEER_NEXT_SERVICE_BOUND"); }
                else { run.pairedWindows++; }
                run.owedPeer = 0;
            }
            if (run.epochs[0] != run.epochs[1] && before.contains(run.epochs[0]) && before.contains(run.epochs[1])) {
                run.owedPeer = selected == run.epochs[0] ? run.epochs[1] : run.epochs[0]; run.owedTick = nativeTick;
            }
            run.lastPollTick = nativeTick;
            event(run, Map.of("type", "POLL", "tick", nativeTick, "before", before, "selected", selected, "used", used));
        }
    }
    public static void sent(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof P11TransitionStatePayload payload) {
            state(listener.getConnection(), payload.state());
        }
    }
    private static void state(Connection connection, Object supplied) {
        var run = active;
        if (run == null || !(supplied instanceof State state)) { return; }
        int role = connection == run.ca ? 0 : connection == run.cb ? 1 : -1;
        if (role < 0) { return; }
        synchronized (run) {
            event(run, Map.of("type", "STATE_SEND_RETURN", "role", role, "state", state));
            if (run.rate && role == 0 && state.requestSeq() == run.rateN && state.outcome() == Outcome.NOT_STARTED
                    && state.reason() == Reason.CONTROL_RATE_LIMIT) { run.rateReceipt = state; }
        }
    }
    private static void unchanged(Run run) {
        require(run.ca.isConnected() && run.cb.isConnected() && run.server.getPlayerList().getPlayer(run.a.getUUID()) == run.a
                && run.server.getPlayerList().getPlayer(run.b.getUUID()) == run.b
                && P11C4aNativeObservations.count(run.ca, RESPAWN_CALL_ENTER) == run.bodyA
                && P11C4aNativeObservations.count(run.cb, RESPAWN_CALL_ENTER) == run.bodyB
                && P11C4aNativeObservations.count(run.ca, RESPAWN_FRAME_PRODUCED) == run.frameA
                && P11C4aNativeObservations.count(run.cb, RESPAWN_FRAME_PRODUCED) == run.frameB, "RF_NATIVE_BODY_OR_PAIR_CHANGED");
    }
    private static void arm(ServerPlayer actor) {
        P11C4aC6EarlyGateProbe.arm(actor, P11C4aC6EarlyGateProbe.Point.PLAY_FIRST);
    }
    private static void gateReport(Run run, String leaf) throws IOException {
        P11C4aEvidence.write(run.output, "rf-gate-" + leaf + ".json", P11C4aC6EarlyGateProbe.reportAndRelease());
    }
    private static void cue(Run run, String suffix) throws IOException { cue(run, "a", suffix); cue(run, "b", suffix); }
    private static void cue(Run run, String role, String suffix) throws IOException { P11C4aEvidence.cue(run.output, role + "-rf-" + suffix + ".ready"); }
    private static boolean receipt(Run run, String role, String leaf) throws IOException {
        return P11C4aEvidence.receiptPresent(run.output.getParent().resolve("client-" + role), leaf + ".json");
    }
    private static long tick(Run run) { return Integer.toUnsignedLong(run.server.getTickCount()); }
    private static void write(Run run, String leaf, String status) throws IOException {
        Map<String, Object> result;
        synchronized (run) {
            require(run.failure == null, "RF_FAILED_OBSERVER_CANNOT_QUALIFY");
            var values = new LinkedHashMap<String, Object>();
            values.put("status", status); values.put("limits", run.limits); values.put("events", List.copyOf(run.events));
            values.put("failure", run.failure == null ? "NONE" : run.failure);
            values.put("pairedActualQ1Windows", run.pairedWindows); values.put("bothWorkInboxSelections", run.bothInbox);
            values.put("bothWorkNotificationSelections", run.bothNotification);
            values.put("notificationThenStillPendingInboxPairs", run.notificationToInboxPairs);
            values.put("fairness", fairnessQualified(run.pairedWindows, run.bothInbox, run.bothNotification,
                    run.notificationToInboxPairs) ? "QUALIFIED" : "UNPROVEN_WINDOW");
            values.put("tryRateLimited", run.rateLimited); values.put("sameNStatusAcceptedAfterTry", run.statusAcceptedAfterTry);
            values.put("actualTryTakeObserved", run.rateTryObserved); values.put("actualStatusTakeObserved", run.rateStatusObserved);
            values.put("actualClientRawRateReceiptObserved", run.clientRateReceipt);
            values.put("rateReceipt", run.rateReceipt);
            values.put("reverseIndependence", rateQualified(run.rateLimited, run.statusAcceptedAfterTry && run.clientRateReceipt, run.rateReceipt != null)
                    ? "QUALIFIED" : "UNPROVEN_RATE_WINDOW");
            values.put("engineeringFutureTryNotUserRetry", true); values.put("fullC4aAcceptance", false);
            values.put("normalFiveScenesClaimed", false);
            result = java.util.Collections.unmodifiableMap(values);
        }
        P11C4aEvidence.write(run.output, leaf, result);
    }
    static boolean fairnessQualified(int windows, int inbox, int notification, int pairs) {
        return windows >= 4 && inbox > 0 && notification > 0 && pairs > 0;
    }
    static boolean rateQualified(boolean limited, boolean accepted, boolean receipt) { return limited && accepted && receipt; }
    private static void event(Run run, Map<String, Object> value) {
        if (run.events.size() >= 4096) { fail(run, "RF_EVENT_BOUND"); return; } run.events.add(value);
    }
    private static void fail(Run run, String code) { if (run.failure == null) { run.failure = code; } }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer a, b; final Connection ca, cb; final Path output;
        final long bodyA, bodyB, frameA, frameB;
        final boolean keepInventory, immediate;
        final Object[] controls = new Object[2]; final long[] epochs = new long[2]; final Request[] lastTry = new Request[2];
        final boolean[] owedInbox = new boolean[2];
        final ArrayList<Map<String, Object>> events = new ArrayList<>();
        volatile Map<String, Object> limits; volatile boolean fair, rate; volatile String failure;
        volatile boolean leftA, leftB, rateTryObserved, rateStatusObserved, rateLimited, statusAcceptedAfterTry, clientRateReceipt;
        volatile long rateN; long rateTryTime, lastPollTick = -1, owedPeer, owedTick;
        int stage, ticks, pairedWindows, bothInbox, bothNotification, notificationToInboxPairs; State rateReceipt;
        Run(MinecraftServer server, ServerPlayer a, ServerPlayer b, Path output) {
            this.server = server; this.a = a; this.b = b; this.output = output;
            keepInventory = server.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
            immediate = server.getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
            ca = a.connection.getConnection(); cb = b.connection.getConnection();
            require(P11C4aNativeObservations.count(ca, LOGIN_FRAME_PRODUCED) == 1
                    && P11C4aNativeObservations.count(cb, LOGIN_FRAME_PRODUCED) == 1
                    && P11C4aNativeObservations.tries(ca) == 0 && P11C4aNativeObservations.tries(cb) == 0,
                    "RF_ORIGINAL_AUTHENTICATED_PLAY_BASELINE");
            bodyA = P11C4aNativeObservations.count(ca, RESPAWN_CALL_ENTER); bodyB = P11C4aNativeObservations.count(cb, RESPAWN_CALL_ENTER);
            frameA = P11C4aNativeObservations.count(ca, RESPAWN_FRAME_PRODUCED); frameB = P11C4aNativeObservations.count(cb, RESPAWN_FRAME_PRODUCED);
        }
    }
    private record Ingress(Run run, int role, Ingress previous) { }
    private record OfferCall(Run run, int role, Request request, Object tryBucket, Object statusBucket, OfferCall previous) { }
}
