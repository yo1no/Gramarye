package com.yo1no.gramarye;

import com.google.gson.JsonParser;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Excluded two real positive obligations across original death and first-End same-C successors. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11CooldownCloneProbe {
    private static Run run;
    private P11CooldownCloneProbe() { }
    public static boolean selected() { return "cooldown-clone".equals(System.getProperty("gramarye.p11.online.case", "")); }

    static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer,
            SkillReference reference, Path output) {
        require(selected() && run == null && actor != peer && !actor.getUUID().equals(peer.getUUID())
                && server.isSameThread(), "ARM_SCOPE");
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = owner == null ? null : owner.body(actor);
        require(body != null && owner.canCopy(body) && !server.isHardcore() && !actor.seenCredits && !actor.wonGame,
                "FRESH_NATIVE_DEATH_AND_FIRST_END");
        run = new Run(server, actor, peer, reference, output, owner, body.account);
    }

    static void released(int index, Object instanceValue, Object projectileValue, Object entryValue) {
        var r = run;
        require(r != null && r.failure == null && r.server.isSameThread() && index == r.releases
                && index < 2 && (index == 0 || r.deathSealed && r.nextCastTaken)
                && instanceValue instanceof ServerSlot.InstanceState && projectileValue instanceof P9StarterProjectile
                && entryValue instanceof P11CastCooldownData.Entry, "EXACT_ORIGINAL_RELEASE");
        var instance = (ServerSlot.InstanceState) instanceValue; var projectile = (P9StarterProjectile) projectileValue;
        var entry = (P11CastCooldownData.Entry) entryValue;
        require(instance.lease.reference.equals(r.reference) && instance.work != null
                && instance.cooldownReceipt != null && instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                && instance.cooldownReceipt.attemptId().equals(entry.attemptId)
                && instance.cooldownReceipt.releasedAt() == entry.releasedAt
                && projectile.getOwner() == r.actor && projectile.isAddedToLevel() && !projectile.isRemoved()
                && entry.kind == 0 && entry.duration == 600 && entry.expiresAt == entry.releasedAt + 600,
                "ACTUAL_OPEN_ARM_NOT_NATIVE_ADD_ALONE");
        if (index == 1) {
            require(gameTime(r) >= r.firstExpiry && !entry.attemptId.equals(r.firstAttempt), "SECOND_REAL_ATTEMPT_AFTER_EXPIRY");
        } else { r.firstExpiry = entry.expiresAt; r.firstAttempt = entry.attemptId; }
        r.instance = instance; r.projectile = projectile; r.active = entry; r.releases++;
        r.issued = false; r.successor = null; r.copyTarget = null; r.copy = null;
        r.copyEntries = r.copyReturns = r.serialWrites = r.serialReads = r.cloneEvents = r.respawns = 0;
    }

    /** Ordinary parent Post, before its generic living-actor check. Original native bodies are not caught/replayed. */
    static void tick() throws IOException {
        var r = run;
        require(r != null && r.server.isSameThread() && ++r.ticks <= 2400 && r.failure == null, "FINITE_CASE_AND_OBSERVER");
        current(r, r.peer);
        if (r.complete || r.releases == 0 || r.deathSealed && r.releases == 1) { return; }
        if (!r.issued) {
            current(r, r.actor);
            require(sameActive(entry(r, r.actor), r.active) && gameTime(r) < r.active.expiresAt
                    && r.instance.work != null && r.instance.activeProjectileContinuation != null,
                    "ACTIVE_BEFORE_ORIGINAL_BOUNDARY");
            r.server.getPlayerList().saveAll();
            var body = r.owner.body(r.actor); r.beforeFloor = body.cooldown.data.clockFloor; r.beforeEpoch = body.source.epoch();
            physical(r, r.actor, r.beforeFloor);
            P11C4aEvidence.write(r.output, "cooldown-clone-before-" + scene(r) + ".json", beforeFacts(r));
            r.issued = true;
            if (r.releases == 1) {
                r.actor.kill();
                require(r.actor.isDeadOrDying(), "ORIGINAL_KILL_RETURN");
            } else {
                require(!r.actor.seenCredits && !r.actor.wonGame && r.actor.level().dimension() == Level.OVERWORLD,
                        "FIRST_END_ORIGINAL_FLAGS");
                r.portal = portal(r.actor);
            }
            return;
        }
        if (r.releases == 2 && r.successor == null) {
            if (r.endStage == 0 && r.actor.level().dimension() == Level.END && !r.actor.wonGame) {
                restore(r); r.endStage = 1;
            }
            if (r.endStage == 1 && P11C4aEvidence.cuePresent(r.output.getParent().resolve("client-a"), "cooldown-clone-end-view.ready")) {
                r.portal = portal(r.actor); r.endStage = 2;
            }
            if (r.endStage == 2 && r.actor.wonGame) { restore(r); r.endStage = 3; }
        }
        if (r.successor == null) { return; }
        current(r, r.successor);
        var body = r.owner.body(r.successor);
        if (body == null || !body.complete || !r.owner.canCopy(body)) { return; }
        require(body.account == r.account && body.source.epoch() > r.beforeEpoch
                && r.successor.connection.getConnection() == r.connection
                && r.instance.work == null && r.instance.lease.pin.isClosed()
                && r.instance.activeProjectileContinuation == null && r.projectile.isRemoved()
                && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                && sameActive(entry(r, r.successor), r.active) && gameTime(r) < r.active.expiresAt,
                "SAME_C_CURRENT_SUCCESSOR_RETAINS_ARM_AFTER_WORK_TERMINAL");
        require(r.copyEntries == 1 && r.copyReturns == 1 && r.serialWrites == 1 && r.serialReads == 1
                && r.cloneEvents == 1 && r.respawns == 1 && r.copyTarget == r.successor,
                "ONE_TRUE_SERIALIZED_COPY_AND_NATIVE_SUCCESSOR");
        if (r.releases == 2) {
            require(r.endStage == 3 && r.successor.seenCredits && !r.successor.wonGame, "ORIGINAL_FIRST_END_COMPLETED");
        }
        String clientLeaf = "cooldown-clone-" + scene(r) + "-client.json";
        if (!clientReceipt(r, clientLeaf)) { return; }
        var client = readJson(r.output.getParent().resolve("client-a").resolve(clientLeaf));
        require(client.get("sourceEpoch").getAsLong() == body.source.epoch()
                && client.get("reference").getAsString().equals(r.reference.toString()), "ACTUAL_CLIENT_CURRENT_B_SOURCE");
        r.server.getPlayerList().saveAll(); physical(r, r.successor, r.beforeFloor);
        var facts = beforeFacts(r);
        facts.put("status", "NATIVE_POSITIVE_ACTIVE_SERIALIZED_CLONE_SAME_CONNECTION");
        facts.put("successorEpoch", body.source.epoch()); facts.put("successorVersion", body.source.version());
        facts.put("sameConnection", true); facts.put("newActor", true); facts.put("sameAccount", true);
        facts.put("copyWasDeath", r.releases == 1); facts.put("copyEntries", r.copyEntries);
        facts.put("copyNormalReturns", r.copyReturns); facts.put("actualSerializerWrites", r.serialWrites);
        facts.put("actualSerializerReads", r.serialReads); facts.put("nativeCloneEvents", r.cloneEvents);
        facts.put("nativeRespawnEvents", r.respawns); facts.put("workAndPinTerminalWithoutRefund", true);
        facts.put("savedFloor", r.savedFloor); facts.put("actualClientCurrentSource", true);
        P11C4aEvidence.write(r.output, "cooldown-clone-" + scene(r) + ".json", facts);
        r.actor = r.successor;
        if (r.releases == 1) { r.deathSealed = true; }
        else { r.complete = true; }
    }

    static boolean nextCastReady() {
        var r = run;
        if (r == null || !r.deathSealed || r.nextCastTaken || r.releases != 1 || gameTime(r) < r.firstExpiry) { return false; }
        require(r.failure == null && r.instance.work == null && r.instance.lease.pin.isClosed(), "NATURAL_EXPIRY_BETWEEN_CLONES");
        r.nextCastTaken = true; return true;
    }
    static ServerPlayer currentActor() { return run == null ? null : run.successor == null ? run.actor : run.successor; }
    static boolean complete() { return run != null && run.complete && run.failure == null; }
    static Map<String, Object> result() {
        require(complete(), "BOTH_NATIVE_CLONES_REQUIRED");
        return Map.of("status", "TWO_POSITIVE_OBLIGATIONS_NATIVE_DEATH_AND_FIRST_END", "genuineArms", run.releases,
                "samePhysicalConnection", true, "naturalExpiryBetweenAttempts", true, "reference", run.reference.toString());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void cloned(PlayerEvent.Clone event) {
        var r = run; if (r == null || !r.issued || event.getOriginal() != r.actor) { return; }
        observe(r, () -> {
            require(event.isWasDeath() == (r.releases == 1) && event.getEntity() == r.copyTarget && ++r.cloneEvents == 1,
                    "EXACT_ORIGINAL_CLONE_EVENT");
        });
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        var r = run;
        if (r == null || !r.issued || !(event.getEntity() instanceof ServerPlayer actor)
                || actor.getServer() != r.server || !actor.getUUID().equals(r.actor.getUUID())) { return; }
        observe(r, () -> {
            require(actor != r.actor && actor == r.copyTarget && r.successor == null
                    && event.isEndConquered() == (r.releases == 2)
                    && actor.connection.getConnection() == r.connection && ++r.respawns == 1, "EXACT_NATIVE_RESPAWN_EVENT");
            r.successor = actor;
        });
    }

    public static Object copyEntered(Entity from, Entity to, boolean death) {
        var r = run;
        if (r == null || !r.issued || from != r.actor) { return null; }
        observe(r, () -> {
            require(r.copy == null && to instanceof ServerPlayer && to != from && death == (r.releases == 1)
                    && ++r.copyEntries == 1, "EXACT_NATIVE_COPY_ENTRY");
            var before = P11CastCooldownAttachments.existing(r.actor);
            require(before != null && before.kind == P11CastCooldownData.Kind.ROUTED
                    && sameActive(before.entries.get(r.reference.skillId().value()), r.active), "COPY_CURRENT_SOURCE_ACTIVE");
            r.copyTarget = (ServerPlayer) to;
            r.copy = new Copy(before, P11CastCooldownAttachments.existing(r.copyTarget));
        });
        return r.copy;
    }
    public static void copyReturned(Object token, boolean normal) {
        var r = run; if (r == null || token == null || token != r.copy) { return; }
        var copy = r.copy;
        try {
            observe(r, () -> {
                require(normal && r.serialWrites == 1 && r.serialReads == 1 && copy.encoded != null, "ORIGINAL_SERIALIZED_COPY_RETURN");
                var after = P11CastCooldownAttachments.existing(r.copyTarget);
                require(after != null && after != copy.source && after != copy.targetBefore && after == copy.decoded
                        && P11CastCooldownCodec.write(after).equals(copy.encoded), "FRESH_NATIVE_COPY_EXACT_SERIALIZED_MATERIAL");
                r.copyReturns++;
            });
        } finally { r.copy = null; }
    }
    public static void serialized(Object data, Tag encoded) {
        var r = run; if (r == null || r.copy == null || data != r.copy.source) { return; }
        observe(r, () -> { require(encoded != null && ++r.serialWrites == 1, "ONE_ORIGINAL_COPY_SERIALIZER_WRITE"); r.copy.encoded = encoded.copy(); });
    }
    public static void deserialized(Object holder, Tag input, Object result) {
        var r = run; if (r == null || r.copy == null || holder != r.copyTarget) { return; }
        observe(r, () -> {
            require(result instanceof P11CastCooldownData && r.copy.encoded != null && r.copy.encoded.equals(input)
                    && ++r.serialReads == 1, "ONE_ORIGINAL_COPY_SERIALIZER_READ");
            r.copy.decoded = (P11CastCooldownData) result;
        });
    }
    static void release() { if (run != null) { restore(run); } run = null; }

    private static LinkedHashMap<String, Object> beforeFacts(Run r) {
        var values = new LinkedHashMap<String, Object>();
        values.put("status", "ACTUAL_ARM_AND_SAVE_BEFORE_ORIGINAL_CLONE"); values.put("scene", scene(r));
        values.put("reference", r.reference.toString()); values.put("durationTicks", r.active.duration);
        values.put("acceptedAt", r.active.acceptedAt); values.put("releaseNotAfter", r.active.releaseNotAfter);
        values.put("releasedAt", r.active.releasedAt); values.put("expiresAt", r.active.expiresAt);
        values.put("sourceEpoch", r.beforeEpoch); values.put("floorBefore", r.beforeFloor);
        return values;
    }
    private static void physical(Run r, ServerPlayer actor, long floor) throws IOException {
        var facts = P11NativeStorageBoundary.diagnostics(r.server, actor.getUUID());
        var writer = facts.writers().stream().filter(value -> value.kind().equals("PLAYER_DATA")).findFirst().orElseThrow();
        require(!writer.dirty() && writer.terminal().equals("COMPLETED") && writer.encode().equals("SUCCEEDED")
                && writer.write().equals("SUCCEEDED") && writer.close().equals("SUCCEEDED") && writer.replace().equals("SUCCEEDED"),
                "ACTUAL_CURRENT_PLAYER_WRITER");
        var file = r.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID() + ".dat");
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)
                && Files.size(file) <= 32L * 1024 * 1024, "OWNED_PHYSICAL_FILE");
        var data = NbtIo.readCompressed(file, NbtAccounter.create(32L * 1024 * 1024));
        require(data.hasUUID("UUID") && data.getUUID("UUID").equals(actor.getUUID()), "PHYSICAL_IDENTITY");
        var cooldown = P11CastCooldownCodec.read(data.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                .get(P11CastCooldownAttachments.ID.toString()));
        require(cooldown.kind == P11CastCooldownData.Kind.ROUTED && cooldown.clockFloor >= floor
                && sameActive(cooldown.entries.get(r.reference.skillId().value()), r.active), "PHYSICAL_SAME_ACTIVE_AND_FLOOR");
        r.savedFloor = cooldown.clockFloor;
    }
    private static P11CastCooldownData.Entry entry(Run r, ServerPlayer actor) {
        var body = r.owner.body(actor);
        require(body != null && r.owner.canCopy(body) && body.account == r.account
                && body.cooldown.data.kind == P11CastCooldownData.Kind.ROUTED, "QUALIFIED_CURRENT_COOLDOWN_SOURCE");
        return body.cooldown.data.entries.get(r.reference.skillId().value());
    }
    private static boolean sameActive(P11CastCooldownData.Entry a, P11CastCooldownData.Entry b) {
        return a != null && b != null && a.kind == 0 && a.skillId.equals(b.skillId) && a.revision == b.revision
                && a.duration == b.duration && a.attemptId.equals(b.attemptId) && a.acceptedAt == b.acceptedAt
                && a.releaseNotAfter == b.releaseNotAfter && a.releasedAt == b.releasedAt && a.expiresAt == b.expiresAt;
    }
    private static Portal portal(ServerPlayer actor) {
        var level = actor.serverLevel(); var position = actor.blockPosition().above(4).offset(4, 0, 0);
        var positions = List.of(position.below(), position, position.above(), position.above(2));
        var previous = new ArrayList<BlockState>();
        var placed = List.of(Blocks.OBSIDIAN.defaultBlockState(), Blocks.END_PORTAL.defaultBlockState(),
                Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState());
        require(position.getY() > level.getMinBuildHeight() && position.getY() + 2 < level.getMaxBuildHeight(), "PORTAL_BOUND");
        for (var pos : positions) { require(level.getBlockEntity(pos) == null, "PORTAL_NO_BLOCK_ENTITY"); previous.add(level.getBlockState(pos)); }
        for (int i = 0; i < positions.size(); i++) { level.setBlock(positions.get(i), placed.get(i), 3); }
        actor.connection.teleport(position.getX() + .5, position.getY() + .25, position.getZ() + .5, actor.getYRot(), actor.getXRot());
        return new Portal(level, positions, previous, placed);
    }
    private static void restore(Run r) {
        if (r.portal == null) { return; }
        var portal = r.portal;
        for (int i = 3; i >= 0; i--) {
            require(portal.level.getBlockState(portal.positions.get(i)).equals(portal.placed.get(i)), "OWNED_PORTAL_NOT_EXTERNALLY_CHANGED");
            portal.level.setBlock(portal.positions.get(i), portal.previous.get(i), 3);
        }
        r.portal = null;
    }
    private static boolean clientReceipt(Run r, String leaf) throws IOException {
        return P11C4aEvidence.receiptPresent(r.output.getParent().resolve("client-a"), leaf);
    }
    private static com.google.gson.JsonObject readJson(Path file) throws IOException {
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) && Files.size(file) <= 65_536,
                "FIXED_CLIENT_RECEIPT_BOUND");
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
    private static void current(Run r, ServerPlayer actor) {
        require(actor.getServer() == r.server && !actor.isFakePlayer() && !actor.isRemoved() && actor.isAlive()
                && actor.connection.getConnection().isConnected() && actor.connection.player == actor
                && r.server.getPlayerList().getPlayer(actor.getUUID()) == actor, "EXACT_CURRENT_ACTOR");
    }
    private static long gameTime(Run r) { return r.server.overworld().getGameTime(); }
    private static String scene(Run r) { return r.releases == 1 ? "death" : "end"; }
    private static void observe(Run r, Runnable observation) {
        if (r.failure != null) { return; }
        try { require(r.server.isSameThread(), "OBSERVER_MAIN_THREAD"); observation.run(); }
        catch (RuntimeException | Error failure) { r.failure = "NATIVE_COPY_OR_SUCCESSOR_OBSERVER"; }
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "COOLDOWN_CLONE_" + code); }
    private record Portal(ServerLevel level, List<BlockPos> positions, List<BlockState> previous, List<BlockState> placed) { }
    private static final class Copy {
        final P11CastCooldownData source, targetBefore; P11CastCooldownData decoded; Tag encoded;
        Copy(P11CastCooldownData source, P11CastCooldownData targetBefore) { this.source = source; this.targetBefore = targetBefore; }
    }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer peer; final Connection connection; final SkillReference reference;
        final Path output; final P11QualifiedSourceOwner owner; final P11QualifiedSourceOwner.Account account;
        ServerPlayer actor, successor, copyTarget; ServerSlot.InstanceState instance; P9StarterProjectile projectile;
        P11CastCooldownData.Entry active; Copy copy; Portal portal; String failure;
        java.util.UUID firstAttempt; long firstExpiry, beforeEpoch, beforeFloor, savedFloor;
        int releases, ticks, endStage, copyEntries, copyReturns, serialWrites, serialReads, cloneEvents, respawns;
        boolean issued, deathSealed, nextCastTaken, complete;
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, SkillReference reference, Path output,
                P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Account account) {
            this.server = server; this.actor = actor; this.peer = peer; connection = actor.connection.getConnection();
            this.reference = reference; this.output = output; this.owner = owner; this.account = account;
        }
    }
}
