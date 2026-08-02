package com.yo1no.gramarye.magic.definition.research;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;

/** Dedicated-only Matrix-F child; all configuration is an explicit bounded hand-off. */
final class P4E0ResearchR2DedicatedDriver {
    private static final int TIMEOUT_EXIT_CODE = 124;
    private static final long REQUIRED_WATCHDOG_SECONDS = 870L;

    private P4E0ResearchR2DedicatedDriver() {
    }

    static void run(MinecraftServer server) throws IOException {
        var fixtureRoot = requiredPath("gramarye.p4e0.research.fixtureRoot");
        var reportRoot = requiredPath("gramarye.p4e0.research.reportRoot");
        var runIndex = Math.toIntExact(P4E0ResearchR2Main.exactPositiveLong(
                required("gramarye.p4e0.research.runIndex"), "run index"));
        var diskBudget = P4E0ResearchR2Main.exactPositiveLong(
                required("gramarye.p4e0.research.diskBudgetBytes"), "disk budget");
        var watchdogSeconds = P4E0ResearchR2Main.exactPositiveLong(
                required("gramarye.p4e0.research.watchdogSeconds"), "watchdog");
        if (watchdogSeconds != REQUIRED_WATCHDOG_SECONDS) {
            throw new IllegalArgumentException("combined watchdog coordinate changed");
        }
        var plan = P4E0ResearchR2Main.standardPlan();
        var spec = plan.requireRun(runIndex);
        if (spec.mode() != P4E0ResearchMatrixPlan.Mode.DEDICATED
                || spec.heapMiB() != Math.toIntExact(P4E0ResearchR2Main.exactPositiveLong(
                        required("gramarye.p4e0.research.heapMiB"), "heap"))
                || !spec.profile().equals(required("gramarye.p4e0.research.profile"))) {
            throw new IllegalArgumentException("combined dedicated properties differ from plan");
        }
        var profilePath = P4E0ResearchR2Main.combinedProfilePath(fixtureRoot, spec);
        var childReport = requiredPath("gramarye.p4e0.research.childReport");
        var marker = requiredPath("gramarye.p4e0.research.runningMarker");
        if (!childReport.equals(
                        P4E0ResearchR2Main.combinedChildRecordPath(reportRoot, spec))
                || !marker.equals(
                        P4E0ResearchR2Main.combinedRunningMarkerPath(reportRoot, spec))) {
            throw new IllegalArgumentException("combined evidence paths differ from plan");
        }
        var profile = P4E0ResearchCombinedProfileFile.read(profilePath);
        if (!profile.planHash().equals(plan.planHash())
                || profile.runIndex() != spec.runIndex()
                || profile.profile().heapMiB() != spec.heapMiB()
                || !profile.profile().kind().name().equals(spec.profile())) {
            throw new IOException("combined profile hand-off differs from dedicated run");
        }

        writeAndForceMarker(
                marker, P4E0ResearchR2Main.combinedRunningMarkerContent(spec, plan));
        try (var watchdog = Watchdog.start(watchdogSeconds)) {
            try {
                P4E0ResearchR2Main.verifyCombinedProfileInput(
                        profile, spec, plan, diskBudget);
                var sample = P4E0ResearchCombinedCoordinator.run(server, profile.profile());
                var record = P4E0ResearchR2Main.completedCombinedRecord(
                        spec,
                        plan,
                        diskBudget,
                        profile,
                        sample.metrics(),
                        sample.heap(),
                        sample.elapsedMillis(),
                        profile.profile().directory());
                record.writeNew(childReport);
            } catch (IOException exception) {
                writeFailureIfAbsent(
                        childReport,
                        P4E0ResearchR2Main.failedCombinedRecord(
                                spec,
                                plan,
                                diskBudget,
                                exception,
                                P4E0ResearchResult.Classification.FIXTURE_INVALID,
                                1,
                                profile.profile().directory()));
                throw exception;
            } catch (RuntimeException exception) {
                writeFailureIfAbsent(
                        childReport,
                        P4E0ResearchR2Main.failedCombinedRecord(
                                spec,
                                plan,
                                diskBudget,
                                exception,
                                P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE,
                                1,
                                profile.profile().directory()));
                throw exception;
            }
        }
    }

    private static void writeFailureIfAbsent(
            Path childReport, P4E0ResearchRunRecord record) throws IOException {
        if (!Files.exists(childReport)) {
            record.writeNew(childReport);
        }
    }

    private static void writeAndForceMarker(Path path, String text) throws IOException {
        var bytes = text.getBytes(StandardCharsets.US_ASCII);
        Files.createDirectories(path.getParent());
        try (var channel = FileChannel.open(
                path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static String required(String property) {
        var value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("combined property is absent: " + property);
        }
        return value;
    }

    private static Path requiredPath(String property) {
        return Path.of(required(property)).toAbsolutePath().normalize();
    }

    private static final class Watchdog implements AutoCloseable {
        private final ScheduledExecutorService executor;

        private Watchdog(ScheduledExecutorService executor) {
            this.executor = executor;
        }

        private static Watchdog start(long timeoutSeconds) {
            ThreadFactory factory = runnable -> {
                var thread = new Thread(runnable, "p4-e0-r2-combined-watchdog");
                thread.setDaemon(true);
                return thread;
            };
            var executor = Executors.newSingleThreadScheduledExecutor(factory);
            executor.schedule(
                    () -> Runtime.getRuntime().halt(TIMEOUT_EXIT_CODE),
                    timeoutSeconds,
                    TimeUnit.SECONDS);
            return new Watchdog(executor);
        }

        @Override
        public void close() {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("combined watchdog did not terminate");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "combined watchdog termination was interrupted", exception);
            }
        }
    }
}
