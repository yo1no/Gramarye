package com.yo1no.gramarye;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

/** Client-only native UI/handler and accepted mirror evidence; never generates R or a replacement actor. */
public final class P11CooldownCloneClientProbe {
    private static Connection connection;
    private static LocalPlayer firstPlayer, deathSuccessor;
    private static int respawns, scene, deathClicks, endClicks;
    private static long sequence, generation, sourceEpoch, sourceVersion, beforeEpoch;
    private static String reference, actualReference, sourceState, state;
    private static int remaining;
    private static boolean deathDone, endDone, endView;
    private P11CooldownCloneClientProbe() { }

    /** Returns whether the parent's ordinary living-world input checks may run this tick. */
    static boolean tick(Minecraft minecraft, Connection exact, Path output, Path serverOutput) throws IOException {
        if (!P11CooldownCloneProbe.selected()) { return true; }
        require(minecraft.isSameThread() && exact != null && exact.isConnected(), "EXACT_LIVE_CONNECTION");
        if (connection == null) {
            require(minecraft.player != null && minecraft.player.connection.getConnection() == exact, "INITIAL_CURRENT_PLAYER");
            connection = exact; firstPlayer = minecraft.player;
        }
        require(connection == exact, "NO_NEW_CONNECTION");
        if (scene == 0 && receipt(serverOutput, "cooldown-clone-before-death.json")) {
            before(serverOutput.resolve("cooldown-clone-before-death.json")); scene = 1;
        }
        if (deathDone && scene == 1 && receipt(serverOutput, "cooldown-clone-before-end.json")) {
            before(serverOutput.resolve("cooldown-clone-before-end.json")); scene = 2;
        }
        if (scene == 1 && !deathDone && deathClicks == 0
                && minecraft.getOverlay() == null && minecraft.screen instanceof DeathScreen screen
                && clickRespawn(minecraft, screen)) { deathClicks++; }
        if (scene == 2 && !endDone) {
            if (!endView && minecraft.level != null && minecraft.level.dimension() == Level.END
                    && minecraft.player != null && minecraft.screen == null && respawns == 2) {
                endView = true; P11C4aEvidence.cue(output, "cooldown-clone-end-view.ready");
            }
            if (endClicks == 0 && minecraft.getOverlay() == null && minecraft.screen instanceof WinScreen) {
                long window = minecraft.getWindow().getWindow();
                int scan = GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_ESCAPE);
                minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_ESCAPE, scan, GLFW.GLFW_PRESS, 0);
                minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_ESCAPE, scan, GLFW.GLFW_RELEASE, 0);
                endClicks++;
            }
        }
        if (!worldReady(minecraft)) { return false; }
        var transition = P11ClientTransitions.view();
        boolean currentActive = reference != null && reference.equals(actualReference) && "AVAILABLE".equals(sourceState)
                && "ACTIVE".equals(state) && sourceEpoch > beforeEpoch && remaining > 0 && remaining <= 600;
        if (scene == 1 && !deathDone && currentActive && respawns == 1 && deathClicks == 1
                && transition != null && transition.kind() == P11TransitionProtocol.Kind.DEATH
                && transition.outcome() == P11TransitionProtocol.Outcome.COMPLETED) {
            require(minecraft.player != firstPlayer && minecraft.player.getUUID().equals(firstPlayer.getUUID()), "ACTUAL_DEATH_SUCCESSOR");
            write(output, "death", minecraft); deathSuccessor = minecraft.player; deathDone = true;
        }
        if (scene == 2 && !endDone && currentActive && respawns == 3 && endClicks == 1 && endView
                && minecraft.level.dimension() == Level.OVERWORLD && transition != null
                && transition.kind() == P11TransitionProtocol.Kind.END
                && transition.outcome() == P11TransitionProtocol.Outcome.COMPLETED) {
            require(minecraft.player != deathSuccessor && minecraft.player.getUUID().equals(firstPlayer.getUUID()), "ACTUAL_END_SUCCESSOR");
            write(output, "end", minecraft); endDone = true;
        }
        return true;
    }

    /** Called by the parent only after the original mirror accepted this exact snapshot. */
    static void snapshot(long actualGeneration, long actualSequence, long epoch, long version,
            String actualSourceState, String actualRef, String actualState, int ticks) {
        if (!P11CooldownCloneProbe.selected()) { return; }
        require(Minecraft.getInstance().isSameThread() && actualGeneration > 0 && actualSequence > sequence,
                "ACTUAL_ACCEPTED_MIRROR_ORDER");
        generation = actualGeneration; sequence = actualSequence; sourceEpoch = epoch; sourceVersion = version;
        sourceState = actualSourceState; actualReference = actualRef; state = actualState; remaining = ticks;
    }

    public static void respawnReturned(ClientPacketListener listener) {
        if (!P11CooldownCloneProbe.selected() || connection == null) { return; }
        var minecraft = Minecraft.getInstance();
        require(minecraft.isSameThread() && listener == minecraft.getConnection()
                && listener.getConnection() == connection && ++respawns <= 3, "ACTUAL_NATIVE_RESPAWN_HANDLER_RETURN");
    }
    static boolean complete() { return deathDone && endDone; }

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

    private static void before(Path file) throws IOException {
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)
                && Files.size(file) <= 65_536, "FIXED_SERVER_RECEIPT_BOUND");
        var facts = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        var nextReference = facts.get("reference").getAsString();
        require(reference == null || reference.equals(nextReference), "SAME_FORMAL_REFERENCE");
        reference = nextReference; beforeEpoch = facts.get("sourceEpoch").getAsLong();
        require(beforeEpoch > 0 && facts.get("durationTicks").getAsInt() == 600, "ACTUAL_POSITIVE_OBLIGATION_CUE");
    }
    private static void write(Path output, String scene, Minecraft minecraft) throws IOException {
        var facts = new LinkedHashMap<String, Object>();
        facts.put("status", "ORIGINAL_NATIVE_CLONE_HANDLER_AND_CURRENT_COOLDOWN_MIRROR"); facts.put("scene", scene);
        facts.put("nativeRespawnHandlerReturns", respawns); facts.put("samePhysicalConnection", true);
        facts.put("originalDeathButtonClicks", deathClicks); facts.put("originalEndFinishClicks", endClicks);
        facts.put("sourceEpochBefore", beforeEpoch); facts.put("sourceEpoch", sourceEpoch); facts.put("sourceVersion", sourceVersion);
        facts.put("generation", generation); facts.put("syncSequence", sequence); facts.put("reference", reference);
        facts.put("state", state); facts.put("remainingTicks", remaining);
        facts.put("dimension", minecraft.level.dimension().location().toString()); facts.put("physicalOSInputClaim", false);
        P11C4aEvidence.write(output, "cooldown-clone-" + scene + "-client.json", facts);
    }
    private static boolean worldReady(Minecraft minecraft) {
        return minecraft.player != null && minecraft.level != null && minecraft.getConnection() == minecraft.player.connection
                && minecraft.getConnection().getConnection() == connection && minecraft.player.isAlive()
                && minecraft.getOverlay() == null && minecraft.screen == null;
    }
    private static boolean receipt(Path output, String leaf) throws IOException { return P11C4aEvidence.receiptPresent(output, leaf); }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "COOLDOWN_CLONE_CLIENT_" + code); }
}
