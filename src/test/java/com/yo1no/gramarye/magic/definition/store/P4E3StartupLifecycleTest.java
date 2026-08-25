package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exact source-order and once-only Gate for the synchronous P4-E3 lifecycle composition. */
final class P4E3StartupLifecycleTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path STORE_SERVICE = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye/magic/definition/store/"
                    + "SkillDefinitionStoreService.java");

    @Test
    void startingAndStoppedHandlersKeepTheOneExistingSynchronousLifecycle() throws Exception {
        var source = Files.readString(STORE_SERVICE);
        var registration = methodBody(source, "registerLifecycleListeners");
        var starting = methodBody(source, "onServerStarting");
        var stopped = methodBody(source, "onServerStopped");

        assertEquals(2, occurrences(registration, "gameBus.addListener("));
        assertEquals(1, occurrences(registration, "this::onServerStarting"));
        assertEquals(1, occurrences(registration, "this::onServerStopped"));
        assertEquals(1, occurrences(starting, "event.getServer()"));
        assertOrdered(
                starting,
                "var server = event.getServer()",
                "install(server)",
                "submissionPort.bootstrapJournal(server)",
                "runP4E3StartupReclaim(server)");
        assertEquals(1, occurrences(stopped, "event.getServer()"));
        assertOrdered(
                stopped,
                "var server = event.getServer()",
                "qualificationStoreView.clearOnServerStopped()",
                "qualificationStoreView.e3StartupView().clearOnServerStopped(server)",
                "rootAuditService.removeServer(server)",
                "uninstall(server)");
        assertEquals(1, occurrences(
                stopped, "qualificationStoreView.clearOnServerStopped()"));
        assertEquals(1, occurrences(
                stopped,
                "qualificationStoreView.e3StartupView().clearOnServerStopped(server)"));
    }

    @Test
    void startupCompositionIsCompleteOnlyAndEveryOperationIsAtMostOnce() throws Exception {
        var body = methodBody(Files.readString(STORE_SERVICE), "runP4E3StartupReclaim");

        assertOrdered(
                body,
                "qualificationStoreView.e3StartupView()",
                "observationView.beginRecording(server)",
                "observationView.recordAuditInvocation(server)",
                "rootAuditService.audit(server)",
                "observationView.recordAuditResult(",
                "auditResult instanceof SkillRetentionRootAuditResult.Complete complete",
                "observationView.completeRecording(server)",
                "return;",
                "observationView.recordCompleteConsumeInvocation(server)",
                "rootAuditService.consumeComplete(server, complete)",
                "observationView.recordSnapshotInvocation(server)",
                "SkillRetentionRootSnapshot.fromCompleteRoots(handoff)",
                "observationView.recordSnapshotResult(",
                "snapshotResult instanceof SkillRetentionRootSnapshot.Complete snapshot",
                "observationView.recordReclaimInvocation(server, exactAdapter.isDirty())",
                "this.reclaim(server, snapshot)",
                "reclaimResult",
                "SkillSubsystemResult.Available<SkillReclaimResult>",
                "SkillReclaimResult.Completed completed",
                "completed.report().revisionsReclaimed() == 0",
                "sourceUnchanged = true",
                "recordP4E3ReclaimResult(observationView, server, reclaimResult)",
                "observationView.recordDirtyAfter(server, exactAdapter.isDirty())",
                "normalTerminal = true");

        for (var invocation : List.of(
                "rootAuditService.audit(server)",
                "rootAuditService.consumeComplete(server, complete)",
                "SkillRetentionRootSnapshot.fromCompleteRoots(handoff)",
                "this.reclaim(server, snapshot)",
                "handoff.markStoreSourceUnchanged()",
                "handoff.close()",
                "rootAuditService.observeP4E3IndexTerminal(server)")) {
            assertEquals(1, occurrences(body, invocation), invocation);
        }
        assertEquals(0, occurrences(body, "for ("));
        assertEquals(0, occurrences(body, "while ("));
    }

    @Test
    void manualNestedFinallyKeepsFailureDefaultAndPrimaryThrowablePrecedence() throws Exception {
        var body = methodBody(Files.readString(STORE_SERVICE), "runP4E3StartupReclaim");

        assertOrdered(
                body,
                "var recording = observationView != null && observationView.beginRecording(server)",
                "try {",
                "var normalTerminal = false",
                "var sourceUnchanged = false",
                "try {",
                "normalTerminal = true",
                "} finally {",
                "try {",
                "if (normalTerminal && sourceUnchanged)",
                "handoff.markStoreSourceUnchanged()",
                "} finally {",
                "handoff.close()",
                "rootAuditService.observeP4E3IndexTerminal(server)",
                "observationView.recordIndexTerminal(",
                "observationView.completeRecording(server)",
                "catch (RuntimeException | Error failure)",
                "observationView.abortRecording(server)",
                "throw failure");
        assertEquals(3, occurrences(body, "try {"));
        assertEquals(2, occurrences(body, "} finally {"));
        assertFalse(body.contains("try ("));
        assertEquals(1, occurrences(body, "catch (RuntimeException | Error failure)"));
        for (var forbidden : List.of(
                "catch (Throwable",
                "catch (OutOfMemoryError",
                "addSuppressed(",
                "CompletableFuture",
                "ExecutorService",
                "Executors.",
                "parallelStream(",
                "new Thread(")) {
            assertFalse(body.contains(forbidden), forbidden);
        }
    }

    @Test
    void startupCompositionDoesNotOwnAnyAdditionalPersistenceOrWorldMutation() throws Exception {
        var body = methodBody(Files.readString(STORE_SERVICE), "runP4E3StartupReclaim");

        for (var forbidden : List.of(
                "bootstrapJournal(",
                "setData(",
                "setDirty(",
                "Files.",
                "NbtIo.",
                "DataFixer",
                "setChunkForced(",
                "getChunk(",
                "invalidateForReconciliation(",
                "removeServer(",
                "uninstall(")) {
            assertFalse(body.contains(forbidden), forbidden);
        }
        assertTrue(body.contains("normalTerminal = true"));
        assertTrue(body.contains("completed.report().revisionsReclaimed() == 0"));
    }

    private static String methodBody(String source, String methodName) {
        var signature = source.indexOf("private void " + methodName + "(");
        if (signature < 0) {
            throw new AssertionError("method not found: " + methodName);
        }
        var open = source.indexOf('{', signature);
        var depth = 0;
        for (var index = open; index < source.length(); index++) {
            var character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(open + 1, index);
            }
        }
        throw new AssertionError("method body did not close: " + methodName);
    }

    private static void assertOrdered(String source, String... fragments) {
        var previous = -1;
        for (var fragment : fragments) {
            var current = source.indexOf(fragment, previous + 1);
            assertTrue(current >= 0, "missing ordered fragment: " + fragment);
            assertTrue(current > previous, "out-of-order fragment: " + fragment);
            previous = current;
        }
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        for (var index = source.indexOf(fragment);
                index >= 0;
                index = source.indexOf(fragment, index + fragment.length())) {
            count++;
        }
        return count;
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root not found");
    }
}
