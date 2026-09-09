package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Focused readiness, failure-policy, ordering, and exact-charge gate for P8-S4. */
final class P8S4ServerTransportTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path ROOT_PACKAGE = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye");
    private static final Path SERVICE_SOURCE = ROOT_PACKAGE.resolve(
            "P8ServerPresentationService.java");
    private static final Path PACKET_SUBMISSION_SOURCE = ROOT_PACKAGE.resolve(
            "P8PacketSubmission.java");
    private static final Path PLATFORM_GAMETEST_SOURCE = ROOT_PACKAGE.resolve(
            "P8S3PresentationGameTests.java");

    @Test
    void sameGenerationScheduleCannotRenewReadyOrTerminalAttemptState() {
        var authority = new P8ServerConnectionAuthority();
        var playerId = new UUID(0L, 0x8401L);
        var identity = authority.open(playerId, 4L, 10L).orElseThrow();
        var successfulKey = authority.due(10L).getFirst();

        assertTrue(authority.beginAttempt(successfulKey, 10L));
        authority.submitted(successfulKey);
        assertAll(
                () -> assertEquals(1, authority.attempts(playerId)),
                () -> assertEquals(identity, authority.captureReady(playerId, 4L).orElseThrow()),
                () -> assertFalse(authority.schedule(playerId, 4L, 100L)),
                () -> assertFalse(authority.schedule(playerId, 3L, 100L)),
                () -> assertEquals(1, authority.attempts(playerId)),
                () -> assertTrue(authority.captureReady(playerId, 4L).isPresent()),
                () -> assertTrue(authority.due(100L).isEmpty()));

        assertTrue(authority.schedule(playerId, 5L, 20L));
        var terminalKey = authority.due(20L).getFirst();
        assertAll(
                () -> assertEquals(0, authority.attempts(playerId)),
                () -> assertTrue(authority.captureReady(playerId, 4L).isEmpty()),
                () -> assertFalse(authority.isCurrent(successfulKey)));

        assertTrue(authority.beginAttempt(terminalKey, 20L));
        authority.failed(terminalKey, 20L);
        assertTrue(authority.beginAttempt(authority.due(21L).getFirst(), 21L));
        authority.failed(terminalKey, 21L);

        authority.scheduleAll(5L, 200L);
        assertAll(
                () -> assertEquals(
                        PresentationLimits.MAX_CATALOG_SUBMISSION_ATTEMPTS,
                        authority.attempts(playerId)),
                () -> assertFalse(authority.schedule(playerId, 5L, 200L)),
                () -> assertFalse(authority.schedule(playerId, 4L, 200L)),
                () -> assertTrue(authority.due(200L).isEmpty()),
                () -> assertTrue(authority.captureReady(playerId, 5L).isEmpty()));
    }

    @Test
    void dueSelectionIsUnsignedOrderedBoundedAndDeferralConsumesNoAttempt() {
        var authority = new P8ServerConnectionAuthority();
        var first = new UUID(0L, 1L);
        var second = new UUID(0L, 2L);
        var unsignedLast = new UUID(-1L, 0L);
        authority.open(unsignedLast, 1L, 30L).orElseThrow();
        authority.open(second, 1L, 30L).orElseThrow();
        authority.open(first, 1L, 30L).orElseThrow();

        var due = authority.due(30L);
        assertAll(
                () -> assertEquals(3, due.size()),
                () -> assertEquals(List.of(first, second, unsignedLast),
                        due.stream().map(P8CatalogAttemptKey::playerId).toList()),
                () -> assertEquals(0, authority.attempts(first)),
                () -> assertEquals(0, authority.attempts(second)),
                () -> assertEquals(0, authority.attempts(unsignedLast)));

        // Only an actual begin consumes an attempt. The bounded queue scan exposes later keys;
        // the service, rather than the queue, owns the exact two-submission tick budget.
        assertTrue(authority.beginAttempt(due.getFirst(), 30L));
        authority.submitted(due.getFirst());
        assertEquals(
                List.of(second, unsignedLast),
                authority.due(31L).stream().map(P8CatalogAttemptKey::playerId).toList());
        assertAll(
                () -> assertEquals(1, authority.attempts(first)),
                () -> assertEquals(0, authority.attempts(second)),
                () -> assertEquals(0, authority.attempts(unsignedLast)));
    }

    @Test
    void staleAttemptCannotMutateAReconnectedEpoch() {
        var authority = new P8ServerConnectionAuthority();
        var playerId = new UUID(0L, 0x8402L);
        var staleIdentity = authority.open(playerId, 7L, 40L).orElseThrow();
        var staleKey = authority.due(40L).getFirst();
        var currentIdentity = authority.open(playerId, 7L, 41L).orElseThrow();
        var currentKey = authority.due(41L).getFirst();

        assertAll(
                () -> assertNotEquals(staleIdentity, currentIdentity),
                () -> assertTrue(
                        currentIdentity.connectionEpoch() > staleIdentity.connectionEpoch()),
                () -> assertFalse(authority.isCurrent(staleKey)),
                () -> assertFalse(authority.beginAttempt(staleKey, 41L)));

        authority.submitted(staleKey);
        authority.failed(staleKey, 41L);
        authority.cancel(staleKey);
        assertAll(
                () -> assertEquals(0, authority.attempts(playerId)),
                () -> assertTrue(authority.captureReady(playerId, 7L).isEmpty()),
                () -> assertEquals(List.of(currentKey), authority.due(41L)),
                () -> assertFalse(authority.isCurrent(staleIdentity, 7L)));

        assertTrue(authority.beginAttempt(currentKey, 41L));
        authority.submitted(currentKey);
        assertAll(
                () -> assertEquals(currentIdentity,
                        authority.captureReady(playerId, 7L).orElseThrow()),
                () -> assertTrue(authority.isCurrent(currentIdentity, 7L)),
                () -> assertFalse(authority.isCurrent(staleIdentity, 7L)));
    }

    @Test
    void catalogOutcomeTransitionsAreReadyRetryOrTerminalWithoutRenewal() {
        var normal = new P8ServerConnectionAuthority();
        var runtimeFailure = new P8ServerConnectionAuthority();
        var errorFailure = new P8ServerConnectionAuthority();
        var normalId = new UUID(0L, 0x8410L);
        var runtimeId = new UUID(0L, 0x8411L);
        var errorId = new UUID(0L, 0x8412L);

        var normalKey = normalKey(normal, normalId, 50L);
        normal.submitted(normalKey);

        var runtimeKey = normalKey(runtimeFailure, runtimeId, 50L);
        runtimeFailure.failed(runtimeKey, 50L);

        var errorKey = normalKey(errorFailure, errorId, 50L);
        errorFailure.cancel(errorKey);

        assertAll(
                () -> assertTrue(normal.ready(normalId, 1L)),
                () -> assertTrue(normal.due(51L).isEmpty()),
                () -> assertFalse(runtimeFailure.ready(runtimeId, 1L)),
                () -> assertEquals(List.of(runtimeKey), runtimeFailure.due(51L)),
                () -> assertEquals(1, runtimeFailure.attempts(runtimeId)),
                () -> assertFalse(errorFailure.ready(errorId, 1L)),
                () -> assertTrue(errorFailure.due(51L).isEmpty()),
                () -> assertEquals(1, errorFailure.attempts(errorId)),
                () -> assertFalse(errorFailure.schedule(errorId, 1L, 52L)),
                        () -> assertTrue(errorFailure.due(52L).isEmpty()));
    }

    @Test
    void serverDrainBindsCatalogAndEventFailuresToTheirExactPolicies() {
        var service = read(SERVICE_SOURCE);
        var catalogDrain = between(
                service,
                "    private CatalogDrainResultForTesting drainCatalogs(",
                "    private void completeCatalogSubmission(");
        var eventDrain = between(
                service,
                "    private CatalogDrainResultForTesting drainPresentation(",
                "    private CatalogDrainResultForTesting drainCatalogs(");
        var catalogPolicy = between(
                service,
                "    private void completeCatalogSubmission(",
                "    private void submitEvent(");
        var eventPolicy = between(
                service,
                "    private void submitEvent(",
                "    static final class CatalogDrainResultForTesting");

        var catalogMeasure = catalogDrain.indexOf("catalogTransport.packetCharge(player, payload)");
        var submissionBudget = catalogDrain.indexOf("MAX_CATALOG_SUBMISSIONS_PER_SERVER_TICK");
        var byteBudget = catalogDrain.indexOf("MAX_CATALOG_SUBMISSION_BYTES_PER_SERVER_TICK");
        var beginAttempt = catalogDrain.indexOf("connectionAuthority.beginAttempt(key, serverTick)");
        var unavailable = catalogDrain.indexOf("if (!catalogTransport.canSubmit(player, payload))");
        var unavailableCancel = catalogDrain.indexOf(
                "connectionAuthority.cancel(key)", unavailable);
        var catalogSubmit = catalogPolicy.indexOf("catalogTransport.submit(player, payload)");
        var runtimeCatch = catalogPolicy.indexOf("catch (RuntimeException ignored)");
        var retry = catalogPolicy.indexOf("connectionAuthority.failed(key, serverTick)");
        var errorCatch = catalogPolicy.indexOf("catch (Error failure)");
        var cancel = catalogPolicy.indexOf("connectionAuthority.cancel(key)", errorCatch);
        var sameObjectRethrow = catalogPolicy.indexOf("throw failure", cancel);
        var submitted = catalogPolicy.indexOf("connectionAuthority.submitted(key)");

        var bufferExpiry = eventDrain.indexOf("tickState = P8TickState.empty()");
        var catalogFirst = eventDrain.indexOf("drainCatalogs(server, serverTick)");
        var measured = eventDrain.indexOf("transport.packetCharge(");
        var admission = eventDrain.indexOf("P8DeliveryAdmission.admit(eligible)");
        var finalOrder = eventDrain.indexOf("admitted.sort(");
        var eventLoop = eventDrain.indexOf("for (P8Delivery delivery : admitted)");
        var eventSubmit = eventDrain.indexOf("submitEvent(", eventLoop);
        var policySubmit = eventPolicy.indexOf("transport.submit(identity, event)");
        var eventRuntimeCatch = eventPolicy.indexOf("catch (RuntimeException ignored)");
        var eventDiagnostic = eventPolicy.indexOf(
                "P8ServerRuntimeDiagnosticCode.EVENT_TRANSPORT_RUNTIME_EXCEPTION",
                eventRuntimeCatch);
        var nullInvariant = eventPolicy.indexOf(
                "Objects.requireNonNull(result, \"P8 transport submission result\")");
        var eventSubmissionRegion = eventDrain.substring(eventLoop);

        assertAll(
                () -> assertTrue(submissionBudget >= 0
                        && unavailable >= 0
                        && submissionBudget < unavailable
                        && unavailable < unavailableCancel
                        && unavailableCancel < catalogMeasure
                        && catalogMeasure < byteBudget
                        && byteBudget < beginAttempt),
                () -> assertTrue(catalogSubmit >= 0
                        && catalogSubmit < runtimeCatch
                        && runtimeCatch < retry
                        && retry < errorCatch
                        && errorCatch < cancel
                        && cancel < sameObjectRethrow),
                () -> assertTrue(sameObjectRethrow < submitted),
                () -> assertTrue(catalogDrain.substring(byteBudget, beginAttempt)
                        .contains("continue;")),
                () -> assertTrue(catalogDrain.substring(unavailable, catalogMeasure)
                        .contains("continue;")),
                () -> assertEquals(1, occurrences(
                        catalogDrain.substring(unavailable, catalogMeasure),
                        "connectionAuthority.cancel(")),
                () -> assertEquals(0, occurrences(catalogDrain, "catalogTransport.submit(")),
                () -> assertEquals(1, occurrences(catalogPolicy, "catalogTransport.submit(")),
                () -> assertEquals(1, occurrences(catalogPolicy, "catch (Error failure)")),
                () -> assertEquals(1, occurrences(catalogPolicy, "throw failure")),
                () -> assertTrue(bufferExpiry >= 0
                        && bufferExpiry < catalogFirst
                        && catalogFirst < measured
                        && measured < admission
                        && admission < finalOrder
                        && finalOrder < eventLoop
                        && eventLoop < eventSubmit),
                () -> assertTrue(policySubmit >= 0
                        && policySubmit < eventRuntimeCatch
                        && eventRuntimeCatch < eventDiagnostic
                        && eventDiagnostic < nullInvariant),
                () -> assertTrue(eventDrain.substring(finalOrder, eventLoop)
                        .contains("compareUnsignedUuid(")),
                () -> assertTrue(eventDrain.substring(finalOrder, eventLoop)
                        .contains("buffered().event().sequence()")),
                () -> assertEquals(1, occurrences(eventSubmissionRegion, "submitEvent(")),
                () -> assertEquals(0, occurrences(eventSubmissionRegion, "connectionAuthority")),
                () -> assertEquals(0, occurrences(eventSubmissionRegion, "catch (Error")),
                () -> assertEquals(0, occurrences(eventSubmissionRegion, "retry")),
                () -> assertEquals(0, occurrences(eventSubmissionRegion, "replay")));
    }

    @Test
    void fanoutUsesOneMeasuredChargePerRecipientAndDeterministicPriorityOrder() {
        var target = new P8SelectedRecipient(
                new P8RecipientIdentity(new UUID(0L, 3L), 1L),
                PresentationOrdering.RecipientCategory.TARGET_SELF,
                100.0D);
        var source = new P8SelectedRecipient(
                new P8RecipientIdentity(new UUID(0L, 2L), 1L),
                PresentationOrdering.RecipientCategory.SOURCE,
                4.0D);
        var ordinary = new P8SelectedRecipient(
                new P8RecipientIdentity(new UUID(0L, 1L), 1L),
                PresentationOrdering.RecipientCategory.ORDINARY,
                1.0D);
        var buffered = buffered(1L, List.of(ordinary, source, target));
        var candidates = List.of(
                delivery(buffered, ordinary, 703),
                delivery(buffered, source, 702),
                delivery(buffered, target, 701));

        var admitted = P8DeliveryAdmission.admit(candidates);

        assertAll(
                () -> assertEquals(
                        List.of(target.identity(), source.identity(), ordinary.identity()),
                        admitted.stream().map(P8Delivery::identity).toList()),
                () -> assertEquals(List.of(701, 702, 703),
                        admitted.stream().map(P8Delivery::packetCharge).toList()),
                () -> assertTrue(admitted.stream()
                        .allMatch(delivery -> delivery.buffered() == buffered)),
                () -> assertEquals(3, admitted.size()));
    }

    @Test
    void admissionConsumesTheMeasuredPacketChargeRatherThanSemanticBodyEstimate() {
        int measuredCharge = PresentationLimits.MAX_EVENT_PACKET_CHARGE_BYTES;
        int candidateCount = Math.toIntExact(
                PresentationLimits.MAX_DELIVERIES_PER_SERVER_PER_TICK);
        var candidates = new ArrayList<P8Delivery>(candidateCount);
        for (var index = candidateCount; index >= 1; index--) {
            var recipient = new P8SelectedRecipient(
                    new P8RecipientIdentity(new UUID(0L, index), 1L),
                    PresentationOrdering.RecipientCategory.ORDINARY,
                    0.0D);
            var buffered = buffered(index, List.of(recipient));
            candidates.add(delivery(buffered, recipient, measuredCharge));
        }

        var admitted = P8DeliveryAdmission.admit(candidates);
        int expectedByMeasuredBytes = Math.toIntExact(
                PresentationLimits.MAX_DELIVERY_BYTES_PER_SERVER_PER_TICK
                        / measuredCharge);

        assertAll(
                () -> assertNotEquals(
                        measuredCharge,
                        candidates.getFirst().buffered().event().packetCharge()),
                () -> assertTrue(expectedByMeasuredBytes < candidateCount),
                () -> assertEquals(expectedByMeasuredBytes, admitted.size()),
                () -> assertEquals(new UUID(0L, 1L), admitted.getFirst().identity().playerId()),
                () -> assertEquals(
                        new UUID(0L, expectedByMeasuredBytes),
                        admitted.getLast().identity().playerId()),
                () -> assertTrue(admitted.stream()
                        .allMatch(delivery -> delivery.packetCharge() == measuredCharge)));
    }

    @Test
    void productionMeasurementUsesTheLivePlayEncoderAndHasAnActualPlatformProbe() {
        var packetSubmission = read(PACKET_SUBMISSION_SOURCE);
        var service = read(SERVICE_SOURCE);
        var gameTest = read(PLATFORM_GAMETEST_SOURCE);
        var productionTransport = between(
                service,
                "    private static final class ProductionPresentationTransport",
                "    static final class ReloadIdentity");

        assertAll(
                () -> assertTrue(packetSubmission.contains(".pipeline()")
                        && packetSubmission.contains(".get(\"encoder\")")
                        && packetSubmission.contains("instanceof PacketEncoder<?> encoder")),
                () -> assertTrue(packetSubmission.contains("ConnectionProtocol.PLAY")),
                () -> assertTrue(packetSubmission.contains("PacketFlow.CLIENTBOUND")),
                () -> assertTrue(packetSubmission.contains(
                        "NetworkRegistry.getConnectionType(")),
                () -> assertTrue(packetSubmission.contains(
                        "GameProtocols.CLIENTBOUND_TEMPLATE.bind(")),
                () -> assertTrue(packetSubmission.contains(
                        "new ClientboundCustomPayloadPacket(payload)")),
                () -> assertTrue(packetSubmission.contains("protocol.codec().encode(")
                        && packetSubmission.contains(
                                "new ClientboundCustomPayloadPacket(payload)")),
                () -> assertTrue(packetSubmission.contains("encoded.readableBytes()")),
                () -> assertTrue(packetSubmission.contains("encoded.release()")),
                () -> assertEquals(1, occurrences(
                        productionTransport,
                        "P8PacketSubmission.measureClientboundPlayPacket(")),
                () -> assertTrue(productionTransport.contains(
                        "event.bodySize(), PresentationLimits.EVENT_PACKET_OVERHEAD_BYTES")),
                () -> assertTrue(productionTransport.contains("if (measured != expected")),
                () -> assertTrue(gameTest.contains(
                        "productionTransportEncodesCatalogBeforePresentationEvent")),
                () -> assertTrue(gameTest.contains(
                        "connection.setupOutboundProtocol(playProtocol)")),
                () -> assertTrue(gameTest.contains(
                        "catalog.packetBytes()\n                                    == payload.bodySize()")),
                () -> assertTrue(gameTest.contains("encodedEvent.packetBytes()")
                        && gameTest.contains(".EVENT_PACKET_OVERHEAD_BYTES")));
    }

    private static P8CatalogAttemptKey normalKey(
            P8ServerConnectionAuthority authority, UUID playerId, long tick) {
        authority.open(playerId, 1L, tick).orElseThrow();
        var key = authority.due(tick).getFirst();
        assertTrue(authority.beginAttempt(key, tick));
        return key;
    }

    private static P8Delivery delivery(
            P8BufferedPresentation buffered,
            P8SelectedRecipient recipient,
            int measuredCharge) {
        return new P8Delivery(
                new PresentationOrdering.DeliveryCandidate(
                        recipient.category(),
                        recipient.squaredDistance(),
                        recipient.identity().playerId(),
                        buffered.event().sequence()),
                recipient.identity(),
                buffered,
                measuredCharge);
    }

    private static P8BufferedPresentation buffered(
            long sequence, List<P8SelectedRecipient> recipients) {
        var event = ((AcceptedPresentationEvent) PresentationEvent.createServer(
                        1L,
                        PresentationEventKind.CAST_RELEASE.wireCode(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        PresentationTestFixtures.id("dimension"),
                        0.0D,
                        64.0D,
                        0.0D,
                        1.0D,
                        0.0D,
                        0.0D,
                        disabledAppearance(),
                        sequence))
                .event();
        var direction = event.direction();
        var appearance = event.appearance();
        var identity = new PresentationCoalescing.Identity(
                1L,
                sequence,
                0,
                event.catalogGeneration(),
                event.kind().wireCode(),
                event.sourceSummary().sourceEntityId(),
                event.sourceSummary().targetEntityId(),
                event.dimension(),
                event.position().x(),
                event.position().y(),
                event.position().z(),
                direction.xQ15(),
                direction.yQ15(),
                direction.zQ15(),
                appearance.primaryArgb(),
                appearance.secondaryArgb(),
                appearance.soundProfileId(),
                appearance.particleProfileId(),
                appearance.trailProfileId(),
                new TreeMap<>(appearance.parameters()),
                appearance.intensityMilli(),
                recipients.stream().map(value -> value.identity().playerId()).toList());
        return new P8BufferedPresentation(
                1L,
                sequence,
                0,
                event,
                PresentationCoalescing.Value.create(identity, sequence),
                recipients,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static EffectiveAppearance disabledAppearance() {
        return new EffectiveAppearance(
                0xff_ffffff,
                0xff_ffffff,
                1_000,
                new ResolvedProfile(
                        ProfileChannel.SOUND,
                        Optional.empty(),
                        ProfileResolutionReason.EXPLICITLY_DISABLED),
                new ResolvedProfile(
                        ProfileChannel.PARTICLE,
                        Optional.empty(),
                        ProfileResolutionReason.EXPLICITLY_DISABLED),
                new ResolvedProfile(
                        ProfileChannel.TRAIL,
                        Optional.empty(),
                        ProfileResolutionReason.EXPLICITLY_DISABLED),
                Map.of());
    }

    private static String between(String source, String startNeedle, String endNeedle) {
        int start = source.indexOf(startNeedle);
        int end = source.indexOf(endNeedle, start + startNeedle.length());
        if (start < 0 || end < 0) {
            throw new IllegalStateException(
                    "unable to locate source region " + startNeedle + " .. " + endNeedle);
        }
        return source.substring(start, end);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new IllegalStateException("unable to read source: " + path, failure);
        }
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project root unavailable");
        }
        return current;
    }
}
