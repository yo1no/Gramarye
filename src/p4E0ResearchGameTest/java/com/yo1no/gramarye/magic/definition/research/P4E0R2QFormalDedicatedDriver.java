package com.yo1no.gramarye.magic.definition.research;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;

/** One-case dedicated formal child with an independent forced 870-second watchdog. */
final class P4E0R2QFormalDedicatedDriver {
    private static final long WATCHDOG_SECONDS = 870L;
    private static final int TIMEOUT_EXIT_CODE = 124;
    private static final String RUNNING = "RUNNING\n";
    private static final String COMPLETED = "COMPLETED\n";
    private static final String FAILED = "FAILED\n";
    private static final String TIMEOUT = "TIMEOUT\n";

    private P4E0R2QFormalDedicatedDriver() {
    }

    static void run(MinecraftServer server) throws IOException {
        if (!(server instanceof GameTestServer) || !server.isSameThread()) {
            throw new IllegalStateException("formal R2Q child requires the GameTest server thread");
        }
        if (!"true".equals(required("gramarye.p4e0.r2q.formal.enabled"))) {
            throw new IllegalStateException("formal child enable property changed");
        }
        var caseIndex = exactCase(required("gramarye.p4e0.r2q.formal.caseIndex"));
        var controlPath = requiredPath("gramarye.p4e0.r2q.formal.studyControl");
        var caseRoot = requiredPath("gramarye.p4e0.r2q.formal.caseRoot");
        var childResult = requiredPath("gramarye.p4e0.r2q.formal.childResult");
        var runningMarker = requiredPath("gramarye.p4e0.r2q.formal.runningMarker");
        var watchdogSeconds = exactPositiveLong(
                required("gramarye.p4e0.r2q.formal.watchdogSeconds"));
        var diskBudget = exactPositiveLong(
                required("gramarye.p4e0.r2q.formal.diskBudgetBytes"));
        if (watchdogSeconds != WATCHDOG_SECONDS
                || diskBudget != P4E0R2QFormalEvidence.FORMAL_DISK_BUDGET_BYTES) {
            throw new IllegalArgumentException("formal child runtime coordinates changed");
        }
        var workRoot = controlPath.getParent();
        var expectedCase = P4E0R2QFormalEvidence.caseDirectory(workRoot, caseIndex);
        if (!caseRoot.equals(expectedCase)
                || !childResult.equals(caseRoot.resolve(P4E0R2QFormalWorkload.CHILD_RESULT))
                || !runningMarker.equals(caseRoot.resolve(P4E0R2QFormalWorkload.RUNNING_MARKER))) {
            throw new IllegalArgumentException("formal child path hand-off changed");
        }
        var control = P4E0R2QFormalEvidence.readControl(controlPath);
        writeNewForced(runningMarker, RUNNING);
        var timeoutMarker = caseRoot.resolve(P4E0R2QFormalWorkload.TIMEOUT_MARKER);
        try (var watchdog = Watchdog.start(timeoutMarker, watchdogSeconds)) {
            final P4E0R2QFormalResult result;
            try {
                result = P4E0R2QFormalWorkload.execute(
                        server, caseRoot, control, caseIndex);
            } catch (P4E0R2QFormalWorkload.FixtureInvalidException exception) {
                writeFailureIfAbsent(
                        childResult,
                        P4E0R2QFormalWorkload.failedResult(
                                control,
                                caseIndex,
                                P4E0R2QFormalResult.ProcessClassification.FIXTURE_INVALID,
                                exception.getClass().getName()));
                replaceForced(runningMarker, FAILED);
                throw exception;
            } catch (RuntimeException exception) {
                writeFailureIfAbsent(
                        childResult,
                        P4E0R2QFormalWorkload.failedResult(
                                control,
                                caseIndex,
                                P4E0R2QFormalResult.ProcessClassification
                                        .INSTRUMENTATION_FAILURE,
                                exception.getClass().getName()));
                replaceForced(runningMarker, FAILED);
                throw exception;
            }
            P4E0R2QFormalEvidence.writeResult(childResult, result);
            replaceForced(runningMarker, COMPLETED);
        }
    }

    static void runRunnerSmoke(MinecraftServer server) throws IOException {
        if (!(server instanceof GameTestServer) || !server.isSameThread()) {
            throw new IllegalStateException("runner smoke requires the GameTest server thread");
        }
        P4E0R2QFormalMain.writeDedicatedRunnerSmoke(
                requiredPath("gramarye.p4e0.r2q.runnerSmokeReportRoot"));
    }

    private static void writeFailureIfAbsent(
            Path path, P4E0R2QFormalResult result) throws IOException {
        if (!Files.exists(path)) {
            P4E0R2QFormalEvidence.writeResult(path, result);
        }
    }

    private static void writeNewForced(Path path, String text) throws IOException {
        P4E0R2QFormalEvidence.writeForcedMarker(path, text);
    }

    private static void replaceForced(Path path, String text) throws IOException {
        var temporary = path.resolveSibling(path.getFileName() + ".replacement");
        writeNewForced(temporary, text);
        try {
            Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            throw new IOException("formal marker replacement requires atomic move", exception);
        }
    }

    private static void forceTimeout(Path marker) throws IOException {
        var bytes = TIMEOUT.getBytes(StandardCharsets.US_ASCII);
        Files.createDirectories(marker.getParent());
        try (var channel = FileChannel.open(
                marker, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void haltAfterTimeout(Path marker) {
        var exitCode = TIMEOUT_EXIT_CODE;
        try {
            forceTimeout(marker);
        } catch (IOException exception) {
            // The distinct exit code keeps marker I/O failure out of the TIMEOUT classification.
            exitCode = 125;
        }
        Runtime.getRuntime().halt(exitCode);
    }

    private static String required(String property) {
        var value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("formal child property is absent: " + property);
        }
        return value;
    }

    private static Path requiredPath(String property) {
        return Path.of(required(property)).toAbsolutePath().normalize();
    }

    private static int exactCase(String value) {
        if (!value.matches("(?:[0-9]|1[0-9]|2[0-8])")) {
            throw new IllegalArgumentException("formal child case index changed");
        }
        return Integer.parseInt(value);
    }

    private static long exactPositiveLong(String value) {
        if (!value.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("formal child numeric property is malformed");
        }
        return Long.parseLong(value);
    }

    private static final class Watchdog implements AutoCloseable {
        private final ScheduledExecutorService executor;

        private Watchdog(ScheduledExecutorService executor) {
            this.executor = executor;
        }

        private static Watchdog start(Path timeoutMarker, long timeoutSeconds) {
            ThreadFactory factory = runnable -> {
                var thread = new Thread(runnable, "p4-e0-r2q-formal-watchdog");
                thread.setDaemon(true);
                return thread;
            };
            var executor = Executors.newSingleThreadScheduledExecutor(factory);
            executor.schedule(
                    () -> haltAfterTimeout(timeoutMarker), timeoutSeconds, TimeUnit.SECONDS);
            return new Watchdog(executor);
        }

        @Override
        public void close() {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("formal watchdog did not terminate");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "formal watchdog termination was interrupted", exception);
            }
        }
    }
}
