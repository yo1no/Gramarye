package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Direct owner/receipt boundaries; these do not substitute for native add/save evidence. */
final class P11CooldownRuntimeBoundaryTest {
    private static final Path ROOT = projectRoot();
    private static final Path JAVA = ROOT.resolve("src/main/java/com/yo1no/gramarye");

    @Test
    void positivePreparationCannotExecuteAndAllP9AdmissionsRequireTheSoleService() throws Exception {
        var source = Files.readString(JAVA.resolve("SkillRuntimeService.java"));
        assertPreparation(source);
        var missingInstall = source.replace("if (!cooldowns.installPending(pending))", "if (false)");
        assertNotEquals(source, missingInstall);
        assertThrows(AssertionError.class, () -> assertPreparation(missingInstall));
        var missingService = source.replace("cooldowns == null || duration.isEmpty()", "duration.isEmpty()");
        assertNotEquals(source, missingService);
        assertThrows(AssertionError.class, () -> assertPreparation(missingService));
        var publishedEarly = source.replace("prepared.publish(slot);", "return accepted; // publication removed");
        assertNotEquals(source, publishedEarly);
        assertThrows(AssertionError.class, () -> assertPreparation(publishedEarly));
    }

    @Test
    void actualAddAndAllPreparedCallbacksRemainInertUntilOwnerArm() throws Exception {
        var runtime = Files.readString(JAVA.resolve("SkillRuntimeService.java"));
        var entity = Files.readString(JAVA.resolve("P9StarterProjectile.java"));
        var handoff = Files.readString(JAVA.resolve("P9WorldEffectHandoff.java"));
        assertPreparedSpawn(runtime, entity, handoff);
        var missingTick = entity.replace("if (continuationPermit.isPreparedSpawn()) {\n            return;\n        }", "");
        assertNotEquals(entity, missingTick);
        assertThrows(AssertionError.class, () -> assertPreparedSpawn(runtime, missingTick, handoff));
        var missingClaim = runtime.replace("if (permit.isPreparedSpawn()) {", "if (false) {");
        assertNotEquals(runtime, missingClaim);
        assertThrows(AssertionError.class, () -> assertPreparedSpawn(missingClaim, entity, handoff));
        var falseAddProof = handoff.replace("opened.prepareBeforeNativeAdd(server, projectile)", "true");
        assertNotEquals(handoff, falseAddProof);
        assertThrows(AssertionError.class, () -> assertPreparedSpawn(runtime, entity, falseAddProof));
    }

    @Test
    void actualScalarReceiptIsMonotonicAndHasNoActorOrRuntimeCustody() throws Exception {
        var runtime = Files.readString(JAVA.resolve("SkillRuntimeService.java"));
        var receipt = declaration(runtime, "static final class CooldownReleaseReceipt");
        for (var forbidden : List.of("ServerPlayer", "MinecraftServer", "ServerSlot", "WorkReservation",
                "SkillRuntimeService owner", "Body", "SkillReference")) {
            assertFalse(receipt.contains(forbidden), forbidden);
        }
        assertTrue(receipt.contains("private CooldownReleaseReceipt(long runtimeServerToken, long preparedRootEventId)"));
        assertFalse(receipt.contains("randomUUID"));
        assertTrue(runtime.contains("new CooldownReleaseReceipt(slot.token.value(), prospectiveEventId.value())"));
        var constructor = SkillRuntimeService.CooldownReleaseReceipt.class.getDeclaredConstructor(long.class, long.class);
        constructor.setAccessible(true);
        var virgin = constructor.newInstance(7L, 19L);
        assertEquals(new UUID(7L, 19L), virgin.attemptId());
        assertNotEquals(virgin.attemptId(), constructor.newInstance(7L, 20L).attemptId());
        assertNotEquals(virgin.attemptId(), constructor.newInstance(8L, 19L).attemptId());
        assertThrows(InvocationTargetException.class, () -> constructor.newInstance(0L, 19L));
        assertThrows(InvocationTargetException.class, () -> constructor.newInstance(7L, 0L));
        assertEquals(P11CastCooldownService.ReleaseFact.UNPUBLISHED, virgin.fact());
        assertEquals(-1, virgin.releasedAt());
        assertEquals(false, invoke(virgin, "mayArm"));
        assertThrows(RuntimeKernelException.class, () -> invoke(virgin, "published"));
        invoke(virgin, "beginPublication");
        assertEquals(P11CastCooldownService.ReleaseFact.UNKNOWN, virgin.fact());
        assertEquals(false, invoke(virgin, "mayArm"));
        invoke(virgin, "published");
        assertEquals(true, invoke(virgin, "mayArm"));
        assertThrows(RuntimeKernelException.class, () -> invoke(virgin, "beginPublication"));
        assertThrows(RuntimeKernelException.class, () -> invoke(virgin, "published"));
        var arm = SkillRuntimeService.CooldownReleaseReceipt.class.getDeclaredMethod("armed", long.class);
        arm.setAccessible(true);
        arm.invoke(virgin, 1002L);
        virgin.revoke();
        virgin.revoke();
        assertEquals(P11CastCooldownService.ReleaseFact.ARM, virgin.fact());
        assertEquals(1002, virgin.releasedAt());
        assertEquals(false, invoke(virgin, "mayArm"));
        for (int point = 0; point < 3; point++) {
            var failed = constructor.newInstance(7L, 21L + point);
            if (point >= 1) invoke(failed, "beginPublication");
            if (point >= 2) invoke(failed, "published");
            failed.revoke();
            failed.revoke();
            assertEquals(P11CastCooldownService.ReleaseFact.NO_RELEASE, failed.fact());
            assertEquals(-1, failed.releasedAt());
            assertEquals(false, invoke(failed, "mayArm"));
            assertThrows(RuntimeKernelException.class, () -> invoke(failed, "beginPublication"));
            assertThrows(RuntimeKernelException.class, () -> invoke(failed, "published"));
        }
    }

    private static Object invoke(SkillRuntimeService.CooldownReleaseReceipt receipt, String name) throws Exception {
        var method = SkillRuntimeService.CooldownReleaseReceipt.class.getDeclaredMethod(name);
        method.setAccessible(true);
        try {
            return method.invoke(receipt);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    @Test
    void armTruthPrecedesOptionalDiagnosticsAndTerminalReconciliationPrecedesWorkRelease() throws Exception {
        var source = Files.readString(JAVA.resolve("SkillRuntimeService.java"));
        assertTerminalAndArm(source);
        var lostReceipt = source.replace("instance.cooldownReceipt.armed(arm.releasedAt());", "/* lost receipt */");
        assertNotEquals(source, lostReceipt);
        assertThrows(AssertionError.class, () -> assertTerminalAndArm(lostReceipt));
        var noSettlement = source.replace("cooldownPreparation.settle();", "/* no settlement */");
        assertNotEquals(source, noSettlement);
        assertThrows(AssertionError.class, () -> assertTerminalAndArm(noSettlement));
        var allocateOnError = source.replace("instance.releaseWorkAfterError();", "instance.releaseWork();");
        assertNotEquals(source, allocateOnError);
        assertThrows(AssertionError.class, () -> assertTerminalAndArm(allocateOnError));
    }

    private static void assertPreparation(String source) {
        var admission = declaration(source, "private RuntimeAdmissionResult acquireAndPublishRoot(");
        assertTrue(admission.contains("cooldowns == null || duration.isEmpty()"));
        assertTrue(admission.contains("slot.runtimeTick, deadlineTick, receipt, instance.work)"));
        before(admission, "new PreparedRoot(", "cooldowns.prepareAdmission(");
        before(admission, "cooldowns.installPending(pending)", "prepared.publish(slot);");
        before(admission, "prepared.publish(slot);", "return accepted;");
        assertTrue(admission.contains("instance.releaseWork();"));
        var prepared = declaration(source, "private static final class PreparedRoot");
        assertFalse(prepared.contains("queue"));
        assertFalse(prepared.contains("addCommittedEvent"));
        before(prepared, "instance.cooldownReceipt.beginPublication();", "publishRoot(slot,");
        var publish = declaration(source, "private static void publishRoot(");
        before(publish, "addCommittedEvent(slot, instance, attribution, event);", "instance.cooldownReceipt.published();");
        before(publish, "instance.cooldownReceipt.published();", "new ServerSlot.P9ActiveDiagnostic");
    }

    private static void assertPreparedSpawn(String runtime, String entity, String handoff) {
        var spawn = declaration(handoff, "public CommitDisposition commitSpawn(");
        before(spawn, "opened.prepareBeforeNativeAdd(server, projectile)", "level.addFreshEntity(projectile)");
        before(spawn, "level.addFreshEntity(projectile)", "opened.transferAfterAppliedSpawn(server, projectile)");
        var tick = declaration(entity, "public void tick()");
        before(tick, "continuationPermit.isPreparedSpawn()", "if (locallyClaimedOrTerminal)");
        assertTrue(declaration(entity, "protected void onHitEntity(").contains("continuationPermit.isPreparedSpawn()"));
        assertTrue(declaration(entity, "protected void onHitBlock(").contains("continuationPermit.isPreparedSpawn()"));
        var claim = declaration(runtime, "private Optional<RuntimePermitClaimDisposition> claimProjectileHitInSlot(");
        before(claim, "permit.isPreparedSpawn()", "var instance = slot.instances.get");
        var preparation = declaration(runtime, "boolean prepareNativeSpawn(");
        before(preparation, "projectileQualification(server, permit, projectile)", "permit.nativeSpawnPrepared = true;");
    }

    private static void assertTerminalAndArm(String source) {
        var transfer = declaration(source, "RuntimePermitTransferDisposition transferSpawnedProjectile(");
        before(transfer, "cooldowns.prepareArm(", "permit.state = RuntimeProjectileContinuationPermit.State.OPEN;");
        before(transfer, "permit.state = RuntimeProjectileContinuationPermit.State.OPEN;", "instance.cooldownReceipt.armed(arm.releasedAt());");
        before(transfer, "instance.cooldownReceipt.armed(arm.releasedAt());", "cooldowns.completeArm(arm);");
        before(transfer, "cooldowns.completeArm(arm);", "RuntimePermitTransferDisposition.TRANSFERRED);");
        var release = declaration(source, "private void releaseWork(boolean reconcile)");
        before(release, "cooldownReceipt.revoke();", "cooldownPreparation.settle();");
        before(release, "cooldownPreparation.settle();", "retained.release();");
        assertTrue(release.contains("finally"));
        assertTrue(declaration(source, "static final class P9InstanceErrorCleanup").contains("instance.releaseWorkAfterError();"));
        var close = declaration(source, "private RuntimePermitCloseDisposition closeProjectileContinuationOnObservedThread(");
        before(close, "instance.cooldownReceipt.revoke();", "instance.activeProjectileContinuation = null;");
    }

    private static void before(String source, String first, String second) {
        int left = source.indexOf(first), right = source.indexOf(second);
        assertTrue(left >= 0 && right > left, first + " before " + second);
    }

    private static String declaration(String source, String anchor) {
        int start = source.indexOf(anchor);
        assertTrue(start >= 0, anchor);
        int brace = source.indexOf('{', start), depth = 1;
        assertTrue(brace >= 0, anchor);
        for (int index = brace + 1; index < source.length(); index++) {
            if (source.charAt(index) == '{') depth++;
            if (source.charAt(index) == '}' && --depth == 0) return source.substring(start, index + 1);
        }
        throw new AssertionError(anchor);
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("project root unavailable");
        return current;
    }
}
