package com.yo1no.gramarye.magic.definition.scoped;

import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ScopeDProductionIsolationTest {
    private static final String PACKAGE = "com/yo1no/gramarye/magic/definition/scoped/";
    private static final Path PROJECT = projectRoot();
    private static final Path MAIN = PROJECT.resolve("src/main/java");
    private static final List<Class<?>> COMPILER_TYPES = List.of(
            ScopeDCompiler.class, ScopeDResolver.class, ScopeDFrontend.class, ScopeDLowering.class,
            ScopeDFeedback.class, ScopeDProgram.class, ScopeDCompilerDiagnostics.class, ScopeDLegacyCompiler.class);
    private static final List<String> FORBIDDEN_LINKS = List.of(
            "net/minecraft/client/", "net/minecraft/world/", "net/minecraft/server/", "net/minecraft/network/",
            "net/neoforged/neoforge/network/", "com/yo1no/gramarye/client/", "com/yo1no/gramarye/magic/runtime/",
            "com/yo1no/gramarye/magic/network/", "com/yo1no/gramarye/magic/effect/",
            "com/yo1no/gramarye/magic/mana/", "java/lang/reflect/");
    private static final Pattern SNAPSHOT_CONSTRUCTION = Pattern.compile(
            "\\b(?:new\\s+(?:ScopeDFrontend\\.)?Snapshot\\s*\\(|Snapshot\\s*::\\s*new\\b)");

    @Test
    void onlyPackageInternalTypesCanNameCatalogSourceAndProof() throws Exception {
        for (Class<?> type : List.of(ScopeDCatalog.class, ScopeDSource.class, ScopeDStorage.class,
                ScopeDDrafts.class, ScopeDData.class, ScopeDDataCodec.class)) {
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getName());
        }
        for (Class<?> type : COMPILER_TYPES) {
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getName());
        }
        var proof = ScopeDCatalog.CompatibilityProof.class;
        assertTrue(Modifier.isFinal(proof.getModifiers()));
        assertFalse(Modifier.isPublic(proof.getModifiers()));
        assertFalse(Serializable.class.isAssignableFrom(proof));
        for (var constructor : proof.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        }
        assertFalse(Arrays.stream(proof.getDeclaredMethods()).anyMatch(method -> method.getReturnType() == proof));
        try (var paths = Files.walk(MAIN)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                if (path.startsWith(MAIN.resolve(PACKAGE))) {
                    continue;
                }
                assertFalse(Files.readString(path).contains("magic.definition.scoped"), path.toString());
            }
        }
    }

    @Test
    void productionJarContainsDataEngineAndNoTestProofFaultOrClientHarness() throws Exception {
        var project = new Properties();
        try (var file = Files.newInputStream(PROJECT.resolve("gradle.properties"))) {
            project.load(file);
        }
        String jarName = project.getProperty("mod_id") + "-" + project.getProperty("mod_version") + ".jar";
        try (var jar = new JarFile(PROJECT.resolve("build/libs").resolve(jarName).toFile())) {
            assertNotNull(jar.getEntry(PACKAGE + "ScopeDCatalog.class"));
            assertNotNull(jar.getEntry(PACKAGE + "ScopeDStorage.class"));
            assertNotNull(jar.getEntry(PACKAGE + "ScopeDCatalog$CompatibilityProof.class"));
            for (Class<?> type : COMPILER_TYPES) {
                assertNotNull(jar.getEntry(PACKAGE + type.getSimpleName() + ".class"), type.getName());
            }
            assertFalse(jar.stream().map(JarEntry::getName)
                    .filter(name -> name.startsWith(PACKAGE) || name.startsWith("com/yo1no/gramarye/ScopeD"))
                    .anyMatch(name -> name.contains("Test") || name.contains("$Child") || name.contains("Client")
                            || name.contains("Fixture") || name.contains("Harness")));
            for (JarEntry entry : jar.stream().filter(value -> value.getName().startsWith(PACKAGE)
                    && value.getName().endsWith(".class")).toList()) {
                try (var bytes = jar.getInputStream(entry)) {
                    // Class references and the fixture namespace are ASCII in JVM constant-pool strings.
                    String bytecode = new String(bytes.readAllBytes(), StandardCharsets.ISO_8859_1);
                    for (String forbidden : FORBIDDEN_LINKS) {
                        assertFalse(bytecode.contains(forbidden), entry.getName() + " links " + forbidden);
                    }
                    assertFalse(bytecode.contains("test:") || bytecode.contains("gramarye:compiler_fixture_"),
                            entry.getName() + " contains a synthetic descriptor tag");
                    if (!entry.getName().startsWith(PACKAGE + "ScopeDFrontend")) {
                        assertFalse(bytecode.contains(PACKAGE + "ScopeDFrontend$Snapshot"),
                                entry.getName() + " reaches the frontend snapshot builder");
                    }
                    if (!entry.getName().equals(PACKAGE + "ScopeDCatalog.class")
                            && !entry.getName().equals(PACKAGE + "ScopeDStorage.class")) {
                        assertFalse(bytecode.contains("commitPreparedImage"),
                                entry.getName() + " reaches the physical commit boundary");
                    }
                }
            }
        }
        String frontendSource = Files.readString(MAIN.resolve(PACKAGE + "ScopeDFrontend.java"));
        assertFalse(SNAPSHOT_CONSTRUCTION.matcher(frontendSource).find(), "Production configures a frontend snapshot");
    }

    private static Path projectRoot() {
        for (Path path = Path.of("").toAbsolutePath().normalize(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("settings.gradle"))) {
                return path;
            }
        }
        throw new IllegalStateException("Project root unavailable");
    }
}
