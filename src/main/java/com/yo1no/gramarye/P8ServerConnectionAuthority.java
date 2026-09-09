package com.yo1no.gramarye;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

/** Bounded server-thread owner for P8 connection epochs and catalog readiness. */
final class P8ServerConnectionAuthority {
    private static final Comparator<UUID> UNSIGNED_UUID_ORDER = (left, right) -> {
        var comparison = Long.compareUnsigned(
                left.getMostSignificantBits(), right.getMostSignificantBits());
        return comparison != 0
                ? comparison
                : Long.compareUnsigned(
                        left.getLeastSignificantBits(), right.getLeastSignificantBits());
    };

    private final TreeMap<UUID, ConnectionRecord> records =
            new TreeMap<>(UNSIGNED_UUID_ORDER);
    private long nextEpoch = 1L;
    private boolean epochExhausted;

    void reset() {
        records.clear();
        nextEpoch = 1L;
        epochExhausted = false;
    }

    Optional<P8RecipientIdentity> open(
            UUID playerId, long catalogGeneration, long eligibleTick) {
        Objects.requireNonNull(playerId, "playerId");
        if (catalogGeneration < 0L || eligibleTick < 0L) {
            throw new IllegalArgumentException("P8 connection coordinate is invalid");
        }
        if (epochExhausted) {
            records.remove(playerId);
            return Optional.empty();
        }
        if (!records.containsKey(playerId)
                        && records.size()
                                == PresentationLimits.MAX_CATALOG_READINESS_RECORDS) {
            return Optional.empty();
        }
        long epoch = nextEpoch;
        if (epoch == Long.MAX_VALUE) {
            epochExhausted = true;
        } else {
            nextEpoch = Math.incrementExact(epoch);
        }
        var record = new ConnectionRecord(playerId, epoch);
        records.put(playerId, record);
        if (catalogGeneration > 0L) {
            record.schedule(catalogGeneration, eligibleTick);
        }
        return Optional.of(new P8RecipientIdentity(playerId, epoch));
    }

    void close(UUID playerId) {
        records.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    void retainOnly(Collection<UUID> currentPlayerIds) {
        Objects.requireNonNull(currentPlayerIds, "currentPlayerIds");
        var current = new java.util.HashSet<UUID>(currentPlayerIds);
        records.keySet().removeIf(playerId -> !current.contains(playerId));
    }

    void retainMatching(Predicate<UUID> currentIdentity) {
        Objects.requireNonNull(currentIdentity, "currentIdentity");
        records.keySet().removeIf(playerId -> !currentIdentity.test(playerId));
    }

    void scheduleAll(long catalogGeneration, long eligibleTick) {
        requireCatalogCoordinate(catalogGeneration, eligibleTick);
        records.values().forEach(record -> record.schedule(catalogGeneration, eligibleTick));
    }

    boolean schedule(UUID playerId, long catalogGeneration, long eligibleTick) {
        Objects.requireNonNull(playerId, "playerId");
        requireCatalogCoordinate(catalogGeneration, eligibleTick);
        var record = records.get(playerId);
        if (record == null) {
            return false;
        }
        return record.schedule(catalogGeneration, eligibleTick);
    }

    List<P8CatalogAttemptKey> due(long tick) {
        if (tick < 0L) {
            throw new IllegalArgumentException("P8 catalog drain tick is invalid");
        }
        var due = new ArrayList<P8CatalogAttemptKey>(
                Math.min(records.size(), PresentationLimits.MAX_CATALOG_READINESS_RECORDS));
        for (var record : records.values()) {
            if (record.queued
                    && record.eligibleTick <= tick
                    && record.attempts
                            < PresentationLimits.MAX_CATALOG_SUBMISSION_ATTEMPTS
                    && record.lastAttemptTick != tick) {
                due.add(record.key());
            }
        }
        return List.copyOf(due);
    }

    boolean isCurrent(P8CatalogAttemptKey key) {
        Objects.requireNonNull(key, "key");
        var record = records.get(key.playerId());
        return record != null && record.matches(key);
    }

    boolean beginAttempt(P8CatalogAttemptKey key, long tick) {
        Objects.requireNonNull(key, "key");
        var record = records.get(key.playerId());
        if (record == null
                || !record.matches(key)
                || !record.queued
                || record.eligibleTick > tick
                || record.lastAttemptTick == tick
                || record.attempts
                        >= PresentationLimits.MAX_CATALOG_SUBMISSION_ATTEMPTS) {
            return false;
        }
        record.attempts = Math.incrementExact(record.attempts);
        record.lastAttemptTick = tick;
        return true;
    }

    void submitted(P8CatalogAttemptKey key) {
        var record = currentRecord(key);
        if (record == null) {
            return;
        }
        record.ready = true;
        record.queued = false;
    }

    void failed(P8CatalogAttemptKey key, long tick) {
        var record = currentRecord(key);
        if (record == null) {
            return;
        }
        record.ready = false;
        if (record.attempts >= PresentationLimits.MAX_CATALOG_SUBMISSION_ATTEMPTS
                || tick == Long.MAX_VALUE) {
            record.queued = false;
            return;
        }
        record.eligibleTick = Math.incrementExact(tick);
    }

    void cancel(P8CatalogAttemptKey key) {
        var record = currentRecord(key);
        if (record == null) {
            return;
        }
        record.ready = false;
        record.queued = false;
    }

    Optional<P8RecipientIdentity> captureReady(
            UUID playerId, long catalogGeneration) {
        Objects.requireNonNull(playerId, "playerId");
        var record = records.get(playerId);
        return record != null
                        && record.ready
                        && record.catalogGeneration == catalogGeneration
                ? Optional.of(new P8RecipientIdentity(playerId, record.epoch))
                : Optional.empty();
    }

    boolean isCurrent(P8RecipientIdentity identity, long catalogGeneration) {
        Objects.requireNonNull(identity, "identity");
        var record = records.get(identity.playerId());
        return record != null
                && record.epoch == identity.connectionEpoch()
                && record.catalogGeneration == catalogGeneration
                && record.ready;
    }

    int size() {
        return records.size();
    }

    int attempts(UUID playerId) {
        var record = records.get(Objects.requireNonNull(playerId, "playerId"));
        return record == null ? 0 : record.attempts;
    }

    boolean ready(UUID playerId, long catalogGeneration) {
        return captureReady(playerId, catalogGeneration).isPresent();
    }

    private ConnectionRecord currentRecord(P8CatalogAttemptKey key) {
        Objects.requireNonNull(key, "key");
        var record = records.get(key.playerId());
        return record != null && record.matches(key) ? record : null;
    }

    private static void requireCatalogCoordinate(long catalogGeneration, long eligibleTick) {
        if (catalogGeneration <= 0L || eligibleTick < 0L) {
            throw new IllegalArgumentException("P8 catalog retry coordinate is invalid");
        }
    }

    private static final class ConnectionRecord {
        private final UUID playerId;
        private final long epoch;
        private long catalogGeneration;
        private long eligibleTick;
        private long lastAttemptTick = Long.MIN_VALUE;
        private int attempts;
        private boolean queued;
        private boolean ready;

        private ConnectionRecord(UUID playerId, long epoch) {
            this.playerId = playerId;
            this.epoch = epoch;
        }

        private boolean schedule(long generation, long tick) {
            if (generation <= catalogGeneration) {
                return false;
            }
            catalogGeneration = generation;
            eligibleTick = tick;
            lastAttemptTick = Long.MIN_VALUE;
            attempts = 0;
            queued = true;
            ready = false;
            return true;
        }

        private P8CatalogAttemptKey key() {
            return new P8CatalogAttemptKey(playerId, epoch, catalogGeneration);
        }

        private boolean matches(P8CatalogAttemptKey key) {
            return playerId.equals(key.playerId())
                    && epoch == key.connectionEpoch()
                    && catalogGeneration == key.catalogGeneration();
        }
    }
}

record P8CatalogAttemptKey(UUID playerId, long connectionEpoch, long catalogGeneration) {
    P8CatalogAttemptKey {
        Objects.requireNonNull(playerId, "playerId");
        if (connectionEpoch <= 0L || catalogGeneration <= 0L) {
            throw new IllegalArgumentException("P8 catalog attempt identity is invalid");
        }
    }
}
