package com.yo1no.gramarye;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** NETWORK-thread handlers retaining no platform context after one bounded handoff. */
final class P8ClientPayloadHandlers {
    private static final Component INVALID_PAYLOAD = Component.translatable(
            "disconnect.gramarye.invalid_presentation_payload");
    private static final P8ClientPayloadDispatchPort PRODUCTION =
            P8ClientPayloadDispatchFactory.production();

    private P8ClientPayloadHandlers() {
        throw new AssertionError("no instances");
    }

    static void handleProfileCatalog(
            ProfileCatalogPayload payload, IPayloadContext context) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        dispatch(context, () -> PRODUCTION.prepareProfileCatalog(payload));
    }

    static void handlePresentationEvent(
            PresentationEventPayload payload, IPayloadContext context) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        dispatch(context, () -> PRODUCTION.preparePresentationEvent(payload));
    }

    private static void dispatch(
            IPayloadContext context,
            Supplier<Optional<P8ClientDispatchTask>> preparation) {
        final Optional<P8ClientDispatchTask> prepared;
        try {
            prepared = Objects.requireNonNull(
                    preparation.get(), "P8 client dispatch preparation");
        } catch (P8ClientDispatchUnavailableException unavailable) {
            context.disconnect(INVALID_PAYLOAD);
            return;
        }
        if (prepared.isEmpty()) {
            return;
        }

        var task = prepared.orElseThrow();
        var transferred = false;
        try {
            context.enqueueWork(task);
            transferred = true;
        } finally {
            if (!transferred) {
                task.releaseAfterFailedEnqueue();
            }
        }
    }
}
