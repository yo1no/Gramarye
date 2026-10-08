package com.yo1no.gramarye;

import com.google.gson.JsonParser;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary.SyncEntryState;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary.SyncReason;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary.SyncSourceState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Exact isolated player-backup obstruction. Native writers and the sole cooldown owner remain original. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11CooldownDurabilityProbe {
    private static final long MAX_PLAYER_BYTES = 32L * 1024 * 1024;
    private static final String MARKER = "gramarye-owned-cooldown-player-backup-obstruction\n";
    private static Run run;
    private P11CooldownDurabilityProbe() { }
    public static boolean selected() {
        return List.of("cooldown-save-active", "cooldown-save-clear")
                .contains(System.getProperty("gramarye.p11.online.case", ""));
    }
    public static boolean clearSelected() {
        return "cooldown-save-clear".equals(System.getProperty("gramarye.p11.online.case", ""));
    }
    static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, SkillReference reference,
            ServerSlot slot, P11CastCooldownService cooldowns, Path output) throws IOException {
        require(selected() && run == null && server.isSameThread() && actor != peer && !actor.getUUID().equals(peer.getUUID()), "ARM_SCOPE");
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.body(actor);
        require(body != null && source.canSerialize(body) && source.canCopy(body)
                && output.toRealPath().equals(P11C4aEvidence.root().resolve("server")), "EXACT_CURRENT_SOURCE_AND_OUTPUT");
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path directory = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toAbsolutePath().normalize();
        require(Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(world) && world.toRealPath().equals(world)
                && directory.equals(world.resolve("playerdata")) && Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(directory) && directory.toRealPath().equals(directory), "EXACT_ORIGINAL_PLAYER_DIRECTORY");
        run = new Run(server, actor, peer, reference, slot, cooldowns, output, source, body, directory, clearSelected());
        current(run);
    }

    /** Controlled original save after actual admission publication, before the queued root drains. */
    static void admitted(MinecraftServer server, ServerPlayer actor, SkillReference reference,
            Object instanceValue, Object result) throws IOException {
        var r = exact(); current(r);
        require(server == r.server && actor == r.actor && reference.equals(r.reference) && r.instance == null
                && instanceValue instanceof ServerSlot.InstanceState && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                "ONE_ACTUAL_ACCEPTED_ROOT");
        r.instance = (ServerSlot.InstanceState) instanceValue;
        var accepted = (RuntimeAdmissionResult.AcceptedMemoryOnly) result;
        var event = r.slot.queue.stream().filter(value -> value.eventId().equals(accepted.eventToken().eventId())).findFirst().orElseThrow();
        require(r.slot.instances.get(r.instance.id) == r.instance && r.instance.id.equals(accepted.eventToken().skillInstanceId())
                && r.instance.work != null && !r.instance.lease.pin.isClosed() && r.instance.activeProjectileContinuation == null
                && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.PENDING
                && !r.instance.inFlight && r.slot.currentEvent != event
                && event.scheduledRuntimeTick() == Math.addExact(event.createdRuntimeTick(), 1)
                && event.createdRuntimeTick() == r.slot.runtimeTick, "PENDING_ROOT_NOT_DISPATCHED");
        r.pending = entry(r);
        require(r.pending != null && r.pending.kind == 1 && r.pending.duration == 600
                && r.pending.attemptId.equals(r.instance.cooldownReceipt.attemptId()), "EXACT_PENDING_MATERIAL");
        r.acceptances++;
        r.server.getPlayerList().saveAll();
        requireSuccess(r); require(sameEntry(physical(r), r.pending), "ACTUAL_DURABLE_PENDING_BEFORE_DISPATCH");
        r.pendingHash = fileHash(r.path); r.pendingSaved = true;
        write(r, "cooldown-durability-pending.json", "CONTROLLED_ORIGINAL_PENDING_SAVE_BEFORE_ROOT_DISPATCH");
        try { installObstruction(r); }
        catch (IOException | RuntimeException | Error primary) { restoreAfterFailure(primary); throw primary; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void joining(EntityJoinLevelEvent event) {
        var r = run;
        if (r == null || r.instance == null || !(event.getEntity() instanceof P9StarterProjectile projectile)
                || r.instance.activeProjectileContinuation == null
                || !projectile.hasContinuationPermitIdentity(r.instance.activeProjectileContinuation)) { return; }
        require(r.server.isSameThread() && r.pendingSaved && r.blockerCreated && r.projectile == null
                && event.getLevel() == r.actor.serverLevel() && !event.isCanceled() && !projectile.isAddedToLevel()
                && !projectile.isRemoved() && r.instance.activeProjectileContinuation.isPreparedSpawn()
                && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.PENDING, "EXACT_PREPARED_NATIVE_JOIN");
        r.projectile = projectile; r.permit = r.instance.activeProjectileContinuation;
        if (r.clear) { event.setCanceled(true); r.cancelledJoins++; }
    }
    public static void addReturned(Entity entity, boolean actual, Throwable primary) {
        var r = run; if (r == null || entity != r.projectile) { return; }
        observe(r, () -> {
            require(primary == null && ++r.addReturns == 1 && actual != r.clear, "ORIGINAL_NATIVE_ADD_RESULT");
            r.addTrue = actual;
        });
    }
    static void transferred(Object projectile, Object result) {
        var r = exact();
        require(!r.clear && projectile == r.projectile && result == RuntimePermitTransferDisposition.TRANSFERRED
                && ++r.transfers == 1 && r.addReturns == 1 && r.addTrue && r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                && r.projectile.getOwner() == r.actor, "ACTUAL_TRUE_ADD_AND_ARM");
        r.active = entry(r);
        require(r.active != null && r.active.kind == 0 && r.active.attemptId.equals(r.pending.attemptId)
                && r.active.releasedAt == r.instance.cooldownReceipt.releasedAt()
                && r.active.expiresAt == r.active.releasedAt + 600, "KNOWN_ARM_SAME_ATTEMPT");
    }
    static void claimed(Object result) { if (run != null && result == RuntimePermitClaimDisposition.QUEUED) run.claims++; }

    /** Existing original writer outcome only; a boolean supplied by the fixture never becomes a receipt. */
    public static void writerFinished(Object owner, Object body, Object receipt, boolean success) {
        var r = run;
        if (r == null || success || owner != r.source || body != r.body
                || !(receipt instanceof P11ReceiptLedger.PhysicalWriterReceipt writer)
                || writer.kind() != P11ReceiptLedger.WriterKind.PLAYER_DATA) return;
        observe(r, () -> {
            require(r.server.isSameThread() && r.blockerCreated && !r.restored
                    && writer.source().account() == r.body.source.account() && ++r.failedWriters <= 16,
                    "ONLY_SELECTED_ORIGINAL_PLAYER_WRITER_FAILURE");
        });
    }

    static boolean tick() throws IOException {
        var r = exact(); current(r);
        require(++r.ticks <= 2400 && r.failure == null && r.claims == 0 && r.peer.getHealth() == r.peerHealth,
                "FINITE_NO_CHILD_OR_PEER_DAMAGE");
        if (r.complete || r.instance == null) return r.complete;
        if (r.phase == 0) {
            if (r.addReturns != 1 || !r.clear && r.transfers != 1) return false;
            requireKnownFact(r); requireBlocker(r);
            long before = writer(r).attempt(); r.server.getPlayerList().saveAll();
            var failed = writer(r);
            require(r.failedWriters > 0 && failed.attempt() > before && failed.dirty() && failed.terminal().equals("FAILED")
                    && failed.encode().equals("SUCCEEDED") && failed.write().equals("SUCCEEDED")
                    && failed.close().equals("SUCCEEDED") && failed.replace().equals("FAILED"), "ACTUAL_NATIVE_REPLACE_FAILURE");
            r.failedAttempt = failed.attempt();
            require(fileHash(r.path).equals(r.pendingHash) && sameEntry(physical(r), r.pending), "OLD_DURABLE_PENDING_NOT_REPLACED");
            requireKnownFact(r); requireProjection(r, true);
            write(r, "cooldown-durability-failed.json", "ORIGINAL_PLAYER_REPLACE_FAILED_KNOWN_MEMORY_FACT_RETAINED");
            r.phase = 1;
        }
        if (r.phase == 1) {
            requireKnownFact(r); requireBlocker(r);
            if (!clientProof(r, "cooldown-durability-mirror.json") || !clientProof(r, "cooldown-durability-hud.json")) return false;
            restore(r); require(r.restored && !r.restoreFailed, "EXACT_OBSTRUCTION_RESTORED");
            r.server.getPlayerList().saveAll(); requireSuccess(r);
            require(writer(r).attempt() > r.failedAttempt && (r.clear ? physical(r) == null : sameEntry(physical(r), r.active)),
                    "ORIGINAL_RETRY_PERSISTS_KNOWN_FACT_NO_WORK_REPLAY");
            requireProjection(r, false); r.retried = true;
            write(r, "cooldown-durability-restored.json", "OWNED_BACKUP_RESTORED_ORIGINAL_SAVE_PERSISTS_KNOWN_FACT");
            r.phase = 2;
        }
        if (r.phase == 2) {
            if (!r.clear && r.server.overworld().getGameTime() < r.active.expiresAt) {
                require(sameEntry(entry(r), r.active), "ARM_NOT_REFUNDED_BEFORE_NATURAL_EXPIRY"); return false;
            }
            if (!terminal(r)) return false;
            if (!r.clear) {
                // Original controlled observation reconciles real elapsed G; it never writes a clock or an entry.
                requireProjection(r, false);
                require(entry(r) == null && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM,
                        "REAL_EXPIRY_RETAINS_IRREVERSIBLE_ARM_FACT");
                r.server.getPlayerList().saveAll(); requireSuccess(r); require(physical(r) == null, "NATIVE_EXPIRED_CLEAR_PHYSICAL");
            }
            require(r.acceptances == 1 && r.claims == 0 && r.restored && r.retried && r.failedWriters > 0,
                    "ONE_ROOT_NO_REPLAY_AND_RESTORED_TERMINAL");
            r.complete = true; write(r, "cooldown-durability-result.json", "NATIVE_KNOWN_COOLDOWN_FACT_SURVIVES_PLAYER_SAVE_FAILURE");
        }
        return r.complete;
    }
    static int expectedSourceFailures() {
        var r = exact(); require(r.complete && r.restored && !r.restoreFailed && r.failure == null && r.failedWriters > 0,
                "OBSERVED_SELECTED_FAILURE_COUNT"); return r.failedWriters;
    }
    static void restoreAfterFailure(Throwable primary) throws IOException {
        var r = run; if (r == null || r.restored) return;
        try { restore(r); }
        catch (IOException | RuntimeException | Error secondary) {
            r.restoreFailed = true;
            if (primary == null) throw secondary;
            try { if (secondary != primary) primary.addSuppressed(secondary); }
            catch (RuntimeException | Error ignored) { /* The original primary remains unchanged. */ }
        }
    }
    static void release() {
        require(run == null || run.restored && !run.restoreFailed, "NEVER_FORGET_OWNED_OBSTRUCTION"); run = null;
    }

    private static void requireKnownFact(Run r) {
        if (r.clear) {
            require(r.cancelledJoins == 1 && !r.addTrue && r.transfers == 0 && terminal(r)
                    && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.NO_RELEASE && entry(r) == null,
                    "ACTUAL_NO_RELEASE_CLEAR_NOT_DISK_PENDING_RECOVERY");
        } else {
            require(r.cancelledJoins == 0 && r.addTrue && r.transfers == 1 && r.active != null
                    && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                    && r.server.overworld().getGameTime() < r.active.expiresAt && sameEntry(entry(r), r.active),
                    "KNOWN_UNEXPIRED_ARM_MEMORY");
        }
    }
    private static void requireProjection(Run r, boolean failed) {
        var capture = r.cooldowns.prepareAndCapture(r.server, r.actor); var projection = capture.projection();
        var entry = projection.entries().stream().filter(value -> value.slot() == 0 && value.reference().equals(r.reference)).findFirst().orElseThrow();
        boolean active = !r.clear && r.server.overworld().getGameTime() < r.active.expiresAt;
        require(capture.isCurrent() && projection.sourceState() == SyncSourceState.AVAILABLE
                && projection.sourceReason() == SyncReason.NONE && projection.sourceEpoch() == r.body.source.epoch()
                && entry.state() == (active ? SyncEntryState.ACTIVE : failed ? SyncEntryState.UNAVAILABLE : SyncEntryState.READY)
                && entry.reason() == (failed ? SyncReason.SAVE_FAILED : SyncReason.NONE)
                && entry.remainingTicks() == (active ? r.active.expiresAt - r.server.overworld().getGameTime() : 0),
                "ACTUAL_CURRENT_OWNER_SAVE_FAILURE_PROJECTION");
    }
    private static void installObstruction(Run r) throws IOException {
        require(!Files.exists(r.preserved, LinkOption.NOFOLLOW_LINKS), "NO_EXISTING_PRESERVED_BACKUP");
        if (Files.exists(r.backup, LinkOption.NOFOLLOW_LINKS)) {
            regular(r.backup); r.originalBackupHash = fileHash(r.backup); r.backupKey = attributes(r.backup).fileKey();
            require(r.backupKey != null, "BACKUP_FILE_KEY_AVAILABLE");
            // No REPLACE_EXISTING or provider-dependent ATOMIC_MOVE overwrite behavior.
            Files.move(r.backup, r.preserved); r.displaced = true;
        }
        Files.createDirectory(r.backup); r.blockerCreated = true; r.blockerKey = attributes(r.backup).fileKey();
        require(r.blockerKey != null, "BLOCKER_FILE_KEY_AVAILABLE");
        Files.writeString(r.marker, MARKER, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        r.markerCreated = true; r.markerKey = attributes(r.marker).fileKey();
        require(r.markerKey != null, "MARKER_FILE_KEY_AVAILABLE"); requireBlocker(r);
    }
    private static void requireBlocker(Run r) throws IOException {
        require(r.blockerCreated && Files.isDirectory(r.backup, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(r.backup)
                && r.blockerKey != null && r.blockerKey.equals(attributes(r.backup).fileKey()), "EXACT_OWNED_BLOCKER_DIRECTORY");
        try (var entries = Files.list(r.backup)) {
            var names = entries.limit(2).toList();
            require(r.markerCreated ? names.size() == 1 && names.getFirst().equals(r.marker) : names.isEmpty(), "NO_FOREIGN_BLOCKER_CONTENT");
        }
        if (r.markerCreated) {
            require(Files.isRegularFile(r.marker, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(r.marker)
                    && r.markerKey != null && r.markerKey.equals(attributes(r.marker).fileKey()) && Files.size(r.marker) == MARKER.length()
                    && Files.readString(r.marker, StandardCharsets.UTF_8).equals(MARKER), "EXACT_OWNED_MARKER");
        }
        if (r.displaced) {
            regular(r.preserved); require(r.backupKey.equals(attributes(r.preserved).fileKey())
                    && r.originalBackupHash.equals(fileHash(r.preserved)), "PRESERVED_BACKUP_IDENTITY_AND_BYTES");
        }
    }
    private static void restore(Run r) throws IOException {
        if (r.blockerCreated) {
            requireBlocker(r);
            if (r.markerCreated) { Files.delete(r.marker); r.markerCreated = false; }
            Files.delete(r.backup); r.blockerCreated = false;
        }
        if (r.displaced) {
            regular(r.preserved); require(r.backupKey.equals(attributes(r.preserved).fileKey())
                    && r.originalBackupHash.equals(fileHash(r.preserved)) && !Files.exists(r.backup, LinkOption.NOFOLLOW_LINKS),
                    "EXACT_BACKUP_RESTORE_NO_OVERWRITE");
            Files.move(r.preserved, r.backup); r.displaced = false;
            require(r.backupKey.equals(attributes(r.backup).fileKey()) && r.originalBackupHash.equals(fileHash(r.backup)),
                    "ORIGINAL_BACKUP_RESTORED_EXACT");
        }
        r.restored = true;
    }
    private static boolean clientProof(Run r, String leaf) throws IOException {
        Path file = r.output.getParent().resolve("client-a").resolve(leaf);
        if (!P11C4aEvidence.receiptPresent(file.getParent(), leaf)) return false;
        require(Files.size(file) <= 65_536, "CLIENT_RECEIPT_BOUND");
        var facts = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        require(facts.get("reference").getAsString().equals(r.reference.toString())
                && facts.get("sourceEpoch").getAsLong() == r.body.source.epoch()
                && facts.get("reason").getAsString().equals("SAVE_FAILED")
                && facts.get("state").getAsString().equals(r.clear ? "UNAVAILABLE" : "ACTIVE"), "ACTUAL_SAME_SOURCE_CLIENT_FAILURE_STATE");
        return true;
    }
    private static P11CastCooldownData.Entry physical(Run r) throws IOException {
        regular(r.path); var player = NbtIo.readCompressed(r.path, NbtAccounter.create(MAX_PLAYER_BYTES));
        require(player.hasUUID("UUID") && player.getUUID("UUID").equals(r.actor.getUUID()), "PHYSICAL_PLAYER_IDENTITY");
        var data = P11CastCooldownCodec.read(player.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).get(P11CastCooldownAttachments.ID.toString()));
        require(data.kind == P11CastCooldownData.Kind.ROUTED, "PHYSICAL_ROUTED_COOLDOWN");
        return data.entries.get(r.reference.skillId().value());
    }
    private static P11CastCooldownData.Entry entry(Run r) {
        require(r.source.body(r.actor) == r.body && r.source.canCopy(r.body) && r.source.canSerialize(r.body)
                && r.body.cooldown.data.kind == P11CastCooldownData.Kind.ROUTED, "EXACT_QUALIFIED_MEMORY_SOURCE");
        return r.body.cooldown.data.entries.get(r.reference.skillId().value());
    }
    private static boolean sameEntry(P11CastCooldownData.Entry a, P11CastCooldownData.Entry b) {
        return a != null && b != null && a.kind == b.kind && a.skillId.equals(b.skillId) && a.revision == b.revision
                && a.duration == b.duration && a.attemptId.equals(b.attemptId) && a.acceptedAt == b.acceptedAt
                && a.releaseNotAfter == b.releaseNotAfter && a.releasedAt == b.releasedAt && a.expiresAt == b.expiresAt;
    }
    private static boolean terminal(Run r) {
        return r.instance.work == null && r.instance.lease.pin.isClosed() && r.instance.activeProjectileContinuation == null
                && !r.slot.instances.containsKey(r.instance.id) && r.projectile != null && r.projectile.isRemoved()
                && !r.slot.activeProjectileContinuations.containsKey(r.permit.permitId);
    }
    private static P11QualifiedSourceOwner.WriterDiagnostic writer(Run r) {
        return r.source.diagnostics(r.actor.getUUID()).writers().stream().filter(value -> value.kind().equals("PLAYER_DATA")).findFirst().orElseThrow();
    }
    private static void requireSuccess(Run r) {
        var writer = writer(r);
        require(!writer.dirty() && writer.terminal().equals("COMPLETED") && writer.encode().equals("SUCCEEDED")
                && writer.write().equals("SUCCEEDED") && writer.close().equals("SUCCEEDED") && writer.replace().equals("SUCCEEDED"),
                "ACTUAL_ORIGINAL_PLAYER_WRITER_SUCCESS");
    }
    private static void regular(Path file) throws IOException {
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)
                && Files.size(file) <= MAX_PLAYER_BYTES, "BOUNDED_ORIGINAL_REGULAR_FILE");
    }
    private static String fileHash(Path path) throws IOException { regular(path); return P11C4aEvidence.hash(Files.readAllBytes(path)); }
    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
    private static void current(Run r) {
        require(r.server.isSameThread() && r.actor.getServer() == r.server && r.peer.getServer() == r.server
                && !r.actor.isRemoved() && !r.peer.isRemoved() && r.actor.isAlive() && r.peer.isAlive()
                && r.actor.connection.player == r.actor && r.actor.connection.getConnection().isConnected()
                && r.actor.connection.getConnection().getPacketListener() == r.actor.connection
                && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == r.actor
                && r.server.getPlayerList().getPlayer(r.peer.getUUID()) == r.peer, "ACTUAL_SAME_JVM_LIVE_ACTORS");
    }
    private static void write(Run r, String leaf, String status) throws IOException {
        var facts = new LinkedHashMap<String, Object>(); facts.put("status", status); facts.put("case", P11C4aEvidence.property("case"));
        facts.put("reference", r.reference.toString()); facts.put("sourceEpoch", r.body.source.epoch()); facts.put("sourceVersion", r.body.source.version());
        facts.put("accepted", r.acceptances); facts.put("originalNativeAddReturns", r.addReturns); facts.put("nativeAddTrue", r.addTrue);
        facts.put("originalJoinCancels", r.cancelledJoins); facts.put("realArms", r.transfers); facts.put("queuedHitChildren", r.claims);
        facts.put("receiptFact", r.instance.cooldownReceipt.fact().name()); facts.put("actualFailedPlayerWriterReturns", r.failedWriters);
        facts.put("controlledPendingSaveBeforeDispatch", r.pendingSaved); facts.put("oldPendingFileSha256", r.pendingHash);
        facts.put("originalBackupSha256", r.originalBackupHash); facts.put("exactObstructionRestored", r.restored);
        facts.put("restoreFailed", r.restoreFailed); facts.put("originalSaveRetried", r.retried);
        facts.put("workPinEntityTerminal", r.projectile != null && terminal(r)); facts.put("nativeGameTime", r.server.overworld().getGameTime());
        if (r.active != null) { facts.put("releasedAt", r.active.releasedAt); facts.put("expiresAt", r.active.expiresAt); }
        facts.put("source", P11C4aEvidence.sourceObservation(r.source.diagnostics(r.actor.getUUID())));
        P11C4aEvidence.write(r.output, leaf, facts);
    }
    private static void observe(Run r, Runnable action) {
        try { action.run(); } catch (RuntimeException | Error ignoredObserver) { r.failure = "ACTUAL_WRITER_OR_ADD_OBSERVER_FAILED"; }
    }
    private static Run exact() { require(run != null, "ARMED"); return run; }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "COOLDOWN_DURABILITY_" + code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final SkillReference reference; final ServerSlot slot;
        final P11CastCooldownService cooldowns; final Path output, path, backup, preserved, marker;
        final P11QualifiedSourceOwner source; final P11QualifiedSourceOwner.Body body; final boolean clear; final float peerHealth;
        ServerSlot.InstanceState instance; P9StarterProjectile projectile; RuntimeProjectileContinuationPermit permit;
        P11CastCooldownData.Entry pending, active; Object blockerKey, markerKey, backupKey;
        String pendingHash, originalBackupHash = "ABSENT", failure; long failedAttempt;
        int ticks, phase, acceptances, cancelledJoins, addReturns, transfers, claims, failedWriters;
        boolean pendingSaved, displaced, blockerCreated, markerCreated, restored, restoreFailed, addTrue, retried, complete;
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, SkillReference reference, ServerSlot slot,
                P11CastCooldownService cooldowns, Path output, P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body,
                Path directory, boolean clear) {
            this.server = server; this.actor = actor; this.peer = peer; this.reference = reference; this.slot = slot;
            this.cooldowns = cooldowns; this.output = output; this.source = source; this.body = body; this.clear = clear;
            peerHealth = peer.getHealth(); path = directory.resolve(actor.getUUID() + ".dat");
            backup = directory.resolve(actor.getUUID() + ".dat_old"); preserved = directory.resolve(actor.getUUID() + ".dat_old.p11-cooldown-preserved");
            marker = backup.resolve("p11-cooldown-owned-marker");
        }
    }
}
