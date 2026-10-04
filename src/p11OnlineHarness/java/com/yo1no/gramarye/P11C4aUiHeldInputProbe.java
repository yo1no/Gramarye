package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;

/** EXTERNAL DRAFT: receives real OS->GLFW state; never injects an Enter callback or sets key state. */
public final class P11C4aUiHeldInputProbe {
    private static Run active;
    private P11C4aUiHeldInputProbe() { }

    static void start(Minecraft minecraft, Connection connection, Path output) {
        var current = P11ClientTransitions.view();
        require(P11C4aEvidence.enabled() && active == null && !P11C4aUiInputProbe.isActive() && minecraft == Minecraft.getInstance()
                && minecraft.isSameThread() && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == connection && connection.isConnected()
                && current != null && current.outcome() == Outcome.COMPLETED
                && !P11ClientTransitions.blocksCast(), "UI_HELD_START_OWNER");
        active = new Run(connection, current.connectionEpoch(), current.sceneSerial(), output);
        // Request native focus once on this owned window; later observations still require
        // actual GLFW focus and key state. Never fake Minecraft's window-active field.
        GLFW.glfwFocusWindow(minecraft.getWindow().getWindow());
        active.nativeFocusRequested = true;
    }

    /** Read-only observation of the original counter, never invokes screen.tick itself. */
    public static void deathTick(DeathScreen screen, int nativeTicker) {
        var run = active;
        if (run == null || run.failure != null || run.unlockSeen) { return; }
        var minecraft = Minecraft.getInstance();
        var state = P11ClientTransitions.view();
        if (!minecraft.isSameThread() || minecraft.screen != screen || state == null
                || state.kind() != Kind.DEATH || state.outcome() != Outcome.BINDING
                || state.connectionEpoch() != run.epoch || state.sceneSerial() <= run.sceneFloor) { return; }
        if (nativeTicker == 19) { run.tick19 = true; }
        if (nativeTicker == 20) {
            if (!run.tick19) { run.failure = "UI_HELD_MISSED_PRE_UNLOCK"; return; }
            run.scene = state.sceneSerial(); run.actor = state.actorGeneration();
            run.unlockSeen = true;
        }
    }

    static boolean firstSubmitted() { return active != null && active.first != null; }
    static boolean isActive() { return active != null; }

    /** Finite parent tick. Returns true only after actual second native frame/terminal and key release. */
    static boolean tick(Minecraft minecraft) throws IOException {
        var run = checked();
        owner(run);
        require(minecraft == Minecraft.getInstance() && ++run.ticks <= 2400, "UI_HELD_PHASE_DEADLINE");
        switch (run.phase) {
            case 0 -> {
                if (!run.unlockSeen || minecraft.getOverlay() != null) { return false; }
                require(minecraft.screen instanceof DeathScreen && run.trySends == 0
                        && P11C4aClientInputProbe.snapshot(minecraft).glfwActivationNeutral(),
                        "UI_HELD_DEATH_OR_INITIAL_NEUTRAL");
                if (!focusedWindow(minecraft)) { return false; }
                focus(minecraft, "deathScreen.respawn"); // Tab callback only. OS driver owns Enter.
                run.phase = 1;
                P11C4aEvidence.cue(run.output, "ui-held-enter-down.ready");
            }
            case 1 -> {
                if (run.first == null) { return false; }
                require(run.presses == 1 && enterDown(minecraft) && focusedWindow(minecraft),
                        "UI_HELD_FIRST_TRY_NOT_OS_GLFW_DOWN");
                run.phase = 2;
            }
            case 2 -> {
                require(enterDown(minecraft) && focusedWindow(minecraft) && run.trySends == 1
                        && run.releases == 0 && run.presses == 1,
                        "UI_HELD_RELEASED_OR_RETRIED_BEFORE_CUE");
                if (run.may == null) { return false; }
                require(run.wait != null && run.waitDown && run.mayDown
                        && P11ClientTransitions.view() == run.may
                        && minecraft.screen instanceof P11ClientTransitionScreen
                        && !minecraft.screen.isPauseScreen() && !P11ClientTransitions.retryArmed()
                        && !button(minecraft, "screen.gramarye.transition.retry").active,
                        "UI_HELD_MAY_ARMED_WITHOUT_NEUTRAL");
                if (run.lastHeldTick != run.controllerTicks) {
                    run.lastHeldTick = run.controllerTicks;
                    run.heldMayTicks++;
                }
                if (run.heldMayTicks < 10) { return false; }
                run.phase = 3;
                P11C4aEvidence.cue(run.output, "ui-held-enter-release.ready");
            }
            case 3 -> {
                require(run.trySends == 1 && run.presses == 1, "UI_HELD_RETRY_ON_RELEASE_OR_EXTRA_PRESS");
                if (run.releases == 0 || enterDown(minecraft)) { return false; }
                require(run.releases == 1 && run.wait != null && run.may != null
                        && P11C4aClientInputProbe.snapshot(minecraft).glfwActivationNeutral(),
                        "UI_HELD_RELEASE_NOT_ACTUAL_NEUTRAL");
                if (!P11ClientTransitions.retryArmed()
                        || !button(minecraft, "screen.gramarye.transition.retry").active) { return false; }
                focus(minecraft, "screen.gramarye.transition.retry");
                run.neutralObserved = true;
                run.phase = 4;
                P11C4aEvidence.cue(run.output, "ui-held-enter-fresh.ready");
            }
            case 4 -> {
                if (run.completed == null || run.second == null || run.releases < 2) { return false; }
                require(run.presses == 2 && run.releases == 2 && run.trySends == 2
                        && run.neutralObserved && !enterDown(minecraft)
                        && P11C4aClientInputProbe.snapshot(minecraft).glfwActivationNeutral()
                        && !P11ClientTransitions.blocksCast() && minecraft.player != null
                        && minecraft.getConnection().getConnection() == run.connection
                        && P11C4aNativeObservations.count(run.connection,
                                P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN) == run.beforeFrames + 1,
                        "UI_HELD_FINAL_EXACT_COUNTS");
                var values = new LinkedHashMap<String, Object>();
                values.put("status", "ACTUAL_OS_INJECTED_GLFW_HOLD_NEUTRAL_FRESH_RETRY_SUBSET");
                values.put("inputKind", "TARGETED_OS_INJECTION_OBSERVED_BY_ORIGINAL_GLFW_KEYBOARD_PATH");
                values.put("humanPhysicalInputClaimed", false);
                values.put("nativeWindowFocusRequested", run.nativeFocusRequested);
                values.put("callbackInjectedEnterCount", 0);
                values.put("nativeUnlock20Observed", run.unlockSeen && run.tick19);
                values.put("firstTry", run.first); values.put("wait", run.wait); values.put("may", run.may);
                values.put("secondTry", run.second); values.put("completed", run.completed);
                values.put("glfwEnterDownAtWait", run.waitDown); values.put("glfwEnterDownAtMay", run.mayDown);
                values.put("heldMayRealClientTicks", run.heldMayTicks);
                values.put("actualPressCallbacks", run.presses); values.put("actualReleaseCallbacks", run.releases);
                values.put("actualRepeatCallbacks", run.repeats); values.put("repeatsWhileMayHeld", run.mayRepeats);
                values.put("repeatSubrowProven", run.mayRepeats > 0);
                values.put("neutralBeforeNewOsOperation", run.neutralObserved);
                values.put("trySubmissions", run.trySends); values.put("nativeRespawnDelta", 1);
                values.put("fullC4aAcceptance", false);
                P11C4aEvidence.write(run.output, "ui-held-input.json", values);
                active = null;
                return true;
            }
            default -> throw new IllegalStateException("UI_HELD_INVALID_PHASE");
        }
        return false;
    }

    public static void controllerTick() { if (active != null) { active.controllerTicks++; } }

    /** Original KeyboardHandler HEAD. Only the external OS driver supplies Enter events. */
    public static void keyEvent(long window, int key, int action) {
        var run = active;
        if (run == null || run.failure != null || key != GLFW.GLFW_KEY_ENTER
                || window != Minecraft.getInstance().getWindow().getWindow()) { return; }
        if (!Minecraft.getInstance().isSameThread()) { run.failure = "UI_HELD_KEY_OFF_THREAD"; return; }
        if (action == GLFW.GLFW_PRESS) {
            if (++run.presses > 2 || run.phase != 1 && run.phase != 4) { run.failure = "UI_HELD_PRESS_OUTSIDE_OS_CUE"; }
        } else if (action == GLFW.GLFW_REPEAT) {
            run.repeats++;
            if (run.may != null && enterDown(Minecraft.getInstance()) && run.trySends == 1) { run.mayRepeats++; }
        } else if (action == GLFW.GLFW_RELEASE) {
            if (++run.releases > 2 || run.phase != 3 && run.phase != 4) { run.failure = "UI_HELD_RELEASE_OUTSIDE_OS_CUE"; }
        }
    }

    public static void submitted(Object value) {
        var run = active;
        if (run == null || run.failure != null || !(value instanceof Request request)
                || request.command() != Command.TRY || !sameScene(run, request.scope(), request.connectionEpoch(),
                        request.sceneSerial(), request.actorGeneration(), request.kind())) { return; }
        try {
            var minecraft = owner(run);
            require(enterDown(minecraft), "UI_HELD_TRY_WITHOUT_REAL_GLFW_DOWN");
            if (++run.trySends == 1) {
                require(run.phase == 1 && run.presses == 1 && run.releases == 0,
                        "UI_HELD_FIRST_SENDER_WITHOUT_OS_PRESS");
                run.first = request;
            } else {
                require(run.trySends == 2 && run.phase == 4 && run.neutralObserved
                        && run.presses == 2 && run.releases == 1 && request.requestSeq() > run.first.requestSeq(),
                        "UI_HELD_DUPLICATE_OR_AUTOMATIC_TRY");
                run.second = request;
            }
        } catch (RuntimeException | Error failure) { run.failure = "UI_HELD_SUBMIT_OBSERVER"; }
    }

    public static void accepted(Object value, Connection connection) {
        var run = active;
        if (run == null || run.failure != null || connection != run.connection || !(value instanceof State state)
                || P11ClientTransitions.view() != state || !sameScene(run, state.scope(), state.connectionEpoch(),
                        state.sceneSerial(), state.actorGeneration(), state.kind())) { return; }
        try {
            var minecraft = owner(run);
            if (state.outcome() == Outcome.NOT_STARTED) {
                require(run.first != null && state.requestSeq() == run.first.requestSeq() && run.trySends == 1,
                        "UI_HELD_NOT_STARTED_WRONG_N");
                if (state.availability() == Availability.WAIT_NOTIFY) {
                    require(state.reason() == Reason.ACTIVE_OPERATION && enterDown(minecraft), "UI_HELD_WAIT_NOT_HELD_FOP");
                    run.wait = state; run.waitDown = true;
                } else if (state.availability() == Availability.MAY_TRY) {
                    require(run.wait != null, "UI_HELD_MAY_WITHOUT_WAIT");
                    if (run.may == null) {
                        require(enterDown(minecraft), "UI_HELD_FIRST_MAY_NOT_HELD");
                        run.mayDown = true;
                    }
                    run.may = state;
                }
            } else if (state.outcome() == Outcome.COMPLETED) {
                require(run.second != null && state.requestSeq() == run.second.requestSeq(), "UI_HELD_TERMINAL_WRONG_N");
                run.completed = state;
            }
        } catch (RuntimeException | Error failure) { run.failure = "UI_HELD_ACCEPTED_OBSERVER"; }
    }

    static void abort() { active = null; } // Root OS driver independently releases any owned held key in finally.

    static java.util.Map<String, Object> pending() {
        var run = active;
        return run == null ? java.util.Map.of("active", false) : java.util.Map.of(
                "active", true, "phase", run.phase, "trySends", run.trySends, "presses", run.presses,
                "releases", run.releases, "heldMayTicks", run.heldMayTicks,
                "failure", run.failure == null ? "NONE" : run.failure);
    }

    private static void focus(Minecraft minecraft, String translation) {
        Button expected = button(minecraft, translation);
        require(expected.active, "UI_HELD_TARGET_BUTTON_DISABLED");
        for (int i = 0; i < 3; i++) {
            if (minecraft.screen.getFocused() == expected) { return; }
            // These are presentation focus callbacks, never Enter activation callbacks.
            minecraft.keyboardHandler.keyPress(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_TAB,
                    GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_TAB), GLFW.GLFW_PRESS, 0);
            minecraft.keyboardHandler.keyPress(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_TAB,
                    GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_TAB), GLFW.GLFW_RELEASE, 0);
        }
        throw new IllegalStateException("UI_HELD_ORIGINAL_TAB_FOCUS_FAILED");
    }

    private static Button button(Minecraft minecraft, String translation) {
        require(minecraft.screen != null, "UI_HELD_NO_SCREEN");
        Button found = null;
        for (var child : minecraft.screen.children()) {
            if (child instanceof Button button && button.visible
                    && button.getMessage().getContents() instanceof TranslatableContents text
                    && text.getKey().equals(translation)) {
                require(found == null, "UI_HELD_AMBIGUOUS_BUTTON"); found = button;
            }
        }
        require(found != null, "UI_HELD_MISSING_BUTTON");
        return found;
    }

    private static boolean enterDown(Minecraft minecraft) {
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_ENTER);
    }

    private static boolean focusedWindow(Minecraft minecraft) {
        return minecraft.isWindowActive()
                && GLFW.glfwGetWindowAttrib(minecraft.getWindow().getWindow(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
    }

    private static boolean sameScene(Run run, Scope scope, long epoch, long scene, long actor, Kind kind) {
        return scope == Scope.PLAY && kind == Kind.DEATH && epoch == run.epoch && scene == run.scene && actor == run.actor;
    }

    private static Minecraft owner(Run run) {
        var minecraft = Minecraft.getInstance();
        require(minecraft.isSameThread() && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == run.connection && run.connection.isConnected(),
                "UI_HELD_EXACT_CLIENT_OWNER");
        return minecraft;
    }

    private static Run checked() {
        var run = active;
        require(run != null && run.failure == null, "UI_HELD_ABSENT_OR_FAILED"); return run;
    }

    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }

    private static final class Run {
        final Connection connection; final long epoch, sceneFloor, beforeFrames; final Path output;
        long scene, actor, controllerTicks, lastHeldTick;
        int phase, ticks, presses, repeats, mayRepeats, releases, trySends, heldMayTicks;
        boolean tick19, unlockSeen, waitDown, mayDown, neutralObserved, nativeFocusRequested;
        Request first, second; State wait, may, completed; String failure;
        Run(Connection connection, long epoch, long sceneFloor, Path output) {
            this.connection = connection; this.epoch = epoch; this.sceneFloor = sceneFloor; this.output = output;
            beforeFrames = P11C4aNativeObservations.count(connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN);
        }
    }
}
