package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Direct positive and negative controls for the P9-S3 GameTest worker-surface consumer. */
final class P9S3DirectConsumerContractTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path VERIFIER = PROJECT_ROOT.resolve(
            "scripts/verify-p7-s4-source-contracts.sh");
    private static final String LOGICAL_P9_SOURCE =
            "src/main/java/com/yo1no/gramarye/P9S3ProjectileGameTests.java";
    private static final String LOGICAL_DC1_TEST =
            "src/test/java/com/yo1no/gramarye/P9S3DirectConsumerContractTest.java";
    private static final List<String> LOGICAL_P9_S4_PATHS = List.of(
            "src/main/java/com/yo1no/gramarye/P8AppliedFactHandoff.java",
            "src/main/java/com/yo1no/gramarye/P8ServerPresentationService.java",
            "src/test/java/com/yo1no/gramarye/P8S4ServerTransportTest.java",
            "src/test/java/com/yo1no/gramarye/magic/runtime/mana/"
                    + "ActionDamageTransactionDebitTest.java",
            "src/test/java/com/yo1no/gramarye/magic/runtime/mana/"
                    + "ActionDamageTransactionPreDebitTest.java",
            "src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectRequestTest.java");
    private static final List<String> LOGICAL_P9_S4_WC1_PATHS = List.of(
            ".github/workflows/build.yml",
            "scripts/collect-p9-s3-rd1-unit-test-diagnostics.sh",
            "scripts/verify-p9-s4-warning-attribution.py");
    private static final String VALID_FIXTURE = """
            package com.yo1no.gramarye;

            import net.minecraft.world.entity.monster.breeze.Breeze;

            public final class P9S3ProjectileGameTests {
                private static void exerciseAgeTerminalBeforeSweep(
                        Object helper, long fixtureId) {
                    try (var scenario = new ProductionScenario(helper, fixtureId)) {
                        var projectile = new Projectile();
                        var target = scenario.addDeflectingTarget(projectile.position().add(projectile.getDeltaMovement().scale(0.5)));
                        var firstObservation = target.isAlive();
                        var secondObservation = target.isAlive();
                        if (!firstObservation || !secondObservation) {
                            throw new AssertionError("liveness observation failed");
                        }
                    }
                }

                private static final class ProductionScenario implements AutoCloseable {
                    private ProductionScenario(Object helper, long fixtureId) {}

                    private Breeze addDeflectingTarget(Vec3 position) {
                        return new Breeze();
                    }

                    @Override
                    public void close() {}
                }

                private static final class Projectile {
                    private Vec3 position() {
                        return new Vec3();
                    }

                    private Vec3 getDeltaMovement() {
                        return new Vec3();
                    }
                }

                private static final class Vec3 {
                    private Vec3 add(Vec3 other) {
                        return this;
                    }

                    private Vec3 scale(double factor) {
                        return this;
                    }
                }
            }
            """;
    private static final String BREEZE_STUB = """
            package net.minecraft.world.entity.monster.breeze;

            public class Breeze {
                public boolean isAlive() {
                    return true;
                }
            }
            """;
    private static final String ALIVE_PROBE_STUB = """
            package com.yo1no.gramarye.fixture;

            public interface AliveProbe {
                boolean isAlive();
            }
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void actualProductionInventoryAcceptsTheBoundBreezeAndScansAllHolders()
            throws Exception {
        var result = runVerifier(List.of("--game-test-count"));

        assertEquals(0, result.exitCode(), result.output());
        assertEquals("38", result.output().trim());

        var exactCorrectionPath = runVerifier(List.of("--is-s4-path", LOGICAL_DC1_TEST));
        assertEquals(0, exactCorrectionPath.exitCode(), exactCorrectionPath.output());
        var nearCorrectionPath = runVerifier(List.of(
                "--is-s4-path", LOGICAL_DC1_TEST + ".extra"));
        assertEquals(1, nearCorrectionPath.exitCode(), nearCorrectionPath.output());

        for (var logicalPath : LOGICAL_P9_S4_PATHS) {
            var exactS4Path = runVerifier(List.of("--is-s4-path", logicalPath));
            assertEquals(0, exactS4Path.exitCode(), exactS4Path.output());
            var nearS4Path = runVerifier(List.of("--is-s4-path", logicalPath + ".extra"));
            assertEquals(1, nearS4Path.exitCode(), nearS4Path.output());
        }
        for (var logicalPath : LOGICAL_P9_S4_WC1_PATHS) {
            var exactWc1Path = runVerifier(List.of("--is-s4-path", logicalPath));
            assertEquals(0, exactWc1Path.exitCode(), exactWc1Path.output());
            var nearWc1Path = runVerifier(List.of("--is-s4-path", logicalPath + ".extra"));
            assertEquals(1, nearWc1Path.exitCode(), nearWc1Path.output());
        }
    }

    @Test
    void directMatcherAcceptsTheStructurallyBoundBreezeReceiver() throws Exception {
        var source = compiledFixture("valid-breeze", VALID_FIXTURE);
        var result = checkFixture(source);

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().isBlank(), result.output());
    }

    @Test
    void receiverStillNamedTargetIsRejectedWhenDeclaredThread() throws Exception {
        var source = compiledFixture(
                "thread-target",
                VALID_FIXTURE.replace(
                        "var target = scenario.addDeflectingTarget(projectile.position().add(projectile.getDeltaMovement().scale(0.5)));",
                        "Thread target = null;"));

        assertRejected(source, "raw worker/task/process surface");

        var encodedThreadOwner = "java.lang.Thre" + "\\" + "uuuu0061d.currentThread().is"
                + "\\" + "uuuu0041live()";
        var encodedSource = compiledFixture(
                "unicode-current-thread",
                VALID_FIXTURE.replace(
                        "var firstObservation = target.isAlive();",
                        "var firstObservation = " + encodedThreadOwner + ";"));

        assertRejected(encodedSource, "unverified liveness receiver");
    }

    @Test
    void currentThreadLivenessIsRejectedIndependently() throws Exception {
        var source = compiledFixture(
                "current-thread",
                VALID_FIXTURE.replace(
                        "var firstObservation = target.isAlive();",
                        "var firstObservation = Thread.currentThread().isAlive();"));

        assertRejected(source, "raw worker/task/process surface");
    }

    @Test
    void sameNameThreadParameterCannotBorrowTheEntityBinding() throws Exception {
        var threadSource = compiledFixture(
                "shadowed-thread-target",
                VALID_FIXTURE.replace(
                        "var secondObservation = target.isAlive();",
                        "class ReceiverProbe {\n"
                                + "                private boolean inspect(\n"
                                + "                        java.lang.Thread\n"
                                + "                        target) {\n"
                                + "                    return target.isAlive();\n"
                                + "                }\n"
                                + "            }\n"
                                + "            var receiverProbe = new ReceiverProbe();\n"
                                + "            var secondObservation = receiverProbe.inspect(null);"));

        assertRejected(threadSource, "raw worker/task/process surface");

        var siblingScopeSource = compiledFixture(
                "sibling-scope-unknown-target",
                VALID_FIXTURE
                        .replace(
                                "import net.minecraft.world.entity.monster.breeze.Breeze;",
                                "import net.minecraft.world.entity.monster.breeze.Breeze;\n"
                                        + "import com.yo1no.gramarye.fixture.AliveProbe;")
                        .replace(
                                "var projectile = new Projectile();\n"
                                        + "            var target = scenario.addDeflectingTarget(projectile.position().add(projectile.getDeltaMovement().scale(0.5)));\n"
                                        + "            var firstObservation = target.isAlive();\n"
                                        + "            var secondObservation = target.isAlive();",
                                "boolean firstObservation;\n"
                                        + "            boolean secondObservation;\n"
                                        + "            {\n"
                                        + "                var projectile = new Projectile();\n"
                                        + "                var target = scenario.addDeflectingTarget(projectile.position().add(projectile.getDeltaMovement().scale(0.5)));\n"
                                        + "            }\n"
                                        + "            {\n"
                                        + "                AliveProbe target = () -> true;\n"
                                        + "                firstObservation = target.isAlive();\n"
                                        + "                secondObservation = target.isAlive();\n"
                                        + "            }"));

        assertRejected(siblingScopeSource, "unverified liveness receiver");
    }

    @Test
    void forbiddenConcurrencyAndProcessSurfacesRemainRejectedBesideLegalEntityUse()
            throws Exception {
        for (var declaration : List.of(
                "private java.util.concurrent.Executor executor;",
                "private java.util.concurrent.Future<?> future;",
                "private ProcessBuilder processBuilder;",
                "private java.util.concurrent.atomic.AtomicReference<Object> reference;")) {
            var label = declaration.substring(declaration.lastIndexOf(' ') + 1)
                    .replace(";", "");
            var source = compiledFixture(
                    "forbidden-" + label,
                    VALID_FIXTURE.replace(
                            "public final class P9S3ProjectileGameTests {",
                            "public final class P9S3ProjectileGameTests {\n    "
                                    + declaration));

            assertRejected(source, "raw worker/task/process surface");
        }
    }

    @Test
    void unknownLivenessReceiverRemainsFailClosed() throws Exception {
        var source = compiledFixture(
                "unknown-target",
                VALID_FIXTURE
                        .replace(
                                "var target = scenario.addDeflectingTarget(projectile.position().add(projectile.getDeltaMovement().scale(0.5)));",
                                "Unknown target = new Unknown();")
                        .replace(
                                "private static final class Projectile {",
                                "private static final class Unknown {\n"
                                        + "        private boolean isAlive() {\n"
                                        + "            return true;\n"
                                        + "        }\n"
                                        + "    }\n\n"
                                        + "    private static final class Projectile {"));

        assertRejected(source, "unverified liveness receiver");

        var overloadBase = VALID_FIXTURE
                .replace(
                        "import net.minecraft.world.entity.monster.breeze.Breeze;",
                        "import net.minecraft.world.entity.monster.breeze.Breeze;\n"
                                + "import com.yo1no.gramarye.fixture.AliveProbe;")
                .replace(
                        "var target = scenario.addDeflectingTarget(projectile.position().add(projectile.getDeltaMovement().scale(0.5)));",
                        "var target = scenario.addDeflectingTarget(new ProbePosition());")
                .replace(
                        "private static final class Projectile {",
                        "private static final class ProbePosition {}\n\n"
                                + "    private static final class Projectile {");
        var overloadSource = compiledFixture(
                "comment-separated-decoy-helper",
                overloadBase.replace(
                        "private Breeze addDeflectingTarget(Vec3 position) {",
                        "private AliveProbe\n"
                                + "    // .\n"
                                + "    addDeflectingTarget(ProbePosition position) {\n"
                                + "        return () -> true;\n"
                                + "    }\n\n"
                                + "    private Breeze addDeflectingTarget(Vec3 position) {"));

        assertRejected(overloadSource, "unverified liveness receiver");

        var ignoredCodePoint = Character.toString(0x200B);
        var ignoredIdentifierSource = compiledFixture(
                "ignorable-identifier-decoy-helper",
                overloadBase.replace(
                        "private Breeze addDeflectingTarget(Vec3 position) {",
                        "private AliveProbe addDeflecting"
                                + ignoredCodePoint
                                + "Target(ProbePosition position) {\n"
                                + "        return () -> true;\n"
                                + "    }\n\n"
                                + "    private Breeze addDeflectingTarget(Vec3 position) {"));

        assertRejected(ignoredIdentifierSource, "unverified liveness receiver");

        var localTypeSource = compiledFixture(
                "local-breeze-name",
                VALID_FIXTURE
                        .replace(
                                "private static final class Projectile {",
                                "private static final class Breeze {\n"
                                        + "        private boolean isAlive() {\n"
                                        + "            return true;\n"
                                        + "        }\n"
                                        + "    }\n\n"
                                        + "    private static final class Projectile {"));

        assertRejected(localTypeSource, "unverified liveness receiver");
    }

    private Fixture compiledFixture(String label, String source) throws IOException {
        var fixtureRoot = Files.createDirectories(temporaryDirectory.resolve(label));
        var sourcePath = fixtureRoot.resolve("P9S3ProjectileGameTests.java");
        Files.writeString(sourcePath, source, StandardCharsets.UTF_8);
        var breezePath = fixtureRoot.resolve(
                "net/minecraft/world/entity/monster/breeze/Breeze.java");
        Files.createDirectories(breezePath.getParent());
        Files.writeString(breezePath, BREEZE_STUB, StandardCharsets.UTF_8);
        var aliveProbePath = fixtureRoot.resolve(
                "com/yo1no/gramarye/fixture/AliveProbe.java");
        Files.createDirectories(aliveProbePath.getParent());
        Files.writeString(aliveProbePath, ALIVE_PROBE_STUB, StandardCharsets.UTF_8);
        var classes = Files.createDirectory(fixtureRoot.resolve("classes"));
        var diagnostics = new ByteArrayOutputStream();
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler unavailable");
        var exitCode = compiler.run(
                null,
                diagnostics,
                diagnostics,
                "--release",
                "21",
                "-d",
                classes.toString(),
                sourcePath.toString(),
                breezePath.toString(),
                aliveProbePath.toString());
        assertEquals(
                0,
                exitCode,
                () -> "fixture must be valid Java:\n"
                        + diagnostics.toString(StandardCharsets.UTF_8));
        return new Fixture(sourcePath, classes);
    }

    private void assertRejected(Fixture fixture, String diagnostic) throws Exception {
        var result = checkFixture(fixture);
        assertEquals(1, result.exitCode(), result.output());
        assertTrue(result.output().contains(diagnostic), result.output());
        assertTrue(result.output().contains(LOGICAL_P9_SOURCE), result.output());
    }

    private ProcessResult checkFixture(Fixture fixture) throws Exception {
        return runVerifier(List.of(
                "--check-game-test-worker-source",
                fixture.source().toString(),
                LOGICAL_P9_SOURCE,
                fixture.classes().toString()));
    }

    private ProcessResult runVerifier(List<String> arguments) throws Exception {
        assertTrue(Files.isRegularFile(VERIFIER), VERIFIER.toString());
        assertFalse(Files.isSymbolicLink(VERIFIER), VERIFIER.toString());
        var command = new java.util.ArrayList<String>();
        command.add("bash");
        command.add(VERIFIER.toString());
        command.addAll(arguments);
        var process = new ProcessBuilder(command)
                .directory(PROJECT_ROOT.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        return new ProcessResult(exitCode, output);
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project root unavailable");
        }
        return current;
    }

    private record ProcessResult(int exitCode, String output) {}

    private record Fixture(Path source, Path classes) {}
}
