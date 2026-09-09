package com.yo1no.gramarye;

import java.util.Objects;
import java.util.Optional;

/** Write-once common forwarding boundary installed only by the client entrypoint. */
final class P8ClientPayloadDispatchFactory {
    private static final P8ClientPayloadDispatchPort DISCONNECTED =
            new DisconnectedPort();
    private static final ForwardingPort PRODUCTION = new ForwardingPort();

    private P8ClientPayloadDispatchFactory() {
        throw new AssertionError("no instances");
    }

    static P8ClientPayloadDispatchPort production() {
        return PRODUCTION;
    }

    static void installClient(P8ClientPayloadDispatchPort clientPort) {
        PRODUCTION.install(clientPort);
    }

    private static final class ForwardingPort implements P8ClientPayloadDispatchPort {
        private P8ClientPayloadDispatchPort delegate = DISCONNECTED;

        private synchronized void install(P8ClientPayloadDispatchPort clientPort) {
            Objects.requireNonNull(clientPort, "clientPort");
            if (delegate != DISCONNECTED) {
                throw new IllegalStateException(
                        "P8 client payload dispatch port is already installed");
            }
            delegate = clientPort;
        }

        @Override
        public synchronized Optional<P8ClientDispatchTask> prepareProfileCatalog(
                ProfileCatalogPayload payload) {
            return delegate.prepareProfileCatalog(payload);
        }

        @Override
        public synchronized Optional<P8ClientDispatchTask> preparePresentationEvent(
                PresentationEventPayload payload) {
            return delegate.preparePresentationEvent(payload);
        }
    }

    private static final class DisconnectedPort implements P8ClientPayloadDispatchPort {
        @Override
        public Optional<P8ClientDispatchTask> prepareProfileCatalog(
                ProfileCatalogPayload payload) {
            Objects.requireNonNull(payload, "payload");
            throw new P8ClientDispatchUnavailableException();
        }

        @Override
        public Optional<P8ClientDispatchTask> preparePresentationEvent(
                PresentationEventPayload payload) {
            Objects.requireNonNull(payload, "payload");
            throw new P8ClientDispatchUnavailableException();
        }
    }
}

final class P8ClientDispatchUnavailableException extends RuntimeException {
    P8ClientDispatchUnavailableException() {
        super(null, null, false, false);
    }
}
