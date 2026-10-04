package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

/** Client-only observations and the one original manual death-button callback. */
public final class P11L1LifecycleClientProbe {
    private static Connection connection;
    private static LocalPlayer original;
    private static ResourceKey<Level> dimension;
    private static Path output;
    private static String mode;
    private static int ticks, respawns, configurations, logins, clicks;
    private static boolean configCue, completed;
    private P11L1LifecycleClientProbe() {}

    static void start(Minecraft minecraft, Path destination) {
        require(connection == null && P11L1ServerHarness.lifecycle() && minecraft.isSameThread()
                && minecraft.player != null && minecraft.getConnection() == minecraft.player.connection,
                "ARM_EXACT_CURRENT_PLAY");
        original = minecraft.player; connection = original.connection.getConnection();
        dimension = minecraft.level.dimension(); output = destination; mode = P11C4aEvidence.property("case");
    }

    static boolean tick(Minecraft minecraft) throws IOException {
        if (completed) { return true; }
        require(connection != null && minecraft.isSameThread() && connection.isConnected() && ++ticks <= 2400,
                "CURRENT_CONNECTION_OR_DEADLINE");
        boolean terminal = P11C4aEvidence.cuePresent(output.getParent().resolve("server"), "a-lifecycle-terminal.ready");
        var state = P11ClientTransitions.view();
        if (mode.equals("l1-work-config") && !configCue && configurations == 1 && state != null
                && state.scope() == P11TransitionProtocol.Scope.CONFIG
                && state.kind() == P11TransitionProtocol.Kind.ENTER_CONFIG
                && state.outcome() == P11TransitionProtocol.Outcome.COMPLETED) {
            require(state.actorGeneration() == 0 && state.requestSeq() == 0 && minecraft.player == null
                    && minecraft.level == null && connection.getPacketListener()
                        instanceof net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl,
                    "ACTUAL_CONFIG_TERMINAL");
            P11C4aEvidence.write(output, "lifecycle-config-terminal.json", java.util.Map.of(
                    "status", "ACTUAL_START_CONFIGURATION_AND_ACCEPTED_CONFIG_TERMINAL", "nativeStartReturns", configurations,
                    "actorGeneration", state.actorGeneration(), "requestSeq", state.requestSeq(),
                    "statusVersion", state.statusVersion(), "sceneSerial", state.sceneSerial(), "playerAbsent", true));
            configCue = true; P11C4aEvidence.cue(output, "lifecycle-config-terminal.ready");
        }
        if (!terminal) { return false; }
        if (mode.equals("l1-work-death") && clicks == 0) {
            if (minecraft.getOverlay() != null || !(minecraft.screen instanceof DeathScreen screen)) { return false; }
            if (!clickRespawn(minecraft, screen)) { return false; }
            clicks++;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null
                || minecraft.getOverlay() != null || minecraft.screen != null) { return false; }
        require(minecraft.getConnection() == minecraft.player.connection && minecraft.getConnection().getConnection() == connection
                && minecraft.player.getUUID().equals(original.getUUID()) && minecraft.player.isAlive(), "ACTUAL_WORLD_IDENTITY");
        if (mode.equals("l1-work-config")) {
            if (logins != 1 || state == null || state.outcome() != P11TransitionProtocol.Outcome.COMPLETED) { return false; }
            require(configCue && respawns == 0 && state.scope() == P11TransitionProtocol.Scope.PREPLAY
                    && state.kind() == P11TransitionProtocol.Kind.RETURN_TO_WORLD && state.requestSeq() == 0
                    && state.targetActorGeneration() > 0 && minecraft.player != original, "TRUE_CONFIG_RETURN_LOGIN");
        } else if (mode.equals("l1-work-death")) {
            if (respawns != 1 || state == null || state.outcome() != P11TransitionProtocol.Outcome.COMPLETED) { return false; }
            require(clicks == 1 && configurations == 0 && logins == 0 && state.kind() == P11TransitionProtocol.Kind.DEATH
                    && state.requestSeq() > 0 && minecraft.player != original, "TRUE_DEATH_RESPAWN");
        } else {
            if (respawns != 1) { return false; }
            require(configurations == 0 && logins == 0 && clicks == 0 && !minecraft.level.dimension().equals(dimension),
                    "TRUE_ORIGINAL_DIMENSION_FRAME");
        }
        var facts = new LinkedHashMap<String, Object>();
        facts.put("status", "ACTUAL_SAME_CONNECTION_NATIVE_WORLD_READY"); facts.put("case", mode);
        facts.put("nativeRespawnReturns", respawns); facts.put("nativeStartConfigurationReturns", configurations);
        facts.put("nativeSuccessorLoginReturns", logins); facts.put("originalDeathButtonClicks", clicks);
        facts.put("samePhysicalConnection", true); facts.put("sameAuthenticatedIdentity", true);
        facts.put("currentDimension", minecraft.level.dimension().location().toString()); facts.put("physicalOsInputClaim", false);
        P11C4aEvidence.write(output, "lifecycle-world-ready.json", facts); completed = true; return true;
    }

    public static void loginReturned(ClientPacketListener listener) {
        if (connection == null || completed) { return; }
        require(mode.equals("l1-work-config") && exact(listener) && logins == 0, "ONE_ORIGINAL_CONFIG_LOGIN"); logins++;
    }
    public static void respawnReturned(ClientPacketListener listener) {
        if (connection == null || completed) { return; }
        require(!mode.equals("l1-work-config") && exact(listener) && respawns == 0, "ONE_ORIGINAL_RESPAWN"); respawns++;
    }
    public static void configurationReturned(ClientPacketListener listener) {
        if (connection == null || completed) { return; }
        require(mode.equals("l1-work-config") && Minecraft.getInstance().isSameThread()
                && listener.getConnection() == connection && configurations == 0, "ONE_ORIGINAL_START_CONFIGURATION"); configurations++;
    }
    private static boolean exact(ClientPacketListener listener) {
        return Minecraft.getInstance().isSameThread() && listener == Minecraft.getInstance().getConnection()
                && listener.getConnection() == connection;
    }
    private static boolean clickRespawn(Minecraft minecraft, DeathScreen screen) {
        Button found = null;
        for (var item : screen.children()) {
            if (item instanceof Button button && button.active && button.visible
                    && button.getMessage().getContents() instanceof TranslatableContents text
                    && text.getKey().equals("deathScreen.respawn")) {
                require(found == null, "AMBIGUOUS_NATIVE_BUTTON"); found = button;
            }
        }
        if (found == null) { return false; }
        require(minecraft.mouseHandler instanceof P11C4aClientInputProbe.MouseInput, "NATIVE_MOUSE_INVOKER_REQUIRED");
        var input = (P11C4aClientInputProbe.MouseInput) minecraft.mouseHandler;
        var window = minecraft.getWindow();
        input.p11$move(window.getWindow(), (found.getX() + found.getWidth() / 2.0) * window.getScreenWidth() / window.getGuiScaledWidth(),
                (found.getY() + found.getHeight() / 2.0) * window.getScreenHeight() / window.getGuiScaledHeight());
        input.p11$press(window.getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
        input.p11$press(window.getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
        return true;
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "L1_LIFECYCLE_CLIENT_" + code); }
}
