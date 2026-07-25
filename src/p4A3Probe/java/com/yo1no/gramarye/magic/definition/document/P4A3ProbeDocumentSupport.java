package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.Dynamic;
import java.util.Objects;

/** Test-only access to the package-internal unparsed appearance snapshot boundary. */
public final class P4A3ProbeDocumentSupport {
    private P4A3ProbeDocumentSupport() {
    }

    public static AppearanceDocument unparsedAppearance(Dynamic<?> raw) {
        return new AppearanceDocument.Unparsed(
                AppearanceRawSnapshot.capture(Objects.requireNonNull(raw, "raw"))
                        .getOrThrow());
    }

    public static AppearanceOverrideDocument unparsedOverride(Dynamic<?> raw) {
        return new AppearanceOverrideDocument.Unparsed(
                AppearanceRawSnapshot.capture(Objects.requireNonNull(raw, "raw"))
                        .getOrThrow());
    }
}
