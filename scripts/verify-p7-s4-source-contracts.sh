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

# P9-S3-DC1 adds one direct regression path without rewriting the immutable
# S2-to-S3 exact-85 projection above.
is_p9_s3_dc1_path() {
    case "$1" in
        src/test/java/com/yo1no/gramarye/P9S3DirectConsumerContractTest.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

# P9-S4 adds the exact six dirty paths that were not already admitted by the
# immutable S3/DC1 projections above. Keep this as a separate closed extension.
is_p9_s4_path() {
    case "$1" in
        src/main/java/com/yo1no/gramarye/P8AppliedFactHandoff.java | \
        src/main/java/com/yo1no/gramarye/P8ServerPresentationService.java | \
        src/test/java/com/yo1no/gramarye/P8S4ServerTransportTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionDebitTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionPreDebitTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectRequestTest.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

# P9-S4-WC1 adds three tracked warning-attribution surfaces outside the
# original exact-27 candidate. The two authorized B-to-F intersections remain
# admitted by the immutable P9-S4 extension above.
is_p9_s4_wc1_path() {
    case "$1" in
        .github/workflows/build.yml | \
        scripts/collect-p9-s3-rd1-unit-test-diagnostics.sh | \
        scripts/verify-p9-s4-warning-attribution.py)
            return 0 ;;
        *) return 1 ;;
    esac
}

# P9-S5-R2 adds one closed candidate projection for the normal-player
# provisioning/input/composition slice. Earlier phase projections intentionally
# remain unchanged; overlap here records the exact final S5 candidate rather
# than admitting a directory, package, or filename prefix.
is_p9_s5_path() {
    case "$1" in
        AGENTS.md | \
        build.gradle | \
        scripts/verify-p4-a3-b-configuration.sh | \
        scripts/verify-p4-b2-b-configuration.sh | \
        scripts/verify-p4-c2-a-configuration.sh | \
        scripts/verify-p4-c2-b-configuration.sh | \
        scripts/verify-p4-d1-configuration.sh | \
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
        src/main/java/com/yo1no/gramarye/P8ClientPayloadDispatchFactory.java | \
        src/main/java/com/yo1no/gramarye/P8ClientPayloadDispatchPort.java | \
        src/main/java/com/yo1no/gramarye/P8ClientPayloadHandlers.java | \
        src/main/java/com/yo1no/gramarye/P8ClientPresentationLifecycle.java | \
        src/main/java/com/yo1no/gramarye/P8ClientPresentationState.java | \
        src/main/java/com/yo1no/gramarye/P9StarterCommand.java | \
        src/main/java/com/yo1no/gramarye/P9StarterSkillContent.java | \
        src/main/java/com/yo1no/gramarye/P9StarterSkillIdentityV0.java | \
        src/main/java/com/yo1no/gramarye/P9S5ProvisioningGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ClientLifecycleEvents.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P9ClientCastInput.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P9ClientKeyMappings.java | \
        src/main/resources/assets/gramarye/lang/en_us.json | \
        src/main/resources/assets/gramarye/lang/zh_tw.json | \
        src/p8S5ClientHarness/java/com/yo1no/gramarye/P8S5ClientRuntimeHarness.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/P9S5ClientRuntimeHarness.java | \
        src/test/java/com/yo1no/gramarye/P6RuntimeExecutionCapabilityTest.java | \
        src/test/java/com/yo1no/gramarye/P7GameTestInventory.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPayloadHandlersTest.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPlayConnection.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPlayEpochAcceptanceTest.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPlayEpochTest.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPresentationExecutionTest.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPresentationLifecycleTest.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPresentationStateConcurrencyTest.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPresentationStateTest.java | \
        src/test/java/com/yo1no/gramarye/P8S2BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P8S4PayloadBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P9S1BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P9S3DirectConsumerContractTest.java | \
        src/test/java/com/yo1no/gramarye/P9S5BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C1ApiGateTest.java | \
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
        src/test/java/com/yo1no/gramarye/magic/network/P7ClientMirrorTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7PayloadRegistrarTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7S2BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7S2DedicatedRegistrationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7S3BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P9ClientCastInputTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ManaBoundaryTest.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

reject_game_test_worker_surface() {
    printf 'Production-packaged GameTest raw worker/task/process surface: %s\n' \
        "$1" >&2
    return 1
}

reject_game_test_liveness_receiver() {
    printf 'Production-packaged GameTest unverified liveness receiver: %s\n' \
        "$1" >&2
    return 1
}

# The sole production-packaged GameTest liveness use is the P9-S3 age-boundary
# assertion. Bind the exception to its enclosing method, local factory call, and
# the factory's concrete Breeze return type. Any extra, moved, shadowed, or
# otherwise unresolved receiver remains fail-closed.
verify_p9_s3_entity_liveness() {
    local source="$1"
    local logical_source="$2"

    if ! LC_ALL=C awk '
        function occurrences(text, pattern, count) {
            count = 0
            while (match(text, pattern)) {
                count++
                text = substr(text, RSTART + RLENGTH)
            }
            return count
        }
        function append_source_token(token) {
            if (token != "") {
                source_tokens[++source_token_count] = token
                source_token_line[source_token_count] = NR
            }
        }
        function append_structural_tokens(text, cursor, character, token) {
            token = ""
            for (cursor = 1; cursor <= length(text); cursor++) {
                character = substr(text, cursor, 1)
                if (character ~ /[[:alnum:]_$]/) {
                    token = token character
                } else {
                    append_source_token(token)
                    token = ""
                    if (character !~ /[[:space:]]/) {
                        append_source_token(character)
                    }
                }
            }
            append_source_token(token)
        }
        function update_method_blocks(text, cursor, character) {
            for (cursor = 1; cursor <= length(text); cursor++) {
                character = substr(text, cursor, 1)
                if (character == "{") {
                    method_depth++
                    method_block_id[method_depth] = ++next_block_id
                } else if (character == "}") {
                    delete method_block_id[method_depth]
                    method_depth--
                }
            }
        }
        BEGIN {
            liveness_pattern = "[.][[:space:]]*isAlive[[:space:]]*[(][[:space:]]*[)]"
            accepted_liveness_pattern = "(^|[^[:alnum:]_$.])target[[:space:]]*[.][[:space:]]*isAlive[[:space:]]*[(][[:space:]]*[)]"
            pending_method = 0
            in_method = 0
            method_depth = 0
            method_count = 0
            closed_method_count = 0
            binding_count = 0
            target_var_count = 0
            all_liveness_count = 0
            method_liveness_count = 0
            accepted_liveness_count = 0
            accepted_same_block_count = 0
            binding_block_id = -1
            binding_line = -1
            scenario_binding_count = 0
            exact_import_count = 0
            scenario_class_count = 0
            helper_count = 0
            constructor_count = 0
            breeze_token_count = 0
            is_alive_token_count = 0
            source_token_count = 0
            comment_ambiguity_count = 0
            quoted_brace_count = 0
            text_block_count = 0
            next_block_id = 0
        }
        {
            line = $0
            if (line ~ /\/\*|\*\/|\/\// \
                    && line != "/** Direct server-world proof for the P9-S3 projectile continuation lifecycle. */") {
                comment_ambiguity_count++
            }
            if (line ~ /[{}]/ && line ~ /["\047]/) {
                quoted_brace_count++
            }
            if (line ~ /"""/) {
                text_block_count++
            }
            token_line = line
            append_structural_tokens(line)
            gsub(/[^[:alnum:]_$]+/, " ", token_line)
            token_count = split(token_line, tokens, /[[:space:]]+/)
            for (token_index = 1; token_index <= token_count; token_index++) {
                if (tokens[token_index] == "Breeze") {
                    breeze_token_count++
                }
                if (tokens[token_index] == "isAlive") {
                    is_alive_token_count++
                }
            }
            if (line ~ /^[[:space:]]*import[[:space:]]+net[.]minecraft[.]world[.]entity[.]monster[.]breeze[.]Breeze[[:space:]]*;[[:space:]]*$/) {
                exact_import_count++
            }
            if (line ~ /^[[:space:]]*private[[:space:]]+static[[:space:]]+final[[:space:]]+class[[:space:]]+ProductionScenario[[:space:]]+implements[[:space:]]+AutoCloseable[[:space:]]*\{[[:space:]]*$/) {
                scenario_class_count++
            }
            if (line ~ /^[[:space:]]*private[[:space:]]+Breeze[[:space:]]+addDeflectingTarget[[:space:]]*\([[:space:]]*Vec3[[:space:]]+position[[:space:]]*\)[[:space:]]*\{[[:space:]]*$/) {
                helper_count++
            }
            if (line ~ /new[[:space:]]+Breeze[[:space:]]*\(/) {
                constructor_count++
            }
            liveness = occurrences(line, liveness_pattern)
            all_liveness_count += liveness

            if (!in_method && !pending_method \
                    && line ~ /private[[:space:]]+static[[:space:]]+void[[:space:]]+exerciseAgeTerminalBeforeSweep[[:space:]]*\(/) {
                pending_method = 1
                method_count++
            }
            if (pending_method && !in_method && line ~ /\{/) {
                pending_method = 0
                in_method = 1
                method_depth = 0
            }

            if (in_method) {
                current_block_id = method_depth > 0 \
                        ? method_block_id[method_depth] : -1
                method_liveness_count += liveness
                accepted_liveness = occurrences(line, accepted_liveness_pattern)
                accepted_liveness_count += accepted_liveness
                if (accepted_liveness > 0 \
                        && line !~ /[{}]/ \
                        && current_block_id == binding_block_id) {
                    accepted_same_block_count += accepted_liveness
                }
                if (line ~ /^[[:space:]]*try[[:space:]]*\([[:space:]]*var[[:space:]]+scenario[[:space:]]*=[[:space:]]*new[[:space:]]+ProductionScenario[[:space:]]*\([[:space:]]*helper[[:space:]]*,[[:space:]]*fixtureId[[:space:]]*\)[[:space:]]*\)[[:space:]]*\{[[:space:]]*$/) {
                    scenario_binding_count++
                }
                if (line ~ /^[[:space:]]*var[[:space:]]+target[[:space:]]*=/) {
                    target_var_count++
                    if (line ~ /^[[:space:]]*var[[:space:]]+target[[:space:]]*=[[:space:]]*scenario[[:space:]]*\.[[:space:]]*addDeflectingTarget[[:space:]]*\(/) {
                        binding_count++
                        binding_block_id = current_block_id
                        binding_line = NR
                    }
                }
                update_method_blocks(line)
                if (method_depth == 0) {
                    in_method = 0
                    closed_method_count++
                }
            }
        }
        END {
            helper_declaration_count = 0
            helper_other_count = 0
            target_binding_shape_count = 0
            for (source_index = 1; source_index <= source_token_count; source_index++) {
                if (source_tokens[source_index] == "addDeflectingTarget") {
                    if (source_tokens[source_index - 1] == "Breeze" \
                            && source_tokens[source_index + 1] == "(" \
                            && source_tokens[source_index + 2] == "Vec3" \
                            && source_tokens[source_index + 3] == "position" \
                            && source_tokens[source_index + 4] == ")" \
                            && source_tokens[source_index + 5] == "{") {
                        helper_declaration_count++
                    } else if (source_tokens[source_index - 1] != ".") {
                        helper_other_count++
                    }
                }
                if (source_tokens[source_index] == "target" \
                        && source_token_line[source_index] == binding_line \
                        && source_tokens[source_index - 1] == "var" \
                        && source_tokens[source_index + 1] == "=" \
                        && source_tokens[source_index + 2] == "scenario" \
                        && source_tokens[source_index + 3] == "." \
                        && source_tokens[source_index + 4] == "addDeflectingTarget" \
                        && source_tokens[source_index + 5] == "(" \
                        && source_tokens[source_index + 6] == "projectile" \
                        && source_tokens[source_index + 7] == "." \
                        && source_tokens[source_index + 8] == "position" \
                        && source_tokens[source_index + 9] == "(" \
                        && source_tokens[source_index + 10] == ")" \
                        && source_tokens[source_index + 11] == "." \
                        && source_tokens[source_index + 12] == "add" \
                        && source_tokens[source_index + 13] == "(" \
                        && source_tokens[source_index + 14] == "projectile" \
                        && source_tokens[source_index + 15] == "." \
                        && source_tokens[source_index + 16] == "getDeltaMovement" \
                        && source_tokens[source_index + 17] == "(" \
                        && source_tokens[source_index + 18] == ")" \
                        && source_tokens[source_index + 19] == "." \
                        && source_tokens[source_index + 20] == "scale" \
                        && source_tokens[source_index + 21] == "(" \
                        && source_tokens[source_index + 22] == "0" \
                        && source_tokens[source_index + 23] == "." \
                        && source_tokens[source_index + 24] == "5" \
                        && source_tokens[source_index + 25] == ")" \
                        && source_tokens[source_index + 26] == ")" \
                        && source_tokens[source_index + 27] == ")" \
                        && source_tokens[source_index + 28] == ";") {
                    target_binding_shape_count++
                }
            }
            valid = method_count == 1 \
                    && closed_method_count == 1 \
                    && !pending_method \
                    && !in_method \
                    && exact_import_count == 1 \
                    && scenario_class_count == 1 \
                    && helper_count == 1 \
                    && constructor_count == 1 \
                    && breeze_token_count == 3 \
                    && comment_ambiguity_count == 0 \
                    && quoted_brace_count == 0 \
                    && text_block_count == 0 \
                    && helper_declaration_count == 1 \
                    && helper_other_count == 0 \
                    && scenario_binding_count == 1 \
                    && binding_count == 1 \
                    && target_var_count == 1 \
                    && target_binding_shape_count == 1 \
                    && binding_block_id >= 0 \
                    && is_alive_token_count == 2 \
                    && all_liveness_count == 2 \
                    && method_liveness_count == 2 \
                    && accepted_liveness_count == 2 \
                    && accepted_same_block_count == 2
            exit(valid ? 0 : 1)
        }
    ' "${source}"; then
        reject_game_test_liveness_receiver "${logical_source}"
        return 1
    fi
}

# Bind the narrow source proof above to javac's resolved owner. The required
# qualification routes produce this class before invoking the source consumer.
verify_p9_s3_compiled_liveness() {
    local source="$1"
    local logical_source="$2"
    local classes_root="$3"
    local class_file bytecode

    class_file="${classes_root}/com/yo1no/gramarye/P9S3ProjectileGameTests.class"
    if [[ ! -d "${classes_root}" || -L "${classes_root}" \
            || ! -f "${class_file}" || -L "${class_file}" \
            || "${source}" -nt "${class_file}" ]] \
            || ! command -v javap >/dev/null 2>&1; then
        reject_game_test_liveness_receiver "${logical_source}"
        return 1
    fi
    if ! bytecode="$(javap \
            -classpath "${classes_root}" \
            -c \
            -p \
            com.yo1no.gramarye.P9S3ProjectileGameTests 2>/dev/null)"; then
        reject_game_test_liveness_receiver "${logical_source}"
        return 1
    fi
    if ! printf '%s\n' "${bytecode}" | LC_ALL=C awk '
        BEGIN {
            invoke_prefix = "^[[:space:]]*[0-9]+:[[:space:]]+invoke(virtual|interface|special|static)[[:space:]]+#[0-9]+[[:space:]]+// (InterfaceMethod|Method) "
        }
        /^  private static void exerciseAgeTerminalBeforeSweep[(]/ {
            method_count++
            in_method = 1
            next
        }
        in_method && /^  (public|protected|private) / {
            in_method = 0
        }
        in_method {
            if ($0 ~ invoke_prefix \
                    "com/yo1no/gramarye/P9S3ProjectileGameTests[$]ProductionScenario[.]addDeflectingTarget:[(][^)]*[)]Lnet/minecraft/world/entity/monster/breeze/Breeze;") {
                helper_return_count++
            }
            if ($0 ~ invoke_prefix ".*[.]isAlive:") {
                all_liveness_owner_count++
            }
            if ($0 ~ invoke_prefix \
                    "net/minecraft/world/entity/monster/breeze/Breeze[.]isAlive:[(][)]Z") {
                breeze_liveness_owner_count++
            }
        }
        END {
            valid = method_count == 1 \
                    && helper_return_count == 1 \
                    && all_liveness_owner_count == 2 \
                    && breeze_liveness_owner_count == 2
            exit(valid ? 0 : 1)
        }
    '; then
        reject_game_test_liveness_receiver "${logical_source}"
        return 1
    fi
}

verify_game_test_worker_source() {
    local source="$1"
    local logical_source="$2"
    local classes_root="$3"
    local status=0

    LC_ALL=C grep -Eq \
        '(^|[^[:alnum:]_$])Thread([^[:alnum:]_$]|$)|Thread\.(ofPlatform|ofVirtual)|Thread\.currentThread[[:space:]]*\([[:space:]]*\)[[:space:]]*\.[[:space:]]*isAlive[[:space:]]*\(|new[[:space:]]+Thread[[:space:]]*\(|\.unstarted[[:space:]]*\(|\.join[[:space:]]*\([[:space:]]*[0-9]|\.interrupt[[:space:]]*\(|AtomicReference|(^|[^[:alnum:]_])(Executor|Future|ProcessBuilder)([^[:alnum:]_]|$)' \
        "${source}" || status=$?
    case "${status}" in
        0) reject_game_test_worker_surface "${logical_source}"; return 1 ;;
        1) ;;
        *) return "${status}" ;;
    esac

    status=0
    LC_ALL=C grep -Fq '\u' "${source}" || status=$?
    case "${status}" in
        0) reject_game_test_liveness_receiver "${logical_source}"; return 1 ;;
        1) ;;
        *) return "${status}" ;;
    esac

    status=0
    LC_ALL=C grep -Eq '[^	 -~]' "${source}" || status=$?
    case "${status}" in
        0) reject_game_test_liveness_receiver "${logical_source}"; return 1 ;;
        1) ;;
        *) return "${status}" ;;
    esac

    status=0
    LC_ALL=C grep -Eq \
        '(^|[^[:alnum:]_$])isAlive([^[:alnum:]_$]|$)' \
        "${source}" || status=$?
    case "${status}" in
        0)
            if [[ "${logical_source}" != \
                    'src/main/java/com/yo1no/gramarye/P9S3ProjectileGameTests.java' ]]; then
                reject_game_test_liveness_receiver "${logical_source}"
                return 1
            fi
            verify_p9_s3_entity_liveness "${source}" "${logical_source}" \
                && verify_p9_s3_compiled_liveness \
                    "${source}" "${logical_source}" "${classes_root}"
            ;;
        1) return 0 ;;
        *) return "${status}" ;;
    esac
}

verify_game_tests() {
    local expected_non_p8 actual actual_non_p8 p8_actual annotation_count source
    local expected_holder_count inspected_holder_count marker_status
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
        'P9S5ProvisioningGameTests.java:registeredStarterCommandCoversProvisioningBranches' \
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
    [[ "$(printf '%s\n' "${expected_non_p8}" | wc -l | tr -d ' ')" -eq 35 ]] || {
        printf '%s\n' 'Non-P8 GameTest inventory must remain exact 35' >&2
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
    expected_holder_count="$(printf '%s\n' "${actual}" \
        | awk -F: 'NF >= 2 { print $1 }' | LC_ALL=C sort -u | wc -l | tr -d ' ')"
    [[ "${expected_holder_count}" -eq 11 ]] || {
        printf 'Production-packaged GameTest holder inventory must remain exact 11 (found %s)\n' \
            "${expected_holder_count}" >&2
        return 1
    }
    inspected_holder_count=0
    while IFS= read -r -d '' source; do
        marker_status=0
        LC_ALL=C grep -Eq '@GameTest[[:space:]]*\(' "${source}" || marker_status=$?
        case "${marker_status}" in
        0)
            inspected_holder_count=$((inspected_holder_count + 1))
            verify_game_test_worker_source \
                "${source}" "${source}" build/classes/java/main || return 1
            ;;
        1) ;;
        *) return "${marker_status}" ;;
        esac
    done < <(find src/main/java/com/yo1no/gramarye -type f -name '*.java' -print0)
    [[ "${inspected_holder_count}" -eq "${expected_holder_count}" \
            && "${inspected_holder_count}" -gt 0 ]] || {
        printf 'Production-packaged GameTest worker scan coverage mismatch: expected %s, inspected %s\n' \
            "${expected_holder_count}" "${inspected_holder_count}" >&2
        return 1
    }
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
    --is-p9-s5-harness)
        [[ "$#" -eq 2 ]]
        repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
        source_path="${2#"${repository_root}/"}"
        case "${source_path}" in
            src/main/java/com/yo1no/gramarye/P9S5ProvisioningGameTests.java) exit 0 ;;
            *) exit 1 ;;
        esac ;;
    --is-s4-path)
        [[ "$#" -eq 2 ]] \
            && { is_s4_path "$2" || is_p9_s3_path "$2" \
                || is_p9_s3_dc1_path "$2" || is_p9_s4_path "$2" \
                || is_p9_s4_wc1_path "$2" || is_p9_s5_path "$2"; } ;;
    --is-p9-s5-path)
        [[ "$#" -eq 2 ]] && is_p9_s5_path "$2" ;;
    --check-game-test-worker-source)
        [[ "$#" -eq 4 && -f "$2" && ! -L "$2" ]] \
            && verify_game_test_worker_source "$2" "$3" "$4" ;;
    --game-test-count) [[ "$#" -eq 1 ]] && verify_game_tests ;;
    *) printf '%s\n' 'Expected --is-s4-path PATH, --is-p9-s5-path PATH, --is-s4-harness PATH, --is-p8-harness PATH, --is-p9-s3-harness PATH, --is-p9-s5-harness PATH, --check-game-test-worker-source FILE LOGICAL_PATH CLASSES_ROOT, or --game-test-count' >&2; exit 2 ;;
esac
