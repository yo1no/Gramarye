package com.yo1no.gramarye.magic.network;

enum CastInputKind {
    CAST(P7NetworkBounds.CAST_INPUT_KIND_CODE);

    private final int semanticCode;

    CastInputKind(int semanticCode) {
        this.semanticCode = semanticCode;
    }

    int semanticCode() {
        return semanticCode;
    }

    static boolean isKnownCode(int rawCode) {
        return rawCode == P7NetworkBounds.CAST_INPUT_KIND_CODE;
    }

    static CastInputKind fromValidatedCode(int rawCode) {
        if (!isKnownCode(rawCode)) {
            throw new P7SemanticInvariantException("unvalidated cast input kind");
        }
        return CAST;
    }
}
