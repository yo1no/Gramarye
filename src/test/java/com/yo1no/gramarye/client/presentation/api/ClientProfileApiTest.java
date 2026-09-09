package com.yo1no.gramarye.client.presentation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.presentation.api.ClientFactoryKey;
import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class ClientProfileApiTest {
    @Test
    void factoryHasExactGenericBoundTopLevelMethodsAndNestedVocabulary() throws Exception {
        assertTrue(Modifier.isPublic(ClientProfileFactory.class.getModifiers()));
        assertTrue(ClientProfileFactory.class.isInterface());
        assertEquals(1, ClientProfileFactory.class.getTypeParameters().length);
        assertEquals("C", ClientProfileFactory.class.getTypeParameters()[0].getName());
        assertEquals(
                List.of(ProfileConfiguration.class.getName()),
                Arrays.stream(ClientProfileFactory.class.getTypeParameters()[0].getBounds())
                        .map(java.lang.reflect.Type::getTypeName)
                        .toList());
        assertEquals(
                Set.of(
                        "Availability",
                        "Result",
                        "AssetView",
                        "Input",
                        "Output",
                        "Particle",
                        "Sound",
                        "Trail"),
                Arrays.stream(ClientProfileFactory.class.getDeclaredClasses())
                        .map(Class::getSimpleName)
                        .collect(Collectors.toUnmodifiableSet()));
        assertEquals(
                Set.of(
                        "availability(ProfileConfiguration,AssetView)->Availability:instance:abstract",
                        "present(ProfileConfiguration,Input,Output)->Result:instance:abstract"),
                productMethodDescriptors(ClientProfileFactory.class));

        var availability = ClientProfileFactory.class.getDeclaredMethod(
                "availability",
                ProfileConfiguration.class,
                ClientProfileFactory.AssetView.class);
        var present = ClientProfileFactory.class.getDeclaredMethod(
                "present",
                ProfileConfiguration.class,
                ClientProfileFactory.Input.class,
                ClientProfileFactory.Output.class);
        assertEquals("C", availability.getGenericParameterTypes()[0].getTypeName());
        assertEquals("C", present.getGenericParameterTypes()[0].getTypeName());
        assertEquals(ClientProfileFactory.Availability.class, availability.getReturnType());
        assertEquals(ClientProfileFactory.Result.class, present.getReturnType());
        assertEquals(
                List.of(
                        ClientProfileFactory.Availability.AVAILABLE,
                        ClientProfileFactory.Availability.UNAVAILABLE),
                List.of(ClientProfileFactory.Availability.values()));
        assertEquals(
                List.of(
                        ClientProfileFactory.Result.PRESENTED,
                        ClientProfileFactory.Result.UNAVAILABLE),
                List.of(ClientProfileFactory.Result.values()));
    }

    @Test
    void assetInputAndOutputRolesHaveOnlyTheExactAbstractOperations() {
        assertExactPublicInterface(
                ClientProfileFactory.AssetView.class,
                Set.of(
                        "soundExists(ResourceLocation)->boolean:instance:abstract",
                        "particleExists(ResourceLocation)->boolean:instance:abstract",
                        "resourceExists(ResourceLocation)->boolean:instance:abstract"));
        assertExactPublicInterface(
                ClientProfileFactory.Input.class,
                Set.of(
                        "eventKindCode()->int:instance:abstract",
                        "sourceEntityId()->OptionalInt:instance:abstract",
                        "targetEntityId()->OptionalInt:instance:abstract",
                        "x()->double:instance:abstract",
                        "y()->double:instance:abstract",
                        "z()->double:instance:abstract",
                        "directionX()->float:instance:abstract",
                        "directionY()->float:instance:abstract",
                        "directionZ()->float:instance:abstract",
                        "primaryArgb()->int:instance:abstract",
                        "secondaryArgb()->int:instance:abstract",
                        "intensity()->int:instance:abstract",
                        "override(ResourceLocation)->OptionalInt:instance:abstract",
                        "visualSeed()->long:instance:abstract",
                        "sequence()->long:instance:abstract"));
        assertExactPublicInterface(
                ClientProfileFactory.Output.class,
                Set.of(
                        "particle(Particle)->boolean:instance:abstract",
                        "sound(Sound)->boolean:instance:abstract",
                        "trail(Trail)->boolean:instance:abstract"));
    }

    @Test
    void commandRecordsHaveExactComponentsAndNullIdentityGuards() {
        assertRecordComponents(
                ClientProfileFactory.Particle.class,
                List.of(
                        "particleTypeId:ResourceLocation",
                        "x:double",
                        "y:double",
                        "z:double",
                        "velocityX:double",
                        "velocityY:double",
                        "velocityZ:double",
                        "argb:int",
                        "size:float",
                        "lifetimeTicks:int"));
        assertRecordComponents(
                ClientProfileFactory.Sound.class,
                List.of(
                        "soundEventId:ResourceLocation",
                        "x:double",
                        "y:double",
                        "z:double",
                        "volume:float",
                        "pitch:float"));
        assertRecordComponents(
                ClientProfileFactory.Trail.class,
                List.of(
                        "particleTypeId:ResourceLocation",
                        "trackedEntityId:OptionalInt",
                        "x:double",
                        "y:double",
                        "z:double",
                        "argb:int",
                        "size:float",
                        "lifetimeTicks:int",
                        "sampleIntervalTicks:int"));

        assertThrows(
                NullPointerException.class,
                () -> new ClientProfileFactory.Particle(
                        null, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0, 1.0F, 1));
        assertThrows(
                NullPointerException.class,
                () -> new ClientProfileFactory.Sound(
                        null, 0.0D, 0.0D, 0.0D, 1.0F, 1.0F));
        assertThrows(
                NullPointerException.class,
                () -> new ClientProfileFactory.Trail(
                        null,
                        OptionalInt.empty(),
                        0.0D,
                        0.0D,
                        0.0D,
                        0,
                        1.0F,
                        1,
                        1));
        assertThrows(
                NullPointerException.class,
                () -> new ClientProfileFactory.Trail(
                        id("particle"), null, 0.0D, 0.0D, 0.0D, 0, 1.0F, 1, 1));
    }

    @Test
    void registrationIsAnExactTypedIdentityRecord() {
        assertTrue(Modifier.isPublic(ClientProfileFactoryRegistration.class.getModifiers()));
        assertTrue(Modifier.isFinal(ClientProfileFactoryRegistration.class.getModifiers()));
        assertTrue(ClientProfileFactoryRegistration.class.isRecord());
        assertEquals(1, ClientProfileFactoryRegistration.class.getTypeParameters().length);
        assertEquals(
                List.of(ProfileConfiguration.class.getName()),
                Arrays.stream(
                                ClientProfileFactoryRegistration.class
                                        .getTypeParameters()[0]
                                        .getBounds())
                        .map(java.lang.reflect.Type::getTypeName)
                        .toList());
        assertRecordComponents(
                ClientProfileFactoryRegistration.class,
                List.of("key:ClientFactoryKey", "factory:ClientProfileFactory"));
        assertEquals(
                "com.yo1no.gramarye.magic.presentation.api.ClientFactoryKey<C>",
                ClientProfileFactoryRegistration.class.getRecordComponents()[0]
                        .getGenericType()
                        .getTypeName());
        assertEquals(
                "com.yo1no.gramarye.client.presentation.api.ClientProfileFactory<C>",
                ClientProfileFactoryRegistration.class.getRecordComponents()[1]
                        .getGenericType()
                        .getTypeName());

        var key = new ClientFactoryKey<TestConfiguration>(id("factory"));
        ClientProfileFactory<TestConfiguration> factory = new TestFactory();
        var registration = new ClientProfileFactoryRegistration<>(key, factory);
        assertSame(key, registration.key());
        assertSame(factory, registration.factory());
        assertThrows(
                NullPointerException.class,
                () -> new ClientProfileFactoryRegistration<TestConfiguration>(null, factory));
        assertThrows(
                NullPointerException.class,
                () -> new ClientProfileFactoryRegistration<TestConfiguration>(key, null));
    }

    @Test
    void registryOwnerHasOnlyTheApprovedPublicStaticSurface() throws Exception {
        assertTrue(Modifier.isPublic(ClientProfileFactories.class.getModifiers()));
        assertTrue(Modifier.isFinal(ClientProfileFactories.class.getModifiers()));
        assertFalse(ClientProfileFactories.class.isInterface());
        assertEquals(Set.of("REGISTRY_KEY"), Arrays.stream(ClientProfileFactories.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toUnmodifiableSet()));
        var keyField = ClientProfileFactories.class.getDeclaredField("REGISTRY_KEY");
        assertTrue(Modifier.isStatic(keyField.getModifiers()));
        assertTrue(Modifier.isFinal(keyField.getModifiers()));
        assertEquals(
                Set.of("registry()->Registry:static:concrete"),
                productMethodDescriptors(ClientProfileFactories.class));
        assertEquals(1, ClientProfileFactories.class.getDeclaredConstructors().length);
        assertTrue(Modifier.isPrivate(
                ClientProfileFactories.class.getDeclaredConstructors()[0].getModifiers()));
        assertEquals(id("client_profile_factory"), ClientProfileFactories.REGISTRY_KEY.location());
    }

    @Test
    void clientApiDeclaresNoCheckedExceptionsOrThrowableRetention() {
        for (var type : List.of(
                ClientProfileFactory.class,
                ClientProfileFactory.Availability.class,
                ClientProfileFactory.Result.class,
                ClientProfileFactory.AssetView.class,
                ClientProfileFactory.Input.class,
                ClientProfileFactory.Output.class,
                ClientProfileFactory.Particle.class,
                ClientProfileFactory.Sound.class,
                ClientProfileFactory.Trail.class,
                ClientProfileFactoryRegistration.class,
                ClientProfileFactories.class)) {
            assertTrue(
                    Arrays.stream(type.getDeclaredMethods())
                            .allMatch(method -> method.getExceptionTypes().length == 0),
                    type.getName());
            assertTrue(
                    Arrays.stream(type.getDeclaredFields())
                            .noneMatch(field -> Throwable.class.isAssignableFrom(field.getType())),
                    type.getName());
        }
    }

    private static void assertExactPublicInterface(
            Class<?> type, Set<String> expectedDescriptors) {
        assertTrue(Modifier.isPublic(type.getModifiers()), type.getName());
        assertTrue(type.isInterface(), type.getName());
        assertEquals(expectedDescriptors, productMethodDescriptors(type));
        assertTrue(
                Arrays.stream(type.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .allMatch(method -> Modifier.isAbstract(method.getModifiers())),
                type.getName());
    }

    private static void assertRecordComponents(
            Class<?> type, List<String> expectedComponents) {
        assertTrue(Modifier.isPublic(type.getModifiers()), type.getName());
        assertTrue(Modifier.isFinal(type.getModifiers()), type.getName());
        assertTrue(type.isRecord(), type.getName());
        var components = type.getRecordComponents();
        assertEquals(
                expectedComponents,
                Arrays.stream(components)
                        .map(component -> component.getName()
                                + ':' + component.getType().getSimpleName())
                        .toList());
        var publicConstructors = Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                .toList();
        assertEquals(1, publicConstructors.size(), type.getName());
        assertTrue(
                Arrays.equals(
                        Arrays.stream(components)
                                .map(java.lang.reflect.RecordComponent::getType)
                                .toArray(Class<?>[]::new),
                        publicConstructors.get(0).getParameterTypes()),
                type.getName());
        assertEquals(
                Arrays.stream(components)
                        .map(component -> component.getName()
                                + "()->" + component.getType().getSimpleName()
                                + ":instance:concrete")
                        .collect(Collectors.toUnmodifiableSet()),
                productMethodDescriptors(type));
    }

    private static Set<String> productMethodDescriptors(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .filter(method -> !isCompilerOwnedMethod(type, method))
                .map(ClientProfileApiTest::descriptor)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String descriptor(Method method) {
        return method.getName()
                + Arrays.stream(method.getParameterTypes())
                        .map(Class::getSimpleName)
                        .collect(Collectors.joining(",", "(", ")"))
                + "->" + method.getReturnType().getSimpleName()
                + (Modifier.isStatic(method.getModifiers()) ? ":static" : ":instance")
                + (Modifier.isAbstract(method.getModifiers()) ? ":abstract" : ":concrete");
    }

    private static boolean isCompilerOwnedMethod(Class<?> owner, Method method) {
        if (owner.isEnum()) {
            return method.getName().equals("values") || method.getName().equals("valueOf");
        }
        return owner.isRecord()
                && Set.of("equals", "hashCode", "toString").contains(method.getName());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }

    private record TestConfiguration(int value) implements ProfileConfiguration {}

    private static final class TestFactory implements ClientProfileFactory<TestConfiguration> {
        @Override
        public Availability availability(TestConfiguration configuration, AssetView assets) {
            return Availability.AVAILABLE;
        }

        @Override
        public Result present(
                TestConfiguration configuration, Input input, Output output) {
            return Result.PRESENTED;
        }
    }
}
