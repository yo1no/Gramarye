package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.P6RuntimeExecutionCapability;
import com.yo1no.gramarye.V0CatalogAuthority;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

final class P7ServerAuthorizationBoundaryTest {
    @Test
    void v0WitnessIsExactAndCannotMintWithoutAuthority() throws Exception {
        var boundary = P7ServerAuthorizationBoundary.class;
        var type = P7ServerAuthorizationBoundary.V0PlayWitness.class;
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertTrue(Modifier.isStatic(type.getModifiers()));
        assertEquals(1, type.getDeclaredConstructors().length);
        assertTrue(Modifier.isPrivate(type.getDeclaredConstructor().getModifiers()));
        assertEquals(0, type.getDeclaredMethods().length);
        assertEquals(Set.of(V0CatalogAuthority.class, P7SessionIdentity.class),
                Arrays.stream(type.getDeclaredFields()).map(java.lang.reflect.Field::getType)
                        .collect(Collectors.toSet()));
        assertTrue(Arrays.stream(type.getDeclaredFields()).allMatch(field ->
                Modifier.isPrivate(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())));
        var capture = boundary.getDeclaredMethod("captureV0Play", V0CatalogAuthority.class,
                MinecraftServer.class, ServerPlayer.class);
        assertEquals("java.util.Optional<" + type.getName() + ">", capture.getGenericReturnType().getTypeName());
        var current = boundary.getDeclaredMethod("currentV0Play", V0CatalogAuthority.class,
                type, MinecraftServer.class, ServerPlayer.class);
        assertEquals(boolean.class, current.getReturnType());
        assertTrue(P7ServerAuthorizationBoundary.captureV0Play(null, null, null).isEmpty());
        assertFalse(P7ServerAuthorizationBoundary.currentV0Play(null, null, null, null));
    }

    @Test
    void publicSurfaceIsExactAndDispatchRemainsPackagePrivate() throws Exception {
        var boundary = P7ServerAuthorizationBoundary.class;
        var constructors = boundary.getDeclaredConstructors();
        var fields = boundary.getDeclaredFields();
        var methods = boundary.getDeclaredMethods();

        assertTrue(Modifier.isPublic(boundary.getModifiers()));
        assertTrue(Modifier.isFinal(boundary.getModifiers()));
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertEquals(8, fields.length);
        assertEquals(
                0,
                Arrays.stream(fields)
                        .filter(field -> Modifier.isPublic(field.getModifiers())
                                || Modifier.isProtected(field.getModifiers()))
                        .count());
        assertEquals(
                6,
                Arrays.stream(methods)
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .count());
        assertEquals(
                6,
                Arrays.stream(methods)
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .count());
        assertEquals(
                Set.of("dispatch", "install", "loginReadyPort", "prepareSync", "captureV0Play", "currentV0Play",
                        "normalReceipt", "dispatchNormal"),
                Arrays.stream(methods)
                        .filter(method -> !method.isSynthetic())
                        .map(Method::getName)
                        .collect(Collectors.toSet()));
        assertEquals(
                3,
                Arrays.stream(methods)
                        .filter(method -> method.getName().equals("install"))
                        .count());
        assertEquals(
                1,
                Arrays.stream(methods)
                        .filter(method -> method.getName().equals("dispatch"))
                        .count());

        var unavailable = boundary.getDeclaredField("UNAVAILABLE_ROOT_INGRESS");
        assertSame(
                P7ServerAuthorizationBoundary.RootIngressPort.class,
                unavailable.getType());
        assertTrue(Modifier.isPrivate(unavailable.getModifiers()));
        assertTrue(Modifier.isStatic(unavailable.getModifiers()));
        assertTrue(Modifier.isFinal(unavailable.getModifiers()));

        var loginPort = boundary.getDeclaredField("LOGIN_READY_PORT");
        assertSame(P7ServerAuthorizationBoundary.LoginReadyPort.class, loginPort.getType());
        assertTrue(Modifier.isPrivate(loginPort.getModifiers()));
        assertTrue(Modifier.isStatic(loginPort.getModifiers()));
        assertTrue(Modifier.isFinal(loginPort.getModifiers()));
        loginPort.setAccessible(true);
        var singleton = loginPort.get(null);
        assertTrue(singleton.getClass().isSynthetic());
        assertEquals(0, singleton.getClass().getDeclaredFields().length,
                "login-ready singleton must not capture capability or lifecycle state");
        var acquireLoginPort = boundary.getDeclaredMethod(
                "loginReadyPort", P6RuntimeExecutionCapability.class);
        assertTrue(Modifier.isPublic(acquireLoginPort.getModifiers()));
        assertTrue(Modifier.isStatic(acquireLoginPort.getModifiers()));
        assertSame(P7ServerAuthorizationBoundary.LoginReadyPort.class,
                acquireLoginPort.getReturnType());
        assertEquals(1, Arrays.stream(methods)
                .filter(method -> method.getName().equals("loginReadyPort")).count());
        assertThrows(NullPointerException.class,
                () -> P7ServerAuthorizationBoundary.loginReadyPort(null));
        var loginOperation = P7ServerAuthorizationBoundary.LoginReadyPort.class
                .getDeclaredMethod("onLoginReady", MinecraftServer.class, ServerPlayer.class);
        assertTrue(Modifier.isPublic(loginOperation.getModifiers()));
        assertTrue(Modifier.isAbstract(loginOperation.getModifiers()));
        assertFalse(Modifier.isStatic(loginOperation.getModifiers()));
        assertSame(void.class, loginOperation.getReturnType());
        assertEquals(2, P7ServerAuthorizationBoundary.LoginReadyPort.class
                .getDeclaredMethods().length);
        var continuationOperation = P7ServerAuthorizationBoundary.LoginReadyPort.class.getDeclaredMethod(
                "onLoginReady", MinecraftServer.class, ServerPlayer.class,
                com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataContinuation.class);
        assertTrue(continuationOperation.isDefault());
        assertFalse(Modifier.isStatic(continuationOperation.getModifiers()));

        var installed = boundary.getDeclaredField("installedRootIngress");
        assertSame(
                P7ServerAuthorizationBoundary.RootIngressPort.class,
                installed.getType());
        assertTrue(Modifier.isPrivate(installed.getModifiers()));
        assertTrue(Modifier.isStatic(installed.getModifiers()));
        assertTrue(Modifier.isVolatile(installed.getModifiers()));
        assertFalse(Modifier.isFinal(installed.getModifiers()));

        var install = boundary.getDeclaredMethod(
                "install",
                P6RuntimeExecutionCapability.class,
                P7ServerAuthorizationBoundary.RootIngressPort.class);
        assertTrue(Modifier.isPublic(install.getModifiers()));
        assertTrue(Modifier.isStatic(install.getModifiers()));
        assertEquals(void.class, install.getReturnType());
        var installProjection = boundary.getDeclaredMethod("install", P6RuntimeExecutionCapability.class,
                P7ServerAuthorizationBoundary.RootIngressPort.class,
                P7ServerAuthorizationBoundary.SyncProjectionPort.class);
        assertTrue(Modifier.isPublic(installProjection.getModifiers()));
        assertTrue(Modifier.isStatic(installProjection.getModifiers()));
        assertSame(void.class, installProjection.getReturnType());
        var installNormal = boundary.getDeclaredMethod("install", P6RuntimeExecutionCapability.class,
                P7ServerAuthorizationBoundary.RootIngressPort.class,
                P7ServerAuthorizationBoundary.SyncProjectionPort.class,
                P7ServerAuthorizationBoundary.NormalRootIngressPort.class);
        assertTrue(Modifier.isPublic(installNormal.getModifiers()));
        assertTrue(Modifier.isStatic(installNormal.getModifiers()));
        assertSame(void.class, installNormal.getReturnType());
        var prepare = boundary.getDeclaredMethod("prepareSync", MinecraftServer.class, ServerPlayer.class);
        assertFalse(Modifier.isPublic(prepare.getModifiers()));
        assertFalse(Modifier.isProtected(prepare.getModifiers()));
        assertSame(P7ServerAuthorizationBoundary.SyncCapture.class, prepare.getReturnType());

        var dispatch = boundary.getDeclaredMethod(
                "dispatch",
                MinecraftServer.class,
                ServerPlayer.class,
                int.class,
                P7ServerAuthorizationBoundary.AdvisoryTargetCheck.class);
        assertFalse(Modifier.isPublic(dispatch.getModifiers()));
        assertFalse(Modifier.isProtected(dispatch.getModifiers()));
        assertFalse(Modifier.isPrivate(dispatch.getModifiers()));
        assertTrue(Modifier.isStatic(dispatch.getModifiers()));
        assertSame(
                P7ServerAuthorizationBoundary.AdmissionDisposition.class,
                dispatch.getReturnType());

        assertEquals(
                Set.of(
                        P7ServerAuthorizationBoundary.RootIngressPort.class,
                        P7ServerAuthorizationBoundary.AdvisoryTargetCheck.class,
                        P7ServerAuthorizationBoundary.AdmissionDisposition.class,
                        P7ServerAuthorizationBoundary.TargetDisposition.class,
                        P7ServerAuthorizationBoundary.LoginReadyPort.class,
                        P7ServerAuthorizationBoundary.SyncProjectionPort.class,
                        P7ServerAuthorizationBoundary.SyncCapture.class,
                        P7ServerAuthorizationBoundary.SyncProjection.class,
                        P7ServerAuthorizationBoundary.SyncEntry.class,
                        P7ServerAuthorizationBoundary.SyncSourceState.class,
                        P7ServerAuthorizationBoundary.SyncEntryState.class,
                        P7ServerAuthorizationBoundary.SyncReason.class,
                        P7ServerAuthorizationBoundary.V0PlayWitness.class,
                        P7ServerAuthorizationBoundary.NormalRootIngressPort.class,
                        P7ServerAuthorizationBoundary.NormalIngressDisposition.class,
                        P7ServerAuthorizationBoundary.NormalFeedback.class,
                        P7ServerAuthorizationBoundary.NormalReceipt.class),
                Set.of(boundary.getDeclaredClasses()));
        assertTrue(Arrays.stream(boundary.getDeclaredClasses())
                .allMatch(type -> Modifier.isPublic(type.getModifiers())));
        var normalOperation = P7ServerAuthorizationBoundary.NormalRootIngressPort.class
                .getDeclaredMethod("authorizeAndAdmit", MinecraftServer.class, ServerPlayer.class,
                        P7ServerAuthorizationBoundary.NormalReceipt.class);
        assertTrue(Modifier.isPublic(normalOperation.getModifiers()));
        assertTrue(Modifier.isAbstract(normalOperation.getModifiers()));
        assertEquals(P7ServerAuthorizationBoundary.NormalIngressDisposition.class, normalOperation.getReturnType());
        assertEquals(1, P7ServerAuthorizationBoundary.NormalRootIngressPort.class.getDeclaredMethods().length);
        assertEquals(Set.of("defer", "matches", "complete"), Arrays.stream(
                P7ServerAuthorizationBoundary.NormalReceipt.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName).collect(Collectors.toSet()));
        assertEquals(List.of("PENDING", "NOT_READY", "CAPACITY", "COOLDOWN", "INSUFFICIENT_MANA",
                "UNAVAILABLE", "REJECTED"), Arrays.stream(P7ServerAuthorizationBoundary.NormalIngressDisposition.values())
                .map(Enum::name).toList());
        assertEquals(List.of("NOT_READY", "CAPACITY", "COOLDOWN_ACTIVE", "INSUFFICIENT_MANA",
                "RATE_LIMITED", "UNAVAILABLE"), Arrays.stream(P7ServerAuthorizationBoundary.NormalFeedback.values())
                .map(Enum::name).toList());

        var rootOperation = P7ServerAuthorizationBoundary.RootIngressPort.class
                .getDeclaredMethod(
                        "authorizeAndAdmit",
                        MinecraftServer.class,
                        ServerPlayer.class,
                        int.class,
                        P7ServerAuthorizationBoundary.AdvisoryTargetCheck.class);
        assertTrue(Modifier.isPublic(rootOperation.getModifiers()));
        assertTrue(Modifier.isAbstract(rootOperation.getModifiers()));
        assertFalse(Modifier.isStatic(rootOperation.getModifiers()));
        assertSame(
                P7ServerAuthorizationBoundary.AdmissionDisposition.class,
                rootOperation.getReturnType());
        assertEquals(
                1,
                P7ServerAuthorizationBoundary.RootIngressPort.class
                        .getDeclaredMethods()
                        .length);

        var targetOperation = P7ServerAuthorizationBoundary.AdvisoryTargetCheck.class
                .getDeclaredMethod("validate", MinecraftServer.class, ServerPlayer.class);
        assertTrue(Modifier.isPublic(targetOperation.getModifiers()));
        assertTrue(Modifier.isAbstract(targetOperation.getModifiers()));
        assertFalse(Modifier.isStatic(targetOperation.getModifiers()));
        assertSame(
                P7ServerAuthorizationBoundary.TargetDisposition.class,
                targetOperation.getReturnType());
        assertEquals(
                1,
                P7ServerAuthorizationBoundary.AdvisoryTargetCheck.class
                        .getDeclaredMethods()
                        .length);

        assertEquals(
                List.of(
                        P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED,
                        P7ServerAuthorizationBoundary.AdmissionDisposition.UNKNOWN_SKILL,
                        P7ServerAuthorizationBoundary.AdmissionDisposition.UNAUTHORIZED_INTENT,
                        P7ServerAuthorizationBoundary.AdmissionDisposition.INVALID_TARGET,
                        P7ServerAuthorizationBoundary.AdmissionDisposition.TARGET_UNAVAILABLE,
                        P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED,
                        P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE,
                        P7ServerAuthorizationBoundary.AdmissionDisposition.INTERNAL_SERVER_FAULT),
                List.of(P7ServerAuthorizationBoundary.AdmissionDisposition.values()));
        assertEquals(
                List.of(
                        P7ServerAuthorizationBoundary.TargetDisposition.VALID,
                        P7ServerAuthorizationBoundary.TargetDisposition.INVALID_TARGET,
                        P7ServerAuthorizationBoundary.TargetDisposition.TARGET_UNAVAILABLE),
                List.of(P7ServerAuthorizationBoundary.TargetDisposition.values()));
    }

    @Test
    void normalScalarReceiptRequiresItsOriginalWitnessAndCannotBeDeferredOrCompletedTwice() throws Exception {
        var identity = new P7SessionIdentity(new java.util.UUID(0, 921), 11, 3);
        var receipt = P7ServerAuthorizationBoundary.normalReceipt(identity, 17);
        var fields = Arrays.stream(P7ServerAuthorizationBoundary.NormalReceipt.class.getDeclaredFields())
                .collect(Collectors.toMap(java.lang.reflect.Field::getName, java.lang.reflect.Field::getType));
        assertEquals(java.util.Map.of("identity", P7SessionIdentity.class, "sequence", long.class,
                "deferred", boolean.class, "completed", boolean.class), fields);
        assertTrue(Arrays.stream(P7ServerAuthorizationBoundary.NormalReceipt.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())));
        assertTrue(Modifier.isPrivate(P7ServerAuthorizationBoundary.NormalReceipt.class
                .getDeclaredConstructor(P7SessionIdentity.class, long.class).getModifiers()));
        var authority = com.yo1no.gramarye.V0CatalogTestAuthority.open(4);
        var witness = scalarWitness(authority, identity);
        assertTrue(receipt.matches(authority, witness));
        assertFalse(receipt.matches(null, witness));
        assertFalse(receipt.matches(authority, null));
        var equalButDifferent = new P7SessionIdentity(identity.authenticatedPlayerId(), 11, 3);
        assertEquals(identity, equalButDifferent);
        assertFalse(receipt.matches(authority, scalarWitness(authority, equalButDifferent)),
                "equal scalars cannot relabel the original captured session object");
        var another = com.yo1no.gramarye.V0CatalogTestAuthority.open(4);
        assertFalse(receipt.matches(authority, scalarWitness(another, identity)));
        assertFalse(receipt.matches(another, witness));
        var foreign = new java.util.concurrent.atomic.AtomicReference<V0CatalogAuthority>();
        var thread = new Thread(() -> foreign.set(com.yo1no.gramarye.V0CatalogTestAuthority.open(4)));
        thread.start();
        thread.join();
        assertFalse(receipt.matches(foreign.get(), scalarWitness(foreign.get(), identity)));
        assertTrue(receipt.defer());
        assertFalse(receipt.defer());
        assertTrue(receipt.deferred());
        assertFalse(receipt.matches(authority, witness));
        receipt.complete(null, null, P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE,
                P7ServerAuthorizationBoundary.NormalFeedback.UNAVAILABLE);
        assertFalse(receipt.deferred());
        assertFalse(receipt.defer());
        receipt.complete(null, null, P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED, null);
        assertFalse(receipt.defer());
        var immediate = P7ServerAuthorizationBoundary.normalReceipt(identity, 18);
        immediate.immediateCompleted();
        assertFalse(immediate.defer());
        assertFalse(immediate.matches(authority, witness));
    }

    /** Private scalar witness construction for a component test, not authenticated PLAY evidence. */
    private static P7ServerAuthorizationBoundary.V0PlayWitness scalarWitness(
            V0CatalogAuthority authority, P7SessionIdentity identity) throws Exception {
        var type = P7ServerAuthorizationBoundary.V0PlayWitness.class;
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        var witness = constructor.newInstance();
        var authorityField = type.getDeclaredField("authority");
        var identityField = type.getDeclaredField("identity");
        authorityField.setAccessible(true);
        identityField.setAccessible(true);
        authorityField.set(witness, authority);
        identityField.set(witness, identity);
        return witness;
    }

    @Test
    void unavailableThenInstallDispatchAndDuplicateFailureOwnStaticLifecycle()
            throws Exception {
        var sentinelField = P7ServerAuthorizationBoundary.class.getDeclaredField(
                "UNAVAILABLE_ROOT_INGRESS");
        var installedField = P7ServerAuthorizationBoundary.class.getDeclaredField(
                "installedRootIngress");
        sentinelField.setAccessible(true);
        installedField.setAccessible(true);
        var productionPort = installedField.get(null);
        var projectionField = P7ServerAuthorizationBoundary.class.getDeclaredField("installedSyncProjection");
        projectionField.setAccessible(true);
        var productionProjection = projectionField.get(null);
        var normalField = P7ServerAuthorizationBoundary.class.getDeclaredField("installedNormalIngress");
        normalField.setAccessible(true);
        var productionNormal = normalField.get(null);
        var manaCapabilityField = P7NetworkComposition.class.getDeclaredField("manaCapability");
        manaCapabilityField.setAccessible(true);
        var productionCapability = manaCapabilityField.get(null);
        manaCapabilityField.set(null, null);
        installedField.set(null, sentinelField.get(null));
        var targetCalls = new AtomicInteger();
        P7ServerAuthorizationBoundary.AdvisoryTargetCheck targetCheck = (server, actor) -> {
            targetCalls.incrementAndGet();
            return P7ServerAuthorizationBoundary.TargetDisposition.VALID;
        };

        try {
            assertSame(
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE,
                    P7ServerAuthorizationBoundary.dispatch(null, null, -1, targetCheck));
            assertEquals(0, targetCalls.get());

            var missingCapability = assertThrows(
                    NullPointerException.class,
                    () -> P7ServerAuthorizationBoundary.install(null, null));
            assertEquals("capability", missingCapability.getMessage());
            assertSame(
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE,
                    P7ServerAuthorizationBoundary.dispatch(null, null, -1, targetCheck));
            assertEquals(0, targetCalls.get());

            var capability = runtimeCapability();
            var missingPort = assertThrows(
                    NullPointerException.class,
                    () -> P7ServerAuthorizationBoundary.install(capability, null));
            assertEquals("rootIngressPort", missingPort.getMessage());
            assertSame(
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE,
                    P7ServerAuthorizationBoundary.dispatch(null, null, -1, targetCheck));
            assertEquals(0, targetCalls.get());

            var firstPortCalls = new AtomicInteger();
            P7ServerAuthorizationBoundary.RootIngressPort firstPort =
                    (server, actor, slot, callback) -> {
                        firstPortCalls.incrementAndGet();
                        assertEquals(63, slot);
                        assertSame(targetCheck, callback);
                        return P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED;
                    };
            P7ServerAuthorizationBoundary.install(capability, firstPort);
            assertEquals(P7ServerAuthorizationBoundary.NormalIngressDisposition.UNAVAILABLE,
                    P7ServerAuthorizationBoundary.dispatchNormal(null, null,
                            P7ServerAuthorizationBoundary.normalReceipt(new P7SessionIdentity(
                                    new java.util.UUID(0, 922), 1, 1), 1)),
                    "legacy install must not implicitly authorize normal roots");
            var unavailableProjection = P7ServerAuthorizationBoundary.prepareSync(null, null).projection();
            assertEquals(0, unavailableProjection.sourceEpoch());
            assertEquals(P7ServerAuthorizationBoundary.SyncSourceState.UNAVAILABLE, unavailableProjection.sourceState());
            assertEquals(P7ServerAuthorizationBoundary.SyncReason.SOURCE_UNAVAILABLE, unavailableProjection.sourceReason());
            assertTrue(unavailableProjection.entries().isEmpty());
            assertSame(
                    P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED,
                    P7ServerAuthorizationBoundary.dispatch(null, null, 63, targetCheck));
            assertEquals(1, firstPortCalls.get());
            assertEquals(0, targetCalls.get());

            var replacementCalls = new AtomicInteger();
            P7ServerAuthorizationBoundary.RootIngressPort replacement =
                    (server, actor, slot, callback) -> {
                        replacementCalls.incrementAndGet();
                        return P7ServerAuthorizationBoundary.AdmissionDisposition.INTERNAL_SERVER_FAULT;
                    };
            assertThrows(
                    P7SemanticInvariantException.class,
                    () -> P7ServerAuthorizationBoundary.install(capability, replacement));
            assertSame(
                    P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED,
                    P7ServerAuthorizationBoundary.dispatch(null, null, 63, targetCheck));
            assertEquals(2, firstPortCalls.get());
            assertEquals(0, replacementCalls.get());
            assertEquals(0, targetCalls.get());
        } finally {
            installedField.set(null, productionPort);
            projectionField.set(null, productionProjection);
            normalField.set(null, productionNormal);
            manaCapabilityField.set(null, productionCapability);
        }
    }

    private static P6RuntimeExecutionCapability runtimeCapability() throws Exception {
        var accessor = P6RuntimeExecutionCapability.class.getDeclaredMethod("forRuntimeAdapter");
        accessor.setAccessible(true);
        return (P6RuntimeExecutionCapability) accessor.invoke(null);
    }
}
