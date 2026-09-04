package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class P7NetworkCompositionS3Test {
    @Test
    void productionCompositionRetainsExactFourPrivateFinalPorts() {
        var fields = Arrays.stream(P7NetworkComposition.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();

        assertEquals(Set.of(
                P7ConnectionEpochSnapshotSource.class,
                P7PendingPermitOwner.class,
                P7ServerIntentDispatchPort.class,
                P7ClientMirrorDispatchPort.class), fields.stream()
                .map(java.lang.reflect.Field::getType)
                .collect(Collectors.toSet()));
        assertTrue(fields.stream().allMatch(field -> Modifier.isPrivate(field.getModifiers())
                && Modifier.isFinal(field.getModifiers())));
        assertSame(P7NetworkComposition.production(), P7NetworkComposition.production());
    }

    @Test
    void productionSessionOwnerStartsEmptyUntilS4Lifecycle() throws Exception {
        var holder = Class.forName(P7NetworkComposition.class.getName() + "$ProductionHolder");
        var field = holder.getDeclaredField("SESSION_SERVICE");
        field.setAccessible(true);
        var service = (P7ServerSessionService) field.get(null);
        var playerId = UUID.fromString("00000000-0000-0000-0000-000000000710");

        assertEquals(0, service.activeSessionCount());
        assertTrue(service.currentEpoch(playerId).isEmpty());
        assertTrue(P7NetworkComposition.production()
                .connectionEpochSource()
                .currentEpoch(playerId)
                .isEmpty());
    }

    @Test
    void productionWiresRealSessionAndDispatcherMethodReferences() throws Exception {
        var source = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/magic/network/P7NetworkComposition.java"));

        assertEquals(1, occurrences(source, "new P7ServerSessionService("));
        assertEquals(1, occurrences(source, "new P7ServerAuthorizationDispatcher("));
        assertEquals(1, occurrences(source, "SESSION_SERVICE::currentEpoch"));
        assertEquals(1, occurrences(source, "SERVER_DISPATCHER::dispatch"));
        assertEquals(1, occurrences(source, "P7ServerIntentResultSink RESULT_SINK"));
        assertFalse(source.contains("OptionalLong.empty()"));
        assertFalse(source.contains("PacketDistributor"));
        assertFalse(source.contains("P7ManaSnapshotBridge"));
    }

    @Test
    void noSessionProductionDispatchFailsClosedWithoutCreatingState() throws Exception {
        var composition = P7NetworkComposition.production();
        var playerId = UUID.fromString("00000000-0000-0000-0000-000000000711");
        var intent = new CastIntent(1L, 0, CastInputKind.CAST, 0, null, null);

        composition.serverIntentDispatchPort().dispatch(
                new P7QueuedCastIntent(playerId, 1L, intent));

        assertTrue(composition.connectionEpochSource().currentEpoch(playerId).isEmpty());
        var holder = Class.forName(P7NetworkComposition.class.getName() + "$ProductionHolder");
        var field = holder.getDeclaredField("SESSION_SERVICE");
        field.setAccessible(true);
        assertEquals(0, ((P7ServerSessionService) field.get(null)).activeSessionCount());
    }

    private static long occurrences(String source, String needle) {
        return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1L;
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
