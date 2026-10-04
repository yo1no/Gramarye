package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

/** Client-only excluded finite input phase. Accepted native states, not cue files, are the evidence. */
public final class P11C4aReloadClientProbe {
    private static Run active;
    private P11C4aReloadClientProbe() { }

    /** Existing client owner calls only after observing its fixed server cue. */
    static void start(Minecraft minecraft, Connection connection, P11C4aReloadBlockerProbe.Mode mode, Path output) {
        require(P11C4aEvidence.enabled() && minecraft.isSameThread() && active == null
                && minecraft.getConnection() != null && minecraft.getConnection().getConnection() == connection
                && connection.isConnected(), "RELOAD_CLIENT_START_OWNER");
        active = new Run(connection, mode, output);
    }

    /** Each successful native callback is invoked once; no automatic retry controller method is used. */
    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && ++run.ticks <= 2400, "RELOAD_CLIENT_PHASE_DEADLINE");
        require(run.failure == null && run.connection.isConnected() && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == run.connection, "RELOAD_CLIENT_CONNECTION_OR_OBSERVER");
        if (P11C4aNativeSenderProbe.selected()) { P11C4aNativeSenderClientProbe.releaseCombatKill(minecraft); }
        if (heldUi(run)) {
            // UI_HELD never enters the callback driver below. The original sender observations
            // still own both request identities; only the external OS driver supplies Enter.
            if (!run.heldComplete) {
                if (!P11C4aUiHeldInputProbe.tick(minecraft)) { return false; }
                run.heldComplete = true;
            }
            require(run.firstSubmit != null && run.retrySubmit != null && run.submits == 2,
                    "RELOAD_HELD_MISSING_ORIGINAL_SENDERS");
            run.deathClicked = true;
            run.retryClicked = true;
            run.retrySeq = run.retrySubmit.requestSeq();
        }
        if (!run.deathClicked) {
            run.deathClicked = P11C4aNativeSenderProbe.selected() ? run.firstSubmit != null : callbackUi(run)
                    ? P11C4aUiInputProbe.firstClicked()
                    : P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.DEATH_RESPAWN);
            return false;
        }
        if (P11C4aNativeSenderProbe.selected()
                && !P11C4aNativeSenderClientProbe.inspectHidden(minecraft, run.waitState, run.mayState)) { return false; }
        var view = P11ClientTransitions.view();
        if (run.mayState != null && !run.retryClicked) {
            require(run.waitState != null && view != null && view == run.mayState
                    && view.outcome() == Outcome.NOT_STARTED && view.availability() == Availability.MAY_TRY,
                    "RELOAD_CLIENT_MAY_NOT_CURRENT");
            if (run.mode == P11C4aReloadBlockerProbe.Mode.FOP && !P11C4aNativeSenderProbe.selected()
                    && !inspectUi(minecraft, run)) { return false; }
            if (callbackUi(run) && !P11C4aUiInputProbe.inspectMay(minecraft)) { return false; }
            if (run.ticks <= run.mayAtTick + 1) { return false; }
            var input = P11C4aClientInputProbe.snapshot(minecraft);
            if (!input.glfwActivationNeutral() || !P11ClientTransitions.retryArmed()) { return false; }
            // The actual GLFW neutral sample is independent of the injected callback. This is
            // not evidence of a physically held/released OS key or of visual screen rendering.
            run.neutralBeforeRetry = input;
            run.retryClicked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.RETRY);
            if (run.retryClicked) {
                var submitted = run.retrySubmit;
                require(submitted != null && submitted.connectionEpoch() == run.mayState.connectionEpoch()
                        && submitted.sceneSerial() == run.mayState.sceneSerial()
                        && submitted.actorGeneration() == run.mayState.actorGeneration()
                        && submitted.requestSeq() > run.mayState.requestSeq(), "RELOAD_RETRY_CALLBACK_DID_NOT_CREATE_NEW_N");
                run.retrySeq = submitted.requestSeq();
            }
            return false;
        }
        if (run.completed == null) { return false; }
        require(run.retryClicked && run.submits == 2 && run.waitState != null && run.mayState != null && view == run.completed
                && run.completed.requestSeq() > run.waitState.requestSeq()
                && run.completed.requestSeq() == run.retrySeq
                && run.completed.connectionEpoch() == run.waitState.connectionEpoch()
                && run.completed.sceneSerial() == run.waitState.sceneSerial()
                && run.completed.actorGeneration() == run.waitState.actorGeneration()
                && P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN)
                    == run.beforeFrames + 1 && minecraft.player != null && !minecraft.player.isDeadOrDying(),
                "RELOAD_CLIENT_MANUAL_RETRY_NOT_EXACT");
        if (callbackUi(run) && !P11C4aUiInputProbe.finish()) { return false; }
        if (P11C4aNativeSenderProbe.selected()) { P11C4aNativeSenderClientProbe.finishImmediate(); }
        var values = new LinkedHashMap<String, Object>();
        values.put("status", "ACTUAL_ACCEPTED_REFUSAL_MAY_FRESH_NATIVE_RETRY_AND_RESPAWN");
        values.put("mode", run.mode.name()); values.put("wait", run.waitState); values.put("mayTry", run.mayState);
        values.put("completed", run.completed); values.put("nativeRespawnDelta", 1); values.put("ticks", run.ticks);
        values.put("originalSenderReturnedSeqAfterNativeCallback", run.retrySeq);
        values.put("originalSenderTryReturns", run.submits); values.put("mayObservedAtProbeTick", run.mayAtTick);
        values.put("beforeRetryInput", run.neutralBeforeRetry); values.put("afterInput", P11C4aClientInputProbe.snapshot(minecraft));
        values.put("inputKind", heldUi(run) ? "TARGETED_OS_INJECTION_OBSERVED_BY_ORIGINAL_GLFW_KEYBOARD_PATH"
                : "CONTROLLED_ORIGINAL_WINDOW_CALLBACK_AFTER_OBSERVED_GLFW_NEUTRAL");
        values.put("physicalHeldKeyClaimed", false); values.put("visualAppearanceClaimed", false);
        values.put("osInjectedGlfwHeldNeutralFreshRetrySubset", heldUi(run) && run.heldComplete);
        values.put("statusLeaveStayNativeCallbacksWithoutRespawn", run.uiStage == 4);
        values.put("statusFrameCapture", P11C4aScreenEvidence.status("status-may"));
        values.put("leaveFrameCapture", P11C4aScreenEvidence.status("leave-confirm"));
        values.put("uiCallbackNative20ResizeDedupeAndActualCastConsumerSubset", callbackUi(run));
        values.put("uiCallbackComponentPresentationFaultNativeRebindFocusAndPauseMenuSubset", callbackUi(run));
        values.put("normalNativeHiddenHistoryOrPhysicalHeldOrPortalOrOsFocusLossOrUnpublishedPauseClaimed", false);
        values.put("fullC4aAcceptance", false);
        values.put("immediateAndHiddenAutoNativeCallsiteSubset", P11C4aNativeSenderProbe.selected());
        P11C4aEvidence.write(run.output, run.label + ".json", values);
        active = null;
        return true;
    }

    private static boolean inspectUi(Minecraft minecraft, Run run) throws IOException {
        require(run.submits == 1 && P11C4aNativeObservations.count(run.connection,
                P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN) == run.beforeFrames,
                "RELOAD_UI_STAY_OR_RENDER_TRIGGERED_RESPAWN");
        switch (run.uiStage) {
            case 0 -> {
                require(P11C4aScreenEvidence.request("status-may"), "RELOAD_STATUS_CAPTURE_NOT_QUEUED");
                run.uiStage = 1;
            }
            case 1 -> {
                if (!captured("status-may")) { return false; }
                require(!P11C4aClientInputProbe.snapshot(minecraft).screenPauses(), "RELOAD_STATUS_SCREEN_PAUSES");
                if (P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.STATUS_ESCAPE)) { run.uiStage = 2; }
            }
            case 2 -> {
                require(P11C4aScreenEvidence.request("leave-confirm"), "RELOAD_LEAVE_CAPTURE_NOT_QUEUED");
                run.uiStage = 3;
            }
            case 3 -> {
                if (!captured("leave-confirm")) { return false; }
                require(!P11C4aClientInputProbe.snapshot(minecraft).screenPauses(), "RELOAD_LEAVE_SCREEN_PAUSES");
                if (P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.LEAVE_STAY)) { run.uiStage = 4; }
            }
            case 4 -> { return true; }
            default -> throw new IllegalStateException("RELOAD_UI_STAGE_INVALID");
        }
        return false;
    }

    private static boolean captured(String label) {
        var status = P11C4aScreenEvidence.status(label);
        require(!status.equals("FAILED"), "RELOAD_GAME_FRAMEBUFFER_CAPTURE_FAILED");
        return status.equals("CAPTURED_VISUAL_ONLY");
    }

    /** Exact production handler TAIL, additionally require this value actually became current. */
    public static void accepted(Object value, Connection connection) {
        var run = active;
        if (run == null || connection != run.connection || !(value instanceof State update)
                || P11ClientTransitions.view() != update || update.kind() != Kind.DEATH) { return; }
        try {
            if (update.outcome() == Outcome.NOT_STARTED && update.availability() == Availability.WAIT_NOTIFY) {
                require(update.reason() == (run.mode == P11C4aReloadBlockerProbe.Mode.FOP
                        ? Reason.ACTIVE_OPERATION : Reason.ACTIVE_CONTEXT)
                        && run.firstSubmit != null && run.firstSubmit.requestSeq() == update.requestSeq()
                        && run.firstSubmit.connectionEpoch() == update.connectionEpoch()
                        && run.firstSubmit.sceneSerial() == update.sceneSerial()
                        && run.firstSubmit.actorGeneration() == update.actorGeneration()
                        && P11C4aNativeObservations.count(connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN)
                            == run.beforeFrames, "RELOAD_CLIENT_WRONG_REFUSAL");
                if (run.waitState == null) { run.waitState = update; }
            } else if (update.outcome() == Outcome.NOT_STARTED && update.availability() == Availability.MAY_TRY) {
                require(run.waitState != null && sameAttempt(run.waitState, update), "RELOAD_CLIENT_MAY_WITHOUT_WAIT");
                if (run.mayState == null) { run.mayAtTick = run.ticks; }
                run.mayState = update;
            } else if (update.outcome() == Outcome.COMPLETED) { run.completed = update; }
        } catch (RuntimeException | Error observerFailure) { run.failure = "RELOAD_CLIENT_ACCEPTED_STATE_OBSERVER"; }
    }

    /** TAIL after the real client's private send path invoked the current native listener. */
    public static void submitted(Object value) {
        P11C4aNativeSenderClientProbe.submitted(value);
        var run = active;
        if (run == null || !(value instanceof Request request) || request.command() != Command.TRY
                || request.kind() != Kind.DEATH) { return; }
        if (++run.submits == 1) { run.firstSubmit = request; }
        else if (run.submits == 2) { run.retrySubmit = request; }
        else { run.failure = "RELOAD_CLIENT_MORE_THAN_TWO_TRY_SUBMISSIONS"; }
    }

    static void release() { active = null; P11C4aUiInputProbe.abort(); P11C4aUiHeldInputProbe.abort(); }

    private static boolean heldUi(Run run) {
        return run.mode == P11C4aReloadBlockerProbe.Mode.FOP
                && P11C4aScenario.MODE == P11C4aScenario.Mode.UI_HELD;
    }

    private static boolean callbackUi(Run run) {
        return run.mode == P11C4aReloadBlockerProbe.Mode.FOP
                && P11C4aScenario.MODE == P11C4aScenario.Mode.UI_CALLBACK;
    }

    private static boolean sameAttempt(State first, State next) {
        return first.connectionEpoch() == next.connectionEpoch() && first.sceneSerial() == next.sceneSerial()
                && first.actorGeneration() == next.actorGeneration() && first.requestSeq() == next.requestSeq();
    }

    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }

    private static final class Run {
        final Connection connection; final P11C4aReloadBlockerProbe.Mode mode; final Path output; final String label;
        final long beforeFrames;
        int ticks, mayAtTick, submits, uiStage;
        long retrySeq;
        boolean deathClicked, retryClicked, heldComplete;
        State waitState, mayState, completed;
        Request firstSubmit, retrySubmit;
        String failure;
        P11C4aClientInputProbe.Snapshot neutralBeforeRetry;
        Run(Connection connection, P11C4aReloadBlockerProbe.Mode mode, Path output) {
            this.connection = connection; this.mode = mode; this.output = output;
            label = mode == P11C4aReloadBlockerProbe.Mode.FOP ? "reload-fop" : "reload-qctx";
            beforeFrames = P11C4aNativeObservations.count(connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN);
        }
    }
}
