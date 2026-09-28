package com.yo1no.gramarye;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** GameTest-only adapter over the existing bounded observation cell, never a recovery invoker. */
public final class P4E2RecoveryGameTestObservation {
    private static final long FINAL_READBACK_CASE = 0x4D3A;

    private P4E2RecoveryGameTestObservation() {}

    public static Handle armFinalReadback(MinecraftServer server, UUID player) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(player, "player");
        if (!server.isSameThread()) {
            throw new IllegalStateException("P4_D3_GAME_TEST_WRONG_THREAD");
        }
        var facade = ModList.get().getModContainerById(Gramarye.MOD_ID).orElseThrow()
                .getCustomExtension(P4E2QualificationFacade.class).orElseThrow();
        return new Handle(facade, facade.arm(server,
                player.getMostSignificantBits(), player.getLeastSignificantBits(),
                FINAL_READBACK_CASE, P4E2QualificationFacade.Phase.READY_RESTART));
    }

    public static void assertFinalReadback(Handle handle) {
        Objects.requireNonNull(handle, "handle");
        var snapshot = handle.facade.consume(handle.session);
        handle.finished = true;
        if (snapshot.caseId() != FINAL_READBACK_CASE
                || snapshot.phase() != P4E2QualificationFacade.Phase.READY_RESTART
                || snapshot.recoveryVariant() != P4E2QualificationFacade.RecoveryVariant.CLEARED
                || snapshot.recoveryDetail() != P4E2QualificationFacade.RecoveryDetail.NONE
                || snapshot.entriesCleared() != 2 || snapshot.stepsReplayed() != 0
                || snapshot.reconciliationVariant()
                        != P4E2QualificationFacade.ReconciliationVariant.RECOVERY_CHANGED
                || snapshot.reconciliationDetail()
                        != P4E2QualificationFacade.ReconciliationDetail.NONE
                || snapshot.continuationCalls() != 1
                || snapshot.setDataAttempts() != 0 || snapshot.setDataSuccesses() != 0) {
            throw new AssertionError("actual final readback did not clear once and hand off without replay");
        }
    }

    public static void discard(Handle handle, Throwable primaryFailure) {
        if (handle == null || handle.finished) { return; }
        try {
            handle.facade.discard(handle.session);
        } catch (RuntimeException cleanupFailure) {
            // Abnormal production completion may already have invalidated this exact cell.
            if (primaryFailure == null) { throw cleanupFailure; }
            if (cleanupFailure != primaryFailure) { primaryFailure.addSuppressed(cleanupFailure); }
        } finally {
            handle.finished = true;
        }
    }

    /** Exact one-shot observation only: no native actor, source capability, or service escapes. */
    public static final class Handle {
        private final P4E2QualificationFacade facade;
        private final P4E2QualificationFacade.Session session;
        private boolean finished;

        private Handle(P4E2QualificationFacade facade, P4E2QualificationFacade.Session session) {
            this.facade = facade;
            this.session = session;
        }
    }
}
