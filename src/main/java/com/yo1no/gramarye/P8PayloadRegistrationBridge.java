package com.yo1no.gramarye;

import java.util.Objects;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registration-only bridge from the unique P7 event subscriber to P8 codecs. */
public final class P8PayloadRegistrationBridge {
    private static boolean registered;

    private P8PayloadRegistrationBridge() {
        throw new AssertionError("no instances");
    }

    public static synchronized void register(PayloadRegistrar registrar) {
        Objects.requireNonNull(registrar, "registrar");
        if (registered) {
            throw new IllegalStateException("P8 payloads are already registered");
        }
        registered = true;
        registrar.playToClient(
                ProfileCatalogPayload.TYPE,
                ProfileCatalogPayload.STREAM_CODEC,
                P8ClientPayloadHandlers::handleProfileCatalog);
        registrar.playToClient(
                PresentationEventPayload.TYPE,
                PresentationEventPayload.STREAM_CODEC,
                P8ClientPayloadHandlers::handlePresentationEvent);
    }
}
