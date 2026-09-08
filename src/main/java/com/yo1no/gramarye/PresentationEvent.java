package com.yo1no.gramarye;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;

/** Immutable semantic value for the fixed P8 presentation-event layout. */
final class PresentationEvent {
    private final long catalogGeneration;
    private final PresentationEventKind kind;
    private final PresentationSourceSummary sourceSummary;
    private final ResourceLocation dimension;
    private final PresentationPosition position;
    private final PresentationDirection direction;
    private final PresentationAppearance appearance;
    private final long visualSeed;
    private final long sequence;
    private final int bodySize;

    private PresentationEvent(
            long catalogGeneration,
            PresentationEventKind kind,
            PresentationSourceSummary sourceSummary,
            ResourceLocation dimension,
            PresentationPosition position,
            PresentationDirection direction,
            PresentationAppearance appearance,
            long visualSeed,
            long sequence,
            int bodySize) {
        this.catalogGeneration = catalogGeneration;
        this.kind = kind;
        this.sourceSummary = sourceSummary;
        this.dimension = dimension;
        this.position = position;
        this.direction = direction;
        this.appearance = appearance;
        this.visualSeed = visualSeed;
        this.sequence = sequence;
        this.bodySize = bodySize;
    }

    static PresentationEventCreation createServer(
            long catalogGeneration,
            int kindCode,
            OptionalInt sourceEntityId,
            OptionalInt targetEntityId,
            ResourceLocation dimension,
            double x,
            double y,
            double z,
            double directionX,
            double directionY,
            double directionZ,
            EffectiveAppearance appearance,
            long sequence) {
        if (sequence < PresentationLimits.MIN_SEQUENCE) {
            return rejected(PresentationEventRejection.INVALID_SEQUENCE);
        }
        return create(
                catalogGeneration,
                kindCode,
                sourceEntityId,
                targetEntityId,
                dimension,
                x,
                y,
                z,
                directionX,
                directionY,
                directionZ,
                appearance,
                PresentationSequence.visualSeed(sequence),
                sequence);
    }

    static PresentationEventCreation create(
            long catalogGeneration,
            int kindCode,
            OptionalInt sourceEntityId,
            OptionalInt targetEntityId,
            ResourceLocation dimension,
            double x,
            double y,
            double z,
            double directionX,
            double directionY,
            double directionZ,
            EffectiveAppearance appearance,
            long visualSeed,
            long sequence) {
        if (catalogGeneration < 1L) {
            return rejected(PresentationEventRejection.INVALID_CATALOG_GENERATION);
        }
        var kind = PresentationEventKind.fromWireCode(kindCode);
        if (kind.isEmpty()) {
            return rejected(PresentationEventRejection.INVALID_KIND);
        }
        if (sourceEntityId == null || targetEntityId == null) {
            return rejected(PresentationEventRejection.INVALID_SOURCE_SUMMARY);
        }
        if (!validEntityId(sourceEntityId) || !validEntityId(targetEntityId)) {
            return rejected(PresentationEventRejection.INVALID_SOURCE_SUMMARY);
        }
        if (dimension == null
                || utf8Length(dimension) > PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES) {
            return rejected(PresentationEventRejection.INVALID_DIMENSION);
        }
        if (!PresentationPosition.isValid(x, y, z)) {
            return rejected(PresentationEventRejection.INVALID_POSITION);
        }
        var direction = PresentationDirection.normalized(directionX, directionY, directionZ);
        if (direction.isEmpty()) {
            return rejected(PresentationEventRejection.INVALID_DIRECTION);
        }
        if (appearance == null) {
            return rejected(PresentationEventRejection.INVALID_APPEARANCE);
        }
        if (sequence < PresentationLimits.MIN_SEQUENCE) {
            return rejected(PresentationEventRejection.INVALID_SEQUENCE);
        }

        var sourceSummary = new PresentationSourceSummary(sourceEntityId, targetEntityId);
        var position = new PresentationPosition(x, y, z);
        var wireAppearance = appearance.wireAppearance();
        var bodySize = bodySize(
                sourceSummary, dimension, wireAppearance, sequence);
        if (bodySize > PresentationLimits.MAX_LEGAL_EVENT_BODY_BYTES
                || bodySize > PresentationLimits.MAX_EVENT_BODY_BYTES) {
            return rejected(PresentationEventRejection.INVALID_BODY_SIZE);
        }
        return new AcceptedPresentationEvent(new PresentationEvent(
                catalogGeneration,
                kind.orElseThrow(),
                sourceSummary,
                dimension,
                position,
                direction.orElseThrow(),
                wireAppearance,
                visualSeed,
                sequence,
                bodySize));
    }

    long catalogGeneration() {
        return catalogGeneration;
    }

    PresentationEventKind kind() {
        return kind;
    }

    PresentationSourceSummary sourceSummary() {
        return sourceSummary;
    }

    ResourceLocation dimension() {
        return dimension;
    }

    PresentationPosition position() {
        return position;
    }

    PresentationDirection direction() {
        return direction;
    }

    PresentationAppearance appearance() {
        return appearance;
    }

    long visualSeed() {
        return visualSeed;
    }

    long sequence() {
        return sequence;
    }

    int bodySize() {
        return bodySize;
    }

    int packetCharge() {
        return Math.addExact(bodySize, PresentationLimits.EVENT_PACKET_OVERHEAD_BYTES);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PresentationEvent that)) {
            return false;
        }
        return catalogGeneration == that.catalogGeneration
                && visualSeed == that.visualSeed
                && sequence == that.sequence
                && bodySize == that.bodySize
                && kind == that.kind
                && sourceSummary.equals(that.sourceSummary)
                && dimension.equals(that.dimension)
                && position.equals(that.position)
                && direction.equals(that.direction)
                && appearance.equals(that.appearance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                catalogGeneration,
                kind,
                sourceSummary,
                dimension,
                position,
                direction,
                appearance,
                visualSeed,
                sequence,
                bodySize);
    }

    @Override
    public String toString() {
        return "PresentationEvent[catalogGeneration=" + catalogGeneration
                + ", kind=" + kind
                + ", sourceSummary=" + sourceSummary
                + ", dimension=" + dimension
                + ", position=" + position
                + ", direction=" + direction
                + ", appearance=" + appearance
                + ", visualSeed=" + visualSeed
                + ", sequence=" + sequence
                + ", bodySize=" + bodySize + ']';
    }

    private static PresentationEventCreation rejected(PresentationEventRejection reason) {
        return new RejectedPresentationEvent(reason);
    }

    private static boolean validEntityId(OptionalInt value) {
        return value.isEmpty()
                || (value.getAsInt() >= 1
                        && value.getAsInt() <= PresentationLimits.MAX_ENTITY_ID);
    }

    private static int bodySize(
            PresentationSourceSummary source,
            ResourceLocation dimension,
            PresentationAppearance appearance,
            long sequence) {
        if (sequence < PresentationLimits.MIN_SEQUENCE) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        long size = 0L;
        size = add(size, 1L); // format
        size = add(size, Long.BYTES); // catalog generation
        size = add(size, 1L); // kind
        size = add(size, 1L); // source flags
        if (source.sourceEntityId().isPresent()) {
            size = add(size, varIntSize(source.sourceEntityId().orElseThrow()));
        }
        if (source.targetEntityId().isPresent()) {
            size = add(size, varIntSize(source.targetEntityId().orElseThrow()));
        }
        size = add(size, resourceLocationSize(dimension));
        size = add(size, Double.BYTES * 3L);
        size = add(size, Short.BYTES * 3L);
        size = add(size, Integer.BYTES * 2L);
        size = add(size, selectionSize(appearance.soundProfileId()));
        size = add(size, selectionSize(appearance.particleProfileId()));
        size = add(size, selectionSize(appearance.trailProfileId()));
        size = add(size, varIntSize(appearance.parameters().size()));
        for (var entry : appearance.parameters().entrySet()) {
            size = add(size, resourceLocationSize(entry.getKey()));
            size = add(size, Integer.BYTES);
        }
        size = add(size, varIntSize(appearance.intensityMilli()));
        size = add(size, Long.BYTES); // visual seed
        size = add(size, Long.BYTES); // sequence
        return Math.toIntExact(size);
    }

    private static long selectionSize(Optional<ResourceLocation> selection) {
        return selection.isEmpty() ? 1L : add(1L, resourceLocationSize(selection.orElseThrow()));
    }

    private static int resourceLocationSize(ResourceLocation id) {
        var bytes = utf8Length(id);
        return Math.addExact(varIntSize(bytes), bytes);
    }

    private static int utf8Length(ResourceLocation id) {
        return id.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static int varIntSize(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("canonical bounded VarInt cannot be negative");
        }
        var remaining = value;
        var bytes = 1;
        while ((remaining & ~0x7F) != 0) {
            remaining >>>= 7;
            bytes++;
        }
        return bytes;
    }

    private static long add(long left, long right) {
        return Math.addExact(left, right);
    }
}

enum PresentationEventKind {
    CAST_START(0),
    CAST_RELEASE(1),
    PROJECTILE_SPAWN(2),
    HIT(3),
    EXPLOSION(4),
    SPLIT(5),
    CHAIN(6),
    MARK_APPLY(7),
    MARK_TRIGGER(8),
    CONSTRUCT_SPAWN(9),
    CONSTRUCT_DESTROY(10);

    private final int wireCode;

    PresentationEventKind(int wireCode) {
        this.wireCode = wireCode;
    }

    int wireCode() {
        return wireCode;
    }

    static Optional<PresentationEventKind> fromWireCode(int code) {
        return code >= 0 && code < values().length
                ? Optional.of(values()[code])
                : Optional.empty();
    }
}

record PresentationSourceSummary(OptionalInt sourceEntityId, OptionalInt targetEntityId) {
    PresentationSourceSummary {
        Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        Objects.requireNonNull(targetEntityId, "targetEntityId");
        requirePositive(sourceEntityId);
        requirePositive(targetEntityId);
    }

    private static void requirePositive(OptionalInt id) {
        if (id.isPresent()
                && (id.getAsInt() < 1 || id.getAsInt() > PresentationLimits.MAX_ENTITY_ID)) {
            throw new IllegalArgumentException("present entity ID must be positive");
        }
    }
}

record PresentationPosition(double x, double y, double z) {
    PresentationPosition {
        if (!isValid(x, y, z)) {
            throw new IllegalArgumentException("event position is outside the P8 bounds");
        }
    }

    static boolean isValid(double x, double y, double z) {
        return Double.isFinite(x)
                && Double.isFinite(y)
                && Double.isFinite(z)
                && Math.abs(x) <= PresentationLimits.MAX_HORIZONTAL_POSITION
                && Math.abs(y) <= PresentationLimits.MAX_VERTICAL_POSITION
                && Math.abs(z) <= PresentationLimits.MAX_HORIZONTAL_POSITION;
    }
}

record PresentationDirection(short xQ15, short yQ15, short zQ15) {
    PresentationDirection {
        requireComponent(xQ15);
        requireComponent(yQ15);
        requireComponent(zQ15);
        if (xQ15 == 0 && yQ15 == 0 && zQ15 == 0) {
            throw new IllegalArgumentException("Q15 direction cannot be all zero");
        }
    }

    static Optional<PresentationDirection> normalized(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return Optional.empty();
        }
        var scale = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        if (scale == 0.0D) {
            return Optional.empty();
        }
        var scaledX = x / scale;
        var scaledY = y / scale;
        var scaledZ = z / scale;
        var magnitude = Math.hypot(Math.hypot(scaledX, scaledY), scaledZ);
        var xQ15 = quantize(scaledX / magnitude);
        var yQ15 = quantize(scaledY / magnitude);
        var zQ15 = quantize(scaledZ / magnitude);
        if (xQ15 == 0 && yQ15 == 0 && zQ15 == 0) {
            return Optional.empty();
        }
        return Optional.of(new PresentationDirection(xQ15, yQ15, zQ15));
    }

    private static short quantize(double value) {
        var clamped = Math.max(-1.0D, Math.min(1.0D, value));
        return (short) Math.round(clamped * PresentationLimits.MAX_DIRECTION_Q15);
    }

    private static void requireComponent(short component) {
        if (component < -PresentationLimits.MAX_DIRECTION_Q15
                || component > PresentationLimits.MAX_DIRECTION_Q15) {
            throw new IllegalArgumentException("Q15 component is outside the P8 bounds");
        }
    }
}

sealed interface PresentationEventCreation
        permits AcceptedPresentationEvent, RejectedPresentationEvent {}

record AcceptedPresentationEvent(PresentationEvent event) implements PresentationEventCreation {
    AcceptedPresentationEvent {
        Objects.requireNonNull(event, "event");
    }
}

record RejectedPresentationEvent(PresentationEventRejection reason)
        implements PresentationEventCreation {
    RejectedPresentationEvent {
        Objects.requireNonNull(reason, "reason");
    }
}

enum PresentationEventRejection {
    INVALID_CATALOG_GENERATION,
    INVALID_KIND,
    INVALID_SOURCE_SUMMARY,
    INVALID_DIMENSION,
    INVALID_POSITION,
    INVALID_DIRECTION,
    INVALID_APPEARANCE,
    INVALID_SEQUENCE,
    INVALID_BODY_SIZE
}
