package com.yo1no.gramarye.magic.definition.store;

import com.google.gson.JsonObject;
import com.yo1no.gramarye.P4E3StartupObservationTestAccess;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** One property-selected actual ServerStarting observation in each isolated P4-E3 child. */
@GameTestHolder("gramarye_p4_e3")
@PrefixGameTestTemplate(false)
public final class P4E3StartupMemoryGameTests {
    private static final String MODE_PROPERTY = "gramarye.p4e3.runMode";
    private static final String REPORT_ROOT_PROPERTY = "gramarye.p4e3.reportRoot";
    private static final String FIRST_RUNTIME_FILE = "first-runtime.json";
    private static final String RESTART_RUNTIME_FILE = "restart-runtime.json";
    private static final long MAXIMUM_REPORT_BYTES = 65_536L;

    private P4E3StartupMemoryGameTests() {
    }

    @GameTest(
            templateNamespace = "gramarye_p4_e3",
            template = "p4_e3_probe",
            timeoutTicks = 11_000)
    public static void observeSelectedStartup(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var mode = Mode.fromProperty();
        var observation = P4E3StartupObservationTestAccess.consume(server);
        requireObservation(mode, observation);
        try {
            var primary = server.getWorldPath(LevelResource.ROOT)
                    .resolve("data")
                    .resolve("gramarye_skill_definitions.dat");
            if (!Files.isRegularFile(primary) || Files.isSymbolicLink(primary)) {
                throw new IOException("P4-E3 SavedData primary is absent");
            }
            writeRuntime(
                    reportRoot(), mode, observation,
                    sha256(primary), Files.size(primary),
                    Files.getLastModifiedTime(primary).toMillis());
            System.out.println(mode == Mode.FIRST
                    ? "P4_E3_FIRST_COMPLETE"
                    : "P4_E3_RESTART_COMPLETE");
            helper.succeed();
        } catch (IOException exception) {
            throw new AssertionError("P4-E3 runtime report failed", exception);
        }
    }

    private static void requireObservation(
            Mode mode, P4E3StartupObservationTestAccess.Observation observation) {
        var first = mode == Mode.FIRST;
        if (observation.sessionToken() != 1L
                || observation.auditInvocations() != 1
                || observation.auditVariant()
                        != com.yo1no.gramarye.P4E2QualificationFacade.E3AuditVariant.COMPLETE
                || observation.auditGeneration() != 1L
                || observation.completeConsumeInvocations() != 1
                || observation.snapshotInvocations() != 1
                || observation.snapshotVariant()
                        != com.yo1no.gramarye.P4E2QualificationFacade.E3SnapshotVariant.COMPLETE
                || observation.completeRootCount() != 65_536
                || observation.reclaimInvocations() != 1
                || observation.reclaimVariant()
                        != (first
                                ? com.yo1no.gramarye.P4E2QualificationFacade
                                        .E3ReclaimVariant.COMPLETED_POSITIVE
                                : com.yo1no.gramarye.P4E2QualificationFacade
                                        .E3ReclaimVariant.COMPLETED_ZERO)
                || observation.historiesScanned() != 2_049
                || observation.revisionsScanned() != (first ? 4_096 : 4_095)
                || observation.historiesChanged() != (first ? 1 : 0)
                || observation.revisionsReclaimed() != (first ? 1 : 0)
                || observation.dirtyBefore()
                || observation.dirtyAfter() != first
                || observation.indexTerminalObservations() != 1
                || observation.indexTerminal()
                        != (first
                                ? com.yo1no.gramarye.P4E2QualificationFacade
                                        .E3IndexTerminal.INCOMPLETE
                                : com.yo1no.gramarye.P4E2QualificationFacade
                                        .E3IndexTerminal.COMPLETE_INDEX)
                || observation.indexGeneration() != 1L) {
            throw new AssertionError(
                    "P4-E3 direct startup observation differs from the locked mode");
        }
    }

    private static void writeRuntime(
            Path reportRoot,
            Mode mode,
            P4E3StartupObservationTestAccess.Observation observation,
            String primarySha256,
            long primaryBytes,
            long primaryModifiedMillis) throws IOException {
        var json = new JsonObject();
        json.addProperty("schema_version", 0);
        json.addProperty("mode", mode.token);
        json.addProperty("session_token", observation.sessionToken());
        json.addProperty("audit_invocations", observation.auditInvocations());
        json.addProperty("audit_variant", observation.auditVariant().name());
        json.addProperty("audit_generation", observation.auditGeneration());
        json.addProperty(
                "complete_consume_invocations", observation.completeConsumeInvocations());
        json.addProperty("snapshot_invocations", observation.snapshotInvocations());
        json.addProperty("snapshot_variant", observation.snapshotVariant().name());
        json.addProperty("complete_root_count", observation.completeRootCount());
        json.addProperty("reclaim_invocations", observation.reclaimInvocations());
        json.addProperty("reclaim_variant", observation.reclaimVariant().name());
        json.addProperty("histories_scanned", observation.historiesScanned());
        json.addProperty("revisions_scanned", observation.revisionsScanned());
        json.addProperty("histories_changed", observation.historiesChanged());
        json.addProperty("revisions_reclaimed", observation.revisionsReclaimed());
        json.addProperty("dirty_before", observation.dirtyBefore());
        json.addProperty("dirty_after", observation.dirtyAfter());
        json.addProperty(
                "index_terminal_observations", observation.indexTerminalObservations());
        json.addProperty("index_terminal", observation.indexTerminal().name());
        json.addProperty("index_generation", observation.indexGeneration());
        json.addProperty("primary_sha256", primarySha256);
        json.addProperty("primary_bytes", primaryBytes);
        json.addProperty("primary_modified_millis", primaryModifiedMillis);
        var bytes = (json + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAXIMUM_REPORT_BYTES) {
            throw new IOException("P4-E3 runtime report exceeds its bound");
        }
        Files.createDirectories(reportRoot);
        var path = reportRoot.resolve(
                mode == Mode.FIRST ? FIRST_RUNTIME_FILE : RESTART_RUNTIME_FILE);
        try (var channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            var source = ByteBuffer.wrap(bytes);
            while (source.hasRemaining()) {
                channel.write(source);
            }
            channel.force(true);
        }
    }

    private static Path reportRoot() {
        var value = System.getProperty(REPORT_ROOT_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("P4-E3 report root property is absent");
        }
        var path = Path.of(value).toAbsolutePath().normalize();
        var portable = path.toString().replace('\\', '/');
        if (!portable.endsWith("/build/reports/p4-e3-fixed-heap")) {
            throw new IllegalStateException("P4-E3 report root is outside its build tree");
        }
        return path;
    }

    private static String sha256(Path path) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (var input = Files.newInputStream(path)) {
            var buffer = new byte[16_384];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private enum Mode {
        FIRST("first"),
        RESTART("restart");

        private final String token;

        Mode(String token) {
            this.token = token;
        }

        private static Mode fromProperty() {
            return switch (System.getProperty(MODE_PROPERTY, "")) {
                case "first" -> FIRST;
                case "restart" -> RESTART;
                default -> throw new IllegalStateException("P4-E3 run mode property is invalid");
            };
        }
    }
}
