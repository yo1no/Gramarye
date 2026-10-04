package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Excluded presentation fault and original input-context checks, never a sender/session repair. */
public final class P11C4aUiContextProbe {
    public interface FocusInput { void c4a$nativeFocus(long window, boolean focused); }
    private static Run active;
    private P11C4aUiContextProbe() { }

    static void start(Connection connection, Path output) {
        require(active == null && Minecraft.getInstance().isSameThread(), "UI_CONTEXT_START_OWNER");
        active = new Run(connection, output);
    }

    static void armHidden(State exact) {
        var run = checked(); var minecraft = owner(run);
        require(!run.hiddenArmed && exact == P11ClientTransitions.view() && exact.scope() == Scope.PLAY
                && exact.kind() == Kind.DEATH && exact.outcome() == Outcome.NOT_STARTED
                && exact.availability() == Availability.MAY_TRY && P11ClientTransitions.blocksCast()
                && minecraft.screen instanceof P11ClientTransitionScreen, "UI_CONTEXT_HIDDEN_EXACT_PENDING");
        run.pending = exact; run.hiddenArmed = true;
    }

    static boolean hiddenDone() { return checked().hiddenDone; }

    /** Exactly one invocation per original scheduled P9 consumer; scope allocates before its fault. */
    public static Call before(boolean registered, boolean session, boolean pendingFlush) {
        var run = active;
        if (run == null || run.failure != null) { return null; }
        var minecraft = owner(run);
        var call = new Call(run, minecraft.screen);
        run.call = call;
        try {
            if (run.hiddenArmed && !run.hiddenDone) {
                require(registered && session && minecraft.player != null && minecraft.level != null
                        && minecraft.getConnection() != null && minecraft.isWindowActive() && !minecraft.isPaused()
                        && same(run.pending, P11ClientTransitions.view()) && P11ClientTransitions.blocksCast()
                        && minecraft.screen instanceof P11ClientTransitionScreen, "UI_CONTEXT_HIDDEN_NATIVE_GATES");
                call.hidden = true;
                // The ONLY presentation fault. Do not invoke setScreen: vanilla recreates a
                // DeathScreen and P11 legitimately repairs it. Restore this exact reference below.
                minecraft.screen = null;
                click(minecraft, GLFW.GLFW_KEY_R);
            } else if (run.phase >= 0 && run.phase < 15) {
                require(registered && session && minecraft.player != null && minecraft.level != null
                        && same(run.terminal, P11ClientTransitions.view()) && !P11ClientTransitions.blocksCast(),
                        "UI_CONTEXT_POST_NATIVE_OWNER");
                if (run.phase != 10 && (!minecraft.isWindowActive() || !focused(minecraft))) { return call; }
                call.phase = run.phase;
                switch (run.phase) {
                    case 0 -> { bind(run, minecraft); click(minecraft, GLFW.GLFW_KEY_R); }
                    case 1 -> click(minecraft, GLFW.GLFW_KEY_V);
                    case 2 -> { restoreKey(run); click(minecraft, GLFW.GLFW_KEY_V); }
                    case 3, 8, 13 -> click(minecraft, GLFW.GLFW_KEY_R);
                    case 4, 7, 12, 14 -> { }
                    case 5 -> {
                        require(minecraft.screen == null && (Object) minecraft.getWindow() instanceof FocusInput,
                                "UI_CONTEXT_FOCUS_CALLBACK_HOOK");
                        call.restoreFocus = true;
                        ((FocusInput) (Object) minecraft.getWindow()).c4a$nativeFocus(minecraft.getWindow().getWindow(), false);
                        require(!minecraft.isWindowActive(), "UI_CONTEXT_NATIVE_FOCUS_CALLBACK_NOT_APPLIED");
                        run.focusGlfwStillFocused = focused(minecraft);
                        click(minecraft, GLFW.GLFW_KEY_R);
                    }
                    case 6, 11 -> {
                        require(pendingFlush && minecraft.screen == null, "UI_CONTEXT_ACTUAL_RECOVERY_FLUSH");
                        click(minecraft, GLFW.GLFW_KEY_R);
                    }
                    case 9 -> {
                        require(minecraft.screen == null, "UI_CONTEXT_PAUSE_BASELINE_SCREEN");
                        minecraft.pauseGame(false);
                        require(minecraft.screen instanceof PauseScreen && minecraft.screen.isPauseScreen(),
                                "UI_CONTEXT_NATIVE_PAUSE_SCREEN");
                        run.pauseRenderStart = run.renderFrames;
                        run.pauseTimeStart = minecraft.level.getGameTime();
                        run.pauseTicks = 0;
                        click(minecraft, GLFW.GLFW_KEY_R);
                    }
                    case 10 -> {
                        require(minecraft.screen instanceof PauseScreen && !minecraft.isPaused()
                                && (minecraft.getSingleplayerServer() == null || minecraft.getSingleplayerServer().isPublished()),
                                "UI_CONTEXT_REMOTE_MENU_NOT_ACTUAL_PAUSE");
                        if (++run.pauseTicks < 2 || run.renderFrames <= run.pauseRenderStart
                                || minecraft.level.getGameTime() <= run.pauseTimeStart) { call.phase = -1; return call; }
                        run.pauseObservedNotPaused = true;
                        run.pauseTimeAdvanced = minecraft.level.getGameTime() - run.pauseTimeStart;
                        click(minecraft, GLFW.GLFW_KEY_ESCAPE);
                        require(minecraft.screen == null, "UI_CONTEXT_NATIVE_PAUSE_ESCAPE");
                        // Closing happens before this consumer, so let its normal flush run now.
                        call.phase = 11;
                        run.phase = 11;
                        require(pendingFlush, "UI_CONTEXT_PAUSE_DID_NOT_MARK_FLUSH");
                        click(minecraft, GLFW.GLFW_KEY_R);
                    }
                    default -> throw new IllegalStateException("UI_CONTEXT_PHASE");
                }
            }
        } catch (RuntimeException | Error failure) { run.failure = "UI_CONTEXT_BEFORE_NATIVE_CONSUMER"; }
        return call;
    }

    /** Always restores the presentation/focus fault even when original P9 throws. */
    public static void after(Call call, boolean normal) {
        if (call == null) { return; }
        var run = call.run; var minecraft = Minecraft.getInstance();
        try {
            if (!normal) { run.failure = "UI_CONTEXT_ORIGINAL_CONSUMER_THROW"; return; }
            if (run.failure != null) { return; }
            if (call.hidden) {
                require(minecraft.screen == null && call.drops == 1 && call.sends == 0
                        && "TRANSITION_PENDING".equals(call.gate) && P11ClientTransitions.blocksCast()
                        && same(run.pending, P11ClientTransitions.view()), "UI_CONTEXT_HIDDEN_NOT_TRANSITION_DROP");
                run.hiddenDone = true;
            } else if (call.phase >= 0) {
                int expected = switch (call.phase) { case 1, 3, 8, 13 -> 1; default -> 0; };
                String gate = switch (call.phase) {
                    case 5 -> "WINDOW_INACTIVE";
                    case 6, 11 -> "PENDING_FLUSH_REQUIRED";
                    case 9 -> "SCREEN_OPEN";
                    default -> "ALLOW";
                };
                int dropped = call.phase == 5 || call.phase == 6 || call.phase == 11 ? 1 : 0;
                require(call.sends == expected && call.drops == dropped && gate.equals(call.gate),
                        "UI_CONTEXT_NATIVE_GATE_SEND_DROP_COUNTS");
                if (call.phase == 5) { run.focusDrops = call.drops; }
                if (call.phase == 6) { run.focusRecoveryDrops = call.drops; }
                if (call.phase == 11) { run.pauseRecoveryDrops = call.drops; }
                run.phase = call.phase + 1;
            }
        } catch (RuntimeException | Error failure) { run.failure = "UI_CONTEXT_AFTER_NATIVE_CONSUMER"; }
        finally {
            if (call.hidden) { minecraft.screen = call.originalScreen; }
            try {
                if (call.restoreFocus) {
                    ((FocusInput) (Object) minecraft.getWindow()).c4a$nativeFocus(minecraft.getWindow().getWindow(), true);
                    GLFW.glfwFocusWindow(minecraft.getWindow().getWindow());
                }
            } catch (RuntimeException | Error cleanupFailure) { run.failure = "UI_CONTEXT_FOCUS_RESTORE_FAILED"; }
            finally { run.call = null; }
        }
    }

    public static void gate(Object value) {
        var run = active;
        if (run != null && run.call != null && run.call.gate == null && value instanceof Enum<?> gate) {
            run.call.gate = gate.name();
        }
    }

    public static void dropped() { if (active != null && active.call != null) { active.call.drops++; } }
    public static void rendered() { if (active != null) { active.renderFrames++; } }
    static boolean ownsPostCasts() { return active != null && active.phase >= 0; }
    static void cast(long sequence, int slot, int mask, boolean noHints) {
        var run = active;
        if (run == null || run.failure != null) { return; }
        var call = run.call;
        if (call == null || call.phase < 0 || run.lastSequence == Long.MAX_VALUE
                || sequence != run.lastSequence + 1 || slot != 0 || mask != 0 || !noHints) {
            run.failure = "UI_CONTEXT_UNEXPECTED_CAST"; return;
        }
        call.sends++; run.sends++; run.lastSequence = sequence;
    }

    static boolean finish(long previousSequence) throws IOException {
        var run = checked(); var minecraft = owner(run);
        require(run.hiddenDone, "UI_CONTEXT_NO_HIDDEN_PENDING_CONSUMER");
        if (run.phase == -1) {
            require(!P11ClientTransitions.blocksCast() && P11ClientTransitions.view().outcome() == Outcome.COMPLETED,
                    "UI_CONTEXT_POST_NOT_SETTLED");
            run.terminal = P11ClientTransitions.view(); run.lastSequence = previousSequence; run.phase = 0;
            return false;
        }
        if (run.phase != 15) { return false; }
        require(!run.rebound && run.sends == 4 && run.focusDrops == 1 && run.focusRecoveryDrops == 1
                && run.pauseRecoveryDrops == 1 && run.pauseObservedNotPaused && minecraft.screen == null,
                "UI_CONTEXT_FINAL_COUNTS");
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "COMPONENT_PRESENTATION_FAULT_AND_NATIVE_INPUT_CONTEXT_SUBSET");
        report.put("hiddenPendingPresentationFaultConsumers", 1);
        report.put("hiddenActualGate", "TRANSITION_PENDING"); report.put("hiddenQueuedNativeDrops", 1);
        report.put("hiddenCastSends", 0); report.put("normalNativeHiddenHistoryClaimed", false);
        report.put("nativeRebindRToVAndExactRestore", true); report.put("oldMappingClickSends", 0);
        report.put("postContextNativeCastSends", run.sends); report.put("lastNativeCastSequence", run.lastSequence);
        report.put("focusLossInputKind", "ORIGINAL_WINDOW_FOCUS_CALLBACK_INJECTION_NOT_OS_FOCUS_LOSS");
        report.put("glfwFocusedDuringInjectedLoss", run.focusGlfwStillFocused);
        report.put("inactiveNativeDrops", run.focusDrops); report.put("focusRecoveryNativeDrops", run.focusRecoveryDrops);
        report.put("actualPauseScreen", true); report.put("actualClientPaused", false);
        report.put("nativeClientWorldTimeAdvancedDuringMenu", run.pauseTimeAdvanced);
        report.put("pauseRecoveryNativeDrops", run.pauseRecoveryDrops);
        report.put("unpublishedSingleplayerPauseClaimed", false); report.put("physicalHeldInputClaimed", false);
        report.put("serverCastAcceptanceClaimed", false); report.put("fullC4aAcceptance", false);
        P11C4aEvidence.write(run.output, "ui-native-context-subset.json", report);
        active = null; return true;
    }

    static void abort() {
        var run = active;
        if (run == null) { return; }
        try { restoreKey(run); }
        catch (RuntimeException | Error failure) { run.failure = "UI_CONTEXT_MAPPING_RESTORE_FAILED"; }
        finally { active = null; }
    }
    static java.util.Map<String, Object> pending() {
        var run = active;
        return run == null ? java.util.Map.of("active", false) : java.util.Map.of(
                "active", true, "phase", run.phase, "hiddenDone", run.hiddenDone,
                "postCastSends", run.sends, "rebound", run.rebound,
                "failure", run.failure == null ? "NONE" : run.failure);
    }
    private static void bind(Run run, Minecraft minecraft) {
        require(minecraft.screen == null, "UI_CONTEXT_REBIND_SCREEN");
        for (var mapping : minecraft.options.keyMappings) {
            if (mapping.getName().equals("key.gramarye.cast")) { require(run.mapping == null, "UI_CONTEXT_DUPLICATE_MAPPING"); run.mapping = mapping; }
            else { require(!mapping.matches(GLFW.GLFW_KEY_V, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_V)), "UI_CONTEXT_V_ALREADY_BOUND"); }
        }
        require(run.mapping != null && run.mapping.getKey().getType() == InputConstants.Type.KEYSYM
                && run.mapping.getKey().getValue() == GLFW.GLFW_KEY_R && run.mapping.getKeyModifier() == KeyModifier.NONE,
                "UI_CONTEXT_BASELINE_MAPPING");
        run.originalKey = run.mapping.getKey(); run.originalModifier = run.mapping.getKeyModifier();
        run.mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_V));
        run.rebound = true;
    }
    private static void restoreKey(Run run) {
        if (!run.rebound) { return; }
        require(run.mapping.getKey().getType() == InputConstants.Type.KEYSYM
                && run.mapping.getKey().getValue() == GLFW.GLFW_KEY_V && run.mapping.getKeyModifier() == KeyModifier.NONE,
                "UI_CONTEXT_MAPPING_CHANGED_EXTERNALLY");
        run.mapping.setKeyModifierAndCode(run.originalModifier, run.originalKey); run.rebound = false;
    }
    private static void click(Minecraft minecraft, int key) {
        long window = minecraft.getWindow().getWindow(); int scan = GLFW.glfwGetKeyScancode(key);
        minecraft.keyboardHandler.keyPress(window, key, scan, GLFW.GLFW_PRESS, 0);
        minecraft.keyboardHandler.keyPress(window, key, scan, GLFW.GLFW_RELEASE, 0);
    }
    private static boolean focused(Minecraft minecraft) { return GLFW.glfwGetWindowAttrib(minecraft.getWindow().getWindow(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE; }
    private static Minecraft owner(Run run) {
        var minecraft = Minecraft.getInstance();
        require(minecraft.isSameThread() && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == run.connection && run.connection.isConnected(), "UI_CONTEXT_EXACT_OWNER");
        return minecraft;
    }
    private static boolean same(State first, State second) {
        return first != null && second != null && first.connectionEpoch() == second.connectionEpoch()
                && first.sceneSerial() == second.sceneSerial() && first.actorGeneration() == second.actorGeneration()
                && first.requestSeq() == second.requestSeq() && first.kind() == second.kind() && first.outcome() == second.outcome();
    }
    private static Run checked() { var run = active; require(run != null && run.failure == null, "UI_CONTEXT_MISSING_OR_FAILED"); return run; }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    public static final class Call {
        final Run run; final Screen originalScreen; int phase = -1, drops, sends; boolean hidden, restoreFocus; String gate;
        private Call(Run run, Screen originalScreen) { this.run = run; this.originalScreen = originalScreen; }
    }
    private static final class Run {
        final Connection connection; final Path output; State pending, terminal; Call call;
        int phase = -1, sends, focusDrops, focusRecoveryDrops, pauseRecoveryDrops, pauseTicks;
        long lastSequence, renderFrames, pauseRenderStart, pauseTimeStart, pauseTimeAdvanced;
        boolean hiddenArmed, hiddenDone, rebound, focusGlfwStillFocused, pauseObservedNotPaused;
        KeyMapping mapping; InputConstants.Key originalKey; KeyModifier originalModifier; String failure;
        Run(Connection connection, Path output) { this.connection = connection; this.output = output; }
    }
}
