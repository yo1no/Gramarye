package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.P4E0ResearchAttachmentFixtures;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Research-only Matrix-E root-vector workloads. These helpers materialize and measure root
 * vectors, but never invoke Store reclaim or any P4-E composition path.
 */
public final class P4E0ResearchRootWorkloads {
    public static final int EXACT_ROOT_COUNT = 65_536;
    public static final int OVER_LIMIT_ROOT_COUNT = 65_537;
    public static final int NINETY_PERCENT_DUPLICATE_DISTINCT_COUNT =
            (EXACT_ROOT_COUNT + 9) / 10;

    private static final long DISTINCT_SKILL_MSB = 0x5034_4530_5232_0000L;

    private P4E0ResearchRootWorkloads() {
    }

    public enum Variant {
        EXACT_ALL_DISTINCT,
        OVER_LIMIT_ALL_DISTINCT,
        EXACT_NINETY_PERCENT_DUPLICATES,
        OVER_LIMIT_NINETY_PERCENT_DUPLICATES,
        PLAYER_ROOTS_PLUS_MAXIMUM_JOURNAL,
        FIRST_MISSING_BEGINNING,
        FIRST_MISSING_MIDDLE,
        FIRST_MISSING_END,
        COMBINED_PLAYER_AND_JOURNAL_OVER_LIMIT
    }

    public enum Admission {
        COMPLETE,
        OVER_LIMIT,
        FIRST_MISSING
    }

    /** Constructs 65,536 distinct routes and admits the exact vector through the public seam. */
    public static Capture exactAllDistinct() {
        return snapshot(
                Variant.EXACT_ALL_DISTINCT,
                distinctRoots(EXACT_ROOT_COUNT),
                EXACT_ROOT_COUNT,
                0,
                0,
                -1,
                List.of());
    }

    /** Constructs 65,537 distinct routes and observes the public cap-plus-one result. */
    public static Capture overLimitAllDistinct() {
        return snapshot(
                Variant.OVER_LIMIT_ALL_DISTINCT,
                distinctRoots(OVER_LIMIT_ROOT_COUNT),
                OVER_LIMIT_ROOT_COUNT,
                0,
                0,
                -1,
                List.of());
    }

    /**
     * Constructs the exact root count with 6,554 distinct values and 58,982 duplicates. The exact
     * integer counts, rather than a rounded percentage label, are exposed in the metrics.
     */
    public static Capture exactNinetyPercentDuplicates() {
        return ninetyPercentDuplicates(
                Variant.EXACT_NINETY_PERCENT_DUPLICATES, EXACT_ROOT_COUNT);
    }

    /** Constructs the cap-plus-one vector with the same explicit 90%-duplicate shape. */
    public static Capture overLimitNinetyPercentDuplicates() {
        return ninetyPercentDuplicates(
                Variant.OVER_LIMIT_NINETY_PERCENT_DUPLICATES,
                OVER_LIMIT_ROOT_COUNT);
    }

    private static Capture ninetyPercentDuplicates(Variant variant, int count) {
        var distinctCount = Math.toIntExact(Math.floorDiv(count + 9L, 10L));
        var distinct = distinctRoots(distinctCount);
        var roots = new ArrayList<SkillReference>(count);
        roots.addAll(distinct);
        for (var index = distinct.size(); index < count; index++) {
            roots.add(distinct.get(index % distinct.size()));
        }
        return snapshot(
                variant,
                roots,
                distinctCount,
                0,
                0,
                -1,
                List.of());
    }

    /** Retains the 320 P4-C player roots followed by all 4,096 D3 prospective journal roots. */
    public static Capture playerRootsPlusMaximumJournal() {
        var attachment = P4E0ResearchAttachmentFixtures.readyRootMax(true);
        var store = P4E0ResearchStoreJournalFixtures.fullCurrentExact();
        var playerRoots = attachment.projectedRoots().orElseThrow();
        var journalRoots = store.unmatchedProspectiveJournalRoots();
        requireComponentCounts(playerRoots, journalRoots);
        var roots = new ArrayList<SkillReference>(
                playerRoots.size() + journalRoots.size());
        roots.addAll(playerRoots);
        roots.addAll(journalRoots);
        return snapshot(
                Variant.PLAYER_ROOTS_PLUS_MAXIMUM_JOURNAL,
                roots,
                distinctCount(roots),
                playerRoots.size(),
                journalRoots.size(),
                -1,
                List.of(attachment, store));
    }

    /**
     * Builds the Matrix-F 65,537-entry attempt by cycling the P4-C player roots and the D3
     * prospective journal roots. Both component sequences remain represented in the raw vector.
     */
    public static Capture combinedPlayerAndJournalOverLimit(
            List<SkillReference> playerRoots,
            List<SkillReference> journalRoots) {
        Objects.requireNonNull(playerRoots, "playerRoots");
        Objects.requireNonNull(journalRoots, "journalRoots");
        requireComponentCounts(playerRoots, journalRoots);
        var components = new ArrayList<SkillReference>(
                playerRoots.size() + journalRoots.size());
        components.addAll(playerRoots);
        components.addAll(journalRoots);
        var roots = new ArrayList<SkillReference>(OVER_LIMIT_ROOT_COUNT);
        for (var index = 0; index < OVER_LIMIT_ROOT_COUNT; index++) {
            roots.add(components.get(index % components.size()));
        }
        return snapshot(
                Variant.COMBINED_PLAYER_AND_JOURNAL_OVER_LIMIT,
                roots,
                distinctCount(components),
                playerRoots.size(),
                journalRoots.size(),
                -1,
                List.of());
    }

    public static Capture firstMissingBeginning() {
        return firstMissing(Variant.FIRST_MISSING_BEGINNING, 0);
    }

    public static Capture firstMissingMiddle() {
        return firstMissing(Variant.FIRST_MISSING_MIDDLE, EXACT_ROOT_COUNT / 2);
    }

    public static Capture firstMissingEnd() {
        return firstMissing(Variant.FIRST_MISSING_END, EXACT_ROOT_COUNT - 1);
    }

    private static Capture firstMissing(Variant variant, int missingIndex) {
        var fixture = P4D3StoreJournalFixture.build();
        var present = P4D3StoreJournalFixture.target(0, 0);
        var missing = P4D3StoreJournalFixture.submissionTarget();
        if (fixture.store().find(present).isEmpty()
                || fixture.store().find(missing).isPresent()) {
            throw new AssertionError("research missing-root controls changed");
        }
        var roots = new ArrayList<SkillReference>(EXACT_ROOT_COUNT);
        for (var index = 0; index < EXACT_ROOT_COUNT; index++) {
            roots.add(index == missingIndex ? missing : present);
        }
        var snapshot = SkillRetentionRootSnapshot.fromCompleteRoots(roots);
        if (!(snapshot instanceof SkillRetentionRootSnapshot.Complete complete)
                || complete.roots().size() != EXACT_ROOT_COUNT) {
            throw new AssertionError("research missing-root vector was not admitted");
        }
        var observedMissing = -1;
        var examined = 0;
        for (var index = 0; index < roots.size(); index++) {
            examined++;
            if (fixture.store().find(roots.get(index)).isEmpty()) {
                observedMissing = index;
                break;
            }
        }
        if (observedMissing != missingIndex || examined != missingIndex + 1) {
            throw new AssertionError("research first-missing scan position changed");
        }
        return new Capture(
                new Metrics(
                        variant,
                        Admission.FIRST_MISSING,
                        roots.size(),
                        2,
                        roots.size() - 2,
                        0,
                        0,
                        observedMissing,
                        examined),
                roots,
                snapshot,
                List.of(fixture));
    }

    private static Capture snapshot(
            Variant variant,
            List<SkillReference> roots,
            int expectedDistinct,
            int playerRootCount,
            int journalRootCount,
            int firstMissingIndex,
            List<Object> retainedSupport) {
        var result = SkillRetentionRootSnapshot.fromCompleteRoots(roots);
        var admission = switch (result) {
            case SkillRetentionRootSnapshot.Complete ignored -> Admission.COMPLETE;
            case SkillRetentionRootSnapshot.OverLimit ignored -> Admission.OVER_LIMIT;
            case SkillRetentionRootSnapshot.Incomplete ignored ->
                    throw new AssertionError("research complete vector became incomplete");
            case SkillRetentionRootSnapshot.Truncated ignored ->
                    throw new AssertionError("research complete vector became incomplete");
        };
        var expectedAdmission = roots.size() == OVER_LIMIT_ROOT_COUNT
                ? Admission.OVER_LIMIT : Admission.COMPLETE;
        if (admission != expectedAdmission) {
            throw new AssertionError("research root admission changed");
        }
        return new Capture(
                new Metrics(
                        variant,
                        admission,
                        roots.size(),
                        expectedDistinct,
                        roots.size() - expectedDistinct,
                        playerRootCount,
                        journalRootCount,
                        firstMissingIndex,
                        roots.size()),
                roots,
                result,
                retainedSupport);
    }

    private static List<SkillReference> distinctRoots(int count) {
        var roots = new ArrayList<SkillReference>(count);
        for (var index = 0; index < count; index++) {
            roots.add(new SkillReference(
                    new SkillId(new UUID(DISTINCT_SKILL_MSB, Integer.toUnsignedLong(index))),
                    new SkillRevision(0)));
        }
        return roots;
    }

    private static int distinctCount(List<SkillReference> roots) {
        return new HashSet<>(roots).size();
    }

    private static void requireComponentCounts(
            List<SkillReference> playerRoots,
            List<SkillReference> journalRoots) {
        if (playerRoots.size() != P4E0ResearchAttachmentFixtures.READY_PROJECTED_ROOT_COUNT
                || journalRoots.size()
                        != P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_ENTRIES) {
            throw new IllegalArgumentException(
                    "research player/journal root component count changed");
        }
    }

    public record Metrics(
            Variant variant,
            Admission admission,
            int rawRootCount,
            int distinctRootCount,
            int duplicateRootCount,
            int playerRootCount,
            int journalRootCount,
            int firstMissingIndex,
            int rootsExamined) {
        public Metrics {
            Objects.requireNonNull(variant, "variant");
            Objects.requireNonNull(admission, "admission");
            if (rawRootCount < 0
                    || distinctRootCount < 0
                    || distinctRootCount > rawRootCount
                    || duplicateRootCount != rawRootCount - distinctRootCount
                    || playerRootCount < 0
                    || journalRootCount < 0
                    || firstMissingIndex < -1
                    || rootsExamined < 0
                    || rootsExamined > rawRootCount) {
                throw new IllegalArgumentException("research root metrics are invalid");
            }
        }
    }

    /** Strongly retains the actual raw vector and its production snapshot result. */
    public static final class Capture {
        private final Metrics metrics;
        private final List<SkillReference> rawRoots;
        private final SkillRetentionRootSnapshot snapshot;
        private final List<Object> retainedSupport;

        private Capture(
                Metrics metrics,
                List<SkillReference> rawRoots,
                SkillRetentionRootSnapshot snapshot,
                List<Object> retainedSupport) {
            this.metrics = Objects.requireNonNull(metrics, "metrics");
            this.rawRoots = List.copyOf(Objects.requireNonNull(rawRoots, "rawRoots"));
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            this.retainedSupport = List.copyOf(
                    Objects.requireNonNull(retainedSupport, "retainedSupport"));
        }

        public Metrics metrics() {
            return metrics;
        }

        public void retainAtPeak() {
            Reference.reachabilityFence(rawRoots);
            Reference.reachabilityFence(snapshot);
            retainedSupport.forEach(Reference::reachabilityFence);
        }
    }
}
