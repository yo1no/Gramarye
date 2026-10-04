package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundDebugSampleSubscriptionPacket;
import static com.yo1no.gramarye.P11TransitionProtocol.*;

/** Excluded bounded native continuation/packet deferral. Never opens a key or Optional value. */
public final class P11C4aPreplayChatClientProbe {
    private static volatile Run active;
    private P11C4aPreplayChatClientProbe() { }

    static void arm(Minecraft minecraft, String role, Path output) {
        if (!P11C4aPreplayChatProbe.selected() || !role.equals("a")) { return; }
        require(active == null && minecraft.isSameThread() && minecraft.player == null
                && minecraft.getConnection() == null, "CHAT_FRESH_CLIENT_ARM");
        active = new Run(output, P11C4aPreplayChatProbe.lateKey());
    }

    /** The ORIGINAL prepare call already returned; only nonsecret reset/readiness facts are observed. */
    public static void prepared(ClientPacketListener listener, boolean originalResetPresent) {
        var run = active; if (run == null) { return; }
        var minecraft = Minecraft.getInstance();
        require(minecraft.isSameThread() && listener.getConnection().isEncrypted()
                && !listener.getConnection().isMemoryConnection() && listener.getConnection().getPacketListener() == listener
                && minecraft.player != null && minecraft.player.connection == listener
                && P11ClientTransitions.allowActorFunctions(listener) && originalResetPresent, "CHAT_ORIGINAL_LOGIN_PREPARE_OWNER");
        if (run.connection == null) { run.connection = listener.getConnection(); run.old = listener; run.oldActor = minecraft.player; }
        require(run.connection == listener.getConnection() && ++run.prepares <= 2, "CHAT_UNEXPECTED_PREPARE_COUNT");
        if (run.prepares == 2) { require(listener != run.old && run.preplayReported, "CHAT_NEW_LOGIN_BEFORE_PREPLAY_PROOF"); run.next = listener; }
    }

    /** Supply only this one original thenAcceptAsync invocation with a bounded executor gate. */
    public static Executor executor(ClientPacketListener listener, Executor original) {
        var run = active;
        if (run == null || !run.late || listener != run.old || run.prepares != 1) { return original; }
        require(original == Minecraft.getInstance() && !run.executorWrapped, "CHAT_ORIGINAL_EXECUTOR_REQUIRED");
        run.executorWrapped = true; run.executor = original;
        return command -> {
            synchronized (run) {
                if (active == run && run.task == null && run.taskCaptures == 0) {
                    run.task = command; run.taskCaptures = 1; return;
                }
                run.failure = "CHAT_MULTIPLE_ORIGINAL_CONTINUATIONS";
            }
            original.execute(command); // Unexpected extra task remains native; this run cannot qualify.
        };
    }
    public static void continuation(ClientPacketListener listener, CompletableFuture<?> originalResult) {
        var run = active;
        if (run != null && run.late && listener == run.old) { run.completion = originalResult; }
    }

    /** Parameter content is never observed. The wrapping mixin compares identities locally only. */
    public static boolean keyObserved(ClientPacketListener listener) {
        var run = active; return run != null && (listener == run.old || listener == run.next);
    }
    public static void keyReturned(ClientPacketListener listener, boolean unchanged, boolean sessionPresent, boolean normal) {
        var run = active; if (run == null) { return; }
        if (!normal) { run.failure = "CHAT_ORIGINAL_KEY_CALLBACK_THROW"; return; }
        if (listener == run.old) {
            run.oldKeyCalls++;
            if (run.late && (!run.released || !unchanged || P11ClientTransitions.allowActorFunctions(listener))) {
                run.failure = "CHAT_LATE_KEY_MUTATED_OR_WRONG_GATE";
            }
        } else if (listener == run.next) {
            run.newKeyCalls++;
            if (unchanged || !sessionPresent || !P11ClientTransitions.allowActorFunctions(listener)) { run.failure = "CHAT_NEW_ORIGINAL_KEY_NOT_INSTALLED"; }
        }
    }

    /** The packet and call operation are genuine native producer values, retained at most once each. */
    public static void send(ClientPacketListener listener, Packet<?> packet, Operation<Void> original) {
        var run = active;
        boolean chat = packet instanceof ServerboundChatSessionUpdatePacket;
        boolean debug = packet instanceof ServerboundDebugSampleSubscriptionPacket;
        if (run != null && !run.late && listener == run.old && !run.released && (chat || debug)) {
            require(Minecraft.getInstance().isSameThread() && P11ClientTransitions.allowActorFunctions(listener), "CHAT_QUEUE_NATIVE_PRODUCER_NOT_LIVE");
            if (chat) { require(run.chat == null, "CHAT_QUEUE_MORE_THAN_ONE_CHAT"); run.chat = new Pending(listener, packet, original); }
            else { require(run.debug == null, "CHAT_QUEUE_MORE_THAN_ONE_DEBUG"); run.debug = new Pending(listener, packet, original); }
            return;
        }
        original.call(listener, packet);
        if (run != null && chat) {
            if (listener == run.old) { run.oldChatSends++; }
            else if (listener == run.next) { run.newChatSends++; }
        }
    }

    public static boolean beforeTick(ClientPacketListener listener) {
        var run = active;
        if (run == null || !run.measurePreplay || !preplay(run) || listener != run.connection.getPacketListener()) { return false; }
        require(Minecraft.getInstance().getDebugOverlay().showNetworkCharts(), "CHAT_PREPLAY_NATIVE_PING_BRANCH_NOT_ENABLED");
        require(!run.insidePreplayTick, "CHAT_NESTED_PREPLAY_TICK");
        run.insidePreplayTick = true; run.preplayTicks++; return true;
    }
    public static void afterTick(boolean selected, boolean normal) {
        var run = active; if (!selected || run == null) { return; }
        run.insidePreplayTick = false;
        if (!normal) { run.failure = "CHAT_ORIGINAL_PREPLAY_TICK_THROW"; }
    }
    public static void actorFunction(int category) {
        var run = active; if (run == null || !run.insidePreplayTick) { return; }
        if (category == 0) { run.refreshCalls++; } else if (category == 1) { run.pingCalls++; } else { run.debugCalls++; }
    }
    public static void oldActorTick(LocalPlayer actor) {
        var run = active; if (run != null && actor == run.oldActor && preplay(run)) { run.oldActorTicks++; }
    }

    /** Parent calls before the existing parking client tick and before it can click Retry. */
    static void tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && run.failure == null && ++run.ticks <= 2400, "CHAT_PHASE_DEADLINE_OR_FAILURE");
        if (run.connection == null) { return; }
        require(run.connection.isConnected(), "CHAT_TRANSPORT_DEPARTED");
        if (!run.ready) {
            if (P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.CLIENT_LOGIN_RETURN) != 1) { return; }
            var overlay = minecraft.getDebugOverlay();
            if (!run.chartsEnabled) {
                if (run.late ? run.taskCaptures != 1 : run.chat == null) { return; }
                require(!overlay.showDebugScreen() && !overlay.showFpsCharts() && !overlay.showNetworkCharts(), "CHAT_FRESH_NATIVE_OVERLAY_REQUIRED");
                if (run.late) { overlay.toggleNetworkCharts(); } else { overlay.toggleFpsCharts(); }
                run.chartsEnabled = true;
            }
            if (run.late ? run.taskCaptures != 1 : run.chat == null || run.debug == null) { return; }
            if (run.late) { require(run.oldKeyCalls == 0 && run.oldChatSends == 0, "CHAT_CALLBACK_RAN_BEFORE_RELEASE"); }
            else { require(run.oldKeyCalls == 1 && run.oldChatSends == 0, "CHAT_ORIGINAL_PACKET_QUEUE_PROVENANCE"); }
            if (!run.late) { overlay.toggleNetworkCharts(); } // Original public UI toggle; no physical key claim.
            run.ready = true;
            P11C4aEvidence.write(run.output, "preplay-chat-ready.json", report(run, "ORIGINAL_NATIVE_VALUES_HELD_NOT_ACCEPTANCE"));
        }
        if (preplay(run) && !run.released) {
            require(minecraft.player == null && minecraft.level == null && run.prepares == 1
                    && !P11ClientTransitions.allowActorFunctions(run.old), "CHAT_PREPLAY_NO_LOGIN_OR_ACTOR_REQUIRED");
            // Original clearClientLevel -> gui.onDisconnected may reset renderDebug.
            // Enable the real native conditional before measuring its gated calls.
            if (!minecraft.getDebugOverlay().showNetworkCharts()) { minecraft.getDebugOverlay().toggleNetworkCharts(); }
            require(minecraft.getDebugOverlay().showNetworkCharts(), "CHAT_PREPLAY_CHARTS_UNAVAILABLE");
            run.measurePreplay = true;
            run.released = true;
            if (run.late) {
                Runnable originalTask;
                synchronized (run) { originalTask = run.task; run.task = null; run.taskReleases++; }
                require(originalTask != null && run.taskReleases == 1, "CHAT_ORIGINAL_TASK_MISSING");
                run.executor.execute(originalTask); // SAME opaque native Runnable; actual Optional/key stays inside it.
            } else {
                Pending chat = run.chat, debug = run.debug; run.chat = null; run.debug = null;
                chat.original.call(chat.listener, chat.packet); run.queuedChatReturns++;
                debug.original.call(debug.listener, debug.packet); run.queuedDebugReturns++;
            }
        }
        if (run.released && !run.preplayReported && run.preplayTicks >= 8) {
            require(preplay(run) && minecraft.player == null && minecraft.level == null
                    && run.refreshCalls == 0 && run.pingCalls == 0 && run.debugCalls == 0 && run.oldActorTicks == 0
                    && run.oldChatSends == 0, "CHAT_PREPLAY_ACTOR_FUNCTION_ESCAPED");
            if (run.late) {
                if (run.completion == null || !run.completion.isDone()) { return; }
                require(!run.completion.isCompletedExceptionally() && run.oldKeyCalls == 1,
                        "CHAT_ORIGINAL_KEY_UNAVAILABLE_OR_FAILED_NOT_PASS");
            } else { require(run.queuedChatReturns == 1 && run.queuedDebugReturns == 1, "CHAT_QUEUE_ORIGINAL_SEND_MISSING"); }
            run.preplayReported = true;
            P11C4aEvidence.write(run.output, "preplay-chat-observed.json", report(run, "ACTUAL_PREPLAY_GATE_AND_ORIGINAL_DEFERRED_WORK_OBSERVED"));
        }
    }

    static boolean finish(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && run.failure == null && run.preplayReported, "CHAT_FINISH_OWNER");
        if (run.newKeyCalls == 0 || run.newChatSends == 0) { return false; }
        require(run.prepares == 2 && run.newKeyCalls == 1 && run.newChatSends == 1
                && minecraft.getConnection() == run.next && run.connection.getPacketListener() == run.next
                && minecraft.player != null && minecraft.player.connection == run.next
                && P11ClientTransitions.allowActorFunctions(run.next), "CHAT_NEW_ORIGINAL_LOGIN_ANNOUNCEMENT_NOT_EXACT");
        if (!run.finished) {
            run.finished = true;
            minecraft.getDebugOverlay().toggleNetworkCharts();
            minecraft.getDebugOverlay().toggleOverlay();
            P11C4aEvidence.write(run.output, "preplay-chat-client.json", report(run, "NEW_ORIGINAL_LOGIN_KEY_ANNOUNCEMENT_SERVER_VALIDATION_REQUIRED"));
        }
        return true;
    }
    static void release() { active = null; }
    private static boolean preplay(Run run) {
        if (run.connection == null || !(run.connection.getPacketListener() instanceof ClientPacketListener listener)
                || P11ClientTransitions.allowActorFunctions(listener)) { return false; }
        var state = P11ClientTransitions.view();
        return state != null && state.scope() == Scope.PREPLAY && state.actorGeneration() == 0 && state.outcome() == Outcome.NOT_STARTED;
    }
    private static Map<String,Object> report(Run run, String status) {
        var values = new LinkedHashMap<String,Object>(); values.put("status",status); values.put("lateKeyMode",run.late);
        values.put("originalLoginPrepares",run.prepares); values.put("opaqueOriginalTaskCaptures",run.taskCaptures); values.put("sameOriginalTaskReleases",run.taskReleases);
        values.put("oldSetKeyPairNormalReturns",run.oldKeyCalls); values.put("oldChatSendReturns",run.oldChatSends);
        values.put("queuedOriginalChatSendReturns",run.queuedChatReturns); values.put("queuedOriginalDebugSendReturns",run.queuedDebugReturns);
        values.put("actualNoLoginPreplayTicks",run.preplayTicks); values.put("nativeRefreshCalls",run.refreshCalls); values.put("nativePingCalls",run.pingCalls);
        values.put("nativeDebugCalls",run.debugCalls); values.put("oldLocalPlayerTicks",run.oldActorTicks);
        values.put("newSetKeyPairNormalReturns",run.newKeyCalls); values.put("newChatSendReturns",run.newChatSends);
        values.put("keyContentReadOrSerialized",false); values.put("physicalInputClaimed",false); values.put("fullC4aAcceptance",false); return values;
    }
    private static void require(boolean value,String code) { P11C4aEvidence.require(value,code); }
    private record Pending(ClientPacketListener listener, Packet<?> packet, Operation<Void> original) { }
    private static final class Run {
        final Path output; final boolean late;
        volatile String failure; volatile Runnable task; volatile int taskCaptures;
        Executor executor; CompletableFuture<?> completion; Connection connection; ClientPacketListener old,next; LocalPlayer oldActor;
        Pending chat,debug; int ticks,prepares,taskReleases,oldKeyCalls,newKeyCalls,oldChatSends,newChatSends;
        int queuedChatReturns,queuedDebugReturns,preplayTicks,refreshCalls,pingCalls,debugCalls,oldActorTicks;
        boolean executorWrapped,chartsEnabled,ready,released,preplayReported,insidePreplayTick,finished,measurePreplay;
        Run(Path output,boolean late) { this.output=output; this.late=late; }
    }
}
