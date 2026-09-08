package com.yo1no.gramarye.gametest;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.definition.action.UnknownActionDefinition;
import com.yo1no.gramarye.magic.definition.codec.ActionDefinitionCodec;
import com.yo1no.gramarye.magic.definition.codec.TriggerDefinitionCodec;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import com.yo1no.gramarye.magic.definition.lookup.RegistryActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.RegistryTriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.migration.DescriptorMigrationAudit;
import com.yo1no.gramarye.magic.definition.trigger.UnknownTriggerDefinition;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
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
        assertCurrentDescriptorRegistryState(
                helper,
                MagicRegistries.TRIGGER_TYPE_REGISTRY_KEY,
                MagicRegistries.triggerTypeRegistry());
        assertCurrentDescriptorRegistryState(
                helper,
                MagicRegistries.ACTION_TYPE_REGISTRY_KEY,
                MagicRegistries.actionTypeRegistry());
        assertCurrentProfileRegistryState(helper);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void productionDefinitionLookupsResolveMissingTypesSafely(GameTestHelper helper) {
        var missingId = ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "p2_b_missing_type");
        var triggerLookup = new RegistryTriggerTypeLookup();
        var actionLookup = new RegistryActionTypeLookup();
        helper.assertTrue(triggerLookup.find(missingId).isEmpty(), "Missing trigger type must return empty");
        helper.assertTrue(actionLookup.find(missingId).isEmpty(), "Missing action type must return empty");

        var envelope = new DefinitionEnvelope(
                missingId,
                0,
                new Dynamic<>(JsonOps.INSTANCE, new JsonObject()));
        var trigger = TriggerDefinitionCodec.resolve(envelope, triggerLookup);
        var action = ActionDefinitionCodec.resolve(envelope, actionLookup);
        helper.assertTrue(
                trigger instanceof UnknownTriggerDefinition unknown
                        && unknown.failure().code() == DefinitionFailure.Code.UNKNOWN_TYPE,
                "Missing trigger type must resolve to UnknownTriggerDefinition");
        helper.assertTrue(
                action instanceof UnknownActionDefinition unknown
                        && unknown.failure().code() == DefinitionFailure.Code.UNKNOWN_TYPE,
                "Missing action type must resolve to UnknownActionDefinition");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void descriptorMigrationCoverageAuditPassesAfterRegistryFreeze(GameTestHelper helper) {
        var failure = DescriptorMigrationAudit.audit(
                MagicRegistries.triggerTypeRegistry(),
                MagicRegistries.actionTypeRegistry());
        helper.assertTrue(
                failure.isEmpty(),
                "Production descriptor and skill migration plans must cover their current schemas");
        helper.succeed();
    }

    private static void assertCurrentDescriptorRegistryState(
            GameTestHelper helper,
            ResourceKey<? extends Registry<?>> registryKey,
            Registry<?> formalRegistry) {
        var registry = BuiltInRegistries.REGISTRY.getOptional(registryKey.location()).orElseThrow();

        helper.assertTrue(registry.key().equals(registryKey), "Descriptor registry has the wrong registry key");
        helper.assertTrue(registry == formalRegistry, "Lookup adapter must expose the formally registered registry");
        // This is a phase-state assertion. Update it when the first production descriptor is introduced.
        helper.assertTrue(registry.size() == 0, "P2-B currently has no production descriptor entries");
        helper.assertFalse(registry instanceof DefaultedRegistry<?>, "Descriptor registry must not have a default entry");
        helper.assertFalse(registry.doesSync(), "Descriptor registry must not sync numeric IDs");
    }

    private static void assertCurrentProfileRegistryState(GameTestHelper helper) {
        var registry = BuiltInRegistries.REGISTRY
                .getOptional(MagicRegistries.PROFILE_TYPE_REGISTRY_KEY.location())
                .orElseThrow();
        var profiles = MagicRegistries.profileTypeRegistry();
        helper.assertTrue(
                registry.key().equals(MagicRegistries.PROFILE_TYPE_REGISTRY_KEY),
                "Profile registry has the wrong registry key");
        helper.assertTrue(
                registry == profiles,
                "Profile lookup must expose the formally registered registry");
        helper.assertFalse(
                registry instanceof DefaultedRegistry<?>,
                "Profile registry must not have a default entry");
        helper.assertFalse(registry.doesSync(), "Profile registry must not sync numeric IDs");
        helper.assertTrue(
                profiles.getMaxId() == 63,
                "Profile registry must enforce the authority-defined maximum ID");
        helper.assertTrue(
                profiles.size() >= 3 && profiles.size() <= 64,
                "Profile registry must retain all built-ins within its extension capacity");

        var requiredBuiltIns = java.util.Map.of(
                registryLocation("sound"), ProfileChannel.SOUND,
                registryLocation("particle"), ProfileChannel.PARTICLE,
                registryLocation("trail"), ProfileChannel.TRAIL);
        for (var entry : requiredBuiltIns.entrySet()) {
            var type = profiles.getOptional(entry.getKey()).orElseThrow();
            helper.assertTrue(
                    type.channel() == entry.getValue(),
                    "Built-in Profile type must retain its exact channel");
            helper.assertTrue(
                    type.currentConfigurationVersion() == 0,
                    "Built-in Profile type must use configuration version zero");
            helper.assertTrue(
                    type.clientFactoryKey().id().equals(entry.getKey()),
                    "Built-in type and factory-key IDs must match");
        }
    }

    private static ResourceLocation registryLocation(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }
}
