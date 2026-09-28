package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;

/** Excluded engineering probes: real native entries, real authenticated actor, no fabricated receipt. */
final class P11NativeGuardProbe {
    // Engineering evidence-reader limit only. It is not a production player-data limit.
    private static final long DIAGNOSTIC_FILE_BYTES = 32L * 1024 * 1024;

    private P11NativeGuardProbe() {}

    enum Probe { OUTSIDE_LOAD, FOREIGN_PLAYER_STORAGE, FOREIGN_WORLD_STORAGE,
        DISTINCT_STATS, DISTINCT_ADVANCEMENTS, CROSS_KIND_STATS,
        STATS_TO_PLAYER_PRIMARY, STATS_TO_PLAYER_BACKUP,
        STATS_TO_HOST_PRIMARY, STATS_TO_HOST_BACKUP, WRONG_THREAD_STATS }
    enum Worker { NOT_STARTED, TERMINAL, STILL_ALIVE }
    record Observation(Probe probe, String outcome, boolean filesUnchanged, boolean exactOwnersUnchanged) {}
    record Report(boolean passed, Worker worker, List<Observation> observations, String failure) {
        Report { observations = List.copyOf(observations); }
        @Override public String toString() {
            var text = new StringBuilder("P11-NATIVE-GUARD-PROBE-V1\n"
                    + "scope=MANAGED_NATIVE_ENTRY_REJECTION\n"
                    + "fixtureActor=false\nmanualBoundaryCallbacks=false\n");
            text.append("worker=").append(worker).append('\n');
            for (var observation : observations) { text.append(observation).append('\n'); }
            text.append("failure=").append(failure).append('\n');
            return text.append("RESULT=").append(passed ? "PASS" : "FAIL").append('\n').toString();
        }
    }

    static Report run(MinecraftServer server, ServerPlayer actor, Path freshOwnedDirectory) {
        var observations = new ArrayList<Observation>();
        var worker = new AtomicReference<>(Worker.NOT_STARTED);
        try {
            require(server.isSameThread() && actor.getServer() == server && !actor.isFakePlayer()
                    && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                    && actor.connection != null && actor.connection.getConnection().isConnected(),
                    "probe requires the exact normal authenticated server actor");
            var initial = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
            require(initial.active() && initial.bodyComplete() && !initial.candidatePresent()
                    && "NONE".equals(initial.sourceFault()), "probe source is not healthy and managed");
            require(freshOwnedDirectory.isAbsolute() && freshOwnedDirectory.equals(freshOwnedDirectory.normalize())
                    && freshOwnedDirectory.getParent() != null
                    && Files.isDirectory(freshOwnedDirectory.getParent(), LinkOption.NOFOLLOW_LINKS),
                    "probe requires a fresh absolute child of its owned evidence directory");
            Files.createDirectory(freshOwnedDirectory);
            var stats = actor.getStats();
            var advancements = actor.getAdvancements();
            var protectedFiles = protectedFiles(server, actor);

            observe(Probe.OUTSIDE_LOAD, server, actor, stats, advancements, protectedFiles,
                    freshOwnedDirectory, observations, () -> server.getPlayerList().load(actor));

            var foreignRoot = freshOwnedDirectory.resolve("foreign-worlds");
            Files.createDirectory(foreignRoot);
            var foreignSource = LevelStorageSource.createDefault(foreignRoot);
            try (var foreign = foreignSource.createAccess("owned-foreign-world")) {
                var storage = new PlayerDataStorage(foreign, server.getFixerUpper());
                var playerFiles = foreign.getLevelPath(LevelResource.PLAYER_DATA_DIR);
                observe(Probe.FOREIGN_PLAYER_STORAGE, server, actor, stats, advancements, protectedFiles,
                        freshOwnedDirectory, observations, () -> storage.save(actor));
                try (var files = Files.list(playerFiles)) {
                    require(files.findAny().isEmpty(), "foreign player storage created a native file");
                }
                observe(Probe.FOREIGN_WORLD_STORAGE, server, actor, stats, advancements, protectedFiles,
                        freshOwnedDirectory, observations,
                        () -> foreign.saveDataTag(server.registryAccess(), server.getWorldData(), null));
                require(Files.notExists(foreign.getLevelPath(LevelResource.ROOT).resolve("level.dat"))
                        && Files.notExists(foreign.getLevelPath(LevelResource.ROOT).resolve("level.dat_old")),
                        "foreign world storage published active world data");
                try (var files = Files.list(foreign.getLevelPath(LevelResource.ROOT))) {
                    require(files.noneMatch(path -> path.getFileName().toString().startsWith("level")),
                            "foreign world storage created level material before rejection");
                }
            }

            var statsPath = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(actor.getUUID() + ".json");
            var counterpartStats = new ServerStatsCounter(server, statsPath.toFile());
            require(counterpartStats != stats, "counterpart stats unexpectedly canonical");
            observe(Probe.DISTINCT_STATS, server, actor, stats, advancements, protectedFiles,
                    freshOwnedDirectory, observations, counterpartStats::save);

            // The native PA constructor invokes automatic rewards for empty criteria. Do not
            // create a second listener owner unless this owned manager proves that path absent.
            require(server.getAdvancements().getAllAdvancements().stream()
                    .noneMatch(holder -> holder.value().criteria().isEmpty()),
                    "distinct PA probe unsupported: automatic reward path would have effects");
            var advancementPath = server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR)
                    .resolve(actor.getUUID() + ".json");
            var counterpartAdvancements = new PlayerAdvancements(server.getFixerUpper(),
                    server.getPlayerList(), server.getAdvancements(), advancementPath, actor);
            try {
                require(counterpartAdvancements != advancements, "counterpart advancements unexpectedly canonical");
                observe(Probe.DISTINCT_ADVANCEMENTS, server, actor, stats, advancements, protectedFiles,
                        freshOwnedDirectory, observations, counterpartAdvancements::save);
            } finally {
                // Original native cleanup affects this distinct instance's listeners only.
                counterpartAdvancements.stopListening();
            }

            counterCollision(Probe.CROSS_KIND_STATS, advancementPath, server, actor, stats,
                    advancements, protectedFiles, freshOwnedDirectory, observations);
            var playerDirectory = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            counterCollision(Probe.STATS_TO_PLAYER_PRIMARY,
                    playerDirectory.resolve(actor.getUUID() + ".dat"), server, actor, stats,
                    advancements, protectedFiles, freshOwnedDirectory, observations);
            counterCollision(Probe.STATS_TO_PLAYER_BACKUP,
                    playerDirectory.resolve(actor.getUUID() + ".dat_old"), server, actor, stats,
                    advancements, protectedFiles, freshOwnedDirectory, observations);
            require(server.isSingleplayerOwner(actor.getGameProfile()),
                    "host NBT collision probes require this exact managed integrated host");
            var worldDirectory = server.getWorldPath(LevelResource.ROOT);
            counterCollision(Probe.STATS_TO_HOST_PRIMARY, worldDirectory.resolve("level.dat"),
                    server, actor, stats, advancements, protectedFiles, freshOwnedDirectory, observations);
            counterCollision(Probe.STATS_TO_HOST_BACKUP, worldDirectory.resolve("level.dat_old"),
                    server, actor, stats, advancements, protectedFiles, freshOwnedDirectory, observations);

            observeOutcome(Probe.WRONG_THREAD_STATS, server, actor, stats, advancements, protectedFiles,
                    freshOwnedDirectory, observations, () -> {
                        var outcome = new AtomicReference<String>();
                        var terminal = new AtomicBoolean();
                        var thread = new Thread(() -> {
                            try { outcome.set(rejectedOutcome(stats::save)); }
                            finally { terminal.set(true); }
                        }, "p11-native-guard-probe");
                        thread.setDaemon(true);
                        worker.set(Worker.STILL_ALIVE);
                        thread.start();
                        try { thread.join(2_000); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("owned guard probe join interrupted", interrupted);
                        }
                        require(!thread.isAlive() && terminal.get(),
                                "owned guard probe did not reach terminality within 2 seconds");
                        worker.set(Worker.TERMINAL);
                        return outcome.get();
                    });

            require(observations.size() == Probe.values().length
                    && observations.stream().map(Observation::probe).distinct().count() == Probe.values().length,
                    "missing or duplicate managed guard case");
            var after = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
            require(after.active() && after.bodyComplete() && !after.candidatePresent()
                    && after.sourceEpoch() == initial.sourceEpoch()
                    && after.sourceVersion() == initial.sourceVersion()
                    && after.equippedSlot0().equals(initial.equippedSlot0()),
                    "guard probe changed the managed body or source identity");
            return new Report(true, worker.get(), observations, "NONE");
        } catch (IOException | RuntimeException failure) {
            String detail = failure.getClass().getName() + ": " + failure.getMessage();
            return new Report(false, worker.get(), observations,
                    detail.length() > 512 ? detail.substring(0, 512) : detail);
        }
    }

    private static void counterCollision(Probe probe, Path target, MinecraftServer server,
            ServerPlayer actor, ServerStatsCounter stats, PlayerAdvancements advancements,
            List<Path> files, Path evidence, List<Observation> observations) throws IOException {
        // The real constructor may reject non-JSON input internally; that does not authorize
        // its void save to overwrite another native owner. No canonical map entry is changed.
        var counterpart = new ServerStatsCounter(server, target.toFile());
        require(counterpart != stats, "collision counter unexpectedly canonical");
        observe(probe, server, actor, stats, advancements, files, evidence, observations, counterpart::save);
    }

    private static void observe(Probe probe, MinecraftServer server, ServerPlayer actor,
            ServerStatsCounter stats, PlayerAdvancements advancements, List<Path> files,
            Path evidence, List<Observation> observations, Runnable action) throws IOException {
        observeOutcome(probe, server, actor, stats, advancements, files, evidence,
                observations, () -> rejectedOutcome(action));
    }

    private static void observeOutcome(Probe probe, MinecraftServer server, ServerPlayer actor,
            ServerStatsCounter stats, PlayerAdvancements advancements, List<Path> files,
            Path evidence, List<Observation> observations, Supplier<String> action) throws IOException {
        Path directory = evidence.resolve(probe.name().toLowerCase(java.util.Locale.ROOT));
        Files.createDirectory(directory);
        var before = snapshot(files, directory, "before");
        String outcome = action.get();
        var after = snapshot(files, directory, "after");
        boolean unchanged = sameFiles(before, after);
        boolean owners = server.getPlayerList().getPlayer(actor.getUUID()) == actor
                && actor.getStats() == stats && actor.getAdvancements() == advancements;
        observations.add(new Observation(probe, outcome, unchanged, owners));
        require(unchanged && owners, "native guard probe changed protected files/owners: " + probe);
        require(P11QualifiedSourceOwner.SourceUnavailable.class.getName().equals(outcome),
                "native entry did not reject with the exact source guard: " + probe + " / " + outcome);
    }

    private static String rejectedOutcome(Runnable action) {
        try { action.run(); return "RETURNED_NORMALLY"; }
        catch (RuntimeException rejected) { return rejected.getClass().getName(); }
    }

    private static List<Path> protectedFiles(MinecraftServer server, ServerPlayer actor) {
        var player = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        var world = server.getWorldPath(LevelResource.ROOT);
        return List.of(player.resolve(actor.getUUID() + ".dat"), player.resolve(actor.getUUID() + ".dat_old"),
                server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(actor.getUUID() + ".json"),
                server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(actor.getUUID() + ".json"),
                world.resolve("level.dat"), world.resolve("level.dat_old"));
    }

    private static List<byte[]> snapshot(List<Path> files, Path evidence, String phase) throws IOException {
        var values = new ArrayList<byte[]>();
        for (int index = 0; index < files.size(); index++) {
            var path = files.get(index);
            if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.writeString(evidence.resolve(phase + "-" + index + ".absent"),
                        "ABSENT\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                values.add(null);
            } else {
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && Files.size(path) <= DIAGNOSTIC_FILE_BYTES,
                        "protected diagnostic input is nonregular or exceeds the evidence reader budget");
                var bytes = Files.readAllBytes(path);
                Files.write(evidence.resolve(phase + "-" + index + ".bin"), bytes,
                        StandardOpenOption.CREATE_NEW);
                values.add(bytes);
            }
        }
        return values;
    }

    private static boolean sameFiles(List<byte[]> before, List<byte[]> after) {
        if (before.size() != after.size()) { return false; }
        for (int index = 0; index < before.size(); index++) {
            if (!Arrays.equals(before.get(index), after.get(index))) { return false; }
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
