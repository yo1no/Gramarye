package com.yo1no.gramarye.magic.definition.store;

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

/** Warning-free negative compilation proof for the two P4-E2 nominal authorities. */
final class P4E2VisibilityCompileTest {
    @TempDir
    Path temporary;

    @Test
    void unrelatedPackageCannotSubclassTheOnlineReconciliationDependency()
            throws IOException {
        assertRejectedWithDiagnostics(
                List.of("compiler.err.cant.inherit.from.sealed"),
                "outside/EvilDependency.java", """
                package outside;

                import com.yo1no.gramarye.magic.definition.store
                        .P4E2OnlineReconciliationDependency;
                import com.yo1no.gramarye.magic.definition.submission
                        .SkillSubmissionRecoveryService;
                import java.util.Optional;
                import net.minecraft.server.level.ServerPlayer;

                public final class EvilDependency
                        implements P4E2OnlineReconciliationDependency {
                    @Override
                    public void reconcileAfterRecovery(
                            ServerPlayer player,
                            SkillSubmissionRecoveryService.RecoveryContinuation continuation,
                            RecoveryKind kind,
                            int entriesCleared,
                            int stepsReplayed,
                            Optional<String> existingExceptionClass) {
                    }
                }
                """);
    }

    @Test
    void unrelatedPackageCannotSubclassThePlayerReconciliationCapability()
            throws IOException {
        assertRejectedWithDiagnostics(
                List.of("compiler.err.cant.inherit.from.sealed"),
                "outside/EvilCapability.java", """
                package outside;

                import com.yo1no.gramarye.magic.definition.store
                        .PlayerSkillAttachmentReconciliationCapability;

                public final class EvilCapability
                        extends PlayerSkillAttachmentReconciliationCapability<Object, Object> {
                }
                """);
    }

    @Test
    void externalCodeCannotConstructTheOpaqueRecoveryContinuation() throws IOException {
        assertRejected("outside/EvilContinuation.java", """
                package outside;

                import com.yo1no.gramarye.magic.definition.submission
                        .SkillSubmissionRecoveryService;

                public final class EvilContinuation {
                    Object construct() {
                        return new SkillSubmissionRecoveryService.RecoveryContinuation();
                    }
                }
                """);
    }

    @Test
    void externalCodeCannotNameOrConstructThePackageOwnedResult() throws IOException {
        assertRejected("outside/EvilResult.java", """
                package outside;

                import com.yo1no.gramarye.magic.definition.store.P4E2ReconciliationResult;

                public final class EvilResult {
                    P4E2ReconciliationResult value;

                    Object construct() {
                        return new P4E2ReconciliationResult.NoChanges(null);
                    }
                }
                """);
    }

    @Test
    void externalCodeCannotConstructEitherExactNominalAuthority() throws IOException {
        assertRejected("outside/EvilConstruction.java", """
                package outside;

                import com.yo1no.gramarye.magic.definition.store
                        .P4E2OnlineReconciliationDependency;
                import com.yo1no.gramarye.magic.definition.store
                        .PlayerSkillAttachmentReconciliationCapability;

                public final class EvilConstruction {
                    Object dependency() {
                        return new P4E2OnlineReconciliationDependency();
                    }

                    Object capability() {
                        return new PlayerSkillAttachmentReconciliationCapability<Object, Object>();
                    }
                }
                """);
    }

    @Test
    void externalCodeCannotConstructOrImplementTheExactPlayerView() throws IOException {
        assertRejected("outside/EvilPlayerView.java", """
                package outside;

                import com.yo1no.gramarye.P4E2QualificationFacade;

                public final class EvilPlayerView {
                    Object construct() {
                        return new P4E2QualificationFacade.PlayerView(null) {
                        };
                    }
                }
                """);
    }

    private void assertRejected(String relativePath, String source) throws IOException {
        assertRejectedWithDiagnostics(List.of(), relativePath, source);
    }

    private void assertRejectedWithDiagnostics(
            List<String> expectedDiagnostics,
            String relativePath,
            String source) throws IOException {
        var result = compile(relativePath, source);
        assertFalse(result.success(), "negative compile probe unexpectedly succeeded");
        assertTrue(result.classCount() == 0,
                () -> "negative probe emitted class output: " + result.classCount());
        assertFalse(result.diagnostics().isBlank(), "negative probe had no compiler diagnostic");
        for (var expected : expectedDiagnostics) {
            assertTrue(result.diagnostics().contains(expected),
                    () -> "missing compiler diagnostic " + expected + ": "
                            + result.diagnostics());
        }
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
