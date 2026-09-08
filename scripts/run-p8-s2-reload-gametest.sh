#!/usr/bin/env bash
set -euo pipefail

project_root="$(git rev-parse --show-toplevel)"
[[ "$(pwd -P)" == "$(cd "${project_root}" && pwd -P)" ]] || {
    echo 'P8-S2 reload GameTest must run from the repository root.' >&2
    exit 2
}

report_root="${GRAMARYE_P8_S2_REPORT_ROOT:-${project_root}/build/reports/p8-s2-r1/reload-gametest}"
[[ "${report_root}" == /* ]] || report_root="${project_root}/${report_root}"
case "/${report_root#/}/" in
    *'/../'*|*'/./'*)
        echo 'P8-S2 report root must be normalized.' >&2
        exit 2
        ;;
esac
case "${report_root}/" in
    "${project_root}/build/"*) ;;
    *)
        echo 'P8-S2 report root must remain below this worktree build directory.' >&2
        exit 2
        ;;
esac

raw_log="${report_root}/raw.log"
[[ ! -e "${report_root}" ]] || {
    echo 'P8-S2 report root must be absent before its single owning run.' >&2
    exit 2
}
mkdir -p -- "${report_root}"

set +e
./gradlew runP8S2ReloadGameTestServer --rerun-tasks --console=plain 2>&1 \
    | tee "${raw_log}"
pipeline_status=("${PIPESTATUS[@]}")
set -e
if (( pipeline_status[1] != 0 )); then
    echo 'P8-S2 raw-log writer failed.' >&2
    exit "${pipeline_status[1]}"
fi
if (( pipeline_status[0] != 0 )); then
    echo "P8-S2 reload GameTest failed with exit ${pipeline_status[0]}." >&2
    exit "${pipeline_status[0]}"
fi

raw_bytes="$(wc -c < "${raw_log}" | tr -d '[:space:]')"
[[ "${raw_bytes}" =~ ^[0-9]+$ ]] && (( raw_bytes <= 33554432 )) || {
    echo "P8-S2 raw log exceeded its 32 MiB bound (${raw_bytes} bytes)." >&2
    exit 1
}

require_literal_once() {
    local literal="$1"
    local count
    count="$(grep -F -c -- "${literal}" "${raw_log}" || true)"
    [[ "${count}" == '1' ]] || {
        echo "P8-S2 expected exactly one raw marker: ${literal} (found ${count})." >&2
        exit 1
    }
}

line_for() {
    local literal="$1"
    awk -v literal="${literal}" 'index($0, literal) { print NR; exit }' "${raw_log}"
}

markers=(
    'Gramarye P8 Profile catalog activated at generation 1'
    'GRAMARYE_P8_S2_RELOAD STARTUP_READY'
    'GRAMARYE_P8_S2_RELOAD TEST_ID=gramarye_p8_s2_reload:p8s2reloadgametests.globalfailureafterp8stagingpreservesactivepublication'
    'GRAMARYE_P8_S2_RELOAD NEGATIVE_BEGIN'
    'GRAMARYE_P8_S2_RELOAD NEGATIVE_PREDECESSORS_APPLIED'
    'GRAMARYE_P8_S2_RELOAD GLOBAL_FAILURE_INJECTED'
    'GRAMARYE_P8_S2_RELOAD NEGATIVE_COMPLETED_EXCEPTIONALLY'
    'GRAMARYE_P8_S2_RELOAD RECOVERY_BEGIN'
    'GRAMARYE_P8_S2_RELOAD RECOVERY_PREDECESSORS_APPLIED'
    'Gramarye P8 Profile catalog activated at generation 2'
    'GRAMARYE_P8_S2_RELOAD RECOVERY_GLOBAL_SYNC'
    'GRAMARYE_P8_S2_RELOAD RECOVERY_COMPLETED_SUCCESSFULLY'
    'GRAMARYE_P8_S2_RELOAD ASSERTIONS_COMPLETE'
    'BUILD SUCCESSFUL'
)

previous_line=0
for marker in "${markers[@]}"; do
    require_literal_once "${marker}"
    marker_line="$(line_for "${marker}")"
    [[ "${marker_line}" =~ ^[0-9]+$ ]] && (( marker_line > previous_line )) || {
        echo "P8-S2 raw marker order changed at: ${marker}" >&2
        exit 1
    }
    previous_line="${marker_line}"
done

for forbidden in \
        'Gramarye P8 Profile catalog activated at generation 3' \
        'Attempted to load class net/minecraft/client' \
        'Invalid dist DEDICATED_SERVER for net.minecraft.client' \
        'BUILD FAILED'; do
    if grep -F -q -- "${forbidden}" "${raw_log}"; then
        echo "P8-S2 raw log contains forbidden evidence: ${forbidden}" >&2
        exit 1
    fi
done

echo "Verified isolated P8-S2 reload GameTest (${raw_bytes} raw bytes)."
