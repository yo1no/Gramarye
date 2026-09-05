#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STORE_ROOT='src/main/java/com/yo1no/gramarye/magic/definition/store'
PLAYER_SERVICE='src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java'
SUBMISSION_ROOT='src/main/java/com/yo1no/gramarye/magic/definition/submission'
RECOVERY_SERVICE="${SUBMISSION_ROOT}/SkillSubmissionRecoveryService.java"
CEILINGS='src/main/java/com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java'
HISTORY_OBSERVATION="${STORE_ROOT}/P4E1StoreHistoryObservation.java"
GROUPED_STORE_AUDIT="${STORE_ROOT}/P4E1GroupedStoreAudit.java"

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
        "${STORE_ROOT}/P4E1PendingJournalObservation.java" | \
        "${STORE_ROOT}/SkillDefinitionStore.java" | \
        "${STORE_ROOT}/SkillDefinitionStoreSubmissionPort.java" | \
        "${STORE_ROOT}/SkillSubmissionRecoveryGameTests.java" | \
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
        P4E1StoreHistoryObservation \
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

verify_shared_history_observation() {
    [[ -f "${HISTORY_OBSERVATION}" ]] \
        || fail 'missing reviewed opaque P4-E1 Store-history observation'
    [[ -f "${GROUPED_STORE_AUDIT}" ]] \
        || fail 'missing reviewed P4-E1 grouped Store-audit coordinator'
    grep -Fq -- 'sealed interface P4E1StoreHistoryObservation' \
        "${HISTORY_OBSERVATION}" \
        || fail 'opaque Store-history observation declaration drifted'
    grep -Fq -- 'boolean ownerMatches(SkillOwnerId expectedOwner)' \
        "${HISTORY_OBSERVATION}" \
        || fail 'opaque owner-match operation is missing'
    grep -Fq -- 'boolean contains(SkillReference reference)' \
        "${HISTORY_OBSERVATION}" \
        || fail 'opaque exact-reference operation is missing'
    grep -Fq -- 'void discard()' "${HISTORY_OBSERVATION}" \
        || fail 'opaque Store-history cleanup operation is missing'
    if grep -Eq -- '(SkillOwnerId|StoredSkillHistory)[[:space:]]+(owner|history)[[:space:]]*\(' \
            "${HISTORY_OBSERVATION}"; then
        fail 'opaque Store-history observation exposes raw owner/history access'
    fi

    [[ "$(grep -F -c -- 'observeExactHistoryForRootAudit(' \
            "${STORE_ROOT}/SkillDefinitionStore.java")" -eq 1 ]] \
        || fail 'Store must declare one exact root-audit history primitive'
    grep -Fq -- 'this::observeExactHistoryForRootAudit' \
        "${STORE_ROOT}/SkillDefinitionStore.java" \
        || fail 'D1 journal audit no longer delegates to the shared low-level primitive'
    [[ "$(grep -F -c -- '.observeExactHistoryForRootAudit(' \
            "${STORE_ROOT}/P4E1GlobalSourceCapture.java")" -eq 1 ]] \
        || fail 'the owner-bound B1 transfer must have one exact Store-history callsite'
    [[ "$(grep -F -c -- '.observeExactHistory(' \
            "${GROUPED_STORE_AUDIT}")" -eq 1 ]] \
        || fail 'B2-A must have one owner-bound history-observation callsite'
    [[ "$(grep -R -l -F --include='*.java' -- \
            '.observeExactHistoryForRootAudit(' src/main/java | wc -l | tr -d ' ')" -eq 2 ]] \
        || fail 'Store-history primitive escaped the exact B1/E2 owner-bound callsites'
    grep -Fq -- '.observeExactHistoryForRootAudit(' \
        "${STORE_ROOT}/P4E2GroupedStoreValidation.java" \
        || fail 'E2 grouped Store validation lost its exact history callsite'
    if grep -Eq -- 'catch[[:space:]]*\((Error|OutOfMemoryError|Throwable)' \
            "${HISTORY_OBSERVATION}" "${GROUPED_STORE_AUDIT}"; then
        fail 'B2-A catches Error, OutOfMemoryError, or Throwable'
    fi
}

verify_unique_owners() {
    local source=''
    local ceiling_count=0
    local commit_count=0
    local reclaim_count=0
    while IFS= read -r -d '' source; do
        if grep -Fq -- 'MAX_PENDING_ATTACHMENT_UPDATES' "${source}"; then
            case "${source}" in
                "${STORE_ROOT}/PendingAttachmentJournalSchema.java" | \
                "${STORE_ROOT}/P4E2OnlineReconciliationCoordinator.java" | \
                "${STORE_ROOT}/P4E2ReconciliationResult.java" | \
                "${SUBMISSION_ROOT}/SkillSubmissionRecoveryService.java" | \
                "${CEILINGS}") ;;
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
    [[ "${ceiling_count}" -eq 5 ]] \
        || fail 'journal entry ceiling must have its schema and exact recovery consumers'
    [[ "${commit_count}" -eq 1 ]] || fail 'production must have exactly one Store commit caller'
    [[ "${reclaim_count}" -eq 2 ]] || fail 'reviewed P4-B reclaim caller set changed'
}

verify_d2a_sources_and_owners() {
    local source=''
    for source in \
        DefaultSkillSubmissionPolicyProvider \
        RandomUuidSkillIdSource \
        SkillDraftCreationService \
        SkillSubmissionCompositionOutcome \
        SkillSubmissionPolicyProvider \
        SkillSubmissionPolicySnapshot \
        SkillSubmissionPreparationPipeline; do
        [[ -f "${SUBMISSION_ROOT}/${source}.java" ]] \
            || fail "missing reviewed D2-A production source ${source}.java"
    done

    [[ "$(grep -R -l -F --include='*.java' -- 'UUID.randomUUID()' src/main/java | wc -l | tr -d ' ')" -eq 1 ]] \
        || fail 'UUID.randomUUID() must have one production owner'
    grep -Fq -- 'UUID.randomUUID()' "${SUBMISSION_ROOT}/RandomUuidSkillIdSource.java" \
        || fail 'random UUID minting escaped its reviewed adapter'

    [[ "$(grep -R -l -F --include='*.java' -- 'SkillQuota.Unlimited.INSTANCE' src/main/java | wc -l | tr -d ' ')" -eq 1 ]] \
        || fail 'submission Unlimited default must have one production owner'
    [[ "$(grep -R -l -F --include='*.java' -- 'new ValidationContext(MagicPolicyLimits.DEFAULTS)' src/main/java | wc -l | tr -d ' ')" -eq 1 ]] \
        || fail 'submission validation default must have one production owner'
    grep -Fq -- 'SkillQuota.Unlimited.INSTANCE' \
        "${SUBMISSION_ROOT}/DefaultSkillSubmissionPolicyProvider.java" \
        || fail 'Unlimited default escaped the reviewed provider'
    grep -Fq -- 'new ValidationContext(MagicPolicyLimits.DEFAULTS)' \
        "${SUBMISSION_ROOT}/DefaultSkillSubmissionPolicyProvider.java" \
        || fail 'validation default escaped the reviewed provider'

    grep -Fq -- 'prepareLatestTransitionToCurrent(' "${PLAYER_SERVICE}" \
        || fail 'P4-C prepare-to-current seam is missing'
    grep -Fq -- 'checkPreparedTransitionCurrent(' "${PLAYER_SERVICE}" \
        || fail 'P4-C currentness seam is missing'
    [[ "$(grep -F -c -- 'validatePreparedTransition(' "${PLAYER_SERVICE}")" -eq 3 ]] \
        || fail 'P4-C check and publish must share one validator'

    for source in \
        DOCUMENT_BLOB_CAPACITY_REJECTED \
        REVISION_BLOB_CAPACITY_REJECTED \
        HISTORY_BLOB_CAPACITY_REJECTED \
        STORE_BLOB_CAPACITY_REJECTED \
        JOURNAL_ENTRY_COUNT_REJECTED \
        JOURNAL_ENCODED_CAPACITY_REJECTED \
        STORE_CARRIER_INVARIANT_FAILURE \
        JOURNAL_CHAIN_INVARIANT_FAILURE \
        SAVED_DATA_CARRIER_INVARIANT_FAILURE \
        PLAN_TRANSITION_PAIRING_FAILURE \
        AUTHORITY_PRECONDITION_MISMATCH \
        TRANSITION_SERVER_MISMATCH \
        NORMAL_SUBMISSION_NO_OP; do
        grep -Fq -- "${source}" "${STORE_ROOT}/SkillDefinitionStoreSubmissionPort.java" \
            || fail "missing refined D1 preparation code ${source}"
    done
    for source in \
        STORE_CARRIER_REJECTED \
        JOURNAL_REJECTED \
        TRANSITION_NO_OP \
        AUTHORITY_MISMATCH; do
        if grep -Fq -- "${source}" "${STORE_ROOT}/SkillDefinitionStoreSubmissionPort.java"; then
            fail "obsolete D1 preparation umbrella remains: ${source}"
        fi
    done
}

is_reviewed_p7_s2_platform_owner() {
    case "$1:$2" in
        CustomPacketPayload:src/main/java/com/yo1no/gramarye/magic/network/CastIntentPayload.java | \
        CustomPacketPayload:src/main/java/com/yo1no/gramarye/magic/network/IntentAckPayload.java | \
        CustomPacketPayload:src/main/java/com/yo1no/gramarye/magic/network/PlayerManaSyncPayload.java | \
        CustomPacketPayload:src/main/java/com/yo1no/gramarye/magic/network/SkillCooldownSyncPayload.java | \
        CustomPacketPayload:src/main/java/com/yo1no/gramarye/magic/network/P7AuthoritativeSyncService.java | \
        CustomPacketPayload:src/main/java/com/yo1no/gramarye/magic/network/P7S4NetworkGameTests.java | \
        PayloadRegistrar:src/main/java/com/yo1no/gramarye/magic/network/P7PayloadRegistrar.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

verify_phase_boundary() {
    local token=''
    local source=''
    local status=0
    grep -Fq -- 'isChangedGenerationSuccessor(' "${PLAYER_SERVICE}" \
        || fail 'P4-C successor predicate is missing'
    grep -Fq -- 'boolean isBoundTo(MinecraftServer server)' "${PLAYER_SERVICE}" \
        || fail 'P4-C prepared-transition server predicate is missing'
    [[ -f "${RECOVERY_SERVICE}" ]] \
        || fail 'P4-D3-A reviewed recovery service is missing'
    grep -Fq -- 'PlayerEvent.PlayerLoggedInEvent' "${RECOVERY_SERVICE}" \
        || fail 'P4-D3-A reviewed login recovery event owner is missing'
    while IFS= read -r -d '' source; do
        if [[ "${source}" != "${RECOVERY_SERVICE}" ]] \
                && [[ "${source}" != 'src/main/java/com/yo1no/gramarye/magic/network/P7ServerLifecycleEvents.java' ]] \
                && [[ "${source}" != 'src/main/java/com/yo1no/gramarye/P7S4LoginManaGameTests.java' ]] \
                && [[ "${source}" != 'src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java' ]] \
                && grep -Fq -- 'PlayerEvent' "${source}"; then
            fail "PlayerEvent escaped the exact P4-D3-A recovery-service allowlist: ${source}"
        fi
    done < <(find src/main/java -type f -name '*.java' -print0)
    for token in \
        OfflineRoot \
        RootCollector \
        CustomPacketPayload \
        PayloadRegistrar; do
        while IFS= read -r -d '' source; do
            if grep -Fq -- "${token}" "${source}" \
                    && ! is_reviewed_p7_s2_platform_owner "${token}" "${source}"; then
                fail "later-phase production token escaped its exact owner allowlist: ${token} (${source})"
            fi
        done < <(find src/main/java -type f -name '*.java' -print0)
    done
    while IFS= read -r -d '' source; do
        case "${source}" in
            "${GROUPED_STORE_AUDIT}" | \
            'src/main/java/com/yo1no/gramarye/magic/network/P7ReloadAdmissionGate.java' | \
            'src/main/java/com/yo1no/gramarye/magic/network/P7ServerLifecycleCoordinator.java' | \
            'src/main/java/com/yo1no/gramarye/P7S4LoginManaGameTests.java' | \
            'src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java' | \
            "${STORE_ROOT}/SkillRetentionRootAuditResult.java" | \
            "${STORE_ROOT}/SkillRetentionRootAuditService.java" | \
            'src/main/java/com/yo1no/gramarye/Gramarye.java' | \
            'src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java' | \
            'src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java' | \
            "${STORE_ROOT}/P4E2BoundPlayerSkillAttachmentReconciliationCapability.java" | \
            "${STORE_ROOT}/P4E2GroupedStoreValidation.java" | \
            "${STORE_ROOT}/P4E2OnlineReconciliationCoordinator.java" | \
            "${STORE_ROOT}/P4E2OnlineReconciliationDependency.java" | \
            "${STORE_ROOT}/P4E2ReconciliationResult.java" | \
            "${STORE_ROOT}/PlayerSkillAttachmentReconciliationCapability.java" | \
            "${STORE_ROOT}/SkillDefinitionStoreService.java" | \
            "${SUBMISSION_ROOT}/SkillSubmissionRecoveryService.java") continue ;;
        esac
        status=0
        grep -Fq -- 'Reconciliation' "${source}" || status=$?
        case "${status}" in
            0) fail "reconciliation escaped the exact E1/E2 owners: ${source}" ;;
            1) ;;
            *) fail "grep failed while checking reconciliation owner: ${source}" ;;
        esac
    done < <(find src/main/java -type f -name '*.java' -print0)
    grep -Fq -- "sourceSets.create('p4D3Probe')" build.gradle \
        || fail 'P4-D3-B reviewed probe source set is missing'
    grep -Fq -- "sourceSets.create('p4D3GameTest')" build.gradle \
        || fail 'P4-D3-B reviewed dedicated source set is missing'
    grep -Fq -- '    name: P4-D memory gates' .github/workflows/build.yml \
        || fail 'P4-D3-B reviewed CI memory job is missing'
}

main() {
    cd "${REPO_ROOT}"
    verify_exact_sources
    verify_shared_history_observation
    verify_unique_owners
    verify_d2a_sources_and_owners
    verify_phase_boundary
    printf '%s\n' \
        'Verified exact P4-D1 ownership with reviewed P4-D2/D3-A production and D3-B test configuration.'
}

main "$@"
