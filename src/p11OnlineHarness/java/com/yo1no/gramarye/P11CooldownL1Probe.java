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
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/** Excluded positive-D observations appended to the unchanged three natural L1 windows. */
public final class P11CooldownL1Probe {
    private static final int DURATION = 600;
    private static PlayerSkillAttachmentService attachments;
    private static SkillDefinitionSubmissionService submissions;
    private static SkillDefinitionStoreService store;
    private static MinecraftServer server;
    private static ServerSlot slot;
    private static SkillReference reference;
    private static Path output;
    private static Episode episode;
    private static int completed;
    private static String observerFailure;
    private static final int ARENA_BLOCK_LIMIT = 26;
    private static final Map<BlockPos, ArenaBlock> arenaBlocks = new LinkedHashMap<>();
    private static ServerLevel arenaLevel;
    private static List<BlockPos> lastRestoredArenaPositions = List.of();
    private static String arenaFailure;
    private static Map<String, Object> blockImpact;
    private P11CooldownL1Probe() { }

    public static boolean selected() {
        return List.of("cooldown-l1-pre-spawn", "cooldown-l1-open", "cooldown-l1-claimed")
                .contains(System.getProperty("gramarye.p11.online.case", ""));
    }

    public static void composition(PlayerSkillAttachmentService actualAttachments,
            SkillDefinitionSubmissionService actualSubmissions, SkillDefinitionStoreService actualStore) {
        if (!selected()) { return; }
        require(attachments == null && actualAttachments != null && actualSubmissions != null && actualStore != null,
                "ORIGINAL_COMPOSITION_ONCE");
        attachments = actualAttachments; submissions = actualSubmissions; store = actualStore;
    }

    public static void runtimeStarted(MinecraftServer exact, Object value) {
        if (!selected()) { return; }
        require(server == null && exact.isSameThread() && value instanceof ServerSlot, "ORIGINAL_RUNTIME_ONCE");
        server = exact; slot = (ServerSlot) value;
    }

    static void prepareEpisode(MinecraftServer exact, ServerPlayer actor, Path destination, int ordinal) throws IOException {
        if (!selected()) { return; }
        check();
        require(exact == server && attachments != null && store != null && ordinal == completed + 1
                && ordinal >= 1 && ordinal <= 3 && (episode == null || episode.sealed), "EPISODE_PREPARATION");
        current(actor); output = destination;
        if (reference == null) { submit(actor); }
        require(equipped(actor).equals(reference), "SAME_FORMAL_REFERENCE_ALL_EPISODES");
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = owner == null ? null : owner.body(actor);
        require(body != null && owner.canCopy(body) && body.account.current == body, "PREPARE_CURRENT_SOURCE");
        episode = new Episode(ordinal, actor, owner, body);
    }

    static void accepted(ServerPlayer actor, Object value, Object result) {
        if (!selected()) { return; }
        check(); var e = episode;
        require(e != null && e.actor == actor && e.instance == null && value instanceof ServerSlot.InstanceState
                && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly, "ONE_REAL_ADMISSION");
        var instance = (ServerSlot.InstanceState) value;
        var admitted = (RuntimeAdmissionResult.AcceptedMemoryOnly) result;
        var root = slot.queue.stream().filter(event -> event.eventId().equals(admitted.eventToken().eventId()))
                .findFirst().orElseThrow();
        require(instance.id.equals(admitted.eventToken().skillInstanceId()) && instance.lease.reference.equals(reference)
                && instance.work != null && instance.work.qualifies(actor) && !instance.lease.pin.isClosed()
                && instance.cooldownPreparation != null && instance.cooldownReceipt != null
                && instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.PENDING
                && slot.instances.get(instance.id) == instance && e.pending != null && e.cell != null,
                "ACTUAL_PUBLISHED_WORK_AND_PENDING");
        require(root.skillInstanceId().equals(instance.id) && root.createdRuntimeTick() == slot.runtimeTick
                && e.pending.attemptId.equals(instance.cooldownReceipt.attemptId())
                && e.pending.releaseNotAfter == Math.addExact(e.pending.acceptedAt,
                        root.deadlineRuntimeTick() - root.createdRuntimeTick())
                && samePending(currentEntry(e), e.pending), "EXACT_PENDING_ROOT_BOUND");
        e.instance = instance; e.root = root; e.work = instance.work;
    }

    static void transferred(Object value, Object disposition) {
        if (!selected() || disposition != RuntimePermitTransferDisposition.TRANSFERRED) { return; }
        check(); var e = episode;
        require(e != null && e.instance != null && e.projectile == null && value instanceof P9StarterProjectile,
                "ONE_ORIGINAL_TRANSFER");
        var projectile = (P9StarterProjectile) value;
        var permit = e.instance.activeProjectileContinuation;
        require(permit != null && permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                && projectile.hasContinuationPermitIdentity(permit) && projectile.isAddedToLevel() && !projectile.isRemoved()
                && projectile.getOwner() == e.actor && e.instance.work == e.work
                && e.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                && e.active != null && sameActive(currentEntry(e), e.active)
                && e.active.attemptId.equals(e.instance.cooldownReceipt.attemptId())
                && e.active.releasedAt == e.instance.cooldownReceipt.releasedAt()
                && e.active.releasedAt == gameTime() && slot.runtimeTick < e.root.deadlineRuntimeTick(),
                "EXACT_NATIVE_OPEN_ARM_CURRENT_TRUTH");
        e.projectile = projectile; e.releaseRuntimeTick = slot.runtimeTick;
        e.logoutBeforeArm = e.logoutObserved;
    }

    static void logoutReturned(ServerPlayer actor) {
        if (!selected()) { return; }
        check(); var e = episode;
        require(e != null && e.actor == actor && !e.logoutObserved && e.instance != null
                && e.instance.logoutState == SkillRuntimeService.LogoutState.COMPLETE
                && e.instance.work == e.work && actor.isRemoved() && !actor.connection.getConnection().isConnected(),
                "WHOLE_NORMAL_LOGOUT_RETAINS_EXACT_WORK");
        var entry = currentEntry(e);
        require(e.active == null ? samePending(entry, e.pending) : sameActive(entry, e.active),
                "LOGOUT_DOES_NOT_RESET_OBLIGATION");
        e.logoutObserved = true; e.logoutRuntimeTick = slot.runtimeTick;
    }

    static void reconnected(ServerPlayer actor) {
        if (!selected()) { return; }
        check(); var e = episode; current(actor);
        require(e != null && e.logoutObserved && e.successor == null && actor != e.actor
                && actor.getUUID().equals(e.actor.getUUID())
                && actor.connection.getConnection() != e.actor.connection.getConnection(), "GENUINE_RECONNECT_B");
        var body = e.owner.body(actor);
        require(body != null && e.owner.canCopy(body) && body.account.current == body && body.complete
                && equipped(actor).equals(reference), "QUALIFIED_CURRENT_B_REFERENCE");
        var entry = body.cooldown.data.entries.get(reference.skillId().value());
        require(e.active == null ? samePending(entry, e.pending)
                : sameActive(entry, e.active) && gameTime() < e.active.expiresAt, "B_SAME_UNEXPIRED_OBLIGATION");
        e.workLiveAtReconnect = e.instance.work == e.work;
        if (e.workLiveAtReconnect) {
            require(body.account == e.account && e.adoptedBody == body && e.adoptedCell == e.cell,
                    "LIVE_WORK_EXACT_ACCOUNT_CELL_HANDOFF");
        }
        e.successor = actor; e.successorEpoch = body.source.epoch(); e.successorVersion = body.source.version();
        e.armBeforeReconnect = e.active != null;
        if (e.projectile != null && !e.projectile.isRemoved()) {
            require(e.projectile.getOwner() == e.actor, "OLD_PROJECTILE_NEVER_RETARGETS_B");
        }
    }

    /** Called only after the original publication returned, with its actual opaque private cell. */
    public static void published(Object sourceValue, Object bodyValue, Object cell, Object dataValue) {
        if (!selected() || episode == null || observerFailure != null) { return; }
        try {
            var e = episode;
            if (!(bodyValue instanceof P11QualifiedSourceOwner.Body body)
                    || !body.actor.getUUID().equals(e.actor.getUUID())) { return; }
            require(server.isSameThread() && sourceValue == e.owner && dataValue instanceof P11CastCooldownData,
                    "PUBLICATION_SOURCE");
            var data = (P11CastCooldownData) dataValue;
            require(body.cooldown.data == data && body.account.current == body && data.kind == P11CastCooldownData.Kind.ROUTED,
                    "ORIGINAL_PUBLICATION_CURRENT_MATERIAL");
            var entry = data.entries.get(reference.skillId().value());
            if (entry == null || e.sealed) { return; }
            if (e.pending == null) {
                if (entry.kind != 1) { return; } // Prior completed episode may still have an expired ACTIVE material.
                require(body.actor == e.actor && entry.kind == 1 && entry.duration == DURATION
                        && entry.revision == reference.revision().value(), "FIRST_PUBLICATION_PENDING");
                e.pending = entry; e.cell = cell; e.pendingFloor = data.clockFloor;
            }
            if (!entry.attemptId.equals(e.pending.attemptId)) { return; }
            require((e.instance == null || e.instance.work != e.work || cell == e.cell)
                    && sameAttempt(entry, e.pending) && data.clockFloor >= e.pendingFloor,
                    "SAME_LIVE_ATTEMPT_CELL_AND_BOUND");
            if (entry.kind == 0) {
                require(entry.expiresAt == Math.addExact(entry.releasedAt, DURATION)
                        && entry.releasedAt >= entry.acceptedAt && entry.releasedAt <= entry.releaseNotAfter
                        && (e.active == null || sameActive(entry, e.active)), "MONOTONIC_TRUE_ACTIVE");
                if (e.active == null) {
                    e.armFloor = data.clockFloor; e.armPublishedToOldActor = body.actor == e.actor;
                }
                e.active = entry;
            } else { require(entry.kind == 1 && e.active == null, "NO_ARM_ROLLBACK"); }
        } catch (RuntimeException | LinkageError failure) { observerFailure = "PUBLICATION_OBSERVER"; }
    }

    /** No map is retained; the actual successful native material handoff supplies this one cell identity. */
    public static void materialAdopted(Object sourceValue, Object bodyValue, boolean normalResult, Map<UUID, ?> cells) {
        if (!selected() || episode == null || observerFailure != null) { return; }
        try {
            var e = episode;
            if (!(bodyValue instanceof P11QualifiedSourceOwner.Body body) || body.actor == e.actor
                    || !body.actor.getUUID().equals(e.actor.getUUID())) { return; }
            require(server.isSameThread() && sourceValue == e.owner && normalResult && body.account.current == body
                    && body.complete && e.owner.canCopy(body), "ORIGINAL_MATERIAL_ADOPTION");
            e.adoptedBody = body; e.adoptedCell = cells.get(body.actor.getUUID());
            if (e.instance != null && e.instance.work == e.work) {
                require(body.account == e.account && e.adoptedCell == e.cell, "NO_LIVE_WORK_CELL_REPLACEMENT");
            }
        } catch (RuntimeException | LinkageError failure) { observerFailure = "ADOPTION_OBSERVER"; }
    }

    static void finishEpisode(int ordinal, ServerPlayer actor) throws IOException {
        if (!selected()) { return; }
        check(); var e = episode; current(actor);
        require(e != null && e.ordinal == ordinal && !e.sealed && e.successor == actor && e.logoutObserved
                && e.active != null && e.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                && e.instance.work == null && e.instance.lease.pin.isClosed()
                && e.instance.activeProjectileContinuation == null && e.projectile.isRemoved(),
                "ORIGINAL_L1_TERMINAL_WITH_ARM_RETAINED");
        var body = e.owner.body(actor);
        require(body != null && e.owner.canCopy(body) && sameActive(currentEntry(e), e.active)
                && gameTime() < e.active.expiresAt, "TERMINAL_CURRENT_ACTIVE_NOT_REFUNDED");
        if (ordinal == 3) { server.getPlayerList().saveAll(); }
        physical(actor, e);
        var facts = new LinkedHashMap<String, Object>();
        facts.put("status", "POSITIVE_D_NATIVE_L1_OBLIGATION_AND_PHYSICAL_SAVE"); facts.put("episode", ordinal);
        facts.put("reference", reference.toString()); facts.put("durationTicks", DURATION);
        facts.put("acceptedAt", e.pending.acceptedAt); facts.put("releaseNotAfter", e.pending.releaseNotAfter);
        facts.put("releasedAt", e.active.releasedAt); facts.put("expiresAt", e.active.expiresAt);
        facts.put("acceptedRuntimeTick", e.root.createdRuntimeTick()); facts.put("rootDeadlineRuntimeTick", e.root.deadlineRuntimeTick());
        facts.put("releaseRuntimeTick", e.releaseRuntimeTick); facts.put("logoutRuntimeTick", e.logoutRuntimeTick);
        facts.put("logoutBeforeArm", e.logoutBeforeArm); facts.put("armBeforeReconnect", e.armBeforeReconnect);
        facts.put("oldWorkLiveAtReconnect", e.workLiveAtReconnect); facts.put("sameCellAtReconnect", e.adoptedCell == e.cell);
        facts.put("armPublicationRecipientWasOldActor", e.armPublishedToOldActor);
        facts.put("originalEpoch", e.originalEpoch); facts.put("successorEpoch", e.successorEpoch);
        facts.put("successorVersionAtLogin", e.successorVersion); facts.put("currentVersionAtSave", body.source.version());
        facts.put("pendingFloor", e.pendingFloor); facts.put("armFloor", e.armFloor);
        facts.put("savedFloor", e.savedFloor); facts.put("sameAttemptAndBoundInSavedCurrentB", true);
        facts.put("nativeOriginalCausalAssertionsRemainRequired", true);
        P11C4aEvidence.write(output, "cooldown-l1-episode-" + ordinal + ".json", facts);
        e.sealed = true; completed++;
    }

    /** Only between completed episodes: no existing event, projectile or hit is held. */
    static boolean nextEpisodeReady(ServerPlayer actor) {
        if (!selected()) { return true; }
        check(); var e = episode; current(actor);
        require(e != null && e.sealed && completed < 3 && e.instance.work == null
                && e.instance.activeProjectileContinuation == null && e.instance.lease.pin.isClosed(),
                "EXPIRY_WAIT_ONLY_AFTER_ORIGINAL_WORK_TERMINAL");
        return gameTime() >= e.active.expiresAt;
    }

    /** Only the existing target floor and lava source, never arbitrary neighboring fluid updates. */
    static void placeArenaBlock(ServerLevel level, BlockPos position, BlockState installed) {
        if (!selected()) { level.setBlock(position, installed, 3); return; }
        require(server != null && server.isSameThread() && level.getServer() == server && level.isLoaded(position),
                "ARENA_WRITE_CONTEXT");
        require(arenaLevel == null || arenaLevel == level, "ARENA_LEVEL_CHANGED");
        var key = position.immutable(); var current = level.getBlockState(key); var previous = arenaBlocks.get(key);
        require(previous == null || current.equals(previous.installed), "ARENA_OWNED_STATE_CHANGED");
        require(previous != null || arenaBlocks.size() < ARENA_BLOCK_LIMIT, "ARENA_BLOCK_BOUND");
        arenaLevel = level;
        arenaBlocks.put(key, new ArenaBlock(previous == null ? current : previous.prior, installed));
        level.setBlock(key, installed, 3);
        require(level.getBlockState(key).equals(installed), "ARENA_INSTALL_NOT_EXACT");
    }

    static void restoreArenaForNextEpisode() throws IOException {
        if (!selected()) { return; }
        check(); var e = episode;
        require(e != null && e.sealed && completed == e.ordinal && completed < 3
                && e.instance.work == null && e.instance.activeProjectileContinuation == null
                && e.instance.lease.pin.isClosed() && e.projectile.isRemoved(), "ARENA_RESTORE_ONLY_AFTER_SEALED_EPISODE");
        int count = arenaBlocks.size();
        try { restoreOwnedArenaBlocks(); }
        catch (RuntimeException | Error failure) { arenaFailure = "OWNED_ARENA_RESTORE_REFUSED"; throw failure; }
        P11C4aEvidence.write(output, "cooldown-l1-arena-restore-" + completed + ".json", Map.of(
                "status", "EXACT_OWNED_ARENA_RESTORED_AFTER_EPISODE_READBACK", "episode", completed,
                "restoredPositions", count, "sealedEpisode", true, "existingWorkTerminal", true,
                "trajectoryOrLifetimeChanged", false, "unrecordedPositionsTouched", false));
    }

    private static void restoreOwnedArenaBlocks() {
        require(server != null && server.isSameThread() && arenaLevel != null && arenaLevel.getServer() == server
                && !arenaBlocks.isEmpty() && arenaBlocks.size() <= ARENA_BLOCK_LIMIT, "ARENA_RESTORE_CONTEXT");
        var positions = List.copyOf(arenaBlocks.keySet());
        for (var position : positions) {
            require(arenaLevel.isLoaded(position) && arenaLevel.getBlockState(position).equals(arenaBlocks.get(position).installed),
                    "ARENA_RESTORE_FOREIGN_STATE");
        }
        // The lava source was installed after its supporting floor; restore it first.
        for (int index = positions.size() - 1; index >= 0; index--) {
            var position = positions.get(index); var owned = arenaBlocks.get(position);
            require(arenaLevel.isLoaded(position) && arenaLevel.getBlockState(position).equals(owned.installed),
                    "ARENA_RESTORE_REENTRANT_CHANGE");
            arenaLevel.setBlock(position, owned.prior, 3);
            require(arenaLevel.getBlockState(position).equals(owned.prior), "ARENA_RESTORE_NOT_EXACT");
            arenaBlocks.remove(position);
        }
        lastRestoredArenaPositions = positions; arenaLevel = null;
    }

    /** Bounded observation only: errors cannot replace the original block-impact primary. */
    public static void blockImpactEntering(Object value, BlockHitResult hit) {
        if (!selected() || episode == null || episode.ordinal != 3 || value != episode.projectile) { return; }
        try {
            if (blockImpact != null) { observerFailure = "DUPLICATE_BLOCK_IMPACT_OBSERVATION"; return; }
            var projectile = episode.projectile; var level = (ServerLevel) projectile.level(); var position = hit.getBlockPos();
            require(server.isSameThread() && level.getServer() == server && level.isLoaded(position), "BLOCK_OBSERVATION_CONTEXT");
            var state = level.getBlockState(position); var facts = new LinkedHashMap<String, Object>();
            facts.put("status", "ACTUAL_ORIGINAL_EPISODE_THREE_BLOCK_IMPACT_NOT_HISTORICAL_ATTRIBUTION");
            facts.put("x", position.getX()); facts.put("y", position.getY()); facts.put("z", position.getZ());
            facts.put("face", hit.getDirection().name());
            facts.put("blockCategory", state.is(Blocks.STONE) ? "STONE" : state.is(Blocks.LAVA) ? "LAVA" : "OTHER");
            facts.put("previousOwnedArenaPosition", lastRestoredArenaPositions.contains(position));
            facts.put("projectileAge", projectile.tickCount); facts.put("successorObserved", episode.successor != null);
            facts.put("originalNormalReturn", false); blockImpact = facts;
        } catch (RuntimeException | Error ignored) { observerFailure = "BLOCK_IMPACT_OBSERVATION_FAILED"; }
    }

    public static void blockImpactReturned(Object value, boolean normal) {
        if (!selected() || episode == null || episode.ordinal != 3 || value != episode.projectile || blockImpact == null) { return; }
        try {
            blockImpact.put("originalNormalReturn", normal); blockImpact.put("projectileRemoved", episode.projectile.isRemoved());
        } catch (RuntimeException | Error ignored) { observerFailure = "BLOCK_IMPACT_RETURN_OBSERVATION_FAILED"; }
    }

    static Map<String, Object> arenaFacts() {
        return Map.of("restoreFailure", arenaFailure == null ? "NONE" : arenaFailure,
                "unrestoredOwnedPositions", arenaBlocks.size(), "lastRestoredPositions", lastRestoredArenaPositions.size(),
                "blockImpact", blockImpact == null ? Map.of("status", "NOT_OBSERVED") : Map.copyOf(blockImpact));
    }

    private record ArenaBlock(BlockState prior, BlockState installed) { }

    static void check() { require(observerFailure == null, observerFailure == null ? "OBSERVER" : observerFailure); }
    static void release() { episode = null; attachments = null; submissions = null; store = null; slot = null; server = null; }

    private static void submit(ServerPlayer actor) throws IOException {
        var old = equipped(actor); var canonical = P9StarterSkillContent.canonicalDraft(old.skillId());
        var first = canonical.nodes().getFirst();
        var payload = P9ActiveCastTriggerType.INSTANCE.payloadCodec().codec()
                .encodeStart(JsonOps.INSTANCE, new P9ActiveCastTriggerPayloadV1(DURATION)).getOrThrow();
        var node = new DraftNode(DraftTriggerSlot.present(new DefinitionEnvelope(P9StarterSkillContent.ACTIVE_CAST_ID,
                1, new Dynamic<>(JsonOps.INSTANCE, payload))), first.action(), first.appearanceOverride());
        var draft = new SkillDraft(canonical.draftSchemaVersion(), old.skillId(), Optional.of(old.revision()),
                List.of(node, canonical.nodes().get(1)), canonical.appearance());
        applied(attachments.putDraft(actor, draft)); var outcome = submissions.submit(actor, old.skillId());
        require(outcome instanceof SkillSubmissionCompositionOutcome.Committed, "FORMAL_V1_COMMITTED");
        reference = ((SkillSubmissionCompositionOutcome.Committed) outcome).reference();
        require(reference.skillId().equals(old.skillId()) && reference.revision().value() > old.revision().value(),
                "FORMAL_SUCCESSOR_REVISION");
        applied(attachments.setEquipped(actor, 0, Optional.of(reference)));
        P11C4aEvidence.write(output, "cooldown-l1-formal-submission.json", Map.of("status", "FORMAL_V1_SUBMIT_AND_EQUIP",
                "reference", reference.toString(), "durationTicks", DURATION, "sameReferenceForThreeEpisodes", true));
    }

    private static void physical(ServerPlayer actor, Episode e) throws IOException {
        var diagnostic = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
        var writer = diagnostic.writers().stream().filter(value -> value.kind().equals("PLAYER_DATA")).findFirst().orElseThrow();
        require(!writer.dirty() && writer.terminal().equals("COMPLETED") && writer.encode().equals("SUCCEEDED")
                && writer.write().equals("SUCCEEDED") && writer.close().equals("SUCCEEDED")
                && writer.replace().equals("SUCCEEDED"), "ACTUAL_CURRENT_PLAYER_WRITER");
        var file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID() + ".dat");
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)
                && Files.size(file) <= 32L * 1024 * 1024, "OWNED_PLAYER_DATA_BOUND");
        var saved = NbtIo.readCompressed(file, NbtAccounter.create(32L * 1024 * 1024));
        require(saved.hasUUID("UUID") && saved.getUUID("UUID").equals(actor.getUUID()), "ACTUAL_PLAYER_FILE_IDENTITY");
        var data = P11CastCooldownCodec.read(saved.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                .get(P11CastCooldownAttachments.ID.toString()));
        require(data.kind == P11CastCooldownData.Kind.ROUTED && data.clockFloor >= e.armFloor
                && sameActive(data.entries.get(reference.skillId().value()), e.active), "PHYSICAL_CURRENT_B_SAME_ACTIVE");
        e.savedFloor = data.clockFloor;
    }

    private static P11CastCooldownData.Entry currentEntry(Episode e) {
        var body = e.owner.current(e.actor.getUUID());
        require(body != null && e.owner.canCopy(body) && body.cooldown.data.kind == P11CastCooldownData.Kind.ROUTED,
                "CURRENT_LOGICAL_OWNER_MATERIAL");
        return body.cooldown.data.entries.get(reference.skillId().value());
    }
    private static boolean sameAttempt(P11CastCooldownData.Entry a, P11CastCooldownData.Entry b) {
        return a != null && b != null && a.skillId.equals(b.skillId) && a.revision == b.revision
                && a.duration == b.duration && a.acceptedAt == b.acceptedAt && a.attemptId.equals(b.attemptId)
                && a.releaseNotAfter == b.releaseNotAfter;
    }
    private static boolean samePending(P11CastCooldownData.Entry a, P11CastCooldownData.Entry b) {
        return sameAttempt(a, b) && a.kind == 1 && a.releasedAt == -1 && a.expiresAt == -1;
    }
    private static boolean sameActive(P11CastCooldownData.Entry a, P11CastCooldownData.Entry b) {
        return sameAttempt(a, b) && a.kind == 0 && a.releasedAt == b.releasedAt && a.expiresAt == b.expiresAt;
    }
    private static SkillReference equipped(ServerPlayer actor) {
        var value = attachments.equippedAt(actor, 0);
        require(value instanceof PlayerSkillAttachmentService.Available<?>, "EQUIPMENT_AVAILABLE");
        return ((PlayerSkillAttachmentService.Available<Optional<SkillReference>>) value).value().orElseThrow();
    }
    private static void applied(PlayerSkillAttachmentService.Result<?> result) {
        require(result instanceof PlayerSkillAttachmentService.Available<?> available
                && available.value() == PlayerSkillAttachmentService.Applied.INSTANCE, "FORMAL_ATTACHMENT_APPLIED");
    }
    private static void current(ServerPlayer actor) {
        require(server.isSameThread() && actor.getServer() == server && !actor.isFakePlayer() && !actor.isRemoved()
                && actor.connection.player == actor && actor.connection.getConnection().isConnected()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor, "EXACT_CURRENT_ACTOR");
    }
    private static long gameTime() { return server.overworld().getGameTime(); }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "COOLDOWN_L1_" + code); }

    private static final class Episode {
        final int ordinal; final ServerPlayer actor; final P11QualifiedSourceOwner owner;
        final P11QualifiedSourceOwner.Account account; final long originalEpoch;
        ServerSlot.InstanceState instance; RuntimeEvent root; P11QualifiedSourceOwner.WorkReservation work;
        P9StarterProjectile projectile; ServerPlayer successor; Object cell, adoptedCell;
        P11QualifiedSourceOwner.Body adoptedBody; P11CastCooldownData.Entry pending, active;
        long pendingFloor, armFloor, savedFloor, releaseRuntimeTick, logoutRuntimeTick, successorEpoch, successorVersion;
        boolean logoutObserved, logoutBeforeArm, armBeforeReconnect, workLiveAtReconnect, armPublishedToOldActor, sealed;
        Episode(int ordinal, ServerPlayer actor, P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body) {
            this.ordinal = ordinal; this.actor = actor; this.owner = owner; account = body.account;
            originalEpoch = body.source.epoch();
        }
    }
}
