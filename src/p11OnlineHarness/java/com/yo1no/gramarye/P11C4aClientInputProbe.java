package com.yo1no.gramarye;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;

/**
 * Excluded engineering input driver. It calls the original window callbacks, never a
 * transition controller action or Button.onPress. Callback injection is not physical
 * OS input: snapshots report the independently observed GLFW neutral state as such.
 */
public final class P11C4aClientInputProbe {
    public enum Action {
        DEATH_RESPAWN, DEATH_TITLE, DEATH_CONFIRM_NO, END_FINISH, RETRY,
        STATUS_ESCAPE, LEAVE_STAY, LEAVE_CONFIRM, RESIZE_SCREEN, HIDE_PENDING_SCREEN,
        ENTER_PRESS, ENTER_REPEAT, ENTER_RELEASE, CAST_PRESS, CAST_REPEAT, CAST_RELEASE, CAST_CLICK
    }

    public enum ScreenKind { NONE, DEATH, DEATH_CONFIRM, END, STATUS, LEAVE_CONFIRM, OTHER }

    public record Snapshot(long completedActions, long mousePresses, long mouseReleases,
            long keyPresses, long keyRepeats, long keyReleases, long resizeCalls, long screenNullCalls,
            ScreenKind screen, boolean screenPauses, boolean glfwActivationNeutral,
            boolean clientThread, boolean overlayPresent) { }

    public interface MouseInput {
        void p11$move(long window, double x, double y);
        void p11$press(long window, int button, int action, int modifiers);
    }

    private static long completedActions;
    private static long mousePresses;
    private static long mouseReleases;
    private static long keyPresses;
    private static long keyRepeats;
    private static long keyReleases;
    private static long resizeCalls;
    private static long screenNullCalls;
    private static boolean enterCallbackDown;
    private static boolean castCallbackDown;

    private P11C4aClientInputProbe() { }

    /** Caller invokes each planned action until true, then advances its own finite scenario. */
    public static boolean act(Minecraft minecraft, Action action) {
        requireOwnedClient(minecraft);
        if (minecraft.getOverlay() != null) { return false; }
        Screen screen = minecraft.screen;
        boolean done = switch (action) {
            case DEATH_RESPAWN -> screen instanceof DeathScreen
                    && click(minecraft, screen, "deathScreen.respawn");
            case DEATH_TITLE -> screen instanceof DeathScreen
                    && click(minecraft, screen, "deathScreen.titleScreen");
            case DEATH_CONFIRM_NO -> screen instanceof DeathScreen.TitleConfirmScreen
                    && click(minecraft, screen, "deathScreen.respawn");
            case END_FINISH -> screen instanceof WinScreen && keyClick(minecraft, GLFW.GLFW_KEY_ESCAPE);
            case RETRY -> screen instanceof P11ClientTransitionScreen
                    && click(minecraft, screen, "screen.gramarye.transition.retry");
            case STATUS_ESCAPE -> screen instanceof P11ClientTransitionScreen
                    && keyClick(minecraft, GLFW.GLFW_KEY_ESCAPE);
            case LEAVE_STAY -> screen instanceof P11ClientLeaveScreen
                    && click(minecraft, screen, "screen.gramarye.transition.stay");
            case LEAVE_CONFIRM -> screen instanceof P11ClientLeaveScreen
                    && click(minecraft, screen, "screen.gramarye.transition.leave");
            case RESIZE_SCREEN -> {
                if (!knownScreen(screen)) { yield false; }
                minecraft.resizeDisplay();
                resizeCalls = Math.incrementExact(resizeCalls);
                yield true;
            }
            case HIDE_PENDING_SCREEN -> {
                if (!(screen instanceof P11ClientTransitionScreen)) { yield false; }
                minecraft.setScreen(null);
                screenNullCalls = Math.incrementExact(screenNullCalls);
                yield true;
            }
            case ENTER_PRESS -> controlScreen(screen) && key(minecraft, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_PRESS);
            case ENTER_REPEAT -> controlScreen(screen) && key(minecraft, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_REPEAT);
            case ENTER_RELEASE -> enterCallbackDown && key(minecraft, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_RELEASE);
            case CAST_PRESS -> castContext(minecraft) && key(minecraft, GLFW.GLFW_KEY_R, GLFW.GLFW_PRESS);
            case CAST_REPEAT -> castContext(minecraft) && key(minecraft, GLFW.GLFW_KEY_R, GLFW.GLFW_REPEAT);
            case CAST_RELEASE -> castCallbackDown && key(minecraft, GLFW.GLFW_KEY_R, GLFW.GLFW_RELEASE);
            case CAST_CLICK -> castContext(minecraft) && keyClick(minecraft, GLFW.GLFW_KEY_R);
        };
        if (done) { completedActions = Math.incrementExact(completedActions); }
        return done;
    }

    public static Snapshot snapshot(Minecraft minecraft) {
        requireOwnedClient(minecraft);
        Screen screen = minecraft.screen;
        long window = minecraft.getWindow().getWindow();
        boolean neutral = !InputConstants.isKeyDown(window, GLFW.GLFW_KEY_ENTER)
                && !InputConstants.isKeyDown(window, GLFW.GLFW_KEY_KP_ENTER)
                && !InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE)
                && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE;
        return new Snapshot(completedActions, mousePresses, mouseReleases, keyPresses, keyRepeats,
                keyReleases, resizeCalls, screenNullCalls, screenKind(screen),
                screen != null && screen.isPauseScreen(), neutral,
                minecraft.isSameThread(), minecraft.getOverlay() != null);
    }

    private static boolean click(Minecraft minecraft, Screen screen, String translation) {
        Button selected = null;
        for (var child : screen.children()) {
            if (child instanceof Button button && button.visible && button.active
                    && button.getMessage().getContents() instanceof TranslatableContents text
                    && text.getKey().equals(translation)) {
                if (selected != null) { throw new IllegalStateException("P11_C4A_AMBIGUOUS_NATIVE_BUTTON"); }
                selected = button;
            }
        }
        if (selected == null) { return false; }
        if (!(minecraft.mouseHandler instanceof MouseInput input)) {
            throw new IllegalStateException("P11_C4A_MOUSE_HOOK_MISSING");
        }
        var window = minecraft.getWindow();
        double x = (selected.getX() + selected.getWidth() / 2.0)
                * window.getScreenWidth() / window.getGuiScaledWidth();
        double y = (selected.getY() + selected.getHeight() / 2.0)
                * window.getScreenHeight() / window.getGuiScaledHeight();
        input.p11$move(window.getWindow(), x, y);
        input.p11$press(window.getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
        mousePresses = Math.incrementExact(mousePresses);
        input.p11$press(window.getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
        mouseReleases = Math.incrementExact(mouseReleases);
        return true;
    }

    private static boolean keyClick(Minecraft minecraft, int key) {
        key(minecraft, key, GLFW.GLFW_PRESS);
        return key(minecraft, key, GLFW.GLFW_RELEASE);
    }

    private static boolean key(Minecraft minecraft, int key, int action) {
        minecraft.keyboardHandler.keyPress(minecraft.getWindow().getWindow(), key,
                GLFW.glfwGetKeyScancode(key), action, 0);
        if (action != GLFW.GLFW_REPEAT) {
            if (key == GLFW.GLFW_KEY_ENTER) { enterCallbackDown = action == GLFW.GLFW_PRESS; }
            if (key == GLFW.GLFW_KEY_R) { castCallbackDown = action == GLFW.GLFW_PRESS; }
        }
        switch (action) {
            case GLFW.GLFW_PRESS -> keyPresses = Math.incrementExact(keyPresses);
            case GLFW.GLFW_REPEAT -> keyRepeats = Math.incrementExact(keyRepeats);
            case GLFW.GLFW_RELEASE -> keyReleases = Math.incrementExact(keyReleases);
            default -> throw new IllegalArgumentException("P11_C4A_UNSUPPORTED_KEY_ACTION");
        }
        return true;
    }

    private static boolean controlScreen(Screen screen) {
        return screen instanceof P11ClientTransitionScreen || screen instanceof P11ClientLeaveScreen;
    }

    private static boolean knownScreen(Screen screen) {
        return controlScreen(screen) || screen instanceof DeathScreen
                || screen instanceof DeathScreen.TitleConfirmScreen || screen instanceof WinScreen;
    }

    private static boolean castContext(Minecraft minecraft) {
        return minecraft.player != null && (minecraft.screen == null || knownScreen(minecraft.screen));
    }

    private static ScreenKind screenKind(Screen screen) {
        if (screen == null) { return ScreenKind.NONE; }
        if (screen instanceof DeathScreen) { return ScreenKind.DEATH; }
        if (screen instanceof DeathScreen.TitleConfirmScreen) { return ScreenKind.DEATH_CONFIRM; }
        if (screen instanceof WinScreen) { return ScreenKind.END; }
        if (screen instanceof P11ClientTransitionScreen) { return ScreenKind.STATUS; }
        if (screen instanceof P11ClientLeaveScreen) { return ScreenKind.LEAVE_CONFIRM; }
        return ScreenKind.OTHER;
    }

    private static void requireOwnedClient(Minecraft minecraft) {
        String scenario = System.getProperty("gramarye.p11.online.case", "");
        if ((!scenario.equals("c4a-dedicated") && !scenario.equals("c4a-host-lan") && !scenario.equals("c4a-reward")
                && !scenario.equals("d3-c4a-baseline") && !scenario.equals("l1-work-context-refusal"))
                || minecraft != Minecraft.getInstance() || !minecraft.isSameThread()) {
            throw new IllegalStateException("P11_C4A_INPUT_OUTSIDE_OWNED_CLIENT");
        }
    }
}
