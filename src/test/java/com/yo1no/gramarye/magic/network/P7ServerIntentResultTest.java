package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class P7ServerIntentResultTest {
    @Test
    void resultIsPackagePrivateFinalAndRetainsOnlyBoundedValueTypes() {
        var type = P7ServerIntentResult.class;
        var fieldTypes = Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getGenericType().getTypeName())
                .toList();

        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertFalse(Modifier.isPublic(type.getModifiers()));
        assertTrue(Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())));
        for (var forbidden : List.of(
                "ServerPlayer",
                "MinecraftServer",
                "Entity",
                "Level",
                "SkillReference",
                "SkillDefinition",
                "RuntimeEvent",
                "RuntimeAdmissionResult",
                "Throwable",
                "ByteBuf",
                "IPayloadContext")) {
            assertTrue(fieldTypes.stream().noneMatch(name -> name.contains(forbidden)), forbidden);
        }
    }

    @Test
    void acceptedAdmissionRequiresAttemptAndMatchingAckSequence() {
        var identity = new P7SessionIdentity(
                UUID.fromString("00000000-0000-0000-0000-000000000704"), 4L);
        var acknowledgement = new IntentAcknowledgement(
                5L,
                IntentAcknowledgement.Disposition.ACCEPTED,
                IntentAcknowledgement.SEQUENCE_CONSUMED,
                null);

        assertThrows(
                P7SemanticInvariantException.class,
                () -> new P7ServerIntentResult(
                        identity,
                        5L,
                        Optional.empty(),
                        Optional.of(acknowledgement),
                        true,
                        false,
                        false,
                        true,
                        true));
        assertThrows(
                P7SemanticInvariantException.class,
                () -> new P7ServerIntentResult(
                        identity,
                        6L,
                        Optional.empty(),
                        Optional.of(acknowledgement),
                        true,
                        false,
                        true,
                        true,
                        true));
    }

    @Test
    void internalFaultHasNoAcknowledgementCandidate() {
        var identity = new P7SessionIdentity(
                UUID.fromString("00000000-0000-0000-0000-000000000705"), 5L);
        var result = P7AdmissionDispositionMapper.fromRootDisposition(
                identity,
                9L,
                P7ServerAuthorizationBoundary.AdmissionDisposition.INTERNAL_SERVER_FAULT);

        assertEquals(
                P7IntentFailureReason.INTERNAL_SERVER_FAULT,
                result.failureReason().orElseThrow());
        assertTrue(result.acknowledgementCandidate().isEmpty());
        assertTrue(result.sequenceConsumed());
    }
}
