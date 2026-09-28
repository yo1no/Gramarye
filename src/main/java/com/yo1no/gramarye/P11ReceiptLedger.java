package com.yo1no.gramarye;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

/**
 * Bounded, slot-owned observations only. This ledger does not choose a qualified source,
 * authorize a cast, write data, clear journals, resume native callers, or open sessions.
 * Native producers supply observations through source/material and physical-source receipts.
 * Isolated tests certify transitions, never Minecraft durability.
 */
final class P11ReceiptLedger {
    enum Observation { NOT_OBSERVED, SUCCEEDED, FAILED, UNKNOWN }
    enum MaterialStep { CONSTRUCTOR, REQUIRED_COPY_OR_LOAD, REQUIRED_CALLER_CONSUMERS, OWNED_DATA }
    enum MembershipStep { WORLD, ROSTER, UUID_LOOKUP, ENTITY_ID_LOOKUP, LISTENER }
    enum ReadinessStep { NATIVE_SYNC, NATIVE_CALLBACK, P4_PUBLICATION, E2, C, P7_SESSION, P7_INITIAL_SYNC }
    enum WriterStep { WRITE, CLOSE, REPLACE, PERSISTED_READBACK }
    enum WriterKind { PLAYER_DATA, LEVEL_PLAYER, STATISTICS, ADVANCEMENTS, CACHE }
    enum PhysicalStep { ENCODE, WRITE, CLOSE, REPLACE, CACHE_ASSIGNMENT }
    enum Disposition { CANDIDATE, LIVE, DETACHED, UNKNOWN }
    enum OperationKind { DEATH, END, JOIN, RETURN_TO_WORLD, ENTER_CONFIG }
    enum Terminal { COMPLETED, FAILED, UNKNOWN, RETIRED }
    enum Change { RECORDED, DUPLICATE, REFUSED, STALE, TERMINAL, EXHAUSTED }

    private final P11IdentityOwner identities;
    private final P11IdentityOwner.ReceiptDomain domain;
    private final Map<P11IdentityOwner.AccountKey, Entry> entries = new HashMap<>();
    private final LongSupplier monotonicMillis;
    private final long observationStartedMillis;
    private final SuccessCount[] physicalSuccesses = new SuccessCount[WriterKind.values().length];
    private long lastObservedMillis = -1;
    private boolean observationClockRegressed;
    private SaveProgress stoppedProgress;
    private boolean stopped;

    P11ReceiptLedger(P11IdentityOwner identities) {
        this(identities, elapsedClock());
    }

    /** Package-private controlled clock seam; time is observation only, never save authority. */
    P11ReceiptLedger(P11IdentityOwner identities, LongSupplier monotonicMillis) {
        this.identities = Objects.requireNonNull(identities, "identities");
        this.monotonicMillis = Objects.requireNonNull(monotonicMillis, "monotonicMillis");
        this.domain = identities.claimReceiptDomain();
        Arrays.fill(physicalSuccesses, new SuccessCount(0, false));
        observationStartedMillis = observationMillis();
    }

    synchronized Optional<Source> firstSource(
            P11IdentityOwner.CapturedIdentity identity, Disposition disposition) {
        Objects.requireNonNull(disposition, "disposition");
        if (stopped || !identities.current(identity) || entries.containsKey(identity.account())) {
            return Optional.empty();
        }
        var epoch = identities.nextSourceEpoch(domain, identity);
        if (epoch.isEmpty()) {
            return Optional.empty();
        }
        identities.revokeDischarge(domain, identity.account());
        var source = new Source(identity, epoch.getAsLong(), 0, disposition);
        entries.put(identity.account(), new Entry(source));
        return Optional.of(source);
    }

    /** Native source custody is deliberately not an authenticated connection binding. */
    synchronized Optional<Source> firstSource(
            P11IdentityOwner.DataIdentity identity, Disposition disposition) {
        Objects.requireNonNull(disposition, "disposition");
        if (stopped || !identities.ownsData(identity) || entries.containsKey(identity.account())) {
            return Optional.empty();
        }
        var epoch = identities.nextDataSourceEpoch(domain, identity);
        if (epoch.isEmpty()) { return Optional.empty(); }
        var source = new Source(identity, epoch.getAsLong(), 0, disposition);
        entries.put(source.account(), new Entry(source));
        return Optional.of(source);
    }

    synchronized Optional<MaterialReceipt> beginMaterial(Source source) {
        var entry = matching(source);
        if (entry == null || source.dataIdentity == null
                || !identities.ownsData(source.dataIdentity) || entry.materialReceipt != null) {
            return Optional.empty();
        }
        var receipt = new MaterialReceipt(domain, source, null);
        entry.materialReceipt = receipt;
        return Optional.of(receipt);
    }

    /** Reserve a candidate without replacing the current complete source or its dirty duty. */
    synchronized Optional<MaterialReceipt> beginHandoff(Source expected,
            P11IdentityOwner.DataIdentity candidate, Disposition disposition) {
        Objects.requireNonNull(disposition, "disposition");
        var entry = matching(expected);
        if (entry == null || expected.dataIdentity == null || !completeMaterial(entry)
                || !identities.ownsData(candidate) || candidate == expected.dataIdentity
                || candidate.account() != expected.account() || entry.candidate != null) {
            return Optional.empty();
        }
        var epoch = identities.nextDataSourceEpoch(domain, candidate);
        if (epoch.isEmpty()) { return Optional.empty(); }
        var source = new Source(candidate, epoch.getAsLong(), 0, disposition);
        var receipt = new MaterialReceipt(domain, source, expected);
        entry.candidate = receipt;
        return Optional.of(receipt);
    }

    synchronized Change material(MaterialReceipt receipt, MaterialStep step, Observation observed) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(observed, "observed");
        var entry = materialEntry(receipt);
        if (entry == null) { return Change.STALE; }
        if (receipt.terminal != null) { return Change.TERMINAL; }
        var change = observe(receipt.facts, step.ordinal(), observed);
        if (entry.materialReceipt == receipt) {
            entry.material[step.ordinal()] = receipt.facts[step.ordinal()];
        }
        return change;
    }

    synchronized Change finishMaterial(MaterialReceipt receipt, Terminal terminal) {
        Objects.requireNonNull(terminal, "terminal");
        var entry = materialEntry(receipt);
        if (entry == null) { return Change.STALE; }
        if (receipt.terminal != null) {
            return receipt.terminal == terminal ? Change.DUPLICATE : Change.TERMINAL;
        }
        if (terminal == Terminal.COMPLETED && !allSucceeded(receipt.facts)) {
            return Change.REFUSED;
        }
        receipt.terminal = terminal;
        return Change.RECORDED;
    }

    /**
     * The exact selected-source receipt and successful candidate observations are the handoff
     * proof. Dirty source obligations move intact; no save or readback is fabricated here.
     */
    synchronized Optional<Source> publishHandoff(MaterialReceipt receipt) {
        var entry = materialEntry(receipt);
        if (entry == null || entry.candidate != receipt || entry.source != receipt.expected
                || receipt.terminal != Terminal.COMPLETED || !allSucceeded(receipt.facts)
                || !identities.commitSourceCustody(domain,
                        receipt.expected.dataIdentity, receipt.source.dataIdentity)) {
            return Optional.empty();
        }
        entry.source = receipt.source;
        entry.materialReceipt = receipt;
        entry.candidate = null;
        System.arraycopy(receipt.facts, 0, entry.material, 0, entry.material.length);
        Arrays.fill(entry.membership, Observation.NOT_OBSERVED);
        Arrays.fill(entry.readiness, Observation.NOT_OBSERVED);
        entry.clearWriterFacts();
        entry.operation = null;
        entry.writer = null;
        entry.dirty = true;
        // Each original physical owner still owes the newly installed body. Retained attempts
        // may finish their own facts, but their captured old epoch cannot clear this obligation.
        for (var obligation : entry.physical.values()) { obligation.dirty = true; }
        observeAllDirty(entry);
        return Optional.of(entry.source);
    }

    synchronized Change discardFailedCandidate(MaterialReceipt receipt) {
        var entry = materialEntry(receipt);
        if (entry == null || entry.candidate != receipt) { return Change.STALE; }
        if (receipt.terminal == null || receipt.terminal == Terminal.COMPLETED) {
            return Change.REFUSED;
        }
        // The native source owner must retain any incident / partial-B material separately.
        // This releases only the failed candidate's opaque identity admission, never A's duty.
        if (!identities.releaseCandidateCustody(domain, receipt.source.dataIdentity)) {
            return Change.STALE;
        }
        entry.candidate = null;
        return Change.RECORDED;
    }

    synchronized Optional<Source> changeSource(Source expected,
            P11IdentityOwner.CapturedIdentity next, Disposition disposition) {
        Objects.requireNonNull(disposition, "disposition");
        var entry = matching(expected);
        if (entry == null || expected.identity == null || !identities.current(next)
                || expected.identity.account() != next.account()
                || !dischargedFacts(entry)) {
            return Optional.empty();
        }
        var epoch = identities.nextSourceEpoch(domain, next);
        if (epoch.isEmpty()) {
            return Optional.empty();
        }
        // The legacy connection-observation path has no material handoff proof. Native source
        // custody uses publishHandoff instead; an equal account alone never erases obligations.
        var source = new Source(next, epoch.getAsLong(), 0, disposition);
        entries.put(next.account(), new Entry(source));
        return Optional.of(source);
    }

    synchronized Optional<Source> advanceMutationVersion(Source expected) {
        var entry = sourceEntry(expected);
        if (entry == null || expected.version == Long.MAX_VALUE) {
            return Optional.empty();
        }
        // Contextual material/membership/readiness belong to the exact operation, not v.
        // Only physical write/readback observations require the new exact data version.
        var source = expected.dataIdentity == null
                ? new Source(expected.identity, expected.epoch, expected.version + 1, expected.disposition)
                : new Source(expected.dataIdentity, expected.epoch, expected.version + 1, expected.disposition);
        if (entry.source == expected) {
            entry.source = source;
            if (entry.materialReceipt != null) { entry.materialReceipt.source = source; }
        } else {
            entry.candidate.source = source;
        }
        entry.dirty = true;
        entry.clearWriterFacts();
        for (var obligation : entry.physical.values()) { obligation.dirty = true; }
        observeAllDirty(entry);
        return Optional.of(source);
    }

    synchronized Optional<OperationReceipt> begin(
            Source source, long scene, long request, OperationKind kind) {
        Objects.requireNonNull(kind, "kind");
        var entry = matching(source);
        if (entry == null || scene <= 0 || request < 0
                || source.identity == null || !identities.current(source.identity)
                || (request == 0 && (kind == OperationKind.DEATH || kind == OperationKind.END))
                || (entry.operation != null
                        && (entry.operation.terminal != Terminal.COMPLETED
                                || !allSucceeded(entry.material)))
                || (entry.writer != null && entry.writer.terminal != Terminal.COMPLETED)) {
            return Optional.empty();
        }
        // Facts belong to this operation, not merely to equal source coordinates. Only resolved
        // prior receipts can be replaced; any outstanding data obligation remains entry.dirty.
        entry.clearFacts();
        entry.writer = null;
        var receipt = new OperationReceipt(source, scene, request, kind);
        entry.operation = receipt;
        return Optional.of(receipt);
    }

    synchronized Change material(OperationReceipt receipt, MaterialStep step, Observation observed) {
        return record(receipt, Objects.requireNonNull(step, "step").ordinal(), observed, 0);
    }

    synchronized Change membership(
            OperationReceipt receipt, MembershipStep step, Observation observed) {
        return record(receipt, Objects.requireNonNull(step, "step").ordinal(), observed, 1);
    }

    synchronized Change readiness(
            OperationReceipt receipt, ReadinessStep step, Observation observed) {
        return record(receipt, Objects.requireNonNull(step, "step").ordinal(), observed, 2);
    }

    private Change record(OperationReceipt receipt, int index, Observation observed, int category) {
        Objects.requireNonNull(observed, "observed");
        var entry = matchingOperation(receipt);
        if (entry == null || !sameContext(receipt.source, entry.source)) {
            return Change.STALE;
        }
        if (receipt.terminal != null) {
            return Change.TERMINAL;
        }
        var facts = switch (category) {
            case 0 -> entry.material;
            case 1 -> entry.membership;
            case 2 -> entry.readiness;
            default -> throw new IllegalStateException("unknown fact category");
        };
        return observe(facts, index, observed);
    }

    synchronized Change finish(OperationReceipt receipt, Terminal terminal) {
        Objects.requireNonNull(terminal, "terminal");
        var entry = matchingOperation(receipt);
        if (entry == null) {
            return Change.STALE;
        }
        if (receipt.terminal != null) {
            return receipt.terminal == terminal ? Change.DUPLICATE : Change.TERMINAL;
        }
        // Same-context mutation-version changes do not cancel this exact operation.
        // Its completion is not a physical writer result for the newer version.
        receipt.terminal = terminal;
        return sameContext(receipt.source, entry.source) ? Change.RECORDED : Change.STALE;
    }

    synchronized Optional<WriterReceipt> beginWriter(OperationReceipt operation) {
        var entry = matchingOperation(operation);
        if (entry == null || operation.source != entry.source
                || operation.terminal != Terminal.COMPLETED || !allSucceeded(entry.material)
                || (entry.writer != null && (entry.writer.source == entry.source
                        || entry.writer.terminal == null))) {
            return Optional.empty();
        }
        var writer = new WriterReceipt(operation.source, operation);
        entry.writer = writer;
        return Optional.of(writer);
    }

    synchronized Change writer(
            WriterReceipt receipt, WriterStep step, Observation observed) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(observed, "observed");
        var entry = matchingWriter(receipt);
        if (entry == null || receipt.source != entry.source) {
            return Change.STALE;
        }
        if (receipt.terminal != null) {
            return Change.TERMINAL;
        }
        int index = step.ordinal();
        if (observed == Observation.SUCCEEDED) {
            for (int before = 0; before < index; before++) {
                if (entry.writerFacts[before] != Observation.SUCCEEDED) {
                    return Change.REFUSED;
                }
            }
        }
        return observe(entry.writerFacts, index, observed);
    }

    synchronized Change finishWriter(WriterReceipt receipt, Terminal terminal) {
        Objects.requireNonNull(terminal, "terminal");
        var entry = matchingWriter(receipt);
        if (entry == null) {
            return Change.STALE;
        }
        if (receipt.terminal != null) {
            return receipt.terminal == terminal ? Change.DUPLICATE : Change.TERMINAL;
        }
        if (receipt.source != entry.source) {
            receipt.terminal = terminal;
            return Change.STALE;
        }
        if (terminal == Terminal.COMPLETED && !allSucceeded(entry.writerFacts)) {
            return Change.REFUSED;
        }
        receipt.terminal = terminal;
        if (terminal == Terminal.COMPLETED) {
            entry.dirty = false;
        }
        return Change.RECORDED;
    }

    /** A legal native save subscope may run before the outer logout/operation is terminal. */
    synchronized Optional<PhysicalWriterReceipt> beginSave(Source source, WriterKind kind) {
        Objects.requireNonNull(kind, "kind");
        var entry = matching(source);
        if (entry == null || source.dataIdentity == null || !completeMaterial(entry)
                || !identities.ownsData(source.dataIdentity)) {
            return Optional.empty();
        }
        return beginPhysical(entry, source, kind, null);
    }

    /** A canonical stats/PA observation is not a whole-player material witness. */
    synchronized Optional<IndependentMaterialReceipt> beginIndependentMaterial(Source source, WriterKind kind) {
        Objects.requireNonNull(kind, "kind");
        var entry = sourceEntry(source);
        if (entry == null || source.dataIdentity == null || !identities.ownsData(source.dataIdentity)
                || (kind != WriterKind.STATISTICS && kind != WriterKind.ADVANCEMENTS)) {
            return Optional.empty();
        }
        var obligation = entry.physical.computeIfAbsent(kind, ignored -> newPhysicalObligation(entry, kind));
        if (obligation.material != null && obligation.material.source == source
                && obligation.material.outcome == Observation.NOT_OBSERVED) {
            return Optional.empty();
        }
        var receipt = new IndependentMaterialReceipt(domain, source, kind);
        obligation.material = receipt;
        return Optional.of(receipt);
    }

    synchronized Change independentMaterial(IndependentMaterialReceipt receipt, Observation observation) {
        Objects.requireNonNull(observation, "observation");
        if (!currentIndependentMaterial(receipt)) { return Change.STALE; }
        if (observation == Observation.NOT_OBSERVED) { return Change.REFUSED; }
        if (receipt.outcome != Observation.NOT_OBSERVED) {
            return receipt.outcome == observation ? Change.DUPLICATE : Change.TERMINAL;
        }
        receipt.outcome = observation;
        return Change.RECORDED;
    }

    synchronized Optional<PhysicalWriterReceipt> beginIndependentSave(IndependentMaterialReceipt material) {
        if (!currentIndependentMaterial(material) || material.outcome != Observation.SUCCEEDED) {
            return Optional.empty();
        }
        return beginPhysical(sourceEntry(material.source), material.source, material.kind, material);
    }

    private Optional<PhysicalWriterReceipt> beginPhysical(Entry entry, Source source,
            WriterKind kind, IndependentMaterialReceipt material) {
        var obligation = entry.physical.computeIfAbsent(kind, ignored -> newPhysicalObligation(entry, kind));
        if (obligation.attempt != null && obligation.attempt.terminal == null) {
            return Optional.empty();
        }
        if (obligation.generation == Long.MAX_VALUE) {
            obligation.dirty = true;
            observeDirty(entry, kind);
            return Optional.empty();
        }
        obligation.generation++;
        identities.revokeDischarge(domain, source.account());
        obligation.dirty = true;
        observeDirty(entry, kind);
        var receipt = new PhysicalWriterReceipt(domain, source, kind, obligation.generation, material);
        obligation.attempt = receipt;
        return Optional.of(receipt);
    }

    synchronized Change markDirty(Source source, WriterKind kind) {
        Objects.requireNonNull(kind, "kind");
        var entry = sourceEntry(source);
        if (entry == null || source.dataIdentity == null) { return Change.STALE; }
        var obligation = entry.physical.computeIfAbsent(kind, ignored -> newPhysicalObligation(entry, kind));
        obligation.dirty = true;
        observeDirty(entry, kind);
        if (kind == WriterKind.PLAYER_DATA || kind == WriterKind.LEVEL_PLAYER) { entry.dirty = true; }
        identities.revokeDischarge(domain, source.account());
        if (obligation.generation == Long.MAX_VALUE) { return Change.EXHAUSTED; }
        obligation.generation++;
        return Change.RECORDED;
    }

    /** The native writer checks this again immediately before its physical replacement. */
    synchronized boolean mayWrite(PhysicalWriterReceipt receipt) {
        if (!currentPhysicalSource(receipt)) { return false; }
        for (var observation : receipt.facts) {
            if (observation == Observation.FAILED || observation == Observation.UNKNOWN) {
                return false;
            }
        }
        return true;
    }

    private boolean currentPhysicalSource(PhysicalWriterReceipt receipt) {
        var entry = physicalEntry(receipt);
        if (entry == null || receipt.terminal != null || sourceEntry(receipt.source) != entry
                || !identities.ownsData(receipt.source.dataIdentity)
                || entry.physical.get(receipt.kind).generation != receipt.materialVersion) {
            return false;
        }
        return receipt.material == null
                ? entry.source == receipt.source && completeMaterial(entry)
                : currentIndependentMaterial(receipt.material)
                        && receipt.material.outcome == Observation.SUCCEEDED;
    }

    synchronized Change physical(PhysicalWriterReceipt receipt,
            PhysicalStep step, Observation observed) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(observed, "observed");
        if (!ownPhysical(receipt)) { return Change.STALE; }
        if (receipt.terminal != null) { return Change.TERMINAL; }
        if (!required(receipt.kind, step)) { return Change.REFUSED; }
        // A failed write may still close successfully in the original finally. Record each
        // true outcome; qualification below still requires every source-specific success.
        var change = observe(receipt.facts, step.ordinal(), observed);
        // Even stale in-flight work keeps its own immutable-at-terminal facts. It never gains
        // current write permission, nor may these facts release the newer version's duty.
        return currentPhysicalSource(receipt) ? change : Change.STALE;
    }

    synchronized Change finishSave(PhysicalWriterReceipt receipt, Terminal terminal) {
        Objects.requireNonNull(terminal, "terminal");
        if (!ownPhysical(receipt)) { return Change.STALE; }
        if (receipt.terminal != null) {
            return receipt.terminal == terminal ? Change.DUPLICATE : Change.TERMINAL;
        }
        if (terminal == Terminal.COMPLETED && !physicalComplete(receipt)) {
            return Change.REFUSED;
        }
        boolean current = currentPhysicalSource(receipt);
        receipt.terminal = terminal;
        // A true old-version IO success is still observed once, but never discharges newer
        // data. Cache assignment, attempt starts, failures and mere void return are not IO.
        if (terminal == Terminal.COMPLETED && receipt.kind != WriterKind.CACHE) {
            int index = receipt.kind.ordinal();
            physicalSuccesses[index] = physicalSuccesses[index].increment();
        }
        if (current && terminal == Terminal.COMPLETED) {
            var entry = entries.get(receipt.source.account());
            entry.physical.get(receipt.kind).dirty = false;
            // A's independent writer can really finish while B is pending. That success
            // cannot erase B's already-observed duty; publication will dirty the same kind,
            // and the next current completion after handoff can end that observation.
            if (entry.candidate == null) {
                entry.observedDirtySince[receipt.kind.ordinal()] = OBSERVED_CLEAN;
            }
            if (receipt.kind == WriterKind.PLAYER_DATA || receipt.kind == WriterKind.LEVEL_PLAYER) {
                entry.dirty = false;
            }
        }
        return current ? Change.RECORDED : Change.STALE;
    }

    synchronized Optional<PhysicalFacts> physicalFacts(Source source, WriterKind kind) {
        Objects.requireNonNull(kind, "kind");
        var entry = sourceEntry(source);
        if (entry == null) { return Optional.empty(); }
        var obligation = entry.physical.get(kind);
        return obligation == null ? Optional.empty()
                : Optional.of(new PhysicalFacts(kind, obligation.generation, obligation.dirty,
                        obligation.attempt));
    }

    synchronized Optional<PhysicalFacts> attemptFacts(PhysicalWriterReceipt receipt) {
        if (!ownPhysical(receipt)) { return Optional.empty(); }
        return Optional.of(new PhysicalFacts(receipt.kind, receipt.materialVersion,
                receipt.terminal != Terminal.COMPLETED, receipt));
    }

    /** Later native read outcome is separate from writing, cache assignment and value equality. */
    synchronized Optional<ReadbackReceipt> beginReadback(Source source, WriterKind kind) {
        Objects.requireNonNull(kind, "kind");
        var entry = matching(source);
        if (entry == null || source.dataIdentity == null || kind == WriterKind.CACHE
                || !completeMaterial(entry)) {
            return Optional.empty();
        }
        var obligation = entry.physical.computeIfAbsent(kind, ignored -> newPhysicalObligation(entry, kind));
        if (obligation.readback != null && obligation.readback.outcome == Observation.NOT_OBSERVED) {
            return Optional.empty();
        }
        var receipt = new ReadbackReceipt(domain, source, kind, obligation.generation);
        obligation.readback = receipt;
        return Optional.of(receipt);
    }

    synchronized Change finishReadback(ReadbackReceipt receipt, Observation observed) {
        Objects.requireNonNull(observed, "observed");
        if (!ownReadback(receipt)) { return Change.STALE; }
        if (observed == Observation.NOT_OBSERVED) { return Change.REFUSED; }
        if (receipt.outcome != Observation.NOT_OBSERVED) {
            return receipt.outcome == observed ? Change.DUPLICATE : Change.TERMINAL;
        }
        receipt.outcome = observed;
        return currentReadbackSource(receipt) ? Change.RECORDED : Change.STALE;
    }

    synchronized boolean persistedReadbackCurrent(ReadbackReceipt receipt) {
        return ownReadback(receipt) && receipt.outcome == Observation.SUCCEEDED
                && currentReadbackSource(receipt);
    }

    synchronized Optional<Facts> facts(Source source) {
        var entry = matching(source);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(new Facts(entry.material, entry.membership, entry.readiness,
                entry.writerFacts, entry.dirty || entry.physical.values().stream().anyMatch(value -> value.dirty),
                entry.operation == null ? null : entry.operation.terminal,
                entry.writer == null || entry.writer.source != entry.source
                        ? null : entry.writer.terminal));
    }

    synchronized int retainedSources() {
        return entries.size();
    }

    synchronized Optional<Discharge> dischargeUnusedAccount(P11IdentityOwner.AccountKey account) {
        if (stopped || !identities.owns(account) || entries.containsKey(account)) {
            return Optional.empty();
        }
        var discharge = new Discharge(domain, account);
        return identities.installDischarge(discharge) ? Optional.of(discharge) : Optional.empty();
    }

    synchronized Optional<Discharge> discharge(Source source) {
        var entry = matching(source);
        if (entry == null || !dischargedFacts(entry)) {
            return Optional.empty();
        }
        entries.remove(source.account());
        var discharge = new Discharge(domain, source.account());
        return identities.installDischarge(discharge) ? Optional.of(discharge) : Optional.empty();
    }

    synchronized void stop() {
        if (stopped) { return; }
        stoppedProgress = saveProgress();
        stopped = true;
        entries.clear();
    }

    /** Records when the native source owner first actually acquires a required writer duty.
     * It does not create physical receipts/obligations or change any qualification predicate. */
    synchronized void observeRequiredWriter(Source source, WriterKind kind) {
        Objects.requireNonNull(kind, "kind");
        var entry = sourceEntry(source);
        if (entry != null) { observeDirty(entry, kind); }
    }

    /** Fixed five-kind scalar snapshot; no attempts, actors, roots, or exception graphs escape. */
    synchronized SaveProgress saveProgress() {
        if (stopped) { return stoppedProgress; }
        long now = observationMillis();
        var elapsed = now >= 0 && observationStartedMillis >= 0 && now >= observationStartedMillis
                ? OptionalLong.of(now - observationStartedMillis) : OptionalLong.empty();
        var kinds = new ArrayList<KindProgress>(WriterKind.values().length);
        for (var kind : WriterKind.values()) {
            int dirtySources = 0;
            long oldest = 0;
            boolean unavailable = false;
            for (var entry : entries.values()) {
                long since = entry.observedDirtySince[kind.ordinal()];
                if (since == UNOBSERVED_DIRTY || since == OBSERVED_CLEAN) { continue; }
                dirtySources++;
                if (since < 0 || now < 0 || now < since) { unavailable = true; }
                else { oldest = Math.max(oldest, now - since); }
            }
            var age = dirtySources > 0 && !unavailable ? OptionalLong.of(oldest) : OptionalLong.empty();
            var successes = physicalSuccesses[kind.ordinal()];
            kinds.add(new KindProgress(kind, dirtySources, age, unavailable,
                    successes.value(), successes.saturated()));
        }
        return new SaveProgress(elapsed, kinds);
    }

    record KindProgress(WriterKind kind, int dirtySources, OptionalLong oldestDirtyMillis,
            boolean ageUnavailable, long successfulPhysicalWrites, boolean counterSaturated) {}

    record SaveProgress(OptionalLong elapsedMillis, List<KindProgress> kinds) {
        SaveProgress { kinds = List.copyOf(kinds); }
    }

    record SuccessCount(long value, boolean saturated) {
        SuccessCount {
            if (value < 0) { throw new IllegalArgumentException("negative success count"); }
        }
        SuccessCount increment() {
            return value == Long.MAX_VALUE ? new SuccessCount(value, true)
                    : new SuccessCount(value + 1, saturated);
        }
    }

    private static final long UNOBSERVED_DIRTY = -2;
    private static final long OBSERVED_CLEAN = -3;

    private PhysicalObligation newPhysicalObligation(Entry entry, WriterKind kind) {
        observeDirty(entry, kind);
        return new PhysicalObligation();
    }

    private void observeDirty(Entry entry, WriterKind kind) {
        long now = observationMillis();
        int index = kind.ordinal();
        long prior = entry.observedDirtySince[index];
        if (prior == UNOBSERVED_DIRTY || prior == OBSERVED_CLEAN) {
            entry.observedDirtySince[index] = now;
        }
    }

    private void observeAllDirty(Entry entry) {
        for (var kind : WriterKind.values()) {
            if (entry.observedDirtySince[kind.ordinal()] != UNOBSERVED_DIRTY
                    || entry.physical.containsKey(kind)) { observeDirty(entry, kind); }
        }
    }

    private long observationMillis() {
        long now = monotonicMillis.getAsLong();
        if (now < 0 || observationClockRegressed) { return -1; }
        if (lastObservedMillis >= 0 && now < lastObservedMillis) {
            observationClockRegressed = true;
            return -1;
        }
        lastObservedMillis = now;
        return now;
    }

    private static LongSupplier elapsedClock() {
        long origin = System.nanoTime();
        return () -> {
            long elapsed = System.nanoTime() - origin;
            return elapsed < 0 ? -1 : elapsed / 1_000_000L;
        };
    }

    private Entry matching(Source source) {
        if (stopped || source == null || !identities.owns(source.account())) {
            return null;
        }
        var entry = entries.get(source.account());
        return entry != null && entry.source == source ? entry : null;
    }

    private Entry sourceEntry(Source source) {
        if (stopped || source == null || !identities.owns(source.account())) { return null; }
        var entry = entries.get(source.account());
        return entry != null && (entry.source == source
                || (entry.candidate != null && entry.candidate.source == source)) ? entry : null;
    }

    private Entry matchingOperation(OperationReceipt receipt) {
        if (stopped || receipt == null || !identities.owns(receipt.source.account())) {
            return null;
        }
        var entry = entries.get(receipt.source.account());
        return entry != null && entry.operation == receipt ? entry : null;
    }

    private Entry matchingWriter(WriterReceipt receipt) {
        if (stopped || receipt == null || !identities.owns(receipt.source.account())) {
            return null;
        }
        var entry = entries.get(receipt.source.account());
        return entry != null && entry.writer == receipt ? entry : null;
    }

    private Entry materialEntry(MaterialReceipt receipt) {
        if (stopped || receipt == null || receipt.domain != domain
                || !identities.ownsData(receipt.source.dataIdentity)) { return null; }
        var entry = entries.get(receipt.source.account());
        if (entry == null) { return null; }
        if (entry.materialReceipt == receipt && sameContext(receipt.source, entry.source)) {
            return entry;
        }
        return entry.candidate == receipt ? entry : null;
    }

    private static boolean completeMaterial(Entry entry) {
        return entry.materialReceipt != null
                && entry.materialReceipt.terminal == Terminal.COMPLETED
                && sameContext(entry.materialReceipt.source, entry.source)
                && allSucceeded(entry.material);
    }

    private boolean ownPhysical(PhysicalWriterReceipt receipt) {
        return !stopped && receipt != null && receipt.domain == domain
                && identities.owns(receipt.source.account());
    }

    private Entry physicalEntry(PhysicalWriterReceipt receipt) {
        if (!ownPhysical(receipt)) { return null; }
        var entry = entries.get(receipt.source.account());
        if (entry == null) { return null; }
        var obligation = entry.physical.get(receipt.kind);
        return obligation != null && obligation.attempt == receipt ? entry : null;
    }

    private boolean currentIndependentMaterial(IndependentMaterialReceipt receipt) {
        if (stopped || receipt == null || receipt.domain != domain
                || !identities.ownsData(receipt.source.dataIdentity)) { return false; }
        var entry = sourceEntry(receipt.source);
        if (entry == null) { return false; }
        var obligation = entry.physical.get(receipt.kind);
        return obligation != null && obligation.material == receipt;
    }

    private boolean ownReadback(ReadbackReceipt receipt) {
        return !stopped && receipt != null && receipt.domain == domain
                && identities.owns(receipt.source.account());
    }

    private boolean currentReadbackSource(ReadbackReceipt receipt) {
        var entry = matching(receipt.source);
        if (entry == null) { return false; }
        var obligation = entry.physical.get(receipt.kind);
        return obligation != null && obligation.readback == receipt
                && obligation.generation == receipt.materialVersion;
    }

    private static boolean required(WriterKind kind, PhysicalStep step) {
        return switch (kind) {
            case CACHE -> step == PhysicalStep.ENCODE || step == PhysicalStep.CACHE_ASSIGNMENT;
            case STATISTICS, ADVANCEMENTS -> step == PhysicalStep.ENCODE
                    || step == PhysicalStep.WRITE || step == PhysicalStep.CLOSE;
            case PLAYER_DATA, LEVEL_PLAYER -> step != PhysicalStep.CACHE_ASSIGNMENT;
        };
    }

    private static boolean physicalComplete(PhysicalWriterReceipt receipt) {
        for (var step : PhysicalStep.values()) {
            if (required(receipt.kind, step) && receipt.facts[step.ordinal()] != Observation.SUCCEEDED) {
                return false;
            }
        }
        return true;
    }

    private static Change observe(Observation[] facts, int index, Observation observed) {
        if (observed == Observation.NOT_OBSERVED) {
            return Change.REFUSED;
        }
        if (facts[index] == observed) {
            return Change.DUPLICATE;
        }
        if (facts[index] != Observation.NOT_OBSERVED) {
            // Unknown/failure cannot be laundered into success by a late callback.
            return Change.REFUSED;
        }
        facts[index] = observed;
        return Change.RECORDED;
    }

    private static boolean allSucceeded(Observation[] facts) {
        for (var fact : facts) {
            if (fact != Observation.SUCCEEDED) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameContext(Source left, Source right) {
        return left.identity == right.identity && left.dataIdentity == right.dataIdentity
                && left.epoch == right.epoch;
    }

    private static boolean dischargedFacts(Entry entry) {
        if (entry.source.dataIdentity != null) {
            if (entry.dirty || !completeMaterial(entry) || entry.candidate != null
                    || entry.physical.isEmpty()) { return false; }
            for (var obligation : entry.physical.values()) {
                if (obligation.dirty || obligation.attempt == null
                        || obligation.attempt.terminal != Terminal.COMPLETED
                        || obligation.attempt.source != entry.source
                        || (obligation.readback != null
                                && obligation.readback.outcome == Observation.NOT_OBSERVED)) {
                    return false;
                }
            }
            return true;
        }
        return !entry.dirty && entry.operation != null
                && entry.operation.terminal == Terminal.COMPLETED
                && entry.operation.source == entry.source
                && entry.writer != null && entry.writer.terminal == Terminal.COMPLETED
                && entry.writer.source == entry.source
                && entry.writer.operation == entry.operation;
    }

    static final class Source {
        private final P11IdentityOwner.CapturedIdentity identity;
        private final P11IdentityOwner.DataIdentity dataIdentity;
        private final long epoch;
        private final long version;
        private final Disposition disposition;

        private Source(P11IdentityOwner.CapturedIdentity identity,
                long epoch, long version, Disposition disposition) {
            this.identity = identity;
            this.dataIdentity = null;
            this.epoch = epoch;
            this.version = version;
            this.disposition = disposition;
        }

        private Source(P11IdentityOwner.DataIdentity identity,
                long epoch, long version, Disposition disposition) {
            this.identity = null;
            this.dataIdentity = identity;
            this.epoch = epoch;
            this.version = version;
            this.disposition = disposition;
        }

        P11IdentityOwner.CapturedIdentity identity() { return identity; }
        P11IdentityOwner.DataIdentity dataIdentity() { return dataIdentity; }
        P11IdentityOwner.AccountKey account() {
            return dataIdentity == null ? identity.account() : dataIdentity.account();
        }
        long epoch() { return epoch; }
        long version() { return version; }
        Disposition disposition() { return disposition; }
    }

    /** Sole-ledger discharge is actor-free and cannot be minted from scalar coordinates. */
    static final class Discharge {
        private final P11IdentityOwner.ReceiptDomain domain;
        private final P11IdentityOwner.AccountKey account;

        private Discharge(P11IdentityOwner.ReceiptDomain domain, P11IdentityOwner.AccountKey account) {
            this.domain = domain;
            this.account = account;
        }

        P11IdentityOwner.ReceiptDomain domain() { return domain; }
        P11IdentityOwner.AccountKey account() { return account; }
    }

    static final class OperationReceipt {
        private final Source source;
        private final long scene;
        private final long request;
        private final OperationKind kind;
        private Terminal terminal;

        private OperationReceipt(Source source, long scene, long request, OperationKind kind) {
            this.source = source;
            this.scene = scene;
            this.request = request;
            this.kind = kind;
        }

        Source source() { return source; }
        long scene() { return scene; }
        long request() { return request; }
        OperationKind kind() { return kind; }
    }

    static final class WriterReceipt {
        private final Source source;
        private final OperationReceipt operation;
        private Terminal terminal;

        private WriterReceipt(Source source, OperationReceipt operation) {
            this.source = source;
            this.operation = operation;
        }

        Source source() { return source; }
        OperationReceipt operation() { return operation; }
    }

    static final class MaterialReceipt {
        private final P11IdentityOwner.ReceiptDomain domain;
        private Source source;
        private final Source expected;
        private final Observation[] facts = new Observation[MaterialStep.values().length];
        private Terminal terminal;

        private MaterialReceipt(P11IdentityOwner.ReceiptDomain domain, Source source, Source expected) {
            this.domain = domain;
            this.source = source;
            this.expected = expected;
            Arrays.fill(facts, Observation.NOT_OBSERVED);
        }

        Source source() { return source; }
        Source selectedSource() { return expected; }
        Terminal terminal() { return terminal; }
        Observation observation(MaterialStep step) { return facts[step.ordinal()]; }
    }

    static final class PhysicalWriterReceipt {
        private final P11IdentityOwner.ReceiptDomain domain;
        private final Source source;
        private final WriterKind kind;
        private final long materialVersion;
        private final IndependentMaterialReceipt material;
        private final Observation[] facts = new Observation[PhysicalStep.values().length];
        private Terminal terminal;

        private PhysicalWriterReceipt(P11IdentityOwner.ReceiptDomain domain, Source source,
                WriterKind kind, long materialVersion, IndependentMaterialReceipt material) {
            this.domain = domain;
            this.source = source;
            this.kind = kind;
            this.materialVersion = materialVersion;
            this.material = material;
            Arrays.fill(facts, Observation.NOT_OBSERVED);
        }

        Source source() { return source; }
        WriterKind kind() { return kind; }
        long materialVersion() { return materialVersion; }
    }

    /** A true canonical stats/PA producer supplies this independently of player-body material. */
    static final class IndependentMaterialReceipt {
        private final P11IdentityOwner.ReceiptDomain domain;
        private final Source source;
        private final WriterKind kind;
        private Observation outcome = Observation.NOT_OBSERVED;

        private IndependentMaterialReceipt(P11IdentityOwner.ReceiptDomain domain,
                Source source, WriterKind kind) {
            this.domain = domain;
            this.source = source;
            this.kind = kind;
        }

        Source source() { return source; }
        WriterKind kind() { return kind; }
        Observation outcome() { return outcome; }
    }

    static final class ReadbackReceipt {
        private final P11IdentityOwner.ReceiptDomain domain;
        private final Source source;
        private final WriterKind kind;
        private final long materialVersion;
        private Observation outcome = Observation.NOT_OBSERVED;

        private ReadbackReceipt(P11IdentityOwner.ReceiptDomain domain, Source source,
                WriterKind kind, long materialVersion) {
            this.domain = domain;
            this.source = source;
            this.kind = kind;
            this.materialVersion = materialVersion;
        }

        Source source() { return source; }
        WriterKind kind() { return kind; }
        Observation outcome() { return outcome; }
    }

    /** Independent native-source outcomes, never a cross-file all-saved flag. */
    static final class PhysicalFacts {
        private final WriterKind kind;
        private final long materialVersion;
        private final boolean dirty;
        private final Source capturedSource;
        private final Terminal terminal;
        private final Observation[] observations;

        private PhysicalFacts(WriterKind kind, long materialVersion,
                boolean dirty, PhysicalWriterReceipt attempt) {
            this.kind = kind;
            this.materialVersion = materialVersion;
            this.dirty = dirty;
            this.capturedSource = attempt == null ? null : attempt.source;
            this.terminal = attempt == null ? null : attempt.terminal;
            this.observations = new Observation[PhysicalStep.values().length];
            if (attempt == null) {
                Arrays.fill(observations, Observation.NOT_OBSERVED);
            } else {
                System.arraycopy(attempt.facts, 0, observations, 0, observations.length);
            }
        }

        WriterKind kind() { return kind; }
        long materialVersion() { return materialVersion; }
        boolean dirty() { return dirty; }
        Source capturedSource() { return capturedSource; }
        Terminal terminal() { return terminal; }
        Observation observation(PhysicalStep step) { return observations[step.ordinal()]; }
    }

    /** Copied bounded observations; no mutable arrays or native references escape. */
    static final class Facts {
        private final Observation[] material;
        private final Observation[] membership;
        private final Observation[] readiness;
        private final Observation[] writer;
        private final boolean dirty;
        private final Terminal operationTerminal;
        private final Terminal writerTerminal;

        private Facts(Observation[] material, Observation[] membership,
                Observation[] readiness, Observation[] writer, boolean dirty,
                Terminal operationTerminal, Terminal writerTerminal) {
            this.material = material.clone();
            this.membership = membership.clone();
            this.readiness = readiness.clone();
            this.writer = writer.clone();
            this.dirty = dirty;
            this.operationTerminal = operationTerminal;
            this.writerTerminal = writerTerminal;
        }

        Observation material(MaterialStep step) { return material[step.ordinal()]; }
        Observation membership(MembershipStep step) { return membership[step.ordinal()]; }
        Observation readiness(ReadinessStep step) { return readiness[step.ordinal()]; }
        Observation writer(WriterStep step) { return writer[step.ordinal()]; }
        boolean materialComplete() { return allSucceeded(material); }
        boolean dirty() { return dirty; }
        Terminal operationTerminal() { return operationTerminal; }
        Terminal writerTerminal() { return writerTerminal; }
    }

    private static final class Entry {
        private Source source;
        private OperationReceipt operation;
        private WriterReceipt writer;
        private MaterialReceipt materialReceipt;
        private MaterialReceipt candidate;
        private final EnumMap<WriterKind, PhysicalObligation> physical = new EnumMap<>(WriterKind.class);
        // Observation metadata is deliberately separate from the authority-bearing dirty facts.
        private final long[] observedDirtySince = new long[WriterKind.values().length];
        private final Observation[] material = new Observation[MaterialStep.values().length];
        private final Observation[] membership = new Observation[MembershipStep.values().length];
        private final Observation[] readiness = new Observation[ReadinessStep.values().length];
        private final Observation[] writerFacts = new Observation[WriterStep.values().length];
        private boolean dirty = true;

        private Entry(Source source) {
            this.source = source;
            Arrays.fill(observedDirtySince, UNOBSERVED_DIRTY);
            clearFacts();
        }

        private void clearFacts() {
            Arrays.fill(material, Observation.NOT_OBSERVED);
            Arrays.fill(membership, Observation.NOT_OBSERVED);
            Arrays.fill(readiness, Observation.NOT_OBSERVED);
            clearWriterFacts();
        }

        private void clearWriterFacts() {
            Arrays.fill(writerFacts, Observation.NOT_OBSERVED);
        }
    }

    private static final class PhysicalObligation {
        private long generation;
        private boolean dirty = true;
        private PhysicalWriterReceipt attempt;
        private ReadbackReceipt readback;
        private IndependentMaterialReceipt material;
    }
}
