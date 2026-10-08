package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import static com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary.*;

/** Sole server-main cooldown truth owner. Cells retain immutable material, never player graphs. */
final class P11CastCooldownService implements P7ServerAuthorizationBoundary.SyncProjectionPort {
    @FunctionalInterface interface PolicyResolver {
        OptionalInt resolve(MinecraftServer server, ServerPlayer actor, SkillReference reference);
    }
    enum ReleaseFact { UNPUBLISHED, PENDING, NO_RELEASE, ARM, UNKNOWN }
    interface ReleaseReceipt { UUID attemptId(); ReleaseFact fact(); long releasedAt(); }
    sealed interface Admission permits Zero, Prepared, Rejected { }
    record Zero() implements Admission { }
    record Rejected(CooldownRejectionReason reason) implements Admission { }
    static final class Prepared implements Admission {
        private final P11CastCooldownService owner;
        private final Cell cell;
        private final P11CastCooldownData.Entry pending;
        private final P11CastCooldownData replacement;
        private final ReleaseReceipt receipt;
        private final P11QualifiedSourceOwner.WorkReservation work;
        private final long expectedGeneration, runtimeAccepted, runtimeDeadline;
        private P11QualifiedSourceOwner.Body preparationBody;
        private P11ReceiptLedger.Source preparationSource;
        private boolean installed;
        private Prepared(P11CastCooldownService owner, Cell cell, P11CastCooldownData.Entry pending,
                P11CastCooldownData replacement, ReleaseReceipt receipt, P11QualifiedSourceOwner.Body body,
                long runtimeAccepted, long runtimeDeadline, P11QualifiedSourceOwner.WorkReservation work) {
            this.owner = owner; this.cell = cell; this.pending = pending; this.replacement = replacement;
            this.receipt = receipt; expectedGeneration = cell.generation;
            preparationBody = body; preparationSource = body.source;
            this.runtimeAccepted = runtimeAccepted; this.runtimeDeadline = runtimeDeadline;
            this.work = work;
        }
        void settle() { owner.settle(this); }
        UUID attemptId() { return pending.attemptId; }
    }
    static final class ArmPreparation {
        private final Prepared attempt;
        private final long generation, releasedAt;
        private final P11CastCooldownData replacement;
        private ArmPreparation(Prepared attempt, long releasedAt, P11CastCooldownData replacement) {
            this.attempt = attempt; this.releasedAt = releasedAt; this.replacement = replacement;
            generation = attempt.cell.generation;
        }
        long releasedAt() { return releasedAt; }
    }

    private final P11FoundationService foundation;
    private final PolicyResolver resolver;
    private final Map<UUID, Cell> cells = new HashMap<>();
    private MinecraftServer server;

    P11CastCooldownService(P11FoundationService foundation, PolicyResolver resolver) {
        this.foundation = Objects.requireNonNull(foundation, "foundation");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }
    void started(MinecraftServer exact) {
        if (server != null || !exact.isSameThread()) { throw new IllegalStateException("COOLDOWN_SLOT_START"); }
        server = exact;
    }
    void stopped(MinecraftServer exact) {
        if (server != exact || !exact.isSameThread()) { throw new IllegalStateException("COOLDOWN_SLOT_STOP"); }
        cells.clear(); server = null;
    }

    Admission prepareAdmission(MinecraftServer exact, ServerPlayer actor, SkillReference reference, int duration,
            long runtimeAccepted, long runtimeDeadline, ReleaseReceipt receipt,
            P11QualifiedSourceOwner.WorkReservation work) {
        if (!owns(exact) || actor == null || actor.getServer() != exact || reference == null || receipt == null
                || work == null || !work.qualifies(actor) || duration < 0 || duration > 600 || runtimeAccepted < 0
                || runtimeAccepted > Long.MAX_VALUE - 101 || runtimeDeadline != runtimeAccepted + 101) {
            return new Rejected(CooldownRejectionReason.UNAVAILABLE);
        }
        var source = foundation.sourceOwner(exact);
        var body = source == null ? null : source.body(actor);
        if (!admissionActor(body, actor) || !source.canCopy(body)) { return new Rejected(CooldownRejectionReason.UNAVAILABLE); }
        var cell = ensureCell(source, body);
        if (cell == null) { return new Rejected(CooldownRejectionReason.UNAVAILABLE); }
        Long now = clock(cell);
        reconcileAt(source, body, cell, now);
        if (now == null) { return new Rejected(CooldownRejectionReason.CLOCK); }
        if (cell.data == null || cell.data.kind != P11CastCooldownData.Kind.ROUTED || !cell.failedWriters.isEmpty()) {
            return new Rejected(CooldownRejectionReason.UNAVAILABLE);
        }
        var existing = cell.data.entries.get(reference.skillId().value());
        var blocked = rejection(existing, now);
        if (blocked != null) { return new Rejected(blocked); }
        if (duration == 0) { return new Zero(); }
        if (cell.data.entries.size() >= 256 || cell.generation == Long.MAX_VALUE || now > Long.MAX_VALUE - 101 - duration) {
            return new Rejected(CooldownRejectionReason.UNAVAILABLE);
        }
        var pending = P11CastCooldownData.Entry.pending(reference.skillId().value(), reference.revision().value(),
                duration, now, Objects.requireNonNull(receipt.attemptId(), "attemptId"), now + (runtimeDeadline - runtimeAccepted));
        var entries = mutable(cell.data); entries.put(pending.skillId, pending);
        return new Prepared(this, cell, pending, P11CastCooldownData.routed(now, entries), receipt,
                body, runtimeAccepted, runtimeDeadline, work);
    }

    boolean installPending(Prepared attempt) {
        if (attempt == null || attempt.owner != this || attempt.installed || !owns(server)) { return false; }
        var body = attempt.preparationBody;
        var source = foundation.sourceOwner(server);
        boolean current = body != null && body.source == attempt.preparationSource
                && admissionActor(body, body.actor) && source != null && source.canCopy(body)
                && sameCell(source, body, attempt.cell) && attempt.cell.generation == attempt.expectedGeneration
                && attempt.receipt.fact() == ReleaseFact.UNPUBLISHED;
        try {
            if (!current) { return false; }
            // Own the exact scalar receipt before the public replacement can be observed.
            // A failed/unknown native publication never leaves an unowned PENDING cell.
            attempt.installed = true;
            try {
                attempt.cell.attempts.put(attempt.pending.skillId, attempt);
                publish(source, body, attempt.cell, attempt.replacement);
            }
            finally {
                if (attempt.cell.data != attempt.replacement) {
                    attempt.cell.attempts.remove(attempt.pending.skillId, attempt);
                    attempt.installed = false;
                }
            }
            return true;
        } finally {
            // The accepted attempt does not retain its old actor or an old source epoch.
            attempt.preparationBody = null; attempt.preparationSource = null;
        }
    }

    ArmPreparation prepareArm(Prepared attempt, P11QualifiedSourceOwner.WorkReservation work,
            ServerPlayer exactCause, long runtimeTick) {
        if (attempt == null || attempt.owner != this || !attempt.installed || !owns(server)
                || work == null || work != attempt.work
                || exactCause == null || exactCause.getServer() != server || runtimeTick < attempt.runtimeAccepted
                || runtimeTick >= attempt.runtimeDeadline || attempt.receipt.fact() != ReleaseFact.PENDING) { return null; }
        var source = foundation.sourceOwner(server);
        var body = source == null ? null : source.cooldownWorkRecipient(work, exactCause);
        if (body == null || !sameCell(source, body, attempt.cell) || !matchesAttempt(attempt)
                || attempt.cell.generation == Long.MAX_VALUE) { return null; }
        Long now = clock(attempt.cell);
        if (now == null || now < attempt.pending.acceptedAt || now > attempt.pending.releaseNotAfter) { return null; }
        var entries = mutable(attempt.cell.data); entries.put(attempt.pending.skillId, attempt.pending.active(now));
        return new ArmPreparation(attempt, now, P11CastCooldownData.routed(now, entries));
    }

    void completeArm(ArmPreparation arm) {
        if (arm == null || arm.attempt.owner != this || !owns(server)) { return; }
        var attempt = arm.attempt;
        if (attempt.receipt.fact() != ReleaseFact.ARM || attempt.receipt.releasedAt() != arm.releasedAt) {
            throw new IllegalStateException("COOLDOWN_ARM_RECEIPT_MISMATCH");
        }
        var source = foundation.sourceOwner(server);
        var body = currentBody(source, attempt.cell);
        if (body != null && attempt.cell.generation == arm.generation && matchesAttempt(attempt)) {
            publish(source, body, attempt.cell, arm.replacement);
            attempt.cell.attempts.remove(attempt.pending.skillId, attempt);
        } else {
            // The receipt remains the exact ARM fact if a later publication or native handoff
            // makes this prebuilt replacement stale. No ACTIVE rollback or NO_RELEASE guess.
            settle(attempt);
        }
    }

    void settle(Prepared attempt) {
        if (attempt == null || attempt.owner != this || !attempt.installed || !owns(server)) { return; }
        var source = foundation.sourceOwner(server);
        var body = currentBody(source, attempt.cell);
        if (body == null) { return; }
        Long now = clock(attempt.cell);
        reconcileAt(source, body, attempt.cell, now);
    }

    boolean materialAdopted(P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body) {
        if (!owns(body.actor.getServer()) || !source.canCopy(body)) { return false; }
        var known = cells.get(body.actor.getUUID());
        var actual = body.cooldown.data;
        if (known == null || known.account != body.account.resource) {
            if (known != null && !known.attempts.isEmpty()) { return false; }
            cells.put(body.actor.getUUID(), new Cell(body.actor.getUUID(), body.account.resource, actual));
            return true;
        }
        if (!sameMaterial(known.data, actual)) { return false; }
        known.data = actual;
        return true;
    }

    void reconcile(P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body) {
        if (!owns(body.actor.getServer()) || !source.canSerialize(body)) { return; }
        var cell = ensureCell(source, body);
        if (cell == null) { return; }
        Long now = clock(cell);
        reconcileAt(source, body, cell, now);
    }

    void writerFinished(P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body,
            P11ReceiptLedger.WriterKind kind, boolean completed) {
        var cell = cells.get(body.actor.getUUID());
        if (cell == null || !sameCell(source, body, cell)) { return; }
        if (completed) { cell.failedWriters.remove(kind); }
        else { cell.failedWriters.add(kind); }
    }

    private Cell ensureCell(P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body) {
        if (!source.canSerialize(body)) { return null; }
        var cell = cells.get(body.actor.getUUID());
        if (cell == null || !sameCell(source, body, cell)) { return null; }
        if (cell.data == null) {
            if (body.inputKind != P11QualifiedSourceOwner.InputKind.PRIMARY
                    && body.inputKind != P11QualifiedSourceOwner.InputKind.HOST_PRIMARY
                    && body.inputKind != P11QualifiedSourceOwner.InputKind.ABSENT
                    && body.inputKind != P11QualifiedSourceOwner.InputKind.MEMORY) { return null; }
            Long now = clock(cell);
            if (now == null || !cell.failedWriters.isEmpty()) { return null; }
            publish(source, body, cell, P11CastCooldownData.routed(now, Map.of()));
        }
        return cell;
    }

    private void reconcileAt(P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body, Cell cell, Long now) {
        if (cell.data == null || cell.data.kind != P11CastCooldownData.Kind.ROUTED) { return; }
        var entries = mutable(cell.data);
        long floor = now == null ? Math.max(cell.data.clockFloor, cell.highWater) : now;
        boolean changed = floor != cell.data.clockFloor;
        var consumed = new ArrayList<Prepared>();
        for (var entry : cell.data.entries.values()) {
            var attempt = cell.attempts.get(entry.skillId);
            var exact = attempt != null && entry.attemptId != null && entry.attemptId.equals(attempt.pending.attemptId)
                    ? attempt : null;
            var settlement = settleEntry(entry, exact == null ? null : exact.receipt, now, floor);
            if (settlement.receiptConsumed && exact != null) { consumed.add(exact); }
            if (settlement.material == null) { entries.remove(entry.skillId); changed = true; }
            else if (settlement.material != entry) { entries.put(entry.skillId, settlement.material); changed = true; }
        }
        if (changed) {
            var replacement = P11CastCooldownData.routed(floor, entries);
            try { publish(source, body, cell, replacement); }
            finally {
                // Even if later bookkeeping throws, remove facts only after the same
                // replacement became actual Attachment truth, never before it.
                if (cell.data == replacement) {
                    for (int index = 0; index < consumed.size(); index++) {
                        var attempt = consumed.get(index); cell.attempts.remove(attempt.pending.skillId, attempt);
                    }
                }
            }
        } else {
            // The matching ACTIVE/clear material was already installed; consuming the
            // same scalar receipt does not require another publication or source version.
            for (var attempt : consumed) { cell.attempts.remove(attempt.pending.skillId, attempt); }
        }
    }

    /** Exact production settlement, separated only to test the scalar facts without Minecraft. */
    static Settlement settleEntry(P11CastCooldownData.Entry entry, ReleaseReceipt receipt, Long now, long floor) {
        Objects.requireNonNull(entry, "entry");
        if (floor < 0 || now != null && now < floor) { throw new IllegalArgumentException("COOLDOWN_UNTRUSTED_TIME"); }
        var replacement = entry;
        boolean consumed = false;
        if (receipt != null) {
            if (entry.attemptId == null || !entry.attemptId.equals(receipt.attemptId())) {
                throw new IllegalArgumentException("COOLDOWN_WRONG_ATTEMPT_RECEIPT");
            }
            switch (receipt.fact()) {
                case ARM -> {
                    long released = receipt.releasedAt();
                    if (released < entry.acceptedAt || released > entry.releaseNotAfter || released > floor
                            || entry.kind >= 3) { throw new IllegalStateException("COOLDOWN_INVALID_ARM_FACT"); }
                    if (entry.kind != 0 || entry.releasedAt != released) { replacement = entry.active(released); }
                    consumed = true;
                }
                case NO_RELEASE -> {
                    if (entry.kind == 0 || entry.kind == 2 && entry.releaseKnowledge == 1) {
                        throw new IllegalStateException("COOLDOWN_ARM_CANNOT_BECOME_NO_RELEASE");
                    }
                    return new Settlement(null, true);
                }
                case UNKNOWN -> {
                    if (entry.kind == 1) {
                        replacement = entry.uncertain(P11CastCooldownData.Reason.PUBLICATION_UNKNOWN, floor, false, -1);
                    }
                }
                case UNPUBLISHED, PENDING -> { }
            }
        } else if (entry.kind == 2 && entry.releaseKnowledge == 1) { replacement = entry.active(entry.releasedAt); }
        if (now != null && replacement.kind == 0 && now >= replacement.expiresAt) {
            return new Settlement(null, receipt != null);
        }
        if (now != null && ((replacement.kind == 1 && receipt == null)
                || replacement.kind == 2 && replacement.releaseKnowledge == 0)
                && now >= replacement.releaseNotAfter + replacement.duration) {
            return new Settlement(null, receipt != null);
        }
        return new Settlement(replacement, consumed);
    }
    record Settlement(P11CastCooldownData.Entry material, boolean receiptConsumed) { }

    private void publish(P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body,
            Cell cell, P11CastCooldownData replacement) {
        if (!sameCell(source, body, cell) || cell.generation == Long.MAX_VALUE || !source.canSerialize(body)) {
            throw new P11QualifiedSourceOwner.SourceUnavailable();
        }
        var previous = cell.data;
        try { P11CastCooldownAttachments.replace(body.actor, previous, replacement); }
        finally {
            // A native publication that succeeded before later bookkeeping failed is still
            // the latest material, never permission to resurrect an older disk PENDING.
            if (P11CastCooldownAttachments.existing(body.actor) == replacement) {
                cell.data = replacement; cell.generation++;
            }
        }
    }

    private Long clock(Cell cell) {
        if (!owns(server) || server.overworld() == null) { return null; }
        long value = server.overworld().getGameTime();
        if (value < 0 || value < cell.highWater || (cell.data != null
                && cell.data.kind == P11CastCooldownData.Kind.ROUTED && value < cell.data.clockFloor)) { return null; }
        cell.highWater = value;
        return value;
    }
    private boolean owns(MinecraftServer exact) { return exact != null && exact == server && exact.isSameThread(); }
    private static boolean admissionActor(P11QualifiedSourceOwner.Body body, ServerPlayer actor) {
        return body != null && body.actor == actor && !actor.isRemoved() && actor.isAlive()
                && actor.connection != null && actor.connection.player == actor && actor.connection.isAcceptingMessages()
                && actor.connection.getConnection().isConnected()
                && actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor;
    }
    private boolean sameCell(P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body, Cell cell) {
        return owns(body.actor.getServer()) && source != null && cells.get(cell.playerId) == cell
                && body.account.resource == cell.account && body.account.current == body && body.account.candidate == null
                && body.cooldown != null && body.cooldown.current(body.actor) && body.cooldown.data == cell.data;
    }
    private P11QualifiedSourceOwner.Body currentBody(P11QualifiedSourceOwner source, Cell cell) {
        if (source == null) { return null; }
        var body = source.current(cell.playerId);
        return body != null && sameCell(source, body, cell) && source.canCopy(body) ? body : null;
    }
    private static boolean matchesAttempt(Prepared attempt) {
        var entry = attempt.cell.data == null ? null : attempt.cell.data.entries.get(attempt.pending.skillId);
        return entry != null && entry.kind == 1 && entry.attemptId.equals(attempt.pending.attemptId)
                && attempt.cell.attempts.get(entry.skillId) == attempt;
    }
    private static TreeMap<UUID, P11CastCooldownData.Entry> mutable(P11CastCooldownData data) {
        var entries = new TreeMap<UUID, P11CastCooldownData.Entry>(P11CastCooldownData.KEY_ORDER);
        entries.putAll(data.entries); return entries;
    }
    private static boolean sameMaterial(P11CastCooldownData first, P11CastCooldownData second) {
        if (first == second) { return true; }
        if (first == null || second == null || first.kind == P11CastCooldownData.Kind.UNBOUND
                || second.kind == P11CastCooldownData.Kind.UNBOUND) { return false; }
        return P11CastCooldownCodec.write(first).equals(P11CastCooldownCodec.write(second));
    }
    private static CooldownRejectionReason rejection(P11CastCooldownData.Entry entry, long now) {
        if (entry == null) { return null; }
        if (entry.kind == 0) { return now < entry.expiresAt ? CooldownRejectionReason.ACTIVE : null; }
        if (entry.kind == 1) { return CooldownRejectionReason.PENDING; }
        if (entry.kind == 2) { return CooldownRejectionReason.RECOVERY; }
        return CooldownRejectionReason.UNAVAILABLE;
    }

    @Override public SyncCapture prepareAndCapture(MinecraftServer exact, ServerPlayer actor) {
        Objects.requireNonNull(exact, "server");
        Objects.requireNonNull(actor, "actor");
        if (!owns(exact) || !syncActor(exact, actor)) { return unavailable(exact, actor); }
        var source = foundation.sourceOwner(exact);
        var body = source == null ? null : source.body(actor);
        if (body == null || !source.canCopy(body)) { return unavailable(exact, actor); }
        var cell = ensureCell(source, body);
        Long now = cell == null ? null : clock(cell);
        if (cell != null) { reconcileAt(source, body, cell, now); }

        // Initialization/reconciliation above belong to the owner. Everything below is
        // one detached observation; policy lookup must not recapture a changed version.
        var version = body.source;
        long generation = cell == null ? -1 : cell.generation;
        var material = cell == null ? null : cell.data;
        long capturedGameTime = exact.overworld() == null ? -1 : exact.overworld().getGameTime();
        var equipment = source.cooldownEquipmentOwner().captureP11Equipment(actor);
        SyncSourceState sourceState = SyncSourceState.AVAILABLE;
        SyncReason sourceReason = SyncReason.NONE;
        if (!equipment.available()) {
            sourceState = SyncSourceState.UNAVAILABLE;
            sourceReason = SyncReason.EQUIPMENT_UNKNOWN;
        } else if (cell == null || material == null || material.kind == P11CastCooldownData.Kind.UNBOUND) {
            sourceState = SyncSourceState.UNAVAILABLE;
            sourceReason = SyncReason.NOT_READY;
        } else if (now == null) {
            sourceState = SyncSourceState.UNAVAILABLE;
            sourceReason = SyncReason.CLOCK;
        } else if (material.kind != P11CastCooldownData.Kind.ROUTED) {
            sourceState = SyncSourceState.UNAVAILABLE;
            sourceReason = reason(material.reason);
        } else {
            // PARTIAL describes local quarantined material, including unequipped keys;
            // provider availability, attempts and durability are entry states only.
            for (var entry : material.entries.values()) {
                if (entry.kind == 3 || entry.kind == 4) {
                    sourceState = SyncSourceState.PARTIAL;
                    sourceReason = reason(entry.reason);
                    break;
                }
            }
        }
        var entries = new ArrayList<SyncEntry>();
        boolean saveFailed = cell != null && !cell.failedWriters.isEmpty();
        for (var equipped : equipment.entries()) {
            if (sourceState == SyncSourceState.UNAVAILABLE) {
                entries.add(new SyncEntry(equipped.slot(), equipped.reference(),
                        SyncEntryState.UNAVAILABLE, sourceReason, 0));
                continue;
            }
            var entry = material.entries.get(equipped.reference().skillId().value());
            SyncEntryState state; SyncReason why; int remaining = 0;
            if (entry != null && entry.kind == 0 && now < entry.expiresAt) {
                state = SyncEntryState.ACTIVE;
                why = saveFailed ? SyncReason.SAVE_FAILED : SyncReason.NONE;
                remaining = Math.toIntExact(entry.expiresAt - now);
            } else if (entry != null && entry.kind == 1) {
                var live = cell.attempts.get(entry.skillId);
                boolean pending = live != null && entry.attemptId.equals(live.pending.attemptId)
                        && (live.receipt.fact() == ReleaseFact.PENDING || live.receipt.fact() == ReleaseFact.UNPUBLISHED);
                state = pending ? SyncEntryState.PENDING : SyncEntryState.RECOVERY_REQUIRED;
                why = saveFailed ? SyncReason.SAVE_FAILED
                        : pending ? SyncReason.PENDING_RELEASE : SyncReason.RECOVERY_REQUIRED;
            } else if (entry != null && entry.kind == 2) {
                state = SyncEntryState.RECOVERY_REQUIRED;
                why = saveFailed ? SyncReason.SAVE_FAILED : SyncReason.OUTCOME_UNCERTAIN;
            } else if (entry != null) {
                state = SyncEntryState.UNAVAILABLE; why = reason(entry.reason);
            } else if (saveFailed) {
                state = SyncEntryState.UNAVAILABLE; why = SyncReason.SAVE_FAILED;
            } else {
                OptionalInt policy = resolver.resolve(exact, actor, equipped.reference());
                state = policy.isPresent() && policy.getAsInt() >= 0 && policy.getAsInt() <= 600
                        ? SyncEntryState.READY : SyncEntryState.UNAVAILABLE;
                why = state == SyncEntryState.READY ? SyncReason.NONE : SyncReason.PROVIDER;
            }
            entries.add(new SyncEntry(equipped.slot(), equipped.reference(), state, why, remaining));
        }
        var projection = new SyncProjection(version.epoch(), version.version(),
                sourceState, sourceReason, entries);
        return new SyncCapture() {
            @Override public SyncProjection projection() { return projection; }
            @Override public boolean isCurrent() {
                return owns(exact) && syncActor(exact, actor) && source.canCopy(body) && body.source == version
                        && cells.get(actor.getUUID()) == cell
                        && (cell == null || sameCell(source, body, cell) && cell.generation == generation
                            && cell.data == material && (!cell.failedWriters.isEmpty()) == saveFailed)
                        && equipment.isCurrent(actor)
                        && (exact.overworld() == null ? -1 : exact.overworld().getGameTime()) == capturedGameTime;
            }
        };
    }

    private SyncCapture unavailable(MinecraftServer exact, ServerPlayer actor) {
        var source = foundation.sourceOwner(exact);
        var body = source == null ? null : source.body(actor);
        var version = body == null ? null : body.source;
        var projection = new SyncProjection(0, 0, SyncSourceState.UNAVAILABLE,
                SyncReason.SOURCE_UNAVAILABLE, java.util.List.of());
        return new SyncCapture() {
            @Override public SyncProjection projection() { return projection; }
            @Override public boolean isCurrent() {
                return syncActor(exact, actor) && foundation.startupState(exact).isPresent()
                        && foundation.sourceOwner(exact) == source
                        && (source == null || source.body(actor) == body
                            && (body == null || body.source == version && !source.canCopy(body)));
            }
        };
    }

    private static boolean syncActor(MinecraftServer exact, ServerPlayer actor) {
        return exact.isSameThread() && actor.getServer() == exact
                && exact.getPlayerList().getPlayer(actor.getUUID()) == actor
                && actor.connection != null && actor.connection.player == actor
                && actor.connection.isAcceptingMessages() && actor.connection.getConnection().isConnected();
    }
    private static SyncReason reason(P11CastCooldownData.Reason reason) {
        if (reason == null) { return SyncReason.SOURCE_UNAVAILABLE; }
        return switch (reason) {
            case UNSUPPORTED -> SyncReason.UNSUPPORTED_VERSION;
            case BYTE_LIMIT, DEPTH_LIMIT, ENTRY_LIMIT -> SyncReason.BOUNDS;
            case CLOCK -> SyncReason.CLOCK;
            case SAVE_FAILURE -> SyncReason.SAVE_FAILED;
            case PUBLICATION_UNKNOWN, RELEASE_UNKNOWN -> SyncReason.OUTCOME_UNCERTAIN;
            case ORPHAN_PENDING -> SyncReason.RECOVERY_REQUIRED;
            default -> SyncReason.MALFORMED;
        };
    }
    private static final class Cell {
        final UUID playerId;
        final P11ControlBudgets.Resources.AccountOwner account;
        P11CastCooldownData data;
        long generation, highWater;
        final Map<UUID, Prepared> attempts = new HashMap<>();
        final EnumSet<P11ReceiptLedger.WriterKind> failedWriters = EnumSet.noneOf(P11ReceiptLedger.WriterKind.class);
        Cell(UUID playerId, P11ControlBudgets.Resources.AccountOwner account, P11CastCooldownData data) {
            this.playerId = playerId; this.account = account; this.data = data;
            highWater = data != null && data.kind == P11CastCooldownData.Kind.ROUTED ? data.clockFloor : 0;
        }
    }
}
