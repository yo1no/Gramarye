package com.yo1no.gramarye.client.presentation.api;

import com.yo1no.gramarye.magic.presentation.api.ClientFactoryKey;
import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import java.util.Objects;

/** One immutable client factory registration bound by an identity-bearing common key. */
public record ClientProfileFactoryRegistration<C extends ProfileConfiguration>(
        ClientFactoryKey<C> key,
        ClientProfileFactory<C> factory) {
    public ClientProfileFactoryRegistration {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(factory, "factory");
    }
}
