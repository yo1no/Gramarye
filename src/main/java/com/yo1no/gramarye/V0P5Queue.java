package com.yo1no.gramarye;

import java.util.Arrays;

/** Fixed typed partition of the P5 owner queue. This storage neither clocks nor dispatches work. */
final class V0P5Queue {
    static final int ROOT_RELEASE = 0;
    static final int ROOT_CREATE = 1;
    static final int ROOT_HIT = 2;
    static final int CHILD_RELEASE = 3;
    static final int CHILD_CREATE = 4;
    static final int CHILD_HIT = 5;
    static final int ROOT_LEASE_CLEANUP = 2;

    private static final int ROLES_PER_LINEAGE = 6;
    private static final byte RESERVED = 1;
    private static final byte ACTIVE = 2;
    private static final byte FENCED = 3;
    private static final byte QUEUED = 1;
    private static final byte CLAIMED = 2;
    private static final byte SPENT = 3;

    private final long[] lineageOwners;
    private final byte[] lineageStates;
    private final boolean[] childReserved;
    private final int[] cleanupClaimed;
    private final int[] cleanupOutstanding;
    private final byte[] eventStates;
    private final long[] sequences;
    private final long[] dueTicks;
    private final long[] legacyOrders;

    V0P5Queue(int maxLineages) {
        if (maxLineages < 1 || maxLineages > 4) {
            throw new IllegalArgumentException("V0 queue lineage capacity is out of range");
        }
        int events = Math.multiplyExact(maxLineages, ROLES_PER_LINEAGE);
        lineageOwners = new long[maxLineages];
        lineageStates = new byte[maxLineages];
        childReserved = new boolean[maxLineages];
        cleanupClaimed = new int[maxLineages];
        cleanupOutstanding = new int[maxLineages];
        eventStates = new byte[events];
        sequences = new long[events];
        dueTicks = new long[events];
        legacyOrders = new long[events];
    }

    boolean reserve(int lineageSlot, long exactLineage) {
        if (!validLineageSlot(lineageSlot) || exactLineage <= 0 || lineageOwners[lineageSlot] != 0) {
            return false;
        }
        lineageOwners[lineageSlot] = exactLineage;
        lineageStates[lineageSlot] = RESERVED;
        return true;
    }

    void activateReserved(int lineageSlot) {
        // The P5 owner prevalidated this exact cell before compare-and-accept.
        lineageStates[lineageSlot] = ACTIVE;
    }

    boolean rollback(int lineageSlot, long exactLineage) {
        if (!owns(lineageSlot, exactLineage) || lineageStates[lineageSlot] != RESERVED) {
            return false;
        }
        clear(lineageSlot);
        return true;
    }

    boolean reserveChild(int lineageSlot, long exactLineage, long parentEventSequence) {
        if (!owns(lineageSlot, exactLineage) || lineageStates[lineageSlot] != ACTIVE
                || childReserved[lineageSlot]) {
            return false;
        }
        int parent = cell(lineageSlot, ROOT_HIT);
        if (eventStates[parent] != CLAIMED || sequences[parent] != parentEventSequence) {
            return false;
        }
        childReserved[lineageSlot] = true;
        return true;
    }

    boolean publish(int lineageSlot, long exactLineage, int role, long dueTick, long legacyOrder,
            long exactEventSequence) {
        if (!owns(lineageSlot, exactLineage) || !validRole(role) || dueTick < 0
                || legacyOrder < 0 || exactEventSequence <= 0 || lineageStates[lineageSlot] == FENCED) {
            return false;
        }
        int index = cell(lineageSlot, role);
        if (eventStates[index] != 0 || !predecessorOwnsPublication(lineageSlot, role)) {
            return false;
        }
        if (role == CHILD_RELEASE && dueTick <= dueTicks[cell(lineageSlot, ROOT_HIT)]) {
            return false;
        }
        sequences[index] = exactEventSequence;
        dueTicks[index] = dueTick;
        legacyOrders[index] = legacyOrder;
        eventStates[index] = QUEUED;
        return true;
    }

    private boolean predecessorOwnsPublication(int lineageSlot, int role) {
        return switch (role) {
            case ROOT_RELEASE -> lineageStates[lineageSlot] == RESERVED;
            case ROOT_CREATE, CHILD_CREATE -> lineageStates[lineageSlot] == ACTIVE
                    && eventStates[cell(lineageSlot, role - 1)] == CLAIMED;
            case ROOT_HIT, CHILD_HIT -> lineageStates[lineageSlot] == ACTIVE
                    && eventStates[cell(lineageSlot, role - 1)] != 0;
            case CHILD_RELEASE -> lineageStates[lineageSlot] == ACTIVE && childReserved[lineageSlot]
                    && eventStates[cell(lineageSlot, ROOT_HIT)] == CLAIMED;
            default -> false;
        };
    }

    /** Returns a V0 cell only if it precedes the already eligible legacy partition head. */
    int ready(long runtimeTick, boolean v0BudgetAvailable, RuntimeEvent eligibleLegacyHead) {
        if (!v0BudgetAvailable) {
            return -1;
        }
        int winner = -1;
        for (int index = 0; index < eventStates.length; index++) {
            if (eventStates[index] == QUEUED && lineageStates[index / ROLES_PER_LINEAGE] == ACTIVE
                    && dueTicks[index] <= runtimeTick && (winner < 0 || precedes(index, winner))) {
                winner = index;
            }
        }
        if (winner < 0 || eligibleLegacyHead == null || eligibleLegacyHead.scheduledRuntimeTick() > runtimeTick) {
            return winner;
        }
        long legacyDue = eligibleLegacyHead.scheduledRuntimeTick();
        return dueTicks[winner] < legacyDue || (dueTicks[winner] == legacyDue
                && eligibleLegacyHead.eventId().value() > legacyOrders[winner]) ? winner : -1;
    }

    private boolean precedes(int contender, int incumbent) {
        return dueTicks[contender] < dueTicks[incumbent] || (dueTicks[contender] == dueTicks[incumbent]
                && (legacyOrders[contender] < legacyOrders[incumbent]
                || (legacyOrders[contender] == legacyOrders[incumbent] && sequences[contender] < sequences[incumbent])));
    }

    boolean claim(int eventCell, long exactLineage, long exactEventSequence, long runtimeTick) {
        if (!ownsEvent(eventCell, exactLineage, exactEventSequence) || eventStates[eventCell] != QUEUED
                || lineageStates[eventCell / ROLES_PER_LINEAGE] != ACTIVE || dueTicks[eventCell] > runtimeTick) {
            return false;
        }
        eventStates[eventCell] = CLAIMED;
        return true;
    }

    boolean finish(int eventCell, long exactLineage, long exactEventSequence) {
        if (!ownsEvent(eventCell, exactLineage, exactEventSequence) || eventStates[eventCell] != CLAIMED) {
            return false;
        }
        eventStates[eventCell] = SPENT;
        return true;
    }

    boolean claimCleanup(int lineageSlot, long exactLineage, int role) {
        if (!owns(lineageSlot, exactLineage) || !validRole(role) || lineageStates[lineageSlot] == FENCED
                || (lineageStates[lineageSlot] == RESERVED && role != ROOT_LEASE_CLEANUP)) {
            return false;
        }
        int mask = 1 << role;
        if ((cleanupClaimed[lineageSlot] & mask) != 0) {
            return false;
        }
        cleanupClaimed[lineageSlot] |= mask;
        cleanupOutstanding[lineageSlot] |= mask;
        return true;
    }

    boolean settleCleanup(int lineageSlot, long exactLineage, int role) {
        if (!owns(lineageSlot, exactLineage) || !validRole(role)) {
            return false;
        }
        int mask = 1 << role;
        if ((cleanupOutstanding[lineageSlot] & mask) == 0) {
            return false;
        }
        cleanupOutstanding[lineageSlot] &= ~mask;
        return true;
    }

    void fence(int lineageSlot, long exactLineage) {
        if (!owns(lineageSlot, exactLineage)) {
            return;
        }
        lineageStates[lineageSlot] = FENCED;
        int first = cell(lineageSlot, ROOT_RELEASE);
        for (int index = first; index < first + ROLES_PER_LINEAGE; index++) {
            if (eventStates[index] == QUEUED) {
                eventStates[index] = SPENT;
            }
        }
    }

    boolean onlyLeaseCleanupRemains(int lineageSlot, long exactLineage) {
        return owns(lineageSlot, exactLineage) && lineageStates[lineageSlot] == FENCED
                && cleanupOutstanding[lineageSlot] == (1 << ROOT_LEASE_CLEANUP)
                && !hasClaims(lineageSlot);
    }

    long sequence(int eventCell) {
        return sequences[eventCell];
    }

    int lineageSlot(int eventCell) {
        return eventCell / ROLES_PER_LINEAGE;
    }

    int role(int eventCell) {
        return eventCell % ROLES_PER_LINEAGE;
    }

    boolean close(int lineageSlot, long exactLineage) {
        if (!owns(lineageSlot, exactLineage) || lineageStates[lineageSlot] != FENCED
                || cleanupOutstanding[lineageSlot] != 0 || hasClaims(lineageSlot)) {
            return false;
        }
        clear(lineageSlot);
        return true;
    }

    private boolean hasClaims(int lineageSlot) {
        int first = cell(lineageSlot, ROOT_RELEASE);
        for (int index = first; index < first + ROLES_PER_LINEAGE; index++) {
            if (eventStates[index] == CLAIMED) {
                return true;
            }
        }
        return false;
    }

    private void clear(int lineageSlot) {
        lineageOwners[lineageSlot] = 0;
        lineageStates[lineageSlot] = 0;
        childReserved[lineageSlot] = false;
        cleanupClaimed[lineageSlot] = 0;
        cleanupOutstanding[lineageSlot] = 0;
        int first = cell(lineageSlot, ROOT_RELEASE);
        int end = first + ROLES_PER_LINEAGE;
        Arrays.fill(eventStates, first, end, (byte) 0);
        Arrays.fill(sequences, first, end, 0L);
        Arrays.fill(dueTicks, first, end, 0L);
        Arrays.fill(legacyOrders, first, end, 0L);
    }

    private boolean ownsEvent(int eventCell, long exactLineage, long exactEventSequence) {
        return eventCell >= 0 && eventCell < eventStates.length && exactEventSequence > 0
                && owns(eventCell / ROLES_PER_LINEAGE, exactLineage) && sequences[eventCell] == exactEventSequence;
    }

    private boolean owns(int lineageSlot, long exactLineage) {
        return validLineageSlot(lineageSlot) && exactLineage > 0 && lineageOwners[lineageSlot] == exactLineage;
    }

    private boolean validLineageSlot(int lineageSlot) {
        return lineageSlot >= 0 && lineageSlot < lineageOwners.length;
    }

    private static boolean validRole(int role) {
        return role >= 0 && role < ROLES_PER_LINEAGE;
    }

    private static int cell(int lineageSlot, int role) {
        return lineageSlot * ROLES_PER_LINEAGE + role;
    }
}
