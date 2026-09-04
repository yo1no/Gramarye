package com.yo1no.gramarye.magic.network;

import java.util.Objects;

final class P7ServerDispatchTask implements Runnable {
    private final P7QueuedCastIntent queuedIntent;
    private final P7ServerIntentDispatchPort dispatchPort;
    private final P7PendingPermit permit;

    P7ServerDispatchTask(
            P7QueuedCastIntent queuedIntent,
            P7ServerIntentDispatchPort dispatchPort,
            P7PendingPermit permit) {
        this.queuedIntent = Objects.requireNonNull(queuedIntent, "queuedIntent");
        this.dispatchPort = Objects.requireNonNull(dispatchPort, "dispatchPort");
        this.permit = Objects.requireNonNull(permit, "permit");
    }

    @Override
    public void run() {
        try {
            dispatchPort.dispatch(queuedIntent);
        } finally {
            permit.release();
        }
    }
}
