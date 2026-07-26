package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;
import java.util.regex.Pattern;

/** One bounded success line emitted by each fixed-heap server process. */
record P4B2ProbeSummary(
        String phase,
        int storeBytes,
        long compressedBytes,
        int histories,
        int revisions,
        long heapMaximum,
        long heapCommitted,
        long heapSampledPeak,
        long heapPoolPeakSum,
        long elapsedMillis,
        String checksum) {
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9-]+");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{16}");

    P4B2ProbeSummary {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(checksum, "checksum");
        if (!TOKEN.matcher(phase).matches() || !CHECKSUM.matcher(checksum).matches()
                || storeBytes < 0 || compressedBytes <= 0 || histories < 0 || revisions < 0
                || heapMaximum <= 0 || heapCommitted < 0 || heapSampledPeak < 0
                || heapPoolPeakSum < 0 || elapsedMillis < 0) {
            throw new IllegalArgumentException("P4-B2 summary value is outside its bound");
        }
    }

    String line() {
        var line = "P4B2_PROBE_OK"
                + " phase=" + phase
                + " store_bytes=" + storeBytes
                + " compressed_bytes=" + compressedBytes
                + " histories=" + histories
                + " revisions=" + revisions
                + " heap_max=" + heapMaximum
                + " heap_committed=" + heapCommitted
                + " heap_peak=" + heapSampledPeak
                + " heap_pool_peak_sum=" + heapPoolPeakSum
                + " elapsed_ms=" + elapsedMillis
                + " checksum=" + checksum;
        if (line.length() > 384) {
            throw new IllegalStateException("P4-B2 summary exceeds its hard length bound");
        }
        return line;
    }
}
