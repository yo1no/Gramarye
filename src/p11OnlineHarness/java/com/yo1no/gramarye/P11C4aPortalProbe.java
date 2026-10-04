package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;

/** Excluded finite world fixture. No actor, source, portal-process, cooldown or effect is synthesized. */
public final class P11C4aPortalProbe {
    private static Run active;
    private P11C4aPortalProbe() { }

    static boolean selected() { return P11C4aScenario.MODE == P11C4aScenario.Mode.PORTAL; }

    /** Called at the first real End visit, before the original exit collision. */
    static boolean prepareExit(ServerPlayer actor, Path output) throws IOException {
        require(selected() && P11C4aEvidence.enabled() && actor.getServer().isSameThread(), "PORTAL_PREPARE_OWNER");
        require(actor.serverLevel().dimension() == Level.END && actor.isAlive()
                && !actor.seenCredits && !actor.wonGame && actor.connection.getConnection().isConnected()
                && actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor, "PORTAL_REAL_FIRST_END_ACTOR");
        if (actor.isOnPortalCooldown()) { return false; }
        var run = active;
        if (run == null) {
            var origin = actor.blockPosition().above(4).offset(4, 0, 0);
            run = new Run(actor, output, origin);
            require(origin.getY() > run.level.getMinBuildHeight()
                    && origin.getY() + 4 < run.level.getMaxBuildHeight(), "PORTAL_WORLD_BOUNDS");
            for (var entry : run.placed.entrySet()) {
                require(run.level.getBlockEntity(entry.getKey()) == null, "PORTAL_EXISTING_BLOCK_ENTITY");
                run.previous.put(entry.getKey(), run.level.getBlockState(entry.getKey()));
            }
            active = run;
            // Match native PortalShape.createPortalBlocks: UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE
            // (18) sends blocks without tearing down the partially assembled finite shape.
            for (var entry : run.placed.entrySet()) {
                run.attempted.put(entry.getKey(), entry.getValue());
                run.level.setBlock(entry.getKey(), entry.getValue(), 18);
            }
            require(new PortalShape(run.level, origin.above(), Direction.Axis.Z).isComplete(), "PORTAL_FRAME_NOT_NATIVE_COMPLETE");
            P11C4aEvidence.write(output, "portal-geometry.json", Map.of("x", origin.getX(), "y", origin.getY(),
                    "z", origin.getZ(), "blocks", run.placed.size(), "dimension", "END", "nativeShapeComplete", true));
            return false;
        }
        require(run.actor == actor && run.failure == null && !run.teleported, "PORTAL_PREPARE_EXACT_RUN");
        if (!P11C4aEvidence.cuePresent(output.resolveSibling("client-a"), "portal-blocks-observed.ready")) { return false; }
        // This existing fixture invokes the exact original admission gate once inside a real
        // native XP grant. Its ACTIVE_OPERATION enum is returned unchanged, never manufactured.
        P11C4aC6EarlyGateProbe.arm(actor, P11C4aC6EarlyGateProbe.Point.PLAY_FIRST);
        run.gateArmed = true;
        actor.connection.teleport(run.origin.getX() - .05, run.origin.getY() + 1.25,
                run.origin.getZ() + .5, actor.getYRot(), actor.getXRot());
        run.teleported = true;
        return true;
    }

    /** Fixed geometry shared with the client readback; all entries are real world blocks. */
    static LinkedHashMap<BlockPos, BlockState> layout(BlockPos origin) {
        var result = new LinkedHashMap<BlockPos, BlockState>();
        for (int y = 0; y <= 4; y++) {
            for (int z = -1; z <= 2; z++) {
                boolean frame = y == 0 || y == 4 || z == -1 || z == 2;
                result.put(origin.offset(0, y, z), frame ? Blocks.OBSIDIAN.defaultBlockState()
                        : Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.Z));
            }
        }
        result.put(origin.offset(-1, 0, 0), Blocks.OBSIDIAN.defaultBlockState());
        result.put(origin.offset(-1, 2, 0), Blocks.AIR.defaultBlockState());
        result.put(origin.offset(-1, 3, 0), Blocks.AIR.defaultBlockState());
        result.put(origin.offset(-1, 1, 0), Blocks.END_PORTAL.defaultBlockState());
        return result;
    }

    /** Existing actual nativeSend RETURN observer delegates here; packet/body unchanged. */
    public static void submitted(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = active;
        if (run == null || listener.getConnection() != run.actor.connection.getConnection()
                || !(packet instanceof ClientboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof P11TransitionStatePayload payload)) { return; }
        var state = payload.state();
        if (state.kind() != Kind.END || state.outcome() == Outcome.BINDING) { return; }
        if (!run.teleported || !run.gateArmed || !run.actor.getServer().isSameThread()) {
            run.failure = "PORTAL_STATE_OUTSIDE_EXACT_FIXTURE"; return;
        }
        if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.WAIT_NOTIFY) {
            if (state.reason() != Reason.ACTIVE_OPERATION || !run.actor.wonGame || run.actor.getHealth() <= 0) {
                run.failure = "PORTAL_NOT_REAL_ALIVE_END_FOP_REFUSAL"; return;
            }
            if (run.wait == null) { run.wait = state; }
        } else if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.MAY_TRY) {
            if (run.wait == null || !sameAttempt(run.wait, state)) { run.failure = "PORTAL_MAY_WITHOUT_REFUSAL"; return; }
            run.may = state;
        } else if (state.outcome() == Outcome.COMPLETED) { run.completed = state; }
    }

    /** Called only after existing parent proved the actual new B and sole native End frame. */
    static void finish(ServerPlayer successor) throws IOException {
        var run = active;
        require(run != null && run.actor.getServer().isSameThread() && run.failure == null
                && run.wait != null && run.may != null && run.completed != null
                && sameScene(run.wait, run.completed) && run.completed.requestSeq() > run.wait.requestSeq()
                && successor != run.actor && successor.isAlive() && successor.seenCredits && !successor.wonGame
                && successor.connection.getConnection() == run.actor.connection.getConnection()
                && P11C4aC6EarlyGateProbe.returned(), "PORTAL_NO_EXACT_FRESH_END_COMPLETION");
        require(P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-a"), "portal-client.json"), "PORTAL_CLIENT_NOT_COMPLETE");
        var gate = P11C4aC6EarlyGateProbe.reportAndRelease();
        require(Boolean.TRUE.equals(gate.get("originalReturned")) && Boolean.TRUE.equals(gate.get("nativeRewardReturned"))
                && ((Number) gate.get("originalGateCalls")).intValue() == 1
                && ((Number) gate.get("actualFopAtGate")).longValue() > 0
                && ((Number) gate.get("actualQctxAtGate")).longValue() == 0, "PORTAL_GATE_OBSERVATION_INVALID");
        restore(run);
        P11C4aEvidence.write(run.output, "portal-server.json", Map.of("status", "ACTUAL_FIRST_END_FOP_REFUSAL_AND_FRESH_RETRY_SUBSET",
                "gate", gate, "wait", run.wait, "mayTry", run.may, "completed", run.completed,
                "actualWorldBlocksRestored", true, "fullC4aAcceptance", false));
        active = null;
    }

    static void abort() {
        var run = active;
        if (run == null) { return; }
        require(run.actor.getServer().isSameThread(), "PORTAL_ABORT_THREAD");
        Throwable primary = null;
        try { P11C4aC6EarlyGateProbe.abort(); }
        catch (RuntimeException | Error failure) { primary = failure; }
        try { restore(run); }
        catch (RuntimeException | Error failure) {
            if (primary == null) { primary = failure; }
            else if (primary != failure) { primary.addSuppressed(failure); }
        } finally { active = null; }
        if (primary instanceof RuntimeException failure) { throw failure; }
        if (primary instanceof Error failure) { throw failure; }
    }

    private static void restore(Run run) {
        // Validate the entire owned set before changing any block. No neighbor-triggered
        // teardown is mistaken for external damage; the same known-shape flags preserve restoration.
        for (var entry : run.attempted.entrySet()) {
            var actual = run.level.getBlockState(entry.getKey());
            require(actual.equals(entry.getValue()) || actual.equals(run.previous.get(entry.getKey())), "PORTAL_BLOCK_CHANGED_EXTERNALLY");
        }
        for (var position : run.attempted.keySet()) { run.level.setBlock(position, run.previous.get(position), 18); }
    }
    private static boolean sameAttempt(State a, State b) { return sameScene(a, b) && a.requestSeq() == b.requestSeq(); }
    private static boolean sameScene(State a, State b) { return a.connectionEpoch() == b.connectionEpoch()
            && a.sceneSerial() == b.sceneSerial() && a.actorGeneration() == b.actorGeneration() && a.kind() == b.kind(); }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private static final class Run {
        final ServerPlayer actor;
        final ServerLevel level;
        final Path output;
        final BlockPos origin;
        final LinkedHashMap<BlockPos, BlockState> placed, previous = new LinkedHashMap<>(), attempted = new LinkedHashMap<>();
        boolean teleported, gateArmed;
        State wait, may, completed;
        String failure;
        Run(ServerPlayer actor, Path output, BlockPos origin) {
            this.actor = actor; this.level = actor.serverLevel(); this.output = output; this.origin = origin; placed = layout(origin);
        }
    }
}
