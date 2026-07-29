#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STORE_ROOT='src/main/java/com/yo1no/gramarye/magic/definition/store'
PLAYER_SERVICE='src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java'
CEILINGS='src/main/java/com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java'

fail() {
    printf 'P4-D1 configuration failure: %s\n' "$1" >&2
    exit 1
}

is_reviewed_d1_journal_owner() {
    case "$1" in
        "${STORE_ROOT}/GramaryeSkillSavedData.java" | \
        "${STORE_ROOT}/JournalTargetAuditProof.java" | \
        "${STORE_ROOT}/JournalTargetAuditResult.java" | \
        "${STORE_ROOT}/PendingAttachmentJournal.java" | \
        "${STORE_ROOT}/PendingAttachmentJournalFailure.java" | \
        "${STORE_ROOT}/PendingAttachmentJournalFraming.java" | \
        "${STORE_ROOT}/PendingAttachmentJournalLifecycle.java" | \
        "${STORE_ROOT}/PendingAttachmentJournalMigration.java" | \
        "${STORE_ROOT}/PendingAttachmentJournalSchema.java" | \
        "${STORE_ROOT}/PendingAttachmentJournalState.java" | \
        "${STORE_ROOT}/PendingAttachmentJournalWireScan.java" | \
        "${STORE_ROOT}/SkillDefinitionStore.java" | \
        "${STORE_ROOT}/SkillDefinitionStoreSubmissionPort.java" | \
        "${STORE_ROOT}/SkillSavedDataLifecycleGameTests.java") return 0 ;;
        *) return 1 ;;
    esac
}

verify_exact_sources() {
    local source=''
    for source in \
        JournalTargetAuditProof \
        JournalTargetAuditResult \
        PendingAttachmentJournal \
        PendingAttachmentJournalFailure \
        PendingAttachmentJournalFraming \
        PendingAttachmentJournalLifecycle \
        PendingAttachmentJournalMigration \
        PendingAttachmentJournalSchema \
        PendingAttachmentJournalState \
        PendingAttachmentJournalWireScan \
        SkillDefinitionStoreSubmissionPort \
        StoreSubmissionAuthorityObservation; do
        [[ -f "${STORE_ROOT}/${source}.java" ]] \
            || fail "missing reviewed production source ${source}.java"
    done

    while IFS= read -r -d '' source; do
        if grep -Fq -- 'PendingAttachmentJournal' "${source}"; then
            is_reviewed_d1_journal_owner "${source}" \
                || fail "pending journal escaped exact source allowlist: ${source}"
        fi
    done < <(find src/main/java -type f -name '*.java' -print0)

    while IFS= read -r -d '' source; do
        if grep -Eq -- '^public[[:space:]]+(final[[:space:]]+)?(class|record|interface|enum)[[:space:]]+' \
                "${source}"; then
            [[ "${source}" == "${STORE_ROOT}/SkillDefinitionStoreSubmissionPort.java" ]] \
                || fail "D1 added an unreviewed public top-level: ${source}"
        fi
    done < <(find "${STORE_ROOT}" -maxdepth 1 -type f \
        \( -name 'PendingAttachmentJournal*.java' \
        -o -name 'JournalTargetAudit*.java' \
        -o -name 'StoreSubmissionAuthorityObservation.java' \
        -o -name 'SkillDefinitionStoreSubmissionPort.java' \) -print0)
}

verify_unique_owners() {
    local source=''
    local ceiling_count=0
    local commit_count=0
    local reclaim_count=0
    while IFS= read -r -d '' source; do
        if grep -Fq -- 'MAX_PENDING_ATTACHMENT_UPDATES' "${source}"; then
            case "${source}" in
                "${STORE_ROOT}/PendingAttachmentJournalSchema.java" | "${CEILINGS}") ;;
                *) fail "journal entry ceiling escaped its unique schema owner: ${source}" ;;
            esac
            ceiling_count=$((ceiling_count + 1))
        fi
        if grep -Eq -- '\.[[:space:]]*commit[[:space:]]*\(' "${source}"; then
            [[ "${source}" == "${STORE_ROOT}/SkillDefinitionStoreSubmissionPort.java" ]] \
                || fail "Store commit escaped the unique D1 port: ${source}"
            commit_count=$((commit_count + 1))
        fi
        if grep -Eq -- '\.[[:space:]]*reclaim[[:space:]]*\(' "${source}"; then
            case "${source}" in
                "${STORE_ROOT}/GramaryeSkillSavedData.java" | \
                "${STORE_ROOT}/SkillDefinitionStoreService.java") ;;
                *) fail "D1 added an unreviewed Store reclaim caller: ${source}" ;;
            esac
            reclaim_count=$((reclaim_count + 1))
        fi
    done < <(find src/main/java -type f -name '*.java' -print0)
    [[ "${ceiling_count}" -eq 2 ]] || fail 'journal entry ceiling must have one schema consumer'
    [[ "${commit_count}" -eq 1 ]] || fail 'production must have exactly one Store commit caller'
    [[ "${reclaim_count}" -eq 2 ]] || fail 'reviewed P4-B reclaim caller set changed'
}

verify_phase_boundary() {
    local token=''
    grep -Fq -- 'isChangedGenerationSuccessor(' "${PLAYER_SERVICE}" \
        || fail 'P4-C successor predicate is missing'
    grep -Fq -- 'boolean isBoundTo(MinecraftServer server)' "${PLAYER_SERVICE}" \
        || fail 'P4-C prepared-transition server predicate is missing'
    for token in \
        SkillDefinitionSubmissionService \
        SkillSubmissionPolicyProvider \
        RandomUuidSkillIdSource \
        SkillDraftCreationService \
        PlayerLoggedInEvent \
        OfflineRoot \
        RootCollector \
        Reconciliation \
        CustomPacketPayload \
        PayloadRegistrar; do
        if grep -R -Fq --include='*.java' -- "${token}" src/main/java; then
            fail "later-phase production token appeared: ${token}"
        fi
    done
    if grep -Fq -- 'p4D' build.gradle; then
        fail 'P4-D must not add a Gradle task/source set'
    fi
    if grep -Fq -- 'P4-D memory gates' .github/workflows/build.yml; then
        fail 'P4-D must not add a CI memory job'
    fi
}

main() {
    cd "${REPO_ROOT}"
    verify_exact_sources
    verify_unique_owners
    verify_phase_boundary
    printf '%s\n' 'Verified exact P4-D1 production ownership and later-phase absence.'
}

main "$@"
