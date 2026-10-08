package com.yo1no.gramarye;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionCompositionOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/** Two real owners, distinct formally owned definitions, and original R/ARM/save paths only. */
public final class P11CooldownDualProbe {
    private static Run run;
    private P11CooldownDualProbe() { }
    public static boolean selected() {
        return "cooldown-dual".equals(System.getProperty("gramarye.p11.online.case", ""));
    }
    static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, ServerSlot slot,
            P11FoundationService foundation, P11CastCooldownService cooldowns,
            PlayerSkillAttachmentService attachments, SkillDefinitionSubmissionService submissions, Path output) throws IOException {
        require(selected() && run == null && server.isSameThread() && actor != peer
                && !actor.getUUID().equals(peer.getUUID()), "EXACT_TWO_AUTHENTICATED_OWNERS");
        var source = foundation.sourceOwner(server);
        require(source != null && output.toRealPath().equals(P11C4aEvidence.root().resolve("server")), "SOURCE_AND_OUTPUT");
        var a = new Participant("a", actor, source.body(actor));
        var b = new Participant("b", peer, source.body(peer));
        require(a.body != null && b.body != null && a.body != b.body && a.body.account != b.body.account,
                "DISTINCT_NATIVE_BODIES_AND_ACCOUNTS");
        run = new Run(server, slot, source, cooldowns, attachments, submissions, output, a, b);
        current(run, a); current(run, b);
        var level = actor.serverLevel();
        require(peer.serverLevel() == level, "SAME_ORIGINAL_LEVEL");
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            level.setBlock(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState(), 3);
        actor.teleportTo(level, .5, 100, .5, java.util.Set.of(), 0, -45);
        peer.teleportTo(level, 2.5, 100, .5, java.util.Set.of(), 90, -45);
        cue(run, "a-starter.ready"); cue(run, "b-starter.ready");
    }
    static boolean tick() throws IOException {
        var r = exact(); current(r, r.a); current(r, r.b);
        require(++r.ticks <= 2400 && !r.observerFailed && r.claims == 0
                && r.a.actor.getHealth() == r.a.health && r.b.actor.getHealth() == r.b.health,
                "FINITE_NO_CHILD_OR_PLAYER_DAMAGE");
        if (r.complete) return true;
        if (r.phase == 0) {
            var a = equipped(r, r.a); var b = equipped(r, r.b);
            if (a.isEmpty() || b.isEmpty()) return false;
            require(!a.orElseThrow().skillId().equals(b.orElseThrow().skillId()), "DISTINCT_FORMAL_SKILL_OWNERSHIP");
            submit(r, r.a, a.orElseThrow()); submit(r, r.b, b.orElseThrow());
            r.phase = 1; cue(r, "a-cast-1.ready"); return false;
        }
        if (r.phase == 1 && r.a.transfers == 1) {
            active(r, r.a); require(r.b.admissions == 0, "A_RELEASE_BEFORE_B_FIRST_R");
            r.phase = 2; cue(r, "b-cast-1.ready"); return false;
        }
        if (r.phase == 2 && r.b.transfers == 1) {
            active(r, r.a); active(r, r.b);
            require(distinct(r.a.cell, r.b.cell, r.a.active, r.b.active), "DISTINCT_SOLE_CELLS_ATTEMPTS_AND_REFS");
            r.phase = 3; cue(r, "a-cast-2.ready"); return false;
        }
        if (r.phase == 3 && r.a.rejections == 1) {
            active(r, r.a); active(r, r.b); r.phase = 4; cue(r, "b-cast-2.ready"); return false;
        }
        if (r.phase == 4 && r.b.rejections == 1) {
            active(r, r.a); active(r, r.b);
            if (!terminal(r, r.a) || !terminal(r, r.b)
                    || !clientReceipt("a", "hud-active.json") || !clientReceipt("b", "hud-active.json")) return false;
            require(r.a.admissions == 1 && r.b.admissions == 1 && r.a.rejections == 1 && r.b.rejections == 1
                    && r.a.transfers == 1 && r.b.transfers == 1 && r.claims == 0, "TWO_REAL_ROOTS_TWO_ACTIVE_REFUSALS");
            r.server.getPlayerList().saveAll();
            active(r, r.a); active(r, r.b);
            require(!r.observerFailed && distinct(r.a.cell, r.b.cell, r.a.active, r.b.active),
                    "ORIGINAL_SAVE_PRESERVES_DISTINCT_CELL_OBSERVATIONS");
            physical(r, r.a); physical(r, r.b);
            writeParticipant(r, r.a); writeParticipant(r, r.b);
            P11C4aEvidence.write(r.output, "cooldown-dual-result.json", Map.ofEntries(
                    Map.entry("status", "TWO_AUTHENTICATED_OWNERS_INDEPENDENT_POSITIVE_COOLDOWNS"),
                    Map.entry("distinctOwnedSkillIds", true), Map.entry("sharedSkillIdPolicyClaim", false),
                    Map.entry("sameClosedProfileDuration", 600), Map.entry("distinctSoleCells", true),
                    Map.entry("distinctAttemptReceipts", true), Map.entry("bArmWhileAActive", r.bArmWhileAActive),
                    Map.entry("originalAcceptedRoots", 2), Map.entry("originalActiveRefusals", 2),
                    Map.entry("queuedHitChildren", r.claims), Map.entry("bothWorkAndPinsTerminal", true),
                    Map.entry("bothOriginalPlayerFilesSameActive", true), Map.entry("normalStopProofIsSeparate", true)));
            r.complete = true;
        }
        return r.complete;
    }
    static void instance(ServerPlayer actor, Object value) {
        var r = exact(); var p = participant(r, actor); current(r, p);
        require(value instanceof ServerSlot.InstanceState && p.constructed == null, "ONE_CALL_LOCAL_CONSTRUCTOR");
        p.constructed = (ServerSlot.InstanceState) value;
        p.leaseBefore = r.slot.leases.get(p.reference);
        p.leaseOwnersBefore = leaseOwners(r.slot, p.constructed.lease);
        p.instancesBefore = r.slot.instances.size(); p.leasesBefore = r.slot.leases.size();
        p.originalWorkBefore = p.instance == null ? null : p.instance.work;
    }
    static void admitted(MinecraftServer server, ServerPlayer actor, SkillReference ref, Object result) {
        var r = exact(); var p = participant(r, actor); current(r, p);
        var instance = p.constructed;
        r.lastAdmission = admissionObservation(r, p, instance, result);
        require(server == r.server && ref.equals(p.reference) && instance != null, "EXACT_ADMISSION_OBSERVATION");
        if (result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly accepted) {
            require((p == r.a ? r.phase == 1 : r.phase == 2) && p.admissions++ == 0
                    && instance.id.equals(accepted.eventToken().skillInstanceId()) && instance.work != null
                    && !instance.lease.pin.isClosed() && r.slot.instances.get(instance.id) == instance
                    && instance.cooldownReceipt != null && instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.PENDING,
                    "ACTUAL_PUBLISHED_PENDING_ROOT");
            var event = r.slot.queue.stream().filter(e -> e.eventId().equals(accepted.eventToken().eventId())).findFirst().orElseThrow();
            var pending = entry(r, p);
            require(p.cell != null && pending != null && pending.kind == 1 && pending.duration == 600
                    && pending.attemptId.equals(instance.cooldownReceipt.attemptId())
                    && event.skillInstanceId().equals(instance.id) && event.createdRuntimeTick() == r.slot.runtimeTick
                    && event.scheduledRuntimeTick() == Math.addExact(event.createdRuntimeTick(), 1)
                    && pending.releaseNotAfter == pending.acceptedAt + event.deadlineRuntimeTick() - event.createdRuntimeTick(),
                    "PENDING_SAME_RECEIPT_AND_ORIGINAL_SCHEDULE");
            if (p == r.b) active(r, r.a);
            p.instance = instance; p.root = event; p.pending = pending;
        } else {
            require((p == r.a ? r.phase == 3 : r.phase == 4) && p.rejections == 0
                    && result instanceof RuntimeAdmissionResult.CooldownRejected rejected
                    && rejected.reason() == CooldownRejectionReason.ACTIVE && instance.work == null
                    && !r.slot.instances.containsKey(instance.id) && rejectedLeaseConserved(r, p, instance),
                    "ORIGINAL_ACTIVE_REFUSAL");
            active(r, p);
            p.rejections++; p.refusalObservation = r.lastAdmission;
        }
        p.constructed = null;
    }
    private static long leaseOwners(ServerSlot slot, RuntimeRevisionLease lease) {
        return slot.instances.values().stream().filter(value -> value.lease == lease).count();
    }
    private static boolean rejectedLeaseConserved(Run r, Participant p, ServerSlot.InstanceState rejected) {
        if (p.instance == null || rejected == p.instance || !rejected.lease.reference.equals(p.reference)
                || !rejected.lease.pin.reference().equals(p.reference)
                || r.slot.instances.size() != p.instancesBefore || r.slot.leases.size() != p.leasesBefore
                || leaseOwners(r.slot, rejected.lease) != p.leaseOwnersBefore) return false;
        if (p.leaseBefore != null) {
            return rejected.lease == p.leaseBefore && rejected.lease == p.instance.lease
                    && r.slot.leases.get(p.reference) == p.leaseBefore && !rejected.lease.pin.isClosed()
                    && p.leaseOwnersBefore == 1 && r.slot.instances.get(p.instance.id) == p.instance
                    && p.originalWorkBefore != null && p.instance.work == p.originalWorkBefore;
        }
        return rejected.lease != p.instance.lease && rejected.lease.pin.isClosed()
                && r.slot.leases.get(p.reference) == null && p.leaseOwnersBefore == 0
                && p.instance.work == null && p.instance.lease.pin.isClosed()
                && !r.slot.instances.containsKey(p.instance.id);
    }
    static Map<String, Object> admissionObservation() {
        return run == null ? Map.of("status", "UNAVAILABLE") : run.lastAdmission;
    }
    private static Map<String, Object> admissionObservation(Run r, Participant p,
            ServerSlot.InstanceState instance, Object result) {
        var facts = new LinkedHashMap<String, Object>();
        facts.put("status", "ACTUAL_ADMISSION_RETURN_BEFORE_HARNESS_ASSERTION");
        facts.put("role", p.role); facts.put("phase", r.phase); facts.put("runtimeTick", r.slot.runtimeTick);
        facts.put("resultKind", resultKind(result));
        facts.put("cooldownReason", result instanceof RuntimeAdmissionResult.CooldownRejected rejected
                ? rejected.reason().name() : "NOT_APPLICABLE");
        facts.put("constructorObserved", instance != null); facts.put("prospectiveWorkPresent", instance != null && instance.work != null);
        facts.put("prospectivePinClosed", instance != null && instance.lease.pin.isClosed());
        facts.put("prospectiveInstancePublished", instance != null && r.slot.instances.containsKey(instance.id));
        facts.put("leaseBranch", p.leaseBefore == null ? "NEW_PROVISIONAL" : "EXISTING_SHARED");
        facts.put("sameOriginalLease", instance != null && p.instance != null && instance.lease == p.instance.lease);
        facts.put("leaseOwnersBefore", p.leaseOwnersBefore);
        facts.put("leaseOwnersAfter", instance == null ? -1L : leaseOwners(r.slot, instance.lease));
        facts.put("instancesBefore", p.instancesBefore); facts.put("instancesAfter", r.slot.instances.size());
        facts.put("leasesBefore", p.leasesBefore); facts.put("leasesAfter", r.slot.leases.size());
        facts.put("originalWorkRetained", p.instance != null && p.originalWorkBefore != null && p.instance.work == p.originalWorkBefore);
        facts.put("originalPinClosed", p.instance != null && p.instance.lease.pin.isClosed());
        facts.put("aAccepted", r.a.admissions); facts.put("bAccepted", r.b.admissions);
        facts.put("aTransfers", r.a.transfers); facts.put("bTransfers", r.b.transfers);
        facts.put("aRefusalsBefore", r.a.rejections); facts.put("bRefusalsBefore", r.b.rejections);
        return Map.copyOf(facts);
    }
    private static String resultKind(Object result) {
        if (!(result instanceof RuntimeAdmissionResult admission)) return "UNAVAILABLE";
        return switch (admission) {
            case RuntimeAdmissionResult.AcceptedMemoryOnly ignored -> "ACCEPTED_MEMORY_ONLY";
            case RuntimeAdmissionResult.CooldownRejected ignored -> "COOLDOWN_REJECTED";
            case RuntimeAdmissionResult.OwnerInstanceUnavailable ignored -> "OWNER_INSTANCE_UNAVAILABLE";
            case RuntimeAdmissionResult.PersistentScheduleUnsupported ignored -> "PERSISTENT_SCHEDULE_UNSUPPORTED";
            case RuntimeAdmissionResult.DelayOutOfRange ignored -> "DELAY_OUT_OF_RANGE";
            case RuntimeAdmissionResult.DelayOverflow ignored -> "DELAY_OVERFLOW";
            case RuntimeAdmissionResult.DeadlineOutOfRange ignored -> "DEADLINE_OUT_OF_RANGE";
            case RuntimeAdmissionResult.DeadlineOverflow ignored -> "DEADLINE_OVERFLOW";
            case RuntimeAdmissionResult.DeadlineBeforeScheduledTick ignored -> "DEADLINE_BEFORE_SCHEDULED";
            case RuntimeAdmissionResult.InvalidRuntimeReference ignored -> "INVALID_RUNTIME_REFERENCE";
            case RuntimeAdmissionResult.SkillRevisionUnavailable ignored -> "SKILL_REVISION_UNAVAILABLE";
            case RuntimeAdmissionResult.InvalidEvent ignored -> "INVALID_EVENT";
            case RuntimeAdmissionResult.ActiveLineageCapacityExceeded ignored -> "ACTIVE_LINEAGE_CAPACITY";
            case RuntimeAdmissionResult.ActiveBudgetAttributionCapacityExceeded ignored -> "ACTIVE_ATTRIBUTION_CAPACITY";
            case RuntimeAdmissionResult.RootAdmissionBudgetExceeded ignored -> "ROOT_ADMISSION_BUDGET";
            case RuntimeAdmissionResult.CircuitBroken ignored -> "CIRCUIT_BROKEN";
            case RuntimeAdmissionResult.ServerNotRunning ignored -> "SERVER_NOT_RUNNING";
            case RuntimeAdmissionResult.ServerStopping ignored -> "SERVER_STOPPING";
            case RuntimeAdmissionResult.WrongThread ignored -> "WRONG_THREAD";
            case RuntimeAdmissionResult.SequenceExhausted ignored -> "SEQUENCE_EXHAUSTED";
            case RuntimeAdmissionResult.TickExhausted ignored -> "TICK_EXHAUSTED";
            case RuntimeAdmissionResult.KernelFaulted ignored -> "KERNEL_FAULTED";
        };
    }
    static void transferred(Object value, Object result) {
        var r = exact(); require(r.server.isSameThread() && value instanceof P9StarterProjectile, "TRANSFER_MAIN_PROJECTILE");
        var projectile = (P9StarterProjectile) value;
        Participant p = null;
        for (var candidate : List.of(r.a, r.b)) {
            if (candidate.instance != null && candidate.instance.activeProjectileContinuation != null
                    && projectile.hasContinuationPermitIdentity(candidate.instance.activeProjectileContinuation)) p = candidate;
        }
        require(p != null && result == RuntimePermitTransferDisposition.TRANSFERRED && p.transfers++ == 0,
                "ONE_ORIGINAL_TRANSFER_PER_OWNER");
        current(r, p); var permit = p.instance.activeProjectileContinuation; var material = entry(r, p);
        require(permit.state == RuntimeProjectileContinuationPermit.State.OPEN && projectile.isAddedToLevel()
                && !projectile.isRemoved() && projectile.getOwner() == p.actor && material != null && material.kind == 0
                && material.duration == 600 && material.attemptId.equals(p.pending.attemptId)
                && material.releasedAt == r.server.overworld().getGameTime() && material.expiresAt == material.releasedAt + 600
                && p.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                && p.instance.cooldownReceipt.releasedAt() == material.releasedAt
                && r.slot.runtimeTick < p.root.deadlineRuntimeTick(), "TRUE_ADD_OPEN_AND_EXACT_ARM");
        p.active = material; p.projectile = projectile; p.permit = permit; p.releaseTick = r.slot.runtimeTick;
        if (p == r.b) { active(r, r.a); r.bArmWhileAActive = true; }
    }
    static void claimed(Object result) { if (run != null && result == RuntimePermitClaimDisposition.QUEUED) run.claims++; }
    /** Existing original publish RETURN supplies opaque cell identity; no lookup or mutable map is retained. */
    public static void published(Object service, Object bodyValue, Object cell, Object replacement, Map<?, ?> actualCells) {
        var r = run; if (r == null || service != r.cooldowns) return;
        try {
            require(r.server.isSameThread(), "PUBLICATION_MAIN");
            Participant p = bodyValue == r.a.body ? r.a : bodyValue == r.b.body ? r.b : null;
            if (p == null || p.reference == null) return;
            require(replacement instanceof P11CastCooldownData && actualCells.get(p.actor.getUUID()) == cell
                    && actualCells.size() == 2 && p.body.cooldown.data == replacement, "ACTUAL_SOLE_CELL_PUBLICATION");
            var data = (P11CastCooldownData) replacement;
            require(data.kind == P11CastCooldownData.Kind.ROUTED && data.entries.size() <= 1
                    && (data.entries.isEmpty() || data.entries.containsKey(p.reference.skillId().value())), "NO_OTHER_OWNER_ENTRY");
            if (data.entries.isEmpty()) return;
            if (p.cell == null) p.cell = cell;
            require(p.cell == cell && (p == r.a ? r.b.cell == null || cell != r.b.cell : r.a.cell == null || cell != r.a.cell),
                    "ONE_DISTINCT_CELL_PER_EXACT_OWNER");
        } catch (RuntimeException | Error secondary) { r.observerFailed = true; }
    }
    static void release() { run = null; }
    private static void submit(Run r, Participant p, SkillReference old) throws IOException {
        var canonical = P9StarterSkillContent.canonicalDraft(old.skillId()); var first = canonical.nodes().getFirst();
        var payload = P9ActiveCastTriggerType.INSTANCE.payloadCodec().codec().encodeStart(JsonOps.INSTANCE,
                new P9ActiveCastTriggerPayloadV1(600)).getOrThrow();
        var replacement = new DraftNode(DraftTriggerSlot.present(new DefinitionEnvelope(P9StarterSkillContent.ACTIVE_CAST_ID,
                1, new Dynamic<>(JsonOps.INSTANCE, payload))), first.action(), first.appearanceOverride());
        applied(r.attachments.putDraft(p.actor, new SkillDraft(canonical.draftSchemaVersion(), old.skillId(), Optional.of(old.revision()),
                List.of(replacement, canonical.nodes().get(1)), canonical.appearance())));
        var outcome = r.submissions.submit(p.actor, old.skillId());
        require(outcome instanceof SkillSubmissionCompositionOutcome.Committed, "FORMAL_SUBMIT_COMMITTED");
        p.reference = ((SkillSubmissionCompositionOutcome.Committed) outcome).reference();
        require(p.reference.skillId().equals(old.skillId()) && p.reference.revision().value() > old.revision().value(), "EXACT_NEW_REVISION");
        applied(r.attachments.setEquipped(p.actor, 0, Optional.of(p.reference)));
        require(equipped(r, p).filter(p.reference::equals).isPresent(), "FORMAL_EQUIP_EXACT_OWNER");
        P11C4aEvidence.write(r.output, "formal-submission-" + p.role + ".json", Map.of("status", "FORMAL_V1_SUBMIT_AND_SLOT_ZERO_EQUIP",
                "role", p.role, "reference", p.reference.toString(), "cooldownTicks", 600, "serverOnlyDraftFixture", true));
    }
    private static Optional<SkillReference> equipped(Run r, Participant p) {
        var value = r.attachments.equippedAt(p.actor, 0);
        require(value instanceof PlayerSkillAttachmentService.Available<?>, "EQUIPMENT_AVAILABLE");
        return ((PlayerSkillAttachmentService.Available<Optional<SkillReference>>) value).value();
    }
    private static P11CastCooldownData.Entry entry(Run r, Participant p) {
        current(r, p); var data = p.body.cooldown.data;
        require(data != null && data.kind == P11CastCooldownData.Kind.ROUTED && data.entries.size() <= 1
                && (data.entries.isEmpty() || data.entries.containsKey(p.reference.skillId().value())), "EXACT_OWNER_ONLY_MATERIAL");
        return data.entries.get(p.reference.skillId().value());
    }
    private static void active(Run r, Participant p) {
        require(sameEntry(entry(r, p), p.active) && r.server.overworld().getGameTime() < p.active.expiresAt
                && p.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM, "KNOWN_ARM_STILL_ACTIVE");
    }
    private static boolean sameEntry(P11CastCooldownData.Entry value, P11CastCooldownData.Entry expected) {
        return value != null && expected != null && value.kind == 0 && value.skillId.equals(expected.skillId)
                && value.revision == expected.revision && value.duration == expected.duration && value.attemptId.equals(expected.attemptId)
                && value.acceptedAt == expected.acceptedAt && value.releaseNotAfter == expected.releaseNotAfter
                && value.releasedAt == expected.releasedAt && value.expiresAt == expected.expiresAt;
    }
    private static boolean distinct(Object aCell, Object bCell, P11CastCooldownData.Entry a, P11CastCooldownData.Entry b) {
        return aCell != null && bCell != null && aCell != bCell && a != null && b != null
                && !a.skillId.equals(b.skillId) && !a.attemptId.equals(b.attemptId) && a.duration == 600 && b.duration == 600;
    }
    private static boolean terminal(Run r, Participant p) {
        return p.instance != null && p.projectile != null && p.instance.work == null && p.instance.lease.pin.isClosed()
                && p.instance.activeProjectileContinuation == null && p.projectile.isRemoved()
                && !r.slot.instances.containsKey(p.instance.id) && !r.slot.activeProjectileContinuations.containsKey(p.permit.permitId);
    }
    private static void physical(Run r, Participant p) throws IOException {
        var writer = r.source.diagnostics(p.actor.getUUID()).writers().stream().filter(w -> w.kind().equals("PLAYER_DATA")).findFirst().orElseThrow();
        require(!writer.dirty() && writer.terminal().equals("COMPLETED") && writer.encode().equals("SUCCEEDED")
                && writer.write().equals("SUCCEEDED") && writer.close().equals("SUCCEEDED") && writer.replace().equals("SUCCEEDED"),
                "ORIGINAL_PLAYER_WRITER_COMPLETE");
        var file = r.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.actor.getUUID() + ".dat");
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)
                && Files.size(file) <= 32L * 1024 * 1024, "BOUNDED_ORIGINAL_PLAYER_FILE");
        var nbt = NbtIo.readCompressed(file, NbtAccounter.create(32L * 1024 * 1024));
        require(nbt.hasUUID("UUID") && nbt.getUUID("UUID").equals(p.actor.getUUID()), "PHYSICAL_EXACT_OWNER");
        var data = P11CastCooldownCodec.read(nbt.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).get(P11CastCooldownAttachments.ID.toString()));
        require(data.kind == P11CastCooldownData.Kind.ROUTED && data.entries.size() == 1
                && sameEntry(data.entries.get(p.reference.skillId().value()), p.active), "PHYSICAL_ONLY_OWN_SAME_ACTIVE");
        p.physicalHash = P11C4aEvidence.hash(Files.readAllBytes(file)); p.writerAttempt = writer.attempt();
    }
    private static void writeParticipant(Run r, Participant p) throws IOException {
        var facts = new LinkedHashMap<String, Object>();
        facts.put("status", "ORIGINAL_R_ARM_ACTIVE_REFUSAL_AND_PHYSICAL_OWNER_ISOLATION"); facts.put("role", p.role);
        facts.put("reference", p.reference.toString()); facts.put("acceptedRoots", p.admissions); facts.put("activeRefusals", p.rejections);
        facts.put("nativeTransfers", p.transfers); facts.put("acceptedRuntimeTick", p.root.createdRuntimeTick());
        facts.put("scheduledRuntimeTick", p.root.scheduledRuntimeTick()); facts.put("releaseRuntimeTick", p.releaseTick);
        facts.put("acceptedAt", p.active.acceptedAt); facts.put("releasedAt", p.active.releasedAt); facts.put("expiresAt", p.active.expiresAt);
        facts.put("durationTicks", 600); facts.put("playerDataSha256", p.physicalHash); facts.put("originalWriterAttempt", p.writerAttempt);
        facts.put("sameActualActorConnectionAndSourceAccount", true); facts.put("otherOwnerEntryAbsent", true);
        facts.put("workAndPinTerminal", true); facts.put("clientHudProofSeparate", true);
        facts.put("activeRefusalObservation", p.refusalObservation);
        P11C4aEvidence.write(r.output, "cooldown-dual-" + p.role + ".json", facts);
    }
    private static void current(Run r, Participant p) {
        var a = p.actor;
        require(r.server.isSameThread() && a.getServer() == r.server && !a.isFakePlayer() && !a.isRemoved() && a.isAlive()
                && a.connection.player == a && a.connection.getConnection() == p.connection && p.connection.isConnected()
                && p.connection.isEncrypted() && !p.connection.isMemoryConnection() && p.connection.getPacketListener() == a.connection
                && r.server.getPlayerList().getPlayer(a.getUUID()) == a && r.source.body(a) == p.body && r.source.canCopy(p.body)
                && p.body.account.current == p.body, "EXACT_CURRENT_AUTHENTICATED_SOURCE");
    }
    private static Participant participant(Run r, ServerPlayer actor) {
        require(actor == r.a.actor || actor == r.b.actor, "EXACT_OBSERVED_ACTOR"); return actor == r.a.actor ? r.a : r.b;
    }
    private static void applied(PlayerSkillAttachmentService.Result<?> value) {
        require(value instanceof PlayerSkillAttachmentService.Available<?> a && a.value() == PlayerSkillAttachmentService.Applied.INSTANCE,
                "ORIGINAL_FORMAL_ATTACHMENT_OPERATION");
    }
    private static boolean clientReceipt(String role, String leaf) throws IOException {
        return P11C4aEvidence.receiptPresent(P11C4aEvidence.root().resolve("client-" + role), leaf);
    }
    private static void cue(Run r, String leaf) throws IOException { P11C4aEvidence.cue(r.output, leaf); }
    private static Run exact() { require(run != null && selected(), "SELECTED_RUN"); return run; }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "COOLDOWN_DUAL_" + code); }
    private static final class Participant {
        final String role; final ServerPlayer actor; final Connection connection; final P11QualifiedSourceOwner.Body body; final float health;
        SkillReference reference; Object cell; ServerSlot.InstanceState constructed, instance; RuntimeEvent root;
        P11CastCooldownData.Entry pending, active; P9StarterProjectile projectile; RuntimeProjectileContinuationPermit permit;
        int admissions, rejections, transfers; long releaseTick, writerAttempt; String physicalHash;
        RuntimeRevisionLease leaseBefore; Object originalWorkBefore; long leaseOwnersBefore;
        int instancesBefore, leasesBefore; Map<String, Object> refusalObservation;
        Participant(String role, ServerPlayer actor, P11QualifiedSourceOwner.Body body) {
            this.role = role; this.actor = actor; connection = actor.connection.getConnection(); this.body = body; health = actor.getHealth();
        }
    }
    private static final class Run {
        final MinecraftServer server; final ServerSlot slot; final P11QualifiedSourceOwner source; final P11CastCooldownService cooldowns;
        final PlayerSkillAttachmentService attachments; final SkillDefinitionSubmissionService submissions; final Path output;
        final Participant a, b; int ticks, phase, claims; boolean observerFailed, bArmWhileAActive, complete;
        Map<String, Object> lastAdmission = Map.of("status", "UNAVAILABLE");
        Run(MinecraftServer server, ServerSlot slot, P11QualifiedSourceOwner source, P11CastCooldownService cooldowns,
                PlayerSkillAttachmentService attachments, SkillDefinitionSubmissionService submissions, Path output, Participant a, Participant b) {
            this.server = server; this.slot = slot; this.source = source; this.cooldowns = cooldowns; this.attachments = attachments;
            this.submissions = submissions; this.output = output; this.a = a; this.b = b;
        }
    }
}
