package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.runtime.mana.ManaAttachmentDefinitionBridge;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

final class P6S4BoundaryTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path ROOT_MAIN = MAIN_JAVA.resolve("com/yo1no/gramarye");
    private static final Path MANA_MAIN = ROOT_MAIN.resolve("magic/runtime/mana");
    private static final Path CAPABILITY_SOURCE =
            ROOT_MAIN.resolve("P6RuntimeExecutionCapability.java");
    private static final Path ADAPTER_SOURCE =
            ROOT_MAIN.resolve("P6RuntimeExecutionPortAdapter.java");
    private static final Path SERVICE_SOURCE = ROOT_MAIN.resolve("SkillRuntimeService.java");
    private static final Path VOCABULARY_SOURCE = ROOT_MAIN.resolve("P5RuntimeVocabulary.java");
    private static final Path BRIDGE_SOURCE = MANA_MAIN.resolve("P6RuntimeExecutionBridge.java");

    @Test
    void publicP6TopLevelAllowlistIsExactlyThreeWithTwoS4Additions() throws Exception {
        var declaration = Pattern.compile(
                "(?m)^public\\s+(?:(?:final|sealed|non-sealed|abstract)\\s+)*"
                        + "(?:class|interface|record|enum)\\s+"
                        + "(P6[A-Za-z0-9_$]*|ManaAttachmentDefinitionBridge)\\b");
        Set<String> publicP6Types;
        try (var paths = Files.walk(MAIN_JAVA)) {
            publicP6Types = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> declaration.matcher(read(path)).results())
                    .map(result -> result.group(1))
                    .collect(Collectors.toUnmodifiableSet());
        }

        assertAll(
                () -> assertEquals(
                        Set.of(
                                "ManaAttachmentDefinitionBridge",
                                "P6RuntimeExecutionCapability",
                                "P6RuntimeExecutionBridge"),
                        publicP6Types),
                () -> assertTrue(Modifier.isPublic(
                        ManaAttachmentDefinitionBridge.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(
                        P6RuntimeExecutionCapability.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(
                        P6RuntimeExecutionBridge.class.getModifiers())));
    }

    @Test
    void capabilityAndBridgeSurfacesMatchTheExactClosedDescriptors() throws Exception {
        var capability = P6RuntimeExecutionCapability.class;
        var bridge = P6RuntimeExecutionBridge.class;
        var execute = bridge.getDeclaredMethod(
                "execute",
                capability,
                ServerPlayer.class,
                ResourceLocation.class,
                long.class,
                long.class,
                java.util.UUID.class,
                long.class,
                long.class,
                P6RuntimeExecutionBridge.GuardPort.class);
        var nested = Arrays.stream(bridge.getDeclaredClasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toUnmodifiableSet());
        var guardMethod = P6RuntimeExecutionBridge.GuardPort.class.getDeclaredMethods();

        assertAll(
                () -> assertTrue(Modifier.isFinal(capability.getModifiers())),
                () -> assertEquals(1, capability.getDeclaredFields().length),
                () -> assertEquals(0, Arrays.stream(capability.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .count()),
                () -> assertEquals(0, Arrays.stream(capability.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .count()),
                () -> assertEquals(1, Arrays.stream(bridge.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .count()),
                () -> assertTrue(Modifier.isPublic(execute.getModifiers())),
                () -> assertTrue(Modifier.isStatic(execute.getModifiers())),
                () -> assertEquals(void.class, execute.getReturnType()),
                () -> assertEquals(0, execute.getExceptionTypes().length),
                () -> assertEquals(Set.of("GuardPort", "GuardPoint", "GuardDecision"), nested),
                () -> assertEquals(1, guardMethod.length),
                () -> assertEquals(
                        P6RuntimeExecutionBridge.GuardDecision.class,
                        guardMethod[0].getReturnType()),
                () -> assertEquals(
                        List.of(P6RuntimeExecutionBridge.GuardPoint.class, int.class),
                        List.of(guardMethod[0].getParameterTypes())));
    }

    @Test
    void productionCompositionSelectsOneStatelessS4AdapterExactlyOnce() throws Exception {
        var service = read(SERVICE_SOURCE);
        var adapter = read(ADAPTER_SOURCE);
        var executionPort = SkillRuntimeService.class.getDeclaredField("executionPort");
        var adapterFields = Arrays.asList(P6RuntimeExecutionPortAdapter.class.getDeclaredFields());
        var publicAdapterMembers = Arrays.stream(
                        P6RuntimeExecutionPortAdapter.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers()))
                .toList();

        assertAll(
                () -> assertTrue(Modifier.isFinal(
                        P6RuntimeExecutionPortAdapter.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        P6RuntimeExecutionPortAdapter.class.getModifiers())),
                () -> assertTrue(RuntimeExecutionPort.class.isAssignableFrom(
                        P6RuntimeExecutionPortAdapter.class)),
                () -> assertEquals(1, publicAdapterMembers.size()),
                () -> assertEquals("execute", publicAdapterMembers.getFirst().getName()),
                () -> assertTrue(adapterFields.stream().allMatch(field ->
                        Modifier.isPrivate(field.getModifiers())
                                && Modifier.isFinal(field.getModifiers()))),
                () -> assertTrue(Modifier.isPrivate(executionPort.getModifiers())),
                () -> assertTrue(Modifier.isFinal(executionPort.getModifiers())),
                () -> assertEquals(1, occurrences(
                        service, "new P6RuntimeExecutionPortAdapter()")),
                () -> assertEquals(0, occurrences(
                        service, "UnavailableRuntimeExecutionPort.INSTANCE")),
                () -> assertEquals(1, occurrences(
                        adapter, "P6RuntimeExecutionBridge::execute")),
                () -> assertEquals(1, occurrences(adapter, "bridgeInvoker.execute(")),
                () -> assertFalse(adapter.contains("catch (")));
    }

    @Test
    void currentProductionContentKeepsBridgeInvocationAndAllMutationsAtZero() {
        var bridge = read(BRIDGE_SOURCE);
        var adapter = read(ADAPTER_SOURCE);
        var registries = read(ROOT_MAIN.resolve("magic/api/registry/MagicRegistries.java"));

        assertAll(
                () -> assertEquals(
                        1, occurrences(bridge, "new ActionExecutorRegistry(List.of())")),
                () -> assertEquals(0, occurrences(bridge, "new ActionExecutorRegistration(")),
                () -> assertEquals(0, occurrences(bridge, "new DamageActionExecutor(")),
                () -> assertEquals(0, occurrences(registries, "ACTION_TYPES.register(\"")),
                () -> assertEquals(0, occurrences(adapter, "new P6RuntimeExecutionInput(")),
                () -> assertTrue(adapter.contains("withoutAuthorizedScalars(")),
                () -> assertTrue(adapter.contains("return Optional.empty();")),
                () -> assertEquals(1, occurrences(
                        adapter, "new RuntimePortOutcome.Completed()")),
                () -> assertEquals(1, occurrences(adapter, "RuntimeChildPlan.EMPTY")));
    }

    @Test
    void p5OwnsOneCallScopedClosedGuardWithoutChangingSchedulerOwnership() {
        var vocabulary = read(VOCABULARY_SOURCE);
        var service = read(SERVICE_SOURCE);
        var components = Arrays.stream(RuntimeExecutionContext.class.getRecordComponents())
                .collect(Collectors.toMap(
                        component -> component.getName(), component -> component.getType()));
        var guardMethods = RuntimeExecutionGuard.class.getDeclaredMethods();

        assertAll(
                () -> assertEquals(
                        RuntimeExecutionGuard.class, components.get("executionGuard")),
                () -> assertEquals(8, components.size()),
                () -> assertEquals(1, guardMethods.length),
                () -> assertEquals(
                        RuntimeExecutionGuardDecision.class,
                        guardMethods[0].getReturnType()),
                () -> assertEquals(
                        List.of("ALLOWED", "CANCELLED", "DEADLINE_EXCEEDED"),
                        Arrays.stream(RuntimeExecutionGuardDecision.values())
                                .map(Enum::name)
                                .toList()),
                () -> assertEquals(1, occurrences(
                        vocabulary, "RuntimeExecutionGuard executionGuard")),
                () -> assertEquals(1, occurrences(
                        service, "() -> runtimeExecutionGuardDecision(slot, instance, event)")),
                () -> assertTrue(service.indexOf("instance.cancellationRequested")
                        < service.lastIndexOf("return deadlineExpired(slot, event)")),
                () -> assertEquals(0, occurrences(adapterAndBridge(), "slot.queue")),
                () -> assertEquals(0, occurrences(adapterAndBridge(), "slot.leases")),
                () -> assertEquals(0, occurrences(adapterAndBridge(), ".pin(")),
                () -> assertEquals(0, occurrences(adapterAndBridge(), ".publish(")));
    }

    @Test
    void s4ProductionAddsNoDamageWorldBackgroundRandomReflectionOrSecondTruth()
            throws IOException {
        var s4Source = read(CAPABILITY_SOURCE) + read(ADAPTER_SOURCE) + read(BRIDGE_SOURCE);
        var manaSource = javaSources(MANA_MAIN).stream()
                .map(P6S4BoundaryTest::read)
                .collect(Collectors.joining("\n"));
        for (var forbidden : List.of(
                "LivingEntity",
                "DamageSource",
                "DamageType",
                ".hurt(",
                ".setBlock(",
                ".addFreshEntity(",
                "java.util.concurrent",
                "new Thread(",
                "ThreadLocal",
                "CompletableFuture",
                "parallelStream",
                "Math.random",
                "java.util.Random",
                "java.lang.reflect",
                "@SuppressWarnings",
                "catch (RuntimeException",
                "catch (Error")) {
            assertFalse(s4Source.contains(forbidden), forbidden);
        }
        assertAll(
                () -> assertEquals(1, occurrences(
                        s4Source, "new ActionDamageTransactionEngine(")),
                () -> assertEquals(1, occurrences(
                        manaSource, "for (EffectStep step : prepared.plan().steps())")),
                () -> assertEquals(1, occurrences(
                        manaSource, "private static final AttachmentType<ManaState> PLAYER_MANA")),
                () -> assertEquals(1, occurrences(manaSource, ".getData(PLAYER_MANA)")),
                () -> assertEquals(1, occurrences(manaSource, ".setData(PLAYER_MANA")));
    }

    private static String adapterAndBridge() {
        return read(ADAPTER_SOURCE) + read(BRIDGE_SOURCE);
    }

    private static List<Path> javaSources(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("unable to inspect " + path, exception);
        }
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        for (var index = source.indexOf(fragment); index >= 0;
                index = source.indexOf(fragment, index + fragment.length())) {
            count++;
        }
        return count;
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root unavailable");
    }
}
