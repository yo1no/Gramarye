package com.yo1no.gramarye.magic.definition.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;

/** Post-process verifier that turns async platform writes into a hard task dependency. */
final class P4B2FileVerifier {
    private static final Optional<HolderLookup.Provider> STANDALONE_PROVIDER =
            Optional.of(RegistryAccess.EMPTY);

    private P4B2FileVerifier() {
    }

    static FileVerification verify(Path gameDirectory, P4B2RunMode expectedMode)
            throws IOException {
        var worldRoot = P4B2FixtureManifest.worldRoot(gameDirectory);
        var manifest = P4B2FixtureManifest.read(worldRoot);
        if (manifest.runMode() != expectedMode) {
            throw new AssertionError("post-run manifest phase does not match its task");
        }
        var primary = P4B2FixtureManifest.primary(worldRoot);
        var actualHash = P4B2Hashing.sha256(primary);
        var actualBytes = Files.size(primary);
        var actualModified = Files.getLastModifiedTime(primary).toMillis();

        if (expectedMode.fullSize()) {
            if (manifest.fixtureCase() == P4B2ProbeCase.HOSTILE_FNAME) {
                P4B2FixtureBuilder.requireCanonicalGzipWithoutFname(primary);
            }
            requireCanonicalFullPrimary(primary, manifest);
            if (expectedMode.restart()) {
                requireUnchanged(manifest, actualHash, actualBytes, actualModified);
            } else {
                if (actualHash.equals(manifest.sourcePrimarySha256())) {
                    throw new AssertionError("first full-size run did not publish canonical bytes");
                }
                manifest.afterFirstRun(actualHash, actualBytes, actualModified).write(worldRoot);
            }
        } else {
            requireUnchanged(manifest, actualHash, actualBytes, actualModified);
            requireOldUnchanged(worldRoot, manifest);
            P4B2FixtureBuilder.requireInvalidClassification(primary, manifest.fixtureCase());
            if (!expectedMode.restart()) {
                manifest.afterFirstRun(actualHash, actualBytes, actualModified).write(worldRoot);
            }
        }

        return new FileVerification(
                manifest.fixtureCase().token(),
                expectedMode.token(),
                actualBytes,
                manifest.sourceFnameBytes(),
                manifest.expectedStoreBytes(),
                manifest.expectedHistories(),
                manifest.expectedRevisions(),
                P4B2Hashing.witness(actualHash));
    }

    private static void requireCanonicalFullPrimary(
            Path primary,
            P4B2FixtureManifest manifest) {
        var loaded = SkillSavedDataPrimaryIngress.load(primary, STANDALONE_PROVIDER);
        if (!(loaded instanceof SkillSavedDataPrimaryLoadResult.Ready ready)
                || ready.candidate().rewriteRequired()
                || ready.candidate().facts().truncated()
                || !ready.candidate().facts().facts().isEmpty()) {
            throw new AssertionError("saved full-size primary is not canonical Ready");
        }
        var candidate = ready.candidate();
        var carrier = candidate.carrier().storeCarrier();
        if (candidate.carrier().pending().byteCount() != 0
                || carrier.storeByteCount() < P4B2FixtureBuilder.FULL_SIZE_MINIMUM_BYTES
                || carrier.storeByteCount() != manifest.expectedStoreBytes()
                || !P4B2Hashing.sha256(carrier).equals(manifest.canonicalStoreSha256())) {
            throw new AssertionError("saved full-size carrier differs from its manifest");
        }
        P4B2FixtureBuilder.requireCarrierDomain(
                carrier,
                candidate.store(),
                manifest.expectedHistories(),
                manifest.expectedRevisions());
    }

    private static void requireUnchanged(
            P4B2FixtureManifest manifest,
            String actualHash,
            long actualBytes,
            long actualModified) {
        if (!actualHash.equals(manifest.expectedPrimarySha256())
                || actualBytes != manifest.expectedPrimaryBytes()
                || actualModified != manifest.expectedPrimaryLastModifiedMillis()) {
            throw new AssertionError("primary changed during a clean/quarantined run");
        }
    }

    private static void requireOldUnchanged(
            Path worldRoot,
            P4B2FixtureManifest manifest) throws IOException {
        var oldPrimary = P4B2FixtureManifest.oldPrimary(worldRoot);
        if (Files.size(oldPrimary) != manifest.expectedOldBytes()
                || !P4B2Hashing.sha256(oldPrimary).equals(manifest.expectedOldSha256())) {
            throw new AssertionError(".dat_old fixture changed or participated in recovery");
        }
    }

    record FileVerification(
            String fixtureCase,
            String phase,
            long primaryBytes,
            long sourceFnameBytes,
            int storeBytes,
            int histories,
            int revisions,
            String checksum) {
        String line() {
            var line = "P4B2_FILE_OK"
                    + " case=" + fixtureCase
                    + " phase=" + phase
                    + " primary_bytes=" + primaryBytes
                    + " source_fname_bytes=" + sourceFnameBytes
                    + " store_bytes=" + storeBytes
                    + " histories=" + histories
                    + " revisions=" + revisions
                    + " checksum=" + checksum;
            if (line.length() > 320) {
                throw new IllegalStateException("file verification summary is unbounded");
            }
            return line;
        }
    }
}
