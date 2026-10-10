package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Actual receipt/stage bodies over typed lease boundaries; not authenticated native evidence. */
final class P7D3MetadataContinuationTest {
    @TempDir static Path temporary;
    private static URLClassLoader loader;
    private static Class<?> harness;

    @BeforeAll
    static void compileActualReceipt() throws Exception {
        var source = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java"));
        var enumsAndStages = source.substring(source.indexOf("    enum MetadataStage"),
                source.indexOf("    /**\n     * One exact account-root retained continuation."));
        var receipt = declaration(source, "public static final class MetadataContinuation");
        var unit = temporary.resolve("SkillSubmissionRecoveryService.java");
        Files.writeString(unit, """
                import java.util.Objects;
                public final class SkillSubmissionRecoveryService {
                %s
                %s
                    private static final class RecoveryContinuation {}
                    private record RecoveryProjection(Object kind, int entriesCleared,
                            int stepsReplayed, Object exceptionClass) {}
                    static MetadataContinuation ready() {
                        var result = new MetadataContinuation(new SkillSubmissionRecoveryService(),
                                new P4E2OnlineReconciliationDependency(), new ServerPlayer(), new RecoveryContinuation());
                        result.lease = new P11NativeStorageBoundary.MetadataLease();
                        result.stages.recoveryStarted(); result.stages.recoveryCompleted();
                        result.reconciliationStarted(result.dependency);
                        result.reconciliationCompleted(result.dependency, true, new P7ServerAuthorizationBoundary.LoginReadyPort());
                        return result;
                    }
                    static MetadataContinuation opened() {
                        var result = ready(); result.sessionStarted(result.loginPort);
                        result.sessionOpened(result.loginPort, 7, 11); return result;
                    }
                    public static String run(String scenario) {
                        switch (scenario) {
                            case "coordinates" -> {
                                var r = opened();
                                check(r.openedSession(r.loginPort) == 7 && r.openedServerGeneration(r.loginPort) == 11);
                                check(r.matchesSession(r.lease, 7, 11));
                                check(!r.matchesSession(r.lease, 7, 12) && !r.matchesSession(r.lease, 8, 11));
                                check(!r.matchesSession(new P11NativeStorageBoundary.MetadataLease(), 7, 11));
                                boolean rejected = false;
                                try { r.sessionOpened(r.loginPort, 8, 12); } catch (IllegalStateException expected) { rejected = true; }
                                check(rejected && r.openedSession(r.loginPort) == 7 && r.openedServerGeneration(r.loginPort) == 11);
                                rejected = false;
                                try { r.openedServerGeneration(new P7ServerAuthorizationBoundary.LoginReadyPort()); }
                                catch (IllegalStateException expected) { rejected = true; }
                                check(rejected);
                                for (long bad : new long[] {0, -1}) {
                                    var fresh = ready(); fresh.sessionStarted(fresh.loginPort);
                                    rejected = false;
                                    try { fresh.sessionOpened(fresh.loginPort, 1, bad); }
                                    catch (IllegalStateException expected) { rejected = true; }
                                    check(rejected && fresh.sessionEpoch == 0 && fresh.sessionGeneration == 0
                                            && fresh.stages.session == MetadataStage.RUNNING);
                                }
                                var maximum = ready(); maximum.sessionStarted(maximum.loginPort);
                                maximum.sessionOpened(maximum.loginPort, Long.MAX_VALUE, Long.MAX_VALUE);
                                check(maximum.matchesSession(maximum.lease, Long.MAX_VALUE, Long.MAX_VALUE));
                            }
                            case "completion" -> {
                                var r = opened();
                                for (var stage : MetadataInitialStage.values()) {
                                    r.observeInitialSync(r.lease, 7, 12, stage);
                                }
                                check(r.stages.mana == MetadataStage.NOT_STARTED && !r.stages.blocked && r.lease.releases == 0);
                                for (var stage : new MetadataInitialStage[] {MetadataInitialStage.MANA_STARTED,
                                        MetadataInitialStage.MANA_SUBMITTED, MetadataInitialStage.COOLDOWN_STARTED,
                                        MetadataInitialStage.COOLDOWN_SUBMITTED}) {
                                    r.observeInitialSync(r.lease, 7, 11, stage);
                                }
                                check(r.stages.complete() && r.lease.releases == 1 && r.lease.closed);
                            }
                            case "source" -> {
                                var r = opened();
                                check(r.loginActor(r.loginPort, r.player));
                                check(!r.loginActor(r.loginPort, new ServerPlayer()));
                                r.lease.current = false;
                                check(!r.loginActor(r.loginPort, r.player));
                                // A version advance prevents resume but not exact-session completion observation.
                                r.observeInitialSync(r.lease, 7, 11, MetadataInitialStage.MANA_STARTED);
                                check(r.stages.mana == MetadataStage.RUNNING && !r.stages.blocked);
                                r.lease.sessionCurrent = false;
                                r.observeInitialSync(r.lease, 7, 11, MetadataInitialStage.MANA_SUBMITTED);
                                check(r.stages.blocked && r.stages.mana == MetadataStage.RUNNING && r.lease.releases == 0);
                            }
                            default -> throw new AssertionError("unknown scenario");
                        }
                        return "PASS";
                    }
                    static void check(boolean condition) { if (!condition) { throw new AssertionError("receipt contract"); } }
                }
                final class ServerPlayer {}
                final class P7ServerAuthorizationBoundary { static final class LoginReadyPort {} }
                final class P4E2OnlineReconciliationDependency {
                    void resumeMissingStages(ServerPlayer actor, Object continuation, Object kind,
                            int cleared, int replayed, Object failure) {}
                }
                final class P11NativeStorageBoundary {
                    static final class MetadataLease { boolean current = true, sessionCurrent = true, closed; int releases; }
                    static boolean metadataCurrent(MetadataLease lease) {
                        return metadataSessionCurrent(lease) && lease.current;
                    }
                    static boolean metadataSessionCurrent(MetadataLease lease) {
                        return lease != null && !lease.closed && lease.sessionCurrent;
                    }
                    static boolean refreshMetadata(MetadataLease lease) { return metadataCurrent(lease); }
                    static void releaseMetadata(MetadataLease lease) {
                        if (lease != null && !lease.closed) { lease.closed = true; lease.releases++; }
                    }
                }
                """.formatted(enumsAndStages, receipt));
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            assertTrue(Boolean.TRUE.equals(compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-d", temporary.toString()), null,
                    manager.getJavaFileObjectsFromPaths(List.of(unit))).call()),
                    () -> diagnostics.getDiagnostics().toString());
        }
        loader = new URLClassLoader(new java.net.URL[] {temporary.toUri().toURL()},
                ClassLoader.getPlatformClassLoader());
        harness = loader.loadClass("SkillSubmissionRecoveryService");
    }

    @AfterAll
    static void closeLoader() throws Exception { if (loader != null) { loader.close(); } }

    @Test void originalReceiptRequiresBothPositiveCoordinatesAndCannotBeRelabelled() throws Exception { run("coordinates"); }
    @Test void oldGenerationCannotCompleteOrReleaseOriginalInitialFamilies() throws Exception { run("completion"); }
    @Test void exactLeaseActorAndSourceCompletionRulesRemainDistinct() throws Exception { run("source"); }

    private static void run(String scenario) throws Exception {
        assertEquals("PASS", harness.getMethod("run", String.class).invoke(null, scenario));
    }

    private static String declaration(String source, String signature) {
        int start = source.indexOf(signature), open = source.indexOf('{', start), depth = 0;
        if (start < 0 || open < 0) { throw new AssertionError("actual receipt declaration missing"); }
        for (int i = open; i < source.length(); i++) {
            if (source.charAt(i) == '{') { depth++; }
            if (source.charAt(i) == '}' && --depth == 0) { return source.substring(start, i + 1); }
        }
        throw new AssertionError("actual receipt declaration incomplete");
    }

    private static Path projectRoot() {
        for (var path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("settings.gradle"))) { return path; }
        }
        throw new AssertionError("project root unavailable");
    }
}
