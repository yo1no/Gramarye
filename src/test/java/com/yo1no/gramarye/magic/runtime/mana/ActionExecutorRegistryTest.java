package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class ActionExecutorRegistryTest {
    private static final ResourceLocation DAMAGE_KEY =
            ResourceLocation.fromNamespaceAndPath("gramarye", "damage");
    private static final ResourceLocation OTHER_KEY =
            ResourceLocation.fromNamespaceAndPath("gramarye", "other");

    @Test
    void singleRegistrationResolvesOnlyExactResourceLocationKey() {
        ActionExecutor executor = input -> NoActionRequest.INSTANCE;
        ActionExecutorRegistry registry = registry(DAMAGE_KEY, executor);

        assertEquals(1, registry.size());
        assertSame(executor, registry.find(DAMAGE_KEY).orElseThrow());
        assertTrue(registry.find(OTHER_KEY).isEmpty());
    }

    @Test
    void constructionDefensivelyCopiesAndLookupIsDeterministic() {
        ActionExecutor executor = input -> NoActionRequest.INSTANCE;
        List<ActionExecutorRegistration> registrations = new ArrayList<>();
        registrations.add(new ActionExecutorRegistration(DAMAGE_KEY, executor));
        ActionExecutorRegistry registry = new ActionExecutorRegistry(registrations);

        registrations.clear();
        registrations.add(new ActionExecutorRegistration(
                OTHER_KEY, input -> NoActionRequest.INSTANCE));

        assertEquals(1, registry.size());
        assertSame(executor, registry.find(DAMAGE_KEY).orElseThrow());
        assertSame(
                registry.find(DAMAGE_KEY).orElseThrow(),
                registry.find(DAMAGE_KEY).orElseThrow());
        assertTrue(registry.find(OTHER_KEY).isEmpty());
    }

    @Test
    void duplicateKeyFailsFast() {
        ActionExecutor first = input -> NoActionRequest.INSTANCE;
        ActionExecutor second = input -> NoActionRequest.INSTANCE;

        assertThrows(IllegalArgumentException.class, () -> new ActionExecutorRegistry(List.of(
                new ActionExecutorRegistration(DAMAGE_KEY, first),
                new ActionExecutorRegistration(DAMAGE_KEY, second))));
    }

    @Test
    void nullRegistryInputEntryKeyAndExecutorFailFast() {
        ActionExecutor executor = input -> NoActionRequest.INSTANCE;
        List<ActionExecutorRegistration> withNullEntry = new ArrayList<>();
        withNullEntry.add(null);

        assertThrows(NullPointerException.class, () -> new ActionExecutorRegistry(null));
        assertThrows(
                NullPointerException.class,
                () -> new ActionExecutorRegistry(withNullEntry));
        assertThrows(
                NullPointerException.class,
                () -> new ActionExecutorRegistration(null, executor));
        assertThrows(
                NullPointerException.class,
                () -> new ActionExecutorRegistration(DAMAGE_KEY, null));
        assertThrows(
                NullPointerException.class,
                () -> registry(DAMAGE_KEY, executor).find(null));
    }

    @Test
    void missingKeyProducesUnsupportedActionWithoutExecutorManaOrPortCalls() {
        AtomicInteger executorCalls = new AtomicInteger();
        ActionExecutorRegistry registry = registry(DAMAGE_KEY, input -> {
            executorCalls.incrementAndGet();
            return NoActionRequest.INSTANCE;
        });

        ActionDamageTransactionResult result = engine(registry).execute(
                invocation(OTHER_KEY),
                forbiddenAccount(),
                0,
                point -> { throw new AssertionError("guard must not be called"); },
                forbiddenPort());

        assertEquals(0, executorCalls.get());
        assertEquals(EffectTerminalStatus.REJECTED, result.effectResult().status());
        assertEquals(
                EffectRejectReason.UNSUPPORTED_ACTION,
                result.effectResult().rejectReason().orElseThrow());
        assertInstanceOf(ManaNotRequired.class, result.manaSummary());
        assertEquals(0, result.manaMutationCount());
    }

    @Test
    void registeredExecutorIsInvokedExactlyOncePerExecution() {
        AtomicInteger executorCalls = new AtomicInteger();
        ActionExecutorRegistry registry = registry(DAMAGE_KEY, input -> {
            executorCalls.incrementAndGet();
            return NoActionRequest.INSTANCE;
        });

        ActionDamageTransactionResult result = engine(registry).execute(
                invocation(DAMAGE_KEY),
                forbiddenAccount(),
                0,
                point -> { throw new AssertionError("guard must not be called"); },
                forbiddenPort());

        assertEquals(1, executorCalls.get());
        assertEquals(EffectTerminalStatus.REJECTED, result.effectResult().status());
        assertEquals(
                EffectRejectReason.INVALID_REQUEST,
                result.effectResult().rejectReason().orElseThrow());
        assertInstanceOf(ManaNotRequired.class, result.manaSummary());
    }

    @Test
    void registryDeclaresNoRuntimeMutationSurface() {
        Set<String> methods = Arrays.stream(ActionExecutorRegistry.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        var fields = ActionExecutorRegistry.class.getDeclaredFields();

        assertEquals(Set.of("find", "size"), methods);
        assertEquals(1, fields.length);
        assertEquals(Map.class, fields[0].getType());
        assertTrue(Modifier.isPrivate(fields[0].getModifiers()));
        assertTrue(Modifier.isFinal(fields[0].getModifiers()));
        assertFalse(Modifier.isStatic(fields[0].getModifiers()));
    }

    private static ActionExecutorRegistry registry(
            ResourceLocation key, ActionExecutor executor) {
        return new ActionExecutorRegistry(List.of(
                new ActionExecutorRegistration(key, executor)));
    }

    private static ActionDamageTransactionEngine engine(ActionExecutorRegistry registry) {
        return new ActionDamageTransactionEngine(
                registry,
                new DamageEffectResolver(),
                new EffectExecutionEngine(),
                new ManaTransactionService());
    }

    private static DamageActionInvocation invocation(ResourceLocation key) {
        return new DamageActionInvocation(
                key,
                new EffectRequestId(101L),
                new SourceEventId(202L),
                new DamageTargetReference(
                        UUID.fromString("10000000-0000-4000-8000-000000000001")),
                25L,
                5L,
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION);
    }

    private static ManaAccountAccess forbiddenAccount() {
        return new ManaAccountAccess() {
            @Override
            public boolean isLogicThread() {
                throw new AssertionError("mana account must not be accessed");
            }

            @Override
            public UUID accountId() {
                throw new AssertionError("mana account must not be accessed");
            }

            @Override
            public ManaAvailability availability() {
                throw new AssertionError("mana account must not be accessed");
            }

            @Override
            public long balance() {
                throw new AssertionError("mana account must not be accessed");
            }

            @Override
            public void writeBalance(long balance) {
                throw new AssertionError("mana account must not be accessed");
            }
        };
    }

    private static DamageEffectCommitPort forbiddenPort() {
        return new DamageEffectCommitPort() {
            @Override
            public boolean isAvailable() {
                throw new AssertionError("commit port must not be accessed");
            }

            @Override
            public EffectStepOutcome commitDamage(DamageEffectStep step) {
                throw new AssertionError("commit port must not be accessed");
            }
        };
    }
}
