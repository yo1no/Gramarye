package com.yo1no.gramarye.magic.definition.scoped;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ScopeDProductionIsolationTest {
    private static final String PACKAGE = "com/yo1no/gramarye/magic/definition/scoped/";
    private static final Path PROJECT = projectRoot();
    private static final Path MAIN = PROJECT.resolve("src/main/java");

    @Test
    void onlyPackageInternalTypesCanNameCatalogSourceAndProof() throws Exception {
        for (Class<?> type : List.of(ScopeDCatalog.class, ScopeDSource.class, ScopeDStorage.class,
                ScopeDDrafts.class, ScopeDData.class, ScopeDDataCodec.class)) {
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getName());
        }
        var proof = ScopeDCatalog.CompatibilityProof.class;
        assertTrue(Modifier.isFinal(proof.getModifiers()));
        for (var constructor : proof.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        }
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
            assertFalse(jar.stream().map(entry -> entry.getName()).filter(name -> name.startsWith(PACKAGE))
                    .anyMatch(name -> name.contains("Test") || name.contains("$Child") || name.contains("Client")));
        }
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
