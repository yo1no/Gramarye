package com.yo1no.gramarye.magic.definition.player;

/** One bounded success line emitted by each dedicated server process. */
record P4C2ProbeSummary(
        String probeCase,
        String phase,
        String state,
        long attachmentBytes,
        long playerdataCompressedBytes,
        String drafts,
        String latest,
        String equipped,
        long heapMaximum,
        long heapCommitted,
        long heapPeak,
        long heapPoolPeakSum,
        long elapsedMillis,
        String attachmentChecksum,
        int storeBytes,
        int storeHistories,
        int storeRevisions,
        String storeChecksum) {
    String line() {
        var line = "P4C2_PROBE_OK"
                + " case=" + probeCase
                + " phase=" + phase
                + " state=" + state
                + " attachment_bytes=" + attachmentBytes
                + " playerdata_compressed_bytes=" + playerdataCompressedBytes
                + " drafts=" + drafts
                + " latest=" + latest
                + " equipped=" + equipped
                + " heap_max=" + heapMaximum
                + " heap_committed=" + heapCommitted
                + " heap_peak=" + heapPeak
                + " heap_pool_peak_sum=" + heapPoolPeakSum
                + " elapsed_ms=" + elapsedMillis
                + " attachment_checksum=" + attachmentChecksum
                + " store_bytes=" + storeBytes
                + " store_histories=" + storeHistories
                + " store_revisions=" + storeRevisions
                + " store_checksum=" + storeChecksum;
        if (line.length() > 640) {
            throw new IllegalStateException("P4-C2 probe summary is unbounded");
        }
        return line;
    }
}
