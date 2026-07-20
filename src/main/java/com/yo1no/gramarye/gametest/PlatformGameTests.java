package com.yo1no.gramarye.gametest;

import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlatformGameTests {
    private PlatformGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void dedicatedServerLoads(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void customDescriptorRegistriesLoadEmpty(GameTestHelper helper) {
        helper.assertTrue(
                MagicRegistries.TRIGGER_TYPE_REGISTRY_KEY.location().equals(registryLocation("trigger_type")),
                "Trigger descriptor registry key must be gramarye:trigger_type");
        helper.assertTrue(
                MagicRegistries.ACTION_TYPE_REGISTRY_KEY.location().equals(registryLocation("action_type")),
                "Action descriptor registry key must be gramarye:action_type");
        assertEmptyDescriptorRegistry(helper, MagicRegistries.TRIGGER_TYPE_REGISTRY_KEY);
        assertEmptyDescriptorRegistry(helper, MagicRegistries.ACTION_TYPE_REGISTRY_KEY);
        helper.succeed();
    }

    private static void assertEmptyDescriptorRegistry(
            GameTestHelper helper,
            ResourceKey<? extends Registry<?>> registryKey) {
        var registry = BuiltInRegistries.REGISTRY.getOptional(registryKey.location()).orElseThrow();

        helper.assertTrue(registry.key().equals(registryKey), "Descriptor registry has the wrong registry key");
        helper.assertTrue(registry.size() == 0, "Descriptor registry must be empty in P2-A");
        helper.assertFalse(registry instanceof DefaultedRegistry<?>, "Descriptor registry must not have a default entry");
        helper.assertFalse(registry.doesSync(), "Descriptor registry must not sync numeric IDs");
    }

    private static ResourceLocation registryLocation(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }
}
