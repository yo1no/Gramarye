package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SkillDocumentReadFailureTest {
    @Test
    void malformedReaderInputMapsToMachineReadableFailureWithoutDiagnosticData() {
        var secret = "reader-boundary-secret";
        var malformed = new JsonObject();
        malformed.addProperty("raw_secret", secret);
        var readResult = SkillDocumentReader.read(new Dynamic<>(JsonOps.INSTANCE, malformed));

        assertTrue(readResult.error().isPresent());
        var failure = SkillDocumentReadFailure.fromReadResult(readResult).orElseThrow();

        assertEquals(SkillDocumentReadFailureCode.READER_REJECTED_INPUT, failure.code());
        assertFalse(failure.toString().contains(secret));
        assertFalse(failure.toString().contains(readResult.error().orElseThrow().message()));
    }

    @Test
    void successfulReaderResultDoesNotCreateFailure() {
        var valid = new Dynamic<>(
                JsonOps.INSTANCE,
                DocumentTestFixtures.documentJson(new JsonObject()));

        assertTrue(SkillDocumentReadFailure.fromReadResult(
                        SkillDocumentReader.read(valid))
                .isEmpty());
    }

    @Test
    void contractContainsOnlyItsImmutableFailureCode() {
        assertTrue(SkillDocumentReadFailure.class.isRecord());
        var components = SkillDocumentReadFailure.class.getRecordComponents();
        assertEquals(1, components.length);
        assertEquals("code", components[0].getName());
        assertEquals(SkillDocumentReadFailureCode.class, components[0].getType());

        var instanceFields = Arrays.stream(SkillDocumentReadFailure.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        assertEquals(1, instanceFields.size());
        assertEquals("code", instanceFields.getFirst().getName());
        assertTrue(Modifier.isFinal(instanceFields.getFirst().getModifiers()));
    }

    @Test
    void nullCodeAndNullReaderResultAreRejectedAsProgrammerMisuse() {
        assertThrows(NullPointerException.class, () -> new SkillDocumentReadFailure(null));
        assertThrows(
                NullPointerException.class,
                () -> SkillDocumentReadFailure.fromReadResult(null));
    }
}
