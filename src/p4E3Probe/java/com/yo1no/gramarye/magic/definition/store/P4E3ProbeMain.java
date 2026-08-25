package com.yo1no.gramarye.magic.definition.store;

import java.nio.file.Path;
import net.minecraft.SharedConstants;

/** External P4-E3 fixture preparation and first/restart verification entry point. */
public final class P4E3ProbeMain {
    private P4E3ProbeMain() {
    }

    public static void main(String[] arguments) throws Exception {
        SharedConstants.tryDetectVersion();
        if (arguments.length != 3) {
            throw new IllegalArgumentException(
                    "usage: prepare-fixture|verify-first|verify-restart <gameDir> <reportDir>");
        }
        var gameDirectory = Path.of(arguments[1]);
        var reportRoot = Path.of(arguments[2]);
        switch (arguments[0]) {
            case "prepare-fixture" -> {
                var manifest = P4E3FixtureBuilder.prepare(gameDirectory, reportRoot);
                System.out.println("P4_E3_FIXTURE_PREPARED"
                        + " directory_entries=" + manifest.vector().directoryEntries()
                        + " relevant_records=" + manifest.vector().relevantRecords()
                        + " raw_roots=" + manifest.vector().rawRootClaims()
                        + " histories=" + manifest.storeHistories()
                        + " revisions=" + manifest.storeRevisions()
                        + " journal_entries=" + manifest.journalEntries()
                        + " journal_bytes=" + manifest.journalBytes());
            }
            case "verify-first" -> System.out.println(P4E3FileVerifier.verify(
                    gameDirectory, reportRoot, P4E3FileVerifier.Mode.FIRST).line());
            case "verify-restart" -> System.out.println(P4E3FileVerifier.verify(
                    gameDirectory, reportRoot, P4E3FileVerifier.Mode.RESTART).line());
            default -> throw new IllegalArgumentException("unknown P4-E3 probe command");
        }
    }
}
