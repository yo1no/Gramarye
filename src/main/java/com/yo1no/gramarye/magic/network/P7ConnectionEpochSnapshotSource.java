package com.yo1no.gramarye.magic.network;

import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.Connection;

interface P7ConnectionEpochSnapshotSource {
    enum CaptureOutcome { CAPTURED, NO_SESSION, CONNECTION_MISMATCH, SERVER_UNAVAILABLE }

    record CaptureResult(CaptureOutcome outcome, Optional<P7SessionIdentity> identity) {
        public CaptureResult {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(identity, "identity");
            if ((outcome == CaptureOutcome.CAPTURED) != identity.isPresent()) {
                throw new P7SemanticInvariantException("capture outcome and identity differ");
            }
        }

        static CaptureResult captured(P7SessionIdentity identity) {
            return new CaptureResult(CaptureOutcome.CAPTURED, Optional.of(identity));
        }

        static CaptureResult rejected(CaptureOutcome outcome) {
            return new CaptureResult(outcome, Optional.empty());
        }
    }

    CaptureResult captureAuthenticatedSession(
            UUID authenticatedPlayerId, Connection connection);

    boolean isCurrentCapture(P7SessionIdentity identity, Connection connection);
}
