package com.yo1no.gramarye;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import org.lwjgl.glfw.GLFW;

/** Actual host keyboard callback prefix only. Existing host Leave driver owns all later input/teardown. */
public final class P11CooldownHostClientProbe {
    private static boolean inputReady, starter, focus, armed, cast, written;
    private static int sends;
    private static String reference, appliedReference, appliedState;
    private static long generation, sequence, sourceEpoch, sourceVersion;
    private P11CooldownHostClientProbe() { }
    public static void inputReady(boolean value) { if (P11CooldownHostProbe.selected()) inputReady = value; }
    public static void submitted(long sequence, int slot, int mask, boolean hintsAbsent) {
        if (!P11CooldownHostProbe.selected()) return;
        require(armed && cast && sends == 0 && sequence > 0 && slot == 0 && mask == 0 && hintsAbsent,
                "ONE_ORIGINAL_R_PAYLOAD"); armed = false; sends++;
    }
    public static void snapshot(long actualGeneration, long actualSequence, long epoch, long version,
            String currentReference, String state) {
        if (!P11CooldownHostProbe.selected() || !"host".equals(System.getProperty("gramarye.p11.online.role", ""))) return;
        generation = actualGeneration; sequence = actualSequence; sourceEpoch = epoch; sourceVersion = version;
        appliedReference = currentReference; appliedState = state;
    }
    static boolean prefix(Minecraft minecraft, Connection connection, String role, Path output, Path serverOutput) throws IOException {
        require(P11CooldownHostProbe.selected() && minecraft.isSameThread() && connection != null
                && connection.isConnected() && minecraft.player != null && minecraft.level != null
                && minecraft.getConnection() != null && minecraft.getConnection().getConnection() == connection,
                "CURRENT_NATIVE_CONNECTION");
        if (role.equals("b")) return P11C4aEvidence.cuePresent(serverOutput, "host-leave-arm.ready");
        require(role.equals("host") && connection.isMemoryConnection() && minecraft.getSingleplayerServer() != null,
                "INTEGRATED_OWNER_NOT_HAS_JOINED");
        if (written) return true;
        if (minecraft.getOverlay() != null || minecraft.screen != null) return false;
        if (!starter && P11C4aEvidence.cuePresent(serverOutput, "cooldown-host-starter.ready")) {
            starter = true; minecraft.getConnection().sendCommand("gramarye starter");
            P11C4aEvidence.cue(output, "cooldown-host-starter-sent.ready"); return false;
        }
        if (reference == null && P11C4aEvidence.receiptPresent(serverOutput, "cooldown-host-formal.json")) {
            var file = serverOutput.resolve("cooldown-host-formal.json");
            require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)
                    && Files.size(file) <= 16_384, "BOUNDED_FORMAL_RECEIPT");
            reference = JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("reference").getAsString();
        }
        if (!focus && reference != null && P11C4aEvidence.cuePresent(serverOutput, "cooldown-host-cast.ready")) {
            focus = true; GLFW.glfwFocusWindow(minecraft.getWindow().getWindow()); return false;
        }
        if (!cast && focus && inputReady && minecraft.isWindowActive() && reference.equals(appliedReference)
                && "READY".equals(appliedState) && generation > 0 && sequence > 0 && sourceEpoch > 0) {
            cast = true; armed = true;
            long window = minecraft.getWindow().getWindow(); int scan = GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_R);
            minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_R, scan, GLFW.GLFW_PRESS, 0);
            minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_R, scan, GLFW.GLFW_RELEASE, 0);
            return false;
        }
        if (P11C4aEvidence.cuePresent(serverOutput, "host-leave-arm.ready")) {
            require(cast && !armed && sends == 1,
                    "ACTUAL_HOST_INPUT_AND_ACTIVE_MIRROR");
            if (!reference.equals(appliedReference) || !"ACTIVE".equals(appliedState)) return false;
            P11C4aEvidence.write(output, "cooldown-host-input.json", Map.of("status", "ORIGINAL_HOST_R_AND_APPLIED_ACTIVE_MIRROR",
                    "originalRCallbacks", 1, "originalP9Sends", sends, "reference", reference,
                    "dispatchGeneration", generation, "syncSequence", sequence, "sourceEpoch", sourceEpoch,
                    "sourceVersion", sourceVersion, "physicalOsInputClaim", false, "hostHasJoinedClaim", false));
            written = true; return true;
        }
        return false;
    }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, "COOLDOWN_HOST_CLIENT_" + code); }
}
