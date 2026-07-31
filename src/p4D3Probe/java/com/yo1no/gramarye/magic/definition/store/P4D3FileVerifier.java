package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.player.P4D3PlayerProbe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;

/** Strict external Store/journal/playerdata verifier for an independent process. */
final class P4D3FileVerifier {
    private P4D3FileVerifier() {
    }

    static Verification verify(Path gameDirectory, P4D3RunMode mode) throws IOException {
        var worldRoot = worldRoot(gameDirectory);
        var manifest = P4D3FixtureManifest.read(worldRoot);
        if (manifest.probeCase() != mode.probeCase()
                || !manifest.phase().equals(mode.completionPhase())) {
            throw new AssertionError("P4-D3 verifier mode and manifest phase differ");
        }
        var actual = inspect(worldRoot, manifest.probeCase());
        requireManifestMatches(manifest, actual);
        requireCaseSemantics(worldRoot, mode, actual);
        return new Verification(
                mode.probeCase().token(), mode.token(), actual.storeBytes(),
                actual.histories(), actual.revisions(), actual.journalBytes(),
                actual.journalEntries(), actual.rootCount(),
                P4D3Hashing.witness(actual.storeChecksum()),
                P4D3Hashing.witness(actual.journalChecksum()),
                P4D3Hashing.witness(actual.selectedAttachmentChecksum()));
    }

    static P4D3FixtureManifest.DiskFacts inspect(
            Path worldRoot, P4D3ProbeCase probeCase) throws IOException {
        var primary = P4D3StoreJournalFixture.primary(worldRoot);
        var loaded = SkillSavedDataPrimaryIngress.load(
                primary, Optional.of(RegistryAccess.EMPTY));
        if (!(loaded instanceof SkillSavedDataPrimaryLoadResult.Ready ready)) {
            throw new AssertionError("P4-D3 primary did not form a Ready candidate");
        }
        var candidate = ready.candidate();
        var carrier = candidate.carrier().storeCarrier();
        var rebuiltResult = SkillStoreCarrierBuilder.rebuild(candidate.store());
        if (!(rebuiltResult instanceof CarrierBuildResult.Success rebuilt)
                || rebuilt.carrier().storeByteCount() != carrier.storeByteCount()
                || rebuilt.carrier().historyCount() != carrier.historyCount()
                || rebuilt.carrier().revisionCount() != carrier.revisionCount()) {
            throw new AssertionError("P4-D3 loaded Store failed exact A3 rebuild");
        }
        var sourceBytes = new byte[carrier.storeByteCount()];
        carrier.copyStoreBlobInto(sourceBytes, 0);
        if (!rebuilt.carrier().matchesStoreBlob(
                ImmutableStoreBlob.takeOwnership(sourceBytes))) {
            throw new AssertionError("P4-D3 loaded Store A3 rebuild was not byte-equal");
        }
        var sourcePending = candidate.carrier().pending();
        var journalLoad = PendingAttachmentJournalFraming.load(sourcePending);
        if (!(journalLoad instanceof PendingAttachmentJournalLoadResult.Loaded journalReady)) {
            throw new AssertionError("P4-D3 pending journal failed strict framing load");
        }
        var journal = journalReady.candidate().journal();
        var encoded = journalReady.candidate().encoded();
        if (!sourcePending.contentEquals(encoded.pending())) {
            throw new AssertionError("P4-D3 disk journal is not canonical");
        }
        var audit = candidate.store().auditJournalTargets(journal);
        if (probeCase == P4D3ProbeCase.J1) {
            if (!(audit instanceof JournalTargetAuditResult.Rejected)) {
                throw new AssertionError("P4-D3 J1 target unexpectedly passed audit");
            }
        } else if (!(audit instanceof JournalTargetAuditResult.Audited)) {
            throw new AssertionError("P4-D3 disk journal target audit failed");
        }

        var selected = P4D3PlayerProbe.readPlayerdata(
                worldRoot, P4D3ProbeSupport.selectedPlayerId());
        var submission = probeCase == P4D3ProbeCase.COMBINED
                ? P4D3PlayerProbe.readPlayerdata(
                        worldRoot, P4D3ProbeSupport.submissionPlayerId())
                : null;
        return new P4D3FixtureManifest.DiskFacts(
                P4D3Hashing.sha256(primary), Files.size(primary),
                carrier.storeByteCount(), carrier.historyCount(), carrier.revisionCount(),
                P4D3Hashing.sha256(carrier), encoded.byteCount(), encoded.entryCount(),
                journal.targetReferences().size(),
                P4D3Hashing.sha256(encoded.pending().copyBytes()),
                selected.attachmentChecksum(), selected.playerdataChecksum(),
                selected.playerdataBytes(),
                submission == null ? P4D3FixtureManifest.NONE
                        : submission.attachmentChecksum(),
                submission == null ? P4D3FixtureManifest.NONE
                        : submission.playerdataChecksum(),
                submission == null ? 0 : submission.playerdataBytes());
    }

    private static void requireManifestMatches(
            P4D3FixtureManifest manifest, P4D3FixtureManifest.DiskFacts actual) {
        if (!manifest.primaryChecksum().equals(actual.primaryChecksum())
                || manifest.primaryBytes() != actual.primaryBytes()
                || manifest.storeBytes() != actual.storeBytes()
                || manifest.histories() != actual.histories()
                || manifest.revisions() != actual.revisions()
                || !manifest.storeChecksum().equals(actual.storeChecksum())
                || manifest.journalBytes() != actual.journalBytes()
                || manifest.journalEntries() != actual.journalEntries()
                || manifest.rootCount() != actual.rootCount()
                || !manifest.journalChecksum().equals(actual.journalChecksum())
                || !manifest.selectedAttachmentChecksum()
                        .equals(actual.selectedAttachmentChecksum())
                || !manifest.selectedPlayerdataChecksum()
                        .equals(actual.selectedPlayerdataChecksum())
                || manifest.selectedPlayerdataBytes() != actual.selectedPlayerdataBytes()
                || !manifest.submissionAttachmentChecksum()
                        .equals(actual.submissionAttachmentChecksum())
                || !manifest.submissionPlayerdataChecksum()
                        .equals(actual.submissionPlayerdataChecksum())
                || manifest.submissionPlayerdataBytes()
                        != actual.submissionPlayerdataBytes()) {
            throw new AssertionError("P4-D3 disk facts differ from bounded manifest");
        }
    }

    private static void requireCaseSemantics(
            Path worldRoot,
            P4D3RunMode mode,
            P4D3FixtureManifest.DiskFacts actual) throws IOException {
        var selected = P4D3PlayerProbe.readPlayerdata(
                worldRoot, P4D3ProbeSupport.selectedPlayerId());
        var first = selected.tuple(P4D3StoreJournalFixture.skillId(0));
        var expectedEntries = switch (mode.probeCase()) {
            case F, H -> mode.restart() ? 0 : 1;
            case COMBINED -> mode.restart() ? 4_092 : 4_096;
            default -> 1;
        };
        if (actual.journalEntries() != expectedEntries
                || actual.rootCount() != expectedEntries) {
            throw new AssertionError("P4-D3 case journal count differs from its matrix");
        }
        switch (mode.probeCase()) {
            case D, E, G -> requireTuple(first,
                    mode.restart() ? Optional.of(P4D3StoreJournalFixture.target(0, 0))
                            : Optional.empty(),
                    mode.restart() ? 1 : 0);
            case F, H -> requireTuple(first,
                    Optional.of(P4D3StoreJournalFixture.target(0, 0)), 1);
            case I -> requireTuple(first,
                    Optional.of(P4D3StoreJournalFixture.target(0, 0)), 7);
            case J1 -> requireTuple(first, Optional.empty(), 0);
            case COMBINED -> {
                requireTuple(first,
                        Optional.of(P4D3StoreJournalFixture.target(0, 1)), 2);
                var second = selected.tuple(P4D3StoreJournalFixture.skillId(1));
                requireTuple(second,
                        mode.restart()
                                ? Optional.of(P4D3StoreJournalFixture.target(1, 1))
                                : Optional.empty(),
                        mode.restart() ? 2 : 0);
                var submission = P4D3PlayerProbe.readPlayerdata(
                        worldRoot, P4D3ProbeSupport.submissionPlayerId());
                requireTuple(
                        submission.tuple(P4D3StoreJournalFixture.submissionSkillId()),
                        Optional.of(P4D3StoreJournalFixture.submissionTarget()), 1);
                if (submission.draftCount() != 1
                        || actual.histories() != 2_049 || actual.revisions() != 4_096) {
                    throw new AssertionError(
                            "P4-D3 combined submission/draft Store shape changed");
                }
            }
        }
    }

    private static void requireTuple(
            P4D3PlayerProbe.Tuple actual,
            Optional<com.yo1no.gramarye.magic.definition.document.SkillReference> pointer,
            int generation) {
        if (!actual.pointer().equals(pointer) || actual.generation() != generation) {
            throw new AssertionError("P4-D3 player tuple differs from its matrix");
        }
    }

    static Path worldRoot(Path gameDirectory) {
        return gameDirectory.resolve("world");
    }

    record Verification(
            String probeCase,
            String phase,
            int storeBytes,
            int histories,
            int revisions,
            int journalBytes,
            int journalEntries,
            int rootCount,
            String storeChecksum,
            String journalChecksum,
            String attachmentChecksum) {
        String line() {
            var line = "P4D3_FILE_OK"
                    + " case=" + probeCase
                    + " phase=" + phase
                    + " store_bytes=" + storeBytes
                    + " histories=" + histories
                    + " revisions=" + revisions
                    + " journal_bytes=" + journalBytes
                    + " journal_entries=" + journalEntries
                    + " roots=" + rootCount
                    + " store_checksum=" + storeChecksum
                    + " journal_checksum=" + journalChecksum
                    + " attachment_checksum=" + attachmentChecksum;
            if (line.length() > 480) {
                throw new IllegalStateException("P4-D3 verifier output is unbounded");
            }
            return line;
        }
    }
}
