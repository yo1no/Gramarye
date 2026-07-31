package com.yo1no.gramarye.magic.definition.store;

import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.SharedConstants;

/** External fixture preparation and strict disk-verification entry point. */
public final class P4D3ProbeMain {
    private P4D3ProbeMain() {
    }

    public static void main(String[] arguments) throws Exception {
        SharedConstants.tryDetectVersion();
        if (arguments.length < 2) {
            throw new IllegalArgumentException("P4-D3 probe command and paths are required");
        }
        if ("prepare-worlds".equals(arguments[0])) {
            if (arguments.length != 9) {
                throw new IllegalArgumentException(
                        "P4-D3 prepare-worlds requires exactly eight directories");
            }
            var worlds = new ArrayList<Path>(8);
            for (var index = 1; index < arguments.length; index++) {
                worlds.add(Path.of(arguments[index]));
            }
            P4D3FixtureBuilder.prepareWorlds(worlds);
            System.out.println("P4D3_FIXTURE_OK"
                    + " store_bytes=" + P4D3StoreJournalFixture.STORE_BYTES
                    + " histories=" + P4D3StoreJournalFixture.HISTORY_COUNT
                    + " revisions=" + P4D3StoreJournalFixture.REVISION_COUNT
                    + " journal_current="
                    + P4D3StoreJournalFixture.CURRENT_JOURNAL_ENTRIES
                    + " journal_prospective="
                    + P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_ENTRIES
                    + " prospective_bytes="
                    + P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_BYTES);
            return;
        }
        if (arguments.length != 2 || !arguments[0].startsWith("verify-")) {
            throw new IllegalArgumentException("unknown P4-D3 probe command");
        }
        var mode = P4D3RunMode.fromToken(arguments[0].substring("verify-".length()));
        System.out.println(P4D3FileVerifier.verify(Path.of(arguments[1]), mode).line());
    }
}
