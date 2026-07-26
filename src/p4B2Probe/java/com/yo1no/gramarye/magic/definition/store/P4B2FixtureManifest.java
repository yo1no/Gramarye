package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

/** Bounded test-only facts used to coordinate two independent server processes. */
record P4B2FixtureManifest(
        P4B2ProbeCase fixtureCase,
        P4B2RunMode runMode,
        String sourcePrimarySha256,
        String expectedPrimarySha256,
        String sourceStoreSha256,
        String canonicalStoreSha256,
        long sourcePrimaryBytes,
        long sourceFnameBytes,
        long expectedPrimaryBytes,
        long expectedPrimaryLastModifiedMillis,
        int expectedStoreBytes,
        int expectedHistories,
        int expectedRevisions,
        String expectedOldSha256,
        long expectedOldBytes) {
    static final String MANIFEST_FILE_NAME = "p4-b2-manifest.properties";
    static final String PRIMARY_FILE_NAME = "gramarye_skill_definitions.dat";
    static final String OLD_FILE_NAME = PRIMARY_FILE_NAME + "_old";

    private static final long MAX_MANIFEST_BYTES = 4_096;
    private static final String NONE = "none";
    private static final Set<String> KEYS = Set.of(
            "case",
            "phase",
            "source_primary_sha256",
            "expected_primary_sha256",
            "source_store_sha256",
            "canonical_store_sha256",
            "source_primary_bytes",
            "source_fname_bytes",
            "expected_primary_bytes",
            "expected_primary_last_modified_millis",
            "expected_store_bytes",
            "expected_histories",
            "expected_revisions",
            "expected_old_sha256",
            "expected_old_bytes");

    P4B2FixtureManifest {
        java.util.Objects.requireNonNull(fixtureCase, "fixtureCase");
        java.util.Objects.requireNonNull(runMode, "runMode");
        if (runMode.fixtureCase() != fixtureCase) {
            throw new IllegalArgumentException("manifest case and run mode do not match");
        }
        P4B2Hashing.requireSha256(sourcePrimarySha256);
        P4B2Hashing.requireSha256(expectedPrimarySha256);
        requireOptionalChecksum(sourceStoreSha256);
        requireOptionalChecksum(canonicalStoreSha256);
        requireOptionalChecksum(expectedOldSha256);
        if (sourcePrimaryBytes <= 0 || sourceFnameBytes < 0 || expectedPrimaryBytes <= 0
                || expectedPrimaryLastModifiedMillis < 0
                || expectedStoreBytes < 0 || expectedHistories < 0
                || expectedRevisions < 0 || expectedOldBytes < 0) {
            throw new IllegalArgumentException("manifest numeric value is outside its bound");
        }
        if (fixtureCase.fullSize()) {
            if (expectedStoreBytes <= 0 || expectedHistories <= 0 || expectedRevisions <= 0
                    || NONE.equals(sourceStoreSha256)
                    || NONE.equals(canonicalStoreSha256)
                    || !NONE.equals(expectedOldSha256)
                    || expectedOldBytes != 0) {
                throw new IllegalArgumentException("full manifest shape is incomplete");
            }
            if ((fixtureCase == P4B2ProbeCase.FULL && sourceFnameBytes != 0)
                    || (fixtureCase == P4B2ProbeCase.HOSTILE_FNAME
                            && (sourceFnameBytes <= 0
                                    || sourcePrimaryBytes
                                            != MagicSafetyCeilings
                                                    .MAX_SKILL_SAVED_DATA_FILE_BYTES))) {
                throw new IllegalArgumentException("full manifest FNAME shape is incomplete");
            }
        } else if (expectedStoreBytes != 0 || expectedHistories != 0
                || expectedRevisions != 0 || !NONE.equals(sourceStoreSha256)
                || !NONE.equals(canonicalStoreSha256)
                || NONE.equals(expectedOldSha256) || expectedOldBytes <= 0
                || sourceFnameBytes != 0) {
            throw new IllegalArgumentException("invalid manifest must not claim Store facts");
        }
    }

    static P4B2FixtureManifest full(
            String sourcePrimarySha256,
            String sourceStoreSha256,
            String canonicalStoreSha256,
            long primaryBytes,
            long lastModifiedMillis,
            int storeBytes,
            int histories,
            int revisions) {
        return new P4B2FixtureManifest(
                P4B2ProbeCase.FULL,
                P4B2RunMode.FULL_FIRST,
                sourcePrimarySha256,
                sourcePrimarySha256,
                sourceStoreSha256,
                canonicalStoreSha256,
                primaryBytes,
                0,
                primaryBytes,
                lastModifiedMillis,
                storeBytes,
                histories,
                revisions,
                NONE,
                0);
    }

    static P4B2FixtureManifest hostileFname(
            String sourcePrimarySha256,
            String sourceStoreSha256,
            String canonicalStoreSha256,
            long primaryBytes,
            long fnameBytes,
            long lastModifiedMillis,
            int storeBytes,
            int histories,
            int revisions) {
        return new P4B2FixtureManifest(
                P4B2ProbeCase.HOSTILE_FNAME,
                P4B2RunMode.HOSTILE_FNAME_FIRST,
                sourcePrimarySha256,
                sourcePrimarySha256,
                sourceStoreSha256,
                canonicalStoreSha256,
                primaryBytes,
                fnameBytes,
                primaryBytes,
                lastModifiedMillis,
                storeBytes,
                histories,
                revisions,
                NONE,
                0);
    }

    static P4B2FixtureManifest invalid(
            P4B2ProbeCase fixtureCase,
            P4B2RunMode firstMode,
            String primarySha256,
            long primaryBytes,
            long lastModifiedMillis,
            String oldSha256,
            long oldBytes) {
        return new P4B2FixtureManifest(
                fixtureCase,
                firstMode,
                primarySha256,
                primarySha256,
                NONE,
                NONE,
                primaryBytes,
                0,
                primaryBytes,
                lastModifiedMillis,
                0,
                0,
                0,
                oldSha256,
                oldBytes);
    }

    P4B2FixtureManifest afterFirstRun(
            String primarySha256,
            long primaryBytes,
            long lastModifiedMillis) {
        if (runMode.restart()) {
            throw new IllegalStateException("manifest is already in its restart phase");
        }
        return new P4B2FixtureManifest(
                fixtureCase,
                runMode.restartMode(),
                sourcePrimarySha256,
                primarySha256,
                sourceStoreSha256,
                canonicalStoreSha256,
                sourcePrimaryBytes,
                sourceFnameBytes,
                primaryBytes,
                lastModifiedMillis,
                expectedStoreBytes,
                expectedHistories,
                expectedRevisions,
                expectedOldSha256,
                expectedOldBytes);
    }

    void write(Path worldRoot) throws IOException {
        var text = "case=" + fixtureCase.token() + "\n"
                + "phase=" + runMode.token() + "\n"
                + "source_primary_sha256=" + sourcePrimarySha256 + "\n"
                + "expected_primary_sha256=" + expectedPrimarySha256 + "\n"
                + "source_store_sha256=" + sourceStoreSha256 + "\n"
                + "canonical_store_sha256=" + canonicalStoreSha256 + "\n"
                + "source_primary_bytes=" + sourcePrimaryBytes + "\n"
                + "source_fname_bytes=" + sourceFnameBytes + "\n"
                + "expected_primary_bytes=" + expectedPrimaryBytes + "\n"
                + "expected_primary_last_modified_millis="
                + expectedPrimaryLastModifiedMillis + "\n"
                + "expected_store_bytes=" + expectedStoreBytes + "\n"
                + "expected_histories=" + expectedHistories + "\n"
                + "expected_revisions=" + expectedRevisions + "\n"
                + "expected_old_sha256=" + expectedOldSha256 + "\n"
                + "expected_old_bytes=" + expectedOldBytes + "\n";
        var bytes = text.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > MAX_MANIFEST_BYTES) {
            throw new IllegalStateException("fixture manifest exceeds its hard byte bound");
        }
        Files.write(worldRoot.resolve(MANIFEST_FILE_NAME), bytes);
    }

    static P4B2FixtureManifest read(Path worldRoot) throws IOException {
        var path = worldRoot.resolve(MANIFEST_FILE_NAME);
        if (Files.size(path) > MAX_MANIFEST_BYTES) {
            throw new IllegalArgumentException("fixture manifest exceeds its hard byte bound");
        }
        var values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            values.load(reader);
        }
        if (!values.stringPropertyNames().equals(KEYS)) {
            throw new IllegalArgumentException("fixture manifest fields are not exact");
        }
        var fixtureCase = P4B2ProbeCase.fromToken(required(values, "case"));
        var runMode = P4B2RunMode.fromToken(required(values, "phase"));
        return new P4B2FixtureManifest(
                fixtureCase,
                runMode,
                required(values, "source_primary_sha256"),
                required(values, "expected_primary_sha256"),
                required(values, "source_store_sha256"),
                required(values, "canonical_store_sha256"),
                parseLong(values, "source_primary_bytes"),
                parseLong(values, "source_fname_bytes"),
                parseLong(values, "expected_primary_bytes"),
                parseLong(values, "expected_primary_last_modified_millis"),
                parseInt(values, "expected_store_bytes"),
                parseInt(values, "expected_histories"),
                parseInt(values, "expected_revisions"),
                required(values, "expected_old_sha256"),
                parseLong(values, "expected_old_bytes"));
    }

    static Path worldRoot(Path gameDirectory) {
        return gameDirectory.resolve("world");
    }

    static Path primary(Path worldRoot) {
        return worldRoot.resolve("data").resolve(PRIMARY_FILE_NAME);
    }

    static Path oldPrimary(Path worldRoot) {
        return worldRoot.resolve("data").resolve(OLD_FILE_NAME);
    }

    private static String required(Properties values, String key) {
        var value = values.getProperty(key);
        if (value == null || value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException("fixture manifest value is missing or unbounded");
        }
        return value;
    }

    private static int parseInt(Properties values, String key) {
        try {
            return Integer.parseInt(required(values, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("fixture manifest integer is malformed");
        }
    }

    private static long parseLong(Properties values, String key) {
        try {
            return Long.parseLong(required(values, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("fixture manifest long is malformed");
        }
    }

    private static void requireOptionalChecksum(String checksum) {
        if (!NONE.equals(checksum)) {
            P4B2Hashing.requireSha256(checksum);
        }
    }
}
