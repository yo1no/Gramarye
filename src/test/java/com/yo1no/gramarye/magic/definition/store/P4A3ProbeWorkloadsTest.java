package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import org.junit.jupiter.api.Test;

class P4A3ProbeWorkloadsTest {
    @Test
    void fullWorkloadShapesAreFixedAndDeterministic() {
        assertAll(
                () -> assertEquals(
                        new P4A3ProbeWorkloads.WorkloadShape(
                                "many-small", 4_095, 32_767, 728, 66_062_342, false),
                        P4A3ProbeWorkloads.shape("many-small")),
                () -> assertEquals(
                        new P4A3ProbeWorkloads.WorkloadShape(
                                "near-entry", 8, 64, 259_000, 66_367_484, false),
                        P4A3ProbeWorkloads.shape("near-entry")),
                () -> assertEquals(
                        new P4A3ProbeWorkloads.WorkloadShape(
                                "mixed", 8, 64, 147_261, 66_060_348, false),
                        P4A3ProbeWorkloads.shape("mixed")),
                () -> assertEquals(
                        new P4A3ProbeWorkloads.WorkloadShape(
                                "dedicated-mixed", 8, 64, 147_261, 66_060_348, true),
                        P4A3ProbeWorkloads.shape("dedicated-mixed")));
    }

    @Test
    void smallFixtureBuildsThroughProductionCarrierPathDeterministically() {
        var firstFixture = P4A3ProbeWorkloads.smallDeterminismFixture();
        var secondFixture = P4A3ProbeWorkloads.smallDeterminismFixture();
        var first = carrier(firstFixture.store());
        var second = carrier(secondFixture.store());

        assertAll(
                () -> assertArrayEquals(copy(first), copy(second)),
                () -> assertEquals(first.historyCount(), second.historyCount()),
                () -> assertEquals(first.revisionCount(), second.revisionCount()),
                () -> assertEquals(first.histories().stream()
                                .map(EncodedHistoryIndex::latestReference).toList(),
                        second.histories().stream()
                                .map(EncodedHistoryIndex::latestReference).toList()),
                () -> assertTrue(first.storeByteCount()
                        < MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES));
    }

    @Test
    void probeChecksumIsDeterministicAndConsumesCarrierBytes() {
        var first = carrier(P4A3ProbeWorkloads.smallDeterminismFixture().store());
        var second = carrier(P4A3ProbeWorkloads.smallDeterminismFixture().store());
        var firstBytes = copy(first);
        var secondBytes = copy(second);
        var firstChecksum = P4A3HeapProbeMain.checksum(List.of(first), firstBytes);
        var secondChecksum = P4A3HeapProbeMain.checksum(List.of(second), secondBytes);
        secondBytes[secondBytes.length - 1] ^= 1;

        assertAll(
                () -> assertEquals(firstChecksum, secondChecksum),
                () -> assertTrue(firstChecksum.matches("[0-9a-f]{16}")),
                () -> assertNotEquals(
                        firstChecksum,
                        P4A3HeapProbeMain.checksum(List.of(second), secondBytes)));
    }

    @Test
    void workloadNamesFailFastAndDedicatedRequiresARealProvider() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P4A3ProbeWorkloads.shape("unknown")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P4A3ProbeWorkloads.create("unknown", java.util.Optional.empty())),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P4A3ProbeWorkloads.create(
                                "dedicated-mixed", java.util.Optional.empty())));
    }

    @Test
    void summaryIsBoundedAndContainsNoPayloadSurface() {
        var summary = new P4A3ProbeSummary(
                "many-small",
                66_062_342,
                4_095,
                32_767,
                1_073_741_824L,
                536_870_912L,
                900_000_000L,
                1_100_000_000L,
                4_000L,
                "0123456789abcdef");
        var line = summary.line();

        assertAll(
                () -> assertTrue(line.startsWith("P4A3_PROBE_OK ")),
                () -> assertTrue(line.length() < 384),
                () -> assertFalse(line.contains("payload")),
                () -> assertFalse(line.contains("document")),
                () -> assertFalse(line.contains("route")),
                () -> assertFalse(line.contains("secret")));
    }

    private static EncodedSkillStoreCarrier carrier(SkillDefinitionStore store) {
        return ((CarrierBuildResult.Success) SkillStoreCarrierBuilder.rebuild(store)).carrier();
    }

    private static byte[] copy(EncodedSkillStoreCarrier carrier) {
        var bytes = new byte[carrier.storeByteCount()];
        carrier.copyStoreBlobInto(bytes, 0);
        return bytes;
    }
}
