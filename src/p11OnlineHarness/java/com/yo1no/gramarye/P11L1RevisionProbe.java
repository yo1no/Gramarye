package com.yo1no.gramarye;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionCompositionOutcome;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Excluded formal revision coexistence observer. No runtime/root/lease mutation. */
public final class P11L1RevisionProbe {
    private static final int OLD_DURATION = 600;
    private static PlayerSkillAttachmentService attachments;
    private static SkillDefinitionSubmissionService submissions;
    private static SkillDefinitionStoreService store;
    private static Run active;
    private P11L1RevisionProbe() {}

    public static boolean selected() {
        return "l1-revision".equals(System.getProperty("gramarye.p11.online.case", ""));
    }

    /** Called only from the actual completed P9StarterCommand constructor. */
    public static void composition(PlayerSkillAttachmentService actualAttachments,
            SkillDefinitionSubmissionService actualSubmissions, SkillDefinitionStoreService actualStore) {
        if (!selected()) { return; }
        require(attachments == null && submissions == null && store == null
                && actualAttachments != null && actualSubmissions != null && actualStore != null,
                "EXACT_COMPOSITION_ONCE");
        attachments = actualAttachments; submissions = actualSubmissions; store = actualStore;
    }

    /** Parent has already verified the original encrypted hasJoined/PLAY actor. */
    public static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer) {
        require(selected() && active == null && attachments != null, "ARM_SCOPE");
        current(server, actor);
        current(server, peer);
        require(actor != peer && !actor.getUUID().equals(peer.getUUID()), "DISTINCT_AUTHENTICATED_PEER");
        var reference = equipped(actor);
        var document = store.find(server, reference);
        require(document instanceof SkillSubsystemResult.Available<?> value
                && value.value() instanceof Optional<?> optional && optional.isPresent(), "OLD_DOCUMENT");
        var exact = ((SkillSubsystemResult.Available<Optional<com.yo1no.gramarye.magic.definition.document.SkillDocument>>) document)
                .value().orElseThrow();
        require(P9StarterSkillContent.hasCanonicalGameplayFingerprint(reference, exact), "OLD_CANONICAL_4000");
        var positive = draft(reference, OLD_DURATION, 4_000L);
        applied(attachments.putDraft(actor, positive), "POSITIVE_FORMAL_DRAFT");
        var submitted = submissions.submit(actor, positive.skillId());
        require(submitted instanceof SkillSubmissionCompositionOutcome.Committed, "POSITIVE_FORMAL_COMMIT");
        var positiveReference = ((SkillSubmissionCompositionOutcome.Committed) submitted).reference();
        require(positiveReference.skillId().equals(reference.skillId())
                && positiveReference.revision().value() > reference.revision().value(), "POSITIVE_FORMAL_REVISION");
        applied(attachments.setEquipped(actor, 0, Optional.of(positiveReference)), "POSITIVE_FORMAL_EQUIP");
        active = new Run(server, actor, peer, positiveReference);
    }

    /** Original P5 admission RETURN, using the observed original InstanceState, not a lookup/mint. */
    public static void accepted(ServerPlayer actor, Object actualInstance, Object result) {
        var run = active;
        if (run == null || actor != run.actor) { return; }
        current(run.server, actor);
        require(actualInstance instanceof ServerSlot.InstanceState
                && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly, "REAL_ACCEPTANCE");
        var instance = (ServerSlot.InstanceState) actualInstance;
        var accepted = (RuntimeAdmissionResult.AcceptedMemoryOnly) result;
        require(instance.id.equals(accepted.eventToken().skillInstanceId())
                && instance.work != null && !instance.lease.pin.isClosed()
                && instance.hasP9AuthenticatedActorWitness(actor), "EXACT_ACCEPTED_WITNESS_AND_WORK");
        if (run.oldInstance == null) {
            require(instance.lease.reference.equals(run.oldReference), "OLD_ADMISSION_REFERENCE");
            run.oldInstance = instance;
            var pending = currentEntry(run);
            require(pending != null && pending.kind == 1 && pending.duration == OLD_DURATION
                    && pending.revision == run.oldReference.revision().value()
                    && instance.cooldownReceipt != null
                    && instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.PENDING
                    && instance.cooldownReceipt.attemptId().equals(pending.attemptId), "OLD_EXACT_PENDING");
            run.pending = pending;
            submitSuccessor(run);
        } else {
            require(run.newInstance == null && run.oldDamageReturns == 1
                    && run.activeRefusals == 1 && run.activeEntry != null
                    && run.server.overworld().getGameTime() >= run.activeEntry.expiresAt
                    && instance != run.oldInstance && instance.lease != run.oldInstance.lease
                    && instance.lease.reference.equals(run.newReference)
                    && instance.cooldownReceipt == null && instance.cooldownPreparation == null
                    && currentEntry(run) == null, "NEW_ZERO_AFTER_ORIGINAL_EXPIRY");
            run.newInstance = instance;
            current(run.server, run.peer);
            run.peerHealthBeforeNew = run.peer.getHealth();
        }
    }

    /** Actual second authenticated R returned from P5; this does not call admission itself. */
    static void refused(ServerPlayer actor, Object actualInstance, Object result) {
        var run = active;
        require(run != null && actor == run.actor && run.activeRefusals == 0
                && run.oldInstance != null && run.newInstance == null && run.activeEntry != null
                && actualInstance instanceof ServerSlot.InstanceState
                && result instanceof RuntimeAdmissionResult.CooldownRejected rejected
                && rejected.reason() == CooldownRejectionReason.ACTIVE, "ACTUAL_ACTIVE_REFUSAL");
        current(run.server, actor);
        var instance = (ServerSlot.InstanceState) actualInstance;
        require(instance != run.oldInstance && instance.lease.reference.equals(run.newReference)
                && instance.work == null && instance.cooldownReceipt == null
                && instance.lease.pin.isClosed()
                && run.server.overworld().getGameTime() < run.activeEntry.expiresAt
                && sameActive(currentEntry(run), run.activeEntry), "NEW_ZERO_CANNOT_WASH_OLD_ACTIVE");
        run.refusedAt = run.server.overworld().getGameTime(); run.activeRefusals++;
    }

    private static void submitSuccessor(Run run) {
        require(run.oldInstance.work != null && !run.oldInstance.lease.pin.isClosed(), "OLD_LIVE_BEFORE_SUBMISSION");
        var oldPin = run.oldInstance.lease.pin;
        var oldDefinition = run.oldInstance.lease.definition;
        var draft = draft(run.oldReference, 0, 5_000L);
        applied(attachments.putDraft(run.actor, draft), "FORMAL_DRAFT_PUBLICATION");
        var outcome = submissions.submit(run.actor, draft.skillId());
        require(outcome instanceof SkillSubmissionCompositionOutcome.Committed, "FORMAL_COMMITTED_SUBMISSION");
        run.newReference = ((SkillSubmissionCompositionOutcome.Committed) outcome).reference();
        require(run.newReference.skillId().equals(run.oldReference.skillId())
                && run.newReference.revision().value() > run.oldReference.revision().value()
                && equipped(run.actor).equals(run.oldReference), "FORMAL_SUCCESSOR_AND_UNCHANGED_OLD_EQUIP");
        applied(attachments.setEquipped(run.actor, 0, Optional.of(run.newReference)), "FORMAL_EQUIP");
        require(equipped(run.actor).equals(run.newReference)
                && run.oldInstance.work != null && run.oldInstance.lease.pin == oldPin && !oldPin.isClosed()
                && run.oldInstance.lease.definition == oldDefinition
                && run.oldInstance.lease.reference.equals(run.oldReference)
                && currentEntry(run) == run.pending, "LIVE_OLD_PIN_AND_PENDING_AFTER_NEW_ZERO_EQUIP");
        run.coexistenceObserved = true;
    }

    private static SkillDraft draft(SkillReference base, int duration, long magnitude) {
        var canonical = P9StarterSkillContent.canonicalDraft(base.skillId());
        var originalRoot = canonical.nodes().getFirst();
        var trigger = P9ActiveCastTriggerType.INSTANCE.payloadCodec().codec()
                .encodeStart(JsonOps.INSTANCE, new P9ActiveCastTriggerPayloadV1(duration)).getOrThrow();
        var root = new DraftNode(DraftTriggerSlot.present(new DefinitionEnvelope(
                P9StarterSkillContent.ACTIVE_CAST_ID, 1, new Dynamic<>(JsonOps.INSTANCE, trigger))),
                originalRoot.action(), originalRoot.appearanceOverride());
        var originalHit = canonical.nodes().get(1);
        var payload = P9DamageActionType.INSTANCE.payloadCodec().codec()
                .encodeStart(JsonOps.INSTANCE, new P9DamageActionPayloadV0(magnitude, 0L)).getOrThrow();
        var hit = new DraftNode(originalHit.trigger(), DraftActionSlot.present(new DefinitionEnvelope(
                P9StarterSkillContent.DAMAGE_ID, 1, new Dynamic<>(JsonOps.INSTANCE, payload))), originalHit.appearanceOverride());
        return new SkillDraft(canonical.draftSchemaVersion(), canonical.skillId(),
                Optional.of(base.revision()), List.of(root, hit), canonical.appearance());
    }

    /** Original successful RESERVED→OPEN RETURN, not a constructed projectile. */
    public static void transferred(Object value) {
        var run = active; if (run == null) { return; }
        require(run.server.isSameThread() && value instanceof P9StarterProjectile, "TRANSFER_TYPE");
        var projectile = (P9StarterProjectile) value;
        require(projectile.getOwner() == run.actor && projectile.isAddedToLevel() && !projectile.isRemoved(), "REAL_TRANSFER");
        if (run.oldProjectile == null) {
            require(run.oldInstance != null, "OLD_ACCEPTED_FIRST");
            var entry = currentEntry(run);
            require(entry != null && entry.kind == 0 && entry.duration == OLD_DURATION
                    && entry.revision == run.oldReference.revision().value()
                    && entry.attemptId.equals(run.pending.attemptId)
                    && entry.acceptedAt == run.pending.acceptedAt && entry.releaseNotAfter == run.pending.releaseNotAfter
                    && entry.expiresAt == Math.addExact(entry.releasedAt, OLD_DURATION)
                    && run.oldInstance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                    && entry.releasedAt == run.oldInstance.cooldownReceipt.releasedAt()
                    && equipped(run.actor).equals(run.newReference), "OLD_ARM_PRESERVED_UNDER_NEW_ZERO_REVISION");
            run.activeEntry = entry; run.oldProjectile = projectile;
        }
        else { require(run.newInstance != null && run.newProjectile == null && projectile != run.oldProjectile,
                "NEW_TRANSFER_ONCE"); run.newProjectile = projectile; }
    }

    /** Observe the sole original hurt RETURN; parent supplies the actual target selected by its arena. */
    public static void damageReturned(LivingEntity target, DamageSource source, float amount, boolean returned) {
        var run = active; if (run == null) { return; }
        if (source.getDirectEntity() == null
                || source.getDirectEntity() != run.oldProjectile && source.getDirectEntity() != run.newProjectile) { return; }
        require(run.server.isSameThread() && source.getEntity() == run.actor && target != run.actor
                && !target.getUUID().equals(run.actor.getUUID()) && returned, "ORIGINAL_DAMAGE_CAUSE");
        if (source.getDirectEntity() == run.oldProjectile) {
            require(amount == 4 && run.oldDamageReturns++ == 0 && run.coexistenceObserved
                    && run.oldInstance.work != null && !run.oldInstance.lease.pin.isClosed()
                    && run.oldInstance.lease.reference.equals(run.oldReference), "OLD_MAGNITUDE_AND_LIVE_PIN");
        } else {
            require(amount == 5 && target == run.peer && currentPeer(run)
                    && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == run.actor
                    && run.actor.connection.getConnection() == run.actorConnection && run.actorConnection.isConnected()
                    && run.peerHealthBeforeNew - target.getHealth() == 5.0F && run.newDamageReturns++ == 0
                    && run.newInstance.lease.reference.equals(run.newReference), "NEW_MAGNITUDE_AND_REFERENCE");
            run.peerHealthDeltaAtHurt = run.peerHealthBeforeNew - target.getHealth();
        }
    }

    public static boolean oldEffectCompleted() { return active != null && active.oldDamageReturns == 1; }
    static boolean activeRefusalObserved() { return active != null && active.activeRefusals == 1; }
    static boolean originalExpiryReached() {
        var run = active;
        return run != null && run.activeRefusals == 1 && run.oldInstance.work == null
                && run.oldInstance.lease.pin.isClosed()
                && run.server.overworld().getGameTime() >= run.activeEntry.expiresAt;
    }

    /** Parent must separately retain native target/health/P8/file observations and normal teardown. */
    public static Map<String, Object> finish() {
        var run = active;
        require(run != null && run.server.isSameThread() && run.coexistenceObserved
                && run.oldDamageReturns == 1 && run.newDamageReturns == 1
                && run.activeRefusals == 1 && currentEntry(run) == null
                && run.oldInstance.work == null && run.newInstance.work == null
                && run.oldInstance.lease.pin.isClosed() && run.newInstance.lease.pin.isClosed()
                && run.oldProjectile.isRemoved() && run.newProjectile.isRemoved(), "EXACT_TWO_WORK_TERMINALS");
        var facts = new LinkedHashMap<String, Object>();
        facts.put("status", "FORMAL_REVISION_COEXISTENCE_AND_TWO_ORIGINAL_EFFECTS_ONLY");
        facts.put("oldReference", run.oldReference.toString()); facts.put("newReference", run.newReference.toString());
        facts.put("oldPinnedDuringNewSubmission", run.coexistenceObserved);
        facts.put("oldDurationTicks", OLD_DURATION); facts.put("newDurationTicks", 0);
        facts.put("oldAcceptedAt", run.activeEntry.acceptedAt); facts.put("oldReleasedAt", run.activeEntry.releasedAt);
        facts.put("oldExpiresAt", run.activeEntry.expiresAt); facts.put("oldAttemptId", run.activeEntry.attemptId.toString());
        facts.put("actualNewRevisionActiveRefusals", run.activeRefusals); facts.put("activeRefusedAt", run.refusedAt);
        facts.put("newZeroCreatedNoCooldownEntry", true);
        facts.put("oldDamage", 4); facts.put("newDamage", 5);
        facts.put("oldOriginalHurtReturns", run.oldDamageReturns); facts.put("newOriginalHurtReturns", run.newDamageReturns);
        facts.put("newEffectActualAuthenticatedPeerHealthDeltaAtHurtReturn", run.peerHealthDeltaAtHurt);
        facts.put("bothWorkAndPinsTerminal", true); facts.put("reloadInvokedByThisHelper", false);
        active = null;
        return Map.copyOf(facts);
    }

    public static void abort() { active = null; }

    private static P11CastCooldownData.Entry currentEntry(Run run) {
        var owner = P11NativeStorageBoundary.nativeSourceOwner(run.actor);
        var body = owner == null ? null : owner.nativeRecipient(run.actor);
        require(body != null && body.account.current == body && owner.canCopy(body)
                && body.cooldown != null && body.cooldown.data.kind == P11CastCooldownData.Kind.ROUTED,
                "CURRENT_SOLE_COOLDOWN_SOURCE");
        return body.cooldown.data.entries.get(run.oldReference.skillId().value());
    }
    private static boolean sameActive(P11CastCooldownData.Entry actual, P11CastCooldownData.Entry expected) {
        return actual != null && actual.kind == 0 && actual.skillId.equals(expected.skillId)
                && actual.revision == expected.revision && actual.duration == expected.duration
                && actual.attemptId.equals(expected.attemptId) && actual.acceptedAt == expected.acceptedAt
                && actual.releaseNotAfter == expected.releaseNotAfter && actual.releasedAt == expected.releasedAt
                && actual.expiresAt == expected.expiresAt;
    }

    private static SkillReference equipped(ServerPlayer actor) {
        var result = attachments.equippedAt(actor, 0);
        require(result instanceof PlayerSkillAttachmentService.Available<?> available
                && available.value() instanceof Optional<?> value && value.isPresent(), "EQUIPPED_AVAILABLE");
        return ((PlayerSkillAttachmentService.Available<Optional<SkillReference>>) result).value().orElseThrow();
    }
    private static void applied(PlayerSkillAttachmentService.Result<?> value, String code) {
        require(value instanceof PlayerSkillAttachmentService.Available<?> result
                && result.value() == PlayerSkillAttachmentService.Applied.INSTANCE, code);
    }
    private static void current(MinecraftServer server, ServerPlayer actor) {
        require(server != null && server.isSameThread() && server.isRunning() && !server.isStopped()
                && actor != null && actor.getServer() == server && !actor.isFakePlayer()
                && !actor.isRemoved() && actor.isAlive() && actor.connection != null
                && actor.connection.getConnection().isConnected()
                && actor.connection.getConnection().getPacketListener() == actor.connection
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor, "ACTUAL_CURRENT_ACTOR");
    }
    private static boolean currentPeer(Run run) {
        return run.server.getPlayerList().getPlayer(run.peer.getUUID()) == run.peer
                && run.peer.connection.getConnection() == run.peerConnection && run.peerConnection.isConnected();
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "L1_REVISION_" + code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final SkillReference oldReference;
        final Connection actorConnection, peerConnection;
        SkillReference newReference; ServerSlot.InstanceState oldInstance, newInstance;
        P9StarterProjectile oldProjectile, newProjectile;
        P11CastCooldownData.Entry pending, activeEntry;
        int oldDamageReturns, newDamageReturns, activeRefusals; long refusedAt;
        boolean coexistenceObserved; float peerHealthBeforeNew, peerHealthDeltaAtHurt;
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, SkillReference reference) {
            this.server = server; this.actor = actor; this.peer = peer; this.oldReference = reference;
            actorConnection = actor.connection.getConnection(); peerConnection = peer.connection.getConnection();
        }
    }
}
