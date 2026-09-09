package com.yo1no.gramarye.client.presentation.api;

import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.presentation.api.ClientFactoryKey;
import com.yo1no.gramarye.magic.presentation.api.ProfileType;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Client-only startup-frozen registry of typed Profile factory bindings. */
public final class ClientProfileFactories {
    public static final ResourceKey<Registry<ClientProfileFactoryRegistration<?>>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(
                            Gramarye.MOD_ID, "client_profile_factory"));

    private ClientProfileFactories() {
        throw new AssertionError("no instances");
    }

    public static Registry<ClientProfileFactoryRegistration<?>> registry() {
        return RegistryHolder.REGISTRY;
    }

    private static final class RegistryHolder {
        private static final Registry<ClientProfileFactoryRegistration<?>> REGISTRY =
                new RegistryBuilder<ClientProfileFactoryRegistration<?>>(REGISTRY_KEY)
                        .maxId(63)
                        .sync(false)
                        .onBake(ClientProfileFactories::validateRegistry)
                        .create();
    }

    private static void validateRegistry(
            Registry<ClientProfileFactoryRegistration<?>> registry) {
        Objects.requireNonNull(registry, "registry");
        if (registry.size() > 64) {
            throw new IllegalStateException(
                    "Client Profile factory registry exceeds 64 entries");
        }

        var profileTypes = MagicRegistries.profileTypeRegistry();
        var matchedKeys = Collections.newSetFromMap(
                new IdentityHashMap<ClientFactoryKey<?>, Boolean>());
        for (var entry : registry.entrySet()) {
            var entryId = entry.getKey().location();
            var registration = Objects.requireNonNull(
                    entry.getValue(), "registered client Profile factory");
            var key = Objects.requireNonNull(
                    registration.key(), "registered client Profile factory key");
            Objects.requireNonNull(
                    registration.factory(), "registered client Profile factory implementation");
            if (!entryId.equals(key.id())) {
                throw new IllegalStateException(
                        "Client Profile factory registry ID does not match its key");
            }

            var matchingTypes = 0;
            for (ProfileType<?> type : profileTypes) {
                var typeKey = Objects.requireNonNull(
                        type.clientFactoryKey(), "Profile client factory key");
                if (typeKey == key) {
                    matchingTypes++;
                }
            }
            if (matchingTypes != 1 || !matchedKeys.add(key)) {
                throw new IllegalStateException(
                        "Client Profile factory key must identify exactly one common Profile type");
            }
        }

        requireBuiltIn(registry, profileTypes, "sound");
        requireBuiltIn(registry, profileTypes, "particle");
        requireBuiltIn(registry, profileTypes, "trail");
    }

    private static void requireBuiltIn(
            Registry<ClientProfileFactoryRegistration<?>> factories,
            Registry<ProfileType<?>> profileTypes,
            String path) {
        var id = ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
        var type = profileTypes.getOptional(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing built-in common Profile type: " + id));
        var registration = factories.getOptional(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing built-in client Profile factory: " + id));
        if (registration.key() != type.clientFactoryKey()) {
            throw new IllegalStateException(
                    "Built-in client Profile factory key identity mismatch: " + id);
        }
    }
}
