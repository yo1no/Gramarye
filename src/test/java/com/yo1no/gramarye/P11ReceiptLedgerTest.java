package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Controlled observations validate receipts only; no native writes or readback occur here. */
final class P11ReceiptLedgerTest {
    private static final UUID UUID_A = UUID.fromString("f6d6ee9a-c41b-416f-a57f-226658ac8f28");

    @Test
    void newlyBoundSourceHasNoMaterialMembershipReadinessOrDurabilityProof() {
        var fixture = fixture();
        var facts = fixture.ledger.facts(fixture.source).orElseThrow();
        assertFalse(facts.materialComplete());
        assertTrue(facts.dirty());
        for (var step : P11ReceiptLedger.MaterialStep.values()) {
            assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED, facts.material(step));
        }
        for (var step : P11ReceiptLedger.MembershipStep.values()) {
            assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED, facts.membership(step));
        }
        for (var step : P11ReceiptLedger.ReadinessStep.values()) {
            assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED, facts.readiness(step));
        }
        for (var step : P11ReceiptLedger.WriterStep.values()) {
            assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED, facts.writer(step));
        }
        assertTrue(fixture.ledger.discharge(fixture.source).isEmpty());
    }

    @Test
    void completeMaterialDoesNotGrantMembershipReadinessOrPersistence() {
        var fixture = fixture();
        var operation = begin(fixture);
        materialComplete(fixture, operation);
        var facts = fixture.ledger.facts(fixture.source).orElseThrow();
        assertTrue(facts.materialComplete());
        assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED,
                facts.membership(P11ReceiptLedger.MembershipStep.LISTENER));
        assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED,
                facts.readiness(P11ReceiptLedger.ReadinessStep.P7_SESSION));
        assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED,
                facts.writer(P11ReceiptLedger.WriterStep.PERSISTED_READBACK));
        assertTrue(fixture.ledger.beginWriter(operation).isEmpty());
    }

    @Test
    void partialAndUnknownObservationsCannotBeUpgradedByLaterSuccessClaims() {
        var fixture = fixture();
        var operation = begin(fixture);
        assertEquals(P11ReceiptLedger.Change.RECORDED, fixture.ledger.material(operation,
                P11ReceiptLedger.MaterialStep.REQUIRED_COPY_OR_LOAD, P11ReceiptLedger.Observation.UNKNOWN));
        assertEquals(P11ReceiptLedger.Change.REFUSED, fixture.ledger.material(operation,
                P11ReceiptLedger.MaterialStep.REQUIRED_COPY_OR_LOAD, P11ReceiptLedger.Observation.SUCCEEDED));
        assertEquals(P11ReceiptLedger.Change.DUPLICATE, fixture.ledger.material(operation,
                P11ReceiptLedger.MaterialStep.REQUIRED_COPY_OR_LOAD, P11ReceiptLedger.Observation.UNKNOWN));
        assertEquals(P11ReceiptLedger.Change.REFUSED, fixture.ledger.membership(operation,
                P11ReceiptLedger.MembershipStep.WORLD, P11ReceiptLedger.Observation.NOT_OBSERVED));
        fixture.ledger.finish(operation, P11ReceiptLedger.Terminal.COMPLETED);
        assertTrue(fixture.ledger.beginWriter(operation).isEmpty());
        assertFalse(fixture.ledger.facts(fixture.source).orElseThrow().materialComplete());
    }

    @Test
    void readinessStagesRemainIndependentAndSnapshotsAreImmutable() {
        var fixture = fixture();
        var operation = begin(fixture);
        var before = fixture.ledger.facts(fixture.source).orElseThrow();
        assertEquals(P11ReceiptLedger.Change.RECORDED, fixture.ledger.readiness(operation,
                P11ReceiptLedger.ReadinessStep.P4_PUBLICATION, P11ReceiptLedger.Observation.SUCCEEDED));
        assertEquals(P11ReceiptLedger.Change.RECORDED, fixture.ledger.readiness(operation,
                P11ReceiptLedger.ReadinessStep.E2, P11ReceiptLedger.Observation.FAILED));
        assertEquals(P11ReceiptLedger.Change.REFUSED, fixture.ledger.readiness(operation,
                P11ReceiptLedger.ReadinessStep.E2, P11ReceiptLedger.Observation.SUCCEEDED));
        var after = fixture.ledger.facts(fixture.source).orElseThrow();
        assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED,
                before.readiness(P11ReceiptLedger.ReadinessStep.P4_PUBLICATION));
        assertEquals(P11ReceiptLedger.Observation.SUCCEEDED,
                after.readiness(P11ReceiptLedger.ReadinessStep.P4_PUBLICATION));
        assertEquals(P11ReceiptLedger.Observation.FAILED,
                after.readiness(P11ReceiptLedger.ReadinessStep.E2));
        assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED,
                after.readiness(P11ReceiptLedger.ReadinessStep.C));
        assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED,
                after.readiness(P11ReceiptLedger.ReadinessStep.P7_INITIAL_SYNC));
    }

    @Test
    void writerRequiresExactOrderedWriteCloseReplaceAndReadbackObservations() {
        var fixture = fixture();
        var operation = begin(fixture);
        materialComplete(fixture, operation);
        fixture.ledger.finish(operation, P11ReceiptLedger.Terminal.COMPLETED);
        var writer = fixture.ledger.beginWriter(operation).orElseThrow();
        assertTrue(fixture.ledger.beginWriter(operation).isEmpty());
        assertEquals(P11ReceiptLedger.Change.REFUSED, fixture.ledger.writer(writer,
                P11ReceiptLedger.WriterStep.PERSISTED_READBACK, P11ReceiptLedger.Observation.SUCCEEDED));
        assertEquals(P11ReceiptLedger.Change.REFUSED,
                fixture.ledger.finishWriter(writer, P11ReceiptLedger.Terminal.COMPLETED));
        for (var step : P11ReceiptLedger.WriterStep.values()) {
            assertTrue(fixture.ledger.facts(fixture.source).orElseThrow().dirty());
            assertEquals(P11ReceiptLedger.Change.RECORDED,
                    fixture.ledger.writer(writer, step, P11ReceiptLedger.Observation.SUCCEEDED));
        }
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                fixture.ledger.finishWriter(writer, P11ReceiptLedger.Terminal.COMPLETED));
        assertFalse(fixture.ledger.facts(fixture.source).orElseThrow().dirty());
        assertEquals(P11ReceiptLedger.Change.DUPLICATE,
                fixture.ledger.finishWriter(writer, P11ReceiptLedger.Terminal.COMPLETED));
        assertEquals(P11ReceiptLedger.Change.TERMINAL,
                fixture.ledger.finishWriter(writer, P11ReceiptLedger.Terminal.FAILED));
        assertEquals(P11ReceiptLedger.Change.TERMINAL, fixture.ledger.writer(writer,
                P11ReceiptLedger.WriterStep.WRITE, P11ReceiptLedger.Observation.SUCCEEDED));
    }

    @Test
    void oldVersionWriterCannotClearNewDirtySource() {
        var fixture = fixture();
        var operation = begin(fixture);
        materialComplete(fixture, operation);
        fixture.ledger.finish(operation, P11ReceiptLedger.Terminal.COMPLETED);
        var writer = fixture.ledger.beginWriter(operation).orElseThrow();
        var newer = fixture.ledger.advanceMutationVersion(fixture.source).orElseThrow();
        assertEquals(fixture.source.epoch(), newer.epoch());
        assertEquals(fixture.source.version() + 1, newer.version());
        assertSame(fixture.source.identity(), newer.identity());
        for (var step : P11ReceiptLedger.WriterStep.values()) {
            assertEquals(P11ReceiptLedger.Change.STALE,
                    fixture.ledger.writer(writer, step, P11ReceiptLedger.Observation.SUCCEEDED));
        }
        assertEquals(P11ReceiptLedger.Change.STALE,
                fixture.ledger.finishWriter(writer, P11ReceiptLedger.Terminal.COMPLETED));
        assertTrue(fixture.ledger.facts(newer).orElseThrow().dirty());
        assertNull(fixture.ledger.facts(newer).orElseThrow().writerTerminal());
        assertTrue(fixture.ledger.facts(fixture.source).isEmpty());
        assertTrue(fixture.ledger.discharge(newer).isEmpty());
    }

    @Test
    void oldEpochReceiptCannotCompleteOrFaultNewActorSource() {
        var fixture = fixture();
        var oldOperation = begin(fixture);
        materialComplete(fixture, oldOperation);
        fixture.ledger.finish(oldOperation, P11ReceiptLedger.Terminal.COMPLETED);
        completeWriter(fixture, fixture.ledger.beginWriter(oldOperation).orElseThrow());
        var b = fixture.identities.bindModel(fixture.identities.modelActor(UUID_A, 17),
                fixture.connection).orElseThrow();
        var newSource = fixture.ledger.changeSource(fixture.source, b,
                P11ReceiptLedger.Disposition.CANDIDATE).orElseThrow();
        assertEquals(2, newSource.epoch());
        assertEquals(0, newSource.version());
        var newer = fixture.ledger.begin(newSource, 2, 42,
                P11ReceiptLedger.OperationKind.DEATH).orElseThrow();
        assertEquals(P11ReceiptLedger.Change.STALE,
                fixture.ledger.finish(oldOperation, P11ReceiptLedger.Terminal.FAILED));
        assertEquals(P11ReceiptLedger.Change.STALE, fixture.ledger.material(oldOperation,
                P11ReceiptLedger.MaterialStep.CONSTRUCTOR, P11ReceiptLedger.Observation.SUCCEEDED));
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                fixture.ledger.finish(newer, P11ReceiptLedger.Terminal.UNKNOWN));
        assertEquals(P11ReceiptLedger.Terminal.UNKNOWN,
                fixture.ledger.facts(newSource).orElseThrow().operationTerminal());
    }

    @Test
    void epochChangeRefusesDirtyOrUnresolvedResponsibilitiesInsteadOfDroppingThem() {
        var fixture = fixture();
        var operation = begin(fixture);
        var b = fixture.identities.bindModel(fixture.identities.modelActor(UUID_A, 17),
                fixture.connection).orElseThrow();
        assertTrue(fixture.ledger.changeSource(fixture.source, b,
                P11ReceiptLedger.Disposition.CANDIDATE).isEmpty());
        assertTrue(fixture.ledger.facts(fixture.source).orElseThrow().dirty());
        fixture.ledger.finish(operation, P11ReceiptLedger.Terminal.UNKNOWN);
        assertTrue(fixture.ledger.changeSource(fixture.source, b,
                P11ReceiptLedger.Disposition.CANDIDATE).isEmpty());
        assertEquals(P11ReceiptLedger.Terminal.UNKNOWN,
                fixture.ledger.facts(fixture.source).orElseThrow().operationTerminal());
        assertEquals(1, fixture.ledger.retainedSources());
    }

    @Test
    void unknownOperationCannotBeLaunderedByStartingAnotherOperation() {
        var fixture = fixture();
        var operation = begin(fixture);
        fixture.ledger.finish(operation, P11ReceiptLedger.Terminal.UNKNOWN);
        assertTrue(fixture.ledger.begin(fixture.source, 1, 42,
                P11ReceiptLedger.OperationKind.DEATH).isEmpty());
    }

    @Test
    void completedWriterFactsCannotBeReusedAsANewWriterReceipt() {
        var fixture = fixture();
        var operation = begin(fixture);
        materialComplete(fixture, operation);
        fixture.ledger.finish(operation, P11ReceiptLedger.Terminal.COMPLETED);
        var writer = fixture.ledger.beginWriter(operation).orElseThrow();
        completeWriter(fixture, writer);
        assertTrue(fixture.ledger.beginWriter(operation).isEmpty());
    }

    @Test
    void finishIsIdempotentAndStaleFinishCannotReleaseNewOperation() {
        var fixture = fixture();
        var old = begin(fixture);
        assertTrue(fixture.ledger.begin(fixture.source, 1, 42,
                P11ReceiptLedger.OperationKind.DEATH).isEmpty());
        materialComplete(fixture, old);
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                fixture.ledger.finish(old, P11ReceiptLedger.Terminal.COMPLETED));
        assertEquals(P11ReceiptLedger.Change.DUPLICATE,
                fixture.ledger.finish(old, P11ReceiptLedger.Terminal.COMPLETED));
        assertEquals(P11ReceiptLedger.Change.TERMINAL,
                fixture.ledger.finish(old, P11ReceiptLedger.Terminal.FAILED));
        var newer = fixture.ledger.begin(fixture.source, 1, 42,
                P11ReceiptLedger.OperationKind.DEATH).orElseThrow();
        assertEquals(P11ReceiptLedger.Change.STALE,
                fixture.ledger.finish(old, P11ReceiptLedger.Terminal.COMPLETED));
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                fixture.ledger.finish(newer, P11ReceiptLedger.Terminal.UNKNOWN));
    }

    @Test
    void nextOperationCannotBorrowMaterialMembershipOrReadinessFromPreviousAttempt() {
        var fixture = fixture();
        var old = begin(fixture);
        materialComplete(fixture, old);
        fixture.ledger.membership(old, P11ReceiptLedger.MembershipStep.LISTENER,
                P11ReceiptLedger.Observation.SUCCEEDED);
        fixture.ledger.readiness(old, P11ReceiptLedger.ReadinessStep.P7_SESSION,
                P11ReceiptLedger.Observation.SUCCEEDED);
        fixture.ledger.finish(old, P11ReceiptLedger.Terminal.COMPLETED);
        var newer = fixture.ledger.begin(fixture.source, 2, 42,
                P11ReceiptLedger.OperationKind.DEATH).orElseThrow();
        var facts = fixture.ledger.facts(fixture.source).orElseThrow();
        assertFalse(facts.materialComplete());
        assertTrue(facts.dirty());
        assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED,
                facts.membership(P11ReceiptLedger.MembershipStep.LISTENER));
        assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED,
                facts.readiness(P11ReceiptLedger.ReadinessStep.P7_SESSION));
        fixture.ledger.finish(newer, P11ReceiptLedger.Terminal.COMPLETED);
        assertTrue(fixture.ledger.beginWriter(newer).isEmpty());
        assertTrue(fixture.ledger.discharge(fixture.source).isEmpty());
    }

    @Test
    void oldCompletedWriterCannotDischargeAnotherOperationAtTheSameSourceVersion() {
        var fixture = fixture();
        var old = begin(fixture);
        materialComplete(fixture, old);
        fixture.ledger.finish(old, P11ReceiptLedger.Terminal.COMPLETED);
        var oldWriter = fixture.ledger.beginWriter(old).orElseThrow();
        completeWriter(fixture, oldWriter);
        var newer = fixture.ledger.begin(fixture.source, 2, 42,
                P11ReceiptLedger.OperationKind.DEATH).orElseThrow();
        materialComplete(fixture, newer);
        fixture.ledger.finish(newer, P11ReceiptLedger.Terminal.COMPLETED);
        assertTrue(fixture.ledger.discharge(fixture.source).isEmpty());
        assertEquals(P11ReceiptLedger.Change.STALE,
                fixture.ledger.finishWriter(oldWriter, P11ReceiptLedger.Terminal.COMPLETED));
        var facts = fixture.ledger.facts(fixture.source).orElseThrow();
        for (var step : P11ReceiptLedger.WriterStep.values()) {
            assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED, facts.writer(step));
        }
        var newWriter = fixture.ledger.beginWriter(newer).orElseThrow();
        assertTrue(fixture.ledger.discharge(fixture.source).isEmpty());
        completeWriter(fixture, newWriter);
        assertTrue(fixture.ledger.discharge(fixture.source).isPresent());
    }

    @Test
    void completedOperationWithIncompleteMaterialCannotEraseUnresolvedFactsByReplacement() {
        var fixture = fixture();
        var old = begin(fixture);
        fixture.ledger.material(old, P11ReceiptLedger.MaterialStep.REQUIRED_COPY_OR_LOAD,
                P11ReceiptLedger.Observation.UNKNOWN);
        fixture.ledger.finish(old, P11ReceiptLedger.Terminal.COMPLETED);
        assertTrue(fixture.ledger.begin(fixture.source, 2, 42,
                P11ReceiptLedger.OperationKind.DEATH).isEmpty());
        assertEquals(P11ReceiptLedger.Observation.UNKNOWN,
                fixture.ledger.facts(fixture.source).orElseThrow()
                        .material(P11ReceiptLedger.MaterialStep.REQUIRED_COPY_OR_LOAD));
        assertTrue(fixture.ledger.facts(fixture.source).orElseThrow().dirty());
    }

    @Test
    void mutationVersionCannotLaunderAnIncompleteOperationIntoANewAdmission() {
        var fixture = fixture();
        var old = begin(fixture);
        fixture.ledger.material(old, P11ReceiptLedger.MaterialStep.REQUIRED_COPY_OR_LOAD,
                P11ReceiptLedger.Observation.UNKNOWN);
        fixture.ledger.finish(old, P11ReceiptLedger.Terminal.COMPLETED);
        var mutated = fixture.ledger.advanceMutationVersion(fixture.source).orElseThrow();
        assertTrue(fixture.ledger.begin(mutated, 2, 42,
                P11ReceiptLedger.OperationKind.DEATH).isEmpty());
        var facts = fixture.ledger.facts(mutated).orElseThrow();
        assertEquals(P11ReceiptLedger.Observation.UNKNOWN,
                facts.material(P11ReceiptLedger.MaterialStep.REQUIRED_COPY_OR_LOAD));
        assertTrue(facts.dirty());
        assertTrue(fixture.ledger.beginWriter(old).isEmpty());
        assertTrue(fixture.ledger.discharge(mutated).isEmpty());
    }

    @Test
    void sameContextMutationAllowsOperationFactsToFinishButDoesNotGrantNewVersionWriter() {
        var fixture = fixture();
        var operation = begin(fixture);
        fixture.ledger.material(operation, P11ReceiptLedger.MaterialStep.CONSTRUCTOR,
                P11ReceiptLedger.Observation.SUCCEEDED);
        fixture.ledger.membership(operation, P11ReceiptLedger.MembershipStep.WORLD,
                P11ReceiptLedger.Observation.SUCCEEDED);
        fixture.ledger.readiness(operation, P11ReceiptLedger.ReadinessStep.NATIVE_SYNC,
                P11ReceiptLedger.Observation.SUCCEEDED);
        var mutated = fixture.ledger.advanceMutationVersion(fixture.source).orElseThrow();
        for (var step : P11ReceiptLedger.MaterialStep.values()) {
            var result = fixture.ledger.material(operation, step, P11ReceiptLedger.Observation.SUCCEEDED);
            assertEquals(step == P11ReceiptLedger.MaterialStep.CONSTRUCTOR
                    ? P11ReceiptLedger.Change.DUPLICATE : P11ReceiptLedger.Change.RECORDED, result);
        }
        assertEquals(P11ReceiptLedger.Change.RECORDED, fixture.ledger.membership(operation,
                P11ReceiptLedger.MembershipStep.LISTENER, P11ReceiptLedger.Observation.SUCCEEDED));
        assertEquals(P11ReceiptLedger.Change.RECORDED, fixture.ledger.readiness(operation,
                P11ReceiptLedger.ReadinessStep.P7_INITIAL_SYNC, P11ReceiptLedger.Observation.SUCCEEDED));
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                fixture.ledger.finish(operation, P11ReceiptLedger.Terminal.COMPLETED));
        var facts = fixture.ledger.facts(mutated).orElseThrow();
        assertTrue(facts.materialComplete());
        assertEquals(P11ReceiptLedger.Observation.SUCCEEDED,
                facts.membership(P11ReceiptLedger.MembershipStep.WORLD));
        assertEquals(P11ReceiptLedger.Observation.SUCCEEDED,
                facts.readiness(P11ReceiptLedger.ReadinessStep.NATIVE_SYNC));
        assertTrue(facts.dirty());
        assertTrue(fixture.ledger.beginWriter(operation).isEmpty());
        assertTrue(fixture.ledger.discharge(mutated).isEmpty());
        var newer = fixture.ledger.begin(mutated, 2, 42,
                P11ReceiptLedger.OperationKind.DEATH).orElseThrow();
        assertTrue(fixture.ledger.beginWriter(newer).isEmpty());
        assertFalse(fixture.ledger.facts(mutated).orElseThrow().materialComplete());
    }

    @Test
    void newOperationCannotDiscardAnInflightFailedOrUnknownWriter() {
        for (var terminal : new P11ReceiptLedger.Terminal[] {
                null, P11ReceiptLedger.Terminal.FAILED, P11ReceiptLedger.Terminal.UNKNOWN }) {
            var fixture = fixture();
            var old = begin(fixture);
            materialComplete(fixture, old);
            fixture.ledger.finish(old, P11ReceiptLedger.Terminal.COMPLETED);
            var writer = fixture.ledger.beginWriter(old).orElseThrow();
            fixture.ledger.writer(writer, P11ReceiptLedger.WriterStep.WRITE,
                    P11ReceiptLedger.Observation.UNKNOWN);
            if (terminal != null) {
                fixture.ledger.finishWriter(writer, terminal);
            }
            assertTrue(fixture.ledger.begin(fixture.source, 2, 42,
                    P11ReceiptLedger.OperationKind.DEATH).isEmpty());
            var facts = fixture.ledger.facts(fixture.source).orElseThrow();
            assertEquals(P11ReceiptLedger.Observation.UNKNOWN,
                    facts.writer(P11ReceiptLedger.WriterStep.WRITE));
            assertTrue(facts.dirty());
            assertTrue(fixture.ledger.discharge(fixture.source).isEmpty());
        }
    }

    @Test
    void wrongOwnerSourceAndAttemptAreInertDespiteMatchingScalarCoordinates() {
        var first = fixture();
        var second = fixture();
        var foreign = begin(second);
        assertEquals(first.source.epoch(), second.source.epoch());
        assertEquals(P11ReceiptLedger.Change.STALE,
                first.ledger.finish(foreign, P11ReceiptLedger.Terminal.COMPLETED));
        assertTrue(first.ledger.facts(second.source).isEmpty());
        assertTrue(first.ledger.begin(second.source, 1, 41,
                P11ReceiptLedger.OperationKind.DEATH).isEmpty());
        assertTrue(first.ledger.changeSource(first.source, second.capture,
                P11ReceiptLedger.Disposition.LIVE).isEmpty());
    }

    @Test
    void requestZeroOnlyBelongsToServerIssuedKindsAndNeverImpliesCompletion() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var ledger = new P11ReceiptLedger(owner);
        var captured = owner.modelActorless(UUID_A, owner.modelConnection()).orElseThrow();
        var source = ledger.firstSource(captured, P11ReceiptLedger.Disposition.UNKNOWN).orElseThrow();
        assertTrue(ledger.begin(source, 1, 0, P11ReceiptLedger.OperationKind.DEATH).isEmpty());
        assertTrue(ledger.begin(source, 1, 0, P11ReceiptLedger.OperationKind.END).isEmpty());
        assertTrue(ledger.begin(source, 0, 0, P11ReceiptLedger.OperationKind.JOIN).isEmpty());
        assertTrue(ledger.begin(source, 1, -1, P11ReceiptLedger.OperationKind.JOIN).isEmpty());
        var request = ledger.begin(source, 1, 0, P11ReceiptLedger.OperationKind.JOIN).orElseThrow();
        assertEquals(0, request.request());
        assertEquals(0, request.source().identity().actorGeneration());
        assertTrue(ledger.beginWriter(request).isEmpty());
    }

    @Test
    void controlRetirementDoesNotDropDirtyFactsAndInvalidatesOldDischargeProof() {
        var owner = P11IdentityOwner.isolatedModel(1);
        var ledger = new P11ReceiptLedger(owner);
        var captured = owner.bindModel(owner.modelActor(UUID_A, 1), owner.modelConnection()).orElseThrow();
        var beforeSource = ledger.dischargeUnusedAccount(captured.account()).orElseThrow();
        var source = ledger.firstSource(captured, P11ReceiptLedger.Disposition.LIVE).orElseThrow();
        assertTrue(owner.retireConnection(captured));
        assertFalse(owner.releaseUnboundAccount(beforeSource));
        assertEquals(1, owner.retainedAccounts());
        assertEquals(1, ledger.retainedSources());
        assertTrue(ledger.facts(source).orElseThrow().dirty());
        assertTrue(ledger.dischargeUnusedAccount(captured.account()).isEmpty());
        assertTrue(ledger.discharge(source).isEmpty());
    }

    @Test
    void completeIsolatedFactsDischargeExactlyOneAccountWithoutNativeQualificationClaim() {
        var fixture = fixture();
        var operation = begin(fixture);
        materialComplete(fixture, operation);
        fixture.ledger.finish(operation, P11ReceiptLedger.Terminal.COMPLETED);
        var writer = fixture.ledger.beginWriter(operation).orElseThrow();
        for (var step : P11ReceiptLedger.WriterStep.values()) {
            fixture.ledger.writer(writer, step, P11ReceiptLedger.Observation.SUCCEEDED);
        }
        fixture.ledger.finishWriter(writer, P11ReceiptLedger.Terminal.COMPLETED);
        var discharge = fixture.ledger.discharge(fixture.source).orElseThrow();
        assertTrue(fixture.identities.retireConnection(fixture.capture));
        assertTrue(fixture.identities.releaseUnboundAccount(discharge));
        assertFalse(fixture.identities.releaseUnboundAccount(discharge));
        assertEquals(0, fixture.identities.retainedAccounts());
        assertEquals(0, fixture.ledger.retainedSources());
        assertFalse(fixture.identities.liveCurrent(fixture.capture));
    }

    @Test
    void dischargeAndSourceReentryKeepAccountEpochMonotonicAndRevokeOldDischarge() {
        var fixture = fixture();
        var operation = begin(fixture);
        materialComplete(fixture, operation);
        fixture.ledger.finish(operation, P11ReceiptLedger.Terminal.COMPLETED);
        completeWriter(fixture, fixture.ledger.beginWriter(operation).orElseThrow());
        var oldDischarge = fixture.ledger.discharge(fixture.source).orElseThrow();
        var next = fixture.ledger.firstSource(fixture.capture,
                P11ReceiptLedger.Disposition.LIVE).orElseThrow();
        assertEquals(fixture.source.epoch() + 1, next.epoch());
        assertEquals(0, next.version());
        assertTrue(fixture.ledger.facts(fixture.source).isEmpty());
        assertTrue(fixture.ledger.facts(next).orElseThrow().dirty());
        assertEquals(1, fixture.ledger.retainedSources());
        assertTrue(fixture.identities.retireConnection(fixture.capture));
        assertFalse(fixture.identities.releaseUnboundAccount(oldDischarge));
        assertEquals(1, fixture.identities.retainedAccounts());
    }

    @Test
    void refusedStaleSourceCreationCannotRevokeSuccessorAccountDischarge() {
        var identities = P11IdentityOwner.isolatedModel(1);
        var connection = identities.modelConnection();
        var old = identities.bindModel(identities.modelActor(UUID_A, 1), connection).orElseThrow();
        var current = identities.bindModel(identities.modelActor(UUID_A, 2), connection).orElseThrow();
        var ledger = new P11ReceiptLedger(identities);
        var discharge = ledger.dischargeUnusedAccount(current.account()).orElseThrow();
        assertTrue(ledger.firstSource(old, P11ReceiptLedger.Disposition.LIVE).isEmpty());
        assertTrue(identities.retireConnection(current));
        assertTrue(identities.releaseUnboundAccount(discharge));
    }

    @Test
    void stoppedOwnerRejectsAllLateResultsAndDropsBoundedFacts() {
        var fixture = fixture();
        var operation = begin(fixture);
        fixture.ledger.stop();
        fixture.identities.stop();
        assertEquals(P11ReceiptLedger.Change.STALE,
                fixture.ledger.finish(operation, P11ReceiptLedger.Terminal.COMPLETED));
        assertTrue(fixture.ledger.facts(fixture.source).isEmpty());
        assertEquals(0, fixture.ledger.retainedSources());
    }

    private static P11ReceiptLedger.OperationReceipt begin(Fixture fixture) {
        return fixture.ledger.begin(fixture.source, 1, 41,
                P11ReceiptLedger.OperationKind.DEATH).orElseThrow();
    }

    private static void materialComplete(Fixture fixture, P11ReceiptLedger.OperationReceipt operation) {
        for (var step : P11ReceiptLedger.MaterialStep.values()) {
            assertEquals(P11ReceiptLedger.Change.RECORDED, fixture.ledger.material(operation, step,
                    P11ReceiptLedger.Observation.SUCCEEDED));
        }
    }

    private static void completeWriter(Fixture fixture, P11ReceiptLedger.WriterReceipt writer) {
        for (var step : P11ReceiptLedger.WriterStep.values()) {
            assertEquals(P11ReceiptLedger.Change.RECORDED,
                    fixture.ledger.writer(writer, step, P11ReceiptLedger.Observation.SUCCEEDED));
        }
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                fixture.ledger.finishWriter(writer, P11ReceiptLedger.Terminal.COMPLETED));
    }

    private static Fixture fixture() {
        var identities = P11IdentityOwner.isolatedModel(2);
        var connection = identities.modelConnection();
        var capture = identities.bindModel(identities.modelActor(UUID_A, 17), connection).orElseThrow();
        var ledger = new P11ReceiptLedger(identities);
        var source = ledger.firstSource(capture, P11ReceiptLedger.Disposition.LIVE).orElseThrow();
        return new Fixture(identities, connection, capture, ledger, source);
    }

    private record Fixture(P11IdentityOwner identities, P11IdentityOwner.ModelConnection connection,
            P11IdentityOwner.CapturedIdentity capture, P11ReceiptLedger ledger,
            P11ReceiptLedger.Source source) {}
}
