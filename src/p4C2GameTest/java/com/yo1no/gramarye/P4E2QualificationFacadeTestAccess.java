package com.yo1no.gramarye;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** Same-package GameTest adapter for the exact per-container qualification extension. */
public final class P4E2QualificationFacadeTestAccess {
    private static final long P4_C2_READY_CASE_ID = 0x4C2L;

    private P4E2QualificationFacadeTestAccess() {
    }

    /** Retrieves the registered facade and arms exactly one READY login session. */
    public static Handle armReady(
            MinecraftServer server, UUID playerUuid, boolean restart) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerUuid, "playerUuid");
        var facade = retrieveExactFacade();
        var facadePhase = restart
                ? P4E2QualificationFacade.Phase.READY_RESTART
                : P4E2QualificationFacade.Phase.READY_FIRST;
        var observationPhase = restart
                ? P4E2QualificationObservation.Phase.RESTART
                : P4E2QualificationObservation.Phase.FIRST;
        var handle = new Handle(facade, playerUuid, facadePhase, observationPhase);
        handle.session = null;
        var session = facade.arm(
                server,
                playerUuid.getMostSignificantBits(),
                playerUuid.getLeastSignificantBits(),
                P4_C2_READY_CASE_ID,
                facadePhase);
        handle.session = session;
        return handle;
    }

    /** Consumes the exact completed cell and projects only its bounded direct facts. */
    public static P4E2QualificationObservation consumeReady(Handle handle) {
        Objects.requireNonNull(handle, "handle");
        if (handle.finished) {
            throw new IllegalStateException("qualification handle was already consumed");
        }
        var facade = retrieveExactFacade();
        requireIdentity(handle, facade);
        var snapshot = facade.consume(handle.session);
        handle.finished = true;
        if (snapshot.caseId() != P4_C2_READY_CASE_ID
                || snapshot.phase() != handle.facadePhase) {
            throw new AssertionError("completed qualification identity differs from its arm");
        }
        if (snapshot.recoveryVariant()
                        == P4E2QualificationFacade.RecoveryVariant.NO_PENDING
                && snapshot.recoveryDetail()
                        != P4E2QualificationFacade.RecoveryDetail.NONE) {
            throw new AssertionError("NoPending retained a nonempty recovery detail");
        }
        if (snapshot.reconciliationVariant()
                        == P4E2QualificationFacade.ReconciliationVariant.NO_CHANGES
                && snapshot.reconciliationDetail()
                        != P4E2QualificationFacade.ReconciliationDetail.NONE) {
            throw new AssertionError("NoChanges retained a nonempty E2 detail");
        }
        return new P4E2QualificationObservation(
                P4E2QualificationObservation.SCHEMA_VERSION,
                P4E2QualificationObservation.CASE_ID,
                handle.observationPhase,
                handle.playerUuid,
                // A consumable Snapshot is formed only after the facade's mandatory
                // single recovery coordinate completed in this same direct cell.
                1,
                recoveryOutcome(snapshot.recoveryVariant()),
                snapshot.entriesCleared(),
                snapshot.stepsReplayed(),
                // These are the actual handler-local counts retained by the same cell;
                // this does not infer from Attachment or Store state.
                snapshot.entriesCleared() > 0 || snapshot.stepsReplayed() > 0,
                snapshot.continuationCalls(),
                e2Result(snapshot.reconciliationVariant()),
                snapshot.invalidationAttempts(),
                snapshot.invalidationAccepted(),
                snapshot.acceptedGenerationPresent(),
                snapshot.setDataAttempts(),
                snapshot.setDataSuccesses(),
                P4E2QualificationObservation.COMPLETION_MARKER);
    }

    /** Clears an active or partial session; a successfully consumed handle is already clear. */
    public static void discard(Handle handle) {
        if (handle == null || handle.finished) {
            return;
        }
        var facade = retrieveExactFacade();
        requireIdentity(handle, facade);
        facade.discard(handle.session);
        handle.finished = true;
    }

    /**
     * Keeps an already propagating login failure primary while attempting strict cleanup once.
     * Normal exits still use {@link #discard(Handle)} without suppressing cleanup failures.
     */
    public static void discardPreservingPrimary(Handle handle, Throwable primaryFailure) {
        if (primaryFailure == null) {
            discard(handle);
            return;
        }
        try {
            discard(handle);
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // Production abnormal completion may already have invalidated this exact session.
            // Do not retry, allocate diagnostics, or replace the original throwable.
            if (handle != null) {
                handle.finished = true;
            }
        }
    }

    private static P4E2QualificationFacade retrieveExactFacade() {
        var first = retrieveFacade();
        var second = retrieveFacade();
        if (first != second) {
            throw new AssertionError("per-container qualification facade identity changed");
        }
        return first;
    }

    private static P4E2QualificationFacade retrieveFacade() {
        var container = ModList.get()
                .getModContainerById(Gramarye.MOD_ID)
                .orElseThrow(() -> new AssertionError("Gramarye mod container is absent"));
        return container.getCustomExtension(P4E2QualificationFacade.class)
                .orElseThrow(() -> new AssertionError(
                        "Gramarye qualification facade is absent"));
    }

    private static void requireIdentity(
            Handle handle, P4E2QualificationFacade facade) {
        if (handle.facade != facade) {
            throw new AssertionError("qualification operation reached a different facade");
        }
    }

    private static P4E2QualificationObservation.RecoveryOutcome recoveryOutcome(
            P4E2QualificationFacade.RecoveryVariant variant) {
        return switch (variant) {
            case NO_PENDING -> P4E2QualificationObservation.RecoveryOutcome.NO_PENDING;
            case CLEARED -> P4E2QualificationObservation.RecoveryOutcome.CLEARED;
            case REPLAYED -> P4E2QualificationObservation.RecoveryOutcome.REPLAYED;
            case CLEARED_AND_REPLAYED ->
                    P4E2QualificationObservation.RecoveryOutcome.CLEARED_AND_REPLAYED;
            case CONFLICT -> P4E2QualificationObservation.RecoveryOutcome.CONFLICT;
            case TARGET_INVALID ->
                    P4E2QualificationObservation.RecoveryOutcome.TARGET_INVALID;
            case UNAVAILABLE -> P4E2QualificationObservation.RecoveryOutcome.UNAVAILABLE;
        };
    }

    private static P4E2QualificationObservation.E2ResultVariant e2Result(
            P4E2QualificationFacade.ReconciliationVariant variant) {
        return switch (variant) {
            case NO_CHANGES -> P4E2QualificationObservation.E2ResultVariant.NO_CHANGES;
            case RECOVERY_CHANGED ->
                    P4E2QualificationObservation.E2ResultVariant.RECOVERY_CHANGED;
            case CHANGED -> P4E2QualificationObservation.E2ResultVariant.CHANGED;
            case DEFERRED -> P4E2QualificationObservation.E2ResultVariant.DEFERRED;
            case FAILED -> P4E2QualificationObservation.E2ResultVariant.FAILED;
            case GENERATION_EXHAUSTED ->
                    P4E2QualificationObservation.E2ResultVariant.GENERATION_EXHAUSTED;
        };
    }

    /** Opaque, single-use GameTest handle. No production service is exposed. */
    public static final class Handle {
        private final P4E2QualificationFacade facade;
        private P4E2QualificationFacade.Session session;
        private final UUID playerUuid;
        private final P4E2QualificationFacade.Phase facadePhase;
        private final P4E2QualificationObservation.Phase observationPhase;
        private boolean finished;

        private Handle(
                P4E2QualificationFacade facade,
                UUID playerUuid,
                P4E2QualificationFacade.Phase facadePhase,
                P4E2QualificationObservation.Phase observationPhase) {
            this.facade = facade;
            this.playerUuid = playerUuid;
            this.facadePhase = facadePhase;
            this.observationPhase = observationPhase;
        }
    }
}
