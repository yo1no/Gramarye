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
        assertEquals(1L, permit.serverGeneration());
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
        assertThrows(P7SemanticInvariantException.class, () ->
                foreignOwner.releaseAfterEnqueueFailure(permit));
        assertEquals(1, owner.playerPending(playerId));
        assertEquals(1, owner.serverPending());
        assertEquals(0, foreignOwner.serverPending());

        permit.release();
        assertTrue(permit.released());
        assertThrows(P7SemanticInvariantException.class, permit::release);
        assertThrows(P7SemanticInvariantException.class, permit::releaseAfterEnqueueFailure);
        assertEquals(0, owner.playerPending(playerId));
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());

        var started = owner.acquire(playerId, 2L).permit().orElseThrow();
        assertTrue(started.tryStartTask());
        assertThrows(P7SemanticInvariantException.class, started::releaseAfterEnqueueFailure);
        assertEquals(1, owner.serverPending());
        started.releaseAfterTask();
        assertEquals(0, owner.serverPending());
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
    void exactSessionInvalidationReleasesOnlyMatchingEpochAndSuppressesItsTask() {
        var owner = new P7PendingPermitOwner();
        var playerId = new UUID(0L, 105L);
        var oldPermit = owner.acquire(playerId, 1L).permit().orElseThrow();
        var currentPermit = owner.acquire(playerId, 2L).permit().orElseThrow();
        var dispatchCalls = new int[1];
        var oldTask = new P7ServerDispatchTask(
                new P7QueuedCastIntent(playerId, 1L, minimumIntent(1L)),
                ignored -> dispatchCalls[0]++,
                oldPermit);

        assertEquals(1, owner.invalidateSession(playerId, 1L));
        assertTrue(oldPermit.released());
        assertEquals(1, owner.playerPending(playerId));
        assertEquals(1, owner.serverPending());
        oldTask.run();

        assertEquals(0, dispatchCalls[0]);
        assertEquals(1, owner.playerPending(playerId));
        assertEquals(1, owner.serverPending());
        assertThrows(P7SemanticInvariantException.class, oldPermit::release);
        currentPermit.release();
        assertEquals(0, owner.serverPending());
    }

    @Test
    void lifecycleTerminationDuringStartedTaskMakesOnlyItsLateFinallyBenign() {
        var owner = new P7PendingPermitOwner();
        var playerId = new UUID(0L, 106L);
        var permit = owner.acquire(playerId, 7L).permit().orElseThrow();

        assertTrue(permit.tryStartTask());
        assertEquals(1, owner.invalidateSession(playerId, 7L));
        permit.releaseAfterTask();

        assertTrue(permit.released());
        assertEquals(0, owner.playerPending(playerId));
        assertEquals(0, owner.serverPending());
        assertThrows(P7SemanticInvariantException.class, permit::release);
        assertFalse(permit.tryStartTask());
    }

    @Test
    void stopTerminalizesAllPermitsAndNewServerGenerationIsIndependent() {
        var owner = new P7PendingPermitOwner();
        var firstPlayer = new UUID(0L, 107L);
        var secondPlayer = new UUID(0L, 108L);
        var oldPermit = owner.acquire(firstPlayer, 1L).permit().orElseThrow();
        var otherOldPermit = owner.acquire(secondPlayer, 1L).permit().orElseThrow();

        assertEquals(2, owner.stopAll());
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
        var newPermit = owner.acquire(firstPlayer, 1L).permit().orElseThrow();

        assertEquals(2L, newPermit.serverGeneration());
        oldPermit.releaseAfterTask();
        otherOldPermit.releaseAfterTask();
        assertEquals(1, owner.playerPending(firstPlayer));
        assertEquals(1, owner.serverPending());
        assertThrows(P7SemanticInvariantException.class, oldPermit::release);
        newPermit.release();
        assertEquals(0, owner.serverPending());
    }

    @Test
    void absentSessionInvalidationAndEmptyStopAreBoundedNoOps() {
        var owner = new P7PendingPermitOwner();
        var playerId = new UUID(0L, 109L);

        assertThrows(NullPointerException.class, () -> owner.invalidateSession(null, 1L));
        assertThrows(P7SemanticInvariantException.class, () ->
                owner.invalidateSession(playerId, 0L));
        assertEquals(0, owner.invalidateSession(playerId, 1L));
        assertEquals(0, owner.stopAll());
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
    }

    @Test
    void staleServerGenerationAcquisitionIsBusyAndDoesNotMutateCounts() {
        var owner = new P7PendingPermitOwner();
        var playerId = new UUID(0L, 110L);
        var oldGeneration = owner.captureServerGeneration();
        assertEquals(0, owner.stopAll());

        var stale = owner.acquire(playerId, 1L, oldGeneration);
        var current = owner.acquire(
                playerId, 1L, owner.captureServerGeneration());

        assertEquals(P7PendingPermitOwner.AcquireOutcome.SERVER_BUSY, stale.outcome());
        assertTrue(stale.permit().isEmpty());
        assertEquals(P7PendingPermitOwner.AcquireOutcome.GRANTED, current.outcome());
        assertEquals(1, owner.playerPending(playerId));
        assertEquals(1, owner.serverPending());
        current.permit().orElseThrow().release();
    }

    @Test
    void ownerUsesOnePrivateMonitorAndS1AccountingWithoutExposingItsMap()
            throws IOException {
        var fieldsByName = Arrays.stream(P7PendingPermitOwner.class.getDeclaredFields())
                .collect(Collectors.toMap(
                        java.lang.reflect.Field::getName, Function.identity()));
        var monitor = fieldsByName.get("monitor");
        var perPlayerPending = fieldsByName.get("perPlayerPending");
        var activePermitsByPlayer = fieldsByName.get("activePermitsByPlayer");
        var serverPending = fieldsByName.get("serverPending");
        var serverGeneration = fieldsByName.get("serverGeneration");

        assertEquals(Set.of(
                        "monitor",
                        "perPlayerPending",
                        "activePermitsByPlayer",
                        "serverPending",
                        "serverGeneration"),
                fieldsByName.keySet());
        assertEquals(Object.class, monitor.getType());
        assertTrue(Modifier.isPrivate(monitor.getModifiers()));
        assertTrue(Modifier.isFinal(monitor.getModifiers()));
        assertEquals(Map.class, perPlayerPending.getType());
        assertTrue(Modifier.isPrivate(perPlayerPending.getModifiers()));
        assertTrue(Modifier.isFinal(perPlayerPending.getModifiers()));
        assertEquals(Map.class, activePermitsByPlayer.getType());
        assertTrue(Modifier.isPrivate(activePermitsByPlayer.getModifiers()));
        assertTrue(Modifier.isFinal(activePermitsByPlayer.getModifiers()));
        assertEquals(int.class, serverPending.getType());
        assertTrue(Modifier.isPrivate(serverPending.getModifiers()));
        assertEquals(long.class, serverGeneration.getType());
        assertTrue(Modifier.isPrivate(serverGeneration.getModifiers()));
        assertFalse(Arrays.stream(P7PendingPermitOwner.class.getDeclaredMethods())
                .anyMatch(method -> Map.class.isAssignableFrom(method.getReturnType())));

        var source = Files.readString(OWNER_SOURCE);
        assertTrue(source.contains("new PendingPermitAccounting(playerPending, serverPending)"));
        assertTrue(source.contains("accounting.acquire()"));
        assertTrue(source.contains(
                "var decision = accounting.release(permit.accountingPermitUnderOwnerLock())"));
        assertTrue(source.contains("int invalidateSession("));
        assertTrue(source.contains("int stopAll()"));
        assertTrue(source.contains("P7PendingPermit.LifecycleState.LIFECYCLE_TERMINATED"));
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
                "serverGeneration", long.class,
                "accountingPermit", PendingPermitAccounting.Permit.class,
                "lifecycleState", P7PendingPermit.LifecycleState.class), fields);
        assertTrue(Arrays.stream(P7PendingPermit.class.getDeclaredFields())
                .filter(field -> Set.of(
                                "owner",
                                "authenticatedPlayerId",
                                "connectionEpoch",
                                "serverGeneration")
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

    private static CastIntent minimumIntent(long sequence) {
        return new CastIntent(sequence, 0, CastInputKind.CAST, 0, null, null);
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
