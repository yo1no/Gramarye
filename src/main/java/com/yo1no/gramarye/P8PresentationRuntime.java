package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

enum P8PresentationOfferOutcome {
    ACCEPTED,
    DEGRADED,
    DROPPED
}

enum P8PresentationSubmissionResult {
    SUBMITTED,
    UNAVAILABLE
}

enum P8ServerRuntimeDiagnosticCode {
    OBSERVER_RUNTIME_EXCEPTION,
    CATALOG_TRANSPORT_RUNTIME_EXCEPTION,
    EVENT_TRANSPORT_RUNTIME_EXCEPTION
}

/** Package-private typed boundary between S3 delivery planning and S4 submission. */
interface P8PresentationTransport {
    Optional<P8RecipientIdentity> captureReadyIdentity(
            ServerPlayer player, long catalogGeneration);

    boolean isCurrent(
            ServerPlayer player,
            P8RecipientIdentity identity,
            long catalogGeneration);

    /** Measures the exact pre-compression PLAY packet immediately before admission. */
    default OptionalInt packetCharge(
            ServerPlayer player,
            P8RecipientIdentity identity,
            PresentationEvent event) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(event, "event");
        return OptionalInt.of(event.packetCharge());
    }

    P8PresentationSubmissionResult submit(
            P8RecipientIdentity identity, PresentationEvent event);
}

enum UnavailableP8PresentationTransport implements P8PresentationTransport {
    INSTANCE;

    @Override
    public Optional<P8RecipientIdentity> captureReadyIdentity(
            ServerPlayer player, long catalogGeneration) {
        Objects.requireNonNull(player, "player");
        return Optional.empty();
    }

    @Override
    public boolean isCurrent(
            ServerPlayer player,
            P8RecipientIdentity identity,
            long catalogGeneration) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(identity, "identity");
        return false;
    }

    @Override
    public OptionalInt packetCharge(
            ServerPlayer player,
            P8RecipientIdentity identity,
            PresentationEvent event) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(event, "event");
        return OptionalInt.empty();
    }

    @Override
    public P8PresentationSubmissionResult submit(
            P8RecipientIdentity identity, PresentationEvent event) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(event, "event");
        return P8PresentationSubmissionResult.UNAVAILABLE;
    }
}

record P8RecipientIdentity(UUID playerId, long connectionEpoch) {
    P8RecipientIdentity {
        Objects.requireNonNull(playerId, "playerId");
        if (connectionEpoch < PresentationLimits.MIN_SEQUENCE) {
            throw new IllegalArgumentException("connection epoch must be positive");
        }
    }
}

record P8SelectedRecipient(
        P8RecipientIdentity identity,
        PresentationOrdering.RecipientCategory category,
        double squaredDistance) {
    P8SelectedRecipient {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(category, "category");
        if (!Double.isFinite(squaredDistance)
                || squaredDistance < 0.0D
                || squaredDistance > PresentationLimits.MAX_RECIPIENT_DISTANCE_SQUARED) {
            throw new IllegalArgumentException("recipient distance is outside the P8 range");
        }
    }
}

record P8RecipientSelection(List<P8SelectedRecipient> recipients, int evaluations) {
    P8RecipientSelection {
        recipients = List.copyOf(Objects.requireNonNull(recipients, "recipients"));
        if (recipients.size() > PresentationLimits.MAX_SELECTED_RECIPIENTS
                || evaluations < 0
                || evaluations > PresentationLimits.MAX_ONLINE_RECIPIENT_SCAN_PER_EVENT) {
            throw new IllegalArgumentException("recipient selection exceeds its P8 bound");
        }
    }
}

record P8EventMaterial(
        PresentationEventKind kind,
        ResourceLocation dimension,
        double x,
        double y,
        double z,
        double directionX,
        double directionY,
        double directionZ,
        OptionalInt sourceEntityId,
        OptionalInt targetEntityId,
        Optional<UUID> sourcePlayerId,
        Optional<UUID> targetPlayerId,
        Optional<UUID> targetEntityUuid) {
    P8EventMaterial {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        Objects.requireNonNull(targetEntityId, "targetEntityId");
        Objects.requireNonNull(sourcePlayerId, "sourcePlayerId");
        Objects.requireNonNull(targetPlayerId, "targetPlayerId");
        Objects.requireNonNull(targetEntityUuid, "targetEntityUuid");
        if ((kind != PresentationEventKind.CAST_RELEASE && kind != PresentationEventKind.HIT)
                || dimension.toString().getBytes(StandardCharsets.UTF_8).length
                        > PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES
                || !validEntityId(sourceEntityId)
                || !validEntityId(targetEntityId)
                || kind == PresentationEventKind.HIT
                        && (targetEntityId.isEmpty() || targetEntityUuid.isEmpty())
                || !PresentationPosition.isValid(x, y, z)
                || PresentationDirection.normalized(
                                directionX, directionY, directionZ)
                        .isEmpty()) {
            throw new IllegalArgumentException("event material has invalid geometry");
        }
    }

    private static boolean validEntityId(OptionalInt entityId) {
        return entityId.isEmpty()
                || entityId.orElseThrow() >= 1
                        && entityId.orElseThrow() <= PresentationLimits.MAX_ENTITY_ID;
    }
}

record P8BufferedPresentation(
        long authoritativeTick,
        long sourceEventId,
        int appliedStepIndex,
        PresentationEvent event,
        PresentationCoalescing.Value coalescingValue,
        List<P8SelectedRecipient> recipients,
        Optional<UUID> sourcePlayerId,
        Optional<UUID> targetPlayerId,
        Optional<UUID> targetEntityUuid) {
    P8BufferedPresentation {
        if (authoritativeTick < 0
                || sourceEventId < PresentationLimits.MIN_SEQUENCE
                || appliedStepIndex < 0
                || appliedStepIndex > 7) {
            throw new IllegalArgumentException("buffered presentation identity is invalid");
        }
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(coalescingValue, "coalescingValue");
        recipients = List.copyOf(Objects.requireNonNull(recipients, "recipients"));
        sourcePlayerId = Objects.requireNonNull(sourcePlayerId, "sourcePlayerId");
        targetPlayerId = Objects.requireNonNull(targetPlayerId, "targetPlayerId");
        targetEntityUuid = Objects.requireNonNull(targetEntityUuid, "targetEntityUuid");
        if (event.sequence() != coalescingValue.sequence()
                || event.visualSeed() != coalescingValue.visualSeed()
                || event.catalogGeneration()
                        != coalescingValue.identity().catalogGeneration()
                || recipients.size() > PresentationLimits.MAX_SELECTED_RECIPIENTS) {
            throw new IllegalArgumentException("buffered presentation values disagree");
        }
    }
}

record P8Delivery(
        PresentationOrdering.DeliveryCandidate ordering,
        P8RecipientIdentity identity,
        P8BufferedPresentation buffered,
        int packetCharge) {
    P8Delivery(
            PresentationOrdering.DeliveryCandidate ordering,
            P8RecipientIdentity identity,
            P8BufferedPresentation buffered) {
        this(ordering, identity, buffered, buffered.event().packetCharge());
    }

    P8Delivery {
        Objects.requireNonNull(ordering, "ordering");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(buffered, "buffered");
        if (!ordering.recipientId().equals(identity.playerId())
                || ordering.sequence() != buffered.event().sequence()) {
            throw new IllegalArgumentException("delivery identity is inconsistent");
        }
        if (packetCharge <= 0
                || packetCharge > PresentationLimits.MAX_EVENT_PACKET_CHARGE_BYTES) {
            throw new IllegalArgumentException("delivery packet charge is outside bounds");
        }
    }
}

/** Pure bounded delivery-budget transition used by the server post-tick drain. */
final class P8DeliveryAdmission {
    private P8DeliveryAdmission() {}

    static List<P8Delivery> admit(List<P8Delivery> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.size() > PresentationLimits.MAX_CANDIDATE_DELIVERIES_PER_TICK) {
            throw new IllegalArgumentException("candidate deliveries exceed the P8 tick bound");
        }

        var orderInputs = candidates.stream()
                .map(value -> Objects.requireNonNull(value, "candidate").ordering())
                .toList();
        var selectedOrder = PresentationOrdering.selectDeliveries(
                orderInputs,
                Math.min(
                        Math.toIntExact(PresentationLimits.MAX_CANDIDATE_DELIVERIES_PER_TICK),
                        orderInputs.size()));
        var byOrdering = new HashMap<
                PresentationOrdering.DeliveryCandidate, P8Delivery>();
        candidates.forEach(delivery -> byOrdering.put(delivery.ordering(), delivery));

        var serverBudget = PresentationBudget.initial(
                PresentationBudget.Scope.DELIVERIES_PER_SERVER);
        var playerBudgets = new HashMap<UUID, PresentationBudget>();
        var admitted = new ArrayList<P8Delivery>();
        for (PresentationOrdering.DeliveryCandidate retained : selectedOrder.retained()) {
            P8Delivery delivery = byOrdering.get(retained);
            if (delivery == null) {
                throw new IllegalStateException("P8 delivery ordering lost its value");
            }
            var charge = new PresentationCost(1L, delivery.packetCharge());
            var nextServer = serverBudget.consume(charge);
            var playerBudget = playerBudgets.getOrDefault(
                    delivery.identity().playerId(),
                    PresentationBudget.initial(PresentationBudget.Scope.DELIVERIES_PER_PLAYER));
            var nextPlayer = playerBudget.consume(charge);
            if (nextServer.outcome() != PresentationBudget.Outcome.ACCEPTED
                    || nextPlayer.outcome() != PresentationBudget.Outcome.ACCEPTED) {
                continue;
            }
            serverBudget = nextServer.nextState();
            playerBudgets.put(delivery.identity().playerId(), nextPlayer.nextState());
            admitted.add(delivery);
        }
        return List.copyOf(admitted);
    }
}

final class P8RecipientSelector {
    private static final Comparator<UUID> UNSIGNED_UUID_ORDER = (left, right) -> {
        var comparison = Long.compareUnsigned(
                left.getMostSignificantBits(), right.getMostSignificantBits());
        return comparison != 0
                ? comparison
                : Long.compareUnsigned(
                        left.getLeastSignificantBits(), right.getLeastSignificantBits());
    };

    private P8RecipientSelector() {}

    static Optional<P8RecipientSelection> select(
            MinecraftServer server,
            P8EventMaterial material,
            PresentationEvent event,
            Optional<Entity> targetEntity,
            Optional<ServerPlayer> sourcePlayer,
            P8PresentationTransport transport) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(targetEntity, "targetEntity");
        Objects.requireNonNull(sourcePlayer, "sourcePlayer");
        Objects.requireNonNull(transport, "transport");

        var online = server.getPlayerList().getPlayers();
        if (!withinOnlineScanBound(online.size())) {
            return Optional.empty();
        }

        var unique = new TreeMap<UUID, ServerPlayer>(UNSIGNED_UUID_ORDER);
        if (material.kind() == PresentationEventKind.CAST_RELEASE) {
            for (ServerPlayer player : online) {
                if (!addBoundedCandidate(unique, player)) {
                    return Optional.empty();
                }
            }
        } else {
            Entity target = targetEntity.orElse(null);
            if (target == null || !(target.level() instanceof ServerLevel targetLevel)) {
                return Optional.of(new P8RecipientSelection(List.of(), 0));
            }
            var watchers = targetLevel.getChunkSource().chunkMap.getPlayersWatching(target);
            if (!withinOnlineScanBound(watchers.size())) {
                return Optional.empty();
            }
            for (ServerPlayer watcher : watchers) {
                if (!addBoundedCandidate(unique, watcher)) {
                    return Optional.empty();
                }
            }
            if (target instanceof ServerPlayer targetPlayer) {
                if (!addBoundedCandidate(unique, targetPlayer)) {
                    return Optional.empty();
                }
            }
            if (sourcePlayer.isPresent()
                    && !addBoundedCandidate(unique, sourcePlayer.orElseThrow())) {
                return Optional.empty();
            }
        }

        var eligible = new ArrayList<P8SelectedRecipient>(
                Math.min(unique.size(), PresentationLimits.MAX_SELECTED_RECIPIENTS));
        var evaluations = 0;
        for (ServerPlayer player : unique.values()) {
            evaluations++;
            if (!currentConnected(server, player)
                    || !player.serverLevel().dimension().location().equals(material.dimension())) {
                continue;
            }
            var distance = player.distanceToSqr(material.x(), material.y(), material.z());
            if (!Double.isFinite(distance)
                    || distance > PresentationLimits.MAX_RECIPIENT_DISTANCE_SQUARED) {
                continue;
            }
            Optional<P8RecipientIdentity> captured = Objects.requireNonNull(
                    transport.captureReadyIdentity(player, event.catalogGeneration()),
                    "ready identity result");
            if (captured.isEmpty()) {
                continue;
            }
            var identity = captured.orElseThrow();
            if (!identity.playerId().equals(player.getUUID())) {
                throw new IllegalStateException("P8 readiness identity belongs to another player");
            }
            eligible.add(new P8SelectedRecipient(
                    identity, category(material, identity.playerId()), distance));
        }

        return Optional.of(retainEligible(eligible, evaluations, event.sequence()));
    }

    static boolean withinOnlineScanBound(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("online recipient count cannot be negative");
        }
        return count <= PresentationLimits.MAX_ONLINE_RECIPIENT_SCAN_PER_EVENT;
    }

    static <T> boolean admitCandidate(
            Map<UUID, T> candidates, UUID playerId, T candidate) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(candidate, "candidate");
        if (candidates.containsKey(playerId)) {
            return true;
        }
        if (candidates.size() >= PresentationLimits.MAX_ONLINE_RECIPIENT_SCAN_PER_EVENT) {
            return false;
        }
        candidates.put(playerId, candidate);
        return true;
    }

    static P8RecipientSelection retainEligible(
            List<P8SelectedRecipient> eligible, int evaluations, long sequence) {
        Objects.requireNonNull(eligible, "eligible");
        if (eligible.size() > PresentationLimits.MAX_ONLINE_RECIPIENT_SCAN_PER_EVENT) {
            throw new IllegalArgumentException("eligible recipients exceed the P8 scan bound");
        }
        var orderInputs = eligible.stream()
                .map(value -> new PresentationOrdering.DeliveryCandidate(
                        value.category(),
                        value.squaredDistance(),
                        value.identity().playerId(),
                        sequence))
                .toList();
        var selectedOrder = PresentationOrdering.selectDeliveries(
                orderInputs,
                Math.min(PresentationLimits.MAX_SELECTED_RECIPIENTS, orderInputs.size()));
        var byOrdering = new HashMap<
                PresentationOrdering.DeliveryCandidate, P8SelectedRecipient>();
        for (var index = 0; index < eligible.size(); index++) {
            byOrdering.put(orderInputs.get(index), eligible.get(index));
        }
        var selected = selectedOrder.retained().stream()
                .map(byOrdering::get)
                .toList();
        return new P8RecipientSelection(selected, evaluations);
    }

    private static boolean addBoundedCandidate(
            Map<UUID, ServerPlayer> candidates, ServerPlayer player) {
        return player == null
                || admitCandidate(candidates, player.getUUID(), player);
    }

    static boolean remainsEligible(
            MinecraftServer server,
            P8BufferedPresentation buffered,
            P8SelectedRecipient selected,
            P8PresentationTransport transport) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(buffered, "buffered");
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(transport, "transport");
        var player = server.getPlayerList().getPlayer(selected.identity().playerId());
        if (player == null
                || !currentConnected(server, player)
                || !player.serverLevel().dimension().location()
                        .equals(buffered.event().dimension())
                || !transport.isCurrent(
                        player,
                        selected.identity(),
                        buffered.event().catalogGeneration())) {
            return false;
        }
        var position = buffered.event().position();
        var distance = player.distanceToSqr(position.x(), position.y(), position.z());
        if (!Double.isFinite(distance)
                || distance > PresentationLimits.MAX_RECIPIENT_DISTANCE_SQUARED) {
            return false;
        }
        if (buffered.event().kind() != PresentationEventKind.HIT
                || selected.category() != PresentationOrdering.RecipientCategory.ORDINARY) {
            return true;
        }

        ServerLevel level = player.serverLevel();
        var targetId = buffered.event().sourceSummary().targetEntityId();
        if (targetId.isEmpty() || buffered.targetEntityUuid().isEmpty()) {
            return false;
        }
        Entity target = level.getEntity(targetId.orElseThrow());
        return target != null
                && target.getUUID().equals(buffered.targetEntityUuid().orElseThrow())
                && level.getChunkSource().chunkMap.getPlayersWatching(target).contains(player);
    }

    private static PresentationOrdering.RecipientCategory category(
            P8EventMaterial material, UUID playerId) {
        if (material.targetPlayerId().stream().anyMatch(playerId::equals)) {
            return PresentationOrdering.RecipientCategory.TARGET_SELF;
        }
        if (material.sourcePlayerId().stream().anyMatch(playerId::equals)) {
            return PresentationOrdering.RecipientCategory.SOURCE;
        }
        return PresentationOrdering.RecipientCategory.ORDINARY;
    }

    static boolean currentConnected(
            MinecraftServer server, ServerPlayer player) {
        var connection = player.connection;
        return player.getServer() == server
                && server.getPlayerList().getPlayer(player.getUUID()) == player
                && connection != null
                && connection.isAcceptingMessages()
                && !player.hasDisconnected();
    }
}

final class P8TickState {
    private static final long NO_TICK = -1L;

    private final long runtimeTick;
    private final List<P8BufferedPresentation> buffer;
    private final PresentationBudget serverLogical;
    private final PresentationBudget bufferBudget;
    private final Map<SkillInstanceId, PresentationBudget> skillLogical;
    private final Map<UUID, PresentationBudget> sourceLogical;
    private final PresentationBudget recipientEvaluations;

    private P8TickState(
            long runtimeTick,
            List<P8BufferedPresentation> buffer,
            PresentationBudget serverLogical,
            PresentationBudget bufferBudget,
            Map<SkillInstanceId, PresentationBudget> skillLogical,
            Map<UUID, PresentationBudget> sourceLogical,
            PresentationBudget recipientEvaluations) {
        if (runtimeTick < NO_TICK) {
            throw new IllegalArgumentException("runtime tick is outside the P8 domain");
        }
        this.runtimeTick = runtimeTick;
        this.buffer = List.copyOf(Objects.requireNonNull(buffer, "buffer"));
        this.serverLogical = Objects.requireNonNull(serverLogical, "serverLogical");
        this.bufferBudget = Objects.requireNonNull(bufferBudget, "bufferBudget");
        this.skillLogical = Map.copyOf(Objects.requireNonNull(skillLogical, "skillLogical"));
        this.sourceLogical = Map.copyOf(Objects.requireNonNull(sourceLogical, "sourceLogical"));
        this.recipientEvaluations =
                Objects.requireNonNull(recipientEvaluations, "recipientEvaluations");
        if (this.buffer.size() > PresentationLimits.MAX_CURRENT_TICK_EVENT_BUFFER_EVENTS
                || this.skillLogical.size() > PresentationLimits.MAX_LOGICAL_EVENTS_PER_SERVER_PER_TICK
                || this.sourceLogical.size() > PresentationLimits.MAX_LOGICAL_EVENTS_PER_SERVER_PER_TICK) {
            throw new IllegalArgumentException("tick state exceeds its P8 retention bound");
        }
    }

    static P8TickState empty() {
        return empty(NO_TICK);
    }

    static P8TickState empty(long runtimeTick) {
        return new P8TickState(
                runtimeTick,
                List.of(),
                PresentationBudget.initial(PresentationBudget.Scope.LOGICAL_EVENTS_PER_SERVER),
                PresentationBudget.initial(PresentationBudget.Scope.SERVER_EVENT_BUFFER),
                Map.of(),
                Map.of(),
                PresentationBudget.initial(PresentationBudget.Scope.RECIPIENT_EVALUATIONS));
    }

    long runtimeTick() {
        return runtimeTick;
    }

    List<P8BufferedPresentation> buffer() {
        return buffer;
    }

    P8TickScratch scratch(long offeredTick) {
        if (offeredTick < 0 || runtimeTick > offeredTick) {
            throw new IllegalArgumentException("presentation tick regressed");
        }
        return runtimeTick == offeredTick
                ? new P8TickScratch(this)
                : new P8TickScratch(empty(offeredTick));
    }

    P8TickState clearBufferPreservingLogicalWork() {
        return new P8TickState(
                runtimeTick,
                List.of(),
                serverLogical,
                PresentationBudget.initial(PresentationBudget.Scope.SERVER_EVENT_BUFFER),
                skillLogical,
                sourceLogical,
                recipientEvaluations);
    }

    static final class P8TickScratch {
        private static final Comparator<P8RecipientIdentity> RECIPIENT_IDENTITY_ORDER =
                (left, right) -> {
                    var comparison = Long.compareUnsigned(
                            left.playerId().getMostSignificantBits(),
                            right.playerId().getMostSignificantBits());
                    if (comparison == 0) {
                        comparison = Long.compareUnsigned(
                                left.playerId().getLeastSignificantBits(),
                                right.playerId().getLeastSignificantBits());
                    }
                    return comparison != 0
                            ? comparison
                            : Long.compare(left.connectionEpoch(), right.connectionEpoch());
                };

        private final long runtimeTick;
        private final ArrayList<P8BufferedPresentation> buffer;
        private PresentationBudget serverLogical;
        private PresentationBudget bufferBudget;
        private final Map<SkillInstanceId, PresentationBudget> skillLogical;
        private final Map<UUID, PresentationBudget> sourceLogical;
        private PresentationBudget recipientEvaluations;

        private P8TickScratch(P8TickState source) {
            this.runtimeTick = source.runtimeTick;
            this.buffer = new ArrayList<>(source.buffer);
            this.serverLogical = source.serverLogical;
            this.bufferBudget = source.bufferBudget;
            this.skillLogical = new HashMap<>(source.skillLogical);
            this.sourceLogical = new HashMap<>(source.sourceLogical);
            this.recipientEvaluations = source.recipientEvaluations;
        }

        boolean admitLogical(SkillInstanceId skillInstanceId, Optional<UUID> sourcePlayerId) {
            Objects.requireNonNull(skillInstanceId, "skillInstanceId");
            Objects.requireNonNull(sourcePlayerId, "sourcePlayerId");
            var charge = PresentationCost.count(1);
            var nextServer = serverLogical.consume(charge);
            var skillBudget = skillLogical.getOrDefault(
                    skillInstanceId,
                    PresentationBudget.initial(
                            PresentationBudget.Scope.LOGICAL_EVENTS_PER_SKILL));
            var nextSkill = skillBudget.consume(charge);
            PresentationBudget.Decision nextSource = null;
            if (sourcePlayerId.isPresent()) {
                var sourceBudget = sourceLogical.getOrDefault(
                        sourcePlayerId.orElseThrow(),
                        PresentationBudget.initial(
                                PresentationBudget.Scope.LOGICAL_EVENTS_PER_SOURCE));
                nextSource = sourceBudget.consume(charge);
            }
            if (nextServer.outcome() != PresentationBudget.Outcome.ACCEPTED
                    || nextSkill.outcome() != PresentationBudget.Outcome.ACCEPTED
                    || nextSource != null
                            && nextSource.outcome() != PresentationBudget.Outcome.ACCEPTED) {
                return false;
            }
            serverLogical = nextServer.nextState();
            skillLogical.put(skillInstanceId, nextSkill.nextState());
            if (nextSource != null) {
                sourceLogical.put(sourcePlayerId.orElseThrow(), nextSource.nextState());
            }
            return true;
        }

        boolean chargeRecipientEvaluations(int evaluations) {
            var decision = recipientEvaluations.consume(PresentationCost.count(evaluations));
            if (decision.outcome() != PresentationBudget.Outcome.ACCEPTED) {
                return false;
            }
            recipientEvaluations = decision.nextState();
            return true;
        }

        PresentationDegradation.Decision admitBuffered(P8BufferedPresentation offered) {
            Objects.requireNonNull(offered, "offered");
            for (P8BufferedPresentation existing : buffer) {
                var decision = PresentationCoalescing.decide(
                        existing.coalescingValue(), offered.coalescingValue());
                if (decision.outcome()
                                == PresentationCoalescing.Outcome.EXACT_IDENTITY_COALESCED
                        && recipientIdentities(existing)
                                .equals(recipientIdentities(offered))) {
                    return bufferAdmissionDecision(true, Optional.of(decision));
                }
            }

            var charge = new PresentationCost(1, offered.event().bodySize());
            var bufferDecision = bufferBudget.consume(charge);
            if (bufferDecision.outcome() != PresentationBudget.Outcome.ACCEPTED) {
                return bufferAdmissionDecision(true, Optional.empty());
            }
            buffer.add(offered);
            bufferBudget = bufferDecision.nextState();
            return bufferAdmissionDecision(false, Optional.empty());
        }

        private static List<P8RecipientIdentity> recipientIdentities(
                P8BufferedPresentation value) {
            return value.recipients().stream()
                    .map(P8SelectedRecipient::identity)
                    .sorted(RECIPIENT_IDENTITY_ORDER)
                    .toList();
        }

        private static PresentationDegradation.Decision bufferAdmissionDecision(
                boolean remainsOverBudget,
                Optional<PresentationCoalescing.Decision> coalescingDecision) {
            return PresentationDegradation.decide(
                    new PresentationDegradation.Candidate(0, 1, false, false),
                    new PresentationDegradation.Constraints(
                            0L, false, 1, false, remainsOverBudget),
                    coalescingDecision);
        }

        P8TickState freeze() {
            return new P8TickState(
                    runtimeTick,
                    buffer,
                    serverLogical,
                    bufferBudget,
                    skillLogical,
                    sourceLogical,
                    recipientEvaluations);
        }
    }

    static P8TickScratch scratch(P8TickState state, long offeredTick) {
        return Objects.requireNonNull(state, "state").scratch(offeredTick);
    }
}
