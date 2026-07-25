package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;
import java.util.regex.Pattern;

/** One bounded success line consumed by local and remote P4-A3 memory gates. */
record P4A3ProbeSummary(
        String workload,
        int blobBytes,
        int histories,
        int revisions,
        long heapMax,
        long heapCommitted,
        long heapPeak,
        long heapPoolPeakSum,
        long elapsedMillis,
        String checksum) {
    private static final int MAX_LINE_LENGTH = 384;
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9-]+");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{16}");

    P4A3ProbeSummary {
        Objects.requireNonNull(workload, "workload");
        Objects.requireNonNull(checksum, "checksum");
        if (!TOKEN.matcher(workload).matches()) {
            throw new IllegalArgumentException("workload is not a bounded token");
        }
        if (!CHECKSUM.matcher(checksum).matches()) {
            throw new IllegalArgumentException("checksum must be 16 lowercase hex characters");
        }
        if (blobBytes <= 0 || histories <= 0 || revisions <= 0
                || heapMax <= 0 || heapCommitted < 0 || heapPeak < 0
                || heapPoolPeakSum < 0
                || elapsedMillis < 0) {
            throw new IllegalArgumentException("probe summary values are outside valid ranges");
        }
    }

    String line() {
        var line = "P4A3_PROBE_OK"
                + " workload=" + workload
                + " blob_bytes=" + blobBytes
                + " histories=" + histories
                + " revisions=" + revisions
                + " heap_max=" + heapMax
                + " heap_committed=" + heapCommitted
                + " heap_peak=" + heapPeak
                + " heap_pool_peak_sum=" + heapPoolPeakSum
                + " elapsed_ms=" + elapsedMillis
                + " checksum=" + checksum;
        if (line.length() > MAX_LINE_LENGTH) {
            throw new IllegalStateException("probe summary exceeds its hard length bound");
        }
        return line;
    }
}
