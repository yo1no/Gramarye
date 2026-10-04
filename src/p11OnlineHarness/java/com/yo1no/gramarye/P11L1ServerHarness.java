package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Excluded actual R/P7/P5 experiment. No work, clock, permit or native-credit setter. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11L1ServerHarness {
    private static final Map<Connection, UUID> AUTH = new IdentityHashMap<>();
    private static volatile MinecraftServer server;
    private static Path output;
    private static ServerPlayer current, peer;
    private static UUID account;
    private static int logins, ticks, episode, finalLogouts;
    private static boolean aCue, bCue, armed, finishing, boundaryArmed;
    private static boolean restartSecond, restartStopRequested, restartReadDone;
    private static ServerSlot.InstanceState restartSecondInstance;
    private static volatile boolean terminal, authFault;
    private static Run run;
    private enum Stage { STARTUP, AUTH_LOGIN, SERVER_POST, ACCEPTANCE, PRESPAWN_CLOSE, OPEN_CLOSE, CLAIMED_CLOSE, ORIGINAL_HURT, PHYSICAL_READBACK }
    private static Stage stage = Stage.STARTUP;
    private P11L1ServerHarness() {}

    public static boolean enabled() {
        return java.util.List.of("l1-pre-spawn", "l1-open", "l1-claimed", "l1-impact-close-custody", "l1-two-work-reload", "l1-two-work-stop",
                        "l1-revision", "l1-p8-send-fault", "l1-ack-fault", "l1-work-death", "l1-work-dimension", "l1-work-config",
                        "l1-partial-reward-function", "l1-work-multi-uuid-qctx", "l1-stats-write-fault-memory",
                        "l1-work-deadline", "l1-spawn-callback-remove", "l1-logout-cleanup-fault", "l1-tracking-retirement", "l1-work-capacity",
                        "l1-natural-unload", "l1-online-peer", "l1-work-context-refusal", "l1-restart-write", "l1-restart-read")
                .contains(System.getProperty("gramarye.p11.online.case", ""));
    }

    public static synchronized void authenticated(MinecraftServer exact, Connection connection, UUID id) {
        if (!enabled() || terminal) { return; }
        if (server != exact || connection == null || id == null || AUTH.size() >= 4 || AUTH.containsKey(connection)) {
            authFault = true; return;
        }
        AUTH.put(connection, id);
        P11L1ContextRefusalProbe.authenticated(exact, connection, id);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void started(ServerStartedEvent event) {
        if (!enabled()) { return; }
        try {
            server = event.getServer();
            require(server.isDedicatedServer() && server.usesAuthentication() && !server.isSingleplayer(), "SERVER_TOPOLOGY");
            output = P11C4aEvidence.reserve("server");
            if (P11L1RestartProbe.readSelected()) {
                P11C4aEvidence.write(output, "l1-restart-before-login.json", P11L1RestartProbe.prepareRead(server, output));
            }
            P11C4aEvidence.write(output, "ready.json", Map.of(
                    "status", "ONLINE_DEDICATED_READY_NO_AUTH_CLAIM", "case", selected(),
                    "runId", P11C4aEvidence.property("runId"), "productionJarSha256", P11OnlineInputs.verifyFrozenJar(),
                    "onlineMode", true, "integrated", false, "expectedPlayers", 2,
                    "configurationSha256", P11C4aLoadedConfiguration.hash()));
        } catch (Exception | LinkageError failure) { fail(code(failure), failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || terminal || !(event.getEntity() instanceof ServerPlayer player) || player.getServer() != server) { return; }
        try {
            stage = Stage.AUTH_LOGIN;
            var connection = player.connection.getConnection();
            if (lifecycle() && boundaryArmed && selected().equals("l1-work-config") && account.equals(player.getUUID())) {
                require(server.isSameThread() && player != current && current.isRemoved()
                        && connection == current.connection.getConnection() && connection.isConnected(), "LIFECYCLE_CONFIG_SAME_CONNECTION_SUCCESSOR");
                P11C4aEvidence.write(output, "a-config-return.json", Map.of("status", "ORIGINAL_CONFIG_LOGIN_NOT_NEW_AUTHENTICATION",
                        "samePhysicalConnection", true, "sameAuthenticatedIdentity", true)); return;
            }
            UUID authenticated;
            synchronized (P11L1ServerHarness.class) { authenticated = AUTH.remove(connection); }
            require(server.isSameThread() && !player.isFakePlayer() && connection.isEncrypted()
                    && !connection.isMemoryConnection() && player.getUUID().equals(authenticated), "EXACT_AUTH_TO_PLAY");
            if (account == null) { account = player.getUUID(); current = player; logins = 1; }
            else if (account.equals(player.getUUID())) {
                require(run != null && run.logoutComplete && current == run.actor && player != current
                        && connection != run.connection && logins == episode, "SAME_ACCOUNT_RECONNECT_ORDER");
                current = player; logins++;
                run.reconnected = true;
                run.reconnectTick = ticks;
                if (run.projectile != null && !run.projectile.isRemoved()) {
                    require(run.projectile.getOwner() == run.actor, "RECONNECT_RETARGETED_OLD_OWNER");
                    run.oldOwnerAfterReconnect = true;
                }
                if (episode == 3 && run.projectile != null && !run.projectile.isRemoved()) {
                    // Real same-UUID B is placed ahead of the old projectile by ordinary teleport.
                    // No hit callback/state is fabricated: original collision must terminate
                    // without a child or damage, not acquire B as a replacement actor.
                    var ahead = run.projectile.position().add(run.projectile.getDeltaMovement().scale(3));
                    player.teleportTo(player.serverLevel(), ahead.x, ahead.y, ahead.z, java.util.Set.of(), 0, 0);
                    run.selfBefore = player.getHealth();
                    run.selfPlaced = true;
                }
            } else {
                require(peer == null && logins == 1, "EXTRA_OR_EARLY_PEER"); peer = player;
            }
            String role = account.equals(player.getUUID()) ? "a" : "b";
            P11C4aEvidence.write(output, role + "-auth-" + (role.equals("a") ? logins : 1) + ".json", Map.of(
                    "status", "ORIGINAL_HAS_JOINED_TO_EXACT_PLAY", "identityPseudonym", P11C4aEvidence.pseudonym(player.getUUID()),
                    "encrypted", true, "memoryConnection", false, "fakePlayer", false, "sameAccountReplacement", role.equals("a") && logins > 1));
        } catch (Exception | LinkageError failure) { fail(code(failure), failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!enabled() || terminal || !(event.getEntity() instanceof ServerPlayer player) || player.getServer() != server) { return; }
        if (run != null && run.closing && player == run.actor) { run.logoutEvents++; return; }
        if (naturalUnload() && boundaryArmed && player == current && P11L1NaturalUnloadProbe.closeRequested()) { finalLogouts++; return; }
        if (terminalBoundary() && boundaryArmed && (player == current || player == peer)
                && (P11L1TerminalBoundaryProbe.nativeErrorStop()
                        || player == current && P11L1TerminalBoundaryProbe.closeRequested())) { finalLogouts++; return; }
        if (lifecycle() && boundaryArmed && !finishing && selected().equals("l1-work-config") && player == current) { return; }
        if (twoWorkStop() && P11L1WorkBoundaryProbe.stopRequested() && (player == peer || player == current)) { finalLogouts++; return; }
        if (P11L1RestartProbe.writeSelected() && restartStopRequested && (player == peer || player == current)) { finalLogouts++; return; }
        if (P11L1SupplementalHarness.observeAckLogout(player)) { finalLogouts++; return; }
        if (finishing && (player == peer || player == current)) { finalLogouts++; return; }
        fail("UNPLANNED_LOGOUT_EVENT_NOT_COMPLETION");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void tick(ServerTickEvent.Post event) {
        if (!enabled() || server != event.getServer() || terminal) { return; }
        try {
            stage = Stage.SERVER_POST;
            require(server.isSameThread() && !authFault && ++ticks <= 30_000, "SERVER_DEADLINE_OR_AUTH_OBSERVER");
            P11L1ContextRefusalProbe.checkTaskHold();
            if (Boolean.parseBoolean(System.getProperty("gramarye.p11.online.readyOnly", "false"))) {
                if (ticks >= 20) { terminal = true; server.halt(false); } return;
            }
            if (!aCue && P11C4aEvidence.receiptPresent(P11C4aEvidence.root().resolve("client-a"), "inputs.json")) {
                aCue = true; P11C4aEvidence.cue(output, "a-connect.ready");
            }
            if (current != null && !bCue && P11C4aEvidence.receiptPresent(P11C4aEvidence.root().resolve("client-b"), "inputs.json")) {
                bCue = true; P11C4aEvidence.cue(output, "b-connect.ready");
            }
            if (current == null || peer == null) { return; }
            if (finishing) {
                if (finalLogouts == 2 && server.getPlayerList().getPlayers().isEmpty()) {
                    terminal = true; server.halt(false);
                }
                return;
            }
            require(peer.connection.getConnection().isConnected() && server.getPlayerList().getPlayer(peer.getUUID()) == peer,
                    "INDEPENDENT_PEER_CHANGED");
            if (P11L1ImpactCustodyProbe.takeReconnectCue()) { P11C4aEvidence.cue(output, "a-reconnect-1.ready"); }
            if (P11L1RestartProbe.readSelected()) { progressRestartRead(); return; }
            if (restartSecond) {
                if (!restartStopRequested && P11L1RestartProbe.beforeOriginalStop(current)) {
                    restartStopRequested = true; server.halt(false);
                }
                return;
            }
            if (!armed) {
                prepareArena(); armed = true;
                if (capacityWork()) {
                    boundaryArmed = true; P11L1CapacityWorkProbe.arm(server, current, peer, output); return;
                }
                P11C4aEvidence.cue(output, "a-starter.ready"); return;
            }
            if (twoWork()) { progressTwoWork(); return; }
            if (naturalUnload()) { progressNaturalUnload(); return; }
            if (onlinePeer()) { progressOnlinePeer(); return; }
            if (P11L1SupplementalHarness.selected()) { progressSupplemental(); return; }
            if (lifecycle()) { progressLifecycle(); return; }
            if (terminalBoundary()) { progressTerminal(); return; }
            if (tracking()) { progressTracking(); return; }
            if (capacityWork()) { progressCapacity(); return; }
            if (run == null) {
                var facts = P11NativeStorageBoundary.diagnostics(server, account);
                if (facts.equippedSlot0().equals("ABSENT") || facts.equippedSlot0().equals("UNAVAILABLE")) { return; }
                episode = 1; run = new Run(current, ticks);
                if (workReward()) {
                    if (multiWorkReward()) {
                        require(!peer.getTags().contains("p11_l1_qctx_peer") && peer.addTag("p11_l1_qctx_peer"), "FRESH_QCTX_PEER_TAG");
                    }
                    P11L1WorkRewardProbe.arm(server, current, peer, selected().equals("l1-partial-reward-function")
                            ? P11L1WorkRewardProbe.Mode.PARTIAL_FUNCTION : P11L1WorkRewardProbe.Mode.MULTI_UUID_QCTX);
                    if (P11L1ContextRefusalProbe.selected()) { P11L1ContextRefusalProbe.arm(server, current, peer, output); }
                }
                if (statsMemory()) { P11L1StatsMemoryProbe.arm(server, current, output); }
                P11C4aEvidence.cue(output, "a-cast-1.ready"); return;
            }
            require(ticks - run.started <= 2400, "EPISODE_DEADLINE");
            if (workReward() && !run.localRewardSealed && P11L1WorkRewardProbe.readyToFinish()) {
                P11C4aEvidence.write(output, "work-reward-local.json", P11L1WorkRewardProbe.finish());
                run.localRewardSealed = true;
                if (P11L1ContextRefusalProbe.selected()) { P11L1ContextRefusalProbe.localSealed(); }
            }
            if (statsMemory() && run.damageReturns == 1 && run.instanceState.work == null && !run.statsWorkSealed) {
                P11C4aEvidence.write(output, "stats-memory-work.json", P11L1StatsMemoryProbe.workTerminal());
                run.statsWorkSealed = true; P11C4aEvidence.cue(output, "a-reconnect-1.ready");
            }
            if (episode == 3 && run.selfCollision && run.reconnected && workRoots() == 0) {
                require(run.logoutComplete && run.claims == 0 && run.damageReturns == 0 && run.nativeDamageReturns == 0
                        && run.transfers == 1 && run.oldOwnerAfterReconnect && run.selfPlaced && run.instanceState.work == null
                        && run.instanceState.lease.pin.isClosed(), "SAME_UUID_SELF_COLLISION_MUST_TERMINATE_WITHOUT_CHILD");
                writeEpisode(); finishing = true;
                P11C4aEvidence.cue(output, "a-finish.ready"); P11C4aEvidence.cue(output, "b-finish.ready"); return;
            }
            if (run.accepted && run.damageReturns == 0 && run.projectile != null && run.projectile.isRemoved() && !run.selfCollision) {
                require(false, "NATURAL_PROJECTILE_ENDED_WITHOUT_REQUIRED_HIT");
            }
            if (episode == 2 && run.damageReturns == 1 && run.reconnected && !run.hazard && workRoots() == 0) {
                require(run.victim.isAlive() && run.victim.getKillCredit() == run.actor && nativeRoots() > 0,
                        "LATE_NATIVE_CREDIT_AFTER_WORK_TERMINAL");
                run.hazard = true;
                // Ordinary world hazard: native LivingEntity ticks consume existing kill credit.
                run.actor.serverLevel().setBlock(run.victim.blockPosition(), Blocks.LAVA.defaultBlockState(), 3);
            }
            if (episode == 3 || run.damageReturns != 1 || !run.reconnected || run.victim.isAlive() || workRoots() != 0 || nativeRoots() != 0) { return; }
            require(run.logoutComplete && run.claims == 1 && run.transfers == 1 && run.p5DamageApplied
                    && run.nativeDamageReturns == 1 && run.nativeOriginA && run.nativeProjectileExact,
                    "ORIGINAL_P5_NATIVE_PATH_INCOMPLETE");
            if (singleNatural()) { finishSingleNatural(); return; }
            require(current.getAdvancements().getOrStartProgress(server.getAdvancements().get(rewardId())).isDone(), "NATIVE_KILL_REWARD_MISSING");
            require(run.rewardReturns == 1 && run.functionReturns == 1 && run.killScoreReturns == 1 && current.totalExperience >= episode * 10
                    && current.getRecipeBook().contains(ResourceLocation.withDefaultNamespace("bread")),
                    "WHOLE_REWARD_XP_RECIPE_MISSING");
            require(run.instanceState.work == null && run.instanceState.lease.pin.isClosed()
                    && run.instanceState.activeProjectileContinuation == null, "WORK_PIN_PERMIT_TERMINAL");
            require(current.getInventory().countItem(net.minecraft.world.item.Items.BREAD) == episode,
                    "WHOLE_REWARD_ORIGINAL_LOOT_INVENTORY");
            var objective = server.getScoreboard().getObjective("p11_l1");
            var score = objective == null ? null : server.getScoreboard().getPlayerScoreInfo(current, objective);
            require(score != null && score.value() == episode, "WHOLE_REWARD_ORIGINAL_FUNCTION_SCORE");
            server.getPlayerList().saveAll();
            readback();
            writeEpisode();
            if (P11L1RestartProbe.writeSelected()) {
                require(episode == 1 && logins == 2, "RESTART_FIRST_REAL_EPISODE");
                P11L1RestartProbe.captureCompleted(server, current, run.instanceState, output);
                restartSecond = true; prepareArena();
                P11C4aEvidence.cue(output, "a-cast-2.ready"); return;
            }
            episode++; prepareArena(); run = new Run(current, ticks);
            P11C4aEvidence.cue(output, "a-cast-" + episode + ".ready");
        } catch (Exception | LinkageError failure) { fail(code(failure), failure); }
    }

    private static void progressRestartRead() throws IOException {
        require(!armed && run == null && !restartSecond && logins == 1, "RESTART_READ_NO_STARTER_OR_CAST_DRIVER");
        if (!restartReadDone) {
            var result = P11L1RestartProbe.verifyRead(current);
            if (result.isEmpty()) { return; }
            server.getPlayerList().saveAll();
            P11C4aEvidence.write(output, "l1-restart-result.json", result.orElseThrow());
            restartReadDone = true;
        }
        finishing = true;
        P11C4aEvidence.cue(output, "a-finish.ready"); P11C4aEvidence.cue(output, "b-finish.ready");
    }

    private static boolean twoWork() { return selected().equals("l1-two-work-reload") || twoWorkStop(); }
    private static boolean twoWorkStop() { return selected().equals("l1-two-work-stop"); }
    static boolean workReward() { return java.util.List.of("l1-partial-reward-function", "l1-work-multi-uuid-qctx", "l1-work-context-refusal").contains(selected()); }
    static boolean multiWorkReward() { return selected().equals("l1-work-multi-uuid-qctx") || P11L1ContextRefusalProbe.selected(); }
    static boolean statsMemory() { return selected().equals("l1-stats-write-fault-memory"); }
    static boolean singleNatural() { return workReward() || statsMemory() || P11L1ImpactCustodyProbe.selected(); }
    static boolean tracking() { return selected().equals("l1-tracking-retirement"); }
    static boolean naturalUnload() { return selected().equals("l1-natural-unload"); }
    static boolean onlinePeer() { return selected().equals("l1-online-peer"); }
    private static void progressNaturalUnload() throws IOException {
        if (!boundaryArmed) {
            var facts = P11NativeStorageBoundary.diagnostics(server, account);
            if (facts.equippedSlot0().equals("ABSENT") || facts.equippedSlot0().equals("UNAVAILABLE")) { return; }
            boundaryArmed = true; P11L1NaturalUnloadProbe.arm(server, current, peer, output);
        }
        P11L1NaturalUnloadProbe.tick();
        require(P11L1NaturalUnloadProbe.failureCode().equals("NONE"), "NATURAL_UNLOAD_OBSERVER_FAILURE");
        if (P11L1NaturalUnloadProbe.complete()) {
            require(finalLogouts == 1 && !current.connection.getConnection().isConnected(), "NATURAL_UNLOAD_ORIGINAL_A_CLOSE_REQUIRED");
            finishing = true; P11C4aEvidence.cue(output, "b-finish.ready");
        }
    }
    private static void progressOnlinePeer() throws IOException {
        if (!boundaryArmed) {
            var facts = P11NativeStorageBoundary.diagnostics(server, account);
            if (facts.equippedSlot0().equals("ABSENT") || facts.equippedSlot0().equals("UNAVAILABLE")) { return; }
            boundaryArmed = true; P11L1OnlinePeerProbe.arm(server, current, peer, output);
        }
        P11L1OnlinePeerProbe.tick();
        require(P11L1OnlinePeerProbe.failureCode().equals("NONE"), "ONLINE_PEER_OBSERVER_FAILURE");
        if (P11L1OnlinePeerProbe.complete()) {
            finishing = true;
            P11C4aEvidence.cue(output, "a-finish.ready"); P11C4aEvidence.cue(output, "b-finish.ready");
        }
    }
    static boolean capacityWork() { return selected().equals("l1-work-capacity"); }
    private static void progressCapacity() throws IOException {
        require(boundaryArmed, "CAPACITY_ARM_BEFORE_STARTER"); P11L1CapacityWorkProbe.tick();
        if (P11L1CapacityWorkProbe.complete()) {
            finishing = true;
            P11C4aEvidence.cue(output, "a-finish.ready"); P11C4aEvidence.cue(output, "b-finish.ready");
        }
    }
    private static void progressTracking() throws IOException {
        if (!boundaryArmed) {
            var facts = P11NativeStorageBoundary.diagnostics(server, account);
            if (facts.equippedSlot0().equals("ABSENT") || facts.equippedSlot0().equals("UNAVAILABLE")) { return; }
            boundaryArmed = true; P11L1TrackingBoundaryProbe.arm(server, current, peer, output);
        }
        P11L1TrackingBoundaryProbe.tick();
        require(P11L1TrackingBoundaryProbe.failureCode().equals("NONE"), "TRACKING_OBSERVER_FAILURE");
        if (P11L1TrackingBoundaryProbe.complete()) {
            finishing = true;
            P11C4aEvidence.cue(output, "a-finish.ready"); P11C4aEvidence.cue(output, "b-finish.ready");
        }
    }
    static boolean terminalBoundary() {
        return java.util.List.of("l1-work-deadline", "l1-spawn-callback-remove", "l1-logout-cleanup-fault").contains(selected());
    }
    private static void progressTerminal() throws IOException {
        if (!boundaryArmed) {
            var facts = P11NativeStorageBoundary.diagnostics(server, account);
            if (facts.equippedSlot0().equals("ABSENT") || facts.equippedSlot0().equals("UNAVAILABLE")) { return; }
            var mode = switch (selected()) {
                case "l1-work-deadline" -> P11L1TerminalBoundaryProbe.Mode.DEADLINE;
                case "l1-spawn-callback-remove" -> P11L1TerminalBoundaryProbe.Mode.SPAWN_CALLBACK_REMOVE;
                case "l1-logout-cleanup-fault" -> P11L1TerminalBoundaryProbe.Mode.LOGOUT_CLEANUP_FAULT;
                default -> throw new IllegalStateException("L1_TERMINAL_CASE");
            };
            boundaryArmed = true; P11L1TerminalBoundaryProbe.arm(server, current, peer, output, mode);
        }
        P11L1TerminalBoundaryProbe.tick();
        require(P11L1TerminalBoundaryProbe.failureCode().equals("NONE"), "TERMINAL_OBSERVER_FAILURE");
        if (P11L1TerminalBoundaryProbe.complete() && !P11L1TerminalBoundaryProbe.nativeErrorStop()) {
            require(!current.connection.getConnection().isConnected(), "TERMINAL_ORIGINAL_A_CLOSE_REQUIRED");
            finishing = true; P11C4aEvidence.cue(output, "b-finish.ready");
        }
    }

    private static void finishSingleNatural() throws IOException {
        if (P11L1ContextRefusalProbe.selected() && !P11L1ContextRefusalProbe.finish(current)) { return; }
        require(run.killScoreReturns == 1 && run.fatalSourceObservations == 1 && run.instanceState.work == null
                && run.instanceState.lease.pin.isClosed() && run.instanceState.activeProjectileContinuation == null
                && current != run.actor && current.totalExperience == 10
                && current.getInventory().countItem(net.minecraft.world.item.Items.BREAD) == 1
                && current.getRecipeBook().contains(ResourceLocation.withDefaultNamespace("bread"))
                && current.getAdvancements().getOrStartProgress(server.getAdvancements().get(rewardId())).isDone(),
                "SINGLE_NATURAL_REWARD_AND_RECONNECT");
        var owner = P11NativeStorageBoundary.nativeSourceOwner(current); var body = owner.nativeRecipient(current);
        require(body != null && owner.canCopy(body) && body.source.epoch() > run.initialEpoch
                && body.stats == run.initialBody.stats && body.advancements == run.initialBody.advancements
                && body.account == run.initialBody.account, "SINGLE_CANONICAL_NEW_EPOCH_NO_REPLAY");
        if (statsMemory()) {
            require(run.statsWorkSealed, "STATS_WORK_BEFORE_RECONNECT");
            P11C4aEvidence.write(output, "stats-memory-reconnected.json", P11L1StatsMemoryProbe.reconnected(current));
            P11L1StatsMemoryProbe.restoreBeforeOriginalSave();
        } else if (P11L1ImpactCustodyProbe.selected()) {
            var objective = server.getScoreboard().getObjective("p11_l1");
            var score = objective == null ? null : server.getScoreboard().getPlayerScoreInfo(current, objective);
            require(run.rewardReturns == 1 && run.functionReturns == 1 && score != null && score.value() == 1,
                    "IMPACT_ORIGINAL_WHOLE_REWARD_AND_FUNCTION_SCORE");
        } else { require(run.localRewardSealed, "ACTUAL_LOCAL_FUNCTION_PROOF_REQUIRED"); }
        server.getPlayerList().saveAll();
        if (statsMemory()) {
            P11C4aEvidence.write(output, "stats-memory-save.json", P11L1StatsMemoryProbe.originalSaveReturned(current));
        }
        readback();
        if (workReward()) { readbackWorkReward(); }
        if (P11L1ImpactCustodyProbe.selected()) {
            P11C4aEvidence.write(output, "impact-custody-result.json", P11L1ImpactCustodyProbe.finish(current));
        }
        writeEpisode(); finishing = true;
        P11C4aEvidence.cue(output, "a-finish.ready"); P11C4aEvidence.cue(output, "b-finish.ready");
    }

    private static void readbackWorkReward() throws IOException {
        boolean multi = multiWorkReward();
        var saved = net.minecraft.nbt.NbtIo.readCompressed(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                .resolve(account + ".dat"), net.minecraft.nbt.NbtAccounter.create(32L * 1024 * 1024));
        var tags = saved.getList("Tags", 8).stream().map(net.minecraft.nbt.Tag::getAsString).collect(java.util.stream.Collectors.toSet());
        require(!tags.contains("p11_l1_unreachable_tail") && !tags.contains("p11_l1_qctx_peer_done")
                && (!multi || tags.contains("p11_l1_qctx_outer") && tags.contains("p11_l1_qctx_outer_done")),
                "ORIGINAL_ALPHA_FUNCTION_TAGS_READBACK");
        var peerSaved = net.minecraft.nbt.NbtIo.readCompressed(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                .resolve(peer.getUUID() + ".dat"), net.minecraft.nbt.NbtAccounter.create(32L * 1024 * 1024));
        int expectedPeerXp = run.peerXpBefore + (multi ? 18 : 0);
        var peerTags = peerSaved.getList("Tags", 8).stream().map(net.minecraft.nbt.Tag::getAsString).collect(java.util.stream.Collectors.toSet());
        require(peer.totalExperience == expectedPeerXp && peerSaved.getInt("XpTotal") == expectedPeerXp
                && !peerTags.contains("p11_l1_qctx_outer_done") && peerTags.contains("p11_l1_qctx_peer_done") == multi,
                "ORIGINAL_PEER_XP_FUNCTION_TAG_READBACK");
        var peerPa = readJson(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("advancements").resolve(peer.getUUID() + ".json"));
        String peerReward = "gramarye_p11_engineering:l1_qctx_peer";
        boolean peerDone = peerPa.has(peerReward) && peerPa.getAsJsonObject(peerReward).get("done").getAsBoolean();
        require(peerDone == multi, "ORIGINAL_PEER_ADVANCEMENT_READBACK");
        var peerStats = readJson(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("stats").resolve(peer.getUUID() + ".json")).getAsJsonObject("stats");
        var peerKilled = peerStats.getAsJsonObject("minecraft:killed"); var peerCustom = peerStats.getAsJsonObject("minecraft:custom");
        int peerChicken = peerKilled != null && peerKilled.has("minecraft:chicken") ? peerKilled.get("minecraft:chicken").getAsInt() : 0;
        int peerMobKills = peerCustom != null && peerCustom.has("minecraft:mob_kills") ? peerCustom.get("minecraft:mob_kills").getAsInt() : 0;
        require(peerChicken == run.peerChickenBefore && peerMobKills == run.peerMobKillsBefore,
                "ORIGINAL_PEER_KILL_STATISTICS_UNCHANGED");
        for (String kind : java.util.List.of("PLAYER_DATA", "STATISTICS", "ADVANCEMENTS")) {
            var writer = P11NativeStorageBoundary.diagnostics(server, peer.getUUID()).writers().stream()
                    .filter(value -> value.kind().equals(kind)).findFirst().orElseThrow();
            require(!writer.dirty() && writer.terminal().equals("COMPLETED") && writer.encode().equals("SUCCEEDED")
                    && writer.write().equals("SUCCEEDED") && writer.close().equals("SUCCEEDED")
                    && (!kind.equals("PLAYER_DATA") || writer.replace().equals("SUCCEEDED")), "ORIGINAL_PEER_WRITER_COMPLETED");
        }
        P11C4aEvidence.write(output, "work-reward-physical.json", Map.of("status", "ORIGINAL_RECONNECT_AND_PHYSICAL_WRITERS_OBSERVED",
                "selectedReward", rewardId().toString(), "alphaXp", current.totalExperience, "peerXp", expectedPeerXp,
                "alphaFunctionTagsReadback", true, "peerFunctionTagsReadback", true, "peerAdvancementDone", peerDone,
                "alphaNewEpoch", true, "canonicalStatisticsAndAdvancementsPreserved", true,
                "peerKillStatisticsUnchanged", true));
    }
    private static void progressTwoWork() throws IOException {
        if (!boundaryArmed) {
            var facts = P11NativeStorageBoundary.diagnostics(server, account);
            if (facts.equippedSlot0().equals("ABSENT") || facts.equippedSlot0().equals("UNAVAILABLE")) { return; }
            boundaryArmed = true;
            P11L1WorkBoundaryProbe.arm(server, current, peer, output, twoWorkStop()
                    ? P11L1WorkBoundaryProbe.Mode.TWO_WORK_STOP : P11L1WorkBoundaryProbe.Mode.TWO_WORK_RELOAD);
        }
        P11L1WorkBoundaryProbe.tick();
        require(P11L1WorkBoundaryProbe.failureCode().equals("NONE"), "TWO_WORK_OBSERVER_FAILURE");
        if (!twoWorkStop() && P11L1WorkBoundaryProbe.complete()) {
            finishing = true;
            P11C4aEvidence.cue(output, "a-finish.ready"); P11C4aEvidence.cue(output, "b-finish.ready");
        }
    }

    private static void progressSupplemental() throws IOException {
        if (!boundaryArmed) {
            var facts = P11NativeStorageBoundary.diagnostics(server, account);
            if (facts.equippedSlot0().equals("ABSENT") || facts.equippedSlot0().equals("UNAVAILABLE")) { return; }
            boundaryArmed = true; P11L1SupplementalHarness.arm(server, current, peer, output);
        }
        P11L1SupplementalHarness.tick();
        if (P11L1SupplementalHarness.complete()) {
            finishing = true;
            if (!P11L1SupplementalHarness.ack()) { P11C4aEvidence.cue(output, "a-finish.ready"); }
            P11C4aEvidence.cue(output, "b-finish.ready");
        }
    }

    static boolean lifecycle() {
        return java.util.List.of("l1-work-death", "l1-work-dimension", "l1-work-config")
                .contains(System.getProperty("gramarye.p11.online.case", ""));
    }
    private static void progressLifecycle() throws IOException {
        if (!boundaryArmed) {
            var facts = P11NativeStorageBoundary.diagnostics(server, account);
            if (facts.equippedSlot0().equals("ABSENT") || facts.equippedSlot0().equals("UNAVAILABLE")) { return; }
            var mode = switch (selected()) {
                case "l1-work-death" -> P11L1LifecycleBoundaryProbe.Mode.ONE_WORK_DEATH;
                case "l1-work-dimension" -> P11L1LifecycleBoundaryProbe.Mode.ONE_WORK_DIMENSION;
                case "l1-work-config" -> P11L1LifecycleBoundaryProbe.Mode.ONE_WORK_CONFIG;
                default -> throw new IllegalStateException("L1_LIFECYCLE_CASE");
            };
            boundaryArmed = true; P11L1LifecycleBoundaryProbe.arm(server, current, peer, output, mode);
        }
        P11L1LifecycleBoundaryProbe.tick();
        require(P11L1LifecycleBoundaryProbe.failureCode().equals("NONE"), "LIFECYCLE_OBSERVER_FAILURE");
        if (P11L1LifecycleBoundaryProbe.complete()) {
            current = server.getPlayerList().getPlayer(account);
            require(current != null && current.connection.getConnection().isConnected(), "LIFECYCLE_FINAL_CURRENT_ACTOR");
            finishing = true;
            P11C4aEvidence.cue(output, "a-finish.ready"); P11C4aEvidence.cue(output, "b-finish.ready");
        }
    }

    private static void prepareArena() {
        var level = current.serverLevel();
        // Loaded by the two actual players; no forced chunks or movement/lifetime changes.
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            level.setBlock(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState(), 3);
        }
        current.teleportTo(level, 0.5, 100, 0.5, java.util.Set.of(), 0, 0);
        peer.teleportTo(level, 2.5, 100, 0.5, java.util.Set.of(), 90, 0);
        if (server.getScoreboard().getObjective("p11_l1") == null) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "scoreboard objectives add p11_l1 dummy");
        }
    }

    public static void accepted(Object runtime, MinecraftServer exact, ServerPlayer actor, Object geometry, Object result) {
        if (enabled() && restartSecond && exact == server && actor == current) {
            try { P11L1RestartProbe.accepted(actor, restartSecondInstance, result); }
            catch (RuntimeException | LinkageError failure) { fail(code(failure), failure); }
            return;
        }
        P11L1NaturalUnloadProbe.accepted(exact, actor, result);
        P11L1OnlinePeerProbe.accepted(exact, actor, geometry, result);
        P11L1WorkBoundaryProbe.accepted(exact, actor, result);
        P11L1SupplementalHarness.accepted(exact, actor, geometry, result);
        P11L1LifecycleBoundaryProbe.accepted(exact, actor, result);
        P11L1TerminalBoundaryProbe.accepted(exact, actor, result);
        P11L1TrackingBoundaryProbe.accepted(exact, actor, result);
        P11L1CapacityWorkProbe.admitted(runtime, exact, actor, result);
        if (!observes(actor) || !(result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly admitted)) { return; }
        try {
            stage = Stage.ACCEPTANCE;
            require(!run.accepted && exact == server && geometry instanceof CastGeometryExecutionDataV0, "ACCEPTANCE_IDENTITY");
            run.accepted = true; run.instance = admitted.eventToken().skillInstanceId().value();
            require(run.instanceState != null && run.instanceState.id.value().equals(run.instance)
                    && run.instanceState.work != null && !run.instanceState.lease.pin.isClosed(), "ACTUAL_INSTANCE_WORK_AND_PIN");
            if (workReward()) { P11L1WorkRewardProbe.accepted(actor, run.instanceState, result); }
            if (statsMemory()) { P11L1StatsMemoryProbe.accepted(actor, run.instanceState, result); }
            run.acceptedAt = ticks; run.initialNative = nativeRoots();
            require(run.initialNative == 0 && workRoots() > 0, "FIRST_OFFLINE_CAUSE_MUST_BEGIN_WITH_WORK_ONLY");
            if (episode == 3) { return; }
            var sample = (CastGeometryExecutionDataV0) geometry;
            Vec3 position = new Vec3(sample.originX(), sample.originY(), sample.originZ());
            Vec3 motion = new Vec3(sample.directionXQ15(), sample.directionYQ15(), sample.directionZQ15()).normalize().scale(1.5);
            for (int i = 0; i < 35; i++) { position = position.add(motion); motion = motion.scale(0.99).add(0, -0.03, 0); }
            var floor = BlockPos.containing(position.x, position.y - 1, position.z);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                require(actor.serverLevel().isLoaded(floor.offset(x, 0, z)), "NATURAL_TARGET_CHUNK_NOT_LOADED");
                actor.serverLevel().setBlock(floor.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
            }
            var victim = episode == 1 ? EntityType.CHICKEN.create(actor.serverLevel()) : EntityType.COW.create(actor.serverLevel());
            require(victim != null, "TARGET_NATIVE_CONSTRUCTOR");
            victim.setNoAi(true); victim.setPersistenceRequired(); victim.addTag(workReward() ? P11L1WorkRewardProbe.targetTag() : "p11_l1_target");
            victim.moveTo(position.x, floor.getY() + 1, position.z, 0, 0);
            require(actor.serverLevel().addFreshEntity(victim), "TARGET_NATIVE_ADD");
            run.victim = victim;
            if (workReward()) { P11L1WorkRewardProbe.target(victim); }
            if (P11L1ImpactCustodyProbe.selected()) {
                P11L1ImpactCustodyProbe.arm(runtime, server, actor, peer, run.instanceState, victim);
            }
        } catch (Exception | LinkageError failure) { fail(code(failure), failure); }
    }

    public static void instanceCreated(ServerPlayer actor, Object instance) {
        if (enabled() && restartSecond && actor == current) {
            require(restartSecondInstance == null && instance instanceof ServerSlot.InstanceState,
                    "RESTART_SECOND_ORIGINAL_CONSTRUCTOR_ONCE");
            restartSecondInstance = (ServerSlot.InstanceState) instance; return;
        }
        P11L1NaturalUnloadProbe.instanceCreated(actor, instance);
        P11L1OnlinePeerProbe.instanceCreated(actor, instance);
        P11L1WorkBoundaryProbe.instanceCreated(actor, instance);
        P11L1SupplementalHarness.instanceCreated(actor, instance);
        P11L1LifecycleBoundaryProbe.instanceCreated(actor, instance);
        P11L1TerminalBoundaryProbe.instanceCreated(actor, instance);
        P11L1TrackingBoundaryProbe.instanceCreated(actor, instance);
        P11L1CapacityWorkProbe.instanceCreated(actor, instance);
        if (observes(actor)) {
            require(run.instanceState == null && instance instanceof ServerSlot.InstanceState, "ORIGINAL_INSTANCE_CONSTRUCTOR_ONCE");
            run.instanceState = (ServerSlot.InstanceState) instance;
        }
    }

    public static void damageEntering(LivingEntity target, net.minecraft.world.damagesource.DamageSource source) {
        P11L1ImpactCustodyProbe.hurtEntering(target, source);
        P11L1NaturalUnloadProbe.damageEntering(source);
        P11L1OnlinePeerProbe.damageEntering(target, source);
        P11L1WorkBoundaryProbe.damageEntering(source);
        P11L1LifecycleBoundaryProbe.damageEntering(source);
        P11L1TerminalBoundaryProbe.damageEntering(source);
        P11L1TrackingBoundaryProbe.damageEntering(source);
        P11L1CapacityWorkProbe.damageEntering(source);
        P11L1WorkRewardProbe.damageEntering(target, source);
        if (!enabled() || run == null || target != run.victim || source.getDirectEntity() != run.projectile) { return; }
        stage = Stage.ORIGINAL_HURT;
        require(run.logoutComplete && source.getEntity() == run.actor && workRoots() > 0 && roots("OPERATION") > 0,
                "WORK_TO_ORIGINAL_NATIVE_OPERATION");
        run.firstHitNativeRoots = nativeRoots();
        require(run.firstHitNativeRoots == 0, "FIRST_HIT_DID_NOT_BEGIN_NATIVE_CREDIT_FREE");
        P11L1ContextRefusalProbe.beforeOriginalHurt();
    }

    public static void experience(ServerPlayer recipient, int amount) {
        P11L1WorkBoundaryProbe.experience(recipient, amount);
        P11L1LifecycleBoundaryProbe.experience(recipient, amount);
        if (workReward()) { return; } // Dedicated exact grant/function observers own these two selected cases.
        if (!enabled() || run == null || !run.accepted || recipient.getUUID().equals(account) == false) { return; }
        if (amount == 3) {
            require(recipient == current && roots("COMMAND_CONTEXT") > 0 && roots("OPERATION") > 0, "NATIVE_REWARD_FUNCTION_CONTEXT_AND_RECIPIENT");
            run.functionReturns++;
        }
        if (amount == 7) { require(recipient == current, "NATIVE_REWARD_RECIPIENT"); run.rewardReturns++; }
    }

    public static boolean beginKillScore(ServerPlayer origin, Entity victim, net.minecraft.world.damagesource.DamageSource fatalSource) {
        if (!enabled() || run == null || victim != run.victim) { return false; }
        require(origin == run.actor && !run.scoreActive && run.killScoreReturns == 0, "ORIGINAL_KILL_SCORE_ORIGIN_AND_ONCE");
        require(run.victim.getKillCredit() == run.actor && roots("OPERATION") > 0, "ACTUAL_DEATH_NATIVE_CREDIT_CONSUMER");
        run.fatalSourceHasEntity = fatalSource.getEntity() != null;
        run.fatalSourceOriginalProjectile = fatalSource.getEntity() == run.actor && fatalSource.getDirectEntity() == run.projectile;
        run.fatalSourceNativeHazard = fatalSource.getEntity() == null && fatalSource.getDirectEntity() == null
                && (fatalSource.is(net.minecraft.world.damagesource.DamageTypes.LAVA)
                        || fatalSource.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE)
                        || fatalSource.is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE));
        run.nativeRootsAtDeath = nativeRoots(); run.workRootsAtDeath = workRoots();
        require(episode == 1 ? run.fatalSourceOriginalProjectile
                : episode == 2 && run.hazard && run.fatalSourceNativeHazard && run.nativeRootsAtDeath > 0 && run.workRootsAtDeath == 0,
                "ORIGINAL_FATAL_SOURCE_BRANCH");
        run.fatalSourceObservations++;
        run.scoreActive = true; return true;
    }
    public static boolean scoreEntering(net.minecraft.world.entity.player.Player receiver) {
        if (!enabled() || run == null || !run.scoreActive || run.scoreEntered) { return false; }
        require(receiver == current && roots("OPERATION") > 0, "NATIVE_SCORE_FIXED_RECIPIENT");
        run.scoreEntered = true; return true;
    }
    public static void scoreReturned(net.minecraft.world.entity.player.Player receiver, int amount, int before, boolean selected) {
        if (!selected) { return; }
        require(run != null && run.scoreActive && receiver == current && receiver.getScore() == before + amount,
                "ORIGINAL_NATIVE_SCORE_WRITE_RETURN");
        run.scoreAmount = amount; run.scoreBefore = before; run.scoreAfter = receiver.getScore(); run.killScoreReturns++;
    }
    public static void endKillScore(boolean observed, boolean normal) {
        if (!observed || run == null) { return; }
        run.scoreActive = false;
        if (normal) { require(run.killScoreReturns == 1, "ORIGINAL_KILL_SCORE_RETURN_MISSING"); }
    }

    public static void dispatchReturned() {
        if (enabled() && run != null && run.accepted && !run.logoutComplete && window().equals("l1-pre-spawn")) {
            stage = Stage.PRESPAWN_CLOSE;
            require(run.projectile == null, "PRESPAWN_ALREADY_SPAWNED"); closeNormally();
        }
    }

    public static void transferred(Object projectile, Object disposition) {
        if (enabled() && restartSecond) {
            P11L1RestartProbe.transferred(projectile, disposition); return;
        }
        P11L1NaturalUnloadProbe.transferred(projectile, disposition);
        P11L1OnlinePeerProbe.transferred(projectile, disposition);
        P11L1WorkBoundaryProbe.transferred(projectile, disposition);
        P11L1SupplementalHarness.transferred(projectile, disposition);
        P11L1LifecycleBoundaryProbe.transferred(projectile, disposition);
        P11L1TerminalBoundaryProbe.transferred(projectile, disposition);
        P11L1TrackingBoundaryProbe.transferred(projectile, disposition);
        P11L1CapacityWorkProbe.transferred(disposition);
        if (!enabled() || run == null || !run.accepted || !(projectile instanceof P9StarterProjectile exact)) { return; }
        if (disposition != RuntimePermitTransferDisposition.TRANSFERRED) { return; }
        require(run.projectile == null && exact.getOwner() == run.actor, "SPAWN_OWNER_OR_DUPLICATE");
        run.projectile = exact; run.transfers++;
        if (workReward()) { P11L1WorkRewardProbe.transferred(projectile); }
    }

    public static void projectileTick(Object object) {
        P11L1NaturalUnloadProbe.projectileTick(object);
        P11L1TerminalBoundaryProbe.projectileTick(object);
        if (!enabled() || run == null || object != run.projectile) { return; }
        if (!run.logoutComplete && window().equals("l1-open") && run.claims == 0 && !run.projectile.isRemoved()) {
            require(run.projectile.tickCount > 0 && run.projectile.getDeltaMovement().lengthSqr() > 0, "OPEN_NATURAL_MISS_REQUIRED");
            stage = Stage.OPEN_CLOSE;
            run.openTick = run.projectile.tickCount; closeNormally();
        }
    }

    public static void claimed(Object disposition) {
        P11L1NaturalUnloadProbe.claimed(disposition);
        P11L1OnlinePeerProbe.claimed(disposition);
        P11L1WorkBoundaryProbe.claimed(disposition);
        P11L1LifecycleBoundaryProbe.claimed(disposition);
        P11L1TerminalBoundaryProbe.claimed(disposition);
        P11L1TrackingBoundaryProbe.claimed(disposition);
        P11L1CapacityWorkProbe.claimed(disposition);
        if (enabled() && run != null && disposition == RuntimePermitClaimDisposition.QUEUED) { run.claims++; }
    }

    public static void hitReturned(Object projectile, Entity actualVictim) {
        P11L1OnlinePeerProbe.hitReturned(projectile, actualVictim);
        if (!enabled() || run == null || projectile != run.projectile) { return; }
        if (P11L1ImpactCustodyProbe.selected()) {
            require(episode == 1 && actualVictim == run.victim && run.claims == 0 && run.closing && !run.logoutComplete,
                    "IMPACT_SELECTED_HOLD_NOT_EARLY_CLAIM");
            P11L1ImpactCustodyProbe.hitReturned(projectile, actualVictim); return;
        }
        if (episode == 3) {
            require(actualVictim == current && current != run.actor && current.getUUID().equals(run.actor.getUUID())
                    && run.claims == 0 && current.getHealth() == run.selfBefore, "REAL_SAME_UUID_COLLISION_EXCLUSION");
            run.selfCollision = true; return;
        }
        require(actualVictim == run.victim && run.claims == 1, "NATURAL_COLLISION_TARGET_OR_CLAIM");
        if (window().equals("l1-claimed") && !run.logoutComplete) { stage = Stage.CLAIMED_CLOSE; closeNormally(); }
    }

    public static void nativeDamage(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount, boolean returned) {
        P11L1OnlinePeerProbe.damageReturned(target, source, amount, returned);
        P11L1SupplementalHarness.damageReturned(target, source, amount, returned);
        P11L1WorkRewardProbe.damageReturned(target, source, amount, returned);
        if (!enabled() || run == null || target != run.victim || source.getDirectEntity() != run.projectile) { return; }
        require(run.logoutComplete && source.getEntity() == run.actor && amount == 4 && returned, "OFFLINE_ORIGINAL_DAMAGE_IDENTITY");
        run.nativeOriginA = true; run.nativeProjectileExact = true; run.nativeDamageReturns++;
    }

    public static void damageReturned(Object disposition) {
        P11L1OnlinePeerProbe.commitReturned(disposition);
        if (!enabled() || run == null || !run.accepted) { return; }
        run.damageReturns++;
        run.p5DamageApplied = disposition == com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.CommitDisposition.APPLIED;
    }

    public static void impactCloseStarting(ServerPlayer actor, Object projectile, LivingEntity victim) {
        require(P11L1ImpactCustodyProbe.selected() && server.isSameThread() && run != null
                && actor == run.actor && projectile == run.projectile && victim == run.victim
                && run.accepted && !run.closing && !run.logoutComplete && run.claims == 0, "IMPACT_CLOSE_EXACT_RUN");
        run.closing = true;
    }

    public static void impactLogoutCompleted(ServerPlayer actor) {
        require(P11L1ImpactCustodyProbe.selected() && server.isSameThread() && run != null
                && actor == run.actor && run.closing && !run.logoutComplete && run.logoutEvents == 1
                && !run.connection.isConnected() && actor.isRemoved()
                && server.getPlayerList().getPlayer(account) == null
                && run.instanceState.logoutState == SkillRuntimeService.LogoutState.COMPLETE,
                "IMPACT_WHOLE_ORIGINAL_LOGOUT_RETURN");
        run.logoutComplete = true; run.closing = false; run.logoutAt = ticks;
    }

    private static void closeNormally() {
        try {
            require(server.isSameThread() && run.accepted && !run.closing && !run.logoutComplete
                    && server.getPlayerList().getPlayer(account) == run.actor && workRoots() > 0, "LOGOUT_EXACT_LIVE_ACCEPTED_OWNER");
            run.closing = true;
            run.connection.disconnect(Component.literal("P11 L1 owned normal connection departure"));
            run.connection.handleDisconnection();
            require(!run.connection.isConnected() && run.logoutEvents == 1 && server.getPlayerList().getPlayer(account) == null
                    && run.actor.isRemoved() && run.instanceState.logoutState == SkillRuntimeService.LogoutState.COMPLETE,
                    "ORIGINAL_LOGOUT_DID_NOT_COMPLETE");
            run.logoutComplete = true; run.closing = false; run.logoutAt = ticks;
            if (statsMemory()) {
                P11C4aEvidence.write(output, "stats-memory-logout.json", P11L1StatsMemoryProbe.normalLogoutReturned());
            } else {
                if (P11L1ContextRefusalProbe.selected()) { P11L1ContextRefusalProbe.normalLogoutReturned(); }
                P11C4aEvidence.cue(output, "a-reconnect-" + episode + ".ready");
            }
        } catch (Exception | LinkageError failure) { fail(code(failure), failure); }
    }

    private static boolean observes(ServerPlayer actor) { return enabled() && run != null && !terminal && actor == run.actor && server.isSameThread(); }
    private static String selected() { return P11C4aEvidence.property("case"); }
    private static String window() { return P11L1ImpactCustodyProbe.selected() ? "l1-impact-close-custody"
            : episode == 3 ? "l1-open" : singleNatural() || P11L1RestartProbe.writeSelected() ? "l1-pre-spawn" : selected(); }
    private static long roots(String kind) { return P11NativeStorageBoundary.diagnostics(server, account).nativeResponsibilities().roots()
            .stream().filter(value -> value.kind().equals(kind)).mapToLong(value -> value.count()).sum(); }
    private static long workRoots() { return roots("WORK"); }
    private static long nativeRoots() { return roots("NATIVE_CREDIT"); }
    private static ResourceLocation rewardId() { return ResourceLocation.fromNamespaceAndPath("gramarye_p11_engineering",
            workReward() ? selected().equals("l1-partial-reward-function") ? "l1_partial_kill" : "l1_qctx_kill"
                    : episode == 1 ? "l1_first_kill" : "l1_late_kill"); }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, "L1_" + code); }
    private static String code(Throwable failure) { return P11C4aEvidence.failureCode(failure); }

    private static void writeEpisode() throws IOException {
        var facts = new LinkedHashMap<String, Object>();
        facts.put("status", "NATIVE_L1_NAMED_EPISODE_OBSERVED_NOT_FULL_MATRIX"); facts.put("episode", episode);
        facts.put("logoutWindow", window()); facts.put("actualP7Acceptance", run.accepted); facts.put("instance", run.instance.toString());
        facts.put("acceptedTick", run.acceptedAt); facts.put("logoutTick", run.logoutAt); facts.put("reconnectTick", run.reconnectTick);
        facts.put("normalConnectionHandleReturned", run.logoutComplete); facts.put("logoutEvents", run.logoutEvents);
        facts.put("naturalOpenTick", run.openTick); facts.put("spawnTransfers", run.transfers); facts.put("naturalClaims", run.claims);
        facts.put("originalDamageReturns", run.nativeDamageReturns); facts.put("p5DamageReturns", run.damageReturns);
        facts.put("initialNativeRoots", run.initialNative); facts.put("workRootsAtTerminal", workRoots());
        facts.put("firstHitNativeRoots", run.firstHitNativeRoots); facts.put("rewardExperienceReturns", run.rewardReturns);
        facts.put("realFunctionContextReturns", run.functionReturns);
        facts.put("nativeKillScoreReturns", run.killScoreReturns); facts.put("nativeScoreAmount", run.scoreAmount);
        facts.put("nativeScoreBefore", run.scoreBefore); facts.put("nativeScoreAfter", run.scoreAfter);
        facts.put("fatalSourceObservations", run.fatalSourceObservations); facts.put("fatalSourceHasEntity", run.fatalSourceHasEntity);
        facts.put("fatalSourceOriginalProjectile", run.fatalSourceOriginalProjectile); facts.put("fatalSourceNativeHazard", run.fatalSourceNativeHazard);
        facts.put("nativeRootsAtActualDeath", run.nativeRootsAtDeath); facts.put("workRootsAtActualDeath", run.workRootsAtDeath);
        facts.put("oldOwnerAfterReconnect", run.oldOwnerAfterReconnect); facts.put("sameUuidBPlacedNaturally", run.selfPlaced);
        facts.put("sameUuidCollisionNoChild", run.selfCollision);
        facts.put("lateDeathUsedOrdinaryLava", run.hazard); facts.put("recipientExperience", current.totalExperience);
        facts.put("originalPlayerDataReadback", run.playerDataReadback); facts.put("originalAdvancementsReadback", run.advancementsReadback);
        facts.put("originalStatisticsReadback", run.statisticsReadback);
        facts.put("readbackChickenKills", run.readbackChickenKills); facts.put("readbackCowKills", run.readbackCowKills);
        facts.put("readbackMobKills", run.readbackMobKills);
        facts.put("immutableAcceptedReference", run.instanceState.lease.reference.toString());
        facts.put("source", P11C4aEvidence.sourceObservation(P11NativeStorageBoundary.diagnostics(server, account)));
        P11C4aEvidence.write(output, "episode-" + episode + ".json", facts);
    }

    private static void readback() throws IOException {
        stage = Stage.PHYSICAL_READBACK;
        var diagnostic = P11NativeStorageBoundary.diagnostics(server, account);
        for (String kind : java.util.List.of("PLAYER_DATA", "STATISTICS", "ADVANCEMENTS")) {
            var writer = diagnostic.writers().stream().filter(value -> kind.equals(value.kind())).findFirst().orElseThrow();
            require(!writer.dirty() && writer.terminal().equals("COMPLETED") && writer.encode().equals("SUCCEEDED")
                    && writer.write().equals("SUCCEEDED") && writer.close().equals("SUCCEEDED")
                    && (!kind.equals("PLAYER_DATA") || writer.replace().equals("SUCCEEDED")), "ORIGINAL_WRITER_NOT_COMPLETED");
        }
        var world = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        var saved = net.minecraft.nbt.NbtIo.readCompressed(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                .resolve(account + ".dat"), net.minecraft.nbt.NbtAccounter.create(32L * 1024 * 1024));
        require(saved.getInt("XpTotal") == current.totalExperience
                && saved.getCompound("recipeBook").getList("recipes", 8).stream().anyMatch(value -> value.getAsString().equals("minecraft:bread")),
                "ORIGINAL_PLAYER_DATA_XP_RECIPE_READBACK");
        int bread = 0; var inventory = saved.getList("Inventory", 10);
        for (int index = 0; index < inventory.size(); index++) {
            var stack = net.minecraft.world.item.ItemStack.parseOptional(server.registryAccess(), inventory.getCompound(index));
            if (stack.is(net.minecraft.world.item.Items.BREAD)) { bread += stack.getCount(); }
        }
        require(bread == episode, "ORIGINAL_PLAYER_DATA_LOOT_READBACK"); run.playerDataReadback = true;
        var advancements = readJson(world.resolve("advancements").resolve(account + ".json"));
        require(advancements.has(rewardId().toString()) && advancements.getAsJsonObject(rewardId().toString()).get("done").getAsBoolean(),
                "ORIGINAL_ADVANCEMENT_WRITER_READBACK"); run.advancementsReadback = true;
        var stats = readJson(world.resolve("stats").resolve(account + ".json")).getAsJsonObject("stats");
        var killed = stats.getAsJsonObject("minecraft:killed");
        var custom = stats.getAsJsonObject("minecraft:custom");
        int chicken = killed != null && killed.has("minecraft:chicken") ? killed.get("minecraft:chicken").getAsInt() : 0;
        int cow = killed != null && killed.has("minecraft:cow") ? killed.get("minecraft:cow").getAsInt() : 0;
        int mobKills = custom != null && custom.has("minecraft:mob_kills") ? custom.get("minecraft:mob_kills").getAsInt() : 0;
        require(statisticsMatch(episode, chicken, cow, mobKills) && run.fatalSourceObservations == 1,
                "ORIGINAL_STATISTICS_WRITER_READBACK"); run.statisticsReadback = true;
        run.readbackChickenKills = chicken; run.readbackCowKills = cow; run.readbackMobKills = mobKills;
    }

    private static boolean statisticsMatch(int episode, int chicken, int cow, int mobKills) {
        // Native awardKillScore uses kill credit; per-type killedEntity uses the final damage-source entity.
        return (episode == 1 || episode == 2) && chicken == 1 && cow == 0 && mobKills == episode;
    }

    private static com.google.gson.JsonObject readJson(Path file) throws IOException {
        require(java.nio.file.Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                && !java.nio.file.Files.isSymbolicLink(file) && java.nio.file.Files.size(file) <= 4 * 1024 * 1024,
                "OWNED_NATIVE_DATA_FILE_SHAPE");
        return com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
    }

    private static void fail(String code) {
        fail(code, null);
    }
    private static void fail(String code, Throwable primary) {
        if (terminal) { return; } terminal = true;
        Map<String, Object> contextObservation;
        try { contextObservation = P11L1ContextRefusalProbe.pending(); }
        catch (RuntimeException | Error ignoredObservation) { contextObservation = Map.of("status", "UNAVAILABLE"); }
        try { P11L1ContextRefusalProbe.abort(); }
        catch (RuntimeException | Error ignoredListenerCleanup) { /* Original failure stays primary. */ }
        boolean restored = true;
        if (statsMemory()) {
            try { P11L1StatsMemoryProbe.restoreAfterFailure(primary); P11L1StatsMemoryProbe.release(); }
            catch (IOException | RuntimeException | Error restorationFailure) { restored = false; }
        }
        try {
            if (output != null) {
                var facts = new LinkedHashMap<String, Object>();
                facts.put("status", "FAIL"); facts.put("code", code); facts.put("ticks", ticks); facts.put("episode", episode);
                facts.put("stage", stage.name()); facts.put("statsObstructionRestored", restored);
                facts.put("contextObservation", contextObservation);
                try { facts.put("naturalObservation", naturalFailureFacts()); }
                catch (RuntimeException | Error ignoredObservation) { facts.put("naturalObservation", Map.of("status", "UNAVAILABLE")); }
                P11C4aEvidence.write(output, "failure.json", facts);
            }
        } catch (IOException | RuntimeException | Error ignoredDiagnostic) { /* Same primary and FAIL; no message or raw frames. */ }
        if (server != null) { server.halt(false); }
    }

    private static Map<String, Object> naturalFailureFacts() {
        if (run == null) { return Map.of("status", "NO_NATURAL_RUN"); }
        var f = new LinkedHashMap<String, Object>();
        f.put("status", "CAPTURE_AT_FAILURE_NOT_CAUSAL_ATTRIBUTION");
        f.put("accepted", run.accepted); f.put("logoutComplete", run.logoutComplete); f.put("reconnected", run.reconnected);
        f.put("transfers", run.transfers); f.put("claims", run.claims); f.put("p5DamageReturns", run.damageReturns);
        f.put("nativeHurtReturns", run.nativeDamageReturns); f.put("nativeRewardReturns", run.rewardReturns);
        f.put("nativeFunctionReturns", run.functionReturns); f.put("openTick", run.openTick);
        f.put("sameUuidCollision", run.selfCollision); f.put("oldOwnerAfterReconnect", run.oldOwnerAfterReconnect);
        f.put("oldConnectionClosed", !run.connection.isConnected()); f.put("oldActorRemoved", run.actor.isRemoved());
        var instance = run.instanceState; var projectile = run.projectile;
        f.put("instancePresent", instance != null);
        if (instance != null) {
            f.put("logoutState", instance.logoutState.name()); f.put("workPresent", instance.work != null);
            f.put("pinClosed", instance.lease.pin.isClosed());
            f.put("permitState", instance.activeProjectileContinuation == null ? "ABSENT" : instance.activeProjectileContinuation.state.name());
            f.put("terminalReason", instance.p9Diagnostic == null || instance.p9Diagnostic.terminalReason == null
                    ? "ABSENT" : instance.p9Diagnostic.terminalReason.name());
        }
        f.put("projectilePresent", projectile != null);
        if (projectile != null) {
            f.put("projectileRemoved", projectile.isRemoved()); f.put("projectileAge", projectile.tickCount);
            f.put("projectileOwnerIsOldA", projectile.getOwner() == run.actor);
            f.put("projectileInOriginalLevel", run.actor.serverLevel().getEntity(projectile.getUUID()) == projectile);
        }
        f.put("victimPresent", run.victim != null);
        if (run.victim != null) { f.put("victimAlive", run.victim.isAlive()); f.put("victimKillCreditIsOldA", run.victim.getKillCredit() == run.actor); }
        f.put("workRoots", workRoots()); f.put("nativeCreditRoots", nativeRoots());
        f.put("operationRoots", roots("OPERATION")); f.put("commandContextRoots", roots("COMMAND_CONTEXT"));
        return f;
    }

    public static void rootRetired(MinecraftServer exact) {
        P11L1WorkBoundaryProbe.rootRetired(exact);
        P11L1TerminalBoundaryProbe.rootRetired(exact);
        if (!enabled() || exact != server || output == null) { return; }
        try {
            var summary = P11NativeStorageBoundary.terminalDiagnostics();
            require(summary != null, "ACTUAL_ROOT_RETIREMENT_SUMMARY_MISSING");
            var roots = summary.nativeResponsibilities().roots();
            require(roots.size() == 5 && roots.stream().map(value -> value.kind()).collect(java.util.stream.Collectors.toSet())
                    .equals(java.util.Set.of("WORK", "NATIVE_CREDIT", "OPERATION", "COMMAND_CONTEXT", "TRANSITION")), "ROOT_KIND_INVENTORY");
            // Actual retirement facts survive an unavailable selected-scenario acceptance premise.
            var facts = new LinkedHashMap<String, Object>();
            facts.put("nativeStopNormal", summary.nativeStopNormal()); facts.put("sourceFailures", summary.failures());
            facts.put("dirtyUuids", summary.resources().dirtyUuids()); facts.put("originalWrites", summary.writes());
            facts.put("allRootCounts", roots.stream().map(root -> Map.of("kind", root.kind(), "count", root.count())).toList());
            var expectation = selectedFailureExpectation(() -> workReward() ? P11L1WorkRewardProbe.expectedSourceFailures()
                    : statsMemory() ? P11L1StatsMemoryProbe.expectedSourceFailures() : 0);
            facts.putAll(expectation);
            boolean expectedAvailable = "AVAILABLE".equals(expectation.get("selectedFailureExpectation"));
            int expectedFailures = (int) expectation.get("expectedSelectedSourceFailures");
            String status = "TERMINAL_NOT_QUALIFIED";
            try {
                var healthy = expectedAvailable && (finishing || twoWorkStop() && P11L1WorkBoundaryProbe.complete()
                        || P11L1RestartProbe.writeSelected() && restartStopRequested) && finalLogouts == 2 && summary.nativeStopNormal()
                        && summary.failures() == expectedFailures && summary.resources().dirtyUuids() == 0;
                healthy = healthy && roots.stream().allMatch(value -> value.count() == 0);
                status = terminalBoundary() && P11L1TerminalBoundaryProbe.complete()
                        ? "NAMED_TERMINAL_BOUNDARY_SOURCE_OBSERVATION_NOT_HEALTHY_STOP_CLAIM"
                        : healthy ? "ORIGINAL_NORMAL_STOP_AND_CLEAN_SOURCE" : "TERMINAL_NOT_QUALIFIED";
                facts.put("selectedCompletionObservation", "AVAILABLE");
            } catch (RuntimeException | Error ignoredAcceptancePremise) {
                facts.put("selectedCompletionObservation", "UNAVAILABLE");
            }
            facts.put("status", status);
            P11C4aEvidence.write(output, "data-terminal.json", facts);
        } catch (IOException | RuntimeException | Error ignored) { /* Missing receipt is not qualification; preserve original stop. */ }
    }

    private static Map<String, Object> selectedFailureExpectation(java.util.function.IntSupplier original) {
        try {
            int expected = original.getAsInt();
            if (expected < 0) { throw new IllegalStateException("NEGATIVE_SELECTED_FAILURE_EXPECTATION"); }
            return Map.of("selectedFailureExpectation", "AVAILABLE", "expectedSelectedSourceFailures", expected);
        } catch (RuntimeException | Error unavailablePremise) {
            return Map.of("selectedFailureExpectation", "UNAVAILABLE", "expectedSelectedSourceFailures", -1);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void stopped(ServerStoppedEvent event) {
        if (!enabled() || event.getServer() != server || output == null) { return; }
        if (P11L1RestartProbe.writeSelected()) {
            try { P11L1RestartProbe.stopped(event.getServer()); }
            catch (IOException | RuntimeException | Error failure) {
                try { P11C4aEvidence.write(output, "l1-restart-stop-failure.json", Map.of("status", "FAIL", "code", code(failure))); }
                catch (IOException | RuntimeException | Error ignoredDiagnostic) { /* Preserve original stop. */ }
            }
        }
        try { P11C4aEvidence.write(output, "stopped.json", Map.of("status", terminalBoundary() && P11L1TerminalBoundaryProbe.complete()
                ? "ORIGINAL_STOP_AFTER_NAMED_TERMINAL_BOUNDARY"
                : (finishing || twoWorkStop() && P11L1WorkBoundaryProbe.stopRequested()
                        || P11L1RestartProbe.writeSelected() && restartStopRequested) && finalLogouts == 2 ? "ORIGINAL_STOP_AFTER_NAMED_EPISODES" : "STOP_AFTER_FAILURE_OR_READY_ONLY",
                "episodes", episode, "finalLogoutEvents", finalLogouts, "remainingPlayers", server.getPlayerList().getPlayers().size())); }
        catch (IOException ignored) { }
        if (statsMemory()) {
            // Also covers an original native error stop which never entered the parent's failure path.
            try { P11L1StatsMemoryProbe.restoreAfterFailure(null); P11L1StatsMemoryProbe.release(); }
            catch (IOException | RuntimeException | Error restorationFailure) {
                try { P11C4aEvidence.write(output, "stats-memory-cleanup-failure.json", Map.of("status", "FAIL",
                        "code", "OWNED_STATS_OBSTRUCTION_RESTORATION_FAILED")); }
                catch (IOException | RuntimeException | Error ignoredDiagnostic) { /* Never replace native stop failure. */ }
            }
        }
        P11L1WorkRewardProbe.abort();
        P11L1ContextRefusalProbe.abort();
        P11L1TrackingBoundaryProbe.release();
        P11L1NaturalUnloadProbe.release();
        P11L1OnlinePeerProbe.release();
        P11L1CapacityWorkProbe.release();
        P11L1ImpactCustodyProbe.release();
    }

    private static final class Run {
        final ServerPlayer actor; final Connection connection; final int started;
        final P11QualifiedSourceOwner.Body initialBody; final long initialEpoch; final int peerXpBefore, peerChickenBefore, peerMobKillsBefore;
        UUID instance; P9StarterProjectile projectile; LivingEntity victim; ServerSlot.InstanceState instanceState;
        int acceptedAt, logoutAt, reconnectTick, openTick, transfers, claims, damageReturns, nativeDamageReturns, logoutEvents;
        int functionReturns, rewardReturns, killScoreReturns, scoreAmount, scoreBefore, scoreAfter, fatalSourceObservations;
        int readbackChickenKills, readbackCowKills, readbackMobKills;
        long initialNative, firstHitNativeRoots, nativeRootsAtDeath, workRootsAtDeath; float selfBefore;
        boolean accepted, closing, logoutComplete, reconnected, oldOwnerAfterReconnect, selfPlaced, selfCollision, nativeOriginA, nativeProjectileExact, p5DamageApplied, hazard;
        boolean playerDataReadback, advancementsReadback, statisticsReadback;
        boolean scoreActive, scoreEntered;
        boolean fatalSourceHasEntity, fatalSourceOriginalProjectile, fatalSourceNativeHazard;
        boolean localRewardSealed, statsWorkSealed;
        Run(ServerPlayer actor, int tick) {
            this.actor = actor; this.connection = actor.connection.getConnection(); this.started = tick;
            initialBody = P11NativeStorageBoundary.nativeSourceOwner(actor).nativeRecipient(actor);
            initialEpoch = initialBody.source.epoch(); peerXpBefore = peer.totalExperience;
            peerChickenBefore = peer.getStats().getValue(net.minecraft.stats.Stats.ENTITY_KILLED.get(EntityType.CHICKEN));
            peerMobKillsBefore = peer.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.MOB_KILLS));
        }
    }
}
