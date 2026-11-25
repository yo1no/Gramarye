package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.P6RuntimeExecutionCapability;
import com.yo1no.gramarye.V0CatalogAuthority;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataContinuation;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class P7ServerAuthorizationBoundary {
    private static final RootIngressPort UNAVAILABLE_ROOT_INGRESS =
            (server, actor, slot, targetCheck) -> AdmissionDisposition.P5_UNAVAILABLE;
    private static final LoginReadyPort LOGIN_READY_PORT =
            (server, actor) -> P7NetworkComposition.onLoginReady(server, actor);

    private static volatile RootIngressPort installedRootIngress = UNAVAILABLE_ROOT_INGRESS;
    private static final SyncProjection UNAVAILABLE_PROJECTION = new SyncProjection(
            0, 0, SyncSourceState.UNAVAILABLE, SyncReason.SOURCE_UNAVAILABLE, List.of());
    private static final SyncProjectionPort UNAVAILABLE_SYNC = (server, actor) -> new SyncCapture() {
        @Override public SyncProjection projection() { return UNAVAILABLE_PROJECTION; }
        @Override public boolean isCurrent() { return true; }
    };
    private static volatile SyncProjectionPort installedSyncProjection = UNAVAILABLE_SYNC;
    private static final NormalRootIngressPort UNAVAILABLE_NORMAL =
            (server, actor, receipt) -> NormalIngressDisposition.UNAVAILABLE;
    private static volatile NormalRootIngressPort installedNormalIngress = UNAVAILABLE_NORMAL;

    private P7ServerAuthorizationBoundary() {}

    public static void install(
            P6RuntimeExecutionCapability capability, RootIngressPort rootIngressPort) {
        install(capability, rootIngressPort, UNAVAILABLE_SYNC);
    }

    public static void install(P6RuntimeExecutionCapability capability, RootIngressPort rootIngressPort,
            SyncProjectionPort syncProjectionPort) {
        install(capability, rootIngressPort, syncProjectionPort, UNAVAILABLE_NORMAL);
    }

    public static void install(P6RuntimeExecutionCapability capability, RootIngressPort rootIngressPort,
            SyncProjectionPort syncProjectionPort, NormalRootIngressPort normalIngressPort) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(rootIngressPort, "rootIngressPort");
        Objects.requireNonNull(syncProjectionPort, "syncProjectionPort");
        Objects.requireNonNull(normalIngressPort, "normalIngressPort");
        synchronized (P7ServerAuthorizationBoundary.class) {
            if (installedRootIngress != UNAVAILABLE_ROOT_INGRESS) {
                throw new P7SemanticInvariantException(
                        "root ingress boundary is already installed");
            }
            P7NetworkComposition.bindManaCapability(capability);
            installedSyncProjection = syncProjectionPort;
            installedNormalIngress = normalIngressPort;
            installedRootIngress = rootIngressPort;
        }
    }

    public static LoginReadyPort loginReadyPort(P6RuntimeExecutionCapability capability) {
        Objects.requireNonNull(capability, "capability");
        return LOGIN_READY_PORT;
    }

    public static Optional<V0PlayWitness> captureV0Play(
            V0CatalogAuthority authority, MinecraftServer server, ServerPlayer actor) {
        if (authority == null || !authority.isOwnerThread()) {
            return Optional.empty();
        }
        var identity = P7NetworkComposition.currentV0PlayIdentity(server, actor);
        if (identity.isEmpty()) {
            return Optional.empty();
        }
        var witness = new V0PlayWitness();
        witness.authority = authority;
        witness.identity = identity.orElseThrow();
        return Optional.of(witness);
    }

    public static boolean currentV0Play(V0CatalogAuthority authority, V0PlayWitness witness,
            MinecraftServer server, ServerPlayer actor) {
        return authority != null && witness != null && witness.authority == authority && authority.isOwnerThread()
                && P7NetworkComposition.currentV0PlayIdentity(server, actor).orElse(null) == witness.identity;
    }

    public static final class V0PlayWitness {
        // Only captureV0Play sets these, before publication on the owner thread.
        private V0CatalogAuthority authority;
        private P7SessionIdentity identity;

        private V0PlayWitness() {}
    }

    static AdmissionDisposition dispatch(
            MinecraftServer server,
            ServerPlayer actor,
            int slot,
            AdvisoryTargetCheck targetCheck) {
        var rootIngressPort = installedRootIngress;
        return rootIngressPort.authorizeAndAdmit(server, actor, slot, targetCheck);
    }

    static NormalReceipt normalReceipt(P7SessionIdentity identity, long sequence) {
        return new NormalReceipt(identity, sequence);
    }

    static NormalIngressDisposition dispatchNormal(MinecraftServer server, ServerPlayer actor,
            NormalReceipt receipt) {
        return Objects.requireNonNull(installedNormalIngress.authorizeAndAdmit(server, actor, receipt),
                "normal ingress result");
    }

    /** Fixed normal operation, separate from the legacy equipped-SkillReference branch. */
    @FunctionalInterface
    public interface NormalRootIngressPort {
        NormalIngressDisposition authorizeAndAdmit(
                MinecraftServer server, ServerPlayer actor, NormalReceipt receipt);
    }

    public enum NormalIngressDisposition {
        PENDING, NOT_READY, CAPACITY, COOLDOWN, INSUFFICIENT_MANA, UNAVAILABLE, REJECTED
    }

    public enum NormalFeedback {
        NOT_READY, CAPACITY, COOLDOWN_ACTIVE, INSUFFICIENT_MANA, RATE_LIMITED, UNAVAILABLE
    }

    /** Scalar result join for the single bounded P5 capture; not a queued ACK or retry. */
    public static final class NormalReceipt {
        private final P7SessionIdentity identity;
        private final long sequence;
        private boolean deferred;
        private boolean completed;

        private NormalReceipt(P7SessionIdentity identity, long sequence) {
            this.identity = Objects.requireNonNull(identity, "identity");
            this.sequence = sequence;
        }

        public boolean defer() {
            if (deferred || completed) { return false; }
            deferred = true;
            return true;
        }

        public boolean matches(V0CatalogAuthority authority, V0PlayWitness witness) {
            return !deferred && !completed && authority != null && witness != null
                    && authority.isOwnerThread() && witness.authority == authority && witness.identity == identity;
        }

        boolean deferred() { return deferred && !completed; }

        void immediateCompleted() { completed = true; }

        public void complete(MinecraftServer server, ServerPlayer exactActor,
                AdmissionDisposition disposition, NormalFeedback feedback) {
            Objects.requireNonNull(disposition, "disposition");
            if (!deferred || completed) { return; }
            completed = true;
            P7NetworkComposition.completeNormal(
                    server, exactActor, identity, sequence, disposition, feedback);
        }
    }

    static SyncCapture prepareSync(MinecraftServer server, ServerPlayer actor) {
        return Objects.requireNonNull(installedSyncProjection.prepareAndCapture(server, actor), "sync capture");
    }

    /** Root-injected projection only: no cast, mutation, actor lookup, or session authority. */
    @FunctionalInterface
    public interface SyncProjectionPort {
        SyncCapture prepareAndCapture(MinecraftServer server, ServerPlayer actor);
    }

    /** Call-local source receipt; never placed in a payload, task, or retained reconciliation queue. */
    public interface SyncCapture {
        SyncProjection projection();
        boolean isCurrent();
    }

    public record SyncEntry(int slot, SkillReference reference, SyncEntryState state,
            SyncReason reason, int remainingTicks) {
        public SyncEntry {
            CooldownSnapshotEntry.validate(slot, reference, state, reason, remainingTicks);
        }
    }

    public record SyncProjection(long sourceEpoch, long sourceVersion, SyncSourceState sourceState,
            SyncReason sourceReason, List<SyncEntry> entries) {
        public SyncProjection {
            Objects.requireNonNull(entries, "entries");
            if (entries.size() > P7NetworkBounds.MAX_SYNC_ENTRIES_PER_PACKET) {
                throw new P7SemanticInvariantException("cooldown projection entry count exceeds its bound");
            }
            entries = List.copyOf(entries);
            new SkillCooldownSnapshot(1, sourceEpoch, sourceVersion, sourceState, sourceReason,
                    entries.stream().map(entry -> new CooldownSnapshotEntry(entry.slot(), entry.reference(),
                            entry.state(), entry.reason(), entry.remainingTicks())).toList());
        }

        SkillCooldownSnapshot snapshot(long sequence) {
            return new SkillCooldownSnapshot(sequence, sourceEpoch, sourceVersion, sourceState, sourceReason,
                    entries.stream().map(entry -> new CooldownSnapshotEntry(entry.slot(), entry.reference(),
                            entry.state(), entry.reason(), entry.remainingTicks())).toList());
        }
    }

    public enum SyncSourceState {
        AVAILABLE(0), PARTIAL(1), UNAVAILABLE(2);
        private final int wireCode;
        SyncSourceState(int wireCode) { this.wireCode = wireCode; }
        int wireCode() { return wireCode; }
        static SyncSourceState fromWireCode(int code) {
            for (var value : values()) { if (value.wireCode == code) { return value; } }
            throw new P7SemanticInvariantException("unknown cooldown source state");
        }
    }

    public enum SyncEntryState {
        READY(0), ACTIVE(1), PENDING(2), RECOVERY_REQUIRED(3), UNAVAILABLE(4);
        private final int wireCode;
        SyncEntryState(int wireCode) { this.wireCode = wireCode; }
        int wireCode() { return wireCode; }
        static SyncEntryState fromWireCode(int code) {
            for (var value : values()) { if (value.wireCode == code) { return value; } }
            throw new P7SemanticInvariantException("unknown cooldown entry state");
        }
    }

    public enum SyncReason {
        NONE(0), PENDING_RELEASE(1), NOT_READY(2), MALFORMED(3), UNSUPPORTED_VERSION(4), BOUNDS(5),
        CLOCK(6), PROVIDER(7), SAVE_FAILED(8), OUTCOME_UNCERTAIN(9), RECOVERY_REQUIRED(10),
        EQUIPMENT_UNKNOWN(11), SOURCE_UNAVAILABLE(12);
        private final int wireCode;
        SyncReason(int wireCode) { this.wireCode = wireCode; }
        int wireCode() { return wireCode; }
        static SyncReason fromWireCode(int code) {
            for (var value : values()) { if (value.wireCode == code) { return value; } }
            throw new P7SemanticInvariantException("unknown cooldown reason");
        }
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

    @FunctionalInterface
    public interface LoginReadyPort {
        void onLoginReady(MinecraftServer server, ServerPlayer actor);

        /** Same sole port, with the root-bound receipt from the original recovery owner. */
        default void onLoginReady(MinecraftServer server, ServerPlayer actor, MetadataContinuation receipt) {
            Objects.requireNonNull(receipt, "metadata receipt");
            if (this == LOGIN_READY_PORT) {
                P7NetworkComposition.onLoginReady(server, actor, receipt, this);
            } else {
                receipt.legacyLoginStarted(this);
                onLoginReady(server, actor);
            }
        }
    }
}
