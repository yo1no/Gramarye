package com.yo1no.gramarye.magic.network;

final class IntentTokenBucket {
    enum Outcome {
        CONSUMED,
        RATE_LIMITED,
        INTERNAL_SERVER_FAULT
    }

    static final class Decision {
        private final Outcome outcome;
        private final IntentTokenBucket nextState;

        private Decision(Outcome outcome, IntentTokenBucket nextState) {
            this.outcome = outcome;
            this.nextState = nextState;
        }

        Outcome outcome() {
            return outcome;
        }

        IntentTokenBucket nextState() {
            return nextState;
        }
    }

    private final int tokens;
    private final long lastRefillTick;

    private IntentTokenBucket(int tokens, long lastRefillTick) {
        if (tokens < 0 || tokens > P7NetworkBounds.RATE_BUCKET_CAPACITY) {
            throw new P7SemanticInvariantException("token count outside capacity");
        }
        this.tokens = tokens;
        this.lastRefillTick = lastRefillTick;
    }

    static IntentTokenBucket initial(long authoritativeTick) {
        return new IntentTokenBucket(
                P7NetworkBounds.RATE_BUCKET_INITIAL_TOKENS, authoritativeTick);
    }

    Decision consume(long authoritativeTick) {
        if (!tickIsMonotonic(authoritativeTick)) {
            return new Decision(Outcome.INTERNAL_SERVER_FAULT, this);
        }

        var refilledTokens = refilledTokens(authoritativeTick);
        if (refilledTokens < P7NetworkBounds.RATE_BUCKET_COST_PER_CAST) {
            return new Decision(Outcome.RATE_LIMITED, this);
        }
        return new Decision(
                Outcome.CONSUMED,
                new IntentTokenBucket(
                        refilledTokens - P7NetworkBounds.RATE_BUCKET_COST_PER_CAST,
                        authoritativeTick));
    }

    boolean tickIsMonotonic(long authoritativeTick) {
        return authoritativeTick >= lastRefillTick;
    }

    private int refilledTokens(long authoritativeTick) {
        if (authoritativeTick == lastRefillTick
                || tokens == P7NetworkBounds.RATE_BUCKET_CAPACITY) {
            return tokens;
        }
        var elapsed = authoritativeTick - lastRefillTick;
        if (elapsed < 0) {
            return P7NetworkBounds.RATE_BUCKET_CAPACITY;
        }
        var missing = P7NetworkBounds.RATE_BUCKET_CAPACITY - tokens;
        var ticksToCapacity = (missing + P7NetworkBounds.RATE_BUCKET_REFILL_PER_TICK - 1L)
                / P7NetworkBounds.RATE_BUCKET_REFILL_PER_TICK;
        if (elapsed >= ticksToCapacity) {
            return P7NetworkBounds.RATE_BUCKET_CAPACITY;
        }
        return tokens + (int) elapsed * P7NetworkBounds.RATE_BUCKET_REFILL_PER_TICK;
    }

    int tokens() {
        return tokens;
    }

    long lastRefillTick() {
        return lastRefillTick;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof IntentTokenBucket that
                        && tokens == that.tokens
                        && lastRefillTick == that.lastRefillTick;
    }

    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(tokens) + Long.hashCode(lastRefillTick);
    }
}
