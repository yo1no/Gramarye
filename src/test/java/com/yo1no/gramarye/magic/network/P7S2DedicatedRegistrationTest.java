package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class P7S2DedicatedRegistrationTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path NETWORK_MAIN = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye/magic/network");

    @Test
    void commonRegistrarPayloadHandlerAndCompositionClassesInitializeWithoutClientCode() {
        var classNames = List.of(
                "com.yo1no.gramarye.magic.network.P7PayloadRegistrar",
                "com.yo1no.gramarye.magic.network.CastIntentPayload",
                "com.yo1no.gramarye.magic.network.IntentAckPayload",
                "com.yo1no.gramarye.magic.network.PlayerManaSyncPayload",
                "com.yo1no.gramarye.magic.network.SkillCooldownSyncPayload",
                "com.yo1no.gramarye.magic.network.P7CastIntentNetworkHandler",
                "com.yo1no.gramarye.magic.network.P7ClientPayloadHandlers",
                "com.yo1no.gramarye.magic.network.P7NetworkComposition");

        classNames.forEach(name -> assertDoesNotThrow(() -> Class.forName(
                name, true, P7S2DedicatedRegistrationTest.class.getClassLoader())));
    }

    @Test
    void productionNetworkSourcesContainNoMinecraftClientReference() throws IOException {
        try (var paths = Files.walk(NETWORK_MAIN)) {
            assertTrue(paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(P7S2DedicatedRegistrationTest::read)
                    .noneMatch(source -> source.contains("net.minecraft.client")));
        }
    }

    @Test
    void registrarDoesNotDependOnClientDistributionAnnotations() {
        var source = read(NETWORK_MAIN.resolve("P7PayloadRegistrar.java"));

        assertFalse(source.contains("Dist.CLIENT"));
        assertFalse(source.contains("OnlyIn"));
        assertFalse(source.contains("net.neoforged.api.distmarker"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("unable to inspect " + path, exception);
        }
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root unavailable");
    }
}
