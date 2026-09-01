#!/usr/bin/env bash
set -euo pipefail

# This Gate is intentionally limited to tools present on the portable CI PATH.
PATH='/usr/bin:/bin'
export PATH

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

for required_tool in awk basename bash cat cmp dirname find git grep jar mktemp pwd rm sed sort tr wc; do
    command -v "${required_tool}" >/dev/null 2>&1 \
        || fail "P4-E3 configuration verifier cannot find required tool: ${required_tool}"
done

REPOSITORY_ROOT=''
if REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; then
    :
else
    fail 'P4-E3 configuration verifier could not resolve the repository root'
fi
cd "${REPOSITORY_ROOT}"

PRODUCTION_SOURCE_LIST=''
TEST_SOURCE_LIST=''
PROBE_SOURCE_LIST=''
GAME_SOURCE_LIST=''
RESEARCH_SOURCE_LIST=''
RESEARCH_GAME_SOURCE_LIST=''
VIEW_BLOCK=''
TASK_BLOCK=''
RUNTIME_MOD_BLOCK=''
PRODUCTION_MOD_BLOCK=''
CI_JOB_BLOCK=''
CI_NEEDS_BLOCK=''
OBSERVATION_BLOCK=''
MASKED_SOURCE=''
MASK_SELF_SOURCE=''
MASK_SELF_RESULT=''
LINE_ENDINGS_SOURCE=''
LINE_ENDINGS_RESULT=''
JAR_FILE_LIST=''
JAR_LISTING=''
JAR_EXTRACT_DIRECTORY=''

cleanup() {
    local temporary=''
    for temporary in \
        "${PRODUCTION_SOURCE_LIST}" \
        "${TEST_SOURCE_LIST}" \
        "${PROBE_SOURCE_LIST}" \
        "${GAME_SOURCE_LIST}" \
        "${RESEARCH_SOURCE_LIST}" \
        "${RESEARCH_GAME_SOURCE_LIST}" \
        "${VIEW_BLOCK}" \
        "${TASK_BLOCK}" \
        "${RUNTIME_MOD_BLOCK}" \
        "${PRODUCTION_MOD_BLOCK}" \
        "${CI_JOB_BLOCK}" \
        "${CI_NEEDS_BLOCK}" \
        "${OBSERVATION_BLOCK}" \
        "${MASKED_SOURCE}" \
        "${MASK_SELF_SOURCE}" \
        "${MASK_SELF_RESULT}" \
        "${LINE_ENDINGS_SOURCE}" \
        "${LINE_ENDINGS_RESULT}" \
        "${JAR_FILE_LIST}" \
        "${JAR_LISTING}"; do
        if [[ -n "${temporary}" ]]; then
            rm -f -- "${temporary}"
        fi
    done
    if [[ -n "${JAR_EXTRACT_DIRECTORY}" ]]; then
        rm -rf -- "${JAR_EXTRACT_DIRECTORY}"
    fi
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

fixed_count() {
    local file="$1"
    local needle="$2"
    local actual=''
    local status=0
    actual="$(LC_ALL=C grep -Fc -- "${needle}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) actual=0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    printf '%s\n' "${actual}"
}

ere_count() {
    local file="$1"
    local pattern="$2"
    local actual=''
    local status=0
    actual="$(LC_ALL=C grep -Ec -- "${pattern}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) actual=0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    printf '%s\n' "${actual}"
}

require_fixed_count() {
    local file="$1"
    local needle="$2"
    local expected="$3"
    local message="$4"
    local actual=''
    actual="$(fixed_count "${file}" "${needle}")"
    [[ "${actual}" -eq "${expected}" ]] \
        || fail "${message} (expected ${expected}, found ${actual})"
}

require_ere_count() {
    local file="$1"
    local pattern="$2"
    local expected="$3"
    local message="$4"
    local actual=''
    actual="$(ere_count "${file}" "${pattern}")"
    [[ "${actual}" -eq "${expected}" ]] \
        || fail "${message} (expected ${expected}, found ${actual})"
}

require_regular_file() {
    local file="$1"
    local message="$2"
    [[ -f "${file}" && ! -L "${file}" ]] || fail "${message}"
}

collect_java_files() {
    local root="$1"
    local destination="$2"
    local status=0
    LC_ALL=C find "${root}" -type f -name '*.java' -print0 > "${destination}" \
        || status=$?
    if [[ "${status}" -ne 0 || ! -s "${destination}" ]]; then
        fail "P4-E3 configuration verifier could not inspect Java sources under ${root}"
    fi
}

require_any_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local message="$3"
    local source=''
    local status=0
    while IFS= read -r -d '' source; do
        status=0
        LC_ALL=C grep -Fq -- "${needle}" "${source}" || status=$?
        case "${status}" in
            0) return 0 ;;
            1) ;;
            *) grep_failed "${source}" "${status}" ;;
        esac
    done < "${file_list}"
    fail "${message}"
}

forbid_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local message="$3"
    local source=''
    while IFS= read -r -d '' source; do
        forbid_fixed "${source}" "${needle}" "${message} (${source})"
    done < "${file_list}"
}

count_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local total=0
    local source=''
    while IFS= read -r -d '' source; do
        total=$((total + $(fixed_count "${source}" "${needle}")))
    done < "${file_list}"
    printf '%s\n' "${total}"
}

# Single-pass Java lexical masker. It preserves byte length and every CR/LF, and has the exact
# CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, CHARACTER, and TEXT_BLOCK states.
mask_java_source() {
    local source="$1"
    local destination="$2"
    {
        LC_ALL=C cat "${source}" || exit 1
        printf '\034'
    } | LC_ALL=C awk '
        BEGIN { state = "CODE"; sentinel = sprintf("%c", 28); terminal_records = 0; ORS = "" }
        function masked(c) { return c == "\r" ? "\r" : " " }
        function triple(s, i) { return substr(s, i, 3) == "\"\"\"" }
        function opening(s, i, j, c, n) {
            if (!triple(s, i)) return 0
            n = length(s)
            for (j = i + 3; j <= n; j++) {
                c = substr(s, j, 1)
                if (c == " " || c == "\t" || c == sprintf("%c", 12)) continue
                if (c == "\r") return 1
                return 0
            }
            return record_has_lf
        }
        {
            s = $0
            terminal_record = substr(s, length(s), 1) == sentinel
            if (terminal_record) {
                terminal_records++
                s = substr(s, 1, length(s) - 1)
            }
            if (index(s, sentinel) != 0) exit 41
            record_has_lf = terminal_record ? 0 : 1
            out = ""
            n = length(s)
            for (i = 1; i <= n; i++) {
                c = substr(s, i, 1)
                nextc = i < n ? substr(s, i + 1, 1) : ""
                if (state == "CODE") {
                    if (c == "/" && nextc == "/") {
                        out = out "  "; i++; state = "LINE_COMMENT"
                    } else if (c == "/" && nextc == "*") {
                        out = out "  "; i++; state = "BLOCK_COMMENT"
                    } else if (opening(s, i)) {
                        out = out "   "; i += 2; state = "TEXT_BLOCK"
                    } else if (c == "\"") {
                        out = out " "; state = "STRING"
                    } else if (c == "\047") {
                        out = out " "; state = "CHARACTER"
                    } else {
                        out = out c
                    }
                } else if (state == "LINE_COMMENT") {
                    out = out masked(c)
                    if (c == "\r") state = "CODE"
                } else if (state == "BLOCK_COMMENT") {
                    if (c == "*" && nextc == "/") {
                        out = out "  "; i++; state = "CODE"
                    } else {
                        out = out masked(c)
                    }
                } else if (state == "STRING" || state == "CHARACTER") {
                    out = out masked(c)
                    if (c == "\\" && i < n) {
                        out = out masked(nextc); i++
                    } else if ((state == "STRING" && c == "\"") || (state == "CHARACTER" && c == "\047")) {
                        state = "CODE"
                    }
                } else if (state == "TEXT_BLOCK") {
                    if (triple(s, i)) {
                        out = out "   "; i += 2; state = "CODE"
                    } else if (c == "\\" && i < n) {
                        out = out masked(c) masked(nextc); i++
                    } else {
                        out = out masked(c)
                    }
                }
            }
            if (state == "LINE_COMMENT") state = "CODE"
            printf "%s", out
            if (record_has_lf) printf "\n"
        }
        END { if (terminal_records != 1) exit 42 }
    ' > "${destination}" \
        || fail "P4-E3 lexical masking failed for ${source}"
}

# Literal finite-state scan for dot + default-ASCII-regex-whitespace + exact method name +
# default-ASCII-regex-whitespace + open parenthesis. It never builds a whole-input regex.
literal_invocation_count() {
    local source="$1"
    local method_name="$2"
    LC_ALL=C awk -v method_name="${method_name}" '
        function whitespace(c) {
            return c == " " || c == "\t" || c == "\n" || c == "\r" || c == sprintf("%c", 11) || c == sprintf("%c", 12)
        }
        function restart(c) {
            if (c == ".") { phase = 1; position = 1 }
            else { phase = 0; position = 1 }
        }
        function consume(c) {
            if (phase == 0) {
                if (c == ".") { phase = 1; position = 1 }
            } else if (phase == 1) {
                if (whitespace(c)) return
                if (c == substr(method_name, 1, 1)) {
                    position = 2
                    phase = length(method_name) == 1 ? 3 : 2
                } else restart(c)
            } else if (phase == 2) {
                if (c == substr(method_name, position, 1)) {
                    position++
                    if (position > length(method_name)) phase = 3
                } else restart(c)
            } else {
                if (whitespace(c)) return
                if (c == "(") count++
                restart(c)
            }
        }
        BEGIN { phase = 0; position = 1; count = 0 }
        {
            for (i = 1; i <= length($0); i++) consume(substr($0, i, 1))
            consume("\n")
        }
        END { print count }
    ' "${source}" || fail "P4-E3 literal invocation scan failed for ${source}"
}

verify_lexical_helpers() {
    local source_bytes=''
    local result_bytes=''
    MASK_SELF_SOURCE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-lexical-source.XXXXXX")" \
        || fail 'P4-E3 verifier could not create lexical helper source'
    MASK_SELF_RESULT="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-lexical-result.XXXXXX")" \
        || fail 'P4-E3 verifier could not create lexical helper result'
    LINE_ENDINGS_SOURCE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-lines-source.XXXXXX")" \
        || fail 'P4-E3 verifier could not create source line-ending fixture'
    LINE_ENDINGS_RESULT="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-lines-result.XXXXXX")" \
        || fail 'P4-E3 verifier could not create result line-ending fixture'
    printf '%b' \
        'owner . audit (\r\n// .consumeComplete(\rowner . reclaim (\n/* .reclaim( */\n' \
        'String x = ".reclaim(";\nchar q = '\''\\'\'''\'';\n' \
        'String text = """\r.consumeComplete(\n\\"""\r\n.reclaim(\n""";\r\n' \
        'owner . observeP4E3IndexTerminal (' > "${MASK_SELF_SOURCE}"
    mask_java_source "${MASK_SELF_SOURCE}" "${MASK_SELF_RESULT}"
    source_bytes="$(LC_ALL=C wc -c < "${MASK_SELF_SOURCE}")"
    result_bytes="$(LC_ALL=C wc -c < "${MASK_SELF_RESULT}")"
    [[ "${source_bytes}" -eq "${result_bytes}" ]] \
        || fail 'P4-E3 lexical masker changed helper byte length'
    LC_ALL=C tr -cd '\r\n' < "${MASK_SELF_SOURCE}" > "${LINE_ENDINGS_SOURCE}"
    LC_ALL=C tr -cd '\r\n' < "${MASK_SELF_RESULT}" > "${LINE_ENDINGS_RESULT}"
    cmp -s "${LINE_ENDINGS_SOURCE}" "${LINE_ENDINGS_RESULT}" \
        || fail 'P4-E3 lexical masker changed helper CR/LF structure'
    [[ "$(literal_invocation_count "${MASK_SELF_RESULT}" audit)" -eq 1 ]] \
        || fail 'P4-E3 literal scan lost the code audit invocation'
    [[ "$(literal_invocation_count "${MASK_SELF_RESULT}" consumeComplete)" -eq 0 ]] \
        || fail 'P4-E3 literal scan exposed a masked consume invocation'
    [[ "$(literal_invocation_count "${MASK_SELF_RESULT}" reclaim)" -eq 1 ]] \
        || fail 'P4-E3 literal scan lost bare-CR code or exposed a masked reclaim invocation'
    [[ "$(literal_invocation_count "${MASK_SELF_RESULT}" observeP4E3IndexTerminal)" -eq 1 ]] \
        || fail 'P4-E3 literal scan lost the code terminal invocation'
}

is_exact_p4e3_path() {
    case "$1" in
        .github/workflows/build.yml | \
        build.gradle | \
        scripts/verify-p4-b2-b-configuration.sh | \
        scripts/verify-p4-c2-b-configuration.sh | \
        scripts/verify-p4-d3-configuration.sh | \
        scripts/verify-p4-e0-r-configuration.sh | \
        scripts/verify-p4-e0-r2q-configuration.sh | \
        scripts/verify-p4-e1-configuration.sh | \
        scripts/verify-p4-e2-configuration.sh | \
        scripts/verify-p4-e3-configuration.sh | \
        src/main/java/com/yo1no/gramarye/Gramarye.java | \
        src/main/java/com/yo1no/gramarye/P5LoadedReferenceResolver.java | \
        src/main/java/com/yo1no/gramarye/P5RuntimeConfiguration.java | \
        src/main/java/com/yo1no/gramarye/P5RuntimeProjector.java | \
        src/main/java/com/yo1no/gramarye/P5RuntimeVocabulary.java | \
        src/main/java/com/yo1no/gramarye/P5ServerRuntimeConfig.java | \
        src/main/java/com/yo1no/gramarye/SkillRuntimeService.java | \
        src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java | \
        src/main/java/com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java | \
        src/p4E3GameTest/java/com/yo1no/gramarye/P4E3StartupObservationTestAccess.java | \
        src/p4E3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupMemoryGameTests.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/player/P4E3PlayerDataFixture.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FileVerifier.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureBuilder.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureManifest.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3ProbeMain.java | \
        src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeTest.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeConfigurationTest.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeHardLimitWorkloadTest.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeKernelTest.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeStaticGateTest.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeVocabularyTest.java | \
        src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeVisibilityCompileTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BCompleteHandoffTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3LeaseTerminalTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupLifecycleTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3ApiGateTest.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

is_exact_probe_path() {
    case "$1" in
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/player/P4E3PlayerDataFixture.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FileVerifier.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureBuilder.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureManifest.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3ProbeMain.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

is_exact_game_path() {
    case "$1" in
        src/p4E3GameTest/java/com/yo1no/gramarye/P4E3StartupObservationTestAccess.java | \
        src/p4E3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupMemoryGameTests.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

verify_repository_scope() {
    local changed=''
    local untracked=''
    local path=''
    local status=0
    changed="$(git diff --name-only HEAD)" || status=$?
    [[ "${status}" -eq 0 ]] || fail 'git failed while checking tracked P4-E3 paths'
    status=0
    untracked="$(git ls-files --others --exclude-standard)" || status=$?
    [[ "${status}" -eq 0 ]] || fail 'git failed while checking untracked P4-E3 paths'
    while IFS= read -r path; do
        [[ -z "${path}" ]] && continue
        is_exact_p4e3_path "${path}" \
            || fail "repository change escaped exact P4-E3 51-path scope: ${path}"
    done <<< "${changed}"$'\n'"${untracked}"
    git diff --quiet HEAD -- \
        src/main/resources src/test/resources docs gradle gradle.properties settings.gradle \
        || fail 'P4-E3 changed resource, authority, wrapper, or version truth'
    [[ ! -e src/p4E3Probe/resources ]] \
        || fail 'P4-E3 must not add tracked probe resources'
    [[ ! -e src/p4E3GameTest/resources ]] \
        || fail 'P4-E3 must not add tracked custom GameTest resources'
}

verify_exact_source_inventory() {
    local source=''
    local relative=''
    local probe_count=0
    local game_count=0
    while IFS= read -r -d '' source; do
        relative="${source#./}"
        is_exact_probe_path "${relative}" \
            || fail "unreviewed P4-E3 probe source: ${relative}"
        probe_count=$((probe_count + 1))
    done < "${PROBE_SOURCE_LIST}"
    while IFS= read -r -d '' source; do
        relative="${source#./}"
        is_exact_game_path "${relative}" \
            || fail "unreviewed P4-E3 custom GameTest source: ${relative}"
        game_count=$((game_count + 1))
    done < "${GAME_SOURCE_LIST}"
    [[ "${probe_count}" -eq 5 ]] \
        || fail "P4-E3 probe source count must be five (found ${probe_count})"
    [[ "${game_count}" -eq 2 ]] \
        || fail "P4-E3 custom GameTest source count must be two (found ${game_count})"
}

verify_existing_verifier_scopes() {
    local verifier=''
    local approved=''
    local approved_count=0
    local modified_existing_verifier_count=0
    for verifier in \
        scripts/verify-p4-b2-b-configuration.sh \
        scripts/verify-p4-e0-r-configuration.sh \
        scripts/verify-p4-e0-r2q-configuration.sh \
        scripts/verify-p4-e1-configuration.sh \
        scripts/verify-p4-e2-configuration.sh; do
        modified_existing_verifier_count=$((modified_existing_verifier_count + 1))
        require_regular_file "${verifier}" \
            "P4-E3 modified existing verifier is missing: ${verifier}"
        if [[ "${verifier}" == scripts/verify-p4-b2-b-configuration.sh ]]; then
            continue
        fi
        approved_count=0
        for approved in \
            .github/workflows/build.yml \
            build.gradle \
            scripts/verify-p4-b2-b-configuration.sh \
            scripts/verify-p4-e0-r-configuration.sh \
            scripts/verify-p4-e0-r2q-configuration.sh \
            scripts/verify-p4-e1-configuration.sh \
            scripts/verify-p4-e2-configuration.sh \
            scripts/verify-p4-e3-configuration.sh \
            src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java \
            src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.java \
            src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java \
            src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java \
            src/p4E3GameTest/java/com/yo1no/gramarye/P4E3StartupObservationTestAccess.java \
            src/p4E3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupMemoryGameTests.java \
            src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/player/P4E3PlayerDataFixture.java \
            src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FileVerifier.java \
            src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureBuilder.java \
            src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureManifest.java \
            src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3ProbeMain.java \
            src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeTest.java \
            src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeVisibilityCompileTest.java \
            src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java \
            src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BCompleteHandoffTest.java \
            src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java \
            src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java \
            src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java \
            src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java \
            src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3ApiGateTest.java \
            src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3LeaseTerminalTest.java \
            src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupLifecycleTest.java; do
            require_fixed "${verifier}" "${approved}" \
                "${verifier} is missing exact Q0.4 path ${approved}"
            approved_count=$((approved_count + 1))
        done
        [[ "${approved_count}" -eq 30 ]] \
            || fail "P4-E3 synchronized verifier inventory must contain 30 paths"
        require_fixed_count "${verifier}" \
            'is_approved_p4e3_changed_path() {' 1 \
            "${verifier} must contain one exact P4-E3 allowlist"
    done
    [[ "${modified_existing_verifier_count}" -eq 5 ]] \
        || fail 'P4-E3 modified existing verifier inventory must contain five scripts'
}

verify_portable_verifier_inventory() {
    local verifier=''
    local verifier_count=0
    for verifier in \
        scripts/verify-p4-b2-b-configuration.sh \
        scripts/verify-p4-c2-a-configuration.sh \
        scripts/verify-p4-c2-b-configuration.sh \
        scripts/verify-p4-d1-configuration.sh \
        scripts/verify-p4-d2-configuration.sh \
        scripts/verify-p4-d3-a-configuration.sh \
        scripts/verify-p4-d3-configuration.sh \
        scripts/verify-p4-e0-r-configuration.sh \
        scripts/verify-p4-e0-r2q-configuration.sh \
        scripts/verify-p4-e1-configuration.sh \
        scripts/verify-p4-e2-configuration.sh \
        scripts/verify-p4-e3-configuration.sh; do
        require_regular_file "${verifier}" \
            "P4-E3 portable verifier is missing: ${verifier}"
        [[ -x "${verifier}" ]] \
            || fail "P4-E3 portable verifier is not executable: ${verifier}"
        verifier_count=$((verifier_count + 1))
    done
    [[ "${verifier_count}" -eq 12 ]] \
        || fail 'P4-E3 portable verifier inventory must contain twelve scripts'
    [[ "$((verifier_count * 3))" -eq 36 ]] \
        || fail 'P4-E3 portable verifier matrix must contain thirty-six invocations'
}

verify_build_contract() {
    local marker=''
    require_ere_count build.gradle \
        "sourceSets\\.create\\('p4E3[A-Za-z0-9_]*'\\)" 2 \
        'P4-E3 must declare exactly two isolated source sets'
    for marker in \
        "sourceSets.create('p4E3Probe')" \
        "sourceSets.create('p4E3GameTest')" \
        'addModdingDependenciesTo(p4E3ProbeSourceSet)' \
        'addModdingDependenciesTo(p4E3GameTestSourceSet)' \
        'add(p4E3ProbeSourceSet.implementationConfigurationName, sourceSets.main.output)' \
        'add(p4E3ProbeSourceSet.implementationConfigurationName, p4A3ProbeSourceSet.output)' \
        'add(p4E3ProbeSourceSet.implementationConfigurationName, p4B2ProbeSourceSet.output)' \
        'add(p4E3ProbeSourceSet.implementationConfigurationName, p4C2ProbeSourceSet.output)' \
        'add(p4E3ProbeSourceSet.implementationConfigurationName, p4D3ProbeSourceSet.output)' \
        'add(p4E3ProbeSourceSet.implementationConfigurationName, p4E0ResearchSourceSet.output)' \
        'add(p4E3GameTestSourceSet.implementationConfigurationName, sourceSets.main.output)' \
        "layout.buildDirectory.dir('generated/p4E3GameTestResources')" \
        'p4E3GameTestSourceSet.output.resourcesDir =' \
        'tasks.named(p4E3GameTestSourceSet.processResourcesTaskName, ProcessResources).configure {' \
        'destinationDir = p4E3GameTestGeneratedResourcesDirectory.get().asFile' \
        "'data/gramarye_p4_e3/structure/p4_e3_probe.nbt'" \
        "'verifyP4E3Configuration', Exec" \
        "tasks.register('prepareP4E3Fixture', JavaExec)" \
        "tasks.named('runP4E3FirstServer', JavaExec)" \
        "tasks.register('verifyP4E3First', JavaExec)" \
        "tasks.named('runP4E3RestartServer', JavaExec)" \
        "tasks.register('verifyP4E3Restart', JavaExec)" \
        "tasks.register('p4E3FixedHeapGate')"; do
        require_fixed build.gradle "${marker}" \
            "P4-E3 build contract is missing ${marker}"
    done
    forbid_fixed build.gradle \
        'add(p4E3GameTestSourceSet.implementationConfigurationName, p4E3ProbeSourceSet.output)' \
        'P4-E3 custom server classpath must exclude probe output'
    forbid_fixed build.gradle "tasks.register('generateP4E3GameTestResources'" \
        'P4-E3 must configure its source-set resource task without adding an eighth custom task'
    forbid_fixed build.gradle 'testImplementation p4E3' \
        'P4-E3 custom outputs must not enter normal JUnit runtime'
    forbid_fixed build.gradle 'runtimeElements.extendsFrom p4E3' \
        'P4-E3 output must not enter publication variants'

    RUNTIME_MOD_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-runtime.XXXXXX")" \
        || fail 'P4-E3 verifier could not create runtime-mod fixture'
    sed -n '/^        p4E3GameTestHarness {$/,/^        }$/p' \
        build.gradle > "${RUNTIME_MOD_BLOCK}"
    require_fixed_count "${RUNTIME_MOD_BLOCK}" '        p4E3GameTestHarness {' 1 \
        'P4-E3 isolated runtime block must appear exactly once'
    require_ere_count "${RUNTIME_MOD_BLOCK}" '^[[:space:]]+sourceSet\(' 2 \
        'P4-E3 custom server mod must contain exactly main and GameTest source sets'
    require_fixed_count "${RUNTIME_MOD_BLOCK}" 'sourceSet(sourceSets.main)' 1 \
        'P4-E3 custom server mod must contain main exactly once'
    require_fixed_count "${RUNTIME_MOD_BLOCK}" \
        'sourceSet(p4E3GameTestSourceSet)' 1 \
        'P4-E3 custom server mod must contain its GameTest source exactly once'
    for marker in p4E3ProbeSourceSet p4E0ResearchSourceSet sourceSets.test; do
        forbid_fixed "${RUNTIME_MOD_BLOCK}" "${marker}" \
            "P4-E3 custom server mod contains forbidden output ${marker}"
    done

    PRODUCTION_MOD_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-production-mod.XXXXXX")" \
        || fail 'P4-E3 verifier could not create production-mod fixture'
    sed -n '/^        "${mod_id}" {$/,/^        }$/p' \
        build.gradle > "${PRODUCTION_MOD_BLOCK}"
    require_fixed_count "${PRODUCTION_MOD_BLOCK}" '        "${mod_id}" {' 1 \
        'default production mod block must appear exactly once'
    require_ere_count "${PRODUCTION_MOD_BLOCK}" '^[[:space:]]+sourceSet\(' 1 \
        'default production mod must contain exactly one source set'
    require_fixed_count "${PRODUCTION_MOD_BLOCK}" 'sourceSet(sourceSets.main)' 1 \
        'default production mod must use sourceSets.main only'
    for marker in p4E3ProbeSourceSet p4E3GameTestSourceSet p4E0ResearchSourceSet; do
        forbid_fixed "${PRODUCTION_MOD_BLOCK}" "${marker}" \
            "default production mod contains forbidden output ${marker}"
    done

    require_ere_count build.gradle \
        '^[[:space:]]+p4E3(First|Restart)Server[[:space:]]*\{' 2 \
        'P4-E3 must declare exactly two custom server run configurations'
    require_fixed_count build.gradle \
        'sourceSet = p4E3GameTestSourceSet' 2 \
        'both P4-E3 servers must use the isolated GameTest source set'
    require_fixed_count build.gradle \
        "systemProperty 'neoforge.enabledGameTestNamespaces', 'gramarye_p4_e3'" 2 \
        'both P4-E3 servers must use the exact custom namespace'
    require_fixed_count build.gradle \
        "systemProperty 'gramarye.p4e3.runMode'" 2 \
        'both P4-E3 servers must bind one exact run mode'
    require_fixed_count build.gradle \
        "systemProperty 'gramarye.p4e3.reportRoot'" 2 \
        'both P4-E3 servers must bind the report root'
    require_fixed_count build.gradle \
        'jvmArguments.addAll(p4E3FixedHeapJvmArgs)' 2 \
        'both P4-E3 servers must use the exact fixed heap'
    require_fixed_count build.gradle \
        'jvmArgs(p4E3FixedHeapJvmArgs)' 3 \
        'all three P4-E3 probe processes must use the exact fixed heap'

    TASK_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-tasks.XXXXXX")" \
        || fail 'P4-E3 verifier could not create task fixture'
    sed -n '/^def verifyP4E3Configuration =/,/^def p4E0ResearchSmokeDirectory =/p' \
        build.gradle > "${TASK_BLOCK}"
    require_fixed_count "${TASK_BLOCK}" 'outputs.upToDateWhen { false }' 5 \
        'each of the five P4-E3 child JVM tasks must disable up-to-date reuse'
    require_fixed_count "${TASK_BLOCK}" 'outputs.cacheIf { false }' 5 \
        'each of the five P4-E3 child JVM tasks must disable cache reuse'
    require_fixed_count "${TASK_BLOCK}" \
        'timeout.set(java.time.Duration.ofSeconds(600))' 3 \
        'prepare and both server children must use 600-second timeouts'
    require_fixed_count "${TASK_BLOCK}" \
        'timeout.set(java.time.Duration.ofSeconds(300))' 2 \
        'both external verifier children must use 300-second timeouts'
    for marker in \
        "'-Xms512m'" \
        "'-Xmx1536m'" \
        "'-XX:+ExitOnOutOfMemoryError'" \
        "layout.buildDirectory.dir('p4-e3/fixed-heap-world')" \
        "layout.buildDirectory.dir('p4-e3/command-work')" \
        "layout.buildDirectory.dir('reports/p4-e3-fixed-heap')" \
        "'prepare-fixture'" \
        "'verify-first'" \
        "'verify-restart'"; do
        require_fixed build.gradle "${marker}" \
            "P4-E3 fixed execution contract is missing ${marker}"
    done
    require_fixed_count "${TASK_BLOCK}" \
        'workingDir(p4E3CommandDirectory.get().asFile)' 3 \
        'all three external probe JVMs must use the build-only command directory'
    require_fixed_count "${TASK_BLOCK}" \
        "throw new GradleException('P4-E3 command working directory is unavailable')" 3 \
        'all three external probe JVMs must fail closed when command-directory creation fails'
    require_fixed "${TASK_BLOCK}" 'verifyP4D3Configuration,' \
        'P4-E3 verification must retain the exact P4-D3 predecessor'
    for edge in \
        'dependsOn(verifyP4E3Configuration)' \
        'dependsOn(prepareP4E3Fixture)' \
        'dependsOn(runP4E3FirstServer)' \
        'dependsOn(verifyP4E3First)' \
        'dependsOn(runP4E3RestartServer)' \
        'dependsOn(verifyP4E3Restart)'; do
        require_fixed "${TASK_BLOCK}" "${edge}" \
            "P4-E3 serialized task graph is missing ${edge}"
    done
    for marker in \
        "tasks.named('jar')" \
        'tasks.named(p4E3ProbeSourceSet.classesTaskName)' \
        'tasks.named(p4E3GameTestSourceSet.classesTaskName)'; do
        require_fixed "${TASK_BLOCK}" "${marker}" \
            "verifyP4E3Configuration is missing dependency ${marker}"
    done
    for marker in retry Retry maxRetries continueOnFailure; do
        forbid_fixed "${TASK_BLOCK}" "${marker}" \
            "P4-E3 task graph contains retry or failure escape ${marker}"
    done
    require_fixed_count "${TASK_BLOCK}" 'P4_E3_FIXED_HEAP_GATE_COMPLETE' 1 \
        'P4-E3 aggregate must emit its exact completion marker once'
}

verify_facade_and_direct_route() {
    local marker=''
    local method=''
    local source=''
    local store_service='src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java'
    local api_gate='src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3ApiGateTest.java'
    local facade_test='src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeTest.java'
    local visibility_test='src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeVisibilityCompileTest.java'
    VIEW_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-view.XXXXXX")" \
        || fail 'P4-E3 verifier could not create view fixture'
    sed -n \
        '/^    public static sealed abstract class E3StartupView permits E3StartupViewImpl {$/,/^    }$/p' \
        src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java \
        > "${VIEW_BLOCK}"
    require_fixed_count "${VIEW_BLOCK}" \
        'public static sealed abstract class E3StartupView permits E3StartupViewImpl {' 1 \
        'the exact sealed E3StartupView declaration changed'
    require_fixed_count "${VIEW_BLOCK}" 'public final ' 13 \
        'E3StartupView must expose exactly thirteen public-final operations'
    for method in \
        beginRecording recordAuditInvocation recordAuditResult \
        recordCompleteConsumeInvocation recordSnapshotInvocation recordSnapshotResult \
        recordReclaimInvocation recordReclaimResult recordDirtyAfter \
        recordIndexTerminal completeRecording abortRecording clearOnServerStopped; do
        require_fixed_count "${VIEW_BLOCK}" " ${method}(" 1 \
            "E3StartupView operation changed or duplicated: ${method}"
    done
    for marker in ' armE3Startup(' ' claimE3Startup(' ' consumeE3Startup('; do
        forbid_fixed "${VIEW_BLOCK}" "${marker}" \
            "E3StartupView leaked a package-private control: ${marker}"
    done
    for marker in \
        'public final E3StartupView e3StartupView()' \
        'private E3StartupView(' \
        'private E3StartupViewImpl(' \
        'static final class E3StartupSession {' \
        'private final P4E2QualificationFacade owner;' \
        'private final long token;' \
        'private E3StartupSession(' \
        'record E3StartupSnapshot(' \
        'long sessionToken,' \
        'int indexTerminalObservations,' \
        'E3IndexTerminal indexTerminal,' \
        'long indexGeneration) {'; do
        require_fixed src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java \
            "${marker}" "facade closed E3 surface is missing ${marker}"
    done
    for marker in \
        'public enum E3AuditVariant {' \
        'public enum E3SnapshotVariant {' \
        'public enum E3ReclaimVariant {' \
        'public enum E3IndexTerminal {'; do
        require_fixed_count src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java \
            "${marker}" 1 "facade must contain one exact bounded enum ${marker}"
    done
    for marker in \
        COMPLETE INCOMPLETE OVER_LIMIT RECONCILIATION_REQUIRED GENERATION_EXHAUSTED \
        TRUNCATED COMPLETED_ZERO COMPLETED_POSITIVE REJECTED UNAVAILABLE \
        COMPLETE_INDEX; do
        require_fixed src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java \
            "${marker}" "facade E3 vocabulary is missing ${marker}"
    done
    for marker in \
        P4E3_STARTUP_OBSERVATION_ALREADY_ACTIVE \
        P4E3_STARTUP_OBSERVATION_ALREADY_CLAIMED \
        P4E3_STARTUP_OBSERVATION_WRONG_STATE \
        P4E3_STARTUP_OBSERVATION_WRONG_CONTEXT \
        P4E3_STARTUP_OBSERVATION_WRONG_SESSION \
        P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE; do
        require_fixed src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java \
            "${marker}" "facade E3 failure taxonomy is missing ${marker}"
    done

    MASKED_SOURCE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-service-mask.XXXXXX")" \
        || fail 'P4-E3 verifier could not create masked service source'
    mask_java_source \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java \
        "${MASKED_SOURCE}"
    [[ "$(literal_invocation_count "${MASKED_SOURCE}" audit)" -eq 1 ]] \
        || fail 'Store service must invoke the global audit exactly once'
    [[ "$(literal_invocation_count "${MASKED_SOURCE}" consumeComplete)" -eq 1 ]] \
        || fail 'Store service must invoke Complete consume exactly once'
    [[ "$(literal_invocation_count "${MASKED_SOURCE}" fromCompleteRoots)" -eq 1 ]] \
        || fail 'Store service must invoke snapshot construction exactly once'
    [[ "$(literal_invocation_count "${MASKED_SOURCE}" observeP4E3IndexTerminal)" -eq 1 ]] \
        || fail 'Store service must invoke the actual post-close terminal observer exactly once'
    require_fixed_count "${MASKED_SOURCE}" 'runP4E3StartupReclaim(server)' 1 \
        'ServerStarting must call the P4-E3 chain exactly once'
    require_fixed_count "${MASKED_SOURCE}" \
        'gameBus.addListener(this::onServerStarting)' 1 \
        'production must retain exactly one ServerStarting listener registration'
    require_fixed_count "${MASKED_SOURCE}" \
        'gameBus.addListener(this::onServerStopped)' 1 \
        'production must retain exactly one ServerStopped listener registration'
    for marker in \
        'onlineReconciliationDependency.reconcile' \
        'PlayerLoggedInEvent' \
        'CompletableFuture' \
        'parallelStream(' \
        'Thread.sleep(' \
        '.post('; do
        forbid_fixed "${MASKED_SOURCE}" "${marker}" \
            "P4-E3 startup chain opened an online/background route: ${marker}"
    done
    while IFS= read -r -d '' source; do
        [[ "${source}" == "${store_service}" ]] && continue
        mask_java_source "${source}" "${MASKED_SOURCE}"
        forbid_fixed "${MASKED_SOURCE}" 'rootAuditService.audit(' \
            "global startup audit escaped the sole Store-service owner (${source})"
        forbid_fixed "${MASKED_SOURCE}" 'runP4E3StartupReclaim(' \
            "closed P4-E3 startup route escaped its sole Store-service owner (${source})"
    done < "${PRODUCTION_SOURCE_LIST}"
    require_fixed src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java \
        'P4E3IndexTerminalObservation observeP4E3IndexTerminal(' \
        'bounded P4-E0-B.9 terminal observer is missing'
    require_fixed src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.java \
        'void markStoreSourceUnchanged()' \
        'package-private P4-E0-B.9 mark seam is missing'
    forbid_fixed src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.java \
        'public void markStoreSourceUnchanged()' \
        'P4-E0-B.9 mark seam became public'

    for marker in \
        'runJdkTool(' \
        '"javap"' \
        '"-public", "-s"' \
        '"-p", "-s", "-c", "-v"' \
        '"jdeps"' \
        '"--ignore-missing-deps"' \
        'completeE3StartupRecording(' \
        '// Field e3CompletedSnapshot:' \
        '// Field e3SessionServer:' \
        '// Field e3State:'; do
        require_fixed "${api_gate}" "${marker}" \
            "P4-E3 API Gate is missing javap/jdeps/bytecode proof ${marker}"
    done
    require_fixed_count "${visibility_test}" \
        'assertRejected("outside/ExternalE3' 8 \
        'P4-E3 must retain exactly eight external negative compile probes'
    for marker in \
        'ToolProvider.getSystemJavaCompiler()' \
        'FacadeStateHarness.java' \
        '"--release", "21"' \
        '"-Xlint:all"' \
        '"-Werror"'; do
        require_fixed "${facade_test}" "${marker}" \
            "P4-E3 positive compile/state harness is missing ${marker}"
    done
}

verify_test_route_and_fixture_contract() {
    local marker=''
    local access='src/p4E3GameTest/java/com/yo1no/gramarye/P4E3StartupObservationTestAccess.java'
    for marker in \
        '@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.DEDICATED_SERVER)' \
        '@SubscribeEvent(priority = EventPriority.HIGHEST)' \
        'static void onServerAboutToStart(ServerAboutToStartEvent event)' \
        'retrieveExactFacade().armE3Startup(event.getServer())' \
        'ModList.get()' \
        'getModContainerById(Gramarye.MOD_ID)' \
        'getCustomExtension(P4E2QualificationFacade.class)' \
        'public static Observation consume(MinecraftServer exactServer)' \
        'public record Observation('; do
        require_fixed "${access}" "${marker}" \
            "P4-E3 deterministic test-only route is missing ${marker}"
    done
    require_fixed_count "${access}" 'ServerAboutToStartEvent event' 1 \
        'P4-E3 test-only route must contain one AboutToStart handler'
    [[ "$(count_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" \
            'ServerAboutToStartEvent')" -eq 0 ]] \
        || fail 'ServerAboutToStart observation listener leaked into production sources'
    forbid_fixed "${access}" 'ServerStoppedEvent' \
        'P4-E3 test-only route must not add a stop listener'
    for marker in \
        ThreadLocal WeakReference IdentityHashMap java.lang.reflect setAccessible \
        Unsafe System.getProperty Files. Path; do
        forbid_fixed "${access}" "${marker}" \
            "P4-E3 test access opened a forbidden side channel: ${marker}"
    done
    forbid_ere "${access}" \
        '^[[:space:]]*(public|protected|private)?[[:space:]]*static[[:space:]]+(final[[:space:]]+)?P4E2QualificationFacade[[:space:]]+[A-Za-z_$][A-Za-z0-9_$]*[[:space:]]*(=|;)' \
        'P4-E3 test access retained a static facade locator'
    OBSERVATION_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-observation.XXXXXX")" \
        || fail 'P4-E3 verifier could not create observation fixture'
    sed -n '/^    public record Observation($/,/^            long indexGeneration) {$/p' \
        "${access}" > "${OBSERVATION_BLOCK}"
    require_ere_count "${OBSERVATION_BLOCK}" \
        '^[[:space:]]+(long|int|boolean|P4E2QualificationFacade\.E3[A-Za-z]+) [a-zA-Z]+[,)]' \
        19 'test-only Observation must have exactly nineteen primitive/enum fields'
    for marker in \
        sessionToken auditInvocations auditVariant auditGeneration \
        completeConsumeInvocations snapshotInvocations snapshotVariant completeRootCount \
        reclaimInvocations reclaimVariant historiesScanned revisionsScanned historiesChanged \
        revisionsReclaimed dirtyBefore dirtyAfter indexTerminalObservations indexTerminal \
        ; do
        require_fixed_count "${OBSERVATION_BLOCK}" " ${marker}," 1 \
            "test-only Observation field changed or duplicated: ${marker}"
    done
    require_fixed_count "${OBSERVATION_BLOCK}" ' indexGeneration) {' 1 \
        'test-only Observation final indexGeneration field changed'
    [[ "$(count_fixed_in_file_list "${GAME_SOURCE_LIST}" '@GameTest(')" -eq 1 ]] \
        || fail 'P4-E3 custom runtime must contain exactly one GameTest dispatcher'
    [[ "$(count_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" '@GameTest(')" -eq 12 ]] \
        || fail 'normal production GameTest inventory must remain exactly twelve'

    for marker in \
        fixture.json first.json restart.json \
        P4_E3_FIXTURE_PREPARED P4_E3_FIRST_VERIFIED P4_E3_RESTART_VERIFIED; do
        require_any_fixed_in_file_list "${PROBE_SOURCE_LIST}" "${marker}" \
            "P4-E3 probe is missing report or marker ${marker}"
    done
    for marker in \
        first-runtime.json restart-runtime.json \
        P4_E3_FIRST_COMPLETE P4_E3_RESTART_COMPLETE; do
        require_any_fixed_in_file_list "${GAME_SOURCE_LIST}" "${marker}" \
            "P4-E3 custom runtime is missing report or marker ${marker}"
    done
    for marker in \
        directory_entries relevant_records compressed_bytes_per_file \
        decompressed_bytes_per_file container_depth_per_file \
        compound_containers_per_file compound_field_entries_per_file \
        list_elements_per_file byte_array_elements_per_file int_array_elements_per_file \
        long_array_elements_per_file modified_utf8_bytes_per_file scalar_tags_per_file \
        compressed_bytes_total decompressed_bytes_total compound_containers_total \
        compound_field_entries_total list_elements_total byte_array_elements_total \
        int_array_elements_total long_array_elements_total modified_utf8_bytes_total \
        scalar_tags_total attachment_admissions raw_root_claims; do
        require_any_fixed_in_file_list "${PROBE_SOURCE_LIST}" "${marker}" \
            "P4-E3 exact 25-counter vector is missing ${marker}"
    done
    for marker in 4_096 2_048 1_024 65_536 2_049 4_095 1_048_538 3955; do
        require_any_fixed_in_file_list "${PROBE_SOURCE_LIST}" "${marker}" \
            "P4-E3 exact fixture geometry is missing ${marker}"
    done
    for marker in \
        Thread.sleep System.gc java.lang.reflect setAccessible sun.misc.Unsafe \
        '@SuppressWarnings' net.minecraft.client org.junit; do
        forbid_fixed_in_file_list "${PROBE_SOURCE_LIST}" "${marker}" \
            "P4-E3 probe code opened a forbidden surface: ${marker}"
        forbid_fixed_in_file_list "${GAME_SOURCE_LIST}" "${marker}" \
            "P4-E3 custom GameTest code opened a forbidden surface: ${marker}"
    done
}

verify_workflow_contract() {
    local job=''
    for job in \
        build p4-a3-memory-gates p4-b-memory-gates p4-c-memory-gates \
        p4-d-memory-gates p4-e-memory-gates; do
        require_fixed_count .github/workflows/build.yml "  ${job}:" 1 \
            "workflow is missing or duplicates exact job ${job}"
    done
    require_ere_count .github/workflows/build.yml '^  [a-z0-9][a-z0-9-]*:$' 6 \
        'workflow must contain exactly six jobs'
    CI_JOB_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-ci.XXXXXX")" \
        || fail 'P4-E3 verifier could not create CI job fixture'
    sed -n '/^  p4-e-memory-gates:/,$p' \
        .github/workflows/build.yml > "${CI_JOB_BLOCK}"
    CI_NEEDS_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-ci-needs.XXXXXX")" \
        || fail 'P4-E3 verifier could not create CI needs fixture'
    sed -n '/^    needs:$/,/^    runs-on:/p' "${CI_JOB_BLOCK}" \
        > "${CI_NEEDS_BLOCK}"
    require_ere_count "${CI_NEEDS_BLOCK}" '^      - [a-z0-9-]+$' 5 \
        'P4-E memory job must need exactly the existing five jobs'
    for job in \
        build p4-a3-memory-gates p4-b-memory-gates p4-c-memory-gates \
        p4-d-memory-gates; do
        require_fixed_count "${CI_NEEDS_BLOCK}" "      - ${job}" 1 \
            "P4-E memory job is missing exact dependency ${job}"
    done
    for marker in \
        '    name: P4-E memory gates' \
        '    timeout-minutes: 45' \
        './gradlew --no-daemon --no-build-cache --rerun-tasks --console=plain p4E3FixedHeapGate'; do
        require_fixed_count "${CI_JOB_BLOCK}" "${marker}" 1 \
            "P4-E memory job is missing exact marker ${marker}"
    done
    require_ere_count "${CI_JOB_BLOCK}" '^        run: ' 1 \
        'P4-E memory job must run only the canonical Gate command'
    for marker in matrix retry continue-on-error allow-failure '|| true' \
        --exclude-task '    if:'; do
        forbid_fixed "${CI_JOB_BLOCK}" "${marker}" \
            "P4-E memory job contains a forbidden escape: ${marker}"
    done
}

verify_jar_isolation_if_present() {
    local jar_path=''
    local class_path=''
    local source=''
    local class_name=''
    local jar_count=0
    local status=0
    if [[ ! -d build/libs ]]; then
        return 0
    fi
    JAR_FILE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-jars.XXXXXX")" \
        || fail 'P4-E3 verifier could not create JAR list'
    JAR_LISTING="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-jar-listing.XXXXXX")" \
        || fail 'P4-E3 verifier could not create JAR listing'
    LC_ALL=C find build/libs -maxdepth 1 -type f -name 'gramarye-*.jar' -print0 \
        > "${JAR_FILE_LIST}" || status=$?
    [[ "${status}" -eq 0 ]] || fail 'P4-E3 verifier could not inspect production JARs'
    while IFS= read -r -d '' jar_path; do
        jar_count=$((jar_count + 1))
        status=0
        jar tf "${jar_path}" > "${JAR_LISTING}" || status=$?
        [[ "${status}" -eq 0 ]] || fail "jar failed while checking ${jar_path}"
        for class_path in \
            'com/yo1no/gramarye/P4E2QualificationFacade$E3StartupView.class' \
            'com/yo1no/gramarye/P4E2QualificationFacade$E3StartupViewImpl.class' \
            'com/yo1no/gramarye/P4E2QualificationFacade$E3AuditVariant.class' \
            'com/yo1no/gramarye/P4E2QualificationFacade$E3SnapshotVariant.class' \
            'com/yo1no/gramarye/P4E2QualificationFacade$E3ReclaimVariant.class' \
            'com/yo1no/gramarye/P4E2QualificationFacade$E3IndexTerminal.class' \
            'com/yo1no/gramarye/P4E2QualificationFacade$E3StartupState.class' \
            'com/yo1no/gramarye/P4E2QualificationFacade$E3StartupSession.class' \
            'com/yo1no/gramarye/P4E2QualificationFacade$E3StartupSnapshot.class'; do
            require_fixed "${JAR_LISTING}" "${class_path}" \
                "production JAR is missing approved closed class ${class_path}"
        done
        while IFS= read -r -d '' source; do
            class_name="${source##*/}"
            class_name="${class_name%.java}"
            forbid_fixed "${JAR_LISTING}" "${class_name}" \
                "P4-E3 probe source leaked into production JAR: ${class_name}"
        done < "${PROBE_SOURCE_LIST}"
        while IFS= read -r -d '' source; do
            class_name="${source##*/}"
            class_name="${class_name%.java}"
            forbid_fixed "${JAR_LISTING}" "${class_name}" \
                "P4-E3 custom GameTest source leaked into production JAR: ${class_name}"
        done < "${GAME_SOURCE_LIST}"
        while IFS= read -r -d '' source; do
            class_path="${source#src/test/java/}"
            class_path="${class_path%.java}"
            forbid_fixed "${JAR_LISTING}" "${class_path}" \
                "normal test output leaked into production JAR: ${class_path}"
        done < "${TEST_SOURCE_LIST}"
        while IFS= read -r -d '' source; do
            class_path="${source#src/p4E0Research/java/}"
            class_path="${class_path%.java}"
            forbid_fixed "${JAR_LISTING}" "${class_path}" \
                "research output leaked into production JAR: ${class_path}"
        done < "${RESEARCH_SOURCE_LIST}"
        while IFS= read -r -d '' source; do
            class_path="${source#src/p4E0ResearchGameTest/java/}"
            class_path="${class_path%.java}"
            forbid_fixed "${JAR_LISTING}" "${class_path}" \
                "research GameTest output leaked into production JAR: ${class_path}"
        done < "${RESEARCH_GAME_SOURCE_LIST}"
        # Qualification tooling is excluded by the exact test, probe, custom GameTest, research,
        # and research GameTest source-owner loops above. Do not reject established main classes
        # merely because their production names contain Candidate or Receipt.
        for class_name in P4E3ApiGateTest P4E3StartupLifecycleTest P4E3LeaseTerminalTest \
                org/junit org/hamcrest \
                'data/gramarye_p4_e3/' 'p4_e3_probe.nbt'; do
            forbid_fixed "${JAR_LISTING}" "${class_name}" \
                "forbidden test or qualification output leaked into production JAR: ${class_name}"
        done
    done < "${JAR_FILE_LIST}"
    [[ "${jar_count}" -eq 1 ]] \
        || fail "P4-E3 verifier expected one production JAR when build/libs exists (found ${jar_count})"

    jar_path=''
    IFS= read -r -d '' jar_path < "${JAR_FILE_LIST}" || true
    [[ -n "${jar_path}" ]] || fail 'P4-E3 verifier lost the production JAR path'
    jar_path="$(cd "$(dirname "${jar_path}")" && pwd)/$(basename "${jar_path}")"
    JAR_EXTRACT_DIRECTORY="$(mktemp -d "${TMPDIR:-/tmp}/gramarye-p4-e3-jar.XXXXXX")" \
        || fail 'P4-E3 verifier could not create JAR extraction directory'
    (
        cd "${JAR_EXTRACT_DIRECTORY}"
        jar xf "${jar_path}" 'com/yo1no/gramarye/P4E2QualificationFacade$E3StartupView.class'
    ) || fail 'P4-E3 verifier could not extract approved facade class'
    cmp -s \
        build/classes/java/main/com/yo1no/gramarye/P4E2QualificationFacade\$E3StartupView.class \
        "${JAR_EXTRACT_DIRECTORY}/com/yo1no/gramarye/P4E2QualificationFacade\$E3StartupView.class" \
        || fail 'production JAR facade class is not byte-identical to this worktree output'
}

main() {
    require_regular_file build.gradle 'P4-E3 verifier cannot inspect build.gradle'
    require_regular_file .github/workflows/build.yml \
        'P4-E3 verifier cannot inspect the workflow'
    require_regular_file scripts/verify-p4-e3-configuration.sh \
        'P4-E3 portable verifier is missing'
    [[ -x scripts/verify-p4-e3-configuration.sh ]] \
        || fail 'P4-E3 portable verifier is not executable'
    forbid_ere scripts/verify-p4-e3-configuration.sh \
        '(^|[;&|()<>`[:space:]])([^;&|()<>`[:space:]]*/)?r[g]([;&|()<>`[:space:]]|$)' \
        'P4-E3 portable verifier must not invoke a developer-only search tool'

    PRODUCTION_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-production.XXXXXX")" \
        || fail 'P4-E3 verifier could not create production source list'
    TEST_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-test.XXXXXX")" \
        || fail 'P4-E3 verifier could not create normal test source list'
    PROBE_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-probe.XXXXXX")" \
        || fail 'P4-E3 verifier could not create probe source list'
    GAME_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-game.XXXXXX")" \
        || fail 'P4-E3 verifier could not create custom GameTest source list'
    RESEARCH_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e3-research.XXXXXX")" \
        || fail 'P4-E3 verifier could not create research source list'
    RESEARCH_GAME_SOURCE_LIST="$(mktemp \
            "${TMPDIR:-/tmp}/gramarye-p4-e3-research-game.XXXXXX")" \
        || fail 'P4-E3 verifier could not create research GameTest source list'
    collect_java_files src/main/java "${PRODUCTION_SOURCE_LIST}"
    collect_java_files src/test/java "${TEST_SOURCE_LIST}"
    collect_java_files src/p4E3Probe/java "${PROBE_SOURCE_LIST}"
    collect_java_files src/p4E3GameTest/java "${GAME_SOURCE_LIST}"
    collect_java_files src/p4E0Research/java "${RESEARCH_SOURCE_LIST}"
    collect_java_files src/p4E0ResearchGameTest/java "${RESEARCH_GAME_SOURCE_LIST}"

    verify_lexical_helpers
    verify_repository_scope
    verify_exact_source_inventory
    verify_existing_verifier_scopes
    verify_portable_verifier_inventory
    verify_build_contract
    verify_facade_and_direct_route
    verify_test_route_and_fixture_contract
    verify_workflow_contract
    verify_jar_isolation_if_present
    printf '%s\n' \
        'Verified exact P4-E3 closed startup route, 51 paths, fixed-heap Gate, workflow, and isolation.'
}

main "$@"
