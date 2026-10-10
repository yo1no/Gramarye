package com.yo1no.gramarye.magic.network;

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

/** Exact lifecycle method bodies with call-local fault boundaries; no native fault claim. */
final class P7D3LifecycleCleanupTest {
    @TempDir static Path temporary;
    private static URLClassLoader loader;
    private static Class<?> harness;

    @BeforeAll
    static void compileActualCleanup() throws Exception {
        var source = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/magic/network/P7ServerLifecycleCoordinator.java"));
        var methods = new StringBuilder();
        for (var signature : List.of("int stop(", "void start(", "private void discardStoppedState(",
                "private static void suppress(", "private void requireServerThread(")) {
            methods.append(declaration(source, signature)).append('\n');
        }
        var unit = temporary.resolve("LifecycleHarness.java");
        Files.writeString(unit, """
                import java.util.LinkedHashSet;
                public final class LifecycleHarness {
                    final Sessions sessions = new Sessions(); final Gate reloadGate = new Gate();
                    final Queue reconciliation = new Queue(); final Diagnostics diagnostics = new Diagnostics();
                    final Access access = new Access(); boolean stopped = true; long drainTick = -1; int processedThisTick;
                    %s
                    public static String run(String scenario) {
                        if (scenario.equals("stop")) {
                            for (boolean error : new boolean[] {false, true}) {
                                for (boolean ownerFails : new boolean[] {false, true}) {
                                    var h = new LifecycleHarness(); var server = new MinecraftServer();
                                    h.start(server); h.reconciliation.add(1);
                                    Throwable primary = error ? new AssertionError("owner") : new IllegalStateException("owner");
                                    Throwable gate = new IllegalArgumentException("gate");
                                    Throwable queue = new AssertionError("queue");
                                    Throwable diagnostic = new IllegalStateException("diagnostic");
                                    h.sessions.failure = ownerFails ? primary : null;
                                    h.reloadGate.closeFailure = gate; h.reconciliation.failure = queue;
                                    h.diagnostics.failure = diagnostic;
                                    try { h.stop(server); throw new AssertionError("stop failure lost"); }
                                    catch (RuntimeException | Error observed) {
                                        check(observed == (ownerFails ? primary : gate));
                                        var suppressed = observed.getSuppressed();
                                        check(suppressed.length == (ownerFails ? 3 : 2));
                                        check(suppressed[suppressed.length - 2] == queue
                                                && suppressed[suppressed.length - 1] == diagnostic);
                                    }
                                    check(h.stopped && !h.sessions.active && h.reconciliation.isEmpty()
                                            && h.sessions.stops == 1 && h.reloadGate.closes == 1 && h.diagnostics.discards == 2);
                                    check(h.stop(server) == 0 && h.sessions.stops == 1);
                                }
                            }
                        } else if (scenario.equals("start")) {
                            for (boolean resetFails : new boolean[] {false, true}) {
                                for (boolean error : new boolean[] {false, true}) {
                                    var h = new LifecycleHarness(); var server = new MinecraftServer();
                                    Throwable primary = error ? new AssertionError("startup") : new IllegalStateException("startup");
                                    var secondary = new IllegalArgumentException("stop secondary");
                                    h.sessions.failure = secondary;
                                    if (resetFails) { h.reloadGate.resetFailure = primary; }
                                    else { h.diagnostics.failure = primary; }
                                    try { h.start(server); throw new AssertionError("startup failure lost"); }
                                    catch (RuntimeException | Error observed) {
                                        check(observed == primary && observed.getSuppressed().length == 1
                                                && observed.getSuppressed()[0] == secondary);
                                    }
                                    check(h.stopped && !h.sessions.active && h.reconciliation.isEmpty()
                                            && h.sessions.starts == 1 && h.sessions.stops == 1 && h.reloadGate.closes == 1);
                                }
                            }
                        } else { throw new AssertionError("unknown scenario"); }
                        return "PASS";
                    }
                    static void check(boolean value) { if (!value) { throw new AssertionError("cleanup contract"); } }
                    static void raise(Throwable value) {
                        if (value instanceof RuntimeException runtime) { throw runtime; }
                        if (value instanceof Error error) { throw error; }
                    }
                    static final class Sessions {
                        boolean active; int starts, stops; Throwable failure;
                        boolean isCurrentServer(MinecraftServer server) { return active; }
                        void start(MinecraftServer server) { starts++; active = true; }
                        int stop(MinecraftServer server) { stops++; active = false; raise(failure); return 1; }
                    }
                    static final class Gate {
                        Throwable closeFailure, resetFailure; int closes;
                        void reset(MinecraftServer server) { raise(resetFailure); }
                        void close(MinecraftServer server) { closes++; raise(closeFailure); }
                    }
                    static final class Queue extends LinkedHashSet<Integer> {
                        Throwable failure;
                        @Override public void clear() { super.clear(); raise(failure); }
                    }
                    static final class Diagnostics {
                        Throwable failure; int discards;
                        void discard() { discards++; raise(failure); }
                    }
                    static final class Access { boolean sameThread(MinecraftServer server) { return true; } }
                }
                final class MinecraftServer {}
                final class P7NetworkBounds { static final int MAX_SERVER_STOP_CLEANUP_RECORDS = 576; }
                final class P7SemanticInvariantException extends IllegalStateException {
                    P7SemanticInvariantException(String message) { super(message); }
                }
                """.formatted(methods));
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            assertTrue(Boolean.TRUE.equals(compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-d", temporary.toString()), null,
                    manager.getJavaFileObjectsFromPaths(List.of(unit))).call()),
                    () -> diagnostics.getDiagnostics().toString());
        }
        loader = new URLClassLoader(new java.net.URL[] {temporary.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        harness = loader.loadClass("LifecycleHarness");
    }

    @AfterAll static void closeLoader() throws Exception { if (loader != null) { loader.close(); } }
    @Test void stopAttemptsEveryBoundedCleanupWithoutReplacingPrimary() throws Exception { run("stop"); }
    @Test void failedStartupRevokesEmptySlotAndPreservesOriginalFailure() throws Exception { run("start"); }

    private static void run(String scenario) throws Exception {
        assertEquals("PASS", harness.getMethod("run", String.class).invoke(null, scenario));
    }

    private static String declaration(String source, String signature) {
        int start = source.indexOf(signature), open = source.indexOf('{', start), depth = 0;
        if (start < 0 || open < 0) { throw new AssertionError("actual lifecycle method missing"); }
        for (int i = open; i < source.length(); i++) {
            if (source.charAt(i) == '{') { depth++; }
            if (source.charAt(i) == '}' && --depth == 0) { return source.substring(start, i + 1); }
        }
        throw new AssertionError("actual lifecycle method incomplete");
    }

    private static Path projectRoot() {
        for (var path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("settings.gradle"))) { return path; }
        }
        throw new AssertionError("project root unavailable");
    }
}
