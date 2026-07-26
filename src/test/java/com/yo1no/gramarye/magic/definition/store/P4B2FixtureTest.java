package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.neoforged.neoforge.common.IOUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class P4B2FixtureTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void smallPhysicalFixtureIsDeterministicCurrentAndDomainEquivalent() {
        var first = P4B2FixtureBuilder.smallStoreFixture();
        var second = P4B2FixtureBuilder.smallStoreFixture();

        assertAll(
                () -> assertArrayEquals(
                        first.canonicalStoreBytes(), second.canonicalStoreBytes()),
                () -> assertArrayEquals(
                        first.noncanonicalStoreBytes(), second.noncanonicalStoreBytes()),
                () -> assertEquals(
                        first.canonicalStoreSha256(), second.canonicalStoreSha256()),
                () -> assertEquals(
                        first.noncanonicalStoreSha256(), second.noncanonicalStoreSha256()),
                () -> assertNotEquals(
                        first.canonicalStoreSha256(), first.noncanonicalStoreSha256()),
                () -> assertFalse(java.util.Arrays.equals(
                        first.canonicalStoreBytes(), first.noncanonicalStoreBytes())),
                () -> assertEquals(2, first.histories()),
                () -> assertEquals(4, first.revisions()));
    }

    @Test
    void manifestRoundTripsExactBoundedFieldsWithoutRawPayload() throws Exception {
        var checksumA = "01".repeat(32);
        var checksumB = "23".repeat(32);
        var manifest = P4B2FixtureManifest.full(
                checksumA,
                checksumB,
                checksumA,
                1_024,
                99,
                P4B2FixtureBuilder.FULL_SIZE_MINIMUM_BYTES,
                8,
                64);
        var world = temporaryDirectory.resolve("manifest-world");
        Files.createDirectories(world);
        manifest.write(world);

        var loaded = P4B2FixtureManifest.read(world);
        var text = Files.readString(world.resolve(P4B2FixtureManifest.MANIFEST_FILE_NAME));
        assertAll(
                () -> assertEquals(manifest, loaded),
                () -> assertEquals(0, loaded.sourceFnameBytes()),
                () -> assertTrue(Files.size(
                                world.resolve(P4B2FixtureManifest.MANIFEST_FILE_NAME))
                        < 4_096),
                () -> assertTrue(text.contains("source_fname_bytes=0")),
                () -> assertFalse(text.contains("document")),
                () -> assertFalse(text.contains("owner")),
                () -> assertFalse(text.contains("path")),
                () -> assertFalse(text.contains("payload")));

        Files.writeString(
                world.resolve(P4B2FixtureManifest.MANIFEST_FILE_NAME),
                text + "unexpected=value\n");
        assertThrows(
                IllegalArgumentException.class,
                () -> P4B2FixtureManifest.read(world));
    }

    @Test
    void hostileFnameManifestRequiresExactMaximumAndPositiveHeaderLength()
            throws Exception {
        var sourcePrimaryChecksum = "45".repeat(32);
        var sourceStoreChecksum = "67".repeat(32);
        var canonicalStoreChecksum = "89".repeat(32);
        var maximum = MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES;
        var fnameBytes = maximum - 1_024L;
        var manifest = P4B2FixtureManifest.hostileFname(
                sourcePrimaryChecksum,
                sourceStoreChecksum,
                canonicalStoreChecksum,
                maximum,
                fnameBytes,
                123,
                66_060_348,
                8,
                64);
        var world = temporaryDirectory.resolve("hostile-manifest-world");
        Files.createDirectories(world);
        manifest.write(world);

        var loaded = P4B2FixtureManifest.read(world);
        var restart = loaded.afterFirstRun(
                canonicalStoreChecksum,
                12_345,
                456);
        var text = Files.readString(
                world.resolve(P4B2FixtureManifest.MANIFEST_FILE_NAME));
        assertAll(
                () -> assertEquals(manifest, loaded),
                () -> assertEquals(P4B2ProbeCase.HOSTILE_FNAME, loaded.fixtureCase()),
                () -> assertEquals(
                        P4B2RunMode.HOSTILE_FNAME_FIRST, loaded.runMode()),
                () -> assertEquals(maximum, loaded.sourcePrimaryBytes()),
                () -> assertEquals(fnameBytes, loaded.sourceFnameBytes()),
                () -> assertEquals(66_060_348, loaded.expectedStoreBytes()),
                () -> assertEquals(
                        P4B2RunMode.HOSTILE_FNAME_RESTART, restart.runMode()),
                () -> assertEquals(maximum, restart.sourcePrimaryBytes()),
                () -> assertEquals(fnameBytes, restart.sourceFnameBytes()),
                () -> assertEquals(12_345, restart.expectedPrimaryBytes()),
                () -> assertTrue(text.contains(
                        "source_primary_bytes=" + maximum)),
                () -> assertTrue(text.contains(
                        "source_fname_bytes=" + fnameBytes)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P4B2FixtureManifest.hostileFname(
                                sourcePrimaryChecksum,
                                sourceStoreChecksum,
                                canonicalStoreChecksum,
                                maximum - 1L,
                                fnameBytes,
                                123,
                                66_060_348,
                                8,
                                64)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P4B2FixtureManifest.hostileFname(
                                sourcePrimaryChecksum,
                                sourceStoreChecksum,
                                canonicalStoreChecksum,
                                maximum,
                                0,
                                123,
                                66_060_348,
                                8,
                                64)));
    }

    @Test
    void invalidFixturesAreDeterministicAndTheirOldPrimariesRemainCanonical() throws Exception {
        var firstRoot = temporaryDirectory.resolve("first");
        var secondRoot = temporaryDirectory.resolve("second");
        prepareInvalidMatrix(firstRoot);
        prepareInvalidMatrix(secondRoot);

        for (var caseDirectory : new String[] {
                "malformed", "trailing", "second-member"
        }) {
            var firstWorld = P4B2FixtureManifest.worldRoot(firstRoot.resolve(caseDirectory));
            var secondWorld = P4B2FixtureManifest.worldRoot(secondRoot.resolve(caseDirectory));
            var first = P4B2FixtureManifest.read(firstWorld);
            var second = P4B2FixtureManifest.read(secondWorld);
            assertAll(
                    () -> assertEquals(first.fixtureCase(), second.fixtureCase()),
                    () -> assertEquals(0, first.sourceFnameBytes()),
                    () -> assertEquals(0, second.sourceFnameBytes()),
                    () -> assertEquals(
                            first.sourcePrimarySha256(), second.sourcePrimarySha256()),
                    () -> assertEquals(first.sourcePrimaryBytes(), second.sourcePrimaryBytes()),
                    () -> assertEquals(first.expectedOldSha256(), second.expectedOldSha256()),
                    () -> assertEquals(first.expectedOldBytes(), second.expectedOldBytes()),
                    () -> assertEquals(
                            first.sourcePrimarySha256(),
                            P4B2Hashing.sha256(P4B2FixtureManifest.primary(firstWorld))),
                    () -> assertEquals(
                            first.expectedOldSha256(),
                            P4B2Hashing.sha256(P4B2FixtureManifest.oldPrimary(firstWorld))));

            var oldLoad = SkillSavedDataPrimaryIngress.load(
                    P4B2FixtureManifest.oldPrimary(firstWorld),
                    Optional.of(RegistryAccess.EMPTY));
            var oldReady = assertInstanceOf(
                    SkillSavedDataPrimaryLoadResult.Ready.class, oldLoad);
            assertFalse(oldReady.candidate().rewriteRequired());
        }

        var malformed = P4B2FixtureManifest.read(P4B2FixtureManifest.worldRoot(
                firstRoot.resolve("malformed")));
        var trailing = P4B2FixtureManifest.read(P4B2FixtureManifest.worldRoot(
                firstRoot.resolve("trailing")));
        var secondMember = P4B2FixtureManifest.read(P4B2FixtureManifest.worldRoot(
                firstRoot.resolve("second-member")));
        assertAll(
                () -> assertNotEquals(
                        malformed.sourcePrimarySha256(), trailing.sourcePrimarySha256()),
                () -> assertNotEquals(
                        trailing.sourcePrimarySha256(), secondMember.sourcePrimarySha256()),
                () -> assertNotEquals(
                        malformed.sourcePrimarySha256(), secondMember.sourcePrimarySha256()));
    }

    @Test
    void summaryIsBoundedAndContainsNoRawState() {
        var line = new P4B2ProbeSummary(
                "full-restart",
                66_060_348,
                1_024_000,
                8,
                64,
                1_073_741_824L,
                536_870_912L,
                900_000_000L,
                950_000_000L,
                2_000,
                "0123456789abcdef").line();

        assertAll(
                () -> assertTrue(line.startsWith("P4B2_PROBE_OK ")),
                () -> assertTrue(line.length() < 384),
                () -> assertFalse(line.contains("document")),
                () -> assertFalse(line.contains("owner")),
                () -> assertFalse(line.contains("path")),
                () -> assertFalse(line.contains("payload")));
    }

    @Test
    void packagedRuntimeFixtureIsDeterministicRewriteReadyAndStrictlyVerifiable()
            throws Exception {
        var firstGame = temporaryDirectory.resolve("packaged-first");
        var secondGame = temporaryDirectory.resolve("packaged-second");
        P4B2FixtureBuilder.preparePackagedRuntime(firstGame);
        P4B2FixtureBuilder.preparePackagedRuntime(secondGame);

        var firstWorld = P4B2FixtureManifest.worldRoot(firstGame);
        var secondWorld = P4B2FixtureManifest.worldRoot(secondGame);
        var firstPrimary = P4B2FixtureManifest.primary(firstWorld);
        var secondPrimary = P4B2FixtureManifest.primary(secondWorld);
        var manifestName = "p4-b2-packaged-runtime.properties";
        var firstManifest = Files.readString(firstWorld.resolve(manifestName));
        var secondManifest = Files.readString(secondWorld.resolve(manifestName));
        var firstLoad = SkillSavedDataPrimaryIngress.load(
                firstPrimary, Optional.of(RegistryAccess.EMPTY));
        var firstReady = assertInstanceOf(
                SkillSavedDataPrimaryLoadResult.Ready.class, firstLoad);
        var candidate = firstReady.candidate();
        var expected = P4B2FixtureBuilder.smallStoreFixture();

        assertAll(
                () -> assertArrayEquals(
                        Files.readAllBytes(firstPrimary), Files.readAllBytes(secondPrimary)),
                () -> assertEquals(firstManifest, secondManifest),
                () -> assertTrue(candidate.rewriteRequired()),
                () -> assertFalse(candidate.facts().truncated()),
                () -> assertTrue(candidate.facts().facts().isEmpty()),
                () -> assertEquals(0, candidate.carrier().pending().byteCount()),
                () -> assertEquals(
                        expected.canonicalStoreSha256(),
                        P4B2Hashing.sha256(candidate.carrier().storeCarrier())),
                () -> assertEquals(2, candidate.carrier().storeCarrier().historyCount()),
                () -> assertEquals(4, candidate.carrier().storeCarrier().revisionCount()),
                () -> assertTrue(firstManifest.contains(
                        "source_store_sha256=" + expected.noncanonicalStoreSha256())),
                () -> assertTrue(firstManifest.contains(
                        "canonical_store_sha256=" + expected.canonicalStoreSha256())),
                () -> assertThrows(
                        AssertionError.class,
                        () -> P4B2RuntimePackagingVerifier.verifyRuntime(firstGame)));

        var adapter = GramaryeSkillSavedData.ready(candidate);
        var platformRoot = new CompoundTag();
        platformRoot.put(
                SkillSavedDataPersistenceSchema.DATA_FIELD,
                adapter.save(new CompoundTag(), RegistryAccess.EMPTY));
        NbtUtils.addCurrentDataVersion(platformRoot);
        IOUtilities.writeNbtCompressed(platformRoot, firstPrimary);

        var verification = P4B2RuntimePackagingVerifier.verifyRuntime(firstGame);
        var canonicalLoad = SkillSavedDataPrimaryIngress.load(
                firstPrimary, Optional.of(RegistryAccess.EMPTY));
        var canonicalReady = assertInstanceOf(
                SkillSavedDataPrimaryLoadResult.Ready.class, canonicalLoad);
        assertAll(
                () -> assertTrue(verification.startsWith(
                        "P4B2_PACKAGED_RUNTIME_OK ")),
                () -> assertFalse(canonicalReady.candidate().rewriteRequired()),
                () -> assertFalse(canonicalReady.candidate().facts().truncated()),
                () -> assertTrue(canonicalReady.candidate().facts().facts().isEmpty()),
                () -> assertEquals(
                        expected.canonicalStoreSha256(),
                        P4B2Hashing.sha256(
                                canonicalReady.candidate().carrier().storeCarrier())),
                () -> assertFalse(Files.exists(
                        P4B2FixtureManifest.oldPrimary(firstWorld))));
    }

    private static void prepareInvalidMatrix(Path root) throws Exception {
        P4B2FixtureBuilder.prepareInvalidWorlds(
                root.resolve("malformed"),
                root.resolve("trailing"),
                root.resolve("second-member"));
    }
}
