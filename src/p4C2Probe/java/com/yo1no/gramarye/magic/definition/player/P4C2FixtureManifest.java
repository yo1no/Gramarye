package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.definition.store.P4C2StoreProbe;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

/** Bounded phase hand-off for one pair of independent fixed-heap server processes. */
record P4C2FixtureManifest(
        P4C2ProbeCase probeCase,
        P4C2RunMode runMode,
        String playerUuidChecksum,
        String expectedState,
        long sourceAttachmentBytes,
        long expectedAttachmentBytes,
        String sourceAttachmentChecksum,
        String expectedAttachmentChecksum,
        String payloadChecksum,
        int expectedDrafts,
        int expectedLatest,
        int expectedEquipped,
        int expectedWitness,
        String expectedPlayerdataChecksum,
        long expectedPlayerdataBytes,
        int storeCarrierBytes,
        int storeHistoryCount,
        int storeRevisionCount,
        String storeSemanticChecksum,
        String storeSourcePrimaryChecksum,
        boolean storeRewriteExpected) {
    static final String FILE_NAME = "p4-c2-manifest.properties";
    static final String ATTACHMENT_KEY = "gramarye:player_skills";
    static final String WITNESS_KEY = "gramarye_p4_c2_witness";
    static final int PREPARED_WITNESS = 0x4C2B00;
    static final int INITIAL_FIRST_WITNESS = 0x4C2C01;
    static final int INITIAL_RESTART_WITNESS = 0x4C2C02;
    static final int FIRST_WITNESS = 0x4C2B01;
    static final int RESTART_WITNESS = 0x4C2B02;
    static final int UNAVAILABLE_COUNT = -1;
    private static final long MAX_MANIFEST_BYTES = 4_096;
    private static final String NONE = "none";
    private static final Set<String> KEYS = Set.of(
            "case", "phase", "player_uuid_checksum", "expected_state",
            "source_attachment_bytes", "expected_attachment_bytes",
            "source_attachment_checksum", "expected_attachment_checksum",
            "payload_checksum", "expected_drafts", "expected_latest",
            "expected_equipped", "expected_witness", "expected_playerdata_checksum",
            "expected_playerdata_bytes", "store_carrier_bytes", "store_history_count",
            "store_revision_count", "store_semantic_checksum", "store_source_primary_checksum",
            "store_rewrite_expected");

    P4C2FixtureManifest {
        java.util.Objects.requireNonNull(probeCase, "probeCase");
        java.util.Objects.requireNonNull(runMode, "runMode");
        if (runMode.probeCase() != probeCase
                || !playerUuidChecksum.equals(P4C2Hashing.uuidChecksum(probeCase.playerId()))
                || !expectedState.equals(probeCase.stateToken())) {
            throw new IllegalArgumentException("manifest route or state identity is invalid");
        }
        P4C2Hashing.requireSha256(playerUuidChecksum);
        P4C2Hashing.requireSha256(sourceAttachmentChecksum);
        P4C2Hashing.requireSha256(expectedAttachmentChecksum);
        P4C2Hashing.requireSha256(payloadChecksum);
        P4C2Hashing.requireSha256(expectedPlayerdataChecksum);
        if (sourceAttachmentBytes <= 0 || expectedAttachmentBytes <= 0
                || expectedPlayerdataBytes <= 0
                || expectedWitness != (runMode.restart() ? RESTART_WITNESS : FIRST_WITNESS)) {
            throw new IllegalArgumentException("manifest byte/witness facts are invalid");
        }
        var unavailable = probeCase != P4C2ProbeCase.READY;
        if (unavailable != (expectedDrafts == UNAVAILABLE_COUNT
                        && expectedLatest == UNAVAILABLE_COUNT
                        && expectedEquipped == UNAVAILABLE_COUNT)
                || !unavailable && (expectedDrafts < 0 || expectedLatest < 0
                        || expectedEquipped < 0)) {
            throw new IllegalArgumentException("manifest availability facts are invalid");
        }
        if (probeCase == P4C2ProbeCase.PRESERVED_RAW) {
            if (storeCarrierBytes < 63 * 1_024 * 1_024
                    || storeHistoryCount != 8 || storeRevisionCount != 64
                    || NONE.equals(storeSemanticChecksum)
                    || NONE.equals(storeSourcePrimaryChecksum)) {
                throw new IllegalArgumentException("preserved manifest lacks full Store facts");
            }
            P4C2Hashing.requireSha256(storeSemanticChecksum);
            P4C2Hashing.requireSha256(storeSourcePrimaryChecksum);
            if (storeRewriteExpected == runMode.restart()) {
                throw new IllegalArgumentException("Store rewrite phase is inverted");
            }
        } else if (storeCarrierBytes != 0 || storeHistoryCount != 0 || storeRevisionCount != 0
                || !NONE.equals(storeSemanticChecksum) || !NONE.equals(storeSourcePrimaryChecksum)
                || storeRewriteExpected) {
            throw new IllegalArgumentException("non-preserved world must not claim Store facts");
        }
    }

    static P4C2FixtureManifest first(
            P4C2ProbeCase probeCase,
            long sourceAttachmentBytes,
            long expectedAttachmentBytes,
            String sourceAttachmentChecksum,
            String expectedAttachmentChecksum,
            String payloadChecksum,
            int drafts,
            int latest,
            int equipped,
            String playerdataChecksum,
            long playerdataBytes,
            P4C2StoreProbe.ExpectedStore store) {
        var mode = switch (probeCase) {
            case READY -> P4C2RunMode.READY_FIRST;
            case PRESERVED_RAW -> P4C2RunMode.PRESERVED_RAW_FIRST;
            case OVERSIZE -> P4C2RunMode.OVERSIZE_FIRST;
        };
        return new P4C2FixtureManifest(
                probeCase, mode, P4C2Hashing.uuidChecksum(probeCase.playerId()),
                probeCase.stateToken(), sourceAttachmentBytes, expectedAttachmentBytes,
                sourceAttachmentChecksum, expectedAttachmentChecksum, payloadChecksum,
                drafts, latest, equipped, FIRST_WITNESS, playerdataChecksum,
                playerdataBytes,
                store == null ? 0 : store.storeBytes(),
                store == null ? 0 : store.histories(),
                store == null ? 0 : store.revisions(),
                store == null ? NONE : store.canonicalStoreChecksum(),
                store == null ? NONE : store.sourcePrimaryChecksum(),
                store != null);
    }

    P4C2StoreProbe.ExpectedStore expectedStore(Path worldRoot) {
        if (probeCase != P4C2ProbeCase.PRESERVED_RAW) {
            throw new IllegalStateException("this world has no full Store expectation");
        }
        try {
            var expected = P4C2StoreProbe.readExpected(worldRoot);
            if (expected.storeBytes() != storeCarrierBytes
                    || expected.histories() != storeHistoryCount
                    || expected.revisions() != storeRevisionCount
                    || !expected.canonicalStoreChecksum().equals(storeSemanticChecksum)
                    || !expected.sourcePrimaryChecksum().equals(storeSourcePrimaryChecksum)) {
                throw new AssertionError("C2 and B2 Store manifests disagree");
            }
            return expected;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read P4-B2 Store manifest", exception);
        }
    }

    P4C2FixtureManifest afterFirstRun(
            String playerdataChecksum, long playerdataBytes) {
        if (runMode.restart()) {
            throw new IllegalStateException("manifest is already in restart phase");
        }
        P4C2Hashing.requireSha256(playerdataChecksum);
        return new P4C2FixtureManifest(
                probeCase, runMode.restartMode(), playerUuidChecksum, expectedState,
                sourceAttachmentBytes, expectedAttachmentBytes,
                sourceAttachmentChecksum, expectedAttachmentChecksum, payloadChecksum,
                expectedDrafts, expectedLatest, expectedEquipped, RESTART_WITNESS,
                playerdataChecksum, playerdataBytes, storeCarrierBytes, storeHistoryCount,
                storeRevisionCount, storeSemanticChecksum, storeSourcePrimaryChecksum, false);
    }

    void write(Path worldRoot) throws IOException {
        var text = "case=" + probeCase.token() + "\n"
                + "phase=" + runMode.token() + "\n"
                + "player_uuid_checksum=" + playerUuidChecksum + "\n"
                + "expected_state=" + expectedState + "\n"
                + "source_attachment_bytes=" + sourceAttachmentBytes + "\n"
                + "expected_attachment_bytes=" + expectedAttachmentBytes + "\n"
                + "source_attachment_checksum=" + sourceAttachmentChecksum + "\n"
                + "expected_attachment_checksum=" + expectedAttachmentChecksum + "\n"
                + "payload_checksum=" + payloadChecksum + "\n"
                + "expected_drafts=" + expectedDrafts + "\n"
                + "expected_latest=" + expectedLatest + "\n"
                + "expected_equipped=" + expectedEquipped + "\n"
                + "expected_witness=" + expectedWitness + "\n"
                + "expected_playerdata_checksum=" + expectedPlayerdataChecksum + "\n"
                + "expected_playerdata_bytes=" + expectedPlayerdataBytes + "\n"
                + "store_carrier_bytes=" + storeCarrierBytes + "\n"
                + "store_history_count=" + storeHistoryCount + "\n"
                + "store_revision_count=" + storeRevisionCount + "\n"
                + "store_semantic_checksum=" + storeSemanticChecksum + "\n"
                + "store_source_primary_checksum=" + storeSourcePrimaryChecksum + "\n"
                + "store_rewrite_expected=" + storeRewriteExpected + "\n";
        var bytes = text.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > MAX_MANIFEST_BYTES) {
            throw new IllegalStateException("P4-C2 manifest exceeds its hard byte bound");
        }
        Files.write(worldRoot.resolve(FILE_NAME), bytes);
    }

    static P4C2FixtureManifest read(Path worldRoot) throws IOException {
        var path = worldRoot.resolve(FILE_NAME);
        if (Files.size(path) > MAX_MANIFEST_BYTES) {
            throw new IllegalArgumentException("P4-C2 manifest exceeds its hard byte bound");
        }
        var values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            values.load(reader);
        }
        if (!values.stringPropertyNames().equals(KEYS)) {
            throw new IllegalArgumentException("P4-C2 manifest fields are not exact");
        }
        return new P4C2FixtureManifest(
                P4C2ProbeCase.fromToken(required(values, "case")),
                P4C2RunMode.fromToken(required(values, "phase")),
                required(values, "player_uuid_checksum"),
                required(values, "expected_state"),
                parseLong(values, "source_attachment_bytes"),
                parseLong(values, "expected_attachment_bytes"),
                required(values, "source_attachment_checksum"),
                required(values, "expected_attachment_checksum"),
                required(values, "payload_checksum"),
                parseInt(values, "expected_drafts"),
                parseInt(values, "expected_latest"),
                parseInt(values, "expected_equipped"),
                parseInt(values, "expected_witness"),
                required(values, "expected_playerdata_checksum"),
                parseLong(values, "expected_playerdata_bytes"),
                parseInt(values, "store_carrier_bytes"),
                parseInt(values, "store_history_count"),
                parseInt(values, "store_revision_count"),
                required(values, "store_semantic_checksum"),
                required(values, "store_source_primary_checksum"),
                parseBoolean(values, "store_rewrite_expected"));
    }

    static Path worldRoot(Path gameDirectory) {
        return gameDirectory.resolve("world");
    }

    static Path playerdata(Path worldRoot, P4C2ProbeCase probeCase) {
        return worldRoot.resolve("playerdata").resolve(probeCase.playerId() + ".dat");
    }

    private static String required(Properties values, String key) {
        var value = values.getProperty(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("missing P4-C2 manifest field");
        }
        return value;
    }

    private static int parseInt(Properties values, String key) {
        try {
            return Integer.parseInt(required(values, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid P4-C2 manifest integer", exception);
        }
    }

    private static long parseLong(Properties values, String key) {
        try {
            return Long.parseLong(required(values, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid P4-C2 manifest long", exception);
        }
    }

    private static boolean parseBoolean(Properties values, String key) {
        return switch (required(values, key)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("invalid P4-C2 manifest boolean");
        };
    }
}
