#!/usr/bin/env bash
set -euo pipefail

PATH='/usr/bin:/bin'
export PATH

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

for required_tool in bash grep find git jar mktemp rm dirname pwd sed; do
    command -v "${required_tool}" >/dev/null 2>&1 \
        || fail "P4-E0-R2Q verifier cannot find required tool: ${required_tool}"
done

REPO_ROOT=''
if REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; then
    :
else
    fail 'P4-E0-R2Q verifier could not resolve repository root'
fi
cd "${REPO_ROOT}"

HELPER_FIXTURE=''
R2Q_SOURCE_LIST=''
R2Q_GAME_LIST=''
JAR_LIST=''
JAR_CONTENTS=''
FORMAL_BLOCK=''
COUNTER_BLOCK=''
CASE_KIND_BLOCK=''
RUNTIME_BLOCK=''

cleanup() {
    local temporary=''
    for temporary in \
        "${HELPER_FIXTURE}" \
        "${R2Q_SOURCE_LIST}" \
        "${R2Q_GAME_LIST}" \
        "${JAR_LIST}" \
        "${JAR_CONTENTS}" \
        "${FORMAL_BLOCK}" \
        "${COUNTER_BLOCK}" \
        "${CASE_KIND_BLOCK}" \
        "${RUNTIME_BLOCK}"; do
        if [[ -n "${temporary}" ]]; then
            rm -f -- "${temporary}"
        fi
    done
}
trap cleanup EXIT HUP INT TERM

grep_failed() {
    fail "grep failed while checking $1 (exit $2)"
}

require_fixed() {
    local file="$1"
    local needle="$2"
    local message="$3"
    local status=0
    LC_ALL=C grep -Fq -- "${needle}" "${file}" || status=$?
    case "${status}" in
        0) return 0 ;;
        1) fail "${message}" ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
}

forbid_fixed() {
    local file="$1"
    local needle="$2"
    local message="$3"
    local status=0
    LC_ALL=C grep -Fq -- "${needle}" "${file}" || status=$?
    case "${status}" in
        0) fail "${message}" ;;
        1) return 0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
}

forbid_ere() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    local status=0
    LC_ALL=C grep -Eq -- "${pattern}" "${file}" || status=$?
    case "${status}" in
        0) fail "${message}" ;;
        1) return 0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
}

require_count() {
    local file="$1"
    local needle="$2"
    local expected="$3"
    local message="$4"
    local actual=''
    local status=0
    actual="$(LC_ALL=C grep -Fc -- "${needle}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) actual=0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    [[ "${actual}" -eq "${expected}" ]] \
        || fail "${message} (expected ${expected}, found ${actual})"
}

require_ere_count() {
    local file="$1"
    local pattern="$2"
    local expected="$3"
    local message="$4"
    local actual=''
    local status=0
    actual="$(LC_ALL=C grep -Ec -- "${pattern}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) actual=0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    [[ "${actual}" -eq "${expected}" ]] \
        || fail "${message} (expected ${expected}, found ${actual})"
}

null_record_count() {
    local file="$1"
    local count=0
    local value=''
    while IFS= read -r -d '' value; do
        count=$((count + 1))
    done < "${file}"
    printf '%s\n' "${count}"
}

require_regular_file() {
    [[ -f "$1" && ! -L "$1" ]] || fail "$2"
}

verify_helpers() {
    local output=''
    local status=0
    HELPER_FIXTURE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-helper.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create helper fixture'
    printf '%s\n' 'present contract' > "${HELPER_FIXTURE}"
    require_fixed "${HELPER_FIXTURE}" 'present contract' \
        'P4-E0-R2Q self-test lost required matching'
    forbid_fixed "${HELPER_FIXTURE}" 'absent contract' \
        'P4-E0-R2Q self-test misclassified an absent pattern'

    output="$({ require_fixed "${HELPER_FIXTURE}" missing EXPECTED_MISSING; } 2>&1)" \
        || status=$?
    [[ "${status}" -eq 1 && "${output}" == 'EXPECTED_MISSING' ]] \
        || fail 'P4-E0-R2Q verifier cannot distinguish a missing contract'
    status=0
    output="$({ forbid_fixed "${HELPER_FIXTURE}" present EXPECTED_FORBIDDEN; } 2>&1)" \
        || status=$?
    [[ "${status}" -eq 1 && "${output}" == 'EXPECTED_FORBIDDEN' ]] \
        || fail 'P4-E0-R2Q verifier cannot distinguish a forbidden contract'
    status=0
    output="$({ require_fixed "${HELPER_FIXTURE}.missing" present WRONG_MISSING; } 2>&1)" \
        || status=$?
    if [[ "${status}" -ne 1 \
            || "${output}" != *'grep failed while checking '* \
            || "${output}" == *'WRONG_MISSING'* ]]; then
        fail 'P4-E0-R2Q verifier cannot distinguish tool error from missing input'
    fi
}

verify_paths_and_boundaries() {
    local untracked=''
    local status=0
    for file in \
        build.gradle \
        scripts/verify-p4-b2-b-configuration.sh \
        scripts/verify-p4-e0-r-configuration.sh \
        scripts/verify-p4-e0-r2q-configuration.sh \
        src/p4E0Research/resources/p4-e0-r2q-profile-v0.json \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/player/P4E0ResearchAttachmentFixtures.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchWireNbt.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QAuditBudget.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QProfile.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QStudyIdentity.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QCasePlan.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QModifiedUtf.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QPositiveWitnesses.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFixturePlan.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QJointRecords.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QMain.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/store/P4E0R2QStoreJournalFixtures.java \
        src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchDedicatedCoordinator.java \
        src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QDedicatedDriver.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QProfileTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QAuditBudgetTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QPositiveWitnessTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QJointRecordsTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QRootProjectionTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QNegativeFixtureTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QModifiedUtfTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QExactGzipWitnessTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFixtureTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QApiGateTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QStudyIdentityTest.java; do
        require_regular_file "${file}" "P4-E0-R2Q reviewed path is missing: ${file}"
    done

    git diff --quiet HEAD -- \
        src/main/java src/main/resources docs/codex-spec docs/architecture \
        .github/workflows gradle.properties \
        || fail 'P4-E0-R2Q modified production, authority, workflow, or version truth'
    untracked="$(git ls-files --others --exclude-standard -- \
        src/main/java src/main/resources docs/codex-spec docs/architecture \
        .github/workflows)" || status=$?
    [[ "${status}" -eq 0 ]] || fail 'git failed while checking prohibited paths'
    [[ -z "${untracked}" ]] \
        || fail "P4-E0-R2Q added a prohibited path: ${untracked}"
    forbid_fixed .github/workflows/build.yml 'p4-e0-r2q' \
        'P4-E0-R2Q must not add a workflow job'
}

verify_profile_manifest() {
    local manifest='src/p4E0Research/resources/p4-e0-r2q-profile-v0.json'
    local marker=''
    for marker in \
        '"schema_version": 0' \
        '"authority": "EXPLORATORY_NON_NORMATIVE"' \
        '"profile_name": "BALANCED_V0_1536_QUALIFICATION"' \
        '"candidate_values"' \
        '"directory_entries": 4096' \
        '"relevant_records": 2048' \
        '"compressed_bytes_per_file": 33559514' \
        '"decompressed_bytes_per_file": 268435456' \
        '"container_depth_per_file": 512' \
        '"compound_containers_per_file": 1024' \
        '"compound_field_entries_per_file": 65537' \
        '"list_elements_per_file": 65536' \
        '"byte_array_elements_per_file": 268435384' \
        '"int_array_elements_per_file": 65536' \
        '"long_array_elements_per_file": 65536' \
        '"modified_utf8_bytes_per_file": 67107692' \
        '"scalar_tags_per_file": 65537' \
        '"counter_coordinates"' \
        '"dependency_edges"' \
        '"failure_precedence"' \
        '"overrun_policy": "INCOMPLETE_AND_CONTINUE"' \
        '"bytes": 12884901888' \
        '"qualification_mib": 1536' \
        '"accepted_data_version": 3955' \
        '"max_dfu_records": 0' \
        '"raw_root_claims": 65536' \
        '"attachment_admissions": 1024' \
        '"compressed_bytes_total": 268440533' \
        '"decompressed_bytes_total": 536870912' \
        '"compound_containers_total": 131072' \
        '"compound_field_entries_total": 524288' \
        '"list_elements_total": 131072' \
        '"byte_array_elements_total": 456524705' \
        '"int_array_elements_total": 131072' \
        '"long_array_elements_total": 131072' \
        '"modified_utf8_bytes_total": 75497472' \
        '"scalar_tags_total": 458752'; do
        require_fixed "${manifest}" "${marker}" \
            "P4-E0-R2Q profile manifest is missing ${marker}"
    done
    forbid_fixed "${manifest}" '"qualification_mib": 1280' \
        'P4-E0-R2Q must not add a 1280 MiB qualification case'
    forbid_fixed "${manifest}" 'production_limit' \
        'P4-E0-R2Q profile must not claim production authority'
}

verify_case_plan_contract() {
    local profile='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QProfile.java'
    local plan='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QCasePlan.java'
    local slug=''
    COUNTER_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-counters.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create counter block'
    CASE_KIND_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-kinds.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create case-kind block'
    sed -n '/^    enum Counter {$/,/^        private final String slug;$/p' \
        "${profile}" > "${COUNTER_BLOCK}"
    sed -n '/^    enum CaseKind {$/,/^    }$/p' \
        "${plan}" > "${CASE_KIND_BLOCK}"
    [[ -s "${COUNTER_BLOCK}" && -s "${CASE_KIND_BLOCK}" ]] \
        || fail 'P4-E0-R2Q verifier could not isolate counter/case enums'

    require_ere_count "${COUNTER_BLOCK}" \
        '^[[:space:]]+[A-Z0-9_]+\("[a-z0-9_]+"\)[,;]$' 25 \
        'P4-E0-R2Q must expose exactly twenty-five typed counter slugs'
    for slug in \
        directory_entries \
        relevant_records \
        compressed_bytes_per_file \
        decompressed_bytes_per_file \
        container_depth_per_file \
        compound_containers_per_file \
        compound_field_entries_per_file \
        list_elements_per_file \
        byte_array_elements_per_file \
        int_array_elements_per_file \
        long_array_elements_per_file \
        modified_utf8_bytes_per_file \
        scalar_tags_per_file \
        compressed_bytes_total \
        decompressed_bytes_total \
        compound_containers_total \
        compound_field_entries_total \
        list_elements_total \
        byte_array_elements_total \
        int_array_elements_total \
        long_array_elements_total \
        modified_utf8_bytes_total \
        scalar_tags_total \
        attachment_admissions \
        raw_root_claims; do
        require_count "${COUNTER_BLOCK}" "(\"${slug}\")" 1 \
            "P4-E0-R2Q typed counter slug changed: ${slug}"
    done

    require_ere_count "${CASE_KIND_BLOCK}" \
        '^[[:space:]]+[A-Z0-9_]+[,;]?$' 5 \
        'P4-E0-R2Q case-kind vocabulary must contain exactly five values'
    for kind in \
        POSITIVE \
        COUNTER_MAX_PLUS_ONE \
        DATA_VERSION_MISSING \
        DATA_VERSION_WRONG_TYPE \
        DATA_VERSION_WRONG_VALUE; do
        require_fixed "${CASE_KIND_BLOCK}" "${kind}" \
            "P4-E0-R2Q case-kind vocabulary is missing ${kind}"
    done
    forbid_fixed "${CASE_KIND_BLOCK}" 'DFU' \
        'P4-E0-R2Q must not add an independent DFU case kind'

    for marker in \
        'CASE_COUNT = 29' \
        'CASE_PREFIX + "exact"' \
        'CASE_PREFIX + "over-" + counter.slug().replace' \
        'addDataVersion(cases, CaseKind.DATA_VERSION_MISSING' \
        'addDataVersion(cases, CaseKind.DATA_VERSION_WRONG_TYPE' \
        'addDataVersion(cases, CaseKind.DATA_VERSION_WRONG_VALUE' \
        'input.size() != CASE_COUNT' \
        '!identifiers.add(spec.caseId())' \
        '!counters.add(target)' \
        'EnumSet.allOf(P4E0R2QProfile.Counter.class)' \
        'dataVersionControls != 3' \
        'spec.expectedDfuInvocations() != 0'; do
        require_fixed "${plan}" "${marker}" \
            "P4-E0-R2Q exact 29-case contract is missing ${marker}"
    done
    forbid_fixed "${plan}" '"dfu-' \
        'P4-E0-R2Q must not add an independent lowercase DFU case ID'
    forbid_fixed "${plan}" '-dfu"' \
        'P4-E0-R2Q must not add an independent lowercase DFU case ID'
    forbid_fixed "${plan}" '1280' \
        'P4-E0-R2Q must not add a 1280 MiB case'
}

verify_build_and_formal_gate() {
    local marker=''
    require_count build.gradle "sourceSets.create('p4E0Research" 2 \
        'P4-E0-R2Q must reuse exactly two research source sets'
    require_fixed scripts/verify-p4-b2-b-configuration.sh \
        "name == 'p4E0R2QDedicatedSmoke'" \
        'P4-E0-R2Q dedicated run is absent from the exact B2 loaded-mod allowlist'
    for marker in \
        'p4E0R2QDedicatedSmoke {' \
        'def p4E0R2QSmokeTimeoutSeconds = 600' \
        "layout.buildDirectory.dir('p4-e0-r2q/dedicated-smoke')" \
        'p4E0R2QDedicatedGameDirectory.get().asFile.deleteDir()' \
        "sourceSet = p4E0ResearchGameTestSourceSet" \
        "systemProperty 'gramarye.p4e0.research.runMode', 'r2q-smoke'" \
        'jvmArguments.addAll(p4E0R2QSmokeJvmArgs)' \
        'timeout.set(java.time.Duration.ofSeconds(p4E0R2QSmokeTimeoutSeconds))' \
        "'prepareP4E0R2Q'" \
        "'verifyP4E0R2QPreflightTests')" \
        "'verifyP4E0R2QProfile'" \
        "'runP4E0R2QSmoke'" \
        "tasks.named('runP4E0R2QDedicatedSmoke', JavaExec)" \
        "'verifyP4E0R2QSmokeOutput'" \
        "'verifyP4E0R2QConfiguration', Exec" \
        "tasks.register('p4E0R2QSmoke')" \
        "tasks.register('p4E0R2QStudy')" \
        'dependsOn(verifyP4E0R2QConfiguration)' \
        'dependsOn(verifyP4E0R2QPreflightTests)' \
        'dependsOn(prepareP4E0R2Q)' \
        'dependsOn(verifyP4E0R2QProfile)' \
        'dependsOn(runP4E0R2QSmoke)' \
        'dependsOn(runP4E0R2QDedicatedSmoke)' \
        'dependsOn(verifyP4E0R2QSmokeOutput)' \
        ".gradleProperty('p4E0R2QFormal')" \
        "commandLine('git', 'status', '--porcelain=v2', '--untracked-files=all')" \
        "commandLine('git', 'rev-parse', 'HEAD')" \
        "commandLine('git', 'rev-parse', 'HEAD^{tree}')" \
        "commandLine('git', 'rev-parse', 'origin/main')" \
        'formal execution is reserved for R2Q-B'; do
        require_fixed build.gradle "${marker}" \
            "P4-E0-R2Q build contract is missing ${marker}"
    done

    RUNTIME_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-runtime.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create runtime block'
    sed -n '/^        p4E0ResearchHarness {$/,/^        }$/p' \
        build.gradle > "${RUNTIME_BLOCK}"
    [[ -s "${RUNTIME_BLOCK}" ]] \
        || fail 'P4-E0-R2Q dedicated runtime block is missing'
    require_ere_count "${RUNTIME_BLOCK}" '^[[:space:]]+sourceSet\(' 7 \
        'P4-E0-R2Q dedicated runtime must contain exactly seven source sets'
    for marker in \
        'sourceSet(sourceSets.main)' \
        'sourceSet(p4A3ProbeSourceSet)' \
        'sourceSet(p4B2ProbeSourceSet)' \
        'sourceSet(p4C2ProbeSourceSet)' \
        'sourceSet(p4D3ProbeSourceSet)' \
        'sourceSet(p4E0ResearchSourceSet)' \
        'sourceSet(p4E0ResearchGameTestSourceSet)'; do
        require_count "${RUNTIME_BLOCK}" "${marker}" 1 \
            "P4-E0-R2Q dedicated runtime changed ${marker}"
    done
    for marker in \
        'sourceSets.test' \
        'p4A3GameTestSourceSet' \
        'p4B2GameTestSourceSet' \
        'p4C2GameTestSourceSet' \
        'p4D3GameTestSourceSet'; do
        forbid_fixed "${RUNTIME_BLOCK}" "${marker}" \
            "P4-E0-R2Q dedicated runtime contains forbidden ${marker}"
    done

    require_count build.gradle \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName' 7 \
        'P4-E0-R2Q dedicated classpath must have exactly seven reviewed dependencies'
    require_count build.gradle \
        'p4E0ResearchGameTestSourceSet.implementationConfigurationName' 7 \
        'P4-E0-R2Q dedicated implementation configuration gained an unreviewed use'
    for marker in \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, sourceSets.main.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4A3ProbeSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4B2ProbeSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4C2ProbeSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4D3ProbeSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4E0ResearchSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, commonsCompressCoordinate)'; do
        require_count build.gradle "${marker}" 1 \
            "P4-E0-R2Q dedicated classpath changed ${marker}"
    done
    for marker in \
        'p4E0ResearchGameTestImplementation' \
        'p4E0ResearchGameTestRuntimeOnly' \
        'p4E0ResearchGameTestRuntimeClasspath' \
        'p4E0ResearchGameTestSourceSet.runtimeOnlyConfigurationName' \
        'p4E0ResearchGameTestSourceSet.runtimeClasspathConfigurationName' \
        'sourceSets.test.output' \
        'testRuntimeClasspath'; do
        forbid_fixed build.gradle "${marker}" \
            "P4-E0-R2Q dedicated runtime acquired forbidden test/JUnit path ${marker}"
    done

    FORMAL_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-formal.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create formal-task block'
    sed -n "/^tasks.register('p4E0R2QStudy') {$/,/^}$/p" \
        build.gradle > "${FORMAL_BLOCK}"
    [[ -s "${FORMAL_BLOCK}" ]] || fail 'P4-E0-R2Q formal task block is missing'
    forbid_fixed "${FORMAL_BLOCK}" 'dependsOn' \
        'P4-E0-R2Q-A formal entry must own no child dependency'
    forbid_fixed "${FORMAL_BLOCK}" 'ProcessBuilder' \
        'P4-E0-R2Q-A formal entry must not launch a child'
    for marker in finalizedBy commandLine javaexec JavaExec Exec; do
        forbid_fixed "${FORMAL_BLOCK}" "${marker}" \
            "P4-E0-R2Q-A formal entry contains forbidden launch seam ${marker}"
    done
}

verify_source_boundary() {
    local file=''
    local source_count=''
    local game_count=''
    R2Q_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-source.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create source list'
    R2Q_GAME_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-game.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create dedicated list'
    find src/p4E0Research/java -type f -name 'P4E0R2Q*.java' -print0 \
        > "${R2Q_SOURCE_LIST}"
    find src/p4E0ResearchGameTest/java -type f -name 'P4E0R2Q*.java' -print0 \
        > "${R2Q_GAME_LIST}"
    source_count="$(null_record_count "${R2Q_SOURCE_LIST}")"
    game_count="$(null_record_count "${R2Q_GAME_LIST}")"
    [[ "${source_count}" -eq 10 && "${game_count}" -eq 1 ]] \
        || fail "P4-E0-R2Q exact source allowlist changed (${source_count}/${game_count})"
    printf '%s\0' \
        'src/p4E0Research/java/com/yo1no/gramarye/magic/definition/player/P4E0ResearchAttachmentFixtures.java' \
        'src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchWireNbt.java' \
        >> "${R2Q_SOURCE_LIST}"
    printf '%s\0' \
        'src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchDedicatedCoordinator.java' \
        >> "${R2Q_GAME_LIST}"
    [[ -s "${R2Q_SOURCE_LIST}" && -s "${R2Q_GAME_LIST}" ]] \
        || fail 'P4-E0-R2Q source lists are empty'
    for list in "${R2Q_SOURCE_LIST}" "${R2Q_GAME_LIST}"; do
        while IFS= read -r -d '' file; do
            for forbidden in \
                'org.junit' 'System.gc(' 'Thread.sleep(' 'Files.readAllBytes' \
                'NbtAccounter.unlimitedHeap' 'java.lang.reflect' \
                'setAccessible(' 'sun.misc.Unsafe' '.reclaim(' \
                'RootIndex' 'Reconciliation' 'CustomPacketPayload' \
                'net.minecraft.client'; do
                forbid_fixed "${file}" "${forbidden}" \
                    "P4-E0-R2Q source contains forbidden ${forbidden}: ${file}"
            done
            forbid_ere "${file}" \
                'catch[[:space:]]*\([^)]*OutOfMemoryError' \
                "P4-E0-R2Q source catches OOME: ${file}"
        done < "${list}"
    done
    for required_case_marker in \
        'p4-e0-r2q-balanced-v0-1536-' \
        'CASE_PREFIX + "exact"' \
        'CASE_PREFIX + "over-"' \
        '"dataversion-missing"' \
        '"dataversion-wrong-type"' \
        '"dataversion-wrong-value"' \
        'CASE_COUNT = 29'; do
        require_fixed \
            src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QCasePlan.java \
            "${required_case_marker}" \
            "P4-E0-R2Q case plan is missing ${required_case_marker}"
    done
    for counter_slug in \
        directory_entries relevant_records \
        compressed_bytes_per_file decompressed_bytes_per_file \
        container_depth_per_file compound_containers_per_file \
        compound_field_entries_per_file list_elements_per_file \
        byte_array_elements_per_file int_array_elements_per_file \
        long_array_elements_per_file modified_utf8_bytes_per_file \
        scalar_tags_per_file compressed_bytes_total decompressed_bytes_total \
        compound_containers_total compound_field_entries_total \
        list_elements_total byte_array_elements_total int_array_elements_total \
        long_array_elements_total modified_utf8_bytes_total scalar_tags_total \
        attachment_admissions raw_root_claims; do
        require_fixed \
            src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QProfile.java \
            "(\"${counter_slug}\")" \
            "P4-E0-R2Q profile is missing counter slug ${counter_slug}"
    done
    for coverage_marker in \
        'for (var counter : P4E0R2QProfile.Counter.values())' \
        "CASE_PREFIX + \"over-\" + counter.slug().replace('_', '-')" \
        '!identifiers.add(spec.caseId())' \
        '!counters.add(target)' \
        'counters.equals(EnumSet.allOf(P4E0R2QProfile.Counter.class))' \
        'dataVersionControls != 3' \
        'spec.expectedDfuInvocations() != 0'; do
        require_fixed \
            src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QCasePlan.java \
            "${coverage_marker}" \
            "P4-E0-R2Q case coverage is missing ${coverage_marker}"
    done
    forbid_fixed \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QCasePlan.java \
        'CASE_PREFIX + "dfu' \
        'P4-E0-R2Q must not define an independent DFU case'
    require_count build.gradle \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName' 7 \
        'P4-E0-R2Q dedicated runtime dependency allowlist changed'
    for forbidden_dependency in \
        'p4E0ResearchGameTestImplementation' \
        'p4E0ResearchGameTestRuntimeOnly' \
        'p4E0ResearchGameTestSourceSet.runtimeOnlyConfigurationName' \
        'testRuntimeClasspath'; do
        forbid_fixed build.gradle "${forbidden_dependency}" \
            "P4-E0-R2Q dedicated runtime contains forbidden dependency seam ${forbidden_dependency}"
    done
    for marker in \
        'P4E0R2QStoreJournalFixtures.buildExact()' \
        'installExactSubmission(server)' \
        'P4D3ProbeSupport.observeLive(server)' \
        'Files.walk(ownedRoot)' \
        'exact_store_journal_root_preflight' \
        'exact_d2_prospective_observed'; do
        require_fixed \
            src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QMain.java \
            "${marker}" \
            "P4-E0-R2Q exact preflight/smoke is missing ${marker}"
    done
}

verify_jar_isolation() {
    local jar_path=''
    local source_path=''
    local class_path=''
    local status=0
    JAR_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-jars.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create JAR list'
    JAR_CONTENTS="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-jar.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create JAR listing'
    find build/libs -maxdepth 1 -type f -name 'gramarye-*.jar' -print0 \
        > "${JAR_LIST}" || status=$?
    [[ "${status}" -eq 0 && -s "${JAR_LIST}" ]] \
        || fail 'P4-E0-R2Q verifier found no production JAR'
    while IFS= read -r -d '' jar_path; do
        status=0
        jar tf "${jar_path}" > "${JAR_CONTENTS}" || status=$?
        [[ "${status}" -eq 0 ]] || fail "jar failed for ${jar_path}"
        while IFS= read -r -d '' source_path; do
            class_path="${source_path#src/p4E0Research/java/}"
            class_path="${class_path%.java}.class"
            forbid_fixed "${JAR_CONTENTS}" "${class_path}" \
                "P4-E0-R2Q class leaked into production JAR: ${class_path}"
        done < "${R2Q_SOURCE_LIST}"
        while IFS= read -r -d '' source_path; do
            class_path="${source_path#src/p4E0ResearchGameTest/java/}"
            class_path="${class_path%.java}.class"
            forbid_fixed "${JAR_CONTENTS}" "${class_path}" \
                "P4-E0-R2Q dedicated class leaked into production JAR: ${class_path}"
        done < "${R2Q_GAME_LIST}"
        forbid_fixed "${JAR_CONTENTS}" 'P4E0R2Q' \
            'P4-E0-R2Q nested/orphan class leaked into production JAR'
        forbid_fixed "${JAR_CONTENTS}" 'p4-e0-r2q-profile-v0.json' \
            'P4-E0-R2Q profile resource leaked into production JAR'
    done < "${JAR_LIST}"
}

main() {
    verify_helpers
    require_regular_file scripts/verify-p4-e0-r2q-configuration.sh \
        'P4-E0-R2Q verifier is missing'
    bash -n scripts/verify-p4-e0-r2q-configuration.sh \
        || fail 'P4-E0-R2Q verifier failed bash -n'
    [[ -x scripts/verify-p4-e0-r2q-configuration.sh ]] \
        || fail 'P4-E0-R2Q verifier mode must be executable'
    verify_paths_and_boundaries
    verify_profile_manifest
    verify_case_plan_contract
    verify_build_and_formal_gate
    verify_source_boundary
    verify_jar_isolation
    printf '%s\n' \
        'Verified P4-E0-R2Q profile, smoke tasks, formal fail-closed entry, boundaries, and JAR isolation.'
}

main "$@"
