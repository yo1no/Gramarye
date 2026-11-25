package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

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

/** Exact scalar receipt and cleanup bodies; these controls do not prove native ARM. */
final class U0NormalRuntimeReceiptTest {
    @TempDir static Path temporary;
    private static URLClassLoader loader;
    private static Class<?> control;

    @BeforeAll static void compileActualBodies() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) { root = root.getParent(); }
        assertNotNull(root);
        String source = Files.readString(root.resolve("src/main/java/com/yo1no/gramarye/V0SlotRuntime.java"));
        StringBuilder bodies = new StringBuilder();
        for (String anchor : List.of("    private static final class V0ReleaseReceipt",
                "    private static void suppress(", "    private static void throwUnchecked(",
                "    private void undoReservation(", "    private void undoCandidate(")) {
            int start = source.indexOf(anchor); assertTrue(start >= 0, anchor);
            int open = source.indexOf('{', start), end = open + 1, depth = 1;
            while (depth > 0) {
                char c = source.charAt(end++);
                if (c == '{') { depth++; } else if (c == '}') { depth--; }
            }
            bodies.append(source, start, end).append('\n');
        }
        Path java = temporary.resolve("Control.java"), classes = Files.createDirectory(temporary.resolve("classes"));
        Files.writeString(java, scaffolding().replace("/* ACTUAL_BODIES */", bodies), StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            assertTrue(Boolean.TRUE.equals(compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-Xlint:all,-serial,-auxiliaryclass", "-Werror",
                            "-classpath", classes.toString(), "-d", classes.toString()), null,
                    manager.getJavaFileObjects(java)).call()), () -> diagnostics.getDiagnostics().toString());
        }
        loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        control = loader.loadClass("com.yo1no.gramarye.Control");
    }

    @AfterAll static void closeLoader() throws Exception { if (loader != null) { loader.close(); } }
    private static void run(String name) throws Exception { control.getMethod("run", String.class).invoke(null, name); }
    @Test void actualArmFactSurvivesLaterRevokeAndFailure() throws Exception { run("arm"); }
    @Test void publicationUnknownIsNotArmableOrRetryable() throws Exception { run("unknown"); }
    @Test void knownNoReleaseIsIrreversible() throws Exception { run("no-release"); }
    @Test void prepublicationRevocationCannotLaterPublish() throws Exception { run("revoke"); }
    @Test void queueCleanupPrimarySurvivesBothSubsequentCleanupFailures() throws Exception { run("queue"); }
    @Test void workCleanupPrimarySurvivesCandidateCleanupFailure() throws Exception { run("work"); }

    private static String scaffolding() { return """
            package com.yo1no.gramarye;
            import java.util.UUID;
            public final class Control {
                final Queue queue = new Queue();
                V0RuntimeCatalog.Candidate pendingDiscard;
                Error discardFailure;
                int discards;
                boolean settleDiscard(V0RuntimeCatalog.Candidate candidate) {
                    discards++;
                    if (discardFailure != null) throw discardFailure;
                    return true;
                }
                /* ACTUAL_BODIES */
                public static void run(String name) {
                    var r = new V0ReleaseReceipt();
                    if (name.equals("arm")) {
                        check(!r.mayArm(), "unpublished"); r.beginPublication(); r.published();
                        check(r.mayArm(), "pending"); r.armed(17); r.revoke(); r.unknown();
                        check(r.fact() == P11CastCooldownService.ReleaseFact.ARM && r.releasedAt() == 17 && !r.mayArm(), "monotonic ARM");
                    } else if (name.equals("unknown")) {
                        r.beginPublication(); r.unknown();
                        check(r.fact() == P11CastCooldownService.ReleaseFact.UNKNOWN && !r.mayArm(), "unknown stays unknown");
                        boolean refused = false; try { r.beginPublication(); } catch (IllegalStateException expected) { refused = true; }
                        check(refused, "no retry");
                    } else if (name.equals("no-release") || name.equals("revoke")) {
                        if (name.equals("no-release")) { r.beginPublication(); r.published(); }
                        r.revoke(); r.unknown();
                        check(r.fact() == P11CastCooldownService.ReleaseFact.NO_RELEASE && !r.mayArm() && r.releasedAt() == -1, "irreversible no-release");
                        boolean refused = false; try { r.beginPublication(); } catch (IllegalStateException expected) { refused = true; }
                        check(refused, "revoked cannot publish");
                    } else {
                        var owner = new Control(); var work = new P11QualifiedSourceOwner.WorkReservation();
                        var first = new AssertionError("first"); var second = new AssertionError("second");
                        var third = new AssertionError("third");
                        if (name.equals("queue")) { owner.queue.failure = first; work.failure = second; owner.discardFailure = third; }
                        else if (name.equals("work")) { work.failure = first; owner.discardFailure = second; }
                        else throw new AssertionError("unknown control");
                        Throwable caught = null;
                        try { owner.undoReservation(0, 1, new V0RuntimeCatalog.Candidate(), work); }
                        catch (RuntimeException | Error failure) { caught = failure; }
                        check(caught == first, "same first Error");
                        check(owner.queue.calls == 1 && work.calls == 1 && owner.discards == 1, "all cleanups exactly once");
                        check(first.getSuppressed().length == 1 && first.getSuppressed()[0] == second, "secondary retained");
                        if (name.equals("queue")) check(second.getSuppressed().length == 1 && second.getSuppressed()[0] == third, "third retained");
                    }
                }
                static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
                static final class Queue {
                    Error failure; int calls;
                    boolean rollback(int cell, long lineage) { calls++; if (failure != null) throw failure; return true; }
                }
            }
            final class P11CastCooldownService {
                enum ReleaseFact { UNPUBLISHED, PENDING, NO_RELEASE, ARM, UNKNOWN }
                interface ReleaseReceipt { UUID attemptId(); ReleaseFact fact(); long releasedAt(); }
            }
            final class P11QualifiedSourceOwner {
                static final class WorkReservation { Error failure; int calls; void release() { calls++; if (failure != null) throw failure; } }
            }
            final class V0RuntimeCatalog { static final class Candidate { } }
            """; }
}
