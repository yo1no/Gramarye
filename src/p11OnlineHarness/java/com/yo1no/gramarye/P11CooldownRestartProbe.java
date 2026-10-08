package com.yo1no.gramarye;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentStorePersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/** Excluded fixed stopped-world readback; never mutates cooldowns, clocks, work, or files. */
public final class P11CooldownRestartProbe {
    private static final long FILE_BOUND = 32L * 1024 * 1024;
    private static final String STOPPED = "ORIGINAL_COOLDOWN_STOPPED_WORLD_NOT_RESTART_PROOF";
    private static MinecraftServer server;
    private static ServerSlot slot;
    private static SkillDefinitionStoreService store;
    private static Path world, output;
    private static UUID owner;
    private static SkillReference reference;
    private static P11CastCooldownData.Entry obligation;
    private static ServerSlot.InstanceState stoppingInstance;
    private static P9StarterProjectile stoppingProjectile;
    private static RuntimeProjectileContinuationPermit stoppingPermit;
    private static Map<String, Object> loadedConfiguration;
    private static String definitionSha;
    private static long savedGameTime;
    private static boolean readPrepared, beforeStop, writeComplete, readLoaded;
    private P11CooldownRestartProbe() {}

    public static boolean writeSelected() { return selected().equals("cooldown-restart-write"); }
    public static boolean readSelected() { return selected().equals("cooldown-restart-read"); }
    private static String selected() { return System.getProperty("gramarye.p11.online.case", ""); }

    static void initialize(MinecraftServer exact, ServerSlot actualSlot, SkillDefinitionStoreService actualStore) {
        if (!writeSelected() && !readSelected()) return;
        require(server == null && exact != null && exact.isSameThread() && actualSlot != null && actualStore != null,
                "SOLE_ACTUAL_COMPOSITION");
        server = exact; slot = actualSlot; store = actualStore;
        emptyRuntime(true);
    }

    static void beforeOriginalStop(ServerPlayer actor, SkillReference ref, P11CastCooldownData.Entry entry,
            ServerSlot.InstanceState instance, P9StarterProjectile projectile, Path serverOutput) throws IOException {
        require(writeSelected() && !beforeStop && actor.getServer() == server && server.isSameThread()
                && server.isRunning() && !server.isStopped() && actor.connection.getConnection().isConnected()
                && instance.work != null && instance.work.qualifies(actor) && !instance.lease.pin.isClosed()
                && instance.activeProjectileContinuation != null && instance.activeProjectileContinuation.state
                        == RuntimeProjectileContinuationPermit.State.OPEN
                && projectile.hasContinuationPermitIdentity(instance.activeProjectileContinuation)
                && !projectile.isRemoved() && projectile.isAddedToLevel() && projectile.getOwner() == actor
                && slot.instances.get(instance.id) == instance && slot.instances.size() == 1
                && slot.activeProjectileContinuations.size() == 1 && entry.kind == 0 && entry.duration == 600
                && instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                && instance.cooldownReceipt.attemptId().equals(entry.attemptId)
                && server.overworld().getGameTime() < entry.expiresAt, "GENUINE_OPEN_ARM_BEFORE_STOP");
        owner = actor.getUUID(); reference = ref; obligation = entry; stoppingInstance = instance;
        stoppingProjectile = projectile; stoppingPermit = instance.activeProjectileContinuation;
        output = serverOutput; world = exactWorld(server); definitionSha = definitionHash();
        loadedConfiguration = P11C4aLoadedConfiguration.snapshot();
        physical();
        beforeStop = true;
        P11C4aEvidence.write(output, "cooldown-restart-before-stop.json", Map.of(
                "status", "REAL_R_OPEN_ARM_AND_ORIGINAL_SAVE_BEFORE_HALT", "durationTicks", 600,
                "gameTime", server.overworld().getGameTime(), "expiresAt", entry.expiresAt,
                "originalWorkPresent", true, "nativeProjectileAge", projectile.tickCount,
                "helperCalledHalt", false));
    }

    static void stopped(MinecraftServer exact) throws IOException {
        if (!writeSelected()) return;
        require(exact == server && exact.isSameThread() && exact.isStopped() && beforeStop && !writeComplete,
                "ORIGINAL_STOPPED_AFTER_ARM");
        require(stoppingInstance.work == null && stoppingInstance.p9ActorWitness() == null
                && stoppingInstance.activeProjectileContinuation == null && stoppingInstance.lease.pin.isClosed()
                && stoppingPermit.state == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                && stoppingProjectile.isRemoved()
                && stoppingInstance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM,
                "STOP_TERMINATED_WORK_WITHOUT_REFUNDING_ARM");
        var data = P11NativeStorageBoundary.terminalDiagnostics();
        require(data != null && data.nativeStopNormal() && data.failures() == 0 && data.resources().dirtyUuids() == 0
                && data.nativeResponsibilities().roots().size() == 5
                && data.nativeResponsibilities().roots().stream().allMatch(value -> value.count() == 0), "CLEAN_NATIVE_STOP");
        physical();
        savedGameTime = nbt(world.resolve("level.dat")).getCompound("Data").getLong("Time");
        require(savedGameTime == server.overworld().getGameTime() && savedGameTime >= obligation.releasedAt
                && savedGameTime < obligation.expiresAt, "ORIGINAL_STOPPED_WORLD_CLOCK");
        var config = Path.of((String) loadedConfiguration.get("path")); regular(config, FILE_BOUND);
        require(hash(config).equals(loadedConfiguration.get("sha256")), "ACTUAL_CONFIG_UNCHANGED_AT_STOP");
        var receipt = new LinkedHashMap<String, Object>();
        receipt.put("schema", 1); receipt.put("status", STOPPED); receipt.put("case", "cooldown-restart-write");
        receipt.put("writeRunId", P11C4aEvidence.property("runId"));
        receipt.put("productionJarSha256", P11OnlineInputs.verifyFrozenJar()); receipt.put("world", world.toString());
        receipt.put("publicMinecraftUuid", owner.toString()); receipt.put("skillId", reference.skillId().value().toString());
        receipt.put("revision", reference.revision().value()); receipt.put("cooldownTicks", 600);
        receipt.put("obligation", Map.of("acceptedAt", obligation.acceptedAt, "releaseNotAfter", obligation.releaseNotAfter,
                "releasedAt", obligation.releasedAt, "expiresAt", obligation.expiresAt, "attemptId", obligation.attemptId.toString()));
        receipt.put("definitionSha256", definitionSha); receipt.put("fileSha256", physicalHashes());
        receipt.put("loadedConfiguration", loadedConfiguration); receipt.put("stoppedGameTime", savedGameTime);
        receipt.put("originalStopNormal", true); receipt.put("openWorkClosedByStop", true);
        P11C4aEvidence.write(output, "cooldown-restart-expected.json", receipt); writeComplete = true;
    }

    static void prepareRead(Path serverOutput) throws IOException {
        require(readSelected() && server != null && server.isSameThread() && !readPrepared
                && server.getPlayerList().getPlayers().isEmpty(), "NEW_SERVER_BEFORE_LOGIN");
        emptyRuntime(true); output = serverOutput; world = exactWorld(server);
        var input = input(server);
        owner = UUID.fromString(input.get("publicMinecraftUuid").getAsString());
        reference = new SkillReference(new SkillId(UUID.fromString(input.get("skillId").getAsString())),
                new SkillRevision(input.get("revision").getAsInt()));
        var value = input.getAsJsonObject("obligation");
        require(value.keySet().equals(Set.of("acceptedAt", "releaseNotAfter", "releasedAt", "expiresAt", "attemptId")),
                "EXACT_IMMUTABLE_OBLIGATION_FIELDS");
        long accepted = value.get("acceptedAt").getAsLong(), bound = value.get("releaseNotAfter").getAsLong();
        long released = value.get("releasedAt").getAsLong(), expires = value.get("expiresAt").getAsLong();
        require(accepted >= 0 && bound >= accepted && released >= accepted && released <= bound
                && expires == Math.addExact(released, 600), "ORIGINAL_OBLIGATION_BOUNDS");
        obligation = P11CastCooldownData.Entry.pending(reference.skillId().value(), reference.revision().value(), 600,
                accepted, UUID.fromString(value.get("attemptId").getAsString()), bound).active(released);
        // This detached decoded comparison value is never installed into an actor, source, or service.
        savedGameTime = input.get("stoppedGameTime").getAsLong(); definitionSha = input.get("definitionSha256").getAsString();
        require(savedGameTime >= released && savedGameTime < expires
                && server.overworld().getGameTime() >= savedGameTime && definitionHash().equals(definitionSha), "NATIVE_NEW_CLOCK_AND_DOCUMENT");
        require(physicalHashes().equals(input.getAsJsonObject("fileSha256")), "SAME_STOPPED_FILES_BEFORE_LOGIN");
        var actualConfig = P11C4aLoadedConfiguration.snapshot(); var priorConfig = input.getAsJsonObject("loadedConfiguration");
        require(actualConfig.get("path").equals(priorConfig.get("path").getAsString())
                && actualConfig.get("sha256").equals(priorConfig.get("sha256").getAsString()), "SAME_ACTUAL_LOADED_CONFIGURATION");
        physical(); readPrepared = true;
        P11C4aEvidence.write(output, "cooldown-restart-before-login.json", Map.of(
                "status", "ACTUAL_NEW_RUNTIME_EMPTY_AND_STOPPED_COOLDOWN_FILES", "sameSavedBytes", true,
                "storedGameTime", savedGameTime, "actualGameTime", server.overworld().getGameTime(),
                "instances", 0, "pendingEvents", 0, "permits", 0, "eventHighWater", slot.eventSequenceHighWater,
                "newServerNoRuntimeRestoration", true));
    }

    static void loaded(ServerPlayer actor, SkillReference equipped, P11CastCooldownData.Entry entry) throws IOException {
        require(readSelected() && readPrepared && !readLoaded && server.isSameThread()
                && actor.getServer() == server && actor.getUUID().equals(owner) && equipped.equals(reference)
                && !actor.isRemoved() && actor.connection.getConnection().isConnected()
                && server.getPlayerList().getPlayer(owner) == actor && same(entry, obligation)
                && server.overworld().getGameTime() >= savedGameTime && server.overworld().getGameTime() < obligation.expiresAt,
                "ACTUAL_SAME_AUTHENTICATED_SOURCE_UNEXPIRED");
        emptyRuntime(true);
        require(P11NativeStorageBoundary.diagnostics(server, owner).nativeResponsibilities().roots().stream()
                .filter(root -> root.kind().equals("WORK")).allMatch(root -> root.count() == 0), "NO_WORK_RESTORED");
        require(definitionHash().equals(definitionSha), "SAME_LOADED_DOCUMENT");
        readLoaded = true;
        P11C4aEvidence.write(output, "cooldown-restart-loaded.json", Map.of(
                "status", "REAL_NEW_SERVER_LOGIN_SAME_ACTIVE_OBLIGATION", "reference", reference.toString(),
                "releasedAt", obligation.releasedAt, "expiresAt", obligation.expiresAt,
                "actualGameTime", server.overworld().getGameTime(), "sameImmutableFields", true,
                "workRestored", false, "starterOrRegrant", false));
    }

    static SkillReference reference() { require(readPrepared, "READ_PREPARED_REFERENCE"); return reference; }
    static long expiresAt() { require(readPrepared, "READ_PREPARED_EXPIRY"); return obligation.expiresAt; }
    static boolean isNewReleaseAfterExpiry(P11CastCooldownData.Entry value) {
        // Token/event scalars intentionally restart with the process; UUID inequality is not cross-server proof.
        return readLoaded && value.acceptedAt >= obligation.expiresAt && value.releasedAt >= value.acceptedAt
                && value.releasedAt > obligation.releasedAt && value.expiresAt > obligation.expiresAt;
    }

    static String expectedConfigurationHash(Path actualCanonicalFile) throws IOException {
        require(readSelected() && server != null && server.isSameThread() && server.isRunning() && !server.isStopped(),
                "CONFIG_EXACT_NEW_SERVER");
        var input = input(server); var prior = input.getAsJsonObject("loadedConfiguration");
        var path = Path.of(prior.get("path").getAsString()); var sha = prior.get("sha256").getAsString();
        require(prior.keySet().equals(Set.of("path", "sha256")) && path.isAbsolute() && path.equals(actualCanonicalFile)
                && path.startsWith(exactWorld(server).getParent()) && path.getFileName().toString().equals(P5ServerRuntimeConfig.CONFIG_FILE_NAME)
                && sha.matches("[0-9a-f]{64}"), "CONFIG_ONLY_EXACT_RECORDED_NATIVE_FILE");
        regular(path, FILE_BOUND); return sha;
    }
    private static JsonObject input(MinecraftServer exact) throws IOException {
        var path = P11C4aEvidence.root().resolve("cooldown-restart-input.json"); regular(path, 32_768);
        var value = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        require(value.keySet().equals(Set.of("schema", "status", "case", "writeRunId", "productionJarSha256", "world",
                "publicMinecraftUuid", "skillId", "revision", "cooldownTicks", "obligation", "definitionSha256", "fileSha256",
                "loadedConfiguration", "stoppedGameTime", "originalStopNormal", "openWorkClosedByStop"))
                && value.get("schema").getAsInt() == 1 && value.get("status").getAsString().equals(STOPPED)
                && value.get("case").getAsString().equals("cooldown-restart-write")
                && !value.get("writeRunId").getAsString().equals(P11C4aEvidence.property("runId"))
                && value.get("productionJarSha256").getAsString().equals(P11OnlineInputs.verifyFrozenJar())
                && value.get("world").getAsString().equals(exactWorld(exact).toString())
                && value.get("cooldownTicks").getAsInt() == 600 && value.get("originalStopNormal").getAsBoolean()
                && value.get("openWorkClosedByStop").getAsBoolean(), "EXACT_CLOSED_STOPPED_WORLD_INPUT");
        return value;
    }
    private static void emptyRuntime(boolean neverAccepted) {
        require(slot.state == ServerSlot.State.RUNNING && slot.instances.isEmpty() && slot.leases.isEmpty()
                && slot.queue.isEmpty() && slot.eventIndex.isEmpty() && slot.activeProjectileContinuations.isEmpty()
                && slot.committedPending == 0 && slot.reservedPending == 0 && slot.deferredCount == 0
                && slot.currentReservationCount == 0 && slot.currentReservationOwner == null && slot.currentEvent == null
                && !slot.dispatching && (!neverAccepted || slot.eventSequenceHighWater == 0 && slot.skillInstanceSequenceHighWater == 0),
                "ACTUAL_FRESH_RUNTIME_NO_RESTORATION");
    }
    private static void physical() throws IOException {
        var player = nbt(world.resolve("playerdata").resolve(owner + ".dat"));
        require(player.hasUUID("UUID") && player.getUUID("UUID").equals(owner), "PHYSICAL_OWNER");
        var attachment = player.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        var material = P11CastCooldownCodec.read(attachment.get(P11CastCooldownAttachments.ID.toString()));
        require(material.kind == P11CastCooldownData.Kind.ROUTED && same(material.entries.get(reference.skillId().value()), obligation),
                "PHYSICAL_UNREFUNDED_ACTIVE_OBLIGATION");
        int matches = 0;
        for (Tag tag : attachment.getCompound("gramarye:player_skills").getList("equipped_slots", Tag.TAG_COMPOUND)) {
            var item = (CompoundTag) tag;
            if (item.getInt("slot") == 0) {
                require(SkillReference.CODEC.parse(NbtOps.INSTANCE, item.get("reference")).result().filter(reference::equals).isPresent(),
                        "PHYSICAL_SAME_EQUIPPED_REFERENCE"); matches++;
            }
        }
        require(matches == 1, "ONE_PHYSICAL_EQUIPPED_SLOT_ZERO");
    }
    private static JsonObject physicalHashes() throws IOException {
        var hashes = new JsonObject();
        for (var leaf : List.of("data/gramarye_skill_definitions.dat", "playerdata/" + owner + ".dat")) {
            var file = world.resolve(leaf); regular(file, FILE_BOUND); hashes.addProperty(leaf, hash(file));
        }
        return hashes;
    }
    private static String definitionHash() {
        var result = store.find(server, reference);
        require(result instanceof SkillSubsystemResult.Available<?> available
                && available.value() instanceof Optional<?> value && value.isPresent(), "ORIGINAL_DOCUMENT_PRESENT");
        var document = ((SkillSubsystemResult.Available<Optional<SkillDocument>>) result).value().orElseThrow();
        require(document.skillId().equals(reference.skillId()) && document.revision().equals(reference.revision()), "EXACT_DOCUMENT_ROUTE");
        var encoded = SkillDocumentStorePersistenceFacade.encodeCurrent(document);
        require(encoded instanceof SkillDocumentStorePersistenceFacade.Encoded, "ORIGINAL_DOCUMENT_ENCODING");
        return P11C4aEvidence.hash(((SkillDocumentStorePersistenceFacade.Encoded) encoded).document().copyBytes());
    }
    private static boolean same(P11CastCooldownData.Entry actual, P11CastCooldownData.Entry expected) {
        return actual != null && actual.kind == 0 && actual.skillId.equals(expected.skillId) && actual.revision == expected.revision
                && actual.duration == expected.duration && actual.acceptedAt == expected.acceptedAt
                && actual.attemptId.equals(expected.attemptId) && actual.releaseNotAfter == expected.releaseNotAfter
                && actual.releasedAt == expected.releasedAt && actual.expiresAt == expected.expiresAt;
    }
    private static Path exactWorld(MinecraftServer exact) throws IOException {
        var path = exact.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(path.getFileName().toString().equals("p11-online-world") && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path) && path.equals(path.toRealPath()), "EXACT_ORIGINAL_WORLD_PATH"); return path;
    }
    private static CompoundTag nbt(Path path) throws IOException { regular(path, FILE_BOUND); return NbtIo.readCompressed(path, NbtAccounter.create(FILE_BOUND)); }
    private static String hash(Path path) throws IOException { return P11C4aEvidence.hash(Files.readAllBytes(path)); }
    private static void regular(Path path, long bound) throws IOException {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
                && path.toAbsolutePath().normalize().equals(path.toRealPath()) && Files.size(path) > 0 && Files.size(path) <= bound,
                "OWNED_REGULAR_BOUNDED_FILE");
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "COOLDOWN_RESTART_" + code); }
}
