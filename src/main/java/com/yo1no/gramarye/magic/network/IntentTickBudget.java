package com.yo1no.gramarye.magic.network;

final class IntentTickBudget {
    enum Kind {
        PLAYER_INGRESS(P7NetworkBounds.MAX_INTENTS_PER_PLAYER_PER_TICK),
        GLOBAL_WORK(P7NetworkBounds.MAX_GLOBAL_WORK_UNITS_PER_TICK);

        private final int limit;

        Kind(int limit) {
            this.limit = limit;
        }

        int limit() {
            return limit;
        }
    }

    enum Outcome {
        ADMITTED,
        DENIED,
        INTERNAL_SERVER_FAULT
    }

    static final class Decision {
        private final Outcome outcome;
        private final IntentTickBudget nextState;

        private Decision(Outcome outcome, IntentTickBudget nextState) {
            this.outcome = outcome;
            this.nextState = nextState;
        }

        Outcome outcome() {
            return outcome;
        }

        IntentTickBudget nextState() {
            return nextState;
        }
    }

    private final Kind kind;
    private final long currentTick;
    private final int used;

    private IntentTickBudget(Kind kind, long currentTick, int used) {
        if (kind == null || used < 0 || used > kind.limit()) {
            throw new P7SemanticInvariantException("tick budget state is invalid");
        }
        this.kind = kind;
        this.currentTick = currentTick;
        this.used = used;
    }

    static IntentTickBudget initial(Kind kind, long authoritativeTick) {
        return new IntentTickBudget(kind, authoritativeTick, 0);
    }

    Decision consume(long authoritativeTick) {
        if (!tickIsMonotonic(authoritativeTick)) {
            return new Decision(Outcome.INTERNAL_SERVER_FAULT, this);
        }
        if (authoritativeTick > currentTick) {
            return new Decision(
                    Outcome.ADMITTED, new IntentTickBudget(kind, authoritativeTick, 1));
        }
        if (used == kind.limit()) {
            return new Decision(Outcome.DENIED, this);
        }
        return new Decision(
                Outcome.ADMITTED, new IntentTickBudget(kind, currentTick, used + 1));
    }

    boolean tickIsMonotonic(long authoritativeTick) {
        return authoritativeTick >= currentTick;
    }

    Kind kind() {
        return kind;
    }

    long currentTick() {
        return currentTick;
    }

    int used() {
        return used;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof IntentTickBudget that
                        && kind == that.kind
                        && currentTick == that.currentTick
                        && used == that.used;
    }

    @Override
    public int hashCode() {
        var result = kind.hashCode();
        result = 31 * result + Long.hashCode(currentTick);
        return 31 * result + Integer.hashCode(used);
    }
}
