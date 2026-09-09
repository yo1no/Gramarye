package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.presentation.api.ClientFactoryKey;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import com.yo1no.gramarye.magic.presentation.api.ProfileCost;
import com.yo1no.gramarye.magic.presentation.api.ProfileType;
import com.yo1no.gramarye.magic.presentation.api.ProfileTypeCapabilities;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import java.io.IOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.junit.jupiter.api.Test;

/** Exact S2 ownership, public-surface, isolation, and production-composition guard. */
final class P8S2BoundaryTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path ROOT_PACKAGE = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye");
    private static final Path SERVICE_SOURCE =
            ROOT_PACKAGE.resolve("P8ServerPresentationService.java");
    private static final Path REGISTRIES_SOURCE =
            ROOT_PACKAGE.resolve("magic/api/registry/MagicRegistries.java");
    private static final Path BUILT_INS_SOURCE =
            ROOT_PACKAGE.resolve("magic/api/registry/BuiltInProfileTypes.java");
    private static final Path GRAMARYE_SOURCE = ROOT_PACKAGE.resolve("Gramarye.java");
    private static final Path PROJECTOR_SOURCE =
            ROOT_PACKAGE.resolve("P5RuntimeProjector.java");
    private static final Path RUNTIME_SOURCE =
            ROOT_PACKAGE.resolve("SkillRuntimeService.java");
    private static final Path P6_BRIDGE_SOURCE = ROOT_PACKAGE.resolve(
            "magic/runtime/mana/P6RuntimeExecutionBridge.java");
    private static final Path P6_ADAPTER_SOURCE =
            ROOT_PACKAGE.resolve("P6RuntimeExecutionPortAdapter.java");
    private static final Path HANDOFF_SOURCE =
            ROOT_PACKAGE.resolve("P8AppliedFactHandoff.java");
    private static final Path PRESENTATION_RUNTIME_SOURCE =
            ROOT_PACKAGE.resolve("P8PresentationRuntime.java");
    private static final Path SUBMISSION_SOURCE = ROOT_PACKAGE.resolve(
            "magic/definition/submission/SkillDefinitionSubmissionService.java");
    private static final Path ARCHITECTURE_SOURCE = PROJECT_ROOT.resolve(
            "docs/architecture/P5-A-server-runtime-event-kernel.md");
    private static final Path P7_LOGIN_ISOLATION_SOURCE =
            ROOT_PACKAGE.resolve("P7S4LoginManaGameTests.java");
    private static final Path LEGACY_SEMANTIC_PACKAGE =
            ROOT_PACKAGE.resolve("magic/presentation");
    private static final Path ROOT_TEST_PACKAGE = PROJECT_ROOT.resolve(
            "src/test/java/com/yo1no/gramarye");
    private static final Path LEGACY_SEMANTIC_TEST_PACKAGE =
            ROOT_TEST_PACKAGE.resolve("magic/presentation");
    private static final Path COMMON_API_PACKAGE =
            ROOT_PACKAGE.resolve("magic/presentation/api");

    private static final String REGISTRY_CLASS = Registry.class.getName();
    private static final String RESOURCE_KEY_CLASS = ResourceKey.class.getName();
    private static final String DEFERRED_REGISTER_CLASS = DeferredRegister.class.getName();
    private static final Set<String> BASE_PUBLIC_FIELDS = Set.of(
            "TRIGGER_TYPE_REGISTRY_KEY:" + RESOURCE_KEY_CLASS,
            "ACTION_TYPE_REGISTRY_KEY:" + RESOURCE_KEY_CLASS,
            "TRIGGER_TYPES:" + DEFERRED_REGISTER_CLASS,
            "ACTION_TYPES:" + DEFERRED_REGISTER_CLASS);
    private static final Set<String> BASE_PUBLIC_METHODS = Set.of(
            "register(" + IEventBus.class.getName() + ")->void",
            "triggerTypeRegistry()->" + REGISTRY_CLASS,
            "actionTypeRegistry()->" + REGISTRY_CLASS);
    private static final Set<String> PROFILE_PUBLIC_FIELDS = Set.of(
            "PROFILE_TYPE_REGISTRY_KEY:" + RESOURCE_KEY_CLASS,
            "PROFILE_TYPES:" + DEFERRED_REGISTER_CLASS);
    private static final Set<String> PROFILE_PUBLIC_METHODS =
            Set.of("profileTypeRegistry()->" + REGISTRY_CLASS);

    private static final Pattern TOP_LEVEL_TYPE = Pattern.compile(
            "(?m)^(?:public\\s+)?(?:(?:final|sealed|non-sealed|abstract)\\s+)*"
                    + "(?:class|interface|record|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern PROFILE_REGISTRY_BUILDER = Pattern.compile(
            "profileTypeRegistry\\s*=\\s*event\\.create\\(\\s*"
                    + "new RegistryBuilder<>\\(PROFILE_TYPE_REGISTRY_KEY\\)\\s*"
                    + "\\.maxId\\(63\\)\\s*"
                    + "\\.sync\\(false\\)\\s*"
                    + "\\.onBake\\(BuiltInProfileTypes::validateRegistry\\)\\s*"
                    + "\\);",
            Pattern.DOTALL);

    private static final Map<Path, Set<String>> NEW_S2_SOURCE_ROLES = Map.of(
            SERVICE_SOURCE,
            Set.of("P8ServerPresentationService"),
            BUILT_INS_SOURCE,
            Set.of(
                    "BuiltInProfileTypes",
                    "SoundProfileConfiguration",
                    "ParticleProfileConfiguration",
                    "TrailProfileConfiguration",
                    "SoundProfileType",
                    "ParticleProfileType",
                    "TrailProfileType",
                    "BuiltInProfileValidation"));
    private static final Map<Path, Set<String>> NEW_S3_SOURCE_ROLES = Map.of(
            HANDOFF_SOURCE,
            Set.of("P8AppliedFactHandoff"),
            PRESENTATION_RUNTIME_SOURCE,
            Set.of(
                    "P8PresentationOfferOutcome",
                    "P8PresentationSubmissionResult",
                    "P8ServerRuntimeDiagnosticCode",
                    "P8PresentationTransport",
                    "UnavailableP8PresentationTransport",
                    "P8RecipientIdentity",
                    "P8SelectedRecipient",
                    "P8RecipientSelection",
                    "P8EventMaterial",
                    "P8BufferedPresentation",
                    "P8Delivery",
                    "P8DeliveryAdmission",
                    "P8RecipientSelector",
                    "P8TickState"));
    private static final Map<String, Set<String>> RELOCATED_SEMANTIC_SOURCES = Map.ofEntries(
            Map.entry("AppearanceEventPatch.java", Set.of("AppearanceEventPatch")),
            Map.entry(
                    "AppearanceSemantics.java",
                    Set.of("AppearanceSemantics", "AppearanceResolution", "AppearancePatchOutcome")),
            Map.entry(
                    "EffectiveAppearance.java",
                    Set.of(
                            "EffectiveAppearance",
                            "ResolvedProfile",
                            "ProfileResolutionReason",
                            "PresentationAppearance")),
            Map.entry("PresentationBudget.java", Set.of("PresentationBudget")),
            Map.entry("PresentationCoalescing.java", Set.of("PresentationCoalescing")),
            Map.entry("PresentationCost.java", Set.of("PresentationCost")),
            Map.entry("PresentationDegradation.java", Set.of("PresentationDegradation")),
            Map.entry(
                    "PresentationEvent.java",
                    Set.of(
                            "PresentationEvent",
                            "PresentationEventKind",
                            "PresentationSourceSummary",
                            "PresentationPosition",
                            "PresentationDirection",
                            "PresentationEventCreation",
                            "AcceptedPresentationEvent",
                            "RejectedPresentationEvent",
                            "PresentationEventRejection")),
            Map.entry("PresentationLimits.java", Set.of("PresentationLimits")),
            Map.entry("PresentationOrdering.java", Set.of("PresentationOrdering")),
            Map.entry(
                    "PresentationProfileView.java",
                    Set.of("PresentationProfileView", "PresentationProfileDescriptor")),
            Map.entry("PresentationSequence.java", Set.of("PresentationSequence")));
    private static final List<Class<?>> RELOCATED_SEMANTIC_TYPES = List.of(
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
    private static final Map<Class<?>, Set<String>> RELOCATED_TEST_METHODS = Map.ofEntries(
            Map.entry(
                    AppearanceSemanticsTest.class,
                    Set.of(
                            "mergesAllFourLayersFieldwiseAndPreservesExplicitDisable",
                            "fallbackLayersContributeNothingWhileAdjacentTypedAndEventDisableLayersApply",
                            "defaultFallbackStatesSupplyNoPersistedOverrides",
                            "profileFallbackIsIndependentAndMissingDefaultDisablesOnlyItsChannel",
                            "missingAndWrongChannelModuleDefaultsHaveDistinctDisabledReasons",
                            "validPatchUsesActionAndEveryApplicableProfileRangeIntersection",
                            "invalidParameterRejectsWholePatchWithoutPartialScalarApplication",
                            "patchRejectsWrongChannelButAcceptsMissingSelectionThroughDefault",
                            "patchBoundsNullsUnknownAndAbsentCapabilityAreClosedOutcomes",
                            "absentAndValidEmptyPatchHaveDistinctNoMutationOutcomes",
                            "overCapacityValuesRejectBeforeTraversingOrCopyingTheirInput")),
            Map.entry(
                    P8S1BoundaryTest.class,
                    Set.of(
                            "exactAuthorizedCommonSubsetIsPublicAndEveryS1SemanticTypeIsPackagePrivate",
                            "productionImportsAreLimitedToApprovedPureAndImmutableValueRoles",
                            "productionHasNoRuntimeInstallationIoNetworkClientOrRawBypass",
                            "semanticStateHasNoAmbientMutableFieldOrLiveObjectRetention",
                            "eventRetainsTheExactBoundedSemanticFieldShape",
                            "currentS1SourceAndTestInventoryContainsNoGameTest")),
            Map.entry(
                    PresentationBudgetTest.class,
                    Set.of(
                            "everyScopeAcceptsZeroMinimumAndExactMaximumButRejectsOneOver",
                            "exactScopeBoundsRemainIndependent",
                            "countAndBytesAreConjunctiveAndRejectionIsAtomic",
                            "unsupportedDimensionsAndCheckedOverflowFailClosed",
                            "checkedMaximumEventFanOutFitsTheIndependentServerDeliveryBudget")),
            Map.entry(
                    PresentationCoalescingTest.class,
                    Set.of(
                            "exactIdentityRetainsEarliestSequenceAndItsSeedRegardlessOfArgumentOrder",
                            "everyAuthorityIdentityFieldParticipatesInCoalescing",
                            "identityDefensivelyCopiesAndCanonicalizesCollections",
                            "coalescingRejectsSeedMismatchAndImpossibleSequenceReuse",
                            "identityEnforcesBoundedExactValueShape")),
            Map.entry(
                    PresentationCostTest.class,
                    Set.of(
                            "costIsNonnegativeAndKeepsCountSeparateFromBytes",
                            "checkedAdditionReturnsExactOrClosedOverflow",
                            "checkedFanOutUsesBothDimensionsAndNeverAdoptsSeedOverflowRules")),
            Map.entry(
                    PresentationDegradationTest.class,
                    Set.of(
                            "stageOrderIsExactAndFinite",
                            "particleMinimumIntervalIncreaseAndSecondaryRemovalUseFixedOrder",
                            "coalescingPrecedesAndPreventsDropOfTheLaterExactIdentity",
                            "dropIsLastAndUnpressuredCandidateIsUnchanged",
                            "candidateAndConstraintBoundsRejectNegativeAndOneOver",
                            "inapplicableFrequencyStageAndUnpressuredCoalescingAreSkipped",
                            "particleOnlyFrequencySampleCanSuppressTheCurrentEmission",
                            "trailOnlySampleReductionIncreasesTheApplicableInterval",
                            "particleOnlyCandidateSkipsTheStageWithoutSuppression")),
            Map.entry(
                    PresentationEventTest.class,
                    Set.of(
                            "eventKindVocabularyIsExactAndClosed",
                            "validEventRetainsOnlyImmutableBoundedSemanticValues",
                            "serverFactoryUsesTheExactPureVisualSeed",
                            "constructionRejectsEveryBoundedSemanticCategory",
                            "positionAndDirectionAcceptExactEdgesAndRejectOneOverOrInvalidQ15",
                            "maximumLegalLayoutIsExactly897BodyAnd926Charge",
                            "eventStripsResolutionDiagnosticsAndAllNewSemanticTypesArePackagePrivate")),
            Map.entry(
                    PresentationOrderingTest.class,
                    Set.of(
                            "deliveryRetentionAndReverseDropUseEveryExactServerTieBreak",
                            "clientRetentionDropsNewestLowerPriorityAndResolvesAllFinalTies",
                            "expiryAndTrailTruncationOrderIsOldestThenChannelThenProfileId",
                            "orderingInputsAreBoundedBeforeTraversalAndRejectDuplicateOrMalformedValues")),
            Map.entry(
                    PresentationSequenceTest.class,
                    Set.of(
                            "initialAllocationIsOneAndLeavesOriginalStateImmutable",
                            "exactStaffordMixThirteenVectorsUseJavaWraparound",
                            "maximumIsAllocatedOnceThenStateIsExhaustedWithoutWrap",
                            "nonpositiveSequenceIsAnInvariantFailure")));
    private static final Map<Class<?>, String> COMMON_API_SHA256 = Map.of(
            ClientFactoryKey.class,
            "a3a55ce5c405e6010ee35204d06163a8fab4ee34b9e9a331499ddab200c4fbee",
            ProfileChannel.class,
            "ff8d950032c6e2839ea92d562266296704e3fd3eb63603be94cec94f4e001150",
            ProfileConfiguration.class,
            "d2b64a5394484f18330e5db3e116b00917fe10da23738a8a12454b3e9924d637",
            ProfileCost.class,
            "0d0ccf09a5b9c35fb58c73d3dcd00f272c47f654b2282bf3c7054106b85239e0",
            ProfileType.class,
            "3382afd978f04410d62a4e20720a82f37aa72ca1795e892aed551b193c9058f0",
            ProfileTypeCapabilities.class,
            "2d7fb7b714bb38264f06ccb60b5d4c849ee508e9f27c6a20440ace0aa200fd31");
    private static final List<Class<?>> BUILT_IN_TOP_LEVEL_TYPES = List.of(
            load("com.yo1no.gramarye.magic.api.registry.BuiltInProfileTypes"),
            load("com.yo1no.gramarye.magic.api.registry.SoundProfileConfiguration"),
            load("com.yo1no.gramarye.magic.api.registry.ParticleProfileConfiguration"),
            load("com.yo1no.gramarye.magic.api.registry.TrailProfileConfiguration"),
            load("com.yo1no.gramarye.magic.api.registry.SoundProfileType"),
            load("com.yo1no.gramarye.magic.api.registry.ParticleProfileType"),
            load("com.yo1no.gramarye.magic.api.registry.TrailProfileType"),
            load("com.yo1no.gramarye.magic.api.registry.BuiltInProfileValidation"));

    @Test
    void magicRegistriesPublicDeltaAndProfileBuilderPolicyAreExact() throws Exception {
        var publicFields = Arrays.stream(MagicRegistries.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .map(field -> field.getName() + ":" + field.getType().getName())
                .collect(Collectors.toUnmodifiableSet());
        var publicMethods = Arrays.stream(MagicRegistries.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(P8S2BoundaryTest::methodCoordinate)
                .collect(Collectors.toUnmodifiableSet());
        var fieldDelta = new HashSet<>(publicFields);
        fieldDelta.removeAll(BASE_PUBLIC_FIELDS);
        var methodDelta = new HashSet<>(publicMethods);
        methodDelta.removeAll(BASE_PUBLIC_METHODS);

        var key = MagicRegistries.class.getDeclaredField("PROFILE_TYPE_REGISTRY_KEY");
        var deferred = MagicRegistries.class.getDeclaredField("PROFILE_TYPES");
        var query = MagicRegistries.class.getDeclaredMethod("profileTypeRegistry");
        var source = read(REGISTRIES_SOURCE);

        assertAll(
                () -> assertEquals(PROFILE_PUBLIC_FIELDS, fieldDelta),
                () -> assertEquals(PROFILE_PUBLIC_METHODS, methodDelta),
                () -> assertEquals(union(BASE_PUBLIC_FIELDS, PROFILE_PUBLIC_FIELDS), publicFields),
                () -> assertEquals(
                        union(BASE_PUBLIC_METHODS, PROFILE_PUBLIC_METHODS), publicMethods),
                () -> assertTrue(Modifier.isPublic(key.getModifiers())),
                () -> assertTrue(Modifier.isStatic(key.getModifiers())),
                () -> assertTrue(Modifier.isFinal(key.getModifiers())),
                () -> assertEquals(ResourceKey.class, key.getType()),
                () -> assertTrue(key.getGenericType().getTypeName().contains(
                        "Registry<com.yo1no.gramarye.magic.presentation.api.ProfileType<?>>")),
                () -> assertTrue(Modifier.isPublic(deferred.getModifiers())),
                () -> assertTrue(Modifier.isStatic(deferred.getModifiers())),
                () -> assertTrue(Modifier.isFinal(deferred.getModifiers())),
                () -> assertEquals(DeferredRegister.class, deferred.getType()),
                () -> assertTrue(deferred.getGenericType().getTypeName().contains(
                        "ProfileType<?>")),
                () -> assertTrue(Modifier.isPublic(query.getModifiers())),
                () -> assertTrue(Modifier.isStatic(query.getModifiers())),
                () -> assertEquals(Registry.class, query.getReturnType()),
                () -> assertEquals(
                        id("profile_type"),
                        MagicRegistries.PROFILE_TYPE_REGISTRY_KEY.location()),
                () -> assertEquals(1, matches(PROFILE_REGISTRY_BUILDER, source)),
                () -> assertEquals(1, occurrences(source, "PROFILE_TYPES.register(modBus);")));
    }

    @Test
    void relocatedSemanticAndTestTopologyPreservesSixCommonApisWithoutBridge() {
        assertEquals(12, RELOCATED_SEMANTIC_SOURCES.size());
        assertEquals(0L, directJavaSourceCount(LEGACY_SEMANTIC_PACKAGE));
        assertEquals(0L, directJavaSourceCount(LEGACY_SEMANTIC_TEST_PACKAGE));
        var sourceDeclaredTypes = new LinkedHashSet<String>();
        RELOCATED_SEMANTIC_SOURCES.forEach((fileName, expectedTypes) -> {
            var current = ROOT_PACKAGE.resolve(fileName);
            var legacy = LEGACY_SEMANTIC_PACKAGE.resolve(fileName);
            assertTrue(Files.isRegularFile(current), current.toString());
            assertFalse(Files.exists(legacy), legacy.toString());
            var actualTypes = TOP_LEVEL_TYPE.matcher(read(current)).results()
                    .map(result -> result.group(1))
                    .collect(Collectors.toUnmodifiableSet());
            assertEquals(expectedTypes, actualTypes, current.toString());
            assertTrue(sourceDeclaredTypes.addAll(actualTypes), current.toString());
        });
        assertEquals(26, sourceDeclaredTypes.size());
        assertEquals(
                RELOCATED_SEMANTIC_TYPES.stream()
                        .map(Class::getSimpleName)
                        .collect(Collectors.toUnmodifiableSet()),
                sourceDeclaredTypes);
        RELOCATED_SEMANTIC_TYPES.forEach(type -> assertAll(
                () -> assertEquals("com.yo1no.gramarye", type.getPackageName()),
                () -> assertFalse(Modifier.isPublic(type.getModifiers()), type.getName()),
                () -> assertFalse(Modifier.isProtected(type.getModifiers()), type.getName())));

        assertEquals(9, RELOCATED_TEST_METHODS.size());
        var totalTestMethods = 0;
        for (var entry : RELOCATED_TEST_METHODS.entrySet()) {
            var type = entry.getKey();
            var sourceName = type.getSimpleName() + ".java";
            assertTrue(Files.isRegularFile(ROOT_TEST_PACKAGE.resolve(sourceName)), sourceName);
            assertFalse(
                    Files.exists(LEGACY_SEMANTIC_TEST_PACKAGE.resolve(sourceName)), sourceName);
            assertEquals("com.yo1no.gramarye", type.getPackageName());
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getName());
            var testMethods = Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Test.class))
                    .toList();
            assertEquals(
                    entry.getValue(),
                    testMethods.stream()
                            .map(java.lang.reflect.Method::getName)
                            .collect(Collectors.toUnmodifiableSet()),
                    type.getName());
            assertTrue(testMethods.stream().allMatch(method ->
                    method.getParameterCount() == 0 && method.getReturnType() == void.class));
            totalTestMethods += testMethods.size();
        }
        assertEquals(54, totalTestMethods);
        var helperSource = ROOT_TEST_PACKAGE.resolve("PresentationTestFixtures.java");
        assertAll(
                () -> assertTrue(Files.isRegularFile(helperSource)),
                () -> assertFalse(Files.exists(
                        LEGACY_SEMANTIC_TEST_PACKAGE.resolve("PresentationTestFixtures.java"))),
                () -> assertEquals(
                        Set.of("PresentationTestFixtures"),
                        TOP_LEVEL_TYPE.matcher(read(helperSource)).results()
                                .map(result -> result.group(1))
                                .collect(Collectors.toUnmodifiableSet())),
                () -> assertEquals(
                        "com.yo1no.gramarye", PresentationTestFixtures.class.getPackageName()),
                () -> assertFalse(Modifier.isPublic(PresentationTestFixtures.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(PresentationTestFixtures.class.getModifiers())),
                () -> assertEquals(
                        0L,
                        Arrays.stream(PresentationTestFixtures.class.getDeclaredMethods())
                                .filter(method -> method.isAnnotationPresent(Test.class))
                                .count()));

        assertEquals(6, COMMON_API_SHA256.size());
        COMMON_API_SHA256.forEach((type, expectedSha256) -> {
            var source = COMMON_API_PACKAGE.resolve(type.getSimpleName() + ".java");
            assertAll(
                    () -> assertEquals(
                            "com.yo1no.gramarye.magic.presentation.api",
                            type.getPackageName()),
                    () -> assertTrue(Modifier.isPublic(type.getModifiers()), type.getName()),
                    () -> assertEquals(expectedSha256, sha256(source), source.toString()));
            var sourceText = read(source);
            for (var semanticType : RELOCATED_SEMANTIC_TYPES) {
                assertFalse(
                        Pattern.compile("\\b" + Pattern.quote(semanticType.getSimpleName()) + "\\b")
                                .matcher(sourceText)
                                .find(),
                        source + " exposes " + semanticType.getSimpleName());
            }
        });
    }

    @Test
    void serverCatalogOwnerIsFinalPackagePrivateAndHasNoExternalMembers() {
        var type = P8ServerPresentationService.class;

        assertAll(
                () -> assertEquals("com.yo1no.gramarye", type.getPackageName()),
                () -> assertTrue(Modifier.isFinal(type.getModifiers())),
                () -> assertFalse(Modifier.isPublic(type.getModifiers())),
                () -> assertFalse(Modifier.isProtected(type.getModifiers())),
                () -> assertTrue(Arrays.stream(type.getDeclaredConstructors())
                        .noneMatch(P8S2BoundaryTest::isPublicOrProtected)),
                () -> assertTrue(Arrays.stream(type.getDeclaredMethods())
                        .noneMatch(P8S2BoundaryTest::isPublicOrProtected)),
                () -> assertTrue(Arrays.stream(type.getDeclaredFields())
                        .noneMatch(P8S2BoundaryTest::isPublicOrProtected)),
                () -> assertTrue(Arrays.stream(type.getDeclaredClasses())
                        .noneMatch(nested -> isPublicOrProtected(nested.getModifiers()))));
    }

    @Test
    void appearanceResolutionSnapshotCaptureSurfaceIsExactAndPackagePrivate()
            throws Exception {
        var nestedByName = Arrays.stream(P8ServerPresentationService.class.getDeclaredClasses())
                .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, type -> type));
        var snapshot = nestedByName.get("AppearanceResolutionSnapshot");
        var catalogSnapshot = nestedByName.get("CatalogSnapshot");

        assertTrue(snapshot != null, "missing AppearanceResolutionSnapshot");
        assertTrue(catalogSnapshot != null, "missing CatalogSnapshot");
        assertAll(
                () -> assertTrue(Modifier.isStatic(snapshot.getModifiers())),
                () -> assertTrue(Modifier.isFinal(snapshot.getModifiers())),
                () -> assertFalse(Modifier.isPublic(snapshot.getModifiers())),
                () -> assertFalse(Modifier.isProtected(snapshot.getModifiers())),
                () -> assertFalse(Modifier.isPrivate(snapshot.getModifiers())));

        var constructors = snapshot.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertAll(
                () -> assertTrue(Modifier.isPrivate(constructors[0].getModifiers())),
                () -> assertEquals(
                        List.of(catalogSnapshot),
                        Arrays.asList(constructors[0].getParameterTypes())),
                () -> assertEquals(0, constructors[0].getExceptionTypes().length));

        var fields = Arrays.stream(snapshot.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .collect(Collectors.toUnmodifiableMap(
                        java.lang.reflect.Field::getName, java.lang.reflect.Field::getType));
        assertEquals(
                Map.of("snapshot", catalogSnapshot, "profiles", PresentationProfileView.class),
                fields);
        Arrays.stream(snapshot.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .forEach(field -> assertAll(
                        () -> assertTrue(Modifier.isPrivate(field.getModifiers())),
                        () -> assertTrue(Modifier.isFinal(field.getModifiers()))));

        var snapshotMethods = Arrays.stream(snapshot.getDeclaredMethods())
                .filter(method -> !method.isSynthetic() && !method.isBridge())
                .collect(Collectors.toUnmodifiableMap(
                        java.lang.reflect.Method::getName, method -> method));
        assertEquals(Set.of("catalogGeneration", "profiles"), snapshotMethods.keySet());
        var generation = snapshotMethods.get("catalogGeneration");
        var profiles = snapshotMethods.get("profiles");
        assertAll(
                () -> assertEquals(long.class, generation.getReturnType()),
                () -> assertEquals(PresentationProfileView.class, profiles.getReturnType()),
                () -> assertEquals(0, generation.getParameterCount()),
                () -> assertEquals(0, profiles.getParameterCount()),
                () -> assertTrue(isPackagePrivate(generation.getModifiers())),
                () -> assertTrue(isPackagePrivate(profiles.getModifiers())));

        var capture = P8ServerPresentationService.class.getDeclaredMethod(
                "captureAppearanceResolutionSnapshot");
        assertTrue(capture.getGenericReturnType() instanceof ParameterizedType);
        var returnType = (ParameterizedType) capture.getGenericReturnType();
        assertAll(
                () -> assertEquals(
                        1L,
                        Arrays.stream(P8ServerPresentationService.class.getDeclaredMethods())
                                .filter(method -> method.getName().equals(
                                        "captureAppearanceResolutionSnapshot"))
                                .count()),
                () -> assertTrue(isPackagePrivate(capture.getModifiers())),
                () -> assertFalse(Modifier.isStatic(capture.getModifiers())),
                () -> assertEquals(Optional.class, capture.getReturnType()),
                () -> assertEquals(Optional.class, returnType.getRawType()),
                () -> assertEquals(
                        List.of(snapshot), Arrays.asList(returnType.getActualTypeArguments())),
                () -> assertEquals(0, capture.getParameterCount()),
                () -> assertEquals(0, capture.getExceptionTypes().length));

        var activeSnapshot = P8ServerPresentationService.class.getDeclaredField("activeSnapshot");
        assertAll(
                () -> assertEquals(catalogSnapshot, activeSnapshot.getType()),
                () -> assertTrue(Modifier.isPrivate(activeSnapshot.getModifiers())),
                () -> assertTrue(Modifier.isVolatile(activeSnapshot.getModifiers())),
                () -> assertTrue(Arrays.stream(
                                P8ServerPresentationService.class.getDeclaredFields())
                        .noneMatch(field -> field.getType() == snapshot
                                || field.getType() == PresentationProfileView.class)));
    }

    @Test
    void appliedFactObserverAndS3RuntimeExtensionUseTheExactAuthorizedOwners()
            throws Exception {
        var bridge = read(P6_BRIDGE_SOURCE);
        var adapter = read(P6_ADAPTER_SOURCE);
        var handoff = read(HANDOFF_SOURCE);
        var service = read(SERVICE_SOURCE);
        var offer = P8ServerPresentationService.class.getDeclaredMethod(
                "offerApplied",
                RuntimeEvent.class,
                RuntimeExecutionContext.class,
                P6RuntimeExecutionBridge.AppliedFact.class);
        var transportMethods = Arrays.stream(P8PresentationTransport.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic() && !method.isBridge())
                .toList();
        var packetCharge = P8PresentationTransport.class.getDeclaredMethod(
                "packetCharge",
                net.minecraft.server.level.ServerPlayer.class,
                P8RecipientIdentity.class,
                PresentationEvent.class);

        assertAll(
                () -> assertEquals(2, NEW_S3_SOURCE_ROLES.size()),
                () -> assertEquals(1, occurrences(
                        bridge, "public interface AppliedFactObserver")),
                () -> assertEquals(1, occurrences(bridge, "public record AppliedFact(")),
                () -> assertEquals(1, occurrences(bridge, "public record AppliedStep(")),
                () -> assertEquals(1, occurrences(bridge, "public enum AppliedStepKind")),
                () -> assertEquals(1, occurrences(bridge, "public enum AppliedTerminal")),
                () -> assertEquals(2, matches(
                        Pattern.compile("AppliedFactObserver\\s+observer\\s*\\)"), bridge)),
                () -> assertEquals(1, occurrences(
                        adapter,
                        "new P8AppliedFactHandoff(presentationService, event, context)")),
                () -> assertEquals(1, occurrences(
                        handoff,
                        "implements P6RuntimeExecutionBridge.AppliedFactObserver")),
                () -> assertEquals(1, occurrences(
                        handoff, "service.offerApplied(event, context, fact);")),
                () -> assertEquals(1, occurrences(
                        handoff, "catch (RuntimeException ignored)")),
                () -> assertFalse(Pattern.compile("catch\\s*\\(\\s*Error\\b")
                        .matcher(handoff).find()),
                () -> assertEquals(1, occurrences(
                        service, "P8PresentationOfferOutcome offerApplied(")),
                () -> assertEquals(
                        List.of("ACCEPTED", "DEGRADED", "DROPPED"),
                        Arrays.stream(P8PresentationOfferOutcome.values())
                                .map(Enum::name)
                                .toList()),
                () -> assertFalse(Modifier.isPublic(
                        P8PresentationOfferOutcome.class.getModifiers())),
                () -> assertEquals(P8PresentationOfferOutcome.class, offer.getReturnType()),
                () -> assertFalse(Modifier.isPublic(offer.getModifiers())),
                () -> assertFalse(Modifier.isProtected(offer.getModifiers())),
                () -> assertEquals(0, offer.getExceptionTypes().length),
                () -> assertFalse(Modifier.isPublic(
                        P8PresentationTransport.class.getModifiers())),
                () -> assertEquals(4, transportMethods.size()),
                () -> assertEquals(
                        Set.of("captureReadyIdentity", "isCurrent", "packetCharge", "submit"),
                        transportMethods.stream()
                                .map(java.lang.reflect.Method::getName)
                                .collect(Collectors.toUnmodifiableSet())),
                () -> assertEquals(OptionalInt.class, packetCharge.getReturnType()),
                () -> assertTrue(packetCharge.isDefault()),
                () -> assertEquals(0, packetCharge.getExceptionTypes().length),
                () -> assertEquals(1, occurrences(
                        read(PRESENTATION_RUNTIME_SOURCE),
                        "return OptionalInt.of(event.packetCharge());")),
                () -> assertFalse(read(PRESENTATION_RUNTIME_SOURCE).contains(
                        "P8PayloadRegistrationBridge")));
    }

    @Test
    void productionAvailabilityUsesOneRootViewAcrossTheExactConsumers() throws Exception {
        var production = SkillDefinitionSubmissionService.class.getDeclaredMethod(
                "production",
                PlayerSkillAttachmentService.class,
                SkillDefinitionStoreSubmissionPort.class,
                SkillSubmissionPolicyProvider.class,
                ProfileAvailabilityView.class);
        var runtimeCreate = SkillRuntimeService.class.getDeclaredMethod(
                "create",
                IEventBus.class,
                SkillDefinitionStoreService.class,
                SkillSubmissionPolicyProvider.class,
                ProfileAvailabilityView.class,
                P6RuntimeExecutionCapability.class,
                P8ServerPresentationService.class);
        var projector = P5RuntimeProjector.class.getDeclaredConstructor(
                ProfileAvailabilityView.class);
        var root = read(GRAMARYE_SOURCE);
        var owner = P8ServerPresentationService.create();

        var viewDeclaration = Pattern.compile(
                "var\\s+profileAvailability\\s*=\\s*"
                        + "p8ServerPresentationService\\.profileAvailabilityView\\(\\);",
                Pattern.DOTALL);
        var submissionWiring = Pattern.compile(
                "skillDefinitionSubmissionService\\s*=\\s*"
                        + "SkillDefinitionSubmissionService\\.production\\(\\s*"
                        + "playerSkillAttachmentService,\\s*"
                        + "skillDefinitionStoreService\\.submissionPort\\(\\),\\s*"
                        + "skillSubmissionPolicyProvider,\\s*profileAvailability\\s*\\);",
                Pattern.DOTALL);
        var runtimeWiring = Pattern.compile(
                "skillRuntimeService\\s*=\\s*SkillRuntimeService\\.create\\(\\s*"
                        + "NeoForge\\.EVENT_BUS,\\s*skillDefinitionStoreService,\\s*"
                        + "skillSubmissionPolicyProvider,\\s*profileAvailability,\\s*"
                        + "runtimeCapability,\\s*p8ServerPresentationService\\s*\\);",
                Pattern.DOTALL);
        var capabilityWiring = Pattern.compile(
                "var\\s+runtimeCapability\\s*=\\s*"
                        + "P6RuntimeExecutionCapability\\.forRuntimeAdapter\\(\\);",
                Pattern.DOTALL);

        assertAll(
                () -> assertTrue(Modifier.isPublic(production.getModifiers())),
                () -> assertTrue(Modifier.isStatic(production.getModifiers())),
                () -> assertEquals(
                        List.of(production),
                        Arrays.stream(SkillDefinitionSubmissionService.class.getDeclaredMethods())
                                .filter(method -> method.getName().equals("production"))
                                .toList()),
                () -> assertTrue(Modifier.isStatic(runtimeCreate.getModifiers())),
                () -> assertFalse(isPublicOrProtected(runtimeCreate)),
                () -> assertFalse(isPublicOrProtected(projector)),
                () -> assertTrue(Arrays.stream(P5RuntimeProjector.class.getDeclaredConstructors())
                        .noneMatch(constructor -> constructor.getParameterCount() == 0)),
                () -> assertSame(
                        owner.profileAvailabilityView(), owner.profileAvailabilityView()),
                () -> assertEquals(1, matches(viewDeclaration, root)),
                () -> assertEquals(1, matches(submissionWiring, root)),
                () -> assertEquals(1, matches(runtimeWiring, root)),
                () -> assertEquals(1, matches(capabilityWiring, root)),
                () -> assertEquals(1, occurrences(
                        root,
                        "P7ServerAuthorizationBoundary.loginReadyPort(runtimeCapability)")),
                () -> assertEquals(1, occurrences(
                        root,
                        "P7ServerAuthorizationBoundary.install(\n"
                                + "                runtimeCapability,")),
                () -> assertEquals(3, matches(Pattern.compile("\\bprofileAvailability\\b"), root)),
                () -> assertEquals(4, matches(Pattern.compile("\\bruntimeCapability\\b"), root)),
                () -> assertFalse(read(PROJECTOR_SOURCE).contains(
                        "ProfileAvailabilityView.unknown()")),
                () -> assertFalse(read(RUNTIME_SOURCE).contains(
                        "ProfileAvailabilityView.unknown()")),
                () -> assertFalse(read(SUBMISSION_SOURCE).contains(
                        "ProfileAvailabilityView.unknown()")));
    }

    @Test
    void exactS2AndAuthorizedS3SourcesHaveNoBypassOrLongLivedObjectRetention() {
        NEW_S2_SOURCE_ROLES.forEach((sourcePath, expectedTopLevelTypes) -> {
            assertTrue(Files.isRegularFile(sourcePath), sourcePath.toString());
            var source = read(sourcePath);
            var actualTopLevelTypes = TOP_LEVEL_TYPE.matcher(source).results()
                    .map(result -> result.group(1))
                    .collect(Collectors.toUnmodifiableSet());
            assertEquals(expectedTopLevelTypes, actualTopLevelTypes, sourcePath.toString());
            commonForbiddenSourcePatterns().forEach((role, pattern) -> assertFalse(
                    pattern.matcher(source).find(),
                    sourcePath + " contains " + role));
            if (!sourcePath.equals(SERVICE_SOURCE)) {
                s2OnlyForbiddenSourcePatterns().forEach((role, pattern) -> assertFalse(
                        pattern.matcher(source).find(),
                        sourcePath + " contains " + role));
            }
        });
        NEW_S3_SOURCE_ROLES.forEach((sourcePath, expectedTopLevelTypes) -> {
            assertTrue(Files.isRegularFile(sourcePath), sourcePath.toString());
            var source = read(sourcePath);
            var actualTopLevelTypes = TOP_LEVEL_TYPE.matcher(source).results()
                    .map(result -> result.group(1))
                    .collect(Collectors.toUnmodifiableSet());
            assertEquals(expectedTopLevelTypes, actualTopLevelTypes, sourcePath.toString());
            commonForbiddenSourcePatterns().forEach((role, pattern) -> assertFalse(
                    pattern.matcher(source).find(),
                    sourcePath + " contains " + role));
        });

        var inspectedTypes = new LinkedHashSet<Class<?>>();
        addTypeTree(P8ServerPresentationService.class, inspectedTypes);
        List.of(
                        P8PresentationOfferOutcome.class,
                        P8PresentationSubmissionResult.class,
                        P8ServerRuntimeDiagnosticCode.class,
                        P8PresentationTransport.class,
                        UnavailableP8PresentationTransport.class,
                        P8RecipientIdentity.class,
                        P8SelectedRecipient.class,
                        P8RecipientSelection.class,
                        P8EventMaterial.class,
                        P8BufferedPresentation.class,
                        P8Delivery.class,
                        P8DeliveryAdmission.class,
                        P8RecipientSelector.class,
                        P8TickState.class)
                .forEach(type -> addTypeTree(type, inspectedTypes));
        BUILT_IN_TOP_LEVEL_TYPES.forEach(type -> addTypeTree(type, inspectedTypes));

        var rawGenericUses = new ArrayList<String>();
        var retainedLiveObjects = new ArrayList<String>();
        for (var type : inspectedTypes) {
            inspectRawGenericSignatures(type, rawGenericUses);
            for (var field : type.getDeclaredFields()) {
                if (field.isSynthetic()) {
                    continue;
                }
                var fieldType = field.getType();
                var signature = field.getGenericType().getTypeName();
                if (fieldType == Object.class
                        || Throwable.class.isAssignableFrom(fieldType)
                        || signature.contains("net.minecraft.world.")
                        || signature.contains("net.minecraft.server.level.ServerPlayer")) {
                    retainedLiveObjects.add(type.getName() + "#" + field.getName()
                            + ":" + signature);
                }
            }
        }

        assertAll(
                () -> assertEquals(List.of(), rawGenericUses),
                () -> assertEquals(List.of(), retainedLiveObjects));
    }

    @Test
    void authorityAndRecoveredP7IsolationRemainExactBaseBytes() {
        assertAll(
                () -> assertEquals(28_565_604L, fileSize(ARCHITECTURE_SOURCE)),
                () -> assertEquals(
                        "119165ae2477be0d49f68a9447d566f6e36fd5baedb1ea34c3952c5cc63076d1",
                        sha256(ARCHITECTURE_SOURCE)),
                () -> assertEquals(33_649L, fileSize(P7_LOGIN_ISOLATION_SOURCE)),
                () -> assertEquals(
                        "14f4c6bec9a069216b0e9c941de44cd8bfa696014987d165a7009c5394c4d0d1",
                        sha256(P7_LOGIN_ISOLATION_SOURCE)));
    }

    private static Map<String, Pattern> commonForbiddenSourcePatterns() {
        return Map.ofEntries(
                Map.entry("unchecked suppression", Pattern.compile("@SuppressWarnings")),
                Map.entry(
                        "reflection or method-handle bypass",
                        Pattern.compile(
                                "java\\.lang\\.(?:reflect|invoke)|Class\\.forName|"
                                        + "ServiceLoader|\\.getDeclared(?:Field|Fields|Method|"
                                        + "Methods|Constructor|Constructors)\\s*\\(")),
                Map.entry(
                        "client package dependency",
                        Pattern.compile(
                                "net\\.minecraft\\.client|com\\.yo1no\\.gramarye\\.client")),
                Map.entry(
                        "P9 gameplay mutation scope",
                        Pattern.compile(
                                "\\bDamageEffectCommitPort\\b|\\bManaTransactionService\\b|"
                                        + "\\bDamageSource\\b|\\.hurt\\s*\\(")),
                Map.entry(
                        "network registration or send",
                        Pattern.compile(
                                "net\\.minecraft\\.network|PacketDistributor|PayloadRegistrar|"
                                        + "CustomPacketPayload|StreamCodec|"
                                        + "P8PayloadRegistrationBridge|"
                                        + "\\.connection\\s*\\.\\s*send\\s*\\(|"
                                        + "\\bsendTo[A-Za-z0-9_$]*\\s*\\(")),
                Map.entry(
                        "background execution owner",
                        Pattern.compile(
                                "java\\.util\\.concurrent|new\\s+Thread\\s*\\(|"
                                        + "ExecutorService|ScheduledExecutor|\\bTimer\\b")));
    }

    private static Map<String, Pattern> s2OnlyForbiddenSourcePatterns() {
        return Map.of(
                "P6 observer installation",
                Pattern.compile(
                        "\\bP6[A-Za-z0-9_$]*\\b|\\bAppliedFact[A-Za-z0-9_$]*\\b"),
                "P8-S3 offer or observer scope",
                Pattern.compile(
                        "\\bP8AppliedFactHandoff\\b|\\bP8PresentationOfferOutcome\\b|"
                                + "\\bofferApplied\\s*\\(|\\bRuntimeEvent\\b|"
                                + "\\bRuntimeExecutionContext\\b"));
    }

    private static void inspectRawGenericSignatures(
            Class<?> type, List<String> violations) {
        inspectType(type.getGenericSuperclass(), type.getName() + " extends", violations);
        for (var implemented : type.getGenericInterfaces()) {
            inspectType(implemented, type.getName() + " implements", violations);
        }
        for (var field : type.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                inspectType(
                        field.getGenericType(),
                        type.getName() + "#" + field.getName(),
                        violations);
            }
        }
        for (var constructor : type.getDeclaredConstructors()) {
            if (!constructor.isSynthetic()) {
                for (var parameter : constructor.getGenericParameterTypes()) {
                    inspectType(parameter, constructor.toString(), violations);
                }
            }
        }
        for (var method : type.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }
            inspectType(method.getGenericReturnType(), method.toString(), violations);
            for (var parameter : method.getGenericParameterTypes()) {
                inspectType(parameter, method.toString(), violations);
            }
        }
    }

    private static void inspectType(Type type, String coordinate, List<String> violations) {
        if (type == null) {
            return;
        }
        if (type instanceof Class<?> rawClass) {
            if (rawClass.getTypeParameters().length != 0) {
                violations.add(coordinate + ":" + rawClass.getTypeName());
            }
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            for (var argument : parameterized.getActualTypeArguments()) {
                inspectType(argument, coordinate, violations);
            }
            return;
        }
        if (type instanceof GenericArrayType array) {
            inspectType(array.getGenericComponentType(), coordinate, violations);
            return;
        }
        if (type instanceof WildcardType wildcard) {
            for (var bound : wildcard.getLowerBounds()) {
                inspectType(bound, coordinate, violations);
            }
            for (var bound : wildcard.getUpperBounds()) {
                inspectType(bound, coordinate, violations);
            }
        }
    }

    private static void addTypeTree(Class<?> type, Set<Class<?>> destination) {
        if (!destination.add(type)) {
            return;
        }
        for (var nested : type.getDeclaredClasses()) {
            addTypeTree(nested, destination);
        }
    }

    private static boolean isPublicOrProtected(Member member) {
        return isPublicOrProtected(member.getModifiers());
    }

    private static boolean isPublicOrProtected(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static boolean isPackagePrivate(int modifiers) {
        return !Modifier.isPublic(modifiers)
                && !Modifier.isProtected(modifiers)
                && !Modifier.isPrivate(modifiers);
    }

    private static String methodCoordinate(java.lang.reflect.Method method) {
        return method.getName()
                + "("
                + Arrays.stream(method.getParameterTypes())
                        .map(Class::getName)
                        .collect(Collectors.joining(","))
                + ")->"
                + method.getReturnType().getName();
    }

    private static <T> Set<T> union(Set<T> first, Set<T> second) {
        var union = new HashSet<>(first);
        union.addAll(second);
        return Set.copyOf(union);
    }

    private static int matches(Pattern pattern, String source) {
        return (int) pattern.matcher(source).results().count();
    }

    private static int occurrences(String source, String token) {
        var count = 0;
        var offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new AssertionError("unable to size source: " + path, exception);
        }
    }

    private static long directJavaSourceCount(Path directory) {
        try (var entries = Files.list(directory)) {
            return entries.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .count();
        } catch (IOException exception) {
            throw new AssertionError("unable to list source directory: " + directory, exception);
        }
    }

    private static String sha256(Path path) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new AssertionError("unable to hash source: " + path, exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("unable to read source: " + path, exception);
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, P8S2BoundaryTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("missing exact S2 production type " + name, exception);
        }
    }

    private static net.minecraft.resources.ResourceLocation id(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                Gramarye.MOD_ID, path);
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
}
