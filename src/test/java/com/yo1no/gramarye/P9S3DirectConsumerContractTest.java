package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Direct positive and negative controls for the P9-S3 worker and P9-S5 path consumers. */
final class P9S3DirectConsumerContractTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path VERIFIER = PROJECT_ROOT.resolve(
            "scripts/verify-p7-s4-source-contracts.sh");
    private static final String LOGICAL_P9_SOURCE =
            "src/main/java/com/yo1no/gramarye/P9S3ProjectileGameTests.java";
    private static final String LOGICAL_P9_S5_HOLDER =
            "src/main/java/com/yo1no/gramarye/P9S5ProvisioningGameTests.java";
    private static final String LOGICAL_COOLDOWN_PLAYER_SOURCE =
            "src/main/java/com/yo1no/gramarye/P7S4LoginManaGameTests.java";
    private static final String LOGICAL_DC1_TEST =
            "src/test/java/com/yo1no/gramarye/P9S3DirectConsumerContractTest.java";
    private static final List<String> COOLDOWN_ONLINE_PATHS = List.of(
            "src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownClientHarness.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownServerHarness.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownRestartProbe.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownL1Probe.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownFaultProbe.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownCostProbe.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownDurabilityProbe.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownDualProbe.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownHostProbe.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownHostClientProbe.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownHostCompositionMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownFaultOperationMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownFaultSourceMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownDurabilityClientProbe.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownDurabilityAddMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownDurabilityWriterMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownCostPacketMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownCloneProbe.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/P11CooldownCloneClientProbe.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownFaultAddMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownFaultProjectileMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownFaultRuntimeMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownFaultArmMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownFaultServerMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownCostCellMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownCostMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownCloneCopyMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownCloneSerializerMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownCloneClientMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownL1MaterialMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/magic/network/P11CooldownInputObservation.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownClientInputMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownClientMirrorMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownCompositionMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownFoundationMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownHudMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownInstanceMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownLoginMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownOwnerMixin.java",
            "src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownRuntimeMixin.java",
            "src/p11OnlineHarness/resources/gramarye-p11-cooldown-harness.mixins.json");
    private static final List<String> COOLDOWN_PRODUCT_PATHS = List.of(
            "src/main/java/com/yo1no/gramarye/P11CastCooldownAttachments.java",
            "src/main/java/com/yo1no/gramarye/P11CastCooldownCodec.java",
            "src/main/java/com/yo1no/gramarye/P11CastCooldownData.java",
            "src/main/java/com/yo1no/gramarye/P11CastCooldownDefinitionBridge.java",
            "src/main/java/com/yo1no/gramarye/P11CastCooldownMaterial.java",
            "src/main/java/com/yo1no/gramarye/P11CastCooldownNbtSize.java",
            "src/main/java/com/yo1no/gramarye/P11CastCooldownService.java",
            "src/main/java/com/yo1no/gramarye/P11CooldownPolicyProjection.java",
            "src/main/java/com/yo1no/gramarye/P7AuthenticatedPlayerCastIngress.java",
            "src/main/java/com/yo1no/gramarye/P9ActiveCastTriggerPayloadV1.java",
            "src/main/java/com/yo1no/gramarye/P9ActiveCastTriggerType.java",
            "src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java",
            "src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachments.java",
            "src/main/java/com/yo1no/gramarye/magic/network/CooldownSnapshotEntry.java",
            "src/main/java/com/yo1no/gramarye/magic/network/P7ClientMirrorDispatchPort.java",
            "src/main/java/com/yo1no/gramarye/magic/network/P7ClientPayloadHandlers.java",
            "src/main/java/com/yo1no/gramarye/magic/network/P7CooldownDispatchTask.java",
            "src/main/java/com/yo1no/gramarye/magic/network/P7IntentAckDispatchTask.java",
            "src/main/java/com/yo1no/gramarye/magic/network/P7ManaDispatchTask.java",
            "src/main/java/com/yo1no/gramarye/magic/network/P7NetworkBounds.java",
            "src/main/java/com/yo1no/gramarye/magic/network/P7PayloadCodecSupport.java",
            "src/main/java/com/yo1no/gramarye/magic/network/P7ServerSessionService.java",
            "src/main/java/com/yo1no/gramarye/magic/network/SkillCooldownSnapshot.java",
            "src/p11GameTestHarness/fixtures/startup.toml",
            "src/p11GameTestHarness/java/com/yo1no/gramarye/P11CooldownGameTestHarness.java",
            "src/p11GameTestHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownGameTestFixtureMixin.java",
            "src/p11GameTestHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownGameTestCompositionMixin.java",
            "src/p11GameTestHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownGameTestRuntimeMixin.java",
            "src/p11GameTestHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownGameTestOwnerMixin.java",
            "src/p11GameTestHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownGameTestCapacityMixin.java",
            "src/p11GameTestHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownP8GameTestScheduleMixin.java",
            "src/p11GameTestHarness/java/com/yo1no/gramarye/harnessmixin/P11CooldownP9GameTestScheduleMixin.java",
            "src/p11GameTestHarness/resources/gramarye-p11-gametest-harness.mixins.json",
            "src/test/java/com/yo1no/gramarye/P11CooldownNbtBoundaryTest.java",
            "src/test/java/com/yo1no/gramarye/P11CooldownPolicyTest.java",
            "src/test/java/com/yo1no/gramarye/P11CooldownProjectionTest.java",
            "src/test/java/com/yo1no/gramarye/P11CooldownRuntimeBoundaryTest.java",
            "src/test/java/com/yo1no/gramarye/P11CooldownServiceTest.java",
            "src/test/java/com/yo1no/gramarye/P7P5AdmissionMapperTest.java",
            "src/test/java/com/yo1no/gramarye/magic/network/P7CastIntentNetworkHandlerTest.java",
            "src/test/java/com/yo1no/gramarye/magic/network/P7ClientPayloadHandlersTest.java",
            "src/test/java/com/yo1no/gramarye/magic/network/P7NetworkBoundsTest.java",
            "src/test/java/com/yo1no/gramarye/magic/network/P7QueuedTaskRetentionTest.java",
            "src/test/java/com/yo1no/gramarye/magic/network/P7RecordingPayloadContext.java",
            "src/test/java/com/yo1no/gramarye/magic/network/P7S2CodecTestSupport.java",
            "src/test/java/com/yo1no/gramarye/magic/network/SkillCooldownSyncPayloadCodecTest.java");
    private static final List<String> LOGICAL_P9_S4_PATHS = List.of(
            "src/main/java/com/yo1no/gramarye/P8AppliedFactHandoff.java",
            "src/main/java/com/yo1no/gramarye/P8ServerPresentationService.java",
            "src/test/java/com/yo1no/gramarye/P8S4ServerTransportTest.java",
            "src/test/java/com/yo1no/gramarye/magic/runtime/mana/"
                    + "ActionDamageTransactionDebitTest.java",
            "src/test/java/com/yo1no/gramarye/magic/runtime/mana/"
                    + "ActionDamageTransactionPreDebitTest.java",
            "src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectRequestTest.java");
    private static final List<String> LOGICAL_P9_S4_WC1_PATHS = List.of(
            ".github/workflows/build.yml",
            "scripts/collect-p9-s3-rd1-unit-test-diagnostics.sh",
            "scripts/verify-p9-s4-warning-attribution.py");
    private static final List<String> LOGICAL_P9_S5_PATHS = List.of(
            "build.gradle",
            "scripts/verify-p4-a3-b-configuration.sh",
            "scripts/verify-p4-b2-b-configuration.sh",
            "scripts/verify-p4-c2-a-configuration.sh",
            "scripts/verify-p4-c2-b-configuration.sh",
            "scripts/verify-p4-d1-configuration.sh",
            "scripts/verify-p4-d2-configuration.sh",
            "scripts/verify-p4-d3-a-configuration.sh",
            "scripts/verify-p4-d3-configuration.sh",
            "scripts/verify-p4-e0-r-configuration.sh",
            "scripts/verify-p4-e0-r2q-configuration.sh",
            "scripts/verify-p4-e1-configuration.sh",
            "scripts/verify-p4-e2-configuration.sh",
            "scripts/verify-p4-e3-configuration.sh",
            "scripts/verify-p7-s4-source-contracts.sh",
            "src/main/java/com/yo1no/gramarye/Gramarye.java",
            "src/main/java/com/yo1no/gramarye/P8ClientPayloadDispatchFactory.java",
            "src/main/java/com/yo1no/gramarye/P8ClientPayloadDispatchPort.java",
            "src/main/java/com/yo1no/gramarye/P8ClientPayloadHandlers.java",
            "src/main/java/com/yo1no/gramarye/P8ClientPresentationLifecycle.java",
            "src/main/java/com/yo1no/gramarye/P8ClientPresentationState.java",
            "src/main/java/com/yo1no/gramarye/P9StarterCommand.java",
            "src/main/java/com/yo1no/gramarye/P9StarterSkillContent.java",
            "src/main/java/com/yo1no/gramarye/P9StarterSkillIdentityV0.java",
            "src/main/java/com/yo1no/gramarye/P9S5ProvisioningGameTests.java",
            "src/main/java/com/yo1no/gramarye/magic/network/P7ClientLifecycleEvents.java",
            "src/main/java/com/yo1no/gramarye/magic/network/P9ClientCastInput.java",
            "src/main/java/com/yo1no/gramarye/magic/network/P9ClientKeyMappings.java",
            "src/main/resources/assets/gramarye/lang/en_us.json",
            "src/main/resources/assets/gramarye/lang/zh_tw.json",
            "src/p8S5ClientHarness/java/com/yo1no/gramarye/P8S5ClientRuntimeHarness.java",
            "src/p9S5ClientHarness/java/com/yo1no/gramarye/P9S5ClientRuntimeHarness.java",
            "src/test/java/com/yo1no/gramarye/P6RuntimeExecutionCapabilityTest.java",
            "src/test/java/com/yo1no/gramarye/P7GameTestInventory.java",
            "src/test/java/com/yo1no/gramarye/P8ClientPayloadHandlersTest.java",
            "src/test/java/com/yo1no/gramarye/P8ClientPlayConnection.java",
            "src/test/java/com/yo1no/gramarye/P8ClientPlayEpochAcceptanceTest.java",
            "src/test/java/com/yo1no/gramarye/P8ClientPlayEpochTest.java",
            "src/test/java/com/yo1no/gramarye/P8ClientPresentationExecutionTest.java",
            "src/test/java/com/yo1no/gramarye/P8ClientPresentationLifecycleTest.java",
            "src/test/java/com/yo1no/gramarye/P8ClientPresentationStateConcurrencyTest.java",
            "src/test/java/com/yo1no/gramarye/P8ClientPresentationStateTest.java",
            "src/test/java/com/yo1no/gramarye/P8S2BoundaryTest.java",
            "src/test/java/com/yo1no/gramarye/P8S4PayloadBoundaryTest.java",
            "src/test/java/com/yo1no/gramarye/P9S1BoundaryTest.java",
            "src/test/java/com/yo1no/gramarye/P9S3DirectConsumerContractTest.java",
            "src/test/java/com/yo1no/gramarye/P9S5BoundaryTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2AApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/P4C1ApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2AApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/P4D1ApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2ApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2BApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3AApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3BApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java",
            "src/test/java/com/yo1no/gramarye/magic/network/P7ClientMirrorTest.java",
            "src/test/java/com/yo1no/gramarye/magic/network/P7PayloadRegistrarTest.java",
            "src/test/java/com/yo1no/gramarye/magic/network/P7S2BoundaryTest.java",
            "src/test/java/com/yo1no/gramarye/magic/network/P7S2DedicatedRegistrationTest.java",
            "src/test/java/com/yo1no/gramarye/magic/network/P7S3BoundaryTest.java",
            "src/test/java/com/yo1no/gramarye/magic/network/P9ClientCastInputTest.java",
            "src/test/java/com/yo1no/gramarye/magic/runtime/mana/ManaBoundaryTest.java");
    private static final String VALID_FIXTURE = """
            package com.yo1no.gramarye;

            import net.minecraft.world.entity.monster.breeze.Breeze;

            public final class P9S3ProjectileGameTests {
                private static void exerciseAgeTerminalBeforeSweep(
                        Object helper, long fixtureId) {
                    try (var scenario = new ProductionScenario(helper, fixtureId)) {
                        var projectile = new Projectile();
                        var target = scenario.addDeflectingTarget(projectile.position().add(projectile.getDeltaMovement().scale(0.5)));
                        var firstObservation = target.isAlive();
                        var secondObservation = target.isAlive();
                        if (!firstObservation || !secondObservation) {
                            throw new AssertionError("liveness observation failed");
                        }
                    }
                }

                private static final class ProductionScenario implements AutoCloseable {
                    private ProductionScenario(Object helper, long fixtureId) {}

                    private Breeze addDeflectingTarget(Vec3 position) {
                        return new Breeze();
                    }

                    @Override
                    public void close() {}
                }

                private static final class Projectile {
                    private Vec3 position() {
                        return new Vec3();
                    }

                    private Vec3 getDeltaMovement() {
                        return new Vec3();
                    }
                }

                private static final class Vec3 {
                    private Vec3 add(Vec3 other) {
                        return this;
                    }

                    private Vec3 scale(double factor) {
                        return this;
                    }
                }
            }
            """;
    private static final String BREEZE_STUB = """
            package net.minecraft.world.entity.monster.breeze;

            public class Breeze {
                public boolean isAlive() {
                    return true;
                }
            }
            """;
    private static final String ALIVE_PROBE_STUB = """
            package com.yo1no.gramarye.fixture;

            public interface AliveProbe {
                boolean isAlive();
            }
            """;
    private static final String VALID_COOLDOWN_PLAYER_FIXTURE = """
            package com.yo1no.gramarye;
            import net.minecraft.server.level.ServerPlayer;
            public final class P7S4LoginManaGameTests {
                static ServerPlayer respawnCooldownMockPlayer(ServerPlayer before) {
                    Object observer = new Object();
                    if (before == null
                            || observer == null || !before.isAlive()) {
                        throw new AssertionError("exact living receiver required");
                    }
                    return before;
                }
            }
            """;
    private static final String SERVER_PLAYER_STUB = """
            package net.minecraft.server.level;
            public class ServerPlayer {
                public boolean isAlive() { return true; }
            }
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void actualProductionInventoryAcceptsTheBoundBreezeAndScansAllHolders()
            throws Exception {
        var result = runVerifier(List.of("--game-test-count"));

        assertEquals(0, result.exitCode(), result.output());
        assertEquals("39", result.output().trim());
        assertEquals(67, LOGICAL_P9_S5_PATHS.size());

        var exactCorrectionPath = runVerifier(List.of("--is-s4-path", LOGICAL_DC1_TEST));
        assertEquals(0, exactCorrectionPath.exitCode(), exactCorrectionPath.output());
        var nearCorrectionPath = runVerifier(List.of(
                "--is-s4-path", LOGICAL_DC1_TEST + ".extra"));
        assertEquals(1, nearCorrectionPath.exitCode(), nearCorrectionPath.output());

        for (var exact : COOLDOWN_ONLINE_PATHS) {
            assertEquals(0, runVerifier(List.of("--is-s4-path", exact)).exitCode(), exact);
            for (var rejected : List.of(exact + ".extra", exact.replace("P11Cooldown", "P11CooldownExtra")
                            .replace("gramarye-p11-cooldown", "gramarye-p11-cooldown-extra"),
                    exact.replace("src/p11OnlineHarness/", "src/foreignHarness/"))) {
                assertFalse(rejected.equals(exact), "negative must change the exact input path");
                assertEquals(1, runVerifier(List.of("--is-s4-path", rejected)).exitCode(), rejected);
            }
        }
        for (var exact : COOLDOWN_PRODUCT_PATHS) {
            assertEquals(0, runVerifier(List.of("--is-s4-path", exact)).exitCode(), exact);
            for (var rejected : List.of(exact + ".extra", exact.replaceFirst("src/[^/]+/", "src/foreign/"),
                    exact.substring(0, exact.lastIndexOf('.') + 1) + "foreign")) {
                assertFalse(rejected.equals(exact));
                assertEquals(1, runVerifier(List.of("--is-s4-path", rejected)).exitCode(), rejected);
            }
        }

        var hud = "src/main/java/com/yo1no/gramarye/magic/network/P7CooldownHud.java";
        assertEquals(0, runVerifier(List.of("--is-s4-path", hud)).exitCode());
        for (var rejected : List.of(hud + ".extra", hud.replace("P7CooldownHud", "P7CooldownHudExtra"),
                hud.replace("magic/network", "foreign/network"))) {
            assertEquals(1, runVerifier(List.of("--is-s4-path", rejected)).exitCode(), rejected);
        }

        for (var logicalPath : LOGICAL_P9_S4_PATHS) {
            var exactS4Path = runVerifier(List.of("--is-s4-path", logicalPath));
            assertEquals(0, exactS4Path.exitCode(), exactS4Path.output());
            var nearS4Path = runVerifier(List.of("--is-s4-path", logicalPath + ".extra"));
            assertEquals(1, nearS4Path.exitCode(), nearS4Path.output());
        }
        for (var logicalPath : LOGICAL_P9_S4_WC1_PATHS) {
            var exactWc1Path = runVerifier(List.of("--is-s4-path", logicalPath));
            assertEquals(0, exactWc1Path.exitCode(), exactWc1Path.output());
            var nearWc1Path = runVerifier(List.of("--is-s4-path", logicalPath + ".extra"));
            assertEquals(1, nearWc1Path.exitCode(), nearWc1Path.output());
        }
        for (var logicalPath : LOGICAL_P9_S5_PATHS) {
            var exactS5Path = runVerifier(List.of("--is-p9-s5-path", logicalPath));
            assertEquals(0, exactS5Path.exitCode(), exactS5Path.output());
            var exactAggregatePath = runVerifier(List.of("--is-s4-path", logicalPath));
            assertEquals(0, exactAggregatePath.exitCode(), exactAggregatePath.output());
            var nearS5Path = runVerifier(List.of(
                    "--is-p9-s5-path", logicalPath + ".extra"));
            assertEquals(1, nearS5Path.exitCode(), nearS5Path.output());
        }
        for (var broadPath : List.of(
                "scripts/verify-p4-configuration.sh",
                "src/main/java/com/yo1no/gramarye/magic/network",
                "src/main/java/com/yo1no/gramarye/magic/network/P9ClientCastInputExtra.java",
                "src/test/java/com/yo1no/gramarye/P9S5BoundaryTestHelper.java")) {
            var broadS5Path = runVerifier(List.of("--is-p9-s5-path", broadPath));
            assertEquals(1, broadS5Path.exitCode(), broadS5Path.output());
        }

        var exactS5Holder = runVerifier(List.of(
                "--is-p9-s5-harness", LOGICAL_P9_S5_HOLDER));
        assertEquals(0, exactS5Holder.exitCode(), exactS5Holder.output());
        var nearS5Holder = runVerifier(List.of(
                "--is-p9-s5-harness", LOGICAL_P9_S5_HOLDER + ".extra"));
        assertEquals(1, nearS5Holder.exitCode(), nearS5Holder.output());
    }

    @Test
    void directMatcherAcceptsTheStructurallyBoundBreezeReceiver() throws Exception {
        var source = compiledFixture("valid-breeze", VALID_FIXTURE);
        var result = checkFixture(source);

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().isBlank(), result.output());
    }

    @Test
    void exactCooldownRespawnParameterGuardRequiresSourceAndCompiledServerPlayer()
            throws Exception {
        var valid = compiledCooldownFixture("cooldown-player", VALID_COOLDOWN_PLAYER_FIXTURE);
        assertEquals(0, checkCooldownFixture(valid, LOGICAL_COOLDOWN_PLAYER_SOURCE).exitCode());
        for (var logical : List.of(LOGICAL_COOLDOWN_PLAYER_SOURCE + ".extra", LOGICAL_P9_SOURCE,
                LOGICAL_COOLDOWN_PLAYER_SOURCE.replace("P7S4LoginManaGameTests", "OtherGameTests"))) {
            assertEquals(1, checkCooldownFixture(valid, logical).exitCode(), logical);
        }
        var missing = new Fixture(valid.source(), Files.createDirectory(temporaryDirectory.resolve("missing-player-class")));
        assertEquals(1, checkCooldownFixture(missing, LOGICAL_COOLDOWN_PLAYER_SOURCE).exitCode());
    }

    @Test
    void cooldownPlayerLivenessCannotMoveDuplicateRebindOrBorrowAnotherReceiver() throws Exception {
        var mutations = List.of(
                VALID_COOLDOWN_PLAYER_FIXTURE.replace("respawnCooldownMockPlayer", "anotherMethod"),
                VALID_COOLDOWN_PLAYER_FIXTURE.replace("return before;", "before.isAlive(); return before;"),
                VALID_COOLDOWN_PLAYER_FIXTURE.replace("Object observer", "before = new ServerPlayer(); Object observer"),
                VALID_COOLDOWN_PLAYER_FIXTURE.replace("!before.isAlive()", "!new ServerPlayer().isAlive()"),
                VALID_COOLDOWN_PLAYER_FIXTURE.replace("!before.isAlive()", "!Thread.currentThread().isAlive()"),
                VALID_COOLDOWN_PLAYER_FIXTURE.replace("before.isAlive()", "before.is" + "\\" + "u0041live()"),
                VALID_COOLDOWN_PLAYER_FIXTURE.replace("ServerPlayer before", "ServerPlayer... before")
                        .replace("before.isAlive()", "before[0].isAlive()").replace("return before;", "return before[0];"),
                VALID_COOLDOWN_PLAYER_FIXTURE.replace("public final class P7S4LoginManaGameTests {",
                        "public final class P7S4LoginManaGameTests {\n"
                                + "static final class ServerPlayer { boolean isAlive() { return true; } }"));
        for (int index = 0; index < mutations.size(); index++) {
            var fixture = compiledCooldownFixture("cooldown-invalid-" + index, mutations.get(index));
            var result = checkCooldownFixture(fixture, LOGICAL_COOLDOWN_PLAYER_SOURCE);
            assertEquals(1, result.exitCode(), "mutation " + index + ": " + result.output());
            assertTrue(result.output().contains(LOGICAL_COOLDOWN_PLAYER_SOURCE), result.output());
        }
        // The source-looking import/parameter cannot certify a stale class with a different owner.
        var shadow = compiledCooldownFixture("cooldown-compiled-shadow", mutations.getLast());
        var valid = compiledCooldownFixture("cooldown-source-for-shadow", VALID_COOLDOWN_PLAYER_FIXTURE);
        Files.copy(shadow.classes().resolve("com/yo1no/gramarye/P7S4LoginManaGameTests.class"),
                valid.classes().resolve("com/yo1no/gramarye/P7S4LoginManaGameTests.class"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertEquals(1, checkCooldownFixture(valid, LOGICAL_COOLDOWN_PLAYER_SOURCE).exitCode());
    }

    @Test
    void receiverStillNamedTargetIsRejectedWhenDeclaredThread() throws Exception {
        var source = compiledFixture(
                "thread-target",
                VALID_FIXTURE.replace(
                        "var target = scenario.addDeflectingTarget(projectile.position().add(projectile.getDeltaMovement().scale(0.5)));",
                        "Thread target = null;"));

        assertRejected(source, "raw worker/task/process surface");

        var encodedThreadOwner = "java.lang.Thre" + "\\" + "uuuu0061d.currentThread().is"
                + "\\" + "uuuu0041live()";
        var encodedSource = compiledFixture(
                "unicode-current-thread",
                VALID_FIXTURE.replace(
                        "var firstObservation = target.isAlive();",
                        "var firstObservation = " + encodedThreadOwner + ";"));

        assertRejected(encodedSource, "unverified liveness receiver");
    }

    @Test
    void currentThreadLivenessIsRejectedIndependently() throws Exception {
        var source = compiledFixture(
                "current-thread",
                VALID_FIXTURE.replace(
                        "var firstObservation = target.isAlive();",
                        "var firstObservation = Thread.currentThread().isAlive();"));

        assertRejected(source, "raw worker/task/process surface");
    }

    @Test
    void sameNameThreadParameterCannotBorrowTheEntityBinding() throws Exception {
        var threadSource = compiledFixture(
                "shadowed-thread-target",
                VALID_FIXTURE.replace(
                        "var secondObservation = target.isAlive();",
                        "class ReceiverProbe {\n"
                                + "                private boolean inspect(\n"
                                + "                        java.lang.Thread\n"
                                + "                        target) {\n"
                                + "                    return target.isAlive();\n"
                                + "                }\n"
                                + "            }\n"
                                + "            var receiverProbe = new ReceiverProbe();\n"
                                + "            var secondObservation = receiverProbe.inspect(null);"));

        assertRejected(threadSource, "raw worker/task/process surface");

        var siblingScopeSource = compiledFixture(
                "sibling-scope-unknown-target",
                VALID_FIXTURE
                        .replace(
                                "import net.minecraft.world.entity.monster.breeze.Breeze;",
                                "import net.minecraft.world.entity.monster.breeze.Breeze;\n"
                                        + "import com.yo1no.gramarye.fixture.AliveProbe;")
                        .replace(
                                "var projectile = new Projectile();\n"
                                        + "            var target = scenario.addDeflectingTarget(projectile.position().add(projectile.getDeltaMovement().scale(0.5)));\n"
                                        + "            var firstObservation = target.isAlive();\n"
                                        + "            var secondObservation = target.isAlive();",
                                "boolean firstObservation;\n"
                                        + "            boolean secondObservation;\n"
                                        + "            {\n"
                                        + "                var projectile = new Projectile();\n"
                                        + "                var target = scenario.addDeflectingTarget(projectile.position().add(projectile.getDeltaMovement().scale(0.5)));\n"
                                        + "            }\n"
                                        + "            {\n"
                                        + "                AliveProbe target = () -> true;\n"
                                        + "                firstObservation = target.isAlive();\n"
                                        + "                secondObservation = target.isAlive();\n"
                                        + "            }"));

        assertRejected(siblingScopeSource, "unverified liveness receiver");
    }

    @Test
    void forbiddenConcurrencyAndProcessSurfacesRemainRejectedBesideLegalEntityUse()
            throws Exception {
        for (var declaration : List.of(
                "private java.util.concurrent.Executor executor;",
                "private java.util.concurrent.Future<?> future;",
                "private ProcessBuilder processBuilder;",
                "private java.util.concurrent.atomic.AtomicReference<Object> reference;")) {
            var label = declaration.substring(declaration.lastIndexOf(' ') + 1)
                    .replace(";", "");
            var source = compiledFixture(
                    "forbidden-" + label,
                    VALID_FIXTURE.replace(
                            "public final class P9S3ProjectileGameTests {",
                            "public final class P9S3ProjectileGameTests {\n    "
                                    + declaration));

            assertRejected(source, "raw worker/task/process surface");
        }
    }

    @Test
    void unknownLivenessReceiverRemainsFailClosed() throws Exception {
        var source = compiledFixture(
                "unknown-target",
                VALID_FIXTURE
                        .replace(
                                "var target = scenario.addDeflectingTarget(projectile.position().add(projectile.getDeltaMovement().scale(0.5)));",
                                "Unknown target = new Unknown();")
                        .replace(
                                "private static final class Projectile {",
                                "private static final class Unknown {\n"
                                        + "        private boolean isAlive() {\n"
                                        + "            return true;\n"
                                        + "        }\n"
                                        + "    }\n\n"
                                        + "    private static final class Projectile {"));

        assertRejected(source, "unverified liveness receiver");

        var overloadBase = VALID_FIXTURE
                .replace(
                        "import net.minecraft.world.entity.monster.breeze.Breeze;",
                        "import net.minecraft.world.entity.monster.breeze.Breeze;\n"
                                + "import com.yo1no.gramarye.fixture.AliveProbe;")
                .replace(
                        "var target = scenario.addDeflectingTarget(projectile.position().add(projectile.getDeltaMovement().scale(0.5)));",
                        "var target = scenario.addDeflectingTarget(new ProbePosition());")
                .replace(
                        "private static final class Projectile {",
                        "private static final class ProbePosition {}\n\n"
                                + "    private static final class Projectile {");
        var overloadSource = compiledFixture(
                "comment-separated-decoy-helper",
                overloadBase.replace(
                        "private Breeze addDeflectingTarget(Vec3 position) {",
                        "private AliveProbe\n"
                                + "    // .\n"
                                + "    addDeflectingTarget(ProbePosition position) {\n"
                                + "        return () -> true;\n"
                                + "    }\n\n"
                                + "    private Breeze addDeflectingTarget(Vec3 position) {"));

        assertRejected(overloadSource, "unverified liveness receiver");

        var ignoredCodePoint = Character.toString(0x200B);
        var ignoredIdentifierSource = compiledFixture(
                "ignorable-identifier-decoy-helper",
                overloadBase.replace(
                        "private Breeze addDeflectingTarget(Vec3 position) {",
                        "private AliveProbe addDeflecting"
                                + ignoredCodePoint
                                + "Target(ProbePosition position) {\n"
                                + "        return () -> true;\n"
                                + "    }\n\n"
                                + "    private Breeze addDeflectingTarget(Vec3 position) {"));

        assertRejected(ignoredIdentifierSource, "unverified liveness receiver");

        var localTypeSource = compiledFixture(
                "local-breeze-name",
                VALID_FIXTURE
                        .replace(
                                "private static final class Projectile {",
                                "private static final class Breeze {\n"
                                        + "        private boolean isAlive() {\n"
                                        + "            return true;\n"
                                        + "        }\n"
                                        + "    }\n\n"
                                        + "    private static final class Projectile {"));

        assertRejected(localTypeSource, "unverified liveness receiver");
    }

    private Fixture compiledFixture(String label, String source) throws IOException {
        var fixtureRoot = Files.createDirectories(temporaryDirectory.resolve(label));
        var sourcePath = fixtureRoot.resolve("P9S3ProjectileGameTests.java");
        Files.writeString(sourcePath, source, StandardCharsets.UTF_8);
        var breezePath = fixtureRoot.resolve(
                "net/minecraft/world/entity/monster/breeze/Breeze.java");
        Files.createDirectories(breezePath.getParent());
        Files.writeString(breezePath, BREEZE_STUB, StandardCharsets.UTF_8);
        var aliveProbePath = fixtureRoot.resolve(
                "com/yo1no/gramarye/fixture/AliveProbe.java");
        Files.createDirectories(aliveProbePath.getParent());
        Files.writeString(aliveProbePath, ALIVE_PROBE_STUB, StandardCharsets.UTF_8);
        var classes = Files.createDirectory(fixtureRoot.resolve("classes"));
        var diagnostics = new ByteArrayOutputStream();
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler unavailable");
        var exitCode = compiler.run(
                null,
                diagnostics,
                diagnostics,
                "--release",
                "21",
                "-proc:none",
                "-d",
                classes.toString(),
                sourcePath.toString(),
                breezePath.toString(),
                aliveProbePath.toString());
        assertEquals(
                0,
                exitCode,
                () -> "fixture must be valid Java:\n"
                        + diagnostics.toString(StandardCharsets.UTF_8));
        return new Fixture(sourcePath, classes);
    }

    private Fixture compiledCooldownFixture(String label, String source) throws IOException {
        var fixtureRoot = Files.createDirectories(temporaryDirectory.resolve(label));
        var sourcePath = fixtureRoot.resolve("P7S4LoginManaGameTests.java");
        Files.writeString(sourcePath, source, StandardCharsets.UTF_8);
        var playerPath = fixtureRoot.resolve("net/minecraft/server/level/ServerPlayer.java");
        Files.createDirectories(playerPath.getParent());
        Files.writeString(playerPath, SERVER_PLAYER_STUB, StandardCharsets.UTF_8);
        var classes = Files.createDirectory(fixtureRoot.resolve("classes"));
        var diagnostics = new ByteArrayOutputStream();
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler unavailable");
        assertEquals(0, compiler.run(null, diagnostics, diagnostics, "--release", "21", "-proc:none", "-d",
                classes.toString(), sourcePath.toString(), playerPath.toString()),
                () -> "fixture must be valid Java:\n" + diagnostics.toString(StandardCharsets.UTF_8));
        return new Fixture(sourcePath, classes);
    }

    private ProcessResult checkCooldownFixture(Fixture fixture, String logical) throws Exception {
        return runVerifier(List.of("--check-game-test-worker-source", fixture.source().toString(),
                logical, fixture.classes().toString()));
    }

    private void assertRejected(Fixture fixture, String diagnostic) throws Exception {
        var result = checkFixture(fixture);
        assertEquals(1, result.exitCode(), result.output());
        assertTrue(result.output().contains(diagnostic), result.output());
        assertTrue(result.output().contains(LOGICAL_P9_SOURCE), result.output());
    }

    private ProcessResult checkFixture(Fixture fixture) throws Exception {
        return runVerifier(List.of(
                "--check-game-test-worker-source",
                fixture.source().toString(),
                LOGICAL_P9_SOURCE,
                fixture.classes().toString()));
    }

    private ProcessResult runVerifier(List<String> arguments) throws Exception {
        assertTrue(Files.isRegularFile(VERIFIER), VERIFIER.toString());
        assertFalse(Files.isSymbolicLink(VERIFIER), VERIFIER.toString());
        var command = new java.util.ArrayList<String>();
        command.add("bash");
        command.add(VERIFIER.toString());
        command.addAll(arguments);
        var process = new ProcessBuilder(command)
                .directory(PROJECT_ROOT.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        return new ProcessResult(exitCode, output);
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project root unavailable");
        }
        return current;
    }

    private record ProcessResult(int exitCode, String output) {}

    private record Fixture(Path source, Path classes) {}
}
