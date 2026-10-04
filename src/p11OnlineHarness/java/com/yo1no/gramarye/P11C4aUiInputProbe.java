package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;

/** EXTERNAL DRAFT: finite excluded callback fixture, not an OS-input or server-authority producer. */
public final class P11C4aUiInputProbe {
    private static Run active;
    private P11C4aUiInputProbe() { }

    /** Must precede the server's new genuine death; never attach late to tick >= 19. */
    static void start(Minecraft minecraft, Connection connection, Path output) {
        var current = P11ClientTransitions.view();
        require(P11C4aEvidence.enabled() && active == null && !P11C4aUiHeldInputProbe.isActive() && minecraft.isSameThread()
                && minecraft.getConnection() != null && minecraft.getConnection().getConnection() == connection
                && connection.isConnected() && current != null && current.outcome() == Outcome.COMPLETED
                && !P11ClientTransitions.blocksCast(), "UI_INPUT_START_OWNER");
        int mappings = 0;
        for (var mapping : minecraft.options.keyMappings) {
            if (mapping.getName().equals("key.gramarye.cast")) {
                require(mapping.getKey().getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                        && mapping.getKey().getValue() == GLFW.GLFW_KEY_R
                        && mapping.getKeyModifier() == net.neoforged.neoforge.client.settings.KeyModifier.NONE,
                        "UI_INPUT_BASELINE_R_MAPPING_NOT_EXACT");
                mappings++;
            }
        }
        require(mappings == 1, "UI_INPUT_CAST_MAPPING_NOT_UNIQUE");
        active = new Run(connection, current.connectionEpoch(), current.sceneSerial(), output);
        P11C4aUiContextProbe.start(connection, output);
        // Native focus request for this owned game window, not a forged gate value.
        GLFW.glfwFocusWindow(minecraft.getWindow().getWindow());
    }

    /** Native DeathScreen.tick TAIL. delayTicker is read-only: never seeded or advanced here. */
    public static void deathTick(DeathScreen screen, int nativeTicker) {
        var run = active;
        if (run == null || run.firstClicked || nativeTicker > 20) { return; }
        try {
            var minecraft = owner(run);
            var state = P11ClientTransitions.view();
            if (minecraft.screen != screen || state == null || state.kind() != Kind.DEATH
                    || state.connectionEpoch() != run.epoch || state.sceneSerial() <= run.sceneFloor) { return; }
            require(state.outcome() == Outcome.BINDING && state.requestSeq() == 0 && run.trySends == 0,
                    "UI_INPUT_DEATH_ALREADY_TRIGGERED");
            if (nativeTicker == 19) {
                require(!P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.DEATH_RESPAWN),
                        "UI_INPUT_NATIVE_BUTTON_UNLOCKED_EARLY");
                run.tick19 = true;
            } else if (nativeTicker == 20) {
                require(run.tick19, "UI_INPUT_NATIVE_TICK19_NOT_OBSERVED");
                run.scene = state.sceneSerial();
                run.actor = state.actorGeneration();
                run.insideUnlock = true;
                try {
                    run.firstClicked = P11C4aClientInputProbe.act(minecraft,
                            P11C4aClientInputProbe.Action.DEATH_RESPAWN);
                } finally { run.insideUnlock = false; }
                require(run.firstClicked && run.trySends == 1 && run.firstAtUnlock,
                        "UI_INPUT_TICK20_DID_NOT_SEND_EXACTLY_ONCE");
            }
        } catch (RuntimeException | Error failure) { run.failure = "UI_INPUT_UNLOCK_OBSERVER"; }
    }

    static boolean firstClicked() { return active != null && active.firstClicked; }
    static boolean isActive() { return active != null; }

    /** Called by the existing reload probe instead of adding a second first sender. */
    static boolean inspectMay(Minecraft minecraft) {
        var run = checked();
        owner(run);
        require(++run.probeTicks <= 2400 && minecraft == Minecraft.getInstance(), "UI_INPUT_DEADLINE");
        var state = P11ClientTransitions.view();
        require(run.waitSeen && state != null && state.outcome() == Outcome.NOT_STARTED
                && state.availability() == Availability.MAY_TRY && sameAttempt(run, state)
                && run.trySends == 1 && run.castSends == 0, "UI_INPUT_MAY_OWNER");
        switch (run.uiStep) {
            case 0 -> {
                require(minecraft.screen instanceof P11ClientTransitionScreen
                        && !minecraft.screen.isPauseScreen(), "UI_INPUT_STATUS_NOT_NONPAUSING");
                require(P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.RESIZE_SCREEN),
                        "UI_INPUT_RESIZE_FAILED");
            }
            case 1 -> {
                Screen exact = minecraft.screen;
                require(exact instanceof P11ClientTransitionScreen, "UI_INPUT_INIT_SCREEN");
                exact.init(minecraft, exact.width, exact.height); // Real public init/reposition/rebuild path.
                require(minecraft.screen == exact, "UI_INPUT_INIT_CHANGED_SCREEN");
            }
            case 2 -> {
                require(P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.HIDE_PENDING_SCREEN)
                        && minecraft.screen instanceof P11ClientTransitionScreen,
                        "UI_INPUT_NULL_REQUEST_NOT_RESTORED");
                run.nullRestored = true; // Does NOT claim an invisible native P9 tick.
            }
            case 3 -> {
                require(minecraft.player != null, "UI_INPUT_NO_OLD_NATIVE_PLAYER");
                minecraft.player.respawn(); // Original transformed LocalPlayer entry, not controller.retry.
            }
            case 4 -> {
                focusRetry(minecraft);
                for (int i = 0; i < 3; i++) { key(minecraft, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_REPEAT); }
                key(minecraft, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_RELEASE);
                run.repeatCallbacks = 3; // No PRESS and no physical-held claim.
            }
            case 5 -> {
                require(P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.STATUS_ESCAPE)
                        && minecraft.screen instanceof P11ClientLeaveScreen
                        && !minecraft.screen.isPauseScreen(), "UI_INPUT_LEAVE_CONFIRM_NOT_NONPAUSING");
            }
            case 6 -> {
                require(P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.RESIZE_SCREEN),
                        "UI_INPUT_LEAVE_RESIZE");
                Screen exact = minecraft.screen;
                exact.init(minecraft, exact.width, exact.height);
                require(exact == minecraft.screen && exact instanceof P11ClientLeaveScreen
                        && !exact.isPauseScreen(), "UI_INPUT_LEAVE_INIT");
            }
            case 7 -> {
                require(P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.LEAVE_STAY)
                        && minecraft.screen instanceof P11ClientTransitionScreen, "UI_INPUT_STAY");
            }
            case 8 -> {
                require(P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.CAST_PRESS)
                        && P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.CAST_REPEAT)
                        && P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.CAST_RELEASE),
                        "UI_INPUT_PENDING_R_CALLBACKS");
                run.pendingCastTick = run.castTicks;
                P11C4aUiContextProbe.armHidden(state);
            }
            default -> {
                if (run.castTicks < run.pendingCastTick + 2 || run.statusSends < 2
                        || !P11C4aUiContextProbe.hiddenDone()) { return false; }
                require(run.statusInterval == 20 && run.trySends == 1 && run.castSends == 0,
                        "UI_INPUT_DEDUPE_OR_STATUS_INTERVAL");
                run.uiComplete = true;
                return true; // Existing reload probe owns the one fresh Retry callback.
            }
        }
        run.uiStep++;
        require(run.trySends == 1 && run.castSends == 0 && sameAttempt(run, P11ClientTransitions.view()),
                "UI_INPUT_PRESENTATION_GENERATED_TRY_OR_CAST");
        return false;
    }

    /** Product tick HEAD; counts real client ticks, never invokes an extra tick. */
    public static void controllerTick() { if (active != null) { active.controllerTicks++; } }

    /** Exact private send TAIL. No packet is constructed or submitted by this observer. */
    public static void submitted(Object value) {
        var run = active;
        if (run == null || !(value instanceof Request request) || request.scope() != Scope.PLAY
                || request.connectionEpoch() != run.epoch || request.sceneSerial() != run.scene
                || request.actorGeneration() != run.actor || request.kind() != Kind.DEATH) { return; }
        try {
            owner(run);
            if (request.command() == Command.TRY) {
                require(++run.trySends <= 2, "UI_INPUT_DUPLICATE_TRY");
                if (run.trySends == 1) {
                    require(run.insideUnlock, "UI_INPUT_FIRST_TRY_NOT_NATIVE_UNLOCK");
                    run.firstAtUnlock = true; run.firstSeq = request.requestSeq();
                } else { require(run.uiComplete && request.requestSeq() > run.firstSeq, "UI_INPUT_UNPLANNED_RETRY"); }
            } else if (request.command() == Command.STATUS && run.trySends == 1) {
                require(request.requestSeq() == run.firstSeq, "UI_INPUT_STATUS_CHANGED_N");
                if (run.statusSends > 0) {
                    run.statusInterval = run.controllerTicks - run.lastStatusTick;
                    require(run.statusInterval == 20, "UI_INPUT_STATUS_NOT_TWENTY_REAL_TICKS");
                }
                run.statusSends++; run.lastStatusTick = run.controllerTicks;
            }
        } catch (RuntimeException | Error failure) { run.failure = "UI_INPUT_SUBMIT_OBSERVER"; }
    }

    public static void accepted(Object value, Connection connection) {
        var run = active;
        if (run == null || connection != run.connection || !(value instanceof State state)
                || P11ClientTransitions.view() != state) { return; }
        settledIfReady();
        if (!sameAttempt(run, state)) { return; }
        if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.WAIT_NOTIFY
                && state.reason() == Reason.ACTIVE_OPERATION) { run.waitSeen = true; }
    }

    /** Also observed at nativeFrameApplied TAIL, since either frame or terminal can arrive last. */
    public static void settledIfReady() {
        var run = active;
        if (run == null || run.settled || run.failure != null || !run.uiComplete || run.trySends != 2) { return; }
        var current = P11ClientTransitions.view();
        if (current != null && current.connectionEpoch() == run.epoch && current.sceneSerial() == run.scene
                && current.kind() == Kind.DEATH && current.outcome() == Outcome.COMPLETED
                && !P11ClientTransitions.blocksCast()) { run.settled = true; }
    }

    /** Read-only P9 owner fields; R callbacks are inserted immediately before its real scheduled consumer. */
    public static void beforeCastTick(boolean registered, boolean session, boolean pendingFlush) {
        var run = active;
        if (run == null || run.failure != null) { return; }
        try {
            var minecraft = owner(run);
            run.castTicks++;
            if (!run.settled || !registered || !session || minecraft.screen != null
                    || minecraft.player == null || minecraft.player.isDeadOrDying()
                    || !minecraft.isWindowActive() || minecraft.isPaused() || P11ClientTransitions.blocksCast()) { return; }
            if (run.castPhase == 0) {
                // Requires the actual successor-context flush. Never sets/reset this field.
                require(pendingFlush, "UI_INPUT_MISSED_NATIVE_RECOVERY_FLUSH");
                require(P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.CAST_CLICK),
                        "UI_INPUT_RECOVERY_R_CALLBACK");
                run.castPhase = 1;
            } else if (run.castPhase == 2) {
                require(!pendingFlush, "UI_INPUT_RECOVERY_STILL_FLUSHING");
                run.castPhase = 3; // One full eligible native tick with no input.
            } else if (run.castPhase == 4) {
                require(!pendingFlush && P11C4aClientInputProbe.act(minecraft,
                        P11C4aClientInputProbe.Action.CAST_CLICK), "UI_INPUT_FRESH_R_CALLBACK");
                run.castPhase = 5;
            } else if (run.castPhase == 6) {
                require(!pendingFlush && run.castSends == 1, "UI_INPUT_FRESH_R_REPLAYED");
                run.castPhase = 7;
            }
        } catch (RuntimeException | Error failure) { run.failure = "UI_INPUT_CAST_TICK_BEFORE"; }
    }

    public static void afterCastTick() {
        var run = active;
        if (run == null) { return; }
        if (run.castPhase == 1) {
            if (run.castSends != 0 || run.recoveryDrops != 1) { run.failure = "UI_INPUT_RECOVERY_DID_NOT_DROP_ONE"; }
            run.castPhase = 2;
        } else if (run.castPhase == 3) {
            if (run.castSends != 0) { run.failure = "UI_INPUT_OLD_R_REPLAYED"; }
            run.castPhase = 4;
        } else if (run.castPhase == 5) {
            if (run.castSends != 1) { run.failure = "UI_INPUT_FRESH_R_NOT_EXACTLY_ONE"; }
            run.castPhase = 6;
        }
    }

    public static void droppedClick() {
        if (active != null && active.castPhase == 1) { active.recoveryDrops++; }
    }

    public static void castSubmitted(long sequence, int slot, int mask, boolean noHints) {
        if (P11C4aUiContextProbe.ownsPostCasts()) {
            P11C4aUiContextProbe.cast(sequence, slot, mask, noHints); return;
        }
        var run = active;
        if (run == null) { return; }
        if (++run.castSends != 1 || run.castPhase != 5 || sequence <= 0 || slot != 0 || mask != 0 || !noHints) {
            run.failure = "UI_INPUT_UNEXPECTED_REAL_CAST_SEND";
        }
        run.freshCastSequence = sequence;
    }

    static boolean finish() throws IOException {
        var run = checked(); owner(run);
        if (run.castPhase != 7) { return false; }
        if (!P11C4aUiContextProbe.finish(run.freshCastSequence)) { return false; }
        var values = new LinkedHashMap<String, Object>();
        values.put("status", "ACTUAL_CALLBACK_AND_NATIVE_CONSUMER_SUBSET");
        values.put("exactNativeUnlockTick", 20); values.put("tick19DisabledObserved", run.tick19);
        values.put("trySends", run.trySends); values.put("statusPollIntervalRealTicks", run.statusInterval);
        values.put("resizeInitNullRestoreAndDuplicateNativeSender", run.uiComplete && run.nullRestored);
        values.put("repeatCallbacksWithoutPress", run.repeatCallbacks);
        values.put("statusLeaveStayNonPausing", run.uiComplete);
        values.put("pendingCastSends", 0); values.put("recoveryQueuedRNativeDrops", run.recoveryDrops);
        values.put("freshCastSends", run.castSends); values.put("freshCastSequence", run.freshCastSequence);
        values.put("physicalHeldKeyClaimed", false); values.put("hiddenPendingNativeTickClaimed", true);
        values.put("hiddenPendingWasOneConsumerPresentationFault", true);
        values.put("normalNativeHiddenHistoryClaimed", false);
        values.put("nativeOwnedWindowFocusRequestedAtArm", true);
        values.put("portalOrPhysicalFocusOrActualPauseClaimed", false);
        values.put("serverCastAcceptanceClaimed", false); values.put("fullC4aAcceptance", false);
        P11C4aEvidence.write(run.output, "ui-input-callback-subset.json", values);
        active = null;
        return true;
    }

    static void abort() { P11C4aUiContextProbe.abort(); active = null; }

    static java.util.Map<String, Object> pending() {
        var run = active;
        return run == null ? java.util.Map.of("active", false) : java.util.Map.of(
                "active", true, "firstClicked", run.firstClicked, "uiStep", run.uiStep,
                "castPhase", run.castPhase, "trySends", run.trySends, "castSends", run.castSends,
                "context", P11C4aUiContextProbe.pending(),
                "failure", run.failure == null ? "NONE" : run.failure);
    }

    private static void focusRetry(Minecraft minecraft) {
        for (int i = 0; i < 3; i++) {
            if (minecraft.screen.getFocused() instanceof Button button && button.active
                    && button.getMessage().getContents() instanceof TranslatableContents text
                    && text.getKey().equals("screen.gramarye.transition.retry")) { return; }
            key(minecraft, GLFW.GLFW_KEY_TAB, GLFW.GLFW_PRESS);
            key(minecraft, GLFW.GLFW_KEY_TAB, GLFW.GLFW_RELEASE);
        }
        throw new IllegalStateException("UI_INPUT_ORIGINAL_TAB_DID_NOT_FOCUS_RETRY");
    }

    private static void key(Minecraft minecraft, int key, int action) {
        minecraft.keyboardHandler.keyPress(minecraft.getWindow().getWindow(), key,
                GLFW.glfwGetKeyScancode(key), action, 0);
    }

    private static Run checked() {
        var run = active;
        require(run != null && run.failure == null, "UI_INPUT_ABSENT_OR_FAILED");
        return run;
    }

    private static Minecraft owner(Run run) {
        var minecraft = Minecraft.getInstance();
        require(minecraft.isSameThread() && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == run.connection && run.connection.isConnected(),
                "UI_INPUT_EXACT_CLIENT_OWNER");
        return minecraft;
    }

    private static boolean sameAttempt(Run run, State state) {
        return state != null && state.scope() == Scope.PLAY && state.kind() == Kind.DEATH
                && state.connectionEpoch() == run.epoch && state.sceneSerial() == run.scene
                && state.actorGeneration() == run.actor && state.requestSeq() == run.firstSeq;
    }

    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }

    private static final class Run {
        final Connection connection; final long epoch, sceneFloor; final Path output;
        long scene, actor, firstSeq, controllerTicks, lastStatusTick, statusInterval, castTicks, pendingCastTick;
        long freshCastSequence;
        int trySends, statusSends, castSends, probeTicks, uiStep, repeatCallbacks, castPhase, recoveryDrops;
        boolean tick19, insideUnlock, firstClicked, firstAtUnlock, waitSeen, nullRestored, uiComplete, settled;
        String failure;
        Run(Connection connection, long epoch, long sceneFloor, Path output) {
            this.connection = connection; this.epoch = epoch; this.sceneFloor = sceneFloor; this.output = output;
        }
    }
}
