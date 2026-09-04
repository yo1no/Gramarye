package com.yo1no.gramarye.magic.network;

import java.util.Objects;

final class P7CooldownDispatchTask implements Runnable {
    private final SkillCooldownSnapshot snapshot;
    private final P7ClientMirrorDispatchPort dispatchPort;

    P7CooldownDispatchTask(
            SkillCooldownSnapshot snapshot, P7ClientMirrorDispatchPort dispatchPort) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.dispatchPort = Objects.requireNonNull(dispatchPort, "dispatchPort");
    }

    @Override
    public void run() {
        dispatchPort.onSkillCooldownSnapshot(snapshot);
    }
}
