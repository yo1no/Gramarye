package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class P7PendingPermitOwnerTest {
    private static final Path OWNER_SOURCE = projectRoot().resolve(
            "src/main/java/com/yo1no/gramarye/magic/network/P7PendingPermitOwner.java");

    @Test
    void firstPermitIsGrantedAndCarriesAuthenticatedIdentityAndEpoch() {
        var owner = new P7PendingPermitOwner();
        var playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        var acquisition = owner.acquire(playerId, 17L);
        var permit = acquisition.permit().orElseThrow();

        assertEquals(P7PendingPermitOwner.AcquireOutcome.GRANTED, acquisition.outcome());
        assertSame(owner, permit.owner());
        assertEquals(playerId, permit.authenticatedPlayerId());
        assertEquals(17L, permit.connectionEpoch());
        assertFalse(permit.released());
        assertEquals(1, owner.playerPending(playerId));
        assertEquals(1, owner.serverPending());
        assertEquals(1, owner.trackedPlayerCount());
    }

    @Test
    void sameUuidGetsExactlyEightPermitsAndNinthIsRejectedAcrossEpochs() {
        var owner = new P7PendingPermitOwner();
        var playerId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var permits = new ArrayList<P7PendingPermit>();

        for (var epoch = 1L; epoch <= 8L; epoch++) {
            var acquisition = owner.acquire(playerId, epoch);
            assertEquals(P7PendingPermitOwner.AcquireOutcome.GRANTED, acquisition.outcome());
            permits.add(acquisition.permit().orElseThrow());
        }
        var rejected = owner.acquire(playerId, Long.MAX_VALUE);

        assertEquals(P7PendingPermitOwner.AcquireOutcome.SERVER_BUSY, rejected.outcome());
        assertTrue(rejected.permit().isEmpty());
        assertEquals(8, owner.playerPending(playerId));
        assertEquals(8, owner.serverPending());
        assertEquals(1, owner.trackedPlayerCount());

        permits.forEach(P7PendingPermit::release);
        assertEquals(0, owner.playerPending(playerId));
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
    }

    @Test
    void serverGetsExactlySixtyFourPermitsAndSixtyFifthIsRejected() {
        var owner = new P7PendingPermitOwner();
        var permits = new ArrayList<P7PendingPermit>();

        for (var index = 1; index <= 64; index++) {
            var playerId = new UUID(0L, index);
            var acquisition = owner.acquire(playerId, 1L);
            assertEquals(P7PendingPermitOwner.AcquireOutcome.GRANTED, acquisition.outcome());
            permits.add(acquisition.permit().orElseThrow());
        }

        var rejectedPlayer = new UUID(0L, 65L);
        var rejected = owner.acquire(rejectedPlayer, 1L);

        assertEquals(P7PendingPermitOwner.AcquireOutcome.SERVER_BUSY, rejected.outcome());
        assertTrue(rejected.permit().isEmpty());
        assertEquals(64, owner.serverPending());
        assertEquals(64, owner.trackedPlayerCount());
        assertEquals(0, owner.playerPending(rejectedPlayer));

        permits.forEach(P7PendingPermit::release);
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
    }

    @Test
    void rejectedAcquisitionLeavesEveryCountUnchanged() {
        var owner = new P7PendingPermitOwner();
        var playerId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        var permits = new ArrayList<P7PendingPermit>();
        for (var epoch = 1L; epoch <= 8L; epoch++) {
            permits.add(owner.acquire(playerId, epoch).permit().orElseThrow());
        }

        var beforePlayer = owner.playerPending(playerId);
        var beforeServer = owner.serverPending();
        var beforeTracked = owner.trackedPlayerCount();
        var rejected = owner.acquire(playerId, 9L);

        assertEquals(P7PendingPermitOwner.AcquireOutcome.SERVER_BUSY, rejected.outcome());
        assertEquals(beforePlayer, owner.playerPending(playerId));
        assertEquals(beforeServer, owner.serverPending());
        assertEquals(beforeTracked, owner.trackedPlayerCount());

        permits.forEach(P7PendingPermit::release);
    }

    @Test
    void releaseDecrementsBothCountsAndRemovesOnlyTheZeroPlayerEntry() {
        var owner = new P7PendingPermitOwner();
        var firstPlayer = new UUID(0L, 101L);
        var secondPlayer = new UUID(0L, 102L);
        var firstPermit = owner.acquire(firstPlayer, 1L).permit().orElseThrow();
        var secondPermit = owner.acquire(firstPlayer, 2L).permit().orElseThrow();
        var otherPermit = owner.acquire(secondPlayer, 1L).permit().orElseThrow();

        firstPermit.release();
        assertEquals(1, owner.playerPending(firstPlayer));
        assertEquals(2, owner.serverPending());
        assertEquals(2, owner.trackedPlayerCount());

        secondPermit.release();
        assertEquals(0, owner.playerPending(firstPlayer));
        assertEquals(1, owner.serverPending());
        assertEquals(1, owner.trackedPlayerCount());

        otherPermit.release();
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
    }

    @Test
    void duplicateAndForeignReleaseAreRejectedWithoutUnderflow() {
        var owner = new P7PendingPermitOwner();
        var foreignOwner = new P7PendingPermitOwner();
        var playerId = new UUID(0L, 103L);
        var permit = owner.acquire(playerId, 1L).permit().orElseThrow();

        assertThrows(P7SemanticInvariantException.class, () -> foreignOwner.release(permit));
        assertEquals(1, owner.playerPending(playerId));
        assertEquals(1, owner.serverPending());
        assertEquals(0, foreignOwner.serverPending());

        permit.release();
        assertTrue(permit.released());
        assertThrows(P7SemanticInvariantException.class, permit::release);
        assertEquals(0, owner.playerPending(playerId));
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
    }

    @Test
    void nullIdentityAndNonpositiveEpochAreRejectedBeforeMutation() {
        var owner = new P7PendingPermitOwner();
        var playerId = new UUID(0L, 104L);

        assertThrows(NullPointerException.class, () -> owner.acquire(null, 1L));
        assertThrows(P7SemanticInvariantException.class, () -> owner.acquire(playerId, 0L));
        assertThrows(P7SemanticInvariantException.class, () -> owner.acquire(playerId, -1L));
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
    }

    @Test
    void ownerUsesOnePrivateMonitorAndS1AccountingWithoutExposingItsMap()
            throws IOException {
        var fieldsByName = Arrays.stream(P7PendingPermitOwner.class.getDeclaredFields())
                .collect(Collectors.toMap(
                        java.lang.reflect.Field::getName, Function.identity()));
        var monitor = fieldsByName.get("monitor");
        var perPlayerPending = fieldsByName.get("perPlayerPending");
        var serverPending = fieldsByName.get("serverPending");

        assertEquals(Set.of("monitor", "perPlayerPending", "serverPending"),
                fieldsByName.keySet());
        assertEquals(Object.class, monitor.getType());
        assertTrue(Modifier.isPrivate(monitor.getModifiers()));
        assertTrue(Modifier.isFinal(monitor.getModifiers()));
        assertEquals(Map.class, perPlayerPending.getType());
        assertTrue(Modifier.isPrivate(perPlayerPending.getModifiers()));
        assertTrue(Modifier.isFinal(perPlayerPending.getModifiers()));
        assertEquals(int.class, serverPending.getType());
        assertTrue(Modifier.isPrivate(serverPending.getModifiers()));
        assertFalse(Arrays.stream(P7PendingPermitOwner.class.getDeclaredMethods())
                .anyMatch(method -> Map.class.isAssignableFrom(method.getReturnType())));

        var source = Files.readString(OWNER_SOURCE);
        assertTrue(source.contains("new PendingPermitAccounting(playerPending, serverPending)"));
        assertTrue(source.contains("accounting.acquire()"));
        assertTrue(source.contains("accounting.release(\n"
                + "                    permit.accountingPermitUnderOwnerLock())"));
        assertEquals(6, occurrences(source, "synchronized (monitor)"));
        assertEquals(3, occurrences(source, "permit.accountingPermitUnderOwnerLock()"));
        assertEquals(1, occurrences(source, "permit.markReleasedUnderOwnerLock("));
        try (var paths = Files.list(OWNER_SOURCE.getParent())) {
            var tokenStateCallers = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        var candidate = read(path);
                        return candidate.contains(".accountingPermitUnderOwnerLock()")
                                || candidate.contains(".markReleasedUnderOwnerLock(");
                    })
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
            assertEquals(Set.of("P7PendingPermitOwner.java"), tokenStateCallers);
        }
        assertFalse(source.contains("MAX_PENDING_INTENTS_PER_PLAYER"));
        assertFalse(source.contains("MAX_PENDING_INTENTS_PER_SERVER"));
    }

    @Test
    void permitRetainsOnlyOwnerUuidEpochAndS1TokenState() {
        var fields = Arrays.stream(P7PendingPermit.class.getDeclaredFields())
                .collect(Collectors.toMap(
                        java.lang.reflect.Field::getName,
                        java.lang.reflect.Field::getType));

        assertEquals(Map.of(
                "owner", P7PendingPermitOwner.class,
                "authenticatedPlayerId", UUID.class,
                "connectionEpoch", long.class,
                "accountingPermit", PendingPermitAccounting.Permit.class), fields);
        assertTrue(Arrays.stream(P7PendingPermit.class.getDeclaredFields())
                .filter(field -> Set.of(
                                "owner", "authenticatedPlayerId", "connectionEpoch")
                        .contains(field.getName()))
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())));
        assertFalse(fields.values().stream()
                .map(Class::getName)
                .anyMatch(name -> name.contains("ServerPlayer")
                        || name.contains("IPayloadContext")
                        || name.contains("ByteBuf")
                        || name.contains("Connection")));
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        var offset = 0;
        while ((offset = source.indexOf(fragment, offset)) >= 0) {
            count++;
            offset += fragment.length();
        }
        return count;
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
