package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.magic.definition.player.P4D3PlayerProbe;
import com.yo1no.gramarye.magic.definition.player.P4E0ResearchAttachmentFixtures;
import com.yo1no.gramarye.magic.definition.store.P4D3StoreJournalFixture;
import com.yo1no.gramarye.magic.definition.store.P4D3ProbeSupport;
import com.yo1no.gramarye.magic.definition.store.P4E0ResearchGzipAdapter;
import com.yo1no.gramarye.magic.definition.store.P4E0R2QStoreJournalFixtures;
import com.yo1no.gramarye.magic.definition.store.SkillRetentionRootSnapshot;
import com.yo1no.gramarye.magic.definition.submission.P4D3SubmissionProbe;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

/** CLI and dedicated coordinator for the bounded, explicitly non-formal R2Q-A smoke. */
public final class P4E0R2QMain {
    private static final int SMOKE_SCHEMA_VERSION = 0;
    private static final int IMPLEMENTATION_SCHEMA_VERSION =
            P4E0R2QStudyIdentity.CURRENT_IMPLEMENTATION_SCHEMA_VERSION;
    private static final int MAXIMUM_CONTROL_BYTES = 131_072;
    private static final int MAXIMUM_RESULT_BYTES = 8_192;
    private static final long SMOKE_WIRE_LIMIT = 1_048_576L;
    private static final String AUTHORITY = "EXPLORATORY_NON_NORMATIVE_R2Q_A_SMOKE";
    private static final String PROFILE_CONTROL = "r2q-profile-control-v0.json";
    private static final String CASE_PLAN_CONTROL = "r2q-case-plan-control-v0.json";
    private static final String EXACT_FIXTURE_CONTROL = "r2q-exact-fixture-control-v0.json";
    private static final String WIRE_FIXTURE = "r2q-reduced-wire-v0.dat";
    private static final String STANDALONE_RESULT = "standalone-smoke-v0.json";
    private static final String DEDICATED_RESULT = "dedicated-smoke-v0.json";
    private static final Set<String> RESULT_FIELDS = Set.of(
            "schema_version",
            "authority",
            "mode",
            "profile_name",
            "profile_manifest_sha256",
            "case_plan_sha256",
            "fixture_root_sha256",
            "exact_fixture_control_sha256",
            "research_implementation_schema_version",
            "case_count",
            "counter_case_count",
            "wire_sha256",
            "wire_physical_bytes",
            "wire_decompressed_bytes",
            "wire_modified_utf8_bytes",
            "strict_gzip_verified",
            "qualification_checkpoint_smoke",
            "profile_arithmetic_preflight",
            "negative_manifest_preflight",
            "exact_store_journal_root_preflight",
            "exact_d2_prospective_observed",
            "actual_d2_submissions",
            "formal_children_started",
            "exact_profile_materialized",
            "result");
    private static final Set<String> EXACT_FIXTURE_FIELDS = Set.of(
            "schema_version",
            "current_store_bytes",
            "current_histories",
            "current_revisions",
            "owner_count",
            "current_journal_entries",
            "current_journal_bytes",
            "prospective_histories",
            "prospective_revisions",
            "prospective_journal_entries",
            "prospective_journal_bytes",
            "latest_roots",
            "equipped_roots",
            "journal_roots",
            "exact_raw_roots",
            "over_raw_roots",
            "exact_roots_complete",
            "over_roots_rejected",
            "attachment_admissions",
            "mixed_family_admissions",
            "minimal_ready_admissions",
            "current_store_checksum",
            "primary_physical_bytes",
            "primary_sha256");

    private P4E0R2QMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "usage: prepare|verify-profile|run-smoke|verify-smoke"
                            + " <fixture-root> <report-root> <synthetic-world-root>");
        }
        SharedConstants.tryDetectVersion();
        var fixtureRoot = boundedRoot(Path.of(args[1]));
        var reportRoot = boundedRoot(Path.of(args[2]));
        var syntheticWorldRoot = boundedRoot(Path.of(args[3]));
        switch (args[0]) {
            case "prepare" -> prepare(fixtureRoot, reportRoot, syntheticWorldRoot);
            case "verify-profile" -> verifyProfile(fixtureRoot, syntheticWorldRoot, true);
            case "run-smoke" -> runStandaloneSmoke(
                    fixtureRoot, reportRoot, syntheticWorldRoot);
            case "verify-smoke" -> verifySmoke(fixtureRoot, reportRoot, syntheticWorldRoot);
            default -> throw new IllegalArgumentException("unknown R2Q-A smoke command");
        }
    }

    /** Called only by the isolated research GameTest holder on the server logic thread. */
    public static void runDedicatedSmoke(
            MinecraftServer server, Path fixturePath, Path reportPath) throws IOException {
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("R2Q-A dedicated smoke requires the server thread");
        }
        var fixtureRoot = boundedRoot(fixturePath);
        var reportRoot = boundedRoot(reportPath);
        var worldRoot = server.getWorldPath(
                net.minecraft.world.level.storage.LevelResource.ROOT);
        verifyProfile(fixtureRoot, worldRoot, true);
        var wire = scanReducedWire(fixtureRoot);
        runExactActualSubmission(server);
        writeResult(
                reportRoot.resolve(DEDICATED_RESULT),
                result("dedicated", fixtureRoot, wire, 1));
    }

    private static void prepare(
            Path fixtureRoot, Path reportRoot, Path syntheticWorldRoot) throws IOException {
        Files.createDirectories(fixtureRoot);
        Files.createDirectories(reportRoot);
        Files.createDirectories(syntheticWorldRoot);
        requireDirectory(fixtureRoot);
        requireDirectory(reportRoot);
        deleteOwned(fixtureRoot.resolve(PROFILE_CONTROL));
        deleteOwned(fixtureRoot.resolve(CASE_PLAN_CONTROL));
        deleteOwned(fixtureRoot.resolve(EXACT_FIXTURE_CONTROL));
        deleteOwned(fixtureRoot.resolve(WIRE_FIXTURE));
        deleteOwned(reportRoot.resolve(STANDALONE_RESULT));
        deleteOwned(reportRoot.resolve(DEDICATED_RESULT));

        P4E0ResearchRunRecord.atomicCreate(
                fixtureRoot.resolve(PROFILE_CONTROL), P4E0R2QProfile.manifestText());
        P4E0ResearchRunRecord.atomicCreate(
                fixtureRoot.resolve(CASE_PLAN_CONTROL),
                P4E0R2QCasePlan.standard().canonicalJson() + System.lineSeparator());
        var exact = P4E0R2QStoreJournalFixtures.buildExact();
        var admissions = exact.admissionFacts();
        exact.writePrimary(syntheticWorldRoot);
        P4E0ResearchRunRecord.atomicCreate(
                fixtureRoot.resolve(EXACT_FIXTURE_CONTROL),
                exactFixtureControl(exact.facts(), admissions, syntheticWorldRoot));
        exact.retainAtPeak();
        writeReducedWire(fixtureRoot.resolve(WIRE_FIXTURE));
        verifyProfile(fixtureRoot, syntheticWorldRoot, true);
    }

    private static void verifyProfile(
            Path fixtureRoot, Path syntheticWorldRoot, boolean requireInitialPrimary)
            throws IOException {
        var profileText = readBounded(
                fixtureRoot.resolve(PROFILE_CONTROL), MAXIMUM_CONTROL_BYTES);
        var caseText = readBounded(
                fixtureRoot.resolve(CASE_PLAN_CONTROL), MAXIMUM_CONTROL_BYTES);
        if (!profileText.equals(P4E0R2QProfile.manifestText())
                || !caseText.equals(
                        P4E0R2QCasePlan.standard().canonicalJson()
                                + System.lineSeparator())) {
            throw new IOException("R2Q-A control publication differs from its locked source");
        }
        requireExactFixtureControl(fixtureRoot, syntheticWorldRoot, requireInitialPrimary);
        requireProfileArithmetic();
        scanReducedWire(fixtureRoot);
    }

    private static void runStandaloneSmoke(
            Path fixtureRoot, Path reportRoot, Path syntheticWorldRoot)
            throws IOException {
        verifyProfile(fixtureRoot, syntheticWorldRoot, true);
        var wire = scanReducedWire(fixtureRoot);
        writeResult(
                reportRoot.resolve(STANDALONE_RESULT),
                result("standalone", fixtureRoot, wire, 0));
    }

    private static void verifySmoke(
            Path fixtureRoot, Path reportRoot, Path syntheticWorldRoot) throws IOException {
        verifyProfile(fixtureRoot, syntheticWorldRoot, false);
        var standalone = readResult(reportRoot.resolve(STANDALONE_RESULT));
        var dedicated = readResult(reportRoot.resolve(DEDICATED_RESULT));
        requireResult(standalone, "standalone", fixtureRoot, 0);
        requireResult(dedicated, "dedicated", fixtureRoot, 1);
        for (var field : List.of(
                "profile_manifest_sha256",
                "case_plan_sha256",
                "fixture_root_sha256",
                "exact_fixture_control_sha256",
                "wire_sha256",
                "wire_physical_bytes",
                "wire_decompressed_bytes",
                "wire_modified_utf8_bytes")) {
            if (!standalone.get(field).equals(dedicated.get(field))) {
                throw new IOException("R2Q-A smoke modes disagree on bounded fixture evidence");
            }
        }
        requireNoFormalArtifacts(reportRoot.getParent(), fixtureRoot.getParent());
    }

    private static void requireProfileArithmetic() {
        var profile = P4E0R2QProfile.locked();
        var blueprint = P4E0R2QFixturePlan.locked();
        if (!profile.candidateValues().equals(blueprint.counters())
                || P4E0R2QProfile.Counter.values().length != 25
                || P4E0R2QCasePlan.standard().cases().size() != 29
                || blueprint.directory().totalEntries() != 4_096
                || blueprint.roots().rawClaims() != 65_536
                || blueprint.roots().overRawClaims() != 65_537
                || P4E0R2QFixturePlan.FIXED_FRAMING_BYTES
                                + P4E0R2QFixturePlan.PAYLOAD_BYTES
                        != profile.candidateValues().decompressedBytesTotal()
                || P4E0R2QFixturePlan.NON_PAYLOAD_BYTE_ARRAY_ELEMENTS
                                + P4E0R2QFixturePlan.PAYLOAD_BYTES
                        != profile.candidateValues().byteArrayElementsTotal()) {
            throw new IllegalStateException("R2Q-A locked arithmetic changed");
        }

        var plan = P4E0R2QCasePlan.standard();
        var counterCases = 0;
        var dataVersionCases = 0;
        for (var spec : plan.cases()) {
            if (spec.kind() == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE) {
                plan.preflightNegative(
                        spec, P4E0R2QFixturePlan.negativeFixture(spec));
                counterCases++;
            } else if (spec.kind() != P4E0R2QCasePlan.CaseKind.POSITIVE) {
                var preflight = plan.preflightDataVersion(
                        spec, P4E0R2QFixturePlan.dataVersionFixture(spec));
                if (preflight.expectedDfuInvocations() != 0
                        || preflight.firstFailureStage()
                                != P4E0R2QCasePlan.FailureStage.DATA_VERSION) {
                    throw new IllegalStateException("R2Q-A DataVersion control changed");
                }
                dataVersionCases++;
            }
        }
        if (counterCases != 25 || dataVersionCases != 3) {
            throw new IllegalStateException("R2Q-A case coverage changed");
        }

        var tuning = blueprint.compressed();
        if (tuning.tunedTotal() != profile.candidateValues().compressedBytesTotal()
                || tuning.files().get(tuning.perFileMaximumIndex()).targetPhysicalBytes()
                        != profile.candidateValues().compressedBytesPerFile()
                || sum(tuning.perFileOverrun())
                        != profile.candidateValues().compressedBytesTotal()
                || sum(tuning.aggregateOverrun())
                        != profile.candidateValues().compressedBytesTotal() + 1L) {
            throw new IllegalStateException("R2Q-A compressed-header arithmetic changed");
        }
    }

    private static long sum(List<P4E0R2QFixturePlan.HeaderTuning> files) {
        return files.stream()
                .mapToLong(P4E0R2QFixturePlan.HeaderTuning::targetPhysicalBytes)
                .sum();
    }

    private static P4E0ResearchWireNbt.WriteFacts writeReducedWire(Path path)
            throws IOException {
        return P4E0ResearchWireNbt.write(
                path,
                new P4E0ResearchWireNbt.HeaderOptions(17, 257, 31, true, 0x5a),
                Deflater.BEST_COMPRESSION,
                SMOKE_WIRE_LIMIT,
                SMOKE_WIRE_LIMIT,
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_INT);
                    output.writeUTF("DataVersion");
                    output.writeInt(3_955);
                    output.writeByte(Tag.TAG_STRING);
                    output.writeUTF("field\u0000\u0080\u0800\ud83d\ude00");
                    output.writeUTF("value\u0000\u07ff\u0800\ud83d\ude00");
                    output.writeByte(Tag.TAG_INT_ARRAY);
                    output.writeUTF("ints");
                    output.writeInt(4);
                    output.writeInt(1);
                    output.writeInt(2);
                    output.writeInt(3);
                    output.writeInt(4);
                    output.writeByte(Tag.TAG_LIST);
                    output.writeUTF("list");
                    output.writeByte(Tag.TAG_BYTE);
                    output.writeInt(3);
                    output.writeByte(5);
                    output.writeByte(6);
                    output.writeByte(7);
                    output.writeByte(Tag.TAG_END);
                });
    }

    private static WireFacts scanReducedWire(Path fixtureRoot) throws IOException {
        var path = fixtureRoot.resolve(WIRE_FIXTURE);
        var budget = new P4E0R2QAuditBudget();
        budget.requireJournalReady(true);
        budget.observeDirectoryEntries(1L);
        var scanned = P4E0ResearchWireNbt.scan(
                path, budget, P4E0R2QAuditBudget.SourceSelection.PRIMARY);
        var dfuProbe = new P4E0R2QAuditBudget.DfuInvocationProbe();
        var dataVersion = budget.observeDataVersion(scanned.dataVersion(), dfuProbe);
        var attachment = P4E0ResearchAttachmentFixtures.readyRootMax(false);
        budget.observeAttachmentAdmission(attachment.variant());
        var roots = budget.captureRawRoots(attachment.projectedRoots().orElseThrow());
        budget.requireStoreAudit(true);
        var audit = budget.facts();
        var strict = P4E0ResearchGzipAdapter.readWireDrain(
                path, SMOKE_WIRE_LIMIT, SMOKE_WIRE_LIMIT);
        if (scanned.physicalBytes() != strict.physicalFileBytes()
                || scanned.decompressedBytes() != strict.decompressedRootBytes()
                || scanned.nbt().modifiedUtf8Bytes() <= 0
                || scanned.nbt().maxContainerDepth() != 2
                || scanned.nbt().compoundCount() != 1
                || scanned.nbt().compoundEntryCount() != 4
                || scanned.nbt().listElementCount() != 3
                || scanned.nbt().intArrayElements() != 4
                || dataVersion.acceptedValue() != 3_955
                || dataVersion.dfuInvocations() != 0
                || !(roots instanceof SkillRetentionRootSnapshot.Complete complete)
                || complete.roots().size() != 320
                || audit.directoryEntries() != 1L
                || audit.relevantRecords() != 1L
                || audit.compressedBytes() != scanned.physicalBytes()
                || audit.decompressedBytes() != scanned.decompressedBytes()
                || audit.attachmentAdmissions() != 1L
                || audit.rawRootClaims() != 320L
                || !audit.journalReady()) {
            throw new IOException("R2Q-A reduced strict-wire observation changed");
        }
        return new WireFacts(
                scanned.sha256(),
                scanned.physicalBytes(),
                scanned.decompressedBytes(),
                scanned.nbt().modifiedUtf8Bytes());
    }

    private static void runExactActualSubmission(MinecraftServer server) {
        var owner = P4E0R2QStoreJournalFixtures.submissionOwner();
        var cookie = CommonListenerCookie.createInitial(
                new GameProfile(owner.value(), "p4e0-r2q-smoke"), false);
        var player = new ServerPlayer(
                server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
        P4E0ResearchAttachmentFixtures.installReAdmittedSubmissionDraft(player);
        var attachments = P4D3PlayerProbe.newService();
        try (var context = P4E0R2QStoreJournalFixtures.installExactSubmission(server)) {
            var current = P4D3ProbeSupport.observeLive(server);
            if (!current.journalReady()
                    || current.storeBytes() != P4E0R2QStoreJournalFixtures.CURRENT_STORE_BYTES
                    || current.histories() != P4E0R2QStoreJournalFixtures.CURRENT_HISTORIES
                    || current.revisions() != P4E0R2QStoreJournalFixtures.CURRENT_REVISIONS
                    || current.journalEntries()
                            != P4E0R2QStoreJournalFixtures.CURRENT_JOURNAL_ENTRIES
                    || current.journalBytes()
                            != P4E0R2QStoreJournalFixtures.CURRENT_JOURNAL_BYTES) {
                throw new AssertionError("R2Q-A exact current Store/journal changed");
            }
            var facts = P4D3SubmissionProbe.submitActual(
                    player,
                    attachments,
                    context.submissionPort(),
                    P4D3StoreJournalFixture.submissionSkillId(),
                    peak -> {
                        if (peak.warningCount() != 1
                                || peak.documentNodeCount() != 1
                                || peak.validatedNodeCount() != 1) {
                            throw new AssertionError(
                                    "R2Q-A reduced D2 peak counts changed: "
                                            + peak.warningCount() + '/'
                                            + peak.documentNodeCount() + '/'
                                            + peak.validatedNodeCount());
                        }
                    });
            if (!facts.target().equals(P4D3StoreJournalFixture.submissionTarget())
                    || facts.warningCount() != 1
                    || facts.stageCounts().rejectionMapping() != 0) {
                throw new AssertionError("R2Q-A exact actual D2 submission changed");
            }
            var prospective = P4D3ProbeSupport.observeLive(server);
            if (!prospective.journalReady()
                    || prospective.histories()
                            != P4E0R2QStoreJournalFixtures.PROSPECTIVE_HISTORIES
                    || prospective.revisions()
                            != P4E0R2QStoreJournalFixtures.PROSPECTIVE_REVISIONS
                    || prospective.journalEntries()
                            != P4E0R2QStoreJournalFixtures.PROSPECTIVE_JOURNAL_ENTRIES
                    || prospective.journalBytes()
                            != P4E0R2QStoreJournalFixtures.PROSPECTIVE_JOURNAL_BYTES
                    || prospective.rootCount()
                            != P4E0R2QStoreJournalFixtures.PROSPECTIVE_JOURNAL_ENTRIES
                    || !prospective.dirty()) {
                throw new AssertionError("R2Q-A actual D2 prospective publication changed");
            }
        }
    }

    private static String exactFixtureControl(
            P4E0R2QStoreJournalFixtures.Facts facts,
            P4E0ResearchAttachmentFixtures.AdmissionFacts admissions,
            Path syntheticWorldRoot)
            throws IOException {
        var primary = P4D3StoreJournalFixture.primary(syntheticWorldRoot);
        if (!Files.isRegularFile(primary, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(primary)) {
            throw new IOException("R2Q-A exact synthetic primary was not published");
        }
        var json = new JsonObject();
        json.addProperty("schema_version", 0);
        json.addProperty("current_store_bytes", facts.currentStoreBytes());
        json.addProperty("current_histories", facts.currentHistories());
        json.addProperty("current_revisions", facts.currentRevisions());
        json.addProperty("owner_count", facts.ownerCount());
        json.addProperty("current_journal_entries", facts.currentJournalEntries());
        json.addProperty("current_journal_bytes", facts.currentJournalBytes());
        json.addProperty("prospective_histories", facts.prospectiveHistories());
        json.addProperty("prospective_revisions", facts.prospectiveRevisions());
        json.addProperty("prospective_journal_entries", facts.prospectiveJournalEntries());
        json.addProperty("prospective_journal_bytes", facts.prospectiveJournalBytes());
        json.addProperty("latest_roots", facts.latestRoots());
        json.addProperty("equipped_roots", facts.equippedRoots());
        json.addProperty("journal_roots", facts.journalRoots());
        json.addProperty("exact_raw_roots", facts.exactRawRoots());
        json.addProperty("over_raw_roots", facts.overRawRoots());
        json.addProperty("exact_roots_complete", facts.exactRootsComplete());
        json.addProperty("over_roots_rejected", facts.overRootsRejected());
        json.addProperty("attachment_admissions", admissions.totalAdmissions());
        json.addProperty("mixed_family_admissions", admissions.mixedFamilyAdmissions());
        json.addProperty("minimal_ready_admissions", admissions.minimalReadyAdmissions());
        json.addProperty("current_store_checksum", facts.currentStoreChecksum());
        json.addProperty("primary_physical_bytes", Files.size(primary));
        json.addProperty("primary_sha256", P4E0ResearchHashing.sha256(primary));
        return json + System.lineSeparator();
    }

    private static void requireExactFixtureControl(
            Path fixtureRoot, Path syntheticWorldRoot, boolean requireInitialPrimary)
            throws IOException {
        var text = readBounded(
                fixtureRoot.resolve(EXACT_FIXTURE_CONTROL), MAXIMUM_CONTROL_BYTES);
        final JsonObject json;
        try {
            json = JsonParser.parseString(text.stripTrailing()).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("R2Q-A exact fixture control is malformed", exception);
        }
        if (!text.endsWith(System.lineSeparator())
                || !json.keySet().equals(EXACT_FIXTURE_FIELDS)
                || json.get("schema_version").getAsInt() != 0
                || json.get("current_store_bytes").getAsInt()
                        != P4E0R2QStoreJournalFixtures.CURRENT_STORE_BYTES
                || json.get("current_histories").getAsInt()
                        != P4E0R2QStoreJournalFixtures.CURRENT_HISTORIES
                || json.get("current_revisions").getAsInt()
                        != P4E0R2QStoreJournalFixtures.CURRENT_REVISIONS
                || json.get("owner_count").getAsInt()
                        != P4E0R2QStoreJournalFixtures.OWNER_COUNT
                || json.get("current_journal_entries").getAsInt()
                        != P4E0R2QStoreJournalFixtures.CURRENT_JOURNAL_ENTRIES
                || json.get("current_journal_bytes").getAsInt()
                        != P4E0R2QStoreJournalFixtures.CURRENT_JOURNAL_BYTES
                || json.get("prospective_histories").getAsInt()
                        != P4E0R2QStoreJournalFixtures.PROSPECTIVE_HISTORIES
                || json.get("prospective_revisions").getAsInt()
                        != P4E0R2QStoreJournalFixtures.PROSPECTIVE_REVISIONS
                || json.get("prospective_journal_entries").getAsInt()
                        != P4E0R2QStoreJournalFixtures.PROSPECTIVE_JOURNAL_ENTRIES
                || json.get("prospective_journal_bytes").getAsInt()
                        != P4E0R2QStoreJournalFixtures.PROSPECTIVE_JOURNAL_BYTES
                || json.get("latest_roots").getAsInt()
                        != P4E0R2QStoreJournalFixtures.LATEST_ROOTS
                || json.get("equipped_roots").getAsInt()
                        != P4E0R2QStoreJournalFixtures.EQUIPPED_ROOTS
                || json.get("journal_roots").getAsInt()
                        != P4E0R2QStoreJournalFixtures.JOURNAL_ROOTS
                || json.get("exact_raw_roots").getAsInt()
                        != P4E0R2QStoreJournalFixtures.RAW_ROOTS
                || json.get("over_raw_roots").getAsInt()
                        != P4E0R2QStoreJournalFixtures.RAW_ROOTS_OVER
                || !json.get("exact_roots_complete").getAsBoolean()
                || !json.get("over_roots_rejected").getAsBoolean()
                || json.get("attachment_admissions").getAsInt() != 1_024
                || json.get("mixed_family_admissions").getAsInt() != 1
                || json.get("minimal_ready_admissions").getAsInt() != 1_023
                || !json.get("current_store_checksum").getAsString()
                        .matches("[0-9a-f]{64}")) {
            throw new IOException("R2Q-A exact Store/journal/root control changed");
        }
        if (requireInitialPrimary) {
            var primary = P4D3StoreJournalFixture.primary(syntheticWorldRoot);
            if (!Files.isRegularFile(primary, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(primary)
                    || Files.size(primary) != json.get("primary_physical_bytes").getAsLong()
                    || !P4E0ResearchHashing.sha256(primary).equals(
                            json.get("primary_sha256").getAsString())) {
                throw new IOException("R2Q-A exact synthetic primary changed before smoke");
            }
        }
    }

    private static JsonObject result(
            String mode, Path fixtureRoot, WireFacts wire, int actualD2Submissions)
            throws IOException {
        var profileHash = P4E0R2QProfile.manifestHash();
        var planHash = P4E0R2QCasePlan.standard().planHash();
        var fixtureHash = fixtureRootHash(fixtureRoot, wire);
        var json = new JsonObject();
        json.addProperty("schema_version", SMOKE_SCHEMA_VERSION);
        json.addProperty("authority", AUTHORITY);
        json.addProperty("mode", mode);
        json.addProperty("profile_name", P4E0R2QProfile.PROFILE_NAME);
        json.addProperty("profile_manifest_sha256", profileHash);
        json.addProperty("case_plan_sha256", planHash);
        json.addProperty("fixture_root_sha256", fixtureHash);
        json.addProperty(
                "exact_fixture_control_sha256",
                P4E0ResearchHashing.sha256(fixtureRoot.resolve(EXACT_FIXTURE_CONTROL)));
        json.addProperty(
                "research_implementation_schema_version", IMPLEMENTATION_SCHEMA_VERSION);
        json.addProperty("case_count", P4E0R2QCasePlan.CASE_COUNT);
        json.addProperty("counter_case_count", P4E0R2QProfile.COUNTER_COUNT);
        json.addProperty("wire_sha256", wire.sha256());
        json.addProperty("wire_physical_bytes", wire.physicalBytes());
        json.addProperty("wire_decompressed_bytes", wire.decompressedBytes());
        json.addProperty("wire_modified_utf8_bytes", wire.modifiedUtf8Bytes());
        json.addProperty("strict_gzip_verified", true);
        json.addProperty("qualification_checkpoint_smoke", true);
        json.addProperty("profile_arithmetic_preflight", true);
        json.addProperty("negative_manifest_preflight", true);
        json.addProperty("exact_store_journal_root_preflight", true);
        json.addProperty("exact_d2_prospective_observed", actualD2Submissions == 1);
        json.addProperty("actual_d2_submissions", actualD2Submissions);
        json.addProperty("formal_children_started", 0);
        json.addProperty("exact_profile_materialized", false);
        json.addProperty("result", "COMPLETED_NON_FORMAL_SMOKE");
        return json;
    }

    private static String fixtureRootHash(Path fixtureRoot, WireFacts wire)
            throws IOException {
        var profile = P4E0ResearchHashing.sha256(fixtureRoot.resolve(PROFILE_CONTROL));
        var casesFile = P4E0ResearchHashing.sha256(fixtureRoot.resolve(CASE_PLAN_CONTROL));
        var exactFixture = P4E0ResearchHashing.sha256(
                fixtureRoot.resolve(EXACT_FIXTURE_CONTROL));
        var cases = P4E0R2QCasePlan.standard().planHash();
        if (!profile.equals(P4E0R2QProfile.manifestHash())
                || !casesFile.equals(P4E0ResearchHashing.sha256(
                        P4E0R2QCasePlan.standard().canonicalJson()
                                + System.lineSeparator()))) {
            throw new IOException("R2Q-A control hashes changed");
        }
        return P4E0ResearchHashing.sha256(
                IMPLEMENTATION_SCHEMA_VERSION + "\n" + profile + "\n" + cases + "\n"
                        + exactFixture + "\n"
                        + wire.sha256() + "\n" + wire.physicalBytes() + "\n"
                        + wire.decompressedBytes() + "\n");
    }

    private static void writeResult(Path path, JsonObject result) throws IOException {
        var text = result.toString() + System.lineSeparator();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_RESULT_BYTES) {
            throw new IOException("R2Q-A smoke result exceeded its bound");
        }
        deleteOwned(path);
        P4E0ResearchRunRecord.atomicCreate(path, text);
    }

    private static JsonObject readResult(Path path) throws IOException {
        var text = readBounded(path, MAXIMUM_RESULT_BYTES);
        if (!text.endsWith(System.lineSeparator())
                || text.indexOf('\r') >= 0
                || text.substring(0, text.length() - System.lineSeparator().length())
                        .contains("\n")) {
            throw new IOException("R2Q-A smoke result framing is not canonical");
        }
        try {
            var json = JsonParser.parseString(text.stripTrailing()).getAsJsonObject();
            if (!json.keySet().equals(RESULT_FIELDS)) {
                throw new IOException("R2Q-A smoke result fields changed");
            }
            return json;
        } catch (RuntimeException exception) {
            throw new IOException("R2Q-A smoke result is malformed", exception);
        }
    }

    private static void requireResult(
            JsonObject json, String mode, Path fixtureRoot, int actualD2Submissions)
            throws IOException {
        var wire = scanReducedWire(fixtureRoot);
        var expected = result(mode, fixtureRoot, wire, actualD2Submissions);
        if (!json.equals(expected)) {
            throw new IOException("R2Q-A smoke result differs from recomputed evidence");
        }
    }

    private static void requireNoFormalArtifacts(Path... ownedRoots) throws IOException {
        var forbidden = Set.of(
                "runs.jsonl",
                "r2q-profile.json",
                "r2q-case-plan.json",
                "summary.md",
                "PROVENANCE.txt",
                "SHA256SUMS.txt");
        for (var ownedRoot : ownedRoots) {
            if (ownedRoot == null || !Files.exists(ownedRoot, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try (var paths = Files.walk(ownedRoot)) {
                if (paths.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .anyMatch(forbidden::contains)) {
                    throw new IOException(
                            "R2Q-A must not publish formal qualification evidence");
                }
            }
        }
    }

    private static Path boundedRoot(Path path) throws IOException {
        var normalized = path.toAbsolutePath().normalize();
        if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(normalized)
                        || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("R2Q-A root is not a real directory");
        }
        return normalized;
    }

    private static void requireDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new IOException("R2Q-A output root is unavailable");
        }
    }

    private static String readBounded(Path path, int maximumBytes) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)
                || Files.size(path) > maximumBytes) {
            throw new IOException("R2Q-A bounded file is unavailable");
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void deleteOwned(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("R2Q-A refuses to replace a symbolic link");
        }
        Files.deleteIfExists(path);
    }

    private record WireFacts(
            String sha256, long physicalBytes, long decompressedBytes, long modifiedUtf8Bytes) {
        private WireFacts {
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}")
                    || physicalBytes <= 0 || decompressedBytes <= 0
                    || modifiedUtf8Bytes <= 0) {
                throw new IllegalArgumentException("invalid R2Q-A reduced wire facts");
            }
        }
    }
}
