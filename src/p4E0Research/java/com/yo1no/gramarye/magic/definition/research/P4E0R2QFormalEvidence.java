package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yo1no.gramarye.magic.definition.store.P4E0R2QStoreJournalFixtures;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Bounded formal controls, case evidence, archives, and all-or-nothing publication. */
final class P4E0R2QFormalEvidence {
    static final int CONTROL_SCHEMA_VERSION = 0;
    static final int MAXIMUM_CONTROL_BYTES = 65_536;
    static final int MAXIMUM_TEXT_ARTIFACT_BYTES = 262_144;
    static final long FORMAL_DISK_BUDGET_BYTES = 12_884_901_888L;
    static final int FORMAL_HEAP_MIB = 1_536;
    static final String LOCKED_PROFILE_HASH =
            "6a6f4541f4c23b9aefad465eb29ec0420d3a4f635f06b528ca07239a93f99418";
    static final String LOCKED_CASE_PLAN_HASH =
            "23408739f292d2a5696c56c39b8b4b3978b3840af383930293efbe6b824f5035";

    static final String RUNS_FILE = "runs.jsonl";
    static final String PROFILE_FILE = "r2q-profile.json";
    static final String CASE_PLAN_FILE = "r2q-case-plan.json";
    static final String SUMMARY_FILE = "summary.md";
    static final String PROVENANCE_FILE = "PROVENANCE.txt";
    static final String CHECKSUMS_FILE = "SHA256SUMS.txt";
    static final String MACOS_METADATA_FILE = ".DS_Store";
    static final Set<String> OFFICIAL_FILES = Set.of(
            RUNS_FILE,
            PROFILE_FILE,
            CASE_PLAN_FILE,
            SUMMARY_FILE,
            PROVENANCE_FILE,
            CHECKSUMS_FILE);

    enum OfficialOutputClassification {
        ABSENT,
        EMPTY_OR_METADATA_ONLY,
        VALID_OFFICIAL_SET,
        MALFORMED_NONEMPTY_OUTPUT
    }

    record OfficialOutputInspection(
            OfficialOutputClassification classification,
            Optional<StudyControl> validatedControl) {
        OfficialOutputInspection {
            Objects.requireNonNull(classification, "classification");
            Objects.requireNonNull(validatedControl, "validatedControl");
            if ((classification == OfficialOutputClassification.VALID_OFFICIAL_SET)
                    != validatedControl.isPresent()) {
                throw new IllegalArgumentException(
                        "only valid official output may carry a validated control");
            }
        }
    }

    private P4E0R2QFormalEvidence() {
    }

    static StudyControl createControl(String gitHead, String gitTree, long diskBudget) {
        if (!P4E0R2QProfile.manifestHash().equals(LOCKED_PROFILE_HASH)
                || !P4E0R2QCasePlan.standard().planHash().equals(LOCKED_CASE_PLAN_HASH)) {
            throw new IllegalStateException("compiled R2Q lock hashes changed");
        }
        if (diskBudget != FORMAL_DISK_BUDGET_BYTES) {
            throw new IllegalArgumentException("formal disk budget differs from the lock");
        }
        var fixtureRootHash = fixtureRootHash();
        var runOrderHash = formalRunOrderHash();
        var identity = P4E0R2QStudyIdentity.calculateFormal(
                gitHead,
                gitTree,
                LOCKED_PROFILE_HASH,
                LOCKED_CASE_PLAN_HASH,
                fixtureRootHash,
                runOrderHash,
                P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION,
                FORMAL_HEAP_MIB,
                diskBudget);
        return new StudyControl(
                identity.studyId(),
                gitHead,
                gitTree,
                LOCKED_PROFILE_HASH,
                LOCKED_CASE_PLAN_HASH,
                fixtureRootHash,
                runOrderHash,
                P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION,
                FORMAL_HEAP_MIB,
                diskBudget);
    }

    static String formalRunOrderHash() {
        var canonical = new StringBuilder();
        for (var spec : P4E0R2QCasePlan.standard().cases()) {
            canonical.append(String.format(
                    java.util.Locale.ROOT,
                    "%02d|%s|prepareP4E0R2QCase%02d|runP4E0R2QCase%02d|verifyP4E0R2QCase%02d\n",
                    spec.index(), spec.caseId(), spec.index(), spec.index(), spec.index()));
        }
        return P4E0ResearchHashing.sha256(canonical.toString());
    }

    static String fixtureRootHash() {
        return FixtureIdentity.HASH;
    }

    private static String computeFixtureRootHash() {
        var blueprint = P4E0R2QFixturePlan.locked();
        var canonical = new StringBuilder()
                .append("fixture_schema=0\n")
                .append("profile=").append(LOCKED_PROFILE_HASH).append('\n')
                .append("case_plan=").append(LOCKED_CASE_PLAN_HASH).append('\n');
        for (var counter : P4E0R2QProfile.Counter.values()) {
            canonical.append(counter.slug()).append('=')
                    .append(blueprint.counters().value(counter)).append('\n');
        }
        var directory = blueprint.directory();
        canonical.append("directory_selected=").append(directory.selectedPrimaries()).append('\n')
                .append("directory_ignored=").append(directory.ignoredOldOrIrrelevant()).append('\n')
                .append("directory_ready=").append(directory.readyAdmissions()).append('\n')
                .append("directory_without_attachment=")
                .append(directory.selectedWithoutGramaryeAttachment()).append('\n');
        var roots = blueprint.roots();
        canonical.append("roots_latest=").append(roots.latestClaims()).append('\n')
                .append("roots_equipped=").append(roots.equippedClaims()).append('\n')
                .append("roots_journal=").append(roots.journalClaims()).append('\n')
                .append("roots_raw=").append(roots.rawClaims()).append('\n')
                .append("roots_over=").append(roots.overRawClaims()).append('\n');
        var records = blueprint.jointRecords().records();
        var tuning = blueprint.compressed().files();
        for (var index = 0; index < records.size(); index++) {
            var record = records.get(index);
            var header = tuning.get(index);
            var options = header.headerOptions();
            canonical.append("record[").append(index).append("]=")
                    .append(record.code()).append('|');
            appendRecordFacts(canonical, record.facts());
                    canonical.append('|').append(record.canonicalPhysicalBytes())
                    .append('|').append(header.targetPhysicalBytes())
                    .append('|').append(header.fileNameBytes())
                    .append('|').append(options.extraBytes())
                    .append('|').append(options.commentBytes())
                    .append('|').append(options.fhcrc())
                    .append('|').append(options.repeatedByte()).append('\n');
            canonical.append("source[").append(index).append("]=")
                    .append(String.format(java.util.Locale.ROOT, "%04d.dat", index))
                    .append(":PRIMARY_SELECTED|")
                    .append(String.format(java.util.Locale.ROOT, "%04d.dat_old", index))
                    .append(":OLD_IGNORED_PRIMARY_PRESENT\n");
        }
        for (var spec : P4E0R2QCasePlan.standard().cases()) {
            if (spec.kind() == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE) {
                var negative = P4E0R2QFixturePlan.negativeFixture(spec);
                var derivation = negative.derivation();
                var binding = negative.physicalBinding();
                canonical.append("negative[").append(spec.index()).append("]=")
                        .append(spec.caseId()).append('|')
                        .append(derivation.targetCounter().name()).append('|')
                        .append(derivation.mutationKind().name()).append('|')
                        .append(derivation.mechanism().name()).append('|')
                        .append(binding.fixtureKind().name()).append('|')
                        .append(binding.mutationPlacement().name()).append('|')
                        .append("FULL_2048_RECORD_PHYSICAL_REBUILD|")
                        .append("PRIMARY_OLD_SOURCE_SELECTION|")
                        .append("STRICT_SINGLE_MEMBER_FHCRC_TUNING\n");
                for (var delta : derivation.physicalDeltas()) {
                    canonical.append("delta=").append(delta.counter().name()).append(':')
                            .append(delta.amount()).append('\n');
                }
                for (var compensation : binding.compensations()) {
                    canonical.append("compensation=")
                            .append(compensation.counter().name()).append(':')
                            .append(compensation.expectedDelta()).append(':')
                            .append(compensation.mechanism().name()).append(':')
                            .append(compensation.placement().name()).append('\n');
                }
            } else if (spec.kind() != P4E0R2QCasePlan.CaseKind.POSITIVE) {
                var dataVersion = P4E0R2QFixturePlan.dataVersionFixture(spec);
                canonical.append("data_version[").append(spec.index()).append("]=")
                        .append(spec.caseId()).append('|')
                        .append(dataVersion.mutationKind().name()).append('|')
                        .append(dataVersion.proofKind().name()).append('|')
                        .append(dataVersion.resultingState().name()).append('|')
                        .append(dataVersion.resultingIntValue()).append('\n');
            }
            canonical.append("case_physical_plan[").append(spec.index()).append("]=")
                    .append(P4E0R2QFormalWorkload.expectedCaseFixtureChecksum(spec))
                    .append('\n');
        }
        canonical.append("store_bytes=")
                .append(P4E0R2QStoreJournalFixtures.CURRENT_STORE_BYTES).append('\n')
                .append("store_histories=")
                .append(P4E0R2QStoreJournalFixtures.CURRENT_HISTORIES).append('\n')
                .append("store_revisions=")
                .append(P4E0R2QStoreJournalFixtures.CURRENT_REVISIONS).append('\n')
                .append("store_checksum=")
                .append(P4E0R2QStoreJournalFixtures.CURRENT_STORE_CHECKSUM).append('\n')
                .append("journal_current_entries=")
                .append(P4E0R2QStoreJournalFixtures.CURRENT_JOURNAL_ENTRIES).append('\n')
                .append("journal_current_bytes=")
                .append(P4E0R2QStoreJournalFixtures.CURRENT_JOURNAL_BYTES).append('\n')
                .append("journal_prospective_entries=")
                .append(P4E0R2QStoreJournalFixtures.PROSPECTIVE_JOURNAL_ENTRIES).append('\n')
                .append("journal_prospective_bytes=")
                .append(P4E0R2QStoreJournalFixtures.PROSPECTIVE_JOURNAL_BYTES).append('\n')
                .append("raw_roots=").append(P4E0R2QStoreJournalFixtures.RAW_ROOTS)
                .append('\n')
                .append("raw_roots_over=")
                .append(P4E0R2QStoreJournalFixtures.RAW_ROOTS_OVER).append('\n');
        return P4E0ResearchHashing.sha256(canonical.toString());
    }

    private static final class FixtureIdentity {
        private static final String HASH = computeFixtureRootHash();

        private FixtureIdentity() {
        }
    }

    private static void appendRecordFacts(
            StringBuilder canonical, P4E0R2QJointRecords.RecordFacts facts) {
        canonical.append(facts.decompressedBytes()).append(',')
                .append(facts.containerDepth()).append(',')
                .append(facts.compoundContainers()).append(',')
                .append(facts.compoundFieldEntries()).append(',')
                .append(facts.listElements()).append(',')
                .append(facts.byteArrayElements()).append(',')
                .append(facts.intArrayElements()).append(',')
                .append(facts.longArrayElements()).append(',')
                .append(facts.modifiedUtf8Bytes()).append(',')
                .append(facts.scalarTags());
    }

    static void writeControl(Path path, StudyControl control) throws IOException {
        writeNewBounded(path, control.toJsonLine(), MAXIMUM_CONTROL_BYTES, true);
    }

    static StudyControl readControl(Path path) throws IOException {
        var text = readBounded(path, MAXIMUM_CONTROL_BYTES);
        if (!text.endsWith("\n") || text.indexOf('\r') >= 0
                || text.substring(0, text.length() - 1).contains("\n")) {
            throw new IOException("formal study control framing changed");
        }
        final JsonObject json;
        try {
            json = JsonParser.parseString(text.stripTrailing()).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("formal study control is malformed");
        }
        var fields = Set.of(
                "schema_version", "study_id", "git_head", "git_tree", "profile_hash",
                "case_plan_hash", "fixture_root_hash", "run_order_hash",
                "implementation_schema_version", "heap_mib", "disk_budget_bytes");
        if (!json.keySet().equals(fields)
                || json.get("schema_version").getAsInt() != CONTROL_SCHEMA_VERSION) {
            throw new IOException("formal study control field set changed");
        }
        try {
            var control = new StudyControl(
                    json.get("study_id").getAsString(),
                    json.get("git_head").getAsString(),
                    json.get("git_tree").getAsString(),
                    json.get("profile_hash").getAsString(),
                    json.get("case_plan_hash").getAsString(),
                    json.get("fixture_root_hash").getAsString(),
                    json.get("run_order_hash").getAsString(),
                    json.get("implementation_schema_version").getAsInt(),
                    json.get("heap_mib").getAsInt(),
                    json.get("disk_budget_bytes").getAsLong());
            if (!control.toJsonLine().equals(text)) {
                throw new IOException("formal study control is not canonical");
            }
            return control;
        } catch (RuntimeException exception) {
            throw new IOException("formal study control values are invalid");
        }
    }

    static Path caseDirectory(Path workRoot, int caseIndex) {
        if (caseIndex < 0 || caseIndex >= P4E0R2QCasePlan.CASE_COUNT) {
            throw new IllegalArgumentException("formal case index is outside plan");
        }
        return safeRoot(workRoot).resolve(String.format(
                java.util.Locale.ROOT, "cases/%02d", caseIndex));
    }

    static void writeResult(Path path, P4E0R2QFormalResult result) throws IOException {
        writeNewBounded(
                path, result.toJsonLine(), P4E0R2QFormalResult.MAXIMUM_JSON_BYTES, true);
    }

    static P4E0R2QFormalResult readResult(Path path) throws IOException {
        return P4E0R2QFormalResult.parseLine(
                readBounded(path, P4E0R2QFormalResult.MAXIMUM_JSON_BYTES));
    }

    static void requireSuccessfulSet(List<P4E0R2QFormalResult> input) throws IOException {
        var results = List.copyOf(Objects.requireNonNull(input, "input"));
        if (results.size() != P4E0R2QCasePlan.CASE_COUNT) {
            throw new IOException("formal aggregation does not contain 29 results");
        }
        String studyId = null;
        var ids = new HashSet<String>();
        var process = new java.util.EnumMap<
                P4E0R2QFormalResult.ProcessClassification, Integer>(
                        P4E0R2QFormalResult.ProcessClassification.class);
        var qualification = new java.util.EnumMap<
                P4E0R2QFormalResult.QualificationResult, Integer>(
                        P4E0R2QFormalResult.QualificationResult.class);
        for (var value : P4E0R2QFormalResult.ProcessClassification.values()) {
            process.put(value, 0);
        }
        for (var value : P4E0R2QFormalResult.QualificationResult.values()) {
            qualification.put(value, 0);
        }
        for (var index = 0; index < results.size(); index++) {
            var result = results.get(index);
            var spec = P4E0R2QCasePlan.standard().cases().get(index);
            if (result.caseIndex() != index || !result.caseId().equals(spec.caseId())
                    || !ids.add(result.caseId())) {
                throw new IOException("formal result order or identity changed");
            }
            if (studyId == null) {
                studyId = result.studyId();
            } else if (!studyId.equals(result.studyId())) {
                throw new IOException("formal aggregation mixes study identities");
            }
            process.compute(result.processClassification(), (ignored, count) -> count + 1);
            qualification.compute(result.qualificationResult(), (ignored, count) -> count + 1);
        }
        if (process.get(P4E0R2QFormalResult.ProcessClassification.COMPLETED) != 29
                || process.entrySet().stream().anyMatch(entry ->
                        entry.getKey()
                                        != P4E0R2QFormalResult.ProcessClassification.COMPLETED
                                && entry.getValue() != 0)
                || qualification.get(
                                P4E0R2QFormalResult.QualificationResult.ADMITTED_EXACT)
                        != 1
                || qualification.get(
                                P4E0R2QFormalResult.QualificationResult
                                        .REJECTED_EXPECTED_COUNTER)
                        != 25
                || qualification.get(
                                P4E0R2QFormalResult.QualificationResult
                                        .REJECTED_EXPECTED_DATA_VERSION)
                        != 3
                || qualification.get(
                                P4E0R2QFormalResult.QualificationResult.NOT_OBSERVED)
                        != 0) {
            throw new IOException("formal process/qualification totals do not pass");
        }
    }

    static OfficialOutputInspection inspectOfficialOutput(Path officialRoot)
            throws IOException {
        var official = safeRoot(officialRoot);
        if (!Files.exists(official, LinkOption.NOFOLLOW_LINKS)) {
            return new OfficialOutputInspection(
                    OfficialOutputClassification.ABSENT, Optional.empty());
        }
        if (!Files.isDirectory(official, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(official)) {
            return new OfficialOutputInspection(
                    OfficialOutputClassification.MALFORMED_NONEMPTY_OUTPUT,
                    Optional.empty());
        }
        var names = directoryEntryNames(official);
        if (names.isEmpty()
                || (names.equals(Set.of(MACOS_METADATA_FILE))
                        && isRegularNonSymlink(official.resolve(MACOS_METADATA_FILE)))) {
            return new OfficialOutputInspection(
                    OfficialOutputClassification.EMPTY_OR_METADATA_ONLY,
                    Optional.empty());
        }
        if (!names.equals(OFFICIAL_FILES) || !hasExactOfficialFileShape(official)) {
            return new OfficialOutputInspection(
                    OfficialOutputClassification.MALFORMED_NONEMPTY_OUTPUT,
                    Optional.empty());
        }
        try {
            return new OfficialOutputInspection(
                    OfficialOutputClassification.VALID_OFFICIAL_SET,
                    Optional.of(readOfficialControl(official)));
        } catch (IOException exception) {
            return new OfficialOutputInspection(
                    OfficialOutputClassification.MALFORMED_NONEMPTY_OUTPUT,
                    Optional.empty());
        }
    }

    static void removeEmptyOrMetadataOnlyOfficial(
            Path repositoryRoot,
            Path officialRoot,
            OfficialOutputInspection expectedInspection) throws IOException {
        var repository = safeRoot(repositoryRoot);
        var official = safeRoot(officialRoot);
        var expectedOwnedRoot = repository.resolve("build/reports/p4-e0-r2q").normalize();
        if (!official.equals(expectedOwnedRoot)
                || expectedInspection.classification()
                        != OfficialOutputClassification.EMPTY_OR_METADATA_ONLY) {
            throw new IOException("formal metadata cleanup is outside its owned build root");
        }
        var current = inspectOfficialOutput(official);
        if (current.classification()
                != OfficialOutputClassification.EMPTY_OR_METADATA_ONLY) {
            throw new IOException("formal metadata-only output changed before cleanup");
        }
        var metadata = official.resolve(MACOS_METADATA_FILE);
        if (Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(metadata);
        }
        Files.delete(official);
    }

    static void aggregateAndPublish(
            Path workRoot,
            Path officialRoot,
            StudyControl control,
            PublicationMover mover) throws IOException {
        var results = loadVerifiedResults(workRoot, control);
        requireSuccessfulSet(results);
        var official = safeRoot(officialRoot);
        if (Files.exists(official, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("official R2Q evidence already exists");
        }
        var parent = Objects.requireNonNull(official.getParent(), "official parent");
        Files.createDirectories(parent);
        var staging = parent.resolve(".p4-e0-r2q-" + control.studyId() + ".staging");
        if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("formal publication staging directory already exists");
        }
        Files.createDirectory(staging);
        var completed = false;
        try {
            writeNewBounded(staging.resolve(RUNS_FILE), runs(results),
                    Math.multiplyExact(P4E0R2QFormalResult.MAXIMUM_JSON_BYTES, 29), false);
            writeNewBounded(staging.resolve(PROFILE_FILE),
                    P4E0R2QProfile.manifestJson() + "\n", MAXIMUM_CONTROL_BYTES, false);
            writeNewBounded(staging.resolve(CASE_PLAN_FILE),
                    P4E0R2QCasePlan.standard().canonicalJson() + "\n",
                    MAXIMUM_CONTROL_BYTES, false);
            writeNewBounded(staging.resolve(SUMMARY_FILE), summary(results, control),
                    MAXIMUM_TEXT_ARTIFACT_BYTES, false);
            writeNewBounded(staging.resolve(PROVENANCE_FILE), provenance(control),
                    MAXIMUM_CONTROL_BYTES, false);
            writeNewBounded(staging.resolve(CHECKSUMS_FILE), checksums(staging),
                    MAXIMUM_CONTROL_BYTES, false);
            requireOfficialDirectory(staging, control);
            if (Files.exists(official, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("official R2Q evidence appeared before publication");
            }
            mover.move(staging, official);
            requireOfficialDirectory(official, control);
            completed = true;
        } finally {
            if (!completed && Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
                deleteTree(staging);
            }
        }
    }

    static void verifyOfficial(Path officialRoot, StudyControl control) throws IOException {
        requireOfficialDirectory(safeRoot(officialRoot), control);
    }

    static Path archiveStaleOfficial(
            Path officialRoot, Path staleRoot, String archiveIdentity) throws IOException {
        var official = safeRoot(officialRoot);
        if (!Files.isDirectory(official, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(official)) {
            throw new IOException("stale formal evidence root is unavailable");
        }
        var previous = readOfficialControl(official);
        requireOfficialDirectory(official, previous);
        var identity = boundedIdentity(archiveIdentity);
        if (!identity.equals(previous.studyId()) && !identity.equals(previous.gitHead())) {
            throw new IOException("stale archive identity differs from validated provenance");
        }
        var target = safeRoot(staleRoot).resolve(identity);
        Files.createDirectories(target.getParent());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("stale formal evidence archive already exists");
        }
        var staging = target.getParent().resolve("." + identity + ".staging");
        if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("stale formal archive staging already exists");
        }
        atomicMove(official, staging);
        atomicMove(
                staging.resolve(CHECKSUMS_FILE),
                staging.resolve("OFFICIAL_SHA256SUMS.txt"));
        var provenance = "schema_version=0\narchive_kind=STALE\nidentity=" + identity
                + "\narchived_at=" + Instant.now() + "\n";
        writeNewBounded(staging.resolve("STALE_PROVENANCE.txt"), provenance,
                MAXIMUM_CONTROL_BYTES, true);
        writeNewBounded(staging.resolve(CHECKSUMS_FILE), archiveChecksums(staging),
                MAXIMUM_CONTROL_BYTES, true);
        requireStaleDirectory(staging, previous, identity);
        atomicMove(staging, target);
        return target;
    }

    static void requireNoFormalStaging(Path officialRoot, Path staleRoot, Path failedRoot)
            throws IOException {
        var officialParent = safeRoot(officialRoot).getParent();
        if (officialParent != null && Files.isDirectory(
                officialParent, LinkOption.NOFOLLOW_LINKS)) {
            try (var entries = Files.newDirectoryStream(officialParent)) {
                for (var entry : entries) {
                    var name = entry.getFileName().toString();
                    if (name.startsWith(".p4-e0-r2q-") && name.endsWith(".staging")) {
                        throw new IOException("formal publication staging residue exists");
                    }
                }
            }
        }
        for (var root : List.of(safeRoot(staleRoot), safeRoot(failedRoot))) {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(root)) {
                throw new IOException("formal evidence archive root is invalid");
            }
            try (var entries = Files.newDirectoryStream(root)) {
                for (var entry : entries) {
                    if (entry.getFileName().toString().endsWith(".staging")) {
                        throw new IOException("formal archive staging residue exists");
                    }
                }
            }
        }
    }

    private static void requireStaleDirectory(
            Path directory, StudyControl previous, String identity) throws IOException {
        var expected = new HashSet<>(OFFICIAL_FILES);
        expected.remove(CHECKSUMS_FILE);
        expected.add("OFFICIAL_SHA256SUMS.txt");
        expected.add("STALE_PROVENANCE.txt");
        expected.add(CHECKSUMS_FILE);
        try (var stream = Files.newDirectoryStream(directory)) {
            var observed = new HashSet<String>();
            for (var path : stream) {
                requireHashableArchiveFile(directory, path);
                observed.add(path.getFileName().toString());
            }
            if (!observed.equals(expected)) {
                throw new IOException("stale formal archive file set changed");
            }
        }
        if (!readProvenanceControl(directory).equals(previous)) {
            throw new IOException("stale formal archive identity changed");
        }
        var stale = readBounded(directory.resolve("STALE_PROVENANCE.txt"),
                MAXIMUM_CONTROL_BYTES);
        if (!stale.startsWith(
                "schema_version=0\narchive_kind=STALE\nidentity=" + identity
                        + "\narchived_at=")) {
            throw new IOException("stale formal archive provenance changed");
        }
        if (!readBounded(directory.resolve(CHECKSUMS_FILE), MAXIMUM_CONTROL_BYTES)
                .equals(archiveChecksums(directory))) {
            throw new IOException("stale formal archive checksums changed");
        }
    }

    static Path preserveFailed(
            Path workRoot, Path failedRoot, StudyControl control, String code)
            throws IOException {
        var source = safeRoot(workRoot);
        var target = safeRoot(failedRoot).resolve(control.studyId());
        Files.createDirectories(target.getParent());
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(source)
                || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("failed evidence cannot be preserved without overwrite");
        }
        var staging = target.getParent().resolve("." + control.studyId() + ".staging");
        if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("failed evidence staging directory already exists");
        }
        Files.createDirectory(staging);
        var moved = false;
        try {
            writeNewBounded(
                    staging.resolve("study-control.json"),
                    control.toJsonLine(),
                    MAXIMUM_CONTROL_BYTES,
                    true);
            for (var index = 0; index < P4E0R2QCasePlan.CASE_COUNT; index++) {
                var sourceCase = caseDirectory(source, index);
                var targetCase = caseDirectory(staging, index);
                preserveManifestIfPresent(sourceCase, targetCase, control, index);
                preserveResultIfPresent(sourceCase, targetCase, control, index, "child-result.json");
                preserveResultIfPresent(
                        sourceCase, targetCase, control, index, "verified-result.json");
                preserveResultIfPresent(
                        sourceCase, targetCase, control, index, "prepare-failure.json");
                preserveMarkerIfPresent(
                        sourceCase, targetCase, "running.marker", 4_096,
                        Set.of("RUNNING\n", "COMPLETED\n", "FAILED\n"));
                preserveMarkerIfPresent(
                        sourceCase, targetCase, "timeout.marker", 4_096,
                        Set.of("TIMEOUT\n"));
                preservePatternIfPresent(
                        sourceCase, targetCase, "exit-code.txt", 64,
                        "-?[0-9]{1,10}\\n");
                preservePatternIfPresent(
                        sourceCase, targetCase, "parent-deadline.marker", 128,
                        "deadline_epoch_millis=[1-9][0-9]{0,18}\\n");
            }
            var failure = "schema_version=0\narchive_kind=FAILED\nstudy_id="
                    + control.studyId() + "\ncode=" + vocabulary(code) + "\n";
            writeNewBounded(staging.resolve("FAILURE.txt"), failure,
                    MAXIMUM_CONTROL_BYTES, true);
            writeNewBounded(staging.resolve(CHECKSUMS_FILE), archiveChecksums(staging),
                    MAXIMUM_CONTROL_BYTES, true);
            atomicMove(staging, target);
            moved = true;
        } finally {
            if (!moved && Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
                deleteTree(staging);
            }
        }
        deleteTree(source);
        return target;
    }

    static void writeForcedMarker(Path path, String content) throws IOException {
        var bytes = content.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length == 0 || bytes.length > 4_096) {
            throw new IOException("formal marker is outside its bound");
        }
        Files.createDirectories(path.getParent());
        try (var channel = FileChannel.open(
                path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    static void writeBoundedSmokeResults(Path path, String content) throws IOException {
        writeNewBounded(path, content, P4E0R2QFormalResult.MAXIMUM_JSON_BYTES, true);
    }

    static boolean exactMarker(Path path, String expected) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            return false;
        }
        var text = readBounded(path, 4_096);
        return StandardCharsets.US_ASCII.newEncoder().canEncode(text)
                && text.equals(expected);
    }

    static long ownedBytes(Path root, long stopAfter) throws IOException {
        var safe = safeRoot(root);
        if (!Files.exists(safe, LinkOption.NOFOLLOW_LINKS)) {
            return 0L;
        }
        var total = 0L;
        try (var stream = Files.walk(safe)) {
            var iterator = stream.iterator();
            while (iterator.hasNext()) {
                var path = iterator.next();
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("formal owned tree contains a symbolic link");
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    total = Math.addExact(total, Files.size(path));
                    if (total > stopAfter) {
                        return total;
                    }
                }
            }
        }
        return total;
    }

    static void requireDiskBudget(Path root, long projectedAdditionalBytes) throws IOException {
        if (projectedAdditionalBytes < 0) {
            throw new IllegalArgumentException("negative formal disk projection");
        }
        var current = ownedBytes(root, FORMAL_DISK_BUDGET_BYTES + 1L);
        if (Math.addExact(current, projectedAdditionalBytes) > FORMAL_DISK_BUDGET_BYTES) {
            throw new ResearchGuardException();
        }
        var existing = Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                ? root : Objects.requireNonNull(root.getParent(), "formal root parent");
        Files.createDirectories(existing);
        FileStore store = Files.getFileStore(existing);
        if (store.getUsableSpace() < projectedAdditionalBytes) {
            throw new ResearchGuardException();
        }
    }

    static final class ResearchGuardException extends IOException {
        private ResearchGuardException() {
            super("formal research guard rejected the projected disk use");
        }
    }

    interface PublicationMover {
        void move(Path source, Path target) throws IOException;
    }

    static PublicationMover atomicDirectoryMover() {
        return P4E0R2QFormalEvidence::atomicMove;
    }

    record StudyControl(
            String studyId,
            String gitHead,
            String gitTree,
            String profileHash,
            String casePlanHash,
            String fixtureRootHash,
            String runOrderHash,
            int implementationSchemaVersion,
            int heapMiB,
            long diskBudgetBytes) {
        StudyControl {
            requireSha256(studyId, "studyId");
            requireGit(gitHead, "gitHead");
            requireGit(gitTree, "gitTree");
            requireSha256(profileHash, "profileHash");
            requireSha256(casePlanHash, "casePlanHash");
            requireSha256(fixtureRootHash, "fixtureRootHash");
            requireSha256(runOrderHash, "runOrderHash");
            if (!profileHash.equals(LOCKED_PROFILE_HASH)
                    || !casePlanHash.equals(LOCKED_CASE_PLAN_HASH)
                    || !fixtureRootHash.equals(P4E0R2QFormalEvidence.fixtureRootHash())
                    || !runOrderHash.equals(P4E0R2QFormalEvidence.formalRunOrderHash())
                    || implementationSchemaVersion
                            != P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION
                    || heapMiB != FORMAL_HEAP_MIB
                    || diskBudgetBytes != FORMAL_DISK_BUDGET_BYTES) {
                throw new IllegalArgumentException("formal study control tuple changed");
            }
            var recomputed = P4E0R2QStudyIdentity.calculateFormal(
                    gitHead,
                    gitTree,
                    profileHash,
                    casePlanHash,
                    fixtureRootHash,
                    runOrderHash,
                    implementationSchemaVersion,
                    heapMiB,
                    diskBudgetBytes);
            if (!studyId.equals(recomputed.studyId())) {
                throw new IllegalArgumentException("formal study id does not match its tuple");
            }
        }

        String toJsonLine() {
            var json = new JsonObject();
            json.addProperty("schema_version", CONTROL_SCHEMA_VERSION);
            json.addProperty("study_id", studyId);
            json.addProperty("git_head", gitHead);
            json.addProperty("git_tree", gitTree);
            json.addProperty("profile_hash", profileHash);
            json.addProperty("case_plan_hash", casePlanHash);
            json.addProperty("fixture_root_hash", fixtureRootHash);
            json.addProperty("run_order_hash", runOrderHash);
            json.addProperty("implementation_schema_version", implementationSchemaVersion);
            json.addProperty("heap_mib", heapMiB);
            json.addProperty("disk_budget_bytes", diskBudgetBytes);
            return json + "\n";
        }
    }

    private static List<P4E0R2QFormalResult> loadVerifiedResults(
            Path workRoot, StudyControl control) throws IOException {
        var results = new ArrayList<P4E0R2QFormalResult>();
        for (var index = 0; index < P4E0R2QCasePlan.CASE_COUNT; index++) {
            var path = caseDirectory(workRoot, index).resolve("verified-result.json");
            var result = readResult(path);
            var manifestChecksum = P4E0R2QFormalWorkload.readVerifiedManifestChecksum(
                    caseDirectory(workRoot, index), control, index);
            if (!result.hasFormalIdentity(control)
                    || !result.caseFixtureChecksum().equals(manifestChecksum)) {
                throw new IOException("formal result identity differs from study control");
            }
            results.add(result);
        }
        return List.copyOf(results);
    }

    private static String runs(List<P4E0R2QFormalResult> results) {
        var text = new StringBuilder();
        results.forEach(result -> text.append(result.toJsonLine()));
        return text.toString();
    }

    private static String summary(
            List<P4E0R2QFormalResult> results, StudyControl control) {
        var positive = results.getFirst();
        var text = new StringBuilder()
                .append("# P4-E0-R2Q qualification evidence\n\n")
                .append("EXPLORATORY_NON_NORMATIVE\n\n")
                .append("- candidate: ").append(P4E0R2QProfile.PROFILE_NAME).append('\n')
                .append("- study_id: ").append(control.studyId()).append('\n')
                .append("- process COMPLETED: 29\n")
                .append("- qualification ADMITTED_EXACT: 1\n")
                .append("- qualification REJECTED_EXPECTED_COUNTER: 25\n")
                .append("- qualification REJECTED_EXPECTED_DATA_VERSION: 3\n")
                .append("- qualification NOT_OBSERVED: 0\n\n")
                .append("## Positive metrics\n\n");
        for (var counter : P4E0R2QProfile.Counter.values()) {
            text.append("- ").append(counter.slug()).append(": ")
                    .append(positive.observedCounters().value(counter)).append('\n');
        }
        text.append("- dfu_invocations: ").append(positive.dfuInvocations()).append('\n')
                .append("- attachment_admissions: ")
                .append(positive.attachmentAdmissions()).append('\n')
                .append("- raw_root_claims: ").append(positive.rawRootClaims()).append('\n')
                .append("- targets_audited: ").append(positive.targetsAudited()).append('\n')
                .append("- reclaim_invocations: ")
                .append(positive.reclaimInvocations()).append('\n')
                .append("- elapsed_millis: ").append(positive.elapsedMillis()).append("\n\n")
                .append("## Heap diagnostics\n\n");
        for (var result : results) {
            text.append("- ").append(result.caseId())
                    .append(": xms=").append(result.heap().xms())
                    .append(", xmx=").append(result.heap().xmx())
                    .append(", initial_committed=")
                    .append(result.heap().initialCommitted())
                    .append(", sampled_peak_used=")
                    .append(result.heap().sampledPeakUsed())
                    .append(", heap_pool_peak_sum=")
                    .append(result.heap().heapPoolPeakSum())
                    .append(", elapsed_millis=").append(result.elapsedMillis()).append('\n');
        }
        text.append("\n## Counter rejection mappings\n\n");
        results.stream().filter(result -> result.qualificationResult()
                        == P4E0R2QFormalResult.QualificationResult.REJECTED_EXPECTED_COUNTER)
                .forEach(result -> text.append("- ").append(result.caseId()).append(": ")
                        .append(result.observedFailureCode()).append(" @ ")
                        .append(result.observedStage()).append('\n'));
        text.append("\n## DataVersion controls\n\n");
        results.stream().filter(result -> result.qualificationResult()
                        == P4E0R2QFormalResult.QualificationResult
                                .REJECTED_EXPECTED_DATA_VERSION)
                .forEach(result -> text.append("- ").append(result.caseId()).append(": ")
                        .append(result.observedFailureCode()).append('\n'));
        text.append("\n## Limitations\n\n")
                .append("This is exploratory qualification evidence. It does not establish ")
                .append("authority approval, a production limit, a heap-safety minimum, or ")
                .append("P4-E0-B readiness.\n");
        return text.toString();
    }

    private static String provenance(StudyControl control) {
        return "schema_version=0\n"
                + "authority=EXPLORATORY_NON_NORMATIVE\n"
                + "profile_name=" + P4E0R2QProfile.PROFILE_NAME + "\n"
                + "study_id=" + control.studyId() + "\n"
                + "git_head=" + control.gitHead() + "\n"
                + "git_tree=" + control.gitTree() + "\n"
                + "profile_hash=" + control.profileHash() + "\n"
                + "case_plan_hash=" + control.casePlanHash() + "\n"
                + "fixture_root_hash=" + control.fixtureRootHash() + "\n"
                + "run_order_hash=" + control.runOrderHash() + "\n"
                + "implementation_schema_version=" + control.implementationSchemaVersion()
                + "\nheap_mib=" + control.heapMiB()
                + "\ndisk_budget_bytes=" + control.diskBudgetBytes() + "\n";
    }

    static StudyControl readOfficialControl(Path directory) throws IOException {
        var official = safeRoot(directory);
        if (!hasExactOfficialFileShape(official)) {
            throw new IOException("official formal evidence file set is incomplete");
        }
        var control = readProvenanceControl(official);
        requireOfficialDirectory(official, control);
        return control;
    }

    private static StudyControl readProvenanceControl(Path directory) throws IOException {
        var text = readBounded(directory.resolve(PROVENANCE_FILE), MAXIMUM_CONTROL_BYTES);
        if (!text.endsWith("\n") || text.indexOf('\r') >= 0) {
            throw new IOException("formal provenance framing changed");
        }
        var values = new HashMap<String, String>();
        for (var line : text.substring(0, text.length() - 1).split("\n", -1)) {
            var split = line.indexOf('=');
            if (split <= 0 || split == line.length() - 1
                    || values.putIfAbsent(line.substring(0, split), line.substring(split + 1))
                            != null) {
                throw new IOException("formal provenance fields are malformed");
            }
        }
        var expected = Set.of(
                "schema_version", "authority", "profile_name", "study_id", "git_head",
                "git_tree", "profile_hash", "case_plan_hash", "fixture_root_hash",
                "run_order_hash", "implementation_schema_version", "heap_mib",
                "disk_budget_bytes");
        if (!values.keySet().equals(expected)
                || !values.get("schema_version").equals("0")
                || !values.get("authority").equals("EXPLORATORY_NON_NORMATIVE")
                || !values.get("profile_name").equals(P4E0R2QProfile.PROFILE_NAME)) {
            throw new IOException("formal provenance schema changed");
        }
        try {
            return new StudyControl(
                    values.get("study_id"), values.get("git_head"), values.get("git_tree"),
                    values.get("profile_hash"), values.get("case_plan_hash"),
                    values.get("fixture_root_hash"), values.get("run_order_hash"),
                    Integer.parseInt(values.get("implementation_schema_version")),
                    Integer.parseInt(values.get("heap_mib")),
                    Long.parseLong(values.get("disk_budget_bytes")));
        } catch (RuntimeException exception) {
            throw new IOException("formal provenance values are invalid");
        }
    }

    private static String checksums(Path directory) throws IOException {
        var names = new ArrayList<>(OFFICIAL_FILES);
        names.remove(CHECKSUMS_FILE);
        names.removeIf(name -> !Files.isRegularFile(
                directory.resolve(name), LinkOption.NOFOLLOW_LINKS));
        names.sort(String::compareTo);
        var text = new StringBuilder();
        for (var name : names) {
            var path = directory.resolve(name);
            requireHashableFile(path, maximumOfficialFileBytes(name));
            text.append(P4E0ResearchHashing.sha256(path))
                    .append("  ").append(name).append('\n');
        }
        return text.toString();
    }

    private static String archiveChecksums(Path directory) throws IOException {
        var entries = new ArrayList<Path>();
        try (var paths = Files.walk(directory)) {
            for (var path : paths.toList()) {
                if (path.equals(directory)) {
                    continue;
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(path)) {
                    continue;
                }
                if (path.getFileName().toString().equals(CHECKSUMS_FILE)) {
                    continue;
                }
                requireHashableArchiveFile(directory, path);
                entries.add(path);
            }
        }
        entries.sort(Comparator.comparing(path -> directory.relativize(path).toString()));
        var text = new StringBuilder();
        for (var entry : entries) {
            var relative = directory.relativize(entry).toString().replace('\\', '/');
            if (relative.contains("\n") || relative.contains("\r")) {
                throw new IOException("archive evidence path is not bounded text");
            }
            text.append(P4E0ResearchHashing.sha256(entry))
                    .append("  ").append(relative).append('\n');
        }
        return text.toString();
    }

    static int maximumOfficialFileBytes(String name) throws IOException {
        return switch (name) {
            case RUNS_FILE -> Math.multiplyExact(
                    P4E0R2QFormalResult.MAXIMUM_JSON_BYTES,
                    P4E0R2QCasePlan.CASE_COUNT);
            case SUMMARY_FILE -> MAXIMUM_TEXT_ARTIFACT_BYTES;
            case PROFILE_FILE, CASE_PLAN_FILE, PROVENANCE_FILE, CHECKSUMS_FILE ->
                    MAXIMUM_CONTROL_BYTES;
            default -> throw new IOException("unknown official formal artifact");
        };
    }

    private static void requireHashableArchiveFile(Path root, Path path) throws IOException {
        var relative = root.relativize(path).toString().replace('\\', '/');
        final int maximum;
        if (relative.equals(RUNS_FILE)) {
            maximum = maximumOfficialFileBytes(RUNS_FILE);
        } else if (relative.equals(SUMMARY_FILE)) {
            maximum = MAXIMUM_TEXT_ARTIFACT_BYTES;
        } else if (relative.matches(
                        "cases/[0-2][0-9]/(?:child-result|verified-result|prepare-failure)\\.json")
                || relative.matches("cases/[0-2][0-9]/case-manifest\\.json")) {
            maximum = P4E0R2QFormalResult.MAXIMUM_JSON_BYTES;
        } else if (relative.matches("cases/[0-2][0-9]/.*\\.marker")
                || relative.matches("cases/[0-2][0-9]/exit-code\\.txt")) {
            maximum = 4_096;
        } else if (relative.matches("cases/[0-2][0-9]/.*\\.status")
                || relative.equals("study-control.json")
                || relative.equals(PROFILE_FILE)
                || relative.equals(CASE_PLAN_FILE)
                || relative.equals(PROVENANCE_FILE)
                || relative.equals(CHECKSUMS_FILE)
                || relative.equals("OFFICIAL_SHA256SUMS.txt")
                || relative.equals("STALE_PROVENANCE.txt")
                || relative.equals("FAILURE.txt")) {
            maximum = MAXIMUM_CONTROL_BYTES;
        } else {
            throw new IOException("archive contains an unapproved evidence file");
        }
        requireHashableFile(path, maximum);
    }

    private static void requireHashableFile(Path path, int maximumBytes) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)
                || Files.size(path) > maximumBytes) {
            throw new IOException("formal evidence is not safe to hash");
        }
    }

    private static void preserveManifestIfPresent(
            Path sourceCase,
            Path targetCase,
            StudyControl control,
            int caseIndex) throws IOException {
        var source = sourceCase.resolve("case-manifest.json");
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            P4E0R2QFormalWorkload.readVerifiedManifestChecksum(
                    sourceCase, control, caseIndex);
            writeNewBounded(
                    targetCase.resolve("case-manifest.json"),
                    readBounded(source, P4E0R2QFormalResult.MAXIMUM_JSON_BYTES),
                    P4E0R2QFormalResult.MAXIMUM_JSON_BYTES,
                    true);
        } catch (IOException | RuntimeException exception) {
            preserveRejectedFacts(
                    source,
                    targetCase.resolve("case-manifest.status"),
                    P4E0R2QFormalResult.MAXIMUM_JSON_BYTES);
        }
    }

    private static void preserveResultIfPresent(
            Path sourceCase,
            Path targetCase,
            StudyControl control,
            int caseIndex,
            String name) throws IOException {
        var source = sourceCase.resolve(name);
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            var result = readResult(source);
            if (!result.hasFormalIdentity(control) || result.caseIndex() != caseIndex) {
                throw new IOException("formal failed result identity changed");
            }
            writeResult(targetCase.resolve(name), result);
        } catch (IOException | RuntimeException exception) {
            preserveRejectedFacts(
                    source,
                    targetCase.resolve(name + ".status"),
                    P4E0R2QFormalResult.MAXIMUM_JSON_BYTES);
        }
    }

    private static void preserveMarkerIfPresent(
            Path sourceCase,
            Path targetCase,
            String name,
            int maximumBytes,
            Set<String> accepted) throws IOException {
        var source = sourceCase.resolve(name);
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            var text = readBounded(source, maximumBytes);
            if (!accepted.contains(text)) {
                throw new IOException("formal marker vocabulary changed");
            }
            writeNewBounded(targetCase.resolve(name), text, maximumBytes, true);
        } catch (IOException | RuntimeException exception) {
            preserveRejectedFacts(
                    source, targetCase.resolve(name + ".status"), maximumBytes);
        }
    }

    private static void preservePatternIfPresent(
            Path sourceCase,
            Path targetCase,
            String name,
            int maximumBytes,
            String pattern) throws IOException {
        var source = sourceCase.resolve(name);
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            var text = readBounded(source, maximumBytes);
            if (!text.matches(pattern)) {
                throw new IOException("formal marker framing changed");
            }
            writeNewBounded(targetCase.resolve(name), text, maximumBytes, true);
        } catch (IOException | RuntimeException exception) {
            preserveRejectedFacts(
                    source, targetCase.resolve(name + ".status"), maximumBytes);
        }
    }

    private static void preserveRejectedFacts(
            Path source, Path target, int maximumBytes) throws IOException {
        final String status;
        final long observedAtLeast;
        final String checksum;
        if (Files.isSymbolicLink(source)) {
            status = "SYMLINK";
            observedAtLeast = 0L;
            checksum = "NONE";
        } else if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            status = "NON_REGULAR";
            observedAtLeast = 0L;
            checksum = "NONE";
        } else if (Files.size(source) > maximumBytes) {
            status = "OVERSIZE";
            observedAtLeast = Math.addExact((long) maximumBytes, 1L);
            checksum = "NONE";
        } else {
            status = "MALFORMED";
            observedAtLeast = Files.size(source);
            checksum = P4E0ResearchHashing.sha256(source);
        }
        var facts = "schema_version=0\nstatus=" + status
                + "\nobserved_at_least=" + observedAtLeast
                + "\nmaximum=" + maximumBytes
                + "\nsha256=" + checksum + "\n";
        writeNewBounded(target, facts, MAXIMUM_CONTROL_BYTES, true);
    }

    private static Set<String> directoryEntryNames(Path directory) throws IOException {
        var names = new HashSet<String>();
        try (var entries = Files.newDirectoryStream(directory)) {
            for (var entry : entries) {
                if (!names.add(entry.getFileName().toString())) {
                    throw new IOException("formal evidence directory contains duplicate entries");
                }
            }
        }
        return Set.copyOf(names);
    }

    private static boolean hasExactOfficialFileShape(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)
                || !directoryEntryNames(directory).equals(OFFICIAL_FILES)) {
            return false;
        }
        for (var name : OFFICIAL_FILES) {
            if (!isRegularNonSymlink(directory.resolve(name))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRegularNonSymlink(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path);
    }

    private static void requireOfficialDirectory(Path directory, StudyControl control)
            throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) {
            throw new IOException("official formal evidence directory is unavailable");
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            var observed = new HashSet<String>();
            for (var entry : entries) {
                var name = entry.getFileName().toString();
                if (!observed.add(name)) {
                    throw new IOException("official formal evidence has invalid entries");
                }
                requireHashableFile(entry, maximumOfficialFileBytes(name));
            }
            if (!observed.equals(OFFICIAL_FILES)) {
                throw new IOException("official formal evidence file set changed");
            }
        }
        var results = new ArrayList<P4E0R2QFormalResult>();
        var runs = readBounded(
                directory.resolve(RUNS_FILE),
                Math.multiplyExact(
                        P4E0R2QFormalResult.MAXIMUM_JSON_BYTES,
                        P4E0R2QCasePlan.CASE_COUNT));
        if (!runs.endsWith("\n") || runs.indexOf('\r') >= 0) {
            throw new IOException("official runs.jsonl framing changed");
        }
        for (var line : runs.substring(0, runs.length() - 1).split("\n", -1)) {
            if (line.isEmpty()) {
                throw new IOException("official runs.jsonl has an empty line");
            }
            results.add(P4E0R2QFormalResult.parseLine(line + "\n"));
            if (results.size() > P4E0R2QCasePlan.CASE_COUNT) {
                throw new IOException("official runs.jsonl has too many lines");
            }
        }
        requireSuccessfulSet(results);
        if (results.stream().anyMatch(result -> !result.hasFormalIdentity(control))) {
            throw new IOException("official result identity differs from provenance");
        }
        var expectedChecksums = checksums(directory);
        var observedChecksums = readBounded(directory.resolve(CHECKSUMS_FILE),
                MAXIMUM_CONTROL_BYTES);
        if (!expectedChecksums.equals(observedChecksums)
                || !readBounded(directory.resolve(PROVENANCE_FILE), MAXIMUM_CONTROL_BYTES)
                        .equals(provenance(control))
                || !readBounded(directory.resolve(PROFILE_FILE), MAXIMUM_CONTROL_BYTES)
                        .equals(P4E0R2QProfile.manifestJson() + "\n")
                || !readBounded(directory.resolve(CASE_PLAN_FILE), MAXIMUM_CONTROL_BYTES)
                        .equals(P4E0R2QCasePlan.standard().canonicalJson() + "\n")
                || !readBounded(directory.resolve(SUMMARY_FILE),
                                MAXIMUM_TEXT_ARTIFACT_BYTES)
                        .equals(summary(results, control))) {
            throw new IOException("official formal evidence hashes or controls changed");
        }
    }

    private static void writeNewBounded(
            Path path, String text, int maximumBytes, boolean force) throws IOException {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximumBytes || Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("bounded formal evidence cannot be created");
        }
        Files.createDirectories(path.getParent());
        try (var channel = FileChannel.open(
                path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            if (force) {
                channel.force(true);
            }
        }
    }

    private static String readBounded(Path path, int maximumBytes) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path) || maximumBytes < 0) {
            throw new IOException("bounded formal evidence file is unavailable");
        }
        var bytes = new ByteArrayOutputStream(Math.min(maximumBytes, 8_192));
        try (var input = Files.newInputStream(path, StandardOpenOption.READ)) {
            var buffer = new byte[Math.min(8_192, Math.addExact(maximumBytes, 1))];
            var remaining = Math.addExact(maximumBytes, 1);
            while (remaining > 0) {
                var read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                bytes.write(buffer, 0, read);
                remaining -= read;
            }
            if (bytes.size() > maximumBytes || input.read() >= 0) {
                throw new IOException("bounded formal evidence exceeds its byte ceiling");
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new IOException("bounded formal evidence is not UTF-8");
        }
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("formal evidence filesystem lacks atomic directory move");
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            var ordered = paths.sorted(Comparator.reverseOrder()).toList();
            for (var path : ordered) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("formal cleanup refuses symbolic links");
                }
                Files.delete(path);
            }
        }
    }

    private static Path safeRoot(Path root) {
        return Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    private static String boundedIdentity(String value) {
        Objects.requireNonNull(value, "archiveIdentity");
        if (!value.matches("[0-9a-f]{40}|[0-9a-f]{64}")) {
            throw new IllegalArgumentException("archive identity is not bounded");
        }
        return value;
    }

    private static String vocabulary(String value) {
        Objects.requireNonNull(value, "code");
        if (!value.matches("[A-Z][A-Z0-9_]{0,95}")) {
            throw new IllegalArgumentException("failure code is not bounded vocabulary");
        }
        return value;
    }

    private static void requireSha256(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " is not a SHA-256 digest");
        }
    }

    private static void requireGit(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException(label + " is not a Git object id");
        }
    }
}
