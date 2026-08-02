package com.yo1no.gramarye.magic.definition.research;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.P4E0ResearchAttachmentFixtures;
import com.yo1no.gramarye.magic.definition.store.P4E0ResearchGzipAdapter;
import com.yo1no.gramarye.magic.definition.store.P4E0ResearchStoreJournalFixtures;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/** Deterministic synthetic fixture builder; it never accepts a user world or playerdata path. */
final class P4E0ResearchFixtureFactory {
    private static final String ATTACHMENT_KEY = "gramarye:player_skills";
    private static final Pattern PRIMARY = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.dat");
    private static final Pattern OLD = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.dat_old");
    private static final int CURRENT_DATA_VERSION = 3_955;
    private static final long ROOT_FRAMING_BYTES = 3L;
    private static final int BASE_RELEVANT_RECORDS = 9;
    private static final int BASE_DIRECTORY_ENTRIES = 16;
    private static final int MAXIMUM_SUPPLEMENTAL_MANIFEST_RECORDS = 12;
    private static final int MAXIMUM_SUPPORTED_TARGET_DEPTH = 513;
    private static final int MAXIMUM_SUPPORTED_MANIFEST_RECORDS = 49;

    private P4E0ResearchFixtureFactory() {
    }

    record Observation(
            List<P4E0ResearchResult.FixtureManifest> manifest,
            P4E0ResearchResult.DirectoryMetrics directory,
            P4E0ResearchResult.WireMetrics wire,
            P4E0ResearchNbtMetrics nbt,
            P4E0ResearchResult.AttachmentMetrics attachment,
            P4E0ResearchResult.RootMetrics roots,
            P4E0ResearchResult.StoreJournalMetrics storeJournal,
            P4E0ResearchResult.Integrity integrity,
            List<Object> retainedAtPeak) {
        Observation {
            manifest = List.copyOf(manifest);
            retainedAtPeak = List.copyOf(retainedAtPeak);
        }

        void retainAtSamplingPoint() {
            retainedAtPeak.forEach(Reference::reachabilityFence);
        }
    }

    static Observation prepareSmoke(
            P4E0ResearchParameters parameters, Path fixtureRoot) throws IOException {
        var safeRoot = requireFixtureRoot(fixtureRoot);
        clearResearchTree(safeRoot);
        Files.createDirectories(safeRoot);
        return createAndMeasure(parameters, safeRoot);
    }

    static Observation observeSmoke(
            P4E0ResearchParameters parameters, Path fixtureRoot) throws IOException {
        var safeRoot = requireFixtureRoot(fixtureRoot);
        if (!Files.isDirectory(safeRoot.resolve("playerdata"))) {
            throw new IOException("synthetic playerdata directory is absent");
        }
        return measureExisting(parameters, safeRoot);
    }

    private static Observation createAndMeasure(
            P4E0ResearchParameters parameters, Path root) throws IOException {
        requireSupportedSmoke(parameters, root);
        var playerdata = root.resolve("playerdata");
        var auxiliary = root.resolve("auxiliary");
        var zeroRootDirectory = root.resolve("zero-root-directory");
        Files.createDirectories(playerdata);
        Files.createDirectories(auxiliary);
        Files.createDirectories(zeroRootDirectory);

        for (var ordinal = 0; ordinal < 16; ordinal++) {
            writeCompressed(
                    minimalCurrentPlayerdata(uuid(parameters.seed(), 100 + ordinal), ordinal),
                    zeroRootDirectory.resolve(
                            uuid(parameters.seed(), 100 + ordinal) + ".dat"));
        }

        var minimal = minimalCurrentPlayerdata(uuid(parameters.seed(), 0), 0);
        var ready = P4E0ResearchAttachmentFixtures.readyRootMax(
                parameters.scenario().includeExistingMixedDraft());
        var preserved = P4E0ResearchAttachmentFixtures.preservedRawExact();
        var oversize = P4E0ResearchAttachmentFixtures.oversizeMarker();
        var unrelated = unrelatedWholeNbt(
                uuid(parameters.seed(), 4),
                parameters.targetCompoundEntries(),
                parameters.targetListElements(),
                parameters.targetArrayElements());
        var acceptedDepth = Math.min(512, parameters.targetDepth() - 1);
        var depthAccepted = depthFixture(uuid(parameters.seed(), 5), acceptedDepth);
        var depthTarget = depthFixture(
                uuid(parameters.seed(), 6), parameters.targetDepth());

        var names = fixtureNames(parameters.seed());
        writeCompressed(minimal, playerdata.resolve(names.get(0)));
        Files.writeString(
                playerdata.resolve(names.get(1)),
                "arbitrary-malformed-old-not-selected\n",
                StandardCharsets.UTF_8);
        writeCompressed(withAttachment(
                minimalCurrentPlayerdata(uuid(parameters.seed(), 1), 1),
                ready.serializedTag()), playerdata.resolve(names.get(2)));
        writeCompressed(withAttachment(
                minimalCurrentPlayerdata(uuid(parameters.seed(), 2), 2),
                preserved.serializedTag()), playerdata.resolve(names.get(3)));
        writeCompressed(withAttachment(
                minimalCurrentPlayerdata(uuid(parameters.seed(), 3), 3),
                oversize.inputTag()), playerdata.resolve(names.get(4)));
        writeCompressed(unrelated, playerdata.resolve(names.get(5)));
        writeCompressed(depthAccepted, playerdata.resolve(names.get(6)));

        var optionalPath = playerdata.resolve(names.get(7));
        P4E0ResearchGzipAdapter.writeAndRead(
                optionalPath,
                minimalCurrentPlayerdata(uuid(parameters.seed(), 7), 7),
                P4E0ResearchGzipAdapter.Options.smokeOptionalFields(),
                parameters.compressedGuardBytes(),
                parameters.decompressedGuardBytes(),
                parameters.nbtQuotaBytes());

        Files.writeString(playerdata.resolve(names.get(8)), "malformed-route\n",
                StandardCharsets.UTF_8);
        Files.writeString(playerdata.resolve(names.get(9)), "uppercase-route\n",
                StandardCharsets.UTF_8);
        Files.writeString(playerdata.resolve(names.get(10)), "ignored\n",
                StandardCharsets.UTF_8);
        Files.writeString(playerdata.resolve(names.get(11)), "ignored\n",
                StandardCharsets.UTF_8);
        Files.writeString(playerdata.resolve(names.get(12)), "irrelevant\n",
                StandardCharsets.UTF_8);
        writeCompressed(minimalCurrentPlayerdata(uuid(parameters.seed(), 8), 8),
                playerdata.resolve(names.get(13)));
        writeCompressed(minimalCurrentPlayerdata(uuid(parameters.seed(), 9), 9),
                playerdata.resolve(names.get(14)));
        Files.writeString(playerdata.resolve(names.get(15)), "malformed-old\n",
                StandardCharsets.UTF_8);
        createSupplementalRecords(
                parameters, playerdata, ready, preserved);
        createSupplementalIgnoredEntries(parameters, playerdata);

        var depthTargetPath = auxiliary.resolve("depth-target-negative.dat");
        writeCompressed(depthTarget, depthTargetPath);
        for (var depth : List.of(1, 64, 128, 256)) {
            var ladderPath = auxiliary.resolve("depth-" + depth + ".dat");
            writeCompressed(depthFixture(
                    uuid(parameters.seed(), 200 + depth), depth), ladderPath);
            var decoded = NbtIo.readCompressed(
                    ladderPath, NbtAccounter.create(parameters.nbtQuotaBytes()));
            if (P4E0ResearchNbtMetrics.measure(decoded).maxContainerDepth() != depth) {
                throw new IllegalStateException("depth ladder fixture changed");
            }
        }
        requireDepthBoundary(
                playerdata.resolve(names.get(6)),
                acceptedDepth,
                depthTargetPath,
                parameters.targetDepth(),
                parameters.nbtQuotaBytes());

        var deterministicOne = auxiliary.resolve("determinism-a.dat");
        var deterministicTwo = auxiliary.resolve("determinism-b.dat");
        P4E0ResearchGzipAdapter.writeAndRead(
                deterministicOne, minimal,
                P4E0ResearchGzipAdapter.Options.canonical(),
                parameters.compressedGuardBytes(),
                parameters.decompressedGuardBytes(),
                parameters.nbtQuotaBytes());
        P4E0ResearchGzipAdapter.writeAndRead(
                deterministicTwo, minimal,
                P4E0ResearchGzipAdapter.Options.canonical(),
                parameters.compressedGuardBytes(),
                parameters.decompressedGuardBytes(),
                parameters.nbtQuotaBytes());
        if (!P4E0ResearchHashing.sha256(deterministicOne)
                .equals(P4E0ResearchHashing.sha256(deterministicTwo))) {
            throw new IllegalStateException("same seed produced different fixture bytes");
        }
        createOptionalTargetFixtures(parameters, auxiliary);
        return measureExisting(parameters, root);
    }

    private static Observation measureExisting(
            P4E0ResearchParameters parameters, Path root) throws IOException {
        requireSupportedSmoke(parameters, root);
        var playerdata = root.resolve("playerdata");
        var names = fixtureNames(parameters.seed());
        var manifests = new ArrayList<P4E0ResearchResult.FixtureManifest>();
        var totals = new MeasurementTotals();
        var selectedPhysical = 0L;
        var selectedHeader = 0L;
        var selectedDecompressed = 0L;
        var decodedChecksums = new TreeMap<String, String>();

        var primaryOverOld = selectResearchSource(playerdata, uuid(parameters.seed(), 0));
        var oldFallback = selectResearchSource(playerdata, uuid(parameters.seed(), 8));
        var pairedOldPath = playerdata.resolve(names.get(1)).toAbsolutePath().normalize();
        if (primaryOverOld.source() != ResearchPhysicalSource.PRIMARY
                || !primaryOverOld.primaryPresent()
                || !primaryOverOld.oldPresent()
                || !primaryOverOld.selectedPath().equals(
                        playerdata.resolve(names.get(0)).toAbsolutePath().normalize())
                || oldFallback.source() != ResearchPhysicalSource.OLD
                || oldFallback.primaryPresent()
                || !oldFallback.oldPresent()
                || !oldFallback.selectedPath().equals(
                        playerdata.resolve(names.get(13)).toAbsolutePath().normalize())
                || P4E0ResearchHashing.sha256(primaryOverOld.selectedPath()).equals(
                        P4E0ResearchHashing.sha256(pairedOldPath))) {
            throw new IllegalStateException(
                    "research-only primary/old physical selection shape changed");
        }

        var selectedRecords = List.of(
                new SelectedFixture(
                        0,
                        primaryOverOld.selectedPath(),
                        aliasForIndex(0),
                        stateForIndex(0)),
                new SelectedFixture(
                        2,
                        selectResearchSource(playerdata, uuid(parameters.seed(), 1))
                                .selectedPath(),
                        aliasForIndex(2),
                        stateForIndex(2)),
                new SelectedFixture(
                        3,
                        selectResearchSource(playerdata, uuid(parameters.seed(), 2))
                                .selectedPath(),
                        aliasForIndex(3),
                        stateForIndex(3)),
                new SelectedFixture(
                        4,
                        selectResearchSource(playerdata, uuid(parameters.seed(), 3))
                                .selectedPath(),
                        aliasForIndex(4),
                        stateForIndex(4)),
                new SelectedFixture(
                        5,
                        selectResearchSource(playerdata, uuid(parameters.seed(), 4))
                                .selectedPath(),
                        aliasForIndex(5),
                        stateForIndex(5)),
                new SelectedFixture(
                        6,
                        selectResearchSource(playerdata, uuid(parameters.seed(), 5))
                                .selectedPath(),
                        aliasForIndex(6),
                        stateForIndex(6)),
                new SelectedFixture(
                        7,
                        selectResearchSource(playerdata, uuid(parameters.seed(), 7))
                                .selectedPath(),
                        aliasForIndex(7),
                        stateForIndex(7)),
                new SelectedFixture(
                        13,
                        oldFallback.selectedPath(),
                        aliasForIndex(13),
                        stateForIndex(13)),
                new SelectedFixture(
                        14,
                        selectResearchSource(playerdata, uuid(parameters.seed(), 9))
                                .selectedPath(),
                        aliasForIndex(14),
                        stateForIndex(14)));

        for (var selected : selectedRecords) {
            var index = selected.fixtureIndex();
            var path = selected.path();
            var observation = P4E0ResearchGzipAdapter.read(
                    path,
                    parameters.compressedGuardBytes(),
                    parameters.decompressedGuardBytes(),
                    parameters.nbtQuotaBytes());
            var tag = observation.decodedRoot();
            requireCurrentDataVersion(tag);
            var measured = totals.observePlayerdata(observation, tag);
            requireExpectedAttachment(index, measured.attachmentVariant());
            var hash = P4E0ResearchHashing.sha256(path);
            manifests.add(new P4E0ResearchResult.FixtureManifest(
                    caseForIndex(index),
                    selected.alias(),
                    observation.physicalFileBytes(),
                    hash,
                    selected.stateCode(),
                    measured.metrics()));
            if (index == 7) {
                selectedPhysical = observation.physicalFileBytes();
                selectedHeader = observation.gzipHeaderBytes();
                selectedDecompressed = observation.decompressedRootBytes();
            }
            decodedChecksums.put(
                    portable(root.relativize(path)),
                    P4E0ResearchHashing.semanticTagChecksum(tag));
        }
        manifests.add(new P4E0ResearchResult.FixtureManifest(
                P4E0ResearchCase.MIXED_DIRECTORY,
                "selection-paired-arbitrary-old-not-selected",
                Files.size(pairedOldPath),
                P4E0ResearchHashing.sha256(pairedOldPath),
                "MALFORMED_OLD_PRESENT_DISTINCT_NOT_SELECTED",
                null));

        var supplementalRecords = parameters.relevantRecords() - BASE_RELEVANT_RECORDS;
        for (var index = 0; index < supplementalRecords; index++) {
            var kind = supplementalKind(parameters, index);
            var path = supplementalPath(playerdata, parameters.seed(), index);
            var observation = P4E0ResearchGzipAdapter.read(
                    path,
                    parameters.compressedGuardBytes(),
                    parameters.decompressedGuardBytes(),
                    parameters.nbtQuotaBytes());
            var tag = observation.decodedRoot();
            requireCurrentDataVersion(tag);
            var measured = totals.observePlayerdata(observation, tag);
            requireExpectedAttachment(kind, measured.attachmentVariant());
            manifests.add(new P4E0ResearchResult.FixtureManifest(
                    switch (kind) {
                        case READY -> P4E0ResearchCase.READY_ROOT_MAX;
                        case PRESERVED_RAW -> P4E0ResearchCase.PRESERVED_RAW_EXACT;
                        case ZERO_ROOT -> P4E0ResearchCase.ZERO_ROOT_MINIMAL;
                    },
                    String.format(Locale.ROOT, "supplemental-record-%02d", index),
                    observation.physicalFileBytes(),
                    P4E0ResearchHashing.sha256(path),
                    switch (kind) {
                        case READY -> "READY_ROOT_MAX";
                        case PRESERVED_RAW -> "QUARANTINED_PRESERVED_RAW";
                        case ZERO_ROOT -> "ZERO_ROOT";
                    },
                    measured.metrics()));
            decodedChecksums.put(
                    portable(root.relativize(path)),
                    P4E0ResearchHashing.semanticTagChecksum(tag));
        }

        var zeroDirectory = root.resolve("zero-root-directory");
        var zeroDirectoryShape = measureDirectory(zeroDirectory);
        if (zeroDirectoryShape.directoryEntriesObserved() != 16L
                || zeroDirectoryShape.canonicalPrimaryNames() != 16L
                || zeroDirectoryShape.canonicalOldNames() != 0L
                || zeroDirectoryShape.uniqueUuidRecords() != 16L
                || zeroDirectoryShape.ignoredEntries() != 0L
                || zeroDirectoryShape.relevantMalformedEntries() != 0L) {
            throw new IllegalStateException(
                    "ZERO_ROOT filesystem directory shape changed");
        }
        var zeroCount = 0;
        var zeroProjectedRoots = 0L;
        try (var stream = Files.newDirectoryStream(zeroDirectory, "*.dat")) {
            var paths = new ArrayList<Path>();
            for (var path : stream) {
                paths.add(path);
            }
            paths.sort(Path::compareTo);
            for (var path : paths) {
                var observation = P4E0ResearchGzipAdapter.read(
                        path,
                        parameters.compressedGuardBytes(),
                        parameters.decompressedGuardBytes(),
                        parameters.nbtQuotaBytes());
                var tag = observation.decodedRoot();
                requireCurrentDataVersion(tag);
                var measured = totals.observePlayerdata(observation, tag);
                if (measured.attachmentVariant().isPresent()) {
                    throw new IllegalStateException(
                            "ZERO_ROOT fixture unexpectedly admitted an Attachment");
                }
                zeroProjectedRoots = Math.addExact(
                        zeroProjectedRoots,
                        measured.metrics().roots().projectedRootCount());
                var decodedKey = portable(root.relativize(path));
                decodedChecksums.put(
                        decodedKey, P4E0ResearchHashing.semanticTagChecksum(tag));
                manifests.add(new P4E0ResearchResult.FixtureManifest(
                        P4E0ResearchCase.ZERO_ROOT_MINIMAL,
                        String.format(Locale.ROOT, "zero-root-record-%02d", zeroCount),
                        observation.physicalFileBytes(),
                        P4E0ResearchHashing.sha256(path),
                        "ZERO_ROOT",
                        measured.metrics()));
                zeroCount++;
            }
        }
        if (zeroCount != 16 || zeroProjectedRoots != 0L) {
            throw new IllegalStateException(
                    "ZERO_ROOT directory is not exactly 16 records with zero projected roots");
        }
        if (P4E0ResearchAttachmentFixtures.PRESERVED_RAW_BYTES != 16_777_216L
                || P4E0ResearchAttachmentFixtures.OVERSIZE_INPUT_BYTES != 16_777_217L
                || P4E0ResearchAttachmentFixtures.OVERSIZE_MARKER_BYTES != 142L) {
            throw new IllegalStateException("P4-C exact Attachment fixture changed");
        }
        if (totals.readyAdmissions() != readyRecordCount(parameters)
                || totals.preservedAdmissions() != parameters.preservedRawRecordCount()
                || totals.oversizeAdmissions() != 1L
                || totals.distinctProjectedRoots() != 256L) {
            throw new IllegalStateException("Ready root-max fixture shape changed");
        }
        // The v0 smoke resource supplies 65_537; later research remains parameter-driven.
        var boundary = P4E0ResearchStoreJournalFixtures.rootBoundary(
                totals.firstProjectedRoot(), parameters.rootClaims());
        if (boundary.exactInputCount() != 65_536
                || boundary.overInputCount() != parameters.rootClaims()
                || !boundary.exactAccepted()
                || !boundary.overRejected()) {
            throw new IllegalStateException("root boundary construction changed");
        }
        var reduced = P4E0ResearchStoreJournalFixtures.reducedEnvelope();
        var directory = measureDirectory(playerdata);
        if (directory.directoryEntriesObserved() != parameters.directoryEntries()) {
            throw new IllegalStateException("smoke directory entry count changed");
        }
        if (directory.uniqueUuidRecords() != parameters.relevantRecords()) {
            throw new IllegalStateException("smoke relevant record count changed");
        }

        addAuxiliaryMeasurements(
                parameters, root, manifests, decodedChecksums, totals);
        var playerdataTree = hashSyntheticTree(playerdata);
        manifests.add(new P4E0ResearchResult.FixtureManifest(
                P4E0ResearchCase.MIXED_DIRECTORY,
                "mixed-directory-summary",
                playerdataTree.physicalBytes(),
                playerdataTree.hash(),
                "DIRECTORY_CLASSIFIED",
                null));

        var reducedFacts = reduced.facts();
        var reducedCarrierBytes = countUnnamed(reduced.savedDataInnerTag());
        manifests.add(new P4E0ResearchResult.FixtureManifest(
                P4E0ResearchCase.COMBINED_ENVELOPE,
                "reduced-combined-envelope",
                reducedCarrierBytes,
                P4E0ResearchHashing.sha256(
                        reducedFacts.storeChecksum() + '|'
                                + Integer.toString(reducedFacts.journalBytes()) + '|'
                                + Long.toString(reducedCarrierBytes)),
                "REDUCED_NON_AUTHORITY_SMOKE",
                null));

        var fixtureTree = hashSyntheticTree(root);
        var decodedTree = hashDecodedChecksums(decodedChecksums);
        var fixtureHash = fixtureTree.hash();
        var semantic = P4E0ResearchHashing.sha256(
                fixtureHash + ':' + decodedTree.hash() + ':'
                        + totals.projectedRootCount() + ':'
                        + totals.nbt().tagCountTotal() + ':'
                        + reducedFacts.storeChecksum() + ':'
                        + reducedFacts.journalBytes());
        var wire = new P4E0ResearchResult.WireMetrics(
                selectedPhysical,
                selectedHeader,
                selectedPhysical,
                selectedDecompressed,
                ROOT_FRAMING_BYTES,
                totals.physicalBytes(),
                totals.decompressedBytes());
        var attachment = totals.attachmentMetrics();
        var roots = new P4E0ResearchResult.RootMetrics(
                totals.projectedRootCount(),
                boundary.overInputCount(),
                totals.distinctProjectedRoots(),
                boundary.exactAccepted() ? "COMPLETE" : "UNEXPECTED",
                boundary.overRejected() ? "OVER_LIMIT" : "UNEXPECTED");
        var store = new P4E0ResearchResult.StoreJournalMetrics(
                reducedFacts.storeBytes(),
                reducedFacts.historyCount(),
                reducedFacts.revisionCount(),
                reducedFacts.journalBytes(),
                reducedFacts.journalEntries(),
                reducedCarrierBytes,
                "REDUCED_NON_AUTHORITY_SMOKE");
        if (manifests.size() > supportedMaximumManifestCount()) {
            throw new IllegalStateException("research manifest exceeded supported maximum");
        }
        var retained = new ArrayList<Object>(totals.retainedAtPeak());
        retained.add(boundary);
        retained.add(reduced);
        retained.add(reduced.savedDataInnerTag().copy());
        return new Observation(
                manifests,
                directory,
                wire,
                totals.nbt(),
                attachment,
                roots,
                store,
                new P4E0ResearchResult.Integrity(
                        fixtureHash,
                        fixtureTree.fileCount(),
                        decodedTree.hash(),
                        decodedTree.fileCount(),
                        semantic),
                retained);
    }

    private static void requireSupportedSmoke(
            P4E0ResearchParameters parameters, Path root) {
        if (!parameters.outputDirectory().equals(root.toAbsolutePath().normalize())
                || parameters.targetDepth() < 2
                || parameters.targetDepth() > supportedMaximumTargetDepth()) {
            throw new IllegalArgumentException("unsupported R1 smoke scenario or coordinate");
        }
        var supplemental = parameters.relevantRecords() - BASE_RELEVANT_RECORDS;
        var ready = readyRecordCount(parameters);
        if (supplemental < 0
                || supplemental > MAXIMUM_SUPPLEMENTAL_MANIFEST_RECORDS
                || parameters.directoryEntries() < BASE_DIRECTORY_ENTRIES + supplemental
                || ready < 1
                || ready > parameters.relevantRecords()
                || parameters.preservedRawRecordCount() < 1
                || Math.addExact(
                                ready - 1L,
                                (long) parameters.preservedRawRecordCount() - 1L)
                        > supplemental
                || parameters.compressedTargetBytes()
                        > parameters.compressedGuardBytes()
                || parameters.decompressedTargetBytes()
                        > parameters.decompressedGuardBytes()) {
            throw new IllegalArgumentException("R1 smoke parameters exceed its bounded manifest");
        }
    }

    static int supportedMaximumSupplementalManifestRecords() {
        return MAXIMUM_SUPPLEMENTAL_MANIFEST_RECORDS;
    }

    static int supportedMaximumTargetDepth() {
        return MAXIMUM_SUPPORTED_TARGET_DEPTH;
    }

    static int supportedMaximumManifestCount() {
        return MAXIMUM_SUPPORTED_MANIFEST_RECORDS;
    }

    private static long readyRecordCount(P4E0ResearchParameters parameters) {
        return Math.round(parameters.relevantRecords() * parameters.readyRecordRatio());
    }

    private static void createSupplementalRecords(
            P4E0ResearchParameters parameters,
            Path playerdata,
            P4E0ResearchAttachmentFixtures.Fixture ready,
            P4E0ResearchAttachmentFixtures.Fixture preserved) throws IOException {
        var count = parameters.relevantRecords() - BASE_RELEVANT_RECORDS;
        for (var index = 0; index < count; index++) {
            var root = minimalCurrentPlayerdata(
                    uuid(parameters.seed(), 300 + index), 300 + index);
            root = switch (supplementalKind(parameters, index)) {
                case READY -> withAttachment(root, ready.serializedTag());
                case PRESERVED_RAW -> withAttachment(root, preserved.serializedTag());
                case ZERO_ROOT -> root;
            };
            writeCompressed(root, supplementalPath(playerdata, parameters.seed(), index));
        }
    }

    private static void createSupplementalIgnoredEntries(
            P4E0ResearchParameters parameters, Path playerdata) throws IOException {
        var populated = BASE_DIRECTORY_ENTRIES
                + parameters.relevantRecords() - BASE_RELEVANT_RECORDS;
        for (var index = populated; index < parameters.directoryEntries(); index++) {
            Files.writeString(
                    playerdata.resolve(String.format(
                            Locale.ROOT, "research-ignored-%08d.bin", index)),
                    "synthetic-only\n",
                    StandardCharsets.UTF_8);
        }
    }

    private static SupplementalKind supplementalKind(
            P4E0ResearchParameters parameters, int index) {
        var additionalReady = Math.toIntExact(readyRecordCount(parameters) - 1L);
        var additionalPreserved = parameters.preservedRawRecordCount() - 1;
        if (index < additionalReady) {
            return SupplementalKind.READY;
        }
        if (index < additionalReady + additionalPreserved) {
            return SupplementalKind.PRESERVED_RAW;
        }
        return SupplementalKind.ZERO_ROOT;
    }

    private static Path supplementalPath(Path playerdata, long seed, int index) {
        return playerdata.resolve(uuid(seed, 300 + index) + ".dat");
    }

    private static void createOptionalTargetFixtures(
            P4E0ResearchParameters parameters, Path auxiliary) throws IOException {
        if (parameters.decompressedTargetBytes() > 0) {
            var root = minimalCurrentPlayerdata(uuid(parameters.seed(), 700), 700);
            root.putByteArray("research_decompressed_target", new byte[0]);
            var base = countUnnamed(root);
            if (parameters.decompressedTargetBytes() < base) {
                throw new IllegalArgumentException("decompressed target is below framing cost");
            }
            var filler = Math.toIntExact(parameters.decompressedTargetBytes() - base);
            root.putByteArray("research_decompressed_target", deterministicBytes(filler));
            var observation = P4E0ResearchGzipAdapter.writeAndRead(
                    auxiliary.resolve("decompressed-target.dat"),
                    root,
                    P4E0ResearchGzipAdapter.Options.canonical(),
                    parameters.compressedGuardBytes(),
                    parameters.decompressedGuardBytes(),
                    parameters.nbtQuotaBytes());
            if (observation.decompressedRootBytes()
                    != parameters.decompressedTargetBytes()) {
                throw new IllegalStateException("decompressed target coordinate changed");
            }
        }
        if (parameters.compressedTargetBytes() > 0) {
            var root = minimalCurrentPlayerdata(uuid(parameters.seed(), 701), 701);
            var path = auxiliary.resolve("compressed-target.dat");
            var base = P4E0ResearchGzipAdapter.writeAndRead(
                    path,
                    root,
                    P4E0ResearchGzipAdapter.Options.canonical(),
                    parameters.compressedGuardBytes(),
                    parameters.decompressedGuardBytes(),
                    parameters.nbtQuotaBytes());
            if (parameters.compressedTargetBytes() <= base.physicalFileBytes() + 1L) {
                throw new IllegalArgumentException("compressed target lacks FNAME framing");
            }
            var fileNameBytes = Math.toIntExact(
                    parameters.compressedTargetBytes() - base.physicalFileBytes() - 1L);
            var observation = P4E0ResearchGzipAdapter.writeAndRead(
                    path,
                    root,
                    P4E0ResearchGzipAdapter.Options.fileNameTarget(fileNameBytes),
                    parameters.compressedGuardBytes(),
                    parameters.decompressedGuardBytes(),
                    parameters.nbtQuotaBytes());
            if (observation.physicalFileBytes() != parameters.compressedTargetBytes()) {
                throw new IllegalStateException("compressed target coordinate changed");
            }
        }
    }

    private static CompoundTag minimalCurrentPlayerdata(UUID playerId, int witness) {
        var root = NbtUtils.addCurrentDataVersion(new CompoundTag());
        root.putUUID("UUID", playerId);
        root.putInt("research_witness", witness);
        requireCurrentDataVersion(root);
        return root;
    }

    private static CompoundTag withAttachment(CompoundTag root, Tag attachment) {
        var attachments = new CompoundTag();
        attachments.put(ATTACHMENT_KEY, attachment.copy());
        root.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, attachments);
        return root;
    }

    private static CompoundTag unrelatedWholeNbt(
            UUID playerId,
            int compoundEntries,
            int listElements,
            int arrayElements) {
        var root = minimalCurrentPlayerdata(playerId, 4);
        root.putString("unrelated_string", "\0A\u0080\u0800-research");
        root.putByteArray("unrelated_bytes", deterministicBytes(arrayElements));
        root.putIntArray("unrelated_ints", new int[] {1, 2, 3, 5, 8, 13});
        root.putLongArray("unrelated_longs", new long[] {21L, 34L, 55L});
        var list = new ListTag();
        for (var index = 0; index < listElements; index++) {
            list.add(IntTag.valueOf(index));
        }
        root.put("unrelated_list", list);
        var compound = new CompoundTag();
        for (var index = 0; index < compoundEntries; index++) {
            compound.putInt(String.format(Locale.ROOT, "entry_%08d", index), index);
        }
        root.put("unrelated_compound", compound);
        var otherAttachments = new CompoundTag();
        otherAttachments.putString("other_mod:opaque", "synthetic-only");
        root.put("research_unrelated_attachments", otherAttachments);
        return root;
    }

    private static CompoundTag depthFixture(UUID playerId, int depth) {
        if (depth < 1) {
            throw new IllegalArgumentException("depth must include the root Compound");
        }
        var root = minimalCurrentPlayerdata(playerId, depth);
        var cursor = root;
        for (var level = 1; level < depth; level++) {
            var child = new CompoundTag();
            cursor.put("d", child);
            cursor = child;
        }
        cursor.putString("leaf", "synthetic");
        if (P4E0ResearchNbtMetrics.measure(root).maxContainerDepth() != depth) {
            throw new IllegalStateException("research depth walker changed");
        }
        return root;
    }

    private static void requireDepthBoundary(
            Path acceptedPath,
            int acceptedDepth,
            Path targetPath,
            int targetDepth,
            long quota) throws IOException {
        var accepted = NbtIo.readCompressed(
                acceptedPath, NbtAccounter.create(quota));
        if (P4E0ResearchNbtMetrics.measure(accepted).maxContainerDepth()
                != acceptedDepth) {
            throw new IllegalStateException("configured accepted depth did not round-trip");
        }
        if (targetDepth <= 512) {
            var target = NbtIo.readCompressed(targetPath, NbtAccounter.create(quota));
            if (P4E0ResearchNbtMetrics.measure(target).maxContainerDepth()
                    != targetDepth) {
                throw new IllegalStateException("configured target depth did not round-trip");
            }
            return;
        }
        requirePlatformDepthRejection(targetPath, quota);
    }

    private static void requirePlatformDepthRejection(Path targetPath, long quota)
            throws IOException {
        try {
            NbtIo.readCompressed(targetPath, NbtAccounter.create(quota));
            throw new IllegalStateException("over-platform target depth unexpectedly decoded");
        } catch (NbtAccounterException expected) {
            // Locked Minecraft depth enforcement is the intended negative control.
        }
    }

    /**
     * Selects one physical source for an R1 synthetic route. This observes research plumbing only;
     * it does not establish a P4-E primary/old truth policy.
     */
    static ResearchSourceSelection selectResearchSource(Path directory, UUID playerId) {
        var safeDirectory = Objects.requireNonNull(directory, "directory")
                .toAbsolutePath().normalize();
        var route = Objects.requireNonNull(playerId, "playerId").toString();
        var primary = safeDirectory.resolve(route + ".dat");
        var old = safeDirectory.resolve(route + ".dat_old");
        var primaryPresent = Files.isRegularFile(primary);
        var oldPresent = Files.isRegularFile(old);
        if (primaryPresent) {
            return new ResearchSourceSelection(
                    ResearchPhysicalSource.PRIMARY,
                    primary,
                    true,
                    oldPresent);
        }
        if (oldPresent) {
            return new ResearchSourceSelection(
                    ResearchPhysicalSource.OLD,
                    old,
                    false,
                    true);
        }
        throw new IllegalStateException("synthetic route has no primary or old source");
    }

    private static P4E0ResearchResult.DirectoryMetrics measureDirectory(Path directory)
            throws IOException {
        var observed = 0L;
        var primaries = 0L;
        var old = 0L;
        var ignored = 0L;
        var malformed = 0L;
        var metadataBytes = 0L;
        Set<String> records = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (var path : stream) {
                observed = Math.addExact(observed, 1L);
                var name = path.getFileName().toString();
                metadataBytes = Math.addExact(
                        metadataBytes, name.getBytes(StandardCharsets.UTF_8).length);
                if (PRIMARY.matcher(name).matches()) {
                    primaries = Math.addExact(primaries, 1L);
                    records.add(name.substring(0, 36));
                } else if (OLD.matcher(name).matches()) {
                    old = Math.addExact(old, 1L);
                    records.add(name.substring(0, 36));
                } else if (name.endsWith(".dat") || name.endsWith(".dat_old")) {
                    malformed = Math.addExact(malformed, 1L);
                } else {
                    ignored = Math.addExact(ignored, 1L);
                }
            }
        }
        return new P4E0ResearchResult.DirectoryMetrics(
                observed, primaries, old, records.size(), ignored, malformed, metadataBytes);
    }

    private static List<String> fixtureNames(long seed) {
        var id0 = uuid(seed, 0).toString();
        var id1 = uuid(seed, 1).toString();
        var id2 = uuid(seed, 2).toString();
        var id3 = uuid(seed, 3).toString();
        var id4 = uuid(seed, 4).toString();
        var id5 = uuid(seed, 5).toString();
        var id6 = uuid(seed, 6).toString();
        var id7 = uuid(seed, 7).toString();
        var id8 = uuid(seed, 8).toString();
        var id9 = uuid(seed, 9).toString();
        return List.of(
                id0 + ".dat",
                id0 + ".dat_old",
                id1 + ".dat",
                id2 + ".dat",
                id3 + ".dat",
                id4 + ".dat",
                id5 + ".dat",
                id7 + ".dat",
                "not-a-uuid.dat",
                id6.toUpperCase(Locale.ROOT) + ".dat",
                "notes.txt",
                "ignored.dat.bak",
                "unrelated.bin",
                id8 + ".dat_old",
                id9 + ".dat",
                "also-not-a-uuid.dat_old");
    }

    private static UUID uuid(long seed, int ordinal) {
        return new UUID(0x5034_4530_0000_0000L ^ seed,
                0x8000_0000_0000_0000L | ordinal & 0x7fff_ffff_ffff_ffffL);
    }

    private static byte[] deterministicBytes(int length) {
        var bytes = new byte[length];
        for (var index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (index * 31 ^ index >>> 3 ^ 0x5a);
        }
        return bytes;
    }

    private static void writeCompressed(CompoundTag root, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        NbtIo.writeCompressed(root, path);
    }

    static long countUnnamed(Tag root) throws IOException {
        var counter = new CountingOutputStream();
        try (var output = new DataOutputStream(counter)) {
            NbtIo.writeUnnamedTag(root, output);
        }
        return counter.count;
    }

    private static P4E0ResearchResult.WireMetrics perFileWire(
            P4E0ResearchGzipAdapter.Observation observation) {
        return new P4E0ResearchResult.WireMetrics(
                observation.physicalFileBytes(),
                observation.gzipHeaderBytes(),
                observation.compressedMemberBytes(),
                observation.decompressedRootBytes(),
                observation.rootFramingBytes(),
                observation.physicalFileBytes(),
                observation.decompressedRootBytes());
    }

    private static P4E0ResearchResult.WireMetrics perFileWire(
            P4E0ResearchGzipAdapter.WireObservation observation) {
        return new P4E0ResearchResult.WireMetrics(
                observation.physicalFileBytes(),
                observation.gzipHeaderBytes(),
                observation.compressedMemberBytes(),
                observation.decompressedRootBytes(),
                observation.rootFramingBytes(),
                observation.physicalFileBytes(),
                observation.decompressedRootBytes());
    }

    private static P4E0ResearchResult.AttachmentMetrics emptyAttachmentMetrics() {
        return new P4E0ResearchResult.AttachmentMetrics(0L, 0L, 0L, 0L, 0L);
    }

    private static void addAuxiliaryMeasurements(
            P4E0ResearchParameters parameters,
            Path root,
            List<P4E0ResearchResult.FixtureManifest> manifests,
            TreeMap<String, String> decodedChecksums,
            MeasurementTotals totals) throws IOException {
        var auxiliary = root.resolve("auxiliary");
        for (var depth : List.of(1, 64, 128, 256)) {
            addDecodedAuxiliary(
                    parameters,
                    root,
                    auxiliary.resolve("depth-" + depth + ".dat"),
                    P4E0ResearchCase.DEPTH_LADDER,
                    "aux-depth-" + depth,
                    "DECODED_DEPTH_" + depth,
                    manifests,
                    decodedChecksums,
                    totals);
        }

        var targetPath = auxiliary.resolve("depth-target-negative.dat");
        if (parameters.targetDepth() <= 512) {
            addDecodedAuxiliary(
                    parameters,
                    root,
                    targetPath,
                    P4E0ResearchCase.DEPTH_LADDER,
                    "aux-depth-configured-target",
                    "DECODED_CONFIGURED_DEPTH",
                    manifests,
                    decodedChecksums,
                    totals);
        } else {
            requirePlatformDepthRejection(
                    targetPath, parameters.nbtQuotaBytes());
            var wire = P4E0ResearchGzipAdapter.readWireDrain(
                    targetPath,
                    parameters.compressedGuardBytes(),
                    parameters.decompressedGuardBytes());
            var deterministic = depthFixture(
                    uuid(parameters.seed(), 6), parameters.targetDepth());
            requireCurrentDataVersion(deterministic);
            if (countUnnamed(deterministic) != wire.decompressedRootBytes()) {
                throw new IllegalStateException(
                        "depth target wire drain differs from deterministic tree");
            }
            var metrics = totals.observeWireOnly(wire, deterministic);
            manifests.add(new P4E0ResearchResult.FixtureManifest(
                    P4E0ResearchCase.DEPTH_LADDER,
                    "aux-depth-configured-target",
                    wire.physicalFileBytes(),
                    P4E0ResearchHashing.sha256(targetPath),
                    "STRICT_WIRE_DRAIN_PLATFORM_DEPTH_REJECTED",
                    metrics));
        }

        for (var name : List.of("determinism-a.dat", "determinism-b.dat")) {
            addDecodedAuxiliary(
                    parameters,
                    root,
                    auxiliary.resolve(name),
                    P4E0ResearchCase.GZIP_HEADER_LADDER,
                    "aux-" + name.substring(0, name.length() - 4),
                    "CANONICAL_DETERMINISTIC_MEMBER",
                    manifests,
                    decodedChecksums,
                    totals);
        }
        if (parameters.decompressedTargetBytes() > 0) {
            addDecodedAuxiliary(
                    parameters,
                    root,
                    auxiliary.resolve("decompressed-target.dat"),
                    P4E0ResearchCase.UNRELATED_WHOLE_NBT,
                    "aux-decompressed-target",
                    "CONFIGURED_DECOMPRESSED_TARGET",
                    manifests,
                    decodedChecksums,
                    totals);
        }
        if (parameters.compressedTargetBytes() > 0) {
            addDecodedAuxiliary(
                    parameters,
                    root,
                    auxiliary.resolve("compressed-target.dat"),
                    P4E0ResearchCase.GZIP_HEADER_LADDER,
                    "aux-compressed-target",
                    "CONFIGURED_COMPRESSED_TARGET",
                    manifests,
                    decodedChecksums,
                    totals);
        }
    }

    private static void addDecodedAuxiliary(
            P4E0ResearchParameters parameters,
            Path root,
            Path path,
            P4E0ResearchCase fixtureCase,
            String alias,
            String stateCode,
            List<P4E0ResearchResult.FixtureManifest> manifests,
            TreeMap<String, String> decodedChecksums,
            MeasurementTotals totals) throws IOException {
        var observation = P4E0ResearchGzipAdapter.read(
                path,
                parameters.compressedGuardBytes(),
                parameters.decompressedGuardBytes(),
                parameters.nbtQuotaBytes());
        var tag = observation.decodedRoot();
        requireCurrentDataVersion(tag);
        var measured = totals.observePlayerdata(observation, tag);
        if (measured.attachmentVariant().isPresent()) {
            throw new IllegalStateException(
                    "auxiliary playerdata unexpectedly admitted an Attachment");
        }
        manifests.add(new P4E0ResearchResult.FixtureManifest(
                fixtureCase,
                alias,
                observation.physicalFileBytes(),
                P4E0ResearchHashing.sha256(path),
                stateCode,
                measured.metrics()));
        decodedChecksums.put(
                portable(root.relativize(path)),
                P4E0ResearchHashing.semanticTagChecksum(tag));
    }

    private static void requireExpectedAttachment(
            int index,
            Optional<P4E0ResearchAttachmentFixtures.Variant> actual) {
        var expected = switch (index) {
            case 2 -> Optional.of(P4E0ResearchAttachmentFixtures.Variant.READY);
            case 3 -> Optional.of(P4E0ResearchAttachmentFixtures.Variant.PRESERVED_RAW);
            case 4 -> Optional.of(P4E0ResearchAttachmentFixtures.Variant.OVERSIZE_MARKER);
            default -> Optional.<P4E0ResearchAttachmentFixtures.Variant>empty();
        };
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "playerdata Attachment admission variant changed");
        }
    }

    private static void requireExpectedAttachment(
            SupplementalKind kind,
            Optional<P4E0ResearchAttachmentFixtures.Variant> actual) {
        var expected = switch (kind) {
            case READY -> Optional.of(P4E0ResearchAttachmentFixtures.Variant.READY);
            case PRESERVED_RAW -> Optional.of(
                    P4E0ResearchAttachmentFixtures.Variant.PRESERVED_RAW);
            case ZERO_ROOT -> Optional.<P4E0ResearchAttachmentFixtures.Variant>empty();
        };
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "supplemental Attachment admission variant changed");
        }
    }

    private static Optional<P4E0ResearchAttachmentFixtures.Fixture> admitAttachment(
            CompoundTag playerdata) {
        var outer = playerdata.get(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        if (outer == null) {
            return Optional.empty();
        }
        if (!(outer instanceof CompoundTag attachments)) {
            throw new IllegalStateException(
                    "synthetic playerdata Attachment container changed type");
        }
        var input = attachments.get(ATTACHMENT_KEY);
        if (input == null) {
            return Optional.empty();
        }
        return Optional.of(P4E0ResearchAttachmentFixtures.admit(
                input, RegistryAccess.EMPTY));
    }

    private record MeasuredFixture(
            P4E0ResearchResult.FixtureMetrics metrics,
            Optional<P4E0ResearchAttachmentFixtures.Variant> attachmentVariant) {
        MeasuredFixture {
            attachmentVariant = Objects.requireNonNull(
                    attachmentVariant, "attachmentVariant");
        }
    }

    /** Mutable checked accumulator whose inputs are only actual per-artifact observations. */
    private static final class MeasurementTotals {
        private final List<Object> retained = new ArrayList<>();
        private final Set<SkillReference> distinctRoots = new HashSet<>();
        private P4E0ResearchNbtMetrics nbt = P4E0ResearchNbtMetrics.zero();
        private SkillReference firstProjectedRoot;
        private long physicalBytes;
        private long decompressedBytes;
        private long attachmentBytes;
        private long attachmentAdmissions;
        private long draftCount;
        private long latestCount;
        private long equippedCount;
        private long projectedRootCount;
        private long readyAdmissions;
        private long preservedAdmissions;
        private long oversizeAdmissions;

        private MeasuredFixture observePlayerdata(
                P4E0ResearchGzipAdapter.Observation observation,
                CompoundTag playerdata) {
            var logical = P4E0ResearchNbtMetrics.measure(playerdata);
            addWire(
                    observation.physicalFileBytes(),
                    observation.decompressedRootBytes(),
                    logical,
                    playerdata);

            var admitted = admitAttachment(playerdata);
            if (admitted.isEmpty()) {
                return new MeasuredFixture(
                        new P4E0ResearchResult.FixtureMetrics(
                                perFileWire(observation),
                                logical,
                                emptyAttachmentMetrics(),
                                emptyRootMetrics()),
                        Optional.empty());
            }

            var fixture = admitted.orElseThrow();
            var roots = fixture.projectedRoots().orElseGet(List::of);
            retained.add(fixture);
            retained.add(roots);
            attachmentBytes = Math.addExact(
                    attachmentBytes, fixture.inputWriteAnyTagBytes());
            attachmentAdmissions = Math.addExact(attachmentAdmissions, 1L);
            draftCount = Math.addExact(draftCount, Math.max(0, fixture.draftCount()));
            latestCount = Math.addExact(latestCount, Math.max(0, fixture.latestCount()));
            equippedCount = Math.addExact(
                    equippedCount, Math.max(0, fixture.equippedCount()));
            projectedRootCount = Math.addExact(projectedRootCount, roots.size());
            distinctRoots.addAll(roots);
            if (firstProjectedRoot == null && !roots.isEmpty()) {
                firstProjectedRoot = roots.get(0);
            }
            switch (fixture.variant()) {
                case READY -> readyAdmissions = Math.addExact(readyAdmissions, 1L);
                case PRESERVED_RAW -> preservedAdmissions = Math.addExact(
                        preservedAdmissions, 1L);
                case OVERSIZE_MARKER -> oversizeAdmissions = Math.addExact(
                        oversizeAdmissions, 1L);
            }

            var attachment = new P4E0ResearchResult.AttachmentMetrics(
                    fixture.inputWriteAnyTagBytes(),
                    1L,
                    Math.max(0, fixture.draftCount()),
                    Math.max(0, fixture.latestCount()),
                    Math.max(0, fixture.equippedCount()));
            var rootMetrics = new P4E0ResearchResult.RootMetrics(
                    roots.size(),
                    roots.size(),
                    new HashSet<>(roots).size(),
                    "NOT_APPLICABLE",
                    "NOT_APPLICABLE");
            return new MeasuredFixture(
                    new P4E0ResearchResult.FixtureMetrics(
                            perFileWire(observation), logical, attachment, rootMetrics),
                    Optional.of(fixture.variant()));
        }

        private P4E0ResearchResult.FixtureMetrics observeWireOnly(
                P4E0ResearchGzipAdapter.WireObservation observation,
                CompoundTag deterministicTree) {
            var logical = P4E0ResearchNbtMetrics.measure(deterministicTree);
            addWire(
                    observation.physicalFileBytes(),
                    observation.decompressedRootBytes(),
                    logical,
                    deterministicTree);
            return new P4E0ResearchResult.FixtureMetrics(
                    perFileWire(observation),
                    logical,
                    emptyAttachmentMetrics(),
                    emptyRootMetrics());
        }

        private void addWire(
                long physical,
                long decompressed,
                P4E0ResearchNbtMetrics logical,
                Tag retainedTag) {
            physicalBytes = Math.addExact(physicalBytes, physical);
            decompressedBytes = Math.addExact(decompressedBytes, decompressed);
            nbt = nbt.plus(logical);
            retained.add(retainedTag);
        }

        private P4E0ResearchResult.AttachmentMetrics attachmentMetrics() {
            return new P4E0ResearchResult.AttachmentMetrics(
                    attachmentBytes,
                    attachmentAdmissions,
                    draftCount,
                    latestCount,
                    equippedCount);
        }

        private SkillReference firstProjectedRoot() {
            if (firstProjectedRoot == null) {
                throw new IllegalStateException("research workload has no projected root");
            }
            return firstProjectedRoot;
        }

        private long distinctProjectedRoots() {
            return distinctRoots.size();
        }

        private List<Object> retainedAtPeak() {
            return List.copyOf(retained);
        }

        private static P4E0ResearchResult.RootMetrics emptyRootMetrics() {
            return new P4E0ResearchResult.RootMetrics(
                    0L, 0L, 0L, "NOT_APPLICABLE", "NOT_APPLICABLE");
        }

        private long physicalBytes() {
            return physicalBytes;
        }

        private long decompressedBytes() {
            return decompressedBytes;
        }

        private P4E0ResearchNbtMetrics nbt() {
            return nbt;
        }

        private long projectedRootCount() {
            return projectedRootCount;
        }

        private long readyAdmissions() {
            return readyAdmissions;
        }

        private long preservedAdmissions() {
            return preservedAdmissions;
        }

        private long oversizeAdmissions() {
            return oversizeAdmissions;
        }
    }

    private static TreeHash hashSyntheticTree(Path root) throws IOException {
        var entries = new StringBuilder();
        var fileCount = 0L;
        var physicalBytes = 0L;
        try (var paths = Files.walk(root)) {
            var files = paths.filter(Files::isRegularFile)
                    .sorted((left, right) -> portable(root.relativize(left))
                            .compareTo(portable(root.relativize(right))))
                    .toList();
            for (var path : files) {
                var size = Files.size(path);
                fileCount = Math.addExact(fileCount, 1L);
                physicalBytes = Math.addExact(physicalBytes, size);
                entries.append(portable(root.relativize(path))).append('|')
                        .append(size).append('|')
                        .append(P4E0ResearchHashing.sha256(path)).append('\n');
            }
        }
        return new TreeHash(
                P4E0ResearchHashing.sha256(entries.toString()),
                fileCount,
                physicalBytes);
    }

    private static TreeHash hashDecodedChecksums(
            TreeMap<String, String> decodedChecksums) {
        var entries = new StringBuilder();
        for (var entry : decodedChecksums.entrySet()) {
            entries.append(entry.getKey()).append('|')
                    .append(entry.getValue()).append('\n');
        }
        return new TreeHash(
                P4E0ResearchHashing.sha256(entries.toString()),
                decodedChecksums.size(),
                0L);
    }

    private static String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static void requireCurrentDataVersion(CompoundTag root) {
        if (!(root.get("DataVersion") instanceof IntTag version)
                || version.getAsInt() != CURRENT_DATA_VERSION) {
            throw new IllegalStateException("synthetic fixture is not current DataVersion");
        }
    }

    private static P4E0ResearchCase caseForIndex(int index) {
        return switch (index) {
            case 0, 13, 14 -> P4E0ResearchCase.ZERO_ROOT_MINIMAL;
            case 2 -> P4E0ResearchCase.READY_ROOT_MAX;
            case 3 -> P4E0ResearchCase.PRESERVED_RAW_EXACT;
            case 4 -> P4E0ResearchCase.OVERSIZE_MARKER;
            case 5 -> P4E0ResearchCase.UNRELATED_WHOLE_NBT;
            case 6 -> P4E0ResearchCase.DEPTH_LADDER;
            case 7 -> P4E0ResearchCase.GZIP_HEADER_LADDER;
            default -> throw new IllegalArgumentException("fixture index is not measured");
        };
    }

    private static String stateForIndex(int index) {
        return switch (index) {
            case 0 -> "PRIMARY_PATH_SELECTED_ZERO_ROOT";
            case 13 -> "OLD_PATH_SELECTED_ZERO_ROOT";
            case 5, 6, 7, 14 -> "ZERO_ROOT";
            case 2 -> "READY_ROOT_MAX";
            case 3 -> "QUARANTINED_PRESERVED_RAW";
            case 4 -> "QUARANTINED_OVERSIZE_MARKER";
            default -> throw new IllegalArgumentException("fixture index is not measured");
        };
    }

    private static String aliasForIndex(int index) {
        return switch (index) {
            case 0 -> "selection-primary-over-arbitrary-old";
            case 2 -> "mixed-ready-root-max";
            case 3 -> "mixed-preserved-raw-exact";
            case 4 -> "mixed-oversize-marker";
            case 5 -> "mixed-unrelated-whole-nbt";
            case 6 -> "mixed-depth-accepted";
            case 7 -> "mixed-gzip-optional-fields";
            case 13 -> "selection-old-with-primary-missing";
            case 14 -> "mixed-zero-root-primary-only";
            default -> throw new IllegalArgumentException("fixture index has no alias");
        };
    }

    private record TreeHash(String hash, long fileCount, long physicalBytes) {
    }

    enum ResearchPhysicalSource {
        PRIMARY,
        OLD
    }

    record ResearchSourceSelection(
            ResearchPhysicalSource source,
            Path selectedPath,
            boolean primaryPresent,
            boolean oldPresent) {
        ResearchSourceSelection {
            Objects.requireNonNull(source, "source");
            selectedPath = Objects.requireNonNull(selectedPath, "selectedPath")
                    .toAbsolutePath().normalize();
        }
    }

    private record SelectedFixture(
            int fixtureIndex, Path path, String alias, String stateCode) {
        SelectedFixture {
            path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
            Objects.requireNonNull(alias, "alias");
            Objects.requireNonNull(stateCode, "stateCode");
        }
    }

    private enum SupplementalKind {
        READY,
        PRESERVED_RAW,
        ZERO_ROOT
    }

    private static Path requireFixtureRoot(Path root) {
        var normalized = root.toAbsolutePath().normalize();
        var marker = Path.of("build", "p4-e0-research");
        var text = normalized.toString().replace('\\', '/');
        if (!text.contains("/" + marker.toString().replace('\\', '/') + "/")) {
            throw new IllegalArgumentException("research fixture root is outside its build tree");
        }
        return normalized;
    }

    private static void clearResearchTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            var ordered = paths.sorted((left, right) -> right.compareTo(left)).toList();
            for (var path : ordered) {
                Files.delete(path);
            }
        }
    }

    private static final class CountingOutputStream extends OutputStream {
        private long count;

        @Override
        public void write(int value) {
            count = Math.addExact(count, 1L);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            if (offset < 0 || length < 0 || offset + length > bytes.length) {
                throw new IndexOutOfBoundsException();
            }
            count = Math.addExact(count, length);
        }
    }
}
