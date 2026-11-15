package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class V0StartupConfigurationTest {
    private static final List<String> NUMERIC_KEYS = List.of(
            "maxLineagesPerAttribution", "maxLineagesPerServer", "rootAttemptsPerTick",
            "maxManifestEntries", "projectileMaxAgeTicks", "projectileMaxRangeBlocks",
            "lineageDeadlineTicks", "presentationRecipientsPerOffer", "positiveRootFee");
    private static final List<Long> MINIMUM = List.of(1L, 1L, 1L, 1L, 1L, 1L, 3L, 1L, 1L);
    private static final List<Long> DEFAULT = List.of(1L, 2L, 1L, 4L, 16L, 16L, 24L, 8L, 1L);
    private static final List<Long> HARD = List.of(2L, 4L, 2L, 8L, 24L, 24L, 40L, 16L, 4L);

    @Test
    void absentDisabledAndUnavailableStatesRemainDistinct() {
        var state = new AtomicReference<>(P11ServerConfigCandidate.unavailable());
        var spec = new P5RawServerConfigSpec(state);
        assertSame(V0StartupLoadState.Unavailable.INSTANCE, state.get().v0State());

        var raw = CommentedConfig.inMemory();
        spec.correct(raw);
        assertFalse(raw.contains("v0"));
        spec.acceptRawConfig(raw);
        assertSame(V0StartupLoadState.Disabled.INSTANCE, state.get().v0State());

        raw.set("v0.enabled", false);
        spec.acceptRawConfig(raw);
        assertSame(V0StartupLoadState.Disabled.INSTANCE, state.get().v0State());
        raw.set("v0.projectileMaxRangeBlocks", 1);
        spec.acceptRawConfig(raw);
        assertSame(V0StartupLoadState.Disabled.INSTANCE, state.get().v0State());

        setDefaultProfile(raw, false);
        spec.acceptRawConfig(raw);
        assertSame(V0StartupLoadState.Disabled.INSTANCE, state.get().v0State());
        spec.acceptConfig(null);
        assertSame(V0StartupLoadState.Unavailable.INSTANCE, state.get().v0State());
    }

    @Test
    void tableShapeAndExplicitBooleanAreValidatedBeforeNumericKeys() {
        var raw = CommentedConfig.inMemory();
        for (var value : List.of(1, false, "section", List.of("not a table"))) {
            raw.set("v0", value);
            assertFailure(raw, V0ConfigFailureCode.WRONG_SECTION_TYPE, "v0");
        }
        raw.clear();
        raw.set("v0.maxLineagesPerServer", "also invalid");
        assertFailure(raw, V0ConfigFailureCode.MISSING_REQUIRED_VALUE, "v0.enabled");
        for (var enabled : List.of(1, "true")) {
            raw.set("v0.enabled", enabled);
            assertFailure(raw, V0ConfigFailureCode.WRONG_VALUE_TYPE, "v0.enabled");
        }
    }

    @Test
    void realTomlAndExactMinimumDefaultHardTuplesDecodeWithoutChangingOtherSections() {
        var toml = new TomlParser().parse(new StringReader("""
                [another]
                enabled = "unrelated"

                [v0]
                enabled = true
                maxLineagesPerAttribution = 1
                maxLineagesPerServer = 2
                rootAttemptsPerTick = 1
                maxManifestEntries = 4
                projectileMaxAgeTicks = 16
                projectileMaxRangeBlocks = 16
                lineageDeadlineTicks = 24
                presentationRecipientsPerOffer = 8
                positiveRootFee = 1
                """));
        assertEquals(new V0StartupLimits(1, 2, 1, 4, 16, 16, 24, 8, 1L), ready(toml));
        assertEquals("unrelated", toml.<String>get("another.enabled"));

        var raw = CommentedConfig.inMemory();
        setProfile(raw, true, MINIMUM);
        assertEquals(new V0StartupLimits(1, 1, 1, 1, 1, 1, 3, 1, 1L), ready(raw));
        setProfile(raw, true, HARD);
        assertEquals(new V0StartupLimits(2, 4, 2, 8, 24, 24, 40, 16, 4L), ready(raw));
        raw.set("v0.maxManifestEntries", 8);
        assertEquals(8, ready(raw).maxManifestEntries());
    }

    @Test
    void eachNumericKeyChecksMissingTypeAndBoundsBeforeCrossFieldChecks() {
        var raw = CommentedConfig.inMemory();
        for (int index = 0; index < NUMERIC_KEYS.size(); index++) {
            var key = path(NUMERIC_KEYS.get(index));
            setDefaultProfile(raw, true);
            raw.remove(key);
            assertFailure(raw, V0ConfigFailureCode.MISSING_REQUIRED_VALUE, key);
            for (var value : List.of(true, "1", 1.0, List.of(1))) {
                raw.set(key, value);
                assertFailure(raw, V0ConfigFailureCode.WRONG_VALUE_TYPE, key);
            }
            for (var value : List.of(0L, -1L, MINIMUM.get(index) - 1)) {
                raw.set(key, value);
                assertFailure(raw, V0ConfigFailureCode.BELOW_MINIMUM, key);
            }
            for (var value : List.of(HARD.get(index) + 1, Long.MAX_VALUE)) {
                raw.set(key, value);
                assertFailure(raw, V0ConfigFailureCode.ABOVE_HARD_MAXIMUM, key);
            }
        }

        raw.clear();
        raw.set("v0.enabled", false);
        raw.set("v0.positiveRootFee", true);
        assertFailure(raw, V0ConfigFailureCode.WRONG_VALUE_TYPE, "v0.positiveRootFee");
    }

    @Test
    void firstFailureOrderingAndRemovedKnobsStayClosed() {
        var raw = CommentedConfig.inMemory();
        setDefaultProfile(raw, true);
        raw.set("v0.maxLineagesPerAttribution", 2);
        raw.set("v0.maxLineagesPerServer", 1);
        raw.set("v0.rootAttemptsPerTick", 2);
        raw.set("v0.lineageDeadlineTicks", 3);
        raw.set("v0.projectileMaxRangeBlocks", 1);
        raw.set("v0.programBytesPerLineage", 1089);
        raw.set("v0.maxClosureSpells", 2);
        raw.remove("v0.maxManifestEntries");
        assertFailure(raw, V0ConfigFailureCode.MISSING_REQUIRED_VALUE, "v0.maxManifestEntries");

        raw.set("v0.maxManifestEntries", 4);
        assertFailure(raw, V0ConfigFailureCode.UNKNOWN_KEY, "v0.maxClosureSpells");
        raw.remove("v0.maxClosureSpells");
        assertFailure(raw, V0ConfigFailureCode.UNKNOWN_KEY, "v0.programBytesPerLineage");
        raw.remove("v0.programBytesPerLineage");

        assertRelationship(raw, "maxLineagesPerAttribution", "maxLineagesPerServer");
        raw.set("v0.maxLineagesPerAttribution", 1);
        assertRelationship(raw, "rootAttemptsPerTick", "maxLineagesPerServer");
        raw.set("v0.rootAttemptsPerTick", 1);
        assertRelationship(raw, "lineageDeadlineTicks", "projectileMaxAgeTicks");
        raw.set("v0.lineageDeadlineTicks", 18);
        assertRelationship(raw, "projectileMaxRangeBlocks", "projectileMaxAgeTicks");
        raw.set("v0.projectileMaxRangeBlocks", 16);
        assertEquals(18, ready(raw).lineageDeadlineTicks());

        raw.clear();
        raw.set("v0.enabled", false);
        raw.set("v0.projectileMaxAgeTicks", 3);
        raw.set("v0.projectileMaxRangeBlocks", 2);
        assertRelationship(raw, "projectileMaxRangeBlocks", "projectileMaxAgeTicks");
    }

    @Test
    void startupVariantsMatchDeclaredFlagsAndKeepStartupLimitsPackageLocal() {
        assertFalse(Modifier.isPublic(V0StartupLoadState.class.getModifiers()));
        assertTrue(V0StartupLoadState.class.isSealed());
        assertEquals(4, V0StartupLoadState.class.getPermittedSubclasses().length);
        for (var member : V0StartupLoadState.class.getPermittedSubclasses()) {
            assertTrue(Modifier.isPublic(member.getModifiers()));
            assertTrue(Modifier.isStatic(member.getModifiers()));
            assertSame(V0StartupLoadState.class, member.getDeclaringClass());
            if (member.isRecord()) {
                assertEquals(1, member.getDeclaredConstructors().length);
                assertTrue(Modifier.isPublic(member.getDeclaredConstructors()[0].getModifiers()));
            }
        }
        assertFalse(Modifier.isPublic(V0StartupLimits.class.getModifiers()));
        assertEquals(NUMERIC_KEYS, Arrays.stream(V0StartupLimits.class.getRecordComponents())
                .map(component -> component.getName()).toList());
    }

    static void setDefaultProfile(CommentedConfig raw, boolean enabled) {
        setProfile(raw, enabled, DEFAULT);
    }

    private static void setProfile(CommentedConfig raw, boolean enabled, List<Long> values) {
        raw.set("v0.enabled", enabled);
        for (int index = 0; index < NUMERIC_KEYS.size(); index++) {
            raw.set(path(NUMERIC_KEYS.get(index)), values.get(index));
        }
    }

    private static V0StartupLimits ready(CommentedConfig raw) {
        return assertInstanceOf(V0StartupLoadState.Ready.class, read(raw)).limits();
    }

    private static V0StartupLoadState read(CommentedConfig raw) {
        var state = new AtomicReference<>(P11ServerConfigCandidate.unavailable());
        new P5RawServerConfigSpec(state).acceptRawConfig(raw);
        return state.get().v0State();
    }

    private static void assertFailure(CommentedConfig raw, V0ConfigFailureCode reason, String key) {
        assertEquals(new V0ConfigFailure(reason, key, Optional.empty()), failure(raw));
    }

    private static void assertRelationship(CommentedConfig raw, String key, String other) {
        assertEquals(new V0ConfigFailure(V0ConfigFailureCode.INCONSISTENT_LIMITS,
                path(key), Optional.of(path(other))), failure(raw));
    }

    private static V0ConfigFailure failure(CommentedConfig raw) {
        return assertInstanceOf(V0StartupLoadState.Invalid.class, read(raw)).failure();
    }

    private static String path(String key) {
        return "v0." + key;
    }
}
