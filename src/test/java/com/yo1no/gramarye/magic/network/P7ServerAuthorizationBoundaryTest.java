package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.P6RuntimeExecutionCapability;
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
    void publicSurfaceIsExactAndDispatchRemainsPackagePrivate() throws Exception {
        var boundary = P7ServerAuthorizationBoundary.class;
        var constructors = boundary.getDeclaredConstructors();
        var fields = boundary.getDeclaredFields();
        var methods = boundary.getDeclaredMethods();

        assertTrue(Modifier.isPublic(boundary.getModifiers()));
        assertTrue(Modifier.isFinal(boundary.getModifiers()));
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertEquals(3, fields.length);
        assertEquals(
                0,
                Arrays.stream(fields)
                        .filter(field -> Modifier.isPublic(field.getModifiers())
                                || Modifier.isProtected(field.getModifiers()))
                        .count());
        assertEquals(
                2,
                Arrays.stream(methods)
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .count());
        assertEquals(
                2,
                Arrays.stream(methods)
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .count());
        assertEquals(
                Set.of("dispatch", "install", "loginReadyPort"),
                Arrays.stream(methods)
                        .filter(method -> !method.isSynthetic())
                        .map(Method::getName)
                        .collect(Collectors.toSet()));
        assertEquals(
                1,
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
        assertEquals(1, P7ServerAuthorizationBoundary.LoginReadyPort.class
                .getDeclaredMethods().length);

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
                        P7ServerAuthorizationBoundary.LoginReadyPort.class),
                Set.of(boundary.getDeclaredClasses()));
        assertTrue(Arrays.stream(boundary.getDeclaredClasses())
                .allMatch(type -> Modifier.isPublic(type.getModifiers())));

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
    void unavailableThenInstallDispatchAndDuplicateFailureOwnStaticLifecycle()
            throws Exception {
        var sentinelField = P7ServerAuthorizationBoundary.class.getDeclaredField(
                "UNAVAILABLE_ROOT_INGRESS");
        var installedField = P7ServerAuthorizationBoundary.class.getDeclaredField(
                "installedRootIngress");
        sentinelField.setAccessible(true);
        installedField.setAccessible(true);
        var productionPort = installedField.get(null);
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
            manaCapabilityField.set(null, productionCapability);
        }
    }

    private static P6RuntimeExecutionCapability runtimeCapability() throws Exception {
        var accessor = P6RuntimeExecutionCapability.class.getDeclaredMethod("forRuntimeAdapter");
        accessor.setAccessible(true);
        return (P6RuntimeExecutionCapability) accessor.invoke(null);
    }
}
