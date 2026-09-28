package com.yo1no.gramarye;

import java.util.List;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;

/** Excluded observation of real native packet handlers across CONFIG handoff and fresh reconnect. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11NativeDeliveryProbe {
    private static final ResourceLocation ROOT = id("delivery_root");
    private static final ResourceLocation RECIPE = id("delivery_recipe");
    private static final ResourceLocation BREAD = ResourceLocation.withDefaultNamespace("bread");
    private static final String TAG = "p11_native_delivery_tail";
    private static volatile State active;

    private P11NativeDeliveryProbe() {}

    static void prepare(MinecraftServer server, ServerPlayer actor) {
        require(active == null && server.isSameThread() && !actor.isFakePlayer()
                        && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                        && P11NativeStorageBoundary.nativeDeliveryEligible(actor),
                "delivery fixture requires exact native online A");
        var root = server.getAdvancements().get(ROOT);
        var recipe = server.getAdvancements().get(RECIPE);
        require(root != null && recipe != null && server.getFunctions().get(id("delivery_tail")).isPresent(),
                "native delivery fixture pack is absent");
        require(!actor.getAdvancements().getOrStartProgress(root).hasProgress()
                        && !actor.getAdvancements().getOrStartProgress(recipe).hasProgress()
                        && !actor.getTags().contains(TAG), "delivery fixture was already consumed");
        var state = new State(actor);
        active = state;
        actor.resetRecipes(List.of(server.getRecipeManager().byKey(BREAD).orElseThrow()));
        require(!actor.getRecipeBook().contains(BREAD), "native bread removal failed");
        require(actor.getAdvancements().award(root, "manual"), "native zero-reward visible fixture did not complete");
        actor.getAdvancements().flushDirty(actor);
        require(state.rootProgressEvents == 1, "native visible fixture progress observer was not reached");
    }

    /** Called only inside the parent's authenticated engineering scope after real normal removal. */
    static String detached(MinecraftServer server, ServerPlayer actor) {
        var state = requireState();
        require(server.isSameThread() && state.original == actor && actor.isRemoved()
                        && server.getPlayerList().getPlayer(actor.getUUID()) == null
                        && !P11NativeStorageBoundary.nativeDeliveryEligible(actor)
                        && !state.detachedDone, "recipe sink fixture did not reach exact detached A once");
        state.grants++;
        AdvancementRewards.Builder.recipe(BREAD).runs(id("delivery_tail")).build().grant(actor);
        require(actor.getRecipeBook().contains(BREAD)
                        && ((P11RecipeDelivery.Access) actor.getRecipeBook()).p11$needsRecipeSync()
                        && actor.getTags().contains(TAG) && state.recipeProgressEvents == 1,
                "detached native grant did not finish logical recipe, criterion, function and delivery obligation");
        state.detachedDone = true;
        return "recipeDetached=NATIVE_SINGLE_GRANT_RECIPE_CRITERION_FUNCTION_CONTINUED\n"
                + "needsRecipeFullSync=true\nrecipeGrantCalls=" + state.grants + '\n';
    }

    static boolean clientReady() {
        var state = active;
        return state != null && state.sameReset && state.sameRecipeInit;
    }

    static String finish(MinecraftServer server, ServerPlayer actor) {
        var state = requireState();
        require(server.isSameThread() && actor != state.original && actor.getUUID().equals(state.playerId)
                        && server.getPlayerList().getPlayer(state.playerId) == actor
                        && actor.getAdvancements() == state.canonical && state.detachedDone
                        && actor.getRecipeBook().contains(BREAD) && actor.getTags().contains(TAG)
                        && !((P11RecipeDelivery.Access) actor.getRecipeBook()).p11$needsRecipeSync(),
                "real replacement B lost canonical progress, logical recipe or current INIT completion");
        require(clientReady() && state.rootProgressEvents == 1 && state.recipeProgressEvents == 1
                        && state.otherActorRootEvents == 0 && state.otherActorRecipeEvents == 0
                        && state.grants == 1 && state.breadAddPackets == 0,
                "new B did not receive native full state or repeated a grant/criterion/ADD packet");
        state.sameDone = true;
        // The companion needs only player-id and old client transport identity for reopen.
        // Production state never retains these fixture references or client objects.
        state.original = null;
        state.canonical = null;
        state.firstListener = null;
        return "P11-NATIVE-DELIVERY-SAME-TRANSPORT-V1\n"
                + "sameCanonicalPA=true\nnewClientListenerSameConnection=true\n"
                + "advancementReset=ACTUAL_NATIVE_HANDLER_RETURN_WITH_COMPLETED_ROOT\n"
                + "recipeInit=ACTUAL_NATIVE_HANDLER_RETURN_AND_CLIENT_BREAD_KNOWN\n"
                + "manualAdvancementProgressEvents=1\nrecipeUnlockProgressEvents=1\n"
                + "nativeRecipeGrantCalls=1\nbreadAddPackets=0\nreplayedReward=false\n"
                + state.sameMeasurements.report("PREPARE_A_THROUGH_SAME_CONNECTION_B") + "RESULT=PASS\n";
    }

    static boolean freshReconnectObserved(Minecraft minecraft) {
        var state = active;
        return state != null && state.sameDone && state.freshReset && state.freshRecipeInit
                && minecraft.player != null && minecraft.player.getUUID().equals(state.playerId)
                && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() != state.firstConnection;
    }

    static String freshReconnectReport(Minecraft minecraft) {
        require(freshReconnectObserved(minecraft), "fresh reconnect native handlers have not both completed");
        require(active.otherActorRootEvents == 0 && active.otherActorRecipeEvents == 0
                        && active.breadAddPackets == 0, "fresh reconnect replayed logical progress or recipe ADD");
        var measurements = active.freshMeasurements.report("AFTER_B_FINISH_THROUGH_FRESH_CONNECTION");
        active = null;
        return "P11-NATIVE-DELIVERY-FRESH-CONNECTION-V1\n"
                + "newClientConnection=true\nadvancementReset=ACTUAL_NATIVE_HANDLER_RETURN_WITH_COMPLETED_ROOT\n"
                + "recipeInit=ACTUAL_NATIVE_HANDLER_RETURN_AND_CLIENT_BREAD_KNOWN\n"
                + "additionalRecipeGrantCalls=0\nfixtureReferencesReleased=true\n" + measurements + "RESULT=PASS\n";
    }

    /** RETURN is after PacketUtils thread transfer and the real ClientAdvancements.update consumer. */
    public static void afterAdvancements(ClientPacketListener listener, ClientboundUpdateAdvancementsPacket packet) {
        var state = active;
        var minecraft = Minecraft.getInstance();
        if (state == null || minecraft.player == null || !minecraft.player.getUUID().equals(state.playerId)) { return; }
        if (packet.shouldReset()) {
            var measured = state.measurements();
            measured.clientResets++;
            measured.clientResetAdded += packet.getAdded().size();
            measured.clientResetRemoved += packet.getRemoved().size();
            measured.clientResetProgress += packet.getProgress().size();
        }
        var progress = packet.getProgress().get(ROOT);
        if (progress == null || !progress.isDone() || listener.getAdvancements().get(ROOT) == null) { return; }
        if (state.firstConnection == null) {
            state.firstConnection = listener.getConnection();
            state.firstListener = listener;
        } else if (packet.shouldReset() && listener.getConnection() == state.firstConnection
                && listener != state.firstListener && !state.sameDone) {
            state.sameReset = true;
        } else if (packet.shouldReset() && state.sameDone && listener.getConnection() != state.firstConnection) {
            state.freshReset = true;
        }
    }

    /** RETURN observes native INIT application, not a fabricated book mutation or packet model. */
    public static void afterRecipes(ClientPacketListener listener, ClientboundRecipePacket packet) {
        var state = active;
        var minecraft = Minecraft.getInstance();
        if (state == null || minecraft.player == null || !minecraft.player.getUUID().equals(state.playerId)) { return; }
        if (packet.getState() == ClientboundRecipePacket.State.INIT) {
            var measured = state.measurements();
            measured.clientRecipeInits++;
            measured.clientRecipes += packet.getRecipes().size();
            measured.clientHighlights += packet.getHighlights().size();
        }
        if (!packet.getRecipes().contains(BREAD)) { return; }
        if (packet.getState() == ClientboundRecipePacket.State.ADD) { state.breadAddPackets++; }
        if (packet.getState() != ClientboundRecipePacket.State.INIT
                || !minecraft.player.getRecipeBook().contains(BREAD) || state.firstConnection == null) { return; }
        if (!state.sameDone && listener.getConnection() == state.firstConnection && listener != state.firstListener) {
            state.sameRecipeInit = true;
        } else if (state.sameDone && listener.getConnection() != state.firstConnection) {
            state.freshRecipeInit = true;
        }
    }

    public static void resetBuilt(PlayerAdvancements canonical, boolean reset,
            ClientboundUpdateAdvancementsPacket packet, long nanos) {
        var state = active;
        if (state == null || !reset) { return; }
        var actor = ((P11CanonicalAdvancements.Access) canonical).p11$associatedPlayer();
        if (actor == null || !actor.getUUID().equals(state.playerId) || !actor.getServer().isSameThread()) { return; }
        var measured = state.measurements();
        measured.resetBuildAttempts++;
        measured.resetBuildNanos += Math.max(0, nanos);
        if (packet == null) { measured.resetBuildFailures++; return; }
        measured.resetBuilds++;
        measured.resetBuiltAdded += packet.getAdded().size();
        measured.resetBuiltRemoved += packet.getRemoved().size();
        measured.resetBuiltProgress += packet.getProgress().size();
    }

    public static void beforeSend(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var measured = serverMeasurements(listener);
        if (measured == null) { return; }
        if (packet instanceof ClientboundUpdateAdvancementsPacket update && update.shouldReset()) {
            measured.resetSendAttempts++;
        } else if (packet instanceof ClientboundRecipePacket recipe
                && recipe.getState() == ClientboundRecipePacket.State.INIT) { measured.recipeInitSendAttempts++; }
    }

    public static void afterSend(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var measured = serverMeasurements(listener);
        if (measured == null) { return; }
        if (packet instanceof ClientboundUpdateAdvancementsPacket update && update.shouldReset()) {
            measured.resetSubmissions++;
            measured.resetSubmittedAdded += update.getAdded().size();
            measured.resetSubmittedRemoved += update.getRemoved().size();
            measured.resetSubmittedProgress += update.getProgress().size();
        } else if (packet instanceof ClientboundRecipePacket recipe
                && recipe.getState() == ClientboundRecipePacket.State.INIT) {
            measured.recipeInitSubmissions++;
            measured.submittedRecipes += recipe.getRecipes().size();
            measured.submittedHighlights += recipe.getHighlights().size();
        }
    }

    private static SyncMeasurements serverMeasurements(ServerCommonPacketListenerImpl listener) {
        var state = active;
        if (state == null || !(listener instanceof ServerGamePacketListenerImpl game)
                || !game.player.getUUID().equals(state.playerId) || !game.player.getServer().isSameThread()) { return null; }
        return state.measurements();
    }

    @SubscribeEvent
    static void progress(AdvancementEvent.AdvancementProgressEvent event) {
        var state = active;
        if (state == null || !event.getEntity().getUUID().equals(state.playerId)) { return; }
        if (event.getAdvancement().id().equals(ROOT)) {
            if (event.getEntity() == state.original) { state.rootProgressEvents++; }
            else { state.otherActorRootEvents++; }
        }
        if (event.getAdvancement().id().equals(RECIPE)) {
            if (event.getEntity() == state.original) { state.recipeProgressEvents++; }
            else { state.otherActorRecipeEvents++; }
        }
    }

    private static final class State {
        final java.util.UUID playerId;
        ServerPlayer original;
        PlayerAdvancements canonical;
        volatile ClientPacketListener firstListener;
        volatile Connection firstConnection;
        volatile boolean sameReset, sameRecipeInit, freshReset, freshRecipeInit, sameDone;
        volatile int breadAddPackets;
        boolean detachedDone;
        volatile int rootProgressEvents, recipeProgressEvents, otherActorRootEvents, otherActorRecipeEvents;
        int grants;
        final SyncMeasurements sameMeasurements = new SyncMeasurements();
        final SyncMeasurements freshMeasurements = new SyncMeasurements();
        SyncMeasurements measurements() { return sameDone ? freshMeasurements : sameMeasurements; }
        State(ServerPlayer actor) { original = actor; playerId = actor.getUUID(); canonical = actor.getAdvancements(); }
    }

    /** Two fixed scalar cells only; no packet, collection, payload or listener history is retained. */
    private static final class SyncMeasurements {
        volatile long resetBuildAttempts, resetBuilds, resetBuildFailures, resetBuildNanos;
        volatile long resetBuiltAdded, resetBuiltRemoved, resetBuiltProgress;
        volatile long resetSendAttempts, resetSubmissions, resetSubmittedAdded, resetSubmittedRemoved, resetSubmittedProgress;
        volatile long recipeInitSendAttempts, recipeInitSubmissions, submittedRecipes, submittedHighlights;
        volatile long clientResets, clientResetAdded, clientResetRemoved, clientResetProgress;
        volatile long clientRecipeInits, clientRecipes, clientHighlights;
        String report(String scope) {
            return "measurementScope=" + scope + '\n'
                    + "resetBuildAttempts=" + resetBuildAttempts + ";completed=" + resetBuilds
                    + ";failed=" + resetBuildFailures + ";constructorNanos=" + resetBuildNanos
                    + ";added=" + resetBuiltAdded + ";removed=" + resetBuiltRemoved + ";progress=" + resetBuiltProgress + '\n'
                    + "resetNativeSendAttempts=" + resetSendAttempts + ";normalSubmissions=" + resetSubmissions
                    + ";added=" + resetSubmittedAdded + ";removed=" + resetSubmittedRemoved + ";progress=" + resetSubmittedProgress + '\n'
                    + "recipeInitNativeSendAttempts=" + recipeInitSendAttempts + ";normalSubmissions=" + recipeInitSubmissions
                    + ";recipes=" + submittedRecipes + ";highlights=" + submittedHighlights + '\n'
                    + "clientResetHandlerReturns=" + clientResets + ";added=" + clientResetAdded
                    + ";removed=" + clientResetRemoved + ";progress=" + clientResetProgress + '\n'
                    + "clientRecipeInitHandlerReturns=" + clientRecipeInits + ";recipes=" + clientRecipes
                    + ";highlights=" + clientHighlights + "\nsubmissionMeans=LOCAL_NORMAL_RETURN_NOT_REMOTE_ACK\n";
        }
    }
    private static State requireState() {
        var state = active;
        require(state != null, "native delivery fixture was not prepared");
        return state;
    }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye_p11_engineering", path);
    }
    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
