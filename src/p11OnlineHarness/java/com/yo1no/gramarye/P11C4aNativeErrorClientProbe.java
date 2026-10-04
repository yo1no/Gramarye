package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** One original unlocked DeathScreen button; no retry or synthetic state is sent. */
@OnlyIn(Dist.CLIENT)
public final class P11C4aNativeErrorClientProbe {
    private static Run active;
    private P11C4aNativeErrorClientProbe() { }
    static void start(Minecraft minecraft, Connection connection, Path output, P11C4aNativeErrorProbe.Mode mode)
            throws IOException {
        require(active == null && minecraft.isSameThread() && minecraft.player != null && minecraft.level != null
                && minecraft.getConnection() != null && minecraft.getConnection().getConnection() == connection
                && connection.isConnected(), "ERROR_CLIENT_REAL_PLAY");
        active = new Run(connection, output, mode);
        P11C4aErrorStatusClientProbe.start(connection, output, mode);
        P11C4aEvidence.write(output, "native-error-armed.json", java.util.Map.of("status", "ARMED_ORIGINAL_SENDER_ONLY", "mode", mode.name()));
    }
    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && run.failure == null && ++run.ticks <= 2400,
                "ERROR_CLIENT_OBSERVER_OR_DEADLINE");
        boolean statusExperimentComplete = P11C4aErrorStatusClientProbe.tick(minecraft);
        var state = P11ClientTransitions.view();
        if (!run.clicked && state != null && state.scope() == Scope.PLAY && state.kind() == Kind.DEATH
                && state.outcome() == Outcome.BINDING && state.requestSeq() == 0) {
            // Actual screen/button semantics, deliberately no stale local-health prerequisite.
            run.clicked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.DEATH_RESPAWN);
        }
        boolean disconnected = !run.connection.isConnected() && minecraft.screen instanceof DisconnectedScreen
                && minecraft.player == null && minecraft.level == null;
        boolean nonRetryable = state != null && run.request != null && same(run.request, state)
                && (state.outcome() == Outcome.FAULT || state.outcome() == Outcome.UNKNOWN)
                && state.availability() == Availability.DISABLED;
        // Server evidence proves policy; this is only actual client observation, never a replacement.
        if (!statusExperimentComplete || !disconnected && !nonRetryable) { return false; }
        require(run.clicked && run.sends == 1 && run.completed == 0 && run.notStarted == 0,
                "ERROR_CLIENT_FALSE_COMPLETION_OR_REPLAY");
        long frames = P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN) - run.beforeFrames;
        require(frames == P11C4aNativeErrorProbe.expectedFrames(run.mode),
                "ERROR_ACTUAL_CLIENT_FRAME_COUNT_NOT_PROVEN");
        if (!run.finished) {
            run.finished=true;
            var values = new LinkedHashMap<String,Object>();
            values.put("status", "ACTUAL_NATIVE_ERROR_CLIENT_NO_COMPLETION_OR_RETRY"); values.put("mode", run.mode.name());
            values.put("originalProductionTrySendReturns", run.sends); values.put("actualRequest", run.request);
            values.put("completedMatchingStates", run.completed); values.put("notStartedMatchingStates", run.notStarted);
            values.put("actualRespawnHandlerReturns", frames); values.put("actualCurrentState", state);
            if (run.mode == P11C4aNativeErrorProbe.Mode.COMPLETE_B_FAULT) {
                values.put("actualAcceptedFrameBeforeFault", run.fullBFrame);
                values.put("actualAcceptedFaultBeforeReadout", run.fullBFault);
            }
            values.put("actualNativeDisconnectedScreenAndClosedConnection", disconnected);
            values.put("input", "ORIGINAL_WINDOW_MOUSE_CALLBACK"); values.put("physicalInputOrVisualAppearanceClaimed", false);
            values.put("fullC4aAcceptance", false);
            P11C4aEvidence.write(run.output, "native-error-client.json", values);
        }
        return true;
    }
    public static void submitted(Object value) {
        P11C4aCompleteBFaultClientProbe.submitted(value);
        var run=active; if(run==null || !(value instanceof Request request) || request.command()!=Command.TRY) { return; }
        if(request.kind()!=Kind.DEATH || request.scope()!=Scope.PLAY || ++run.sends!=1) {run.failure="ERROR_CLIENT_EXTRA_TRY";return;}
        run.request=request;
    }
    public static void received(Object value, Connection connection) {
        var run=active;
        if(run==null || connection!=run.connection || !(value instanceof State state) || !same(run.request,state)) {return;}
        if(state.outcome()==Outcome.COMPLETED) {run.completed++;}
        if(state.outcome()==Outcome.NOT_STARTED) {run.notStarted++;}
        // This mode needs actual accepted same-request history, not the aggregate outcome counters.
        if (run.mode == P11C4aNativeErrorProbe.Mode.COMPLETE_B_FAULT
                && state.equals(P11ClientTransitions.view())) {
            if (state.outcome() == Outcome.NATIVE_FRAME && run.fullBFrame == null) {
                run.fullBFrame = state;
            }
            if (state.outcome() == Outcome.FAULT && run.fullBFault == null
                    && fullBTerminalMatches(run.request, run.fullBFrame, state, state)
                    && P11C4aNativeObservations.count(connection,
                            P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN) - run.beforeFrames == 1) {
                run.fullBFault = state;
            }
        }
    }
    static void release() { P11C4aErrorStatusClientProbe.release(); active=null; }
    static boolean terminalCountersUnchanged() {
        var run = active;
        return run != null && run.finished && run.failure == null && run.clicked
                && run.sends == 1 && run.completed == 0 && run.notStarted == 0;
    }
    /** Read-only continuation guard. It cannot create a request, state, UI action, or live permit. */
    static boolean completeBFaultLeaveAllowed(Connection exact, State current) {
        var run = active;
        return run != null && run.mode == P11C4aNativeErrorProbe.Mode.COMPLETE_B_FAULT
                && run.connection == exact && terminalCountersUnchanged()
                && P11C4aNativeObservations.count(exact,
                        P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN) - run.beforeFrames == 1
                && fullBTerminalMatches(run.request, run.fullBFrame, run.fullBFault, current);
    }
    static boolean fullBTerminalMatches(Request request, State frame, State fault, State current) {
        if (request == null || request.command() != Command.TRY || request.scope() != Scope.PLAY
                || request.kind() != Kind.DEATH || frame == null || fault == null || current == null
                || !same(request, frame) || !same(request, fault) || !same(request, current)
                || frame.outcome() != Outcome.NATIVE_FRAME || frame.availability() != Availability.DISABLED
                || frame.reason() != Reason.NONE || frame.targetActorGeneration() <= 0
                || fault.outcome() != Outcome.FAULT || fault.availability() != Availability.DISABLED
                || fault.reason() != Reason.NATIVE_FAILURE || fault.targetActorGeneration() != 0
                || fault.statusVersion() <= frame.statusVersion()) { return false; }
        return current.equals(fault) || current.outcome() == Outcome.UNKNOWN
                && current.availability() == Availability.DISABLED
                && current.reason() == Reason.STATUS_UNAVAILABLE && current.targetActorGeneration() == 0
                && current.statusVersion() > fault.statusVersion();
    }
    private static boolean same(Request request,State state) {
        return request!=null && request.connectionEpoch()==state.connectionEpoch() && request.sceneSerial()==state.sceneSerial()
                && request.actorGeneration()==state.actorGeneration() && request.requestSeq()==state.requestSeq()
                && request.kind()==state.kind() && request.scope()==state.scope();
    }
    private static void require(boolean value,String code) {P11C4aEvidence.require(value,code);}
    private static final class Run {
        final Connection connection; final Path output; final P11C4aNativeErrorProbe.Mode mode; final long beforeFrames;
        Request request; State fullBFrame, fullBFault; String failure; int ticks,sends,completed,notStarted; boolean clicked,finished;
        Run(Connection connection,Path output,P11C4aNativeErrorProbe.Mode mode) {
            this.connection=connection;this.output=output;this.mode=mode;
            beforeFrames=P11C4aNativeObservations.count(connection,P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN);
        }
    }
}
