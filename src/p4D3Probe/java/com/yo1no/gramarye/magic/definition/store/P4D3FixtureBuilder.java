package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.player.P4D3PlayerProbe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Creates the eight isolated P4-D3 worlds from one exact deterministic full fixture. */
final class P4D3FixtureBuilder {
    private P4D3FixtureBuilder() {
    }

    static void prepareWorlds(List<Path> gameDirectories) throws IOException {
        if (gameDirectories.size() != P4D3ProbeCase.values().length) {
            throw new IllegalArgumentException("P4-D3 preparation requires exactly eight worlds");
        }
        var fixture = P4D3StoreJournalFixture.build();
        var cases = P4D3ProbeCase.values();
        for (var index = 0; index < cases.length; index++) {
            prepare(gameDirectories.get(index), cases[index], fixture);
        }
    }

    private static void prepare(
            Path gameDirectory,
            P4D3ProbeCase probeCase,
            P4D3StoreJournalFixture.Fixture fixture) throws IOException {
        var worldRoot = P4D3FileVerifier.worldRoot(gameDirectory);
        deleteTree(worldRoot);
        Files.createDirectories(worldRoot);

        var journal = switch (probeCase) {
            case J1 -> P4D3StoreJournalFixture.invalidTargetJournal();
            case COMBINED -> fixture.currentJournal();
            default -> P4D3StoreJournalFixture.singleJournal();
        };
        var encoded = probeCase == P4D3ProbeCase.COMBINED
                ? fixture.encodedCurrent()
                : P4D3StoreJournalFixture.requireEncoded(journal);
        P4D3StoreJournalFixture.writePrimary(
                worldRoot, fixture.carrier(), encoded,
                probeCase == P4D3ProbeCase.COMBINED);

        var selectedShape = switch (probeCase) {
            case D, E, G, J1 -> P4D3PlayerProbe.AttachmentShape.EXPECTED;
            case F, H -> P4D3PlayerProbe.AttachmentShape.TARGET;
            case I -> P4D3PlayerProbe.AttachmentShape.THIRD;
            case COMBINED -> P4D3PlayerProbe.AttachmentShape.COMBINED_SELECTED;
        };
        P4D3PlayerProbe.writePlayerdata(
                worldRoot, P4D3ProbeSupport.selectedPlayerId(), selectedShape);
        if (probeCase == P4D3ProbeCase.COMBINED) {
            P4D3PlayerProbe.writePlayerdata(
                    worldRoot,
                    P4D3ProbeSupport.submissionPlayerId(),
                    P4D3PlayerProbe.AttachmentShape.COMBINED_SUBMISSION);
        }

        var disk = P4D3FileVerifier.inspect(worldRoot, probeCase);
        new P4D3FixtureManifest(
                probeCase,
                "prepared",
                selectedShape.name(),
                P4D3Hashing.uuid(P4D3ProbeSupport.selectedPlayerId()),
                probeCase == P4D3ProbeCase.COMBINED
                        ? P4D3Hashing.uuid(P4D3ProbeSupport.submissionPlayerId())
                        : P4D3FixtureManifest.NONE,
                disk.primaryChecksum(), disk.primaryBytes(), disk.storeBytes(),
                disk.histories(), disk.revisions(), disk.storeChecksum(),
                disk.journalBytes(), disk.journalEntries(), disk.rootCount(),
                disk.journalChecksum(), disk.selectedAttachmentChecksum(),
                disk.selectedPlayerdataChecksum(), disk.selectedPlayerdataBytes(),
                disk.submissionAttachmentChecksum(), disk.submissionPlayerdataChecksum(),
                disk.submissionPlayerdataBytes(), "PREPARED", 0, 0, 0, 0, 0)
                .write(worldRoot);
    }

    private static void deleteTree(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        try (var paths = Files.walk(target)) {
            var ordered = paths.sorted(Comparator.reverseOrder()).toList();
            for (var path : ordered) {
                Files.delete(path);
            }
        }
    }
}
