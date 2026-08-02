package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded JSON result and heap diagnostics for a single synthetic research child. */
final class P4E0ResearchResult {
    static final int SCHEMA_VERSION = 0;
    static final int MAXIMUM_JSON_BYTES = 65_536;

    enum Classification {
        COMPLETED,
        REJECTED_BY_RESEARCH_GUARD,
        FIXTURE_INVALID,
        INSTRUMENTATION_FAILURE,
        CHILD_EXIT_FAILURE,
        TIMEOUT,
        OOME_EXIT
    }

    record FixtureManifest(
            P4E0ResearchCase fixtureCase,
            String artifactAlias,
            long physicalBytes,
            String hash,
            String stateCode,
            FixtureMetrics metrics) {
        FixtureManifest {
            Objects.requireNonNull(fixtureCase, "fixtureCase");
            requireBounded(artifactAlias, 80, "artifactAlias");
            requireHash(hash);
            requireBounded(stateCode, 80, "stateCode");
            if (physicalBytes < 0) {
                throw new IllegalArgumentException("negative fixture byte count");
            }
        }

        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("case", fixtureCase.name());
            json.addProperty("artifact_alias", artifactAlias);
            json.addProperty("physical_bytes", physicalBytes);
            json.addProperty("hash", hash);
            json.addProperty("state_code", stateCode);
            if (metrics != null) {
                json.add("metrics", metrics.toJson());
            }
            return json;
        }
    }

    /** Per-playerdata measurement vector; directory/envelope summary entries omit this object. */
    record FixtureMetrics(
            WireMetrics wire,
            P4E0ResearchNbtMetrics nbt,
            AttachmentMetrics attachment,
            RootMetrics roots) {
        FixtureMetrics {
            Objects.requireNonNull(wire, "wire");
            Objects.requireNonNull(nbt, "nbt");
            Objects.requireNonNull(attachment, "attachment");
            Objects.requireNonNull(roots, "roots");
        }

        JsonObject toJson() {
            var json = new JsonObject();
            json.add("wire_metrics", wire.toJson());
            json.add("nbt_metrics", nbt.toJson());
            json.add("attachment_metrics", attachment.toJson());
            json.add("root_metrics", roots.toJson());
            return json;
        }
    }

    record DirectoryMetrics(
            long directoryEntriesObserved,
            long canonicalPrimaryNames,
            long canonicalOldNames,
            long uniqueUuidRecords,
            long ignoredEntries,
            long relevantMalformedEntries,
            long metadataBytesEstimate) {
        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("directory_entries_observed", directoryEntriesObserved);
            json.addProperty("canonical_primary_names", canonicalPrimaryNames);
            json.addProperty("canonical_old_names", canonicalOldNames);
            json.addProperty("unique_uuid_records", uniqueUuidRecords);
            json.addProperty("ignored_entries", ignoredEntries);
            json.addProperty("relevant_malformed_entries", relevantMalformedEntries);
            json.addProperty("metadata_bytes_estimate", metadataBytesEstimate);
            return json;
        }
    }

    record WireMetrics(
            long physicalFileBytes,
            long gzipHeaderBytes,
            long compressedMemberBytes,
            long decompressedRootBytes,
            long rootFramingBytes,
            long compressedBytesTotal,
            long decompressedBytesTotal) {
        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("physical_file_bytes", physicalFileBytes);
            json.addProperty("gzip_header_bytes", gzipHeaderBytes);
            json.addProperty("compressed_member_bytes", compressedMemberBytes);
            json.addProperty("decompressed_root_bytes", decompressedRootBytes);
            json.addProperty("root_framing_bytes", rootFramingBytes);
            json.addProperty("compressed_bytes_total", compressedBytesTotal);
            json.addProperty("decompressed_bytes_total", decompressedBytesTotal);
            return json;
        }
    }

    record AttachmentMetrics(
            long attachmentWriteAnyTagBytes,
            long attachmentAdmissionCount,
            long draftCount,
            long latestCount,
            long equippedCount) {
        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("attachment_write_any_tag_bytes", attachmentWriteAnyTagBytes);
            json.addProperty("attachment_admission_count", attachmentAdmissionCount);
            json.addProperty("draft_count", draftCount);
            json.addProperty("latest_count", latestCount);
            json.addProperty("equipped_count", equippedCount);
            return json;
        }
    }

    record RootMetrics(
            long projectedRootCount,
            long rootClaimsRaw,
            long distinctRootReferences,
            String exactLimitClassification,
            String overLimitClassification) {
        RootMetrics {
            requireBounded(exactLimitClassification, 80, "exactLimitClassification");
            requireBounded(overLimitClassification, 80, "overLimitClassification");
        }

        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("projected_root_count", projectedRootCount);
            json.addProperty("root_claims_raw", rootClaimsRaw);
            json.addProperty("distinct_root_references", distinctRootReferences);
            json.addProperty("exact_limit_classification", exactLimitClassification);
            json.addProperty("over_limit_classification", overLimitClassification);
            return json;
        }
    }

    record StoreJournalMetrics(
            long storeBytes,
            long storeHistories,
            long storeRevisions,
            long journalBytes,
            long journalEntries,
            long carrierBytes,
            String envelopeCode) {
        StoreJournalMetrics {
            requireBounded(envelopeCode, 80, "envelopeCode");
        }

        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("store_bytes", storeBytes);
            json.addProperty("store_histories", storeHistories);
            json.addProperty("store_revisions", storeRevisions);
            json.addProperty("journal_bytes", journalBytes);
            json.addProperty("journal_entries", journalEntries);
            json.addProperty("carrier_bytes", carrierBytes);
            json.addProperty("envelope_code", envelopeCode);
            return json;
        }
    }

    record Integrity(
            String fixtureHash,
            long fixtureFileCount,
            String decodedHash,
            long decodedArtifactCount,
            String semanticChecksum) {
        Integrity(String fixtureHash, String decodedHash, String semanticChecksum) {
            this(fixtureHash, 0L, decodedHash, 0L, semanticChecksum);
        }

        Integrity {
            requireHash(fixtureHash);
            requireHash(decodedHash);
            requireHash(semanticChecksum);
            if (fixtureFileCount < 0 || decodedArtifactCount < 0) {
                throw new IllegalArgumentException("negative integrity artifact count");
            }
        }

        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("fixture_hash", fixtureHash);
            json.addProperty("fixture_file_count", fixtureFileCount);
            json.addProperty("decoded_hash", decodedHash);
            json.addProperty("decoded_artifact_count", decodedArtifactCount);
            json.addProperty("semantic_checksum", semanticChecksum);
            return json;
        }
    }

    record HeapMetrics(
            long xms,
            long xmx,
            long initialCommitted,
            long sampledPeakUsed,
            long heapPoolPeakSum,
            long gcCount,
            long gcTimeMillis) {
        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("xms", xms);
            json.addProperty("xmx", xmx);
            json.addProperty("initial_committed", initialCommitted);
            json.addProperty("sampled_peak_used", sampledPeakUsed);
            json.addProperty("heap_pool_peak_sum", heapPoolPeakSum);
            json.addProperty("gc_count", gcCount);
            json.addProperty("gc_time_millis", gcTimeMillis);
            return json;
        }
    }

    private final P4E0ResearchParameters parameters;
    private final List<FixtureManifest> fixtureManifest;
    private final long elapsedMillis;
    private final HeapMetrics heap;
    private final DirectoryMetrics directory;
    private final WireMetrics wire;
    private final P4E0ResearchNbtMetrics nbt;
    private final AttachmentMetrics attachment;
    private final RootMetrics roots;
    private final StoreJournalMetrics storeJournal;
    private final Integrity integrity;
    private final int processExitCode;
    private final Classification classification;
    private final String boundedFailureClass;

    P4E0ResearchResult(
            P4E0ResearchParameters parameters,
            List<FixtureManifest> fixtureManifest,
            long elapsedMillis,
            HeapMetrics heap,
            DirectoryMetrics directory,
            WireMetrics wire,
            P4E0ResearchNbtMetrics nbt,
            AttachmentMetrics attachment,
            RootMetrics roots,
            StoreJournalMetrics storeJournal,
            Integrity integrity,
            int processExitCode,
            Classification classification,
            String boundedFailureClass) {
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.fixtureManifest = List.copyOf(fixtureManifest);
        this.elapsedMillis = elapsedMillis;
        this.heap = Objects.requireNonNull(heap, "heap");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.wire = Objects.requireNonNull(wire, "wire");
        this.nbt = Objects.requireNonNull(nbt, "nbt");
        this.attachment = Objects.requireNonNull(attachment, "attachment");
        this.roots = Objects.requireNonNull(roots, "roots");
        this.storeJournal = Objects.requireNonNull(storeJournal, "storeJournal");
        this.integrity = Objects.requireNonNull(integrity, "integrity");
        this.processExitCode = processExitCode;
        this.classification = Objects.requireNonNull(classification, "classification");
        this.boundedFailureClass = boundedFailureClass == null
                ? "" : bounded(boundedFailureClass, 160);
        if (elapsedMillis < 0 || processExitCode < 0 || fixtureManifest.size() > 64) {
            throw new IllegalArgumentException("unbounded research result");
        }
    }

    String toBoundedJson() {
        var root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.addProperty("git_head", detectGitHead());
        root.addProperty("scenario", parameters.scenario().name());
        root.add("parameters", parameters.toJson());
        var fixtures = new JsonArray();
        fixtureManifest.forEach(fixture -> fixtures.add(fixture.toJson()));
        root.add("fixture_manifest", fixtures);

        var jvm = new JsonObject();
        jvm.addProperty("java_version", bounded(System.getProperty("java.version"), 80));
        jvm.addProperty("vm_name", bounded(System.getProperty("java.vm.name"), 120));
        root.add("jvm", jvm);
        var os = new JsonObject();
        os.addProperty("name", bounded(System.getProperty("os.name"), 80));
        os.addProperty("arch", bounded(System.getProperty("os.arch"), 80));
        root.add("os", os);

        var process = new JsonObject();
        process.addProperty("exit_code", processExitCode);
        process.addProperty("failure_class", boundedFailureClass);
        root.add("process_result", process);
        root.addProperty("elapsed_millis", elapsedMillis);
        root.add("heap", heap.toJson());
        root.add("directory_metrics", directory.toJson());
        root.add("wire_metrics", wire.toJson());
        root.add("nbt_metrics", nbt.toJson());
        root.add("attachment_metrics", attachment.toJson());
        root.add("root_metrics", roots.toJson());
        root.add("store_journal_metrics", storeJournal.toJson());
        root.add("integrity", integrity.toJson());
        root.addProperty("classification", classification.name());
        var text = root.toString();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_JSON_BYTES) {
            throw new IllegalStateException("research JSON exceeded its bounded schema");
        }
        return text;
    }

    void write(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().normalize().getParent());
        Files.writeString(path, toBoundedJson() + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    static final class HeapSampler implements AutoCloseable {
        private final List<MemoryPoolMXBean> pools;
        private final ScheduledExecutorService executor;
        private final AtomicLong sampledPeak;
        private final long xms;
        private final long xmx;
        private final long initialCommitted;
        private boolean closed;

        private HeapSampler() {
            pools = ManagementFactory.getMemoryPoolMXBeans().stream()
                    .filter(pool -> pool.getType() == MemoryType.HEAP)
                    .toList();
            if (pools.isEmpty()) {
                throw new IllegalStateException("no heap pools available");
            }
            var initialHeapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            xms = Math.max(0L, initialHeapUsage.getInit());
            xmx = Runtime.getRuntime().maxMemory();
            initialCommitted = initialHeapUsage.getCommitted();
            sampledPeak = new AtomicLong(currentUsed(pools));
            ThreadFactory factory = runnable -> {
                var thread = new Thread(runnable, "p4-e0-research-heap-sampler");
                thread.setDaemon(true);
                return thread;
            };
            executor = Executors.newSingleThreadScheduledExecutor(factory);
            executor.scheduleAtFixedRate(this::sample, 0L, 20L, TimeUnit.MILLISECONDS);
        }

        static HeapSampler start() {
            return new HeapSampler();
        }

        private void sample() {
            sampledPeak.accumulateAndGet(currentUsed(pools), Math::max);
        }

        HeapMetrics finish() {
            close();
            var peakSum = 0L;
            for (var pool : pools) {
                peakSum = Math.addExact(peakSum, pool.getPeakUsage().getUsed());
            }
            var gcCount = 0L;
            var gcTime = 0L;
            for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (collector.getCollectionCount() >= 0) {
                    gcCount = Math.addExact(gcCount, collector.getCollectionCount());
                }
                if (collector.getCollectionTime() >= 0) {
                    gcTime = Math.addExact(gcTime, collector.getCollectionTime());
                }
            }
            return new HeapMetrics(
                    xms,
                    xmx,
                    initialCommitted,
                    sampledPeak.get(),
                    peakSum,
                    gcCount,
                    gcTime);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            sample();
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(
                        Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("heap sampler did not stop");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("heap sampler stop was interrupted", exception);
            }
        }

        private static long currentUsed(List<MemoryPoolMXBean> pools) {
            var sum = 0L;
            for (var pool : pools) {
                sum = Math.addExact(sum, pool.getUsage().getUsed());
            }
            return sum;
        }
    }

    private static String bounded(String value, int maximum) {
        var safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }

    private static String detectGitHead() {
        var configured = System.getProperty("gramarye.p4e0.gitHead", "");
        if (configured.matches("[0-9a-f]{40}")) {
            return configured;
        }
        try {
            for (var candidate = Path.of("").toAbsolutePath().normalize();
                    candidate != null;
                    candidate = candidate.getParent()) {
                var git = candidate.resolve(".git");
                var headFile = git.resolve("HEAD");
                if (!Files.isRegularFile(headFile) || Files.size(headFile) > 256L) {
                    continue;
                }
                var head = Files.readString(headFile, StandardCharsets.US_ASCII).trim();
                if (head.matches("[0-9a-f]{40}")) {
                    return head;
                }
                if (head.startsWith("ref: ")) {
                    var relative = Path.of(head.substring(5));
                    if (relative.isAbsolute() || relative.normalize().startsWith("..")) {
                        break;
                    }
                    var reference = git.resolve(relative).normalize();
                    if (reference.startsWith(git)
                            && Files.isRegularFile(reference)
                            && Files.size(reference) <= 256L) {
                        var value = Files.readString(
                                reference, StandardCharsets.US_ASCII).trim();
                        if (value.matches("[0-9a-f]{40}")) {
                            return value;
                        }
                    }
                }
                break;
            }
        } catch (IOException ignored) {
            // The bounded machine value below represents unavailable repository metadata.
        }
        return "UNKNOWN";
    }

    private static void requireBounded(String value, int maximum, String label) {
        if (value == null || value.length() > maximum) {
            throw new IllegalArgumentException(label + " is not bounded");
        }
    }

    private static void requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid SHA-256 value");
        }
    }
}
