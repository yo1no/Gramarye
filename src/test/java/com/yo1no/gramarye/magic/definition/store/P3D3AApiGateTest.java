package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPlan;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class P3D3AApiGateTest {
    private static final String STORE_PACKAGE =
            "com.yo1no.gramarye.magic.definition.store.";

    @Test
    void storeHasExactlyTheReviewedFinalD3FieldsAndPublicMethods() throws Exception {
        var histories = SkillDefinitionStore.class.getDeclaredField("histories");
        var activePinCounts = SkillDefinitionStore.class.getDeclaredField("activePinCounts");
        var instanceFields = Arrays.stream(SkillDefinitionStore.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        var publicMethods = Arrays.stream(SkillDefinitionStore.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();
        var pin = SkillDefinitionStore.class.getDeclaredMethod("pin", SkillReference.class);
        var reclaim = SkillDefinitionStore.class.getDeclaredMethod(
                "reclaim", SkillRetentionRootSnapshot.class);
        var pinReturn = assertInstanceOf(ParameterizedType.class, pin.getGenericReturnType());

        assertAll(
                () -> assertEquals(2, instanceFields.size()),
                () -> assertMapField(histories, SkillId.class, StoredSkillHistory.class),
                () -> assertMapField(activePinCounts, SkillReference.class, Integer.class),
                () -> assertFalse(Modifier.isVolatile(activePinCounts.getModifiers())),
                () -> assertEquals(7, publicMethods.size()),
                () -> assertEquals(Set.of(
                                "find", "latestReference", "ownerOf", "committedSkillCount",
                                "commit", "pin", "reclaim"),
                        publicMethods.stream()
                                .map(method -> method.getName())
                                .collect(Collectors.toSet())),
                () -> assertTrue(Modifier.isPublic(pin.getModifiers())),
                () -> assertFalse(Modifier.isStatic(pin.getModifiers())),
                () -> assertEquals(Optional.class, pinReturn.getRawType()),
                () -> assertEquals(List.of(SkillRevisionPin.class),
                        Arrays.asList(pinReturn.getActualTypeArguments())),
                () -> assertTrue(Modifier.isPublic(reclaim.getModifiers())),
                () -> assertFalse(Modifier.isStatic(reclaim.getModifiers())),
                () -> assertEquals(SkillReclaimResult.class, reclaim.getReturnType()),
                () -> assertEquals(Optional.class,
                        SkillDefinitionStore.class.getMethod("find", SkillReference.class)
                                .getReturnType()),
                () -> assertEquals(SkillStoreCommitResult.class,
                        SkillDefinitionStore.class.getMethod(
                                        "commit", SkillSubmissionPlan.class, SkillQuota.class)
                                .getReturnType()));
    }

    @Test
    void constructionPathsInitializeOnlyHistoriesAndTransientPins() throws Exception {
        var constructors = SkillDefinitionStore.class.getDeclaredConstructors();
        var empty = SkillDefinitionStore.class.getDeclaredConstructor();
        var restore = SkillDefinitionStore.class.getDeclaredConstructor(Map.class);

        assertAll(
                () -> assertEquals(2, constructors.length),
                () -> assertTrue(Modifier.isPublic(empty.getModifiers())),
                () -> assertTrue(Modifier.isPrivate(restore.getModifiers())),
                () -> assertEquals(List.of(Map.class), Arrays.asList(restore.getParameterTypes())),
                () -> assertFalse(Modifier.isPublic(
                        SkillDefinitionStore.class.getDeclaredMethod("snapshot").getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SkillDefinitionStore.class.getDeclaredMethod(
                                        "restore", SkillDefinitionStoreSnapshot.class)
                                .getModifiers())));
    }

    @Test
    void pinHandleHasTheExactStoreBoundIdempotentShape() throws Exception {
        var store = SkillRevisionPin.class.getDeclaredField("store");
        var reference = SkillRevisionPin.class.getDeclaredField("reference");
        var closed = SkillRevisionPin.class.getDeclaredField("closed");
        var fields = Arrays.asList(SkillRevisionPin.class.getDeclaredFields());
        var constructor = SkillRevisionPin.class.getDeclaredConstructor(
                SkillDefinitionStore.class, SkillReference.class);
        var methods = Arrays.asList(SkillRevisionPin.class.getDeclaredMethods());

        assertAll(
                () -> assertTrue(Modifier.isPublic(SkillRevisionPin.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(SkillRevisionPin.class.getModifiers())),
                () -> assertTrue(AutoCloseable.class.isAssignableFrom(SkillRevisionPin.class)),
                () -> assertEquals(Set.of(AutoCloseable.class),
                        Set.of(SkillRevisionPin.class.getInterfaces())),
                () -> assertEquals(3, fields.size()),
                () -> assertPrivateFinalField(store, SkillDefinitionStore.class),
                () -> assertPrivateFinalField(reference, SkillReference.class),
                () -> assertEquals(boolean.class, closed.getType()),
                () -> assertTrue(Modifier.isPrivate(closed.getModifiers())),
                () -> assertFalse(Modifier.isStatic(closed.getModifiers())),
                () -> assertFalse(Modifier.isFinal(closed.getModifiers())),
                () -> assertFalse(Modifier.isVolatile(closed.getModifiers())),
                () -> assertFalse(Modifier.isPublic(constructor.getModifiers())),
                () -> assertFalse(Modifier.isProtected(constructor.getModifiers())),
                () -> assertFalse(Modifier.isPrivate(constructor.getModifiers())),
                () -> assertEquals(1, SkillRevisionPin.class.getDeclaredConstructors().length),
                () -> assertEquals(3, methods.size()),
                () -> assertTrue(methods.stream()
                        .allMatch(method -> Modifier.isPublic(method.getModifiers()))),
                () -> assertEquals(Set.of("reference", "isClosed", "close"),
                        methods.stream()
                                .map(method -> method.getName())
                                .collect(Collectors.toSet())),
                () -> assertEquals(SkillReference.class,
                        SkillRevisionPin.class.getMethod("reference").getReturnType()),
                () -> assertEquals(boolean.class,
                        SkillRevisionPin.class.getMethod("isClosed").getReturnType()),
                () -> assertEquals(void.class,
                        SkillRevisionPin.class.getMethod("close").getReturnType()),
                () -> assertTrue(Arrays.stream(SkillRevisionPin.class.getDeclaredMethods())
                        .noneMatch(method -> Set.of(
                                        "equals", "hashCode", "toString", "finalize",
                                        "store", "detach", "transfer", "reopen")
                                .contains(method.getName()))),
                () -> assertTrue(Arrays.stream(SkillRevisionPin.class.getDeclaredFields())
                        .noneMatch(field -> field.getType().getSimpleName().contains("Codec"))));
    }

    @Test
    void releaseAndOverflowSeamsArePackagePrivateAndMinimal() throws Exception {
        var release = SkillDefinitionStore.class.getDeclaredMethod(
                "releasePin", SkillReference.class);
        var checkedIncrement = SkillDefinitionStore.class.getDeclaredMethod(
                "checkedIncrementPinCount", int.class);

        assertAll(
                () -> assertPackagePrivate(release.getModifiers()),
                () -> assertFalse(Modifier.isStatic(release.getModifiers())),
                () -> assertEquals(void.class, release.getReturnType()),
                () -> assertPackagePrivate(checkedIncrement.getModifiers()),
                () -> assertTrue(Modifier.isStatic(checkedIncrement.getModifiers())),
                () -> assertEquals(int.class, checkedIncrement.getReturnType()),
                () -> assertTrue(Arrays.stream(SkillDefinitionStore.class.getDeclaredMethods())
                        .noneMatch(method -> Modifier.isPublic(method.getModifiers())
                                && Set.of("unpin", "releasePin", "retire", "delete")
                                        .contains(method.getName()))));
    }

    @Test
    void snapshotAndStoredHistoryShapesContainNoPinOrReclaimState() {
        var historyFields = Arrays.stream(StoredSkillHistory.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();

        assertAll(
                () -> assertListField(
                        SkillDefinitionStoreSnapshot.class.getDeclaredField("histories"),
                        SkillHistorySnapshot.class),
                () -> assertEquals(1,
                        SkillDefinitionStoreSnapshot.class.getDeclaredFields().length),
                () -> assertEquals(Set.of("skillId", "owner", "revisions"),
                        Arrays.stream(SkillHistorySnapshot.class.getDeclaredFields())
                                .map(Field::getName)
                                .collect(Collectors.toSet())),
                () -> assertEquals(SkillId.class,
                        SkillHistorySnapshot.class.getDeclaredField("skillId").getType()),
                () -> assertEquals(SkillOwnerId.class,
                        SkillHistorySnapshot.class.getDeclaredField("owner").getType()),
                () -> assertListField(
                        SkillHistorySnapshot.class.getDeclaredField("revisions"),
                        SkillRevisionSnapshot.class),
                () -> assertEquals(Set.of("revision", "document"),
                        Arrays.stream(SkillRevisionSnapshot.class.getDeclaredFields())
                                .map(Field::getName)
                                .collect(Collectors.toSet())),
                () -> assertEquals(SkillRevision.class,
                        SkillRevisionSnapshot.class.getDeclaredField("revision").getType()),
                () -> assertEquals(SkillDocument.class,
                        SkillRevisionSnapshot.class.getDeclaredField("document").getType()),
                () -> assertEquals(2, historyFields.size()),
                () -> assertEquals(Set.of("owner", "revisions"), historyFields.stream()
                        .map(field -> field.getName())
                        .collect(Collectors.toSet())),
                () -> assertTrue(historyFields.stream()
                        .anyMatch(field -> field.getType() == SkillOwnerId.class)),
                () -> assertTrue(Arrays.stream(StoredSkillHistory.class.getDeclaredMethods())
                        .noneMatch(method -> method.getName().equals("reclaim"))),
                () -> assertTrue(Arrays.stream(StoredSkillHistory.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().equals("retainRevisions")
                                && method.getReturnType() == StoredSkillHistory.class
                                && !Modifier.isPublic(method.getModifiers()))));
    }

    @Test
    void handleConstructionIsCentralizedInStoreProductionSource() throws Exception {
        var sourceRoot = projectRoot().resolve("src/main/java");
        var storeSource = sourceRoot.resolve(
                "com/yo1no/gramarye/magic/definition/store/SkillDefinitionStore.java");
        var sites = pinConstructionSites(sourceRoot);
        var sanctionedFactory = SkillDefinitionStore.class.getDeclaredMethod(
                "pin", SkillReference.class);
        var publicFactories = productionClasses().stream()
                .map(P3D3AApiGateTest::loadWithoutInitialization)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> containsType(
                        method.getGenericReturnType(), SkillRevisionPin.class))
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(List.of(storeSource), sites),
                () -> assertEquals(1, pinConstructionCount(readSource(storeSource))),
                () -> assertEquals(Set.of(sanctionedFactory), publicFactories));
    }

    @Test
    void sourceKeepsPinPublicationBeforeMutationAndCommitIndependentOfPins() throws Exception {
        var source = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStore.java"));
        var pinStart = source.indexOf("public Optional<SkillRevisionPin> pin");
        var pinEnd = source.indexOf("public SkillReclaimResult reclaim", pinStart);
        var pinSource = source.substring(pinStart, pinEnd);
        var commitStart = source.indexOf("public SkillStoreCommitResult commit");
        var commitEnd = source.indexOf("SkillDefinitionStoreSnapshot snapshot()", commitStart);
        var helperStart = source.indexOf("private SkillStoreCommitResult commitExpectedAbsent");
        var helperEnd = source.indexOf("private int globalRetainedRevisionCount", helperStart);
        var commitSource = source.substring(commitStart, commitEnd)
                + source.substring(helperStart, helperEnd);
        var existence = pinSource.indexOf("history.revisions().containsKey");
        var checked = pinSource.indexOf("checkedIncrementPinCount");
        var handle = pinSource.indexOf("new SkillRevisionPin");
        var optional = pinSource.indexOf("Optional.of(handle)");
        var put = pinSource.indexOf("activePinCounts.put");
        var returned = pinSource.indexOf("return result");

        assertAll(
                () -> assertTrue(0 <= existence && existence < checked),
                () -> assertTrue(checked < handle),
                () -> assertTrue(handle < optional),
                () -> assertTrue(optional < put),
                () -> assertTrue(put < returned),
                () -> assertFalse(pinSource.contains("activePinCounts.merge")),
                () -> assertFalse(pinSource.contains("Math.addExact")),
                () -> assertFalse(pinSource.contains("histories.put")),
                () -> assertFalse(pinSource.contains("histories.remove")),
                () -> assertFalse(commitSource.contains("activePinCounts")));
    }

    @Test
    void phaseLocalProductionTreeContainsFinalD3ButNoP4Surface() throws Exception {
        var productionClasses = productionClasses();
        var forbiddenTopLevelTypes = Set.of(
                "SkillDefinitionSubmissionService",
                "RootProvider");
        var storeTypes = productionClasses.stream()
                .filter(name -> name.startsWith(STORE_PACKAGE))
                .map(P3D3AApiGateTest::loadWithoutInitialization)
                .toList();

        assertAll(
                () -> assertTrue(Set.of(
                                "SkillRetentionRootSnapshot",
                                "SkillReclaimFailure",
                                "SkillReclaimResult",
                                "SkillReclaimReport")
                        .stream()
                        .allMatch(simpleName -> productionClasses.stream().anyMatch(className ->
                                simpleTopLevelName(className).equals(simpleName)))),
                () -> assertTrue(forbiddenTopLevelTypes.stream().noneMatch(simpleName ->
                        productionClasses.stream().anyMatch(className ->
                                simpleTopLevelName(className).equals(simpleName)))),
                () -> assertTrue(productionClasses.stream()
                        .map(P3D3AApiGateTest::simpleTopLevelName)
                        .map(String::toLowerCase)
                        .noneMatch(name -> name.contains("rootprovider")
                                || name.contains("rootcollector"))),
                () -> assertTrue(storeTypes.stream()
                        .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                        .noneMatch(method -> Set.of(
                                        "retire", "delete", "save", "load", "setDirty")
                                .contains(method.getName()))),
                () -> assertTrue(storeTypes.stream().allMatch(P3D3AApiGateTest::hasNoP4Dependency)));
    }

    private static void assertMapField(Field field, Class<?> key, Class<?> value) {
        var genericType = assertInstanceOf(ParameterizedType.class, field.getGenericType());
        assertAll(
                () -> assertEquals(Map.class, field.getType()),
                () -> assertEquals(Map.class, genericType.getRawType()),
                () -> assertEquals(List.of(key, value),
                        Arrays.asList(genericType.getActualTypeArguments())),
                () -> assertTrue(Modifier.isPrivate(field.getModifiers())),
                () -> assertTrue(Modifier.isFinal(field.getModifiers())));
    }

    private static void assertPrivateFinalField(Field field, Class<?> type) {
        assertAll(
                () -> assertEquals(type, field.getType()),
                () -> assertTrue(Modifier.isPrivate(field.getModifiers())),
                () -> assertFalse(Modifier.isStatic(field.getModifiers())),
                () -> assertTrue(Modifier.isFinal(field.getModifiers())));
    }

    private static void assertListField(Field field, Class<?> elementType) {
        var genericType = assertInstanceOf(ParameterizedType.class, field.getGenericType());
        assertAll(
                () -> assertEquals(List.class, field.getType()),
                () -> assertEquals(List.class, genericType.getRawType()),
                () -> assertEquals(List.of(elementType),
                        Arrays.asList(genericType.getActualTypeArguments())));
    }

    private static void assertPackagePrivate(int modifiers) {
        assertAll(
                () -> assertFalse(Modifier.isPublic(modifiers)),
                () -> assertFalse(Modifier.isProtected(modifiers)),
                () -> assertFalse(Modifier.isPrivate(modifiers)));
    }

    private static List<Path> pinConstructionSites(Path sourceRoot) throws Exception {
        try (var paths = Files.walk(sourceRoot)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsPinConstruction(readSource(path)))
                    .sorted()
                    .toList();
        }
    }

    private static boolean containsPinConstruction(String source) {
        return pinConstructionCount(source) > 0;
    }

    private static int pinConstructionCount(String source) {
        var code = withoutCommentsAndLiterals(source);
        var qualifiedType = "(?:[A-Za-z_$][A-Za-z0-9_$]*\\s*\\.\\s*)*"
                + Pattern.quote("SkillRevisionPin");
        var constructors = Pattern.compile("\\bnew\\s+" + qualifiedType + "\\s*\\(")
                .matcher(code)
                .results()
                .count();
        var references = Pattern.compile("\\b" + qualifiedType + "\\s*::\\s*new\\b")
                .matcher(code)
                .results()
                .count();
        return Math.toIntExact(constructors + references);
    }

    private static String readSource(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new AssertionError("Unable to read production source: " + path, exception);
        }
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

    private static Set<String> productionClasses() throws Exception {
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
            return Class.forName(className, false, P3D3AApiGateTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Compiled production class could not be loaded: " + className, exception);
        }
    }

    private static String simpleTopLevelName(String className) {
        var simpleName = className.substring(className.lastIndexOf('.') + 1);
        var nestedSeparator = simpleName.indexOf('$');
        return nestedSeparator < 0 ? simpleName : simpleName.substring(0, nestedSeparator);
    }

    private static boolean hasNoP4Dependency(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                        .allMatch(field -> isNotP4Type(field.getGenericType()))
                && Arrays.stream(type.getDeclaredMethods()).allMatch(method ->
                        isNotP4Type(method.getGenericReturnType())
                                && Arrays.stream(method.getGenericParameterTypes())
                                        .allMatch(P3D3AApiGateTest::isNotP4Type))
                && Arrays.stream(type.getDeclaredConstructors()).allMatch(constructor ->
                        Arrays.stream(constructor.getGenericParameterTypes())
                                .allMatch(P3D3AApiGateTest::isNotP4Type));
    }

    private static boolean isNotP4Type(Type type) {
        return isNotP4Type(type, new HashSet<>());
    }

    private static boolean isNotP4Type(Type type, Set<Type> visited) {
        if (!visited.add(type)) {
            return true;
        }
        return switch (type) {
            case Class<?> raw -> {
                var name = raw.getName();
                yield !name.contains("Codec")
                        && !name.contains("DynamicOps")
                        && !name.contains("SavedData")
                        && !name.contains("Attachment")
                        && !name.startsWith("net.minecraft.")
                        && !name.startsWith("net.neoforged.")
                        && !name.startsWith("java.util.concurrent.")
                        && (!raw.isArray() || isNotP4Type(raw.getComponentType(), visited));
            }
            case ParameterizedType parameterized ->
                    isNotP4Type(parameterized.getRawType(), visited)
                            && Arrays.stream(parameterized.getActualTypeArguments())
                                    .allMatch(argument -> isNotP4Type(argument, visited));
            case GenericArrayType array ->
                    isNotP4Type(array.getGenericComponentType(), visited);
            case WildcardType wildcard ->
                    Arrays.stream(wildcard.getUpperBounds())
                                    .allMatch(bound -> isNotP4Type(bound, visited))
                            && Arrays.stream(wildcard.getLowerBounds())
                                    .allMatch(bound -> isNotP4Type(bound, visited));
            case TypeVariable<?> variable -> Arrays.stream(variable.getBounds())
                    .allMatch(bound -> isNotP4Type(bound, visited));
            default -> true;
        };
    }

    private static boolean containsType(Type type, Class<?> expected) {
        return containsType(type, expected, new HashSet<>());
    }

    private static boolean containsType(
            Type type,
            Class<?> expected,
            Set<Type> visited) {
        if (!visited.add(type)) {
            return false;
        }
        return switch (type) {
            case Class<?> raw -> raw == expected
                    || raw.isArray() && containsType(raw.getComponentType(), expected, visited);
            case ParameterizedType parameterized ->
                    containsType(parameterized.getRawType(), expected, visited)
                            || Arrays.stream(parameterized.getActualTypeArguments())
                                    .anyMatch(argument -> containsType(argument, expected, visited));
            case GenericArrayType array ->
                    containsType(array.getGenericComponentType(), expected, visited);
            case WildcardType wildcard ->
                    Arrays.stream(wildcard.getUpperBounds())
                                    .anyMatch(bound -> containsType(bound, expected, visited))
                            || Arrays.stream(wildcard.getLowerBounds())
                                    .anyMatch(bound -> containsType(bound, expected, visited));
            case TypeVariable<?> variable -> Arrays.stream(variable.getBounds())
                    .anyMatch(bound -> containsType(bound, expected, visited));
            default -> false;
        };
    }

    private enum LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER
    }
}
