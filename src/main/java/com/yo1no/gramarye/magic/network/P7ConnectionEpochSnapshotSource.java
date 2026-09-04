package com.yo1no.gramarye.magic.network;

import java.util.OptionalLong;
import java.util.UUID;

@FunctionalInterface
interface P7ConnectionEpochSnapshotSource {
    OptionalLong currentEpoch(UUID authenticatedPlayerId);
}
