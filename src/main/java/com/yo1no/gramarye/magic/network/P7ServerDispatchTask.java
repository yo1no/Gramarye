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
        var identity = queuedIntent.sessionIdentity();
        if (!identity.authenticatedPlayerId().equals(permit.authenticatedPlayerId())
                || identity.connectionEpoch() != permit.connectionEpoch()
                || identity.serverGeneration() != permit.serverGeneration()) {
            throw new P7SemanticInvariantException("queued intent and permit identity differ");
        }
    }

    @Override
    public void run() {
        if (!permit.tryStartTask()) {
            return;
        }
        Throwable primary = null;
        try {
            dispatchPort.dispatch(queuedIntent);
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (primary == null) {
                permit.releaseAfterTask();
            } else {
                try {
                    permit.releaseAfterTask();
                } catch (RuntimeException | Error secondary) {
                    suppress(primary, secondary);
                }
            }
        }
    }

    private static void suppress(Throwable primary, Throwable secondary) {
        if (primary != secondary) {
            try {
                primary.addSuppressed(secondary);
            } catch (RuntimeException | Error suppressionFailure) {
                // Even failed diagnostic suppression cannot replace the original primary.
            }
        }
    }
}
