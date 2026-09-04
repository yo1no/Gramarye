package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class P7AdvisoryTargetValidator {
    P7ServerAuthorizationBoundary.TargetDisposition validate(
            MinecraftServer server,
            ServerPlayer actor,
            AimHint aimHint,
            EntityHint entityHint) {
        validateAim(aimHint);
        if (entityHint == null) {
            return P7ServerAuthorizationBoundary.TargetDisposition.VALID;
        }

        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(actor, "actor");
        var actorLevel = actor.serverLevel();
        var target = actorLevel.getEntity(entityHint.networkId());
        if (target == null) {
            return P7ServerAuthorizationBoundary.TargetDisposition.TARGET_UNAVAILABLE;
        }
        if (target.getId() != entityHint.networkId()
                || target.level() != actorLevel
                || target.getServer() != server
                || !target.isAddedToLevel()
                || target.isRemoved()
                || !target.isAlive()) {
            return P7ServerAuthorizationBoundary.TargetDisposition.INVALID_TARGET;
        }
        return P7ServerAuthorizationBoundary.TargetDisposition.VALID;
    }

    private static void validateAim(AimHint aimHint) {
        if (aimHint == null) {
            return;
        }
        if (!AimHint.componentsValid(aimHint.x(), aimHint.y(), aimHint.z())) {
            throw new P7SemanticInvariantException("invalid retained aim hint");
        }
        var lengthSquared = (long) aimHint.x() * aimHint.x()
                + (long) aimHint.y() * aimHint.y()
                + (long) aimHint.z() * aimHint.z();
        var inverseLength = 1.0D / StrictMath.sqrt(lengthSquared);
        if (!Double.isFinite(aimHint.x() * inverseLength)
                || !Double.isFinite(aimHint.y() * inverseLength)
                || !Double.isFinite(aimHint.z() * inverseLength)) {
            throw new P7SemanticInvariantException("non-finite normalized aim hint");
        }
    }
}
