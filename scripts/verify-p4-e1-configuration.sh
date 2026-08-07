#!/bin/bash
set -euo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
MAIN_JAVA="$REPOSITORY_ROOT/src/main/java"
STORE_ROOT="$MAIN_JAVA/com/yo1no/gramarye/magic/definition/store"
PLAYER_ROOT="$MAIN_JAVA/com/yo1no/gramarye/magic/definition/player"
CEILINGS="$MAIN_JAVA/com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java"
BUDGET="$STORE_ROOT/P4E1AuditBudget.java"
HEAP_OBSERVATION="$STORE_ROOT/P4E1HeapFloorObservation.java"
PREFLIGHT="$STORE_ROOT/P4E1SourceAdmissionPreflight.java"
HEAP_CHILD="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorChildMatrixTest.java"
HEAP_PROBE="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorProbeMain.java"
HEAP_UNIT="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorObservationTest.java"
PREFLIGHT_UNIT="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceAdmissionPreflightTest.java"
PHASE_TYPES="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4EPhaseTypes.java"
E0_LEDGER="$REPOSITORY_ROOT/docs/architecture/P4-E0-root-audit-boundary.md"

fail() {
    echo "P4-E1 configuration verification failed: $1" >&2
    exit 1
}

grep_state() {
    local file=$1
    local token=$2
    set +e
    grep -F -q -- "$token" "$file" 2>/dev/null
    GREP_RESULT=$?
    set -e
}

require_fixed() {
    local file=$1
    local token=$2
    grep_state "$file" "$token"
    case "$GREP_RESULT" in
        0) ;;
        1) fail "required marker missing from $file: $token" ;;
        *) fail "grep tool error while reading $file" ;;
    esac
}

reject_fixed() {
    local file=$1
    local token=$2
    grep_state "$file" "$token"
    case "$GREP_RESULT" in
        0) fail "forbidden marker present in $file: $token" ;;
        1) ;;
        *) fail "grep tool error while reading $file" ;;
    esac
}

is_allowed_changed_path() {
    case "$1" in
        docs/architecture/P4-0-persistence-boundary.md | \
        docs/architecture/P4-E0-root-audit-boundary.md | \
        scripts/verify-p4-c2-a-configuration.sh | \
        scripts/verify-p4-c2-b-configuration.sh | \
        scripts/verify-p4-d3-a-configuration.sh | \
        scripts/verify-p4-d3-configuration.sh | \
        scripts/verify-p4-e0-r-configuration.sh | \
        scripts/verify-p4-e0-r2q-configuration.sh | \
        scripts/verify-p4-e1-configuration.sh | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentAdmission.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSerializer.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSourceObservation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditBudget.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditCounter.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditStage.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompressedCapacityRejected.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1FileMetadata.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1FileSystemAccess.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorObservation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorStatus.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1IntegratedSnapshotTraversal.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataDirectorySnapshot.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataFileReader.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataNbtScanner.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataSourceSelector.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceAdmissionPreflight.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceFailure.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/StrictSingleMemberGzipInput.java | \
        src/main/java/com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentAdmissionTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSourceObservationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchConfigurationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P3D1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P3D2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2PhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditBudgetTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorChildMatrixTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorObservationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorProbeMain.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1IntegratedSnapshotTraversalTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataDirectorySnapshotTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataFileReaderTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataNbtScannerTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataSourceSelectorTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceAdmissionPreflightTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1TestBudgets.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4EPhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/StrictSingleMemberGzipInputTest.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

verify_changed_paths() {
    local temporary tracked untracked path candidate staged_status
    temporary=$(mktemp -d "${TMPDIR:-/tmp}/gramarye-p4-e1-paths.XXXXXX")
    tracked="$temporary/tracked.bin"
    untracked="$temporary/untracked.bin"
    trap 'rm -rf -- "$temporary"' RETURN

    set +e
    git -C "$REPOSITORY_ROOT" diff --cached --quiet --no-ext-diff --
    staged_status=$?
    set -e
    case "$staged_status" in
        0) ;;
        1) fail "the P4-E1-A verifier requires an empty staged diff" ;;
        *) fail "git failed while checking the staged diff" ;;
    esac

    git -C "$REPOSITORY_ROOT" diff \
            --name-only --no-renames --no-ext-diff -z HEAD -- > "$tracked" \
        || fail "git failed while reading tracked E1-A changes"
    git -C "$REPOSITORY_ROOT" ls-files \
            --others --exclude-standard -z -- > "$untracked" \
        || fail "git failed while reading untracked E1-A changes"

    for inventory in "$tracked" "$untracked"; do
        while IFS= read -r -d '' path; do
            is_allowed_changed_path "$path" \
                || fail "changed path is outside the exact E1-A allowlist: $path"
            candidate="$REPOSITORY_ROOT/$path"
            [ -f "$candidate" ] \
                || fail "allowed E1-A path is missing or not a regular file: $path"
            [ ! -L "$candidate" ] \
                || fail "allowed E1-A path is a symlink: $path"
            case "$path" in
                scripts/*.sh)
                    [ -x "$candidate" ] \
                        || fail "allowed E1-A verifier is not executable: $path"
                    ;;
                *.java | *.md) ;;
                *) fail "allowed E1-A path has an unexpected file type: $path" ;;
            esac
        done < "$inventory"
    done

    rm -rf -- "$temporary"
    trap - RETURN
}

require_exact_count() {
    local file=$1
    local token=$2
    local expected=$3
    local actual
    actual=$(grep -F -c -- "$token" "$file") || {
        local status=$?
        if [ "$status" -eq 1 ]; then
            actual=0
        else
            fail "grep tool error while counting $file"
        fi
    }
    [ "$actual" -eq "$expected" ] \
        || fail "expected $expected occurrences in $file, found $actual: $token"
}

self_regression() {
    local temporary
    temporary=$(mktemp -d "${TMPDIR:-/tmp}/gramarye-p4-e1-verifier.XXXXXX")
    trap 'rm -rf -- "$temporary"' RETURN
    echo 'required-marker' > "$temporary/present.txt"

    grep_state "$temporary/present.txt" 'required-marker'
    [ "$GREP_RESULT" -eq 0 ] || fail "self-test could not classify present marker"
    grep_state "$temporary/present.txt" 'missing-marker'
    [ "$GREP_RESULT" -eq 1 ] || fail "self-test could not classify missing marker"
    grep_state "$temporary/missing-file.txt" 'required-marker'
    [ "$GREP_RESULT" -gt 1 ] || fail "self-test confused tool error with missing marker"

    grep_state "$temporary/present.txt" 'forbidden-marker'
    [ "$GREP_RESULT" -eq 1 ] || fail "self-test confused absent forbidden marker"
    echo 'forbidden-marker' >> "$temporary/present.txt"
    grep_state "$temporary/present.txt" 'forbidden-marker'
    [ "$GREP_RESULT" -eq 0 ] || fail "self-test could not classify forbidden marker"

    is_allowed_changed_path \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditBudget.java' \
        || fail "self-test rejected an exact allowed E1-A path"
    if is_allowed_changed_path 'build.gradle'; then
        fail "self-test accepted a forbidden Gradle path"
    fi
    if is_allowed_changed_path 'src/main/resources/gramarye/e1.json'; then
        fail "self-test accepted a forbidden production resource path"
    fi
    if is_allowed_changed_path \
            'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditBudgetExtra.java'; then
        fail "self-test accepted a prefix-near production path"
    fi
    if is_allowed_changed_path 'logs/latest.log'; then
        fail "self-test accepted a repository-root runtime log"
    fi
    if git -C "$temporary" diff --name-only --no-renames -z HEAD -- \
            > /dev/null 2>&1; then
        fail "self-test confused a Git tool error with an empty changed set"
    fi

    rm -rf -- "$temporary"
    trap - RETURN
}

self_regression
verify_changed_paths
[ -x "$0" ] || fail "portable verifier is not executable"

EXPECTED_STORE_TYPE_COUNT=15
ACTUAL_STORE_TYPE_COUNT=$(find "$STORE_ROOT" -maxdepth 1 -name 'P4E1*.java' -print \
        | wc -l | tr -d ' ')
[ "$ACTUAL_STORE_TYPE_COUNT" -eq "$EXPECTED_STORE_TYPE_COUNT" ] \
    || fail "expected $EXPECTED_STORE_TYPE_COUNT exact P4-E1 production types, found $ACTUAL_STORE_TYPE_COUNT"

for name in \
        P4E1AuditBudget \
        P4E1AuditCounter \
        P4E1AuditStage \
        P4E1CompressedCapacityRejected \
        P4E1FileMetadata \
        P4E1FileSystemAccess \
        P4E1HeapFloorObservation \
        P4E1HeapFloorStatus \
        P4E1IntegratedSnapshotTraversal \
        P4E1PlayerDataDirectorySnapshot \
        P4E1PlayerDataFileReader \
        P4E1PlayerDataNbtScanner \
        P4E1PlayerDataSourceSelector \
        P4E1SourceAdmissionPreflight \
        P4E1SourceFailure; do
    file="$STORE_ROOT/$name.java"
    [ -f "$file" ] || fail "required production source missing: $file"
    [ ! -L "$file" ] || fail "required production source is a symlink: $file"
done

for file in "$CEILINGS" "$BUDGET" "$PREFLIGHT" \
        "$PLAYER_ROOT/PlayerSkillAttachmentAdmission.java" \
        "$PLAYER_ROOT/PlayerSkillAttachmentSourceObservation.java" \
        "$HEAP_CHILD" "$HEAP_PROBE" "$HEAP_UNIT" "$PREFLIGHT_UNIT" "$PHASE_TYPES"; do
    [ -f "$file" ] || fail "required reviewed file missing: $file"
    [ ! -L "$file" ] || fail "required reviewed file is a symlink: $file"
done

while IFS='|' read -r name value; do
    require_exact_count "$CEILINGS" "$name = $value" 1
    require_exact_count "$BUDGET" "$name" 1
done <<'MAXIMA'
MAX_PLAYERDATA_DIRECTORY_ENTRIES|4_096
MAX_PLAYERDATA_RELEVANT_RECORDS|2_048
MAX_PLAYERDATA_COMPRESSED_BYTES_PER_FILE|33_559_514
MAX_PLAYERDATA_DECOMPRESSED_BYTES_PER_FILE|268_435_456
MAX_PLAYERDATA_CONTAINER_DEPTH_PER_FILE|512
MAX_PLAYERDATA_COMPOUND_CONTAINERS_PER_FILE|1_024
MAX_PLAYERDATA_COMPOUND_FIELD_ENTRIES_PER_FILE|65_537
MAX_PLAYERDATA_LIST_ELEMENTS_PER_FILE|65_536
MAX_PLAYERDATA_BYTE_ARRAY_ELEMENTS_PER_FILE|268_435_384
MAX_PLAYERDATA_INT_ARRAY_ELEMENTS_PER_FILE|65_536
MAX_PLAYERDATA_LONG_ARRAY_ELEMENTS_PER_FILE|65_536
MAX_PLAYERDATA_MODIFIED_UTF8_BYTES_PER_FILE|67_107_692
MAX_PLAYERDATA_SCALAR_TAGS_PER_FILE|65_537
MAX_PLAYERDATA_COMPRESSED_BYTES_TOTAL|268_440_533
MAX_PLAYERDATA_DECOMPRESSED_BYTES_TOTAL|536_870_912
MAX_PLAYERDATA_COMPOUND_CONTAINERS_TOTAL|131_072
MAX_PLAYERDATA_COMPOUND_FIELD_ENTRIES_TOTAL|524_288
MAX_PLAYERDATA_LIST_ELEMENTS_TOTAL|131_072
MAX_PLAYERDATA_BYTE_ARRAY_ELEMENTS_TOTAL|456_524_705
MAX_PLAYERDATA_INT_ARRAY_ELEMENTS_TOTAL|131_072
MAX_PLAYERDATA_LONG_ARRAY_ELEMENTS_TOTAL|131_072
MAX_PLAYERDATA_MODIFIED_UTF8_BYTES_TOTAL|75_497_472
MAX_PLAYERDATA_SCALAR_TAGS_TOTAL|458_752
MAX_PLAYERDATA_ATTACHMENT_ADMISSIONS|1_024
MAXIMA

require_exact_count "$CEILINGS" \
    'MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES = 1_610_612_736L' 1
require_exact_count "$HEAP_OBSERVATION" \
    'MagicSafetyCeilings.MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES' 1
require_fixed "$BUDGET" 'case RAW_ROOT_CLAIMS -> MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM'
require_fixed "$STORE_ROOT/P4E1AuditCounter.java" 'enum P4E1AuditCounter'
require_fixed "$PREFLIGHT" 'P4E1HeapFloorObservation.observe()'
require_fixed "$PREFLIGHT" 'case HEAP_FLOOR_NOT_MET -> new Incomplete('
require_fixed "$PREFLIGHT" 'case HEAP_FLOOR_UNVERIFIABLE -> new Incomplete('
require_fixed "$HEAP_OBSERVATION" 'getVMOption("MaxHeapSize")'
require_exact_count "$HEAP_OBSERVATION" 'Runtime.getRuntime().maxMemory()' 1
require_fixed "$HEAP_CHILD" 'ALIGNED_TO_FLOOR_CONTROL'
require_fixed "$HEAP_CHILD" 'BELOW_FLOOR_CONTROL'
require_fixed "$HEAP_CHILD" '"-XX:+UseG1GC"'
require_fixed "$HEAP_CHILD" '"-XX:+UseParallelGC"'
require_fixed "$HEAP_CHILD" '"-XX:+UseSerialGC"'
require_fixed "$HEAP_CHILD" '"-XX:+UseZGC"'
require_fixed "$HEAP_CHILD" '"1536m"'
require_fixed "$HEAP_CHILD" '"1535m"'
require_fixed "$HEAP_CHILD" '"1024m"'
require_fixed "$HEAP_CHILD" 'Duration.ofSeconds(45)'
require_fixed "$HEAP_CHILD" 'redirectOutput(ProcessBuilder.Redirect.DISCARD)'
require_fixed "$HEAP_CHILD" 'readNBytes(MAX_RESULT_BYTES + 1)'
require_fixed "$HEAP_UNIT" 'FLOOR - 1L'
require_fixed "$HEAP_UNIT" 'FLOOR + 1L'
require_fixed "$HEAP_UNIT" 'Long.MAX_VALUE'
require_fixed "$HEAP_PROBE" 'effective_max_heap_size_bytes='
require_fixed "$HEAP_PROBE" 'runtime_max_memory_bytes='
require_fixed "$HEAP_PROBE" 'heap_memory_usage_max_bytes='
require_fixed "$HEAP_PROBE" 'floor_bytes='
require_fixed "$HEAP_PROBE" 'classification='
require_fixed "$HEAP_PROBE" 'exception_class='
require_fixed "$HEAP_PROBE" 'source_work_calls='
require_fixed "$HEAP_PROBE" 'observedBudgetedSourceWork(preflight)'
reject_fixed "$HEAP_PROBE" 'sourceWorkCalls = 0'
require_fixed "$HEAP_PROBE" 'MAX_RESULT_BYTES = 4_096'
reject_fixed "$HEAP_PROBE" 'System.out'
for source_work in \
        DIRECTORY_ENUMERATION \
        FILE_ATTRIBUTE_READS \
        FILE_OPENS \
        GZIP_PARSER_CALLS \
        NBT_SCANNER_CALLS \
        INTEGRATED_TRAVERSAL_CALLS \
        P4C_ADMISSION_CALLS \
        ONLINE_ATTACHMENT_READS \
        JOURNAL_CALLS \
        ROOT_CLAIMS \
        STORE_AUDIT_CALLS \
        RECLAIM_CALLS; do
    require_exact_count "$PREFLIGHT_UNIT" "$source_work" 1
done
require_exact_count "$PREFLIGHT_UNIT" 'sourceWork.assertAll(0)' 1
require_fixed "$PREFLIGHT_UNIT" 'evaluateThenAttemptSourceAdmission(observation, sourceWork)'
require_exact_count "$PREFLIGHT" 'new P4E1AuditBudget(new QualifiedPermit())' 1
require_fixed "$PREFLIGHT" 'private QualifiedPermit()'
require_fixed "$BUDGET" \
    'P4E1AuditBudget(P4E1SourceAdmissionPreflight.QualifiedPermit permit)'
reject_fixed "$BUDGET" 'static P4E1AuditBudget create('
for production_source in $(find "$MAIN_JAVA" -type f -name '*.java' -print); do
    [ "$production_source" = "$PREFLIGHT" ] && continue
    reject_fixed "$production_source" 'P4E1SourceAdmissionPreflight.evaluate('
done
require_fixed "$E0_LEDGER" 'P4-E0-B.2 effective-MaxHeapSize authority correction'
require_fixed "$E0_LEDGER" 'P4-E0-B.2 authority patch          = COMPLETE'
require_fixed "$STORE_ROOT/P4E1PlayerDataDirectorySnapshot.java" 'LevelResource.PLAYER_DATA_DIR'
require_fixed "$STORE_ROOT/P4E1PlayerDataDirectorySnapshot.java" 'RecordSelection selectRecords('
require_fixed "$STORE_ROOT/P4E1PlayerDataDirectorySnapshot.java" \
    'excludedIntegratedOwner.filter(record.playerId()::equals)'
require_fixed "$STORE_ROOT/P4E1PlayerDataNbtScanner.java" 'new ArrayDeque<>()'
require_fixed "$STORE_ROOT/P4E1PlayerDataNbtScanner.java" 'CURRENT_DATA_VERSION = 3_955'
reject_fixed "$STORE_ROOT/P4E1PlayerDataNbtScanner.java" 'attachmentOuterWrongType'
require_fixed "$STORE_ROOT/StrictSingleMemberGzipInput.java" \
    'GzipCompressorInputStream(bufferedCompressed, false)'
require_exact_count "$STORE_ROOT/StrictSingleMemberGzipInput.java" \
    'new GzipCompressorInputStream(' 1

E1_SOURCES=$(find "$STORE_ROOT" -maxdepth 1 -type f -name 'P4E1*.java' -print)
[ -n "$E1_SOURCES" ] || fail "P4-E1 source allowlist is empty"

for token in \
        'java.util.zip.GZIPInputStream' \
        'Files.readAllBytes' \
        'NbtAccounter.unlimitedHeap' \
        'java.lang.reflect' \
        'setAccessible(' \
        'sun.misc.Unsafe' \
        'Thread.sleep(' \
        'System.gc(' \
        'Executors.' \
        'ExecutorService' \
        'java.util.concurrent.Future' \
        'new Thread(' \
        'RuntimeMXBean' \
        'getInputArguments(' \
        'PrintFlagsFinal' \
        'jcmd' \
        '.setData(' \
        'NbtIo.write' \
        'Files.write' \
        'FileOutputStream' \
        'PlayerDataStorage' \
        '.saveAll(' \
        '.saveWithoutId(' \
        'PendingSkillSubmissionJournal' \
        'SkillDefinitionStoreService' \
        'SkillDefinitionStoreSubmissionPort' \
        'net.minecraft.client' \
        'SkillRetentionRootSnapshot' \
        '.reclaim(' \
        'ServerStartingEvent' \
        'ServerStartedEvent' \
        'PlayerLoggedInEvent' \
        'PlayerLoggedOutEvent' \
        'PlayerEvent.Clone' \
        'CompletableFuture' \
        'parallelStream(' \
        'CustomPacketPayload' \
        'PayloadRegistrar'; do
    for source in $E1_SOURCES \
            "$PLAYER_ROOT/PlayerSkillAttachmentAdmission.java" \
            "$PLAYER_ROOT/PlayerSkillAttachmentSourceObservation.java"; do
        reject_fixed "$source" "$token"
    done
done

for token in \
        'P4E1PlayerDataDirectorySnapshot' \
        'P4E1FileSystemAccess' \
        'P4E1PlayerDataFileReader' \
        'P4E1PlayerDataNbtScanner' \
        'P4E1IntegratedSnapshotTraversal' \
        'PlayerSkillAttachmentAdmission' \
        'PlayerSkillAttachmentSourceObservation' \
        'PendingSkillSubmissionJournal' \
        'SkillRetentionRootSnapshot' \
        'SkillDefinitionStore' \
        '.reclaim('; do
    reject_fixed "$PREFLIGHT" "$token"
done

for source in $E1_SOURCES \
        "$PLAYER_ROOT/PlayerSkillAttachmentAdmission.java" \
        "$PLAYER_ROOT/PlayerSkillAttachmentSourceObservation.java"; do
    reject_fixed "$source" 'catch (Error'
    reject_fixed "$source" 'catch (OutOfMemoryError'
    reject_fixed "$source" 'catch (Throwable'
done

reject_fixed "$STORE_ROOT/P4E1PlayerDataNbtScanner.java" 'NbtIo.'
reject_fixed "$STORE_ROOT/P4E1IntegratedSnapshotTraversal.java" '.copy('
reject_fixed "$STORE_ROOT/P4E1IntegratedSnapshotTraversal.java" 'NbtIo.'
reject_fixed "$STORE_ROOT/P4E1IntegratedSnapshotTraversal.java" 'toString('

reject_fixed "$REPOSITORY_ROOT/build.gradle" 'p4E1'
reject_fixed "$REPOSITORY_ROOT/build.gradle" 'P4E1'
reject_fixed "$REPOSITORY_ROOT/.github/workflows/build.yml" 'p4-e1'
reject_fixed "$REPOSITORY_ROOT/.github/workflows/build.yml" 'P4-E1'

for forbidden_name in \
        SkillRetentionRootAuditService.java \
        P4E1GlobalInventory.java \
        P4E1RootIndex.java \
        P4E1Reconciliation.java; do
    [ ! -e "$STORE_ROOT/$forbidden_name" ] \
        || fail "later-phase production source exists: $forbidden_name"
done

require_fixed "$PHASE_TYPES" '"P4E1CompressedCapacityRejected"'
require_fixed "$PHASE_TYPES" '"P4E1SourceAdmissionPreflight"'

echo "P4-E1 configuration verification passed"
