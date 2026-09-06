package com.yo1no.gramarye.magic.presentation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class ProfileApiTest {
    @Test
    void profileChannelsHaveExactStableWireVocabulary() {
        assertEquals(
                Set.of(ProfileChannel.SOUND, ProfileChannel.PARTICLE, ProfileChannel.TRAIL),
                Set.of(ProfileChannel.values()));
        assertEquals(0, ProfileChannel.SOUND.wireCode());
        assertEquals(1, ProfileChannel.PARTICLE.wireCode());
        assertEquals(2, ProfileChannel.TRAIL.wireCode());
        assertEquals(ProfileChannel.SOUND, ProfileChannel.fromWireCode(0).orElseThrow());
        assertEquals(ProfileChannel.PARTICLE, ProfileChannel.fromWireCode(1).orElseThrow());
        assertEquals(ProfileChannel.TRAIL, ProfileChannel.fromWireCode(2).orElseThrow());
        assertTrue(ProfileChannel.fromWireCode(-1).isEmpty());
        assertTrue(ProfileChannel.fromWireCode(3).isEmpty());
    }

    @Test
    void clientFactoryKeyUsesIdentityAndEnforcesUtf8Bound() {
        var maximum = idWithTotalBytes(128);
        var first = new ClientFactoryKey<TestConfiguration>(maximum);
        var second = new ClientFactoryKey<TestConfiguration>(maximum);

        assertSame(maximum, first.id());
        assertNotEquals(first, second);
        assertEquals(System.identityHashCode(first), first.hashCode());
        assertThrows(NullPointerException.class, () -> new ClientFactoryKey<>(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClientFactoryKey<>(idWithTotalBytes(129)));
    }

    @Test
    void profileCostAcceptsEveryInclusiveBoundary() {
        assertEquals(new ProfileCost(0, 0, 0, 0, 1), new ProfileCost(0, 0, 0, 0, 1));
        assertEquals(
                new ProfileCost(256, 8, 1, 48, 120),
                new ProfileCost(256, 8, 1, 48, 120));
    }

    @Test
    void profileCostRejectsEveryOutOfRangeDimension() {
        assertThrows(IllegalArgumentException.class, () -> new ProfileCost(-1, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ProfileCost(257, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ProfileCost(0, -1, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ProfileCost(0, 9, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ProfileCost(0, 0, -1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ProfileCost(0, 0, 2, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ProfileCost(0, 0, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ProfileCost(0, 0, 0, 49, 1));
        assertThrows(IllegalArgumentException.class, () -> new ProfileCost(0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProfileCost(0, 0, 0, 0, 121));
    }

    @Test
    void capabilitiesEnforceBoundedImmutableParameterPolicy() {
        var mutable = new HashMap<ResourceLocation, AppearanceParameterPolicy.IntRange>();
        for (var index = 0; index < 16; index++) {
            mutable.put(id("key_" + index), new AppearanceParameterPolicy.IntRange(-index, index));
        }
        var policy = new AppearanceParameterPolicy(mutable);
        var capabilities = new ProfileTypeCapabilities(true, false, true, false, policy);
        mutable.clear();

        assertSame(policy, capabilities.adjustableParameters());
        assertEquals(16, capabilities.adjustableParameters().integerRanges().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> capabilities.adjustableParameters()
                        .integerRanges()
                        .put(id("later"), new AppearanceParameterPolicy.IntRange(0, 0)));
        assertThrows(
                NullPointerException.class,
                () -> new ProfileTypeCapabilities(false, false, false, false, null));
    }

    @Test
    void capabilitiesRejectParameterCountAndUtf8OneOver() {
        var seventeen = new HashMap<ResourceLocation, AppearanceParameterPolicy.IntRange>();
        for (var index = 0; index < 17; index++) {
            seventeen.put(id("key_" + index), new AppearanceParameterPolicy.IntRange(0, 0));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProfileTypeCapabilities(
                        false, false, false, false, new AppearanceParameterPolicy(seventeen)));
        assertEquals(
                1,
                new ProfileTypeCapabilities(
                                false,
                                false,
                                false,
                                false,
                                new AppearanceParameterPolicy(Map.of(
                                        idWithTotalBytes(32),
                                        new AppearanceParameterPolicy.IntRange(0, 0))))
                        .adjustableParameters()
                        .integerRanges()
                        .size());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProfileTypeCapabilities(
                        false,
                        false,
                        false,
                        false,
                        new AppearanceParameterPolicy(Map.of(
                                idWithTotalBytes(33),
                                new AppearanceParameterPolicy.IntRange(0, 0)))));
    }

    @Test
    void declaredPublicSurfaceIsTheAuthorizedCommonSubset() {
        assertTrue(ProfileConfiguration.class.isInterface());
        assertTrue(Modifier.isAbstract(ProfileConfiguration.class.getModifiers()));
        assertFalse(Modifier.isFinal(ProfileConfiguration.class.getModifiers()));
        assertEquals(Set.of(), declaredPublicFields(ProfileConfiguration.class));
        assertEquals(Set.of(), declaredPublicConstructors(ProfileConfiguration.class));
        assertEquals(Set.of(), productMethods(ProfileConfiguration.class));

        assertTrue(ProfileChannel.class.isEnum());
        assertTrue(Modifier.isFinal(ProfileChannel.class.getModifiers()));
        assertEquals(Set.of(), declaredPublicFields(ProfileChannel.class));
        assertEquals(Set.of(), declaredPublicConstructors(ProfileChannel.class));
        assertEquals(Set.of("wireCode", "fromWireCode"), productMethods(ProfileChannel.class));

        assertFalse(ClientFactoryKey.class.isInterface());
        assertFalse(ClientFactoryKey.class.isEnum());
        assertFalse(ClientFactoryKey.class.isRecord());
        assertTrue(Modifier.isFinal(ClientFactoryKey.class.getModifiers()));
        assertEquals(Set.of(), declaredPublicFields(ClientFactoryKey.class));
        assertEquals(Set.of("ResourceLocation"), declaredPublicConstructors(ClientFactoryKey.class));
        assertEquals(Set.of("id"), productMethods(ClientFactoryKey.class));

        assertTrue(ProfileCost.class.isRecord());
        assertTrue(Modifier.isFinal(ProfileCost.class.getModifiers()));
        assertEquals(Set.of(), declaredPublicFields(ProfileCost.class));
        assertEquals(
                Set.of("int,int,int,int,int"), declaredPublicConstructors(ProfileCost.class));
        assertEquals(
                Set.of(
                        "particleStarts",
                        "soundStarts",
                        "trailStarts",
                        "trailSegments",
                        "lifetimeTicks"),
                productMethods(ProfileCost.class));

        assertTrue(ProfileTypeCapabilities.class.isRecord());
        assertTrue(Modifier.isFinal(ProfileTypeCapabilities.class.getModifiers()));
        assertEquals(Set.of(), declaredPublicFields(ProfileTypeCapabilities.class));
        assertEquals(
                Set.of("boolean,boolean,boolean,boolean,AppearanceParameterPolicy"),
                declaredPublicConstructors(ProfileTypeCapabilities.class));
        assertEquals(
                Set.of(
                        "supportsPrimaryColor",
                        "supportsSecondaryColor",
                        "supportsDirection",
                        "supportsTrail",
                        "adjustableParameters"),
                productMethods(ProfileTypeCapabilities.class));

        assertTrue(ProfileType.class.isInterface());
        assertTrue(Modifier.isAbstract(ProfileType.class.getModifiers()));
        assertFalse(Modifier.isFinal(ProfileType.class.getModifiers()));
        assertEquals(Set.of(), declaredPublicFields(ProfileType.class));
        assertEquals(Set.of(), declaredPublicConstructors(ProfileType.class));
        assertEquals(
                Set.of(
                        "currentConfigurationVersion",
                        "channel",
                        "configurationCodec",
                        "clientFactoryKey",
                        "capabilities",
                        "estimateCost",
                        "validate"),
                productMethods(ProfileType.class));
        assertTrue(Arrays.stream(ProfileType.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .allMatch(method -> Modifier.isAbstract(method.getModifiers())));

        for (var type : List.of(
                ProfileConfiguration.class,
                ProfileChannel.class,
                ClientFactoryKey.class,
                ProfileType.class)) {
            assertTrue(
                    Arrays.stream(type.getDeclaredMethods()).noneMatch(ProfileApiTest::isObjectOverride),
                    type.getName());
        }
    }

    @Test
    void genericBoundsMethodDescriptorsAndIdentityOverridesAreExact() throws Exception {
        assertEquals(
                Set.of("wireCode()->int:instance:concrete", "fromWireCode(int)->Optional:static:concrete"),
                productMethodDescriptors(ProfileChannel.class));
        assertEquals(
                Set.of("id()->ResourceLocation:instance:concrete"),
                productMethodDescriptors(ClientFactoryKey.class));
        assertEquals(
                Set.of(
                        "particleStarts()->int:instance:concrete",
                        "soundStarts()->int:instance:concrete",
                        "trailStarts()->int:instance:concrete",
                        "trailSegments()->int:instance:concrete",
                        "lifetimeTicks()->int:instance:concrete"),
                productMethodDescriptors(ProfileCost.class));
        assertEquals(
                Set.of(
                        "supportsPrimaryColor()->boolean:instance:concrete",
                        "supportsSecondaryColor()->boolean:instance:concrete",
                        "supportsDirection()->boolean:instance:concrete",
                        "supportsTrail()->boolean:instance:concrete",
                        "adjustableParameters()->AppearanceParameterPolicy:instance:concrete"),
                productMethodDescriptors(ProfileTypeCapabilities.class));
        assertEquals(
                Set.of(
                        "currentConfigurationVersion()->int:instance:abstract",
                        "channel()->ProfileChannel:instance:abstract",
                        "configurationCodec()->MapCodec:instance:abstract",
                        "clientFactoryKey()->ClientFactoryKey:instance:abstract",
                        "capabilities()->ProfileTypeCapabilities:instance:abstract",
                        "estimateCost(ProfileConfiguration)->ProfileCost:instance:abstract",
                        "validate(ProfileConfiguration,ValidationContext)->ValidationResult:instance:abstract"),
                productMethodDescriptors(ProfileType.class));

        assertEquals(
                List.of(ProfileConfiguration.class.getName()),
                Arrays.stream(ClientFactoryKey.class.getTypeParameters()[0].getBounds())
                        .map(java.lang.reflect.Type::getTypeName)
                        .toList());
        assertEquals(
                List.of(ProfileConfiguration.class.getName()),
                Arrays.stream(ProfileType.class.getTypeParameters()[0].getBounds())
                        .map(java.lang.reflect.Type::getTypeName)
                        .toList());
        assertEquals(
                "com.mojang.serialization.MapCodec<C>",
                ProfileType.class.getDeclaredMethod("configurationCodec")
                        .getGenericReturnType().getTypeName());
        assertEquals(
                "com.yo1no.gramarye.magic.presentation.api.ClientFactoryKey<C>",
                ProfileType.class.getDeclaredMethod("clientFactoryKey")
                        .getGenericReturnType().getTypeName());
        assertEquals(
                "C",
                ProfileType.class.getDeclaredMethod("estimateCost", ProfileConfiguration.class)
                        .getGenericParameterTypes()[0].getTypeName());
        assertEquals(
                "C",
                ProfileType.class
                        .getDeclaredMethod(
                                "validate", ProfileConfiguration.class, ValidationContext.class)
                        .getGenericParameterTypes()[0]
                        .getTypeName());

        assertFalse(Arrays.stream(ClientFactoryKey.class.getDeclaredMethods())
                .anyMatch(method -> Set.of("equals", "hashCode").contains(method.getName())));
        for (var type : List.of(
                ProfileConfiguration.class,
                ProfileChannel.class,
                ClientFactoryKey.class,
                ProfileCost.class,
                ProfileTypeCapabilities.class,
                ProfileType.class)) {
            assertTrue(Arrays.stream(type.getDeclaredMethods())
                    .allMatch(method -> method.getExceptionTypes().length == 0));
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }

    private static ResourceLocation idWithTotalBytes(int bytes) {
        return ResourceLocation.fromNamespaceAndPath("a", "x".repeat(bytes - 2));
    }

    private static Set<String> declaredPublicFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .filter(field -> !field.isEnumConstant())
                .map(Field::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> declaredPublicConstructors(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                .map(ProfileApiTest::parameterSignature)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String parameterSignature(Constructor<?> constructor) {
        return Arrays.stream(constructor.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
    }

    private static Set<String> productMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .filter(method -> !isCompilerOwnedMethod(type, method))
                .map(Method::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> productMethodDescriptors(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .filter(method -> !isCompilerOwnedMethod(type, method))
                .map(method -> method.getName()
                        + Arrays.stream(method.getParameterTypes())
                                .map(Class::getSimpleName)
                                .collect(Collectors.joining(",", "(", ")"))
                        + "->" + method.getReturnType().getSimpleName()
                        + (Modifier.isStatic(method.getModifiers()) ? ":static" : ":instance")
                        + (Modifier.isAbstract(method.getModifiers()) ? ":abstract" : ":concrete"))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isCompilerOwnedMethod(Class<?> owner, Method method) {
        if (owner.isEnum()) {
            var valuesMethod = Modifier.isStatic(method.getModifiers())
                    && method.getName().equals("values")
                    && method.getParameterCount() == 0
                    && method.getReturnType().isArray()
                    && method.getReturnType().getComponentType() == owner;
            var valueOfMethod = Modifier.isStatic(method.getModifiers())
                    && method.getName().equals("valueOf")
                    && Arrays.equals(method.getParameterTypes(), new Class<?>[] {String.class})
                    && method.getReturnType() == owner;
            return valuesMethod || valueOfMethod;
        }
        return owner.isRecord()
                && Modifier.isPublic(method.getModifiers())
                && Modifier.isFinal(method.getModifiers())
                && isObjectOverride(method);
    }

    private static boolean isObjectOverride(Method method) {
        var equalsMethod = method.getName().equals("equals")
                && method.getReturnType() == boolean.class
                && Arrays.equals(method.getParameterTypes(), new Class<?>[] {Object.class});
        var hashCodeMethod = method.getName().equals("hashCode")
                && method.getReturnType() == int.class
                && method.getParameterCount() == 0;
        var toStringMethod = method.getName().equals("toString")
                && method.getReturnType() == String.class
                && method.getParameterCount() == 0;
        return equalsMethod || hashCodeMethod || toStringMethod;
    }

    private record TestConfiguration() implements ProfileConfiguration {}
}
