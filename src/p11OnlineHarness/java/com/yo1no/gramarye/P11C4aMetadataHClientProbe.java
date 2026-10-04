package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** Excluded client-only H phase. Every TRY comes from an original native screen callback. */
@OnlyIn(Dist.CLIENT)
public final class P11C4aMetadataHClientProbe {
    private static Run active;
    private P11C4aMetadataHClientProbe() { }

    /** The server cue may precede actual local Login handling; tick waits for real PLAY/Death. */
    static void start(Minecraft minecraft, Connection connection, Path output) throws IOException {
        String role = P11C4aEvidence.property("role");
        require(P11C4aEvidence.enabled() && minecraft.isSameThread() && active == null
                && connection != null && connection.isConnected()
                && java.util.List.of("a", "host").contains(role), "METADATA_H_CLIENT_START_OWNER");
        require(output.equals(P11C4aEvidence.root().resolve("client-" + role))
                && Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(output),
                "METADATA_H_CLIENT_OUTPUT");
        active = new Run(connection, output);
    }

    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && ++run.ticks <= 2400
                && run.failure == null && run.connection.isConnected(), "METADATA_H_CLIENT_OWNER_OR_DEADLINE");
        if (!currentPlay(minecraft, run)) {
            require(!run.deathClicked, "METADATA_H_CLIENT_LEFT_PLAY_AFTER_TRY");
            return false; // Cue existence never substitutes for local native Login/PLAY.
        }
        var view = P11ClientTransitions.view();
        if (!run.deathClicked) {
            if (view == null || view.scope() != Scope.PLAY || view.kind() != Kind.DEATH
                    || view.outcome() != Outcome.BINDING || view.requestSeq() != 0
                    || view.actorGeneration() <= 0) { return false; }
            run.binding = view;
            if (!run.preClickRecorded) {
                run.preClickRecorded = true;
                var values = new LinkedHashMap<String, Object>();
                values.put("status", "FIRST_EXACT_DEATH_BINDING_BEFORE_NATIVE_SCREEN_ACTION");
                values.put("probeTick", run.ticks); values.put("currentPlay", currentPlay(minecraft, run));
                values.put("localDead", minecraft.player.isDeadOrDying()); values.put("view", view);
                values.put("input", P11C4aClientInputProbe.snapshot(minecraft));
                values.put("originalProductionTrySendReturns", run.submits);
                values.put("fullC4aAcceptance", false);
                P11C4aEvidence.write(run.output, "reload-h-client-preclick.json", values);
            }
            // Native CombatKill opens DeathScreen before the next server doTick health sync.
            // The real screen/button guards still decide when its original callback can run.
            run.deathClicked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.DEATH_RESPAWN);
            if (run.deathClicked) {
                require(run.submits == 1 && run.first != null && sameScene(run.first, run.binding),
                        "METADATA_H_CLIENT_NATIVE_DEATH_DID_NOT_SUBMIT");
            }
            return false;
        }
        if (run.may != null && !run.retryClicked) {
            require(run.wait != null && view == run.may && matches(run.first, view)
                    && view.outcome() == Outcome.NOT_STARTED && view.availability() == Availability.MAY_TRY
                    && frames(run) == run.beforeFrames, "METADATA_H_CLIENT_MAY_NOT_CURRENT");
            if (run.ticks <= run.mayTick + 1 || !P11ClientTransitions.retryArmed()) { return false; }
            var input = P11C4aClientInputProbe.snapshot(minecraft);
            if (!input.glfwActivationNeutral()) { return false; }
            run.neutral = input;
            run.retryClicked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.RETRY);
            if (run.retryClicked) {
                require(run.submits == 2 && run.second != null && sameScene(run.second, run.may)
                        && run.second.requestSeq() > run.first.requestSeq(),
                        "METADATA_H_CLIENT_RETRY_NOT_NEW_ORIGINAL_N");
            }
            return false;
        }
        if (run.completed == null) { return false; }
        require(run.retryClicked && run.submits == 2 && run.wait != null && run.may != null
                && view == run.completed && matches(run.second, view)
                && run.second.requestSeq() > run.first.requestSeq()
                && frames(run) == run.beforeFrames + 1 && minecraft.player.isAlive(),
                "METADATA_H_CLIENT_TERMINAL_FACTS");
        var values = new LinkedHashMap<String, Object>();
        values.put("status", "ACTUAL_H_REFUSAL_MAY_NEUTRAL_MANUAL_RETRY_AND_RESPAWN");
        values.put("mode", "REAL_METADATA_H"); values.put("binding", run.binding);
        values.put("firstOriginalTry", run.first); values.put("wait", run.wait); values.put("mayTry", run.may);
        values.put("secondOriginalTry", run.second); values.put("completed", run.completed);
        values.put("originalProductionTrySendReturns", run.submits);
        values.put("actualNativeRespawnDelta", frames(run) - run.beforeFrames);
        values.put("mayObservedAtProbeTick", run.mayTick); values.put("ticks", run.ticks);
        values.put("neutralBeforeFreshRetry", run.neutral);
        values.put("inputKind", "CONTROLLED_ORIGINAL_WINDOW_CALLBACK_AFTER_OBSERVED_GLFW_NEUTRAL");
        values.put("physicalHeldKeyClaimed", false); values.put("visualAppearanceClaimed", false);
        values.put("fullC4aAcceptance", false);
        P11C4aEvidence.write(run.output, "reload-h.json", values);
        active = null;
        return true;
    }

    /** Exact handler TAIL, same current native listener and the actual installed State identity. */
    public static void accepted(Object value, Connection connection, ICommonPacketListener listener) {
        var run = active;
        if (run == null || connection != run.connection || !(value instanceof State state)
                || connection.getPacketListener() != listener || listener.getConnection() != connection
                || listener.protocol() != ConnectionProtocol.PLAY || P11ClientTransitions.view() != state
                || state.scope() != Scope.PLAY || state.kind() != Kind.DEATH) { return; }
        try {
            require(Minecraft.getInstance().isSameThread(), "METADATA_H_CLIENT_STATE_THREAD");
            if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.WAIT_NOTIFY) {
                require(matches(run.first, state) && state.reason() == Reason.ACTIVE_TRANSITION
                        && frames(run) == run.beforeFrames, "METADATA_H_CLIENT_WRONG_WAIT");
                if (run.wait == null) { run.wait = state; }
            } else if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.MAY_TRY) {
                require(run.wait != null && matches(run.first, state)
                        && state.statusVersion() > run.wait.statusVersion() && frames(run) == run.beforeFrames,
                        "METADATA_H_CLIENT_MAY_WITHOUT_WAIT");
                if (run.may == null) { run.mayTick = run.ticks; }
                run.may = state; // A retained same-object notification does not reset neutral timing.
            } else if (state.outcome() == Outcome.COMPLETED) {
                require(run.may != null && matches(run.second, state), "METADATA_H_CLIENT_WRONG_COMPLETION");
                run.completed = state;
            }
        } catch (RuntimeException | Error observerFailure) { fail(run, "METADATA_H_CLIENT_STATE_OBSERVER"); }
    }

    /** Last RETURN follows the original current-listener send; no early guard-return is counted. */
    public static void submitted(Object value) {
        var run = active;
        if (run == null || !(value instanceof Request request) || request.command() != Command.TRY) { return; }
        try {
            require(Minecraft.getInstance().isSameThread() && currentPlay(Minecraft.getInstance(), run)
                    && request.scope() == Scope.PLAY && request.kind() == Kind.DEATH
                    && run.binding != null && sameScene(request, run.binding), "METADATA_H_CLIENT_SEND_OWNER");
            if (++run.submits == 1) { run.first = request; }
            else if (run.submits == 2) {
                require(run.may != null && request.requestSeq() > run.first.requestSeq(),
                        "METADATA_H_CLIENT_SEND_NOT_FRESH");
                run.second = request;
            } else { fail(run, "METADATA_H_CLIENT_EXTRA_TRY"); }
        } catch (RuntimeException | Error observerFailure) { fail(run, "METADATA_H_CLIENT_SEND_OBSERVER"); }
    }

    static void release() { active = null; }
    private static boolean currentPlay(Minecraft minecraft, Run run) {
        return minecraft.player != null && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == run.connection
                && run.connection.getPacketListener() == minecraft.getConnection()
                && minecraft.getConnection().protocol() == ConnectionProtocol.PLAY;
    }
    private static long frames(Run run) {
        return P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN);
    }
    private static boolean matches(Request request, State state) {
        return request != null && state != null && sameScene(request, state)
                && request.requestSeq() == state.requestSeq();
    }
    private static boolean sameScene(Request request, State state) {
        return request.scope() == state.scope() && request.connectionEpoch() == state.connectionEpoch()
                && request.sceneSerial() == state.sceneSerial() && request.actorGeneration() == state.actorGeneration()
                && request.kind() == state.kind();
    }
    private static void fail(Run run, String code) { if (run.failure == null) { run.failure = code; } }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private static final class Run {
        final Connection connection; final Path output; final long beforeFrames;
        int ticks, mayTick, submits;
        boolean deathClicked, retryClicked, preClickRecorded;
        State binding, wait, may, completed;
        Request first, second;
        String failure;
        P11C4aClientInputProbe.Snapshot neutral;
        Run(Connection connection, Path output) {
            this.connection = connection; this.output = output; beforeFrames = frames(this);
        }
    }
}
