package com.yo1no.gramarye.magic.definition.research;

import com.yo1no.gramarye.magic.definition.player.P4E0ResearchAttachmentFixtures;
import com.yo1no.gramarye.magic.definition.store.P4E0ResearchCombinedStoreSession;
import com.yo1no.gramarye.magic.definition.store.P4E0ResearchGzipAdapter;
import com.yo1no.gramarye.magic.definition.store.P4E0ResearchRootWorkloads;
import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

/**
 * Research-only Matrix-F retained envelope. Every profile coordinate is caller-supplied and
 * exposed again through {@link Metrics}; this class does not choose a frontier or safety value.
 */
public final class P4E0ResearchCombinedEnvelope {
    private static final List<Integer> MATRIX_HEAPS_MIB =
            List.of(1_024, 1_280, 1_536, 1_792, 2_048);

    private final Profile profile;
    private final DirectorySnapshot directory;
    private final P4E0ResearchGzipAdapter.Observation selectedPlayerdata;
    private final P4E0ResearchAttachmentFixtures.Fixture attachment;
    private final P4E0ResearchCombinedStoreSession.Prepared store;
    private final P4E0ResearchRootWorkloads.Capture roots;
    private final Metrics metrics;

    private P4E0ResearchCombinedEnvelope(
            Profile profile,
            DirectorySnapshot directory,
            P4E0ResearchGzipAdapter.Observation selectedPlayerdata,
            P4E0ResearchAttachmentFixtures.Fixture attachment,
            P4E0ResearchCombinedStoreSession.Prepared store,
            P4E0ResearchRootWorkloads.Capture roots,
            Metrics metrics) {
        this.profile = profile;
        this.directory = directory;
        this.selectedPlayerdata = selectedPlayerdata;
        this.attachment = attachment;
        this.store = store;
        this.roots = roots;
        this.metrics = metrics;
    }

    /** Materializes every non-platform-copy object required by one explicit combined profile. */
    public static P4E0ResearchCombinedEnvelope prepare(Profile profile) throws IOException {
        Objects.requireNonNull(profile, "profile");
        var directory = DirectorySnapshot.capture(
                profile.directory(), profile.directoryEntries());
        var selectedHash = P4E0ResearchHashing.sha256(profile.selectedPlayerdata());
        if (!selectedHash.equals(profile.selectedPlayerdataSha256())) {
            throw new IOException("combined selected playerdata hash differs from profile");
        }
        var selected = P4E0ResearchGzipAdapter.read(
                profile.selectedPlayerdata(),
                profile.compressedGuardBytes(),
                profile.decompressedGuardBytes(),
                profile.nbtQuotaBytes());
        if (selected.physicalFileBytes() != profile.selectedPhysicalBytes()
                || selected.decompressedRootBytes()
                        != profile.selectedDecompressedBytes()) {
            throw new IOException(
                    "combined selected playerdata coordinate differs from profile");
        }

        var store = P4E0ResearchCombinedStoreSession.prepare();
        var attachment = P4E0ResearchAttachmentFixtures.readyRootMax(true);
        var playerRoots = attachment.projectedRoots().orElseThrow();
        var journalRoots = store.prospectiveJournalRoots();
        var roots = P4E0ResearchRootWorkloads.combinedPlayerAndJournalOverLimit(
                playerRoots, journalRoots);
        var rootMetrics = roots.metrics();
        if (rootMetrics.rawRootCount()
                        != P4E0ResearchRootWorkloads.OVER_LIMIT_ROOT_COUNT
                || rootMetrics.playerRootCount()
                        != P4E0ResearchAttachmentFixtures.READY_PROJECTED_ROOT_COUNT
                || rootMetrics.journalRootCount() != 4_096
                || rootMetrics.admission()
                        != P4E0ResearchRootWorkloads.Admission.OVER_LIMIT) {
            throw new AssertionError("combined raw-root attempt changed");
        }

        var metrics = new Metrics(
                profile,
                directory.entries().size(),
                directory.metadataNameBytes(),
                directory.referencedFileBytes(),
                selected.physicalFileBytes(),
                selected.gzipHeaderBytes(),
                selected.decompressedRootBytes(),
                selectedHash,
                attachment.draftCount(),
                attachment.latestCount(),
                attachment.equippedCount(),
                attachment.projectedRoots().orElseThrow().size(),
                rootMetrics,
                store.metrics());
        return new P4E0ResearchCombinedEnvelope(
                profile, directory, selected, attachment, store, roots, metrics);
    }

    public Metrics metrics() {
        return metrics;
    }

    /** Starts the actual DimensionDataStorage save whose platform copy is held by the IO latch. */
    public P4E0ResearchCombinedStoreSession.HeldSave beginHeldPlatformSave(
            MinecraftServer server) {
        return store.beginHeldSave(server);
    }

    /** Keeps every explicit Matrix-F object alive at the heap sampling point. */
    public void retainAtPeak() {
        Reference.reachabilityFence(profile);
        Reference.reachabilityFence(directory);
        Reference.reachabilityFence(selectedPlayerdata);
        attachment.retainAtPeak();
        store.retainAtPeak();
        roots.retainAtPeak();
    }

    public enum ProfileKind {
        BALANCED,
        DIRECTORY_HEAVY,
        SINGLE_FILE_HEAVY
    }

    /** Exact selected values for one profile/heap child; no value is inferred by this type. */
    public record Profile(
            ProfileKind kind,
            int heapMiB,
            Path directory,
            int directoryEntries,
            String directoryShape,
            Path selectedPlayerdata,
            String selectedFixtureId,
            String selectedFixtureShape,
            long selectedPhysicalBytes,
            long selectedDecompressedBytes,
            String selectedPlayerdataSha256,
            long compressedGuardBytes,
            long decompressedGuardBytes,
            long nbtQuotaBytes) {
        public Profile {
            Objects.requireNonNull(kind, "kind");
            directory = requireResearchPath(directory, "directory");
            selectedPlayerdata = requireResearchPath(
                    selectedPlayerdata, "selectedPlayerdata");
            directoryShape = boundedToken(directoryShape, "directoryShape");
            selectedFixtureId = boundedToken(selectedFixtureId, "selectedFixtureId");
            selectedFixtureShape = boundedToken(
                    selectedFixtureShape, "selectedFixtureShape");
            if (!MATRIX_HEAPS_MIB.contains(heapMiB)
                    || directoryEntries <= 0
                    || selectedPhysicalBytes <= 0
                    || selectedDecompressedBytes <= 0
                    || compressedGuardBytes < selectedPhysicalBytes
                    || decompressedGuardBytes < selectedDecompressedBytes
                    || nbtQuotaBytes < selectedDecompressedBytes
                    || !selectedPlayerdataSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "combined profile coordinate is invalid");
            }
        }

        private static Path requireResearchPath(Path path, String name) {
            var normalized = Objects.requireNonNull(path, name)
                    .toAbsolutePath().normalize();
            var portable = normalized.toString().replace('\\', '/');
            if (!portable.contains("/build/p4-e0-research/")) {
                throw new IllegalArgumentException(
                        name + " is outside the synthetic research build tree");
            }
            return normalized;
        }

        private static String boundedToken(String value, String name) {
            var token = Objects.requireNonNull(value, name);
            if (token.isEmpty()
                    || token.length() > 80
                    || !token.matches("[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException(name + " is not a bounded token");
            }
            return token;
        }
    }

    public record Metrics(
            Profile profile,
            int directoryEntriesRetained,
            long directoryMetadataNameBytes,
            long directoryReferencedFileBytes,
            long selectedPhysicalBytes,
            long selectedGzipHeaderBytes,
            long selectedDecompressedBytes,
            String selectedPlayerdataSha256,
            int attachmentDrafts,
            int attachmentLatestStates,
            int attachmentEquippedReferences,
            int attachmentProjectedRoots,
            P4E0ResearchRootWorkloads.Metrics rootMetrics,
            P4E0ResearchCombinedStoreSession.Metrics storeMetrics) {
        public Metrics {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(rootMetrics, "rootMetrics");
            Objects.requireNonNull(storeMetrics, "storeMetrics");
            if (directoryEntriesRetained != profile.directoryEntries()
                    || directoryMetadataNameBytes < 0
                    || directoryReferencedFileBytes < 0
                    || selectedPhysicalBytes != profile.selectedPhysicalBytes()
                    || selectedGzipHeaderBytes < 10
                    || selectedDecompressedBytes
                            != profile.selectedDecompressedBytes()
                    || !selectedPlayerdataSha256.equals(
                            profile.selectedPlayerdataSha256())
                    || attachmentDrafts <= 0
                    || attachmentLatestStates
                            != P4E0ResearchAttachmentFixtures.READY_LATEST_COUNT
                    || attachmentEquippedReferences
                            != P4E0ResearchAttachmentFixtures.READY_EQUIPPED_COUNT
                    || attachmentProjectedRoots
                            != P4E0ResearchAttachmentFixtures.READY_PROJECTED_ROOT_COUNT) {
                throw new IllegalArgumentException("combined metrics are inconsistent");
            }
        }
    }

    private record DirectoryEntryMetadata(
            String name,
            long size,
            long lastModifiedMillis,
            boolean regularFile,
            boolean symbolicLink) {
        private DirectoryEntryMetadata {
            Objects.requireNonNull(name, "name");
            if (name.isEmpty() || name.length() > 160 || size < 0
                    || lastModifiedMillis < 0) {
                throw new IllegalArgumentException(
                        "combined directory metadata is invalid");
            }
        }
    }

    private record DirectorySnapshot(
            List<DirectoryEntryMetadata> entries,
            long metadataNameBytes,
            long referencedFileBytes) {
        private DirectorySnapshot {
            entries = List.copyOf(entries);
            if (metadataNameBytes < 0 || referencedFileBytes < 0) {
                throw new IllegalArgumentException(
                        "combined directory totals are invalid");
            }
        }

        private static DirectorySnapshot capture(Path directory, int expectedEntries)
                throws IOException {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("combined directory profile is absent");
            }
            var entries = new ArrayList<DirectoryEntryMetadata>(expectedEntries);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (var path : stream) {
                    var attributes = Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    var name = path.getFileName().toString();
                    entries.add(new DirectoryEntryMetadata(
                            name,
                            attributes.size(),
                            attributes.lastModifiedTime().toMillis(),
                            attributes.isRegularFile(),
                            attributes.isSymbolicLink()));
                }
            }
            entries.sort(Comparator.comparing(DirectoryEntryMetadata::name));
            if (entries.size() != expectedEntries) {
                throw new IOException(
                        "combined directory entry count differs from profile");
            }
            var nameBytes = 0L;
            var fileBytes = 0L;
            for (var entry : entries) {
                nameBytes = Math.addExact(
                        nameBytes,
                        entry.name().getBytes(StandardCharsets.UTF_8).length);
                fileBytes = Math.addExact(fileBytes, entry.size());
            }
            return new DirectorySnapshot(entries, nameBytes, fileBytes);
        }
    }
}
