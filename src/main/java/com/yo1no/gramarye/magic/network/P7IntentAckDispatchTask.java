package com.yo1no.gramarye.magic.network;

import java.util.Objects;

final class P7IntentAckDispatchTask implements Runnable {
    private final IntentAcknowledgement acknowledgement;
    private final P7ClientMirrorDispatchPort dispatchPort;

    P7IntentAckDispatchTask(
            IntentAcknowledgement acknowledgement,
            P7ClientMirrorDispatchPort dispatchPort) {
        this.acknowledgement = Objects.requireNonNull(
                acknowledgement, "acknowledgement");
        this.dispatchPort = Objects.requireNonNull(dispatchPort, "dispatchPort");
    }

    @Override
    public void run() {
        dispatchPort.onIntentAcknowledgement(acknowledgement);
    }
}
