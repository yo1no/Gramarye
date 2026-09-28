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

    @Test
    void sourceMaterialCanQualifyBeforeOuterLogoutTerminalWithoutGrantingReadiness() {
        var fixture = dataFixture(false);
        assertTrue(fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).isEmpty());
        var material = fixture.ledger.beginMaterial(fixture.source).orElseThrow();
        assertTrue(fixture.ledger.beginMaterial(fixture.source).isEmpty());
        assertEquals(P11ReceiptLedger.Change.REFUSED,
                fixture.ledger.finishMaterial(material, P11ReceiptLedger.Terminal.COMPLETED));
        completeMaterial(fixture.ledger, material);
        var writer = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        assertTrue(fixture.ledger.mayWrite(writer));
        var facts = fixture.ledger.facts(fixture.source).orElseThrow();
        assertTrue(facts.materialComplete());
        assertNull(facts.operationTerminal());
        assertEquals(P11ReceiptLedger.Observation.NOT_OBSERVED,
                facts.readiness(P11ReceiptLedger.ReadinessStep.P7_SESSION));
        assertTrue(fixture.ledger.begin(fixture.source, 1, 41,
                P11ReceiptLedger.OperationKind.DEATH).isEmpty());
    }

    @Test
    void writerKindsUseOnlyTheirNativeStepsAndReadbackIsSeparate() {
        for (var kind : P11ReceiptLedger.WriterKind.values()) {
            var fixture = dataFixture(true);
            var writer = fixture.ledger.beginSave(fixture.source, kind).orElseThrow();
            assertTrue(fixture.ledger.beginSave(fixture.source, kind).isEmpty());
            assertEquals(P11ReceiptLedger.Change.REFUSED,
                    fixture.ledger.finishSave(writer, P11ReceiptLedger.Terminal.COMPLETED));
            completePhysical(fixture.ledger, writer);
            assertFalse(fixture.ledger.physicalFacts(fixture.source, kind).orElseThrow().dirty());
            assertEquals(P11ReceiptLedger.Change.DUPLICATE,
                    fixture.ledger.finishSave(writer, P11ReceiptLedger.Terminal.COMPLETED));
            assertFalse(fixture.ledger.mayWrite(writer));
            if (kind == P11ReceiptLedger.WriterKind.CACHE) {
                assertTrue(fixture.ledger.beginReadback(fixture.source, kind).isEmpty());
            } else {
                var readback = fixture.ledger.beginReadback(fixture.source, kind).orElseThrow();
                assertFalse(fixture.ledger.persistedReadbackCurrent(readback));
                assertEquals(P11ReceiptLedger.Change.RECORDED,
                        fixture.ledger.finishReadback(readback, P11ReceiptLedger.Observation.SUCCEEDED));
                assertTrue(fixture.ledger.persistedReadbackCurrent(readback));
            }
        }
    }

    @Test
    void cacheAndJsonCannotInventReplaceOrDischargeOtherPhysicalSourceDirty() {
        var fixture = dataFixture(true);
        fixture.ledger.markDirty(fixture.source, P11ReceiptLedger.WriterKind.PLAYER_DATA);
        fixture.ledger.markDirty(fixture.source, P11ReceiptLedger.WriterKind.LEVEL_PLAYER);
        var cache = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.CACHE).orElseThrow();
        assertEquals(P11ReceiptLedger.Change.REFUSED, fixture.ledger.physical(cache,
                P11ReceiptLedger.PhysicalStep.WRITE, P11ReceiptLedger.Observation.SUCCEEDED));
        completePhysical(fixture.ledger, cache);
        assertTrue(fixture.ledger.facts(fixture.source).orElseThrow().dirty());
        var json = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.ADVANCEMENTS).orElseThrow();
        assertEquals(P11ReceiptLedger.Change.REFUSED, fixture.ledger.physical(json,
                P11ReceiptLedger.PhysicalStep.REPLACE, P11ReceiptLedger.Observation.SUCCEEDED));
        completePhysical(fixture.ledger, json);
        assertTrue(fixture.ledger.physicalFacts(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow().dirty());
        assertTrue(fixture.ledger.physicalFacts(fixture.source,
                P11ReceiptLedger.WriterKind.LEVEL_PLAYER).orElseThrow().dirty());
        var player = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        completePhysical(fixture.ledger, player);
        assertTrue(fixture.ledger.physicalFacts(fixture.source,
                P11ReceiptLedger.WriterKind.LEVEL_PLAYER).orElseThrow().dirty());
        assertTrue(fixture.ledger.discharge(fixture.source).isEmpty());
    }

    @Test
    void nativeAttemptFailureIsImmutableAndNewIoRetryDoesNotReplayMaterial() {
        var fixture = dataFixture(true);
        var failed = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        fixture.ledger.physical(failed, P11ReceiptLedger.PhysicalStep.ENCODE,
                P11ReceiptLedger.Observation.SUCCEEDED);
        fixture.ledger.physical(failed, P11ReceiptLedger.PhysicalStep.WRITE,
                P11ReceiptLedger.Observation.SUCCEEDED);
        fixture.ledger.physical(failed, P11ReceiptLedger.PhysicalStep.CLOSE,
                P11ReceiptLedger.Observation.FAILED);
        assertEquals(P11ReceiptLedger.Change.REFUSED, fixture.ledger.physical(failed,
                P11ReceiptLedger.PhysicalStep.CLOSE, P11ReceiptLedger.Observation.SUCCEEDED));
        assertEquals(P11ReceiptLedger.Change.REFUSED,
                fixture.ledger.finishSave(failed, P11ReceiptLedger.Terminal.COMPLETED));
        fixture.ledger.finishSave(failed, P11ReceiptLedger.Terminal.FAILED);
        var snapshot = fixture.ledger.attemptFacts(failed).orElseThrow();
        var retry = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        completePhysical(fixture.ledger, retry);
        assertEquals(P11ReceiptLedger.Terminal.FAILED, snapshot.terminal());
        assertEquals(P11ReceiptLedger.Observation.FAILED,
                snapshot.observation(P11ReceiptLedger.PhysicalStep.CLOSE));
        assertEquals(P11ReceiptLedger.Change.TERMINAL,
                fixture.ledger.finishSave(failed, P11ReceiptLedger.Terminal.COMPLETED));
        assertTrue(fixture.ledger.beginMaterial(fixture.source).isEmpty());
    }

    @Test
    void failedWriteStillRecordsItsActualSuccessfulFinallyClose() {
        var fixture = dataFixture(true);
        var writer = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        fixture.ledger.physical(writer, P11ReceiptLedger.PhysicalStep.ENCODE,
                P11ReceiptLedger.Observation.SUCCEEDED);
        assertEquals(P11ReceiptLedger.Change.RECORDED, fixture.ledger.physical(writer,
                P11ReceiptLedger.PhysicalStep.WRITE, P11ReceiptLedger.Observation.FAILED));
        assertEquals(P11ReceiptLedger.Change.RECORDED, fixture.ledger.physical(writer,
                P11ReceiptLedger.PhysicalStep.CLOSE, P11ReceiptLedger.Observation.SUCCEEDED));
        assertFalse(fixture.ledger.mayWrite(writer));
        assertEquals(P11ReceiptLedger.Change.REFUSED,
                fixture.ledger.finishSave(writer, P11ReceiptLedger.Terminal.COMPLETED));
        fixture.ledger.finishSave(writer, P11ReceiptLedger.Terminal.FAILED);
        var facts = fixture.ledger.attemptFacts(writer).orElseThrow();
        assertEquals(P11ReceiptLedger.Observation.FAILED,
                facts.observation(P11ReceiptLedger.PhysicalStep.WRITE));
        assertEquals(P11ReceiptLedger.Observation.SUCCEEDED,
                facts.observation(P11ReceiptLedger.PhysicalStep.CLOSE));
        assertTrue(fixture.ledger.physicalFacts(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow().dirty());
    }

    @Test
    void stalePhysicalCompletionRetainsItsFactsWithoutClearingNewMutationDirty() {
        var fixture = dataFixture(true);
        var old = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        for (var step : new P11ReceiptLedger.PhysicalStep[] {
                P11ReceiptLedger.PhysicalStep.ENCODE, P11ReceiptLedger.PhysicalStep.WRITE,
                P11ReceiptLedger.PhysicalStep.CLOSE, P11ReceiptLedger.PhysicalStep.REPLACE }) {
            fixture.ledger.physical(old, step, P11ReceiptLedger.Observation.SUCCEEDED);
        }
        var newer = fixture.ledger.advanceMutationVersion(fixture.source).orElseThrow();
        assertFalse(fixture.ledger.mayWrite(old));
        assertEquals(P11ReceiptLedger.Change.STALE,
                fixture.ledger.finishSave(old, P11ReceiptLedger.Terminal.COMPLETED));
        assertEquals(P11ReceiptLedger.Terminal.COMPLETED,
                fixture.ledger.attemptFacts(old).orElseThrow().terminal());
        assertTrue(fixture.ledger.physicalFacts(newer,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow().dirty());
        assertTrue(fixture.ledger.beginSave(newer, P11ReceiptLedger.WriterKind.PLAYER_DATA).isPresent());
    }

    @Test
    void completedCallbackPrefixCannotDischargeSubsequentNativeTailVersion() {
        var fixture = dataFixture(true);
        var player = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        var level = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.LEVEL_PLAYER).orElseThrow();
        completePhysical(fixture.ledger, player);
        completePhysical(fixture.ledger, level);
        var readback = fixture.ledger.beginReadback(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        fixture.ledger.finishReadback(readback, P11ReceiptLedger.Observation.SUCCEEDED);
        assertTrue(fixture.ledger.persistedReadbackCurrent(readback));
        assertFalse(fixture.ledger.physicalFacts(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow().dirty());
        assertFalse(fixture.ledger.physicalFacts(fixture.source,
                P11ReceiptLedger.WriterKind.LEVEL_PLAYER).orElseThrow().dirty());
        var tail = fixture.ledger.advanceMutationVersion(fixture.source).orElseThrow();
        assertEquals(fixture.source.epoch(), tail.epoch());
        assertEquals(fixture.source.version() + 1, tail.version());
        assertTrue(fixture.ledger.facts(tail).orElseThrow().materialComplete());
        assertFalse(fixture.ledger.persistedReadbackCurrent(readback));
        for (var kind : java.util.List.of(P11ReceiptLedger.WriterKind.PLAYER_DATA,
                P11ReceiptLedger.WriterKind.LEVEL_PLAYER)) {
            fixture.ledger.markDirty(tail, kind);
            assertTrue(fixture.ledger.physicalFacts(tail, kind).orElseThrow().dirty());
            completePhysical(fixture.ledger, fixture.ledger.beginSave(tail, kind).orElseThrow());
            assertFalse(fixture.ledger.physicalFacts(tail, kind).orElseThrow().dirty());
        }
        assertEquals(P11ReceiptLedger.Terminal.COMPLETED, fixture.ledger.attemptFacts(player).orElseThrow().terminal());
        assertEquals(P11ReceiptLedger.Terminal.COMPLETED, fixture.ledger.attemptFacts(level).orElseThrow().terminal());
    }

    @Test
    void sameSourceKindMutationRevokesReplacementAndReadbackWithoutChangingOtherKinds() {
        var fixture = dataFixture(true);
        var player = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        var stats = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.STATISTICS).orElseThrow();
        var readback = fixture.ledger.beginReadback(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        fixture.ledger.finishReadback(readback, P11ReceiptLedger.Observation.SUCCEEDED);
        assertTrue(fixture.ledger.persistedReadbackCurrent(readback));
        fixture.ledger.markDirty(fixture.source, P11ReceiptLedger.WriterKind.PLAYER_DATA);
        assertFalse(fixture.ledger.mayWrite(player));
        assertTrue(fixture.ledger.mayWrite(stats));
        assertFalse(fixture.ledger.persistedReadbackCurrent(readback));
    }

    @Test
    void completeDirtyHandoffPreservesResponsibilitiesAndRevokesOldWriterBeforeCompletion() {
        var fixture = dataFixture(true);
        var oldWriter = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        var nextActor = fixture.identities.modelActor(UUID_A, 17);
        var candidateIdentity = fixture.identities.captureModelSource(nextActor).orElseThrow();
        var candidate = fixture.ledger.beginHandoff(fixture.source, candidateIdentity,
                P11ReceiptLedger.Disposition.LIVE).orElseThrow();
        assertSame(fixture.source, candidate.selectedSource());
        assertTrue(fixture.ledger.publishHandoff(candidate).isEmpty());
        assertTrue(fixture.ledger.facts(fixture.source).orElseThrow().dirty());
        assertTrue(fixture.ledger.mayWrite(oldWriter));
        completeMaterial(fixture.ledger, candidate);
        var next = fixture.ledger.publishHandoff(candidate).orElseThrow();
        assertEquals(fixture.source.epoch() + 1, next.epoch());
        assertTrue(fixture.ledger.facts(next).orElseThrow().dirty());
        assertTrue(fixture.ledger.physicalFacts(next,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow().dirty());
        assertFalse(fixture.identities.ownsData(fixture.identity));
        assertTrue(fixture.identities.matchesModelSource(next.dataIdentity(), nextActor));
        assertFalse(fixture.ledger.mayWrite(oldWriter));
        assertEquals(P11ReceiptLedger.Change.STALE, fixture.ledger.physical(oldWriter,
                P11ReceiptLedger.PhysicalStep.ENCODE, P11ReceiptLedger.Observation.FAILED));
        assertEquals(P11ReceiptLedger.Change.STALE,
                fixture.ledger.finishSave(oldWriter, P11ReceiptLedger.Terminal.FAILED));
        assertEquals(P11ReceiptLedger.Observation.FAILED,
                fixture.ledger.attemptFacts(oldWriter).orElseThrow()
                        .observation(P11ReceiptLedger.PhysicalStep.ENCODE));
        assertTrue(fixture.ledger.beginSave(next, P11ReceiptLedger.WriterKind.PLAYER_DATA).isPresent());
        assertTrue(fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).isEmpty());
    }

    @Test
    void candidateCannotLaunderMissingMaterialsOrAChangedSelectedSource() {
        var fixture = dataFixture(true);
        var next = fixture.identities.captureModelSource(
                fixture.identities.modelActor(UUID_A, 17)).orElseThrow();
        var candidate = fixture.ledger.beginHandoff(fixture.source, next,
                P11ReceiptLedger.Disposition.LIVE).orElseThrow();
        completeMaterial(fixture.ledger, candidate);
        var newer = fixture.ledger.advanceMutationVersion(fixture.source).orElseThrow();
        assertTrue(fixture.ledger.publishHandoff(candidate).isEmpty());
        assertTrue(fixture.ledger.facts(newer).orElseThrow().dirty());
        assertTrue(fixture.identities.ownsData(fixture.identity));
        assertTrue(fixture.ledger.facts(candidate.source()).isEmpty());
    }

    @Test
    void failedCandidateKeepsOldSourceAndCannotBeMarkedComplete() {
        var fixture = dataFixture(true);
        var next = fixture.identities.captureModelSource(
                fixture.identities.modelActor(UUID_A, 17)).orElseThrow();
        var candidate = fixture.ledger.beginHandoff(fixture.source, next,
                P11ReceiptLedger.Disposition.CANDIDATE).orElseThrow();
        fixture.ledger.material(candidate, P11ReceiptLedger.MaterialStep.REQUIRED_COPY_OR_LOAD,
                P11ReceiptLedger.Observation.UNKNOWN);
        assertEquals(P11ReceiptLedger.Change.REFUSED, fixture.ledger.material(candidate,
                P11ReceiptLedger.MaterialStep.REQUIRED_COPY_OR_LOAD, P11ReceiptLedger.Observation.SUCCEEDED));
        fixture.ledger.finishMaterial(candidate, P11ReceiptLedger.Terminal.FAILED);
        assertTrue(fixture.ledger.publishHandoff(candidate).isEmpty());
        assertEquals(P11ReceiptLedger.Change.RECORDED, fixture.ledger.discardFailedCandidate(candidate));
        assertFalse(fixture.identities.ownsData(next));
        assertTrue(fixture.ledger.facts(fixture.source).orElseThrow().dirty());
        assertTrue(fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).isPresent());
    }

    @Test
    void sourceWriterReadbackAndMaterialReceiptsRejectOtherLedgerAndStoppedSlot() {
        var first = dataFixture(true);
        var second = dataFixture(true);
        var writer = first.ledger.beginSave(first.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        var readback = first.ledger.beginReadback(first.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        assertFalse(second.ledger.mayWrite(writer));
        assertTrue(second.ledger.attemptFacts(writer).isEmpty());
        assertEquals(P11ReceiptLedger.Change.STALE,
                second.ledger.finishSave(writer, P11ReceiptLedger.Terminal.FAILED));
        assertEquals(P11ReceiptLedger.Change.STALE,
                second.ledger.finishReadback(readback, P11ReceiptLedger.Observation.SUCCEEDED));
        assertTrue(second.ledger.firstSource(first.identity,
                P11ReceiptLedger.Disposition.LIVE).isEmpty());
        first.ledger.stop();
        first.identities.stop();
        assertFalse(first.ledger.mayWrite(writer));
        assertEquals(P11ReceiptLedger.Change.STALE,
                first.ledger.finishReadback(readback, P11ReceiptLedger.Observation.SUCCEEDED));
    }

    @Test
    void candidateCallbackMutationKeepsContextAndPublishesItsLatestVersion() {
        var fixture = dataFixture(true);
        var nextIdentity = fixture.identities.captureModelSource(
                fixture.identities.modelActor(UUID_A, 17)).orElseThrow();
        var material = fixture.ledger.beginHandoff(fixture.source, nextIdentity,
                P11ReceiptLedger.Disposition.LIVE).orElseThrow();
        fixture.ledger.material(material, P11ReceiptLedger.MaterialStep.CONSTRUCTOR,
                P11ReceiptLedger.Observation.SUCCEEDED);
        var first = material.source();
        var mutated = fixture.ledger.advanceMutationVersion(first).orElseThrow();
        assertSame(mutated, material.source());
        assertSame(first.dataIdentity(), mutated.dataIdentity());
        assertEquals(first.epoch(), mutated.epoch());
        assertEquals(first.version() + 1, mutated.version());
        assertTrue(fixture.ledger.advanceMutationVersion(first).isEmpty());
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                fixture.ledger.markDirty(mutated, P11ReceiptLedger.WriterKind.PLAYER_DATA));
        for (var step : P11ReceiptLedger.MaterialStep.values()) {
            assertEquals(step == P11ReceiptLedger.MaterialStep.CONSTRUCTOR
                            ? P11ReceiptLedger.Change.DUPLICATE : P11ReceiptLedger.Change.RECORDED,
                    fixture.ledger.material(material, step, P11ReceiptLedger.Observation.SUCCEEDED));
        }
        fixture.ledger.finishMaterial(material, P11ReceiptLedger.Terminal.COMPLETED);
        assertSame(mutated, fixture.ledger.publishHandoff(material).orElseThrow());
        assertTrue(fixture.ledger.facts(mutated).orElseThrow().materialComplete());
        assertTrue(fixture.ledger.physicalFacts(mutated,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow().dirty());
    }

    @Test
    void independentCanonicalJsonWriterDoesNotRequireOrCompletePartialPlayerBody() {
        var fixture = dataFixture(false);
        var bodyMaterial = fixture.ledger.beginMaterial(fixture.source).orElseThrow();
        fixture.ledger.material(bodyMaterial, P11ReceiptLedger.MaterialStep.REQUIRED_COPY_OR_LOAD,
                P11ReceiptLedger.Observation.FAILED);
        fixture.ledger.finishMaterial(bodyMaterial, P11ReceiptLedger.Terminal.FAILED);
        assertTrue(fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).isEmpty());
        assertTrue(fixture.ledger.beginIndependentMaterial(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).isEmpty());
        for (var kind : new P11ReceiptLedger.WriterKind[] {
                P11ReceiptLedger.WriterKind.STATISTICS, P11ReceiptLedger.WriterKind.ADVANCEMENTS }) {
            var material = fixture.ledger.beginIndependentMaterial(fixture.source, kind).orElseThrow();
            assertTrue(fixture.ledger.beginIndependentSave(material).isEmpty());
            assertEquals(P11ReceiptLedger.Change.RECORDED, fixture.ledger.independentMaterial(
                    material, P11ReceiptLedger.Observation.SUCCEEDED));
            var writer = fixture.ledger.beginIndependentSave(material).orElseThrow();
            completePhysical(fixture.ledger, writer);
            assertFalse(fixture.ledger.physicalFacts(fixture.source, kind).orElseThrow().dirty());
        }
        assertFalse(fixture.ledger.facts(fixture.source).orElseThrow().materialComplete());
        assertTrue(fixture.ledger.facts(fixture.source).orElseThrow().dirty());
        assertTrue(fixture.ledger.discharge(fixture.source).isEmpty());
    }

    @Test
    void independentCandidateJsonReceiptIsRevokedByExactSourceVersionOrMaterialReplacement() {
        var fixture = dataFixture(true);
        var nextIdentity = fixture.identities.captureModelSource(
                fixture.identities.modelActor(UUID_A, 17)).orElseThrow();
        var body = fixture.ledger.beginHandoff(fixture.source, nextIdentity,
                P11ReceiptLedger.Disposition.CANDIDATE).orElseThrow();
        var material = fixture.ledger.beginIndependentMaterial(body.source(),
                P11ReceiptLedger.WriterKind.STATISTICS).orElseThrow();
        fixture.ledger.independentMaterial(material, P11ReceiptLedger.Observation.SUCCEEDED);
        var writer = fixture.ledger.beginIndependentSave(material).orElseThrow();
        assertTrue(fixture.ledger.mayWrite(writer));
        var newer = fixture.ledger.advanceMutationVersion(body.source()).orElseThrow();
        assertFalse(fixture.ledger.mayWrite(writer));
        assertTrue(fixture.ledger.beginIndependentSave(material).isEmpty());
        assertEquals(P11ReceiptLedger.Change.STALE,
                fixture.ledger.finishSave(writer, P11ReceiptLedger.Terminal.FAILED));
        var current = fixture.ledger.beginIndependentMaterial(newer,
                P11ReceiptLedger.WriterKind.STATISTICS).orElseThrow();
        fixture.ledger.independentMaterial(current, P11ReceiptLedger.Observation.SUCCEEDED);
        var currentWriter = fixture.ledger.beginIndependentSave(current).orElseThrow();
        var failed = fixture.ledger.beginIndependentMaterial(newer,
                P11ReceiptLedger.WriterKind.STATISTICS).orElseThrow();
        assertFalse(fixture.ledger.mayWrite(currentWriter));
        fixture.ledger.independentMaterial(failed, P11ReceiptLedger.Observation.UNKNOWN);
        assertEquals(P11ReceiptLedger.Change.TERMINAL,
                fixture.ledger.independentMaterial(failed, P11ReceiptLedger.Observation.SUCCEEDED));
        assertTrue(fixture.ledger.beginIndependentSave(failed).isEmpty());
    }

    private static DataFixture dataFixture(boolean qualified) {
        var identities = P11IdentityOwner.isolatedModel(2);
        var identity = identities.captureModelSource(identities.modelActor(UUID_A, 17)).orElseThrow();
        var ledger = new P11ReceiptLedger(identities);
        var source = ledger.firstSource(identity, P11ReceiptLedger.Disposition.LIVE).orElseThrow();
        if (qualified) {
            completeMaterial(ledger, ledger.beginMaterial(source).orElseThrow());
        }
        return new DataFixture(identities, identity, ledger, source);
    }

    private static void completeMaterial(P11ReceiptLedger ledger, P11ReceiptLedger.MaterialReceipt receipt) {
        for (var step : P11ReceiptLedger.MaterialStep.values()) {
            assertEquals(P11ReceiptLedger.Change.RECORDED,
                    ledger.material(receipt, step, P11ReceiptLedger.Observation.SUCCEEDED));
        }
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                ledger.finishMaterial(receipt, P11ReceiptLedger.Terminal.COMPLETED));
    }

    private static void completePhysical(P11ReceiptLedger ledger,
            P11ReceiptLedger.PhysicalWriterReceipt writer) {
        for (var step : P11ReceiptLedger.PhysicalStep.values()) {
            boolean required = switch (writer.kind()) {
                case CACHE -> step == P11ReceiptLedger.PhysicalStep.ENCODE
                        || step == P11ReceiptLedger.PhysicalStep.CACHE_ASSIGNMENT;
                case STATISTICS, ADVANCEMENTS -> step == P11ReceiptLedger.PhysicalStep.ENCODE
                        || step == P11ReceiptLedger.PhysicalStep.WRITE || step == P11ReceiptLedger.PhysicalStep.CLOSE;
                case PLAYER_DATA, LEVEL_PLAYER -> step != P11ReceiptLedger.PhysicalStep.CACHE_ASSIGNMENT;
            };
            if (required) {
                assertEquals(P11ReceiptLedger.Change.RECORDED,
                        ledger.physical(writer, step, P11ReceiptLedger.Observation.SUCCEEDED));
            }
        }
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                ledger.finishSave(writer, P11ReceiptLedger.Terminal.COMPLETED));
    }

    private record DataFixture(P11IdentityOwner identities, P11IdentityOwner.DataIdentity identity,
            P11ReceiptLedger ledger, P11ReceiptLedger.Source source) {}

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
