package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Controlled production-owner observations, not fabricated native IO or capacity qualification. */
final class P11SaveProgressTest {
    private static final UUID PLAYER = new UUID(1, 2);

    @Test
    void requiredKindObservationStartsAtAcquisitionWithoutCreatingSaveAuthority() {
        var fixture = fixture(10, false);
        for (var kind : P11ReceiptLedger.WriterKind.values()) {
            fixture.ledger.observeRequiredWriter(fixture.source, kind);
            assertTrue(fixture.ledger.physicalFacts(fixture.source, kind).isEmpty());
            assertTrue(fixture.ledger.beginSave(fixture.source, kind).isEmpty());
        }
        fixture.clock.set(100);
        var progress = fixture.ledger.saveProgress();
        assertEquals(5, progress.kinds().size());
        assertEquals(5, progress.kinds().stream().map(P11ReceiptLedger.KindProgress::kind).distinct().count());
        assertEquals(90, progress.elapsedMillis().orElseThrow());
        for (var value : progress.kinds()) {
            assertEquals(1, value.dirtySources());
            assertEquals(90, value.oldestDirtyMillis().orElseThrow());
            assertFalse(value.ageUnavailable());
            assertEquals(0, value.successfulPhysicalWrites());
        }
        assertThrows(UnsupportedOperationException.class, () -> progress.kinds().clear());
        assertFalse(fixture.ledger.facts(fixture.source).orElseThrow().materialComplete());
    }

    @Test
    void repeatedDirtyAndFailedSavesKeepOldestPerKindUntilThatKindReallyCompletes() {
        var fixture = fixture(0, true);
        var player = P11ReceiptLedger.WriterKind.PLAYER_DATA;
        var stats = P11ReceiptLedger.WriterKind.STATISTICS;
        fixture.ledger.observeRequiredWriter(fixture.source, player);
        fixture.clock.set(20);
        fixture.ledger.observeRequiredWriter(fixture.source, stats);
        fixture.clock.set(40);
        fixture.ledger.markDirty(fixture.source, player);
        var failed = fixture.ledger.beginSave(fixture.source, player).orElseThrow();
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                fixture.ledger.finishSave(failed, P11ReceiptLedger.Terminal.FAILED));
        fixture.clock.set(100);
        assertEquals(100, kind(fixture, player).oldestDirtyMillis().orElseThrow());
        assertEquals(80, kind(fixture, stats).oldestDirtyMillis().orElseThrow());
        assertEquals(0, kind(fixture, player).successfulPhysicalWrites());

        var successful = fixture.ledger.beginSave(fixture.source, player).orElseThrow();
        complete(fixture.ledger, successful);
        assertEquals(0, kind(fixture, player).dirtySources());
        assertTrue(kind(fixture, player).oldestDirtyMillis().isEmpty());
        assertEquals(80, kind(fixture, stats).oldestDirtyMillis().orElseThrow());
        fixture.clock.set(120);
        fixture.ledger.markDirty(fixture.source, player);
        fixture.clock.set(150);
        assertEquals(30, kind(fixture, player).oldestDirtyMillis().orElseThrow());
        assertEquals(130, kind(fixture, stats).oldestDirtyMillis().orElseThrow());
    }

    @Test
    void physicalSuccessRequiresEveryNativeStepAndCountsOnceWhileCacheNeverCountsAsIo() {
        var fixture = fixture(0, true);
        for (var writerKind : P11ReceiptLedger.WriterKind.values()) {
            var receipt = fixture.ledger.beginSave(fixture.source, writerKind).orElseThrow();
            assertEquals(P11ReceiptLedger.Change.REFUSED,
                    fixture.ledger.finishSave(receipt, P11ReceiptLedger.Terminal.COMPLETED));
            assertEquals(0, kind(fixture, writerKind).successfulPhysicalWrites());
            complete(fixture.ledger, receipt);
            assertEquals(P11ReceiptLedger.Change.DUPLICATE,
                    fixture.ledger.finishSave(receipt, P11ReceiptLedger.Terminal.COMPLETED));
            assertEquals(writerKind == P11ReceiptLedger.WriterKind.CACHE ? 0 : 1,
                    kind(fixture, writerKind).successfulPhysicalWrites());
            assertFalse(kind(fixture, writerKind).counterSaturated());
        }
        var unknown = fixture.ledger.beginSave(fixture.source,
                P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        fixture.ledger.finishSave(unknown, P11ReceiptLedger.Terminal.UNKNOWN);
        assertEquals(1, kind(fixture, P11ReceiptLedger.WriterKind.PLAYER_DATA).successfulPhysicalWrites());
    }

    @Test
    void oldVersionActualIoSuccessCountsButNeverClearsNewVersionDirtyAge() {
        var fixture = fixture(10, true);
        var writerKind = P11ReceiptLedger.WriterKind.PLAYER_DATA;
        fixture.ledger.observeRequiredWriter(fixture.source, writerKind);
        var old = fixture.ledger.beginSave(fixture.source, writerKind).orElseThrow();
        observeComplete(fixture.ledger, old);
        fixture.clock.set(30);
        var newer = fixture.ledger.advanceMutationVersion(fixture.source).orElseThrow();
        fixture.clock.set(80);
        assertEquals(P11ReceiptLedger.Change.STALE,
                fixture.ledger.finishSave(old, P11ReceiptLedger.Terminal.COMPLETED));
        assertEquals(1, kind(fixture, writerKind).successfulPhysicalWrites());
        assertEquals(70, kind(fixture, writerKind).oldestDirtyMillis().orElseThrow());
        assertTrue(fixture.ledger.physicalFacts(newer, writerKind).orElseThrow().dirty());
        assertEquals(P11ReceiptLedger.Change.DUPLICATE,
                fixture.ledger.finishSave(old, P11ReceiptLedger.Terminal.COMPLETED));
        var current = fixture.ledger.beginSave(newer, writerKind).orElseThrow();
        complete(fixture.ledger, current);
        assertEquals(2, kind(fixture, writerKind).successfulPhysicalWrites());
        assertEquals(0, kind(fixture, writerKind).dirtySources());
    }

    @Test
    void handoffPreservesFirstDirtyObservationAndNeverResetsToPublicationTime() {
        var fixture = fixture(10, true);
        var writerKind = P11ReceiptLedger.WriterKind.PLAYER_DATA;
        fixture.ledger.observeRequiredWriter(fixture.source, writerKind);
        fixture.clock.set(30);
        var identity = fixture.identities.captureModelSource(
                fixture.identities.modelActor(PLAYER, 18)).orElseThrow();
        var material = fixture.ledger.beginHandoff(fixture.source, identity,
                P11ReceiptLedger.Disposition.CANDIDATE).orElseThrow();
        fixture.ledger.observeRequiredWriter(material.source(), writerKind);
        completeMaterial(fixture.ledger, material);
        fixture.clock.set(70);
        var current = fixture.ledger.publishHandoff(material).orElseThrow();
        fixture.clock.set(100);
        assertEquals(90, kind(fixture, writerKind).oldestDirtyMillis().orElseThrow());
        complete(fixture.ledger, fixture.ledger.beginSave(current, writerKind).orElseThrow());
        assertEquals(0, kind(fixture, writerKind).dirtySources());
    }

    @Test
    void predecessorIndependentCompletionCannotEraseObservedPendingSuccessorAge() {
        var fixture = fixture(10, true);
        var writerKind = P11ReceiptLedger.WriterKind.STATISTICS;
        var independent = fixture.ledger.beginIndependentMaterial(fixture.source, writerKind).orElseThrow();
        fixture.ledger.independentMaterial(independent, P11ReceiptLedger.Observation.SUCCEEDED);
        var oldWriter = fixture.ledger.beginIndependentSave(independent).orElseThrow();
        observeComplete(fixture.ledger, oldWriter);
        fixture.clock.set(30);
        var identity = fixture.identities.captureModelSource(
                fixture.identities.modelActor(PLAYER, 18)).orElseThrow();
        var candidate = fixture.ledger.beginHandoff(fixture.source, identity,
                P11ReceiptLedger.Disposition.CANDIDATE).orElseThrow();
        fixture.ledger.observeRequiredWriter(candidate.source(), writerKind);
        fixture.clock.set(50);
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                fixture.ledger.finishSave(oldWriter, P11ReceiptLedger.Terminal.COMPLETED));
        assertEquals(1, kind(fixture, writerKind).successfulPhysicalWrites());
        assertEquals(40, kind(fixture, writerKind).oldestDirtyMillis().orElseThrow());
        assertFalse(fixture.ledger.physicalFacts(fixture.source, writerKind).orElseThrow().dirty(),
                "telemetry must not change the existing real predecessor writer outcome");
        completeMaterial(fixture.ledger, candidate);
        fixture.clock.set(80);
        var current = fixture.ledger.publishHandoff(candidate).orElseThrow();
        assertEquals(70, kind(fixture, writerKind).oldestDirtyMillis().orElseThrow());
        complete(fixture.ledger, fixture.ledger.beginSave(current, writerKind).orElseThrow());
        assertEquals(0, kind(fixture, writerKind).dirtySources());
        assertEquals(2, kind(fixture, writerKind).successfulPhysicalWrites());
    }

    @Test
    void distinctAccountsShareBoundedKindOldestWithoutSharingTheirClear() {
        var fixture = fixture(0, true);
        var writerKind = P11ReceiptLedger.WriterKind.STATISTICS;
        fixture.ledger.observeRequiredWriter(fixture.source, writerKind);
        fixture.clock.set(30);
        var secondIdentity = fixture.identities.captureModelSource(
                fixture.identities.modelActor(new UUID(3, 4), 19)).orElseThrow();
        var second = fixture.ledger.firstSource(secondIdentity, P11ReceiptLedger.Disposition.LIVE).orElseThrow();
        completeMaterial(fixture.ledger, fixture.ledger.beginMaterial(second).orElseThrow());
        fixture.ledger.observeRequiredWriter(second, writerKind);
        fixture.clock.set(100);
        assertEquals(2, kind(fixture, writerKind).dirtySources());
        assertEquals(100, kind(fixture, writerKind).oldestDirtyMillis().orElseThrow());
        complete(fixture.ledger, fixture.ledger.beginSave(fixture.source, writerKind).orElseThrow());
        assertEquals(1, kind(fixture, writerKind).dirtySources());
        assertEquals(70, kind(fixture, writerKind).oldestDirtyMillis().orElseThrow());
    }

    @Test
    void missingClockHistoryStaysUnknownButDoesNotBlockSaveOrSuccessfulIoCounting() {
        var fixture = fixture(-1, true);
        var writerKind = P11ReceiptLedger.WriterKind.PLAYER_DATA;
        fixture.ledger.observeRequiredWriter(fixture.source, writerKind);
        fixture.clock.set(500);
        assertTrue(fixture.ledger.saveProgress().elapsedMillis().isEmpty());
        assertTrue(kind(fixture, writerKind).ageUnavailable());
        assertTrue(kind(fixture, writerKind).oldestDirtyMillis().isEmpty());
        complete(fixture.ledger, fixture.ledger.beginSave(fixture.source, writerKind).orElseThrow());
        assertEquals(1, kind(fixture, writerKind).successfulPhysicalWrites());
        assertEquals(0, kind(fixture, writerKind).dirtySources());
        fixture.clock.set(600);
        fixture.ledger.markDirty(fixture.source, writerKind);
        fixture.clock.set(650);
        assertEquals(50, kind(fixture, writerKind).oldestDirtyMillis().orElseThrow());
        assertFalse(kind(fixture, writerKind).ageUnavailable());
    }

    @Test
    void regressedOrUnknownSamplesNeverProduceNegativeAgesAndCannotClearDuty() {
        var fixture = fixture(100, true);
        var writerKind = P11ReceiptLedger.WriterKind.PLAYER_DATA;
        fixture.ledger.observeRequiredWriter(fixture.source, writerKind);
        fixture.clock.set(-1);
        assertTrue(kind(fixture, writerKind).ageUnavailable());
        fixture.clock.set(200);
        assertEquals(100, kind(fixture, writerKind).oldestDirtyMillis().orElseThrow());
        fixture.clock.set(199);
        assertTrue(kind(fixture, writerKind).ageUnavailable());
        assertTrue(fixture.ledger.saveProgress().elapsedMillis().isEmpty());
        fixture.clock.set(500);
        assertTrue(kind(fixture, writerKind).oldestDirtyMillis().isEmpty());
        assertEquals(1, kind(fixture, writerKind).dirtySources());
        complete(fixture.ledger, fixture.ledger.beginSave(fixture.source, writerKind).orElseThrow());
        assertEquals(0, kind(fixture, writerKind).dirtySources());
        assertEquals(1, kind(fixture, writerKind).successfulPhysicalWrites());
    }

    @Test
    void longMaximumElapsedAndSaturatedCounterRemainRepresentableAndExplicit() {
        var fixture = fixture(0, true);
        var writerKind = P11ReceiptLedger.WriterKind.PLAYER_DATA;
        fixture.ledger.observeRequiredWriter(fixture.source, writerKind);
        fixture.clock.set(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, fixture.ledger.saveProgress().elapsedMillis().orElseThrow());
        assertEquals(Long.MAX_VALUE, kind(fixture, writerKind).oldestDirtyMillis().orElseThrow());
        var exactMaximum = new P11ReceiptLedger.SuccessCount(Long.MAX_VALUE - 1, false).increment();
        assertEquals(Long.MAX_VALUE, exactMaximum.value());
        assertFalse(exactMaximum.saturated());
        var saturated = exactMaximum.increment();
        assertEquals(Long.MAX_VALUE, saturated.value());
        assertTrue(saturated.saturated());
        assertEquals(saturated, saturated.increment());
        assertThrows(IllegalArgumentException.class, () -> new P11ReceiptLedger.SuccessCount(-1, false));
    }

    @Test
    void stopKeepsOneScalarTerminalSnapshotAndLateCompletionsCannotChangeIt() {
        var fixture = fixture(0, true);
        var writerKind = P11ReceiptLedger.WriterKind.PLAYER_DATA;
        fixture.ledger.observeRequiredWriter(fixture.source, writerKind);
        var receipt = fixture.ledger.beginSave(fixture.source, writerKind).orElseThrow();
        observeComplete(fixture.ledger, receipt);
        fixture.clock.set(100);
        fixture.ledger.stop();
        var terminal = fixture.ledger.saveProgress();
        fixture.clock.set(900);
        fixture.ledger.stop();
        assertSame(terminal, fixture.ledger.saveProgress());
        assertEquals(P11ReceiptLedger.Change.STALE,
                fixture.ledger.finishSave(receipt, P11ReceiptLedger.Terminal.COMPLETED));
        assertEquals(0, kind(fixture, writerKind).successfulPhysicalWrites());
        assertEquals(100, kind(fixture, writerKind).oldestDirtyMillis().orElseThrow());
        assertEquals(0, fixture.ledger.retainedSources());
    }

    private static Fixture fixture(long now, boolean qualified) {
        var clock = new AtomicLong(now);
        var identities = P11IdentityOwner.isolatedModel(3);
        var identity = identities.captureModelSource(identities.modelActor(PLAYER, 17)).orElseThrow();
        var ledger = new P11ReceiptLedger(identities, clock::get);
        var source = ledger.firstSource(identity, P11ReceiptLedger.Disposition.LIVE).orElseThrow();
        if (qualified) { completeMaterial(ledger, ledger.beginMaterial(source).orElseThrow()); }
        return new Fixture(clock, identities, ledger, source);
    }

    private static P11ReceiptLedger.KindProgress kind(Fixture fixture, P11ReceiptLedger.WriterKind kind) {
        return fixture.ledger.saveProgress().kinds().stream().filter(value -> value.kind() == kind)
                .findFirst().orElseThrow();
    }

    private static void completeMaterial(P11ReceiptLedger ledger, P11ReceiptLedger.MaterialReceipt receipt) {
        for (var step : P11ReceiptLedger.MaterialStep.values()) {
            assertEquals(P11ReceiptLedger.Change.RECORDED,
                    ledger.material(receipt, step, P11ReceiptLedger.Observation.SUCCEEDED));
        }
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                ledger.finishMaterial(receipt, P11ReceiptLedger.Terminal.COMPLETED));
    }

    private static void complete(P11ReceiptLedger ledger, P11ReceiptLedger.PhysicalWriterReceipt receipt) {
        observeComplete(ledger, receipt);
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                ledger.finishSave(receipt, P11ReceiptLedger.Terminal.COMPLETED));
    }

    private static void observeComplete(P11ReceiptLedger ledger,
            P11ReceiptLedger.PhysicalWriterReceipt receipt) {
        for (var step : P11ReceiptLedger.PhysicalStep.values()) {
            boolean required = switch (receipt.kind()) {
                case PLAYER_DATA, LEVEL_PLAYER -> step != P11ReceiptLedger.PhysicalStep.CACHE_ASSIGNMENT;
                case STATISTICS, ADVANCEMENTS -> step == P11ReceiptLedger.PhysicalStep.ENCODE
                        || step == P11ReceiptLedger.PhysicalStep.WRITE || step == P11ReceiptLedger.PhysicalStep.CLOSE;
                case CACHE -> step == P11ReceiptLedger.PhysicalStep.ENCODE
                        || step == P11ReceiptLedger.PhysicalStep.CACHE_ASSIGNMENT;
            };
            if (required) {
                assertEquals(P11ReceiptLedger.Change.RECORDED,
                        ledger.physical(receipt, step, P11ReceiptLedger.Observation.SUCCEEDED));
            }
        }
    }

    private record Fixture(AtomicLong clock, P11IdentityOwner identities,
            P11ReceiptLedger ledger, P11ReceiptLedger.Source source) {}
}
