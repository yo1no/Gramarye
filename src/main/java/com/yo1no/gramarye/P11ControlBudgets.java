package com.yo1no.gramarye;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/** Scalar-only foundation accounting; none of these results grants native or writer authority. */
final class P11ControlBudgets {
    private P11ControlBudgets() {
        throw new AssertionError("no instances");
    }

    enum RateResult { ACCEPTED, RATE_LIMITED, CLOCK_UNAVAILABLE }

    enum WaitResult { NOT_STARTED, WAITING, EXPIRED, CLOCK_UNAVAILABLE }

    /** One instance per connection and command class; scene changes never create a replacement. */
    static final class TokenBucket {
        private final long capacityUnits;
        private final int refillPerSecond;
        private long units;
        private long lastMillis;
        private boolean clockRegressed;

        TokenBucket(int burst, int refillPerSecond, long initialMillis) {
            if (burst < 1 || refillPerSecond < 1 || initialMillis < 0) {
                throw new IllegalArgumentException("invalid token bucket inputs");
            }
            // A unit is one thousandth of a token. Every valid int burst fits in long.
            capacityUnits = (long) burst * 1_000L;
            this.refillPerSecond = refillPerSecond;
            units = capacityUnits;
            lastMillis = initialMillis;
        }

        synchronized RateResult take(long nowMillis) {
            var available = availability(nowMillis);
            if (available == RateResult.ACCEPTED) { units -= 1_000L; }
            return available;
        }

        /** Same checked refill, without consuming a TRY for a bounded readiness notification. */
        synchronized RateResult availability(long nowMillis) {
            requireClockEncoding(nowMillis);
            if (clockRegressed || nowMillis == -1) {
                return RateResult.CLOCK_UNAVAILABLE;
            }
            if (nowMillis < lastMillis) {
                clockRegressed = true;
                return RateResult.CLOCK_UNAVAILABLE;
            }
            long elapsed = nowMillis - lastMillis;
            long missing = capacityUnits - units;
            long fillMillis = missing / refillPerSecond
                    + (missing % refillPerSecond == 0 ? 0 : 1);
            // Multiply only below the saturation threshold; the product is then < missing.
            units = elapsed >= fillMillis
                    ? capacityUnits : units + elapsed * refillPerSecond;
            lastMillis = nowMillis;
            if (units < 1_000L) {
                return RateResult.RATE_LIMITED;
            }
            return RateResult.ACCEPTED;
        }
    }

    /** First proven playerless refusal starts the sole interval; there is deliberately no reset. */
    static final class AdmissionWait {
        private final long timeoutMillis;
        private boolean started;
        private boolean expired;
        private boolean clockUnavailable;
        private long startMillis;
        private long lastMillis;

        AdmissionWait(long timeoutMillis) {
            if (timeoutMillis <= 0) {
                throw new IllegalArgumentException("invalid admission wait");
            }
            this.timeoutMillis = timeoutMillis;
        }

        synchronized WaitResult refused(long nowMillis) {
            requireClockEncoding(nowMillis);
            if (!started) {
                started = true;
                clockUnavailable = nowMillis == -1;
                startMillis = nowMillis;
                lastMillis = nowMillis;
            }
            return observe(nowMillis);
        }

        synchronized WaitResult observe(long nowMillis) {
            requireClockEncoding(nowMillis);
            if (!started) {
                return WaitResult.NOT_STARTED;
            }
            if (expired) {
                return WaitResult.EXPIRED;
            }
            if (clockUnavailable || nowMillis == -1) {
                return WaitResult.CLOCK_UNAVAILABLE;
            }
            if (nowMillis < lastMillis) {
                clockUnavailable = true;
                return WaitResult.CLOCK_UNAVAILABLE;
            }
            lastMillis = nowMillis;
            if (nowMillis - startMillis >= timeoutMillis) {
                expired = true;
                return WaitResult.EXPIRED;
            }
            return WaitResult.WAITING;
        }
    }

    /** One actor-free wakeup for the whole dispatcher, not one native task per packet. */
    static final class MainWakeup {
        private boolean queued;
        private boolean running;
        private boolean dirty;
        private boolean exhausted;
        private boolean retired;

        synchronized boolean request() {
            if (retired) { return false; }
            dirty = true;
            if (queued || running || exhausted) { return false; }
            queued = true;
            return true;
        }

        synchronized boolean beginQueued() {
            if (!queued) { return false; }
            queued = false;
            if (retired || running) { return false; }
            running = true;
            dirty = false;
            return true;
        }

        synchronized boolean beginTick(boolean newTick) {
            if (newTick) { exhausted = false; }
            if (retired || running) { return false; }
            // A previously queued native task still exists. Do not erase its reservation.
            running = true;
            dirty = false;
            return true;
        }

        synchronized boolean complete(boolean budgetRemaining) {
            if (!running) { throw new IllegalStateException("P11_WAKEUP_NOT_RUNNING"); }
            running = false;
            exhausted = !budgetRemaining;
            if (retired || queued || exhausted || !dirty) { return false; }
            queued = true;
            return true;
        }

        synchronized void retire() { retired = true; }
    }

    /** Explicit membership capacity is not K_waiting or the P7 session ceiling. */
    static final class FairDispatcher {
        private final long capacity;
        private final int quantaPerTick;
        private final Map<Long, Member> members = new HashMap<>();
        private final ArrayDeque<Long> ready = new ArrayDeque<>();
        private long tick = -1;
        private int usedQuanta;
        private boolean retired;

        FairDispatcher(long capacity, int quantaPerTick) {
            if (capacity < 1 || quantaPerTick < 1) {
                throw new IllegalArgumentException("invalid dispatcher limits");
            }
            this.capacity = capacity;
            this.quantaPerTick = quantaPerTick;
        }

        synchronized Optional<Member> register(long connectionId) {
            if (connectionId <= 0) {
                throw new IllegalArgumentException("invalid connection id");
            }
            if (retired || members.containsKey(connectionId) || members.size() >= capacity) {
                return Optional.empty();
            }
            var member = new Member(this, connectionId);
            members.put(connectionId, member);
            return Optional.of(member);
        }

        synchronized boolean eligible(Member member) {
            if (!owns(member)) {
                return false;
            }
            if (member.dispatch != null) {
                // An arrival during service cannot vanish in complete(false).
                member.arrivedDuringDispatch = true;
            } else if (!member.queued) {
                member.queued = true;
                ready.addLast(member.connectionId);
            }
            return true;
        }

        synchronized Optional<Dispatch> poll(long currentTick) {
            if (retired) {
                return Optional.empty();
            }
            if (currentTick < 0 || currentTick < tick) {
                throw new IllegalArgumentException("control tick regressed");
            }
            if (currentTick != tick) {
                tick = currentTick;
                usedQuanta = 0;
            }
            if (usedQuanta >= quantaPerTick || ready.isEmpty()) {
                return Optional.empty();
            }
            var member = members.get(ready.removeFirst());
            member.queued = false;
            var dispatch = new Dispatch(member);
            member.dispatch = dispatch;
            usedQuanta++;
            return Optional.of(dispatch);
        }

        synchronized boolean complete(Dispatch dispatch, boolean stillEligible) {
            if (dispatch == null || !owns(dispatch.member)
                    || dispatch.member.dispatch != dispatch) {
                return false;
            }
            var member = dispatch.member;
            member.dispatch = null;
            if (stillEligible || member.arrivedDuringDispatch) {
                member.queued = true;
                ready.addLast(member.connectionId);
            }
            member.arrivedDuringDispatch = false;
            return true;
        }

        synchronized boolean retire(Member member) {
            if (!owns(member)) {
                return false;
            }
            members.remove(member.connectionId);
            if (member.queued) {
                ready.remove(member.connectionId);
            }
            member.queued = false;
            member.dispatch = null;
            member.arrivedDuringDispatch = false;
            return true;
        }

        synchronized int members() {
            return members.size();
        }

        synchronized int queued() {
            return ready.size();
        }

        synchronized boolean budgetRemaining(long currentTick) {
            if (currentTick < 0 || currentTick < tick) {
                throw new IllegalArgumentException("control tick regressed");
            }
            return !retired && (currentTick != tick || usedQuanta < quantaPerTick);
        }

        synchronized boolean retireSlot() {
            if (retired) {
                return false;
            }
            retired = true;
            members.clear();
            ready.clear();
            return true;
        }

        private boolean owns(Member member) {
            return !retired && member != null && member.dispatcher == this
                    && members.get(member.connectionId) == member;
        }

        static final class Member {
            private final FairDispatcher dispatcher;
            private final long connectionId;
            private boolean queued;
            private boolean arrivedDuringDispatch;
            private Dispatch dispatch;

            private Member(FairDispatcher dispatcher, long connectionId) {
                this.dispatcher = dispatcher;
                this.connectionId = connectionId;
            }

            long connectionId() {
                return connectionId;
            }
        }

        static final class Dispatch {
            private final Member member;

            private Dispatch(Member member) {
                this.member = member;
            }

            long connectionId() {
                return member.connectionId;
            }
        }
    }

    // R = W ∪ N ∪ F_op ∪ Q_ctx ∪ D_d ∪ I_w ∪ H, deduplicated by UUID.
    enum Root { WORK, NATIVE_CREDIT, OPERATION, COMMAND_CONTEXT, DIRTY, WRITE, TRANSITION }

    enum SealState { RESERVED, QUEUED, IN_FLIGHT, RELEASED }

    record ResourceCounts(int retainedUuids, int waitingConnections, int sealedSnapshots,
            long sealedBytes, int inFlight, int dirtyUuids) { }

    record DirtyAge(int dirtyUuids, OptionalLong oldestMillis,
            boolean warning, boolean clockUnavailable) { }

    /**
     * Each root reservation represents aggregate membership, not an individual work permit.
     * Its trusted caller must finish all represented obligations before releasing membership.
     * This class neither observes native roots nor asserts physical-save success.
     */
    static final class Resources {
        private final P11StartupLimits limits;
        private final Domain domain;
        private final Map<UUID, AccountOwner> accounts = new HashMap<>();
        private final Map<Long, WaitingReservation> waiting = new HashMap<>();
        private final Set<SealedReservation> sealed = new HashSet<>();
        private long sealedBytes;
        private SealedReservation inFlight;
        private int dirtyCount;
        private long lastDirtyClock = -1;
        private boolean dirtyClockRegressed;
        private boolean retired;

        Resources(P11StartupLimits limits) {
            this(limits, new Domain());
        }

        private Resources(P11StartupLimits limits, Domain domain) {
            this.limits = Objects.requireNonNull(limits, "limits");
            this.domain = domain;
        }

        synchronized AccountOwner newAccountOwner(UUID playerId) {
            requireActive();
            return new AccountOwner(this, Objects.requireNonNull(playerId, "playerId"));
        }

        synchronized ConnectionOwner newConnectionOwner(long connectionId) {
            requireActive();
            if (connectionId <= 0) {
                throw new IllegalArgumentException("invalid connection id");
            }
            return new ConnectionOwner(this, connectionId);
        }

        /** New responsibility admission; an exact repeated reservation is a no-op, not new work. */
        synchronized Optional<RootReservation> tryAcquireRoot(
                AccountOwner owner, Root root, boolean newDirtyWork) {
            Objects.requireNonNull(root, "root");
            if (root == Root.DIRTY) {
                throw new IllegalArgumentException("dirty membership comes from markDirty");
            }
            if (!issued(owner) || owner.finished) {
                return Optional.empty();
            }
            var current = accounts.get(owner.playerId);
            if (current != null && current != owner) {
                return Optional.empty();
            }
            var existing = owner.roots[root.ordinal()];
            if (existing != null) {
                return Optional.of(existing);
            }
            if (accounts.size() > limits.maxUuids()
                    || (current == null && accounts.size() >= limits.maxUuids())
                    || (newDirtyWork && dirtyCount >= limits.dirtyUuidAdmissionWatermark())) {
                return Optional.empty();
            }
            var reservation = new RootReservation(owner, root);
            accounts.put(owner.playerId, owner);
            owner.roots[root.ordinal()] = reservation;
            return Optional.of(reservation);
        }

        /**
         * Retains another root of an already accepted obligation, even after a lower-budget
         * transfer. The exact currently retained owner is required; this is not new admission.
         */
        synchronized Optional<RootReservation> retainRoot(AccountOwner owner, Root root) {
            Objects.requireNonNull(root, "root");
            if (root == Root.DIRTY) {
                throw new IllegalArgumentException("dirty membership comes from markDirty");
            }
            if (!owns(owner)) {
                return Optional.empty();
            }
            var existing = owner.roots[root.ordinal()];
            if (existing != null) {
                return Optional.of(existing);
            }
            var reservation = new RootReservation(owner, root);
            owner.roots[root.ordinal()] = reservation;
            return Optional.of(reservation);
        }

        synchronized boolean releaseRoot(RootReservation reservation) {
            if (!owns(reservation) || reservation.root == Root.DIRTY
                    || (reservation.root == Root.WRITE && reservation.owner.sealedCount != 0)) {
                return false;
            }
            clearRoot(reservation);
            return true;
        }

        /** Existing admitted mutations may cross the watermark; they are never evicted. */
        synchronized Optional<DirtyObservation> markDirty(AccountOwner owner, long nowMillis) {
            requireClockEncoding(nowMillis);
            if (!owns(owner)) {
                return Optional.empty();
            }
            if (owner.dirty == null) {
                owner.roots[Root.DIRTY.ordinal()] = new RootReservation(owner, Root.DIRTY);
                owner.dirtySince = nowMillis;
                dirtyCount++;
            }
            var observation = new DirtyObservation(owner);
            owner.dirty = observation;
            observeDirtyClock(nowMillis);
            return Optional.of(observation);
        }

        /** Only accounts for a caller-proven terminal; it is not a writer/readback receipt. */
        synchronized boolean releaseDirty(DirtyObservation observation) {
            if (observation == null || !owns(observation.owner)
                    || observation.owner.dirty != observation) {
                return false;
            }
            var owner = observation.owner;
            owner.dirty = null;
            dirtyCount--;
            clearRoot(owner.roots[Root.DIRTY.ordinal()]);
            return true;
        }

        synchronized DirtyAge dirtyAge(long nowMillis) {
            requireClockEncoding(nowMillis);
            if (retired) {
                return new DirtyAge(dirtyCount, OptionalLong.empty(), false, true);
            }
            observeDirtyClock(nowMillis);
            boolean unavailable = dirtyClockRegressed || nowMillis == -1;
            long oldest = -1;
            for (var owner : accounts.values()) {
                if (owner.dirty != null) {
                    if (owner.dirtySince < 0 || nowMillis < owner.dirtySince) {
                        unavailable = true;
                    } else if (!dirtyClockRegressed && nowMillis >= 0) {
                        oldest = Math.max(oldest, nowMillis - owner.dirtySince);
                    }
                }
            }
            return new DirtyAge(dirtyCount,
                    oldest < 0 ? OptionalLong.empty() : OptionalLong.of(oldest),
                    oldest >= limits.oldestDirtyWarnMillis(), unavailable);
        }

        synchronized Optional<WaitingReservation> tryAcquireWaiting(ConnectionOwner owner) {
            if (retired || owner == null || owner.resources != this || owner.finished) {
                return Optional.empty();
            }
            var existing = waiting.get(owner.connectionId);
            if (existing != null) {
                return existing.owner == owner ? Optional.of(existing) : Optional.empty();
            }
            if (waiting.size() >= limits.maxWaitingConnections()) {
                return Optional.empty();
            }
            var reservation = new WaitingReservation(owner);
            waiting.put(owner.connectionId, reservation);
            return Optional.of(reservation);
        }

        synchronized boolean releaseWaiting(WaitingReservation reservation) {
            if (retired || reservation == null || reservation.owner.resources != this
                    || waiting.get(reservation.owner.connectionId) != reservation) {
                return false;
            }
            waiting.remove(reservation.owner.connectionId);
            reservation.owner.finished = true;
            return true;
        }

        synchronized Optional<SealedReservation> tryReserveSealed(AccountOwner owner, long bytes) {
            if (bytes <= 0) {
                throw new IllegalArgumentException("sealed bytes must be positive");
            }
            if (!owns(owner) || owner.roots[Root.WRITE.ordinal()] == null
                    || sealed.size() >= limits.maxSealedSnapshots()
                    || sealedBytes > limits.maxSealedBytes()
                    || bytes > limits.maxSealedBytes() - sealedBytes) {
                return Optional.empty();
            }
            var reservation = new SealedReservation(owner, bytes);
            sealed.add(reservation);
            sealedBytes += bytes;
            owner.sealedCount++;
            return Optional.of(reservation);
        }

        synchronized boolean queue(SealedReservation reservation) {
            if (!owns(reservation) || reservation.state != SealState.RESERVED) {
                return false;
            }
            reservation.state = SealState.QUEUED;
            return true;
        }

        synchronized boolean start(SealedReservation reservation) {
            if (!owns(reservation) || reservation.state != SealState.QUEUED || inFlight != null) {
                return false;
            }
            reservation.state = SealState.IN_FLIGHT;
            inFlight = reservation;
            return true;
        }

        /** Budget release alone does not clear dirty or establish a physical writer outcome. */
        synchronized boolean releaseSealed(SealedReservation reservation) {
            if (!owns(reservation)) {
                return false;
            }
            sealed.remove(reservation);
            sealedBytes -= reservation.bytes;
            reservation.owner.sealedCount--;
            if (inFlight == reservation) {
                inFlight = null;
            }
            reservation.state = SealState.RELEASED;
            return true;
        }

        synchronized ResourceCounts counts() {
            return new ResourceCounts(accounts.size(), waiting.size(), sealed.size(), sealedBytes,
                    inFlight == null ? 0 : 1, dirtyCount);
        }

        /** Slot stop revokes authority, not obligations; bounded scalar evidence remains readable. */
        synchronized boolean retireSlot() {
            if (retired) {
                return false;
            }
            retired = true;
            return true;
        }

        /** Creates only an empty same-domain target; it does not import asserted counts. */
        synchronized Resources newTransferTarget(P11StartupLimits targetLimits) {
            requireActive();
            return new Resources(targetLimits, domain);
        }

        /**
         * Moves genuine retained reservations, including over-budget ones, without minting more.
         * Not wired to config reload, world loading, or live capacity changes in this slice.
         */
        boolean transferTo(Resources target) {
            if (target == null || target == this) {
                return false;
            }
            // Transfer is the only two-ledger operation. No callback or external work is invoked.
            synchronized (Resources.class) {
                synchronized (this) {
                    synchronized (target) {
                        if (retired || target.retired || domain != target.domain
                                || !target.accounts.isEmpty() || !target.waiting.isEmpty()
                                || !target.sealed.isEmpty()) {
                            return false;
                        }
                        target.accounts.putAll(accounts);
                        target.waiting.putAll(waiting);
                        target.sealed.addAll(sealed);
                        for (var owner : accounts.values()) {
                            owner.resources = target;
                        }
                        for (var reservation : waiting.values()) {
                            reservation.owner.resources = target;
                        }
                        target.sealedBytes = sealedBytes;
                        target.inFlight = inFlight;
                        target.dirtyCount = dirtyCount;
                        target.lastDirtyClock = lastDirtyClock;
                        target.dirtyClockRegressed = dirtyClockRegressed;
                        accounts.clear();
                        waiting.clear();
                        sealed.clear();
                        sealedBytes = 0;
                        inFlight = null;
                        dirtyCount = 0;
                        retired = true;
                        return true;
                    }
                }
            }
        }

        private boolean issued(AccountOwner owner) {
            return !retired && owner != null && owner.resources == this;
        }

        private boolean owns(AccountOwner owner) {
            return issued(owner) && accounts.get(owner.playerId) == owner;
        }

        private boolean owns(RootReservation reservation) {
            return reservation != null && owns(reservation.owner)
                    && reservation.owner.roots[reservation.root.ordinal()] == reservation;
        }

        private boolean owns(SealedReservation reservation) {
            return reservation != null && owns(reservation.owner) && sealed.contains(reservation);
        }

        private void clearRoot(RootReservation reservation) {
            var owner = reservation.owner;
            owner.roots[reservation.root.ordinal()] = null;
            for (var remaining : owner.roots) {
                if (remaining != null) {
                    return;
                }
            }
            accounts.remove(owner.playerId);
            owner.finished = true;
        }

        private void observeDirtyClock(long nowMillis) {
            if (nowMillis >= 0) {
                if (nowMillis < lastDirtyClock) {
                    dirtyClockRegressed = true;
                } else {
                    lastDirtyClock = nowMillis;
                }
            }
        }

        private void requireActive() {
            if (retired) {
                throw new IllegalStateException("resource owner retired");
            }
        }

        private static final class Domain { }

        static final class AccountOwner {
            private Resources resources;
            private final UUID playerId;
            private final RootReservation[] roots = new RootReservation[Root.values().length];
            private DirtyObservation dirty;
            private long dirtySince;
            private int sealedCount;
            private boolean finished;

            private AccountOwner(Resources resources, UUID playerId) {
                this.resources = resources;
                this.playerId = playerId;
            }
        }

        static final class ConnectionOwner {
            private Resources resources;
            private final long connectionId;
            private boolean finished;

            private ConnectionOwner(Resources resources, long connectionId) {
                this.resources = resources;
                this.connectionId = connectionId;
            }
        }

        static final class RootReservation {
            private final AccountOwner owner;
            private final Root root;

            private RootReservation(AccountOwner owner, Root root) {
                this.owner = owner;
                this.root = root;
            }
        }

        static final class DirtyObservation {
            private final AccountOwner owner;

            private DirtyObservation(AccountOwner owner) {
                this.owner = owner;
            }
        }

        static final class WaitingReservation {
            private final ConnectionOwner owner;

            private WaitingReservation(ConnectionOwner owner) {
                this.owner = owner;
            }
        }

        static final class SealedReservation {
            private final AccountOwner owner;
            private final long bytes;
            private SealState state = SealState.RESERVED;

            private SealedReservation(AccountOwner owner, long bytes) {
                this.owner = owner;
                this.bytes = bytes;
            }
        }
    }

    private static void requireClockEncoding(long millis) {
        if (millis < -1) {
            throw new IllegalArgumentException("clock must be nonnegative or unknown (-1)");
        }
    }
}
