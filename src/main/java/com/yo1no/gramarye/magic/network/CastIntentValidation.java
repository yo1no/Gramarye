package com.yo1no.gramarye.magic.network;

import java.util.Optional;

final class CastIntentValidation {
    enum Outcome {
        VALID,
        INVALID
    }

    private final Outcome outcome;
    private final CastIntent intent;
    private final P7IntentFailureReason failureReason;

    private CastIntentValidation(
            Outcome outcome, CastIntent intent, P7IntentFailureReason failureReason) {
        this.outcome = outcome;
        this.intent = intent;
        this.failureReason = failureReason;
    }

    static CastIntentValidation validate(
            long sequence,
            int slot,
            int rawInputKind,
            int presenceMask,
            Integer aimX,
            Integer aimY,
            Integer aimZ,
            Integer entityNetworkId) {
        if (!CastInputKind.isKnownCode(rawInputKind)
                || slot < P7NetworkBounds.SLOT_MIN
                || slot > P7NetworkBounds.SLOT_MAX
                || (presenceMask & ~P7NetworkBounds.ALLOWED_PRESENCE_MASK) != 0) {
            return invalid();
        }

        var aimFlag = (presenceMask & (1 << P7NetworkBounds.AIM_PRESENT_BIT)) != 0;
        var entityFlag =
                (presenceMask & (1 << P7NetworkBounds.ENTITY_HINT_PRESENT_BIT)) != 0;
        var allAimScalarsPresent = aimX != null && aimY != null && aimZ != null;
        var anyAimScalarPresent = aimX != null || aimY != null || aimZ != null;

        if (aimFlag != allAimScalarsPresent
                || (!aimFlag && anyAimScalarPresent)
                || entityFlag != (entityNetworkId != null)) {
            return invalid();
        }
        if (aimFlag && !AimHint.componentsValid(aimX, aimY, aimZ)) {
            return invalid();
        }
        if (entityFlag && !EntityHint.valueValid(entityNetworkId)) {
            return invalid();
        }

        var aim = aimFlag ? new AimHint(aimX, aimY, aimZ) : null;
        var entity = entityFlag ? new EntityHint(entityNetworkId) : null;
        var intent = new CastIntent(
                sequence,
                slot,
                CastInputKind.fromValidatedCode(rawInputKind),
                presenceMask,
                aim,
                entity);
        if (intent.encodedBodySize() > P7NetworkBounds.ACTUAL_MAX_CAST_INTENT_BODY_BYTES
                || intent.encodedBodySize() > P7NetworkBounds.MAX_C2S_INTENT_BYTES) {
            throw new P7SemanticInvariantException("canonical cast body exceeds its bound");
        }
        return new CastIntentValidation(Outcome.VALID, intent, null);
    }

    private static CastIntentValidation invalid() {
        return new CastIntentValidation(
                Outcome.INVALID, null, P7IntentFailureReason.MALFORMED_PAYLOAD);
    }

    Outcome outcome() {
        return outcome;
    }

    boolean valid() {
        return outcome == Outcome.VALID;
    }

    Optional<CastIntent> intent() {
        return Optional.ofNullable(intent);
    }

    Optional<P7IntentFailureReason> failureReason() {
        return Optional.ofNullable(failureReason);
    }
}
