package com.yo1no.gramarye.magic.network;

final class RateStrikeState {
    enum Outcome {
        RECORDED,
        INTERNAL_SERVER_FAULT
    }

    static final class Decision {
        private final Outcome outcome;
        private final RateStrikeState nextState;
        private final boolean disconnect;

        private Decision(Outcome outcome, RateStrikeState nextState, boolean disconnect) {
            this.outcome = outcome;
            this.nextState = nextState;
            this.disconnect = disconnect;
        }

        Outcome outcome() {
            return outcome;
        }

        RateStrikeState nextState() {
            return nextState;
        }

        boolean disconnect() {
            return disconnect;
        }
    }

    private final boolean windowActive;
    private final long windowStartTick;
    private final long lastStrikeTick;
    private final int strikeCount;

    private RateStrikeState(
            boolean windowActive, long windowStartTick, long lastStrikeTick, int strikeCount) {
        if (windowActive) {
            if (strikeCount < 1
                    || strikeCount > P7NetworkBounds.RATE_STRIKE_DISCONNECT_THRESHOLD
                    || lastStrikeTick < windowStartTick) {
                throw new P7SemanticInvariantException("rate strike window is invalid");
            }
        } else if (strikeCount != 0) {
            throw new P7SemanticInvariantException("inactive rate strike window has strikes");
        }
        this.windowActive = windowActive;
        this.windowStartTick = windowStartTick;
        this.lastStrikeTick = lastStrikeTick;
        this.strikeCount = strikeCount;
    }

    static RateStrikeState initial() {
        return new RateStrikeState(false, 0L, 0L, 0);
    }

    Decision recordRateLimited(long authoritativeTick) {
        if (!tickIsMonotonic(authoritativeTick)) {
            return new Decision(Outcome.INTERNAL_SERVER_FAULT, this, false);
        }
        if (!windowActive || outsideWindow(authoritativeTick)) {
            return new Decision(
                    Outcome.RECORDED,
                    new RateStrikeState(true, authoritativeTick, authoritativeTick, 1),
                    false);
        }
        var nextCount = Math.min(
                P7NetworkBounds.RATE_STRIKE_DISCONNECT_THRESHOLD, strikeCount + 1);
        return new Decision(
                Outcome.RECORDED,
                new RateStrikeState(
                        true, windowStartTick, authoritativeTick, nextCount),
                nextCount == P7NetworkBounds.RATE_STRIKE_DISCONNECT_THRESHOLD);
    }

    boolean tickIsMonotonic(long authoritativeTick) {
        return !windowActive || authoritativeTick >= lastStrikeTick;
    }

    private boolean outsideWindow(long authoritativeTick) {
        var elapsed = authoritativeTick - windowStartTick;
        return elapsed < 0 || elapsed >= P7NetworkBounds.RATE_STRIKE_WINDOW_TICKS;
    }

    boolean windowActive() {
        return windowActive;
    }

    long windowStartTick() {
        if (!windowActive) {
            throw new P7SemanticInvariantException("rate strike window is inactive");
        }
        return windowStartTick;
    }

    long lastStrikeTick() {
        if (!windowActive) {
            throw new P7SemanticInvariantException("rate strike window is inactive");
        }
        return lastStrikeTick;
    }

    int strikeCount() {
        return strikeCount;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof RateStrikeState that
                        && windowActive == that.windowActive
                        && windowStartTick == that.windowStartTick
                        && lastStrikeTick == that.lastStrikeTick
                        && strikeCount == that.strikeCount;
    }

    @Override
    public int hashCode() {
        var result = Boolean.hashCode(windowActive);
        result = 31 * result + Long.hashCode(windowStartTick);
        result = 31 * result + Long.hashCode(lastStrikeTick);
        return 31 * result + Integer.hashCode(strikeCount);
    }
}
