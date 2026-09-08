package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileCost;
import com.yo1no.gramarye.magic.presentation.api.ProfileTypeCapabilities;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

final class PresentationTestFixtures {
    private PresentationTestFixtures() {}

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    static PresentationProfileDescriptor descriptor(
            ResourceLocation id,
            ProfileChannel channel,
            Map<ResourceLocation, AppearanceParameterPolicy.IntRange> ranges) {
        return new PresentationProfileDescriptor(
                id,
                channel,
                new ProfileTypeCapabilities(
                        true,
                        true,
                        true,
                        channel == ProfileChannel.TRAIL,
                        new AppearanceParameterPolicy(ranges)),
                new ProfileCost(0, 0, 0, 0, 1));
    }

    static PresentationProfileView view(PresentationProfileDescriptor... descriptors) {
        var byId = new LinkedHashMap<ResourceLocation, PresentationProfileDescriptor>();
        Arrays.stream(descriptors).forEach(descriptor -> {
            if (byId.put(descriptor.id(), descriptor) != null) {
                throw new IllegalArgumentException("duplicate fixture Profile ID");
            }
        });
        var immutable = Map.copyOf(byId);
        return id -> Optional.ofNullable(immutable.get(id));
    }

    static PresentationProfileView defaults() {
        return view(
                descriptor(
                        AppearanceSemantics.DEFAULT_SOUND_PROFILE,
                        ProfileChannel.SOUND,
                        Map.of()),
                descriptor(
                        AppearanceSemantics.DEFAULT_PARTICLE_PROFILE,
                        ProfileChannel.PARTICLE,
                        Map.of()),
                descriptor(
                        AppearanceSemantics.DEFAULT_TRAIL_PROFILE,
                        ProfileChannel.TRAIL,
                        Map.of()));
    }
}
