package com.yo1no.gramarye;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Excluded one-shot real-constructor fault; never mints source, material or writer facts. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
final class P11NativeConstructorFaultProbe {
    private static final long ENGINEERING_READ_BOUND = 32L * 1024 * 1024;
    private static MinecraftServer server;
    private static ServerPlayer original;
    private static Connection transport;
    private static volatile boolean armed;
    private static volatile boolean fired;
    private static volatile Throwable observationFailure;
    private static ConstructorFault sentinel;
    private static P11QualifiedSourceOwner.Diagnostics atThrow;
    private static volatile FailureReport afterFailure;
    private static List<FileFingerprint> protectedFiles;
    private static String cacheFingerprint;

    private P11NativeConstructorFaultProbe() {}

    static void arm(MinecraftServer actualServer, ServerPlayer actor) {
        require(server == null && !armed && !fired, "constructor probe is process-local and single-use");
        require(actualServer.isSameThread() && actor.getServer() == actualServer && !actor.isFakePlayer()
                        && actualServer.getPlayerList().getPlayer(actor.getUUID()) == actor
                        && actualServer.getPlayerCount() == 1 && actor.connection != null
                        && actor.connection.getConnection().isConnected()
                        && actualServer.isSingleplayerOwner(actor.getGameProfile()),
                "constructor probe requires the exact sole authenticated integrated host");
        var initial = P11NativeStorageBoundary.diagnostics(actualServer, actor.getUUID());
        require(initial.active() && initial.bodyComplete() && !initial.candidatePresent()
                        && "NONE".equals(initial.sourceFault()), "constructor probe source is not healthy");
        server = actualServer;
        original = actor;
        transport = actor.connection.getConnection();
        armed = true;
    }

    static boolean fired() { return fired; }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void observeConstruction(EntityEvent.EntityConstructing event) {
        if (!armed || !(event.getEntity() instanceof ServerPlayer partial)
                || partial == original || partial.level().getServer() != server) { return; }
        armed = false;
        try {
            // The partial player's profile/server/attachment fields are not initialized yet.
            // Identity comes from the previously observed real CONFIG transport, not from B.
            require(server.isSameThread() && transport.isConnected()
                            && transport.getPacketListener() instanceof ServerConfigurationPacketListenerImpl listener
                            && listener.getMainThreadEventLoop() == server
                            && listener.getOwner().getId().equals(original.getUUID())
                            && original.isRemoved() && server.getPlayerList().getPlayerCount() == 0,
                    "constructor event escaped the exact owned configuration handoff");
            atThrow = P11NativeStorageBoundary.diagnostics(server, original.getUUID());
            require(atThrow.active() && atThrow.bodyComplete() && !atThrow.candidatePresent()
                            && "NONE".equals(atThrow.sourceFault())
                            && atThrow.resources().sealedSnapshots() == 2
                            && atThrow.resources().sealedBytes() > 0,
                    "constructor fault did not reach the real two-seal pre-constructor selection");
            protectedFiles = fingerprintFiles();
            cacheFingerprint = fingerprintCache();
            sentinel = new ConstructorFault();
        } catch (RuntimeException | Error failure) {
            observationFailure = failure;
            fired = true;
            throw failure;
        }
        fired = true;
        throw sentinel; // Original configuration catch/disconnect remains the actual consumer.
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void observeUnwind(ServerTickEvent.Post event) {
        observeUnwind(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void observeStopping(ServerStoppingEvent event) {
        // Network work can run in waitUntilNextTick after that tick's Post event. The
        // automatic CONFIG disconnect may halt before any next Post, but not before this
        // original lifecycle event, which precedes stopServer's original save writers.
        observeUnwind(event.getServer());
    }

    private static void observeUnwind(MinecraftServer actualServer) {
        if (!fired || afterFailure != null || observationFailure != null || actualServer != server) { return; }
        try { afterFailure = assertAfterFailure(); }
        catch (RuntimeException | Error failure) { observationFailure = failure; }
    }

    static FailureReport assertAfterFailure() {
        requireNoObservationFailure();
        require(server != null && server.isSameThread() && fired && sentinel != null,
                "native constructor fault has not actually fired on this server");
        var after = P11NativeStorageBoundary.diagnostics(server, original.getUUID());
        requireRetainedSource(after);
        require("PARTIAL".equals(after.sourceFault()), "post-constructor source is not PARTIAL");
        boolean rejected = false;
        try { original.saveWithoutId(new CompoundTag()); }
        catch (P11QualifiedSourceOwner.SourceUnavailable expected) { rejected = true; }
        require(rejected, "actual old-player native serialization accepted a partial constructor source");
        requireProtectedMaterial();
        var finalObservation = P11NativeStorageBoundary.diagnostics(server, original.getUUID());
        requireRetainedSource(finalObservation);
        require("PARTIAL".equals(finalObservation.sourceFault())
                        && P11NativeStorageBoundary.observerFailureCount() == 0,
                "native material denial lost the primary fault or hid an observer failure");
        return new FailureReport(sentinel.getClass().getName(), sentinel.getMessage(), atThrow,
                finalObservation, true, protectedFiles, cacheFingerprint);
    }

    static FailureReport afterFailureReport() {
        requireNoObservationFailure();
        require(afterFailure != null, "real post-unwind observation is absent");
        return afterFailure;
    }

    static P11QualifiedSourceOwner.Diagnostics assertFinalWriters(MinecraftServer actualServer) {
        require(actualServer == server && server.isSameThread(), "wrong native final-writer server");
        afterFailureReport();
        var diagnostic = P11NativeStorageBoundary.diagnostics(server, original.getUUID());
        requireRetainedSource(diagnostic);
        // Later refused stop writers may report WRITE disposition; they cannot erase dirty
        // duty or replace any material. PARTIAL itself was observed before original stop.
        require("PARTIAL".equals(diagnostic.sourceFault()) || "WRITE".equals(diagnostic.sourceFault()),
                "native stop lost the constructor failure disposition");
        requireProtectedMaterial();
        return diagnostic;
    }

    static P11QualifiedSourceOwner.Summary assertStopped() {
        afterFailureReport();
        require(server.isShutdown() && !transport.isConnected(), "fault's original server thread/transport is not terminal");
        var summary = P11NativeStorageBoundary.terminalDiagnostics();
        require(summary != null && summary.nativeStopNormal() && summary.accounts() == 1
                        && summary.resources().retainedUuids() == 1 && summary.resources().dirtyUuids() == 1
                        && summary.resources().sealedSnapshots() == 0 && summary.resources().sealedBytes() == 0
                        && summary.resources().inFlight() == 0 && summary.failures() > 0
                        && summary.writes() == atThrow.writes()
                        && P11NativeStorageBoundary.observerFailureCount() == 0,
                "native stop did not retain the unresolved constructor responsibility: " + summary);
        require(protectedFiles.equals(fingerprintFiles()), "stop changed original protected files");
        return summary;
    }

    private static void requireRetainedSource(P11QualifiedSourceOwner.Diagnostics diagnostic) {
        require(diagnostic.active() && diagnostic.sourceEpoch() == atThrow.sourceEpoch()
                        && diagnostic.sourceVersion() == atThrow.sourceVersion()
                        && diagnostic.bodyComplete() && !diagnostic.candidatePresent()
                        && diagnostic.resources().dirtyUuids() == 1
                        && diagnostic.resources().retainedUuids() == 1
                        && diagnostic.resources().sealedSnapshots() == 0
                        && diagnostic.resources().sealedBytes() == 0 && diagnostic.resources().inFlight() == 0
                        && diagnostic.writes() == atThrow.writes()
                        && server.getPlayerList().getPlayer(original.getUUID()) == null,
                "partial constructor admitted B, dropped duty, leaked seals or added physical writes: " + diagnostic);
        for (var prior : atThrow.writers()) {
            var matches = diagnostic.writers().stream().filter(now -> now.kind().equals(prior.kind())).toList();
            require(matches.size() == 1, "writer kind disappeared after constructor fault");
            var now = matches.getFirst();
            // Dirty generations may advance on refusal. Physical observations may not.
            require(now.terminal().equals(prior.terminal()) && now.encode().equals(prior.encode())
                            && now.write().equals(prior.write()) && now.close().equals(prior.close())
                            && now.replace().equals(prior.replace())
                            && now.cacheAssignment().equals(prior.cacheAssignment()),
                    "constructor fault created a new physical receipt outcome: " + now.kind());
        }
    }

    private static void requireProtectedMaterial() {
        require(protectedFiles.equals(fingerprintFiles()), "constructor fault changed protected native files");
        require(cacheFingerprint.equals(fingerprintCache()), "constructor fault replaced or changed host cache");
    }

    private static List<FileFingerprint> fingerprintFiles() {
        var root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        var player = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toAbsolutePath().normalize();
        var result = new ArrayList<FileFingerprint>();
        for (var file : List.of(player.resolve(original.getUUID() + ".dat"),
                player.resolve(original.getUUID() + ".dat_old"), root.resolve("level.dat"), root.resolve("level.dat_old"))) {
            try {
                if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
                    result.add(new FileFingerprint(root.relativize(file).toString(), -1, "ABSENT"));
                    continue;
                }
                require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                                && Files.size(file) <= ENGINEERING_READ_BOUND, "unsafe engineering fingerprint input");
                var digest = sha256();
                try (var input = Files.newInputStream(file)) {
                    var buffer = new byte[8192];
                    for (int count; (count = input.read(buffer)) >= 0;) { digest.update(buffer, 0, count); }
                }
                result.add(new FileFingerprint(root.relativize(file).toString(), Files.size(file),
                        HexFormat.of().formatHex(digest.digest())));
            } catch (IOException failure) { throw new IllegalStateException("protected native file observation failed", failure); }
        }
        return List.copyOf(result);
    }

    private static String fingerprintCache() {
        var cache = server.getPlayerList().getSingleplayerData();
        require(cache != null && P11StrictNbtSize.measure(cache, ENGINEERING_READ_BOUND)
                        instanceof P11StrictNbtSize.Fits, "native host cache missing or outside engineering reader bound");
        var digest = sha256();
        try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            NbtIo.writeUnnamedTag(cache, output); // Unmanaged primitive observation, no boundary grant.
        } catch (IOException failure) { throw new IllegalStateException("native cache observation failed", failure); }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void requireNoObservationFailure() {
        if (observationFailure != null) { throw new IllegalStateException("constructor probe observation failed", observationFailure); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }

    record FileFingerprint(String relativePath, long bytes, String sha256) {}
    record FailureReport(String sentinelClass, String sentinelMessage,
            P11QualifiedSourceOwner.Diagnostics atThrow, P11QualifiedSourceOwner.Diagnostics afterFailure,
            boolean actualNativeSerializationRejected, List<FileFingerprint> files, String hostCacheSha256) {
        FailureReport { files = List.copyOf(files); }
        @Override public String toString() {
            return "P11-NATIVE-CONSTRUCTOR-FAULT-V1\nlayer=ACTUAL_NATIVE_CONSTRUCTOR_AND_UNWIND\n"
                    + "injection=EntityEvent.EntityConstructing\nlatePostJsonConstructorFault=NOT_COVERED\n"
                    + "sentinel=" + sentinelClass + ':' + sentinelMessage + "\n"
                    + "atThrow=" + atThrow + "\nafterFailure=" + afterFailure + "\n"
                    + "actualNativeSerializationRejected=" + actualNativeSerializationRejected + "\n"
                    + "protectedFiles=" + files + "\nhostCacheSha256=" + hostCacheSha256 + "\n";
        }
    }

    private static final class ConstructorFault extends RuntimeException {
        private ConstructorFault() { super("P11_OWNED_NATIVE_CONSTRUCTOR_FAULT"); }
    }
}
