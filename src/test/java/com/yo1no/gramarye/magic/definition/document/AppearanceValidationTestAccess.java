package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.Dynamic;

/** Test-only construction seam for already-bounded quarantined appearance snapshots. */
public final class AppearanceValidationTestAccess {
    private AppearanceValidationTestAccess() {
    }

    public static AppearanceDocument unparsedTop(Dynamic<?> raw) {
        var snapshot = AppearanceRawSnapshot.capture(raw).result().orElseThrow();
        return new AppearanceDocument.Unparsed(snapshot);
    }

    public static AppearanceOverrideDocument unparsedOverride(Dynamic<?> raw) {
        var snapshot = AppearanceRawSnapshot.capture(raw).result().orElseThrow();
        return new AppearanceOverrideDocument.Unparsed(snapshot);
    }
}
