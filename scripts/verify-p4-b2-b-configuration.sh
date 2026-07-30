#!/usr/bin/env bash
set -euo pipefail

# Keep the verifier independent of developer-only search tools and shell aliases while retaining
# the JDK selected by Gradle/CI for the production-JAR inspection.
if [[ -n "${JAVA_HOME:-}" ]]; then
    PATH="${JAVA_HOME}/bin:/usr/bin:/bin:/usr/sbin:/sbin"
else
    PATH='/usr/bin:/bin:/usr/sbin:/sbin'
fi
export PATH

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

for required_tool in grep find mktemp rm jar dirname pwd; do
    command -v "${required_tool}" >/dev/null 2>&1 \
        || fail "P4-B2-B configuration verifier cannot find required tool: ${required_tool}"
done

REPO_ROOT=''
if REPO_ROOT="$(
    cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd
)"; then
    :
else
    fail 'P4-B2-B configuration verifier could not resolve the repository root'
fi

PRODUCTION_SOURCE_LIST=''
PROBE_SOURCE_LIST=''
GAME_TEST_SOURCE_LIST=''
JAR_FILE_LIST=''
JAR_LISTING=''
B2_JOB_BLOCK=''
A3_JOB_BLOCK=''
HELPER_FIXTURE=''
COMMONS_COMPRESS_VERSION=''

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
        0)
            return 0
            ;;
        1)
            fail "${message}"
            ;;
        *)
            grep_failed "${file}" "${status}"
            ;;
    esac
}

require_ere() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    local status=0

    LC_ALL=C grep -Eq -- "${pattern}" "${file}" || status=$?
    case "${status}" in
        0)
            return 0
            ;;
        1)
            fail "${message}"
            ;;
        *)
            grep_failed "${file}" "${status}"
            ;;
    esac
}

forbid_fixed() {
    local file="$1"
    local needle="$2"
    local message="$3"
    local status=0

    LC_ALL=C grep -Fq -- "${needle}" "${file}" || status=$?
    case "${status}" in
        0)
            fail "${message}"
            ;;
        1)
            return 0
            ;;
        *)
            grep_failed "${file}" "${status}"
            ;;
    esac
}

forbid_ere() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    local status=0

    LC_ALL=C grep -Eq -- "${pattern}" "${file}" || status=$?
    case "${status}" in
        0)
            fail "${message}"
            ;;
        1)
            return 0
            ;;
        *)
            grep_failed "${file}" "${status}"
            ;;
    esac
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
        0)
            ;;
        1)
            actual=0
            ;;
        *)
            grep_failed "${file}" "${status}"
            ;;
    esac
    if [[ ! "${actual}" =~ ^[0-9]+$ ]]; then
        fail "grep returned a non-numeric match count while checking ${file}"
    fi
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

    if [[ ! -d "${root}" ]]; then
        fail "P4-B2-B configuration verifier could not inspect missing source root: ${root}"
    fi
    LC_ALL=C find "${root}" -type f -name '*.java' -print0 > "${destination}" || status=$?
    if [[ "${status}" -ne 0 ]]; then
        fail "find failed while checking ${root} (exit ${status})"
    fi
    if [[ ! -s "${destination}" ]]; then
        fail "P4-B2-B configuration verifier could not inspect ${root}: no Java files found"
    fi
}

require_nul_entry_count() {
    local file_list="$1"
    local expected="$2"
    local message="$3"
    local entry=''
    local actual=0

    while IFS= read -r -d '' entry; do
        actual=$((actual + 1))
    done < "${file_list}"
    if [[ "${actual}" -ne "${expected}" ]]; then
        fail "${message} (expected ${expected}, found ${actual})"
    fi
}

forbid_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local message="$3"
    local file=''

    while IFS= read -r -d '' file; do
        forbid_fixed "${file}" "${needle}" "${message}: ${file}"
    done < "${file_list}"
}

forbid_ere_in_file_list() {
    local file_list="$1"
    local pattern="$2"
    local message="$3"
    local file=''

    while IFS= read -r -d '' file; do
        forbid_ere "${file}" "${pattern}" "${message}: ${file}"
    done < "${file_list}"
}

extract_yaml_job() {
    local workflow="$1"
    local job_id="$2"
    local destination="$3"
    local line=''
    local found=0

    : > "${destination}"
    while IFS= read -r line || [[ -n "${line}" ]]; do
        if [[ "${line}" == "  ${job_id}:" ]]; then
            found=1
        elif [[ "${found}" -eq 1 \
            && "${line}" =~ ^[[:space:]]{2}[A-Za-z0-9_-]+:$ ]]; then
            break
        fi
        if [[ "${found}" -eq 1 ]]; then
            printf '%s\n' "${line}" >> "${destination}"
        fi
    done < "${workflow}"
    if [[ "${found}" -ne 1 || ! -s "${destination}" ]]; then
        fail "P4-B2-B configuration verifier could not isolate workflow job ${job_id}"
    fi
}

cleanup() {
    local temporary=''

    for temporary in \
        "${PRODUCTION_SOURCE_LIST}" \
        "${PROBE_SOURCE_LIST}" \
        "${GAME_TEST_SOURCE_LIST}" \
        "${JAR_FILE_LIST}" \
        "${JAR_LISTING}" \
        "${B2_JOB_BLOCK}" \
        "${A3_JOB_BLOCK}" \
        "${HELPER_FIXTURE}"; do
        if [[ -n "${temporary}" ]]; then
            rm -f -- "${temporary}"
        fi
    done
}

verify_search_helpers() {
    local missing_output=''
    local forbidden_output=''
    local tool_error_output=''
    local count_output=''
    local status=0

    HELPER_FIXTURE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-b2-helper-fixture.XXXXXX")" \
        || fail 'P4-B2-B configuration verifier could not create its helper fixture'
    printf 'present contract\npresent contract\n' > "${HELPER_FIXTURE}"

    require_fixed \
        "${HELPER_FIXTURE}" \
        'present contract' \
        'P4-B2-B verifier self-check lost fixed required matching'
    require_ere \
        "${HELPER_FIXTURE}" \
        '^present contract$' \
        'P4-B2-B verifier self-check lost ERE required matching'
    require_ere_count \
        "${HELPER_FIXTURE}" \
        '^present contract$' \
        2 \
        'P4-B2-B verifier self-check lost exact-count matching'
    forbid_fixed \
        "${HELPER_FIXTURE}" \
        'absent contract' \
        'P4-B2-B verifier self-check misclassified an absent fixed pattern'
    forbid_ere \
        "${HELPER_FIXTURE}" \
        '^absent contract$' \
        'P4-B2-B verifier self-check misclassified an absent ERE pattern'

    missing_output="$(
        {
            require_fixed \
                "${HELPER_FIXTURE}" \
                'missing contract' \
                'EXPECTED_MISSING_CONFIGURATION'
        } 2>&1
    )" || status=$?
    if [[ "${status}" -ne 1 || "${missing_output}" != 'EXPECTED_MISSING_CONFIGURATION' ]]; then
        fail 'P4-B2-B verifier self-check could not distinguish a missing pattern'
    fi

    status=0
    forbidden_output="$(
        {
            forbid_fixed \
                "${HELPER_FIXTURE}" \
                'present contract' \
                'EXPECTED_FORBIDDEN_CONFIGURATION'
        } 2>&1
    )" || status=$?
    if [[ "${status}" -ne 1 \
        || "${forbidden_output}" != 'EXPECTED_FORBIDDEN_CONFIGURATION' ]]; then
        fail 'P4-B2-B verifier self-check could not detect a forbidden pattern'
    fi

    status=0
    count_output="$(
        {
            require_ere_count \
                "${HELPER_FIXTURE}" \
                '^present contract$' \
                1 \
                'EXPECTED_COUNT_MISMATCH'
        } 2>&1
    )" || status=$?
    if [[ "${status}" -ne 1 \
        || "${count_output}" != 'EXPECTED_COUNT_MISMATCH (expected 1, found 2)' ]]; then
        fail 'P4-B2-B verifier self-check could not detect a count mismatch'
    fi

    status=0
    tool_error_output="$(
        {
            require_fixed \
                "${HELPER_FIXTURE}.missing" \
                'present contract' \
                'MUST_NOT_BE_REPORTED_AS_MISSING_CONFIGURATION'
        } 2>&1
    )" || status=$?
    if [[ "${status}" -ne 1 \
        || "${tool_error_output}" != *'grep failed while checking '* \
        || "${tool_error_output}" != *'(exit 2)'* \
        || "${tool_error_output}" == *'MUST_NOT_BE_REPORTED_AS_MISSING_CONFIGURATION'* ]]; then
        fail 'P4-B2-B verifier self-check could not distinguish a grep error'
    fi
}

verify_p4_a3_contract_markers() {
    local literal=''

    for literal in \
        "sourceSets.create('p4A3Probe')" \
        "sourceSets.create('p4A3GameTest')" \
        "tasks.register('generateP4A3GameTestResources', Sync)" \
        'addModdingDependenciesTo(p4A3ProbeSourceSet)' \
        'addModdingDependenciesTo(p4A3GameTestSourceSet)' \
        "'p4A3HeapProbeManySmall', 'many-small'" \
        "'p4A3HeapProbeNearEntry', 'near-entry'" \
        "'p4A3HeapProbeMixed', 'mixed'" \
        "tasks.register('p4A3HeapProbe')" \
        "tasks.named('runP4A3HeapProbeServer', JavaExec)" \
        'def verifyP4A3BConfiguration = tasks.register(' \
        "'verifyP4A3BConfiguration', Exec)" \
        "'gramarye_p4_a3'"; do
        require_fixed \
            build.gradle \
            "${literal}" \
            "P4-B2-B check detected a missing P4-A3 contract marker: ${literal}"
    done

    A3_JOB_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-a3-job.XXXXXX")" \
        || fail 'P4-B2-B configuration verifier could not create the P4-A3 job fixture'
    extract_yaml_job .github/workflows/build.yml p4-a3-memory-gates "${A3_JOB_BLOCK}"
    for literal in \
        '  p4-a3-memory-gates:' \
        '    name: P4-A3 memory gates' \
        '    needs: build' \
        '    timeout-minutes: 15' \
        '        timeout-minutes: 2' \
        '        timeout-minutes: 5' \
        '        timeout-minutes: 6' \
        './gradlew --no-daemon --console=plain verifyP4A3BConfiguration' \
        './gradlew --no-daemon --console=plain p4A3HeapProbe' \
        './gradlew --no-daemon --console=plain runP4A3HeapProbeServer'; do
        require_fixed \
            "${A3_JOB_BLOCK}" \
            "${literal}" \
            "P4-B2-B check detected a changed P4-A3 CI contract marker: ${literal}"
    done
}

verify_b2_build_contracts() {
    local literal=''
    local run_task=''

    require_ere_count \
        build.gradle \
        "sourceSets\\.create\\('p4B2[A-Za-z0-9_]*'\\)" \
        2 \
        'P4-B2-B must declare exactly its two reviewed source sets'
    for literal in \
        "sourceSets.create('p4B2Probe')" \
        "sourceSets.create('p4B2GameTest')" \
        "tasks.register('generateP4B2GameTestResources', Sync)" \
        "'data/gramarye_p4_b2/structure/p4_b2_probe.nbt'" \
        'addModdingDependenciesTo(p4B2ProbeSourceSet)' \
        'addModdingDependenciesTo(p4B2GameTestSourceSet)' \
        'sourceSet(p4B2ProbeSourceSet)' \
        'sourceSet(p4B2GameTestSourceSet)' \
        "mods.named('p4B2HeapProbe')" \
        "name.startsWith('p4B2') ? p4B2ProbeMod" \
        "name.startsWith('p4C2') ? p4C2ProbeMod : productionMod" \
        'add(p4B2ProbeSourceSet.implementationConfigurationName, sourceSets.main.output)' \
        'add(p4B2ProbeSourceSet.implementationConfigurationName, p4A3ProbeSourceSet.output)' \
        'add(p4B2GameTestSourceSet.implementationConfigurationName, sourceSets.main.output)' \
        'add(p4B2GameTestSourceSet.implementationConfigurationName, p4A3ProbeSourceSet.output)' \
        'add(p4B2GameTestSourceSet.implementationConfigurationName, p4B2ProbeSourceSet.output)' \
        'testImplementation p4B2ProbeSourceSet.output' \
        "tasks.register('resetP4B2HostileFnameWorld', Delete)" \
        "tasks.register('prepareP4B2HostileFnameWorld', JavaExec)" \
        "tasks.register('p4B2InvalidRestartGate')" \
        "tasks.register('verifyP4B2Configuration', Exec)" \
        "tasks.register('p4B2FixedHeapGate')" \
        'verifyP4A3BConfiguration' \
        "'scripts/verify-p4-b2-b-configuration.sh'"; do
        require_fixed \
            build.gradle \
            "${literal}" \
            "P4-B2-B configuration check missing ${literal} in build.gradle"
    done

    require_ere_count \
        build.gradle \
        "^[[:space:]]+p4B2(HeapLoadSave|HeapRestart|HostileFname|HostileFnameRestart|Malformed|MalformedRestart|Trailing|TrailingRestart|SecondMember|SecondMemberRestart)Server[[:space:]]*\\{$" \
        10 \
        'P4-B2-B must declare exactly ten reviewed run configurations'
    require_ere_count \
        build.gradle \
        "tasks\\.named\\('runP4B2[^']+Server',[[:space:]]*JavaExec\\)" \
        10 \
        'P4-B2-B must bind exactly ten reviewed JavaExec run tasks'

    for run_task in \
        runP4B2HeapLoadSaveServer \
        runP4B2HeapRestartServer \
        runP4B2HostileFnameServer \
        runP4B2HostileFnameRestartServer \
        runP4B2MalformedServer \
        runP4B2MalformedRestartServer \
        runP4B2TrailingServer \
        runP4B2TrailingRestartServer \
        runP4B2SecondMemberServer \
        runP4B2SecondMemberRestartServer; do
        require_fixed \
            build.gradle \
            "tasks.named('${run_task}', JavaExec)" \
            "P4-B2-B configuration check missing reviewed task ${run_task}"
    done

    for literal in \
        "'full-first-load-save'" \
        "'full-restart'" \
        "'hostile-fname-first'" \
        "'hostile-fname-restart'" \
        "'prepare-hostile-fname'" \
        "'verify-hostile-fname-first'" \
        "'verify-hostile-fname-restart'" \
        "'malformed-first'" \
        "'malformed-restart'" \
        "'trailing-first'" \
        "'trailing-restart'" \
        "'second-member-first'" \
        "'second-member-restart'"; do
        require_fixed \
            build.gradle \
            "${literal}" \
            "P4-B2-B configuration check missing run mode ${literal}"
    done

    for literal in \
        'def p4B2FixedHeapJvmArgs = [' \
        "'-Xms512m'" \
        "'-Xmx1024m'" \
        "'-XX:+ExitOnOutOfMemoryError'"; do
        require_fixed \
            build.gradle \
            "${literal}" \
            "P4-B2-B fixed-heap configuration check missing ${literal}"
    done
    require_ere_count \
        build.gradle \
        'jvmArguments\.addAll\(p4B2FixedHeapJvmArgs\)' \
        10 \
        'Every P4-B2-B server run must use the shared fixed-heap arguments exactly once'
    require_ere_count \
        build.gradle \
        'jvmArgs\(p4B2FixedHeapJvmArgs\)' \
        4 \
        'P4-B2-B preparation and file verification tasks lost their fixed-heap arguments'
    require_ere_count \
        build.gradle \
        'taskBefore\(tasks\.named\(p4B2GameTestSourceSet\.classesTaskName\)\)' \
        10 \
        'Every P4-B2-B run must compile its isolated GameTest source set exactly once'
    require_ere_count \
        build.gradle \
        'timeout\.set\(java\.time\.Duration\.ofSeconds\(600\)\)' \
        6 \
        'The four P4-B full-size runs, packaged smoke, and reviewed C2 successor chain must retain 600-second timeout declarations'
    require_ere_count \
        build.gradle \
        'timeout\.set\(java\.time\.Duration\.ofSeconds\(300\)\)' \
        6 \
        'The existing A3/P4-B declarations and two reviewed C2 preparations must retain 300-second timeout declarations'
    for literal in \
        'runP4B2MalformedServer,' \
        'runP4B2MalformedRestartServer,' \
        'runP4B2TrailingServer,' \
        'runP4B2TrailingRestartServer,' \
        'runP4B2SecondMemberServer,' \
        'runP4B2SecondMemberRestartServer,' \
        '].each { taskProvider ->' \
        'timeout.set(java.time.Duration.ofSeconds(300))'; do
        require_fixed \
            build.gradle \
            "${literal}" \
            "P4-B2-B invalid-run timeout group missing ${literal}"
    done

    for literal in \
        'dependsOn(prepareP4B2HeapWorld)' \
        'finalizedBy(verifyP4B2HeapLoadSaveOutput)' \
        'mustRunAfter(runP4B2HeapLoadSaveServer)' \
        'dependsOn(runP4B2HeapLoadSaveServer, verifyP4B2HeapLoadSaveOutput)' \
        'mustRunAfter(verifyP4B2HeapLoadSaveOutput)' \
        'finalizedBy(verifyP4B2HeapRestartOutput)' \
        'mustRunAfter(runP4B2HeapRestartServer)' \
        'dependsOn(prepareP4B2HostileFnameWorld)' \
        'mustRunAfter(verifyP4B2HeapRestartOutput)' \
        'finalizedBy(verifyP4B2HostileFnameOutput)' \
        'mustRunAfter(runP4B2HostileFnameServer)' \
        'dependsOn(runP4B2HostileFnameServer, verifyP4B2HostileFnameOutput)' \
        'mustRunAfter(verifyP4B2HostileFnameOutput)' \
        'finalizedBy(verifyP4B2HostileFnameRestartOutput)' \
        'mustRunAfter(runP4B2HostileFnameRestartServer)' \
        'dependsOn(prepareP4B2InvalidWorlds)' \
        'mustRunAfter(verifyP4B2HostileFnameRestartOutput)' \
        'finalizedBy(verifyP4B2MalformedOutput)' \
        'mustRunAfter(runP4B2MalformedServer)' \
        'dependsOn(runP4B2MalformedServer, verifyP4B2MalformedOutput)' \
        'mustRunAfter(verifyP4B2MalformedOutput)' \
        'finalizedBy(verifyP4B2MalformedRestartOutput)' \
        'mustRunAfter(runP4B2MalformedRestartServer)' \
        'mustRunAfter(verifyP4B2MalformedRestartOutput)' \
        'finalizedBy(verifyP4B2TrailingOutput)' \
        'mustRunAfter(runP4B2TrailingServer)' \
        'dependsOn(runP4B2TrailingServer, verifyP4B2TrailingOutput)' \
        'mustRunAfter(verifyP4B2TrailingOutput)' \
        'finalizedBy(verifyP4B2TrailingRestartOutput)' \
        'mustRunAfter(runP4B2TrailingRestartServer)' \
        'mustRunAfter(verifyP4B2TrailingRestartOutput)' \
        'finalizedBy(verifyP4B2SecondMemberOutput)' \
        'mustRunAfter(runP4B2SecondMemberServer)' \
        'dependsOn(runP4B2SecondMemberServer, verifyP4B2SecondMemberOutput)' \
        'mustRunAfter(verifyP4B2SecondMemberOutput)' \
        'finalizedBy(verifyP4B2SecondMemberRestartOutput)' \
        'mustRunAfter(runP4B2SecondMemberRestartServer)' \
        'runP4B2HostileFnameRestartServer,' \
        'verifyP4B2HostileFnameRestartOutput,'; do
        require_fixed \
            build.gradle \
            "${literal}" \
            "P4-B2-B dependency/finalizer ordering check missing ${literal}"
    done
}

verify_runtime_packaging_contract() {
    local key=''
    local value=''
    local literal=''

    require_regular_file \
        gradle.properties \
        'P4-B2-R configuration verifier cannot inspect gradle.properties'
    require_ere_count \
        gradle.properties \
        '^commons_compress_version=' \
        1 \
        'P4-B2-R must define exactly one Commons Compress version property'
    require_ere_count \
        gradle.properties \
        '^commons_compress_version=1\.26\.0$' \
        1 \
        'P4-B2-R must lock Commons Compress to exactly 1.26.0'
    while IFS='=' read -r key value; do
        if [[ "${key}" == 'commons_compress_version' ]]; then
            COMMONS_COMPRESS_VERSION="${value}"
        fi
    done < gradle.properties
    if [[ "${COMMONS_COMPRESS_VERSION}" != '1.26.0' ]]; then
        fail 'P4-B2-R could not read the locked Commons Compress version truth'
    fi

    forbid_fixed \
        build.gradle \
        '1.26.0' \
        'P4-B2-R build.gradle must consume the version property, not duplicate its literal'
    require_ere_count \
        build.gradle \
        "providers\.gradleProperty\('commons_compress_version'\)\.get\(\)" \
        1 \
        'P4-B2-R must consume its Commons Compress version property exactly once'
    require_ere_count \
        build.gradle \
        '"org\.apache\.commons:commons-compress:\$\{commonsCompressVersion\}"' \
        1 \
        'P4-B2-R must construct exactly one property-backed Commons Compress coordinate'
    require_ere_count \
        build.gradle \
        'jarJar\(implementation\(commonsCompressCoordinate\)\)[[:space:]]*\{' \
        1 \
        'P4-B2-R must use the official wrapped Jar-in-Jar implementation seam exactly once'
    require_ere_count \
        build.gradle \
        'strictly "\[\$\{commonsCompressVersion\}\]"' \
        1 \
        'P4-B2-R Jar-in-Jar metadata must negotiate the exact locked version range'
    require_ere_count \
        build.gradle \
        '^[[:space:]]+prefer commonsCompressVersion$' \
        1 \
        'P4-B2-R Jar-in-Jar dependency must prefer the locked version'
    require_ere_count \
        build.gradle \
        '^[[:space:]]+additionalRuntimeClasspath commonsCompressCoordinate$' \
        1 \
        'P4-B2-R must add Commons Compress to every ModDev run exactly once'
    forbid_ere \
        build.gradle \
        '^[[:space:]]*implementation[[:space:]]+commonsCompressCoordinate' \
        'P4-B2-R must not retain a duplicate plain implementation declaration'

    for literal in \
        "p4B2PackagedServerInstaller {" \
        "configurations.named('p4B2PackagedServerInstaller')" \
        '"net.neoforged:neoforge:${neo_version}:installer"' \
        "tasks.register('prepareP4B2PackagedRuntimeFixture', JavaExec)" \
        "tasks.register('verifyP4B2PackagedArtifact', JavaExec)" \
        "tasks.register('stageP4B2PackagedRuntime', Sync)" \
        "tasks.register('runP4B2PackagedRuntimeSmoke', Exec)" \
        "tasks.register('verifyP4B2PackagedRuntimeOutput', JavaExec)" \
        "tasks.register('p4B2RuntimePackagingGate')" \
        "def deployableModArtifact = tasks.named('jar', Jar).flatMap { it.archiveFile }" \
        "'prepare-packaged-runtime'" \
        "'verify-packaged-artifact'" \
        "'verify-packaged-runtime'" \
        "'scripts/run-p4-b2-packaged-runtime-smoke.sh'" \
        'from(deployableModArtifact)' \
        'dependsOn(verifyP4B2PackagedRuntimeOutput)' \
        'p4B2RuntimePackagingGate,'; do
        require_fixed \
            build.gradle \
            "${literal}" \
            "P4-B2-R packaged artifact/task graph is missing ${literal}"
    done

    require_regular_file \
        scripts/run-p4-b2-packaged-runtime-smoke.sh \
        'P4-B2-R packaged-runtime smoke script is missing or not a regular file'
    for literal in \
        'readonly PRIMARY_RELATIVE=' \
        'cp "$MOD_ARTIFACT" "$SERVER_ROOT/mods/gramarye.jar"' \
        'cp "$FIXTURE_ROOT/$PRIMARY_RELATIVE" "$SERVER_ROOT/$PRIMARY_RELATIVE"' \
        'Packaged runtime server must install exactly the Gramarye mod artifact' \
        '"$JAVA_EXECUTABLE" @user_jvm_args.txt @"$UNIX_ARGS" nogui' \
        "'-Xlog:class+load=info'" \
        'ClassNotFoundException|NoClassDefFoundError' \
        'commons-compress-$COMPRESS_VERSION.jar' \
        'P4B2_PACKAGED_SERVER_OK ready=true clean_shutdown=true nested_gzip_class=true'; do
        require_fixed \
            scripts/run-p4-b2-packaged-runtime-smoke.sh \
            "${literal}" \
            "P4-B2-R packaged-runtime smoke lost required marker ${literal}"
    done

    for literal in \
        'private static final String METADATA_PATH = NESTED_PREFIX + "metadata.json";' \
        'name.startsWith("org/apache/commons/compress/")' \
        'deployable artifact must contain one reviewed nested jar' \
        'verifyNestedJar(artifact, nestedEntry, expectedVersion);' \
        'foundClass |= GZIP_CLASS.equals(name);' \
        'foundLicense |= name.startsWith("META-INF/LICENSE");' \
        'foundNotice |= name.startsWith("META-INF/NOTICE");'; do
        require_fixed \
            'src/p4B2Probe/java/com/yo1no/gramarye/magic/definition/store/P4B2RuntimePackagingVerifier.java' \
            "${literal}" \
            "P4-B2-R controlled artifact verifier lost required marker ${literal}"
    done
}

verify_b2_ci_contract() {
    local literal=''

    B2_JOB_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-b2-job.XXXXXX")" \
        || fail 'P4-B2-B configuration verifier could not create the P4-B job fixture'
    extract_yaml_job .github/workflows/build.yml p4-b-memory-gates "${B2_JOB_BLOCK}"

    for literal in \
        '  p4-b-memory-gates:' \
        '    name: P4-B memory gates' \
        '    needs:' \
        '      - build' \
        '      - p4-a3-memory-gates' \
        '    timeout-minutes: 30' \
        '        timeout-minutes: 3' \
        '        timeout-minutes: 26' \
        '      - name: Run P4-B2-B fixed-heap valid, hostile-FNAME, and quarantine restart gates' \
        './gradlew --no-daemon --console=plain verifyP4B2Configuration' \
        './gradlew --no-daemon --console=plain p4B2FixedHeapGate'; do
        require_fixed \
            "${B2_JOB_BLOCK}" \
            "${literal}" \
            "P4-B2-B CI contract missing ${literal}"
    done
    require_ere_count \
        "${B2_JOB_BLOCK}" \
        '^[[:space:]]+timeout-minutes:' \
        3 \
        'P4-B memory job must keep exactly its 30/3/26-minute timeout declarations'
    require_ere_count \
        "${B2_JOB_BLOCK}" \
        '^[[:space:]]+- (build|p4-a3-memory-gates)$' \
        2 \
        'P4-B memory job must depend only on build and P4-A3 memory gates'
    require_ere_count \
        "${B2_JOB_BLOCK}" \
        'run: ./gradlew --no-daemon --console=plain verifyP4B2Configuration$' \
        1 \
        'P4-B memory job must run its configuration verifier exactly once'
    require_ere_count \
        "${B2_JOB_BLOCK}" \
        'run: ./gradlew --no-daemon --console=plain p4B2FixedHeapGate$' \
        1 \
        'P4-B memory job must run its required fixed-heap gate exactly once'

    for literal in \
        'continue-on-error' \
        'allow-failure' \
        '|| true' \
        '--exclude-task'; do
        forbid_fixed \
            "${B2_JOB_BLOCK}" \
            "${literal}" \
            "P4-B memory job must not contain a skip/failure escape: ${literal}"
    done
    forbid_ere \
        "${B2_JOB_BLOCK}" \
        '^[[:space:]]*if:' \
        'P4-B memory job must not conditionally skip its required gates'
    forbid_ere \
        "${B2_JOB_BLOCK}" \
        '(^|[[:space:]])-x([[:space:]]|$)' \
        'P4-B memory job must not exclude required Gradle tasks'
}

verify_b2_sources_and_outputs() {
    local literal=''
    local source=''
    local class_name=''
    local package_path='com/yo1no/gramarye/magic/definition/store'

    PRODUCTION_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-b2-production.XXXXXX")" \
        || fail 'P4-B2-B verifier could not create its production source list'
    PROBE_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-b2-probe.XXXXXX")" \
        || fail 'P4-B2-B verifier could not create its probe source list'
    GAME_TEST_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-b2-gametest.XXXXXX")" \
        || fail 'P4-B2-B verifier could not create its GameTest source list'
    collect_java_files src/main/java "${PRODUCTION_SOURCE_LIST}"
    collect_java_files src/p4B2Probe/java "${PROBE_SOURCE_LIST}"
    collect_java_files src/p4B2GameTest/java "${GAME_TEST_SOURCE_LIST}"
    require_nul_entry_count \
        "${PROBE_SOURCE_LIST}" \
        8 \
        'P4-B2-B probe source set must contain exactly its eight reviewed Java files'
    require_nul_entry_count \
        "${GAME_TEST_SOURCE_LIST}" \
        2 \
        'P4-B2-B GameTest source set must contain exactly its two reviewed Java files'

    for source in \
        P4B2ProbeSummary \
        P4B2FileVerifier \
        P4B2ProbeMain \
        P4B2Hashing \
        P4B2FixtureManifest \
        P4B2FixtureBuilder \
        P4B2ProbeCase \
        P4B2RuntimePackagingVerifier; do
        require_regular_file \
            "src/p4B2Probe/java/${package_path}/${source}.java" \
            "P4-B2-B probe source is missing or not a regular file: ${source}.java"
    done
    for source in P4B2MemoryGameTests P4B2ProbeServerLifecycle; do
        require_regular_file \
            "src/p4B2GameTest/java/${package_path}/${source}.java" \
            "P4-B2-B GameTest source is missing or not a regular file: ${source}.java"
    done

    require_fixed \
        "src/p4B2Probe/java/${package_path}/P4B2FixtureBuilder.java" \
        'static final int FULL_SIZE_MINIMUM_BYTES = 63 * 1_024 * 1_024;' \
        'P4-B2-B full-size fixture must assert a minimum uncompressed Store size of 63 MiB'
    require_fixed \
        "src/p4B2Probe/java/${package_path}/P4B2FixtureBuilder.java" \
        'carrier.storeByteCount() < FULL_SIZE_MINIMUM_BYTES' \
        'P4-B2-B fixture must enforce its 63 MiB minimum against the actual carrier'
    for literal in \
        'MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES' \
        'HOSTILE_FNAME_BYTE' \
        'Math.subtractExact' \
        'Files.size(basePrimary)' \
        'prepareHostileFname' \
        'requireExactMaximumHostileFname' \
        'requireCanonicalGzipWithoutFname'; do
        require_fixed \
            "src/p4B2Probe/java/${package_path}/P4B2FixtureBuilder.java" \
            "${literal}" \
            "P4-B2-B hostile-FNAME fixture contract missing ${literal}"
    done
    require_fixed \
        "src/p4B2Probe/java/${package_path}/P4B2FixtureManifest.java" \
        'source_fname_bytes' \
        'P4-B2-B hostile-FNAME fixture manifest lost its exact FNAME-byte count'
    require_fixed \
        "src/p4B2Probe/java/${package_path}/P4B2ProbeCase.java" \
        'HOSTILE_FNAME("hostile-fname")' \
        'P4-B2-B hostile-FNAME probe case lost its stable token'
    require_fixed \
        "src/p4B2Probe/java/${package_path}/P4B2ProbeCase.java" \
        'HOSTILE_FNAME_FIRST("hostile-fname-first"' \
        'P4-B2-B hostile-FNAME first-run mode lost its stable token'
    require_fixed \
        "src/p4B2Probe/java/${package_path}/P4B2ProbeCase.java" \
        'HOSTILE_FNAME_RESTART("hostile-fname-restart"' \
        'P4-B2-B hostile-FNAME restart mode lost its stable token'
    require_fixed \
        "src/p4B2GameTest/java/${package_path}/P4B2MemoryGameTests.java" \
        '@GameTestHolder("gramarye_p4_b2")' \
        'P4-B2-B GameTest holder lost its isolated namespace'
    require_fixed \
        "src/p4B2GameTest/java/${package_path}/P4B2MemoryGameTests.java" \
        'templateNamespace = "gramarye_p4_b2"' \
        'P4-B2-B GameTest template lost its isolated namespace'
    require_ere_count \
        "src/p4B2GameTest/java/${package_path}/P4B2MemoryGameTests.java" \
        '^[[:space:]]*@GameTest\(' \
        1 \
        'P4-B2-B must keep exactly one property-dispatched GameTest entry point'

    for literal in \
        'PlayerSkillAttachment' \
        'IAttachmentSerializer' \
        'PendingAttachmentJournal' \
        'SkillDefinitionSubmissionService' \
        'RootCollector' \
        'RootIndex' \
        'OfflineRoot' \
        'Reconciliation' \
        'CustomPacketPayload' \
        'StreamCodec' \
        'PayloadRegistrar' \
        'PacketDistributor' \
        'net.minecraft.client' \
        'Clientbound' \
        'Serverbound' \
        'java.lang.reflect' \
        'setAccessible' \
        'sun.misc.Unsafe' \
        '@SuppressWarnings' \
        '.commit('; do
        forbid_fixed_in_file_list \
            "${PROBE_SOURCE_LIST}" \
            "${literal}" \
            "P4-C or later/test-bypass domain appeared in P4-B2-B probe sources (${literal})"
        forbid_fixed_in_file_list \
            "${GAME_TEST_SOURCE_LIST}" \
            "${literal}" \
            "P4-C or later/test-bypass domain appeared in P4-B2-B GameTest sources (${literal})"
    done

    for literal in \
        'P4B2' \
        'p4B2Probe' \
        'p4B2GameTest' \
        'gramarye_p4_b2' \
        'P4C2Probe' \
        'P4C2MemoryGameTests' \
        '"gramarye_p4_c2"'; do
        forbid_fixed_in_file_list \
            "${PRODUCTION_SOURCE_LIST}" \
            "${literal}" \
            "P4-B2-B probe code leaked into production Java sources (${literal})"
    done

    # P4-C2-A and D1/D2 composition are reviewed by their own portable verifiers. D3-A adds one
    # exact login-event owner; offline-root, manual-clone, and networking surfaces remain absent.
    for literal in \
        'OfflineRoot' \
        'CustomPacketPayload' \
        'PayloadRegistrar' \
        'PacketDistributor'; do
        forbid_fixed_in_file_list \
            "${PRODUCTION_SOURCE_LIST}" \
            "${literal}" \
            "unreviewed later lifecycle/root/network surface appeared (${literal})"
    done
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
    require_regular_file \
        'scripts/verify-p4-c2-a-configuration.sh' \
        'P4-C2-A portable configuration verifier is missing'
    [[ -x scripts/verify-p4-c2-a-configuration.sh ]] \
        || fail 'P4-C2-A portable configuration verifier is not executable'

    for literal in NoClassDefFoundError LinkageError Error; do
        forbid_ere_in_file_list \
            "${PRODUCTION_SOURCE_LIST}" \
            "catch[[:space:]]*\\([^)]*(java\\.lang\\.)?${literal}([^[:alnum:]_\$]|$)" \
            "P4-B2-R production code must not catch dependency linkage failure (${literal})"
    done

    for class_name in \
        P4B2ProbeSummary \
        P4B2FileVerifier \
        P4B2ProbeMain \
        P4B2Hashing \
        P4B2FixtureManifest \
        P4B2FixtureBuilder \
        P4B2ProbeCase \
        P4B2RuntimePackagingVerifier; do
        require_regular_file \
            "build/classes/java/p4B2Probe/${package_path}/${class_name}.class" \
            "P4-B2-B verifier could not find compiled probe output ${class_name}.class"
    done
    for class_name in P4B2MemoryGameTests P4B2ProbeServerLifecycle; do
        require_regular_file \
            "build/classes/java/p4B2GameTest/${package_path}/${class_name}.class" \
            "P4-B2-B verifier could not find compiled GameTest output ${class_name}.class"
    done
    require_regular_file \
        'build/resources/p4B2GameTest/data/gramarye_p4_b2/structure/p4_b2_probe.nbt' \
        'P4-B2-B verifier could not find its generated isolated GameTest structure'
}

verify_production_jar_isolation() {
    local jar_path=''
    local literal=''
    local jar_count=0
    local status=0

    JAR_FILE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-b2-jars.XXXXXX")" \
        || fail 'P4-B2-B verifier could not create its production-JAR list'
    JAR_LISTING="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-b2-jar-listing.XXXXXX")" \
        || fail 'P4-B2-B verifier could not create its production-JAR listing'
    LC_ALL=C find \
        build/libs \
        -maxdepth 1 \
        -type f \
        -name 'gramarye-*.jar' \
        -print0 > "${JAR_FILE_LIST}" || status=$?
    if [[ "${status}" -ne 0 ]]; then
        fail "find failed while checking build/libs (exit ${status})"
    fi

    while IFS= read -r -d '' jar_path; do
        jar_count=$((jar_count + 1))
        status=0
        jar tf "${jar_path}" > "${JAR_LISTING}" || status=$?
        if [[ "${status}" -ne 0 ]]; then
            fail "jar failed while checking ${jar_path} (exit ${status})"
        fi
        for literal in \
            'P4A3' \
            'p4A3Probe' \
            'p4A3GameTest' \
            'gramarye_p4_a3' \
            'P4B2' \
            'p4B2Probe' \
            'p4B2GameTest' \
            'gramarye_p4_b2' \
            'P4C2' \
            'p4C2Probe' \
            'p4C2GameTest' \
            'gramarye_p4_c2'; do
            forbid_fixed \
                "${JAR_LISTING}" \
                "${literal}" \
                "P4-A3/P4-B2-B probe classes or resources leaked into ${jar_path} (${literal})"
        done
        require_fixed \
            "${JAR_LISTING}" \
            'META-INF/jarjar/metadata.json' \
            "P4-B2-R deployable artifact lacks Jar-in-Jar metadata: ${jar_path}"
        require_fixed \
            "${JAR_LISTING}" \
            "META-INF/jarjar/commons-compress-${COMMONS_COMPRESS_VERSION}.jar" \
            "P4-B2-R deployable artifact lacks its exact nested Commons Compress jar: ${jar_path}"
        require_ere_count \
            "${JAR_LISTING}" \
            '^META-INF/jarjar/[^/]+\.jar$' \
            1 \
            "P4-B2-R deployable artifact must contain exactly one reviewed nested jar: ${jar_path}"
        forbid_ere \
            "${JAR_LISTING}" \
            '^org/apache/commons/compress/' \
            "P4-B2-R must not shade Commons Compress classes into the root artifact: ${jar_path}"
    done < "${JAR_FILE_LIST}"
    if [[ "${jar_count}" -ne 1 ]]; then
        fail "P4-B2-R must have exactly one deployable Gramarye JAR (found ${jar_count})"
    fi
}

main() {
    cd "${REPO_ROOT}"
    trap cleanup EXIT

    verify_search_helpers
    forbid_ere \
        scripts/verify-p4-b2-b-configuration.sh \
        '(^|[;&|()<>`[:space:]])([^;&|()<>`[:space:]]*/)?r[g]([;&|()<>`[:space:]]|$)' \
        'P4-B2-B configuration verifier must not invoke a non-portable search tool'

    require_regular_file build.gradle 'P4-B2-B configuration verifier cannot inspect build.gradle'
    require_regular_file \
        .github/workflows/build.yml \
        'P4-B2-B configuration verifier cannot inspect the build workflow'
    verify_p4_a3_contract_markers
    verify_b2_build_contracts
    verify_runtime_packaging_contract
    verify_b2_ci_contract
    verify_b2_sources_and_outputs
    verify_production_jar_isolation
    bash scripts/verify-p4-d1-configuration.sh

    printf '%s\n' \
        'Verified P4-B2-B source sets, fixed-heap/restart tasks, CI, outputs, phase bounds, and JAR isolation.'
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
