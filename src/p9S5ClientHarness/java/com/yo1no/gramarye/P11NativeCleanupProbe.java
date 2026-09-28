package com.yo1no.gramarye;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Excluded actual-logout fault: proves only named structural facts, never opaque completion. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
final class P11NativeCleanupProbe {
    private static Observation active;
    private static boolean used;

    private P11NativeCleanupProbe() {}

    /** The caller owns termination after this expected original exception; CONFIG never starts. */
    static String run(MinecraftServer server, ServerPlayer actor) {
        require(System.getProperty(P11SourceWriterClientHarness.OUTPUT_PROPERTY) != null
                        && "cleanup-failure".equals(System.getProperty("gramarye.p11.sourceWriter.case")),
                "cleanup probe is restricted to its explicitly selected engineering run");
        require(!used && active == null && server.isSameThread() && actor.getServer() == server
                        && !actor.isFakePlayer() && server.getPlayerCount() == 1
                        && server.isSingleplayerOwner(actor.getGameProfile())
                        && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                        && actor.connection != null && actor.connection.getConnection().isConnected(),
                "cleanup probe requires the exact sole authenticated integrated actor");
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = owner == null ? null : owner.body(actor);
        require(body != null && body.complete && !body.logoutAttempted && body.envelope == null
                        && !body.account.cleanupUnknown && owner.canCopy(body),
                "cleanup probe requires a fresh complete body with no prior logout envelope");
        var source = body.source;
        var stats = actor.getStats();
        var advancements = actor.getAdvancements();
        long observerFailures = P11NativeStorageBoundary.observerFailureCount();
        long cleanupFailures = P11NativeCleanup.secondaryFailures();
        var observation = new Observation(actor);
        used = true;
        active = observation;
        try {
            boolean samePrimary = false;
            try { actor.connection.switchToConfig(); }
            catch (RuntimeException primary) {
                if (primary != observation.fault) { throw primary; }
                samePrimary = true;
            }
            require(samePrimary && observation.loggedOut == 1 && observation.leave == 1,
                    "actual native removal did not preserve the exact injected primary once");
            require(actor.isRemoved() && server.getPlayerList().getPlayer(actor.getUUID()) != actor
                            && server.getPlayerList().getPlayers().stream().noneMatch(value -> value == actor)
                            && actor.serverLevel().getEntity(actor.getId()) != actor
                            && actor.serverLevel().getEntity(actor.getUUID()) != actor,
                    "the named world/PlayerList structural tail retained the failed exact actor");
            require(owner.body(actor) == body && body.source == source && body.complete
                            && body.fault == P11QualifiedSourceOwner.Fault.NONE
                            && body.stats == stats && body.advancements == advancements
                            && owner.canonicalStats(stats) == body
                            && owner.canonicalAdvancements(advancements) == body,
                    "structural cleanup retired or replaced canonical source custody");
            require(body.logoutAttempted && !body.logoutActive && body.account.cleanupUnknown
                            && body.envelope == null && body.pendingEnvelope == null
                            && !owner.canCopy(body) && !P11NativeStorageBoundary.detachedPresence(actor),
                    "structural-only cleanup was promoted to a complete logout/source selection");
            var afterFirst = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
            boolean replayRefused = false;
            try { server.getPlayerList().remove(actor); }
            catch (P11QualifiedSourceOwner.SourceUnavailable expected) { replayRefused = true; }
            var afterReplay = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
            require(replayRefused && observation.loggedOut == 1 && observation.leave == 1
                            && afterReplay.sourceEpoch() == afterFirst.sourceEpoch()
                            && afterReplay.sourceVersion() == afterFirst.sourceVersion()
                            && afterReplay.writers().equals(afterFirst.writers())
                            && afterReplay.serializations() == afterFirst.serializations()
                            && afterReplay.writes() == afterFirst.writes(),
                    "second removal replayed an event, writer, or whole native cleanup");
            require(P11NativeStorageBoundary.observerFailureCount() == observerFailures
                            && P11NativeCleanup.secondaryFailures() == cleanupFailures,
                    "an observer/repair failure was hidden behind the primary");
            return "layer=ACTUAL_SWITCH_TO_CONFIG_LOGOUT_AND_ENTITY_LEAVE_FAILURE\n"
                    + "primaryPropagatedSame=true\nloggedOutEvents=" + observation.loggedOut
                    + "\nleaveEvents=" + observation.leave
                    + "\nworldAndRosterExactActorAbsent=true\ncanonicalOwnersRetained=true\n"
                    + "cleanupDisposition=STRUCTURAL_ONLY\nwholeNativeCompletion=false\n"
                    + "fullLogoutEnvelopePromoted=false\nrepeatNativeRemovalRefused=true\n"
                    + "copySelectionRefused=true\nconfigurationPacketSent=false\n"
                    + "opaqueBossBroadcastTailClaimed=false\n" + afterReplay + "\n";
        } finally { active = null; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        var observation = active;
        if (observation != null && event.getEntity() == observation.actor) { observation.loggedOut++; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void leave(EntityLeaveLevelEvent event) {
        var observation = active;
        if (observation == null || event.getEntity() != observation.actor) { return; }
        observation.leave++;
        require(observation.leave == 1 && observation.loggedOut == 1
                        && observation.actor.getServer().isSameThread(),
                "cleanup injection did not follow the exact native logout once");
        throw observation.fault;
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }

    private static final class Observation {
        final ServerPlayer actor;
        final RuntimeException fault = new RuntimeException("P11_OWNED_ENTITY_LEAVE_FAULT");
        int loggedOut, leave;
        Observation(ServerPlayer actor) { this.actor = actor; }
    }
}
