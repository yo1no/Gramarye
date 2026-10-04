package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;

/** Original client handler RETURN observations, not server-side send claims. */
public final class P11C4aRewardContinuityClientProbe {
    private static final ResourceLocation ROOT = ResourceLocation.fromNamespaceAndPath("gramarye_p11_engineering", "delivery_root");
    private static final ResourceLocation BREAD = ResourceLocation.withDefaultNamespace("bread");
    private static Run active;
    private P11C4aRewardContinuityClientProbe() {}
    static void tick(Minecraft minecraft, Connection connection, String role, Path output) throws IOException {
        if (!P11C4aRewardContinuityProbe.selected() || !role.equals("a")) { return; }
        if (active == null) {
            require(minecraft.isSameThread() && connection != null && connection.isConnected(), "REWARD_CLIENT_INITIAL_CONNECTION");
            active = new Run(connection, output);
        }
        var r = active;
        require(r.connection == connection && r.failure == null, "REWARD_CLIENT_OBSERVER_FAILED");
        if (r.complete) {
            require(r.starts == 1 && r.resets == 1 && r.inits == 1 && r.breadAdds == 0, "REWARD_CLIENT_LATE_REPLAY");
            return;
        }
        if (r.resets == 0 || r.inits == 0) { return; }
        require(r.starts == 1 && r.resets == 1 && r.inits == 1 && r.breadAdds == 0
                && r.resetRootDone && r.initBreadKnown && minecraft.player != null && minecraft.level != null
                && minecraft.getConnection() == r.next && r.next != r.previous
                && r.next.getConnection() == r.connection && r.connection.getPacketListener() == r.next,
                "REWARD_CLIENT_CURRENT_RESET_INIT_NOT_PROVEN");
        P11C4aEvidence.write(r.output, "reward-delivery-client.json", Map.of(
                "status", "PASS_ACTUAL_SAME_CONNECTION_NEW_LISTENER_RESET_AND_RECIPE_INIT",
                "configurationStartHandlerReturns", r.starts, "advancementResetHandlerReturns", r.resets,
                "recipeInitHandlerReturns", r.inits, "breadAddHandlerReturns", r.breadAdds,
                "completedRootPresentInActualReset", r.resetRootDone, "breadKnownAfterActualInit", r.initBreadKnown,
                "sameConnectionNewListener", true, "rewardOrPacketReplayUsed", false));
        r.complete = true;
    }
    public static void configurationStarted(ClientPacketListener listener) {
        var r = active; if (r == null || listener.getConnection() != r.connection) { return; }
        observe(r, () -> {
            var minecraft = Minecraft.getInstance();
            require(minecraft.isSameThread() && minecraft.player == null && minecraft.level == null && r.starts++ == 0,
                    "REWARD_CLIENT_CONFIG_START_NOT_ORIGINAL_ACTORLESS_RETURN");
            r.previous = listener;
        });
    }
    public static void advancements(ClientPacketListener listener, ClientboundUpdateAdvancementsPacket packet) {
        var r = active; if (r == null || r.starts == 0 || listener.getConnection() != r.connection || !packet.shouldReset()) { return; }
        observe(r, () -> {
            var minecraft = Minecraft.getInstance();
            require(minecraft.isSameThread() && listener != r.previous && minecraft.player != null
                    && minecraft.getConnection() == listener && r.connection.getPacketListener() == listener,
                    "REWARD_CLIENT_RESET_NOT_CURRENT_NATIVE_HANDLER");
            r.resets++; r.next = listener;
            var progress = packet.getProgress().get(ROOT);
            r.resetRootDone = progress != null && progress.isDone() && listener.getAdvancements().get(ROOT) != null;
        });
    }
    public static void recipes(ClientPacketListener listener, ClientboundRecipePacket packet) {
        var r = active; if (r == null || r.starts == 0 || listener.getConnection() != r.connection) { return; }
        observe(r, () -> {
            var minecraft = Minecraft.getInstance();
            require(minecraft.isSameThread() && listener != r.previous && minecraft.player != null
                    && minecraft.getConnection() == listener && r.connection.getPacketListener() == listener,
                    "REWARD_CLIENT_RECIPE_NOT_CURRENT_NATIVE_HANDLER");
            if (packet.getState() == ClientboundRecipePacket.State.ADD && packet.getRecipes().contains(BREAD)) { r.breadAdds++; }
            if (packet.getState() == ClientboundRecipePacket.State.INIT) {
                r.inits++; r.next = listener;
                r.initBreadKnown = packet.getRecipes().contains(BREAD) && minecraft.player.getRecipeBook().contains(BREAD);
            }
        });
    }
    static boolean complete() { return active != null && active.complete && active.failure == null; }
    static void release() { active = null; }
    private static void observe(Run r, Runnable body) {
        try { body.run(); } catch (RuntimeException | Error failure) { if (r.failure == null) { r.failure = "REWARD_CLIENT_NATIVE_OBSERVER_FAILED"; } }
    }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private static final class Run {
        final Connection connection; final Path output; ClientPacketListener previous, next;
        int starts, resets, inits, breadAdds; boolean resetRootDone, initBreadKnown, complete; String failure;
        Run(Connection connection, Path output) { this.connection = connection; this.output = output; }
    }
}
