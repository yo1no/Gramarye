#!/usr/bin/env bash

set -uo pipefail
umask 077

fail() {
    printf 'P9-S3-RD1 diagnostic runner: %s\n' "$1" >&2
    exit 2
}

sha256_file() {
    local source="$1"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$source" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$source" | awk '{print $1}'
    else
        printf 'UNAVAILABLE\n'
    fi
}

WORKER_INVENTORY_DETAIL=''
WORKER_INVENTORY_COUNT=0
inventory_worker_error_names() {
    local source="$1"
    local destination="$2"
    local maximum_entries="$3"
    local names
    local measurement
    local measurement_status
    WORKER_INVENTORY_DETAIL=''
    WORKER_INVENTORY_COUNT=0
    names="$(mktemp "${diagnostic_root}/status/rd1-worker-names.XXXXXX")" \
        || { WORKER_INVENTORY_DETAIL='could not reserve bounded names'; return 1; }
    measurement="$(mktemp "${diagnostic_root}/status/rd1-worker-count.XXXXXX")" \
        || { rm -f "${names}"; WORKER_INVENTORY_DETAIL='could not reserve bounded count'; return 1; }
    if find "${source}" -mindepth 1 -maxdepth 1 \
            -name 'worker-error-*' -print0 | (
        inventory_count=0
        while IFS= read -r -d '' entry; do
            inventory_count=$((inventory_count + 1))
            if (( inventory_count > maximum_entries )); then
                printf 'FAIL\tworker-error inventory exceeded its entry bound\n' \
                    > "${measurement}"
                exit 1
            fi
            if [[ -L "${entry}" || ! -f "${entry}" ]]; then
                printf 'FAIL\tworker-error inventory contains a non-regular entry\n' \
                    > "${measurement}"
                exit 1
            fi
            inventory_name="${entry##*/}"
            if [[ "${inventory_name}" == *$'\n'* \
                    || "${inventory_name}" == *$'\r'* ]]; then
                printf 'FAIL\tworker-error inventory contains a malformed name\n' \
                    > "${measurement}"
                exit 1
            fi
            printf '%s\n' "${inventory_name}" >> "${names}" || exit 1
        done
        printf 'PASS\t%s\n' "${inventory_count}" > "${measurement}"
    ); then
        IFS=$'\t' read -r measurement_status WORKER_INVENTORY_COUNT \
            < "${measurement}"
        if [[ "${measurement_status}" != 'PASS' \
                || ! "${WORKER_INVENTORY_COUNT}" =~ ^[0-9]+$ ]]; then
            WORKER_INVENTORY_DETAIL='bounded worker-error inventory is malformed'
            rm -f "${names}" "${measurement}"
            return 1
        fi
        if ! LC_ALL=C sort "${names}" > "${destination}"; then
            WORKER_INVENTORY_DETAIL='could not sort bounded worker-error inventory'
            rm -f "${names}" "${measurement}"
            return 1
        fi
        rm -f "${names}" "${measurement}"
        return 0
    fi
    IFS=$'\t' read -r measurement_status WORKER_INVENTORY_DETAIL \
        < "${measurement}"
    if [[ "${measurement_status}" != 'FAIL' \
            || -z "${WORKER_INVENTORY_DETAIL}" ]]; then
        WORKER_INVENTORY_DETAIL='could not enumerate worker-error files'
    fi
    rm -f "${names}" "${measurement}"
    return 1
}

for required_name in \
        GRAMARYE_RD1_DIAGNOSTIC_ROOT \
        RUNNER_TEMP \
        HOME \
        GITHUB_SHA \
        GITHUB_RUN_ID \
        GITHUB_RUN_ATTEMPT \
        GITHUB_JOB; do
    [[ -n "${!required_name:-}" ]] \
        || fail "required environment ${required_name} is absent"
done

diagnostic_root="${GRAMARYE_RD1_DIAGNOSTIC_ROOT}"
runner_temp="$(cd "${RUNNER_TEMP}" 2>/dev/null && pwd -P)" \
    || fail 'RUNNER_TEMP is not an existing directory'
[[ "${diagnostic_root}" == /* ]] \
    || fail 'diagnostic root is not absolute'
[[ "${diagnostic_root}" != *$'\n'* && "${diagnostic_root}" != *$'\r'* ]] \
    || fail 'diagnostic root contains a line break'
[[ ! -e "${diagnostic_root}" && ! -L "${diagnostic_root}" ]] \
    || fail 'diagnostic root already exists'
[[ "${GITHUB_SHA}" =~ ^[0-9a-f]{40}$ ]] \
    || fail 'GITHUB_SHA is not an exact commit identity'
[[ "${GITHUB_RUN_ID}" =~ ^[1-9][0-9]*$ ]] \
    || fail 'GITHUB_RUN_ID is malformed'
[[ "${GITHUB_RUN_ATTEMPT}" =~ ^[1-9][0-9]*$ ]] \
    || fail 'GITHUB_RUN_ATTEMPT is malformed'
[[ "${GITHUB_JOB}" == 'build' ]] \
    || fail 'diagnostics are authorized only for the build job'
expected_diagnostic_root="${runner_temp}/gramarye-p9-s3-rd1-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}-${GITHUB_JOB}"
[[ "${diagnostic_root}" == "${expected_diagnostic_root}" ]] \
    || fail 'diagnostic root does not match the exact run/attempt/job namespace'
[[ -x ./gradlew && -f ./gradlew ]] \
    || fail 'project Gradle wrapper is unavailable'
[[ -f gradle/p9-s3-rd1-test-diagnostics.init.gradle \
        && ! -L gradle/p9-s3-rd1-test-diagnostics.init.gradle ]] \
    || fail 'diagnostic init script is unavailable or linked'
[[ -f scripts/collect-p9-s3-rd1-unit-test-diagnostics.sh \
        && ! -L scripts/collect-p9-s3-rd1-unit-test-diagnostics.sh ]] \
    || fail 'diagnostic collector is unavailable or linked'

mkdir -p \
    "${diagnostic_root}/fatal" \
    "${diagnostic_root}/metadata" \
    "${diagnostic_root}/raw" \
    "${diagnostic_root}/status" \
    || fail 'could not create the diagnostic root'
diagnostic_root="$(cd "${diagnostic_root}" && pwd -P)" \
    || fail 'could not canonicalize the diagnostic root'
[[ "${diagnostic_root}" == "${expected_diagnostic_root}" ]] \
    || fail 'canonical diagnostic root escaped its exact namespace'

# The workflow-only routing variable is not part of the original Test worker
# environment. The absolute diagnostic root is forwarded to Gradle only through
# the explicit tracked Gradle project property below.
unset GRAMARYE_RD1_DIAGNOSTIC_ROOT

runner_script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)/$(basename "${BASH_SOURCE[0]}")"
init_script="$(pwd -P)/gradle/p9-s3-rd1-test-diagnostics.init.gradle"
collector_script="$(pwd -P)/scripts/collect-p9-s3-rd1-unit-test-diagnostics.sh"
created_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
actual_commit="$(git rev-parse HEAD 2>/dev/null)" \
    || fail 'could not resolve the checked-out commit'
actual_tree="$(git rev-parse 'HEAD^{tree}' 2>/dev/null)" \
    || fail 'could not resolve the checked-out tree'
[[ "${actual_commit}" == "${GITHUB_SHA}" ]] \
    || fail 'checked-out commit does not match GITHUB_SHA'
runner_sha="$(sha256_file "${runner_script}")"
init_sha="$(sha256_file "${init_script}")"
collector_sha="$(sha256_file "${collector_script}")"
for diagnostic_sha in "${runner_sha}" "${init_sha}" "${collector_sha}"; do
    [[ "${diagnostic_sha}" =~ ^[0-9a-f]{64}$ ]] \
        || fail 'SHA-256 tooling is unavailable or malformed'
done

{
    printf 'SCHEMA=P9-S3-RD1-TEST-WORKER-DIAGNOSTIC-V1\n'
    printf 'CREATED_AT=%s\n' "${created_at}"
    printf 'SOURCE_COMMIT=%s\n' "${GITHUB_SHA}"
    printf 'OBSERVED_COMMIT=%s\n' "${actual_commit}"
    printf 'OBSERVED_TREE=%s\n' "${actual_tree}"
    printf 'RUN_ID=%s\n' "${GITHUB_RUN_ID}"
    printf 'RUN_ATTEMPT=%s\n' "${GITHUB_RUN_ATTEMPT}"
    printf 'JOB=%s\n' "${GITHUB_JOB}"
    printf 'TASK=:test\n'
    printf 'WORKING_DIRECTORY=%s\n' "$(pwd -P)"
    printf 'ORIGINAL_WORKFLOW_COMMAND=./gradlew test\n'
    printf 'PRODUCT_TEST_SELECTION=UNCHANGED_FULL_TEST_TASK\n'
    printf 'HEAP_POLICY=UNCHANGED\n'
    printf 'FORK_POLICY=UNCHANGED\n'
    printf 'PARALLELISM_POLICY=UNCHANGED\n'
    printf 'TIMEOUT_POLICY=UNCHANGED\n'
    printf 'CACHE_POLICY=UNCHANGED\n'
    printf 'DIAGNOSTIC_ROOT_ENV_PROPAGATED_TO_GRADLE=false\n'
    printf 'WORKER_PID=NOT_OBSERVED_AT_PRESTART\n'
    printf 'CONTROLLER_JAVA_IDENTITY_SOURCE=metadata/controller-java-version.log\n'
    printf 'WORKER_JAVA_IDENTITY_SOURCE=raw/gradle-test.log\n'
    printf 'RUNNER_SCRIPT_SHA256=%s\n' "${runner_sha}"
    printf 'INIT_SCRIPT_SHA256=%s\n' "${init_sha}"
    printf 'COLLECTOR_SCRIPT_SHA256=%s\n' "${collector_sha}"
} > "${diagnostic_root}/metadata/execution.properties" \
    || fail 'could not write execution metadata'

gradle_command=(
    ./gradlew
    --init-script gradle/p9-s3-rd1-test-diagnostics.init.gradle
    --console=plain
    --info
    --stacktrace
    "-Pgramarye.rd1.enabled=true"
    "-Pgramarye.rd1.diagnosticRoot=${diagnostic_root}"
    test
)
printf '%s\n' "${gradle_command[@]}" \
    > "${diagnostic_root}/metadata/test-command.argv" \
    || fail 'could not write command identity'

./gradlew --version \
    > "${diagnostic_root}/metadata/gradle-version.log" 2>&1
gradle_version_exit=$?
java -XshowSettings:properties -version \
    > "${diagnostic_root}/metadata/controller-java-version.log" 2>&1
java_version_exit=$?
printf 'GRADLE_VERSION_PROBE_EXIT=%s\nCONTROLLER_JAVA_PROBE_EXIT=%s\n' \
    "${gradle_version_exit}" "${java_version_exit}" \
    > "${diagnostic_root}/metadata/version-probe-exits.properties"

gradle_home="${GRADLE_USER_HOME:-${HOME}/.gradle}"
[[ "${gradle_home}" == /* && "${gradle_home}" != *$'\n'* \
        && "${gradle_home}" != *$'\r'* ]] \
    || fail 'Gradle user home is not an absolute bounded path'
if [[ -d "${gradle_home}" && ! -L "${gradle_home}" ]]; then
    gradle_home="$(cd "${gradle_home}" && pwd -P)" \
        || fail 'could not canonicalize Gradle user home'
else
    fail 'Gradle user home is unavailable or linked after the version probe'
fi
worker_directory="${gradle_home}/workers"
worker_initial_status='ABSENT'
worker_inventory_limit=1024
worker_inventory_count=0
if [[ -d "${worker_directory}" && ! -L "${worker_directory}" ]]; then
    worker_initial_status='AVAILABLE'
    inventory_worker_error_names \
        "${worker_directory}" \
        "${diagnostic_root}/metadata/worker-error-files-before.txt" \
        "${worker_inventory_limit}" \
        || fail "could not snapshot existing Gradle worker-error files: ${WORKER_INVENTORY_DETAIL}"
    worker_inventory_count="${WORKER_INVENTORY_COUNT}"
elif [[ -e "${worker_directory}" || -L "${worker_directory}" ]]; then
    fail 'Gradle worker-error path is not a regular directory boundary'
else
    : > "${diagnostic_root}/metadata/worker-error-files-before.txt" \
        || fail 'could not create the empty worker-error baseline'
fi
printf 'WORKER_ERROR_DIRECTORY=%s\nWORKER_ERROR_DIRECTORY_INITIAL_STATUS=%s\nWORKER_ERROR_BASELINE_COUNT=%s\nWORKER_ERROR_INVENTORY_LIMIT=%s\n' \
    "${worker_directory}" "${worker_initial_status}" \
    "${worker_inventory_count}" "${worker_inventory_limit}" \
    > "${diagnostic_root}/metadata/worker-error-source.properties" \
    || fail 'could not record the Gradle worker-error source'

started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
"${gradle_command[@]}" 2>&1 \
    | tee "${diagnostic_root}/raw/gradle-test.log"
pipeline_status=("${PIPESTATUS[@]}")
gradle_exit="${pipeline_status[0]:-125}"
tee_exit="${pipeline_status[1]:-125}"
finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

if [[ "${gradle_exit}" == '0' ]]; then
    product_test_outcome='PASS'
else
    product_test_outcome='FAIL'
fi
{
    printf 'STARTED_AT=%s\n' "${started_at}"
    printf 'FINISHED_AT=%s\n' "${finished_at}"
    printf 'GRADLE_EXIT_CODE=%s\n' "${gradle_exit}"
    printf 'TEE_EXIT_CODE=%s\n' "${tee_exit}"
    printf 'PRIMARY_EXIT_SOURCE=PIPESTATUS_0\n'
    printf 'PRODUCT_TEST_OUTCOME=%s\n' "${product_test_outcome}"
} > "${diagnostic_root}/status/test-exit.properties.partial"
mv \
    "${diagnostic_root}/status/test-exit.properties.partial" \
    "${diagnostic_root}/status/test-exit.properties"

exit "${gradle_exit}"
