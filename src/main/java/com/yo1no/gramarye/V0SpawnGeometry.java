package com.yo1no.gramarye;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class V0SpawnGeometry {
    private final ServerLevel level;
    private final Vec3 origin;
    private final int directionXQ15;
    private final int directionYQ15;
    private final int directionZQ15;
    private final int launchStepMilli;

    V0SpawnGeometry(ServerLevel level, Vec3 origin, int directionXQ15, int directionYQ15,
            int directionZQ15, int launchStepMilli) {
        this.level = Objects.requireNonNull(level, "level");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.directionXQ15 = directionXQ15;
        this.directionYQ15 = directionYQ15;
        this.directionZQ15 = directionZQ15;
        this.launchStepMilli = launchStepMilli;
    }

    ServerLevel level() { return level; }
    Vec3 origin() { return origin; }
    int directionXQ15() { return directionXQ15; }
    int directionYQ15() { return directionYQ15; }
    int directionZQ15() { return directionZQ15; }
    int launchStepMilli() { return launchStepMilli; }
}
