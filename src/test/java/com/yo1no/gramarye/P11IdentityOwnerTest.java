package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Isolated identity-kernel evidence, not a native player lifecycle qualification. */
final class P11IdentityOwnerTest {
    private static final UUID UUID_A = UUID.fromString("f6d6ee9a-c41b-416f-a57f-226658ac8f28");
    private static final UUID UUID_B = UUID.fromString("1c95a6be-55d9-408c-9319-fe05f386f356");

    @Test
    void equalEntityIdsAndUuidNeverSubstituteForExactActorIdentity() {
        var owner = P11IdentityOwner.isolatedModel(2);
        var a = owner.modelActor(UUID_A, 17);
        var b = owner.modelActor(UUID_A, 17);
        var connection = owner.modelConnection();
        assertEquals(a, b);
        assertNotSame(a, b);
        var first = owner.bindModel(a, connection).orElseThrow();
        var second = owner.bindModel(b, connection).orElseThrow();
        assertFalse(owner.current(first));
        assertFalse(first.currentBinding());
        assertTrue(first.connection().currentConnection());
        assertTrue(owner.current(second));
        assertTrue(second.currentBinding());
        assertSame(first.account(), second.account());
        assertSame(first.connection(), second.connection());
        assertNotSame(first.actor(), second.actor());
        assertEquals(first.actorGeneration() + 1, second.actorGeneration());
        assertFalse(owner.retireConnection(first));
        assertTrue(owner.current(second));
    }

    @Test
    void exactReobservationDoesNotCreateAnotherGeneration() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var actor = owner.modelActor(UUID_A, 1);
        var connection = owner.modelConnection();
        var captured = owner.bindModel(actor, connection).orElseThrow();
        assertSame(captured, owner.bindModel(actor, connection).orElseThrow());
        assertEquals(1, captured.actorGeneration());
        assertEquals(1, owner.retainedBindings());
    }

    @Test
    void newConnectionDoesNotInheritOldConnectionIdentity() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var actor = owner.modelActor(UUID_A, 1);
        var oldConnection = owner.modelConnection();
        var first = owner.bindModel(actor, oldConnection).orElseThrow();
        var second = owner.bindModel(actor, owner.modelConnection()).orElseThrow();
        assertFalse(owner.current(first));
        assertFalse(first.connection().currentConnection());
        assertNotSame(first.connection(), second.connection());
        assertTrue(second.connectionEpoch() > first.connectionEpoch());
        assertSame(first.account(), second.account());
        assertEquals(1, second.actorGeneration());
        assertTrue(owner.bindModel(actor, oldConnection).isEmpty());
    }

    @Test
    void identicalScalarsInAnotherServerDomainHaveNoAuthority() {
        var firstOwner = P11IdentityOwner.isolatedModel(1);
        var secondOwner = P11IdentityOwner.isolatedModel(1);
        var first = firstOwner.bindModel(firstOwner.modelActor(UUID_A, 1),
                firstOwner.modelConnection()).orElseThrow();
        var second = secondOwner.bindModel(secondOwner.modelActor(UUID_A, 1),
                secondOwner.modelConnection()).orElseThrow();
        assertEquals(first.connectionEpoch(), second.connectionEpoch());
        assertEquals(first.actorGeneration(), second.actorGeneration());
        assertNotSame(first.slot(), second.slot());
        assertFalse(firstOwner.current(second));
        assertFalse(secondOwner.current(first));
        assertFalse(firstOwner.retireConnection(second));
        assertTrue(first.isolatedModel());
        assertFalse(firstOwner.liveCurrent(first));
    }

    @Test
    void fixtureObjectsFromOtherDomainsCannotBeBound() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var other = P11IdentityOwner.isolatedModel(1);
        assertTrue(owner.bindModel(other.modelActor(UUID_A, 1), owner.modelConnection()).isEmpty());
        assertTrue(owner.bindModel(owner.modelActor(UUID_A, 1), other.modelConnection()).isEmpty());
        assertTrue(owner.modelActorless(UUID_A, other.modelConnection()).isEmpty());
    }

    @Test
    void actorlessHandoffKeepsConnectionAndMonotonicActorGeneration() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var connection = owner.modelConnection();
        var config = owner.modelActorless(UUID_A, connection).orElseThrow();
        assertEquals(0, config.actorGeneration());
        var first = owner.bindModel(owner.modelActor(UUID_A, 1), connection).orElseThrow();
        var returned = owner.modelActorless(UUID_A, connection).orElseThrow();
        var second = owner.bindModel(owner.modelActor(UUID_A, 1), connection).orElseThrow();
        assertSame(config.connection(), second.connection());
        assertSame(config.account(), second.account());
        assertEquals(1, first.actorGeneration());
        assertEquals(0, returned.actorGeneration());
        assertEquals(2, second.actorGeneration());
        assertFalse(owner.current(config));
        assertFalse(config.currentBinding());
        assertTrue(config.connection().currentConnection());
        assertFalse(owner.current(returned));
        assertFalse(owner.liveCurrent(second));
    }

    @Test
    void oneAuthenticatedConnectionCannotBelongToTwoAccounts() {
        var owner = P11IdentityOwner.isolatedModel(2);
        var connection = owner.modelConnection();
        owner.modelActorless(UUID_A, connection).orElseThrow();
        assertTrue(owner.modelActorless(UUID_B, connection).isEmpty());
        assertTrue(owner.bindModel(owner.modelActor(UUID_B, 1), connection).isEmpty());
        assertEquals(1, owner.retainedAccounts());
    }

    @Test
    void retirementDoesNotReleaseAccountDataResponsibilityOrPermitConnectionReplay() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var connection = owner.modelConnection();
        var captured = owner.bindModel(owner.modelActor(UUID_A, 1), connection).orElseThrow();
        assertTrue(owner.retireConnection(captured));
        assertFalse(owner.retireConnection(captured));
        assertFalse(captured.currentBinding());
        assertFalse(captured.connection().currentConnection());
        assertEquals(0, owner.retainedBindings());
        assertEquals(1, owner.retainedAccounts());
        assertTrue(owner.owns(captured.account()));
        assertTrue(owner.modelActorless(UUID_A, connection).isEmpty());
        assertTrue(owner.bindModel(owner.modelActor(UUID_A, 2), connection).isEmpty());
        assertTrue(owner.bindModel(owner.modelActor(UUID_B, 3), owner.modelConnection()).isEmpty());
    }

    @Test
    void soleLedgerCanDischargeUnusedUnboundAccountExactlyOnce() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var ledger = new P11ReceiptLedger(owner);
        assertThrows(IllegalStateException.class, () -> new P11ReceiptLedger(owner));
        var captured = owner.modelActorless(UUID_A, owner.modelConnection()).orElseThrow();
        var proof = ledger.dischargeUnusedAccount(captured.account()).orElseThrow();
        assertFalse(owner.releaseUnboundAccount(proof));
        assertTrue(owner.retireConnection(captured));
        assertTrue(owner.releaseUnboundAccount(proof));
        assertFalse(owner.releaseUnboundAccount(proof));
        assertEquals(0, owner.retainedAccounts());
        var replacement = owner.modelActorless(UUID_B, owner.modelConnection()).orElseThrow();
        assertTrue(owner.current(replacement));
        assertFalse(owner.releaseUnboundAccount(proof));
    }

    @Test
    void unusedDischargeCannotReleaseALaterBindingLifecycle() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var ledger = new P11ReceiptLedger(owner);
        var connection = owner.modelConnection();
        var first = owner.modelActorless(UUID_A, connection).orElseThrow();
        var oldDischarge = ledger.dischargeUnusedAccount(first.account()).orElseThrow();
        var replacement = owner.bindModel(owner.modelActor(UUID_A, 1), connection).orElseThrow();
        assertTrue(owner.retireConnection(replacement));
        assertFalse(owner.releaseUnboundAccount(oldDischarge));
        assertEquals(1, owner.retainedAccounts());
        var currentDischarge = ledger.dischargeUnusedAccount(replacement.account()).orElseThrow();
        assertTrue(owner.releaseUnboundAccount(currentDischarge));
    }

    @Test
    void sourceEpochAllocatorRequiresSoleReceiptDomainAndExactCurrentBinding() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var domain = owner.claimReceiptDomain();
        var connection = owner.modelConnection();
        var first = owner.modelActorless(UUID_A, connection).orElseThrow();
        assertEquals(1, owner.nextSourceEpoch(domain, first).orElseThrow());
        var other = P11IdentityOwner.isolatedModel(1);
        assertTrue(owner.nextSourceEpoch(other.claimReceiptDomain(), first).isEmpty());
        assertTrue(owner.nextSourceEpoch(null, first).isEmpty());
        var replacement = owner.bindModel(owner.modelActor(UUID_A, 1), connection).orElseThrow();
        assertTrue(owner.nextSourceEpoch(domain, first).isEmpty());
        assertEquals(2, owner.nextSourceEpoch(domain, replacement).orElseThrow());
        assertTrue(owner.retireConnection(replacement));
        assertTrue(owner.nextSourceEpoch(domain, replacement).isEmpty());
    }

    @Test
    void successfulEpochAllocationAtomicallyRevokesEarlierUnusedDischarge() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var ledger = new P11ReceiptLedger(owner);
        var captured = owner.modelActorless(UUID_A, owner.modelConnection()).orElseThrow();
        var discharge = ledger.dischargeUnusedAccount(captured.account()).orElseThrow();
        assertEquals(1, owner.nextSourceEpoch(discharge.domain(), captured).orElseThrow());
        // Reproduce the gap before a caller can install its source: old proof cannot release.
        assertTrue(owner.retireConnection(captured));
        assertFalse(owner.releaseUnboundAccount(discharge));
        assertEquals(1, owner.retainedAccounts());
    }

    @Test
    void stopDropsBindingsAndPermanentlyInvalidatesSlot() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var captured = owner.modelActorless(UUID_A, owner.modelConnection()).orElseThrow();
        owner.stop();
        owner.stop();
        assertFalse(owner.current(captured));
        assertFalse(captured.currentBinding());
        assertFalse(captured.connection().currentConnection());
        assertFalse(owner.owns(captured.account()));
        assertFalse(owner.liveCurrent(captured));
        assertEquals(0, owner.retainedBindings());
        assertEquals(0, owner.retainedAccounts());
        assertTrue(owner.modelActorless(UUID_A, owner.modelConnection()).isEmpty());
        assertThrows(IllegalStateException.class, () -> new P11ReceiptLedger(owner));
    }

    @Test
    void capacitiesRejectInvalidInputsRatherThanCreatingUnboundedOwners() {
        assertThrows(IllegalArgumentException.class, () -> P11IdentityOwner.isolatedModel(0));
        assertThrows(IllegalArgumentException.class, () -> P11IdentityOwner.isolatedModel(-1));
    }

    @Test
    void sourceCustodyDoesNotBindOrReplaceAuthenticatedConnection() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var actor = owner.modelActor(UUID_A, 17);
        var data = owner.captureModelSource(actor).orElseThrow();
        assertEquals(1, owner.retainedAccounts());
        assertEquals(0, owner.retainedBindings());
        assertSame(data, owner.captureModelSource(actor).orElseThrow());
        var authenticated = owner.bindModel(actor, owner.modelConnection()).orElseThrow();
        assertSame(data.account(), authenticated.account());
        var nextActor = owner.modelActor(UUID_A, 17);
        var candidate = owner.captureModelSource(nextActor).orElseThrow();
        assertTrue(owner.current(authenticated));
        assertTrue(authenticated.currentBinding());
        assertTrue(owner.matchesModelSource(data, actor));
        assertFalse(owner.matchesModelSource(data, nextActor));
        assertTrue(owner.matchesModelSource(candidate, nextActor));
        assertTrue(owner.retireConnection(authenticated));
        assertTrue(owner.ownsData(data));
        assertTrue(owner.ownsData(candidate));
        assertFalse(owner.liveCurrent(authenticated));
        assertFalse(owner.matchesSource(data, null));
    }

    @Test
    void sourceCustodyHasOnlyCurrentAndCandidateAndUsesExactNotEqualActors() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var firstActor = owner.modelActor(UUID_A, 17);
        var secondActor = owner.modelActor(UUID_A, 17);
        var thirdActor = owner.modelActor(UUID_A, 17);
        var first = owner.captureModelSource(firstActor).orElseThrow();
        var second = owner.captureModelSource(secondActor).orElseThrow();
        assertEquals(firstActor, secondActor);
        assertNotSame(first, second);
        assertTrue(owner.captureModelSource(thirdActor).isEmpty());
        assertTrue(owner.captureModelSource(owner.modelActor(UUID_B, 1)).isEmpty());
        var domain = owner.claimReceiptDomain();
        assertFalse(owner.commitSourceCustody(null, first, second));
        assertTrue(owner.commitSourceCustody(domain, first, second));
        assertFalse(owner.ownsData(first));
        assertTrue(owner.ownsData(second));
        assertTrue(owner.captureModelSource(thirdActor).isPresent());
        assertEquals(1, owner.retainedAccounts());
    }

    @Test
    void sourceCustodyRejectsForeignDomainAndStopsWithoutNativeReferencesInTokens() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var foreign = P11IdentityOwner.isolatedModel(1);
        assertTrue(owner.captureModelSource(foreign.modelActor(UUID_A, 1)).isEmpty());
        var local = owner.captureModelSource(owner.modelActor(UUID_A, 1)).orElseThrow();
        var other = foreign.captureModelSource(foreign.modelActor(UUID_A, 1)).orElseThrow();
        assertFalse(owner.ownsData(other));
        assertFalse(owner.matchesModelSource(other, owner.modelActor(UUID_A, 1)));
        var domain = owner.claimReceiptDomain();
        assertTrue(owner.nextDataSourceEpoch(domain, other).isEmpty());
        assertEquals(1, owner.nextDataSourceEpoch(domain, local).orElseThrow());
        owner.stop();
        assertFalse(owner.ownsData(local));
        assertTrue(owner.nextDataSourceEpoch(domain, local).isEmpty());
        assertTrue(owner.captureModelSource(owner.modelActor(UUID_A, 1)).isEmpty());
    }

    @Test
    void nativeCurrentBindingRequiresInstalledExactListenerButExpectationIsNotMembership()
            throws IOException {
        // Source-boundary evidence only: isolated fixtures do not impersonate native listeners.
        var source = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/P11IdentityOwner.java"));
        var bind = source.substring(source.indexOf("synchronized Optional<CapturedIdentity> bindAuthenticated("),
                source.indexOf("synchronized Optional<CapturedIdentity> bindAuthenticatedActorless("));
        assertTrue(bind.contains("connection.getPacketListener() != actor.connection"));
        assertTrue(bind.indexOf("connection.getPacketListener() != actor.connection")
                < bind.indexOf("return bind(actor.getUUID()"));
        var current = source.substring(source.indexOf("synchronized boolean liveCurrent("),
                source.indexOf("synchronized boolean owns("));
        assertTrue(current.contains("references.connection.getPacketListener() == actor.connection"));
        assertTrue(current.contains("actorless.connection.getPacketListener() == actorless.listener"));
        var expected = source.substring(source.indexOf("synchronized Optional<CapturedIdentity> bindExpectedActor("),
                source.indexOf("synchronized ModelActor modelActor("));
        assertFalse(expected.contains("getPacketListener()"));
        assertFalse(expected.contains("getPlayerList()"));
        assertTrue(expected.contains("expectedActor.connection.getConnection() != connection"));
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("project root unavailable");
    }
}
