package com.yo1no.gramarye;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentStorePersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/** Excluded same-world restart readback. Never starts a server, admits work, saves, closes, or repairs. */
public final class P11L1RestartProbe {
    private static final long FILE_BOUND = 32L * 1024 * 1024;
    private static final String REWARD = "gramarye_p11_engineering:l1_first_kill";
    private static PlayerSkillAttachmentService attachments;
    private static SkillDefinitionStoreService store;
    private static MinecraftServer actualServer;
    private static SkillRuntimeService actualRuntime;
    private static ServerSlot actualSlot;
    private static Path output, world;
    private static JsonObject expected;
    private static Map<String, Object> loadedConfiguration;
    private static UUID ownerId;
    private static String scoreboardHolderHash;
    private static SkillReference reference;
    private static ServerSlot.InstanceState stoppingInstance;
    private static RuntimeProjectileContinuationPermit stoppingPermit;
    private static P9StarterProjectile stoppingProjectile;
    private static BlockPos stoppedPosition;
    private static String stoppedDimension;
    private static boolean beforeStop, stopped, readPrepared, readComplete;
    private static int readTicks;
    private P11L1RestartProbe() {}

    public static boolean writeSelected() { return selected().equals("l1-restart-write"); }
    public static boolean readSelected() { return selected().equals("l1-restart-read"); }
    private static String selected() { return System.getProperty("gramarye.p11.online.case", ""); }

    /** Original P9StarterCommand composition only; no substitute service is constructed. */
    public static void composition(PlayerSkillAttachmentService actualAttachments, SkillDefinitionStoreService actualStore) {
        if (!writeSelected() && !readSelected()) { return; }
        require(attachments == null && store == null && actualAttachments != null && actualStore != null, "COMPOSITION_ONCE");
        attachments = actualAttachments; store = actualStore;
    }

    /** Original handleRuntimeStarted normal RETURN, receiving the exact slots.get(server) identity. */
    public static void runtimeStarted(Object runtime, MinecraftServer server, Object slot) {
        if (!writeSelected() && !readSelected()) { return; }
        require(actualServer == null && runtime instanceof SkillRuntimeService && slot instanceof ServerSlot
                && server != null && server.isSameThread() && server.isRunning() && !server.isStopped(), "ACTUAL_RUNTIME_START_ONCE");
        actualRuntime = (SkillRuntimeService) runtime; actualServer = server; actualSlot = (ServerSlot) slot;
        emptyRuntime(true);
    }

    /** After first original natural R/kill/reward and save, while the genuine replacement is online. */
    public static void captureCompleted(MinecraftServer server, ServerPlayer actor, Object completed, Path serverOutput)
            throws IOException {
        require(writeSelected() && expected == null && server == actualServer && attachments != null
                && completed instanceof ServerSlot.InstanceState, "CAPTURE_COMPLETED_SCOPE");
        current(actor); emptyRuntime(false);
        var instance = (ServerSlot.InstanceState) completed;
        require(instance.work == null && instance.lease.pin.isClosed() && instance.activeProjectileContinuation == null,
                "FIRST_REAL_WORK_TERMINAL");
        reference = equipped(actor);
        require(reference.equals(instance.lease.reference), "FIRST_REAL_REFERENCE");
        ownerId = actor.getUUID(); scoreboardHolderHash = hashText(actor.getScoreboardName());
        world = exactWorld(server); output = serverOutput;
        expected = logical(actor);
        require(expected.get("xp").getAsInt() == 10 && expected.get("bread").getAsInt() == 1
                && expected.get("scoreboard").getAsInt() == 1 && expected.get("chicken").getAsInt() == 1
                && expected.get("mobKills").getAsInt() == 1, "FIRST_REAL_NATIVE_REWARD_VALUES");
        physical(expected, false);
        P11C4aEvidence.write(output, "l1-restart-first-work.json", Map.of("status", "FIRST_REAL_WORK_AND_NATIVE_SAVE_OBSERVED",
                "ownerHash", hashText(ownerId.toString()), "definitionSha256", expected.get("definitionSha256").getAsString(),
                "reference", reference.toString(), "nativePlayerScore", actor.getScore(), "p11_l1", 1));
    }

    /** Existing accepted observer passes the second genuine R's actual constructor-observed instance/result. */
    public static void accepted(ServerPlayer actor, Object instanceValue, Object result) {
        if (!writeSelected() || expected == null) { return; }
        require(!beforeStop && stoppingInstance == null && instanceValue instanceof ServerSlot.InstanceState
                && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly, "SECOND_ORIGINAL_ACCEPTANCE_ONCE");
        current(actor);
        var instance = (ServerSlot.InstanceState) instanceValue;
        require(actor.getUUID().equals(ownerId) && instance.id.equals(((RuntimeAdmissionResult.AcceptedMemoryOnly) result)
                .eventToken().skillInstanceId()) && instance.lease.reference.equals(reference)
                && instance.work != null && instance.work.qualifies(actor) && !instance.lease.pin.isClosed()
                && instance.hasP9AuthenticatedActorWitness(actor), "SECOND_EXACT_ACCEPTED_WORK");
        stoppingInstance = instance;
    }

    public static void transferred(Object value, Object disposition) {
        if (!writeSelected() || expected == null || disposition != RuntimePermitTransferDisposition.TRANSFERRED) { return; }
        require(stoppingInstance != null && stoppingProjectile == null && value instanceof P9StarterProjectile, "SECOND_ORIGINAL_TRANSFER_ONCE");
        var projectile = (P9StarterProjectile) value;
        var permit = stoppingInstance.activeProjectileContinuation;
        require(permit != null && permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                && permit.plannedProjectileId.equals(projectile.getUUID()) && projectile.hasContinuationPermitIdentity(permit),
                "SECOND_EXACT_OPEN_PERMIT");
        stoppingPermit = permit; stoppingProjectile = projectile;
    }

    /** Parent calls on ordinary Post, then invokes original halt(false) once; this method does not stop anything. */
    public static boolean beforeOriginalStop(ServerPlayer actor) throws IOException {
        require(writeSelected() && expected != null && !beforeStop, "STOP_SCOPE");
        if (stoppingProjectile == null || stoppingProjectile.tickCount == 0) { return false; }
        current(actor);
        require(logical(actor).equals(expected), "FIRST_REWARD_NOT_CHANGED_BY_EMPTY_RAY");
        require(stoppingInstance.work != null && stoppingInstance.work.qualifies(actor)
                && !stoppingInstance.lease.pin.isClosed() && stoppingInstance.activeProjectileContinuation == stoppingPermit
                && stoppingPermit.state == RuntimeProjectileContinuationPermit.State.OPEN
                && !stoppingProjectile.isRemoved() && stoppingProjectile.getOwner() == actor
                && actor.serverLevel().getEntity(stoppingProjectile.getUUID()) == stoppingProjectile
                && actualSlot.instances.get(stoppingInstance.id) == stoppingInstance
                && actualSlot.activeProjectileContinuations.get(stoppingPermit.permitId) == stoppingPermit
                && actualSlot.instances.size() == 1 && actualSlot.activeProjectileContinuations.size() == 1
                && roots(actor, "WORK") == 1, "NONVACUOUS_GENUINE_OPEN_AT_STOP");
        stoppedPosition = stoppingProjectile.blockPosition(); stoppedDimension = stoppingProjectile.level().dimension().location().toString();
        // Capture while the platform config is still loaded; ServerStopped may follow config unload.
        loadedConfiguration = P11C4aLoadedConfiguration.snapshot();
        beforeStop = true;
        P11C4aEvidence.write(output, "l1-restart-before-stop.json", Map.of("status", "GENUINE_SECOND_R_OPEN_BEFORE_ORIGINAL_STOP",
                "workCount", 1, "age", stoppingProjectile.tickCount, "secondInstance", stoppingInstance.id.toString(),
                "permitState", stoppingPermit.state.name(), "helperCalledHalt", false));
        return true;
    }

    /** Original ServerStopped only, after actual source retirement/physical native stop. */
    public static void stopped(MinecraftServer server) throws IOException {
        if (!writeSelected()) { return; }
        require(server == actualServer && server.isSameThread() && server.isStopped() && beforeStop && !stopped,
                "ORIGINAL_STOPPED_AFTER_OPEN");
        require(stoppingInstance.work == null && stoppingInstance.p9ActorWitness() == null
                && stoppingInstance.lease.pin.isClosed() && stoppingInstance.activeProjectileContinuation == null
                && stoppingPermit.state == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                && stoppingProjectile.isRemoved(), "ORIGINAL_STOP_CLOSED_WORK_NOT_MANUAL_CLEANUP");
        var terminal = P11NativeStorageBoundary.terminalDiagnostics();
        require(terminal != null && terminal.nativeStopNormal() && terminal.failures() == 0
                && terminal.resources().dirtyUuids() == 0 && terminal.nativeResponsibilities().roots().stream()
                .allMatch(value -> value.count() == 0), "ORIGINAL_CLEAN_PHYSICAL_STOP");
        physical(expected, true);
        var configPath = Path.of((String) loadedConfiguration.get("path"));
        regular(configPath, FILE_BOUND);
        require(P11C4aEvidence.hash(Files.readAllBytes(configPath)).equals(loadedConfiguration.get("sha256")),
                "LOADED_CONFIG_UNCHANGED_THROUGH_ORIGINAL_STOP");
        var receipt = new LinkedHashMap<String, Object>();
        receipt.put("status", "ORIGINAL_STOPPED_WORLD_READY_FOR_NEW_SERVER_NOT_RESTART_PROOF");
        receipt.put("writeRunId", P11C4aEvidence.property("runId")); receipt.put("world", world.toString());
        receipt.put("publicMinecraftUuid", ownerId.toString()); receipt.put("scoreboardHolderHash", scoreboardHolderHash);
        receipt.put("skillId", reference.skillId().value().toString()); receipt.put("revision", reference.revision().value());
        receipt.put("material", expected); receipt.put("fileSha256", physicalHashes());
        receipt.put("loadedConfiguration", loadedConfiguration);
        receipt.put("stoppedProjectile", stoppingProjectile.getUUID().toString()); receipt.put("dimension", stoppedDimension);
        receipt.put("chunkX", stoppedPosition.getX() >> 4); receipt.put("chunkZ", stoppedPosition.getZ() >> 4);
        receipt.put("originalStopNormal", true); receipt.put("openWorkClosedByStop", true);
        P11C4aEvidence.write(output, "l1-restart-expected.json", receipt); stopped = true;
    }

    /** Fixed fresh evidence-root input is copied by the closed launcher from the exact original stopped receipt. */
    public static Map<String, Object> prepareRead(MinecraftServer server, Path serverOutput) throws IOException {
        require(readSelected() && server == actualServer && !readPrepared && expected == null
                && store != null && attachments != null && server.getPlayerList().getPlayers().isEmpty(), "NEW_SERVER_BEFORE_ANY_PLAYER");
        emptyRuntime(true); output = serverOutput; world = exactWorld(server);
        var input = readJson(P11C4aEvidence.root().resolve("l1-restart-input.json"));
        require(input.get("status").getAsString().equals("ORIGINAL_STOPPED_WORLD_READY_FOR_NEW_SERVER_NOT_RESTART_PROOF")
                && !input.get("writeRunId").getAsString().equals(P11C4aEvidence.property("runId"))
                && input.get("originalStopNormal").getAsBoolean() && input.get("openWorkClosedByStop").getAsBoolean()
                && input.get("world").getAsString().equals(world.toString()), "EXACT_OLD_STOPPED_WORLD_INPUT");
        var actualConfig = P11C4aLoadedConfiguration.snapshot();
        var priorConfig = input.getAsJsonObject("loadedConfiguration");
        require(actualConfig.get("path").equals(priorConfig.get("path").getAsString())
                && actualConfig.get("sha256").equals(priorConfig.get("sha256").getAsString()),
                "ACTUAL_RESTART_LOADED_EXACT_OLD_CONFIGURATION");
        ownerId = UUID.fromString(input.get("publicMinecraftUuid").getAsString());
        scoreboardHolderHash = input.get("scoreboardHolderHash").getAsString();
        reference = new SkillReference(new SkillId(UUID.fromString(input.get("skillId").getAsString())),
                new SkillRevision(input.get("revision").getAsInt()));
        expected = input.getAsJsonObject("material").deepCopy();
        require(physicalHashes().equals(input.getAsJsonObject("fileSha256")), "SAME_STOPPED_PHYSICAL_BYTES_BEFORE_LOGIN");
        require(definitionHash().equals(expected.get("definitionSha256").getAsString()), "ORIGINAL_NEW_STORE_LOADED_EXACT_DOCUMENT");
        physical(expected, true); noProjectiles();
        require(rootsForOwner("WORK") == 0, "NEW_SOURCE_HAS_NO_RESTORED_W");
        stoppedDimension = input.get("dimension").getAsString();
        stoppedPosition = new BlockPos(input.get("chunkX").getAsInt() << 4, 64, input.get("chunkZ").getAsInt() << 4);
        readPrepared = true;
        return Map.of("status", "ACTUAL_NEW_RUNTIME_EMPTY_AND_SAME_WORLD_FILES_BEFORE_ANY_R",
                "runtimeTick", actualSlot.runtimeTick, "eventSequence", actualSlot.eventSequenceHighWater,
                "instanceSequence", actualSlot.skillInstanceSequenceHighWater, "work", 0,
                "definitionSha256", definitionHash(), "sameWorldPath", true, "sameSavedBytes", true);
    }

    /** Only the closed launcher-supplied stopped-world receipt can admit its exact native config. */
    static String expectedConfigurationHash(Path actualCanonicalFile) throws IOException {
        require(readSelected() && actualServer != null && actualServer.isSameThread()
                && actualServer.isRunning() && !actualServer.isStopped(), "CONFIG_READ_EXACT_NEW_SERVER");
        var actualWorld = exactWorld(actualServer);
        var input = readJson(P11C4aEvidence.root().resolve("l1-restart-input.json"));
        require(input.get("status").getAsString().equals("ORIGINAL_STOPPED_WORLD_READY_FOR_NEW_SERVER_NOT_RESTART_PROOF")
                && !input.get("writeRunId").getAsString().equals(P11C4aEvidence.property("runId"))
                && input.get("originalStopNormal").getAsBoolean() && input.get("openWorkClosedByStop").getAsBoolean()
                && input.get("world").getAsString().equals(actualWorld.toString()), "CONFIG_OLD_STOPPED_WORLD_BINDING");
        var prior = input.getAsJsonObject("loadedConfiguration");
        var path = Path.of(prior.get("path").getAsString());
        var hash = prior.get("sha256").getAsString();
        require(path.isAbsolute() && path.equals(actualCanonicalFile) && path.startsWith(actualWorld.getParent())
                && path.getFileName().toString().equals(P5ServerRuntimeConfig.CONFIG_FILE_NAME)
                && hash.matches("[0-9a-f]{64}"), "CONFIG_EXACT_RECORDED_PATH_AND_HASH");
        regular(path, FILE_BOUND);
        return hash;
    }

    /** Genuine authenticated login supplied by parent; no provisioning/cast. Wait only for ordinary chunk loading. */
    public static Optional<Map<String, Object>> verifyRead(ServerPlayer actor) throws IOException {
        require(readSelected() && readPrepared && !readComplete, "READ_SCOPE"); current(actor);
        require(actor.getUUID().equals(ownerId) && hashText(actor.getScoreboardName()).equals(scoreboardHolderHash), "SAME_AUTHENTICATED_OWNER");
        emptyRuntime(true); require(roots(actor, "WORK") == 0, "NO_WORK_RESTORED_AFTER_LOGIN"); noProjectiles();
        var level = actualServer.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(stoppedDimension)));
        require(level != null, "ORIGINAL_DIMENSION_LOADED");
        require(level instanceof P11L1TrackingBoundaryProbe.LevelAccess, "EXACT_NATIVE_ENTITY_MANAGER_OBSERVER");
        var manager = ((P11L1TrackingBoundaryProbe.LevelAccess) level).p11$l1EntityManager();
        if (!level.hasChunkAt(stoppedPosition)
                || !manager.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(stoppedPosition).toLong())) {
            readTicks = 0; return Optional.empty();
        }
        require(logical(actor).equals(expected), "LOADED_DEFINITION_EQUIPMENT_AND_NATIVE_REWARD_PRESERVED");
        if (++readTicks < 20) { return Optional.empty(); }
        physical(expected, true); readComplete = true;
        return Optional.of(Map.of("status", "SAME_WORLD_NEW_SERVER_READBACK_WITHOUT_RUNTIME_RESTORATION",
                "actualLoadedChunkObservationTicks", readTicks, "definitionSha256", definitionHash(),
                "equippedReference", reference.toString(), "p11_l1", expected.get("scoreboard").getAsInt(),
                "work", 0, "instances", 0, "pendingEvents", 0, "activePermits", 0, "noStarterOrCastClaimFromHelper", true));
    }

    private static void emptyRuntime(boolean neverAccepted) {
        require(actualRuntime != null && actualSlot != null && actualSlot.state == ServerSlot.State.RUNNING
                && actualSlot.queue.isEmpty() && actualSlot.eventIndex.isEmpty() && actualSlot.instances.isEmpty()
                && actualSlot.activeProjectileContinuations.isEmpty() && actualSlot.leases.isEmpty()
                && actualSlot.committedPending == 0 && actualSlot.reservedPending == 0 && actualSlot.deferredCount == 0
                && actualSlot.currentReservationCount == 0 && actualSlot.currentReservationOwner == null
                && actualSlot.currentEvent == null && !actualSlot.dispatching
                && (!neverAccepted || actualSlot.eventSequenceHighWater == 0 && actualSlot.skillInstanceSequenceHighWater == 0),
                "ACTUAL_RUNTIME_NOT_EMPTY");
    }
    private static JsonObject logical(ServerPlayer actor) {
        require(equipped(actor).equals(reference), "EXACT_EQUIPPED_REFERENCE");
        var objective = actualServer.getScoreboard().getObjective("p11_l1");
        var score = objective == null ? null : actualServer.getScoreboard().getPlayerScoreInfo(actor, objective);
        var advancement = actualServer.getAdvancements().get(ResourceLocation.parse(REWARD));
        require(score != null && advancement != null && actor.getAdvancements().getOrStartProgress(advancement).isDone(), "ACTUAL_SCORE_AND_PA");
        var value = new JsonObject(); value.addProperty("definitionSha256", definitionHash());
        value.addProperty("xp", actor.totalExperience); value.addProperty("bread", actor.getInventory().countItem(Items.BREAD));
        value.addProperty("recipe", actor.getRecipeBook().contains(ResourceLocation.withDefaultNamespace("bread")));
        value.addProperty("advancement", true); value.addProperty("scoreboard", score.value());
        value.addProperty("chicken", actor.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.CHICKEN)));
        value.addProperty("mobKills", actor.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS))); return value;
    }
    private static SkillReference equipped(ServerPlayer actor) {
        var result = attachments.equippedAt(actor, 0);
        require(result instanceof PlayerSkillAttachmentService.Available<?> available
                && available.value() instanceof Optional<?> option && option.isPresent(), "EQUIPPED_AVAILABLE");
        return ((PlayerSkillAttachmentService.Available<Optional<SkillReference>>) result).value().orElseThrow();
    }
    private static String definitionHash() {
        var found = store.find(actualServer, reference);
        require(found instanceof SkillSubsystemResult.Available<?> available
                && available.value() instanceof Optional<?> option && option.isPresent(), "EXACT_DOCUMENT_AVAILABLE");
        var document = ((SkillSubsystemResult.Available<Optional<SkillDocument>>) found).value().orElseThrow();
        require(document.skillId().equals(reference.skillId()) && document.revision().equals(reference.revision()), "DOCUMENT_ROUTE");
        var encoded = SkillDocumentStorePersistenceFacade.encodeCurrent(document);
        require(encoded instanceof SkillDocumentStorePersistenceFacade.Encoded, "FORMAL_DOCUMENT_ENCODING");
        return P11C4aEvidence.hash(((SkillDocumentStorePersistenceFacade.Encoded) encoded).document().copyBytes());
    }
    private static void physical(JsonObject values, boolean scoreboardRequired) throws IOException {
        var player = nbt(world.resolve("playerdata").resolve(ownerId + ".dat"));
        require(player.hasUUID("UUID") && player.getUUID("UUID").equals(ownerId) && player.getInt("XpTotal") == values.get("xp").getAsInt(), "PHYSICAL_PLAYER_ID_XP");
        var slots = player.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).getCompound("gramarye:player_skills").getList("equipped_slots", Tag.TAG_COMPOUND);
        int matches = 0;
        for (Tag tag : slots) { var entry = (CompoundTag) tag; if (entry.getInt("slot") == 0) {
            require(SkillReference.CODEC.parse(NbtOps.INSTANCE, entry.get("reference")).result().filter(reference::equals).isPresent(), "PHYSICAL_EQUIPPED_REFERENCE"); matches++;
        }}
        require(matches == 1, "ONE_PHYSICAL_SLOT_ZERO"); int bread = 0;
        for (Tag tag : player.getList("Inventory", Tag.TAG_COMPOUND)) {
            var item = (CompoundTag) tag; if (item.getString("id").equals("minecraft:bread")) { bread += item.getInt("count"); }
        }
        require(bread == values.get("bread").getAsInt() && player.getCompound("recipeBook").getList("recipes", Tag.TAG_STRING).stream()
                .anyMatch(tag -> tag.getAsString().equals("minecraft:bread")) == values.get("recipe").getAsBoolean(), "PHYSICAL_INVENTORY_RECIPE");
        var pa = readJson(world.resolve("advancements").resolve(ownerId + ".json"));
        require(pa.has(REWARD) && pa.getAsJsonObject(REWARD).get("done").getAsBoolean(), "PHYSICAL_PA");
        var stats = readJson(world.resolve("stats").resolve(ownerId + ".json")).getAsJsonObject("stats");
        require(stat(stats, "minecraft:killed", "minecraft:chicken") == values.get("chicken").getAsInt()
                && stat(stats, "minecraft:custom", "minecraft:mob_kills") == values.get("mobKills").getAsInt(), "PHYSICAL_STATS");
        if (scoreboardRequired) {
            int found = 0;
            for (Tag tag : nbt(world.resolve("data/scoreboard.dat")).getCompound("data").getList("PlayerScores", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) tag;
                if (entry.getString("Objective").equals("p11_l1") && hashText(entry.getString("Name")).equals(scoreboardHolderHash)) {
                    require(entry.getInt("Score") == values.get("scoreboard").getAsInt(), "PHYSICAL_SCORE_VALUE"); found++;
                }
            }
            require(found == 1, "ONE_ORIGINAL_PHYSICAL_SCORE");
        }
    }
    private static JsonObject physicalHashes() throws IOException {
        var hashes = new JsonObject();
        for (String leaf : List.of("data/gramarye_skill_definitions.dat", "data/scoreboard.dat", "playerdata/" + ownerId + ".dat",
                "advancements/" + ownerId + ".json", "stats/" + ownerId + ".json")) {
            Path path = world.resolve(leaf); regular(path, FILE_BOUND); hashes.addProperty(leaf, P11C4aEvidence.hash(Files.readAllBytes(path)));
        }
        return hashes;
    }
    private static void noProjectiles() {
        int total = 0;
        for (var level : actualServer.getAllLevels()) { for (var entity : level.getAllEntities()) {
            require(++total <= 8192, "OWNED_WORLD_ENTITY_OBSERVATION_BOUND"); require(!(entity instanceof P9StarterProjectile), "P9_ENTITY_RESTORED");
        }}
    }
    private static long roots(ServerPlayer actor, String kind) { return P11NativeStorageBoundary.diagnostics(actualServer, actor.getUUID())
            .nativeResponsibilities().roots().stream().filter(value -> value.kind().equals(kind)).mapToLong(value -> value.count()).sum(); }
    private static long rootsForOwner(String kind) { return P11NativeStorageBoundary.diagnostics(actualServer, ownerId)
            .nativeResponsibilities().roots().stream().filter(value -> value.kind().equals(kind)).mapToLong(value -> value.count()).sum(); }
    private static int stat(JsonObject root, String family, String key) {
        var values = root.getAsJsonObject(family); return values != null && values.has(key) ? values.get(key).getAsInt() : 0;
    }
    private static CompoundTag nbt(Path path) throws IOException { regular(path, FILE_BOUND); return NbtIo.readCompressed(path, NbtAccounter.create(FILE_BOUND)); }
    private static JsonObject readJson(Path path) throws IOException { regular(path, 4L * 1024 * 1024); return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    private static void regular(Path path, long bound) throws IOException {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
                && path.toAbsolutePath().normalize().equals(path.toRealPath()) && Files.size(path) > 0 && Files.size(path) <= bound, "OWNED_REGULAR_BOUNDED_FILE");
    }
    private static Path exactWorld(MinecraftServer server) throws IOException {
        var path = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(path.getFileName().toString().equals("p11-online-world") && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path) && path.toRealPath().equals(path), "EXACT_NATIVE_WORLD_PATH"); return path;
    }
    private static String hashText(String text) { return P11C4aEvidence.hash(text.getBytes(StandardCharsets.UTF_8)); }
    private static void current(ServerPlayer actor) {
        require(actualServer != null && actualServer.isSameThread() && actualServer.isRunning() && !actualServer.isStopped()
                && actor != null && actor.getServer() == actualServer && !actor.isFakePlayer() && !actor.isRemoved() && actor.isAlive()
                && actor.connection != null && actor.connection.getConnection().isConnected()
                && actor.connection.getConnection().getPacketListener() == actor.connection
                && actualServer.getPlayerList().getPlayer(actor.getUUID()) == actor, "EXACT_CURRENT_ACTOR");
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "L1_RESTART_" + code); }
}
