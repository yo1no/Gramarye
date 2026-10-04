package com.yo1no.gramarye;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.LevelResource;

/** Excluded, exact isolated statistics-file obstruction; no replacement writer or source repair. */
public final class P11L1StatsMemoryProbe {
    public static final String FIXTURE_SHA256 = "04c94e6c2913d0ad20a200e03c55787c740275c9a70fcc94af5de2286d4c52a3";
    private static final long MAX_JSON = 1024 * 1024;
    private static Run active;
    private P11L1StatsMemoryProbe() {}

    /** Called once while the authenticated old actor is normal PLAY, before original R. */
    public static void arm(MinecraftServer server, ServerPlayer actor, Path output) throws IOException {
        require("l1-stats-write-fault-memory".equals(System.getProperty("gramarye.p11.online.case", ""))
                && active == null && server != null && server.isSameThread() && current(server, actor), "EXACT_CASE_AND_ACTOR");
        require(FIXTURE_SHA256.equals(P11C4aLoadedConfiguration.hash()), "EXACT_APPROVED_LOADED_MEMORY_FIXTURE");
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.nativeRecipient(actor);
        require(body != null && source.canCopy(body) && source.canSerialize(body)
                && body.fault == P11QualifiedSourceOwner.Fault.NONE && !body.account.cleanupUnknown
                && body.stats instanceof P11IndependentMaterialWitness witness && witness.p11$materialComplete(),
                "REAL_COMPLETE_CANONICAL_STATS");
        require(output.toRealPath().equals(P11C4aEvidence.root().resolve("server")), "OWNED_EVIDENCE_CHILD");
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(world)
                && world.toRealPath().equals(world), "EXACT_NATIVE_WORLD_ROOT");
        Path directory = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toAbsolutePath().normalize();
        boolean directoryCreated = prepareStatsDirectory(world, directory);
        var run = new Run(server, actor, output, source, body, directory.resolve(actor.getUUID() + ".json"), directoryCreated);
        active = run;
        try {
            if (Files.exists(run.path, LinkOption.NOFOLLOW_LINKS)) {
                requireRegular(run.path); run.originalHash = P11C4aEvidence.hash(Files.readAllBytes(run.path));
                Files.move(run.path, run.backup); run.displaced = true;
            }
            Files.createDirectory(run.path); run.blockerCreated = true;
            run.blockerKey = attributes(run.path).fileKey();
            require(run.blockerKey != null, "OWNED_BLOCKER_FILE_IDENTITY_AVAILABLE");
            requireBlocker(run);
        } catch (IOException | RuntimeException | Error primary) {
            restoreAfterFailure(primary); throw primary;
        }
    }
    public static void accepted(ServerPlayer actor, Object value, Object result) {
        var run = active; if (run == null || actor != run.actor) { return; }
        require(run.server.isSameThread() && run.instance == null && value instanceof ServerSlot.InstanceState
                && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly, "ONE_ACTUAL_R_ACCEPTANCE");
        var instance = (ServerSlot.InstanceState) value;
        require(instance.work != null && !instance.lease.pin.isClosed() && instance.hasP9AuthenticatedActorWitness(actor)
                && instance.id.equals(((RuntimeAdmissionResult.AcceptedMemoryOnly) result).eventToken().skillInstanceId()),
                "EXACT_ACCEPTED_W"); run.instance = instance;
    }
    /** After the real sole-writer accounting returns; never changes its outcome or receipt. */
    public static void writerFinished(Object owner, Object body, Object receipt, boolean success) {
        var run = active;
        if (run == null || owner != run.source || !(body instanceof P11QualifiedSourceOwner.Body exact)
                || exact.account != run.body.account || success) { return; }
        if (!(receipt instanceof P11ReceiptLedger.PhysicalWriterReceipt physical)
                || physical.kind() != P11ReceiptLedger.WriterKind.STATISTICS || run.restored
                || !run.server.isSameThread() || physical.source().account() != exact.source.account()
                || ++run.failedWriterReturns > 16) { run.observerFailed = true; }
    }
    public static int expectedSourceFailures() {
        var run = exact(); require(run.saveObserved && !run.observerFailed && run.failedWriterReturns > 0,
                "ACTUAL_SELECTED_FAILURE_ACCOUNTING"); return run.failedWriterReturns;
    }
    /** AFTER original whole onDisconnect/handleDisconnection normal return, never its inner event. */
    public static Map<String, Object> normalLogoutReturned() throws IOException {
        var run = exact(); require(!run.logoutObserved && run.instance != null && run.instance.work != null
                && run.instance.logoutState == SkillRuntimeService.LogoutState.COMPLETE && run.instance.logoutScope == null
                && run.actor.isRemoved() && !run.actor.connection.getConnection().isConnected()
                && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == null, "WHOLE_NORMAL_LOGOUT_WITH_WORK");
        requireMaterial(run, run.body); requireBlocker(run);
        var writer = statsWriter(run);
        require(!run.observerFailed && run.failedWriterReturns > 0
                && writer.attempt() > run.beforeAttempt && writer.dirty() && "FAILED".equals(writer.terminal())
                && "SUCCEEDED".equals(writer.encode()) && "UNKNOWN".equals(writer.write())
                && "UNKNOWN".equals(writer.close()), "ORIGINAL_SWALLOWED_IO_FAILURE_RECEIPT");
        run.failedAttempt = writer.attempt(); run.logoutObserved = true;
        return facts(run, "ORIGINAL_NORMAL_LOGOUT_WITH_STATS_IO_FAILURE_NOT_CLEANUP_FAULT");
    }
    /** After actual natural chicken work returns/W closes, before issuing the real reconnect cue. */
    public static Map<String, Object> workTerminal() throws IOException {
        var run = exact(); require(run.logoutObserved && !run.workObserved && run.instance.work == null
                && run.instance.lease.pin.isClosed() && run.source.body(run.actor) == run.body
                && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == null, "ACTUAL_WORK_TERMINAL_BEFORE_CLIENT_RECONNECT");
        requireMaterial(run, run.body); requireBlocker(run);
        require(run.body.stats.getValue(Stats.ENTITY_KILLED.get(EntityType.CHICKEN)) == run.chickenBefore + 1
                && run.body.stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)) == run.mobBefore + 1
                && run.actor.totalExperience == run.xpBefore + 10 && statsWriter(run).dirty(), "LATEST_NATIVE_MEMORY_DESPITE_PHYSICAL_FAULT");
        run.latestXp = run.actor.totalExperience; run.latestVersion = run.body.source.version(); run.workObserved = true;
        return facts(run, "ACTUAL_WORK_MUTATED_QUALIFIED_LATEST_MEMORY_PENDING_PHYSICAL_WRITE");
    }
    /** Real original factory/placement/P7 yields B. The blocked canonical file must not be reloaded. */
    public static Map<String, Object> reconnected(ServerPlayer replacement) throws IOException {
        var run = exact(); require(run.workObserved && !run.reconnectObserved && replacement != run.actor
                && replacement.getUUID().equals(run.actor.getUUID()) && current(run.server, replacement)
                && replacement.connection.getConnection() != run.actor.connection.getConnection(), "ACTUAL_NEW_AUTHENTICATED_CONNECTION_BODY");
        var body = run.source.nativeRecipient(replacement);
        requireMaterial(run, body); requireBlocker(run);
        require(body.source.epoch() > run.initialEpoch && body.inputKind == P11QualifiedSourceOwner.InputKind.MEMORY
                && body.stats == run.body.stats && body.advancements == run.body.advancements
                && replacement.totalExperience == run.latestXp
                && body.stats.getValue(Stats.ENTITY_KILLED.get(EntityType.CHICKEN)) == run.chickenBefore + 1
                && body.stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)) == run.mobBefore + 1
                && statsWriter(run).dirty(), "LATEST_MEMORY_HANDOFF_WITH_SAME_CANONICAL_JSON_NO_REPLAY");
        run.replacement = replacement; run.replacementBody = body; run.reconnectObserved = true;
        return facts(run, "ORIGINAL_MEMORY_RECONNECT_BEFORE_OBSTRUCTION_REMOVAL");
    }
    /** Restore only our exact still-empty file-identity directory. Parent then requests normal native save. */
    public static void restoreBeforeOriginalSave() throws IOException {
        var run = exact(); require(run.reconnectObserved && !run.restored, "RESTORE_AFTER_REAL_MEMORY_HANDOFF"); restore(run);
    }
    public static Map<String, Object> originalSaveReturned(ServerPlayer replacement) throws IOException {
        var run = exact(); require(run.restored && run.reconnectObserved && replacement == run.replacement
                && current(run.server, replacement), "NORMAL_LIVE_SAVE_RECIPIENT");
        requireMaterial(run, run.replacementBody);
        var writer = statsWriter(run);
        require(!run.observerFailed && run.failedWriterReturns > 0
                && writer.attempt() > run.failedAttempt && !writer.dirty() && "COMPLETED".equals(writer.terminal())
                && "SUCCEEDED".equals(writer.encode()) && "SUCCEEDED".equals(writer.write())
                && "SUCCEEDED".equals(writer.close()), "FRESH_ORIGINAL_STATS_PHYSICAL_SUCCESS");
        requireRegular(run.path);
        JsonObject statistics = JsonParser.parseString(Files.readString(run.path, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("stats");
        require(statistic(statistics, "minecraft:killed", "minecraft:chicken") == run.chickenBefore + 1
                && statistic(statistics, "minecraft:custom", "minecraft:mob_kills") == run.mobBefore + 1,
                "LATEST_ORIGINAL_STATS_JSON_READBACK");
        Path player = run.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(replacement.getUUID() + ".dat");
        require(Files.isRegularFile(player, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(player)
                && Files.size(player) <= 32L * 1024 * 1024, "OWNED_NATIVE_PLAYER_FILE");
        var saved = NbtIo.readCompressed(player, NbtAccounter.create(32L * 1024 * 1024));
        require(saved.getInt("XpTotal") == run.latestXp && replacement.totalExperience == run.latestXp,
                "LATEST_ORIGINAL_PLAYER_DATA_READBACK");
        run.saveObserved = true; return facts(run, "NAMED_STATS_IO_MEMORY_RECOVERY_NOT_FULL_L1_OR_STOP_ACCEPTANCE");
    }
    /** Failure path cleanup only; never saves, repairs a source, or changes an escaping primary. */
    public static void restoreAfterFailure(Throwable primary) throws IOException {
        var run = active; if (run == null || run.restored) { return; }
        try { restore(run); }
        catch (IOException | RuntimeException | Error secondary) {
            run.restoreFailed = true;
            if (primary == null) { throw secondary; }
            try { if (primary != secondary) { primary.addSuppressed(secondary); } }
            catch (RuntimeException | Error ignoredDiagnosticFailure) { /* same primary remains the caller's */ }
        }
    }
    public static void release() {
        var run = active; require(run == null || run.restored && !run.restoreFailed, "DO_NOT_FORGET_OWNED_OBSTRUCTION"); active = null;
    }
    private static boolean prepareStatsDirectory(Path world, Path directory) throws IOException {
        require(Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(world)
                && world.toRealPath().equals(world) && directory.equals(world.resolve("stats")), "EXACT_NATIVE_STATS_PARENT");
        // Native getPlayerStats constructs a counter without creating its parent; the first save may be later.
        boolean created = !Files.exists(directory, LinkOption.NOFOLLOW_LINKS);
        if (created) { Files.createDirectory(directory); }
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(directory)
                && directory.toRealPath().equals(directory), "EXACT_NATIVE_STATS_DIRECTORY");
        return created;
    }
    private static void restore(Run run) throws IOException {
        if (run.blockerCreated) {
            requireBlocker(run); Files.delete(run.path); run.blockerCreated = false;
        }
        if (run.displaced) {
            requireRegular(run.backup);
            require(run.originalHash.equals(P11C4aEvidence.hash(Files.readAllBytes(run.backup)))
                    && !Files.exists(run.path, LinkOption.NOFOLLOW_LINKS), "EXACT_BACKUP_NO_OVERWRITE");
            Files.copy(run.backup, run.path);
            require(run.originalHash.equals(P11C4aEvidence.hash(Files.readAllBytes(run.path))), "RESTORED_ORIGINAL_BYTES");
        }
        run.restored = true;
    }
    private static void requireBlocker(Run run) throws IOException {
        require(run.blockerCreated && Files.isDirectory(run.path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(run.path)
                && run.blockerKey != null && run.blockerKey.equals(attributes(run.path).fileKey()), "EXACT_OWNED_EMPTY_BLOCKER");
        try (var contents = Files.list(run.path)) { require(contents.findAny().isEmpty(), "BLOCKER_MUST_REMAIN_EMPTY"); }
        if (run.displaced) { requireRegular(run.backup); require(run.originalHash.equals(P11C4aEvidence.hash(Files.readAllBytes(run.backup))), "BACKUP_BYTES_UNCHANGED"); }
    }
    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
    private static void requireRegular(Path file) throws IOException {
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)
                && Files.size(file) <= MAX_JSON, "OWNED_BOUNDED_REGULAR_STATS_FILE");
    }
    private static int statistic(JsonObject statistics, String category, String key) {
        var values = statistics.getAsJsonObject(category); return values == null || !values.has(key) ? 0 : values.get(key).getAsInt();
    }
    private static void requireMaterial(Run run, P11QualifiedSourceOwner.Body body) {
        require(body != null && body.account == run.body.account && run.source.canCopy(body) && run.source.canSerialize(body)
                && body.fault == P11QualifiedSourceOwner.Fault.NONE && !body.account.cleanupUnknown
                && body.stats == run.body.stats && body.advancements == run.body.advancements, "PHYSICAL_FAILURE_NOT_MATERIAL_FAILURE");
    }
    private static P11QualifiedSourceOwner.WriterDiagnostic statsWriter(Run run) {
        return run.source.diagnostics(run.actor.getUUID()).writers().stream().filter(w -> "STATISTICS".equals(w.kind())).findFirst().orElseThrow();
    }
    private static Map<String, Object> facts(Run run, String status) {
        var result = new LinkedHashMap<String, Object>(); result.put("status", status);
        result.put("fixtureSha256", FIXTURE_SHA256); result.put("sourceBeforeEpoch", run.initialEpoch);
        result.put("sourceAtWorkTerminalVersion", run.latestVersion); result.put("originalStatsFileExisted", run.displaced);
        result.put("statsDirectoryCreatedForFixture", run.directoryCreated);
        result.put("originalBackupSha256", run.originalHash); result.put("failedOriginalStatsAttempt", run.failedAttempt);
        result.put("normalLogoutObserved", run.logoutObserved); result.put("workTerminalObserved", run.workObserved);
        result.put("actualMemoryReconnect", run.reconnectObserved); result.put("ownObstructionRestored", run.restored);
        result.put("restorationFailed", run.restoreFailed); result.put("freshOriginalPhysicalReadback", run.saveObserved);
        result.put("actualSelectedFailedWriterReturns", run.failedWriterReturns); result.put("observerFailed", run.observerFailed);
        result.put("source", P11C4aEvidence.sourceObservation(run.source.diagnostics(run.actor.getUUID())));
        return java.util.Collections.unmodifiableMap(result);
    }
    private static Run exact() { var run = active; require(run != null && run.server.isSameThread(), "SAME_NATIVE_MAIN_OWNER"); return run; }
    private static boolean current(MinecraftServer server, ServerPlayer actor) {
        return actor != null && !actor.isFakePlayer() && actor.getServer() == server && !actor.isRemoved()
                && actor.connection != null && actor.connection.player == actor && actor.connection.getConnection().isConnected()
                && actor.connection.getConnection().getPacketListener() == actor.connection
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor;
    }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, "L1_STATS_MEMORY_" + code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor; final P11QualifiedSourceOwner source;
        final P11QualifiedSourceOwner.Body body; final Path path, backup; final boolean directoryCreated;
        final long initialEpoch, beforeAttempt; final int chickenBefore, mobBefore, xpBefore;
        ServerSlot.InstanceState instance; ServerPlayer replacement; P11QualifiedSourceOwner.Body replacementBody;
        Object blockerKey; String originalHash = "ABSENT"; long failedAttempt, latestVersion; int latestXp, failedWriterReturns;
        boolean displaced, blockerCreated, restored, restoreFailed, logoutObserved, workObserved, reconnectObserved, saveObserved, observerFailed;
        Run(MinecraftServer server, ServerPlayer actor, Path output, P11QualifiedSourceOwner source,
                P11QualifiedSourceOwner.Body body, Path path, boolean directoryCreated) {
            this.server = server; this.actor = actor; this.source = source; this.body = body; this.path = path;
            this.directoryCreated = directoryCreated;
            backup = output.resolve("stats-before-owned-io-fault.json"); initialEpoch = body.source.epoch();
            beforeAttempt = statsWriter(this).attempt(); chickenBefore = body.stats.getValue(Stats.ENTITY_KILLED.get(EntityType.CHICKEN));
            mobBefore = body.stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)); xpBefore = actor.totalExperience;
        }
    }
}
