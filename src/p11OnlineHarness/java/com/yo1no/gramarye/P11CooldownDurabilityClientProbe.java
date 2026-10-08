package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

/** Selected same-JVM client evidence from the original accepted mirror and original HUD draw. */
final class P11CooldownDurabilityClientProbe {
    private static Connection connection;
    private static Path output;
    private static long generation, sequence, epoch, version;
    private static String reference, state, reason;
    private static int remaining;
    private static Map<String, Object> firstMirror, firstDraw;
    private static boolean mirrorWritten, drawWritten;
    private P11CooldownDurabilityClientProbe() { }

    static void tick(Minecraft minecraft, Connection exact, Path destination, Path serverOutput) throws IOException {
        if (!P11CooldownDurabilityProbe.selected()) return;
        require(minecraft.isSameThread() && exact != null && exact.isConnected() && minecraft.player != null
                && minecraft.getConnection() == minecraft.player.connection
                && minecraft.getConnection().getConnection() == exact, "SAME_LIVE_CLIENT");
        if (connection == null) { connection = exact; output = destination; }
        require(connection == exact && output.equals(destination), "NO_RECONNECT_OR_OUTPUT_CHANGE");
        if (firstMirror != null && !mirrorWritten) {
            P11C4aEvidence.write(output, "cooldown-durability-mirror.json", firstMirror); mirrorWritten = true;
        }
        if (firstDraw != null && !drawWritten) {
            P11C4aEvidence.write(output, "cooldown-durability-hud.json", firstDraw); drawWritten = true;
        }
    }
    /** Parent calls only after the actual mirror accepted this precise snapshot and native generation. */
    static void snapshot(long actualGeneration, long actualSequence, long sourceEpoch, long sourceVersion,
            String sourceState, String sourceReason, String exactReference, String entryState, String entryReason, int ticks) {
        if (!P11CooldownDurabilityProbe.selected()) return;
        require(Minecraft.getInstance().isSameThread() && actualGeneration > 0 && actualSequence > sequence,
                "ORIGINAL_ACCEPTED_SNAPSHOT_ORDER");
        generation = actualGeneration; sequence = actualSequence; epoch = sourceEpoch; version = sourceVersion;
        reference = exactReference; state = entryState; reason = entryReason; remaining = ticks;
        if (!"SAVE_FAILED".equals(reason)) return;
        require("AVAILABLE".equals(sourceState) && "NONE".equals(sourceReason) && reference != null && epoch > 0
                && state.equals(P11CooldownDurabilityProbe.clearSelected() ? "UNAVAILABLE" : "ACTIVE")
                && (P11CooldownDurabilityProbe.clearSelected() ? ticks == 0 : ticks > 0 && ticks <= 600),
                "KNOWN_FACT_NOT_FALSE_READY_OR_RECOVERY");
        if (firstMirror == null) firstMirror = facts("ORIGINAL_ACCEPTED_SAVE_FAILED_COOLDOWN", false);
    }
    static void hudDrawn(boolean exactLabel, long actualGeneration, long actualSequence, String exactReference,
            String entryState, int ticks) {
        if (!P11CooldownDurabilityProbe.selected() || firstMirror == null || !"SAVE_FAILED".equals(reason) || firstDraw != null) return;
        require(Minecraft.getInstance().isSameThread() && exactLabel && actualGeneration == generation && actualSequence == sequence
                && reference.equals(exactReference) && state.equals(entryState) && ticks == remaining,
                "ORIGINAL_HUD_DRAW_OF_EXACT_ACCEPTED_FAILURE_STATE");
        firstDraw = facts("ORIGINAL_SAVE_FAILED_COOLDOWN_HUD_DRAW_RETURN", true);
    }
    static boolean complete() { return mirrorWritten && drawWritten; }
    private static Map<String, Object> facts(String status, boolean rendered) {
        var values = new LinkedHashMap<String, Object>(); values.put("status", status); values.put("generation", generation);
        values.put("sequence", sequence); values.put("sourceEpoch", epoch); values.put("sourceVersion", version);
        values.put("reference", reference); values.put("state", state); values.put("reason", reason);
        values.put("remainingTicks", remaining); values.put("originalHudDrawReturned", rendered);
        values.put("physicalOSInputClaim", false); return Map.copyOf(values);
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "COOLDOWN_DURABILITY_CLIENT_" + code); }
}
