package com.yo1no.gramarye.magic.presentation.api;

import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Immutable declarative capability bounds for one startup-frozen Profile type. */
public record ProfileTypeCapabilities(
        boolean supportsPrimaryColor,
        boolean supportsSecondaryColor,
        boolean supportsDirection,
        boolean supportsTrail,
        AppearanceParameterPolicy adjustableParameters) {
    private static final int MAX_ADJUSTABLE_PARAMETERS = 16;
    private static final int MAX_PARAMETER_KEY_UTF8_BYTES = 32;

    public ProfileTypeCapabilities {
        adjustableParameters = Objects.requireNonNull(adjustableParameters, "adjustableParameters");
        var integerRanges = adjustableParameters.integerRanges();
        if (integerRanges.size() > MAX_ADJUSTABLE_PARAMETERS) {
            throw new IllegalArgumentException("adjustableParameters exceeds 16 entries");
        }
        for (var key : integerRanges.keySet()) {
            if (key.toString().getBytes(StandardCharsets.UTF_8).length
                    > MAX_PARAMETER_KEY_UTF8_BYTES) {
                throw new IllegalArgumentException(
                        "adjustable parameter key exceeds 32 UTF-8 bytes: " + key);
            }
        }
    }
}
