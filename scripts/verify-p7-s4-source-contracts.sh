#!/usr/bin/env bash
set -euo pipefail

# Direct shared inventory for the existing P4/P7 consumers. This is not a phase Gate.
is_s4_path() {
    case "$1" in
        scripts/verify-p7-s4-source-contracts.sh | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationCoordinator.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillDefinitionSubmissionGameTests.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeHardLimitWorkloadTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/P7GameTestInventory.java | \
        src/test/java/com/yo1no/gramarye/P7ManaSnapshotBridgeTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7S4ServerBehaviorTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7ClientMirrorTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LoginReadyHandoffTest.java | \
        src/main/java/com/yo1no/gramarye/P7S4LoginManaGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/P7ManaSnapshotBridge.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7SyncSequence.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ServerSyncState.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7AuthoritativeSyncService.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ServerLifecycleCoordinator.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ServerLifecycleEvents.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ReloadStartEvents.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7Diagnostics.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ClientMirror.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ClientMirrorDispatchFactory.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ClientLifecycleEvents.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7S4NetworkGameTests.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

verify_game_tests() {
    local expected actual annotation_count
    expected="$(printf '%s\n' \
        'P7S4LoginManaGameTests.java:manaObservationPreservesAvailableAndMalformedAttachmentTruth' \
        'P7S4LoginManaGameTests.java:loginPortRejectsNoncurrentPlayerBeforeSessionOpen' \
        'P7S4LoginManaGameTests.java:e2NormalAndChangedTerminalsHandoffOnceAndQuarantineNeverHandoffs' \
        'P7S4LoginManaGameTests.java:e2LoginPortRuntimeFailurePropagatesTheSameObject' \
        'P7S4LoginManaGameTests.java:e2LoginPortErrorPropagatesTheSameObject' \
        'gametest/PlatformGameTests.java:customDescriptorRegistriesLoadEmpty' \
        'gametest/PlatformGameTests.java:dedicatedServerLoads' \
        'gametest/PlatformGameTests.java:descriptorMigrationCoverageAuditPassesAfterRegistryFreeze' \
        'gametest/PlatformGameTests.java:productionDefinitionLookupsResolveMissingTypesSafely' \
        'magic/definition/player/PlayerSkillAttachmentGameTests.java:registeredAttachmentPersistsThroughActualPlayerdataSaveAndReload' \
        'magic/definition/player/PlayerSkillAttachmentGameTests.java:registeredQuarantineAndCopyLifecycleRemainTotal' \
        'magic/definition/store/SkillSavedDataLifecycleGameTests.java:startupInstalledExactReadyAdapterInOverworldCache' \
        'magic/definition/store/SkillSubmissionRecoveryGameTests.java:persistedBaseReplaysPendingChainOnLogin' \
        'magic/definition/store/SkillSubmissionRecoveryGameTests.java:persistedFinalClearsPendingChainWithoutReplayOnLogin' \
        'magic/definition/store/SkillSubmissionRecoveryGameTests.java:persistedIntermediateClearsPrefixBeforeReplayOnLogin' \
        'magic/definition/submission/SkillDefinitionSubmissionGameTests.java:fullSubmissionCommitsStoreJournalThenAttachmentExactlyOnce' \
        'magic/definition/submission/SkillDefinitionSubmissionGameTests.java:postCommitAttachmentDriftReturnsPendingRecovery' \
        'magic/network/P7S4NetworkGameTests.java:actualPostE2LoginOpensOneSessionAndSubmitsOneInitialFullSet' \
        'magic/network/P7S4NetworkGameTests.java:actualRespawnDimensionAndReconnectPreserveThenReplaceSessionIdentity' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:deathCloneCopiesExactManaState' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:dimensionTravelKeepsSingleManaTruth' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:duplicatePersistentManaTruthIsAbsent' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:malformedAttachmentRemainsUnavailableWithoutMutation' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:newPlayerAbsentStateIsAvailableZero' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:nonDeathCloneCopiesExactManaState' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:validAttachmentSerializesAndLoadsExactly' \
        | LC_ALL=C sort)"
    actual="$(find src/main/java/com/yo1no/gramarye -type f -name '*.java' \
        -exec awk '
            FNR == 1 { pending = 0 }
            /@GameTest[[:space:]]*\(/ { pending = 1 }
            pending && /public[[:space:]]+static[[:space:]]+void[[:space:]]+/ {
                method = $0
                sub(/^.*public[[:space:]]+static[[:space:]]+void[[:space:]]+/, "", method)
                sub(/[[:space:]]*\(.*$/, "", method)
                file = FILENAME
                sub(/^src\/main\/java\/com\/yo1no\/gramarye\//, "", file)
                print file ":" method
                pending = 0
            }
        ' {} + | LC_ALL=C sort)"
    [[ "${actual}" == "${expected}" ]] || {
        printf '%s\n' 'P7-S4 GameTest source path/method inventory mismatch' >&2
        return 1
    }
    annotation_count="$(find src/main/java/com/yo1no/gramarye -type f -name '*.java' \
        -exec awk '/@GameTest[[:space:]]*\(/ { count++ } END { print count + 0 }' {} + \
        | awk '{ sum += $1 } END { print sum + 0 }')"
    [[ "${annotation_count}" -eq "$(printf '%s\n' "${actual}" | wc -l | tr -d ' ')" ]] \
        || { printf '%s\n' 'Unsupported or duplicate GameTest declaration' >&2; return 1; }
    printf '%s\n' "${annotation_count}"
}

case "${1:-}" in
    --is-s4-harness)
        [[ "$#" -eq 2 ]]
        repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
        source_path="${2#"${repository_root}/"}"
        case "${source_path}" in
            src/main/java/com/yo1no/gramarye/P7S4LoginManaGameTests.java | \
            src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java | \
            src/main/java/com/yo1no/gramarye/magic/network/P7S4NetworkGameTests.java) exit 0 ;;
            *) exit 1 ;;
        esac ;;
    --is-s4-path) [[ "$#" -eq 2 ]] && is_s4_path "$2" ;;
    --game-test-count) [[ "$#" -eq 1 ]] && verify_game_tests ;;
    *) printf '%s\n' 'Expected --is-s4-path PATH, --is-s4-harness PATH, or --game-test-count' >&2; exit 2 ;;
esac
