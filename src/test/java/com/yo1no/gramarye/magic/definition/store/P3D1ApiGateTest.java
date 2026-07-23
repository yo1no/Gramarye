package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class P3D1ApiGateTest {
    private static final String STORE_PACKAGE =
            "com.yo1no.gramarye.magic.definition.store.";

    @Test
    void storeIsTheOnlyPublicD1TypeAndHasOnlyTheReviewedReadSurface() throws Exception {
        var publicMethods = Arrays.stream(SkillDefinitionStore.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        var publicConstructors = Arrays.stream(SkillDefinitionStore.class.getDeclaredConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                .toList();

        assertAll(
                () -> assertTrue(Modifier.isPublic(SkillDefinitionStore.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(SkillDefinitionStore.class.getModifiers())),
                () -> assertEquals(Set.of(
                                "find", "latestReference", "ownerOf", "committedSkillCount", "commit"),
                        publicMethods),
                () -> assertEquals(1, publicConstructors.size()),
                () -> assertEquals(0, publicConstructors.getFirst().getParameterCount()),
                () -> assertFalse(Modifier.isPublic(StoredSkillHistory.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(StoredSkillHistory.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(SkillDefinitionStoreSnapshot.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(SkillHistorySnapshot.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(SkillRevisionSnapshot.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(SkillDefinitionStoreRestoreResult.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(SkillDefinitionStoreRestoreFailure.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(SkillStoreCapacityScope.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SkillDefinitionStore.class.getDeclaredMethod("snapshot").getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SkillDefinitionStore.class.getDeclaredMethod(
                                        "restore", SkillDefinitionStoreSnapshot.class)
                                .getModifiers())),
                () -> assertTrue(List.of(
                                SkillDefinitionStoreSnapshot.class,
                                SkillHistorySnapshot.class,
                                SkillRevisionSnapshot.class)
                        .stream()
                        .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                        .noneMatch(method -> method.getName().equals("equals")
                                || method.getName().equals("hashCode"))),
                () -> assertTrue(List.of(
                                SkillDefinitionStoreSnapshot.class,
                                SkillHistorySnapshot.class,
                                SkillRevisionSnapshot.class)
                        .stream()
                        .flatMap(type -> Arrays.stream(type.getDeclaredConstructors()))
                        .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers()))));
    }

    @Test
    void readMethodTypesAreExactAndDoNotExposeCollectionsOrRuntimeProjection() throws Exception {
        assertAll(
                () -> assertEquals(
                        Optional.class,
                        SkillDefinitionStore.class.getMethod("find", SkillReference.class).getReturnType()),
                () -> assertEquals(
                        Optional.class,
                        SkillDefinitionStore.class.getMethod("latestReference",
                                com.yo1no.gramarye.magic.api.id.SkillId.class).getReturnType()),
                () -> assertEquals(
                        Optional.class,
                        SkillDefinitionStore.class.getMethod("ownerOf",
                                com.yo1no.gramarye.magic.api.id.SkillId.class).getReturnType()),
                () -> assertEquals(
                        int.class,
                        SkillDefinitionStore.class.getMethod("committedSkillCount", SkillOwnerId.class)
                                .getReturnType()),
                () -> assertTrue(Arrays.stream(SkillDefinitionStore.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .noneMatch(method -> Map.class.isAssignableFrom(method.getReturnType())
                                || Iterable.class.isAssignableFrom(method.getReturnType())
                                || java.util.Iterator.class.isAssignableFrom(method.getReturnType())
                                || java.util.stream.Stream.class.isAssignableFrom(method.getReturnType())
                                || method.getReturnType().getSimpleName().equals("ValidatedSkillDefinition"))));
    }

    @Test
    void storeAndStoredHistoryContainOnlyOwnerAndHistoryTruth() {
        var storeFields = Arrays.stream(SkillDefinitionStore.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        var historyFields = Arrays.stream(StoredSkillHistory.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        var forbiddenFieldFragments = List.of(
                "latest",
                "highest",
                "next",
                "byowner",
                "count",
                "allocator",
                "pin",
                "quota",
                "validated");

        assertAll(
                () -> assertEquals(1, storeFields.size()),
                () -> assertTrue(Map.class.isAssignableFrom(storeFields.getFirst().getType())),
                () -> assertEquals(2, historyFields.size()),
                () -> assertTrue(historyFields.stream()
                        .anyMatch(field -> field.getType() == SkillOwnerId.class)),
                () -> assertTrue(historyFields.stream()
                        .anyMatch(field -> NavigableMap.class.isAssignableFrom(field.getType()))),
                () -> assertTrue(storeFields.stream().noneMatch(field ->
                        forbiddenFieldFragments.stream().anyMatch(fragment ->
                                field.getName().toLowerCase().contains(fragment)))),
                () -> assertTrue(historyFields.stream().noneMatch(field ->
                        forbiddenFieldFragments.stream().anyMatch(fragment ->
                                field.getName().toLowerCase().contains(fragment)))));
    }

    @Test
    void restoreResultAndFailureShapesAreTypedAndContainNoDiagnosticChannel() {
        var resultPermits = Set.of(SkillDefinitionStoreRestoreResult.class.getPermittedSubclasses());
        var failurePermits = Set.of(SkillDefinitionStoreRestoreFailure.class.getPermittedSubclasses());
        var expectedFailures = Set.of(
                SkillDefinitionStoreRestoreFailure.CapacityExceeded.class,
                SkillDefinitionStoreRestoreFailure.DuplicateSkillId.class,
                SkillDefinitionStoreRestoreFailure.EmptyHistory.class,
                SkillDefinitionStoreRestoreFailure.DuplicateRevision.class,
                SkillDefinitionStoreRestoreFailure.DocumentSkillIdMismatch.class,
                SkillDefinitionStoreRestoreFailure.DocumentRevisionMismatch.class,
                SkillDefinitionStoreRestoreFailure.UnsupportedDocumentSchema.class,
                SkillDefinitionStoreRestoreFailure.EmptyDocumentNodes.class);

        assertAll(
                () -> assertTrue(SkillDefinitionStoreRestoreResult.class.isSealed()),
                () -> assertEquals(Set.of(
                                SkillDefinitionStoreRestoreResult.Restored.class,
                                SkillDefinitionStoreRestoreResult.Rejected.class),
                        resultPermits),
                () -> assertTrue(SkillDefinitionStoreRestoreFailure.class.isSealed()),
                () -> assertEquals(expectedFailures, failurePermits),
                () -> assertTrue(expectedFailures.stream()
                        .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                        .noneMatch(component -> component.getType() == String.class
                                || Throwable.class.isAssignableFrom(component.getType())
                                || SkillDocument.class.isAssignableFrom(component.getType()))));
    }

    @Test
    void phaseLocalFullTreeGateAllowsD2ButStillRejectsD3AndCompositionTypes() throws Exception {
        // P3-D2 phase-local: D3/composition must flip individual assertions only when reviewed.
        var allProduction = productionClassNames();
        var storeTypes = allProduction.stream()
                .filter(name -> name.startsWith(STORE_PACKAGE))
                .map(P3D1ApiGateTest::loadWithoutInitialization)
                .toList();
        var forbiddenTypeDeclarations = List.of(
                "SkillDefinitionSubmissionService",
                "RandomUuidSkillIdSource",
                "SkillSubmissionAuthorizationAdapter");

        assertAll(
                () -> assertTrue(allProduction.contains(SkillDefinitionStore.class.getName())),
                () -> assertTrue(forbiddenTypeDeclarations.stream().noneMatch(name ->
                        allProduction.stream().anyMatch(className ->
                                simpleTopLevelName(className).equals(name)))),
                () -> assertTrue(storeTypes.stream().allMatch(P3D1ApiGateTest::hasOnlyD1SurfaceTypes)),
                () -> assertTrue(storeTypes.stream()
                        .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                        .noneMatch(method -> Set.of(
                                        "insert", "put", "remove", "retire",
                                        "pin", "unpin", "reclaim")
                                .contains(method.getName()))),
                () -> assertTrue(storeTypes.stream()
                        .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                        .noneMatch(method -> Modifier.isSynchronized(method.getModifiers()))));
    }

    @Test
    void successorAndOwnerIdentityApisStayAtTheirPhaseBoundaries() {
        assertAll(
                () -> assertTrue(Arrays.stream(SkillRevision.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().equals("successor")
                                && method.getReturnType() == Optional.class
                                && Modifier.isPublic(method.getModifiers()))),
                () -> assertTrue(Arrays.stream(SkillOwnerId.class.getDeclaredFields())
                        .noneMatch(field -> field.getType().getSimpleName().contains("Codec"))),
                () -> assertTrue(Arrays.stream(SkillDefinitionStore.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().equals("commit")
                                && Modifier.isPublic(method.getModifiers()))));
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

    private static Class<?> loadWithoutInitialization(String className) {
        try {
            return Class.forName(className, false, P3D1ApiGateTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Compiled production class could not be loaded: " + className, exception);
        }
    }

    private static String simpleTopLevelName(String className) {
        var simpleName = className.substring(className.lastIndexOf('.') + 1);
        var nestedSeparator = simpleName.indexOf('$');
        return nestedSeparator < 0 ? simpleName : simpleName.substring(0, nestedSeparator);
    }

    private static boolean hasOnlyD1SurfaceTypes(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).allMatch(field -> isAllowedType(field.getType()))
                && Arrays.stream(type.getDeclaredMethods()).allMatch(method ->
                        isAllowedType(method.getReturnType())
                                && Arrays.stream(method.getParameterTypes()).allMatch(P3D1ApiGateTest::isAllowedType))
                && Arrays.stream(type.getDeclaredConstructors()).allMatch(constructor ->
                        Arrays.stream(constructor.getParameterTypes()).allMatch(P3D1ApiGateTest::isAllowedType));
    }

    private static boolean isAllowedType(Class<?> type) {
        var name = type.getName();
        return !name.contains("Codec")
                && !name.contains("DynamicOps")
                && !name.contains("SavedData")
                && !name.startsWith("net.minecraft.")
                && !name.startsWith("java.util.concurrent.")
                && !name.startsWith("java.util.concurrent.locks.");
    }
}
