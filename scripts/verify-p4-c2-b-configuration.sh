#!/usr/bin/env bash
set -euo pipefail

# Deliberately use only tools available in the requested minimal PATH. JAVA_HOME is not required;
# the locked JDK's jar launcher is present in /usr/bin on supported local and CI hosts.
PATH='/usr/bin:/bin'
export PATH

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

for required_tool in bash grep find git jar mktemp rm dirname pwd sed; do
    command -v "${required_tool}" >/dev/null 2>&1 \
        || fail "P4-C2-B configuration verifier cannot find required tool: ${required_tool}"
done

REPO_ROOT=''
if REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; then
    :
else
    fail 'P4-C2-B configuration verifier could not resolve the repository root'
fi
cd "${REPO_ROOT}"

PRODUCTION_SOURCE_LIST=''
PROBE_SOURCE_LIST=''
GAME_TEST_SOURCE_LIST=''
JAR_FILE_LIST=''
JAR_LISTING=''
C_JOB_BLOCK=''

cleanup() {
    local file=''
    for file in \
        "${PRODUCTION_SOURCE_LIST}" \
        "${PROBE_SOURCE_LIST}" \
        "${GAME_TEST_SOURCE_LIST}" \
        "${JAR_FILE_LIST}" \
        "${JAR_LISTING}" \
        "${C_JOB_BLOCK}"; do
        if [[ -n "${file}" ]]; then
            rm -f -- "${file}"
        fi
    done
}
trap cleanup EXIT HUP INT TERM

grep_failed() {
    local file="$1"
    local status="$2"
    fail "grep failed while checking ${file} (exit ${status})"
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

require_ere() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    local status=0
    LC_ALL=C grep -Eq -- "${pattern}" "${file}" || status=$?
    case "${status}" in
        0) return 0 ;;
        1) fail "${message}" ;;
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

require_fixed_count() {
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
    if [[ "${actual}" -ne "${expected}" ]]; then
        fail "${message} (expected ${expected}, found ${actual})"
    fi
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
    if [[ "${actual}" -ne "${expected}" ]]; then
        fail "${message} (expected ${expected}, found ${actual})"
    fi
}

require_regular_file() {
    local file="$1"
    local message="$2"
    if [[ ! -f "${file}" || -L "${file}" ]]; then
        fail "${message}"
    fi
}

collect_java_files() {
    local root="$1"
    local destination="$2"
    local status=0
    LC_ALL=C find "${root}" -type f -name '*.java' -print0 > "${destination}" || status=$?
    if [[ "${status}" -ne 0 || ! -s "${destination}" ]]; then
        fail "P4-C2-B verifier could not inspect Java sources under ${root}"
    fi
}

require_nul_entry_count() {
    local file="$1"
    local expected="$2"
    local message="$3"
    local count=0
    local entry=''
    while IFS= read -r -d '' entry; do
        count=$((count + 1))
    done < "${file}"
    if [[ "${count}" -ne "${expected}" ]]; then
        fail "${message} (expected ${expected}, found ${count})"
    fi
}

forbid_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local message="$3"
    local file=''
    while IFS= read -r -d '' file; do
        forbid_fixed "${file}" "${needle}" "${message} (${file})"
    done < "${file_list}"
}

require_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local message="$3"
    local file=''
    local status=0
    while IFS= read -r -d '' file; do
        status=0
        LC_ALL=C grep -Fq -- "${needle}" "${file}" || status=$?
        case "${status}" in
            0) return 0 ;;
            1) ;;
            *) grep_failed "${file}" "${status}" ;;
        esac
    done < "${file_list}"
    fail "${message}"
}

extract_yaml_job() {
    local yaml="$1"
    local job="$2"
    local output="$3"
    local status=0
    LC_ALL=C sed -n "/^  ${job}:/,/^  [A-Za-z0-9_-][A-Za-z0-9_-]*:/p" \
        "${yaml}" > "${output}" || status=$?
    if [[ "${status}" -ne 0 || ! -s "${output}" ]]; then
        fail "P4-C2-B verifier could not extract workflow job ${job}"
    fi
}

verify_search_helpers() {
    local fixture=''
    local output=''
    local status=0
    fixture="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-helper.XXXXXX")" \
        || fail 'P4-C2-B verifier could not create helper fixture'
    printf '%s\n' 'present contract' > "${fixture}"
    require_fixed "${fixture}" 'present contract' 'helper failed to find a present contract'
    forbid_fixed "${fixture}" 'absent contract' 'helper reported an absent contract as present'
    status=0
    output="$({ require_fixed "${fixture}.missing" 'present contract' 'WRONG_MISSING'; } 2>&1)" \
        || status=$?
    rm -f -- "${fixture}"
    if [[ "${status}" -ne 1 \
            || "${output}" != *'grep failed while checking '* \
            || "${output}" == *'WRONG_MISSING'* ]]; then
        fail 'P4-C2-B verifier could not distinguish a grep I/O error from a missing pattern'
    fi
}

is_reviewed_d3a_production_path() {
    case "$1" in
        src/main/java/com/yo1no/gramarye/Gramarye.java | \
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
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillDefinitionSubmissionGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionPort.java) return 0 ;;
        *) return 1 ;;
    esac
}

verify_production_freeze() {
    local changed=''
    local path=''
    local untracked=''
    local status=0
    git diff --quiet HEAD -- src/main/resources || status=$?
    if [[ "${status}" -ne 0 ]]; then
        fail 'P4-D3-A must not modify tracked production resources'
    fi
    status=0
    changed="$(git diff --name-only HEAD -- src/main/java)" || status=$?
    if [[ "${status}" -ne 0 ]]; then
        fail "git failed while checking tracked production Java (exit ${status})"
    fi
    while IFS= read -r path; do
        [[ -z "${path}" ]] && continue
        is_reviewed_d3a_production_path "${path}" \
            || fail "production Java changed outside exact current P4-D3-A allowlist: ${path}"
    done <<< "${changed}"
    status=0
    untracked="$(git ls-files --others --exclude-standard -- \
        src/main/java src/main/resources)" || status=$?
    if [[ "${status}" -ne 0 ]]; then
        fail "git failed while checking untracked production paths (exit ${status})"
    fi
    while IFS= read -r path; do
        [[ -z "${path}" ]] && continue
        is_reviewed_d3a_production_path "${path}" \
            || fail "untracked production path escaped exact current P4-D3-A allowlist: ${path}"
    done <<< "${untracked}"
}

verify_sources() {
    local package_path='com/yo1no/gramarye/magic/definition/player'
    local store_path='com/yo1no/gramarye/magic/definition/store'
    local source=''
    local literal=''

    PRODUCTION_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-production.XXXXXX")" \
        || fail 'P4-C2-B verifier could not create production source list'
    PROBE_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-probe.XXXXXX")" \
        || fail 'P4-C2-B verifier could not create probe source list'
    GAME_TEST_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-gametest.XXXXXX")" \
        || fail 'P4-C2-B verifier could not create GameTest source list'
    collect_java_files src/main/java "${PRODUCTION_SOURCE_LIST}"
    collect_java_files src/p4C2Probe/java "${PROBE_SOURCE_LIST}"
    collect_java_files src/p4C2GameTest/java "${GAME_TEST_SOURCE_LIST}"
    require_nul_entry_count "${PROBE_SOURCE_LIST}" 9 \
        'P4-C2-B probe source set must contain exactly nine reviewed files'
    require_nul_entry_count "${GAME_TEST_SOURCE_LIST}" 2 \
        'P4-C2-B GameTest source set must contain exactly two reviewed files'

    for source in \
        P4C2FileVerifier \
        P4C2FixtureBuilder \
        P4C2FixtureManifest \
        P4C2Hashing \
        P4C2ProbeCase \
        P4C2ProbeMain \
        P4C2ProbeSummary \
        P4C2RunMode; do
        require_regular_file \
            "src/p4C2Probe/java/${package_path}/${source}.java" \
            "P4-C2-B probe source missing: ${source}.java"
    done
    for source in P4C2MemoryGameTests P4C2ProbeServerLifecycle; do
        require_regular_file \
            "src/p4C2GameTest/java/${package_path}/${source}.java" \
            "P4-C2-B GameTest source missing: ${source}.java"
    done
    require_regular_file \
        "src/p4C2Probe/java/${store_path}/P4C2StoreProbe.java" \
        'P4-C2-B self-contained bounded Store probe is missing from its isolated source set'

    for literal in \
        'PendingAttachmentJournal' \
        'SkillDefinitionSubmissionService' \
        'RootCollector' \
        'RootIndex' \
        'OfflineRoot' \
        'Reconciliation' \
        'CustomPacketPayload' \
        'PayloadRegistrar' \
        'PacketDistributor' \
        'net.minecraft.client' \
        'java.lang.reflect' \
        'setAccessible' \
        'sun.misc.Unsafe' \
        '@SuppressWarnings' \
        '.commit(' \
        '.reclaim('; do
        forbid_fixed_in_file_list "${PROBE_SOURCE_LIST}" "${literal}" \
            "P4-C2-B probe contains later/test-bypass surface ${literal}"
        forbid_fixed_in_file_list "${GAME_TEST_SOURCE_LIST}" "${literal}" \
            "P4-C2-B GameTest contains later/test-bypass surface ${literal}"
    done
    for literal in \
        'P4C2ProbeMain' \
        'P4C2MemoryGameTests' \
        '@GameTestHolder("gramarye_p4_c2")'; do
        forbid_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" "${literal}" \
            "P4-C2-B fixture leaked into production Java (${literal})"
    done

    require_regular_file \
        'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' \
        'P4-D3-A reviewed recovery service is missing'
    require_fixed \
        'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' \
        'PlayerEvent.PlayerLoggedInEvent' \
        'P4-D3-A reviewed login recovery event owner is missing'
    while IFS= read -r -d '' source; do
        if [[ "${source}" != \
                'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' ]]; then
            forbid_fixed "${source}" 'PlayerEvent' \
                'PlayerEvent escaped the exact P4-D3-A recovery-service allowlist'
        fi
    done < "${PRODUCTION_SOURCE_LIST}"
}

verify_build_contract() {
    local literal=''
    local task=''

    require_ere_count build.gradle "sourceSets\.create\('p4C2[A-Za-z0-9_]*'\)" 2 \
        'P4-C2-B must declare exactly its two reviewed source sets'
    for literal in \
        "sourceSets.create('p4C2Probe')" \
        "sourceSets.create('p4C2GameTest')" \
        "tasks.register('generateP4C2GameTestResources', Sync)" \
        'data/gramarye_p4_c2/structure/p4_c2_probe.nbt' \
        'addModdingDependenciesTo(p4C2ProbeSourceSet)' \
        'addModdingDependenciesTo(p4C2GameTestSourceSet)' \
        'sourceSet(p4C2ProbeSourceSet)' \
        'sourceSet(p4C2GameTestSourceSet)' \
        'p4C2HeapProbe' \
        'add(p4C2ProbeSourceSet.implementationConfigurationName, sourceSets.main.output)' \
        'add(p4C2GameTestSourceSet.implementationConfigurationName, sourceSets.main.output)' \
        'add(p4C2GameTestSourceSet.implementationConfigurationName, p4C2ProbeSourceSet.output)' \
        'testImplementation p4C2ProbeSourceSet.output' \
        'p4C2FixedHeapJvmArgs' \
        "'-Xms512m'" \
        "'-Xmx1024m'" \
        "'-XX:+ExitOnOutOfMemoryError'" \
        'Duration.ofSeconds(600)' \
        'Duration.ofSeconds(180)' \
        "tasks.register('prepareP4C2Worlds'" \
        "'verifyP4C2AConfiguration', Exec" \
        "'verifyP4C2Configuration', Exec" \
        "tasks.register('p4C2FixedHeapGate')"; do
        require_fixed build.gradle "${literal}" \
            "P4-C2-B build contract missing ${literal}"
    done
    forbid_fixed build.gradle 'testImplementation p4C2GameTestSourceSet.output' \
        'P4-C2-B dedicated holder must not join ordinary JUnit runtime'
    forbid_fixed build.gradle 'runtimeElements.extendsFrom p4C2' \
        'P4-C2-B source set must not become a published runtime variant'
    for literal in \
        'add(p4C2ProbeSourceSet.implementationConfigurationName, p4A3ProbeSourceSet.output)' \
        'add(p4C2ProbeSourceSet.implementationConfigurationName, p4B2ProbeSourceSet.output)' \
        'add(p4C2GameTestSourceSet.implementationConfigurationName, p4A3ProbeSourceSet.output)' \
        'add(p4C2GameTestSourceSet.implementationConfigurationName, p4B2ProbeSourceSet.output)'; do
        forbid_fixed build.gradle "${literal}" \
            'P4-C2-B dedicated runtime must not depend on earlier probe outputs'
    done

    require_ere_count build.gradle \
        "^[[:space:]]+p4C2(Ready|ReadyRestart|PreservedRaw|PreservedRawRestart|Oversize|OversizeRestart)Server[[:space:]]*\{" \
        6 'P4-C2-B must declare exactly six dedicated run configurations'
    require_ere_count build.gradle \
        "tasks\.named\('runP4C2(Ready|ReadyRestart|PreservedRaw|PreservedRawRestart|Oversize|OversizeRestart)Server',[[:space:]]*JavaExec\)" \
        6 'P4-C2-B must bind exactly six dedicated JavaExec tasks'
    for task in \
        runP4C2ReadyServer \
        verifyP4C2ReadyFirst \
        runP4C2ReadyRestartServer \
        verifyP4C2ReadyRestart \
        runP4C2PreservedRawServer \
        verifyP4C2PreservedRawFirst \
        runP4C2PreservedRawRestartServer \
        verifyP4C2PreservedRawRestart \
        runP4C2OversizeServer \
        verifyP4C2OversizeFirst \
        runP4C2OversizeRestartServer \
        verifyP4C2OversizeRestart; do
        require_fixed build.gradle "${task}" \
            "P4-C2-B serialized task graph missing ${task}"
    done

    for literal in \
        "'ready-first'" \
        "'ready-restart'" \
        "'preserved-raw-first'" \
        "'preserved-raw-restart'" \
        "'oversize-first'" \
        "'oversize-restart'" \
        "'gramarye.p4c2.runMode'" \
        'p4C2ReadyGameDirectory' \
        'p4C2PreservedRawGameDirectory' \
        'p4C2OversizeGameDirectory' \
        'P4B2ProbeMain' \
        "'prepare-full'" \
        'prepareP4C2PreservedStore' \
        'resetP4C2Worlds,' \
        'mustRunAfter(prepareP4C2PreservedStore)' \
        'finalizedBy(verifyP4C2ReadyFirst)' \
        'finalizedBy(verifyP4C2ReadyRestart)' \
        'finalizedBy(verifyP4C2PreservedRawFirst)' \
        'finalizedBy(verifyP4C2PreservedRawRestart)' \
        'finalizedBy(verifyP4C2OversizeFirst)' \
        'finalizedBy(verifyP4C2OversizeRestart)' \
        'dependsOn(runP4C2ReadyServer, verifyP4C2ReadyFirst)' \
        'dependsOn(runP4C2ReadyRestartServer, verifyP4C2ReadyRestart)' \
        'dependsOn(runP4C2PreservedRawServer, verifyP4C2PreservedRawFirst)' \
        'dependsOn(runP4C2PreservedRawRestartServer, verifyP4C2PreservedRawRestart)' \
        'dependsOn(runP4C2OversizeServer, verifyP4C2OversizeFirst)'; do
        require_fixed build.gradle "${literal}" \
            "P4-C2-B preparation/serialization contract missing ${literal}"
    done
    require_fixed_count build.gradle 'jvmArguments.addAll(p4C2FixedHeapJvmArgs)' 6 \
        'Every P4-C2-B server process must use the fixed 1 GiB JVM arguments'
    require_fixed build.gradle 'jvmArgs(p4C2FixedHeapJvmArgs)' \
        'Every P4-C2-B external verifier must use the fixed 1 GiB JVM arguments'
}

verify_fixture_and_lifecycle_contracts() {
    local probe_file='src/p4C2Probe/java/com/yo1no/gramarye/magic/definition/player/P4C2FixtureBuilder.java'
    local manifest='src/p4C2Probe/java/com/yo1no/gramarye/magic/definition/player/P4C2FixtureManifest.java'
    local summary='src/p4C2Probe/java/com/yo1no/gramarye/magic/definition/player/P4C2ProbeSummary.java'
    local game_test='src/p4C2GameTest/java/com/yo1no/gramarye/magic/definition/player/P4C2MemoryGameTests.java'
    local store_probe='src/p4C2Probe/java/com/yo1no/gramarye/magic/definition/store/P4C2StoreProbe.java'
    local literal=''

    for literal in 16_777_211 16_777_212 16_777_216 16_777_217; do
        require_fixed "${probe_file}" "${literal}" \
            "P4-C2-B fixture lost exact raw/count coordinate ${literal}"
    done
    for literal in \
        'store_carrier_bytes' \
        'store_history_count' \
        'store_revision_count' \
        'store_semantic_checksum' \
        'store_source_primary_checksum' \
        'store_rewrite_expected'; do
        require_fixed "${manifest}" "${literal}" \
            "P4-C2-B manifest lost bounded Store evidence ${literal}"
    done
    for literal in \
        'p4-b2-manifest.properties' \
        'FULL_STORE_MINIMUM_BYTES = 63 * 1_024 * 1_024' \
        '"full-first-load-save".equals(required(values, "phase"))' \
        'expected_store_bytes' \
        'canonical_store_sha256'; do
        require_fixed "${store_probe}" "${literal}" \
            "P4-C2-B self-contained Store probe lost manifest/assertion marker ${literal}"
    done
    for literal in P4B2FixtureManifest P4B2FixtureBuilder P4B2Hashing; do
        forbid_fixed "${store_probe}" "${literal}" \
            "P4-C2-B isolated Store probe retained runtime dependency ${literal}"
    done
    require_fixed \
        'src/p4B2Probe/java/com/yo1no/gramarye/magic/definition/store/P4B2FixtureBuilder.java' \
        'FULL_SIZE_MINIMUM_BYTES = 63 * 1_024 * 1_024' \
        'P4-C2-B Store reuse lost the actual P4-B2 63 MiB minimum'
    for literal in \
        'current.setHealth(0.0F)' \
        'ServerboundClientCommandPacket.Action.PERFORM_RESPAWN' \
        'current.connection.handleClientCommand' \
        'current.changeDimension(new DimensionTransition' \
        'Level.END' \
        'current.showEndCredits()' \
        'server.getPlayerList().saveAll()' \
        'NbtIo.readCompressed' \
        'NbtAccounter.create'; do
        require_fixed_in_file_list "${GAME_TEST_SOURCE_LIST}" "${literal}" \
            "P4-C2-B GameTest set lost actual lifecycle/readback marker ${literal}"
    done
    for literal in \
        'P4C2_PROBE_OK' \
        'store_bytes=' \
        'store_histories=' \
        'store_revisions=' \
        'store_checksum='; do
        require_fixed "${summary}" "${literal}" \
            "P4-C2-B bounded summary lost marker ${literal}"
    done
    require_ere_count "${game_test}" '^[[:space:]]*@GameTest\(' 1 \
        'P4-C2-B must expose exactly one property-dispatched GameTest'
    require_fixed "${game_test}" '@GameTestHolder("gramarye_p4_c2")' \
        'P4-C2-B GameTest holder lost its isolated namespace'
    require_fixed "${game_test}" 'templateNamespace = "gramarye_p4_c2"' \
        'P4-C2-B GameTest holder lost its isolated template namespace'
    forbid_fixed_in_file_list "${GAME_TEST_SOURCE_LIST}" \
        'PlayerSkillAttachmentSerializer.INSTANCE' \
        'P4-C2-B must not replace actual lifecycle paths with direct serializer calls'
}

verify_ci_contract() {
    local literal=''
    C_JOB_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c-job.XXXXXX")" \
        || fail 'P4-C2-B verifier could not create workflow block'
    extract_yaml_job .github/workflows/build.yml p4-c-memory-gates "${C_JOB_BLOCK}"
    for literal in \
        '  p4-c-memory-gates:' \
        '    name: P4-C memory gates' \
        '    needs:' \
        '      - build' \
        '      - p4-a3-memory-gates' \
        '      - p4-b-memory-gates' \
        '    timeout-minutes: 30' \
        './gradlew --no-daemon --console=plain verifyP4C2Configuration' \
        './gradlew --no-daemon --console=plain p4C2FixedHeapGate'; do
        require_fixed "${C_JOB_BLOCK}" "${literal}" \
            "P4-C memory job missing ${literal}"
    done
    require_ere_count "${C_JOB_BLOCK}" \
        '^[[:space:]]+- (build|p4-a3-memory-gates|p4-b-memory-gates)$' 3 \
        'P4-C memory job must depend on exactly the three prior required jobs'
    for literal in 'continue-on-error' 'allow-failure' '|| true' '--exclude-task'; do
        forbid_fixed "${C_JOB_BLOCK}" "${literal}" \
            "P4-C memory job contains failure/skip escape ${literal}"
    done
    forbid_ere "${C_JOB_BLOCK}" '^[[:space:]]*if:' \
        'P4-C memory job must not conditionally skip its gates'
}

verify_compiled_outputs_and_jar() {
    local package_path='com/yo1no/gramarye/magic/definition/player'
    local store_path='com/yo1no/gramarye/magic/definition/store'
    local source=''
    local jar_path=''
    local status=0
    local jar_count=0

    for source in \
        P4C2FileVerifier \
        P4C2FixtureBuilder \
        P4C2FixtureManifest \
        P4C2Hashing \
        P4C2ProbeCase \
        P4C2ProbeMain \
        P4C2ProbeSummary \
        P4C2RunMode; do
        require_regular_file "build/classes/java/p4C2Probe/${package_path}/${source}.class" \
            "P4-C2-B compiled probe output missing ${source}.class"
    done
    require_regular_file \
        "build/classes/java/p4C2Probe/${store_path}/P4C2StoreProbe.class" \
        'P4-C2-B compiled self-contained Store probe output is missing'
    for source in P4C2MemoryGameTests P4C2ProbeServerLifecycle; do
        require_regular_file "build/classes/java/p4C2GameTest/${package_path}/${source}.class" \
            "P4-C2-B compiled GameTest output missing ${source}.class"
    done
    require_regular_file \
        'build/resources/p4C2GameTest/data/gramarye_p4_c2/structure/p4_c2_probe.nbt' \
        'P4-C2-B generated structure is missing from its isolated output'

    JAR_FILE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-jars.XXXXXX")" \
        || fail 'P4-C2-B verifier could not create JAR list'
    JAR_LISTING="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-jar.XXXXXX")" \
        || fail 'P4-C2-B verifier could not create JAR listing'
    LC_ALL=C find build/libs -maxdepth 1 -type f -name 'gramarye-*.jar' -print0 \
        > "${JAR_FILE_LIST}" || status=$?
    if [[ "${status}" -ne 0 ]]; then
        fail "find failed while checking production JARs (exit ${status})"
    fi
    while IFS= read -r -d '' jar_path; do
        jar_count=$((jar_count + 1))
        status=0
        jar tf "${jar_path}" > "${JAR_LISTING}" || status=$?
        if [[ "${status}" -ne 0 ]]; then
            fail "jar failed while checking ${jar_path} (exit ${status})"
        fi
        for source in \
            P4C2 p4C2Probe p4C2GameTest gramarye_p4_c2 \
            P4D3 p4D3Probe p4D3GameTest gramarye_p4_d3; do
            forbid_fixed "${JAR_LISTING}" "${source}" \
                "P4-C2-B fixture leaked into production JAR ${jar_path} (${source})"
        done
        for source in \
            P4D2ApiGateTest \
            P4D2BApiGateTest \
            SkillDraftCreationServiceTest \
            SkillDefinitionSubmissionServiceTest \
            SkillSubmissionCompositionOutcomeTest \
            SkillSubmissionPolicyProviderTest \
            SkillSubmissionPreparationPipelineTest; do
            forbid_fixed "${JAR_LISTING}" "${source}" \
                "P4-D2-A test fixture leaked into production JAR ${jar_path} (${source})"
        done
    done < "${JAR_FILE_LIST}"
    if [[ "${jar_count}" -ne 1 ]]; then
        fail "P4-C2-B expects one deployable production JAR (found ${jar_count})"
    fi
}

main() {
    verify_search_helpers
    forbid_ere scripts/verify-p4-c2-b-configuration.sh \
        '(^|[;&|()<>`[:space:]])([^;&|()<>`[:space:]]*/)?r[g]([;&|()<>`[:space:]]|$)' \
        'P4-C2-B portable verifier must not invoke rg'
    verify_production_freeze
    verify_sources
    verify_build_contract
    verify_fixture_and_lifecycle_contracts
    verify_ci_contract
    verify_compiled_outputs_and_jar
    bash scripts/verify-p4-c2-a-configuration.sh
    bash scripts/verify-p4-d1-configuration.sh
    printf '%s\n' \
        'Verified P4-C2-B source sets, six fixed-heap lifecycles, CI, phase bounds, and JAR isolation.'
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
