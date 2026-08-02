package com.yo1no.gramarye.magic.definition.research;

import java.io.IOException;
import java.util.Objects;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;

/** Dedicated GameTest-side owner for one explicit Matrix-F combined sampling window. */
final class P4E0ResearchCombinedCoordinator {
    private static final long MEBIBYTE = 1_048_576L;

    private P4E0ResearchCombinedCoordinator() {
    }

    static Sample run(
            MinecraftServer server,
            P4E0ResearchCombinedEnvelope.Profile profile) throws IOException {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(profile, "profile");
        if (!(server instanceof GameTestServer) || !server.isSameThread()) {
            throw new IllegalStateException(
                    "combined research must run on the dedicated GameTest server thread");
        }

        var started = System.nanoTime();
        try (var sampler = P4E0ResearchResult.HeapSampler.start()) {
            var envelope = P4E0ResearchCombinedEnvelope.prepare(profile);
            P4E0ResearchResult.HeapMetrics heap;
            try (var heldSave = envelope.beginHeldPlatformSave(server)) {
                envelope.retainAtPeak();
                heldSave.retainAtPeak();
                heap = sampler.finish();
                envelope.retainAtPeak();
                heldSave.retainAtPeak();
            }
            var expectedXmx = Math.multiplyExact((long) profile.heapMiB(), MEBIBYTE);
            if (heap.xms() != 512L * MEBIBYTE || heap.xmx() != expectedXmx) {
                throw new IllegalArgumentException(
                        "combined dedicated heap differs from its explicit profile");
            }
            envelope.retainAtPeak();
            return new Sample(
                    envelope.metrics(),
                    heap,
                    (System.nanoTime() - started) / 1_000_000L);
        }
    }

    record Sample(
            P4E0ResearchCombinedEnvelope.Metrics metrics,
            P4E0ResearchResult.HeapMetrics heap,
            long elapsedMillis) {
        Sample {
            Objects.requireNonNull(metrics, "metrics");
            Objects.requireNonNull(heap, "heap");
            if (elapsedMillis < 0) {
                throw new IllegalArgumentException(
                        "combined elapsed time is negative");
            }
        }
    }
}
