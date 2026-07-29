package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class P3C4ApiGateTest {
    private static final String STORE_CLASS =
            "com.yo1no.gramarye.magic.definition.store.SkillDefinitionStore";

    @Test
    void publicModelsAndInternalConstructionSeamsHaveTheRequiredVisibility() {
        assertAll(
                () -> assertTrue(Modifier.isPublic(
                        SkillCommitPrecondition.class.getModifiers())),
                () -> assertTrue(SkillCommitPrecondition.class.isSealed()),
                () -> assertTrue(Modifier.isPublic(SkillSubmissionPlan.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(SkillSubmissionPlan.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(
                        SkillSubmissionOutcome.class.getModifiers())),
                () -> assertTrue(SkillSubmissionOutcome.class.isSealed()),
                () -> assertFalse(Modifier.isPublic(
                        SkillCommitPreconditionFactory.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SkillSubmissionOutcomeMapper.class.getModifiers())));
    }

    @Test
    void onlyPreparedOutcomeExposesAPlanAndFailuresExposeNoPartialCore() {
        assertAll(
                () -> assertTrue(hasPublicMethod(
                        SkillSubmissionOutcome.Prepared.class, "plan")),
                () -> assertFalse(hasPublicMethod(SkillSubmissionOutcome.class, "plan")),
                () -> assertNoMethods(
                        SkillSubmissionOutcome.Invalid.class,
                        "plan", "proposedDocument", "validatedDefinition", "precondition", "owner"),
                () -> assertNoMethods(
                        SkillSubmissionOutcome.Conflict.class,
                        "plan", "proposedDocument", "validatedDefinition", "precondition", "owner"),
                () -> assertNoMethods(
                        SkillSubmissionOutcome.IdentityRejected.class,
                        "plan", "proposedDocument", "validatedDefinition", "precondition", "owner", "latest"),
                () -> assertNoMethods(
                        SkillSubmissionOutcome.RevisionExhausted.class,
                        "plan", "proposedDocument", "validatedDefinition", "precondition", "owner"));
    }

    @Test
    void outcomeRecordComponentsRemainMinimalAndTyped() {
        assertAll(
                () -> assertEquals(Set.of("plan", "report"),
                        componentNames(SkillSubmissionOutcome.Prepared.class)),
                () -> assertEquals(Set.of("report"),
                        componentNames(SkillSubmissionOutcome.Invalid.class)),
                () -> assertEquals(Set.of("conflict", "report"),
                        componentNames(SkillSubmissionOutcome.Conflict.class)),
                () -> assertEquals(Set.of("rejection", "report"),
                        componentNames(SkillSubmissionOutcome.IdentityRejected.class)),
                () -> assertEquals(Set.of("latest", "report"),
                        componentNames(SkillSubmissionOutcome.RevisionExhausted.class)));
    }

    @Test
    void mapperAndPreconditionFactoryAcceptOnlyCompletedStageTokens() {
        var mapperParameters = Arrays.stream(
                        SkillSubmissionOutcomeMapper.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("from"))
                .map(Method::getParameterTypes)
                .map(types -> List.of(types[0]))
                .collect(Collectors.toSet());
        var factoryMethods = Arrays.stream(
                        SkillCommitPreconditionFactory.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("from"))
                .toList();

        assertAll(
                () -> assertEquals(Set.of(
                                List.of(DraftSubmissionPrecheck.Invalid.class),
                                List.of(SubmissionAuthorityCheck.IdentityRejected.class),
                                List.of(SubmissionAuthorityCheck.Conflict.class),
                                List.of(SubmissionPreparationCheck.class)),
                        mapperParameters),
                () -> assertEquals(1, factoryMethods.size()),
                () -> assertEquals(List.of(AuthorizedSkillState.class),
                        Arrays.asList(factoryMethods.getFirst().getParameterTypes())),
                () -> assertFalse(mapperParameters.contains(
                        List.of(DraftSubmissionPrecheck.Ready.class))),
                () -> assertFalse(mapperParameters.contains(
                        List.of(SubmissionAuthorityCheck.Passed.class))));
    }

    @Test
    void c4PublicModelsHaveNoPersistenceNetworkOrCommitSurface() {
        var types = List.of(
                SkillCommitPrecondition.class,
                SkillCommitPrecondition.ExpectedAbsent.class,
                SkillCommitPrecondition.ExpectedLatest.class,
                SkillSubmissionPlan.class,
                SkillSubmissionOutcome.class,
                SkillSubmissionOutcome.Prepared.class,
                SkillSubmissionOutcome.Invalid.class,
                SkillSubmissionOutcome.Conflict.class,
                SkillSubmissionOutcome.IdentityRejected.class,
                SkillSubmissionOutcome.RevisionExhausted.class);
        var forbiddenNames = Set.of(
                "encode", "decode", "save", "load", "write", "commit", "submit", "apply", "retry");

        assertTrue(types.stream().allMatch(type ->
                Arrays.stream(type.getDeclaredFields()).noneMatch(field ->
                        field.getType().getName().contains("Codec")
                                || field.getType().getName().contains("StreamCodec"))
                        && Arrays.stream(type.getDeclaredMethods()).noneMatch(method ->
                                method.getReturnType().getName().contains("Codec")
                                        || method.getReturnType().getName().contains("StreamCodec")
                                        || forbiddenNames.contains(method.getName().toLowerCase()))));
    }

    @Test
    void phaseLocalGateAllowsReviewedD2APrimitivesButRejectsFacadeAndLegacyTypes()
            throws Exception {
        // Reviewed D2-A primitives are legal; authenticated facade and later-phase types remain absent.
        var productionClasses = productionClassNames();
        var absentSimpleNames = List.of(
                "SkillDefinitionSubmissionService",
                "SkillSubmissionAuthorizationAdapter",
                "SkillQuotaView",
                "SkillPin");
        var presentSimpleNames = List.of(
                "SkillDefinitionStore",
                "SkillStoreCommitResult",
                "SkillStoreCommitConflict",
                "SkillQuota",
                "SkillRevisionPin",
                "SkillRetentionRootSnapshot",
                "SkillReclaimFailure",
                "SkillReclaimResult",
                "SkillReclaimReport");

        assertAll(
                () -> assertTrue(classExists(STORE_CLASS)),
                () -> assertTrue(presentSimpleNames.stream().allMatch(name ->
                        productionClasses.stream().anyMatch(className ->
                                simpleTopLevelName(className).equals(name)))),
                () -> assertTrue(absentSimpleNames.stream().noneMatch(name ->
                        productionClasses.stream().anyMatch(className ->
                                simpleTopLevelName(className).equals(name)))));
    }

    private static boolean hasPublicMethod(Class<?> type, String name) {
        return Arrays.stream(type.getMethods())
                .anyMatch(method -> method.getName().equals(name));
    }

    private static void assertNoMethods(Class<?> type, String... names) {
        var forbidden = Set.of(names);
        assertFalse(Arrays.stream(type.getMethods())
                .anyMatch(method -> forbidden.contains(method.getName())));
    }

    private static Set<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, P3C4ApiGateTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException expected) {
            return false;
        }
    }

    private static Set<String> productionClassNames() throws Exception {
        var root = projectRoot().resolve("build/classes/java/main");
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".class"))
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(name -> name.substring(0, name.length() - ".class".length()))
                    .map(name -> name.replace(java.io.File.separatorChar, '.'))
                    .collect(Collectors.toSet());
        }
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("build.gradle"))
                    && Files.isDirectory(candidate.resolve("src/main/java"))) {
                return candidate;
            }
        }
        throw new AssertionError("Unable to locate the Gradle project root");
    }

    private static String simpleTopLevelName(String className) {
        var simpleName = className.substring(className.lastIndexOf('.') + 1);
        var nestedSeparator = simpleName.indexOf('$');
        return nestedSeparator < 0 ? simpleName : simpleName.substring(0, nestedSeparator);
    }
}
