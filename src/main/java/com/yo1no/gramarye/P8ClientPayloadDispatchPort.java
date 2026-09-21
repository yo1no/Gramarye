package com.yo1no.gramarye;

import java.util.Optional;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** Common-safe bounded handoff from NETWORK decode to the client presentation owner. */
interface P8ClientPayloadDispatchPort {
    Optional<P8ClientDispatchTask> prepareProfileCatalog(
            Connection sourceConnection,
            ICommonPacketListener sourcePlayListener,
            ProfileCatalogPayload payload);

    Optional<P8ClientDispatchTask> preparePresentationEvent(
            Connection sourceConnection,
            ICommonPacketListener sourcePlayListener,
            PresentationEventPayload payload);
}
