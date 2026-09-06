package com.yo1no.gramarye.magic.presentation.api;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Identity-bearing binding witness shared by one common Profile type and its client factory. */
public final class ClientFactoryKey<C extends ProfileConfiguration> {
    private static final int MAX_ID_UTF8_BYTES = 128;

    private final ResourceLocation id;

    public ClientFactoryKey(ResourceLocation id) {
        this.id = Objects.requireNonNull(id, "id");
        if (id.toString().getBytes(StandardCharsets.UTF_8).length > MAX_ID_UTF8_BYTES) {
            throw new IllegalArgumentException("id exceeds 128 UTF-8 bytes");
        }
    }

    public ResourceLocation id() {
        return id;
    }
}
