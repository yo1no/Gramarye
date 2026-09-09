package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class P7S2DedicatedRegistrationTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path NETWORK_MAIN = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye/magic/network");
    private static final Path ROOT_MAIN = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye");
    private static final Set<String> P8_COMMON_REGISTRATION_SOURCES = Set.of(
            "P8ClientDispatchTask.java",
            "P8ClientPayloadDispatchFactory.java",
            "P8ClientPayloadDispatchPort.java",
            "P8ClientPayloadHandlers.java",
            "P8PayloadCodecSupport.java",
            "P8PayloadRegistrationBridge.java",
            "P8ProfileCatalogEntry.java",
            "P8ProfileCatalogSnapshot.java",
            "PresentationEventPayload.java",
            "ProfileCatalogPayload.java");

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
                "com.yo1no.gramarye.magic.network.P7NetworkComposition",
                "com.yo1no.gramarye.P8ClientDispatchTask",
                "com.yo1no.gramarye.P8ClientPayloadDispatchFactory",
                "com.yo1no.gramarye.P8ClientPayloadDispatchPort",
                "com.yo1no.gramarye.P8ClientPayloadHandlers",
                "com.yo1no.gramarye.P8PayloadCodecSupport",
                "com.yo1no.gramarye.P8PayloadRegistrationBridge",
                "com.yo1no.gramarye.P8ProfileCatalogEntry",
                "com.yo1no.gramarye.P8ProfileCatalogSnapshot",
                "com.yo1no.gramarye.PresentationEventPayload",
                "com.yo1no.gramarye.ProfileCatalogPayload");

        classNames.forEach(name -> assertDoesNotThrow(() -> Class.forName(
                name, true, P7S2DedicatedRegistrationTest.class.getClassLoader())));
    }

    @Test
    void productionNetworkSourcesContainNoMinecraftClientReference() throws IOException {
        var clientOwner = NETWORK_MAIN.resolve("P7ClientLifecycleEvents.java");
        var client = read(clientOwner);
        assertTrue(client.contains("@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)"));
        assertTrue(client.contains("final class P7ClientLifecycleEvents"));
        assertFalse(client.contains("public class P7ClientLifecycleEvents"));
        assertEquals(3, client.split("@SubscribeEvent", -1).length - 1);
        try (var paths = Files.walk(NETWORK_MAIN)) {
            assertTrue(paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.equals(clientOwner))
                    .map(P7S2DedicatedRegistrationTest::read)
                    .noneMatch(source -> source.contains("net.minecraft.client")
                            || source.contains("P7ClientLifecycleEvents")
                            || source.contains("Dist.CLIENT")));
        }
    }

    @Test
    void registrarDoesNotDependOnClientDistributionAnnotations() {
        var source = read(NETWORK_MAIN.resolve("P7PayloadRegistrar.java"));

        assertFalse(source.contains("Dist.CLIENT"));
        assertFalse(source.contains("OnlyIn"));
        assertFalse(source.contains("net.neoforged.api.distmarker"));
    }

    @Test
    void exactP8RegistrationGraphRemainsCommonSideSafe() {
        for (var sourceName : P8_COMMON_REGISTRATION_SOURCES) {
            var source = read(ROOT_MAIN.resolve(sourceName));
            assertFalse(source.contains("net.minecraft.client"), sourceName);
            assertFalse(source.contains("P8ClientPresentationLifecycle"), sourceName);
            assertFalse(source.contains("GramaryeClient"), sourceName);
            assertFalse(source.contains("Dist.CLIENT"), sourceName);
        }
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
