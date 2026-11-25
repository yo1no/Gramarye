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

/** Exact shipping method-body fault control over value fakes; not native/readback evidence. */
final class P11CooldownReadbackFailureTest {
    @TempDir static Path temporary;
    private static URLClassLoader loader;
    private static Class<?> control;

    @BeforeAll static void compileExactObservationBody() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) { root = root.getParent(); }
        assertNotNull(root);
        String source = Files.readString(root.resolve("src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java"));
        int start = source.indexOf("    void cooldownPrimaryReadback(");
        assertTrue(start >= 0);
        int open = source.indexOf('{', start), depth = 1, end = open + 1;
        while (depth != 0) {
            char c = source.charAt(end++);
            if (c == '{') { depth++; } else if (c == '}') { depth--; }
        }
        String method = source.substring(start, end);
        Path java = temporary.resolve("Control.java"), classes = Files.createDirectory(temporary.resolve("classes"));
        Files.writeString(java, scaffolding().replace("/* ACTUAL_METHOD */", method), StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            assertTrue(Boolean.TRUE.equals(compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-Xlint:all,-serial,-auxiliaryclass", "-Werror",
                            "-classpath", classes.toString(), "-d", classes.toString()), null,
                    manager.getJavaFileObjects(java)).call()), () -> diagnostics.getDiagnostics().toString());
        }
        loader = new URLClassLoader(new java.net.URL[] {classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        control = loader.loadClass("com.yo1no.gramarye.Control");
    }
    @AfterAll static void closeLoader() throws Exception { if (loader != null) { loader.close(); } }
    private static void run(String test) throws Exception { control.getMethod("run", String.class).invoke(null, test); }

    @Test void successfulOriginalObservationCanRecordProof() throws Exception { run("success"); }
    @Test void capacityRefusalDoesNotChangeTheOriginalOutcome() throws Exception { run("capacity"); }
    @Test void wrongNativeScopeCannotAssertReadSuccess() throws Exception { run("scope"); }
    @Test void changedSourceCannotRebaseAnOldRead() throws Exception { run("stale"); }
    @Test void errorBeforeReceiptIsTheExactPrimary() throws Exception { run("begin-error"); }
    @Test void runtimePrimaryClosesOnlyNewPendingObservation() throws Exception { run("runtime"); }
    @Test void cleanupErrorCannotReplaceTheExactObservationPrimary() throws Exception { run("primary-cleanup"); }
    @Test void cleanupErrorWithoutAPrimaryIsRethrown() throws Exception { run("cleanup-only"); }
    @Test void postSuccessObservationErrorIsNotReportedAsNormal() throws Exception { run("post-error"); }

    private static String scaffolding() { return """
            package com.yo1no.gramarye;
            import java.util.Optional;
            public final class Control {
                public static void run(String test) {
                    var owner = new P11QualifiedSourceOwner(); var body = new P11QualifiedSourceOwner.Body();
                    var writer = new P11ReceiptLedger.PhysicalWriterReceipt();
                    var witness = new P11QualifiedSourceOwner.SelectionWitness();
                    var nativeRead = new P11NativeStorageBoundary.PrimaryReadRequest();
                    var primary = new AssertionError("controlled primary");
                    var cleanup = new AssertionError("controlled cleanup");
                    switch (test) {
                        case "success" -> { }
                        case "capacity" -> owner.receipts.capacity = false;
                        case "scope" -> nativeRead.current = false;
                        case "stale" -> owner.receipts.onBegin = () -> body.source = new P11ReceiptLedger.Source();
                        case "begin-error" -> owner.receipts.beginFailure = primary;
                        case "runtime" -> owner.receipts.primary = new IllegalStateException("controlled primary");
                        case "primary-cleanup" -> { owner.receipts.primary = primary; owner.receipts.cleanup = cleanup; }
                        case "cleanup-only" -> { owner.receipts.refuse = true; owner.receipts.cleanup = cleanup; }
                        case "post-error" -> owner.receipts.postFailure = primary;
                        default -> throw new AssertionError("unknown control");
                    }
                    Throwable caught = null;
                    try { owner.cooldownPrimaryReadback(body, writer, witness, nativeRead); }
                    catch (RuntimeException | Error failure) { caught = failure; }
                    if (test.equals("success")) {
                        check(caught == null && body.cooldownReadback != null && owner.receipts.succeeded == 1, "recorded exact observation");
                    } else {
                        check(body.cooldownReadback == null, "no forged proof");
                        if (test.equals("begin-error") || test.equals("primary-cleanup") || test.equals("post-error")) check(caught == primary, "same primary Error");
                        else if (test.equals("runtime")) check(caught == owner.receipts.primary && owner.receipts.unknown == 1, "same runtime plus UNKNOWN cleanup");
                        else if (test.equals("cleanup-only")) check(caught == cleanup, "same cleanup Error is primary");
                        else check(caught == null, "known unavailability preserves normal");
                        if (test.equals("primary-cleanup")) check(owner.receipts.unknown == 1, "one cleanup attempt");
                        if (test.equals("scope")) check(owner.receipts.began == 0, "no unauthorized observation");
                    }
                    check(nativeRead.originalVerified, "original native outcome untouched");
                }
                static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
            }
            final class P11NativeStorageBoundary {
                static final class PrimaryReadRequest { boolean current = true; final boolean originalVerified = true; }
                static boolean cooldownReadbackScopeCurrent(PrimaryReadRequest read, P11QualifiedSourceOwner owner,
                        P11QualifiedSourceOwner.Body body, P11ReceiptLedger.PhysicalWriterReceipt writer,
                        P11QualifiedSourceOwner.SelectionWitness witness) { return read.current; }
            }
            final class P11QualifiedSourceOwner {
                final P11ReceiptLedger receipts = new P11ReceiptLedger();
                static final class Body { P11ReceiptLedger.Source source = new P11ReceiptLedger.Source(); CooldownReadback cooldownReadback; }
                static final class SelectionWitness { }
                static final class CooldownReadback {
                    CooldownReadback(P11ReceiptLedger.Source source, P11ReceiptLedger.PhysicalWriterReceipt writer,
                            P11ReceiptLedger.ReadbackReceipt readback, SelectionWitness witness) { }
                }
                boolean completedPlayerWrite(Body body, P11ReceiptLedger.PhysicalWriterReceipt writer, SelectionWitness witness) { return true; }
                boolean metadataCurrent(Body body, P11ReceiptLedger.Source source, SelectionWitness witness) { return body.source == source; }
                /* ACTUAL_METHOD */
            }
            final class P11ReceiptLedger {
                enum Observation { NOT_OBSERVED, SUCCEEDED, UNKNOWN }
                enum WriterKind { PLAYER_DATA }
                enum Change { RECORDED, REFUSED }
                static final class Source { }
                static final class PhysicalWriterReceipt { }
                static final class ReadbackReceipt {
                    Observation value = Observation.NOT_OBSERVED;
                    Observation outcome() { return value; }
                }
                boolean capacity = true, refuse;
                int began, succeeded, unknown;
                Throwable beginFailure, primary, cleanup, postFailure;
                Runnable onBegin = () -> {};
                Optional<ReadbackReceipt> beginReadback(Source source, WriterKind kind) {
                    began++; raise(beginFailure); onBegin.run(); return capacity ? Optional.of(new ReadbackReceipt()) : Optional.empty();
                }
                Change finishReadback(ReadbackReceipt receipt, Observation observed) {
                    if (observed == Observation.UNKNOWN) { unknown++; raise(cleanup); receipt.value = observed; return Change.RECORDED; }
                    raise(primary); if (refuse) return Change.REFUSED;
                    succeeded++; receipt.value = observed; return Change.RECORDED;
                }
                boolean persistedReadbackCurrent(ReadbackReceipt receipt) { raise(postFailure); return receipt.value == Observation.SUCCEEDED; }
                static void raise(Throwable fault) {
                    if (fault instanceof RuntimeException failure) throw failure;
                    if (fault instanceof Error failure) throw failure;
                }
            }
            """; }
}
