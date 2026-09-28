package com.yo1no.gramarye;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Excluded actual-constructor fault, after the original PA association; never mints source proof. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11AssociationFaultProbe {
    private static MinecraftServer server;
    private static ServerPlayer original;
    private static PlayerAdvancements canonical;
    private static long epoch;
    private static boolean unknownCallback;
    private static boolean armed;
    private static boolean fired;
    private static volatile String report;
    private static volatile RuntimeException observationFailure;
    private static final AssociationFault FAULT = new AssociationFault();

    private P11AssociationFaultProbe() {}

    static void arm(MinecraftServer actual, ServerPlayer actor, boolean afterCallbackEntry) {
        require(System.getProperty(P11SourceWriterClientHarness.OUTPUT_PROPERTY) != null
                        && (afterCallbackEntry ? "association-unknown" : "association-failure")
                                .equals(System.getProperty("gramarye.p11.sourceWriter.case")),
                "association probe is restricted to its explicitly selected engineering run");
        require(server == null && !armed && !fired, "association probe is one-shot per process");
        require(actual.isSameThread() && actor.getServer() == actual && !actor.isFakePlayer()
                        && actual.getPlayerList().getPlayer(actor.getUUID()) == actor
                        && actor.connection != null && actor.connection.getConnection().isConnected(),
                "association probe requires an actual authenticated current actor");
        var diagnostic = P11NativeStorageBoundary.diagnostics(actual, actor.getUUID());
        require(diagnostic.bodyComplete() && !diagnostic.candidatePresent()
                        && diagnostic.sourceFault().equals("NONE"), "source is not complete");
        server = actual; original = actor; canonical = actor.getAdvancements();
        epoch = diagnostic.sourceEpoch(); unknownCallback = afterCallbackEntry; armed = true;
    }

    /** Called only by the excluded constructor mixin after the advancements field assignment. */
    public static void afterAssociation(ServerPlayer actor) {
        if (!matches(actor) || unknownCallback) { return; }
        failAt(actor, "POST_ASSOCIATION_PRE_CALLBACK");
    }

    /** Production's preceding constructor callback seam has already marked unknown escape. */
    public static void callbackEntry(ServerPlayer actor) {
        if (!matches(actor) || !unknownCallback) { return; }
        failAt(actor, "UNKNOWN_CALLBACK_ENTRY");
    }

    private static boolean matches(ServerPlayer actor) {
        return armed && actor != original && actor.getServer() == server
                && actor.getUUID().equals(original.getUUID());
    }

    private static void failAt(ServerPlayer actor, String stage) {
        require(server.isSameThread() && original.isRemoved()
                        && server.getPlayerList().getPlayer(original.getUUID()) == null
                        && actor.getAdvancements() == canonical
                        && ((P11CanonicalAdvancements.Access) canonical).p11$associatedPlayer() == actor,
                "fault did not follow an existing canonical PA association on the real handoff");
        armed = false; fired = true;
        FAULT.stage = stage;
        throw FAULT;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void afterTick(ServerTickEvent.Post event) { observe(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void beforeStop(ServerStoppingEvent event) { observe(event.getServer()); }

    private static void observe(MinecraftServer actual) {
        if (actual != server || !fired || report != null || observationFailure != null) { return; }
        try { report = verify(); }
        catch (RuntimeException failure) { observationFailure = failure; }
    }

    private static String verify() {
        var diagnostic = P11NativeStorageBoundary.diagnostics(server, original.getUUID());
        var receiver = ((P11CanonicalAdvancements.Access) canonical).p11$associatedPlayer();
        require(diagnostic.sourceEpoch() == epoch && original.isRemoved()
                        && server.getPlayerList().getPlayer(original.getUUID()) == null,
                "constructor fault replaced source or revived original actor");
        if (unknownCallback) {
            require(receiver != original && !diagnostic.sourceFault().equals("NONE"),
                    "unknown callback incorrectly authorized association withdrawal");
        } else {
            require(receiver == original && diagnostic.bodyComplete()
                            && !diagnostic.candidatePresent(), "pristine association was not withdrawn");
            var owner = P11NativeStorageBoundary.nativeSourceOwner(original);
            require(owner != null && owner.canSerialize(owner.body(original)),
                    "proven rollback left complete A permanently poisoned");
        }
        require(P11NativeStorageBoundary.observerFailureCount() == 0, "observer failure hid the primary fault");
        return "layer=ACTUAL_NATIVE_CONSTRUCTOR_AND_UNWIND\nstage=" + FAULT.stage
                + "\nwithdrawn=" + (receiver == original) + "\nsource=" + diagnostic
                + "\noriginalActorStillRemoved=true\nfullRewardClaimed=false\n";
    }

    static boolean fired() { return fired; }
    static String report() {
        if (observationFailure != null) { throw observationFailure; }
        require(report != null, "association fault has no observed native unwind");
        return report;
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }

    private static final class AssociationFault extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private String stage;
        private AssociationFault() { super("P11_OWNED_ASSOCIATION_CONSTRUCTOR_FAULT"); }
    }
}
