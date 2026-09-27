package com.yo1no.gramarye;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Root-held startup foundation only. There are no packet, tick, writer or native producers. */
final class P11FoundationService {
    private MinecraftServer server;
    private P11StartupLoadState startupState = P11StartupLoadState.Unavailable.INSTANCE;
    private P11FoundationSlot slot;

    void started(ServerStartedEvent event, P11StartupLoadState snapshot) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(snapshot, "snapshot");
        var exact = event.getServer();
        if (!exact.isSameThread()) {
            throw new IllegalStateException("P11_START_WRONG_THREAD");
        }
        if (server != null) {
            throw new IllegalStateException("P11_SLOT_ALREADY_OBSERVED");
        }
        server = exact;
        startupState = snapshot;
        if (snapshot instanceof P11StartupLoadState.Ready ready) {
            slot = new P11FoundationSlot(ready.limits(),
                    new P11IdentityOwner(exact, ready.limits().maxUuids()));
        }
        // Invalid/missing P11 configuration is visible but does not break existing P5 gameplay.
        // Foundation existence is not a qualified source, writer, live control or RUNNING grant.
    }

    Optional<P11StartupLoadState> startupState(MinecraftServer exact) {
        return server != null && server == exact ? Optional.of(startupState) : Optional.empty();
    }

    void stopping(ServerStoppingEvent event) { stopExact(event.getServer()); }
    void stopped(ServerStoppedEvent event) { stopExact(event.getServer()); }

    private void stopExact(MinecraftServer exact) {
        if (server != exact) { return; }
        if (!exact.isSameThread()) {
            throw new IllegalStateException("P11_STOP_WRONG_THREAD");
        }
        if (slot != null) { slot.retire(); }
        slot = null;
        server = null;
        startupState = P11StartupLoadState.Unavailable.INSTANCE;
    }
}

/** The same limits/owners used by the live root can be exercised in an isolated identity domain. */
final class P11FoundationSlot {
    private final P11StartupLimits limits;
    private final P11IdentityOwner identities;
    private final P11ReceiptLedger receipts;
    private final P11ControlBudgets.Resources resources;
    private boolean retired;

    P11FoundationSlot(P11StartupLimits limits, P11IdentityOwner identities) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.identities = Objects.requireNonNull(identities, "identities");
        receipts = new P11ReceiptLedger(identities);
        resources = new P11ControlBudgets.Resources(limits);
    }

    P11StartupLimits limits() { return limits; }
    boolean retired() { return retired; }

    void retire() {
        if (retired) { return; }
        retired = true;
        resources.retireSlot();
        receipts.stop();
        identities.stop();
    }
}
