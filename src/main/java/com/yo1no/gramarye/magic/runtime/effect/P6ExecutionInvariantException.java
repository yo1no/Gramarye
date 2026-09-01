package com.yo1no.gramarye.magic.runtime.effect;

import java.util.Objects;

enum P6ExecutionInvariantCode {
    RESOLVER_RETURNED_NULL,
    INVALID_ACCEPTED_PLAN,
    GUARD_RETURNED_NULL,
    PORT_RETURNED_NULL,
    ACTUAL_MUTATION_EXCEEDS_DECLARED,
    TOTAL_MUTATION_EXCEEDS_BOUND,
    TRACE_CAPACITY_EXCEEDED,
    UNSUPPORTED_COMMIT_STEP,
    IMPOSSIBLE_RESULT,
    INVALID_GUARD_DECISION_USAGE
}

final class P6ExecutionInvariantException extends RuntimeException {
    private final P6ExecutionInvariantCode code;

    P6ExecutionInvariantException(P6ExecutionInvariantCode code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    P6ExecutionInvariantCode code() {
        return code;
    }
}
