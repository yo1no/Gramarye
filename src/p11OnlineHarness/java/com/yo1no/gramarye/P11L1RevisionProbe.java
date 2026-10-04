package com.yo1no.gramarye;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
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
        active = new Run(server, actor, peer, reference);
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
            submitSuccessor(run);
        } else {
            require(run.newInstance == null && run.oldDamageReturns == 1
                    && instance != run.oldInstance && instance.lease != run.oldInstance.lease
                    && instance.lease.reference.equals(run.newReference), "NEW_ORDINARY_ADMISSION_REFERENCE");
            run.newInstance = instance;
            current(run.server, run.peer);
            run.peerHealthBeforeNew = run.peer.getHealth();
        }
    }

    private static void submitSuccessor(Run run) {
        require(run.oldInstance.work != null && !run.oldInstance.lease.pin.isClosed(), "OLD_LIVE_BEFORE_SUBMISSION");
        var oldPin = run.oldInstance.lease.pin;
        var oldDefinition = run.oldInstance.lease.definition;
        var canonical = P9StarterSkillContent.canonicalDraft(run.oldReference.skillId());
        var originalHit = canonical.nodes().get(1);
        var payload = P9DamageActionType.INSTANCE.payloadCodec().codec()
                .encodeStart(JsonOps.INSTANCE, new P9DamageActionPayloadV0(5_000L, 0L)).getOrThrow();
        var hit = new DraftNode(originalHit.trigger(), DraftActionSlot.present(new DefinitionEnvelope(
                P9StarterSkillContent.DAMAGE_ID, 1, new Dynamic<>(JsonOps.INSTANCE, payload))), originalHit.appearanceOverride());
        var draft = new SkillDraft(canonical.draftSchemaVersion(), canonical.skillId(),
                Optional.of(run.oldReference.revision()), List.of(canonical.nodes().getFirst(), hit), canonical.appearance());
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
                && run.oldInstance.lease.reference.equals(run.oldReference), "LIVE_OLD_PIN_AFTER_NEW_EQUIP");
        run.coexistenceObserved = true;
    }

    /** Original successful RESERVED→OPEN RETURN, not a constructed projectile. */
    public static void transferred(Object value) {
        var run = active; if (run == null) { return; }
        require(run.server.isSameThread() && value instanceof P9StarterProjectile, "TRANSFER_TYPE");
        var projectile = (P9StarterProjectile) value;
        require(projectile.getOwner() == run.actor && projectile.isAddedToLevel() && !projectile.isRemoved(), "REAL_TRANSFER");
        if (run.oldProjectile == null) { require(run.oldInstance != null, "OLD_ACCEPTED_FIRST"); run.oldProjectile = projectile; }
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

    /** Parent must separately retain native target/health/P8/file observations and normal teardown. */
    public static Map<String, Object> finish() {
        var run = active;
        require(run != null && run.server.isSameThread() && run.coexistenceObserved
                && run.oldDamageReturns == 1 && run.newDamageReturns == 1
                && run.oldInstance.work == null && run.newInstance.work == null
                && run.oldInstance.lease.pin.isClosed() && run.newInstance.lease.pin.isClosed()
                && run.oldProjectile.isRemoved() && run.newProjectile.isRemoved(), "EXACT_TWO_WORK_TERMINALS");
        var facts = new LinkedHashMap<String, Object>();
        facts.put("status", "FORMAL_REVISION_COEXISTENCE_AND_TWO_ORIGINAL_EFFECTS_ONLY");
        facts.put("oldReference", run.oldReference.toString()); facts.put("newReference", run.newReference.toString());
        facts.put("oldPinnedDuringNewSubmission", run.coexistenceObserved);
        facts.put("oldDamage", 4); facts.put("newDamage", 5);
        facts.put("oldOriginalHurtReturns", run.oldDamageReturns); facts.put("newOriginalHurtReturns", run.newDamageReturns);
        facts.put("newEffectActualAuthenticatedPeerHealthDeltaAtHurtReturn", run.peerHealthDeltaAtHurt);
        facts.put("bothWorkAndPinsTerminal", true); facts.put("reloadInvokedByThisHelper", false);
        active = null;
        return Map.copyOf(facts);
    }

    public static void abort() { active = null; }

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
        int oldDamageReturns, newDamageReturns; boolean coexistenceObserved; float peerHealthBeforeNew, peerHealthDeltaAtHurt;
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, SkillReference reference) {
            this.server = server; this.actor = actor; this.peer = peer; this.oldReference = reference;
            actorConnection = actor.connection.getConnection(); peerConnection = peer.connection.getConnection();
        }
    }
}
