package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import net.minecraft.resources.ResourceLocation;

/** Read-only presentation availability port; P8 supplies any registry-backed adapter. */
@FunctionalInterface
public interface ProfileAvailabilityView {
    ProfileAvailability availability(AppearanceField field, ResourceLocation profileId);

    static ProfileAvailabilityView unknown() {
        return (field, profileId) -> ProfileAvailability.UNKNOWN;
    }
}
