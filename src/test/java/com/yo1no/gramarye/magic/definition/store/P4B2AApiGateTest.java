package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.Test;

/** Phase-local API, source-ownership, and configuration gate for P4-B2-A. */
class P4B2AApiGateTest {
    private static final String STORE_PACKAGE =
            "com.yo1no.gramarye.magic.definition.store.";
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path STORE_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store");
    private static final Pattern TOP_LEVEL_TYPE_DECLARATION = Pattern.compile(
            "(?m)^(?:(?:public|protected|private|abstract|final|non-sealed|sealed|static)\\s+)*"
                    + "(?:class|record|interface|enum)\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Set<String> PUBLIC_B2_A_TOP_LEVEL_TYPES = Set.of(
            "ControlledSkillPin",
            "SkillDefinitionStoreService",
            "SkillSavedDataLifecycleGameTests",
            "SkillSubsystemResult",
            "SkillSubsystemUnavailableReason");

    @Test
    void exactReviewedTypesExistWithOnlyTheApprovedPublicSurface() throws Exception {
        var sources = b2Sources();
        var declared = sources.stream()
                .flatMap(path -> TOP_LEVEL_TYPE_DECLARATION
                        .matcher(withoutCommentsAndLiterals(read(path)))
                        .results()
                        .map(match -> match.group(1)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var loaded = P4B2PhaseTypes.TOP_LEVEL_TYPE_NAMES.stream()
                .map(P4B2AApiGateTest::loadStoreType)
                .toList();
        var publicTypes = loaded.stream()
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());

        assertEquals(P4B2PhaseTypes.TOP_LEVEL_TYPE_NAMES, declared);
        assertEquals(P4B2PhaseTypes.TOP_LEVEL_TYPE_NAMES,
                loaded.stream().map(Class::getSimpleName).collect(Collectors.toSet()));
        assertEquals(PUBLIC_B2_A_TOP_LEVEL_TYPES, publicTypes);
        assertFalse(Modifier.isPublic(GramaryeSkillSavedData.class.getModifiers()));
        assertTrue(SavedData.class.isAssignableFrom(GramaryeSkillSavedData.class));

        assertEquals(
                Set.of("close", "isClosed", "reference"),
                publicDeclaredMethodNames(ControlledSkillPin.class));
        assertEquals(
                Set.of(
                        "committedSkillCount",
                        "find",
                        "latestReference",
                        "ownerOf",
                        "pin",
                        "reclaim",
                        "registerOn"),
                publicDeclaredMethodNames(SkillDefinitionStoreService.class));
        assertEquals(
                Set.of("startupInstalledExactReadyAdapterInOverworldCache"),
                publicDeclaredMethodNames(SkillSavedDataLifecycleGameTests.class));
        assertTrue(Arrays.stream(SkillDefinitionStoreService.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
    }

    @Test
    void fileCeilingAndSavedDataSubclassHaveOneReviewedProductionOwner() throws Exception {
        var production = productionSources(MAIN_JAVA);
        var ceilingReferences = filesContaining(
                production, "MAX_SKILL_SAVED_DATA_FILE_BYTES");
        var savedDataSubclasses = filesMatching(
                production,
                Pattern.compile(
                        "\\bextends\\s+(?:net\\.minecraft\\.world\\.level\\.saveddata\\.)?"
                                + "SavedData\\b"));

        assertEquals(73_400_320, MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES);
        assertEquals(
                Set.of(
                        "com/yo1no/gramarye/magic/definition/store/"
                                + "SkillSavedDataPrimaryIngress.java",
                        "com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java"),
                ceilingReferences);
        assertEquals(
                Set.of("com/yo1no/gramarye/magic/definition/store/"
                        + "GramaryeSkillSavedData.java"),
                savedDataSubclasses);
    }

    @Test
    void publicApiLeaksNoRawPersistenceOrFailureState() {
        var reviewed = nestedTypeClosure(PUBLIC_B2_A_TOP_LEVEL_TYPES.stream()
                .map(P4B2AApiGateTest::loadStoreType)
                .toList());

        for (var type : reviewed) {
            Arrays.stream(type.getDeclaredFields())
                    .filter(field -> !field.isSynthetic())
                    .filter(field -> isPublicOrProtected(field.getModifiers()))
                    .forEach(field -> assertFalse(
                            exposesForbiddenType(field.getGenericType()),
                            () -> type.getName() + " field leaks " + field.getGenericType()));
            Arrays.stream(type.getDeclaredConstructors())
                    .filter(constructor -> !constructor.isSynthetic())
                    .filter(constructor -> isPublicOrProtected(constructor.getModifiers()))
                    .forEach(constructor -> {
                        for (var parameter : constructor.getGenericParameterTypes()) {
                            assertFalse(exposesForbiddenType(parameter),
                                    () -> type.getName() + " constructor leaks " + parameter);
                        }
                        for (var exception : constructor.getGenericExceptionTypes()) {
                            assertFalse(exposesForbiddenType(exception),
                                    () -> type.getName() + " constructor leaks " + exception);
                        }
                    });
            Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> !method.isSynthetic() && !method.isBridge())
                    .filter(method -> isPublicOrProtected(method.getModifiers()))
                    .forEach(method -> {
                        assertFalse(exposesForbiddenType(method.getGenericReturnType()),
                                () -> type.getName() + " method leaks "
                                        + method.getGenericReturnType());
                        for (var parameter : method.getGenericParameterTypes()) {
                            assertFalse(exposesForbiddenType(parameter),
                                    () -> type.getName() + " method leaks " + parameter);
                        }
                        for (var exception : method.getGenericExceptionTypes()) {
                            assertFalse(exposesForbiddenType(exception),
                                    () -> type.getName() + " method leaks " + exception);
                        }
                    });
            if (type.isRecord()) {
                for (var component : type.getRecordComponents()) {
                    assertFalse(exposesForbiddenType(component.getGenericType()),
                            () -> type.getName() + " component leaks "
                                    + component.getGenericType());
                }
            }
        }

        assertEquals(
                Set.of(SkillSavedDataState.class),
                instanceFieldTypes(GramaryeSkillSavedData.class));
        assertEquals(
                Set.of(
                        SkillDefinitionStore.class,
                        SkillSavedDataInnerCarrier.class,
                        boolean.class),
                instanceFieldTypes(SkillSavedDataState.Ready.class));
        assertEquals(
                Set.of(SkillSavedDataPrimaryFailure.class),
                instanceFieldTypes(SkillSavedDataState.Quarantined.class));
        assertEquals(
                Set.of(SkillSavedDataRuntimeFailure.class),
                instanceFieldTypes(SkillSavedDataState.Unavailable.class));
        assertEquals(
                Set.of(SkillSavedDataRuntimeFailure.Code.class),
                instanceFieldTypes(SkillSavedDataRuntimeFailure.class));
        assertEquals(
                Set.of(GzipFailureRecorder.State.class),
                instanceFieldTypes(GzipFailureRecorder.class));

        var boundedFailures = nestedTypeClosure(
                List.of(SkillSavedDataPrimaryFailure.class));
        for (var failureType : boundedFailures) {
            Arrays.stream(failureType.getDeclaredFields())
                    .filter(field -> !field.isSynthetic())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .forEach(field -> assertFalse(
                            field.getType() == byte[].class
                                    || Path.class.isAssignableFrom(field.getType())
                                    || File.class.isAssignableFrom(field.getType())
                                    || InputStream.class.isAssignableFrom(field.getType())
                                    || Throwable.class.isAssignableFrom(field.getType()),
                            () -> failureType.getName() + " retains raw failure state in "
                                    + field.getName()));
        }
    }

    @Test
    void strictIngressAndLifecycleSourcesKeepTheReviewedBoundary() throws Exception {
        var sources = b2Sources();
        var code = sources.stream()
                .map(P4B2AApiGateTest::read)
                .map(P4B2AApiGateTest::withoutCommentsAndLiterals)
                .collect(Collectors.joining("\n"));
        var raw = sources.stream().map(P4B2AApiGateTest::read)
                .collect(Collectors.joining("\n"));
        var forbiddenFragments = List.of(
                "computeIfAbsent",
                "readTagFromDisk",
                "Files.readAllBytes",
                "java.nio.file.Files.readAllBytes",
                "Files.exists",
                "Files.notExists",
                "GZIPInputStream",
                "GZIPOutputStream",
                "getCompressedCount",
                "NbtIo.readCompressed",
                "unlimitedHeap",
                "decompressConcatenated",
                "setDecompressConcatenated",
                "java.lang.reflect",
                "Class.forName",
                "setAccessible",
                "MethodHandles",
                "VarHandle",
                "sun.misc.Unsafe",
                "PlayerSkillAttachment",
                "PendingAttachmentJournal",
                "Attachment",
                "Journal",
                "AttachmentType",
                "IAttachmentHolder",
                "RootCollector",
                "RootIndex",
                "RootProvider",
                "OfflineRoot",
                "Offline",
                "Reconciliation",
                "StreamCodec",
                "CustomPacketPayload",
                "Network",
                "Packet",
                "ServerPlayer",
                "Client",
                "GramaryeClient",
                "p4B2Probe",
                "p4B2GameTest",
                "P4B2Heap");

        for (var forbidden : forbiddenFragments) {
            assertFalse(code.contains(forbidden), () -> "B2-A source contains " + forbidden);
        }
        assertFalse(Pattern.compile("\\.\\s*available\\s*\\(").matcher(code).find());
        assertFalse(Pattern.compile("\\.\\s*commit\\s*\\(").matcher(code).find());
        assertFalse(raw.contains(".dat_old"));

        var strict = read(STORE_ROOT.resolve("StrictSingleMemberGzipInput.java"));
        var ingress = read(STORE_ROOT.resolve("SkillSavedDataPrimaryIngress.java"));
        assertTrue(strict.contains(
                "new GzipCompressorInputStream(bufferedCompressed, false)"));
        assertFalse(Pattern.compile(
                        "new\\s+GzipCompressorInputStream\\s*\\([^;]*,\\s*true\\s*\\)",
                        Pattern.DOTALL)
                .matcher(withoutCommentsAndLiterals(strict))
                .find());
        assertEquals(
                Set.of("com/yo1no/gramarye/magic/definition/store/"
                        + "StrictSingleMemberGzipInput.java"),
                filesContaining(productionSources(MAIN_JAVA), "GzipCompressorInputStream"));
        assertTrue(ingress.contains("BasicFileAttributes.class"));
        assertTrue(ingress.contains("LinkOption.NOFOLLOW_LINKS"));
        assertTrue(ingress.contains("FileChannel.open(primary, READ_NOFOLLOW)"));
        assertEquals(1, occurrences(
                withoutCommentsAndLiterals(ingress), "FileChannel.open("));
        assertEquals(
                Set.of("com/yo1no/gramarye/magic/definition/store/"
                        + "SkillSavedDataPrimaryIngress.java"),
                filesContaining(productionSources(MAIN_JAVA), "FileChannel.open("));
        assertFalse(Pattern.compile("\\.\\s*position\\s*\\(")
                .matcher(withoutCommentsAndLiterals(strict))
                .find());
        assertEquals(
                Set.of("SkillDefinitionStoreService.java"),
                fileNamesContaining(sources, "ServerStartingEvent"));
        assertEquals(
                Set.of("SkillDefinitionStoreService.java"),
                fileNamesContaining(sources, "ServerStoppedEvent"));
    }

    @Test
    void installReclaimAndSavePublicationOrderingRemainFailClosed() throws Exception {
        var service = read(STORE_ROOT.resolve("SkillDefinitionStoreService.java"));
        var adapter = read(STORE_ROOT.resolve("GramaryeSkillSavedData.java"));
        var controlledPin = read(STORE_ROOT.resolve("ControlledSkillPin.java"));
        var root = read(MAIN_JAVA.resolve("com/yo1no/gramarye/Gramarye.java"));
        var install = sourceSlice(service, "    void install(", "\n    void uninstall(");
        var save = sourceSlice(
                adapter,
                "    public CompoundTag save(",
                "\n    private static <T>");

        assertOrdered(
                install,
                "installedServers.containsKey(server)",
                "SkillSavedDataPrimaryIngress.load(server)",
                "storage.set(SAVED_DATA_NAME, adapter)",
                "storage.get(CACHE_HIT_ONLY_FACTORY, SAVED_DATA_NAME)",
                "adapter.setDirty()",
                "installedServers.put(server, InstalledMarker.INSTANCE)");
        assertTrue(service.contains("private final IdentityHashMap<MinecraftServer, InstalledMarker>"));
        assertTrue(service.contains("installedServers.remove(server)"));
        assertTrue(service.contains("if (!server.isSameThread())"));
        assertTrue(service.contains("BOOTSTRAP_ALREADY_INSTALLED"));
        assertTrue(service.contains("BOOTSTRAP_NOT_INSTALLED"));
        assertFalse(service.contains("static final IdentityHashMap"));
        assertFalse(service.contains("ConcurrentHashMap"));
        assertFalse(service.contains("synchronized"));
        assertFalse(service.contains("volatile"));
        assertEquals(IdentityHashMap.class,
                fieldType(SkillDefinitionStoreService.class, "installedServers"));
        assertFalse(Modifier.isStatic(
                fieldModifiers(SkillDefinitionStoreService.class, "installedServers")));
        assertEquals(
                Set.of(
                        "com/yo1no/gramarye/Gramarye.java",
                        "com/yo1no/gramarye/magic/definition/store/"
                                + "SkillDefinitionStoreService.java"),
                filesContaining(productionSources(MAIN_JAVA), "registerOn("));
        assertTrue(root.contains("private final SkillDefinitionStoreService"));
        assertTrue(root.contains("SkillDefinitionStoreService.registerOn(\n"
                + "                NeoForge.EVENT_BUS)"));

        assertOrdered(adapter, "var replacement =", "state = replacement;", "setDirty();");
        assertOrdered(adapter,
                "state = new SkillSavedDataState.Unavailable(", "setDirty(false);");
        assertTrue(save.contains("return ready.innerCarrier().createDataTag();"));
        assertFalse(save.contains("setDirty"));
        assertFalse(save.contains(".store()"));
        assertFalse(save.contains("rebuild"));
        assertFalse(save.contains("encode"));
        assertFalse(save.contains("File"));

        assertEquals(
                Set.of(net.minecraft.server.MinecraftServer.class, SkillRevisionPin.class),
                instanceFieldTypes(ControlledSkillPin.class));
        var close = sourceSlice(
                controlledPin,
                "    public void close()",
                "\n    private void requireServerThread()");
        assertOrdered(close, "requireServerThread();", "delegate.close();");
        assertTrue(controlledPin.contains("if (!server.isSameThread())"));
        assertFalse(controlledPin.contains("setDirty"));
        assertFalse(controlledPin.contains("reclaim"));
        assertFalse(controlledPin.contains("InstalledMarker"));

        var forbiddenStaticTypes = Set.<Class<?>>of(
                SkillDefinitionStore.class,
                EncodedSkillStoreCarrier.class,
                GramaryeSkillSavedData.class,
                SkillSavedDataState.class,
                SkillSavedDataInnerCarrier.class,
                SkillRevisionPin.class);
        for (var typeName : P4B2PhaseTypes.TOP_LEVEL_TYPE_NAMES) {
            var type = loadStoreType(typeName);
            assertTrue(Arrays.stream(type.getDeclaredFields())
                    .filter(field -> !field.isSynthetic())
                    .filter(field -> Modifier.isStatic(field.getModifiers()))
                    .noneMatch(field -> forbiddenStaticTypes.contains(field.getType())),
                    () -> type.getName() + " retains static live persistence state");
        }
    }

    @Test
    void normalGameTestCountIsFiveAndB2BConfigurationRemainsAbsent() throws Exception {
        var production = productionSources(MAIN_JAVA);
        var allMain = production.stream().map(P4B2AApiGateTest::read)
                .collect(Collectors.joining("\n"));
        var platformTests = read(MAIN_JAVA.resolve(
                "com/yo1no/gramarye/gametest/PlatformGameTests.java"));
        var lifecycleTests = read(STORE_ROOT.resolve(
                "SkillSavedDataLifecycleGameTests.java"));

        assertEquals(5, occurrences(allMain, "@GameTest("));
        assertEquals(4, occurrences(platformTests, "@GameTest("));
        assertEquals(1, occurrences(lifecycleTests, "@GameTest("));
        assertTrue(lifecycleTests.contains("@GameTestHolder(Gramarye.MOD_ID)"));
        assertTrue(lifecycleTests.contains(
                "startupInstalledExactReadyAdapterInOverworldCache"));
        assertTrue(lifecycleTests.contains("templateNamespace = \"minecraft\""));

        var build = read(PROJECT_ROOT.resolve("build.gradle")).toLowerCase();
        var workflow = read(PROJECT_ROOT.resolve(".github/workflows/build.yml")).toLowerCase();
        assertTrue(build.contains("gametestserver {"));
        assertTrue(workflow.contains("./gradlew rungametestserver"));
        for (var marker : List.of(
                "p4b2", "p4-b2", "p4_b2", "p4-b-memory", "p4-b memory", "p4bmemory")) {
            assertFalse(build.contains(marker), () -> "B2-B marker in build.gradle: " + marker);
            assertFalse(workflow.contains(marker), () -> "B2-B marker in workflow: " + marker);
        }
        assertFalse(Files.exists(PROJECT_ROOT.resolve("src/p4B2Probe")));
        assertFalse(Files.exists(PROJECT_ROOT.resolve("src/p4B2GameTest")));
    }

    private static Set<String> publicDeclaredMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic() && !method.isBridge())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
    }

    private static boolean exposesForbiddenType(Type type) {
        if (type instanceof Class<?> raw) {
            if (raw.isArray()) {
                return raw == byte[].class || exposesForbiddenType(raw.getComponentType());
            }
            return SavedData.class.isAssignableFrom(raw)
                    || raw == DimensionDataStorage.class
                    || raw == SkillDefinitionStore.class
                    || raw == EncodedSkillStoreCarrier.class
                    || raw == GramaryeSkillSavedData.class
                    || raw == SkillSavedDataState.class
                    || raw == SkillSavedDataInnerCarrier.class
                    || raw == SkillSavedDataReadyCandidate.class
                    || raw == OpaquePendingAttachmentUpdatesBlob.class
                    || raw == SkillRevisionPin.class
                    || raw == CompoundTag.class
                    || Tag.class.isAssignableFrom(raw)
                    || Path.class.isAssignableFrom(raw)
                    || File.class.isAssignableFrom(raw)
                    || InputStream.class.isAssignableFrom(raw)
                    || FileChannel.class.isAssignableFrom(raw)
                    || Throwable.class.isAssignableFrom(raw);
        }
        if (type instanceof ParameterizedType parameterized) {
            if (exposesForbiddenType(parameterized.getRawType())) {
                return true;
            }
            if (parameterized.getOwnerType() != null
                    && exposesForbiddenType(parameterized.getOwnerType())) {
                return true;
            }
            return Arrays.stream(parameterized.getActualTypeArguments())
                    .anyMatch(P4B2AApiGateTest::exposesForbiddenType);
        }
        if (type instanceof GenericArrayType array) {
            return exposesForbiddenType(array.getGenericComponentType());
        }
        if (type instanceof WildcardType wildcard) {
            return Arrays.stream(wildcard.getLowerBounds())
                            .anyMatch(P4B2AApiGateTest::exposesForbiddenType)
                    || Arrays.stream(wildcard.getUpperBounds())
                            .anyMatch(P4B2AApiGateTest::exposesForbiddenType);
        }
        if (type instanceof TypeVariable<?> variable) {
            return Arrays.stream(variable.getBounds())
                    .anyMatch(P4B2AApiGateTest::exposesForbiddenType);
        }
        return false;
    }

    private static boolean isPublicOrProtected(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static List<Class<?>> nestedTypeClosure(List<Class<?>> roots) {
        var result = new LinkedHashSet<Class<?>>();
        var pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            var type = pending.removeFirst();
            if (!result.add(type)) {
                continue;
            }
            pending.addAll(Arrays.asList(type.getDeclaredClasses()));
            var permitted = type.getPermittedSubclasses();
            if (permitted != null) {
                pending.addAll(Arrays.asList(permitted));
            }
        }
        return List.copyOf(result);
    }

    private static List<Path> b2Sources() throws Exception {
        return productionSources(STORE_ROOT).stream()
                .filter(path -> P4B2PhaseTypes.containsSourceFileName(
                        path.getFileName().toString()))
                .toList();
    }

    private static List<Path> productionSources(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static Set<String> filesContaining(List<Path> sources, String fragment) {
        return sources.stream()
                .filter(path -> read(path).contains(fragment))
                .map(MAIN_JAVA::relativize)
                .map(Path::toString)
                .map(name -> name.replace(File.separatorChar, '/'))
                .collect(Collectors.toSet());
    }

    private static Set<String> fileNamesContaining(List<Path> sources, String fragment) {
        return sources.stream()
                .filter(path -> read(path).contains(fragment))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
    }

    private static Set<String> filesMatching(List<Path> sources, Pattern pattern) {
        return sources.stream()
                .filter(path -> pattern.matcher(
                        withoutCommentsAndLiterals(read(path))).find())
                .map(MAIN_JAVA::relativize)
                .map(Path::toString)
                .map(name -> name.replace(File.separatorChar, '/'))
                .collect(Collectors.toSet());
    }

    private static void assertOrdered(String source, String... fragments) {
        var previous = -1;
        for (var fragment : fragments) {
            var current = source.indexOf(fragment);
            assertTrue(current > previous,
                    "Expected ordered fragment after offset " + previous + ": " + fragment);
            previous = current;
        }
    }

    private static String sourceSlice(String source, String start, String end) {
        var startIndex = source.indexOf(start);
        var endIndex = source.indexOf(end, startIndex + start.length());
        if (startIndex < 0 || endIndex < 0) {
            throw new AssertionError("Unable to isolate reviewed source slice");
        }
        return source.substring(startIndex, endIndex);
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        for (var index = source.indexOf(fragment); index >= 0;
                index = source.indexOf(fragment, index + fragment.length())) {
            count++;
        }
        return count;
    }

    private static Class<?> fieldType(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name).getType();
        } catch (NoSuchFieldException exception) {
            throw new AssertionError("Missing reviewed field " + type.getName() + "." + name,
                    exception);
        }
    }

    private static Set<Class<?>> instanceFieldTypes(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getType())
                .collect(Collectors.toSet());
    }

    private static int fieldModifiers(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name).getModifiers();
        } catch (NoSuchFieldException exception) {
            throw new AssertionError("Missing reviewed field " + type.getName() + "." + name,
                    exception);
        }
    }

    private static Class<?> loadStoreType(String simpleName) {
        try {
            return Class.forName(
                    STORE_PACKAGE + simpleName,
                    false,
                    P4B2AApiGateTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Missing reviewed P4-B2-A type: " + simpleName, exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("Unable to inspect " + path, exception);
        }
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("build.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root not found");
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
                    var delimiter = state == LexicalState.STRING ? '"' : '\'';
                    if (current == '\\' && next != '\0') {
                        sanitized.append("  ");
                        index++;
                    } else {
                        sanitized.append(current == '\n' ? '\n' : ' ');
                        if (current == delimiter) {
                            state = LexicalState.CODE;
                        }
                    }
                }
            }
        }
        return sanitized.toString();
    }

    private enum LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER
    }
}
