package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Excluded finite ordering fixture. No source root, sender, ticket or credits flag is minted. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aNativeSenderProbe {
    private static volatile Run active;
    private P11C4aNativeSenderProbe() { }

    static boolean selected() { return P11C4aScenario.MODE == P11C4aScenario.Mode.NATIVE_SENDERS; }

    static void armImmediate(MinecraftServer server, ServerPlayer actor, String role, Path output) {
        require(selected() && P11C4aEvidence.enabled() && active == null && server.isSameThread()
                && server.isDedicatedServer() && role.equals("a") && actor.getServer() == server
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor && actor.isAlive()
                && !actor.isFakePlayer() && P11NativeStorageBoundary.nativeDeliveryEligible(actor)
                && actor.connection.getConnection().isEncrypted() && !actor.connection.getConnection().isMemoryConnection(),
                "SENDERS_ARM_EXACT_AUTHENTICATED_NATIVE_ACTOR");
        var run = new Run(server, actor, role, output);
        active = run;
        P11C4aConfigPrimaryProbe.armSender(run.connection, output, true);
        server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, server);
    }

    /** Only the next real network-owned doTick is selected, not an additional invocation. */
    public static boolean beforeDoTick(ServerPlayer actor) {
        var run = active;
        if (run == null || actor != run.deathActor || run.deathEntered) { return false; }
        require(run.server.isSameThread() && run.connection.getPacketListener() == actor.connection
                && run.server.getPlayerList().getPlayer(actor.getUUID()) == actor && actor.isAlive(),
                "SENDERS_NATIVE_TICK_OWNER");
        run.deathEntered = true;
        run.deathTick = run.server.getTickCount();
        run.inOriginalTick = true;
        actor.kill();
        require(actor.isDeadOrDying(), "SENDERS_ORIGINAL_KILL_NOT_DEAD");
        return true;
    }

    /** Called only after the original native send returns within that selected doTick. */
    public static void healthSent(ServerPlayer actor, Packet<?> packet) {
        var run = active;
        if (run != null && actor == run.deathActor && run.inOriginalTick
                && packet instanceof ClientboundSetHealthPacket health && health.getHealth() == 0.0F) {
            run.nativeZeroHealthSends++;
        }
    }

    public static void afterDoTick(ServerPlayer actor, boolean selected, boolean normal) {
        if (!selected) { return; }
        var run = active;
        if (run == null || actor != run.deathActor) { return; }
        run.inOriginalTick = false;
        if (!normal) { run.failure = "SENDERS_ORIGINAL_DO_TICK_THROW"; return; }
        require(run.nativeZeroHealthSends == 1 && !run.reloadStarted && run.failure == null
                && P11C4aNativeObservations.tries(run.connection) == run.beforeTries,
                "SENDERS_HEALTH_OR_EARLY_TRY_BEFORE_FOP");
        run.nativeTickReturned = true;
    }

    /** Read-only observation of the original tickChildren resumeFlushing RETURN. */
    public static void flushed(net.minecraft.server.network.ServerCommonPacketListenerImpl listener) {
        var run = active;
        if (run == null || listener != run.deathActor.connection || !run.nativeTickReturned
                || run.nativeFlushReturns != 0) { return; }
        require(run.server.isSameThread() && run.connection.getPacketListener() == listener
                && !run.reloadStarted, "SENDERS_NATIVE_FLUSH_OWNER");
        run.nativeFlushReturns = 1;
        run.flushTick = run.server.getTickCount();
    }

    /** Notification after existing XP observer has measured the real O>0/Q=0 source. */
    static void fopObserved(ServerPlayer actor) {
        var run = active;
        if (run != null && actor == run.deathActor) {
            require(run.reloadStarted && !run.inOriginalTick && run.failure == null, "SENDERS_FOP_ORDER");
            run.fopObserved = true;
        }
    }

    public static void ingress(Object value, Connection connection) {
        var run = active;
        if (run == null || connection != run.connection
                || !(value instanceof P11TransitionProtocol.Request request)
                || request.command() != P11TransitionProtocol.Command.TRY) { return; }
        if (!run.fopObserved) { run.failure = "SENDERS_TRY_ARRIVED_BEFORE_REAL_FOP"; }
        if (run.secondEndStarted) { run.failure = "SENDERS_SECOND_END_UNEXPECTED_TRY"; }
    }

    /** Existing ServerTick.Post owner; the selected network tick has fully returned. */
    static boolean advanceReload() throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && ++run.waitTicks <= 2400
                && run.failure == null, "SENDERS_NATIVE_TICK_DEADLINE_OR_EARLY_INGRESS");
        if (!run.reloadStarted && run.nativeTickReturned && run.nativeFlushReturns == 1) {
            require(run.server.getTickCount() == run.deathTick && run.flushTick == run.deathTick
                    && P11C4aNativeObservations.tries(run.connection) == run.beforeTries,
                    "SENDERS_TRY_BEFORE_CALLBACK_RELEASE");
            run.reloadStarted = true;
            try {
                P11C4aReloadBlockerProbe.start(run.server, run.deathActor,
                        P11C4aReloadBlockerProbe.Mode.FOP, run.role, run.output);
            } catch (RuntimeException | Error primary) {
                P11C4aConfigPrimaryProbe.listener(P11C4aConfigPrimaryProbe.Stage.RELOAD_OWNER_FAILURE,
                        run.deathActor.connection, primary);
                throw primary;
            }
            require(run.failure == null && run.fopObserved, "SENDERS_EARLY_INGRESS_OR_MISSING_FOP");
        }
        return run.reloadStarted;
    }

    static void immediateFinished() throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && run.failure == null && run.fopObserved
                && run.nativeZeroHealthSends == 1 && !run.immediateFinished, "SENDERS_IMMEDIATE_FINISH");
        restoreRules(run);
        run.immediateFinished = true;
        P11C4aEvidence.write(run.output, "native-hidden-auto.json", Map.of(
                "status", "REAL_NATIVE_HEALTH_BEFORE_FOP_THEN_HIDDEN_AUTO_DEDUPE",
                "originalDoTickInvocations", 1, "originalZeroHealthSendReturns", run.nativeZeroHealthSends,
                "originalResumeFlushingReturnsBeforeReload", run.nativeFlushReturns,
                "originalFlushServerTick", run.flushTick,
                "sameNativeDeathTickPostFop", run.flushTick == run.deathTick,
                "earlyTryObserved", false, "fopMeasuredByExistingXpObserver", run.fopObserved,
                "evidenceRequiresReloadFopAndClientHiddenAutoReceipts", true, "fullC4aAcceptance", false));
        P11C4aEvidence.cue(run.output, run.role + "-second-end-prepare.ready");
    }

    static boolean secondEnd(ServerPlayer actor, Path clientOutput) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && run.immediateFinished && run.failure == null
                && ++run.endTicks <= 2400 && actor.connection.getConnection() == run.connection
                && run.connection.isConnected() && run.server.getPlayerList().getPlayer(actor.getUUID()) == actor,
                "SENDERS_SECOND_END_OWNER_OR_DEADLINE");
        if (!run.secondEndStarted) {
            if (!P11C4aEvidence.cuePresent(clientOutput, "second-end-armed.ready")) { return false; }
            require(actor.seenCredits && !actor.wonGame && actor.isAlive() && actor.level().dimension() == Level.OVERWORLD,
                    "SENDERS_SECOND_END_REQUIRES_ORIGINAL_SEEN_CREDITS");
            if (actor.isOnPortalCooldown()) { run.cooldownWaitTicks++; return false; }
            run.endActor = actor;
            run.sourceBody = P11NativeStorageBoundary.nativeSourceOwner(actor).body(actor);
            require(run.sourceBody != null, "SENDERS_SECOND_END_SOURCE_BODY");
            run.endTries = P11C4aNativeObservations.tries(run.connection);
            run.endBodies = count(run, P11C4aNativeObservations.Event.RESPAWN_CALL_ENTER);
            run.endFrames = count(run, P11C4aNativeObservations.Event.RESPAWN_FRAME_PRODUCED);
            run.secondEndStarted = true;
            run.portal = portal(actor);
            return false;
        }
        require(actor == run.endActor && actor.seenCredits && !actor.wonGame
                && P11NativeStorageBoundary.nativeSourceOwner(actor).body(actor) == run.sourceBody
                && P11C4aNativeObservations.tries(run.connection) == run.endTries
                && count(run, P11C4aNativeObservations.Event.RESPAWN_CALL_ENTER) == run.endBodies
                && count(run, P11C4aNativeObservations.Event.RESPAWN_FRAME_PRODUCED) == run.endFrames,
                "SENDERS_SECOND_END_ACTOR_SOURCE_OR_CONTROL_CHANGED");
        if (run.endStage == 0 && actor.level().dimension() == Level.END && run.dimensionChanges == 1) {
            restorePortal(run);
            run.endStage = 1;
        }
        if (run.endStage == 1 && P11C4aEvidence.cuePresent(clientOutput, "second-end-view.ready")) {
            // Unlike first credits, seenCredits=true uses Entity's ordinary cooldown.
            // Stay outside any return portal until the actual native countdown expires.
            if (actor.isOnPortalCooldown()) { run.cooldownWaitTicks++; return false; }
            run.portal = portal(actor);
            run.endStage = 2;
        }
        if (run.endStage != 2 || actor.level().dimension() != Level.OVERWORLD || run.dimensionChanges != 2) { return false; }
        restorePortal(run);
        if (!P11C4aEvidence.receiptPresent(clientOutput, "second-end.json")) { return false; }
        require(run.server.saveEverything(true, false, false), "SENDERS_SECOND_END_ORIGINAL_SAVE");
        var saved = NbtIo.readCompressed(run.server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(actor.getUUID() + ".dat"), NbtAccounter.unlimitedHeap());
        require(saved.getBoolean("seenCredits") && saved.getString("Dimension").equals("minecraft:overworld"),
                "SENDERS_SECOND_END_NATIVE_DISK_READBACK");
        P11C4aEvidence.write(run.output, "second-end.json", Map.of(
                "status", "SECOND_NATIVE_END_SAME_ACTOR_DIMENSION_NONMATCH_ZERO_TRY",
                "originalSeenCreditsAtStart", true, "sameServerActorAndSourceBody", true,
                "actualDimensionChanges", run.dimensionChanges, "additionalTries", 0,
                "p11RespawnBodies", 0, "p11ExpectedFrames", 0, "originalSeenCreditsDiskReadback", true,
                "directWinGameSenderExistsInPinnedVersion", false, "fullC4aAcceptance", false));
        P11C4aConfigPrimaryProbe.finish(run.connection);
        active = null;
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        var run = active;
        if (run == null || !run.secondEndStarted || event.getEntity() != run.endActor) { return; }
        require(run.server.isSameThread(), "SENDERS_DIMENSION_NOT_MAIN");
        boolean entering = run.dimensionChanges == 0 && event.getFrom() == Level.OVERWORLD && event.getTo() == Level.END;
        boolean leaving = run.dimensionChanges == 1 && event.getFrom() == Level.END && event.getTo() == Level.OVERWORLD;
        require(entering || leaving, "SENDERS_DIMENSION_SEQUENCE");
        run.dimensionChanges++;
    }

    static void abort() { var run = active; if (run != null) { restoreRules(run); restorePortal(run); active = null; } }
    private static long count(Run run, P11C4aNativeObservations.Event event) { return P11C4aNativeObservations.count(run.connection, event); }
    private static void restoreRules(Run run) {
        if (run.rulesRestored) { return; }
        run.server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(run.keepInventory, run.server);
        run.server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(run.immediate, run.server);
        run.rulesRestored = true;
    }
    private static Portal portal(ServerPlayer actor) {
        var level = actor.serverLevel(); var position = actor.blockPosition().above(4).offset(4, 0, 0);
        var positions = List.of(position.below(), position, position.above(), position.above(2));
        var previous = new ArrayList<BlockState>();
        var placed = List.of(Blocks.OBSIDIAN.defaultBlockState(), Blocks.END_PORTAL.defaultBlockState(),
                Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState());
        require(position.getY() > level.getMinBuildHeight() && position.getY() + 2 < level.getMaxBuildHeight(), "SENDERS_PORTAL_BOUND");
        for (var pos : positions) { require(level.getBlockEntity(pos) == null, "SENDERS_PORTAL_BLOCK_ENTITY"); previous.add(level.getBlockState(pos)); }
        for (int i = 0; i < positions.size(); i++) { level.setBlock(positions.get(i), placed.get(i), 3); }
        actor.connection.teleport(position.getX() + .5, position.getY() + .25, position.getZ() + .5, actor.getYRot(), actor.getXRot());
        return new Portal(level, positions, previous, placed);
    }
    private static void restorePortal(Run run) {
        var portal = run.portal;
        if (portal == null) { return; }
        for (int i = 3; i >= 0; i--) {
            require(portal.level.getBlockState(portal.positions.get(i)).equals(portal.placed.get(i)), "SENDERS_PORTAL_CHANGED_EXTERNALLY");
            portal.level.setBlock(portal.positions.get(i), portal.previous.get(i), 3);
        }
        run.portal = null;
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private record Portal(ServerLevel level, List<BlockPos> positions, List<BlockState> previous, List<BlockState> placed) { }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer deathActor; final Connection connection;
        final String role; final Path output; final boolean keepInventory, immediate; final long beforeTries;
        volatile String failure; volatile boolean fopObserved, secondEndStarted;
        boolean deathEntered, inOriginalTick, nativeTickReturned, reloadStarted, rulesRestored, immediateFinished;
        int nativeZeroHealthSends, nativeFlushReturns, flushTick, deathTick, waitTicks, endTicks, endStage, dimensionChanges, cooldownWaitTicks;
        long endTries, endBodies, endFrames; ServerPlayer endActor; Object sourceBody; Portal portal;
        Run(MinecraftServer server, ServerPlayer actor, String role, Path output) {
            this.server = server; deathActor = actor; connection = actor.connection.getConnection(); this.role = role; this.output = output;
            keepInventory = server.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
            immediate = server.getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
            beforeTries = P11C4aNativeObservations.tries(connection);
        }
    }
}
