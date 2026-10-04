package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.P11L1PacketProbe;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Excluded typed view of the actual ACK; no payload/session creation or mutation. */
public final class P11L1AckObservation {
    private P11L1AckObservation() {}
    public static boolean policyEntered(Object value) {
        if (!(value instanceof P7ServerIntentResult result) || !result.p5AdmissionAccepted()
                || result.acknowledgementCandidate().isEmpty()) { return false; }
        var acknowledgement = result.acknowledgementCandidate().orElseThrow();
        return acknowledgement.disposition() == IntentAcknowledgement.Disposition.ACCEPTED
                && P11L1PacketProbe.ackPolicyEntered(result.sessionIdentity().authenticatedPlayerId(),
                        acknowledgement.sequence());
    }
    public static void beforeSend(ServerPlayer actor, CustomPacketPayload value) {
        if (value instanceof IntentAckPayload payload
                && payload.acknowledgement().disposition() == IntentAcknowledgement.Disposition.ACCEPTED) {
            P11L1PacketProbe.beforeAcceptedAck(actor, payload.acknowledgement().sequence());
        }
    }
}
