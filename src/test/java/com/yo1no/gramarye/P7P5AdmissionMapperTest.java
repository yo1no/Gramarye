package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.yo1no.gramarye.magic.api.id.EventId;
import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class P7P5AdmissionMapperTest {
    @Test
    void allTwentyOneAdmissionVariantsHaveTheExactOneSixteenThreeOnePartition() {
        var serverToken = new RuntimeServerToken(1);
        var skillInstanceId = new SkillInstanceId(new UUID(1, 1));
        RuntimeAdmissionResult accepted = new RuntimeAdmissionResult.AcceptedMemoryOnly(
                new RuntimeEventToken(serverToken, skillInstanceId, new EventId(1)),
                new RuntimeCancellationToken(serverToken, skillInstanceId));
        List<RuntimeAdmissionResult> ordinaryRejections = List.of(
                new RuntimeAdmissionResult.PersistentScheduleUnsupported(),
                new RuntimeAdmissionResult.DelayOutOfRange(-1, 12_000),
                new RuntimeAdmissionResult.DelayOverflow(),
                new RuntimeAdmissionResult.DeadlineOutOfRange(-1, 12_000),
                new RuntimeAdmissionResult.DeadlineOverflow(),
                new RuntimeAdmissionResult.DeadlineBeforeScheduledTick(2, 1),
                new RuntimeAdmissionResult.InvalidRuntimeReference(
                        RuntimeReferenceFailureReason.WRONG_SERVER),
                new RuntimeAdmissionResult.SkillRevisionUnavailable(
                        new SkillRevisionUnavailableReason.ExactRevisionMissing()),
                new RuntimeAdmissionResult.InvalidEvent(
                        InvalidEventReason.INVALID_REFERENCE_SHAPE),
                new RuntimeAdmissionResult.OwnerInstanceUnavailable(),
                new RuntimeAdmissionResult.ActiveLineageCapacityExceeded(128, 128),
                new RuntimeAdmissionResult.ActiveBudgetAttributionCapacityExceeded(32, 32),
                new RuntimeAdmissionResult.RootAdmissionBudgetExceeded(64),
                new RuntimeAdmissionResult.CircuitBroken(new RuntimeCircuitBreakerSummary(
                        RuntimeCircuitBreakReason.PLAYER_PENDING_EVENTS_EXCEEDED,
                        1_024,
                        1,
                        1_024,
                        0,
                        false)),
                new RuntimeAdmissionResult.SequenceExhausted(
                        RuntimeSequenceKind.EVENT_SEQUENCE),
                new RuntimeAdmissionResult.TickExhausted());
        List<RuntimeAdmissionResult> unavailable = List.of(
                new RuntimeAdmissionResult.ServerNotRunning(),
                new RuntimeAdmissionResult.ServerStopping(),
                new RuntimeAdmissionResult.KernelFaulted());
        RuntimeAdmissionResult wrongThread = new RuntimeAdmissionResult.WrongThread();

        var allVariants = new ArrayList<RuntimeAdmissionResult>();
        allVariants.add(accepted);
        allVariants.addAll(ordinaryRejections);
        allVariants.addAll(unavailable);
        allVariants.add(wrongThread);

        assertEquals(16, ordinaryRejections.size());
        assertEquals(3, unavailable.size());
        assertEquals(21, allVariants.size());
        assertEquals(21, RuntimeAdmissionResult.class.getPermittedSubclasses().length);
        assertEquals(
                Arrays.stream(RuntimeAdmissionResult.class.getPermittedSubclasses())
                        .collect(Collectors.toSet()),
                allVariants.stream().map(Object::getClass).collect(Collectors.toSet()));

        assertSame(
                P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED,
                P7AuthenticatedPlayerCastIngress.mapAdmission(accepted));
        ordinaryRejections.forEach(result -> assertSame(
                P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED,
                P7AuthenticatedPlayerCastIngress.mapAdmission(result),
                result.getClass().getSimpleName()));
        unavailable.forEach(result -> assertSame(
                P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE,
                P7AuthenticatedPlayerCastIngress.mapAdmission(result),
                result.getClass().getSimpleName()));
        assertSame(
                P7ServerAuthorizationBoundary.AdmissionDisposition.INTERNAL_SERVER_FAULT,
                P7AuthenticatedPlayerCastIngress.mapAdmission(wrongThread));

        assertEquals(
                Map.of(
                        P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED,
                        1L,
                        P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED,
                        16L,
                        P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE,
                        3L,
                        P7ServerAuthorizationBoundary.AdmissionDisposition.INTERNAL_SERVER_FAULT,
                        1L),
                allVariants.stream()
                        .map(P7AuthenticatedPlayerCastIngress::mapAdmission)
                        .collect(Collectors.groupingBy(
                                Function.identity(), Collectors.counting())));
    }
}
