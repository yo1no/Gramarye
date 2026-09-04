package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class P7NetworkBoundsTest {
    private static final Map<String, Object> EXACT_CONSTANTS = Map.ofEntries(
            Map.entry("MAX_C2S_INTENT_BYTES", 32),
            Map.entry("MAX_S2C_ACK_BYTES", 32),
            Map.entry("MAX_S2C_SYNC_BYTES", 4096),
            Map.entry("MAX_INTENTS_PER_PACKET", 1),
            Map.entry("MAX_INTENTS_PER_PLAYER_PER_TICK", 8),
            Map.entry("RATE_BUCKET_CAPACITY", 8),
            Map.entry("MAX_GLOBAL_WORK_UNITS_PER_TICK", 64),
            Map.entry("MAX_PENDING_INTENTS_PER_PLAYER", 8),
            Map.entry("MAX_PENDING_INTENTS_PER_SERVER", 64),
            Map.entry("NETWORK_SEQUENCE_MIN", 1L),
            Map.entry("NETWORK_SEQUENCE_MAX", Long.MAX_VALUE),
            Map.entry("RETAINED_REPLAY_SCALARS", 1),
            Map.entry("MAX_FUTURE_SEQUENCE_GAP", 0),
            Map.entry("MAX_ACK_ENTRIES_PER_PACKET", 1),
            Map.entry("MAX_PENDING_ACKS_PER_PLAYER", 0),
            Map.entry("MAX_SYNC_ENTRIES_PER_PACKET", 64),
            Map.entry("MAX_SYNC_PAYLOAD_BYTES", 4096),
            Map.entry("MAX_WIRE_STRING_OR_RESOURCE_BYTES", 128),
            Map.entry("MAX_OPTIONAL_ENTITY_HINTS_PER_INTENT", 1),
            Map.entry("MAX_ACTIVE_SESSIONS_PER_PLAYER", 1),
            Map.entry("MAX_ACTIVE_SESSIONS_PER_SERVER", 256),
            Map.entry("MAX_DIAGNOSTIC_RECORDS", 256),
            Map.entry("MAX_DISCONNECT_CLEANUP_WORK", 9),
            Map.entry("MAX_RELOAD_RECONCILIATION_QUEUE", 256),
            Map.entry("MAX_RELOAD_RECONCILIATION_PER_TICK", 16),
            Map.entry("MAX_SERVER_STOP_CLEANUP_RECORDS", 576),
            Map.entry("RATE_STRIKE_DISCONNECT_THRESHOLD", 8),
            Map.entry("RATE_STRIKE_WINDOW_TICKS", 100),
            Map.entry("MIN_RESYNC_INTERVAL_TICKS", 20),
            Map.entry("PROTOCOL_VERSION", "gramarye-p7-v0"),
            Map.entry("SEQUENCE_EXHAUSTION_BOUNDARY", Long.MAX_VALUE),
            Map.entry("MAX_CUMULATIVE_P7_WORK_PER_TICK", 64),
            Map.entry("RATE_BUCKET_INITIAL_TOKENS", 8),
            Map.entry("RATE_BUCKET_REFILL_PER_TICK", 2),
            Map.entry("RATE_BUCKET_COST_PER_CAST", 1),
            Map.entry("SLOT_MIN", 0),
            Map.entry("SLOT_MAX", 63),
            Map.entry("CAST_INPUT_KIND_CODE", 0),
            Map.entry("AIM_PRESENT_BIT", 0),
            Map.entry("ENTITY_HINT_PRESENT_BIT", 1),
            Map.entry("ALLOWED_PRESENCE_MASK", 0b00000011),
            Map.entry("Q15_MIN", -32767),
            Map.entry("Q15_MAX", 32767),
            Map.entry("Q15_RESERVED", -32768),
            Map.entry("ENTITY_HINT_MIN", 1),
            Map.entry("ENTITY_HINT_MAX", Integer.MAX_VALUE),
            Map.entry("ACTUAL_MAX_CAST_INTENT_BODY_BYTES", 22),
            Map.entry("ACTUAL_MAX_ACK_BODY_BYTES", 18));

    @Test
    void exactTwentyEightRowsAndNestedStructuralConstantsMatchAuthority()
            throws Exception {
        var fieldsByName = Arrays.stream(P7NetworkBounds.class.getDeclaredFields())
                .collect(Collectors.toMap(field -> field.getName(), field -> field));

        assertEquals(48, EXACT_CONSTANTS.size());
        assertEquals(EXACT_CONSTANTS.keySet(), fieldsByName.keySet());
        for (var entry : EXACT_CONSTANTS.entrySet()) {
            var field = fieldsByName.get(entry.getKey());
            assertEquals(entry.getValue(), field.get(null), entry.getKey());
        }
    }

    @Test
    void repeatedAuthorityValuesRemainIdenticalWithinTheSingleOwner() {
        assertEquals(
                P7NetworkBounds.MAX_GLOBAL_WORK_UNITS_PER_TICK,
                P7NetworkBounds.MAX_CUMULATIVE_P7_WORK_PER_TICK);
        assertEquals(
                P7NetworkBounds.MAX_S2C_SYNC_BYTES,
                P7NetworkBounds.MAX_SYNC_PAYLOAD_BYTES);
        assertEquals(
                P7NetworkBounds.NETWORK_SEQUENCE_MAX,
                P7NetworkBounds.SEQUENCE_EXHAUSTION_BOUNDARY);
        assertEquals(
                P7NetworkBounds.RATE_BUCKET_CAPACITY,
                P7NetworkBounds.RATE_BUCKET_INITIAL_TOKENS);
        assertEquals(
                P7NetworkBounds.CAST_INPUT_KIND_CODE,
                CastInputKind.CAST.semanticCode());
    }

    @Test
    void boundsOwnerIsPackagePrivateFinalAndHasOnlyImmutableConstants()
            throws Exception {
        var typeModifiers = P7NetworkBounds.class.getModifiers();
        var fields = P7NetworkBounds.class.getDeclaredFields();
        var constructors = P7NetworkBounds.class.getDeclaredConstructors();

        assertFalse(Modifier.isPublic(typeModifiers));
        assertFalse(Modifier.isProtected(typeModifiers));
        assertTrue(Modifier.isFinal(typeModifiers));
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertEquals(0, P7NetworkBounds.class.getDeclaredMethods().length);
        assertEquals(0, P7NetworkBounds.class.getDeclaredClasses().length);
        assertTrue(Arrays.stream(fields).allMatch(field -> {
            var modifiers = field.getModifiers();
            return Modifier.isStatic(modifiers)
                    && Modifier.isFinal(modifiers)
                    && !Modifier.isPublic(modifiers)
                    && !Modifier.isProtected(modifiers)
                    && (field.getType() == int.class
                            || field.getType() == long.class
                            || field.getType() == String.class);
        }));

        constructors[0].setAccessible(true);
        var failure = assertThrows(
                InvocationTargetException.class, () -> constructors[0].newInstance());
        assertInstanceOf(AssertionError.class, failure.getCause());
    }

    @Test
    void exactBoundNamesAreNotRedeclaredByAnotherProductionType() throws Exception {
        var networkRoot = projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/magic/network");
        try (var paths = Files.list(networkRoot)) {
            var otherSources = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString()
                            .equals("P7NetworkBounds.java"))
                    .map(P7NetworkBoundsTest::read)
                    .collect(Collectors.joining("\n"));
            for (var name : EXACT_CONSTANTS.keySet()) {
                var declaration = Pattern.compile(
                        "(?m)\\bstatic\\s+final\\s+[^;=\\n]+\\b"
                                + Pattern.quote(name)
                                + "\\s*=");
                assertFalse(declaration.matcher(otherSources).find(), name);
            }
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception failure) {
            throw new AssertionError("cannot read " + path, failure);
        }
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new AssertionError("project root is unavailable");
        }
        return current;
    }
}
