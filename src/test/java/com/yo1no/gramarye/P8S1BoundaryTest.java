package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.presentation.api.ClientFactoryKey;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import com.yo1no.gramarye.magic.presentation.api.ProfileCost;
import com.yo1no.gramarye.magic.presentation.api.ProfileType;
import com.yo1no.gramarye.magic.presentation.api.ProfileTypeCapabilities;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class P8S1BoundaryTest {
    private static final String GAME_TEST_ANNOTATION = "@" + "GameTest";
    private static final Path REPOSITORY_ROOT = findRepositoryRoot();
    private static final Path PRODUCTION_ROOT = REPOSITORY_ROOT.resolve("src/main/java");
    private static final Path TEST_ROOT = REPOSITORY_ROOT.resolve("src/test/java");

    private static final Set<String> S1_PRODUCTION_SOURCES = Set.of(
            "com/yo1no/gramarye/AppearanceEventPatch.java",
            "com/yo1no/gramarye/AppearanceSemantics.java",
            "com/yo1no/gramarye/EffectiveAppearance.java",
            "com/yo1no/gramarye/PresentationBudget.java",
            "com/yo1no/gramarye/PresentationCoalescing.java",
            "com/yo1no/gramarye/PresentationCost.java",
            "com/yo1no/gramarye/PresentationDegradation.java",
            "com/yo1no/gramarye/PresentationEvent.java",
            "com/yo1no/gramarye/PresentationLimits.java",
            "com/yo1no/gramarye/PresentationOrdering.java",
            "com/yo1no/gramarye/PresentationProfileView.java",
            "com/yo1no/gramarye/PresentationSequence.java",
            "com/yo1no/gramarye/magic/presentation/api/ClientFactoryKey.java",
            "com/yo1no/gramarye/magic/presentation/api/ProfileChannel.java",
            "com/yo1no/gramarye/magic/presentation/api/ProfileConfiguration.java",
            "com/yo1no/gramarye/magic/presentation/api/ProfileCost.java",
            "com/yo1no/gramarye/magic/presentation/api/ProfileType.java",
            "com/yo1no/gramarye/magic/presentation/api/ProfileTypeCapabilities.java");

    private static final Set<String> S1_TEST_SOURCES = Set.of(
            "com/yo1no/gramarye/AppearanceSemanticsTest.java",
            "com/yo1no/gramarye/P8S1BoundaryTest.java",
            "com/yo1no/gramarye/PresentationBudgetTest.java",
            "com/yo1no/gramarye/PresentationCoalescingTest.java",
            "com/yo1no/gramarye/PresentationCostTest.java",
            "com/yo1no/gramarye/PresentationDegradationTest.java",
            "com/yo1no/gramarye/PresentationEventTest.java",
            "com/yo1no/gramarye/PresentationOrderingTest.java",
            "com/yo1no/gramarye/PresentationSequenceTest.java",
            "com/yo1no/gramarye/PresentationTestFixtures.java",
            "com/yo1no/gramarye/magic/presentation/api/ProfileApiTest.java");

    private static final Set<String> ALLOWED_IMPORTS = Set.of(
            "com.mojang.serialization.MapCodec",
            "com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy",
            "com.yo1no.gramarye.magic.definition.document.AppearanceDefinition",
            "com.yo1no.gramarye.magic.definition.document.AppearanceOverride",
            "com.yo1no.gramarye.magic.definition.document.ProfileSelection",
            "com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearance",
            "com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearanceOverride",
            "com.yo1no.gramarye.magic.limits.MagicSafetyCeilings",
            "com.yo1no.gramarye.magic.presentation.api.ProfileChannel",
            "com.yo1no.gramarye.magic.presentation.api.ProfileCost",
            "com.yo1no.gramarye.magic.presentation.api.ProfileTypeCapabilities",
            "com.yo1no.gramarye.magic.validation.ValidationContext",
            "com.yo1no.gramarye.magic.validation.ValidationResult",
            "java.nio.charset.StandardCharsets",
            "java.util.ArrayList",
            "java.util.Collections",
            "java.util.Comparator",
            "java.util.List",
            "java.util.Map",
            "java.util.Objects",
            "java.util.Optional",
            "java.util.OptionalInt",
            "java.util.OptionalLong",
            "java.util.SortedMap",
            "java.util.TreeMap",
            "java.util.UUID",
            "net.minecraft.resources.ResourceLocation");

    private static final List<Class<?>> INTERNAL_TOP_LEVEL_TYPES = List.of(
            AppearanceEventPatch.class,
            AppearanceSemantics.class,
            AppearanceResolution.class,
            AppearancePatchOutcome.class,
            EffectiveAppearance.class,
            ResolvedProfile.class,
            ProfileResolutionReason.class,
            PresentationAppearance.class,
            PresentationBudget.class,
            PresentationCoalescing.class,
            PresentationCost.class,
            PresentationDegradation.class,
            PresentationEvent.class,
            PresentationEventKind.class,
            PresentationSourceSummary.class,
            PresentationPosition.class,
            PresentationDirection.class,
            PresentationEventCreation.class,
            AcceptedPresentationEvent.class,
            RejectedPresentationEvent.class,
            PresentationEventRejection.class,
            PresentationLimits.class,
            PresentationOrdering.class,
            PresentationProfileView.class,
            PresentationProfileDescriptor.class,
            PresentationSequence.class);

    @Test
    void exactAuthorizedCommonSubsetIsPublicAndEveryS1SemanticTypeIsPackagePrivate() {
        for (var type : List.of(
                ProfileConfiguration.class,
                ProfileChannel.class,
                ClientFactoryKey.class,
                ProfileCost.class,
                ProfileTypeCapabilities.class,
                ProfileType.class)) {
            assertTrue(Modifier.isPublic(type.getModifiers()), type.getName());
        }
        for (var type : INTERNAL_TOP_LEVEL_TYPES) {
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getName());
            assertFalse(Modifier.isProtected(type.getModifiers()), type.getName());
        }
    }

    @Test
    void productionImportsAreLimitedToApprovedPureAndImmutableValueRoles() throws IOException {
        assertReviewedSourcesPresent(PRODUCTION_ROOT, S1_PRODUCTION_SOURCES);
        for (var relative : S1_PRODUCTION_SOURCES) {
            var source = Files.readString(PRODUCTION_ROOT.resolve(relative));
            var imports = source.lines()
                    .map(String::strip)
                    .filter(line -> line.startsWith("import "))
                    .map(line -> line.substring("import ".length(), line.length() - 1))
                    .collect(Collectors.toUnmodifiableSet());
            assertTrue(ALLOWED_IMPORTS.containsAll(imports), relative + " imports " + imports);
        }
    }

    @Test
    void productionHasNoRuntimeInstallationIoNetworkClientOrRawBypass() throws IOException {
        var forbidden = List.of(
                GAME_TEST_ANNOTATION,
                "@SuppressWarnings",
                "Class.forName",
                "Dynamic<",
                "JsonElement",
                "JsonObject",
                "MinecraftServer",
                "ServerPlayer",
                "PayloadRegistrar",
                "IPayloadContext",
                "IEventBus",
                "net.minecraft.client",
                "net.minecraft.network",
                "net.minecraft.server",
                "net.minecraft.world",
                "net.neoforged",
                "java.io.",
                "java.nio.file.",
                "java.lang.reflect",
                "java.util.concurrent",
                "java.util.Random",
                "ThreadLocal",
                "Thread.currentThread",
                "Math.random",
                "System.currentTimeMillis",
                "System.nanoTime",
                "Throwable",
                "RuntimeException",
                "Error");
        for (var relative : S1_PRODUCTION_SOURCES) {
            var source = Files.readString(PRODUCTION_ROOT.resolve(relative));
            for (var token : forbidden) {
                assertFalse(source.contains(token), relative + " contains forbidden token " + token);
            }
        }
    }

    @Test
    void semanticStateHasNoAmbientMutableFieldOrLiveObjectRetention() {
        for (var type : INTERNAL_TOP_LEVEL_TYPES) {
            for (var field : type.getDeclaredFields()) {
                if (!field.isSynthetic()) {
                    assertTrue(Modifier.isFinal(field.getModifiers()), type.getName() + '.' + field.getName());
                }
                var fieldType = field.getType().getName();
                var genericFieldType = field.getGenericType().getTypeName();
                assertFalse(fieldType.contains("MinecraftServer"), field.toString());
                assertFalse(fieldType.contains("ServerPlayer"), field.toString());
                assertFalse(fieldType.contains("RuntimeEvent"), field.toString());
                assertFalse(fieldType.contains("RuntimeExecutionContext"), field.toString());
                assertFalse(Throwable.class.isAssignableFrom(field.getType()), field.toString());
                assertFalse(genericFieldType.contains("java.lang.Object"), field.toString());
                assertFalse(genericFieldType.contains("com.google.gson"), field.toString());
                assertFalse(genericFieldType.contains("Dynamic"), field.toString());
                assertFalse(genericFieldType.contains("RawTree"), field.toString());
            }
        }
    }

    @Test
    void eventRetainsTheExactBoundedSemanticFieldShape() {
        assertEquals(
                Map.of(
                        "catalogGeneration", "long",
                        "kind", PresentationEventKind.class.getName(),
                        "sourceSummary", PresentationSourceSummary.class.getName(),
                        "dimension", "net.minecraft.resources.ResourceLocation",
                        "position", PresentationPosition.class.getName(),
                        "direction", PresentationDirection.class.getName(),
                        "appearance", PresentationAppearance.class.getName(),
                        "visualSeed", "long",
                        "sequence", "long",
                        "bodySize", "int"),
                List.of(PresentationEvent.class.getDeclaredFields()).stream()
                        .filter(field -> !field.isSynthetic())
                        .collect(Collectors.toUnmodifiableMap(
                                field -> field.getName(),
                                field -> field.getGenericType().getTypeName())));
    }

    @Test
    void currentS1SourceAndTestInventoryContainsNoGameTest() {
        assertReviewedSourcesPresent(PRODUCTION_ROOT, S1_PRODUCTION_SOURCES);
        assertReviewedSourcesPresent(TEST_ROOT, S1_TEST_SOURCES);
        assertEquals(
                0L,
                S1_PRODUCTION_SOURCES.stream()
                        .map(PRODUCTION_ROOT::resolve)
                        .filter(path -> read(path).contains(GAME_TEST_ANNOTATION))
                        .count());
        assertEquals(
                0L,
                S1_TEST_SOURCES.stream()
                        .map(TEST_ROOT::resolve)
                        .filter(path -> read(path).contains(GAME_TEST_ANNOTATION))
                        .count());
    }

    private static void assertReviewedSourcesPresent(Path root, Set<String> sources) {
        for (var relative : sources) {
            assertTrue(
                    Files.isRegularFile(root.resolve(relative)),
                    "missing reviewed S1 source " + relative);
        }
    }

    private static Path findRepositoryRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))
                    && Files.isDirectory(current.resolve("src/main/java"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate repository root from test working directory");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("cannot read S1 source " + path, exception);
        }
    }
}
