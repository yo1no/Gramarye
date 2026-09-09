package com.yo1no.gramarye;

import java.util.Optional;

/** Common-safe bounded handoff from NETWORK decode to the client presentation owner. */
interface P8ClientPayloadDispatchPort {
    Optional<P8ClientDispatchTask> prepareProfileCatalog(ProfileCatalogPayload payload);

    Optional<P8ClientDispatchTask> preparePresentationEvent(
            PresentationEventPayload payload);
}
