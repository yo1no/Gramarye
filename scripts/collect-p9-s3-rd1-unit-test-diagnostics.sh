#!/usr/bin/env bash

set -uo pipefail
umask 077

fail() {
    printf 'P9-S3-RD1 diagnostic collector: %s\n' "$1" >&2
    exit 2
}

for required_name in \
        GRAMARYE_RD1_DIAGNOSTIC_ROOT \
        RUNNER_TEMP \
        HOME \
        GITHUB_OUTPUT \
        GITHUB_RUN_ID \
        GITHUB_RUN_ATTEMPT \
        GITHUB_JOB; do
    [[ -n "${!required_name:-}" ]] \
        || fail "required environment ${required_name} is absent"
done

requested_diagnostic_root="${GRAMARYE_RD1_DIAGNOSTIC_ROOT}"
runner_temp="$(cd "${RUNNER_TEMP}" 2>/dev/null && pwd -P)" \
    || fail 'RUNNER_TEMP is not an existing directory'
[[ "${GITHUB_RUN_ID}" =~ ^[1-9][0-9]*$ ]] \
    || fail 'GITHUB_RUN_ID is malformed'
[[ "${GITHUB_RUN_ATTEMPT}" =~ ^[1-9][0-9]*$ ]] \
    || fail 'GITHUB_RUN_ATTEMPT is malformed'
[[ "${GITHUB_JOB}" == 'build' ]] \
    || fail 'diagnostics are authorized only for the build job'
expected_diagnostic_root="${runner_temp}/gramarye-p9-s3-rd1-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}-${GITHUB_JOB}"
[[ "${requested_diagnostic_root}" == "${expected_diagnostic_root}" ]] \
    || fail 'diagnostic root does not match the exact run/attempt/job namespace'
[[ -d "${requested_diagnostic_root}" && ! -L "${requested_diagnostic_root}" ]] \
    || fail 'diagnostic root is unavailable or linked'
diagnostic_root="$(cd "${requested_diagnostic_root}" && pwd -P)" \
    || fail 'could not canonicalize the diagnostic root'
[[ "${diagnostic_root}" == "${expected_diagnostic_root}" ]] \
    || fail 'canonical diagnostic root escaped its exact namespace'
[[ -f "${GITHUB_OUTPUT}" && ! -L "${GITHUB_OUTPUT}" ]] \
    || fail 'GitHub step-output file is unavailable or linked'

for runner_directory in fatal metadata raw status; do
    [[ -d "${diagnostic_root}/${runner_directory}" \
            && ! -L "${diagnostic_root}/${runner_directory}" ]] \
        || fail "runner directory ${runner_directory} is unavailable or linked"
done

artifact_root="${diagnostic_root}/artifact"
[[ ! -e "${artifact_root}" && ! -L "${artifact_root}" ]] \
    || fail 'artifact staging root already exists'
mkdir -p \
    "${artifact_root}/collected" \
    "${artifact_root}/fatal" \
    "${artifact_root}/metadata" \
    "${artifact_root}/raw" \
    "${artifact_root}/status" \
    "${artifact_root}/system" \
    "${artifact_root}/worker-errors" \
    || fail 'could not prepare isolated artifact staging'

availability="${artifact_root}/status/availability.tsv.partial"
collector_log="${artifact_root}/raw/collector.log"
: > "${availability}" || fail 'could not create the availability record'
: > "${collector_log}" || fail 'could not create the collector log'
printf 'category\tstatus\tdetail\n' >> "${availability}" \
    || fail 'could not initialize the availability record'
collection_failures=0
maximum_staged_copy_bytes=234881024
staged_copy_bytes=0
maximum_staged_copy_entries=180000
staged_copy_entries=0

record() {
    printf '%s\t%s\t%s\n' "$1" "$2" "$3" >> "${availability}" \
        || fail 'could not append the availability record'
    printf '%s %s %s\n' "$1" "$2" "$3" \
        | tee -a "${collector_log}" \
        || fail 'could not append the collector log'
}

mark_failure() {
    collection_failures=$((collection_failures + 1))
    record "$1" 'COLLECTION_FAILED' "$2"
}

MEASURED_BYTES=0
MEASURED_ENTRIES=0
MEASURE_FAILURE_DETAIL=''
measure_regular_tree() {
    local source="$1"
    local maximum_bytes="$2"
    local maximum_entries="$3"
    local measurement
    local measurement_status
    MEASURED_BYTES=0
    MEASURED_ENTRIES=0
    MEASURE_FAILURE_DETAIL=''
    measurement="$(mktemp "${diagnostic_root}/status/rd1-measurement.XXXXXX")" \
        || { MEASURE_FAILURE_DETAIL='could not create a bounded measurement'; return 1; }
    if find "${source}" -mindepth 1 -print0 | (
        measured_bytes=0
        measured_entries=0
        while IFS= read -r -d '' entry; do
            measured_entries=$((measured_entries + 1))
            if (( measured_entries > maximum_entries )); then
                printf 'FAIL\tsource tree exceeded its entry bound\n' \
                    > "${measurement}"
                exit 1
            fi
            if [[ -L "${entry}" || ! -d "${entry}" && ! -f "${entry}" ]]; then
                printf 'FAIL\tsource tree contains a non-regular entry\n' \
                    > "${measurement}"
                exit 1
            fi
            if [[ -f "${entry}" ]]; then
                entry_size="$(wc -c < "${entry}" 2>/dev/null | tr -d ' ')"
                if [[ ! "${entry_size}" =~ ^[0-9]+$ \
                        || "${#entry_size}" -gt 10 ]]; then
                    printf 'FAIL\tcould not safely measure a source file\n' \
                        > "${measurement}"
                    exit 1
                fi
                if (( entry_size > maximum_bytes - measured_bytes )); then
                    printf 'FAIL\tsource tree exceeded its logical-byte bound\n' \
                        > "${measurement}"
                    exit 1
                fi
                measured_bytes=$((measured_bytes + entry_size))
            fi
        done
        printf 'PASS\t%s\t%s\n' "${measured_bytes}" "${measured_entries}" \
            > "${measurement}"
    ); then
        IFS=$'\t' read -r measurement_status \
            MEASURED_BYTES MEASURED_ENTRIES < "${measurement}"
        if [[ "${measurement_status}" != 'PASS' \
                || ! "${MEASURED_BYTES}" =~ ^[0-9]+$ \
                || ! "${MEASURED_ENTRIES}" =~ ^[0-9]+$ ]]; then
            MEASURE_FAILURE_DETAIL='bounded measurement result is malformed'
            rm -f "${measurement}"
            return 1
        fi
        rm -f "${measurement}"
        return 0
    fi
    IFS=$'\t' read -r measurement_status MEASURE_FAILURE_DETAIL \
        < "${measurement}"
    if [[ "${measurement_status}" != 'FAIL' \
            || -z "${MEASURE_FAILURE_DETAIL}" ]]; then
        MEASURE_FAILURE_DETAIL='could not enumerate the source tree'
    fi
    rm -f "${measurement}"
    return 1
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

copy_regular() {
    local category="$1"
    local source="$2"
    local destination="$3"
    local maximum_bytes="$4"
    local missing_status="$5"
    local source_size
    if [[ -L "${source}" ]]; then
        mark_failure "${category}" 'source is a symbolic link'
        return
    fi
    if [[ ! -f "${source}" ]]; then
        if [[ "${missing_status}" == 'COLLECTION_FAILED' ]]; then
            mark_failure "${category}" "${source} is absent"
        else
            record "${category}" "${missing_status}" "${source}"
        fi
        return
    fi
    source_size="$(wc -c < "${source}" 2>/dev/null | tr -d ' ')"
    if [[ ! "${source_size}" =~ ^[0-9]+$ \
            || "${source_size}" -gt "${maximum_bytes}" ]]; then
        mark_failure "${category}" "source exceeded the ${maximum_bytes}-byte bound"
        return
    fi
    if (( source_size > maximum_staged_copy_bytes - staged_copy_bytes )); then
        mark_failure "${category}" \
            'source exceeded the remaining cumulative staging bound'
        return
    fi
    if (( staged_copy_entries >= maximum_staged_copy_entries )); then
        mark_failure "${category}" \
            'source exceeded the remaining cumulative staging entry bound'
        return
    fi
    mkdir -p "$(dirname "${destination}")" \
        || { mark_failure "${category}" 'destination creation failed'; return; }
    if cp "${source}" "${destination}"; then
        staged_copy_bytes=$((staged_copy_bytes + source_size))
        staged_copy_entries=$((staged_copy_entries + 1))
        record "${category}" 'PRODUCED' "${source_size} bytes"
    else
        mark_failure "${category}" 'bounded copy failed'
    fi
}

copy_tree() {
    local category="$1"
    local source="$2"
    local destination="$3"
    local missing_status="$4"
    if [[ -L "${source}" ]]; then
        mark_failure "${category}" 'source tree is a symbolic link'
        return
    fi
    if [[ ! -d "${source}" ]]; then
        if [[ "${missing_status}" == 'COLLECTION_FAILED' ]]; then
            mark_failure "${category}" \
                "${source} is absent after a successful test task"
        else
            record "${category}" "${missing_status}" "${source}"
        fi
        return
    fi
    if ! measure_regular_tree "${source}" 134217728 100000; then
        mark_failure "${category}" "${MEASURE_FAILURE_DETAIL}"
        return
    fi
    if (( MEASURED_ENTRIES == 0 )); then
        if [[ "${missing_status}" == 'COLLECTION_FAILED' ]]; then
            mark_failure "${category}" 'source tree is empty after a successful test task'
        else
            record "${category}" "${missing_status}" 'source tree is empty'
        fi
        return
    fi
    if (( MEASURED_BYTES > maximum_staged_copy_bytes - staged_copy_bytes )); then
        mark_failure "${category}" \
            'source tree exceeded the remaining cumulative staging bound'
        return
    fi
    if (( MEASURED_ENTRIES > maximum_staged_copy_entries - staged_copy_entries )); then
        mark_failure "${category}" \
            'source tree exceeded the remaining cumulative staging entry bound'
        return
    fi
    mkdir -p "${destination}" \
        || { mark_failure "${category}" 'destination creation failed'; return; }
    if cp -R "${source}/." "${destination}/"; then
        staged_copy_bytes=$((staged_copy_bytes + MEASURED_BYTES))
        staged_copy_entries=$((staged_copy_entries + MEASURED_ENTRIES))
        record "${category}" 'PRODUCED' \
            "${MEASURED_BYTES} bytes,${MEASURED_ENTRIES} entries"
    else
        mark_failure "${category}" 'bounded copy failed'
    fi
}

test_exit_file="${diagnostic_root}/status/test-exit.properties"
primary_exit='UNAVAILABLE'
tee_exit='UNAVAILABLE'
if [[ -f "${test_exit_file}" && ! -L "${test_exit_file}" ]]; then
    primary_exit="$(sed -n 's/^GRADLE_EXIT_CODE=//p' "${test_exit_file}")"
    tee_exit="$(sed -n 's/^TEE_EXIT_CODE=//p' "${test_exit_file}")"
    if [[ "${primary_exit}" =~ ^[0-9]+$ && "${tee_exit}" =~ ^[0-9]+$ ]]; then
        record 'test-exit-record' 'PRODUCED' "gradle=${primary_exit},tee=${tee_exit}"
        if [[ "${tee_exit}" != '0' ]]; then
            mark_failure 'raw-tee' "tee exited ${tee_exit}"
        fi
    else
        mark_failure 'test-exit-record' 'exit fields are missing or malformed'
    fi
else
    mark_failure 'test-exit-record' 'status/test-exit.properties is absent'
fi
copy_regular \
    'test-exit-artifact' \
    "${test_exit_file}" \
    "${artifact_root}/status/test-exit.properties" \
    1048576 \
    'COLLECTION_FAILED'

for metadata_file in \
        execution.properties \
        test-command.argv \
        gradle-version.log \
        controller-java-version.log \
        version-probe-exits.properties \
        worker-error-files-before.txt \
        worker-error-source.properties; do
    copy_regular \
        "metadata-${metadata_file}" \
        "${diagnostic_root}/metadata/${metadata_file}" \
        "${artifact_root}/metadata/${metadata_file}" \
        1048576 \
        'COLLECTION_FAILED'
done

version_probe_file="${diagnostic_root}/metadata/version-probe-exits.properties"
if [[ -f "${version_probe_file}" && ! -L "${version_probe_file}" ]]; then
    gradle_probe_exit="$(sed -n 's/^GRADLE_VERSION_PROBE_EXIT=//p' \
        "${version_probe_file}")"
    java_probe_exit="$(sed -n 's/^CONTROLLER_JAVA_PROBE_EXIT=//p' \
        "${version_probe_file}")"
    if [[ ! "${gradle_probe_exit}" =~ ^[0-9]+$ \
            || ! "${java_probe_exit}" =~ ^[0-9]+$ ]]; then
        mark_failure 'version-probe-exits' 'probe exit fields are missing or malformed'
    elif [[ "${gradle_probe_exit}" != '0' || "${java_probe_exit}" != '0' ]]; then
        mark_failure 'version-probe-exits' \
            "gradle=${gradle_probe_exit},controller-java=${java_probe_exit}"
    else
        record 'version-probe-exits' 'PRODUCED' 'gradle=0,controller-java=0'
    fi
fi

raw_log="${diagnostic_root}/raw/gradle-test.log"
before_raw_copy_failures="${collection_failures}"
copy_regular \
    'gradle-raw-log' \
    "${raw_log}" \
    "${artifact_root}/raw/gradle-test.log" \
    134217728 \
    'COLLECTION_FAILED'
raw_log_accepted='false'
if [[ "${collection_failures}" == "${before_raw_copy_failures}" \
        && -s "${raw_log}" ]]; then
    raw_log_accepted='true'
elif [[ "${collection_failures}" == "${before_raw_copy_failures}" ]]; then
    mark_failure 'gradle-raw-log' 'complete Gradle raw log is empty'
fi

worker_termination_observed='false'
if [[ "${raw_log_accepted}" == 'true' ]]; then
    if grep -Fq 'Test process encountered an unexpected problem' "${raw_log}" \
            || grep -Fq 'Could not complete execution for Gradle Test Executor' \
                "${raw_log}"; then
        worker_termination_observed='true'
        record 'worker-termination-marker' 'PRODUCED' \
            'Gradle reported unexpected Test worker termination'
    else
        record 'worker-termination-marker' 'NOT_PRODUCED' \
            'no exact Gradle Test worker termination marker'
    fi
fi

if [[ "${primary_exit}" == '0' && "${raw_log_accepted}" == 'true' ]]; then
    if grep -Eq \
            '^[[:space:]]*>[[:space:]]+Task :test[[:space:]]+(FROM-CACHE|UP-TO-DATE)[[:space:]]*$' \
            "${raw_log}" \
            || grep -Fq "Skipping task ':test' as it is up-to-date" "${raw_log}"; then
        mark_failure 'test-execution' \
            'successful :test was satisfied without a fresh worker execution'
    elif grep -Eq ' STARTED[[:space:]]*$' "${raw_log}" \
            && grep -Eq ' PASSED[[:space:]]*$' "${raw_log}"; then
        record 'test-event-stream' 'PRODUCED' \
            'native STARTED and completion events observed'
    else
        mark_failure 'test-event-stream' \
            'successful :test lacks native STARTED/completion evidence'
    fi
fi

if [[ "${primary_exit}" == '0' ]]; then
    missing_results_status='COLLECTION_FAILED'
elif [[ "${worker_termination_observed}" == 'true' ]]; then
    missing_results_status='INCOMPLETE_DUE_TO_TERMINATION'
else
    missing_results_status='NOT_PRODUCED'
fi
copy_tree \
    'junit-test-results' \
    'build/test-results/test' \
    "${artifact_root}/collected/test-results" \
    "${missing_results_status}"
copy_tree \
    'junit-html-report' \
    'build/reports/tests/test' \
    "${artifact_root}/collected/html-report" \
    "${missing_results_status}"

if [[ "${primary_exit}" == '0' ]]; then
    junit_xml_sample=''
    if ! junit_xml_sample="$(find \
            "${artifact_root}/collected/test-results" \
            -type f -name 'TEST-*.xml' -print -quit 2>/dev/null)"; then
        mark_failure 'junit-xml-presence' \
            'could not inspect collected JUnit XML results'
    elif [[ -z "${junit_xml_sample}" ]]; then
        mark_failure 'junit-xml-presence' \
            'successful :test produced no TEST-*.xml result'
    else
        record 'junit-xml-presence' 'PRODUCED' \
            'at least one TEST-*.xml result was collected'
    fi
    if [[ -L "${artifact_root}/collected/html-report/index.html" \
            || ! -f "${artifact_root}/collected/html-report/index.html" ]]; then
        mark_failure 'junit-html-entrypoint' \
            'successful :test produced no regular HTML index'
    else
        record 'junit-html-entrypoint' 'PRODUCED' \
            'collected/html-report/index.html'
    fi
fi

fatal_source="${diagnostic_root}/fatal"
fatal_list="${artifact_root}/metadata/fatal-files.txt"
fatal_admitted='true'
if ! measure_regular_tree "${fatal_source}" 67108864 16; then
    mark_failure 'jvm-fatal-log' "${MEASURE_FAILURE_DETAIL}"
    fatal_admitted='false'
fi
fatal_invalid=''
if [[ "${fatal_admitted}" == 'true' ]] \
        && ! fatal_invalid="$(find "${fatal_source}" -mindepth 1 -maxdepth 1 \
        ! -type f -print -quit 2>/dev/null)"; then
    mark_failure 'jvm-fatal-log' 'could not inspect fatal directory entry types'
elif [[ -n "${fatal_invalid}" ]]; then
    mark_failure 'jvm-fatal-log' 'fatal directory contains a non-regular entry'
fi
fatal_unexpected=''
if [[ "${fatal_admitted}" == 'true' ]] \
        && ! fatal_unexpected="$(find "${fatal_source}" -mindepth 1 -maxdepth 1 \
        -type f ! -name 'hs_err_pid*.log' -print -quit 2>/dev/null)"; then
    mark_failure 'jvm-fatal-log' 'could not inspect fatal-log names'
elif [[ -n "${fatal_unexpected}" ]]; then
    mark_failure 'jvm-fatal-log' 'fatal directory contains an unexpected file'
fi
if [[ "${fatal_admitted}" != 'true' ]]; then
    : > "${fatal_list}" || fail 'could not create fatal-log inventory'
    fatal_count='UNAVAILABLE'
elif ! find "${fatal_source}" -maxdepth 1 -type f -name 'hs_err_pid*.log' \
        -exec basename {} \; | LC_ALL=C sort > "${fatal_list}"; then
    mark_failure 'jvm-fatal-log' 'could not inventory fatal logs'
    fatal_count='UNAVAILABLE'
else
    fatal_count="$(wc -l < "${fatal_list}" 2>/dev/null | tr -d ' ')"
fi
if [[ "${fatal_count}" =~ ^[0-9]+$ && "${fatal_count}" -gt 4 ]]; then
    mark_failure 'jvm-fatal-log' 'fatal-log count exceeded the exact collection bound'
elif [[ "${fatal_count}" =~ ^[0-9]+$ && "${fatal_count}" -gt 0 ]]; then
    copied_fatal_logs=0
    while IFS= read -r fatal_name; do
        [[ "${fatal_name}" == hs_err_pid*.log && "${fatal_name}" != */* ]] \
            || { mark_failure 'jvm-fatal-log' 'malformed fatal-log name'; continue; }
        before_fatal_failures="${collection_failures}"
        copy_regular \
            "jvm-fatal-log-${fatal_name}" \
            "${fatal_source}/${fatal_name}" \
            "${artifact_root}/fatal/${fatal_name}" \
            16777216 \
            'COLLECTION_FAILED'
        if [[ "${collection_failures}" == "${before_fatal_failures}" ]]; then
            copied_fatal_logs=$((copied_fatal_logs + 1))
        fi
    done < "${fatal_list}"
    if (( copied_fatal_logs > 0 )); then
        record 'jvm-fatal-log' 'PRODUCED' \
            "discovered=${fatal_count},copied=${copied_fatal_logs}"
    fi
elif [[ "${fatal_count}" == '0' ]]; then
    record 'jvm-fatal-log' 'ABSENT_NOT_REQUIRED' 'no hs_err file was produced'
else
    mark_failure 'jvm-fatal-log' 'fatal-log inventory count is malformed'
fi

worker_source_file="${diagnostic_root}/metadata/worker-error-source.properties"
worker_source=''
worker_initial_status=''
worker_baseline_count=''
worker_inventory_limit=''
if [[ -f "${worker_source_file}" && ! -L "${worker_source_file}" ]]; then
    worker_source="$(sed -n 's/^WORKER_ERROR_DIRECTORY=//p' \
        "${worker_source_file}" 2>/dev/null)"
    worker_initial_status="$(sed -n \
        's/^WORKER_ERROR_DIRECTORY_INITIAL_STATUS=//p' \
        "${worker_source_file}" 2>/dev/null)"
    worker_baseline_count="$(sed -n 's/^WORKER_ERROR_BASELINE_COUNT=//p' \
        "${worker_source_file}" 2>/dev/null)"
    worker_inventory_limit="$(sed -n 's/^WORKER_ERROR_INVENTORY_LIMIT=//p' \
        "${worker_source_file}" 2>/dev/null)"
fi
gradle_home="${GRADLE_USER_HOME:-${HOME}/.gradle}"
gradle_home_valid='false'
if [[ -d "${gradle_home}" && ! -L "${gradle_home}" ]]; then
    if canonical_gradle_home="$(cd "${gradle_home}" && pwd -P)"; then
        gradle_home="${canonical_gradle_home}"
        gradle_home_valid='true'
    else
        mark_failure 'gradle-worker-error' 'could not canonicalize Gradle user home'
    fi
else
    mark_failure 'gradle-worker-error' 'Gradle user home is unavailable or linked'
fi
expected_worker_source="${gradle_home}/workers"
after_worker_files="${artifact_root}/metadata/worker-error-files-after.txt"
new_worker_files="${artifact_root}/metadata/worker-error-files-new.txt"
worker_boundary_valid='true'
baseline_worker_file="${diagnostic_root}/metadata/worker-error-files-before.txt"
baseline_actual_count='UNAVAILABLE'
if [[ -f "${baseline_worker_file}" && ! -L "${baseline_worker_file}" ]]; then
    baseline_actual_count="$(wc -l < "${baseline_worker_file}" 2>/dev/null \
        | tr -d ' ')"
else
    mark_failure 'gradle-worker-error' \
        'recorded worker-error baseline is unavailable or linked'
    worker_boundary_valid='false'
fi
if [[ "${worker_inventory_limit}" != '1024' \
        || ! "${worker_baseline_count}" =~ ^[0-9]+$ \
        || ! "${baseline_actual_count}" =~ ^[0-9]+$ \
        || "${worker_baseline_count}" != "${baseline_actual_count}" \
        || "${worker_baseline_count}" -gt "${worker_inventory_limit}" ]]; then
    mark_failure 'gradle-worker-error' \
        'recorded worker-error baseline count or bound is malformed'
    worker_boundary_valid='false'
elif ! LC_ALL=C sort -c "${baseline_worker_file}" >/dev/null 2>&1; then
    mark_failure 'gradle-worker-error' \
        'recorded worker-error baseline is not sorted'
    worker_boundary_valid='false'
fi
if [[ "${gradle_home_valid}" != 'true' \
        || "${worker_source}" != "${expected_worker_source}" \
        || ! "${worker_initial_status}" =~ ^(AVAILABLE|ABSENT)$ \
        || "${worker_boundary_valid}" != 'true' ]]; then
    mark_failure 'gradle-worker-error' 'recorded worker-error boundary is malformed'
    : > "${after_worker_files}" || fail 'could not create worker-error inventory'
    : > "${new_worker_files}" || fail 'could not create worker-error delta'
elif [[ -d "${worker_source}" && ! -L "${worker_source}" ]]; then
    worker_inventory_admitted='true'
    if ! inventory_worker_error_names \
            "${worker_source}" "${after_worker_files}" \
            "${worker_inventory_limit}"; then
        mark_failure 'gradle-worker-error' \
            "${WORKER_INVENTORY_DETAIL}"
        worker_inventory_admitted='false'
        : > "${after_worker_files}" \
            || fail 'could not create worker-error inventory'
    fi
    if [[ "${worker_inventory_admitted}" == 'true' ]] && ! comm -13 \
            "${diagnostic_root}/metadata/worker-error-files-before.txt" \
            "${after_worker_files}" > "${new_worker_files}"; then
        mark_failure 'gradle-worker-error' 'could not derive new worker-error files'
        worker_inventory_admitted='false'
    elif [[ "${worker_inventory_admitted}" != 'true' ]]; then
        : > "${new_worker_files}" \
            || fail 'could not create worker-error delta'
    fi
    copied_worker_errors=0
    new_worker_count='UNAVAILABLE'
    if [[ "${worker_inventory_admitted}" == 'true' ]]; then
        new_worker_count="$(wc -l < "${new_worker_files}" 2>/dev/null | tr -d ' ')"
        if [[ ! "${new_worker_count}" =~ ^[0-9]+$ \
                || "${new_worker_count}" -gt 16 ]]; then
            mark_failure 'gradle-worker-error' \
                'new worker-error count exceeded the collection bound'
            worker_inventory_admitted='false'
        fi
    fi
    if [[ "${worker_inventory_admitted}" == 'true' ]]; then
        while IFS= read -r worker_name; do
            [[ "${worker_name}" == worker-error-* && "${worker_name}" != */* ]] \
                || { mark_failure 'gradle-worker-error' 'malformed worker-error name'; continue; }
            before_worker_failures="${collection_failures}"
            copy_regular \
                "gradle-worker-error-${worker_name}" \
                "${worker_source}/${worker_name}" \
                "${artifact_root}/worker-errors/${worker_name}" \
                16777216 \
                'COLLECTION_FAILED'
            if [[ "${collection_failures}" == "${before_worker_failures}" ]]; then
                copied_worker_errors=$((copied_worker_errors + 1))
            fi
        done < "${new_worker_files}"
    fi
    if (( copied_worker_errors > 0 )); then
        record 'gradle-worker-error' 'PRODUCED' "count=${copied_worker_errors}"
    elif [[ "${worker_inventory_admitted}" == 'true' \
            && "${new_worker_count}" == '0' ]]; then
        record 'gradle-worker-error' 'NOT_PRODUCED' 'no new worker-error file'
    fi
elif [[ -e "${worker_source}" || -L "${worker_source}" \
        || "${worker_initial_status}" == 'AVAILABLE' ]]; then
    mark_failure 'gradle-worker-error' \
        'worker-error directory disappeared or became an invalid boundary'
    : > "${after_worker_files}" || fail 'could not create worker-error inventory'
    : > "${new_worker_files}" || fail 'could not create worker-error delta'
else
    : > "${after_worker_files}" || fail 'could not create worker-error inventory'
    : > "${new_worker_files}" || fail 'could not create worker-error delta'
    record 'gradle-worker-error' 'NOT_PRODUCED' \
        'worker-error directory was absent before and after the test task'
fi

if uname -a > "${artifact_root}/system/uname.txt"; then
    record 'runner-uname' 'PRODUCED' 'system/uname.txt'
else
    mark_failure 'runner-uname' 'uname collection failed'
fi
for cgroup_file in memory.max memory.current memory.events memory.swap.max; do
    copy_regular \
        "cgroup-${cgroup_file}" \
        "/sys/fs/cgroup/${cgroup_file}" \
        "${artifact_root}/system/cgroup-${cgroup_file}" \
        1048576 \
        'RUNNER_UNAVAILABLE'
done

artifact_symlink_list="${artifact_root}/metadata/rejected-artifact-symlinks.txt"
if ! find "${artifact_root}" -type l -print \
        > "${artifact_symlink_list}"; then
    mark_failure 'artifact-boundary' 'could not inspect artifact staging for links'
elif [[ -s "${artifact_symlink_list}" ]]; then
    mark_failure 'artifact-boundary' \
        'artifact staging contained links; linked entries were excluded'
    if ! find "${artifact_root}" -type l -delete; then
        printf 'P9-S3-RD1 diagnostic collector: unsafe artifact links remain\n' >&2
        exit 3
    fi
else
    record 'artifact-boundary' 'PRODUCED' 'staging contains regular entries only'
fi
if find "${artifact_root}" -type l -print -quit | grep -q .; then
    printf 'P9-S3-RD1 diagnostic collector: unsafe artifact links remain\n' >&2
    exit 3
fi
if ! measure_regular_tree "${artifact_root}" 268435456 200000; then
    mark_failure 'artifact-boundary' "${MEASURE_FAILURE_DETAIL}"
else
    record 'artifact-size-bound' 'PRODUCED' \
        "${MEASURED_BYTES} bytes,${MEASURED_ENTRIES} entries"
fi

if (( collection_failures == 0 )); then
    collection_status='PASS'
else
    collection_status='FAIL'
fi
if ! {
    printf 'COLLECTED_AT=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'PRIMARY_TEST_EXIT=%s\n' "${primary_exit}"
    printf 'WORKER_TERMINATION_OBSERVED=%s\n' "${worker_termination_observed}"
    printf 'EVIDENCE_COLLECTION_STATUS=%s\n' "${collection_status}"
    printf 'EVIDENCE_COLLECTION_FAILURES=%s\n' "${collection_failures}"
    printf 'STAGED_COPY_BYTES=%s\n' "${staged_copy_bytes}"
    printf 'MAXIMUM_STAGED_COPY_BYTES=%s\n' \
        "${maximum_staged_copy_bytes}"
    printf 'STAGED_COPY_ENTRIES=%s\n' "${staged_copy_entries}"
    printf 'MAXIMUM_STAGED_COPY_ENTRIES=%s\n' \
        "${maximum_staged_copy_entries}"
} > "${artifact_root}/status/collection-exit.properties"; then
    printf 'P9-S3-RD1 diagnostic collector: could not write final status\n' >&2
    exit 3
fi
if ! mv "${availability}" "${artifact_root}/status/availability.tsv"; then
    printf 'P9-S3-RD1 diagnostic collector: could not finalize availability\n' >&2
    exit 3
fi
if ! measure_regular_tree "${artifact_root}" 268435456 200000; then
    printf 'P9-S3-RD1 diagnostic collector: finalized staging exceeded its bound\n' >&2
    exit 3
fi

artifact_archive="$(mktemp "${diagnostic_root}/rd1-upload.XXXXXX")" \
    || fail 'could not reserve an isolated artifact archive'
[[ -f "${artifact_archive}" && ! -L "${artifact_archive}" ]] \
    || fail 'reserved artifact archive is not a regular file'
if ! tar -cf "${artifact_archive}" -C "${artifact_root}" .; then
    printf 'P9-S3-RD1 diagnostic collector: artifact archive creation failed\n' >&2
    exit 3
fi
archive_size="$(wc -c < "${artifact_archive}" 2>/dev/null | tr -d ' ')"
if [[ ! "${archive_size}" =~ ^[0-9]+$ \
        || "${archive_size}" -gt 536870912 ]]; then
    printf 'P9-S3-RD1 diagnostic collector: artifact archive exceeded its bound\n' >&2
    exit 3
fi
if ! printf 'archive_path=%s\n' "${artifact_archive}" \
        >> "${GITHUB_OUTPUT}"; then
    printf 'P9-S3-RD1 diagnostic collector: could not publish archive path\n' >&2
    exit 3
fi
if ! printf 'safe_to_upload=true\n' >> "${GITHUB_OUTPUT}"; then
    printf 'P9-S3-RD1 diagnostic collector: could not publish safe upload output\n' >&2
    exit 3
fi

printf 'P9-S3-RD1 collection %s; primary test exit=%s; failures=%s\n' \
    "${collection_status}" "${primary_exit}" "${collection_failures}"
if (( collection_failures == 0 )); then
    exit 0
fi
exit 3
