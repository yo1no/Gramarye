package com.yo1no.gramarye;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Excluded engineering-only native material mismatch; never manufactures source or writer facts. */
final class P11NativeMaterialProbe {
    // Evidence-reader/output limit only, not a production player or cache limit.
    private static final int EVIDENCE_BYTES = 32 * 1024 * 1024;
    private static final List<String> WHOLE = List.of("PLAYER_DATA", "LEVEL_PLAYER", "CACHE");
    private static final List<String> INDEPENDENT = List.of("STATISTICS", "ADVANCEMENTS");
    private static final ResourceLocation MANA =
            ResourceLocation.fromNamespaceAndPath("gramarye", "player_mana");

    private P11NativeMaterialProbe() {}

    record Observation(String phase, boolean nativeSaveReturned, long sourceEpoch,
            long sourceVersion, long serializations, long writes, int dirtyUuids, String sourceFault) {}

    /** historicalFaultCount is the observed minimum of distinct refused whole-writer duties,
     * not a fabricated exact value of the source owner's private failure counter. */
    record Report(boolean passed, long historicalFaultCount, boolean restoredExactState,
            List<Observation> observations, String failure) {
        Report { observations = List.copyOf(observations); }

        @Override public String toString() {
            var text = new StringBuilder("P11-NATIVE-MATERIAL-PROBE-V1\n"
                    + "scope=MANAGED_NATIVE_MANA_MATERIAL_MISMATCH\n"
                    + "fixtureActor=false\nmanualBoundaryCallbacks=false\n"
                    + "historicalFaultCountMeaning=OBSERVED_REFUSAL_LOWER_BOUND\n");
            text.append("historicalFaultCount=").append(historicalFaultCount).append('\n');
            text.append("restoredExactState=").append(restoredExactState).append('\n');
            for (var observation : observations) { text.append(observation).append('\n'); }
            text.append("failure=").append(failure).append('\n');
            return text.append("RESULT=").append(passed ? "PASS" : "FAIL").append('\n').toString();
        }
    }

    static Report run(MinecraftServer server, ServerPlayer actor, Path freshOwnedDirectory) {
        var state = new State();
        try {
            require(server.isSameThread() && actor.getServer() == server && !actor.isFakePlayer()
                    && server.isSingleplayerOwner(actor.getGameProfile())
                    && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                    && actor.connection != null && actor.connection.getConnection().isConnected(),
                    "material probe requires the exact authenticated ordinary integrated host");
            require(freshOwnedDirectory.isAbsolute()
                    && freshOwnedDirectory.equals(freshOwnedDirectory.normalize())
                    && freshOwnedDirectory.getParent() != null
                    && Files.isDirectory(freshOwnedDirectory.getParent(), LinkOption.NOFOLLOW_LINKS),
                    "material probe needs a fresh absolute child of its owned evidence directory");
            Files.createDirectory(freshOwnedDirectory);
            var type = NeoForgeRegistries.ATTACHMENT_TYPES.get(MANA);
            require(type != null, "registered native Mana attachment type is missing");
            exercise(server, actor, type, freshOwnedDirectory, state);
            return new Report(true, state.historicalFaultCount, state.restoredExactState,
                    state.observations, "NONE");
        } catch (IOException | RuntimeException failure) {
            String detail = failure.getClass().getName() + ": " + failure.getMessage();
            return new Report(false, state.historicalFaultCount, state.restoredExactState,
                    state.observations, detail.length() > 512 ? detail.substring(0, 512) : detail);
        }
    }

    private static <T> void exercise(MinecraftServer server, ServerPlayer actor,
            AttachmentType<T> type, Path evidence, State state) throws IOException {
        // A wildcard capture keeps the actual private ManaState type intact: no raw cast,
        // reflection, invented state, or new product getter is needed.
        T original = actor.getExistingDataOrNull(type);
        require(original != null && actor.hasData(type),
                "probe requires already-present Mana; it must not provision gameplay state");
        var stats = actor.getStats();
        var advancements = actor.getAdvancements();
        var initial = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
        requireCurrent(initial, null);
        require("NONE".equals(initial.sourceFault()), "material probe source has an earlier incident");
        long observerFailures = P11NativeStorageBoundary.observerFailureCount();
        var files = protectedFiles(server, actor);
        write(evidence.resolve("protected-files.txt"), String.join("\n",
                files.stream().map(path -> path.toAbsolutePath().normalize().toString()).toList()) + "\n");

        boolean baselineReturn = server.saveEverything(true, false, false);
        var baseline = observe("baseline", baselineReturn, server, actor, evidence, state);
        requireCurrent(baseline, initial);
        require(baselineReturn && "NONE".equals(baseline.sourceFault()),
                "healthy baseline native save did not return normally");
        requireAllSuccessful(baseline);
        require(actor.getExistingDataOrNull(type) == original, "baseline changed the exact Mana state");
        var before = snapshot(files, evidence, "baseline");
        var originalCache = server.getPlayerList().getSingleplayerData();
        require(originalCache != null, "healthy native save produced no integrated host cache");
        var cacheBefore = cacheBytes(originalCache);
        Files.write(evidence.resolve("baseline-cache.nbt"), cacheBefore, StandardOpenOption.CREATE_NEW);

        T replacement = null;
        Throwable primary = null;
        try {
            require(actor.removeData(type) == original, "native remove did not return the original exact state");
            replacement = actor.getData(type);
            require(replacement != null && replacement != original
                    && replacement.getClass() == original.getClass(),
                    "native default was not a distinct state of the same registered Mana type");
            // Intentionally bypasses only the production Mana owner's publication API in this
            // engineering fixture. The source still witnesses the exact original object.
            boolean mismatchReturn = server.saveEverything(true, false, false);
            var mismatch = observe("mismatch", mismatchReturn, server, actor, evidence, state);
            var after = snapshot(files, evidence, "mismatch");
            var cacheAfter = server.getPlayerList().getSingleplayerData();
            var cacheAfterBytes = cacheBytes(cacheAfter);
            Files.write(evidence.resolve("mismatch-cache.nbt"), cacheAfterBytes,
                    StandardOpenOption.CREATE_NEW);
            boolean nbtUnchanged = sameNbtFiles(before, after);
            boolean cacheUnchanged = cacheAfter == originalCache && Arrays.equals(cacheBefore, cacheAfterBytes);
            write(evidence.resolve("mismatch-observation.txt"),
                    "nativeSaveReturnIsNotDurabilityProof=true\n"
                    + "protectedNbtFilesUnchanged=" + nbtUnchanged + "\n"
                    + "cacheExactReferenceAndBytesUnchanged=" + cacheUnchanged + "\n"
                    + "serializationsDelta=" + (mismatch.serializations() - baseline.serializations()) + "\n"
                    + "writerCompletionsDelta=" + (mismatch.writes() - baseline.writes()) + "\n");
            requireCurrent(mismatch, baseline);
            require(mismatchReturn && nbtUnchanged && cacheUnchanged,
                    "material mismatch modified protected native NBT/cache or stopped native save");
            require(mismatch.serializations() == baseline.serializations()
                    && mismatch.writes() - baseline.writes() == INDEPENDENT.size(),
                    "mismatch must skip whole serialization but complete exactly two independent writers");
            require("WRITE".equals(mismatch.sourceFault()) && mismatch.resources().dirtyUuids() == 1,
                    "whole-writer refusals did not retain aggregate dirty responsibility");
            for (var kind : WHOLE) {
                var prior = writer(baseline, kind);
                var current = writer(mismatch, kind);
                require(current.dirty() && current.attempt() > prior.attempt()
                        && samePriorAttemptFacts(prior, current),
                        "refused whole writer was falsely completed or lost prior facts: " + kind);
                state.historicalFaultCount++;
            }
            for (var kind : INDEPENDENT) {
                var current = writer(mismatch, kind);
                requireSuccessful(current);
                require(current.attempt() > writer(baseline, kind).attempt(),
                        "independent owner reused a historical completion: " + kind);
            }
            require(actor.getStats() == stats && actor.getAdvancements() == advancements
                    && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                    && actor.getExistingDataOrNull(type) == replacement,
                    "native mismatch path changed exact actor/independent/attachment owners");
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            // Restoration runs even if native save, evidence IO, or any assertion fails.
            // A restore failure is secondary to the actual original failure, never its replacement.
            try {
                T replaced = actor.setData(type, original);
                state.restoredExactState = actor.hasData(type)
                        && actor.getExistingDataOrNull(type) == original;
                require(state.restoredExactState, "exact original Mana state could not be restored");
                if (replacement != null) {
                    require(replaced == replacement, "restoration observed an unexpected intervening Mana owner");
                }
            } catch (RuntimeException | Error secondary) {
                if (primary != null) { primary.addSuppressed(secondary); }
                else { throw secondary; }
            }
        }

        // Recovery uses the same actual actor and original native writers, not another command,
        // source publication, new body, artificial receipt, or direct material reset.
        boolean recoveryReturn = server.saveEverything(true, false, false);
        var recovered = observe("restored", recoveryReturn, server, actor, evidence, state);
        snapshot(files, evidence, "restored");
        Files.write(evidence.resolve("restored-cache.nbt"),
                cacheBytes(server.getPlayerList().getSingleplayerData()), StandardOpenOption.CREATE_NEW);
        requireCurrent(recovered, baseline);
        require(recoveryReturn && "WRITE".equals(recovered.sourceFault()),
                "recovery must preserve the historical refusal while completing a fresh native save");
        requireAllSuccessful(recovered);
        require(state.historicalFaultCount == WHOLE.size() && state.restoredExactState
                && actor.getExistingDataOrNull(type) == original && actor.getStats() == stats
                && actor.getAdvancements() == advancements
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                && P11NativeStorageBoundary.observerFailureCount() == observerFailures,
                "material probe did not preserve exact owners or introduced an observer failure");
        for (var kind : WHOLE) {
            require(writer(recovered, kind).attempt() > writer(baseline, kind).attempt(),
                    "restored whole writer lacks a fresh native attempt: " + kind);
        }
    }

    private static P11QualifiedSourceOwner.Diagnostics observe(String phase, boolean nativeReturn,
            MinecraftServer server, ServerPlayer actor, Path evidence, State state) throws IOException {
        var diagnostic = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
        state.observations.add(new Observation(phase, nativeReturn, diagnostic.sourceEpoch(),
                diagnostic.sourceVersion(), diagnostic.serializations(), diagnostic.writes(),
                diagnostic.resources().dirtyUuids(), diagnostic.sourceFault()));
        write(evidence.resolve(phase + "-diagnostics.txt"), diagnostic + "\n");
        return diagnostic;
    }

    private static void requireCurrent(P11QualifiedSourceOwner.Diagnostics current,
            P11QualifiedSourceOwner.Diagnostics previous) {
        require(current.active() && current.bodyComplete() && !current.candidatePresent()
                && current.sourceEpoch() > 0 && current.resources().retainedUuids() == 1
                && current.resources().waitingConnections() == 0 && current.resources().inFlight() == 0
                && current.resources().sealedSnapshots() == 0 && current.resources().sealedBytes() == 0,
                "material probe lacks a complete current source or leaked a reservation");
        if (previous != null) {
            require(current.sourceEpoch() == previous.sourceEpoch()
                    && current.sourceVersion() == previous.sourceVersion()
                    && current.equippedSlot0().equals(previous.equippedSlot0()),
                    "native probe changed the source identity or equipped reference");
        }
    }

    private static void requireAllSuccessful(P11QualifiedSourceOwner.Diagnostics diagnostic) {
        for (var kind : List.of("PLAYER_DATA", "LEVEL_PLAYER", "STATISTICS", "ADVANCEMENTS")) {
            requireSuccessful(writer(diagnostic, kind));
        }
        var cache = writer(diagnostic, "CACHE");
        require(!cache.dirty() && cache.attempt() > 0 && "COMPLETED".equals(cache.terminal())
                && "SUCCEEDED".equals(cache.encode()) && "SUCCEEDED".equals(cache.cacheAssignment())
                && "NOT_OBSERVED".equals(cache.write()) && "NOT_OBSERVED".equals(cache.close())
                && "NOT_OBSERVED".equals(cache.replace()), "actual cache assignment proof is absent");
        require(diagnostic.resources().dirtyUuids() == 0,
                "successful native writers did not discharge aggregate dirty responsibility");
    }

    private static void requireSuccessful(P11QualifiedSourceOwner.WriterDiagnostic value) {
        boolean json = INDEPENDENT.contains(value.kind());
        require(!value.dirty() && value.attempt() > 0 && "COMPLETED".equals(value.terminal())
                && "SUCCEEDED".equals(value.encode()) && "SUCCEEDED".equals(value.write())
                && "SUCCEEDED".equals(value.close())
                && (json ? "NOT_OBSERVED" : "SUCCEEDED").equals(value.replace())
                && "NOT_OBSERVED".equals(value.cacheAssignment()),
                "actual native writer proof is absent: " + value);
    }

    private static P11QualifiedSourceOwner.WriterDiagnostic writer(
            P11QualifiedSourceOwner.Diagnostics diagnostic, String kind) {
        var matches = diagnostic.writers().stream().filter(value -> kind.equals(value.kind())).toList();
        require(matches.size() == 1, "missing/duplicate native writer diagnostic: " + kind);
        return matches.getFirst();
    }

    private static boolean samePriorAttemptFacts(P11QualifiedSourceOwner.WriterDiagnostic before,
            P11QualifiedSourceOwner.WriterDiagnostic after) {
        return before.terminal().equals(after.terminal()) && before.encode().equals(after.encode())
                && before.write().equals(after.write()) && before.close().equals(after.close())
                && before.replace().equals(after.replace())
                && before.cacheAssignment().equals(after.cacheAssignment());
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
        var result = new ArrayList<byte[]>();
        for (int index = 0; index < files.size(); index++) {
            var path = files.get(index);
            if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
                write(evidence.resolve(phase + "-" + index + ".absent"), "ABSENT\n");
                result.add(null);
            } else {
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && Files.size(path) <= EVIDENCE_BYTES, "evidence input is nonregular or too large");
                var bytes = Files.readAllBytes(path);
                Files.write(evidence.resolve(phase + "-" + index + ".bin"), bytes, StandardOpenOption.CREATE_NEW);
                result.add(bytes);
            }
        }
        return result;
    }

    private static boolean sameNbtFiles(List<byte[]> before, List<byte[]> after) {
        // JSON owners are deliberately allowed to save independently; all their bytes are
        // archived, but equality is not imposed on valid independent native transitions.
        for (int index : new int[] {0, 1, 4, 5}) {
            if (!Arrays.equals(before.get(index), after.get(index))) { return false; }
        }
        return true;
    }

    private static byte[] cacheBytes(CompoundTag cache) throws IOException {
        require(cache != null, "native integrated cache is missing");
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(new OutputStream() {
            private int written;
            @Override public void write(int value) throws IOException {
                reserve(1); bytes.write(value);
            }
            @Override public void write(byte[] input, int offset, int length) throws IOException {
                reserve(length); bytes.write(input, offset, length);
            }
            private void reserve(int length) throws IOException {
                if (length < 0 || length > EVIDENCE_BYTES - written) {
                    throw new IOException("cache evidence exceeds the engineering output bound");
                }
                written += length;
            }
        })) {
            NbtIo.writeUnnamedTag(cache, output);
        }
        return bytes.toByteArray();
    }

    private static void write(Path path, String value) throws IOException {
        Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }

    private static final class State {
        final List<Observation> observations = new ArrayList<>();
        long historicalFaultCount;
        boolean restoredExactState;
    }
}
