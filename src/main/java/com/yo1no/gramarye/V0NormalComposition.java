package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.Optional;
import net.minecraft.world.level.storage.LevelResource;

/** Ordinary composition captures only the world-owned path; the root starts it after constructing its slot. */
final class V0NormalComposition {
    private static V0NormalColdLane previous;

    private V0NormalComposition() { }

    static Optional<V0SlotBinding> open(V0SlotStartup startup) {
        if (startup == null || !startup.server().isSameThread() || previous != null && !previous.finished()) {
            return Optional.empty();
        }
        var path = startup.server().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                .resolve("gramarye").resolve("spell_catalog");
        var catalog = V0RuntimeCatalog.openNormal(startup.authority(), path);
        var mailbox = new V0ColdMailbox(startup.authority());
        var cold = new V0NormalColdLane(startup.authority(), mailbox, catalog);
        var binding = V0SlotBinding.createNormal(catalog, mailbox, cold);
        return Optional.of(binding);
    }

    static void claimStartup(V0NormalColdLane cold) {
        if (previous != null && !previous.finished()) {
            throw new IllegalStateException("Previous normal catalog owner has not finished");
        }
        // Own even a start() failure until abort proves NEW or the original worker retires.
        previous = cold;
    }

    static void releaseUnstarted(V0NormalColdLane cold) {
        if (previous == cold) { previous = null; }
    }
}
