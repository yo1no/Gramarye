package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.runtime.mana.P11ManaMaterial;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;

/** Server-thread source custody in the foundation slot. Never an authentication/cast grant. */
final class P11QualifiedSourceOwner {
    enum InputKind { PRIMARY, HOST_PRIMARY, ABSENT, MEMORY, ERROR, FALLBACK, UNKNOWN }
    enum Fault { NONE, PARTIAL, READ, FALLBACK, MATERIAL, REENTRANT, STALE, CAPACITY, CLEANUP, WRITE }
    enum ControlGate { CLEAR, UNMANAGED, ACTIVE_OPERATION, ACTIVE_CONTEXT, ACTIVE_TRANSITION, SOURCE_UNKNOWN }

    private final MinecraftServer server;
    private final P11IdentityOwner identities;
    private final P11ReceiptLedger receipts;
    private final P11ControlBudgets.Resources resources;
    private final P11StartupLimits limits;
    private final P11SourceProvenance provenance;
    private final PlayerSkillAttachmentService attachments;
    private final Map<UUID, Account> accounts = new HashMap<>();
    private boolean stopping;
    private boolean detachedPlayersStopFlushed;
    private boolean detachedStopFlushed;
    private boolean retired;
    private long serializations;
    private long serializerNanos;
    private long writeNanos;
    private long writes;
    private long failures;
    private final long clockOrigin = System.nanoTime();

    P11QualifiedSourceOwner(MinecraftServer server, P11IdentityOwner identities,
            P11ReceiptLedger receipts, P11ControlBudgets.Resources resources,
            P11StartupLimits limits, P11SourceProvenance provenance,
            PlayerSkillAttachmentService attachments) {
        this.server = server;
        this.identities = identities;
        this.receipts = receipts;
        this.resources = resources;
        this.limits = limits;
        this.provenance = provenance;
        this.attachments = attachments;
    }

    boolean owns(MinecraftServer exact) {
        return !retired && exact == server && exact.isSameThread();
    }

    Account account(ServerPlayer actor) {
        return owns(actor.getServer()) ? accounts.get(actor.getUUID()) : null;
    }

    boolean hasAccount(UUID playerId) {
        requireMain();
        return accounts.containsKey(playerId);
    }

    Body current(UUID playerId) {
        requireMain();
        var account = accounts.get(playerId);
        return account == null ? null : account.current;
    }

    /** Read-only per-account admission facts. An untracked connection never reserves T here. */
    ControlGate controlGate(UUID playerId, ServerPlayer expectedActor) {
        requireMain();
        var account = accounts.get(playerId);
        if (account == null) { return ControlGate.UNMANAGED; }
        var body = account.current;
        if (body == null || account.constructorFailed || account.cleanupUnknown
                || body.fault != Fault.NONE || (expectedActor != null && body.actor != expectedActor)) {
            return ControlGate.SOURCE_UNKNOWN;
        }
        // A known in-flight successor is H, not an invented complete B or an unmanaged UUID.
        if (account.candidate != null && account.candidate.fault != Fault.NONE) {
            return ControlGate.SOURCE_UNKNOWN;
        }
        if (account.candidate == null && account.constructing == null
                && (!canCopy(body) || !canonicalInputComplete(body))) {
            return ControlGate.SOURCE_UNKNOWN;
        }
        var blocker = nativeBlocker(account);
        if (blocker != ControlGate.CLEAR) { return blocker; }
        return canCopy(body) && canonicalInputComplete(body) ? ControlGate.CLEAR : ControlGate.SOURCE_UNKNOWN;
    }

    private static ControlGate nativeBlocker(Account account) {
        if (account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] != 0) {
            return ControlGate.ACTIVE_OPERATION;
        }
        if (account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] != 0) {
            return ControlGate.ACTIVE_CONTEXT;
        }
        if (account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] != 0) {
            return ControlGate.ACTIVE_TRANSITION;
        }
        return ControlGate.CLEAR;
    }

    /** Root dispatcher only: true native-body custody, not NETWORK waiting or an admission grant. */
    ControlCustody beginControlCustody(UUID playerId) {
        requireMain();
        var gate = controlGate(playerId, null);
        if (gate == ControlGate.UNMANAGED) { return null; }
        if (gate != ControlGate.CLEAR) { throw new SourceUnavailable(); }
        var body = accounts.get(playerId).current;
        var custody = new ControlCustody(this, body);
        if (!retainNativeRoot(body, P11ControlBudgets.Root.TRANSITION)) { throw new SourceUnavailable(); }
        return custody;
    }

    /** Only the root's exact active factory ticket may adopt a newly acquired first-B account. */
    ControlCustody attachControlCustody(Body body) {
        requireMain();
        if (body == null || body(body.actor) != body || body.fault != Fault.NONE) {
            throw new SourceUnavailable();
        }
        var custody = new ControlCustody(this, body);
        if (!retainNativeRoot(body, P11ControlBudgets.Root.TRANSITION)) { throw new SourceUnavailable(); }
        return custody;
    }

    void closeControlCustody(ControlCustody custody) {
        if (custody == null) { return; }
        requireMain();
        if (custody.owner != this) { throw new SourceUnavailable(); }
        if (custody.closed) { return; }
        custody.closed = true;
        releaseNativeRoot(custody.body, P11ControlBudgets.Root.TRANSITION);
    }

    /** Validate retained canonical input. Disk durability is not a rebind prerequisite. */
    void flushIndependentBeforeLogin(Body body) {
        requireMain();
        if (!canSerialize(body) || !canonicalInputComplete(body)) { throw new SourceUnavailable(); }
    }

    LoginIndependent beginLoginIndependent(UUID playerId) {
        requireMain();
        var body = current(playerId);
        if (body == null) { return null; }
        if (!canSerialize(body) || !canonicalInputComplete(body)) { throw new SourceUnavailable(); }
        var ticket = new LoginIndependent(body);
        body.account.login = ticket;
        return ticket;
    }

    void finishLoginIndependent(LoginIndependent ticket, ServerPlayer actor) {
        if (ticket == null) { return; }
        requireMain();
        if (actor.getServer() != server || !actor.getUUID().equals(ticket.previous.actor.getUUID())) {
            throw new SourceUnavailable();
        }
        ticket.actor = actor;
        if (ticket.previous.account.constructing != ticket || !loginIndependentCurrent(ticket)) {
            throw new SourceUnavailable();
        }
    }

    void constructorStarted(LoginIndependent ticket) {
        requireMain();
        if (ticket == null || !loginIndependentCurrent(ticket)
                || ticket.previous.account.constructing != null) { throw new SourceUnavailable(); }
        ticket.previous.account.constructing = ticket;
    }

    void abortLoginIndependent(LoginIndependent ticket, ServerPlayer observedPartial, boolean withdrawn) {
        if (ticket == null || !owns(server)) { return; }
        var account = ticket.previous.account;
        if (account.constructing == ticket) {
            account.constructing = null;
            if (!withdrawn) { constructorEscaped(ticket.previous,
                    observedPartial == null ? ticket.actor : observedPartial); }
        }
        if (account.login == ticket) { account.login = null; }
    }

    /** Login and respawn may throw before the constructor-return observer can retain B. */
    void constructorEscaped(Body previous, ServerPlayer partialActor) {
        if (previous == null || !owns(server) || previous.account.current != previous) { return; }
        var account = previous.account;
        account.constructorFailed = true;
        account.partialActor = partialActor;
        if (account.candidate != null) {
            fail(account.candidate, Fault.PARTIAL);
            return;
        }
        // Native maps may hold constructor results even when no B reference was returned.
        // Keep A and its owners, but never turn that unknown tail into another source/save.
        previous.fault = Fault.PARTIAL;
        provenance.invalidate(previous.actor);
        retainSynchronousDuty(previous);
    }

    private boolean loginIndependentCurrent(LoginIndependent ticket) {
        if (ticket == null || ticket.previous.account.login != ticket
                || ticket.previous.account.current != ticket.previous
                || ticket.previous.source != ticket.source || !currentMaterial(ticket.previous)
                || !canonicalInputComplete(ticket.previous)) { return false; }
        return ticket.stats == ticket.previous.stats && ticket.advancements == ticket.previous.advancements;
    }

    /** Only authenticated native placement or an already-owned native copy scope calls here. */
    Body candidate(ServerPlayer actor) {
        requireMain();
        var account = accounts.get(actor.getUUID());
        if (account == null) {
            if (stopping) { return null; }
            var resource = resources.newAccountOwner(actor.getUUID());
            var retained = resources.tryAcquireRoot(resource, P11ControlBudgets.Root.WRITE, true);
            if (retained.isEmpty()) { return null; } // No responsibility was acquired: native path.
            var identity = identities.captureSource(actor);
            if (identity.isEmpty()) {
                resources.releaseRoot(retained.orElseThrow());
                return null;
            }
            var source = receipts.firstSource(identity.orElseThrow(),
                    P11ReceiptLedger.Disposition.CANDIDATE).orElseThrow();
            account = new Account(resource);
            accounts.put(actor.getUUID(), account);
            var material = receipts.beginMaterial(source).orElseThrow();
            var body = new Body(account, actor, source, material);
            account.current = body;
            account.candidate = body;
            initialize(body);
            return body;
        }
        if (account.candidate != null) {
            if (account.candidate.actor == actor) { return account.candidate; }
            fault(account, Fault.REENTRANT);
            throw new SourceUnavailable();
        }
        if (account.current.actor == actor) { return account.current; }
        P11NativeStorageBoundary.releaseMetadata(account.metadata);
        if (actor.getStats() != account.current.stats || actor.getAdvancements() != account.current.advancements) {
            fault(account, Fault.MATERIAL);
            throw new SourceUnavailable();
        }
        account.login = null;
        var identity = identities.captureSource(actor).orElseThrow(SourceUnavailable::new);
        var material = receipts.beginHandoff(account.current.source, identity,
                P11ReceiptLedger.Disposition.CANDIDATE).orElseThrow(SourceUnavailable::new);
        var body = new Body(account, actor, material.source(), material);
        account.candidate = body;
        initialize(body);
        account.constructing = null;
        return body;
    }

    private void initialize(Body body) {
        receipts.material(body.material, P11ReceiptLedger.MaterialStep.CONSTRUCTOR,
                P11ReceiptLedger.Observation.SUCCEEDED);
        // Observe the actual acquisition instant, not a later first-save approximation.
        // These bounded metadata observations confer no material or writer permission.
        receipts.observeRequiredWriter(body.source, P11ReceiptLedger.WriterKind.PLAYER_DATA);
        receipts.observeRequiredWriter(body.source, P11ReceiptLedger.WriterKind.STATISTICS);
        receipts.observeRequiredWriter(body.source, P11ReceiptLedger.WriterKind.ADVANCEMENTS);
        if (server.isSingleplayerOwner(body.actor.getGameProfile())) {
            receipts.observeRequiredWriter(body.source, P11ReceiptLedger.WriterKind.LEVEL_PLAYER);
            receipts.observeRequiredWriter(body.source, P11ReceiptLedger.WriterKind.CACHE);
        }
        body.account.dirty = resources.markDirty(body.account.resource, now()).orElseThrow();
        // Changing the P4 mirror early is intentional: a partial B can never reuse A's proof.
        provenance.manage(body.actor, body.source.epoch(), body.source.version());
    }

    Body body(ServerPlayer actor) {
        var account = account(actor);
        if (account == null) { return null; }
        if (account.candidate != null && account.candidate.actor == actor) { return account.candidate; }
        return account.current != null && account.current.actor == actor ? account.current : null;
    }

    Body canonicalStats(ServerStatsCounter stats) {
        requireMain();
        for (var account : accounts.values()) {
            if (account.candidate != null && account.candidate.stats == stats) { return account.candidate; }
            if (account.current != null && account.current.stats == stats) { return account.current; }
        }
        return null;
    }

    Body canonicalAdvancements(PlayerAdvancements advancements) {
        requireMain();
        for (var account : accounts.values()) {
            if (account.candidate != null && account.candidate.advancements == advancements) {
                return account.candidate;
            }
            if (account.current != null && account.current.advancements == advancements) {
                return account.current;
            }
        }
        return null;
    }

    private boolean canonicalInputComplete(Body body) {
        return body != null && body.stats instanceof P11IndependentMaterialWitness stats
                && stats.p11$materialComplete()
                && body.advancements instanceof P11IndependentMaterialWitness advancements
                && advancements.p11$materialComplete();
    }

    /** Exact native receiver only. UUID equality never upgrades a stale actor into a caller. */
    Body nativeRecipient(ServerPlayer supplied) {
        var body = body(supplied);
        if (body == null || body.fault != Fault.NONE || !owns(supplied.getServer())) { return null; }
        if (body.account.candidate == body) { return body; }
        return currentMaterial(body) ? body : null;
    }

    /** Called only with field-local custody captured from the actual native producer. */
    Body nativeRecipient(Body captured) {
        if (captured == null || !owns(captured.actor.getServer())
                || accounts.get(captured.actor.getUUID()) != captured.account) { return null; }
        if (captured.account.candidate != null) { return null; }
        var current = captured.account.current;
        return currentMaterial(current) ? current : null;
    }

    /** Prepared before P5 publishes an accepted instance. The receipt never owns an actor. */
    WorkReservation acquireWork(ServerPlayer actor) {
        if (actor == null || !owns(actor.getServer()) || stopping
                || P11LiveTransitionBoundary.nativeContinuity(this) == null) { return null; }
        var body = body(actor);
        if (!canCopy(body) || !canonicalInputComplete(body)
                || server.getPlayerList().getPlayer(actor.getUUID()) != actor
                || actor.isRemoved() || !actor.isAlive() || actor.isFakePlayer()
                || actor.connection == null || actor.connection.player != actor
                || !actor.connection.getConnection().isConnected()
                || actor.connection.getConnection().getPacketListener() != actor.connection) { return null; }
        var account = body.account;
        int index = P11ControlBudgets.Root.WORK.ordinal();
        if (account.nativeCounts[index] == Long.MAX_VALUE || !resources.mayAdmitWork(account.resource)) {
            return null;
        }
        // Allocate the receipt before the aggregate reservation/count is changed.
        var work = new WorkReservation(this, account.resource, actor.getUUID());
        if (account.nativeCounts[index] == 0) {
            var reservation = resources.tryAcquireRoot(account.resource, P11ControlBudgets.Root.WORK, true);
            if (reservation.isEmpty()) { return null; }
            account.nativeRoots[index] = reservation.orElseThrow();
            account.nativeSince[index] = now();
        }
        account.nativeCounts[index]++;
        account.nativePeaks[index] = Math.max(account.nativePeaks[index], account.nativeCounts[index]);
        return work;
    }

    private Body workRecipient(WorkReservation work, ServerPlayer exactA) {
        if (work == null || work.owner != this || work.closed || stopping || exactA == null
                || !owns(exactA.getServer()) || !work.playerId.equals(exactA.getUUID())
                || P11LiveTransitionBoundary.nativeContinuity(this) == null) { return null; }
        var account = accounts.get(work.playerId);
        if (account == null || account.resource != work.account
                || account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()] == 0) { return null; }
        var recipient = account.current;
        return canCopy(recipient) && canonicalInputComplete(recipient) ? recipient : null;
    }

    private void releaseWork(WorkReservation work) {
        if (work == null || work.owner != this || work.closed) { return; }
        requireMain();
        work.closed = true;
        var account = accounts.get(work.playerId);
        if (account == null || account.resource != work.account) { return; }
        releaseRoot(account, work.playerId, P11ControlBudgets.Root.WORK);
    }

    boolean retainNativeRoot(Body body, P11ControlBudgets.Root kind) {
        requireMain();
        if (body == null || accounts.get(body.actor.getUUID()) != body.account
                || (kind != P11ControlBudgets.Root.NATIVE_CREDIT
                        && kind != P11ControlBudgets.Root.OPERATION
                        && kind != P11ControlBudgets.Root.COMMAND_CONTEXT
                        && kind != P11ControlBudgets.Root.TRANSITION)) { return false; }
        var account = body.account;
        int index = kind.ordinal();
        if (account.nativeCounts[index] == Long.MAX_VALUE) { return false; }
        if (account.nativeCounts[index] == 0) {
            var reservation = resources.retainRoot(account.resource, kind);
            if (reservation.isEmpty()) { return false; }
            account.nativeRoots[index] = reservation.orElseThrow();
            account.nativeSince[index] = now();
        }
        account.nativeCounts[index]++;
        account.nativePeaks[index] = Math.max(account.nativePeaks[index], account.nativeCounts[index]);
        return true;
    }

    void releaseNativeRoot(Body body, P11ControlBudgets.Root kind) {
        if (body == null || !owns(body.actor.getServer())) { return; }
        releaseRoot(body.account, body.actor.getUUID(), kind);
    }

    private void releaseRoot(Account account, UUID playerId, P11ControlBudgets.Root kind) {
        int index = kind.ordinal();
        if (account.nativeCounts[index] == 0) { return; }
        if (--account.nativeCounts[index] == 0) {
            resources.releaseRoot(account.nativeRoots[index]);
            account.nativeRoots[index] = null;
            account.nativeSince[index] = 0;
            if (kind == P11ControlBudgets.Root.OPERATION || kind == P11ControlBudgets.Root.COMMAND_CONTEXT
                    || kind == P11ControlBudgets.Root.TRANSITION) {
                P11LiveTransitionBoundary.blockersChanged(server, playerId);
            }
        }
    }

    /** May advance v inside the same fixed e/R operation; never claims physical success. */
    void nativeMutation(Body body) {
        if (body == null || !owns(body.actor.getServer()) || body(body.actor) != body) { return; }
        publication(body.actor, body.source.epoch(), body.source.version());
    }

    void nativeEscape(ServerPlayer actor) { P11NativeStorageBoundary.nativeEscape(actor); }

    void nativeOperationFailed(Body body) {
        if (body == null || !owns(body.actor.getServer())) { return; }
        body.account.dirty = resources.markDirty(body.account.resource, now()).orElseThrow();
        failures = increment(failures);
    }

    /** A callback/readiness failure after the proved material prefix is not partial data. */
    void nativeLifecycleFailed(Body body) {
        if (body == null) { return; }
        if (!body.complete) { fail(body, Fault.PARTIAL); return; }
        body.account.cleanupUnknown = true;
        fault(body.account, Fault.CLEANUP);
    }

    boolean associationSourceCurrent(Body body) {
        return currentMaterial(body) && !body.account.cleanupUnknown;
    }

    void nativeCleanup(Body body, boolean complete) {
        if (body == null || !owns(body.actor.getServer())) { return; }
        body.account.cleanupUnknown = !complete;
        if (!complete) { fault(body.account, Fault.CLEANUP); }
    }

    boolean canonicalAssociated(PlayerAdvancements advancements, ServerPlayer receiver) {
        var body = canonicalAdvancements(advancements);
        return body != null && (body.actor == receiver
                || body.account.partialActor == receiver
                || P11NativeStorageBoundary.provisionalAssociation(advancements, receiver));
    }

    boolean detachedPresence(ServerPlayer actor) {
        var body = body(actor);
        return body != null && body.complete && !body.logoutActive
                && body.account.candidate != body && body.envelope != null
                && server.getPlayerList().getPlayer(actor.getUUID()) != actor;
    }

    Body hostBody() {
        requireMain();
        Body host = null;
        for (var account : accounts.values()) {
            var body = account.candidate != null ? account.candidate : account.current;
            if (body != null && server.isSingleplayerOwner(body.actor.getGameProfile())) {
                if (host != null && host != body) { throw new SourceUnavailable(); }
                host = body;
            }
        }
        return host;
    }

    void beginInput(Body body, P11SourceProvenance.SelectedInput input) {
        if (!provenance.beginSelectedLoad(body.actor, body.source.epoch(), body.source.version(), input)) {
            throw new SourceUnavailable();
        }
    }

    PlayerSkillAttachmentService.P11AttachmentReadResult missingInput(Body body) {
        var token = attachments.captureP11MissingRead(body.actor, provenance);
        if (!provenance.readCompleted(body.actor, token)) { throw new SourceUnavailable(); }
        return token;
    }

    void loaded(Body body, P11SourceProvenance.SelectedInput input,
            PlayerSkillAttachmentService.P11AttachmentReadResult readResult) {
        if (body == null || body.loadObserved || body.fault != Fault.NONE) {
            if (body != null) { fail(body, Fault.MATERIAL); }
            return;
        }
        body.loadObserved = true;
        if (!provenance.recordSelectedLoad(body.actor,
                attachments.captureP11Source(body.actor, provenance), body.source.epoch(),
                body.source.version(), input, readResult)) {
            fail(body, Fault.MATERIAL);
        }
    }

    void loadReturned(Body body, boolean complete) {
        if (body == null) { return; }
        if (!complete || !body.loadObserved || body.fault != Fault.NONE
                || !currentAttachmentSource(body)) { fail(body, Fault.MATERIAL); return; }
        receipts.material(body.material, P11ReceiptLedger.MaterialStep.REQUIRED_COPY_OR_LOAD,
                P11ReceiptLedger.Observation.SUCCEEDED);
        receipts.material(body.material, P11ReceiptLedger.MaterialStep.OWNED_DATA,
                P11ReceiptLedger.Observation.SUCCEEDED);
    }

    void callerComplete(Body body) {
        if (body == null || body.complete) { return; }
        if (body.fault != Fault.NONE || !body.loadObserved
                || !currentAttachmentSource(body)) { fail(body, Fault.PARTIAL); return; }
        receipts.material(body.material, P11ReceiptLedger.MaterialStep.REQUIRED_CALLER_CONSUMERS,
                P11ReceiptLedger.Observation.SUCCEEDED);
        if (receipts.finishMaterial(body.material, P11ReceiptLedger.Terminal.COMPLETED)
                != P11ReceiptLedger.Change.RECORDED) {
            fail(body, Fault.PARTIAL);
            return;
        }
        var account = body.account;
        if (account.current != body) {
            body.source = receipts.publishHandoff(body.material).orElseThrow(SourceUnavailable::new);
            account.current = body;
        }
        body.complete = true;
        account.candidate = null;
        account.fault = Fault.NONE;
        // B never inherits A's logout envelope. The old actor graph is released here.
    }

    void fail(Body body, Fault reason) {
        if (body == null) { return; }
        body.fault = reason;
        fault(body.account, reason);
        provenance.invalidate(body.actor);
        if (!body.complete) { receipts.finishMaterial(body.material, P11ReceiptLedger.Terminal.FAILED); }
    }

    private void fault(Account account, Fault reason) {
        account.fault = reason;
        account.dirty = resources.markDirty(account.resource, now()).orElseThrow();
        failures = increment(failures);
    }

    boolean canSerialize(Body body) {
        // Membership/cleanup uncertainty forbids a new handoff, not independently proved data.
        return currentMaterial(body) && body.account.constructing == null
                && (!body.actor.isRemoved() || body.envelope != null || body.logoutActive);
    }

    boolean canCopy(Body body) {
        return currentMaterial(body) && body.account.constructing == null && !body.account.cleanupUnknown;
    }

    /** Only the closed comparison scope may inspect A after B construction, never save it. */
    boolean canInspectConstructedPredecessor(Body body) {
        var ticket = body == null ? null : body.account.constructing;
        return ticket != null && ticket.previous == body && ticket.actor != null
                && body.source == ticket.source && currentMaterial(body)
                && (!body.actor.isRemoved() || body.envelope != null || body.logoutActive);
    }

    private boolean currentMaterial(Body body) {
        return body != null && !retired && body.complete && body.fault == Fault.NONE
                && body.account.current == body && body.account.candidate == null
                && identities.matchesSource(body.source.dataIdentity(), body.actor)
                && currentAttachmentSource(body);
    }

    /** Exact material provenance only; this does not require membership or P4/E2 readiness. */
    private boolean currentAttachmentSource(Body body) {
        var current = provenance.observe(body.actor,
                attachments.captureP11Source(body.actor, provenance));
        return current.kind() == P11SourceProvenance.Kind.CURRENT
                && current.sourceEpoch() == body.source.epoch()
                && current.sourceVersion() == body.source.version()
                && body.mana != null && body.mana.isCurrent(body.actor);
    }

    boolean manaRead(Body body, P11ManaMaterial.Read result) {
        if (body == null || result == null || !result.isBoundTo(body.actor)) { return false; }
        // The closed serializer/copy result is installed immediately after its callback returns.
        body.mana = result.observedState();
        return true;
    }

    void manaPublication(Body body, P11ManaMaterial.Publication publication) {
        if (body.mana == null || publication == null || !publication.follows(body.mana, body.actor)) {
            fail(body, Fault.MATERIAL);
            return;
        }
        var previous = body.source;
        body.mana = publication.observedState();
        publication(body.actor, body.source.epoch(), body.source.version());
        P11NativeStorageBoundary.metadataManaPublished(this, body, previous);
    }

    P11ReceiptLedger.PhysicalWriterReceipt beginWriter(Body body,
            P11ReceiptLedger.WriterKind kind) {
        return beginWriter(body, kind, false);
    }

    P11ReceiptLedger.PhysicalWriterReceipt beginSynchronousPlayerWriter(Body body) {
        if (body == null || !canSerialize(body)) {
            retainSynchronousDuty(body);
            return null;
        }
        // The original PlayerList.save writes PD before its canonical JSON tails. Their
        // outstanding durability duty cannot prevent this fresh PD attempt from starting;
        // each original writer must still earn its own exact-version physical receipt.
        return beginWriter(body, P11ReceiptLedger.WriterKind.PLAYER_DATA, true);
    }

    private P11ReceiptLedger.PhysicalWriterReceipt beginWriter(Body body,
            P11ReceiptLedger.WriterKind kind, boolean preserveIndependent) {
        if (!canSerialize(body)) {
            // A stale actor must not poison its successor. A refusal of the still-owned
            // current body, however, never leaves old successful proofs looking clean.
            if (body != null && owns(server) && body.account.current == body
                    && accounts.get(body.actor.getUUID()) == body.account) {
                receipts.markDirty(body.source, kind);
                fault(body.account, Fault.WRITE);
            }
            return null;
        }
        body.account.dirty = resources.markDirty(body.account.resource, now()).orElseThrow();
        if (kind == P11ReceiptLedger.WriterKind.PLAYER_DATA && !preserveIndependent) {
            // A normal player save starts before the two canonical JSON saves. Prior-cycle
            // JSON proofs cannot release this cycle's aggregate duty in that call-local gap.
            receipts.markDirty(body.source, P11ReceiptLedger.WriterKind.STATISTICS);
            receipts.markDirty(body.source, P11ReceiptLedger.WriterKind.ADVANCEMENTS);
        }
        if ((kind == P11ReceiptLedger.WriterKind.PLAYER_DATA || kind == P11ReceiptLedger.WriterKind.CACHE)
                && server.isSingleplayerOwner(body.actor.getGameProfile())) {
            receipts.markDirty(body.source, P11ReceiptLedger.WriterKind.LEVEL_PLAYER);
        }
        return receipts.beginSave(body.source, kind).orElse(null);
    }

    P11ReceiptLedger.PhysicalWriterReceipt beginIndependentWriter(Body body,
            P11ReceiptLedger.WriterKind kind, P11IndependentMaterialWitness witness) {
        if (!independentEligible(body, kind) || witness == null
                || (kind == P11ReceiptLedger.WriterKind.STATISTICS && witness != body.stats)
                || (kind == P11ReceiptLedger.WriterKind.ADVANCEMENTS && witness != body.advancements)
                || !witness.p11$materialComplete()) {
            return null;
        }
        var material = receipts.beginIndependentMaterial(body.source, kind);
        if (material.isEmpty()) { return null; }
        if (receipts.independentMaterial(material.orElseThrow(), P11ReceiptLedger.Observation.SUCCEEDED)
                != P11ReceiptLedger.Change.RECORDED) { return null; }
        body.account.dirty = resources.markDirty(body.account.resource, now()).orElseThrow();
        return receipts.beginIndependentSave(material.orElseThrow()).orElse(null);
    }

    void independentSkipped(Body body, P11ReceiptLedger.WriterKind kind) {
        if (body == null || retired) { return; }
        receipts.markDirty(body.source, kind);
        body.account.dirty = resources.markDirty(body.account.resource, now()).orElseThrow();
        body.account.fault = Fault.WRITE;
        failures = increment(failures);
    }

    void independentMutation(Body body, P11ReceiptLedger.WriterKind kind) {
        if (!independentEligible(body, kind)) { return; }
        receipts.markDirty(body.source, kind);
        body.account.dirty = resources.markDirty(body.account.resource, now()).orElseThrow();
    }

    private boolean independentEligible(Body body, P11ReceiptLedger.WriterKind kind) {
        if (body == null || !owns(server)
                || !identities.matchesSource(body.source.dataIdentity(), body.actor)) { return false; }
        return switch (kind) {
            case STATISTICS -> canonicalStats(body.stats) == body;
            case ADVANCEMENTS -> canonicalAdvancements(body.advancements) == body;
            default -> false;
        };
    }

    boolean mayWrite(Body body, P11ReceiptLedger.PhysicalWriterReceipt receipt) {
        if (receipt == null || body == null || receipt.source() != body.source) { return false; }
        boolean eligible = switch (receipt.kind()) {
            case STATISTICS -> independentEligible(body, receipt.kind())
                    && body.stats instanceof P11IndependentMaterialWitness witness
                    && witness.p11$materialComplete();
            case ADVANCEMENTS -> independentEligible(body, receipt.kind())
                    && body.advancements instanceof P11IndependentMaterialWitness witness
                    && witness.p11$materialComplete();
            default -> canSerialize(body);
        };
        return eligible && receipts.mayWrite(receipt);
    }

    void physical(P11ReceiptLedger.PhysicalWriterReceipt receipt,
            P11ReceiptLedger.PhysicalStep step, P11ReceiptLedger.Observation outcome) {
        if (receipt != null) { receipts.physical(receipt, step, outcome); }
    }

    void finishWriter(Body body, P11ReceiptLedger.PhysicalWriterReceipt receipt,
            boolean success, long durationNanos) {
        writeNanos = add(writeNanos, Math.max(0, durationNanos));
        writes = increment(writes);
        if (receipt == null || body == null || !owns(server)
                || receipt.source().account() != body.source.account()) { return; }
        var result = receipts.finishSave(receipt, success
                ? P11ReceiptLedger.Terminal.COMPLETED : P11ReceiptLedger.Terminal.FAILED);
        if (result == P11ReceiptLedger.Change.STALE) {
            // The old attempt's real outcome is retained in its receipt. Success for its old
            // version is not a current failure, and neither outcome clears the new dirty duty.
            if (!success) { failures = increment(failures); }
            return;
        }
        if (!success || result != P11ReceiptLedger.Change.RECORDED) {
            fault(body.account, Fault.WRITE);
            return;
        }
        // Absence of an attempted writer is not proof of success. Every required original
        // physical owner must have its own completed proof for this exact body version.
        boolean clean = canSerialize(body)
                && physicalClean(body, P11ReceiptLedger.WriterKind.PLAYER_DATA)
                && physicalClean(body, P11ReceiptLedger.WriterKind.STATISTICS)
                && physicalClean(body, P11ReceiptLedger.WriterKind.ADVANCEMENTS)
                && (!server.isSingleplayerOwner(body.actor.getGameProfile())
                        || physicalClean(body, P11ReceiptLedger.WriterKind.LEVEL_PLAYER));
        if (clean && body.account.dirty != null) {
            resources.releaseDirty(body.account.dirty);
            body.account.dirty = null;
        }
    }

    private boolean physicalClean(Body body, P11ReceiptLedger.WriterKind kind) {
        return receipts.physicalFacts(body.source, kind).filter(facts -> !facts.dirty()
                && facts.capturedSource() == body.source
                && facts.terminal() == P11ReceiptLedger.Terminal.COMPLETED).isPresent();
    }

    /** Exact new native attempt, not a prior clean flag, authorizes this synchronous reread. */
    boolean completedPlayerWrite(Body body, P11ReceiptLedger.PhysicalWriterReceipt receipt) {
        return completedPlayerWrite(body, receipt, null);
    }

    boolean completedPlayerWrite(Body body, P11ReceiptLedger.PhysicalWriterReceipt receipt,
            SelectionWitness witness) {
        if (receipt == null || receipt.kind() != P11ReceiptLedger.WriterKind.PLAYER_DATA
                || !selectedInputCurrent(body, receipt.source(), witness)) { return false; }
        var current = receipts.physicalFacts(body.source, receipt.kind()).orElse(null);
        var attempt = receipts.attemptFacts(receipt).orElse(null);
        if (current == null || attempt == null || current.dirty()
                || current.materialVersion() != receipt.materialVersion()
                || current.capturedSource() != receipt.source()
                || current.terminal() != P11ReceiptLedger.Terminal.COMPLETED
                || attempt.terminal() != P11ReceiptLedger.Terminal.COMPLETED) { return false; }
        for (var step : new P11ReceiptLedger.PhysicalStep[] {
                P11ReceiptLedger.PhysicalStep.ENCODE, P11ReceiptLedger.PhysicalStep.WRITE,
                P11ReceiptLedger.PhysicalStep.CLOSE, P11ReceiptLedger.PhysicalStep.REPLACE }) {
            if (attempt.observation(step) != P11ReceiptLedger.Observation.SUCCEEDED) { return false; }
        }
        return true;
    }

    /** A real read/coherence failure cannot discard newer, possibly unversioned native data. */
    void retainSynchronousDuty(Body body) {
        if (body == null || !owns(server) || body.account.current != body
                || accounts.get(body.actor.getUUID()) != body.account) { return; }
        receipts.markDirty(body.source, P11ReceiptLedger.WriterKind.PLAYER_DATA);
        if (server.isSingleplayerOwner(body.actor.getGameProfile())) {
            receipts.markDirty(body.source, P11ReceiptLedger.WriterKind.LEVEL_PLAYER);
        }
        fault(body.account, body.fault == Fault.PARTIAL ? Fault.PARTIAL : Fault.READ);
    }

    void publication(ServerPlayer actor, long epoch, long version) {
        var body = body(actor);
        if (body == null || body.source.epoch() != epoch || body.source.version() != version) {
            if (body != null) { fail(body, Fault.STALE); }
            return;
        }
        var prior = body.source;
        body.source = receipts.advanceMutationVersion(prior).orElseThrow(SourceUnavailable::new);
        provenance.advanceVersion(actor, epoch, version, body.source.version());
        body.account.dirty = resources.markDirty(body.account.resource, now()).orElseThrow();
        receipts.markDirty(body.source, P11ReceiptLedger.WriterKind.PLAYER_DATA);
        if (server.isSingleplayerOwner(actor.getGameProfile())) {
            receipts.markDirty(body.source, P11ReceiptLedger.WriterKind.LEVEL_PLAYER);
        }
    }

    Optional<P11SourceProvenance.Lineage> lineage(Body body) {
        return body == null ? Optional.empty() : provenance.captureLineage(
                body.actor, body.source.epoch(), body.source.version());
    }

    SelectionWitness captureSelection(Body body) {
        if (!canSerialize(body)) { throw new SourceUnavailable(); }
        var skills = attachments.captureP11Source(body.actor, provenance);
        var mana = P11ManaMaterial.capture(body.actor);
        if (!skills.isCurrent(body.actor) || !mana.sameState(body.mana)) { throw new SourceUnavailable(); }
        return new SelectionWitness(this, body, skills, mana);
    }

    boolean metadataCurrent(Body body, P11ReceiptLedger.Source version, SelectionWitness witness) {
        return canSerialize(body) && body.source == version && witness != null
                && witness.owner == this && witness.body == body && witness.source == version
                && witness.skills.isCurrent(body.actor) && witness.mana.isCurrent(body.actor)
                && witness.mana.sameState(body.mana);
    }

    boolean metadataSkillsCurrent(Body body, SelectionWitness witness) {
        return canSerialize(body) && witness != null && witness.owner == this && witness.body == body
                && witness.skills.isCurrent(body.actor);
    }

    Sealed seal(Body body, CompoundTag root) {
        if (!canSerialize(body) || body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] != 0
                || body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] != 0) { return null; }
        var version = body.source;
        long remaining = limits.maxSealedBytes() - resources.counts().sealedBytes();
        var size = P11StrictNbtSize.measure(root, Math.max(0, remaining));
        if (!(size instanceof P11StrictNbtSize.Fits fits)) { return null; }
        var reservation = resources.tryReserveSealed(body.account.resource, fits.bytes());
        if (reservation.isEmpty()) { return null; }
        boolean retained = false;
        try {
            var copy = root.copy();
            if (body.source != version || !canSerialize(body)) { fail(body, Fault.STALE); return null; }
            var result = new Sealed(body, version, copy, reservation.orElseThrow(), captureSelection(body));
            retained = true;
            return result;
        } finally {
            if (!retained) { resources.releaseSealed(reservation.orElseThrow()); }
        }
    }

    /** A second load view retains its own count/bytes while the original selected seal lives. */
    Sealed sealForSelectedInput(Sealed selected) {
        requireMain();
        if (!selectedInputCurrent(selected)) { return null; }
        long remaining = limits.maxSealedBytes() - resources.counts().sealedBytes();
        var size = P11StrictNbtSize.measure(selected.root, Math.max(0, remaining));
        if (!(size instanceof P11StrictNbtSize.Fits fits)) { return null; }
        var reservation = resources.tryReserveSealed(selected.body.account.resource, fits.bytes());
        if (reservation.isEmpty()) { return null; }
        boolean retained = false;
        try {
            var copy = selected.root.copy();
            if (!selectedInputCurrent(selected)) { return null; }
            var result = new Sealed(selected.body, selected.source, copy, reservation.orElseThrow(), selected.witness);
            retained = true;
            return result;
        } finally {
            if (!retained) { resources.releaseSealed(reservation.orElseThrow()); }
        }
    }

    boolean selectedInputCurrent(Sealed selected) {
        return selected != null && !selected.released
                && selectedInputCurrent(selected.body, selected.source, selected.witness);
    }

    private boolean selectedInputCurrent(Body body, P11ReceiptLedger.Source version,
            SelectionWitness witness) {
        if (body == null || !owns(server)) { return false; }
        var account = body.account;
        if (accounts.get(body.actor.getUUID()) != account || account.current != body
                || body.source != version || !body.complete || body.fault != Fault.NONE
                || !identities.matchesSource(version.dataIdentity(), body.actor)) { return false; }
        // A deliberately installed candidate does not invalidate the immutable input it is
        // currently consuming. A different selection or a changed A source does invalidate it.
        if (account.candidate == null) { return currentAttachmentSource(body); }
        // B deliberately replaces the UUID's provenance mirror. This closed pre-candidate
        // observation only keeps its already-selected input valid; it never lets A write.
        return account.candidate != body && account.candidate.material.selectedSource() == version
                && witness != null && witness.owner == this && witness.body == body
                && witness.source == version && witness.skills.isCurrent(body.actor)
                && witness.mana.isCurrent(body.actor) && witness.mana.sameState(body.mana);
    }

    void release(Sealed sealed) {
        if (sealed != null && !sealed.released) {
            sealed.released = true;
            resources.releaseSealed(sealed.reservation);
        }
    }

    void serialized(long elapsedNanos) {
        serializations = increment(serializations);
        serializerNanos = add(serializerNanos, Math.max(0, elapsedNanos));
    }

    void stopping() { stopping = true; }

    /** Stop's player-save phase, before removeAll and the original world/host writer. */
    void flushDetachedPlayersAtStop() {
        requireMain();
        if (!stopping || detachedPlayersStopFlushed) { return; }
        detachedPlayersStopFlushed = true;
        for (var account : java.util.List.copyOf(accounts.values())) {
            var body = account.current;
            try {
                if (body == null || !detachedStopCurrent(body, body.source)
                        || receipts.physicalFacts(body.source, P11ReceiptLedger.WriterKind.PLAYER_DATA)
                                .filter(P11ReceiptLedger.PhysicalFacts::dirty).isEmpty()) { continue; }
                P11NativeStorageBoundary.saveDetachedAtStop(this, body);
            } catch (RuntimeException | Error failure) {
                // This additional save cannot replace native stop's primary or skip another UUID.
                failures = increment(failures);
                try {
                    if (body != null && accounts.get(body.actor.getUUID()) == account
                            && account.current == body && account.candidate == null) {
                        account.fault = Fault.WRITE;
                        receipts.markDirty(body.source, P11ReceiptLedger.WriterKind.PLAYER_DATA);
                        account.dirty = resources.markDirty(account.resource, now()).orElseThrow();
                    }
                } catch (RuntimeException | Error secondary) {
                    failures = increment(failures);
                }
            }
        }
    }

    /** Exact existing source only; the stop caller and one-use request are checked by the bridge. */
    boolean detachedStopCurrent(Body body, P11ReceiptLedger.Source version) {
        requireMain();
        return stopping && body != null && body.source == version && accounts.get(body.actor.getUUID()) == body.account
                && canSerialize(body) && !body.logoutActive && body.actor.connection != null
                && body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0
                && server.getPlayerList().getPlayer(body.actor.getUUID()) == null
                && server.getPlayerList().getPlayers().stream().noneMatch(actor -> actor == body.actor);
    }

    /** Last native stop anchor while the world lock is still held; never serializes a body. */
    void flushDetachedIndependentAtStop() {
        requireMain();
        if (detachedStopFlushed) { return; }
        detachedStopFlushed = true;
        // Native codec/writer calls run outside a monitor and may reenter; retain no new graph.
        for (var account : java.util.List.copyOf(accounts.values())) {
            var body = account.candidate != null ? account.candidate : account.current;
            if (body == null || accounts.get(body.actor.getUUID()) != account
                    || server.getPlayerList().getPlayer(body.actor.getUUID()) != null) { continue; }
            flushDetachedIndependent(body, P11ReceiptLedger.WriterKind.STATISTICS);
            flushDetachedIndependent(body, P11ReceiptLedger.WriterKind.ADVANCEMENTS);
        }
    }

    private void flushDetachedIndependent(Body body, P11ReceiptLedger.WriterKind kind) {
        try {
            if (!independentEligible(body, kind)
                    || receipts.physicalFacts(body.source, kind).filter(P11ReceiptLedger.PhysicalFacts::dirty).isEmpty()) {
                return;
            }
            // Original sole writers still prove their own material, encode/write/close and outcome.
            if (kind == P11ReceiptLedger.WriterKind.STATISTICS) { body.stats.save(); }
            else if (kind == P11ReceiptLedger.WriterKind.ADVANCEMENTS) { body.advancements.save(); }
        } catch (RuntimeException | Error failure) {
            // A secondary independent failure cannot mask native stop or skip the other owner.
            body.account.fault = Fault.WRITE;
            failures = increment(failures);
        }
    }

    Summary retire(boolean nativeStopNormal) {
        requireMain();
        var summary = new Summary(accounts.size(), resources.counts(), resources.dirtyAge(now()),
                serializations, serializerNanos, writes, writeNanos, failures, nativeStopNormal,
                receipts.saveProgress(), nativeResponsibilities());
        for (var account : accounts.values()) { P11NativeStorageBoundary.releaseMetadata(account.metadata); }
        retired = true;
        accounts.clear();
        return summary;
    }

    private void requireMain() {
        if (!owns(server)) { throw new IllegalStateException("P11_SOURCE_WRONG_SLOT_OR_THREAD"); }
    }

    Diagnostics diagnostics(UUID uuid) {
        requireMain();
        var account = accounts.get(uuid);
        var body = account == null ? null : account.candidate != null ? account.candidate : account.current;
        var writers = new java.util.ArrayList<WriterDiagnostic>();
        if (body != null) {
            for (var kind : P11ReceiptLedger.WriterKind.values()) {
                receipts.physicalFacts(body.source, kind).ifPresent(facts -> writers.add(
                        new WriterDiagnostic(kind.name(), facts.materialVersion(), facts.dirty(),
                                facts.terminal() == null ? "OPEN" : facts.terminal().name(),
                                facts.observation(P11ReceiptLedger.PhysicalStep.ENCODE).name(),
                                facts.observation(P11ReceiptLedger.PhysicalStep.WRITE).name(),
                                facts.observation(P11ReceiptLedger.PhysicalStep.CLOSE).name(),
                                facts.observation(P11ReceiptLedger.PhysicalStep.REPLACE).name(),
                                facts.observation(P11ReceiptLedger.PhysicalStep.CACHE_ASSIGNMENT).name())));
            }
        }
        String equipped = "UNAVAILABLE";
        if (body != null && attachments.equippedAt(body.actor, 0)
                instanceof PlayerSkillAttachmentService.Available<Optional<com.yo1no.gramarye.magic.definition.document.SkillReference>> available) {
            equipped = available.value().map(Object::toString).orElse("ABSENT");
        }
        return new Diagnostics(true, body == null ? 0 : body.source.epoch(),
                body == null ? 0 : body.source.version(), body != null && body.complete,
                account != null && account.candidate != null,
                account == null ? "UNMANAGED" : account.fault.name(),
                body == null ? "UNKNOWN" : body.inputKind.name(), equipped,
                java.util.List.copyOf(writers), resources.counts(), serializations,
                serializerNanos, writes, writeNanos, resources.dirtyAge(now()), receipts.saveProgress(),
                nativeResponsibilities());
    }

    record Diagnostics(boolean active, long sourceEpoch, long sourceVersion, boolean bodyComplete,
            boolean candidatePresent, String sourceFault, String sourceInput, String equippedSlot0,
            java.util.List<WriterDiagnostic> writers, P11ControlBudgets.ResourceCounts resources,
            long serializations, long serializerNanos, long writes, long writeNanos,
            P11ControlBudgets.DirtyAge dirtyAge, P11ReceiptLedger.SaveProgress saveProgress,
            NativeResponsibilities nativeResponsibilities) {}
    record WriterDiagnostic(String kind, long attempt, boolean dirty, String terminal,
            String encode, String write, String close, String replace, String cacheAssignment) {}

    private long now() {
        long elapsed = System.nanoTime() - clockOrigin;
        return elapsed < 0 ? -1 : elapsed / 1_000_000L;
    }
    private static long increment(long value) { return value == Long.MAX_VALUE ? value : value + 1; }
    private static long add(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private NativeResponsibilities nativeResponsibilities() {
        var roots = new java.util.ArrayList<NativeRootDiagnostic>();
        long now = now();
        int live = 0, detached = 0, partial = 0;
        for (var account : accounts.values()) {
            var body = account.candidate != null ? account.candidate : account.current;
            if (body != null) {
                if (!body.complete) { partial++; }
                else if (server.getPlayerList().getPlayer(body.actor.getUUID()) == body.actor) { live++; }
                else { detached++; }
            }
        }
        for (var kind : new P11ControlBudgets.Root[] { P11ControlBudgets.Root.WORK, P11ControlBudgets.Root.NATIVE_CREDIT,
                P11ControlBudgets.Root.OPERATION, P11ControlBudgets.Root.COMMAND_CONTEXT,
                P11ControlBudgets.Root.TRANSITION }) {
            long count = 0, peaks = 0, oldest = 0;
            for (var account : accounts.values()) {
                int index = kind.ordinal();
                count = add(count, account.nativeCounts[index]);
                peaks = add(peaks, account.nativePeaks[index]);
                if (account.nativeCounts[index] != 0 && now >= account.nativeSince[index]) {
                    oldest = Math.max(oldest, now - account.nativeSince[index]);
                }
            }
            roots.add(new NativeRootDiagnostic(kind.name(), count, peaks, oldest));
        }
        return new NativeResponsibilities(live, detached, partial, java.util.List.copyOf(roots));
    }

    record NativeResponsibilities(int liveCanonicalHolders, int detachedCanonicalHolders,
            int partialHolders, java.util.List<NativeRootDiagnostic> roots) {}
    /** Sum of per-account observed peaks, deliberately not a simultaneous whole-server peak. */
    record NativeRootDiagnostic(String kind, long count, long accountPeakSum, long oldestAgeMillis) {}

    record Summary(int accounts, P11ControlBudgets.ResourceCounts resources,
            P11ControlBudgets.DirtyAge dirtyAge, long serializations, long serializerNanos,
            long writes, long writeNanos, long failures, boolean nativeStopNormal,
            P11ReceiptLedger.SaveProgress saveProgress, NativeResponsibilities nativeResponsibilities) {}

    static final class Body {
        final Account account;
        final ServerPlayer actor;
        final ServerStatsCounter stats;
        final PlayerAdvancements advancements;
        P11ManaMaterial.State mana;
        P11ReceiptLedger.Source source;
        final P11ReceiptLedger.MaterialReceipt material;
        boolean loadObserved;
        boolean complete;
        boolean logoutActive;
        boolean logoutAttempted;
        Envelope envelope;
        Envelope pendingEnvelope;
        Fault fault = Fault.NONE;
        InputKind inputKind = InputKind.UNKNOWN;

        Body(Account account, ServerPlayer actor, P11ReceiptLedger.Source source,
                P11ReceiptLedger.MaterialReceipt material) {
            this.account = account;
            this.actor = actor;
            this.stats = actor.getStats();
            this.advancements = actor.getAdvancements();
            this.mana = P11ManaMaterial.capture(actor);
            this.source = source;
            this.material = material;
        }
    }

    /** The exact original account remains held even when its material body changes A to B. */
    static final class ControlCustody {
        private final P11QualifiedSourceOwner owner;
        private final Body body;
        private boolean closed;
        private ControlCustody(P11QualifiedSourceOwner owner, Body body) {
            this.owner = owner;
            this.body = body;
        }
    }

    static final class Account {
        final P11ControlBudgets.Resources.AccountOwner resource;
        P11ControlBudgets.Resources.DirtyObservation dirty;
        Body current;
        Body candidate;
        LoginIndependent login;
        LoginIndependent constructing;
        ServerPlayer partialActor;
        boolean constructorFailed;
        boolean cleanupUnknown;
        P11NativeStorageBoundary.MetadataLease metadata;
        final long[] nativeCounts = new long[P11ControlBudgets.Root.values().length];
        final long[] nativePeaks = new long[P11ControlBudgets.Root.values().length];
        final long[] nativeSince = new long[P11ControlBudgets.Root.values().length];
        final P11ControlBudgets.Resources.RootReservation[] nativeRoots =
                new P11ControlBudgets.Resources.RootReservation[P11ControlBudgets.Root.values().length];
        Fault fault = Fault.NONE;
        Account(P11ControlBudgets.Resources.AccountOwner resource) { this.resource = resource; }
    }

    /** P5 alone keeps its exact-A witness; this is only the same account's bounded W duty. */
    static final class WorkReservation {
        private final P11QualifiedSourceOwner owner;
        private final P11ControlBudgets.Resources.AccountOwner account;
        private final UUID playerId;
        private boolean closed;

        private WorkReservation(P11QualifiedSourceOwner owner,
                P11ControlBudgets.Resources.AccountOwner account, UUID playerId) {
            this.owner = owner; this.account = account; this.playerId = playerId;
        }

        boolean qualifies(ServerPlayer exactA) { return owner.workRecipient(this, exactA) != null; }
        void release() { owner.releaseWork(this); }
        P11NativeOperationBoundary.OperationScope beginNative(ServerPlayer exactA) {
            var recipient = owner.workRecipient(this, exactA);
            return recipient == null ? null : P11NativeOperationBoundary.beginAcceptedWork(owner, recipient, exactA);
        }
    }

    /** At most one pre-constructor continuity witness; never an independent save permission. */
    static final class LoginIndependent {
        private final Body previous;
        private final P11ReceiptLedger.Source source;
        private final ServerStatsCounter stats;
        private final PlayerAdvancements advancements;
        private ServerPlayer actor;
        private LoginIndependent(Body previous) {
            this.previous = previous; this.source = previous.source;
            this.stats = previous.stats; this.advancements = previous.advancements;
        }
    }

    /** Custody of three fields from the original private .dat serializer material. No added
     * deep copy/snapshot: only the original read-only writer sees this root. A later selected
     * load view is copied only by seal(), after count/bytes reservation. Null means deletion. */
    static final class Envelope {
        private final Tag position;
        private final Tag vehicle;
        private final Tag brain;
        Envelope(CompoundTag root) {
            position = root.get("Pos");
            vehicle = root.get("RootVehicle");
            brain = root.get("Brain");
        }
        void apply(CompoundTag root) {
            put(root, "Pos", position); put(root, "RootVehicle", vehicle); put(root, "Brain", brain);
        }
        private static void put(CompoundTag root, String name, Tag tag) {
            if (tag == null) { root.remove(name); } else { root.put(name, tag); }
        }
    }

    static final class Sealed {
        final Body body;
        final P11ReceiptLedger.Source source;
        final CompoundTag root;
        final P11ControlBudgets.Resources.SealedReservation reservation;
        final SelectionWitness witness;
        boolean released;
        Sealed(Body body, P11ReceiptLedger.Source source, CompoundTag root,
                P11ControlBudgets.Resources.SealedReservation reservation, SelectionWitness witness) {
            this.body = body; this.source = source; this.root = root; this.reservation = reservation;
            this.witness = witness;
        }
    }

    static final class SelectionWitness {
        private final P11QualifiedSourceOwner owner;
        private final Body body;
        private final P11ReceiptLedger.Source source;
        private final PlayerSkillAttachmentService.P11AttachmentSnapshot skills;
        private final P11ManaMaterial.State mana;
        private SelectionWitness(P11QualifiedSourceOwner owner, Body body,
                PlayerSkillAttachmentService.P11AttachmentSnapshot skills, P11ManaMaterial.State mana) {
            this.owner = owner; this.body = body; this.source = body.source;
            this.skills = skills; this.mana = mana;
        }
    }

    static final class SourceUnavailable extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        SourceUnavailable() { super("P11_QUALIFIED_SOURCE_UNAVAILABLE"); }
    }
}
