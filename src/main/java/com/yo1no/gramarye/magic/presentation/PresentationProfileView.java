package com.yo1no.gramarye.magic.presentation;

import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileCost;
import com.yo1no.gramarye.magic.presentation.api.ProfileTypeCapabilities;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Pure typed lookup used by S1 without installing or retaining a live Profile catalog. */
@FunctionalInterface
interface PresentationProfileView {
    Optional<PresentationProfileDescriptor> find(ResourceLocation id);
}

/** Immutable S1 projection of the Profile facts needed for resolution and patch validation. */
record PresentationProfileDescriptor(
        ResourceLocation id,
        ProfileChannel channel,
        ProfileTypeCapabilities capabilities,
        ProfileCost estimatedCost) {
    PresentationProfileDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(estimatedCost, "estimatedCost");
        if (utf8Length(id) > PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES) {
            throw new IllegalArgumentException("Profile ID exceeds the P8 UTF-8 bound");
        }
    }

    private static int utf8Length(ResourceLocation id) {
        return id.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
