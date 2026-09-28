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

    private final MinecraftServer server;
    private final P11IdentityOwner identities;
    private final P11ReceiptLedger receipts;
    private final P11ControlBudgets.Resources resources;
    private final P11StartupLimits limits;
    private final P11SourceProvenance provenance;
    private final PlayerSkillAttachmentService attachments;
    private final Map<UUID, Account> accounts = new HashMap<>();
    private boolean stopping;
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

    /** A late original listener tick may dirty these after logout's save; use their sole writers. */
    void flushIndependentBeforeLogin(Body body) {
        requireMain();
        if (!canSerialize(body)) { throw new SourceUnavailable(); }
        if (!physicalClean(body, P11ReceiptLedger.WriterKind.STATISTICS)) { body.stats.save(); }
        if (!physicalClean(body, P11ReceiptLedger.WriterKind.ADVANCEMENTS)) { body.advancements.save(); }
        if (!canSerialize(body) || !physicalClean(body, P11ReceiptLedger.WriterKind.STATISTICS)
                || !physicalClean(body, P11ReceiptLedger.WriterKind.ADVANCEMENTS)) {
            fault(body.account, Fault.WRITE);
            throw new SourceUnavailable();
        }
    }

    LoginIndependent beginLoginIndependent(UUID playerId) {
        requireMain();
        var body = current(playerId);
        if (body == null) { return null; }
        if (!canSerialize(body) || !physicalClean(body, P11ReceiptLedger.WriterKind.STATISTICS)
                || !physicalClean(body, P11ReceiptLedger.WriterKind.ADVANCEMENTS)) {
            fault(body.account, Fault.WRITE);
            throw new SourceUnavailable();
        }
        var stats = receipts.physicalFacts(body.source, P11ReceiptLedger.WriterKind.STATISTICS).orElseThrow();
        var advancements = receipts.physicalFacts(body.source, P11ReceiptLedger.WriterKind.ADVANCEMENTS).orElseThrow();
        var ticket = new LoginIndependent(body, stats.materialVersion(), advancements.materialVersion());
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

    void abortLoginIndependent(LoginIndependent ticket) {
        if (ticket == null || !owns(server)) { return; }
        var account = ticket.previous.account;
        if (account.constructing == ticket) {
            account.constructing = null;
            constructorEscaped(ticket.previous, ticket.actor);
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
                || !physicalClean(ticket.previous, P11ReceiptLedger.WriterKind.STATISTICS)
                || !physicalClean(ticket.previous, P11ReceiptLedger.WriterKind.ADVANCEMENTS)) { return false; }
        var stats = receipts.physicalFacts(ticket.source, P11ReceiptLedger.WriterKind.STATISTICS).orElseThrow();
        var advancements = receipts.physicalFacts(ticket.source, P11ReceiptLedger.WriterKind.ADVANCEMENTS).orElseThrow();
        return stats.materialVersion() == ticket.statsVersion && advancements.materialVersion() == ticket.advancementsVersion;
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
        if (actor.getStats() != account.current.stats || actor.getAdvancements() != account.current.advancements) {
            var login = account.login;
            if (!loginIndependentCurrent(login) || login.actor != actor) {
                fault(account, Fault.WRITE);
                throw new SourceUnavailable();
            }
            account.login = null;
        }
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
        return canCopy(body)
                && (!body.actor.isRemoved() || body.envelope != null || body.logoutActive);
    }

    boolean canCopy(Body body) {
        return currentMaterial(body) && body.account.constructing == null;
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
        body.mana = publication.observedState();
        publication(body.actor, body.source.epoch(), body.source.version());
    }

    P11ReceiptLedger.PhysicalWriterReceipt beginWriter(Body body,
            P11ReceiptLedger.WriterKind kind) {
        return beginWriter(body, kind, false);
    }

    P11ReceiptLedger.PhysicalWriterReceipt beginSynchronousPlayerWriter(Body body) {
        if (body == null || !canSerialize(body)
                || !physicalClean(body, P11ReceiptLedger.WriterKind.STATISTICS)
                || !physicalClean(body, P11ReceiptLedger.WriterKind.ADVANCEMENTS)) {
            retainSynchronousDuty(body);
            return null;
        }
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
                || body.account.constructorFailed
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

    Sealed seal(Body body, CompoundTag root) {
        if (!canSerialize(body)) { return null; }
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

    Summary retire(boolean nativeStopNormal) {
        requireMain();
        var summary = new Summary(accounts.size(), resources.counts(), resources.dirtyAge(now()),
                serializations, serializerNanos, writes, writeNanos, failures, nativeStopNormal);
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
                serializerNanos, writes, writeNanos, resources.dirtyAge(now()));
    }

    record Diagnostics(boolean active, long sourceEpoch, long sourceVersion, boolean bodyComplete,
            boolean candidatePresent, String sourceFault, String sourceInput, String equippedSlot0,
            java.util.List<WriterDiagnostic> writers, P11ControlBudgets.ResourceCounts resources,
            long serializations, long serializerNanos, long writes, long writeNanos,
            P11ControlBudgets.DirtyAge dirtyAge) {}
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

    record Summary(int accounts, P11ControlBudgets.ResourceCounts resources,
            P11ControlBudgets.DirtyAge dirtyAge, long serializations, long serializerNanos,
            long writes, long writeNanos, long failures, boolean nativeStopNormal) {}

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

    static final class Account {
        final P11ControlBudgets.Resources.AccountOwner resource;
        P11ControlBudgets.Resources.DirtyObservation dirty;
        Body current;
        Body candidate;
        LoginIndependent login;
        LoginIndependent constructing;
        ServerPlayer partialActor;
        boolean constructorFailed;
        Fault fault = Fault.NONE;
        Account(P11ControlBudgets.Resources.AccountOwner resource) { this.resource = resource; }
    }

    /** At most one pre-constructor continuity witness; never an independent save permission. */
    static final class LoginIndependent {
        private final Body previous;
        private final P11ReceiptLedger.Source source;
        private final long statsVersion, advancementsVersion;
        private ServerPlayer actor;
        private LoginIndependent(Body previous, long statsVersion, long advancementsVersion) {
            this.previous = previous; this.source = previous.source;
            this.statsVersion = statsVersion; this.advancementsVersion = advancementsVersion;
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
