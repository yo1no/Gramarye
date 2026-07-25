package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SupportedDynamicTrees;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;

/** Entry point for deterministic fixed-heap P4-A3 carrier verification. */
public final class P4A3HeapProbeMain {
    static final int FULL_SIZE_MINIMUM_BYTES = 63 * 1_024 * 1_024;

    private P4A3HeapProbeMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("expected exactly one P4-A3 workload name");
        }
        System.out.println(run(arguments[0], Optional.empty()).line());
    }

    public static P4A3ProbeSummary runDedicated(HolderLookup.Provider provider) {
        return run("dedicated-mixed", Optional.of(provider));
    }

    static P4A3ProbeSummary run(
            String workloadName,
            Optional<HolderLookup.Provider> provider) {
        var startedAt = System.nanoTime();
        var heap = P4A3ProbeHeapMetrics.start();
        var expectedShape = P4A3ProbeWorkloads.shape(workloadName);
        var workload = P4A3ProbeWorkloads.create(workloadName, provider);
        heap.sample();

        var baseResult = SkillStoreCarrierBuilder.rebuild(workload.store());
        if (baseResult instanceof CarrierBuildResult.Failure failure) {
            var firstDocument = workload.store().find(workload.expectedFirstLatest()).orElseThrow();
            var documentResult = com.yo1no.gramarye.magic.definition.document
                    .SkillDocumentStorePersistenceFacade.encodeCurrent(firstDocument);
            throw new AssertionError("carrier rebuild failed: "
                    + failure.failure().getClass().getSimpleName()
                    + " document=" + documentResult);
        }
        var base = requireBuild(baseResult);
        heap.sample();
        requireFullSize(base, expectedShape.expectedBaseBlobBytes());
        requireCarrierShape(
                base,
                workload.store(),
                workload.expectedHistoryCount(),
                workload.expectedRevisionCount(),
                workload.expectedFirstLatest(),
                workload.expectedLastLatest());
        requireEntryBounds(base, !workloadName.equals("many-small"));

        // This is the save-callback-shaped full-size copy and remains live while old/new carriers
        // coexist. It is also consumed by the checksum and byte-for-byte immutability checks.
        var baseSaveCopy = copy(base);

        var a2Encoding = SkillDefinitionStorePersistenceBridge.encodeCurrentStoreBlob(
                workload.store());
        var a2Blob = switch (a2Encoding) {
            case StorePersistenceEncodeResult.Success success -> success.blob();
            case StorePersistenceEncodeResult.Failure failure ->
                    throw new AssertionError("A2 Store encode rejected the legal probe workload: "
                            + failure.failure().getClass().getSimpleName());
        };
        requireBytesEqual(baseSaveCopy, a2Blob.copyBytes(), "carrier/A2 encode mismatch");
        heap.sample();

        var loadedResult = SkillDefinitionStorePersistenceBridge.loadStoreBlob(a2Blob, provider);
        var loadedStore = switch (loadedResult) {
            case StorePersistenceLoadResult.Loaded loaded -> {
                if (loaded.rewritePending()) {
                    throw new AssertionError("current Store unexpectedly requested canonical rewrite");
                }
                yield loaded.store();
            }
            case StorePersistenceLoadResult.Failure failure ->
                    throw new AssertionError("A2 load rejected the legal probe workload: "
                            + failure.failure().getClass().getSimpleName());
        };
        requireCarrierShape(
                base,
                loadedStore,
                workload.expectedHistoryCount(),
                workload.expectedRevisionCount(),
                workload.expectedFirstLatest(),
                workload.expectedLastLatest());
        requireContexts(loadedStore, workload);
        heap.sample();

        var prepared = switch (SkillStoreCarrierBuilder.prepareProspectiveUpdate(
                base, workload.plan())) {
            case CarrierUpdateResult.Prepared success -> success.update();
            case CarrierUpdateResult.Failure failure ->
                    throw new AssertionError("prospective carrier rejected: "
                            + failure.failure().getClass().getSimpleName());
        };
        if (!prepared.isFor(base) || prepared.baseCarrier() != base) {
            throw new AssertionError("prospective carrier lost its base identity");
        }
        var prospective = prepared.prospectiveCarrier();
        if (prospective.historyCount() != workload.expectedProspectiveHistoryCount()
                || prospective.revisionCount() != workload.expectedProspectiveRevisionCount()) {
            throw new AssertionError("prospective carrier counts do not match the workload");
        }
        if (prospective.storeByteCount()
                > MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES) {
            throw new AssertionError("prospective carrier exceeds the Store ceiling");
        }
        if (prospective.findHistory(prepared.proposedReference().skillId())
                .flatMap(history -> history.findRevision(
                        prepared.proposedReference().revision()))
                .isEmpty()) {
            throw new AssertionError("prospective route is absent");
        }

        // Both carrier objects and their root blobs are strongly reachable at this point.
        var prospectiveCopy = copy(prospective);
        requireProspectiveMatchesCommittedStore(
                prospective,
                prospectiveCopy,
                loadedStore,
                workload);
        heap.sample();
        if (base == prospective || prepared.baseCarrier() == prospective) {
            throw new AssertionError("prospective carrier reused the base object");
        }
        requireBytesEqual(baseSaveCopy, copy(base), "prospective update mutated base carrier");

        var reclaim = loadedStore.reclaim(
                SkillRetentionRootSnapshot.fromCompleteRoots(List.of()));
        if (!(reclaim instanceof SkillReclaimResult.Completed completed)
                || completed.report().revisionsReclaimed() <= 0) {
            throw new AssertionError("probe reclaim did not remove an old revision");
        }
        var postReclaimSnapshot = loadedStore.snapshot();
        var filtered = SkillStoreCarrierBuilder.filterAfterReclaim(
                base, postReclaimSnapshot);
        var rebuiltAfterReclaim = requireBuild(
                SkillStoreCarrierBuilder.rebuild(loadedStore));
        var filteredCopy = copy(filtered);
        requireBytesEqual(
                filteredCopy,
                copy(rebuiltAfterReclaim),
                "filtered carrier differs from full post-reclaim rebuild");
        heap.sample();

        // Consume all full-size results so the probe outcome cannot be optimized into metadata.
        var checksum = checksum(
                List.of(base, prospective, filtered),
                baseSaveCopy,
                prospectiveCopy,
                filteredCopy);
        if (base.storeByteCount() != baseSaveCopy.length
                || prospective.storeByteCount() != prospectiveCopy.length
                || filtered.storeByteCount() != filteredCopy.length) {
            throw new AssertionError("carrier copy length mismatch");
        }
        var heapSnapshot = heap.finish();
        var elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        return new P4A3ProbeSummary(
                workload.name(),
                base.storeByteCount(),
                base.historyCount(),
                base.revisionCount(),
                heapSnapshot.maximum(),
                heapSnapshot.initialCommitted(),
                heapSnapshot.peakUsed(),
                heapSnapshot.poolPeakSum(),
                elapsedMillis,
                checksum);
    }

    private static EncodedSkillStoreCarrier requireBuild(CarrierBuildResult result) {
        return switch (result) {
            case CarrierBuildResult.Success success -> success.carrier();
            case CarrierBuildResult.Failure failure ->
                    throw new AssertionError("carrier rebuild failed: "
                            + failure.failure().getClass().getSimpleName()
                            + " " + failure.failure());
        };
    }

    private static void requireFullSize(
            EncodedSkillStoreCarrier carrier,
            int expectedBlobBytes) {
        if (carrier.storeByteCount() < FULL_SIZE_MINIMUM_BYTES
                || carrier.storeByteCount()
                        > MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES) {
            throw new AssertionError("actual Store blob is outside the 63-64 MiB gate: "
                    + carrier.storeByteCount());
        }
        if (carrier.storeByteCount() != expectedBlobBytes) {
            throw new AssertionError("deterministic Store blob size changed: expected "
                    + expectedBlobBytes + " but was " + carrier.storeByteCount());
        }
    }

    private static void requireEntryBounds(
            EncodedSkillStoreCarrier carrier,
            boolean requireNearEntryShape) {
        var minimumLargeRevision = MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES * 9 / 10;
        var minimumLargeHistory = MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES * 9 / 10;
        for (var history : carrier.histories()) {
            if (history.byteLength()
                    > MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES) {
                throw new AssertionError("history exceeds its encoded byte ceiling");
            }
            if (requireNearEntryShape && history.byteLength() < minimumLargeHistory) {
                throw new AssertionError("large-entry workload history is not near its ceiling");
            }
            for (var revision : history.revisions()) {
                if (revision.byteLength()
                        > MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES) {
                    throw new AssertionError("revision exceeds its encoded byte ceiling");
                }
                if (requireNearEntryShape && revision.byteLength() < minimumLargeRevision) {
                    throw new AssertionError("large-entry workload revision is not near its ceiling");
                }
            }
        }
    }

    private static void requireCarrierShape(
            EncodedSkillStoreCarrier carrier,
            SkillDefinitionStore store,
            int expectedHistories,
            int expectedRevisions,
            com.yo1no.gramarye.magic.definition.document.SkillReference expectedFirst,
            com.yo1no.gramarye.magic.definition.document.SkillReference expectedLast) {
        if (carrier.historyCount() != expectedHistories
                || carrier.revisionCount() != expectedRevisions) {
            throw new AssertionError("carrier count mismatch");
        }
        if (!carrier.histories().getFirst().latestReference().equals(expectedFirst)
                || !carrier.histories().getLast().latestReference().equals(expectedLast)) {
            throw new AssertionError("carrier boundary route mismatch");
        }
        var revisions = 0;
        com.yo1no.gramarye.magic.api.id.SkillId previous = null;
        for (var history : carrier.histories()) {
            if (previous != null
                    && previous.value().compareTo(history.skillId().value()) >= 0) {
                throw new AssertionError("carrier route order is not canonical");
            }
            previous = history.skillId();
            if (!store.ownerOf(history.skillId()).orElseThrow().equals(history.owner())) {
                throw new AssertionError("loaded owner differs from carrier owner");
            }
            if (!store.latestReference(history.skillId()).orElseThrow()
                    .equals(history.latestReference())) {
                throw new AssertionError("loaded latest differs from carrier latest");
            }
            for (var revision : history.revisions()) {
                var document = store.find(revision.reference()).orElseThrow();
                if (!document.skillId().equals(revision.reference().skillId())
                        || !document.revision().equals(revision.reference().revision())) {
                    throw new AssertionError("loaded document route mismatch");
                }
                revisions++;
            }
        }
        if (revisions != expectedRevisions) {
            throw new AssertionError("loaded revision traversal count mismatch");
        }
    }

    private static void requireProspectiveMatchesCommittedStore(
            EncodedSkillStoreCarrier prospective,
            byte[] prospectiveCopy,
            SkillDefinitionStore baseStore,
            P4A3ProbeWorkload workload) {
        var expectedStore = switch (SkillDefinitionStore.restore(baseStore.snapshot())) {
            case SkillDefinitionStoreRestoreResult.Restored restored -> restored.store();
            case SkillDefinitionStoreRestoreResult.Rejected rejected ->
                    throw new AssertionError("prospective fixture restore rejected: "
                            + rejected.failure().getClass().getSimpleName());
        };
        var proposedDocument = workload.plan().proposedDocument();
        var proposedReference = new com.yo1no.gramarye.magic.definition.document.SkillReference(
                proposedDocument.skillId(), proposedDocument.revision());
        if (!(expectedStore.commit(workload.plan(), SkillQuota.Unlimited.INSTANCE)
                instanceof SkillStoreCommitResult.Committed committed)
                || !committed.committed().equals(proposedReference)) {
            throw new AssertionError("legal prospective plan did not commit to its expected route");
        }
        var rebuilt = requireBuild(SkillStoreCarrierBuilder.rebuild(expectedStore));
        requireBytesEqual(
                prospectiveCopy,
                copy(rebuilt),
                "prospective carrier differs from committed Store full rebuild");
        requireCarrierShape(
                prospective,
                expectedStore,
                workload.expectedProspectiveHistoryCount(),
                workload.expectedProspectiveRevisionCount(),
                prospective.histories().getFirst().latestReference(),
                prospective.histories().getLast().latestReference());
    }

    private static void requireContexts(
            SkillDefinitionStore store,
            P4A3ProbeWorkload workload) {
        var document = store.find(workload.expectedFirstLatest()).orElseThrow();
        var actual = contexts(document);
        if (!actual.equals(workload.expectedContexts())) {
            throw new AssertionError("hydrated raw contexts do not match the workload");
        }
        var registryCount = actual.stream().filter(SerializedTreeContext::registryContext).count();
        if (workload.registryContextsRequired() && registryCount < 3) {
            throw new AssertionError("dedicated workload lacks required RegistryOps contexts");
        }
        if (!workload.registryContextsRequired() && registryCount != 0) {
            throw new AssertionError("plain workload unexpectedly contains RegistryOps contexts");
        }
    }

    private static Set<SerializedTreeContext> contexts(SkillDocument document) {
        var contexts = new HashSet<SerializedTreeContext>();
        for (var node : document.nodes()) {
            contexts.add(context(node.trigger().copyRawPayload()));
            contexts.add(context(node.action().copyRawPayload()));
            if (node.appearanceOverride()
                    instanceof AppearanceOverrideDocument.Unparsed unparsed) {
                contexts.add(context(unparsed.copyRawAppearance()));
            }
        }
        if (document.appearance() instanceof AppearanceDocument.Unparsed unparsed) {
            contexts.add(context(unparsed.copyRawAppearance()));
        }
        return Set.copyOf(contexts);
    }

    private static SerializedTreeContext context(
            com.mojang.serialization.Dynamic<?> dynamic) {
        return SupportedDynamicTrees.contextOf(dynamic).result().orElseThrow();
    }

    private static byte[] copy(EncodedSkillStoreCarrier carrier) {
        var result = new byte[carrier.storeByteCount()];
        carrier.copyStoreBlobInto(result, 0);
        return result;
    }

    private static void requireBytesEqual(
            byte[] expected,
            byte[] actual,
            String failure) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(failure);
        }
    }

    static String checksum(
            List<EncodedSkillStoreCarrier> carriers,
            byte[]... values) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var value : values) {
                digest.update(value);
            }
            for (var carrier : carriers) {
                updateLong(digest, carrier.historyCount());
                updateLong(digest, carrier.revisionCount());
                for (var history : carrier.histories()) {
                    updateLong(digest, history.skillId().value().getMostSignificantBits());
                    updateLong(digest, history.skillId().value().getLeastSignificantBits());
                    updateLong(digest, history.owner().value().getMostSignificantBits());
                    updateLong(digest, history.owner().value().getLeastSignificantBits());
                    for (var revision : history.revisions()) {
                        updateLong(digest, revision.reference().revision().value());
                    }
                }
            }
            var encoded = digest.digest();
            var result = new StringBuilder(16);
            for (var index = 0; index < 8; index++) {
                result.append(Character.forDigit((encoded[index] >>> 4) & 0xF, 16));
                result.append(Character.forDigit(encoded[index] & 0xF, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is unavailable", exception);
        }
    }

    private static void updateLong(MessageDigest digest, long value) {
        for (var shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            digest.update((byte) (value >>> shift));
        }
    }
}
