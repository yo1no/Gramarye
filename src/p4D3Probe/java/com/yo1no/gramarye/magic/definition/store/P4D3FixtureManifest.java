package com.yo1no.gramarye.magic.definition.store;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.Set;

/** Bounded crash/restart hand-off containing facts and hashes, never fixture payloads. */
record P4D3FixtureManifest(
        P4D3ProbeCase probeCase,
        String phase,
        String expectedStateCode,
        String selectedPlayerChecksum,
        String submissionPlayerChecksum,
        String primaryChecksum,
        long primaryBytes,
        int storeBytes,
        int histories,
        int revisions,
        String storeChecksum,
        int journalBytes,
        int journalEntries,
        int rootCount,
        String journalChecksum,
        String selectedAttachmentChecksum,
        String selectedPlayerdataChecksum,
        long selectedPlayerdataBytes,
        String submissionAttachmentChecksum,
        String submissionPlayerdataChecksum,
        long submissionPlayerdataBytes,
        String outcomeCode,
        long heapMax,
        long initialCommitted,
        long sampledPeak,
        long poolPeakSum,
        long elapsedMillis) {
    static final String FILE_NAME = "p4-d3-manifest.properties";
    static final String NONE = "none";
    static final long MAX_BYTES = 4_096;
    private static final Set<String> KEYS = Set.of(
            "case", "phase", "expected_state_code", "selected_player_checksum",
            "submission_player_checksum", "primary_checksum", "primary_bytes",
            "store_bytes", "histories", "revisions", "store_checksum",
            "journal_bytes", "journal_entries", "root_count", "journal_checksum",
            "selected_attachment_checksum", "selected_playerdata_checksum",
            "selected_playerdata_bytes", "submission_attachment_checksum",
            "submission_playerdata_checksum", "submission_playerdata_bytes",
            "outcome_code", "heap_max", "initial_committed", "sampled_peak",
            "pool_peak_sum", "elapsed_millis");

    P4D3FixtureManifest {
        java.util.Objects.requireNonNull(probeCase, "probeCase");
        requireToken(phase);
        requireToken(expectedStateCode);
        requireToken(outcomeCode);
        P4D3Hashing.requireSha256(selectedPlayerChecksum);
        requireOptionalChecksum(submissionPlayerChecksum);
        P4D3Hashing.requireSha256(primaryChecksum);
        P4D3Hashing.requireSha256(storeChecksum);
        P4D3Hashing.requireSha256(journalChecksum);
        P4D3Hashing.requireSha256(selectedAttachmentChecksum);
        P4D3Hashing.requireSha256(selectedPlayerdataChecksum);
        requireOptionalChecksum(submissionAttachmentChecksum);
        requireOptionalChecksum(submissionPlayerdataChecksum);
        if (primaryBytes <= 0 || storeBytes <= 0 || histories <= 0 || revisions <= 0
                || journalBytes < 0 || journalEntries < 0 || rootCount != journalEntries
                || selectedPlayerdataBytes <= 0 || submissionPlayerdataBytes < 0
                || heapMax < 0 || initialCommitted < 0 || sampledPeak < 0
                || poolPeakSum < 0 || elapsedMillis < 0) {
            throw new IllegalArgumentException("P4-D3 manifest numeric facts are invalid");
        }
        if ((journalBytes == 0) != (journalEntries == 0)) {
            throw new IllegalArgumentException("P4-D3 empty journal facts are inconsistent");
        }
        var combined = probeCase == P4D3ProbeCase.COMBINED;
        if (combined != (!NONE.equals(submissionPlayerChecksum)
                && !NONE.equals(submissionAttachmentChecksum)
                && !NONE.equals(submissionPlayerdataChecksum)
                && submissionPlayerdataBytes > 0)) {
            throw new IllegalArgumentException("P4-D3 submission-player facts are inconsistent");
        }
    }

    P4D3FixtureManifest withPhaseAndDisk(
            String nextPhase,
            String nextState,
            DiskFacts disk,
            String nextOutcome,
            long nextHeapMax,
            long nextInitialCommitted,
            long nextSampledPeak,
            long nextPoolPeakSum,
            long nextElapsedMillis) {
        return new P4D3FixtureManifest(
                probeCase, nextPhase, nextState, selectedPlayerChecksum,
                submissionPlayerChecksum, disk.primaryChecksum(), disk.primaryBytes(),
                disk.storeBytes(), disk.histories(), disk.revisions(), disk.storeChecksum(),
                disk.journalBytes(), disk.journalEntries(), disk.rootCount(),
                disk.journalChecksum(), disk.selectedAttachmentChecksum(),
                disk.selectedPlayerdataChecksum(), disk.selectedPlayerdataBytes(),
                disk.submissionAttachmentChecksum(), disk.submissionPlayerdataChecksum(),
                disk.submissionPlayerdataBytes(), nextOutcome, nextHeapMax,
                nextInitialCommitted, nextSampledPeak, nextPoolPeakSum, nextElapsedMillis);
    }

    void write(Path worldRoot) throws IOException {
        var text = "case=" + probeCase.token() + '\n'
                + "phase=" + phase + '\n'
                + "expected_state_code=" + expectedStateCode + '\n'
                + "selected_player_checksum=" + selectedPlayerChecksum + '\n'
                + "submission_player_checksum=" + submissionPlayerChecksum + '\n'
                + "primary_checksum=" + primaryChecksum + '\n'
                + "primary_bytes=" + primaryBytes + '\n'
                + "store_bytes=" + storeBytes + '\n'
                + "histories=" + histories + '\n'
                + "revisions=" + revisions + '\n'
                + "store_checksum=" + storeChecksum + '\n'
                + "journal_bytes=" + journalBytes + '\n'
                + "journal_entries=" + journalEntries + '\n'
                + "root_count=" + rootCount + '\n'
                + "journal_checksum=" + journalChecksum + '\n'
                + "selected_attachment_checksum=" + selectedAttachmentChecksum + '\n'
                + "selected_playerdata_checksum=" + selectedPlayerdataChecksum + '\n'
                + "selected_playerdata_bytes=" + selectedPlayerdataBytes + '\n'
                + "submission_attachment_checksum=" + submissionAttachmentChecksum + '\n'
                + "submission_playerdata_checksum=" + submissionPlayerdataChecksum + '\n'
                + "submission_playerdata_bytes=" + submissionPlayerdataBytes + '\n'
                + "outcome_code=" + outcomeCode + '\n'
                + "heap_max=" + heapMax + '\n'
                + "initial_committed=" + initialCommitted + '\n'
                + "sampled_peak=" + sampledPeak + '\n'
                + "pool_peak_sum=" + poolPeakSum + '\n'
                + "elapsed_millis=" + elapsedMillis + '\n';
        var bytes = text.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > MAX_BYTES) {
            throw new IllegalStateException("P4-D3 manifest exceeds 4 KiB");
        }
        Files.createDirectories(worldRoot);
        try (var channel = FileChannel.open(
                worldRoot.resolve(FILE_NAME),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            var source = ByteBuffer.wrap(bytes);
            while (source.hasRemaining()) {
                channel.write(source);
            }
            channel.force(true);
        }
    }

    static P4D3FixtureManifest read(Path worldRoot) throws IOException {
        var path = worldRoot.resolve(FILE_NAME);
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) {
            throw new IllegalArgumentException("P4-D3 manifest is absent or oversized");
        }
        var values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            values.load(reader);
        }
        if (!values.stringPropertyNames().equals(KEYS)) {
            throw new IllegalArgumentException("P4-D3 manifest fields are not exact");
        }
        return new P4D3FixtureManifest(
                P4D3ProbeCase.fromToken(required(values, "case")),
                required(values, "phase"), required(values, "expected_state_code"),
                required(values, "selected_player_checksum"),
                required(values, "submission_player_checksum"),
                required(values, "primary_checksum"), parseLong(values, "primary_bytes"),
                parseInt(values, "store_bytes"), parseInt(values, "histories"),
                parseInt(values, "revisions"), required(values, "store_checksum"),
                parseInt(values, "journal_bytes"), parseInt(values, "journal_entries"),
                parseInt(values, "root_count"), required(values, "journal_checksum"),
                required(values, "selected_attachment_checksum"),
                required(values, "selected_playerdata_checksum"),
                parseLong(values, "selected_playerdata_bytes"),
                required(values, "submission_attachment_checksum"),
                required(values, "submission_playerdata_checksum"),
                parseLong(values, "submission_playerdata_bytes"),
                required(values, "outcome_code"), parseLong(values, "heap_max"),
                parseLong(values, "initial_committed"), parseLong(values, "sampled_peak"),
                parseLong(values, "pool_peak_sum"), parseLong(values, "elapsed_millis"));
    }

    private static String required(Properties values, String key) {
        var value = values.getProperty(key);
        if (value == null || value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException("P4-D3 manifest value is missing or unbounded");
        }
        return value;
    }

    private static int parseInt(Properties values, String key) {
        try {
            return Integer.parseInt(required(values, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("P4-D3 manifest integer is malformed");
        }
    }

    private static long parseLong(Properties values, String key) {
        try {
            return Long.parseLong(required(values, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("P4-D3 manifest long is malformed");
        }
    }

    private static void requireOptionalChecksum(String value) {
        if (!NONE.equals(value)) {
            P4D3Hashing.requireSha256(value);
        }
    }

    private static void requireToken(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("P4-D3 manifest token is invalid");
        }
    }

    record DiskFacts(
            String primaryChecksum,
            long primaryBytes,
            int storeBytes,
            int histories,
            int revisions,
            String storeChecksum,
            int journalBytes,
            int journalEntries,
            int rootCount,
            String journalChecksum,
            String selectedAttachmentChecksum,
            String selectedPlayerdataChecksum,
            long selectedPlayerdataBytes,
            String submissionAttachmentChecksum,
            String submissionPlayerdataChecksum,
            long submissionPlayerdataBytes) {
    }
}
