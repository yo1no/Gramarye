package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Direct production observation-state tests; native producer evidence is separate. */
final class P11NativeContextFactsTest {
    @Test
    void emptyDrainAndTracerCloseDoNotReleaseOuterContext() {
        var facts = new P11NativeContextFacts();
        facts.started();
        facts.drained(true, 10, true, true);
        facts.tracerClosed();
        assertEquals(P11NativeContextFacts.Drain.EMPTY, facts.drain());
        assertTrue(facts.tracerDidClose());
        assertFalse(facts.terminal());
        assertTrue(facts.outerFinished(true));
        assertTrue(facts.terminal());
        assertTrue(facts.normal());
        assertFalse(facts.outerFinished(false));
        assertTrue(facts.normal());
    }

    @Test
    void quotaIsNativeTerminalDispositionEvenWithRemainingCommands() {
        var facts = new P11NativeContextFacts();
        facts.started();
        facts.drained(true, 0, false, true);
        assertEquals(P11NativeContextFacts.Drain.QUOTA, facts.drain());
        assertFalse(facts.terminal());
        facts.outerFinished(true);
        assertTrue(facts.normal());
    }

    @Test
    void clearedQueuesAfterOverflowAreNotSuccessfulFullDrain() {
        var facts = new P11NativeContextFacts();
        facts.started();
        facts.overflow();
        facts.drained(true, 20, true, true);
        assertEquals(P11NativeContextFacts.Drain.OVERFLOW, facts.drain());
        assertFalse(facts.terminal());
    }

    @Test
    void discardIsFrameLocalAndDoesNotFinishOtherCommands() {
        var facts = new P11NativeContextFacts();
        facts.started();
        facts.discarded();
        facts.discarded();
        assertEquals(2, facts.discardedFrames());
        assertEquals(P11NativeContextFacts.Drain.RUNNING, facts.drain());
        assertFalse(facts.terminal());
    }

    @Test
    void throwAndTracerCompletionCannotBecomeSuccessfulDrain() {
        var facts = new P11NativeContextFacts();
        facts.started();
        facts.drained(false, 9, false, true);
        facts.tracerClosed();
        facts.outerFinished(false);
        assertEquals(P11NativeContextFacts.Drain.THREW, facts.drain());
        assertFalse(facts.normal());
        facts.drained(true, 9, true, true);
        assertEquals(P11NativeContextFacts.Drain.THREW, facts.drain());
    }

    @Test
    void consumerFailureBeforeDrainStillHasOuterFailureTerminal() {
        var facts = new P11NativeContextFacts();
        facts.tracerClosed();
        assertFalse(facts.terminal());
        facts.outerFinished(false);
        assertEquals(P11NativeContextFacts.Drain.NOT_RUN, facts.drain());
        assertFalse(facts.normal());
    }

    @Test
    void bothTrueNativeTerminalsInvalidatePrefixOnlySaveBeforeReleasingTheirOwnRoot() throws Exception {
        String source = boundarySource();
        String end = source.substring(source.indexOf("public static void end(OperationScope"),
                source.indexOf("public static Credit acquireCredit"));
        assertOperationTerminal(end);
        assertThrows(AssertionError.class, () -> assertOperationTerminal(
                end.replace("afterNative(scope.binding);", "")));
        assertThrows(AssertionError.class, () -> assertOperationTerminal(
                end.replace("afterNative(scope.binding);", "if (!normal) { afterNative(scope.binding); }")));
        String context = source.substring(source.indexOf("private static void finishContext("),
                source.indexOf("private static void afterNative("));
        assertContextTerminal(context);
        assertThrows(AssertionError.class, () -> assertContextTerminal(
                context.replace("if (!context.facts.outerFinished(normal)) { return; }", "")));
        assertThrows(AssertionError.class, () -> assertContextTerminal(
                context.replace("afterNative(binding);", "")));
    }

    @Test
    void postNativeObservationIsFixedBodyOnlyAndSecondaryCannotReplaceNativePrimary() throws Exception {
        String source = boundarySource();
        String observer = source.substring(source.indexOf("private static void afterNative("),
                source.indexOf("private static void release("));
        assertTrue(observer.contains("try { binding.owner.nativeMutation(binding.body); }"));
        assertTrue(observer.contains("catch (RuntimeException | Error secondary) { observerFailed(); }"));
        assertFalse(observer.contains("throw ") || observer.contains("getUUID") || observer.contains("nativeRecipient"));
        String owner = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java"));
        String mutation = owner.substring(owner.indexOf("void nativeMutation(Body body)"),
                owner.indexOf("void nativeEscape("));
        assertTrue(mutation.contains("body(body.actor) != body"));
        assertTrue(mutation.contains("publication(body.actor, body.source.epoch(), body.source.version())"));
    }

    private static void assertOperationTerminal(String source) {
        assertOrdered(source, "if (scope == null || scope.closed) { return; }", "scope.closed = true;",
                "if (OPERATION.get() != scope) { observerFailed(); return; }", "OPERATION.set(scope.previous);",
                "afterNative(scope.binding);", "if (!normal)", "nativeOperationFailed(scope.binding.body)",
                "finally { release(scope.binding, P11ControlBudgets.Root.OPERATION); }");
        assertTrue(source.contains("afterNative(scope.binding);\n        try {"));
    }

    private static void assertContextTerminal(String source) {
        assertOrdered(source, "if (!context.facts.outerFinished(normal)) { return; }", "for (var binding : context.bindings)",
                "afterNative(binding);", "if (!normal)", "nativeOperationFailed(binding.body)",
                "finally { release(binding, P11ControlBudgets.Root.COMMAND_CONTEXT); }", "context.bindings.clear();");
        assertTrue(source.contains("afterNative(binding);\n            try {"));
    }

    private static void assertOrdered(String source, String... values) {
        int cursor = 0;
        for (var value : values) {
            int found = source.indexOf(value, cursor);
            assertTrue(found >= cursor, value);
            cursor = found + value.length();
        }
    }

    private static String boundarySource() throws Exception {
        return Files.readString(projectRoot().resolve("src/main/java/com/yo1no/gramarye/P11NativeOperationBoundary.java"));
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("build.gradle"))) { current = current.getParent(); }
        if (current == null) { throw new IllegalStateException("project root not found"); }
        return current;
    }
}
