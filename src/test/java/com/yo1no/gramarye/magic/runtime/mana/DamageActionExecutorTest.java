package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class DamageActionExecutorTest {
    private static final ResourceLocation DAMAGE_KEY =
            ResourceLocation.fromNamespaceAndPath("gramarye", "damage");

    @Test
    void validInputProducesExactlyOneDamageRequestPreservingEveryField() {
        DamageActionInvocation input = invocation(25L, 7L);
        ProducedActionRequest produced = assertInstanceOf(
                ProducedActionRequest.class,
                new DamageActionExecutor().execute(input));
        DamageEffectRequest request = produced.request();

        assertSame(input.requestId(), request.requestId());
        assertSame(input.sourceEventId(), request.sourceEventId());
        assertSame(input.target(), request.target());
        assertEquals(input.magnitude(), request.magnitude());
        assertEquals(input.manaCost(), request.manaCost());
        assertSame(input.compensationPolicy(), request.compensationPolicy());
    }

    @Test
    void repeatedExecutionIsDeterministic() {
        DamageActionInvocation input = invocation(99L, 13L);
        DamageActionExecutor executor = new DamageActionExecutor();

        assertEquals(executor.execute(input), executor.execute(input));
    }

    @Test
    void invalidSupportedInputProducesTypedNoRequest() {
        DamageActionExecutor executor = new DamageActionExecutor();

        assertSame(NoActionRequest.INSTANCE, executor.execute(null));
        for (DamageActionInvocation invalid : new DamageActionInvocation[] {
            invocation(0L, 0L),
            invocation(P6EffectBounds.MAX_EFFECT_MAGNITUDE + 1L, 0L),
            invocation(1L, -1L),
            invocation(1L, P6EffectBounds.MAX_MANA_OPERATION_AMOUNT + 1L)
        }) {
            assertSame(NoActionRequest.INSTANCE, executor.execute(invalid));
        }
    }

    @Test
    void executorOutcomesAreClosedAndRejectNullOrImpossiblePayloads() {
        Set<Class<?>> permitted = Arrays.stream(
                        ActionExecutorOutcome.class.getPermittedSubclasses())
                .collect(Collectors.toSet());

        assertTrue(ActionExecutorOutcome.class.isSealed());
        assertEquals(Set.of(ProducedActionRequest.class, NoActionRequest.class), permitted);
        assertEquals(1, NoActionRequest.values().length);
        assertThrows(NullPointerException.class, () -> new ProducedActionRequest(null));
        assertThrows(NullPointerException.class, () -> new DamageActionInvocation(
                null,
                new EffectRequestId(1L),
                new SourceEventId(1L),
                target(),
                1L,
                0L,
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION));
        assertThrows(NullPointerException.class, () -> new DamageActionInvocation(
                DAMAGE_KEY,
                null,
                new SourceEventId(1L),
                target(),
                1L,
                0L,
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION));
        assertThrows(NullPointerException.class, () -> new DamageActionInvocation(
                DAMAGE_KEY,
                new EffectRequestId(1L),
                new SourceEventId(1L),
                null,
                1L,
                0L,
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION));
    }

    @Test
    void damageExecutorHasNoWorldManaOrRetainedDependency() throws IOException {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/magic/runtime/mana/"
                        + "DamageActionExecutor.java"));
        Set<Class<?>> interfaces = Set.of(DamageActionExecutor.class.getInterfaces());

        assertEquals(Set.of(ActionExecutor.class), interfaces);
        assertEquals(0, DamageActionExecutor.class.getDeclaredFields().length);
        assertEquals(1, DamageActionExecutor.class.getDeclaredMethods().length);
        for (String forbidden : Set.of(
                "ManaTransactionService",
                "ManaAccountAccess",
                "DamageEffectCommitPort",
                "net.minecraft.world",
                "RuntimeExecutionPort",
                "getData(",
                "setData(",
                ".hurt(")) {
            assertTrue(!source.contains(forbidden), forbidden);
        }
    }

    private static DamageActionInvocation invocation(long magnitude, long manaCost) {
        return new DamageActionInvocation(
                DAMAGE_KEY,
                new EffectRequestId(301L),
                new SourceEventId(302L),
                target(),
                magnitude,
                manaCost,
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION);
    }

    private static DamageTargetReference target() {
        return new DamageTargetReference(
                UUID.fromString("30000000-0000-4000-8000-000000000001"));
    }

    private static Path projectRoot() {
        for (Path candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root unavailable");
    }
}
