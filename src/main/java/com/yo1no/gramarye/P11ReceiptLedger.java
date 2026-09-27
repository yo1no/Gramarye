package com.yo1no.gramarye;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded, slot-owned observations only. This ledger does not choose a qualified source,
 * authorize a cast, write data, clear journals, resume native callers, or open sessions.
 * A native producer must eventually supply each observation; no such producer is installed
 * by this foundation slice. Isolated tests certify transitions, never Minecraft durability.
 */
final class P11ReceiptLedger {
    enum Observation { NOT_OBSERVED, SUCCEEDED, FAILED, UNKNOWN }
    enum MaterialStep { CONSTRUCTOR, REQUIRED_COPY_OR_LOAD, REQUIRED_CALLER_CONSUMERS, OWNED_DATA }
    enum MembershipStep { WORLD, ROSTER, UUID_LOOKUP, ENTITY_ID_LOOKUP, LISTENER }
    enum ReadinessStep { NATIVE_SYNC, NATIVE_CALLBACK, P4_PUBLICATION, E2, C, P7_SESSION, P7_INITIAL_SYNC }
    enum WriterStep { WRITE, CLOSE, REPLACE, PERSISTED_READBACK }
    enum Disposition { CANDIDATE, LIVE, DETACHED, UNKNOWN }
    enum OperationKind { DEATH, END, JOIN, RETURN_TO_WORLD, ENTER_CONFIG }
    enum Terminal { COMPLETED, FAILED, UNKNOWN, RETIRED }
    enum Change { RECORDED, DUPLICATE, REFUSED, STALE, TERMINAL, EXHAUSTED }

    private final P11IdentityOwner identities;
    private final P11IdentityOwner.ReceiptDomain domain;
    private final Map<P11IdentityOwner.AccountKey, Entry> entries = new HashMap<>();
    private boolean stopped;

    P11ReceiptLedger(P11IdentityOwner identities) {
        this.identities = Objects.requireNonNull(identities, "identities");
        this.domain = identities.claimReceiptDomain();
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

    synchronized Optional<Source> changeSource(Source expected,
            P11IdentityOwner.CapturedIdentity next, Disposition disposition) {
        Objects.requireNonNull(disposition, "disposition");
        var entry = matching(expected);
        if (entry == null || !identities.current(next)
                || expected.identity.account() != next.account()
                || !dischargedFacts(entry)) {
            return Optional.empty();
        }
        var epoch = identities.nextSourceEpoch(domain, next);
        if (epoch.isEmpty()) {
            return Optional.empty();
        }
        // This foundation has no qualified handoff bridge. Never erase an unresolved source,
        // operation, writer or dirty obligation just because another actor / epoch appeared.
        var source = new Source(next, epoch.getAsLong(), 0, disposition);
        entries.put(next.account(), new Entry(source));
        return Optional.of(source);
    }

    synchronized Optional<Source> advanceMutationVersion(Source expected) {
        var entry = matching(expected);
        if (entry == null || expected.version == Long.MAX_VALUE) {
            return Optional.empty();
        }
        // Contextual material/membership/readiness belong to the exact operation, not v.
        // Only physical write/readback observations require the new exact data version.
        var source = new Source(expected.identity, expected.epoch, expected.version + 1,
                expected.disposition);
        entry.source = source;
        entry.dirty = true;
        entry.clearWriterFacts();
        return Optional.of(source);
    }

    synchronized Optional<OperationReceipt> begin(
            Source source, long scene, long request, OperationKind kind) {
        Objects.requireNonNull(kind, "kind");
        var entry = matching(source);
        if (entry == null || scene <= 0 || request < 0
                || !identities.current(source.identity)
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

    synchronized Optional<Facts> facts(Source source) {
        var entry = matching(source);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(new Facts(entry.material, entry.membership, entry.readiness,
                entry.writerFacts, entry.dirty,
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
        entries.remove(source.identity.account());
        var discharge = new Discharge(domain, source.identity.account());
        return identities.installDischarge(discharge) ? Optional.of(discharge) : Optional.empty();
    }

    synchronized void stop() {
        stopped = true;
        entries.clear();
    }

    private Entry matching(Source source) {
        if (stopped || source == null || !identities.owns(source.identity.account())) {
            return null;
        }
        var entry = entries.get(source.identity.account());
        return entry != null && entry.source == source ? entry : null;
    }

    private Entry matchingOperation(OperationReceipt receipt) {
        if (stopped || receipt == null || !identities.owns(receipt.source.identity.account())) {
            return null;
        }
        var entry = entries.get(receipt.source.identity.account());
        return entry != null && entry.operation == receipt ? entry : null;
    }

    private Entry matchingWriter(WriterReceipt receipt) {
        if (stopped || receipt == null || !identities.owns(receipt.source.identity.account())) {
            return null;
        }
        var entry = entries.get(receipt.source.identity.account());
        return entry != null && entry.writer == receipt ? entry : null;
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
        return left.identity == right.identity && left.epoch == right.epoch;
    }

    private static boolean dischargedFacts(Entry entry) {
        return !entry.dirty && entry.operation != null
                && entry.operation.terminal == Terminal.COMPLETED
                && entry.operation.source == entry.source
                && entry.writer != null && entry.writer.terminal == Terminal.COMPLETED
                && entry.writer.source == entry.source
                && entry.writer.operation == entry.operation;
    }

    static final class Source {
        private final P11IdentityOwner.CapturedIdentity identity;
        private final long epoch;
        private final long version;
        private final Disposition disposition;

        private Source(P11IdentityOwner.CapturedIdentity identity,
                long epoch, long version, Disposition disposition) {
            this.identity = identity;
            this.epoch = epoch;
            this.version = version;
            this.disposition = disposition;
        }

        P11IdentityOwner.CapturedIdentity identity() { return identity; }
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
        private final Observation[] material = new Observation[MaterialStep.values().length];
        private final Observation[] membership = new Observation[MembershipStep.values().length];
        private final Observation[] readiness = new Observation[ReadinessStep.values().length];
        private final Observation[] writerFacts = new Observation[WriterStep.values().length];
        private boolean dirty = true;

        private Entry(Source source) {
            this.source = source;
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
}
