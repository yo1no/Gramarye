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

final class P4E1A1VisibilityCompileTest {
    @TempDir
    Path temporary;

    @Test
    void unrelatedPackageCannotSubclassTheSealedExactSource() throws IOException {
        assertRejectedWithDiagnostics(
                List.of(
                        "compiler.err.cant.inherit.from.sealed",
                        "compiler.err.not.def.public.cant.access"),
                "outside/EvilExactSource.java", """
                package outside;

                import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
                import com.yo1no.gramarye.magic.definition.store.PlayerSkillAttachmentAdmissionSource;

                public final class EvilExactSource
                        extends PlayerSkillAttachmentAdmissionSource<Object, Object> {
                    public EvilExactSource(PlayerSkillAttachmentService service) {
                        super(service, new Object(), new Object(), 1L, new Object(), new Object());
                    }
                }
                """);
    }

    @Test
    void genericOpaqueSubclassCompilesButCannotBePassedToAdmission() throws IOException {
        assertAccepted("outside/AllowedOpaque.java", """
                package outside;

                import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;

                public final class AllowedOpaque
                        extends PlayerSkillAttachmentService.OpaqueAdmissionSource<Object, Object> {
                    public AllowedOpaque() {
                        super(null, new Object(), new Object(), 1L, new Object(), new Object());
                    }
                }
                """);

        assertRejected("outside/EvilInvocation.java", """
                package outside;

                import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;

                public final class EvilInvocation {
                    void invoke(PlayerSkillAttachmentService service, AllowedOpaque source) {
                        service.admitForRootAudit(source);
                    }
                }
                """, "outside/AllowedOpaque.java", """
                package outside;

                import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;

                public final class AllowedOpaque
                        extends PlayerSkillAttachmentService.OpaqueAdmissionSource<Object, Object> {
                    public AllowedOpaque() {
                        super(null, new Object(), new Object(), 1L, new Object(), new Object());
                    }
                }
                """);
    }

    @Test
    void rawExactSourceCannotBypassTheWarningFreeGate() throws IOException {
        assertRejected("outside/RawInvocation.java", """
                package outside;

                import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
                import com.yo1no.gramarye.magic.definition.store.PlayerSkillAttachmentAdmissionSource;

                public final class RawInvocation {
                    void invoke(
                            PlayerSkillAttachmentService service,
                            PlayerSkillAttachmentAdmissionSource candidate) {
                        service.admitForRootAudit(candidate);
                    }
                }
                """);
    }

    private void assertAccepted(String relativePath, String source) throws IOException {
        var result = compile(List.of(new Probe(relativePath, source)), "accepted");
        assertTrue(result.success(), () -> "expected probe to compile: " + result.diagnostics());
        assertTrue(result.classCount() > 0, "accepted probe emitted no class");
    }

    private void assertRejected(String relativePath, String source, String... additional)
            throws IOException {
        assertRejectedWithDiagnostics(List.of(), relativePath, source, additional);
    }

    private void assertRejectedWithDiagnostics(
            List<String> expectedDiagnostics,
            String relativePath,
            String source,
            String... additional) throws IOException {
        var probes = new java.util.ArrayList<Probe>();
        probes.add(new Probe(relativePath, source));
        for (var index = 0; index < additional.length; index += 2) {
            probes.add(new Probe(additional[index], additional[index + 1]));
        }
        var result = compile(probes, "rejected-" + Integer.toUnsignedString(relativePath.hashCode()));
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

    private CompileResult compile(List<Probe> probes, String name) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Java 21 system compiler unavailable");
        var sourceRoot = Files.createDirectories(temporary.resolve(name).resolve("source"));
        var outputRoot = Files.createDirectories(temporary.resolve(name).resolve("classes"));
        var paths = new java.util.ArrayList<Path>();
        for (var probe : probes) {
            var path = sourceRoot.resolve(probe.relativePath());
            Files.createDirectories(path.getParent());
            Files.writeString(path, probe.source(), StandardCharsets.UTF_8);
            paths.add(path);
        }

        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        boolean success;
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, java.util.Locale.ROOT, StandardCharsets.UTF_8)) {
            var units = files.getJavaFileObjectsFromPaths(paths);
            var options = List.of(
                    "--release", "21",
                    "-Xlint:all",
                    "-Werror",
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", outputRoot.toString());
            success = Boolean.TRUE.equals(compiler.getTask(
                    null, files, diagnostics, options, null, units).call());
        }
        long classCount;
        try (var stream = Files.walk(outputRoot)) {
            classCount = stream.filter(path -> path.toString().endsWith(".class")).count();
        }
        var boundedDiagnostics = diagnostics.getDiagnostics().stream()
                .limit(8)
                .map(diagnostic -> diagnostic.getKind() + ":" + diagnostic.getCode())
                .collect(java.util.stream.Collectors.joining(","));
        return new CompileResult(success, classCount, boundedDiagnostics);
    }

    private record Probe(String relativePath, String source) {
    }

    private record CompileResult(boolean success, long classCount, String diagnostics) {
    }
}
