package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.P11D3ClientHarness;
import com.yo1no.gramarye.P11D3ServerHarness;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only actual sender/mirror observation. The sole replay is the identical captured
 * immutable payload through the original PacketDistributor path, never a new intent. */
public final class P11D3ClientObservation {
    private static CastIntentPayload sent;
    private static Connection source;
    private static boolean replayed;
    private P11D3ClientObservation() { }
    public static void submitted(Object actual) {
        if (!P11D3ServerHarness.selected()) return;
        P11D3ServerHarness.require(actual instanceof CastIntentPayload, "CLIENT_ACTUAL_PAYLOAD");
        var payload = (CastIntentPayload) actual;
        var minecraft = Minecraft.getInstance();
        P11D3ServerHarness.require(minecraft.isSameThread() && minecraft.getConnection() != null, "CLIENT_ORIGINAL_SEND_THREAD");
        if (sent == null) { sent = payload; source = minecraft.getConnection().getConnection(); }
        var value = payload.intent();
        P11D3ClientHarness.submitted(value.sequence(), value.slot(), value.presenceMask(),
                value.aimHint().isEmpty() && value.entityHint().isEmpty());
    }
    public static void replay() {
        var minecraft = Minecraft.getInstance();
        P11D3ServerHarness.require(minecraft.isSameThread() && sent != null && !replayed
                && minecraft.getConnection() != null && minecraft.getConnection().getConnection() == source
                && source.isConnected(), "CLIENT_EXACT_SAME_SESSION_REPLAY");
        replayed = true; PacketDistributor.sendToServer(sent);
    }
    public static boolean ready() {
        var mirror = P7ClientLifecycleEvents.mirror();
        return mirror.cooldownSnapshot().map(snapshot -> snapshot.sourceState() == P7ServerAuthorizationBoundary.SyncSourceState.AVAILABLE
                && snapshot.entries().stream().anyMatch(entry -> entry.slot() == 0
                        && entry.state() == P7ServerAuthorizationBoundary.SyncEntryState.READY)).orElse(false);
    }
    public static void acknowledgement(Object actualMirror, long generation, Object actualAck) {
        if (!P11D3ServerHarness.selected() || !(actualMirror instanceof P7ClientMirror mirror)
                || !(actualAck instanceof IntentAcknowledgement ack)) return;
        if (generation == mirror.currentDispatchGeneration() && mirror.lastAcknowledgement().orElse(null) == ack)
            P11D3ClientHarness.acknowledged(ack.sequence(), ack.disposition().name());
    }
    public static void clear() { sent = null; source = null; replayed = false; }
}
