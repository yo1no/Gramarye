#!/bin/bash
set -euo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
MAIN_JAVA="$REPOSITORY_ROOT/src/main/java"
STORE_ROOT="$MAIN_JAVA/com/yo1no/gramarye/magic/definition/store"
PLAYER_ROOT="$MAIN_JAVA/com/yo1no/gramarye/magic/definition/player"
CEILINGS="$MAIN_JAVA/com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java"
BUDGET="$STORE_ROOT/P4E1AuditBudget.java"
HEAP_OBSERVATION="$STORE_ROOT/P4E1HeapFloorObservation.java"
PREFLIGHT="$STORE_ROOT/P4E1SourceAdmissionPreflight.java"
ADMISSION_SOURCE="$STORE_ROOT/PlayerSkillAttachmentAdmissionSource.java"
BOUND_ADMISSION_SOURCE="$STORE_ROOT/P4E1BoundPlayerSkillAttachmentAdmissionSource.java"
AUDITED_CAPTURE="$STORE_ROOT/P4E1AuditedCapture.java"
COMPLETE_HANDOFF="$STORE_ROOT/P4E1CompleteRootHandoff.java"
FINAL_FRESHNESS="$STORE_ROOT/P4E1FinalFreshness.java"
GROUPED_STORE_AUDIT="$STORE_ROOT/P4E1GroupedStoreAudit.java"
HISTORY_OBSERVATION="$STORE_ROOT/P4E1StoreHistoryObservation.java"
AUDIT_RESULT="$STORE_ROOT/SkillRetentionRootAuditResult.java"
AUDIT_SERVICE="$STORE_ROOT/SkillRetentionRootAuditService.java"
STORE="$STORE_ROOT/SkillDefinitionStore.java"
GLOBAL_CAPTURE="$STORE_ROOT/P4E1GlobalSourceCapture.java"
PLAYER_SERVICE="$PLAYER_ROOT/PlayerSkillAttachmentService.java"
HEAP_CHILD="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorChildMatrixTest.java"
HEAP_PROBE="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorProbeMain.java"
HEAP_UNIT="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorObservationTest.java"
PREFLIGHT_UNIT="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceAdmissionPreflightTest.java"
PHASE_TYPES="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4EPhaseTypes.java"
A1_API_GATE="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1A1ApiGateTest.java"
A1_VISIBILITY_GATE="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1A1VisibilityCompileTest.java"
A1_BRIDGE_TEST="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1RootAuditBridgeTest.java"
A1_TEST_SUPPORT="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentServiceTestSupport.java"
B1_API_GATE="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1ApiGateTest.java"
B1_CORE_TEST="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1CoreTest.java"
B2A_API_GATE="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2AApiGateTest.java"
B2A_STORE_AUDIT_TEST="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2AStoreAuditTest.java"
B2B_API_GATE="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java"
B2B_FINAL_FRESHNESS_TEST="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BFinalFreshnessTest.java"
B2B_INDEX_LIFECYCLE_TEST="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BIndexLifecycleTest.java"
B2B_COMPLETE_HANDOFF_TEST="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BCompleteHandoffTest.java"
B_API_GATE="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java"
HISTORY_OBSERVATION_TEST="$REPOSITORY_ROOT/src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1StoreHistoryObservationTest.java"
E0_LEDGER="$REPOSITORY_ROOT/docs/architecture/P4-E0-root-audit-boundary.md"

fail() {
    echo "P4-E1 configuration verification failed: $1" >&2
    exit 1
}

grep_state() {
    local file=$1
    local token=$2
    set +e
    grep -F -q -- "$token" "$file" 2>/dev/null
    GREP_RESULT=$?
    set -e
}

require_fixed() {
    local file=$1
    local token=$2
    grep_state "$file" "$token"
    case "$GREP_RESULT" in
        0) ;;
        1) fail "required marker missing from $file: $token" ;;
        *) fail "grep tool error while reading $file" ;;
    esac
}

reject_fixed() {
    local file=$1
    local token=$2
    grep_state "$file" "$token"
    case "$GREP_RESULT" in
        0) fail "forbidden marker present in $file: $token" ;;
        1) ;;
        *) fail "grep tool error while reading $file" ;;
    esac
}

is_approved_p4e3_changed_path() {
    case "$1" in
        .github/workflows/build.yml | \
        build.gradle | \
        scripts/verify-p4-b2-b-configuration.sh | \
        scripts/verify-p4-e0-r-configuration.sh | \
        scripts/verify-p4-e0-r2q-configuration.sh | \
        scripts/verify-p4-e1-configuration.sh | \
        scripts/verify-p4-e2-configuration.sh | \
        scripts/verify-p4-e3-configuration.sh | \
        src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java | \
        src/p4E3GameTest/java/com/yo1no/gramarye/P4E3StartupObservationTestAccess.java | \
        src/p4E3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupMemoryGameTests.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/player/P4E3PlayerDataFixture.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FileVerifier.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureBuilder.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureManifest.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3ProbeMain.java | \
        src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeTest.java | \
        src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeVisibilityCompileTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BCompleteHandoffTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3LeaseTerminalTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupLifecycleTest.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

is_approved_p6_s2_r3_changed_path() {
    case "$1" in
        docs/architecture/P5-A-server-runtime-event-kernel.md | \
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
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachments.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ManaAttachmentDefinitionBridge.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ManaAttachments.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ManaBoundaryTest.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

is_approved_p6_s3_relocation_deletion_path() {
    case "$1" in
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/DamageEffectCommitPort.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectCommitPlan.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionEngine.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionGuard.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionResult.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectRequest.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectResolution.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectStep.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectStepOutcome.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectTrace.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/P6EffectBounds.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/P6ExecutionInvariantException.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/DamageEffectCommitPortTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/DamageEffectRequestTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/DamageEffectResolverTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectCommitPlanTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectEngineTestDoubles.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionEngineFailureTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionEngineSuccessTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionGuardTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionResultTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectSemanticBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectStepOutcomeTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectTestFixtures.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectTraceTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/P6EffectVocabularyTest.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

is_approved_p6_s3_changed_path() {
    case "$1" in
        scripts/verify-p4-c2-b-configuration.sh | \
        scripts/verify-p4-d3-a-configuration.sh | \
        scripts/verify-p4-d3-configuration.sh | \
        scripts/verify-p4-e0-r-configuration.sh | \
        scripts/verify-p4-e0-r2q-configuration.sh | \
        scripts/verify-p4-e1-configuration.sh | \
        scripts/verify-p4-e2-configuration.sh | \
        scripts/verify-p4-e3-configuration.sh | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/DamageEffectCommitPort.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectCommitPlan.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionEngine.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionGuard.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionResult.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectRequest.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectResolution.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectStep.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectStepOutcome.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectTrace.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/P6EffectBounds.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/P6ExecutionInvariantException.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectCommitPort.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectCommitPlan.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionEngine.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionGuard.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionResult.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectRequest.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectResolution.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectStep.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectStepOutcome.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectTrace.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/P6EffectBounds.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/P6ExecutionInvariantException.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionExecutor.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionExecutorRegistry.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/DamageActionInvocation.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/DamageActionExecutor.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionResult.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionEngine.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/DamageEffectCommitPortTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/DamageEffectRequestTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/DamageEffectResolverTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectCommitPlanTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectEngineTestDoubles.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionEngineFailureTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionEngineSuccessTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionGuardTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionResultTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectSemanticBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectStepOutcomeTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectTestFixtures.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/EffectTraceTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/effect/P6EffectVocabularyTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectCommitPortTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectRequestTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectResolverTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectCommitPlanTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectEngineTestDoubles.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionEngineFailureTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionEngineSuccessTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionGuardTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionResultTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectSemanticBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectStepOutcomeTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectTestFixtures.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectTraceTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/P6EffectVocabularyTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionExecutorRegistryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageActionExecutorTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionPreDebitTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionDebitTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionOutcomeTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionCompensationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionThrowableTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionTraceTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionResultTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/P6S3BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionTransactionTestFixtures.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ManaBoundaryTest.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

is_allowed_changed_path() {
    is_approved_p4e3_changed_path "$1" && return 0
    is_approved_p6_s2_r3_changed_path "$1" && return 0
    is_approved_p6_s3_changed_path "$1" && return 0
    case "$1" in
        docs/architecture/P4-0-persistence-boundary.md | \
        docs/architecture/P4-E0-root-audit-boundary.md | \
        scripts/verify-p4-c2-a-configuration.sh | \
        scripts/verify-p4-c2-b-configuration.sh | \
        scripts/verify-p4-d2-configuration.sh | \
        scripts/verify-p4-d3-a-configuration.sh | \
        scripts/verify-p4-d3-configuration.sh | \
        scripts/verify-p4-d1-configuration.sh | \
        scripts/verify-p4-e0-r-configuration.sh | \
        scripts/verify-p4-e0-r2q-configuration.sh | \
        scripts/verify-p4-e0-r-configuration.sh | \
        scripts/verify-p4-e0-r2q-configuration.sh | \
        scripts/verify-p4-e1-configuration.sh | \
        scripts/verify-p4-e2-configuration.sh | \
        src/p4C2GameTest/java/com/yo1no/gramarye/P4E2QualificationFacadeTestAccess.java | \
        src/p4C2GameTest/java/com/yo1no/gramarye/magic/definition/player/P4C2MemoryGameTests.java | \
        src/p4C2Probe/java/com/yo1no/gramarye/P4E2QualificationObservation.java | \
        src/p4C2Probe/java/com/yo1no/gramarye/magic/definition/player/P4C2FileVerifier.java | \
        src/p4C2Probe/java/com/yo1no/gramarye/magic/definition/player/P4C2FixtureBuilder.java | \
        src/p4C2Probe/java/com/yo1no/gramarye/magic/definition/store/P4C2StoreProbe.java | \
        src/main/java/com/yo1no/gramarye/Gramarye.java | \
        src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSourceObservation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditBudget.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditedCapture.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1BoundPlayerSkillAttachmentAdmissionSource.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1FinalFreshness.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1IntegratedSnapshotTraversal.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PendingJournalObservation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataDirectorySnapshot.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataSourceSelector.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1RawClaimBuffer.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1RootSourceFamily.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceFailure.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceInventory.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1StoreHistoryObservation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2BoundPlayerSkillAttachmentReconciliationCapability.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2GroupedStoreValidation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationCoordinator.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationDependency.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2ReconciliationResult.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentReconciliationCapability.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditResult.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentAdmissionSource.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStore.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionPort.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillDefinitionSubmissionGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/player/P4E2AtomicReconciliationTest.java | \
        src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeTest.java | \
        src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeVisibilityCompileTest.java | \
        src/test/java/com/yo1no/gramarye/P4E2QualificationObservationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentServiceTestSupport.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/player/P4C2FixtureTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2BPhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4A2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4A3AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2PhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3PhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4DPhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1A1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1A1VisibilityCompileTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1CoreTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2AStoreAuditTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BCompleteHandoffTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BFinalFreshnessTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BIndexLifecycleTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1StoreHistoryObservationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1IntegratedSnapshotTraversalTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataSourceSelectorTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1RootAuditBridgeTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2GroupedStoreValidationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2PhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2VisibilityCompileTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionAuthorityTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryServiceTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4EPhaseTypes.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

verify_changed_paths() {
    local temporary tracked untracked path candidate staged_status
    temporary=$(mktemp -d "${TMPDIR:-/tmp}/gramarye-p4-e1-paths.XXXXXX")
    tracked="$temporary/tracked.bin"
    untracked="$temporary/untracked.bin"
    trap 'rm -rf -- "$temporary"' RETURN

    set +e
    git -C "$REPOSITORY_ROOT" diff --cached --quiet --no-ext-diff --
    staged_status=$?
    set -e
    case "$staged_status" in
        0) ;;
        1) fail "the P4-E1-A verifier requires an empty staged diff" ;;
        *) fail "git failed while checking the staged diff" ;;
    esac

    git -C "$REPOSITORY_ROOT" diff \
            --name-only --no-renames --no-ext-diff -z HEAD -- > "$tracked" \
        || fail "git failed while reading tracked E1-A changes"
    git -C "$REPOSITORY_ROOT" ls-files \
            --others --exclude-standard -z -- > "$untracked" \
        || fail "git failed while reading untracked E1-A changes"

    for inventory in "$tracked" "$untracked"; do
        while IFS= read -r -d '' path; do
            is_allowed_changed_path "$path" \
                || fail "changed path is outside the exact E1-A allowlist: $path"
            candidate="$REPOSITORY_ROOT/$path"
            if is_approved_p6_s3_relocation_deletion_path "$path"; then
                [ ! -e "$candidate" ] && [ ! -L "$candidate" ] \
                    || fail "approved P6-S1 relocation source still exists: $path"
                continue
            fi
            [ -f "$candidate" ] \
                || fail "allowed E1-A path is missing or not a regular file: $path"
            [ ! -L "$candidate" ] \
                || fail "allowed E1-A path is a symlink: $path"
            case "$path" in
                scripts/*.sh)
                    [ -x "$candidate" ] \
                        || fail "allowed E1-A verifier is not executable: $path"
                    ;;
                build.gradle | .github/workflows/build.yml)
                    [ ! -x "$candidate" ] \
                        || fail "allowed P4-E3 build/workflow path is executable: $path"
                    ;;
                *.java | *.md) ;;
                *) fail "allowed E1-A path has an unexpected file type: $path" ;;
            esac
        done < "$inventory"
    done

    rm -rf -- "$temporary"
    trap - RETURN
}

require_exact_count() {
    local file=$1
    local token=$2
    local expected=$3
    local actual
    actual=$(grep -F -c -- "$token" "$file") || {
        local status=$?
        if [ "$status" -eq 1 ]; then
            actual=0
        else
            fail "grep tool error while counting $file"
        fi
    }
    [ "$actual" -eq "$expected" ] \
        || fail "expected $expected occurrences in $file, found $actual: $token"
}

self_regression() {
    local temporary
    temporary=$(mktemp -d "${TMPDIR:-/tmp}/gramarye-p4-e1-verifier.XXXXXX")
    trap 'rm -rf -- "$temporary"' RETURN
    echo 'required-marker' > "$temporary/present.txt"

    grep_state "$temporary/present.txt" 'required-marker'
    [ "$GREP_RESULT" -eq 0 ] || fail "self-test could not classify present marker"
    grep_state "$temporary/present.txt" 'missing-marker'
    [ "$GREP_RESULT" -eq 1 ] || fail "self-test could not classify missing marker"
    grep_state "$temporary/missing-file.txt" 'required-marker'
    [ "$GREP_RESULT" -gt 1 ] || fail "self-test confused tool error with missing marker"

    grep_state "$temporary/present.txt" 'forbidden-marker'
    [ "$GREP_RESULT" -eq 1 ] || fail "self-test confused absent forbidden marker"
    echo 'forbidden-marker' >> "$temporary/present.txt"
    grep_state "$temporary/present.txt" 'forbidden-marker'
    [ "$GREP_RESULT" -eq 0 ] || fail "self-test could not classify forbidden marker"

    is_allowed_changed_path \
        'src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java' \
        || fail "self-test rejected an exact allowed E1-A path"
    is_allowed_changed_path \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentAdmissionSource.java' \
        || fail "self-test rejected the exact sealed source path"
    is_allowed_changed_path \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1BoundPlayerSkillAttachmentAdmissionSource.java' \
        || fail "self-test rejected the exact bound source path"
    is_allowed_changed_path \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java' \
        || fail "self-test rejected the exact B2-B service path"
    is_allowed_changed_path \
        'src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java' \
        || fail "self-test rejected the exact B2-B API Gate path"
    for approved in \
            'build.gradle' \
            '.github/workflows/build.yml' \
            'scripts/verify-p4-e3-configuration.sh' \
            'src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3ProbeMain.java' \
            'src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java' \
            'src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java' \
            'src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3ApiGateTest.java'; do
        is_allowed_changed_path "$approved" \
            || fail "self-test rejected an exact approved P4-E3 path: $approved"
    done
    if is_allowed_changed_path 'build.gradle.extra'; then
        fail "self-test accepted a prefix-near Gradle path"
    fi
    if is_allowed_changed_path \
            'src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3Unexpected.java'; then
        fail "self-test accepted a prefix-near P4-E3 probe path"
    fi
    if is_allowed_changed_path 'src/main/resources/gramarye/e1.json'; then
        fail "self-test accepted a forbidden production resource path"
    fi
    if is_allowed_changed_path \
            'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1BoundPlayerSkillAttachmentAdmissionSourceExtra.java'; then
        fail "self-test accepted a prefix-near production path"
    fi
    if is_allowed_changed_path \
            'src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditServiceExtra.java'; then
        fail "self-test accepted a prefix-near B2-B service path"
    fi
    if is_allowed_changed_path \
            'src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTestExtra.java'; then
        fail "self-test accepted a prefix-near B2-B API Gate path"
    fi
    if is_allowed_changed_path \
            'src/main/java/com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentAdmissionSourceExtra.java'; then
        fail "self-test accepted a prefix-near sealed source path"
    fi
    if is_allowed_changed_path 'logs/latest.log'; then
        fail "self-test accepted a repository-root runtime log"
    fi
    if git -C "$temporary" diff --name-only --no-renames -z HEAD -- \
            > /dev/null 2>&1; then
        fail "self-test confused a Git tool error with an empty changed set"
    fi

    rm -rf -- "$temporary"
    trap - RETURN
}

self_regression
verify_changed_paths
[ -x "$0" ] || fail "portable verifier is not executable"

EXPECTED_STORE_TYPE_COUNT=26
ACTUAL_STORE_TYPE_COUNT=$(find "$STORE_ROOT" -maxdepth 1 -name 'P4E1*.java' -print \
        | wc -l | tr -d ' ')
[ "$ACTUAL_STORE_TYPE_COUNT" -eq "$EXPECTED_STORE_TYPE_COUNT" ] \
    || fail "expected $EXPECTED_STORE_TYPE_COUNT exact P4-E1 production types, found $ACTUAL_STORE_TYPE_COUNT"

for name in \
        P4E1AuditBudget \
        P4E1AuditedCapture \
        P4E1AuditCounter \
        P4E1AuditStage \
        P4E1CompressedCapacityRejected \
        P4E1FileMetadata \
        P4E1FileSystemAccess \
        P4E1HeapFloorObservation \
        P4E1HeapFloorStatus \
        P4E1IntegratedSnapshotTraversal \
        P4E1BoundPlayerSkillAttachmentAdmissionSource \
        P4E1CompleteRootHandoff \
        P4E1FinalFreshness \
        P4E1GlobalSourceCapture \
        P4E1GroupedStoreAudit \
        P4E1PendingJournalObservation \
        P4E1PlayerDataDirectorySnapshot \
        P4E1PlayerDataFileReader \
        P4E1PlayerDataNbtScanner \
        P4E1PlayerDataSourceSelector \
        P4E1RawClaimBuffer \
        P4E1RootSourceFamily \
        P4E1SourceAdmissionPreflight \
        P4E1SourceFailure \
        P4E1SourceInventory \
        P4E1StoreHistoryObservation; do
    file="$STORE_ROOT/$name.java"
    [ -f "$file" ] || fail "required production source missing: $file"
    [ ! -L "$file" ] || fail "required production source is a symlink: $file"
done

for file in "$AUDIT_RESULT" "$AUDIT_SERVICE"; do
    [ -f "$file" ] || fail "required B2-B production source missing: $file"
    [ ! -L "$file" ] || fail "required B2-B production source is a symlink: $file"
done

for file in "$CEILINGS" "$BUDGET" "$PREFLIGHT" \
        "$ADMISSION_SOURCE" "$BOUND_ADMISSION_SOURCE" "$PLAYER_SERVICE" \
        "$PLAYER_ROOT/PlayerSkillAttachmentAdmission.java" \
        "$PLAYER_ROOT/PlayerSkillAttachmentSourceObservation.java" \
        "$HEAP_CHILD" "$HEAP_PROBE" "$HEAP_UNIT" "$PREFLIGHT_UNIT" "$PHASE_TYPES" \
        "$A1_API_GATE" "$A1_VISIBILITY_GATE" "$A1_BRIDGE_TEST" "$A1_TEST_SUPPORT"; do
    [ -f "$file" ] || fail "required reviewed file missing: $file"
    [ ! -L "$file" ] || fail "required reviewed file is a symlink: $file"
done

for file in "$B1_API_GATE" "$B1_CORE_TEST"; do
    [ -f "$file" ] || fail "required B1 reviewed file missing: $file"
    [ ! -L "$file" ] || fail "required B1 reviewed file is a symlink: $file"
done

for file in "$B2A_API_GATE" "$B2A_STORE_AUDIT_TEST" "$HISTORY_OBSERVATION_TEST"; do
    [ -f "$file" ] || fail "required B2-A reviewed file missing: $file"
    [ ! -L "$file" ] || fail "required B2-A reviewed file is a symlink: $file"
done

for file in "$B2B_API_GATE" "$B2B_FINAL_FRESHNESS_TEST" \
        "$B2B_INDEX_LIFECYCLE_TEST" "$B2B_COMPLETE_HANDOFF_TEST" "$B_API_GATE"; do
    [ -f "$file" ] || fail "required B2-B reviewed test missing: $file"
    [ ! -L "$file" ] || fail "required B2-B reviewed test is a symlink: $file"
done

while IFS='|' read -r name value; do
    require_exact_count "$CEILINGS" "$name = $value" 1
    require_exact_count "$BUDGET" "$name" 1
done <<'MAXIMA'
MAX_PLAYERDATA_DIRECTORY_ENTRIES|4_096
MAX_PLAYERDATA_RELEVANT_RECORDS|2_048
MAX_PLAYERDATA_COMPRESSED_BYTES_PER_FILE|33_559_514
MAX_PLAYERDATA_DECOMPRESSED_BYTES_PER_FILE|268_435_456
MAX_PLAYERDATA_CONTAINER_DEPTH_PER_FILE|512
MAX_PLAYERDATA_COMPOUND_CONTAINERS_PER_FILE|1_024
MAX_PLAYERDATA_COMPOUND_FIELD_ENTRIES_PER_FILE|65_537
MAX_PLAYERDATA_LIST_ELEMENTS_PER_FILE|65_536
MAX_PLAYERDATA_BYTE_ARRAY_ELEMENTS_PER_FILE|268_435_384
MAX_PLAYERDATA_INT_ARRAY_ELEMENTS_PER_FILE|65_536
MAX_PLAYERDATA_LONG_ARRAY_ELEMENTS_PER_FILE|65_536
MAX_PLAYERDATA_MODIFIED_UTF8_BYTES_PER_FILE|67_107_692
MAX_PLAYERDATA_SCALAR_TAGS_PER_FILE|65_537
MAX_PLAYERDATA_COMPRESSED_BYTES_TOTAL|268_440_533
MAX_PLAYERDATA_DECOMPRESSED_BYTES_TOTAL|536_870_912
MAX_PLAYERDATA_COMPOUND_CONTAINERS_TOTAL|131_072
MAX_PLAYERDATA_COMPOUND_FIELD_ENTRIES_TOTAL|524_288
MAX_PLAYERDATA_LIST_ELEMENTS_TOTAL|131_072
MAX_PLAYERDATA_BYTE_ARRAY_ELEMENTS_TOTAL|456_524_705
MAX_PLAYERDATA_INT_ARRAY_ELEMENTS_TOTAL|131_072
MAX_PLAYERDATA_LONG_ARRAY_ELEMENTS_TOTAL|131_072
MAX_PLAYERDATA_MODIFIED_UTF8_BYTES_TOTAL|75_497_472
MAX_PLAYERDATA_SCALAR_TAGS_TOTAL|458_752
MAX_PLAYERDATA_ATTACHMENT_ADMISSIONS|1_024
MAXIMA

require_exact_count "$CEILINGS" \
    'MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES = 1_610_612_736L' 1
require_exact_count "$HEAP_OBSERVATION" \
    'MagicSafetyCeilings.MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES' 1
require_fixed "$BUDGET" 'case RAW_ROOT_CLAIMS -> MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM'
require_fixed "$STORE_ROOT/P4E1AuditCounter.java" 'enum P4E1AuditCounter'
require_fixed "$PREFLIGHT" 'P4E1HeapFloorObservation.observe()'
require_fixed "$PREFLIGHT" 'case HEAP_FLOOR_NOT_MET -> new Incomplete('
require_fixed "$PREFLIGHT" 'case HEAP_FLOOR_UNVERIFIABLE -> new Incomplete('
require_fixed "$HEAP_OBSERVATION" 'getVMOption("MaxHeapSize")'
require_exact_count "$HEAP_OBSERVATION" 'Runtime.getRuntime().maxMemory()' 1
require_fixed "$HEAP_CHILD" 'ALIGNED_TO_FLOOR_CONTROL'
require_fixed "$HEAP_CHILD" 'BELOW_FLOOR_CONTROL'
require_fixed "$HEAP_CHILD" '"-XX:+UseG1GC"'
require_fixed "$HEAP_CHILD" '"-XX:+UseParallelGC"'
require_fixed "$HEAP_CHILD" '"-XX:+UseSerialGC"'
require_fixed "$HEAP_CHILD" '"-XX:+UseZGC"'
require_fixed "$HEAP_CHILD" '"1536m"'
require_fixed "$HEAP_CHILD" '"1535m"'
require_fixed "$HEAP_CHILD" '"1024m"'
require_fixed "$HEAP_CHILD" 'Duration.ofSeconds(45)'
require_fixed "$HEAP_CHILD" 'redirectOutput(ProcessBuilder.Redirect.DISCARD)'
require_fixed "$HEAP_CHILD" 'readNBytes(MAX_RESULT_BYTES + 1)'
require_fixed "$HEAP_UNIT" 'FLOOR - 1L'
require_fixed "$HEAP_UNIT" 'FLOOR + 1L'
require_fixed "$HEAP_UNIT" 'Long.MAX_VALUE'
require_fixed "$HEAP_PROBE" 'effective_max_heap_size_bytes='
require_fixed "$HEAP_PROBE" 'runtime_max_memory_bytes='
require_fixed "$HEAP_PROBE" 'heap_memory_usage_max_bytes='
require_fixed "$HEAP_PROBE" 'floor_bytes='
require_fixed "$HEAP_PROBE" 'classification='
require_fixed "$HEAP_PROBE" 'exception_class='
require_fixed "$HEAP_PROBE" 'source_work_calls='
require_fixed "$HEAP_PROBE" 'observedBudgetedSourceWork(preflight)'
reject_fixed "$HEAP_PROBE" 'sourceWorkCalls = 0'
require_fixed "$HEAP_PROBE" 'MAX_RESULT_BYTES = 4_096'
reject_fixed "$HEAP_PROBE" 'System.out'
for source_work in \
        DIRECTORY_ENUMERATION \
        FILE_ATTRIBUTE_READS \
        FILE_OPENS \
        GZIP_PARSER_CALLS \
        NBT_SCANNER_CALLS \
        INTEGRATED_TRAVERSAL_CALLS \
        P4C_ADMISSION_CALLS \
        ONLINE_ATTACHMENT_READS \
        JOURNAL_CALLS \
        ROOT_CLAIMS \
        STORE_AUDIT_CALLS \
        RECLAIM_CALLS; do
    require_exact_count "$PREFLIGHT_UNIT" "$source_work" 1
done
require_exact_count "$PREFLIGHT_UNIT" 'sourceWork.assertAll(0)' 1
require_fixed "$PREFLIGHT_UNIT" 'evaluateThenAttemptSourceAdmission(observation, sourceWork)'
require_exact_count "$PREFLIGHT" 'new P4E1AuditBudget(new QualifiedPermit())' 1
require_fixed "$PREFLIGHT" 'private QualifiedPermit()'
require_fixed "$BUDGET" \
    'P4E1AuditBudget(P4E1SourceAdmissionPreflight.QualifiedPermit permit)'
reject_fixed "$BUDGET" 'static P4E1AuditBudget create('
for production_source in $(find "$MAIN_JAVA" -type f -name '*.java' -print); do
    [ "$production_source" = "$PREFLIGHT" ] && continue
    [ "$production_source" = "$STORE_ROOT/P4E1GlobalSourceCapture.java" ] && continue
    reject_fixed "$production_source" 'P4E1SourceAdmissionPreflight.evaluate('
done
require_fixed "$E0_LEDGER" 'P4-E0-B.2 effective-MaxHeapSize authority correction'
require_fixed "$E0_LEDGER" 'P4-E0-B.2 authority patch          = COMPLETE'
require_fixed "$STORE_ROOT/P4E1PlayerDataDirectorySnapshot.java" 'LevelResource.PLAYER_DATA_DIR'
require_fixed "$STORE_ROOT/P4E1PlayerDataDirectorySnapshot.java" 'RecordSelection selectRecords('
require_fixed "$STORE_ROOT/P4E1PlayerDataDirectorySnapshot.java" \
    'excludedIntegratedOwner.filter(record.playerId()::equals)'
require_fixed "$STORE_ROOT/P4E1PlayerDataNbtScanner.java" 'new ArrayDeque<>()'
require_fixed "$STORE_ROOT/P4E1PlayerDataNbtScanner.java" 'CURRENT_DATA_VERSION = 3_955'
reject_fixed "$STORE_ROOT/P4E1PlayerDataNbtScanner.java" 'attachmentOuterWrongType'
require_fixed "$STORE_ROOT/StrictSingleMemberGzipInput.java" \
    'GzipCompressorInputStream(bufferedCompressed, false)'
require_exact_count "$STORE_ROOT/StrictSingleMemberGzipInput.java" \
    'new GzipCompressorInputStream(' 1

require_fixed "$ADMISSION_SOURCE" \
    'public sealed abstract class PlayerSkillAttachmentAdmissionSource<I, P>'
require_fixed "$ADMISSION_SOURCE" \
    'extends PlayerSkillAttachmentService.OpaqueAdmissionSource<I, P>'
require_fixed "$ADMISSION_SOURCE" \
    'permits P4E1BoundPlayerSkillAttachmentAdmissionSource'
reject_fixed "$ADMISSION_SOURCE" 'Tag'
reject_fixed "$ADMISSION_SOURCE" 'public PlayerSkillAttachmentAdmissionSource('
reject_fixed "$ADMISSION_SOURCE" 'protected PlayerSkillAttachmentAdmissionSource('
require_fixed "$BOUND_ADMISSION_SOURCE" \
    'final class P4E1BoundPlayerSkillAttachmentAdmissionSource'
require_fixed "$BOUND_ADMISSION_SOURCE" \
    'extends PlayerSkillAttachmentAdmissionSource<Tag, HolderLookup.Provider>'
require_exact_count "$BOUND_ADMISSION_SOURCE" \
    'new P4E1BoundPlayerSkillAttachmentAdmissionSource(' 2
require_exact_count "$BOUND_ADMISSION_SOURCE" 'service.admitForRootAudit(' 2
require_exact_count "$BOUND_ADMISSION_SOURCE" 'admitDiskObservation(' 1
require_exact_count "$BOUND_ADMISSION_SOURCE" 'admitIntegratedObservation(' 1
reject_fixed "$BOUND_ADMISSION_SOURCE" 'return new P4E1BoundPlayerSkillAttachmentAdmissionSource('
for production_source in $(find "$MAIN_JAVA" -type f -name '*.java' -print); do
    [ "$production_source" = "$BOUND_ADMISSION_SOURCE" ] && continue
    reject_fixed "$production_source" 'new P4E1BoundPlayerSkillAttachmentAdmissionSource('
    reject_fixed "$production_source" 'service.admitForRootAudit('
done
require_fixed "$PLAYER_SERVICE" \
    'public abstract static class OpaqueAdmissionSource<I, P>'
require_fixed "$PLAYER_SERVICE" \
    'public RootAuditAdmissionResult admitForRootAudit('
require_fixed "$PLAYER_SERVICE" \
    'PlayerSkillAttachmentAdmissionSource<?, ?> source)'
require_exact_count "$PLAYER_SERVICE" 'admitForRootAudit(' 1
reject_fixed "$PLAYER_SERVICE" 'admitForRootAudit(OpaqueAdmissionSource'
reject_fixed "$PLAYER_SERVICE" 'admitForRootAudit(Object'
reject_fixed "$PLAYER_SERVICE" 'admitForRootAudit(Tag'
require_fixed "$PLAYER_SERVICE" 'public int rootCount(RootAuditAdmitted admitted)'
require_fixed "$PLAYER_SERVICE" \
    'public void drainRootProjection(RootAuditAdmitted admitted, RootAuditSink sink)'
require_fixed "$PLAYER_SERVICE" \
    'public void discardRootProjection(RootAuditAdmitted admitted)'
require_fixed "$PLAYER_SERVICE" 'opaque.inputIdentity = null'
require_fixed "$PLAYER_SERVICE" 'opaque.measurementInputIdentity = null'
require_fixed "$PLAYER_SERVICE" 'opaque.providerIdentity = null'
require_fixed "$PLAYER_SERVICE" 'opaque.providerWitnessIdentity = null'
require_fixed "$PLAYER_SERVICE" 'opaque.exactEncodedWidth = CLEARED_ENCODED_WIDTH'
require_fixed "$PLAYER_SERVICE" 'admitted.consumeAndClear()'
require_fixed "$PLAYER_SERVICE" 'sink::latest'
require_fixed "$PLAYER_SERVICE" 'sink.equipped(entry.slot(), entry.reference())'
require_exact_count "$PLAYER_SERVICE" 'rootAuditAdmission.admit(' 1
require_exact_count "$PLAYER_ROOT/PlayerSkillAttachmentSerializer.java" 'input.copy()' 1

for source in "$ADMISSION_SOURCE" "$BOUND_ADMISSION_SOURCE"; do
    reject_fixed "$source" '.copy('
    reject_fixed "$source" 'NbtIo.'
    reject_fixed "$source" 'writeAnyTag'
    reject_fixed "$source" 'AttachmentTagSize.measure'
    reject_fixed "$source" 'List<SkillReference>'
    reject_fixed "$source" 'Codec'
    reject_fixed "$source" 'Serializable'
    reject_fixed "$source" 'static {'
done
for token in \
        '.copy()' \
        'NbtIo.' \
        'writeAnyTag' \
        'AttachmentTagSize.measure' \
        'SkillRetentionRootAuditService' \
        'PendingAttachmentJournal' \
        'CompleteCapture' \
        '.reclaim(' \
        'static {'; do
    reject_fixed "$PLAYER_SERVICE" "$token"
done
require_exact_count "$PLAYER_SERVICE" '.setData(' 1
require_fixed "$A1_API_GATE" 'final class P4E1A1ApiGateTest'
require_fixed "$A1_VISIBILITY_GATE" 'final class P4E1A1VisibilityCompileTest'
require_fixed "$A1_BRIDGE_TEST" 'final class P4E1RootAuditBridgeTest'
require_fixed "$A1_TEST_SUPPORT" 'PlayerSkillAttachmentSerializer.INSTANCE.read('
require_fixed "$B2B_API_GATE" 'final class P4E1B2BApiGateTest'
require_fixed "$B_API_GATE" 'final class P4E1BApiGateTest'

for source in "$ADMISSION_SOURCE" "$BOUND_ADMISSION_SOURCE" "$PLAYER_SERVICE"; do
    reject_fixed "$source" '@SuppressWarnings'
    reject_fixed "$source" 'unchecked'
    reject_fixed "$source" 'java.lang.reflect'
    reject_fixed "$source" 'sun.misc.Unsafe'
    reject_fixed "$source" 'setAccessible('
done

E1_SOURCES="$(find "$STORE_ROOT" -maxdepth 1 -type f -name 'P4E1*.java' -print)
$ADMISSION_SOURCE
$AUDIT_RESULT
$AUDIT_SERVICE"
[ -n "$E1_SOURCES" ] || fail "P4-E1 source allowlist is empty"

for token in \
        'java.util.zip.GZIPInputStream' \
        'Files.readAllBytes' \
        'NbtAccounter.unlimitedHeap' \
        'java.lang.reflect' \
        'setAccessible(' \
        'sun.misc.Unsafe' \
        'Thread.sleep(' \
        'System.gc(' \
        'Executors.' \
        'ExecutorService' \
        'java.util.concurrent.Future' \
        'new Thread(' \
        'RuntimeMXBean' \
        'getInputArguments(' \
        'PrintFlagsFinal' \
        'jcmd' \
        '.setData(' \
        'NbtIo.write' \
        'Files.write' \
        'FileOutputStream' \
        'Files.delete' \
        'Files.move' \
        'Files.newOutputStream' \
        'PlayerDataStorage' \
        '.saveAll(' \
        '.saveWithoutId(' \
        'PendingSkillSubmissionJournal' \
        'net.minecraft.client' \
        'SkillRetentionRootSnapshot' \
        '.reclaim(' \
        'ServerStartingEvent' \
        'ServerStartedEvent' \
        'PlayerLoggedInEvent' \
        'PlayerLoggedOutEvent' \
        'PlayerEvent.Clone' \
        'CompletableFuture' \
        'parallelStream(' \
        'java.lang.ref.Cleaner' \
        'java.util.WeakHashMap' \
        'finalize(' \
        'CustomPacketPayload' \
        'PayloadRegistrar'; do
    for source in $E1_SOURCES \
            "$PLAYER_ROOT/PlayerSkillAttachmentAdmission.java" \
            "$PLAYER_ROOT/PlayerSkillAttachmentSourceObservation.java"; do
        reject_fixed "$source" "$token"
    done
done

for token in \
        'P4E1PlayerDataDirectorySnapshot' \
        'P4E1FileSystemAccess' \
        'P4E1PlayerDataFileReader' \
        'P4E1PlayerDataNbtScanner' \
        'P4E1IntegratedSnapshotTraversal' \
        'PlayerSkillAttachmentAdmission' \
        'PlayerSkillAttachmentSourceObservation' \
        'PendingSkillSubmissionJournal' \
        'SkillRetentionRootSnapshot' \
        'SkillDefinitionStore' \
        '.reclaim('; do
    reject_fixed "$PREFLIGHT" "$token"
done

for source in $E1_SOURCES \
        "$PLAYER_ROOT/PlayerSkillAttachmentAdmission.java" \
        "$PLAYER_ROOT/PlayerSkillAttachmentSourceObservation.java"; do
    reject_fixed "$source" 'catch (Error'
    reject_fixed "$source" 'catch (OutOfMemoryError'
    reject_fixed "$source" 'catch (Throwable'
done

reject_fixed "$STORE_ROOT/P4E1PlayerDataNbtScanner.java" 'NbtIo.'
reject_fixed "$STORE_ROOT/P4E1IntegratedSnapshotTraversal.java" '.copy('
reject_fixed "$STORE_ROOT/P4E1IntegratedSnapshotTraversal.java" 'NbtIo.'
reject_fixed "$STORE_ROOT/P4E1IntegratedSnapshotTraversal.java" 'toString('

reject_fixed "$REPOSITORY_ROOT/build.gradle" 'p4E1'
reject_fixed "$REPOSITORY_ROOT/build.gradle" 'P4E1'
reject_fixed "$REPOSITORY_ROOT/.github/workflows/build.yml" 'p4-e1'
reject_fixed "$REPOSITORY_ROOT/.github/workflows/build.yml" 'P4-E1'

for forbidden_name in \
        P4E1GlobalInventory.java \
        P4E1RootIndex.java \
        P4E1Reconciliation.java; do
    [ ! -e "$STORE_ROOT/$forbidden_name" ] \
        || fail "later-phase production source exists: $forbidden_name"
done

verify_a1_jar_isolation() {
    local listing jar_path jar_count
    listing=$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e1-jar.XXXXXX")
    trap 'rm -f -- "$listing"' RETURN
    jar_count=0
    for jar_path in "$REPOSITORY_ROOT"/build/libs/gramarye-*.jar; do
        [ -f "$jar_path" ] || continue
        [ ! -L "$jar_path" ] || fail "production JAR is a symlink: $jar_path"
        jar_count=$((jar_count + 1))
        jar tf "$jar_path" > "$listing" \
            || fail "jar tool failed while inspecting $jar_path"
        for class_path in \
                'com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentAdmissionSource.class' \
                'com/yo1no/gramarye/magic/definition/store/P4E1BoundPlayerSkillAttachmentAdmissionSource.class' \
                'com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService$OpaqueAdmissionSource.class' \
                'com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService$RootAuditAdmissionResult.class' \
                'com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService$RootAuditAdmitted.class' \
                'com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService$RootAuditRejected.class' \
                'com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService$RootAuditOversize.class' \
                'com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService$RootAuditSink.class'; do
            require_fixed "$listing" "$class_path"
        done
        for class_path in \
                'com/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture.class' \
                'com/yo1no/gramarye/magic/definition/store/P4E1AuditedCapture.class' \
                'com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.class' \
                'com/yo1no/gramarye/magic/definition/store/P4E1FinalFreshness.class' \
                'com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit.class' \
                'com/yo1no/gramarye/magic/definition/store/P4E1PendingJournalObservation.class' \
                'com/yo1no/gramarye/magic/definition/store/P4E1RawClaimBuffer.class' \
                'com/yo1no/gramarye/magic/definition/store/P4E1RootSourceFamily.class' \
                'com/yo1no/gramarye/magic/definition/store/P4E1SourceInventory.class' \
                'com/yo1no/gramarye/magic/definition/store/P4E1StoreHistoryObservation.class' \
                'com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditResult.class' \
                'com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditResult$AuditSummary.class' \
                'com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditResult$Complete.class' \
                'com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditResult$Incomplete.class' \
                'com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditResult$OverLimit.class' \
                'com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditResult$ReconciliationRequired.class' \
                'com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.class' \
                'com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService$IndexSlot.class' \
                'com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService$IndexState.class' \
                'com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService$IndexedBacking.class' \
                'com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService$IndexedSource.class' \
                'com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService$PermitCell.class' \
                'com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService$LeaseCell.class' \
                'com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService$OnlineRootAuditHandle.class'; do
            require_fixed "$listing" "$class_path"
        done
        for forbidden_class in \
                P4E1A1ApiGateTest \
                P4E1A1VisibilityCompileTest \
                P4E1RootAuditBridgeTest \
                P4E1B1ApiGateTest \
                P4E1B1CoreTest \
                P4E1B2AApiGateTest \
                P4E1B2AStoreAuditTest \
                P4E1B2BApiGateTest \
                P4E1B2BCompleteHandoffTest \
                P4E1B2BFinalFreshnessTest \
                P4E1B2BIndexLifecycleTest \
                P4E1BApiGateTest \
                P4E1StoreHistoryObservationTest \
                PlayerSkillAttachmentServiceTestSupport; do
            reject_fixed "$listing" "$forbidden_class"
        done
    done
    [ "$jar_count" -eq 1 ] \
        || fail "expected one production JAR for A.1 isolation, found $jar_count"
    rm -f -- "$listing"
    trap - RETURN
}

verify_a1_jar_isolation

require_fixed "$PHASE_TYPES" '"P4E1CompressedCapacityRejected"'
require_fixed "$PHASE_TYPES" '"P4E1SourceAdmissionPreflight"'
require_fixed "$PHASE_TYPES" '"P4E1AuditedCapture"'
require_fixed "$PHASE_TYPES" '"P4E1CompleteRootHandoff"'
require_fixed "$PHASE_TYPES" '"P4E1FinalFreshness"'
require_fixed "$PHASE_TYPES" '"P4E1GroupedStoreAudit"'
require_fixed "$PHASE_TYPES" '"P4E1StoreHistoryObservation"'
require_fixed "$PHASE_TYPES" '"SkillRetentionRootAuditResult"'
require_fixed "$PHASE_TYPES" '"SkillRetentionRootAuditService"'

require_fixed "$AUDIT_SERVICE" 'final class SkillRetentionRootAuditService'
require_fixed "$AUDIT_SERVICE" \
    'IdentityHashMap<MinecraftServer, IndexSlot> index = new IdentityHashMap<>()'
require_exact_count "$AUDIT_SERVICE" \
    'SkillRetentionRootAuditResult audit(MinecraftServer server)' 1
require_exact_count "$AUDIT_SERVICE" 'P4E1CompleteRootHandoff consumeComplete(' 1
require_exact_count "$AUDIT_SERVICE" \
    'InvalidationResult invalidateForReconciliation(MinecraftServer server)' 1
require_exact_count "$AUDIT_SERVICE" \
    'boolean isReconciliationInvalidationCurrent(' 1
require_exact_count "$AUDIT_SERVICE" 'void removeServer(MinecraftServer server)' 1
require_exact_count "$AUDIT_SERVICE" 'index.remove(server)' 1
require_exact_count "$AUDIT_SERVICE" 'handoff.forceInvalidate(this)' 1
reject_fixed "$AUDIT_SERVICE" 'WeakHashMap'
require_fixed "$AUDIT_RESULT" 'public sealed abstract class SkillRetentionRootAuditResult'
require_fixed "$AUDIT_RESULT" 'public static final class Complete'
require_fixed "$AUDIT_RESULT" 'private Complete('
require_fixed "$AUDIT_RESULT" 'public record AuditSummary('
require_fixed "$FINAL_FRESHNESS" 'final class P4E1FinalFreshness'
require_fixed "$COMPLETE_HANDOFF" \
    'final class P4E1CompleteRootHandoff implements Iterable<SkillReference>, AutoCloseable'

for source in "$AUDIT_SERVICE" "$AUDIT_RESULT" "$FINAL_FRESHNESS" "$COMPLETE_HANDOFF"; do
    for forbidden in \
            '.commit(' \
            '.pin(' \
            '.snapshot(' \
            '.append(' \
            '.reclaim(' \
            '.setData(' \
            '.setDirty(' \
            'SkillRetentionRootSnapshot.fromCompleteRoots' \
            'NbtIo.write' \
            'DataFixer' \
            'Files.write' \
            'Files.move' \
            'Files.delete' \
            'Files.newOutputStream' \
            'getChunk(' \
            'setChunkForced' \
            'List<SkillReference>' \
            'ArrayList<SkillReference>' \
            'SkillReference[]'; do
        reject_fixed "$source" "$forbidden"
    done
done
for production_source in $(find "$MAIN_JAVA" -type f -name '*.java' -print); do
    if [ "$production_source" = "$STORE_ROOT/SkillDefinitionStoreService.java" ]; then
        require_exact_count "$production_source" 'new SkillRetentionRootAuditService(' 1
    else
        reject_fixed "$production_source" 'new SkillRetentionRootAuditService('
    fi
done

for source in "$AUDITED_CAPTURE" "$GROUPED_STORE_AUDIT" "$HISTORY_OBSERVATION"; do
    reject_fixed "$source" 'public class '
    reject_fixed "$source" 'public final class '
    reject_fixed "$source" 'public interface '
    reject_fixed "$source" 'public sealed interface '
done
require_fixed "$HISTORY_OBSERVATION" 'sealed interface P4E1StoreHistoryObservation'
require_fixed "$HISTORY_OBSERVATION" 'boolean ownerMatches(SkillOwnerId expectedOwner)'
require_fixed "$HISTORY_OBSERVATION" 'boolean contains(SkillReference reference)'
require_fixed "$HISTORY_OBSERVATION" 'void discard()'
reject_fixed "$HISTORY_OBSERVATION" 'StoredSkillHistory history()'
reject_fixed "$HISTORY_OBSERVATION" 'SkillOwnerId owner()'
reject_fixed "$HISTORY_OBSERVATION" 'SkillReference latest('
require_exact_count "$STORE" 'observeExactHistoryForRootAudit(' 1
require_exact_count "$STORE" 'this::observeExactHistoryForRootAudit' 1
require_exact_count "$GLOBAL_CAPTURE" '.observeExactHistoryForRootAudit(' 1
require_exact_count "$GROUPED_STORE_AUDIT" '.observeExactHistory(' 1
require_fixed "$GROUPED_STORE_AUDIT" 'new LinkedHashMap<SkillId, ObservationSlot>()'
require_fixed "$GROUPED_STORE_AUDIT" 'staleObservedAtLeast'
require_fixed "$GROUPED_STORE_AUDIT" 'STORE_OWNER_MISMATCH'
require_fixed "$GROUPED_STORE_AUDIT" 'STORE_REFERENCE_MISSING'
require_fixed "$GROUPED_STORE_AUDIT" 'JOURNAL_TARGET_INVALID'
reject_fixed "$AUDITED_CAPTURE" 'P4E1StoreHistoryObservation'
reject_fixed "$AUDITED_CAPTURE" 'StoredSkillHistory'
for source in "$AUDITED_CAPTURE" "$GROUPED_STORE_AUDIT"; do
    for forbidden in \
            'SkillRetentionRootAuditResult' \
            'P4E1RootIndex' \
            'P4E1RootHandoff' \
            'P4E1Complete' \
            'SkillRetentionRootSnapshot' \
            'List<SkillReference>' \
            'ArrayList<SkillReference>' \
            '.pin(' \
            '.snapshot(' \
            '.reclaim(' \
            '.setDirty(' \
            '.setData(' \
            'Files.write' \
            'Files.move' \
            'Files.delete' \
            'Files.newOutputStream' \
            'ServerStartingEvent' \
            'ServerStoppedEvent' \
            'Executor' \
            'Future' \
            'new Thread(' \
            'parallelStream(' \
            'CustomPacketPayload' \
            'exceptionClassName.length() > 160' \
            '@SuppressWarnings' \
            'catch (Error' \
            'catch (OutOfMemoryError' \
            'catch (Throwable'; do
        reject_fixed "$source" "$forbidden"
    done
done

echo "P4-E1 configuration verification passed"
