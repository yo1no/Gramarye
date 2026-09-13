package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SpawnProjectileActionExecutorTest {
    private static final ResourceLocation SPAWN_KEY =
            ResourceLocation.fromNamespaceAndPath("gramarye", "spawn_projectile");

    @Test
    void matchingTypedInvocationProjectsEverySpawnScalarExactly() {
        SpawnProjectileActionInvocation input = invocation(SPAWN_KEY);
        ProducedActionRequest produced = assertInstanceOf(
                ProducedActionRequest.class,
                new SpawnProjectileActionExecutor().execute(input));
        SpawnProjectileRequest request = assertInstanceOf(
                SpawnProjectileRequest.class, produced.request());

        assertSame(input.requestId(), request.requestId());
        assertSame(input.sourceEventId(), request.sourceEventId());
        assertEquals(input.dimension(), request.dimension());
        assertEquals(input.originX(), request.originX());
        assertEquals(input.originY(), request.originY());
        assertEquals(input.originZ(), request.originZ());
        assertEquals(input.directionXQ15(), request.directionXQ15());
        assertEquals(input.directionYQ15(), request.directionYQ15());
        assertEquals(input.directionZQ15(), request.directionZQ15());
        assertEquals(input.profileCode(), request.profileCode());
        assertEquals(input.manaCost(), request.manaCost());
        assertSame(input.compensationPolicy(), request.compensationPolicy());
    }

    @Test
    void nullCrossVariantAndWrongKeyAreClosedNoRequestResults() {
        SpawnProjectileActionExecutor executor = new SpawnProjectileActionExecutor();

        assertSame(NoActionRequest.INSTANCE, executor.execute(null));
        assertSame(NoActionRequest.INSTANCE, executor.execute(
                ActionTransactionTestFixtures.invocation()));
        assertSame(NoActionRequest.INSTANCE, executor.execute(invocation(
                ResourceLocation.fromNamespaceAndPath("gramarye", "other"))));
    }

    private static SpawnProjectileActionInvocation invocation(ResourceLocation key) {
        return new SpawnProjectileActionInvocation(
                key,
                new EffectRequestId(401L),
                new SourceEventId(401L),
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                -30_000_000.0,
                -20_000_000.0,
                29_999_999.999,
                -32_767,
                0,
                32_767,
                0,
                0L,
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION);
    }
}
