package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11ReceiptLedger.Observation.FAILED;
import static com.yo1no.gramarye.P11ReceiptLedger.Observation.SUCCEEDED;
import static com.yo1no.gramarye.P11ReceiptLedger.Observation.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Actual production observation kernel/scopes only; not native IO, injection, or durability proof. */
final class P11NativeStorageOutcomeTest {
    @Test
    void normalReturnAloneProvesNoStage() {
        for (var kind : P11NativeWriteFacts.Kind.values()) {
            var facts = new P11NativeWriteFacts(kind);
            facts.nativeReturned(true);
            assertEquals(new P11NativeWriteFacts.Result(UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, false),
                    facts.settle());
        }
    }

    @Test
    void nbtNeedsActualBodyEveryClosePathAndReplace() {
        var facts = nbt();
        facts.nbtBody(true);
        assertFalse(facts.readyToReplace());
        facts.closed(true);
        assertFalse(facts.readyToReplace());
        facts.ioReturned(true);
        assertTrue(facts.readyToReplace());
        facts.replaced(true);
        assertEquals(new P11NativeWriteFacts.Result(SUCCEEDED, SUCCEEDED, SUCCEEDED, SUCCEEDED, true),
                facts.settle());
    }

    @Test
    void nativeWriteCloseSuccessWithoutReplaceDoesNotCompleteNbt() {
        var facts = writableNbt();
        var result = facts.settle();
        assertEquals(UNKNOWN, result.replace());
        assertFalse(result.successful());
    }

    @Test
    void helperRefusalIsNotFabricatedNativeReplaceFalse() {
        var facts = writableNbt();
        facts.rejected();
        assertFalse(facts.readyToReplace());
        assertEquals(UNKNOWN, facts.settle().replace());
        assertFalse(facts.settle().successful());
    }

    @Test
    void actualReplaceFalseStaysFailedAfterLaterTrue() {
        var facts = writableNbt();
        facts.replaced(false);
        facts.replaced(true);
        assertEquals(FAILED, facts.settle().replace());
        assertFalse(facts.settle().successful());
    }

    @Test
    void replaceObservedBeforeWriteCannotBeRescuedByLaterStages() {
        var facts = nbt();
        facts.replaced(true);
        facts.nbtBody(true);
        facts.closed(true);
        facts.ioReturned(true);
        assertEquals(SUCCEEDED, facts.settle().replace());
        assertFalse(facts.settle().successful());
    }

    @Test
    void stringFallbackCannotBeWashedByBodyNormalReturn() {
        var facts = nbt();
        facts.encoded(false);
        facts.nbtBody(true);
        facts.closed(true);
        facts.ioReturned(true);
        assertFalse(facts.readyToReplace());
        assertEquals(FAILED, facts.settle().encode());
        assertFalse(facts.settle().successful());
    }

    @Test
    void bodyExceptionDoesNotInventSpecificEncodingFailure() {
        var facts = nbt();
        facts.nbtBody(false);
        facts.closed(true);
        facts.ioReturned(false);
        assertEquals(new P11NativeWriteFacts.Result(UNKNOWN, FAILED, SUCCEEDED, UNKNOWN, false),
                facts.settle());
    }

    @Test
    void closeFailureSurvivesRedundantSuccessfulCloses() {
        var facts = nbt();
        facts.nbtBody(true);
        facts.closed(false);
        facts.closed(true);
        facts.closed(true);
        facts.ioReturned(false);
        facts.ioReturned(true);
        assertFalse(facts.readyToReplace());
        assertEquals(FAILED, facts.settle().close());
        assertFalse(facts.settle().successful());
    }

    @Test
    void normalFileUtilsHelperProvesJointWriteAndCloseButNotReplace() {
        var facts = stats();
        facts.encoded(true);
        facts.written(true);
        facts.closed(true);
        facts.replaced(true);
        assertEquals(new P11NativeWriteFacts.Result(SUCCEEDED, SUCCEEDED, SUCCEEDED, UNKNOWN, true),
                facts.settle());
    }

    @Test
    void fileUtilsFailureCannotNameWriteOrCloseAsFailed() {
        var facts = stats();
        facts.encoded(true);
        facts.written(false);
        facts.closed(false);
        facts.nativeReturned(true); // The original stats method catches IOException.
        assertEquals(new P11NativeWriteFacts.Result(SUCCEEDED, UNKNOWN, UNKNOWN, UNKNOWN, false),
                facts.settle());
    }

    @Test
    void laterHelperSuccessCannotEraseEarlierUnknownFailure() {
        var facts = stats();
        facts.encoded(true);
        facts.written(false);
        facts.closed(false);
        facts.written(true);
        facts.closed(true);
        assertFalse(facts.settle().successful());
    }

    @Test
    void advancementWriteAndCloseHaveDistinctExactFailures() {
        var write = advancement();
        write.encoded(true);
        write.written(false);
        write.closed(true);
        assertEquals(new P11NativeWriteFacts.Result(SUCCEEDED, FAILED, SUCCEEDED, UNKNOWN, false),
                write.settle());
        var close = advancement();
        close.encoded(true);
        close.written(true);
        close.closed(false);
        close.nativeReturned(true); // Swallowed native IO/JsonIOException is not a receipt.
        assertEquals(new P11NativeWriteFacts.Result(SUCCEEDED, SUCCEEDED, FAILED, UNKNOWN, false),
                close.settle());
    }

    @Test
    void advancementEarlyCodecOrOpenFailureDoesNotInventMissingObservations() {
        var codec = advancement();
        codec.nativeReturned(false);
        assertEquals(new P11NativeWriteFacts.Result(UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, false),
                codec.settle());
        var open = advancement();
        open.encoded(true);
        open.nativeReturned(true);
        assertEquals(new P11NativeWriteFacts.Result(SUCCEEDED, UNKNOWN, UNKNOWN, UNKNOWN, false),
                open.settle());
    }

    @Test
    void terminalSnapshotIsIdempotentAndRejectsLateCallbacks() {
        var facts = advancement();
        facts.encoded(true);
        facts.written(true);
        facts.closed(true);
        var result = facts.settle();
        assertTrue(result.successful());
        facts.encoded(false);
        facts.written(false);
        facts.closed(false);
        facts.rejected();
        facts.nativeReturned(false);
        facts.replaced(false);
        assertSame(result, facts.settle());
    }

    @Test
    void exceptionAfterObservedStagesCannotReportNormalScopeCompletion() {
        var facts = advancement();
        facts.encoded(true);
        facts.written(true);
        facts.closed(true);
        facts.nativeReturned(false);
        assertFalse(facts.settle().successful());
    }

    @Test
    void unownedNbtScopesMaskForeignCallsAndOnlyExactTopCanEnd() {
        var first = new CompoundTag();
        var outer = P11NativeStorageBoundary.beginNbtWrite(first, Path.of("unopened-first"));
        var stream = P11NativeStorageBoundary.beginNbtStream(first);
        var foreign = P11NativeStorageBoundary.beginNbtWrite(new CompoundTag(), Path.of("unopened-foreign"));
        try {
            assertSame(outer, stream.previous);
            assertSame(stream, foreign.previous);
            P11NativeStorageBoundary.endNbtWrite(outer, true);
            assertFalse(outer.finished);
            P11NativeStorageBoundary.nbtBodyWritten(true);
            P11NativeStorageBoundary.nbtStreamClosed(false);
            P11NativeStorageBoundary.stringEncodingFallback();
            P11NativeStorageBoundary.endNbtWrite(foreign, true);
            assertTrue(foreign.finished);
            P11NativeStorageBoundary.endNbtWrite(foreign, false);
            assertFalse(stream.finished);
        } finally {
            P11NativeStorageBoundary.endNbtWrite(foreign, false);
            P11NativeStorageBoundary.endNbtStream(stream, false);
            P11NativeStorageBoundary.endNbtWrite(outer, false);
        }
        assertTrue(outer.finished);
        assertTrue(stream.finished);
    }

    @Test
    void unownedIndependentTokensCannotEndNewerScopeOrUseNbtEnd() {
        // No native owner is provided: these are deliberately unqualified masking scopes,
        // never a fabricated ServerPlayer, canonical stats instance, or physical receipt.
        var outer = P11NativeStorageBoundary.beginStats(null, null, null);
        var inner = P11NativeStorageBoundary.beginStats(null, null, null);
        try {
            assertSame(outer, inner.previous);
            assertTrue(P11NativeStorageBoundary.independentPermitted(inner));
            assertFalse(P11NativeStorageBoundary.independentPermitted(outer));
            P11NativeStorageBoundary.endNbtWrite(inner, true);
            assertFalse(inner.finished);
            P11NativeStorageBoundary.endIndependent(outer, true);
            assertFalse(outer.finished);
            P11NativeStorageBoundary.independentEncoded(false);
            P11NativeStorageBoundary.independentWritten(true);
            P11NativeStorageBoundary.independentClosed(true);
            P11NativeStorageBoundary.endIndependent(inner, true);
            assertTrue(inner.finished);
            assertTrue(P11NativeStorageBoundary.independentPermitted(outer));
            P11NativeStorageBoundary.endIndependent(inner, false);
            assertTrue(P11NativeStorageBoundary.independentPermitted(outer));
            assertEquals(new P11NativeWriteFacts.Result(UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, false),
                    outer.facts.settle());
        } finally {
            P11NativeStorageBoundary.endIndependent(inner, false);
            P11NativeStorageBoundary.endIndependent(outer, false);
        }
        assertTrue(outer.finished);
        assertTrue(P11NativeStorageBoundary.independentPermitted(null));
    }

    private static P11NativeWriteFacts nbt() {
        return new P11NativeWriteFacts(P11NativeWriteFacts.Kind.NBT);
    }

    private static P11NativeWriteFacts stats() {
        return new P11NativeWriteFacts(P11NativeWriteFacts.Kind.STATISTICS);
    }

    private static P11NativeWriteFacts advancement() {
        return new P11NativeWriteFacts(P11NativeWriteFacts.Kind.ADVANCEMENTS);
    }

    private static P11NativeWriteFacts writableNbt() {
        var facts = nbt();
        facts.nbtBody(true);
        facts.closed(true);
        facts.ioReturned(true);
        return facts;
    }
}
