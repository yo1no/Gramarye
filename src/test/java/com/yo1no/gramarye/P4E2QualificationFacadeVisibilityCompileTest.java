package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Warning-free compile-negative proof for the A0.3 facade boundary. */
final class P4E2QualificationFacadeVisibilityCompileTest {
    @TempDir
    Path temporary;

    @Test
    void externalCodeCannotConstructFacade() throws IOException {
        assertRejected("outside/ExternalConstructor.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalConstructor {
                    private final P4E2QualificationFacade facade =
                            new P4E2QualificationFacade();
                }
                """);
    }

    @Test
    void externalCodeCannotArm() throws IOException {
        assertRejected("outside/ExternalArm.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalArm {
                    void attack(P4E2QualificationFacade facade) {
                        facade.arm(null, 1L, 2L, 3L, null);
                    }
                }
                """);
    }

    @Test
    void externalCodeCannotConsume() throws IOException {
        assertRejected("outside/ExternalConsume.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalConsume {
                    void attack(P4E2QualificationFacade facade) {
                        facade.consume(null);
                    }
                }
                """);
    }

    @Test
    void externalCodeCannotDiscard() throws IOException {
        assertRejected("outside/ExternalDiscard.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalDiscard {
                    void attack(P4E2QualificationFacade facade) {
                        facade.discard(null);
                    }
                }
                """);
    }

    @Test
    void externalCodeCannotRetrieveOwnerBoundViews() throws IOException {
        assertRejected("outside/ExternalViewAccessor.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalViewAccessor {
                    P4E2QualificationFacade.SubmissionView attack(
                            P4E2QualificationFacade facade) {
                        return facade.submissionView();
                    }
                }
                """);
    }

    @Test
    void externalCodeCannotConstructView() throws IOException {
        assertRejected("outside/ViewConstruction.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ViewConstruction {
                    private final P4E2QualificationFacade.SubmissionView forged =
                            new P4E2QualificationFacade.SubmissionView(null) {
                            };
                }
                """);
    }

    @Test
    void externalCodeCannotSubclassSealedStoreView() throws IOException {
        assertRejected("outside/SealedViewSubclass.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class SealedViewSubclass
                        extends P4E2QualificationFacade.StoreView {
                }
                """);
    }

    @Test
    void externalEvilViewCannotSubclassPlayerView() throws IOException {
        assertRejected("outside/EvilView.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class EvilView extends P4E2QualificationFacade.PlayerView {
                }
                """);
    }

    @Test
    void externalEvilSessionCannotNameInternalSession() throws IOException {
        assertRejected("outside/EvilSession.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class EvilSession {
                    private P4E2QualificationFacade.Session stolen;
                }
                """);
    }

    @Test
    void externalCodeCannotNameInternalSnapshot() throws IOException {
        assertRejected("outside/ExternalSnapshot.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalSnapshot {
                    private P4E2QualificationFacade.Snapshot stolen;
                }
                """);
    }

    @Test
    void externalCodeCannotArmE3Startup() throws IOException {
        assertRejected("outside/ExternalE3Arm.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalE3Arm {
                    void attack(P4E2QualificationFacade facade) {
                        facade.armE3Startup(null);
                    }
                }
                """);
    }

    @Test
    void externalCodeCannotClaimE3Startup() throws IOException {
        assertRejected("outside/ExternalE3Claim.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalE3Claim {
                    void attack(P4E2QualificationFacade facade) {
                        facade.claimE3Startup(null);
                    }
                }
                """);
    }

    @Test
    void externalCodeCannotConsumeE3Startup() throws IOException {
        assertRejected("outside/ExternalE3Consume.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalE3Consume {
                    void attack(P4E2QualificationFacade facade) {
                        facade.consumeE3Startup(null, null);
                    }
                }
                """);
    }

    @Test
    void externalCodeCannotAbortE3Startup() throws IOException {
        assertRejected("outside/ExternalE3Abort.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalE3Abort {
                    void attack(P4E2QualificationFacade facade) {
                        facade.abortE3Startup(null, null);
                    }
                }
                """);
    }

    @Test
    void externalCodeCannotNameE3Session() throws IOException {
        assertRejected("outside/ExternalE3Session.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalE3Session {
                    private P4E2QualificationFacade.E3StartupSession stolen;
                }
                """);
    }

    @Test
    void externalCodeCannotNameE3Snapshot() throws IOException {
        assertRejected("outside/ExternalE3Snapshot.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalE3Snapshot {
                    private P4E2QualificationFacade.E3StartupSnapshot stolen;
                }
                """);
    }

    @Test
    void externalCodeCannotConstructOrExtendE3View() throws IOException {
        assertRejected("outside/ExternalE3View.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalE3View
                        extends P4E2QualificationFacade.E3StartupView {
                    ExternalE3View(P4E2QualificationFacade owner) {
                        super(owner);
                    }
                }
                """);
    }

    @Test
    void externalE3ViewHasNoRawAuthorityGetter() throws IOException {
        assertRejected("outside/ExternalE3RawGetter.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class ExternalE3RawGetter {
                    Object attack(P4E2QualificationFacade.StoreView view) {
                        return view.e3StartupView().service();
                    }
                }
                """);
    }

    private void assertRejected(String relativePath, String source) throws IOException {
        var result = compile(relativePath, source);
        assertFalse(result.success(), "negative compile probe unexpectedly succeeded");
        assertTrue(result.classCount() == 0,
                () -> "negative probe emitted class output: " + result.classCount());
        assertFalse(result.diagnostics().isBlank(), "negative probe had no compiler diagnostic");
    }

    private CompileResult compile(String relativePath, String source) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Java 21 system compiler unavailable");
        var sourceRoot = Files.createDirectories(temporary.resolve(
                Integer.toUnsignedString(relativePath.hashCode())).resolve("source"));
        var outputRoot = Files.createDirectories(sourceRoot.getParent().resolve("classes"));
        var path = sourceRoot.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source, StandardCharsets.UTF_8);

        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        boolean success;
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, java.util.Locale.ROOT, StandardCharsets.UTF_8)) {
            var units = files.getJavaFileObjectsFromPaths(List.of(path));
            var options = List.of(
                    "--release", "21",
                    "-proc:none",
                    "-Xlint:all",
                    "-Werror",
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", outputRoot.toString());
            success = Boolean.TRUE.equals(compiler.getTask(
                    null, files, diagnostics, options, null, units).call());
        }

        long classCount;
        try (var stream = Files.walk(outputRoot)) {
            classCount = stream.filter(candidate -> candidate.toString().endsWith(".class"))
                    .count();
        }
        var boundedDiagnostics = diagnostics.getDiagnostics().stream()
                .limit(8)
                .map(diagnostic -> diagnostic.getKind() + ":" + diagnostic.getCode())
                .collect(java.util.stream.Collectors.joining(","));
        return new CompileResult(success, classCount, boundedDiagnostics);
    }

    private record CompileResult(boolean success, long classCount, String diagnostics) {
    }
}
