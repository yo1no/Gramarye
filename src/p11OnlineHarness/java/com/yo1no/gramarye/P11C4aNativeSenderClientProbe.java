package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.Level;

/** Client-only original native callsite observations; no health, key, sequence or controller seed. */
public final class P11C4aNativeSenderClientProbe {
    private static volatile Run active;
    private P11C4aNativeSenderClientProbe() { }

    static void start(Minecraft minecraft, Connection connection, Path output) {
        require(P11C4aNativeSenderProbe.selected() && minecraft.isSameThread() && active == null
                && minecraft.player != null && minecraft.level != null && !minecraft.player.isDeadOrDying()
                && minecraft.player.connection.getConnection() == connection && connection.isConnected()
                && connection.isEncrypted() && !connection.isMemoryConnection(), "SENDERS_CLIENT_PREARM_OWNER");
        active = new Run(connection, minecraft.player, output);
        P11C4aConfigPrimaryProbe.armSender(connection, output, false);
    }

    /** Two fixed original tasks only; preserve their observed native scheduling order. */
    public static Runnable scheduled(Packet<?> packet, PacketListener listener,
            BlockableEventLoop<?> executor, Runnable originalTask) {
        var run = active;
        if (run == null || run.endStarted || listener != run.deathActor.connection) { return originalTask; }
        int kind = packet instanceof ClientboundPlayerCombatKillPacket combat
                && combat.playerId() == run.deathActor.getId() ? 1
                : packet instanceof ClientboundSetHealthPacket health && health.getHealth() == 0.0F ? 2 : 0;
        if (kind == 0) { return originalTask; }
        return () -> {
            var minecraft = Minecraft.getInstance();
            if (active != run || !run.connection.isConnected()) { originalTask.run(); return; }
            require(minecraft.isSameThread() && executor == minecraft
                    && run.connection.getPacketListener() == listener && minecraft.player == run.deathActor
                    && !run.deathActor.isDeadOrDying() && run.callbackCaptures < 2
                    && (kind == 1 ? run.combatCaptures == 0 : run.healthCaptures == 0),
                    "SENDERS_CAPTURE_EXACT_NATIVE_CALLBACK");
            run.originalCallbacks[run.callbackCaptures] = originalTask;
            run.callbackKinds[run.callbackCaptures] = kind;
            if (kind == 1) { run.combatCaptures++; } else { run.healthCaptures++; }
            if (run.callbackCaptures == 0) { run.capturedAtNanos = System.nanoTime(); }
            run.callbackCaptures++;
            if (run.callbackCaptures != 2) { return; }
            try {
                P11C4aEvidence.write(run.output, "native-combat-captured.json", Map.of(
                        "status", "ENGINEERING_CUSTODY_OF_TWO_ORIGINAL_PACKET_UTILS_RUNNABLES_NOT_ACCEPTANCE",
                        "originalRunnableCaptures", run.callbackCaptures, "originalRunnableInvocations", 0,
                        "combatCaptures", run.combatCaptures, "zeroHealthCaptures", run.healthCaptures,
                        "nativeArrivalOrder", callbackOrder(run),
                        "clientThread", true, "sameActorAndConnection", true,
                        "packetTextOrAuthDataReadOrRecorded", false));
                P11C4aEvidence.cue(run.output, "native-combat-captured.ready");
            } catch (IOException failure) { run.failure = "SENDERS_CAPTURE_EVIDENCE_IO"; }
        };
    }

    /** Original Runnable retains shouldHandleMessage and original native onPacketError policy. */
    static void releaseCombatKill(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && run.failure == null,
                "SENDERS_RELEASE_CLIENT_OWNER");
        if (run.callbackReleases != 0 || run.callbackCaptures == 0) { return; }
        require(System.nanoTime() - run.capturedAtNanos <= java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
                && run.connection.isConnected() && run.connection.getPacketListener() == run.deathActor.connection
                && minecraft.player == run.deathActor && !run.deathActor.isDeadOrDying()
                && run.callbackReleases == 0 && run.tryReturns == 0,
                "SENDERS_CAPTURE_DEADLINE_OR_OWNER_LOST");
        if (run.callbackCaptures != 2 || !P11C4aEvidence.cuePresent(
                run.output.resolveSibling("server"), "a-reload-fop.ready")) { return; }
        require(run.combatCaptures == 1 && run.healthCaptures == 1 && run.zeroHealthReturns == 0
                && !run.deathActor.shouldShowDeathScreen(), "SENDERS_RELEASE_REQUIRES_TWO_UNEXECUTED_CALLBACKS");
        Runnable first = run.originalCallbacks[0];
        Runnable second = run.originalCallbacks[1];
        run.originalCallbacks[0] = null;
        run.originalCallbacks[1] = null;
        run.callbackReleases = 2;
        minecraft.executeIfPossible(() -> {
            require(Minecraft.getInstance().isSameThread() && active == run && run.callbackInvocations == 0,
                    "SENDERS_ORIGINAL_CALLBACKS_NOT_ONCE");
            run.callbackInvocations++;
            first.run();
            run.callbackReturns++;
            run.callbackInvocations++;
            second.run();
            run.callbackReturns++;
        });
    }

    /** Unique CombatKill→LocalPlayer.respawn callsite, before/after the untouched original operation. */
    public static void immediate(LocalPlayer actor, boolean returned) {
        var run = active;
        if (run == null || run.endStarted || actor != run.deathActor) { return; }
        require(Minecraft.getInstance().isSameThread() && Minecraft.getInstance().player == actor
                && actor.connection.getConnection() == run.connection && !actor.shouldShowDeathScreen(),
                "SENDERS_ORIGINAL_IMMEDIATE_OWNER");
        if (returned) { run.immediateReturns++; } else { run.immediateCalls++; }
        require(run.immediateCalls == 1 && run.immediateReturns <= 1, "SENDERS_MULTIPLE_ORIGINAL_IMMEDIATE");
    }

    /** Actual native health-handler return, not an inferred health value from CombatKill. */
    public static void healthApplied(ClientPacketListener listener, ClientboundSetHealthPacket packet) {
        var run = active;
        if (run == null || run.endStarted || run.hiddenDone || listener != run.deathActor.connection
                || packet.getHealth() != 0.0F) { return; }
        require(Minecraft.getInstance().isSameThread() && run.connection.getPacketListener() == listener
                && Minecraft.getInstance().player == run.deathActor && run.deathActor.isDeadOrDying(),
                "SENDERS_ORIGINAL_HEALTH_ZERO_NOT_APPLIED");
        run.zeroHealthReturns++;
    }

    /** Exact Minecraft.setScreen(null)→LocalPlayer.respawn callsite, never a substitute call. */
    public static void hidden(LocalPlayer actor, boolean returned) {
        var run = active;
        if (run == null || run.endStarted || run.hiddenDone || !run.hiddenCalling && !run.hiddenInvoked) { return; }
        require(Minecraft.getInstance().isSameThread() && actor == run.deathActor
                && actor.isDeadOrDying() && !actor.shouldShowDeathScreen(), "SENDERS_HIDDEN_AUTO_OWNER");
        if (returned) { run.hiddenReturns++; } else { run.hiddenCalls++; }
        hiddenDiagnostic(run, returned);
        require(run.hiddenCalling && run.hiddenCalls >= 1 && run.hiddenCalls <= 2
                && run.hiddenReturns == run.hiddenCalls - (returned ? 0 : 1)
                && run.hiddenScreenDepth == run.hiddenCalls && run.tryReturns == 1,
                "SENDERS_HIDDEN_NATIVE_TWO_ENTRY_BALANCE");
    }


    /** Exact original setScreen call stack only, selected by the existing one-call fixture. */
    public static Object hiddenScreenEntering() {
        var run = active;
        if (run == null || !run.hiddenCalling) { return null; }
        require(++run.hiddenScreenDepth <= 2, "SENDERS_HIDDEN_SCREEN_DEPTH_EXCEEDED");
        run.hiddenScreenPeak = Math.max(run.hiddenScreenPeak, run.hiddenScreenDepth);
        return run;
    }

    public static void hiddenScreenLeaving(Object token) {
        if (token instanceof Run run) { run.hiddenScreenDepth--; }
    }

    /** Fixed bounded pre-guard observations; failure never replaces the existing count guard. */
    private static void hiddenDiagnostic(Run run, boolean returned) {
        String leaf = !returned && run.hiddenCalls == 1 ? "native-hidden-entry-1.json"
                : returned && run.hiddenCalls == 1 && run.hiddenReturns == 1 ? "native-hidden-return-1.json"
                : !returned && run.hiddenCalls == 2 ? "native-hidden-entry-2.json"
                : returned && run.hiddenCalls == 2 && run.hiddenReturns == 2 ? "native-hidden-return-2.json"
                : !returned && run.hiddenCalls == 3 ? "native-hidden-entry-3.json" : null;
        if (leaf == null) { return; }
        try {
            var minecraft = Minecraft.getInstance();
            var values = new java.util.LinkedHashMap<String, Object>();
            values.put("status", "PRE_GUARD_NATIVE_HIDDEN_OBSERVATION_NOT_ACCEPTANCE");
            values.put("returned", returned);
            values.put("hiddenCalls", run.hiddenCalls);
            values.put("hiddenReturns", run.hiddenReturns);
            values.put("setScreenDepth", run.hiddenScreenDepth);
            values.put("setScreenPeakDepth", run.hiddenScreenPeak);
            values.put("screen", minecraft.screen == null ? "NONE"
                    : minecraft.screen instanceof P11ClientTransitionScreen ? "P11_STATUS"
                    : minecraft.screen instanceof P11ClientLeaveScreen ? "P11_LEAVE" : "OTHER");
            values.put("mouseGrabbed", minecraft.mouseHandler.isMouseGrabbed());
            values.put("windowActive", minecraft.isWindowActive());
            values.put("sameActor", minecraft.player == run.deathActor);
            values.put("sameCurrentListener", run.connection.getPacketListener() == run.deathActor.connection);
            values.put("trySendReturns", run.tryReturns);
            values.put("currentState", P11ClientTransitions.view());
            values.put("exactNativeTwoEntryBalanceGuard", true);
            values.put("fullC4aAcceptance", false);
            P11C4aEvidence.write(run.output, leaf, values);
        } catch (IOException | RuntimeException | Error diagnosticFailure) {
            if (run.failure == null) { run.failure = "SENDERS_HIDDEN_DIAGNOSTIC_FAILED"; }
        }
    }

    /** Existing send observer invokes this even when its own reload phase is inactive. */
    static void submitted(Object value) {
        var run = active;
        if (run == null || !(value instanceof Request request) || request.command() != Command.TRY) { return; }
        require(!run.endStarted, "SENDERS_SECOND_END_CLIENT_UNEXPECTED_TRY");
        run.tryReturns++;
        require(run.tryReturns <= 2 && request.kind() == Kind.DEATH, "SENDERS_CLIENT_UNEXPECTED_TRY");
    }

    static boolean inspectHidden(Minecraft minecraft, State wait, State may) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && !run.endStarted, "SENDERS_HIDDEN_PHASE");
        if (run.hiddenDone) { return true; }
        if (wait == null || run.zeroHealthReturns == 0) { return false; }
        var before = P11ClientTransitions.view();
        require(may == null && before == wait && wait.outcome() == Outcome.NOT_STARTED
                && wait.availability() == Availability.WAIT_NOTIFY && wait.reason() == Reason.ACTIVE_OPERATION
                && minecraft.player == run.deathActor && run.deathActor.isDeadOrDying()
                && !run.deathActor.shouldShowDeathScreen() && minecraft.screen instanceof P11ClientTransitionScreen
                && run.immediateCalls == 1 && run.immediateReturns == 1 && run.tryReturns == 1
                && run.combatCaptures == 1 && run.healthCaptures == 1
                && run.callbackCaptures == 2 && run.callbackReleases == 2
                && run.callbackInvocations == 2 && run.callbackReturns == 2
                && run.zeroHealthReturns == 1 && frames(run) == run.beforeFrames,
                "SENDERS_HIDDEN_REQUIRES_GENUINE_FOP_REFUSAL_AND_NATIVE_DEATH");
        if (!run.hiddenInvoked) {
            // Pinned grabMouse has one native reentry only under these observed conditions.
            // Wait for actual readiness; never focus/grab/release the mouse or manufacture it.
            if (!minecraft.isWindowActive() || minecraft.mouseHandler.isMouseGrabbed()) { return false; }
            run.hiddenCalling = true;
            try { minecraft.setScreen(null); }
            finally { run.hiddenCalling = false; }
            require(run.hiddenCalls == 2 && run.hiddenReturns == 2 && run.hiddenScreenDepth == 0
                    && run.hiddenScreenPeak == 2 && run.tryReturns == 1 && frames(run) == run.beforeFrames
                    && minecraft.player == run.deathActor && minecraft.screen instanceof P11ClientTransitionScreen
                    && P11ClientTransitions.view() == before && run.failure == null,
                    "SENDERS_HIDDEN_AUTO_DUPLICATED_REQUEST_OR_LOST_SCREEN");
            run.hiddenInvoked = true;
            run.hiddenWait = before;
            run.hiddenFirstGameTime = run.hiddenLastGameTime = minecraft.level.getGameTime();
            return false;
        }
        require(run.hiddenCalls == 2 && run.hiddenReturns == 2 && run.hiddenScreenDepth == 0
                && run.hiddenScreenPeak == 2 && run.tryReturns == 1 && frames(run) == run.beforeFrames
                && minecraft.player == run.deathActor && minecraft.screen instanceof P11ClientTransitionScreen
                && P11ClientTransitions.view() == run.hiddenWait && before == run.hiddenWait && run.failure == null,
                "SENDERS_HIDDEN_FOLLOWING_TICK_UNSTABLE");
        long now = minecraft.level.getGameTime();
        require(now >= run.hiddenLastGameTime, "SENDERS_HIDDEN_CLIENT_TIME_REVERSED");
        if (now == run.hiddenLastGameTime) { return false; }
        run.hiddenLastGameTime = now;
        if (++run.hiddenStableTicks < 2) { return false; }
        run.hiddenDone = true;
        var receipt = new java.util.LinkedHashMap<String, Object>();
        receipt.put("status", "ORIGINAL_IMMEDIATE_AND_HIDDEN_AUTO_SAME_F0_ZERO_SECOND_TRY");
        receipt.put("wait", wait); receipt.put("originalImmediateCalls", run.immediateCalls);
        receipt.put("originalNativeZeroHealthReturns", run.zeroHealthReturns);
        receipt.put("originalHiddenAutoCalls", run.hiddenCalls); receipt.put("originalHiddenAutoReturns", run.hiddenReturns);
        receipt.put("expectedNativeHiddenEntries", 2); receipt.put("nativeScreenPeakDepth", run.hiddenScreenPeak);
        receipt.put("nativeScreenFinalDepth", run.hiddenScreenDepth);
        receipt.put("followingNativeClientLevelTicksObserved", run.hiddenStableTicks);
        receipt.put("followingNativeClientGameTimeDelta", run.hiddenLastGameTime - run.hiddenFirstGameTime);
        receipt.put("stableP11StatusAfterOriginalOuterReturnAndFollowingTicks", true);
        receipt.put("windowActiveAndMouseUngrabbedObservedBeforeCall", true);
        receipt.put("trySendReturnsAfterBothCallsites", run.tryReturns); receipt.put("nativeRespawnDeltaBeforeRetry", 0);
        receipt.put("controlledSetScreenNullNotPhysicalInput", true); receipt.put("fullC4aAcceptance", false);
        receipt.put("originalPacketRunnableCaptures", run.callbackCaptures);
        receipt.put("originalPacketRunnableReleases", run.callbackReleases);
        receipt.put("originalPacketRunnableInvocations", run.callbackInvocations);
        receipt.put("originalPacketRunnableReturns", run.callbackReturns);
        receipt.put("nativeArrivalAndReleaseOrder", callbackOrder(run));
        receipt.put("engineeringDelayedCombatKillAndHealthCallbacks", true);
        P11C4aEvidence.write(run.output, "native-hidden-auto.json", receipt);
        return true;
    }

    static void finishImmediate() {
        var run = active;
        require(run != null && run.hiddenDone && run.tryReturns == 2 && frames(run) == run.beforeFrames + 1,
                "SENDERS_FRESH_RETRY_NOT_EXACT");
        run.immediateFinished = true;
    }

    static void startSecondEnd(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && run.immediateFinished && !run.endStarted && minecraft.isSameThread()
                && minecraft.player != null && minecraft.level != null && minecraft.level.dimension() == Level.OVERWORLD
                && !minecraft.player.isDeadOrDying() && minecraft.player.connection.getConnection() == run.connection,
                "SENDERS_SECOND_END_CLIENT_OWNER");
        run.endState = P11ClientTransitions.view();
        require(run.endState != null && run.endState.outcome() == Outcome.COMPLETED
                && !P11ClientTransitions.blocksCast(), "SENDERS_SECOND_END_UNSETTLED_BASELINE");
        run.endFrames = frames(run); run.endStarted = true;
        P11C4aEvidence.cue(run.output, "second-end-armed.ready");
    }

    public static void gameEvent(ClientPacketListener listener, ClientboundGameEventPacket packet) {
        var run = active;
        if (run != null && run.endStarted && listener.getConnection() == run.connection
                && packet.getEvent() == ClientboundGameEventPacket.WIN_GAME) { run.winPackets++; }
    }

    static boolean tickSecondEnd(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && run.endStarted && minecraft.isSameThread() && ++run.endTicks <= 2400
                && run.connection.isConnected() && run.winPackets == 0 && !(minecraft.screen instanceof WinScreen)
                && run.tryReturns == 2 && P11ClientTransitions.view() != null
                && P11ClientTransitions.view().equals(run.endState)
                && !P11ClientTransitions.blocksCast(), "SENDERS_SECOND_END_CLIENT_CONTROL_CHANGED");
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) { return false; }
        require(minecraft.player.connection.getConnection() == run.connection, "SENDERS_SECOND_END_CLIENT_CONNECTION");
        if (!run.endView && minecraft.level.dimension() == Level.END && frames(run) == run.endFrames + 1) {
            run.endView = true;
            P11C4aEvidence.cue(run.output, "second-end-view.ready");
        }
        if (!run.endView || minecraft.level.dimension() != Level.OVERWORLD || frames(run) != run.endFrames + 2) { return false; }
        P11C4aEvidence.write(run.output, "second-end.json", Map.of(
                "status", "TWO_ORIGINAL_DIMENSION_FRAMES_NO_WIN_SCREEN_TRY_OR_P11_STATE_CHANGE",
                "actualEndViewObserved", true, "nativeRespawnHandlerDelta", 2,
                "winGamePackets", run.winPackets, "additionalTryReturns", 0,
                "retainedCompletedState", run.endState, "fullC4aAcceptance", false));
        P11C4aConfigPrimaryProbe.finish(run.connection);
        active = null;
        return true;
    }

    static void release() { active = null; }
    private static java.util.List<String> callbackOrder(Run run) {
        return java.util.List.of(run.callbackKinds[0] == 1 ? "COMBAT_KILL" : "ZERO_HEALTH",
                run.callbackKinds[1] == 1 ? "COMBAT_KILL" : "ZERO_HEALTH");
    }
    private static long frames(Run run) { return P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN); }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final Connection connection; final LocalPlayer deathActor; final Path output; final long beforeFrames;
        int immediateCalls, immediateReturns, hiddenCalls, hiddenReturns, zeroHealthReturns, tryReturns, winPackets, endTicks;
        int hiddenScreenDepth, hiddenScreenPeak, hiddenStableTicks;
        boolean hiddenInvoked;
        long hiddenFirstGameTime, hiddenLastGameTime;
        State hiddenWait;
        final Runnable[] originalCallbacks = new Runnable[2];
        final int[] callbackKinds = new int[2];
        String failure;
        long capturedAtNanos;
        int combatCaptures, healthCaptures, callbackCaptures, callbackReleases, callbackInvocations, callbackReturns;
        boolean hiddenCalling, hiddenDone, immediateFinished, endStarted, endView; long endFrames; State endState;
        Run(Connection connection, LocalPlayer actor, Path output) {
            this.connection = connection; deathActor = actor; this.output = output; beforeFrames = frames(this);
        }
    }
}
