package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class P7ReloadAdmissionGateTest {
    @Test
    void productionGateBeginsOpenAndHasOnlyClosedInternalStates() throws Exception {
        var gate = new P7ReloadAdmissionGate();
        var stateField = P7ReloadAdmissionGate.class.getDeclaredField("state");
        stateField.setAccessible(true);
        var state = (Enum<?>) stateField.get(gate);
        var stateType = P7ReloadAdmissionGate.class.getDeclaredClasses()[0];

        assertEquals("OPEN", state.name());
        assertEquals(
                java.util.Set.of("OPEN", "RELOAD_IN_PROGRESS"),
                Arrays.stream(stateType.getEnumConstants())
                        .map(value -> ((Enum<?>) value).name())
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(Modifier.isPrivate(stateField.getModifiers()));
        assertFalse(Modifier.isStatic(stateField.getModifiers()));
    }

    @Test
    void closeAndOpenAreServerThreadOnlyAndEventUnwired() throws Exception {
        var source = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/magic/network/"
                        + "P7ReloadAdmissionGate.java"));

        assertTrue(source.contains("void close(MinecraftServer server)"));
        assertTrue(source.contains("void open(MinecraftServer server)"));
        assertTrue(source.contains("!server.isSameThread()"));
        assertFalse(source.contains("SubscribeEvent"));
        assertFalse(source.contains("addListener"));
        assertFalse(source.contains("Queue"));
        assertFalse(source.contains("Set<"));
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
