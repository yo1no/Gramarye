package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.EncodedSkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SubmissionPlanTestFactory;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillStoreCarrierCapacityTest {
    @Test
    void documentEncodeRejectionPropagatesWithoutPartialBuildOrUpdate() {
        var skillId = StoreTestFixtures.skillId(700);
        var owner = StoreTestFixtures.ownerId(700);
        var plan = SubmissionPlanTestFactory.oversizedDocumentPlan(
                skillId, owner, 5, 120_000);
        var proposedDocument = plan.proposedDocument();
        var snapshot = StoreTestFixtures.snapshot(new SkillHistorySnapshot(
                skillId,
                owner,
                List.of(new SkillRevisionSnapshot(
                        proposedDocument.revision(), proposedDocument))));
        var store = StoreTestFixtures.restore(snapshot);
        var base = carrierFromHistories(List.of());
        var before = bytes(base);

        var build = assertInstanceOf(
                CarrierBuildResult.Failure.class,
                SkillStoreCarrierBuilder.rebuild(store));
        var update = assertInstanceOf(
                CarrierUpdateResult.Failure.class,
                SkillStoreCarrierBuilder.prepareProspectiveUpdate(base, plan));
        var buildCapacity = assertInstanceOf(
                StorePersistenceFailure.DocumentBlobEncodedCapacityExceeded.class,
                build.failure());
        var updateCapacity = assertInstanceOf(
                StorePersistenceFailure.DocumentBlobEncodedCapacityExceeded.class,
                update.failure());

        assertEquals(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES,
                buildCapacity.maximum());
        assertEquals(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES,
                updateCapacity.maximum());
        assertEquals(buildCapacity, updateCapacity);
        var after = store.snapshot().histories().getFirst();
        assertEquals(skillId, after.skillId());
        assertEquals(owner, after.owner());
        assertEquals(List.of(proposedDocument), after.revisions().stream()
                .map(SkillRevisionSnapshot::document).toList());
        assertArrayEquals(before, bytes(base));
        assertTrue(buildCapacity.toString().length() < 160);
    }

    @Test
    void exactHistoryCeilingIsAcceptedAndAppendPropagatesHistoryCapacity() {
        var sized = historyAtSize(
                MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES, 800);
        var base = carrierFromHistories(List.of(sized));
        var before = digest(base);
        var latest = base.histories().getFirst().latestReference();
        var plan = SubmissionPlanTestFactory.existingPlan(
                sized.skillId(), sized.owner(), latest.revision());

        var result = assertInstanceOf(
                CarrierUpdateResult.Failure.class,
                SkillStoreCarrierBuilder.prepareProspectiveUpdate(base, plan));
        var capacity = assertInstanceOf(
                StorePersistenceFailure.HistoryBlobEncodedCapacityExceeded.class,
                result.failure());

        assertEquals(MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES,
                base.totalHistoryBlobBytes());
        assertEquals(MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES,
                capacity.maximum());
        assertEquals(MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES + 1L,
                capacity.observedAtLeast());
        assertArrayEquals(before, digest(base));
    }

    @Test
    void exactStoreCeilingIsAcceptedAndAdditionPropagatesStoreCapacity() {
        var histories = new ArrayList<StoreNbtFraming.EncodedHistoryFrame>();
        for (var index = 0; index < 7; index++) {
            histories.add(historyAtSize(
                    MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES,
                    900 + index));
        }
        var remaining = Math.toIntExact(
                (long) MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES
                        - emptyStoreByteCount()
                        - histories.stream()
                                .mapToLong(history -> Integer.BYTES + history.blob().byteCount())
                                .sum()
                        - Integer.BYTES);
        histories.add(historyAtSize(remaining, 907));
        var base = carrierFromHistories(histories);
        histories.clear();
        var before = digest(base);
        var plan = SubmissionPlanTestFactory.newPlan(
                StoreTestFixtures.skillId(1_000), StoreTestFixtures.ownerId(1_000));

        var result = assertInstanceOf(
                CarrierUpdateResult.Failure.class,
                SkillStoreCarrierBuilder.prepareProspectiveUpdate(base, plan));
        var capacity = assertInstanceOf(
                StorePersistenceFailure.StoreBlobEncodedCapacityExceeded.class,
                result.failure());

        assertEquals(MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES,
                base.storeByteCount());
        assertEquals(MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES,
                capacity.maximum());
        assertEquals(MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES + 1L,
                capacity.observedAtLeast());
        assertArrayEquals(before, digest(base));
    }

    @Test
    void checkedRangeArithmeticRejectsOverflowBeforeCarrierConstruction() {
        var reference = new SkillReference(
                StoreTestFixtures.skillId(1), StoreTestFixtures.revision(0));

        assertThrows(ArithmeticException.class,
                () -> StoreNbtFraming.BlobRange.fromLong(Long.MAX_VALUE, 1));
        assertThrows(ArithmeticException.class,
                () -> new EncodedRevisionIndex(reference, Integer.MAX_VALUE, 1));
    }

    @Test
    void currentDocumentCeilingDominatesRevisionEnvelopeCeiling() {
        var largestCurrentRevision = Math.addExact(
                revisionWrapperByteCount(), MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES);

        assertTrue(largestCurrentRevision
                < MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES);
    }

    private static EncodedSkillStoreCarrier carrierFromHistories(
            List<? extends StoreNbtFraming.RoutedHistorySource> histories) {
        var frame = StoreNbtFraming.encodeStoreWithLayout(
                        StorePersistenceSchema.CURRENT_SCHEMA_VERSION, histories)
                .successValue().orElseThrow();
        return EncodedSkillStoreCarrier.fromLayout(
                StoreEncodingLayout.fromWriterFrame(frame));
    }

    private static StoreNbtFraming.EncodedHistoryFrame historyAtSize(
            int encodedLength,
            long identity) {
        var skillId = StoreTestFixtures.skillId(identity);
        var owner = StoreTestFixtures.ownerId(identity);
        var bodyLength = encodedLength - emptyHistoryByteCount(skillId, owner);
        var maximumRevisionLength = revisionWrapperByteCount()
                + MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES;
        var maximumContribution = Integer.BYTES + maximumRevisionLength;
        var fullEntries = bodyLength / maximumContribution;
        var remaining = bodyLength - fullEntries * maximumContribution;
        var revisions = new ArrayList<StoreNbtFraming.EncodedRevisionFrame>();
        for (var revision = 0; revision < fullEntries; revision++) {
            revisions.add(revisionAtSize(
                    skillId, maximumRevisionLength, revision));
        }
        if (remaining > 0) {
            revisions.add(revisionAtSize(
                    skillId, remaining - Integer.BYTES, fullEntries));
        }
        var frame = StoreNbtFraming.encodeHistoryWithLayout(skillId, owner, revisions)
                .successValue().orElseThrow();
        assertEquals(encodedLength, frame.blob().byteCount());
        return frame;
    }

    private static StoreNbtFraming.EncodedRevisionFrame revisionAtSize(
            SkillId skillId,
            int encodedLength,
            int revision) {
        var documentLength = encodedLength - revisionWrapperByteCount();
        if (documentLength <= 0
                || documentLength > MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES) {
            throw new AssertionError("requested revision length is not representable");
        }
        var reference = new SkillReference(skillId, StoreTestFixtures.revision(revision));
        var encoded = StoreNbtFraming.encodeRevisionWithRoute(
                        reference,
                        new RevisionPersistentEnvelopeV0(
                                reference.revision(),
                                StorePersistenceSchema.DOCUMENT_ENCODING,
                                EncodedSkillDocument.copyOf(new byte[documentLength])))
                .successValue().orElseThrow();
        assertEquals(encodedLength, encoded.blob().byteCount());
        return encoded;
    }

    private static int emptyStoreByteCount() {
        return StoreNbtFraming.encodeStore(new StorePersistentEnvelopeV0(0, List.of()))
                .successValue().orElseThrow().byteCount();
    }

    private static int emptyHistoryByteCount(SkillId skillId, SkillOwnerId owner) {
        return StoreNbtFraming.encodeHistory(
                        new HistoryPersistentEnvelopeV0(skillId, owner, List.of()))
                .successValue().orElseThrow().byteCount();
    }

    private static int revisionWrapperByteCount() {
        var oneByte = StoreNbtFraming.encodeRevision(new RevisionPersistentEnvelopeV0(
                        StoreTestFixtures.revision(0),
                        StorePersistenceSchema.DOCUMENT_ENCODING,
                        EncodedSkillDocument.copyOf(new byte[] {0})))
                .successValue().orElseThrow();
        return oneByte.byteCount() - 1;
    }

    private static byte[] bytes(EncodedSkillStoreCarrier carrier) {
        var bytes = new byte[carrier.storeByteCount()];
        carrier.copyStoreBlobInto(bytes, 0);
        return bytes;
    }

    private static byte[] digest(EncodedSkillStoreCarrier carrier) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes(carrier));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

}
