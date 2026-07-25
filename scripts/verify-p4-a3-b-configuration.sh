#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd -- "${script_dir}/.." && pwd)"
cd "${project_dir}"

require_literal() {
    local file="$1"
    local literal="$2"
    if ! rg -F --quiet -- "${literal}" "${file}"; then
        printf 'P4-A3-B configuration check missing %s in %s\n' "${literal}" "${file}" >&2
        exit 1
    fi
}

for literal in \
    "sourceSets.create('p4A3Probe')" \
    "sourceSets.create('p4A3GameTest')" \
    "tasks.register('generateP4A3GameTestResources', Sync)" \
    "p4A3ProbeTestSupport" \
    "addModdingDependenciesTo(p4A3ProbeSourceSet)" \
    "addModdingDependenciesTo(p4A3GameTestSourceSet)" \
    "'p4A3HeapProbeManySmall', 'many-small'" \
    "'p4A3HeapProbeNearEntry', 'near-entry'" \
    "'p4A3HeapProbeMixed', 'mixed'" \
    "tasks.register('p4A3HeapProbe')" \
    "tasks.named('runP4A3HeapProbeServer', JavaExec)" \
    "'gramarye_p4_a3'" \
    "Duration.ofSeconds(180)" \
    "Duration.ofSeconds(300)" \
    "'-Xms512m'" \
    "'-Xmx1024m'" \
    "'-XX:+ExitOnOutOfMemoryError'"; do
    require_literal build.gradle "${literal}"
done

require_literal \
    src/p4A3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4A3CarrierGameTests.java \
    '@GameTestHolder("gramarye_p4_a3")'
require_literal \
    src/p4A3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4A3CarrierGameTests.java \
    'templateNamespace = "gramarye_p4_a3"'

for literal in \
    'p4-a3-memory-gates:' \
    'needs: build' \
    "java-version: '21'" \
    'timeout-minutes: 15' \
    'timeout-minutes: 5' \
    'timeout-minutes: 6' \
    './gradlew --no-daemon --console=plain verifyP4A3BConfiguration' \
    './gradlew --no-daemon --console=plain p4A3HeapProbe' \
    './gradlew --no-daemon --console=plain runP4A3HeapProbeServer'; do
    require_literal .github/workflows/build.yml "${literal}"
done

if rg --quiet 'continue-on-error|allow-failure' .github/workflows/build.yml; then
    printf 'P4-A3-B memory Gate must not allow failure\n' >&2
    exit 1
fi

if rg --quiet 'P4A3HeapProbe|P4A3CarrierGameTests' src/main/java; then
    printf 'P4-A3-B probe code leaked into production Java sources\n' >&2
    exit 1
fi

if rg --quiet 'SkillSavedData|PlayerSkillAttachment|PendingAttachmentJournal' src/main/java; then
    printf 'P4-B or later lifecycle types appeared during P4-A3-B\n' >&2
    exit 1
fi

test -f build/classes/java/p4A3Probe/com/yo1no/gramarye/magic/definition/store/P4A3HeapProbeMain.class
test -f build/classes/java/p4A3GameTest/com/yo1no/gramarye/magic/definition/store/P4A3CarrierGameTests.class
test -f build/resources/p4A3GameTest/data/gramarye_p4_a3/structure/p4_a3_probe.nbt

jar_path="$(find build/libs -maxdepth 1 -type f -name 'gramarye-*.jar' -print -quit)"
if [[ -z "${jar_path}" ]]; then
    printf 'P4-A3-B configuration check could not find the production JAR\n' >&2
    exit 1
fi
if jar tf "${jar_path}" | rg --quiet 'P4A3|p4A3Probe|p4A3GameTest'; then
    printf 'P4-A3-B probe classes or resources leaked into the production JAR\n' >&2
    exit 1
fi

printf 'Verified P4-A3-B task, source-set, CI, and JAR isolation contracts.\n'
