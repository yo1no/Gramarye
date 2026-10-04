package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;

/** Client-only original resource reload, without a server reload, cast or world reset. */
public final class P11L1ClientResourceProbe {
    private static Connection connection;
    private static LocalPlayer player;
    private static CompletableFuture<Void> future;
    private static Path output;
    private static boolean completed;
    private P11L1ClientResourceProbe() {}

    /** The parent calls once on the server's old-work-live cue; the server never waits/stalls work. */
    public static void begin(Minecraft minecraft, Path child) throws IOException {
        require(P11L1RevisionProbe.selected() && minecraft.isSameThread() && future == null
                && minecraft.player != null && minecraft.getConnection() == minecraft.player.connection
                && minecraft.getOverlay() == null && minecraft.getConnection().getConnection().isConnected(), "BEGIN");
        player = minecraft.player; connection = minecraft.getConnection().getConnection(); output = child;
        future = minecraft.reloadResourcePacks();
        require(future != null, "ORIGINAL_FUTURE");
        P11C4aEvidence.write(output, "client-resource-reload-started.json", Map.of(
                "status", "ORIGINAL_CLIENT_RESOURCE_RELOAD_CALLED_NOT_SERVER_RELOAD", "originalCalls", 1));
    }

    public static boolean tick(Minecraft minecraft) throws IOException {
        if (future == null || completed) { return completed; }
        require(minecraft.isSameThread() && minecraft.player == player && minecraft.getConnection() == player.connection
                && player.connection.getConnection() == connection && connection.isConnected(), "UNCHANGED_ACTUAL_PLAY");
        if (!future.isDone()) { return false; }
        future.join();
        completed = true;
        P11C4aEvidence.write(output, "client-resource-reload-completed.json", Map.of(
                "status", "ORIGINAL_CLIENT_FUTURE_NORMAL_SAME_PLAY", "originalCalls", 1,
                "sameConnection", true, "samePlayer", true, "serverWorkConclusion", "REQUIRES_SEPARATE_SERVER_OBSERVATION"));
        return true;
    }

    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "L1_CLIENT_RELOAD_" + code); }
}
