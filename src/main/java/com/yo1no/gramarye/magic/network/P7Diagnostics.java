package com.yo1no.gramarye.magic.network;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Fixed scalar-only observation ring and independently bounded log throttle. */
final class P7Diagnostics {
    record Observation(UUID playerId, long tick, P7IntentFailureReason reason, long count) {}

    private static final class Container {
        private final Observation[] ring = new Observation[P7NetworkBounds.MAX_DIAGNOSTIC_RECORDS];
        private final Observation[] throttle = new Observation[P7NetworkBounds.MAX_DIAGNOSTIC_RECORDS];
        private int cursor;
        private int size;
    }

    private Container container = new Container();

    void record(UUID playerId, long tick, P7IntentFailureReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (tick < 0) {
            throw new P7SemanticInvariantException("diagnostic tick is negative");
        }
        var state = container;
        long count = 1;
        for (var previous : state.ring) {
            if (previous != null && Objects.equals(previous.playerId(), playerId)
                    && previous.reason() == reason) {
                count = previous.count() == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(count, previous.count() + 1);
            }
        }
        var observation = new Observation(playerId, tick, reason, count);
        state.ring[state.cursor] = observation;
        state.cursor = (state.cursor + 1) % state.ring.length;
        state.size = Math.min(state.size + 1, state.ring.length);
        int reusable = -1;
        for (int index = 0; index < state.throttle.length; index++) {
            var previous = state.throttle[index];
            if (previous != null && Objects.equals(previous.playerId(), playerId)
                    && previous.reason() == reason) {
                if (tick < previous.tick() || tick - previous.tick() < 100) {
                    return;
                }
                reusable = index;
                break;
            }
            if (reusable < 0 && (previous == null || tick >= previous.tick() && tick - previous.tick() >= 100)) {
                reusable = index;
            }
        }
        // A full throttle suppresses a new pair; it never evicts a still-protected pair.
        if (reusable >= 0) {
            state.throttle[reusable] = observation;
            LogUtils.getLogger().debug("P7 diagnostic player={} tick={} reason={} count={}",
                    playerId, tick, reason, count);
        }
    }

    List<Observation> snapshot() {
        return java.util.Arrays.stream(container.ring).filter(Objects::nonNull).toList();
    }

    void discard() {
        container = new Container();
    }
}
