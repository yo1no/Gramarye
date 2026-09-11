package com.yo1no.gramarye.client.presentation.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.presentation.api.ClientFactoryKey;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import com.yo1no.gramarye.magic.presentation.api.ProfileCost;
import com.yo1no.gramarye.magic.presentation.api.ProfileType;
import com.yo1no.gramarye.magic.presentation.api.ProfileTypeCapabilities;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.junit.jupiter.api.Test;

final class ClientProfileFactoriesRegistryTest {
    private static final ClientProfileFactory<TestConfiguration> FACTORY =
            new ClientProfileFactory<>() {
                @Override
                public Availability availability(
                        TestConfiguration configuration, AssetView assets) {
                    return Availability.AVAILABLE;
                }

                @Override
                public Result present(
                        TestConfiguration configuration, Input input, Output output) {
                    return Result.PRESENTED;
                }
            };

    @Test
    void actualFactoryRegistryAcceptsExactSixtyFourAndFailsStartupWithoutPartialOmission() {
        var exact = registries("exact");
        registerPairs(exact, false);

        assertEquals(64, exact.profileTypes().size());
        assertEquals(64, exact.factories().size());
        assertDoesNotThrow(exact.profileTypes()::freeze);
        assertDoesNotThrow(exact.factories()::freeze);
        assertThrows(
                IllegalStateException.class,
                () -> Registry.register(
                        exact.factories(),
                        id("late_factory"),
                        new ClientProfileFactoryRegistration<>(
                                new ClientFactoryKey<>(id("late_factory")), FACTORY)));
        assertEquals(64, exact.factories().size());

        var oneOver = registries("one_over");
        registerPairs(oneOver, false);
        assertDoesNotThrow(oneOver.profileTypes()::freeze);
        assertThrows(
                IllegalStateException.class,
                () -> Registry.register(
                        oneOver.factories(),
                        id("capacity_factory_64"),
                        new ClientProfileFactoryRegistration<>(
                                new ClientFactoryKey<>(id("capacity_factory_64")), FACTORY)));
        assertEquals(64, oneOver.factories().size());
        assertDoesNotThrow(oneOver.factories()::freeze);

        var mismatch = registries("identity_mismatch");
        registerPairs(mismatch, true);
        assertDoesNotThrow(mismatch.profileTypes()::freeze);
        assertEquals(64, mismatch.factories().size());
        assertThrows(IllegalStateException.class, mismatch.factories()::freeze);
        assertEquals(64, mismatch.factories().size());
    }

    private static FactoryRegistries registries(String suffix) {
        ResourceKey<Registry<ProfileType<?>>> profileTypeKey =
                ResourceKey.createRegistryKey(id("pc1_" + suffix + "_profile_types"));
        Registry<ProfileType<?>> profileTypes = new RegistryBuilder<ProfileType<?>>(profileTypeKey)
                .maxId(63)
                .sync(false)
                .disableRegistrationCheck()
                .create();
        ResourceKey<Registry<ClientProfileFactoryRegistration<?>>> factoryKey =
                ResourceKey.createRegistryKey(id("pc1_" + suffix + "_client_factories"));
        Registry<ClientProfileFactoryRegistration<?>> factories =
                new RegistryBuilder<ClientProfileFactoryRegistration<?>>(factoryKey)
                        .maxId(63)
                        .sync(false)
                        .onBake(registry ->
                                ClientProfileFactories.validateRegistry(registry, profileTypes))
                        .disableRegistrationCheck()
                        .create();
        return new FactoryRegistries(profileTypes, factories);
    }

    private static void registerPairs(
            FactoryRegistries registries, boolean mismatchLastIdentity) {
        for (var index = 0; index < 64; index++) {
            var profileId = switch (index) {
                case 0 -> id("sound");
                case 1 -> id("particle");
                case 2 -> id("trail");
                default -> id("capacity_factory_%02d".formatted(index));
            };
            var typeKey = new ClientFactoryKey<TestConfiguration>(profileId);
            Registry.register(registries.profileTypes(), profileId, profileType(typeKey));
            var registrationKey = mismatchLastIdentity && index == 63
                    ? new ClientFactoryKey<TestConfiguration>(profileId)
                    : typeKey;
            Registry.register(
                    registries.factories(),
                    profileId,
                    new ClientProfileFactoryRegistration<>(registrationKey, FACTORY));
        }
    }

    private static ProfileType<TestConfiguration> profileType(
            ClientFactoryKey<TestConfiguration> factoryKey) {
        return new ProfileType<>() {
            @Override
            public int currentConfigurationVersion() {
                return 0;
            }

            @Override
            public ProfileChannel channel() {
                return ProfileChannel.SOUND;
            }

            @Override
            public MapCodec<TestConfiguration> configurationCodec() {
                return MapCodec.unit(TestConfiguration.INSTANCE);
            }

            @Override
            public ClientFactoryKey<TestConfiguration> clientFactoryKey() {
                return factoryKey;
            }

            @Override
            public ProfileTypeCapabilities capabilities() {
                return new ProfileTypeCapabilities(
                        false,
                        false,
                        false,
                        false,
                        AppearanceParameterPolicy.none());
            }

            @Override
            public ProfileCost estimateCost(TestConfiguration configuration) {
                return new ProfileCost(0, 1, 0, 0, 1);
            }

            @Override
            public ValidationResult validate(
                    TestConfiguration configuration, ValidationContext context) {
                return ValidationResult.valid();
            }
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }

    private enum TestConfiguration implements ProfileConfiguration {
        INSTANCE
    }

    private record FactoryRegistries(
            Registry<ProfileType<?>> profileTypes,
            Registry<ClientProfileFactoryRegistration<?>> factories) {}
}
