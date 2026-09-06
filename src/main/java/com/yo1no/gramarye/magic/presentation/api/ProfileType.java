package com.yo1no.gramarye.magic.presentation.api;

import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;

/** Startup-frozen common descriptor for one typed presentation Profile. */
public interface ProfileType<C extends ProfileConfiguration> {
    int currentConfigurationVersion();

    ProfileChannel channel();

    MapCodec<C> configurationCodec();

    ClientFactoryKey<C> clientFactoryKey();

    ProfileTypeCapabilities capabilities();

    ProfileCost estimateCost(C configuration);

    ValidationResult validate(C configuration, ValidationContext context);
}
