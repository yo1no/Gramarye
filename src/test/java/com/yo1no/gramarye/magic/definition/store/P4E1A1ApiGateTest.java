package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** Exact nominal, generic-signature, lifetime, and phase gate for P4-E1-A.1. */
final class P4E1A1ApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path STORE_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store");
    private static final Path PLAYER_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player");

    @Test
    void exactSealedNominalHierarchyHasOnePackagePrivateTagBinding() {
        var source = PlayerSkillAttachmentAdmissionSource.class;
        var bound = P4E1BoundPlayerSkillAttachmentAdmissionSource.class;
        var opaque = PlayerSkillAttachmentService.OpaqueAdmissionSource.class;
        var sourceSuper = (ParameterizedType) source.getGenericSuperclass();
        var boundSuper = (ParameterizedType) bound.getGenericSuperclass();

        assertAll(
                () -> assertTrue(Modifier.isPublic(source.getModifiers())),
                () -> assertTrue(Modifier.isAbstract(source.getModifiers())),
                () -> assertTrue(source.isSealed()),
                () -> assertEquals(List.of(bound),
                        Arrays.asList(source.getPermittedSubclasses())),
                () -> assertFalse(Modifier.isPublic(bound.getModifiers())),
                () -> assertTrue(Modifier.isFinal(bound.getModifiers())),
                () -> assertEquals(source, boundSuper.getRawType()),
                () -> assertEquals(
                        List.of(Tag.class, HolderLookup.Provider.class),
                        Arrays.asList(boundSuper.getActualTypeArguments())),
                () -> assertEquals(opaque, sourceSuper.getRawType()),
                () -> assertTrue(Arrays.stream(sourceSuper.getActualTypeArguments())
                        .allMatch(TypeVariable.class::isInstance)),
                () -> assertEquals(2, source.getTypeParameters().length),
                () -> assertEquals(0, source.getDeclaredFields().length),
                () -> assertEquals(0, bound.getDeclaredFields().length),
                () -> assertEquals(1, source.getDeclaredConstructors().length),
                () -> assertEquals(1, bound.getDeclaredConstructors().length),
                () -> assertTrue(Arrays.stream(source.getDeclaredConstructors())
                        .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                                || Modifier.isProtected(constructor.getModifiers()))),
                () -> assertTrue(Arrays.stream(bound.getDeclaredConstructors())
                        .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                                || Modifier.isProtected(constructor.getModifiers()))),
                () -> assertEquals(0, Arrays.stream(source.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .count()),
                () -> assertEquals(0, Arrays.stream(bound.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .count()),
                () -> assertEquals(
                        Set.of("admitDiskObservation", "admitIntegratedObservation"),
                        Arrays.stream(bound.getDeclaredMethods())
                                .map(method -> method.getName()).collect(Collectors.toSet())),
                () -> assertTrue(Arrays.stream(bound.getDeclaredMethods())
                        .allMatch(method -> Modifier.isStatic(method.getModifiers()))));
    }

    @Test
    void genericOpaqueStorageHasOnlyPrivateFieldsAndOneProtectedGenericConstructor() {
        var opaque = PlayerSkillAttachmentService.OpaqueAdmissionSource.class;
        var constructors = opaque.getDeclaredConstructors();
        var constructor = constructors[0];
        var rawParameters = Arrays.asList(constructor.getParameterTypes());
        var genericParameters = Arrays.asList(constructor.getGenericParameterTypes());

        assertAll(
                () -> assertTrue(Modifier.isPublic(opaque.getModifiers())),
                () -> assertTrue(Modifier.isAbstract(opaque.getModifiers())),
                () -> assertTrue(Modifier.isStatic(opaque.getModifiers())),
                () -> assertEquals(1, constructors.length),
                () -> assertTrue(Modifier.isProtected(constructor.getModifiers())),
                () -> assertEquals(
                        List.of(
                                PlayerSkillAttachmentService.class,
                                Object.class,
                                Object.class,
                                long.class,
                                Object.class,
                                Object.class),
                        rawParameters),
                () -> assertEquals(
                        List.of(
                                PlayerSkillAttachmentService.class,
                                opaque.getTypeParameters()[0],
                                opaque.getTypeParameters()[0],
                                long.class,
                                opaque.getTypeParameters()[1],
                                opaque.getTypeParameters()[1]),
                        genericParameters),
                () -> assertEquals(
                        Set.of(
                                "owner",
                                "inputIdentity",
                                "measurementInputIdentity",
                                "exactEncodedWidth",
                                "providerIdentity",
                                "providerWitnessIdentity",
                                "consumed"),
                        Arrays.stream(opaque.getDeclaredFields())
                                .map(field -> field.getName()).collect(Collectors.toSet())),
                () -> assertTrue(Arrays.stream(opaque.getDeclaredFields())
                        .allMatch(field -> Modifier.isPrivate(field.getModifiers()))),
                () -> assertEquals(0, opaque.getDeclaredMethods().length));
    }

    @Test
    void playerServiceExposesOnlyTheReviewedAdmissionAndCallbackOperations() throws Exception {
        var service = PlayerSkillAttachmentService.class;
        var admit = service.getDeclaredMethod(
                "admitForRootAudit", PlayerSkillAttachmentAdmissionSource.class);
        var count = service.getDeclaredMethod(
                "rootCount", PlayerSkillAttachmentService.RootAuditAdmitted.class);
        var drain = service.getDeclaredMethod(
                "drainRootProjection",
                PlayerSkillAttachmentService.RootAuditAdmitted.class,
                PlayerSkillAttachmentService.RootAuditSink.class);
        var discard = service.getDeclaredMethod(
                "discardRootProjection",
                PlayerSkillAttachmentService.RootAuditAdmitted.class);
        var sink = PlayerSkillAttachmentService.RootAuditSink.class;
        var genericAdmissionParameter = assertParameterized(
                admit.getGenericParameterTypes()[0]);
        var reviewedNames = Set.of(
                "admitForRootAudit",
                "rootCount",
                "drainRootProjection",
                "discardRootProjection");

        assertAll(
                () -> assertEquals(
                        PlayerSkillAttachmentService.RootAuditAdmissionResult.class,
                        admit.getReturnType()),
                () -> assertEquals(
                        PlayerSkillAttachmentAdmissionSource.class,
                        admit.getParameterTypes()[0]),
                () -> assertEquals(
                        PlayerSkillAttachmentAdmissionSource.class,
                        genericAdmissionParameter.getRawType()),
                () -> assertTrue(Arrays.stream(
                                genericAdmissionParameter.getActualTypeArguments())
                        .allMatch(WildcardType.class::isInstance)),
                () -> assertTrue(Arrays.stream(
                                genericAdmissionParameter.getActualTypeArguments())
                        .map(WildcardType.class::cast)
                        .allMatch(argument -> argument.getLowerBounds().length == 0
                                && Arrays.equals(
                                        new java.lang.reflect.Type[] {Object.class},
                                        argument.getUpperBounds()))),
                () -> assertFalse(Arrays.asList(admit.getParameterTypes())
                        .contains(PlayerSkillAttachmentService.OpaqueAdmissionSource.class)),
                () -> assertEquals(4, Arrays.stream(service.getDeclaredMethods())
                        .filter(method -> reviewedNames.contains(method.getName()))
                        .count()),
                () -> assertEquals(int.class, count.getReturnType()),
                () -> assertEquals(void.class, drain.getReturnType()),
                () -> assertEquals(void.class, discard.getReturnType()),
                () -> assertEquals(
                        Set.of("latest", "equipped"),
                        Arrays.stream(sink.getDeclaredMethods())
                                .map(method -> method.getName()).collect(Collectors.toSet())),
                () -> assertEquals(
                        List.of(SkillReference.class),
                        Arrays.asList(sink.getDeclaredMethod(
                                "latest", SkillReference.class).getParameterTypes())),
                () -> assertEquals(
                        List.of(int.class, SkillReference.class),
                        Arrays.asList(sink.getDeclaredMethod(
                                "equipped", int.class, SkillReference.class).getParameterTypes())));
    }

    @Test
    void resultsAndSingleUseHandleHaveNoPublicConstructorOrRootBackingAccessor() {
        var result = PlayerSkillAttachmentService.RootAuditAdmissionResult.class;
        var admitted = PlayerSkillAttachmentService.RootAuditAdmitted.class;
        var rejected = PlayerSkillAttachmentService.RootAuditRejected.class;
        var oversize = PlayerSkillAttachmentService.RootAuditOversize.class;
        var expectedPermits = Set.of(admitted, rejected, oversize);

        assertAll(
                () -> assertTrue(result.isSealed()),
                () -> assertEquals(expectedPermits,
                        Set.of(result.getPermittedSubclasses())),
                () -> assertEquals(0, result.getDeclaredMethods().length),
                () -> assertTrue(Arrays.stream(admitted.getDeclaredConstructors())
                        .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()))),
                () -> assertTrue(Arrays.stream(rejected.getDeclaredConstructors())
                        .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()))),
                () -> assertEquals(0, Arrays.stream(admitted.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .count()),
                () -> assertEquals(0, Arrays.stream(rejected.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .count()),
                () -> assertTrue(Arrays.stream(admitted.getDeclaredFields())
                        .allMatch(field -> Modifier.isPrivate(field.getModifiers()))),
                () -> assertTrue(Arrays.stream(rejected.getDeclaredFields())
                        .allMatch(field -> Modifier.isPrivate(field.getModifiers()))),
                () -> assertEquals(
                        Set.of("owner", "latest", "equipped", "rootCount", "consumed"),
                        Arrays.stream(admitted.getDeclaredFields())
                                .map(field -> field.getName()).collect(Collectors.toSet())),
                () -> assertEquals(
                        Set.of("failure"),
                        Arrays.stream(rejected.getDeclaredFields())
                                .map(field -> field.getName()).collect(Collectors.toSet())),
                () -> assertEquals(
                        "com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentFailure",
                        rejected.getDeclaredField("failure").getType().getName()),
                () -> assertTrue(Stream.of(admitted, rejected)
                        .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                        .noneMatch(field -> Tag.class.isAssignableFrom(field.getType())
                                || HolderLookup.Provider.class.isAssignableFrom(field.getType()))),
                () -> assertFalse(java.io.Serializable.class.isAssignableFrom(admitted)),
                () -> assertFalse(Cloneable.class.isAssignableFrom(admitted)));
    }

    @Test
    void productionCallSitesAndPhaseAbsencesAreExact() throws Exception {
        var bound = Files.readString(STORE_ROOT.resolve(
                "P4E1BoundPlayerSkillAttachmentAdmissionSource.java"));
        var source = Files.readString(STORE_ROOT.resolve(
                "PlayerSkillAttachmentAdmissionSource.java"));
        var service = Files.readString(PLAYER_ROOT.resolve(
                "PlayerSkillAttachmentService.java"));
        var production = javaSources(MAIN_JAVA);
        var reviewed = bound + '\n' + source + '\n' + service;
        var bridgeOnly = bound + '\n' + source;

        assertAll(
                () -> assertEquals(2, occurrences(
                        production, "new P4E1BoundPlayerSkillAttachmentAdmissionSource(")),
                () -> assertEquals(1, occurrences(service, "rootAuditAdmission.admit(")),
                () -> assertEquals(2, occurrences(bound, "service.admitForRootAudit(")),
                () -> assertEquals(1, occurrences(
                        bound, "admitDiskObservation(")),
                () -> assertEquals(1, occurrences(
                        bound, "admitIntegratedObservation(")),
                () -> assertFalse(source.contains("Tag")),
                () -> assertFalse(source.contains("CompoundTag")),
                () -> assertFalse(source.contains("Path")),
                () -> assertFalse(source.contains("byte[]")),
                () -> assertFalse(reviewed.contains("@SuppressWarnings")),
                () -> assertFalse(reviewed.contains(" unchecked ")),
                () -> assertFalse(reviewed.contains("java.lang.reflect")),
                () -> assertFalse(reviewed.contains("sun.misc.Unsafe")),
                () -> assertFalse(reviewed.contains("setAccessible(")),
                () -> assertFalse(reviewed.contains("SkillRetentionRootAuditService")),
                () -> assertFalse(reviewed.contains("SkillRetentionRootSnapshot")),
                () -> assertFalse(reviewed.contains(".reclaim(")),
                () -> assertFalse(bridgeOnly.contains(".setData(")),
                () -> assertEquals(1, occurrences(service, ".setData(")),
                () -> assertFalse(reviewed.contains("PendingAttachmentJournal")),
                () -> assertFalse(reviewed.contains("CompleteCapture")),
                () -> assertFalse(reviewed.contains("static {")),
                () -> assertFalse(reviewed.contains("Map<")),
                () -> assertFalse(reviewed.contains("Codec")),
                () -> assertFalse(reviewed.contains("Serializable")));
    }

    private static ParameterizedType assertParameterized(java.lang.reflect.Type type) {
        assertTrue(type instanceof ParameterizedType, () -> "not parameterized: " + type);
        return (ParameterizedType) type;
    }

    private static String javaSources(Path root) throws Exception {
        var text = new StringBuilder();
        try (var stream = Files.walk(root)) {
            for (var path : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                text.append(Files.readString(path)).append('\n');
            }
        }
        return text.toString();
    }

    private static int occurrences(String text, String token) {
        var count = 0;
        var offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
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
