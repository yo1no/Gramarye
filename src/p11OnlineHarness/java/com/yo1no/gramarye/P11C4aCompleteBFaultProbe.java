package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Excluded exact complete-B event fault. Never calls respawn, removes a player or changes a ticket. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aCompleteBFaultProbe {
    enum LaterRoute { UNOBSERVED, ORIGINAL_CONNECTED_CATCH, ORIGINAL_RUNSERVER_CATCH }
    private static final String PREFIX = "c4a_full_b_native_prefix";
    private static final ResourceLocation SKILLS = ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "player_skills");
    private static final ResourceLocation MANA = ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "player_mana");
    private static Run active;
    private P11C4aCompleteBFaultProbe() { }
    static boolean selected() { return P11C4aScenario.MODE == P11C4aScenario.Mode.NATIVE_COMPLETE_B_FAULT; }

    static void start(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output) {
        require(selected() && active == null && server.isSameThread()
                && actor != peer && actor.connection.getConnection().isConnected()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                && !server.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)
                && !server.getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN), "FULL_B_START_NATIVE_DEATH_RULES");
        var run = new Run(server, actor, peer, output);
        var before = run.source.diagnostics(actor.getUUID());
        var body = run.source.nativeRecipient(actor);
        require(body != null && body.actor == actor && run.source.canCopy(body)
                && before.sourceEpoch() > 0 && before.sourceEpoch() < Long.MAX_VALUE
                && body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0,
                "FULL_B_SOURCE_NOT_INITIAL_QUIESCENT");
        var material = material(actor);
        run.startSkillsPresent = material.skills != null;
        run.startManaPresent = material.mana != null;
        run.beforeEpoch = before.sourceEpoch();
        run.beforeVersion = before.sourceVersion();
        run.beforeWriterAttempt = before.writers().stream().filter(w -> w.kind().equals("PLAYER_DATA"))
                .mapToLong(P11QualifiedSourceOwner.WriterDiagnostic::attempt).findFirst().orElse(0);
        active = run;
    }

    /** Observe only the real NF attachment-copy producer, after any ordinary intervening P7 observation. */
    public static Object attachmentCopyEntered(Entity from, Entity to, boolean wasDeath) {
        var run = active;
        if (run == null || !selected() || from != run.original) { return null; }
        try {
            require(run.server.isSameThread() && run.failure == null && wasDeath
                    && to instanceof ServerPlayer && to != from && run.copyEntries++ == 0
                    && run.source.body((ServerPlayer) to) != null, "FULL_B_EXACT_NATIVE_ATTACHMENT_COPY_ENTRY");
            run.copyTarget = (ServerPlayer) to;
            run.copyInput = material(run.original);
            run.copyTargetBefore = material(run.copyTarget);
        } catch (RuntimeException | Error secondary) { run.failure = "FULL_B_ATTACHMENT_COPY_ENTRY_OBSERVER_FAILED"; }
        return run;
    }

    public static void attachmentCopyEnded(Object token, boolean normal) {
        if (!(token instanceof Run run)) { return; }
        try {
            require(active == run && run.failure == null && normal && run.copyEntries == 1 && run.copyReturns++ == 0,
                    "FULL_B_NATIVE_ATTACHMENT_COPY_NOT_RETURNED");
            run.copyOutput = material(run.copyTarget);
            require(copyMatches(run.copyInput.skills, run.copyInput.skillTag,
                            run.copyTargetBefore.skills, run.copyTargetBefore.skillTag,
                            run.copyOutput.skills, run.copyOutput.skillTag)
                    && copyMatches(run.copyInput.mana, run.copyInput.manaTag,
                            run.copyTargetBefore.mana, run.copyTargetBefore.manaTag,
                            run.copyOutput.mana, run.copyOutput.manaTag), "FULL_B_NATIVE_ATTACHMENT_COPY_PARITY");
        } catch (RuntimeException | Error secondary) { run.failure = "FULL_B_ATTACHMENT_COPY_RETURN_OBSERVER_FAILED"; }
    }

    /** Pure evidence predicate: source absence preserves the actual target, never invents a default. */
    static boolean copyMatches(Object source, Tag sourceTag, Object targetBefore, Tag beforeTag,
            Object targetAfter, Tag afterTag) {
        if ((source != null) != (sourceTag != null) || (targetBefore != null) != (beforeTag != null)
                || (targetAfter != null) != (afterTag != null)) { return false; }
        return source == null ? targetAfter == targetBefore && java.util.Objects.equals(beforeTag, afterTag)
                : targetAfter != null && targetAfter != source && targetAfter != targetBefore && sourceTag.equals(afterTag);
    }

    private static Material material(ServerPlayer actor) {
        var skills = NeoForgeRegistries.ATTACHMENT_TYPES.get(SKILLS);
        var mana = NeoForgeRegistries.ATTACHMENT_TYPES.get(MANA);
        require(skills != null && mana != null, "FULL_B_REGISTERED_ATTACHMENT_TYPES_MISSING");
        Object skillValue = actor.getExistingDataOrNull(skills), manaValue = actor.getExistingDataOrNull(mana);
        CompoundTag serialized = actor.serializeAttachments(actor.getServer().registryAccess());
        Tag skillTag = serialized == null ? null : serialized.get(SKILLS.toString());
        Tag manaTag = serialized == null ? null : serialized.get(MANA.toString());
        require(actor.hasData(skills) == (skillValue != null) && actor.hasData(mana) == (manaValue != null)
                && (skillValue != null) == (skillTag != null) && (manaValue != null) == (manaTag != null),
                "FULL_B_ACTUAL_ATTACHMENT_PRESENCE_INCONSISTENT");
        return new Material(skillValue, skillTag == null ? null : skillTag.copy(),
                manaValue, manaTag == null ? null : manaTag.copy());
    }

    private record Material(Object skills, Tag skillTag, Object mana, Tag manaTag) { }

    @SubscribeEvent(priority = EventPriority.LOWEST) static void death(LivingDeathEvent event) {
        var run = active;
        if (run != null && event.getEntity() == run.original) { run.deaths++; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) static void clone(PlayerEvent.Clone event) {
        var run = active;
        if (run == null || event.getOriginal() != run.original) { return; }
        require(event.getEntity() instanceof ServerPlayer && run.server.isSameThread()
                && event.isWasDeath() && ++run.clones == 1 && run.failure == null
                && run.copyEntries == 1 && run.copyReturns == 1 && event.getEntity() == run.copyTarget,
                "FULL_B_ORIGINAL_CLONE_EVENT");
        run.next = (ServerPlayer) event.getEntity();
        require(run.next != run.original && run.next.getAdvancements() == run.advancements
                && run.next.getStats() == run.stats && run.next.getHealth() == run.next.getMaxHealth()
                && run.next.getHealth() > 10 && run.next.totalExperience == 0,
                "FULL_B_NATIVE_CLONE_PREFIX");
        run.next.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10);
        require(run.next.getHealth() > run.next.getMaxHealth(), "FULL_B_NATIVE_CLAMP_INPUT");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        var run = active;
        if (run == null || event.getEntity() != run.next) { return; }
        require(run.server.isSameThread() && !event.isEndConquered() && ++run.respawns == 1
                && run.clones == 1 && run.next.getHealth() == 10
                && run.server.getPlayerList().getPlayer(run.original.getUUID()) == run.next
                && run.next.serverLevel().getEntity(run.next.getId()) == run.next
                && run.next.serverLevel().getEntity(run.next.getUUID()) == run.next
                && run.original.connection.player == run.original && run.next.connection == run.original.connection,
                "FULL_B_TRUE_RESPAWN_EVENT_AFTER_MATERIAL_AND_ROSTER");
        var body = run.source.body(run.next);
        require(body != null && body.complete && body.fault == P11QualifiedSourceOwner.Fault.NONE
                && run.source.nativeRecipient(run.next) == body && body.account.current == body
                && body.account.candidate == null && run.source.body(run.original) == null,
                "FULL_B_EVENT_NOT_QUALIFIED_COMPLETE_B");
        run.body = body;
        var copied = material(run.next);
        run.eventSkillsPresent = copied.skills != null; run.eventManaPresent = copied.mana != null;
        run.manaChangedAfterCopy = copied.mana != run.copyOutput.mana
                || !java.util.Objects.equals(copied.manaTag, run.copyOutput.manaTag);
        require(run.failure == null && run.copyReturns == 1
                && run.next.getEnderChestInventory() == run.original.getEnderChestInventory()
                && run.next.getFoodData() != run.original.getFoodData(), "FULL_B_NATIVE_COPY_PARITY");
        run.epochAtEvent = run.source.diagnostics(run.original.getUUID()).sourceEpoch();
        run.hAtEvent = body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()];
        require(run.epochAtEvent == run.beforeEpoch + 1 && run.hAtEvent > 0, "FULL_B_ACTUAL_NEW_SOURCE_AND_CUSTODY");
        AdvancementRewards.Builder.experience(17).build().grant(run.next);
        run.next.addTag(PREFIX);
        require(run.next.totalExperience == 17, "FULL_B_ORIGINAL_REWARD_PREFIX");
        run.versionAtFault = run.source.diagnostics(run.original.getUUID()).sourceVersion();
        P11C4aNativeErrorProbe.completeBFault(run.next); // Throws its exact owned RuntimeException through the original event bus.
        throw new IllegalStateException("FULL_B_OWNED_FAULT_DID_NOT_ESCAPE");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        var run = active;
        if (run == null) { return; }
        if (event.getEntity() == run.next) { run.fullBLogouts++; }
        if (event.getEntity() == run.original) { run.originalTransportLogouts++; }
        if (event.getEntity() == run.peer) { run.peerLogouts++; }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) static void leave(EntityLeaveLevelEvent event) {
        var run = active;
        if (run == null) { return; }
        if (event.getEntity() == run.original) { run.originalLeaves++; }
        if (event.getEntity() == run.next) { run.fullBLeaves++; }
    }
    public static void loaded(Entity entity) {
        var run = active;
        if (run != null && entity instanceof ServerPlayer player && player.getServer() == run.server
                && player.getUUID().equals(run.original.getUUID())) { run.loads++; }
    }
    static ServerPlayer exactBody() { var run = active; return run == null ? null : run.next; }

    /** Called after original F1 cleanup, original suppressing server policy and complete service finally. */
    static void policyReturned() throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && !run.policyObserved && run.body != null
                && run.deaths == 1 && run.clones == 1 && run.respawns == 1 && run.loads == 0
                && run.originalLeaves == 1 && run.fullBLeaves == 1 && run.fullBLogouts == 1
                && run.originalTransportLogouts == 0 && run.peerLogouts == 0,
                "FULL_B_ORIGINAL_F1_EVENT_COUNTS");
        require(run.next.isRemoved() && run.original.isRemoved() && run.original.connection.player == run.original
                && run.server.getPlayerList().getPlayer(run.original.getUUID()) == null
                && run.next.serverLevel().getEntity(run.next.getId()) != run.next
                && run.next.serverLevel().getEntity(run.next.getUUID()) != run.next
                && run.source.body(run.next) == run.body && run.source.body(run.original) == null
                && run.body.complete && run.body.fault == P11QualifiedSourceOwner.Fault.NONE
                && run.body.account.current == run.body && run.body.account.candidate == null
                && run.body.logoutAttempted && !run.body.logoutActive && run.body.envelope != null
                && run.body.pendingEnvelope == null && !run.body.account.cleanupUnknown && run.source.canSerialize(run.body)
                && run.body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0
                && run.body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0
                && run.body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0
                && run.source.canonicalAdvancements(run.advancements) == run.body
                && run.source.canonicalStats(run.stats) == run.body,
                "FULL_B_NATIVE_CLEANUP_OR_SOURCE_OWNER");
        require(P11NativeStorageBoundary.observerFailureCount() == run.observerBefore
                && P11NativeCleanup.secondaryFailures() == run.cleanupBefore, "FULL_B_SECONDARY_FAILURE");
        var diagnostics = run.source.diagnostics(run.original.getUUID());
        require(diagnostics.sourceEpoch() == run.epochAtEvent && diagnostics.sourceVersion() >= run.versionAtFault,
                "FULL_B_TERMINAL_SOURCE_REPLACED");
        var writer = diagnostics.writers().stream().filter(w -> w.kind().equals("PLAYER_DATA")).findFirst().orElseThrow();
        require(writer.attempt() > run.beforeWriterAttempt && writer.terminal().equals("COMPLETED")
                && writer.encode().equals("SUCCEEDED") && writer.write().equals("SUCCEEDED")
                && writer.close().equals("SUCCEEDED") && writer.replace().equals("SUCCEEDED"),
                "FULL_B_NATIVE_PHYSICAL_PLAYER_WRITE_INCOMPLETE");
        run.firstReadback = readback(run);
        run.sourceAfterPolicy = P11C4aEvidence.sourceObservation(diagnostics);
        run.policyObserved = true;
        run.peerTick = run.peer.tickCount; run.serverTick = run.server.getTickCount();
        run.peerTime = run.peer.serverLevel().getGameTime();
        P11C4aEvidence.write(run.output, "complete-b-native-f1.json", report(run, "ORIGINAL_COMPLETE_B_FAULT_LOGOUT_AND_PHYSICAL_READBACK"));
        P11C4aEvidence.cue(run.output, "complete-b-policy-returned.ready");
    }

    static boolean afterClientProof() throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && run.policyObserved && run.failure == null && ++run.ticks <= 2400,
                "FULL_B_PEER_OWNER_OR_DEADLINE");
        if (!run.peerProved) {
            require(run.peer.connection.getConnection() == run.peerConnection && run.peerConnection.isConnected()
                    && run.peer.isAlive() && run.server.getPlayerList().getPlayer(run.peer.getUUID()) == run.peer,
                    "FULL_B_DISTINCT_PEER_DISRUPTED");
            if (run.server.getTickCount() - run.serverTick < 20 || run.peer.tickCount - run.peerTick < 20
                    || run.peer.serverLevel().getGameTime() - run.peerTime < 20
                    || !P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-b"), "complete-b-peer-continuation.json")) { return false; }
            run.peerProved = true;
            P11C4aEvidence.write(run.output, "complete-b-peer-continuation.json", report(run, "OTHER_AUTHENTICATED_PLAYER_ORIGINAL_NATIVE_TICKS_CONTINUED"));
            run.peerExitCued = true;
            P11C4aEvidence.cue(run.output, "b-complete-b-exit.ready");
        }
        if (!run.peerExitCued || run.peerConnection.isConnected()
                || !P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-b"), "complete-b-client-terminal.json")) { return false; }
        require(run.fullBLogouts == 1 && run.fullBLeaves == 1 && run.peerLogouts == 1
                && run.loads == 0 && run.source.body(run.original) == null
                && P11C4aNativeObservations.tries(run.original.connection.getConnection()) == 1
                && P11C4aNativeObservations.tries(run.peerConnection) == 0
                && run.original.connection.player == run.original, "FULL_B_NO_REPLAY_OR_EXTRA_B_LOGOUT");
        if (!run.leaveCued) {
            require(run.original.connection.getConnection().isConnected(), "FULL_B_A_CLOSED_BEFORE_SEPARATE_LEAVE_EPISODE");
            run.leaveCued = true;
            P11C4aEvidence.write(run.output, "complete-b-pre-leave.json", report(run, "PEER_TERMINAL_THEN_ORIGINAL_FAULT_LEAVE_ALLOWED"));
            P11C4aEvidence.cue(run.output, "a-complete-b-leave.ready");
        }
        boolean clientsTerminal = !run.original.connection.getConnection().isConnected()
                && !run.peerConnection.isConnected()
                && P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-a"), "complete-b-client-terminal.json")
                && P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-b"), "complete-b-client-terminal.json");
        if (!connectedCleanupReady(run.laterRoute, run.connectedCatchReturns, run.connectedTickReturns,
                run.runServerCatches, run.server.getTickCount(), run.connectedTick, clientsTerminal)) { return false; }
        require(!run.cleanupRequested && run.staleRemoveEntries == 1 && run.staleRemoveThrows == 1
                && run.source.body(run.next) == run.body && run.body.account.current == run.body
                && run.body.account.candidate == null && run.source.canSerialize(run.body)
                && run.source.diagnostics(run.original.getUUID()).sourceEpoch() == run.epochAtEvent
                && run.body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0
                && run.body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0
                && run.body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0,
                "FULL_B_CONNECTED_CATCH_CONTINUATION_SOURCE");
        run.cleanupRequested = true;
        writeLaterRoute(run); // This later real server tick is outside the native connections monitor.
        P11C4aEvidence.write(run.output, "complete-b-connected-continuation.json", report(run,
                "ORIGINAL_CONNECTED_CATCH_TICK_RETURN_THEN_NATIVE_CONTINUATION_AND_BOTH_CLIENT_TERMINALS"));
        return true; // Only this fully observed continuing branch permits labelled ordinary harness shutdown.
    }

    /** Exact later original stale-A cleanup call; observation only and never a substitute remove. */
    public static Object staleRemoveEntered(ServerPlayer actor) {
        var run = active;
        if (run == null || actor != run.original || !run.policyObserved || !run.leaveCued) { return null; }
        try {
            require(run.server.isSameThread() && run.failure == null && run.staleRemoveEntries++ == 0
                    && !run.original.connection.getConnection().isConnected() && !run.peerConnection.isConnected()
                    && run.peerLogouts == 1 && run.fullBLogouts == 1 && run.originalTransportLogouts == 0
                    && run.source.hasAccount(actor.getUUID()) && run.source.body(actor) == null
                    && run.source.body(run.next) == run.body && run.body.account.current == run.body
                    && run.original.connection.player == run.original, "FULL_B_LATER_STALE_A_REMOVE_OWNER");
        } catch (RuntimeException | Error secondary) { run.failure = "FULL_B_LATER_REMOVE_ENTRY_OBSERVER_FAILED"; }
        return run;
    }

    public static void staleRemoveEnded(Object token, boolean normal, Throwable primary) {
        if (!(token instanceof Run run)) { return; }
        try {
            require(run.failure == null && !normal && primary instanceof P11QualifiedSourceOwner.SourceUnavailable
                    && run.staleRemoveEntries == 1 && run.staleRemoveThrows++ == 0
                    && run.originalTransportLogouts == 0 && run.fullBLogouts == 1 && run.fullBLeaves == 1,
                    "FULL_B_LATER_GUARD_DID_NOT_PRESERVE_NATIVE_PRIMARY");
            run.stalePrimary = primary;
            // No I/O in the native connections monitor. The exact original receiver writes this later.
        } catch (RuntimeException | Error secondary) { run.failure = "FULL_B_LATER_REMOVE_RETURN_OBSERVER_FAILED"; }
    }

    /** Original connected Exception handler's final call returned; scalar observation only under its monitor. */
    public static void connectedCatchReturned(ServerConnectionListener listener, Connection connection, Exception primary) {
        var run = active;
        if (run == null || !run.policyObserved || !run.leaveCued
                || connection != run.original.connection.getConnection()) { return; }
        try {
            require(run.server.isSameThread() && listener.getServer() == run.server && run.failure == null
                    && run.stalePrimary != null && primary == run.stalePrimary
                    && run.staleRemoveEntries == 1 && run.staleRemoveThrows == 1
                    && run.laterRoute == LaterRoute.UNOBSERVED && run.runServerCatches == 0
                    && run.connectedCatchReturns++ == 0 && !connection.isConnected(),
                    "FULL_B_CONNECTED_CATCH_EXACT_PRIMARY_OR_OWNER");
            run.connectedOwner = listener;
        } catch (RuntimeException | Error secondary) { run.failure = "FULL_B_CONNECTED_CATCH_OBSERVER_FAILED"; }
    }

    /** Unique normal tick RETURN is after monitorexit, not just the catch logger/send RETURN. */
    public static void connectedTickReturned(ServerConnectionListener listener) {
        var run = active;
        if (run == null || run.connectedOwner != listener || run.laterRoute != LaterRoute.UNOBSERVED) { return; }
        try {
            require(run.server.isSameThread() && run.failure == null && run.connectedCatchReturns == 1
                    && run.connectedTickReturns++ == 0 && run.runServerCatches == 0,
                    "FULL_B_CONNECTED_CATCH_WHOLE_TICK_NOT_RETURNED");
            run.connectedTick = run.server.getTickCount();
            run.laterRoute = LaterRoute.ORIGINAL_CONNECTED_CATCH;
            run.connectedOwner = null;
        } catch (RuntimeException | Error secondary) { run.failure = "FULL_B_CONNECTED_TICK_OBSERVER_FAILED"; }
    }

    /** Called by the existing original native logger observer; first F1 primary is never relabelled. */
    static void laterOuter(Throwable primary, boolean taskCatch) {
        var run = active;
        if (run == null || run.stalePrimary == null || primary != run.stalePrimary) { return; }
        try {
            require(run.failure == null && run.policyObserved && run.leaveCued && !taskCatch
                    && run.staleRemoveThrows == 1 && run.originalTransportLogouts == 0
                    && run.laterRoute == LaterRoute.UNOBSERVED && run.connectedCatchReturns == 0
                    && run.connectedTickReturns == 0 && run.runServerCatches++ == 0 && !run.cleanupRequested
                    && run.body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0,
                    "FULL_B_LATER_NATIVE_OUTER_ROUTE");
            run.laterRoute = LaterRoute.ORIGINAL_RUNSERVER_CATCH;
            writeLaterRoute(run);
        } catch (IOException | RuntimeException | Error secondary) { run.failure = "FULL_B_LATER_OUTER_OBSERVER_FAILED"; }
    }

    private static void writeLaterRoute(Run run) throws IOException {
        P11C4aEvidence.write(run.output, "complete-b-stale-a-remove.json", report(run,
                "LATER_ORIGINAL_LEAVE_STALE_A_GUARD_THROW_BEFORE_NATIVE_REMOVE_BODY"));
        P11C4aEvidence.write(run.output, "complete-b-later-native-outer.json", report(run,
                "LATER_SAME_SOURCE_UNAVAILABLE_AT_ORIGINAL_RECEIVER_NOT_FIRST_EVENT_POLICY"));
    }

    /** Pure evidence predicates only, never a product permission or a way to select a native branch. */
    static boolean connectedCleanupReady(LaterRoute route, int catchReturns, int tickReturns, int crashes,
            int nowTick, int returnedTick, boolean clientsTerminal) {
        return route == LaterRoute.ORIGINAL_CONNECTED_CATCH && catchReturns == 1 && tickReturns == 1
                && crashes == 0 && nowTick > returnedTick && clientsTerminal;
    }
    static boolean terminalRouteValid(LaterRoute route, int catchReturns, int tickReturns, int crashes, boolean cleanup) {
        return route == LaterRoute.ORIGINAL_CONNECTED_CATCH && catchReturns == 1 && tickReturns == 1 && crashes == 0 && cleanup
                || route == LaterRoute.ORIGINAL_RUNSERVER_CATCH && catchReturns == 0 && tickReturns == 0 && crashes == 1 && !cleanup;
    }

    static void stopped(MinecraftServer server) {
        var run = active;
        if (run == null || run.server != server) { return; }
        try {
            var terminal = P11NativeStorageBoundary.terminalDiagnostics();
            if (terminal != null) {
                run.stopObserved = true; run.nativeStopNormal = terminal.nativeStopNormal();
                run.terminalFailures = terminal.failures(); run.terminalDirty = terminal.resources().dirtyUuids();
            }
            require(run.failure == null && run.peerProved && run.peerExitCued && run.leaveCued
                    && terminalRouteValid(run.laterRoute, run.connectedCatchReturns, run.connectedTickReturns,
                            run.runServerCatches, run.cleanupRequested)
                    && run.staleRemoveEntries == 1 && run.staleRemoveThrows == 1 && run.originalTransportLogouts == 0
                    && terminal != null && terminal.nativeStopNormal()
                    && terminal.failures() == 1 && terminal.resources().dirtyUuids() == 0
                    && readback(run).equals(run.firstReadback), "FULL_B_NORMAL_TEARDOWN_DURABILITY");
            var values = new LinkedHashMap<>(report(run, "ORIGINAL_F1_DATA_AND_SEPARATE_OBSERVED_LATER_ROUTE_STOP_PHYSICAL_FACTS"));
            values.put("nativeStopNormal", terminal.nativeStopNormal()); values.put("remainingDirtyUuids", terminal.resources().dirtyUuids());
            values.put("nativeFailures", terminal.failures()); values.put("physicalWrites", terminal.writes());
            values.put("expectedInjectedLifecycleFailureCount", 1);
            P11C4aEvidence.write(run.output, "complete-b-stopped.json", values);
        } catch (IOException | RuntimeException | Error secondary) {
            // An observation fault never changes the original completed native stop into an exception.
            // Success requires complete-b-stopped.json; this fixed failure receipt cannot substitute it.
            try { P11C4aEvidence.write(run.output, "complete-b-stopped-failure.json", Map.of(
                    "status", "FAIL", "code", "FULL_B_STOP_OBSERVATION_FAILED",
                    "terminalSummaryObserved", run.stopObserved, "nativeStopNormal", run.nativeStopNormal,
                    "nativeFailures", run.terminalFailures, "remainingDirtyUuids", run.terminalDirty,
                    "category", secondary instanceof IOException ? "IO" : secondary instanceof Error ? "ERROR" : "RUNTIME")); }
            catch (IOException | RuntimeException | Error unrecorded) { /* Missing success remains failure. */ }
        } finally { active = null; }
    }
    static void abort() { active = null; }
    private static Map<String,Object> readback(Run run) throws IOException {
        var path = run.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(run.original.getUUID() + ".dat");
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= 32L * 1024 * 1024,
                "FULL_B_OWNED_NATIVE_PLAYER_FILE");
        var nbt = NbtIo.readCompressed(path, NbtAccounter.create(32L * 1024 * 1024));
        boolean tag = nbt.getList("Tags", Tag.TAG_STRING).stream().anyMatch(value -> value.getAsString().equals(PREFIX));
        require(nbt.getInt("XpTotal") == 17 && nbt.getFloat("Health") == 10 && tag,
                "FULL_B_SAVED_CALLBACK_PREFIX_LOST");
        return Map.of("xp", nbt.getInt("XpTotal"), "health", nbt.getFloat("Health"), "fixedPrefixTagPresent", tag);
    }
    private static Map<String,Object> report(Run run, String status) {
        var values = new LinkedHashMap<String,Object>();
        values.put("status", status); values.put("deathEvents", run.deaths); values.put("cloneEvents", run.clones);
        values.put("completeBRespawnCallbacks", run.respawns); values.put("originalALeaves", run.originalLeaves);
        values.put("fullBLeaves", run.fullBLeaves); values.put("fullBOriginalLogouts", run.fullBLogouts);
        values.put("originalATransportLogoutsAfterProof", run.originalTransportLogouts); values.put("peerOriginalLogouts", run.peerLogouts);
        values.put("actualEntityLoads", run.loads); values.put("sourceEpochBefore", run.beforeEpoch);
        values.put("sourceEpochAtCompleteBCallback", run.epochAtEvent); values.put("actualHAtCallback", run.hAtEvent);
        values.put("sourceVersionBeforeDeath", run.beforeVersion); values.put("sourceVersionAtEventFault", run.versionAtFault);
        values.put("sourceAfterPolicy", run.sourceAfterPolicy);
        values.put("attachmentPresenceAtStart", Map.of("skills", run.startSkillsPresent, "mana", run.startManaPresent));
        values.put("actualAttachmentCopyEntries", run.copyEntries); values.put("actualAttachmentCopyNormalReturns", run.copyReturns);
        values.put("attachmentPresenceAtCopyInput", presence(run.copyInput));
        values.put("attachmentPresenceAtCopyTargetBefore", presence(run.copyTargetBefore));
        values.put("attachmentPresenceAtCopyReturn", presence(run.copyOutput));
        values.put("attachmentPresenceAtQualifiedRespawnEvent", Map.of("skills", run.eventSkillsPresent, "mana", run.eventManaPresent));
        values.put("manaChangedBetweenCopyReturnAndQualifiedEvent", run.manaChangedAfterCopy);
        values.put("attachmentParityRule", "PRESENT_SERIALIZED_EQUAL_FRESH_OBJECT_ABSENT_PRESERVES_ORIGINAL_TARGET");
        values.put("fixtureMaterialProvisioning", false);
        values.put("sourceLatest", run.source.owns(run.server)
                ? P11C4aEvidence.sourceObservation(run.source.diagnostics(run.original.getUUID()))
                : Map.of("status", "ORIGINAL_OWNER_RETIRED_SEE_TERMINAL_SUMMARY"));
        values.put("originalPlayerFileReadback", run.firstReadback); values.put("otherAuthenticatedPeerContinuation", run.peerProved);
        values.put("laterStaleARemoveEntries", run.staleRemoveEntries); values.put("laterStaleARemoveThrows", run.staleRemoveThrows);
        values.put("laterOriginalRoute", run.laterRoute.name());
        values.put("laterSourceUnavailableSamePrimaryAtNativeRunServerCatch", run.runServerCatches == 1);
        values.put("laterSamePrimaryConnectedCatchSetReadOnlyReturns", run.connectedCatchReturns);
        values.put("laterConnectedWholeTickReturns", run.connectedTickReturns);
        values.put("laterConnectedReturnServerTick", run.connectedTick);
        values.put("postProofOrdinaryShutdownRequested", run.cleanupRequested);
        values.put("firstEventPolicyAndLaterLeavePrimaryAreSeparate", true);
        values.put("failureCode", run.failure == null ? "NONE" : run.failure);
        values.put("nativePolicyAutoHaltClaimed", false); values.put("fullC4aAcceptance", false);
        return values;
    }
    private static Map<String,Object> presence(Material material) {
        return material == null ? Map.of("observed", false)
                : Map.of("observed", true, "skills", material.skills != null, "mana", material.mana != null);
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer original, peer; final Path output;
        final net.minecraft.network.Connection peerConnection; final P11QualifiedSourceOwner source;
        final PlayerAdvancements advancements; final ServerStatsCounter stats;
        final long observerBefore = P11NativeStorageBoundary.observerFailureCount(), cleanupBefore = P11NativeCleanup.secondaryFailures();
        ServerPlayer next, copyTarget; P11QualifiedSourceOwner.Body body;
        Material copyInput, copyTargetBefore, copyOutput;
        long beforeEpoch, beforeVersion, beforeWriterAttempt, epochAtEvent, versionAtFault, hAtEvent, peerTime;
        int deaths, clones, respawns, fullBLogouts, originalTransportLogouts, peerLogouts, originalLeaves, fullBLeaves, loads;
        int peerTick, serverTick, ticks, staleRemoveEntries, staleRemoveThrows, copyEntries, copyReturns;
        int connectedCatchReturns, connectedTickReturns, runServerCatches, connectedTick = -1;
        boolean policyObserved, peerProved, peerExitCued, leaveCued, cleanupRequested, stopObserved, nativeStopNormal;
        boolean startSkillsPresent, startManaPresent, eventSkillsPresent, eventManaPresent, manaChangedAfterCopy;
        LaterRoute laterRoute = LaterRoute.UNOBSERVED;
        ServerConnectionListener connectedOwner;
        long terminalFailures = -1, terminalDirty = -1;
        Throwable stalePrimary; String failure;
        Map<String,Object> sourceAfterPolicy, firstReadback;
        Run(MinecraftServer server, ServerPlayer original, ServerPlayer peer, Path output) {
            this.server=server; this.original=original; this.peer=peer; this.output=output;
            peerConnection=peer.connection.getConnection(); source=P11NativeStorageBoundary.nativeSourceOwner(original);
            advancements=original.getAdvancements(); stats=original.getStats();
        }
    }
}
