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

# Exact P9-S3 pre-commit source projection consumed by the historical
# configuration verifiers. No directory or prefix admission is intentional.
is_p9_s3_path() {
    case "$1" in
        build.gradle | \
        scripts/verify-p4-b2-b-configuration.sh | \
        scripts/verify-p4-c2-a-configuration.sh | \
        scripts/verify-p4-c2-b-configuration.sh | \
        scripts/verify-p4-d2-configuration.sh | \
        scripts/verify-p4-d3-a-configuration.sh | \
        scripts/verify-p4-d3-configuration.sh | \
        scripts/verify-p4-e0-r-configuration.sh | \
        scripts/verify-p4-e0-r2q-configuration.sh | \
        scripts/verify-p4-e1-configuration.sh | \
        scripts/verify-p4-e2-configuration.sh | \
        scripts/verify-p4-e3-configuration.sh | \
        scripts/verify-p7-s4-source-contracts.sh | \
        src/main/java/com/yo1no/gramarye/Gramarye.java | \
        src/main/java/com/yo1no/gramarye/P5RuntimeVocabulary.java | \
        src/main/java/com/yo1no/gramarye/P6RuntimeExecutionPortAdapter.java | \
        src/main/java/com/yo1no/gramarye/P7S4LoginManaGameTests.java | \
        src/main/java/com/yo1no/gramarye/P8S3PresentationGameTests.java | \
        src/main/java/com/yo1no/gramarye/P9S3ProjectileGameTests.java | \
        src/main/java/com/yo1no/gramarye/P9StarterProjectile.java | \
        src/main/java/com/yo1no/gramarye/P9StarterProjectileClientEvents.java | \
        src/main/java/com/yo1no/gramarye/P9StarterProjectileRegistration.java | \
        src/main/java/com/yo1no/gramarye/P9WorldEffectHandoff.java | \
        src/main/java/com/yo1no/gramarye/SkillRuntimeService.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionEngine.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionExecutor.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionInvocation.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/DamageActionExecutor.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/DamageActionInvocation.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectCommitPort.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectCommitPort.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionEngine.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectRequest.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectResolution.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectStep.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/P6RuntimeExecutionBridge.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/SpawnProjectileActionExecutor.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/SpawnProjectileActionInvocation.java | \
        src/main/resources/META-INF/accesstransformer.cfg | \
        src/p9S3ClientHarness/java/com/yo1no/gramarye/P9S3ClientRuntimeHarness.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeHardLimitWorkloadTest.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeKernelTest.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeStaticGateTest.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeVocabularyTest.java | \
        src/test/java/com/yo1no/gramarye/P6RuntimeExecutionAdapterTest.java | \
        src/test/java/com/yo1no/gramarye/P6RuntimeExecutionCapabilityTest.java | \
        src/test/java/com/yo1no/gramarye/P6S4BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P7GameTestInventory.java | \
        src/test/java/com/yo1no/gramarye/P8S2BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P9S1BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7S2BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7S3BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionCompensationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionResultTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionThrowableTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionTraceTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionExecutorRegistryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionTransactionTestFixtures.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageActionExecutorTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectCommitPortTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectResolverTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectCommitPortTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectEngineTestDoubles.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionEngineFailureTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionEngineSuccessTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionGuardTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectSemanticBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectTestFixtures.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ManaBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/P6EffectVocabularyTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/P6RuntimeExecutionBridgeTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/P6S3BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/SpawnProjectileActionExecutorTest.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

verify_game_tests() {
    local expected_non_p8 actual actual_non_p8 p8_actual annotation_count source
    expected_non_p8="$(printf '%s\n' \
        'P7S4LoginManaGameTests.java:manaObservationPreservesAvailableAndMalformedAttachmentTruth' \
        'P7S4LoginManaGameTests.java:loginPortRejectsNoncurrentPlayerBeforeSessionOpen' \
        'P7S4LoginManaGameTests.java:e2NormalAndChangedTerminalsHandoffOnceAndQuarantineNeverHandoffs' \
        'P7S4LoginManaGameTests.java:e2LoginPortRuntimeFailurePropagatesTheSameObject' \
        'P7S4LoginManaGameTests.java:e2LoginPortErrorPropagatesTheSameObject' \
        'P7S4LoginManaGameTests.java:actualP9ReservedContinuationSurvivesRootAndClosesLateWithoutWorldEffects' \
        'P7S4LoginManaGameTests.java:actualP9ActorWitnessRejectsRespawnDimensionAndLogoutBeforeTransfer' \
        'P9S3ProjectileGameTests.java:cancelledSweepContinuesAndInvalidImpactsTerminal' \
        'P9S3ProjectileGameTests.java:falseAndThrowingInsertionNeverOpenOrPublish' \
        'P9S3ProjectileGameTests.java:realSpawnTransferHitAndNextDrainUseTheHeldChild' \
        'P9S3ProjectileGameTests.java:replacementRemovalAndDeadlineCloseWithoutDamage' \
        'P9S3ProjectileGameTests.java:reservedClaimAndWrongTransferWitnessesAreOneShot' \
        'P9S3ProjectileGameTests.java:sixteenthOpenPermitIsThePerPlayerMaximum' \
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
    [[ "$(printf '%s\n' "${expected_non_p8}" | wc -l | tr -d ' ')" -eq 34 ]] || {
        printf '%s\n' 'Non-P8 GameTest inventory must remain exact 34' >&2
        return 1
    }
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
    actual_non_p8="$(printf '%s\n' "${actual}" \
        | awk -F: '$1 != "P8S3PresentationGameTests.java"')"
    p8_actual="$(printf '%s\n' "${actual}" \
        | awk -F: '$1 == "P8S3PresentationGameTests.java"')"
    [[ "${actual_non_p8}" == "${expected_non_p8}" ]] || {
        printf '%s\n' 'Non-P8 GameTest source path/method inventory mismatch' >&2
        return 1
    }
    [[ -n "${p8_actual}" ]] || {
        printf '%s\n' 'Exact P8-S3 GameTest holder is empty or missing' >&2
        return 1
    }
    annotation_count="$(find src/main/java/com/yo1no/gramarye -type f -name '*.java' \
        -exec awk '/@GameTest[[:space:]]*\(/ { count++ } END { print count + 0 }' {} + \
        | awk '{ sum += $1 } END { print sum + 0 }')"
    [[ "${annotation_count}" -eq "$(printf '%s\n' "${actual}" | wc -l | tr -d ' ')" ]] \
        || { printf '%s\n' 'Unsupported or duplicate GameTest declaration' >&2; return 1; }
    while IFS= read -r -d '' source; do
        if LC_ALL=C grep -Eq \
                'Thread\.(ofPlatform|ofVirtual)|new[[:space:]]+Thread[[:space:]]*\(|\.unstarted[[:space:]]*\(|\.join[[:space:]]*\([[:space:]]*[0-9]|\.isAlive[[:space:]]*\(|\.interrupt[[:space:]]*\(|AtomicReference|(^|[^[:alnum:]_])(Executor|Future|ProcessBuilder)([^[:alnum:]_]|$)' \
                "${source}"; then
            printf 'Production-packaged GameTest raw worker/task/process surface: %s\n' \
                "${source}" >&2
            return 1
        fi
    done < <(find src/main/java/com/yo1no/gramarye -type f -name '*.java' \
        -exec grep -lZ '@GameTest[[:space:]]*(' {} +)
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
    --is-p8-harness)
        [[ "$#" -eq 2 ]]
        repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
        source_path="${2#"${repository_root}/"}"
        case "${source_path}" in
            src/main/java/com/yo1no/gramarye/P8S3PresentationGameTests.java) exit 0 ;;
            *) exit 1 ;;
        esac ;;
    --is-p9-s3-harness)
        [[ "$#" -eq 2 ]]
        repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
        source_path="${2#"${repository_root}/"}"
        case "${source_path}" in
            src/main/java/com/yo1no/gramarye/P9S3ProjectileGameTests.java) exit 0 ;;
            *) exit 1 ;;
        esac ;;
    --is-s4-path)
        [[ "$#" -eq 2 ]] && { is_s4_path "$2" || is_p9_s3_path "$2"; } ;;
    --game-test-count) [[ "$#" -eq 1 ]] && verify_game_tests ;;
    *) printf '%s\n' 'Expected --is-s4-path PATH, --is-s4-harness PATH, --is-p8-harness PATH, --is-p9-s3-harness PATH, or --game-test-count' >&2; exit 2 ;;
esac
