package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPlan;
import com.yo1no.gramarye.magic.definition.submission.SubmissionPlanTestFactory;
import com.yo1no.gramarye.magic.definition.validation.ValidatedSkillDefinition;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class P3D2ApiGateTest {
    private static final String STORE_PACKAGE =
            "com.yo1no.gramarye.magic.definition.store.";
    private static final String COMMIT_RESULT_NAME = STORE_PACKAGE + "SkillStoreCommitResult";
    private static final String COMMIT_CONFLICT_NAME = STORE_PACKAGE + "SkillStoreCommitConflict";
    private static final Set<String> RESULT_VARIANTS = Set.of(
            "Committed", "Conflict", "QuotaRejected", "CapacityRejected", "OwnerRejected");
    private static final Set<String> CONFLICT_VARIANTS = Set.of(
            "ExpectedAbsentButPresent", "ExpectedLatestButAbsent", "LatestMismatch");

    @Test
    void storeExposesOnlyReviewedCommitPinAndReclaimMutations() throws Exception {
        var commit = SkillDefinitionStore.class.getDeclaredMethod(
                "commit", SkillSubmissionPlan.class, SkillQuota.class);
        var publicMethods = Arrays.stream(SkillDefinitionStore.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertAll(
                () -> assertTrue(Modifier.isPublic(commit.getModifiers())),
                () -> assertFalse(Modifier.isStatic(commit.getModifiers())),
                () -> assertEquals(SkillStoreCommitResult.class, commit.getReturnType()),
                () -> assertEquals(Set.of(
                                "find", "latestReference", "ownerOf", "committedSkillCount",
                                "commit", "pin", "reclaim"),
                        publicMethods),
                () -> assertTrue(Arrays.stream(SkillDefinitionStore.class.getDeclaredMethods())
                        .noneMatch(method -> Set.of(
                                        "insert", "put", "remove", "retire",
                                        "unpin", "save", "load")
                                .contains(method.getName()))));
    }

    @Test
    void quotaIsTheExactImmutablePublicSealedValueModel() {
        assertAll(
                () -> assertTrue(Modifier.isPublic(SkillQuota.class.getModifiers())),
                () -> assertTrue(SkillQuota.class.isSealed()),
                () -> assertEquals(Set.of(
                                SkillQuota.Unlimited.class,
                                SkillQuota.Limited.class),
                        Set.of(SkillQuota.class.getPermittedSubclasses())),
                () -> assertTrue(SkillQuota.Unlimited.class.isEnum()),
                () -> assertArrayEquals(
                        new SkillQuota.Unlimited[] {SkillQuota.Unlimited.INSTANCE},
                        SkillQuota.Unlimited.values()),
                () -> assertComponents(
                        SkillQuota.Limited.class,
                        List.of("maxCommittedSkills"),
                        List.of(int.class)));
    }

    @Test
    void conflictVocabularyAndCommonSkillIdentityAreExact() throws Exception {
        var skillId = SkillStoreCommitConflict.class.getMethod("skillId");
        var variants = Set.of(
                SkillStoreCommitConflict.ExpectedAbsentButPresent.class,
                SkillStoreCommitConflict.ExpectedLatestButAbsent.class,
                SkillStoreCommitConflict.LatestMismatch.class);

        assertAll(
                () -> assertTrue(Modifier.isPublic(SkillStoreCommitConflict.class.getModifiers())),
                () -> assertTrue(SkillStoreCommitConflict.class.isSealed()),
                () -> assertEquals(variants,
                        Set.of(SkillStoreCommitConflict.class.getPermittedSubclasses())),
                () -> assertEquals(SkillId.class, skillId.getReturnType()),
                () -> assertTrue(Modifier.isPublic(skillId.getModifiers())),
                () -> assertComponents(
                        SkillStoreCommitConflict.ExpectedAbsentButPresent.class,
                        List.of("skillId"),
                        List.of(SkillId.class)),
                () -> assertComponents(
                        SkillStoreCommitConflict.ExpectedLatestButAbsent.class,
                        List.of("expected"),
                        List.of(com.yo1no.gramarye.magic.definition.document.SkillReference.class)),
                () -> assertComponents(
                        SkillStoreCommitConflict.LatestMismatch.class,
                        List.of("expected", "observed"),
                        List.of(
                                com.yo1no.gramarye.magic.definition.document.SkillReference.class,
                                com.yo1no.gramarye.magic.definition.document.SkillReference.class)));
    }

    @Test
    void commitResultVocabularyAndConstructionPointsAreExact() {
        var variants = Set.of(
                SkillStoreCommitResult.Committed.class,
                SkillStoreCommitResult.Conflict.class,
                SkillStoreCommitResult.QuotaRejected.class,
                SkillStoreCommitResult.CapacityRejected.class,
                SkillStoreCommitResult.OwnerRejected.class);

        assertAll(
                () -> assertTrue(Modifier.isPublic(SkillStoreCommitResult.class.getModifiers())),
                () -> assertTrue(SkillStoreCommitResult.class.isSealed()),
                () -> assertEquals(variants,
                        Set.of(SkillStoreCommitResult.class.getPermittedSubclasses())),
                () -> assertComponents(
                        SkillStoreCommitResult.Committed.class,
                        List.of("committed"),
                        List.of(com.yo1no.gramarye.magic.definition.document.SkillReference.class)),
                () -> assertComponents(
                        SkillStoreCommitResult.Conflict.class,
                        List.of("conflict"),
                        List.of(SkillStoreCommitConflict.class)),
                () -> assertComponents(
                        SkillStoreCommitResult.QuotaRejected.class,
                        List.of("skillId", "current", "maximum"),
                        List.of(SkillId.class, int.class, int.class)),
                () -> assertComponents(
                        SkillStoreCommitResult.CapacityRejected.class,
                        List.of("scope", "current", "maximum"),
                        List.of(SkillStoreCapacityScope.class, int.class, int.class)),
                () -> assertComponents(
                        SkillStoreCommitResult.OwnerRejected.class,
                        List.of("skillId"),
                        List.of(SkillId.class)));
    }

    @Test
    void storeIsTheOnlyProductionCommitResultOrConflictConstructionPoint() throws Exception {
        var sourceRoot = projectRoot().resolve("src/main/java");
        var storeSource = sourceRoot.resolve(
                "com/yo1no/gramarye/magic/definition/store/SkillDefinitionStore.java");
        var resultModelSource = sourceRoot.resolve(
                "com/yo1no/gramarye/magic/definition/store/SkillStoreCommitResult.java");
        var conflictModelSource = sourceRoot.resolve(
                "com/yo1no/gramarye/magic/definition/store/SkillStoreCommitConflict.java");
        var constructionSites = productionConstructionSites(sourceRoot);
        var constructedKinds = constructionSites.stream()
                .flatMap(site -> site.kinds().stream())
                .collect(Collectors.toSet());
        var storeText = Files.readString(storeSource);

        assertAll(
                () -> assertEquals(Set.of(storeSource), constructionSites.stream()
                        .map(ConstructionSite::source)
                        .collect(Collectors.toSet())),
                () -> assertEquals(Set.of(
                                "SkillStoreCommitResult.Committed",
                                "SkillStoreCommitResult.Conflict",
                                "SkillStoreCommitResult.QuotaRejected",
                                "SkillStoreCommitResult.CapacityRejected",
                                "SkillStoreCommitResult.OwnerRejected",
                                "SkillStoreCommitConflict.ExpectedAbsentButPresent",
                                "SkillStoreCommitConflict.ExpectedLatestButAbsent",
                                "SkillStoreCommitConflict.LatestMismatch"),
                        constructedKinds),
                // Public record canonical constructors are API shape, not production creation sites.
                () -> assertTrue(constructionSites.stream().noneMatch(site ->
                        site.source().equals(resultModelSource)
                                || site.source().equals(conflictModelSource))),
                () -> assertFalse(storeText.contains("validatedDefinition()")),
                () -> assertFalse(storeText.contains("ValidatedSkillDefinition")));
    }

    @Test
    void constructionPointScannerRecognizesReviewedJavaSpellingsAndFactoryWrappers() {
        var source = """
                import com.yo1no.gramarye.magic.definition.store.SkillStoreCommitResult.Committed;
                import static com.yo1no.gramarye.magic.definition.store.SkillStoreCommitConflict.*;
                final class Probe {
                    Object nested(Object value) {
                        return new SkillStoreCommitResult.Conflict(value);
                    }
                    Object fullyQualified(Object value) {
                        return new com.yo1no.gramarye.magic.definition.store.SkillStoreCommitResult.OwnerRejected(value);
                    }
                    Object imported(Object value) {
                        return new Committed(value);
                    }
                    Object factoryWrapper(Object value) {
                        return new ExpectedAbsentButPresent(value);
                    }
                    java.util.function.Function<Object, Object> constructorReference() {
                        return SkillStoreCommitConflict.LatestMismatch::new;
                    }
                }
                """;

        assertEquals(Set.of(
                        "SkillStoreCommitResult.Conflict",
                        "SkillStoreCommitResult.OwnerRejected",
                        "SkillStoreCommitResult.Committed",
                        "SkillStoreCommitConflict.ExpectedAbsentButPresent",
                        "SkillStoreCommitConflict.LatestMismatch"),
                constructedCommitKinds(source));
    }

    @Test
    void resultsHaveNoSecondTruthDiagnosticOrNormalExhaustionVariant() {
        var variants = Arrays.asList(SkillStoreCommitResult.class.getPermittedSubclasses());
        var forbiddenComponentTypes = Set.of(
                SkillSubmissionPlan.class,
                SkillDocument.class,
                ValidatedSkillDefinition.class,
                ValidationResult.class,
                SkillOwnerId.class,
                String.class,
                Throwable.class);

        assertAll(
                () -> assertTrue(variants.stream()
                        .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                        .noneMatch(component -> forbiddenComponentTypes.stream()
                                .anyMatch(forbidden -> forbidden.isAssignableFrom(component.getType())))),
                () -> assertTrue(variants.stream().noneMatch(type ->
                        type.getSimpleName().contains("RevisionExhausted")
                                || type.getSimpleName().contains("AlreadyCommitted")
                                || type.getSimpleName().contains("Retry"))),
                () -> assertTrue(Arrays.stream(SkillDefinitionStore.class.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .allMatch(field -> Map.class.isAssignableFrom(field.getType()))));
    }

    @Test
    void capacityScopeIsTheSolePublicVocabularyAndCanonicalMaximumIsInternal()
            throws Exception {
        var canonicalMaximum = SkillStoreCapacityScope.class.getDeclaredMethod("canonicalMaximum");

        assertAll(
                () -> assertTrue(Modifier.isPublic(SkillStoreCapacityScope.class.getModifiers())),
                () -> assertArrayEquals(new SkillStoreCapacityScope[] {
                                SkillStoreCapacityScope.OWNER_SKILL_HISTORIES,
                                SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES,
                                SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS,
                                SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS
                        },
                        SkillStoreCapacityScope.values()),
                () -> assertEquals(int.class, canonicalMaximum.getReturnType()),
                () -> assertFalse(Modifier.isPublic(canonicalMaximum.getModifiers())));
    }

    @Test
    void compiledProductionTreeContainsFinalD3ButNoP4OrCompositionSurface() throws Exception {
        var productionClasses = productionClassNames();
        var storeTypes = productionClasses.stream()
                .filter(name -> name.startsWith(STORE_PACKAGE))
                .filter(name -> !isP4A2StoreType(name))
                .map(P3D2ApiGateTest::loadWithoutInitialization)
                .toList();
        var forbiddenTopLevelTypes = Set.of(
                "SkillDefinitionSubmissionService",
                "RandomUuidSkillIdSource",
                "SkillSubmissionAuthorizationAdapter",
                "SkillPin");

        assertAll(
                () -> assertTrue(productionClasses.contains(SkillRevisionPin.class.getName())),
                () -> assertTrue(productionClasses.contains(SkillRetentionRootSnapshot.class.getName())),
                () -> assertTrue(productionClasses.contains(SkillReclaimFailure.class.getName())),
                () -> assertTrue(productionClasses.contains(SkillReclaimReport.class.getName())),
                () -> assertTrue(productionClasses.contains(SkillReclaimResult.class.getName())),
                () -> assertTrue(forbiddenTopLevelTypes.stream().noneMatch(simpleName ->
                        productionClasses.stream().anyMatch(className ->
                                simpleTopLevelName(className).equals(simpleName)))),
                () -> assertTrue(storeTypes.stream()
                        .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                        .noneMatch(method -> Set.of(
                                        "unpin", "retire", "save", "load")
                                .contains(method.getName()))),
                () -> assertTrue(storeTypes.stream().allMatch(P3D2ApiGateTest::hasNoP4Dependency)));
    }

    @Test
    void submissionPlanFactoryExistsOnlyOnTheTestClasspath() throws Exception {
        var productionClasses = productionClassNames();
        var projectRoot = projectRoot();
        var relativeFactoryPath = Path.of(
                "com/yo1no/gramarye/magic/definition/submission/SubmissionPlanTestFactory.java");

        assertAll(
                () -> assertFalse(productionClasses.contains(SubmissionPlanTestFactory.class.getName())),
                () -> assertFalse(Files.exists(
                        projectRoot.resolve("src/main/java").resolve(relativeFactoryPath))),
                () -> assertTrue(Files.isRegularFile(
                        projectRoot.resolve("src/test/java").resolve(relativeFactoryPath))),
                () -> assertEquals(Set.of("newPlan", "existingPlan"),
                        Arrays.stream(SubmissionPlanTestFactory.class.getDeclaredMethods())
                                .filter(method -> Modifier.isPublic(method.getModifiers()))
                                .map(method -> method.getName())
                                .collect(Collectors.toSet())));
    }

    private static void assertComponents(
            Class<? extends Record> type,
            List<String> expectedNames,
            List<Class<?>> expectedTypes) {
        assertAll(
                () -> assertTrue(Modifier.isPublic(type.getModifiers())),
                () -> assertEquals(expectedNames,
                        Arrays.stream(type.getRecordComponents())
                                .map(component -> component.getName())
                                .toList()),
                () -> assertEquals(expectedTypes,
                        Arrays.stream(type.getRecordComponents())
                                .map(component -> component.getType())
                                .toList()),
                () -> assertEquals(1, Arrays.stream(type.getDeclaredConstructors())
                        .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                        .count()));
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

    private static List<ConstructionSite> productionConstructionSites(Path sourceRoot)
            throws Exception {
        try (var paths = Files.walk(sourceRoot)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> new ConstructionSite(
                            path,
                            constructedCommitKinds(readSource(path))))
                    .filter(site -> !site.kinds().isEmpty())
                    .toList();
        }
    }

    private static String readSource(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new AssertionError("Unable to read production source: " + path, exception);
        }
    }

    private static Set<String> constructedCommitKinds(String source) {
        var imports = importedTypeNames(source);
        var code = withoutCommentsAndLiterals(source);
        var constructed = new LinkedHashSet<String>();
        collectConstructedKinds(
                code, imports, COMMIT_RESULT_NAME, "SkillStoreCommitResult", RESULT_VARIANTS, constructed);
        collectConstructedKinds(
                code,
                imports,
                COMMIT_CONFLICT_NAME,
                "SkillStoreCommitConflict",
                CONFLICT_VARIANTS,
                constructed);
        return Set.copyOf(constructed);
    }

    private static void collectConstructedKinds(
            String code,
            Set<String> imports,
            String outerQualifiedName,
            String outerSimpleName,
            Set<String> variants,
            Set<String> constructed) {
        var variantAlternatives = variants.stream()
                .sorted()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        var qualifiedOuter = "(?:[A-Za-z_$][A-Za-z0-9_$]*\\s*\\.\\s*)*"
                + Pattern.quote(outerSimpleName);
        var qualifiedConstruction = Pattern.compile(
                "\\bnew\\s+" + qualifiedOuter + "\\s*\\.\\s*("
                        + variantAlternatives + ")\\s*\\(");
        var qualifiedReference = Pattern.compile(
                "\\b" + qualifiedOuter + "\\s*\\.\\s*("
                        + variantAlternatives + ")\\s*::\\s*new\\b");

        collectQualifiedMatches(
                qualifiedConstruction, code, outerSimpleName, constructed);
        collectQualifiedMatches(
                qualifiedReference, code, outerSimpleName, constructed);

        for (var variant : variants) {
            if (imports.contains(outerQualifiedName + "." + variant)
                    || imports.contains(outerQualifiedName + ".*")) {
                var unqualifiedConstruction = Pattern.compile(
                        "\\bnew\\s+" + Pattern.quote(variant) + "\\s*\\(");
                var unqualifiedReference = Pattern.compile(
                        "\\b" + Pattern.quote(variant) + "\\s*::\\s*new\\b");
                if (unqualifiedConstruction.matcher(code).find()
                        || unqualifiedReference.matcher(code).find()) {
                    constructed.add(outerSimpleName + "." + variant);
                }
            }
        }
    }

    private static void collectQualifiedMatches(
            Pattern pattern,
            String code,
            String outerSimpleName,
            Set<String> constructed) {
        var matcher = pattern.matcher(code);
        while (matcher.find()) {
            constructed.add(outerSimpleName + "." + matcher.group(1));
        }
    }

    private static Set<String> importedTypeNames(String source) {
        var imports = new LinkedHashSet<String>();
        var matcher = Pattern.compile(
                        "(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_$][A-Za-z0-9_$.]*\\*?)\\s*;")
                .matcher(source);
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return Set.copyOf(imports);
    }

    private static String withoutCommentsAndLiterals(String source) {
        var sanitized = new StringBuilder(source.length());
        var state = LexicalState.CODE;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    if (current == '/' && next == '/') {
                        sanitized.append("  ");
                        index++;
                        state = LexicalState.LINE_COMMENT;
                    } else if (current == '/' && next == '*') {
                        sanitized.append("  ");
                        index++;
                        state = LexicalState.BLOCK_COMMENT;
                    } else if (current == '"') {
                        sanitized.append(' ');
                        state = LexicalState.STRING;
                    } else if (current == '\'') {
                        sanitized.append(' ');
                        state = LexicalState.CHARACTER;
                    } else {
                        sanitized.append(current);
                    }
                }
                case LINE_COMMENT -> {
                    sanitized.append(current == '\n' ? '\n' : ' ');
                    if (current == '\n') {
                        state = LexicalState.CODE;
                    }
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        sanitized.append("  ");
                        index++;
                        state = LexicalState.CODE;
                    } else {
                        sanitized.append(current == '\n' ? '\n' : ' ');
                    }
                }
                case STRING, CHARACTER -> {
                    sanitized.append(current == '\n' ? '\n' : ' ');
                    if (current == '\\' && next != '\0') {
                        sanitized.append(next == '\n' ? '\n' : ' ');
                        index++;
                    } else if ((state == LexicalState.STRING && current == '"')
                            || (state == LexicalState.CHARACTER && current == '\'')) {
                        state = LexicalState.CODE;
                    }
                }
            }
        }
        return sanitized.toString();
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

    private static Class<?> loadWithoutInitialization(String className) {
        try {
            return Class.forName(className, false, P3D2ApiGateTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Compiled production class could not be loaded: " + className, exception);
        }
    }

    private static String simpleTopLevelName(String className) {
        var simpleName = className.substring(className.lastIndexOf('.') + 1);
        var nestedSeparator = simpleName.indexOf('$');
        return nestedSeparator < 0 ? simpleName : simpleName.substring(0, nestedSeparator);
    }

    private static boolean isP4A2StoreType(String className) {
        var name = simpleTopLevelName(className);
        return name.startsWith("StorePersistence")
                || name.equals("StoreNbtFraming")
                || name.endsWith("PersistentEnvelopeV0")
                || Set.of(
                                "ImmutableStoreBlob",
                                "ImmutableHistoryBlob",
                                "ImmutableRevisionBlob")
                        .contains(name)
                || name.equals("SkillDefinitionStorePersistenceBridge");
    }

    private static boolean hasNoP4Dependency(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).allMatch(field -> isNotP4Type(field.getType()))
                && Arrays.stream(type.getDeclaredMethods()).allMatch(method ->
                        isNotP4Type(method.getReturnType())
                                && Arrays.stream(method.getParameterTypes()).allMatch(P3D2ApiGateTest::isNotP4Type))
                && Arrays.stream(type.getDeclaredConstructors()).allMatch(constructor ->
                        Arrays.stream(constructor.getParameterTypes()).allMatch(P3D2ApiGateTest::isNotP4Type));
    }

    private static boolean isNotP4Type(Class<?> type) {
        var name = type.getName();
        return !name.contains("Codec")
                && !name.contains("DynamicOps")
                && !name.contains("SavedData")
                && !name.contains("Attachment")
                && !name.startsWith("net.minecraft.")
                && !name.startsWith("net.neoforged.")
                && !name.startsWith("java.util.concurrent.");
    }

    private enum LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER
    }

    private record ConstructionSite(Path source, Set<String> kinds) {
        private ConstructionSite {
            kinds = Set.copyOf(kinds);
        }
    }
}
