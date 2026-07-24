package com.yo1no.gramarye.magic.definition.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PipelineFactReportTest {
    @Test
    void appendRetainsLeftThenRightWithoutDeduplication() {
        var repeated = fact(0, SkillMigrationFactCode.STEP_APPLIED);
        var left = new PipelineFactReport(List.of(
                repeated,
                fact(1, SkillMigrationFactCode.PAYLOAD_STEP_APPLIED)), false);
        var right = new PipelineFactReport(List.of(
                repeated,
                fact(2, SkillMigrationFactCode.STORE_STEP_APPLIED)), false);

        var merged = left.append(right);

        assertEquals(List.of(
                repeated,
                fact(1, SkillMigrationFactCode.PAYLOAD_STEP_APPLIED),
                repeated,
                fact(2, SkillMigrationFactCode.STORE_STEP_APPLIED)), merged.facts());
        assertFalse(merged.truncated());
        assertThrows(UnsupportedOperationException.class, () -> merged.facts().clear());
    }

    @Test
    void appendAcceptsExactCapAndTruncatesOnlyOverflow() {
        var leftFacts = IntStream.range(0, MagicSafetyCeilings.MAX_PIPELINE_FACTS - 1)
                .mapToObj(index -> fact(index, SkillMigrationFactCode.STEP_APPLIED))
                .toList();
        var exact = new PipelineFactReport(leftFacts, false).append(
                new PipelineFactReport(List.of(
                        fact(MagicSafetyCeilings.MAX_PIPELINE_FACTS - 1,
                                SkillMigrationFactCode.PAYLOAD_STEP_APPLIED)), false));
        var overflow = new PipelineFactReport(leftFacts, false).append(
                new PipelineFactReport(List.of(
                        fact(MagicSafetyCeilings.MAX_PIPELINE_FACTS - 1,
                                SkillMigrationFactCode.PAYLOAD_STEP_APPLIED),
                        fact(MagicSafetyCeilings.MAX_PIPELINE_FACTS,
                                SkillMigrationFactCode.STORE_STEP_APPLIED)), false));

        assertEquals(MagicSafetyCeilings.MAX_PIPELINE_FACTS, exact.facts().size());
        assertFalse(exact.truncated());
        assertEquals(MagicSafetyCeilings.MAX_PIPELINE_FACTS, overflow.facts().size());
        assertTrue(overflow.truncated());
        assertEquals(
                SkillMigrationFactCode.PAYLOAD_STEP_APPLIED,
                overflow.facts().get(MagicSafetyCeilings.MAX_PIPELINE_FACTS - 1).code());
    }

    @Test
    void appendPropagatesEitherTruncatedSourceAndSnapshotsInputs() {
        var mutable = new ArrayList<SkillMigrationFact>();
        mutable.add(fact(0, SkillMigrationFactCode.STEP_APPLIED));
        var leftTruncated = new PipelineFactReport(mutable, true);
        mutable.clear();
        var rightTruncated = new PipelineFactReport(
                List.of(fact(1, SkillMigrationFactCode.STORE_STEP_APPLIED)), true);

        var leftResult = leftTruncated.append(new PipelineFactReport(List.of(), false));
        var rightResult = new PipelineFactReport(List.of(), false).append(rightTruncated);

        assertEquals(1, leftResult.facts().size());
        assertTrue(leftResult.truncated());
        assertEquals(rightTruncated.facts(), rightResult.facts());
        assertTrue(rightResult.truncated());
        assertThrows(NullPointerException.class, () -> leftTruncated.append(null));
    }

    private static SkillMigrationFact fact(int fromVersion, SkillMigrationFactCode code) {
        return new SkillMigrationFact(
                code,
                fromVersion,
                fromVersion + 1,
                OptionalInt.empty());
    }
}
