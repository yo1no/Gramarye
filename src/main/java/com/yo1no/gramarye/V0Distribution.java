package com.yo1no.gramarye;

import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/** Ordinary normal composition has no engineering issuer or recipe. */
final class V0Distribution {
    private V0Distribution() { }

    static V0StartupProfile profile(MinecraftServer server, V0StartupLoadState engineeringState) {
        return V0StartupProfile.normal();
    }

    static Optional<V0SlotBinding> open(V0SlotStartup startup) {
        return startup.profile() == V0StartupProfile.Kind.NORMAL
                ? V0NormalComposition.open(startup) : Optional.empty();
    }
}
