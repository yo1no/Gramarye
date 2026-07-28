package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Phase-local API gate for the isolated P4-A3-B probe surface. */
class P4A3BApiGateTest {
    @Test
    void carrierProductionShapeIsUnchanged() {
        var fieldNames = Arrays.stream(EncodedSkillStoreCarrier.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(field -> field.getName())
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(Set.of(
                                "storeBlob",
                                "histories",
                                "historyCount",
                                "revisionCount",
                                "totalHistoryBlobBytes",
                                "totalRevisionBlobBytes"),
                        fieldNames),
                () -> assertFalse(Modifier.isPublic(
                        EncodedSkillStoreCarrier.class.getModifiers())),
                () -> assertEquals(
                        Set.of("toString"),
                        Arrays.stream(EncodedSkillStoreCarrier.class.getDeclaredMethods())
                                .filter(method -> Modifier.isPublic(method.getModifiers()))
                                .map(method -> method.getName())
                                .collect(Collectors.toSet())));
    }

    @Test
    void probeSupportIsAvailableToLightweightTestsAndHolderIsAbsent() {
        assertAll(
                () -> assertEquals(
                        "P4A3HeapProbeMain",
                        P4A3HeapProbeMain.class.getSimpleName()),
                () -> assertEquals(
                        "P4B2ProbeMain",
                        P4B2ProbeMain.class.getSimpleName()),
                () -> assertEquals(
                        "P4C2ProbeMain",
                        Class.forName(
                                "com.yo1no.gramarye.magic.definition.player.P4C2ProbeMain")
                                .getSimpleName()),
                () -> assertThrows(ClassNotFoundException.class, () -> Class.forName(
                        "com.yo1no.gramarye.magic.definition.store.P4A3CarrierGameTests")),
                () -> assertThrows(ClassNotFoundException.class, () -> Class.forName(
                        "com.yo1no.gramarye.magic.definition.store.P4B2MemoryGameTests")),
                () -> assertThrows(ClassNotFoundException.class, () -> Class.forName(
                        "com.yo1no.gramarye.magic.definition.player.P4C2MemoryGameTests")));
    }

    @Test
    void fixtureBuildersNeverManufactureCarrierObjects() {
        assertAll(
                () -> assertTrue(Arrays.stream(P4A3ProbeWorkloads.class.getDeclaredMethods())
                        .noneMatch(method -> method.getReturnType()
                                == EncodedSkillStoreCarrier.class)),
                () -> assertTrue(Arrays.stream(P4A3ProbeWorkload.class.getDeclaredFields())
                        .noneMatch(field -> field.getType()
                                == EncodedSkillStoreCarrier.class)));
    }

    @Test
    void probeMainExposesOnlyForkedAndDedicatedEntrypoints() {
        var publicMethods = Arrays.stream(P4A3HeapProbeMain.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertEquals(Set.of("main", "runDedicated"), publicMethods);
    }
}
