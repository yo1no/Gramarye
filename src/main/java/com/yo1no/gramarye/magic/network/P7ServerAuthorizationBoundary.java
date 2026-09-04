package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.P6RuntimeExecutionCapability;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class P7ServerAuthorizationBoundary {
    private static final RootIngressPort UNAVAILABLE_ROOT_INGRESS =
            (server, actor, slot, targetCheck) -> AdmissionDisposition.P5_UNAVAILABLE;

    private static volatile RootIngressPort installedRootIngress = UNAVAILABLE_ROOT_INGRESS;

    private P7ServerAuthorizationBoundary() {}

    public static void install(
            P6RuntimeExecutionCapability capability, RootIngressPort rootIngressPort) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(rootIngressPort, "rootIngressPort");
        synchronized (P7ServerAuthorizationBoundary.class) {
            if (installedRootIngress != UNAVAILABLE_ROOT_INGRESS) {
                throw new P7SemanticInvariantException(
                        "root ingress boundary is already installed");
            }
            installedRootIngress = rootIngressPort;
        }
    }

    static AdmissionDisposition dispatch(
            MinecraftServer server,
            ServerPlayer actor,
            int slot,
            AdvisoryTargetCheck targetCheck) {
        var rootIngressPort = installedRootIngress;
        return rootIngressPort.authorizeAndAdmit(server, actor, slot, targetCheck);
    }

    @FunctionalInterface
    public interface RootIngressPort {
        AdmissionDisposition authorizeAndAdmit(
                MinecraftServer server,
                ServerPlayer actor,
                int slot,
                AdvisoryTargetCheck targetCheck);
    }

    @FunctionalInterface
    public interface AdvisoryTargetCheck {
        TargetDisposition validate(MinecraftServer server, ServerPlayer actor);
    }

    public enum AdmissionDisposition {
        ACCEPTED,
        UNKNOWN_SKILL,
        UNAUTHORIZED_INTENT,
        INVALID_TARGET,
        TARGET_UNAVAILABLE,
        P5_ADMISSION_REJECTED,
        P5_UNAVAILABLE,
        INTERNAL_SERVER_FAULT
    }

    public enum TargetDisposition {
        VALID,
        INVALID_TARGET,
        TARGET_UNAVAILABLE
    }
}
