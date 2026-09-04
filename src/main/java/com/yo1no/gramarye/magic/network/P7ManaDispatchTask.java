package com.yo1no.gramarye.magic.network;

import java.util.Objects;

final class P7ManaDispatchTask implements Runnable {
    private final PlayerManaSnapshot snapshot;
    private final P7ClientMirrorDispatchPort dispatchPort;

    P7ManaDispatchTask(
            PlayerManaSnapshot snapshot, P7ClientMirrorDispatchPort dispatchPort) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.dispatchPort = Objects.requireNonNull(dispatchPort, "dispatchPort");
    }

    @Override
    public void run() {
        dispatchPort.onPlayerManaSnapshot(snapshot);
    }
}
