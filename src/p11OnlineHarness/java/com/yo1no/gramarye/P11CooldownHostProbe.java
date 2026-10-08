package com.yo1no.gramarye;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/** Excluded host prefix/stop proof. Original host-refusal/Stay/ACK/Leave remains the terminal driver. */
public final class P11CooldownHostProbe {
    private static PlayerSkillAttachmentService attachments;
    private static SkillDefinitionSubmissionService submissions;
    private static SkillDefinitionStoreService store;
    private static volatile Run run;
    private P11CooldownHostProbe() { }

    public static boolean selected() {
        return P11C4aScenario.MODE == P11C4aScenario.Mode.COOLDOWN_HOST
                && "c4a-host-lan".equals(System.getProperty("gramarye.p11.online.case", ""));
    }
    public static void composition(PlayerSkillAttachmentService actualAttachments,
            SkillDefinitionSubmissionService actualSubmissions, SkillDefinitionStoreService actualStore) {
        if (!selected()) return;
        require(attachments == null && actualAttachments != null && actualSubmissions != null && actualStore != null,
                "ORIGINAL_COMPOSITION_ONCE");
        attachments = actualAttachments; submissions = actualSubmissions; store = actualStore;
    }
    static boolean started() { return run != null; }
    static void start(MinecraftServer server, ServerPlayer host, ServerPlayer peer, Path output) throws IOException {
        require(selected() && run == null && attachments != null && submissions != null && store != null
                && server.isSameThread() && server.isSingleplayer() && !server.isDedicatedServer()
                && server.usesAuthentication() && server.isPublished()
                && host != peer && !host.isFakePlayer() && !peer.isFakePlayer()
                && server.isSingleplayerOwner(host.getGameProfile()) && !server.isSingleplayerOwner(peer.getGameProfile())
                && host.connection.getConnection().isMemoryConnection()
                && !peer.connection.getConnection().isMemoryConnection() && peer.connection.getConnection().isEncrypted(),
                "ORIGINAL_HOST_AND_AUTHENTICATED_PEER");
        var r = new Run(server, host, peer, output); current(r); run = r;
        require(r.peerBody.cooldown.data.kind == P11CastCooldownData.Kind.ROUTED
                && r.peerBody.cooldown.data.entries.isEmpty(), "FRESH_PEER_DATUM");
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            var block = new BlockPos(x, 99, z); require(host.serverLevel().isLoaded(block), "LOADED_ARENA");
            host.serverLevel().setBlock(block, Blocks.STONE.defaultBlockState(), 3);
        }
        host.teleportTo(host.serverLevel(), .5, 100, .5, java.util.Set.of(), 0, -70);
        peer.teleportTo(peer.serverLevel(), 2.5, 100, .5, java.util.Set.of(), 90, 0);
        P11C4aEvidence.cue(output, "cooldown-host-starter.ready");
    }
    /** True only after one real root has terminated and all three original save routes proved the datum. */
    static boolean prefix() throws IOException {
        var r = exact(); r.stage = Stage.PREFIX_CURRENT; current(r); check(r);
        require(++r.ticks <= 2400, "FINITE_PREFIX");
        if (r.saved) return true;
        if (r.reference == null) {
            r.stage = Stage.WAIT_STARTER;
            if (!P11C4aEvidence.cuePresent(r.output.getParent().resolve("client-host"), "cooldown-host-starter-sent.ready")) return false;
            var selected = attachments.equippedAt(r.host, 0);
            require(selected instanceof PlayerSkillAttachmentService.Available<?>, "EQUIPMENT_AVAILABLE");
            var old = ((PlayerSkillAttachmentService.Available<Optional<SkillReference>>) selected).value();
            if (old.isEmpty()) return false;
            r.stage = Stage.FORMAL_SUBMISSION;
            submit(r, old.orElseThrow()); P11C4aEvidence.cue(r.output, "cooldown-host-cast.ready"); return false;
        }
        r.stage = Stage.WAIT_WORK_TERMINAL;
        if (r.transfers != 1 || !terminal(r)) return false;
        r.stage = Stage.EMPTY_RAY_GUARD;
        require(r.accepted == 1 && r.claims == 0 && r.host.totalExperience == r.xp
                && r.peer.totalExperience == r.peerXp && r.peer.getHealth() == r.peerHealth, "ONE_EMPTY_RAY_NO_REWARD_OR_PEER_EFFECT");
        r.stage = Stage.LIVE_OBLIGATION; liveObligation(r);
        r.stage = Stage.SOURCE_BEFORE_SAVE;
        var before = r.source.diagnostics(r.host.getUUID());
        r.beforeSave = before;
        r.stage = Stage.ORIGINAL_SAVE;
        r.server.saveEverything(false, true, true);
        r.originalSaveReturned = true;
        var after = writers(r, before);
        var files = readMaterials(r);
        r.stage = Stage.SAVED_RECEIPT;
        var facts = facts(r, "ORIGINAL_CACHE_PLAYER_AND_LEVEL_SAVE_WITH_ACTIVE_COOLDOWN");
        facts.put("sourceBefore", P11C4aEvidence.sourceObservation(before));
        facts.put("sourceAfter", P11C4aEvidence.sourceObservation(after)); facts.put("materials", files);
        P11C4aEvidence.write(r.output, "cooldown-host-saved.json", facts); r.saved = true;
        return true;
    }
    /** Called throughout the unchanged refusal interval, before its terminal Leave intent. */
    static void refusalCurrent() {
        var r = exact(); check(r); current(r); liveObligation(r);
        require(r.saved && terminal(r) && r.claims == 0 && r.peerBody.cooldown.data.entries.isEmpty(),
                "REFUSAL_DOES_NOT_REPLAY_OR_REFUND");
    }
    private static void submit(Run r, SkillReference old) throws IOException {
        var canonical = P9StarterSkillContent.canonicalDraft(old.skillId());
        var first = canonical.nodes().getFirst();
        var payload = P9ActiveCastTriggerType.INSTANCE.payloadCodec().codec()
                .encodeStart(JsonOps.INSTANCE, new P9ActiveCastTriggerPayloadV1(600)).getOrThrow();
        var trigger = DraftTriggerSlot.present(new DefinitionEnvelope(P9StarterSkillContent.ACTIVE_CAST_ID, 1,
                new Dynamic<>(JsonOps.INSTANCE, payload)));
        var draft = new SkillDraft(canonical.draftSchemaVersion(), old.skillId(), Optional.of(old.revision()),
                List.of(new DraftNode(trigger, first.action(), first.appearanceOverride()), canonical.nodes().get(1)), canonical.appearance());
        applied(attachments.putDraft(r.host, draft));
        var submitted = submissions.submit(r.host, old.skillId());
        require(submitted instanceof SkillSubmissionCompositionOutcome.Committed, "FORMAL_SUBMISSION");
        r.reference = ((SkillSubmissionCompositionOutcome.Committed) submitted).reference();
        require(r.reference.skillId().equals(old.skillId()) && r.reference.revision().value() > old.revision().value(), "EXACT_NEW_REVISION");
        applied(attachments.setEquipped(r.host, 0, Optional.of(r.reference)));
        P11C4aEvidence.write(r.output, "cooldown-host-formal.json", Map.of("status", "FORMAL_HOST_V1_SUBMIT_AND_EQUIP",
                "reference", r.reference.toString(), "cooldownTicks", 600, "hostHasJoinedClaim", false));
    }
    public static void instance(ServerPlayer actor, Object value) {
        var r = run; if (r == null || actor != r.host) return;
        observe(r, () -> { require(r.server.isSameThread() && r.instance == null && value instanceof ServerSlot.InstanceState,
                "ONE_ACTUAL_INSTANCE"); r.instance = (ServerSlot.InstanceState) value; });
    }
    public static void accepted(MinecraftServer server, ServerPlayer actor, SkillReference reference, Object result) {
        var r = run; if (r == null || actor != r.host) return;
        observe(r, () -> {
            require(server == r.server && server.isSameThread() && r.reference.equals(reference) && ++r.accepted == 1
                    && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly && r.instance != null
                    && r.instance.id.equals(((RuntimeAdmissionResult.AcceptedMemoryOnly) result).eventToken().skillInstanceId())
                    && r.instance.work != null && r.instance.work.qualifies(actor) && !r.instance.lease.pin.isClosed()
                    && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.PENDING, "ACTUAL_PENDING_ROOT");
            var pending = entry(r); require(pending.kind == 1 && pending.duration == 600
                    && pending.attemptId.equals(r.instance.cooldownReceipt.attemptId()), "SOLE_PENDING_DATUM");
            r.pending = pending;
        });
    }
    public static void transferred(Object value, Object result) {
        var r = run; if (r == null || !(value instanceof P9StarterProjectile projectile)
                || r.instance == null || !projectile.hasContinuationPermitIdentity(r.instance.activeProjectileContinuation)) return;
        observe(r, () -> {
            require(r.server.isSameThread() && r.accepted == 1 && ++r.transfers == 1
                    && result == RuntimePermitTransferDisposition.TRANSFERRED && projectile.getOwner() == r.host
                    && projectile.isAddedToLevel() && !projectile.isRemoved(), "ORIGINAL_TRUE_TRANSFER");
            r.projectile = projectile; r.permit = r.instance.activeProjectileContinuation;
            r.active = entry(r);
            require(r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN && r.active.kind == 0
                    && r.active.duration == 600 && r.active.revision == r.reference.revision().value()
                    && r.pending.attemptId.equals(r.active.attemptId) && r.pending.acceptedAt == r.active.acceptedAt
                    && r.pending.releaseNotAfter == r.active.releaseNotAfter
                    && r.active.releasedAt == r.server.overworld().getGameTime()
                    && r.active.expiresAt == Math.addExact(r.active.releasedAt, 600)
                    && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                    && r.instance.cooldownReceipt.releasedAt() == r.active.releasedAt, "GENUINE_HOST_ARM");
            P11C4aEvidence.write(r.output, "cooldown-host-arm.json", facts(r, "ORIGINAL_HOST_R_NATIVE_ARM"));
        });
    }
    public static void claimed(Object disposition) {
        var r = run; if (r != null && disposition == RuntimePermitClaimDisposition.QUEUED) r.claims++;
    }
    public static void beforeRoot(MinecraftServer server) {
        var r = run; if (r == null || r.server != server) return;
        observe(r, () -> {
            require(server.isSameThread() && r.saved && P11C4aHostLeaveProbe.terminalIntent() && terminal(r), "ORIGINAL_LEAVE_BEFORE_RETIREMENT");
            liveObligation(r);
            r.stopSource = P11C4aEvidence.sourceObservation(writers(r, null));
            r.stopCacheFloor = material(r, r.server.getPlayerList().getSingleplayerData(), "CACHE").clockFloor;
            r.beforeRoot = true;
        });
    }
    public static void rootRetired(MinecraftServer server) {
        var r = run; if (r == null || r.server != server) return;
        observe(r, () -> { require(server.isSameThread() && !r.retired, "ONE_ROOT_RETIREMENT");
            r.summary = P11NativeStorageBoundary.terminalDiagnostics(); r.retired = true; });
        seal(r);
    }
    static void stopped(MinecraftServer server) {
        var r = run; if (r == null || r.server != server) return;
        observe(r, () -> { require(server.isSameThread() && !r.stopped && P11C4aHostLeaveProbe.serverStopped(),
                "ORIGINAL_HOST_REFUSAL_STOP"); r.stopped = true; }); seal(r);
    }
    private static void seal(Run r) {
        if (!r.stopped || !r.retired || r.written) return;
        try {
            check(r); require(r.beforeRoot && terminal(r) && r.claims == 0, "COMPLETE_PREFIX_AND_STOP");
            var s = r.summary;
            require(s != null && s.nativeStopNormal() && s.failures() == 0 && s.resources().dirtyUuids() == 0
                    && s.nativeResponsibilities().roots().size() == 5
                    && s.nativeResponsibilities().roots().stream().allMatch(value -> value.count() == 0), "ACTUAL_SOURCE_TERMINAL");
            var files = readMaterials(r);
            var facts = facts(r, "ORIGINAL_HOST_REFUSAL_LEAVE_PRESERVES_DURABLE_COOLDOWN");
            facts.put("sourceBeforeRetirement", r.stopSource); facts.put("materials", files);
            facts.put("cacheFloorBeforeRetirement", r.stopCacheFloor);
            facts.put("nativeStopNormal", s.nativeStopNormal()); facts.put("sourceFailures", s.failures());
            facts.put("dirtyUuids", s.resources().dirtyUuids());
            facts.put("allRootCounts", s.nativeResponsibilities().roots().stream().map(value -> Map.of("kind", value.kind(), "count", value.count())).toList());
            P11C4aEvidence.write(r.output, "cooldown-host-stopped.json", facts); r.written = true;
        } catch (IOException | RuntimeException | Error problem) {
            fail(r); try { P11C4aEvidence.write(r.output, "cooldown-host-failure.json", Map.of("status", "FAIL", "code", r.failure)); }
            catch (IOException | RuntimeException | Error ignored) { /* Never replace the original stop policy. */ }
        }
    }
    static boolean complete() { var r = run; return r != null && r.written && r.failure == null; }
    private static P11QualifiedSourceOwner.Diagnostics writers(Run r, P11QualifiedSourceOwner.Diagnostics before) {
        r.stage = Stage.SOURCE_AFTER_SAVE;
        var current = r.source.diagnostics(r.host.getUUID());
        r.afterSave = current;
        require(current.sourceEpoch() == r.epoch && current.sourceVersion() == r.body.source.version(), "CURRENT_SOURCE_WRITERS");
        for (String name : List.of("CACHE", "PLAYER_DATA", "LEVEL_PLAYER")) {
            r.writer = Writer.valueOf(name); r.stage = Stage.CURRENT_WRITER_LOOKUP;
            var w = current.writers().stream().filter(value -> value.kind().equals(name)).findFirst().orElseThrow();
            r.stage = Stage.CURRENT_WRITER_ASSERT;
            require(!w.dirty() && w.terminal().equals("COMPLETED") && w.encode().equals("SUCCEEDED")
                    && (name.equals("CACHE") ? w.cacheAssignment().equals("SUCCEEDED")
                        : w.write().equals("SUCCEEDED") && w.close().equals("SUCCEEDED") && w.replace().equals("SUCCEEDED")), "EXACT_NATIVE_WRITER_STAGES");
            if (before != null) {
                r.stage = Stage.PRIOR_WRITER_LOOKUP;
                var prior = before.writers().stream().filter(value -> value.kind().equals(name)).findFirst().orElse(null);
                r.stage = Stage.PRIOR_WRITER_ASSERT;
                require(w.attempt() > 0 && (prior == null || w.attempt() > prior.attempt()), "NEW_ORIGINAL_SAVE_ATTEMPT");
            }
        }
        return current;
    }
    private static Map<String, Object> readMaterials(Run r) throws IOException {
        r.stage = Stage.READ_MATERIALS;
        long now = r.server.overworld().getGameTime(); require(now < r.active.expiresAt, "NATURAL_D600_WINDOW_NOT_EXTENDED");
        var cache = material(r, r.server.getPlayerList().getSingleplayerData(), "CACHE");
        var pdPath = r.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(r.host.getUUID() + ".dat");
        var levelPath = r.server.getWorldPath(LevelResource.ROOT).resolve("level.dat");
        var pd = material(r, nbt(pdPath), "PLAYER_DATA");
        var root = nbt(levelPath); require(root.get("Data") instanceof CompoundTag
                && root.getCompound("Data").get("Player") instanceof CompoundTag, "REAL_LEVEL_PLAYER_ENVELOPE");
        var level = material(r, root.getCompound("Data").getCompound("Player"), "LEVEL_PLAYER");
        require(cache.clockFloor >= r.savedFloor && pd.clockFloor >= r.savedFloor && level.clockFloor >= r.savedFloor,
                "CLOCK_FLOOR_NOT_REWOUND");
        r.savedFloor = Math.min(cache.clockFloor, Math.min(pd.clockFloor, level.clockFloor));
        return Map.of("cacheFloor", cache.clockFloor, "playerFloor", pd.clockFloor, "levelPlayerFloor", level.clockFloor,
                "playerDataSha256", hash(pdPath), "levelDataSha256", hash(levelPath), "nativeGameTime", now,
                "originalCacheNotPhysicalDurability", true, "bothPhysicalPlayerRoutesRead", true);
    }
    private static P11CastCooldownData material(Run r, CompoundTag tag, String route) {
        require(tag != null && tag.hasUUID("UUID") && tag.getUUID("UUID").equals(r.host.getUUID())
                && tag.get(AttachmentHolder.ATTACHMENTS_NBT_KEY) instanceof CompoundTag, "EXACT_" + route + "_OWNER");
        var data = P11CastCooldownCodec.read(tag.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).get(P11CastCooldownAttachments.ID.toString()));
        require(data.kind == P11CastCooldownData.Kind.ROUTED && data.entries.size() == 1
                && same(data.entries.get(r.reference.skillId().value()), r.active)
                && data.clockFloor >= r.active.releasedAt && data.clockFloor <= r.server.overworld().getGameTime(), "EXACT_" + route + "_DATUM");
        return data;
    }
    private static void liveObligation(Run r) {
        require(r.active != null && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                && same(entry(r), r.active) && r.server.overworld().getGameTime() < r.active.expiresAt, "KNOWN_ARM_NOT_REFUNDED");
    }
    private static P11CastCooldownData.Entry entry(Run r) {
        require(r.source.body(r.host) == r.body && r.body.cooldown != null && r.body.cooldown.current(r.host)
                && r.body.cooldown.data.kind == P11CastCooldownData.Kind.ROUTED, "CURRENT_BOUND_DATUM");
        var entry = r.body.cooldown.data.entries.get(r.reference.skillId().value()); require(entry != null, "EXACT_ENTRY"); return entry;
    }
    private static boolean same(P11CastCooldownData.Entry actual, P11CastCooldownData.Entry expected) {
        return actual != null && expected != null && actual.kind == 0 && actual.skillId.equals(expected.skillId)
                && actual.revision == expected.revision && actual.duration == expected.duration
                && actual.acceptedAt == expected.acceptedAt && actual.attemptId.equals(expected.attemptId)
                && actual.releaseNotAfter == expected.releaseNotAfter && actual.releasedAt == expected.releasedAt
                && actual.expiresAt == expected.expiresAt;
    }
    private static boolean terminal(Run r) {
        return r.instance != null && r.projectile != null && r.instance.work == null && r.instance.lease.pin.isClosed()
                && r.instance.activeProjectileContinuation == null && r.permit.state == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                && r.projectile.isRemoved() && r.body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()] == 0;
    }
    private static void current(Run r) {
        require(r.server.isSameThread() && r.host.connection.getConnection().isConnected() && r.peer.connection.getConnection().isConnected()
                && r.server.getPlayerList().getPlayer(r.host.getUUID()) == r.host && r.server.getPlayerList().getPlayer(r.peer.getUUID()) == r.peer
                && r.source.body(r.host) == r.body && r.source.body(r.peer) == r.peerBody && r.body.source.epoch() == r.epoch
                && r.source.canCopy(r.body) && r.source.canCopy(r.peerBody), "SAME_NATIVE_SOURCE_AND_BODIES");
    }
    private static Map<String, Object> facts(Run r, String status) {
        var f = new LinkedHashMap<String, Object>(); f.put("status", status); f.put("reference", r.reference.toString());
        f.put("accepted", r.accepted); f.put("originalTransfers", r.transfers); f.put("queuedHitChildren", r.claims);
        f.put("receiptFact", r.instance.cooldownReceipt.fact().name()); f.put("sourceEpoch", r.epoch);
        f.put("obligation", Map.of("attemptId", r.active.attemptId.toString(), "acceptedAt", r.active.acceptedAt,
                "releaseNotAfter", r.active.releaseNotAfter, "releasedAt", r.active.releasedAt, "expiresAt", r.active.expiresAt, "duration", 600));
        f.put("workPinEntityTerminal", terminal(r)); f.put("hostMemoryConnection", true);
        f.put("hostHasJoinedClaim", false); f.put("physicalOsInputClaim", false); return f;
    }
    private static CompoundTag nbt(Path path) throws IOException { shape(path); return NbtIo.readCompressed(path, NbtAccounter.create(32L * 1024 * 1024)); }
    private static String hash(Path path) throws IOException { shape(path); return P11C4aEvidence.hash(Files.readAllBytes(path)); }
    private static void shape(Path path) throws IOException { require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
            && !Files.isSymbolicLink(path) && path.toAbsolutePath().normalize().equals(path.toRealPath())
            && Files.size(path) > 0 && Files.size(path) <= 4L * 1024 * 1024, "OWNED_PHYSICAL_FILE"); }
    private static void applied(PlayerSkillAttachmentService.Result<?> result) {
        require(result instanceof PlayerSkillAttachmentService.Available<?> available
                && available.value() == PlayerSkillAttachmentService.Applied.INSTANCE, "FORMAL_ATTACHMENT_SERVICE");
    }
    private static void observe(Run r, Observation action) { try { action.run(); } catch (IOException | RuntimeException | Error problem) { recordFailure(r, problem); fail(r); } }
    private static void fail(Run r) { if (r.failure == null) r.failure = "COOLDOWN_HOST_OBSERVER_FAILED"; }
    private static void check(Run r) { require(r.failure == null, "OBSERVER_FAILED"); }
    private static Run exact() { require(run != null, "STARTED"); return run; }
    private static void require(boolean condition, String code) {
        if (!condition) {
            var r = run;
            if (r != null && r.category == FailureCategory.NONE && r.assertion == AssertionCode.NONE) { r.assertion = assertionCode(code); }
        }
        P11C4aEvidence.require(condition, "COOLDOWN_HOST_" + code);
    }
    /** Called only by the selected original scene catch, before unchanged failure/stop handling. */
    static void sceneFailure(Throwable primary) {
        var r = run; if (!selected() || r == null || !r.server.isSameThread() || r.diagnosticWritten) return;
        try {
            recordFailure(r, primary);
            var facts = new LinkedHashMap<String, Object>();
            facts.put("status", "FAILURE_DIAGNOSTIC_NOT_ACCEPTANCE");
            facts.put("stage", r.firstStage.name()); facts.put("category", r.category.name());
            facts.put("assertion", r.assertion.name()); facts.put("writer", r.firstWriter.name());
            facts.put("originalSaveReturned", r.originalSaveReturned);
            facts.put("accepted", r.accepted); facts.put("transfers", r.transfers); facts.put("claims", r.claims);
            facts.put("savedReceiptWritten", r.saved);
            facts.put("workPresent", r.instance != null && r.instance.work != null);
            facts.put("pinClosed", r.instance != null && r.instance.lease.pin.isClosed());
            facts.put("projectileRemoved", r.projectile != null && r.projectile.isRemoved());
            facts.put("beforeWriters", diagnosticWriters(r.beforeSave));
            facts.put("afterWriters", diagnosticWriters(r.afterSave));
            P11C4aEvidence.write(r.output, "cooldown-host-diagnostic.json", facts);
            r.diagnosticWritten = true;
        } catch (IOException | RuntimeException | Error ignored) { /* Diagnostic failure never replaces the original scene failure. */ }
    }
    private static void recordFailure(Run r, Throwable primary) {
        if (r.category != FailureCategory.NONE) return;
        r.firstStage = r.stage; r.firstWriter = r.writer;
        r.category = failureCategory(primary);
    }
    private static FailureCategory failureCategory(Throwable primary) {
        if (primary instanceof java.util.NoSuchElementException) return FailureCategory.NO_SUCH_ELEMENT;
        if (primary instanceof NullPointerException) return FailureCategory.NULL_POINTER;
        if (primary instanceof IOException) return FailureCategory.IO;
        if (primary instanceof IllegalArgumentException) return FailureCategory.ILLEGAL_ARGUMENT;
        if (primary instanceof LinkageError) return FailureCategory.LINKAGE;
        if (primary instanceof RuntimeException) return FailureCategory.RUNTIME;
        if (primary instanceof Error) return FailureCategory.ERROR;
        return FailureCategory.OTHER;
    }
    private static AssertionCode assertionCode(String code) {
        try { return AssertionCode.valueOf(code); }
        catch (IllegalArgumentException ignored) { return AssertionCode.OTHER; }
    }
    private static Map<String, Object> diagnosticWriters(P11QualifiedSourceOwner.Diagnostics value) {
        if (value == null) return Map.of("observed", false);
        return Map.of("observed", true, "sourceEpoch", value.sourceEpoch(), "sourceVersion", value.sourceVersion(),
                "writers", value.writers().stream().map(w -> Map.of("kind", w.kind(), "attempt", w.attempt(),
                    "dirty", w.dirty(), "terminal", w.terminal(), "encode", w.encode(), "write", w.write(),
                    "close", w.close(), "replace", w.replace(), "cacheAssignment", w.cacheAssignment())).toList());
    }
    private enum Stage { NONE, PREFIX_CURRENT, WAIT_STARTER, FORMAL_SUBMISSION, WAIT_WORK_TERMINAL,
        EMPTY_RAY_GUARD, LIVE_OBLIGATION, SOURCE_BEFORE_SAVE, ORIGINAL_SAVE, SOURCE_AFTER_SAVE,
        CURRENT_WRITER_LOOKUP, CURRENT_WRITER_ASSERT, PRIOR_WRITER_LOOKUP, PRIOR_WRITER_ASSERT,
        READ_MATERIALS, SAVED_RECEIPT }
    private enum Writer { NONE, CACHE, PLAYER_DATA, LEVEL_PLAYER }
    private enum FailureCategory { NONE, NO_SUCH_ELEMENT, NULL_POINTER, IO, ILLEGAL_ARGUMENT, LINKAGE, RUNTIME, ERROR, OTHER }
    private enum AssertionCode { NONE, OTHER, ORIGINAL_COMPOSITION_ONCE, ORIGINAL_HOST_AND_AUTHENTICATED_PEER,
        FRESH_PEER_DATUM, LOADED_ARENA, FINITE_PREFIX, EQUIPMENT_AVAILABLE, ONE_EMPTY_RAY_NO_REWARD_OR_PEER_EFFECT,
        REFUSAL_DOES_NOT_REPLAY_OR_REFUND, FORMAL_SUBMISSION, EXACT_NEW_REVISION, ONE_ACTUAL_INSTANCE,
        ACTUAL_PENDING_ROOT, SOLE_PENDING_DATUM, ORIGINAL_TRUE_TRANSFER, GENUINE_HOST_ARM,
        ORIGINAL_LEAVE_BEFORE_RETIREMENT, ONE_ROOT_RETIREMENT, ORIGINAL_HOST_REFUSAL_STOP,
        COMPLETE_PREFIX_AND_STOP, ACTUAL_SOURCE_TERMINAL, CURRENT_SOURCE_WRITERS, EXACT_NATIVE_WRITER_STAGES,
        NEW_ORIGINAL_SAVE_ATTEMPT, NATURAL_D600_WINDOW_NOT_EXTENDED, REAL_LEVEL_PLAYER_ENVELOPE,
        CLOCK_FLOOR_NOT_REWOUND, EXACT_CACHE_OWNER, EXACT_PLAYER_DATA_OWNER, EXACT_LEVEL_PLAYER_OWNER,
        EXACT_CACHE_DATUM, EXACT_PLAYER_DATA_DATUM, EXACT_LEVEL_PLAYER_DATUM, KNOWN_ARM_NOT_REFUNDED,
        CURRENT_BOUND_DATUM, EXACT_ENTRY, SAME_NATIVE_SOURCE_AND_BODIES, OWNED_PHYSICAL_FILE,
        FORMAL_ATTACHMENT_SERVICE, OBSERVER_FAILED, STARTED, SOURCE_PRESENT, BODIES_PRESENT }
    @FunctionalInterface private interface Observation { void run() throws IOException; }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer host, peer; final Path output;
        final P11QualifiedSourceOwner source; final P11QualifiedSourceOwner.Body body, peerBody;
        final long epoch; final int xp, peerXp; final float peerHealth;
        SkillReference reference; ServerSlot.InstanceState instance; P9StarterProjectile projectile;
        RuntimeProjectileContinuationPermit permit; P11CastCooldownData.Entry pending, active;
        int ticks, accepted, transfers, claims; long savedFloor, stopCacheFloor;
        boolean saved, beforeRoot, retired, stopped; volatile boolean written; String failure;
        Stage stage = Stage.NONE, firstStage = Stage.NONE; Writer writer = Writer.NONE, firstWriter = Writer.NONE;
        FailureCategory category = FailureCategory.NONE; AssertionCode assertion = AssertionCode.NONE;
        boolean originalSaveReturned, diagnosticWritten;
        P11QualifiedSourceOwner.Diagnostics beforeSave, afterSave;
        Map<String, Object> stopSource; P11QualifiedSourceOwner.Summary summary;
        Run(MinecraftServer s, ServerPlayer h, ServerPlayer p, Path o) {
            server = s; host = h; peer = p; output = o; source = P11NativeStorageBoundary.nativeSourceOwner(h);
            require(source != null, "SOURCE_PRESENT"); body = source.body(h); peerBody = source.body(p);
            require(body != null && peerBody != null, "BODIES_PRESENT"); epoch = body.source.epoch();
            xp = h.totalExperience; peerXp = p.totalExperience; peerHealth = p.getHealth();
        }
    }
}
