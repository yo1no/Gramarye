package com.yo1no.gramarye.magic.validation;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Stable, namespaced identity for a validation issue; it is not a human diagnostic. */
public record ValidationIssueCode(ResourceLocation value) {
    public ValidationIssueCode {
        Objects.requireNonNull(value, "value");
        requireBoundedPart(value.getNamespace(), "namespace");
        requireBoundedPart(value.getPath(), "path");
        if (value.toString().length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("validation issue code exceeds the string ceiling");
        }
    }

    public static ValidationIssueCode fromNamespaceAndPath(String namespace, String path) {
        requireBoundedPart(namespace, "namespace");
        requireBoundedPart(path, "path");
        var value = ResourceLocation.tryBuild(namespace, path);
        if (value == null) {
            throw new IllegalArgumentException("invalid validation issue code");
        }
        return new ValidationIssueCode(value);
    }

    private static void requireBoundedPart(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        if (value.length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
            throw new IllegalArgumentException(name + " exceeds the string ceiling");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
