package com.yo1no.gramarye.magic.definition.store;

import com.google.gson.JsonObject;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.RegistryAccess;

/** Independent strict disk/report verifier for the two P4-E3 startup processes. */
final class P4E3FileVerifier {
    private static final String FIRST_RUNTIME_FILE = "first-runtime.json";
    private static final String FIRST_RESULT_FILE = "first.json";
    private static final String RESTART_RUNTIME_FILE = "restart-runtime.json";
    private static final String RESTART_RESULT_FILE = "restart.json";
    private static final Set<String> RUNTIME_FIELDS = Set.of(
            "schema_version", "mode", "session_token",
            "audit_invocations", "audit_variant", "audit_generation",
            "complete_consume_invocations", "snapshot_invocations",
            "snapshot_variant", "complete_root_count", "reclaim_invocations",
            "reclaim_variant", "histories_scanned", "revisions_scanned",
            "histories_changed", "revisions_reclaimed", "dirty_before",
            "dirty_after", "index_terminal_observations", "index_terminal",
            "index_generation", "primary_sha256", "primary_bytes",
            "primary_modified_millis");
    private static final Set<String> RESULT_FIELDS = Set.of(
            "schema_version", "mode", "primary_sha256", "primary_bytes",
            "primary_modified_millis", "playerdata_inventory_sha256",
            "store_histories", "store_revisions", "journal_entries",
            "journal_bytes", "revisions_reclaimed", "index_terminal");

    private P4E3FileVerifier() {
    }

    static Verification verify(Path gameDirectory, Path reportRoot, Mode mode)
            throws IOException {
        var game = gameDirectory.toAbsolutePath().normalize();
        var reports = reportRoot.toAbsolutePath().normalize();
        var world = game.resolve("world");
        var fixture = P4E3FixtureManifest.read(reports);
        var runtime = readRuntime(reports, mode);
        requireRuntime(runtime, mode);
        var disk = inspect(world);
        var inventory = inventory(world.resolve("playerdata"));
        if (!inventory.sha256().equals(fixture.playerdataInventorySha256())
                || inventory.entries() != 4_096
                || inventory.relevant() != 2_048
                || inventory.oldFiles() != 2_048
                || inventory.compressedTotal() != 268_440_533L
                || inventory.compressedMaximum() != 33_559_514L) {
            throw new IOException("P4-E3 playerdata inventory changed after startup");
        }
        if (disk.histories() != 2_049 || disk.revisions() != 4_095
                || disk.journalEntries() != 4_096
                || disk.journalBytes() != 1_048_538) {
            throw new IOException("P4-E3 post-reclaim Store/journal geometry changed");
        }
        if (mode == Mode.FIRST) {
            if (fixture.primarySha256().equals(disk.primarySha256())
                    || fixture.storeRevisions() != 4_096
                    || !runtime.primarySha256().equals(fixture.primarySha256())
                    || runtime.primaryBytes() != fixture.primaryBytes()
                    || runtime.primaryModifiedMillis() != fixture.primaryModifiedMillis()) {
                throw new IOException("P4-E3 first positive reclaim was not saved");
            }
        } else {
            var first = readResult(reports.resolve(FIRST_RESULT_FILE), Mode.FIRST);
            if (!first.primarySha256().equals(disk.primarySha256())
                    || first.primaryBytes() != disk.primaryBytes()
                    || first.primaryModifiedMillis() != disk.primaryModifiedMillis()
                    || !runtime.primarySha256().equals(first.primarySha256())
                    || runtime.primaryBytes() != first.primaryBytes()
                    || runtime.primaryModifiedMillis() != first.primaryModifiedMillis()) {
                throw new IOException("P4-E3 restart rewrote the first saved primary");
            }
        }

        var result = new Result(
                mode.token,
                disk.primarySha256(),
                disk.primaryBytes(),
                disk.primaryModifiedMillis(),
                inventory.sha256(),
                disk.histories(),
                disk.revisions(),
                disk.journalEntries(),
                disk.journalBytes(),
                runtime.revisionsReclaimed(),
                runtime.indexTerminal());
        writeResult(
                reports.resolve(mode == Mode.FIRST
                        ? FIRST_RESULT_FILE : RESTART_RESULT_FILE),
                result);
        return new Verification(
                mode.token, disk.histories(), disk.revisions(), disk.journalEntries(),
                disk.journalBytes(), runtime.revisionsReclaimed(),
                runtime.indexTerminal(), disk.primarySha256().substring(0, 16));
    }

    private static RuntimeObservation readRuntime(Path reports, Mode mode) throws IOException {
        var json = P4E3FixtureManifest.readBounded(reports.resolve(
                mode == Mode.FIRST ? FIRST_RUNTIME_FILE : RESTART_RUNTIME_FILE));
        if (!json.keySet().equals(RUNTIME_FIELDS)
                || P4E3FixtureManifest.exactInt(json, "schema_version") != 0) {
            throw new IOException("P4-E3 runtime report fields changed");
        }
        return new RuntimeObservation(
                P4E3FixtureManifest.exactString(json, "mode"),
                P4E3FixtureManifest.exactLong(json, "session_token"),
                P4E3FixtureManifest.exactInt(json, "audit_invocations"),
                P4E3FixtureManifest.exactString(json, "audit_variant"),
                P4E3FixtureManifest.exactLong(json, "audit_generation"),
                P4E3FixtureManifest.exactInt(json, "complete_consume_invocations"),
                P4E3FixtureManifest.exactInt(json, "snapshot_invocations"),
                P4E3FixtureManifest.exactString(json, "snapshot_variant"),
                P4E3FixtureManifest.exactInt(json, "complete_root_count"),
                P4E3FixtureManifest.exactInt(json, "reclaim_invocations"),
                P4E3FixtureManifest.exactString(json, "reclaim_variant"),
                P4E3FixtureManifest.exactInt(json, "histories_scanned"),
                P4E3FixtureManifest.exactInt(json, "revisions_scanned"),
                P4E3FixtureManifest.exactInt(json, "histories_changed"),
                P4E3FixtureManifest.exactInt(json, "revisions_reclaimed"),
                exactBoolean(json, "dirty_before"),
                exactBoolean(json, "dirty_after"),
                P4E3FixtureManifest.exactInt(json, "index_terminal_observations"),
                P4E3FixtureManifest.exactString(json, "index_terminal"),
                P4E3FixtureManifest.exactLong(json, "index_generation"),
                P4E3FixtureManifest.exactString(json, "primary_sha256"),
                P4E3FixtureManifest.exactLong(json, "primary_bytes"),
                P4E3FixtureManifest.exactLong(json, "primary_modified_millis"));
    }

    private static void requireRuntime(RuntimeObservation runtime, Mode mode)
            throws IOException {
        var first = mode == Mode.FIRST;
        if (!runtime.mode().equals(mode.token)
                || runtime.sessionToken() != 1L
                || runtime.auditInvocations() != 1
                || !runtime.auditVariant().equals("COMPLETE")
                || runtime.auditGeneration() != 1L
                || runtime.completeConsumeInvocations() != 1
                || runtime.snapshotInvocations() != 1
                || !runtime.snapshotVariant().equals("COMPLETE")
                || runtime.completeRootCount() != 65_536
                || runtime.reclaimInvocations() != 1
                || runtime.historiesScanned() != 2_049
                || runtime.revisionsScanned() != (first ? 4_096 : 4_095)
                || runtime.historiesChanged() != (first ? 1 : 0)
                || runtime.revisionsReclaimed() != (first ? 1 : 0)
                || runtime.dirtyBefore()
                || runtime.dirtyAfter() != first
                || runtime.indexTerminalObservations() != 1
                || runtime.indexGeneration() != 1L
                || !runtime.reclaimVariant().equals(
                        first ? "COMPLETED_POSITIVE" : "COMPLETED_ZERO")
                || !runtime.indexTerminal().equals(
                        first ? "INCOMPLETE" : "COMPLETE_INDEX")) {
            throw new IOException("P4-E3 direct startup observation differs from its mode");
        }
        requireSha256(runtime.primarySha256());
        if (runtime.primaryBytes() <= 0L || runtime.primaryModifiedMillis() < 0L) {
            throw new IOException("P4-E3 runtime primary identity is invalid");
        }
    }

    private static DiskFacts inspect(Path worldRoot) throws IOException {
        var primary = P4D3StoreJournalFixture.primary(worldRoot);
        var loaded = SkillSavedDataPrimaryIngress.load(
                primary, Optional.of(RegistryAccess.EMPTY));
        if (!(loaded instanceof SkillSavedDataPrimaryLoadResult.Ready ready)) {
            throw new IOException("P4-E3 post-startup primary did not load");
        }
        var candidate = ready.candidate();
        var carrier = candidate.carrier().storeCarrier();
        var journalLoad = PendingAttachmentJournalFraming.load(candidate.carrier().pending());
        if (!(journalLoad instanceof PendingAttachmentJournalLoadResult.Loaded journalReady)) {
            throw new IOException("P4-E3 post-startup journal did not load");
        }
        var journal = journalReady.candidate().journal();
        requireStableFinalShape(candidate.store(), journal);
        return new DiskFacts(
                carrier.historyCount(), carrier.revisionCount(),
                journalReady.candidate().encoded().entryCount(),
                journalReady.candidate().encoded().byteCount(),
                P4E3FixtureManifest.sha256(primary),
                Files.size(primary), Files.getLastModifiedTime(primary).toMillis());
    }

    private static void requireStableFinalShape(
            SkillDefinitionStore store, PendingAttachmentJournal journal) throws IOException {
        var histories = store.snapshot().histories();
        var h0 = history(histories, P4D3StoreJournalFixture.skillId(0));
        var h1 = history(histories, P4D3StoreJournalFixture.skillId(1));
        var h2 = history(histories, P4D3StoreJournalFixture.skillId(2));
        var h0r0 = P4D3StoreJournalFixture.target(0, 0);
        var h0r2 = P4D3StoreJournalFixture.target(0, 2);
        var h1r0 = P4D3StoreJournalFixture.target(1, 0);
        var h2r0 = P4D3StoreJournalFixture.target(2, 0);
        var h2r1 = P4D3StoreJournalFixture.target(2, 1);
        if (!revisions(h0).equals(List.of(h0r0, h0r2))
                || !revisions(h1).equals(List.of(h1r0))
                || !revisions(h2).equals(List.of(h2r0, h2r1))) {
            throw new IOException("P4-E3 post-reclaim H0/H1/H2 Store shape changed");
        }
        var targets = journal.entries().stream()
                .filter(entry -> entry.skillId().equals(h0.skillId())
                        || entry.skillId().equals(h1.skillId())
                        || entry.skillId().equals(h2.skillId()))
                .map(PendingAttachmentJournalEntry::targetPointer)
                .toList();
        if (!targets.equals(List.of(h0r0, h0r2, h1r0, h2r0, h2r1, h2r0))) {
            throw new IOException("P4-E3 H0/H1/H2 journal shape changed");
        }
    }

    private static SkillHistorySnapshot history(
            List<SkillHistorySnapshot> histories,
            com.yo1no.gramarye.magic.api.id.SkillId skillId) throws IOException {
        return histories.stream().filter(history -> history.skillId().equals(skillId))
                .findFirst().orElseThrow(() -> new IOException("P4-E3 history is absent"));
    }

    private static List<SkillReference> revisions(SkillHistorySnapshot history) {
        return history.revisions().stream()
                .map(revision -> new SkillReference(history.skillId(), revision.revision()))
                .toList();
    }

    private static Inventory inventory(Path playerdata) throws IOException {
        if (!Files.isDirectory(playerdata, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(playerdata)) {
            throw new IOException("P4-E3 playerdata directory is absent");
        }
        var lines = new ArrayList<String>(4_096);
        var entries = 0;
        var relevant = 0;
        var old = 0;
        var compressedTotal = 0L;
        var compressedMaximum = 0L;
        try (var stream = Files.list(playerdata)) {
            for (var path : stream.sorted(Comparator.naturalOrder()).toList()) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(path)) {
                    throw new IOException("P4-E3 playerdata contains a non-regular entry");
                }
                entries++;
                var name = path.getFileName().toString();
                var size = Files.size(path);
                if (name.endsWith(".dat")) {
                    relevant++;
                    compressedTotal = Math.addExact(compressedTotal, size);
                    compressedMaximum = Math.max(compressedMaximum, size);
                } else if (name.endsWith(".dat_old")) {
                    old++;
                    if (size != 1L) {
                        throw new IOException("P4-E3 old record is not its exact one byte");
                    }
                } else {
                    throw new IOException("P4-E3 playerdata contains an irrelevant entry");
                }
                lines.add(name + ":" + size + ":" + P4E3FixtureManifest.sha256(path));
            }
        }
        return new Inventory(
                entries, relevant, old, compressedTotal, compressedMaximum,
                P4E3FixtureManifest.sha256(P4E3FixtureBuilder.inventoryText(lines)));
    }

    private static void writeResult(Path path, Result result) throws IOException {
        var json = new JsonObject();
        json.addProperty("schema_version", 0);
        json.addProperty("mode", result.mode());
        json.addProperty("primary_sha256", result.primarySha256());
        json.addProperty("primary_bytes", result.primaryBytes());
        json.addProperty("primary_modified_millis", result.primaryModifiedMillis());
        json.addProperty("playerdata_inventory_sha256", result.playerdataInventorySha256());
        json.addProperty("store_histories", result.storeHistories());
        json.addProperty("store_revisions", result.storeRevisions());
        json.addProperty("journal_entries", result.journalEntries());
        json.addProperty("journal_bytes", result.journalBytes());
        json.addProperty("revisions_reclaimed", result.revisionsReclaimed());
        json.addProperty("index_terminal", result.indexTerminal());
        P4E3FixtureManifest.writeBounded(path, json);
    }

    private static Result readResult(Path path, Mode expected) throws IOException {
        var json = P4E3FixtureManifest.readBounded(path);
        if (!json.keySet().equals(RESULT_FIELDS)
                || P4E3FixtureManifest.exactInt(json, "schema_version") != 0
                || !P4E3FixtureManifest.exactString(json, "mode").equals(expected.token)) {
            throw new IOException("P4-E3 verified result fields changed");
        }
        return new Result(
                expected.token,
                P4E3FixtureManifest.exactString(json, "primary_sha256"),
                P4E3FixtureManifest.exactLong(json, "primary_bytes"),
                P4E3FixtureManifest.exactLong(json, "primary_modified_millis"),
                P4E3FixtureManifest.exactString(json, "playerdata_inventory_sha256"),
                P4E3FixtureManifest.exactInt(json, "store_histories"),
                P4E3FixtureManifest.exactInt(json, "store_revisions"),
                P4E3FixtureManifest.exactInt(json, "journal_entries"),
                P4E3FixtureManifest.exactInt(json, "journal_bytes"),
                P4E3FixtureManifest.exactInt(json, "revisions_reclaimed"),
                P4E3FixtureManifest.exactString(json, "index_terminal"));
    }

    private static boolean exactBoolean(JsonObject json, String field) throws IOException {
        var value = json.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IOException("P4-E3 report boolean is absent: " + field);
        }
        return value.getAsBoolean();
    }

    private static void requireSha256(String value) throws IOException {
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IOException("P4-E3 report checksum is malformed");
        }
    }

    enum Mode {
        FIRST("first"),
        RESTART("restart");

        private final String token;

        Mode(String token) {
            this.token = token;
        }
    }

    record Verification(
            String mode,
            int histories,
            int revisions,
            int journalEntries,
            int journalBytes,
            int revisionsReclaimed,
            String indexTerminal,
            String primaryWitness) {
        String line() {
            String marker = switch (mode) {
                case "first" -> "P4_E3_FIRST_VERIFIED";
                case "restart" -> "P4_E3_RESTART_VERIFIED";
                default -> throw new IllegalStateException("Unexpected verification mode: " + mode);
            };
            return marker
                    + " histories=" + histories
                    + " revisions=" + revisions
                    + " journal_entries=" + journalEntries
                    + " journal_bytes=" + journalBytes
                    + " reclaimed=" + revisionsReclaimed
                    + " terminal=" + indexTerminal
                    + " primary=" + primaryWitness;
        }
    }

    private record RuntimeObservation(
            String mode,
            long sessionToken,
            int auditInvocations,
            String auditVariant,
            long auditGeneration,
            int completeConsumeInvocations,
            int snapshotInvocations,
            String snapshotVariant,
            int completeRootCount,
            int reclaimInvocations,
            String reclaimVariant,
            int historiesScanned,
            int revisionsScanned,
            int historiesChanged,
            int revisionsReclaimed,
            boolean dirtyBefore,
            boolean dirtyAfter,
            int indexTerminalObservations,
            String indexTerminal,
            long indexGeneration,
            String primarySha256,
            long primaryBytes,
            long primaryModifiedMillis) {
    }

    private record DiskFacts(
            int histories,
            int revisions,
            int journalEntries,
            int journalBytes,
            String primarySha256,
            long primaryBytes,
            long primaryModifiedMillis) {
    }

    private record Inventory(
            int entries,
            int relevant,
            int oldFiles,
            long compressedTotal,
            long compressedMaximum,
            String sha256) {
    }

    private record Result(
            String mode,
            String primarySha256,
            long primaryBytes,
            long primaryModifiedMillis,
            String playerdataInventorySha256,
            int storeHistories,
            int storeRevisions,
            int journalEntries,
            int journalBytes,
            int revisionsReclaimed,
            String indexTerminal) {
    }
}
